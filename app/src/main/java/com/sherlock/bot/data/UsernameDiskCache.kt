package com.sherlock.bot.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Persistent username-report cache with TTL (default 24h).
 * One JSON file: map of lowercase username → { savedAtMs, report }.
 */
class UsernameDiskCache(
    private val file: File,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    data class Entry(
        val savedAtMs: Long,
        val report: OsintResult.UsernameReport,
    )

    @Synchronized
    fun get(username: String): OsintResult.UsernameReport? =
        getEntry(username)?.report

    @Synchronized
    fun getEntry(username: String): Entry? {
        val key = username.lowercase()
        val entry = readAll()[key] ?: return null
        if (clock() - entry.savedAtMs > ttlMs) {
            removeKey(key)
            return null
        }
        return entry
    }

    @Synchronized
    fun put(username: String, report: OsintResult.UsernameReport) {
        if (report.cancelled) return
        val key = username.lowercase()
        val all = readAll().toMutableMap()
        all[key] = Entry(
            savedAtMs = clock(),
            report = report.copy(
                fromCache = false,
                cacheSavedAtMs = null,
                cacheTtlMs = null,
            ),
        )
        writeAll(all)
    }

    @Synchronized
    fun remove(username: String) {
        removeKey(username.lowercase())
    }

    @Synchronized
    fun clear() {
        if (file.exists()) file.delete()
    }

    @Synchronized
    fun size(): Int = readAll().count { clock() - it.value.savedAtMs <= ttlMs }

    @Synchronized
    fun keys(): Set<String> =
        readAll().filterValues { clock() - it.savedAtMs <= ttlMs }.keys

    @Synchronized
    fun oldestSavedAtMs(): Long? =
        readAll()
            .filterValues { clock() - it.savedAtMs <= ttlMs }
            .values
            .minOfOrNull { it.savedAtMs }

    val ttlMillis: Long get() = ttlMs

    private fun removeKey(key: String) {
        val all = readAll().toMutableMap()
        if (all.remove(key) != null) writeAll(all)
    }

    private fun readAll(): Map<String, Entry> {
        if (!file.exists()) return emptyMap()
        return runCatching {
            UsernameReportCodec.decodeMap(file.readText())
        }.getOrDefault(emptyMap())
    }

    private fun writeAll(map: Map<String, Entry>) {
        file.parentFile?.mkdirs()
        val pruned = map.filterValues { clock() - it.savedAtMs <= ttlMs }
        AtomicFiles.writeText(file, UsernameReportCodec.encodeMap(pruned))
    }

    companion object {
        val DEFAULT_TTL_MS: Long = TimeUnit.HOURS.toMillis(24)
    }
}

object UsernameReportCodec {

    fun encodeMap(map: Map<String, UsernameDiskCache.Entry>): String {
        val root = JSONObject()
        for ((key, entry) in map) {
            root.put(
                key,
                JSONObject()
                    .put("savedAtMs", entry.savedAtMs)
                    .put("report", encodeReport(entry.report)),
            )
        }
        return root.toString()
    }

    fun decodeMap(json: String): Map<String, UsernameDiskCache.Entry> {
        if (json.isBlank()) return emptyMap()
        val root = JSONObject(json)
        return buildMap {
            for (key in root.keys()) {
                val obj = root.getJSONObject(key)
                put(
                    key,
                    UsernameDiskCache.Entry(
                        savedAtMs = obj.getLong("savedAtMs"),
                        report = decodeReport(obj.getJSONObject("report")),
                    ),
                )
            }
        }
    }

    fun encodeReport(report: OsintResult.UsernameReport): JSONObject {
        return JSONObject()
            .put("username", report.username)
            .put("elapsedMs", report.elapsedMs)
            .put("cancelled", report.cancelled)
            .put(
                "found",
                JSONArray().also { arr ->
                    report.found.forEach { hit -> arr.put(encodeHit(hit)) }
                },
            )
            .put("notFound", JSONArray(report.notFound))
            .put("errors", JSONArray(report.errors))
            .put(
                "uncertain",
                JSONArray().also { arr ->
                    report.uncertain.forEach { hit -> arr.put(encodeHit(hit)) }
                },
            )
            .also { json ->
                report.previousDiff?.let { json.put("previousDiff", it) }
                report.cacheSavedAtMs?.let { json.put("cacheSavedAtMs", it) }
                report.cacheTtlMs?.let { json.put("cacheTtlMs", it) }
            }
    }

    fun encodeHit(hit: SiteHit): JSONObject =
        JSONObject()
            .put("site", hit.site)
            .put("url", hit.url)
            .put("categories", JSONArray(hit.categories))
            .put("confidence", hit.confidence.id)

    fun decodeReport(obj: JSONObject): OsintResult.UsernameReport {
        val foundArr = obj.optJSONArray("found") ?: JSONArray()
        val found = buildList {
            for (i in 0 until foundArr.length()) {
                add(decodeHit(foundArr.getJSONObject(i), defaultConfidence = HitConfidence.CONFIRMED))
            }
        }
        val uncertainArr = obj.optJSONArray("uncertain") ?: JSONArray()
        val uncertain = buildList {
            for (i in 0 until uncertainArr.length()) {
                add(decodeHit(uncertainArr.getJSONObject(i), defaultConfidence = HitConfidence.UNCERTAIN))
            }
        }
        return OsintResult.UsernameReport(
            username = obj.getString("username"),
            found = found,
            uncertain = uncertain,
            notFound = (obj.optJSONArray("notFound") ?: JSONArray()).toStringList(),
            errors = (obj.optJSONArray("errors") ?: JSONArray()).toStringList(),
            elapsedMs = obj.optLong("elapsedMs", 0L),
            cancelled = obj.optBoolean("cancelled", false),
            fromCache = false,
            cacheSavedAtMs = obj.optLong("cacheSavedAtMs").takeIf { obj.has("cacheSavedAtMs") },
            cacheTtlMs = obj.optLong("cacheTtlMs").takeIf { obj.has("cacheTtlMs") },
            previousDiff = obj.optString("previousDiff", "").takeIf { it.isNotBlank() },
        )
    }

    fun decodeHit(hit: JSONObject, defaultConfidence: HitConfidence): SiteHit {
        val cats = hit.optJSONArray("categories")
        val confidence = if (hit.has("confidence")) {
            HitConfidence.fromId(hit.optString("confidence"))
        } else {
            defaultConfidence
        }
        return SiteHit(
            site = hit.getString("site"),
            url = hit.getString("url"),
            categories = cats?.toStringList().orEmpty(),
            confidence = confidence,
        )
    }

    private fun JSONArray.toStringList(): List<String> = buildList {
        for (i in 0 until length()) add(getString(i))
    }
}
