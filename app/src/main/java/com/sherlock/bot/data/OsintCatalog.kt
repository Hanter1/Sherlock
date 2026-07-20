package com.sherlock.bot.data

data class OsintSite(
    val name: String,
    val urlTemplate: String,
    /** HTTP codes that mean "profile exists" when error codes are not used. */
    val okCodes: Set<Int> = setOf(200),
    /** HTTP codes that mean "profile missing". */
    val errorCodes: Set<Int> = setOf(404),
    val useHead: Boolean = true,
)

object OsintCatalog {
    val usernameSites: List<OsintSite> = listOf(
        OsintSite("GitHub", "https://github.com/{user}", errorCodes = setOf(404)),
        OsintSite("GitLab", "https://gitlab.com/{user}", errorCodes = setOf(404)),
        OsintSite("Reddit", "https://www.reddit.com/user/{user}", errorCodes = setOf(404)),
        OsintSite("Telegram", "https://t.me/{user}", okCodes = setOf(200), errorCodes = emptySet()),
        OsintSite("VK", "https://vk.com/{user}", errorCodes = setOf(404)),
        OsintSite("Habr", "https://habr.com/ru/users/{user}/", errorCodes = setOf(404)),
        OsintSite("DeviantArt", "https://www.deviantart.com/{user}", errorCodes = setOf(404)),
        OsintSite("Pinterest", "https://www.pinterest.com/{user}/", errorCodes = setOf(404)),
        OsintSite("Twitch", "https://www.twitch.tv/{user}", errorCodes = setOf(404)),
        OsintSite("Steam", "https://steamcommunity.com/id/{user}", errorCodes = setOf(404)),
        OsintSite("About.me", "https://about.me/{user}", errorCodes = setOf(404)),
        OsintSite("Keybase", "https://keybase.io/{user}", errorCodes = setOf(404)),
        OsintSite("Roblox", "https://www.roblox.com/users/profile?username={user}", errorCodes = setOf(404)),
        OsintSite("TikTok", "https://www.tiktok.com/@{user}", errorCodes = setOf(404)),
        OsintSite("Medium", "https://medium.com/@{user}", errorCodes = setOf(404)),
        OsintSite("SoundCloud", "https://soundcloud.com/{user}", errorCodes = setOf(404)),
        OsintSite("Flickr", "https://www.flickr.com/people/{user}/", errorCodes = setOf(404)),
        OsintSite("HackerNoon", "https://hackernoon.com/u/{user}", errorCodes = setOf(404)),
        OsintSite("ProductHunt", "https://www.producthunt.com/@{user}", errorCodes = setOf(404)),
        OsintSite("Linktree", "https://linktr.ee/{user}", errorCodes = setOf(404)),
    )
}
