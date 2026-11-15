package com.gdvalishvili.appinspector.ui.logs

import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gdvalishvili.appinspector.R
import com.gdvalishvili.appinspector.data.appLabelOrNull
import com.gdvalishvili.appinspector.data.logs.CrashEntry
import com.gdvalishvili.appinspector.data.logs.CrashKind
import com.gdvalishvili.appinspector.data.logs.LogLine
import com.gdvalishvili.appinspector.data.logs.LogsPrefs
import com.gdvalishvili.appinspector.data.logs.SnapshotInfo
import com.gdvalishvili.appinspector.data.logs.filterLines
import com.gdvalishvili.appinspector.model.AppEntry
import com.gdvalishvili.appinspector.util.copyToClipboard
import com.gdvalishvili.appinspector.util.formatDateTime
import com.gdvalishvili.appinspector.util.shareText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val lineTime: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
private val shortTime: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
private val fileStamp: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").withZone(ZoneId.systemDefault())

private const val MIN_ITEMS_FOR_SCROLLBAR = 50
private const val MAX_COPY_CHARS = 500_000 // clipboard has a size limit (~1 MB)

enum class LogsTab { LOG, ERRORS, SAVED }
enum class TimeFilter { MIN_15, HOUR_1, ALL, RANGE }

/** app == null: system-wide logs; otherwise only that app's lines and crashes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    app: AppEntry?,
    onBack: () -> Unit,
    viewModel: LogsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val autoDelete by viewModel.autoDelete.collectAsStateWithLifecycle()
    val keepCount by viewModel.keepCount.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(app?.key) { viewModel.init(app) }

    var tab by rememberSaveable { mutableStateOf(LogsTab.LOG) }
    var timeFilter by rememberSaveable { mutableStateOf(TimeFilter.ALL) }
    var errorsOnly by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var rangeFrom by rememberSaveable { mutableStateOf<Long?>(null) }
    var rangeTo by rememberSaveable { mutableStateOf<Long?>(null) }
    var showRange by remember { mutableStateOf(false) }
    var crashToShow by remember { mutableStateOf<CrashEntry?>(null) }
    var snapshotToDelete by remember { mutableStateOf<SnapshotInfo?>(null) }

    // Relative filters count back from the moment the log was read (or saved)
    val rf = rangeFrom
    val rt = rangeTo
    val from: Long? = when (timeFilter) {
        TimeFilter.MIN_15 -> state.loadedAt - 15 * 60_000L
        TimeFilter.HOUR_1 -> state.loadedAt - 60 * 60_000L
        TimeFilter.ALL -> null
        TimeFilter.RANGE -> rf
    }
    val to: Long? = if (timeFilter == TimeFilter.RANGE) rt else null
    val filtered = remember(state.lines, errorsOnly, query, from, to) {
        filterLines(state.lines, errorsOnly, query, from, to)
    }
    val rangeLabel = if (rf != null && rt != null) {
        shortTime.format(Instant.ofEpochMilli(rf)) + "–" + shortTime.format(Instant.ofEpochMilli(rt))
    } else null
    val scopeName = app?.packageName ?: "system"

    // --- Selection (indexes into "filtered"; reset whenever the visible lines change) ---
    var selected by remember(filtered) { mutableStateOf<Set<Int>>(emptySet()) }
    var anchor by remember(filtered) { mutableIntStateOf(-1) }
    BackHandler(enabled = tab == LogsTab.LOG && selected.isNotEmpty()) {
        selected = emptySet()
        anchor = -1
    }
    fun selectedLines(): List<LogLine> = selected.sorted().mapNotNull { filtered.getOrNull(it) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (app == null) stringResource(R.string.logs_title)
                        else stringResource(R.string.logs_title_app, app.label),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                LogsTab.entries.forEach { t ->
                    Tab(
                        selected = tab == t,
                        onClick = { tab = t },
                        text = { Text(stringResource(tabLabel(t))) },
                    )
                }
            }
            when (tab) {
                LogsTab.LOG -> LogTab(
                    state = state,
                    filtered = filtered,
                    timeFilter = timeFilter,
                    rangeLabel = rangeLabel,
                    errorsOnly = errorsOnly,
                    query = query,
                    searchOpen = searchOpen,
                    selected = selected,
                    onTimeFilter = { f -> if (f == TimeFilter.RANGE) showRange = true else timeFilter = f },
                    onErrorsOnly = { errorsOnly = it },
                    onQuery = { query = it },
                    onSearchOpen = { open ->
                        searchOpen = open
                        if (!open) query = ""
                    },
                    onRefresh = viewModel::refresh,
                    onSave = {
                        scope.launch {
                            if (viewModel.saveSnapshot()) {
                                Toast.makeText(context, R.string.logs_saved_toast, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onShare = {
                        scope.launch {
                            val text = withContext(Dispatchers.Default) { filtered.joinToString("\n") { it.raw } }
                            val stamp = fileStamp.format(Instant.ofEpochMilli(state.loadedAt))
                            shareText(context, "logs_${scopeName}_$stamp.txt", text)
                        }
                    },
                    onToggleLine = { i ->
                        selected = if (i in selected) selected - i else selected + i
                        if (selected.isEmpty()) anchor = -1
                    },
                    onLongPressLine = { i ->
                        if (selected.isEmpty() || anchor < 0) {
                            selected = setOf(i)
                        } else {
                            // Second long-press: everything between the previous point and this one
                            selected = selected + (minOf(anchor, i)..maxOf(anchor, i))
                        }
                        anchor = i
                    },
                    onSelectAll = {
                        selected = filtered.indices.toSet()
                        anchor = -1
                    },
                    onSaveSelected = {
                        val lines = selectedLines()
                        scope.launch {
                            if (viewModel.saveLines(lines)) {
                                Toast.makeText(context, R.string.logs_saved_toast, Toast.LENGTH_SHORT).show()
                                selected = emptySet()
                                anchor = -1
                            }
                        }
                    },
                    onShareSelected = {
                        val lines = selectedLines()
                        scope.launch {
                            val text = withContext(Dispatchers.Default) { lines.joinToString("\n") { it.raw } }
                            val stamp = fileStamp.format(Instant.ofEpochMilli(state.loadedAt))
                            shareText(context, "logs_${scopeName}_${stamp}_selection.txt", text)
                        }
                    },
                    onCopySelected = {
                        val text = selectedLines().joinToString("\n") { it.raw }
                        if (text.length > MAX_COPY_CHARS) {
                            Toast.makeText(context, R.string.logs_copy_too_large, Toast.LENGTH_LONG).show()
                        } else {
                            copyToClipboard(context, text)
                        }
                    },
                    onClearSelection = {
                        selected = emptySet()
                        anchor = -1
                    },
                )
                LogsTab.ERRORS -> ErrorsTab(
                    state = state,
                    onOpen = { crashToShow = it },
                    onRefresh = viewModel::loadCrashes,
                )
                LogsTab.SAVED -> SavedTab(
                    state = state,
                    autoDelete = autoDelete,
                    keepCount = keepCount,
                    onAutoDelete = viewModel::setAutoDelete,
                    onKeepCount = viewModel::setKeepCount,
                    onOpen = {
                        viewModel.openSnapshot(it)
                        tab = LogsTab.LOG
                    },
                    onDelete = { snapshotToDelete = it },
                )
            }
        }
    }

    if (showRange) {
        RangeDialog(
            onDismiss = { showRange = false },
            onConfirm = { f, t ->
                rangeFrom = f
                rangeTo = t
                timeFilter = TimeFilter.RANGE
                showRange = false
            },
        )
    }

    crashToShow?.let { entry ->
        AlertDialog(
            onDismissRequest = { crashToShow = null },
            title = {
                Text(
                    (entry.label ?: entry.packageName ?: stringResource(R.string.logs_scope_system)) +
                            " · " + stringResource(kindLabel(entry.kind))
                )
            },
            text = {
                SelectionContainer {
                    Column(
                        Modifier
                            .heightIn(max = 480.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(entry.text, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { crashToShow = null }) { Text(stringResource(R.string.logs_close)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch {
                        val stamp = fileStamp.format(Instant.ofEpochMilli(entry.time))
                        shareText(context, "crash_${entry.packageName ?: "system"}_$stamp.txt", entry.text)
                    }
                }) { Text(stringResource(R.string.logs_share)) }
            },
        )
    }

    snapshotToDelete?.let { snapshot ->
        AlertDialog(
            onDismissRequest = { snapshotToDelete = null },
            title = { Text(stringResource(R.string.logs_delete_confirm)) },
            text = { Text(formatDateTime(snapshot.createdAt)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSnapshot(snapshot)
                    snapshotToDelete = null
                }) { Text(stringResource(R.string.logs_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { snapshotToDelete = null }) { Text(stringResource(R.string.logs_cancel)) }
            },
        )
    }
}

private fun tabLabel(t: LogsTab) = when (t) {
    LogsTab.LOG -> R.string.logs_tab_log
    LogsTab.ERRORS -> R.string.logs_tab_errors
    LogsTab.SAVED -> R.string.logs_tab_saved
}

private fun timeLabel(t: TimeFilter) = when (t) {
    TimeFilter.MIN_15 -> R.string.logs_time_15m
    TimeFilter.HOUR_1 -> R.string.logs_time_1h
    TimeFilter.ALL -> R.string.logs_time_all
    TimeFilter.RANGE -> R.string.logs_time_range
}

private fun kindLabel(k: CrashKind) = when (k) {
    CrashKind.CRASH -> R.string.logs_kind_crash
    CrashKind.ANR -> R.string.logs_kind_anr
    CrashKind.NATIVE -> R.string.logs_kind_native
    CrashKind.SYSTEM -> R.string.logs_kind_system
}

// ---------------- Log tab ----------------

@Composable
private fun LogTab(
    state: LogsUiState,
    filtered: List<LogLine>,
    timeFilter: TimeFilter,
    rangeLabel: String?,
    errorsOnly: Boolean,
    query: String,
    searchOpen: Boolean,
    selected: Set<Int>,
    onTimeFilter: (TimeFilter) -> Unit,
    onErrorsOnly: (Boolean) -> Unit,
    onQuery: (String) -> Unit,
    onSearchOpen: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onToggleLine: (Int) -> Unit,
    onLongPressLine: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onSaveSelected: () -> Unit,
    onShareSelected: () -> Unit,
    onCopySelected: () -> Unit,
    onClearSelection: () -> Unit,
) {
    val viewing = state.viewing
    Column(Modifier.fillMaxSize()) {
        if (selected.isNotEmpty()) {
            SelectionBar(
                count = selected.size,
                onSelectAll = onSelectAll,
                onSave = onSaveSelected,
                onShare = onShareSelected,
                onCopy = onCopySelected,
                onClose = onClearSelection,
            )
        } else {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (!state.readLogsGranted && viewing == null) AccessCard()
                if (viewing != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    ) {
                        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.logs_snapshot_banner, formatDateTime(viewing.createdAt)),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onRefresh) { Text(stringResource(R.string.logs_back_to_live)) }
                        }
                    }
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TimeFilter.entries.forEach { f ->
                        FilterChip(
                            selected = timeFilter == f,
                            onClick = { onTimeFilter(f) },
                            label = {
                                Text(
                                    if (f == TimeFilter.RANGE && timeFilter == TimeFilter.RANGE && rangeLabel != null) rangeLabel
                                    else stringResource(timeLabel(f))
                                )
                            },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = errorsOnly, onCheckedChange = onErrorsOnly)
                    Text(
                        stringResource(R.string.logs_errors_only),
                        modifier = Modifier.clickable { onErrorsOnly(!errorsOnly) },
                    )
                }
                if (searchOpen) {
                    val focus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { focus.requestFocus() }
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQuery,
                        label = { Text(stringResource(R.string.logs_search)) },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { onSearchOpen(false) }) {
                                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.logs_close))
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus),
                    )
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onRefresh, enabled = !state.loading) {
                        Text("↻ " + stringResource(R.string.logs_refresh))
                    }
                    if (!searchOpen) {
                        TextButton(onClick = { onSearchOpen(true) }) {
                            Text("🔍 " + stringResource(R.string.logs_search))
                        }
                    }
                    TextButton(
                        onClick = onSave,
                        enabled = viewing == null && state.lines.isNotEmpty() && !state.loading,
                    ) { Text(stringResource(R.string.logs_save)) }
                    TextButton(onClick = onShare, enabled = filtered.isNotEmpty()) {
                        Text(stringResource(R.string.logs_share))
                    }
                }
                val firstTime = state.lines.firstOrNull { it.time != null }?.time
                Text(
                    text = listOfNotNull(
                        firstTime?.let { stringResource(R.string.logs_history_from, formatDateTime(it)) },
                        stringResource(R.string.logs_count, filtered.size, state.lines.size),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val problem = when {
                    state.uidMissing -> stringResource(R.string.logs_no_uid)
                    state.error != null -> stringResource(R.string.logs_error, state.error)
                    state.ownOnly && state.readLogsGranted -> stringResource(R.string.logs_own_only)
                    else -> null
                }
                if (problem != null) {
                    Text(
                        problem,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        when {
            state.loading -> Column(Modifier.padding(16.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    stringResource(R.string.logs_loading),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            filtered.isEmpty() -> Text(
                stringResource(R.string.logs_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            else -> LogLines(
                lines = filtered,
                selected = selected,
                onToggle = onToggleLine,
                onLongPress = onLongPressLine,
            )
        }
    }
}

/** Shown instead of the filters while lines are selected. */
@Composable
private fun SelectionBar(
    count: Int,
    onSelectAll: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.logs_close))
                }
                Text(stringResource(R.string.logs_selected_count, count), style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = onSelectAll) { Text(stringResource(R.string.logs_select_visible)) }
                TextButton(onClick = onSave) { Text(stringResource(R.string.logs_save)) }
                TextButton(onClick = onShare) { Text(stringResource(R.string.logs_share)) }
                TextButton(onClick = onCopy) { Text(stringResource(R.string.logs_copy)) }
            }
            if (count == 1) {
                Text(
                    stringResource(R.string.logs_select_hint),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                )
            }
        }
    }
}

