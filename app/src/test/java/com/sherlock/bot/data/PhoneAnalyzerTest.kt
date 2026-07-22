package com.sherlock.bot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PhoneAnalyzerTest {

    @Before
    fun setUp() {
        DefDirectory.loadForTests()
    }

    @Test
    fun normalizePlus7() {
        assertEquals("79001234567", PhoneAnalyzer.normalizeRu("+79001234567"))
    }

    @Test
    fun normalizeLeading8() {
        assertEquals("79001234567", PhoneAnalyzer.normalizeRu("8 (900) 123-45-67"))
    }

    @Test
    fun normalizeTenDigitsAsRu() {
        assertEquals("79001234567", PhoneAnalyzer.normalizeRu("9001234567"))
    }

    @Test
    fun rejectTooShort() {
        assertNull(PhoneAnalyzer.normalizeRu("12345"))
        assertNull(PhoneAnalyzer.normalizeRu("+123"))
    }

    @Test
    fun rejectNonRuNormalizeStillNull() {
        assertNull(PhoneAnalyzer.normalizeRu("+12345678901"))
    }

    @Test
    fun analyzeUsNumber() {
        val report = PhoneAnalyzer.analyze("+12025550123")
        assertTrue(report.body.contains("+12025550123"))
        assertTrue(report.body.contains("США") || report.body.contains("Канада"))
        assertTrue(report.body.contains("202"))
    }

    @Test
    fun analyzeUkraine() {
        val report = PhoneAnalyzer.analyze("+380501234567")
        assertTrue(report.body.contains("Украина"))
        assertTrue(report.body.contains("+380501234567"))
    }

    @Test
    fun analyzeUk() {
        val report = PhoneAnalyzer.analyze("+447911123456")
        assertTrue(report.body.contains("Великобритания"))
    }

    @Test
    fun analyzeContainsNormalizedNumber() {
        val report = PhoneAnalyzer.analyze("+79161234567")
        assertEquals("Телефон · открытые данные", report.title)
        assertTrue(report.body.contains("+79161234567"))
        assertTrue(report.body.contains("DEF-код: 916"))
        assertTrue(report.body.contains("MTS"))
        assertTrue(report.body.contains("Россия") || report.body.contains("Казахстан"))
    }

    @Test
    fun analyzeBelarus() {
        val report = PhoneAnalyzer.analyze("+375291234567")
        assertTrue(report.body.contains("Беларусь"))
        assertTrue(report.body.contains("+375291234567"))
        assertTrue(report.body.contains("Префикс РБ: 29"))
        assertTrue(report.body.contains("A1") || report.body.contains("МТС"))
    }

    @Test
    fun normalizeByFrom80() {
        assertEquals("375291234567", PhoneAnalyzer.normalizeBy("80291234567"))
    }

    @Test
    fun analyzeInvalidMessage() {
        val report = PhoneAnalyzer.analyze("abc")
        assertEquals("Телефон", report.title)
        assertTrue(report.body.contains("Не распознал"))
        assertTrue(report.body.contains("+375"))
    }

    @Test
    fun operatorHeuristicsFromDirectory() {
        assertTrue(PhoneAnalyzer.guessRuOperator("900").contains("Beeline") || PhoneAnalyzer.guessRuOperator("900").contains("билайн"))
        assertTrue(PhoneAnalyzer.guessRuOperator("916").contains("MTS"))
        assertTrue(PhoneAnalyzer.guessRuOperator("926").contains("MegaFon"))
        assertTrue(PhoneAnalyzer.guessRuOperator("961").contains("t2") || PhoneAnalyzer.guessRuOperator("961").contains("Tele2"))
        assertTrue(PhoneAnalyzer.guessRuOperator("777").contains("не определён"))
    }
}
