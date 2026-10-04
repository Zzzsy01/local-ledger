package com.localledger.app.ui.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localledger.app.data.repository.LedgerRepository
import com.localledger.app.domain.Category
import com.localledger.app.ui.common.displayMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoryState(val categories: List<Category> = emptyList(), val loading: Boolean = true, val error: String? = null)
class CategoryViewModel(private val repository: LedgerRepository) : ViewModel() {
    val state = repository.categories.map { categories -> CategoryState(categories, loading = false) }
        .catch { emit(CategoryState(loading = false, error = it.displayMessage())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryState())
    private val mutableMessages = MutableSharedFlow<String>()
    val messages = mutableMessages.asSharedFlow()
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableOperationError = MutableStateFlow<String?>(null)
    val operationError = mutableOperationError.asStateFlow()
    fun clearError() { mutableOperationError.value = null }

    fun save(id: String?, name: String, type: Int, onSaved: () -> Unit) = action {
        repository.saveCategory(id, name.trim(), type)
        onSaved()
    }
    fun move(id: String, direction: Int) = action { repository.moveCategory(id, direction) }
    fun disable(id: String) = action { repository.disableCategory(id); mutableMessages.emit("分类已停用，历史账目仍保留") }
    private fun action(block: suspend () -> Unit) {
        if (mutableBusy.value) return
        mutableBusy.value = true
        mutableOperationError.value = null
        viewModelScope.launch {
            try { block() } catch (error: Exception) {
                val message = error.displayMessage()
                mutableOperationError.value = message
                mutableMessages.emit(message)
            }
            finally { mutableBusy.value = false }
        }
    }
}
