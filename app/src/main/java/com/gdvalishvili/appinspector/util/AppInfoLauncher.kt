package com.gdvalishvili.appinspector.util

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import com.gdvalishvili.appinspector.R
import com.gdvalishvili.appinspector.model.AppEntry
import com.gdvalishvili.appinspector.model.AppProfile

/** Opens the system "App info" screen; work apps go through LauncherApps (verified in spike). */
fun openAppInfo(context: Context, app: AppEntry) {
    try {
        val component = app.component
        if (app.profile == AppProfile.WORK && component != null) {
            context.getSystemService(LauncherApps::class.java)
                .startAppDetailsActivity(component, app.user, null, null)
        } else {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", app.packageName, null),
                )
            )
        }
    } catch (e: Exception) {
        Toast.makeText(context, R.string.error_open_app_info, Toast.LENGTH_SHORT).show()
    }
}

/** Opens the Play Store page of a (removed) app; falls back to the web page. */
fun openStorePage(context: Context, packageName: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
    } catch (e: Exception) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
        )
    }
}

/** Opens the system "App info" screen for an app in this profile. */
fun openAppInfoForPackage(context: Context, packageName: String) {
    try {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null),
            )
        )
    } catch (e: Exception) {
        Toast.makeText(context, R.string.error_open_app_info, Toast.LENGTH_SHORT).show()
    }
}