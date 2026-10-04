package com.localledger.app.ui.life

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.localledger.app.domain.*
import java.time.LocalDate

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun LifeCreateDialog(onDismiss: () -> Unit, onSelect: (LifeKind) -> Unit, onWishes: () -> Unit,
    onTasks: () -> Unit, onFocus: () -> Unit, onStudy: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("新建生活记录") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("计划")
            FlowRow { TextButton(onWishes) { Text("购物与存钱") }; TextButton(onTasks) { Text("待办事项") }
                lifePlanKinds.forEach { kind -> TextButton({ onSelect(kind) }) { Text(kind.label) } } }
            Text("时间")
            FlowRow { lifeTimeKinds.forEach { kind -> TextButton({ onSelect(kind) }) { Text(kind.label) } } }
            Text("记录")
            FlowRow { listOf(LifeKind.HABIT, LifeKind.MOOD, LifeKind.DIARY, LifeKind.SUBSCRIPTION).forEach { kind ->
                TextButton({ onSelect(kind) }) { Text(kind.label) } }
                TextButton(onFocus) { Text("番茄钟") }; TextButton(onStudy) { Text("单词卡") } }
        }
    }, confirmButton = { TextButton(onDismiss) { Text("取消") } })
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun LifeItemDialog(kind: LifeKind, existing: LifeItem?, busy: Boolean, error: String?,
    onDismiss: () -> Unit, onSave: (LifeDraft) -> Unit) {
    val initial = existing?.let(LifeDraft::from) ?: LifeDraft.create(kind)
    var title by rememberSaveable(existing?.id, kind) { mutableStateOf(initial.title) }
    var date by rememberSaveable(existing?.id, kind) { mutableStateOf(initial.date) }
    var note by rememberSaveable(existing?.id, kind) { mutableStateOf(initial.note) }
    var end by rememberSaveable(existing?.id, kind) { mutableStateOf(initial.endDate) }
    var location by rememberSaveable(existing?.id, kind) { mutableStateOf(initial.location) }
    var target by rememberSaveable(existing?.id, kind) { mutableStateOf(initial.target) }
    var progress by rememberSaveable(existing?.id, kind) { mutableStateOf(initial.progress) }
    var unit by rememberSaveable(existing?.id, kind) { mutableStateOf(initial.unit) }
    var amount by rememberSaveable(existing?.id, kind) { mutableStateOf(initial.amount) }
    var yearly by rememberSaveable(existing?.id, kind) { mutableStateOf(initial.repeatYearly) }
    var mood by rememberSaveable(existing?.id, kind) { mutableIntStateOf(initial.mood) }
    var cycle by rememberSaveable(existing?.id, kind) { mutableStateOf(initial.cycleMonths) }
    LifeFormDialog(kind.label, busy, error, onDismiss, {
        onSave(LifeDraft(existing?.id, kind, title, date, note, end, location, target, progress, unit, amount, yearly, mood, cycle))
    }) {
        LifeText("名称", title, busy) { title = it }
        LifeDate(if (kind == LifeKind.SUBSCRIPTION) "下次续期" else "日期", date, !busy) { date = it }
        if (kind in lifePlanKinds) {
            LifeText("目标（整数）", target, busy) { target = it }
            LifeText("当前进度（整数）", progress, busy) { progress = it }
            LifeText("单位", unit, busy) { unit = it }
        }
        if (kind == LifeKind.TRAVEL) {
            LifeText("目的地（选填）", location, busy) { location = it }
            LifeDate("结束日期（选填）", end, !busy, optional = true) { end = it }
            LifeText("旅行预算（元，选填）", amount, busy) { amount = it }
        }
        if (kind in setOf(LifeKind.BIRTHDAY, LifeKind.ANNIVERSARY)) Row {
            Checkbox(yearly, { yearly = it }, enabled = !busy); Text("每年重复")
        }
        if (kind == LifeKind.MOOD) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..5).forEach { score -> FilterChip(score == mood, { mood = score }, label = { Text(score.toString()) }, enabled = !busy) }
        }
        if (kind == LifeKind.SUBSCRIPTION) {
            LifeText("每期金额（元）", amount, busy) { amount = it }
            LifeText("续期间隔（月）", cycle, busy) { cycle = it }
        }
        OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text(if (kind == LifeKind.DIARY) "日记正文" else "备注（选填）") }, minLines = 3, maxLines = 7, enabled = !busy)
    }
}

