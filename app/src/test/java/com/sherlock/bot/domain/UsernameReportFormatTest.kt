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
    fun foundOnlyHidesErrorsAndUncertain() {
        val withUncertain = report.copy(
            uncertain = listOf(SiteHit("npm", "https://www.npmjs.com/~alice")),
        )
        val text = BotInteractor().formatUsernameReport(withUncertain, UsernameReportFilter.FOUND_ONLY).text
        assertTrue(text.contains("Фильтр: только найденные"))
        assertTrue(text.contains("GitHub"))
        assertFalse(text.contains("Недоступно / блок:"))
        assertFalse(text.contains("Instagram: blocked"))
        assertFalse(text.contains("Неуверенно"))
        assertFalse(text.contains("npm"))
    }

    @Test
    fun fullReportShowsUncertainSection() {
        val withUncertain = report.copy(
            uncertain = listOf(SiteHit("npm", "https://www.npmjs.com/~alice")),
        )
        val message = BotInteractor().formatUsernameReport(withUncertain)
        assertTrue(message.text.contains("неуверенно: 1"))
        assertTrue(message.text.contains("Неуверенно (нет маркера профиля):"))
        assertTrue(message.text.contains("npm"))
        assertTrue(message.actions.any { it.id == "rescan_errors" })
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

    @Test
    fun cacheLabelShowsAgeAndRemaining() {
        val now = System.currentTimeMillis()
        val cached = report.copy(
            fromCache = true,
            elapsedMs = 0,
            cacheSavedAtMs = now - 3_600_000L,
            cacheTtlMs = 24 * 3_600_000L,
        )
        val text = BotInteractor().formatUsernameReport(cached).text
        assertTrue(text.contains("из кэша"))
        assertTrue(text.contains("возраст"))
        assertTrue(text.contains("ещё ~"))
    }
}
