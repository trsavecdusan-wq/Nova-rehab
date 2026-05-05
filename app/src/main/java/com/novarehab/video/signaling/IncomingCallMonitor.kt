package com.novarehab.video.signaling

import com.novarehab.utils.PrefsManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class IncomingCallRequest(
    val roomId: String,
    val contactName: String
)

class IncomingCallMonitor(
    private val prefsManager: PrefsManager,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val signalingBaseUrl: String = DEFAULT_SIGNALING_BASE_URL
) {
    fun findIncomingCall(activeIncomingRoomId: String?): IncomingCallRequest? {
        val ids = listOf("c01", "c02", "c03", "c04", "c05", "c06")
        val contacts = prefsManager.getContacts()

        ids.forEachIndexed { index, id ->
            if (!prefsManager.isContactIncomingCallEnabled(index)) return@forEachIndexed

            val roomId = "novarehab_$id"
            if (activeIncomingRoomId == roomId) return@forEachIndexed

            val json = getOutgoingRequest(roomId) ?: return@forEachIndexed
            if (json.optString("status") != "calling") return@forEachIndexed

            val name = json.optString("contactName")
                .ifBlank { contacts.getOrNull(index)?.name }
                .orEmpty()
                .ifBlank { id.uppercase() }

            return IncomingCallRequest(roomId, name)
        }

        return null
    }

    fun sendStatus(roomId: String, status: String) {
        runCatching {
            val body = JSONObject()
                .put("status", status)
                .put("updatedAt", System.currentTimeMillis())
                .toString()
                .toRequestBody(jsonType)

            val request = Request.Builder()
                .url(roomUrl(roomId, "outgoingRequest"))
                .patch(body)
                .build()

            httpClient.newCall(request).execute().use {
            }
        }
    }

    private fun getOutgoingRequest(roomId: String): JSONObject? {
        return try {
            val request = Request.Builder()
                .url(roomUrl(roomId, "outgoingRequest"))
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null

                val raw = response.body?.string().orEmpty()
                if (raw.isBlank() || raw == "null") null else JSONObject(raw)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun roomUrl(roomId: String, child: String): String {
        return "${signalingBaseUrl.trimEnd('/')}/rooms/$roomId/$child.json"
    }

    private companion object {
        const val DEFAULT_SIGNALING_BASE_URL =
            "https://novarehab-dfcb9-default-rtdb.europe-west1.firebasedatabase.app"

        val jsonType = "application/json; charset=utf-8".toMediaType()
    }
}
