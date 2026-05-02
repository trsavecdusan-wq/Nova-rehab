package com.novarehab.communication.ai

import com.novarehab.communication.model.CommunicationItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiReplyAssistantTest {
    @Test
    fun fallbackSuggestionsStayShortAndConfirmedByUser() {
        val item = CommunicationItem(
            id = "voda",
            label = "VODA",
            ttsText = "Želim vodo.",
            iconRes = 0
        )

        val suggestions = AiReplyAssistant.suggestReplies(item, PatientState(possibleFatigue = true), language = "sl")

        assertTrue(suggestions.size <= 3)
        assertEquals("Želim vodo.", suggestions.first())
        assertTrue(suggestions.any { it.contains("Utrujena") })
    }
}
