package dev.magicthemer.theme

import java.io.ByteArrayInputStream
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/** Classic ZIP editor: untouched records are copied raw, including descriptors. */
internal object ThemeZip {
    sealed interface Content
    data class Bytes(val bytes: ByteArray) : Content
    data class FileContent(val file: File) : Content

    private const val MAX_INPUT = 256L * 1024 * 1024
    private const val MAX_OUTPUT = 512L * 1024 * 1024
    private const val MAX_MEMBER = 128L * 1024 * 1024
    private const val MAX_CENTRAL = 16L * 1024 * 1024
    private const val MAX_ENTRIES = 20_000
    private const val LOCAL = 0x04034b50L
    private const val CENTRAL = 0x02014b50L
    private const val END = 0x06054b50L
    private const val DESCRIPTOR = 0x08074b50L
    private data class Entry(val name: String, val central: ByteArray, val local: ByteArray, val offset: Long, var end: Long = 0)
    private data class Archive(val entries: List<Entry>, val comment: ByteArray)
    private data class Payload(val file: File, val size: Long, val crc: Long)

    fun rewrite(source: File, output: OutputStream, replacements: Map<String, Content>, removed: Set<String> = emptySet(),
        normalizeDescriptors: Boolean = false) {
        require(replacements.keys.intersect(removed).isEmpty()) { "ZIP member cannot be replaced and removed." }
        val staged = ArrayList<File>()
        try {
            RandomAccessFile(source, "r").use { input ->
                val archive = readArchive(input)
                val sink = BoundedOutput(output)
                if (replacements.isEmpty() && removed.isEmpty() &&
                    (!normalizeDescriptors || archive.entries.none { u16(it.central, 8) and 8 != 0 })) {
                    copy(input, 0, input.length(), sink); return
                }
                val names = archive.entries.mapTo(hashSetOf()) { it.name }
                val appended = replacements.keys.filter { it !in names }
                require(archive.entries.count { it.name !in removed } + appended.size <= MAX_ENTRIES) { "Too many ZIP members." }
                val centralRecords = hashMapOf<String, ByteArray>()
                for (entry in archive.entries.sortedBy { it.offset }) {
                    if (entry.name in removed) continue
                    val offset = sink.count
                    val replacement = replacements[entry.name]
                    val central = if (replacement == null) {
                        if (normalizeDescriptors && u16(entry.central, 8) and 8 != 0) normalize(entry, input, sink)
                        else { copy(input, entry.offset, entry.end - entry.offset, sink); entry.central.copyOf() }
                    } else changed(entry, replacement, source.absoluteFile.parentFile, sink, staged)
                    put32(central, 42, offset)
                    centralRecords[entry.name] = central
                }
                for (name in appended) {
                    val offset = sink.count
                    val entry = freshEntry(name)
                    val central = changed(entry, replacements.getValue(name), source.absoluteFile.parentFile, sink, staged)
                    put32(central, 42, offset)
                    centralRecords[name] = central
                }
                finish(sink, archive.entries.filter { it.name !in removed }.map { centralRecords.getValue(it.name) } +
                    appended.map { centralRecords.getValue(it) }, archive.comment)
            }
        } finally { staged.forEach { it.delete() } }
    }

    fun create(output: OutputStream, entries: Map<String, Content>, tempDirectory: File? = null) {
        require(entries.size <= MAX_ENTRIES) { "Too many ZIP members." }
        val staged = ArrayList<File>()
        try {
            val sink = BoundedOutput(output)
            val central = entries.map { (name, content) ->
                val offset = sink.count
                changed(freshEntry(name), content, tempDirectory, sink, staged).also { put32(it, 42, offset) }
            }
            finish(sink, central, byteArrayOf())
        } finally { staged.forEach { it.delete() } }
    }

