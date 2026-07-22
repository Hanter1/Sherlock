package com.sherlock.bot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogLimitsTest {

    @Test
    fun rejectsNonHttpsUrl() {
        assertEquals("нужен HTTPS URL", CatalogLimits.validateRemoteUrl("http://example.com/c.json"))
        assertNull(CatalogLimits.validateRemoteUrl("https://example.com/c.json"))
    }

    @Test
    fun rejectsDuplicateSitesAndHttpTemplates() {
        val parsed = OsintCatalogParser.parseFull(
            """
            {
              "version": 1,
              "sites": [
                {"name":"A","urlTemplate":"https://a.com/{user}"},
                {"name":"A","urlTemplate":"https://b.com/{user}"}
              ]
            }
            """.trimIndent(),
        )
        assertTrue(CatalogLimits.validateParsed(parsed)!!.contains("дубликат"))
    }

    @Test
    fun rejectsHttpTemplate() {
        val parsed = OsintCatalogParser.parseFull(
            """
            {
              "version": 1,
              "sites": [
                {"name":"A","urlTemplate":"http://a.com/{user}"}
              ]
            }
            """.trimIndent(),
        )
        assertTrue(CatalogLimits.validateParsed(parsed)!!.contains("HTTPS"))
    }

    @Test
    fun acceptsValidCatalog() {
        val parsed = OsintCatalogParser.parseFull(
            """
            {
              "version": 1,
              "sites": [
                {"name":"GitHub","urlTemplate":"https://github.com/{user}","rateLimitMs":100}
              ]
            }
            """.trimIndent(),
        )
        assertNull(CatalogLimits.validateParsed(parsed))
    }
}
