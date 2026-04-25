package com.novarehab.utils

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class OpenAiTranslateManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("translation_cache", Context.MODE_PRIVATE)

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun translate(
        text: String,
        targetLanguage: String,
        apiKey: String,
        onResult: (String) -> Unit
    ) {
        val cleanText = text.trim()

        if (cleanText.isEmpty()) {
            onResult("")
            return
        }

        if (targetLanguage == "sl") {
            onResult(cleanText)
            return
        }

        if (apiKey.isBlank()) {
            onResult(cleanText)
            return
        }

        val cacheKey = "${targetLanguage}_${cleanText.hashCode()}"

        prefs.getString(cacheKey, null)?.let { cached ->
            if (cached.isNotBlank()) {
                onResult(cached)
                return
            }
        }

        Thread {
            val translated = try {
                translateWithOpenAi(cleanText, targetLanguage, apiKey)
            } catch (_: Exception) {
                null
            }

            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (!translated.isNullOrBlank()) {
                    prefs.edit().putString(cacheKey, translated).apply()
                    onResult(translated)
                } else {
                    onResult(cleanText)
                }
            }
        }.start()
    }

    private fun translateWithOpenAi(
        text: String,
        targetLanguage: String,
        apiKey: String
    ): String? {
        val targetName = when (targetLanguage) {
            "uk", "ua" -> "Ukrainian"
            "en" -> "English"
            "de" -> "German"
            "hr" -> "Croatian"
            "sr" -> "Serbian"
            else -> "Slovenian"
        }

        val messages = JSONArray()
            .put(
                JSONObject()
                    .put("role", "system")
                    .put(
                        "content",
                        "Translate short patient communication phrases. Return only the translated phrase. Keep it natural, clear, polite, and in first person when appropriate. Do not add explanations."
                    )
            )
            .put(
                JSONObject()
                    .put("role", "user")
                    .put("content", "Target language: $targetName\nText: $text")
            )

        val bodyJson = JSONObject()
            .put("model", "gpt-4o-mini")
            .put("temperature", 0.1)
            .put("messages", messages)

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null

            val raw = response.body?.string() ?: return null
            val json = JSONObject(raw)

            return json
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
        }
    }
}
