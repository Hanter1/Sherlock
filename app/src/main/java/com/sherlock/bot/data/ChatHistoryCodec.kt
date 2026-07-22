package com.sherlock.bot.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.LinkedHashMap

data class ChatSnapshot(
    val messages: List<ChatMessage>,
    val pendingMode: SearchMode,
    val reports: Map<String, OsintResult.UsernameReport> = emptyMap(),
)

/**
 * Encode/decode chat history as JSON (no typing/status bubbles).
 * v2 adds structured username reports keyed by reportId.
 */
object ChatHistoryCodec {
    private const val VERSION = 2
    const val MAX_MESSAGES = 80
    const val MAX_REPORTS = 40

    fun encode(snapshot: ChatSnapshot): String {
        val messages = snapshot.messages
            .filterNot { it.isTyping || it.id == STATUS_MESSAGE_ID }
            .takeLast(MAX_MESSAGES)
        val referencedIds = messages.mapNotNull { it.reportId }.toSet()
        val kept = LinkedHashMap<String, OsintResult.UsernameReport>()
        for ((id, report) in snapshot.reports) {
            if (id in referencedIds) kept[id] = report
        }
        while (kept.size > MAX_REPORTS) {
            kept.remove(kept.keys.first())
        }
        val reports = kept

        val root = JSONObject()
        root.put("v", VERSION)
        root.put("pendingMode", snapshot.pendingMode.name)
        val arr = JSONArray()
        messages.forEach { msg ->
            arr.put(
                JSONObject().apply {
                    put("id", msg.id)
                    put("text", msg.text)
                    put("fromBot", msg.fromBot)
                    put("timestamp", msg.timestamp)
                    msg.reportId?.let { put("reportId", it) }
                    val actions = JSONArray()
                    msg.actions.forEach { action ->
                        actions.put(
                            JSONObject()
                                .put("id", action.id)
                                .put("label", action.label),
                        )
                    }
                    put("actions", actions)
                },
            )
        }
        root.put("messages", arr)
        val reportsJson = JSONObject()
        for ((id, report) in reports) {
            reportsJson.put(id, UsernameReportCodec.encodeReport(report))
        }
        root.put("reports", reportsJson)
        return root.toString()
    }

    fun decode(raw: String): ChatSnapshot? {
        if (raw.isBlank()) return null
        return try {
            val root = JSONObject(raw)
            val mode = runCatching {
                SearchMode.valueOf(root.optString("pendingMode", SearchMode.NONE.name))
            }.getOrDefault(SearchMode.NONE)
            val arr = root.optJSONArray("messages") ?: JSONArray()
            val messages = buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val actionsJson = obj.optJSONArray("actions") ?: JSONArray()
                    val actions = buildList {
                        for (j in 0 until actionsJson.length()) {
                            val a = actionsJson.getJSONObject(j)
                            add(BotAction(a.getString("id"), a.getString("label")))
                        }
                    }
                    add(
                        ChatMessage(
                            id = obj.getString("id"),
                            text = obj.getString("text"),
                            fromBot = obj.getBoolean("fromBot"),
                            timestamp = obj.optLong("timestamp", 0L),
                            actions = actions,
                            isTyping = false,
                            reportId = obj.optString("reportId", "").takeIf { it.isNotBlank() },
                        ),
                    )
                }
            }
            val reportsObj = root.optJSONObject("reports")
            val reports = buildMap {
                if (reportsObj != null) {
                    for (key in reportsObj.keys()) {
                        put(key, UsernameReportCodec.decodeReport(reportsObj.getJSONObject(key)))
                    }
                }
            }
            ChatSnapshot(messages = messages, pendingMode = mode, reports = reports)
        } catch (_: Exception) {
            null
        }
    }

    const val STATUS_MESSAGE_ID = "status"
}
