package com.localledger.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetDao {
    @Query("SELECT * FROM assets WHERE isDeleted = 1") fun deleted(): Flow<List<AssetEntity>>
    @Query("SELECT * FROM assets WHERE isDeleted = 0 ORDER BY valueMinor DESC, id")
    fun observeAssets(): Flow<List<AssetEntity>>
    @Query("SELECT * FROM assets WHERE id = :id") suspend fun asset(id: String): AssetEntity?
    @Query("SELECT * FROM assets ORDER BY id") suspend fun allAssets(): List<AssetEntity>
    @Query("SELECT COALESCE(SUM(valueMinor), 0) FROM assets WHERE isDeleted = 0 AND status IN ('持有中', '闲置中')") suspend fun total(): Long
    @Upsert suspend fun save(asset: AssetEntity)
    @Insert suspend fun insertAll(assets: List<AssetEntity>)
    @Query("DELETE FROM assets") suspend fun clear()
}
