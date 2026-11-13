package com.gdvalishvili.appinspector.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import kotlinx.coroutines.flow.first

enum class SortOrder { NAME, INSTALLED, UPDATED, LAST_USED, RECENTLY_USED }

/** Exactly one filter is active at a time. */
enum class AppFilter { ALL, USER, SYSTEM, WORK, REMOVED }

data class ListSettings(
    val filter: AppFilter = AppFilter.USER,
    val sort: SortOrder = SortOrder.NAME,
    val showCategory: Boolean = false,
    val showSource: Boolean = false,
    val category: Int? = null,          // null = all categories
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Remembers view settings and "language already chosen" between launches. */
class SettingsStore(private val context: Context) {

    private val prefs: Flow<Preferences> = context.settingsDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    val settings: Flow<ListSettings> = prefs.map { p ->
        ListSettings(
            filter = p[FILTER]?.let { name -> AppFilter.entries.firstOrNull { it.name == name } }
                ?: AppFilter.USER,
            sort = p[SORT]?.let { name -> SortOrder.entries.firstOrNull { it.name == name } }
                ?: SortOrder.NAME,
            showCategory = p[SHOW_CATEGORY] ?: false,
            showSource = p[SHOW_SOURCE] ?: false,
            category = p[CATEGORY],
        )
    }

    val languageChosen: Flow<Boolean> = prefs.map { it[LANGUAGE_CHOSEN] ?: false }

    suspend fun setFilter(value: AppFilter) {
        context.settingsDataStore.edit { it[FILTER] = value.name }
    }

    suspend fun setSort(value: SortOrder) {
        context.settingsDataStore.edit { it[SORT] = value.name }
    }

    suspend fun setShowCategory(value: Boolean) {
        context.settingsDataStore.edit {
            it[SHOW_CATEGORY] = value
            if (!value) it.remove(CATEGORY) // turning categories off clears the category filter
        }
    }

    suspend fun setShowSource(value: Boolean) {
        context.settingsDataStore.edit { it[SHOW_SOURCE] = value }
    }

    suspend fun setCategory(value: Int?) {
        context.settingsDataStore.edit {
            if (value == null) it.remove(CATEGORY) else it[CATEGORY] = value
        }
    }

    suspend fun setLanguageChosen(value: Boolean) {
        context.settingsDataStore.edit { it[LANGUAGE_CHOSEN] = value }
    }

    /** Last calendar day (ISO yyyy-MM-dd) whose usage is stored in daily_usage. */
    suspend fun lastUsageDay(): String? = prefs.first()[LAST_USAGE_DAY]

    suspend fun setLastUsageDay(day: String) {
        context.settingsDataStore.edit { it[LAST_USAGE_DAY] = day }
    }

    /** Version of the usage counting rules that produced the stored days. */
    suspend fun usageAlgorithmVersion(): Int = prefs.first()[USAGE_ALGORITHM_VERSION] ?: 1

    suspend fun setUsageAlgorithmVersion(value: Int) {
        context.settingsDataStore.edit { it[USAGE_ALGORITHM_VERSION] = value }
    }
    private companion object {
        val FILTER = stringPreferencesKey("filter")
        val SORT = stringPreferencesKey("sort")
        val SHOW_CATEGORY = booleanPreferencesKey("show_category")
        val SHOW_SOURCE = booleanPreferencesKey("show_source")
        val CATEGORY = intPreferencesKey("category")
        val LANGUAGE_CHOSEN = booleanPreferencesKey("language_chosen")
        val LAST_USAGE_DAY = stringPreferencesKey("last_usage_day")
        val USAGE_ALGORITHM_VERSION = intPreferencesKey("usage_algorithm_version")
    }
}