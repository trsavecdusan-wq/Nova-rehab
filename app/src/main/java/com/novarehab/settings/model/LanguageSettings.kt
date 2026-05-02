package com.novarehab.settings.model

data class LanguageSettings(
    val patientPrimaryLanguage: String = "sl",
    val patientSecondaryLanguage: String = "uk",
    val caregiverLanguage: String = "sl",
    val autoTranslateEnabled: Boolean = false,
    val autoDetectCaregiverLanguage: Boolean = false,
    val showDualLanguageText: Boolean = false,
    val speakDualLanguage: Boolean = false,
    val fallbackLanguage: String = "sl"
) {
    companion object {
        val supportedLanguages = setOf("sl", "uk", "en", "de", "hr", "sr", "ru")
    }
}
