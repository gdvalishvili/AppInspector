package com.gdvalishvili.appinspector.data.logs

import android.content.Context
import android.content.pm.PackageManager
import android.os.DropBoxManager
import com.gdvalishvili.appinspector.data.appLabelOrNull

enum class CrashKind { CRASH, ANR, NATIVE, SYSTEM }

data class CrashEntry(
    val time: Long,
    val tag: String,
    val kind: CrashKind,
    val packageName: String?,
    val label: String?,
    val summary: String,
    val text: String,
)

/** Text parsing of DropBox entries. Pure Kotlin: unit-tested. */
object DropBoxParser {
    private const val MAX_SUMMARY = 200
    private val PROCESS = Regex("(?m)^Process: (\\S+)")
    private val PACKAGE = Regex("(?m)^Package: (\\S+)")
    private val SUBJECT = Regex("(?m)^Subject: (.+)$")
    private val ABORT = Regex("(?m)^Abort message: (.+)$")
    private val SIGNAL = Regex("(?m)^(signal \\d+.*)$")

    /** Only the tags we show; tombstones/proto duplicate the native crash entries. */
    fun kindOf(tag: String): CrashKind? = when {
        tag.endsWith("_native_crash") -> CrashKind.NATIVE
        tag.endsWith("_crash") -> CrashKind.CRASH
        tag.endsWith("_anr") -> CrashKind.ANR
        tag == "SYSTEM_RESTART" || tag == "system_server_watchdog" -> CrashKind.SYSTEM
        else -> null
    }

    fun packageOf(text: String): String? =
        PROCESS.find(text)?.groupValues?.get(1) ?: PACKAGE.find(text)?.groupValues?.get(1)

    fun summaryOf(text: String): String {
        SUBJECT.find(text)?.let { return it.groupValues[1].trim().take(MAX_SUMMARY) }
        ABORT.find(text)?.let { return it.groupValues[1].trim().take(MAX_SUMMARY) }
        SIGNAL.find(text)?.let { return it.groupValues[1].trim().take(MAX_SUMMARY) }
        val body = text.substringAfter("\n\n", "")
        val line = body.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
            ?: text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
            ?: ""
        return line.take(MAX_SUMMARY)
    }
}

/** Crash / ANR history kept by the system on disk (survives reboots, usually a few days). */
class DropBoxReader(private val context: Context) {

    data class Result(val entries: List<CrashEntry>, val noPermission: Boolean)

    fun read(packageName: String?): Result {
        if (!granted(READ_DROPBOX_DATA) && !granted(READ_LOGS)) return Result(emptyList(), true)
        val dbm = context.getSystemService(DropBoxManager::class.java) ?: return Result(emptyList(), false)
        val pm = context.packageManager
        val out = ArrayList<CrashEntry>()
        try {
            var since = 0L
            while (true) {
                val entry = dbm.getNextEntry(null, since) ?: break
                entry.use { e ->
                    since = e.timeMillis
                    val tag = e.tag
                    val kind = DropBoxParser.kindOf(tag) ?: return@use
                    val text = e.getText(MAX_TEXT_BYTES).orEmpty()
                    val pkg = DropBoxParser.packageOf(text)
                    if (packageName != null && pkg != packageName) return@use
                    out += CrashEntry(
                        time = e.timeMillis,
                        tag = tag,
                        kind = kind,
                        packageName = pkg,
                        label = pkg?.let { pm.appLabelOrNull(it) },
                        summary = DropBoxParser.summaryOf(text),
                        text = text,
                    )
                }
            }
        } catch (e: SecurityException) {
            return Result(emptyList(), true)
        }
        return Result(out.sortedByDescending { it.time }.take(MAX_ENTRIES), false)
    }

    private fun granted(permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val READ_LOGS = "android.permission.READ_LOGS"
        const val READ_DROPBOX_DATA = "android.permission.READ_DROPBOX_DATA"
        private const val MAX_TEXT_BYTES = 64 * 1024
        private const val MAX_ENTRIES = 200
    }
}