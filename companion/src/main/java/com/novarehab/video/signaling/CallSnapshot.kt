package com.novarehab.video.signaling

import com.novarehab.video.model.CallState

data class CallSnapshot(
    val roomId: String,
    val contactId: String,
    val contactName: String,
    val state: CallState,
    val updatedAt: Long,
    val errorMessage: String = ""
)
