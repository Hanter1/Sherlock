package com.sherlock.bot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ChatSearchTest {

    private val messages = listOf(
        ChatMessage("1", "привет", fromBot = false),
        ChatMessage("2", "—— Сводка ——\nНик `alice`", fromBot = true),
        ChatMessage("3", "Email · открытые данные\nMX ok", fromBot = true),
    )

    @Test
    fun emptyQueryReturnsAll() {
        assertEquals(3, ChatSearch.filter(messages, "").size)
        assertEquals(3, ChatSearch.filter(messages, "   ").size)
    }

    @Test
    fun filtersCaseInsensitive() {
        val hit = ChatSearch.filter(messages, "ALICE")
        assertEquals(1, hit.size)
        assertEquals("2", hit.single().id)
    }

    @Test
    fun previewTruncates() {
        val long = "a".repeat(120)
        val preview = ChatSearch.preview(long, maxChars = 20)
        assertTrue(preview.endsWith("…"))
        assertTrue(preview.length <= 20)
    }

    @Test
    fun detectsReportLike() {
        assertTrue(ChatSearch.isReportLike("—— Сводка ——"))
        assertTrue(ChatSearch.isReportLike("—— Сравнение ников ——"))
        assertTrue(ChatSearch.isReportLike("Email · открытые данные\nbody"))
        assertFalse(ChatSearch.isReportLike("привет"))
    }

    @Test
    fun journalWorthyKeepsQueriesAndReports() {
        val welcome = ChatMessage("w", "Sherlock Bot — OSINT", fromBot = true)
        val query = ChatMessage("q", "durov", fromBot = false)
        val report = ChatMessage(
            "r",
            "—— Сводка ——\nНик `durov`\nНайдено: *14* · нет: 20 · ошибки: 0 · всего: 34",
            fromBot = true,
        )
        assertFalse(ChatSearch.isJournalWorthy(welcome))
        assertTrue(ChatSearch.isJournalWorthy(query))
        assertTrue(ChatSearch.isJournalWorthy(report))
    }

    @Test
    fun journalTitleForUsernameReport() {
        val report = ChatMessage(
            "r",
            "—— Сводка ——\nНик `durov`\nНайдено: *14* · нет: 20 · ошибки: 0 · всего: 34",
            fromBot = true,
            timestamp = 0L,
        )
        assertEquals("durov · 14 найдено", ChatSearch.journalTitle(report))
    }

    @Test
    fun journalTitleForUserQuery() {
        val query = ChatMessage("q", "/username alice", fromBot = false)
        assertEquals("username alice", ChatSearch.journalTitle(query))
    }
}

class CatalogProbeConfigTest {

    @Test
    fun probeSitesExistInCatalog() {
        val catalog = File("src/main/assets/osint_sites.json")
        assertTrue(catalog.exists())
        val names = OsintCatalogParser.parse(catalog.readText()).map { it.name }.toSet()
        listOf("GitHub", "Bitbucket", "Codeberg").forEach { name ->
            assertTrue("$name missing from catalog", name in names)
        }
    }

    @Test
    fun probeScriptExists() {
        val script = File("../scripts/probe_catalog.py")
        assertTrue("probe script missing at ${script.absolutePath}", script.exists())
        val text = script.readText()
        assertTrue(text.contains("GitHub"))
        assertTrue(text.contains("torvalds"))
    }
}