@Composable
private fun LifeText(label: String, value: String, busy: Boolean, change: (String) -> Unit) {
    OutlinedTextField(value, change, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true, enabled = !busy)
}

@Composable
internal fun LifeDate(label: String, value: String, enabled: Boolean, optional: Boolean = false, change: (String) -> Unit) {
    val context = LocalContext.current
    Row {
        OutlinedButton({
            val day = if (value.isEmpty()) LocalDate.now() else LocalDate.parse(value)
            DatePickerDialog(context, { _, y, m, d -> change(LocalDate.of(y, m + 1, d).toString()) }, day.year, day.monthValue - 1, day.dayOfMonth).show()
        }, enabled = enabled, modifier = Modifier.weight(1f)) { Text("$label  ${value.ifBlank { "选择" }}") }
        if (optional && value.isNotEmpty()) TextButton({ change("") }, enabled = enabled) { Text("清除") }
    }
}

@Composable
private fun LifeFormDialog(title: String, busy: Boolean, error: String?, dismiss: () -> Unit, save: () -> Unit,
    content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, title = { Text(title) }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content(); error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(save, enabled = !busy) { Text(if (busy) "保存中…" else "保存") } },
        dismissButton = { TextButton(dismiss, enabled = !busy) { Text("取消") } })
}

@Composable
internal fun LifeDeleteDialog(title: String, busy: Boolean, error: String?, onDismiss: () -> Unit, onConfirm: () -> Unit,
    description: String = "移入回收站后，可在设置中恢复。") {
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("移除「$title」？") }, text = {
        Column { Text(description); error?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
    }, confirmButton = { TextButton(onConfirm, enabled = !busy) { Text("移除") } }, dismissButton = { TextButton(onDismiss, enabled = !busy) { Text("取消") } })
}

@Composable
internal fun FocusCreateDialog(busy: Boolean, error: String?, dismiss: () -> Unit, save: (String, String) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var minutes by rememberSaveable { mutableStateOf("25") }
    LifeFormDialog("开始专注", busy, error, dismiss, { save(title, minutes) }) {
        LifeText("这次专注什么？", title, busy) { title = it }
        LifeText("分钟（1–180）", minutes, busy) { minutes = it }
        Text("离开页面后继续计时，暂停与完成状态保存在本机。", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun StudyCardDialog(card: StudyCard?, busy: Boolean, error: String?, dismiss: () -> Unit,
    save: (String, String, String, String) -> Unit) {
    var language by rememberSaveable(card?.id) { mutableStateOf(card?.language ?: "en") }
    var word by rememberSaveable(card?.id) { mutableStateOf(card?.word.orEmpty()) }
    var meaning by rememberSaveable(card?.id) { mutableStateOf(card?.meaning.orEmpty()) }
    var note by rememberSaveable(card?.id) { mutableStateOf(card?.note.orEmpty()) }
    LifeFormDialog("单词卡", busy, error, dismiss, { save(language, word, meaning, note) }) {
        Row { listOf("en" to "英语", "ja" to "日语").forEach { (key, label) -> FilterChip(language == key, { language = key }, label = { Text(label) }, enabled = !busy) } }
        LifeText("单词", word, busy) { word = it }; LifeText("释义", meaning, busy) { meaning = it }
        LifeText("例句或读音（选填）", note, busy) { note = it }
    }
}

@Composable
internal fun StudyReviewDialog(card: StudyCard?, busy: Boolean, error: String?, dismiss: () -> Unit, review: (StudyCard, Boolean) -> Unit) {
    var revealed by rememberSaveable(card?.id) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, title = { Text(card?.word ?: "复习完成") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (card == null) Text("今天到期的卡片已经复习完了。")
            else if (revealed) { Text(card.meaning); card.note?.let { Text(it) } }
            else Text("回想释义，然后查看答案。")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = {
        if (card == null) TextButton(dismiss) { Text("完成") }
        else if (!revealed) TextButton({ revealed = true }) { Text("查看答案") }
        else TextButton({ review(card, true) }, enabled = !busy) { Text("记得") }
    }, dismissButton = {
        if (card != null && revealed) TextButton({ review(card, false) }, enabled = !busy) { Text("忘记了") }
        else TextButton(dismiss, enabled = !busy) { Text("关闭") }
    })
}
