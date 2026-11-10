package com.bunc.tv.ui.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bunc.tv.data.ServiceLocator
import com.bunc.tv.data.model.Category
import com.bunc.tv.data.model.Content
import com.bunc.tv.data.model.Section
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    private val repo = ServiceLocator.repo

    val isM3u = repo.isM3u
    val sections: List<Section> =
        if (isM3u) listOf(Section.LIVE) else listOf(Section.LIVE, Section.MOVIES, Section.SERIES)

    var section by mutableStateOf(Section.LIVE)
        private set
    var categories by mutableStateOf<List<Category>>(emptyList())
        private set
    var selectedCategory by mutableStateOf("")
        private set
    var content by mutableStateOf<List<Content>>(emptyList())
        private set
    var loadingContent by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init { loadCategories() }

    fun selectSection(s: Section) {
        if (s == section) return
        section = s
        selectedCategory = ""
        loadCategories()
    }

    fun selectCategory(id: String) {
        if (id == selectedCategory) return
        selectedCategory = id
        loadContent()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            error = null
            categories = try {
                repo.categories(section)
            } catch (e: Exception) {
                error = e.message; emptyList()
            }
            selectedCategory = categories.firstOrNull()?.id ?: ""
            loadContent()
        }
    }

    private fun loadContent() {
        viewModelScope.launch {
            loadingContent = true
            error = null
            content = try {
                repo.content(section, selectedCategory)
            } catch (e: Exception) {
                error = e.message; emptyList()
            }
            loadingContent = false
        }
    }

    /** Leaves the current profile and returns to the picker; saved profiles are kept. */
    fun switchProfile(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.reset()
            onDone()
        }
    }

    // Stream URL helpers for the UI
    fun liveUrl(c: Content): String = if (isM3u) repo.m3uUrl(c.id) else repo.liveUrl(c.id)
    fun movieUrl(c: Content): String = repo.movieUrl(c.id, c.containerExt)
}
