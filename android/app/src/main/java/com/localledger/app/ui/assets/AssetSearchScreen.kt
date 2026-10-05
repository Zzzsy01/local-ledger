package com.localledger.app.ui.assets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localledger.app.ui.common.Loading
import com.localledger.app.ui.common.Message
import com.localledger.app.ui.common.visibleAmount

@Composable
fun AssetSearchScreen(model: AssetsViewModel, onOpen: (String) -> Unit, onBrowse: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        OutlinedTextField(state.filters.query, { value -> model.filter { it.copy(query = value) } }, Modifier.fillMaxWidth(),
            label = { Text("找一件东西") }, placeholder = { Text("名称、位置、标签、编号或历史记录") }, singleLine = true,
            trailingIcon = { if (state.filters.query.isNotEmpty()) TextButton({ model.filter { it.copy(query = "") } }) { Text("清除") } })
        Text("多个关键词用空格分开，例如「相机 柜子」。历史位置也能搜到，结果优先告诉你现在放在哪。",
            Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onBrowse) { Text("浏览我的博物馆 →") }
        if (state.loading) Loading()
        state.error?.let { Message(it, true) }
        Text("${state.items.size} 件藏品", style = MaterialTheme.typography.labelLarge)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
            if (!state.loading && state.items.isEmpty()) item { Message("没有找到。试试物品名称、以前的存放位置，或先添加物品档案。") }
            items(state.items, key = { it.id }) { asset ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(asset.id) }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(asset.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("现在：${asset.location ?: "未填写位置"}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        if (asset.id in state.historyMatches) Text("匹配到历史记录 · 上方为当前位置", style = MaterialTheme.typography.labelSmall)
                        Text("购入 ${asset.purchaseDate ?: "日期未填"} · ${asset.purchaseMinor?.let { "¥${visibleAmount(it)}" } ?: "买价未填"}")
                        Text((if (asset.isValueKnown) "当前估值 ¥${visibleAmount(asset.valueMinor)}" else "尚未估值") + " · ${asset.status}")
                        if (asset.tags.isNotEmpty()) Text(asset.tags.joinToString("  ") { "#$it" }, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
