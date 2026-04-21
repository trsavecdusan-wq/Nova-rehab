package com.novarehab.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONArray

data class RadioStation(val name: String, val url: String)
data class Contact(val name: String, val phone: String, val emoji: String = "👤", val language: String = "sl")

class PrefsManager(context: Context) {

    private val prefs = context.getSharedPreferences("nova_rehab_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    // ── PIN ──────────────────────────────────────────────────────────────────
    fun getPin(): String = prefs.getString("pin", "1234") ?: "1234"
    fun savePin(pin: String) = prefs.edit().putString("pin", pin).apply()

    // ── SERVER ───────────────────────────────────────────────────────────────
    fun getServerIp(): String = prefs.getString("server_ip", "") ?: ""
    fun saveServerIp(ip: String) = prefs.edit().putString("server_ip", ip).apply()
    fun getServerPort(): String = prefs.getString("server_port", "8080") ?: "8080"
    fun saveServerPort(port: String) = prefs.edit().putString("server_port", port).apply()

    // ── PACIENT ──────────────────────────────────────────────────────────────
    fun getPatientName(): String = prefs.getString("patient_name", "") ?: ""
    fun savePatientName(name: String) = prefs.edit().putString("patient_name", name).apply()

    // ── NAVIGACIJA ───────────────────────────────────────────────────────────
    fun isNavigationEnabled(): Boolean = prefs.getBoolean("navigation_enabled", false)
    fun saveNavigationEnabled(enabled: Boolean) = prefs.edit().putBoolean("navigation_enabled", enabled).apply()
    fun getHomeAddress(): String = prefs.getString("home_address", "") ?: ""
    fun saveHomeAddress(address: String) = prefs.edit().putString("home_address", address).apply()

    // ── KIOSK ────────────────────────────────────────────────────────────────
    fun getKioskReturnMinutes(): Long = prefs.getLong("kiosk_return_minutes", 5L)
    fun saveKioskReturnMinutes(minutes: Long) = prefs.edit().putLong("kiosk_return_minutes", minutes).apply()

    // ── OPENAI TTS ───────────────────────────────────────────────────────────
    fun getOpenAiKey(): String = prefs.getString("openai_key", "") ?: ""
    fun saveOpenAiKey(key: String) = prefs.edit().putString("openai_key", key).apply()
    fun getTtsVoice(): String = prefs.getString("tts_voice", "nova") ?: "nova"
    fun saveTtsVoice(voice: String) = prefs.edit().putString("tts_voice", voice).apply()

    // ── GMAIL / POROCILA ─────────────────────────────────────────────────────
    fun getGmailUser(): String = prefs.getString("gmail_user", "") ?: ""
    fun saveGmailUser(email: String) = prefs.edit().putString("gmail_user", email).apply()
    fun getGmailAppPassword(): String = prefs.getString("gmail_pass", "") ?: ""
    fun saveGmailAppPassword(pass: String) = prefs.edit().putString("gmail_pass", pass).apply()
    fun getReportMail1(): String = prefs.getString("report_mail1", "") ?: ""
    fun saveReportMail1(email: String) = prefs.edit().putString("report_mail1", email).apply()
    fun getReportMail2(): String = prefs.getString("report_mail2", "") ?: ""
    fun saveReportMail2(email: String) = prefs.edit().putString("report_mail2", email).apply()
    fun isReportMail1Enabled(): Boolean = prefs.getBoolean("report_mail1_enabled", true)
    fun saveReportMail1Enabled(v: Boolean) = prefs.edit().putBoolean("report_mail1_enabled", v).apply()
    fun isReportMail2Enabled(): Boolean = prefs.getBoolean("report_mail2_enabled", false)
    fun saveReportMail2Enabled(v: Boolean) = prefs.edit().putBoolean("report_mail2_enabled", v).apply()
    fun getReportHour(): Int = prefs.getInt("report_hour", 8)
    fun saveReportHour(hour: Int) = prefs.edit().putInt("report_hour", hour).apply()

    // ── MAIL ZA POROCILA ─────────────────────────────────────────────────────
    fun getReportEmail(): String = prefs.getString("report_email", "") ?: ""
    fun saveReportEmail(email: String) = prefs.edit().putString("report_email", email).apply()

    // ── RADIO ────────────────────────────────────────────────────────────────
    fun getRadioStations(): List<RadioStation> {
        val json = prefs.getString("radio_stations", null)
        if (json != null) {
            return try {
                val type = object : TypeToken<List<RadioStation>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) { defaultStations() }
        }
        return defaultStations()
    }

    fun saveRadioStations(stations: List<RadioStation>) {
        prefs.edit().putString("radio_stations", gson.toJson(stations)).apply()
    }

    fun saveRadioStationsJson(json: String) {
        try {
            JSONArray(json)
            prefs.edit().putString("radio_stations", json).apply()
        } catch (e: Exception) { }
    }

    // ── KONTAKTI ─────────────────────────────────────────────────────────────
    fun getContacts(): List<Contact> {
        val json = prefs.getString("contacts", null)
        if (json != null) {
            return try {
                val type = object : TypeToken<List<Contact>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) { defaultContacts() }
        }
        return defaultContacts()
    }

    fun saveContacts(contacts: List<Contact>) {
        prefs.edit().putString("contacts", gson.toJson(contacts)).apply()
    }

    // ── PRIVZETE VREDNOSTI ───────────────────────────────────────────────────
    private fun defaultContacts(): List<Contact> = listOf(
        Contact("Mama", "", "👩", "sl"),
        Contact("Oče", "", "👨", "sl"),
        Contact("Sestra", "", "👧", "sl"),
        Contact("Brat", "", "🧑", "sl"),
        Contact("Zdravnik", "", "👨‍⚕️", "sl"),
        Contact("Skrbnik", "", "🧑‍💼", "sl")
    )

    private fun defaultStations(): List<RadioStation> = listOf(
        RadioStation("Val 202",      "https://icecast2.rtvslo.si/val202_aac"),
        RadioStation("ARS",          "https://icecast2.rtvslo.si/ars1_aac"),
        RadioStation("UA Kultura",   "https://stream.rcs.revma.com/an1ugyygzk8uv"),
        RadioStation("UA Promin",    "https://stream.rcs.revma.com/ypqvyy2ynk8uv"),
        RadioStation("UA Pershyi",   "https://stream.rcs.revma.com/qnkh30knab8uv"),
        RadioStation("🎵 Glasba",    "music://local")
    )
}
