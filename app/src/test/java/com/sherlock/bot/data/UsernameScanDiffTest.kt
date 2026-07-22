package com.sherlock.bot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsernameScanDiffTest {

    @Test
    fun detectsAppearedAndDisappeared() {
        val prev = OsintResult.UsernameReport(
            username = "a",
            found = listOf(
                SiteHit("GitHub", "https://github.com/a"),
                SiteHit("Telegram", "https://t.me/a"),
            ),
            notFound = emptyList(),
            errors = emptyList(),
            elapsedMs = 1,
        )
        val curr = OsintResult.UsernameReport(
            username = "a",
            found = listOf(
                SiteHit("GitHub", "https://github.com/a"),
                SiteHit("VK", "https://vk.com/a"),
            ),
            notFound = emptyList(),
            errors = emptyList(),
            elapsedMs = 2,
        )
        val d = UsernameScanDiff.diff(prev, curr)
        assertEquals(listOf("VK"), d.appeared)
        assertEquals(listOf("Telegram"), d.disappeared)
        assertEquals(listOf("GitHub"), d.stillFound)
        val text = UsernameScanDiff.format(prev, curr)!!
        assertTrue(text.contains("появились"))
        assertTrue(text.contains("пропали"))
    }
}

class BelPhoneCodesTest {
    @Test
    fun knownPrefixes() {
        assertTrue(BelPhoneCodes.lookup("29").contains("A1") || BelPhoneCodes.lookup("29").contains("МТС"))
        assertTrue(BelPhoneCodes.lookup("99").contains("не в справочнике"))
    }
}
