package com.bunc.tv.ui.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bunc.tv.data.ServiceLocator
import kotlinx.coroutines.launch

class LoginViewModel : ViewModel() {
    private val repo = ServiceLocator.repo

    var mode by mutableStateOf("xtream")   // "xtream" | "m3u"
    var name by mutableStateOf("")
    var host by mutableStateOf("")
    var user by mutableStateOf("")
    var pass by mutableStateOf("")
    var m3u by mutableStateOf("")
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
        private set

    fun connect(onDone: () -> Unit) {
        if (loading) return
        loading = true
        error = null
        viewModelScope.launch {
            val res = if (mode == "xtream") {
                repo.connectXtream(host, user, pass, name)
            } else {
                repo.connectM3u(m3u, name)
            }
            loading = false
            res.onSuccess {
                // Save as a profile and mark it the current one.
                repo.toSaved()?.let { saved ->
                    val stored = ServiceLocator.prefs.upsert(saved)
                    ServiceLocator.prefs.setLast(stored.id)
                }
                onDone()
            }.onFailure {
                error = it.message ?: "Connection failed"
            }
        }
    }
}
