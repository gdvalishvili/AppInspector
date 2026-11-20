package com.gdvalishvili.appinspector.ui.background

import android.text.format.Formatter
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
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gdvalishvili.appinspector.R
import com.gdvalishvili.appinspector.data.background.BackgroundSnapshot
import com.gdvalishvili.appinspector.data.background.FgsUsage
import com.gdvalishvili.appinspector.data.background.NetUsage
import com.gdvalishvili.appinspector.data.background.StandbyBucket
import com.gdvalishvili.appinspector.model.AppEntry
import com.gdvalishvili.appinspector.model.AppKind
import com.gdvalishvili.appinspector.model.AppProfile
import com.gdvalishvili.appinspector.util.AppIcons
import com.gdvalishvili.appinspector.util.formatDuration
import com.gdvalishvili.appinspector.util.openAppInfo
import com.gdvalishvili.appinspector.util.openAppInfoForPackage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

enum class BackgroundTab { NOW, WEEK, LIMITS }
enum class BackgroundSort { TIME, DATA, NAME }

/** One package of the 7-day list. app == null: not an installed app of this profile (shared uid, removed). */
private data class BgRow(
    val packageName: String,
    val app: AppEntry?,
    val label: String,
    val fgs: FgsUsage?,
    val net: NetUsage?,
) {
    val isSystem: Boolean get() = app == null || app.kind != AppKind.USER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundScreen(
    apps: List<AppEntry>?,
    onBack: () -> Unit,
    onOpenDetails: (String) -> Unit,
    viewModel: BackgroundViewModel = viewModel(),
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(BackgroundTab.NOW) }
    var sort by rememberSaveable { mutableStateOf(BackgroundSort.TIME) }
    var includeSystem by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(apps) { if (apps != null) viewModel.load(apps) }

    val byPackage = remember(apps) {
        apps.orEmpty().filter { it.profile == AppProfile.THIS }.associateBy { it.packageName }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bg_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { apps?.let { viewModel.load(it, force = true) } }, enabled = !loading) {
                        Icon(painterResource(R.drawable.ic_refresh), contentDescription = stringResource(R.string.list_refresh))
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
                BackgroundTab.entries.forEach { t ->
                    Tab(selected = tab == t, onClick = { tab = t }, text = { Text(stringResource(tabLabel(t))) })
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
            }
            val snap = snapshot
            when {
                snap == null -> Unit // first load: the progress bar above is shown
                !snap.hasUsageAccess -> InfoCard(stringResource(R.string.bg_no_access), error = true)
                else -> {
                    val rows = remember(snap, byPackage) { buildRows(snap, byPackage) }
                    val visible = rows.filter { includeSystem || !it.isSystem }
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            FilterChip(
                                selected = includeSystem,
                                onClick = { includeSystem = !includeSystem },
                                label = { Text(stringResource(R.string.bg_include_system)) },
                            )
                        }
                        when (tab) {
                            BackgroundTab.NOW -> nowTab(visible, onOpenDetails)
                            BackgroundTab.WEEK -> weekTab(visible, sort, onSort = { sort = it }, onOpenDetails)
                            BackgroundTab.LIMITS -> limitsTab(snap, byPackage, includeSystem, onOpenDetails)
                        }
                    }
                }
            }
        }
    }
}

private fun tabLabel(t: BackgroundTab) = when (t) {
    BackgroundTab.NOW -> R.string.bg_tab_now
    BackgroundTab.WEEK -> R.string.bg_tab_week
    BackgroundTab.LIMITS -> R.string.bg_tab_limits
}

private fun buildRows(snap: BackgroundSnapshot, byPackage: Map<String, AppEntry>): List<BgRow> =
    (snap.fgs.keys + snap.network.keys)
        .filter { it.isNotEmpty() }
        .map { pkg ->
            val app = byPackage[pkg]
            BgRow(pkg, app, app?.label ?: pkg, snap.fgs[pkg], snap.network[pkg])
        }

// ---------------- Now ----------------

private fun LazyListScope.nowTab(rows: List<BgRow>, onOpenDetails: (String) -> Unit) {
    item { InfoCard(textRes = R.string.bg_now_hint) }
    val running = rows.filter { it.fgs?.running != null }.sortedBy { it.fgs!!.running!!.start }
    if (running.isEmpty()) {
        item { EmptyText(R.string.bg_now_empty) }
    } else {
        items(running, key = { "now:" + it.packageName }) { row ->
            val session = row.fgs!!.running!!
            val now = System.currentTimeMillis()
            AppLine(
                row = row,
                lines = listOfNotNull(
                    stringResource(
                        R.string.bg_running_since,
                        timeFormatter.format(Instant.ofEpochMilli(session.start)),
                        formatDuration(now - session.start),
                    ),
                    session.className?.substringAfterLast('.'),
                ),
                onOpenDetails = onOpenDetails,
            )
        }
    }
}

// ---------------- 7 days ----------------

