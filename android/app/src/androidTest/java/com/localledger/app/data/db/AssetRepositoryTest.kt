package com.localledger.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.localledger.app.data.backup.BackupCodec
import com.localledger.app.data.backup.BackupFiles
import com.localledger.app.data.repository.AssetRepository
import com.localledger.app.data.repository.LedgerRepository
import com.localledger.app.domain.MonthlySummary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssetRepositoryTest {
    @Test fun assetCrudAndCompleteBackupKeepLedgerIndependent() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java).build()
        try {
            val ledger = LedgerRepository(db, context.getSharedPreferences("assets-test", Context.MODE_PRIVATE), BackupFiles(context.contentResolver))
            ledger.initialize()
            val repository = AssetRepository(db)
            repository.save(null, "Camera", "摄影", 5000, 2100, 10, listOf(2000, 2200), "used")
            val original = repository.assets.first().single()
            assertEquals(listOf(2000L, 2200L), original.referencePricesMinor)
            assertEquals(MonthlySummary(), ledger.summary(0, 100).first())
            val snapshot = ledger.snapshot()
            assertEquals(snapshot, BackupCodec.decode(BackupCodec.encode(snapshot)))
            repository.save(original.id, "Camera", "摄影", 5000, 0, 20, emptyList(), null)
            assertEquals(0L, repository.assets.first().single().valueMinor)
            repository.delete(original.id)
            assertTrue(repository.assets.first().isEmpty())
            assertTrue(ledger.snapshot().assets.single().isDeleted)
            ledger.restore(snapshot)
            assertEquals(original, repository.assets.first().single())
            var rejected = false
            try { ledger.restore(snapshot.copy(assets = listOf(original.copy(valueMinor = -1)))) }
            catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            assertEquals(snapshot, ledger.snapshot())
            val legacy = org.json.JSONObject(BackupCodec.encode(snapshot)).put("version", 1).apply { remove("assets") }
            ledger.restore(BackupCodec.decode(legacy.toString()))
            assertTrue(repository.assets.first().isEmpty())
        } finally { db.close() }
    }

    @Test fun totalOverflowRollsBackAssetWrite() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java).build()
        try {
            val repository = AssetRepository(db)
            repository.save(null, "Large", "其他", null, Long.MAX_VALUE, 10, emptyList(), null)
            var rejected = false
            try { repository.save(null, "Next", "其他", null, 1, 10, emptyList(), null) }
            catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            assertEquals(1, repository.assets.first().size)
        } finally { db.close() }
    }
}
