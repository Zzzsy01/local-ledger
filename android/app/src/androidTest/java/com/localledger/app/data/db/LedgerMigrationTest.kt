package com.localledger.app.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LedgerMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), LedgerDatabase::class.java)
    @Test fun migrationFromOneRetainsAccountsCategoriesAndDeletedHistory() {
        val name = "migration-assets-test.db"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        try {
            helper.createDatabase(name, 1).apply {
                execSQL("INSERT INTO accounts VALUES ('00000000-0000-4000-8000-000000000001', 'cash', 0)")
                execSQL("INSERT INTO categories VALUES ('10000000-0000-4000-8000-000000000001', 'food', 0, NULL, 0, 1)")
                execSQL("INSERT INTO transactions VALUES ('20000000-0000-4000-8000-000000000001', 12345, 0, '10000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000001', 'keep', 10, 10, 20, 1, 8)")
                close()
            }
            helper.runMigrationsAndValidate(name, 2, true).apply {
                query("SELECT amountMinor, note, isDeleted, source FROM transactions").use {
                    assertTrue(it.moveToFirst()); assertEquals(12345L, it.getLong(0)); assertEquals("keep", it.getString(1))
                    assertEquals(1, it.getInt(2)); assertEquals(8, it.getInt(3))
                }
                query("SELECT COUNT(*) FROM assets").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
                query("SELECT isDeleted FROM categories").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
                query("SELECT name FROM accounts").use { assertTrue(it.moveToFirst()); assertEquals("cash", it.getString(0)) }
                close()
            }
        } finally { context.deleteDatabase(name) }
    }
    @Test fun migrationFromInstalledVersionTwoRetainsAssetsAndAddsNewTables() {
        val name = "migration-features-test.db"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        try {
            helper.createDatabase(name, 2).apply {
                execSQL("INSERT INTO accounts VALUES ('00000000-0000-4000-8000-000000000001', 'cash', 0)")
                execSQL("INSERT INTO categories VALUES ('10000000-0000-4000-8000-000000000001', 'food', 0, NULL, 0, 1)")
                execSQL("INSERT INTO transactions VALUES ('20000000-0000-4000-8000-000000000001', 12345, 0, '10000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000001', 'keep', 10, 10, 20, 1, 8)")
                execSQL("INSERT INTO assets VALUES ('30000000-0000-4000-8000-000000000001', 'camera', 'photo', 30000, 15000, 10, '10000,20000', 'keep', 10, 20, 0)")
                close()
            }
            helper.runMigrationsAndValidate(name, 3, true).apply {
                query("SELECT amountMinor,isDeleted,importKey FROM transactions").use { assertTrue(it.moveToFirst()); assertEquals(12345L,it.getLong(0)); assertEquals(1,it.getInt(1)); assertTrue(it.isNull(2)) }
                query("SELECT valueMinor,referencePricesMinor FROM assets").use { assertTrue(it.moveToFirst()); assertEquals(15000L,it.getLong(0)); assertEquals("10000,20000",it.getString(1)) }
                for (table in listOf("memos","budgets","recurring_rules")) query("SELECT COUNT(*) FROM $table").use { assertTrue(it.moveToFirst()); assertEquals(0,it.getInt(0)) }
                close()
            }
        } finally { context.deleteDatabase(name) }
    }
}
