package com.gdvalishvili.appinspector.ui.components

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gdvalishvili.appinspector.R
import com.gdvalishvili.appinspector.model.AppEntry
import com.gdvalishvili.appinspector.model.AppKind
import com.gdvalishvili.appinspector.model.AppProfile
import com.gdvalishvili.appinspector.model.AppSize
import com.gdvalishvili.appinspector.model.RemovedApp
import com.gdvalishvili.appinspector.model.UsageStatus
import com.gdvalishvili.appinspector.util.AppDescriptions
import com.gdvalishvili.appinspector.util.AppIcons
import com.gdvalishvili.appinspector.util.categoryTitle
import com.gdvalishvili.appinspector.util.formatDate
import com.gdvalishvili.appinspector.util.formatDateTime
import com.gdvalishvili.appinspector.util.formatDuration
import com.gdvalishvili.appinspector.util.relativeTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private val ICON_SIZE = 44.dp
private val STALE_MS = TimeUnit.DAYS.toMillis(90)

/** One installed app. Tap = system App info, long-press = our details screen. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppRow(
    app: AppEntry,
    now: Long,
    showCategory: Boolean,
    showSource: Boolean,
    showSize: Boolean,
    showDescription: Boolean,
    weekMs: Long?,          // null = not available (work app or no Usage access)
    size: AppSize?,
    sizesReady: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardDefaults.shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(Modifier.padding(12.dp)) {
            AppIcon(app)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(app.packageName)
                        app.versionName?.let { append(" · v").append(it) }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Tags(app, now)
                Spacer(Modifier.height(4.dp))
                DateLine(R.string.label_installed, installedText(app))
                DateLine(R.string.label_updated, updatedText(app))
                DateLine(R.string.label_last_used, lastUsedText(app, now))
                if (weekMs != null) DateLine(R.string.label_week, formatDuration(weekMs))
                if (showCategory) DateLine(R.string.label_category, categoryTitle(app.category))
                if (showSource) DateLine(R.string.label_source, sourceText(app))
                if (showSize) DateLine(R.string.label_size, sizeText(size, sizesReady))
                if (showDescription) DescriptionLine(app)
            }
        }
    }
}

@Composable
private fun AppIcon(app: AppEntry) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { ICON_SIZE.roundToPx() }
    // Load icon off the main thread, only for rows that are on screen
    val icon by produceState<ImageBitmap?>(initialValue = AppIcons.cached(app.key), app.key) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                AppIcons.load(context.applicationContext, app, sizePx)
            }
        }
    }
    val bitmap = icon
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(ICON_SIZE))
    } else {
        Box(
            Modifier
                .size(ICON_SIZE)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
        )
    }
}

private data class TagSpec(@param:StringRes val text: Int, val container: Color, val content: Color)

@Composable
private fun Tags(app: AppEntry, now: Long) {
    val scheme = MaterialTheme.colorScheme
    // Fixed orange for "not used for a long time" (dynamic color has no orange)
    val dark = isSystemInDarkTheme()
    val staleContainer = if (dark) Color(0xFF5A3A00) else Color(0xFFFFDDB3)
    val staleContent = if (dark) Color(0xFFFFDDB3) else Color(0xFF2A1800)
    val lastUsedAt = app.lastUsedAt

    val tags = buildList {
        if (app.profile == AppProfile.WORK) {
            add(TagSpec(R.string.badge_work, scheme.tertiaryContainer, scheme.onTertiaryContainer))
        }
        when (app.kind) {
            AppKind.SYSTEM ->
                add(TagSpec(R.string.badge_system, scheme.surfaceVariant, scheme.onSurfaceVariant))
            AppKind.UPDATED_SYSTEM ->
                add(TagSpec(R.string.badge_updated_system, scheme.secondaryContainer, scheme.onSecondaryContainer))
            AppKind.USER -> Unit
        }
        if (app.isArchived) {
            add(TagSpec(R.string.badge_archived, scheme.surfaceVariant, scheme.onSurfaceVariant))
        }
        when (app.usageStatus) {
            UsageStatus.NEVER_OPENED ->
                add(TagSpec(R.string.badge_never_opened, scheme.errorContainer, scheme.onErrorContainer))
            UsageStatus.BACKGROUND ->
                add(TagSpec(R.string.badge_background, scheme.surfaceVariant, scheme.onSurfaceVariant))
            UsageStatus.OLDER_THAN_HISTORY ->
                add(TagSpec(R.string.badge_very_old, staleContainer, staleContent))
            UsageStatus.USED ->
                if (lastUsedAt != null && now - lastUsedAt > STALE_MS) {
                    add(TagSpec(R.string.badge_stale, staleContainer, staleContent))
                }
            else -> Unit
        }
    }
    if (tags.isEmpty()) return

    Row(
        modifier = Modifier
            .padding(top = 6.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tags.forEach { Tag(it) }
    }
}

@Composable
private fun Tag(spec: TagSpec) {
    Surface(color = spec.container, contentColor = spec.content, shape = RoundedCornerShape(50)) {
        Text(
            text = stringResource(spec.text),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun DateLine(@StringRes label: Int, value: String) {
    Text(
        text = stringResource(label) + ": " + value,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ---------- Texts shared with the details screen ----------

@Composable
internal fun installedText(app: AppEntry): String {
    val installedAt = app.installedAt
    return when {
        app.isFactoryInstall -> stringResource(R.string.value_factory)
        installedAt == null -> stringResource(R.string.value_dash)
        else -> formatDate(installedAt)
    }
}

@Composable
internal fun updatedText(app: AppEntry): String {
    val updatedAt = app.updatedAt
    return when {
        updatedAt == null ->
            if (app.profile == AppProfile.THIS) stringResource(R.string.value_never_updated)
            else stringResource(R.string.value_dash)
        app.updatedAtApprox -> "≈ " + formatDate(updatedAt)
        else -> formatDate(updatedAt)
    }
}

@Composable
internal fun lastUsedText(app: AppEntry, now: Long): String {
    val lastUsedAt = app.lastUsedAt
    return when (app.usageStatus) {
        UsageStatus.USED ->
            if (lastUsedAt != null) "${relativeTime(lastUsedAt, now)} · ${formatDate(lastUsedAt)}"
            else stringResource(R.string.value_dash)
        UsageStatus.NEVER_OPENED -> stringResource(R.string.value_never_opened)
        UsageStatus.OLDER_THAN_HISTORY -> stringResource(R.string.value_older_than_history)
        UsageStatus.BACKGROUND -> stringResource(R.string.value_background)
        UsageStatus.NO_PERMISSION -> stringResource(R.string.value_no_permission)
        UsageStatus.NOT_AVAILABLE -> stringResource(R.string.value_dash)
    }
}

@Composable
internal fun sourceText(app: AppEntry): String {
    val installer = app.installerPackage
    return when {
        !app.installSourceKnown -> stringResource(R.string.value_dash)
        installer == null ->
            if (app.kind == AppKind.USER) stringResource(R.string.source_unknown)
            else stringResource(R.string.source_preinstalled)
        installer == "com.android.shell" -> "ADB"
        else -> app.installerLabel ?: installer
    }
}

@Composable
internal fun sizeText(size: AppSize?, ready: Boolean): String {
    val context = LocalContext.current
    return when {
        size != null -> stringResource(
            R.string.size_detail,
            Formatter.formatShortFileSize(context, size.total),
            Formatter.formatShortFileSize(context, size.appBytes),
            Formatter.formatShortFileSize(context, size.dataBytes),
        )
        !ready -> stringResource(R.string.value_loading)
        else -> stringResource(R.string.value_dash)
    }
}

/** Manifest description, loaded lazily only for rows on screen. */
@Composable
internal fun DescriptionLine(app: AppEntry) {
    val context = LocalContext.current
    val text by produceState<String?>(AppDescriptions.cached(app.key), app.key) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { AppDescriptions.load(context.applicationContext, app) }
        }
    }
    val shown = when (val t = text) {
        null -> stringResource(R.string.value_loading)
        "" -> stringResource(R.string.value_dash)
        else -> t
    }
    Text(
        text = stringResource(R.string.label_description) + ": " + shown,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

// ---------- Removed apps ----------

/** Row for a removed app. Tap opens its Play Store page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemovedRow(app: RemovedApp, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(app.packageName)
                    app.versionName?.let { append(" · v").append(it) }
                },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (app.profile == AppProfile.WORK) {
                Row(Modifier.padding(top = 6.dp)) {
                    Tag(TagSpec(R.string.badge_work, scheme.tertiaryContainer, scheme.onTertiaryContainer))
                }
            }
            Spacer(Modifier.height(4.dp))
            DateLine(
                R.string.label_installed,
                app.installedAt?.let { formatDate(it) } ?: stringResource(R.string.value_dash),
            )
            DateLine(
                R.string.label_removed,
                (if (app.removedAtApprox) "≈ " else "") + formatDateTime(app.removedAt),
            )
        }
    }
}