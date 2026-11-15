package com.gdvalishvili.appinspector.data.logs

import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.ZoneId

/** One logcat line. time == null and level '?' = a line we could not parse (kept as plain text). */
data class LogLine(
    val time: Long?,
    val pid: Int,
    val tid: Int,
    val level: Char,     // V D I W E F (A = assert)
    val tag: String,
    val message: String,
    val raw: String,     // original text: used for saving, sharing and copying
) {
    val isError: Boolean get() = level == 'E' || level == 'F' || level == 'A'
}

/** Parser for "logcat -v threadtime -v year" (year optional). Pure Kotlin: unit-tested. */
object LogParser {

    private val THREADTIME = Regex(
        """^(?:(\d{4})-)?(\d{2})-(\d{2})\s+(\d{2}):(\d{2}):(\d{2})\.(\d{3})\s+(\d+)\s+(\d+)\s+([VDIWEFAS])\s(.*)$"""
    )

    fun parse(raw: String, zone: ZoneId, now: LocalDateTime): LogLine? {
        if (raw.isBlank() || raw.startsWith("---------")) return null // buffer headers
        val m = THREADTIME.matchEntire(raw) ?: return plain(raw)
        val g = m.groupValues

        var dt = try {
            LocalDateTime.of(
                if (g[1].isEmpty()) now.year else g[1].toInt(),
                g[2].toInt(), g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].toInt(),
                g[7].toInt() * 1_000_000,
            )
        } catch (e: DateTimeException) {
            return plain(raw)
        }
        // No year in the line and the date looks like the future: it was last year (new year's eve)
        if (g[1].isEmpty() && dt.isAfter(now.plusDays(1))) dt = dt.minusYears(1)

        val rest = g[11]
        val sep = rest.indexOf(": ")
        val (tag, message) = when {
            sep >= 0 -> rest.substring(0, sep).trim() to rest.substring(sep + 2)
            rest.trimEnd().endsWith(":") -> rest.trimEnd().dropLast(1).trim() to ""
            else -> "" to rest
        }
        return LogLine(
            time = dt.atZone(zone).toInstant().toEpochMilli(),
            pid = g[8].toInt(),
            tid = g[9].toInt(),
            level = g[10][0],
            tag = tag,
            message = message,
            raw = raw,
        )
    }

    private fun plain(raw: String) = LogLine(null, -1, -1, '?', "", raw, raw)
}

/** Filters of the Log tab. from/to null = no limit. Lines without time only pass when there is no time limit. */
fun filterLines(lines: List<LogLine>, errorsOnly: Boolean, query: String, from: Long?, to: Long?): List<LogLine> {
    val q = query.trim()
    return lines.filter { l ->
        (!errorsOnly || l.isError) &&
                (from == null || (l.time != null && l.time >= from)) &&
                (to == null || (l.time != null && l.time <= to)) &&
                (q.isEmpty() || l.tag.contains(q, ignoreCase = true) || l.message.contains(q, ignoreCase = true))
    }
}