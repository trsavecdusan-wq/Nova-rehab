package com.novarehab.utils

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

class OpenAiTtsManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var mediaPlayer: MediaPlayer? = null
    private val cacheDir = File(context.getExternalFilesDir(null), "tts_cache").also { it.mkdirs() }
    private val stats by lazy { StatsManager(context) }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Nastavi slovenščino kot privzeto
                val result = tts?.setLanguage(Locale("sl", "SI"))
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.getDefault())
                }
                tts?.setSpeechRate(0.9f)
                tts?.setPitch(1.0f)
                ttsReady = true
            }
        }
    }

    fun initLocalTts(onReady: (() -> Unit)? = null) {
        if (ttsReady) { onReady?.invoke(); return }
        initTts()
        // Pokliči onReady čez 1s ko je TTS verjetno inicializiran
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            onReady?.invoke()
        }, 1000)
    }

    fun speak(
        text: String,
        language: String,
        apiKey: String,
        voice: String,
        onDone: () -> Unit = {}
    ) {
        if (text.isEmpty()) { onDone(); return }

        if (apiKey.isNotEmpty()) {
            speakOpenAI(text, language, apiKey, voice, onDone)
        } else {
            speakAndroid(text, language, onDone)
        }
    }

    // OpenAI TTS z cache
    private fun speakOpenAI(text: String, language: String, apiKey: String, voice: String, onDone: () -> Unit) {
        val cacheFile = File(cacheDir, "${text.hashCode()}_${language}_${voice}.mp3")
        if (cacheFile.exists() && cacheFile.length() > 1024) {
            playFile(cacheFile, onDone)
            return
        }
        Thread {
            try {
                val body = JSONObject().apply {
                    put("model", "tts-1")
                    put("input", text)
                    put("voice", voice)
                }.toString().toRequestBody("application/json".toMediaType())

                val req = Request.Builder()
                    .url("https://api.openai.com/v1/audio/speech")
                    .header("Authorization", "Bearer $apiKey")
                    .post(body)
                    .build()

                val resp = http.newCall(req).execute()
                if (resp.isSuccessful) {
                    val bytes = resp.body?.bytes()
                    if (bytes != null && bytes.size > 1024) {
                        cacheFile.writeBytes(bytes)
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            playFile(cacheFile, onDone)
                        }
                        return@Thread
                    }
                }
                stats.log(StatEvent.TTS_ERROR, "HTTP ${resp.code}")
            } catch (e: Exception) {
                stats.log(StatEvent.TTS_ERROR, e.message ?: "error")
            }
            // Fallback na Android TTS
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                speakAndroid(text, language, onDone)
            }
        }.start()
    }

    // Direktni Android TTS - enostavno in zanesljivo
    fun speakAndroid(text: String, language: String, onDone: () -> Unit = {}) {
        if (!ttsReady) {
            // TTS še ni pripravljen - počakaj in poskusi znova
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                speakAndroid(text, language, onDone)
            }, 500)
            return
        }

        // Nastavi jezik
        val locale = if (language == "uk") Locale("uk", "UA") else Locale("sl", "SI")
        val langResult = tts?.setLanguage(locale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA ||
            langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            if (language == "uk") {
                // Ukrainščina ni nameščena → Google Translate TTS
                speakGoogleTranslate(text, language, onDone)
                return
            }
            tts?.setLanguage(Locale.getDefault())
        }

        val utteranceId = "utt_${System.currentTimeMillis()}"

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                android.os.Handler(android.os.Looper.getMainLooper()).post { onDone() }
            }
            override fun onError(id: String?) {
                android.os.Handler(android.os.Looper.getMainLooper()).post { onDone() }
            }
        })

        tts?.stop()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private fun playFile(file: File, onDone: () -> Unit) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
                setOnCompletionListener { release(); mediaPlayer = null; onDone() }
                setOnErrorListener { _, _, _ -> release(); mediaPlayer = null; onDone(); true }
            }
        } catch (e: Exception) {
            onDone()
        }
    }

    fun stop() {
        tts?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun speakGoogleTranslate(text: String, language: String, onDone: () -> Unit) {
        val cacheFile = File(cacheDir, "gt_${text.hashCode()}_$language.mp3")
        if (cacheFile.exists() && cacheFile.length() > 1024) {
            playFile(cacheFile, onDone)
            return
        }
        Thread {
            try {
                val encoded = java.net.URLEncoder.encode(text.take(200), "UTF-8")
                val url = "https://translate.google.com/translate_tts?ie=UTF-8&tl=$language&client=tw-ob&q=$encoded"
                val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
                val resp = http.newCall(req).execute()
                if (resp.isSuccessful) {
                    val bytes = resp.body?.bytes()
                    if (bytes != null && bytes.size > 1024) {
                        cacheFile.writeBytes(bytes)
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            playFile(cacheFile, onDone)
                        }
                        return@Thread
                    }
                }
            } catch (e: Exception) {}
            // Fallback - uporabi privzeti TTS jezik
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                tts?.setLanguage(Locale.getDefault())
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "gt_${System.currentTimeMillis()}")
                val delay = (text.length * 90 + 1500).toLong()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(onDone, delay)
            }
        }.start()
    }

    fun destroy() {
        stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }
}
