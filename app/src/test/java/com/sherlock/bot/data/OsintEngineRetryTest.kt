package com.sherlock.bot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OsintEngineRetryTest {

    private val engine = OsintEngine()

    @Test
    fun retryableStatusCodes() {
        assertTrue(engine.shouldRetry(429))
        assertTrue(engine.shouldRetry(503))
        assertTrue(engine.shouldRetry(500))
        assertFalse(engine.shouldRetry(404))
        assertFalse(engine.shouldRetry(200))
        assertFalse(engine.shouldRetry(403))
    }

    @Test
    fun backoffDoublesUntilCap() {
        assertEquals(800L, OsintEngine.nextDelay(400L))
        assertEquals(1600L, OsintEngine.nextDelay(800L))
        assertEquals(3200L, OsintEngine.nextDelay(1600L))
        assertEquals(3500L, OsintEngine.nextDelay(3200L))
        assertEquals(3500L, OsintEngine.nextDelay(3500L))
    }

    @Test
    fun parseRetryAfterSeconds() {
        assertEquals(2000L, OsintEngine.parseRetryAfterMs("2"))
        assertEquals(null, OsintEngine.parseRetryAfterMs("Wed, 01 Jan 2020"))
        assertEquals(null, OsintEngine.parseRetryAfterMs(null))
    }

    @Test
    fun resolveRetryPrefersRetryAfter() {
        assertEquals(2100L, OsintEngine.resolveRetryDelayMs(400L, retryAfterMs = 2000L, jitterMs = 100L))
        assertEquals(500L, OsintEngine.resolveRetryDelayMs(400L, retryAfterMs = null, jitterMs = 100L))
    }

    @Test
    fun jitterWithinQuarter() {
        val jitter = OsintEngine.jitterMs(400L) { 50 }
        assertEquals(50L, jitter)
        assertEquals(0L, OsintEngine.jitterMs(0L) { error("no") })
    }
}
