package com.novarehab.communication.data

import android.content.Context
import com.novarehab.core.storage.NovaRehabPaths
import com.novarehab.utils.CustomCommIcon
import org.json.JSONArray
import org.json.JSONObject

class PersonalIconBankManager(context: Context) {
    private val appContext = context.applicationContext
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
                            imagePath = item.optString("imagePath")
                                .trim()
                                .ifBlank { resolveDefaultImagePath(id) },
                            enabled = item.optBoolean("enabled", true),
                            pinnedMain = item.optBoolean("pinnedMain", false),
                            pinnedVideo = item.optBoolean("pinnedVideo", false),
                            showOnMain = item.optBoolean("showOnMain", true),
                            children = item.optJSONArray("children").toStringList()
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
                    .put("imagePath", item.imagePath.ifBlank { resolveDefaultImagePath(item.id) })
                    .put("enabled", item.enabled)
                    .put("pinnedMain", item.pinnedMain)
                    .put("pinnedVideo", item.pinnedVideo)
                    .put("showOnMain", item.showOnMain)
                    .put("children", JSONArray(item.children))
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

    private fun resolveDefaultImagePath(iconId: String): String {
        val file = paths.customIconFile(iconId)
        return if (file.exists()) file.absolutePath else ""
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            optString(index).trim().takeIf { it.isNotBlank() }
        }
    }
}


