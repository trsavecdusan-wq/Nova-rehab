package com.novarehab.utils

import android.content.Context

class IconTextManager(context: Context) {
    private val prefs = context.getSharedPreferences("icon_texts", Context.MODE_PRIVATE)

    private val defaults = mapOf(
        "pomoc" to "Potrebujem pomoc.",
        "piti" to "Zejna sem.",
        "jesti" to "Lacna sem.",
        "bolecina" to "Imam bolecine.",
        "kopalnica" to "Potrebujem v kopalnico.",
        "dobro" to "Dobro se pocutim.",
        "slabo" to "Ne pocutim se dobro.",
        "utrujena" to "Utrujena sem, rada bi pocivala.",
        "mraz" to "Mrzlo mi je.",
        "vroce" to "Vroce mi je.",
        "hvala" to "Hvala lepa.",
        "pridi_sem" to "Prosim pridi sem k meni.",
        "pocakaj" to "Pocakaj prosim.",
        "zdravilo" to "Cas je za zdravilo.",
        "telefon" to "Prosim prinesite mi telefon.",
        "tv" to "Prosim vklopite televizijo.",
        "postelja" to "Rada bi lezala v postelji.",
        "okno" to "Prosim odprite okno.",
        "vesela" to "Vesela sem.",
        "zalostna" to "Zalostna sem.",
        "jezna" to "Jezna sem.",
        "strah" to "Prestrasena sem.",
        "tesnoba" to "Tesnobno se pocutim.",
        "objemi" to "Bi me objel?"
    )

    private val submenuPrompts = mapOf(
        "piti" to "Kaj zelis piti?",
        "jesti" to "Kaj zelis jesti?",
        "slabo" to "Kaj te moti?",
        "pomoc" to "Kaksno pomoc potrebujes?"
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
}
