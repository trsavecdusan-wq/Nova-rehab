package com.novarehab.video.communication

import android.content.Context
import com.novarehab.communication.data.CommunicationRepository
import com.novarehab.communication.model.CommunicationItem
import com.novarehab.communication.stats.CommunicationStatEvent
import com.novarehab.communication.stats.CommunicationStatsManager
import com.novarehab.utils.CustomCommIcon
import java.util.Locale

class VideoCommunicationManager(
    private val context: Context,
    private val statsManager: CommunicationStatsManager = CommunicationStatsManager()
) {
    fun buildOverlayItems(
        language: String,
        customIcons: List<CustomCommIcon>
    ): List<CommunicationItem> {
        val normalizedLanguage = language.trim().lowercase(Locale.getDefault()).ifBlank { "sl" }
        val localizedCustomIcons = customIcons.filter {
            val itemLanguage = it.language.trim().lowercase(Locale.getDefault()).ifBlank { normalizedLanguage }
            itemLanguage == normalizedLanguage || it.title.isBlank() && it.text.isBlank()
        }
        val baseItems = CommunicationRepository.getMainItems(context, normalizedLanguage, localizedCustomIcons)

        return baseItems
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<CommunicationItem> { it.pinnedVideo }
                    .thenByDescending { it.priority }
                    .thenBy { it.label }
            )
    }

    fun logSelection(item: CommunicationItem, language: String) {
        statsManager.log(
            CommunicationStatEvent(
                type = "video_overlay_icon",
                itemId = item.id,
                language = language,
                details = item.label
            )
        )
    }

    fun exportStats(): List<CommunicationStatEvent> = statsManager.exportSnapshot()
}

