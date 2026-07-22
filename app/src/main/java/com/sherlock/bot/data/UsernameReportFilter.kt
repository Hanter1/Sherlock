package com.sherlock.bot.data

enum class UsernameReportFilter {
    /** Full report: found + summary + errors. */
    FULL,

    /** Only found profiles (and summary counts). */
    FOUND_ONLY,

    /** Hide error/blocked section. */
    HIDE_ERRORS,
}

object SiteCategories {
    fun label(tag: String): String = when (tag.lowercase()) {
        "dev" -> "разработка"
        "social" -> "соцсети"
        "gaming" -> "игры"
        "media" -> "медиа"
        "design" -> "дизайн"
        "creator" -> "креаторы"
        else -> tag
    }

    fun formatElapsed(ms: Long): String = when {
        ms < 1000L -> "$ms мс"
        else -> String.format("%.1f с", ms / 1000.0)
    }
}
