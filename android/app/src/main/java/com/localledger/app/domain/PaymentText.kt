package com.localledger.app.domain

data class PaymentHint(val amountMinor: Long?, val type: Int?, val merchant: String?)
data class PaymentCandidate(val id: String, val source: String, val text: String, val capturedAt: Long, val isDeleted: Boolean = false)

fun paymentHint(raw: String): PaymentHint {
    require(raw.length <= 32_000) { "识别文本过长。" }
    val text = raw.replace('￥', '¥')
    val rejected = listOf("退款", "撤销", "失败", "红包", "优惠券", "待支付", "余额不足").any { it in text }
    val type = when {
        rejected -> null
        listOf("到账", "收款成功", "收到转账", "收款金额").any { it in text } -> INCOME
        listOf("支付成功", "付款成功", "实付", "实际支付", "成功付款").any { it in text } -> EXPENSE
        else -> null
    }
    val labelled = Regex("(?:实付|实际支付|付款金额|收款金额|支付金额|成功付款|到账金额)[\\s：:]*[¥]?[\\s]*([0-9]+(?:\\.[0-9]{1,2})?)(?![0-9.])").findAll(text)
        .mapNotNull { parseAmount(it.groupValues[1]) }.toList()
    val currency = Regex("¥\\s*([0-9]+(?:\\.[0-9]{1,2})?)(?![0-9.])").findAll(text).mapNotNull { parseAmount(it.groupValues[1]) }.toList()
    val amounts = labelled.ifEmpty { currency }
    val amount = if (type != null && amounts.distinct().size == 1) amounts.first() else null
    val merchant = Regex("(?:商户(?:名称)?|收款方|付款方|交易对方)[：: ]+([^\\n]{1,80})").find(text)?.groupValues?.get(1)?.trim()
    return PaymentHint(amount, type, merchant)
}
