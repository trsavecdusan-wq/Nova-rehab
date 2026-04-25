package com.novarehab.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
    private var initializingDefault = false

    // RHVoice Android uporablja ta package name v večini distribucij. Če ni nameščen,
    // se varno vrnemo na sistemski Android TTS.
    private val rhVoicePackage = "com.github.olga_yakovleva.rhvoice.android"

    // Interni cache: ko je OpenAI govor enkrat ustvarjen, deluje tudi pozneje brez ponovnega prenosa.
    private val cacheDir = File(context.filesDir, "tts_cache").also { it.mkdirs() }

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    init {
        initBestLocalTts()
    }

    private fun initBestLocalTts() {
        val engine = if (isPackageInstalled(rhVoicePackage)) rhVoicePackage else null
        initTts(engine)
    }

    private fun initTts(enginePackage: String?) {
        try {
            tts = if (enginePackage != null) {
                TextToSpeech(context, { status -> onTtsInit(status, enginePackage != null) }, enginePackage)
            } else {
                initializingDefault = true
                TextToSpeech(context) { status -> onTtsInit(status, false) }
            }
        } catch (e: Exception) {
            initializingDefault = true
            tts = TextToSpeech(context) { status -> onTtsInit(status, false) }
        }
    }

    private fun onTtsInit(status: Int, triedRhVoice: Boolean) {
        ttsReady = (status == TextToSpeech.SUCCESS)
        if (!ttsReady && triedRhVoice) {
            // RHVoice je lahko nameščen, ampak brez glasu ali z napako. Vrnemo se na sistemski TTS.
            try { tts?.shutdown() } catch (_: Exception) {}
            initializingDefault = true
            tts = TextToSpeech(context) { st -> onTtsInit(st, false) }
            return
        }
        if (ttsReady) {
            tts?.setSpeechRate(0.88f)
            tts?.setPitch(1.0f)
            setBestLocale("sl")
        }
    }

    fun initLocalTts(onReady: (() -> Unit)? = null) {
        if (ttsReady) { onReady?.invoke(); return }
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ onReady?.invoke() }, 1500)
    }

    fun speak(text: String, language: String, apiKey: String, voice: String, onDone: () -> Unit = {}) {
        val clean = text.trim()
        if (clean.isEmpty()) { onDone(); return }

        // Če je OpenAI cache že narejen, ga predvajamo tudi brez interneta.
        val cache = File(cacheDir, cacheName(clean, language, voice))
        if (cache.exists() && cache.length() > 1024) {
            playFile(cache, onDone)
            return
        }

        // Online OpenAI samo, če je ključ vpisan in je internet dejansko aktiven.
        if (apiKey.isNotBlank() && isOnline()) {
            speakOpenAI(clean, language, apiKey, voice, cache, onDone)
        } else {
            // Offline: prednost RHVoice, drugače Android TTS. To je varnostni način za izpad interneta.
            speakAndroid(clean, language, onDone)
        }
    }

    private fun speakOpenAI(text: String, language: String, apiKey: String, voice: String, cache: File, onDone: () -> Unit) {
        Thread {
            try {
                val body = JSONObject().apply {
                    put("model", "tts-1")
                    put("input", text)
                    put("voice", voice.ifBlank { "nova" })
                }.toString().toRequestBody("application/json".toMediaType())
                val resp = http.newCall(Request.Builder()
                    .url("https://api.openai.com/v1/audio/speech")
                    .header("Authorization", "Bearer $apiKey")
                    .post(body)
                    .build()).execute()
                if (resp.isSuccessful) {
                    val bytes = resp.body?.bytes()
                    if (bytes != null && bytes.size > 1024) {
                        cache.writeBytes(bytes)
                        android.os.Handler(android.os.Looper.getMainLooper()).post { playFile(cache, onDone) }
                        return@Thread
                    }
                }
            } catch (_: Exception) {}
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
        setBestLocale(language)
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

    private fun setBestLocale(language: String) {
        val candidates = when (language) {
            "uk" -> listOf(Locale("uk", "UA"), Locale("ru", "RU"), Locale("sl", "SI"))
            "hr" -> listOf(Locale("hr", "HR"), Locale("sr", "RS"), Locale("sl", "SI"))
            "sr" -> listOf(Locale("sr", "RS"), Locale("hr", "HR"), Locale("sl", "SI"))
            "de" -> listOf(Locale.GERMANY, Locale("sl", "SI"))
            "en" -> listOf(Locale.ENGLISH, Locale("sl", "SI"))
            else -> listOf(Locale("sl", "SI"), Locale("hr", "HR"), Locale("sr", "RS"), Locale.ENGLISH)
        }
        for (locale in candidates) {
            val r = tts?.setLanguage(locale)
            if (r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED) return
        }
        tts?.setLanguage(Locale.getDefault())
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

    private fun isOnline(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Exception) { false }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) { false }
    }

    private fun cacheName(text: String, language: String, voice: String): String =
        "${text.hashCode()}_${language}_${voice.ifBlank { "nova" }}.mp3"

    fun stop() { tts?.stop(); mediaPlayer?.release(); mediaPlayer = null }

    fun destroy() {
        stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }
}
