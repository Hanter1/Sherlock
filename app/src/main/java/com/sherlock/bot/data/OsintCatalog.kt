package com.sherlock.bot.data

import org.json.JSONArray
import org.json.JSONObject

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
)

object OsintCatalogParser {

    data class ParsedCatalog(
        val version: Int,
        val updated: String,
        val sites: List<OsintSite>,
        val expectedSha256: String = "",
        val signature: String = "",
    )

    fun parse(json: String): List<OsintSite> = parseFull(json).sites

    fun parseFull(json: String): ParsedCatalog {
        val root = JSONObject(json)
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
        )
    }

    private fun parseSite(obj: JSONObject): OsintSite {
        val name = obj.getString("name")
        val urlTemplate = obj.getString("urlTemplate")
        require(urlTemplate.contains("{user}")) {
            "urlTemplate for $name must contain {user}"
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
        )
    }

    private fun JSONObject.optIntArray(key: String): Set<Int>? {
        if (!has(key) || isNull(key)) return null
        val arr = getJSONArray(key)
        return buildSet {
            for (i in 0 until arr.length()) add(arr.getInt(i))
        }
    }

    private fun JSONObject.optStringArray(key: String): List<String> {
        if (!has(key) || isNull(key)) return emptyList()
        val arr = getJSONArray(key)
        return buildList {
            for (i in 0 until arr.length()) add(arr.getString(i))
        }
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
            source = source,
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
          "useHead": true
        },
        {
          "name": "Telegram",
          "urlTemplate": "https://t.me/{user}",
          "okCodes": [200],
          "errorCodes": [],
          "errorBodyMarkers": ["If you have <strong>Telegram", "tgme_icon_user"],
          "okBodyMarkers": ["tgme_page_title", "tgme_page_photo"],
          "useHead": false
        },
        {
          "name": "Steam",
          "urlTemplate": "https://steamcommunity.com/id/{user}",
          "errorCodes": [],
          "errorBodyMarkers": ["The specified profile could not be found", "g_rgProfileData = {}"],
          "okBodyMarkers": ["steamID64", "actual_persona_name"],
          "useHead": false
        }
      ]
    }
    """
}
