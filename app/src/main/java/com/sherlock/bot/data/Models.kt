package com.sherlock.bot.data

data class ChatMessage(
    val id: String,
    val text: String,
    val fromBot: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val actions: List<BotAction> = emptyList(),
    val isTyping: Boolean = false,
)

data class BotAction(
    val id: String,
    val label: String,
)

enum class SearchMode {
    NONE,
    USERNAME,
    PHONE,
    EMAIL,
    FULL_NAME,
}

sealed class OsintResult {
    data class UsernameReport(
        val username: String,
        val found: List<SiteHit>,
        val notFound: List<String>,
        val errors: List<String>,
        val elapsedMs: Long,
    ) : OsintResult()

    data class InfoReport(
        val title: String,
        val body: String,
    ) : OsintResult()
}

data class SiteHit(
    val site: String,
    val url: String,
)
