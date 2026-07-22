package com.sherlock.bot.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

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
            val request = Request.Builder()
                .url(avatarUrl)
                .head()
                .header("User-Agent", HttpHeaders.USER_AGENT)
                .header("Accept-Language", HttpHeaders.ACCEPT_LANGUAGE)
                .build()
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> Result.Found(hash, avatarUrl.replace("?d=404&s=128", "?s=128"), profileUrl)
                    404 -> Result.Missing(hash)
                    else -> {
                        // Some CDNs reject HEAD — try GET
                        val getReq = Request.Builder()
                            .url(avatarUrl)
                            .get()
                            .header("User-Agent", HttpHeaders.USER_AGENT)
                            .header("Accept-Language", HttpHeaders.ACCEPT_LANGUAGE)
                            .build()
                        client.newCall(getReq).execute().use { getResponse ->
                            when (getResponse.code) {
                                200 -> Result.Found(hash, avatarUrl.replace("?d=404&s=128", "?s=128"), profileUrl)
                                404 -> Result.Missing(hash)
                                else -> Result.Failed(hash, "HTTP ${getResponse.code}")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Result.Failed(hash, e.message ?: "network error")
        }
    }

    companion object {
        fun md5Hex(input: String): String {
            val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
