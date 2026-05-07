package com.novarehab.communication.data

import android.content.Context
import android.util.Log
import com.novarehab.R
import com.novarehab.communication.model.CommunicationItem
import com.novarehab.utils.CustomCommIcon
import org.json.JSONArray
import org.json.JSONObject

object CommunicationRepository {
    private const val MAX_SUBMENU_ITEMS = 6

    fun load(context: Context, language: String): List<CommunicationItem> {
        val normalizedLanguage = normalizeLanguage(language)
        return runCatching {
            val slDocument = parseDocument(context, readAsset(context, "communication/sl.json"))
            if (slDocument.topLevelIds.isEmpty()) return@runCatching loadFallback(context)

            if (normalizedLanguage == "sl") {
                buildTopLevelItems(
                    topLevelIds = slDocument.topLevelIds,
                    primaryItems = slDocument.itemsById,
                    fallbackItems = slDocument.itemsById
                )
            } else {
                val localizedDocument = runCatching {
                    parseDocument(context, readAsset(context, "communication/$normalizedLanguage.json"))
                }.getOrNull()

                buildTopLevelItems(
                    topLevelIds = slDocument.topLevelIds,
                    primaryItems = localizedDocument?.itemsById.orEmpty(),
                    fallbackItems = slDocument.itemsById
                )
            }
        }.getOrElse {
            loadFallback(context)
        }.ifEmpty {
            loadFallback(context)
        }
    }

    fun loadFallback(context: Context): List<CommunicationItem> {
        return runCatching {
            val slDocument = parseDocument(context, readAsset(context, "communication/sl.json"))
            buildTopLevelItems(
                topLevelIds = slDocument.topLevelIds,
                primaryItems = slDocument.itemsById,
                fallbackItems = slDocument.itemsById
            )
        }.getOrElse {
            listOf(
                CommunicationItem("piti", "ÄąËťEJNA", "ÄąËťejna sem.", R.drawable.comm_piti, shortLabel = "ÄąËťEJNA", questionText = "Kaj ÄąÄľeliÄąË‡ piti?", category = "basic", icon = "comm_piti", priority = 10),
                CommunicationItem("jesti", "LAĂ„ĹšNA", "LaĂ„Ĺ¤na sem.", R.drawable.comm_jesti, shortLabel = "LAĂ„ĹšNA", questionText = "Kaj ÄąÄľeliÄąË‡ jesti?", category = "basic", icon = "comm_jesti", priority = 20),
                CommunicationItem("slabo", "SLABO", "Ne poĂ„Ĺ¤utim se dobro.", R.drawable.comm_slabo, shortLabel = "SLABO", questionText = "Kaj te moti?", category = "feeling", icon = "comm_slabo", priority = 30),
                CommunicationItem("pomoc", "POMOĂ„Ĺš", "Potrebujem pomoĂ„Ĺ¤.", R.drawable.comm_pomoc, shortLabel = "POMOĂ„Ĺš", questionText = "KakÄąË‡no pomoĂ„Ĺ¤ potrebujeÄąË‡?", category = "help", icon = "comm_pomoc", priority = 40),
                CommunicationItem("kopalnica", "WC", "Potrebujem v kopalnico.", R.drawable.comm_kopalnica, shortLabel = "WC", category = "basic", icon = "comm_kopalnica", priority = 50),
                CommunicationItem("dobro", "DOBRO", "Dobro se poĂ„Ĺ¤utim.", R.drawable.comm_dobro, shortLabel = "DOBRO", category = "feeling", icon = "comm_dobro", priority = 60),
                CommunicationItem("utrujena", "UTRUJENA", "Utrujena sem, rada bi poĂ„Ĺ¤ivala.", R.drawable.comm_utrujena, shortLabel = "UTRUJENA", category = "feeling", icon = "comm_utrujena", priority = 70),
                CommunicationItem("mraz", "MRAZ", "Mrzlo mi je.", R.drawable.comm_mraz, shortLabel = "MRAZ", category = "comfort", icon = "comm_mraz", priority = 80),
                CommunicationItem("vroce", "VROĂ„ĹšE", "VroĂ„Ĺ¤e mi je.", R.drawable.comm_vroce, shortLabel = "VROĂ„ĹšE", category = "comfort", icon = "comm_vroce", priority = 90),
                CommunicationItem("hvala", "HVALA", "Hvala lepa.", R.drawable.comm_hvala, shortLabel = "HVALA", category = "social", icon = "comm_hvala", priority = 100),
                CommunicationItem("pridi_sem", "PRIDI", "Prosim pridi sem k meni.", R.drawable.comm_pridi_sem, shortLabel = "PRIDI", category = "help", icon = "comm_pridi_sem", priority = 110),
                CommunicationItem("pocakaj", "POĂ„ĹšAKAJ", "PoĂ„Ĺ¤akaj prosim.", R.drawable.comm_pocakaj, shortLabel = "POĂ„ĹšAKAJ", category = "social", icon = "comm_pocakaj", priority = 120)
            )
        }
    }
    fun getMainItems(
        context: Context,
        language: String,
        customIcons: List<CustomCommIcon> = emptyList()
    ): List<CommunicationItem> {
        return normalizeMainItems(mergeConfiguredItems(load(context, language), customIcons))
    }

