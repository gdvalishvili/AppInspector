package com.gdvalishvili.appinspector.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gdvalishvili.appinspector.data.AppFilter
import com.gdvalishvili.appinspector.data.AppsSnapshot
import com.gdvalishvili.appinspector.data.InstalledAppsRepository
import com.gdvalishvili.appinspector.data.ListSettings
import com.gdvalishvili.appinspector.data.SettingsStore
import com.gdvalishvili.appinspector.data.SortOrder
import com.gdvalishvili.appinspector.data.WorkProfileState
import com.gdvalishvili.appinspector.data.isRunningInProfile
import com.gdvalishvili.appinspector.model.AppEntry
import com.gdvalishvili.appinspector.model.AppKind
import com.gdvalishvili.appinspector.model.AppProfile
import com.gdvalishvili.appinspector.model.UsageStatus
import com.gdvalishvili.appinspector.util.AppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

data class AppListUiState(
    val isLoading: Boolean = true,
    val hasData: Boolean = false,
    val apps: List<AppEntry> = emptyList(),
    val totalCount: Int = 0,
    val counts: Map<AppFilter, Int> = emptyMap(),
    val hasUsageAccess: Boolean = true,
    val workProfile: WorkProfileState = WorkProfileState.NONE,
    val settings: ListSettings = ListSettings(),
    val now: Long = System.currentTimeMillis(),
)

class AppListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = InstalledAppsRepository(application)
    private val settingsStore = SettingsStore(application)

    private val snapshot = MutableStateFlow<AppsSnapshot?>(null)
    private val loading = MutableStateFlow(false)
    private var loadJob: Job? = null

    /** Search text as Compose state, so the TextField stays in sync while typing. */
    var query by mutableStateOf("")
        private set

    val runningInProfile: Boolean = isRunningInProfile(application)

    /** null = not loaded yet (avoid flashing the language dialog). */
    val languageChosen: StateFlow<Boolean?> = settingsStore.languageChosen
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val uiState: StateFlow<AppListUiState> = combine(
        snapshot,
        settingsStore.settings,
        snapshotFlow { query },
        loading,
    ) { snap, settings, q, isLoading ->
        val all = snap?.apps.orEmpty()
        AppListUiState(
            isLoading = isLoading,
            hasData = snap != null,
            apps = filterAndSort(all, settings, q),
            totalCount = all.size,
            counts = AppFilter.entries.associateWith { f -> all.count { it.matches(f) } },
            hasUsageAccess = snap?.hasUsageAccess ?: true,
            workProfile = snap?.workProfile ?: WorkProfileState.NONE,
            settings = settings,
            now = System.currentTimeMillis(),
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppListUiState())

    /** Reloads everything from the system. Called on every return to the app. */
    fun refresh() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            loading.value = true
            try {
                snapshot.value = withContext(Dispatchers.IO) { repository.load() }
            } finally {
                loading.value = false
            }
        }
    }

    fun onQueryChange(value: String) {
        query = value
    }

    fun setFilter(value: AppFilter) {
        viewModelScope.launch { settingsStore.setFilter(value) }
    }

    fun setSort(value: SortOrder) {
        viewModelScope.launch { settingsStore.setSort(value) }
    }

    fun chooseLanguage(tag: String) {
        viewModelScope.launch {
            settingsStore.setLanguageChosen(true)
            AppLanguage.set(getApplication<Application>(), tag)
        }
    }
}

/** Which apps each filter chip shows. */
internal fun AppEntry.matches(filter: AppFilter): Boolean = when (filter) {
    AppFilter.ALL -> true
    AppFilter.USER -> profile == AppProfile.THIS && kind == AppKind.USER
    AppFilter.SYSTEM -> profile == AppProfile.THIS && kind != AppKind.USER
    AppFilter.WORK -> profile == AppProfile.WORK
}

/** SQL analogy: WHERE (filter + search) ORDER BY (selected sort), then name. */
internal fun filterAndSort(all: List<AppEntry>, s: ListSettings, query: String): List<AppEntry> {
    val needle = query.trim()
    val filtered = all.filter { app ->
        app.matches(s.filter) &&
                (needle.isEmpty() ||
                        app.label.contains(needle, ignoreCase = true) ||
                        app.packageName.contains(needle, ignoreCase = true))
    }

    val collator = Collator.getInstance(Locale.getDefault())
    val byName: Comparator<AppEntry> = compareBy(collator) { it.label }

    val comparator: Comparator<AppEntry> = when (s.sort) {
        SortOrder.NAME -> byName
        SortOrder.INSTALLED ->
            compareByDescending<AppEntry> { it.installedAt ?: Long.MIN_VALUE }.then(byName)
        SortOrder.UPDATED ->
            compareByDescending<AppEntry> { it.updatedAt ?: it.installedAt ?: Long.MIN_VALUE }
                .then(byName)
        SortOrder.LAST_USED ->
            // Apps with usage info first (oldest on top), apps without usage info at the end
            compareBy<AppEntry> { if (it.hasUsageInfo()) 0 else 1 }
                .thenBy { it.unusedSortKey() }
                .then(byName)
        SortOrder.RECENTLY_USED ->
            // Most recently used first; apps without a usage record go to the end
            compareByDescending<AppEntry> { it.lastUsedAt ?: Long.MIN_VALUE }.then(byName)
    }
    return filtered.sortedWith(comparator)
}

private fun AppEntry.hasUsageInfo(): Boolean = usageStatus == UsageStatus.USED ||
        usageStatus == UsageStatus.NEVER_OPENED ||
        usageStatus == UsageStatus.OLDER_THAN_HISTORY

private fun AppEntry.unusedSortKey(): Long = when (usageStatus) {
    UsageStatus.OLDER_THAN_HISTORY -> 0L
    UsageStatus.NEVER_OPENED -> installedAt ?: 0L
    UsageStatus.USED -> lastUsedAt ?: 0L
    else -> Long.MAX_VALUE
}