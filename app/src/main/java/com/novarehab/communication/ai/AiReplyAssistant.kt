package com.novarehab.communication.ai

import com.novarehab.communication.model.CommunicationItem

data class PatientState(
    val possibleFatigue: Boolean = false,
    val possibleFrustration: Boolean = false,
    val activeLanguage: String = "sl"
)

data class RecentStats(
    val recentItemIds: List<String> = emptyList(),
    val hourOfDay: Int = -1
)

object AiReplyAssistant {
    fun suggestReplies(
        selectedItem: CommunicationItem,
        patientState: PatientState = PatientState(),
        recentStats: RecentStats = RecentStats(),
        language: String = patientState.activeLanguage
    ): List<String> {
        val base = selectedItem.ttsText.ifBlank { selectedItem.label }
        val cleanBase = base.trim().ifBlank { fallbackBase(language) }
        val suggestions = mutableListOf<String>()

        suggestions += cleanBase
        suggestions += politeVariant(cleanBase, language)

        if (patientState.possibleFatigue) {
            suggestions += when (language.lowercase()) {
                "uk" -> "Я втомлена, будь ласка допоможіть."
                "en" -> "I am tired, please help me."
                else -> "Utrujena sem, prosim pomagajte mi."
            }
        } else if (patientState.possibleFrustration) {
            suggestions += when (language.lowercase()) {
                "uk" -> "Будь ласка, зачекайте."
                "en" -> "Please wait a moment."
                else -> "Prosim, počakajte trenutek."
            }
        } else if (recentStats.recentItemIds.contains("pocakaj")) {
            suggestions += when (language.lowercase()) {
                "uk" -> "Поясню ще раз."
                "en" -> "I will explain again."
                else -> "Povedala bom še enkrat."
            }
        }

        return suggestions.distinct().take(3)
    }

    fun simplifyText(text: String, language: String): String {
        val trimmed = text.trim()
        if (trimmed.length <= 80) return trimmed
        val sentence = trimmed.split('.', '!', '?').firstOrNull()?.trim().orEmpty()
        return sentence.ifBlank { trimmed.take(80) }
    }

    fun translateIfNeeded(text: String, targetLanguage: String): String {
        return text
    }

    fun createCaregiverMessage(selectedItem: CommunicationItem, context: String = ""): String {
        val base = selectedItem.ttsText.ifBlank { selectedItem.label }.trim()
        return listOf(base, context.trim())
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Pacient želi pomoč." }
    }

    private fun politeVariant(text: String, language: String): String {
        return when (language.lowercase()) {
            "uk" -> "Будь ласка, ${text.replaceFirstChar { it.lowercase() }}"
            "en" -> "Please, ${text.replaceFirstChar { it.lowercase() }}"
            else -> "Prosim, ${text.replaceFirstChar { it.lowercase() }}"
        }
    }

    private fun fallbackBase(language: String): String {
        return when (language.lowercase()) {
            "uk" -> "Будь ласка, допоможіть."
            "en" -> "Please help me."
            else -> "Prosim pomagajte mi."
        }
    }
}