    private fun readArchive(input: RandomAccessFile): Archive {
        val length = input.length()
        require(length in 22..MAX_INPUT) { "ZIP is missing or exceeds 256 MiB." }
        val tailSize = minOf(length, 65_557).toInt()
        val tail = read(input, length - tailSize, tailSize)
        val endIndex = (tail.size - 22 downTo 0).firstOrNull {
            u32(tail, it) == END && it + 22 + u16(tail, it + 20) == tail.size
        } ?: throw IllegalArgumentException("ZIP has no supported end record; trailing data is not supported.")
        val endOffset = length - tailSize + endIndex
        require(u16(tail, endIndex + 4) == 0 && u16(tail, endIndex + 6) == 0) { "Multi-disk ZIP is not supported." }
        val count = u16(tail, endIndex + 10)
        require(count == u16(tail, endIndex + 8) && count <= MAX_ENTRIES) { "ZIP64 or excessive member count is not supported." }
        val centralSize = u32(tail, endIndex + 12)
        val centralOffset = u32(tail, endIndex + 16)
        require(centralSize <= MAX_CENTRAL && centralOffset + centralSize == endOffset) { "Invalid ZIP central bounds or unsupported signed/ZIP64 data." }
        val entries = ArrayList<Entry>(count)
        val seen = hashSetOf<String>()
        var position = centralOffset
        var localMetadata = 0L
        repeat(count) {
            require(position + 46 <= endOffset) { "Truncated ZIP central record." }
            val header = read(input, position, 46)
            require(u32(header, 0) == CENTRAL) { "Invalid ZIP central signature." }
            val nameSize = u16(header, 28); val extraSize = u16(header, 30); val commentSize = u16(header, 32)
            require(nameSize in 1..512 && position + 46 + nameSize + extraSize + commentSize <= endOffset) { "Invalid ZIP member name or central bounds." }
            val central = read(input, position, 46 + nameSize + extraSize + commentSize)
            val flags = u16(central, 8); val method = u16(central, 10)
            require(flags and 0x080e == flags && method in setOf(0, 8) && u16(central, 6) <= 20 && u16(central, 34) == 0) {
                "Encrypted, enhanced or multi-disk ZIP is not supported."
            }
            require(method != 0 || flags and 6 == 0) { "Unsupported stored ZIP flags." }
            val compressed = u32(central, 20); val size = u32(central, 24); val offset = u32(central, 42)
            require(compressed <= MAX_INPUT && size <= MAX_MEMBER && offset < centralOffset) { "Invalid ZIP member size or offset." }
            checkExtra(central, 46 + nameSize, extraSize)
            val rawName = central.copyOfRange(46, 46 + nameSize)
            val name = decodeName(rawName, flags)
            require(seen.add(name)) { "Duplicate ZIP member: $name" }
            require(offset + 30 <= centralOffset) { "Truncated ZIP local record." }
            val localHead = read(input, offset, 30)
            require(u32(localHead, 0) == LOCAL && u16(localHead, 26) == nameSize &&
                u16(localHead, 4) == u16(central, 6) && u16(localHead, 6) == flags && u16(localHead, 8) == method &&
                u16(localHead, 10) == u16(central, 12) && u16(localHead, 12) == u16(central, 14)) { "ZIP local and central headers disagree." }
            val localExtra = u16(localHead, 28)
            localMetadata += 30L + nameSize + localExtra
            require(localMetadata <= MAX_CENTRAL) { "ZIP local metadata exceeds its supported size." }
            require(offset + 30 + nameSize + localExtra + compressed <= centralOffset) { "ZIP local payload is out of bounds." }
            val local = read(input, offset, 30 + nameSize + localExtra)
            require(local.copyOfRange(30, 30 + nameSize).contentEquals(rawName)) { "ZIP local filename differs from the central filename." }
            checkExtra(local, 30 + nameSize, localExtra)
            if (flags and 8 == 0) require(u32(local, 14) == u32(central, 16) &&
                u32(local, 18) == compressed && u32(local, 22) == size) { "ZIP local CRC or sizes disagree." }
            else require(listOf(14, 18, 22).all { u32(local, it) == 0L }) { "Unsupported ZIP descriptor header." }
            entries += Entry(name, central, local, offset)
            position += central.size
        }
        require(position == endOffset) { "ZIP central signatures or trailing data are not supported." }
        val physical = entries.sortedBy { it.offset }
        require((physical.firstOrNull()?.offset ?: centralOffset) == 0L) { "Prefixed ZIP is not supported." }
        physical.forEachIndexed { index, entry ->
            val next = physical.getOrNull(index + 1)?.offset ?: centralOffset
            val dataEnd = entry.offset + entry.local.size + u32(entry.central, 20)
            require(dataEnd <= next) { "ZIP member records overlap." }
            if (u16(entry.central, 8) and 8 != 0) {
                val descriptorSize = next - dataEnd
                require(descriptorSize == 12L || descriptorSize == 16L) { "Invalid ZIP data descriptor bounds." }
                val descriptor = read(input, dataEnd, descriptorSize.toInt())
                val at = if (descriptorSize == 16L) { require(u32(descriptor, 0) == DESCRIPTOR) { "Invalid ZIP data descriptor signature." }; 4 } else 0
                require(u32(descriptor, at) == u32(entry.central, 16) && u32(descriptor, at + 4) == u32(entry.central, 20) &&
                    u32(descriptor, at + 8) == u32(entry.central, 24)) { "ZIP data descriptor values disagree." }
            } else require(dataEnd == next) { "ZIP gaps or signing blocks are not supported." }
            entry.end = next
        }
        return Archive(entries, tail.copyOfRange(endIndex + 22, tail.size))
    }

