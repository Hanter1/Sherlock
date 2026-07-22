package com.sherlock.bot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MxTxtLookupTest {

    @Test
    fun parsesTxtRecords() {
        val json = """
            {
              "Status": 0,
              "Answer": [
                {"name":"example.com","type":16,"TTL":300,"data":"\"v=spf1 include:_spf.google.com ~all\""},
                {"name":"example.com","type":16,"TTL":300,"data":"\"google-site-verification=abc\""}
              ]
            }
        """.trimIndent()
        val result = MxLookup.parseTxtDnsJson(json)
        assertTrue(result is MxLookup.TxtResult.Ok)
        val records = (result as MxLookup.TxtResult.Ok).records
        assertEquals(2, records.size)
        assertTrue(records[0].startsWith("v=spf1"))
    }

    @Test
    fun extractSpfAndDmarc() {
        val spf = MxLookup.extractSpf(
            MxLookup.TxtResult.Ok(listOf("v=spf1 -all", "other")),
        )
        assertNotNull(spf)
        assertEquals("v=spf1 -all", spf!!.record)
        assertNull(spf.error)

        val dmarc = MxLookup.extractDmarc(
            MxLookup.TxtResult.Ok(listOf("v=DMARC1; p=reject; rua=mailto:a@b.c")),
        )
        assertEquals("reject", dmarc!!.policy)
        assertEquals("reject", MxLookup.parseDmarcPolicy(dmarc.record))
    }

    @Test
    fun missingSpf() {
        val spf = MxLookup.extractSpf(MxLookup.TxtResult.Ok(listOf("plain text")))
        assertTrue(spf!!.missing)
    }

    @Test
    fun nxDomainTxtIsEmptyOk() {
        val result = MxLookup.parseTxtDnsJson("""{"Status":3}""")
        assertTrue(result is MxLookup.TxtResult.Ok)
        assertTrue((result as MxLookup.TxtResult.Ok).records.isEmpty())
    }
}
