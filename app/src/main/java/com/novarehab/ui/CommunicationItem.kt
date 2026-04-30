package com.novarehab.ui

data class CommunicationItem(
    val id: String,
    val label: String,
    val ttsText: String,
    val iconRes: Int,
    val children: List<CommunicationItem> = emptyList()
)
