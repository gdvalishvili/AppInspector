package com.gdvalishvili.appinspector.data.logs

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** packageName == null: a system-wide snapshot. */
data class SnapshotInfo(val file: File, val createdAt: Long, val packageName: String?, val sizeBytes: Long)

/** Manually saved logs: files/log_snapshots/<time>__<SYSTEM|package>.log.gz (no database). */
class LogSnapshots(context: Context) {

    private val dir = File(context.filesDir, "log_snapshots")

    /** packageName == null: all snapshots; otherwise only that app's. Newest first. */
    fun list(packageName: String?): List<SnapshotInfo> =
        dir.listFiles().orEmpty()
            .filter { it.name.endsWith(SUFFIX) }
            .map { info(it) }
            .filter { packageName == null || it.packageName == packageName }
            .sortedByDescending { it.createdAt }

    /** keepMax != null: after saving, only the newest keepMax snapshots (of all kinds) remain. */
    fun save(lines: List<LogLine>, packageName: String?, keepMax: Int?): SnapshotInfo {
        dir.mkdirs()
        val file = File(dir, "${STAMP.format(LocalDateTime.now())}__${packageName ?: SYSTEM}$SUFFIX")
        GZIPOutputStream(FileOutputStream(file)).bufferedWriter(Charsets.UTF_8).use { w ->
            lines.forEach { w.write(it.raw); w.newLine() }
        }
        if (keepMax != null) list(null).drop(keepMax).forEach { it.file.delete() }
        return info(file)
    }

    fun load(snapshot: SnapshotInfo): List<LogLine> {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now()
        return GZIPInputStream(FileInputStream(snapshot.file)).bufferedReader(Charsets.UTF_8).useLines { seq ->
            seq.mapNotNull { LogParser.parse(it, zone, now) }.toList()
        }
    }

    fun delete(snapshot: SnapshotInfo) {
        snapshot.file.delete()
    }

    private fun info(file: File): SnapshotInfo {
        val scope = file.name.removeSuffix(SUFFIX).substringAfter("__", SYSTEM)
        return SnapshotInfo(file, file.lastModified(), scope.takeIf { it != SYSTEM }, file.length())
    }

    private companion object {
        const val SUFFIX = ".log.gz"
        const val SYSTEM = "SYSTEM"
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
    }
}