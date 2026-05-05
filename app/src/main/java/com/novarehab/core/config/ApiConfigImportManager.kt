package com.novarehab.core.config

import android.content.Context
import com.novarehab.core.storage.NovaRehabPaths
import com.novarehab.utils.ApiConfigManager
import org.json.JSONObject

class ApiConfigImportManager(context: Context) {
    private val appContext = context.applicationContext
    private val apiConfig = ApiConfigManager(appContext)
    private val paths = NovaRehabPaths(appContext)

    fun importIfAvailable(): ImportResult {
        val file = paths.apiConfigFile
        if (!file.exists()) return ImportResult.NotFound

        return try {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            val baseUrl = json.optString("apiBaseUrl")
                .ifBlank { json.optString("baseUrl") }
                .trim()
            val token = json.optString("apiToken")
                .ifBlank { json.optString("token") }
                .trim()
            val provider = json.optString("selectedProvider")
                .ifBlank { json.optString("provider", apiConfig.getSelectedProvider()) }
                .trim()

            if (baseUrl.isBlank() && token.isBlank() && provider.isBlank()) {
                ImportResult.Invalid
            } else {
                if (baseUrl.isNotBlank()) apiConfig.saveApiBaseUrl(baseUrl)
                if (token.isNotBlank()) apiConfig.saveApiToken(token)
                apiConfig.saveSelectedProvider(provider.ifBlank { apiConfig.getSelectedProvider() })
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
