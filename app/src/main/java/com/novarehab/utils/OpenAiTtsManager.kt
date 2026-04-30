package com.novarehab.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
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
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
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
        apiBaseUrl: String = "",
        onDone: () -> Unit = {}
    ) {
        speak(
            text = text,
            language = language,
            apiKey = apiKey,
            voice = voice,
            apiBaseUrl = apiBaseUrl,
            speed = 0.92f,
            volume = 1.0f,
            style = "Speak clearly, warmly, calmly and naturally. Use a gentle rehabilitation assistant voice. Keep the speech easy to understand, with good articulation and a natural Slovenian rhythm when speaking Slovenian.",
            onDone = onDone
        )
    }

    fun speak(
        text: String,
        language: String,
        apiKey: String,
        voice: String,
        apiBaseUrl: String,
        speed: Float,
        volume: Float,
        style: String,
        onDone: () -> Unit = {}
    ) {
        val clean = text.trim()
        if (clean.isEmpty()) {
            onDone()
            return
        }

        val safeVoice = normalizeVoice(voice)
        val safeSpeed = speed.coerceIn(0.65f, 1.25f)
        val safeVolume = volume.coerceIn(0.2f, 1.0f)
        val cache = File(cacheDir, cacheName(clean, language, safeVoice, safeSpeed, style))

        if (cache.exists() && cache.length() > 1024) {
            playFile(cache, safeVolume, onDone)
            return
        }

        if (apiKey.isNotBlank() && apiBaseUrl.isNotBlank() && isOnline()) {
            speakOpenAI(
                text = clean,
                language = language,
                apiKey = apiKey,
                apiBaseUrl = apiBaseUrl,
                voice = safeVoice,
                speed = safeSpeed,
                volume = safeVolume,
                style = style,
                cache = cache,
                onDone = onDone
            )
        } else {
            speakAndroid(clean, language, onDone)
        }
    }

    private fun speakOpenAI(
        text: String,
        language: String,
        apiKey: String,
        apiBaseUrl: String,
        voice: String,
        speed: Float,
        volume: Float,
        style: String,
        cache: File,
        onDone: () -> Unit
    ) {
        Thread {
            try {
                val bodyJson = JSONObject().apply {
                    put("model", "gpt-4o-mini-tts")
                    put("input", text)
                    put("voice", voice)
                    put("speed", speed)
                    put("instructions", style)
                    put("response_format", "mp3")
                }

                val request = Request.Builder()
                    .url(buildEndpoint(apiBaseUrl, "v1/audio/speech"))
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                http.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bytes = response.body?.bytes()
                        if (bytes != null && bytes.size > 1024) {
                            cache.writeBytes(bytes)
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                playFile(cache, volume, onDone)
                            }
                            return@Thread
                        }
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
                if (ttsReady) {
                    speakAndroid(text, language, onDone)
                } else {
                    Toast.makeText(context, "Slovenski glas ni namescen.", Toast.LENGTH_LONG).show()
                    onDone()
                }
            }
            return
        }

        if (!setBestLocale(language)) {
            Toast.makeText(context, "Slovenski glas ni namescen.", Toast.LENGTH_LONG).show()
        }

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
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, uid)
    }

    private fun setBestLocale(language: String): Boolean {
        val candidates = when (language.lowercase()) {
            "sl", "si", "slovenian" -> listOf(
                Locale("sl", "SI"),
                Locale("hr", "HR"),
                Locale("sr", "RS")
            )

            "uk", "ua", "ukrainian" -> listOf(
                Locale("uk", "UA"),
                Locale("ru", "RU"),
                Locale("hr", "HR"),
                Locale("sl", "SI")
            )

            "hr" -> listOf(
                Locale("hr", "HR"),
                Locale("sr", "RS"),
                Locale("sl", "SI")
            )

            "sr" -> listOf(
                Locale("sr", "RS"),
                Locale("hr", "HR"),
                Locale("sl", "SI")
            )

            "de" -> listOf(
                Locale.GERMANY,
                Locale("de", "DE")
            )

            "en", "english" -> listOf(
                Locale.US,
                Locale.UK,
                Locale.ENGLISH
            )

            else -> listOf(
                Locale(language),
                Locale(language, "")
            )
        }

        for (locale in candidates) {
            val result = tts?.setLanguage(locale)
            if (
                result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                return true
            }
        }

        val fallback = tts?.setLanguage(Locale("sl", "SI"))
        return fallback != TextToSpeech.LANG_MISSING_DATA &&
            fallback != TextToSpeech.LANG_NOT_SUPPORTED
    }

    private fun playFile(file: File, volume: Float, onDone: () -> Unit) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                setVolume(volume, volume)
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

    private fun normalizeVoice(voice: String): String {
        return when (voice.trim().lowercase()) {
            "alloy", "ash", "ballad", "coral", "echo", "fable", "nova",
            "onyx", "sage", "shimmer", "verse", "marin", "cedar" -> voice.trim().lowercase()
            else -> "marin"
        }
    }

    private fun buildEndpoint(baseUrl: String, path: String): String {
        val base = baseUrl.trim().trimEnd('/')
        val cleanPath = path.trim().trimStart('/')

        return if (base.endsWith("/v1")) {
            "$base/${cleanPath.removePrefix("v1/")}"
        } else {
            "$base/$cleanPath"
        }
    }

    private fun cacheName(
        text: String,
        language: String,
        voice: String,
        speed: Float,
        style: String
    ): String {
        val key = "${text}_${language}_${voice}_${speed}_${style}".hashCode()
        return "${key}_${language}_${voice}.mp3"
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
