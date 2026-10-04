package com.localledger.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.localledger.app.domain.CategoryTotal
import com.localledger.app.domain.MonthlySummary
import com.localledger.app.domain.AccountTotal
import com.localledger.app.domain.ReportMetrics
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    @Query("SELECT * FROM categories ORDER BY type, sortOrder, id")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM accounts ORDER BY id")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("""
        SELECT transactions.*, categories.name AS categoryName, accounts.name AS accountName
        FROM transactions
        JOIN categories ON categories.id = transactions.categoryId
        JOIN accounts ON accounts.id = transactions.accountId
        WHERE transactions.isDeleted = 0 AND occurredAt >= :start AND occurredAt < :end
        ORDER BY occurredAt DESC, createdAt DESC, transactions.id DESC
    """)
    fun observeEntries(start: Long, end: Long): Flow<List<LedgerEntryRow>>

    @Query("""
        SELECT transactions.*, categories.name AS categoryName, accounts.name AS accountName
        FROM transactions
        JOIN categories ON categories.id = transactions.categoryId
        JOIN accounts ON accounts.id = transactions.accountId
        WHERE transactions.isDeleted = 0 AND occurredAt >= :start AND occurredAt < :end
          AND (:type IS NULL OR transactions.type = :type)
          AND (:categoryId IS NULL OR transactions.categoryId = :categoryId)
          AND (:accountId IS NULL OR transactions.accountId = :accountId)
          AND (:minimum IS NULL OR amountMinor >= :minimum)
          AND (:maximum IS NULL OR amountMinor <= :maximum)
          AND (:keyword = '' OR instr(lower(COALESCE(note, '') || ' ' || categories.name || ' ' || accounts.name), lower(:keyword)) > 0)
        ORDER BY occurredAt DESC, createdAt DESC, transactions.id DESC
    """)
    fun observeFilteredEntries(start: Long, end: Long, keyword: String, type: Int?, categoryId: String?,
        accountId: String?, minimum: Long?, maximum: Long?): Flow<List<LedgerEntryRow>>

    @Query("""
        SELECT COALESCE(SUM(CASE WHEN type = 1 THEN amountMinor ELSE 0 END), 0) AS income,
               COALESCE(SUM(CASE WHEN type = 0 THEN amountMinor ELSE 0 END), 0) AS expense
        FROM transactions WHERE isDeleted = 0 AND occurredAt >= :start AND occurredAt < :end
    """)
    fun observeSummary(start: Long, end: Long): Flow<MonthlySummary>

    @Query("""
        SELECT categories.id AS categoryId, categories.name AS name, transactions.type AS type,
               SUM(transactions.amountMinor) AS total
        FROM transactions JOIN categories ON categories.id = transactions.categoryId
        WHERE transactions.isDeleted = 0 AND occurredAt >= :start AND occurredAt < :end
              AND transactions.type = :type
        GROUP BY categories.id, categories.name, transactions.type
        ORDER BY total DESC, categories.id
    """)
    fun observeCategoryTotals(start: Long, end: Long, type: Int): Flow<List<CategoryTotal>>

    @Query("""
        SELECT accounts.id AS accountId, accounts.name AS name, SUM(transactions.amountMinor) AS total
        FROM transactions JOIN accounts ON accounts.id = transactions.accountId
        WHERE transactions.isDeleted = 0 AND occurredAt >= :start AND occurredAt < :end AND transactions.type = :type
        GROUP BY accounts.id, accounts.name ORDER BY total DESC, accounts.id
    """)
    fun observeAccountTotals(start: Long, end: Long, type: Int): Flow<List<AccountTotal>>

    @Query("""
        SELECT COUNT(*) AS transactionCount,
               COALESCE(SUM(CASE WHEN type = 0 THEN 1 ELSE 0 END), 0) AS expenseCount,
               COALESCE(MAX(CASE WHEN type = 0 THEN amountMinor ELSE 0 END), 0) AS largestExpense
        FROM transactions WHERE isDeleted = 0 AND occurredAt >= :start AND occurredAt < :end
    """)
    fun observeReportMetrics(start: Long, end: Long): Flow<ReportMetrics>

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM transactions WHERE isDeleted = 0 AND type = :type")
    suspend fun activeTotal(type: Int): Long

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun transaction(id: String): TransactionEntity?

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun category(id: String): CategoryEntity?

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun account(id: String): AccountEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE categoryId = :id)")
    suspend fun categoryHasTransactions(id: String): Boolean

    @Query("SELECT * FROM categories ORDER BY type, sortOrder, id")
    suspend fun allCategories(): List<CategoryEntity>

    @Query("SELECT * FROM accounts ORDER BY id")
    suspend fun allAccounts(): List<AccountEntity>

    @Query("SELECT * FROM transactions ORDER BY id")
    suspend fun allTransactions(): List<TransactionEntity>

    @Query("SELECT (SELECT COUNT(*) FROM categories) + (SELECT COUNT(*) FROM accounts) + (SELECT COUNT(*) FROM transactions)")
    suspend fun totalRowCount(): Long

    @Upsert
    suspend fun saveCategory(category: CategoryEntity)

    @Upsert
    suspend fun saveTransaction(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAccounts(accounts: List<AccountEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)

    @Query("DELETE FROM transactions")
    suspend fun clearTransactions()

    @Query("DELETE FROM categories")
    suspend fun clearCategories()

    @Query("DELETE FROM accounts")
    suspend fun clearAccounts()
}
