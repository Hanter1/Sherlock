package com.sherlock.bot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheAgeTest {

    @Test
    fun formatsMinutesAndHours() {
        assertEquals("45 мин", CacheAge.formatDuration(45 * 60_000L))
        assertEquals("2 ч", CacheAge.formatDuration(2 * 3_600_000L))
        assertEquals("2 ч 15 мин", CacheAge.formatDuration(2 * 3_600_000L + 15 * 60_000L))
    }

    @Test
    fun cacheLineIncludesRemainingWhenTtlKnown() {
        val saved = 1_000_000L
        val now = saved + 3_600_000L
        val line = CacheAge.formatCacheLine(saved, ttlMs = 24 * 3_600_000L, nowMs = now)
        assertTrue(line!!.contains("возраст 1 ч"))
        assertTrue(line.contains("ещё ~"))
    }

    @Test
    fun cacheLineWithoutTtlIsAgeOnly() {
        val saved = 1_000_000L
        val now = saved + 120_000L
        assertEquals("возраст 2 мин", CacheAge.formatCacheLine(saved, ttlMs = null, nowMs = now))
    }

    @Test
    fun nullSavedAtYieldsNull() {
        assertNull(CacheAge.formatCacheLine(null, 1_000L))
    }
}
