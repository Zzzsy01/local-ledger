package com.localledger.app.ui.common

import kotlinx.coroutines.CancellationException

internal fun Throwable.displayMessage(): String {
    if (this is CancellationException) throw this
    return message?.takeIf { it.isNotBlank() } ?: "操作失败，请稍后重试"
}
