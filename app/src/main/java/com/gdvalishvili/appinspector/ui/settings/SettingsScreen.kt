package com.gdvalishvili.appinspector.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gdvalishvili.appinspector.R
import com.gdvalishvili.appinspector.notify.ReportNotifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var notificationsAllowed by remember { mutableStateOf(ReportNotifier.canNotify(context)) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showTimePicker by remember { mutableStateOf(false) }

    // Re-check when returning from system settings
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = ReportNotifier.canNotify(context)
        viewModel.refreshAccess()
        onPauseOrDispose { }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsAllowed = ReportNotifier.canNotify(context)
        if (granted) pendingAction?.invoke()
        pendingAction = null
    }

    /** Runs [action] only when notifications are allowed; asks for the permission first if needed. */
    fun withNotificationPermission(action: () -> Unit) {
        when {
            ReportNotifier.canNotify(context) -> action()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                pendingAction = action
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            else -> openNotificationSettings(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        }
    ) { innerPadding ->
        val s = settings
        if (s != null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // ---------- Reports ----------
                SectionTitle(R.string.settings_reports)
                if (!notificationsAllowed && (s.daily || s.weekly || s.monthly)) {
                    BlockedCard { openNotificationSettings(context) }
                }
                SwitchRow(R.string.settings_daily, R.string.settings_daily_desc, s.daily) { on ->
                    if (on) withNotificationPermission { viewModel.setDaily(true) } else viewModel.setDaily(false)
                }
                SwitchRow(R.string.settings_weekly, R.string.settings_weekly_desc, s.weekly) { on ->
                    if (on) withNotificationPermission { viewModel.setWeekly(true) } else viewModel.setWeekly(false)
                }
                SwitchRow(R.string.settings_monthly, R.string.settings_monthly_desc, s.monthly) { on ->
                    if (on) withNotificationPermission { viewModel.setMonthly(true) } else viewModel.setMonthly(false)
                }
                ValueRow(
                    title = R.string.settings_time,
                    description = R.string.settings_time_desc,
                    value = "%02d:%02d".format(s.hour, s.minute),
                ) { showTimePicker = true }
                OutlinedButton(
                    onClick = { withNotificationPermission { viewModel.sendNow() } },
                    modifier = Modifier.padding(vertical = 8.dp),
                ) { Text(stringResource(R.string.settings_send_now)) }

                HorizontalDivider(Modifier.padding(vertical = 12.dp))

                // ---------- Data ----------
                SectionTitle(R.string.settings_data)
                Text(stringResource(R.string.settings_retention), style = MaterialTheme.typography.bodyLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    listOf(1, 2, 5).forEach { years ->
                        FilterChip(
                            selected = s.retentionYears == years,
                            onClick = { viewModel.setRetention(years) },
                            label = { Text(pluralStringResource(R.plurals.years_count, years, years)) },
                        )
                    }
                }
                ValueRow(
                    title = R.string.settings_usage_access,
                    description = null,
                    value = stringResource(if (viewModel.hasUsageAccess) R.string.settings_on else R.string.settings_off),
                ) { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }

                HorizontalDivider(Modifier.padding(vertical = 12.dp))

                // ---------- About ----------
                SectionTitle(R.string.settings_about)
                Text(
                    stringResource(R.string.settings_version, viewModel.versionName),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    val s = settings
    if (showTimePicker && s != null) {
        TimeDialog(
            hour = s.hour,
            minute = s.minute,
            onConfirm = viewModel::setTime,
            onDismiss = { showTimePicker = false },
        )
    }
}

@Composable
private fun SectionTitle(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun SwitchRow(
    @StringRes title: Int,
    @StringRes description: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ValueRow(
    @StringRes title: Int,
    @StringRes description: Int?,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    stringResource(description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text("$value  ›", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun BlockedCard(onAllow: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.settings_notif_blocked),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAllow) { Text(stringResource(R.string.settings_notif_allow)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(hour: Int, minute: Int, onConfirm: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onConfirm(state.hour, state.minute)
                onDismiss()
            }) { Text(stringResource(R.string.date_pick_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        text = { TimePicker(state = state) },
    )
}

private fun openNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    )
}