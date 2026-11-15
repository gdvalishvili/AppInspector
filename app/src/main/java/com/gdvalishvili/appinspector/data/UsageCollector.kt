package com.gdvalishvili.appinspector.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.gdvalishvili.appinspector.db.AppDatabase
import com.gdvalishvili.appinspector.db.DailyUsageEntity
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Stores per-app foreground time for whole calendar days (00:00-24:00, device time zone).
 * The counting itself is in ForegroundCounter (unit-tested).
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

    /** Foreground time per package for one calendar day. Also usable for "today so far". */
    fun foregroundTimes(day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Map<String, Long> {
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = minOf(
            day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            System.currentTimeMillis(),
        )
        if (end <= start) return emptyMap()
        // Look a bit before midnight to catch an app that was already open at 00:00
        return ForegroundCounter.count(readEvents(start - LOOK_BEHIND_MS, end), start, end)
    }

    /** Android usage events -> plain list for ForegroundCounter. */
    private fun readEvents(from: Long, to: Long): List<ForegroundEvent> {
        val result = ArrayList<ForegroundEvent>()
        val events = usm.queryEvents(from, to)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                EVENT_RESUMED, EVENT_PAUSED, EVENT_SCREEN_NON_INTERACTIVE, EVENT_DEVICE_SHUTDOWN ->
                    result += ForegroundEvent(event.timeStamp, event.eventType, event.packageName, event.className)
            }
        }
        return result
    }

    /** Automatic retention: delete history older than the chosen number of years. */
    private suspend fun cleanup(today: LocalDate, zone: ZoneId) {
        val years = settings.reportSettingsNow().retentionYears.toLong()
        val minDay = today.minusYears(years)
        usageDao.deleteUsageOlderThan(minDay.toString())
        usageDao.deleteEventsOlderThan(minDay.atStartOfDay(zone).toInstant().toEpochMilli())
    }

    private companion object {
        /** Increase when counting rules change: stored days get recounted. */
        const val ALGORITHM_VERSION = 3
        const val MAX_BACKFILL_DAYS = 7L
        val LOOK_BEHIND_MS = TimeUnit.HOURS.toMillis(2)
    }
}