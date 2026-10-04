package com.localledger.app.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localledger.app.data.repository.LedgerRepository
import com.localledger.app.domain.Account
import com.localledger.app.domain.AccountBalance
import com.localledger.app.domain.formatAmount
import com.localledger.app.domain.parseBalanceAmount
import com.localledger.app.ui.common.displayMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountDraft(val id: String? = null, val name: String = "", val initialBalance: String = "0.00")
data class AccountsState(val balances: List<AccountBalance> = emptyList(), val draft: AccountDraft? = null,
    val saving: Boolean = false, val loading: Boolean = true, val error: String? = null)

class AccountsViewModel(private val repository: LedgerRepository) : ViewModel() {
    private val changes = MutableStateFlow(AccountsState())
    val state = combine(repository.balances, changes) { balances, current -> current.copy(balances = balances, loading = false) }
        .catch { emit(changes.value.copy(loading = false, error = it.displayMessage())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsState())

    fun edit(account: Account?) { changes.update { it.copy(draft = account?.let { AccountDraft(it.id, it.name, formatAmount(it.initialBalanceMinor)) } ?: AccountDraft(), error = null) } }
    fun closeEditor() { if (!changes.value.saving) changes.update { it.copy(draft = null, error = null) } }
    fun setName(value: String) { changes.update { it.copy(draft = it.draft?.copy(name = value), error = null) } }
    fun setInitialBalance(value: String) { changes.update { it.copy(draft = it.draft?.copy(initialBalance = value), error = null) } }
    fun save() {
        val draft = changes.value.draft ?: return
        if (changes.value.saving) return
        val amount = parseBalanceAmount(draft.initialBalance)
        if (amount == null) { changes.update { it.copy(error = "初始余额须为有效金额，最多两位小数，可为负数。") }; return }
        changes.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try { repository.saveAccount(draft.id, draft.name, amount); changes.update { it.copy(saving = false, draft = null) } }
            catch (error: Exception) { changes.update { it.copy(saving = false, error = error.displayMessage()) } }
        }
    }
    fun setDisabled(account: Account, disabled: Boolean) {
        if (changes.value.saving) return
        changes.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try { repository.setAccountDisabled(account.id, disabled); changes.update { it.copy(saving = false) } }
            catch (error: Exception) { changes.update { it.copy(saving = false, error = error.displayMessage()) } }
        }
    }
}
