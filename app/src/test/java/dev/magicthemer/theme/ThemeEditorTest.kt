package dev.magicthemer.theme

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ThemeEditorTest {
    @get:Rule val folder = TemporaryFolder()

    private val png = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aL1sAAAAASUVORK5CYII="
    )
    private val replacementPng = png + byteArrayOf(42)
    private val metadata = """<?xml version="1.0" encoding="UTF-8"?>
        <HnTheme vendor="custom"><title>Original</title><designer>Artist</designer>
        <screen>FHD</screen><version>8.0.37</version><briefInfo>Existing description</briefInfo>
        <!-- keep this --><custom><nested flag="yes">untouched</nested></custom></HnTheme>""".trimIndent().toByteArray()
    private val assets = linkedMapOf(
        "com.example.first.png" to png,
        "res/drawable/com.example.second.png" to png,
        "icon_mask.png" to byteArrayOf(4, 3, 2, 1),
        "theme.xml" to "<icons keep='yes'/>".toByteArray(),
        "dynamic_icons/com.example.clock/config.xml" to "<clock/>".toByteArray(),
        "dynamic_icons/com.example.clock/com.example.clock.png" to png,
    )
    private val unrelated = linkedMapOf(
        "wallpaper/home_wallpaper_0.jpg" to byteArrayOf(3, 7, 9),
        "com.android.systemui" to archive(linkedMapOf("colors.xml" to "<colors/>".toByteArray())),
        "fonts/custom.ttf" to byteArrayOf(10, 20, 30),
        "preview/cover.jpg" to byteArrayOf(1, 5, 8),
    )

    @Test fun opensAndReadsFlatAndNestedStaticIconsWithoutChangingSource() {
        val source = theme()
        val original = source.readBytes()
        val document = ThemeEditor.open(source)
        assertEquals("Original", document.title)
        assertEquals("Artist", document.designer)
        assertEquals("Existing description", document.briefInfo)
        assertEquals(mapOf("com.example.first" to "com.example.first.png", "com.example.second" to "res/drawable/com.example.second.png"), document.iconEntries)
        assertArrayEquals(png, ThemeEditor.readIcon(document, "com.example.first"))
        assertArrayEquals(png, ThemeEditor.readIcon(document, "com.example.second"))
        assertNull(ThemeEditor.readIcon(document, "com.example.missing"))
        assertArrayEquals(original, source.readBytes())
        val cache = document.iconsFile
        assertTrue(cache.isFile)
        ThemeEditor.close(document)
        assertFalse(cache.exists())
        assertArrayEquals(original, source.readBytes())
    }

    @Test fun editsMetadataAndMergesIconsWhilePreservingOtherContents() {
        val source = theme()
        val original = source.readBytes()
        val document = ThemeEditor.open(source)
        val output = edit(document, title = "New & <theme>", designer = "New artist", briefInfo = "Edited description",
            replacements = mapOf("com.example.second" to replacementPng, "org.example.newapp" to png))
        val entries = unzip(output)
        unrelated.forEach { (name, bytes) -> assertArrayEquals("Preserve $name", bytes, entries[name]) }
        val icons = unzip(entries.getValue("icons"))
        assertArrayEquals(png, icons["com.example.first.png"])
        assertArrayEquals(replacementPng, icons["res/drawable/com.example.second.png"])
        assertArrayEquals(png, icons["org.example.newapp.png"])
        assets.filterKeys { !it.endsWith("com.example.first.png") && !it.endsWith("com.example.second.png") }
            .forEach { (name, bytes) -> assertArrayEquals(bytes, icons[name]) }
        val xml = xml(entries.getValue("description.xml"))
        assertEquals("HnTheme", xml.documentElement.tagName)
        assertEquals("custom", xml.documentElement.getAttribute("vendor"))
        assertEquals("New & <theme>", xml.getElementsByTagName("title").item(0).textContent)
        assertEquals("New artist", xml.getElementsByTagName("designer").item(0).textContent)
        assertEquals("Edited description", xml.getElementsByTagName("briefInfo").item(0).textContent)
        assertEquals("8.0.37", xml.getElementsByTagName("version").item(0).textContent)
        assertEquals("untouched", xml.getElementsByTagName("nested").item(0).textContent)
        assertTrue(entries.getValue("description.xml").decodeToString().contains("keep this"))
        assertArrayEquals(original, source.readBytes())
        ThemeEditor.close(document)
    }

    @Test fun removesOnlyRequestedPackageAndReplaceAllKeepsConfiguration() {
        val document = ThemeEditor.open(theme())
        val removed = unzip(unzip(edit(document, removed = setOf("com.example.first"))).getValue("icons"))
        assertFalse(removed.containsKey("com.example.first.png"))
        assertArrayEquals(png, removed["res/drawable/com.example.second.png"])
        val replaced = unzip(unzip(edit(document, replacements = mapOf("org.example.newapp" to png), replaceAll = true)).getValue("icons"))
        assertEquals(setOf("org.example.newapp.png", "icon_mask.png", "theme.xml", "dynamic_icons/com.example.clock/config.xml", "dynamic_icons/com.example.clock/com.example.clock.png"), replaced.keys)
        assertArrayEquals(assets.getValue("theme.xml"), replaced["theme.xml"])
        assertArrayEquals(png, replaced["dynamic_icons/com.example.clock/com.example.clock.png"])
        ThemeEditor.close(document)
    }

    @Test fun replacesAllAliasesAndWarnsAboutAmbiguousPackagePaths() {
        val aliases = LinkedHashMap(assets).apply { put("res/other/com.example.first.png", png) }
        val document = ThemeEditor.open(theme(iconEntries = aliases))
        assertEquals("com.example.first.png", document.iconEntries["com.example.first"])
        assertTrue(document.warnings.any { it.contains("several icon paths") })
        val icons = unzip(unzip(edit(document, replacements = mapOf("com.example.first" to replacementPng))).getValue("icons"))
        assertArrayEquals(replacementPng, icons["com.example.first.png"])
        assertArrayEquals(replacementPng, icons["res/other/com.example.first.png"])
        ThemeEditor.close(document)
    }

    @Test fun preservesLegacyRootBriefInfoCaseAndUnchangedMetadataAttributes() {
        val legacy = """<HwTheme flag="legacy"><title role="label">Original</title><designer>Artist</designer><version>6.0.9</version><briefinfo>Existing description</briefinfo><other enabled="yes"/></HwTheme>""".toByteArray()
        val document = ThemeEditor.open(theme(description = legacy))
        assertFalse(document.warnings.any { it.contains("legacy Huawei") })
        val metadata = xml(unzip(edit(document, briefInfo = "Updated legacy description")).getValue("description.xml"))
        assertEquals("HwTheme", metadata.documentElement.tagName)
        assertEquals("label", (metadata.getElementsByTagName("title").item(0) as org.w3c.dom.Element).getAttribute("role"))
        assertEquals("Updated legacy description", metadata.getElementsByTagName("briefinfo").item(0).textContent)
        assertEquals(0, metadata.getElementsByTagName("briefInfo").length)
        assertNotNull(metadata.getElementsByTagName("other").item(0))
        ThemeEditor.close(document)
    }

    @Test fun optionalPreviewsReplaceTheirOwnEntriesAndAddMissingEntries() {
        val document = ThemeEditor.open(theme())
        val small = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 1)
        val large = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 2)
        val entries = unzip(edit(document, preview = small, iconsPreview = large))
        assertArrayEquals(small, entries["preview/icon_small.jpg"])
        assertArrayEquals(large, entries["preview/preview_icons_0.jpg"])
        assertArrayEquals(unrelated.getValue("preview/cover.jpg"), entries["preview/cover.jpg"])
        ThemeEditor.close(document)
    }

    @Test fun rejectsUnsafeOrDuplicateArchivePathsAndDtdBeforeCaching() {
        listOf("../escape", "/absolute", "a/../escape", "a\\escape", "C:/escape", "a//escape").forEach { unsafe ->
            rejects { ThemeEditor.open(theme(extras = mapOf(unsafe to byteArrayOf(1)))) }
        }
        val duplicate = archive(linkedMapOf("description.xml" to metadata, "icons" to archive(assets), "x.txt" to byteArrayOf(1), "y.txt" to byteArrayOf(2)))
        for (index in 0..duplicate.size - 5) {
            if (duplicate.copyOfRange(index, index + 5).contentEquals("y.txt".toByteArray())) "x.txt".toByteArray().copyInto(duplicate, index)
        }
        rejects { ThemeEditor.open(writeFile(duplicate)) }
        rejects { ThemeEditor.open(theme(description = "<!DOCTYPE HnTheme [<!ENTITY e 'blocked'>]><HnTheme><title>&e;</title></HnTheme>".toByteArray())) }
        rejects { ThemeEditor.open(theme(iconEntries = mapOf("../com.example.app.png" to png))) }
        assertTrue(folder.root.listFiles().orEmpty().none { it.name.startsWith("theme-icons-") })
    }

    @Test fun rejectsNonZipMissingModulesInvalidIconsAndMetadataLimits() {
        rejects { ThemeEditor.open(writeFile("not a ZIP".toByteArray())) }
        rejects { ThemeEditor.open(writeFile(archive(mapOf("description.xml" to metadata)))) }
        rejects { ThemeEditor.open(writeFile(archive(mapOf("icons" to archive(assets))))) }
        rejects { ThemeEditor.open(theme(iconEntries = mapOf("com.example.app.png" to byteArrayOf(1, 2, 3)))) }
        rejects { ThemeEditor.open(theme(iconEntries = mapOf("com.example.app.png" to ByteArray(ThemeEditor.MAX_ICON_BYTES + 1)))) }
        rejects { ThemeEditor.open(theme(description = ByteArray(ThemeEditor.MAX_METADATA_BYTES + 1))) }
        rejects { ThemeEditor.open(theme(description = "<OtherTheme/>".toByteArray())) }
        rejects { ThemeEditor.open(theme(description = byteArrayOf(0xff.toByte(), 0xfe.toByte(), 0))) }
        rejects { ThemeEditor.open(theme(description = ("<HnTheme>" + "<nested>".repeat(65) + "</nested>".repeat(65) + "</HnTheme>").toByteArray())) }
    }

    @Test fun validatesReplacementsAndDetectsChangedSourceBeforeOutput() {
        val source = theme()
        val document = ThemeEditor.open(source)
        val destination = ByteArrayOutputStream()
        rejects { ThemeEditor.write(document, destination, "Title", "Artist", "Description", mapOf("../escape" to png)) }
        rejects { ThemeEditor.write(document, destination, "Title", "Artist", "Description", mapOf("com.example.app" to byteArrayOf(1))) }
        rejects { ThemeEditor.write(document, destination, "Title", "Artist", "Description", mapOf("com.example.app" to png), setOf("com.example.app")) }
        rejects { ThemeEditor.write(document, destination, "bad\u0000title", "Artist", "Description", emptyMap()) }
        source.appendBytes(byteArrayOf(1))
        rejects { ThemeEditor.write(document, destination, "Title", "Artist", "Description", emptyMap()) }
        assertEquals(0, destination.size())
        ThemeEditor.close(document)
        assertTrue(folder.root.listFiles().orEmpty().none { it.name.startsWith("theme-edited-") })
    }

    @Test fun memberAndEntryCountBoundsRejectHighlyCompressedArchives() {
        val hugeMember = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("huge.bin"))
                val chunk = ByteArray(32 * 1024)
                repeat((ThemeEditor.MAX_MEMBER_BYTES / chunk.size).toInt()) { zip.write(chunk) }
                zip.write(0)
                zip.closeEntry()
                mapOf("description.xml" to metadata, "icons" to archive(assets)).forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
                }
            }
        }.toByteArray()
        rejects { ThemeEditor.open(writeFile(hugeMember)) }
        val manyEntries = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                repeat(ThemeEditor.MAX_ENTRIES + 1) { zip.putNextEntry(ZipEntry("item$it")); zip.closeEntry() }
            }
        }.toByteArray()
        rejects { ThemeEditor.open(writeFile(manyEntries)) }
    }

    @Test fun preservesObservedRootLauncherAliasesWithoutTreatingThemAsPackages() {
        val aliases = LinkedHashMap(assets).apply {
            put(":CAMERA.png", png)
            put(":LAUNCHER_ACTION_APP_DRAWER.png", png)
        }
        val document = ThemeEditor.open(theme(iconEntries = aliases))
        assertFalse(document.iconEntries.keys.any { it.startsWith(":") })
        val saved = unzip(unzip(edit(document, replacements = mapOf("com.example.first" to replacementPng))).getValue("icons"))
        assertArrayEquals(png, saved[":CAMERA.png"])
        assertArrayEquals(png, saved[":LAUNCHER_ACTION_APP_DRAWER.png"])
        ThemeEditor.close(document)
        listOf("folder/:CAMERA.png", ":camera.png", "C:/drive.png", ":CAMERA/other.png").forEach { unsafe ->
            rejects { ThemeEditor.open(theme(iconEntries = mapOf(unsafe to png))) }
        }
    }

    @Test fun workingHwThemeProfileKeepsAuthorAndVersionAndUpdatesOnlyMatchingLocalizedTitle() {
        val working = """<HwTheme><title>Original</title><title-cn>Original</title-cn><author>Original author</author><designer>Artist</designer><version>12</version><font>Default</font><font-cn>Default</font-cn></HwTheme>""".toByteArray()
        val document = ThemeEditor.open(theme(description = working))
        assertFalse(document.compatibilityRepairAvailable)
        val saved = xml(unzip(edit(document, title = "Edited", designer = "New designer")).getValue("description.xml"))
        assertEquals("HwTheme", saved.documentElement.tagName)
        assertEquals("12", saved.getElementsByTagName("version").item(0).textContent)
        assertEquals("Original author", saved.getElementsByTagName("author").item(0).textContent)
        assertEquals("Edited", saved.getElementsByTagName("title-cn").item(0).textContent)
        ThemeEditor.close(document)
        val localized = ThemeEditor.open(theme(description = working.decodeToString().replace("<title-cn>Original</title-cn>", "<title-cn>本地名称</title-cn>").toByteArray()))
        val unchanged = xml(unzip(edit(localized, title = "Edited")).getValue("description.xml"))
        assertEquals("本地名称", unchanged.getElementsByTagName("title-cn").item(0).textContent)
        ThemeEditor.close(localized)
    }

    @Test fun compatibilityUpgradeRecognizesOnlyMinimalOlderMagicThemerExports() {
        val old = """<HnTheme><title>Old export</title><designer>Magic Themer</designer><screen>FHD</screen><version>8.0.0</version><briefInfo/></HnTheme>""".toByteArray()
        val jpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xd9.toByte())
        val original = theme(description = old, iconEntries = mapOf("com.example.first.png" to png), extras = mapOf("preview/preview_icons_0.jpg" to jpeg))
        val originalBytes = original.readBytes()
        val document = ThemeEditor.open(original)
        assertTrue(document.compatibilityRepairAvailable)
        val ordinary = xml(unzip(edit(document)).getValue("description.xml"))
        assertEquals("HnTheme", ordinary.documentElement.tagName)
        val output = ByteArrayOutputStream().also { ThemeEditor.write(document, it, "Repaired", document.designer, document.briefInfo, emptyMap(), upgradeMinimalProfile = true) }.toByteArray()
        val saved = unzip(output)
        val xml = xml(saved.getValue("description.xml"))
        assertEquals("HwTheme", xml.documentElement.tagName)
        assertEquals("12", xml.getElementsByTagName("version").item(0).textContent)
        assertEquals("Repaired", xml.getElementsByTagName("title-cn").item(0).textContent)
        assertEquals("Magic Themer", xml.getElementsByTagName("author").item(0).textContent)
        assertEquals("Default", xml.getElementsByTagName("font").item(0).textContent)
        assertEquals(0, xml.getElementsByTagName("screen").length)
        assertArrayEquals(jpeg, saved["preview/preview_unlock_0.jpg"])
        assertEquals("<HWTheme />", saved.getValue("unlock/theme.xml").decodeToString())
        assertArrayEquals(png, unzip(saved.getValue("icons"))["com.example.first.png"])
        assertArrayEquals(originalBytes, original.readBytes())
        ThemeEditor.close(document)

        listOf(old.decodeToString().replace("Magic Themer", "Other author").toByteArray(),
            old.decodeToString().replace("<HnTheme>", "<HnTheme vendor='custom'>").toByteArray(),
            old.decodeToString().replace("8.0.0", "10.0.0").toByteArray()).forEach { metadata ->
            val other = ThemeEditor.open(theme(description = metadata, iconEntries = mapOf("com.example.first.png" to png), extras = emptyMap()))
            assertFalse(other.compatibilityRepairAvailable)
            rejects { ThemeEditor.write(other, ByteArrayOutputStream(), other.title, other.designer, other.briefInfo, emptyMap(), upgradeMinimalProfile = true) }
            ThemeEditor.close(other)
        }
        val fullTheme = ThemeEditor.open(theme(description = old, iconEntries = mapOf("com.example.first.png" to png)))
        assertFalse(fullTheme.compatibilityRepairAvailable)
        ThemeEditor.close(fullTheme)
    }

    @Test fun fileBackedFullThemeOverOldExpandedBudgetPreservesLargeModules() {
        val inner = folder.newFile("large-icons.zip")
        val chunk = ByteArray(32 * 1024)
        ZipOutputStream(inner.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("configuration.bin"))
            repeat(2048) { zip.write(chunk) }
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("com.example.first.png")); zip.write(png); zip.closeEntry()
        }
        val source = folder.newFile("large-theme.hnt")
        ZipOutputStream(source.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("description.xml")); zip.write(metadata); zip.closeEntry()
            zip.putNextEntry(ZipEntry("icons")); inner.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            zip.putNextEntry(ZipEntry("wallpaper/home_wallpaper_0.jpg"))
            repeat(2080) { zip.write(chunk) }
            zip.closeEntry()
        }
        val document = ThemeEditor.open(source)
        val saved = folder.newFile("large-theme-edited.hnt")
        ThemeEditor.write(document, saved.outputStream(), "Edited", document.designer, document.briefInfo, mapOf("com.example.first" to replacementPng))
        ZipFile(source).use { before -> ZipFile(saved).use { after ->
            assertEquals(65L * 1024 * 1024, after.getEntry("wallpaper/home_wallpaper_0.jpg").size)
            assertArrayEquals(memberDigest(before, "wallpaper/home_wallpaper_0.jpg"), memberDigest(after, "wallpaper/home_wallpaper_0.jpg"))
        } }
        val reopened = ThemeEditor.open(saved)
        assertArrayEquals(replacementPng, ThemeEditor.readIcon(reopened, "com.example.first"))
        ThemeEditor.close(reopened)
        ThemeEditor.close(document)
    }

    /** Optional local validation; no external theme artwork is a test dependency. */
    @Test fun optionalUserSuppliedWorkingThemeOpensAndRoundTripsUnchangedModules() {
        val path = System.getProperty("magicthemer.compatibilityTheme") ?: System.getenv("MAGIC_THEMER_COMPATIBILITY_THEME")
        assumeTrue("Set MAGIC_THEMER_COMPATIBILITY_THEME to validate a supplied local theme.", !path.isNullOrBlank())
        val source = folder.newFile("user-working-theme.hnt")
        File(requireNotNull(path)).inputStream().use { input -> source.outputStream().use { input.copyTo(it) } }
        val document = ThemeEditor.open(source)
        assertTrue(document.iconEntries.isNotEmpty())
        assertFalse(document.compatibilityRepairAvailable)
        val saved = folder.newFile("user-working-theme-edited.hnt")
        ThemeEditor.write(document, saved.outputStream(), "Compatibility round trip", document.designer, document.briefInfo, emptyMap())
        ZipFile(source).use { before -> ZipFile(saved).use { after ->
            before.entries().asSequence().filter { it.name != "description.xml" && it.name != "icons" }.forEach { entry ->
                assertArrayEquals("Preserve ${entry.name}", memberDigest(before, entry.name), memberDigest(after, entry.name))
            }
            val oldXml = xml(before.getInputStream(before.getEntry("description.xml")).use { it.readBytes() })
            val newXml = xml(after.getInputStream(after.getEntry("description.xml")).use { it.readBytes() })
            assertEquals(oldXml.documentElement.tagName, newXml.documentElement.tagName)
            assertEquals(oldXml.getElementsByTagName("version").item(0).textContent, newXml.getElementsByTagName("version").item(0).textContent)
        } }
        val reopened = ThemeEditor.open(saved)
        assertEquals(document.iconEntries.keys, reopened.iconEntries.keys)
        ThemeEditor.close(reopened)
        ThemeEditor.close(document)
    }

    private fun memberDigest(zip: ZipFile, name: String): ByteArray = MessageDigest.getInstance("SHA-256").also { hash ->
        zip.getInputStream(zip.getEntry(name)).use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) { val count = input.read(buffer); if (count < 0) break; hash.update(buffer, 0, count) }
        }
    }.digest()

    private fun theme(description: ByteArray = metadata, iconEntries: Map<String, ByteArray> = assets, extras: Map<String, ByteArray> = unrelated): File =
        writeFile(archive(linkedMapOf("description.xml" to description, "icons" to archive(iconEntries)).apply { putAll(extras) }))

    private fun writeFile(bytes: ByteArray): File = folder.newFile().also { it.writeBytes(bytes) }

    private fun edit(document: ThemeDocument, title: String = document.title, designer: String = document.designer,
        briefInfo: String = document.briefInfo, replacements: Map<String, ByteArray> = emptyMap(),
        removed: Set<String> = emptySet(), replaceAll: Boolean = false, preview: ByteArray? = null, iconsPreview: ByteArray? = null): ByteArray =
        ByteArrayOutputStream().also { ThemeEditor.write(document, it, title, designer, briefInfo, replacements, removed, replaceAll, preview, iconsPreview) }.toByteArray()

    private fun archive(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip -> entries.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() } }
    }.toByteArray()

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> = linkedMapOf<String, ByteArray>().also { entries ->
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip -> while (true) { val entry = zip.nextEntry ?: break; entries[entry.name] = zip.readBytes() } }
    }

    private fun xml(bytes: ByteArray) = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(bytes))

    private fun rejects(block: () -> Unit) {
        try { block(); fail("Expected this input to be rejected") } catch (_: IllegalArgumentException) { }
    }
}
