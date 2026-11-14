package com.bunc.tv.ui.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bunc.tv.data.FolderOrder
import com.bunc.tv.data.ServiceLocator
import com.bunc.tv.data.model.Category
import com.bunc.tv.data.model.Section
import kotlinx.coroutines.launch

/** Folder sorting settings: server order, A–Z, or a custom numbered order (per section, per profile). */
class SettingsViewModel : ViewModel() {
    private val repo = ServiceLocator.repo
    private val prefs = ServiceLocator.prefs

    val sections: List<Section> =
        if (repo.isM3u) listOf(Section.LIVE) else listOf(Section.LIVE, Section.MOVIES, Section.SERIES)

    var section by mutableStateOf(Section.LIVE)
        private set
    var mode by mutableStateOf(FolderOrder.SERVER)
        private set
    /** Folders in the order they will be shown (custom order when mode = custom). */
    var folders by mutableStateOf<List<Category>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set

    private var serverOrder: List<Category> = emptyList()
    private var key = "default"

    init { load() }

    fun selectSection(s: Section) {
        if (s == section) return
        section = s
        load()
    }

    private fun load() {
        viewModelScope.launch {
            loading = true
            key = FolderOrder.profileKey()
            mode = prefs.getSortMode(key, section.name)
            serverOrder = try {
                repo.categories(section).filter { it.id.isNotBlank() }
            } catch (e: Exception) {
                emptyList()
            }
            folders = preview()
            loading = false
        }
    }

    private suspend fun preview(): List<Category> = when (mode) {
        FolderOrder.AZ -> FolderOrder.alphabetical(serverOrder)
        FolderOrder.CUSTOM -> FolderOrder.byCustomOrder(serverOrder, prefs.getCustomOrder(key, section.name))
        else -> serverOrder
    }

    fun setMode(m: String) {
        viewModelScope.launch {
            // Switching to custom starts from whatever order is on screen now.
            val current = folders
            mode = m
            prefs.setSortMode(key, section.name, m)
            if (m == FolderOrder.CUSTOM) {
                folders = current
                prefs.setCustomOrder(key, section.name, current.map { it.id })
            } else {
                folders = preview()
            }
            FolderOrder.version++
        }
    }

    /** Move folder at [from] to position [to] (0-based) and save. */
    fun moveTo(from: Int, to: Int) {
        if (mode != FolderOrder.CUSTOM) return
        val list = folders.toMutableList()
        if (from !in list.indices) return
        val target = to.coerceIn(0, list.lastIndex)
        if (target == from) return
        val item = list.removeAt(from)
        list.add(target, item)
        folders = list
        save()
    }

    fun move(index: Int, delta: Int) = moveTo(index, index + delta)

    /** In custom mode: reorder everything A–Z as a starting point. */
    fun sortCustomAz() {
        if (mode != FolderOrder.CUSTOM) return
        folders = FolderOrder.alphabetical(folders)
        save()
    }

    /** In custom mode: go back to the provider's order. */
    fun resetCustom() {
        if (mode != FolderOrder.CUSTOM) return
        folders = serverOrder
        save()
    }

    private fun save() {
        val ids = folders.map { it.id }
        viewModelScope.launch {
            prefs.setCustomOrder(key, section.name, ids)
            FolderOrder.version++
        }
    }
}
