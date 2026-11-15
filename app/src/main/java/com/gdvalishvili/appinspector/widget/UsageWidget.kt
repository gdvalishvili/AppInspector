package com.gdvalishvili.appinspector.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.gdvalishvili.appinspector.MainActivity
import com.gdvalishvili.appinspector.R
import com.gdvalishvili.appinspector.util.formatDurationText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val SMALL = DpSize(110.dp, 48.dp)
private val LARGE = DpSize(200.dp, 110.dp)
private val timeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

/** Home screen widget: today's total + top 3 apps. Tap = today's report, ↻ = refresh now. */
class UsageWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, LARGE))

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Fresh numbers every time the system (re)starts the widget, e.g. every 30 min
        runCatching { UsageWidgetData.load(context) }.getOrNull()?.let { data ->
            updateAppWidgetState(context, id) { data.writeTo(it) }
        }
        provideContent {
            GlanceTheme {
                WidgetContent(UsageWidgetData.readFrom(currentState<Preferences>()))
            }
        }
    }
}

class UsageWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UsageWidget()
}

/** ↻ button. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        UsageWidgetUpdater.refreshAll(context)
    }
}

/** Recomputes and redraws all our widgets (called from ↻, app open and DailyWorker). */
object UsageWidgetUpdater {
    suspend fun refreshAll(context: Context) {
        val appContext = context.applicationContext
        val ids = GlanceAppWidgetManager(appContext).getGlanceIds(UsageWidget::class.java)
        if (ids.isEmpty()) return
        val data = runCatching { UsageWidgetData.load(appContext) }.getOrNull() ?: return
        val widget = UsageWidget()
        ids.forEach { id ->
            updateAppWidgetState(appContext, id) { data.writeTo(it) }
            widget.update(appContext, id)
        }
    }
}

private fun openTodayIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java)
        .putExtra(MainActivity.EXTRA_OPEN_TODAY, true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

@Composable
private fun WidgetContent(data: UsageWidgetData?) {
    val context = LocalContext.current
    val small = LocalSize.current.height < LARGE.height
    val primary = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp)
    val secondary = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp)
    val big = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(12.dp)
            .clickable(actionStartActivity(openTodayIntent(context))),
    ) {
        when {
            data == null -> Text(context.getString(R.string.widget_loading), style = secondary)
            data.noAccess -> Text(context.getString(R.string.widget_no_access), style = secondary)
            small -> {
                Text(context.getString(R.string.widget_today), style = secondary)
                Text(formatDurationText(context, data.totalMs), style = big)
            }
            else -> {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = context.getString(R.string.widget_today) + ": " + formatDurationText(context, data.totalMs),
                        style = big,
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight(),
                    )
                    Text(
                        text = "↻",
                        style = big,
                        modifier = GlanceModifier
                            .padding(horizontal = 8.dp)
                            .clickable(actionRunCallback<RefreshAction>()),
                    )
                }
                Spacer(GlanceModifier.height(6.dp))
                if (data.top.isEmpty()) {
                    Text(context.getString(R.string.widget_no_apps), style = secondary)
                } else {
                    data.top.forEach { app -> AppLine(app, primary) }
                }
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    text = context.getString(R.string.widget_updated, timeFormatter.format(Instant.ofEpochMilli(data.updatedAt))),
                    style = secondary,
                )
            }
        }
    }
}

@Composable
private fun AppLine(app: WidgetApp, style: TextStyle) {
    val context = LocalContext.current
    val icon = remember(app.packageName) { loadIcon(context, app.packageName) }
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(provider = ImageProvider(icon), contentDescription = null, modifier = GlanceModifier.size(20.dp))
        } else {
            Spacer(GlanceModifier.size(20.dp))
        }
        Spacer(GlanceModifier.width(8.dp))
        Text(text = app.label, style = style, maxLines = 1, modifier = GlanceModifier.defaultWeight())
        Text(text = formatDurationText(context, app.ms), style = style)
    }
}

private fun loadIcon(context: Context, packageName: String): Bitmap? = try {
    context.packageManager.getApplicationIcon(packageName).toBitmap(72, 72)
} catch (e: PackageManager.NameNotFoundException) {
    null
}