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
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.luminance
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
import com.localledger.app.domain.TRANSFER
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
import com.localledger.app.ui.common.FeatureCard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.collect

private val dateFormat = DateTimeFormatter.ofPattern("M月d日 E", Locale.CHINA)

@Composable
fun HomeScreen(model: HomeViewModel, snackbar: SnackbarHostState, onEdit: (String) -> Unit, onCopy: (String) -> Unit = {},
    onAccounts: () -> Unit = {}, onAddTransaction: () -> Unit = {}, onPlanning: () -> Unit = {}, overview: @Composable () -> Unit = {}) {
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
        if (!state.search.open) {
            item { SummaryCard(state.summary, "${state.month.monthValue}月收支概览", Modifier.padding(horizontal = 20.dp)) }
            item { QuickEntries(state, onCopy, onAddTransaction) }
            item { BudgetCard(state, onPlanning) }
            item { MonthCalendar(state, model::selectDay) }
        }
        item {
            Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (state.search.open) "查找账目" else state.selectedDay?.let { "${it.monthValue}月${it.dayOfMonth}日明细" } ?: "本月明细", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = model::toggleSearch) {
                    Icon(painterResource(R.drawable.ic_ui_search), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(if (state.search.open) "关闭筛选" else "搜索筛选", Modifier.padding(start = 6.dp))
                }
            }
        }
        if (state.search.open) item { SearchPanel(state, model) }
        if (state.search.open) item { SummaryCard(state.summary, "筛选收支概览", Modifier.padding(horizontal = 20.dp)) }
        if (!state.search.open && state.selectedDay != null) item {
            Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("月概要保留整月数据 · 以下为当天 ${state.entries.size} 笔", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { model.selectDay(null) }) { Text("看整月") }
            }
        }
        if (state.search.open && !state.loading && state.error == null) item {
            Text("匹配 ${state.entries.size} 笔 · ${if (state.search.fromDate != null || state.search.throughDate != null) "自定义日期" else if (state.search.allDates) "全部日期" else "当前所选月份"}",
                Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall)
        }
        if (state.loading) item { Loading() }
        state.error?.let { error -> item { Message(error, isError = true) } }
        if (!state.loading && state.error == null && groups.isEmpty()) {
            item { Message(if (state.search.open) "没有符合条件的账目" else if (state.selectedDay != null) "当天还没有账目" else "这个月还没有账目\n点右下角「记一笔」，开始记录日常收支") }
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
                            Text(if (entry.transaction.type == TRANSFER) "${entry.accountName} → ${entry.transferAccountName.orEmpty()}" else entry.accountName,
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            listOfNotNull(entry.transaction.merchant, entry.transaction.location).takeIf { it.isNotEmpty() }?.let {
                                Text(it.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (entry.transaction.isReimbursable) Text(if (entry.transaction.reimbursementStatus == 1) "已报销" else "待报销",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            entry.transaction.note?.takeIf { it.isNotBlank() }?.let {
                                Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Column(Modifier.weight(.9f).padding(start = 8.dp), horizontalAlignment = Alignment.End) {
                            Text((when (entry.transaction.type) { INCOME -> "+ ¥"; TRANSFER -> "¥"; else -> "− ¥" }) + visibleAmount(entry.transaction.amountMinor),
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
        if (!state.search.open) item { overview() }
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
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val incomeColor = if (dark) Color(0xFFBCE2C6) else Color(0xFF306C50)
    val expenseColor = if (dark) Color(0xFFFFC9A5) else Color(0xFF965128)
    FeatureCard(modifier) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = MaterialTheme.typography.labelLarge)
                    Text("结余", color = LocalContentColor.current.copy(alpha = .8f), style = MaterialTheme.typography.bodySmall)
                }
                Image(painterResource(R.drawable.art_wallet), null, Modifier.size(84.dp))
            }
            Text("¥${visibleAmount(summary.income - summary.expense)}", style = MaterialTheme.typography.headlineLarge,
                fontSize = 34.sp, fontWeight = FontWeight.Bold)
            HorizontalDivider(color = LocalContentColor.current.copy(alpha = .16f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(painterResource(R.drawable.ic_ui_arrow_up_right), contentDescription = null,
                            modifier = Modifier.size(16.dp), tint = incomeColor)
                        Text("收入", color = incomeColor, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("¥${visibleAmount(summary.income)}", color = incomeColor, fontWeight = FontWeight.SemiBold)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(painterResource(R.drawable.ic_ui_arrow_down_right), contentDescription = null,
                            modifier = Modifier.size(16.dp), tint = expenseColor)
                        Text("支出", color = expenseColor, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("¥${visibleAmount(summary.expense)}", color = expenseColor, fontWeight = FontWeight.SemiBold)
                }
            }
    }
}

@Composable
private fun BudgetCard(state: HomeState, onPlanning: () -> Unit) {
    val budget = state.budget
    val remaining = budget?.let { it.amountMinor - state.monthSummary.expense }
    Card(onClick = onPlanning, modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${state.month.monthValue}月预算" + budget?.let { " · ¥${visibleAmount(it.amountMinor)}" }.orEmpty(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(if (remaining == null) "点击设置，为开销留一个界限" else if (remaining < 0) "已超支 ¥${visibleAmount(-remaining)}" else "还可支出 ¥${visibleAmount(remaining)}",
                        color = if (remaining != null && remaining < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                }
                Icon(painterResource(R.drawable.ic_ui_chevron_right), "调整预算", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (budget != null && !com.localledger.app.ui.common.LocalHideAmounts.current) {
                val progress = (state.monthSummary.expense.toDouble() / budget.amountMinor).toFloat().coerceIn(0f, 1f)
                Box(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))) {
                    Box(Modifier.fillMaxWidth(progress).height(8.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickEntries(state: HomeState, onCopy: (String) -> Unit, onAdd: () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("快捷记账", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.quickEntries.forEach { entry ->
                FilterChip(selected = false, onClick = { onCopy(entry.transaction.id) }, label = { Text(entry.categoryName) })
            }
            FilterChip(selected = false, onClick = onAdd, label = { Text("＋ 记一笔") })
        }
    }
}

@Composable
private fun MonthCalendar(state: HomeState, onSelect: (LocalDate?) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var heatType by rememberSaveable { mutableStateOf<Int?>(null) }
    val zone = ZoneId.systemDefault()
    val rows = remember(state.calendarEntries, zone) { state.calendarEntries.groupBy { Instant.ofEpochMilli(it.transaction.occurredAt).atZone(zone).toLocalDate() } }
    val amounts = rows.mapValues { (_, entries) -> entries.filter { it.transaction.type == heatType }.sumOf { it.transaction.amountMinor } }
    val maximum = amounts.values.maxOrNull()?.coerceAtLeast(1) ?: 1L
    Card(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("账单日历", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起" else "展开") }
            }
            if (expanded) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(null to "记录点", EXPENSE to "支出热力", INCOME to "收入热力").forEach { (type, label) ->
                        FilterChip(selected = heatType == type, onClick = { heatType = type }, label = { Text(label, style = MaterialTheme.typography.labelSmall) })
                    }
                }
                Row(Modifier.fillMaxWidth()) { listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                    Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } }
                val leading = state.month.atDay(1).dayOfWeek.value - 1
                val cells = ((leading + state.month.lengthOfMonth() + 6) / 7) * 7
                for (week in 0 until cells / 7) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (weekday in 0..6) {
                        val day = week * 7 + weekday - leading + 1
                        if (day !in 1..state.month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(48.dp))
                        else {
                            val date = state.month.atDay(day)
                            val selected = state.selectedDay == date
                            val amount = amounts[date] ?: 0L
                            val color = if (heatType == INCOME) Color(0xFF287B60) else Color(0xFFCF733B)
                            val background = when {
                                selected -> MaterialTheme.colorScheme.primaryContainer
                                heatType != null && amount > 0 -> color.copy(alpha = .12f + .48f * (amount.toDouble() / maximum).toFloat())
                                else -> Color.Transparent
                            }
                            Surface(Modifier.weight(1f).height(48.dp).clickable { onSelect(date) }, color = background, shape = RoundedCornerShape(12.dp),
                                border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Text(day.toString(), style = MaterialTheme.typography.bodySmall)
                                    Text(if (rows[date].isNullOrEmpty()) " " else "•", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
                Text(if (heatType == null) "圆点表示当天有记录；点击日期查看当日明细。" else "颜色越深金额越高；转账不计入收支热力。",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val today = LocalDate.now()
                val anchor = state.selectedDay ?: if (java.time.YearMonth.from(today) == state.month) today else state.month.atDay(1)
                val monday = anchor.minusDays((anchor.dayOfWeek.value - 1).toLong())
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { offset ->
                        val date = monday.plusDays(offset.toLong())
                        val enabled = java.time.YearMonth.from(date) == state.month
                        val selected = state.selectedDay == date
                        Surface(Modifier.weight(1f).height(48.dp).clickable(enabled = enabled) { onSelect(date) },
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, shape = RoundedCornerShape(10.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .35f))
                                Text(if (rows[date].isNullOrEmpty()) " " else "•", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
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
            label = { Text("搜索备注、商家、地点或账户") }, singleLine = true, shape = RoundedCornerShape(16.dp),
            leadingIcon = { Icon(painterResource(R.drawable.ic_ui_search), contentDescription = null, modifier = Modifier.size(20.dp)) })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = draft.allDates, onClick = { model.setAllDates(true) }, label = { Text("全部日期") })
            FilterChip(selected = !draft.allDates, onClick = { model.setAllDates(false) }, label = { Text("所选月份") })
            FilterChip(selected = draft.reimbursement == 0, onClick = { model.setReimbursement(if (draft.reimbursement == 0) null else 0) }, label = { Text("待报销") })
            FilterChip(selected = draft.reimbursement == 1, onClick = { model.setReimbursement(if (draft.reimbursement == 1) null else 1) }, label = { Text("已报销") })
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
                listOf(null to "全部", EXPENSE to "支出", INCOME to "收入", TRANSFER to "转账").forEach { (type, label) ->
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
