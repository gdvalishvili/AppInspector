package com.gdvalishvili.appinspector.ui.background

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gdvalishvili.appinspector.data.background.BackgroundRepository
import com.gdvalishvili.appinspector.data.background.BackgroundSnapshot
import com.gdvalishvili.appinspector.model.AppEntry
import com.gdvalishvili.appinspector.model.AppProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BackgroundViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BackgroundRepository(application)

    private val _snapshot = MutableStateFlow<BackgroundSnapshot?>(null)
    val snapshot: StateFlow<BackgroundSnapshot?> = _snapshot

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private var job: Job? = null

    /** force = false: load only once (first open); ↻ passes true. */
    fun load(apps: List<AppEntry>, force: Boolean = false) {
        if (job?.isActive == true) return
        if (!force && _snapshot.value != null) return
        job = viewModelScope.launch {
            _loading.value = true
            try {
                val packages = apps.filter { it.profile == AppProfile.THIS }.map { it.packageName }
                _snapshot.value = repository.load(packages)
            } finally {
                _loading.value = false
            }
        }
    }
}