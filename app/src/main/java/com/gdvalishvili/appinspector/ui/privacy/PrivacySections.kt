package com.gdvalishvili.appinspector.ui.privacy

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gdvalishvili.appinspector.R
import com.gdvalishvili.appinspector.data.privacy.ExportRow
import com.gdvalishvili.appinspector.data.privacy.PrivacyExport
import com.gdvalishvili.appinspector.data.privacy.PrivacyRepository
import com.gdvalishvili.appinspector.data.privacy.PrivacyResult
import com.gdvalishvili.appinspector.data.privacy.RiskLevel
import com.gdvalishvili.appinspector.data.privacy.RiskRules
import com.gdvalishvili.appinspector.model.AppEntry
import com.gdvalishvili.appinspector.model.AppProfile
import com.gdvalishvili.appinspector.util.formatDateTime
import com.gdvalishvili.appinspector.util.shareText
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** "Privacy" card for the details screen: level, reasons, trackers with what they usually collect. */
@Composable
fun PrivacyDetailsSection(app: AppEntry, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    var scanning by remember { mutableStateOf(false) }
    val result by produceState<PrivacyResult?>(null, app.key, reload) {
        if (app.profile != AppProfile.THIS) return@produceState
        value = PrivacyRepository(context.applicationContext).cached(listOf(app))[app.key]
    }

    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.pv_details_title), style = MaterialTheme.typography.titleMedium)
            val r = result
            when {
                app.profile != AppProfile.THIS -> Small(stringResource(R.string.bg_details_work))
                scanning -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                r == null -> {
                    Small(stringResource(R.string.pv_details_not_scanned))
                    TextButton(onClick = {
                        scanning = true
                        scope.launch {
                            PrivacyRepository(context.applicationContext).scan(listOf(app)) { _, _ -> }
                            scanning = false
                            reload++
                        }
                    }) { Text(stringResource(R.string.pv_details_scan)) }
                }
                else -> {
                    Text(
                        stringResource(R.string.pv_details_level, levelLabel(r.risk.level), r.risk.score),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = levelColor(r.risk.level),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    r.risk.reasons.forEach { Small(reasonLine(it)) }
                    if (r.risk.reasons.count { it.kind.isTracker } > 0 &&
                        r.risk.reasons.filter { it.kind.isTracker }.sumOf { it.points } > RiskRules.TRACKER_CAP
                    ) {
                        Small(stringResource(R.string.pv_trackers_capped, RiskRules.TRACKER_CAP))
                    }
                    if (r.skippedDex > 0) Small(stringResource(R.string.pv_partial))
                    if (r.trackers.isNotEmpty()) {
                        Text(
                            stringResource(R.string.pv_details_trackers, r.trackers.size),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        SelectionContainer {
                            Column {
                                r.trackers.forEach { t ->
                                    Text(
                                        t.name + " · " + t.categories.joinToString(", "),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(top = 6.dp),
                                    )
                                    Small(stringResource(R.string.pv_details_collects, typicalCollection(t.categories)))
                                    if (t.website.isNotBlank()) Small(t.website)
                                }
                            }
                        }
                    }
                    Small(stringResource(R.string.pv_details_disclaimer))
                }
            }
        }
    }
}

@Composable
private fun Small(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/** Packages with HIGH privacy risk (from the cache only), for the cleanup mark. */
@Composable
fun rememberHighRiskPackages(apps: List<AppEntry>?): Set<String> {
    val context = LocalContext.current
    val result by produceState(emptySet<String>(), apps != null) {
        if (apps == null) return@produceState
        value = PrivacyRepository(context.applicationContext).cached(apps)
            .values.filter { it.risk.level == RiskLevel.HIGH }
            .mapTo(HashSet()) { it.packageName }
    }
    return result
}

/** Builds the CSV of the given rows and opens the share sheet. */
suspend fun exportPrivacyCsv(context: Context, rows: List<Pair<AppEntry, PrivacyResult>>, levelNames: Map<RiskLevel, String>) {
    val csv = PrivacyExport.csv(
        rows.map { (app, r) ->
            ExportRow(
                app = app.label,
                packageName = app.packageName,
                level = levelNames[r.risk.level] ?: r.risk.level.name,
                score = r.risk.score,
                trackers = r.trackers.map { it.name },
                categories = r.trackers.flatMap { it.categories }.distinct(),
                permissions = r.grantedSensitive.map { it.substringAfterLast('.') }.sorted(),
                backgroundMb7d = r.backgroundBytes7d / (1024 * 1024),
                scannedAt = formatDateTime(r.scannedAt),
            )
        }
    )
    val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
    shareText(context, "privacy_$stamp.csv", csv)
}