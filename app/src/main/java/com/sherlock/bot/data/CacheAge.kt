package com.sherlock.bot.data

/**
 * Human-readable age / remaining TTL for username cache hits.
 */
object CacheAge {

    fun formatAge(savedAtMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        val age = (nowMs - savedAtMs).coerceAtLeast(0L)
        return formatDuration(age)
    }

    fun formatRemaining(savedAtMs: Long, ttlMs: Long, nowMs: Long = System.currentTimeMillis()): String? {
        val left = ttlMs - (nowMs - savedAtMs)
        if (left <= 0L) return null
        return formatDuration(left)
    }

    fun formatCacheLine(
        savedAtMs: Long?,
        ttlMs: Long?,
        nowMs: Long = System.currentTimeMillis(),
    ): String? {
        if (savedAtMs == null) return null
        val age = formatAge(savedAtMs, nowMs)
        val remaining = ttlMs?.let { formatRemaining(savedAtMs, it, nowMs) }
        return if (remaining != null) {
            "возраст $age · ещё ~$remaining"
        } else {
            "возраст $age"
        }
    }

    fun formatDuration(ms: Long): String = when {
        ms < 60_000L -> "${(ms / 1000L).coerceAtLeast(1L)} с"
        ms < 3_600_000L -> "${ms / 60_000L} мин"
        ms < 86_400_000L -> {
            val hours = ms / 3_600_000L
            val mins = (ms % 3_600_000L) / 60_000L
            if (mins == 0L) "$hours ч" else "$hours ч $mins мин"
        }
        else -> {
            val days = ms / 86_400_000L
            val hours = (ms % 86_400_000L) / 3_600_000L
            if (hours == 0L) "$days д" else "$days д $hours ч"
        }
    }
}
