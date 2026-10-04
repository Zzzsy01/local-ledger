package com.localledger.app.data.repository

import androidx.room.withTransaction
import com.localledger.app.data.db.LedgerDatabase
import com.localledger.app.data.db.toDomain
import com.localledger.app.data.db.toEntity
import com.localledger.app.domain.*
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.map

class LifeRepository(private val db: LedgerDatabase) {
    private val dao = db.lifeDao()
    val items = dao.observeItems().map { rows -> rows.map { it.toDomain() } }
    val checkIns = dao.observeCheckIns().map { rows -> rows.map { it.toDomain() } }
    val focusSessions = dao.observeFocusSessions().map { rows -> rows.map { it.toDomain() } }
    val studyCards = dao.observeStudyCards().map { rows -> rows.map { it.toDomain() } }

    suspend fun item(id: String): LifeItem? = dao.item(id)?.takeUnless { it.isDeleted }?.toDomain()

    suspend fun saveItem(item: LifeItem) = db.withTransaction {
        val existing = dao.item(item.id)?.toDomain()
        require(existing == null || (!existing.isDeleted && existing.kind == item.kind)) { "记录已删除，或记录类型不一致。" }
        val now = System.currentTimeMillis()
        val normalized = item.copy(title = item.title.trim(), note = item.note?.trim()?.takeIf { it.isNotEmpty() },
            location = item.location?.trim()?.takeIf { it.isNotEmpty() }, unit = item.unit?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = existing?.createdAt ?: now, updatedAt = maxOf(now, existing?.updatedAt ?: now), isDeleted = false)
        validateLifeItem(normalized)
        // Moving a habit's start beyond its recorded days would make existing history invalid.
        if (normalized.kind == LifeKind.HABIT) require(dao.allCheckIns().none {
            it.itemId == normalized.id && it.dateEpochDay < normalized.dateEpochDay
        }) { "开始日期不能晚于已保存的打卡日期。" }
        dao.saveItem(normalized.toEntity())
    }

    suspend fun setProgress(id: String, progress: Long) = changeItem(id) { item ->
        require(item.kind in lifePlanKinds) { "此记录没有目标进度。" }
        item.copy(progressCount = progress)
    }

    suspend fun renewSubscription(id: String) = changeItem(id) { item ->
        item.copy(dateEpochDay = nextSubscriptionDate(item).toEpochDay())
    }

    suspend fun deleteItem(id: String) = changeItem(id) { it.copy(isDeleted = true) }

    private suspend fun changeItem(id: String, change: (LifeItem) -> LifeItem) = db.withTransaction {
        val item = requireNotNull(item(id)) { "记录已不存在。" }
        val updated = change(item).copy(updatedAt = maxOf(System.currentTimeMillis(), item.updatedAt))
        validateLifeItem(updated); dao.saveItem(updated.toEntity())
    }

    suspend fun toggleCheckIn(id: String, day: LocalDate = LocalDate.now()) = db.withTransaction {
        val habit = requireNotNull(item(id)) { "习惯已不存在。" }
        require(habit.kind == LifeKind.HABIT && day.toEpochDay() >= habit.dateEpochDay && day <= LocalDate.now()) { "只能打卡开始日期之后、今天及以前的日期。" }
        val existing = dao.checkIn(id, day.toEpochDay())?.toDomain()
        val now = System.currentTimeMillis()
        val checked = LifeCheckIn(id, day.toEpochDay(), existing?.createdAt ?: now,
            maxOf(now, existing?.updatedAt ?: now), isDeleted = existing?.isDeleted == false)
        validateLifeCheckIn(checked); dao.saveCheckIn(checked.toEntity())
    }

    suspend fun startFocus(title: String, minutes: Int) = db.withTransaction {
        require(dao.activeFocusSession() == null) { "请先完成或放弃当前专注。" }
        require(minutes in 1..180) { "专注时长应为 1 至 180 分钟。" }
        val now = System.currentTimeMillis()
        val session = FocusSession(UUID.randomUUID().toString(), title.trim(), minutes * 60, startedAt = now,
            status = FocusStatus.RUNNING, createdAt = now, updatedAt = now)
        validateFocusSession(session); dao.saveFocusSession(session.toEntity())
    }

