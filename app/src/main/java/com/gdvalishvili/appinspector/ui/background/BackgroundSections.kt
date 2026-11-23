package com.gdvalishvili.appinspector.ui.background

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gdvalishvili.appinspector.R
import com.gdvalishvili.appinspector.data.background.BackgroundCapabilities
import com.gdvalishvili.appinspector.data.background.BackgroundRepository
import com.gdvalishvili.appinspector.data.background.BackgroundSnapshot
import com.gdvalishvili.appinspector.model.AppEntry
import com.gdvalishvili.appinspector.model.AppProfile
import com.gdvalishvili.appinspector.util.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val sinceFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
private const val CLEANUP_DATA_THRESHOLD = 1_000_000L // 1 MB of background data in 7 days

/** "Background (7 days)" card for the details screen. */
@Composable
fun BackgroundDetailsSection(app: AppEntry, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val data by produceState<Pair<BackgroundSnapshot, BackgroundCapabilities?>?>(null, app.key) {
        if (app.profile != AppProfile.THIS) return@produceState
        val repo = BackgroundRepository(context.applicationContext)
        val snap = repo.load(listOf(app.packageName))
        val caps = withContext(Dispatchers.IO) { repo.capabilitiesOf(app.packageName) }
        value = snap to caps
    }

    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(R.string.bg_details_title), style = MaterialTheme.typography.titleSmall)
            val d = data
            when {
                app.profile != AppProfile.THIS -> Line(stringResource(R.string.bg_details_work))
                d == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                else -> {
                    val (snap, caps) = d
                    if (!snap.hasUsageAccess) {
                        Line(stringResource(R.string.bg_no_access))
                    } else {
                        val fgs = snap.fgs[app.packageName]
                        val running = fgs?.running
                        if (running != null) {
                            Line(
                                stringResource(
                                    R.string.bg_running_since,
                                    sinceFormatter.format(Instant.ofEpochMilli(running.start)),
                                    formatDuration(snap.now - running.start),
                                )
                            )
                        }
                        Line(
                            if (fgs != null && fgs.totalMs > 0L) {
                                stringResource(R.string.bg_fgs_line, formatDuration(fgs.totalMs), formatDuration(fgs.todayMs), fgs.starts)
                            } else stringResource(R.string.bg_details_no_fgs)
                        )
                        val net = snap.network[app.packageName]
                        Line(
                            if (net != null && net.backgroundBytes > 0L) {
                                val text = stringResource(
                                    R.string.bg_net_line,
                                    Formatter.formatShortFileSize(context, net.backgroundBytes),
                                    Formatter.formatShortFileSize(context, net.backgroundTodayBytes),
                                )
                                if (net.sharedUid) text + " · " + stringResource(R.string.bg_shared_uid) else text
                            } else stringResource(R.string.bg_details_no_data)
                        )
                        NetworkHistoryBlock(app.packageName)
                        val bucket = snap.buckets[app.packageName]
                        Line(
                            if (bucket != null) stringResource(R.string.bg_details_bucket, stringResource(bucketLabel(bucket)))
                            else stringResource(R.string.bg_details_bucket_unknown)
                        )
                    }
                    Line(
                        stringResource(
                            if (app.packageName in snap.batteryExempt) R.string.bg_details_battery_yes
                            else R.string.bg_details_battery_no
                        )
                    )
                    if (caps != null && !caps.isEmpty) {
                        val parts = buildList {
                            if (caps.startsOnBoot) add(stringResource(R.string.bg_cap_boot))
                            if (caps.exactAlarms) add(stringResource(R.string.bg_cap_alarm))
                            if (caps.wakeLock) add(stringResource(R.string.bg_cap_wakelock))
                            if (caps.asksBatteryExemption) add(stringResource(R.string.bg_cap_battery))
                            if (caps.fgsTypes.isNotEmpty()) {
                                add(stringResource(R.string.bg_cap_fgs, caps.fgsTypes.joinToString(", ")))
                            }
                        }
                        Line(stringResource(R.string.bg_details_caps, parts.joinToString(" · ")))
                    }
                }
            }
        }
    }
}

@Composable
private fun Line(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/**
 * Packages that were active in the background in the last 7 days (for the cleanup ⚠ mark):
 * any foreground service time, a running service, or at least 1 MB of background data.
 */
@Composable
fun rememberBackgroundActivePackages(apps: List<AppEntry>?): Set<String> {
    val context = LocalContext.current
    val result by produceState(emptySet<String>(), apps != null) {
        if (apps == null) return@produceState
        val snap = BackgroundRepository(context.applicationContext).load(emptyList())
        if (!snap.hasUsageAccess) return@produceState
        value = buildSet {
            snap.fgs.values.forEach { if (it.totalMs > 0L || it.running != null) add(it.packageName) }
            snap.network.forEach { (pkg, net) -> if (net.backgroundBytes >= CLEANUP_DATA_THRESHOLD) add(pkg) }
        }
    }
    return result
}