package com.novarehab.communication.model

data class CommunicationItem(
    val id: String,
    val label: String,
    val ttsText: String,
    val iconRes: Int,
    val children: List<CommunicationItem> = emptyList(),
    val shortLabel: String = label,
    val questionText: String = "",
    val category: String = "",
    val icon: String = "",
    val priority: Int = 0,
    val emotionalTags: List<String> = emptyList(),
    val aiPromptHint: String = "",
    val requiresConfirmation: Boolean = false,
    val enabled: Boolean = true,
    val arasaacKey: String = "",
    val symbolKey: String = "",
    val logEventType: String = "iconClicked"
)
