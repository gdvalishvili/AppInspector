package com.gdvalishvili.appinspector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.gdvalishvili.appinspector.ui.AppListScreen
import com.gdvalishvili.appinspector.ui.report.ReportScreen
import com.gdvalishvili.appinspector.ui.theme.AppInspectorTheme

/** Two screens, simple state-based navigation (no navigation library needed). */
enum class Screen { LIST, REPORT }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppInspectorTheme {
                var screen by rememberSaveable { mutableStateOf(Screen.LIST) }
                when (screen) {
                    Screen.LIST -> AppListScreen(onOpenReport = { screen = Screen.REPORT })
                    Screen.REPORT -> {
                        // System back returns to the list instead of closing the app
                        BackHandler { screen = Screen.LIST }
                        ReportScreen(onBack = { screen = Screen.LIST })
                    }
                }
            }
        }
    }
}