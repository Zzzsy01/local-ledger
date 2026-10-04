package com.localledger.app.data.repository

import androidx.room.withTransaction
import com.localledger.app.data.db.LedgerDatabase
import com.localledger.app.data.db.toDomain
import com.localledger.app.data.db.toEntity
import com.localledger.app.domain.Memo
import com.localledger.app.domain.validateMemo
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MemoRepository(private val db: LedgerDatabase, private val onChanged: () -> Unit = {}) {
    private val dao = db.memoDao()
    val memos: Flow<List<Memo>> = dao.observeMemos().map { rows -> rows.map { it.toDomain() } }
    suspend fun memo(id: String): Memo? = dao.memo(id)?.takeUnless { it.isDeleted }?.toDomain()

    suspend fun save(id: String?, title: String, content: String, isPinned: Boolean, isDone: Boolean,
        kind: String = "note", color: String = "yellow", folder: String = "默认", dueEpochDay: Long? = null) = db.withTransaction {
        val existing = id?.let { requireNotNull(dao.memo(it)) { "备忘录已不存在。" } }
        require(existing?.isDeleted != true) { "备忘录已删除。" }
        val now = System.currentTimeMillis()
        val memo = Memo(existing?.id ?: UUID.randomUUID().toString(), title.trim(), content.trim(), isPinned, isDone,
            existing?.createdAt ?: now, maxOf(now, existing?.updatedAt ?: now), kind = kind, color = color, folder = folder.trim(), dueEpochDay = dueEpochDay)
        validateMemo(memo)
        dao.save(memo.toEntity())
    }.also { onChanged() }

    suspend fun setPinned(id: String, pinned: Boolean) = change(id) { it.copy(isPinned = pinned) }
    suspend fun setDone(id: String, done: Boolean) = change(id) { it.copy(isDone = done) }
    suspend fun delete(id: String) = change(id) { it.copy(isDeleted = true) }
    private suspend fun change(id: String, block: (Memo) -> Memo) = db.withTransaction {
        val existing = requireNotNull(memo(id)) { "备忘录已不存在。" }
        dao.save(block(existing).copy(updatedAt = maxOf(System.currentTimeMillis(), existing.updatedAt)).toEntity())
    }.also { onChanged() }
}
