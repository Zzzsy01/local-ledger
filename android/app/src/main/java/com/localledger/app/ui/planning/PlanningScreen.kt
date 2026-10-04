package com.localledger.app.ui.planning

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localledger.app.domain.*
import com.localledger.app.ui.common.Loading
import com.localledger.app.ui.common.Message
import com.localledger.app.ui.common.MonthPicker
import com.localledger.app.ui.common.visibleAmount
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun PlanningScreen(model: PlanningViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val draft by model.draft.collectAsStateWithLifecycle()
    var editBudget by rememberSaveable { mutableStateOf(false) }
    var budgetAmount by rememberSaveable { mutableStateOf("") }
    var confirmId by rememberSaveable { mutableStateOf<String?>(null) }
    var disableId by rememberSaveable { mutableStateOf<String?>(null) }
    val todayEnd = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { MonthPicker(state.month.month, model::changeMonth) }
        if (state.loading) item { Loading() }
        state.error?.let { item { Message(it, isError = true) } }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("月预算", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    val budget = state.month.budget
                    if (budget == null) Text("设置本月可用额度，追踪支出进度", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else {
                        Text("预算 ¥${visibleAmount(budget.amountMinor)}", style = MaterialTheme.typography.titleMedium)
                        Text("已用 ¥${visibleAmount(state.month.expense)} · 剩余 ¥${visibleAmount(budget.amountMinor - state.month.expense)}")
                        LinearProgressIndicator(progress = { (state.month.expense.toDouble() / budget.amountMinor).coerceIn(0.0, 1.0).toFloat() }, modifier = Modifier.fillMaxWidth())
                        if (state.month.expense > budget.amountMinor) Text("已超出预算 ¥${visibleAmount(state.month.expense - budget.amountMinor)}", color = MaterialTheme.colorScheme.error)
                    }
                    Row {
                        TextButton(enabled = !busy, onClick = { budgetAmount = budget?.amountMinor?.let(::formatAmount).orEmpty(); editBudget = true }) { Text(if (budget == null) "设置预算" else "修改预算") }
                        if (budget != null) TextButton(enabled = !busy, onClick = model::clearBudget) { Text("移除预算") }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("固定收支", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                TextButton(enabled = !busy, onClick = { model.openRule() }) { Text("新增") }
            }
            Text("订阅、房租、工资等按月提醒，确认后才记入账本；可在设置开启到期通知。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        error?.let { item { Message(it, isError = true) } }
        message?.let { item { Message(it) } }
        if (state.rules.isEmpty() && !state.loading) item { Message("还没有固定账目") }
        items(state.rules, key = { it.id }) { rule ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(rule.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text((if (rule.type == INCOME) "+" else "−") + "¥${visibleAmount(rule.amountMinor)}", fontWeight = FontWeight.SemiBold)
                    }
                    val date = Instant.ofEpochMilli(rule.nextDueAt).atZone(ZoneId.systemDefault()).toLocalDate()
                    val category = state.categories.firstOrNull { it.id == rule.categoryId }
                    val account = state.accounts.firstOrNull { it.id == rule.accountId }
                    Text("${category?.name.orEmpty()} · ${account?.name.orEmpty()} · 每月 ${rule.dayOfMonth} 日", style = MaterialTheme.typography.bodySmall)
                    Text("${if (rule.nextDueAt < todayEnd) "待处理" else "下次到期"}：$date", color = if (rule.nextDueAt < todayEnd) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (category?.isDeleted == true || account?.isDeleted == true) Text("分类或账户已停用，确认前请编辑更换", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (rule.nextDueAt < todayEnd) {
                            TextButton(enabled = !busy, onClick = { confirmId = rule.id }) { Text("确认记账") }
                            TextButton(enabled = !busy, onClick = { model.process(rule, false) }) { Text("跳过") }
                        }
                        TextButton(enabled = !busy, onClick = { model.openRule(rule) }) { Text("编辑") }
                        TextButton(enabled = !busy, onClick = { disableId = rule.id }) { Text("停用") }
                    }
                }
            }
        }
    }
    if (editBudget) AlertDialog(onDismissRequest = { if (!busy) editBudget = false }, title = { Text("${state.month.month} 月预算") },
        text = { Column {
            OutlinedTextField(budgetAmount, { budgetAmount = it }, label = { Text("额度（元）") }, singleLine = true, enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }, confirmButton = { TextButton(enabled = !busy, onClick = { model.saveBudget(budgetAmount) { editBudget = false } }) { Text("保存") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { editBudget = false }) { Text("取消") } })
    state.rules.find { it.id == confirmId }?.let { rule ->
        AlertDialog(onDismissRequest = { confirmId = null }, title = { Text("确认本期记账") },
            text = { Text("${rule.name} · ¥${visibleAmount(rule.amountMinor)}\n账目日期使用本期到期日，确认后推进到下个月。") },
            confirmButton = { TextButton(enabled = !busy, onClick = { confirmId = null; model.process(rule, true) }) { Text("确认记账") } },
            dismissButton = { TextButton(onClick = { confirmId = null }) { Text("取消") } })
    }
    state.rules.find { it.id == disableId }?.let { rule ->
        AlertDialog(onDismissRequest = { disableId = null }, title = { Text("停用固定账目？") }, text = { Text("${rule.name} 将不再提醒，已记账流水保留。") },
            confirmButton = { TextButton(enabled = !busy, onClick = { disableId = null; model.disable(rule) }) { Text("停用") } },
            dismissButton = { TextButton(onClick = { disableId = null }) { Text("取消") } })
    }
    draft?.let { RuleEditor(model, state, it, busy, error) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleEditor(model: PlanningViewModel, state: PlanningState, draft: RuleDraft, busy: Boolean, error: String?) {
    val context = LocalContext.current
    AlertDialog(onDismissRequest = model::closeEditor, title = { Text(if (draft.id == null) "新增固定账目" else "编辑固定账目") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(draft.name, { value -> model.changeDraft { it.copy(name = value) } }, label = { Text("名称，例如房租或会员") }, enabled = !busy, singleLine = true)
                OutlinedTextField(draft.amount, { value -> model.changeDraft { it.copy(amount = value) } }, label = { Text("金额（元）") }, enabled = !busy, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(draft.type == EXPENSE, onClick = { model.changeDraft { it.copy(type = EXPENSE, categoryId = null) } }, label = { Text("支出") }, enabled = !busy)
                    FilterChip(draft.type == INCOME, onClick = { model.changeDraft { it.copy(type = INCOME, categoryId = null) } }, label = { Text("收入") }, enabled = !busy)
                }
                Text("分类")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.categories.filter { it.type == draft.type && (!it.isDeleted || it.id == draft.categoryId) }.forEach { category ->
                        FilterChip(draft.categoryId == category.id, onClick = { model.changeDraft { it.copy(categoryId = category.id) } },
                            label = { Text(category.name + if (category.isDeleted) "（停用）" else "") }, enabled = !busy)
                    }
                }
                Text("账户")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.accounts.filter { !it.isDeleted || it.id == draft.accountId }.forEach { account ->
                        FilterChip(draft.accountId == account.id, onClick = { model.changeDraft { it.copy(accountId = account.id) } },
                            label = { Text(account.name + if (account.isDeleted) "（停用）" else "") }, enabled = !busy)
                    }
                }
                TextButton(enabled = !busy, onClick = {
                    DatePickerDialog(context, { _, year, month, day -> model.changeDraft { it.copy(date = LocalDate.of(year, month + 1, day), day = day.toString()) } },
                        draft.date.year, draft.date.monthValue - 1, draft.date.dayOfMonth).show()
                }) { Text("下次到期：${draft.date}") }
                OutlinedTextField(draft.day, { value -> model.changeDraft { it.copy(day = value) } }, label = { Text("每月目标日（1—31）") }, enabled = !busy, singleLine = true,
                    supportingText = { Text("不足目标日的月份使用月末，下月仍保留目标日") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(draft.note, { value -> model.changeDraft { it.copy(note = value) } }, label = { Text("备注（选填）") }, enabled = !busy, maxLines = 3)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = { TextButton(enabled = !busy, onClick = model::saveRule) { Text(if (busy) "保存中…" else "保存") } },
        dismissButton = { TextButton(enabled = !busy, onClick = model::closeEditor) { Text("取消") } })
}
