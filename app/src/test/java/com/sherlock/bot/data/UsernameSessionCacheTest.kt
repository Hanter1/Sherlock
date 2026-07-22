package com.sherlock.bot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsernameSessionCacheTest {

    @Test
    fun storesAndReturnsByNormalizedKey() {
        val cache = UsernameSessionCache()
        val report = OsintResult.UsernameReport(
            username = "Durov",
            found = listOf(SiteHit("GitHub", "https://github.com/durov")),
            notFound = emptyList(),
            errors = emptyList(),
            elapsedMs = 120,
        )
        cache.put("Durov", report)
        val hit = cache.get("durov")
        assertEquals("Durov", hit!!.username)
        assertEquals(1, hit.found.size)
        assertFalse(hit.fromCache)
    }

    @Test
    fun ignoresCancelledReports() {
        val cache = UsernameSessionCache()
        cache.put(
            "x",
            OsintResult.UsernameReport(
                username = "x",
                found = emptyList(),
                notFound = emptyList(),
                errors = emptyList(),
                elapsedMs = 1,
                cancelled = true,
            ),
        )
        assertNull(cache.get("x"))
    }

    @Test
    fun clearEmptiesCache() {
        val cache = UsernameSessionCache()
        cache.put(
            "a",
            OsintResult.UsernameReport(
                username = "a",
                found = emptyList(),
                notFound = listOf("GitHub"),
                errors = emptyList(),
                elapsedMs = 1,
            ),
        )
        assertTrue(cache.size() == 1)
        cache.clear()
        assertEquals(0, cache.size())
        assertNull(cache.get("a"))
    }
}
