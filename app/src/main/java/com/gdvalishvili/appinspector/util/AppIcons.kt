package com.gdvalishvili.appinspector.util

import android.content.Context
import android.content.pm.LauncherApps
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.gdvalishvili.appinspector.model.AppEntry
import com.gdvalishvili.appinspector.model.AppProfile

/** Loads app icons lazily (per visible row) and keeps them in a memory cache. */
object AppIcons {

    private val cache = LruCache<String, ImageBitmap>(400)

    fun cached(key: String): ImageBitmap? = cache.get(key)

    fun load(context: Context, app: AppEntry, sizePx: Int): ImageBitmap? {
        cache.get(app.key)?.let { return it }
        val drawable = try {
            when (app.profile) {
                AppProfile.THIS -> context.packageManager.getApplicationIcon(app.packageName)
                // Badged icon = icon with the work briefcase
                AppProfile.WORK -> context.getSystemService(LauncherApps::class.java)
                    .getActivityList(app.packageName, app.user)
                    .firstOrNull()
                    ?.getBadgedIcon(0)
            }
        } catch (e: Exception) {
            null
        } ?: return null

        val bitmap = drawable.toBitmap(sizePx, sizePx).asImageBitmap()
        cache.put(app.key, bitmap)
        return bitmap
    }
}