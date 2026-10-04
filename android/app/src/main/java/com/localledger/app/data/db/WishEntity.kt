package com.localledger.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.localledger.app.domain.Wish

@Entity(tableName = "wishes")
data class WishEntity(
    @PrimaryKey val id: String,
    val name: String,
    val targetMinor: Long,
    val savedMinor: Long = 0,
    val note: String? = null,
    val isPurchased: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
)

internal fun WishEntity.toDomain() = Wish(id, name, targetMinor, savedMinor, note, isPurchased, createdAt, updatedAt, isDeleted)
internal fun Wish.toEntity() = WishEntity(id, name, targetMinor, savedMinor, note, isPurchased, createdAt, updatedAt, isDeleted)
