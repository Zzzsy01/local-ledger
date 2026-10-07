package com.localledger.app.ui.wishes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localledger.app.R
import com.localledger.app.domain.Wish
import com.localledger.app.domain.formatAmount
import com.localledger.app.domain.parseAmount
import com.localledger.app.ui.common.LocalHideAmounts
import com.localledger.app.ui.common.Loading
import com.localledger.app.ui.common.Message
import com.localledger.app.ui.common.visibleAmount
import com.localledger.app.ui.common.FeatureCard

@Composable
fun WishesScreen(model: WishesViewModel, onAdd: () -> Unit, onEdit: (String) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val filter by model.filter.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val hidden = LocalHideAmounts.current
    var deleting by remember { mutableStateOf<Wish?>(null) }
    var savingForId by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp)) {
        item { FeatureCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("心愿清单", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("把喜欢，慢慢变成拥有", style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = .8f))
                    }
                    Image(painterResource(R.drawable.art_museum), null, Modifier.size(84.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("还需要攒", style = MaterialTheme.typography.labelMedium,
                        color = LocalContentColor.current.copy(alpha = .8f))
                    Text("¥${visibleAmount(state.totals.remainingMinor)}", style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    WishSummaryMetric("待买预算", "¥${visibleAmount(state.totals.targetMinor)}", Modifier.weight(1.5f))
                    WishSummaryMetric("想买", "${state.totals.active} 个", Modifier.weight(1f))
                    WishSummaryMetric("已达成", "${state.totals.purchased} 个", Modifier.weight(1f))
                }
                if (!hidden) {
                    LinearProgressIndicator(progress = { state.totals.progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(6.dp)),
                        color = MaterialTheme.colorScheme.primary, trackColor = LocalContentColor.current.copy(alpha = .12f))
                }
                Text("每一步积累，都离心愿更近。", style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = .8f))
        } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("active" to "想买", "purchased" to "已达成").forEach { (value, label) ->
                FilterChip(filter == value, { model.filter(value) }, label = { Text(label) },
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.heightIn(min = 48.dp),
                    border = BorderStroke(.7.dp, if (filter == value) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.outlineVariant),
                    leadingIcon = if (value == "purchased") {
                        { Icon(painterResource(R.drawable.ic_ui_check), contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null)
            }
        } }
        if (state.loading) item { Loading() }
        state.error?.let { item { Message(it, true) } }
        if (!state.loading && state.error == null && state.items.isEmpty()) item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    WishIcon(purchased = filter == "purchased")
                    Text(if (filter == "active") "给喜欢的东西留个位置" else "收藏你的每一次达成",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (filter == "active") "写下第一个心愿和预计价格，\n让每一次积累都有方向。" else "买到喜欢的东西后，在这里留下一份成就。",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (filter == "active") FilledTonalButton(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(painterResource(R.drawable.ic_ui_plus), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("添加心愿")
                    }
                }
            }
        }
        items(state.items, key = { it.id }) { wish ->
            var menuExpanded by remember(wish.id) { mutableStateOf(false) }
            Card(Modifier.fillMaxWidth().clickable { onEdit(wish.id) }, shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WishIcon(wish.isPurchased)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(wish.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(if (wish.isPurchased) "已买到" else "慢慢攒，慢慢靠近", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box {
                            IconButton(onClick = { menuExpanded = true }, enabled = !busy) {
                                Icon(painterResource(R.drawable.ic_ui_more), contentDescription = "心愿操作",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                DropdownMenuItem(text = { Text("编辑心愿") }, onClick = { menuExpanded = false; onEdit(wish.id) })
                                DropdownMenuItem(text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(painterResource(R.drawable.ic_ui_trash), contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) },
                                    onClick = { menuExpanded = false; deleting = wish })
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (wish.isPurchased) "预计价格" else "还差", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("¥${visibleAmount(if (wish.isPurchased) wish.targetMinor else wish.remainingMinor)}",
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("预计 ¥${visibleAmount(wish.targetMinor)}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("已攒 ¥${visibleAmount(wish.savedMinor)}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!wish.isPurchased) {
                        if (!hidden) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            LinearProgressIndicator(progress = { wish.progress },
                                modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(6.dp)),
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = .1f))
                            Text("${(wish.progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (!wish.note.isNullOrBlank()) Text(wish.note, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!wish.isPurchased) {
                            FilledTonalButton(onClick = { savingForId = wish.id }, enabled = !busy,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                                Text("更新攒钱")
                            }
                            TextButton(onClick = { model.purchase(wish) }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                                Icon(painterResource(R.drawable.ic_ui_check), contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("买到啦")
                            }
                        } else FilledTonalButton(onClick = { model.purchase(wish) }, enabled = !busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                            Text("移回想买")
                        }
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
private fun WishSummaryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = LocalContentColor.current.copy(alpha = .8f))
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun WishIcon(purchased: Boolean) {
    Box(Modifier.size(40.dp).background(
        if (purchased) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
        RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
        Icon(painterResource(if (purchased) R.drawable.ic_ui_check else R.drawable.ic_ui_goal), contentDescription = null,
            tint = if (purchased) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp))
    }
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
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp)) {
            if (draft.loading) item { Loading() }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    WishIcon(purchased = false)
                    Text("给喜欢的东西一个目标", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(draft.name, { value -> model.change { it.copy(name = value) } }, Modifier.fillMaxWidth(),
                            label = { Text("想买什么") }, placeholder = { Text("例如，相机、自行车或一台新电脑") },
                            singleLine = true, enabled = enabled, shape = RoundedCornerShape(14.dp))
                        OutlinedTextField(draft.target, { value -> model.change { it.copy(target = value) } }, Modifier.fillMaxWidth(),
                            label = { Text("预计价格（元）") }, singleLine = true, enabled = enabled, shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        OutlinedTextField(draft.savedAmount, { value -> model.change { it.copy(savedAmount = value) } }, Modifier.fillMaxWidth(),
                            label = { Text("已攒金额（元）") }, singleLine = true, enabled = enabled, shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        OutlinedTextField(draft.note, { value -> model.change { it.copy(note = value) } }, Modifier.fillMaxWidth(),
                            label = { Text("为什么想买（选填）") }, minLines = 3, enabled = enabled, shape = RoundedCornerShape(14.dp))
                    }
                }
            }
            item { Text("价格可以是大致预算，攒钱进度由你更新。买到后标记达成，给自己留下一份成就。",
                Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        draft.error?.let { Text(it, Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.error) }
        Button(model::save, enabled = enabled && !draft.saved && draft.name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).height(52.dp),
            shape = RoundedCornerShape(16.dp)) { Text(if (draft.saving) "保存中…" else "保存心愿") }
    }
}
