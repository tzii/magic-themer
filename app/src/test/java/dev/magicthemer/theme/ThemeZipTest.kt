package dev.magicthemer.theme

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ThemeZipTest {
    @get:Rule val folder = TemporaryFolder()
    private data class Raw(val name: String, val central: ByteArray, val localRecord: ByteArray, val offset: Int)
    private val content = linkedMapOf("description.xml" to "Original description".toByteArray(), "icons" to "Original opaque module".toByteArray())

    @Test fun noOpRewriteIsByteIdenticalIncludingDescriptorsExtrasAttributesAndComments() {
        val bytes = descriptorArchive()
        val source = file(bytes)
        assertArrayEquals(bytes, rewritten(source))
        assertTrue(raw(bytes).all { u16(it.central, 8) and 8 != 0 })
        assertTrue(raw(bytes).all { u32(it.central, 38) == 0x81801234L })
    }

    @Test fun replacingOneMemberPreservesEveryOtherRawRecordAndCentralMetadata() {
        val sourceBytes = descriptorArchive()
        val replacement = "Updated & UTF-8 café".toByteArray()
        val result = rewritten(file(sourceBytes), mapOf("description.xml" to ThemeZip.Bytes(replacement)))
        val before = raw(sourceBytes).associateBy { it.name }
        val after = raw(result).associateBy { it.name }
        assertArrayEquals(before.getValue("icons").localRecord, after.getValue("icons").localRecord)
        assertArrayEquals(withoutOffset(before.getValue("icons").central), withoutOffset(after.getValue("icons").central))
        val changed = after.getValue("description.xml")
        assertEquals(0, u16(changed.localRecord, 6) and 8)
        assertEquals(u32(changed.central, 16), u32(changed.localRecord, 14))
        assertEquals(u32(changed.central, 20), u32(changed.localRecord, 18))
        assertEquals(replacement.size.toLong(), u32(changed.localRecord, 22))
        assertEquals(CRC32().apply { update(replacement) }.value, u32(changed.central, 16))
        assertEquals(u32(before.getValue("description.xml").central, 38), u32(changed.central, 38))
        assertArrayEquals(comment(sourceBytes), comment(result))
        assertContents(result, content + ("description.xml" to replacement))
    }

    @Test fun explicitDescriptorRepairPreservesCompressedPayloadsAndUntouchedNormalRecords() {
        val newContent = "Already framed without a descriptor".toByteArray()
        val mixed = rewritten(file(descriptorArchive()), mapOf("normal.txt" to ThemeZip.Bytes(newContent)))
        val normalized = ByteArrayOutputStream().also {
            ThemeZip.rewrite(file(mixed), it, emptyMap(), normalizeDescriptors = true)
        }.toByteArray()
        val before = raw(mixed).associateBy { it.name }
        val after = raw(normalized).associateBy { it.name }
        for ((name, record) in before) {
            val fixed = after.getValue(name)
            assertEquals(0, u16(fixed.localRecord, 6) and 8)
            assertEquals(u32(fixed.central, 16), u32(fixed.localRecord, 14))
            assertEquals(u32(fixed.central, 20), u32(fixed.localRecord, 18))
            assertEquals(u32(fixed.central, 24), u32(fixed.localRecord, 22))
            fun compressed(value: Raw): ByteArray {
                val start = 30 + u16(value.localRecord, 26) + u16(value.localRecord, 28)
                return value.localRecord.copyOfRange(start, start + u32(value.central, 20).toInt())
            }
            assertArrayEquals(compressed(record), compressed(fixed))
            if (u16(record.central, 8) and 8 == 0) {
                assertArrayEquals(record.localRecord, fixed.localRecord)
                assertArrayEquals(withoutOffset(record.central), withoutOffset(fixed.central))
            }
        }
        assertArrayEquals(comment(mixed), comment(normalized))
        assertContents(normalized, content + ("normal.txt" to newContent))
        assertArrayEquals(normalized, ByteArrayOutputStream().also {
            ThemeZip.rewrite(file(normalized), it, emptyMap(), normalizeDescriptors = true)
        }.toByteArray())
    }

    @Test fun appendAndRemoveKeepOrderAndSupportFileBackedPayloads() {
        val bytes = descriptorArchive()
        val payload = folder.newFile("replacement.bin").apply { writeText("File-backed content") }
        val result = rewritten(file(bytes), linkedMapOf("new/item.txt" to ThemeZip.FileContent(payload)), setOf("description.xml"))
        assertEquals(listOf("icons", "new/item.txt"), raw(result).map { it.name })
        val appended = raw(result).last()
        assertEquals(0x806, u16(appended.localRecord, 6))
        assertEquals(0x0333, u16(appended.central, 4))
        assertEquals(0x81800000L, u32(appended.central, 38))
        assertEquals(33, u16(appended.localRecord, 12))
        assertArrayEquals(comment(bytes), comment(result))
        assertContents(result, mapOf("icons" to content.getValue("icons"), "new/item.txt" to payload.readBytes()))
    }

    @Test fun freshArchiveUsesKnownLocalSizesAndStoredEmptyUnixDirectories() {
        val output = ByteArrayOutputStream()
        val entries = linkedMapOf("preview/" to ThemeZip.Bytes(byteArrayOf()), "preview/é.txt" to ThemeZip.Bytes("Artwork preview".toByteArray()))
        ThemeZip.create(output, entries, folder.root)
        val records = raw(output.toByteArray())
        assertEquals(0, u16(records.first().localRecord, 8))
        assertEquals(0x800, u16(records.first().localRecord, 6))
        assertEquals(0x41c00000L, u32(records.first().central, 38))
        assertTrue(records.all { u16(it.localRecord, 6) and 8 == 0 })
        assertTrue(records.all { u32(it.localRecord, 18) == u32(it.central, 20) })
        assertContents(output.toByteArray(), mapOf("preview/" to byteArrayOf(), "preview/é.txt" to "Artwork preview".toByteArray()))
        assertTrue(folder.root.listFiles().orEmpty().none { it.name.startsWith("theme-zip-payload-") })
    }

    @Test fun rejectsEncryptedZip64DuplicateAndMismatchedLocalHeaders() {
        val original = descriptorArchive()
        rejects { rewritten(file(original.copyOf().apply { val at = centralOffset(this); put16(this, at + 8, u16(this, at + 8) or 1); put16(this, 6, u16(this, 6) or 1) })) }
        rejects { rewritten(file(original.copyOf().apply { put16(this, endOffset(this) + 8, 0xffff); put16(this, endOffset(this) + 10, 0xffff) })) }
        rejects { rewritten(file(original.copyOf().apply { put32(this, centralOffset(this) + 20, 0xffffffffL) })) }
        rejects { rewritten(file(original.copyOf().apply { put32(this, centralOffset(this) + 42, size.toLong()) })) }
        rejects { rewritten(file(original.copyOf().apply { this[30] = 'x'.code.toByte() })) }
        rejects { rewritten(file(original.copyOf().apply { put16(this, 6, 0x800) })) }
        val duplicated = simpleArchive(linkedMapOf("a.txt" to byteArrayOf(1), "b.txt" to byteArrayOf(2)))
        val second = raw(duplicated).last()
        val secondCentral = centralOffset(duplicated) + raw(duplicated).first().central.size
        "a.txt".toByteArray().copyInto(duplicated, second.offset + 30)
        "a.txt".toByteArray().copyInto(duplicated, secondCentral + 46)
        rejects { rewritten(file(duplicated)) }
    }

    @Test fun rejectsMalformedDescriptorsExtrasPrefixesSignaturesAndTrailingData() {
        val original = descriptorArchive()
        val first = raw(original).first()
        val descriptor = first.offset + first.localRecord.size - 16
        rejects { rewritten(file(original.copyOf().apply { this[descriptor + 4] = (this[descriptor + 4].toInt() xor 1).toByte() })) }
        rejects { rewritten(file(original.copyOf().apply { put16(this, 30 + u16(this, 26), 1) })) }
        rejects { rewritten(file(original.copyOf().apply { put16(this, 30 + u16(this, 26), 0x14) })) }
        rejects { rewritten(file(original.copyOf().apply { put16(this, 28, 0xffff) })) }
        rejects { rewritten(file(original + byteArrayOf(0))) }
        rejects { rewritten(file(byteArrayOf(0) + original)) }
        val end = endOffset(original)
        val signed = original.copyOfRange(0, end) + byteArrayOf(0x50, 0x4b, 0x05, 0x05, 0, 0) + original.copyOfRange(end, original.size)
        put32(signed, end + 6 + 12, u32(original, end + 12) + 6)
        rejects { rewritten(file(signed)) }
        rejects { rewritten(file(original.copyOf().apply { put16(this, endOffset(this) + 8, 20_001); put16(this, endOffset(this) + 10, 20_001) })) }
    }

    @Test fun replacementFailureCleansCompressedFilesAndRejectsNonemptyDirectories() {
        val failing = object : OutputStream() { override fun write(value: Int) { throw IOException("Unavailable") } }
        try { ThemeZip.create(failing, mapOf("x.txt" to ThemeZip.Bytes("payload".toByteArray())), folder.root); fail("Expected write failure") }
        catch (error: IOException) { assertEquals("Unavailable", error.message) }
        rejects { ThemeZip.create(ByteArrayOutputStream(), mapOf("folder/" to ThemeZip.Bytes(byteArrayOf(1))), folder.root) }
        assertTrue(folder.root.listFiles().orEmpty().none { it.name.startsWith("theme-zip-payload-") })
    }

    private fun descriptorArchive(): ByteArray {
        val bytes = ByteArrayOutputStream().also { output -> ZipOutputStream(output).use { zip ->
            zip.setComment("Original archive comment ♠")
            content.forEach { (name, data) ->
                zip.putNextEntry(ZipEntry(name).apply {
                    time = 1_650_000_000_000L
                    comment = "Member comment α"
                    extra = byteArrayOf(0xfe.toByte(), 0xaf.toByte(), 3, 0, 1, 2, 3)
                }); zip.write(data); zip.closeEntry()
            }
        } }.toByteArray()
        var at = centralOffset(bytes)
        repeat(u16(bytes, endOffset(bytes) + 10)) {
            put16(bytes, at + 4, 0x0333); put32(bytes, at + 38, 0x81801234L)
            at += 46 + u16(bytes, at + 28) + u16(bytes, at + 30) + u16(bytes, at + 32)
        }
        return bytes
    }
    private fun simpleArchive(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also {
        ThemeZip.create(it, entries.mapValues { (_, bytes) -> ThemeZip.Bytes(bytes) }, folder.root)
    }.toByteArray()
    private fun rewritten(source: File, replacements: Map<String, ThemeZip.Content> = emptyMap(), removed: Set<String> = emptySet()): ByteArray =
        ByteArrayOutputStream().also { ThemeZip.rewrite(source, it, replacements, removed) }.toByteArray()
    private fun file(bytes: ByteArray): File = folder.newFile().apply { writeBytes(bytes) }
    private fun assertContents(bytes: ByteArray, expected: Map<String, ByteArray>) {
        ZipFile(file(bytes)).use { zip ->
            assertEquals(expected.keys, zip.entries().asSequence().map { it.name }.toSet())
            expected.forEach { (name, data) -> assertArrayEquals(data, zip.getInputStream(zip.getEntry(name)).use { it.readBytes() }) }
        }
    }
    private fun raw(bytes: ByteArray): List<Raw> {
        val offset = centralOffset(bytes)
        var at = offset
        val records = ArrayList<Pair<ByteArray, Int>>()
        repeat(u16(bytes, endOffset(bytes) + 10)) {
            val size = 46 + u16(bytes, at + 28) + u16(bytes, at + 30) + u16(bytes, at + 32)
            records += bytes.copyOfRange(at, at + size) to u32(bytes, at + 42).toInt(); at += size
        }
        return records.map { (central, start) ->
            val next = records.map { it.second }.filter { it > start }.minOrNull() ?: offset
            Raw(String(central.copyOfRange(46, 46 + u16(central, 28)), Charsets.UTF_8), central, bytes.copyOfRange(start, next), start)
        }
    }
    private fun withoutOffset(bytes: ByteArray): ByteArray = bytes.copyOf().apply { put32(this, 42, 0) }
    private fun comment(bytes: ByteArray): ByteArray = bytes.copyOfRange(endOffset(bytes) + 22, bytes.size)
    private fun centralOffset(bytes: ByteArray): Int = u32(bytes, endOffset(bytes) + 16).toInt()
    private fun endOffset(bytes: ByteArray): Int = (bytes.size - 22 downTo 0).first { u32(bytes, it) == 0x06054b50L && it + 22 + u16(bytes, it + 20) == bytes.size }
    private fun u16(bytes: ByteArray, at: Int): Int = (bytes[at].toInt() and 255) or ((bytes[at + 1].toInt() and 255) shl 8)
    private fun u32(bytes: ByteArray, at: Int): Long = (0..3).fold(0L) { value, index -> value or ((bytes[at + index].toLong() and 255) shl (index * 8)) }
    private fun put16(bytes: ByteArray, at: Int, value: Int) { repeat(2) { bytes[at + it] = (value ushr (8 * it)).toByte() } }
    private fun put32(bytes: ByteArray, at: Int, value: Long) { repeat(4) { bytes[at + it] = (value ushr (8 * it)).toByte() } }
    private fun rejects(block: () -> Unit) { try { block(); fail("Expected ZIP rejection") } catch (_: IllegalArgumentException) { } }
}
