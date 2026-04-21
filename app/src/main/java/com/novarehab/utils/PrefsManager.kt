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

    fun getPin(): String = prefs.getString("pin", "1234") ?: "1234"
    fun savePin(pin: String) = prefs.edit().putString("pin", pin).apply()

    fun getServerIp(): String = prefs.getString("server_ip", "") ?: ""
    fun saveServerIp(ip: String) = prefs.edit().putString("server_ip", ip).apply()
    fun getServerPort(): String = prefs.getString("server_port", "8080") ?: "8080"
    fun saveServerPort(port: String) = prefs.edit().putString("server_port", port).apply()

    fun getReportEmail(): String = prefs.getString("report_email", "") ?: ""
    fun saveReportEmail(email: String) = prefs.edit().putString("report_email", email).apply()

    fun getHomeAddress(): String = prefs.getString("home_address", "") ?: ""
    fun saveHomeAddress(address: String) = prefs.edit().putString("home_address", address).apply()

    fun getKioskReturnMinutes(): Long = prefs.getLong("kiosk_return_minutes", 5L)
    fun saveKioskReturnMinutes(minutes: Long) = prefs.edit().putLong("kiosk_return_minutes", minutes).apply()

    fun isNavigationEnabled(): Boolean = prefs.getBoolean("navigation_enabled", true)
    fun saveNavigationEnabled(enabled: Boolean) = prefs.edit().putBoolean("navigation_enabled", enabled).apply()

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

    private fun defaultContacts(): List<Contact> = listOf(
        Contact("Mama", "", "👩", "sl"),
        Contact("Oče", "", "👨", "sl"),
        Contact("Sestra", "", "👧", "sl"),
        Contact("Brat", "", "🧑", "sl"),
        Contact("Zdravnik", "", "👨‍⚕️", "sl"),
        Contact("Skrbnik", "", "🧑‍💼", "sl")
    )

    private fun defaultStations(): List<RadioStation> = listOf(
        RadioStation("UA Kultura",  "https://stream.rcs.revma.com/an1ugyygzk8uv"),
        RadioStation("Rocks UA",    "https://pub0302.101.ru:8443/stream/pro/aac/128/101"),
        RadioStation("Radio Promin","https://stream.rcs.revma.com/ypqvyy2ynk8uv"),
        RadioStation("Радіо Люкс", "https://online.radiolux.ua/radiolux"),
        RadioStation("Radio 1 SLO", "https://icecast2.rtvslo.si/ars1_aac"),
        RadioStation("URC Radio",   "https://stream.urcradio.com/stream")
    )
}
