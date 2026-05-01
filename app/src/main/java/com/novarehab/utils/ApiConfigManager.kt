package com.novarehab.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ApiConfigManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = createPrefs(appContext)

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun saveApiBaseUrl(baseUrl: String) {
        prefs.edit().putString(KEY_BASE_URL, normalizeBaseUrl(baseUrl)).commit()
        Log.d("NovaRehabApi", "API saved: baseUrl length=${getApiBaseUrl().length}, token length=${getApiToken().length}")
    }

    fun saveApiToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token.trim()).commit()
        Log.d("NovaRehabApi", "API saved: baseUrl length=${getApiBaseUrl().length}, token length=${getApiToken().length}")
    }

    fun getApiBaseUrl(): String {
        return prefs.getString(KEY_BASE_URL, "") ?: ""
    }

    fun getApiToken(): String {
        return prefs.getString(KEY_TOKEN, "") ?: ""
    }

    fun isApiConfigured(): Boolean {
        return getApiBaseUrl().isNotBlank() && getApiToken().isNotBlank()
    }

    fun clearApiConfig() {
        prefs.edit()
            .remove(KEY_BASE_URL)
            .remove(KEY_TOKEN)
            .apply()
    }

    fun buildEndpoint(path: String): String {
        val base = getApiBaseUrl().trim().trimEnd('/')
        val cleanPath = path.trim().trimStart('/')

        if (base.isBlank()) return cleanPath

        return if (base.endsWith("/v1")) {
            "$base/${cleanPath.removePrefix("v1/")}"
        } else {
            "$base/$cleanPath"
        }
    }

    fun getStatusText(): String {
        val baseUrl = getApiBaseUrl()
        val token = getApiToken()

        return when {
            baseUrl.isBlank() -> "Base URL manjka"
            token.isBlank() -> "Token manjka"
            else -> "API shranjen"
        }
    }

    fun testApiConnection(onResult: (Boolean, String) -> Unit) {
        val baseUrl = getApiBaseUrl()
        val token = getApiToken()

        if (baseUrl.isBlank()) {
            onResult(false, "API base URL manjka.")
            return
        }

        if (token.isBlank()) {
            onResult(false, "API key / token manjka.")
            return
        }

        Thread {
            val result = try {
                val request = Request.Builder()
                    .url(buildEndpoint("v1/models"))
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()

                http.newCall(request).execute().use { response ->
                    if (response.code in 200..399) {
                        true to "API povezava deluje"
                    } else {
                        false to "API povezava ni uspela (${response.code})."
                    }
                }
            } catch (e: Exception) {
                false to "API povezava ni uspela: ${e.localizedMessage ?: "neznana napaka"}"
            }

            android.os.Handler(android.os.Looper.getMainLooper()).post {
                onResult(result.first, result.second)
            }
        }.start()
    }

    private fun normalizeBaseUrl(baseUrl: String): String {
        return baseUrl.trim().trimEnd('/')
    }

    private fun createPrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    companion object {
        private const val PREFS_NAME = "nova_rehab_api_config"
        private const val KEY_BASE_URL = "api_base_url"
        private const val KEY_TOKEN = "api_token"
    }
}
