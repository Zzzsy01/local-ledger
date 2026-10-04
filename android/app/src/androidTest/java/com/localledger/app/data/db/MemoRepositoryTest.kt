package com.localledger.app.data.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.localledger.app.data.repository.MemoRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MemoRepositoryTest {
    @Test fun editsAndStatusChangesKeepIdentityAndDeletedRecords() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java).build()
        try {
            val repository = MemoRepository(db)
            repository.save(null, " First ", " original ", false, false)
            val first = repository.memos.first().single()
            assertEquals("First", first.title)
            repository.save(null, "Second", "", true, false)
            assertEquals("Second", repository.memos.first().first().title)
            repository.save(first.id, "Renamed", "edited", false, true)
            val edited = requireNotNull(repository.memo(first.id))
            assertEquals(first.id, edited.id)
            assertEquals(first.createdAt, edited.createdAt)
            assertTrue(edited.isDone)
            assertTrue(edited.updatedAt >= first.updatedAt)
            repository.setPinned(first.id, true)
            repository.setDone(first.id, false)
            assertFalse(requireNotNull(repository.memo(first.id)).isDone)
            assertTrue(requireNotNull(repository.memo(first.id)).isPinned)
            var rejected = false
            try { repository.save(first.id, "  ", "", false, false) }
            catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            assertEquals("Renamed", requireNotNull(repository.memo(first.id)).title)
            repository.delete(first.id)
            assertNull(repository.memo(first.id))
            assertEquals("Second", repository.memos.first().single().title)
            val deleted = db.memoDao().allMemos().single { it.id == first.id }
            assertTrue(deleted.isDeleted)
            rejected = false
            try { repository.save(first.id, "Revive", "", false, false) }
            catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
        } finally { db.close() }
    }
}
