package com.novarehab.utils

import android.content.Context

class IconTextManager(context: Context) {
    private val prefs = context.getSharedPreferences("icon_texts", Context.MODE_PRIVATE)

    private val defaults = mapOf(
        "pomoc" to "Potrebujem pomoč, prosim pridite",
        "piti" to "Žejna sem, prosim prinesite mi piti",
        "jesti" to "Lačna sem, bi rada jedla",
        "bolecina" to "Imam bolečine",
        "kopalnica" to "Potrebujem v kopalnico",
        "dobro" to "Dobro se počutim",
        "slabo" to "Ne počutim se dobro",
        "utrujena" to "Utrujena sem, rada bi počivala",
        "mraz" to "Mrzlica mi je",
        "vroce" to "Vroče mi je",
        "hvala" to "Hvala lepa",
        "pridi_sem" to "Prosim pridi sem k meni",
        "pocakaj" to "Počakaj prosim, ne odhajaj",
        "zdravilo" to "Čas je za zdravilo",
        "telefon" to "Prosim prinesite mi telefon",
        "tv" to "Prosim vklopite televizijo",
        "postelja" to "Rada bi ležala v postelji",
        "okno" to "Prosim odprite okno",
        "vesela" to "Vesela sem",
        "zalostna" to "Žalostna sem",
        "jezna" to "Jezna sem",
        "strah" to "Prestrašena sem",
        "tesnoba" to "Tesnobno se počutim",
        "objemi" to "Bi me objel?"
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
}
