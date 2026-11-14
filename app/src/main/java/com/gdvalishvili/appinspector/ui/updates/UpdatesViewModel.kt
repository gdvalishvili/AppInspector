package com.gdvalishvili.appinspector.ui.updates

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gdvalishvili.appinspector.data.PeriodChange
import com.gdvalishvili.appinspector.db.AppDatabase
import com.gdvalishvili.appinspector.db.EventType
import com.gdvalishvili.appinspector.model.AppProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Updates of the last 7 days, from our own journal (app_event). */
class UpdatesViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.get(application).usageDao()
    private val _recent = MutableStateFlow<List<PeriodChange>>(emptyList())
    val recent: StateFlow<List<PeriodChange>> = _recent

    fun load() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val from = now - TimeUnit.DAYS.toMillis(7)
            _recent.value = withContext(Dispatchers.IO) {
                dao.eventsBetween(from, now + 1)
                    .filter { it.type == EventType.UPDATED && it.profile == AppProfile.THIS.name }
                    .map { PeriodChange(it.type, it.packageName, it.label, it.version, it.time, it.approx) }
            }
        }
    }
}