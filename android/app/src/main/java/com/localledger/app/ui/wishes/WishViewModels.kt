package com.localledger.app.ui.wishes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localledger.app.data.repository.WishRepository
import com.localledger.app.domain.Wish
import com.localledger.app.domain.WishTotals
import com.localledger.app.domain.formatAmount
import com.localledger.app.domain.parseAmount
import com.localledger.app.domain.wishTotals
import com.localledger.app.ui.common.displayMessage
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class WishesState(val items: List<Wish> = emptyList(), val totals: WishTotals = WishTotals(),
    val loading: Boolean = true, val error: String? = null)

class WishesViewModel(private val repository: WishRepository, private val savedState: SavedStateHandle) : ViewModel() {
    val filter = savedState.getStateFlow("filter", "active")
    val state = combine(repository.wishes, filter) { wishes, filter ->
        WishesState(wishes.filter { if (filter == "purchased") it.isPurchased else !it.isPurchased }, wishTotals(wishes), loading = false)
    }.catch { emit(WishesState(loading = false, error = it.displayMessage())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WishesState())
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    fun filter(value: String) { require(value in listOf("active", "purchased")); savedState["filter"] = value }
    fun dismissError() { mutableError.value = null }
    fun saved(id: String, amount: Long) = action { repository.setSaved(id, amount) }
    fun purchase(wish: Wish) = action { repository.setPurchased(wish.id, !wish.isPurchased) }
    fun delete(id: String) = action { repository.delete(id) }
    private fun action(block: suspend () -> Unit) {
        if (mutableBusy.value) return
        mutableBusy.value = true
        viewModelScope.launch {
            try { block() } catch (error: Exception) { mutableError.value = error.displayMessage() }
            finally { mutableBusy.value = false }
        }
    }
}

data class WishDraft(val name: String = "", val target: String = "", val savedAmount: String = "0", val note: String = "",
    val loading: Boolean = false, val saving: Boolean = false, val saved: Boolean = false, val error: String? = null)

class WishEditorViewModel(private val repository: WishRepository, private val savedState: SavedStateHandle, private val id: String?) : ViewModel() {
    private val mutableDraft = MutableStateFlow(WishDraft(name = savedState["name"] ?: "", target = savedState["target"] ?: "",
        savedAmount = savedState["savedAmount"] ?: "0", note = savedState["note"] ?: "",
        loading = id != null && savedState.get<Boolean>("loaded") != true))
    val draft = mutableDraft.asStateFlow()
    init { if (mutableDraft.value.loading) viewModelScope.launch {
        try {
            val wish = requireNotNull(repository.wish(id!!)) { "心愿已不存在。" }
            change { it.copy(name = wish.name, target = formatAmount(wish.targetMinor), savedAmount = formatAmount(wish.savedMinor), note = wish.note.orEmpty(), loading = false) }
            savedState["loaded"] = true
        } catch (error: Exception) { mutableDraft.update { it.copy(loading = false, error = error.displayMessage()) } }
    } }
    fun change(block: (WishDraft) -> WishDraft) {
        if (mutableDraft.value.saving) return
        mutableDraft.update { block(it).copy(error = null) }
        val draft = mutableDraft.value
        savedState["name"] = draft.name; savedState["target"] = draft.target
        savedState["savedAmount"] = draft.savedAmount; savedState["note"] = draft.note
    }
    fun save() {
        val draft = mutableDraft.value
        if (draft.loading || draft.saving || draft.saved) return
        mutableDraft.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val target = requireNotNull(parseAmount(draft.target)) { "预计价格请输入大于零、最多两位小数的金额。" }
                val saved = requireNotNull(parseAmount(draft.savedAmount, allowZero = true)) { "已攒金额请输入零或正数，最多两位小数。" }
                repository.save(id, draft.name, target, saved, draft.note)
                mutableDraft.update { it.copy(saving = false, saved = true) }
            } catch (error: Exception) { mutableDraft.update { it.copy(saving = false, error = error.displayMessage()) } }
        }
    }
}
