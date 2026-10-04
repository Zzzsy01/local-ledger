package com.localledger.app.ui.add

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localledger.app.data.repository.LedgerRepository
import com.localledger.app.domain.Account
import com.localledger.app.domain.Category
import com.localledger.app.domain.EXPENSE
import com.localledger.app.domain.formatAmount
import com.localledger.app.domain.parseAmount
import com.localledger.app.ui.common.displayMessage
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EntryDraft(
    val amount: String = "",
    val type: Int = EXPENSE,
    val categoryId: String? = null,
    val accountId: String? = null,
    val note: String = "",
    val date: LocalDate = LocalDate.now(),
    val originalOccurredAt: Long? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
)
data class EntryState(
    val draft: EntryDraft = EntryDraft(),
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
)

class EntryViewModel(
    private val repository: LedgerRepository,
    private val savedState: SavedStateHandle,
    private val id: String?,
    private val copyFromId: String? = null,
) : ViewModel() {
    val copying: Boolean = copyFromId != null
    private val restored = savedState.get<Boolean>("loaded") == true
    private val draft = MutableStateFlow(EntryDraft(
        amount = savedState["amount"] ?: "",
        type = savedState["type"] ?: EXPENSE,
        categoryId = savedState["categoryId"],
        accountId = savedState["accountId"],
        note = savedState["note"] ?: "",
        date = savedState.get<String>("date")?.let(LocalDate::parse) ?: LocalDate.now(),
        originalOccurredAt = savedState["originalOccurredAt"],
        loading = (id != null || copyFromId != null) && !restored,
    ))
    val state = combine(draft, repository.categories, repository.accounts, ::EntryState)
        .catch { emit(EntryState(draft.value.copy(error = it.displayMessage(), loading = false))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EntryState(draft.value))

    init {
        viewModelScope.launch {
            val sourceId = id ?: copyFromId
            if (sourceId != null && !restored) {
                try {
                    val entry = requireNotNull(repository.transaction(sourceId)) { "这笔账目已不存在" }
                    require(!entry.isDeleted) { "这笔账目已删除" }
                    val categoryId = if (copying) repository.categories.first().find { it.id == entry.categoryId && !it.isDeleted }?.id else entry.categoryId
                    val accountId = if (copying) repository.accounts.first().find { it.id == entry.accountId && !it.isDeleted }?.id else entry.accountId
                    change { it.copy(
                        amount = formatAmount(entry.amountMinor), type = entry.type,
                        categoryId = categoryId, accountId = accountId,
                        note = entry.note.orEmpty(),
                        date = if (copying) LocalDate.now() else Instant.ofEpochMilli(entry.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate(),
                        originalOccurredAt = if (copying) null else entry.occurredAt, loading = false,
                    ) }
                    savedState["loaded"] = true
                } catch (error: Exception) {
                    draft.update { it.copy(loading = false, error = error.displayMessage()) }
                }
            }
            repository.accounts.catch { error ->
                draft.update { it.copy(error = error.displayMessage()) }
            }.collect { accounts ->
                if (draft.value.accountId == null && !copying) {
                    val active = accounts.filterNot { it.isDeleted }
                    val previous = active.find { it.id == repository.lastAccountId() } ?: active.firstOrNull()
                    previous?.let { selected -> change { it.copy(accountId = selected.id) } }
                }
            }
        }
    }

    private fun change(block: (EntryDraft) -> EntryDraft) {
        draft.update { current -> block(current).copy(error = null) }
        val value = draft.value
        savedState["amount"] = value.amount
        savedState["type"] = value.type
        savedState["categoryId"] = value.categoryId
        savedState["accountId"] = value.accountId
        savedState["note"] = value.note
        savedState["date"] = value.date.toString()
        savedState["originalOccurredAt"] = value.originalOccurredAt
    }

    fun setAmount(value: String) { change { it.copy(amount = value) } }
    fun setType(value: Int) { if (value != draft.value.type) change { it.copy(type = value, categoryId = null) } }
    fun setCategory(value: String) { change { it.copy(categoryId = value) } }
    fun setAccount(value: String) { change { it.copy(accountId = value) } }
    fun setNote(value: String) { change { it.copy(note = value) } }
    fun setDate(value: LocalDate) { change { it.copy(date = value) } }

    fun save() {
        val value = draft.value
        if (value.saving || value.loading || value.saved) return
        val amount = parseAmount(value.amount)
        val problem = when {
            amount == null || amount <= 0 -> "请输入大于 0 的金额，最多两位小数"
            value.categoryId == null -> "请选择分类"
            value.accountId == null -> "请选择账户"
            else -> null
        }
        if (problem != null) { draft.update { it.copy(error = problem) }; return }
        draft.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val zone = ZoneId.systemDefault()
                val previous = value.originalOccurredAt?.let { Instant.ofEpochMilli(it).atZone(zone) }
                val occurredAt = if (previous?.toLocalDate() == value.date) value.originalOccurredAt!! else
                    value.date.atTime(previous?.toLocalTime() ?: LocalTime.now()).atZone(zone).toInstant().toEpochMilli()
                repository.saveTransaction(id, amount!!, value.type, value.categoryId!!, value.accountId!!, value.note.trim().ifEmpty { null }, occurredAt)
                draft.update { it.copy(saving = false, saved = true) }
            } catch (error: Exception) { draft.update { it.copy(saving = false, error = error.displayMessage()) } }
        }
    }
}
