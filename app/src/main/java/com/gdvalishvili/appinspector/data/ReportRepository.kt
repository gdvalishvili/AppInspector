package com.gdvalishvili.appinspector.data

import android.content.Context
import com.gdvalishvili.appinspector.db.AppDatabase
import com.gdvalishvili.appinspector.model.Period
import java.time.LocalDate
import java.time.ZoneId

data class AppUsage(val packageName: String, val label: String, val ms: Long, val installed: Boolean)

data class DayUsage(val date: LocalDate, val ms: Long)

data class PeriodChange(
    val type: String,        // EventType.*
    val packageName: String,
    val label: String,
    val version: String?,
    val time: Long,
    val approx: Boolean,
)

data class Report(
    val period: Period,
    val totalMs: Long,               // launcher excluded
    val launcherMs: Long,            // shown as a separate line
    val dailyAverageMs: Long,
    val previousAverageMs: Long?,    // null = no fair comparison possible
    val days: List<DayUsage>,        // for the chart
    val apps: List<AppUsage>,        // launcher excluded, most used first
    val changes: List<PeriodChange>,
    val firstDataDay: LocalDate,     // earliest day we have data for
)

/** Builds a report: stored days from the database + today counted live. */
class ReportRepository(context: Context) {

    private val pm = context.packageManager
    private val dao = AppDatabase.get(context).usageDao()
    private val collector = UsageCollector(context)

    suspend fun load(period: Period, zone: ZoneId = ZoneId.systemDefault()): Report {
        val today = LocalDate.now(zone)
        val launcher = pm.defaultLauncherPackage()
        val firstDay = dao.firstDay()?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today

        val perPackage = HashMap<String, Long>()
        val perDay = HashMap<LocalDate, Long>()
        val from = period.start.toString()
        val to = period.end.toString()

        dao.totalsByPackage(from, to).forEach { perPackage[it.packageName] = it.ms }
        dao.totalsByDay(from, to, launcher.orEmpty()).forEach { row ->
            runCatching { LocalDate.parse(row.day) }.getOrNull()?.let { perDay[it] = row.ms }
        }

        // Today is not stored yet: count it live with the same algorithm
        if (today in period) {
            val live = collector.foregroundTimes(today, zone)
            live.forEach { (pkg, ms) -> perPackage[pkg] = (perPackage[pkg] ?: 0L) + ms }
            perDay[today] = live.filterKeys { it != launcher }.values.sum()
        }

        val launcherMs = launcher?.let { perPackage.remove(it) } ?: 0L
        val totalMs = perPackage.values.sum()
        val coveredDays = period.days
            .count { !it.isAfter(today) && !it.isBefore(firstDay) }
            .coerceAtLeast(1)

        val apps = perPackage
            .filterValues { it >= MIN_APP_MS }
            .map { (pkg, ms) ->
                val label = pm.appLabelOrNull(pkg)
                AppUsage(pkg, label ?: dao.lastKnownLabel(pkg) ?: pkg, ms, installed = label != null)
            }
            .sortedByDescending { it.ms }

        val fromMs = period.start.atStartOfDay(zone).toInstant().toEpochMilli()
        val toMs = period.end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val changes = dao.eventsBetween(fromMs, toMs).map {
            PeriodChange(it.type, it.packageName, it.label, it.version, it.time, it.approx)
        }

        // Compare only finished periods, and only if the previous one is fully covered by data
        val previousAverage = if (today in period) null else previousAverage(period, firstDay, launcher)

        return Report(
            period = period,
            totalMs = totalMs,
            launcherMs = launcherMs,
            dailyAverageMs = totalMs / coveredDays,
            previousAverageMs = previousAverage,
            days = period.days.map { DayUsage(it, perDay[it] ?: 0L) },
            apps = apps,
            changes = changes,
            firstDataDay = firstDay,
        )
    }

    private suspend fun previousAverage(period: Period, firstDay: LocalDate, launcher: String?): Long? {
        val previous = period.previous()
        if (previous.start.isBefore(firstDay)) return null
        val total = dao.totalsByPackage(previous.start.toString(), previous.end.toString())
            .filter { it.packageName != launcher }
            .sumOf { it.ms }
        return total / previous.days.size
    }

    private companion object {
        const val MIN_APP_MS = 60_000L // hide apps used less than 1 minute
    }
}