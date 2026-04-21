package com.novarehab.service

import android.content.Context
import com.novarehab.utils.PrefsManager
import com.novarehab.utils.RadioStation
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

// Potegne delujoče radio postaje iz radio-browser.info API
object RadioBrowserService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // API strežniki - vzame prvega ki se odzove
    private val servers = listOf(
        "https://de1.api.radio-browser.info",
        "https://at1.api.radio-browser.info",
        "https://nl1.api.radio-browser.info"
    )

    fun fetchStations(context: Context, onSuccess: (List<RadioStation>) -> Unit, onError: () -> Unit) {
        Thread {
            try {
                val stations = mutableListOf<RadioStation>()

                // Poišči UA postaje (jezik: ukrainian, samo delujoče, sortirane po popularnosti)
                val uaStations = fetchByLanguage("ukrainian", 4)
                stations.addAll(uaStations)

                // Poišči SLO postaje
                val sloStations = fetchByCountryCode("SI", 2)
                stations.addAll(sloStations)

                if (stations.isNotEmpty()) {
                    // Shrani v prefs
                    PrefsManager(context).saveRadioStations(stations)
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        onSuccess(stations)
                    }
                } else {
                    android.os.Handler(android.os.Looper.getMainLooper()).post { onError() }
                }
            } catch (e: Exception) {
                android.os.Handler(android.os.Looper.getMainLooper()).post { onError() }
            }
        }.start()
    }

    private fun fetchByLanguage(language: String, limit: Int): List<RadioStation> {
        for (server in servers) {
            try {
                val url = "$server/json/stations/search?" +
                    "language=$language&lastcheckok=1&order=clickcount&reverse=true&limit=$limit&hidebroken=true"
                val result = get(url)
                if (result != null) return parseStations(result)
            } catch (e: Exception) { continue }
        }
        return emptyList()
    }

    private fun fetchByCountryCode(countryCode: String, limit: Int): List<RadioStation> {
        for (server in servers) {
            try {
                val url = "$server/json/stations/search?" +
                    "countrycodeexact=$countryCode&lastcheckok=1&order=clickcount&reverse=true&limit=$limit&hidebroken=true"
                val result = get(url)
                if (result != null) return parseStations(result)
            } catch (e: Exception) { continue }
        }
        return emptyList()
    }

    private fun get(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "NovaRehab/1.0")
            .build()
        val response = client.newCall(request).execute()
        return if (response.isSuccessful) response.body?.string() else null
    }

    private fun parseStations(json: String): List<RadioStation> {
        val result = mutableListOf<RadioStation>()
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val name = obj.optString("name", "").trim()
            // Uporabi url_resolved (že razrešen redirect)
            val url = obj.optString("url_resolved", "").ifEmpty {
                obj.optString("url", "")
            }
            if (name.isNotEmpty() && url.isNotEmpty() && url.startsWith("http")) {
                result.add(RadioStation(name.take(20), url))
            }
        }
        return result
    }
}
