package com.localledger.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.localledger.app.domain.Memo

@Entity(tableName = "memos")
data class MemoEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val isPinned: Boolean = false,
    val isDone: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
)

internal fun MemoEntity.toDomain() = Memo(id, title, content, isPinned, isDone, createdAt, updatedAt, isDeleted)
internal fun Memo.toEntity() = MemoEntity(id, title, content, isPinned, isDone, createdAt, updatedAt, isDeleted)
