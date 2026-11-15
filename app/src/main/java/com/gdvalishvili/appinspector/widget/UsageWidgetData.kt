package com.gdvalishvili.appinspector.widget

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gdvalishvili.appinspector.data.ReportRepository
import com.gdvalishvili.appinspector.model.Period
import com.gdvalishvili.appinspector.model.PeriodType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class WidgetApp(val packageName: String, val label: String, val ms: Long)

/** What the widget shows. Stored in the widget's own state so a refresh redraws it. */
data class UsageWidgetData(
    val totalMs: Long,
    val top: List<WidgetApp>,
    val updatedAt: Long,
    val noAccess: Boolean,
) {
    fun writeTo(prefs: MutablePreferences) {
        prefs[KEY_TOTAL] = totalMs
        prefs[KEY_UPDATED] = updatedAt
        prefs[KEY_NO_ACCESS] = noAccess
        prefs[KEY_TOP] = top.joinToString("\n") { "${it.packageName}\t${clean(it.label)}\t${it.ms}" }
    }

    companion object {
        private const val TOP_COUNT = 3
        private val KEY_TOTAL = longPreferencesKey("total_ms")
        private val KEY_UPDATED = longPreferencesKey("updated_at")
        private val KEY_NO_ACCESS = booleanPreferencesKey("no_access")
        private val KEY_TOP = stringPreferencesKey("top")

        private fun clean(label: String) = label.replace('\t', ' ').replace('\n', ' ')

        /** null = nothing computed yet (widget just added). */
        fun readFrom(prefs: Preferences): UsageWidgetData? {
            val updated = prefs[KEY_UPDATED] ?: return null
            val top = prefs[KEY_TOP].orEmpty()
                .lineSequence()
                .mapNotNull { line ->
                    val parts = line.split('\t')
                    if (parts.size == 3) parts[2].toLongOrNull()?.let { WidgetApp(parts[0], parts[1], it) } else null
                }
                .toList()
            return UsageWidgetData(prefs[KEY_TOTAL] ?: 0L, top, updated, prefs[KEY_NO_ACCESS] ?: false)
        }

        /** Same numbers as the report's "Today" (launcher excluded, apps under 1 min hidden). */
        suspend fun load(context: Context): UsageWidgetData = withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            if (!hasUsageAccess(context)) return@withContext UsageWidgetData(0L, emptyList(), now, noAccess = true)
            val report = ReportRepository(context).load(Period.of(PeriodType.DAY, LocalDate.now()))
            UsageWidgetData(
                totalMs = report.totalMs,
                top = report.apps.take(TOP_COUNT).map { WidgetApp(it.packageName, it.label, it.ms) },
                updatedAt = now,
                noAccess = false,
            )
        }
        @Suppress("DEPRECATION")
        private fun hasUsageAccess(context: Context): Boolean {
            val ops = context.getSystemService(AppOpsManager::class.java)
            val mode = if (Build.VERSION.SDK_INT >= 29) {
                ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            }
            return mode == AppOpsManager.MODE_ALLOWED ||
                    (mode == AppOpsManager.MODE_DEFAULT &&
                            context.checkSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) ==
                            PackageManager.PERMISSION_GRANTED)
        }
    }
}