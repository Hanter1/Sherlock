package com.sherlock.bot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NameUsernameCandidatesTest {

    @Test
    fun transliteratesRussian() {
        assertEquals("ivanov", NameUsernameCandidates.transliterate("Иванов"))
        assertEquals("ivan", NameUsernameCandidates.transliterate("Иван"))
        assertEquals("aleksey", NameUsernameCandidates.transliterate("Алексей"))
    }

    @Test
    fun transliteratesBelarusian() {
        assertEquals("i", NameUsernameCandidates.transliterate("і"))
        assertTrue(NameUsernameCandidates.transliterate("Кавалёў").startsWith("kaval"))
    }

    @Test
    fun buildsNickCandidatesForFio() {
        val parsed = NameUsernameCandidates.parse("Иванов Иван")!!
        val nicks = NameUsernameCandidates.candidates(parsed)
        assertTrue(nicks.contains("ivanov"))
        assertTrue(nicks.any { it.contains("ivan") && it.contains("ivanov") || it == "ivanivanov" || it == "ivan_ivanov" })
        assertTrue(nicks.size in 1..NameUsernameCandidates.MAX_CANDIDATES)
        assertTrue(nicks.all { it.matches(Regex("^[a-z0-9._-]{2,39}$")) })
    }

    @Test
    fun rejectsSingleToken() {
        assertEquals(null, NameUsernameCandidates.parse("Иванов"))
        assertTrue(NameUsernameCandidates.fromRaw("Иванов").isEmpty())
    }

    @Test
    fun latinNamePassthrough() {
        val nicks = NameUsernameCandidates.fromRaw("Smith John")
        assertFalse(nicks.isEmpty())
        assertTrue(nicks.contains("smith") || nicks.contains("johnsmith") || nicks.contains("john_smith"))
    }
}
