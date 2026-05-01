package com.novarehab.utils

import android.content.Context
import org.json.JSONObject

class IconTextManager(context: Context) {
    private val prefs = context.getSharedPreferences("icon_texts", Context.MODE_PRIVATE)

    private val defaults = mapOf(
        "pomoc" to "Potrebujem pomoč.",
        "piti" to "Žejna sem.",
        "jesti" to "Lačna sem.",
        "bolecina" to "Imam bolečine.",
        "kopalnica" to "Potrebujem v kopalnico.",
        "dobro" to "Dobro se počutim.",
        "slabo" to "Ne počutim se dobro.",
        "utrujena" to "Utrujena sem, rada bi počivala.",
        "mraz" to "Mrzlo mi je.",
        "vroce" to "Vroče mi je.",
        "hvala" to "Hvala lepa.",
        "pridi_sem" to "Prosim pridi sem k meni.",
        "pocakaj" to "Počakaj prosim.",
        "zdravilo" to "Čas je za zdravilo.",
        "telefon" to "Prosim prinesite mi telefon.",
        "tv" to "Prosim vklopite televizijo.",
        "postelja" to "Rada bi ležala v postelji.",
        "okno" to "Prosim odprite okno.",
        "vesela" to "Vesela sem.",
        "zalostna" to "Žalostna sem.",
        "jezna" to "Jezna sem.",
        "strah" to "Prestrašena sem.",
        "tesnoba" to "Tesnobno se počutim.",
        "objemi" to "Bi me objel?"
    )

    private val submenuPrompts = mapOf(
        "piti" to "Kaj želiš piti?",
        "jesti" to "Kaj želiš jesti?",
        "slabo" to "Kaj te moti?",
        "pomoc" to "Kakšno pomoč potrebuješ?"
    )

    fun getText(id: String): String {
        val direct = prefs.getString(id, null)
        if (!direct.isNullOrBlank()) return direct

        val oldSl = prefs.getString("${id}_sl", null)
        if (!oldSl.isNullOrBlank()) return oldSl

        return defaults[id] ?: ""
    }

    fun setText(id: String, text: String) {
        prefs.edit()
            .putString(id, text)
            .putString("${id}_sl", text)
            .remove("${id}_uk")
            .apply()
    }

    fun getText(id: String, lang: String): String {
        return getText(id)
    }

    fun setText(id: String, lang: String, text: String) {
        setText(id, text)
    }

    fun getSubmenuPrompt(id: String): String {
        return prefs.getString("${id}_submenu_prompt", null)
            ?.takeIf { it.isNotBlank() }
            ?: submenuPrompts[id].orEmpty()
    }

    fun setSubmenuPrompt(id: String, text: String) {
        prefs.edit().putString("${id}_submenu_prompt", text).apply()
    }

    fun exportTexts(ids: List<String>): JSONObject {
        val json = JSONObject()
        ids.forEach { id ->
            json.put(id, getText(id))
        }
        return json
    }

    fun exportSubmenuPrompts(ids: List<String>): JSONObject {
        val json = JSONObject()
        ids.forEach { id ->
            json.put(id, getSubmenuPrompt(id))
        }
        return json
    }

    fun importTexts(json: JSONObject) {
        val editor = prefs.edit()
        json.keys().forEach { id ->
            val value = json.optString(id)
            if (value.isNotBlank()) {
                editor.putString(id, value)
                editor.putString("${id}_sl", value)
                editor.remove("${id}_uk")
            }
        }
        editor.apply()
    }

    fun importSubmenuPrompts(json: JSONObject) {
        val editor = prefs.edit()
        json.keys().forEach { id ->
            val value = json.optString(id)
            if (value.isNotBlank()) {
                editor.putString("${id}_submenu_prompt", value)
            }
        }
        editor.apply()
    }
}
