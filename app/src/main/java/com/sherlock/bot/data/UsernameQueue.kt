package com.sherlock.bot.data

/**
 * Parses a multi-nick scan argument: spaces and/or commas.
 * Caps length to keep sequential scans practical on mobile.
 */
object UsernameQueue {
    const val MAX_NICKS = 5

    private val usernamePattern = Regex("^[A-Za-z0-9._-]{2,32}$")

    data class ParseResult(
        val nicks: List<String>,
        val rejected: List<String> = emptyList(),
        val truncated: Boolean = false,
    )

    fun parse(raw: String): ParseResult {
        val tokens = raw
            .split(Regex("[,\\s]+"))
            .map { it.trim().removePrefix("@") }
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return ParseResult(emptyList())

        val rejected = mutableListOf<String>()
        val accepted = LinkedHashSet<String>()
        for (token in tokens) {
            val key = token.lowercase()
            if (accepted.any { it.equals(token, ignoreCase = true) }) continue
            if (!token.matches(usernamePattern)) {
                rejected += token
                continue
            }
            accepted += token
        }
        val list = accepted.toList()
        val truncated = list.size > MAX_NICKS
        return ParseResult(
            nicks = list.take(MAX_NICKS),
            rejected = rejected,
            truncated = truncated,
        )
    }
}
