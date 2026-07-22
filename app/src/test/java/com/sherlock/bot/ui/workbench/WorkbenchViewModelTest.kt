package com.sherlock.bot.ui.workbench

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sherlock.bot.data.AppSettings
import com.sherlock.bot.data.ChatHistoryStore
import com.sherlock.bot.data.NetworkMonitor
import com.sherlock.bot.data.OsintCatalog
import com.sherlock.bot.data.OsintEngine
import com.sherlock.bot.data.OsintResult
import com.sherlock.bot.data.OsintSite
import com.sherlock.bot.data.SiteHit
import com.sherlock.bot.data.UsernameDiskCache
import com.sherlock.bot.data.UsernameSessionCache
import com.sherlock.bot.domain.BotInteractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class WorkbenchViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()
    private lateinit var server: MockWebServer
    private lateinit var app: Application
    private lateinit var vm: WorkbenchViewModel
    private lateinit var bot: BotInteractor

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        app = ApplicationProvider.getApplicationContext()
        server = MockWebServer()
        server.start()
        val template = server.url("/u/PLACEHOLDER").toString().replace("PLACEHOLDER", "{user}")
        OsintCatalog.loadForTests(
            listOf(
                OsintSite(
                    name = "Local",
                    urlTemplate = template,
                    trustHttpStatus = true,
                    useHead = false,
                ),
            ),
        )
        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        val session = UsernameSessionCache()
        val disk = UsernameDiskCache(File(app.filesDir, "vm_test_cache.json"))
        disk.clear()
        val osint = OsintEngine(
            client = client,
            sessionCache = session,
            diskCache = disk,
            maxParallel = { 1 },
            maxRetries = 0,
        )
        bot = BotInteractor(osint = osint, isOnline = { true })
        val settings = AppSettings(app)
        settings.persistHistory = false
        settings.disclaimerAccepted = true
        vm = WorkbenchViewModel(
            application = app,
            networkMonitor = NetworkMonitor(app),
            appSettings = settings,
            sessionCache = session,
            diskCache = disk,
            osint = osint,
            bot = bot,
            historyStore = ChatHistoryStore(
                context = app,
                fileName = "vm_test_history.json",
                encryptedFileName = "vm_test_history.enc",
                encrypted = false,
            ),
        )
        runBlocking { awaitHistoryLoaded() }
    }

    @After
    fun tearDown() {
        server.shutdown()
        OsintCatalog.loadForTests()
        Dispatchers.resetMain()
    }

    @Test
    fun actionUsesSpecificReportIdNotLast() = runBlocking {
        val alice = OsintResult.UsernameReport(
            username = "alice",
            found = listOf(SiteHit("GitHub", "https://github.com/alice")),
            notFound = emptyList(),
            errors = emptyList(),
            elapsedMs = 1,
        )
        val bob = OsintResult.UsernameReport(
            username = "bob",
            found = listOf(SiteHit("GitHub", "https://github.com/bob")),
            notFound = emptyList(),
            errors = listOf("X: blocked"),
            elapsedMs = 2,
        )
        val aliceMsg = bot.formatUsernameReport(alice)
        val bobMsg = bot.formatUsernameReport(bob)
        vm.seedMessagesForTests(listOf(aliceMsg, bobMsg))

        vm.onAction(aliceMsg.id, "report_found")
        delay(80)
        val last = vm.state.value.messages.last()
        assertTrue(last.text.contains("alice"))
        assertTrue(last.text.contains("Фильтр: только найденные"))
        assertEquals(aliceMsg.reportId, last.reportId)
    }

    @Test
    fun clearWhileBusyCancelsAndResets() = runBlocking {
        server.enqueue(
            MockResponse()
                .setBodyDelay(20, TimeUnit.SECONDS)
                .setSocketPolicy(SocketPolicy.NO_RESPONSE),
        )
        vm.onInputChange("/username slowuser")
        vm.send()
        delay(200)
        assertTrue(vm.state.value.isBusy)
        vm.confirmClearHistory()
        delay(300)
        assertFalse(vm.state.value.isBusy)
        assertTrue(vm.state.value.messages.any { it.text.contains("Sherlock Bot") })
    }

    @Test
    fun cancelMidScanProducesPartialReport() = runBlocking {
        server.enqueue(
            MockResponse()
                .setBodyDelay(30, TimeUnit.SECONDS)
                .setSocketPolicy(SocketPolicy.NO_RESPONSE),
        )
        vm.onInputChange("/username cancelme")
        vm.send()
        assertTrue("expected busy after send", waitUntil { vm.state.value.isBusy })
        vm.cancelScan()
        assertTrue("expected idle after cancel", waitUntil { !vm.state.value.isBusy })
        assertTrue(
            vm.state.value.messages.any {
                it.fromBot && (it.text.contains("остановлен") || it.text.contains("Скан остановлен"))
            },
        )
    }

    @Test
    fun sendWhileBusyIsIgnored() = runBlocking {
        server.enqueue(
            MockResponse()
                .setBodyDelay(30, TimeUnit.SECONDS)
                .setSocketPolicy(SocketPolicy.NO_RESPONSE),
        )
        vm.onInputChange("/username first")
        vm.send()
        assertTrue(waitUntil { vm.state.value.isBusy })
        val messagesBefore = vm.state.value.messages.size
        vm.onInputChange("/username second")
        vm.send()
        assertTrue(vm.state.value.isBusy)
        assertEquals(messagesBefore, vm.state.value.messages.size)
        vm.cancelScan()
        assertTrue(waitUntil { !vm.state.value.isBusy })
    }

    private suspend fun awaitHistoryLoaded() {
        repeat(50) {
            if (vm.state.value.historyLoaded) return
            delay(20)
        }
        error("history not loaded")
    }

    private suspend fun waitUntil(timeoutMs: Long = 8_000, condition: () -> Boolean): Boolean {
        val steps = (timeoutMs / 50).toInt().coerceAtLeast(1)
        repeat(steps) {
            if (condition()) return true
            delay(50)
        }
        return condition()
    }
}
