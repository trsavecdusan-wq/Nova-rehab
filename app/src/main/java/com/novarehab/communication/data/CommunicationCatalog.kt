package com.novarehab.communication.data

import android.content.Context
import android.util.Log
import com.novarehab.communication.model.CommunicationItem
import com.novarehab.learning.LearningProfileManager
import com.novarehab.utils.PrefsManager
import kotlin.math.ceil

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

        val sortedItems = if (!prefsManager.isAutoSortCommunicationIconsEnabled()) {
            items
        } else {
            items.sortedWith(
                compareByDescending<CommunicationItem> { it.id in urgentIds || it.pinned }
                    .thenByDescending { learningProfileManager.usageCount(it.id) }
                    .thenBy { it.priority }
            )
        }

        val pageSize = prefsManager.getCommIconsPerPage().let { if (it in setOf(6, 8, 9, 12, 15, 18)) it else 9 }
        val pageCount = maxOf(1, ceil(sortedItems.size.toDouble() / pageSize).toInt())
        Log.d(
            "NovaRehabPaging",
            "total_main_icons=${sortedItems.size}, enabled_main_icons=${sortedItems.size}, page_size=$pageSize, page_count=$pageCount"
        )
        return sortedItems
    }

    private companion object {
        val urgentIds = setOf("pomoc", "kopalnica", "bolecina", "slabo")
    }
}
