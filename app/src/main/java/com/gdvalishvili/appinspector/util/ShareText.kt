package com.gdvalishvili.appinspector.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import com.gdvalishvili.appinspector.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Writes text to cache/share/<name> and opens the Android share sheet. Old shared files are removed. */
suspend fun shareText(context: Context, fileName: String, text: String) {
    val uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, fileName.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        file.writeText(text, Charsets.UTF_8)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, null))
}

fun copyToClipboard(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText("log", text))
    // Android 13+ shows its own "copied" confirmation
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, R.string.logs_copied, Toast.LENGTH_SHORT).show()
}