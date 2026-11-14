package com.gdvalishvili.appinspector

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gdvalishvili.appinspector.model.Period
import com.gdvalishvili.appinspector.model.PeriodType
import com.gdvalishvili.appinspector.notify.ReportNotifier
import com.gdvalishvili.appinspector.ui.AppListScreen
import com.gdvalishvili.appinspector.ui.AppListViewModel
import com.gdvalishvili.appinspector.ui.details.AppDetailsScreen
import com.gdvalishvili.appinspector.ui.report.ReportScreen
import com.gdvalishvili.appinspector.ui.settings.SettingsScreen
import com.gdvalishvili.appinspector.ui.theme.AppInspectorTheme
import java.time.LocalDate
import com.gdvalishvili.appinspector.ui.updates.UpdatesScreen

/** Simple state-based navigation (no navigation library needed). */
enum class Screen { LIST, REPORT, SETTINGS, DETAILS, UPDATES }

class MainActivity : ComponentActivity() {

    /** Period requested by a notification tap (or a chart bar); consumed by the report screen. */
    private val requestedPeriod = mutableStateOf<Period?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) requestedPeriod.value = readPeriod(intent)

        setContent {
            AppInspectorTheme {
                // Shared with AppListScreen (same Activity -> same instance)
                val listViewModel: AppListViewModel = viewModel()
                val allApps by listViewModel.allApps.collectAsStateWithLifecycle()
                val listStateForUpdates by listViewModel.uiState.collectAsStateWithLifecycle()
                var screen by rememberSaveable { mutableStateOf(Screen.LIST) }
                var detailsKey by rememberSaveable { mutableStateOf<String?>(null) }
                var detailsReturn by rememberSaveable { mutableStateOf(Screen.LIST) }

                fun openDetails(key: String, from: Screen) {
                    detailsKey = key
                    detailsReturn = from
                    screen = Screen.DETAILS
                }

                val request = requestedPeriod.value
                LaunchedEffect(request) {
                    if (request != null) screen = Screen.REPORT
                }

                when (screen) {
                    Screen.LIST -> AppListScreen(
                        onOpenReport = { screen = Screen.REPORT },
                        onOpenSettings = { screen = Screen.SETTINGS },
                        onOpenDetails = { key -> openDetails(key, Screen.LIST) },
                        viewModel = listViewModel,
                        onOpenUpdates = { screen = Screen.UPDATES },
                    )
                    Screen.REPORT -> {
                        BackHandler { screen = Screen.LIST }
                        ReportScreen(
                            onBack = { screen = Screen.LIST },
                            requestedPeriod = request,
                            onRequestHandled = { requestedPeriod.value = null },
                            onOpenDetails = { key -> openDetails(key, Screen.REPORT) },
                        )
                    }
                    Screen.SETTINGS -> {
                        BackHandler { screen = Screen.LIST }
                        SettingsScreen(onBack = { screen = Screen.LIST })
                    }
                    Screen.UPDATES -> {
                        BackHandler { screen = Screen.LIST }
                        UpdatesScreen(
                            apps = allApps,
                            weekUsage = listStateForUpdates.weekUsage,
                            onBack = { screen = Screen.LIST },
                            onOpenDetails = { key -> openDetails(key, Screen.UPDATES) },
                            onRefreshList = listViewModel::refresh,
                        )
                    }
                    Screen.DETAILS -> {
                        BackHandler { screen = detailsReturn }
                        val apps = allApps
                        val app = apps?.firstOrNull { it.key == detailsKey }
                        when {
                            app != null -> AppDetailsScreen(
                                app = app,
                                onBack = { screen = detailsReturn },
                                onOpenDay = { day -> requestedPeriod.value = Period.of(PeriodType.DAY, day) },
                                onRefreshList = listViewModel::refresh,
                            )
                            // App was uninstalled (or is gone): go back
                            apps != null -> LaunchedEffect(Unit) { screen = detailsReturn }
                            // List not loaded yet (e.g. opened from a report after a notification)
                            else -> {
                                LaunchedEffect(Unit) { listViewModel.refresh() }
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
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