    suspend fun pauseFocus(id: String) = db.withTransaction {
        val existing = requireNotNull(dao.focusSession(id)?.takeUnless { it.isDeleted }?.toDomain()) { "专注记录已不存在。" }
        require(existing.status == FocusStatus.RUNNING) { "当前专注没有在计时。" }
        val now = System.currentTimeMillis()
        val completed = completeFocusIfDue(existing, now)
        val updated = if (completed.status == FocusStatus.COMPLETED) completed else existing.copy(
            elapsedMillis = existing.elapsedAt(now), startedAt = null, status = FocusStatus.PAUSED,
            updatedAt = maxOf(now, existing.updatedAt))
        validateFocusSession(updated); dao.saveFocusSession(updated.toEntity())
    }

    suspend fun resumeFocus(id: String) = db.withTransaction {
        val existing = requireNotNull(dao.focusSession(id)?.takeUnless { it.isDeleted }?.toDomain()) { "专注记录已不存在。" }
        require(existing.status == FocusStatus.PAUSED) { "此专注不能继续计时。" }
        require(dao.activeFocusSession()?.id == id) { "已有其他专注正在计时。" }
        val now = System.currentTimeMillis()
        val updated = existing.copy(startedAt = maxOf(now, existing.createdAt), status = FocusStatus.RUNNING, updatedAt = maxOf(now, existing.updatedAt))
        validateFocusSession(updated); dao.saveFocusSession(updated.toEntity())
    }

    /** One persisted transition, even if the screen polls repeatedly or the process resumes late. */
    suspend fun completeDueFocus(now: Long = System.currentTimeMillis()) = db.withTransaction {
        val session = dao.activeFocusSession()?.toDomain() ?: return@withTransaction
        val completed = completeFocusIfDue(session, now)
        if (completed != session) { validateFocusSession(completed); dao.saveFocusSession(completed.toEntity()) }
    }

    suspend fun deleteFocus(id: String) = db.withTransaction {
        val existing = requireNotNull(dao.focusSession(id)?.takeUnless { it.isDeleted }?.toDomain()) { "专注记录已不存在。" }
        val now = System.currentTimeMillis()
        val resolved = completeFocusIfDue(existing, now)
        val deleted = resolved.copy(isDeleted = true, elapsedMillis = resolved.elapsedAt(now),
            startedAt = null, status = if (resolved.status == FocusStatus.COMPLETED) FocusStatus.COMPLETED else FocusStatus.PAUSED,
            updatedAt = maxOf(now, existing.updatedAt))
        validateFocusSession(deleted); dao.saveFocusSession(deleted.toEntity())
    }

    suspend fun saveStudyCard(id: String?, language: String, word: String, meaning: String, note: String?) = db.withTransaction {
        val existing = id?.let { requireNotNull(dao.studyCard(it)?.takeUnless { row -> row.isDeleted }?.toDomain()) { "单词卡已不存在。" } }
        val now = System.currentTimeMillis()
        val normalizedWord = word.trim()
        val normalizedMeaning = meaning.trim()
        val changed = existing != null && (existing.language != language || existing.word != normalizedWord || existing.meaning != normalizedMeaning)
        val card = StudyCard(existing?.id ?: UUID.randomUUID().toString(), language, normalizedWord, normalizedMeaning,
            note?.trim()?.takeIf { it.isNotEmpty() }, if (existing == null || changed) LocalDate.now().toEpochDay() else existing.dueEpochDay,
            if (existing == null || changed) 0 else existing.correctStreak, if (existing == null || changed) 0 else existing.intervalDays,
            if (existing == null || changed) null else existing.lastReviewEpochDay,
            existing?.createdAt ?: now, maxOf(now, existing?.updatedAt ?: now))
        validateStudyCard(card); dao.saveStudyCard(card.toEntity())
    }

    suspend fun reviewCard(id: String, remembered: Boolean) = db.withTransaction {
        val card = requireNotNull(dao.studyCard(id)?.takeUnless { it.isDeleted }?.toDomain()) { "单词卡已不存在。" }
        dao.saveStudyCard(reviewStudyCard(card, remembered, LocalDate.now(), System.currentTimeMillis()).toEntity())
    }

    suspend fun deleteCard(id: String) = db.withTransaction {
        val existing = requireNotNull(dao.studyCard(id)?.takeUnless { it.isDeleted }?.toDomain()) { "单词卡已不存在。" }
        val deleted = existing.copy(isDeleted = true, updatedAt = maxOf(System.currentTimeMillis(), existing.updatedAt))
        validateStudyCard(deleted); dao.saveStudyCard(deleted.toEntity())
    }
}
