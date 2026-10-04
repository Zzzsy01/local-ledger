package com.localledger.app.data.repository

import androidx.room.withTransaction
import com.localledger.app.data.db.LedgerDatabase
import com.localledger.app.data.db.toDomain
import com.localledger.app.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

data class DeletedRecord(val type: String, val id: String, val title: String, val deletedAt: Long)

class RecycleRepository(private val db: LedgerDatabase, private val ledger: LedgerRepository, private val onMemoChanged: () -> Unit = {}) {
    val items: Flow<List<DeletedRecord>> = combine(listOf(
        db.memoDao().deleted().map { rows -> rows.map { DeletedRecord("备忘录", it.id, it.title, it.updatedAt) } },
        db.assetDao().deleted().map { rows -> rows.map { DeletedRecord("物品", it.id, it.name, it.updatedAt) } },
        db.wishDao().deleted().map { rows -> rows.map { DeletedRecord("心愿", it.id, it.name, it.updatedAt) } },
        db.lifeDao().deletedItems().map { rows -> rows.map { DeletedRecord("生活", it.id, it.title, it.updatedAt) } },
        db.lifeDao().deletedFocus().map { rows -> rows.map { DeletedRecord("专注", it.id, it.title, it.updatedAt) } },
        db.lifeDao().deletedCards().map { rows -> rows.map { DeletedRecord("单词", it.id, it.word, it.updatedAt) } },
        db.ledgerDao().deletedTransactions().map { rows -> rows.map { DeletedRecord("账目", it.id, it.note ?: "¥${formatAmount(it.amountMinor)}", it.updatedAt) } },
    )) { lists -> lists.flatMap { it }.sortedByDescending { it.deletedAt } }

    suspend fun restore(record: DeletedRecord) = db.withTransaction {
        val now = System.currentTimeMillis()
        when (record.type) {
            "备忘录" -> db.memoDao().memo(record.id)?.let { db.memoDao().save(it.copy(isDeleted = false, updatedAt = maxOf(now, it.updatedAt))) }
            "物品" -> db.assetDao().asset(record.id)?.let { item ->
                val restored = item.copy(isDeleted = false, updatedAt = maxOf(now, item.updatedAt))
                validateAsset(restored.toDomain())
                summarizeAssets(db.assetDao().allAssets().filterNot { it.id == item.id }.map { it.toDomain() } + restored.toDomain())
                db.assetDao().save(restored)
            }
            "心愿" -> db.wishDao().wish(record.id)?.let { item ->
                val restored = item.copy(isDeleted = false, updatedAt = maxOf(now, item.updatedAt))
                wishTotals(db.wishDao().allWishes().filterNot { it.id == item.id }.map { it.toDomain() } + restored.toDomain())
                db.wishDao().save(restored)
            }
            "生活" -> db.lifeDao().item(record.id)?.let { db.lifeDao().saveItem(it.copy(isDeleted = false, updatedAt = maxOf(now, it.updatedAt))) }
            "专注" -> db.lifeDao().focusSession(record.id)?.let { item ->
                require(item.status == "COMPLETED" || db.lifeDao().activeFocusSession() == null) { "请先结束当前专注。" }
                // A discarded timer resumes as paused; time spent in the recycle bin is not focus time.
                val restored = if (item.status == "RUNNING") item.copy(status = "PAUSED", startedAt = null) else item
                db.lifeDao().saveFocusSession(restored.copy(isDeleted = false, updatedAt = maxOf(now, item.updatedAt)))
            }
            "单词" -> db.lifeDao().studyCard(record.id)?.let { db.lifeDao().saveStudyCard(it.copy(isDeleted = false, updatedAt = maxOf(now, it.updatedAt))) }
            "账目" -> ledger.undoDeleteTransaction(record.id)
        }
    }.also { if (record.type == "备忘录") onMemoChanged() }
}
