package com.localledger.app.data.repository

import android.content.SharedPreferences
import android.net.Uri
import androidx.room.withTransaction
import com.localledger.app.data.backup.BackupFiles
import com.localledger.app.data.db.CategoryEntity
import com.localledger.app.data.db.LedgerDatabase
import com.localledger.app.data.db.TransactionEntity
import com.localledger.app.data.db.toDomain
import com.localledger.app.data.db.toEntity
import com.localledger.app.domain.Account
import com.localledger.app.domain.Category
import com.localledger.app.domain.CategoryTotal
import com.localledger.app.domain.EXPENSE
import com.localledger.app.domain.INCOME
import com.localledger.app.domain.LedgerEntry
import com.localledger.app.domain.LedgerSnapshot
import com.localledger.app.domain.MonthlySummary
import com.localledger.app.domain.LedgerFilter
import com.localledger.app.domain.AccountTotal
import com.localledger.app.domain.ReportMetrics
import com.localledger.app.domain.Transaction
import com.localledger.app.domain.validateSnapshot
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LedgerRepository(
    private val db: LedgerDatabase,
    private val preferences: SharedPreferences,
    private val backupFiles: BackupFiles,
) {
    private val dao = db.ledgerDao()
    val categories: Flow<List<Category>> = dao.observeCategories().map { rows -> rows.map { it.toDomain() } }
    val accounts: Flow<List<Account>> = dao.observeAccounts().map { rows -> rows.map { it.toDomain() } }

    suspend fun initialize() = db.seedDefaults()

    fun entries(start: Long, end: Long): Flow<List<LedgerEntry>> = dao.observeEntries(start, end).map { rows -> rows.map { it.toDomain() } }
    fun summary(start: Long, end: Long): Flow<MonthlySummary> = dao.observeSummary(start, end)
    fun categoryTotals(start: Long, end: Long, type: Int): Flow<List<CategoryTotal>> = dao.observeCategoryTotals(start, end, type)
    fun accountTotals(start: Long, end: Long, type: Int): Flow<List<AccountTotal>> = dao.observeAccountTotals(start, end, type)
    fun reportMetrics(start: Long, end: Long): Flow<ReportMetrics> = dao.observeReportMetrics(start, end)
    fun filteredEntries(filter: LedgerFilter): Flow<List<LedgerEntry>> {
        require(filter.start >= 0 && filter.end > filter.start) { "筛选日期无效。" }
        require(filter.type == null || filter.type == EXPENSE || filter.type == INCOME) { "收支类型无效。" }
        require((filter.minimum ?: 0) >= 0 && (filter.maximum ?: 0) >= 0) { "筛选金额无效。" }
        require(filter.minimum == null || filter.maximum == null || filter.minimum <= filter.maximum) { "最低金额不能大于最高金额。" }
        return dao.observeFilteredEntries(filter.start, filter.end, filter.keyword.trim(), filter.type,
            filter.categoryId, filter.accountId, filter.minimum, filter.maximum).map { rows -> rows.map { it.toDomain() } }
    }
    fun lastAccountId(): String? = preferences.getString(LAST_ACCOUNT_ID, null)

    suspend fun transaction(id: String): Transaction? = dao.transaction(id)?.takeUnless { it.isDeleted }?.toDomain()

    suspend fun saveTransaction(
        id: String?, amountMinor: Long, type: Int, categoryId: String,
        accountId: String, note: String?, occurredAt: Long, source: Int = 0, importKey: String? = null,
    ): String {
        require(amountMinor > 0) { "请输入大于零的金额。" }
        require(type == EXPENSE || type == INCOME) { "收支类型无效。" }
        require(occurredAt >= 0) { "记账日期无效。" }
        require(source >= 0) { "账目来源无效。" }
        val savedId = db.withTransaction {
            val existing = id?.let { requireNotNull(dao.transaction(it)) { "这笔记录已不存在。" } }
            require(existing?.isDeleted != true) { "这笔记录已删除。" }
            val category = requireNotNull(dao.category(categoryId)) { "请选择有效分类。" }
            val account = requireNotNull(dao.account(accountId)) { "请选择有效账户。" }
            require(category.type == type) { "分类与收支类型不一致。" }
            require(!category.isDeleted || existing?.categoryId == categoryId) { "此分类已停用，请选择其他分类。" }
            require(!account.isDeleted || existing?.accountId == accountId) { "此账户已停用，请选择其他账户。" }
            val replacedAmount = existing?.takeIf { it.type == type }?.amountMinor ?: 0L
            try {
                Math.addExact(dao.activeTotal(type) - replacedAmount, amountMinor)
            } catch (error: ArithmeticException) {
                throw IllegalArgumentException("该收支类型累计金额超出可保存范围。", error)
            }
            val now = System.currentTimeMillis()
            val record = TransactionEntity(
                id = existing?.id ?: UUID.randomUUID().toString(), amountMinor = amountMinor,
                type = type, categoryId = categoryId, accountId = accountId,
                note = note?.trim()?.takeIf { it.isNotEmpty() }, occurredAt = occurredAt,
                createdAt = existing?.createdAt ?: now,
                updatedAt = maxOf(now, existing?.updatedAt ?: now, existing?.createdAt ?: now),
                source = existing?.source ?: source,
                importKey = existing?.importKey ?: importKey,
            )
            dao.saveTransaction(record)
            record.id
        }
        preferences.edit().putString(LAST_ACCOUNT_ID, accountId).apply()
        return savedId
    }

    suspend fun deleteTransaction(id: String) = db.withTransaction {
        val existing = dao.transaction(id) ?: return@withTransaction
        if (!existing.isDeleted) dao.saveTransaction(existing.copy(
            isDeleted = true, updatedAt = maxOf(System.currentTimeMillis(), existing.updatedAt, existing.createdAt),
        ))
    }

    suspend fun undoDeleteTransaction(id: String) = db.withTransaction {
        val existing = requireNotNull(dao.transaction(id)) { "这笔记录已不存在。" }
        if (!existing.isDeleted) return@withTransaction
        requireNotNull(dao.category(existing.categoryId)) { "原分类已不存在。" }
        requireNotNull(dao.account(existing.accountId)) { "原账户已不存在。" }
        try {
            Math.addExact(dao.activeTotal(existing.type), existing.amountMinor)
        } catch (error: ArithmeticException) {
            throw IllegalArgumentException("累计金额超出范围，无法撤销删除。", error)
        }
        dao.saveTransaction(existing.copy(isDeleted = false,
            updatedAt = maxOf(System.currentTimeMillis(), existing.updatedAt, existing.createdAt)))
    }

    suspend fun saveCategory(id: String?, name: String, type: Int) = db.withTransaction {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "请输入分类名称。" }
        require(type == EXPENSE || type == INCOME) { "收支类型无效。" }
        val existing = id?.let { requireNotNull(dao.category(it)) { "此分类已不存在。" } }
        require(existing == null || existing.type == type ||
            (!dao.categoryHasTransactions(existing.id) && !db.planningDao().categoryHasRules(existing.id))) {
            "已用于记账或订阅计划的分类不能更改收支类型。"
        }
        val sortOrder = existing?.sortOrder ?: Math.addExact(dao.allCategories().filter { it.type == type }.maxOfOrNull { it.sortOrder } ?: -1, 1)
        dao.saveCategory(existing?.copy(name = trimmed, type = type) ?: CategoryEntity(
            id = UUID.randomUUID().toString(), name = trimmed, type = type, sortOrder = sortOrder,
        ))
    }

    suspend fun moveCategory(id: String, direction: Int) = db.withTransaction {
        require(direction == -1 || direction == 1) { "排序方向无效。" }
        val category = requireNotNull(dao.category(id)) { "此分类已不存在。" }
        require(!category.isDeleted) { "此分类已停用。" }
        val ordered = dao.allCategories().filter { it.type == category.type && !it.isDeleted }.toMutableList()
        val index = ordered.indexOfFirst { it.id == id }
        val target = index + direction
        if (target !in ordered.indices) return@withTransaction
        val neighbor = ordered[target]
        ordered[target] = category
        ordered[index] = neighbor
        ordered.forEachIndexed { order, item ->
            if (item.sortOrder != order) dao.saveCategory(item.copy(sortOrder = order))
        }
    }

    suspend fun disableCategory(id: String) = db.withTransaction {
        val category = requireNotNull(dao.category(id)) { "此分类已不存在。" }
        dao.saveCategory(category.copy(isDeleted = true))
    }

    suspend fun snapshot(): LedgerSnapshot = db.withTransaction {
        LedgerSnapshot(
            dao.allCategories().map { it.toDomain() },
            dao.allAccounts().map { it.toDomain() },
            dao.allTransactions().map { it.toDomain() },
            db.assetDao().allAssets().map { it.toDomain() },
            db.memoDao().allMemos().map { it.toDomain() },
            db.planningDao().allBudgets().map { it.toDomain() },
            db.planningDao().allRules().map { it.toDomain() },
            db.wishDao().allWishes().map { it.toDomain() },
        )
    }

    suspend fun restore(snapshot: LedgerSnapshot) {
        validateSnapshot(snapshot)
        db.withTransaction {
            db.wishDao().clear()
            db.planningDao().clearRules()
            db.planningDao().clearBudgets()
            db.memoDao().clear()
            db.assetDao().clear()
            dao.clearTransactions()
            dao.clearCategories()
            dao.clearAccounts()
            dao.insertCategories(snapshot.categories.map { it.toEntity() })
            dao.insertAccounts(snapshot.accounts.map { it.toEntity() })
            dao.insertTransactions(snapshot.transactions.map { it.toEntity() })
            db.assetDao().insertAll(snapshot.assets.map { it.toEntity() })
            db.memoDao().insertAll(snapshot.memos.map { it.toEntity() })
            db.planningDao().insertBudgets(snapshot.budgets.map { it.toEntity() })
            db.planningDao().insertRules(snapshot.recurringRules.map { it.toEntity() })
            db.wishDao().insertAll(snapshot.wishes.map { it.toEntity() })
        }
        preferences.edit().remove(LAST_ACCOUNT_ID).apply()
    }

    suspend fun exportBackup(uri: Uri) = backupFiles.write(uri, snapshot())

    suspend fun readBackup(uri: Uri): LedgerSnapshot = backupFiles.read(uri)

    private companion object { const val LAST_ACCOUNT_ID = "last_account_id" }
}
