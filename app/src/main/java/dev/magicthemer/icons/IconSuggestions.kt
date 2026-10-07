package dev.magicthemer.icons

import dev.magicthemer.ApplicationTarget
import java.text.Normalizer
import java.util.Locale

/** A choice to review, never an automatic assignment. */
data class IconSuggestion(
    val drawableName: String,
    val label: String,
    val reason: String,
    val score: Int,
)

/**
 * An offline index of artwork names and the pack's own app associations.
 *
 * Build once per loaded pack. Queries inspect a bounded shortlist rather than
 * repeatedly scanning or decoding a catalogue of up to 100,000 drawables. Only
 * whole words, complete names and explicit app associations produce suggestions;
 * a coincidental substring such as "note" in "notebooking" is insufficient.
 */
class IconSuggestions(data: PackData) {
    private data class Artwork(val name: String, val words: Set<String>, val compact: String, val label: String)

    private val artwork: List<Artwork>
    private val byName: Map<String, Int>
    private val byCompact = HashMap<String, MutableList<Int>>()
    private val byWord = HashMap<String, MutableList<Int>>()
    private val byPackage = HashMap<String, MutableList<IconMapping>>()
    private val byActivity = HashMap<String, MutableList<IconMapping>>()

    init {
        require(data.drawables.size <= MAX_CATALOGUE && data.mappings.size <= MAX_CATALOGUE) {
            "The suggestion index exceeds the 100,000 icon limit."
        }
        val names = (data.drawables.asSequence() + data.mappings.asSequence().map { it.drawableName })
            .filter { data.isDrawableAvailable(it) && AppFilterParser.validDrawableName(it) }
            .distinct().sorted().toList()
        require(names.size <= MAX_CATALOGUE) { "The suggestion index exceeds the 100,000 icon limit." }
        artwork = names.map { name ->
            val words = normalizedWords(name)
            val separated = normalizedWords(name, splitCamelCase = true)
            Artwork(name, meaningfulWords(words + separated).distinct().take(MAX_WORDS).toSet(), compact(words), humanLabel(separated))
        }
        byName = artwork.mapIndexed { index, icon -> icon.name to index }.toMap()
        artwork.forEachIndexed { index, icon ->
            if (icon.compact.isNotEmpty()) addPosting(byCompact, icon.compact, index)
            for (word in icon.words.take(MAX_WORDS)) addPosting(byWord, word, index)
        }
        // Sort independently of metadata order so ties remain stable when a
        // pack author merely rearranges appfilter.xml or drawable.xml.
        for (mapping in data.mappings.sortedWith(compareBy({ it.packageName }, { it.activityName }, { it.drawableName }))) {
            if (mapping.drawableName !in byName) continue
            addMapping(byPackage, mapping.packageName, mapping)
            if (mapping.activityName.isNotEmpty()) addMapping(byActivity, mapping.activityName, mapping)
        }
    }

    fun suggest(target: ApplicationTarget, limit: Int = 8): List<IconSuggestion> {
        if (limit <= 0 || artwork.isEmpty()) return emptyList()
        val count = limit.coerceAtMost(MAX_RESULTS)
        val labelWords = normalizedWords(target.label.take(256))
        val labelCompact = compact(labelWords)
        val labelTokens = meaningfulWords(labelWords).distinct().take(MAX_WORDS).toSet()
        val separatedLabelTokens = meaningfulWords(normalizedWords(target.label.take(256), splitCamelCase = true))
            .distinct().take(MAX_WORDS).toSet().takeIf { it.size >= 2 && it != labelTokens }.orEmpty()
        val packageTokens = target.packageName.split('.').asReversed()
            .flatMap { normalizedWords(it) }
            .map { it.trimEnd { char -> char.isDigit() } }
            .filter(::isMeaningful).take(3).toSet()
        val aliases = APP_ALIASES[target.packageName].orEmpty().map { normalizedWords(it) }
        val aliasCompacts = aliases.map(::compact).filter { it.isNotEmpty() }.toSet()
        val aliasTokens = aliases.flatMap(::meaningfulWords).toSet()
        val ranked = HashMap<Int, IconSuggestion>()

        fun offer(index: Int, score: Int, reason: String) {
            val icon = artwork[index]
            val old = ranked[index]
            if (old == null || score > old.score) {
                ranked[index] = IconSuggestion(icon.name, icon.label, reason, score)
            }
        }

        // The pack's explicit associations outrank inferred names. If its
        // primary entry is absent, any remaining real artwork for the same app
        // still provides a useful and correctly identified repair suggestion.
        val activities = target.activities.take(MAX_WORDS).toSet()
        for (mapping in byPackage[target.packageName].orEmpty()) {
            val index = byName.getValue(mapping.drawableName)
            if (mapping.activityName in activities) offer(index, 1_200, "Matches launcher activity")
            else offer(index, 1_100, "Mapped to this app")
        }
        for (activity in activities) {
            for (mapping in byActivity[activity].orEmpty()) {
                offer(byName.getValue(mapping.drawableName), 1_150, "Matches launcher activity")
            }
        }
        if (labelCompact.isNotEmpty()) {
            for (index in byCompact[labelCompact].orEmpty()) offer(index, 950, "Matches app name")
        }
        for (alias in aliasCompacts) {
            for (index in byCompact[alias].orEmpty()) offer(index, 900, "Known app name")
        }

        val candidates = linkedSetOf<Int>()
        val queryTokens = (labelTokens + separatedLabelTokens + aliasTokens + packageTokens).take(MAX_WORDS)
        // Rarer words are more useful and also prevent a common token from
        // consuming the shortlist before a distinctive app name is considered.
        for (token in queryTokens.sortedWith(compareBy({ byWord[it]?.size ?: 0 }, { it }))) {
            for (index in byWord[token].orEmpty()) {
                if (candidates.size >= MAX_CANDIDATES) break
                candidates += index
            }
        }
        for (index in candidates) {
            val icon = artwork[index]
            val labelMatches = icon.words.intersect(labelTokens).size
            val aliasMatches = icon.words.intersect(aliasTokens).size
            val packageMatches = icon.words.intersect(packageTokens).size
            // ArchiveTune can match archive_tune_alt even when it has no pack
            // mapping. Require every separated word, so ArchiveTune does not
            // suggest an unrelated archive or music/tune icon. Keep the whole
            // brand path above for names such as YouTube and ChatGPT.
            val completeSeparatedName = separatedLabelTokens.isNotEmpty() && icon.words.containsAll(separatedLabelTokens)
            when {
                labelMatches > 0 && labelMatches * 2 >= labelTokens.size -> {
                    val complete = labelMatches == labelTokens.size
                    offer(index, (if (complete) 800 else 650) + labelMatches * 20 - minOf(icon.words.size, 20),
                        if (complete) "Matches app name" else "Shares app name words")
                }
                completeSeparatedName -> offer(index, 800 + separatedLabelTokens.size * 20 - minOf(icon.words.size, 20), "Matches app name")
                aliasMatches > 0 -> offer(index, 600 + aliasMatches * 20 - minOf(icon.words.size, 20), "Known app name")
                packageMatches > 0 -> offer(index, 500 + packageMatches * 20 - minOf(icon.words.size, 20), "Matches package name")
            }
        }
        return ranked.values.sortedWith(compareByDescending<IconSuggestion> { it.score }.thenBy { it.drawableName }).take(count)
    }

