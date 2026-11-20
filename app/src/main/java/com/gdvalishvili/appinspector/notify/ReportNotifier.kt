package com.gdvalishvili.appinspector.notify

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.gdvalishvili.appinspector.MainActivity
import com.gdvalishvili.appinspector.R
import com.gdvalishvili.appinspector.data.Report
import com.gdvalishvili.appinspector.data.ReportRepository
import com.gdvalishvili.appinspector.data.SettingsStore
import com.gdvalishvili.appinspector.db.EventType
import com.gdvalishvili.appinspector.model.Period
import com.gdvalishvili.appinspector.model.PeriodType
import com.gdvalishvili.appinspector.util.formatDurationText
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

/** Builds and posts the report notification (silent or with sound, per settings). */
object ReportNotifier {

    const val EXTRA_PERIOD_TYPE = "period_type"
    const val EXTRA_PERIOD_START = "period_start"
    private const val CHANNEL_ID = "reports"
    private const val CHANNEL_SOUND_ID = "reports_sound"
    private const val NOTIFICATION_ID = 1001
    private const val TOP_APPS = 3

    /** True when the user allows notifications for this app (includes the Android 13+ permission). */
    fun canNotify(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * Sends one notification with all due sections.
     * force = true: "Show report now" -> sends the enabled sections (or yesterday) without marking them sent.
     */
    suspend fun sendDueReports(context: Context, force: Boolean = false) {
        val settings = SettingsStore(context)
        val rs = settings.reportSettingsNow()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)

        val yesterday = Period.of(PeriodType.DAY, today.minusDays(1))
        val lastWeek = Period.of(PeriodType.WEEK, today).previous()
        val lastMonth = Period.of(PeriodType.MONTH, today).previous()

        val periods = buildList {
            if (force) {
                if (rs.daily || (!rs.weekly && !rs.monthly)) add(yesterday)
                if (rs.weekly) add(lastWeek)
                if (rs.monthly) add(lastMonth)
            } else {
                // Not sent yet -> due (also catches up if the phone was off on Monday / the 1st)
                if (rs.daily && settings.lastSent(PeriodType.DAY) != yesterday.start.toString()) add(yesterday)
                if (rs.weekly && settings.lastSent(PeriodType.WEEK) != lastWeek.start.toString()) add(lastWeek)
                if (rs.monthly && settings.lastSent(PeriodType.MONTH) != lastMonth.start.toString()) add(lastMonth)
            }
        }
        if (periods.isEmpty() || !canNotify(context)) return // not marked as sent -> retried next time

        val repository = ReportRepository(context)
        val reports = periods.map { it to repository.load(it, zone) }
        post(context, reports, withSound = settings.notifySound.first())

        if (!force) periods.forEach { settings.setLastSent(it.type, it.start) }
    }

    /** Two channels: Android does not let an app change a channel's sound after it is created. */
    private fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_reports),
                NotificationManager.IMPORTANCE_LOW, // silent
            ).apply { description = context.getString(R.string.channel_reports_desc) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SOUND_ID,
                context.getString(R.string.channel_reports_sound),
                NotificationManager.IMPORTANCE_DEFAULT, // default sound
            ).apply { description = context.getString(R.string.channel_reports_desc) }
        )
    }

    @SuppressLint("MissingPermission") // checked with canNotify() before calling
    private fun post(context: Context, reports: List<Pair<Period, Report>>, withSound: Boolean) {
        ensureChannels(context)
        val lines = reports.map { (period, report) -> sectionText(context, period, report) }

        // Tap opens the biggest period of this notification (month > week > day)
        val target = reports.maxBy { it.first.type.ordinal }.first
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_PERIOD_TYPE, target.type.name)
            putExtra(EXTRA_PERIOD_START, target.start.toString())
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, if (withSound) CHANNEL_SOUND_ID else CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_title))
            .setContentText(lines.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n\n")))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(if (withSound) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission revoked between check and post: nothing to do
        }
    }

    /** "Yesterday: 6h 35m · Lichess 3h 21m, Facebook 1h 16m, Chess Prep 18m · +1 / −0 apps" */
    private fun sectionText(context: Context, period: Period, report: Report): String {
        fun d(ms: Long) = formatDurationText(context, ms)
        val header = when (period.type) {
            PeriodType.DAY -> context.getString(R.string.notif_yesterday, d(report.totalMs))
            PeriodType.WEEK -> context.getString(R.string.notif_week, d(report.totalMs), d(report.dailyAverageMs))
            PeriodType.MONTH -> context.getString(R.string.notif_month, d(report.totalMs), d(report.dailyAverageMs))
        }
        val top = report.apps.take(TOP_APPS).joinToString(", ") { "${it.label} ${d(it.ms)}" }
        val installed = report.changes.count { it.type == EventType.INSTALLED }
        val removed = report.changes.count { it.type == EventType.REMOVED }
        return buildString {
            append(header)
            if (top.isNotEmpty()) append(" · ").append(top)
            if (installed + removed > 0) append(context.getString(R.string.notif_changes, installed, removed))
        }
    }
}