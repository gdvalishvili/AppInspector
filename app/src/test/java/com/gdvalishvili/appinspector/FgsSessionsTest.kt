package com.gdvalishvili.appinspector

import com.gdvalishvili.appinspector.data.background.FgsEvent
import com.gdvalishvili.appinspector.data.background.FgsEventKind.DEVICE_OFF
import com.gdvalishvili.appinspector.data.background.FgsEventKind.START
import com.gdvalishvili.appinspector.data.background.FgsEventKind.STOP
import com.gdvalishvili.appinspector.data.background.FgsSessions
import com.gdvalishvili.appinspector.data.background.FgsTypes
import com.gdvalishvili.appinspector.data.background.StandbyBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FgsSessionsTest {

    private val min = 60_000L
    private fun ev(t: Long, kind: com.gdvalishvili.appinspector.data.background.FgsEventKind, pkg: String = "a", cls: String? = "S") =
        FgsEvent(t * min, pkg, cls, kind)

    @Test
    fun startStopPair() {
        val s = FgsSessions.build(listOf(ev(10, START), ev(40, STOP)), queryStart = 0)
        assertEquals(1, s.size)
        assertEquals(30 * min, s[0].end!! - s[0].start)
    }

    @Test
    fun runningWithoutStop() {
        val s = FgsSessions.build(listOf(ev(10, START)), queryStart = 0)
        assertTrue(s.single().isRunning)
        val u = FgsSessions.summarize(s, from = 0, todayStart = 0, now = 70 * min).getValue("a")
        assertEquals(60 * min, u.totalMs)
        assertNotNull(u.running)
    }

    @Test
    fun stopWithoutStartBeganBeforeQuery() {
        val s = FgsSessions.build(listOf(ev(20, STOP)), queryStart = 5 * min)
        assertEquals(5 * min, s.single().start)
    }

    @Test
    fun duplicateStopIgnored() {
        val s = FgsSessions.build(listOf(ev(10, START), ev(20, STOP), ev(30, STOP)), queryStart = 0)
        assertEquals(1, s.size)
    }

    @Test
    fun shutdownClosesOpenSessions() {
        val s = FgsSessions.build(
            listOf(ev(10, START), ev(15, START, pkg = "b"), ev(50, DEVICE_OFF, pkg = "", cls = null)),
            queryStart = 0,
        )
        assertEquals(2, s.size)
        assertTrue(s.none { it.isRunning })
    }

    @Test
    fun windowClipsAndTodayCounts() {
        val s = FgsSessions.build(listOf(ev(0, START), ev(100, STOP), ev(200, START), ev(230, STOP)), queryStart = 0)
        val u = FgsSessions.summarize(s, from = 50 * min, todayStart = 150 * min, now = 300 * min).getValue("a")
        assertEquals((50 + 30) * min, u.totalMs) // 50..100 and 200..230
        assertEquals(30 * min, u.todayMs)
        assertEquals(1, u.starts)                // only the start at 200 is inside the window
        assertNull(u.running)
    }

    @Test
    fun bucketsAndTypes() {
        assertEquals(StandbyBucket.RARE, StandbyBucket.of(40))
        assertEquals(StandbyBucket.RESTRICTED, StandbyBucket.of(45))
        assertEquals(StandbyBucket.OTHER, StandbyBucket.of(5))
        assertEquals(listOf("dataSync", "location"), FgsTypes.names(1 or 8))
        assertEquals(listOf("specialUse"), FgsTypes.names(1 shl 30))
    }
}