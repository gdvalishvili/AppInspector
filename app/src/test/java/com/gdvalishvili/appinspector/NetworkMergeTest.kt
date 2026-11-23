package com.gdvalishvili.appinspector

import com.gdvalishvili.appinspector.data.background.NetworkMerge
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkMergeTest {

    @Test
    fun mergesUidsToPackages() {
        val rows = NetworkMerge.rows(
            day = "2026-11-23",
            bg = mapOf(10001 to 500L, 10002 to 0L, -4 to 999L),
            fg = mapOf(10001 to 100L, 1000 to 50L),
        ) { uid ->
            when (uid) {
                10001 -> listOf("com.a")
                1000 -> listOf("android", "com.android.settings") // shared system uid
                else -> emptyList()
            }
        }
        assertEquals(listOf("android", "com.a", "com.android.settings"), rows.map { it.packageName })
        val a = rows.first { it.packageName == "com.a" }
        assertEquals(500L, a.bgBytes)
        assertEquals(100L, a.fgBytes)
        assertEquals(50L, rows.first { it.packageName == "android" }.fgBytes)
    }
}