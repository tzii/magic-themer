package dev.magicthemer.icons

import dev.magicthemer.ApplicationTarget
import java.util.Locale

/** Formats uncovered apps into a plain-text icon request ready to share with pack authors. */
object IconRequestFormatter {
    const val DEFAULT_MAX_APPS = 150

    fun format(packLabel: String?, apps: List<ApplicationTarget>, maxApps: Int = DEFAULT_MAX_APPS): String {
        val ordered = apps.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        val limit = maxApps.coerceAtLeast(1)
        val included = ordered.take(limit)
        val omitted = ordered.size - included.size
        val usedSlugs = HashSet<String>()
        return buildString {
            append("Icon request")
            if (!packLabel.isNullOrBlank()) append(" for ").append(packLabel.trim())
            append(" (").append(ordered.size).append(if (ordered.size == 1) " app" else " apps").append(")\n\n")
            included.forEach { app ->
                val components = componentSpecs(app)
                append(app.label).append('\n')
                components.forEach { append(it).append('\n') }
                append("https://play.google.com/store/apps/details?id=").append(app.packageName).append("\n\n")
            }
            append("appfilter.xml\n")
            included.forEach { app ->
                val safeComment = sanitizeXmlComment(app.label)
                val drawable = uniqueSlug(drawableSlug(app.label, app.packageName), usedSlugs)
                val components = componentSpecs(app)
                append("<!-- ").append(safeComment).append(" -->\n")
                components.forEach { component ->
                    append("<item component=\"").append(component).append("\" drawable=\"").append(drawable).append("\" />\n")
                }
            }
            if (omitted > 0) {
                append("\n...and ").append(omitted).append(if (omitted == 1) " more app omitted." else " more apps omitted.")
            }
        }.trimEnd()
    }

    private fun componentSpecs(app: ApplicationTarget): List<String> =
        if (app.activities.isEmpty()) listOf("ComponentInfo{${app.packageName}}")
        else app.activities.map { "ComponentInfo{${app.packageName}/$it}" }

    private fun sanitizeXmlComment(text: String): String =
        buildString {
            var i = 0
            while (i < text.length) {
                val cp = Character.codePointAt(text, i)
                if (cp == 0x20 || cp in 0x21..0xd7ff || cp in 0xe000..0xfffd || cp in 0x10000..0x10ffff) {
                    appendCodePoint(cp)
                }
                i += Character.charCount(cp)
            }
        }.replace(Regex("-{2,}"), "-").trim('-')

    private fun uniqueSlug(base: String, used: MutableSet<String>): String {
        if (used.add(base)) return base
        var suffix = 2
        while (true) {
            val suffixText = "_$suffix"
            val candidate = base.take(64 - suffixText.length).trimEnd('_') + suffixText
            if (used.add(candidate)) return candidate
            suffix++
        }
    }

    internal fun drawableSlug(label: String, packageName: String): String {
        val slug = label.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
        if (slug.isNotEmpty() && slug.first().isLetter()) return slug.take(64).trimEnd('_')
        val fallback = packageName.substringAfterLast('.').lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "_").trim('_')
        if (fallback.isNotEmpty() && fallback.first().isLetter()) return fallback.take(64).trimEnd('_')
        return ("app_$fallback").take(64).trimEnd('_')
    }
}
