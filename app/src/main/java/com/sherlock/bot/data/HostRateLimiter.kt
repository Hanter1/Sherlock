package com.sherlock.bot.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/**
 * Limits concurrent in-flight requests per host and spaces them out,
 * so one slow/rate-limited site does not starve the global scan pool.
 */
class HostRateLimiter(
    private val maxConcurrentPerHost: Int = DEFAULT_MAX_PER_HOST,
    private val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val gates = ConcurrentHashMap<String, HostGate>()

    suspend fun <T> withHost(host: String, block: suspend () -> T): T {
        val key = host.trim().lowercase().ifBlank { "_" }
        val gate = gates.getOrPut(key) {
            HostGate(
                maxConcurrent = maxConcurrentPerHost.coerceAtLeast(1),
                minIntervalMs = minIntervalMs.coerceAtLeast(0L),
                clock = clock,
            )
        }
        return gate.withPermit(block)
    }

    private class HostGate(
        maxConcurrent: Int,
        private val minIntervalMs: Long,
        private val clock: () -> Long,
    ) {
        private val semaphore = Semaphore(maxConcurrent)
        private val pace = Mutex()
        private var lastStartMs = 0L

        suspend fun <T> withPermit(block: suspend () -> T): T =
            semaphore.withPermit {
                if (minIntervalMs > 0L) {
                    pace.withLock {
                        val now = clock()
                        val wait = lastStartMs + minIntervalMs - now
                        if (wait > 0L) delay(wait)
                        lastStartMs = clock()
                    }
                }
                block()
            }
    }

    companion object {
        const val DEFAULT_MAX_PER_HOST = 2
        const val DEFAULT_MIN_INTERVAL_MS = 120L
    }
}
