package dev.magicthemer.ui

import java.text.Normalizer
import java.util.Locale

/** Searchable text normalized once and reused for every keystroke. */
class SearchText private constructor(
    /** Words as written, in order: YouTube Music → youtube, music. */
    private val whole: List<String>,
    /** Camel-case and digit boundaries split: YouTube Music → you, tube, music. */
    private val separated: List<String>,
    /** All letters and digits joined, so "droid deck" and "DroidDeck" compare equal. */
    internal val compact: String,
) {
    internal val words: Set<String> = (whole + separated).toSet()
    /** First letter of each separated word, so "yt" finds YouTube and "gm" Google Maps. */
    internal val initials: String = separated.joinToString("") { it.take(1) }

    internal fun startsWithWords(tokens: List<String>): Boolean =
        tokens.isNotEmpty() && (whole.take(tokens.size) == tokens || separated.take(tokens.size) == tokens)

    companion object {
        private val EMPTY = SearchText(emptyList(), emptyList(), "")
        private val DIACRITICS = Regex("\\p{M}+")
        private val SEPARATORS = Regex("[^\\p{L}\\p{N}]+")
        private val BOUNDARIES = Regex("(?<=\\p{Ll})(?=\\p{Lu})|(?<=\\p{L})(?=\\p{N})|(?<=\\p{N})(?=\\p{L})")
        private const val MAX_LENGTH = 256

        fun of(text: String): SearchText {
            val raw = rawWords(text)
            if (raw.isEmpty()) return EMPTY
            val whole = raw.map(::lower)
            val separated = raw.flatMap { word -> word.split(BOUNDARIES).filter { it.isNotEmpty() } }.map(::lower)
            return SearchText(whole, separated, whole.joinToString(""))
        }

        /** Lower-case words as typed, without camel-case separation. */
        internal fun queryWords(text: String): List<String> = rawWords(text).map(::lower)

        /** Accents are removed before splitting; case is kept for camel-case boundaries. */
        private fun rawWords(text: String): List<String> =
            SEPARATORS.split(DIACRITICS.replace(Normalizer.normalize(text.take(MAX_LENGTH), Normalizer.Form.NFD), "")).filter { it.isNotEmpty() }

        private fun lower(text: String): String = text.lowercase(Locale.ROOT)
    }
}

/**
 * Ranked matching for the app and icon pickers. Every query word must match a
 * word in some field. Complete names beat leading words, whole words beat
 * partial ones, and a small typo still finds the result. The first field is the
 * primary name; later fields such as package names rank lower.
 */
class SearchQuery(raw: String) {
    private val tokens = SearchText.queryWords(raw.take(MAX_QUERY)).distinct().take(MAX_TOKENS)
    private val compact = tokens.joinToString("")

    val isBlank: Boolean get() = compact.isEmpty()

    /** Higher is better; null when the fields do not match. */
    fun score(fields: List<SearchText>): Int? {
        if (isBlank) return 0
        var best = -1
        fields.forEachIndexed { index, field ->
            val whole = when {
                field.compact.isEmpty() -> -1
                field.compact == compact -> EXACT
                field.startsWithWords(tokens) -> LEADING_WORDS
                tokens.all { it in field.words } -> WHOLE_WORDS
                field.compact.startsWith(compact) -> PREFIX
                compact.length >= 2 && field.initials.startsWith(compact) -> INITIALS
                compact.length >= 3 && field.compact.contains(compact) -> CONTAINS
                // A typo in a short word still counts across the whole name: "droid dek".
                compact.length >= 5 && withinTypos(compact, field.compact, allowed = 1) -> WHOLE_TYPO
                else -> -1
            }
            if (whole >= 0) best = maxOf(best, whole - index * SECONDARY_PENALTY)
        }
        var total = 0
        for (token in tokens) {
            var tokenBest = -1
            fields.forEachIndexed { index, field ->
                for (word in field.words) {
                    val score = wordScore(token, word)
                    if (score >= 0) tokenBest = maxOf(tokenBest, if (index == 0) score else score * 2 / 3)
                }
            }
            if (tokenBest < 0) return best.takeIf { it >= 0 }
            total += tokenBest
        }
        val average = total / tokens.size
        // Word quality breaks ties: chrome_beta (whole word) before chromecast.
        return maxOf(best, WORDS + average) + average / 10
    }

    /** Best matches first, keeping the input order for ties; a blank query keeps every item. */
    fun <T> rank(items: List<T>, fields: (T) -> List<SearchText>): List<T> {
        if (isBlank) return items
        return items.mapIndexedNotNull { index, item -> score(fields(item))?.let { Triple(item, it, index) } }
            .sortedWith(compareByDescending<Triple<T, Int, Int>> { it.second }.thenBy { it.third })
            .map { it.first }
    }

    private fun wordScore(token: String, word: String): Int = when {
        word == token -> 120
        word.startsWith(token) -> 95
        token.length >= 3 && word.contains(token) -> 60
        // Shorter words have too many one-letter neighbours: "chat" would find "what".
        token.length >= 5 && withinTypos(token, word) -> 40
        else -> -1
    }

    /** One edit (two for long words), also against a longer word's start while still typing. */
    private fun withinTypos(token: String, word: String, allowed: Int = if (token.length >= 8) 2 else 1): Boolean {
        if (kotlin.math.abs(word.length - token.length) <= allowed && distance(token, word, allowed) <= allowed) return true
        val lengths = (token.length - allowed)..minOf(token.length + allowed, word.length - 1)
        return lengths.any { length -> length > 0 && distance(token, word.substring(0, length), allowed) <= allowed }
    }

    companion object {
        private const val MAX_QUERY = 128
        private const val MAX_TOKENS = 8
        private const val EXACT = 1_000
        private const val LEADING_WORDS = 900
        private const val WHOLE_WORDS = 750
        private const val PREFIX = 700
        private const val INITIALS = 600
        private const val CONTAINS = 500
        private const val WHOLE_TYPO = 350
        private const val WORDS = 300
        private const val SECONDARY_PENALTY = 150

        /** Optimal string alignment distance; returns limit + 1 once every row exceeds [limit]. */
        internal fun distance(a: String, b: String, limit: Int): Int {
            var beforePrevious = IntArray(b.length + 1)
            var previous = IntArray(b.length + 1) { it }
            var current = IntArray(b.length + 1)
            for (i in 1..a.length) {
                current[0] = i
                var rowMinimum = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    var value = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
                    if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) value = minOf(value, beforePrevious[j - 2] + 1)
                    current[j] = value
                    rowMinimum = minOf(rowMinimum, value)
                }
                if (rowMinimum > limit) return limit + 1
                val recycled = beforePrevious; beforePrevious = previous; previous = current; current = recycled
            }
            return previous[b.length]
        }
    }
}
