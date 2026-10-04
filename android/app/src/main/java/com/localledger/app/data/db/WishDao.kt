package com.localledger.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WishDao {
    @Query("SELECT * FROM wishes WHERE isDeleted = 1") fun deleted(): Flow<List<WishEntity>>
    @Query("SELECT * FROM wishes WHERE isDeleted = 0 ORDER BY isPurchased, updatedAt DESC, id")
    fun observeWishes(): Flow<List<WishEntity>>
    @Query("SELECT * FROM wishes WHERE id = :id") suspend fun wish(id: String): WishEntity?
    @Query("SELECT * FROM wishes ORDER BY id") suspend fun allWishes(): List<WishEntity>
    @Upsert suspend fun save(wish: WishEntity)
    @Insert suspend fun insertAll(wishes: List<WishEntity>)
    @Query("DELETE FROM wishes") suspend fun clear()
}
