package com.novarehab.companion

import android.content.Context
import org.json.JSONObject

private const val DEFAULT_PATIENT_NAME = "Lana"

data class CompanionContactConfig(
    val contactId: String,
    val contactName: String,
    val roomId: String,
    val preferredLanguage: String,
    val patientName: String = DEFAULT_PATIENT_NAME
)

class ContactConfigManager(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences("companion_contact_config", Context.MODE_PRIVATE)

    val availableContacts: List<CompanionContactConfig> = listOf(
        CompanionContactConfig("c01", "Žana", "novarehab_c01", "uk"),
        CompanionContactConfig("c02", "Dedek", "novarehab_c02", "uk"),
        CompanionContactConfig("c03", "Inna", "novarehab_c03", "uk"),
        CompanionContactConfig("c04", "Julija", "novarehab_c04", "uk"),
        CompanionContactConfig("c05", "Kuma", "novarehab_c05", "uk"),
        CompanionContactConfig("c06", "Dušan", "novarehab_c06", "sl")
    )

    fun getCurrent(): CompanionContactConfig =
        getCurrentOrNull() ?: availableContacts.last()

    fun getCurrentOrNull(): CompanionContactConfig? {
        val savedId = prefs.getString(KEY_CONTACT_ID, null)?.trim().orEmpty()
        if (savedId.isBlank()) return null

        val savedName = prefs.getString(KEY_CONTACT_NAME, null)?.trim().orEmpty()
        val savedRoomId = prefs.getString(KEY_ROOM_ID, null)?.trim().orEmpty()
        val savedLanguage = prefs.getString(KEY_LANGUAGE, null)?.trim().orEmpty()
        val savedPatientName = prefs.getString(KEY_PATIENT_NAME, null)?.trim().orEmpty()

        val predefined = availableContacts.firstOrNull { it.contactId == savedId }
        val resolved = if (predefined != null) {
            predefined.copy(
                contactName = savedName.ifBlank { predefined.contactName },
                roomId = savedRoomId.ifBlank { predefined.roomId },
                preferredLanguage = savedLanguage.ifBlank { predefined.preferredLanguage },
                patientName = savedPatientName.ifBlank { predefined.patientName }
            )
        } else {
            CompanionContactConfig(
                contactId = savedId,
                contactName = savedName.ifBlank { savedId.uppercase() },
                roomId = savedRoomId.ifBlank { "novarehab_$savedId" },
                preferredLanguage = savedLanguage.ifBlank { "sl" },
                patientName = savedPatientName.ifBlank { DEFAULT_PATIENT_NAME }
            )
        }

        return resolved.takeIf(::isValid)
    }

    fun isConfigured(): Boolean = getCurrentOrNull() != null

    fun isValid(config: CompanionContactConfig?): Boolean {
        if (config == null) return false
        if (config.contactId.isBlank()) return false
        if (config.contactName.isBlank()) return false
        if (config.roomId.isBlank()) return false
        if (config.preferredLanguage.isBlank()) return false
        return config.preferredLanguage in setOf("sl", "uk", "en", "de", "hr")
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun save(config: CompanionContactConfig) {
        require(isValid(config)) { "Neveljavna nastavitev kontakta." }

        prefs.edit()
            .putString(KEY_CONTACT_ID, config.contactId)
            .putString(KEY_CONTACT_NAME, config.contactName)
            .putString(KEY_ROOM_ID, config.roomId)
            .putString(KEY_LANGUAGE, config.preferredLanguage)
            .putString(KEY_PATIENT_NAME, config.patientName.ifBlank { DEFAULT_PATIENT_NAME })
            .apply()
    }

    fun importFromSharedPayload(raw: String): Result<CompanionContactConfig> {
        return runCatching {
            val normalized = raw
                .replace("\uFEFF", "")
                .substringAfter(CONFIG_PREFIX, raw)
                .trim()

            val json = JSONObject(normalized)
            val config = CompanionContactConfig(
                contactId = json.optString("contact_id").trim(),
                contactName = json.optString("contact_name").trim(),
                roomId = json.optString("room_id").trim(),
                preferredLanguage = json.optString("preferred_language").trim().ifBlank { "sl" },
                patientName = json.optString("patient_name").trim().ifBlank { DEFAULT_PATIENT_NAME }
            )

            if (!isValid(config)) {
                throw IllegalArgumentException("Uvožena nastavitev ni veljavna.")
            }

            save(config)
            config
        }
    }

    companion object {
        private const val KEY_CONTACT_ID = "contact_id"
        private const val KEY_CONTACT_NAME = "contact_name"
        private const val KEY_ROOM_ID = "room_id"
        private const val KEY_LANGUAGE = "preferred_language"
        private const val KEY_PATIENT_NAME = "patient_name"
        const val CONFIG_PREFIX = "NOVAREHAB_COMPANION_CONFIG:"
    }
}
