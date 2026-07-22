package com.sherlock.bot.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class JournalKind(val label: String) {
    USERNAME("Ник"),
    COMPARE("Сравнение"),
    EMAIL("Email"),
    PHONE("Телефон"),
    FULL_NAME("ФИО"),
    QUERY("Запрос"),
    OTHER("Прочее"),
}

/**
 * One investigation unit in the journal: optional user query + bot report.
 */
data class JournalCase(
    val id: String,
    val kind: JournalKind,
    val title: String,
    val meta: String,
    val timestamp: Long,
    val messageIds: List<String>,
    val selectMessageId: String,
    val pinned: Boolean = false,
)

object JournalCases {

    private val dayFormat = SimpleDateFormat("d MMM yyyy", Locale("ru"))

    fun build(messages: List<ChatMessage>, pinnedId: String? = null): List<JournalCase> {
        val worthy = messages.filter(ChatSearch::isJournalWorthy)
        if (worthy.isEmpty()) return emptyList()

        val cases = mutableListOf<JournalCase>()
        var i = 0
        while (i < worthy.size) {
            val msg = worthy[i]
            if (!msg.fromBot) {
                val next = worthy.getOrNull(i + 1)
                if (next != null && next.fromBot && ChatSearch.isReportLike(next.text)) {
                    cases += caseFromPair(query = msg, report = next, pinnedId = pinnedId)
                    i += 2
                    continue
                }
                cases += caseFromQuery(msg, pinnedId)
                i += 1
            } else {
                cases += caseFromReport(msg, pinnedId)
                i += 1
            }
        }
        return cases
    }

    fun groupByDay(cases: List<JournalCase>, nowMs: Long = System.currentTimeMillis()): List<Pair<String, List<JournalCase>>> {
        if (cases.isEmpty()) return emptyList()
        val sorted = cases.sortedByDescending { it.timestamp }
        val groups = LinkedHashMap<String, MutableList<JournalCase>>()
        for (c in sorted) {
            val key = dayLabel(c.timestamp, nowMs)
            groups.getOrPut(key) { mutableListOf() }.add(c)
        }
        return groups.map { it.key to it.value }
    }

    fun dayLabel(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        val dayStart = startOfDay(nowMs)
        val tsDay = startOfDay(timestampMs)
        return when (tsDay) {
            dayStart -> "Сегодня"
            dayStart - 86_400_000L -> "Вчера"
            else -> dayFormat.format(Date(timestampMs))
        }
    }

    private fun startOfDay(ms: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = ms
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun caseFromPair(query: ChatMessage, report: ChatMessage, pinnedId: String?): JournalCase {
        val kind = kindOf(report.text)
        val title = ChatSearch.journalTitle(report)
        val time = ChatSearch.formatTime(report.timestamp)
        return JournalCase(
            id = report.id,
            kind = kind,
            title = title,
            meta = "${kind.label} · $time",
            timestamp = report.timestamp,
            messageIds = listOf(query.id, report.id),
            selectMessageId = report.id,
            pinned = report.id == pinnedId || query.id == pinnedId,
        )
    }

    private fun caseFromReport(report: ChatMessage, pinnedId: String?): JournalCase {
        val kind = kindOf(report.text)
        return JournalCase(
            id = report.id,
            kind = kind,
            title = ChatSearch.journalTitle(report),
            meta = "${kind.label} · ${ChatSearch.formatTime(report.timestamp)}",
            timestamp = report.timestamp,
            messageIds = listOf(report.id),
            selectMessageId = report.id,
            pinned = report.id == pinnedId,
        )
    }

    private fun caseFromQuery(query: ChatMessage, pinnedId: String?): JournalCase {
        return JournalCase(
            id = query.id,
            kind = JournalKind.QUERY,
            title = ChatSearch.journalTitle(query),
            meta = "Запрос · ${ChatSearch.formatTime(query.timestamp)}",
            timestamp = query.timestamp,
            messageIds = listOf(query.id),
            selectMessageId = query.id,
            pinned = query.id == pinnedId,
        )
    }

    fun kindOf(reportText: String): JournalKind = when {
        reportText.contains("—— Сводка ——") -> JournalKind.USERNAME
        reportText.contains("—— Сравнение ников ——") -> JournalKind.COMPARE
        reportText.startsWith("Email ·") -> JournalKind.EMAIL
        reportText.startsWith("Телефон ·") -> JournalKind.PHONE
        reportText.startsWith("ФИО") -> JournalKind.FULL_NAME
        else -> JournalKind.OTHER
    }
}
