package com.localledger.app.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LocalAccent = staticCompositionLocalOf { "blue" }

private val ledgerTypography = Typography().run { copy(
    headlineLarge = headlineLarge.copy(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineMedium = headlineMedium.copy(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = headlineSmall.copy(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = titleLarge.copy(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = titleMedium.copy(fontSize = 17.sp, lineHeight = 25.sp, letterSpacing = 0.sp),
    bodyLarge = bodyLarge.copy(fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium = bodyMedium.copy(fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = bodySmall.copy(fontSize = 12.sp, lineHeight = 19.sp),
    labelLarge = labelLarge.copy(letterSpacing = 0.sp),
) }

private val ledgerShapes = Shapes(small = RoundedCornerShape(14.dp), medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp))

@Composable
fun LedgerTheme(darkTheme: Boolean = isSystemInDarkTheme(), accent: String = "blue", content: @Composable () -> Unit) {
    val scheme = if (darkTheme) darkColorScheme(
        primary = Color(0xFF89D2BF), onPrimary = Color(0xFF00392E),
        primaryContainer = Color(0xFF145145), onPrimaryContainer = Color(0xFFB8EEDD),
        secondary = Color(0xFFA4C8EE), onSecondary = Color(0xFF123C60),
        secondaryContainer = Color(0xFF254E44), onSecondaryContainer = Color(0xFFB8EEDD),
        tertiary = Color(0xFFE3B489),
        background = Color(0xFF101A17), surface = Color(0xFF17231F),
        surfaceVariant = Color(0xFF2A3B34), surfaceContainerHigh = Color(0xFF203129),
        surfaceContainerHighest = Color(0xFF2A3B34),
        onSurface = Color(0xFFE1EBE5), onSurfaceVariant = Color(0xFFB6C9BF), outlineVariant = Color(0xFF354840),
    ) else lightColorScheme(
        primary = Color(0xFF267666), onPrimary = Color.White,
        primaryContainer = Color(0xFFD8EDE6), onPrimaryContainer = Color(0xFF123C32),
        secondary = Color(0xFF365D83), onSecondary = Color.White,
        secondaryContainer = Color(0xFFE1EDE8), onSecondaryContainer = Color(0xFF194D40),
        tertiary = Color(0xFF986442), background = Color(0xFFF4F6F5),
        surface = Color.White, surfaceVariant = Color(0xFFE8EEEB),
        surfaceContainerHigh = Color(0xFFECF2EF), surfaceContainerHighest = Color(0xFFE3EBE7),
        onSurface = Color(0xFF1A2D28), onSurfaceVariant = Color(0xFF63766E),
        outlineVariant = Color(0xFFDCE5E0),
    )
    val colors = if (accent == "green") scheme else if (darkTheme) scheme.copy(
        primary = Color(0xFFAEBEFF), onPrimary = Color(0xFF142967),
        primaryContainer = Color(0xFF263653), onPrimaryContainer = Color(0xFFDEE5FF),
        secondary = Color(0xFFADCBEF), secondaryContainer = Color(0xFF263C58),
        onSecondaryContainer = Color(0xFFD9E8FF),
        background = Color(0xFF11151C), surface = Color(0xFF1B202A),
        surfaceVariant = Color(0xFF2C3142), surfaceContainerHigh = Color(0xFF222735),
        surfaceContainerHighest = Color(0xFF2C3142),
        onSurface = Color(0xFFE7E9F3), onSurfaceVariant = Color(0xFFB9C0D5),
        outlineVariant = Color(0xFF353C50),
    ) else scheme.copy(
        primary = Color(0xFF4F66D5), onPrimary = Color.White,
        primaryContainer = Color(0xFFEAF0FF), onPrimaryContainer = Color(0xFF2B4279),
        secondary = Color(0xFF46668D), secondaryContainer = Color(0xFFE6EEFA),
        onSecondaryContainer = Color(0xFF2C456B),
        background = Color(0xFFF7F8FC), surface = Color.White,
        surfaceVariant = Color(0xFFEBEEF7), surfaceContainerHigh = Color(0xFFEDF0F9),
        surfaceContainerHighest = Color(0xFFE5E9F4),
        onSurface = Color(0xFF252C3E), onSurfaceVariant = Color(0xFF6B7589),
        outlineVariant = Color(0xFFE2E7F0),
    )
    CompositionLocalProvider(LocalAccent provides accent) {
        MaterialTheme(colorScheme = colors, typography = ledgerTypography, shapes = ledgerShapes, content = content)
    }
}
