package com.sherlock.bot.data

/**
 * Builds likely public usernames from a full name (ФИО), then fed into the username catalog scan.
 * Focus: RU/BY Cyrillic → Latin + common nick patterns.
 */
object NameUsernameCandidates {

    const val MAX_CANDIDATES = UsernameQueue.MAX_NICKS

    private val nickPattern = Regex("^[A-Za-z0-9._-]{2,39}$")

    data class ParsedName(
        val display: String,
        val last: String,
        val first: String,
        val middle: String? = null,
    )

    fun parse(raw: String): ParsedName? {
        val parts = raw.trim().replace(Regex("\\s+"), " ").split(" ")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (parts.size < 2) return null
        val display = parts.joinToString(" ")
        if (display.length < 5) return null
        return ParsedName(
            display = display,
            last = parts[0],
            first = parts[1],
            middle = parts.getOrNull(2),
        )
    }

    /** Ordered unique Latin nick candidates. */
    fun candidates(parsed: ParsedName, limit: Int = MAX_CANDIDATES): List<String> {
        val last = transliterate(parsed.last)
        val first = transliterate(parsed.first)
        val middle = parsed.middle?.let { transliterate(it) }.orEmpty()
        if (last.length < 2 && first.length < 2) return emptyList()

        val fi = first.firstOrNull()?.toString().orEmpty()
        val li = last.firstOrNull()?.toString().orEmpty()
        val mi = middle.firstOrNull()?.toString().orEmpty()

        val raw = buildList {
            add(last)
            add(first)
            add(first + last)
            add(last + first)
            add("${first}_$last")
            add("${last}_$first")
            add("$first.$last")
            add("$last.$first")
            add("$fi$last")
            add("$last$fi")
            add("${fi}_$last")
            add("${last}_$fi")
            if (middle.length >= 2) {
                add(first + middle + last)
                add("${first}_$middle")
            }
            if (mi.isNotEmpty()) {
                add("$fi$mi$last")
            }
            // Compact BY/RU style without separators
            if (last.length in 3..12 && first.length in 2..12) {
                add(last + first.take(1))
                add(first.take(1) + last)
            }
        }

        val out = LinkedHashSet<String>()
        for (candidate in raw) {
            val nick = sanitize(candidate)
            if (nick.matches(nickPattern)) out += nick
            if (out.size >= limit) break
        }
        return out.toList()
    }

    fun fromRaw(raw: String, limit: Int = MAX_CANDIDATES): List<String> {
        val parsed = parse(raw) ?: return emptyList()
        return candidates(parsed, limit)
    }

    fun sanitize(value: String): String =
        value.lowercase()
            .replace(Regex("[^a-z0-9._-]+"), "")
            .trim('.', '-', '_')
            .take(39)

    /**
     * Practical scientific / passport-style transliteration for RU + BY letters.
     */
    fun transliterate(input: String): String {
        val s = input.trim().lowercase()
        if (s.isEmpty()) return ""
        if (s.all { it in 'a'..'z' || it.isDigit() }) return sanitize(s)

        val out = StringBuilder()
        for (ch in s) {
            out.append(MAP[ch] ?: if (ch in 'a'..'z' || ch.isDigit()) ch.toString() else "")
        }
        return sanitize(out.toString())
    }

    private val MAP: Map<Char, String> = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
        'е' to "e", 'ё' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i",
        'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
        'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
        'у' to "u", 'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch",
        'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "",
        'э' to "e", 'ю' to "yu", 'я' to "ya",
        // Belarusian
        'і' to "i", 'ў' to "u", '’' to "", '\'' to "",
        'ґ' to "g",
    )
}
