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
    val folders: List<String> = emptyList(),
)

class MemosViewModel(private val repository: MemoRepository, private val savedState: SavedStateHandle) : ViewModel() {
    val query = savedState.getStateFlow("query", "")
    val filter = savedState.getStateFlow("filter", "all")
    val kind = savedState.getStateFlow("kind", "note")
    val folder = savedState.getStateFlow("folder", "全部")
    val sort = savedState.getStateFlow("sort", "updated")
    private val options = combine(kind, folder, sort) { k, f, s -> Triple(k, f, s) }
    val state = combine(repository.memos, query, filter, options) { all, query, filter, options ->
        val memos = all.filter { it.kind == options.first }
        val words = query.trim()
        MemosState(memos.filter { memo ->
            (when (filter) { "done" -> memo.isDone; "open" -> !memo.isDone; "pinned" -> memo.isPinned; else -> true }) &&
                (options.second == "全部" || memo.folder == options.second) &&
                (words.isBlank() || memo.title.contains(words, ignoreCase = true) || memo.content.contains(words, ignoreCase = true))
        }.sortedWith(compareByDescending<Memo> { it.isPinned }.thenComparator { a, b ->
            when (options.third) { "title" -> a.title.compareTo(b.title); "created" -> b.createdAt.compareTo(a.createdAt); else -> b.updatedAt.compareTo(a.updatedAt) }
        }), memos.size, memos.count { !it.isDone }, memos.count { it.isPinned }, loading = false, folders = memos.map { it.folder }.distinct().sorted())
    }.catch { emit(MemosState(loading = false, error = it.displayMessage())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MemosState())
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    fun query(value: String) { savedState["query"] = value }
    fun kind(value: String) { require(value in listOf("note", "todo")); savedState["kind"] = value }
    fun folder(value: String) { savedState["folder"] = value }
    fun sort(value: String) { savedState["sort"] = value }
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
    val kind: String = "note", val color: String = "yellow", val folder: String = "默认", val dueDate: String = "",
)

class MemoEditorViewModel(private val repository: MemoRepository, private val savedState: SavedStateHandle, private val id: String?) : ViewModel() {
    private val mutableDraft = MutableStateFlow(MemoDraft(
        title = savedState["title"] ?: "", content = savedState["content"] ?: "",
        isPinned = savedState["pinned"] ?: false, isDone = savedState["done"] ?: false,
        kind = savedState["kind"] ?: "note", color = savedState["color"] ?: "yellow", folder = savedState["folder"] ?: "默认", dueDate = savedState["dueDate"] ?: "",
        loading = id != null && savedState.get<Boolean>("loaded") != true,
    ))
    val draft = mutableDraft.asStateFlow()
    init { if (mutableDraft.value.loading) viewModelScope.launch {
        try {
            val memo = requireNotNull(repository.memo(id!!)) { "备忘录已不存在。" }
            change { it.copy(title = memo.title, content = memo.content, isPinned = memo.isPinned, isDone = memo.isDone, loading = false,
                kind = memo.kind, color = memo.color, folder = memo.folder, dueDate = memo.dueEpochDay?.let { java.time.LocalDate.ofEpochDay(it).toString() }.orEmpty()) }
            savedState["loaded"] = true
        } catch (error: Exception) { mutableDraft.update { it.copy(loading = false, error = error.displayMessage()) } }
    } }
    fun change(block: (MemoDraft) -> MemoDraft) {
        if (mutableDraft.value.saving) return
        mutableDraft.update { block(it).copy(error = null) }
        val d = mutableDraft.value
        savedState["title"] = d.title; savedState["content"] = d.content
        savedState["pinned"] = d.isPinned; savedState["done"] = d.isDone
        savedState["kind"] = d.kind; savedState["color"] = d.color; savedState["folder"] = d.folder; savedState["dueDate"] = d.dueDate
    }
    fun save() {
        val d = mutableDraft.value
        if (d.loading || d.saving || d.saved) return
        mutableDraft.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                repository.save(id, d.title, d.content, d.isPinned, d.isDone, d.kind, d.color, d.folder,
                    d.dueDate.takeIf { it.isNotBlank() }?.let { java.time.LocalDate.parse(it).toEpochDay() })
                mutableDraft.update { it.copy(saving = false, saved = true) }
            } catch (error: Exception) { mutableDraft.update { it.copy(saving = false, error = error.displayMessage()) } }
        }
    }
}
