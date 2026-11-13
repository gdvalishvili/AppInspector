package com.gdvalishvili.appinspector.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.gdvalishvili.appinspector.db.AppDatabase
import com.gdvalishvili.appinspector.db.DailyUsageEntity
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

// UsageEvents.Event type values (stable; literals avoid API-level lint noise)
private const val EVENT_RESUMED = 1                 // ACTIVITY_RESUMED / MOVE_TO_FOREGROUND
private const val EVENT_PAUSED = 2                  // ACTIVITY_PAUSED / MOVE_TO_BACKGROUND
private const val EVENT_SCREEN_NON_INTERACTIVE = 16 // screen off
private const val EVENT_DEVICE_SHUTDOWN = 26

/**
 * Counts foreground time per app for whole calendar days (00:00-24:00, device time zone)
 * from usage events and stores finished days in daily_usage.
 * Rule: only one app is in the foreground at a time.
 * The launcher is stored too; reports show it as a separate line.
 */
class UsageCollector(context: Context) {

    private val usm = context.getSystemService(UsageStatsManager::class.java)
    private val usageDao = AppDatabase.get(context).usageDao()
    private val settings = SettingsStore(context)
    private val usage = UsageRepository(context)

    /** Stores every finished day that is not stored yet (events exist only ~7 days back). */
    suspend fun collectMissingDays(zone: ZoneId = ZoneId.systemDefault()) {
        if (!usage.hasAccess()) return
        val today = LocalDate.now(zone)
        val yesterday = today.minusDays(1)
        val earliest = yesterday.minusDays(MAX_BACKFILL_DAYS - 1)

        // Counting rules changed -> recount the days we still have events for
        val algorithmChanged = settings.usageAlgorithmVersion() < ALGORITHM_VERSION
        val last = if (algorithmChanged) null
        else settings.lastUsageDay()?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        var day = maxOf(last?.plusDays(1) ?: earliest, earliest)
        while (!day.isAfter(yesterday)) {
            val rows = foregroundTimes(day, zone).map { (pkg, ms) ->
                DailyUsageEntity(day = day.toString(), packageName = pkg, foregroundMs = ms)
            }
            usageDao.replaceDay(day.toString(), rows)
            settings.setLastUsageDay(day.toString())
            day = day.plusDays(1)
        }
        if (algorithmChanged) settings.setUsageAlgorithmVersion(ALGORITHM_VERSION)
        cleanup(today, zone)
    }

    /** Foreground time per package for one calendar day. Also, usable for "today so far". */
    fun foregroundTimes(day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Map<String, Long> {
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = minOf(
            day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            System.currentTimeMillis(),
        )
        if (end <= start) return emptyMap()

        // Look a bit before midnight to catch an app that was already open at 00:00
        val events = usm.queryEvents(start - LOOK_BEHIND_MS, end)
        val totals = HashMap<String, Long>()

        var currentPackage: String? = null
        var currentActivity: String? = null
        var since = 0L

        fun closeCurrent(at: Long) {
            val pkg = currentPackage ?: return
            val from = maxOf(since, start)
            val to = minOf(at, end)
            if (to > from) totals[pkg] = (totals[pkg] ?: 0L) + (to - from)
            currentPackage = null
            currentActivity = null
        }

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val time = event.timeStamp
            when (event.eventType) {
                EVENT_SCREEN_NON_INTERACTIVE, EVENT_DEVICE_SHUTDOWN -> closeCurrent(time)

                EVENT_RESUMED -> {
                    val pkg = event.packageName ?: continue
                    if (pkg != currentPackage) {
                        // A new app on screen ends the previous one (fixes "stuck" sessions)
                        closeCurrent(time)
                        currentPackage = pkg
                        since = time
                    }
                    currentActivity = event.className
                }

                EVENT_PAUSED -> {
                    // Close only when the activity that is on screen pauses
                    if (event.packageName == currentPackage && event.className == currentActivity) {
                        closeCurrent(time)
                    }
                }
            }
        }
        closeCurrent(end) // still open at the end of the window
        return totals
    }

    /** Automatic retention: delete history older than RETENTION_YEARS. */
    private suspend fun cleanup(today: LocalDate, zone: ZoneId) {
        val minDay = today.minusYears(RETENTION_YEARS)
        usageDao.deleteUsageOlderThan(minDay.toString())
        usageDao.deleteEventsOlderThan(minDay.atStartOfDay(zone).toInstant().toEpochMilli())
    }

    private companion object {
        /** Increase when counting rules change: stored days get recounted. */
        const val ALGORITHM_VERSION = 2
        const val MAX_BACKFILL_DAYS = 7L
        const val RETENTION_YEARS = 2L
        val LOOK_BEHIND_MS = TimeUnit.HOURS.toMillis(2)
    }
}