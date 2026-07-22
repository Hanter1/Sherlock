package com.sherlock.bot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MxLookupTest {

    @Test
    fun parsesMxRecordsSortedByPriority() {
        val json = """
            {
              "Status": 0,
              "Question": [{"name": "example.com", "type": 15}],
              "Answer": [
                {"name": "example.com", "type": 15, "TTL": 300, "data": "20 alt.example.com."},
                {"name": "example.com", "type": 15, "TTL": 300, "data": "10 mail.example.com."}
              ]
            }
        """.trimIndent()

        val result = MxLookup.parseDnsJson(json)
        assertTrue(result is MxLookup.Result.Ok)
        val records = (result as MxLookup.Result.Ok).records
        assertEquals(2, records.size)
        assertEquals(10, records[0].priority)
        assertEquals("mail.example.com", records[0].host)
        assertEquals(20, records[1].priority)
        assertEquals("alt.example.com", records[1].host)
    }

    @Test
    fun emptyAnswerMeansNoMx() {
        val json = """{"Status":0,"Question":[{"name":"x.test","type":15}]}"""
        val result = MxLookup.parseDnsJson(json)
        assertTrue(result is MxLookup.Result.Ok)
        assertTrue((result as MxLookup.Result.Ok).records.isEmpty())
    }

    @Test
    fun nonZeroStatusIsFailure() {
        val json = """{"Status":3,"Question":[{"name":"missing.test","type":15}]}"""
        val result = MxLookup.parseDnsJson(json)
        assertTrue(result is MxLookup.Result.Failed)
    }
}
