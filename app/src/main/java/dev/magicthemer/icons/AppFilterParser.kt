package dev.magicthemer.icons

import java.io.InputStream
import java.time.LocalDate

/** The non-Android core is shared by compiled XML resources and text appfilters. */
internal data class ParsedAppFilter(
    val mappings: List<IconMapping>,
    val warnings: List<String>,
    val fallbackStyle: PackFallbackStyle = PackFallbackStyle(),
    val calendarMappings: List<CalendarMapping> = emptyList(),
)

internal object AppFilterParser {
    const val MAX_XML_BYTES = SecureIconXml.MAX_XML_BYTES
    const val MAX_MAPPINGS = 100_000
    const val MAX_FALLBACK_DRAWABLES = 64

    fun parse(stream: InputStream, dayOfMonth: Int = LocalDate.now().dayOfMonth): ParsedAppFilter {
        val builder = Builder(dayOfMonth)
        SecureIconXml.parse(stream, "appfilter.xml", builder::startElement)
        return builder.build()
    }

    internal class Builder(private val dayOfMonth: Int = LocalDate.now().dayOfMonth) {
        private val mappings = ArrayList<IconMapping>()
        private val calendars = ArrayList<IconMapping>()
        private val calendarEntries = ArrayList<CalendarMapping>()
        private val backgrounds = linkedSetOf<String>()
        private val masks = linkedSetOf<String>()
        private val upons = linkedSetOf<String>()
        private var scale = 1f
        private val notices = linkedSetOf<String>()
        private var skipped = 0
        private var invalidComponents = 0
        private var invalidDrawables = 0
        private var elements = 0
        private var firstElement = true

        init { require(dayOfMonth in 1..31) }

        fun startElement(name: String, attributes: Map<String, String>) {
            if (++elements > 250_000) throw IllegalArgumentException("Too many elements in appfilter.xml.")
            if (firstElement) {
                require(name == "resources") { "The appfilter root must be <resources>." }
                firstElement = false
            }
            when (name) {
                "item" -> addMapping(attributes["component"], attributes["drawable"], false)
                "calendar" -> {
                    val prefix = attributes["prefix"]
                    val rawComponent = attributes["component"]?.trim()
                    if (prefix == null || !validDrawableName(prefix + dayOfMonth)) {
                        skipped++
                        invalidDrawables++
                    } else if (rawComponent == ":CALENDAR") {
                        calendarEntries += CalendarMapping(":CALENDAR", "", "", prefix)
                        notices += "Calendar icons can export a live HONOR background, plus a static snapshot for day $dayOfMonth."
                    } else {
                        val added = addMapping(rawComponent, prefix + dayOfMonth, true)
                        if (added != null) {
                            calendarEntries += CalendarMapping(added.component, added.packageName, added.activityName, prefix)
                            notices += "Calendar icons can export a live HONOR background, plus a static snapshot for day $dayOfMonth."
                        }
                    }
                }
                "iconback" -> {
                    collectImages(attributes, backgrounds)
                    notices += "This pack defines fallback backgrounds, masks or scaling for unmapped apps."
                }
                "iconmask" -> {
                    collectImages(attributes, masks)
                    notices += "This pack defines fallback backgrounds, masks or scaling for unmapped apps."
                }
                "iconupon" -> {
                    collectImages(attributes, upons)
                    notices += "This pack defines fallback backgrounds, masks or scaling for unmapped apps."
                }
                "scale" -> {
                    attributes["factor"]?.trim()?.toFloatOrNull()?.takeIf { it.isFinite() && it in 0.1f..2.0f }?.let { scale = it }
                    notices += "This pack defines fallback backgrounds, masks or scaling for unmapped apps."
                }
                "dynamic-clock" -> notices += "Dynamic clocks are exported as a static drawable; the hands do not update."
            }
        }

        private fun collectImages(attributes: Map<String, String>, target: MutableSet<String>) {
            val ordered = attributes.entries
                .filter { it.key.startsWith("img") }
                .sortedWith(compareBy<Map.Entry<String, String>> { it.key.removePrefix("img").toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it.key })
            for ((_, value) in ordered) {
                if (target.size >= MAX_FALLBACK_DRAWABLES) break
                val trimmed = value.trim()
                if (validDrawableName(trimmed)) target += trimmed
            }
        }

        private fun addMapping(component: String?, drawable: String?, calendar: Boolean): IconMapping? {
            if (mappings.size + calendars.size >= MAX_MAPPINGS) {
                throw IllegalArgumentException("appfilter.xml exceeds the 100,000 mapping limit.")
            }
            val mapping = mapping(component, drawable)
            if (mapping == null) {
                skipped++
                if (drawable == null || !validDrawableName(drawable)) invalidDrawables++ else invalidComponents++
                return null
            }
            if (calendar) calendars += mapping else mappings += mapping
            return mapping
        }

        fun build(): ParsedAppFilter {
            require(!firstElement) { "Empty appfilter.xml." }
            if (skipped > 0) notices += "$skipped invalid or unsupported icon mappings were skipped ($invalidComponents component identifiers, $invalidDrawables drawable names)."
            // A calendar entry takes precedence over its ordinary static item,
            // independently of their order in the document. Retain every other
            // mapping, including mappings for applications not installed here.
            val calendarComponents = calendars.mapTo(hashSetOf()) { it.component }
            return ParsedAppFilter(
                mappings.filterNot { it.component in calendarComponents } + calendars,
                notices.toList(),
                PackFallbackStyle(backgrounds.toList(), masks.toList(), upons.toList(), scale),
                calendarEntries.toList(),
            )
        }
    }

    internal fun mapping(component: String?, drawable: String?): IconMapping? {
        if (component == null || drawable == null || component.length > 1_024 || !validDrawableName(drawable)) return null
        val raw = component.trim().let {
            if (it.startsWith("ComponentInfo{") && it.endsWith("}")) it.substring(14, it.length - 1) else it
        }
        val parts = raw.split('/')
        // Some packs provide a package-wide fallback without specifying a launch
        // Activity. Keep the package mapping without inventing a component class.
        if (parts.size == 1 && validPackageName(raw)) {
            return IconMapping("ComponentInfo{$raw}", raw, "", drawable)
        }
        if (parts.size != 2) return null
        val packageName = parts[0]
        val activityName = if (parts[1].startsWith('.')) packageName + parts[1] else parts[1]
        if (!validPackageName(packageName) || activityName.length > 512 || !CLASS_PATTERN.matches(activityName)) return null
        return IconMapping("ComponentInfo{$packageName/$activityName}", packageName, activityName, drawable)
    }

    internal fun validPackageName(name: String): Boolean = name.length in 3..255 && PACKAGE_PATTERN.matches(name)
    internal fun validDrawableName(name: String): Boolean = name.length in 1..128 && DRAWABLE_PATTERN.matches(name)

    private val PACKAGE_PATTERN = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")
    private val CLASS_PATTERN = Regex("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*")
    private val DRAWABLE_PATTERN = Regex("[A-Za-z_][A-Za-z0-9_]*")
}
