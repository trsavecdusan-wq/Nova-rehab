package com.novarehab.companion

object CompanionContactText {
    fun callButtonText(contactName: String): String = "POKLIČI ${toAccusative(contactName)}"

    fun idleStatus(contactName: String): String = "Pokliči ${toAccusative(contactName)}"

    fun callingStatus(contactName: String): String = "Kličem ${toAccusative(contactName)}"

    fun waitingStatus(contactName: String): String = "Čakam odgovor ${toGenitive(contactName)}"

    fun acceptedStatus(contactName: String): String = "$contactName je sprejel"

    fun rejectedStatus(contactName: String): String = "$contactName je zavrnil"

    fun busyStatus(contactName: String): String = "$contactName je zaseden"

    private fun toAccusative(contactName: String): String {
        return when (contactName) {
            "Žana" -> "Žano"
            "Inna" -> "Inno"
            "Julija" -> "Julijo"
            "Kuma" -> "Kumo"
            else -> contactName
        }
    }

    private fun toGenitive(contactName: String): String {
        return when (contactName) {
            "Žana" -> "Žane"
            "Inna" -> "Inne"
            "Julija" -> "Julije"
            "Kuma" -> "Kume"
            else -> contactName
        }
    }
}
