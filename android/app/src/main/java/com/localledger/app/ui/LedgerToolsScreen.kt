package com.localledger.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.localledger.app.R

@Composable
fun LedgerToolsScreen(open: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("记账工具", style = MaterialTheme.typography.headlineSmall)
            Text("管理资金、安排收支、整理账单", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { Text("资金管理", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
        item { ToolLink("账户管理", "查看余额，新增或调整资金账户", "accounts", open) }
        item { ToolLink("预算与固定账目", "月预算、订阅及定期收支", "planning", open) }
        item { ToolLink("报销管理", "跟进待报销和已报销账目", "reimbursement", open) }
        item { Text("账单整理", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
        item { ToolLink("截图与自动采集", "离线识别截图，核对支付通知", "capture", open) }
        item { ToolLink("导入账单 CSV", "预览微信、支付宝等账单后导入", "import", open) }
        item { ToolLink("记账分类", "新增、排序、改名和停用分类", "category", open) }
        item { Text("完整备份、隐私、提醒及应用更新在右上角设置。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun ToolLink(title: String, description: String, route: String, open: (String) -> Unit) {
    Card(onClick = { open(route) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(painterResource(R.drawable.ic_ui_chevron_right), null, Modifier.size(18.dp))
        }
    }
}
