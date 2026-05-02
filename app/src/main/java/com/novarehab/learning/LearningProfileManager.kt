package com.novarehab.learning

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

class LearningProfileManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("nova_learning_profile", Context.MODE_PRIVATE)

    fun recordIconUsed(iconId: String) {
        if (iconId.isBlank()) return

        val now = System.currentTimeMillis()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val countKey = "icon_count_$iconId"
        val timeKey = "icon_last_$iconId"
        val hourKey = "icon_hour_${hour}_$iconId"

        prefs.edit()
            .putInt(countKey, prefs.getInt(countKey, 0) + 1)
            .putLong(timeKey, now)
            .putInt(hourKey, prefs.getInt(hourKey, 0) + 1)
            .apply()
    }

    fun recordAiSuggestion(iconId: String, confirmed: Boolean) {
        if (iconId.isBlank()) return
        val key = if (confirmed) "ai_confirmed_$iconId" else "ai_rejected_$iconId"
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    fun mostUsedIconIds(limit: Int = 12): List<String> {
        return prefs.all.keys
            .filter { it.startsWith("icon_count_") }
            .map { key -> key.removePrefix("icon_count_") to (prefs.all[key] as? Int ?: 0) }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    fun usageCount(iconId: String): Int = prefs.getInt("icon_count_$iconId", 0)

    fun exportJson(): JSONObject {
        val iconUsage = JSONObject()
        val lastUsed = JSONObject()
        val confirmed = JSONObject()
        val rejected = JSONObject()

        prefs.all.forEach { (key, value) ->
            when {
                key.startsWith("icon_count_") -> iconUsage.put(key.removePrefix("icon_count_"), value)
                key.startsWith("icon_last_") -> lastUsed.put(key.removePrefix("icon_last_"), value)
                key.startsWith("ai_confirmed_") -> confirmed.put(key.removePrefix("ai_confirmed_"), value)
                key.startsWith("ai_rejected_") -> rejected.put(key.removePrefix("ai_rejected_"), value)
            }
        }

        return JSONObject()
            .put("iconUsageCount", iconUsage)
            .put("lastUsedAt", lastUsed)
            .put("confirmedAiSuggestions", confirmed)
            .put("rejectedAiSuggestions", rejected)
            .put("frequentSequences", JSONArray())
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
