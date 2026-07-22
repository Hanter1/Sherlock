package com.sherlock.bot.data

import android.content.Context

/**
 * Persistent user preferences for scans.
 */
class AppSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var maxParallel: Int
        get() {
            val value = prefs.getInt(KEY_PARALLEL, DEFAULT_PARALLEL)
            return if (value in ALLOWED_PARALLEL) value else DEFAULT_PARALLEL
        }
        set(value) {
            val safe = if (value in ALLOWED_PARALLEL) value else DEFAULT_PARALLEL
            prefs.edit().putInt(KEY_PARALLEL, safe).apply()
        }

    /** When false, Instagram / X are skipped during username scans. Default off — IG deep-links are unreliable. */
    var includeBotProtected: Boolean
        get() = prefs.getBoolean(KEY_BOT_PROTECTED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_BOT_PROTECTED, value).apply()
        }

    /** User accepted third-party email lookups (DoH / Gravatar). */
    var emailLookupConsent: Boolean
        get() = prefs.getBoolean(KEY_EMAIL_CONSENT, false)
        set(value) {
            prefs.edit().putBoolean(KEY_EMAIL_CONSENT, value).apply()
        }

    /** MX + SPF/DMARC via DNS-over-HTTPS (Cloudflare / Google). */
    var emailLookupMx: Boolean
        get() = prefs.getBoolean(KEY_EMAIL_MX, true)
        set(value) {
            prefs.edit().putBoolean(KEY_EMAIL_MX, value).apply()
        }

    /** Gravatar avatar check (sends MD5 of email). */
    var emailLookupGravatar: Boolean
        get() = prefs.getBoolean(KEY_EMAIL_GRAVATAR, true)
        set(value) {
            prefs.edit().putBoolean(KEY_EMAIL_GRAVATAR, value).apply()
        }

    /** When sharing/copying, strip phones and emails from the text. */
    var redactPiiOnShare: Boolean
        get() = prefs.getBoolean(KEY_REDACT_SHARE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_REDACT_SHARE, value).apply()
        }

    var disclaimerAccepted: Boolean
        get() = prefs.getBoolean(KEY_DISCLAIMER, false)
        set(value) {
            prefs.edit().putBoolean(KEY_DISCLAIMER, value).apply()
        }

    /** FLAG_SECURE — hide app contents in Recents / block screenshots. */
    var hideInRecents: Boolean
        get() = prefs.getBoolean(KEY_HIDE_RECENTS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_HIDE_RECENTS, value).apply()
        }

    var pinnedMessageId: String?
        get() = prefs.getString(KEY_PINNED_ID, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().putString(KEY_PINNED_ID, value).apply()
        }

    /** Optional HTTPS URL to osint_sites.json for remote catalog updates. */
    var catalogUrl: String
        get() = prefs.getString(KEY_CATALOG_URL, DEFAULT_CATALOG_URL).orEmpty()
        set(value) {
            prefs.edit().putString(KEY_CATALOG_URL, value.trim()).apply()
        }

    /** Last selected workbench mode (username / phone / …). */
    var pendingMode: SearchMode
        get() = runCatching {
            SearchMode.valueOf(prefs.getString(KEY_PENDING_MODE, SearchMode.NONE.name)!!)
        }.getOrDefault(SearchMode.NONE)
        set(value) {
            prefs.edit().putString(KEY_PENDING_MODE, value.name).apply()
        }

    /** When false, chat history is not written to disk. */
    var persistHistory: Boolean
        get() = prefs.getBoolean(KEY_PERSIST_HISTORY, true)
        set(value) {
            prefs.edit().putBoolean(KEY_PERSIST_HISTORY, value).apply()
        }

    /** Username scan category preset. */
    var scanPreset: ScanPreset
        get() = ScanPreset.fromId(prefs.getString(KEY_SCAN_PRESET, ScanPreset.QUICK.id))
        set(value) {
            prefs.edit().putString(KEY_SCAN_PRESET, value.id).apply()
        }

    /** Include NSFW sites from the sherlock-project catalog. */
    var includeNsfw: Boolean
        get() = prefs.getBoolean(KEY_INCLUDE_NSFW, false)
        set(value) {
            prefs.edit().putBoolean(KEY_INCLUDE_NSFW, value).apply()
        }

    /**
     * Developer override: allow remote catalog from any HTTPS host
     * (default — only GitHub/GitLab/Codeberg/jsDelivr allowlist).
     */
    var catalogAllowAnyHost: Boolean
        get() = prefs.getBoolean(KEY_CATALOG_ANY_HOST, false)
        set(value) {
            prefs.edit().putBoolean(KEY_CATALOG_ANY_HOST, value).apply()
        }

    /**
     * When true, remote catalog must include a valid ECDSA signature
     * ([CatalogSignature]). Invalid signatures are always rejected.
     */
    var catalogRequireSignature: Boolean
        get() = prefs.getBoolean(KEY_CATALOG_REQUIRE_SIG, false)
        set(value) {
            prefs.edit().putBoolean(KEY_CATALOG_REQUIRE_SIG, value).apply()
        }

    fun cycleParallel(): Int {
        val next = when (maxParallel) {
            3 -> 6
            6 -> 10
            else -> 3
        }
        maxParallel = next
        return next
    }

    companion object {
        private const val PREFS = "sherlock_settings"
        private const val KEY_PARALLEL = "max_parallel"
        private const val KEY_BOT_PROTECTED = "include_bot_protected"
        private const val KEY_EMAIL_CONSENT = "email_lookup_consent"
        private const val KEY_EMAIL_MX = "email_lookup_mx"
        private const val KEY_EMAIL_GRAVATAR = "email_lookup_gravatar"
        private const val KEY_REDACT_SHARE = "redact_pii_on_share"
        private const val KEY_DISCLAIMER = "disclaimer_accepted"
        private const val KEY_HIDE_RECENTS = "hide_in_recents"
        private const val KEY_PINNED_ID = "pinned_message_id"
        private const val KEY_CATALOG_URL = "catalog_url"
        private const val KEY_PENDING_MODE = "pending_mode"
        private const val KEY_PERSIST_HISTORY = "persist_history"
        private const val KEY_SCAN_PRESET = "scan_preset"
        private const val KEY_INCLUDE_NSFW = "include_nsfw"
        private const val KEY_CATALOG_ANY_HOST = "catalog_allow_any_host"
        private const val KEY_CATALOG_REQUIRE_SIG = "catalog_require_signature"
        const val DEFAULT_PARALLEL = 6
        val ALLOWED_PARALLEL = setOf(3, 6, 10)
        /** Empty = only bundled asset until user sets a URL. */
        const val DEFAULT_CATALOG_URL = ""
    }
}
