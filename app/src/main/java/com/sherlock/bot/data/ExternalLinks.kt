package com.sherlock.bot.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Opens http(s) links. Instagram/X profile URLs are forced into a browser Custom Tab
 * because the Instagram app often swallows https deep-links and lands on the feed ("nowhere").
 */
object ExternalLinks {

    private val INSTAGRAM_HOSTS = setOf("instagram.com", "www.instagram.com")
    private val X_HOSTS = setOf("x.com", "www.x.com", "twitter.com", "www.twitter.com")

    private val BROWSER_PACKAGES = listOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "org.mozilla.firefox",
        "com.microsoft.emmx",
        "com.opera.browser",
        "com.brave.browser",
    )

    fun open(context: Context, rawUrl: String) {
        val url = normalizeUrl(rawUrl)
        val uri = Uri.parse(url)
        val host = uri.host?.lowercase().orEmpty()
        if (host in INSTAGRAM_HOSTS || host in X_HOSTS) {
            openInBrowser(context, preferInstagramWebUri(uri))
            return
        }
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: ActivityNotFoundException) {
            openInBrowser(context, uri)
        }
    }

    fun openInBrowser(context: Context, uri: Uri) {
        val tabs = CustomTabsIntent.Builder().build()
        tabs.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        for (pkg in BROWSER_PACKAGES) {
            if (!isInstalled(context, pkg)) continue
            try {
                tabs.intent.setPackage(pkg)
                tabs.launchUrl(context, uri)
                return
            } catch (_: Exception) {
                tabs.intent.setPackage(null)
            }
        }
        try {
            tabs.intent.setPackage(null)
            tabs.launchUrl(context, uri)
        } catch (_: Exception) {
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    "Открыть ссылку",
                ),
            )
        }
    }

    /** Prefer www + trailing slash profile form that browsers handle well. */
    fun preferInstagramWebUri(uri: Uri): Uri {
        val host = uri.host?.lowercase().orEmpty()
        if (host !in INSTAGRAM_HOSTS) return uri
        val segments = uri.pathSegments.filter { it.isNotBlank() }
        // /username or /_u/username
        val user = when {
            segments.size >= 2 && segments[0] == "_u" -> segments[1]
            segments.isNotEmpty() -> segments[0]
            else -> return uri
        }
        if (user.startsWith("p") && segments.size > 1) return uri // post/reel paths
        if (user in setOf("p", "reel", "reels", "stories", "explore", "accounts")) return uri
        return Uri.parse("https://www.instagram.com/$user/")
    }

    fun normalizeUrl(raw: String): String {
        val t = raw.trim()
        return when {
            t.startsWith("http://", ignoreCase = true) || t.startsWith("https://", ignoreCase = true) -> t
            else -> "https://$t"
        }
    }

    private fun isInstalled(context: Context, packageName: String): Boolean =
        runCatching {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)
}
