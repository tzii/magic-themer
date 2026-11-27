package dev.magicthemer.theme

import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ThemeExportHistoryTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun suggestsIncrementedCopyTitlesWithinMaxLength() {
        assertEquals("Arcticons 2", ThemeExportHistory.suggestCopyTitle("Arcticons"))
        assertEquals("Arcticons 3", ThemeExportHistory.suggestCopyTitle("Arcticons 2"))
        assertEquals("Arcticons 10", ThemeExportHistory.suggestCopyTitle("Arcticons 9"))
        assertEquals("Enix v3", ThemeExportHistory.suggestCopyTitle("Enix v2"))
        assertEquals("Enix (3)", ThemeExportHistory.suggestCopyTitle("Enix (2)"))
        assertEquals("Icons8 2", ThemeExportHistory.suggestCopyTitle("Icons8"))
        val longTitle = "A".repeat(64)
        val bumped = ThemeExportHistory.suggestCopyTitle(longTitle)
        assertEquals(64, bumped.length)
        assertTrue(bumped.endsWith(" 2"))
    }

    @Test fun persistsRecordsAndMatchesByArchiveOrIconsDigest() {
        val file = folder.newFile("history.tsv")
        val history = ThemeExportHistory(file, maxRecords = 2)
        val first = ThemeExportRecord("aaa", "111", "Pack\tOne, v1", "com.pack.one", setOf("com.app.a", "com.app.b"), 10L)
        val second = ThemeExportRecord("bbb", "222", "Pack Two", "com.pack.two", setOf("com.app.c"), 20L)
        val third = ThemeExportRecord("ccc", "333", "Pack Three", null, setOf("com.app.d"), 30L)
        history.record(first)
        history.record(second)
        val foundFirst = history.find("aaa", "zzz")
        assertEquals("Pack\tOne, v1", foundFirst?.title)
        assertEquals(setOf("com.app.a", "com.app.b"), foundFirst?.customizedPackages)
        // Falls back to matching inner icons SHA-256 when outer archive was renamed/repacked.
        assertEquals(setOf("com.app.c"), history.find("unknown", "222")?.customizedPackages)
        // Bounded to maxRecords (oldest evicted).
        history.record(third)
        assertNull(history.find("aaa", "111"))
        assertEquals(setOf("com.app.d"), history.find("ccc", "333")?.customizedPackages)
    }

    @Test fun recoversGracefullyFromCorruptHistoryFileAndHashesZipExport() {
        val corrupt = folder.newFile("corrupt.json").apply { writeText("{not json") }
        val history = ThemeExportHistory(corrupt)
        assertNull(history.find("aaa", "111"))
        history.record(ThemeExportRecord("aaa", "111", "Ok", null, setOf("com.a")))
        assertEquals(setOf("com.a"), history.find("aaa", "111")?.customizedPackages)

        val zip = folder.newFile("sample.hnt")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("icons"))
            out.write("inner-zip-bytes".toByteArray())
            out.closeEntry()
        }
        val (archiveSha, iconsSha) = ThemeExportHistory.hashExport(zip)
        assertEquals(64, archiveSha.length)
        assertEquals(64, iconsSha.length)
    }
}
