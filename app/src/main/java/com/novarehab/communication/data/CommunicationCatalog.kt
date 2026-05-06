package com.novarehab.communication.data

import android.content.Context
import com.novarehab.communication.model.CommunicationItem
import com.novarehab.learning.LearningProfileManager
import com.novarehab.utils.PrefsManager

class CommunicationCatalog(
    private val context: Context,
    private val prefsManager: PrefsManager,
    private val learningProfileManager: LearningProfileManager
) {
    fun loadMain(language: String): List<CommunicationItem> {
        val items = CommunicationRepository.getMainItems(
            context = context,
            language = language,
            customIcons = prefsManager.getCustomCommIcons()
        )

        if (!prefsManager.isAutoSortCommunicationIconsEnabled()) {
            return items.take(MAX_MAIN_ITEMS)
        }

        return items.sortedWith(
            compareByDescending<CommunicationItem> { it.id in urgentIds || it.pinned }
                .thenByDescending { learningProfileManager.usageCount(it.id) }
                .thenBy { it.priority }
        ).take(MAX_MAIN_ITEMS)
    }

    private companion object {
        const val MAX_MAIN_ITEMS = 12
        val urgentIds = setOf("pomoc", "kopalnica", "bolecina", "slabo")
    }
}