    fun getChildren(
        context: Context,
        language: String,
        parentId: String,
        customIcons: List<CustomCommIcon> = emptyList()
    ): List<CommunicationItem> {
        return normalizeChildren(searchById(context, language, id = parentId, customIcons = customIcons)?.children.orEmpty())
    }

    fun searchById(
        context: Context,
        language: String,
        id: String,
        customIcons: List<CustomCommIcon> = emptyList()
    ): CommunicationItem? {
        return flatten(mergeConfiguredItems(load(context, language), customIcons)).firstOrNull { it.id == id }
    }

    fun customItems(items: List<CustomCommIcon>): List<CommunicationItem> {
        return normalizeMainItems(
            items
                .filter { it.showOnMain && (it.text.isNotBlank() || it.title.isNotBlank()) }
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
                        pinnedVideo = item.pinnedVideo,
                        children = emptyList()
                    )
                }
        )
    }

    private fun parseDocument(context: Context, raw: String): ParsedDocument {
        val root = JSONObject(raw)
        val array = root.optJSONArray("items") ?: JSONArray()
        val itemsById = linkedMapOf<String, ParsedItem>()
        val declaredMainIds = root.optJSONArray("main").toStringList()
        val parsedIds = mutableListOf<String>()
        val allChildrenIds = linkedSetOf<String>()

        for (index in 0 until array.length()) {
            val itemJson = array.optJSONObject(index) ?: continue
            val parsed = parseParsedItem(context, itemJson, itemsById)
            if (parsed.id.isNotBlank()) {
                parsedIds += parsed.id
                allChildrenIds += parsed.childIds
            }
        }

        val topLevelIds = if (declaredMainIds.isNotEmpty()) {
            declaredMainIds.filter { it in itemsById }
        } else {
            parsedIds.filter { it !in allChildrenIds }
        }

        return ParsedDocument(
            topLevelIds = topLevelIds.distinct(),
            itemsById = itemsById
        )
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

    private fun parseParsedItem(
        context: Context,
        json: JSONObject,
        itemsById: MutableMap<String, ParsedItem>
    ): ParsedItem {
        val iconName = json.optString("icon", "ic_contact_default")
        val childIds = mutableListOf<String>()
        val childrenArray = json.optJSONArray("children") ?: JSONArray()

        for (index in 0 until childrenArray.length()) {
            val childObject = childrenArray.optJSONObject(index)
            if (childObject != null) {
                val child = parseParsedItem(context, childObject, itemsById)
                if (child.id.isNotBlank()) {
                    childIds += child.id
                }
                continue
            }

            val childId = childrenArray.optString(index).trim()
            if (childId.isNotBlank()) {
                childIds += childId
            }
        }

        return ParsedItem(
            id = json.optString("id"),
            label = json.optString("label"),
            shortLabel = json.optString("shortLabel", json.optString("label")),
            ttsText = json.optString("ttsText"),
            questionText = json.optString("questionText"),
            category = json.optString("category"),
            icon = iconName,
            iconRes = resolveDrawable(context, iconName),
            childIds = childIds.distinct(),
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
        ).also { parsed ->
            if (parsed.id.isNotBlank()) {
                itemsById[parsed.id] = parsed
            }
        }
    }

    private fun buildTopLevelItems(
        topLevelIds: List<String>,
        primaryItems: Map<String, ParsedItem>,
        fallbackItems: Map<String, ParsedItem>
    ): List<CommunicationItem> {
        return normalizeMainItems(
            topLevelIds.mapNotNull { id ->
                buildStructuredItem(
                    id = id,
                    primaryItems = primaryItems,
                    fallbackItems = fallbackItems,
                    visited = emptySet()
                )
            }
        )
    }

    private fun buildStructuredItem(
        id: String,
        primaryItems: Map<String, ParsedItem>,
        fallbackItems: Map<String, ParsedItem>,
        visited: Set<String>
    ): CommunicationItem? {
        if (id.isBlank() || id in visited) return null

        val fallback = fallbackItems[id]
        val primary = primaryItems[id]
        val template = fallback ?: primary ?: return null

        val childIds = if (fallback?.childIds?.isNotEmpty() == true) fallback.childIds else template.childIds
        val nextVisited = visited + id
        val children = normalizeChildren(
            childIds.mapNotNull { childId ->
                buildStructuredItem(childId, primaryItems, fallbackItems, nextVisited)
            }
        )

        val label = primary?.label.takeUnless { it.isNullOrBlank() }
            ?: fallback?.label.takeUnless { it.isNullOrBlank() }
            ?: template.id.uppercase()
        val shortLabel = primary?.shortLabel.takeUnless { it.isNullOrBlank() }
            ?: fallback?.shortLabel.takeUnless { it.isNullOrBlank() }
            ?: label
        val ttsText = primary?.ttsText.takeUnless { it.isNullOrBlank() }
            ?: fallback?.ttsText.takeUnless { it.isNullOrBlank() }
            ?: label
        val questionText = primary?.questionText.takeUnless { it.isNullOrBlank() }
            ?: fallback?.questionText.takeUnless { it.isNullOrBlank() }
            ?: ttsText
        val iconName = primary?.icon.takeUnless { it.isNullOrBlank() }
            ?: fallback?.icon.takeUnless { it.isNullOrBlank() }
            ?: template.icon
        if (primary == null || primary.label.isBlank() || primary.ttsText.isBlank() || (childIds.isNotEmpty() && primary.questionText.isBlank())) {
            Log.w("NovaRehabComm", "Missing translation for icon id=$id, using Slovenian fallback text.")
        }
        val iconRes = when {
            primary != null && primary.iconRes != R.drawable.ic_contact_default -> primary.iconRes
            fallback != null -> fallback.iconRes
            else -> template.iconRes
        }

        return CommunicationItem(
            id = template.id,
            label = label,
            shortLabel = shortLabel,
            ttsText = ttsText,
            questionText = questionText,
            category = primary?.category.takeUnless { it.isNullOrBlank() }
                ?: fallback?.category.takeUnless { it.isNullOrBlank() }
                ?: template.category,
            icon = iconName,
            iconRes = iconRes,
            children = children,
            priority = fallback?.priority ?: primary?.priority ?: template.priority,
            emotionalTags = primary?.emotionalTags?.takeIf { it.isNotEmpty() }
                ?: fallback?.emotionalTags?.takeIf { it.isNotEmpty() }
                ?: template.emotionalTags,
            aiPromptHint = primary?.aiPromptHint.takeUnless { it.isNullOrBlank() }
                ?: fallback?.aiPromptHint.takeUnless { it.isNullOrBlank() }
                ?: template.aiPromptHint,
            requiresConfirmation = primary?.requiresConfirmation ?: fallback?.requiresConfirmation ?: template.requiresConfirmation,
            enabled = primary?.enabled ?: fallback?.enabled ?: template.enabled,
            arasaacKey = primary?.arasaacKey.takeUnless { it.isNullOrBlank() }
                ?: fallback?.arasaacKey.takeUnless { it.isNullOrBlank() }
                ?: template.arasaacKey,
            symbolKey = primary?.symbolKey.takeUnless { it.isNullOrBlank() }
                ?: fallback?.symbolKey.takeUnless { it.isNullOrBlank() }
                ?: template.symbolKey,
            logEventType = primary?.logEventType.takeUnless { it.isNullOrBlank() }
                ?: fallback?.logEventType.takeUnless { it.isNullOrBlank() }
                ?: template.logEventType,
            pinnedMain = fallback?.pinnedMain ?: primary?.pinnedMain ?: template.pinnedMain,
            pinnedVideo = fallback?.pinnedVideo ?: primary?.pinnedVideo ?: template.pinnedVideo,
            usageRank = fallback?.usageRank ?: primary?.usageRank ?: template.usageRank
        )
    }

    private fun mergeConfiguredItems(
        baseItems: List<CommunicationItem>,
        customIcons: List<CustomCommIcon>
    ): List<CommunicationItem> {
        if (customIcons.isEmpty()) return baseItems

        val overrideMap = customIcons
            .filter { it.id.isNotBlank() }
            .associateBy { it.id }
        val baseMap = flatten(baseItems).associateBy { it.id }
        val topLevelIds = mutableListOf<String>()

        baseItems.forEach { item ->
            val override = overrideMap[item.id]
            if ((override?.showOnMain ?: true) && (override?.enabled ?: item.enabled)) {
                topLevelIds += item.id
            }
        }

        customIcons.forEach { item ->
            val hasContent = item.title.isNotBlank() || item.text.isNotBlank() || item.children.isNotEmpty()
            if (item.showOnMain && item.enabled && item.id !in topLevelIds && (item.id in baseMap || hasContent)) {
                topLevelIds += item.id
            }
        }

        return topLevelIds.mapNotNull { id ->
            buildConfiguredItem(id, baseMap, overrideMap, emptySet())
        }
    }

    private fun buildConfiguredItem(
        id: String,
        baseMap: Map<String, CommunicationItem>,
        overrideMap: Map<String, CustomCommIcon>,
        visited: Set<String>
    ): CommunicationItem? {
        if (id.isBlank() || id in visited) return null

        val base = baseMap[id]
        val override = overrideMap[id]
        if (base == null && override == null) return null

        val enabled = override?.enabled ?: base?.enabled ?: true
        val iconRes = base?.iconRes ?: R.drawable.ic_contact_default
        if (!enabled || iconRes == 0) return null

        val childIds = when {
            override != null -> override.children.distinct()
            base != null -> base.children.map { it.id }.distinct()
            else -> emptyList()
        }

        val nextVisited = visited + id
        val children = normalizeChildren(
            childIds.mapNotNull { childId -> buildConfiguredItem(childId, baseMap, overrideMap, nextVisited) }
        )

        if (base != null) {
            return base.copy(
                children = children,
                enabled = enabled,
                pinnedMain = override?.pinnedMain ?: base.pinnedMain,
                pinnedVideo = override?.pinnedVideo ?: base.pinnedVideo
            )
        }

        val custom = override ?: return null
        val label = custom.title.ifBlank { custom.text }.ifBlank { custom.id.uppercase() }
        val speech = custom.text.ifBlank { custom.title }.ifBlank { label }

        return CommunicationItem(
            id = custom.id,
            label = label.uppercase(),
            shortLabel = label.uppercase(),
            ttsText = speech,
            iconRes = R.drawable.ic_contact_default,
            children = children,
            category = "custom",
            icon = "ic_contact_default",
            enabled = custom.enabled,
            pinnedMain = custom.pinnedMain,
            pinnedVideo = custom.pinnedVideo
        )
    }

    private fun normalizeMainItems(items: List<CommunicationItem>): List<CommunicationItem> {
        val disabledCount = items.count { !it.enabled }
        val missingIdCount = items.count { it.id.isBlank() }
        val missingIconCount = items.count { it.iconRes == 0 }
        val filtered = items
            .filter { it.enabled && it.id.isNotBlank() && it.iconRes != 0 }
            .map { item -> item.copy(children = normalizeChildren(item.children)) }
            .sortedBy { it.priority }

        Log.d(
            "NovaRehabPaging",
            "total_main_icons=${items.size}, enabled_main_icons=${filtered.size}, hidden_icons=disabled:$disabledCount,missing_id:$missingIdCount,missing_icon:$missingIconCount"
        )

        return filtered
    }

    private fun normalizeChildren(items: List<CommunicationItem>): List<CommunicationItem> {
        return items
            .filter { it.enabled && it.id.isNotBlank() && it.iconRes != 0 }
            .sortedBy { it.priority }
            .take(MAX_SUBMENU_ITEMS)
    }

    private fun flatten(items: List<CommunicationItem>): List<CommunicationItem> {
        return items + items.flatMap { flatten(it.children) }
    }

    private fun readAsset(context: Context, path: String): String {
        return context.assets.open(path).use { input ->
            input.readBytes().toString(Charsets.UTF_8).removePrefix("\uFEFF")
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

    private data class ParsedDocument(
        val topLevelIds: List<String>,
        val itemsById: Map<String, ParsedItem>
    )

    private data class ParsedItem(
        val id: String,
        val label: String,
        val shortLabel: String,
        val ttsText: String,
        val questionText: String,
        val category: String,
        val icon: String,
        val iconRes: Int,
        val childIds: List<String>,
        val priority: Int,
        val emotionalTags: List<String>,
        val aiPromptHint: String,
        val requiresConfirmation: Boolean,
        val enabled: Boolean,
        val arasaacKey: String,
        val symbolKey: String,
        val logEventType: String,
        val pinnedMain: Boolean,
        val pinnedVideo: Boolean,
        val usageRank: Int
    )
}






