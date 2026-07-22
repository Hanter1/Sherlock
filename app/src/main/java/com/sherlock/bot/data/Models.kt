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
        /** When [fromCache], wall-clock time the entry was saved. */
        val cacheSavedAtMs: Long? = null,
        /** Disk-cache TTL used for remaining-time hint (null for session-only). */
        val cacheTtlMs: Long? = null,
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

enum class HitConfidence(val id: String, val label: String) {
    CONFIRMED("confirmed", "подтверждено"),
    UNCERTAIN("uncertain", "неуверенно"),
    ;

    companion object {
        fun fromId(raw: String?): HitConfidence =
            entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: CONFIRMED
    }
}

data class SiteHit(
    val site: String,
    val url: String,
    val categories: List<String> = emptyList(),
    val confidence: HitConfidence = HitConfidence.CONFIRMED,
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
    /** Nick being scanned (useful for multi-nick queues). */
    val username: String? = null,
    /** 1-based index in a username queue, if any. */
    val queueIndex: Int? = null,
    val queueTotal: Int? = null,
)
