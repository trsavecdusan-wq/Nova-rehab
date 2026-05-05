package com.novarehab.communication.data

import android.content.Context
import com.novarehab.R
import com.novarehab.communication.model.CommunicationItem
import com.novarehab.utils.CustomCommIcon
import org.json.JSONArray
import org.json.JSONObject

object CommunicationRepository {
    private const val MAX_MAIN_ITEMS = 12
    private const val MAX_SUBMENU_ITEMS = 6

    fun load(context: Context, language: String): List<CommunicationItem> {
        val normalizedLanguage = normalizeLanguage(language)
        return runCatching {
            parseItems(context, readAsset(context, "communication/$normalizedLanguage.json"))
        }.getOrElse {
            loadFallback()
        }
    }

    fun loadFallback(): List<CommunicationItem> = listOf(
        CommunicationItem(
            id = "piti",
            label = "ŽEJNA",
            shortLabel = "ŽEJNA",
            ttsText = "Žejna sem.",
            questionText = "Kaj želiš piti?",
            category = "basic",
            icon = "comm_piti",
            iconRes = R.drawable.comm_piti,
            priority = 10,
            emotionalTags = listOf("need"),
            aiPromptHint = "Predlagaj kratek stavek za pijačo.",
            logEventType = "drink",
            children = listOf(
                CommunicationItem("voda", "VODA", "Želim vodo.", R.drawable.comm_piti, shortLabel = "VODA", category = "drink", icon = "comm_piti", priority = 1, aiPromptHint = "Voda"),
                CommunicationItem("caj", "ČAJ", "Želim čaj.", R.drawable.comm_piti, shortLabel = "ČAJ", category = "drink", icon = "comm_piti", priority = 2, aiPromptHint = "Čaj"),
                CommunicationItem("sok", "SOK", "Želim sok.", R.drawable.comm_piti, shortLabel = "SOK", category = "drink", icon = "comm_piti", priority = 3, aiPromptHint = "Sok")
            )
        ),
        CommunicationItem(
            id = "jesti",
            label = "LAČNA",
            shortLabel = "LAČNA",
            ttsText = "Lačna sem.",
            questionText = "Kaj želiš jesti?",
            category = "basic",
            icon = "comm_jesti",
            iconRes = R.drawable.comm_jesti,
            priority = 20,
            emotionalTags = listOf("need"),
            aiPromptHint = "Predlagaj kratek stavek za hrano.",
            logEventType = "food",
            children = listOf(
                CommunicationItem("zajtrk", "ZAJTRK", "Želim zajtrk.", R.drawable.comm_jesti, shortLabel = "ZAJTRK", category = "food", icon = "comm_jesti"),
                CommunicationItem("kosilo", "KOSILO", "Želim kosilo.", R.drawable.comm_jesti, shortLabel = "KOSILO", category = "food", icon = "comm_jesti"),
                CommunicationItem("prigrizek", "PRIGRIZEK", "Želim prigrizek.", R.drawable.comm_jesti, shortLabel = "PRIGRIZEK", category = "food", icon = "comm_jesti")
            )
        ),
        CommunicationItem(
            id = "slabo",
            label = "SLABO",
            shortLabel = "SLABO",
            ttsText = "Ne počutim se dobro.",
            questionText = "Kaj te moti?",
            category = "feeling",
            icon = "comm_slabo",
            iconRes = R.drawable.comm_slabo,
            priority = 30,
            emotionalTags = listOf("discomfort"),
            aiPromptHint = "Predlagaj kratek stavek za počutje.",
            logEventType = "feeling",
            children = listOf(
                CommunicationItem("bolecina", "BOLEČINA", "Imam bolečino.", R.drawable.comm_bolecina, shortLabel = "BOLEČINA", category = "feeling", icon = "comm_bolecina"),
                CommunicationItem("slabost", "SLABOST", "Slabo mi je.", R.drawable.comm_slabo, shortLabel = "SLABOST", category = "feeling", icon = "comm_slabo"),
                CommunicationItem("tesnoba", "TESNOBA", "Čutim tesnobo.", R.drawable.comm_tesnoba, shortLabel = "TESNOBA", category = "feeling", icon = "comm_tesnoba")
            )
        ),
        CommunicationItem(
            id = "pomoc",
            label = "POMOČ",
            shortLabel = "POMOČ",
            ttsText = "Potrebujem pomoč.",
            questionText = "Kakšno pomoč potrebuješ?",
            category = "help",
            icon = "comm_pomoc",
            iconRes = R.drawable.comm_pomoc,
            priority = 40,
            emotionalTags = listOf("help"),
            aiPromptHint = "Predlagaj kratek stavek za pomoč.",
            logEventType = "help",
            children = listOf(
                CommunicationItem("pomoc_pridi", "PRIDI", "Prosim pridi k meni.", R.drawable.comm_pridi_sem, shortLabel = "PRIDI", category = "help", icon = "comm_pridi_sem"),
                CommunicationItem("pomoc_dvigni", "DVIGNI ME", "Prosim dvigni me.", R.drawable.comm_pomoc, shortLabel = "DVIGNI ME", category = "help", icon = "comm_pomoc"),
                CommunicationItem("pomoc_polozaj", "POPRAVI POLOŽAJ", "Prosim popravi moj položaj.", R.drawable.comm_postelja, shortLabel = "POPRAVI", category = "help", icon = "comm_postelja")
            )
        ),
        CommunicationItem("kopalnica", "WC", "Potrebujem v kopalnico.", R.drawable.comm_kopalnica, shortLabel = "WC", category = "basic", icon = "comm_kopalnica", priority = 50),
        CommunicationItem("dobro", "DOBRO", "Dobro se počutim.", R.drawable.comm_dobro, shortLabel = "DOBRO", category = "feeling", icon = "comm_dobro", priority = 60),
        CommunicationItem("utrujena", "UTRUJENA", "Utrujena sem, rada bi počivala.", R.drawable.comm_utrujena, shortLabel = "UTRUJENA", category = "feeling", icon = "comm_utrujena", emotionalTags = listOf("possibleFatigue"), priority = 70),
        CommunicationItem("mraz", "MRAZ", "Mrzlo mi je.", R.drawable.comm_mraz, shortLabel = "MRAZ", category = "comfort", icon = "comm_mraz", priority = 80),
        CommunicationItem("vroce", "VROČE", "Vroče mi je.", R.drawable.comm_vroce, shortLabel = "VROČE", category = "comfort", icon = "comm_vroce", priority = 90),
        CommunicationItem("hvala", "HVALA", "Hvala lepa.", R.drawable.comm_hvala, shortLabel = "HVALA", category = "social", icon = "comm_hvala", priority = 100),
        CommunicationItem("pridi_sem", "PRIDI", "Prosim pridi sem k meni.", R.drawable.comm_pridi_sem, shortLabel = "PRIDI", category = "help", icon = "comm_pridi_sem", priority = 110),
        CommunicationItem("pocakaj", "POČAKAJ", "Počakaj prosim.", R.drawable.comm_pocakaj, shortLabel = "POČAKAJ", category = "social", icon = "comm_pocakaj", priority = 120),
        CommunicationItem("zdravilo", "ZDRAVILO", "Čas je za zdravilo.", R.drawable.comm_zdravilo, shortLabel = "ZDRAVILO", category = "care", icon = "comm_zdravilo", priority = 130),
        CommunicationItem("telefon", "TELEFON", "Prinesite mi telefon.", R.drawable.comm_telefon, shortLabel = "TELEFON", category = "care", icon = "comm_telefon", priority = 140),
        CommunicationItem("tv", "TV", "Vklopite televizijo.", R.drawable.comm_tv, shortLabel = "TV", category = "activity", icon = "comm_tv", priority = 150),
        CommunicationItem("postelja", "POSTELJA", "Rada bi ležala.", R.drawable.comm_postelja, shortLabel = "POSTELJA", category = "comfort", icon = "comm_postelja", priority = 160),
        CommunicationItem("okno", "OKNO", "Odprite okno.", R.drawable.comm_okno, shortLabel = "OKNO", category = "comfort", icon = "comm_okno", priority = 170),
        CommunicationItem("vesela", "VESELA", "Vesela sem.", R.drawable.comm_vesela, shortLabel = "VESELA", category = "emotion", icon = "comm_vesela", priority = 180),
        CommunicationItem("zalostna", "ŽALOSTNA", "Žalostna sem.", R.drawable.comm_zalostna, shortLabel = "ŽALOSTNA", category = "emotion", icon = "comm_zalostna", priority = 190),
        CommunicationItem("jezna", "JEZNA", "Jezna sem.", R.drawable.comm_jezna, shortLabel = "JEZNA", category = "emotion", icon = "comm_jezna", priority = 200),
        CommunicationItem("strah", "STRAH", "Prestrašena sem.", R.drawable.comm_strah, shortLabel = "STRAH", category = "emotion", icon = "comm_strah", priority = 210),
        CommunicationItem("tesnoba_sama", "TESNOBA", "Tesnobno se počutim.", R.drawable.comm_tesnoba, shortLabel = "TESNOBA", category = "emotion", icon = "comm_tesnoba", priority = 220),
        CommunicationItem("objemi", "OBJEMI", "Bi me objel?", R.drawable.comm_objemi, shortLabel = "OBJEMI", category = "social", icon = "comm_objemi", priority = 230)
    )

