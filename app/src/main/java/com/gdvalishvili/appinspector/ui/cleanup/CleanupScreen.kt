package com.gdvalishvili.appinspector.ui.cleanup

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gdvalishvili.appinspector.R
import com.gdvalishvili.appinspector.data.CleanupGroup
import com.gdvalishvili.appinspector.data.CleanupRules
import com.gdvalishvili.appinspector.model.AppEntry
import com.gdvalishvili.appinspector.model.UsageStatus
import com.gdvalishvili.appinspector.util.AppIcons
import com.gdvalishvili.appinspector.util.formatDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.concurrent.TimeUnit

enum class CleanupSort { SIZE, LAST_USED, NAME }

private const val CONFIRM_NAMES_MAX = 10

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanupScreen(
    apps: List<AppEntry>?,
    onBack: () -> Unit,
    onOpenDetails: (String) -> Unit,
    onRefreshList: () -> Unit,
    viewModel: CleanupViewModel = viewModel(),
) {
    val excluded by viewModel.excluded.collectAsStateWithLifecycle()
    val sizes by viewModel.sizes.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val run by viewModel.run.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val ownPackage = context.packageName

    var group by rememberSaveable { mutableStateOf(CleanupGroup.NEVER_OPENED) }
    var sort by rememberSaveable { mutableStateOf(CleanupSort.SIZE) }
    var showConfirm by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        onRefreshList()
        onPauseOrDispose { }
    }

    // --- Groups ---
    val grouped: Map<CleanupGroup, List<AppEntry>>? = remember(apps, excluded) {
        val ex = excluded
        if (apps == null || ex == null) {
            null
        } else {
            val now = System.currentTimeMillis()
            val map = CleanupGroup.entries.associateWith { mutableListOf<AppEntry>() }
            apps.forEach { app ->
                CleanupRules.groupsOf(app, ownPackage, ex, now).forEach { g -> map.getValue(g).add(app) }
            }
            map
        }
    }
    LaunchedEffect(grouped) {
        grouped?.values?.flatten()?.map { it.packageName }?.distinct()?.let(viewModel::loadSizes)
    }
    val visible = remember(grouped, group, sort, sizes) { sortCleanup(grouped?.get(group).orEmpty(), sort, sizes) }
    val noAccess = apps?.any { it.usageStatus == UsageStatus.NO_PERMISSION } == true
    val selectable = group != CleanupGroup.EXCLUDED && run == null
    val visiblePackages = visible.map { it.packageName }.toSet()
    val allVisibleSelected = visiblePackages.isNotEmpty() && selected.containsAll(visiblePackages)

    // --- System uninstall dialogs, one by one ---
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onDialogReturned()
    }
    val pending = run?.takeIf { it.current != null && it.launchedIndex != it.index }
    LaunchedEffect(pending?.index, pending?.current) {
        val r = pending ?: return@LaunchedEffect
        val pkg = r.current ?: return@LaunchedEffect
        try {
            launcher.launch(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", pkg, null)))
            viewModel.markLaunched(r.index)
        } catch (e: ActivityNotFoundException) {
            viewModel.markLaunched(r.index)
            viewModel.onDialogReturned()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cleanup_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    if (selectable && visible.isNotEmpty()) {
                        TextButton(onClick = {
                            viewModel.setSelection(
                                if (allVisibleSelected) selected - visiblePackages else selected + visiblePackages
                            )
                        }) {
                            Text(
                                stringResource(
                                    if (allVisibleSelected) R.string.cleanup_clear_selection
                                    else R.string.cleanup_select_all
                                )
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            val r = run
            when {
                r != null && !r.finished -> ProgressBar(r, onStop = viewModel::requestStop)
                selected.isNotEmpty() && r == null -> {
                    val total = selected.sumOf { sizes[it] ?: 0L }
                    Surface(tonalElevation = 3.dp) {
                        Button(
                            onClick = { showConfirm = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(16.dp),
                        ) {
                            Text(
                                stringResource(
                                    R.string.cleanup_delete,
                                    selected.size,
                                    Formatter.formatShortFileSize(context, total),
                                )
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            item {
                GroupRow(
                    current = group,
                    counts = CleanupGroup.entries.associateWith { grouped?.get(it)?.size ?: 0 },
                    onSelect = {
                        group = it
                        viewModel.setSelection(emptySet())
                    },
                )
            }
            item {
                Text(
                    text = stringResource(groupDescription(group)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            item { SortRow(current = sort, onSelect = { sort = it }) }
            if (noAccess) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    ) {
                        Text(stringResource(R.string.cleanup_no_access), modifier = Modifier.padding(12.dp))
                    }
                }
            }
            when {
                grouped == null -> item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp)) }
                visible.isEmpty() -> item {
                    Text(
                        text = stringResource(R.string.cleanup_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
                else -> items(visible, key = { it.key }) { app ->
                    CleanupRow(
                        app = app,
                        sizeBytes = sizes[app.packageName],
                        selectable = selectable,
                        selected = app.packageName in selected,
                        isExcludedGroup = group == CleanupGroup.EXCLUDED,
                        onToggle = { viewModel.toggle(app.packageName) },
                        onDetails = { onOpenDetails(app.key) },
                        onExclude = { value -> viewModel.setExcluded(app.packageName, value) },
                    )
                }
            }
        }
    }

    // --- Our own confirmation before the system dialogs ---
    if (showConfirm) {
        val names = (apps.orEmpty().filter { it.packageName in selected }.map { it.label }).sortedWith(Collator.getInstance())
        val shown = names.take(CONFIRM_NAMES_MAX).joinToString("\n") { "• $it" } +
                if (names.size > CONFIRM_NAMES_MAX) "\n" + stringResource(R.string.cleanup_more, names.size - CONFIRM_NAMES_MAX) else ""
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(stringResource(R.string.cleanup_confirm_title, selected.size)) },
            text = { Text(stringResource(R.string.cleanup_confirm_text, shown)) },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    viewModel.startUninstall()
                }) { Text(stringResource(R.string.cleanup_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text(stringResource(R.string.cleanup_cancel)) }
            },
        )
    }

    // --- Summary ---
    val finished = run?.takeIf { it.finished }
    if (finished != null) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.cleanup_done_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.cleanup_done_text,
                        finished.removed,
                        finished.skipped,
                        Formatter.formatShortFileSize(context, finished.freedBytes),
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.finishRun()
                    onRefreshList()
                }) { Text(stringResource(R.string.cleanup_ok)) }
            },
        )
    }
}

private fun groupDescription(group: CleanupGroup): Int = when (group) {
    CleanupGroup.NEVER_OPENED -> R.string.cleanup_desc_never
    CleanupGroup.UNUSED_90 -> R.string.cleanup_desc_90
    CleanupGroup.UNUSED_180 -> R.string.cleanup_desc_180
    CleanupGroup.BACKGROUND -> R.string.cleanup_desc_background
    CleanupGroup.EXCLUDED -> R.string.cleanup_desc_excluded
}

/** SIZE: biggest first (unknown last). LAST_USED: never used / oldest first. */
private fun sortCleanup(list: List<AppEntry>, sort: CleanupSort, sizes: Map<String, Long>): List<AppEntry> =
    when (sort) {
        CleanupSort.SIZE -> list.sortedByDescending { sizes[it.packageName] ?: -1L }
        CleanupSort.LAST_USED -> list.sortedBy { it.lastUsedAt ?: Long.MIN_VALUE }
        CleanupSort.NAME -> list.sortedWith(compareBy(Collator.getInstance()) { it.label })
    }

@Composable
private fun GroupRow(current: CleanupGroup, counts: Map<CleanupGroup, Int>, onSelect: (CleanupGroup) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CleanupGroup.entries.forEach { g ->
            val label = when (g) {
                CleanupGroup.NEVER_OPENED -> R.string.cleanup_group_never
                CleanupGroup.UNUSED_90 -> R.string.cleanup_group_90
                CleanupGroup.UNUSED_180 -> R.string.cleanup_group_180
                CleanupGroup.BACKGROUND -> R.string.cleanup_group_background
                CleanupGroup.EXCLUDED -> R.string.cleanup_group_excluded
            }
            FilterChip(
                selected = current == g,
                onClick = { onSelect(g) },
                label = { Text(stringResource(label, counts[g] ?: 0)) },
            )
        }
    }
}

