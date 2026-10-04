package com.localledger.app.ui.importing

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localledger.app.ui.common.visibleAmount
import java.time.Instant
import java.time.ZoneId

@Composable
fun ImportScreen(model: ImportViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::read) }
    val template = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let(model::template) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("先预览，再导入", style = MaterialTheme.typography.headlineSmall) }
        item { Text("使用模板列：时间、收支、金额、分类、账户、备注、来源ID。前三列必填，金额以元填写；分类和账户留空时使用下方选择。微信／支付宝导出文件请先整理为此格式。") }
        item { OutlinedButton(onClick = { template.launch("随手账导入模板.csv") }, enabled = !state.busy) { Text("保存 CSV 模板") } }
        item { Choice("文件编码", state.encoding, listOf("UTF-8", "GB18030").map { it to it }, !state.busy) { value -> model.change { it.copy(encoding = value) } } }
        item { Choice("默认支出分类", state.categories.find { it.id == state.expense }?.name.orEmpty(), state.categories.filter { it.type == 0 }.map { it.id to it.name }, !state.busy) { value -> model.change { it.copy(expense = value) } } }
        item { Choice("默认收入分类", state.categories.find { it.id == state.income }?.name.orEmpty(), state.categories.filter { it.type == 1 }.map { it.id to it.name }, !state.busy) { value -> model.change { it.copy(income = value) } } }
        item { Choice("默认账户", state.accounts.find { it.id == state.account }?.name.orEmpty(), state.accounts.map { it.id to it.name }, !state.busy) { value -> model.change { it.copy(account = value) } } }
        item { Button(onClick = { pick.launch(arrayOf("text/*", "application/csv", "application/octet-stream")) }, enabled = !state.busy && state.expense.isNotBlank() && state.income.isNotBlank() && state.account.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("选择 CSV 并预览") } }
        if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.message?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
        state.preview?.let { preview ->
            val importable = preview.items.filter { !it.alreadyImported && (!state.skipSimilar || !it.similar) }
            item { HorizontalDivider(); Text("${preview.items.size} 笔 · 已导入 ${preview.items.count { it.alreadyImported }} 笔 · 待导入 ${importable.size} 笔", style = MaterialTheme.typography.titleMedium) }
            item { Row { Checkbox(state.skipSimilar, model::skipSimilar, enabled = !state.busy); Column(Modifier.weight(1f)) { Text("跳过疑似重复账目"); Text("金额、收支、时间与备注相同视为疑似重复；来源 ID 或同文件同一行用于确定已导入。", style = MaterialTheme.typography.bodySmall) } } }
            item { Button(onClick = model::confirm, enabled = !state.busy && importable.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("确认导入 ${importable.size} 笔") } }
            items(preview.items, key = { it.bill.row }) { item ->
                val date = Instant.ofEpochMilli(item.bill.occurredAt).atZone(ZoneId.systemDefault()).toLocalDateTime()
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    Text("${if(item.bill.type == 0) "支出" else "收入"} ¥${visibleAmount(item.bill.amountMinor)} · $date")
                    Text(item.bill.note.orEmpty().ifBlank { "无备注" }, maxLines = 2)
                    Text(when { item.alreadyImported -> "已导入，将跳过"; item.similar -> "疑似重复"; else -> "可导入" }, color = MaterialTheme.colorScheme.primary)
                } }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Choice(label: String, current: String, options: List<Pair<String, String>>, enabled: Boolean, select: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("$label：${current.ifBlank { "请选择" }}") }
        DropdownMenu(expanded, { expanded = false }) { options.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { expanded = false; select(id) }) } }
    }
}
