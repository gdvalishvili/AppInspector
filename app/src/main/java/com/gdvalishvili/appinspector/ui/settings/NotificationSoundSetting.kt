package com.gdvalishvili.appinspector.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gdvalishvili.appinspector.R
import com.gdvalishvili.appinspector.data.SettingsStore
import kotlinx.coroutines.launch

/** "Notification sound" switch for report notifications (default off = silent). */
@Composable
fun NotificationSoundSetting(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { SettingsStore(context.applicationContext) }
    val enabled by store.notifySound.collectAsStateWithLifecycle(initialValue = false)
    val scope = rememberCoroutineScope()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_sound), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.settings_sound_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = enabled, onCheckedChange = { value -> scope.launch { store.setNotifySound(value) } })
    }
}