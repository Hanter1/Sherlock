package com.sherlock.bot.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Filter chat messages by free-text query and build journal summaries.
 */
object ChatSearch {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun filter(messages: List<ChatMessage>, query: String): List<ChatMessage> {
        val q = query.trim()
        if (q.isEmpty()) return messages
        return messages.filter { it.text.contains(q, ignoreCase = true) }
    }

    fun preview(text: String, maxChars: Int = 96): String {
        val oneLine = text.replace('\n', ' ').trim()
        if (oneLine.length <= maxChars) return oneLine
        return oneLine.take(maxChars - 1).trimEnd() + "…"
    }

    fun isReportLike(text: String): Boolean =
        text.contains("—— Сводка ——") ||
            text.contains("—— Сравнение ников ——") ||
            text.startsWith("Email ·") ||
            text.startsWith("Телефон ·") ||
            text.startsWith("ФИО")

    /** User queries and finished reports — not welcome / prompts / status. */
    fun isJournalWorthy(message: ChatMessage): Boolean {
        if (message.isTyping || message.id == ChatHistoryCodec.STATUS_MESSAGE_ID) return false
        if (!message.fromBot) return true
        return isReportLike(message.text)
    }

    fun formatTime(timestampMs: Long): String =
        timeFormat.format(Date(timestampMs))

    /**
     * One-line journal title, e.g. `durov · 14 найдено` or the raw query.
     */
    fun journalTitle(message: ChatMessage): String {
        if (!message.fromBot) {
            return preview(message.text.removePrefix("/").trim(), 56)
        }
        val text = message.text
        val nick = Regex("""Ник `([^`]+)`""").find(text)?.groupValues?.get(1)
            ?: Regex("""отчёт по `([^`]+)`""").find(text)?.groupValues?.get(1)
            ?: Regex("""остановлен · `([^`]+)`""").find(text)?.groupValues?.get(1)
        val found = Regex("""(?iu)найдено:\s*\*?(\d+)\*?""").find(text)?.groupValues?.get(1)
        if (nick != null && found != null) {
            return "$nick · $found найдено"
        }
        if (nick != null) {
            return nick
        }
        if (text.contains("—— Сравнение ников ——")) {
            val a = Regex("""A · `([^`]+)`""").find(text)?.groupValues?.get(1)
            val b = Regex("""B · `([^`]+)`""").find(text)?.groupValues?.get(1)
            if (a != null && b != null) return "$a ↔ $b"
            return "Сравнение ников"
        }
        if (text.startsWith("Email ·")) {
            val email = Regex("""`([^`]+@[^`]+)`""").find(text)?.groupValues?.get(1)
            return email?.let { "Email · $it" } ?: "Email"
        }
        if (text.startsWith("Телефон ·")) {
            val phone = Regex("""`(\+?[0-9\-\s]+)`""").find(text)?.groupValues?.get(1)
            return phone?.let { "Телефон · $it" } ?: "Телефон"
        }
        if (text.startsWith("ФИО")) {
            val name = text.lineSequence().drop(1).firstOrNull { it.isNotBlank() }?.trim()
            return name?.let { preview(it, 40) } ?: "ФИО"
        }
        return preview(text, 56)
    }

    fun journalMeta(message: ChatMessage): String {
        val kind = if (message.fromBot) "ОТЧЁТ" else "ЗАПРОС"
        return "$kind · ${formatTime(message.timestamp)}"
    }
}
