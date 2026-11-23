package com.gdvalishvili.appinspector.data.privacy

enum class RiskLevel { LOW, MEDIUM, HIGH }

/** Why an app got its level. detail: tracker names, MB, service types (shown in the UI). */
enum class ReasonKind {
    TRACKER_ADS, TRACKER_PROFILING, TRACKER_LOCATION, TRACKER_IDENTIFICATION, TRACKER_ANALYTICS,
    PERM_LOCATION, PERM_MICROPHONE, PERM_SMS, PERM_CALL_LOG, PERM_CAMERA, PERM_CONTACTS, PERM_SENSORS,
    BACKGROUND_DATA, FGS_SENSITIVE;

    val isTracker: Boolean get() = name.startsWith("TRACKER_")
}

data class RiskReason(val kind: ReasonKind, val points: Int, val detail: String = "")

data class RiskInput(
    val trackers: List<TrackerDef>,
    val grantedPermissions: Set<String>,
    val backgroundBytes7d: Long,
    val fgsTypes: List<String>,
)

/** score: trackers capped at TRACKER_CAP + everything else. */
data class RiskResult(val level: RiskLevel, val score: Int, val reasons: List<RiskReason>)

/**
 * Simple, explainable rules (unit-tested). Every point comes with a reason.
 * Trackers: ads/profiling/location/identification = 2, analytics = 1, crash reporting = 0 (per tracker),
 *   but at most TRACKER_CAP points in total -> trackers alone give at most MEDIUM.
 * Granted permissions: location, microphone, SMS, call log = 2; camera, contacts, body sensors = 1 (once per group).
 * Background data >= 50 MB in 7 days = 1; foreground service of type location/microphone/camera = 1.
 * HIGH needs score >= 6 AND trackers AND sensitive access/behavior. MEDIUM: score >= 3.
 */
object RiskRules {

    const val HIGH_FROM = 6
    const val MEDIUM_FROM = 3
    const val TRACKER_CAP = 4
    const val BACKGROUND_DATA_LIMIT = 50L * 1024 * 1024

    // Literal names (no Android needed in unit tests)
    private val PERMISSION_GROUPS: List<Triple<ReasonKind, Int, Set<String>>> = listOf(
        Triple(ReasonKind.PERM_LOCATION, 2, setOf(
            "android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_BACKGROUND_LOCATION",
        )),
        Triple(ReasonKind.PERM_MICROPHONE, 2, setOf("android.permission.RECORD_AUDIO")),
        Triple(ReasonKind.PERM_SMS, 2, setOf(
            "android.permission.READ_SMS", "android.permission.RECEIVE_SMS", "android.permission.SEND_SMS",
        )),
        Triple(ReasonKind.PERM_CALL_LOG, 2, setOf("android.permission.READ_CALL_LOG")),
        Triple(ReasonKind.PERM_CAMERA, 1, setOf("android.permission.CAMERA")),
        Triple(ReasonKind.PERM_CONTACTS, 1, setOf("android.permission.READ_CONTACTS")),
        Triple(ReasonKind.PERM_SENSORS, 1, setOf("android.permission.BODY_SENSORS")),
    )
    private const val COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"
    private val SENSITIVE_FGS = setOf("location", "microphone", "camera")

    /** All permission names the rules look at (the repository only needs to check these). */
    val SENSITIVE_PERMISSIONS: Set<String> =
        PERMISSION_GROUPS.flatMap { it.third }.toSet() + COARSE_LOCATION

    fun evaluate(input: RiskInput): RiskResult {
        val reasons = ArrayList<RiskReason>()

        // Trackers: grouped by their strongest category, one reason per group
        val groups = input.trackers.groupBy { trackerKind(it) }
        for (kind in listOf(
            ReasonKind.TRACKER_ADS, ReasonKind.TRACKER_PROFILING, ReasonKind.TRACKER_LOCATION,
            ReasonKind.TRACKER_IDENTIFICATION, ReasonKind.TRACKER_ANALYTICS,
        )) {
            val list = groups[kind].orEmpty()
            if (list.isNotEmpty()) {
                val per = if (kind == ReasonKind.TRACKER_ANALYTICS) 1 else 2
                reasons += RiskReason(kind, per * list.size, list.joinToString(", ") { it.name })
            }
        }

        // Granted sensitive permissions
        for ((kind, points, names) in PERMISSION_GROUPS) {
            if (names.any { it in input.grantedPermissions }) reasons += RiskReason(kind, points)
        }
        // Approximate location only: half weight
        if (reasons.none { it.kind == ReasonKind.PERM_LOCATION } && COARSE_LOCATION in input.grantedPermissions) {
            reasons += RiskReason(ReasonKind.PERM_LOCATION, 1, "approximate")
        }

        if (input.backgroundBytes7d >= BACKGROUND_DATA_LIMIT) {
            reasons += RiskReason(ReasonKind.BACKGROUND_DATA, 1, (input.backgroundBytes7d / (1024 * 1024)).toString())
        }
        val fgs = input.fgsTypes.filter { it in SENSITIVE_FGS }
        if (fgs.isNotEmpty()) reasons += RiskReason(ReasonKind.FGS_SENSITIVE, 1, fgs.joinToString(", "))

        val trackerPoints = minOf(reasons.filter { it.kind.isTracker }.sumOf { it.points }, TRACKER_CAP)
        val otherPoints = reasons.filter { !it.kind.isTracker }.sumOf { it.points }
        val score = trackerPoints + otherPoints
        val level = when {
            score >= HIGH_FROM && trackerPoints > 0 && otherPoints > 0 -> RiskLevel.HIGH
            score >= MEDIUM_FROM -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
        return RiskResult(level, score, reasons.filter { it.points > 0 })
    }

    /** Strongest category wins; crash reporting only -> null (no points). */
    private fun trackerKind(t: TrackerDef): ReasonKind? = when {
        "Advertisement" in t.categories -> ReasonKind.TRACKER_ADS
        "Profiling" in t.categories -> ReasonKind.TRACKER_PROFILING
        "Location" in t.categories -> ReasonKind.TRACKER_LOCATION
        "Identification" in t.categories -> ReasonKind.TRACKER_IDENTIFICATION
        "Analytics" in t.categories -> ReasonKind.TRACKER_ANALYTICS
        else -> null
    }
}