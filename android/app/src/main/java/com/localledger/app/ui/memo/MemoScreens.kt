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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localledger.app.R
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
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("我的备忘录", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("随手记下，让生活有条不紊", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp)) { Text("新建") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("全部" to state.total, "待完成" to state.unfinished, "置顶" to state.pinned).forEach { (label, count) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(count.toString(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
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
        item {
            FlowRow(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to "全部", "open" to "未完成", "pinned" to "置顶", "done" to "已完成").forEach { (value, label) ->
                    FilterChip(filter == value, { model.filter(value) }, label = { Text(label) },
                        modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(.7.dp, if (filter == value) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.outlineVariant),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer))
                }
            }
        }
        if (state.loading) item { Loading() }
        state.error?.let { item { Message(it, true) } }
        if (!state.loading && state.error == null && state.items.isEmpty()) item {
            Surface(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface, border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(painterResource(R.drawable.ic_ui_notebook), contentDescription = null,
                        modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(if (state.total == 0) "留住第一个想法" else "没有符合条件的备忘录",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (state.total == 0) "待办、灵感、每月复盘……\n把容易忘记的事情留在这里。" else "试试其他关键词或筛选条件。",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.total == 0) TextButton(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp)) { Text("新建备忘录") }
                }
            }
        }
        items(state.items, key = { it.id }) { memo ->
            Card(onClick = { onEdit(memo.id) }, modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(), shape = RoundedCornerShape(22.dp),
                border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(6.dp).background(if (memo.isDone) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(3.dp)))
                            Text(if (memo.isDone) "已完成" else "待完成", style = MaterialTheme.typography.labelSmall,
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
                        color = if (memo.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    if (memo.content.isNotBlank()) Text(memo.content, style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val date = Instant.ofEpochMilli(memo.updatedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    Text(date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), thickness = 0.7.dp)
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilledTonalButton(onClick = { model.complete(memo) }, enabled = !busy, shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
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
        LazyColumn(Modifier.weight(1f).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp)) {
            if (draft.loading) item { Loading() }
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
