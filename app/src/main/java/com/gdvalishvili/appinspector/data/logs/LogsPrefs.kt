package com.gdvalishvili.appinspector.data.logs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.logsDataStore: DataStore<Preferences> by preferencesDataStore(name = "logs_settings")

class LogsPrefs(context: Context) {

    private val store = context.applicationContext.logsDataStore
    private val keyAutoDelete = booleanPreferencesKey("auto_delete")
    private val keyKeepCount = intPreferencesKey("keep_count")

    private val data: Flow<Preferences> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    /** Default ON: keep only the newest snapshots. */
    val autoDelete: Flow<Boolean> = data.map { it[keyAutoDelete] ?: true }

    /** How many snapshot files to keep when auto delete is on. */
    val keepCount: Flow<Int> = data.map { (it[keyKeepCount] ?: DEFAULT_KEEP).coerceIn(MIN_KEEP, MAX_KEEP) }

    suspend fun setAutoDelete(value: Boolean) {
        store.edit { it[keyAutoDelete] = value }
    }

    suspend fun setKeepCount(value: Int) {
        store.edit { it[keyKeepCount] = value.coerceIn(MIN_KEEP, MAX_KEEP) }
    }

    companion object {
        const val DEFAULT_KEEP = 20
        const val MIN_KEEP = 1
        const val MAX_KEEP = 500
    }
}