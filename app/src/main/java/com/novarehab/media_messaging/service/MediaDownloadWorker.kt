package com.novarehab.media_messaging.service

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class MediaDownloadWorker {
    private val httpClient = OkHttpClient()

    data class IncomingImagePayload(
        val messageId: String,
        val senderId: String,
        val senderName: String,
        val mimeType: String,
        val base64Data: String,
        val createdAt: Long,
        val messageText: String
    )

    fun fetchLatestAllowedImage(signalingBaseUrl: String, allowedSenderIds: Set<String>): IncomingImagePayload? {
        val request = Request.Builder()
            .url("${signalingBaseUrl.trimEnd('/')}/mediaInbox.json")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            if (body.isBlank() || body == "null") return null

            val root = JSONObject(body)
            val keys = root.keys()
            var latest: IncomingImagePayload? = null

            while (keys.hasNext()) {
                val key = keys.next()
                val item = root.optJSONObject(key) ?: continue
                val senderId = item.optString("senderId").trim()
                if (senderId !in allowedSenderIds) continue
                if (item.optString("fileType") != "image") continue

                val payload = IncomingImagePayload(
                    messageId = item.optString("messageId").ifBlank { key },
                    senderId = senderId,
                    senderName = item.optString("senderName"),
                    mimeType = item.optString("mimeType", "image/jpeg"),
                    base64Data = item.optString("base64Data"),
                    createdAt = item.optLong("createdAt", 0L),
                    messageText = item.optString("messageText")
                )

                if (payload.base64Data.isBlank()) continue
                if (latest == null || payload.createdAt > latest!!.createdAt) {
                    latest = payload
                }
            }

            return latest
        }
    }

    fun deleteRemoteMessage(signalingBaseUrl: String, messageId: String) {
        val request = Request.Builder()
            .url("${signalingBaseUrl.trimEnd('/')}/mediaInbox/$messageId.json")
            .delete()
            .build()

        runCatching {
            httpClient.newCall(request).execute().close()
        }
    }
}
