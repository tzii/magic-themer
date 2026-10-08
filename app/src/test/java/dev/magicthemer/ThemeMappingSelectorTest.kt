package dev.magicthemer

import dev.magicthemer.icons.IconMapping
import org.junit.Assert.*
import org.junit.Test

class ThemeMappingSelectorTest {
    private fun icon(pkg: String, activity: String, drawable: String) =
        IconMapping("ComponentInfo{$pkg/$activity}", pkg, activity, drawable)

    @Test fun launcherActivityWinsOverOtherPackEntriesForSamePackage() {
        val mappings = listOf(icon("com.example", "com.example.Hidden", "wrong"), icon("com.example", "com.example.Main", "correct"))
        val selected = ThemeMappingSelector.select(mappings, listOf(ApplicationTarget("com.example", "App", listOf("com.example.Main"))), false)
        assertEquals(listOf("correct"), selected.map { it.drawableName })
    }

    @Test fun honorCanOnlyThemeBuildsWhoseResourcesUseTheInstalledPackage() {
        assertTrue(ApplicationTarget("com.example", "App", emptyList()).honorThemeable)
        assertTrue(ApplicationTarget("com.example", "App", emptyList(), "com.example").honorThemeable)
        // DroidDeck's AnTuTu variant: only the manifest package was renamed after compilation.
        assertFalse(ApplicationTarget("com.antutu.benchmark.full", "DroidDeck", emptyList(), "com.droiddeck.launcher").honorThemeable)
    }

    @Test fun installedOnlyAndAllModesProduceDifferentCoverage() {
        val mappings = listOf(icon("com.present", "com.present.Main", "present"), icon("com.absent", "com.absent.Main", "absent"))
        val apps = listOf(ApplicationTarget("com.present", "Present", listOf("com.present.Main")))
        assertEquals(1, ThemeMappingSelector.select(mappings, apps, false).size)
        assertEquals(2, ThemeMappingSelector.select(mappings, apps, true).size)
    }

    @Test fun manualAssignmentAddsUnmappedInstalledAppAndKeepsSingleIconPerPackage() {
        val app = ApplicationTarget("com.custom", "Custom", listOf("com.custom.Main"))
        val custom = icon(app.packageName, app.activities.first(), "chosen")
        assertEquals(listOf(custom), ThemeMappingSelector.select(emptyList(), listOf(app), false, mapOf(app.packageName to custom)))
        assertEquals(listOf(custom), ThemeMappingSelector.select(listOf(custom, custom.copy(drawableName = "other")), listOf(app), true, mapOf(app.packageName to custom)))
    }
}
