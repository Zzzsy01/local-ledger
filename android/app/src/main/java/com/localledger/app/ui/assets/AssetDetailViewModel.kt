package com.localledger.app.ui.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localledger.app.data.repository.AssetRepository
import com.localledger.app.domain.*
import com.localledger.app.ui.common.displayMessage
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

data class AssetDetailState(val asset: Asset? = null, val records: List<AssetRecord> = emptyList(), val loading: Boolean = true, val error: String? = null)
class AssetDetailViewModel(private val repository: AssetRepository, private val id: String) : ViewModel() {
    val state = combine(repository.assets, repository.records) { assets, records ->
        AssetDetailState(assets.firstOrNull { it.id == id }, records.filter { it.assetId == id }, false)
    }.catch { emit(AssetDetailState(loading = false, error = it.displayMessage())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AssetDetailState())
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    fun dismissError() { mutableError.value = null }
    fun photoFile(path: String) = repository.photoFile(path)
    fun favorite(value: Boolean) = action { repository.favorite(id, value) }
    fun move(location: String, done: () -> Unit) = action { repository.move(id, location); done() }
    fun revalue(amount: String, day: String, done: () -> Unit) = action {
        repository.revalue(id, requireNotNull(parseAmount(amount, allowZero = true)) { "请填写有效估值，可为 0。" }, LocalDate.parse(day)); done()
    }
    fun record(recordId: String?, kind: String, content: String, day: String, done: () -> Unit) = action {
        repository.saveRecord(id, recordId, kind, content, LocalDate.parse(day)); done()
    }
    private fun action(block: suspend () -> Unit) {
        if (mutableBusy.value) return
        mutableBusy.value = true; mutableError.value = null
        viewModelScope.launch {
            try { block() } catch (error: Exception) { mutableError.value = error.displayMessage() }
            finally { mutableBusy.value = false }
        }
    }
}
