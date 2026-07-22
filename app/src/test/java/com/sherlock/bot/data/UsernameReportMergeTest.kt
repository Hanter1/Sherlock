package com.sherlock.bot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsernameReportMergeTest {

    private val previous = OsintResult.UsernameReport(
        username = "alice",
        found = listOf(SiteHit("GitHub", "https://github.com/alice", listOf("dev"))),
        uncertain = listOf(SiteHit("npm", "https://www.npmjs.com/~alice")),
        notFound = listOf("Steam"),
        errors = listOf("Instagram: blocked / challenge", "X: blocked / challenge"),
        elapsedMs = 1000,
    )

    @Test
    fun failedSiteNamesIncludeErrorsAndUncertain() {
        assertEquals(
            setOf("Instagram", "X", "npm"),
            UsernameReportMerge.failedSiteNames(previous),
        )
    }

    @Test
    fun mergeReplacesOnlyRecheckedSites() {
        val partial = OsintResult.UsernameReport(
            username = "alice",
            found = listOf(SiteHit("Instagram", "https://instagram.com/alice")),
            uncertain = emptyList(),
            notFound = listOf("npm"),
            errors = listOf("X: still blocked"),
            elapsedMs = 200,
        )
        val merged = UsernameReportMerge.merge(
            previous,
            partial,
            setOf("Instagram", "X", "npm"),
        )
        assertEquals(listOf("GitHub", "Instagram"), merged.found.map { it.site })
        assertTrue(merged.uncertain.isEmpty())
        assertEquals(listOf("Steam", "npm"), merged.notFound)
        assertEquals(listOf("X: still blocked"), merged.errors)
        assertEquals(1200L, merged.elapsedMs)
    }
}
