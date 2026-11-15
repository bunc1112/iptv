package com.bunc.tv.ui.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bunc.tv.data.FolderOrder
import com.bunc.tv.data.ServiceLocator
import com.bunc.tv.data.model.Category
import com.bunc.tv.data.model.Content
import com.bunc.tv.data.model.Section
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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

    // Only the newest request may update the screen (fast tab/folder switching).
    private var categoriesJob: Job? = null
    private var contentJob: Job? = null

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
        categoriesJob?.cancel()
        contentJob?.cancel()
        val wanted = section
        categoriesJob = viewModelScope.launch {
            error = null
            loadingContent = true
            content = emptyList()
            val loaded = try {
                FolderOrder.apply(wanted, repo.categories(wanted))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = friendly(e); emptyList()
            }
            if (wanted != section) return@launch
            categories = loaded
            selectedCategory = loaded.firstOrNull()?.id ?: ""
            if (loaded.isEmpty()) { loadingContent = false; return@launch }
            loadContent()
        }
    }

    /** Re-sort the folder list after the sorting settings changed; keeps the current selection. */
    fun reorder() {
        viewModelScope.launch {
            categories = try {
                FolderOrder.apply(section, repo.categories(section))
            } catch (e: Exception) {
                categories
            }
        }
    }

    private fun loadContent() {
        contentJob?.cancel()
        val wantedSection = section
        val wantedCategory = selectedCategory
        contentJob = viewModelScope.launch {
            loadingContent = true
            error = null
            val loaded = try {
                repo.content(wantedSection, wantedCategory)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = friendly(e); emptyList()
            }
            if (wantedSection != section || wantedCategory != selectedCategory) return@launch
            content = loaded
            loadingContent = false
        }
    }

    private fun friendly(e: Exception): String = when (e) {
        is java.net.UnknownHostException -> "Can't find the server. Check your internet connection."
        is java.net.SocketTimeoutException -> "The server took too long to answer. Try again."
        is java.io.IOException -> "Connection problem. Check your internet and try again."
        else -> "Couldn't load this list from the server."
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
