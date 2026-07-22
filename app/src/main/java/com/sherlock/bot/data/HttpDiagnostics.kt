package com.sherlock.bot.data

/**
 * Compact HTTP metadata for a site check (shown in reports / exports).
 */
data class HttpDiagnostics(
    val httpCode: Int? = null,
    val finalUrl: String? = null,
    val redirectCount: Int = 0,
    val detail: String? = null,
) {
    fun formatBrief(): String {
        val parts = buildList {
            httpCode?.let { add("HTTP $it") }
            if (redirectCount > 0) add("redir×$redirectCount")
            detail?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
        }
        return parts.joinToString(" · ")
    }

    fun withDetail(extra: String?): HttpDiagnostics {
        val merged = listOfNotNull(detail, extra?.trim()?.takeIf { it.isNotEmpty() })
            .distinct()
            .joinToString(" · ")
            .ifBlank { null }
        return copy(detail = merged)
    }

    companion object {
        fun of(
            code: Int?,
            finalUrl: String? = null,
            redirectCount: Int = 0,
            detail: String? = null,
        ): HttpDiagnostics = HttpDiagnostics(
            httpCode = code,
            finalUrl = finalUrl,
            redirectCount = redirectCount,
            detail = detail,
        )
    }
}
