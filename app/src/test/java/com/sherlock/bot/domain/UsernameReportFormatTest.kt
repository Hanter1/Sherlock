package com.sherlock.bot.domain

import com.sherlock.bot.data.OsintResult
import com.sherlock.bot.data.SiteHit
import com.sherlock.bot.data.UsernameReportFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsernameReportFormatTest {

    private val report = OsintResult.UsernameReport(
        username = "alice",
        found = listOf(
            SiteHit("GitHub", "https://github.com/alice", listOf("dev")),
            SiteHit("Telegram", "https://t.me/alice", listOf("social")),
        ),
        notFound = listOf("Steam"),
        errors = listOf("Instagram: blocked / challenge", "X: blocked / challenge"),
        elapsedMs = 1500,
    )

    @Test
    fun fullReportHasSummaryCategoriesAndErrors() {
        val text = BotInteractor().formatUsernameReport(report).text
        assertTrue(text.contains("—— Сводка ——"))
        assertTrue(text.contains("Найдено: *2*"))
        assertTrue(text.contains("ошибки: 2"))
        assertTrue(text.contains("1.5 с") || text.contains("1,5 с"))
        assertTrue(text.contains("По категориям:"))
        assertTrue(text.contains("[разработка]"))
        assertTrue(text.contains("[соцсети]"))
        assertTrue(text.contains("Недоступно / блок:"))
        assertTrue(text.contains("Instagram"))
    }

    @Test
    fun foundOnlyHidesErrors() {
        val text = BotInteractor().formatUsernameReport(report, UsernameReportFilter.FOUND_ONLY).text
        assertTrue(text.contains("Фильтр: только найденные"))
        assertTrue(text.contains("GitHub"))
        assertFalse(text.contains("Недоступно / блок:"))
        assertFalse(text.contains("Instagram: blocked"))
    }

    @Test
    fun hideErrorsKeepsFound() {
        val text = BotInteractor().formatUsernameReport(report, UsernameReportFilter.HIDE_ERRORS).text
        assertTrue(text.contains("Фильтр: без ошибок"))
        assertTrue(text.contains("Telegram"))
        assertFalse(text.contains("Недоступно / блок:"))
    }

    @Test
    fun cacheLabelSkipsTime() {
        val cached = report.copy(fromCache = true, elapsedMs = 0)
        val text = BotInteractor().formatUsernameReport(cached).text
        assertTrue(text.contains("из кэша"))
        assertFalse(text.contains("Время:"))
    }
}
