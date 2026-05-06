package com.novarehab.utils

import android.content.Context
import com.novarehab.core.storage.NovaRehabPaths
import java.io.File

data class MusicImportProgress(
    val current: Int,
    val total: Int,
    val fileName: String
)

data class MusicImportResult(
    val copied: Int,
    val skipped: Int,
    val duplicates: Int,
    val total: Int,
    val message: String? = null
)

class UsbMusicImportManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = PrefsManager(appContext)
    private val paths = NovaRehabPaths(appContext)

    private val supportedExtensions = setOf("mp3", "wav", "m4a", "aac", "ogg", "flac")
    private val ignoredDirectories = setOf(
        "android",
        "dcim",
        "movies",
        "pictures",
        "download",
        "system volume information",
        "lost.dir",
        ".trash-1000",
        "trash"
    )

    fun findReadableUsbRoots(): List<File> {
        val candidates = mutableListOf<File>()
        candidates += listOf(
            File("/storage"),
            File("/mnt/media_rw"),
            File("/mnt/usb"),
            File("/storage/usb"),
            File("/storage/usb0")
        )

        return candidates
            .flatMap { root ->
                when {
                    !root.exists() || !root.isDirectory -> emptyList()
                    root.name in listOf("storage", "media_rw") -> root.listFiles()?.filter { it.isDirectory } ?: emptyList()
                    else -> listOf(root)
                }
            }
            .filter { file ->
                file.exists() &&
                    file.isDirectory &&
                    file.canRead() &&
                    !file.absolutePath.contains("/emulated/") &&
                    !file.absolutePath.contains("/self/")
            }
            .distinctBy { it.absolutePath }
    }

    fun resolveTargetDir(): File {
        return when (prefs.getUsbMusicImportTarget()) {
            "sd" -> paths.removableMusicDir ?: paths.musicDir
            "internal" -> paths.musicDir
            else -> paths.removableMusicDir ?: paths.musicDir
        }.also { it.mkdirs() }
    }

    fun scanMusicFiles(root: File): List<File> {
        return root.walkTopDown()
            .onEnter { dir -> dir.name.lowercase() !in ignoredDirectories }
            .filter { file ->
                file.isFile &&
                    file.canRead() &&
                    file.extension.lowercase() in supportedExtensions
            }
            .sortedBy { it.name.lowercase() }
            .toList()
    }

    fun importFromUsb(
        usbRoot: File,
        onProgress: (MusicImportProgress) -> Unit
    ): MusicImportResult {
        if (!prefs.isUsbMusicImportEnabled()) {
            return MusicImportResult(0, 0, 0, 0, "USB uvoz glasbe je izklopljen.")
        }

        val targetDir = resolveTargetDir()
        val files = scanMusicFiles(usbRoot)
        if (files.isEmpty()) {
            return MusicImportResult(0, 0, 0, 0, "Ni najdenih glasbenih datotek.")
        }

        var copied = 0
        var skipped = 0
        var duplicates = 0
        val allowOverwrite = prefs.getUsbMusicOverwriteDifferent()

        files.forEachIndexed { index, source ->
            if (!usbRoot.exists()) {
                return MusicImportResult(copied, skipped, duplicates, files.size, "USB ključek ni več priklopljen.")
            }

            onProgress(MusicImportProgress(index + 1, files.size, source.name))

            val target = File(targetDir, source.name)
            if (target.exists()) {
                if (target.length() == source.length()) {
                    skipped++
                    return@forEachIndexed
                }

                val destination = if (allowOverwrite) target else uniqueDuplicate(targetDir, source.name).also { duplicates++ }
                val error = copySafely(source, destination)
                if (error == null) copied++ else return MusicImportResult(copied, skipped, duplicates, files.size, error)
            } else {
                val error = copySafely(source, target)
                if (error == null) copied++ else return MusicImportResult(copied, skipped, duplicates, files.size, error)
            }
        }

        return MusicImportResult(copied, skipped, duplicates, files.size, null)
    }

    private fun copySafely(source: File, target: File): String? {
        return try {
            target.parentFile?.mkdirs()
            if (target.parentFile?.usableSpace ?: 0L < source.length()) {
                return "Na kartici ni dovolj prostora."
            }
            source.copyTo(target, overwrite = true)
            if (!target.exists() || target.length() <= 0L) {
                "Kopiranje ni uspelo za ${source.name}."
            } else {
                null
            }
        } catch (e: Exception) {
            e.localizedMessage ?: "Neberljiva datoteka: ${source.name}"
        }
    }

    private fun uniqueDuplicate(targetDir: File, originalName: String): File {
        val original = File(originalName)
        val base = original.nameWithoutExtension
        val ext = original.extension
        var index = 1
        while (true) {
            val candidateName = if (ext.isBlank()) "$base ($index)" else "$base ($index).$ext"
            val candidate = File(targetDir, candidateName)
            if (!candidate.exists()) return candidate
            index++
        }
    }
}
