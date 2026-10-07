package com.localledger.app.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localledger.app.domain.INCOME
import java.time.YearMonth

@Composable
internal fun MonthPicker(month: YearMonth, onChange: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onChange(-1) }) { Text("上月") }
        Text("${month.year}年${month.monthValue}月", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        TextButton(onClick = { onChange(1) }) { Text("下月") }
    }
}

@Composable
internal fun amountColor(type: Int): Color = when(type) { INCOME -> Color(0xFF398365); com.localledger.app.domain.TRANSFER -> Color(0xFF547EA8); else -> Color(0xFFB66C60) }

@Composable
internal fun Loading() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
internal fun Message(text: String, isError: Boolean = false) {
    Text(text, Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun PageHeading(title: String, subtitle: String, illustration: Int, modifier: Modifier = Modifier, eyebrow: String = "随手账 · 日常收藏") {
    val compact = LocalConfiguration.current.screenWidthDp < 360 || LocalDensity.current.fontScale > 1.2f
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.size(if (compact) 76.dp else 108.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(if (compact) 62.dp else 90.dp).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .6f), CircleShape))
            Image(painterResource(illustration), null, Modifier.fillMaxSize())
        }
    }
}

@Composable
internal fun FeatureCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val blue = LocalAccent.current == "blue"
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val start = if (dark) { if (blue) Color(0xFF293D62) else Color(0xFF284C40) }
        else if (blue) Color(0xFFDDEBFF) else Color(0xFFDDF3E5)
    val end = if (dark) { if (blue) Color(0xFF405E80) else Color(0xFF456857) }
        else if (blue) Color(0xFFF0F6FF) else Color(0xFFFFF0CE)
    val ink = if (dark) Color(0xFFF8F5E9) else if (blue) Color(0xFF29466B) else Color(0xFF244D38)
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp, 28.dp, 10.dp, 28.dp), color = start, contentColor = ink) {
        Column(Modifier.background(Brush.linearGradient(listOf(start, end))).drawBehind {
            val center = Offset(size.width * 1.05f, size.height * .18f)
            drawCircle(ink.copy(alpha = .035f), size.width * .53f, center)
            drawCircle(ink.copy(alpha = .09f), size.width * .68f, center, style = Stroke(1.dp.toPx()))
            drawCircle(ink.copy(alpha = .07f), size.width * .82f, center, style = Stroke(1.dp.toPx()))
        }.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
        }
    }
}
