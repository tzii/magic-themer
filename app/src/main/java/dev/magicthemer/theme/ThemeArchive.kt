package dev.magicthemer.theme

import java.io.File
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes an experimental, icon-only HONOR theme. Device acceptance must be
 * verified in the Themes app; exporting an archive does not apply it.
 *
 * Both extensions use the HwTheme 12 description observed in a theme that
 * the user's MagicOS 10 EU Themes app accepts. The archive's `icons`
 * entry is itself an extensionless ZIP with flat package-name PNG entries.
 * The schema version is a theme format version, not the phone's OS version.
 */
object ThemeArchive {
    const val SCHEMA_VERSION = "12"
    const val MAX_ICON_COUNT = 20_000
    const val MAX_ICON_BYTES = 256 * 1024
    const val MAX_TOTAL_ICON_BYTES = 64L * 1024 * 1024
    const val MAX_PREVIEW_BYTES = 4 * 1024 * 1024

    private val packagePattern = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")
    private val pngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)

    /** Validates every input before writing, then closes [output] on completion. */
    fun write(
        output: OutputStream,
        title: String,
        sourceLabel: String,
        icons: List<ThemeIcon>,
        extension: String,
        preview: ByteArray? = null,
        iconsPreview: ByteArray? = null,
        tempDirectory: File? = null,
        designer: String = "Magic Themer",
        briefInfo: String? = null,
    ) {
        require(extension == "hnt" || extension == "hwt") { "Choose the hnt or hwt theme format." }
        require(title.isNotBlank() && title.length <= 64) { "Theme title must contain 1–64 characters." }
        require(sourceLabel.isNotBlank() && sourceLabel.length <= 256) { "Icon pack label must contain 1–256 characters." }
        require(isXmlText(title) && isXmlText(sourceLabel)) { "Theme metadata contains invalid XML characters." }
        val resolvedBriefInfo = briefInfo ?: "Icons converted locally from $sourceLabel."
        require(designer.length <= 128 && isXmlText(designer)) { "Designer must contain up to 128 valid XML characters." }
        require(resolvedBriefInfo.length <= 4096 && isXmlText(resolvedBriefInfo)) { "Description must contain up to 4096 valid XML characters." }
        require(icons.isNotEmpty() && icons.size <= MAX_ICON_COUNT) { "Choose between 1 and $MAX_ICON_COUNT icons." }

        val seen = HashSet<String>()
        var totalBytes = 0L
        icons.forEach { icon ->
            require(icon.packageName.length <= 255 && packagePattern.matches(icon.packageName)) {
                "Invalid Android package name: ${icon.packageName.take(80)}"
            }
            require(seen.add(icon.packageName)) { "Duplicate icon package: ${icon.packageName}" }
            require(icon.png.size in pngSignature.size..MAX_ICON_BYTES) { "Icon exceeds the supported PNG size." }
            require(pngSignature.indices.all { icon.png[it] == pngSignature[it] }) { "Icon is not a PNG." }
            totalBytes += icon.png.size
            require(totalBytes <= MAX_TOTAL_ICON_BYTES) { "Icon data exceeds 64 MiB." }
        }
        listOfNotNull(preview, iconsPreview).forEach {
            require(it.size in 3..MAX_PREVIEW_BYTES) { "Preview exceeds the supported JPEG size." }
            require(it[0] == 0xff.toByte() && it[1] == 0xd8.toByte() && it[2] == 0xff.toByte()) {
                "Preview is not a JPEG."
            }
        }

        val description = """<?xml version="1.0" encoding="UTF-8"?>
<HwTheme>
    <title>${xmlEscape(title)}</title>
    <title-cn>${xmlEscape(title)}</title-cn>
    <author>${xmlEscape(designer)}</author>
    <designer>${xmlEscape(designer)}</designer>
    <version>$SCHEMA_VERSION</version>
    <font>Default</font>
    <font-cn>Default</font-cn>
    <briefInfo>${xmlEscape(resolvedBriefInfo)}</briefInfo>
</HwTheme>
""".toByteArray(StandardCharsets.UTF_8)

        // Keep the complete inner archive on disk instead of retaining a second
        // copy of a whole pack's rendered PNG data in memory.
        val nestedIcons = File.createTempFile("theme-export-icons-", ".zip", tempDirectory)
        try {
            ZipOutputStream(nestedIcons.outputStream(), StandardCharsets.UTF_8).use { zip ->
                icons.sortedBy { it.packageName }.forEach { icon ->
                    put(zip, "${icon.packageName}.png", icon.png)
                }
            }
            val unlockPreview = iconsPreview ?: preview
            validateCompletedArchive(nestedIcons.length(), totalBytes, description.size, preview?.size ?: 0,
                iconsPreview?.size ?: 0, unlockPreview?.size ?: 0)
            ZipOutputStream(output, StandardCharsets.UTF_8).use { zip ->
                put(zip, "description.xml", description)
                // An empty lock-screen declaration accompanies the accepted
                // profile. It contains no proprietary artwork or system module.
                put(zip, "unlock/theme.xml", "<HWTheme />".toByteArray(StandardCharsets.UTF_8))
                zip.putNextEntry(ZipEntry("icons").apply { time = 0L })
                nestedIcons.inputStream().use { it.copyTo(zip, 32 * 1024) }
                zip.closeEntry()
                preview?.let { put(zip, "preview/icon_small.jpg", it) }
                iconsPreview?.let { put(zip, "preview/preview_icons_0.jpg", it) }
                unlockPreview?.let { put(zip, "preview/preview_unlock_0.jpg", it) }
            }
        } finally {
            nestedIcons.delete()
        }
    }

    /** Include the completed ZIP's headers/compression overhead and every outer member. */
    internal fun validateCompletedArchive(
        innerBytes: Long,
        rawIconBytes: Long,
        descriptionBytes: Int,
        previewBytes: Int,
        iconsPreviewBytes: Int,
        unlockPreviewBytes: Int = 0,
    ) {
        require(innerBytes in 0..ThemeEditor.MAX_MEMBER_BYTES) { "Packed icons ZIP exceeds 128 MiB; select fewer icons." }
        require(rawIconBytes in 0..MAX_TOTAL_ICON_BYTES && descriptionBytes >= 0 && previewBytes >= 0 && iconsPreviewBytes >= 0 && unlockPreviewBytes >= 0) { "Invalid packed theme size." }
        val outerMemberBytes = descriptionBytes.toLong() + previewBytes + iconsPreviewBytes + unlockPreviewBytes + 11
        require(rawIconBytes + outerMemberBytes <= ThemeEditor.MAX_EXPANDED_BYTES - innerBytes) {
            "Packed theme data exceeds the 512 MiB import limit; select fewer icons or use smaller previews."
        }
    }

    private fun put(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        // DEFLATED matches the authored converter's ordinary ZIP packaging.
        // A fixed timestamp and sorted icons make repeated exports identical.
        val entry = ZipEntry(name).apply { time = 0L }
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun xmlEscape(value: String): String = buildString {
        value.forEach { character ->
            append(when (character) {
                '&' -> "&amp;"
                '<' -> "&lt;"
                '>' -> "&gt;"
                '"' -> "&quot;"
                '\'' -> "&apos;"
                else -> character.toString()
            })
        }
    }

    private fun isXmlText(value: String): Boolean {
        var index = 0
        while (index < value.length) {
            val codePoint = Character.codePointAt(value, index)
            if (codePoint != 0x09 && codePoint != 0x0a && codePoint != 0x0d &&
                codePoint !in 0x20..0xd7ff && codePoint !in 0xe000..0xfffd &&
                codePoint !in 0x10000..0x10ffff
            ) return false
            index += Character.charCount(codePoint)
        }
        return true
    }
}
