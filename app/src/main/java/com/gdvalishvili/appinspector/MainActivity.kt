package com.gdvalishvili.appinspector

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.gdvalishvili.appinspector.model.Period
import com.gdvalishvili.appinspector.model.PeriodType
import com.gdvalishvili.appinspector.notify.ReportNotifier
import com.gdvalishvili.appinspector.ui.AppListScreen
import com.gdvalishvili.appinspector.ui.report.ReportScreen
import com.gdvalishvili.appinspector.ui.settings.SettingsScreen
import com.gdvalishvili.appinspector.ui.theme.AppInspectorTheme
import java.time.LocalDate

/** Simple state-based navigation (no navigation library needed). */
enum class Screen { LIST, REPORT, SETTINGS }

class MainActivity : ComponentActivity() {

    /** Period requested by a notification tap; consumed by the report screen. */
    private val requestedPeriod = mutableStateOf<Period?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) requestedPeriod.value = readPeriod(intent)

        setContent {
            AppInspectorTheme {
                var screen by rememberSaveable { mutableStateOf(Screen.LIST) }
                val request = requestedPeriod.value
                LaunchedEffect(request) {
                    if (request != null) screen = Screen.REPORT
                }

                when (screen) {
                    Screen.LIST -> AppListScreen(
                        onOpenReport = { screen = Screen.REPORT },
                        onOpenSettings = { screen = Screen.SETTINGS },
                    )
                    Screen.REPORT -> {
                        BackHandler { screen = Screen.LIST }
                        ReportScreen(
                            onBack = { screen = Screen.LIST },
                            requestedPeriod = request,
                            onRequestHandled = { requestedPeriod.value = null },
                        )
                    }
                    Screen.SETTINGS -> {
                        BackHandler { screen = Screen.LIST }
                        SettingsScreen(onBack = { screen = Screen.LIST })
                    }
                }
            }
        }
    }

    /** App already open (singleTop) and the user taps a notification. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readPeriod(intent)?.let { requestedPeriod.value = it }
    }

    private fun readPeriod(intent: Intent?): Period? {
        val type = intent?.getStringExtra(ReportNotifier.EXTRA_PERIOD_TYPE) ?: return null
        val start = intent.getStringExtra(ReportNotifier.EXTRA_PERIOD_START) ?: return null
        return runCatching { Period(PeriodType.valueOf(type), LocalDate.parse(start)) }.getOrNull()
    }
}