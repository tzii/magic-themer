package dev.magicthemer.icons

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawableListParserTest {
    private fun parse(xml: String) = DrawableListParser.parse(ByteArrayInputStream(xml.toByteArray()))

    @Test fun retainsAlternativesAndUnmappedArtworkInCatalogueOrder() {
        val result = parse("""<resources>
            <category title="Applications"/>
            <item drawable="messages"/>
            <item drawable="messages_alt_1"/>
            <category title="Extra artwork"/>
            <item drawable="custom_rainbow"/>
            <item drawable="messages"/>
            </resources>""")
        assertEquals(listOf("messages", "messages_alt_1", "custom_rainbow"), result.drawables)
        assertTrue(result.warnings.isEmpty())
    }

    @Test fun skipsUnsafeAndMissingNamesWithoutLosingValidAlternatives() {
        val result = parse("""<resources>
            <item drawable="../outside"/><item drawable="@drawable/ref"/><item/>
            <item drawable="valid_alternative"/>
            </resources>""")
        assertEquals(listOf("valid_alternative"), result.drawables)
        assertEquals(listOf("3 invalid drawable.xml icon names were skipped."), result.warnings)
    }

    @Test fun compiledResourceBuilderUsesSameValidation() {
        val builder = DrawableListParser.Builder()
        builder.startElement("resources", emptyMap())
        builder.startElement("category", mapOf("title" to "Alternatives"))
        builder.startElement("item", mapOf("drawable" to "original"))
        builder.startElement("item", mapOf("drawable" to "alternative"))
        builder.startElement("item", mapOf("drawable" to "../invalid"))
        val result = builder.build()
        assertEquals(listOf("original", "alternative"), result.drawables)
        assertEquals(1, result.warnings.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsExternalDocumentTypes() {
        parse("""<!DOCTYPE resources SYSTEM "https://example.invalid/icons"><resources/>""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedRoot() { parse("<icons><item drawable=\"icon\"/></icons>") }

    @Test(expected = IllegalArgumentException::class)
    fun boundsCatalogueCount() {
        val builder = DrawableListParser.Builder()
        builder.startElement("resources", emptyMap())
        repeat(DrawableListParser.MAX_DRAWABLES + 1) {
            builder.startElement("item", mapOf("drawable" to "icon_$it"))
        }
    }
}
