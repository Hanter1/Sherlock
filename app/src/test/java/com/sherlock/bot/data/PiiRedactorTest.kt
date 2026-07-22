package com.sherlock.bot.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PiiRedactorTest {

    @Test
    fun redactsEmailAndPhone() {
        val raw = "Пишите на name@mail.ru или +375291234567"
        val out = PiiRedactor.redact(raw)
        assertTrue(out.contains("@mail.ru"))
        assertTrue(out.contains("4567"))
        assertFalse(out.contains("name@mail.ru"))
        assertFalse(out.contains("291234567"))
    }

    @Test
    fun detectsPii() {
        assertTrue(PiiRedactor.containsPii("a@b.co"))
        assertTrue(PiiRedactor.containsPii("+375 29 123-45-67"))
        assertFalse(PiiRedactor.containsPii("ник durov без контактов"))
    }

    @Test
    fun masksLongLocalPart() {
        val out = PiiRedactor.redact("alice@example.com")
        assertTrue(out.startsWith("a***e@"))
        assertTrue(out.endsWith("example.com"))
    }
}
