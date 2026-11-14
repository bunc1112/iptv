package com.bunc.tv.ui.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bunc.tv.data.ServiceLocator
import com.bunc.tv.data.model.Content
import com.bunc.tv.data.model.Section
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer

/** Searches live channels, movies and series by name (case- and accent-insensitive). */
class SearchViewModel : ViewModel() {
    private val repo = ServiceLocator.repo
    val isM3u = repo.isM3u

    var query by mutableStateOf("")
        private set
    var loadingCatalog by mutableStateOf(true)
        private set
    var live by mutableStateOf<List<Content>>(emptyList())
        private set
    var movies by mutableStateOf<List<Content>>(emptyList())
        private set
    var series by mutableStateOf<List<Content>>(emptyList())
        private set

    // Catalog with pre-normalized names for fast filtering.
    private var allLive: List<Pair<String, Content>> = emptyList()
    private var allMovies: List<Pair<String, Content>> = emptyList()
    private var allSeries: List<Pair<String, Content>> = emptyList()
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            coroutineScope {
                val l = async { load(Section.LIVE) }
                val m = async { if (isM3u) emptyList() else load(Section.MOVIES) }
                val s = async { if (isM3u) emptyList() else load(Section.SERIES) }
                allLive = l.await(); allMovies = m.await(); allSeries = s.await()
            }
            loadingCatalog = false
            filter()
        }
    }

    private suspend fun load(section: Section): List<Pair<String, Content>> =
        try {
            val items = repo.content(section, "")
            withContext(Dispatchers.Default) { items.map { norm(it.name) to it } }
        } catch (e: Exception) {
            emptyList()
        }

    fun onQuery(q: String) {
        query = q
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(250) // wait until typing pauses
            filter()
        }
    }

    private suspend fun filter() {
        val words = norm(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty() || query.trim().length < 2) {
            live = emptyList(); movies = emptyList(); series = emptyList()
            return
        }
        withContext(Dispatchers.Default) {
            fun match(list: List<Pair<String, Content>>) =
                list.asSequence()
                    .filter { (name, _) -> words.all { name.contains(it) } }
                    .map { it.second }
                    .take(MAX_PER_SECTION)
                    .toList()
            val l = match(allLive); val m = match(allMovies); val s = match(allSeries)
            withContext(Dispatchers.Main) { live = l; movies = m; series = s }
        }
    }

    fun liveUrl(c: Content): String = if (isM3u) repo.m3uUrl(c.id) else repo.liveUrl(c.id)
    fun movieUrl(c: Content): String = repo.movieUrl(c.id, c.containerExt)

    companion object {
        private const val MAX_PER_SECTION = 150

        /** lowercase, strip accents (č→c, ž→z, đ→d …) */
        fun norm(s: String): String =
            Normalizer.normalize(s, Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .replace('đ', 'd').replace('Đ', 'D')
                .lowercase()
    }
}
