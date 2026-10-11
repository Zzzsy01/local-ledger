package com.localledger.app.ui.backup

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localledger.app.domain.ReportRange
import com.localledger.app.ui.common.visibleAmount
import java.time.LocalDate

@Composable
fun FinanceExportScreen(model: FinanceExportViewModel, onSavingChanged: (Boolean) -> Unit = {}) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val json = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { uri -> model.export(uri, false) } }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let { uri -> model.export(uri, true) } }
    val enabled = !state.loading && !state.busy
    LaunchedEffect(state.busy) { onSavingChanged(state.busy) }
    DisposableEffect(Unit) { onDispose { onSavingChanged(false) } }
    BackHandler(enabled = state.busy) {}
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("导出财务分析数据", style = MaterialTheme.typography.headlineSmall) }
        item { Text("选择范围并核对内容，再保存到你指定的位置。", style = MaterialTheme.typography.bodyMedium) }
        item { Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(true to "开始日期", false to "结束日期（含当天）").forEach { (start, label) ->
                val date = if (start) state.range.from else state.range.through
                TextButton(enabled = !state.busy, onClick = {
                    DatePickerDialog(context, { _, year, month, day ->
                        val picked = LocalDate.of(year, month + 1, day)
                        model.select(if (start) ReportRange(picked, maxOf(picked, state.range.through)) else ReportRange(minOf(picked, state.range.from), picked))
                    }, date.year, date.monthValue - 1, date.dayOfMonth).apply {
                        datePicker.minDate = LocalDate.of(1970, 1, 1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }.show()
                }) { Text("$label：$date") }
            }
            TextButton(enabled = !state.busy && !state.loading, onClick = { model.select(state.fullRange) }) { Text("选择全部记录日期") }
        } } }
        item { Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("范围内 ${state.count} 笔 · 含转账", style = MaterialTheme.typography.titleMedium)
            Text("收入 ¥${visibleAmount(state.summary.income)} · 支出 ¥${visibleAmount(state.summary.expense)}")
            Text("JSON 包含流水、分类、全部账户账面余额、区间预算、固定账目及统计说明。CSV 仅包含区间流水。")
            Text("包含商家、地点和手写备注。采集来源账目的备注会省略，避免带出通知或识别原文。", style = MaterialTheme.typography.bodySmall)
            Text("不包含备忘录、日记、物品照片、支付候选及已删除流水。分析文件不能用于完整恢复。", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } } }
        if (state.loading || state.busy) item { CircularProgressIndicator() }
        state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
        state.notice?.let { notice -> item { Text(notice, color = MaterialTheme.colorScheme.primary) } }
        item { Button(enabled = enabled, onClick = { json.launch("随手账财务分析-${state.range.from}-${state.range.through}.json") }, modifier = Modifier.fillMaxWidth()) { Text("保存 JSON · 用于分析") } }
        item { OutlinedButton(enabled = enabled, onClick = { csv.launch("随手账流水-${state.range.from}-${state.range.through}.csv") }, modifier = Modifier.fillMaxWidth()) { Text("保存 CSV · 用表格查看") } }
    }
}
