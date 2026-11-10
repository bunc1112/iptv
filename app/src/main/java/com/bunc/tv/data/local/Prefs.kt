package com.bunc.tv.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "bunc_prefs")

/** One saved login. type is "xtream" or "m3u". id identifies the profile. */
data class SavedAccount(
    val id: String = "",
    val type: String = "xtream",
    val name: String = "",
    val host: String = "",
    val user: String = "",
    val pass: String = "",
    val m3uUrl: String = ""
)

/**
 * Stores several profiles (users) and remembers the last one used.
 * Profiles are kept as a JSON array so an unlimited number can be saved.
 */
class Prefs(private val context: Context) {

    private val gson = Gson()

    private object Keys {
        val PROFILES = stringPreferencesKey("profiles_json")
        val LAST = stringPreferencesKey("last_profile_id")
    }

    suspend fun loadProfiles(): List<SavedAccount> {
        val json = context.dataStore.data.first()[Keys.PROFILES] ?: return emptyList()
        return try {
            gson.fromJson(json, Array<SavedAccount>::class.java)?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun saveProfiles(list: List<SavedAccount>) {
        context.dataStore.edit { it[Keys.PROFILES] = gson.toJson(list) }
    }

    /** Add a new profile (blank id → generated) or update an existing one. Returns the stored account. */
    suspend fun upsert(account: SavedAccount): SavedAccount {
        val stored = if (account.id.isBlank()) account.copy(id = UUID.randomUUID().toString()) else account
        val list = loadProfiles().toMutableList()
        val idx = list.indexOfFirst { it.id == stored.id }
        if (idx >= 0) list[idx] = stored else list.add(stored)
        saveProfiles(list)
        return stored
    }

    suspend fun delete(id: String) {
        saveProfiles(loadProfiles().filterNot { it.id == id })
        if (getLast() == id) setLast(null)
    }

    suspend fun getProfile(id: String): SavedAccount? = loadProfiles().firstOrNull { it.id == id }

    suspend fun setLast(id: String?) {
        context.dataStore.edit { p ->
            if (id == null) p.remove(Keys.LAST) else p[Keys.LAST] = id
        }
    }

    suspend fun getLast(): String? = context.dataStore.data.first()[Keys.LAST]
}
