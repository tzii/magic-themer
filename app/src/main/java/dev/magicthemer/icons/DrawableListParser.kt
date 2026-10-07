package dev.magicthemer.icons

import java.io.InputStream

internal data class ParsedDrawableList(val drawables: List<String>, val warnings: List<String>)

/** drawable.xml lists alternatives and artwork that may have no appfilter mapping. */
internal object DrawableListParser {
    const val MAX_DRAWABLES = 100_000

    fun parse(stream: InputStream): ParsedDrawableList {
        val builder = Builder()
        SecureIconXml.parse(stream, "drawable.xml", builder::startElement)
        return builder.build()
    }

    internal class Builder {
        private val drawables = linkedSetOf<String>()
        private var firstElement = true
        private var elements = 0
        private var skipped = 0

        fun startElement(name: String, attributes: Map<String, String>) {
            require(++elements <= 250_000) { "Too many elements in drawable.xml." }
            if (firstElement) {
                require(name == "resources") { "The drawable list root must be <resources>." }
                firstElement = false
            }
            if (name == "item") {
                val drawable = attributes["drawable"]
                if (drawable == null || !AppFilterParser.validDrawableName(drawable)) skipped++
                else {
                    require(drawable in drawables || drawables.size < MAX_DRAWABLES) { "drawable.xml exceeds the 100,000 icon limit." }
                    drawables += drawable
                }
            }
        }

        fun build(): ParsedDrawableList {
            require(!firstElement) { "Empty drawable.xml." }
            val warnings = if (skipped > 0) listOf("$skipped invalid drawable.xml icon names were skipped.") else emptyList()
            return ParsedDrawableList(drawables.toList(), warnings)
        }
    }
}
