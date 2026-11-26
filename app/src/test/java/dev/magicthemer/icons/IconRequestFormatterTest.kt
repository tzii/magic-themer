package dev.magicthemer.icons

import dev.magicthemer.ApplicationTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconRequestFormatterTest {
    @Test fun formatsSortedComponentsPlayLinksAndAppfilterSnippets() {
        val apps = listOf(
            ApplicationTarget("com.zeta.player", "Zeta Player", listOf("com.zeta.player.MainActivity", "com.zeta.player.AltActivity")),
            ApplicationTarget("org.alpha.app", "Alpha --- Notes", emptyList()),
            ApplicationTarget("org.dup.app", "Zeta Player", listOf("org.dup.app.Main")),
        )
        val text = IconRequestFormatter.format("Arcticons", apps)
        assertTrue(text.startsWith("Icon request for Arcticons (3 apps)"))
        assertTrue(text.indexOf("Alpha --- Notes") < text.indexOf("Zeta Player"))
        assertTrue(text.contains("ComponentInfo{com.zeta.player/com.zeta.player.MainActivity}"))
        assertTrue(text.contains("ComponentInfo{com.zeta.player/com.zeta.player.AltActivity}"))
        assertTrue(text.contains("https://play.google.com/store/apps/details?id=com.zeta.player"))
        assertTrue(text.contains("ComponentInfo{org.alpha.app}"))
        assertTrue(text.contains("""<item component="ComponentInfo{com.zeta.player/com.zeta.player.MainActivity}" drawable="zeta_player" />"""))
        assertTrue(text.contains("""<item component="ComponentInfo{com.zeta.player/com.zeta.player.AltActivity}" drawable="zeta_player" />"""))
        assertTrue(text.contains("""drawable="zeta_player_2""""))
        // Multiple hyphens in XML comments must never leave "--".
        assertTrue(text.contains("<!-- Alpha - Notes -->"))

        // Capping avoids Binder TransactionTooLargeException on very large lists.
        val capped = IconRequestFormatter.format("Pack", apps, maxApps = 2)
        assertTrue(capped.contains("...and 1 more app omitted."))
    }

    @Test fun singleAppPluralAndSlugFallbacksAreBounded() {
        val single = IconRequestFormatter.format(null, listOf(ApplicationTarget("com.example.one", "One", emptyList())))
        assertTrue(single.startsWith("Icon request (1 app)"))
        assertEquals("my_app", IconRequestFormatter.drawableSlug("My App!", "com.example.foo"))
        assertEquals("foo", IconRequestFormatter.drawableSlug("1234", "com.example.foo"))
        assertEquals("app_123", IconRequestFormatter.drawableSlug("!!!", "com.example.123"))
        assertTrue(IconRequestFormatter.drawableSlug("!!!", "com.example." + "9".repeat(80)).length <= 64)
    }
}
