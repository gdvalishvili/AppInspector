package com.gdvalishvili.appinspector.data

import android.content.Context
import com.gdvalishvili.appinspector.db.AppDatabase
import com.gdvalishvili.appinspector.db.AppEventEntity
import com.gdvalishvili.appinspector.db.AppStateEntity
import com.gdvalishvili.appinspector.db.EventType
import com.gdvalishvili.appinspector.model.AppEntry
import com.gdvalishvili.appinspector.model.AppProfile
import com.gdvalishvili.appinspector.model.RemovedApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Compares the current app list with the last known state (app_state)
 * and writes install / update / removal events (app_event).
 */
class AppHistoryRepository(context: Context) {

    private val dao = AppDatabase.get(context).appDao()

    val removals: Flow<List<RemovedApp>> = dao.latestRemovals().map { list ->
        list.map { it.toRemovedApp() }
    }

    suspend fun sync(current: List<AppEntry>, workProfile: WorkProfileState) {
        val now = System.currentTimeMillis()
        val stored = dao.allStates().associateBy { it.packageName to it.profile }
        // First run: just remember the current state, do not flood the journal
        val firstRun = stored.isEmpty()

        val currentKeys = HashSet<Pair<String, String>>()
        val upserts = mutableListOf<AppStateEntity>()
        val events = mutableListOf<AppEventEntity>()

        for (app in current) {
            val key = app.packageName to app.profile.name
            currentKeys += key
            val newState = AppStateEntity(
                packageName = app.packageName,
                profile = app.profile.name,
                label = app.label,
                version = app.versionName,
                installedAt = app.installedAt,
                updatedAt = app.updatedAt,
            )
            val old = stored[key]
            val versionChanged = newState.version != null && newState.version != old?.version
            val updateTimeChanged = newState.updatedAt != null && newState.updatedAt != old?.updatedAt

            when {
                old == null -> {
                    upserts += newState
                    if (!firstRun) {
                        events += newState.toEvent(
                            type = EventType.INSTALLED,
                            time = app.installedAt ?: now,
                            approx = app.installedAt == null,
                        )
                    }
                }
                versionChanged || updateTimeChanged -> {
                    upserts += newState
                    events += newState.toEvent(
                        type = EventType.UPDATED,
                        time = app.updatedAt ?: now,
                        approx = app.updatedAt == null || app.updatedAtApprox,
                    )
                }
                old.label != newState.label -> upserts += newState
            }
        }

        // Missing apps = removed. A paused/unavailable work profile is NOT a removal.
        val deletes = stored
            .filterKeys { it !in currentKeys }
            .values
            .filter { it.profile != AppProfile.WORK.name || workProfile == WorkProfileState.ACTIVE }
        deletes.forEach { events += it.toEvent(EventType.REMOVED, time = now, approx = true) }

        if (upserts.isNotEmpty() || deletes.isNotEmpty() || events.isNotEmpty()) {
            dao.applySync(upserts, deletes, events)
        }
    }

    /** Called by the system broadcast: exact removal time for apps in this profile. */
    suspend fun recordRemoval(packageName: String) {
        val state = dao.state(packageName, AppProfile.THIS.name) ?: return
        val event = state.toEvent(EventType.REMOVED, time = System.currentTimeMillis(), approx = false)
        dao.applySync(upserts = emptyList(), deletes = listOf(state), events = listOf(event))
    }

    private fun AppStateEntity.toEvent(type: String, time: Long, approx: Boolean) = AppEventEntity(
        time = time,
        packageName = packageName,
        profile = profile,
        label = label,
        version = version,
        installedAt = installedAt,
        type = type,
        approx = approx,
    )

    private fun AppEventEntity.toRemovedApp(): RemovedApp {
        val appProfile = AppProfile.entries.firstOrNull { it.name == profile } ?: AppProfile.THIS
        return RemovedApp(
            key = "${appProfile.name}:$packageName",
            packageName = packageName,
            label = label,
            versionName = version,
            profile = appProfile,
            installedAt = installedAt,
            removedAt = time,
            removedAtApprox = approx,
        )
    }
}