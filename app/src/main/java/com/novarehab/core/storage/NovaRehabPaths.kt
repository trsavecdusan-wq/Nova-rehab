package com.novarehab.core.storage

import android.content.Context
import android.os.Environment
import java.io.File

class NovaRehabPaths(context: Context) {
    private val appContext = context.applicationContext

    val rootDir: File by lazy {
        File(Environment.getExternalStorageDirectory(), "NovaRehab").also { it.mkdirs() }
    }

    val configDir: File by lazy {
        File(rootDir, "config").also { it.mkdirs() }
    }

    val customIconsDir: File by lazy {
        File(rootDir, "custom_icons").also { it.mkdirs() }
    }

    val iconArchiveDir: File by lazy {
        File(rootDir, "icon_archive").also { it.mkdirs() }
    }

    val galleryDir: File by lazy {
        File(rootDir, "gallery").also { it.mkdirs() }
    }

    val galleryCameraDir: File by lazy {
        File(galleryDir, "camera").also { it.mkdirs() }
    }

    val contactsDir: File by lazy {
        File(rootDir, "contacts").also { it.mkdirs() }
    }

    val backupDir: File by lazy {
        File(configDir, "backup").also { it.mkdirs() }
    }

    val communicationCustomFile: File by lazy {
        File(configDir, "communication_custom.json")
    }

    val communicationCustomBackupFile: File by lazy {
        File(configDir, "communication_custom.backup.json")
    }

    val apiConfigFile: File by lazy {
        File(configDir, "api_config.json")
    }

    val settingsBackupFile: File by lazy {
        File(backupDir, "novarehab-settings.json")
    }

    fun customIconFile(iconId: String): File = File(customIconsDir, "$iconId.png")

    fun archivedIconFile(iconId: String): File = File(iconArchiveDir, "$iconId.png")

    fun contactImageFile(index: Int): File = File(contactsDir, "contact_${index + 1}.png")
}
