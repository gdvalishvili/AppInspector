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
import android.content.ActivityNotFoundException
import android.content.ComponentName


/** Opens the system "App info" screen; work apps go through LauncherApps (verified in spike). */
fun openAppInfo(context: Context, app: AppEntry) {
    try {
        val component = app.component
        val user = app.user
        if (app.profile == AppProfile.WORK && component != null && user != null) {
            context.getSystemService(LauncherApps::class.java)
                .startAppDetailsActivity(component, user, null, null)
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

/** Opens the system uninstall dialog (the user confirms). Needs REQUEST_DELETE_PACKAGES. */
fun requestUninstall(context: Context, packageName: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null)))
    } catch (e: Exception) {
        Toast.makeText(context, R.string.error_open_app_info, Toast.LENGTH_SHORT).show()
    }
}

/** Opens the Play Store app itself (Manage apps → Updates is one tap from there). */
fun openPlayStoreApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage("com.android.vending")
    try {
        if (intent != null) context.startActivity(intent)
        else Toast.makeText(context, R.string.error_open_app_info, Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, R.string.error_open_app_info, Toast.LENGTH_SHORT).show()
    }
}

private const val PLAY_PACKAGE = "com.android.vending"
private const val PLAY_MY_APPS_ACTION = "com.google.android.finsky.VIEW_MY_DOWNLOADS"
private const val PLAY_MAIN_ACTIVITY = "com.google.android.finsky.activities.MainActivity"

/**
 * Opens Play Store's "Manage apps & device" page.
 * Unofficial intent (verified with adb on Pixel). It may break after a Play update,
 * so we fall back to simply opening the Play Store.
 */
fun openPlayUpdates(context: Context) {
    val candidates = listOf(
        Intent(PLAY_MY_APPS_ACTION).setComponent(ComponentName(PLAY_PACKAGE, PLAY_MAIN_ACTIVITY)),
        Intent(PLAY_MY_APPS_ACTION).setPackage(PLAY_PACKAGE),
    )
    for (intent in candidates) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: ActivityNotFoundException) {
            // try next
        } catch (e: SecurityException) {
            // try next
        }
    }
    openPlayStoreApp(context)
}

/** Launches the app: this profile via its launch intent, work profile via LauncherApps. */
fun openApp(context: Context, app: AppEntry) {
    try {
        if (app.profile == AppProfile.WORK) {
            val component = app.component
            val user = app.user
            if (component != null && user != null) {
                context.getSystemService(LauncherApps::class.java)
                    .startMainActivity(component, user, null, null)
                return
            }
        } else {
            val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            if (intent != null) {
                context.startActivity(intent)
                return
            }
        }
    } catch (e: Exception) {
        // fall through to the message
    }
    Toast.makeText(context, R.string.error_open_app, Toast.LENGTH_SHORT).show()
}