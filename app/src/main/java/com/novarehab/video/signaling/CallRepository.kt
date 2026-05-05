package com.novarehab.video.signaling

import com.novarehab.video.model.CallState

data class CallSnapshot(
    val roomId: String,
    val contactId: String,
    val contactName: String,
    val state: CallState,
    val updatedAt: Long = System.currentTimeMillis(),
    val errorMessage: String = ""
)

class CallRepository {
    private val calls = LinkedHashMap<String, CallSnapshot>()

    fun update(snapshot: CallSnapshot) {
        calls[snapshot.roomId] = snapshot
    }

    fun get(roomId: String): CallSnapshot? = calls[roomId]

    fun isBusy(roomId: String): Boolean = calls[roomId]?.state in setOf(CallState.RINGING, CallState.ACTIVE)

    fun markMissed(roomId: String) {
        val current = calls[roomId] ?: return
        calls[roomId] = current.copy(state = CallState.MISSED, updatedAt = System.currentTimeMillis())
    }

    fun clear(roomId: String) {
        calls.remove(roomId)
    }
}
