package com.gdvalishvili.appinspector.data

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import com.gdvalishvili.appinspector.model.AppEntry
import com.gdvalishvili.appinspector.model.AppKind
import com.gdvalishvili.appinspector.model.AppProfile
import com.gdvalishvili.appinspector.model.UsageStatus

enum class WorkProfileState { NONE, ACTIVE, PAUSED }

data class WorkProfileData(val state: WorkProfileState, val apps: List<AppEntry>)

/** Read-only view of other profiles (work) via LauncherApps. Installs nothing there. */
class WorkProfileRepository(private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    fun load(): WorkProfileData {
        val me = Process.myUserHandle()
        val others = launcherApps.profiles.filter { it != me }
        if (others.isEmpty()) return WorkProfileData(WorkProfileState.NONE, emptyList())

        var anyPaused = false
        val apps = others.flatMap { user ->
            if (userManager.isQuietModeEnabled(user)) {
                anyPaused = true
                emptyList()
            } else {
                launcherApps.getActivityList(null, user)
                    .distinctBy { it.componentName.packageName }
                    .map { toEntry(it, user) }
            }
        }
        val state = if (anyPaused && apps.isEmpty()) WorkProfileState.PAUSED else WorkProfileState.ACTIVE
        return WorkProfileData(state, apps)
    }

    private fun toEntry(info: LauncherActivityInfo, user: UserHandle): AppEntry {
        val ai = info.applicationInfo
        val kind = kindOf(ai)
        val rawInstall = info.firstInstallTime
        val isFactory = kind != AppKind.USER && rawInstall in 1L until FACTORY_INSTALL_THRESHOLD_MS
        val pkg = info.componentName.packageName
        return AppEntry(
            key = "WORK:$pkg",
            packageName = pkg,
            label = info.label.toString(),
            versionName = context.packageManager.versionNameFromApk(ai.sourceDir),
            kind = kind,
            profile = AppProfile.WORK,
            user = user,
            component = info.componentName,
            installedAt = rawInstall.takeIf { it > 0L && !isFactory },
            isFactoryInstall = isFactory,
            updatedAt = apkFileTime(ai.sourceDir),
            updatedAtApprox = true,
            lastUsedAt = null,
            usageStatus = UsageStatus.NOT_AVAILABLE,
            hasLauncher = true,
            isArchived = isArchivedCompat(ai),
        )
    }
}