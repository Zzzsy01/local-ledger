package com.localledger.app.ui.add

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localledger.app.domain.EXPENSE
import com.localledger.app.domain.INCOME
import com.localledger.app.ui.common.Loading
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryScreen(model: EntryViewModel, onSavingChanged: (Boolean) -> Unit = {}, onSaved: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val draft = state.draft
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var amountFocused by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = draft.saving) { /* Wait for the atomic save before leaving. */ }
    LaunchedEffect(draft.saving) { onSavingChanged(draft.saving) }
    DisposableEffect(Unit) { onDispose { onSavingChanged(false) } }
    LaunchedEffect(draft.saved) { if (draft.saved) { keyboard?.hide(); onSaved() } }
    val categories = state.categories.filter { it.type == draft.type && (!it.isDeleted || it.id == draft.categoryId) }
    val accounts = state.accounts.filter { !it.isDeleted || it.id == draft.accountId }
    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (model.copying) item {
                Text("根据原账目再记一笔，日期为今天。已停用的分类或账户需重新选择。",
                    Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                var amountLaidOut by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = draft.amount, onValueChange = model::setAmount,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus)
                        .onGloballyPositioned { amountLaidOut = true },
                    label = { Text("金额") }, prefix = { Text("¥") },
                    supportingText = { Text("人民币 · 最多两位小数") },
                    singleLine = true, enabled = !draft.loading && !draft.saving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { model.save() }),
                    textStyle = MaterialTheme.typography.headlineSmall,
                )
                LaunchedEffect(draft.loading, amountLaidOut) {
                    if (!draft.loading && amountLaidOut && !amountFocused) {
                        focus.requestFocus()
                        amountFocused = true
                        keyboard?.show()
                    }
                }
            }
            if (draft.loading) item { Loading() }
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = draft.type == EXPENSE, onClick = { model.setType(EXPENSE) }, label = { Text("支出") }, enabled = !draft.loading && !draft.saving)
                    FilterChip(selected = draft.type == INCOME, onClick = { model.setType(INCOME) }, label = { Text("收入") }, enabled = !draft.loading && !draft.saving)
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("分类", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        categories.forEach { category ->
                            FilterChip(selected = draft.categoryId == category.id,
                                onClick = { model.setCategory(category.id) }, enabled = !draft.loading && !draft.saving,
                                label = { Text(category.name + if (category.isDeleted) "（已停用）" else "") })
                        }
                    }
                    if (categories.isEmpty() && !draft.loading) Text("暂无可用分类，请先在分类管理中添加", style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("账户", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        accounts.forEach { account ->
                            FilterChip(selected = draft.accountId == account.id,
                                onClick = { model.setAccount(account.id) }, enabled = !draft.loading && !draft.saving,
                                label = { Text(account.name + if (account.isDeleted) "（已停用）" else "") })
                        }
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("日期", style = MaterialTheme.typography.titleSmall)
                    TextButton(enabled = !draft.loading && !draft.saving, onClick = {
                        DatePickerDialog(context, { _, year, month, day -> model.setDate(LocalDate.of(year, month + 1, day)) },
                            draft.date.year, draft.date.monthValue - 1, draft.date.dayOfMonth).show()
                    }) { Text(draft.date.toString()) }
                }
            }
            item {
                OutlinedTextField(value = draft.note, onValueChange = model::setNote,
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(), label = { Text("备注（选填）") },
                    enabled = !draft.loading && !draft.saving, minLines = 1, maxLines = 4)
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
        draft.error?.let { error ->
            Text(error, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = model::save, enabled = !draft.loading && !draft.saving && !draft.saved,
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp)) {
            if (draft.saving) {
                CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(if (draft.saving) "保存中…" else "保存账目")
        }
    }
}
