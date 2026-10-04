package com.localledger.app.ui.assets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localledger.app.data.repository.AssetRepository
import com.localledger.app.domain.*
import com.localledger.app.ui.common.displayMessage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AssetsState(val items: List<Asset> = emptyList(), val loading: Boolean = true, val error: String? = null)
class AssetsViewModel(private val repository: AssetRepository) : ViewModel() {
    val state = repository.assets.map { AssetsState(it, false) }
        .catch { emit(AssetsState(loading = false, error = it.displayMessage())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssetsState())
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    fun dismissError() { mutableError.value = null }
    fun delete(id: String) { viewModelScope.launch {
        try { repository.delete(id) } catch (error: Exception) { mutableError.value = error.displayMessage() }
    } }
}

data class AssetDraft(
    val name: String = "", val kind: String = "数码", val purchase: String = "", val value: String = "",
    val prices: String = "", val note: String = "", val date: LocalDate = LocalDate.now(),
    val loading: Boolean = false, val saving: Boolean = false, val saved: Boolean = false, val error: String? = null,
)
class AssetEditorViewModel(private val repository: AssetRepository, private val savedState: SavedStateHandle, private val id: String?) : ViewModel() {
    private val mutableDraft = MutableStateFlow(AssetDraft(
        name = savedState["name"] ?: "", kind = savedState["kind"] ?: "数码",
        purchase = savedState["purchase"] ?: "", value = savedState["value"] ?: "",
        prices = savedState["prices"] ?: "", note = savedState["note"] ?: "",
        date = savedState.get<String>("date")?.let(LocalDate::parse) ?: LocalDate.now(),
        loading = id != null && savedState.get<Boolean>("loaded") != true,
    ))
    val draft = mutableDraft.asStateFlow()
    init { if (mutableDraft.value.loading) viewModelScope.launch {
        try {
            val asset = requireNotNull(repository.asset(id!!)) { "物品已不存在。" }
            change { it.copy(name = asset.name, kind = asset.kind, purchase = asset.purchaseMinor?.let(::formatAmount).orEmpty(),
                value = formatAmount(asset.valueMinor), prices = asset.referencePricesMinor.joinToString(", ", transform = ::formatAmount),
                note = asset.note.orEmpty(), date = Instant.ofEpochMilli(asset.pricedAt).atZone(ZoneId.systemDefault()).toLocalDate(), loading = false) }
            savedState["loaded"] = true
        } catch (error: Exception) { mutableDraft.update { it.copy(loading = false, error = error.displayMessage()) } }
    } }
    fun change(block: (AssetDraft) -> AssetDraft) {
        if (mutableDraft.value.saving) return
        mutableDraft.update { block(it).copy(error = null) }
        val d = mutableDraft.value
        savedState["name"] = d.name; savedState["kind"] = d.kind; savedState["purchase"] = d.purchase
        savedState["value"] = d.value; savedState["prices"] = d.prices; savedState["note"] = d.note; savedState["date"] = d.date.toString()
    }
    fun reportError(message: String) { mutableDraft.update { it.copy(error = message) } }
    fun save() {
        val d = mutableDraft.value
        if (d.saving || d.loading || d.saved) return
        mutableDraft.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val references = parseReferencePrices(d.prices)
                val value = if (references.isNotEmpty()) averagePrice(references) else requireNotNull(parseAmount(d.value, allowZero = true)) { "请填写当前估值，可填 0。" }
                val purchase = if (d.purchase.isBlank()) null else requireNotNull(parseAmount(d.purchase, allowZero = true)) { "购入价格式不正确。" }
                repository.save(id, d.name, d.kind, purchase, value, d.date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), references, d.note)
                mutableDraft.update { it.copy(saving = false, saved = true) }
            } catch (error: Exception) { mutableDraft.update { it.copy(saving = false, error = error.displayMessage()) } }
        }
    }
}
