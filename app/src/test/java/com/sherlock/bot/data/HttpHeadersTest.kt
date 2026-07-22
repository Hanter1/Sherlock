package com.sherlock.bot.data

import org.junit.Assert.assertTrue
import org.junit.Test

class HttpHeadersTest {

    @Test
    fun browserLikeUserAgent() {
        assertTrue(HttpHeaders.USER_AGENT.contains("Mozilla/5.0"))
        assertTrue(HttpHeaders.USER_AGENT.contains("Chrome"))
        assertTrue(HttpHeaders.ACCEPT_LANGUAGE.contains("be"))
        assertTrue(HttpHeaders.ACCEPT_LANGUAGE.contains("ru"))
        assertTrue(HttpHeaders.ACCEPT_HTML.contains("text/html"))
    }
}
