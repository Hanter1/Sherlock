package com.sherlock.bot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHistoryCodecTest {

    @Test
    fun roundTripPreservesMessagesAndMode() {
        val snapshot = ChatSnapshot(
            messages = listOf(
                ChatMessage(
                    id = "1",
                    text = "hello \"world\"\nline",
                    fromBot = false,
                    timestamp = 42L,
                ),
                ChatMessage(
                    id = "2",
                    text = "Отчёт",
                    fromBot = true,
                    timestamp = 43L,
                    actions = listOf(BotAction("username", "Никнейм")),
                ),
            ),
            pendingMode = SearchMode.EMAIL,
        )

        val encoded = ChatHistoryCodec.encode(snapshot)
        val decoded = ChatHistoryCodec.decode(encoded)
        assertNotNull(decoded)
        assertEquals(SearchMode.EMAIL, decoded!!.pendingMode)
        assertEquals(2, decoded.messages.size)
        assertEquals("hello \"world\"\nline", decoded.messages[0].text)
        assertEquals("Никнейм", decoded.messages[1].actions.single().label)
        assertFalse(decoded.messages.any { it.isTyping })
    }

    @Test
    fun skipsTypingStatusMessages() {
        val snapshot = ChatSnapshot(
            messages = listOf(
                ChatMessage(id = "ok", text = "keep", fromBot = true),
                ChatMessage(
                    id = ChatHistoryCodec.STATUS_MESSAGE_ID,
                    text = "typing",
                    fromBot = true,
                    isTyping = true,
                ),
            ),
            pendingMode = SearchMode.NONE,
        )
        val decoded = ChatHistoryCodec.decode(ChatHistoryCodec.encode(snapshot))!!
        assertEquals(1, decoded.messages.size)
        assertEquals("ok", decoded.messages.single().id)
    }

    @Test
    fun invalidJsonReturnsNull() {
        assertTrue(ChatHistoryCodec.decode("{not json") == null)
    }

    @Test
    fun roundTripPreservesReportIdAndPayload() {
        val report = OsintResult.UsernameReport(
            username = "alice",
            found = listOf(SiteHit("GitHub", "https://github.com/alice", listOf("dev"))),
            notFound = listOf("Steam"),
            errors = emptyList(),
            elapsedMs = 42,
        )
        val snapshot = ChatSnapshot(
            messages = listOf(
                ChatMessage(
                    id = "msg-1",
                    text = "отчёт",
                    fromBot = true,
                    reportId = "rep-1",
                    actions = listOf(BotAction("rescan", "Повторить")),
                ),
            ),
            pendingMode = SearchMode.NONE,
            reports = mapOf("rep-1" to report),
        )
        val decoded = ChatHistoryCodec.decode(ChatHistoryCodec.encode(snapshot))!!
        assertEquals("rep-1", decoded.messages.single().reportId)
        assertEquals("alice", decoded.reports["rep-1"]!!.username)
        assertEquals("GitHub", decoded.reports["rep-1"]!!.found.single().site)
    }

    @Test
    fun dropsUnreferencedReports() {
        val report = OsintResult.UsernameReport(
            username = "x",
            found = emptyList(),
            notFound = emptyList(),
            errors = emptyList(),
            elapsedMs = 1,
        )
        val snapshot = ChatSnapshot(
            messages = listOf(ChatMessage(id = "1", text = "hi", fromBot = false)),
            pendingMode = SearchMode.NONE,
            reports = mapOf("orphan" to report),
        )
        val decoded = ChatHistoryCodec.decode(ChatHistoryCodec.encode(snapshot))!!
        assertTrue(decoded.reports.isEmpty())
    }
}
