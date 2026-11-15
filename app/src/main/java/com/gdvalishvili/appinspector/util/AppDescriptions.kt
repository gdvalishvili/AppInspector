package com.gdvalishvili.appinspector.util

import android.content.Context
import android.content.pm.LauncherApps
import android.util.LruCache
import com.gdvalishvili.appinspector.model.AppEntry
import com.gdvalishvili.appinspector.model.AppProfile

/** Manifest description (android:description). Most apps don't set it -> "" (shown as "—"). */
object AppDescriptions {

    private val cache = LruCache<String, String>(400)

    fun cached(key: String): String? = cache.get(key)

    fun load(context: Context, app: AppEntry): String {
        cache.get(app.key)?.let { return it }
        val pm = context.packageManager
        val text = try {
            val info = when (app.profile) {
                AppProfile.THIS -> @Suppress("DEPRECATION") pm.getApplicationInfo(app.packageName, 0)
                AppProfile.WORK -> app.user?.let { user ->
                    context.getSystemService(LauncherApps::class.java)
                        .getApplicationInfo(app.packageName, 0, user)
                }
            }
            info?.loadDescription(pm)?.toString()?.trim().orEmpty()
        } catch (e: Exception) {
            ""
        }
        cache.put(app.key, text)
        return text
    }
}