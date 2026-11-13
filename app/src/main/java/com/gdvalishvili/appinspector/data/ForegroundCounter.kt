package com.gdvalishvili.appinspector.data

// UsageEvents.Event type values (stable; literals keep this file free of Android classes)
internal const val EVENT_RESUMED = 1                 // ACTIVITY_RESUMED / MOVE_TO_FOREGROUND
internal const val EVENT_PAUSED = 2                  // ACTIVITY_PAUSED / MOVE_TO_BACKGROUND
internal const val EVENT_SCREEN_NON_INTERACTIVE = 16 // screen off
internal const val EVENT_DEVICE_SHUTDOWN = 26

/** Minimal usage event: pure Kotlin, so the counting can be unit-tested on the computer. */
data class ForegroundEvent(
    val time: Long,
    val type: Int,
    val packageName: String?,
    val className: String?,
)

/**
 * Window-stack counting (verified against Digital Wellbeing):
 * open windows are kept as a stack; only the TOP window's app gets time.
 * When the top window closes, the one below continues. Screen off clears everything.
 */
object ForegroundCounter {

    fun count(events: List<ForegroundEvent>, start: Long, end: Long): Map<String, Long> {
        val totals = HashMap<String, Long>()
        val stack = ArrayList<Pair<String, String>>() // (package, activity), top = last
        var topPackage: String? = null
        var since = 0L

        fun updateTop(at: Long) {
            val newTop = stack.lastOrNull()?.first
            if (newTop == topPackage) return
            topPackage?.let { pkg ->
                val from = maxOf(since, start)
                val to = minOf(at, end)
                if (to > from) totals[pkg] = (totals[pkg] ?: 0L) + (to - from)
            }
            topPackage = newTop
            since = at
        }

        for (event in events) {
            when (event.type) {
                EVENT_SCREEN_NON_INTERACTIVE, EVENT_DEVICE_SHUTDOWN -> {
                    stack.clear()
                    updateTop(event.time)
                }
                EVENT_RESUMED -> {
                    val key = (event.packageName ?: continue) to event.className.orEmpty()
                    stack.remove(key) // move to the top if it was already open
                    stack.add(key)
                    updateTop(event.time)
                }
                EVENT_PAUSED -> {
                    val key = (event.packageName ?: continue) to event.className.orEmpty()
                    stack.remove(key)
                    updateTop(event.time)
                }
            }
        }
        stack.clear()
        updateTop(end) // close whatever is still open at the end of the window
        return totals
    }
}