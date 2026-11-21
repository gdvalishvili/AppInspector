package com.gdvalishvili.appinspector

import com.gdvalishvili.appinspector.data.privacy.ReasonKind
import com.gdvalishvili.appinspector.data.privacy.RiskInput
import com.gdvalishvili.appinspector.data.privacy.RiskLevel
import com.gdvalishvili.appinspector.data.privacy.RiskRules
import com.gdvalishvili.appinspector.data.privacy.TrackerDef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskRulesTest {

    private fun tracker(name: String, vararg cats: String) = TrackerDef(name.hashCode(), name, cats.toList(), emptyList(), "")
    private fun input(
        trackers: List<TrackerDef> = emptyList(),
        perms: Set<String> = emptySet(),
        bg: Long = 0L,
        fgs: List<String> = emptyList(),
    ) = RiskInput(trackers, perms, bg, fgs)

    @Test
    fun cleanAppIsLow() {
        val r = RiskRules.evaluate(input())
        assertEquals(RiskLevel.LOW, r.level)
        assertTrue(r.reasons.isEmpty())
    }

    @Test
    fun adsAndMicrophoneAreHigh() {
        val r = RiskRules.evaluate(
            input(
                trackers = listOf(tracker("AdA", "Advertisement"), tracker("AdB", "Advertisement", "Analytics")),
                perms = setOf("android.permission.RECORD_AUDIO"),
            )
        )
        assertEquals(6, r.score)
        assertEquals(RiskLevel.HIGH, r.level)
        assertEquals("AdA, AdB", r.reasons.first { it.kind == ReasonKind.TRACKER_ADS }.detail)
    }

    @Test
    fun mediumThreshold() {
        val analyticsAndCamera = input(
            trackers = listOf(tracker("Stats", "Analytics")),
            perms = setOf("android.permission.CAMERA"),
        )
        assertEquals(RiskLevel.LOW, RiskRules.evaluate(analyticsAndCamera).level) // 2 points
        val plusContacts = analyticsAndCamera.copy(grantedPermissions = analyticsAndCamera.grantedPermissions + "android.permission.READ_CONTACTS")
        assertEquals(RiskLevel.MEDIUM, RiskRules.evaluate(plusContacts).level)   // 3 points
    }

    @Test
    fun crashReportingCostsNothing() {
        val r = RiskRules.evaluate(input(trackers = listOf(tracker("Crashy", "Crash reporting"))))
        assertEquals(0, r.score)
        assertTrue(r.reasons.isEmpty())
    }

    @Test
    fun locationCountedOnce() {
        val r = RiskRules.evaluate(
            input(perms = setOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"))
        )
        assertEquals(2, r.score)
        val coarse = RiskRules.evaluate(input(perms = setOf("android.permission.ACCESS_COARSE_LOCATION")))
        assertEquals(1, coarse.score)
    }

    @Test
    fun backgroundDataAndServices() {
        val r = RiskRules.evaluate(input(bg = 60L * 1024 * 1024, fgs = listOf("dataSync", "location")))
        assertEquals(2, r.score)
        assertEquals("location", r.reasons.first { it.kind == ReasonKind.FGS_SENSITIVE }.detail)
        assertEquals(0, RiskRules.evaluate(input(bg = 10L * 1024 * 1024, fgs = listOf("dataSync"))).score)
    }
}