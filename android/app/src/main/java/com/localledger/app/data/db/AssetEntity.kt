package com.localledger.app.data.db

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.localledger.app.domain.Asset
import org.json.JSONArray

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
    @ColumnInfo(defaultValue = "1") val quantity: Int = 1,
    @ColumnInfo(defaultValue = "'购买'") val acquisition: String = "购买",
    @ColumnInfo(defaultValue = "NULL") val purchaseDate: String? = null,
    @ColumnInfo(defaultValue = "NULL") val channel: String? = null,
    @ColumnInfo(defaultValue = "NULL") val location: String? = null,
    @ColumnInfo(defaultValue = "NULL") val warrantyUntil: String? = null,
    @ColumnInfo(defaultValue = "'持有中'") val status: String = "持有中",
    @ColumnInfo(defaultValue = "NULL") val disposedDate: String? = null,
    @ColumnInfo(defaultValue = "'[]'") val photoPaths: String = "[]",
)

class AssetConverters {
    @TypeConverter fun encode(prices: List<Long>): String = prices.joinToString(",")
    @TypeConverter fun decode(value: String): List<Long> = if (value.isEmpty()) emptyList() else value.split(',').map(String::toLong)
}

internal fun AssetEntity.toDomain() = Asset(id, name, kind, purchaseMinor, valueMinor, pricedAt, referencePricesMinor, note, createdAt, updatedAt, isDeleted,
    quantity, acquisition, purchaseDate, channel, location, warrantyUntil, status, disposedDate,
    JSONArray(photoPaths).let { photos -> List(photos.length()) { photos.getString(it) } })
internal fun Asset.toEntity() = AssetEntity(id, name, kind, purchaseMinor, valueMinor, pricedAt, referencePricesMinor, note, createdAt, updatedAt, isDeleted,
    quantity, acquisition, purchaseDate, channel, location, warrantyUntil, status, disposedDate, JSONArray(photoPaths).toString())