    private fun freshEntry(name: String): Entry {
        val encoded = name.toByteArray(Charsets.UTF_8)
        require(encoded.size in 1..512 && decodeName(encoded, 0x800) == name) { "Invalid ZIP member name." }
        val directory = name.endsWith('/')
        val flags = if (directory) 0x800 else 0x806
        val method = if (directory) 0 else 8
        val local = ByteArray(30 + encoded.size)
        put32(local, 0, LOCAL); put16(local, 4, 20); put16(local, 6, flags); put16(local, 8, method)
        put16(local, 12, 33); put16(local, 26, encoded.size); encoded.copyInto(local, 30)
        val central = ByteArray(46 + encoded.size)
        put32(central, 0, CENTRAL); put16(central, 4, 0x0333); put16(central, 6, 20); put16(central, 8, flags)
        put16(central, 10, method); put16(central, 14, 33); put16(central, 28, encoded.size)
        put32(central, 38, if (directory) 0x41c00000L else 0x81800000L); encoded.copyInto(central, 46)
        return Entry(name, central, local, 0)
    }

    private fun changed(entry: Entry, content: Content, directory: File?, output: OutputStream, staged: MutableList<File>): ByteArray {
        val method = u16(entry.central, 10)
        val flags = u16(entry.central, 8) and 8.inv()
        val level = when (flags and 6) { 2 -> Deflater.BEST_COMPRESSION; 4 -> 3; 6 -> Deflater.BEST_SPEED; else -> Deflater.DEFAULT_COMPRESSION }
        val payload = stage(content, method, level, directory, staged)
        try {
            require(!entry.name.endsWith('/') || payload.size == 0L) { "ZIP directory must be empty." }
            val local = entry.local.copyOf()
            put16(local, 6, flags); put32(local, 14, payload.crc); put32(local, 18, payload.file.length()); put32(local, 22, payload.size)
            output.write(local)
            payload.file.inputStream().use { it.copyTo(output, 32 * 1024) }
            return entry.central.copyOf().also {
                put16(it, 8, flags); put32(it, 16, payload.crc); put32(it, 20, payload.file.length()); put32(it, 24, payload.size)
            }
        } finally { payload.file.delete(); staged.remove(payload.file) }
    }

    /** Explicit repair changes framing only; the original deflate bytes survive. */
    private fun normalize(entry: Entry, input: RandomAccessFile, output: OutputStream): ByteArray {
        val flags = u16(entry.central, 8) and 8.inv()
        val local = entry.local.copyOf()
        put16(local, 6, flags)
        put32(local, 14, u32(entry.central, 16)); put32(local, 18, u32(entry.central, 20)); put32(local, 22, u32(entry.central, 24))
        output.write(local)
        copy(input, entry.offset + entry.local.size, u32(entry.central, 20), output)
        return entry.central.copyOf().also { put16(it, 8, flags) }
    }

