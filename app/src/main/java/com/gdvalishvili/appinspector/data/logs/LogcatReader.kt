package com.gdvalishvili.appinspector.data.logs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId
import android.os.Process as AndroidProcess

/** Runs "logcat -d" (dump the current buffers, then exit). No background work. */
class LogcatReader {

    data class Result(val lines: List<LogLine>, val ownOnly: Boolean, val error: String?)

    /** uid != null: only that app's lines (logcat filters them itself). */
    suspend fun read(uid: Int?): Result = withContext(Dispatchers.IO) {
        val command = mutableListOf("logcat", "-d", "-b", "main,system,crash", "-v", "threadtime", "-v", "year")
        if (uid != null) command += "--uid=$uid"
        try {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val zone = ZoneId.systemDefault()
            val now = LocalDateTime.now()
            val buffer = ArrayDeque<LogLine>()
            process.inputStream.bufferedReader().useLines { seq ->
                for (raw in seq) {
                    val line = LogParser.parse(raw, zone, now) ?: continue
                    buffer.addLast(line)
                    if (buffer.size > MAX_LINES) buffer.removeFirst()
                }
            }
            process.waitFor()
            // Access denied (or not granted): logcat silently returns only our own lines
            val myPid = AndroidProcess.myPid()
            val parsed = buffer.filter { it.pid >= 0 }
            val ownOnly = uid == null && parsed.isNotEmpty() && parsed.all { it.pid == myPid }
            Result(buffer.toList(), ownOnly, null)
        } catch (e: Exception) {
            Result(emptyList(), false, e.message ?: e.javaClass.simpleName)
        }
    }

    private companion object {
        const val MAX_LINES = 50_000
    }
}