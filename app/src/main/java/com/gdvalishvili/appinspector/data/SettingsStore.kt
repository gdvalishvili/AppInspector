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
import com.gdvalishvili.appinspector.model.Period
import com.gdvalishvili.appinspector.model.PeriodType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalDate

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

data class ReportSettings(
    val daily: Boolean = false,
    val weekly: Boolean = false,
    val monthly: Boolean = false,
    val hour: Int = 9,
    val minute: Int = 0,
    val retentionYears: Int = 2,
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** All small settings of the app (key-value). */
class SettingsStore(private val context: Context) {

    private val prefs: Flow<Preferences> = context.settingsDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    // ---------- List view ----------

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

    // ---------- Language ----------

    val languageChosen: Flow<Boolean> = prefs.map { it[LANGUAGE_CHOSEN] ?: false }

    suspend fun setLanguageChosen(value: Boolean) {
        context.settingsDataStore.edit { it[LANGUAGE_CHOSEN] = value }
    }

    // ---------- Reports ----------

    val reportSettings: Flow<ReportSettings> = prefs.map { p ->
        ReportSettings(
            daily = p[REPORT_DAILY] ?: false,
            weekly = p[REPORT_WEEKLY] ?: false,
            monthly = p[REPORT_MONTHLY] ?: false,
            hour = p[REPORT_HOUR] ?: 9,
            minute = p[REPORT_MINUTE] ?: 0,
            retentionYears = p[RETENTION_YEARS] ?: 2,
        )
    }

    suspend fun reportSettingsNow(): ReportSettings = reportSettings.first()

    /** Enabling marks the last finished period as "sent": the first report comes at the next period. */
    suspend fun setReportDaily(value: Boolean) {
        context.settingsDataStore.edit {
            it[REPORT_DAILY] = value
            if (value) it[LAST_SENT_DAY] = LocalDate.now().minusDays(1).toString()
        }
    }

    suspend fun setReportWeekly(value: Boolean) {
        context.settingsDataStore.edit {
            it[REPORT_WEEKLY] = value
            if (value) it[LAST_SENT_WEEK] = Period.of(PeriodType.WEEK, LocalDate.now()).previous().start.toString()
        }
    }

    suspend fun setReportMonthly(value: Boolean) {
        context.settingsDataStore.edit {
            it[REPORT_MONTHLY] = value
            if (value) it[LAST_SENT_MONTH] = Period.of(PeriodType.MONTH, LocalDate.now()).previous().start.toString()
        }
    }

    suspend fun setReportTime(hour: Int, minute: Int) {
        context.settingsDataStore.edit {
            it[REPORT_HOUR] = hour
            it[REPORT_MINUTE] = minute
        }
    }

    suspend fun setRetentionYears(value: Int) {
        context.settingsDataStore.edit { it[RETENTION_YEARS] = value }
    }

    /** Start date (ISO) of the last period whose report was sent, per period type. */
    suspend fun lastSent(type: PeriodType): String? = prefs.first()[lastSentKey(type)]

    suspend fun setLastSent(type: PeriodType, start: LocalDate) {
        context.settingsDataStore.edit { it[lastSentKey(type)] = start.toString() }
    }

    private fun lastSentKey(type: PeriodType) = when (type) {
        PeriodType.DAY -> LAST_SENT_DAY
        PeriodType.WEEK -> LAST_SENT_WEEK
        PeriodType.MONTH -> LAST_SENT_MONTH
    }

    // ---------- Usage collection ----------

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
        val REPORT_DAILY = booleanPreferencesKey("report_daily")
        val REPORT_WEEKLY = booleanPreferencesKey("report_weekly")
        val REPORT_MONTHLY = booleanPreferencesKey("report_monthly")
        val REPORT_HOUR = intPreferencesKey("report_hour")
        val REPORT_MINUTE = intPreferencesKey("report_minute")
        val RETENTION_YEARS = intPreferencesKey("retention_years")
        val LAST_SENT_DAY = stringPreferencesKey("last_sent_day")
        val LAST_SENT_WEEK = stringPreferencesKey("last_sent_week")
        val LAST_SENT_MONTH = stringPreferencesKey("last_sent_month")
        val LAST_USAGE_DAY = stringPreferencesKey("last_usage_day")
        val USAGE_ALGORITHM_VERSION = intPreferencesKey("usage_algorithm_version")
    }
}