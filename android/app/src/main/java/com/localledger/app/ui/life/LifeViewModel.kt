package com.localledger.app.ui.life

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localledger.app.data.repository.LifeRepository
import com.localledger.app.domain.*
import com.localledger.app.ui.common.displayMessage
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class LifeState(
    val items: List<LifeItem> = emptyList(),
    val checkIns: List<LifeCheckIn> = emptyList(),
    val focusSessions: List<FocusSession> = emptyList(),
    val cards: List<StudyCard> = emptyList(),
    val now: Long = System.currentTimeMillis(),
    val loading: Boolean = true,
    val error: String? = null,
)

data class LifeDraft(
    val id: String? = null,
    val kind: LifeKind = LifeKind.HABIT,
    val title: String = "",
    val date: String = LocalDate.now().toString(),
    val note: String = "",
    val endDate: String = "",
    val location: String = "",
    val target: String = "1",
    val progress: String = "0",
    val unit: String = "项",
    val amount: String = "",
    val repeatYearly: Boolean = false,
    val mood: Int = 3,
    val cycleMonths: String = "1",
) {
    companion object {
        fun create(kind: LifeKind) = LifeDraft(kind = kind, unit = when (kind) {
            LifeKind.LEARNING -> "课"; LifeKind.READING -> "页"; else -> "项"
        }, repeatYearly = kind in setOf(LifeKind.BIRTHDAY, LifeKind.ANNIVERSARY))
        fun from(item: LifeItem) = LifeDraft(item.id, item.kind, item.title, item.date.toString(), item.note.orEmpty(),
            item.endDateEpochDay?.let { LocalDate.ofEpochDay(it).toString() }.orEmpty(), item.location.orEmpty(),
            item.targetCount?.toString() ?: "1", item.progressCount.toString(), item.unit ?: "项",
            item.amountMinor?.let(::formatAmount).orEmpty(), item.repeatYearly, item.mood ?: 3,
            item.subscriptionCycleMonths?.toString() ?: "1")
    }
}

class LifeViewModel(private val repository: LifeRepository, private val savedState: SavedStateHandle) : ViewModel() {
    private val clock = MutableStateFlow(System.currentTimeMillis())
    val search = savedState.getStateFlow("lifeSearch", "")
    val monthlySummary = savedState.getStateFlow("lifeMonthlySummary", false)
    val state = combine(repository.items, repository.checkIns, repository.focusSessions, repository.studyCards, clock) { items, checks, focus, cards, now ->
        LifeState(items, checks, focus, cards, now, loading = false)
    }.catch { emit(LifeState(loading = false, error = it.displayMessage())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LifeState())
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    private val mutableMessage = MutableStateFlow<String?>(null)
    val message = mutableMessage.asStateFlow()

    init {
        viewModelScope.launch {
            // Persisted wall-clock anchor also catches completion after a process restart.
            repository.completeDueFocus()
            while (true) {
                clock.value = System.currentTimeMillis()
                if (state.value.focusSessions.any { it.status == FocusStatus.RUNNING && it.remainingSeconds(clock.value) == 0L }) {
                    try { repository.completeDueFocus(clock.value) }
                    catch (error: Exception) { mutableError.value = error.displayMessage() }
                }
                delay(1_000)
            }
        }
    }

    fun setSearch(value: String) { savedState["lifeSearch"] = value }
    fun setMonthlySummary(value: Boolean) { savedState["lifeMonthlySummary"] = value }
    fun clearFeedback() { mutableError.value = null; mutableMessage.value = null }

    fun saveDraft(draft: LifeDraft, onSaved: () -> Unit) = action {
        val existing = draft.id?.let { requireNotNull(repository.item(it)) { "记录已不存在。" } }
        val date = LocalDate.parse(draft.date)
        val plan = draft.kind in lifePlanKinds
        val subscription = draft.kind == LifeKind.SUBSCRIPTION
        val travel = draft.kind == LifeKind.TRAVEL
        val now = System.currentTimeMillis()
        val amount = if ((travel || subscription) && draft.amount.isNotBlank())
            requireNotNull(parseAmount(draft.amount)) { "金额应大于零，最多两位小数。" } else null
        val item = LifeItem(existing?.id ?: UUID.randomUUID().toString(), draft.kind, draft.title, date.toEpochDay(),
            draft.note.takeIf { it.isNotBlank() },
            if (travel && draft.endDate.isNotBlank()) LocalDate.parse(draft.endDate).toEpochDay() else null,
            draft.location.takeIf { travel && it.isNotBlank() },
            if (plan) requireNotNull(draft.target.toLongOrNull()) { "请输入整数目标。" } else null,
            if (plan) requireNotNull(draft.progress.toLongOrNull()) { "请输入整数进度。" } else 0,
            draft.unit.takeIf { plan }, amount,
            draft.repeatYearly && draft.kind in setOf(LifeKind.BIRTHDAY, LifeKind.ANNIVERSARY),
            draft.mood.takeIf { draft.kind == LifeKind.MOOD },
            if (subscription) requireNotNull(draft.cycleMonths.toIntOrNull()) { "请输入整数续期间隔。" } else null,
            if (subscription) existing?.renewalDayOfMonth?.takeIf { existing.dateEpochDay == date.toEpochDay() } ?: date.dayOfMonth else null,
            existing?.createdAt ?: now, maxOf(now, existing?.updatedAt ?: now))
        repository.saveItem(item); mutableMessage.value = "${draft.kind.label}已保存"; onSaved()
    }

    fun setProgress(item: LifeItem, value: Long) = action { repository.setProgress(item.id, value); mutableMessage.value = "目标进度已更新" }
    fun toggleCheckIn(item: LifeItem, day: LocalDate = LocalDate.now()) = action { repository.toggleCheckIn(item.id, day) }
    fun renew(item: LifeItem) = action { repository.renewSubscription(item.id); mutableMessage.value = "续期日期已推进；如需记账，请在账本新增支出" }
    fun delete(item: LifeItem, onDeleted: () -> Unit) = action { repository.deleteItem(item.id); mutableMessage.value = "已移入回收站"; onDeleted() }
    fun startFocus(title: String, minutes: String, onStarted: () -> Unit) = action {
        repository.startFocus(title, requireNotNull(minutes.toIntOrNull()) { "请输入整数分钟。" }); onStarted()
    }
    fun pauseFocus(session: FocusSession) = action { repository.pauseFocus(session.id) }
    fun resumeFocus(session: FocusSession) = action { repository.resumeFocus(session.id) }
    fun completeFocus() = action { repository.completeDueFocus() }
    fun deleteFocus(session: FocusSession, onDeleted: () -> Unit) = action { repository.deleteFocus(session.id); onDeleted() }
    fun saveCard(id: String?, language: String, word: String, meaning: String, note: String, onSaved: () -> Unit) = action {
        repository.saveStudyCard(id, language, word, meaning, note); mutableMessage.value = "单词卡已保存"; onSaved()
    }
    fun review(card: StudyCard, remembered: Boolean) = action { repository.reviewCard(card.id, remembered) }
    fun deleteCard(card: StudyCard, onDeleted: () -> Unit) = action { repository.deleteCard(card.id); onDeleted() }

    private fun action(block: suspend () -> Unit) {
        if (mutableBusy.value) return
        mutableBusy.value = true; clearFeedback()
        viewModelScope.launch {
            try { block() } catch (error: Exception) { mutableError.value = error.displayMessage() }
            finally { mutableBusy.value = false }
        }
    }
}
