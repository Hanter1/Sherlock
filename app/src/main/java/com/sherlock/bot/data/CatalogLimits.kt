package com.sherlock.bot.data

/**
 * Safety bounds for remote / parsed OSINT catalogs.
 */
object CatalogLimits {
    const val MAX_BYTES = 512_000
    const val MAX_SITES = 200
    const val MAX_RATE_LIMIT_MS = 10_000L
    const val MAX_MARKER_LEN = 200
    const val MAX_MARKERS_PER_LIST = 32
    const val MAX_NAME_LEN = 64

    fun validateRemoteUrl(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return "пустой URL"
        if (!trimmed.startsWith("https://", ignoreCase = true)) {
            return "нужен HTTPS URL"
        }
        return null
    }

    fun validateParsed(parsed: OsintCatalogParser.ParsedCatalog): String? {
        if (parsed.sites.isEmpty()) return "каталог пуст"
        if (parsed.sites.size > MAX_SITES) {
            return "слишком много площадок (${parsed.sites.size} > $MAX_SITES)"
        }
        val names = HashSet<String>()
        for (site in parsed.sites) {
            if (site.name.isBlank() || site.name.length > MAX_NAME_LEN) {
                return "некорректное имя площадки"
            }
            if (!names.add(site.name)) {
                return "дубликат площадки: ${site.name}"
            }
            if (!site.urlTemplate.startsWith("https://", ignoreCase = true)) {
                return "${site.name}: urlTemplate должен быть HTTPS"
            }
            if (site.rateLimitMs > MAX_RATE_LIMIT_MS) {
                return "${site.name}: rateLimitMs > $MAX_RATE_LIMIT_MS"
            }
            val markerError = validateMarkers(site.name, "errorBodyMarkers", site.errorBodyMarkers)
                ?: validateMarkers(site.name, "blockBodyMarkers", site.blockBodyMarkers)
                ?: validateMarkers(site.name, "okBodyMarkers", site.okBodyMarkers)
            if (markerError != null) return markerError
        }
        return null
    }

    private fun validateMarkers(site: String, field: String, markers: List<String>): String? {
        if (markers.size > MAX_MARKERS_PER_LIST) {
            return "$site: $field слишком длинный список"
        }
        if (markers.any { it.length > MAX_MARKER_LEN }) {
            return "$site: $field содержит слишком длинный маркер"
        }
        return null
    }
}
