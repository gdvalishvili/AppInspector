package com.gdvalishvili.appinspector

import com.gdvalishvili.appinspector.model.Period
import com.gdvalishvili.appinspector.model.PeriodType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PeriodTest {

    private fun d(text: String) = LocalDate.parse(text)

    @Test
    fun weekStartsOnMonday() {
        val week = Period.of(PeriodType.WEEK, d("2026-11-13")) // Friday
        assertEquals(d("2026-11-09"), week.start)
        assertEquals(d("2026-11-15"), week.end)
        assertEquals(7, week.days.size)
    }

    @Test
    fun februaryLength() {
        assertEquals(d("2028-02-29"), Period.of(PeriodType.MONTH, d("2028-02-10")).end) // leap year
        assertEquals(d("2027-02-28"), Period.of(PeriodType.MONTH, d("2027-02-10")).end)
    }

    @Test
    fun navigationAcrossYearBoundary() {
        assertEquals(d("2026-12-31"), Period.of(PeriodType.DAY, d("2027-01-01")).previous().start)
        assertEquals(d("2026-12-28"), Period.of(PeriodType.WEEK, d("2027-01-01")).start)
        assertEquals(d("2027-01-01"), Period.of(PeriodType.MONTH, d("2026-12-15")).next().start)
    }

    @Test
    fun containsUsesInclusiveBounds() {
        val month = Period.of(PeriodType.MONTH, d("2026-11-13"))
        assertTrue(d("2026-11-01") in month)
        assertTrue(d("2026-11-30") in month)
        assertFalse(d("2026-12-01") in month)
        assertEquals(30, month.days.size)
    }
}