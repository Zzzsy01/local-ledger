package com.localledger.app.ui.memo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localledger.app.data.repository.MemoRepository
import com.localledger.app.domain.Memo
import com.localledger.app.ui.common.displayMessage
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MemosState(
    val items: List<Memo> = emptyList(), val total: Int = 0, val unfinished: Int = 0, val pinned: Int = 0,
    val loading: Boolean = true, val error: String? = null,
)

class MemosViewModel(private val repository: MemoRepository, private val savedState: SavedStateHandle) : ViewModel() {
    val query = savedState.getStateFlow("query", "")
    val filter = savedState.getStateFlow("filter", "all")
    val state = combine(repository.memos, query, filter) { memos, query, filter ->
        val words = query.trim()
        MemosState(memos.filter { memo ->
            (when (filter) { "done" -> memo.isDone; "open" -> !memo.isDone; "pinned" -> memo.isPinned; else -> true }) &&
                (words.isBlank() || memo.title.contains(words, ignoreCase = true) || memo.content.contains(words, ignoreCase = true))
        }, memos.size, memos.count { !it.isDone }, memos.count { it.isPinned }, loading = false)
    }.catch { emit(MemosState(loading = false, error = it.displayMessage())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MemosState())
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    fun query(value: String) { savedState["query"] = value }
    fun filter(value: String) { require(value in listOf("all", "open", "done", "pinned")); savedState["filter"] = value }
    fun dismissError() { mutableError.value = null }
    fun pin(memo: Memo) = action { repository.setPinned(memo.id, !memo.isPinned) }
    fun complete(memo: Memo) = action { repository.setDone(memo.id, !memo.isDone) }
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

data class MemoDraft(
    val title: String = "", val content: String = "", val isPinned: Boolean = false, val isDone: Boolean = false,
    val loading: Boolean = false, val saving: Boolean = false, val saved: Boolean = false, val error: String? = null,
)

class MemoEditorViewModel(private val repository: MemoRepository, private val savedState: SavedStateHandle, private val id: String?) : ViewModel() {
    private val mutableDraft = MutableStateFlow(MemoDraft(
        title = savedState["title"] ?: "", content = savedState["content"] ?: "",
        isPinned = savedState["pinned"] ?: false, isDone = savedState["done"] ?: false,
        loading = id != null && savedState.get<Boolean>("loaded") != true,
    ))
    val draft = mutableDraft.asStateFlow()
    init { if (mutableDraft.value.loading) viewModelScope.launch {
        try {
            val memo = requireNotNull(repository.memo(id!!)) { "备忘录已不存在。" }
            change { it.copy(title = memo.title, content = memo.content, isPinned = memo.isPinned, isDone = memo.isDone, loading = false) }
            savedState["loaded"] = true
        } catch (error: Exception) { mutableDraft.update { it.copy(loading = false, error = error.displayMessage()) } }
    } }
    fun change(block: (MemoDraft) -> MemoDraft) {
        if (mutableDraft.value.saving) return
        mutableDraft.update { block(it).copy(error = null) }
        val d = mutableDraft.value
        savedState["title"] = d.title; savedState["content"] = d.content
        savedState["pinned"] = d.isPinned; savedState["done"] = d.isDone
    }
    fun save() {
        val d = mutableDraft.value
        if (d.loading || d.saving || d.saved) return
        mutableDraft.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                repository.save(id, d.title, d.content, d.isPinned, d.isDone)
                mutableDraft.update { it.copy(saving = false, saved = true) }
            } catch (error: Exception) { mutableDraft.update { it.copy(saving = false, error = error.displayMessage()) } }
        }
    }
}
