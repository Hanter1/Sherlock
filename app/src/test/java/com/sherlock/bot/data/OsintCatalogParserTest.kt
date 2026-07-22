package com.sherlock.bot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OsintCatalogParserTest {

    @Test
    fun parsesFullCatalogAssetShape() {
        val json = """
            {
              "version": 1,
              "sites": [
                {
                  "name": "GitHub",
                  "urlTemplate": "https://github.com/{user}",
                  "errorCodes": [404],
                  "useHead": true
                },
                {
                  "name": "Telegram",
                  "urlTemplate": "https://t.me/{user}",
                  "okCodes": [200],
                  "errorCodes": [],
                  "errorBodyMarkers": ["tgme_icon_user"],
                  "okBodyMarkers": ["tgme_page_title"],
                  "useHead": false
                }
              ]
            }
        """.trimIndent()

        val sites = OsintCatalogParser.parse(json)
        assertEquals(2, sites.size)
        assertEquals("GitHub", sites[0].name)
        assertTrue(sites[0].useHead)
        assertEquals(setOf(404), sites[0].errorCodes)
        assertEquals(emptySet<Int>(), sites[1].errorCodes)
        assertEquals(listOf("tgme_page_title"), sites[1].okBodyMarkers)
        assertFalse(sites[1].useHead)
    }

    @Test
    fun parsesBlockBodyMarkers() {
        val sites = OsintCatalogParser.parse(
            """
            {
              "version": 2,
              "sites": [
                {
                  "name": "TikTok",
                  "urlTemplate": "https://www.tiktok.com/@{user}",
                  "errorCodes": [],
                  "blockBodyMarkers": ["_wafchallenge", "SlardarWAF"],
                  "okBodyMarkers": ["uniqueId"],
                  "useHead": false
                }
              ]
            }
            """.trimIndent(),
        )
        assertEquals(listOf("_wafchallenge", "SlardarWAF"), sites.single().blockBodyMarkers)
        assertEquals(emptySet<Int>(), sites.single().errorCodes)
    }

    @Test
    fun parsesCommittedAssetFile() {
        val file = java.io.File("src/main/assets/osint_sites.json")
        assertTrue("asset missing at ${file.absolutePath}", file.exists())
        val sites = OsintCatalogParser.parse(file.readText())
        assertTrue(sites.size >= 25)
        assertTrue(sites.any { it.name == "Twitch" && it.okBodyMarkers.contains("isLiveBroadcast") })
        assertTrue(sites.any { it.name == "VK" && it.okBodyMarkers.contains("owner_id") })
        assertTrue(sites.any { it.name == "TikTok" && it.blockBodyMarkers.contains("SlardarWAF") })
        assertTrue(sites.any { it.name == "Bluesky" })
        assertTrue(sites.any { it.name == "YouTube" })
        assertTrue(sites.any { it.name == "Instagram" && it.blockBodyMarkers.isNotEmpty() })
        assertTrue(sites.any { it.name == "X" && it.blockBodyMarkers.isNotEmpty() })
        assertTrue(sites.any { it.name == "GitHub" && it.categories.contains("dev") })
        assertTrue(sites.any { it.name == "Telegram" && it.categories.contains("social") })
        assertTrue(sites.any { it.name == "OK.ru" })
        assertTrue(sites.any { it.name == "TikTok" && it.rateLimitMs > 0 })
        val full = OsintCatalogParser.parseFull(file.readText())
        assertEquals(6, full.version)
        assertNull(CatalogLimits.validateParsed(full))
        assertTrue(sites.any { it.name == "GitHub" && it.trustHttpStatus })
        assertTrue(sites.any { it.name == "GitLab" && it.okBodyMarkers.isNotEmpty() })
    }

    @Test
    fun parsesRateLimitMs() {
        val sites = OsintCatalogParser.parse(
            """
            {
              "version": 5,
              "sites": [
                {
                  "name": "X",
                  "urlTemplate": "https://x.com/{user}",
                  "errorCodes": [],
                  "rateLimitMs": 1000,
                  "useHead": false
                }
              ]
            }
            """.trimIndent(),
        )
        assertEquals(1000L, sites.single().rateLimitMs)
    }

    @Test
    fun parsesCategories() {
        val sites = OsintCatalogParser.parse(
            """
            {
              "version": 4,
              "sites": [
                {
                  "name": "GitHub",
                  "urlTemplate": "https://github.com/{user}",
                  "errorCodes": [404],
                  "categories": ["dev"],
                  "useHead": true
                }
              ]
            }
            """.trimIndent(),
        )
        assertEquals(listOf("dev"), sites.single().categories)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMissingUserPlaceholder() {
        OsintCatalogParser.parse(
            """
            {"version":1,"sites":[{"name":"X","urlTemplate":"https://x.com/nope"}]}
            """.trimIndent(),
        )
    }
}
