package com.localledger.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localledger.app.data.repository.LedgerRepository
import com.localledger.app.domain.Account
import com.localledger.app.domain.Category
import com.localledger.app.domain.INCOME
import com.localledger.app.domain.EXPENSE
import com.localledger.app.domain.Budget
import com.localledger.app.domain.LedgerEntry
import com.localledger.app.domain.LedgerFilter
import com.localledger.app.domain.MonthlySummary
import com.localledger.app.domain.monthBounds
import com.localledger.app.domain.parseAmount
import com.localledger.app.ui.common.displayMessage
import java.time.YearMonth
import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchDraft(
    val open: Boolean = false, val keyword: String = "", val allDates: Boolean = true,
    val type: Int? = null, val categoryId: String? = null, val accountId: String? = null,
    val minimum: String = "", val maximum: String = "",
    val fromDate: LocalDate? = null, val throughDate: LocalDate? = null,
    val reimbursement: Int? = null,
)
data class HomeState(
    val month: YearMonth = YearMonth.now(),
    val summary: MonthlySummary = MonthlySummary(0, 0),
    val entries: List<LedgerEntry> = emptyList(),
    val search: SearchDraft = SearchDraft(),
    val categories: List<Category> = emptyList(), val accounts: List<Account> = emptyList(),
    val loading: Boolean = true, val error: String? = null,
    val selectedDay: LocalDate? = null,
    val calendarEntries: List<LedgerEntry> = emptyList(),
    val monthSummary: MonthlySummary = MonthlySummary(),
    val budget: Budget? = null,
    val quickEntries: List<LedgerEntry> = emptyList(),
)
data class HomeMessage(val text: String, val undoId: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val repository: LedgerRepository) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())
    private val zone = MutableStateFlow(ZoneId.systemDefault())
    private val search = MutableStateFlow(SearchDraft())
    private val selectedDay = MutableStateFlow<LocalDate?>(null)
    private val mutableMessages = MutableSharedFlow<HomeMessage>()
    val messages = mutableMessages.asSharedFlow()
    private val selection = combine(month, zone, search, repository.categories, repository.accounts) { selected, currentZone, filter, categories, accounts ->
        Triple(HomeState(month = selected, search = filter, categories = categories, accounts = accounts), currentZone, filter)
    }
    val state = combine(selection, selectedDay) { selection, day -> selection.copy(first = selection.first.copy(selectedDay = day)) }
        .flatMapLatest { (initial, currentZone, draft) ->
        flow {
            emit(initial)
            val (baseStart, baseEnd) = if (draft.open && draft.allDates) 0L to Long.MAX_VALUE else monthBounds(initial.month, currentZone)
            val start = if (draft.open) draft.fromDate?.atStartOfDay(currentZone)?.toInstant()?.toEpochMilli() ?: baseStart else baseStart
            val end = if (draft.open) draft.throughDate?.plusDays(1)?.atStartOfDay(currentZone)?.toInstant()?.toEpochMilli() ?: baseEnd else baseEnd
            val minimum = draft.minimum.takeIf { draft.open && it.isNotBlank() }?.let {
                requireNotNull(parseAmount(it, allowZero = true)) { "最低金额格式无效，最多两位小数" }
            }
            val maximum = draft.maximum.takeIf { draft.open && it.isNotBlank() }?.let {
                requireNotNull(parseAmount(it, allowZero = true)) { "最高金额格式无效，最多两位小数" }
            }
            val filter = LedgerFilter(start, end, if (draft.open) draft.keyword else "",
                draft.type.takeIf { draft.open }, draft.categoryId.takeIf { draft.open },
                draft.accountId.takeIf { draft.open }, minimum, maximum, draft.reimbursement.takeIf { draft.open })
            val (monthStart, monthEnd) = monthBounds(initial.month, currentZone)
            emitAll(combine(repository.filteredEntries(filter), repository.entries(monthStart, monthEnd),
                repository.budget(initial.month.toString()), repository.entries(0, Long.MAX_VALUE)) { filtered, monthly, budget, recent ->
                fun summary(entries: List<LedgerEntry>) = MonthlySummary(
                    entries.filter { it.transaction.type == INCOME }.sumOf { it.transaction.amountMinor },
                    entries.filter { it.transaction.type == EXPENSE }.sumOf { it.transaction.amountMinor })
                val entries = if (!draft.open && initial.selectedDay != null) filtered.filter {
                    Instant.ofEpochMilli(it.transaction.occurredAt).atZone(currentZone).toLocalDate() == initial.selectedDay
                } else filtered
                val common = recent.filter { !it.transaction.isDeleted }.groupBy { it.transaction.categoryId }
                    .values.sortedByDescending { it.size }.mapNotNull { rows ->
                        rows.firstOrNull { row -> initial.accounts.any { it.id == row.transaction.accountId && !it.isDeleted } &&
                            initial.categories.any { it.id == row.transaction.categoryId && !it.isDeleted } }
                    }.take(4)
                initial.copy(summary = if (draft.open) summary(filtered) else summary(monthly), entries = entries,
                    calendarEntries = monthly, monthSummary = summary(monthly), budget = budget, quickEntries = common, loading = false)
            })
        }.catch { emit(initial.copy(loading = false, error = it.displayMessage())) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun changeMonth(offset: Long) { selectedDay.value = null; month.update { it.plusMonths(offset) } }
    fun selectDay(value: LocalDate?) { selectedDay.value = if (value == selectedDay.value) null else value }
    fun refreshTimeZone() { zone.value = ZoneId.systemDefault() }
    fun toggleSearch() { selectedDay.value = null; search.update { if (it.open) SearchDraft() else it.copy(open = true) } }
    fun setKeyword(value: String) { search.update { it.copy(keyword = value) } }
    fun setAllDates(value: Boolean) { search.update { it.copy(allDates = value, fromDate = null, throughDate = null) } }
    fun setFromDate(value: LocalDate) { search.update { it.copy(allDates = true, fromDate = value) } }
    fun setThroughDate(value: LocalDate) { search.update { it.copy(allDates = true, throughDate = value) } }
    fun setType(value: Int?) { search.update { it.copy(type = value, categoryId = null) } }
    fun setCategory(value: String?) { search.update { it.copy(categoryId = value) } }
    fun setAccount(value: String?) { search.update { it.copy(accountId = value) } }
    fun setMinimum(value: String) { search.update { it.copy(minimum = value) } }
    fun setMaximum(value: String) { search.update { it.copy(maximum = value) } }
    fun setReimbursement(value: Int?) { search.update { it.copy(reimbursement = value) } }
    fun showReimbursements() { search.value = SearchDraft(open = true, reimbursement = 0) }
    fun clearFilters() { search.value = SearchDraft(open = true) }

    fun delete(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteTransaction(id)
                mutableMessages.emit(HomeMessage("账目已删除", id))
            } catch (error: Exception) { mutableMessages.emit(HomeMessage(error.displayMessage())) }
        }
    }
    fun undo(id: String) {
        viewModelScope.launch {
            try { repository.undoDeleteTransaction(id); mutableMessages.emit(HomeMessage("已恢复账目")) }
            catch (error: Exception) { mutableMessages.emit(HomeMessage(error.displayMessage())) }
        }
    }
}
