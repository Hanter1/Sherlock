package com.sherlock.bot.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ChatHistoryStore(
    context: Context,
    private val fileName: String = "chat_history.json",
) {
    private val file = File(context.applicationContext.filesDir, fileName)

    suspend fun load(): ChatSnapshot? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        ChatHistoryCodec.decode(file.readText())
    }

    suspend fun save(snapshot: ChatSnapshot) = withContext(Dispatchers.IO) {
        val json = ChatHistoryCodec.encode(snapshot)
        AtomicFiles.writeText(file, json)
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        if (file.exists()) file.delete()
    }
}
