package com.novarehab.core.config

import android.content.Context
import android.os.Environment
import com.novarehab.utils.ApiConfigManager
import org.json.JSONObject
import java.io.File

class ApiConfigImportManager(context: Context) {
    private val appContext = context.applicationContext
    private val apiConfig = ApiConfigManager(appContext)

    fun importIfAvailable(): ImportResult {
        val file = File(
            Environment.getExternalStorageDirectory(),
            "NovaRehab/config/api_config.json"
        )

        if (!file.exists()) return ImportResult.NotFound

        return try {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            val baseUrl = json.optString("apiBaseUrl").trim()
            val token = json.optString("apiToken").trim()
            val provider = json.optString("selectedProvider", "openai").trim()

            if (baseUrl.isBlank() || token.isBlank()) {
                ImportResult.Invalid
            } else {
                apiConfig.saveApiBaseUrl(baseUrl)
                apiConfig.saveApiToken(token)
                apiConfig.saveSelectedProvider(provider.ifBlank { "openai" })
                ImportResult.Imported(file.absolutePath)
            }
        } catch (_: Exception) {
            ImportResult.Invalid
        }
    }

    sealed class ImportResult {
        object NotFound : ImportResult()
        object Invalid : ImportResult()
        data class Imported(val path: String) : ImportResult()
    }
}
