package com.localledger.app.ui.planning

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localledger.app.data.repository.LedgerRepository
import com.localledger.app.data.repository.PlanningRepository
import com.localledger.app.domain.*
import com.localledger.app.ui.common.displayMessage
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PlanningMonth(val month: YearMonth = YearMonth.now(), val budget: Budget? = null, val expense: Long = 0)
data class PlanningState(val month: PlanningMonth = PlanningMonth(), val rules: List<RecurringRule> = emptyList(),
    val categories: List<Category> = emptyList(), val accounts: List<Account> = emptyList(), val loading: Boolean = true, val error: String? = null)
data class RuleDraft(val id: String? = null, val name: String = "", val amount: String = "", val type: Int = EXPENSE,
    val categoryId: String? = null, val accountId: String? = null, val date: LocalDate = LocalDate.now(),
    val day: String = LocalDate.now().dayOfMonth.toString(), val note: String = "")

@OptIn(ExperimentalCoroutinesApi::class)
class PlanningViewModel(private val repository: PlanningRepository, private val ledger: LedgerRepository, private val savedState: SavedStateHandle) : ViewModel() {
    private val month = MutableStateFlow(savedState.get<String>("month")?.let(YearMonth::parse) ?: YearMonth.now())
    private val monthly = month.flatMapLatest { selected ->
        val (start, end) = monthBounds(selected)
        combine(repository.budget(selected.toString()), ledger.summary(start, end)) { budget, summary -> PlanningMonth(selected, budget, summary.expense) }
    }
    val state = combine(monthly, repository.rules, ledger.categories, ledger.accounts) { period, rules, categories, accounts ->
        PlanningState(period, rules, categories, accounts, loading = false)
    }.catch { emit(PlanningState(loading = false, error = it.displayMessage())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanningState())
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableMessage = MutableStateFlow<String?>(null)
    val message = mutableMessage.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    private val mutableDraft = MutableStateFlow(if (savedState.get<Boolean>("editorOpen") == true) RuleDraft(
        id = savedState["ruleId"], name = savedState["ruleName"] ?: "", amount = savedState["ruleAmount"] ?: "",
        type = savedState["ruleType"] ?: EXPENSE, categoryId = savedState["ruleCategory"], accountId = savedState["ruleAccount"],
        date = savedState.get<String>("ruleDate")?.let(LocalDate::parse) ?: LocalDate.now(), day = savedState["ruleDay"] ?: LocalDate.now().dayOfMonth.toString(),
        note = savedState["ruleNote"] ?: "",
    ) else null)
    val draft = mutableDraft.asStateFlow()

    fun changeMonth(delta: Long) { month.value = month.value.plusMonths(delta); savedState["month"] = month.value.toString() }
    fun openRule(rule: RecurringRule? = null) {
        if (mutableBusy.value) return
        mutableError.value = null
        val account = state.value.accounts.firstOrNull { !it.isDeleted && it.id == ledger.lastAccountId() }
            ?: state.value.accounts.firstOrNull { !it.isDeleted }
        mutableDraft.value = rule?.let { RuleDraft(it.id, it.name, formatAmount(it.amountMinor), it.type, it.categoryId, it.accountId,
            Instant.ofEpochMilli(it.nextDueAt).atZone(ZoneId.systemDefault()).toLocalDate(), it.dayOfMonth.toString(), it.note.orEmpty()) }
            ?: RuleDraft(accountId = account?.id)
        persistDraft()
    }
    fun changeDraft(block: (RuleDraft) -> RuleDraft) {
        if (mutableBusy.value) return
        mutableDraft.value = mutableDraft.value?.let(block)
        mutableError.value = null
        persistDraft()
    }
    fun closeEditor() { if (!mutableBusy.value) { mutableDraft.value = null; savedState["editorOpen"] = false; mutableError.value = null } }
    private fun persistDraft() {
        val d = mutableDraft.value ?: return
        savedState["editorOpen"] = true; savedState["ruleId"] = d.id; savedState["ruleName"] = d.name; savedState["ruleAmount"] = d.amount
        savedState["ruleType"] = d.type; savedState["ruleCategory"] = d.categoryId; savedState["ruleAccount"] = d.accountId
        savedState["ruleDate"] = d.date.toString(); savedState["ruleDay"] = d.day; savedState["ruleNote"] = d.note
    }
    fun saveRule() = action {
        val d = requireNotNull(mutableDraft.value)
        val amount = requireNotNull(parseAmount(d.amount)) { "请输入大于零的金额，最多两位小数。" }
        val day = requireNotNull(d.day.toIntOrNull()?.takeIf { it in 1..31 }) { "每月日期应在 1 至 31 日之间。" }
        repository.saveRule(d.id, d.name, amount, d.type, requireNotNull(d.categoryId) { "请选择分类。" },
            requireNotNull(d.accountId) { "请选择账户。" }, d.date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), day, d.note)
        mutableDraft.value = null; savedState["editorOpen"] = false
        mutableMessage.value = "固定账目已保存，到期后由你确认入账"
    }
    fun saveBudget(amount: String, onSaved: () -> Unit) {
        val selected = month.value.toString()
        action {
            repository.saveBudget(selected, requireNotNull(parseAmount(amount)) { "请输入大于零的月预算。" })
            mutableMessage.value = "月预算已保存"; onSaved()
        }
    }
    fun clearBudget() {
        val selected = month.value.toString()
        action { repository.clearBudget(selected); mutableMessage.value = "本月预算已移除" }
    }
    fun disable(rule: RecurringRule) = action { repository.disableRule(rule.id); mutableMessage.value = "固定账目已停用，已入账记录保留" }
    fun process(rule: RecurringRule, record: Boolean) = action {
        repository.processRule(rule.id, rule.nextDueAt, record)
        mutableMessage.value = if (record) "已记账，并推进至下个月" else "已跳过本期，并推进至下个月"
    }
    private fun action(block: suspend () -> Unit) {
        if (mutableBusy.value) return
        mutableBusy.value = true; mutableError.value = null; mutableMessage.value = null
        viewModelScope.launch {
            try { block() } catch (error: Exception) { mutableError.value = error.displayMessage() }
            finally { mutableBusy.value = false }
        }
    }
}
