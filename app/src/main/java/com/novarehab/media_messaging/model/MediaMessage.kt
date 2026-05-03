package com.novarehab.media_messaging.model

data class MediaMessage(
    val messageId: String,
    val senderId: String,
    val senderName: String,
    val targetContactId: String,
    val fileType: String,
    val mimeType: String,
    val receivedAt: Long,
    val localPath: String,
    val thumbnailPath: String = "",
    val messageText: String = "",
    val seen: Boolean = false
)
