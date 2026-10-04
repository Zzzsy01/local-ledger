package com.localledger.app.ui.assets

import android.app.DatePickerDialog
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localledger.app.domain.*
import com.localledger.app.R
import com.localledger.app.ui.common.Loading
import com.localledger.app.ui.common.Message
import com.localledger.app.ui.common.visibleAmount
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun AssetsScreen(model: AssetsViewModel, onEdit: (String) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<Asset?>(null) }
    val total = state.items.fold(0L) { sum, item -> Math.addExact(sum, item.valueMinor) }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(painterResource(R.drawable.ic_ui_wallet), contentDescription = null,
                            modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("物品身家", color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelLarge)
                    }
                    Text("¥${visibleAmount(total)}", color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.headlineLarge, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text("${state.items.size} 件物品 · 当前参考估值", color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodyMedium)
                    Text("仅统计物品，不含现金与账户余额", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .75f),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Text("我的物品", Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (state.loading) item { Loading() }
        state.error?.let { item { Message(it, true) } }
        if (!state.loading && state.items.isEmpty()) item { Message("手机、相机、自行车……\n记录你拥有的东西，看看它们现在值多少。") }
        items(state.items, key = { it.id }) { asset ->
            var menuOpen by remember { mutableStateOf(false) }
            Card(Modifier.padding(horizontal = 20.dp).fillMaxWidth().clickable { onEdit(asset.id) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(14.dp)) {
                            Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                                Icon(painterResource(R.drawable.ic_ui_package), contentDescription = null,
                                    modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(asset.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(asset.kind, Modifier.padding(top = 3.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
                                Icon(painterResource(R.drawable.ic_ui_more), contentDescription = "${asset.name}物品操作",
                                    modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(text = { Text("移除物品", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(painterResource(R.drawable.ic_ui_trash), contentDescription = null,
                                        modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error) },
                                    onClick = { menuOpen = false; deleting = asset })
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("当前估值", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        Text("¥${visibleAmount(asset.valueMinor)}", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
                    }
                    val date = Instant.ofEpochMilli(asset.pricedAt).atZone(ZoneId.systemDefault()).toLocalDate()
                    Text("${date} · " + if (asset.referencePricesMinor.isEmpty()) "手动估值" else "${asset.referencePricesMinor.size} 个参考报价均值", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
                    Text(asset.purchaseMinor?.let { "购入价 ¥${visibleAmount(it)}" } ?: "未填写购入价",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
    deleting?.let { asset -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("移除「${asset.name}」？") }, text = { Text("物品将不再计入身家估值。收支账目不会改变。") }, confirmButton = { TextButton(onClick = { model.delete(asset.id); deleting = null }) { Text("移除") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }) }
    error?.let { AlertDialog(onDismissRequest = model::dismissError, title = { Text("操作未完成") }, text = { Text(it) }, confirmButton = { TextButton(onClick = model::dismissError) { Text("知道了") } }) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssetEditorScreen(model: AssetEditorViewModel, onSavingChanged: (Boolean) -> Unit, onSaved: () -> Unit) {
    val draft by model.draft.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    BackHandler(draft.saving) { }
    LaunchedEffect(draft.saving) { onSavingChanged(draft.saving) }
    DisposableEffect(Unit) { onDispose { onSavingChanged(false) } }
    LaunchedEffect(draft.saved) { if (draft.saved) onSaved() }
    val enabled = !draft.loading && !draft.saving
    val mean = remember(draft.prices) { runCatching { parseReferencePrices(draft.prices).takeIf { it.isNotEmpty() }?.let(::averagePrice) }.getOrNull() }
    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(Modifier.weight(1f).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 8.dp)) {
            if (draft.loading) item { Loading() }
            item { OutlinedTextField(draft.name, { value -> model.change { it.copy(name = value) } }, Modifier.fillMaxWidth(), label = { Text("物品名称／型号") }, supportingText = { Text("例如 iPhone 15 128G，型号和配置越具体越好") }, enabled = enabled, singleLine = true) }
            item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { assetKinds.forEach { kind -> FilterChip(draft.kind == kind, { model.change { it.copy(kind = kind) } }, label = { Text(kind) }, enabled = enabled) } } }
            item { OutlinedButton(onClick = {
                val uri = Uri.Builder().scheme("https").authority("www.goofish.com").path("search").appendQueryParameter("q", draft.name).build()
                try { uriHandler.openUri(uri.toString()) } catch (_: IllegalArgumentException) { model.reportError("无法打开浏览器，请先安装浏览器或闲鱼。") }
            }, enabled = enabled && draft.name.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(16.dp)) {
                Text("去闲鱼找同款参考价", Modifier.weight(1f), textAlign = TextAlign.Center)
                Icon(painterResource(R.drawable.ic_ui_arrow_up_right), contentDescription = null, modifier = Modifier.size(20.dp))
            } }
            item { OutlinedTextField(draft.prices, { value -> model.change { it.copy(prices = value, date = LocalDate.now()) } }, Modifier.fillMaxWidth(), label = { Text("参考报价（元，选填）") }, placeholder = { Text("例如 2300, 2500, 2400") }, supportingText = { Text("手动录入同配置、同成色的报价，用逗号分隔。排除配件、定金和故障机；挂牌价不等于成交价。") }, enabled = enabled, minLines = 2) }
            item { OutlinedTextField(if (draft.prices.isBlank()) draft.value else mean?.let(::formatAmount).orEmpty(), { value -> model.change { it.copy(value = value, date = LocalDate.now()) } }, Modifier.fillMaxWidth(), label = { Text(if (draft.prices.isBlank()) "当前二手估值" else "参考报价均值") }, prefix = { Text("¥") }, supportingText = { Text(if (draft.prices.isBlank()) "可手动填写，物品无残值时填 0" else if (mean == null) "请检查参考报价格式" else "根据你录入的报价计算，保存后计入身家") }, isError = draft.prices.isNotBlank() && mean == null, enabled = enabled, readOnly = draft.prices.isNotBlank(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true) }
            item { OutlinedTextField(draft.purchase, { value -> model.change { it.copy(purchase = value) } }, Modifier.fillMaxWidth(), label = { Text("购入价（选填）") }, prefix = { Text("¥") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = enabled, singleLine = true) }
            item { TextButton(onClick = { DatePickerDialog(context, { _, y, m, d -> model.change { it.copy(date = LocalDate.of(y, m + 1, d)) } }, draft.date.year, draft.date.monthValue - 1, draft.date.dayOfMonth).show() }, enabled = enabled) { Text("估值日期  ${draft.date}") } }
            item { OutlinedTextField(draft.note, { value -> model.change { it.copy(note = value) } }, Modifier.fillMaxWidth(), label = { Text("成色、配置、参考链接（选填）") }, enabled = enabled, maxLines = 4) }
            item { Spacer(Modifier.height(8.dp)) }
        }
        draft.error?.let { Text(it, Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.error) }
        Button(onClick = model::save, enabled = enabled && !draft.saved, modifier = Modifier.fillMaxWidth().padding(20.dp).height(52.dp),
            shape = RoundedCornerShape(16.dp)) { Text(if (draft.saving) "保存中…" else "保存物品") }
    }
}
