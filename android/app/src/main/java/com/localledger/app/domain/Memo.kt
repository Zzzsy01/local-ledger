package com.localledger.app.domain

import java.util.UUID

data class Memo(
    val id: String,
    val title: String,
    val content: String,
    val isPinned: Boolean = false,
    val isDone: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
)

fun validateMemo(memo: Memo) {
    require(UUID.fromString(memo.id).toString() == memo.id) { "备忘录标识无效。" }
    require(memo.title.isNotBlank()) { "请填写备忘录标题。" }
    require(memo.createdAt >= 0 && memo.updatedAt >= memo.createdAt) { "备忘录时间无效。" }
}
