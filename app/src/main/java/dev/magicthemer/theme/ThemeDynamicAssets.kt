package dev.magicthemer.theme

import dev.magicthemer.icons.CalendarMapping
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Fallback assets for HONOR's launcher to style apps that have no explicit icon.
 *
 * Per HONOR/EMUI's `HwResourcesImpl` and `IconBitmapUtils`:
 * - `icon_mask.png` multiplies each app-icon pixel's alpha by the mask's alpha
 *   (opaque keeps, transparent clips — the inverse of Android's DST_OUT `<iconmask>`).
 * - `icon_background_0.png`, `icon_background_1.png`, ... are drawn behind the masked icon.
 * - `icon_border.png` is drawn on top, AND must be present for `icon_background*.png` to load.
 */
data class ThemeFallbackStyle(
    val maskPng: ByteArray? = null,
    val borderPng: ByteArray? = null,
    val backgroundPngs: List<ByteArray> = emptyList(),
) {
    val isEmpty: Boolean get() = maskPng == null && borderPng == null && backgroundPngs.isEmpty()
}

/**
 * Dynamic calendar background (`dynamic_icons/com.android.calendar/calendar_background.png`)
 * and contrasting date text color for `com.hihonor.android.launcher`.
 */
data class ThemeDynamicCalendar(
    val backgroundPng: ByteArray,
    val dateColorHex: String,
)

/** Pure pixel-math helpers for fallback masks and dynamic calendar extraction. */
internal object ThemeDynamicAssets {
    const val MAX_BACKGROUNDS = 16
    val CALENDAR_SAMPLE_DAYS = listOf(1, 4, 7, 11, 14, 17, 20, 24, 28, 30)
    private val HEX_COLOR_PATTERN = Regex("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")
    private val PREFERRED_CALENDAR_PACKAGES = listOf(
        "com.hihonor.calendar",
        "com.android.calendar",
        "com.huawei.calendar",
    )

    fun isValidHexColor(hex: String): Boolean = HEX_COLOR_PATTERN.matches(hex)

    /**
     * Converts an Android ADW/Nova `<iconmask>` (DST_OUT: opaque erases, transparent keeps)
     * into HONOR's `icon_mask.png` (DST_IN: opaque black keeps, transparent clips).
     * When [scale] < 1 and a background is present, shrinks the kept silhouette around
     * the center so HONOR scales unthemed icons down inside `icon_background_*.png`.
     */
    fun invertAlphaToBlackMask(sourcePixels: IntArray, width: Int, height: Int, scale: Float = 1f): IntArray {
        require(sourcePixels.size == width * height && width > 0 && height > 0)
        val inverted = IntArray(sourcePixels.size) { i ->
            val keepAlpha = 255 - (sourcePixels[i] ushr 24)
            keepAlpha shl 24
        }
        return scaleCenteredAlphaMask(inverted, width, height, scale)
    }

    /**
     * Derives an HONOR `icon_mask.png` silhouette from an `<iconback>` drawable's alpha
     * channel when the pack does not ship an `<iconmask>`.
     */
    fun alphaSilhouetteToBlackMask(sourcePixels: IntArray, width: Int, height: Int, scale: Float = 1f): IntArray {
        require(sourcePixels.size == width * height && width > 0 && height > 0)
        val silhouette = IntArray(sourcePixels.size) { i -> sourcePixels[i] and (0xff shl 24) }
        return scaleCenteredAlphaMask(silhouette, width, height, scale)
    }

    private fun scaleCenteredAlphaMask(maskPixels: IntArray, width: Int, height: Int, scale: Float): IntArray {
        val clamped = if (scale.isFinite()) scale.coerceIn(0.25f, 1f) else 1f
        if (clamped >= 0.995f || width <= 1 || height <= 1) return maskPixels
        val out = IntArray(width * height)
        val cx = (width - 1) / 2.0f
        val cy = (height - 1) / 2.0f
        for (y in 0 until height) {
            val sy = ((y - cy) / clamped) + cy
            val y0 = sy.toInt()
            if (sy < 0f || sy >= height - 1) continue
            val fy = sy - y0
            val row0 = y0 * width
            val row1 = (y0 + 1) * width
            for (x in 0 until width) {
                val sx = ((x - cx) / clamped) + cx
                if (sx < 0f || sx >= width - 1) continue
                val x0 = sx.toInt()
                val fx = sx - x0
                val a00 = maskPixels[row0 + x0] ushr 24
                val a10 = maskPixels[row0 + x0 + 1] ushr 24
                val a01 = maskPixels[row1 + x0] ushr 24
                val a11 = maskPixels[row1 + x0 + 1] ushr 24
                val top = a00 + (a10 - a00) * fx
                val bottom = a01 + (a11 - a01) * fx
                val alpha = (top + (bottom - top) * fy).roundToInt().coerceIn(0, 255)
                out[y * width + x] = alpha shl 24
            }
        }
        return out
    }

    /** Pick the most relevant `<calendar>` mapping for HONOR's live calendar icon. */
    fun selectCalendarMapping(mappings: List<CalendarMapping>, installedPackages: Set<String>): CalendarMapping? {
        if (mappings.isEmpty()) return null
        for (preferred in PREFERRED_CALENDAR_PACKAGES) {
            mappings.firstOrNull { it.packageName == preferred }?.let { return it }
        }
        mappings.firstOrNull { it.packageName in installedPackages }?.let { return it }
        return mappings.first()
    }

    /**
     * Reconstructs a blank `calendar_background.png` and extracts the pack's digit color
     * by comparing several day-of-month calendar bitmaps (e.g. days 1, 4, 7, 11, 14, ...).
     *
     * Background pixels match across the majority of days; digit pixels differ only in the
     * subset of days whose strokes pass through that pixel.
     */
    fun extractBackgroundAndDateColor(samples: List<IntArray>, width: Int, height: Int): Pair<IntArray, String> {
        val n = samples.size
        require(n >= 4) { "At least 4 calendar day bitmaps are required." }
        val pixelCount = width * height
        require(pixelCount > 0 && samples.all { it.size == pixelCount }) { "Calendar samples must have matching dimensions." }

        val background = IntArray(pixelCount)
        val alphas = IntArray(n)
        val reds = IntArray(n)
        val greens = IntArray(n)
        val blues = IntArray(n)
        val majority = (n / 2) + 1

        val digitReds = ArrayList<Int>(1024)
        val digitGreens = ArrayList<Int>(1024)
        val digitBlues = ArrayList<Int>(1024)

        for (i in 0 until pixelCount) {
            // Fast exact majority check (handles >99% of pixels without sorting).
            var modeValue = samples[0][i]
            var modeCount = 0
            for (s in 0 until n) {
                val v = samples[s][i]
                var count = 0
                for (t in s until n) if (samples[t][i] == v) count++
                if (count > modeCount) {
                    modeCount = count
                    modeValue = v
                }
                if (modeCount >= majority) break
            }
            val bgPixel = if (modeCount >= majority) {
                modeValue
            } else {
                for (s in 0 until n) {
                    val v = samples[s][i]
                    alphas[s] = (v ushr 24) and 0xff
                    reds[s] = (v ushr 16) and 0xff
                    greens[s] = (v ushr 8) and 0xff
                    blues[s] = v and 0xff
                }
                alphas.sort(); reds.sort(); greens.sort(); blues.sort()
                val mid = n / 2
                (alphas[mid] shl 24) or (reds[mid] shl 16) or (greens[mid] shl 8) or blues[mid]
            }
            background[i] = bgPixel

            // Collect opaque pixels that clearly diverge from the reconstructed background:
            // those belong to the rendered calendar digits.
            val bgA = (bgPixel ushr 24) and 0xff
            val bgR = (bgPixel ushr 16) and 0xff
            val bgG = (bgPixel ushr 8) and 0xff
            val bgB = bgPixel and 0xff
            for (s in 0 until n) {
                val v = samples[s][i]
                if (v == bgPixel) continue
                val a = (v ushr 24) and 0xff
                if (a < 160) continue
                val r = (v ushr 16) and 0xff
                val g = (v ushr 8) and 0xff
                val b = v and 0xff
                val diff = abs(a - bgA) + abs(r - bgR) + abs(g - bgG) + abs(b - bgB)
                if (diff > 96) {
                    digitReds += r
                    digitGreens += g
                    digitBlues += b
                }
            }
        }

        val dateColorHex = if (digitReds.size >= 20) {
            digitReds.sort(); digitGreens.sort(); digitBlues.sort()
            val mid = digitReds.size / 2
            String.format(Locale.ROOT, "#%02x%02x%02x", digitReds[mid], digitGreens[mid], digitBlues[mid])
        } else {
            contrastingFallbackColor(background, width, height)
        }
        return background to dateColorHex
    }

    private fun contrastingFallbackColor(background: IntArray, width: Int, height: Int): String {
        val xStart = width / 5
        val xEnd = width - xStart
        val yStart = height / 5
        val yEnd = height - yStart
        var lumSum = 0.0
        var count = 0
        for (y in yStart until yEnd) {
            val row = y * width
            for (x in xStart until xEnd) {
                val p = background[row + x]
                val a = (p ushr 24) and 0xff
                if (a < 128) continue
                val r = (p ushr 16) and 0xff
                val g = (p ushr 8) and 0xff
                val b = p and 0xff
                lumSum += 0.299 * r + 0.587 * g + 0.114 * b
                count++
            }
        }
        if (count == 0) return "#ffffff"
        return if (lumSum / count > 150.0) "#1c1b1f" else "#ffffff"
    }

    /** Minimal `theme.xml` for `com.hihonor.android.launcher` setting live calendar text colors. */
    fun launcherThemeXml(dateColorHex: String): ByteArray {
        require(isValidHexColor(dateColorHex)) { "Invalid calendar date color." }
        return """<?xml version="1.0" encoding="UTF-8"?>
<resources>
    <color name="calendar_dynamic_date">$dateColorHex</color>
    <color name="calendar_dynamic">$dateColorHex</color>
    <color name="calendar_dynamic_shadow">#00000000</color>
</resources>
""".toByteArray(StandardCharsets.UTF_8)
    }
}
