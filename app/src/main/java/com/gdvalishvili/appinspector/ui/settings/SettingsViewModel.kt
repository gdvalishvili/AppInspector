package com.gdvalishvili.appinspector.ui.settings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gdvalishvili.appinspector.data.ReportSettings
import com.gdvalishvili.appinspector.data.SettingsStore
import com.gdvalishvili.appinspector.data.UsageRepository
import com.gdvalishvili.appinspector.notify.ReportNotifier
import com.gdvalishvili.appinspector.work.DailyWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SettingsStore(application)
    private val usage = UsageRepository(application)

    val settings: StateFlow<ReportSettings?> = store.reportSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var hasUsageAccess by mutableStateOf(usage.hasAccess())
        private set

    @Suppress("DEPRECATION")
    val versionName: String = application.packageManager
        .getPackageInfo(application.packageName, 0).versionName.orEmpty()

    fun refreshAccess() {
        hasUsageAccess = usage.hasAccess()
    }

    fun setDaily(value: Boolean) {
        viewModelScope.launch { store.setReportDaily(value) }
    }

    fun setWeekly(value: Boolean) {
        viewModelScope.launch { store.setReportWeekly(value) }
    }

    fun setMonthly(value: Boolean) {
        viewModelScope.launch { store.setReportMonthly(value) }
    }

    fun setTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            store.setReportTime(hour, minute)
            DailyWorker.schedule(getApplication(), hour, minute, replace = true)
        }
    }

    fun setRetention(years: Int) {
        viewModelScope.launch { store.setRetentionYears(years) }
    }

    fun sendNow() {
        viewModelScope.launch(Dispatchers.IO) {
            ReportNotifier.sendDueReports(getApplication(), force = true)
        }
    }
}