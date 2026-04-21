package com.novarehab.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

// Samodejno zaznavanje jezika iz govora
class LanguageDetector(
    private val context: Context,
    private val onLanguageDetected: (String) -> Unit  // "sl" ali "uk"
) {
    private var recognizer: SpeechRecognizer? = null
    private var isListening = false
    private var isEnabled = true

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        isEnabled = true
        listen()
    }

    fun stop() {
        isEnabled = false
        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
        isListening = false
    }

    fun pause() { isEnabled = false; recognizer?.stopListening() }
    fun resume() { isEnabled = true; listen() }

    private fun listen() {
        if (!isEnabled || isListening) return

        try {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { isListening = true }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { isListening = false }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val detected = results?.getStringArrayList("android.speech.extra.RESULTS_LANGS")

                    if (!detected.isNullOrEmpty()) {
                        val lang = detected[0].lowercase()
                        val detectedLang = when {
                            lang.startsWith("uk") -> "uk"
                            lang.startsWith("sl") -> "sl"
                            lang.contains("ukr")  -> "uk"
                            lang.contains("slo")  -> "sl"
                            else -> null
                        }
                        detectedLang?.let { onLanguageDetected(it) }
                    } else if (!matches.isNullOrEmpty()) {
                        // Heuristika: ukrainske besede imajo specifične znake
                        val text = matches[0]
                        val hasUkrainianChars = text.any { it in "іїєґ" }
                        val hasSlovenianChars = text.any { it in "čšž" }
                        when {
                            hasUkrainianChars -> onLanguageDetected("uk")
                            hasSlovenianChars -> onLanguageDetected("sl")
                        }
                    }

                    // Posluša znova po kratki pavzi
                    if (isEnabled) {
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ listen() }, 2000)
                    }
                }

                override fun onError(error: Int) {
                    isListening = false
                    if (isEnabled) {
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ listen() }, 3000)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "und")  // undefined = samodejno zaznaj
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
            }
            recognizer?.startListening(intent)
        } catch (e: Exception) {}
    }
}
