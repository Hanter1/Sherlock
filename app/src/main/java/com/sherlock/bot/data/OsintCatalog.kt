package com.sherlock.bot.data

/**
 * How to decide username claimed / available (aligned with sherlock-project).
 */
enum class SiteErrorType(val id: String) {
    /** Previous Sherlock Bot heuristics (okBodyMarkers / trustHttpStatus / uncertain). */
    LEGACY("legacy"),
    /** HTTP 2xx → found; otherwise missing/error. */
    STATUS_CODE("status_code"),
    /** Body contains errorMsg → missing; 5xx → uncertain; else found. */
    MESSAGE("message"),
    /** Redirect away from profile URL → missing. */
    RESPONSE_URL("response_url"),
    ;

    companion object {
        fun fromId(raw: String?): SiteErrorType =
            entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: LEGACY
    }
}

data class OsintSite(
    val name: String,
    val urlTemplate: String,
    val okCodes: Set<Int> = setOf(200),
    val errorCodes: Set<Int> = setOf(404),
    /** Body markers that mean "profile missing". */
    val errorBodyMarkers: List<String> = emptyList(),
    /** Body markers that mean bot-protection / block → Error, not Found/Missing. */
    val blockBodyMarkers: List<String> = emptyList(),
    /** If non-empty, at least one marker must be present to count as found. */
    val okBodyMarkers: List<String> = emptyList(),
    /** Tags: dev, social, gaming, media, design, creator. */
    val categories: List<String> = emptyList(),
    val useHead: Boolean = false,
    /** Optional pause before request (ms) to reduce antibot triggers. */
    val rateLimitMs: Long = 0L,
    /**
     * When true and there are no okBodyMarkers, HTTP status alone may confirm FOUND
     * (typical for HEAD probes where 404 is reliable).
     */
    val trustHttpStatus: Boolean = false,
    val errorType: SiteErrorType = SiteErrorType.LEGACY,
    /** Skip site when username does not match (sherlock regexCheck). */
    val regexCheck: String = "",
    /** Alternate probe URL (sherlock urlProbe); still uses `{user}`. */
    val urlProbe: String = "",
    val nsfw: Boolean = false,
    /** Curated / regional short list for «Быстрый» preset. */
    val curated: Boolean = false,
    val requestHeaders: Map<String, String> = emptyMap(),
)

object OsintCatalogParser {

    data class ParsedCatalog(
        val version: Int,
        val updated: String,
        val sites: List<OsintSite>,
        val expectedSha256: String = "",
        val signature: String = "",
        val sourceLabel: String = "",
    )

    fun parse(json: String): List<OsintSite> = parseFull(json).sites

    fun parseFull(json: String): ParsedCatalog {
        val root = org.json.JSONObject(json)
        val sitesArr = root.getJSONArray("sites")
        require(sitesArr.length() > 0) { "osint catalog is empty" }
        val sites = buildList {
            for (i in 0 until sitesArr.length()) {
                add(parseSite(sitesArr.getJSONObject(i)))
            }
        }
        return ParsedCatalog(
            version = root.optInt("version", 1),
            updated = root.optString("updated", ""),
            sites = sites,
            expectedSha256 = root.optString("sha256", ""),
            signature = root.optString("signature", ""),
            sourceLabel = root.optString("source", ""),
        )
    }

    private fun parseSite(obj: org.json.JSONObject): OsintSite {
        val name = obj.getString("name")
        val urlTemplate = obj.getString("urlTemplate")
        require(urlTemplate.contains("{user}")) {
            "urlTemplate for $name must contain {user}"
        }
        val urlProbe = obj.optString("urlProbe", "")
        if (urlProbe.isNotBlank()) {
            require(urlProbe.contains("{user}")) {
                "urlProbe for $name must contain {user}"
            }
        }
        return OsintSite(
            name = name,
            urlTemplate = urlTemplate,
            okCodes = obj.optIntArray("okCodes") ?: setOf(200),
            errorCodes = obj.optIntArray("errorCodes") ?: setOf(404),
            errorBodyMarkers = obj.optStringArray("errorBodyMarkers"),
            blockBodyMarkers = obj.optStringArray("blockBodyMarkers"),
            okBodyMarkers = obj.optStringArray("okBodyMarkers"),
            categories = obj.optStringArray("categories"),
            useHead = obj.optBoolean("useHead", false),
            rateLimitMs = obj.optLong("rateLimitMs", 0L)
                .coerceIn(0L, CatalogLimits.MAX_RATE_LIMIT_MS),
            trustHttpStatus = obj.optBoolean("trustHttpStatus", false),
            errorType = SiteErrorType.fromId(obj.optString("errorType", SiteErrorType.LEGACY.id)),
            regexCheck = obj.optString("regexCheck", "").take(CatalogLimits.MAX_REGEX_LEN),
            urlProbe = urlProbe,
            nsfw = obj.optBoolean("nsfw", false),
            curated = obj.optBoolean("curated", false),
            requestHeaders = obj.optStringMap("headers"),
        )
    }

