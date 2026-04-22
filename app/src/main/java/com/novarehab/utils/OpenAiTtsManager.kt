package com.novarehab.utils

import android.content.Context
import android.media.AudioAttributes
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

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    init {
        tts = TextToSpeech(context) { status ->
            ttsReady = (status == TextToSpeech.SUCCESS)
            if (ttsReady) {
                tts?.setSpeechRate(0.9f)
                // Poskusi nastaviti slovenščino, sicer privzeti jezik
                val r = tts?.setLanguage(Locale("sl", "SI"))
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.getDefault())
                }
            }
        }
    }

    fun initLocalTts(onReady: (() -> Unit)? = null) {
        if (ttsReady) { onReady?.invoke(); return }
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ onReady?.invoke() }, 1500)
    }

    fun speak(text: String, language: String, apiKey: String, voice: String, onDone: () -> Unit = {}) {
        if (text.isEmpty()) { onDone(); return }
        if (apiKey.isNotEmpty()) {
            speakOpenAI(text, language, apiKey, voice, onDone)
        } else {
            // Brez API ključa: Google Translate TTS (deluje brez nameščenih glasov)
            speakGoogle(text, language, onDone)
        }
    }

    private fun speakOpenAI(text: String, language: String, apiKey: String, voice: String, onDone: () -> Unit) {
        val cache = File(cacheDir, "${text.hashCode()}_${language}_${voice}.mp3")
        if (cache.exists() && cache.length() > 1024) { playFile(cache, onDone); return }
        Thread {
            try {
                val body = JSONObject().apply {
                    put("model", "tts-1"); put("input", text); put("voice", voice)
                }.toString().toRequestBody("application/json".toMediaType())
                val resp = http.newCall(Request.Builder()
                    .url("https://api.openai.com/v1/audio/speech")
                    .header("Authorization", "Bearer $apiKey")
                    .post(body).build()).execute()
                if (resp.isSuccessful) {
                    val bytes = resp.body?.bytes()
                    if (bytes != null && bytes.size > 1024) {
                        cache.writeBytes(bytes)
                        android.os.Handler(android.os.Looper.getMainLooper()).post { playFile(cache, onDone) }
                        return@Thread
                    }
                }
            } catch (e: Exception) {}
            android.os.Handler(android.os.Looper.getMainLooper()).post { speakGoogle(text, language, onDone) }
        }.start()
    }

    // Google Translate TTS - deluje brez nameščenih glasov, podpira SL in UK
    fun speakGoogle(text: String, language: String, onDone: () -> Unit = {}) {
        val cache = File(cacheDir, "g_${text.hashCode()}_$language.mp3")
        if (cache.exists() && cache.length() > 1024) { playFile(cache, onDone); return }
        Thread {
            try {
                val enc = java.net.URLEncoder.encode(text.take(200), "UTF-8")
                val url = "https://translate.google.com/translate_tts?ie=UTF-8&tl=$language&client=tw-ob&q=$enc"
                val resp = http.newCall(Request.Builder()
                    .url(url).header("User-Agent", "Mozilla/5.0").build()).execute()
                if (resp.isSuccessful) {
                    val bytes = resp.body?.bytes()
                    if (bytes != null && bytes.size > 1024) {
                        cache.writeBytes(bytes)
                        android.os.Handler(android.os.Looper.getMainLooper()).post { playFile(cache, onDone) }
                        return@Thread
                    }
                }
            } catch (e: Exception) {}
            // Zadnji fallback - Android TTS
            android.os.Handler(android.os.Looper.getMainLooper()).post { speakAndroid(text, language, onDone) }
        }.start()
    }

    fun speakAndroid(text: String, language: String, onDone: () -> Unit = {}) {
        if (!ttsReady) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                speakAndroid(text, language, onDone)
            }, 500)
            return
        }
        val locale = if (language == "uk") Locale("uk", "UA") else Locale("sl", "SI")
        val r = tts?.setLanguage(locale)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts?.setLanguage(Locale.getDefault())
        }
        val uid = "u${System.currentTimeMillis()}"
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                android.os.Handler(android.os.Looper.getMainLooper()).post { onDone() }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                android.os.Handler(android.os.Looper.getMainLooper()).post { onDone() }
            }
        })
        tts?.stop()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, uid)
    }

    private fun playFile(file: File, onDone: () -> Unit) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                setDataSource(file.absolutePath)
                prepare()
                start()
                setOnCompletionListener { release(); mediaPlayer = null; onDone() }
                setOnErrorListener { _, _, _ -> release(); mediaPlayer = null; onDone(); true }
            }
        } catch (e: Exception) { onDone() }
    }

    fun stop() { tts?.stop(); mediaPlayer?.release(); mediaPlayer = null }

    fun destroy() {
        stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }
}