@Composable
private fun SortRow(current: CleanupSort, onSelect: (CleanupSort) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CleanupSort.entries.forEach { s ->
            val label = when (s) {
                CleanupSort.SIZE -> R.string.cleanup_sort_size
                CleanupSort.LAST_USED -> R.string.cleanup_sort_last_used
                CleanupSort.NAME -> R.string.cleanup_sort_name
            }
            FilterChip(
                selected = current == s,
                onClick = { onSelect(s) },
                label = { Text(stringResource(label)) },
            )
        }
    }
}

@Composable
private fun ProgressBar(run: UninstallRun, onStop: () -> Unit) {
    val total = run.queue.size
    Surface(tonalElevation = 3.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.cleanup_progress, minOf(run.index + 1, total), total),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onStop, enabled = !run.stopRequested) {
                    Text(stringResource(R.string.cleanup_stop))
                }
            }
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else run.index.toFloat() / total },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Tap = select, long-press = menu (details / exclude or restore). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CleanupRow(
    app: AppEntry,
    sizeBytes: Long?,
    selectable: Boolean,
    selected: Boolean,
    isExcludedGroup: Boolean,
    onToggle: () -> Unit,
    onDetails: () -> Unit,
    onExclude: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { if (selectable) onToggle() else menuOpen = true },
                    onLongClick = { menuOpen = true },
                )
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectable) {
                Checkbox(checked = selected, onCheckedChange = null)
                Spacer(Modifier.width(8.dp))
            }
            SmallIcon(app)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = statusText(app) +
                            (app.installedAt?.let { " · " + stringResource(R.string.label_installed) + ": " + formatDate(it) } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = sizeBytes?.let { Formatter.formatShortFileSize(context, it) } ?: "…",
                style = MaterialTheme.typography.labelLarge,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.cleanup_menu_details)) },
                onClick = {
                    menuOpen = false
                    onDetails()
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (isExcludedGroup) R.string.cleanup_menu_restore else R.string.cleanup_menu_exclude
                        )
                    )
                },
                onClick = {
                    menuOpen = false
                    onExclude(!isExcludedGroup)
                },
            )
        }
    }
}

@Composable
private fun statusText(app: AppEntry): String = when {
    !app.hasLauncher || app.usageStatus == UsageStatus.BACKGROUND -> stringResource(R.string.cleanup_background)
    app.usageStatus == UsageStatus.NEVER_OPENED -> stringResource(R.string.cleanup_never_opened)
    app.usageStatus == UsageStatus.OLDER_THAN_HISTORY -> stringResource(R.string.cleanup_older_than_history)
    app.lastUsedAt != null -> {
        val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - app.lastUsedAt).coerceAtLeast(0L).toInt()
        stringResource(R.string.cleanup_last_used_prefix) + " " + pluralStringResource(R.plurals.cleanup_days_ago, days, days)
    }
    else -> ""
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