package com.localledger.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localledger.app.domain.CategoryTotal
import com.localledger.app.ui.common.LocalHideAmounts
import com.localledger.app.ui.common.visibleAmount
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToInt

internal data class ChartValue(val label: String, val amount: Long)

@Composable
internal fun TimelineChart(values: List<ChartValue>, bars: Boolean = false) {
    val hidden = LocalHideAmounts.current
    if (hidden) {
        Text("金额已隐藏，关闭隐藏金额后可查看图表。", Modifier.padding(vertical = 24.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        return
    }
    if (values.isEmpty()) return
    var selected by remember(values) { mutableIntStateOf(values.indexOfLast { it.amount > 0 }.takeIf { it >= 0 } ?: 0) }
    val point = values[selected]
    val pointAmount = visibleAmount(point.amount)
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val maximum = values.maxOf { it.amount }.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(point.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("¥$pointAmount", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = { selected-- }, enabled = selected > 0) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "上一个数据点") }
            IconButton(onClick = { selected++ }, enabled = selected < values.lastIndex) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "下一个数据点") }
        }
        Canvas(Modifier.fillMaxWidth().height(170.dp)
            .semantics { contentDescription = "${if (bars) "柱状" else "趋势"}图，${point.label}，金额 $pointAmount 元。可用前后按钮查看各数据点。" }
            .pointerInput(values, bars) {
                detectTapGestures { tap ->
                    val left = 12.dp.toPx()
                    val width = size.width - left * 2
                    selected = if (bars) (((tap.x - left) / width) * values.size).toInt().coerceIn(values.indices)
                        else (((tap.x - left) / width) * values.lastIndex).roundToInt().coerceIn(values.indices)
                }
            }) {
            val left = 12.dp.toPx()
            val top = 10.dp.toPx()
            val bottom = size.height - 8.dp.toPx()
            val width = size.width - left * 2
            val height = bottom - top
            for (line in 0..3) {
                val y = top + height * line / 3
                drawLine(grid, Offset(left, y), Offset(size.width - left, y), strokeWidth = 1.dp.toPx())
            }
            fun x(index: Int): Float = if (bars) left + width * (index + .5f) / values.size
                else if (values.size == 1) size.width / 2 else left + width * index / values.lastIndex
            fun y(index: Int): Float = bottom - height * chartFraction(values[index].amount, maximum)
            if (bars) {
                val barWidth = (width / values.size * .58f).coerceAtMost(32.dp.toPx())
                values.indices.forEach { index ->
                    val barHeight = (bottom - y(index)).coerceAtLeast(2.dp.toPx())
                    drawRoundRect(color.copy(alpha = if (index == selected) 1f else .42f),
                        topLeft = Offset(x(index) - barWidth / 2, bottom - barHeight), size = Size(barWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                }
            } else {
                val line = Path().apply { values.indices.forEach { index ->
                    if (index == 0) moveTo(x(index), y(index)) else lineTo(x(index), y(index))
                } }
                val area = Path().apply { addPath(line); lineTo(x(values.lastIndex), bottom); lineTo(x(0), bottom); close() }
                drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = .22f), color.copy(alpha = .01f)), top, bottom))
                drawPath(line, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                drawLine(color.copy(alpha = .25f), Offset(x(selected), top), Offset(x(selected), bottom), strokeWidth = 1.dp.toPx())
                drawCircle(color.copy(alpha = .16f), 9.dp.toPx(), Offset(x(selected), y(selected)))
                drawCircle(color, 4.dp.toPx(), Offset(x(selected), y(selected)))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(0, values.lastIndex / 2, values.lastIndex).distinct().forEach { index ->
                Text(values[index].label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun CategoryRing(categories: List<CategoryTotal>, total: Long) {
    if (categories.isEmpty()) { Text("这个周期还没有记录", color = MaterialTheme.colorScheme.onSurfaceVariant); return }
    val hidden = LocalHideAmounts.current
    val palette = listOf(MaterialTheme.colorScheme.primary, Color(0xFF69A8EE), Color(0xFF39B7B7),
        Color(0xFFB098E5), Color(0xFFE5AD6B), MaterialTheme.colorScheme.secondary)
    var selectedId by remember(categories) { mutableStateOf(categories.first().categoryId) }
    val selected = categories.first { it.categoryId == selectedId }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (!hidden) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(184.dp).semantics { contentDescription = "分类占比图，${selected.name}，${reportPercentage(selected.total, total)}" }) {
                val stroke = 30.dp.toPx()
                val inset = stroke / 2 + 4.dp.toPx()
                var start = -90f
                categories.forEachIndexed { index, category ->
                    val sweep = chartFraction(category.total, total) * 360f
                    drawArc(palette[index % palette.size], start, sweep, false,
                        topLeft = Offset(inset, inset), size = Size(size.width - inset * 2, size.height - inset * 2),
                        style = Stroke(width = if (category.categoryId == selectedId) stroke else stroke - 5.dp.toPx()))
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("¥${visibleAmount(selected.total)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(selected.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        categories.forEachIndexed { index, category ->
            Surface(onClick = { selectedId = category.categoryId }, color = if (selectedId == category.categoryId)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = .5f) else Color.Transparent,
                shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(9.dp).background(palette[index % palette.size], CircleShape))
                        Text(category.name, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        Text("¥${visibleAmount(category.total)}", fontWeight = FontWeight.SemiBold)
                    }
                    if (!hidden) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LinearProgressIndicator(progress = { chartFraction(category.total, total) }, Modifier.weight(1f).height(4.dp),
                            color = palette[index % palette.size], trackColor = MaterialTheme.colorScheme.surfaceVariant)
                        Text(reportPercentage(category.total, total), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

internal fun reportPercentage(value: Long, total: Long): String = if (total <= 0) "0.0%" else
    BigDecimal.valueOf(value).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP).toPlainString() + "%"

// Only chart geometry uses floating point; amounts and averages use integer cents.
internal fun chartFraction(value: Long, total: Long): Float = if (total <= 0) 0f else
    (value.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
