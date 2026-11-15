package com.gdvalishvili.appinspector

import com.gdvalishvili.appinspector.data.CleanupGroup
import com.gdvalishvili.appinspector.data.CleanupInput
import com.gdvalishvili.appinspector.data.CleanupRules
import com.gdvalishvili.appinspector.model.UsageStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class CleanupRulesTest {

    private val now = 1_800_000_000_000L
    private fun daysAgo(d: Long) = now - TimeUnit.DAYS.toMillis(d)

    private fun input(
        isUserApp: Boolean = true,
        isThisProfile: Boolean = true,
        isOwnApp: Boolean = false,
        isArchived: Boolean = false,
        hasLauncher: Boolean = true,
        usageStatus: UsageStatus = UsageStatus.USED,
        installedAt: Long? = daysAgo(365),
        lastUsedAt: Long? = null,
        excluded: Boolean = false,
    ) = CleanupInput(isUserApp, isThisProfile, isOwnApp, isArchived, hasLauncher, usageStatus, installedAt, lastUsedAt, excluded)

    private fun groups(i: CleanupInput) = CleanupRules.groups(i, now)

    @Test
    fun notCandidates() {
        val unused = UsageStatus.NEVER_OPENED
        assertEquals(emptySet<CleanupGroup>(), groups(input(isUserApp = false, usageStatus = unused)))
        assertEquals(emptySet<CleanupGroup>(), groups(input(isThisProfile = false, usageStatus = unused)))
        assertEquals(emptySet<CleanupGroup>(), groups(input(isOwnApp = true, usageStatus = unused)))
        assertEquals(emptySet<CleanupGroup>(), groups(input(isArchived = true, usageStatus = unused)))
    }

    @Test
    fun excludedOnlyInExcludedGroup() {
        assertEquals(
            setOf(CleanupGroup.EXCLUDED),
            groups(input(excluded = true, usageStatus = UsageStatus.NEVER_OPENED)),
        )
    }

    @Test
    fun backgroundOnlyInBackgroundGroup() {
        assertEquals(setOf(CleanupGroup.BACKGROUND), groups(input(hasLauncher = false, usageStatus = UsageStatus.NEVER_OPENED)))
        assertEquals(setOf(CleanupGroup.BACKGROUND), groups(input(usageStatus = UsageStatus.BACKGROUND)))
    }

    @Test
    fun neverOpenedByInstallAge() {
        val never = UsageStatus.NEVER_OPENED
        assertEquals(emptySet<CleanupGroup>(), groups(input(usageStatus = never, installedAt = daysAgo(10))))
        assertEquals(setOf(CleanupGroup.NEVER_OPENED), groups(input(usageStatus = never, installedAt = daysAgo(40))))
        assertEquals(
            setOf(CleanupGroup.NEVER_OPENED, CleanupGroup.UNUSED_90),
            groups(input(usageStatus = never, installedAt = daysAgo(100))),
        )
    }

    @Test
    fun unusedByLastUse() {
        assertEquals(emptySet<CleanupGroup>(), groups(input(lastUsedAt = daysAgo(10))))
        assertEquals(setOf(CleanupGroup.UNUSED_90), groups(input(lastUsedAt = daysAgo(120))))
        assertEquals(setOf(CleanupGroup.UNUSED_90, CleanupGroup.UNUSED_180), groups(input(lastUsedAt = daysAgo(200))))
    }

    @Test
    fun olderThanHistoryAndNoPermission() {
        assertEquals(
            setOf(CleanupGroup.UNUSED_90, CleanupGroup.UNUSED_180),
            groups(input(usageStatus = UsageStatus.OLDER_THAN_HISTORY, installedAt = null)),
        )
        assertEquals(emptySet<CleanupGroup>(), groups(input(usageStatus = UsageStatus.NO_PERMISSION)))
    }
}