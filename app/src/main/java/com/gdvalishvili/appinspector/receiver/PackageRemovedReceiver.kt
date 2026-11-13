package com.gdvalishvili.appinspector.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gdvalishvili.appinspector.data.AppHistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** System tells us when an app is fully uninstalled -> exact removal time. */
class PackageRemovedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_FULLY_REMOVED) return
        val packageName = intent.data?.schemeSpecificPart ?: return

        // goAsync: keep the receiver alive while we write to the database
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppHistoryRepository(context.applicationContext).recordRemoval(packageName)
            } finally {
                pending.finish()
            }
        }
    }
}