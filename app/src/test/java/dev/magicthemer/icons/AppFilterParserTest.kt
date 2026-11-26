package dev.magicthemer.icons

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFilterParserTest {
    private fun parse(xml: String, day: Int = 7) = AppFilterParser.parse(ByteArrayInputStream(xml.toByteArray()), day)

    @Test fun expandsShortComponentsAndRetainsAppsNotInstalled() {
        val result = parse("""<resources>
            <item component="ComponentInfo{com.example.app/.MainActivity}" drawable="example"/>
            <item component="ComponentInfo{com.other.app/com.other.Activity}" drawable="other"/>
            </resources>""")
        assertEquals(2, result.mappings.size)
        assertEquals("com.example.app.MainActivity", result.mappings.first().activityName)
        assertEquals("ComponentInfo{com.example.app/com.example.app.MainActivity}", result.mappings.first().component)
    }

    @Test fun skipsInvalidIdentifiersAndLauncherCommands() {
        val result = parse("""<resources>
            <item component="ComponentInfo{com.good.app/.Main}" drawable="../outside"/>
            <item component="ComponentInfo{../bad/.Main}" drawable="bad"/>
            <item component=":LAUNCHER_ACTION_APP_DRAWER" drawable="drawer"/>
            <item component="ComponentInfo{com.good.app/.Main}" drawable="good"/>
            </resources>""")
        assertEquals(listOf("good"), result.mappings.map { it.drawableName })
        assertTrue(result.warnings.any { it.startsWith("3 invalid") })
    }

    @Test fun calendarSnapshotOverridesStaticEntryRegardlessOfOrder() {
        val result = parse("""<resources>
            <calendar component="ComponentInfo{com.calendar.app/.Main}" prefix="calendar_"/>
            <item component="ComponentInfo{com.calendar.app/.Main}" drawable="calendar_static"/>
            </resources>""")
        assertEquals(listOf("calendar_7"), result.mappings.map { it.drawableName })
        assertTrue(result.warnings.any { it.contains("snapshot for day 7") })
    }

    @Test fun reportsUnsupportedFallbackAndDynamicClock() {
        val result = parse("""<resources><iconback img2="back2" img1="back1"/><iconmask img1="mask"/><iconupon img1="upon"/>
            <scale factor="0.75"/><dynamic-clock drawable="clock"/></resources>""")
        assertEquals(2, result.warnings.size)
        assertTrue(result.warnings.any { it.contains("unmapped apps") })
        assertTrue(result.warnings.any { it.contains("hands do not update") })
        assertEquals(listOf("back1", "back2"), result.fallbackStyle.backgrounds)
        assertEquals(listOf("mask"), result.fallbackStyle.masks)
        assertEquals(listOf("upon"), result.fallbackStyle.upons)
        assertEquals(0.75f, result.fallbackStyle.scale, 0.001f)
    }

    @Test fun calendarMappingPreservesPrefixAlongsideSnapshot() {
        val result = parse("""<resources>
            <calendar component="ComponentInfo{com.hihonor.calendar/.Main}" prefix="cal_"/>
            <calendar component=":CALENDAR" prefix="nova_cal_"/>
            </resources>""", day = 12)
        assertEquals(listOf("cal_12"), result.mappings.map { it.drawableName })
        assertEquals(2, result.calendarMappings.size)
        assertEquals("cal_", result.calendarMappings[0].prefix)
        assertEquals("com.hihonor.calendar", result.calendarMappings[0].packageName)
        assertEquals(":CALENDAR", result.calendarMappings[1].component)
        assertEquals("nova_cal_", result.calendarMappings[1].prefix)
    }

    @Test fun retainsPackageOnlyMappingsWithoutInventingActivity() {
        val result = parse("""<resources>
            <item component="ComponentInfo{com.example.app}" drawable="package_icon"/>
            <item component="ComponentInfo{com.example.app/.MainActivity}" drawable="activity_icon"/>
            </resources>""")
        assertEquals(2, result.mappings.size)
        assertEquals("com.example.app", result.mappings[0].packageName)
        assertEquals("", result.mappings[0].activityName)
        assertEquals("ComponentInfo{com.example.app}", result.mappings[0].component)
        assertEquals("com.example.app.MainActivity", result.mappings[1].activityName)
        assertTrue(result.warnings.isEmpty())
    }

    @Test fun defaultCatalogueDeduplicatesMappedArtwork() {
        val result = parse("""<resources>
            <item component="ComponentInfo{com.example.app/.MainActivity}" drawable="shared"/>
            <item component="ComponentInfo{com.other.app/.MainActivity}" drawable="shared"/>
            </resources>""")
        val data = PackData(IconPack("com.test.pack", "Pack"), result.mappings, result.warnings)
        assertEquals(listOf("shared"), data.drawables)
        assertEquals(2, data.mappings.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDtdBeforeXmlParsing() {
        parse("""<!DOCTYPE resources SYSTEM "file:///etc/passwd"><resources/>""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonResourcesRoot() { parse("<other/>") }
}
