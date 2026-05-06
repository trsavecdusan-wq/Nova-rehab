package com.novarehab.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import java.util.concurrent.atomic.AtomicBoolean

enum class SpeechSource {
    CACHE,
    OPENAI,
    LOCAL_DIRECT,
    LOCAL_FALLBACK
}

data class SpeechPlaybackReport(
    val source: SpeechSource,
    val delayMs: Long,
    val cacheHit: Boolean,
    val success: Boolean
)

class OpenAiTtsManager(private val context: Context) {
    private val appContext = context.applicationContext
    private val prefs = PrefsManager(appContext)
    private val cacheManager = SpeechCacheManager(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var mediaPlayer: MediaPlayer? = null

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    init {
        initLocalTts()
    }

    fun initLocalTts(onReady: (() -> Unit)? = null) {
        if (tts != null) {
            onReady?.invoke()
            return
        }

        tts = TextToSpeech(appContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                applyLocalSettings()
                setBestLocale(prefs.getDefaultSpeechLanguage())
            }
            onReady?.invoke()
        }
    }

    fun preloadCommonPhrasesAsync(language: String = prefs.getDefaultSpeechLanguage()) {
        if (!prefs.isOpenAiTtsEnabled()) return
        val apiKey = prefs.runCatchingApiKey()
        val apiBaseUrl = prefs.runCatchingApiBaseUrl()
        if (apiKey.isBlank() || apiBaseUrl.isBlank() || !isOnline()) return

        Thread {
            cacheManager.deleteOldCache()
            val voice = normalizeVoice(prefs.getTtsVoice())
            val speed = prefs.getTtsSpeed().coerceIn(0.65f, 1.25f)
            val model = prefs.getTtsModel().ifBlank { "gpt-4o-mini-tts" }
            val format = prefs.getTtsResponseFormat().ifBlank { "mp3" }
            val style = buildStylePrompt(prefs.getSpeechStylePreset())

            cacheManager.preloadCommonPhrases().forEach { phrase ->
                val cached = cacheManager.getCachedAudio(phrase, language, voice, speed, style, model, format)
                if (cached == null) {
                    requestOpenAiAudio(
                        text = phrase,
                        language = language,
                        apiKey = apiKey,
                        apiBaseUrl = apiBaseUrl,
                        voice = voice,
                        speed = speed,
                        style = style,
                        model = model,
                        format = format
                    )?.let { bytes ->
                        cacheManager.saveCachedAudio(phrase, language, voice, speed, style, model, format, bytes)
                    }
                }
            }
        }.start()
    }

    fun clearCache() = cacheManager.clearCache()

    fun cacheSize(): Long = cacheManager.cacheSize()

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
            speed = prefs.getTtsSpeed(),
            volume = prefs.getTtsVolume(),
            style = buildStylePrompt(prefs.getSpeechStylePreset()),
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
        val clean = applySpeechTextRules(text.trim())
        if (clean.isEmpty()) {
            onDone()
            return
        }

        cacheManager.deleteOldCache()

        val mode = prefs.getSpeechResponseMode()
        val provider = prefs.getSpeechProviderMode()
        val openAiEnabled = prefs.isOpenAiTtsEnabled() && apiKey.isNotBlank() && apiBaseUrl.isNotBlank()
        val fallbackEnabled = prefs.isLocalTtsFallbackEnabled()
        val online = isOnline()
        val safeVoice = normalizeVoice(voice)
        val safeSpeed = speed.coerceIn(0.65f, 1.25f)
        val safeVolume = volume.coerceIn(0.2f, 1.0f)
        val model = prefs.getTtsModel().ifBlank { "gpt-4o-mini-tts" }
        val format = prefs.getTtsResponseFormat().ifBlank { "mp3" }
        val cacheFile = cacheManager.getCachedAudio(clean, language, safeVoice, safeSpeed, style, model, format)
        val startedAt = System.currentTimeMillis()

        if (cacheFile != null) {
            playFile(cacheFile, safeVolume, startedAt, SpeechSource.CACHE, cacheHit = true, onDone = onDone)
            return
        }

        if (provider == "local_android_tts" || !openAiEnabled || !online) {
            speakLocalWithReport(
                clean,
                language,
                if (openAiEnabled && online) SpeechSource.LOCAL_DIRECT else SpeechSource.LOCAL_FALLBACK,
                startedAt,
                onDone
            )
            if (openAiEnabled && online) {
                generateCacheInBackground(clean, language, apiKey, safeVoice, apiBaseUrl, safeSpeed, style, model, format)
            }
            return
        }

