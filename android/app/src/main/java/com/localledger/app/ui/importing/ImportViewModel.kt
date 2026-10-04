package com.localledger.app.ui.importing

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localledger.app.data.repository.ImportRepository
import com.localledger.app.data.repository.LedgerRepository
import com.localledger.app.domain.*
import com.localledger.app.ui.common.displayMessage
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ImportState(val categories: List<Category> = emptyList(), val accounts: List<Account> = emptyList(),
    val expense: String = "", val income: String = "", val account: String = "", val encoding: String = "UTF-8",
    val preview: ImportPreview? = null, val skipSimilar: Boolean = true, val busy: Boolean = false, val message: String? = null)
class ImportViewModel(private val repository: ImportRepository, ledger: LedgerRepository) : ViewModel() {
    private val mutable = MutableStateFlow(ImportState())
    val state = mutable.asStateFlow()
    init { viewModelScope.launch { combine(ledger.categories, ledger.accounts) { categories, accounts -> categories to accounts }.collect { (categories, accounts) ->
        mutable.update { it.copy(categories = categories.filter { c -> !c.isDeleted }, accounts = accounts.filter { a -> !a.isDeleted },
            expense = it.expense.ifBlank { categories.firstOrNull { c -> c.type == 0 && !c.isDeleted }?.id.orEmpty() },
            income = it.income.ifBlank { categories.firstOrNull { c -> c.type == 1 && !c.isDeleted }?.id.orEmpty() },
            account = it.account.ifBlank { accounts.firstOrNull { a -> !a.isDeleted }?.id.orEmpty() }) }
    } } }
    fun change(block: (ImportState) -> ImportState) { if (!mutable.value.busy) mutable.update { block(it).copy(preview = null, message = null) } }
    fun skipSimilar(value: Boolean) { mutable.update { it.copy(skipSimilar = value) } }
    fun read(uri: Uri) = run { val s = mutable.value; operate {
        mutable.update { it.copy(preview = repository.preview(uri, s.encoding, s.expense, s.income, s.account)) }
    } }
    fun template(uri: Uri) = operate { repository.template(uri); mutable.update { it.copy(message = "导入模板已保存，请按表头整理账单。") } }
    fun confirm() { val s = mutable.value; val preview = s.preview ?: return; operate {
        val count = repository.confirm(preview, s.skipSimilar)
        mutable.update { it.copy(preview = null, message = "已导入 $count 笔账目。") }
    } }
    private fun operate(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try { block() } catch (e: Exception) { mutable.update { it.copy(message = e.displayMessage()) } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
}
