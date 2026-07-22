package com.sherlock.bot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailAnalyzerTest {

    @Test
    fun parsesValidEmail() {
        val parsed = EmailAnalyzer.parse("Name.User+tag@Example.COM")
        assertNotNull(parsed)
        assertEquals("name.user+tag@example.com", parsed!!.email)
        assertEquals("name.user+tag", parsed.local)
        assertEquals("example.com", parsed.domain)
    }

    @Test
    fun rejectsInvalid() {
        assertNull(EmailAnalyzer.parse("not-an-email"))
        assertNull(EmailAnalyzer.parse("@nodomain.com"))
    }

    @Test
    fun formatIncludesMxAndGravatar() {
        val parsed = EmailAnalyzer.parse("a@example.com")!!
        val mx = MxLookup.Result.Ok(
            records = listOf(
                MxLookup.MxRecord(10, "mail.example.com"),
                MxLookup.MxRecord(20, "alt.example.com"),
            ),
            provider = "Cloudflare",
        )
        val gravatar = GravatarLookup.Result.Found(
            hash = "abc",
            avatarUrl = "https://www.gravatar.com/avatar/abc?s=128",
            profileUrl = "https://www.gravatar.com/abc",
        )
        val policy = MxLookup.MailPolicy(
            spf = MxLookup.SpfInfo("v=spf1 include:_spf.google.com ~all"),
            dmarc = MxLookup.DmarcInfo("v=DMARC1; p=none;", policy = "none"),
            provider = "Cloudflare",
        )
        val report = EmailAnalyzer.formatReport(parsed, mx, gravatar, policy)
        assertTrue(report.body.contains("[10] mail.example.com"))
        assertTrue(report.body.contains("источник: Cloudflare"))
        assertTrue(report.body.contains("профиль найден"))
        assertTrue(report.body.contains("https://www.gravatar.com/abc"))
        assertTrue(report.body.contains("SPF: есть"))
        assertTrue(report.body.contains("DMARC: есть"))
        assertTrue(report.body.contains("p=none"))
    }

    @Test
    fun formatMissingGravatar() {
        val parsed = EmailAnalyzer.parse("a@example.com")!!
        val report = EmailAnalyzer.formatReport(
            parsed,
            MxLookup.Result.Ok(emptyList()),
            GravatarLookup.Result.Missing("deadbeefcafebabe"),
        )
        assertTrue(report.body.contains("публичного аватара нет"))
    }

    @Test
    fun formatRespectsDisabledLookups() {
        val parsed = EmailAnalyzer.parse("a@example.com")!!
        val report = EmailAnalyzer.formatReport(
            parsed = parsed,
            mx = null,
            gravatar = null,
            mxEnabled = false,
            gravatarEnabled = false,
        )
        assertTrue(report.body.contains("MX / SPF / DMARC: выключено"))
        assertTrue(report.body.contains("Gravatar: выключено"))
    }
}
