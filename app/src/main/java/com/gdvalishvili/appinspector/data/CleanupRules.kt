package com.gdvalishvili.appinspector.data

import com.gdvalishvili.appinspector.model.AppEntry
import com.gdvalishvili.appinspector.model.AppKind
import com.gdvalishvili.appinspector.model.AppProfile
import com.gdvalishvili.appinspector.model.UsageStatus
import java.util.concurrent.TimeUnit

enum class CleanupGroup { NEVER_OPENED, UNUSED_90, UNUSED_180, BACKGROUND, EXCLUDED }

/** Plain values only, so the rules can be unit-tested without Android objects. */
data class CleanupInput(
    val isUserApp: Boolean,
    val isThisProfile: Boolean,
    val isOwnApp: Boolean,
    val isArchived: Boolean,
    val hasLauncher: Boolean,
    val usageStatus: UsageStatus,
    val installedAt: Long?,
    val lastUsedAt: Long?,
    val excluded: Boolean,
)

object CleanupRules {
    const val NEVER_OPENED_DAYS = 30L
    const val UNUSED_DAYS = 90L
    const val LONG_UNUSED_DAYS = 180L

    /**
     * Which cleanup groups an app belongs to (90+ and 180+ overlap on purpose).
     * Not candidates at all: system apps, work profile, our own app, archived apps.
     * Excluded and background apps get only their own group.
     */
    fun groups(input: CleanupInput, now: Long): Set<CleanupGroup> {
        if (!input.isUserApp || !input.isThisProfile || input.isOwnApp || input.isArchived) return emptySet()
        if (input.excluded) return setOf(CleanupGroup.EXCLUDED)
        if (!input.hasLauncher || input.usageStatus == UsageStatus.BACKGROUND) return setOf(CleanupGroup.BACKGROUND)

        val installedDays = input.installedAt?.let { daysBetween(it, now) }
        val unusedDays: Long? = when (input.usageStatus) {
            UsageStatus.USED -> input.lastUsedAt?.let { daysBetween(it, now) }
            UsageStatus.NEVER_OPENED -> installedDays
            UsageStatus.OLDER_THAN_HISTORY -> installedDays ?: Long.MAX_VALUE
            else -> null // no permission / not available: we cannot judge
        }

        val result = mutableSetOf<CleanupGroup>()
        if (input.usageStatus == UsageStatus.NEVER_OPENED && (installedDays ?: 0L) >= NEVER_OPENED_DAYS) {
            result += CleanupGroup.NEVER_OPENED
        }
        if (unusedDays != null && unusedDays >= UNUSED_DAYS) result += CleanupGroup.UNUSED_90
        if (unusedDays != null && unusedDays >= LONG_UNUSED_DAYS) result += CleanupGroup.UNUSED_180
        return result
    }

    fun groupsOf(app: AppEntry, ownPackage: String, excluded: Set<String>, now: Long): Set<CleanupGroup> =
        groups(
            CleanupInput(
                isUserApp = app.kind == AppKind.USER,
                isThisProfile = app.profile == AppProfile.THIS,
                isOwnApp = app.packageName == ownPackage,
                isArchived = app.isArchived,
                hasLauncher = app.hasLauncher,
                usageStatus = app.usageStatus,
                installedAt = app.installedAt,
                lastUsedAt = app.lastUsedAt,
                excluded = app.packageName in excluded,
            ),
            now,
        )

    private fun daysBetween(from: Long, to: Long): Long =
        TimeUnit.MILLISECONDS.toDays(to - from).coerceAtLeast(0L)
}