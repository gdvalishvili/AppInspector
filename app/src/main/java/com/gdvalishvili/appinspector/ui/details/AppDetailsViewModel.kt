package com.gdvalishvili.appinspector.ui.details

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gdvalishvili.appinspector.data.AppDetailsExtras
import com.gdvalishvili.appinspector.data.AppDetailsRepository
import com.gdvalishvili.appinspector.model.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppDetailsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppDetailsRepository(application)
    private val _extras = MutableStateFlow<AppDetailsExtras?>(null)
    val extras: StateFlow<AppDetailsExtras?> = _extras
    private var job: Job? = null

    fun load(app: AppEntry) {
        job?.cancel()
        job = viewModelScope.launch {
            _extras.value = withContext(Dispatchers.IO) { repository.load(app) }
        }
    }
}