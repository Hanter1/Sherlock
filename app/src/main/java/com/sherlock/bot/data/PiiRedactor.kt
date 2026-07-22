package com.sherlock.bot.data

/**
 * Scrubs phones / emails from shared report text.
 */
object PiiRedactor {
    private val emailRegex = Regex(
        """[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""",
    )
    private val phoneRegex = Regex(
        """(?<![A-Za-z0-9])(?:\+?\d[\d\-\s()]{8,}\d)""",
    )

    fun redact(text: String): String {
        var out = emailRegex.replace(text) { match ->
            maskEmail(match.value)
        }
        out = phoneRegex.replace(out) { match ->
            maskPhone(match.value)
        }
        return out
    }

    fun containsPii(text: String): Boolean =
        emailRegex.containsMatchIn(text) || phoneRegex.containsMatchIn(text)

    private fun maskEmail(email: String): String {
        val at = email.indexOf('@')
        if (at <= 0) return "[email]"
        val local = email.substring(0, at)
        val domain = email.substring(at + 1)
        val localMask = when {
            local.length <= 2 -> "*"
            else -> local.take(1) + "***" + local.takeLast(1)
        }
        return "$localMask@$domain"
    }

    private fun maskPhone(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        if (digits.length < 7) return "[phone]"
        return "+***" + digits.takeLast(4)
    }
}
