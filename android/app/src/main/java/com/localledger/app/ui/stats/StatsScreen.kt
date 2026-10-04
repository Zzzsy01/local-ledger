package com.localledger.app.ui.stats

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localledger.app.domain.*
import com.localledger.app.ui.common.LocalHideAmounts
import com.localledger.app.ui.common.Loading
import com.localledger.app.ui.common.Message
import com.localledger.app.ui.common.visibleAmount
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun StatsScreen(model: StatsViewModel, onEdit: (String) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { model.refreshTimeZone() }
    var customOpen by rememberSaveable { mutableStateOf(false) }
    val total = if (state.type == INCOME) state.summary.income else state.summary.expense
    val previous = if (state.type == INCOME) state.previous.income else state.previous.expense
    val yearAgo = if (state.type == INCOME) state.yearAgo.income else state.yearAgo.expense
    val kind = if (state.type == INCOME) "收入" else "支出"
    val hidden = LocalHideAmounts.current
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("收支报表", style = MaterialTheme.typography.headlineSmall)
            Text("看清变化，找到花钱的方向", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                ReportPeriod.entries.forEachIndexed { index, period ->
                    SegmentedButton(selected = state.period == period,
                        onClick = { if (period == ReportPeriod.CUSTOM) customOpen = true else model.setPeriod(period) },
                        shape = SegmentedButtonDefaults.itemShape(index, ReportPeriod.entries.size), icon = {}) { Text(period.label) }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                if (state.period != ReportPeriod.CUSTOM) TextButton(onClick = { model.changePeriod(-1) }) { Text("上一${periodUnit(state.period)}") }
                TextButton(onClick = { customOpen = true }, modifier = if (state.period == ReportPeriod.CUSTOM) Modifier.fillMaxWidth() else Modifier.weight(1f)) {
                    Text(rangeLabel(state), style = MaterialTheme.typography.titleSmall)
                }
                if (state.period != ReportPeriod.CUSTOM) TextButton(onClick = { model.changePeriod(1) }) { Text("下一${periodUnit(state.period)}") }
            }
        }
        item { Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(EXPENSE to "支出", INCOME to "收入").forEach { (type, label) ->
                FilterChip(state.type == type, { model.setType(type) }, label = { Text(label) })
            }
        } }
        if (state.loading) item { Loading() }
        state.error?.let { error -> item { Message(error, isError = true) } }
        if (!state.loading && state.error == null) {
            item { ReportOverview(state, total, kind) }
            item { ReportCard("本期小结") {
                val top = state.categories.firstOrNull()
                Text(if (top == null) "这个周期还没有${kind}记录。" else
                    "${kind}最多的是「${top.name}」${if (hidden) "。" else "，占 ${reportPercentage(top.total, total)}。"}",
                    style = MaterialTheme.typography.bodyMedium)
                if (!hidden) {
                    percentageChange(total, previous)?.let { Text("比${state.previousLabel} $it", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
                    if (state.period in listOf(ReportPeriod.MONTH, ReportPeriod.YEAR)) percentageChange(total, yearAgo)?.let {
                        Text("同比 $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (previous == 0L) Text("${state.previousLabel}没有同类金额，暂不计算变化比例。", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!LocalDate.now().isBefore(state.range.from) && !LocalDate.now().isAfter(state.range.through)) {
                    Text("当前周期尚未结束，变化比例与完整前期比较。", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } }
            item { ReportCard("${kind}趋势") {
                Text(if (state.monthlyPoints) "按月汇总 · 点选图表查看金额" else "按日汇总 · 点选图表查看金额",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TimelineChart(state.points.map { point -> ChartValue(point.date.format(DateTimeFormatter.ofPattern(
                    if (state.monthlyPoints) "yyyy/M" else "M/d")), if (state.type == INCOME) point.summary.income else point.summary.expense) })
            } }
            item { ReportCard("${kind}分类构成") { CategoryRing(state.categories, total) } }
            item { ReportCard(if (state.period == ReportPeriod.YEAR) "全年月度${kind}对比" else "近六个月${kind}对比") {
                if (state.period != ReportPeriod.YEAR) Text("按完整自然月统计，包含所选区间之外的账目。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TimelineChart(state.trend.map { month -> ChartValue("${month.month.monthValue}月",
                    if (state.type == INCOME) month.summary.income else month.summary.expense) }, bars = true)
            } }
            item { ReportCard("账户${kind}分布") {
                if (state.accounts.isEmpty()) Text("这个周期还没有记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.accounts.forEach { account ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(account.name, Modifier.weight(1f))
                        Text("¥${visibleAmount(account.total)}", fontWeight = FontWeight.Medium)
                    }
                    if (!hidden) LinearProgressIndicator(progress = { chartFraction(account.total, total) }, Modifier.fillMaxWidth().height(4.dp))
                }
                Text("支付账户的收支金额，不代表账户余额。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
            item { ReportCard("${kind}排行 · 前 10 笔") {
                if (state.ranking.isEmpty()) Text("这个周期还没有记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.ranking.forEachIndexed { index, entry ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(Modifier.fillMaxWidth().clickable { onEdit(entry.transaction.id) }.padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text((index + 1).toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(entry.categoryName, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            Text("${if (state.type == INCOME) "+" else "−"}¥${visibleAmount(entry.transaction.amountMinor)}", fontWeight = FontWeight.SemiBold)
                        }
                        val date = Instant.ofEpochMilli(entry.transaction.occurredAt).atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("yyyy/M/d HH:mm"))
                        Text("$date · ${entry.accountName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        entry.transaction.note?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            } }
        }
    }
    if (customOpen) CustomRangeDialog(state.range, onDismiss = { customOpen = false }, onConfirm = { from, through ->
        model.setCustomRange(from, through); customOpen = false
    })
}

@Composable
private fun ReportOverview(state: StatsState, total: Long, kind: String) {
    Card(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("本期结余", style = MaterialTheme.typography.labelLarge)
            Text("¥${visibleAmount(state.summary.income - state.summary.expense)}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OverviewValue("收入", state.summary.income, Modifier.weight(1f))
                OverviewValue("支出", state.summary.expense, Modifier.weight(1f))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .12f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OverviewValue("区间日均${kind}", averageMinor(total, state.range.days), Modifier.weight(1f))
                OverviewValue("比${state.previousLabel}${kind}", total - if (state.type == INCOME) state.previous.income else state.previous.expense, Modifier.weight(1f))
            }
            Text("共 ${state.metrics.transactionCount} 笔 · 日均按区间 ${state.range.days} 天计算", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OverviewValue("单笔平均支出", averageMinor(state.summary.expense, state.metrics.expenseCount), Modifier.weight(1f))
                OverviewValue("最大单笔支出", state.metrics.largestExpense, Modifier.weight(1f))
            }
            if (!LocalHideAmounts.current && state.summary.income > 0) Text("结余率 ${reportPercentage(state.summary.income - state.summary.expense, state.summary.income)}",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ReportCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(.7.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun OverviewValue(label: String, amount: Long, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text("¥${visibleAmount(amount)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

private fun periodUnit(period: ReportPeriod): String = when (period) {
    ReportPeriod.WEEK -> "周"; ReportPeriod.YEAR -> "年"; else -> "月"
}

private fun rangeLabel(state: StatsState): String = when (state.period) {
    ReportPeriod.MONTH -> "${state.range.from.year}年${state.range.from.monthValue}月"
    ReportPeriod.YEAR -> "${state.range.from.year}年"
    else -> "${state.range.from.format(DateTimeFormatter.ofPattern("yyyy/M/d"))} — ${state.range.through.format(DateTimeFormatter.ofPattern(if (state.range.from.year == state.range.through.year) "M/d" else "yyyy/M/d"))}"
}

@Composable
private fun CustomRangeDialog(initial: ReportRange, onDismiss: () -> Unit, onConfirm: (LocalDate, LocalDate) -> Unit) {
    val context = LocalContext.current
    var fromDay by rememberSaveable { mutableLongStateOf(initial.from.toEpochDay()) }
    var throughDay by rememberSaveable { mutableLongStateOf(initial.through.toEpochDay()) }
    val from = LocalDate.ofEpochDay(fromDay)
    val through = LocalDate.ofEpochDay(throughDay)
    fun pick(selected: LocalDate, onPicked: (LocalDate) -> Unit) {
        DatePickerDialog(context, { _, year, month, day -> onPicked(LocalDate.of(year, month + 1, day)) },
            selected.year, selected.monthValue - 1, selected.dayOfMonth).apply {
            datePicker.minDate = LocalDate.of(1970, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.show()
    }
    val valid = !through.isBefore(from) && !from.isBefore(LocalDate.of(1970, 1, 1))
    AlertDialog(onDismissRequest = onDismiss, title = { Text("自定义报表日期") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { pick(from) { fromDay = it.toEpochDay() } }) { Text("开始：$from") }
            TextButton(onClick = { pick(through) { throughDay = it.toEpochDay() } }) { Text("结束：$through") }
            Text("包含开始和结束当天；超过 62 天的趋势按月汇总。", style = MaterialTheme.typography.bodySmall)
            if (!valid) Text("结束日期不能早于开始日期。", color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(onClick = { onConfirm(from, through) }, enabled = valid) { Text("查看报表") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
