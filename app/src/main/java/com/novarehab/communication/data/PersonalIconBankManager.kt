package com.novarehab.communication.data

import android.content.Context
import com.novarehab.core.storage.NovaRehabPaths
import com.novarehab.utils.CustomCommIcon
import org.json.JSONArray
import org.json.JSONObject

class PersonalIconBankManager(context: Context) {
    private val paths = NovaRehabPaths(context)

    fun load(): List<CustomCommIcon> {
        val source = when {
            paths.communicationCustomFile.exists() -> paths.communicationCustomFile
            paths.communicationCustomBackupFile.exists() -> paths.communicationCustomBackupFile
            else -> null
        } ?: return emptyList()

        return runCatching {
            val root = JSONObject(source.readText(Charsets.UTF_8))
            val array = root.optJSONArray("items") ?: JSONArray()
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { item ->
                    val id = item.optString("id").trim()
                    if (id.isBlank()) {
                        null
                    } else {
                        CustomCommIcon(
                            id = id,
                            title = item.optString("title").trim(),
                            text = item.optString("text").trim(),
                            language = item.optString("language", "sl").trim().ifBlank { "sl" },
                            imagePath = item.optString("imagePath").trim(),
                            enabled = item.optBoolean("enabled", true),
                            pinnedMain = item.optBoolean("pinnedMain", false),
                            pinnedVideo = item.optBoolean("pinnedVideo", false)
                        )
                    }
                }
            }
        }.getOrElse { emptyList() }
    }

    fun save(items: List<CustomCommIcon>) {
        val normalized = items
            .filter { it.id.isNotBlank() }
            .sortedBy { it.id }

        backupCurrent()

        val array = JSONArray()
        normalized.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("text", item.text)
                    .put("language", item.language)
                    .put("imagePath", item.imagePath)
                    .put("enabled", item.enabled)
                    .put("pinnedMain", item.pinnedMain)
                    .put("pinnedVideo", item.pinnedVideo)
            )
        }

        paths.communicationCustomFile.parentFile?.mkdirs()
        paths.communicationCustomFile.writeText(
            JSONObject()
                .put("version", 1)
                .put("items", array)
                .toString(2),
            Charsets.UTF_8
        )
    }

    fun backupNow() {
        backupCurrent(force = true)
    }

    fun restoreBackup(): Boolean {
        if (!paths.communicationCustomBackupFile.exists()) return false
        paths.communicationCustomFile.parentFile?.mkdirs()
        paths.communicationCustomBackupFile.copyTo(paths.communicationCustomFile, overwrite = true)
        return true
    }

    private fun backupCurrent(force: Boolean = false) {
        if (!paths.communicationCustomFile.exists()) {
            if (force && !paths.communicationCustomBackupFile.exists()) {
                paths.communicationCustomBackupFile.parentFile?.mkdirs()
                paths.communicationCustomBackupFile.writeText(
                    JSONObject().put("version", 1).put("items", JSONArray()).toString(2),
                    Charsets.UTF_8
                )
            }
            return
        }

        paths.communicationCustomBackupFile.parentFile?.mkdirs()
        paths.communicationCustomFile.copyTo(paths.communicationCustomBackupFile, overwrite = true)
    }
}
