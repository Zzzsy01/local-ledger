package com.localledger.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
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
    @ColumnInfo(defaultValue = "'note'") val kind: String = "note",
    @ColumnInfo(defaultValue = "'yellow'") val color: String = "yellow",
    @ColumnInfo(defaultValue = "'默认'") val folder: String = "默认",
    @ColumnInfo(defaultValue = "NULL") val dueEpochDay: Long? = null,
)

internal fun MemoEntity.toDomain() = Memo(id, title, content, isPinned, isDone, createdAt, updatedAt, isDeleted, kind, color, folder, dueEpochDay)
internal fun Memo.toEntity() = MemoEntity(id, title, content, isPinned, isDone, createdAt, updatedAt, isDeleted, kind, color, folder, dueEpochDay)
