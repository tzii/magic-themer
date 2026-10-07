package dev.magicthemer.icons

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.util.TypedValue
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.Locale
import org.xmlpull.v1.XmlPullParser

/** Installed, user-owned packs only; no APK downloading or foreign code loading. */
class IconPackRepository(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager
    private var resourcePackage: String? = null
    private var resourceCache: Resources? = null

    /** Call on a background thread. Manifest queries must match both discovery lists. */
    fun discover(): List<IconPack> {
        val packs = linkedMapOf<String, IconPack>()
        val intents = DISCOVERY_ACTIONS.map { Intent(it) } +
            DISCOVERY_CATEGORIES.map { Intent(Intent.ACTION_MAIN).addCategory(it) }
        for (intent in intents) {
            val activities = if (Build.VERSION.SDK_INT >= 33) {
                packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(intent, 0)
            }
            for (activity in activities) {
                val info = activity.activityInfo ?: continue
                if (!info.enabled || !info.applicationInfo.enabled || !AppFilterParser.validPackageName(info.packageName)) continue
                packs.putIfAbsent(info.packageName, IconPack(info.packageName, info.applicationInfo.loadLabel(packageManager).toString()))
            }
        }
        return packs.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    /** Read mappings and the full drawable catalogue without allocating images. */
    fun load(pack: IconPack): PackData {
        require(AppFilterParser.validPackageName(pack.packageName)) { "Invalid icon pack package name." }
        val resources = resources(pack.packageName)
        val warnings = ArrayList<String>()
        val appfilter = readMetadata(
            resources, pack.packageName, "appfilter", warnings, readAll = false,
            parseCompiled = { res, id ->
                val builder = AppFilterParser.Builder()
                parseCompiledXml(res, id, builder::startElement)
                builder.build()
            },
            parseText = { AppFilterParser.parse(it) },
        ).firstOrNull()
        if (appfilter != null) warnings += appfilter.warnings

        val catalogues = readMetadata(
            resources, pack.packageName, "drawable", warnings, readAll = true,
            parseCompiled = { res, id ->
                val builder = DrawableListParser.Builder()
                parseCompiledXml(res, id, builder::startElement)
                builder.build()
            },
            parseText = { DrawableListParser.parse(it) },
        )
        val mappings = appfilter?.mappings.orEmpty()
        val drawables = linkedSetOf<String>()
        // Display catalogue order first; append mapped icons omitted by the
        // catalogue. Alternatives remain artwork, never fabricated theme entries.
        for (catalogue in catalogues) {
            drawables += catalogue.drawables
            warnings += catalogue.warnings
        }
        drawables += mappings.map { it.drawableName }
        require(drawables.size <= DrawableListParser.MAX_DRAWABLES) { "The combined icon catalogue exceeds the 100,000 icon limit." }
        if (appfilter == null) {
            if (drawables.isEmpty()) throw IllegalArgumentException("No readable icon metadata was found in ${pack.label}. ${warnings.joinToString(" ")}")
            warnings += "No readable appfilter.xml was found; browse the artwork and assign icons to apps manually."
        }
        // Check names once while loading metadata. This does not decode bitmaps
        // or inflate vectors, so missing mappings can be marked before scrolling
        // their thumbnails into view or attempting an export.
        val unavailable = drawables.filterTo(linkedSetOf()) { drawableId(resources, pack.packageName, it) == 0 }
        if (unavailable.isNotEmpty()) {
            warnings += "${unavailable.size} catalogue resources are missing from the installed icon pack. Choose another icon for affected apps."
        }
        return PackData(pack, mappings, warnings.distinct(), drawables.toList(), unavailable)
    }

    /** Draw one icon on demand, flattening bitmap, vector and adaptive resources. */
    fun render(data: PackData, mapping: IconMapping, size: Int): Bitmap? = renderDrawable(data, mapping.drawableName, size)

    /** Render a gallery alternative, including artwork without an app mapping. */
    fun renderDrawable(data: PackData, drawableName: String, size: Int): Bitmap? {
        require(size in 32..1024) { "Icon size must be between 32 and 1024 pixels." }
        if (!data.isDrawableAvailable(drawableName) || !AppFilterParser.validPackageName(data.pack.packageName) || !AppFilterParser.validDrawableName(drawableName)) return null
        return try {
            val resources = resources(data.pack.packageName)
            val id = drawableId(resources, data.pack.packageName, drawableName)
            if (id == 0) return null
            val value = TypedValue()
            resources.getValue(id, value, true)
            val path = value.string?.toString()?.lowercase(Locale.ROOT).orEmpty()
            val raster = !path.endsWith(".9.png") && listOf(".png", ".webp", ".jpg", ".jpeg").any(path::endsWith)
            if (raster) {
                // Decode raster source data near the requested size; drawing a
                // full-density BitmapDrawable onto a small canvas is too late
                // to bound its source allocation.
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true; inScaled = false }
                BitmapFactory.decodeResource(resources, id, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
                val largest = maxOf(bounds.outWidth, bounds.outHeight)
                var sample = 1
                while (sample <= Int.MAX_VALUE / 2 && largest / (sample * 2) >= size) sample *= 2
                val options = BitmapFactory.Options().apply {
                    inScaled = false
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val source = BitmapFactory.decodeResource(resources, id, options) ?: return null
                try {
                    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
                        Canvas(bitmap).drawBitmap(source, null, Rect(0, 0, size, size), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                    }
                } finally {
                    source.recycle()
                }
            }
            val drawable = resources.getDrawable(id, null).mutate()
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
                drawable.setBounds(0, 0, size, size)
                drawable.draw(Canvas(bitmap))
            }
        } catch (_: Exception) {
            // The caller counts/report these misses rather than exporting a blank icon.
            null
        }
    }

    private fun drawableId(resources: Resources, packageName: String, name: String): Int {
        val drawable = resources.getIdentifier(name, "drawable", packageName)
        return if (drawable != 0) drawable else resources.getIdentifier(name, "mipmap", packageName)
    }

    /** Refresh must discard resource handles when an installed pack was updated. */
    @Synchronized
    fun clearResourceCache() {
        resourcePackage = null
        resourceCache = null
    }

    @Synchronized
    private fun resources(packageName: String): Resources {
        if (resourcePackage == packageName) resourceCache?.let { return it }
        // getResourcesForApplication provides asset/split APK resolution without
        // CONTEXT_INCLUDE_CODE or CONTEXT_IGNORE_SECURITY.
        return packageManager.getResourcesForApplication(packageName).also {
            resourcePackage = packageName
            resourceCache = it
        }
    }

    private fun <T> readMetadata(
        resources: Resources,
        packageName: String,
        name: String,
        warnings: MutableList<String>,
        readAll: Boolean,
        parseCompiled: (Resources, Int) -> T,
        parseText: (InputStream) -> T,
    ): List<T> {
        val parsed = ArrayList<T>()
        for (source in listOf("xml", "raw", "assets")) {
            val id = if (source == "assets") 0 else resources.getIdentifier(name, source, packageName)
            if (source != "assets" && id == 0) continue
            try {
                parsed += when (source) {
                    "xml" -> parseCompiled(resources, id)
                    "raw" -> resources.openRawResource(id).use(parseText)
                    else -> resources.assets.open("$name.xml").use(parseText)
                }
                if (!readAll) break
            } catch (error: FileNotFoundException) {
                if (source != "assets") warnings += "$name.xml could not be read from $source (${error.javaClass.simpleName})."
            } catch (error: Exception) {
                warnings += "$name.xml could not be read from $source (${error.javaClass.simpleName})."
            }
        }
        return parsed
    }

    private fun parseCompiledXml(resources: Resources, id: Int, startElement: (String, Map<String, String>) -> Unit) {
        val parser = resources.getXml(id)
        try {
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) {
                    require(parser.attributeCount <= 128) { "Too many attributes in icon metadata." }
                    val attributes = buildMap {
                        for (index in 0 until parser.attributeCount) put(parser.getAttributeName(index), parser.getAttributeValue(index))
                    }
                    startElement(parser.name, attributes)
                }
                parser.next()
            }
        } finally {
            parser.close()
        }
    }

    companion object {
        val DISCOVERY_ACTIONS = listOf(
            "org.adw.launcher.THEMES",
            "org.adw.ActivityStarter.THEMES",
            "com.novalauncher.THEME",
            "ch.deletescape.lawnchair.ICONPACK",
            "app.lawnchair.icons.THEMED_ICON",
            "com.gau.go.launcherex.theme",
            "com.dlto.atom.launcher.THEME",
            "ginlemon.smartlauncher.THEMES",
            "com.sonymobile.home.ICON_PACK",
        )

        // Packs use both action and MAIN/category conventions; querying only
        // one form misses otherwise valid dashboard implementations.
        val DISCOVERY_CATEGORIES = listOf(
            "com.anddoes.launcher.THEME",
            "com.gau.go.launcherex.theme",
            "com.dlto.atom.launcher.THEME",
            "ginlemon.smartlauncher.THEMES",
            "com.sonymobile.home.ICON_PACK",
            "com.teslacoilsw.launcher.THEME",
            "com.novalauncher.THEME",
            "com.fede.launcher.THEME_ICONPACK",
        )
    }
}
