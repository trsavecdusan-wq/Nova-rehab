package com.novarehab.utils

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.novarehab.core.storage.NovaRehabPaths
import com.novarehab.learning.LearningProfileManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class ConfigImportMode {
    FULL,
    ICONS,
    CONTACTS,
    STATISTICS
}

data class ConfigImportPreview(
    val hasSettings: Boolean,
    val hasIcons: Boolean,
    val hasContacts: Boolean,
    val hasStatistics: Boolean,
    val entryCount: Int,
    val fileNames: List<String>
)

class ConfigExportImportManager(context: Context) {
    private val appContext = context.applicationContext
    private val paths = NovaRehabPaths(appContext)
    private val prefs = PrefsManager(appContext)
    private val backupManager = SettingsBackupManager(appContext)
    private val learningProfile = LearningProfileManager(appContext)

    fun defaultExportName(): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return "NovaRehab_Export_$date.zip"
    }

    fun defaultStatisticsName(): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return "NovaRehab_Statistics_$date.json"
    }

    fun exportToUri(uri: Uri): Result<Long> {
        return runCatching {
            appContext.contentResolver.openOutputStream(uri, "w")?.use { output ->
                ZipOutputStream(output).use { zip ->
                    writeExportBundle(zip)
                }
            } ?: error("Izvozne datoteke ni bilo mogoče odpreti.")
            val size = appContext.contentResolver.openFileDescriptor(uri, "r")?.statSize ?: 0L
            prefs.saveLastConfigExportAt(System.currentTimeMillis())
            prefs.saveLastConfigExportSize(size)
            size
        }
    }

    fun createShareZip(): File {
        val exportDir = File(appContext.cacheDir, "exports").also { it.mkdirs() }
        val target = File(exportDir, defaultExportName())
        FileOutputStream(target).use { output ->
            ZipOutputStream(output).use { zip ->
                writeExportBundle(zip)
            }
        }
        prefs.saveLastConfigExportAt(System.currentTimeMillis())
        prefs.saveLastConfigExportSize(target.length())
        return target
    }

    fun exportStatisticsToUri(uri: Uri): Result<Long> {
        return runCatching {
            val json = buildStatisticsJson().toString(2).toByteArray(Charsets.UTF_8)
            appContext.contentResolver.openOutputStream(uri, "w")?.use { it.write(json) }
                ?: error("Datoteke statistike ni bilo mogoče zapisati.")
            json.size.toLong()
        }
    }

    fun shareUriFor(file: File): Uri {
        return FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
    }

    fun inspectImportBundle(uri: Uri): ConfigImportPreview {
        val fileNames = mutableListOf<String>()
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory) fileNames += entry.name
                    zip.closeEntry()
                }
            }
        } ?: error("Uvozne datoteke ni bilo mogoče prebrati.")

        return ConfigImportPreview(
            hasSettings = fileNames.any { it == "settings/settings.json" },
            hasIcons = fileNames.any { it.startsWith("custom_icons/") || it.startsWith("icon_archive/") || it.contains("communication_custom") },
            hasContacts = fileNames.any { it.startsWith("contacts/") } || fileNames.any { it == "settings/settings.json" },
            hasStatistics = fileNames.any { it == "learning/learning_profile.json" || it == "stats/nova_stats.db" || it == "analytics/stats-summary.json" },
            entryCount = fileNames.size,
            fileNames = fileNames
        )
    }

    fun importFromUri(uri: Uri, mode: ConfigImportMode): Result<Unit> {
        val rollbackFile = File(File(appContext.cacheDir, "exports").also { it.mkdirs() }, "rollback_${System.currentTimeMillis()}.zip")
        return runCatching {
            FileOutputStream(rollbackFile).use { output ->
                ZipOutputStream(output).use { zip -> writeExportBundle(zip) }
            }

            backupManager.backupNow()
            val tempDir = extractToTempDir(uri)
            try {
                importFromDirectory(tempDir, mode)
                prefs.saveLastConfigImportAt(System.currentTimeMillis())
                backupManager.backupNow()
            } catch (e: Exception) {
                val rollbackDir = extractZipFile(rollbackFile)
                importFromDirectory(rollbackDir, ConfigImportMode.FULL)
                throw e
            }
        }
    }

    private fun writeExportBundle(zip: ZipOutputStream) {
        addJsonEntry(zip, "manifest.json", buildManifest())
        addJsonEntry(zip, "settings/settings.json", backupManager.exportSettingsJson())
        addJsonEntry(zip, "learning/learning_profile.json", learningProfile.exportJson())
        addJsonEntry(zip, "analytics/stats-summary.json", buildStatisticsJson())

        addFileIfExists(zip, appContext.getDatabasePath("nova_stats.db"), "stats/nova_stats.db")
        addFileIfExists(zip, paths.communicationCustomFile, "config/communication_custom.json")
        addFileIfExists(zip, paths.communicationCustomBackupFile, "config/communication_custom.backup.json")
        addFileIfExists(zip, paths.apiConfigFile, "config/api_config.json")
        addFileIfExists(zip, paths.settingsBackupFile, "config/backups/novarehab-settings.json")
        addFileIfExists(zip, File(paths.rootDir, "music_history.json"), "analytics/music_history.json")

        addDirectory(zip, paths.customIconsDir, "custom_icons")
        addDirectory(zip, paths.iconArchiveDir, "icon_archive")
        addDirectory(zip, paths.contactsDir, "contacts")
    }

    private fun buildManifest(): JSONObject {
        return JSONObject()
            .put("type", "novarehab-export")
            .put("createdAt", System.currentTimeMillis())
            .put("profileType", "patient-profile")
            .put("sections", JSONArray().put("settings").put("icons").put("contacts").put("statistics").put("learning"))
    }

    private fun buildStatisticsJson(): JSONObject {
        val stats = StatsManager(appContext)
        val reports = JSONArray()
        stats.getLast30Days().forEach { report ->
            reports.put(
                JSONObject()
                    .put("date", report.date)
                    .put("appMinutes", report.appMinutes)
                    .put("radioMinutes", report.radioMinutes)
                    .put("commIconCount", report.commIconCount)
                    .put("navCount", report.navCount)
                    .put("navMinutes", report.navMinutes)
                    .put("callCount", report.callCount)
                    .put("musicMinutes", report.musicMinutes)
                    .put("langChanges", report.langChanges)
                    .put("ttsErrors", report.ttsErrors)
            )
        }
        stats.close()
        return JSONObject().put("reports", reports)
    }

    private fun importFromDirectory(tempDir: File, mode: ConfigImportMode) {
        val settingsFile = File(tempDir, "settings/settings.json")
        val settingsJson = if (settingsFile.exists()) JSONObject(settingsFile.readText(Charsets.UTF_8)) else null

        when (mode) {
            ConfigImportMode.FULL -> {
                settingsJson?.let { backupManager.restoreJson(it) }
                restoreLearning(tempDir)
                restoreStatsDb(tempDir)
                restoreFiles(tempDir, includeIcons = true, includeContacts = true)
            }
            ConfigImportMode.ICONS -> {
                settingsJson?.optJSONArray("customCommIcons")?.let { customIcons ->
                    backupManager.restoreJson(JSONObject().put("customCommIcons", customIcons))
                }
                restoreFiles(tempDir, includeIcons = true, includeContacts = false)
            }
            ConfigImportMode.CONTACTS -> {
                settingsJson?.optJSONArray("contacts")?.let { contacts ->
                    backupManager.restoreJson(JSONObject().put("contacts", contacts))
                }
                restoreFiles(tempDir, includeIcons = false, includeContacts = true)
            }
            ConfigImportMode.STATISTICS -> {
                restoreLearning(tempDir)
                restoreStatsDb(tempDir)
            }
        }
    }

    private fun restoreLearning(tempDir: File) {
        val file = File(tempDir, "learning/learning_profile.json")
        if (file.exists()) {
            learningProfile.importJson(JSONObject(file.readText(Charsets.UTF_8)))
        }
    }

    private fun restoreStatsDb(tempDir: File) {
        val source = File(tempDir, "stats/nova_stats.db")
        if (!source.exists()) return
        val target = appContext.getDatabasePath("nova_stats.db")
        StatsManager(appContext).close()
        target.parentFile?.mkdirs()
        source.copyTo(target, overwrite = true)
    }

    private fun restoreFiles(tempDir: File, includeIcons: Boolean, includeContacts: Boolean) {
        if (includeIcons) {
            copyFileIfExists(File(tempDir, "config/communication_custom.json"), paths.communicationCustomFile)
            copyFileIfExists(File(tempDir, "config/communication_custom.backup.json"), paths.communicationCustomBackupFile)
            copyDirectory(File(tempDir, "custom_icons"), paths.customIconsDir)
            copyDirectory(File(tempDir, "icon_archive"), paths.iconArchiveDir)
        }
        if (includeContacts) {
            copyDirectory(File(tempDir, "contacts"), paths.contactsDir)
        }
    }

    private fun addJsonEntry(zip: ZipOutputStream, entryName: String, json: JSONObject) {
        val bytes = json.toString(2).toByteArray(Charsets.UTF_8)
        zip.putNextEntry(ZipEntry(entryName))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun addFileIfExists(zip: ZipOutputStream, file: File, entryName: String) {
        if (!file.exists() || !file.isFile) return
        zip.putNextEntry(ZipEntry(entryName))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun addDirectory(zip: ZipOutputStream, sourceDir: File, entryPrefix: String) {
        if (!sourceDir.exists() || !sourceDir.isDirectory) return
        sourceDir.walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                val relative = file.relativeTo(sourceDir).invariantSeparatorsPath
                zip.putNextEntry(ZipEntry("$entryPrefix/$relative"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
    }

    private fun extractToTempDir(uri: Uri): File {
        val targetDir = File(appContext.cacheDir, "import_${System.currentTimeMillis()}").also { it.mkdirs() }
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip -> extract(zip, targetDir) }
        } ?: error("Uvozne datoteke ni bilo mogoče odpreti.")
        return targetDir
    }

    private fun extractZipFile(file: File): File {
        val targetDir = File(appContext.cacheDir, "rollback_${System.currentTimeMillis()}").also { it.mkdirs() }
        ZipInputStream(file.inputStream()).use { zip -> extract(zip, targetDir) }
        return targetDir
    }

    private fun extract(zip: ZipInputStream, targetDir: File) {
        while (true) {
            val entry = zip.nextEntry ?: break
            val target = File(targetDir, entry.name)
            if (entry.isDirectory) {
                target.mkdirs()
            } else {
                target.parentFile?.mkdirs()
                target.outputStream().use { zip.copyTo(it) }
            }
            zip.closeEntry()
        }
    }

    private fun copyDirectory(sourceDir: File, targetDir: File) {
        if (!sourceDir.exists() || !sourceDir.isDirectory) return
        sourceDir.walkTopDown().forEach { source ->
            val target = File(targetDir, source.relativeTo(sourceDir).invariantSeparatorsPath)
            if (source.isDirectory) {
                target.mkdirs()
            } else {
                target.parentFile?.mkdirs()
                source.copyTo(target, overwrite = true)
            }
        }
    }

    private fun copyFileIfExists(source: File, target: File) {
        if (!source.exists() || !source.isFile) return
        target.parentFile?.mkdirs()
        source.copyTo(target, overwrite = true)
    }
}