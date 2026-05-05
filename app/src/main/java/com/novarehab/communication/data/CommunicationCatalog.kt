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
    fun load(language: String): List<CommunicationItem> {
        val items = CommunicationRepository.load(context, language) +
            CommunicationRepository.customItems(prefsManager.getCustomCommIcons())

        if (!prefsManager.isAutoSortCommunicationIconsEnabled()) {
            return items
        }

        return items.sortedWith(
            compareByDescending<CommunicationItem> { it.id in urgentIds || it.pinned }
                .thenByDescending { learningProfileManager.usageCount(it.id) }
                .thenBy { it.priority }
        )
    }

    private companion object {
        val urgentIds = setOf("pomoc", "kopalnica", "bolecina", "slabo")
    }
}
