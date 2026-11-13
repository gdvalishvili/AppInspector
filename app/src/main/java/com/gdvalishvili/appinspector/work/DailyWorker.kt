package com.gdvalishvili.appinspector.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gdvalishvili.appinspector.data.AppHistoryRepository
import com.gdvalishvili.appinspector.data.InstalledAppsRepository
import com.gdvalishvili.appinspector.data.UsageCollector
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Runs once a day (~09:00, not exact: WorkManager saves battery). Survives reboots.
 * 1) sync install/update/removal journal  2) store usage of finished days
 * 3) notifications: added in stage 2.0-d
 */
class DailyWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        val context = applicationContext
        val snapshot = InstalledAppsRepository(context).load()
        AppHistoryRepository(context).sync(snapshot.apps, snapshot.workProfile)
        UsageCollector(context).collectMissingDays()
        Result.success()
    } catch (e: Exception) {
        Log.e(TAG, "Daily work failed", e)
        Result.retry()
    }

    companion object {
        private const val TAG = "AppInspector"
        private const val UNIQUE_NAME = "daily_work"

        /** Schedules the daily job once; KEEP = do nothing if it already exists. */
        fun schedule(context: Context, hour: Int = 9) {
            val now = ZonedDateTime.now()
            var next = now.toLocalDate().atTime(hour, 0).atZone(now.zone)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delay = Duration.between(now, next)

            val request = PeriodicWorkRequestBuilder<DailyWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}