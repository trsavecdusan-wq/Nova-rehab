package com.novarehab.utils

import android.content.Context

// Shrani/preberi custom tekste za ikone
class IconTextManager(context: Context) {
    private val prefs = context.getSharedPreferences("icon_texts", Context.MODE_PRIVATE)

    // Privzeti teksti
    private val defaults = mapOf(
        "pomoc_sl" to "Potrebujem pomoč, prosim pridite",
        "pomoc_uk" to "Мені потрібна допомога, будь ласка",
        "piti_sl" to "Žejna sem, prinesite mi piti",
        "piti_uk" to "Я хочу пити",
        "jesti_sl" to "Lačna sem, bi rada jedla",
        "jesti_uk" to "Я голодна, хочу їсти",
        "bolecina_sl" to "Imam bolečine",
        "bolecina_uk" to "У мене болить",
        "kopalnica_sl" to "Potrebujem v kopalnico",
        "kopalnica_uk" to "Мені потрібно в туалет",
        "dobro_sl" to "Dobro se počutim",
        "dobro_uk" to "Я почуваюся добре",
        "slabo_sl" to "Ne počutim se dobro",
        "slabo_uk" to "Я погано почуваюся",
        "utrujena_sl" to "Utrujena sem, rada bi počivala",
        "utrujena_uk" to "Я втомилась",
        "mraz_sl" to "Mrzlica mi je",
        "mraz_uk" to "Мені холодно",
        "vroce_sl" to "Vroče mi je",
        "vroce_uk" to "Мені жарко",
        "hvala_sl" to "Hvala lepa",
        "hvala_uk" to "Дякую щиро",
        "pridi_sem_sl" to "Prosim pridi sem k meni",
        "pridi_sem_uk" to "Будь ласка, підійдіть до мене",
        "pocakaj_sl" to "Počakaj prosim",
        "pocakaj_uk" to "Зачекай будь ласка",
        "zdravilo_sl" to "Čas je za zdravilo",
        "zdravilo_uk" to "Час приймати ліки",
        "telefon_sl" to "Prinesite mi telefon",
        "telefon_uk" to "Принесіть телефон",
        "tv_sl" to "Vklopite televizijo",
        "tv_uk" to "Увімкніть телевізор",
        "postelja_sl" to "Rada bi ležala",
        "postelja_uk" to "Хочу лягти",
        "okno_sl" to "Odprite okno",
        "okno_uk" to "Відкрийте вікно",
        "vesela_sl" to "Vesela sem",
        "vesela_uk" to "Я рада",
        "zalostna_sl" to "Žalostna sem",
        "zalostna_uk" to "Мені сумно",
        "jezna_sl" to "Jezna sem",
        "jezna_uk" to "Я сердита",
        "strah_sl" to "Prestrašena sem",
        "strah_uk" to "Мені страшно",
        "tesnoba_sl" to "Tesnobno se počutim",
        "tesnoba_uk" to "Мені тривожно",
        "objemi_sl" to "Bi me objel?",
        "objemi_uk" to "Обійми мене"
    )

    fun getText(id: String, lang: String): String {
        val key = "${id}_${lang}"
        return prefs.getString(key, defaults[key] ?: "") ?: ""
    }

    fun setText(id: String, lang: String, text: String) {
        prefs.edit().putString("${id}_${lang}", text).apply()
    }
}
