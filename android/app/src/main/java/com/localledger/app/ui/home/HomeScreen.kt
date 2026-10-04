package com.localledger.app.ui.home

import android.app.DatePickerDialog
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Surface
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localledger.app.domain.INCOME
import com.localledger.app.domain.EXPENSE
import com.localledger.app.domain.LedgerEntry
import com.localledger.app.domain.MonthlySummary
import com.localledger.app.domain.formatAmount
import com.localledger.app.R
import com.localledger.app.ui.common.Loading
import com.localledger.app.ui.common.Message
import com.localledger.app.ui.common.MonthPicker
import com.localledger.app.ui.common.amountColor
import com.localledger.app.ui.common.visibleAmount
import com.localledger.app.ui.common.LocalAccent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.collect

private val dateFormat = DateTimeFormatter.ofPattern("M月d日 E", Locale.CHINA)

@Composable
fun HomeScreen(model: HomeViewModel, snackbar: SnackbarHostState, onEdit: (String) -> Unit, onCopy: (String) -> Unit = {}) {
    val state by model.state.collectAsStateWithLifecycle()
    var deletion by remember { mutableStateOf<LedgerEntry?>(null) }
    LaunchedEffect(model) {
        model.messages.collect { message ->
            val result = snackbar.showSnackbar(message.text, actionLabel = message.undoId?.let { "撤销" },
                duration = if (message.undoId == null) SnackbarDuration.Short else SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) message.undoId?.let(model::undo)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { model.refreshTimeZone() }
    val zone = ZoneId.systemDefault()
    val groups = remember(state.entries, zone) {
        state.entries.groupBy { Instant.ofEpochMilli(it.transaction.occurredAt).atZone(zone).toLocalDate() }
            .toSortedMap(compareByDescending { it })
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!state.search.open || !state.search.allDates) item { MonthPicker(state.month, model::changeMonth) }
        item {
            Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (state.search.open) "查找账目" else "收支明细", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = model::toggleSearch) {
                    Icon(painterResource(R.drawable.ic_ui_search), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(if (state.search.open) "关闭筛选" else "搜索筛选", Modifier.padding(start = 6.dp))
                }
            }
        }
        if (state.search.open) item { SearchPanel(state, model) }
        item { SummaryCard(state.summary, if (state.search.open) "筛选结余" else "${state.month.monthValue}月结余", Modifier.padding(horizontal = 20.dp)) }
        if (state.search.open && !state.loading && state.error == null) item {
            Text("匹配 ${state.entries.size} 笔 · ${if (state.search.fromDate != null || state.search.throughDate != null) "自定义日期" else if (state.search.allDates) "全部日期" else "当前所选月份"}",
                Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall)
        }
        if (state.loading) item { Loading() }
        state.error?.let { error -> item { Message(error, isError = true) } }
        if (!state.loading && state.error == null && groups.isEmpty()) {
            item { Message(if (state.search.open) "没有符合条件的账目" else "这个月还没有账目\n点右下角「记一笔」，开始记录日常收支") }
        }
        groups.forEach { (date, entries) ->
            item(key = "day-$date") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(date.format(dateFormat), style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${entries.size} 笔", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(entries, key = { it.transaction.id }) { entry ->
                var menuOpen by remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().clickable { onEdit(entry.transaction.id) },
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        Surface(color = amountColor(entry.transaction.type).copy(alpha = .1f), shape = RoundedCornerShape(14.dp)) {
                            Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                                Text(entry.categoryName.take(1), color = amountColor(entry.transaction.type), fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(entry.categoryName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                                maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text(entry.accountName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            entry.transaction.note?.takeIf { it.isNotBlank() }?.let {
                                Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Column(Modifier.weight(.9f).padding(start = 8.dp), horizontalAlignment = Alignment.End) {
                            Text((if (entry.transaction.type == INCOME) "+ ¥" else "− ¥") + visibleAmount(entry.transaction.amountMinor),
                                modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleMedium, color = amountColor(entry.transaction.type),
                                textAlign = TextAlign.End)
                            Box {
                                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
                                    Icon(painterResource(R.drawable.ic_ui_more), contentDescription = "${entry.categoryName}账目操作",
                                        modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(text = { Text("再记一笔") },
                                        leadingIcon = { Icon(painterResource(R.drawable.ic_ui_copy), contentDescription = null, modifier = Modifier.size(20.dp)) },
                                        onClick = { menuOpen = false; onCopy(entry.transaction.id) })
                                    DropdownMenuItem(text = { Text("删除账目", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(painterResource(R.drawable.ic_ui_trash), contentDescription = null,
                                            modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error) },
                                        onClick = { menuOpen = false; deletion = entry })
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
    deletion?.let { entry ->
        AlertDialog(
            onDismissRequest = { deletion = null },
            title = { Text("删除这笔账目？") },
            text = { Text("${entry.categoryName} · ¥${visibleAmount(entry.transaction.amountMinor)}") },
            confirmButton = { TextButton(onClick = { model.delete(entry.transaction.id); deletion = null }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deletion = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun SummaryCard(summary: MonthlySummary, title: String, modifier: Modifier = Modifier) {
    val blue = LocalAccent.current == "blue"
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(if (blue) listOf(Color(0xFF273B85), Color(0xFF5273DE)) else listOf(Color(0xFF123D34), Color(0xFF217764)))).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.labelLarge)
                Text("收支概览", color = Color.White.copy(alpha = .65f), style = MaterialTheme.typography.labelSmall)
            }
            Text("¥${visibleAmount(summary.income - summary.expense)}", style = MaterialTheme.typography.headlineLarge,
                fontSize = 32.sp, color = Color.White, fontWeight = FontWeight.Bold)
            HorizontalDivider(color = Color.White.copy(alpha = .16f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(painterResource(R.drawable.ic_ui_arrow_up_right), contentDescription = null,
                            modifier = Modifier.size(16.dp), tint = Color(0xFFC3E7D7))
                        Text("收入", color = Color(0xFFC3E7D7), style = MaterialTheme.typography.bodySmall)
                    }
                    Text("¥${visibleAmount(summary.income)}", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(painterResource(R.drawable.ic_ui_arrow_down_right), contentDescription = null,
                            modifier = Modifier.size(16.dp), tint = Color(0xFFF1D1B1))
                        Text("支出", color = Color(0xFFF1D1B1), style = MaterialTheme.typography.bodySmall)
                    }
                    Text("¥${visibleAmount(summary.expense)}", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchPanel(state: HomeState, model: HomeViewModel) {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    val draft = state.search
    Card(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(draft.keyword, model::setKeyword, modifier = Modifier.fillMaxWidth(),
            label = { Text("搜索备注、分类或账户") }, singleLine = true, shape = RoundedCornerShape(16.dp),
            leadingIcon = { Icon(painterResource(R.drawable.ic_ui_search), contentDescription = null, modifier = Modifier.size(20.dp)) })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = draft.allDates, onClick = { model.setAllDates(true) }, label = { Text("全部日期") })
            FilterChip(selected = !draft.allDates, onClick = { model.setAllDates(false) }, label = { Text("所选月份") })
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起" else "更多筛选") }
        }
        if (expanded) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("开始" to draft.fromDate, "结束" to draft.throughDate).forEach { (label, selected) ->
                    TextButton(onClick = {
                        val date = selected ?: LocalDate.now()
                        DatePickerDialog(context, { _, y, m, d ->
                            val chosen = LocalDate.of(y, m + 1, d)
                            if (label == "开始") model.setFromDate(chosen) else model.setThroughDate(chosen)
                        }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                    }, modifier = Modifier.weight(1f)) { Text("$label：${selected ?: "不限"}") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null to "全部", EXPENSE to "支出", INCOME to "收入").forEach { (type, label) ->
                    FilterChip(selected = draft.type == type, onClick = { model.setType(type) }, label = { Text(label) })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterMenu("分类", draft.categoryId, state.categories.filter { draft.type == null || it.type == draft.type }
                    .map { it.id to (it.name + if (it.isDeleted) "（已停用）" else "") }, model::setCategory, Modifier.weight(1f))
                FilterMenu("账户", draft.accountId, state.accounts.map { it.id to it.name }, model::setAccount, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(draft.minimum, model::setMinimum, Modifier.weight(1f), label = { Text("最低金额") },
                    prefix = { Text("¥") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(draft.maximum, model::setMaximum, Modifier.weight(1f), label = { Text("最高金额") },
                    prefix = { Text("¥") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
            TextButton(onClick = model::clearFilters) { Text("清空筛选条件") }
        }
    }
    }
}

@Composable
private fun FilterMenu(label: String, selected: String?, options: List<Pair<String, String>>, onSelect: (String?) -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        TextButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label：${options.find { it.first == selected }?.second ?: "全部"} ▾", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("全部$label") }, onClick = { onSelect(null); open = false })
            options.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(id); open = false }) }
        }
    }
}
