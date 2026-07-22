package com.sherlock.bot.data

import org.json.JSONObject

/**
 * DEF/ABC mobile code → operator label map (+7 RU/KZ heuristic).
 */
object DefDirectory {
    @Volatile
    private var codes: Map<String, String> = emptyMap()

    @Volatile
    private var updated: String = ""

    @Volatile
    private var note: String = ""

    fun loadFromJson(json: String) {
        val parsed = parse(json)
        codes = parsed.codes
        updated = parsed.updated
        note = parsed.note
    }

    fun isLoaded(): Boolean = codes.isNotEmpty()

    fun lookup(defCode: String): String {
        val label = codes[defCode]
        return if (label != null) {
            "вероятно $label"
        } else {
            "не определён по справочнику DEF${updated.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""}"
        }
    }

    fun updatedLabel(): String = updated

    fun noteText(): String = note

    fun codeCount(): Int = codes.size

    fun loadForTests(json: String = FALLBACK_JSON) {
        loadFromJson(json)
    }

    data class Parsed(
        val codes: Map<String, String>,
        val updated: String,
        val note: String,
    )

    fun parse(json: String): Parsed {
        val root = JSONObject(json)
        val mapObj = root.getJSONObject("codes")
        val map = buildMap {
            val keys = mapObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, mapObj.getString(key))
            }
        }
        require(map.isNotEmpty()) { "DEF catalog is empty" }
        return Parsed(
            codes = map,
            updated = root.optString("updated", ""),
            note = root.optString("note", ""),
        )
    }

    const val FALLBACK_JSON = """
    {
      "version": 1,
      "updated": "fallback",
      "note": "Встроенный запасной справочник DEF.",
      "codes": {
        "900": "Beeline / билайн",
        "903": "t2 / Tele2",
        "910": "MTS / МТС",
        "916": "MTS / МТС",
        "920": "MegaFon / МегаФон",
        "926": "MegaFon / МегаФон",
        "960": "t2 / Tele2",
        "961": "t2 / Tele2",
        "999": "Yota / MegaFon"
      }
    }
    """
}
