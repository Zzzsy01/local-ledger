package com.localledger.app.data.importing

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.localledger.app.LedgerApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import android.util.Log

class PaymentNotifications : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onNotificationPosted(notification: StatusBarNotification) {
        val app = application as LedgerApplication
        if (!app.settingsRepository.settings.value.capturePayments || notification.packageName !in listOf("com.tencent.mm", "com.eg.android.AlipayGphone")) return
        val extras = notification.notification.extras
        val text = listOf(extras.getCharSequence(Notification.EXTRA_TITLE), extras.getCharSequence(Notification.EXTRA_TEXT), extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
            .mapNotNull { it?.toString() }.distinct().joinToString("\n")
        scope.launch {
            try { app.captureRepository.capture(if (notification.packageName == "com.tencent.mm") "微信" else "支付宝", notification.key, text, notification.postTime) }
            catch (error: Exception) { Log.w("PaymentCapture", "Unable to save candidate", error) }
        }
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
