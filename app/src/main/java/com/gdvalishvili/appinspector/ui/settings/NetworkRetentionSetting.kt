package com.gdvalishvili.appinspector.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gdvalishvili.appinspector.R
import com.gdvalishvili.appinspector.data.SettingsStore
import kotlinx.coroutines.launch

/** Retention of the background data history (daily_network); 0 = forever, default 2 years. */
@Composable
fun NetworkRetentionSetting(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { SettingsStore(context.applicationContext) }
    val years by store.networkRetentionYears.collectAsStateWithLifecycle(initialValue = 2)
    val scope = rememberCoroutineScope()

    Column(modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(stringResource(R.string.settings_net_retention), style = MaterialTheme.typography.bodyLarge)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(1, 2, 5, 0).forEach { value ->
                FilterChip(
                    selected = years == value,
                    onClick = { scope.launch { store.setNetworkRetentionYears(value) } },
                    label = {
                        Text(
                            if (value == 0) stringResource(R.string.settings_net_forever)
                            else stringResource(R.string.settings_net_years, value)
                        )
                    },
                )
            }
        }
    }
}