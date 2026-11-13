package com.gdvalishvili.appinspector.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import com.gdvalishvili.appinspector.model.AppKind
import java.io.File

/** System image uses 2009-01-01 as a placeholder install time; anything before 2010 is "factory". */
internal const val FACTORY_INSTALL_THRESHOLD_MS = 1_262_304_000_000L // 2010-01-01 UTC

internal fun kindOf(ai: ApplicationInfo): AppKind = when {
    (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 -> AppKind.UPDATED_SYSTEM
    (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 -> AppKind.SYSTEM
    else -> AppKind.USER
}

internal fun isArchivedCompat(ai: ApplicationInfo): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && ai.isArchived

internal fun PackageManager.installedPackagesCompat(): List<PackageInfo> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM ->
        // Android 15+: include archived apps too
        getInstalledPackages(
            PackageManager.PackageInfoFlags.of(PackageManager.MATCH_ARCHIVED_PACKAGES)
        )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
        getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
    else -> {
        @Suppress("DEPRECATION")
        getInstalledPackages(0)
    }
}

/** Package names that have a launcher icon in the current profile (one query for all apps). */
internal fun PackageManager.launcherPackageNames(): Set<String> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        queryIntentActivities(intent, 0)
    }
    return activities.mapTo(HashSet()) { it.activityInfo.packageName }
}

/** Reads versionName directly from the APK file (works for work-profile apps, verified in spike). */
internal fun PackageManager.versionNameFromApk(apkPath: String?): String? {
    if (apkPath == null) return null
    return try {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getPackageArchiveInfo(apkPath, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            getPackageArchiveInfo(apkPath, 0)
        }
        info?.versionName
    } catch (e: Exception) {
        null
    }
}

/** APK file modification time: approximate "last update" for work-profile apps. */
internal fun apkFileTime(apkPath: String?): Long? {
    if (apkPath == null) return null
    return File(apkPath).lastModified().takeIf { it > 0L }
}

/** True when this copy runs inside a profile (e.g. Private Space), not the main user. */
fun isRunningInProfile(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            context.getSystemService(UserManager::class.java).isProfile

/** Package that installed the app (Play Store, file manager, shell...). null = preinstalled/unknown. */
internal fun PackageManager.installerPackageCompat(packageName: String): String? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        getInstallSourceInfo(packageName).installingPackageName
    } else {
        @Suppress("DEPRECATION")
        getInstallerPackageName(packageName)
    }
} catch (e: Exception) {
    null
}

/** Human-readable app name for a package, or null if not visible/installed. */
@Suppress("DEPRECATION")
internal fun PackageManager.appLabelOrNull(packageName: String): String? = try {
    getApplicationLabel(getApplicationInfo(packageName, 0)).toString()
} catch (e: Exception) {
    null
}

/** Package of the default home app (launcher), shown as a separate line in reports. */
internal fun PackageManager.defaultLauncherPackage(): String? {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        resolveActivity(
            intent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
        )
    } else {
        @Suppress("DEPRECATION")
        resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    }
    return info?.activityInfo?.packageName
}