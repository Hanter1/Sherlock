package com.sherlock.bot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReportExporterTest {

    private val report = OsintResult.UsernameReport(
        username = "alice",
        found = listOf(
            SiteHit("GitHub", "https://github.com/alice", listOf("dev")),
            SiteHit("Telegram", "https://t.me/alice", listOf("social")),
        ),
        notFound = listOf("Steam"),
        errors = listOf("Instagram: blocked / challenge"),
        elapsedMs = 2100,
    )

    @Test
    fun markdownContainsSections() {
        val md = ReportExporter.toMarkdown(report)
        assertTrue(md.contains("# Sherlock Bot"))
        assertTrue(md.contains("`alice`"))
        assertTrue(md.contains("[GitHub](https://github.com/alice)"))
        assertTrue(md.contains("## Недоступно / блок"))
        assertTrue(md.contains("Steam"))
    }

    @Test
    fun jsonIsParseable() {
        val json = ReportExporter.toJson(report)
        assertTrue(json.contains("\"username\""))
        assertTrue(json.contains("alice"))
        assertTrue(json.contains("exportedAt"))
        val decoded = UsernameReportCodec.decodeReport(org.json.JSONObject(json))
        assertEquals("alice", decoded.username)
        assertEquals(2, decoded.found.size)
    }

    @Test
    fun jsonIncludesConfidence() {
        val withUncertain = report.copy(
            uncertain = listOf(
                SiteHit("npm", "https://www.npmjs.com/~alice", confidence = HitConfidence.UNCERTAIN),
            ),
        )
        val json = ReportExporter.toJson(withUncertain)
        assertTrue(json.contains("\"confidence\": \"confirmed\"") || json.contains("\"confidence\":\"confirmed\""))
        assertTrue(json.contains("\"confidence\": \"uncertain\"") || json.contains("\"confidence\":\"uncertain\""))
    }

    @Test
    fun markdownMentionsConfidence() {
        val md = ReportExporter.toMarkdown(report)
        assertTrue(md.contains("подтверждено"))
    }

    @Test
    fun writeExportCreatesFile() {
        val dir = File.createTempFile("exports", "").also {
            it.delete()
            it.mkdirs()
            it.deleteOnExit()
        }
        val file = ReportExporter.writeExport(dir, "alice", "md", "# hi\n")
        assertTrue(file.exists())
        assertTrue(file.name.startsWith("alice_"))
        assertTrue(file.name.endsWith(".md"))
        assertEquals("# hi\n", file.readText())
    }
}
