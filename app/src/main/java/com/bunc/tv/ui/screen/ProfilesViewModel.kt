package com.bunc.tv.ui.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bunc.tv.data.ServiceLocator
import com.bunc.tv.data.local.SavedAccount
import kotlinx.coroutines.launch

class ProfilesViewModel : ViewModel() {

    var profiles by mutableStateOf<List<SavedAccount>>(emptyList())
        private set
    var connecting by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init { load() }

    fun load() {
        viewModelScope.launch {
            profiles = ServiceLocator.prefs.loadProfiles()
        }
    }

    /** Connect with a saved profile, then continue. */
    fun use(profile: SavedAccount, onDone: () -> Unit) {
        if (connecting) return
        connecting = true
        error = null
        viewModelScope.launch {
            val res = ServiceLocator.repo.restore(profile)
            connecting = false
            res.onSuccess {
                ServiceLocator.prefs.setLast(profile.id)
                onDone()
            }.onFailure {
                error = "Couldn't connect to ${profile.name.ifBlank { "profile" }}"
            }
        }
    }

    fun delete(profile: SavedAccount) {
        viewModelScope.launch {
            ServiceLocator.prefs.delete(profile.id)
            load()
        }
    }
}