    fun getMainItems(context: Context, language: String): List<CommunicationItem> {
        return normalizeMainItems(load(context, language))
    }

    fun getChildren(context: Context, language: String, parentId: String): List<CommunicationItem> {
        return normalizeChildren(searchById(context, language, parentId)?.children.orEmpty())
    }

    fun searchById(context: Context, language: String, id: String): CommunicationItem? {
        return flatten(load(context, language)).firstOrNull { it.id == id }
    }

    fun customItems(items: List<CustomCommIcon>): List<CommunicationItem> {
        return normalizeMainItems(
            items
            .filter { it.text.isNotBlank() || it.title.isNotBlank() }
            .map { item ->
                CommunicationItem(
                    id = item.id,
                    label = item.title.ifBlank { item.text }.uppercase(),
                    shortLabel = item.title.ifBlank { item.text }.uppercase(),
                    ttsText = item.text.ifBlank { item.title },
                    iconRes = R.drawable.ic_contact_default,
                    category = "custom",
                    icon = "ic_contact_default",
                    enabled = item.enabled,
                    pinnedMain = item.pinnedMain,
                    pinnedVideo = item.pinnedVideo
                )
            }
        )
    }

    private fun parseItems(context: Context, raw: String): List<CommunicationItem> {
        val root = JSONObject(raw)
        val array = root.optJSONArray("items") ?: JSONArray()
        val parsedItems = (0 until array.length())
            .mapNotNull { index -> array.optJSONObject(index)?.let { parseItem(context, it) } }
        return normalizeMainItems(parsedItems).ifEmpty { loadFallback() }
    }

