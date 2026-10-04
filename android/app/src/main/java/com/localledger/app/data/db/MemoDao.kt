package com.localledger.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoDao {
    @Query("SELECT * FROM memos WHERE isDeleted = 0 ORDER BY isPinned DESC, isDone, updatedAt DESC, id")
    fun observeMemos(): Flow<List<MemoEntity>>
    @Query("SELECT * FROM memos WHERE id = :id") suspend fun memo(id: String): MemoEntity?
    @Query("SELECT * FROM memos ORDER BY id") suspend fun allMemos(): List<MemoEntity>
    @Upsert suspend fun save(memo: MemoEntity)
    @Insert suspend fun insertAll(memos: List<MemoEntity>)
    @Query("DELETE FROM memos") suspend fun clear()
}
