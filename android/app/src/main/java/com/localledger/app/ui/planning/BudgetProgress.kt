package com.localledger.app.ui.planning

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.localledger.app.domain.budgetPace
import com.localledger.app.ui.common.LocalHideAmounts
import com.localledger.app.ui.common.visibleAmount
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun BudgetProgress(month: YearMonth, amountMinor: Long, expenseMinor: Long, onEntries: () -> Unit) {
    var today by remember { mutableStateOf(LocalDate.now()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { today = LocalDate.now() }
    val pace = budgetPace(month, amountMinor, expenseMinor, today)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("已记录支出 ¥${visibleAmount(expenseMinor)}", style = MaterialTheme.typography.bodySmall)
        pace.dailyMinor?.let { Text("含今天还剩 ${pace.remainingDays} 天 · 日均额度 ¥${visibleAmount(it)}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        if (pace.remainingDays == 0) Text(if (today.isBefore(month.atDay(1))) "预算期尚未开始" else "预算期已结束",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!LocalHideAmounts.current) {
            Text("消费进度", style = MaterialTheme.typography.labelSmall)
            LinearProgressIndicator(progress = { (expenseMinor.toDouble() / amountMinor).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Text("日历进度 · ${pace.elapsedDays}/${month.lengthOfMonth()} 天", style = MaterialTheme.typography.labelSmall)
            LinearProgressIndicator(progress = { pace.elapsedDays.toFloat() / month.lengthOfMonth() }, modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondary)
        }
        TextButton(onClick = onEntries) { Text("查看本月支出") }
    }
}