/**
 * Normal mode: tap = show the whole line, long-press = start selecting.
 * Selection mode: tap = select/unselect, long-press = select the range from the previous point.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LogLines(
    lines: List<LogLine>,
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
    onLongPress: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val expanded = remember(lines) { mutableStateMapOf<Int, Boolean>() }
    val toggleExpanded = { i: Int -> expanded[i] = expanded[i] != true }
    val warnColor = if (isSystemInDarkTheme()) Color(0xFFFFB870) else Color(0xFF8A4B00)
    val highlight = MaterialTheme.colorScheme.secondaryContainer
    val selecting = selected.isNotEmpty()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // room on the right for the scrollbar, at the bottom for the jump buttons
            contentPadding = PaddingValues(start = 8.dp, end = 32.dp, top = 4.dp, bottom = 136.dp),
        ) {
            itemsIndexed(lines) { index, line ->
                val isSelected = index in selected
                val color = when (line.level) {
                    'E', 'F', 'A' -> MaterialTheme.colorScheme.error
                    'W' -> warnColor
                    'D', 'V' -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                }
                val mark = when {
                    !selecting -> ""
                    isSelected -> "✓ "
                    else -> "   "
                }
                Text(
                    text = mark + displayText(line),
                    color = color,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = if (expanded[index] == true) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) highlight else Color.Transparent)
                        .combinedClickable(
                            onClick = { if (selecting) onToggle(index) else toggleExpanded(index) },
                            onLongClick = { onLongPress(index) },
                        )
                        .padding(vertical = 2.dp),
                )
            }
        }

        FastScrollbar(
            state = listState,
            itemCount = lines.size,
            label = { i -> lines.getOrNull(i)?.time?.let { lineTime.format(Instant.ofEpochMilli(it)) } },
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 40.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (listState.canScrollBackward) {
                FloatingActionButton(
                    onClick = { scope.launch { listState.scrollToItem(0) } },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_vertical_align_top),
                        contentDescription = stringResource(R.string.logs_go_top),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            if (listState.canScrollForward) {
                FloatingActionButton(
                    onClick = { scope.launch { listState.scrollToItem(lines.lastIndex) } },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_vertical_align_bottom),
                        contentDescription = stringResource(R.string.logs_go_bottom),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}

/**
 * Draggable scrollbar for long lists: drag the thumb (or tap the track) to jump.
 * While dragging, a bubble shows the time of the first visible line.
 */
