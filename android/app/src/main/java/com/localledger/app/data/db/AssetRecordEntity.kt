package com.localledger.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.localledger.app.domain.AssetRecord

@Entity(tableName = "asset_records", foreignKeys = [ForeignKey(entity = AssetEntity::class,
    parentColumns = ["id"], childColumns = ["assetId"], onDelete = ForeignKey.CASCADE)], indices = [Index("assetId")])
data class AssetRecordEntity(
    @PrimaryKey val id: String, val assetId: String, val kind: String, val occurredAt: Long,
    val content: String, val fromLocation: String?, val toLocation: String?,
    val amountMinor: Long?, val previousAmountMinor: Long?, val createdAt: Long, val updatedAt: Long,
)
internal fun AssetRecordEntity.toDomain() = AssetRecord(id, assetId, kind, occurredAt, content, fromLocation, toLocation, amountMinor, previousAmountMinor, createdAt, updatedAt)
internal fun AssetRecord.toEntity() = AssetRecordEntity(id, assetId, kind, occurredAt, content, fromLocation, toLocation, amountMinor, previousAmountMinor, createdAt, updatedAt)
