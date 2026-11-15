package com.gdvalishvili.appinspector.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.gdvalishvili.appinspector.db.AppDatabase
import com.gdvalishvili.appinspector.model.AppEntry
import com.gdvalishvili.appinspector.model.AppProfile
import com.gdvalishvili.appinspector.model.AppSize
import java.time.LocalDate
import java.time.ZoneId

/** Everything the details screen shows on top of the list data (AppEntry). */
data class AppDetailsExtras(
    val versionCode: Long?,
    val targetSdk: Int?,
    val minSdk: Int?,
    val size: AppSize?,
    val days: List<DayUsage>?,        // last 30 days incl. today; null = not available (work / no access)
    val permissions: List<String>?,   // granted "dangerous" permissions; null = not available (work)
    val events: List<PeriodChange>,   // install / update / removal history of this app
)

class AppDetailsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val pm = context.packageManager
    private val dao = AppDatabase.get(context).usageDao()
    private val collector = UsageCollector(context)
    private val sizes = SizeRepository(context)
    private val usage = UsageRepository(context)

    suspend fun load(app: AppEntry, zone: ZoneId = ZoneId.systemDefault()): AppDetailsExtras {
        val isThisProfile = app.profile == AppProfile.THIS
        val info = if (isThisProfile) packageInfo(app.packageName) else null
        val appInfo = info?.applicationInfo ?: workAppInfo(app)

        return AppDetailsExtras(
            versionCode = info?.let { PackageInfoCompat.getLongVersionCode(it) },
            targetSdk = appInfo?.targetSdkVersion,
            minSdk = appInfo?.minSdkVersion,
            size = sizes.sizeOf(app),
            days = if (isThisProfile && usage.hasAccess()) last30Days(app.packageName, zone) else null,
            permissions = info?.let { grantedDangerousPermissions(it) },
            events = dao.eventsForPackage(app.packageName, app.profile.name).map {
                PeriodChange(it.type, it.packageName, it.label, it.version, it.time, it.approx)
            },
        )
    }

    private fun packageInfo(packageName: String): PackageInfo? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        }
    } catch (e: Exception) {
        null
    }

    private fun workAppInfo(app: AppEntry): ApplicationInfo? = try {
        app.user?.let { user ->
            appContext.getSystemService(LauncherApps::class.java).getApplicationInfo(app.packageName, 0, user)
        }
    } catch (e: Exception) {
        null
    }

    /** Runtime ("dangerous") permissions that are currently granted, with system-translated names. */
    private fun grantedDangerousPermissions(info: PackageInfo): List<String> {
        val names = info.requestedPermissions ?: return emptyList()
        val flags = info.requestedPermissionsFlags ?: return emptyList()
        return names.indices.mapNotNull { i ->
            val granted = (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
            if (!granted) return@mapNotNull null
            val permission = try {
                pm.getPermissionInfo(names[i], 0)
            } catch (e: Exception) {
                return@mapNotNull null
            }
            val dangerous = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                permission.protection == PermissionInfo.PROTECTION_DANGEROUS
            } else {
                @Suppress("DEPRECATION")
                (permission.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE) ==
                        PermissionInfo.PROTECTION_DANGEROUS
            }
            if (!dangerous) return@mapNotNull null
            permission.loadLabel(pm).toString().replaceFirstChar { it.uppercase() }
        }.distinct().sorted()
    }

    /** Stored days + today counted live (same algorithm as reports). */
    private suspend fun last30Days(packageName: String, zone: ZoneId): List<DayUsage> {
        val today = LocalDate.now(zone)
        val from = today.minusDays(29)
        val stored = dao.dailyTotalsForPackage(packageName, from.toString(), today.minusDays(1).toString())
            .mapNotNull { row -> runCatching { LocalDate.parse(row.day) }.getOrNull()?.let { it to row.ms } }
            .toMap()
        val todayMs = collector.foregroundTimes(today, zone)[packageName] ?: 0L
        return (0L..29L).map { offset ->
            val day = from.plusDays(offset)
            DayUsage(day, if (day == today) todayMs else stored[day] ?: 0L)
        }
    }
}