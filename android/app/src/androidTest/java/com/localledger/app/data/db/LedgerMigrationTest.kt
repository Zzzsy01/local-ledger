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
    @Test fun migrationFromFivePreservesArchivesWithoutInventingHistory() {
        val name = "migration-archive-test.db"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        try {
            helper.createDatabase(name, 5).apply {
                execSQL("INSERT INTO assets (id,name,kind,purchaseMinor,valueMinor,pricedAt,referencePricesMinor,note,createdAt,updatedAt,isDeleted,location,purchaseDate) VALUES ('30000000-0000-4000-8000-000000000001','camera','摄影',50000,20000,10,'','keep',10,20,0,'书房第二层','2026-01-01')")
                close()
            }
            helper.runMigrationsAndValidate(name, 6, true).apply {
                query("SELECT name,purchaseMinor,valueMinor,location,purchaseDate,tags,serialNumber,isFavorite,isValueKnown FROM assets").use {
                    assertTrue(it.moveToFirst()); assertEquals("camera",it.getString(0)); assertEquals(50000L,it.getLong(1)); assertEquals(20000L,it.getLong(2))
                    assertEquals("书房第二层",it.getString(3)); assertEquals("2026-01-01",it.getString(4)); assertEquals("[]",it.getString(5))
                    assertTrue(it.isNull(6)); assertEquals(0,it.getInt(7)); assertEquals(1,it.getInt(8))
                }
                query("SELECT COUNT(*) FROM asset_records").use { assertTrue(it.moveToFirst()); assertEquals(0,it.getInt(0)) }
                query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
                close()
            }
        } finally { context.deleteDatabase(name) }
    }
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), LedgerDatabase::class.java)

    @Test fun migrationFromFourPreservesOldColumnsAndAddsLifeAndTransferFields() {
        val name = "migration-life-test.db"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        try {
            helper.createDatabase(name, 4).apply {
                execSQL("INSERT INTO accounts VALUES ('00000000-0000-4000-8000-000000000001','cash',0)")
                execSQL("INSERT INTO categories VALUES ('10000000-0000-4000-8000-000000000001','food',0,NULL,0,1)")
                execSQL("INSERT INTO transactions VALUES ('20000000-0000-4000-8000-000000000001',12345,0,'10000000-0000-4000-8000-000000000001','00000000-0000-4000-8000-000000000001','keep',10,10,20,1,8,'csv-kept')")
                execSQL("INSERT INTO assets VALUES ('30000000-0000-4000-8000-000000000001','camera','摄影',30000,15000,10,'10000,20000','keep',10,20,0)")
                execSQL("INSERT INTO memos VALUES ('40000000-0000-4000-8000-000000000001','memo','keep',1,1,10,20,0)")
                execSQL("INSERT INTO wishes VALUES ('50000000-0000-4000-8000-000000000001','camera',30000,15000,'keep',0,10,20,0)")
                close()
            }
            helper.runMigrationsAndValidate(name, 5, true).apply {
                query("SELECT amountMinor,note,isDeleted,source,importKey,transferAccountId,isReimbursable FROM transactions").use {
                    assertTrue(it.moveToFirst());assertEquals(12345,it.getInt(0));assertEquals("keep",it.getString(1));assertEquals(1,it.getInt(2));assertEquals(8,it.getInt(3));assertEquals("csv-kept",it.getString(4));assertTrue(it.isNull(5));assertEquals(0,it.getInt(6))
                }
                query("SELECT name,valueMinor,quantity,status,photoPaths FROM assets").use { assertTrue(it.moveToFirst());assertEquals("camera",it.getString(0));assertEquals(15000,it.getInt(1));assertEquals(1,it.getInt(2));assertEquals("持有中",it.getString(3));assertEquals("[]",it.getString(4)) }
                query("SELECT content,isDone,kind,color,folder FROM memos").use { assertTrue(it.moveToFirst());assertEquals("keep",it.getString(0));assertEquals(1,it.getInt(1));assertEquals("note",it.getString(2));assertEquals("yellow",it.getString(3));assertEquals("默认",it.getString(4)) }
                query("SELECT savedMinor FROM wishes").use { assertTrue(it.moveToFirst());assertEquals(15000,it.getInt(0)) }
                query("SELECT type FROM categories WHERE id='00000000-0000-0000-0000-000000000002'").use { assertTrue(it.moveToFirst());assertEquals(2,it.getInt(0)) }
                for (table in listOf("life_items","life_check_ins","focus_sessions","study_cards","payment_candidates")) query("SELECT COUNT(*) FROM $table").use { assertTrue(it.moveToFirst());assertEquals(0,it.getInt(0)) }
                query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
                close()
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun migrationFromThreePreservesSevenTablesAndAddsEmptyWishlist() {
        val name = "migration-wishes-test.db"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tables = listOf("accounts", "categories", "transactions", "assets", "memos", "budgets", "recurring_rules")
        fun rows(database: androidx.sqlite.db.SupportSQLiteDatabase, table: String) = database.query("SELECT * FROM $table ORDER BY 1").use { cursor ->
            buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).map { if (cursor.isNull(it)) null else cursor.getString(it) }) }
        }
        try {
            val previous = helper.createDatabase(name, 3)
            previous.execSQL("INSERT INTO accounts VALUES ('00000000-0000-4000-8000-000000000001', 'cash', 0)")
            previous.execSQL("INSERT INTO categories VALUES ('10000000-0000-4000-8000-000000000001', 'food', 0, NULL, 0, 1)")
            previous.execSQL("INSERT INTO transactions VALUES ('20000000-0000-4000-8000-000000000001', 12345, 0, '10000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000001', 'keep', 10, 10, 20, 1, 8, 'csv-kept')")
            previous.execSQL("INSERT INTO assets VALUES ('30000000-0000-4000-8000-000000000001', 'camera', 'photo', 30000, 15000, 10, '10000,20000', 'keep', 10, 20, 0)")
            previous.execSQL("INSERT INTO memos VALUES ('40000000-0000-4000-8000-000000000001', 'memo', 'keep', 1, 1, 10, 20, 0)")
            previous.execSQL("INSERT INTO budgets VALUES ('2026-10', 200000, 20)")
            previous.execSQL("INSERT INTO recurring_rules VALUES ('50000000-0000-4000-8000-000000000001', 'rent', 2000, 0, '10000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000001', 30, 31, 'keep', 10, 20, 1)")
            val original = tables.associateWith { rows(previous, it) }
            previous.close()
            helper.runMigrationsAndValidate(name, 4, true).apply {
                tables.forEach { assertEquals(original[it], rows(this, it)) }
                assertTrue(rows(this, "wishes").isEmpty())
                close()
            }
        } finally { context.deleteDatabase(name) }
    }
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
