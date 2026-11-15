package com.gdvalishvili.appinspector

import com.gdvalishvili.appinspector.data.logs.LogParser
import com.gdvalishvili.appinspector.data.logs.filterLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class LogParserTest {

    private val zone = ZoneId.of("Asia/Tbilisi")
    private val now = LocalDateTime.of(2026, 11, 15, 14, 45)

    private fun epoch(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int, ms: Int) =
        LocalDateTime.of(y, mo, d, h, mi, s, ms * 1_000_000).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun threadtimeWithYear() {
        val l = LogParser.parse("2026-11-15 14:30:01.123  1234  1250 E ActivityManager: ANR in com.foo", zone, now)!!
        assertEquals(epoch(2026, 11, 15, 14, 30, 1, 123), l.time)
        assertEquals(1234, l.pid)
        assertEquals(1250, l.tid)
        assertEquals('E', l.level)
        assertEquals("ActivityManager", l.tag)
        assertEquals("ANR in com.foo", l.message)
    }

    @Test
    fun withoutYearAndPaddedTag() {
        val l = LogParser.parse("11-12 10:18:52.970  1281  1281 F libc    : Fatal signal 6", zone, now)!!
        assertEquals(epoch(2026, 11, 12, 10, 18, 52, 970), l.time)
        assertEquals("libc", l.tag)
        assertEquals("Fatal signal 6", l.message)
    }

    @Test
    fun newYearUsesPreviousYear() {
        val jan = LocalDateTime.of(2027, 1, 1, 0, 10)
        val l = LogParser.parse("12-31 23:59:00.000     1     1 I Tag: x", zone, jan)!!
        assertEquals(epoch(2026, 12, 31, 23, 59, 0, 0), l.time)
    }

    @Test
    fun headersSkippedAndUnknownLinesKept() {
        assertNull(LogParser.parse("--------- beginning of main", zone, now))
        val raw = LogParser.parse("hello world", zone, now)!!
        assertNull(raw.time)
        assertEquals('?', raw.level)
        assertEquals("hello world", raw.message)
    }

    @Test
    fun emptyMessage() {
        val l = LogParser.parse("2026-11-15 14:30:01.123    10    11 W Tag:", zone, now)!!
        assertEquals("Tag", l.tag)
        assertEquals("", l.message)
    }

    @Test
    fun filters() {
        val lines = listOf(
            "2026-11-15 14:00:00.000  1  1 I Chrome: page loaded",
            "2026-11-15 14:10:00.000  1  1 E Chrome: crash here",
            "2026-11-15 14:20:00.000  1  1 W System: low memory",
        ).map { LogParser.parse(it, zone, now)!! }
        assertEquals(1, filterLines(lines, errorsOnly = true, query = "", from = null, to = null).size)
        assertEquals(2, filterLines(lines, errorsOnly = false, query = "chrome", from = null, to = null).size)
        val from = epoch(2026, 11, 15, 14, 5, 0, 0)
        val to = epoch(2026, 11, 15, 14, 15, 0, 0)
        assertEquals(1, filterLines(lines, errorsOnly = false, query = "", from = from, to = to).size)
    }
}