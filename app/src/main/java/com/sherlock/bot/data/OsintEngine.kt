package com.sherlock.bot.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Response
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern
import kotlin.coroutines.coroutineContext

class OsintEngine(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build(),
    private val maxParallel: () -> Int = { 6 },
    private val maxRetries: Int = 2,
    private val mxLookup: MxLookup = MxLookup(client),
    private val gravatarLookup: GravatarLookup = GravatarLookup(client),
    private val sessionCache: UsernameSessionCache = UsernameSessionCache(),
    private val diskCache: UsernameDiskCache? = null,
    private val includeBotProtected: () -> Boolean = { true },
    private val scanPreset: () -> ScanPreset = { ScanPreset.ALL },
    private val emailLookupMx: () -> Boolean = { true },
    private val emailLookupGravatar: () -> Boolean = { true },
    private val hostRateLimiter: HostRateLimiter = HostRateLimiter(),
) {
    private val usernamePattern = Pattern.compile("^[A-Za-z0-9._-]{2,32}$")

    suspend fun searchUsername(
        raw: String,
        onProgress: suspend (SiteCheckProgress) -> Unit = {},
        bypassCache: Boolean = false,
        onlySites: Set<String>? = null,
    ): OsintResult.UsernameReport = withContext(Dispatchers.IO) {
        val username = raw.trim().removePrefix("@")
        require(usernamePattern.matcher(username).matches()) {
            "Никнейм должен быть 2–32 символа: буквы, цифры, . _ -"
        }

        val partialScan = !onlySites.isNullOrEmpty()
        if (!partialScan) {
            if (bypassCache) {
                // keep previous for Δ before wipe
            } else {
                sessionCache.getEntry(username)?.let { entry ->
                    return@withContext entry.report.copy(
                        fromCache = true,
                        elapsedMs = 0,
                        previousDiff = null,
                        cacheSavedAtMs = entry.savedAtMs,
                        cacheTtlMs = null,
                    )
                }
                diskCache?.getEntry(username)?.let { entry ->
                    sessionCache.put(username, entry.report)
                    return@withContext entry.report.copy(
                        fromCache = true,
                        elapsedMs = 0,
                        previousDiff = null,
                        cacheSavedAtMs = entry.savedAtMs,
                        cacheTtlMs = diskCache.ttlMillis,
                    )
                }
            }
        }

        val previous = if (partialScan) null else sessionCache.get(username) ?: diskCache?.get(username)
        if (bypassCache && !partialScan) {
            invalidateUsername(username)
        }

        val sites = ScanSiteFilter.filter(
            sites = OsintCatalog.usernameSites,
            includeBotProtected = includeBotProtected(),
            preset = scanPreset(),
        ).let { list ->
            if (onlySites.isNullOrEmpty()) list
            else list.filter { it.name in onlySites }
        }
        require(sites.isNotEmpty()) { "Нет площадок для проверки (проверьте настройки каталога)" }

        val started = System.currentTimeMillis()
        val done = AtomicInteger(0)
        val parallel = maxParallel().coerceIn(1, 16)
        val semaphore = Semaphore(parallel)

        val results = coroutineScope {
            sites.map { site ->
                async {
                    val host = site.urlTemplate
                        .replace("{user}", "probe")
                        .toHttpUrlOrNull()
                        ?.host
                        ?.takeIf { it.isNotBlank() }
                        ?: site.name.lowercase()
                    hostRateLimiter.withHost(host) {
                        semaphore.withPermit {
                            currentCoroutineContext().ensureActive()
                            if (site.rateLimitMs > 0) {
                                delay(site.rateLimitMs)
                                currentCoroutineContext().ensureActive()
                            }
                            val outcome = checkSite(site, username)
                            currentCoroutineContext().ensureActive()
                            val n = done.incrementAndGet()
                            val progress = when (outcome) {
                                is CheckOutcome.Found -> SiteCheckProgress(
                                    site = site.name,
                                    status = SiteCheckStatus.FOUND,
                                    url = outcome.url,
                                    done = n,
                                    total = sites.size,
                                    username = username,
                                )
                                is CheckOutcome.Uncertain -> SiteCheckProgress(
                                    site = site.name,
                                    status = SiteCheckStatus.UNCERTAIN,
                                    url = outcome.url,
                                    reason = outcome.diagnostics?.formatBrief() ?: outcome.reason,
                                    done = n,
                                    total = sites.size,
                                    username = username,
                                )
                                is CheckOutcome.Missing -> SiteCheckProgress(
                                    site = site.name,
                                    status = SiteCheckStatus.MISSING,
                                    done = n,
                                    total = sites.size,
                                    username = username,
                                )
                                is CheckOutcome.Error -> SiteCheckProgress(
                                    site = site.name,
                                    status = SiteCheckStatus.ERROR,
                                    reason = outcome.diagnostics?.formatBrief()?.ifBlank { null }
                                        ?: outcome.reason,
                                    done = n,
                                    total = sites.size,
                                    username = username,
                                )
                            }
                            onProgress(progress)
                            outcome
                        }
                    }
                }
            }.awaitAll()
        }

        val report = buildUsernameReport(username, results, started, cancelled = false)
        if (partialScan) {
            return@withContext report
        }
        val withDiff = if (previous != null && !previous.cancelled) {
            report.copy(previousDiff = UsernameScanDiff.format(previous, report))
        } else {
            report
        }
        sessionCache.put(username, withDiff)
        diskCache?.put(username, withDiff)
        withDiff
    }

    /** Re-check error (+ optional uncertain) sites and merge into [previous]. */
    suspend fun rescanFailedSites(
        previous: OsintResult.UsernameReport,
        includeUncertain: Boolean = true,
        onProgress: suspend (SiteCheckProgress) -> Unit = {},
    ): OsintResult.UsernameReport {
        val names = if (includeUncertain) {
            UsernameReportMerge.failedSiteNames(previous)
        } else {
            UsernameReportMerge.errorSiteNames(previous)
        }
        require(names.isNotEmpty()) { "Нет ошибок / неуверенных площадок для повтора" }
        val partial = searchUsername(
            raw = previous.username,
            onProgress = onProgress,
            bypassCache = true,
            onlySites = names,
        )
        val merged = UsernameReportMerge.merge(previous, partial, names)
        sessionCache.put(previous.username, merged)
        diskCache?.put(previous.username, merged)
        return merged
    }

    fun invalidateUsername(username: String) {
        sessionCache.remove(username)
        diskCache?.remove(username)
    }

    fun clearUsernameCaches() {
        sessionCache.clear()
        diskCache?.clear()
    }

    fun usernameCacheSize(): Int {
        val keys = sessionCache.keys().toMutableSet()
        keys += diskCache?.keys().orEmpty()
        return keys.size
    }

    fun usernameCacheSummary(): String {
        val n = usernameCacheSize()
        if (n == 0) return "пусто"
        val oldest = listOfNotNull(
            sessionCache.oldestSavedAtMs(),
            diskCache?.oldestSavedAtMs(),
        ).minOrNull()
        val age = oldest?.let { CacheAge.formatAge(it) }
        val ttlHours = TimeUnit.MILLISECONDS.toHours(diskCache?.ttlMillis ?: UsernameDiskCache.DEFAULT_TTL_MS)
        return buildString {
            append("$n")
            if (age != null) append(" · старше $age")
            append(" · TTL ${ttlHours}ч")
        }
    }

    fun analyzePhone(raw: String): OsintResult.InfoReport = PhoneAnalyzer.analyze(raw)

    suspend fun analyzeEmail(raw: String): OsintResult.InfoReport = withContext(Dispatchers.IO) {
        val parsed = EmailAnalyzer.parse(raw) ?: return@withContext EmailAnalyzer.invalidReport()
        val mxOn = emailLookupMx()
        val gravatarOn = emailLookupGravatar()
        if (!mxOn && !gravatarOn) {
            return@withContext EmailAnalyzer.formatReport(
                parsed = parsed,
                mx = null,
                gravatar = null,
                policy = null,
                mxEnabled = false,
                gravatarEnabled = false,
            )
        }
        coroutineScope {
            val mxDeferred = if (mxOn) async { mxLookup.lookup(parsed.domain) } else null
            val policyDeferred = if (mxOn) async { mxLookup.lookupMailPolicy(parsed.domain) } else null
            val gravatarDeferred = if (gravatarOn) async { gravatarLookup.lookup(parsed.email) } else null
            EmailAnalyzer.formatReport(
                parsed = parsed,
                mx = mxDeferred?.await(),
                gravatar = gravatarDeferred?.await(),
                policy = policyDeferred?.await(),
                mxEnabled = mxOn,
                gravatarEnabled = gravatarOn,
            )
        }
    }

    suspend fun compareUsernames(
        rawA: String,
        rawB: String,
        onProgress: suspend (SiteCheckProgress) -> Unit = {},
    ): OsintResult.InfoReport {
        val siteTotal = ScanSiteFilter.filter(
            sites = runCatching { OsintCatalog.usernameSites }.getOrDefault(emptyList()),
            includeBotProtected = includeBotProtected(),
            preset = scanPreset(),
        ).size.coerceAtLeast(1)
        val total = siteTotal * 2

        val reportA = searchUsername(rawA, onProgress = { p ->
            onProgress(
                p.copy(
                    site = "A·${p.site}",
                    done = p.done,
                    total = total,
                ),
            )
        })
        val reportB = searchUsername(rawB, onProgress = { p ->
            onProgress(
                p.copy(
                    site = "B·${p.site}",
                    done = siteTotal + p.done,
                    total = total,
                ),
            )
        })
        return UsernameCompare.toReport(reportA, reportB)
    }

    fun analyzeFullName(raw: String): OsintResult.InfoReport {
        val name = raw.trim().replace(Regex("\\s+"), " ")
        val parts = name.split(" ")
        if (parts.size < 2 || name.length < 5) {
            return OsintResult.InfoReport(
                title = "ФИО",
                body = "Пришлите минимум фамилию и имя, например: `Иванов Иван` или `Кавалёў Аляксей`.",
            )
        }
        val query = URLEncoder.encode(name, StandardCharsets.UTF_8.name())
        return OsintResult.InfoReport(
            title = "ФИО · публичные ссылки",
            body = buildString {
                appendLine("Запрос: *$name*")
                appendLine()
                appendLine("Открытые точки входа (акцент на РБ):")
                appendLine("• Google BY: https://www.google.by/search?q=$query&hl=be")
                appendLine("• Yandex BY: https://yandex.by/search/?text=$query")
                appendLine("• VK: https://vk.com/search?c%5Bq%5D=$query&c%5Bsection%5D=people")
                appendLine("• Google: https://www.google.com/search?q=$query")
                appendLine()
                appendLine("Приложение не агрегирует персональные досье и не лезет в закрытые реестры.")
            }.trim(),
        )
    }

    private suspend fun checkSite(site: OsintSite, username: String): CheckOutcome {
        val url = site.urlTemplate.replace("{user}", username)
        return try {
            val needsBody = site.errorBodyMarkers.isNotEmpty() ||
                site.okBodyMarkers.isNotEmpty() ||
                site.blockBodyMarkers.isNotEmpty()
            val method = if (!needsBody && site.useHead) "HEAD" else "GET"
            var response = executeWithRetry(method, url, readBody = needsBody || method == "GET")

            if (method == "HEAD" && response.code in setOf(405, 403, 400)) {
                currentCoroutineContext().ensureActive()
                response = executeWithRetry("GET", url, readBody = needsBody)
            }

            if (response.code == 429) {
                return CheckOutcome.Error(
                    site = site.name,
                    reason = "rate limited",
                    diagnostics = response.toDiagnostics("rate limited"),
                )
            }

            classify(
                site = site,
                url = url,
                code = response.code,
                body = response.body,
                probe = response,
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            CheckOutcome.Error(
                site = site.name,
                reason = e.message ?: "network error",
                diagnostics = HttpDiagnostics(detail = e.message ?: "network error"),
            )
        }
    }

    internal fun classify(
        site: OsintSite,
        url: String,
        code: Int,
        body: String?,
        probe: HttpProbe? = null,
    ): CheckOutcome {
        val baseDiag = probe?.toDiagnostics() ?: HttpDiagnostics.of(code = code, finalUrl = url)

        if (site.errorCodes.isNotEmpty() && code in site.errorCodes) {
            return CheckOutcome.Missing(site.name)
        }

        val text = body.orEmpty()
        if (site.blockBodyMarkers.any { marker -> text.contains(marker, ignoreCase = true) }) {
            return CheckOutcome.Error(
                site = site.name,
                reason = "blocked / challenge",
                diagnostics = baseDiag.withDetail("blocked / challenge"),
            )
        }
        if (site.errorBodyMarkers.any { marker -> text.contains(marker, ignoreCase = true) }) {
            return CheckOutcome.Missing(site.name)
        }

        if (site.okBodyMarkers.isNotEmpty()) {
            return if (site.okBodyMarkers.any { marker -> text.contains(marker, ignoreCase = true) }) {
                CheckOutcome.Found(site.name, url, baseDiag)
            } else if (code in 200..299) {
                CheckOutcome.Missing(site.name)
            } else {
                CheckOutcome.Error(
                    site = site.name,
                    reason = "HTTP $code",
                    diagnostics = baseDiag.withDetail("HTTP $code"),
                )
            }
        }

        if (site.trustHttpStatus) {
            return when {
                code in site.okCodes -> CheckOutcome.Found(site.name, url, baseDiag)
                code in 200..299 -> CheckOutcome.Found(site.name, url, baseDiag)
                code == 404 -> CheckOutcome.Missing(site.name)
                else -> CheckOutcome.Error(
                    site = site.name,
                    reason = "HTTP $code",
                    diagnostics = baseDiag.withDetail("HTTP $code"),
                )
            }
        }

        // Without profile markers / trusted status codes, HTTP 2xx is not a confirmed FOUND.
        return when {
            code in site.okCodes || code in 200..299 -> CheckOutcome.Uncertain(
                site = site.name,
                url = url,
                reason = "нет маркера профиля",
                diagnostics = baseDiag.withDetail("нет маркера профиля"),
            )
            code == 404 -> CheckOutcome.Missing(site.name)
            else -> CheckOutcome.Error(
                site = site.name,
                reason = "HTTP $code",
                diagnostics = baseDiag.withDetail("HTTP $code"),
            )
        }
    }

    internal fun shouldRetry(code: Int): Boolean = code in RETRYABLE_CODES

    private suspend fun executeWithRetry(
        method: String,
        url: String,
        readBody: Boolean,
    ): HttpProbe {
        var delayMs = INITIAL_RETRY_DELAY_MS
        var lastError: Exception? = null
        repeat(maxRetries + 1) { attempt ->
            currentCoroutineContext().ensureActive()
            try {
                val probe = execute(method, url, readBody)
                if (!shouldRetry(probe.code) || attempt == maxRetries) {
                    return probe
                }
                val wait = resolveRetryDelayMs(
                    attemptDelayMs = delayMs,
                    retryAfterMs = probe.retryAfterMs,
                    jitterMs = jitterMs(delayMs) { bound -> kotlin.random.Random.nextInt(bound) },
                )
                delay(wait)
                delayMs = nextDelay(delayMs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                if (attempt == maxRetries) throw e
                val wait = resolveRetryDelayMs(
                    attemptDelayMs = delayMs,
                    retryAfterMs = null,
                    jitterMs = jitterMs(delayMs) { bound -> kotlin.random.Random.nextInt(bound) },
                )
                delay(wait)
                delayMs = nextDelay(delayMs)
            }
        }
        throw lastError ?: IllegalStateException("retry exhausted")
    }

    private suspend fun execute(method: String, url: String, readBody: Boolean): HttpProbe {
        val request = Request.Builder()
            .url(url)
            .method(method, null)
            .header("User-Agent", HttpHeaders.USER_AGENT)
            .header("Accept", HttpHeaders.ACCEPT_HTML)
            .header("Accept-Language", HttpHeaders.ACCEPT_LANGUAGE)
            .build()

        val call = client.newCall(request)
        val completionHandle = coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion {
            call.cancel()
        }
        try {
            call.execute().use { response ->
                val body = if (readBody) {
                    response.body?.string()?.take(MAX_BODY_CHARS)
                } else {
                    null
                }
                val retryAfterMs = parseRetryAfterMs(response.header("Retry-After"))
                return HttpProbe(
                    code = response.code,
                    body = body,
                    retryAfterMs = retryAfterMs,
                    finalUrl = response.request.url.toString(),
                    redirectCount = countRedirects(response),
                )
            }
        } finally {
            completionHandle?.dispose()
        }
    }

    private fun buildUsernameReport(
        username: String,
        results: List<CheckOutcome>,
        started: Long,
        cancelled: Boolean,
    ): OsintResult.UsernameReport {
        val categoriesByName = runCatching {
            OsintCatalog.usernameSites.associate { it.name to it.categories }
        }.getOrDefault(emptyMap())
        val found = results.mapNotNull { it as? CheckOutcome.Found }.map {
            SiteHit(
                site = it.site,
                url = it.url,
                categories = categoriesByName[it.site].orEmpty(),
                confidence = HitConfidence.CONFIRMED,
                diagnostics = it.diagnostics,
            )
        }
        val uncertain = results.mapNotNull { it as? CheckOutcome.Uncertain }.map {
            SiteHit(
                site = it.site,
                url = it.url,
                categories = categoriesByName[it.site].orEmpty(),
                confidence = HitConfidence.UNCERTAIN,
                diagnostics = it.diagnostics,
            )
        }
        val notFound = results.mapNotNull { it as? CheckOutcome.Missing }.map { it.site }
        val errors = results.mapNotNull { it as? CheckOutcome.Error }.map { err ->
            val diag = err.diagnostics?.formatBrief()?.takeIf { it.isNotBlank() }
            if (diag != null && !diag.contains(err.reason)) {
                "${err.site}: $diag"
            } else if (diag != null) {
                "${err.site}: $diag"
            } else {
                "${err.site}: ${err.reason}"
            }
        }
        return OsintResult.UsernameReport(
            username = username,
            found = found.sortedBy { it.site },
            uncertain = uncertain.sortedBy { it.site },
            notFound = notFound.sorted(),
            errors = errors.sorted(),
            elapsedMs = System.currentTimeMillis() - started,
            cancelled = cancelled,
        )
    }

    internal data class HttpProbe(
        val code: Int,
        val body: String?,
        val retryAfterMs: Long? = null,
        val finalUrl: String? = null,
        val redirectCount: Int = 0,
    ) {
        fun toDiagnostics(detail: String? = null): HttpDiagnostics =
            HttpDiagnostics(
                httpCode = code,
                finalUrl = finalUrl,
                redirectCount = redirectCount,
                detail = detail,
            )
    }

    internal sealed class CheckOutcome {
        data class Found(
            val site: String,
            val url: String,
            val diagnostics: HttpDiagnostics? = null,
        ) : CheckOutcome()

        data class Uncertain(
            val site: String,
            val url: String,
            val reason: String,
            val diagnostics: HttpDiagnostics? = null,
        ) : CheckOutcome()

        data class Missing(val site: String) : CheckOutcome()

        data class Error(
            val site: String,
            val reason: String,
            val diagnostics: HttpDiagnostics? = null,
        ) : CheckOutcome()
    }

    companion object {
        private const val MAX_BODY_CHARS = 256_000
        private const val INITIAL_RETRY_DELAY_MS = 400L
        private const val MAX_RETRY_DELAY_MS = 3500L
        private val RETRYABLE_CODES = setOf(408, 425, 429, 500, 502, 503, 504)

        internal fun countRedirects(response: Response): Int {
            var count = 0
            var prior = response.priorResponse
            while (prior != null) {
                count++
                prior = prior.priorResponse
            }
            return count
        }

        internal fun nextDelay(current: Long): Long =
            (current * 2).coerceAtMost(MAX_RETRY_DELAY_MS)

        /** Prefer Retry-After when present; otherwise exponential backoff (+ optional jitter). */
        internal fun resolveRetryDelayMs(
            attemptDelayMs: Long,
            retryAfterMs: Long? = null,
            jitterMs: Long = 0L,
        ): Long {
            val base = retryAfterMs ?: attemptDelayMs
            return (base + jitterMs.coerceAtLeast(0L)).coerceIn(0L, MAX_RETRY_DELAY_MS * 3)
        }

        internal fun parseRetryAfterMs(header: String?): Long? {
            if (header.isNullOrBlank()) return null
            val seconds = header.trim().toLongOrNull() ?: return null
            if (seconds < 0L) return null
            return (seconds * 1000L).coerceAtMost(MAX_RETRY_DELAY_MS * 3)
        }

        internal fun jitterMs(baseDelayMs: Long, randomBoundExclusive: (Int) -> Int): Long {
            val spread = (baseDelayMs * 0.25).toLong().coerceAtLeast(0L)
            if (spread <= 0L) return 0L
            return randomBoundExclusive((spread + 1).toInt()).toLong()
        }
    }
}
