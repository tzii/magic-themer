package dev.magicthemer

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.HorizontalScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ConcatAdapter
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dev.magicthemer.icons.IconMapping
import dev.magicthemer.icons.IconPack
import dev.magicthemer.icons.IconPackRepository
import dev.magicthemer.icons.PackData
import dev.magicthemer.icons.IconSuggestions
import dev.magicthemer.theme.ThemeArchive
import dev.magicthemer.theme.ThemeDocument
import dev.magicthemer.theme.ThemeEditor
import dev.magicthemer.theme.ThemeIcon
import dev.magicthemer.ui.ThumbnailLoader
import dev.magicthemer.ui.VisualAdapter
import dev.magicthemer.ui.VisualItem
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject

/** Offline icon-pack conversion and copy-based editing of ordinary local themes. */
class MainActivity : AppCompatActivity() {
    private data class Choice(val pack: String, val drawable: String)
    private data class DocumentResult(val requestCode: Int, val resultCode: Int, val uri: String?)
    private data class RenderCandidate(val data: PackData, val mapping: IconMapping)
    private data class AppIconState(val imageKey: String, val status: String, val needsIcon: Boolean = false, val customized: Boolean = false)
    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val repository by lazy { IconPackRepository(this) }
    private val preferences by lazy { getSharedPreferences("builder", MODE_PRIVATE) }
    // Active sessions must survive Android's cache eviction under disk pressure.
    private val themeWorkDir by lazy { File(filesDir, "theme-work").apply {
        require(isDirectory || mkdirs()) { "Could not create theme working storage." }
    } }
    private val loadedPacks = ConcurrentHashMap<String, PackData>()
    private val suggestionIndexes = ConcurrentHashMap<String, IconSuggestions>()
    private val failedIcons = ConcurrentHashMap.newKeySet<String>()
    private val thumbnails by lazy { ThumbnailLoader(::renderThumbnail) }
    private var packs = emptyList<IconPack>()
    private var apps = emptyList<ApplicationTarget>()
    @Volatile private var document: ThemeDocument? = null
    @Volatile private var packData: PackData? = null
    private var appliedPack: PackData? = null
    private val choices = linkedMapOf<String, Choice>()
    private val removedPackages = linkedSetOf<String>()
    private var busy = false
    private var ready = false
    private var initialized = false
    private var deferredDocumentResult: DocumentResult? = null
    private var revision = 0L
    private var extension = "hnt"
    private var pendingExport: String? = null
    private var pendingReport = ""
    private var selectedTab = 0
    private var appFilter = FILTER_ALL
    private lateinit var toolbar: MaterialToolbar
    private lateinit var choosePackButton: MaterialButton
    private lateinit var importButton: MaterialButton
    private lateinit var saveButton: MaterialButton
    private lateinit var applyPackButton: MaterialButton
    private lateinit var sourceIcon: ImageView
    private lateinit var sourceTitle: TextView
    private lateinit var sourceSubtitle: TextView
    private lateinit var status: TextView
    private lateinit var progress: LinearProgressIndicator
    private lateinit var tabs: TabLayout
    private lateinit var pages: List<View>
    private lateinit var appSearch: TextInputEditText
    private lateinit var iconSearch: TextInputEditText
    private lateinit var appSummary: TextView
    private lateinit var appFilters: ChipGroup
    private lateinit var allAppsChip: Chip
    private lateinit var missingAppsChip: Chip
    private lateinit var customizedAppsChip: Chip
    private lateinit var iconSummary: TextView
    private lateinit var appAdapter: VisualAdapter
    private lateinit var iconAdapter: VisualAdapter
    private lateinit var titleInput: TextInputEditText
    private lateinit var designerInput: TextInputEditText
    private lateinit var briefInput: TextInputEditText
    private lateinit var wholePack: MaterialSwitch
    private lateinit var replaceIcons: MaterialSwitch
    private lateinit var replaceNote: TextView
    private lateinit var repairCompatibility: MaterialSwitch
    private lateinit var repairNote: TextView
    private lateinit var formatInput: MaterialAutoCompleteTextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        pendingExport = savedInstanceState?.getString("pendingExport") ?: preferences.getString("pendingExport", null)
        pendingReport = savedInstanceState?.getString("pendingReport") ?: preferences.getString("pendingReport", "").orEmpty()
        selectedTab = savedInstanceState?.getInt("tab") ?: preferences.getInt("tab", 0)
        appFilter = savedInstanceState?.getInt("appFilter") ?: preferences.getInt("appFilter", FILTER_ALL)
        extension = savedInstanceState?.getString("extension") ?: preferences.getString("extension", "hnt") ?: "hnt"
        if (savedInstanceState?.containsKey("documentRequest") == true) {
            deferredDocumentResult = DocumentResult(savedInstanceState.getInt("documentRequest"),
                savedInstanceState.getInt("documentResult"), savedInstanceState.getString("documentUri"))
        }
        buildUi(savedInstanceState)
        ready = true
        val source = savedInstanceState?.getString("source") ?: preferences.getString("source", null)
        val pack = savedInstanceState?.getString("pack") ?: preferences.getString("pack", null)
        val applied = savedInstanceState?.getString("applied") ?: preferences.getString("applied", null)
        initialize(source, pack, applied)
    }

    private fun buildUi(saved: Bundle?) {
        val frame = FrameLayout(this).apply { setBackgroundColor(color(com.google.android.material.R.attr.colorSurface)) }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        frame.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL))
        frame.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) {
                val wanted = minOf(right - left - frame.paddingLeft - frame.paddingRight, dp(1000))
                if (column.layoutParams.width != wanted && wanted > 0) column.layoutParams = (column.layoutParams as FrameLayout.LayoutParams).apply { width = wanted }
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(frame) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }
        toolbar = MaterialToolbar(this).apply {
            title = "Magic Themer"
            subtitle = "Free, offline, ad-free"
            menu.add(0, MENU_NEW, 0, "New theme")
            menu.add(0, MENU_REFRESH, 1, "Refresh packs")
            menu.add(0, MENU_DETAILS, 2, "Pack details")
            menu.add(0, MENU_THEMES, 3, "Open HONOR Themes")
            menu.add(0, MENU_HELP, 4, "Apply help")
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_NEW -> if (!busy) newTheme()
                    MENU_REFRESH -> if (!busy) refreshPacks()
                    MENU_DETAILS -> showPackDetails()
                    MENU_THEMES -> openThemes()
                    MENU_HELP -> showGuide()
                }
                true
            }
        }
        column.addView(toolbar, LinearLayout.LayoutParams(-1, dp(64)))
        val sourceArea = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), dp(8)) }
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        choosePackButton = button("Choose pack", tonal = true) { choosePack() }
        importButton = button("Open .hnt", outlined = true) { openThemePicker() }
        buttons.addView(choosePackButton, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(8) })
        buttons.addView(importButton, LinearLayout.LayoutParams(0, dp(52), 1f))
        sourceArea.addView(buttons)
        val card = MaterialCardView(this).apply {
            cardElevation = 0f; radius = dp(20).toFloat(); strokeWidth = 0
            setCardBackgroundColor(color(com.google.android.material.R.attr.colorSurfaceContainerLow))
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(12), dp(12), dp(12)) }
        sourceIcon = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        row.addView(sourceIcon, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(14) })
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        sourceTitle = label("Choose a source", 16f, bold = true)
        sourceSubtitle = label("An installed pack or an existing HONOR theme", 12f, muted = true).apply { maxLines = 2 }
        labels.addView(sourceTitle); labels.addView(sourceSubtitle)
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(row)
        sourceArea.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        column.addView(sourceArea)
        tabs = TabLayout(this).apply {
            tabMode = TabLayout.MODE_FIXED
            addTab(newTab().setText("Apps")); addTab(newTab().setText("Pack icons")); addTab(newTab().setText("Theme"))
        }
        column.addView(tabs, LinearLayout.LayoutParams(-1, dp(48)))
        val body = FrameLayout(this)
        val appPage = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(10), dp(16), 0) }
        appSearch = searchField(appPage, "Search apps or packages")
        val filterScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER }
        appFilters = ChipGroup(this).apply { isSingleSelection = true; isSelectionRequired = true; isSingleLine = true; chipSpacingHorizontal = dp(8) }
        fun filterChip(value: String, filter: Int): Chip = Chip(this, null, com.google.android.material.R.attr.chipStyle).apply {
            id = View.generateViewId(); text = value; isCheckable = true; isChecked = appFilter == filter
            setOnClickListener { appFilter = filter; updateAppList(); persist() }
        }
        allAppsChip = filterChip("All", FILTER_ALL)
        missingAppsChip = filterChip("Needs icon", FILTER_MISSING)
        customizedAppsChip = filterChip("Customized", FILTER_CUSTOMIZED)
        appFilters.addView(allAppsChip); appFilters.addView(missingAppsChip); appFilters.addView(customizedAppsChip)
        filterScroll.addView(appFilters)
        appPage.addView(filterScroll, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(6) })
        appSummary = label("Finding installed apps…", 12f, muted = true)
        appPage.addView(appSummary, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(4) })
        appAdapter = VisualAdapter(this, thumbnails, false) { item ->
            if (!busy) targets().firstOrNull { it.packageName == item.key }?.let(::chooseIcon)
        }
        appPage.addView(recycler(appAdapter, false), LinearLayout.LayoutParams(-1, 0, 1f))
        val iconPage = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(12), 0) }
        iconSearch = searchField(iconPage, "Search pack icons")
        iconSummary = label("Choose a pack to browse its artwork", 12f, muted = true)
        iconPage.addView(iconSummary, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(4); marginStart = dp(4) })
        iconAdapter = VisualAdapter(this, thumbnails, true) { item ->
            if (!busy) packData?.let { previewIcon(it, item.key) }
        }
        iconPage.addView(recycler(iconAdapter, true), LinearLayout.LayoutParams(-1, 0, 1f))
        val themeScroll = ScrollView(this).apply { isFillViewport = true }
        val themePage = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(20)) }
        themeScroll.addView(themePage)
        titleInput = editorField(themePage, "Theme name", 64, false).apply { setText(saved?.getString("title") ?: preferences.getString("title", "My Magic Icons")) }
        designerInput = editorField(themePage, "Designer", 128, false).apply { setText(saved?.getString("designer") ?: preferences.getString("designer", "Magic Themer")) }
        briefInput = editorField(themePage, "Description", 4096, true).apply { setText(saved?.getString("brief") ?: preferences.getString("brief", "")) }
        repairCompatibility = MaterialSwitch(this).apply {
            text = "Repair theme compatibility"
            isChecked = saved?.getBoolean("repair") ?: preferences.getBoolean("repair", true)
            setOnCheckedChangeListener { _, _ -> if (ready) persist() }
        }
        themePage.addView(repairCompatibility, LinearLayout.LayoutParams(-1, dp(56)))
        repairNote = label("Convert this minimal legacy theme to the experimental HONOR profile; your icons and designer credit are preserved.", 13f, muted = true)
        themePage.addView(repairNote)
        wholePack = MaterialSwitch(this).apply {
            text = "Whole icon pack"
            isChecked = saved?.getBoolean("whole") ?: preferences.getBoolean("whole", true)
            setOnCheckedChangeListener { _, _ -> if (ready) { persist(); refreshUi() } }
        }
        themePage.addView(wholePack, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(8) })
        themePage.addView(label("Include every app mapping, including apps not installed here. Alternative artwork can be assigned individually.", 13f, muted = true))
        applyPackButton = button("Apply whole pack to theme", tonal = true) { applyWholePack() }
        themePage.addView(applyPackButton, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        replaceIcons = MaterialSwitch(this).apply {
            text = "Replace existing icon set"
            isChecked = saved?.getBoolean("replace") ?: preferences.getBoolean("replace", false)
            setOnCheckedChangeListener { _, _ -> if (ready) { persist(); refreshUi() } }
        }
        themePage.addView(replaceIcons, LinearLayout.LayoutParams(-1, dp(56)))
        replaceNote = label("Off: merge the pack and keep uncovered theme icons. On: replace static package icons when a pack is explicitly applied.", 13f, muted = true)
        themePage.addView(replaceNote)
        val formatLayout = TextInputLayout(this, null, com.google.android.material.R.attr.textInputStyle).apply {
            hint = "Theme format"; endIconMode = TextInputLayout.END_ICON_DROPDOWN_MENU
        }
        formatInput = MaterialAutoCompleteTextView(formatLayout.context).apply {
            inputType = 0
            setAdapter(ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line, FORMATS))
            setText(FORMATS[if (extension == "hwt") 1 else 0], false)
            setOnItemClickListener { _, _, index, _ -> extension = if (index == 1) "hwt" else "hnt"; if (ready) persist() }
        }
        formatLayout.addView(formatInput)
        themePage.addView(formatLayout, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
        themePage.addView(label("Static icons • 192 px PNG • saved as a new file", 13f, muted = true), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        pages = listOf(appPage, iconPage, themeScroll)
        pages.forEach { body.addView(it, FrameLayout.LayoutParams(-1, -1)) }
        column.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8))
            setBackgroundColor(color(com.google.android.material.R.attr.colorSurfaceContainerLow))
        }
        progress = LinearProgressIndicator(this).apply { isIndeterminate = true; visibility = View.GONE }
        footer.addView(progress, LinearLayout.LayoutParams(-1, dp(4)))
        status = label("Choose a pack or open a theme to begin.", 12f, muted = true).apply {
            maxLines = 2; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        footer.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6); bottomMargin = dp(4) })
        saveButton = button("Save theme") { exportTheme() }
        footer.addView(saveButton, LinearLayout.LayoutParams(-1, dp(52)))
        column.addView(footer)
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                selectedTab = tab.position; showPage()
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(tabs.windowToken, 0)
                if (ready) persist()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        tabs.getTabAt(selectedTab.coerceIn(0, 2))?.select(); showPage()
        watchSearch(appSearch, ::updateAppList); watchSearch(iconSearch, ::updateIconList)
        listOf(titleInput, designerInput, briefInput).forEach { input -> watchText(input) { if (ready) persist() } }
        setContentView(frame)
        ViewCompat.requestApplyInsets(frame)
        updateControls()
    }

    private fun initialize(sourceName: String?, selectedPackage: String?, appliedPackage: String?) {
        setBusy(true, "Reading local icon packs and apps…")
        val pendingName = pendingExport
        background {
            val restoredSource = cachedImport(sourceName)
            val restoredExport = workingExport(pendingName)
            cleanupWorkingFiles(setOfNotNull(restoredSource, restoredExport))
            val found = repository.discover()
            val installed = discoverApps()
            var warning = if (sourceName != null && restoredSource == null) "The working theme is unavailable. Open the original .hnt file again." else ""
            val restored = restoredSource?.let { file ->
                runCatching { ThemeEditor.open(file) }.getOrElse { warning = it.message ?: "Could not restore the theme."; null }
            }
            val selected = found.firstOrNull { it.packageName == selectedPackage }?.let {
                runCatching { repository.load(it) }.getOrElse { warning = it.message ?: "Could not restore this pack."; null }
            }
            selected?.let(::cachePack)
            val applied = if (restored == null || appliedPackage == null) null else {
                selected?.takeIf { it.pack.packageName == appliedPackage }
                    ?: found.firstOrNull { it.packageName == appliedPackage }?.let { runCatching { repository.load(it) }.getOrNull() }
            }
            applied?.let(::cachePack)
            ui(onDiscard = { restored?.let(ThemeEditor::close) }) {
                packs = found; apps = installed; document = restored; packData = selected; appliedPack = applied
                pendingExport = restoredExport?.absolutePath
                if (sourceName != null && restored != null && sourceName != restored.sourceFile.name) migrateSession(sourceName, restored.sourceFile.name)
                initialized = true
                restoreSession()
                persist()
                setBusy(false, warning.ifBlank {
                    if (restored != null) "Theme opened. Edit its icons or metadata, then save a copy."
                    else if (selected != null) "Pack ready. Tap an app to change its icon."
                    else "Choose a pack or open an existing .hnt theme."
                })
                refreshUi()
                deferredDocumentResult?.let { result -> deferredDocumentResult = null; handleDocumentResult(result) }
            }
        }
    }

    private fun discoverApps(): List<ApplicationTarget> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val rows = if (Build.VERSION.SDK_INT >= 33) packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        else { @Suppress("DEPRECATION") packageManager.queryIntentActivities(intent, 0) }
        return rows.filter { it.activityInfo?.enabled == true && it.activityInfo.applicationInfo.enabled }
            .groupBy { it.activityInfo.packageName }.map { (pkg, matches) ->
                ApplicationTarget(pkg, matches.first().activityInfo.applicationInfo.loadLabel(packageManager).toString(), matches.map { it.activityInfo.name }.distinct())
            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    private fun refreshPacks() {
        val previous = packData?.pack?.packageName
        val previousApplied = appliedPack?.pack?.packageName
        val referenced = choices.values.mapTo(hashSetOf()) { it.pack }.apply {
            previous?.let(::add); previousApplied?.let(::add)
        }
        setBusy(true, "Refreshing installed packs…")
        background {
            repository.clearResourceCache()
            val found = repository.discover(); val installed = discoverApps()
            val refreshed = found.filter { it.packageName in referenced }.associate { pack ->
                val data = repository.load(pack)
                cachePack(data)
                pack.packageName to data
            }
            ui {
                failedIcons.clear()
                loadedPacks.keys.removeIf { it !in refreshed }
                suggestionIndexes.keys.removeIf { it !in refreshed }
                packs = found; apps = installed; packData = previous?.let(refreshed::get)
                appliedPack = previousApplied?.let(refreshed::get)
                choices.entries.removeAll { (_, choice) -> found.none { it.packageName == choice.pack } }
                thumbnails.clear(); ++revision
                persist(); saveSession(); refreshUi(forceRebind = true)
                setBusy(false, "${found.size} installed pack(s) found.")
            }
        }
    }

    private fun choosePack() {
        if (packs.isEmpty()) {
            MaterialAlertDialogBuilder(this).setTitle("Install an icon pack")
                .setMessage("Install a compatible icon pack from Google Play, then use Refresh packs. Packs expose their public appfilter.xml and drawable catalogue; no paid content is downloaded or unlocked.")
                .setPositiveButton("Refresh") { _, _ -> refreshPacks() }.setNegativeButton("Close", null).show()
            return
        }
        visualSheet("Choose icon pack", "Installed packs on this device", false,
            items = { query -> packs.filter { matches(query, it.label, it.packageName) }.map { VisualItem(it.packageName, it.label, it.packageName, "app:${it.packageName}") } },
            onSelect = { item -> packs.firstOrNull { it.packageName == item.key }?.let(::loadPack) })
    }

    private fun loadPack(pack: IconPack) {
        saveSession()
        setBusy(true, "Reading ${pack.label}…")
        background {
            val data = repository.load(pack)
            cachePack(data)
            ui {
                failedIcons.removeIf { it.startsWith("pack:${pack.packageName}:") }
                packData = data
                if (document == null) restoreSession()
                persist(); refreshUi()
                setBusy(false, if (document == null) "${data.drawables.size} icons ready to browse." else "Pack loaded. Apply it explicitly or choose individual icons.")
            }
        }
    }

    private fun applyWholePack() {
        val data = packData ?: return
        if (document == null) return
        appliedPack = data
        saveSession(); persist(); refreshUi()
        status.text = "${data.pack.label} will ${if (replaceIcons.isChecked) "replace" else "merge with"} this theme’s static icon set."
    }

    private fun targets(): List<ApplicationTarget> {
        val installed = apps.associateBy { it.packageName }
        val extra = document?.iconEntries.orEmpty().keys.filter { it !in installed }
            .map { ApplicationTarget(it, it.substringAfterLast('.').replaceFirstChar { c -> c.uppercase() }, emptyList()) }
        return (apps + extra).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    private fun defaultMappings(): Map<String, IconMapping> {
        val data = if (document == null) packData else appliedPack
        return data?.let { ThemeMappingSelector.select(it.mappings, apps, wholePack.isChecked).associateBy { mapping -> mapping.packageName } }.orEmpty()
    }

    private fun appIconState(packageName: String, defaults: Map<String, IconMapping>): AppIconState {
        val original = "app:$packageName"
        if (packageName in removedPackages) return AppIconState(original, "Needs icon · using original", needsIcon = true)
        choices[packageName]?.let { choice ->
            val data = loadedPacks[choice.pack]
            val key = "pack:${choice.pack}:${choice.drawable}"
            return if (data?.isDrawableAvailable(choice.drawable) == true && key !in failedIcons)
                AppIconState(key, "Customized", customized = true)
            else unavailablePackIconState(packageName)
        }
        val defaultData = if (document == null) packData else appliedPack
        defaults[packageName]?.let { mapping ->
            val key = "pack:${defaultData!!.pack.packageName}:${mapping.drawableName}"
            if (defaultData.isDrawableAvailable(mapping.drawableName) && key !in failedIcons)
                return AppIconState(key, "Themed · pack")
            return unavailablePackIconState(packageName)
        }
        return preservedImportedIconState(packageName) ?: AppIconState(original, "Needs icon", needsIcon = true)
    }

    private fun unavailablePackIconState(packageName: String): AppIconState =
        preservedImportedIconState(packageName, replacementUnavailable = true)
            ?: AppIconState("app:$packageName", "Needs icon · artwork unavailable", needsIcon = true)

    private fun preservedImportedIconState(packageName: String, replacementUnavailable: Boolean = false): AppIconState? {
        val source = document
        if (source != null && packageName in source.iconEntries && !(replaceIcons.isChecked && appliedPack != null)) {
            val key = "theme:${source.sourceFile.name}:$packageName"
            return if (key !in failedIcons) AppIconState(key, "Themed · imported" + if (replacementUnavailable) "\nReplacement unavailable" else "")
            else AppIconState("app:$packageName", "Needs icon · image cannot be read", needsIcon = true)
        }
        return null
    }

    private fun updateAppList() {
        if (!::appAdapter.isInitialized) return
        val query = appSearch.text.toString().trim()
        val defaults = defaultMappings()
        val all = targets()
        val states = all.associate { it.packageName to appIconState(it.packageName, defaults) }
        val missing = states.values.count { it.needsIcon }
        val customized = states.values.count { it.customized }
        allAppsChip.text = "All (${all.size})"
        missingAppsChip.text = "Needs icon ($missing)"
        customizedAppsChip.text = "Customized ($customized)"
        appFilters.check(when (appFilter) { FILTER_MISSING -> missingAppsChip.id; FILTER_CUSTOMIZED -> customizedAppsChip.id; else -> allAppsChip.id })
        val visible = all.filter {
            val state = states.getValue(it.packageName)
            matches(query, it.label, it.packageName) && when (appFilter) {
                FILTER_MISSING -> state.needsIcon
                FILTER_CUSTOMIZED -> state.customized
                else -> true
            }
        }
        appAdapter.submit(visible.map { target ->
            val state = states.getValue(target.packageName)
            VisualItem(target.packageName, target.label, target.packageName, "app:${target.packageName}",
                state.imageKey.takeUnless { it == "app:${target.packageName}" }, state.status, state.needsIcon)
        })
        val themed = apps.count { states[it.packageName]?.needsIcon == false }
        appSummary.text = when {
            visible.isEmpty() && query.isBlank() && appFilter == FILTER_MISSING -> "Every app has a theme icon."
            visible.isEmpty() -> "No apps match this filter."
            query.isNotBlank() -> "${visible.size} app(s) match · original → chosen icon"
            else -> "$themed of ${apps.size} installed apps themed · original → chosen icon"
        }
    }

    private fun updateIconList() {
        if (!::iconAdapter.isInitialized) return
        val data = packData
        val query = iconSearch.text.toString().trim()
        val names = data?.let(::availableIconNames).orEmpty().filter { matches(query, it, friendlyName(it)) }
        iconAdapter.submit(names.map { VisualItem(it, friendlyName(it), "", "pack:${data!!.pack.packageName}:$it") })
        iconSummary.text = if (data == null) "Choose a pack to browse its artwork" else "${names.size} of ${data.drawables.size} icons · alternatives included"
    }

    private fun refreshUi(forceRebind: Boolean = false) {
        val source = document
        val data = packData
        toolbar.subtitle = if (source == null) "Free, offline, ad-free" else "Editing a theme copy"
        sourceTitle.text = source?.title?.ifBlank { "Imported theme" } ?: data?.pack?.label ?: "Choose a source"
        sourceSubtitle.text = when {
            source != null -> "${source.iconEntries.size} theme icons" + (data?.let { " · ${it.pack.label} selected" } ?: " · original file preserved")
            data != null -> "${data.drawables.size} icons · ${data.mappings.map { it.packageName }.distinct().size} app mappings"
            else -> "An installed pack or an existing HONOR theme"
        }
        if (data != null) thumbnails.bind(sourceIcon, "app:${data.pack.packageName}", 128)
        else if (source?.iconEntries?.isNotEmpty() == true) thumbnails.bind(sourceIcon, "theme:${source.sourceFile.name}:${source.iconEntries.keys.first()}", 128)
        else { thumbnails.unbind(sourceIcon); sourceIcon.setImageResource(android.R.drawable.ic_menu_gallery) }
        applyPackButton.visibility = if (source == null) View.GONE else View.VISIBLE
        replaceIcons.visibility = if (source == null) View.GONE else View.VISIBLE
        replaceNote.visibility = if (source == null) View.GONE else View.VISIBLE
        val repairable = source?.compatibilityRepairAvailable == true
        repairCompatibility.visibility = if (repairable) View.VISIBLE else View.GONE
        repairNote.visibility = if (repairable) View.VISIBLE else View.GONE
        applyPackButton.text = if (data != null && appliedPack?.pack?.packageName == data.pack.packageName) "Pack applied · apply again" else "Apply whole pack to theme"
        saveButton.text = if (source == null) "Save theme" else "Save theme copy"
        updateAppList(); updateIconList()
        if (forceRebind) { appAdapter.notifyDataSetChanged(); iconAdapter.notifyDataSetChanged() }
        updateControls()
    }

    private fun chooseIcon(target: ApplicationTarget) {
        val data = packData
        if (data == null) {
            MaterialAlertDialogBuilder(this).setTitle("Icon for ${target.label}")
                .setMessage("Choose an icon pack to browse replacements. You can also remove this theme’s custom icon and use the original app icon.")
                .setPositiveButton("Choose pack") { _, _ -> choosePack() }
                .setNeutralButton("Use original") { _, _ -> useOriginal(target.packageName) }
                .setNegativeButton("Close", null).show()
            return
        }
        visualSheet("Icon for ${target.label}", target.packageName, true,
            items = { query -> availableIconNames(data).filter { matches(query, it, friendlyName(it)) }.map { VisualItem(it, friendlyName(it), "", "pack:${data.pack.packageName}:$it") } },
            suggested = { query ->
                suggestionIndexes[data.pack.packageName]?.suggest(target).orEmpty()
                    .filter { it.drawableName !in failedPackNames(data) && matches(query, it.drawableName, it.label) }
                    .map { VisualItem(it.drawableName, it.label, it.reason, "pack:${data.pack.packageName}:${it.drawableName}") }
            },
            actions = listOf("Use original" to { useOriginal(target.packageName) }, "Reset" to {
                choices.remove(target.packageName); removedPackages.remove(target.packageName); saveSession(); refreshUi(); status.text = "Icon reset for ${target.label}."
            }),
            onSelect = { item -> assign(target, data, item.key) })
    }

    private fun useOriginal(packageName: String) {
        choices.remove(packageName); removedPackages.add(packageName)
        saveSession(); refreshUi(); status.text = "The original app icon will be used."
    }

    private fun assign(target: ApplicationTarget, data: PackData, drawable: String) {
        choices[target.packageName] = Choice(data.pack.packageName, drawable)
        removedPackages.remove(target.packageName)
        saveSession(); refreshUi(); status.text = "Icon selected for ${target.label}."
    }

    private fun previewIcon(data: PackData, drawable: String) {
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(24), dp(16), dp(24), dp(12)) }
        val image = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; contentDescription = friendlyName(drawable) }
        panel.addView(image, LinearLayout.LayoutParams(dp(144), dp(144)))
        panel.addView(label(drawable, 13f, muted = true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        thumbnails.bind(image, "pack:${data.pack.packageName}:$drawable", 256)
        val dialog = MaterialAlertDialogBuilder(this).setTitle(friendlyName(drawable)).setView(panel)
            .setPositiveButton("Use for app") { _, _ -> chooseTarget(data, drawable) }.setNegativeButton("Close", null).create()
        dialog.setOnDismissListener { thumbnails.unbind(image) }
        dialog.show()
    }

    private fun chooseTarget(data: PackData, drawable: String) {
        val defaults = defaultMappings()
        visualSheet("Choose an app", "Use ${friendlyName(drawable)} for…", false,
            items = { query -> targets().filter { matches(query, it.label, it.packageName) }
                .sortedWith(compareBy<ApplicationTarget> { !appIconState(it.packageName, defaults).needsIcon }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.label })
                .map { target -> VisualItem(target.packageName, target.label, target.packageName, "app:${target.packageName}", "pack:${data.pack.packageName}:$drawable",
                    appIconState(target.packageName, defaults).status, appIconState(target.packageName, defaults).needsIcon) } },
            onSelect = { item -> targets().firstOrNull { it.packageName == item.key }?.let { assign(it, data, drawable) } })
    }

    private fun failedPackNames(data: PackData): Set<String> {
        val prefix = "pack:${data.pack.packageName}:"
        return failedIcons.filter { it.startsWith(prefix) }.mapTo(hashSetOf()) { it.removePrefix(prefix) }
    }

    private fun availableIconNames(data: PackData): List<String> {
        val failed = failedPackNames(data)
        return data.drawables.filter { data.isDrawableAvailable(it) && it !in failed }
    }

    private fun visualSheet(
        title: String,
        subtitle: String,
        grid: Boolean,
        items: (String) -> List<VisualItem>,
        suggested: ((String) -> List<VisualItem>)? = null,
        actions: List<Pair<String, () -> Unit>> = emptyList(),
        onSelect: (VisualItem) -> Unit,
    ) {
        val dialog = BottomSheetDialog(this)
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(18), dp(16), dp(12)) }
        panel.addView(label(title, 22f, bold = true))
        panel.addView(label(subtitle, 12f, muted = true), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(12) })
        val search = searchField(panel, if (grid) "Search icons" else "Search names or packages")
        val adapter = VisualAdapter(this, thumbnails, grid) { item ->
            if (!busy) { dialog.dismiss(); onSelect(item) }
        }
        val suggestedAdapter = suggested?.let { VisualAdapter(this, thumbnails, grid) { item -> if (!busy) { dialog.dismiss(); onSelect(item) } } }
        val suggestedHeader = suggested?.let { SheetSectionHeader("Suggested icons") }
        val catalogueHeader = suggested?.let { SheetSectionHeader("All pack icons") }
        val contentAdapter = if (suggestedAdapter == null) adapter else ConcatAdapter(suggestedHeader!!, suggestedAdapter, catalogueHeader!!, adapter)
        val list = recycler(contentAdapter, grid)
        if (suggestedAdapter != null && grid) (list.layoutManager as GridLayoutManager).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int = if (position == 0 || position == suggestedAdapter.itemCount + 1) spanCount else 1
            }
        }
        panel.addView(list, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        actions.forEach { (text, action) -> controls.addView(button(text, outlined = true) { dialog.dismiss(); if (!busy) action() }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(6) }) }
        controls.addView(button("Close", tonal = true) { dialog.dismiss() }, LinearLayout.LayoutParams(if (actions.isEmpty()) -2 else 0, dp(52), if (actions.isEmpty()) 0f else 1f))
        panel.addView(controls, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        fun updateResults(query: String) {
            val matches = items(query)
            val recommendations = suggested?.invoke(query).orEmpty()
            suggestedAdapter?.submit(recommendations)
            suggestedHeader?.update(if (recommendations.isEmpty()) "No close suggestions · browse all icons below" else "Suggested icons (${recommendations.size}) · tap to choose")
            catalogueHeader?.update("All pack icons (${matches.size})")
            adapter.submit(matches)
        }
        updateResults("")
        watchSearch(search) { updateResults(search.text.toString().trim()) }
        dialog.setContentView(panel)
        dialog.setOnDismissListener { list.adapter = null }
        dialog.show()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
            sheet.layoutParams = sheet.layoutParams.apply { height = (resources.displayMetrics.heightPixels * 0.9f).toInt() }
            BottomSheetBehavior.from(sheet).apply { skipCollapsed = true; state = BottomSheetBehavior.STATE_EXPANDED }
        }
    }

    private inner class SheetSectionHeader(private var value: String) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount(): Int = 1
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            object : RecyclerView.ViewHolder(label("", 14f, muted = true, bold = true).apply {
                setPadding(dp(4), dp(14), dp(4), dp(10)); layoutParams = RecyclerView.LayoutParams(-1, -2)
                ViewCompat.setAccessibilityHeading(this, true)
            }) {}
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) { (holder.itemView as TextView).text = value }
        fun update(text: String) { value = text; notifyItemChanged(0) }
    }

    private fun openThemePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, OPEN_THEME)
    }

    private fun importTheme(uri: android.net.Uri) {
        setBusy(true, "Opening and validating theme…")
        background {
            val copy = File.createTempFile("imported-", ".hnt", themeWorkDir)
            var opened: ThemeDocument? = null
            try {
                val input = contentResolver.openInputStream(uri) ?: error("This file cannot be read.")
                input.use { source -> copy.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024); var total = 0L
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw InterruptedException()
                        val count = source.read(buffer); if (count < 0) break
                        total += count; require(total <= ThemeEditor.MAX_ARCHIVE_BYTES) { "Theme exceeds 256 MiB." }
                        output.write(buffer, 0, count)
                    }
                } }
                val imported = ThemeEditor.open(copy)
                opened = imported
                val name = runCatching { contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else "" } }.getOrNull().orEmpty()
                ui(onDiscard = { ThemeEditor.close(imported); copy.delete() }) {
                    saveSession()
                    val old = document
                    document = imported; appliedPack = null; choices.clear(); removedPackages.clear()
                    ready = false
                    titleInput.setText(imported.title.ifBlank { "My Magic Theme" }); designerInput.setText(imported.designer); briefInput.setText(imported.briefInfo)
                    replaceIcons.isChecked = false
                    repairCompatibility.isChecked = true
                    extension = if (name.endsWith(".hwt", true)) "hwt" else "hnt"
                    formatInput.setText(FORMATS[if (extension == "hwt") 1 else 0], false)
                    ready = true
                    old?.let { ThemeEditor.close(it); if (it.sourceFile != copy) it.sourceFile.delete() }
                    saveSession(); persist(); refreshUi()
                    setBusy(false, "Theme opened. Original file preserved; saving creates a new copy.")
                    if (imported.warnings.isNotEmpty()) showThemeDetails()
                }
            } catch (failure: Exception) {
                opened?.let(ThemeEditor::close); copy.delete(); throw failure
            }
        }
    }

    private fun newTheme() {
        val old = document
        document = null; appliedPack = null; choices.clear(); removedPackages.clear()
        old?.let { ThemeEditor.close(it); it.sourceFile.delete() }
        ready = false
        titleInput.setText("My Magic Icons"); designerInput.setText("Magic Themer"); briefInput.setText("")
        wholePack.isChecked = true; replaceIcons.isChecked = false
        repairCompatibility.isChecked = true
        ready = true
        saveSession(); persist(); refreshUi()
        status.text = "New icon theme. Choose a pack, then save."
    }

    private fun exportCandidates(
        data: PackData?, assignments: Map<String, Choice>, exportApps: List<ApplicationTarget>,
        exportPacks: List<IconPack>, includeAll: Boolean, imported: Boolean, removed: Set<String>,
    ): List<RenderCandidate> {
        val selected = linkedMapOf<String, RenderCandidate>()
        data?.let { source ->
            ThemeMappingSelector.select(source.mappings, exportApps, includeAll).forEach { selected[it.packageName] = RenderCandidate(source, it) }
        }
        val installed = exportApps.mapTo(hashSetOf()) { it.packageName }
        assignments.forEach { (pkg, choice) ->
            if (imported || includeAll || pkg in installed) {
                val source = loadedPacks[choice.pack]
                    ?: exportPacks.firstOrNull { it.packageName == choice.pack }?.let { repository.load(it).also(::cachePack) }
                    ?: error("The icon pack for $pkg is no longer installed.")
                val activity = exportApps.firstOrNull { it.packageName == pkg }?.activities?.firstOrNull().orEmpty()
                selected[pkg] = RenderCandidate(source, IconMapping("ComponentInfo{$pkg/$activity}", pkg, activity, choice.drawable))
            }
        }
        removed.forEach(selected::remove)
        require(selected.size <= ThemeArchive.MAX_ICON_COUNT) { "This selection exceeds 20,000 apps. Turn Whole icon pack off to export installed apps only." }
        return selected.values.toList()
    }

    private fun exportTheme() {
        val title = titleInput.text.toString().trim()
        if (title.isBlank()) { titleInput.error = "Enter a theme name"; tabs.getTabAt(2)?.select(); return }
        val source = document
        val designer = designerInput.text.toString()
        val brief = briefInput.text.toString()
        val format = extension
        val removed = removedPackages.toSet()
        val replaceAll = source != null && appliedPack != null && replaceIcons.isChecked
        val repair = source?.compatibilityRepairAvailable == true && repairCompatibility.isChecked
        val base = if (source == null) packData else appliedPack
        val assignments = choices.toMap()
        val exportApps = apps.toList()
        val exportPacks = packs.toList()
        val includeAll = wholePack.isChecked
        val sourceLabel = packData?.pack?.label ?: "Selected icons"
        setBusy(true, "Preparing theme…")
        background {
            val candidates = exportCandidates(base, assignments, exportApps, exportPacks, includeAll, source != null, removed)
            require(source != null || candidates.isNotEmpty()) { "No app icons selected. Assign icons manually or select a pack with app mappings." }
            val replacements = linkedMapOf<String, ByteArray>()
            val previewIcons = ArrayList<Bitmap>()
            var missing = 0; var bytes = 0L
            val output = File.createTempFile("export-", ".$format", themeWorkDir)
            try {
                candidates.forEachIndexed { index, candidate ->
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                    val bitmap = repository.render(candidate.data, candidate.mapping, 192)
                    if (bitmap == null) missing++ else try {
                        val png = ByteArrayOutputStream().use { buffer ->
                            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, buffer)) { "Could not encode an icon." }; buffer.toByteArray()
                        }
                        require(png.size <= ThemeArchive.MAX_ICON_BYTES) { "An icon exceeds 256 KiB." }
                        bytes += png.size; require(bytes <= ThemeArchive.MAX_TOTAL_ICON_BYTES) { "Rendered icons exceed 64 MiB. Turn Whole icon pack off." }
                        replacements[candidate.mapping.packageName] = png
                        if ((source == null || repair) && previewIcons.size < 12) previewIcons += Bitmap.createScaledBitmap(bitmap, 80, 80, true)
                    } finally { bitmap.recycle() }
                    if (index % 20 == 0) ui { status.text = "Rendering ${index + 1} / ${candidates.size} app icons…" }
                }
                if (candidates.isNotEmpty() && (source == null || replaceAll)) require(replacements.isNotEmpty()) { "None of the selected icons could be read. Try another icon pack." }
                if (source != null && !replaceAll && repair && previewIcons.size < 12) {
                    source.iconEntries.keys.filter { it !in replacements && it !in removed }.take(12 - previewIcons.size).forEach { pkg ->
                        ThemeEditor.readIcon(source, pkg)?.let { png -> decodeThemePng(png, 80)?.let(previewIcons::add) }
                    }
                }
                // Keep an imported working theme's authored previews intact.
                val preview = if (source == null || repair) createPreview(title, previewIcons, 720, 1280) else null
                val iconsPreview = if (source == null || repair) createPreview(title, previewIcons, 720, 1280) else null
                output.outputStream().use { stream ->
                    if (source == null) ThemeArchive.write(
                        stream, title, sourceLabel, replacements.map { ThemeIcon(it.key, it.value) }, format,
                        preview = preview, iconsPreview = iconsPreview, tempDirectory = themeWorkDir, designer = designer, briefInfo = brief,
                    ) else ThemeEditor.write(source, stream, title, designer, brief, replacements, removed, replaceAll, preview, iconsPreview,
                        upgradeMinimalProfile = repair)
                }
                val count = if (source == null || replaceAll) replacements.size else (source.iconEntries.keys - removed + replacements.keys).size
                val report = "$count static icon(s) saved" + if (missing > 0) "; $missing missing drawable(s) skipped." else "."
                ui(onDiscard = { output.delete() }) {
                    pendingExport?.let(::File)?.takeIf(::isWorkingExport)?.delete()
                    pendingExport = output.absolutePath; pendingReport = report
                    persist(); setBusy(false, "$report Choose where to save the new file.")
                    val safeName = title.replace(Regex("[^\\p{L}\\p{N} _-]"), "_").take(64).ifBlank { "Magic Theme" }
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE); type = "application/octet-stream"
                        putExtra(Intent.EXTRA_TITLE, safeName + if (source == null) ".$format" else "-edited.$format")
                    }
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, SAVE_THEME)
                }
            } catch (failure: Exception) { output.delete(); throw failure }
            finally { previewIcons.forEach { it.recycle() } }
        }
    }

    private fun createPreview(title: String, icons: List<Bitmap>, width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap); canvas.drawColor(Color.rgb(239, 244, 240))
            val ratio = width / 510f
            canvas.scale(ratio, ratio)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(31, 67, 55); textSize = 22f; isFakeBoldText = true }
            canvas.drawText(title.take(32), 24f, 36f, paint)
            val rowGap = if (height > width) 150 else 94
            icons.take(12).forEachIndexed { index, icon ->
                val left = 29 + index % 4 * 122; val top = 54 + index / 4 * rowGap
                canvas.drawBitmap(icon, null, Rect(left, top, left + 80, top + 80), null)
            }
            return ByteArrayOutputStream().use { stream -> check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)); stream.toByteArray() }
        } finally { bitmap.recycle() }
    }

    @Deprecated("Platform document picker callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val result = DocumentResult(requestCode, resultCode, data?.data?.toString())
        if (!initialized && requestCode in setOf(OPEN_THEME, SAVE_THEME)) {
            deferredDocumentResult = result
            return
        }
        handleDocumentResult(result)
    }

    private fun handleDocumentResult(result: DocumentResult) {
        val requestCode = result.requestCode
        val resultCode = result.resultCode
        val uri = result.uri?.let(android.net.Uri::parse)
        if (requestCode == OPEN_THEME) {
            if (resultCode == Activity.RESULT_OK && uri != null) importTheme(uri)
            return
        }
        if (requestCode != SAVE_THEME) return
        if (resultCode != Activity.RESULT_OK || uri == null) { status.text = "Save cancelled. Your edits are still available."; return }
        val file = pendingExport?.let(::File)
        if (file == null || !isWorkingExport(file) || !file.isFile) {
            pendingExport = null; persist()
            status.text = "The working export is unavailable. Save your theme again."
            return
        }
        setBusy(true, "Saving new theme file…")
        background {
            val expectedBytes = file.length()
            var expectedHash = byteArrayOf()
            var verifiedName: String? = null
            try {
                require(expectedBytes > 0) { "The working export is empty. Save your theme again." }
                expectedHash = file.inputStream().use { verifiedHash(it, expectedBytes) }
                val stream = contentResolver.openOutputStream(uri, "w") ?: error("This location cannot be written.")
                val writtenBytes = stream.use { output -> file.inputStream().use { it.copyTo(output, 32 * 1024) } }
                require(writtenBytes == expectedBytes) { "The saved file has an unexpected length." }
                val savedInput = contentResolver.openInputStream(uri) ?: error("This location does not allow verification of the saved file.")
                val savedHash = savedInput.use { verifiedHash(it, expectedBytes) }
                require(MessageDigest.isEqual(expectedHash, savedHash)) { "The saved file differs from the working export." }
                verifiedName = runCatching {
                    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
                }.getOrNull()
            } catch (failure: Exception) {
                // This URI was just created by ACTION_CREATE_DOCUMENT; the
                // imported source and private working export remain untouched.
                val removed = runCatching { DocumentsContract.deleteDocument(contentResolver, uri) }.getOrDefault(false)
                val cleanup = if (removed) " The unverified destination was removed." else " The destination could not be verified; remove it before retrying."
                throw IllegalStateException("Save verification failed: ${failure.message ?: "the saved file could not be read"}. Working export retained; save again.$cleanup", failure)
            }
            val filename = verifiedName?.takeIf { it.isNotBlank() }
            val suffix = ".${file.extension}"
            val filenameWarning = when {
                filename == null -> "The provider did not report the filename. Check that the saved name ends in $suffix."
                !filename.endsWith(suffix, ignoreCase = true) -> "Warning: the provider saved an unexpected extension. Rename $filename to end in $suffix before opening HONOR Themes."
                else -> ""
            }
            val hashLabel = expectedHash.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
            val verifiedReport = "$pendingReport\n\nFile: ${filename ?: "name unavailable"}\n$expectedBytes bytes verified\nSHA-256: $hashLabel" +
                if (filenameWarning.isBlank()) "" else "\n\n$filenameWarning"
            ui {
                file.delete(); pendingExport = null; persist()
                setBusy(false, "$pendingReport Verified ${filename ?: "saved file"} ($expectedBytes bytes).")
                MaterialAlertDialogBuilder(this).setTitle("Theme saved")
                    .setMessage("$verifiedReport\n\nSave or copy it to internal storage / HONOR / Themes, then open HONOR Themes → Me → My downloads → Customize → Icons. File verification checks the saved bytes; discovery and compatibility still require checking on your phone.")
                    .setPositiveButton("Open Themes") { _, _ -> openThemes() }.setNegativeButton("Done", null).show()
            }
        }
    }

    /** Read at most the known working export's size, rejecting extra/missing bytes. */
    private fun verifiedHash(input: InputStream, expectedBytes: Long): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(32 * 1024)
        var total = 0L
        while (true) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            val count = input.read(buffer); if (count < 0) break
            total += count
            require(total <= expectedBytes) { "The saved file is longer than expected." }
            digest.update(buffer, 0, count)
        }
        require(total == expectedBytes) { "The saved file is shorter than expected ($total of $expectedBytes bytes)." }
        return digest.digest()
    }

    private fun renderThumbnail(key: String, size: Int): Bitmap? {
        val parts = key.split(':', limit = 3)
        val image = when (parts.firstOrNull()) {
            "pack" -> if (parts.size == 3) loadedPacks[parts[1]]?.let { repository.renderDrawable(it, parts[2], size) } else null
            "theme" -> {
                val source = document
                if (parts.size == 3 && source?.sourceFile?.name == parts[1]) ThemeEditor.readIcon(source, parts[2])?.let { decodeThemePng(it, size) } else null
            }
            "app" -> if (parts.size >= 2) runCatching {
                val drawable = packageManager.getApplicationIcon(parts[1]).mutate()
                Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap -> drawable.setBounds(0, 0, size, size); drawable.draw(Canvas(bitmap)) }
            }.getOrNull() else null
            else -> null
        }
        // Resource lookup is cheap and used for all rows. Actual decode failures
        // are learned only as images become visible, without decoding the pack.
        if (image == null && (key.startsWith("pack:") || key.startsWith("theme:")) && failedIcons.add(key)) {
            ui { if (ready) { updateAppList(); updateIconList() } }
        }
        return image
    }

    private fun decodeThemePng(bytes: ByteArray, size: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..4096 || bounds.outHeight !in 1..4096) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= size) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        return if (decoded.width == size && decoded.height == size) decoded else {
            Bitmap.createScaledBitmap(decoded, size, size, true).also { decoded.recycle() }
        }
    }

    private fun showPackDetails() {
        val data = packData
        if (data == null) { showThemeDetails(); return }
        val mappings = data.mappings.map { it.packageName }.distinct().size
        MaterialAlertDialogBuilder(this).setTitle(data.pack.label).setMessage(
            "${data.pack.packageName}\n\n${data.drawables.size} catalogue icons · $mappings app mappings\n\n" +
                data.warnings.ifEmpty { listOf("No parsing warnings.") }.joinToString("\n\n") +
                "\n\nWhole icon pack exports every known app mapping. Alternative and unmapped artwork can be assigned manually. Fallback masks and animated calendars or clocks are not reproduced.",
        ).setPositiveButton("Close", null).show()
    }

    private fun showThemeDetails() {
        val source = document ?: run { showGuide(); return }
        MaterialAlertDialogBuilder(this).setTitle("Imported theme").setMessage(
            "${source.title}\n${source.iconEntries.size} static package icons\n\n" + source.warnings.ifEmpty { listOf("Other theme modules and unknown configuration are preserved when saving a copy.") }.joinToString("\n\n"),
        ).setPositiveButton("Close", null).show()
    }

    private fun showGuide() {
        MaterialAlertDialogBuilder(this).setTitle("Apply your theme").setMessage(
            "1. Save or copy the exported file to internal storage / HONOR / Themes.\n\n2. Open HONOR Themes → Me → My downloads.\n\n3. Choose Customize → Icons and select your theme. Menu labels can vary by region.\n\nNew exports follow an experimental profile based on a working HONOR theme. Applying exported or converted files on MagicOS 10.0.0.168 EU still needs phone verification.\n\nImported ordinary ZIP themes are saved as a new copy. Static package PNGs and title, designer, and description can be edited; other modules and authored previews are preserved. Selecting a pack does not alter an imported theme until you apply the pack or assign an icon. Minimal legacy icon themes can use Repair theme compatibility when saving a copy; icons and designer credit are preserved.\n\nUse Needs icon to find apps without usable artwork. Suggested icons are local name and app matches: review and tap to assign them.\n\nUse exports personally; artwork remains owned by its creator.",
        ).setPositiveButton("Open Themes") { _, _ -> openThemes() }.setNegativeButton("Close", null).show()
    }

    private fun openThemes() {
        val intent = listOf("com.hihonor.android.thememanager", "com.huawei.android.thememanager").firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
        if (intent == null) MaterialAlertDialogBuilder(this).setTitle("Open HONOR Themes manually")
            .setMessage("Open the Themes app, or Settings → Home screen & personalization → Themes.").setPositiveButton("Close", null).show()
        else runCatching { startActivity(intent) }.onFailure { status.text = "Open Themes from your home screen." }
    }

    private fun sessionKey(): String = document?.sourceFile?.name ?: "new:${packData?.pack?.packageName ?: "none"}"

    private fun saveSession() {
        if (!initialized) return
        val json = JSONObject()
        choices.forEach { (pkg, choice) -> json.put(pkg, JSONObject().put("pack", choice.pack).put("drawable", choice.drawable)) }
        preferences.edit().putString("choices:${sessionKey()}", json.toString())
            .putString("removed:${sessionKey()}", JSONArray(removedPackages.toList()).toString()).apply()
    }

    private fun restoreSession() {
        choices.clear(); removedPackages.clear()
        val json = runCatching { JSONObject(preferences.getString("choices:${sessionKey()}", "{}") ?: "{}") }.getOrNull()
        json?.keys()?.forEach { pkg ->
            val choice = json.optJSONObject(pkg) ?: return@forEach
            val pack = choice.optString("pack"); val drawable = choice.optString("drawable")
            if (packs.any { it.packageName == pack } && drawable.isNotBlank()) choices[pkg] = Choice(pack, drawable)
        }
        val removed = runCatching { JSONArray(preferences.getString("removed:${sessionKey()}", "[]") ?: "[]") }.getOrNull()
        if (removed != null) for (i in 0 until removed.length()) removedPackages.add(removed.optString(i))
        // Ensure icons from previously selected packs are renderable after restart.
        val missing = choices.values.map { it.pack }.distinct().filter { !loadedPacks.containsKey(it) }
        if (missing.isNotEmpty()) background {
            missing.forEach { pkg -> packs.firstOrNull { it.packageName == pkg }?.let { runCatching { repository.load(it) }.getOrNull()?.let(::cachePack) } }
            ui { thumbnails.clear(); ++revision; refreshUi(forceRebind = true) }
        }
    }

    private fun persist() {
        if (!ready || !initialized) return
        preferences.edit().putString("pack", packData?.pack?.packageName).putString("source", document?.sourceFile?.name)
            .putString("applied", appliedPack?.pack?.packageName).putString("title", titleInput.text.toString())
            .putString("designer", designerInput.text.toString()).putString("brief", briefInput.text.toString())
            .putBoolean("whole", wholePack.isChecked).putBoolean("replace", replaceIcons.isChecked)
            .putBoolean("repair", repairCompatibility.isChecked)
            .putString("extension", extension).putInt("tab", selectedTab).putInt("appFilter", appFilter)
            .putString("pendingExport", pendingExport).putString("pendingReport", pendingReport).apply()
    }

    private fun cachedImport(name: String?): File? = name?.takeIf(::isImportName)?.let { filename ->
        File(themeWorkDir, filename).takeIf(File::isFile)
            ?: File(cacheDir, filename).takeIf(File::isFile)?.let(::migrateWorkingFile)
    }

    private fun isImportName(name: String): Boolean = name.startsWith("imported-") && name.endsWith(".hnt") && '/' !in name && '\\' !in name
    private fun isExportName(name: String): Boolean = name.startsWith("export-") && (name.endsWith(".hnt") || name.endsWith(".hwt")) && '/' !in name && '\\' !in name
    private fun isWorkingExport(file: File): Boolean = isExportName(file.name) && file.absoluteFile.parentFile == themeWorkDir.absoluteFile

    /** Recover pre-0.3 working files only from this app's private cache. */
    private fun workingExport(path: String?): File? {
        val old = path?.let(::File) ?: return null
        if (!isExportName(old.name)) return null
        if (isWorkingExport(old)) return old.takeIf(File::isFile)
        if (old.absoluteFile.parentFile != cacheDir.absoluteFile) return null
        return File(themeWorkDir, old.name).takeIf(File::isFile)
            ?: old.takeIf(File::isFile)?.let(::migrateWorkingFile)
    }

    /** Rename is atomic on the app's volume; a bounded copy handles a fallback. */
    private fun migrateWorkingFile(source: File): File {
        require(source.absoluteFile.parentFile == cacheDir.absoluteFile && (isImportName(source.name) || isExportName(source.name))) { "Invalid legacy working file." }
        val destination = File(themeWorkDir, source.name)
        fun migratedByAnotherActivity(): Boolean = !source.isFile && destination.isFile && destination.length() in 1..ThemeEditor.MAX_ARCHIVE_BYTES
        if (migratedByAnotherActivity()) return destination
        require(source.length() in 1..ThemeEditor.MAX_ARCHIVE_BYTES) { "Legacy working file exceeds its supported size." }
        if (!destination.exists() && source.renameTo(destination)) return destination
        if (migratedByAnotherActivity()) return destination
        val prefix = if (isImportName(source.name)) "imported-" else "export-"
        val copy = File.createTempFile(prefix, ".${source.extension}", themeWorkDir)
        try {
            source.inputStream().use { input -> copy.outputStream().use { output ->
                val buffer = ByteArray(32 * 1024); var total = 0L
                while (true) {
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                    val count = input.read(buffer); if (count < 0) break
                    total += count; require(total <= ThemeEditor.MAX_ARCHIVE_BYTES) { "Legacy working file exceeds its supported size." }
                    output.write(buffer, 0, count)
                }
                require(total > 0) { "Legacy working file is empty." }
            } }
            source.delete()
            return copy
        } catch (failure: Exception) { copy.delete(); throw failure }
    }

    private fun migrateSession(oldName: String, newName: String) {
        preferences.edit().putString("choices:$newName", preferences.getString("choices:$oldName", "{}"))
            .putString("removed:$newName", preferences.getString("removed:$oldName", "[]")).apply()
    }

    /** Leave fresh files to workers from a rotating Activity; remove aged orphans. */
    private fun cleanupWorkingFiles(retained: Set<File>) {
        val keep = retained.mapTo(hashSetOf()) { it.absoluteFile }
        val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        themeWorkDir.listFiles()?.filter { file -> file.isFile && file.lastModified() in 1 until cutoff && file.absoluteFile !in keep && (
            isImportName(file.name) || isExportName(file.name) ||
                (file.name.endsWith(".zip") && listOf("theme-icons-", "theme-edited-", "theme-export-icons-").any { prefix -> file.name.startsWith(prefix) }) ||
                (file.name.startsWith("theme-zip-payload-") && file.name.endsWith(".tmp"))
            ) }?.forEach { it.delete() }
    }

    /** Pack reads and index construction stay on the shared background worker. */
    private fun cachePack(data: PackData) {
        if (loadedPacks[data.pack.packageName] !== data || !suggestionIndexes.containsKey(data.pack.packageName)) {
            suggestionIndexes[data.pack.packageName] = IconSuggestions(data)
        }
        loadedPacks[data.pack.packageName] = data
    }

    private fun background(block: () -> Unit) {
        if (worker.isShutdown) return
        worker.execute {
            try { block() } catch (failure: Exception) {
                if (failure !is InterruptedException) ui { setBusy(false, failure.message ?: "This operation failed. Try again.") }
            }
        }
    }

    private fun ui(onDiscard: () -> Unit = {}, block: () -> Unit) {
        handler.post { if (!isDestroyed && !isFinishing) block() else onDiscard() }
    }

    private fun setBusy(value: Boolean, message: String) {
        busy = value; status.text = message; progress.visibility = if (value) View.VISIBLE else View.GONE; updateControls()
    }

    private fun updateControls() {
        if (!::saveButton.isInitialized) return
        choosePackButton.isEnabled = !busy; importButton.isEnabled = !busy
        saveButton.isEnabled = !busy && (document != null || packData != null)
        applyPackButton.isEnabled = !busy && document != null && packData != null
        titleInput.isEnabled = !busy; designerInput.isEnabled = !busy; briefInput.isEnabled = !busy
        wholePack.isEnabled = !busy; replaceIcons.isEnabled = !busy && appliedPack != null; formatInput.isEnabled = !busy
        repairCompatibility.isEnabled = !busy
        appSearch.isEnabled = !busy; iconSearch.isEnabled = !busy
        toolbar.menu.findItem(MENU_NEW)?.isEnabled = !busy
        toolbar.menu.findItem(MENU_REFRESH)?.isEnabled = !busy
    }

    private fun showPage() { if (::pages.isInitialized) pages.forEachIndexed { index, view -> view.visibility = if (index == selectedTab) View.VISIBLE else View.GONE } }

    private fun recycler(adapter: RecyclerView.Adapter<*>, grid: Boolean): RecyclerView = RecyclerView(this).apply {
        layoutManager = if (grid) GridLayoutManager(this@MainActivity, (resources.displayMetrics.widthPixels / dp(110)).coerceIn(3, 8)) else LinearLayoutManager(this@MainActivity)
        this.adapter = adapter; clipToPadding = false; setPadding(0, 0, 0, dp(8)); itemAnimator = null
    }

    private fun searchField(parent: LinearLayout, hint: String): TextInputEditText {
        val layout = TextInputLayout(this, null, com.google.android.material.R.attr.textInputStyle).apply { this.hint = hint; endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT }
        val input = TextInputEditText(layout.context).apply { setSingleLine(); textSize = 15f; inputType = android.text.InputType.TYPE_CLASS_TEXT }
        layout.addView(input); parent.addView(layout, LinearLayout.LayoutParams(-1, dp(60)))
        return input
    }

    private fun editorField(parent: LinearLayout, hint: String, limit: Int, multiline: Boolean): TextInputEditText {
        val layout = TextInputLayout(this, null, com.google.android.material.R.attr.textInputStyle).apply { this.hint = hint; isCounterEnabled = true; counterMaxLength = limit }
        val input = TextInputEditText(layout.context).apply {
            filters = arrayOf(InputFilter.LengthFilter(limit)); textSize = 16f
            if (multiline) { minLines = 2; maxLines = 4; gravity = Gravity.TOP; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE }
            else setSingleLine()
        }
        layout.addView(input); parent.addView(layout, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        return input
    }

    private fun button(text: String, tonal: Boolean = false, outlined: Boolean = false, action: () -> Unit): MaterialButton =
        MaterialButton(this, null, when { outlined -> com.google.android.material.R.attr.materialButtonOutlinedStyle; tonal -> com.google.android.material.R.attr.materialButtonTonalStyle; else -> com.google.android.material.R.attr.materialButtonStyle }).apply {
            this.text = text; isAllCaps = false; textSize = 14f; minHeight = dp(48); setOnClickListener { if (!busy) action() }
        }

    private fun label(value: String, size: Float, muted: Boolean = false, bold: Boolean = false): TextView = TextView(this).apply {
        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
        text = value; textSize = size
        setTextColor(color(if (muted) com.google.android.material.R.attr.colorOnSurfaceVariant else com.google.android.material.R.attr.colorOnSurface))
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun watchText(input: TextInputEditText, changed: () -> Unit) { input.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = changed()
        override fun afterTextChanged(s: Editable?) = Unit
    }) }

    private fun watchSearch(input: TextInputEditText, changed: () -> Unit) {
        var callback: Runnable? = null
        watchText(input) {
            callback?.let(handler::removeCallbacks)
            callback = Runnable { if (!isDestroyed && !isFinishing) changed() }.also { handler.postDelayed(it, 180) }
        }
    }

    private fun matches(query: String, vararg values: String): Boolean = query.isBlank() || values.any { it.contains(query, ignoreCase = true) }
    private fun friendlyName(value: String): String = value.trim('_').replace('_', ' ').replaceFirstChar { it.uppercase() }
    private fun color(attribute: Int): Int = MaterialColors.getColor(this, attribute, Color.GRAY)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    override fun onSaveInstanceState(outState: Bundle) {
        persist(); saveSession()
        outState.putString("source", document?.sourceFile?.name); outState.putString("pack", packData?.pack?.packageName)
        outState.putString("applied", appliedPack?.pack?.packageName); outState.putString("title", titleInput.text.toString())
        outState.putString("designer", designerInput.text.toString()); outState.putString("brief", briefInput.text.toString())
        outState.putBoolean("whole", wholePack.isChecked); outState.putBoolean("replace", replaceIcons.isChecked)
        outState.putBoolean("repair", repairCompatibility.isChecked)
        outState.putString("extension", extension); outState.putInt("tab", selectedTab); outState.putInt("appFilter", appFilter)
        outState.putString("pendingExport", pendingExport); outState.putString("pendingReport", pendingReport)
        deferredDocumentResult?.let { result ->
            outState.putInt("documentRequest", result.requestCode); outState.putInt("documentResult", result.resultCode)
            outState.putString("documentUri", result.uri)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onPause() { if (ready) { persist(); saveSession() }; super.onPause() }

    override fun onDestroy() {
        ready = false; worker.shutdownNow()
        thumbnails.close(); document?.let(ThemeEditor::close)
        super.onDestroy()
    }

    companion object {
        private const val OPEN_THEME = 101
        private const val SAVE_THEME = 100
        private const val MENU_NEW = 201
        private const val MENU_REFRESH = 202
        private const val MENU_DETAILS = 203
        private const val MENU_THEMES = 204
        private const val MENU_HELP = 205
        private const val FILTER_ALL = 0
        private const val FILTER_MISSING = 1
        private const val FILTER_CUSTOMIZED = 2
        private val FORMATS = listOf("HONOR (.hnt)", "Compatibility (.hwt)")
    }
}
