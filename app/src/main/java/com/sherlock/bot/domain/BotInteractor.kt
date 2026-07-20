package com.sherlock.bot.domain

import com.sherlock.bot.data.BotAction
import com.sherlock.bot.data.ChatMessage
import com.sherlock.bot.data.OsintEngine
import com.sherlock.bot.data.OsintResult
import com.sherlock.bot.data.SearchMode
import java.util.UUID

class BotInteractor(
    private val osint: OsintEngine = OsintEngine(),
) {
    fun welcome(): ChatMessage = bot(
        text = """
            Sherlock Bot — OSINT-помощник в виде отдельного приложения.
            
            Ищу только по открытым источникам (публичные профили, эвристики).
            Закрытые базы, утечки и «пробив ФИО владельца номера» здесь не используются.
            
            Выберите действие или напишите команду:
            /start · /help · /username · /phone · /email · /name
        """.trimIndent(),
        actions = mainMenu(),
    )

    fun help(): ChatMessage = bot(
        text = """
            Команды:
            /username <ник> — проверить ник на 20 публичных площадках
            /phone <номер> — нормализация + эвристика оператора (+7)
            /email <почта> — разбор адреса
            /name <ФИО> — ссылки на публичный поиск
            
            Можно нажать кнопку меню или просто прислать ник/номер — я попробую угадать тип запроса.
        """.trimIndent(),
        actions = mainMenu(),
    )

    fun askFor(mode: SearchMode): ChatMessage = when (mode) {
        SearchMode.USERNAME -> bot("Пришлите никнейм (без @ или с @). Пример: `durov`")
        SearchMode.PHONE -> bot("Пришлите телефон. Пример: `+79001234567`")
        SearchMode.EMAIL -> bot("Пришлите email. Пример: `name@mail.ru`")
        SearchMode.FULL_NAME -> bot("Пришлите ФИО. Пример: `Иванов Иван`")
        SearchMode.NONE -> help()
    }

    suspend fun handleUserText(text: String, pendingMode: SearchMode): Pair<List<ChatMessage>, SearchMode> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList<ChatMessage>() to pendingMode

        when {
            trimmed.equals("/start", true) || trimmed.equals("старт", true) ->
                return listOf(welcome()) to SearchMode.NONE
            trimmed.equals("/help", true) || trimmed.equals("помощь", true) ->
                return listOf(help()) to SearchMode.NONE
            trimmed.startsWith("/username", true) -> {
                val arg = trimmed.substringAfter(" ", "").trim()
                return if (arg.isBlank()) {
                    listOf(askFor(SearchMode.USERNAME)) to SearchMode.USERNAME
                } else {
                    listOf(formatResult(osint.searchUsername(arg))) to SearchMode.NONE
                }
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

        val mode = pendingMode.takeIf { it != SearchMode.NONE } ?: detectMode(trimmed)
        val result = runCatching {
            when (mode) {
                SearchMode.USERNAME -> osint.searchUsername(trimmed)
                SearchMode.PHONE -> osint.analyzePhone(trimmed)
                SearchMode.EMAIL -> osint.analyzeEmail(trimmed)
                SearchMode.FULL_NAME -> osint.analyzeFullName(trimmed)
                SearchMode.NONE -> null
            }
        }.fold(
            onSuccess = { it },
            onFailure = {
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

    fun handleAction(actionId: String): Pair<ChatMessage, SearchMode> = when (actionId) {
        "username" -> askFor(SearchMode.USERNAME) to SearchMode.USERNAME
        "phone" -> askFor(SearchMode.PHONE) to SearchMode.PHONE
        "email" -> askFor(SearchMode.EMAIL) to SearchMode.EMAIL
        "name" -> askFor(SearchMode.FULL_NAME) to SearchMode.FULL_NAME
        "help" -> help() to SearchMode.NONE
        "menu" -> welcome() to SearchMode.NONE
        else -> help() to SearchMode.NONE
    }

    private fun detectMode(text: String): SearchMode = when {
        text.contains("@") && text.contains(".") && !text.startsWith("@") -> SearchMode.EMAIL
        text.filter { it.isDigit() }.length >= 10 -> SearchMode.PHONE
        text.trim().split(Regex("\\s+")).size >= 2 && text.any { it.isCyrillicLetter() || it.isLetter() } &&
            text.any { it.isWhitespace() } -> SearchMode.FULL_NAME
        text.removePrefix("@").matches(Regex("^[A-Za-z0-9._-]{2,32}$")) -> SearchMode.USERNAME
        else -> SearchMode.NONE
    }

    private fun formatResult(result: OsintResult): ChatMessage = when (result) {
        is OsintResult.UsernameReport -> {
            val body = buildString {
                appendLine("Отчёт по нику `${result.username}`")
                appendLine("Проверено площадок: ${result.found.size + result.notFound.size + result.errors.size}")
                appendLine("Найдено: *${result.found.size}* · нет: ${result.notFound.size} · ошибки: ${result.errors.size}")
                appendLine("Время: ${result.elapsedMs} мс")
                appendLine()
                if (result.found.isNotEmpty()) {
                    appendLine("Найдены профили:")
                    result.found.forEach { appendLine("• ${it.site}: ${it.url}") }
                    appendLine()
                } else {
                    appendLine("Публичные профили не найдены.")
                    appendLine()
                }
                if (result.errors.isNotEmpty()) {
                    appendLine("Недоступно сейчас:")
                    result.errors.take(5).forEach { appendLine("• $it") }
                    if (result.errors.size > 5) appendLine("• …ещё ${result.errors.size - 5}")
                }
            }.trim()
            bot(body, actions = mainMenu())
        }
        is OsintResult.InfoReport -> bot(
            "${result.title}\n\n${result.body}",
            actions = mainMenu(),
        )
    }

    private fun mainMenu(): List<BotAction> = listOf(
        BotAction("username", "Никнейм"),
        BotAction("phone", "Телефон"),
        BotAction("email", "Email"),
        BotAction("name", "ФИО"),
        BotAction("help", "Помощь"),
    )

    private fun bot(text: String, actions: List<BotAction> = emptyList()) = ChatMessage(
        id = UUID.randomUUID().toString(),
        text = text,
        fromBot = true,
        actions = actions,
    )

    private fun Char.isCyrillicLetter(): Boolean = this in '\u0400'..'\u04FF'
}
