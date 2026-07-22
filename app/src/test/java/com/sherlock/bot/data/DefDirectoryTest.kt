package com.sherlock.bot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DefDirectoryTest {

    @Before
    fun setUp() {
        DefDirectory.loadForTests()
    }

    @Test
    fun lookupKnownCode() {
        assertTrue(DefDirectory.lookup("916").contains("MTS"))
        assertTrue(DefDirectory.lookup("926").contains("MegaFon"))
        assertTrue(DefDirectory.lookup("961").contains("Tele2") || DefDirectory.lookup("961").contains("t2"))
    }

    @Test
    fun lookupUnknownCode() {
        assertTrue(DefDirectory.lookup("777").contains("не определён"))
    }

    @Test
    fun parseAssetShapedJson() {
        val parsed = DefDirectory.parse(
            """
            {
              "version": 1,
              "updated": "2026-07",
              "note": "test",
              "codes": { "999": "Yota / MegaFon" }
            }
            """.trimIndent(),
        )
        assertEquals("2026-07", parsed.updated)
        assertEquals("Yota / MegaFon", parsed.codes["999"])
    }
}
