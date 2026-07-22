package com.sherlock.bot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanSiteFilterTest {

    private val sites = listOf(
        OsintSite("GitHub", "https://github.com/{user}", categories = listOf("dev"), curated = true),
        OsintSite("Telegram", "https://t.me/{user}", categories = listOf("social"), curated = true),
        OsintSite("VK", "https://vk.com/{user}", categories = listOf("social"), curated = true),
        OsintSite("Habr", "https://habr.com/{user}", categories = listOf("dev"), curated = true),
        OsintSite("OK.ru", "https://ok.ru/{user}", categories = listOf("social"), curated = true),
        OsintSite("Twitch", "https://twitch.tv/{user}", categories = listOf("media", "gaming")),
        OsintSite("Instagram", "https://instagram.com/{user}", categories = listOf("social")),
        OsintSite("X", "https://x.com/{user}", categories = listOf("social")),
        OsintSite("Behance", "https://behance.net/{user}", categories = listOf("design")),
        OsintSite("NSFW Site", "https://nsfw.example/{user}", categories = listOf("nsfw"), nsfw = true),
    )

    @Test
    fun allIncludesCatalogExceptNsfwByDefault() {
        val filtered = ScanSiteFilter.filter(sites, includeBotProtected = true, preset = ScanPreset.ALL)
        assertEquals(sites.size - 1, filtered.size)
        assertTrue(filtered.none { it.nsfw })
    }

    @Test
    fun quickUsesCurated() {
        val filtered = ScanSiteFilter.filter(sites, includeBotProtected = true, preset = ScanPreset.QUICK)
        assertEquals(setOf("GitHub", "Telegram", "VK", "Habr", "OK.ru"), filtered.map { it.name }.toSet())
    }

    @Test
    fun nsfwIncludedWhenEnabled() {
        val filtered = ScanSiteFilter.filter(
            sites,
            includeBotProtected = true,
            preset = ScanPreset.ALL,
            includeNsfw = true,
        )
        assertTrue(filtered.any { it.name == "NSFW Site" })
        assertEquals(sites.size, filtered.size)
    }

    @Test
    fun socialOnlyHasSocialOrCreator() {
        val filtered = ScanSiteFilter.filter(sites, includeBotProtected = true, preset = ScanPreset.SOCIAL)
        assertEquals(setOf("Telegram", "VK", "OK.ru", "Instagram", "X"), filtered.map { it.name }.toSet())
    }

    @Test
    fun byFocusIsRegionalSet() {
        val filtered = ScanSiteFilter.filter(sites, includeBotProtected = true, preset = ScanPreset.BY)
        assertEquals(setOf("VK", "OK.ru", "Telegram", "Habr"), filtered.map { it.name }.toSet())
    }

    @Test
    fun mediaIncludesDesignAndGaming() {
        val filtered = ScanSiteFilter.filter(sites, includeBotProtected = true, preset = ScanPreset.MEDIA)
        assertTrue(filtered.any { it.name == "Twitch" })
        assertTrue(filtered.any { it.name == "Behance" })
    }

    @Test
    fun botProtectedCanBeExcluded() {
        val filtered = ScanSiteFilter.filter(sites, includeBotProtected = false, preset = ScanPreset.ALL)
        assertTrue(filtered.none { it.name in ScanSiteFilter.BOT_PROTECTED_NAMES })
        assertEquals(
            listOf("GitHub", "Telegram", "VK", "Habr", "OK.ru", "Twitch", "Behance"),
            filtered.map { it.name },
        )
    }

    @Test
    fun keepsBotProtectedWhenEnabled() {
        val filtered = ScanSiteFilter.filter(sites, includeBotProtected = true, preset = ScanPreset.ALL)
        assertTrue(filtered.any { it.name == "Instagram" })
        assertTrue(filtered.any { it.name == "X" })
    }
}
