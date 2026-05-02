package com.novarehab.speech.model

data class SpeechSettings(
    val localSpeechEnabled: Boolean = true,
    val apiSpeechEnabled: Boolean = false,
    val preferredEngine: String = "",
    val voice: String = "",
    val speechRate: Float = 0.9f,
    val pitch: Float = 1.0f,
    val volume: Float = 1.0f,
    val language: String = "sl",
    val fallbackLanguage: String = "sl",
    val pauseRadioDuringSpeech: Boolean = true,
    val cacheApiSpeech: Boolean = true,
    val clearSpeechCache: Boolean = false
)
