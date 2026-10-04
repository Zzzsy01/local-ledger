package com.localledger.app.ui.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localledger.app.data.repository.LedgerRepository
import com.localledger.app.domain.*
import com.localledger.app.ui.common.displayMessage
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

data class MonthTotal(val month: YearMonth, val summary: MonthlySummary)
data class StatsState(
    val period: ReportPeriod = ReportPeriod.MONTH,
    val range: ReportRange = ReportRange(YearMonth.now().atDay(1), YearMonth.now().atEndOfMonth()),
    val type: Int = EXPENSE,
    val summary: MonthlySummary = MonthlySummary(),
    val categories: List<CategoryTotal> = emptyList(), val accounts: List<AccountTotal> = emptyList(),
    val metrics: ReportMetrics = ReportMetrics(),
    val previous: MonthlySummary = MonthlySummary(), val yearAgo: MonthlySummary = MonthlySummary(),
    val trend: List<MonthTotal> = emptyList(), val points: List<ReportPoint> = emptyList(),
    val ranking: List<LedgerEntry> = emptyList(), val loading: Boolean = true, val error: String? = null,
) {
    val monthlyPoints: Boolean get() = period == ReportPeriod.YEAR || range.days > 62
    val previousLabel: String get() = when (period) {
        ReportPeriod.WEEK -> "上周"; ReportPeriod.MONTH -> "上月"; ReportPeriod.YEAR -> "上年"; ReportPeriod.CUSTOM -> "前一等长区间"
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(repository: LedgerRepository, private val savedState: SavedStateHandle) : ViewModel() {
    private data class Selection(val period: ReportPeriod, val anchor: LocalDate, val custom: ReportRange)
    private val selection = MutableStateFlow(Selection(
        ReportPeriod.valueOf(savedState["period"] ?: ReportPeriod.MONTH.name),
        LocalDate.ofEpochDay(savedState["anchor"] ?: LocalDate.now().toEpochDay()),
        ReportRange(LocalDate.ofEpochDay(savedState["from"] ?: YearMonth.now().atDay(1).toEpochDay()),
            LocalDate.ofEpochDay(savedState["through"] ?: LocalDate.now().toEpochDay())),
    ))
    private val type = savedState.getStateFlow("type", EXPENSE)
    private val zone = MutableStateFlow(ZoneId.systemDefault())
    val state = combine(selection, type, zone) { selected, kind, currentZone ->
        StatsState(period = selected.period, type = kind, range = reportRange(selected.period, selected.anchor, selected.custom)) to currentZone
    }
        .flatMapLatest { (initial, currentZone) ->
            val range = initial.range
            val (start, end) = range.bounds(currentZone)
            val (previousStart, previousEnd) = previousReportRange(initial.period, range).bounds(currentZone)
            val yearAgo = if (initial.period in listOf(ReportPeriod.MONTH, ReportPeriod.YEAR)) {
                val (from, to) = reportRange(initial.period, range.from.minusYears(1), range).bounds(currentZone)
                repository.summary(from, to)
            } else flowOf(MonthlySummary())
            val lastMonth = YearMonth.from(range.through)
            val months = if (initial.period == ReportPeriod.YEAR) (1..12).map { YearMonth.of(range.from.year, it) }
                else (5L downTo 0L).map { lastMonth.minusMonths(it) }
            val trend = combine(months.map { value ->
                val (from, to) = monthBounds(value, currentZone)
                repository.summary(from, to)
            }) { totals -> months.mapIndexed { index, value -> MonthTotal(value, totals[index]) } }
            val report = combine(repository.summary(start, end), repository.categoryTotals(start, end, initial.type),
                repository.accountTotals(start, end, initial.type), repository.reportMetrics(start, end)) { summary, categories, accounts, metrics ->
                initial.copy(summary = summary, categories = categories, accounts = accounts, metrics = metrics)
            }
            combine(report, repository.summary(previousStart, previousEnd), yearAgo, trend, repository.entries(start, end)) {
                    data, previous, priorYear, totals, entries ->
                data.copy(previous = previous, yearAgo = priorYear, trend = totals,
                    points = reportPoints(entries, range, currentZone, initial.monthlyPoints),
                    ranking = entries.filter { it.transaction.type == initial.type }
                        .sortedWith(compareByDescending<LedgerEntry> { it.transaction.amountMinor }
                            .thenByDescending { it.transaction.occurredAt }.thenBy { it.transaction.id }).take(10),
                    loading = false)
            }.onStart { emit(initial) }.catch { emit(initial.copy(loading = false, error = it.displayMessage())) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsState())

    fun changePeriod(offset: Long) {
        val current = selection.value
        select(current.copy(anchor = when (current.period) {
            ReportPeriod.WEEK -> current.anchor.plusWeeks(offset)
            ReportPeriod.YEAR -> current.anchor.plusYears(offset)
            else -> current.anchor.plusMonths(offset)
        }))
    }
    fun setPeriod(value: ReportPeriod) { select(selection.value.copy(period = value)) }
    fun setCustomRange(from: LocalDate, through: LocalDate) {
        val range = ReportRange(from, through)
        require(!range.from.isBefore(LocalDate.of(1970, 1, 1))) { "请选择 1970 年以后的日期。" }
        select(selection.value.copy(period = ReportPeriod.CUSTOM, anchor = through, custom = range))
    }
    private fun select(value: Selection) {
        savedState["period"] = value.period.name
        savedState["anchor"] = value.anchor.toEpochDay()
        savedState["from"] = value.custom.from.toEpochDay()
        savedState["through"] = value.custom.through.toEpochDay()
        selection.value = value
    }
    fun setType(value: Int) { require(value == EXPENSE || value == INCOME); savedState["type"] = value }
    fun refreshTimeZone() { zone.value = ZoneId.systemDefault() }
}
