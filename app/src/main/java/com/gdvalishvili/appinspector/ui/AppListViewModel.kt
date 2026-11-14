package com.gdvalishvili.appinspector.ui

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gdvalishvili.appinspector.data.AppFilter
import com.gdvalishvili.appinspector.data.AppHistoryRepository
import com.gdvalishvili.appinspector.data.AppsSnapshot
import com.gdvalishvili.appinspector.data.InstalledAppsRepository
import com.gdvalishvili.appinspector.data.ListSettings
import com.gdvalishvili.appinspector.data.SettingsStore
import com.gdvalishvili.appinspector.data.SizeRepository
import com.gdvalishvili.appinspector.data.SortOrder
import com.gdvalishvili.appinspector.data.UsageCollector
import com.gdvalishvili.appinspector.data.UsageRepository
import com.gdvalishvili.appinspector.data.WorkProfileState
import com.gdvalishvili.appinspector.data.isRunningInProfile
import com.gdvalishvili.appinspector.db.AppDatabase
import com.gdvalishvili.appinspector.model.AppEntry
import com.gdvalishvili.appinspector.model.AppKind
import com.gdvalishvili.appinspector.model.AppProfile
import com.gdvalishvili.appinspector.model.AppSize
import com.gdvalishvili.appinspector.model.RemovedApp
import com.gdvalishvili.appinspector.model.UsageStatus
import com.gdvalishvili.appinspector.util.AppLanguage
import com.gdvalishvili.appinspector.work.DailyWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.flow.map

data class AppListUiState(
    val isLoading: Boolean = true,
    val hasData: Boolean = false,
    val apps: List<AppEntry> = emptyList(),
    val removed: List<RemovedApp> = emptyList(),
    val totalCount: Int = 0,
    val counts: Map<AppFilter, Int> = emptyMap(),
    val categoryCounts: Map<Int, Int> = emptyMap(),
    val hasUsageAccess: Boolean = true,
    val workProfile: WorkProfileState = WorkProfileState.NONE,
    val settings: ListSettings = ListSettings(),
    val weekUsage: Map<String, Long> = emptyMap(),   // packageName -> ms (this profile, last 7 days)
    val sizes: Map<String, AppSize>? = null,          // AppEntry.key -> size; null = not loaded yet
    val now: Long = System.currentTimeMillis(),
)

class AppListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = InstalledAppsRepository(application)
    private val history = AppHistoryRepository(application)
    private val settingsStore = SettingsStore(application)
    private val usageCollector = UsageCollector(application)
    private val usageAccess = UsageRepository(application)
    private val usageDao = AppDatabase.get(application).usageDao()
    private val sizeRepository = SizeRepository(application)

    private val snapshot = MutableStateFlow<AppsSnapshot?>(null)
    private val loading = MutableStateFlow(false)
    private val weekUsage = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val sizes = MutableStateFlow<Map<String, AppSize>?>(null)
    private var loadJob: Job? = null
    private var sizeJob: Job? = null

    /** Search text as Compose state, so the TextField stays in sync while typing. */
    var query by mutableStateOf("")
        private set

    val runningInProfile: Boolean = isRunningInProfile(application)

    /** All loaded apps (unfiltered); used by the details screen to find an app by key. */
    val allApps: StateFlow<List<AppEntry>?> = snapshot
        .map { it?.apps }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** null = not loaded yet (avoid flashing the language dialog). */
    val languageChosen: StateFlow<Boolean?> = settingsStore.languageChosen
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // Make sure the daily job exists, at the chosen time (no-op if already scheduled)
        viewModelScope.launch {
            val rs = settingsStore.reportSettingsNow()
            DailyWorker.schedule(application, rs.hour, rs.minute, replace = false)
        }
    }

    // Small helper flow: removed apps + extra per-app data (combine accepts at most 5 flows)
    private val extras = combine(history.removals, weekUsage, sizes) { removals, week, sizeMap ->
        Triple(removals, week, sizeMap)
    }

    val uiState: StateFlow<AppListUiState> = combine(
        snapshot,
        settingsStore.settings,
        snapshotFlow { query },
        loading,
        extras,
    ) { snap, settings, q, isLoading, (removals, week, sizeMap) ->
        val all = snap?.apps.orEmpty()
        // Hide "removed" entries for apps that were installed again
        val installedKeys = all.mapTo(HashSet()) { it.key }
        val removed = removals.filter { it.key !in installedKeys }
        val showRemoved = settings.filter == AppFilter.REMOVED
        val needle = q.trim()

        AppListUiState(
            isLoading = isLoading,
            hasData = snap != null,
            apps = if (showRemoved) emptyList()
            else filterAndSort(all, settings, q, week, sizeMap.orEmpty()),
            removed = if (showRemoved) removed.filter { it.matchesQuery(needle) } else emptyList(),
            totalCount = all.size,
            counts = AppFilter.entries.associateWith { f ->
                if (f == AppFilter.REMOVED) removed.size else all.count { it.matches(f) }
            },
            categoryCounts = all.filter { it.matches(settings.filter) }
                .groupingBy { it.category }
                .eachCount(),
            hasUsageAccess = snap?.hasUsageAccess ?: true,
            workProfile = snap?.workProfile ?: WorkProfileState.NONE,
            settings = settings,
            weekUsage = week,
            sizes = sizeMap,
            now = System.currentTimeMillis(),
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppListUiState())

    /** Reloads from the system, updates history and 7-day usage. Called on every return to the app. */
    fun refresh() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            loading.value = true
            try {
                val snap = withContext(Dispatchers.IO) { repository.load() }
                snapshot.value = snap
                withContext(Dispatchers.IO) {
                    try {
                        history.sync(snap.apps, snap.workProfile)
                        // Catch up on missing days even if the daily job was delayed
                        usageCollector.collectMissingDays()
                        weekUsage.value = loadWeekUsage()
                    } catch (e: Exception) {
                        // History/usage must never break the main list
                        Log.e("AppInspector", "History/usage update failed", e)
                    }
                }
                if (settingsStore.settings.first().showSize) loadSizes()
            } finally {
                loading.value = false
            }
        }
    }

    /** Last 7 days incl. today (today counted live). Empty without Usage access. */
    private suspend fun loadWeekUsage(): Map<String, Long> {
        if (!usageAccess.hasAccess()) return emptyMap()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val totals = HashMap<String, Long>()
        usageDao.totalsByPackage(today.minusDays(6).toString(), today.minusDays(1).toString())
            .forEach { totals[it.packageName] = it.ms }
        usageCollector.foregroundTimes(today, zone).forEach { (pkg, ms) ->
            totals[pkg] = (totals[pkg] ?: 0L) + ms
        }
        return totals
    }

    /** Computes sizes in the background (a few seconds); the list shows "Loading…" meanwhile. */
    private fun loadSizes() {
        val apps = snapshot.value?.apps ?: return
        sizeJob?.cancel()
        sizeJob = viewModelScope.launch {
            sizes.value = withContext(Dispatchers.IO) { sizeRepository.sizesOf(apps) }
        }
    }

    fun onQueryChange(value: String) {
        query = value
    }

    fun setFilter(value: AppFilter) {
        viewModelScope.launch { settingsStore.setFilter(value) }
    }

    fun setSort(value: SortOrder) {
        viewModelScope.launch {
            settingsStore.setSort(value)
            // Sorting by size needs sizes
            if (value == SortOrder.SIZE) {
                settingsStore.setShowSize(true)
                if (sizes.value == null) loadSizes()
            }
        }
    }

    fun setShowCategory(value: Boolean) {
        viewModelScope.launch { settingsStore.setShowCategory(value) }
    }

    fun setShowSource(value: Boolean) {
        viewModelScope.launch { settingsStore.setShowSource(value) }
    }

    fun setShowSize(value: Boolean) {
        viewModelScope.launch {
            settingsStore.setShowSize(value)
            if (value && sizes.value == null) loadSizes()
        }
    }

    fun setShowDescription(value: Boolean) {
        viewModelScope.launch { settingsStore.setShowDescription(value) }
    }

    fun setCategory(value: Int?) {
        viewModelScope.launch { settingsStore.setCategory(value) }
    }

    fun chooseLanguage(tag: String) {
        viewModelScope.launch {
            settingsStore.setLanguageChosen(true)
            AppLanguage.set(getApplication<Application>(), tag)
        }
    }
}

/** Which installed apps each filter chip shows (REMOVED uses the journal instead). */
internal fun AppEntry.matches(filter: AppFilter): Boolean = when (filter) {
    AppFilter.ALL -> true
    AppFilter.USER -> profile == AppProfile.THIS && kind == AppKind.USER
    AppFilter.SYSTEM -> profile == AppProfile.THIS && kind != AppKind.USER
    AppFilter.WORK -> profile == AppProfile.WORK
    AppFilter.REMOVED -> false
}

private fun RemovedApp.matchesQuery(needle: String): Boolean =
    needle.isEmpty() ||
            label.contains(needle, ignoreCase = true) ||
            packageName.contains(needle, ignoreCase = true)

/** SQL analogy: WHERE (filter + category + search) ORDER BY (selected sort), then name. */
internal fun filterAndSort(
    all: List<AppEntry>,
    s: ListSettings,
    query: String,
    weekUsage: Map<String, Long> = emptyMap(),
    sizes: Map<String, AppSize> = emptyMap(),
): List<AppEntry> {
    val needle = query.trim()
    val filtered = all.filter { app ->
        app.matches(s.filter) &&
                (!s.showCategory || s.category == null || app.category == s.category) &&
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
            compareByDescending<AppEntry> { it.lastUsedAt ?: Long.MIN_VALUE }.then(byName)
        SortOrder.MOST_USED ->
            // Work apps have no usage data -> at the end
            compareByDescending<AppEntry> { app ->
                if (app.profile == AppProfile.THIS) weekUsage[app.packageName] ?: 0L else -1L
            }.then(byName)
        SortOrder.SIZE ->
            compareByDescending<AppEntry> { app -> sizes[app.key]?.total ?: -1L }.then(byName)
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