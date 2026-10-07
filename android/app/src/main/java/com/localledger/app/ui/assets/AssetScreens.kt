package com.localledger.app.ui.assets

import android.app.DatePickerDialog
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localledger.app.R
import com.localledger.app.domain.*
import com.localledger.app.ui.common.Loading
import com.localledger.app.ui.common.Message
import com.localledger.app.ui.common.visibleAmount
import com.localledger.app.ui.common.PageHeading
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun AssetsScreen(model: AssetsViewModel, onEdit: (String) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<Asset?>(null) }
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    val stackSummary = LocalConfiguration.current.screenWidthDp < 360 || LocalDensity.current.fontScale > 1.2f
    LazyVerticalGrid(GridCells.Fixed(if (state.filters.grid) 2 else 1), Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 104.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PageHeading("我的博物馆", "珍藏每件物品，也珍藏它的故事", R.drawable.art_museum, eyebrow = "私人展柜 · 日常珍藏")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("全部藏品" to state.total, "收藏展柜" to state.favoriteCount, "待整理" to state.needsOrganizingCount).forEach { (label, count) ->
                        FilterChip(state.filters.collection == label, { model.filter { it.copy(collection = label) } }, label = { Text("$label $count") })
                    }
                }
                if (state.filters.collection == "待整理") Text("补齐位置、购入日期或买价，下次就更容易找到。", style = MaterialTheme.typography.bodySmall)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            FlowRow(maxItemsInEachRow = if (stackSummary) 1 else 2, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard("已填估值合计", "¥${visibleAmount(state.summary.heldValue)}", "持有 ${state.summary.heldQuantity} 件 · 含闲置", Modifier.weight(1f), Color(0xFFE7F0E8))
                SummaryCard("闲置中", "${state.summary.idleQuantity} 件", "已清出 ${state.summary.clearedQuantity} 件", Modifier.weight(1f), Color(0xFFFFF0E3))
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedTextField(state.filters.query, { query -> model.filter { it.copy(query = query) } }, Modifier.fillMaxWidth(),
                placeholder = { Text("搜索名称、位置、标签、编号或历史") }, singleLine = true, shape = RoundedCornerShape(18.dp),
                leadingIcon = { Icon(painterResource(R.drawable.ic_ui_search), null, Modifier.size(20.dp)) },
                trailingIcon = { IconButton(onClick = { filtersOpen = !filtersOpen }) {
                    Icon(painterResource(R.drawable.ic_ui_settings), if (filtersOpen) "收起藏品筛选" else "筛选藏品", Modifier.size(20.dp))
                } },
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant))
        }
        if (filtersOpen) item(span = { GridItemSpan(maxLineSpan) }) {
            AssetFilterMenu(state.filters.location, (listOf("全部位置", "未填位置") + state.locations).distinct(), Modifier.fillMaxWidth()) { location -> model.filter { it.copy(location = location) } }
        }
        if (filtersOpen) item(span = { GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                AssetFilterMenu(state.filters.status, listOf("全部") + assetStatuses, Modifier.weight(1f)) { selected -> model.filter { it.copy(status = selected) } }
                AssetFilterMenu(if (state.filters.kind == "全部") "分类" else state.filters.kind, listOf("全部") + state.kinds, Modifier.weight(1f)) { selected -> model.filter { it.copy(kind = selected) } }
                AssetFilterMenu("排序", assetSorts, Modifier.weight(1f)) { selected -> model.filter { it.copy(sort = selected) } }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val active = listOf(state.filters.location.takeUnless { it == "全部位置" }, state.filters.status.takeUnless { it == "全部" }, state.filters.kind.takeUnless { it == "全部" }).filterNotNull()
                Text((listOf("${state.items.size} 件藏品", state.filters.sort) + active).joinToString(" · "), Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                IconButton(onClick = { model.filter { it.copy(grid = !it.grid) } }, modifier = Modifier.semantics { contentDescription = if (state.filters.grid) "切换列表" else "切换网格" }) {
                    Text(if (state.filters.grid) "☰" else "▦", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        if (state.loading) item(span = { GridItemSpan(maxLineSpan) }) { Loading() }
        state.error?.let { item(span = { GridItemSpan(maxLineSpan) }) { Message(it, true) } }
        if (!state.loading && state.items.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Message(if (state.total == 0) "记录你拥有的东西\n添加物品后，可以查看持有估值与日均成本。" else "没有符合条件的物品。")
        }
        items(state.items, key = { it.id }) { asset -> AssetCard(asset, state.filters.grid, asset.photoPaths.firstOrNull()?.let(model::photoFile), asset.id in state.historyMatches, { onEdit(asset.id) }, { deleting = asset }) }
    }
    deleting?.let { asset -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("移除「${asset.name}」？") }, text = { Text("这条物品记录将被移除。若要保留使用历史，请编辑为已出、丢失或退役状态。") },
        confirmButton = { TextButton(onClick = { model.delete(asset.id); deleting = null }) { Text("移除") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }) }
    error?.let { AlertDialog(onDismissRequest = model::dismissError, title = { Text("操作未完成") }, text = { Text(it) }, confirmButton = { TextButton(onClick = model::dismissError) { Text("知道了") } }) }
}

@Composable
private fun SummaryCard(title: String, value: String, detail: String, modifier: Modifier, lightColor: Color) {
    val color = if (MaterialTheme.colorScheme.background.luminance() > .5f) lightColor else MaterialTheme.colorScheme.surfaceContainerHigh
    Card(modifier, shape = RoundedCornerShape(22.dp, 8.dp, 22.dp, 22.dp), colors = CardDefaults.cardColors(containerColor = color)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AssetFilterMenu(label: String, options: List<String>, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 6.dp), shape = RoundedCornerShape(14.dp)) { Text("$label ▾", maxLines = 1, overflow = TextOverflow.Ellipsis) }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) { options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { expanded = false; onSelect(option) }) } }
    }
}

