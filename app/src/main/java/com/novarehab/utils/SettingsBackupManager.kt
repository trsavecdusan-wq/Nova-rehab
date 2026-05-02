package com.novarehab.utils

import android.content.Context
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class SettingsBackupManager(private val context: Context) {

    companion object {
        private const val SETTINGS_FILE = "novarehab-settings.json"
        private const val INFO_FILE = "PREBERI-NovaRehab.txt"
        private const val INFO_TEXT =
            "Ta mapa hrani nastavitve NovaRehab. Pusti jo v Prenosi/NovaRehab, da jih aplikacija po ponovni namestitvi lahko sama obnovi."
    }

    private val prefs = PrefsManager(context)
    private val apiConfig = ApiConfigManager(context)
    private val iconTextManager = IconTextManager(context)

    private val standardIconIds = listOf(
        "pomoc",
        "piti",
        "jesti",
        "bolecina",
        "kopalnica",
        "dobro",
        "slabo",
        "utrujena",
        "mraz",
        "vroce",
        "hvala",
        "pridi_sem",
        "pocakaj",
        "zdravilo",
        "telefon",
        "tv",
        "postelja",
        "okno",
        "vesela",
        "zalostna",
        "jezna",
        "strah",
        "tesnoba",
        "voda",
        "caj",
        "sok",
        "zajtrk",
        "kosilo",
        "prigrizek",
        "slabost",
        "pomoc_pridi",
        "pomoc_dvigni",
        "pomoc_polozaj",
        "objemi"
    )

    fun restoreIfAvailable(): Boolean {
        return try {
            val raw = readPublicFile("", SETTINGS_FILE)
                ?: readAppPrivateFile()
                ?: return false

            val json = JSONObject(raw)
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
            val json = exportJson()
            val text = json.toString(2)

            writePublicFile("", SETTINGS_FILE, text.toByteArray(Charsets.UTF_8), "application/json")
            writePublicFile("", INFO_FILE, INFO_TEXT.toByteArray(Charsets.UTF_8), "text/plain")

            backupMediaFolder("icons")
            backupMediaFolder("contacts")

            val appFile = File(context.getExternalFilesDir(null), "backup/novarehab-settings.json")
            appFile.parentFile?.mkdirs()
            appFile.writeText(text, Charsets.UTF_8)

            true
        } catch (_: Exception) {
            false
        }
    }

    private fun readAppPrivateFile(): String? {
        val file = File(context.getExternalFilesDir(null), "backup/novarehab-settings.json")
        return if (file.exists()) {
            runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
        } else {
            null
        }
    }

    private fun backupRoot(): File {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(downloads, "NovaRehab")
    }

    private fun backupMediaFolder(folderName: String) {
        val sourceDir = File(context.getExternalFilesDir(null), folderName)
        if (!sourceDir.exists()) return

        val targetDir = File(backupRoot(), folderName)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) targetDir.mkdirs()

        sourceDir.listFiles()?.forEach { source ->
            if (source.isFile) {
                val mimeType = if (source.extension.equals("png", ignoreCase = true)) "image/png" else "application/octet-stream"
                runCatching { writePublicFile(folderName, source.name, source.readBytes(), mimeType) }
            }
        }
    }

    private fun restoreMediaFolder(folderName: String) {
        val targetDir = File(context.getExternalFilesDir(null), folderName)
        targetDir.mkdirs()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listPublicFiles(folderName).forEach { publicFile ->
                runCatching {
                    context.contentResolver.openInputStream(publicFile.uri)?.use { input ->
                        File(targetDir, publicFile.name).outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        } else {
            val sourceDir = File(backupRoot(), folderName)
            if (!sourceDir.exists()) return

            sourceDir.listFiles()?.forEach { source ->
                if (source.isFile) {
                    runCatching {
                        source.copyTo(File(targetDir, source.name), overwrite = true)
                    }
                }
            }
        }
    }

    private fun writePublicFile(folderName: String, fileName: String, bytes: ByteArray, mimeType: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relativePath = publicRelativePath(folderName)
            findPublicFile(folderName, fileName)?.let { context.contentResolver.delete(it, null, null) }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Backup file could not be created")

            context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                output.write(bytes)
            } ?: throw IllegalStateException("Backup file could not be opened")

            val doneValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            context.contentResolver.update(uri, doneValues, null, null)
        } else {
            val targetDir = if (folderName.isBlank()) backupRoot() else File(backupRoot(), folderName)
            targetDir.mkdirs()
            File(targetDir, fileName).writeBytes(bytes)
        }
    }

    private fun readPublicFile(folderName: String, fileName: String): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = findPublicFile(folderName, fileName) ?: return null
            return context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }
        }

        val file = if (folderName.isBlank()) {
            File(backupRoot(), fileName)
        } else {
            File(File(backupRoot(), folderName), fileName)
        }

        return if (file.exists()) file.readText(Charsets.UTF_8) else null
    }

    private fun findPublicFile(folderName: String, fileName: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?"
        val args = arrayOf(fileName, publicRelativePath(folderName))

        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                return ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
            }
        }

        return null
    }

    private fun listPublicFiles(folderName: String): List<PublicBackupFile> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()

        val result = mutableListOf<PublicBackupFile>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME
        )
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH}=?"
        val args = arrayOf(publicRelativePath(folderName))

        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                result.add(
                    PublicBackupFile(
                        name = name,
                        uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                    )
                )
            }
        }

        return result
    }

    private fun publicRelativePath(folderName: String): String {
        val base = "${Environment.DIRECTORY_DOWNLOADS}/NovaRehab/"
        return if (folderName.isBlank()) base else "$base$folderName/"
    }

    private data class PublicBackupFile(
        val name: String,
        val uri: Uri
    )

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
            .put("iconTexts", iconTextManager.exportTexts(standardIconIds))
            .put("submenuPrompts", iconTextManager.exportSubmenuPrompts(standardIconIds))
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

        json.optJSONObject("iconTexts")?.let { iconTextManager.importTexts(it) }
        json.optJSONObject("submenuPrompts")?.let { iconTextManager.importSubmenuPrompts(it) }

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
