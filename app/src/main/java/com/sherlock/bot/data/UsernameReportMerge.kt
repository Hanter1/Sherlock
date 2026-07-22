package com.sherlock.bot.data

/**
 * Merges a partial rescan (errors / uncertain sites) into a previous username report.
 */
object UsernameReportMerge {

    fun errorSiteNames(report: OsintResult.UsernameReport): Set<String> =
        report.errors.map { it.substringBefore(":").trim() }.filter { it.isNotEmpty() }.toSet()

    fun failedSiteNames(report: OsintResult.UsernameReport): Set<String> =
        errorSiteNames(report) + report.uncertain.map { it.site }.toSet()

    fun merge(
        previous: OsintResult.UsernameReport,
        partial: OsintResult.UsernameReport,
        recheckedSites: Set<String>,
    ): OsintResult.UsernameReport {
        if (recheckedSites.isEmpty()) return previous
        val found = previous.found.filter { it.site !in recheckedSites }.toMutableList()
        val uncertain = previous.uncertain.filter { it.site !in recheckedSites }.toMutableList()
        val notFound = previous.notFound.filter { it !in recheckedSites }.toMutableList()
        val errors = previous.errors
            .filter { it.substringBefore(":").trim() !in recheckedSites }
            .toMutableList()

        found += partial.found.filter { it.site in recheckedSites }
        uncertain += partial.uncertain.filter { it.site in recheckedSites }
        notFound += partial.notFound.filter { it in recheckedSites }
        errors += partial.errors.filter { it.substringBefore(":").trim() in recheckedSites }

        return previous.copy(
            found = found.sortedBy { it.site },
            uncertain = uncertain.sortedBy { it.site },
            notFound = notFound.sorted(),
            errors = errors.sorted(),
            elapsedMs = previous.elapsedMs + partial.elapsedMs,
            fromCache = false,
            cancelled = previous.cancelled || partial.cancelled,
            previousDiff = null,
        )
    }
}
