package com.sherlock.bot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UsernameDiskCacheTest {

    @Test
    fun persistsAndReturnsWithinTtl() {
        val file = File.createTempFile("uname-cache", ".json")
        file.deleteOnExit()
        var now = 1_000_000L
        val cache = UsernameDiskCache(
            file = file,
            ttlMs = 60_000L,
            clock = { now },
        )
        val report = OsintResult.UsernameReport(
            username = "Durov",
            found = listOf(SiteHit("GitHub", "https://github.com/durov", listOf("dev"))),
            notFound = listOf("X"),
            errors = listOf("TikTok: blocked"),
            elapsedMs = 900,
        )
        cache.put("Durov", report)

        val hit = cache.get("durov")
        assertEquals("Durov", hit!!.username)
        assertEquals(listOf("dev"), hit.found.single().categories)
        assertEquals(1, hit.notFound.size)
        assertFalse(hit.fromCache)

        // New instance reads same file
        val cache2 = UsernameDiskCache(file = file, ttlMs = 60_000L, clock = { now })
        assertEquals(1, cache2.get("DUROV")!!.found.size)
    }

    @Test
    fun expiresAfterTtl() {
        val file = File.createTempFile("uname-cache-ttl", ".json")
        file.deleteOnExit()
        var now = 1_000_000L
        val cache = UsernameDiskCache(file = file, ttlMs = 1_000L, clock = { now })
        cache.put(
            "a",
            OsintResult.UsernameReport(
                username = "a",
                found = emptyList(),
                notFound = listOf("GitHub"),
                errors = emptyList(),
                elapsedMs = 10,
            ),
        )
        assertTrue(cache.get("a") != null)
        now += 2_000L
        assertNull(cache.get("a"))
    }

    @Test
    fun getEntryExposesSavedAt() {
        val file = File.createTempFile("uname-cache-age", ".json")
        file.deleteOnExit()
        var now = 5_000_000L
        val cache = UsernameDiskCache(file = file, ttlMs = 60_000L, clock = { now })
        cache.put(
            "bob",
            OsintResult.UsernameReport(
                username = "bob",
                found = emptyList(),
                notFound = listOf("GitHub"),
                errors = emptyList(),
                elapsedMs = 10,
            ),
        )
        val entry = cache.getEntry("bob")
        assertEquals(5_000_000L, entry!!.savedAtMs)
        assertEquals("bob", entry.report.username)
    }
}

