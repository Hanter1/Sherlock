package com.sherlock.bot.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class OsintEngineHttpTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val template = server.url("/u/PLACEHOLDER").toString().replace("PLACEHOLDER", "{user}")
        OsintCatalog.loadForTests(
            listOf(
                OsintSite(
                    name = "Local",
                    urlTemplate = template,
                    okCodes = setOf(200),
                    errorCodes = setOf(404),
                    trustHttpStatus = true,
                    useHead = false,
                ),
            ),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        OsintCatalog.loadForTests()
    }

    @Test
    fun cancelAbortsInFlightRequest() = runBlocking {
        server.enqueue(
            MockResponse()
                .setBodyDelay(30, TimeUnit.SECONDS)
                .setSocketPolicy(SocketPolicy.NO_RESPONSE),
        )
        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        val engine = OsintEngine(
            client = client,
            maxParallel = { 1 },
            maxRetries = 0,
        )
        val job = async {
            engine.searchUsername("alice", bypassCache = true)
        }
        delay(250)
        job.cancel()
        var cancelled = false
        try {
            withTimeout(5_000) { job.await() }
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
    }

    @Test
    fun retriesOn503ThenSucceeds() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("profile"))
        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        val engine = OsintEngine(
            client = client,
            maxParallel = { 1 },
            maxRetries = 2,
        )
        val report = engine.searchUsername("bob", bypassCache = true)
        assertEquals(listOf("Local"), report.found.map { it.site })
        assertTrue(server.requestCount >= 2)
    }
}
