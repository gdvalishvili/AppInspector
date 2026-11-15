package com.gdvalishvili.appinspector.data

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Process
import android.os.storage.StorageManager
import com.gdvalishvili.appinspector.model.AppEntry
import com.gdvalishvili.appinspector.model.AppProfile
import com.gdvalishvili.appinspector.model.AppSize

/** App sizes via StorageStatsManager (needs Usage access, which we already have). */
class SizeRepository(context: Context) {

    private val storageStats = context.getSystemService(StorageStatsManager::class.java)

    fun sizeOf(app: AppEntry): AppSize? {
        val user = if (app.profile == AppProfile.THIS) Process.myUserHandle() else app.user ?: return null
        return try {
            val stats = storageStats.queryStatsForPackage(StorageManager.UUID_DEFAULT, app.packageName, user)
            AppSize(stats.appBytes, stats.dataBytes, stats.cacheBytes)
        } catch (e: Exception) {
            null // e.g. work profile not accessible, or app removed meanwhile
        }
    }

    /** key (AppEntry.key) -> size. Slow-ish (a few seconds for ~500 apps): call off the main thread. */
    fun sizesOf(apps: List<AppEntry>): Map<String, AppSize> =
        apps.mapNotNull { app -> sizeOf(app)?.let { app.key to it } }.toMap()
}