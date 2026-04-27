
package com.novarehab.ui

import androidx.annotation.DrawableRes

data class RehabCallContact(
    val id: String,
    val displayName: String,
    @DrawableRes val imageRes: Int,
    val preferredLanguageCode: String,
    val callRoomId: String
)
