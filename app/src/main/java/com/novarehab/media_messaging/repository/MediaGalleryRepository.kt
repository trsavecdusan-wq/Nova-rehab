package com.novarehab.media_messaging.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.novarehab.core.storage.NovaRehabPaths
import com.novarehab.media_messaging.model.MediaMessage
import java.io.File
import java.io.FileOutputStream

class MediaGalleryRepository(context: Context) {
    private val appContext = context.applicationContext
    private val gson = Gson()
    private val paths = NovaRehabPaths(appContext)
    private val rootDir = paths.galleryDir
    private val imagesDir = File(rootDir, "images")
    private val thumbsDir = File(rootDir, "thumbnails")
    private val metadataFile = File(rootDir, "media-metadata.json")

    init {
        imagesDir.mkdirs()
        thumbsDir.mkdirs()
    }

    fun saveIncomingImage(
        messageId: String,
        senderId: String,
        senderName: String,
        base64Data: String,
        mimeType: String,
        receivedAt: Long,
        messageText: String = ""
    ): MediaMessage? {
        return runCatching {
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            val imageFile = File(imagesDir, "$messageId.jpg")
            imageFile.writeBytes(bytes)

            val thumbFile = File(thumbsDir, "$messageId.jpg")
            createThumbnail(bytes, thumbFile)

            val message = MediaMessage(
                messageId = messageId,
                senderId = senderId,
                senderName = senderName,
                targetContactId = "tablet",
                fileType = "image",
                mimeType = mimeType,
                receivedAt = receivedAt,
                localPath = imageFile.absolutePath,
                thumbnailPath = thumbFile.absolutePath,
                messageText = messageText,
                seen = false
            )

            val updated = (loadAll().filterNot { it.messageId == messageId } + message)
                .sortedByDescending { it.receivedAt }
            writeAll(updated)
            message
        }.getOrNull()
    }

    fun loadAll(): List<MediaMessage> {
        val storedItems = if (!metadataFile.exists()) {
            emptyList()
        } else {
            runCatching {
                val type = object : TypeToken<List<MediaMessage>>() {}.type
                gson.fromJson<List<MediaMessage>>(metadataFile.readText(Charsets.UTF_8), type).orEmpty()
            }.getOrElse { emptyList() }
        }

        val scannedItems = scanGalleryFiles()
        if (storedItems.isEmpty()) return scannedItems
        if (scannedItems.isEmpty()) return storedItems

        return (storedItems + scannedItems)
            .distinctBy { it.localPath }
            .sortedByDescending { it.receivedAt }
    }

    private fun scanGalleryFiles(): List<MediaMessage> {
        val candidates = sequenceOf(
            rootDir,
            imagesDir,
            paths.galleryCameraDir
        ).flatMap { directory ->
            directory.listFiles()
                .orEmpty()
                .asSequence()
                .filter { file -> file.isFile && file.extension.lowercase() in supportedExtensions }
        }

        return candidates.map { file ->
            MediaMessage(
                messageId = "file_${file.nameWithoutExtension}",
                senderId = "gallery",
                senderName = if (file.parentFile?.name == "camera") "Kamera" else "Galerija",
                targetContactId = "tablet",
                fileType = "image",
                mimeType = guessMimeType(file.extension),
                receivedAt = file.lastModified(),
                localPath = file.absolutePath,
                thumbnailPath = "",
                messageText = "",
                seen = true
            )
        }.toList()
    }

    private fun guessMimeType(extension: String): String {
        return when (extension.lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
    }

    private val supportedExtensions: Set<String>
        get() = setOf("jpg", "jpeg", "png", "webp")

    fun unseenCount(): Int = loadAll().count { !it.seen }

    fun markAllSeen() {
        val updated = loadAll().map { it.copy(seen = true) }
        writeAll(updated)
    }

    fun delete(messageId: String) {
        val items = loadAll()
        val target = items.firstOrNull { it.messageId == messageId } ?: return
        File(target.localPath).delete()
        if (target.thumbnailPath.isNotBlank()) File(target.thumbnailPath).delete()
        writeAll(items.filterNot { it.messageId == messageId })
    }

    fun saveCameraCapture(source: File, senderName: String = "Kamera"): MediaMessage? {
        if (!source.exists()) return null

        val messageId = "camera_${System.currentTimeMillis()}"
        val targetFile = File(paths.galleryCameraDir, source.name.ifBlank { "$messageId.jpg" })
        source.copyTo(targetFile, overwrite = true)

        val thumbFile = File(thumbsDir, "$messageId.jpg")
        createThumbnail(targetFile.readBytes(), thumbFile)

        val message = MediaMessage(
            messageId = messageId,
            senderId = "camera",
            senderName = senderName,
            targetContactId = "tablet",
            fileType = "image",
            mimeType = "image/jpeg",
            receivedAt = System.currentTimeMillis(),
            localPath = targetFile.absolutePath,
            thumbnailPath = thumbFile.absolutePath,
            messageText = "Mirror capture",
            seen = true
        )

        val updated = (loadAll().filterNot { it.messageId == message.messageId } + message)
            .sortedByDescending { it.receivedAt }
        writeAll(updated)
        return message
    }

    private fun writeAll(items: List<MediaMessage>) {
        rootDir.mkdirs()
        metadataFile.writeText(gson.toJson(items), Charsets.UTF_8)
    }

    private fun createThumbnail(bytes: ByteArray, target: File) {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
        val scaled = Bitmap.createScaledBitmap(bitmap, 240, 240, true)
        FileOutputStream(target).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
    }
}