        when (mode) {
            "fast_local_first" -> {
                speakLocalWithReport(clean, language, SpeechSource.LOCAL_DIRECT, startedAt, onDone)
                generateCacheInBackground(clean, language, apiKey, safeVoice, apiBaseUrl, safeSpeed, style, model, format)
            }
            "openai_if_cached" -> {
                speakLocalWithReport(clean, language, SpeechSource.LOCAL_DIRECT, startedAt, onDone)
                generateCacheInBackground(clean, language, apiKey, safeVoice, apiBaseUrl, safeSpeed, style, model, format)
            }
            "openai_preferred" -> {
                speakOpenAiWithFallback(
                    text = clean,
                    language = language,
                    apiKey = apiKey,
                    voice = safeVoice,
                    apiBaseUrl = apiBaseUrl,
                    speed = safeSpeed,
                    volume = safeVolume,
                    style = style,
                    model = model,
                    format = format,
                    fallbackEnabled = fallbackEnabled,
                    fallbackDelayMs = 1200L,
                    startedAt = startedAt,
                    onDone = onDone
                )
            }
            else -> {
                val shortPhrase = clean.length <= 24 || clean.split(' ').filter { it.isNotBlank() }.size <= 4
                if (shortPhrase) {
                    speakLocalWithReport(clean, language, SpeechSource.LOCAL_DIRECT, startedAt, onDone)
                    generateCacheInBackground(clean, language, apiKey, safeVoice, apiBaseUrl, safeSpeed, style, model, format)
                } else {
                    speakOpenAiWithFallback(
                        text = clean,
                        language = language,
                        apiKey = apiKey,
                        voice = safeVoice,
                        apiBaseUrl = apiBaseUrl,
                        speed = safeSpeed,
                        volume = safeVolume,
                        style = style,
                        model = model,
                        format = format,
                        fallbackEnabled = fallbackEnabled,
                        fallbackDelayMs = 900L,
                        startedAt = startedAt,
                        onDone = onDone
                    )
                }
            }
        }
    }

    fun speakOpenAiOnly(
        text: String,
        language: String,
        apiKey: String,
        voice: String,
        apiBaseUrl: String,
        onDone: () -> Unit = {}
    ) {
        val clean = applySpeechTextRules(text.trim())
        if (clean.isEmpty()) {
            onDone()
            return
        }

        val startedAt = System.currentTimeMillis()
        val safeVoice = normalizeVoice(voice)
        val speed = prefs.getTtsSpeed().coerceIn(0.65f, 1.25f)
        val volume = prefs.getTtsVolume().coerceIn(0.2f, 1.0f)
        val model = prefs.getTtsModel().ifBlank { "gpt-4o-mini-tts" }
        val format = prefs.getTtsResponseFormat().ifBlank { "mp3" }
        val style = buildStylePrompt(prefs.getSpeechStylePreset())

        val cacheFile = cacheManager.getCachedAudio(clean, language, safeVoice, speed, style, model, format)
        if (cacheFile != null) {
            playFile(cacheFile, volume, startedAt, SpeechSource.CACHE, cacheHit = true, onDone = onDone)
            return
        }

        speakOpenAiWithFallback(
            text = clean,
            language = language,
            apiKey = apiKey,
            voice = safeVoice,
            apiBaseUrl = apiBaseUrl,
            speed = speed,
            volume = volume,
            style = style,
            model = model,
            format = format,
            fallbackEnabled = true,
            fallbackDelayMs = 1200L,
            startedAt = startedAt,
            onDone = onDone
        )
    }

    fun speakAndroid(
        text: String,
        language: String,
        onDone: () -> Unit = {}
    ) {
        speakLocalWithReport(
            text = applySpeechTextRules(text),
            language = language,
            source = SpeechSource.LOCAL_DIRECT,
            startedAt = System.currentTimeMillis(),
            onDone = onDone
        )
    }

    private fun speakOpenAiWithFallback(
        text: String,
        language: String,
        apiKey: String,
        voice: String,
        apiBaseUrl: String,
        speed: Float,
        volume: Float,
        style: String,
        model: String,
        format: String,
        fallbackEnabled: Boolean,
        fallbackDelayMs: Long,
        startedAt: Long,
        onDone: () -> Unit
    ) {
        val delivered = AtomicBoolean(false)

        if (fallbackEnabled) {
            mainHandler.postDelayed({
                if (delivered.compareAndSet(false, true)) {
                    speakLocalWithReport(text, language, SpeechSource.LOCAL_FALLBACK, startedAt, onDone)
                }
            }, fallbackDelayMs)
        }

        Thread {
            val bytes = requestOpenAiAudio(
                text = text,
                language = language,
                apiKey = apiKey,
                apiBaseUrl = apiBaseUrl,
                voice = voice,
                speed = speed,
                style = style,
                model = model,
                format = format
            )

            if (bytes != null && bytes.size > 1024) {
                val file = cacheManager.saveCachedAudio(text, language, voice, speed, style, model, format, bytes)
                mainHandler.post {
                    if (delivered.compareAndSet(false, true)) {
                        playFile(file, volume, startedAt, SpeechSource.OPENAI, cacheHit = false, onDone = onDone)
                    }
                }
            } else if (!fallbackEnabled) {
                mainHandler.post {
                    if (delivered.compareAndSet(false, true)) {
                        speakLocalWithReport(text, language, SpeechSource.LOCAL_FALLBACK, startedAt, onDone)
                    }
                }
            }
        }.start()
    }

    private fun generateCacheInBackground(
        text: String,
        language: String,
        apiKey: String,
        voice: String,
        apiBaseUrl: String,
        speed: Float,
        style: String,
        model: String,
        format: String
    ) {
        if (!prefs.isOpenAiTtsEnabled() || apiKey.isBlank() || apiBaseUrl.isBlank() || !isOnline()) return
        if (cacheManager.getCachedAudio(text, language, voice, speed, style, model, format) != null) return

        Thread {
            requestOpenAiAudio(
                text = text,
                language = language,
                apiKey = apiKey,
                apiBaseUrl = apiBaseUrl,
                voice = voice,
                speed = speed,
                style = style,
                model = model,
                format = format
            )?.let { bytes ->
                if (bytes.size > 1024) {
                    cacheManager.saveCachedAudio(text, language, voice, speed, style, model, format, bytes)
                }
            }
        }.start()
    }

    private fun requestOpenAiAudio(
        text: String,
        language: String,
        apiKey: String,
        apiBaseUrl: String,
        voice: String,
        speed: Float,
        style: String,
        model: String,
        format: String
    ): ByteArray? {
        return try {
            val bodyJson = JSONObject().apply {
                put("model", model)
                put("input", text)
                put("voice", voice)
                put("speed", speed)
                put("instructions", styleWithLanguage(language, style))
                put("response_format", format)
            }

            val request = Request.Builder()
                .url(buildEndpoint(apiBaseUrl, "v1/audio/speech"))
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.bytes()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun speakLocalWithReport(
        text: String,
        language: String,
        source: SpeechSource,
        startedAt: Long,
        onDone: () -> Unit
    ) {
        if (!ttsReady) {
            initLocalTts {
                if (ttsReady) {
                    speakLocalWithReport(text, language, source, startedAt, onDone)
                } else {
                    recordAndFinish(source, startedAt, cacheHit = false, success = false, onDone = onDone)
                }
            }
            return
        }

        if (!setBestLocale(language) && !setBestLocale(prefs.getFallbackSpeechLanguage())) {
            recordAndFinish(source, startedAt, cacheHit = false, success = false, onDone = onDone)
            return
        }

        val uid = "rehab_${System.currentTimeMillis()}"
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, prefs.getTtsVolume())
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                mainHandler.post {
                    recordAndFinish(source, startedAt, cacheHit = false, success = true, onDone = onDone)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                mainHandler.post {
                    recordAndFinish(source, startedAt, cacheHit = false, success = false, onDone = onDone)
                }
            }
        })

        tts?.stop()
        applyLocalSettings()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, uid)
    }

    private fun applyLocalSettings() {
        tts?.setSpeechRate(prefs.getTtsSpeed())
        tts?.setPitch(prefs.getTtsPitch())
    }

    private fun setBestLocale(language: String): Boolean {
        val candidates = when (language.lowercase()) {
            "sl", "si", "slovenian" -> listOf(Locale("sl", "SI"), Locale("hr", "HR"), Locale("sr", "RS"))
            "uk", "ua", "ukrainian" -> listOf(Locale("uk", "UA"), Locale("ru", "RU"), Locale("hr", "HR"), Locale("sl", "SI"))
            "hr" -> listOf(Locale("hr", "HR"), Locale("sr", "RS"), Locale("sl", "SI"))
            "sr" -> listOf(Locale("sr", "RS"), Locale("hr", "HR"), Locale("sl", "SI"))
            "de" -> listOf(Locale.GERMANY, Locale("de", "DE"))
            "en", "english" -> listOf(Locale.US, Locale.UK, Locale.ENGLISH)
            else -> listOf(Locale(language), Locale(language, ""))
        }

        for (locale in candidates) {
            val result = tts?.setLanguage(locale)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                return true
            }
        }

        val fallback = tts?.setLanguage(Locale("sl", "SI"))
        return fallback != TextToSpeech.LANG_MISSING_DATA && fallback != TextToSpeech.LANG_NOT_SUPPORTED
    }

    private fun playFile(
        file: File,
        volume: Float,
        startedAt: Long,
        source: SpeechSource,
        cacheHit: Boolean,
        onDone: () -> Unit
    ) {
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
                    recordAndFinish(source, startedAt, cacheHit, success = true, onDone = onDone)
                }
                setOnErrorListener { _, _, _ ->
                    release()
                    mediaPlayer = null
                    recordAndFinish(source, startedAt, cacheHit, success = false, onDone = onDone)
                    true
                }
            }
        } catch (_: Exception) {
            recordAndFinish(source, startedAt, cacheHit, success = false, onDone = onDone)
        }
    }

    private fun recordAndFinish(
        source: SpeechSource,
        startedAt: Long,
        cacheHit: Boolean,
        success: Boolean,
        onDone: () -> Unit
    ) {
        val delay = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
        prefs.recordSpeechDiagnostic(delay, source.name.lowercase(), cacheHit, success)
        onDone()
    }

    private fun isOnline(): Boolean {
        return try {
            val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
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
        return if (base.endsWith("/v1")) "$base/${cleanPath.removePrefix("v1/")}" else "$base/$cleanPath"
    }

    private fun styleWithLanguage(language: String, style: String): String {
        val languageHint = when (language.lowercase()) {
            "uk" -> "Speak clearly in Ukrainian."
            "en" -> "Speak clearly in English."
            "de" -> "Speak clearly in German."
            "hr" -> "Speak clearly in Croatian."
            else -> "Speak clearly in Slovenian."
        }
        return "$languageHint $style"
    }

    private fun buildStylePrompt(preset: String): String {
        val clarity = prefs.getSpeechPronunciationClarity()
        val warmth = prefs.getSpeechEmotionalWarmth()
        val calmness = prefs.getSpeechCalmness()
        val pauseWords = prefs.getSpeechPauseBetweenWordsMs()
        val pauseSentences = prefs.getSpeechPauseBetweenSentencesMs()
        val rehab = prefs.isSpeechRehabilitationModeEnabled()
        val shortMode = prefs.isSpeechShortSentenceModeEnabled()

        val presetLine = when (preset) {
            "slow_clear" -> "Speak slowly and clearly."
            "warm_caregiver" -> "Speak warmly and kindly, like a trusted caregiver."
            "very_simple" -> "Use very simple, short sentences."
            "ukrainian_clear" -> "Use very clear Ukrainian rehabilitation speech."
            "slovenian_clear" -> "Use very clear Slovenian rehabilitation speech."
            "calm" -> "Speak calmly and steadily."
            "warm" -> "Speak warmly and gently."
            else -> "Speak clearly, warmly and calmly, like a patient rehabilitation assistant."
        }

        return buildString {
            append(presetLine)
            append(" Keep pronunciation clarity around $clarity out of 100.")
            append(" Emotional warmth around $warmth out of 100.")
            append(" Calmness around $calmness out of 100.")
            if (rehab) append(" Use rehabilitation-friendly pacing.")
            if (shortMode) append(" Keep sentences short and easy to understand.")
            if (pauseWords > 0) append(" Add about $pauseWords milliseconds of space between important words.")
            if (pauseSentences > 0) append(" Add about $pauseSentences milliseconds pause between sentences.")
        }
    }

    private fun applySpeechTextRules(text: String): String {
        var result = text.trim()
        if (prefs.isSpeechShortSentenceModeEnabled() && result.length > 120) {
            result = result.take(120).trimEnd('.', ',', ';', ':') + "."
        }
        return result
    }

    private fun PrefsManager.runCatchingApiKey(): String {
        return try {
            ApiConfigManager(appContext).getApiToken()
        } catch (_: Exception) {
            ""
        }
    }

    private fun PrefsManager.runCatchingApiBaseUrl(): String {
        return try {
            ApiConfigManager(appContext).getApiBaseUrl()
        } catch (_: Exception) {
            ""
        }
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
