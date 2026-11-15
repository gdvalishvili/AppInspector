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
import com.gdvalishvili.appinspector.notify.ReportNotifier
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import com.gdvalishvili.appinspector.widget.UsageWidgetUpdater

/**
 * Runs once a day at the chosen time (approximate: WorkManager saves battery). Survives reboots.
 * 1) sync install/update/removal journal  2) store usage of finished days  3) send due reports
 */
class DailyWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        val context = applicationContext
        val snapshot = InstalledAppsRepository(context).load()
        AppHistoryRepository(context).sync(snapshot.apps, snapshot.workProfile)
        UsageCollector(context).collectMissingDays()
        ReportNotifier.sendDueReports(context)
        UsageWidgetUpdater.refreshAll(applicationContext)
        Result.success()
    } catch (e: Exception) {
        Log.e(TAG, "Daily work failed", e)
        Result.retry()
    }

    companion object {
        private const val TAG = "AppInspector"
        private const val UNIQUE_NAME = "daily_work"

        /**
         * replace = false: keep the existing schedule (app start).
         * replace = true: the user changed the time -> reschedule.
         */
        fun schedule(context: Context, hour: Int, minute: Int, replace: Boolean) {
            val now = ZonedDateTime.now()
            var next = now.toLocalDate().atTime(hour, minute).atZone(now.zone)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delay = Duration.between(now, next)

            val request = PeriodicWorkRequestBuilder<DailyWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
                else ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}