package com.novarehab.companion

import android.content.ContentResolver
import android.net.Uri
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class CompanionMediaSender(
    private val signalingBaseUrl: String
) {
    private val httpClient = OkHttpClient()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    fun sendImage(
        contentResolver: ContentResolver,
        imageUri: Uri,
        senderId: String,
        senderName: String
    ) {
        val bytes = contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Slike ni bilo mogoče prebrati.")

        if (bytes.size > 5 * 1024 * 1024) {
            throw IllegalStateException("Slika je prevelika. Dovoljenih je največ 5 MB.")
        }

        val messageId = "media_" + System.currentTimeMillis()
        val body = JSONObject()
            .put("messageId", messageId)
            .put("senderId", senderId)
            .put("senderName", senderName)
            .put("targetContactId", "tablet")
            .put("fileType", "image")
            .put("mimeType", "image/jpeg")
            .put("createdAt", System.currentTimeMillis())
            .put("base64Data", Base64.encodeToString(bytes, Base64.NO_WRAP))
            .toString()
            .toRequestBody(jsonType)

        val request = Request.Builder()
            .url("${signalingBaseUrl.trimEnd('/')}/mediaInbox/$messageId.json")
            .put(body)
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Pošiljanje slike ni uspelo (${response.code}).")
            }
        }
    }
}
