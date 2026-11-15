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

/** Result of comparing the current app list with the last known state. */
internal data class SyncDiff(
    val upserts: List<AppStateEntity>,
    val deletes: List<AppStateEntity>,
    val events: List<AppEventEntity>,
) {
    val isEmpty: Boolean get() = upserts.isEmpty() && deletes.isEmpty() && events.isEmpty()
}

/**
 * Pure function (unit-tested): what changed between [stored] state and [current] apps.
 * First run (nothing stored) only remembers the state and writes no events.
 * A paused/unavailable work profile is NOT treated as removal.
 */
internal fun diffStates(
    current: List<AppEntry>,
    stored: List<AppStateEntity>,
    workProfile: WorkProfileState,
    now: Long,
): SyncDiff {
    val storedByKey = stored.associateBy { it.packageName to it.profile }
    val firstRun = storedByKey.isEmpty()
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
        val old = storedByKey[key]
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

    val deletes = storedByKey
        .filterKeys { it !in currentKeys }
        .values
        .filter { it.profile != AppProfile.WORK.name || workProfile == WorkProfileState.ACTIVE }
    deletes.forEach { events += it.toEvent(EventType.REMOVED, time = now, approx = true) }

    return SyncDiff(upserts, deletes, events)
}

internal fun AppStateEntity.toEvent(type: String, time: Long, approx: Boolean) = AppEventEntity(
    time = time,
    packageName = packageName,
    profile = profile,
    label = label,
    version = version,
    installedAt = installedAt,
    type = type,
    approx = approx,
)

/** Writes install / update / removal events (app_event) based on diffStates. */
class AppHistoryRepository(context: Context) {

    private val dao = AppDatabase.get(context).appDao()

    val removals: Flow<List<RemovedApp>> = dao.latestRemovals().map { list ->
        list.map { it.toRemovedApp() }
    }

    suspend fun sync(current: List<AppEntry>, workProfile: WorkProfileState) {
        val diff = diffStates(current, dao.allStates(), workProfile, System.currentTimeMillis())
        if (!diff.isEmpty) dao.applySync(diff.upserts, diff.deletes, diff.events)
    }

    /** Called by the system broadcast: exact removal time for apps in this profile. */
    suspend fun recordRemoval(packageName: String) {
        val state = dao.state(packageName, AppProfile.THIS.name) ?: return
        val event = state.toEvent(EventType.REMOVED, time = System.currentTimeMillis(), approx = false)
        dao.applySync(upserts = emptyList(), deletes = listOf(state), events = listOf(event))
    }

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