package com.localledger.app.data.importing

import org.junit.Assert.*
import org.junit.Test

class PaymentCsvTest {
    @Test fun wechatPreambleAndQuotedCommasKeepOnlySuccessfulPayments() {
        val text = "微信支付账单明细\n导出时间,2026-10-04\n交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注\n2026-10-03 12:00:00,商户消费,书店,\"书,笔\",支出,¥25.50,零钱,支付成功,wx001,m1,/\n2026-10-03 13:00:00,商户消费,书店,退款,收入,¥25.50,零钱,退款成功,wx002,m2,/"
        val rows = parseCsvBills(text)
        assertEquals(1,rows.size);assertEquals(2550L,rows.single().amountMinor)
        assertEquals("微信",rows.single().account);assertEquals("wechat:wx001",rows.single().sourceId)
        assertTrue(rows.single().note!!.contains("书,笔"));assertEquals("",rows.single().category)
    }
    @Test fun alipayTransactionIdsAreStableAndDuplicateIdsAreRejected() {
        val text = "交易号,商家订单号,交易创建时间,付款时间,最近修改时间,交易来源地,类型,交易对方,商品名称,金额（元）,收/支,交易状态,服务费（元）,成功退款（元）,备注\nali001,m1,2026-10-03 12:00:00,2026-10-03 12:00:00,2026-10-03 12:00:00,手机,消费,商户,午餐,18.00,支出,交易成功,0,0,/"
        val row = parseCsvBills(text).single()
        assertEquals("支付宝",row.account);assertEquals("alipay:ali001",row.sourceId)
        assertThrows(IllegalArgumentException::class.java) { parseCsvBills(text + "\n" + text.lines().last()) }
    }
}
