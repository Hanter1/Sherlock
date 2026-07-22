package com.sherlock.bot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MxLookupFallbackTest {

    @Test
    fun parseKeepsProviderWhenCopied() {
        val json = """
            {
              "Status": 0,
              "Answer": [
                {"name":"example.com","type":15,"TTL":300,"data":"10 mail.example.com."}
              ]
            }
        """.trimIndent()
        val parsed = MxLookup.parseDnsJson(json)
        assertTrue(parsed is MxLookup.Result.Ok)
        val withProvider = (parsed as MxLookup.Result.Ok).copy(provider = "Google")
        assertEquals("Google", withProvider.provider)
        assertEquals("mail.example.com", withProvider.records.single().host)
    }

    @Test
    fun defaultEndpointsIncludeCloudflareThenGoogle() {
        assertEquals("Cloudflare", MxLookup.DEFAULT_ENDPOINTS[0].name)
        assertEquals("Google", MxLookup.DEFAULT_ENDPOINTS[1].name)
        assertTrue(MxLookup.DEFAULT_ENDPOINTS[0].baseUrl.contains("cloudflare"))
        assertTrue(MxLookup.DEFAULT_ENDPOINTS[1].baseUrl.contains("dns.google"))
    }
}
