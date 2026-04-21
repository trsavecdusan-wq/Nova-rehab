package com.novarehab.utils

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import com.novarehab.utils.StatEvent
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

class OpenAiTtsManager(private val context: Context) {

    private val stats by lazy { StatsManager(context) }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val cacheDir = File(context.getExternalFilesDir(null), "tts_cache").also { it.mkdirs() }
    private var mediaPlayer: MediaPlayer? = null
    private var localTts: TextToSpeech? = null
    private var localTtsReady = false

    // Inicializiraj lokalni TTS kot fallback
    fun initLocalTts() {
        localTts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                localTtsReady = true
                localTts?.setSpeechRate(0.9f)
            }
        }
    }

    fun speak(
        text: String,
        language: String,        // "sl" ali "uk"
        apiKey: String,
        voice: String,           // nova, shimmer, alloy, echo, fable, onyx
        onDone: () -> Unit = {}
    ) {
        if (text.isEmpty()) { onDone(); return }

        val apiKeyTrimmed = apiKey.trim()

        if (apiKeyTrimmed.isEmpty()) {
            // Ni API ključa → lokalni TTS
            speakLocal(text, language, onDone)
            return
        }

        // Preveri cache
        val cacheFile = getCacheFile(text, language, voice)
        if (cacheFile.exists() && cacheFile.length() > 0) {
            playMp3(cacheFile, onDone)
            return
        }

        // Pokliči OpenAI API v ozadju
        Thread {
            try {
                val json = JSONObject().apply {
                    put("model", "tts-1")
                    put("input", text)
                    put("voice", voice)
                    // Jezik je določen z vsebino besedila - OpenAI ga zazna samodejno
                }

                val request = Request.Builder()
                    .url("https://api.openai.com/v1/audio/speech")
                    .header("Authorization", "Bearer $apiKeyTrimmed")
                    .header("Content-Type", "application/json")
                    .post(json.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        cacheFile.writeBytes(bytes)
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            playMp3(cacheFile, onDone)
                        }
                    } else {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            speakLocal(text, language, onDone)
                        }
                    }
                } else {
                    // API napaka - zabeleži
                    stats.log(StatEvent.TTS_ERROR, "HTTP ${response.code}")
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        speakLocal(text, language, onDone)
                    }
                }
            } catch (e: Exception) {
                stats.log(StatEvent.TTS_ERROR, e.message ?: "unknown")
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    speakLocal(text, language, onDone)
                }
            }
        }.start()
    }

    private fun playMp3(file: File, onDone: () -> Unit) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    release()
                    mediaPlayer = null
                    onDone()
                }
            }
        } catch (e: Exception) {
            onDone()
        }
    }

    private fun speakLocal(text: String, language: String, onDone: () -> Unit) {
        if (!localTtsReady) { onDone(); return }
        val locale = if (language == "uk") Locale("uk", "UA") else Locale("sl", "SI")
        val result = localTts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Ukrainščina ni podprta lokalno → poskusi Google Translate TTS
            speakGoogleTranslate(text, language, onDone)
            return
        }
        localTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "local_${System.currentTimeMillis()}")
        // Oceni trajanje in pokliči onDone
        val delay = (text.length * 90 + 1500).toLong()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(onDone, delay)
    }

    private fun speakGoogleTranslate(text: String, language: String, onDone: () -> Unit) {
        // Fallback: Google Translate TTS (brezplačen, brez API ključa)
        val cacheFile = getCacheFile(text, language, "google")
        if (cacheFile.exists() && cacheFile.length() > 0) {
            playMp3(cacheFile, onDone)
            return
        }
        Thread {
            try {
                val encoded = java.net.URLEncoder.encode(text.take(200), "UTF-8")
                val url = "https://translate.google.com/translate_tts?ie=UTF-8&tl=$language&client=tw-ob&q=$encoded"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        cacheFile.writeBytes(bytes)
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            playMp3(cacheFile, onDone)
                        }
                        return@Thread
                    }
                }
                android.os.Handler(android.os.Looper.getMainLooper()).post { onDone() }
            } catch (e: Exception) {
                android.os.Handler(android.os.Looper.getMainLooper()).post { onDone() }
            }
        }.start()
    }

    fun stop() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        localTts?.stop()
    }

    fun clearCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    fun destroy() {
        stop()
        localTts?.shutdown()
    }

    private fun getCacheFile(text: String, language: String, voice: String): File {
        val hash = "${text}_${language}_${voice}".hashCode().toString()
        return File(cacheDir, "tts_$hash.mp3")
    }
}
