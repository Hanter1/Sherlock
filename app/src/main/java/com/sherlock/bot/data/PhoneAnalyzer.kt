package com.sherlock.bot.data

/**
 * Phone normalization with Belarus (+375) first, then +7 DEF, +380 / +1 / +44.
 */
object PhoneAnalyzer {

    data class ParsedPhone(
        val e164: String,
        val digits: String,
        val countryLabel: String,
        val countryCode: String,
        val defCode: String? = null,
        val operatorHint: String? = null,
        val regionNote: String? = null,
    )

    /**
     * Returns 11-digit national number starting with 7, or null if input is not a plausible +7 number.
     */
    fun normalizeRu(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        if (digits.startsWith("375")) return null
        if (digits.length == 10 && digits.startsWith("0")) return null
        val normalized = when {
            digits.length == 11 && (digits.startsWith("7") || digits.startsWith("8")) ->
                "7" + digits.drop(1)
            digits.length == 10 -> "7$digits"
            else -> digits
        }
        return normalized.takeIf { it.length == 11 && it.startsWith("7") }
    }

    /** 375 + 9 digits → 12 total. */
    fun normalizeBy(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.startsWith("375") && digits.length == 12 -> digits
            // local 8 0XX … sometimes written without +375
            digits.startsWith("80") && digits.length == 11 -> "375${digits.drop(2)}"
            else -> null
        }?.takeIf { it.startsWith("375") && it.length == 12 }
    }

    fun parse(raw: String): ParsedPhone? {
        val digits = raw.filter { it.isDigit() }
        if (digits.length < 10) return null

        normalizeBy(raw)?.let { by ->
            val prefix = by.substring(3, 5)
            return ParsedPhone(
                e164 = "+$by",
                digits = by,
                countryLabel = "Беларусь",
                countryCode = "375",
                defCode = prefix,
                operatorHint = BelPhoneCodes.lookup(prefix),
                regionNote = "Эвристика по префиксу РБ (не HLR). Формат: +375 XX XXX-XX-XX",
            )
        }

        // Ukraine: 380 + 9 digits
        if (digits.startsWith("380") && digits.length == 12) {
            return ParsedPhone(
                e164 = "+$digits",
                digits = digits,
                countryLabel = "Украина",
                countryCode = "380",
                regionNote = "Оператор по справочнику РБ/РФ не определяется",
            )
        }
        if (digits.startsWith("0") && digits.length == 10) {
            val intl = "380${digits.drop(1)}"
            return ParsedPhone(
                e164 = "+$intl",
                digits = intl,
                countryLabel = "Украина",
                countryCode = "380",
                regionNote = "Нормализовано из 0XXXXXXXXX → +380…",
            )
        }

        if (digits.startsWith("1") && digits.length == 11) {
            return ParsedPhone(
                e164 = "+$digits",
                digits = digits,
                countryLabel = "США / Канада (NANP)",
                countryCode = "1",
                regionNote = "Код зоны: ${digits.substring(1, 4)} · без привязки к оператору",
            )
        }

        if (digits.startsWith("44") && digits.length in 11..13) {
            return ParsedPhone(
                e164 = "+$digits",
                digits = digits,
                countryLabel = "Великобритания",
                countryCode = "44",
                regionNote = "Без локального справочника операторов",
            )
        }

        normalizeRu(raw)?.let { ru ->
            val code = ru.substring(1, 4)
            return ParsedPhone(
                e164 = "+$ru",
                digits = ru,
                countryLabel = "Россия / Казахстан",
                countryCode = "7",
                defCode = code,
                operatorHint = guessRuOperator(code),
                regionNote = "DEF-эвристика по assets/def_codes.json (не HLR)",
            )
        }

        return null
    }

    fun analyze(raw: String): OsintResult.InfoReport {
        val parsed = parse(raw)
        if (parsed == null) {
            return OsintResult.InfoReport(
                title = "Телефон",
                body = buildString {
                    appendLine("Не распознал номер.")
                    appendLine()
                    appendLine("Поддерживаются (приоритет — Беларусь):")
                    appendLine("• `+375…` — Беларусь (+ префикс оператора)")
                    appendLine("• `+7…` — Россия / Казахстан (+ DEF)")
                    appendLine("• `+380…` — Украина")
                    appendLine("• `+1…` — США / Канада")
                    appendLine("• `+44…` — Великобритания")
                    appendLine()
                    appendLine("Пример РБ: `+375291234567`")
                }.trim(),
            )
        }

        val updated = DefDirectory.updatedLabel()
        val isBy = parsed.countryCode == "375"
        return OsintResult.InfoReport(
            title = "Телефон · открытые данные",
            body = buildString {
                appendLine("Номер: `${parsed.e164}`")
                appendLine("Страна: ${parsed.countryLabel} (код +${parsed.countryCode})")
                if (parsed.defCode != null) {
                    if (isBy) {
                        appendLine("Префикс РБ: ${parsed.defCode}")
                    } else {
                        appendLine("DEF-код: ${parsed.defCode}")
                    }
                    appendLine("Оператор (эвристика): ${parsed.operatorHint}")
                    if (!isBy && updated.isNotBlank()) {
                        appendLine("Справочник DEF: $updated")
                    }
                }
                parsed.regionNote?.let { appendLine(it) }
                appendLine()
                appendLine("Что проверено здесь:")
                appendLine("• нормализация и страна по префиксу")
                if (isBy) {
                    appendLine("• эвристика мобильных префиксов Беларуси (25/29/33/44)")
                } else if (parsed.defCode != null) {
                    appendLine("• справочник DEF из assets/def_codes.json")
                }
                appendLine()
                appendLine("Чего нет в приложении (намеренно):")
                appendLine("• ФИО владельца, утечки, GetContact и закрытые базы")
                appendLine()
                val note = DefDirectory.noteText()
                if (note.isNotBlank() && parsed.defCode != null && !isBy) {
                    appendLine(note)
                    appendLine()
                }
                appendLine("Используйте только законно и с уважением к приватности (в т.ч. по праву РБ).")
            }.trim(),
        )
    }

    fun guessRuOperator(defCode: String): String = DefDirectory.lookup(defCode)
}
