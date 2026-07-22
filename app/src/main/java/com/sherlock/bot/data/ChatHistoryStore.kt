package com.sherlock.bot.data

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Persists chat history. Prefers EncryptedFile; migrates legacy plaintext once.
 */
class ChatHistoryStore(
    context: Context,
    private val fileName: String = "chat_history.json",
    private val encryptedFileName: String = "chat_history.enc",
) {
    private val appContext = context.applicationContext
    private val plainFile = File(appContext.filesDir, fileName)
    private val encFile = File(appContext.filesDir, encryptedFileName)

    suspend fun load(): ChatSnapshot? = withContext(Dispatchers.IO) {
        if (encFile.exists()) {
            return@withContext runCatching {
                openEncrypted().openFileInput().bufferedReader().use { reader ->
                    ChatHistoryCodec.decode(reader.readText())
                }
            }.getOrNull()
        }
        if (!plainFile.exists()) return@withContext null
        val decoded = ChatHistoryCodec.decode(plainFile.readText())
        if (decoded != null) {
            runCatching {
                writeEncrypted(decoded)
                plainFile.delete()
            }
        }
        decoded
    }

    suspend fun save(snapshot: ChatSnapshot) = withContext(Dispatchers.IO) {
        writeEncrypted(snapshot)
        if (plainFile.exists()) plainFile.delete()
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        if (plainFile.exists()) plainFile.delete()
        if (encFile.exists()) encFile.delete()
    }

    private fun writeEncrypted(snapshot: ChatSnapshot) {
        val json = ChatHistoryCodec.encode(snapshot)
        if (encFile.exists()) encFile.delete()
        openEncrypted().openFileOutput().bufferedWriter().use { writer ->
            writer.write(json)
        }
    }

    private fun openEncrypted(): EncryptedFile {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedFile.Builder(
            appContext,
            encFile,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
        ).build()
    }
}
