package com.sherlock.bot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSignatureTest {

    @Test
    fun signAndVerifyRoundTrip() {
        val (pub, priv) = CatalogSignature.generateKeyPairB64()
        val sites = listOf(
            OsintSite("GitHub", "https://github.com/{user}", categories = listOf("dev"), trustHttpStatus = true),
        )
        val payload = CatalogSignature.payloadBytes(7, "2026-07", sites)
        val signature = CatalogSignature.sign(payload, priv)
        assertTrue(CatalogSignature.verify(payload, signature, pub))
    }

    @Test
    fun rejectsTamperedPayload() {
        val (pub, priv) = CatalogSignature.generateKeyPairB64()
        val sites = listOf(OsintSite("A", "https://a.test/{user}"))
        val payload = CatalogSignature.payloadBytes(1, "x", sites)
        val signature = CatalogSignature.sign(payload, priv)
        val tampered = CatalogSignature.payloadBytes(2, "x", sites)
        assertFalse(CatalogSignature.verify(tampered, signature, pub))
    }

    @Test
    fun sitesDigestIsStable() {
        val a = listOf(
            OsintSite("B", "https://b/{user}", okCodes = setOf(200, 201)),
            OsintSite("A", "https://a/{user}", okCodes = setOf(201, 200)),
        )
        val b = listOf(
            OsintSite("B", "https://b/{user}", okCodes = setOf(201, 200)),
            OsintSite("A", "https://a/{user}", okCodes = setOf(200, 201)),
        )
        assertEquals(CatalogSignature.sitesDigest(a), CatalogSignature.sitesDigest(b))
    }
}
