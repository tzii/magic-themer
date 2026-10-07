package dev.magicthemer.theme

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.SAXParserFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.SAXException
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

/** Edits ordinary local ZIP themes without extracting any archive paths. */
object ThemeEditor {
    const val MAX_ENTRIES = 20_000
    const val MAX_ARCHIVE_BYTES = 256L * 1024 * 1024
    const val MAX_EXPANDED_BYTES = 512L * 1024 * 1024
    const val MAX_MEMBER_BYTES = 128L * 1024 * 1024
    const val MAX_METADATA_BYTES = 1024 * 1024
    const val MAX_ICON_BYTES = 512 * 1024
    const val MAX_PREVIEW_BYTES = 4 * 1024 * 1024

    private val packagePattern = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")
    private val launcherAliasPattern = Regex(":[A-Z_]+\\.png")
    private val pngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)

    /** [file] must be the app's private working copy; its contents are never changed. */
    fun open(file: File): ThemeDocument {
        require(file.isFile && file.length() in 1..MAX_ARCHIVE_BYTES) { "Theme file is missing or exceeds 256 MiB." }
        val cache = File.createTempFile("theme-icons-", ".zip", file.absoluteFile.parentFile)
        try {
            val budget = Budget()
            var description: ByteArray? = null
            var foundIcons = false
            var outerNames: Set<String> = emptySet()
            ZipFile(file, StandardCharsets.UTF_8).use { zip ->
                val entries = checkedEntries(zip)
                outerNames = entries.map { it.name }.toSet()
                require(entries.any { it.name == "description.xml" && !it.isDirectory }) { "Theme has no description.xml." }
                require(entries.any { it.name == "icons" && !it.isDirectory }) { "Theme has no extensionless icons ZIP module." }
                entries.forEach { entry ->
                    when (entry.name) {
                        "description.xml" -> {
                            require(entry.size <= MAX_METADATA_BYTES) { "Theme metadata exceeds 1 MiB." }
                            val buffer = ByteArrayOutputStream()
                            readEntry(zip, entry, budget, buffer, MAX_METADATA_BYTES.toLong())
                            description = buffer.toByteArray()
                        }
                        "icons" -> cache.outputStream().use { sink ->
                            readEntry(zip, entry, budget, sink)
                            foundIcons = true
                        }
                        else -> readEntry(zip, entry, budget)
                    }
                }
            }
            require(foundIcons) { "Theme icons module could not be read." }
            val xml = parseMetadata(requireNotNull(description))
            val paths = linkedMapOf<String, MutableList<String>>()
            var onlyFlatPackageIcons = true
            ZipFile(cache, StandardCharsets.UTF_8).use { zip ->
                checkedEntries(zip).forEach { entry ->
                    val packageName = iconPackage(entry.name)
                    if (packageName != null && !entry.isDirectory) {
                        require(entry.size <= MAX_ICON_BYTES) { "Icon $packageName exceeds 512 KiB." }
                        val prefix = readEntry(zip, entry, budget, limit = MAX_ICON_BYTES.toLong())
                        require(prefix.contentEquals(pngSignature)) { "Icon $packageName is not a PNG." }
                        paths.getOrPut(packageName) { mutableListOf() }.add(entry.name)
                        if (entry.name != "$packageName.png") onlyFlatPackageIcons = false
                    } else {
                        onlyFlatPackageIcons = false
                        readEntry(zip, entry, budget)
                    }
                }
            }
            val mappedPaths = paths.mapValues { (_, entries) -> entries.sortedWith(compareBy({ it.count { c -> c == '/' } }, { it })) }
            val repairable = onlyFlatPackageIcons && isMinimalOldExport(xml, outerNames)
            val warnings = buildList {
                if (mappedPaths.isEmpty()) add("No static package PNG icons were found; dynamic icons and configuration are preserved.")
                if (mappedPaths.values.any { it.size > 1 }) add("Some packages have several icon paths; replacements update each matching path.")
                if (repairable) add("This older Magic Themer export can be upgraded to the MagicOS 10 compatibility profile.")
            }
            return ThemeDocument(
                file.absoluteFile, metadataText(xml, "title"), metadataText(xml, "designer"),
                metadataText(xml, "briefInfo", "briefinfo"), mappedPaths.mapValues { it.value.first() },
                warnings, repairable, cache, mappedPaths, digest(file), digest(cache),
            )
        } catch (failure: Exception) {
            cache.delete()
            throw IllegalArgumentException("Cannot open this theme: ${failure.message ?: "invalid archive"}", failure)
        }
    }

    fun readIcon(document: ThemeDocument, packageName: String): ByteArray? {
        val name = document.iconEntries[packageName] ?: return null
        ZipFile(document.iconsFile, StandardCharsets.UTF_8).use { zip ->
            val entry = zip.getEntry(name) ?: return null
            val buffer = ByteArrayOutputStream()
            readEntry(zip, entry, Budget(), buffer, MAX_ICON_BYTES.toLong())
            return buffer.toByteArray()
        }
    }

    /** Writes a new theme, preserving unrelated member contents and metadata nodes. */
    fun write(
        document: ThemeDocument,
        output: OutputStream,
        title: String,
        designer: String,
        briefInfo: String,
        replacements: Map<String, ByteArray>,
        removedPackages: Set<String> = emptySet(),
        replaceAllIcons: Boolean = false,
        preview: ByteArray? = null,
        iconsPreview: ByteArray? = null,
        upgradeMinimalProfile: Boolean = false,
    ) {
        checkText(title, 64, "Theme title", allowBlank = false)
        checkText(designer, 128, "Designer")
        checkText(briefInfo, 4096, "Description")
        require(replacements.size <= MAX_ENTRIES) { "Too many replacement icons." }
        replacements.forEach { (name, png) ->
            checkPackage(name)
            require(png.size in pngSignature.size..MAX_ICON_BYTES && pngSignature.indices.all { png[it] == pngSignature[it] }) {
                "Replacement for $name is not a supported PNG (maximum 512 KiB)."
            }
        }
        removedPackages.forEach(::checkPackage)
        require(replacements.keys.intersect(removedPackages).isEmpty()) { "An icon cannot be replaced and removed at the same time." }
        require(!upgradeMinimalProfile || document.compatibilityRepairAvailable) { "Only older minimal Magic Themer exports can use this compatibility upgrade." }
        listOfNotNull(preview, iconsPreview).forEach { jpeg ->
            require(jpeg.size in 3..MAX_PREVIEW_BYTES && jpeg[0] == 0xff.toByte() && jpeg[1] == 0xd8.toByte() && jpeg[2] == 0xff.toByte()) {
                "Preview must be a JPEG of at most 4 MiB."
            }
        }
        require(digest(document.sourceFile).contentEquals(document.sourceDigest)) { "Source theme changed; import it again." }
        require(digest(document.iconsFile).contentEquals(document.iconsDigest)) { "Working icons changed; import the theme again." }

        val editedIcons = File.createTempFile("theme-edited-", ".zip", document.sourceFile.parentFile)
        try {
            val innerExpandedBytes = rewriteIcons(document, editedIcons, replacements, removedPackages, replaceAllIcons)
            ZipFile(document.sourceFile, StandardCharsets.UTF_8).use { source ->
                val entries = checkedEntries(source)
                val originalXml = ByteArrayOutputStream().also {
                    readEntry(source, source.getEntry("description.xml"), Budget(), it, MAX_METADATA_BYTES.toLong())
                }.toByteArray()
                val xml = parseMetadata(originalXml)
                val previousTitle = metadataText(xml, "title")
                if (metadataText(xml, "title-cn") == previousTitle) updateMetadata(xml, "title-cn", title)
                updateMetadata(xml, "title", title)
                updateMetadata(xml, "designer", designer)
                updateMetadata(xml, "briefInfo", briefInfo, "briefinfo")
                if (upgradeMinimalProfile) upgradeMetadataProfile(xml, title, designer)
                val newXml = serializeMetadata(xml)
                val changed = linkedMapOf<String, ByteArray>("description.xml" to newXml)
                preview?.let { changed["preview/icon_small.jpg"] = it }
                iconsPreview?.let { changed["preview/preview_icons_0.jpg"] = it }
                if (upgradeMinimalProfile) {
                    changed["unlock/theme.xml"] = "<HWTheme />".toByteArray(StandardCharsets.UTF_8)
                    val unlockPreview = iconsPreview ?: preview ?: entries.firstOrNull {
                        it.name == "preview/preview_icons_0.jpg" || it.name == "preview/icon_small.jpg"
                    }?.let { entry ->
                        ByteArrayOutputStream().also {
                            readEntry(source, entry, Budget(), it, MAX_PREVIEW_BYTES.toLong())
                        }.toByteArray()
                    }
                    unlockPreview?.let { changed["preview/preview_unlock_0.jpg"] = it }
                }
                // Validate the completed output's expanded size before touching the destination.
                var outputBytes = editedIcons.length()
                entries.filter { it.name != "icons" }.forEach { outputBytes += changed[it.name]?.size?.toLong() ?: it.size }
                changed.filterKeys { name -> entries.none { it.name == name } }.values.forEach { outputBytes += it.size }
                require(outputBytes <= MAX_EXPANDED_BYTES - innerExpandedBytes) { "Edited theme data exceeds 512 MiB." }
                require(entries.size + changed.keys.count { name -> entries.none { it.name == name } } <= MAX_ENTRIES) { "Edited theme has too many entries." }

                ZipOutputStream(output, StandardCharsets.UTF_8).use { destination ->
                    entries.forEach { entry ->
                        when {
                            entry.name == "icons" -> put(destination, entry.name, editedIcons.inputStream(), entry)
                            changed.containsKey(entry.name) -> put(destination, entry.name, ByteArrayInputStream(requireNotNull(changed.remove(entry.name))), entry)
                            else -> put(destination, entry.name, source.getInputStream(entry), entry)
                        }
                    }
                    changed.forEach { (name, bytes) -> put(destination, name, ByteArrayInputStream(bytes)) }
                }
            }
        } finally {
            editedIcons.delete()
        }
    }

    /** Disposes only the private icons copy made by [open], leaving the source untouched. */
    fun close(document: ThemeDocument) { document.iconsFile.delete() }

    private fun rewriteIcons(document: ThemeDocument, target: File, replacements: Map<String, ByteArray>, removed: Set<String>, replaceAll: Boolean): Long {
        val budget = Budget()
        ZipFile(document.iconsFile, StandardCharsets.UTF_8).use { source ->
            val entries = checkedEntries(source)
            val handled = hashSetOf<String>()
            var writtenCount = 0
            ZipOutputStream(target.outputStream(), StandardCharsets.UTF_8).use { destination ->
                entries.forEach { entry ->
                    val name = iconPackage(entry.name).takeUnless { entry.isDirectory }
                    when {
                        name != null && name in removed -> Unit
                        name != null && replacements.containsKey(name) -> {
                            val bytes = replacements.getValue(name)
                            budget.add(bytes.size.toLong())
                            put(destination, entry.name, ByteArrayInputStream(bytes), entry)
                            handled.add(name)
                            writtenCount++
                        }
                        name != null && replaceAll -> Unit
                        else -> {
                            budget.add(entry.size)
                            put(destination, entry.name, source.getInputStream(entry), entry)
                            writtenCount++
                        }
                    }
                }
                replacements.toSortedMap().filterKeys { it !in handled }.forEach { (name, bytes) ->
                    budget.add(bytes.size.toLong())
                    put(destination, "$name.png", ByteArrayInputStream(bytes))
                    writtenCount++
                }
            }
            require(writtenCount <= MAX_ENTRIES) { "Edited icons module has too many entries." }
        }
        require(target.length() <= MAX_MEMBER_BYTES) { "Edited icons module exceeds 128 MiB." }
        return budget.total
    }

    private fun checkedEntries(zip: ZipFile): List<ZipEntry> {
        require(zip.size() <= MAX_ENTRIES) { "Archive contains more than $MAX_ENTRIES entries." }
        val seen = hashSetOf<String>()
        return zip.entries().asSequence().map { entry ->
            val name = entry.name
            // Known working themes include flat launcher aliases such as
            // :CAMERA.png. No member is extracted to a path on disk.
            val supportedAlias = launcherAliasPattern.matches(name)
            require(name.isNotEmpty() && name.length <= 512 && !name.startsWith('/') && !name.contains('\\') && (!name.contains(':') || supportedAlias) && name.none { it.code < 32 || it.code == 127 }) { "Archive contains an unsafe entry name." }
            val segments = name.removeSuffix("/").split('/')
            require(segments.none { it.isEmpty() || it == "." || it == ".." }) { "Archive contains an unsafe path." }
            require(seen.add(name)) { "Archive contains duplicate entry: $name" }
            require(entry.size in 0..MAX_MEMBER_BYTES) { "Archive member exceeds 128 MiB: $name" }
            require(entry.method == ZipEntry.STORED || entry.method == ZipEntry.DEFLATED) { "Unsupported ZIP compression method." }
            entry
        }.toList()
    }

    /** Fully reads a member, checking its actual size and CRC; returns its first PNG-signature-sized bytes. */
    private fun readEntry(zip: ZipFile, entry: ZipEntry, budget: Budget, sink: OutputStream? = null, limit: Long = MAX_MEMBER_BYTES): ByteArray {
        require(entry.size in 0..limit) { "Archive member exceeds its supported size: ${entry.name}" }
        val prefix = ByteArrayOutputStream(pngSignature.size)
        val crc = CRC32()
        var bytesRead = 0L
        zip.getInputStream(entry).use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                bytesRead += count
                require(bytesRead <= limit) { "Expanded archive member exceeds its supported size." }
                budget.add(count.toLong())
                crc.update(buffer, 0, count)
                if (prefix.size() < pngSignature.size) prefix.write(buffer, 0, minOf(count, pngSignature.size - prefix.size()))
                sink?.write(buffer, 0, count)
            }
        }
        require(bytesRead == entry.size && crc.value == entry.crc) { "Corrupt ZIP member: ${entry.name}" }
        return prefix.toByteArray()
    }

    private fun put(zip: ZipOutputStream, name: String, input: InputStream, original: ZipEntry? = null) {
        val entry = ZipEntry(name).apply {
            time = original?.time?.takeIf { it >= 0 } ?: 0L
            comment = original?.comment
        }
        zip.putNextEntry(entry)
        input.use { it.copyTo(zip, 32 * 1024) }
        zip.closeEntry()
    }

    private fun iconPackage(path: String): String? {
        if (path.startsWith("dynamic_icons/")) return null
        if (!path.endsWith(".png")) return null
        return path.substringAfterLast('/').removeSuffix(".png").takeIf { it.length <= 255 && packagePattern.matches(it) }
    }

    private fun checkPackage(name: String) {
        require(name.length <= 255 && packagePattern.matches(name)) { "Invalid Android package name." }
    }

    private fun parseMetadata(bytes: ByteArray): Document {
        require(bytes.size <= MAX_METADATA_BYTES) { "Theme metadata exceeds 1 MiB." }
        val text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        require(!Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(text)) { "Theme XML cannot contain a DTD or entity declaration." }
        checkXmlStructure(text)
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            runCatching { isXIncludeAware = false }
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
            runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") }
        }
        val builder = factory.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> throw SAXException("External XML entities are not supported.") }
        }
        return builder.parse(InputSource(StringReader(text.removePrefix("\uFEFF")))).also {
            require(it.documentElement.tagName in setOf("HnTheme", "HwTheme")) { "Unsupported theme metadata root." }
            require(it.doctype == null) { "Theme XML cannot contain a DTD." }
        }
    }

    private fun checkXmlStructure(text: String) {
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        val reader = factory.newSAXParser().xmlReader
        reader.entityResolver = org.xml.sax.EntityResolver { _, _ -> throw SAXException("External XML entities are not supported.") }
        reader.contentHandler = object : DefaultHandler() {
            private var depth = 0
            private var count = 0
            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                depth++
                count++
                require(depth <= 64 && count <= MAX_ENTRIES) { "Theme XML is too deeply nested or contains too many elements." }
            }
            override fun endElement(uri: String?, localName: String?, qName: String?) { depth-- }
        }
        reader.parse(InputSource(StringReader(text.removePrefix("\uFEFF"))))
    }

    private fun metadataElement(document: Document, vararg names: String): Element? =
        (0 until document.documentElement.childNodes.length).map { document.documentElement.childNodes.item(it) }
            .filterIsInstance<Element>().firstOrNull { it.tagName in names }

    private fun metadataText(document: Document, vararg names: String): String = metadataElement(document, *names)?.textContent.orEmpty()

    /** Recognize only the exact, minimal v0.1/v0.2 profile authored here. */
    private fun isMinimalOldExport(xml: Document, outerNames: Set<String>): Boolean {
        val root = xml.documentElement
        val expected = setOf("title", "designer", "screen", "version", "briefInfo")
        val children = (0 until root.childNodes.length).map { root.childNodes.item(it) }.filterIsInstance<Element>()
        return root.tagName == "HnTheme" && root.attributes.length == 0 && children.map { it.tagName }.toSet() == expected &&
            children.size == expected.size && children.all { it.attributes.length == 0 && it.childNodes.let { nodes ->
                (0 until nodes.length).none { nodes.item(it) is Element }
            } } && metadataText(xml, "version") == "8.0.0" && metadataText(xml, "screen") == "FHD" &&
            metadataText(xml, "designer") == "Magic Themer" && outerNames.all {
                it in setOf("description.xml", "icons", "preview/", "preview/icon_small.jpg", "preview/preview_icons_0.jpg")
            }
    }

    private fun upgradeMetadataProfile(xml: Document, title: String, designer: String) {
        val previousRoot = xml.documentElement
        val newRoot = xml.createElement("HwTheme")
        while (previousRoot.hasChildNodes()) newRoot.appendChild(previousRoot.firstChild)
        xml.replaceChild(newRoot, previousRoot)
        metadataElement(xml, "screen")?.let { xml.documentElement.removeChild(it) }
        updateMetadata(xml, "title-cn", title)
        updateMetadata(xml, "author", designer)
        updateMetadata(xml, "version", ThemeArchive.SCHEMA_VERSION)
        updateMetadata(xml, "font", "Default")
        updateMetadata(xml, "font-cn", "Default")
    }

    private fun updateMetadata(document: Document, name: String, value: String, vararg aliases: String) {
        val existing = metadataElement(document, name, *aliases)
        if (existing != null) {
            // Leave an unchanged element entirely intact, including attributes/children.
            if (existing.textContent != value) existing.textContent = value
        } else document.documentElement.appendChild(document.createElement(name).apply { textContent = value })
    }

    private fun serializeMetadata(document: Document): ByteArray {
        val factory = TransformerFactory.newInstance().apply {
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
            runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalStylesheet", "") }
        }
        return ByteArrayOutputStream().also { buffer ->
            factory.newTransformer().apply {
                setOutputProperty(OutputKeys.ENCODING, "UTF-8")
                setOutputProperty(OutputKeys.INDENT, "no")
            }.transform(DOMSource(document), StreamResult(buffer))
        }.toByteArray().also { require(it.size <= MAX_METADATA_BYTES) { "Edited metadata exceeds 1 MiB." } }
    }

    private fun checkText(value: String, max: Int, label: String, allowBlank: Boolean = true) {
        require(value.length <= max && (allowBlank || value.isNotBlank())) { "$label must contain ${if (allowBlank) "0" else "1"}–$max characters." }
        var index = 0
        while (index < value.length) {
            val cp = Character.codePointAt(value, index)
            require(cp == 9 || cp == 10 || cp == 13 || cp in 0x20..0xd7ff || cp in 0xe000..0xfffd || cp in 0x10000..0x10ffff) { "$label contains invalid XML characters." }
            index += Character.charCount(cp)
        }
    }

    private fun digest(file: File): ByteArray = MessageDigest.getInstance("SHA-256").also { digest ->
        require(file.isFile && file.length() <= MAX_ARCHIVE_BYTES) { "Theme working file is unavailable or too large." }
        file.inputStream().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
    }.digest()

    private class Budget {
        var total = 0L
            private set
        fun add(bytes: Long) {
            require(bytes >= 0 && bytes <= MAX_EXPANDED_BYTES - total) { "Expanded theme data exceeds 512 MiB." }
            total += bytes
        }
    }
}
