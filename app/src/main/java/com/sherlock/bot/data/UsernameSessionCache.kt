package com.sherlock.bot.data

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory username report cache for the current app process/session.
 */
class UsernameSessionCache {
    private val map = ConcurrentHashMap<String, OsintResult.UsernameReport>()

    fun get(username: String): OsintResult.UsernameReport? =
        map[username.lowercase()]

    fun put(username: String, report: OsintResult.UsernameReport) {
        if (report.cancelled) return
        map[username.lowercase()] = report.copy(fromCache = false)
    }

    fun remove(username: String) {
        map.remove(username.lowercase())
    }

    fun clear() {
        map.clear()
    }

    fun size(): Int = map.size
}
