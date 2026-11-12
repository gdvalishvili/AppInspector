package com.gdvalishvili.appinspector.data

import android.content.Context
import android.os.Process
import com.gdvalishvili.appinspector.model.AppEntry
import com.gdvalishvili.appinspector.model.AppKind
import com.gdvalishvili.appinspector.model.AppProfile
import com.gdvalishvili.appinspector.model.UsageStatus

data class AppsSnapshot(
    val apps: List<AppEntry>,
    val hasUsageAccess: Boolean,
    val workProfile: WorkProfileState,
)

/** Single entry point: this profile (PackageManager + usage) + work profile (LauncherApps). */
class InstalledAppsRepository(context: Context) {

    private val pm = context.packageManager
    private val usageRepository = UsageRepository(context)
    private val workRepository = WorkProfileRepository(context)

    fun load(): AppsSnapshot {
        val hasAccess = usageRepository.hasAccess()
        val usage = if (hasAccess) usageRepository.load() else null
        val own = loadOwnProfile(usage)
        val work = workRepository.load()
        return AppsSnapshot(own + work.apps, hasAccess, work.state)
    }

    private fun loadOwnProfile(usage: UsageData?): List<AppEntry> {
        val me = Process.myUserHandle()
        val launcherPackages = pm.launcherPackageNames()

        return pm.installedPackagesCompat().mapNotNull { p ->
            val ai = p.applicationInfo ?: return@mapNotNull null
            val kind = kindOf(ai)
            val archived = isArchivedCompat(ai)
            val isFactory = kind != AppKind.USER &&
                    p.firstInstallTime in 1L until FACTORY_INSTALL_THRESHOLD_MS
            val installedAt = p.firstInstallTime.takeIf { it > 0L && !isFactory }
            val updatedAt = p.lastUpdateTime.takeIf { it > 0L && it != p.firstInstallTime }
            val hasLauncher = archived || p.packageName in launcherPackages
            val lastUsed = usage?.lastUsed?.get(p.packageName)?.takeIf { it > 0L }

            AppEntry(
                key = "THIS:${p.packageName}",
                packageName = p.packageName,
                label = ai.loadLabel(pm).toString(),
                versionName = p.versionName,
                kind = kind,
                profile = AppProfile.THIS,
                user = me,
                component = null,
                installedAt = installedAt,
                isFactoryInstall = isFactory,
                updatedAt = updatedAt,
                updatedAtApprox = false,
                lastUsedAt = lastUsed,
                usageStatus = usageStatusOf(usage, lastUsed, hasLauncher, installedAt),
                hasLauncher = hasLauncher,
                isArchived = archived,
            )
        }
    }

    private fun usageStatusOf(
        usage: UsageData?,
        lastUsed: Long?,
        hasLauncher: Boolean,
        installedAt: Long?,
    ): UsageStatus {
        val historyStart = usage?.historyStart
        return when {
            usage == null -> UsageStatus.NO_PERMISSION
            lastUsed != null -> UsageStatus.USED
            !hasLauncher -> UsageStatus.BACKGROUND
            installedAt != null && historyStart != null && installedAt >= historyStart ->
                UsageStatus.NEVER_OPENED
            else -> UsageStatus.OLDER_THAN_HISTORY
        }
    }
}