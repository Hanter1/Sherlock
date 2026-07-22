package com.sherlock.bot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsernameCompareTest {

    @Test
    fun diffsSharedAndExclusive() {
        val a = OsintResult.UsernameReport(
            username = "alice",
            found = listOf(
                SiteHit("GitHub", "https://github.com/alice", listOf("dev")),
                SiteHit("Telegram", "https://t.me/alice", listOf("social")),
            ),
            notFound = listOf("Steam"),
            errors = emptyList(),
            elapsedMs = 100,
        )
        val b = OsintResult.UsernameReport(
            username = "bob",
            found = listOf(
                SiteHit("GitHub", "https://github.com/bob", listOf("dev")),
                SiteHit("VK", "https://vk.com/bob", listOf("social")),
            ),
            notFound = emptyList(),
            errors = emptyList(),
            elapsedMs = 200,
        )
        val diff = UsernameCompare.diff(a, b)
        assertEquals(1, diff.both.size)
        assertEquals("GitHub", diff.both.single().site)
        assertEquals("https://github.com/alice", diff.both.single().urlA)
        assertEquals("https://github.com/bob", diff.both.single().urlB)
        assertEquals(listOf("Telegram"), diff.onlyA.map { it.site })
        assertEquals(listOf("VK"), diff.onlyB.map { it.site })

        val text = UsernameCompare.formatDiff(diff)
        assertTrue(text.contains("Общих: *1*"))
        assertTrue(text.contains("Только `alice`"))
        assertTrue(text.contains("Только `bob`"))
        assertTrue(text.contains("0.3 с") || text.contains("0,3 с") || text.contains("300 мс"))
    }
}
