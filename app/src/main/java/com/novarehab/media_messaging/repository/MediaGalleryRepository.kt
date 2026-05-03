package com.novarehab.media_messaging.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.novarehab.media_messaging.model.MediaMessage
import java.io.File
import java.io.FileOutputStream

class MediaGalleryRepository(context: Context) {
    private val appContext = context.applicationContext
    private val gson = Gson()
    private val rootDir = File(appContext.getExternalFilesDir(null), "NovaRehab/gallery")
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
        if (!metadataFile.exists()) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<MediaMessage>>() {}.type
            gson.fromJson<List<MediaMessage>>(metadataFile.readText(Charsets.UTF_8), type).orEmpty()
        }.getOrElse { emptyList() }
    }

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
