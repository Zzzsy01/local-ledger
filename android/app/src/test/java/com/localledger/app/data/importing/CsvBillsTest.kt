package com.localledger.app.data.importing

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class CsvBillsTest {
    @Test fun quotedRowsAmountsDatesAndInvalidFilesAreHandledWithoutMutation() {
        val text = "\uFEFF时间,收支,金额,分类,账户,备注,来源ID\r\n2026-12-31 23:59:59,支出,0.01,,,\"午餐,外卖\n含\"\"备注\"\"\",one\r\n2027-01-01,收入,20.50,,,,two\r\n"
        val rows = parseCsvBills(text, ZoneId.of("Asia/Shanghai"))
        assertEquals(2, rows.size)
        assertEquals(1L, rows[0].amountMinor)
        assertEquals("午餐,外卖\n含\"备注\"", rows[0].note)
        assertTrue(rows[1].occurredAt > rows[0].occurredAt)
        listOf(text.replace("0.01", "0.001"), text.replace("0.01", "-1"), text.replace("2027-01-01", "2027-02-30"),
            text.replace(",two", ",one"), "时间,收支,金额\n2026-01-01,支出,\"1", "时间,收支,金额\n2026-01-01,支出,\"1\"bad").forEach {
            assertThrows(Exception::class.java) { parseCsvBills(it) }
        }
    }
}
