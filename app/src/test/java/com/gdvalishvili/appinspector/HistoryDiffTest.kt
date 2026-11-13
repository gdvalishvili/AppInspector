package com.gdvalishvili.appinspector

import com.gdvalishvili.appinspector.data.WorkProfileState
import com.gdvalishvili.appinspector.data.diffStates
import com.gdvalishvili.appinspector.db.AppStateEntity
import com.gdvalishvili.appinspector.db.EventType
import com.gdvalishvili.appinspector.model.AppEntry
import com.gdvalishvili.appinspector.model.AppKind
import com.gdvalishvili.appinspector.model.AppProfile
import com.gdvalishvili.appinspector.model.UsageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryDiffTest {

    private val now = 50_000L

    private fun app(pkg: String, version: String = "1.0", profile: AppProfile = AppProfile.THIS, label: String = pkg) =
        AppEntry(
            key = "${profile.name}:$pkg", packageName = pkg, label = label, versionName = version,
            kind = AppKind.USER, profile = profile, user = null, component = null,
            installedAt = 1_000L, isFactoryInstall = false, updatedAt = null, updatedAtApprox = false,
            lastUsedAt = null, usageStatus = UsageStatus.NEVER_OPENED, hasLauncher = true, isArchived = false,
        )

    private fun state(pkg: String, version: String = "1.0", profile: AppProfile = AppProfile.THIS, label: String = pkg) =
        AppStateEntity(pkg, profile.name, label, version, installedAt = 1_000L, updatedAt = null)

    @Test
    fun firstRunRemembersStateWithoutEvents() {
        val diff = diffStates(listOf(app("a"), app("b")), emptyList(), WorkProfileState.NONE, now)
        assertEquals(2, diff.upserts.size)
        assertTrue(diff.events.isEmpty())
    }

    @Test
    fun newAppIsInstalledEventWithItsInstallTime() {
        val diff = diffStates(listOf(app("a"), app("new")), listOf(state("a")), WorkProfileState.NONE, now)
        val event = diff.events.single()
        assertEquals(EventType.INSTALLED, event.type)
        assertEquals("new", event.packageName)
        assertEquals(1_000L, event.time)
    }

    @Test
    fun versionChangeIsUpdatedEvent() {
        val diff = diffStates(listOf(app("a", version = "2.0")), listOf(state("a")), WorkProfileState.NONE, now)
        assertEquals(EventType.UPDATED, diff.events.single().type)
    }

    @Test
    fun missingAppIsRemovedWithApproximateTime() {
        val diff = diffStates(listOf(app("a")), listOf(state("a"), state("gone")), WorkProfileState.NONE, now)
        val event = diff.events.single()
        assertEquals(EventType.REMOVED, event.type)
        assertEquals("gone", event.packageName)
        assertTrue(event.approx)
        assertEquals(listOf("gone"), diff.deletes.map { it.packageName })
    }

    @Test
    fun pausedWorkProfileIsNotRemoval() {
        val stored = listOf(state("a"), state("outlook", profile = AppProfile.WORK))
        val diff = diffStates(listOf(app("a")), stored, WorkProfileState.PAUSED, now)
        assertTrue(diff.isEmpty)
    }

    @Test
    fun activeWorkProfileMissingAppIsRemoval() {
        val stored = listOf(state("a"), state("outlook", profile = AppProfile.WORK))
        val diff = diffStates(listOf(app("a")), stored, WorkProfileState.ACTIVE, now)
        assertEquals("outlook", diff.events.single().packageName)
    }

    @Test
    fun labelChangeOnlyUpdatesStateWithoutEvent() {
        val diff = diffStates(listOf(app("a", label = "New name")), listOf(state("a")), WorkProfileState.NONE, now)
        assertEquals(1, diff.upserts.size)
        assertTrue(diff.events.isEmpty())
    }
}