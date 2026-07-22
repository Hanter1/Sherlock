package com.sherlock.bot.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * DNS-over-HTTPS client (Cloudflare → Google).
 * Supports MX and TXT (SPF / DMARC).
 */
class MxLookup(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build(),
    private val endpoints: List<DohEndpoint> = DEFAULT_ENDPOINTS,
) {
    data class DohEndpoint(val name: String, val baseUrl: String)

    data class MxRecord(val priority: Int, val host: String)

    sealed class Result {
        data class Ok(
            val records: List<MxRecord>,
            val provider: String = "",
        ) : Result()

        data class Failed(val reason: String) : Result()
    }

    sealed class TxtResult {
        data class Ok(
            val records: List<String>,
            val provider: String = "",
        ) : TxtResult()

        data class Failed(val reason: String) : TxtResult()
    }

    suspend fun lookup(domain: String): Result = withContext(Dispatchers.IO) {
        val failures = mutableListOf<String>()
        for (endpoint in endpoints) {
            when (val result = lookupMxOnce(endpoint, domain)) {
                is Result.Ok -> return@withContext result
                is Result.Failed -> failures += "${endpoint.name}: ${result.reason}"
            }
        }
        Result.Failed(failures.joinToString(" · ").ifBlank { "DNS unavailable" })
    }

    suspend fun lookupTxt(name: String): TxtResult = withContext(Dispatchers.IO) {
        val failures = mutableListOf<String>()
        for (endpoint in endpoints) {
            when (val result = lookupTxtOnce(endpoint, name)) {
                is TxtResult.Ok -> return@withContext result
                is TxtResult.Failed -> failures += "${endpoint.name}: ${result.reason}"
            }
        }
        TxtResult.Failed(failures.joinToString(" · ").ifBlank { "DNS unavailable" })
    }

    /** Domain SPF (TXT with v=spf1) + DMARC (_dmarc.domain). */
    suspend fun lookupMailPolicy(domain: String): MailPolicy = coroutineScope {
        val spfDeferred = async { lookupTxt(domain) }
        val dmarcDeferred = async { lookupTxt("_dmarc.$domain") }
        val spfTxt = spfDeferred.await()
        val dmarcTxt = dmarcDeferred.await()
        MailPolicy(
            spf = extractSpf(spfTxt),
            dmarc = extractDmarc(dmarcTxt),
            provider = listOf(
                (spfTxt as? TxtResult.Ok)?.provider,
                (dmarcTxt as? TxtResult.Ok)?.provider,
            ).firstOrNull { !it.isNullOrBlank() }.orEmpty(),
        )
    }

    private suspend fun lookupMxOnce(endpoint: DohEndpoint, domain: String): Result {
        return try {
            val body = fetchDnsJson(endpoint, domain, "MX")
                ?: return Result.Failed("empty body")
            when (val parsed = parseDnsJson(body)) {
                is Result.Ok -> parsed.copy(provider = endpoint.name)
                is Result.Failed -> parsed
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.Failed(e.message ?: "network error")
        }
    }

    private suspend fun lookupTxtOnce(endpoint: DohEndpoint, name: String): TxtResult {
        return try {
            val body = fetchDnsJson(endpoint, name, "TXT")
                ?: return TxtResult.Failed("empty body")
            when (val parsed = parseTxtDnsJson(body)) {
                is TxtResult.Ok -> parsed.copy(provider = endpoint.name)
                is TxtResult.Failed -> parsed
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            TxtResult.Failed(e.message ?: "network error")
        }
    }

    private suspend fun fetchDnsJson(endpoint: DohEndpoint, name: String, type: String): String? {
        val url = endpoint.baseUrl.toHttpUrl().newBuilder()
            .setQueryParameter("name", name)
            .setQueryParameter("type", type)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", HttpHeaders.ACCEPT_DNS_JSON)
            .header("User-Agent", HttpHeaders.USER_AGENT)
            .header("Accept-Language", HttpHeaders.ACCEPT_LANGUAGE)
            .get()
            .build()
        val call = client.newCall(request)
        val handle = coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion {
            call.cancel()
        }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code}")
                }
                return response.body?.string()
            }
        } finally {
            handle?.dispose()
        }
    }

    data class MailPolicy(
        val spf: SpfInfo?,
        val dmarc: DmarcInfo?,
        val provider: String = "",
    )

    data class SpfInfo(
        val record: String,
        val missing: Boolean = false,
        val error: String? = null,
    )

    data class DmarcInfo(
        val record: String,
        val policy: String?,
        val missing: Boolean = false,
        val error: String? = null,
    )

    companion object {
        val DEFAULT_ENDPOINTS = listOf(
            DohEndpoint("Cloudflare", "https://cloudflare-dns.com/dns-query"),
            DohEndpoint("Google", "https://dns.google/resolve"),
        )

        fun extractSpf(txt: TxtResult): SpfInfo? = when (txt) {
            is TxtResult.Failed -> SpfInfo(record = "", error = txt.reason)
            is TxtResult.Ok -> {
                val hit = txt.records.firstOrNull { it.contains("v=spf1", ignoreCase = true) }
                if (hit != null) SpfInfo(record = hit)
                else SpfInfo(record = "", missing = true)
            }
        }

        fun extractDmarc(txt: TxtResult): DmarcInfo? = when (txt) {
            is TxtResult.Failed -> DmarcInfo(record = "", policy = null, error = txt.reason)
            is TxtResult.Ok -> {
                val hit = txt.records.firstOrNull { it.contains("v=DMARC1", ignoreCase = true) }
                if (hit != null) {
                    DmarcInfo(record = hit, policy = parseDmarcPolicy(hit))
                } else {
                    DmarcInfo(record = "", policy = null, missing = true)
                }
            }
        }

        fun parseDmarcPolicy(record: String): String? {
            val match = Regex("""\bp\s*=\s*([a-zA-Z]+)""", RegexOption.IGNORE_CASE)
                .find(record)
            return match?.groupValues?.get(1)?.lowercase()
        }

        fun parseDnsJson(json: String): Result {
            val status = Regex(""""Status"\s*:\s*(\d+)""")
                .find(json)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
                ?: return Result.Failed("некорректный DNS-ответ")

            if (status != 0) {
                return Result.Failed("DNS status $status (домен не резолвится или нет MX)")
            }

            val answerBlock = Regex(""""Answer"\s*:\s*\[(.*?)]\s*[,}]""", RegexOption.DOT_MATCHES_ALL)
                .find(json)
                ?.groupValues
                ?.get(1)

            if (answerBlock == null) {
                return Result.Ok(emptyList())
            }

            val records = Regex(""""type"\s*:\s*15[\s\S]*?"data"\s*:\s*"([^"]+)"""")
                .findAll(answerBlock)
                .mapNotNull { match ->
                    val data = match.groupValues[1].trim().trimEnd('.')
                    val parts = data.split(Regex("\\s+"), limit = 2)
                    if (parts.size != 2) return@mapNotNull null
                    val priority = parts[0].toIntOrNull() ?: return@mapNotNull null
                    MxRecord(priority, parts[1].trimEnd('.'))
                }
                .sortedBy { it.priority }
                .toList()

            return Result.Ok(records)
        }

        fun parseTxtDnsJson(json: String): TxtResult {
            val status = Regex(""""Status"\s*:\s*(\d+)""")
                .find(json)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
                ?: return TxtResult.Failed("некорректный DNS-ответ")

            // NXDOMAIN / no data → empty TXT list (not hard fail for optional SPF/DMARC)
            if (status != 0) {
                return TxtResult.Ok(emptyList())
            }

            val answerBlock = Regex(""""Answer"\s*:\s*\[(.*?)]\s*[,}]""", RegexOption.DOT_MATCHES_ALL)
                .find(json)
                ?.groupValues
                ?.get(1)
                ?: return TxtResult.Ok(emptyList())

            val records = Regex(""""type"\s*:\s*16[\s\S]*?"data"\s*:\s*"((?:\\.|[^"\\])*)"""")
                .findAll(answerBlock)
                .map { match -> unescapeTxtData(match.groupValues[1]) }
                .filter { it.isNotBlank() }
                .toList()

            return TxtResult.Ok(records)
        }

        internal fun unescapeTxtData(raw: String): String {
            return raw
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .trim()
                .removeSurrounding("\"")
                .trim()
        }
    }
}
