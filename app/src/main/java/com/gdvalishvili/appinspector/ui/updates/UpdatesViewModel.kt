package com.gdvalishvili.appinspector.ui.updates

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gdvalishvili.appinspector.data.PeriodChange
import com.gdvalishvili.appinspector.data.UpdatesPrefs
import com.gdvalishvili.appinspector.data.UpdatesSort
import com.gdvalishvili.appinspector.db.AppDatabase
import com.gdvalishvili.appinspector.db.EventType
import com.gdvalishvili.appinspector.model.AppProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Updates page state: last 7 days journal + saved sort order. */
class UpdatesViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.get(application).usageDao()
    private val prefs = UpdatesPrefs(application)

    private val _recent = MutableStateFlow<List<PeriodChange>>(emptyList())
    val recent: StateFlow<List<PeriodChange>> = _recent

    val sort: StateFlow<UpdatesSort> = prefs.sort
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UpdatesSort.OLDEST_FIRST)

    fun setSort(sort: UpdatesSort) {
        viewModelScope.launch { prefs.setSort(sort) }
    }

    /** Every update separately (journal), newest first. */
    fun load() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val from = now - TimeUnit.DAYS.toMillis(7)
            _recent.value = withContext(Dispatchers.IO) {
                dao.eventsBetween(from, now + 1)
                    .filter { it.type == EventType.UPDATED && it.profile == AppProfile.THIS.name }
                    .map { PeriodChange(it.type, it.packageName, it.label, it.version, it.time, it.approx) }
                    .sortedByDescending { it.time }
            }
        }
    }
}