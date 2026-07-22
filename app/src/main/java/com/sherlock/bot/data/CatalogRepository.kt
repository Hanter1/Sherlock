package com.sherlock.bot.data

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Loads bundled catalog, overlays a newer remote copy from disk / URL.
 */
class CatalogRepository(
    context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) {
    private val appContext = context.applicationContext
    private val remoteFile = File(appContext.filesDir, REMOTE_FILE)
    private val settings = AppSettings(appContext)

    sealed class UpdateResult {
        data class Ok(
            val version: Int,
            val siteCount: Int,
            val sha256: String,
            val fromCache: Boolean = false,
        ) : UpdateResult()

        data class Failed(val reason: String) : UpdateResult()
    }

    /** Prefer remote file if version >= asset, else asset. */
    fun loadIntoMemory() {
        val assetJson = readAsset()
        val asset = OsintCatalogParser.parseFull(assetJson)
        val remoteJson = remoteFile.takeIf { it.exists() }?.readText()
        if (remoteJson != null && remoteJson.length <= CatalogLimits.MAX_BYTES) {
            runCatching {
                val remote = OsintCatalogParser.parseFull(remoteJson)
                CatalogLimits.validateParsed(remote)?.let { error(it) }
                if (remote.version >= asset.version) {
                    OsintCatalog.load(
                        sites = remote.sites,
                        version = remote.version,
                        updated = remote.updated,
                        source = "remote",
                        sha256 = sha256Hex(remoteJson),
                    )
                    return
                }
            }
        }
        OsintCatalog.load(
            sites = asset.sites,
            version = asset.version,
            updated = asset.updated,
            source = "asset",
            sha256 = sha256Hex(assetJson),
        )
    }

    fun updateFromConfiguredUrl(): UpdateResult {
        val url = settings.catalogUrl.trim()
        if (url.isBlank()) {
            return UpdateResult.Failed("Укажите URL каталога в настройках")
        }
        return updateFromUrl(url)
    }

    fun updateFromUrl(url: String): UpdateResult {
        CatalogLimits.validateRemoteUrl(url)?.let { return UpdateResult.Failed(it) }
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", HttpHeaders.USER_AGENT)
                .header("Accept", "application/json,text/plain,*/*")
                .header("Accept-Language", HttpHeaders.ACCEPT_LANGUAGE)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return UpdateResult.Failed("HTTP ${response.code}")
                }
                val bodyBytes = response.body?.bytes() ?: ByteArray(0)
                if (bodyBytes.isEmpty()) return UpdateResult.Failed("пустой ответ")
                if (bodyBytes.size > CatalogLimits.MAX_BYTES) {
                    return UpdateResult.Failed(
                        "каталог слишком большой (${bodyBytes.size} > ${CatalogLimits.MAX_BYTES})",
                    )
                }
                val body = bodyBytes.toString(Charsets.UTF_8)
                val parsed = OsintCatalogParser.parseFull(body)
                CatalogLimits.validateParsed(parsed)?.let { return UpdateResult.Failed(it) }
                val assetVersion = runCatching {
                    OsintCatalogParser.parseFull(readAsset()).version
                }.getOrDefault(0)
                if (parsed.version < assetVersion) {
                    return UpdateResult.Failed(
                        "remote version ${parsed.version} < asset $assetVersion",
                    )
                }
                val hash = sha256Hex(body)
                val expected = parsed.expectedSha256
                if (expected.isNotBlank() && !expected.equals(hash, ignoreCase = true)) {
                    return UpdateResult.Failed("sha256 не совпал")
                }
                AtomicFiles.writeText(remoteFile, body)
                settings.catalogUrl = url
                OsintCatalog.load(
                    sites = parsed.sites,
                    version = parsed.version,
                    updated = parsed.updated,
                    source = "remote",
                    sha256 = hash,
                )
                UpdateResult.Ok(
                    version = parsed.version,
                    siteCount = parsed.sites.size,
                    sha256 = hash,
                )
            }
        } catch (e: Exception) {
            UpdateResult.Failed(e.message ?: "network error")
        }
    }

    fun clearRemote() {
        if (remoteFile.exists()) remoteFile.delete()
        loadIntoMemory()
    }

    private fun readAsset(): String {
        return appContext.assets.open(SherlockAppAssets.CATALOG).bufferedReader().use { it.readText() }
    }

    companion object {
        const val REMOTE_FILE = "osint_sites_remote.json"

        fun sha256Hex(text: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}

/** Avoid circular dependency on Application class name in data layer. */
object SherlockAppAssets {
    const val CATALOG = "osint_sites.json"
    const val DEF = "def_codes.json"
}
