package com.gdvalishvili.appinspector

import com.gdvalishvili.appinspector.data.AppFilter
import com.gdvalishvili.appinspector.data.ListSettings
import com.gdvalishvili.appinspector.data.SortOrder
import com.gdvalishvili.appinspector.model.AppEntry
import com.gdvalishvili.appinspector.model.AppKind
import com.gdvalishvili.appinspector.model.AppProfile
import com.gdvalishvili.appinspector.model.UsageStatus
import com.gdvalishvili.appinspector.ui.filterAndSort
import org.junit.Assert.assertEquals
import org.junit.Test

class FilterAndSortTest {

    private fun app(
        pkg: String,
        label: String = pkg,
        kind: AppKind = AppKind.USER,
        profile: AppProfile = AppProfile.THIS,
        installedAt: Long? = 1_000L,
        lastUsedAt: Long? = null,
        status: UsageStatus = if (lastUsedAt != null) UsageStatus.USED else UsageStatus.NEVER_OPENED,
        category: Int = -1,
    ) = AppEntry(
        key = "${profile.name}:$pkg", packageName = pkg, label = label, versionName = "1.0",
        kind = kind, profile = profile, user = null, component = null,
        installedAt = installedAt, isFactoryInstall = false, updatedAt = null, updatedAtApprox = false,
        lastUsedAt = lastUsedAt, usageStatus = status, hasLauncher = true, isArchived = false,
        category = category,
    )

    private val apps = listOf(
        app("com.user.a", "Alpha"),
        app("com.sys.b", "Beta", kind = AppKind.SYSTEM),
        app("com.sys.c", "Chrome", kind = AppKind.UPDATED_SYSTEM),
        app("com.work.d", "Delta", profile = AppProfile.WORK, status = UsageStatus.NOT_AVAILABLE),
    )

    private fun names(list: List<AppEntry>) = list.map { it.label }

    @Test
    fun eachFilterShowsOnlyItsGroup() {
        assertEquals(listOf("Alpha"), names(filterAndSort(apps, ListSettings(filter = AppFilter.USER), "")))
        assertEquals(listOf("Beta", "Chrome"), names(filterAndSort(apps, ListSettings(filter = AppFilter.SYSTEM), "")))
        assertEquals(listOf("Delta"), names(filterAndSort(apps, ListSettings(filter = AppFilter.WORK), "")))
        assertEquals(4, filterAndSort(apps, ListSettings(filter = AppFilter.ALL), "").size)
    }

    @Test
    fun searchMatchesLabelAndPackageIgnoringCase() {
        val all = ListSettings(filter = AppFilter.ALL)
        assertEquals(listOf("Chrome"), names(filterAndSort(apps, all, "CHRO")))
        assertEquals(listOf("Delta"), names(filterAndSort(apps, all, "com.work")))
    }

    @Test
    fun unusedFirstPutsOldestOnTopAndNoPermissionLast() {
        val list = listOf(
            app("a", "Recent", lastUsedAt = 9_000L),
            app("b", "Old", lastUsedAt = 2_000L),
            app("c", "Ancient", installedAt = null, status = UsageStatus.OLDER_THAN_HISTORY),
            app("d", "NoAccess", status = UsageStatus.NO_PERMISSION),
        )
        val sorted = filterAndSort(list, ListSettings(filter = AppFilter.ALL, sort = SortOrder.LAST_USED), "")
        assertEquals(listOf("Ancient", "Old", "Recent", "NoAccess"), names(sorted))
    }

    @Test
    fun recentlyUsedPutsNewestFirstAndUnusedLast() {
        val list = listOf(
            app("a", "Old", lastUsedAt = 2_000L),
            app("b", "Never"),
            app("c", "Recent", lastUsedAt = 9_000L),
        )
        val sorted = filterAndSort(list, ListSettings(filter = AppFilter.ALL, sort = SortOrder.RECENTLY_USED), "")
        assertEquals(listOf("Recent", "Old", "Never"), names(sorted))
    }

    @Test
    fun categoryFilterAppliesOnlyWhenCategoriesAreShown() {
        val list = listOf(app("a", "Game", category = 0), app("b", "Other", category = 5))
        val off = ListSettings(filter = AppFilter.ALL, showCategory = false, category = 0)
        val on = ListSettings(filter = AppFilter.ALL, showCategory = true, category = 0)
        assertEquals(2, filterAndSort(list, off, "").size)
        assertEquals(listOf("Game"), names(filterAndSort(list, on, "")))
    }
}