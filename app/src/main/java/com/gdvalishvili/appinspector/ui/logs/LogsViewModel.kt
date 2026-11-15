package com.gdvalishvili.appinspector.ui.logs

import android.app.Application
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gdvalishvili.appinspector.data.logs.CrashEntry
import com.gdvalishvili.appinspector.data.logs.DropBoxReader
import com.gdvalishvili.appinspector.data.logs.LogLine
import com.gdvalishvili.appinspector.data.logs.LogSnapshots
import com.gdvalishvili.appinspector.data.logs.LogcatReader
import com.gdvalishvili.appinspector.data.logs.LogsPrefs
import com.gdvalishvili.appinspector.data.logs.SnapshotInfo
import com.gdvalishvili.appinspector.model.AppEntry
import com.gdvalishvili.appinspector.model.AppProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LogsUiState(
    val loading: Boolean = true,
    val lines: List<LogLine> = emptyList(),
    val loadedAt: Long = System.currentTimeMillis(),
    val ownOnly: Boolean = false,
    val error: String? = null,
    val uidMissing: Boolean = false,
    val viewing: SnapshotInfo? = null,          // non-null = showing a saved log
    val crashes: List<CrashEntry>? = null,      // null = loading
    val crashesNoPermission: Boolean = false,
    val snapshots: List<SnapshotInfo> = emptyList(),
    val readLogsGranted: Boolean = false,
)

class LogsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val logcat = LogcatReader()
    private val dropBox = DropBoxReader(application)
    private val snapshotStore = LogSnapshots(application)
    private val prefs = LogsPrefs(application)

    private val _state = MutableStateFlow(LogsUiState())
    val state: StateFlow<LogsUiState> = _state

    val autoDelete: StateFlow<Boolean> = prefs.autoDelete
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private var scopeKey: String? = null
    private var packageName: String? = null
    private var uid: Int? = null

    /** target == null: system-wide. Same scope again: keep what is loaded (↻ reloads). */
    fun init(target: AppEntry?) {
        val key = target?.key ?: SYSTEM_KEY
        if (key == scopeKey) {
            _state.update { it.copy(readLogsGranted = readLogsGranted()) }
            return
        }
        scopeKey = key
        packageName = target?.packageName
        uid = target?.let { uidOf(it) }
        _state.value = LogsUiState(uidMissing = target != null && uid == null, readLogsGranted = readLogsGranted())
        refresh()
        loadCrashes()
        loadSnapshots()
    }

    fun refresh() {
        _state.update { it.copy(readLogsGranted = readLogsGranted()) }
        if (_state.value.uidMissing) {
            _state.update { it.copy(loading = false) }
            return
        }
        _state.update { it.copy(loading = true, viewing = null, error = null) }
        viewModelScope.launch {
            val r = logcat.read(uid)
            _state.update {
                it.copy(
                    loading = false,
                    lines = r.lines,
                    loadedAt = System.currentTimeMillis(),
                    ownOnly = r.ownOnly,
                    error = r.error,
                )
            }
        }
    }

    fun loadCrashes() {
        _state.update { it.copy(crashes = null) }
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { dropBox.read(packageName) }
            _state.update { it.copy(crashes = r.entries, crashesNoPermission = r.noPermission) }
        }
    }

    private fun loadSnapshots() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { snapshotStore.list(packageName) }
            _state.update { it.copy(snapshots = list) }
        }
    }

    /** Saves the full live buffer (not only the filtered lines), so filters still work later. */
    suspend fun saveSnapshot(): Boolean {
        val s = _state.value
        if (s.viewing != null || s.lines.isEmpty()) return false
        val keep = if (autoDelete.value) MAX_SNAPSHOTS else null
        withContext(Dispatchers.IO) { snapshotStore.save(s.lines, packageName, keep) }
        loadSnapshots()
        return true
    }

    fun openSnapshot(snapshot: SnapshotInfo) {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val lines = withContext(Dispatchers.IO) { snapshotStore.load(snapshot) }
            _state.update {
                it.copy(loading = false, lines = lines, loadedAt = snapshot.createdAt, viewing = snapshot, ownOnly = false, error = null)
            }
        }
    }

    fun deleteSnapshot(snapshot: SnapshotInfo) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { snapshotStore.delete(snapshot) }
            if (_state.value.viewing == snapshot) refresh()
            loadSnapshots()
        }
    }

    fun setAutoDelete(value: Boolean) {
        viewModelScope.launch { prefs.setAutoDelete(value) }
    }

    private fun readLogsGranted() =
        app.checkSelfPermission(DropBoxReader.READ_LOGS) == PackageManager.PERMISSION_GRANTED

    /** Work profile apps have a different UID than the same app in the personal profile. */
    private fun uidOf(entry: AppEntry): Int? = try {
        if (entry.profile == AppProfile.WORK) {
            val user = entry.user ?: return null
            app.getSystemService(LauncherApps::class.java).getApplicationInfo(entry.packageName, 0, user).uid
        } else if (Build.VERSION.SDK_INT >= 33) {
            app.packageManager.getApplicationInfo(entry.packageName, PackageManager.ApplicationInfoFlags.of(0)).uid
        } else {
            @Suppress("DEPRECATION")
            app.packageManager.getApplicationInfo(entry.packageName, 0).uid
        }
    } catch (e: Exception) {
        null
    }

    companion object {
        const val MAX_SNAPSHOTS = 20
        private const val SYSTEM_KEY = "SYSTEM"
    }
}