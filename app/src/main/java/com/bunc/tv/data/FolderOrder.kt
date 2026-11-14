package com.bunc.tv.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.bunc.tv.data.model.Category
import com.bunc.tv.data.model.Section
import java.text.Collator

/**
 * Applies the user's folder sorting (server order, A–Z, or custom numbered order)
 * to a category list. "All" always stays first.
 */
object FolderOrder {

    /** Bumped whenever sorting settings change, so Home re-sorts its folder list. */
    var version by mutableIntStateOf(0)

    const val SERVER = "server"
    const val AZ = "az"
    const val CUSTOM = "custom"

    private val collator: Collator = Collator.getInstance().apply { strength = Collator.PRIMARY }

    suspend fun profileKey(): String = ServiceLocator.prefs.getLast() ?: "default"

    fun alphabetical(list: List<Category>): List<Category> =
        list.sortedWith { a, b -> collator.compare(a.name, b.name) }

    /** Put folders in the saved custom order; folders not in it keep server order at the end. */
    fun byCustomOrder(list: List<Category>, order: List<String>): List<Category> {
        val pos = order.withIndex().associate { it.value to it.index }
        return list.sortedBy { pos[it.id] ?: Int.MAX_VALUE }
    }

    suspend fun apply(section: Section, categories: List<Category>): List<Category> {
        val all = categories.filter { it.id.isBlank() }
        val rest = categories.filter { it.id.isNotBlank() }
        val key = profileKey()
        val prefs = ServiceLocator.prefs
        val sorted = when (prefs.getSortMode(key, section.name)) {
            AZ -> alphabetical(rest)
            CUSTOM -> byCustomOrder(rest, prefs.getCustomOrder(key, section.name))
            else -> rest
        }
        return all + sorted
    }
}
