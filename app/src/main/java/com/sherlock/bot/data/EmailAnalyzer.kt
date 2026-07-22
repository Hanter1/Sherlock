package com.sherlock.bot.data

import java.util.regex.Pattern

/**
 * Email format check + report formatting. MX / TXT / Gravatar resolved separately.
 */
object EmailAnalyzer {

    private val emailPattern = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    data class Parsed(val email: String, val local: String, val domain: String)

    fun parse(raw: String): Parsed? {
        val email = raw.trim().lowercase()
        if (!emailPattern.matcher(email).matches()) return null
        return Parsed(
            email = email,
            local = email.substringBefore("@"),
            domain = email.substringAfter("@"),
        )
    }

    fun invalidReport(): OsintResult.InfoReport = OsintResult.InfoReport(
        title = "Email",
        body = "Похоже, это не email. Пример: name@example.com",
    )

    fun formatReport(
        parsed: Parsed,
        mx: MxLookup.Result,
        gravatar: GravatarLookup.Result,
        policy: MxLookup.MailPolicy? = null,
    ): OsintResult.InfoReport {
        val body = buildString {
            appendLine("Адрес: `${parsed.email}`")
            appendLine("Локальная часть: ${parsed.local} (${parsed.local.length} симв.)")
            appendLine("Домен: ${parsed.domain}")
            appendLine()
            appendLine("MX (DNS-over-HTTPS · Cloudflare → Google):")
            when (mx) {
                is MxLookup.Result.Ok -> {
                    if (mx.provider.isNotBlank()) {
                        appendLine("• источник: ${mx.provider}")
                    }
                    if (mx.records.isEmpty()) {
                        appendLine("• записей MX нет — почта на домене, скорее всего, не принимается")
                    } else {
                        mx.records.forEach { rec ->
                            appendLine("• [${rec.priority}] ${rec.host}")
                        }
                    }
                }
                is MxLookup.Result.Failed -> {
                    appendLine("• не удалось запросить: ${mx.reason}")
                }
            }
            appendLine()
            appendLine("SPF / DMARC (TXT):")
            appendPolicy(policy)
            appendLine()
            appendLine("Gravatar (публичный аватар по MD5):")
            when (gravatar) {
                is GravatarLookup.Result.Found -> {
                    appendLine("• профиль найден")
                    appendLine("• аватар: ${gravatar.avatarUrl}")
                    appendLine("• страница: ${gravatar.profileUrl}")
                }
                is GravatarLookup.Result.Missing -> {
                    appendLine("• публичного аватара нет (hash `${gravatar.hash.take(8)}…`)")
                }
                is GravatarLookup.Result.Failed -> {
                    appendLine("• не удалось проверить: ${gravatar.reason}")
                }
            }
            appendLine()
            appendLine("Полный «пробив» по утечкам в приложение не встроен.")
            appendLine("Подсказка: проверьте ник на том же имени через «Поиск по нику».")
        }.trim()

        return OsintResult.InfoReport(
            title = "Email · открытые данные",
            body = body,
        )
    }

    private fun StringBuilder.appendPolicy(policy: MxLookup.MailPolicy?) {
        if (policy == null) {
            appendLine("• не запрашивалось")
            return
        }
        if (policy.provider.isNotBlank()) {
            appendLine("• источник: ${policy.provider}")
        }
        when {
            policy.spf == null -> appendLine("• SPF: н/д")
            policy.spf.error != null -> appendLine("• SPF: ошибка — ${policy.spf.error}")
            policy.spf.missing -> appendLine("• SPF: запись не найдена")
            else -> {
                appendLine("• SPF: есть")
                appendLine("  `${policy.spf.record.take(120)}${if (policy.spf.record.length > 120) "…" else ""}`")
            }
        }
        when {
            policy.dmarc == null -> appendLine("• DMARC: н/д")
            policy.dmarc.error != null -> appendLine("• DMARC: ошибка — ${policy.dmarc.error}")
            policy.dmarc.missing -> appendLine("• DMARC: запись не найдена")
            else -> {
                val p = policy.dmarc.policy?.let { " (p=$it)" }.orEmpty()
                appendLine("• DMARC: есть$p")
                appendLine("  `${policy.dmarc.record.take(120)}${if (policy.dmarc.record.length > 120) "…" else ""}`")
            }
        }
    }
}
