package com.localledger.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.localledger.app.data.backup.*
import com.localledger.app.data.repository.*
import com.localledger.app.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class AssetArchiveIntegrationTest {
    @Test fun movesValuationsManualEditsAndBackupKeepHistoryAndCurrentStateTogether() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java).build()
        try {
            val ledger = LedgerRepository(db, context.getSharedPreferences("archive-test", Context.MODE_PRIVATE), BackupFiles(context.contentResolver))
            ledger.initialize()
            val repo = AssetRepository(db)
            repo.save(null, "旅行相机", "摄影", 100000, 0, 0, emptyList(), "毕业礼物", location = "办公室", tags = listOf("旅行"), serialNumber = "SN-123", isValueKnown = false)
            val asset = repo.assets.first().single()
            assertNull(repo.records.first().single().amountMinor)
            repo.move(asset.id, "书房第二层")
            repo.revalue(asset.id, 50000, LocalDate.now())
            repo.favorite(asset.id, true)
            val current = repo.assets.first().single()
            assertEquals("书房第二层", current.location); assertTrue(current.isValueKnown); assertTrue(current.isFavorite)
            val movement = repo.records.first().single { it.kind == "位置变更" }
            assertEquals("办公室", movement.fromLocation); assertEquals(current.location, movement.toLocation)
            assertTrue(assetMatches(current, "相机 办公室", repo.records.first()))
            repo.saveRecord(asset.id, null, "保养", "清洁镜头", LocalDate.now())
            val manual = repo.records.first().single { it.kind == "保养" }
            repo.saveRecord(asset.id, manual.id, "保养", "清洁镜头与传感器", LocalDate.now())
            assertEquals(4, repo.records.first().size)
            assertEquals("清洁镜头与传感器", repo.records.first().single { it.id == manual.id }.content)
            try { repo.saveRecord(asset.id, movement.id, "随记", "覆盖历史", LocalDate.now()); fail("Automatic records are immutable") } catch (_: IllegalArgumentException) { }
            val snapshot = BackupCodec.decode(BackupCodec.encode(ledger.snapshot()))
            repo.delete(asset.id); assertTrue(repo.records.first().isEmpty()); assertEquals(4, ledger.snapshot().assetRecords.size)
            ledger.restore(snapshot); assertEquals(snapshot, ledger.snapshot())
            assertEquals(MonthlySummary(), ledger.summary(0, Long.MAX_VALUE).first())
        } finally { db.close() }
    }
    @Test fun failedValuationDoesNotLeaveHistoryOrChangeUnknownValue() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java).build()
        try {
            val repo = AssetRepository(db)
            repo.save(null, "Big", "其他", null, Long.MAX_VALUE, 0, emptyList(), null)
            repo.save(null, "Unknown", "其他", null, 0, 0, emptyList(), null, isValueKnown = false)
            val item = repo.assets.first().first { it.name == "Unknown" }
            val before = repo.records.first()
            try { repo.revalue(item.id, 1, LocalDate.now()); fail("Overflow must reject atomically") } catch (_: IllegalArgumentException) { }
            assertEquals(item, repo.asset(item.id)); assertEquals(before, repo.records.first())
        } finally { db.close() }
    }
}
