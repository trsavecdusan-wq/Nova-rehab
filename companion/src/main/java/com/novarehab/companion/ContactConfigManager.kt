package com.novarehab.companion

import android.content.Context

data class CompanionContactConfig(
    val contactId: String,
    val contactName: String,
    val roomId: String,
    val preferredLanguage: String
)

class ContactConfigManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("companion_contact_config", Context.MODE_PRIVATE)

    val availableContacts: List<CompanionContactConfig> = listOf(
        CompanionContactConfig("c01", "Žana", "novarehab_c01", "sl"),
        CompanionContactConfig("c02", "Dedek", "novarehab_c02", "sl"),
        CompanionContactConfig("c03", "Inna", "novarehab_c03", "uk"),
        CompanionContactConfig("c04", "Julija", "novarehab_c04", "sl"),
        CompanionContactConfig("c05", "Kuma", "novarehab_c05", "sl"),
        CompanionContactConfig("c06", "Dušan", "novarehab_c06", "sl")
    )

    fun getCurrent(): CompanionContactConfig {
        val savedId = prefs.getString(KEY_CONTACT_ID, null)
        return availableContacts.firstOrNull { it.contactId == savedId }
            ?: availableContacts.last()
    }

    fun isConfigured(): Boolean = prefs.contains(KEY_CONTACT_ID)

    fun save(config: CompanionContactConfig) {
        prefs.edit()
            .putString(KEY_CONTACT_ID, config.contactId)
            .putString(KEY_CONTACT_NAME, config.contactName)
            .putString(KEY_ROOM_ID, config.roomId)
            .putString(KEY_LANGUAGE, config.preferredLanguage)
            .apply()
    }

    companion object {
        private const val KEY_CONTACT_ID = "contact_id"
        private const val KEY_CONTACT_NAME = "contact_name"
        private const val KEY_ROOM_ID = "room_id"
        private const val KEY_LANGUAGE = "preferred_language"
    }
}
