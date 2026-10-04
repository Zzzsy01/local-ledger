package com.localledger.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.localledger.app.data.backup.BackupFiles
import com.localledger.app.data.repository.LedgerRepository
import com.localledger.app.data.repository.PlanningRepository
import com.localledger.app.domain.EXPENSE
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlanningRepositoryTest {
    @Test fun budgetsAndMonthlyConfirmationAreAtomicAndCannotRecordSamePeriodTwice() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java).build()
        try {
            val ledger = LedgerRepository(db, context.getSharedPreferences("planning-test", Context.MODE_PRIVATE), BackupFiles(context.contentResolver))
            ledger.initialize()
            val repository = PlanningRepository(db, ledger)
            val category = ledger.categories.first().first { it.type == EXPENSE }
            val account = ledger.accounts.first().first()
            repository.saveBudget("2025-01", 150000)
            repository.saveBudget("2025-01", 180000)
            assertEquals(180000L, repository.budget("2025-01").first()!!.amountMinor)
            assertEquals(1, db.planningDao().allBudgets().size)
            val january = LocalDate.of(2025, 1, 31).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            repository.saveRule(null, "Rent", 12345, EXPENSE, category.id, account.id, january, 31, null)
            val rule = repository.rules.first().single()
            repository.processRule(rule.id, january, true)
            val recorded = ledger.snapshot().transactions.single()
            assertEquals(12345L, recorded.amountMinor)
            assertEquals(january, recorded.occurredAt)
            var rejected = false
            try { repository.processRule(rule.id, january, true) }
            catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            assertEquals(1, ledger.snapshot().transactions.size)
            val february = repository.rules.first().single()
            assertEquals(LocalDate.of(2025, 2, 28), java.time.Instant.ofEpochMilli(february.nextDueAt).atZone(ZoneId.systemDefault()).toLocalDate())
            repository.processRule(rule.id, february.nextDueAt, false)
            assertEquals(1, ledger.snapshot().transactions.size)
            val march = repository.rules.first().single()
            assertEquals(LocalDate.of(2025, 3, 31), java.time.Instant.ofEpochMilli(march.nextDueAt).atZone(ZoneId.systemDefault()).toLocalDate())
            ledger.disableCategory(category.id)
            rejected = false
            try { repository.processRule(rule.id, march.nextDueAt, true) }
            catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            assertEquals(march, repository.rules.first().single())
            assertEquals(1, ledger.snapshot().transactions.size)
            repository.disableRule(rule.id)
            assertTrue(repository.rules.first().isEmpty())
            assertTrue(db.planningDao().allRules().single().isDeleted)
        } finally { db.close() }
    }

    @Test fun transactionOverflowLeavesRuleUnprocessed() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java).build()
        try {
            val ledger = LedgerRepository(db, context.getSharedPreferences("planning-overflow-test", Context.MODE_PRIVATE), BackupFiles(context.contentResolver))
            ledger.initialize()
            val repository = PlanningRepository(db, ledger)
            val category = ledger.categories.first().first { it.type == EXPENSE }
            val account = ledger.accounts.first().first()
            ledger.saveTransaction(null, Long.MAX_VALUE, EXPENSE, category.id, account.id, null, 0)
            repository.saveRule(null, "Fee", 1, EXPENSE, category.id, account.id, 0, 1, null)
            val rule = repository.rules.first().single()
            var rejected = false
            try { repository.processRule(rule.id, rule.nextDueAt, true) }
            catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            assertEquals(rule, repository.rules.first().single())
            assertEquals(1, ledger.snapshot().transactions.size)
        } finally { db.close() }
    }
}
