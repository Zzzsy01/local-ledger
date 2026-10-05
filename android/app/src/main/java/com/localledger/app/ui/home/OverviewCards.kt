package com.localledger.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localledger.app.data.repository.LedgerRepository
import com.localledger.app.data.repository.AssetRepository
import com.localledger.app.domain.summarizeAssets
import com.localledger.app.ui.common.LocalHideAmounts
import com.localledger.app.ui.common.visibleAmount
import java.math.BigInteger
import java.math.BigDecimal

@Composable
fun OverviewCards(ledger: LedgerRepository, assets: AssetRepository, onAccounts: () -> Unit, onAssets: () -> Unit) {
    val balances by ledger.balances.collectAsStateWithLifecycle(emptyList())
    val items by assets.assets.collectAsStateWithLifecycle(emptyList())
    val cash = balances.fold(BigInteger.ZERO) { total, balance -> total + BigInteger.valueOf(balance.balanceMinor) }
    val held = summarizeAssets(items)
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(onClick = onAccounts, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("账户余额", style = MaterialTheme.typography.labelLarge)
                Text("¥${if (LocalHideAmounts.current) "••••" else BigDecimal(cash, 2).toPlainString()}", style = MaterialTheme.typography.headlineLarge)
                Text("${balances.size} 个账户 · 包含已停用账户余额", style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(onClick = onAssets, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) { Text("我的博物馆", style = MaterialTheme.typography.titleSmall); Text("${held.heldQuantity} 件 · ${held.idleQuantity} 件闲置", style = MaterialTheme.typography.bodySmall) }
                Column(Modifier.weight(1f)) { Text("已填估值", style = MaterialTheme.typography.labelSmall); Text("¥${visibleAmount(held.heldValue)}", style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}
