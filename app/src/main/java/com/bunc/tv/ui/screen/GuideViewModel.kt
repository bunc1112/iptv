package com.bunc.tv.ui.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bunc.tv.data.NavState
import com.bunc.tv.data.PlaybackQueue
import com.bunc.tv.data.ServiceLocator
import com.bunc.tv.data.model.Program
import com.bunc.tv.data.model.Section
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** TV Guide: a list of channels and the full schedule of the selected one. */
class GuideViewModel : ViewModel() {
    private val repo = ServiceLocator.repo

    var channels by mutableStateOf<List<PlaybackQueue.Item>>(emptyList())
        private set
    var selected by mutableIntStateOf(0)
        private set
    /** null while loading */
    var programs by mutableStateOf<List<Program>?>(null)
        private set
    var loadingChannels by mutableStateOf(true)
        private set

    private var scheduleJob: Job? = null

    init {
        val preset = NavState.guideChannels
        if (preset.isNotEmpty()) {
            channels = preset
            selected = NavState.guideStart.coerceIn(0, preset.lastIndex)
            loadingChannels = false
            loadSchedule(immediate = true)
        } else {
            viewModelScope.launch {
                channels = try {
                    repo.content(Section.LIVE, "").map {
                        PlaybackQueue.Item(it.name, repo.liveUrl(it.id), it.id, it.logo)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList()
                }
                selected = 0
                loadingChannels = false
                loadSchedule(immediate = true)
            }
        }
    }

    fun select(index: Int) {
        if (index == selected || index !in channels.indices) return
        selected = index
        loadSchedule(immediate = false)
    }

    private fun loadSchedule(immediate: Boolean) {
        scheduleJob?.cancel()
        programs = null
        val id = channels.getOrNull(selected)?.streamId
        scheduleJob = viewModelScope.launch {
            // While the remote is scrolling through channels, wait for it to settle.
            if (!immediate) delay(300)
            programs = if (id == null) emptyList() else try {
                repo.fullEpg(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
