package com.gdvalishvili.appinspector.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.gdvalishvili.appinspector.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

// Device time zone (Asia/Tbilisi on this phone)
private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault())

fun formatDate(epochMs: Long): String = dateFormatter.format(Instant.ofEpochMilli(epochMs))
private val dateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault())

fun formatDateTime(epochMs: Long): String = dateTimeFormatter.format(Instant.ofEpochMilli(epochMs))
@Composable
fun relativeTime(epochMs: Long, now: Long): String {
    val days = TimeUnit.MILLISECONDS.toDays(now - epochMs).coerceAtLeast(0L).toInt()
    return when {
        days < 1 -> stringResource(R.string.rel_today)
        days < 2 -> stringResource(R.string.rel_yesterday)
        days < 30 -> pluralStringResource(R.plurals.rel_days_ago, days, days)
        days < 365 -> (days / 30).let { pluralStringResource(R.plurals.rel_months_ago, it, it) }
        else -> (days / 365).let { pluralStringResource(R.plurals.rel_years_ago, it, it) }
    }
}

@Composable
fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000L
    val hours = (totalMinutes / 60).toInt()
    val minutes = (totalMinutes % 60).toInt()
    return if (hours > 0) stringResource(R.string.duration_hm, hours, minutes)
    else stringResource(R.string.duration_m, minutes)
}

/** Same as formatDuration, for code outside Compose (notifications). */
fun formatDurationText(context: android.content.Context, ms: Long): String {
    val totalMinutes = ms / 60_000L
    val hours = (totalMinutes / 60).toInt()
    val minutes = (totalMinutes % 60).toInt()
    return if (hours > 0) context.getString(R.string.duration_hm, hours, minutes)
    else context.getString(R.string.duration_m, minutes)
}