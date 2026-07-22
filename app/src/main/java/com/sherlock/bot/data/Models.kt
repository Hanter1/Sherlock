package com.sherlock.bot.data

data class ChatMessage(
    val id: String,
    val text: String,
    val fromBot: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val actions: List<BotAction> = emptyList(),
    val isTyping: Boolean = false,
    /** Links message actions (filter / export / rescan) to a stored [OsintResult.UsernameReport]. */
    val reportId: String? = null,
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
    COMPARE,
}

sealed class OsintResult {
    data class UsernameReport(
        val username: String,
        val found: List<SiteHit>,
        val notFound: List<String>,
        val errors: List<String>,
        val elapsedMs: Long,
        val cancelled: Boolean = false,
        val fromCache: Boolean = false,
        /** Human-readable Δ vs previous scan of the same nick (if any). */
        val previousDiff: String? = null,
        /**
         * HTTP looked like a hit, but the site has no profile markers —
         * low-confidence (not a confirmed FOUND).
         */
        val uncertain: List<SiteHit> = emptyList(),
    ) : OsintResult()

    data class InfoReport(
        val title: String,
        val body: String,
    ) : OsintResult()
}

data class SiteHit(
    val site: String,
    val url: String,
    val categories: List<String> = emptyList(),
)

enum class SiteCheckStatus {
    FOUND,
    UNCERTAIN,
    MISSING,
    ERROR,
}

data class SiteCheckProgress(
    val site: String,
    val status: SiteCheckStatus,
    val url: String? = null,
    val reason: String? = null,
    val done: Int,
    val total: Int,
)
