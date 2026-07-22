package com.sherlock.bot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanSiteFilterTest {

    private val sites = listOf(
        OsintSite("GitHub", "https://github.com/{user}", categories = listOf("dev")),
        OsintSite("Telegram", "https://t.me/{user}", categories = listOf("social")),
        OsintSite("VK", "https://vk.com/{user}", categories = listOf("social")),
        OsintSite("Habr", "https://habr.com/{user}", categories = listOf("dev")),
        OsintSite("OK.ru", "https://ok.ru/{user}", categories = listOf("social")),
        OsintSite("Twitch", "https://twitch.tv/{user}", categories = listOf("media", "gaming")),
        OsintSite("Instagram", "https://instagram.com/{user}", categories = listOf("social")),
        OsintSite("X", "https://x.com/{user}", categories = listOf("social")),
        OsintSite("Behance", "https://behance.net/{user}", categories = listOf("design")),
    )

    @Test
    fun allIncludesCatalog() {
        val filtered = ScanSiteFilter.filter(sites, includeBotProtected = true, preset = ScanPreset.ALL)
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
    }
}
