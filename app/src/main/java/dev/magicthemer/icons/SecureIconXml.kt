package dev.magicthemer.icons

import java.io.ByteArrayInputStream
import java.io.InputStream
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

/** Shared bounded text reader; pack metadata requires no DTD or external entities. */
internal object SecureIconXml {
    const val MAX_XML_BYTES = 16 * 1024 * 1024

    fun parse(stream: InputStream, documentName: String, startElement: (String, Map<String, String>) -> Unit) {
        val bytes = stream.readNBytesCompat(MAX_XML_BYTES + 1)
        require(bytes.size <= MAX_XML_BYTES) { "$documentName exceeds the 16 MiB limit." }
        require(bytes.none { it == 0.toByte() }) { "Text icon metadata must use UTF-8 or an ASCII-compatible encoding." }
        // Android SAX implementations vary in their support for security flags.
        // Reject declarations before parsing as well as rejecting entity resolution.
        val text = bytes.toString(Charsets.UTF_8)
        require(!text.contains("<!DOCTYPE", ignoreCase = true) &&
            !text.contains("<!ENTITY", ignoreCase = true)) { "XML document types and entities are not supported." }
        val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
        for (feature in listOf(
            "http://xml.org/sax/features/external-general-entities",
            "http://xml.org/sax/features/external-parameter-entities",
            "http://apache.org/xml/features/nonvalidating/load-external-dtd",
        )) {
            try { factory.setFeature(feature, false) } catch (_: Exception) { /* Expat differs from desktop SAX. */ }
        }
        val reader = factory.newSAXParser().xmlReader
        reader.entityResolver = org.xml.sax.EntityResolver { _, _ ->
            throw SAXException("External XML entities are not supported.")
        }
        reader.contentHandler = object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
                require(attributes.length <= 128) { "Too many attributes in $documentName." }
                val values = buildMap {
                    for (index in 0 until attributes.length) {
                        put(attributes.getLocalName(index).ifEmpty { attributes.getQName(index) }, attributes.getValue(index))
                    }
                }
                startElement(localName.orEmpty().ifEmpty { qName.orEmpty() }, values)
            }
        }
        reader.errorHandler = object : DefaultHandler() {
            override fun error(error: org.xml.sax.SAXParseException) { throw error }
            override fun fatalError(error: org.xml.sax.SAXParseException) { throw error }
        }
        reader.parse(InputSource(ByteArrayInputStream(bytes)))
    }

    private fun InputStream.readNBytesCompat(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (output.size() < limit) {
            val count = read(buffer, 0, minOf(buffer.size, limit - output.size()))
            if (count < 0) break
            if (count == 0) continue
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
