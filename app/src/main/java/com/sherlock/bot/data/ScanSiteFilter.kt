package com.sherlock.bot.data

/**
 * Which catalog sites to include in a username scan.
 */
object ScanSiteFilter {
    /** Sites that often hit antibot / login walls from a plain client. */
    val BOT_PROTECTED_NAMES: Set<String> = setOf("Instagram", "X")

    fun filter(sites: List<OsintSite>, includeBotProtected: Boolean): List<OsintSite> {
        if (includeBotProtected) return sites
        return sites.filterNot { it.name in BOT_PROTECTED_NAMES }
    }
}
