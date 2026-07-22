package com.sherlock.bot.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GravatarLookupTest {

    @Test
    fun md5MatchesLowercasedEmail() {
        // Standard MD5 of trimmed lowercase email (Gravatar algorithm)
        val hash = GravatarLookup.md5Hex("MyEmailAddress@example.com".trim().lowercase())
        assertEquals("0bc83cb571cd1c50ba6f3e8a78ef1346", hash)
    }

    @Test
    fun md5IsLowercaseHex32() {
        val hash = GravatarLookup.md5Hex("test@example.com")
        assertEquals(32, hash.length)
        assertEquals(hash, hash.lowercase())
    }
}
