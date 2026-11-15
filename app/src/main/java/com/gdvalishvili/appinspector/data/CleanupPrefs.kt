package com.gdvalishvili.appinspector.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.cleanupDataStore: DataStore<Preferences> by preferencesDataStore(name = "cleanup_settings")

/** "Never touch" list: package names excluded from cleanup. Survives uninstall/reinstall of the app. */
class CleanupPrefs(context: Context) {

    private val store = context.applicationContext.cleanupDataStore
    private val keyExcluded = stringSetPreferencesKey("excluded_packages")

    val excluded: Flow<Set<String>> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[keyExcluded] ?: emptySet() }

    suspend fun setExcluded(packageName: String, excluded: Boolean) {
        store.edit { prefs ->
            val current = prefs[keyExcluded] ?: emptySet()
            prefs[keyExcluded] = if (excluded) current + packageName else current - packageName
        }
    }
}