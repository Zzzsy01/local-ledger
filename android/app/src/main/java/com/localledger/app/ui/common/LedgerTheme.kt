package com.localledger.app.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalAccent = staticCompositionLocalOf { "blue" }

@Composable
fun LedgerTheme(darkTheme: Boolean = isSystemInDarkTheme(), accent: String = "blue", content: @Composable () -> Unit) {
    val scheme = if (darkTheme) darkColorScheme(
        primary = Color(0xFF89D2BF), onPrimary = Color(0xFF00392E),
        primaryContainer = Color(0xFF145145), onPrimaryContainer = Color(0xFFB8EEDD),
        secondary = Color(0xFFA4C8EE), onSecondary = Color(0xFF123C60),
        secondaryContainer = Color(0xFF254E44), onSecondaryContainer = Color(0xFFB8EEDD),
        background = Color(0xFF101A17), surface = Color(0xFF14201C),
        surfaceVariant = Color(0xFF2A3B34), surfaceContainerHigh = Color(0xFF203129),
        surfaceContainerHighest = Color(0xFF2A3B34),
        onSurface = Color(0xFFE1EBE5), onSurfaceVariant = Color(0xFFB6C9BF),
    ) else lightColorScheme(
        primary = Color(0xFF146657), onPrimary = Color.White,
        primaryContainer = Color(0xFFD8EDE6), onPrimaryContainer = Color(0xFF123C32),
        secondary = Color(0xFF365D83), onSecondary = Color.White,
        secondaryContainer = Color(0xFFE1EDE8), onSecondaryContainer = Color(0xFF194D40),
        tertiary = Color(0xFFB66B3F), background = Color(0xFFF4F6F5),
        surface = Color.White, surfaceVariant = Color(0xFFE8EEEB),
        surfaceContainerHigh = Color(0xFFECF2EF), surfaceContainerHighest = Color(0xFFE3EBE7),
        onSurface = Color(0xFF1A2D28), onSurfaceVariant = Color(0xFF63766E),
        outlineVariant = Color(0xFFDCE5E0),
    )
    val colors = if (accent == "green") scheme else if (darkTheme) scheme.copy(
        primary = Color(0xFFAEBEFF), onPrimary = Color(0xFF142967),
        primaryContainer = Color(0xFF263C7B), onPrimaryContainer = Color(0xFFDEE5FF),
        secondary = Color(0xFFADCBEF), secondaryContainer = Color(0xFF263C58),
        onSecondaryContainer = Color(0xFFD9E8FF),
        background = Color(0xFF0E1017), surface = Color(0xFF171A24),
        surfaceVariant = Color(0xFF2C3142), surfaceContainerHigh = Color(0xFF222735),
        surfaceContainerHighest = Color(0xFF2C3142),
        onSurface = Color(0xFFE7E9F3), onSurfaceVariant = Color(0xFFB9C0D5),
        outlineVariant = Color(0xFF353C50),
    ) else scheme.copy(
        primary = Color(0xFF4562D6), onPrimary = Color.White,
        primaryContainer = Color(0xFFE3E9FF), onPrimaryContainer = Color(0xFF25366D),
        secondary = Color(0xFF46668D), secondaryContainer = Color(0xFFE6EEFA),
        onSecondaryContainer = Color(0xFF2C456B),
        background = Color(0xFFF5F6FB), surface = Color.White,
        surfaceVariant = Color(0xFFEBEEF7), surfaceContainerHigh = Color(0xFFEDF0F9),
        surfaceContainerHighest = Color(0xFFE5E9F4),
        onSurface = Color(0xFF21283B), onSurfaceVariant = Color(0xFF68738B),
        outlineVariant = Color(0xFFDDE2F0),
    )
    CompositionLocalProvider(LocalAccent provides accent) { MaterialTheme(colorScheme = colors, content = content) }
}
