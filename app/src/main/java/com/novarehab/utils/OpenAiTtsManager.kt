package com.novarehab.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

object OpenAiTtsManager {

    private var tts: TextToSpeech? = null
    private var initialized = false

    fun init(context: Context) {
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {

                    val result = tts?.setLanguage(Locale("sl", "SI"))

                    if (result == TextToSpeech.LANG_MISSING_DATA ||
                        result == TextToSpeech.LANG_NOT_SUPPORTED) {

                        tts?.setLanguage(Locale("hr", "HR"))
                    }

                    initialized = true
                }
            }
        }
    }

    fun speak(context: Context, text: String) {
        if (!initialized) {
            init(context)
        }

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }
}
