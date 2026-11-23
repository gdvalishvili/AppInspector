package com.gdvalishvili.appinspector.ui.background

import android.text.format.Formatter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gdvalishvili.appinspector.R
import com.gdvalishvili.appinspector.db.AppDatabase
import com.gdvalishvili.appinspector.db.DailyNetworkEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val shortDay: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM")
private const val DAYS = 30

/** Total + busiest day + small bar chart of background data for the last 30 finished days. */
@Composable
fun NetworkHistoryBlock(packageName: String) {
    val context = LocalContext.current
    val days by produceState<List<DailyNetworkEntity>?>(null, packageName) {
        val today = LocalDate.now()
        value = withContext(Dispatchers.IO) {
            AppDatabase.get(context.applicationContext).networkDao()
                .daysForPackage(packageName, today.minusDays(DAYS.toLong()).toString(), today.minusDays(1).toString())
        }
    }
    val list = days ?: return
    val style = MaterialTheme.typography.bodySmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    if (list.none { it.bgBytes > 0L }) {
        Text(stringResource(R.string.bg_history_empty), style = style, color = muted, modifier = Modifier.padding(top = 4.dp))
        return
    }
    val total = list.sumOf { it.bgBytes }
    val max = list.maxBy { it.bgBytes }
    Text(
        stringResource(
            R.string.bg_history_title,
            Formatter.formatShortFileSize(context, total),
            Formatter.formatShortFileSize(context, max.bgBytes),
            LocalDate.parse(max.day).format(shortDay),
        ),
        style = style,
        color = muted,
        modifier = Modifier.padding(top = 4.dp),
    )
    val today = LocalDate.now()
    val byDay = list.associate { it.day to it.bgBytes }
    val values = (DAYS downTo 1).map { byDay[today.minusDays(it.toLong()).toString()] ?: 0L }
    val color = MaterialTheme.colorScheme.primary
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(top = 4.dp)
    ) {
        val maxValue = values.max().coerceAtLeast(1L).toFloat()
        val w = size.width / values.size
        values.forEachIndexed { i, v ->
            val h = size.height * (v / maxValue)
            drawRect(color, topLeft = Offset(i * w + w * 0.15f, size.height - h), size = Size(w * 0.7f, h))
        }
    }
}