package com.localledger.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.localledger.app.domain.Asset

@Entity(tableName = "assets")
data class AssetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val purchaseMinor: Long?,
    val valueMinor: Long,
    val pricedAt: Long,
    val referencePricesMinor: List<Long>,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean,
)

class AssetConverters {
    @TypeConverter fun encode(prices: List<Long>): String = prices.joinToString(",")
    @TypeConverter fun decode(value: String): List<Long> = if (value.isEmpty()) emptyList() else value.split(',').map(String::toLong)
}

internal fun AssetEntity.toDomain() = Asset(id, name, kind, purchaseMinor, valueMinor, pricedAt, referencePricesMinor, note, createdAt, updatedAt, isDeleted)
internal fun Asset.toEntity() = AssetEntity(id, name, kind, purchaseMinor, valueMinor, pricedAt, referencePricesMinor, note, createdAt, updatedAt, isDeleted)
