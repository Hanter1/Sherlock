package com.sherlock.bot.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class OsintEngineClassifyTest {

    private val engine = OsintEngine()

    init {
        OsintCatalog.loadForTests()
    }

    @Test
    fun withoutOkMarkersHttp200IsUncertainNotFound() {
        val site = OsintSite(
            name = "Mystery",
            urlTemplate = "https://example.com/{user}",
            okCodes = setOf(200),
            errorCodes = setOf(404),
            useHead = true,
        )
        val result = engine.classify(site, "https://example.com/x", 200, null)
        assertTrue(result is OsintEngine.CheckOutcome.Uncertain)
    }

    @Test
    fun trustedHttpStatusAllowsFoundWithoutMarkers() {
        val site = OsintSite(
            name = "GitHub",
            urlTemplate = "https://github.com/{user}",
            okCodes = setOf(200),
            errorCodes = setOf(404),
            useHead = true,
            trustHttpStatus = true,
        )
        val found = engine.classify(site, "https://github.com/x", 200, null)
        assertTrue(found is OsintEngine.CheckOutcome.Found)
        val missing = engine.classify(site, "https://github.com/x", 404, null)
        assertTrue(missing is OsintEngine.CheckOutcome.Missing)
    }

    @Test
    fun status404MeansMissing() {
        val site = OsintSite("GitHub", "https://github.com/{user}", errorCodes = setOf(404))
        val result = engine.classify(site, "https://github.com/x", 404, null)
        assertTrue(result is OsintEngine.CheckOutcome.Missing)
    }

    @Test
    fun telegramErrorMarkerMeansMissing() {
        val site = OsintCatalog.usernameSites.first { it.name == "Telegram" }
        val body = """
            <div class="tgme_icon_user"></div>
            <div>If you have <strong>Telegram</strong>, you can contact <a>@nobody</a></div>
        """.trimIndent()
        val result = engine.classify(site, "https://t.me/nobody", 200, body)
        assertTrue(result is OsintEngine.CheckOutcome.Missing)
    }

    @Test
    fun telegramOkMarkersMeanFound() {
        val site = OsintCatalog.usernameSites.first { it.name == "Telegram" }
        val body = """
            <div class="tgme_page_title">Pavel Durov</div>
            <div class="tgme_page_photo"></div>
        """.trimIndent()
        val result = engine.classify(site, "https://t.me/durov", 200, body)
        assertTrue(result is OsintEngine.CheckOutcome.Found)
    }

    @Test
    fun okMarkersRequiredAvoidFalsePositive() {
        val site = OsintCatalog.usernameSites.first { it.name == "Steam" }
        val result = engine.classify(site, "https://steamcommunity.com/id/x", 200, "<html>empty</html>")
        assertTrue(result is OsintEngine.CheckOutcome.Missing)
    }

    @Test
    fun twitchRequiresLiveMarkers() {
        val site = OsintSite(
            name = "Twitch",
            urlTemplate = "https://www.twitch.tv/{user}",
            errorCodes = emptySet(),
            okBodyMarkers = listOf("isLiveBroadcast", "og:video", "twitter:title"),
        )
        val missing = engine.classify(site, "https://www.twitch.tv/x", 200, "<title>Twitch</title>")
        assertTrue(missing is OsintEngine.CheckOutcome.Missing)
        val found = engine.classify(
            site,
            "https://www.twitch.tv/xqc",
            200,
            """<meta property="og:video" content="https://player.twitch.tv/?channel=xqc"/><script>isLiveBroadcast</script>""",
        )
        assertTrue(found is OsintEngine.CheckOutcome.Found)
    }

    @Test
    fun vkUses404AndOwnerMarkers() {
        val site = OsintSite(
            name = "VK",
            urlTemplate = "https://vk.com/{user}",
            errorCodes = setOf(404),
            errorBodyMarkers = listOf("page_not_found"),
            okBodyMarkers = listOf("owner_id", "wall_module", "screen_name"),
        )
        assertTrue(
            engine.classify(site, "https://vk.com/x", 404, "page_not_found")
                is OsintEngine.CheckOutcome.Missing,
        )
        assertTrue(
            engine.classify(site, "https://vk.com/durov", 200, """{"owner_id":1,"screen_name":"durov"}""")
                is OsintEngine.CheckOutcome.Found,
        )
        assertTrue(
            engine.classify(site, "https://vk.com/x", 200, "<html>login wall</html>")
                is OsintEngine.CheckOutcome.Missing,
        )
    }

    @Test
    fun tiktokWafBecomesErrorNotFound() {
        val site = OsintSite(
            name = "TikTok",
            urlTemplate = "https://www.tiktok.com/@{user}",
            errorCodes = emptySet(),
            blockBodyMarkers = listOf("_wafchallenge", "SlardarWAF"),
            okBodyMarkers = listOf("uniqueId", "userInfo"),
        )
        val blocked = engine.classify(
            site,
            "https://www.tiktok.com/@x",
            200,
            """<script>_wafchallenge</script><script>SlardarWAF</script>""",
        )
        assertTrue(blocked is OsintEngine.CheckOutcome.Error)
        assertTrue((blocked as OsintEngine.CheckOutcome.Error).reason.contains("blocked"))
    }

    @Test
    fun fullNameLinksAreUrlEncoded() {
        val report = engine.analyzeFullName("Иванов Иван")
        assertTrue(report.body.contains("q=%D0%98%D0%B2%D0%B0%D0%BD%D0%BE%D0%B2"))
        val googleLine = report.body.lineSequence().first { it.contains("google.com") }
        val encoded = googleLine.substringAfter("q=")
        val decoded = URLDecoder.decode(encoded, StandardCharsets.UTF_8.name())
        assertTrue(decoded.contains("Иванов"))
        assertTrue(decoded.contains("Иван"))
    }

    @Test
    fun uncertainIncludesHttpDiagnostics() {
        val site = OsintSite(
            name = "Mystery",
            urlTemplate = "https://example.com/{user}",
            okCodes = setOf(200),
            errorCodes = setOf(404),
        )
        val result = engine.classify(
            site = site,
            url = "https://example.com/x",
            code = 200,
            body = null,
            probe = null,
        )
        assertTrue(result is OsintEngine.CheckOutcome.Uncertain)
        val uncertain = result as OsintEngine.CheckOutcome.Uncertain
        assertTrue(uncertain.diagnostics?.formatBrief()?.contains("HTTP 200") == true)
        assertTrue(uncertain.diagnostics?.formatBrief()?.contains("нет маркера") == true)
    }
}
