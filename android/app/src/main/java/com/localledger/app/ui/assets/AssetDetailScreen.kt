package com.localledger.app.ui.assets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localledger.app.domain.*
import com.localledger.app.ui.common.Loading
import com.localledger.app.ui.common.Message
import com.localledger.app.ui.common.LocalHideAmounts
import com.localledger.app.ui.common.visibleAmount
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private fun recordDate(time: Long) = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate().toString()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssetDetailScreen(model: AssetDetailViewModel, onEdit: (String) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val asset = state.asset
    var action by rememberSaveable { mutableStateOf<String?>(null) }
    var editingRecord by rememberSaveable { mutableStateOf<String?>(null) }
    var photo by rememberSaveable { mutableStateOf<String?>(null) }
    var timeline by rememberSaveable { mutableStateOf("全部") }
    if (state.loading) { Loading(); return }
    if (asset == null) { Message(state.error ?: "这件藏品已移入回收站，或已不存在。", true); return }
    fun open(value: String) { model.dismissError(); editingRecord = null; action = value }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                asset.photoPaths.firstOrNull()?.let { path -> AssetPhoto(model.photoFile(path), Modifier.fillMaxWidth().height(220.dp).clickable { photo = path }, asset.name) }
                if (asset.photoPaths.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    asset.photoPaths.forEach { path -> AssetPhoto(model.photoFile(path), Modifier.size(70.dp).clickable { photo = path }, "查看照片") }
                }
                Text(asset.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${asset.kind} · ${asset.quantity} 件 · ${asset.status}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { asset.tags.forEach { Text("#$it", color = MaterialTheme.colorScheme.primary) } }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ model.favorite(!asset.isFavorite) }, enabled = !busy) { Text(if (asset.isFavorite) "已入收藏展柜 ★" else "加入收藏展柜 ☆") }
                    TextButton({ onEdit(asset.id) }, enabled = !busy) { Text("编辑档案") }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("现在放在", style = MaterialTheme.typography.labelLarge)
                    SelectionContainer { Text(asset.location ?: "还没有记录位置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    Text("可以细到房间 / 柜子 / 第几层，也可以填写当前保管人。", style = MaterialTheme.typography.bodySmall)
                    TextButton({ open("move") }, enabled = !busy) { Text("移动位置 / 交给别人保管") }
                }
            }
        }
        item { EditorSection("购买与价值") {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) { Text("当时买价", style = MaterialTheme.typography.labelMedium); Text(asset.purchaseMinor?.let { "¥${visibleAmount(it)}" } ?: "未填写", style = MaterialTheme.typography.titleLarge) }
                Column(Modifier.weight(1f)) { Text("现在估值", style = MaterialTheme.typography.labelMedium); Text(if (asset.isValueKnown) "¥${visibleAmount(asset.valueMinor)}" else "未估值", style = MaterialTheme.typography.titleLarge) }
            }
            Text("购入：${asset.purchaseDate ?: "未填写日期"} · ${asset.acquisition}")
            asset.channel?.let { Text("渠道：$it") }
            if (asset.isValueKnown) Text("估值于 ${recordDate(asset.pricedAt)} · ${if (asset.referencePricesMinor.isEmpty()) "手动填写" else "挂牌参考价均值"}", style = MaterialTheme.typography.bodySmall)
            Text("估值不是实时市场报价；重新核对后可更新。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (asset.isValueKnown && !LocalHideAmounts.current) asset.purchaseMinor?.let { purchase -> Text("相对买价 ${if (asset.valueMinor >= purchase) "+" else "−"}¥${visibleAmount(kotlin.math.abs(asset.valueMinor - purchase))}") }
            TextButton({ open("value") }, enabled = !busy) { Text("更新估值并留档") }
        } }
        item { EditorSection("陪伴与档案") {
            Text(assetHoldingDays(asset)?.let { if (asset.isHeld) "已经陪伴 $it 天" else "持有过 $it 天" } ?: "补充购入日期后计算陪伴天数")
            assetDailyCostMinor(asset)?.let { Text("日均购入成本 ¥${visibleAmount(it)}") }
            asset.warrantyUntil?.let { Text("保修至 $it${if (LocalDate.parse(it) < LocalDate.now()) " · 已到期" else ""}") }
            asset.disposedDate?.let { Text("处置日期：$it") }
            SelectionContainer { Text("编号：${asset.serialNumber ?: asset.id.take(8).uppercase()}") }
            Text("建档 ${recordDate(asset.createdAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        item { EditorSection("物品的故事") { SelectionContainer { Text(asset.note?.takeIf { it.isNotBlank() } ?: "为什么买下它、怎么使用、有什么回忆……可以在编辑档案中记下来。") } } }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("流转与使用记录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton({ open("record") }, enabled = !busy) { Text("记一条") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("全部", "位置", "估值", "手记").forEach { label ->
                FilterChip(timeline == label, { timeline = label }, label = { Text(label) })
            } }
        }
        val records = state.records.filter { when (timeline) { "位置" -> it.kind == "位置变更"; "估值" -> it.kind in listOf("建立档案", "估值更新"); "手记" -> it.kind in manualAssetRecordKinds; else -> true } }
        if (records.isEmpty()) item { Message("还没有这类记录。搬动、估值和档案变化会自动留痕；使用、保养和借还可以随手记。") }
        items(records, key = { it.id }) { record -> EditorSection("${recordDate(record.occurredAt)} · ${record.kind}") {
            SelectionContainer { Text(record.content) }
            if (record.kind == "位置变更") {
                Text("从：${record.fromLocation ?: "未填写"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("到：${record.toLocation ?: "未填写"}", fontWeight = FontWeight.SemiBold)
            }
            record.amountMinor?.let { Text((record.previousAmountMinor?.let { previous -> "¥${visibleAmount(previous)} → " } ?: "") + "¥${visibleAmount(it)}") }
            if (record.amountMinor == null) record.previousAmountMinor?.let { Text("¥${visibleAmount(it)} → 未填写") }
            if (record.kind in manualAssetRecordKinds) TextButton({ model.dismissError(); editingRecord = record.id; action = "record" }, enabled = !busy) { Text("编辑这条记录") }
        } }
        if (action == null) error?.let { item { Message(it, true) } }
    }
    photo?.let { path -> Dialog(onDismissRequest = { photo = null }) {
        Surface(shape = MaterialTheme.shapes.large) { Column(Modifier.padding(12.dp)) {
            AssetPhoto(model.photoFile(path), Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 500.dp), asset.name, ContentScale.Fit)
            TextButton({ photo = null }) { Text("关闭照片") }
        } }
    } }
    action?.let { kind ->
        val existing = state.records.firstOrNull { it.id == editingRecord }
        AssetActionDialog(kind, asset, existing, busy, error, { if (!busy) action = null }) { text, type, day ->
            when (kind) {
                "move" -> model.move(text) { action = null }
                "value" -> model.revalue(text, day) { action = null }
                else -> model.record(existing?.id, type, text, day) { action = null }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssetActionDialog(action: String, asset: Asset, existing: AssetRecord?, busy: Boolean, error: String?, dismiss: () -> Unit,
    save: (String, String, String) -> Unit) {
    val hidden = LocalHideAmounts.current
    var text by rememberSaveable(action, existing?.id) { mutableStateOf(when (action) { "move" -> asset.location.orEmpty(); "value" -> if (hidden || !asset.isValueKnown) "" else formatAmount(asset.valueMinor); else -> existing?.content.orEmpty() }) }
    var kind by rememberSaveable(existing?.id) { mutableStateOf(existing?.kind ?: "随记") }
    var day by rememberSaveable(existing?.id) { mutableStateOf(existing?.let { recordDate(it.occurredAt) } ?: LocalDate.now().toString()) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(when (action) { "move" -> "更新存放位置"; "value" -> "记录当前估值"; else -> "留一条物品手记" }) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (action == "record") FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { manualAssetRecordKinds.forEach { option -> FilterChip(kind == option, { kind = option }, label = { Text(option) }, enabled = !busy) } }
            OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), enabled = !busy,
                label = { Text(when (action) { "move" -> "房间 / 柜子 / 层数 / 保管人"; "value" -> "估值（元）"; else -> "内容" }) },
                minLines = if (action == "value") 1 else 3, maxLines = 6,
                keyboardOptions = KeyboardOptions(keyboardType = if (action == "value") KeyboardType.Decimal else KeyboardType.Text))
            if (action != "move") AssetDateField("记录日期", day, !busy, { day = it }, false)
            if (action == "record") Text("借出或归还时，也请用「移动位置」更新当前保管人，方便下次找到它。", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton({ save(text, kind, day) }, enabled = !busy) { Text(if (busy) "保存中…" else "保存") } },
        dismissButton = { TextButton(dismiss, enabled = !busy) { Text("取消") } })
}
