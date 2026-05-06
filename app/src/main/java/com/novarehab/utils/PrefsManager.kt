package com.novarehab.utils

import android.content.Context
import com.novarehab.communication.data.PersonalIconBankManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class RadioStation(
    val name: String,
    val url: String
)

data class Contact(
    val name: String,
    val phone: String,
    val emoji: String = "",
    val language: String = "sl"
)

data class CustomCommIcon(
    val id: String,
    val title: String,
    val text: String,
    val language: String = "sl",
    val imagePath: String = "",
    val enabled: Boolean = true,
    val pinnedMain: Boolean = false,
    val pinnedVideo: Boolean = false,
    val showOnMain: Boolean = true,
    val children: List<String> = emptyList()
)

class PrefsManager(context: Context) {

    private val prefs = context.getSharedPreferences("nova_rehab_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val STATIONS_VERSION = 10
    private val personalIconBankManager = PersonalIconBankManager(context.applicationContext)

    fun getPin(): String = prefs.getString("pin", "1234") ?: "1234"
    fun savePin(pin: String) = prefs.edit().putString("pin", pin).apply()

    fun getServerIp(): String = prefs.getString("server_ip", "") ?: ""
    fun saveServerIp(ip: String) = prefs.edit().putString("server_ip", ip).apply()

    fun getServerPort(): String = prefs.getString("server_port", "8080") ?: "8080"
    fun saveServerPort(port: String) = prefs.edit().putString("server_port", port).apply()

    fun getPatientName(): String = prefs.getString("patient_name", "") ?: ""
    fun savePatientName(name: String) = prefs.edit().putString("patient_name", name).apply()

    fun isNavigationEnabled(): Boolean = prefs.getBoolean("navigation_enabled", false)
    fun saveNavigationEnabled(v: Boolean) = prefs.edit().putBoolean("navigation_enabled", v).apply()

    fun getHomeAddress(): String = prefs.getString("home_address", "") ?: ""
    fun saveHomeAddress(address: String) = prefs.edit().putString("home_address", address).apply()

    fun getKioskReturnMinutes(): Long = prefs.getLong("kiosk_return_minutes", 5L)
    fun saveKioskReturnMinutes(m: Long) = prefs.edit().putLong("kiosk_return_minutes", m).apply()

    fun getGuestLanguageReturnMinutes(): Long =
        prefs.getLong("guest_language_return_minutes", 15L).coerceIn(1L, 240L)

    fun saveGuestLanguageReturnMinutes(minutes: Long) =
        prefs.edit().putLong("guest_language_return_minutes", minutes.coerceIn(1L, 240L)).apply()

    fun getCommSubmenuTimeoutSeconds(): Long =
        prefs.getLong("comm_submenu_timeout_seconds", 15L).coerceIn(5L, 120L)

    fun saveCommSubmenuTimeoutSeconds(seconds: Long) =
        prefs.edit().putLong("comm_submenu_timeout_seconds", seconds.coerceIn(5L, 120L)).apply()

    fun isCommSubmenuEnabled(iconId: String, defaultValue: Boolean): Boolean =
        prefs.getBoolean("comm_submenu_enabled_$iconId", defaultValue)

    fun saveCommSubmenuEnabled(iconId: String, enabled: Boolean) =
        prefs.edit().putBoolean("comm_submenu_enabled_$iconId", enabled).apply()

    fun getDefaultSpeechLanguage(): String =
        prefs.getString("default_speech_language", "sl") ?: "sl"

    fun saveDefaultSpeechLanguage(lang: String) =
        prefs.edit().putString("default_speech_language", lang).apply()

    fun isAutoLanguageEnabled(): Boolean =
        prefs.getBoolean("auto_language_enabled", false)

    fun saveAutoLanguageEnabled(v: Boolean) =
        prefs.edit().putBoolean("auto_language_enabled", v).apply()

    fun getPatientLanguage1(): String =
        prefs.getString("patient_language_1", "sl") ?: "sl"

    fun savePatientLanguage1(lang: String) =
        prefs.edit().putString("patient_language_1", lang).apply()

    fun getPatientLanguage2(): String =
        prefs.getString("patient_language_2", "uk") ?: "uk"

    fun savePatientLanguage2(lang: String) =
        prefs.edit().putString("patient_language_2", lang).apply()

    fun getCommIconsPerPage(): Int {
        val v = prefs.getInt("comm_icons_per_page", 9)
        return if (v in setOf(6, 8, 9, 12, 15, 18)) v else 9
    }

    fun saveCommIconsPerPage(v: Int) {
        val safe = if (v in setOf(6, 8, 9, 12, 15, 18)) v else 9
        prefs.edit().putInt("comm_icons_per_page", safe).apply()
    }

    fun isAutoSortCommunicationIconsEnabled(): Boolean =
        prefs.getBoolean("comm_auto_sort_icons", false)

    fun saveAutoSortCommunicationIconsEnabled(enabled: Boolean) =
        prefs.edit().putBoolean("comm_auto_sort_icons", enabled).apply()

    fun getTtsVoice(): String = prefs.getString("tts_voice", "marin") ?: "marin"
    fun saveTtsVoice(voice: String) = prefs.edit().putString("tts_voice", voice).apply()

    fun getTtsTestLanguage(): String =
        prefs.getString("tts_test_language", "sl") ?: "sl"

    fun saveTtsTestLanguage(lang: String) =
        prefs.edit().putString("tts_test_language", lang).apply()

    fun getTtsVoiceGender(): String =
        prefs.getString("tts_voice_gender", "naravni") ?: "naravni"

    fun saveTtsVoiceGender(gender: String) =
        prefs.edit().putString("tts_voice_gender", gender).apply()

    fun getTtsSpeed(): Float =
        prefs.getFloat("tts_speed", 0.88f).coerceIn(0.80f, 1.06f)

    fun saveTtsSpeed(speed: Float) =
        prefs.edit().putFloat("tts_speed", speed.coerceIn(0.80f, 1.06f)).apply()

    fun getTtsVolume(): Float =
        prefs.getFloat("tts_volume", 1.0f).coerceIn(0.7f, 1.0f)

    fun saveTtsVolume(volume: Float) =
        prefs.edit().putFloat("tts_volume", volume.coerceIn(0.7f, 1.0f)).apply()

    fun getTtsPitch(): Float =
        prefs.getFloat("tts_pitch", 1.0f).coerceIn(0.5f, 2.0f)

    fun saveTtsPitch(pitch: Float) =
        prefs.edit().putFloat("tts_pitch", pitch.coerceIn(0.5f, 2.0f)).apply()

    fun getFallbackSpeechLanguage(): String =
        prefs.getString("fallback_speech_language", "sl") ?: "sl"

    fun saveFallbackSpeechLanguage(lang: String) =
        prefs.edit().putString("fallback_speech_language", lang.ifBlank { "sl" }).apply()

    fun getCustomCommIcons(): List<CustomCommIcon> {
        val fromFile = personalIconBankManager.load()
        if (fromFile.isNotEmpty()) return fromFile

        val json = prefs.getString("custom_comm_icons", null)
        val legacy = if (json != null) {
            try {
                val type = object : TypeToken<List<CustomCommIcon>>() {}.type
                gson.fromJson<List<CustomCommIcon>>(json, type).orEmpty()
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        if (legacy.isNotEmpty()) {
            personalIconBankManager.save(legacy)
        }
        return legacy
    }

    fun saveCustomCommIcons(items: List<CustomCommIcon>) {
        prefs.edit().putString("custom_comm_icons", gson.toJson(items)).apply()
        personalIconBankManager.save(items)
    }

    fun getGmailUser(): String = prefs.getString("gmail_user", "") ?: ""
    fun saveGmailUser(email: String) = prefs.edit().putString("gmail_user", email).apply()

    fun getGmailAppPassword(): String = prefs.getString("gmail_pass", "") ?: ""
    fun saveGmailAppPassword(pass: String) = prefs.edit().putString("gmail_pass", pass).apply()

    fun getReportMail1(): String = prefs.getString("report_mail1", "") ?: ""
    fun saveReportMail1(email: String) = prefs.edit().putString("report_mail1", email).apply()

    fun getReportMail2(): String = prefs.getString("report_mail2", "") ?: ""
    fun saveReportMail2(email: String) = prefs.edit().putString("report_mail2", email).apply()

    fun isReportMail1Enabled(): Boolean = prefs.getBoolean("report_mail1_enabled", true)
    fun saveReportMail1Enabled(v: Boolean) =
        prefs.edit().putBoolean("report_mail1_enabled", v).apply()

    fun isReportMail2Enabled(): Boolean = prefs.getBoolean("report_mail2_enabled", false)
    fun saveReportMail2Enabled(v: Boolean) =
        prefs.edit().putBoolean("report_mail2_enabled", v).apply()

    fun getReportHour(): Int = prefs.getInt("report_hour", 8)
    fun saveReportHour(hour: Int) = prefs.edit().putInt("report_hour", hour).apply()

    fun getRadioStations(): List<RadioStation> {
        val savedVersion = prefs.getInt("stations_version", 0)

        if (savedVersion < STATIONS_VERSION) {
            val stations = defaultStations()
            prefs.edit()
                .putInt("stations_version", STATIONS_VERSION)
                .putString("radio_stations", gson.toJson(stations))
                .apply()
            return stations
        }

        val json = prefs.getString("radio_stations", null)
        if (json != null) {
            return try {
                val type = object : TypeToken<List<RadioStation>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                defaultStations()
            }
        }

        return defaultStations()
    }

    fun saveRadioStations(stations: List<RadioStation>) {
        prefs.edit()
            .putString("radio_stations", gson.toJson(stations))
            .apply()
    }

    fun saveRadioStationsJson(json: String) {
        prefs.edit()
            .putString("radio_stations", json)
            .apply()
    }

    fun getContacts(): List<Contact> {
        val json = prefs.getString("contacts", null)
        if (json != null) {
            return try {
                val type = object : TypeToken<List<Contact>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                defaultContacts()
            }
        }
        return defaultContacts()
    }

    fun saveContacts(contacts: List<Contact>) {
        prefs.edit().putString("contacts", gson.toJson(contacts)).apply()
    }

    fun isContactIncomingCallEnabled(index: Int): Boolean =
        prefs.getBoolean("contact_${index}_incoming_calls_enabled", true)

    fun saveContactIncomingCallEnabled(index: Int, enabled: Boolean) =
        prefs.edit().putBoolean("contact_${index}_incoming_calls_enabled", enabled).apply()

    fun isContactOutgoingCallEnabled(index: Int): Boolean =
        prefs.getBoolean("contact_${index}_outgoing_calls_enabled", true)

    fun saveContactOutgoingCallEnabled(index: Int, enabled: Boolean) =
        prefs.edit().putBoolean("contact_${index}_outgoing_calls_enabled", enabled).apply()

    fun isUsbMusicImportEnabled(): Boolean =
        prefs.getBoolean("usb_music_import_enabled", true)

    fun saveUsbMusicImportEnabled(enabled: Boolean) =
        prefs.edit().putBoolean("usb_music_import_enabled", enabled).apply()

    fun isUsbMusicAutoImportEnabled(): Boolean =
        prefs.getBoolean("usb_music_auto_import_enabled", false)

    fun saveUsbMusicAutoImportEnabled(enabled: Boolean) =
        prefs.edit().putBoolean("usb_music_auto_import_enabled", enabled).apply()

    fun getUsbMusicImportTarget(): String =
        prefs.getString("usb_music_import_target", "auto") ?: "auto"

    fun saveUsbMusicImportTarget(target: String) =
        prefs.edit().putString("usb_music_import_target", target.ifBlank { "auto" }).apply()

    fun getUsbMusicOverwriteDifferent(): Boolean =
        prefs.getBoolean("usb_music_overwrite_different", false)

    fun saveUsbMusicOverwriteDifferent(enabled: Boolean) =
        prefs.edit().putBoolean("usb_music_overwrite_different", enabled).apply()

    fun getLastConfigExportAt(): Long =
        prefs.getLong("config_export_last_at", 0L)

    fun saveLastConfigExportAt(timestamp: Long) =
        prefs.edit().putLong("config_export_last_at", timestamp).apply()

    fun getLastConfigExportSize(): Long =
        prefs.getLong("config_export_last_size", 0L)

    fun saveLastConfigExportSize(size: Long) =
        prefs.edit().putLong("config_export_last_size", size).apply()

    fun getLastConfigImportAt(): Long =
        prefs.getLong("config_import_last_at", 0L)

    fun saveLastConfigImportAt(timestamp: Long) =
        prefs.edit().putLong("config_import_last_at", timestamp).apply()

    fun getSpeechProviderMode(): String =
        prefs.getString("speech_provider_mode", "hybrid_auto") ?: "hybrid_auto"

    fun saveSpeechProviderMode(mode: String) =
        prefs.edit().putString("speech_provider_mode", mode.ifBlank { "hybrid_auto" }).apply()

    fun getSpeechResponseMode(): String =
        prefs.getString("speech_response_mode", "hybrid_auto") ?: "hybrid_auto"

    fun saveSpeechResponseMode(mode: String) =
        prefs.edit().putString("speech_response_mode", mode.ifBlank { "hybrid_auto" }).apply()

    fun isOpenAiTtsEnabled(): Boolean =
        prefs.getBoolean("speech_openai_enabled", true)

    fun saveOpenAiTtsEnabled(enabled: Boolean) =
        prefs.edit().putBoolean("speech_openai_enabled", enabled).apply()

    fun isLocalTtsFallbackEnabled(): Boolean =
        prefs.getBoolean("speech_local_fallback_enabled", true)

    fun saveLocalTtsFallbackEnabled(enabled: Boolean) =
        prefs.edit().putBoolean("speech_local_fallback_enabled", enabled).apply()

    fun getTtsModel(): String =
        prefs.getString("speech_tts_model", "gpt-4o-mini-tts") ?: "gpt-4o-mini-tts"

    fun saveTtsModel(model: String) =
        prefs.edit().putString("speech_tts_model", model.ifBlank { "gpt-4o-mini-tts" }).apply()

    fun getTtsResponseFormat(): String =
        prefs.getString("speech_tts_response_format", "mp3") ?: "mp3"

    fun saveTtsResponseFormat(format: String) =
        prefs.edit().putString("speech_tts_response_format", format.ifBlank { "mp3" }).apply()

    fun getSpeechStylePreset(): String =
        prefs.getString("speech_style_preset", "rehabilitation_assistant") ?: "rehabilitation_assistant"

    fun saveSpeechStylePreset(preset: String) =
        prefs.edit().putString("speech_style_preset", preset.ifBlank { "rehabilitation_assistant" }).apply()

    fun getSpeechPauseBetweenWordsMs(): Int =
        prefs.getInt("speech_pause_words_ms", 0).coerceIn(0, 400)

    fun saveSpeechPauseBetweenWordsMs(value: Int) =
        prefs.edit().putInt("speech_pause_words_ms", value.coerceIn(0, 400)).apply()

    fun getSpeechPauseBetweenSentencesMs(): Int =
        prefs.getInt("speech_pause_sentences_ms", 120).coerceIn(0, 1200)

    fun saveSpeechPauseBetweenSentencesMs(value: Int) =
        prefs.edit().putInt("speech_pause_sentences_ms", value.coerceIn(0, 1200)).apply()

    fun getSpeechPronunciationClarity(): Int =
        prefs.getInt("speech_pronunciation_clarity", 80).coerceIn(0, 100)

    fun saveSpeechPronunciationClarity(value: Int) =
        prefs.edit().putInt("speech_pronunciation_clarity", value.coerceIn(0, 100)).apply()

    fun getSpeechEmotionalWarmth(): Int =
        prefs.getInt("speech_emotional_warmth", 70).coerceIn(0, 100)

    fun saveSpeechEmotionalWarmth(value: Int) =
        prefs.edit().putInt("speech_emotional_warmth", value.coerceIn(0, 100)).apply()

    fun getSpeechCalmness(): Int =
        prefs.getInt("speech_calmness", 80).coerceIn(0, 100)

    fun saveSpeechCalmness(value: Int) =
        prefs.edit().putInt("speech_calmness", value.coerceIn(0, 100)).apply()

    fun isSpeechRehabilitationModeEnabled(): Boolean =
        prefs.getBoolean("speech_rehabilitation_mode", true)

    fun saveSpeechRehabilitationModeEnabled(enabled: Boolean) =
        prefs.edit().putBoolean("speech_rehabilitation_mode", enabled).apply()

    fun isSpeechShortSentenceModeEnabled(): Boolean =
        prefs.getBoolean("speech_short_sentence_mode", true)

    fun saveSpeechShortSentenceModeEnabled(enabled: Boolean) =
        prefs.edit().putBoolean("speech_short_sentence_mode", enabled).apply()

    fun getLastSpeechSource(): String =
        prefs.getString("speech_last_source", "ni podatka") ?: "ni podatka"

    fun getSpeechAverageDelayMs(): Long =
        prefs.getLong("speech_avg_delay_ms", 0L)

    fun getSpeechRequestCount(): Int =
        prefs.getInt("speech_request_count", 0)

    fun getSpeechCacheHitCount(): Int =
        prefs.getInt("speech_cache_hit_count", 0)

    fun getSpeechFailureCount(): Int =
        prefs.getInt("speech_failure_count", 0)

    fun getSpeechCacheHitRatePercent(): Int {
        val requests = getSpeechRequestCount()
        if (requests <= 0) return 0
        return ((getSpeechCacheHitCount() * 100.0) / requests).toInt().coerceIn(0, 100)
    }

    fun isHardwareVolumeControlEnabled(): Boolean =
        prefs.getBoolean("hardware_volume_control_enabled", false)

    fun saveHardwareVolumeControlEnabled(enabled: Boolean) =
        prefs.edit().putBoolean("hardware_volume_control_enabled", enabled).apply()

    fun getHardwareVolumeButtonMode(): String =
        prefs.getString("hardware_volume_button_mode", "normal_android") ?: "normal_android"

    fun saveHardwareVolumeButtonMode(mode: String) =
        prefs.edit().putString("hardware_volume_button_mode", mode.ifBlank { "normal_android" }).apply()

    fun recordSpeechDiagnostic(delayMs: Long, source: String, cacheHit: Boolean, success: Boolean) {
        val oldCount = getSpeechRequestCount()
        val newCount = oldCount + 1
        val currentAverage = getSpeechAverageDelayMs()
        val newAverage = if (oldCount <= 0) delayMs else ((currentAverage * oldCount) + delayMs) / newCount
        val currentHits = getSpeechCacheHitCount()
        val currentFailures = getSpeechFailureCount()

        prefs.edit()
            .putInt("speech_request_count", newCount)
            .putLong("speech_avg_delay_ms", newAverage)
            .putString("speech_last_source", source)
            .putInt("speech_cache_hit_count", if (cacheHit) currentHits + 1 else currentHits)
            .putInt("speech_failure_count", if (success) currentFailures else currentFailures + 1)
            .apply()
    }

    private fun defaultContacts(): List<Contact> = listOf(
        Contact("Mama", "", "", "sl"),
        Contact("Ata", "", "", "sl"),
        Contact("Sestra", "", "", "sl"),
        Contact("Brat", "", "", "sl"),
        Contact("Zdravnik", "", "", "sl"),
        Contact("Skrbnik", "", "", "sl")
    )

    private fun defaultStations(): List<RadioStation> = listOf(
        RadioStation("Radio 1", "https://live.radio.si/Radio1"),
        RadioStation("Radio Center", "https://stream2.radiocenter.si/center"),
        RadioStation("ROKS UA", "https://online.radioroks.ua/RadioROKS"),
        RadioStation("Kiss FM UA", "https://online.kissfm.ua/KissFM"),
        RadioStation("Nashe UA", "https://online.nasheradio.ua/NasheRadio"),
        RadioStation("USB glasba", "music://local")
    )
}

