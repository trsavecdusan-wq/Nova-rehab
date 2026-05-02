package com.novarehab.communication.ai

import com.novarehab.communication.model.CommunicationItem
import com.novarehab.emotion.face.FaceObservation
import com.novarehab.learning.LearningProfileManager

data class IconSuggestionContext(
    val recentIconIds: List<String> = emptyList(),
    val hourOfDay: Int = -1,
    val screen: String = "main",
    val faceObservation: FaceObservation? = null,
    val videoCallActive: Boolean = false
)

object IconSuggestionEngine {
    fun suggestIcons(
        items: List<CommunicationItem>,
        learningProfile: LearningProfileManager,
        context: IconSuggestionContext
    ): List<CommunicationItem> {
        val byId = flatten(items).associateBy { it.id }
        val suggestions = mutableListOf<CommunicationItem>()

        if (context.faceObservation?.possibleFrustration == true) {
            listOf("pomoc", "pocakaj", "slabo").forEach { id -> byId[id]?.let { suggestions += it } }
        }

        if (context.faceObservation?.possibleFatigue == true) {
            listOf("utrujena", "piti", "voda").forEach { id -> byId[id]?.let { suggestions += it } }
        }

        learningProfile.mostUsedIconIds(limit = 6)
            .mapNotNull { byId[it] }
            .forEach { suggestions += it }

        return suggestions.distinctBy { it.id }.take(3)
    }

    private fun flatten(items: List<CommunicationItem>): List<CommunicationItem> {
        return items + items.flatMap { flatten(it.children) }
    }
}
