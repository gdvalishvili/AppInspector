package com.gdvalishvili.appinspector

import com.gdvalishvili.appinspector.data.EVENT_PAUSED
import com.gdvalishvili.appinspector.data.EVENT_RESUMED
import com.gdvalishvili.appinspector.data.EVENT_SCREEN_NON_INTERACTIVE
import com.gdvalishvili.appinspector.data.ForegroundCounter
import com.gdvalishvili.appinspector.data.ForegroundEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundCounterTest {

    private fun resumed(t: Long, pkg: String, cls: String = "Main") = ForegroundEvent(t, EVENT_RESUMED, pkg, cls)
    private fun paused(t: Long, pkg: String, cls: String = "Main") = ForegroundEvent(t, EVENT_PAUSED, pkg, cls)
    private fun screenOff(t: Long) = ForegroundEvent(t, EVENT_SCREEN_NON_INTERACTIVE, "android", null)

    private fun count(vararg events: ForegroundEvent, start: Long = 0L, end: Long = 10_000L) =
        ForegroundCounter.count(events.toList(), start, end)

    @Test
    fun simpleSession() {
        assertEquals(mapOf("a" to 300L), count(resumed(100, "a"), paused(400, "a")))
    }

    @Test
    fun switchingApps() {
        val result = count(resumed(100, "a"), paused(200, "a"), resumed(200, "b"), paused(500, "b"))
        assertEquals(100L, result["a"])
        assertEquals(300L, result["b"])
    }

    @Test
    fun stuckSessionDoesNotCountWhileOtherAppsAreOnTop() {
        // Facebook never sends PAUSED; the user goes to Lichess, then home, then screen off
        val result = count(
            resumed(100, "facebook"),
            resumed(200, "lichess"),
            paused(400, "lichess"), resumed(400, "launcher"),
            screenOff(500), paused(500, "launcher"),
        )
        assertEquals(100L, result["facebook"])
        assertEquals(200L, result["lichess"])
        assertEquals(100L, result["launcher"])
    }

    @Test
    fun smallOverlayReturnsTimeToTheAppBelow() {
        // A dialog from another package covers Lichess for a moment; Lichess is not paused
        val result = count(
            resumed(100, "lichess"),
            resumed(200, "dialog", "Popup"), paused(250, "dialog", "Popup"),
            paused(500, "lichess"),
        )
        assertEquals(350L, result["lichess"])
        assertEquals(50L, result["dialog"])
    }

    @Test
    fun screenOffStopsCounting() {
        val result = count(resumed(100, "a"), screenOff(300), resumed(400, "a"), paused(500, "a"))
        assertEquals(300L, result["a"])
    }

    @Test
    fun sessionIsClippedToTheDayWindow() {
        // Opened before midnight (start = 1000), closed after
        assertEquals(mapOf("a" to 500L), count(resumed(500, "a"), paused(1_500, "a"), start = 1_000L, end = 2_000L))
        // Still open at the end of the window
        assertEquals(mapOf("b" to 500L), count(resumed(1_500, "b"), start = 1_000L, end = 2_000L))
    }
}