@Composable
private fun FastScrollbar(state: LazyListState, itemCount: Int, label: (Int) -> String?) {
    if (itemCount < MIN_ITEMS_FOR_SCROLLBAR) return
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val thumbHeight = 48.dp
    val thumbPx = with(density) { thumbHeight.toPx() }
    var trackPx by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }

    fun maxFirst(): Int = (itemCount - state.layoutInfo.visibleItemsInfo.size).coerceAtLeast(1)

    val progress by remember(itemCount) {
        derivedStateOf { (state.firstVisibleItemIndex.toFloat() / maxFirst()).coerceIn(0f, 1f) }
    }

    fun jumpTo(y: Float) {
        val usable = (trackPx - thumbPx).coerceAtLeast(1f)
        val fraction = ((y - thumbPx / 2f) / usable).coerceIn(0f, 1f)
        scope.launch { state.scrollToItem((fraction * maxFirst()).roundToInt()) }
    }

    val thumbOffset = (progress * (trackPx - thumbPx)).coerceAtLeast(0f).roundToInt()
    val thumbColor = MaterialTheme.colorScheme.primary.copy(alpha = if (dragging) 0.9f else 0.5f)

    Box(Modifier.fillMaxSize()) {
        // Touch track (right edge)
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight()
                .width(28.dp)
                .onSizeChanged { trackPx = it.height }
                .pointerInput(itemCount) { detectTapGestures { jumpTo(it.y) } }
                .pointerInput(itemCount) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            dragging = true
                            jumpTo(it.y)
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, _ ->
                        change.consume()
                        jumpTo(change.position.y)
                    }
                }
        ) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(0, thumbOffset) }
                    .padding(end = 4.dp)
                    .width(if (dragging) 10.dp else 6.dp)
                    .height(thumbHeight)
                    .background(thumbColor, RoundedCornerShape(5.dp))
            )
        }
        // Time bubble next to the thumb while dragging
        if (dragging) {
            val text = label(state.firstVisibleItemIndex)
            if (text != null) {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset { IntOffset(0, thumbOffset) }
                        .padding(end = 36.dp),
                ) {
                    Text(
                        text,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

private fun displayText(line: LogLine): String {
    val time = line.time ?: return line.message
    return "${lineTime.format(Instant.ofEpochMilli(time))} ${line.level} ${line.tag}: ${line.message}"
}

@Composable
private fun AccessCard() {
    val context = LocalContext.current
    val pkg = context.packageName
    val commands = "adb shell pm grant $pkg android.permission.READ_LOGS\n" +
            "adb shell pm grant $pkg android.permission.READ_DROPBOX_DATA\n" +
            "adb shell am force-stop $pkg"
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(R.string.logs_access_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.logs_access_text),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            SelectionContainer {
                Text(commands, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
            }
            TextButton(onClick = { copyToClipboard(context, commands) }, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.logs_copy))
            }
        }
    }
}

