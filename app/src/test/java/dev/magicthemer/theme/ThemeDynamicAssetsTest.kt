package dev.magicthemer.theme

import dev.magicthemer.icons.CalendarMapping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeDynamicAssetsTest {
    @Test fun invertsAndroidDstOutMaskToHonorKeepAlpha() {
        // 4x4 mask where the 2x2 center is transparent (kept in DST_OUT) and border is opaque (erased).
        val width = 4
        val height = 4
        val src = IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            val inside = x in 1..2 && y in 1..2
            if (inside) 0x00ffffff else 0xff123456.toInt()
        }
        val honor = ThemeDynamicAssets.invertAlphaToBlackMask(src, width, height, 1f)
        for (i in honor.indices) {
            val x = i % width
            val y = i / width
            val inside = x in 1..2 && y in 1..2
            assertEquals("Pixel ($x,$y) RGB must be black", 0, honor[i] and 0x00ffffff)
            assertEquals("Pixel ($x,$y) alpha", if (inside) 255 else 0, honor[i] ushr 24)
        }
    }

    @Test fun scalingMaskShrinksKeptRegionAroundCenter() {
        val size = 20
        val solidKeep = IntArray(size * size) { 0x00000000 } // transparent in DST_OUT => all kept
        val scaled = ThemeDynamicAssets.invertAlphaToBlackMask(solidKeep, size, size, 0.5f)
        // Corners must become transparent; center must stay opaque.
        assertEquals(0, scaled[0] ushr 24)
        assertEquals(0, scaled[size - 1] ushr 24)
        assertEquals(255, scaled[(size / 2) * size + (size / 2)] ushr 24)
    }

    @Test fun alphaSilhouetteMaskPreservesBackgroundShapeAndScales() {
        val size = 16
        val bg = IntArray(size * size) { i ->
            val x = i % size
            val y = i / size
            if (x in 2..13 && y in 2..13) 0xffff0000.toInt() else 0x00000000
        }
        val mask = ThemeDynamicAssets.alphaSilhouetteToBlackMask(bg, size, size, 1f)
        assertEquals(0, mask[0] ushr 24)
        assertEquals(255, mask[8 * size + 8] ushr 24)
        assertEquals(0, mask[8 * size + 8] and 0x00ffffff)
    }

    @Test fun extractsCleanCalendarBackgroundAndDigitColorForFilledPack() {
        val size = 24
        val bgColor = 0xfff4f4f4.toInt()
        val headerColor = 0xffd32f2f.toInt()
        val digitColor = 0xff223344.toInt()

        fun sample(digitXRange: IntRange): IntArray = IntArray(size * size) { i ->
            val x = i % size
            val y = i / size
            when {
                y < 6 -> headerColor
                y in 10..18 && x in digitXRange -> digitColor
                else -> bgColor
            }
        }

        // Six day samples with distinct digit stroke positions; no pixel is covered by digits in a majority.
        val samples = listOf(
            sample(4..6),
            sample(7..9),
            sample(10..12),
            sample(13..15),
            sample(16..18),
            sample(19..21),
        )
        val (bg, dateHex) = ThemeDynamicAssets.extractBackgroundAndDateColor(samples, size, size)
        assertEquals("#223344", dateHex)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val expected = if (y < 6) headerColor else bgColor
                assertEquals("Pixel ($x,$y) must be clean background", expected, bg[y * size + x])
            }
        }
    }

    @Test fun extractsCleanCalendarBackgroundAndWhiteDigitsForTransparentLinePack() {
        val size = 20
        val outline = 0xffffffff.toInt()
        val transparent = 0x00000000

        fun sample(digitX: Int): IntArray = IntArray(size * size) { i ->
            val x = i % size
            val y = i / size
            when {
                x == 1 || x == size - 2 || y == 1 || y == size - 2 -> outline
                x in digitX..(digitX + 1) && y in 6..15 -> outline
                else -> transparent
            }
        }

        val samples = listOf(sample(4), sample(7), sample(10), sample(13), sample(15))
        val (bg, dateHex) = ThemeDynamicAssets.extractBackgroundAndDateColor(samples, size, size)
        assertEquals("#ffffff", dateHex)
        // Outline preserved, interior completely cleared to transparent.
        assertEquals(outline, bg[1 * size + 5])
        assertEquals(transparent, bg[10 * size + 10])
    }

    @Test fun uniformCalendarSamplesFallBackToContrastingDateColor() {
        val size = 16
        val lightSamples = List(4) { IntArray(size * size) { 0xffffffff.toInt() } }
        assertEquals("#1c1b1f", ThemeDynamicAssets.extractBackgroundAndDateColor(lightSamples, size, size).second)
        val darkSamples = List(4) { IntArray(size * size) { 0xff111111.toInt() } }
        assertEquals("#ffffff", ThemeDynamicAssets.extractBackgroundAndDateColor(darkSamples, size, size).second)
    }

    @Test fun selectsHonorCalendarBeforeInstalledAndGenericCalendars() {
        val google = CalendarMapping("ComponentInfo{com.google.android.calendar/.Main}", "com.google.android.calendar", "com.google.android.calendar.Main", "gcal_")
        val honor = CalendarMapping("ComponentInfo{com.hihonor.calendar/.Main}", "com.hihonor.calendar", "com.hihonor.calendar.Main", "hcal_")
        val other = CalendarMapping("ComponentInfo{com.other.cal/.Main}", "com.other.cal", "com.other.cal.Main", "ocal_")
        assertEquals(honor, ThemeDynamicAssets.selectCalendarMapping(listOf(other, google, honor), setOf("com.google.android.calendar")))
        assertEquals(google, ThemeDynamicAssets.selectCalendarMapping(listOf(other, google), setOf("com.google.android.calendar")))
        assertEquals(other, ThemeDynamicAssets.selectCalendarMapping(listOf(other, google), emptySet()))
    }

    @Test fun launcherThemeXmlValidatesColorAndEmitsCalendarEntries() {
        val xml = ThemeDynamicAssets.launcherThemeXml("#12ab34").decodeToString()
        assertTrue(xml.contains("""<color name="calendar_dynamic_date">#12ab34</color>"""))
        assertTrue(xml.contains("""<color name="calendar_dynamic">#12ab34</color>"""))
        assertTrue(xml.contains("""<color name="calendar_dynamic_shadow">#00000000</color>"""))
        assertTrue(ThemeDynamicAssets.isValidHexColor("#ff12ab34"))
        assertFalse(ThemeDynamicAssets.isValidHexColor("red"))
        assertFalse(ThemeDynamicAssets.isValidHexColor("#12345"))
    }

    @Test fun handlesNanScaleAndEvenSampleTieWithoutGhosting() {
        val size = 8
        val keepAll = IntArray(size * size) { 0x00000000 }
        val nanScaled = ThemeDynamicAssets.invertAlphaToBlackMask(keepAll, size, size, Float.NaN)
        assertEquals(255, nanScaled[0] ushr 24)

        // With 4 samples and a 2-vs-2 split, strict majority (>2) triggers median fallback.
        val s1 = IntArray(size * size) { 0xff202020.toInt() }
        val s2 = IntArray(size * size) { 0xff202020.toInt() }
        val s3 = IntArray(size * size) { 0xffe0e0e0.toInt() }
        val s4 = IntArray(size * size) { 0xffe0e0e0.toInt() }
        val (bg, _) = ThemeDynamicAssets.extractBackgroundAndDateColor(listOf(s1, s2, s3, s4), size, size)
        assertEquals(size * size, bg.size)
    }
}
