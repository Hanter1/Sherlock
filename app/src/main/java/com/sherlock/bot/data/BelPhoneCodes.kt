package com.sherlock.bot.data

/**
 * Belarus (+375) mobile prefix heuristics (not HLR).
 * Format: 375 + AB + 7 digits (12 digits total).
 */
object BelPhoneCodes {
    private val codes = mapOf(
        "25" to "life:) / A1 (возможны MVNO)",
        "29" to "A1 / МТС Беларусь (возможны MVNO)",
        "33" to "МТС Беларусь / A1",
        "44" to "A1 (velcom)",
    )

    fun lookup(prefix2: String): String {
        val label = codes[prefix2]
        return if (label != null) {
            "вероятно $label"
        } else {
            "мобильный РБ (префикс $prefix2) — оператор не в справочнике"
        }
    }

    fun knownPrefixes(): Set<String> = codes.keys
}
