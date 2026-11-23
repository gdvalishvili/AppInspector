package com.gdvalishvili.appinspector.ui.privacy

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gdvalishvili.appinspector.R
import com.gdvalishvili.appinspector.data.privacy.PrivacyResult
import com.gdvalishvili.appinspector.data.privacy.RiskLevel
import com.gdvalishvili.appinspector.model.AppEntry
import com.gdvalishvili.appinspector.model.AppKind
import com.gdvalishvili.appinspector.model.AppProfile
import com.gdvalishvili.appinspector.util.AppIcons
import com.gdvalishvili.appinspector.util.formatDateTime
import com.gdvalishvili.appinspector.util.openAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

enum class PrivacyFilter { ALL, HIGH, WITH_TRACKERS, NO_TRACKERS }

private data class PrivacyRow(val app: AppEntry, val result: PrivacyResult)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    apps: List<AppEntry>?,
    onBack: () -> Unit,
    onOpenDetails: (String) -> Unit,
    viewModel: PrivacyViewModel = viewModel(),
) {
    val results by viewModel.results.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val levelNames = mapOf(
        RiskLevel.LOW to stringResource(R.string.pv_level_low),
        RiskLevel.MEDIUM to stringResource(R.string.pv_level_medium),
        RiskLevel.HIGH to stringResource(R.string.pv_level_high),
    )
    var filter by rememberSaveable { mutableStateOf(PrivacyFilter.ALL) }
    var includeSystem by rememberSaveable { mutableStateOf(false) }

    // Only this profile is scanned; system apps only when the switch is on
    val targets = remember(apps, includeSystem) {
        apps.orEmpty().filter {
            it.profile == AppProfile.THIS && !it.isArchived && (includeSystem || it.kind == AppKind.USER)
        }
    }
    LaunchedEffect(apps) { apps?.let { viewModel.loadCached(it.filter { a -> a.profile == AppProfile.THIS }) } }

    val rows = remember(results, targets) {
        val map = results.orEmpty()
        targets.mapNotNull { app -> map[app.key]?.let { PrivacyRow(app, it) } }
            .sortedWith(
                compareByDescending<PrivacyRow> { it.result.risk.level.ordinal }
                    .thenByDescending { it.result.risk.score }
                    .thenBy(Collator.getInstance()) { it.app.label }
            )
    }
    val counts = PrivacyFilter.entries.associateWith { f -> rows.count { it.matches(f) } }
    val visible = rows.filter { it.matches(filter) }
    val lastScan = rows.maxOfOrNull { it.result.scannedAt }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pv_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.pv_intro), style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.size(8.dp))
                        val p = progress
                        if (p != null) {
                            LinearProgressIndicator(
                                progress = { if (p.second == 0) 0f else p.first.toFloat() / p.second },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                stringResource(R.string.pv_scanning, p.first, p.second),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        } else {
                            Button(onClick = { apps?.let { viewModel.scan(targets) } }, enabled = apps != null) {
                                Text(stringResource(if (rows.isEmpty()) R.string.pv_scan else R.string.pv_rescan))
                            }
                            Text(
                                if (lastScan == null) stringResource(R.string.pv_not_scanned)
                                else stringResource(R.string.pv_scanned, rows.size, targets.size, formatDateTime(lastScan)),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        TextButton(onClick = { openPrivacySettings(context) }) {
                            if (rows.isNotEmpty() && progress == null) {
                                TextButton(onClick = {
                                    scope.launch { exportPrivacyCsv(context, visible.map { it.app to it.result }, levelNames) }
                                }) { Text(stringResource(R.string.pv_export)) }
                            }
                            Text(stringResource(R.string.pv_dashboard))
                        }
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PrivacyFilter.entries.forEach { f ->
                        val label = when (f) {
                            PrivacyFilter.ALL -> R.string.pv_filter_all
                            PrivacyFilter.HIGH -> R.string.pv_filter_high
                            PrivacyFilter.WITH_TRACKERS -> R.string.pv_filter_trackers
                            PrivacyFilter.NO_TRACKERS -> R.string.pv_filter_clean
                        }
                        FilterChip(
                            selected = filter == f,
                            onClick = { filter = f },
                            label = { Text(stringResource(label, counts[f] ?: 0)) },
                        )
                    }
                    FilterChip(
                        selected = includeSystem,
                        onClick = { includeSystem = !includeSystem },
                        label = { Text(stringResource(R.string.pv_include_system)) },
                    )
                }
            }
            when {
                results == null -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                rows.isEmpty() -> Unit // the card above explains what to do
                visible.isEmpty() -> item {
                    Text(
                        stringResource(R.string.pv_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                else -> items(visible, key = { it.app.key }) { row ->
                    PrivacyRowItem(
                        row = row,
                        onClick = { onOpenDetails(row.app.key) },
                        onLongClick = { openAppInfo(context, row.app) },
                    )
                }
            }
            item {
                Text(
                    stringResource(R.string.pv_attribution),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

private fun PrivacyRow.matches(f: PrivacyFilter) = when (f) {
    PrivacyFilter.ALL -> true
    PrivacyFilter.HIGH -> result.risk.level == RiskLevel.HIGH
    PrivacyFilter.WITH_TRACKERS -> result.trackers.isNotEmpty()
    PrivacyFilter.NO_TRACKERS -> result.trackers.isEmpty()
}

/** No public intent opens the Privacy dashboard itself: open Privacy settings (one tap from there). */
private fun openPrivacySettings(context: android.content.Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_PRIVACY_SETTINGS))
    } catch (e: Exception) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PrivacyRowItem(row: PrivacyRow, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SmallIcon(row.app)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(row.app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                shortSummary(row.result),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.result.skippedDex > 0) {
                Text(stringResource(R.string.pv_partial), style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            levelLabel(row.result.risk.level),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = levelColor(row.result.risk.level),
        )
    }
}

@Composable
private fun SmallIcon(app: AppEntry) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { 36.dp.roundToPx() }
    val icon by produceState(AppIcons.cached(app.key), app.key) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { AppIcons.load(context.applicationContext, app, sizePx) }
        }
    }
    val bitmap: ImageBitmap? = icon
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(36.dp))
    } else {
        Box(
            Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
        )
    }
}