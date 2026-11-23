package com.gdvalishvili.appinspector.ui.privacy

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.gdvalishvili.appinspector.R
import com.gdvalishvili.appinspector.data.privacy.PrivacyResult
import com.gdvalishvili.appinspector.data.privacy.ReasonKind
import com.gdvalishvili.appinspector.data.privacy.RiskLevel

@Composable
fun levelLabel(level: RiskLevel): String = stringResource(
    when (level) {
        RiskLevel.LOW -> R.string.pv_level_low
        RiskLevel.MEDIUM -> R.string.pv_level_medium
        RiskLevel.HIGH -> R.string.pv_level_high
    }
)

@Composable
fun levelColor(level: RiskLevel): Color = when (level) {
    RiskLevel.HIGH -> MaterialTheme.colorScheme.error
    RiskLevel.MEDIUM -> if (isSystemInDarkTheme()) Color(0xFFFFB870) else Color(0xFF8A4B00)
    RiskLevel.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** "5 trackers · location, microphone · background data 120 MB" */
@Composable
fun shortSummary(result: PrivacyResult): String {
    val parts = ArrayList<String>()
    parts += if (result.trackers.isEmpty()) stringResource(R.string.pv_no_trackers)
    else pluralStringResource(R.plurals.pv_trackers, result.trackers.size, result.trackers.size)
    val other = result.risk.reasons.mapNotNull { r ->
        when (r.kind) {
            ReasonKind.PERM_LOCATION -> stringResource(R.string.pv_r_location)
            ReasonKind.PERM_MICROPHONE -> stringResource(R.string.pv_r_microphone)
            ReasonKind.PERM_SMS -> stringResource(R.string.pv_r_sms)
            ReasonKind.PERM_CALL_LOG -> stringResource(R.string.pv_r_call_log)
            ReasonKind.PERM_CAMERA -> stringResource(R.string.pv_r_camera)
            ReasonKind.PERM_CONTACTS -> stringResource(R.string.pv_r_contacts)
            ReasonKind.PERM_SENSORS -> stringResource(R.string.pv_r_sensors)
            ReasonKind.BACKGROUND_DATA -> stringResource(R.string.pv_r_background_data, r.detail)
            ReasonKind.FGS_SENSITIVE -> stringResource(R.string.pv_r_fgs, r.detail)
            else -> null // tracker reasons are already counted above
        }
    }
    if (other.isNotEmpty()) parts += other.joinToString(", ")
    return parts.joinToString(" · ")
}