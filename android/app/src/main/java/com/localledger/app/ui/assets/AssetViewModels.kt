package com.localledger.app.ui.assets

import android.net.Uri
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

val assetSorts = listOf("最近添加", "估值从高到低", "购入价从高到低", "日均成本从高到低", "名称")
data class AssetFilters(val query: String = "", val status: String = "全部", val kind: String = "全部", val sort: String = "最近添加", val grid: Boolean = false)
data class AssetsState(val items: List<Asset> = emptyList(), val loading: Boolean = true, val error: String? = null,
    val summary: AssetSummary = AssetSummary(), val total: Int = 0, val kinds: List<String> = assetKinds, val filters: AssetFilters = AssetFilters())
class AssetsViewModel(private val repository: AssetRepository) : ViewModel() {
    private val filters = MutableStateFlow(AssetFilters())
    val state = combine(repository.assets, filters) { assets, filter ->
        val matching = assets.filter { asset ->
            (filter.status == "全部" || asset.status == filter.status) && (filter.kind == "全部" || asset.kind == filter.kind) &&
                (filter.query.isBlank() || listOf(asset.name, asset.kind, asset.note.orEmpty(), asset.location.orEmpty(), asset.channel.orEmpty()).any { it.contains(filter.query.trim(), ignoreCase = true) })
        }
        val sorted = when (filter.sort) {
            "估值从高到低" -> matching.sortedByDescending { it.valueMinor }
            "购入价从高到低" -> matching.sortedByDescending { it.purchaseMinor ?: -1 }
            "日均成本从高到低" -> matching.sortedByDescending { assetDailyCostMinor(it) ?: -1 }
            "名称" -> matching.sortedBy { it.name }
            else -> matching.sortedByDescending { it.createdAt }
        }
        AssetsState(sorted, false, summary = summarizeAssets(assets), total = assets.size, kinds = (assetKinds + assets.map { it.kind }).distinct(), filters = filter)
    }
        .catch { emit(AssetsState(loading = false, error = it.displayMessage())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssetsState())
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    fun filter(block: (AssetFilters) -> AssetFilters) { filters.update(block) }
    fun photoFile(path: String) = repository.photoFile(path)
    fun dismissError() { mutableError.value = null }
    fun delete(id: String) { viewModelScope.launch {
        try { repository.delete(id) } catch (error: Exception) { mutableError.value = error.displayMessage() }
    } }
}

data class AssetDraft(
    val name: String = "", val kind: String = "数码", val purchase: String = "", val value: String = "",
    val prices: String = "", val note: String = "", val date: LocalDate = LocalDate.now(),
    val loading: Boolean = false, val saving: Boolean = false, val saved: Boolean = false, val error: String? = null,
    val quantity: Int = 1, val acquisition: String = "购买", val purchaseDate: String = "", val channel: String = "",
    val location: String = "", val warrantyUntil: String = "", val status: String = "持有中", val disposedDate: String = "",
    val photoPaths: List<String> = emptyList(), val importing: Boolean = false,
)
class AssetEditorViewModel(private val repository: AssetRepository, private val savedState: SavedStateHandle, private val id: String?) : ViewModel() {
    private val mutableDraft = MutableStateFlow(AssetDraft(
        name = savedState["name"] ?: "", kind = savedState["kind"] ?: "数码",
        purchase = savedState["purchase"] ?: "", value = savedState["value"] ?: "",
        prices = savedState["prices"] ?: "", note = savedState["note"] ?: "",
        date = savedState.get<String>("date")?.let(LocalDate::parse) ?: LocalDate.now(),
        loading = id != null && savedState.get<Boolean>("loaded") != true,
        quantity = savedState["quantity"] ?: 1, acquisition = savedState["acquisition"] ?: "购买",
        purchaseDate = savedState["purchaseDate"] ?: "", channel = savedState["channel"] ?: "", location = savedState["location"] ?: "",
        warrantyUntil = savedState["warrantyUntil"] ?: "", status = savedState["status"] ?: "持有中", disposedDate = savedState["disposedDate"] ?: "",
        photoPaths = savedState.get<ArrayList<String>>("photoPaths")?.toList() ?: emptyList(),
    ))
    val draft = mutableDraft.asStateFlow()
    init { if (mutableDraft.value.loading) viewModelScope.launch {
        try {
            val asset = requireNotNull(repository.asset(id!!)) { "物品已不存在。" }
            change { it.copy(name = asset.name, kind = asset.kind, purchase = asset.purchaseMinor?.let(::formatAmount).orEmpty(),
                value = formatAmount(asset.valueMinor), prices = asset.referencePricesMinor.joinToString(", ", transform = ::formatAmount),
                note = asset.note.orEmpty(), date = Instant.ofEpochMilli(asset.pricedAt).atZone(ZoneId.systemDefault()).toLocalDate(), loading = false,
                quantity = asset.quantity, acquisition = asset.acquisition, purchaseDate = asset.purchaseDate.orEmpty(),
                channel = asset.channel.orEmpty(), location = asset.location.orEmpty(), warrantyUntil = asset.warrantyUntil.orEmpty(),
                status = asset.status, disposedDate = asset.disposedDate.orEmpty(), photoPaths = asset.photoPaths) }
            savedState["loaded"] = true
        } catch (error: Exception) { mutableDraft.update { it.copy(loading = false, error = error.displayMessage()) } }
    } }
    fun change(block: (AssetDraft) -> AssetDraft) {
        if (mutableDraft.value.saving) return
        mutableDraft.update { block(it).copy(error = null) }
        val d = mutableDraft.value
        savedState["name"] = d.name; savedState["kind"] = d.kind; savedState["purchase"] = d.purchase
        savedState["value"] = d.value; savedState["prices"] = d.prices; savedState["note"] = d.note; savedState["date"] = d.date.toString()
        savedState["quantity"] = d.quantity; savedState["acquisition"] = d.acquisition; savedState["purchaseDate"] = d.purchaseDate
        savedState["channel"] = d.channel; savedState["location"] = d.location; savedState["warrantyUntil"] = d.warrantyUntil
        savedState["status"] = d.status; savedState["disposedDate"] = d.disposedDate; savedState["photoPaths"] = ArrayList(d.photoPaths)
    }
    fun photoFile(path: String) = repository.photoFile(path)
    fun importPhotos(uris: List<Uri>) {
        val d = mutableDraft.value
        if (uris.isEmpty() || d.saving || d.importing || d.loading) return
        if (uris.size + d.photoPaths.size > 4) { reportError("最多添加 4 张照片，请减少选择数量。"); return }
        mutableDraft.update { it.copy(importing = true, error = null) }
        viewModelScope.launch {
            try {
                for (uri in uris) {
                    val path = repository.importPhoto(uri)
                    change { it.copy(photoPaths = it.photoPaths + path) }
                }
            } catch (error: Exception) { reportError(error.displayMessage()) }
            finally { mutableDraft.update { it.copy(importing = false) } }
        }
    }
    fun reportError(message: String) { mutableDraft.update { it.copy(error = message) } }
    fun save() {
        val d = mutableDraft.value
        if (d.saving || d.loading || d.saved || d.importing) return
        mutableDraft.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val references = parseReferencePrices(d.prices)
                val value = if (references.isNotEmpty()) averagePrice(references) else requireNotNull(parseAmount(d.value, allowZero = true)) { "请填写当前估值，可填 0。" }
                val purchase = if (d.purchase.isBlank()) null else requireNotNull(parseAmount(d.purchase, allowZero = true)) { "购入价格式不正确。" }
                repository.save(id, d.name, d.kind, purchase, value, d.date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), references, d.note,
                    d.quantity, d.acquisition, d.purchaseDate.takeIf { it.isNotBlank() }, d.channel, d.location,
                    d.warrantyUntil.takeIf { it.isNotBlank() }, d.status, d.disposedDate.takeIf { it.isNotBlank() }, d.photoPaths)
                mutableDraft.update { it.copy(saving = false, saved = true) }
            } catch (error: Exception) { mutableDraft.update { it.copy(saving = false, error = error.displayMessage()) } }
        }
    }
}
