package com.sherlock.bot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalCasesTest {

    @Test
    fun pairsQueryWithFollowingReport() {
        val query = ChatMessage("q1", "/username durov", fromBot = false, timestamp = 1_000L)
        val report = ChatMessage(
            "r1",
            "—— Сводка ——\nНик `durov`\nНайдено: *2* · нет: 1 · ошибки: 0 · всего: 3",
            fromBot = true,
            timestamp = 2_000L,
            reportId = "rep1",
        )
        val cases = JournalCases.build(listOf(query, report))
        assertEquals(1, cases.size)
        assertEquals(JournalKind.USERNAME, cases.single().kind)
        assertEquals(listOf("q1", "r1"), cases.single().messageIds)
        assertEquals("durov · 2 найдено", cases.single().title)
    }

    @Test
    fun groupsTodayAndOlder() {
        val now = 1_720_000_000_000L
        val today = JournalCase(
            id = "a",
            kind = JournalKind.EMAIL,
            title = "a",
            meta = "m",
            timestamp = now,
            messageIds = listOf("a"),
            selectMessageId = "a",
        )
        val older = today.copy(id = "b", timestamp = now - 3 * 86_400_000L, selectMessageId = "b", messageIds = listOf("b"))
        val groups = JournalCases.groupByDay(listOf(today, older), nowMs = now)
        assertEquals("Сегодня", groups.first().first)
        assertTrue(groups.size >= 2)
    }
}
