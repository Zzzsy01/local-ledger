package com.localledger.app.ui.wishes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localledger.app.domain.Wish
import com.localledger.app.domain.formatAmount
import com.localledger.app.domain.parseAmount
import com.localledger.app.ui.common.LocalHideAmounts
import com.localledger.app.ui.common.Loading
import com.localledger.app.ui.common.Message
import com.localledger.app.ui.common.visibleAmount

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WishesScreen(model: WishesViewModel, onAdd: () -> Unit, onEdit: (String) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val filter by model.filter.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val hidden = LocalHideAmounts.current
    var deleting by remember { mutableStateOf<Wish?>(null) }
    var savingForId by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 88.dp)) {
        item { Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("想买的东西", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("为喜欢的生活，慢慢攒一点", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = onAdd) { Text("添加心愿") }
                }
                Text("还需要攒", style = MaterialTheme.typography.labelLarge)
                Text("¥${visibleAmount(state.totals.remainingMinor)}", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("待买预算 ¥${visibleAmount(state.totals.targetMinor)} · ${state.totals.active} 个心愿", style = MaterialTheme.typography.bodyMedium)
                if (!hidden) {
                    LinearProgressIndicator(progress = { state.totals.progress }, modifier = Modifier.fillMaxWidth().height(8.dp))
                    Text("整体准备进度 ${(state.totals.progress * 100).toInt()}% · 已达成 ${state.totals.purchased} 个", style = MaterialTheme.typography.bodySmall)
                } else Text("已达成 ${state.totals.purchased} 个", style = MaterialTheme.typography.bodySmall)
                Text("每一步积累，都离心愿更近。", style = MaterialTheme.typography.bodySmall)
            }
        } }
        item { Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("active" to "想买", "purchased" to "已达成").forEach { (value, label) ->
                FilterChip(filter == value, { model.filter(value) }, label = { Text(label) })
            }
        } }
        if (state.loading) item { Loading() }
        state.error?.let { item { Message(it, true) } }
        if (!state.loading && state.error == null && state.items.isEmpty()) item {
            Message(if (filter == "active") "写下第一个心愿和预计价格，\n让每一次积累都有方向。" else "买到喜欢的东西后，在这里留下一份成就。")
        }
        items(state.items, key = { it.id }) { wish ->
            Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clickable { onEdit(wish.id) }, shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(wish.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(if (wish.isPurchased) "已买到" else "心愿", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    }
                    Text("预计 ¥${visibleAmount(wish.targetMinor)} · 已攒 ¥${visibleAmount(wish.savedMinor)}", style = MaterialTheme.typography.bodyMedium)
                    if (!wish.isPurchased) {
                        if (!hidden) LinearProgressIndicator(progress = { wish.progress }, modifier = Modifier.fillMaxWidth().height(6.dp))
                        Text(if (!hidden && wish.remainingMinor == 0L) "已经攒够了，准备迎接它吧！" else "还差 ¥${visibleAmount(wish.remainingMinor)}",
                            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    }
                    if (!wish.note.isNullOrBlank()) Text(wish.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (!wish.isPurchased) TextButton(onClick = { savingForId = wish.id }, enabled = !busy) { Text("更新攒钱") }
                        TextButton(onClick = { model.purchase(wish) }, enabled = !busy) { Text(if (wish.isPurchased) "移回想买" else "买到啦") }
                        TextButton(onClick = { deleting = wish }, enabled = !busy) { Text("删除") }
                    }
                }
            }
        }
    }
    state.items.firstOrNull { it.id == savingForId }?.let { wish ->
        var amount by rememberSaveable(wish.id) { mutableStateOf(formatAmount(wish.savedMinor)) }
        var inputError by remember(wish.id) { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { if (!busy) savingForId = null }, title = { Text("为「${wish.name}」攒钱") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("填入目前为这个心愿预留的总金额。")
                OutlinedTextField(amount, { amount = it; inputError = null }, label = { Text("已攒金额（元）") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                inputError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(onClick = {
                val parsed = parseAmount(amount, allowZero = true)
                if (parsed == null) inputError = "请输入零或正数，最多两位小数。"
                else { model.saved(wish.id, parsed); savingForId = null }
            }, enabled = !busy) { Text("保存进度") } },
            dismissButton = { TextButton(onClick = { savingForId = null }, enabled = !busy) { Text("取消") } })
    }
    deleting?.let { wish -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除「${wish.name}」？") },
        text = { Text("这个心愿将从清单中移除。") },
        confirmButton = { TextButton(onClick = { model.delete(wish.id); deleting = null }, enabled = !busy) { Text("删除") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }) }
    error?.let { AlertDialog(onDismissRequest = model::dismissError, title = { Text("操作未完成") }, text = { Text(it) },
        confirmButton = { TextButton(onClick = model::dismissError) { Text("知道了") } }) }
}

@Composable
fun WishEditorScreen(model: WishEditorViewModel, onSavingChanged: (Boolean) -> Unit, onSaved: () -> Unit) {
    val draft by model.draft.collectAsStateWithLifecycle()
    BackHandler(draft.saving) { }
    LaunchedEffect(draft.saving) { onSavingChanged(draft.saving) }
    DisposableEffect(Unit) { onDispose { onSavingChanged(false) } }
    LaunchedEffect(draft.saved) { if (draft.saved) onSaved() }
    val enabled = !draft.loading && !draft.saving
    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (draft.loading) item { Loading() }
            item { Text("给喜欢的东西一个目标", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            item { OutlinedTextField(draft.name, { value -> model.change { it.copy(name = value) } }, Modifier.fillMaxWidth(),
                label = { Text("想买什么") }, placeholder = { Text("例如，相机、自行车或一台新电脑") }, singleLine = true, enabled = enabled) }
            item { OutlinedTextField(draft.target, { value -> model.change { it.copy(target = value) } }, Modifier.fillMaxWidth(),
                label = { Text("预计价格（元）") }, singleLine = true, enabled = enabled, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)) }
            item { OutlinedTextField(draft.savedAmount, { value -> model.change { it.copy(savedAmount = value) } }, Modifier.fillMaxWidth(),
                label = { Text("已攒金额（元）") }, singleLine = true, enabled = enabled, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)) }
            item { OutlinedTextField(draft.note, { value -> model.change { it.copy(note = value) } }, Modifier.fillMaxWidth(),
                label = { Text("为什么想买（选填）") }, minLines = 3, enabled = enabled) }
            item { Text("价格可以是大致预算，攒钱进度由你更新。买到后标记达成，给自己留下一份成就。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        draft.error?.let { Text(it, Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.error) }
        Button(model::save, enabled = enabled && !draft.saved && draft.name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp)) { Text(if (draft.saving) "保存中…" else "保存心愿") }
    }
}
