package com.novarehab.service

import android.content.Context
import com.novarehab.utils.PrefsManager
import com.novarehab.utils.RadioStation
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

object RadioBrowserService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // Preizkušeni direktni stream URL-ji - brez redirectov
    // Pridobljeni prek radio-browser.info url_resolved polja
    private val hardcodedStations = listOf(
        RadioStation("Radio Center", "http://stream2.radiocenter.si:8000/center"),
        RadioStation("Val 202", "http://mp3.rtvslo.si/val202"),
        RadioStation("Radio 1", "http://mp3.rtvslo.si/ra1"),
        RadioStation("Lux FM UA", "https://online.luxfm.com.ua/luxfm"),
        RadioStation("Nashe UA", "https://nashe1.hostingradio.ru/nashe-256.mp3"),
        RadioStation("🎵 Glasba", "music://local")
    ),
        RadioStation("Radio 1",       "http://mp3.rtvslo.si/ra1"),
        RadioStation("Val 202",       "http://mp3.rtvslo.si/val202"),
        // Ukrajna - glasba
        RadioStation("Lux FM UA",     "https://online.luxfm.com.ua/luxfm"),
        RadioStation("Nashe UA",      "https://nashe1.hostingradio.ru/nashe-256.mp3"),
        RadioStation("🎵 Glasba",     "music://local")
    )
        RadioStation("Val 202",     "http://mp3.rtvslo.si/val202"),
        RadioStation("Radio 1",     "http://mp3.rtvslo.si/ra1"),
        RadioStation("Ars",         "http://mp3.rtvslo.si/ars"),
        // BBC - zanesljiv
        RadioStation("BBC World",   "https://stream.live.vc.bbcmedia.co.uk/bbc_world_service"),
        // UA - revma CDN
        RadioStation("UA Kultura",  "https://stream.rcs.revma.com/an1ugyygzk8uv"),
        RadioStation("🎵 Glasba",   "music://local")
    )

    fun fetchStations(
        context: Context,
        onSuccess: (List<RadioStation>) -> Unit,
        onError: () -> Unit
    ) {
        // Poskusi potegniti sveže URL-je iz radio-browser.info
        // Če ne uspe, uporabi hardcoded
        Thread {
            try {
                val stations = mutableListOf<RadioStation>()

                // SLO postaje
                val slo = fetchFromApi("https://de1.api.radio-browser.info/json/stations/search?countrycodeexact=SI&lastcheckok=1&order=clickcount&reverse=true&limit=2&hidebroken=true")
                stations.addAll(slo)

                // UA postaje
                val ua = fetchFromApi("https://de1.api.radio-browser.info/json/stations/search?languageexact=ukrainian&lastcheckok=1&order=clickcount&reverse=true&limit=3&hidebroken=true")
                stations.addAll(ua)

                // Dodaj glasbo
                stations.add(RadioStation("🎵 Glasba", "music://local"))

                if (stations.size >= 3) {
                    PrefsManager(context).saveRadioStations(stations)
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        onSuccess(stations)
                    }
                } else {
                    throw Exception("Not enough stations")
                }
            } catch (e: Exception) {
                // Fallback na hardcoded
                PrefsManager(context).saveRadioStations(hardcodedStations)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onSuccess(hardcodedStations)
                }
            }
        }.start()
    }

    private fun fetchFromApi(url: String): List<RadioStation> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "NovaRehab/2.0")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return emptyList()

        val body = response.body?.string() ?: return emptyList()
        val arr = JSONArray(body)
        val result = mutableListOf<RadioStation>()

        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val name = obj.optString("name", "").trim().take(15)
            // url_resolved je že razrešen URL po redirectu
            val streamUrl = obj.optString("url_resolved", "").ifEmpty {
                obj.optString("url", "")
            }
            if (name.isNotEmpty() && streamUrl.startsWith("http")) {
                result.add(RadioStation(name, streamUrl))
            }
        }
        return result
    }
}
