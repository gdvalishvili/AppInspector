package com.gdvalishvili.appinspector.ui.updates

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gdvalishvili.appinspector.R
import com.gdvalishvili.appinspector.data.PeriodChange
import com.gdvalishvili.appinspector.model.AppEntry
import com.gdvalishvili.appinspector.model.AppProfile
import com.gdvalishvili.appinspector.util.AppIcons
import com.gdvalishvili.appinspector.util.formatDate
import com.gdvalishvili.appinspector.util.formatDateTime
import com.gdvalishvili.appinspector.util.openPlayStoreApp
import com.gdvalishvili.appinspector.util.openStorePage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private const val PLAY_STORE = "com.android.vending"

/** Minimum age (days since the last update) for each chip. */
enum class UpdateThreshold(val minDays: Long) { ALL(0), DAYS_30(30), DAYS_90(90) }

private data class UpdateRow(
    val app: AppEntry,
    val lastChange: Long,      // last update, or install time if never updated
    val days: Long,
    val neverUpdated: Boolean,
)

/**
 * Apps installed/updated by Play, oldest update first.
 * Cannot tell whether an update is available (no public API): shows how long ago each app was updated.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(
    apps: List<AppEntry>?,
    onBack: () -> Unit,
    onOpenDetails: (String) -> Unit,
    onRefreshList: () -> Unit,
    viewModel: UpdatesViewModel = viewModel(),
) {
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var threshold by rememberSaveable { mutableStateOf(UpdateThreshold.ALL) }
    var recentExpanded by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onRefreshList()
        onPauseOrDispose { }
    }

    val rows = remember(apps) {
        val now = System.currentTimeMillis()
        apps.orEmpty()
            .filter { it.profile == AppProfile.THIS && it.installerPackage == PLAY_STORE }
            .mapNotNull { app ->
                val last = app.updatedAt ?: app.installedAt ?: return@mapNotNull null
                UpdateRow(
                    app = app,
                    lastChange = last,
                    days = TimeUnit.MILLISECONDS.toDays(now - last).coerceAtLeast(0L),
                    neverUpdated = app.updatedAt == null,
                )
            }
            .sortedBy { it.lastChange }
    }
    val counts = UpdateThreshold.entries.associateWith { t -> rows.count { it.days >= t.minDays } }
    val visible = rows.filter { it.days >= threshold.minDays }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.updates_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back),
                        )
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
            item { TipCard(onOpenPlay = { openPlayStoreApp(context) }) }
            item {
                RecentCard(
                    recent = recent,
                    expanded = recentExpanded,
                    onToggle = { recentExpanded = !recentExpanded },
                )
            }
            item {
                ThresholdRow(current = threshold, counts = counts, onSelect = { threshold = it })
            }
            when {
                apps == null -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                visible.isEmpty() -> item {
                    Text(
                        text = stringResource(R.string.updates_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
                else -> items(visible, key = { it.app.key }) { row ->
                    UpdateRowItem(
                        row = row,
                        onClick = { openStorePage(context, row.app.packageName) },
                        onLongClick = { onOpenDetails(row.app.key) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TipCard(onOpenPlay: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(R.string.updates_tip), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onOpenPlay, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.updates_open_play))
            }
        }
    }
}

@Composable
private fun RecentCard(recent: List<PeriodChange>, expanded: Boolean, onToggle: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = (if (expanded) "▾ " else "▸ ") + stringResource(R.string.updates_recent, recent.size),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = recent.isNotEmpty(), onClick = onToggle),
            )
            if (expanded) {
                recent.forEach { c ->
                    Text(
                        text = c.label + (c.version?.let { " · v$it" } ?: "") + " · " +
                                (if (c.approx) "≈ " else "") + formatDateTime(c.time),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ThresholdRow(
    current: UpdateThreshold,
    counts: Map<UpdateThreshold, Int>,
    onSelect: (UpdateThreshold) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        UpdateThreshold.entries.forEach { t ->
            val label = when (t) {
                UpdateThreshold.ALL -> R.string.updates_all
                UpdateThreshold.DAYS_30 -> R.string.updates_30
                UpdateThreshold.DAYS_90 -> R.string.updates_90
            }
            FilterChip(
                selected = current == t,
                onClick = { onSelect(t) },
                label = { Text(stringResource(label, counts[t] ?: 0)) },
            )
        }
    }
}

/** Tap = Play Store page (to press "Update"), long-press = our details screen. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UpdateRowItem(row: UpdateRow, onClick: () -> Unit, onLongClick: () -> Unit) {
    val dark = isSystemInDarkTheme()
    val staleColor = if (dark) Color(0xFFFFB870) else Color(0xFF8A4B00) // orange, 90+ days
    val dateLabel = stringResource(if (row.neverUpdated) R.string.label_installed else R.string.label_updated)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SmallIcon(row.app)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = row.app.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = (row.app.versionName?.let { "v$it · " } ?: "") + dateLabel + ": " + formatDate(row.lastChange),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = pluralStringResource(R.plurals.updates_days, row.days.toInt(), row.days.toInt()) + "  ↗",
            style = MaterialTheme.typography.labelLarge,
            color = if (row.days >= UpdateThreshold.DAYS_90.minDays) staleColor
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SmallIcon(app: AppEntry) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { 36.dp.roundToPx() }
    val icon by produceState<ImageBitmap?>(AppIcons.cached(app.key), app.key) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { AppIcons.load(context.applicationContext, app, sizePx) }
        }
    }
    val bitmap = icon
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