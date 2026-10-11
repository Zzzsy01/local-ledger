package com.localledger.app.ui.backup

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localledger.app.data.repository.LedgerRepository
import com.localledger.app.domain.*
import com.localledger.app.ui.common.displayMessage
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class FinanceExportState(
    val range: ReportRange = ReportRange(YearMonth.now().atDay(1), LocalDate.now()),
    val fullRange: ReportRange = range, val count: Int = 0, val summary: MonthlySummary = MonthlySummary(),
    val loading: Boolean = true, val busy: Boolean = false, val error: String? = null, val notice: String? = null,
)

class FinanceExportViewModel(private val repository: LedgerRepository, private val savedState: SavedStateHandle) : ViewModel() {
    private val selected = savedState.getStateFlow("financeRange", "${YearMonth.now().atDay(1)}/${LocalDate.now()}")
    private val operation = MutableStateFlow(FinanceExportState())
    private fun range(value: String): ReportRange = value.split('/').let { ReportRange(LocalDate.parse(it[0]), LocalDate.parse(it[1])) }
    val state = combine(selected, repository.entries(0, Long.MAX_VALUE), operation) { value, all, status ->
        val range = range(value)
        val zone = ZoneId.systemDefault()
        val (from, end) = range.bounds(zone)
        val entries = all.filter { it.transaction.occurredAt >= from && it.transaction.occurredAt < end }
        val dates = all.map { Instant.ofEpochMilli(it.transaction.occurredAt).atZone(zone).toLocalDate() }
        status.copy(range = range, fullRange = ReportRange(dates.minOrNull() ?: YearMonth.now().atDay(1), maxOf(dates.maxOrNull() ?: LocalDate.now(), LocalDate.now())),
            count = entries.size, summary = MonthlySummary(entries.filter { it.transaction.type == INCOME }.sumOf { it.transaction.amountMinor },
                entries.filter { it.transaction.type == EXPENSE }.sumOf { it.transaction.amountMinor }), loading = false)
    }.catch { emit(FinanceExportState(loading = false, error = it.displayMessage())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FinanceExportState())

    fun select(range: ReportRange) { savedState["financeRange"] = "${range.from}/${range.through}"; operation.value = FinanceExportState() }
    fun export(uri: Uri, csv: Boolean) {
        if (operation.value.busy) return
        val range = range(selected.value)
        operation.value = FinanceExportState(busy = true)
        viewModelScope.launch {
            try {
                repository.exportFinance(uri, range, csv)
                operation.value = FinanceExportState(notice = "${if (csv) "流水 CSV" else "财务分析 JSON"} 已保存。")
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { operation.value = FinanceExportState(error = error.displayMessage()) }
            finally { operation.update { it.copy(busy = false) } }
        }
    }
}
