package com.sherlock.bot.data

/**
 * Diff found-sites between two scans of the same username.
 */
object UsernameScanDiff {

    data class Diff(
        val appeared: List<String>,
        val disappeared: List<String>,
        val stillFound: List<String>,
    )

    fun diff(
        previous: OsintResult.UsernameReport,
        current: OsintResult.UsernameReport,
    ): Diff {
        val prev = previous.found.map { it.site }.toSet()
        val curr = current.found.map { it.site }.toSet()
        return Diff(
            appeared = (curr - prev).sorted(),
            disappeared = (prev - curr).sorted(),
            stillFound = (curr.intersect(prev)).sorted(),
        )
    }

    fun format(previous: OsintResult.UsernameReport, current: OsintResult.UsernameReport): String? {
        val d = diff(previous, current)
        if (d.appeared.isEmpty() && d.disappeared.isEmpty()) {
            return "С прошлого скана найденные площадки не изменились (${d.stillFound.size})."
        }
        return buildString {
            appendLine("Δ с прошлого скана:")
            if (d.appeared.isNotEmpty()) {
                appendLine("• появились: ${d.appeared.joinToString(", ")}")
            }
            if (d.disappeared.isNotEmpty()) {
                appendLine("• пропали: ${d.disappeared.joinToString(", ")}")
            }
            append("• без изменений: ${d.stillFound.size}")
        }.trim()
    }
}
