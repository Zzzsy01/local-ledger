package com.localledger.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetDao {
    @Query("SELECT asset_records.* FROM asset_records JOIN assets ON assets.id = asset_records.assetId WHERE assets.isDeleted = 0 ORDER BY occurredAt DESC, createdAt DESC, asset_records.id")
    fun observeRecords(): Flow<List<AssetRecordEntity>>
    @Query("SELECT * FROM asset_records ORDER BY id") suspend fun allRecords(): List<AssetRecordEntity>
    @Query("SELECT * FROM asset_records WHERE id = :id") suspend fun record(id: String): AssetRecordEntity?
    @Upsert suspend fun saveRecord(record: AssetRecordEntity)
    @Insert suspend fun insertRecords(records: List<AssetRecordEntity>)
    @Query("DELETE FROM asset_records") suspend fun clearRecords()
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
