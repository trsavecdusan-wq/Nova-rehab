package com.novarehab.utils

import android.content.Context
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class SettingsBackupManager(private val context: Context) {

    private val prefs = PrefsManager(context)
    private val apiConfig = ApiConfigManager(context)

    fun restoreIfAvailable(): Boolean {
        val file = backupFile()
        if (!file.exists()) return false

        return try {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            restore(json)
            restoreMediaFolder("icons")
            restoreMediaFolder("contacts")
            true
        } catch (_: Exception) {
            false
        }
    }

    fun backupNow(): Boolean {
        return try {
            val file = backupFile()
            file.parentFile?.mkdirs()
            file.writeText(exportJson().toString(2), Charsets.UTF_8)
            backupMediaFolder("icons")
            backupMediaFolder("contacts")

            val appFile = File(context.getExternalFilesDir(null), "backup/novarehab-settings.json")
            appFile.parentFile?.mkdirs()
            appFile.writeText(exportJson().toString(2), Charsets.UTF_8)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun backupFile(): File {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(downloads, "NovaRehab/novarehab-settings.json")
    }

    private fun backupRoot(): File {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(downloads, "NovaRehab")
    }

    private fun backupMediaFolder(folderName: String) {
        val sourceDir = File(context.getExternalFilesDir(null), folderName)
        if (!sourceDir.exists()) return

        val targetDir = File(backupRoot(), folderName)
        targetDir.mkdirs()

        sourceDir.listFiles()?.forEach { source ->
            if (source.isFile) {
                runCatching {
                    source.copyTo(File(targetDir, source.name), overwrite = true)
                }
            }
        }
    }

    private fun restoreMediaFolder(folderName: String) {
        val sourceDir = File(backupRoot(), folderName)
        if (!sourceDir.exists()) return

        val targetDir = File(context.getExternalFilesDir(null), folderName)
        targetDir.mkdirs()

        sourceDir.listFiles()?.forEach { source ->
            if (source.isFile) {
                runCatching {
                    source.copyTo(File(targetDir, source.name), overwrite = true)
                }
            }
        }
    }

    private fun exportJson(): JSONObject {
        return JSONObject()
            .put("version", 1)
            .put("patientName", prefs.getPatientName())
            .put("defaultSpeechLanguage", prefs.getDefaultSpeechLanguage())
            .put("patientLanguage1", prefs.getPatientLanguage1())
            .put("patientLanguage2", prefs.getPatientLanguage2())
            .put("autoLanguageEnabled", prefs.isAutoLanguageEnabled())
            .put("commIconsPerPage", prefs.getCommIconsPerPage())
            .put("commSubmenuTimeoutSeconds", prefs.getCommSubmenuTimeoutSeconds())
            .put("ttsVoice", prefs.getTtsVoice())
            .put("ttsSpeed", prefs.getTtsSpeed().toDouble())
            .put("ttsVolume", prefs.getTtsVolume().toDouble())
            .put("navigationEnabled", prefs.isNavigationEnabled())
            .put("homeAddress", prefs.getHomeAddress())
            .put("kioskReturnMinutes", prefs.getKioskReturnMinutes())
            .put("guestLanguageReturnMinutes", prefs.getGuestLanguageReturnMinutes())
            .put("serverIp", prefs.getServerIp())
            .put("serverPort", prefs.getServerPort())
            .put("gmailUser", prefs.getGmailUser())
            .put("gmailAppPassword", prefs.getGmailAppPassword())
            .put("reportMail1", prefs.getReportMail1())
            .put("reportMail2", prefs.getReportMail2())
            .put("reportMail1Enabled", prefs.isReportMail1Enabled())
            .put("reportMail2Enabled", prefs.isReportMail2Enabled())
            .put("reportHour", prefs.getReportHour())
            .put("apiBaseUrl", apiConfig.getApiBaseUrl())
            .put("apiToken", apiConfig.getApiToken())
            .put("radioStations", JSONArray(prefs.getRadioStations().map {
                JSONObject()
                    .put("name", it.name)
                    .put("url", it.url)
            }))
            .put("contacts", JSONArray(prefs.getContacts().mapIndexed { index, contact ->
                JSONObject()
                    .put("name", contact.name)
                    .put("phone", contact.phone)
                    .put("emoji", contact.emoji)
                    .put("language", contact.language)
                    .put("incomingCalls", prefs.isContactIncomingCallEnabled(index))
                    .put("outgoingCalls", prefs.isContactOutgoingCallEnabled(index))
            }))
            .put("customCommIcons", JSONArray(prefs.getCustomCommIcons().map {
                JSONObject()
                    .put("id", it.id)
                    .put("title", it.title)
                    .put("text", it.text)
                    .put("language", it.language)
            }))
    }

    private fun restore(json: JSONObject) {
        prefs.savePatientName(json.optString("patientName", prefs.getPatientName()))
        prefs.saveDefaultSpeechLanguage(json.optString("defaultSpeechLanguage", prefs.getDefaultSpeechLanguage()))
        prefs.savePatientLanguage1(json.optString("patientLanguage1", prefs.getPatientLanguage1()))
        prefs.savePatientLanguage2(json.optString("patientLanguage2", prefs.getPatientLanguage2()))
        prefs.saveAutoLanguageEnabled(json.optBoolean("autoLanguageEnabled", prefs.isAutoLanguageEnabled()))
        prefs.saveCommIconsPerPage(json.optInt("commIconsPerPage", prefs.getCommIconsPerPage()))
        prefs.saveCommSubmenuTimeoutSeconds(json.optLong("commSubmenuTimeoutSeconds", prefs.getCommSubmenuTimeoutSeconds()))
        prefs.saveTtsVoice(json.optString("ttsVoice", prefs.getTtsVoice()))
        prefs.saveTtsSpeed(json.optDouble("ttsSpeed", prefs.getTtsSpeed().toDouble()).toFloat())
        prefs.saveTtsVolume(json.optDouble("ttsVolume", prefs.getTtsVolume().toDouble()).toFloat())
        prefs.saveNavigationEnabled(json.optBoolean("navigationEnabled", prefs.isNavigationEnabled()))
        prefs.saveHomeAddress(json.optString("homeAddress", prefs.getHomeAddress()))
        prefs.saveKioskReturnMinutes(json.optLong("kioskReturnMinutes", prefs.getKioskReturnMinutes()))
        prefs.saveGuestLanguageReturnMinutes(json.optLong("guestLanguageReturnMinutes", prefs.getGuestLanguageReturnMinutes()))
        prefs.saveServerIp(json.optString("serverIp", prefs.getServerIp()))
        prefs.saveServerPort(json.optString("serverPort", prefs.getServerPort()))
        prefs.saveGmailUser(json.optString("gmailUser", prefs.getGmailUser()))
        prefs.saveGmailAppPassword(json.optString("gmailAppPassword", prefs.getGmailAppPassword()))
        prefs.saveReportMail1(json.optString("reportMail1", prefs.getReportMail1()))
        prefs.saveReportMail2(json.optString("reportMail2", prefs.getReportMail2()))
        prefs.saveReportMail1Enabled(json.optBoolean("reportMail1Enabled", prefs.isReportMail1Enabled()))
        prefs.saveReportMail2Enabled(json.optBoolean("reportMail2Enabled", prefs.isReportMail2Enabled()))
        prefs.saveReportHour(json.optInt("reportHour", prefs.getReportHour()))
        apiConfig.saveApiBaseUrl(json.optString("apiBaseUrl", apiConfig.getApiBaseUrl()))
        apiConfig.saveApiToken(json.optString("apiToken", apiConfig.getApiToken()))

        json.optJSONArray("radioStations")?.let { array ->
            val stations = (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                RadioStation(item.optString("name"), item.optString("url"))
            }.filter { it.name.isNotBlank() && it.url.isNotBlank() }

            if (stations.isNotEmpty()) prefs.saveRadioStations(stations)
        }

        json.optJSONArray("contacts")?.let { array ->
            val contacts = (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null

                prefs.saveContactIncomingCallEnabled(index, item.optBoolean("incomingCalls", true))
                prefs.saveContactOutgoingCallEnabled(index, item.optBoolean("outgoingCalls", true))

                Contact(
                    name = item.optString("name"),
                    phone = item.optString("phone"),
                    emoji = item.optString("emoji"),
                    language = item.optString("language", "sl")
                )
            }

            if (contacts.isNotEmpty()) prefs.saveContacts(contacts)
        }

        json.optJSONArray("customCommIcons")?.let { array ->
            val icons = (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null

                CustomCommIcon(
                    id = item.optString("id"),
                    title = item.optString("title"),
                    text = item.optString("text"),
                    language = item.optString("language", "sl")
                )
            }.filter { it.id.isNotBlank() }

            prefs.saveCustomCommIcons(icons)
        }
    }
}
