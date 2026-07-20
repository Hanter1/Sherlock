package com.sherlock.bot.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class OsintEngine(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build(),
) {
    private val usernamePattern = Pattern.compile("^[A-Za-z0-9._-]{2,32}$")

    suspend fun searchUsername(raw: String): OsintResult.UsernameReport = withContext(Dispatchers.IO) {
        val username = raw.trim().removePrefix("@")
        require(usernamePattern.matcher(username).matches()) {
            "Никнейм должен быть 2–32 символа: буквы, цифры, . _ -"
        }

        val started = System.currentTimeMillis()
        val results = coroutineScope {
            OsintCatalog.usernameSites.map { site ->
                async { checkSite(site, username) }
            }.awaitAll()
        }

        val found = results.mapNotNull { it as? CheckOutcome.Found }.map { SiteHit(it.site, it.url) }
        val notFound = results.mapNotNull { it as? CheckOutcome.Missing }.map { it.site }
        val errors = results.mapNotNull { it as? CheckOutcome.Error }.map { "${it.site}: ${it.reason}" }

        OsintResult.UsernameReport(
            username = username,
            found = found.sortedBy { it.site },
            notFound = notFound.sorted(),
            errors = errors.sorted(),
            elapsedMs = System.currentTimeMillis() - started,
        )
    }

    fun analyzePhone(raw: String): OsintResult.InfoReport {
        val digits = raw.filter { it.isDigit() }
        val normalized = when {
            digits.length == 11 && (digits.startsWith("7") || digits.startsWith("8")) ->
                "7" + digits.drop(1)
            digits.length == 10 -> "7$digits"
            else -> digits
        }

        if (normalized.length != 11 || !normalized.startsWith("7")) {
            return OsintResult.InfoReport(
                title = "Телефон",
                body = "Не распознал номер. Пришлите в формате +7XXXXXXXXXX.",
            )
        }

        val code = normalized.substring(1, 4)
        val operatorHint = guessRuOperator(code)
        val masked = "+$normalized"

        return OsintResult.InfoReport(
            title = "Телефон · открытые данные",
            body = buildString {
                appendLine("Номер: `$masked`")
                appendLine("Страна: Россия / Казахстан (код +7)")
                appendLine("DEF-код: $code")
                appendLine("Оператор (эвристика): $operatorHint")
                appendLine()
                appendLine("Что проверено здесь:")
                appendLine("• нормализация и базовая гео/оператор-эвристика")
                appendLine()
                appendLine("Чего нет в приложении (намеренно):")
                appendLine("• ФИО владельца, утечки, GetContact и закрытые базы")
                appendLine()
                appendLine("Для расследований используйте только законные открытые источники и с согласия субъекта, где это требуется.")
            }.trim(),
        )
    }

    fun analyzeEmail(raw: String): OsintResult.InfoReport {
        val email = raw.trim().lowercase()
        val ok = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
            .matcher(email).matches()
        if (!ok) {
            return OsintResult.InfoReport(
                title = "Email",
                body = "Похоже, это не email. Пример: name@example.com",
            )
        }
        val domain = email.substringAfter("@")
        val local = email.substringBefore("@")
        return OsintResult.InfoReport(
            title = "Email · открытые данные",
            body = buildString {
                appendLine("Адрес: `$email`")
                appendLine("Локальная часть: $local (${local.length} симв.)")
                appendLine("Домен: $domain")
                appendLine("MX/WHOIS: запросите отдельно через публичные DNS/WHOIS сервисы")
                appendLine()
                appendLine("Полный «пробив» по утечкам в приложение не встроен — только публичная разборка формата.")
                appendLine("Подсказка: проверьте ник на том же имени через «Поиск по нику».")
            }.trim(),
        )
    }

    fun analyzeFullName(raw: String): OsintResult.InfoReport {
        val name = raw.trim().replace(Regex("\\s+"), " ")
        val parts = name.split(" ")
        if (parts.size < 2 || name.length < 5) {
            return OsintResult.InfoReport(
                title = "ФИО",
                body = "Пришлите минимум фамилию и имя, например: `Иванов Иван`.",
            )
        }
        val query = parts.joinToString("+")
        return OsintResult.InfoReport(
            title = "ФИО · публичные ссылки",
            body = buildString {
                appendLine("Запрос: *$name*")
                appendLine()
                appendLine("Открытые точки входа (откройте вручную):")
                appendLine("• Google: https://www.google.com/search?q=$query")
                appendLine("• Yandex: https://yandex.ru/search/?text=$query")
                appendLine("• VK: https://vk.com/search?c%5Bq%5D=$query&c%5Bsection%5D=people")
                appendLine()
                appendLine("Приложение не агрегирует персональные досье и не лезет в закрытые реестры.")
            }.trim(),
        )
    }

    private fun checkSite(site: OsintSite, username: String): CheckOutcome {
        val url = site.urlTemplate.replace("{user}", username)
        return try {
            val method = if (site.useHead) "HEAD" else "GET"
            var code = execute(method, url)
            // Some CDNs reject HEAD — retry GET
            if (code == 405 || code == 403 || code == 400) {
                code = execute("GET", url)
            }
            when {
                site.errorCodes.isNotEmpty() && code in site.errorCodes ->
                    CheckOutcome.Missing(site.name)
                code in site.okCodes ->
                    CheckOutcome.Found(site.name, url)
                code in 200..299 ->
                    CheckOutcome.Found(site.name, url)
                code == 404 ->
                    CheckOutcome.Missing(site.name)
                else ->
                    CheckOutcome.Error(site.name, "HTTP $code")
            }
        } catch (e: Exception) {
            CheckOutcome.Error(site.name, e.message ?: "network error")
        }
    }

    private fun execute(method: String, url: String): Int {
        val request = Request.Builder()
            .url(url)
            .method(method, null)
            .header(
                "User-Agent",
                "SherlockBot/1.0 (Android; public OSINT; +https://github.com/sherlock-project/sherlock)",
            )
            .header("Accept", "text/html,application/xhtml+xml")
            .build()
        client.newCall(request).execute().use { response ->
            return response.code
        }
    }

    private fun guessRuOperator(defCode: String): String = when (defCode) {
        in listOf("900", "901", "902", "904", "908", "950", "951", "952", "953", "958") ->
            "вероятно Beeline / смежные"
        in listOf("910", "911", "912", "913", "914", "915", "916", "917", "918", "919", "980", "981", "982", "983", "984", "985", "986", "987", "988", "989") ->
            "вероятно MTS / смежные"
        in listOf("920", "921", "922", "923", "924", "925", "926", "927", "928", "929", "930", "931", "932", "933", "934", "936", "937", "938", "939") ->
            "вероятно Megafon / Yota / смежные"
        in listOf("903", "905", "906", "909", "960", "961", "962", "963", "964", "965", "966", "967", "968", "969") ->
            "вероятно Tele2 / t2 / смежные"
        else -> "не определён по простому справочнику DEF"
    }

    private sealed class CheckOutcome {
        data class Found(val site: String, val url: String) : CheckOutcome()
        data class Missing(val site: String) : CheckOutcome()
        data class Error(val site: String, val reason: String) : CheckOutcome()
    }
}
