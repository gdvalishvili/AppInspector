package com.gdvalishvili.appinspector.util

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList

/** Per-app language via the system LocaleManager (Android 13+). Older devices follow the system language. */
object AppLanguage {
    const val GEORGIAN = "ka"
    const val ENGLISH = "en"

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** Changing the language recreates the Activity; the ViewModel survives. */
    fun set(context: Context, tag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags(tag)
        }
    }
}