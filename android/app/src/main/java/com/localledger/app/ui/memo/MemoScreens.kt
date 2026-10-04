package com.localledger.app.ui.memo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localledger.app.domain.Memo
import com.localledger.app.ui.common.Loading
import com.localledger.app.ui.common.Message
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun MemosScreen(model: MemosViewModel, onAdd: () -> Unit, onEdit: (String) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val query by model.query.collectAsStateWithLifecycle()
    val filter by model.filter.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<Memo?>(null) }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 88.dp)) {
        item {
            Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("把想法留在这里", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("待办、灵感，还有生活里的小事", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        TextButton(onClick = onAdd) { Text("新建") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("全部备忘" to state.total, "待完成" to state.unfinished, "已置顶" to state.pinned).forEach { (label, count) ->
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(count.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }
            }
        }
        item { OutlinedTextField(query, model::query, Modifier.padding(horizontal = 16.dp).fillMaxWidth(), label = { Text("搜索标题或内容") }, singleLine = true) }
        item {
            FlowRow(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to "全部", "open" to "未完成", "pinned" to "置顶", "done" to "已完成").forEach { (value, label) ->
                    FilterChip(filter == value, { model.filter(value) }, label = { Text(label) })
                }
            }
        }
        if (state.loading) item { Loading() }
        state.error?.let { item { Message(it, true) } }
        if (!state.loading && state.error == null && state.items.isEmpty()) item {
            Message(if (state.total == 0) "想买的东西、待办事项、每月复盘……\n把容易忘记的事情留在这里。" else "没有符合条件的备忘录")
        }
        items(state.items, key = { it.id }) { memo ->
            Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clickable { onEdit(memo.id) }, shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (memo.isPinned) Text("置顶", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                        Text(memo.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                            textDecoration = if (memo.isDone) TextDecoration.LineThrough else null,
                            color = if (memo.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    }
                    if (memo.content.isNotBlank()) Text(memo.content, maxLines = 3, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val date = Instant.ofEpochMilli(memo.updatedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    Text("$date · ${if (memo.isDone) "已完成" else "未完成"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { model.pin(memo) }, enabled = !busy) { Text(if (memo.isPinned) "取消置顶" else "置顶") }
                        TextButton(onClick = { model.complete(memo) }, enabled = !busy) { Text(if (memo.isDone) "重新打开" else "完成") }
                        TextButton(onClick = { deleting = memo }, enabled = !busy) { Text("删除") }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    deleting?.let { memo -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除「${memo.title}」？") },
        text = { Text("这条备忘录将从列表中移除。") },
        confirmButton = { TextButton(onClick = { model.delete(memo.id); deleting = null }, enabled = !busy) { Text("删除") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }) }
    error?.let { AlertDialog(onDismissRequest = model::dismissError, title = { Text("操作未完成") }, text = { Text(it) },
        confirmButton = { TextButton(onClick = model::dismissError) { Text("知道了") } }) }
}

@Composable
fun MemoEditorScreen(model: MemoEditorViewModel, onSavingChanged: (Boolean) -> Unit, onSaved: () -> Unit) {
    val draft by model.draft.collectAsStateWithLifecycle()
    BackHandler(draft.saving) { }
    LaunchedEffect(draft.saving) { onSavingChanged(draft.saving) }
    DisposableEffect(Unit) { onDispose { onSavingChanged(false) } }
    LaunchedEffect(draft.saved) { if (draft.saved) onSaved() }
    val enabled = !draft.loading && !draft.saving
    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (draft.loading) item { Loading() }
            item { OutlinedTextField(draft.title, { value -> model.change { it.copy(title = value) } }, Modifier.fillMaxWidth(), label = { Text("标题") }, enabled = enabled, singleLine = true) }
            item { OutlinedTextField(draft.content, { value -> model.change { it.copy(content = value) } }, Modifier.fillMaxWidth(), label = { Text("内容（选填）") }, placeholder = { Text("记下计划、购物清单或复盘……") }, enabled = enabled, minLines = 8) }
            item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("置顶显示"); Switch(draft.isPinned, { value -> model.change { it.copy(isPinned = value) } }, enabled = enabled, modifier = Modifier.semantics { contentDescription = "置顶显示" })
            } }
            item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("标记为已完成"); Switch(draft.isDone, { value -> model.change { it.copy(isDone = value) } }, enabled = enabled, modifier = Modifier.semantics { contentDescription = "标记为已完成" })
            } }
            item { Text("编辑内容在当前草稿中保留，点击保存后写入账本。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        }
        draft.error?.let { Text(it, Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.error) }
        Button(model::save, enabled = enabled && !draft.saved && draft.title.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp)) { Text(if (draft.saving) "保存中…" else "保存备忘录") }
    }
}
