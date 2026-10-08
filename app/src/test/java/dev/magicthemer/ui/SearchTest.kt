package dev.magicthemer.ui

import org.junit.Assert.*
import org.junit.Test

class SearchTest {
    private fun rank(query: String, vararg names: String): List<String> =
        SearchQuery(query).rank(names.toList()) { listOf(SearchText.of(it)) }

    private fun matches(query: String, vararg fields: String): Boolean =
        SearchQuery(query).score(fields.map(SearchText::of)) != null

    @Test fun exactNameRanksBeforeLeadingWordsWholeWordsAndPrefixes() {
        assertEquals(listOf("chrome", "chrome_beta", "google_chrome", "chromecast", "xchromex"),
            rank("chrome", "xchromex", "chromecast", "google_chrome", "chrome_beta", "chrome"))
        assertEquals(listOf("Maps", "Google Maps Go", "Mapsme"), rank("maps", "Mapsme", "Google Maps Go", "Maps"))
        assertEquals(listOf("Telegram", "Hotel Deals"), rank("tel", "Hotel Deals", "Telegram"))
    }

    @Test fun joinedAndSeparatedSpellingsMatchEachOther() {
        assertTrue(matches("droid deck", "DroidDeck"))
        assertTrue(matches("droiddeck", "Droid Deck"))
        assertTrue(matches("whatsapp", "whats_app"))
        assertTrue(matches("archive tune", "ArchiveTune"))
    }

    @Test fun everyQueryWordMustMatchSomewhere() {
        assertTrue(matches("google maps", "Google Maps"))
        assertTrue(matches("maps google", "Google Maps"))
        assertFalse(matches("google music", "Google Maps"))
        assertTrue(matches("ente auth", "Ente Auth", "io.ente.auth"))
    }

    @Test fun singleTyposAndPartialTypingStillMatch() {
        assertTrue(matches("watsapp", "WhatsApp"))
        assertTrue(matches("telgram", "Telegram"))
        assertTrue(matches("spotfy", "Spotify"))
        assertTrue(matches("instgr", "Instagram"))
        assertTrue(matches("droid dek", "DroidDeck"))
        assertTrue(matches("google mpas", "Google Maps"))
        assertFalse(matches("abc", "Instagram"))
        assertFalse(matches("cat", "Chat"))
        assertFalse(matches("chat", "WhatsApp"))
        assertFalse(matches("google maps", "Google Pays"))
    }

    @Test fun initialsAccentsAndCaseAreHandled() {
        assertTrue(matches("yt", "YouTube"))
        assertTrue(matches("gm", "Google Maps"))
        assertTrue(matches("meteo", "Météo"))
        assertTrue(matches("MÉTÉO", "meteo"))
    }

    @Test fun primaryFieldOutranksSecondaryField() {
        val query = SearchQuery("google")
        val label = query.score(listOf(SearchText.of("Google"), SearchText.of("com.google.android.googlequicksearchbox")))!!
        val packageOnly = query.score(listOf(SearchText.of("Gmail"), SearchText.of("com.google.android.gm")))!!
        assertTrue(label > packageOnly)
    }

    @Test fun blankQueryKeepsOrderAndUnmatchedItemsAreDropped() {
        assertEquals(listOf("b", "a"), rank("  ", "b", "a"))
        assertEquals(emptyList<String>(), rank("zzz", "alpha", "beta"))
    }

    @Test fun distanceCountsTranspositionsAndStopsAtTheLimit() {
        assertEquals(1, SearchQuery.distance("teh", "the", 1))
        assertEquals(0, SearchQuery.distance("same", "same", 1))
        assertEquals(2, SearchQuery.distance("abcd", "wxyz", 1))
    }
}
