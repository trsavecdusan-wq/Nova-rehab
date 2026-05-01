private data class IncomingCallRequest(
    val roomId: String,
    val contactName: String
)

private fun checkIncomingCompanionCalls() {
    Thread {
        val request = findIncomingCompanionCall()
        if (request != null) {
            runOnUiThread {
                showIncomingCallDialog(request)
            }
        }
    }.start()
}

private fun findIncomingCompanionCall(): IncomingCallRequest? {
    val contacts = prefs.getContacts()
    val defaultIds = listOf("contact1", "contact2", "contact3", "contact4", "contact5", "contact6")

    defaultIds.forEachIndexed { index, id ->
        if (!prefs.isContactIncomingCallEnabled(index)) return@forEachIndexed

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

private fun showIncomingCallDialog(request: IncomingCallRequest) {
    if (activeIncomingRoomId == request.roomId || isFinishing) return

    activeIncomingRoomId = request.roomId

    android.app.AlertDialog.Builder(this)
        .setTitle("${request.contactName} kliče")
        .setMessage("TESTNI KLIC\nSogovornik želi poklicati Lano.")
        .setPositiveButton("SPREJMI") { _, _ ->
            sendOutgoingRequestStatus(request.roomId, "accepted")
            Toast.makeText(this, "Testni klic sprejet.", Toast.LENGTH_LONG).show()
            activeIncomingRoomId = null
        }
        .setNegativeButton("ZAVRNI") { _, _ ->
            sendOutgoingRequestStatus(request.roomId, "rejected")
            Toast.makeText(this, "Klic zavrnjen.", Toast.LENGTH_SHORT).show()
            activeIncomingRoomId = null
        }
        .setOnCancelListener {
            sendOutgoingRequestStatus(request.roomId, "rejected")
            activeIncomingRoomId = null
        }
        .show()
}

private fun sendOutgoingRequestStatus(roomId: String, status: String) {
    Thread {
        try {
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
        } catch (_: Exception) {
        }
    }.start()
}

private fun roomUrl(roomId: String, child: String): String {
    return "${SIGNALING_BASE_URL.trimEnd('/')}/rooms/$roomId/$child.json"
}
