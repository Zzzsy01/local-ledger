package com.localledger.app.data.db

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.localledger.app.data.backup.BackupFiles
import com.localledger.app.data.repository.LedgerRepository
import com.localledger.app.domain.Account
import com.localledger.app.domain.Category
import com.localledger.app.domain.EXPENSE
import com.localledger.app.domain.INCOME
import com.localledger.app.domain.LedgerSnapshot
import com.localledger.app.domain.MonthlySummary
import com.localledger.app.domain.LedgerFilter
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LedgerRepositoryTest {
    private lateinit var db: LedgerDatabase
    private lateinit var repository: LedgerRepository
    private lateinit var expense: Category
    private lateinit var income: Category
    private lateinit var account: Account

    @Before fun setup() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("ledger-repository-test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java).build()
        repository = LedgerRepository(db, preferences, BackupFiles(context.contentResolver))
        repository.initialize()
        expense = repository.categories.first().first { it.type == EXPENSE }
        income = repository.categories.first().first { it.type == INCOME }
        account = repository.accounts.first().first()
    }

    @After fun close() { db.close() }

    @Test fun archivedCategoryKeepsHistoryAndEditsButCannotReceiveNewEntries() = runBlocking {
        val id = repository.saveTransaction(null, 1200, EXPENSE, expense.id, account.id, "lunch", 10)
        repository.disableCategory(expense.id)
        assertEquals(expense.name, repository.entries(0, 100).first().single().categoryName)
        assertEquals(1200L, repository.summary(0, 100).first().expense)
        assertEquals(1200L, repository.categoryTotals(0, 100, EXPENSE).first().single().total)
        assertRejected { repository.saveTransaction(null, 100, EXPENSE, expense.id, account.id, null, 11) }
        repository.saveTransaction(id, 1500, EXPENSE, expense.id, account.id, null, 10)
        assertEquals(1500L, repository.summary(0, 100).first().expense)
        repository.deleteTransaction(id)
        assertEquals(0L, repository.summary(0, 100).first().expense)
        assertTrue(repository.entries(0, 100).first().isEmpty())
        assertTrue(repository.snapshot().transactions.single().isDeleted)
        repository.initialize()
        assertTrue(repository.categories.first().single { it.id == expense.id }.isDeleted)
    }

    @Test fun categoryTypeAndSqliteForeignKeysProtectReferencedHistory() = runBlocking {
        val id = repository.saveTransaction(null, 1, EXPENSE, expense.id, account.id, null, 10)
        repository.deleteTransaction(id)
        assertRejected { repository.saveCategory(expense.id, "changed", INCOME) }
        assertEquals(EXPENSE, db.ledgerDao().category(expense.id)!!.type)
        val dangling = TransactionEntity(
            UUID.randomUUID().toString(), 1, EXPENSE, UUID.randomUUID().toString(), account.id,
            occurredAt = 10, createdAt = 10, updatedAt = 10,
        )
        var rejected = false
        try { db.ledgerDao().saveTransaction(dangling) } catch (_: android.database.sqlite.SQLiteConstraintException) { rejected = true }
        assertTrue("SQLite must reject missing category", rejected)
        assertEquals(1, db.ledgerDao().allTransactions().size)
    }

    @Test fun rangesAreHalfOpenAndDeletedIncomeIsExcluded() = runBlocking {
        repository.saveTransaction(null, 100, EXPENSE, expense.id, account.id, null, 10)
        repository.saveTransaction(null, 200, EXPENSE, expense.id, account.id, null, 20)
        val incomeId = repository.saveTransaction(null, 300, INCOME, income.id, account.id, null, 15)
        assertEquals(MonthlySummary(300, 100), repository.summary(10, 20).first())
        assertEquals(2, repository.entries(10, 20).first().size)
        repository.deleteTransaction(incomeId)
        assertEquals(MonthlySummary(0, 100), repository.summary(10, 20).first())
    }

    @Test fun activeTotalsStayWithinLongRangeAcrossInsertEditAndTypeChanges() = runBlocking {
        val largeId = repository.saveTransaction(null, Long.MAX_VALUE - 5, EXPENSE, expense.id, account.id, null, 10)
        val smallId = repository.saveTransaction(null, 5, EXPENSE, expense.id, account.id, null, 10)
        assertEquals(Long.MAX_VALUE, repository.summary(0, 100).first().expense)
        assertRejected { repository.saveTransaction(null, 1, EXPENSE, expense.id, account.id, null, 10) }
        assertRejected { repository.saveTransaction(largeId, Long.MAX_VALUE - 4, EXPENSE, expense.id, account.id, null, 10) }
        repository.saveTransaction(largeId, Long.MAX_VALUE - 5, INCOME, income.id, account.id, null, 10)
        assertEquals(MonthlySummary(Long.MAX_VALUE - 5, 5), repository.summary(0, 100).first())
        repository.saveTransaction(largeId, Long.MAX_VALUE, INCOME, income.id, account.id, null, 10)
        repository.deleteTransaction(smallId)
        repository.saveTransaction(null, Long.MAX_VALUE, EXPENSE, expense.id, account.id, null, 10)
        assertEquals(MonthlySummary(Long.MAX_VALUE, Long.MAX_VALUE), repository.summary(0, 100).first())
    }

    @Test fun filtersReportsAndUndoUseActiveRowsAndPreserveArchivedReferences() = runBlocking {
        val otherAccount = repository.accounts.first().first { it.id != account.id }
        val matched = repository.saveTransaction(null, 1250, EXPENSE, expense.id, account.id, "Coffee 20%", 10)
        repository.saveTransaction(null, 2400, EXPENSE, expense.id, otherAccount.id, "other", 20)
        repository.saveTransaction(null, 5000, INCOME, income.id, account.id, "salary", 15)
        repository.disableCategory(expense.id)
        val filter = LedgerFilter(0, 20, "coffee 20%", EXPENSE, expense.id, account.id, 1200, 1300)
        assertEquals(matched, repository.filteredEntries(filter).first().single().transaction.id)
        assertEquals(3, repository.filteredEntries(LedgerFilter()).first().size)
        assertEquals(3650L, repository.accountTotals(0, 100, EXPENSE).first().sumOf { it.total })
        assertEquals(3L, repository.reportMetrics(0, 100).first().transactionCount)
        assertEquals(2L, repository.reportMetrics(0, 100).first().expenseCount)
        assertEquals(2400L, repository.reportMetrics(0, 100).first().largestExpense)
        repository.deleteTransaction(matched)
        assertTrue(repository.filteredEntries(filter).first().isEmpty())
        assertEquals(2400L, repository.summary(0, 100).first().expense)
        repository.undoDeleteTransaction(matched)
        assertEquals(matched, repository.filteredEntries(filter).first().single().transaction.id)
        assertEquals(3650L, repository.summary(0, 100).first().expense)
        assertTrue(repository.categories.first().single { it.id == expense.id }.isDeleted)
        assertRejected { repository.filteredEntries(LedgerFilter(minimum = 200, maximum = 100)) }
    }

    @Test fun undoOverflowDoesNotRestoreDeletedRowAndCopiesGetNewIdentityAndManualSource() = runBlocking {
        val original = repository.saveTransaction(null, 5, EXPENSE, expense.id, account.id, "imported", 10, source = 1, importKey = "test-import-key")
        repository.saveTransaction(original, 5, EXPENSE, expense.id, account.id, "edited", 10)
        assertEquals("test-import-key", repository.transaction(original)!!.importKey)
        val copy = repository.saveTransaction(null, 5, EXPENSE, expense.id, account.id, "edited", 11)
        assertFalse(original == copy)
        assertEquals(0, repository.transaction(copy)!!.source)
        assertEquals(null, repository.transaction(copy)!!.importKey)
        repository.deleteTransaction(original)
        repository.deleteTransaction(copy)
        repository.saveTransaction(null, Long.MAX_VALUE, EXPENSE, expense.id, account.id, null, 12)
        assertRejected { repository.undoDeleteTransaction(original) }
        assertTrue(repository.snapshot().transactions.single { it.id == original }.isDeleted)
        assertEquals(Long.MAX_VALUE, repository.summary(0, 100).first().expense)
    }

    @Test fun restorePreservesIdsAndBadRestoreKeepsCurrentDatabase() = runBlocking {
        val id = repository.saveTransaction(null, 321, EXPENSE, expense.id, account.id, "original", 10)
        repository.disableCategory(expense.id)
        val stored = repository.snapshot()
        val backup = stored.copy(
            categories = stored.categories.map { if (it.id == expense.id) it.copy(icon = "food", sortOrder = 42) else it }
                .sortedWith(compareBy({ it.type }, { it.sortOrder }, { it.id })),
            accounts = stored.accounts.map { if (it.id == account.id) it.copy(isDeleted = true) else it },
            transactions = stored.transactions.map { it.copy(source = 8) },
        )
        repository.deleteTransaction(id)
        repository.restore(backup)
        assertEquals(backup, repository.snapshot())
        assertFalse(repository.transaction(id)!!.isDeleted)
        assertRejected { repository.restore(backup.copy(categories = emptyList())) }
        assertEquals(backup, repository.snapshot())
        repository.restore(LedgerSnapshot(emptyList(), backup.accounts, emptyList()))
        repository.initialize()
        assertTrue(repository.categories.first().none { it.type != com.localledger.app.domain.TRANSFER })
        assertTrue(repository.snapshot().transactions.isEmpty())
    }

    @Test fun diskReopenAndNativeJsonRestorePreserveIdentityHistoryAndLongAmounts() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = UUID.randomUUID().toString()
        val databaseName = "ledger-persistence-test-$suffix.db"
        val preferencesName = "ledger-persistence-test-$suffix"
        val backupFile = File(context.cacheDir, "ledger-backup-test-$suffix.json")
        val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        var openedDatabase: LedgerDatabase? = null
        try {
            val originalDatabase = Room.databaseBuilder(context, LedgerDatabase::class.java, databaseName).build()
            openedDatabase = originalDatabase
            val original = LedgerRepository(originalDatabase, preferences, BackupFiles(context.contentResolver))
            original.initialize()
            val savedCategories = original.categories.first()
            val expenseCategory = savedCategories.first { it.type == EXPENSE }
            val incomeCategory = savedCategories.first { it.type == INCOME }
            val savedAccount = original.accounts.first().first()
            val largeAmount = 9_007_199_254_740_993L
            original.saveTransaction(null, largeAmount, INCOME, incomeCategory.id, savedAccount.id, "exact long", 10)
            original.saveTransaction(null, 375, EXPENSE, expenseCategory.id, savedAccount.id, "retained", 10)
            val deletedId = original.saveTransaction(null, 125, EXPENSE, expenseCategory.id, savedAccount.id, "deleted", 10)
            original.disableCategory(expenseCategory.id)
            original.deleteTransaction(deletedId)
            val saved = original.snapshot()
            original.exportBackup(Uri.fromFile(backupFile))
            val decoded = original.readBackup(Uri.fromFile(backupFile))
            assertEquals(saved, decoded)
            originalDatabase.close()
            openedDatabase = null

            val reopenedDatabase = Room.databaseBuilder(context, LedgerDatabase::class.java, databaseName).build()
            openedDatabase = reopenedDatabase
            val reopened = LedgerRepository(reopenedDatabase, preferences, BackupFiles(context.contentResolver))
            reopened.initialize()
            assertEquals(saved, reopened.snapshot())
            assertEquals(MonthlySummary(largeAmount, 375), reopened.summary(0, 100).first())
            assertTrue(reopened.categories.first().single { it.id == expenseCategory.id }.isDeleted)
            assertTrue(reopened.snapshot().transactions.single { it.id == deletedId }.isDeleted)
            reopened.saveTransaction(null, 1, INCOME, incomeCategory.id, savedAccount.id, "after reopen", 10)
            reopened.restore(decoded)
            assertEquals(saved, reopened.snapshot())
            assertEquals(MonthlySummary(largeAmount, 375), reopened.summary(0, 100).first())
        } finally {
            openedDatabase?.close()
            context.deleteDatabase(databaseName)
            context.deleteSharedPreferences(preferencesName)
            backupFile.delete()
        }
    }

    private suspend fun assertRejected(action: suspend () -> Unit) {
        var rejected = false
        try { action() } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue("invalid ledger input must be rejected", rejected)
    }
}