@Composable
private fun AssetCard(asset: Asset, grid: Boolean, file: File?, historyMatch: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val days = assetHoldingDays(asset, today)
    val daily = assetDailyCostMinor(asset, today)
    Card(Modifier.fillMaxWidth().clickable(onClick = onEdit), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))) {
        Column(Modifier.padding(if (grid) 12.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (grid && file != null) AssetPhoto(file, Modifier.fillMaxWidth().aspectRatio(1.2f), asset.name)
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!grid || file == null) AssetPhoto(file, Modifier.size(if (grid) 48.dp else 76.dp), asset.name)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(asset.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${asset.acquisition} · ${asset.kind}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${asset.quantity} 件 · ${asset.status}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Text("现在：${asset.location ?: "未填写位置"}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (historyMatch) Text("匹配到历史记录 · 上方为当前位置", style = MaterialTheme.typography.labelSmall)
            if (asset.isFavorite) Text("★ 收藏展柜", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            if (asset.tags.isNotEmpty()) Text(asset.tags.joinToString(" ") { "#$it" }, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(daily?.let { "日均 ¥${visibleAmount(it)} · ${days} 天" } ?: if (asset.purchaseDate == null) "补充购入日期后计算日均成本" else "未填写购入成本", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary, maxLines = 2)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("当前估值", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (asset.isValueKnown) "¥${visibleAmount(asset.valueMinor)}" else "未估值", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(painterResource(R.drawable.ic_ui_more), "${asset.name}物品操作", Modifier.size(18.dp)) }
                    DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }) { DropdownMenuItem(text = { Text("移除物品") }, onClick = { menuOpen = false; onDelete() }) }
                }
            }
            Text(asset.purchaseMinor?.let { "购入成本 ¥${visibleAmount(it)}" } ?: "未填写购入成本", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            asset.purchaseDate?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            asset.warrantyUntil?.let { Text(if (LocalDate.parse(it) >= today) "保修至 $it" else "保修已到期", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
internal fun AssetPhoto(file: File?, modifier: Modifier, description: String, scale: ContentScale = ContentScale.Crop) {
    val bitmap by produceState<ImageBitmap?>(null, file?.absolutePath) {
        value = file?.let { imageFile -> withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(imageFile.absolutePath, bounds)
            val options = BitmapFactory.Options().apply { inSampleSize = 1; while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 512) inSampleSize *= 2 }
            BitmapFactory.decodeFile(imageFile.absolutePath, options)?.asImageBitmap()
        } }
    }
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it, description, Modifier.fillMaxSize(), contentScale = scale) }
            ?: Icon(painterResource(R.drawable.ic_ui_package), description, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssetEditorScreen(model: AssetEditorViewModel, onSavingChanged: (Boolean) -> Unit, onSaved: () -> Unit) {
    val draft by model.draft.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4), model::importPhotos)
    val busy = draft.saving || draft.importing
    BackHandler(busy) { }
    LaunchedEffect(busy) { onSavingChanged(busy) }
    DisposableEffect(Unit) { onDispose { onSavingChanged(false) } }
    LaunchedEffect(draft.saved) { if (draft.saved) onSaved() }
    val enabled = !draft.loading && !busy
    val mean = remember(draft.prices) { runCatching { parseReferencePrices(draft.prices).takeIf { it.isNotEmpty() }?.let(::averagePrice) }.getOrNull() }
    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            if (draft.loading) item { Loading() }
            item { EditorSection("先记下它在哪里") {
                OutlinedTextField(draft.name, { value -> model.change { it.copy(name = value) } }, Modifier.fillMaxWidth(), label = { Text("物品名称／型号") }, enabled = enabled, singleLine = true)
                OutlinedTextField(draft.location, { value -> model.change { it.copy(location = value) } }, Modifier.fillMaxWidth(), label = { Text("存放位置 / 当前保管人（选填）") }, placeholder = { Text("家 / 书房 / 白色柜子 / 第二层") }, enabled = enabled, minLines = 2)
                Text("先填名称和位置即可保存，其他资料可以慢慢补全。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
            item { EditorSection("物品图片 · ${draft.photoPaths.size}/4") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    draft.photoPaths.forEachIndexed { index, path -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AssetPhoto(model.photoFile(path), Modifier.size(70.dp), "物品照片 ${index + 1}")
                        TextButton(onClick = { model.change { it.copy(photoPaths = it.photoPaths - path) } }, enabled = enabled) { Text("移除") }
                    } }
                    if (draft.photoPaths.size < 4) OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = enabled, modifier = Modifier.size(70.dp), shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(0.dp)) { Icon(painterResource(R.drawable.ic_ui_plus), "选择物品照片", Modifier.size(24.dp)) }
                }
                Text(if (draft.importing) "正在保存照片…" else "第一张作为封面，照片保存在本机，最多 4 张。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
            item { EditorSection("分类与收藏") {
                OutlinedTextField(draft.tags, { value -> model.change { it.copy(tags = value) } }, Modifier.fillMaxWidth(), label = { Text("标签 / 别名（空格或逗号分隔）") }, placeholder = { Text("旅行 随身 耳机") }, enabled = enabled)
                OutlinedTextField(draft.serialNumber, { value -> model.change { it.copy(serialNumber = value) } }, Modifier.fillMaxWidth(), label = { Text("序列号 / 自定义编号（选填）") }, enabled = enabled, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(draft.isFavorite, { value -> model.change { it.copy(isFavorite = value) } }, enabled = enabled); Text("放进我的收藏展柜") }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { (assetKinds + draft.kind).distinct().forEach { kind -> FilterChip(draft.kind == kind, { model.change { it.copy(kind = kind) } }, label = { Text(kind) }, enabled = enabled) } }
                OutlinedTextField(draft.kind, { value -> model.change { it.copy(kind = value) } }, Modifier.fillMaxWidth(), label = { Text("类别（可自定义）") }, singleLine = true, enabled = enabled)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("数量", Modifier.weight(1f))
                    OutlinedButton(onClick = { model.change { it.copy(quantity = it.quantity - 1) } }, enabled = enabled && draft.quantity > 1) { Text("−") }
                    Text(draft.quantity.toString(), Modifier.padding(horizontal = 16.dp))
                    OutlinedButton(onClick = { model.change { it.copy(quantity = it.quantity + 1) } }, enabled = enabled && draft.quantity < Int.MAX_VALUE) { Text("＋") }
                }
            } }
            item { EditorSection("获取信息") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { assetAcquisitions.forEach { method -> FilterChip(draft.acquisition == method, { model.change { it.copy(acquisition = method) } }, label = { Text(method) }, enabled = enabled) } }
                OutlinedTextField(draft.purchase, { value -> model.change { it.copy(purchase = value) } }, Modifier.fillMaxWidth(), label = { Text("购入总成本（选填）") }, prefix = { Text("¥") }, supportingText = { Text("这一条物品记录的总成本，用于计算日均成本；赠送物品可填 0。") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = enabled, singleLine = true)
                AssetDateField("购入日期", draft.purchaseDate, enabled, { value -> model.change { it.copy(purchaseDate = value) } })
                OutlinedTextField(draft.channel, { value -> model.change { it.copy(channel = value) } }, Modifier.fillMaxWidth(), label = { Text("获取渠道（选填）") }, placeholder = { Text("如：京东、淘宝、线下门店") }, enabled = enabled, singleLine = true)
            } }
            item { EditorSection("保修信息") {
                AssetDateField("保修到期", draft.warrantyUntil, enabled, { value -> model.change { it.copy(warrantyUntil = value) } })
                Text("日均成本按购入总成本 ÷ 持有自然日计算，购入当天计 1 天。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
            item { EditorSection("物品状态") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { assetStatuses.forEach { status -> FilterChip(draft.status == status, {
                    model.change { it.copy(status = status, disposedDate = if (status in listOf("持有中", "闲置中")) "" else it.disposedDate.ifBlank { LocalDate.now().toString() }) }
                }, label = { Text(status) }, enabled = enabled) } }
                if (draft.status !in listOf("持有中", "闲置中")) {
                    AssetDateField("处置日期", draft.disposedDate, enabled, { value -> model.change { it.copy(disposedDate = value) } }, allowClear = false)
                    Text("处置后保留记录，停止累计使用天数，并从持有估值中移出。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } }
            item { EditorSection("当前估值") {
                OutlinedButton(onClick = {
                    val uri = Uri.Builder().scheme("https").authority("www.goofish.com").path("search").appendQueryParameter("q", draft.name).build()
                    try { uriHandler.openUri(uri.toString()) } catch (_: IllegalArgumentException) { model.reportError("无法打开浏览器，请先安装浏览器或闲鱼。") }
                }, enabled = enabled && draft.name.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                    Text("去闲鱼找同款参考价", Modifier.weight(1f), textAlign = TextAlign.Center)
                    Icon(painterResource(R.drawable.ic_ui_arrow_up_right), null, Modifier.size(20.dp))
                }
                OutlinedTextField(draft.prices, { value -> model.change { it.copy(prices = value, date = LocalDate.now()) } }, Modifier.fillMaxWidth(), label = { Text("参考报价（元，选填）") }, placeholder = { Text("例如 2300, 2500, 2400") }, supportingText = { Text("手动录入同配置、同成色的报价，用逗号分隔。排除配件、定金和故障机；挂牌价不等于成交价。") }, enabled = enabled, minLines = 2)
                OutlinedTextField(if (draft.prices.isBlank()) draft.value else mean?.let(::formatAmount).orEmpty(), { value -> model.change { it.copy(value = value, date = LocalDate.now()) } }, Modifier.fillMaxWidth(), label = { Text(if (draft.prices.isBlank()) "当前二手估值" else "参考报价均值") }, prefix = { Text("¥") },
                    supportingText = { Text(if (draft.prices.isBlank()) "不知道可留空，确认无残值时填 0；这是本条记录总估值。" else if (mean == null) "请检查参考报价格式" else "根据参考报价计算总估值，购入成本单独保留。") }, isError = draft.prices.isNotBlank() && mean == null, enabled = enabled, readOnly = draft.prices.isNotBlank(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                AssetDateField("估值日期", draft.date.toString(), enabled, { value -> model.change { it.copy(date = LocalDate.parse(value)) } }, allowClear = false)
            } }
            item { EditorSection("物品的故事") { OutlinedTextField(draft.note, { value -> model.change { it.copy(note = value) } }, Modifier.fillMaxWidth(), label = { Text("来历、回忆、成色、配置或参考链接") }, enabled = enabled, minLines = 3, maxLines = 8) } }
        }
        draft.error?.let { Text(it, Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.error) }
        Button(onClick = model::save, enabled = enabled && !draft.saved, modifier = Modifier.fillMaxWidth().padding(20.dp).height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF29A50), contentColor = Color(0xFF33200F)), shape = RoundedCornerShape(16.dp)) { Text(if (busy) "保存中…" else "保存物品") }
    }
}

@Composable
internal fun EditorSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold); content() }
    }
}

@Composable
internal fun AssetDateField(label: String, value: String, enabled: Boolean, onChange: (String) -> Unit, allowClear: Boolean = true) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = {
            val date = if (value.isBlank()) LocalDate.now() else LocalDate.parse(value)
            DatePickerDialog(context, { _, year, month, day -> onChange(LocalDate.of(year, month + 1, day).toString()) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
        }, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) { Text("$label  ${value.ifBlank { "选择日期" }}") }
        if (allowClear && value.isNotBlank()) TextButton(onClick = { onChange("") }, enabled = enabled) { Text("清除") }
    }
}
