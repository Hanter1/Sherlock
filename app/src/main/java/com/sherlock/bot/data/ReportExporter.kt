package com.sherlock.bot.data

import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Builds Markdown / JSON exports for username reports.
 */
object ReportExporter {

    fun toMarkdown(report: OsintResult.UsernameReport): String {
        val total = report.found.size + report.uncertain.size + report.notFound.size + report.errors.size
        return buildString {
            appendLine("# Sherlock Bot — отчёт")
            appendLine()
            appendLine("- **Ник:** `${report.username}`")
            appendLine("- **Найдено:** ${report.found.size}")
            appendLine("- **Неуверенно:** ${report.uncertain.size}")
            appendLine("- **Нет:** ${report.notFound.size}")
            appendLine("- **Ошибки:** ${report.errors.size}")
            appendLine("- **Всего площадок:** $total")
            if (!report.fromCache) {
                appendLine("- **Время:** ${SiteCategories.formatElapsed(report.elapsedMs)}")
            } else {
                appendLine("- **Источник:** кэш")
                CacheAge.formatCacheLine(report.cacheSavedAtMs, report.cacheTtlMs)?.let {
                    appendLine("- **Кэш:** $it")
                }
            }
            if (report.cancelled) appendLine("- **Статус:** остановлен (частичный)")
            appendLine()
            if (report.found.isNotEmpty()) {
                appendLine("## Найдены")
                appendLine()
                appendLine("_Уверенность: ${HitConfidence.CONFIRMED.label}_")
                appendLine()
                val groups = linkedMapOf<String, MutableList<SiteHit>>()
                for (hit in report.found) {
                    val key = hit.categories.firstOrNull()?.let { SiteCategories.label(it) } ?: "прочее"
                    groups.getOrPut(key) { mutableListOf() }.add(hit)
                }
                for ((label, hits) in groups) {
                    appendLine("### $label")
                    appendLine()
                    hits.forEach { appendLine("- [${it.site}](${it.url})") }
                    appendLine()
                }
            } else {
                appendLine("## Найдены")
                appendLine()
                appendLine("_Публичные профили не найдены._")
                appendLine()
            }
            if (report.uncertain.isNotEmpty()) {
                appendLine("## Неуверенно")
                appendLine()
                appendLine("_Уверенность: ${HitConfidence.UNCERTAIN.label} — HTTP ок, но нет маркера профиля._")
                appendLine()
                report.uncertain.forEach { appendLine("- [${it.site}](${it.url})") }
                appendLine()
            }
            if (report.errors.isNotEmpty()) {
                appendLine("## Недоступно / блок")
                appendLine()
                report.errors.forEach { appendLine("- $it") }
                appendLine()
            }
            if (report.notFound.isNotEmpty()) {
                appendLine("## Нет профиля")
                appendLine()
                appendLine(report.notFound.joinToString(", "))
                appendLine()
            }
            appendLine("---")
            appendLine("_Только открытые источники. Sherlock Bot._")
        }.trim() + "\n"
    }

    fun toJson(report: OsintResult.UsernameReport): String {
        val obj = UsernameReportCodec.encodeReport(report)
        obj.put("exportedAt", isoNow())
        obj.put("fromCache", report.fromCache)
        report.cacheSavedAtMs?.let { obj.put("cacheSavedAtMs", it) }
        report.cacheTtlMs?.let { obj.put("cacheTtlMs", it) }
        CacheAge.formatCacheLine(report.cacheSavedAtMs, report.cacheTtlMs)?.let {
            obj.put("cacheAge", it)
        }
        return obj.toString(2) + "\n"
    }

    fun toMarkdownFromText(title: String, body: String): String {
        return buildString {
            appendLine("# $title")
            appendLine()
            appendLine(body.trim())
            appendLine()
            appendLine("---")
            appendLine("_Sherlock Bot_")
        }.trim() + "\n"
    }

    fun writeExport(dir: File, baseName: String, extension: String, content: String): File {
        dir.mkdirs()
        val safe = baseName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(48).ifBlank { "report" }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "${safe}_$stamp.$extension")
        file.writeText(content)
        return file
    }

    private fun isoNow(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date())
    }
}
