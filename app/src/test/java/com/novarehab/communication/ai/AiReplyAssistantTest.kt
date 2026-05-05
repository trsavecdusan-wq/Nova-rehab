package com.novarehab.communication.ai

import com.novarehab.communication.model.CommunicationItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiReplyAssistantTest {
    @Test
    fun fallbackSuggestionsStayShortAndIncludeFatigueVariant() {
        val item = CommunicationItem(
            id = "voda",
            label = "VODA",
            ttsText = "Zelim vodo.",
            iconRes = 0
        )

        val suggestions = AiReplyAssistant.suggestReplies(
            selectedItem = item,
            patientState = PatientState(possibleFatigue = true),
            language = "sl"
        )

        assertTrue(suggestions.size <= 3)
        assertEquals("Zelim vodo.", suggestions.first())
        assertTrue(suggestions.any { it.contains("Utrujena") })
    }

    @Test
    fun suggestionsNeverExceedThreeItems() {
        val item = CommunicationItem(
            id = "pomoc",
            label = "POMOC",
            ttsText = "Prosim pomagajte mi.",
            iconRes = 0
        )

        val suggestions = AiReplyAssistant.suggestReplies(
            selectedItem = item,
            patientState = PatientState(
                possibleFatigue = true,
                possibleFrustration = true,
                activeLanguage = "sl"
            ),
            recentStats = RecentStats(recentItemIds = listOf("pocakaj")),
            language = "sl"
        )

        assertTrue(suggestions.size <= 3)
    }
}
