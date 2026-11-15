package com.gdvalishvili.appinspector.data

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import java.util.concurrent.TimeUnit

/** lastUsed: packageName -> max lastTimeUsed; historyStart: oldest bucket the system still keeps. */
data class UsageData(val lastUsed: Map<String, Long>, val historyStart: Long?)

class UsageRepository(private val context: Context) {

    private val usm = context.getSystemService(UsageStatsManager::class.java)

    fun hasAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            )
        }
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    /** Takes the newest lastTimeUsed across daily/weekly/monthly/yearly buckets. */
    fun load(): UsageData {
        val now = System.currentTimeMillis()
        val begin = now - LOOKBACK_MS
        val lastUsed = HashMap<String, Long>()
        var historyStart: Long? = null

        for (interval in INTERVALS) {
            val stats = usm.queryUsageStats(interval, begin, now) ?: continue
            for (s in stats) {
                val t = s.lastTimeUsed
                if (t > (lastUsed[s.packageName] ?: 0L)) lastUsed[s.packageName] = t
                val start = s.firstTimeStamp
                if (start > 0L && (historyStart == null || start < historyStart)) {
                    historyStart = start
                }
            }
        }
        return UsageData(lastUsed, historyStart)
    }

    private companion object {
        val LOOKBACK_MS = TimeUnit.DAYS.toMillis(3 * 365L)
        val INTERVALS = listOf(
            UsageStatsManager.INTERVAL_DAILY,
            UsageStatsManager.INTERVAL_WEEKLY,
            UsageStatsManager.INTERVAL_MONTHLY,
            UsageStatsManager.INTERVAL_YEARLY,
        )
    }
}