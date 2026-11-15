package com.gdvalishvili.appinspector.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.updatesDataStore: DataStore<Preferences> by preferencesDataStore(name = "updates_settings")

/** Sort order of the Updates page. Stored by name. */
enum class UpdatesSort { OLDEST_FIRST, NEWEST_FIRST, NAME, MOST_USED }

class UpdatesPrefs(context: Context) {

    private val store = context.applicationContext.updatesDataStore
    private val keySort = stringPreferencesKey("updates_sort")

    val sort: Flow<UpdatesSort> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            val name = prefs[keySort]
            UpdatesSort.entries.firstOrNull { it.name == name } ?: UpdatesSort.OLDEST_FIRST
        }

    suspend fun setSort(sort: UpdatesSort) {
        store.edit { it[keySort] = sort.name }
    }
}