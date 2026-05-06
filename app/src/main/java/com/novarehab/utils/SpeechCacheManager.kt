package com.novarehab.utils

import android.content.Context
import com.novarehab.core.storage.NovaRehabPaths
import java.io.File
import java.security.MessageDigest

class SpeechCacheManager(context: Context) {
    private val paths = NovaRehabPaths(context.applicationContext)
    private val cacheDir: File = paths.speechCacheDir

    fun getCachedAudio(
        text: String,
        language: String,
        voice: String,
        speed: Float,
        style: String,
        model: String,
        format: String
    ): File? {
        val file = cacheFile(text, language, voice, speed, style, model, format)
        return file.takeIf { it.exists() && it.length() > 1024 }
    }

    fun saveCachedAudio(
        text: String,
        language: String,
        voice: String,
        speed: Float,
        style: String,
        model: String,
        format: String,
        bytes: ByteArray
    ): File {
        val file = cacheFile(text, language, voice, speed, style, model, format)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        return file
    }

    fun preloadCommonPhrases(): List<String> = listOf(
        "Želim vodo.",
        "Želim čaj.",
        "Želim sok.",
        "Potrebujem pomoč.",
        "Želim na WC.",
        "Počakaj prosim.",
        "Ne razumem.",
        "Prosim ponovi.",
        "Da.",
        "Ne."
    )

    fun clearCache() {
        cacheDir.listFiles()?.forEach { file ->
            if (file.isFile) file.delete()
        }
    }

    fun cacheSize(): Long {
        return cacheDir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    fun deleteOldCache(maxAgeDays: Int = 14, maxBytes: Long = 150L * 1024L * 1024L) {
        val now = System.currentTimeMillis()
        val maxAgeMs = maxAgeDays.coerceAtLeast(1) * 24L * 60L * 60L * 1000L
        val files = cacheDir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() }.orEmpty()

        files.filter { now - it.lastModified() > maxAgeMs }.forEach { it.delete() }

        var currentSize = cacheSize()
        if (currentSize <= maxBytes) return

        files.sortedBy { it.lastModified() }.forEach { file ->
            if (currentSize <= maxBytes) return
            val length = file.length()
            if (file.delete()) {
                currentSize -= length
            }
        }
    }

    private fun cacheFile(
        text: String,
        language: String,
        voice: String,
        speed: Float,
        style: String,
        model: String,
        format: String
    ): File {
        val safeFormat = format.lowercase().ifBlank { "mp3" }
        val key = listOf(text, language, voice, speed.toString(), style, model, safeFormat).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(cacheDir, "$digest.$safeFormat")
    }
}
