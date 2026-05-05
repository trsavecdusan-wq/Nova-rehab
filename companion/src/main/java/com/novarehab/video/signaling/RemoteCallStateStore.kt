package com.novarehab.video.signaling

import com.novarehab.video.model.CallState
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class RemoteCallStateStore(
    private val signalingBaseUrl: String,
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    fun read(roomId: String): CallSnapshot? {
        val request = Request.Builder()
            .url(roomUrl(roomId, "callState"))
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null

            val raw = response.body?.string().orEmpty()
            if (raw.isBlank() || raw == "null") return null

            val json = JSONObject(raw)
            val stateName = json.optString("state").trim().uppercase()
            val state = runCatching { CallState.valueOf(stateName) }.getOrDefault(CallState.IDLE)
            val room = json.optString("roomId").ifBlank { roomId }

            return CallSnapshot(
                roomId = room,
                contactId = json.optString("contactId"),
                contactName = json.optString("contactName"),
                state = state,
                updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
                errorMessage = json.optString("errorMessage")
            )
        }
    }

    fun write(snapshot: CallSnapshot) {
        val body = JSONObject()
            .put("roomId", snapshot.roomId)
            .put("contactId", snapshot.contactId)
            .put("contactName", snapshot.contactName)
            .put("state", snapshot.state.name)
            .put("updatedAt", snapshot.updatedAt)
            .put("errorMessage", snapshot.errorMessage)
            .toString()
            .toRequestBody(jsonType)

        val request = Request.Builder()
            .url(roomUrl(snapshot.roomId, "callState"))
            .put(body)
            .build()

        httpClient.newCall(request).execute().use {
        }
    }

    private fun roomUrl(roomId: String, child: String): String {
        return "${signalingBaseUrl.trimEnd('/')}/rooms/$roomId/$child.json"
    }
}
