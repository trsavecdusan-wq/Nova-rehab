package com.novarehab.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONArray

data class RadioStation(val name: String, val url: String)

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

    fun getRadioStations(): List<RadioStation> {
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
        prefs.edit().putString("radio_stations", gson.toJson(stations)).apply()
    }

    fun saveRadioStationsJson(json: String) {
        try {
            JSONArray(json)
            prefs.edit().putString("radio_stations", json).apply()
        } catch (e: Exception) { }
    }

    private fun defaultStations(): List<RadioStation> {
        return listOf(
            RadioStation("Radio Lutsk", "http://online.lutsk.fm:8000/lutsk"),
            RadioStation("UA Kultura", "http://stream.rcs.revma.com/an1ugyygzk8uv"),
            RadioStation("Radio Promin", "http://stream.rcs.revma.com/ypqvyy2ynk8uv"),
            RadioStation("FM Galychyna", "http://stream.galychyna.com:8000/fm"),
            RadioStation("URC Radio", "https://stream.urcradio.com/stream"),
            RadioStation("USB Glasba", "usb://local")
        )
    }
}
