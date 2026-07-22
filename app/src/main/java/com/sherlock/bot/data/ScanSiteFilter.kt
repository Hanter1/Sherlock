package com.sherlock.bot.data

/**
 * Which catalog sites to include in a username scan.
 */
enum class ScanPreset(
    val id: String,
    val label: String,
) {
    QUICK("quick", "Быстрый"),
    ALL("all", "Sherlock Full"),
    SOCIAL("social", "Соцсети"),
    DEV("dev", "Dev"),
    MEDIA("media", "Медиа"),
    BY("by", "РБ"),
    ;

    companion object {
        fun fromId(raw: String?): ScanPreset =
            entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: QUICK
    }
}

object ScanSiteFilter {
    /** Sites that often hit antibot / login walls from a plain client. */
    val BOT_PROTECTED_NAMES: Set<String> = setOf("Instagram", "X")

    /** Belarus-oriented / regional public profiles. */
    val BY_FOCUS_NAMES: Set<String> = setOf("VK", "OK.ru", "Telegram", "Habr")

    fun filter(
        sites: List<OsintSite>,
        includeBotProtected: Boolean,
        preset: ScanPreset = ScanPreset.QUICK,
        includeNsfw: Boolean = false,
    ): List<OsintSite> {
        var filtered = when (preset) {
            ScanPreset.QUICK -> {
                val curated = sites.filter { it.curated }
                curated.ifEmpty { sites.filterNot { it.nsfw } }
            }
            ScanPreset.ALL -> sites
            ScanPreset.SOCIAL -> sites.filter { site ->
                site.categories.any { it in setOf("social", "creator") }
            }
            ScanPreset.DEV -> sites.filter { site ->
                site.categories.any { it == "dev" }
            }
            ScanPreset.MEDIA -> sites.filter { site ->
                site.categories.any { it in setOf("media", "design", "gaming") }
            }
            ScanPreset.BY -> sites.filter { it.name in BY_FOCUS_NAMES }
        }
        if (!includeNsfw) {
            filtered = filtered.filterNot { it.nsfw }
        }
        if (!includeBotProtected) {
            filtered = filtered.filterNot { it.name in BOT_PROTECTED_NAMES }
        }
        return filtered
    }
}
