package com.gdvalishvili.appinspector.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class SortOrder { NAME, INSTALLED, UPDATED, LAST_USED, RECENTLY_USED }
/** Exactly one filter is active at a time. */
enum class AppFilter { ALL, USER, SYSTEM, WORK }

data class ListSettings(
    val filter: AppFilter = AppFilter.USER,
    val sort: SortOrder = SortOrder.NAME,
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Remembers filter, sort and "language already chosen" between launches. */
class SettingsStore(private val context: Context) {

    private val prefs: Flow<Preferences> = context.settingsDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    val settings: Flow<ListSettings> = prefs.map { p ->
        ListSettings(
            filter = p[FILTER]?.let { name -> AppFilter.entries.firstOrNull { it.name == name } }
                ?: AppFilter.USER,
            sort = p[SORT]?.let { name -> SortOrder.entries.firstOrNull { it.name == name } }
                ?: SortOrder.NAME,
        )
    }

    val languageChosen: Flow<Boolean> = prefs.map { it[LANGUAGE_CHOSEN] ?: false }

    suspend fun setFilter(value: AppFilter) {
        context.settingsDataStore.edit { it[FILTER] = value.name }
    }

    suspend fun setSort(value: SortOrder) {
        context.settingsDataStore.edit { it[SORT] = value.name }
    }

    suspend fun setLanguageChosen(value: Boolean) {
        context.settingsDataStore.edit { it[LANGUAGE_CHOSEN] = value }
    }

    private companion object {
        val FILTER = stringPreferencesKey("filter")
        val SORT = stringPreferencesKey("sort")
        val LANGUAGE_CHOSEN = booleanPreferencesKey("language_chosen")
    }
}