    private fun parseItem(context: Context, json: JSONObject): CommunicationItem {
        val iconName = json.optString("icon", "ic_contact_default")
        val childrenArray = json.optJSONArray("children") ?: JSONArray()
        val children = (0 until childrenArray.length())
            .mapNotNull { index -> childrenArray.optJSONObject(index)?.let { parseItem(context, it) } }
            .let { normalizeChildren(it) }

        return CommunicationItem(
            id = json.optString("id"),
            label = json.optString("label"),
            shortLabel = json.optString("shortLabel", json.optString("label")),
            ttsText = json.optString("ttsText"),
            questionText = json.optString("questionText"),
            category = json.optString("category"),
            icon = iconName,
            iconRes = resolveDrawable(context, iconName),
            children = children,
            priority = json.optInt("priority", 0),
            emotionalTags = json.optJSONArray("emotionalTags").toStringList(),
            aiPromptHint = json.optString("aiPromptHint"),
            requiresConfirmation = json.optBoolean("requiresConfirmation", false),
            enabled = json.optBoolean("enabled", true),
            arasaacKey = json.optString("arasaacKey"),
            symbolKey = json.optString("symbolKey"),
            logEventType = json.optString("logEventType", "iconClicked"),
            pinnedMain = json.optBoolean("pinnedMain", json.optBoolean("pinned", false)),
            pinnedVideo = json.optBoolean("pinnedVideo", json.optBoolean("pinned", false)),
            usageRank = json.optInt("usageRank", 0)
        )
    }

    private fun normalizeMainItems(items: List<CommunicationItem>): List<CommunicationItem> {
        return items
            .filter { it.enabled && it.id.isNotBlank() && it.label.isNotBlank() && it.ttsText.isNotBlank() }
            .map { item -> item.copy(children = normalizeChildren(item.children)) }
            .sortedBy { it.priority }
            .take(MAX_MAIN_ITEMS)
    }

    private fun normalizeChildren(items: List<CommunicationItem>): List<CommunicationItem> {
        return items
            .filter { it.enabled && it.id.isNotBlank() && it.label.isNotBlank() && it.ttsText.isNotBlank() }
            .sortedBy { it.priority }
            .take(MAX_SUBMENU_ITEMS)
    }

    private fun flatten(items: List<CommunicationItem>): List<CommunicationItem> {
        return items + items.flatMap { flatten(it.children) }
    }

    private fun readAsset(context: Context, path: String): String {
        return context.assets.open(path).use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        }
    }

    private fun normalizeLanguage(language: String): String {
        return when (language.lowercase()) {
            "ua" -> "uk"
            "sl", "uk", "en", "de", "hr", "sr", "ru" -> language.lowercase()
            else -> "sl"
        }
    }

    private fun resolveDrawable(context: Context, name: String): Int {
        val id = context.resources.getIdentifier(name, "drawable", context.packageName)
        return if (id != 0) id else R.drawable.ic_contact_default
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }
    }
}
