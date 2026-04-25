package com.novarehab.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

object OpenAiTtsManager {

    private var tts: TextToSpeech? = null

    fun init(context: Context) {
        if (tts == null) {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {

                    // POSKUSI SLOVENŠČINO
                    val result = tts?.setLanguage(Locale("sl", "SI"))

                    if (result == TextToSpeech.LANG_MISSING_DATA ||
                        result == TextToSpeech.LANG_NOT_SUPPORTED) {

                        // če slovenščina ne dela → hrvaščina (bolj razumljivo kot angleško)
                        tts?.setLanguage(Locale("hr", "HR"))
                    }
                }
            }
        }
    }

    fun speak(context: Context, text: String) {
        if (tts == null) init(context)

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "rehab_tts")
    }
}
