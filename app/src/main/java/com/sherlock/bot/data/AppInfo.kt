package com.sherlock.bot.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Human-readable "About" blurb: APK version + catalog versions.
 */
object AppInfo {

    fun versionName(context: Context): String = runCatching {
        val pm = context.packageManager
        val pkg = context.packageName
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)).versionName
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, 0).versionName
        }
    }.getOrNull().orEmpty().ifBlank { "?" }

    fun versionCode(context: Context): Long = runCatching {
        val pm = context.packageManager
        val pkg = context.packageName
        val info = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, 0)
        }
        if (Build.VERSION.SDK_INT >= 28) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }.getOrDefault(0L)

    fun aboutText(context: Context): String {
        val catalog = OsintCatalog.info()
        val defUpdated = DefDirectory.updatedLabel().ifBlank { "—" }
        val defCodes = DefDirectory.codeCount()
        val verCode = versionCode(context)
        return buildString {
            appendLine("Sherlock Bot")
            appendLine("Версия приложения: ${versionName(context)} ($verCode)")
            appendLine()
            appendLine("Каталог площадок (osint_sites.json):")
            appendLine("• version: ${catalog.version}")
            appendLine("• updated: ${catalog.updated.ifBlank { "—" }}")
            appendLine("• площадок: ${catalog.siteCount}")
            appendLine("• источник: ${catalog.source}")
            if (catalog.sha256.isNotBlank()) {
                appendLine("• sha256: ${catalog.sha256.take(12)}…")
            }
            appendLine()
            appendLine("Справочник DEF (def_codes.json):")
            appendLine("• updated: $defUpdated")
            appendLine("• кодов: $defCodes")
            appendLine()
            appendLine("Упор на Беларусь: +375, Google BY / Yandex BY, язык be-BY.")
            appendLine("Только открытые источники. Без утечек и закрытых баз.")
            appendLine("Кэш ников: сессия + диск (24 ч). Повтор без кэша даёт Δ с прошлого скана.")
            appendLine("Настройки: параллелизм, Instagram/X, remote-каталог, скрытие в недавних.")
        }.trim()
    }
}
