package com.gdvalishvili.appinspector.util

import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.gdvalishvili.appinspector.R

/** Localized category name from the system (Game, Social, ...). */
@Composable
fun categoryTitle(category: Int): String {
    val context = LocalContext.current
    return ApplicationInfo.getCategoryTitle(context, category)?.toString()
        ?: stringResource(R.string.category_undefined)
}