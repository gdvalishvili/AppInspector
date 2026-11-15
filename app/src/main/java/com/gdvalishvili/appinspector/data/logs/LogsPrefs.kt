package com.gdvalishvili.appinspector.data.logs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.logsDataStore: DataStore<Preferences> by preferencesDataStore(name = "logs_settings")

class LogsPrefs(context: Context) {

    private val store = context.applicationContext.logsDataStore
    private val keyAutoDelete = booleanPreferencesKey("auto_delete")

    /** Default ON: keep only the newest snapshots. */
    val autoDelete: Flow<Boolean> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[keyAutoDelete] ?: true }

    suspend fun setAutoDelete(value: Boolean) {
        store.edit { it[keyAutoDelete] = value }
    }
}