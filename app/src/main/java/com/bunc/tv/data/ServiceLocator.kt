package com.bunc.tv.data

import android.content.Context
import com.bunc.tv.data.local.Prefs

/** Tiny manual DI so the MVP stays free of a DI framework. */
object ServiceLocator {
    lateinit var prefs: Prefs
        private set

    val repo = Repository

    fun init(context: Context) {
        prefs = Prefs(context.applicationContext)
    }
}

/**
 * Holds the current playback list so the player can zap up/down a channel
 * without leaving the player. For movies/episodes the list has a single item.
 */
object PlaybackQueue {
    /** streamId is set for Xtream live channels (used for the EPG); logo is shown in the guide. */
    data class Item(
        val title: String,
        val url: String,
        val streamId: Int? = null,
        val logo: String? = null
    )

    @Volatile var items: List<Item> = emptyList()
    @Volatile var index: Int = 0
    /** True for live TV (reconnects to the live edge); false for movies/episodes (resume in place). */
    @Volatile var live: Boolean = false

    /** A single movie or episode. */
    fun setSingle(url: String, title: String) {
        items = listOf(Item(title, url)); index = 0; live = false
    }

    /** A list of live channels the player can zap through. */
    fun setList(list: List<Item>, startIndex: Int) {
        items = list
        index = startIndex.coerceIn(0, maxOf(0, list.size - 1))
        live = true
    }

    fun current(): Item? = items.getOrNull(index)
}

/** Carries the series title into the Episodes screen without stuffing it in a route. */
object NavState {
    @Volatile var seriesTitle: String = ""

    /** Channels the TV Guide should show (empty = all live channels) and which one to start on. */
    @Volatile var guideChannels: List<PlaybackQueue.Item> = emptyList()
    @Volatile var guideStart: Int = 0
}
