package dev.magicthemer.icons

import java.io.InputStream
import java.time.LocalDate

/** The non-Android core is shared by compiled XML resources and text appfilters. */
internal data class ParsedAppFilter(
    val mappings: List<IconMapping>,
    val warnings: List<String>,
)

internal object AppFilterParser {
    const val MAX_XML_BYTES = SecureIconXml.MAX_XML_BYTES
    const val MAX_MAPPINGS = 100_000

    fun parse(stream: InputStream, dayOfMonth: Int = LocalDate.now().dayOfMonth): ParsedAppFilter {
        val builder = Builder(dayOfMonth)
        SecureIconXml.parse(stream, "appfilter.xml", builder::startElement)
        return builder.build()
    }

    internal class Builder(private val dayOfMonth: Int = LocalDate.now().dayOfMonth) {
        private val mappings = ArrayList<IconMapping>()
        private val calendars = ArrayList<IconMapping>()
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
                    if (prefix == null || !validDrawableName(prefix + dayOfMonth)) {
                        skipped++
                        invalidDrawables++
                    } else {
                        addMapping(attributes["component"], prefix + dayOfMonth, true)
                        notices += "Calendar icons are a snapshot for day $dayOfMonth; rebuild the theme after the date changes."
                    }
                }
                "iconback", "iconmask", "iconupon", "scale" ->
                    notices += "This pack defines fallback backgrounds, masks or scaling. Only explicitly mapped icons are exported; unmapped apps keep their current icons."
                "dynamic-clock" -> notices += "Dynamic clocks are exported as a static drawable; the hands do not update."
            }
        }

        private fun addMapping(component: String?, drawable: String?, calendar: Boolean) {
            if (mappings.size + calendars.size >= MAX_MAPPINGS) {
                throw IllegalArgumentException("appfilter.xml exceeds the 100,000 mapping limit.")
            }
            val mapping = mapping(component, drawable)
            if (mapping == null) {
                skipped++
                if (drawable == null || !validDrawableName(drawable)) invalidDrawables++ else invalidComponents++
            }
            else if (calendar) calendars += mapping
            else mappings += mapping
        }

        fun build(): ParsedAppFilter {
            require(!firstElement) { "Empty appfilter.xml." }
            if (skipped > 0) notices += "$skipped invalid or unsupported icon mappings were skipped ($invalidComponents component identifiers, $invalidDrawables drawable names)."
            // A calendar entry takes precedence over its ordinary static item,
            // independently of their order in the document. Retain every other
            // mapping, including mappings for applications not installed here.
            val calendarComponents = calendars.mapTo(hashSetOf()) { it.component }
            return ParsedAppFilter(mappings.filterNot { it.component in calendarComponents } + calendars, notices.toList())
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
