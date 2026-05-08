package com.novarehab.communication.data

import android.content.Context
import android.util.Log
import com.novarehab.BuildConfig
import com.novarehab.communication.model.CommunicationItem
import com.novarehab.learning.LearningProfileManager
import com.novarehab.utils.PrefsManager
import kotlin.math.ceil
import kotlin.math.min

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

        val pageSize = prefsManager.getCommIconsPerPage().let { if (it in setOf(4, 9, 16, 25)) it else 9 }
        val pageCount = maxOf(1, ceil(sortedItems.size.toDouble() / pageSize).toInt())
        val page1Count = min(pageSize, sortedItems.size)
        val page2Count = if (sortedItems.size > pageSize) min(pageSize, sortedItems.size - pageSize) else 0

        if (BuildConfig.DEBUG) {
            Log.d(
                "NovaRehabPaging",
                "total_main_icons=${sortedItems.size}, enabled_main_icons=${sortedItems.size}, page_size=$pageSize, page_count=$pageCount, page_1_count=$page1Count, page_2_count=$page2Count"
            )
        }
        return sortedItems
    }

    private companion object {
        val urgentIds = setOf("pomoc", "kopalnica", "bolecina", "slabo")
    }
}
