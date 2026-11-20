package com.gdvalishvili.appinspector.data.background

/** Event kinds we use from UsageEvents (FOREGROUND_SERVICE_START/STOP, DEVICE_SHUTDOWN/STARTUP). */
enum class FgsEventKind { START, STOP, DEVICE_OFF }

data class FgsEvent(val time: Long, val packageName: String, val className: String?, val kind: FgsEventKind)

/** One foreground service run. end == null: still running (no STOP seen yet). */
data class FgsSession(val packageName: String, val className: String?, val start: Long, val end: Long?) {
    val isRunning: Boolean get() = end == null
}

/** Per-app summary for a window (e.g. the last 7 days). */
data class FgsUsage(
    val packageName: String,
    val totalMs: Long,
    val todayMs: Long,
    val starts: Int,
    val running: FgsSession?,   // newest running session, if any
)

/** Pure logic (unit-tested): START/STOP pairs -> sessions -> per-app totals. */
object FgsSessions {

    /**
     * queryStart: beginning of the event query. A STOP for a service never seen before
     * means it started earlier, so the session starts at queryStart (clipped later).
     * A device shutdown/startup closes every open session.
     */
    fun build(events: List<FgsEvent>, queryStart: Long): List<FgsSession> {
        val open = LinkedHashMap<Pair<String, String?>, Long>()
        val seen = HashSet<Pair<String, String?>>()
        val out = ArrayList<FgsSession>()
        for (e in events.sortedBy { it.time }) {
            val key = e.packageName to e.className
            when (e.kind) {
                FgsEventKind.START -> {
                    seen += key
                    if (key !in open) open[key] = e.time // repeated START: keep the first one
                }
                FgsEventKind.STOP -> {
                    val start = open.remove(key)
                    when {
                        start != null -> out += FgsSession(e.packageName, e.className, start, e.time)
                        key !in seen -> out += FgsSession(e.packageName, e.className, queryStart, e.time)
                        // else: duplicate STOP, ignore
                    }
                    seen += key
                }
                FgsEventKind.DEVICE_OFF -> {
                    open.forEach { (k, start) -> out += FgsSession(k.first, k.second, start, e.time) }
                    open.clear()
                }
            }
        }
        open.forEach { (k, start) -> out += FgsSession(k.first, k.second, start, null) }
        return out
    }

    /** Time inside [from, now] per app; "today" inside [todayStart, now]. */
    fun summarize(sessions: List<FgsSession>, from: Long, todayStart: Long, now: Long): Map<String, FgsUsage> =
        sessions.groupBy { it.packageName }
            .mapValues { (pkg, list) ->
                FgsUsage(
                    packageName = pkg,
                    totalMs = list.sumOf { overlap(it, from, now) },
                    todayMs = list.sumOf { overlap(it, todayStart, now) },
                    starts = list.count { it.start >= from },
                    running = list.filter { it.isRunning }.maxByOrNull { it.start },
                )
            }
            .filterValues { it.totalMs > 0 || it.running != null }

    private fun overlap(s: FgsSession, from: Long, now: Long): Long {
        val a = maxOf(s.start, from)
        val b = minOf(s.end ?: now, now)
        return (b - a).coerceAtLeast(0L)
    }
}

/** App standby bucket: how strongly Android limits the app in the background. */
enum class StandbyBucket {
    ACTIVE, WORKING_SET, FREQUENT, RARE, RESTRICTED, OTHER;

    companion object {
        // Values of UsageStatsManager.STANDBY_BUCKET_* (literal, so unit tests need no Android)
        fun of(raw: Int): StandbyBucket = when (raw) {
            10 -> ACTIVE
            20 -> WORKING_SET
            30 -> FREQUENT
            40 -> RARE
            45 -> RESTRICTED
            else -> OTHER
        }
    }
}

/** Foreground service types declared in the manifest (ServiceInfo.FOREGROUND_SERVICE_TYPE_* bits). */
object FgsTypes {
    private val NAMES = listOf(
        1 to "dataSync",
        2 to "mediaPlayback",
        4 to "phoneCall",
        8 to "location",
        16 to "connectedDevice",
        32 to "mediaProjection",
        64 to "camera",
        128 to "microphone",
        256 to "health",
        512 to "remoteMessaging",
        1024 to "systemExempted",
        2048 to "shortService",
        8192 to "mediaProcessing",
        1 shl 30 to "specialUse",
    )

    fun names(mask: Int): List<String> = NAMES.filter { (bit, _) -> mask and bit != 0 }.map { it.second }
}