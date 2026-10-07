package dev.magicthemer.theme

import java.nio.charset.StandardCharsets

/** Replaces only requested direct-child values in already validated UTF-8 theme XML. */
internal object ThemeMetadata {
    private data class ElementRange(val name: String, val start: Int, val openEnd: Int, val closeStart: Int, val end: Int, val empty: Boolean)

    fun patch(original: ByteArray, changes: Map<String, String>): ByteArray {
        if (changes.isEmpty()) return original
        val text = original.toString(StandardCharsets.UTF_8)
        val children = mutableListOf<ElementRange>()
        var depth = 0
        var position = 0
        var childStart = -1
        var childOpenEnd = -1
        var childName = ""
        var rootClose = -1
        while (position < text.length) {
            val start = text.indexOf('<', position)
            if (start < 0) break
            val specialEnd = when {
                text.startsWith("<!--", start) -> text.indexOf("-->", start + 4).takeIf { it >= 0 }?.plus(3)
                text.startsWith("<![CDATA[", start) -> text.indexOf("]]>", start + 9).takeIf { it >= 0 }?.plus(3)
                text.startsWith("<?", start) -> text.indexOf("?>", start + 2).takeIf { it >= 0 }?.plus(2)
                else -> null
            }
            if (specialEnd != null) { position = specialEnd; continue }
            var quote: Char? = null
            var end = start + 1
            while (end < text.length) {
                val character = text[end]
                if (quote != null) { if (character == quote) quote = null }
                else if (character == '\'' || character == '"') quote = character
                else if (character == '>') break
                end++
            }
            require(end < text.length && quote == null) { "Cannot preserve this metadata layout." }
            val token = text.substring(start + 1, end).trim()
            require(!token.startsWith('!')) { "Unsupported metadata declaration." }
            val closing = token.startsWith('/')
            val empty = !closing && token.endsWith('/')
            val name = token.removePrefix("/").takeWhile { !it.isWhitespace() && it != '/' }
            if (closing) {
                if (depth == 2) children += ElementRange(childName, childStart, childOpenEnd, start, end + 1, false)
                if (depth == 1) rootClose = start
                depth--
            } else {
                if (depth == 1) {
                    if (empty) children += ElementRange(name, start, end + 1, end + 1, end + 1, true)
                    else { childName = name; childStart = start; childOpenEnd = end + 1 }
                }
                if (!empty) depth++
            }
            require(depth >= 0) { "Invalid metadata nesting." }
            position = end + 1
        }
        require(depth == 0 && rootClose >= 0) { "Cannot preserve this metadata root." }
        val edits = mutableListOf<Pair<IntRange, String>>()
        val additions = StringBuilder()
        changes.forEach { (name, value) ->
            val escaped = escape(value)
            val element = children.firstOrNull { it.name == name }
            if (element == null) additions.append('<').append(name).append('>').append(escaped).append("</").append(name).append('>')
            else if (element.empty) {
                val opening = text.substring(element.start, element.openEnd).replace(Regex("/\\s*>$"), ">")
                edits += (element.start until element.end) to "$opening$escaped</$name>"
            } else edits += (element.openEnd until element.closeStart) to escaped
        }
        if (additions.isNotEmpty()) edits += (rootClose until rootClose) to additions.toString()
        val edited = StringBuilder(text)
        edits.sortedByDescending { it.first.first }.forEach { (range, value) ->
            edited.replace(range.first, range.last + 1, value)
        }
        return edited.toString().toByteArray(StandardCharsets.UTF_8)
    }

    private fun escape(value: String): String = buildString {
        value.forEach { append(when (it) { '&' -> "&amp;"; '<' -> "&lt;"; '>' -> "&gt;"; '\r' -> "&#13;"; else -> it.toString() }) }
    }
}
