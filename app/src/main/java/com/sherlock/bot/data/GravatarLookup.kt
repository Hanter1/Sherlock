package com.sherlock.bot.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Public Gravatar lookup by email MD5 (no account data beyond avatar presence).
 */
class GravatarLookup(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build(),
) {
    sealed class Result {
        data class Found(val hash: String, val avatarUrl: String, val profileUrl: String) : Result()
        data class Missing(val hash: String) : Result()
        data class Failed(val hash: String, val reason: String) : Result()
    }

    suspend fun lookup(email: String): Result = withContext(Dispatchers.IO) {
        val hash = md5Hex(email.trim().lowercase())
        val avatarUrl = "https://www.gravatar.com/avatar/$hash?d=404&s=128"
        val profileUrl = "https://www.gravatar.com/$hash"
        try {
            when (val code = probe(avatarUrl, head = true)) {
                200 -> Result.Found(hash, avatarUrl.replace("?d=404&s=128", "?s=128"), profileUrl)
                404 -> Result.Missing(hash)
                else -> {
                    when (val getCode = probe(avatarUrl, head = false)) {
                        200 -> Result.Found(hash, avatarUrl.replace("?d=404&s=128", "?s=128"), profileUrl)
                        404 -> Result.Missing(hash)
                        else -> Result.Failed(hash, "HTTP $getCode")
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failed(hash, e.message ?: "network error")
        }
    }

    private suspend fun probe(url: String, head: Boolean): Int {
        val request = Request.Builder()
            .url(url)
            .method(if (head) "HEAD" else "GET", null)
            .header("User-Agent", HttpHeaders.USER_AGENT)
            .header("Accept-Language", HttpHeaders.ACCEPT_LANGUAGE)
            .build()
        val call = client.newCall(request)
        val handle = coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion {
            call.cancel()
        }
        try {
            call.execute().use { return it.code }
        } finally {
            handle?.dispose()
        }
    }

    companion object {
        fun md5Hex(input: String): String {
            val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
