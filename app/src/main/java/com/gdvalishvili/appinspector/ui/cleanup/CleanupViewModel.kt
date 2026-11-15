package com.gdvalishvili.appinspector.ui.cleanup

import android.app.Application
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gdvalishvili.appinspector.data.CleanupPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** State of a batch uninstall: one system dialog per app, in order. */
data class UninstallRun(
    val queue: List<String>,
    val index: Int = 0,
    val removed: Int = 0,
    val skipped: Int = 0,
    val freedBytes: Long = 0L,
    val stopRequested: Boolean = false,
    val waitingForDialog: Boolean = true,
    val launchedIndex: Int = -1,   // guards against re-opening the dialog after rotation
    val finished: Boolean = false,
) {
    val current: String? get() = if (!finished && waitingForDialog) queue.getOrNull(index) else null
}

class CleanupViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = CleanupPrefs(application)
    private val pm = application.packageManager
    private val storageStats = application.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager

    /** null until loaded, so excluded apps never flash into other groups. */
    val excluded: StateFlow<Set<String>?> = prefs.excluded
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _sizes = MutableStateFlow<Map<String, Long>>(emptyMap())
    val sizes: StateFlow<Map<String, Long>> = _sizes

    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected

    private val _run = MutableStateFlow<UninstallRun?>(null)
    val run: StateFlow<UninstallRun?> = _run

    fun setExcluded(packageName: String, value: Boolean) {
        viewModelScope.launch { prefs.setExcluded(packageName, value) }
        if (value) _selected.update { it - packageName }
    }

    fun toggle(packageName: String) {
        _selected.update { if (packageName in it) it - packageName else it + packageName }
    }

    fun setSelection(packages: Set<String>) {
        _selected.value = packages
    }

    /** App + data size (cache not counted, like the main list). */
    fun loadSizes(packages: List<String>) {
        val missing = packages.filter { it !in _sizes.value }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val computed = withContext(Dispatchers.IO) {
                missing.mapNotNull { pkg -> sizeOf(pkg)?.let { pkg to it } }.toMap()
            }
            _sizes.update { it + computed }
        }
    }

    private fun sizeOf(packageName: String): Long? = try {
        val s = storageStats.queryStatsForPackage(StorageManager.UUID_DEFAULT, packageName, Process.myUserHandle())
        s.appBytes + s.dataBytes
    } catch (e: Exception) {
        null
    }

    fun startUninstall() {
        val queue = _selected.value.toList()
        if (queue.isNotEmpty()) _run.value = UninstallRun(queue)
    }

    fun markLaunched(index: Int) {
        _run.update { it?.copy(launchedIndex = index) }
    }

    fun requestStop() {
        _run.update { it?.copy(stopRequested = true) }
    }

    /** Called when the system dialog closes: check whether the app is really gone, then move on. */
    fun onDialogReturned() {
        val run = _run.value ?: return
        if (!run.waitingForDialog) return
        val pkg = run.queue.getOrNull(run.index) ?: return
        _run.value = run.copy(waitingForDialog = false)

        viewModelScope.launch {
            val removed = withContext(Dispatchers.IO) { waitUntilRemoved(pkg) }
            val r = _run.value ?: return@launch
            val next = r.index + 1
            val done = next >= r.queue.size || r.stopRequested
            _run.value = r.copy(
                index = next,
                removed = r.removed + if (removed) 1 else 0,
                skipped = r.skipped + if (removed) 0 else 1,
                freedBytes = r.freedBytes + if (removed) (_sizes.value[pkg] ?: 0L) else 0L,
                waitingForDialog = !done,
                finished = done,
            )
            if (removed) _selected.update { it - pkg }
        }
    }

    fun finishRun() {
        _run.value = null
        _selected.value = emptySet()
    }

    /** Uninstall may finish a moment after the dialog closes: check a few times. */
    private suspend fun waitUntilRemoved(packageName: String): Boolean {
        repeat(4) {
            if (!isInstalled(packageName)) return true
            delay(300)
        }
        return !isInstalled(packageName)
    }

    private fun isInstalled(packageName: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, 0)
        }
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}