// ---------------- Error history tab ----------------

@Composable
private fun ErrorsTab(state: LogsUiState, onOpen: (CrashEntry) -> Unit, onRefresh: () -> Unit) {
    val crashes = state.crashes
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onRefresh, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text("↻ " + stringResource(R.string.logs_refresh))
        }
        when {
            state.crashesNoPermission -> Column(Modifier.padding(horizontal = 16.dp)) {
                AccessCard()
                Text(stringResource(R.string.logs_dropbox_no_access), style = MaterialTheme.typography.bodySmall)
            }
            crashes == null -> LinearProgressIndicator(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
            crashes.isEmpty() -> Text(
                stringResource(R.string.logs_errors_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(crashes) { _, entry -> CrashRow(entry, onClick = { onOpen(entry) }) }
            }
        }
    }
}

@Composable
private fun CrashRow(entry: CrashEntry, onClick: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                formatDateTime(entry.time) + " · " + stringResource(kindLabel(entry.kind)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                entry.label ?: entry.packageName ?: stringResource(R.string.logs_scope_system),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                entry.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------- Saved tab ----------------

@Composable
private fun SavedTab(
    state: LogsUiState,
    autoDelete: Boolean,
    keepCount: Int,
    onAutoDelete: (Boolean) -> Unit,
    onKeepCount: (Int) -> Unit,
    onOpen: (SnapshotInfo) -> Unit,
    onDelete: (SnapshotInfo) -> Unit,
) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.logs_auto_delete), modifier = Modifier.weight(1f))
            Switch(checked = autoDelete, onCheckedChange = onAutoDelete)
        }
        if (autoDelete) {
            val focusManager = LocalFocusManager.current
            var text by remember(keepCount) { mutableStateOf(keepCount.toString()) }
            val newValue = text.toIntOrNull()?.takeIf { it in LogsPrefs.MIN_KEEP..LogsPrefs.MAX_KEEP }
            val canSave = newValue != null && newValue != keepCount
            val save = {
                if (newValue != null && newValue != keepCount) {
                    onKeepCount(newValue)
                    focusManager.clearFocus()
                    Toast.makeText(context, R.string.logs_saved_toast, Toast.LENGTH_SHORT).show()
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.logs_keep_count), modifier = Modifier.weight(1f))
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter(Char::isDigit).take(3) },
                    singleLine = true,
                    isError = text.isNotEmpty() && newValue == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    modifier = Modifier.width(80.dp),
                )
                TextButton(onClick = { save() }, enabled = canSave) {
                    Text(stringResource(R.string.logs_save))
                }
            }
            Text(
                stringResource(R.string.logs_keep_hint, keepCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        } else {
            Text(
                stringResource(R.string.logs_keep_off),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        Text(
            stringResource(
                R.string.logs_saved_summary,
                state.snapshots.size,
                Formatter.formatShortFileSize(context, state.snapshots.sumOf { it.sizeBytes }),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.snapshots.isEmpty()) {
            Text(
                stringResource(R.string.logs_saved_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(state.snapshots, key = { it.file.name }) { snapshot ->
                    SnapshotRow(snapshot, onOpen = { onOpen(snapshot) }, onDelete = { onDelete(snapshot) })
                }
            }
        }
    }
}

@Composable
private fun SnapshotRow(snapshot: SnapshotInfo, onOpen: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val pkg = snapshot.packageName
    val scopeLabel = if (pkg == null) {
        stringResource(R.string.logs_scope_system)
    } else {
        remember(pkg) { context.packageManager.appLabelOrNull(pkg) } ?: pkg
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(formatDateTime(snapshot.createdAt), style = MaterialTheme.typography.bodyLarge)
            Text(
                scopeLabel + " · " + Formatter.formatShortFileSize(context, snapshot.sizeBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onDelete) { Text(stringResource(R.string.logs_delete)) }
    }
}

// ---------------- Range dialog ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeDialog(onDismiss: () -> Unit, onConfirm: (Long, Long) -> Unit) {
    var yesterday by remember { mutableStateOf(false) }
    val now = remember { LocalTime.now() }
    val fromState = rememberTimePickerState(initialHour = (now.hour - 1).coerceAtLeast(0), initialMinute = now.minute, is24Hour = true)
    val toState = rememberTimePickerState(initialHour = now.hour, initialMinute = now.minute, is24Hour = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.logs_range_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !yesterday, onClick = { yesterday = false }, label = { Text(stringResource(R.string.logs_range_today)) })
                    FilterChip(selected = yesterday, onClick = { yesterday = true }, label = { Text(stringResource(R.string.logs_range_yesterday)) })
                }
                Text(stringResource(R.string.logs_range_from), modifier = Modifier.padding(top = 8.dp))
                TimeInput(state = fromState)
                Text(stringResource(R.string.logs_range_to))
                TimeInput(state = toState)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val zone = ZoneId.systemDefault()
                val day = LocalDate.now().minusDays(if (yesterday) 1 else 0)
                val a = day.atTime(fromState.hour, fromState.minute).atZone(zone).toInstant().toEpochMilli()
                val b = day.atTime(toState.hour, toState.minute).atZone(zone).toInstant().toEpochMilli() + 59_999L
                onConfirm(minOf(a, b), maxOf(a, b))
            }) { Text(stringResource(R.string.logs_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.logs_cancel)) }
        },
    )
}