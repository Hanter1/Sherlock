package com.sherlock.bot.data

/**
 * Browser-like request headers for public profile probes.
 * Custom bot UAs are often blocked harder than a normal mobile Chrome string.
 */
object HttpHeaders {
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    const val ACCEPT_LANGUAGE = "be-BY,be;q=0.9,ru-BY;q=0.8,ru;q=0.7,en;q=0.5"

    const val ACCEPT_HTML =
        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"

    const val ACCEPT_DNS_JSON = "application/dns-json"
}
