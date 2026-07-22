package com.sherlock.bot.domain

import com.sherlock.bot.data.BotAction
import com.sherlock.bot.data.CacheAge
import com.sherlock.bot.data.ChatHistoryCodec
import com.sherlock.bot.data.ChatMessage
import com.sherlock.bot.data.OsintCatalog
import com.sherlock.bot.data.OsintEngine
import com.sherlock.bot.data.OsintResult
import com.sherlock.bot.data.SearchMode
import com.sherlock.bot.data.SiteCategories
import com.sherlock.bot.data.SiteCheckProgress
import com.sherlock.bot.data.SiteCheckStatus
import com.sherlock.bot.data.SiteHit
import com.sherlock.bot.data.UsernameQueue
import com.sherlock.bot.data.UsernameReportFilter
import com.sherlock.bot.data.UsernameReportMerge
import java.util.LinkedHashMap
import java.util.UUID

class BotInteractor(
    private val osint: OsintEngine = OsintEngine(),
    private val isOnline: () -> Boolean = { true },
) {
    /** Last username scan/report — convenience fallback; prefer [reportFor]. */
    var lastUsernameReport: OsintResult.UsernameReport? = null
        private set

    private val reportsById = LinkedHashMap<String, OsintResult.UsernameReport>()

    fun reportFor(reportId: String?): OsintResult.UsernameReport? {
        if (!reportId.isNullOrBlank()) {
            reportsById[reportId]?.let { return it }
        }
        return lastUsernameReport
    }

    fun allReports(): Map<String, OsintResult.UsernameReport> = reportsById.toMap()

    fun replaceReports(reports: Map<String, OsintResult.UsernameReport>) {
        reportsById.clear()
        reportsById.putAll(reports)
        lastUsernameReport = reports.values.lastOrNull()
    }

    fun clearReports() {
        reportsById.clear()
        lastUsernameReport = null
    }

    fun removeReports(ids: Collection<String>) {
        ids.forEach { reportsById.remove(it) }
        lastUsernameReport = reportsById.values.lastOrNull()
    }

    private fun putReport(reportId: String, report: OsintResult.UsernameReport) {
        val isNew = !reportsById.containsKey(reportId)
        reportsById[reportId] = report
        if (isNew) {
            lastUsernameReport = report
        }
        while (reportsById.size > ChatHistoryCodec.MAX_REPORTS) {
            val oldest = reportsById.keys.firstOrNull() ?: break
            reportsById.remove(oldest)
        }
    }
    fun welcome(): ChatMessage = bot(
        text = """
            Sherlock Bot — OSINT-помощник с упором на Беларусь.
            
            Ищу только по открытым источникам (публичные профили, DNS, эвристики).
            Закрытые базы, утечки и «пробив ФИО владельца номера» здесь не используются.
            
            Выберите действие или напишите команду:
            /start · /help · /username · /compare · /phone · /email · /name · /about
        """.trimIndent(),
        actions = mainMenu(),
    )

    fun help(): ChatMessage = bot(
        text = """
            Команды:
            /username <ник> — проверка (несколько через пробел/запятую, до ${UsernameQueue.MAX_NICKS})
            /compare <ник1> <ник2> — общие / только A / только B
            /phone <номер> — Беларусь +375 (приоритет), также +7/+380/+1/+44
            /email <почта> — MX + SPF/DMARC + Gravatar (после согласия; тумблеры в настройках)
            /name <ФИО> — поиск Google BY / Yandex BY / VK
            /clear — очистить историю чата
            /about — версия приложения и каталогов
            /settings — параллелизм, пресет, кэш, remote-каталог
            
            Под отчётом: фильтры, «Закрепить», «Повторить без кэша», «Добить ошибки», экспорт MD/JSON.
            Поиск по истории — лупа в шапке.
            Каталог площадок: ${siteCount()} шт. (${OsintCatalog.info().source})
        """.trimIndent(),
        actions = mainMenu(),
    )

    fun askFor(mode: SearchMode): ChatMessage = when (mode) {
        SearchMode.USERNAME -> bot(
            "Пришлите никнейм (без @ или с @). Пример: `durov`\n" +
                "Несколько ников: `durov, telegram` (до ${UsernameQueue.MAX_NICKS})",
        )
        SearchMode.PHONE -> bot("Пришлите телефон. Пример РБ: `+375291234567` (также +7 / +380 / +1 / +44)")
        SearchMode.EMAIL -> bot("Пришлите email. Пример: `name@mail.ru`")
        SearchMode.FULL_NAME -> bot("Пришлите ФИО. Пример: `Іваноў Іван` или `Иванов Иван`")
        SearchMode.COMPARE -> bot("Пришлите два ника через пробел. Пример: `durov telegram`")
        SearchMode.NONE -> help()
    }

    fun historyCleared(): ChatMessage = bot(
        text = "История очищена. Начнём заново.",
        actions = mainMenu(),
    )

    fun offlineMessage(): ChatMessage = bot(
        text = "Нет сети. Подключитесь к интернету и повторите запрос.\n\nОфлайн доступны: /phone и /name (локально).",
        actions = mainMenu(),
    )

    suspend fun handleUserText(
        text: String,
        pendingMode: SearchMode,
        onScanProgress: suspend (SiteCheckProgress) -> Unit = {},
    ): Pair<List<ChatMessage>, SearchMode> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList<ChatMessage>() to pendingMode

        when {
            trimmed.equals("/start", true) || trimmed.equals("старт", true) ->
                return listOf(welcome()) to SearchMode.NONE
            trimmed.equals("/help", true) || trimmed.equals("помощь", true) ->
                return listOf(help()) to SearchMode.NONE
            trimmed.equals("/about", true) || trimmed.equals("о приложении", true) ->
                return emptyList<ChatMessage>() to SearchMode.NONE
            trimmed.equals("/settings", true) || trimmed.equals("настройки", true) ->
                return emptyList<ChatMessage>() to SearchMode.NONE
            trimmed.equals("/clear", true) || trimmed.equals("очистить", true) ->
                return listOf(historyCleared()) to SearchMode.NONE
            trimmed.startsWith("/username", true) -> {
                val arg = trimmed.substringAfter(" ", "").trim()
                return if (arg.isBlank()) {
                    listOf(askFor(SearchMode.USERNAME)) to SearchMode.USERNAME
                } else {
                    requireOnline()?.let { return listOf(it) to SearchMode.NONE }
                    handleUsernameArg(arg, onScanProgress)
                }
            }
            trimmed.startsWith("/compare", true) -> {
                val arg = trimmed.substringAfter(" ", "").trim()
                return handleCompareArg(arg, onScanProgress)
            }
            trimmed.startsWith("/phone", true) -> {
                val arg = trimmed.substringAfter(" ", "").trim()
                return if (arg.isBlank()) {
                    listOf(askFor(SearchMode.PHONE)) to SearchMode.PHONE
                } else {
                    listOf(formatResult(osint.analyzePhone(arg))) to SearchMode.NONE
                }
            }
            trimmed.startsWith("/email", true) -> {
                val arg = trimmed.substringAfter(" ", "").trim()
                return if (arg.isBlank()) {
                    listOf(askFor(SearchMode.EMAIL)) to SearchMode.EMAIL
                } else {
                    requireOnline()?.let { return listOf(it) to SearchMode.NONE }
                    listOf(formatResult(osint.analyzeEmail(arg))) to SearchMode.NONE
                }
            }
            trimmed.startsWith("/name", true) -> {
                val arg = trimmed.substringAfter(" ", "").trim()
                return if (arg.isBlank()) {
                    listOf(askFor(SearchMode.FULL_NAME)) to SearchMode.FULL_NAME
                } else {
                    listOf(formatResult(osint.analyzeFullName(arg))) to SearchMode.NONE
                }
            }
        }

        val mode = pendingMode.takeIf { it != SearchMode.NONE } ?: QueryClassifier.detectMode(trimmed)
        if (mode == SearchMode.USERNAME || mode == SearchMode.EMAIL || mode == SearchMode.COMPARE) {
            requireOnline()?.let { return listOf(it) to SearchMode.NONE }
        }

        if (mode == SearchMode.COMPARE) {
            return handleCompareArg(trimmed, onScanProgress)
        }

        val result = runCatching {
            when (mode) {
                SearchMode.USERNAME -> return handleUsernameArg(trimmed, onScanProgress)
                SearchMode.PHONE -> osint.analyzePhone(trimmed)
                SearchMode.EMAIL -> osint.analyzeEmail(trimmed)
                SearchMode.FULL_NAME -> osint.analyzeFullName(trimmed)
                SearchMode.COMPARE, SearchMode.NONE -> null
            }
        }.fold(
            onSuccess = { it },
            onFailure = {
                if (it is kotlinx.coroutines.CancellationException) throw it
                return listOf(
                    bot("Не вышло: ${it.message ?: "ошибка"}\n\nПопробуйте ещё раз или /help"),
                ) to SearchMode.NONE
            },
        )

        return if (result == null) {
            listOf(
                bot(
                    "Не понял запрос. Выберите действие:",
                    actions = mainMenu(),
                ),
            ) to SearchMode.NONE
        } else {
            listOf(formatResult(result)) to SearchMode.NONE
        }
    }

    fun handleAction(actionId: String): Pair<ChatMessage, SearchMode>? = when (actionId) {
        "username" -> askFor(SearchMode.USERNAME) to SearchMode.USERNAME
        "compare" -> askFor(SearchMode.COMPARE) to SearchMode.COMPARE
        "phone" -> askFor(SearchMode.PHONE) to SearchMode.PHONE
        "email" -> askFor(SearchMode.EMAIL) to SearchMode.EMAIL
        "name" -> askFor(SearchMode.FULL_NAME) to SearchMode.FULL_NAME
        "help" -> help() to SearchMode.NONE
        "menu" -> welcome() to SearchMode.NONE
        "about" -> null // ViewModel opens About sheet
        "settings" -> null // ViewModel opens Settings
        "clear_history" -> null // handled by ViewModel
        "report_found", "report_no_errors", "report_full" -> null
        "rescan", "rescan_errors", "export_md", "export_json", "pin_report" -> null
        else -> help() to SearchMode.NONE
    }

    private suspend fun handleCompareArg(
        arg: String,
        onScanProgress: suspend (SiteCheckProgress) -> Unit,
    ): Pair<List<ChatMessage>, SearchMode> {
        if (arg.isBlank()) {
            return listOf(askFor(SearchMode.COMPARE)) to SearchMode.COMPARE
        }
        val parts = arg.split(Regex("\\s+")).map { it.removePrefix("@") }.filter { it.isNotBlank() }
        if (parts.size < 2) {
            return listOf(
                bot("Нужны два ника. Пример: `/compare durov telegram`"),
            ) to SearchMode.COMPARE
        }
        requireOnline()?.let { return listOf(it) to SearchMode.NONE }
        val a = parts[0]
        val b = parts[1]
        if (a.equals(b, ignoreCase = true)) {
            return listOf(bot("Ники должны отличаться.")) to SearchMode.NONE
        }
        return listOf(formatResult(osint.compareUsernames(a, b, onScanProgress))) to SearchMode.NONE
    }

    private suspend fun handleUsernameArg(
        arg: String,
        onScanProgress: suspend (SiteCheckProgress) -> Unit,
    ): Pair<List<ChatMessage>, SearchMode> {
        val parsed = UsernameQueue.parse(arg)
        if (parsed.nicks.isEmpty()) {
            val hint = if (parsed.rejected.isNotEmpty()) {
                "Некорректные ники: ${parsed.rejected.joinToString()}"
            } else {
                "Нужен никнейм 2–32 символа: буквы, цифры, . _ -"
            }
            return listOf(bot(hint)) to SearchMode.USERNAME
        }
        val replies = mutableListOf<ChatMessage>()
        if (parsed.rejected.isNotEmpty()) {
            replies += bot("Пропущены: ${parsed.rejected.joinToString()}")
        }
        if (parsed.truncated) {
            replies += bot("Очередь обрезана до ${UsernameQueue.MAX_NICKS} ников.")
        }
        if (parsed.nicks.size > 1) {
            replies += bot("Очередь: ${parsed.nicks.joinToString(" · ")}")
        }
        parsed.nicks.forEachIndexed { index, nick ->
            val queueIndex = index + 1
            val queueTotal = parsed.nicks.size
            val report = osint.searchUsername(
                raw = nick,
                onProgress = { progress ->
                    onScanProgress(
                        progress.copy(
                            username = nick,
                            queueIndex = if (queueTotal > 1) queueIndex else null,
                            queueTotal = if (queueTotal > 1) queueTotal else null,
                        ),
                    )
                },
            )
            replies += formatResult(report)
        }
        return replies to SearchMode.NONE
    }

    suspend fun rescanUsername(
        username: String,
        onScanProgress: suspend (SiteCheckProgress) -> Unit = {},
    ): ChatMessage {
        requireOnline()?.let { return it }
        return formatResult(osint.searchUsername(username, onScanProgress, bypassCache = true))
    }

    suspend fun rescanFailedSites(
        report: OsintResult.UsernameReport,
        reportId: String,
        onScanProgress: suspend (SiteCheckProgress) -> Unit = {},
    ): ChatMessage {
        requireOnline()?.let { return it }
        val merged = osint.rescanFailedSites(report, includeUncertain = true, onScanProgress)
        return formatUsernameReport(merged, reportId = reportId)
    }

    fun clearUsernameCaches() {
        osint.clearUsernameCaches()
    }

    fun usernameCacheSize(): Int = osint.usernameCacheSize()

    fun usernameCacheSummary(): String = osint.usernameCacheSummary()

    fun formatScanProgress(
        username: String,
        lines: List<String>,
        done: Int,
        total: Int,
        queueIndex: Int? = null,
        queueTotal: Int? = null,
    ): String {
        return buildString {
            if (queueIndex != null && queueTotal != null && queueTotal > 1) {
                appendLine("Очередь $queueIndex/$queueTotal · `$username`… $done/$total")
            } else {
                appendLine("Сканирую `$username`… $done/$total")
            }
            appendLine("Нажмите ■ Стоп, чтобы прервать")
            if (lines.isNotEmpty()) {
                appendLine()
                lines.takeLast(12).forEach { appendLine(it) }
            }
        }.trim()
    }

    fun progressLine(progress: SiteCheckProgress): String = when (progress.status) {
        SiteCheckStatus.FOUND -> "✓ ${progress.site}"
        SiteCheckStatus.UNCERTAIN -> "~ ${progress.site} — ?"
        SiteCheckStatus.MISSING -> "· ${progress.site} — нет"
        SiteCheckStatus.ERROR -> "? ${progress.site} — ${progress.reason ?: "ошибка"}"
    }

    fun cancelledScanMessage(
        username: String,
        progress: List<SiteCheckProgress>,
    ): ChatMessage {
        if (progress.isEmpty()) {
            return bot("Операция остановлена.", actions = mainMenu())
        }
        val found = progress.filter { it.status == SiteCheckStatus.FOUND }
        val uncertain = progress.count { it.status == SiteCheckStatus.UNCERTAIN }
        val missing = progress.count { it.status == SiteCheckStatus.MISSING }
        val errors = progress.count { it.status == SiteCheckStatus.ERROR }
        val body = buildString {
            appendLine("—— Сводка ——")
            appendLine("Скан остановлен · `$username`")
            appendLine(
                "Проверено: ${progress.size} · найдено: *${found.size}* · " +
                    "неуверенно: $uncertain · нет: $missing · ошибки: $errors",
            )
            appendLine()
            if (found.isNotEmpty()) {
                appendLine("Уже найдены:")
                found.forEach { p ->
                    appendLine("• ${p.site}${p.url?.let { ": $it" } ?: ""}")
                }
            } else {
                appendLine("До остановки подтверждённые профили не найдены.")
            }
        }.trim()
        return bot(body, actions = reportActions())
    }

    fun formatUsernameReport(
        result: OsintResult.UsernameReport,
        filter: UsernameReportFilter = UsernameReportFilter.FULL,
        reportId: String = UUID.randomUUID().toString(),
    ): ChatMessage {
        putReport(reportId, result)
        val total = result.found.size + result.uncertain.size + result.notFound.size + result.errors.size
        val body = buildString {
            appendLine("—— Сводка ——")
            when {
                result.fromCache -> {
                    val age = CacheAge.formatCacheLine(result.cacheSavedAtMs, result.cacheTtlMs)
                    if (age != null) {
                        appendLine("Ник `${result.username}` · из кэша · $age")
                    } else {
                        appendLine("Ник `${result.username}` · из кэша")
                    }
                }
                result.cancelled -> appendLine("Скан остановлен · частичный отчёт по `${result.username}`")
                else -> appendLine("Ник `${result.username}`")
            }
            appendLine(
                "Найдено: *${result.found.size}* · неуверенно: ${result.uncertain.size} · " +
                    "нет: ${result.notFound.size} · ошибки: ${result.errors.size} · всего: $total",
            )
            if (!result.fromCache) {
                appendLine("Время: ${SiteCategories.formatElapsed(result.elapsedMs)}")
            }
            appendCategoryBreakdown(result.found)
            result.previousDiff?.let {
                appendLine()
                appendLine(it)
            }
            appendLine()

            when (filter) {
                UsernameReportFilter.FOUND_ONLY -> {
                    appendLine("Фильтр: только найденные (подтверждённые)")
                    appendLine()
                }
                UsernameReportFilter.HIDE_ERRORS -> {
                    appendLine("Фильтр: без ошибок / блокировок")
                    appendLine()
                }
                UsernameReportFilter.FULL -> Unit
            }

            if (result.found.isNotEmpty()) {
                appendLine("Найдены профили:")
                appendFoundGrouped(result.found)
                appendLine()
            } else {
                appendLine("Подтверждённые публичные профили не найдены.")
                appendLine()
            }

            if (filter != UsernameReportFilter.FOUND_ONLY && result.uncertain.isNotEmpty()) {
                appendLine("Неуверенно (нет маркера профиля):")
                result.uncertain.forEach { hit ->
                    val diag = hit.diagnostics?.formatBrief()?.takeIf { it.isNotBlank() }
                    if (diag != null) {
                        appendLine("• ${hit.site}: ${hit.url} ($diag)")
                    } else {
                        appendLine("• ${hit.site}: ${hit.url}")
                    }
                }
                appendLine()
            }

            if (filter == UsernameReportFilter.FULL && result.errors.isNotEmpty()) {
                appendLine("Недоступно / блок:")
                result.errors.take(8).forEach { appendLine("• $it") }
                if (result.errors.size > 8) appendLine("• …ещё ${result.errors.size - 8}")
            }
        }.trim()
        return bot(body, actions = usernameReportActions(result), reportId = reportId)
    }

    private fun StringBuilder.appendCategoryBreakdown(found: List<SiteHit>) {
        if (found.isEmpty()) return
        val counts = linkedMapOf<String, Int>()
        for (hit in found) {
            val tags = hit.categories.ifEmpty { listOf("other") }
            for (tag in tags) {
                val label = SiteCategories.label(tag)
                counts[label] = (counts[label] ?: 0) + 1
            }
        }
        if (counts.isNotEmpty()) {
            appendLine(
                "По категориям: " + counts.entries.joinToString(" · ") { "${it.key} ${it.value}" },
            )
        }
    }

    private fun StringBuilder.appendFoundGrouped(found: List<SiteHit>) {
        val groups = linkedMapOf<String, MutableList<SiteHit>>()
        for (hit in found) {
            val key = hit.categories.firstOrNull()?.let { SiteCategories.label(it) } ?: "прочее"
            groups.getOrPut(key) { mutableListOf() }.add(hit)
        }
        for ((label, hits) in groups) {
            appendLine("[$label]")
            hits.forEach { appendLine("• ${it.site}: ${it.url}") }
        }
    }

    private fun requireOnline(): ChatMessage? =
        if (isOnline()) null else offlineMessage()

    private fun siteCount(): Int = runCatching { OsintCatalog.usernameSites.size }.getOrDefault(0)

    fun formatResult(result: OsintResult): ChatMessage = when (result) {
        is OsintResult.UsernameReport -> formatUsernameReport(result, UsernameReportFilter.FULL)
        is OsintResult.InfoReport -> bot(
            "${result.title}\n\n${result.body}",
            actions = reportActions(),
        )
    }

    fun filterFromAction(actionId: String): UsernameReportFilter? = when (actionId) {
        "report_found" -> UsernameReportFilter.FOUND_ONLY
        "report_no_errors" -> UsernameReportFilter.HIDE_ERRORS
        "report_full" -> UsernameReportFilter.FULL
        else -> null
    }

    private fun mainMenu(): List<BotAction> = listOf(
        BotAction("username", "Никнейм"),
        BotAction("compare", "Сравнить"),
        BotAction("phone", "Телефон"),
        BotAction("email", "Email"),
        BotAction("name", "ФИО"),
        BotAction("help", "Помощь"),
        BotAction("settings", "Настройки"),
        BotAction("about", "О приложении"),
        BotAction("clear_history", "Очистить чат"),
    )

    private fun usernameReportActions(report: OsintResult.UsernameReport): List<BotAction> {
        val actions = mutableListOf(
            BotAction("rescan", "Повторить без кэша"),
        )
        if (UsernameReportMerge.failedSiteNames(report).isNotEmpty()) {
            actions.add(BotAction("rescan_errors", "Добить ошибки"))
        }
        actions += listOf(
            BotAction("pin_report", "Закрепить"),
            BotAction("report_found", "Только найденные"),
            BotAction("report_no_errors", "Без ошибок"),
            BotAction("report_full", "Полный отчёт"),
            BotAction("export_md", "Экспорт MD"),
            BotAction("export_json", "Экспорт JSON"),
            BotAction("share", "Поделиться"),
            BotAction("copy", "Копировать"),
            BotAction("username", "Никнейм"),
            BotAction("clear_history", "Очистить чат"),
        )
        return actions
    }

    private fun reportActions(): List<BotAction> = listOf(
        BotAction("share", "Поделиться"),
        BotAction("copy", "Копировать"),
        BotAction("export_md", "Экспорт MD"),
        BotAction("pin_report", "Закрепить"),
        BotAction("username", "Никнейм"),
        BotAction("compare", "Сравнить"),
        BotAction("phone", "Телефон"),
        BotAction("email", "Email"),
        BotAction("name", "ФИО"),
        BotAction("clear_history", "Очистить чат"),
    )

    private fun bot(
        text: String,
        actions: List<BotAction> = emptyList(),
        reportId: String? = null,
    ) = ChatMessage(
        id = UUID.randomUUID().toString(),
        text = text,
        fromBot = true,
        actions = actions,
        reportId = reportId,
    )
}
