package com.sherlock.bot.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class HostRateLimiterTest {

    @Test
    fun spacesRequestsToSameHost() = runBlocking {
        var now = 1_000L
        val limiter = HostRateLimiter(
            maxConcurrentPerHost = 1,
            minIntervalMs = 50L,
            clock = { now },
        )
        val starts = mutableListOf<Long>()
        val jobs = (1..3).map {
            async {
                limiter.withHost("example.com") {
                    starts += now
                    now += 10L
                }
            }
        }
        // Advance clock while waits happen inside limiter
        repeat(20) {
            delay(5)
            now += 25L
            if (starts.size >= 3) return@repeat
        }
        jobs.awaitAll()
        assertTrue(starts.size == 3)
        assertTrue(starts[1] - starts[0] >= 50L || starts[2] - starts[0] >= 50L)
    }

    @Test
    fun allowsParallelAcrossHosts() = runBlocking {
        val limiter = HostRateLimiter(maxConcurrentPerHost = 1, minIntervalMs = 0L)
        val concurrent = AtomicInteger(0)
        val maxSeen = AtomicInteger(0)
        val jobs = listOf("a.com", "b.com", "c.com").map { host ->
            async {
                limiter.withHost(host) {
                    val n = concurrent.incrementAndGet()
                    maxSeen.updateAndGet { cur -> maxOf(cur, n) }
                    delay(40)
                    concurrent.decrementAndGet()
                }
            }
        }
        jobs.awaitAll()
        assertTrue(maxSeen.get() >= 2)
    }
}
