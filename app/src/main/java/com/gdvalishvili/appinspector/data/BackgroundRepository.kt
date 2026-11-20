package com.gdvalishvili.appinspector.data.background

import android.Manifest
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.PowerManager
import com.gdvalishvili.appinspector.data.UsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Background (STATE_DEFAULT) and foreground bytes, Wi-Fi + mobile. sharedUid: several apps share one uid. */
data class NetUsage(
    val backgroundBytes: Long,
    val foregroundBytes: Long,
    val backgroundTodayBytes: Long,
    val sharedUid: Boolean,
)

data class BackgroundSnapshot(
    val hasUsageAccess: Boolean,
    val from: Long,                         // start of the 7-day window
    val now: Long,
    val fgs: Map<String, FgsUsage>,         // packageName -> foreground service usage
    val buckets: Map<String, StandbyBucket>,// last known bucket (only if it changed in the window)
    val network: Map<String, NetUsage>,     // packageName -> background data
    val batteryExempt: Set<String>,         // "Unrestricted": ignores battery optimization
)

/** What an app is able to do in the background, read from its manifest. */
data class BackgroundCapabilities(
    val startsOnBoot: Boolean,
    val exactAlarms: Boolean,
    val wakeLock: Boolean,
    val asksBatteryExemption: Boolean,
    val fgsTypes: List<String>,
) {
    val isEmpty: Boolean get() = !startsOnBoot && !exactAlarms && !wakeLock && !asksBatteryExemption && fgsTypes.isEmpty()
}

/** Reads everything on demand (no background work, no database). */
class BackgroundRepository(private val context: Context) {

    private val pm = context.packageManager
    private val usm = context.getSystemService(UsageStatsManager::class.java)
    private val usageAccess = UsageRepository(context)

    /** packages: the apps to check for battery exemption (usually all installed apps of this profile). */
    suspend fun load(packages: Collection<String>): BackgroundSnapshot = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val from = now - TimeUnit.DAYS.toMillis(WINDOW_DAYS)
        val todayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val battery = batteryExempt(packages)

        if (!usageAccess.hasAccess()) {
            return@withContext BackgroundSnapshot(false, from, now, emptyMap(), emptyMap(), emptyMap(), battery)
        }

        val queryStart = from - LOOK_BEHIND_MS
        val events = ArrayList<FgsEvent>()
        val buckets = HashMap<String, StandbyBucket>()
        try {
            val list = usm.queryEvents(queryStart, now)
            val e = UsageEvents.Event()
            while (list.hasNextEvent()) {
                list.getNextEvent(e)
                when (e.eventType) {
                    EVENT_FGS_START -> events += FgsEvent(e.timeStamp, e.packageName, e.className, FgsEventKind.START)
                    EVENT_FGS_STOP -> events += FgsEvent(e.timeStamp, e.packageName, e.className, FgsEventKind.STOP)
                    EVENT_DEVICE_SHUTDOWN, EVENT_DEVICE_STARTUP ->
                        events += FgsEvent(e.timeStamp, "", null, FgsEventKind.DEVICE_OFF)
                    EVENT_BUCKET_CHANGED ->
                        if (Build.VERSION.SDK_INT >= 28) buckets[e.packageName] = StandbyBucket.of(e.appStandbyBucket)
                }
            }
        } catch (e: Exception) {
            // Usage events unavailable: show empty sections instead of crashing
        }
        val sessions = FgsSessions.build(events, queryStart)
        val fgs = FgsSessions.summarize(sessions, from, todayStart, now)

        BackgroundSnapshot(
            hasUsageAccess = true,
            from = from,
            now = now,
            fgs = fgs,
            buckets = buckets,
            network = network(from, todayStart, now),
            batteryExempt = battery,
        )
    }

    fun capabilitiesOf(packageName: String): BackgroundCapabilities? {
        val info = packageInfo(packageName) ?: return null
        val perms = info.requestedPermissions?.toSet().orEmpty()
        val fgsMask = if (Build.VERSION.SDK_INT >= 29) {
            info.services.orEmpty().fold(0) { acc, s -> acc or s.foregroundServiceType }
        } else 0
        return BackgroundCapabilities(
            startsOnBoot = Manifest.permission.RECEIVE_BOOT_COMPLETED in perms,
            exactAlarms = "android.permission.SCHEDULE_EXACT_ALARM" in perms || "android.permission.USE_EXACT_ALARM" in perms,
            wakeLock = Manifest.permission.WAKE_LOCK in perms,
            asksBatteryExemption = "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" in perms,
            fgsTypes = FgsTypes.names(fgsMask),
        )
    }

    private fun batteryExempt(packages: Collection<String>): Set<String> {
        val power = context.getSystemService(PowerManager::class.java) ?: return emptySet()
        return packages.filterTo(HashSet()) { pkg ->
            try { power.isIgnoringBatteryOptimizations(pkg) } catch (e: Exception) { false }
        }
    }

    /** Sums Wi-Fi + mobile by uid and maps uids to package names. */
    private fun network(from: Long, todayStart: Long, now: Long): Map<String, NetUsage> {
        val nsm = context.getSystemService(NetworkStatsManager::class.java) ?: return emptyMap()
        val bg = HashMap<Int, Long>()
        val fg = HashMap<Int, Long>()
        val bgToday = HashMap<Int, Long>()
        @Suppress("DEPRECATION")
        val types = listOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)
        for (type in types) {
            collect(nsm, type, from, now) { uid, state, bytes ->
                if (state == NetworkStats.Bucket.STATE_FOREGROUND) fg.merge(uid, bytes, Long::plus)
                else bg.merge(uid, bytes, Long::plus)
            }
            collect(nsm, type, todayStart, now) { uid, state, bytes ->
                if (state != NetworkStats.Bucket.STATE_FOREGROUND) bgToday.merge(uid, bytes, Long::plus)
            }
        }
        val out = HashMap<String, NetUsage>()
        for (uid in bg.keys + fg.keys) {
            if (uid < 0) continue // removed apps, tethering
            val packages = pm.getPackagesForUid(uid)?.toList().orEmpty()
            val usage = NetUsage(bg[uid] ?: 0L, fg[uid] ?: 0L, bgToday[uid] ?: 0L, sharedUid = packages.size > 1)
            packages.forEach { out[it] = usage }
        }
        return out
    }

    private inline fun collect(
        nsm: NetworkStatsManager,
        type: Int,
        start: Long,
        end: Long,
        onBucket: (uid: Int, state: Int, bytes: Long) -> Unit,
    ) {
        try {
            // subscriberId = null: all mobile networks (allowed since API 29 without phone permissions)
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

    private fun packageInfo(packageName: String): PackageInfo? = try {
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, flags)
        }
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    companion object {
        const val WINDOW_DAYS = 7L
        private val LOOK_BEHIND_MS = TimeUnit.HOURS.toMillis(2)
        // UsageEvents.Event types (literal: some are API 29+, minSdk is 26)
        private const val EVENT_BUCKET_CHANGED = 11
        private const val EVENT_FGS_START = 19
        private const val EVENT_FGS_STOP = 20
        private const val EVENT_DEVICE_SHUTDOWN = 26
        private const val EVENT_DEVICE_STARTUP = 27
    }
}