package com.gdvalishvili.appinspector.ui.privacy

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gdvalishvili.appinspector.data.privacy.PrivacyRepository
import com.gdvalishvili.appinspector.data.privacy.PrivacyResult
import com.gdvalishvili.appinspector.model.AppEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Activity-scoped: a running scan continues when the user leaves the screen. */
class PrivacyViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PrivacyRepository(application)

    /** AppEntry.key -> result. null = cache not read yet. */
    private val _results = MutableStateFlow<Map<String, PrivacyResult>?>(null)
    val results: StateFlow<Map<String, PrivacyResult>?> = _results

    /** null = not scanning; otherwise (done, total). */
    private val _progress = MutableStateFlow<Pair<Int, Int>?>(null)
    val progress: StateFlow<Pair<Int, Int>?> = _progress

    private var job: Job? = null

    /** Fast: only cached results (no APK reading). */
    fun loadCached(apps: List<AppEntry>) {
        if (job?.isActive == true || _results.value != null) return
        job = viewModelScope.launch { _results.value = repository.cached(apps) }
    }

    fun scan(apps: List<AppEntry>) {
        if (_progress.value != null) return
        job?.cancel()
        job = viewModelScope.launch {
            _progress.value = 0 to apps.size
            try {
                _results.value = repository.scan(apps) { done, total -> _progress.value = done to total }
            } finally {
                _progress.value = null
            }
        }
    }
}