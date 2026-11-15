package com.gdvalishvili.appinspector.ui.report

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gdvalishvili.appinspector.data.Report
import com.gdvalishvili.appinspector.data.ReportRepository
import com.gdvalishvili.appinspector.data.UsageCollector
import com.gdvalishvili.appinspector.model.Period
import com.gdvalishvili.appinspector.model.PeriodType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

enum class ReportSort { TIME, NAME }

data class ReportUiState(
    val period: Period,
    val today: LocalDate,
    val report: Report? = null,
    val loading: Boolean = true,
    val sort: ReportSort = ReportSort.TIME,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
)

class ReportViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ReportRepository(application)
    private val collector = UsageCollector(application)
    private val zone: ZoneId = ZoneId.systemDefault()
    private var collected = false
    private var loadJob: Job? = null

    private val _state = MutableStateFlow(
        LocalDate.now(zone).let { today ->
            ReportUiState(period = Period.of(PeriodType.DAY, today), today = today)
        }
    )
    val state: StateFlow<ReportUiState> = _state

    fun refresh() = load()

    /** Opens a specific period (used by arrows, calendar, chart and later by notifications). */
    fun open(period: Period) {
        _state.update { it.copy(period = period) }
        load()
    }

    /** Day/Week/Month switch: keeps "today" if it is in the current period. */
    fun setType(type: PeriodType) {
        val s = _state.value
        val anchor = if (s.today in s.period) s.today else s.period.start
        open(Period.of(type, anchor))
    }

    fun previous() = open(_state.value.period.previous())

    fun next() = open(_state.value.period.next())

    fun pickDate(date: LocalDate) = open(Period.of(_state.value.period.type, date))

    fun openDay(date: LocalDate) = open(Period.of(PeriodType.DAY, date))

    fun setSort(value: ReportSort) = _state.update { it.copy(sort = value) }

    private fun load() {
        loadJob?.cancel()
        val period = _state.value.period
        _state.update { it.copy(loading = true) }
        loadJob = viewModelScope.launch {
            val report = withContext(Dispatchers.IO) {
                // Make sure finished days are stored before the first report
                if (!collected) {
                    collector.collectMissingDays(zone)
                    collected = true
                }
                repository.load(period, zone)
            }
            val today = LocalDate.now(zone)
            _state.update {
                it.copy(
                    report = report,
                    today = today,
                    loading = false,
                    canGoBack = period.start.isAfter(report.firstDataDay),
                    canGoForward = period.end.isBefore(today),
                )
            }
        }
    }
}