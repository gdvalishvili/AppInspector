package com.gdvalishvili.appinspector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gdvalishvili.appinspector.spike.SpikeScreen
import com.gdvalishvili.appinspector.ui.theme.AppInspectorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppInspectorTheme {
                SpikeScreen()
            }
        }
    }
}