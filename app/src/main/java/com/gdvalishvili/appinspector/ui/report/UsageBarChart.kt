package com.gdvalishvili.appinspector.ui.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gdvalishvili.appinspector.data.DayUsage
import java.time.LocalDate
import java.time.format.TextStyle

/** Simple bar chart (one bar per day). Tap a bar to open that day. No chart library needed. */
@Composable
fun UsageBarChart(
    days: List<DayUsage>,
    today: LocalDate,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (days.isEmpty()) return
    val max = days.maxOf { it.ms }.coerceAtLeast(1L)
    val barColor = MaterialTheme.colorScheme.primary
    val todayColor = MaterialTheme.colorScheme.tertiary
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val locale = LocalConfiguration.current.locales[0]
    val isWeek = days.size <= 7

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .pointerInput(days) {
                    detectTapGestures { offset ->
                        val slot = size.width.toFloat() / days.size
                        val index = (offset.x / slot).toInt().coerceIn(0, days.lastIndex)
                        val day = days[index].date
                        if (!day.isAfter(today)) onDayClick(day)
                    }
                }
        ) {
            val slot = size.width / days.size
            val barWidth = slot * 0.6f
            val minBar = 2.dp.toPx()
            days.forEachIndexed { i, d ->
                val h = if (d.ms == 0L) minBar
                else (d.ms.toFloat() / max * size.height).coerceAtLeast(minBar)
                drawRoundRect(
                    color = when {
                        d.ms == 0L -> emptyColor
                        d.date == today -> todayColor
                        else -> barColor
                    },
                    topLeft = Offset(i * slot + (slot - barWidth) / 2f, size.height - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            days.forEach { d ->
                val label = when {
                    isWeek -> d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                    d.date.dayOfMonth == 1 || d.date.dayOfMonth % 5 == 0 -> d.date.dayOfMonth.toString()
                    else -> ""
                }
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                )
            }
        }
    }
}