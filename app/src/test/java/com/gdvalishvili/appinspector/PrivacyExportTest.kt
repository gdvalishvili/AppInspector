package com.gdvalishvili.appinspector

import com.gdvalishvili.appinspector.data.privacy.ExportRow
import com.gdvalishvili.appinspector.data.privacy.PrivacyExport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyExportTest {

    @Test
    fun headerAndRow() {
        val csv = PrivacyExport.csv(
            listOf(ExportRow("ჩემი აპი", "com.a", "მაღალი", 7, listOf("AdMob", "Facebook Ads"), listOf("Advertisement"), listOf("RECORD_AUDIO"), 120,900, "23.11.2026 23:40"))
        )
        assertTrue(csv.startsWith("\uFEFFapp;package;level;score;"))
        val line = csv.lines()[1]
        assertEquals("ჩემი აპი;com.a;მაღალი;7;AdMob, Facebook Ads;Advertisement;RECORD_AUDIO;120;900;23.11.2026 23:40", line.trimEnd('\r'))
    }

    @Test
    fun escaping() {
        assertEquals("plain", PrivacyExport.escape("plain"))
        assertEquals("\"a;b\"", PrivacyExport.escape("a;b"))
        assertEquals("\"say \"\"hi\"\"\"", PrivacyExport.escape("say \"hi\""))
    }
}