    private fun stage(content: Content, method: Int, level: Int, directory: File?, staged: MutableList<File>): Payload {
        val expected = when (content) { is Bytes -> content.bytes.size.toLong(); is FileContent -> content.file.length().also { require(content.file.isFile) { "ZIP input file is unavailable." } } }
        require(expected in 0..MAX_MEMBER) { "ZIP member exceeds 128 MiB." }
        val file = File.createTempFile("theme-zip-payload-", ".tmp", directory).also(staged::add)
        val crc = CRC32(); var size = 0L
        fun copyTo(sink: OutputStream) {
            val input = when (content) { is Bytes -> ByteArrayInputStream(content.bytes); is FileContent -> content.file.inputStream() }
            input.use {
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    val count = it.read(buffer); if (count < 0) break
                    size += count; require(size <= MAX_MEMBER) { "ZIP input grew beyond 128 MiB." }
                    crc.update(buffer, 0, count); sink.write(buffer, 0, count)
                }
            }
        }
        val deflater = Deflater(level, true)
        try { file.outputStream().use { sink ->
            if (method == 0) copyTo(sink) else DeflaterOutputStream(sink, deflater, 32 * 1024).use { copyTo(it) }
        } } finally { deflater.end() }
        require(size == expected) { "ZIP input changed while reading." }
        return Payload(file, size, crc.value)
    }

    private fun finish(output: BoundedOutput, records: List<ByteArray>, comment: ByteArray) {
        val offset = output.count
        records.forEach(output::write)
        val end = ByteArray(22)
        put32(end, 0, END); put16(end, 8, records.size); put16(end, 10, records.size)
        put32(end, 12, output.count - offset); put32(end, 16, offset); put16(end, 20, comment.size)
        output.write(end); output.write(comment)
    }

    private fun checkExtra(bytes: ByteArray, start: Int, length: Int) {
        var position = start
        val end = start + length
        while (position < end) {
            require(position + 4 <= end) { "Malformed ZIP extra field." }
            val id = u16(bytes, position); val size = u16(bytes, position + 2)
            require(id !in setOf(1, 0x14, 0x15, 0x16, 0x17, 0x9901) && position + 4 + size <= end) { "ZIP64, signed, encrypted or malformed ZIP extra fields are not supported." }
            position += 4 + size
        }
    }

    private fun decodeName(bytes: ByteArray, flags: Int): String {
        val charset = if (flags and 0x800 != 0) Charsets.UTF_8 else Charset.forName("IBM437")
        return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString().also { require(it.isNotEmpty() && it.length <= 512 && it.none { c -> c.code < 32 || c.code == 127 }) { "Invalid ZIP filename." } }
    }

    private fun read(input: RandomAccessFile, offset: Long, count: Int): ByteArray = ByteArray(count).also {
        require(offset >= 0 && count >= 0 && offset + count <= input.length()) { "ZIP read is out of bounds." }
        input.seek(offset); input.readFully(it)
    }
    private fun copy(input: RandomAccessFile, offset: Long, size: Long, output: OutputStream) {
        input.seek(offset); var remaining = size
        val buffer = ByteArray(32 * 1024)
        while (remaining > 0) { val count = minOf(remaining, buffer.size.toLong()).toInt(); input.readFully(buffer, 0, count); output.write(buffer, 0, count); remaining -= count }
    }
    private fun u16(bytes: ByteArray, at: Int): Int = (bytes[at].toInt() and 255) or ((bytes[at + 1].toInt() and 255) shl 8)
    private fun u32(bytes: ByteArray, at: Int): Long = (0..3).fold(0L) { value, index -> value or ((bytes[at + index].toLong() and 255) shl (index * 8)) }
    private fun put16(bytes: ByteArray, at: Int, value: Int) { repeat(2) { bytes[at + it] = (value ushr (8 * it)).toByte() } }
    private fun put32(bytes: ByteArray, at: Int, value: Long) { require(value in 0..0xffffffffL); repeat(4) { bytes[at + it] = (value ushr (8 * it)).toByte() } }
    private class BoundedOutput(private val output: OutputStream) : OutputStream() {
        var count = 0L; private set
        override fun write(value: Int) { require(count < MAX_OUTPUT) { "ZIP output exceeds 512 MiB." }; output.write(value); count++ }
        override fun write(bytes: ByteArray, offset: Int, length: Int) { require(length >= 0 && length.toLong() <= MAX_OUTPUT - count) { "ZIP output exceeds 512 MiB." }; output.write(bytes, offset, length); count += length }
    }
}
