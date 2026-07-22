package com.sherlock.bot.domain

import com.sherlock.bot.data.SearchMode

/**
 * Guesses search mode from free-form user input.
 * Pure JVM — unit-tested without Android.
 */
object QueryClassifier {

    private val usernameRegex = Regex("^[A-Za-z0-9._-]{2,32}$")

    fun detectMode(text: String): SearchMode = when {
        text.contains("@") && text.contains(".") && !text.startsWith("@") -> SearchMode.EMAIL
        text.filter { it.isDigit() }.length >= 10 -> SearchMode.PHONE
        text.trim().split(Regex("\\s+")).size >= 2 &&
            text.any { it.isCyrillicLetter() || it.isLetter() } &&
            text.any { it.isWhitespace() } -> SearchMode.FULL_NAME
        text.removePrefix("@").matches(usernameRegex) -> SearchMode.USERNAME
        else -> SearchMode.NONE
    }

    private fun Char.isCyrillicLetter(): Boolean = this in '\u0400'..'\u04FF'
}
