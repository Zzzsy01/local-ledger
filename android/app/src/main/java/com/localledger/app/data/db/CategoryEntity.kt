package com.localledger.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: Int,
    val icon: String? = null,
    val sortOrder: Int = 0,
    val isDeleted: Boolean = false,
)
