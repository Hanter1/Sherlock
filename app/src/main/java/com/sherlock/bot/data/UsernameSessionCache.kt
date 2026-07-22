package com.sherlock.bot.data

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory username report cache for the current app process/session.
 */
class UsernameSessionCache(
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    data class Entry(
        val savedAtMs: Long,
        val report: OsintResult.UsernameReport,
    )

    private val map = ConcurrentHashMap<String, Entry>()

    fun get(username: String): OsintResult.UsernameReport? =
        getEntry(username)?.report

    fun getEntry(username: String): Entry? =
        map[username.lowercase()]

    fun put(username: String, report: OsintResult.UsernameReport) {
        if (report.cancelled) return
        map[username.lowercase()] = Entry(
            savedAtMs = clock(),
            report = report.copy(
                fromCache = false,
                cacheSavedAtMs = null,
                cacheTtlMs = null,
            ),
        )
    }

    fun remove(username: String) {
        map.remove(username.lowercase())
    }

    fun clear() {
        map.clear()
    }

    fun size(): Int = map.size

    fun keys(): Set<String> = map.keys.toSet()

    fun oldestSavedAtMs(): Long? = map.values.minOfOrNull { it.savedAtMs }
}
