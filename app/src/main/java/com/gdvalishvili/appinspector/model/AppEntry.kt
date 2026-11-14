package com.gdvalishvili.appinspector.model

import android.content.ComponentName
import android.os.UserHandle

enum class AppKind { USER, SYSTEM, UPDATED_SYSTEM }

enum class AppProfile { THIS, WORK }

enum class UsageStatus {
    USED,               // has a lastTimeUsed record
    NEVER_OPENED,       // installed within history window, has launcher, no record
    OLDER_THAN_HISTORY, // no record, but installed before history window (or unknown)
    BACKGROUND,         // no launcher icon: cannot be "opened" by the user
    NO_PERMISSION,      // Usage access is off
    NOT_AVAILABLE,      // other profile (work): usage not accessible
}

/**
 * One row of the list. SQL analogy: primary key = key (profile + packageName).
 */
data class AppEntry(
    val key: String,
    val packageName: String,
    val label: String,
    val versionName: String?,
    val kind: AppKind,
    val profile: AppProfile,
    val user: UserHandle?,           // null only in unit tests (Android classes are not available there)
    val component: ComponentName?,   // needed to open App info for work apps
    val installedAt: Long?,          // null = unknown
    val isFactoryInstall: Boolean,   // preinstalled; real install date unknown
    val updatedAt: Long?,            // null = never updated / unknown
    val updatedAtApprox: Boolean,    // true = APK file date (work profile)
    val lastUsedAt: Long?,
    val usageStatus: UsageStatus,
    val hasLauncher: Boolean,
    val isArchived: Boolean,
    val category: Int = -1,                    // ApplicationInfo.CATEGORY_* ; -1 = undefined
    val installerPackage: String? = null,      // who installed it (e.g. com.android.vending)
    val installerLabel: String? = null,        // human name of the installer, if resolvable
    val installSourceKnown: Boolean = false,   // false for work apps (not accessible)
)

/** An app that was removed (from the event journal). */
data class RemovedApp(
    val key: String,              // same format as AppEntry.key: "THIS:pkg" / "WORK:pkg"
    val packageName: String,
    val label: String,
    val versionName: String?,
    val profile: AppProfile,
    val installedAt: Long?,
    val removedAt: Long,
    val removedAtApprox: Boolean,
)

/** Storage used by an app. dataBytes already includes cacheBytes. */
data class AppSize(val appBytes: Long, val dataBytes: Long, val cacheBytes: Long) {
    val total: Long get() = appBytes + dataBytes
}