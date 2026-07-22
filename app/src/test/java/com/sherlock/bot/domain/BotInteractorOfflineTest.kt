package com.sherlock.bot.domain

import com.sherlock.bot.data.OsintEngine
import com.sherlock.bot.data.SearchMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class BotInteractorOfflineTest {

    @Test
    fun usernameRequiresNetwork() = runBlocking {
        val bot = BotInteractor(
            osint = OsintEngine(),
            isOnline = { false },
        )
        // Catalog must be loaded for engine path; offline check happens first
        com.sherlock.bot.data.OsintCatalog.loadForTests()
        val (messages, _) = bot.handleUserText("/username durov", SearchMode.NONE)
        assertTrue(messages.single().text.contains("Нет сети"))
    }

    @Test
    fun phoneWorksOffline() = runBlocking {
        val bot = BotInteractor(
            osint = OsintEngine(),
            isOnline = { false },
        )
        val (messages, _) = bot.handleUserText("/phone +79001234567", SearchMode.NONE)
        assertTrue(messages.single().text.contains("Телефон"))
        assertTrue(messages.single().text.contains("+79001234567"))
    }
}
