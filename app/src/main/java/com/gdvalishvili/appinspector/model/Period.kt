package com.gdvalishvili.appinspector.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class PeriodType { DAY, WEEK, MONTH }

/** A calendar period from start to end (both inclusive). Week = Monday..Sunday. */
data class Period(val type: PeriodType, val start: LocalDate) {

    val end: LocalDate
        get() = when (type) {
            PeriodType.DAY -> start
            PeriodType.WEEK -> start.plusDays(6)
            PeriodType.MONTH -> start.plusMonths(1).minusDays(1)
        }

    val days: List<LocalDate>
        get() = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    fun previous(): Period = when (type) {
        PeriodType.DAY -> copy(start = start.minusDays(1))
        PeriodType.WEEK -> copy(start = start.minusWeeks(1))
        PeriodType.MONTH -> copy(start = start.minusMonths(1))
    }

    fun next(): Period = when (type) {
        PeriodType.DAY -> copy(start = start.plusDays(1))
        PeriodType.WEEK -> copy(start = start.plusWeeks(1))
        PeriodType.MONTH -> copy(start = start.plusMonths(1))
    }

    companion object {
        /** The period of the given type that contains [date]. */
        fun of(type: PeriodType, date: LocalDate): Period = when (type) {
            PeriodType.DAY -> Period(type, date)
            PeriodType.WEEK -> Period(type, date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
            PeriodType.MONTH -> Period(type, date.withDayOfMonth(1))
        }
    }
}