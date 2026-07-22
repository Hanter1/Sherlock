package com.sherlock.bot.data

/**
 * Compare two username scan reports: shared hits vs only-A / only-B.
 */
object UsernameCompare {

    data class SharedHit(
        val site: String,
        val urlA: String,
        val urlB: String,
        val categories: List<String> = emptyList(),
    )

    data class Diff(
        val usernameA: String,
        val usernameB: String,
        val both: List<SharedHit>,
        val onlyA: List<SiteHit>,
        val onlyB: List<SiteHit>,
        val elapsedMs: Long,
        val fromCacheA: Boolean,
        val fromCacheB: Boolean,
    )

    fun diff(
        a: OsintResult.UsernameReport,
        b: OsintResult.UsernameReport,
    ): Diff {
        val mapA = a.found.associateBy { it.site }
        val mapB = b.found.associateBy { it.site }
        val bothNames = mapA.keys.intersect(mapB.keys).sorted()
        val onlyANames = (mapA.keys - mapB.keys).sorted()
        val onlyBNames = (mapB.keys - mapA.keys).sorted()
        return Diff(
            usernameA = a.username,
            usernameB = b.username,
            both = bothNames.map { name ->
                val ha = mapA.getValue(name)
                val hb = mapB.getValue(name)
                SharedHit(
                    site = name,
                    urlA = ha.url,
                    urlB = hb.url,
                    categories = ha.categories.ifEmpty { hb.categories },
                )
            },
            onlyA = onlyANames.map { mapA.getValue(it) },
            onlyB = onlyBNames.map { mapB.getValue(it) },
            elapsedMs = a.elapsedMs + b.elapsedMs,
            fromCacheA = a.fromCache,
            fromCacheB = b.fromCache,
        )
    }

    fun formatDiff(diff: Diff): String = buildString {
        appendLine("—— Сравнение ников ——")
        appendLine("`${diff.usernameA}` vs `${diff.usernameB}`")
        appendLine(
            "Общих: *${diff.both.size}* · только A: ${diff.onlyA.size} · только B: ${diff.onlyB.size}",
        )
        when {
            diff.fromCacheA || diff.fromCacheB -> {
                val parts = buildList {
                    if (diff.fromCacheA) add("A из кэша")
                    if (diff.fromCacheB) add("B из кэша")
                }
                appendLine(parts.joinToString(" · "))
            }
            else -> appendLine("Время: ${SiteCategories.formatElapsed(diff.elapsedMs)}")
        }
        appendLine()

        if (diff.both.isNotEmpty()) {
            appendLine("На обоих (${diff.both.size}):")
            diff.both.forEach { hit ->
                appendLine("• ${hit.site}")
                appendLine("  A: ${hit.urlA}")
                appendLine("  B: ${hit.urlB}")
            }
            appendLine()
        } else {
            appendLine("Общих найденных площадок нет.")
            appendLine()
        }

        if (diff.onlyA.isNotEmpty()) {
            appendLine("Только `${diff.usernameA}`:")
            diff.onlyA.forEach { appendLine("• ${it.site}: ${it.url}") }
            appendLine()
        }

        if (diff.onlyB.isNotEmpty()) {
            appendLine("Только `${diff.usernameB}`:")
            diff.onlyB.forEach { appendLine("• ${it.site}: ${it.url}") }
            appendLine()
        }

        appendLine("Сравнение только по *найденным* профилям (missing/error не смешиваются).")
    }.trim()

    fun toReport(
        a: OsintResult.UsernameReport,
        b: OsintResult.UsernameReport,
    ): OsintResult.InfoReport = OsintResult.InfoReport(
        title = "Сравнение ников",
        body = formatDiff(diff(a, b)),
    )
}
