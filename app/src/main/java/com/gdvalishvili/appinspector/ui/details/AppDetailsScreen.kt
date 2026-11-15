package com.gdvalishvili.appinspector.ui.details

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gdvalishvili.appinspector.R
import com.gdvalishvili.appinspector.data.AppDetailsExtras
import com.gdvalishvili.appinspector.data.DayUsage
import com.gdvalishvili.appinspector.data.PeriodChange
import com.gdvalishvili.appinspector.db.EventType
import com.gdvalishvili.appinspector.model.AppEntry
import com.gdvalishvili.appinspector.model.AppKind
import com.gdvalishvili.appinspector.model.AppProfile
import com.gdvalishvili.appinspector.ui.components.DescriptionLine
import com.gdvalishvili.appinspector.ui.components.installedText
import com.gdvalishvili.appinspector.ui.components.lastUsedText
import com.gdvalishvili.appinspector.ui.components.sizeText
import com.gdvalishvili.appinspector.ui.components.sourceText
import com.gdvalishvili.appinspector.ui.components.updatedText
import com.gdvalishvili.appinspector.ui.report.UsageBarChart
import com.gdvalishvili.appinspector.util.AppIcons
import com.gdvalishvili.appinspector.util.categoryTitle
import com.gdvalishvili.appinspector.util.formatDateTime
import com.gdvalishvili.appinspector.util.formatDuration
import com.gdvalishvili.appinspector.util.openAppInfo
import com.gdvalishvili.appinspector.util.openStorePage
import com.gdvalishvili.appinspector.util.requestUninstall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import com.gdvalishvili.appinspector.util.openApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailsScreen(
    app: AppEntry,
    onBack: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onRefreshList: () -> Unit,
    onOpenLogs: () -> Unit = {},
) {
    // One ViewModel per app
    val viewModel: AppDetailsViewModel = viewModel(key = "details:${app.key}")
    val extras by viewModel.extras.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }

    // Reload after returning (App info, permission changes, uninstall dialog)
    LifecycleResumeEffect(app.key) {
        viewModel.load(app)
        onRefreshList()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header(app, extras?.versionCode) }
            item { Actions(app, onOpenLogs) }
            val current = extras
            if (current == null) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            } else {
                current.days?.let { days -> item { UsageSection(days, today, onOpenDay) } }
                item { InfoSection(app, current) }
                current.permissions?.let { permissions -> item { PermissionsSection(permissions) } }
                if (current.events.isNotEmpty()) item { HistorySection(current.events) }
            }
        }
    }
}

@Composable
private fun Header(app: AppEntry, versionCode: Long?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BigIcon(app)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(app.label, style = MaterialTheme.typography.headlineSmall)
            Text(
                app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val parts = buildList {
                app.versionName?.let { add("v$it") }
                versionCode?.let { add(stringResource(R.string.details_version_code, it)) }
                when (app.kind) {
                    AppKind.SYSTEM -> add(stringResource(R.string.badge_system))
                    AppKind.UPDATED_SYSTEM -> add(stringResource(R.string.badge_updated_system))
                    AppKind.USER -> Unit
                }
                if (app.profile == AppProfile.WORK) add(stringResource(R.string.badge_work))
            }
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun BigIcon(app: AppEntry) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { 56.dp.roundToPx() }
    val icon by produceState<ImageBitmap?>(AppIcons.cached(app.key), app.key) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { AppIcons.load(context.applicationContext, app, sizePx) }
        }
    }
    val bitmap = icon
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(56.dp))
    } else {
        Box(
            Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
        )
    }
}

@Composable
private fun Actions(app: AppEntry, onOpenLogs: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (app.hasLauncher && !app.isArchived) {
            OutlinedButton(onClick = { openApp(context, app) }) {
                Text(stringResource(R.string.action_open))
            }
        }
        FilledTonalButton(onClick = { openAppInfo(context, app) }) {
            Text(stringResource(R.string.action_app_info))
        }
        OutlinedButton(onClick = { openStorePage(context, app.packageName) }) {
            Text(stringResource(R.string.action_store))
        }
        OutlinedButton(onClick = onOpenLogs) {
            Text(stringResource(R.string.logs_title))
        }
        // Only user apps in this profile can be uninstalled from here (system/work: via App info)
        if (app.profile == AppProfile.THIS && app.kind == AppKind.USER) {
            OutlinedButton(onClick = { requestUninstall(context, app.packageName) }) {
                Text(stringResource(R.string.action_uninstall))
            }
        }
    }
}

@Composable
private fun UsageSection(days: List<DayUsage>, today: LocalDate, onOpenDay: (LocalDate) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.details_usage_30), style = MaterialTheme.typography.titleMedium)
            UsageBarChart(
                days = days,
                today = today,
                onDayClick = onOpenDay,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            val total = days.sumOf { it.ms }
            // Average from the first day with data (history may be shorter than 30 days)
            val firstWithData = days.indexOfFirst { it.ms > 0L }
            val coveredDays = if (firstWithData < 0) 1 else days.size - firstWithData
            Text(
                stringResource(
                    R.string.details_usage_summary,
                    formatDuration(total),
                    formatDuration(total / coveredDays),
                    formatDuration(days.last().ms),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun InfoSection(app: AppEntry, extras: AppDetailsExtras) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.details_info), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            InfoLine(R.string.label_installed, installedText(app))
            InfoLine(R.string.label_updated, updatedText(app))
            InfoLine(R.string.label_last_used, lastUsedText(app, System.currentTimeMillis()))
            InfoLine(R.string.label_source, sourceText(app))
            InfoLine(R.string.label_category, categoryTitle(app.category))
            val target = extras.targetSdk
            val min = extras.minSdk
            if (target != null && min != null) {
                InfoLine(R.string.label_sdk, stringResource(R.string.details_sdk, target, min))
            }
            InfoLine(R.string.label_size, sizeText(extras.size, ready = true))
            Spacer(Modifier.height(4.dp))
            DescriptionLine(app)
        }
    }
}

@Composable
private fun InfoLine(@StringRes label: Int, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
    }
}

@Composable
private fun PermissionsSection(permissions: List<String>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.details_permissions, permissions.size),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            if (permissions.isEmpty()) {
                Text(
                    stringResource(R.string.details_permissions_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                permissions.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@Composable
private fun HistorySection(events: List<PeriodChange>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.details_history), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            events.forEach { e ->
                val type = when (e.type) {
                    EventType.INSTALLED -> R.string.event_installed
                    EventType.UPDATED -> R.string.event_updated
                    else -> R.string.event_removed
                }
                Text(
                    text = stringResource(type) + " · " +
                            (e.version?.let { "v$it · " } ?: "") +
                            (if (e.approx) "≈ " else "") + formatDateTime(e.time),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}