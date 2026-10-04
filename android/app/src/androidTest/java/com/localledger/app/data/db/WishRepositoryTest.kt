package com.localledger.app.data.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.localledger.app.data.backup.BackupCodec
import com.localledger.app.data.backup.BackupFiles
import com.localledger.app.data.repository.LedgerRepository
import com.localledger.app.data.repository.MemoRepository
import com.localledger.app.data.repository.WishRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WishRepositoryTest {
    @Test fun progressHistoryBackupRestoreAndOverflowKeepDataConsistent() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java).build()
        try {
            db.seedDefaults()
            val ledger = LedgerRepository(db, context.getSharedPreferences("wish-backup-test", 0), BackupFiles(context.contentResolver))
            val repository = WishRepository(db)
            MemoRepository(db).save(null, "Keep memo", "content", true, false)
            repository.save(null, " Camera ", 50000, 10000, " keep ")
            val first = repository.wishes.first().single()
            assertEquals("Camera", first.name)
            repository.setSaved(first.id, 20000)
            assertEquals(30000L, requireNotNull(repository.wish(first.id)).remainingMinor)
            repository.setPurchased(first.id, true)
            repository.save(first.id, "New camera", 60000, 25000, "note")
            val edited = requireNotNull(repository.wish(first.id))
            assertTrue(edited.isPurchased)
            assertEquals(first.createdAt, edited.createdAt)
            repository.delete(first.id)
            assertNull(repository.wish(first.id))
            repository.save(null, "Bike", 30000, 20000, null)
            val original = ledger.snapshot()
            assertTrue(original.wishes.first { it.id == first.id }.isDeleted)
            assertEquals(original, BackupCodec.decode(BackupCodec.encode(original)))
            repository.setPurchased(repository.wishes.first().single().id, true)
            ledger.restore(original)
            assertEquals(original, ledger.snapshot())
            ledger.restore(original.copy(wishes = emptyList()))
            assertTrue(db.wishDao().allWishes().isEmpty())
            assertEquals(original.memos, ledger.snapshot().memos)
            repository.save(null, "Maximum", Long.MAX_VALUE, 0, null)
            val before = ledger.snapshot()
            var rejected = false
            try { repository.save(null, "Overflow", 1, 0, null) }
            catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            assertEquals(before, ledger.snapshot())
            rejected = false
            try { ledger.restore(before.copy(wishes = before.wishes + before.wishes)) }
            catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            assertEquals(before, ledger.snapshot())
        } finally { db.close() }
    }
}
