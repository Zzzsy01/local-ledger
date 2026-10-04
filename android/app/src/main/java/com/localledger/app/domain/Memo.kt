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
    val kind: String = "note",
    val color: String = "yellow",
    val folder: String = "默认",
    val dueEpochDay: Long? = null,
)

fun validateMemo(memo: Memo) {
    require(UUID.fromString(memo.id).toString() == memo.id) { "备忘录标识无效。" }
    require(memo.title.isNotBlank()) { "请填写备忘录标题。" }
    require(memo.createdAt >= 0 && memo.updatedAt >= memo.createdAt) { "备忘录时间无效。" }
    require(memo.kind in listOf("note", "todo") && memo.color in listOf("yellow", "blue", "green", "pink")) { "备忘录类型或颜色无效。" }
    require(memo.folder.isNotBlank()) { "分组不能为空。" }
    memo.dueEpochDay?.let { validateLifeDay(it) }
}
