package com.novarehab.utils

import android.content.Context

class LanguageDetector(
    private val context: Context,
    private val onLanguageDetected: (String) -> Unit
) {
    fun start() {
        // Namerno izklopljeno.
        // Neprestano Android SpeechRecognizer poslušanje povzroča pisk
        // in začasno utiša radio vsakih nekaj sekund.
    }

    fun stop() {
        // Ni aktivnega poslušanja.
    }

    fun pause() {
        // Ni aktivnega poslušanja.
    }

    fun resume() {
        // Namerno izklopljeno, da radio ostane stabilen.
    }
}
