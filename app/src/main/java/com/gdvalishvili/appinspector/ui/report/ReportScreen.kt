package com.gdvalishvili.appinspector.ui.report

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gdvalishvili.appinspector.R
import com.gdvalishvili.appinspector.data.AppUsage
import com.gdvalishvili.appinspector.data.PeriodChange
import com.gdvalishvili.appinspector.data.Report
import com.gdvalishvili.appinspector.db.EventType
import com.gdvalishvili.appinspector.model.Period
import com.gdvalishvili.appinspector.model.PeriodType
import com.gdvalishvili.appinspector.util.AppIcons
import com.gdvalishvili.appinspector.util.formatDateTime
import com.gdvalishvili.appinspector.util.formatDuration
import com.gdvalishvili.appinspector.util.openAppInfoForPackage
import com.gdvalishvili.appinspector.util.openStorePage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import androidx.compose.runtime.LaunchedEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    onBack: () -> Unit,
    requestedPeriod: Period? = null,
    onRequestHandled: () -> Unit = {},
    viewModel: ReportViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    var showPicker by remember { mutableStateOf(false) }

    // Opened from a notification: show that period
    LaunchedEffect(requestedPeriod) {
        if (requestedPeriod != null) {
            viewModel.open(requestedPeriod)
            onRequestHandled()
        }
    }

    // Reload when returning to the screen (today's numbers keep growing)
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.report_title)) },
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
            item { PeriodTypeRow(state.period.type, viewModel::setType) }
            item {
                PeriodNavigator(
                    label = periodLabel(state.period, state.today),
                    canGoBack = state.canGoBack,
                    canGoForward = state.canGoForward,
                    onPrevious = viewModel::previous,
                    onNext = viewModel::next,
                    onPick = { showPicker = true },
                )
            }
            item {
                Box(Modifier.fillMaxWidth().height(2.dp)) {
                    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }

            val report = state.report
            if (report != null) {
                item { SummaryCard(report) }
                if (report.period.type != PeriodType.DAY) {
                    item {
                        UsageBarChart(
                            days = report.days,
                            today = state.today,
                            onDayClick = viewModel::openDay,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
                item { SortRow(state.sort, viewModel::setSort) }

                val apps = if (state.sort == ReportSort.NAME) {
                    val collator = Collator.getInstance(locale)
                    report.apps.sortedWith(compareBy(collator) { it.label })
                } else {
                    report.apps
                }
                if (apps.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.report_no_data),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
                items(apps, key = { it.packageName }) { app ->
                    ReportAppRow(app, report.totalMs) {
                        if (app.installed) openAppInfoForPackage(context, app.packageName)
                        else openStorePage(context, app.packageName)
                    }
                }
                if (report.changes.isNotEmpty()) {
                    item { ChangesSection(report.changes) }
                }
            }
        }
    }

    if (showPicker) {
        PeriodDatePicker(
            initial = state.period.start,
            firstDay = state.report?.firstDataDay ?: state.today,
            today = state.today,
            onPick = viewModel::pickDate,
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun PeriodTypeRow(current: PeriodType, onSelect: (PeriodType) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            PeriodType.DAY to R.string.period_day,
            PeriodType.WEEK to R.string.period_week,
            PeriodType.MONTH to R.string.period_month,
        ).forEach { (type, label) ->
            FilterChip(
                selected = current == type,
                onClick = { onSelect(type) },
                label = { Text(stringResource(label)) },
            )
        }
    }
}

@Composable
private fun PeriodNavigator(
    label: String,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onPrevious, enabled = canGoBack) {
            Text("‹", style = MaterialTheme.typography.headlineSmall)
        }
        TextButton(onClick = onPick, modifier = Modifier.weight(1f)) {
            Text(label, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        }
        TextButton(onClick = onNext, enabled = canGoForward) {
            Text("›", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun periodLabel(period: Period, today: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return when (period.type) {
        PeriodType.DAY -> {
            val text = DateTimeFormatter.ofPattern("d MMMM yyyy", locale).format(period.start)
            when (period.start) {
                today -> stringResource(R.string.rel_today) + " · " + text
                today.minusDays(1) -> stringResource(R.string.rel_yesterday) + " · " + text
                else -> text
            }
        }
        PeriodType.WEEK ->
            DateTimeFormatter.ofPattern("d MMM", locale).format(period.start) + " – " +
                    DateTimeFormatter.ofPattern("d MMM yyyy", locale).format(period.end)
        PeriodType.MONTH -> DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(period.start)
    }
}

@Composable
private fun SummaryCard(report: Report) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.report_screen_time),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(formatDuration(report.totalMs), style = MaterialTheme.typography.headlineMedium)
            if (report.period.type != PeriodType.DAY) {
                Text(
                    stringResource(R.string.report_daily_average, formatDuration(report.dailyAverageMs)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            report.previousAverageMs?.takeIf { it > 0L }?.let { previous ->
                val percent = ((report.dailyAverageMs - previous) * 100 / previous).toInt()
                val change = when {
                    percent > 0 -> "+$percent% ▲"
                    percent < 0 -> "$percent% ▼"
                    else -> "0%"
                }
                Text(
                    stringResource(R.string.report_vs_previous, change),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (report.launcherMs > 0L) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.report_launcher, formatDuration(report.launcherMs)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SortRow(current: ReportSort, onSelect: (ReportSort) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = current == ReportSort.TIME,
            onClick = { onSelect(ReportSort.TIME) },
            label = { Text(stringResource(R.string.report_sort_time)) },
        )
        FilterChip(
            selected = current == ReportSort.NAME,
            onClick = { onSelect(ReportSort.NAME) },
            label = { Text(stringResource(R.string.report_sort_name)) },
        )
    }
}

@Composable
private fun ReportAppRow(app: AppUsage, totalMs: Long, onClick: () -> Unit) {
    val fraction = if (totalMs > 0L) app.ms.toFloat() / totalMs else 0f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PackageIcon(app.packageName)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(formatDuration(app.ms), style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${(fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PackageIcon(packageName: String) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { 36.dp.roundToPx() }
    val icon by produceState<ImageBitmap?>(AppIcons.cached("THIS:$packageName"), packageName) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                AppIcons.loadForPackage(context.applicationContext, packageName, sizePx)
            }
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

/** Installed / updated / removed in the period; each group expands on tap. */
@Composable
private fun ChangesSection(changes: List<PeriodChange>) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(stringResource(R.string.report_changes), style = MaterialTheme.typography.titleMedium)
        listOf(
            EventType.INSTALLED to R.string.changes_installed,
            EventType.UPDATED to R.string.changes_updated,
            EventType.REMOVED to R.string.changes_removed,
        ).forEach { (type, title) ->
            val group = changes.filter { it.type == type }
            if (group.isNotEmpty()) {
                var expanded by remember(type) { mutableStateOf(false) }
                Text(
                    text = (if (expanded) "▾ " else "▸ ") + stringResource(title, group.size),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(vertical = 8.dp),
                )
                if (expanded) {
                    group.forEach { c ->
                        Text(
                            text = c.label + (c.version?.let { " · v$it" } ?: "") + " · " +
                                    (if (c.approx) "≈ " else "") + formatDateTime(c.time),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodDatePicker(
    initial: LocalDate,
    firstDay: LocalDate,
    today: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // DatePicker works with UTC midnight millis
    val minMs = firstDay.toUtcMillis()
    val maxMs = today.toUtcMillis()
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial.toUtcMillis().coerceIn(minMs, maxMs),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis in minMs..maxMs
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let { onPick(it.toLocalDateUtc()) }
                onDismiss()
            }) { Text(stringResource(R.string.date_pick_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    ) {
        DatePicker(state = pickerState)
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDateUtc(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()