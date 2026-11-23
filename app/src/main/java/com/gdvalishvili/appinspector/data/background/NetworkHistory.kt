package com.gdvalishvili.appinspector.data.background

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import com.gdvalishvili.appinspector.data.SettingsStore
import com.gdvalishvili.appinspector.data.UsageRepository
import com.gdvalishvili.appinspector.db.AppDatabase
import com.gdvalishvili.appinspector.db.DailyNetworkEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/**
 * Stores background/foreground internet per app per day (daily_network).
 * Called by the daily job and when the Privacy screen opens. First run backfills up to 30 days.
 */
class NetworkHistory(private val context: Context) {

    private val dao = AppDatabase.get(context).networkDao()
    private val settings = SettingsStore(context)
    private val usage = UsageRepository(context)
    private val pm = context.packageManager

    suspend fun collect() = withContext(Dispatchers.IO) {
        if (!usage.hasAccess()) return@withContext
        val nsm = context.getSystemService(NetworkStatsManager::class.java) ?: return@withContext
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val yesterday = today.minusDays(1)
        val last = dao.lastDay()?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        var day = last?.plusDays(1) ?: today.minusDays(BACKFILL_DAYS)

        while (!day.isAfter(yesterday)) {
            val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val bg = HashMap<Int, Long>()
            val fg = HashMap<Int, Long>()
            @Suppress("DEPRECATION")
            for (type in listOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)) {
                collectSummary(nsm, type, start, end) { uid, state, bytes ->
                    if (state == NetworkStats.Bucket.STATE_FOREGROUND) fg.merge(uid, bytes, Long::plus)
                    else bg.merge(uid, bytes, Long::plus)
                }
            }
            val rows = NetworkMerge.rows(day.toString(), bg, fg) { uid -> pm.getPackagesForUid(uid)?.toList().orEmpty() }
            if (rows.isNotEmpty()) dao.upsertAll(rows)
            day = day.plusDays(1)
        }

        val years = settings.networkRetentionYears.first()
        if (years > 0) dao.deleteOlderThan(today.minusYears(years.toLong()).toString())
    }

    private inline fun collectSummary(
        nsm: NetworkStatsManager, type: Int, start: Long, end: Long,
        onBucket: (uid: Int, state: Int, bytes: Long) -> Unit,
    ) {
        try {
            val stats = nsm.querySummary(type, null, start, end) ?: return
            val b = NetworkStats.Bucket()
            try {
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(b)
                    onBucket(b.uid, b.state, b.rxBytes + b.txBytes)
                }
            } finally {
                stats.close()
            }
        } catch (e: Exception) {
            // No stats for this network type (e.g. no SIM): skip
        }
    }

    private companion object {
        const val BACKFILL_DAYS = 30L
    }
}

/** Pure (unit-tested): per-uid bytes -> one row per package. Shared uids give each package the uid total. */
object NetworkMerge {
    fun rows(
        day: String,
        bg: Map<Int, Long>,
        fg: Map<Int, Long>,
        packagesOf: (Int) -> List<String>,
    ): List<DailyNetworkEntity> {
        val out = HashMap<String, DailyNetworkEntity>()
        for (uid in bg.keys + fg.keys) {
            if (uid < 0) continue // removed apps, tethering
            val b = bg[uid] ?: 0L
            val f = fg[uid] ?: 0L
            if (b == 0L && f == 0L) continue
            for (pkg in packagesOf(uid)) {
                val old = out[pkg]
                out[pkg] = if (old == null) DailyNetworkEntity(day, pkg, b, f)
                else old.copy(bgBytes = old.bgBytes + b, fgBytes = old.fgBytes + f)
            }
        }
        return out.values.sortedBy { it.packageName }
    }
}