    private fun addPosting(index: MutableMap<String, MutableList<Int>>, word: String, value: Int) {
        val matches = index.getOrPut(word) { ArrayList() }
        if (matches.size < MAX_POSTINGS) matches += value
    }

    private fun addMapping(index: MutableMap<String, MutableList<IconMapping>>, key: String, mapping: IconMapping) {
        val matches = index.getOrPut(key) { ArrayList() }
        if (matches.size < MAX_POSTINGS) matches += mapping
    }

    companion object {
        private const val MAX_CATALOGUE = 100_000
        private const val MAX_POSTINGS = 1_024
        private const val MAX_CANDIDATES = 2_048
        private const val MAX_RESULTS = 12
        private const val MAX_WORDS = 12
        private val CAMEL_CASE = Regex("([a-z0-9])([A-Z])")
        private val DIACRITICS = Regex("\\p{M}+")
        private val SEPARATORS = Regex("[^a-z0-9]+")
        private val IGNORED = setOf(
            "com", "org", "net", "dev", "example", "android", "google", "apps", "app", "application",
            "main", "activity", "launcher", "client", "mobile", "free", "pro", "package",
            "icon", "icons", "alt", "alternative", "round", "circle", "square", "dark", "light",
            "white", "black", "classic", "filled", "outline", "material", "themed", "new", "old",
            "samsung", "microsoft", "amazon", "apple", "honor", "hihonor", "huawei",
        )

        private fun normalizedWords(text: String, splitCamelCase: Boolean = false): List<String> {
            // Preserve brands such as YouTube as one query word. Artwork also
            // indexes a separated spelling, so ArchiveTune can match Archive Tune.
            val separated = if (splitCamelCase) text.replace(CAMEL_CASE, "$1 $2") else text
            val folded = DIACRITICS.replace(Normalizer.normalize(separated, Normalizer.Form.NFD), "")
                .lowercase(Locale.ROOT)
            return SEPARATORS.split(folded).filter { it.isNotEmpty() && !it.all(Char::isDigit) }
        }

        private fun isMeaningful(word: String): Boolean = word.length >= 3 && word !in IGNORED && !word.all(Char::isDigit)
        private fun meaningfulWords(words: List<String>): List<String> = words.filter(::isMeaningful)
        private fun compact(words: List<String>): String = words.joinToString("")
        private fun humanLabel(words: List<String>): String = words.joinToString(" ") { word ->
            word.replaceFirstChar { it.titlecase(Locale.ROOT) }
        }

        // Exact package IDs only. These are identity/name aliases rather than
        // broad categories such as "browser" that could point at another app.
        private val APP_ALIASES = mapOf(
            "com.google.android.gm" to listOf("gmail"),
            "com.android.vending" to listOf("play store", "google play store"),
            "com.google.android.apps.maps" to listOf("maps", "google maps"),
            "com.google.android.apps.docs" to listOf("drive", "google drive"),
            "com.google.android.apps.photos" to listOf("photos", "google photos"),
            "com.google.android.deskclock" to listOf("clock"),
            "com.android.deskclock" to listOf("clock"),
            "com.google.android.calculator" to listOf("calculator"),
            "com.android.calculator2" to listOf("calculator"),
            "com.twitter.android" to listOf("twitter", "x"),
            "com.openai.chatgpt" to listOf("chatgpt"),
        )
    }
}
