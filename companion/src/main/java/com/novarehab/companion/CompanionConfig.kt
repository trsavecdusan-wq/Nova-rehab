package com.novarehab.companion

object CompanionConfig {
    private var current: CompanionContactConfig = CompanionContactConfig(
        contactId = "c06",
        contactName = "Dušan",
        roomId = "novarehab_c06",
        preferredLanguage = "sl"
    )

    val companionId: String
        get() = "universal"

    val contactId: String
        get() = current.contactId

    val contactName: String
        get() = current.contactName

    val roomId: String
        get() = current.roomId

    val preferredLanguageCode: String
        get() = current.preferredLanguage

    const val signalingBaseUrl: String =
        "https://novarehab-dfcb9-default-rtdb.europe-west1.firebasedatabase.app"
    const val updateCheckUrl: String =
        "https://github.com/trsavecdusan-wq/Nova-rehab/releases/download/novarehab-companion-latest/app-version.json"
    const val apkDownloadUrl: String =
        "https://github.com/trsavecdusan-wq/Nova-rehab/releases/download/novarehab-companion-latest/companion-debug.apk"
    const val incomingCallsEnabled: Boolean = true
    const val outgoingCallsEnabled: Boolean = true

    fun update(config: CompanionContactConfig) {
        current = config
    }
}
