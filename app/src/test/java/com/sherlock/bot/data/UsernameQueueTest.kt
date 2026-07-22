package com.sherlock.bot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsernameQueueTest {

    @Test
    fun parsesCommaAndSpaceSeparated() {
        val parsed = UsernameQueue.parse("durov, telegram  pavel")
        assertEquals(listOf("durov", "telegram", "pavel"), parsed.nicks)
        assertTrue(parsed.rejected.isEmpty())
        assertFalse(parsed.truncated)
    }

    @Test
    fun stripsAtAndDedupes() {
        val parsed = UsernameQueue.parse("@Alice alice ALICE")
        assertEquals(listOf("Alice"), parsed.nicks)
    }

    @Test
    fun rejectsInvalidAndCapsAtMax() {
        val raw = (1..7).joinToString(" ") { "user$it" } + " bad!"
        val parsed = UsernameQueue.parse(raw)
        assertEquals(UsernameQueue.MAX_NICKS, parsed.nicks.size)
        assertTrue(parsed.truncated)
        assertTrue(parsed.rejected.contains("bad!"))
    }
}
