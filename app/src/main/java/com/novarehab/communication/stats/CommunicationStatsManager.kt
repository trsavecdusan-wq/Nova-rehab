package com.novarehab.communication.stats

data class CommunicationStatEvent(
    val type: String,
    val itemId: String = "",
    val language: String = "sl",
    val details: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

class CommunicationStatsManager {
    private val pendingEvents = mutableListOf<CommunicationStatEvent>()

    fun log(event: CommunicationStatEvent) {
        pendingEvents += event
        if (pendingEvents.size > 500) {
            pendingEvents.removeAt(0)
        }
    }

    fun exportSnapshot(): List<CommunicationStatEvent> = pendingEvents.toList()

    fun clear() {
        pendingEvents.clear()
    }
}
