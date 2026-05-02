package com.novarehab.ui

import android.content.Context
import com.novarehab.utils.CustomCommIcon

object CommunicationRepository {

    fun load(context: Context, language: String): List<CommunicationItem> {
        return com.novarehab.communication.data.CommunicationRepository.load(context, language)
    }

    fun loadFallback(): List<CommunicationItem> {
        return com.novarehab.communication.data.CommunicationRepository.loadFallback()
    }

    fun defaultItems(): List<CommunicationItem> {
        return loadFallback()
    }

    fun getMainItems(context: Context, language: String): List<CommunicationItem> {
        return com.novarehab.communication.data.CommunicationRepository.getMainItems(context, language)
    }

    fun getChildren(context: Context, language: String, parentId: String): List<CommunicationItem> {
        return com.novarehab.communication.data.CommunicationRepository.getChildren(context, language, parentId)
    }

    fun searchById(context: Context, language: String, id: String): CommunicationItem? {
        return com.novarehab.communication.data.CommunicationRepository.searchById(context, language, id)
    }

    fun customItems(items: List<CustomCommIcon>): List<CommunicationItem> {
        return com.novarehab.communication.data.CommunicationRepository.customItems(items)
    }
}
