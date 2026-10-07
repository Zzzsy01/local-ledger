package com.localledger.app.ui.memo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import java.time.LocalDate
import com.localledger.app.ui.life.LifeDate
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localledger.app.R
import com.localledger.app.domain.Memo
import com.localledger.app.ui.common.Loading
import com.localledger.app.ui.common.Message
import com.localledger.app.ui.common.PageHeading
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun MemosScreen(model: MemosViewModel, onAdd: (String) -> Unit, onEdit: (String) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val query by model.query.collectAsStateWithLifecycle()
    val filter by model.filter.collectAsStateWithLifecycle()
    val kind by model.kind.collectAsStateWithLifecycle()
    val folder by model.folder.collectAsStateWithLifecycle()
    val sort by model.sort.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val add = { onAdd(kind) }
    var deleting by remember { mutableStateOf<Memo?>(null) }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)) {
        item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PageHeading(if (kind == "todo") "今天，慢慢来" else "我的笔记", "随手记下，留住生活里的小事", R.drawable.art_journal,
                    eyebrow = if (kind == "todo") "待办清单 · 做一件少一件" else "灵感 · 日常 · 小计划")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("全部" to state.total, "待完成" to state.unfinished, "置顶" to state.pinned).forEach { (label, count) ->
                        Surface(Modifier.weight(1f), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(count.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
        item {
            FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("note" to "笔记", "todo" to "待办").forEach { (value, label) -> FilterChip(kind == value, { model.kind(value) }, label = { Text(label) }) }
                var filtersOpen by remember { mutableStateOf(false) }
                Box {
                    TextButton({ filtersOpen = true }) { Text(when(filter) { "open" -> "未完成"; "pinned" -> "置顶"; "done" -> "已完成"; else -> "全部" } + " ▾") }
                    DropdownMenu(filtersOpen, { filtersOpen = false }) {
                        listOf("all" to "全部", "open" to "未完成", "pinned" to "置顶", "done" to "已完成").forEach { (value, label) ->
                            DropdownMenuItem(text = { Text(label + if (filter == value) " ✓" else "") }, onClick = { model.filter(value); filtersOpen = false })
                        }
                    }
                }
                var foldersOpen by remember { mutableStateOf(false) }
                var sortOpen by remember { mutableStateOf(false) }
                Box { TextButton({ foldersOpen = true }) { Text(if (folder == "全部") "分组 ▾" else "$folder ▾", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    DropdownMenu(foldersOpen, { foldersOpen = false }) { (listOf("全部") + state.folders).forEach { value ->
                        DropdownMenuItem(text = { Text(value) }, onClick = { model.folder(value); foldersOpen = false }) } } }
                Box { IconButton({ sortOpen = true }) { Icon(painterResource(R.drawable.ic_ui_settings), "笔记排序", Modifier.size(20.dp)) }
                    DropdownMenu(sortOpen, { sortOpen = false }) { listOf("updated" to "最近修改", "created" to "最近创建", "title" to "名称").forEach { (value, label) ->
                        DropdownMenuItem(text = { Text(label + if (sort == value) " ✓" else "") }, onClick = { model.sort(value); sortOpen = false }) } } }
            }
        }
        item {
            OutlinedTextField(query, model::query,
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().semantics { contentDescription = "搜索标题或内容" },
                placeholder = { Text("搜索标题或内容") }, singleLine = true, shape = RoundedCornerShape(18.dp),
                leadingIcon = { Icon(painterResource(R.drawable.ic_ui_search), contentDescription = null, Modifier.size(20.dp)) },
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant))
        }
        if (state.loading) item { Loading() }
        state.error?.let { item { Message(it, true) } }
        if (!state.loading && state.error == null && state.items.isEmpty()) item {
            Surface(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface, border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (state.total == 0) "留住第一个想法" else "没有符合条件的备忘录",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (state.total == 0) "待办、灵感、每月复盘……\n把容易忘记的事情留在这里。" else "试试其他关键词或筛选条件。",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.total == 0) TextButton(onClick = add, modifier = Modifier.heightIn(min = 48.dp)) { Text("新建备忘录") }
                }
            }
        }
        items(state.items, key = { it.id }) { memo ->
            val ink = if (MaterialTheme.colorScheme.background.luminance() < .5f) Color(0xFFF2EEE2) else Color(0xFF333B31)
            Card(onClick = { onEdit(memo.id) }, modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                shape = RoundedCornerShape(8.dp, 24.dp, 24.dp, 24.dp),
                colors = CardDefaults.cardColors(containerColor = memoColor(memo.color), contentColor = ink)) {
                if (memo.kind == "todo") {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(memo.isDone, { model.complete(memo) }, enabled = !busy,
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary, uncheckedColor = ink.copy(alpha = .65f)))
                        Column(Modifier.weight(1f).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(memo.title, style = MaterialTheme.typography.titleMedium, color = ink,
                                textDecoration = if (memo.isDone) TextDecoration.LineThrough else null, maxLines = 3)
                            if (memo.content.isNotBlank()) Text(memo.content, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = .75f))
                            Text(memo.folder + memo.dueEpochDay?.let { " · ${LocalDate.ofEpochDay(it)}" }.orEmpty() + if (memo.isPinned) " · 置顶" else "",
                                style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = .75f))
                        }
                        IconButton({ model.pin(memo) }, enabled = !busy) { Icon(painterResource(R.drawable.ic_ui_pin), if (memo.isPinned) "取消置顶" else "置顶", tint = ink.copy(alpha = .75f), modifier = Modifier.size(18.dp)) }
                        IconButton({ deleting = memo }, enabled = !busy) { Icon(painterResource(R.drawable.ic_ui_trash), "删除待办", tint = ink.copy(alpha = .75f), modifier = Modifier.size(18.dp)) }
                    }
                } else {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(6.dp).background(if (memo.isDone) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(3.dp)))
                            Text(if (memo.isDone) "已完成" else memo.folder, style = MaterialTheme.typography.labelSmall,
                                color = if (memo.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                        }
                        if (memo.isPinned) Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_ui_pin), contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("置顶", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Text(memo.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        textDecoration = if (memo.isDone) TextDecoration.LineThrough else null,
                        color = ink)
                    if (memo.content.isNotBlank()) Text(memo.content, style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4, overflow = TextOverflow.Ellipsis, color = ink.copy(alpha = .8f))
                    memo.dueEpochDay?.let { Text("到期 ${LocalDate.ofEpochDay(it)}", style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = .75f)) }
                    val date = Instant.ofEpochMilli(memo.updatedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    Text(date, style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = .7f))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), thickness = 0.7.dp)
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilledTonalButton(onClick = { model.complete(memo) }, enabled = !busy, shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = ink.copy(alpha = .07f), contentColor = ink)) {
                            Icon(painterResource(R.drawable.ic_ui_check), contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (memo.isDone) "重新打开" else "完成")
                        }
                        Row {
                            IconButton(onClick = { model.pin(memo) }, enabled = !busy, modifier = Modifier.size(48.dp)) {
                                Icon(painterResource(R.drawable.ic_ui_pin), contentDescription = if (memo.isPinned) "取消备忘录置顶" else "置顶备忘录",
                                    modifier = Modifier.size(20.dp), tint = if (memo.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { deleting = memo }, enabled = !busy, modifier = Modifier.size(48.dp)) {
                                Icon(painterResource(R.drawable.ic_ui_trash), contentDescription = "删除备忘录", modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    deleting?.let { memo -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除「${memo.title}」？") },
        text = { Text("移入回收站后，可在设置中恢复。") },
        confirmButton = { TextButton(onClick = { model.delete(memo.id); deleting = null }, enabled = !busy) { Text("删除") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }) }
    error?.let { AlertDialog(onDismissRequest = model::dismissError, title = { Text("操作未完成") }, text = { Text(it) },
        confirmButton = { TextButton(onClick = model::dismissError) { Text("知道了") } }) }
}

@Composable
private fun memoColor(color: String): Color = if (MaterialTheme.colorScheme.background.luminance() < .5f) {
    when(color) { "blue" -> Color(0xFF293B49); "green" -> Color(0xFF344333); "pink" -> Color(0xFF493640); else -> Color(0xFF474331) }
} else when(color) { "blue" -> Color(0xFFE4EDF3); "green" -> Color(0xFFE9EEDC); "pink" -> Color(0xFFF4E5E5); else -> Color(0xFFF3E9CA) }

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun MemoEditorScreen(model: MemoEditorViewModel, onSavingChanged: (Boolean) -> Unit, onSaved: () -> Unit) {
    val draft by model.draft.collectAsStateWithLifecycle()
    BackHandler(draft.saving) { }
    LaunchedEffect(draft.saving) { onSavingChanged(draft.saving) }
    DisposableEffect(Unit) { onDispose { onSavingChanged(false) } }
    LaunchedEffect(draft.saved) { if (draft.saved) onSaved() }
    val enabled = !draft.loading && !draft.saving
    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(Modifier.weight(1f).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp)) {
            if (draft.loading) item { Loading() }
            item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("note" to "笔记", "todo" to "待办").forEach { (value, label) -> FilterChip(draft.kind == value, { model.change { it.copy(kind = value) } }, label = { Text(label) }, enabled = enabled) }
                listOf("yellow" to "奶黄", "blue" to "雾蓝", "green" to "浅绿", "pink" to "粉色").forEach { (value, label) ->
                    FilterChip(draft.color == value, { model.change { it.copy(color = value) } }, label = { Text(label) }, enabled = enabled,
                        colors = FilterChipDefaults.filterChipColors(containerColor = memoColor(value), selectedContainerColor = memoColor(value), labelColor = MaterialTheme.colorScheme.onSurface, selectedLabelColor = MaterialTheme.colorScheme.onSurface)) }
            } }
            item { OutlinedTextField(draft.folder, { value -> model.change { it.copy(folder = value) } }, Modifier.fillMaxWidth(), label = { Text("分组") }, singleLine = true, enabled = enabled) }
            if (draft.kind == "todo") item { LifeDate("到期日期", draft.dueDate, enabled, optional = true) { value -> model.change { it.copy(dueDate = value) } } }
            item { Text("给想法一个位置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
            item {
                OutlinedTextField(draft.title, { value -> model.change { it.copy(title = value) } }, Modifier.fillMaxWidth(),
                    label = { Text("标题") }, placeholder = { Text("这次想记下什么？") }, enabled = enabled, minLines = 1, maxLines = 3,
                    shape = RoundedCornerShape(18.dp), textStyle = MaterialTheme.typography.titleMedium,
                    colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant))
            }
            item {
                OutlinedTextField(draft.content, { value -> model.change { it.copy(content = value) } }, Modifier.fillMaxWidth(),
                    label = { Text("内容（选填）") }, placeholder = { Text("记下计划、随手灵感或复盘……") }, enabled = enabled, minLines = 7,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant))
            }
            item {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(painterResource(R.drawable.ic_ui_pin), contentDescription = null, modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("置顶显示", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Switch(draft.isPinned, { value -> model.change { it.copy(isPinned = value) } }, enabled = enabled,
                                modifier = Modifier.semantics { contentDescription = "置顶显示" })
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), thickness = 0.7.dp)
                        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(painterResource(R.drawable.ic_ui_check), contentDescription = null, modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("标记为已完成", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Switch(draft.isDone, { value -> model.change { it.copy(isDone = value) } }, enabled = enabled,
                                modifier = Modifier.semantics { contentDescription = "标记为已完成" })
                        }
                    }
                }
            }
        }
        draft.error?.let { Text(it, Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.error) }
        Button(model::save, enabled = enabled && !draft.saved && draft.title.isNotBlank(), shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp).heightIn(min = 54.dp)) {
            Text(if (draft.saving) "保存中…" else "保存备忘录")
        }
    }
}
