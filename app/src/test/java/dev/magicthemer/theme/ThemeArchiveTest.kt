package dev.magicthemer.theme

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import dev.magicthemer.icons.AppFilterParser
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class ThemeArchiveTest {
    @get:Rule val folder = TemporaryFolder()
    private val png = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aL1sAAAAASUVORK5CYII="
    )

    @Test fun emitsReadableNestedArchiveAndXmlRoundTrip() {
        val title = "A & B <icons> \"quoted\" 'pack'"
        val source = "Example & snowman ☃"
        val result = export(title = title, source = source, icons = listOf(
            ThemeIcon("org.example.zed", png), ThemeIcon("com.example.alpha", png)
        ))
        val outer = unzip(result)
        assertEquals(listOf("description.xml", "unlock/theme.xml", "icons"), outer.keys.toList())
        val description = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }.newDocumentBuilder().parse(ByteArrayInputStream(outer.getValue("description.xml")))
        assertEquals("HwTheme", description.documentElement.tagName)
        assertEquals(title, description.getElementsByTagName("title").item(0).textContent)
        assertEquals(title, description.getElementsByTagName("title-cn").item(0).textContent)
        assertEquals("Magic Themer", description.getElementsByTagName("author").item(0).textContent)
        assertEquals("Default", description.getElementsByTagName("font").item(0).textContent)
        assertEquals("Default", description.getElementsByTagName("font-cn").item(0).textContent)
        assertEquals(ThemeArchive.SCHEMA_VERSION, description.getElementsByTagName("version").item(0).textContent)
        assertEquals("Icons converted locally from $source.", description.getElementsByTagName("briefInfo").item(0).textContent)
        val tags = (0 until description.documentElement.childNodes.length).mapNotNull { index ->
            description.documentElement.childNodes.item(index).takeIf { it.nodeType == 1.toShort() }?.nodeName
        }
        assertEquals(listOf("title", "title-cn", "author", "designer", "version", "font", "font-cn", "briefInfo"), tags)
        assertEquals("<HWTheme />", outer.getValue("unlock/theme.xml").decodeToString())
        val inner = unzip(outer.getValue("icons"))
        assertEquals(listOf("com.example.alpha.png", "org.example.zed.png"), inner.keys.toList())
        inner.values.forEach { assertArrayEquals(png, it) }
    }

    @Test fun deterministicBytesIgnoreInputIconOrdering() {
        val icons = listOf(ThemeIcon("org.example.zed", png), ThemeIcon("com.example.alpha", png))
        assertArrayEquals(export(icons = icons), export(icons = icons.reversed()))
        assertArrayEquals(export(extension = "hnt"), export(extension = "hwt"))
    }

    @Test fun customDesignerAndDescriptionRoundTripAsXmlText() {
        val output = ByteArrayOutputStream()
        val designer = "Artist & co. <custom>"
        val brief = "My icons: \"custom\" & original ☃"
        ThemeArchive.write(output, "Example", "Pack", listOf(ThemeIcon("com.example.app", png)), "hnt", designer = designer, briefInfo = brief)
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(ByteArrayInputStream(unzip(output.toByteArray()).getValue("description.xml")))
        assertEquals(designer, document.getElementsByTagName("designer").item(0).textContent)
        assertEquals(brief, document.getElementsByTagName("briefInfo").item(0).textContent)
        assertEquals(ThemeArchive.SCHEMA_VERSION, document.getElementsByTagName("version").item(0).textContent)
    }

    @Test fun underscorePackageMappingExportsImportsAndCanBeEdited() {
        val parsed = AppFilterParser.parse(ByteArrayInputStream("""
            <resources><item component="ComponentInfo{com._example.app/.MainActivity}" drawable="custom_icon"/></resources>
        """.trimIndent().toByteArray()))
        val packageName = parsed.mappings.single().packageName
        val source = folder.newFile().apply { writeBytes(export(icons = listOf(ThemeIcon(packageName, png)))) }
        val document = ThemeEditor.open(source)
        try {
            assertArrayEquals(png, ThemeEditor.readIcon(document, packageName))
            val edited = ByteArrayOutputStream()
            val replacement = png + byteArrayOf(42)
            ThemeEditor.write(document, edited, document.title, document.designer, document.briefInfo, mapOf(packageName to replacement))
            val inner = unzip(unzip(edited.toByteArray()).getValue("icons"))
            assertArrayEquals(replacement, inner.getValue("$packageName.png"))
        } finally {
            ThemeEditor.close(document)
        }
    }

    @Test fun previewsAreOptionalAndUseDocumentedEntryNames() {
        val jpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xd9.toByte())
        val largeJpeg = jpeg + byteArrayOf(0)
        val entries = unzip(export(preview = jpeg, iconsPreview = largeJpeg))
        assertArrayEquals(largeJpeg, entries.getValue("preview/preview_icons_0.jpg"))
        assertArrayEquals(jpeg, entries.getValue("preview/icon_small.jpg"))
        assertArrayEquals(largeJpeg, entries.getValue("preview/preview_unlock_0.jpg"))
        assertFalse(entries.keys.any { it.contains("wallpaper") || it.startsWith("com.") || it.startsWith("framework-") })
    }

    @Test fun ordinaryZipCompressionAndTimestampAreFixed() {
        ZipInputStream(ByteArrayInputStream(export())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                assertEquals(ZipEntry.DEFLATED, entry.method)
                assertEquals(0L, entry.time)
                zip.readBytes()
            }
        }
    }

    @Test fun rejectsTraversalDuplicateAndInvalidNamesBeforeWriting() {
        listOf("../escape", "com.example/escape", "com.example\\escape", "com.example\u0000", "single", "com..example").forEach { name ->
            rejects { export(icons = listOf(ThemeIcon(name, png))) }
        }
        rejects { export(icons = listOf(ThemeIcon("com.example.app", png), ThemeIcon("com.example.app", png))) }
    }

    @Test fun rejectsInvalidMetadataAndFormats() {
        rejects { export(title = " ") }
        rejects { export(title = "x".repeat(65)) }
        rejects { export(title = "bad\u0001title") }
        rejects { export(title = "bad\uD800title") }
        rejects { export(source = "") }
        rejects { export(source = "x".repeat(257)) }
        rejects { export(extension = "zip") }
    }

    @Test fun rejectsWrongImageFormatsAndSizeBounds() {
        rejects { export(icons = emptyList()) }
        rejects { export(icons = List(ThemeArchive.MAX_ICON_COUNT + 1) { ThemeIcon("com.example.app", png) }) }
        rejects { export(icons = listOf(ThemeIcon("com.example.app", byteArrayOf(1, 2, 3)))) }
        rejects { export(icons = listOf(ThemeIcon("com.example.app", ByteArray(ThemeArchive.MAX_ICON_BYTES + 1)))) }
        rejects { export(preview = png) }
        rejects { export(preview = ByteArray(ThemeArchive.MAX_PREVIEW_BYTES + 1)) }
        val fullSizedPng = ByteArray(ThemeArchive.MAX_ICON_BYTES).also { png.copyInto(it) }
        rejects { export(icons = List(257) { ThemeIcon("com.example.app$it", fullSizedPng) }) }
    }

    @Test fun rejectedInputLeavesOutputUntouched() {
        val output = ByteArrayOutputStream()
        rejects { ThemeArchive.write(output, "Good", "Pack", listOf(ThemeIcon("../escape", png)), "hnt") }
        assertEquals(0, output.size())
    }

    @Test fun fileBackedExportCleansTemporaryArchiveAfterSuccess() {
        val cache = folder.newFolder("export-cache")
        val entries = unzip(export(tempDirectory = cache))
        assertArrayEquals(png, unzip(entries.getValue("icons")).getValue("com.example.app.png"))
        assertTrue(cache.listFiles().orEmpty().isEmpty())
    }

    @Test fun completedInnerZipSizeIncludesHeadersBeyondRawIconBudget() {
        val entries = unzip(export())
        val actualInnerSize = entries.getValue("icons").size.toLong()
        assertTrue(actualInnerSize > png.size)
        ThemeArchive.validateCompletedArchive(actualInnerSize, png.size.toLong(), entries.getValue("description.xml").size, 0, 0)
        // The boundary uses lengths only, avoiding a 64 MiB allocation or fixture.
        ThemeArchive.validateCompletedArchive(ThemeEditor.MAX_MEMBER_BYTES, 0, 1, 0, 0)
        rejects { ThemeArchive.validateCompletedArchive(ThemeEditor.MAX_MEMBER_BYTES + 1, 0, 1, 0, 0) }
    }

    @Test fun completedArchiveEnforcesRawIconBudgetAndIncludesUnlockPreview() {
        val inner = ThemeEditor.MAX_MEMBER_BYTES
        ThemeArchive.validateCompletedArchive(inner, ThemeArchive.MAX_TOTAL_ICON_BYTES, 4096, 4096, 4096, 4096)
        rejects { ThemeArchive.validateCompletedArchive(inner, ThemeArchive.MAX_TOTAL_ICON_BYTES + 1, 4096, 4096, 4096, 4096) }
        rejects { ThemeArchive.validateCompletedArchive(inner, 1, 4096, 4096, 4096, -1) }
    }

    @Test fun fileBackedExportCleansTemporaryArchiveWhenDestinationFails() {
        val cache = folder.newFolder("failing-export-cache")
        val failingOutput = object : OutputStream() {
            override fun write(value: Int) { throw IOException("Destination unavailable") }
            override fun write(bytes: ByteArray, offset: Int, length: Int) { throw IOException("Destination unavailable") }
        }
        try {
            ThemeArchive.write(failingOutput, "Example", "Pack", listOf(ThemeIcon("com.example.app", png)), "hnt", tempDirectory = cache)
            fail("Expected destination failure")
        } catch (failure: IOException) {
            assertEquals("Destination unavailable", failure.message)
        }
        assertTrue(cache.listFiles().orEmpty().isEmpty())
    }

    @Test fun acceptsTwentyThousandMappedIconsWithinTheByteBudget() {
        val icons = List(ThemeArchive.MAX_ICON_COUNT) { ThemeIcon("com.example.app$it", png) }
        val inner = unzip(export(icons = icons)).getValue("icons")
        var count = 0
        ZipInputStream(ByteArrayInputStream(inner)).use { zip ->
            while (zip.nextEntry != null) {
                assertArrayEquals(png, zip.readBytes())
                count++
            }
        }
        assertEquals(20_000, count)
    }

    private fun export(
        title: String = "Example theme",
        source: String = "Example pack",
        icons: List<ThemeIcon> = listOf(ThemeIcon("com.example.app", png)),
        extension: String = "hnt",
        preview: ByteArray? = null,
        iconsPreview: ByteArray? = null,
        tempDirectory: File? = null,
    ): ByteArray = ByteArrayOutputStream().also {
        ThemeArchive.write(it, title, source, icons, extension, preview, iconsPreview, tempDirectory)
    }.toByteArray()

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> = linkedMapOf<String, ByteArray>().also { result ->
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                assertTrue("Duplicate output ZIP entry", !result.containsKey(entry.name))
                result[entry.name] = zip.readBytes()
            }
        }
    }

    private fun rejects(block: () -> Unit) {
        try {
            block()
            fail("Expected input validation to reject this value")
        } catch (_: IllegalArgumentException) {
            // Expected: malformed inputs are rejected before archive construction.
        }
    }
}
