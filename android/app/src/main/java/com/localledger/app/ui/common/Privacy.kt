package com.localledger.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.localledger.app.domain.formatAmount

val LocalHideAmounts = compositionLocalOf { false }

@Composable
fun visibleAmount(minor: Long): String = if (LocalHideAmounts.current) "••••" else formatAmount(minor)
