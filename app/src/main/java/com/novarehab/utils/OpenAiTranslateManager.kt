package com.novarehab.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

class OpenAiTtsManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var mediaPlayer: MediaPlayer? = null

    private val cacheDir = File(context.filesDir, "tts_cache").also { it.mkdirs() }

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    init {
        initLocalTts()
    }

    fun initLocalTts(onReady: (() -> Unit)? = null) {
        if (tts != null) {
            onReady?.invoke()
            return
        }

        tts = TextToSpeech(context.applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.setSpeechRate(0.88f)
                tts?.setPitch(1.0f)
                setBestLocale("sl")
            }
            onReady?.invoke()
        }
    }

    fun speak(
        text: String,
        language: String,
        apiKey: String,
        voice: String,
        onDone: () -> Unit = {}
    ) {
        val clean = text.trim()
        if (clean.isEmpty()) {
            onDone()
            return
        }

        val cache = File(cacheDir, cacheName(clean, language, voice))

        if (cache.exists() && cache.length() > 1024) {
            playFile(cache, onDone)
            return
        }

        if (apiKey.isNotBlank() && isOnline()) {
            speakOpenAI(clean, language, apiKey, voice, cache, onDone)
        } else {
            speakAndroid(clean, language, onDone)
        }
    }

    private fun speakOpenAI(
        text: String,
        language: String,
        apiKey: String,
        voice: String,
        cache: File,
        onDone: () -> Unit
    ) {
        Thread {
            try {
                val body = JSONObject().apply {
                    put("model", "tts-1")
                    put("input", text)
                    put("voice", voice.ifBlank { "nova" })
                }.toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("https://api.openai.com/v1/audio/speech")
                    .header("Authorization", "Bearer $apiKey")
                    .post(body)
                    .build()

                val response = http.newCall(request).execute()

                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null && bytes.size > 1024) {
                        cache.writeBytes(bytes)
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            playFile(cache, onDone)
                        }
                        return@Thread
                    }
                }
            } catch (_: Exception) {
            }

            android.os.Handler(android.os.Looper.getMainLooper()).post {
                speakAndroid(text, language, onDone)
            }
        }.start()
    }

    fun speakAndroid(
        text: String,
        language: String,
        onDone: () -> Unit = {}
    ) {
        if (!ttsReady) {
            initLocalTts {
                speakAndroid(text, language, onDone)
            }
            return
        }

        setBestLocale(language)

        val uid = "rehab_${System.currentTimeMillis()}"

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onDone()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onDone()
                }
            }
        })

        tts?.stop()
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, uid)

        if (result == TextToSpeech.ERROR || result == null) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                onDone()
            }, 500L)
        }
    }

    private fun setBestLocale(language: String) {
        val candidates = when (language.lowercase()) {
            "sl", "si", "slovenian" -> listOf(
                Locale("sl", "SI"),
                Locale("hr", "HR"),
                Locale("sr", "RS"),
                Locale.getDefault(),
                Locale.ENGLISH
            )

            "uk", "ua", "ukrainian" -> listOf(
                Locale("uk", "UA"),
                Locale("ru", "RU"),
                Locale("hr", "HR"),
                Locale("sl", "SI"),
                Locale.getDefault(),
                Locale.ENGLISH
            )

            "hr" -> listOf(
                Locale("hr", "HR"),
                Locale("sr", "RS"),
                Locale("sl", "SI"),
                Locale.getDefault(),
                Locale.ENGLISH
            )

            "sr" -> listOf(
                Locale("sr", "RS"),
                Locale("hr", "HR"),
                Locale("sl", "SI"),
                Locale.getDefault(),
                Locale.ENGLISH
            )

            "en", "english" -> listOf(
                Locale.US,
                Locale.UK,
                Locale.ENGLISH,
                Locale.getDefault()
            )

            else -> listOf(
                Locale("sl", "SI"),
                Locale("hr", "HR"),
                Locale("sr", "RS"),
                Locale.getDefault(),
                Locale.ENGLISH
            )
        }

        for (locale in candidates) {
            val result = tts?.setLanguage(locale)
            if (
                result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                return
            }
        }

        tts?.setLanguage(Locale.getDefault())
    }

    private fun playFile(file: File, onDone: () -> Unit) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    release()
                    mediaPlayer = null
                    onDone()
                }
                setOnErrorListener { _, _, _ ->
                    release()
                    mediaPlayer = null
                    onDone()
                    true
                }
            }
        } catch (_: Exception) {
            onDone()
        }
    }

    private fun isOnline(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Exception) {
            false
        }
    }

    private fun cacheName(text: String, language: String, voice: String): String {
        return "${text.hashCode()}_${language}_${voice.ifBlank { "nova" }}.mp3"
    }

    fun stop() {
        tts?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun destroy() {
        stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }
}
