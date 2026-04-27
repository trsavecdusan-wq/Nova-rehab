package com.novarehab.ui

import androidx.annotation.DrawableRes

data class VideoAnswer(
    val id: String,
    @DrawableRes val iconRes: Int,
    val textSl: String,
    val textUk: String
)