private fun LazyListScope.weekTab(
    rows: List<BgRow>,
    sort: BackgroundSort,
    onSort: (BackgroundSort) -> Unit,
    onOpenDetails: (String) -> Unit,
) {
    item { InfoCard(textRes = R.string.bg_week_hint) }
    item {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BackgroundSort.entries.forEach { s ->
                val label = when (s) {
                    BackgroundSort.TIME -> R.string.bg_sort_time
                    BackgroundSort.DATA -> R.string.bg_sort_data
                    BackgroundSort.NAME -> R.string.bg_sort_name
                }
                FilterChip(selected = sort == s, onClick = { onSort(s) }, label = { Text(stringResource(label)) })
            }
        }
    }
    val active = rows.filter { (it.fgs?.totalMs ?: 0L) > 0L || (it.net?.backgroundBytes ?: 0L) > 0L }
    val sorted = when (sort) {
        BackgroundSort.TIME -> active.sortedByDescending { it.fgs?.totalMs ?: 0L }
        BackgroundSort.DATA -> active.sortedByDescending { it.net?.backgroundBytes ?: 0L }
        BackgroundSort.NAME -> active.sortedWith(compareBy(Collator.getInstance()) { it.label })
    }
    if (sorted.isEmpty()) {
        item { EmptyText(R.string.bg_week_empty) }
    } else {
        items(sorted, key = { "week:" + it.packageName }) { row ->
            val context = LocalContext.current
            val lines = buildList {
                row.fgs?.takeIf { it.totalMs > 0L }?.let {
                    add(stringResource(R.string.bg_fgs_line, formatDuration(it.totalMs), formatDuration(it.todayMs), it.starts))
                }
                row.net?.takeIf { it.backgroundBytes > 0L }?.let {
                    val text = stringResource(
                        R.string.bg_net_line,
                        Formatter.formatShortFileSize(context, it.backgroundBytes),
                        Formatter.formatShortFileSize(context, it.backgroundTodayBytes),
                    )
                    add(if (it.sharedUid) text + " · " + stringResource(R.string.bg_shared_uid) else text)
                }
            }
            AppLine(row = row, lines = lines, onOpenDetails = onOpenDetails)
        }
    }
}

// ---------------- Restrictions ----------------

private fun LazyListScope.limitsTab(
    snap: BackgroundSnapshot,
    byPackage: Map<String, AppEntry>,
    includeSystem: Boolean,
    onOpenDetails: (String) -> Unit,
) {
    fun visible(pkg: String): Boolean {
        val app = byPackage[pkg] ?: return includeSystem
        return includeSystem || app.kind == AppKind.USER
    }

    item { InfoCard(textRes = R.string.bg_limits_hint) }
    val grouped = snap.buckets.filterKeys { visible(it) }.entries.groupBy({ it.value }, { it.key })
    if (grouped.isEmpty()) {
        item { EmptyText(R.string.bg_limits_empty) }
    }
    StandbyBucket.entries.forEach { bucket ->
        val packages = grouped[bucket].orEmpty()
        if (packages.isNotEmpty()) {
            item(key = "bucket:" + bucket.name) { SectionTitle(stringResource(bucketLabel(bucket)) + " (${packages.size})") }
            val rows = packages
                .map { pkg -> BgRow(pkg, byPackage[pkg], byPackage[pkg]?.label ?: pkg, null, null) }
                .sortedWith(compareBy(Collator.getInstance()) { it.label })
            items(rows, key = { "b:${bucket.name}:" + it.packageName }) { row ->
                AppLine(row = row, lines = emptyList(), onOpenDetails = onOpenDetails)
            }
        }
    }
    val exempt = snap.batteryExempt.filter { visible(it) }
        .map { pkg -> BgRow(pkg, byPackage[pkg], byPackage[pkg]?.label ?: pkg, null, null) }
        .sortedWith(compareBy(Collator.getInstance()) { it.label })
    item(key = "exempt") { SectionTitle(stringResource(R.string.bg_battery_exempt) + " (${exempt.size})") }
    if (exempt.isEmpty()) {
        item { EmptyText(R.string.bg_battery_empty) }
    } else {
        items(exempt, key = { "e:" + it.packageName }) { row ->
            AppLine(row = row, lines = emptyList(), onOpenDetails = onOpenDetails)
        }
    }
}

private fun bucketLabel(b: StandbyBucket) = when (b) {
    StandbyBucket.ACTIVE -> R.string.bg_bucket_active
    StandbyBucket.WORKING_SET -> R.string.bg_bucket_working_set
    StandbyBucket.FREQUENT -> R.string.bg_bucket_frequent
    StandbyBucket.RARE -> R.string.bg_bucket_rare
    StandbyBucket.RESTRICTED -> R.string.bg_bucket_restricted
    StandbyBucket.OTHER -> R.string.bg_bucket_other
}

// ---------------- Shared pieces ----------------

/** Tap = App info (Force stop there), long-press = our details screen. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppLine(row: BgRow, lines: List<String>, onOpenDetails: (String) -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (row.app != null) openAppInfo(context, row.app) else openAppInfoForPackage(context, row.packageName)
                },
                onLongClick = { row.app?.let { onOpenDetails(it.key) } },
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SmallIcon(row.app)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(row.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            lines.forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SmallIcon(app: AppEntry?) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { 36.dp.roundToPx() }
    val icon by produceState(app?.let { AppIcons.cached(it.key) }, app?.key) {
        if (value == null && app != null) {
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

@Composable
private fun InfoCard(text: String? = null, textRes: Int? = null, error: Boolean = false) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (error) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(if (error) 16.dp else 0.dp),
    ) {
        Text(
            text ?: stringResource(textRes!!),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun EmptyText(res: Int) {
    Text(
        stringResource(res),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}