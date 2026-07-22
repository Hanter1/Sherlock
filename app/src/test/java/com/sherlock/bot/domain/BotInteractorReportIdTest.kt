package com.sherlock.bot.domain

import com.sherlock.bot.data.OsintResult
import com.sherlock.bot.data.SiteHit
import com.sherlock.bot.data.UsernameReportFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BotInteractorReportIdTest {

    private val alice = OsintResult.UsernameReport(
        username = "alice",
        found = listOf(SiteHit("GitHub", "https://github.com/alice", listOf("dev"))),
        notFound = listOf("Steam"),
        errors = emptyList(),
        elapsedMs = 100,
    )
    private val bob = OsintResult.UsernameReport(
        username = "bob",
        found = listOf(SiteHit("GitHub", "https://github.com/bob", listOf("dev"))),
        notFound = emptyList(),
        errors = listOf("X: blocked"),
        elapsedMs = 200,
    )

    @Test
    fun filterUsesSpecificReportNotLast() {
        val bot = BotInteractor()
        val aliceMsg = bot.formatUsernameReport(alice)
        val bobMsg = bot.formatUsernameReport(bob)
        assertNotNull(aliceMsg.reportId)
        assertNotNull(bobMsg.reportId)
        assertNotEquals(aliceMsg.reportId, bobMsg.reportId)

        val filtered = bot.formatUsernameReport(
            result = bot.reportFor(aliceMsg.reportId)!!,
            filter = UsernameReportFilter.FOUND_ONLY,
            reportId = aliceMsg.reportId!!,
        )
        assertEquals(aliceMsg.reportId, filtered.reportId)
        assertTrue(filtered.text.contains("alice"))
        assertTrue(filtered.text.contains("Фильтр: только найденные"))
        assertEquals("alice", bot.reportFor(filtered.reportId)!!.username)
        // last scan is still bob, but alice report stays addressable
        assertEquals("bob", bot.lastUsernameReport!!.username)
    }

    @Test
    fun replaceReportsRestoresLookup() {
        val bot = BotInteractor()
        val msg = bot.formatUsernameReport(alice)
        val id = msg.reportId!!
        bot.clearReports()
        assertEquals(null, bot.reportFor(id))
        bot.replaceReports(mapOf(id to alice))
        assertEquals("alice", bot.reportFor(id)!!.username)
    }
}
