package dev.magicthemer.theme

import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipFile

/** Snapshot of which packages were manually customized in an exported theme. */
data class ThemeExportRecord(
    val archiveSha256: String,
    val iconsSha256: String,
    val title: String,
    val basePack: String?,
    val customizedPackages: Set<String>,
    val timestampMillis: Long = System.currentTimeMillis(),
)

/**
 * Local ledger of themes exported by Magic Themer, keyed by SHA-256 of the
 * archive and its inner `icons` ZIP so re-opening an exported `.hnt` highlights
 * previously customized apps without adding any extra files to the `.hnt` archive.
 */
class ThemeExportHistory(private val file: File, private val maxRecords: Int = 50) {
    @Synchronized
    fun record(entry: ThemeExportRecord) {
        val current = readAll().filterNot {
            it.archiveSha256.equals(entry.archiveSha256, ignoreCase = true) ||
                (entry.iconsSha256.isNotEmpty() && it.iconsSha256.equals(entry.iconsSha256, ignoreCase = true) && it.title == entry.title)
        }
        val updated = (listOf(entry) + current).take(maxRecords)
        writeAll(updated)
    }

    @Synchronized
    fun find(archiveSha256: String, iconsSha256: String): ThemeExportRecord? {
        val all = readAll()
        return all.firstOrNull { it.archiveSha256.equals(archiveSha256, ignoreCase = true) }
            ?: all.firstOrNull { iconsSha256.isNotEmpty() && it.iconsSha256.equals(iconsSha256, ignoreCase = true) }
    }

    private fun readAll(): List<ThemeExportRecord> {
        if (!file.isFile) return emptyList()
        return runCatching {
            val lines = file.readLines(StandardCharsets.UTF_8)
            if (lines.firstOrNull() != FORMAT_HEADER) return emptyList()
            buildList {
                for (i in 1 until lines.size) {
                    val parts = lines[i].split('\t')
                    if (parts.size < 6) continue
                    val archive = parts[0].trim()
                    if (archive.isEmpty()) continue
                    val icons = parts[1].trim()
                    val timestamp = parts[2].toLongOrNull() ?: 0L
                    val title = unescape(parts[3])
                    val basePack = unescape(parts[4]).takeIf { it.isNotBlank() }
                    val custom = parts[5].split(',')
                        .map { unescape(it).trim() }
                        .filterTo(linkedSetOf()) { it.isNotEmpty() }
                    add(ThemeExportRecord(archive, icons, title, basePack, custom, timestamp))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeAll(records: List<ThemeExportRecord>) {
        val content = buildString {
            append(FORMAT_HEADER).append('\n')
            records.forEach { rec ->
                append(rec.archiveSha256.trim()).append('\t')
                append(rec.iconsSha256.trim()).append('\t')
                append(rec.timestampMillis).append('\t')
                append(escape(rec.title)).append('\t')
                append(escape(rec.basePack.orEmpty())).append('\t')
                append(rec.customizedPackages.sorted().joinToString(",") { escape(it) }).append('\n')
            }
        }
        val parent = file.absoluteFile.parentFile
        if (parent != null && !parent.isDirectory) parent.mkdirs()
        val tmp = File.createTempFile("export-history-", ".tmp", parent)
        try {
            tmp.writeText(content, StandardCharsets.UTF_8)
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        } catch (_: Exception) {
            tmp.delete()
        }
    }

    companion object {
        private const val FORMAT_HEADER = "magic-themer-export-history-v1"
        private val TRAILING_NUMBER = Regex("^(.*?)(\\s+(?:v|\\()?)(\\d+)(\\))?$")

        /**
         * Suggests a distinct copy title so MagicOS Themes lists the edited copy
         * alongside the original (`Enix` → `Enix 2`, `Enix 2` → `Enix 3`, `Enix (2)` → `Enix (3)`).
         */
        fun suggestCopyTitle(original: String, maxLength: Int = 64): String {
            val trimmed = original.trim()
            if (trimmed.isEmpty()) return "My Magic Theme 2"
            val match = TRAILING_NUMBER.matchEntire(trimmed)
            if (match != null) {
                val base = match.groupValues[1]
                val prefix = match.groupValues[2]
                val num = match.groupValues[3].toLongOrNull()
                val closing = match.groupValues[4]
                val balancedParens = prefix.endsWith("(") == (closing == ")")
                if (balancedParens && num != null && num < 999_999L && base.isNotBlank()) {
                    val suffix = "$prefix${num + 1}$closing"
                    val fittedBase = base.take((maxLength - suffix.length).coerceAtLeast(0)).trimEnd()
                    val combined = (fittedBase + suffix).trim()
                    if (combined.isNotEmpty() && combined != trimmed) return combined
                }
            }
            val suffix = " 2"
            return trimmed.take((maxLength - suffix.length).coerceAtLeast(1)).trimEnd() + suffix
        }

        fun hex(bytes: ByteArray): String =
            bytes.joinToString("") { byte -> String.format(Locale.ROOT, "%02x", byte.toInt() and 0xff) }

        /** Computes `(archiveSha256, innerIconsSha256)` for an exported `.hnt`/`.hwt` file. */
        fun hashExport(archiveFile: File): Pair<String, String> {
            val archiveSha = archiveFile.inputStream().use(::sha256)
            val iconsSha = runCatching {
                ZipFile(archiveFile, StandardCharsets.UTF_8).use { zip ->
                    zip.getEntry("icons")?.let { entry -> zip.getInputStream(entry).use(::sha256) }.orEmpty()
                }
            }.getOrDefault("")
            return archiveSha to iconsSha
        }

        private fun sha256(input: InputStream): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
            return hex(digest.digest())
        }

        private fun escape(value: String): String = buildString(value.length) {
            for (ch in value) when (ch) {
                '\\' -> append("\\\\")
                '\t' -> append("\\t")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                ',' -> append("\\c")
                else -> append(ch)
            }
        }

        private fun unescape(value: String): String = buildString(value.length) {
            var i = 0
            while (i < value.length) {
                val ch = value[i]
                if (ch == '\\' && i + 1 < value.length) {
                    when (value[i + 1]) {
                        '\\' -> append('\\')
                        't' -> append('\t')
                        'n' -> append('\n')
                        'r' -> append('\r')
                        'c' -> append(',')
                        else -> append(value[i + 1])
                    }
                    i += 2
                } else {
                    append(ch)
                    i++
                }
            }
        }
    }
}