    private fun org.json.JSONObject.optIntArray(key: String): Set<Int>? {
        if (!has(key) || isNull(key)) return null
        val arr = getJSONArray(key)
        return buildSet {
            for (i in 0 until arr.length()) add(arr.getInt(i))
        }
    }

    private fun org.json.JSONObject.optStringArray(key: String): List<String> {
        if (!has(key) || isNull(key)) return emptyList()
        val arr = getJSONArray(key)
        return buildList {
            for (i in 0 until arr.length()) add(arr.getString(i))
        }
    }

    private fun org.json.JSONObject.optStringMap(key: String): Map<String, String> {
        if (!has(key) || isNull(key)) return emptyMap()
        val obj = optJSONObject(key) ?: return emptyMap()
        val out = linkedMapOf<String, String>()
        val keys = obj.keys()
        while (keys.hasNext() && out.size < CatalogLimits.MAX_HEADERS) {
            val k = keys.next()
            val v = obj.optString(k, "").take(CatalogLimits.MAX_HEADER_VALUE_LEN)
            if (k.isNotBlank() && v.isNotBlank()) out[k] = v
        }
        return out
    }
}

data class CatalogInfo(
    val version: Int = 0,
    val updated: String = "",
    val siteCount: Int = 0,
    val source: String = "asset",
    val sha256: String = "",
)

object OsintCatalog {
    @Volatile
    private var loaded: List<OsintSite>? = null

    @Volatile
    private var catalogInfo: CatalogInfo = CatalogInfo()

    val usernameSites: List<OsintSite>
        get() = loaded ?: error("OsintCatalog is not initialized. Call load() first.")

    fun info(): CatalogInfo = catalogInfo

    fun load(
        sites: List<OsintSite>,
        version: Int = 1,
        updated: String = "",
        source: String = "asset",
        sha256: String = "",
    ) {
        require(sites.isNotEmpty()) { "catalog must not be empty" }
        loaded = sites
        catalogInfo = CatalogInfo(
            version = version,
            updated = updated,
            siteCount = sites.size,
            source = source,
            sha256 = sha256,
        )
    }

    fun loadFromJson(json: String, source: String = "asset") {
        val parsed = OsintCatalogParser.parseFull(json)
        load(
            sites = parsed.sites,
            version = parsed.version,
            updated = parsed.updated,
            source = source.ifBlank { parsed.sourceLabel.ifBlank { "asset" } },
            sha256 = CatalogRepository.sha256Hex(json),
        )
    }

    fun isLoaded(): Boolean = loaded != null

    /** For unit tests that need sites without Android assets. */
    fun loadForTests(sites: List<OsintSite> = OsintCatalogParser.parse(FALLBACK_JSON)) {
        load(sites, version = 0, updated = "test", source = "test")
    }

    // Minimal fallback used only if assets fail at runtime
    const val FALLBACK_JSON = """
    {
      "version": 1,
      "sites": [
        {
          "name": "GitHub",
          "urlTemplate": "https://github.com/{user}",
          "errorCodes": [404],
          "useHead": true,
          "trustHttpStatus": true,
          "errorType": "status_code",
          "curated": true
        },
        {
          "name": "Telegram",
          "urlTemplate": "https://t.me/{user}",
          "okCodes": [200],
          "errorCodes": [],
          "errorBodyMarkers": ["tgme_icon_user", "tgme_username_link"],
          "okBodyMarkers": ["tgme_page_title", "tgme_page_photo"],
          "useHead": false,
          "errorType": "legacy",
          "curated": true
        },
        {
          "name": "Steam",
          "urlTemplate": "https://steamcommunity.com/id/{user}",
          "errorCodes": [],
          "errorBodyMarkers": ["The specified profile could not be found", "g_rgProfileData = {}"],
          "okBodyMarkers": ["steamID64", "actual_persona_name"],
          "useHead": false,
          "errorType": "legacy",
          "curated": true
        }
      ]
    }
    """
}
