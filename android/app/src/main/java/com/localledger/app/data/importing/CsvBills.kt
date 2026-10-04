package com.localledger.app.data.importing

import com.localledger.app.domain.CsvBill
import com.localledger.app.domain.parseAmount
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

const val CSV_TEMPLATE = "时间,收支,金额,分类,账户,备注,来源ID\r\n2026-10-01 12:00:00,支出,25.50,餐饮,微信,午餐,\r\n"

// Bounded CSV with quoted commas/newlines and doubled quotes, not a split-on-comma parser.
fun parseCsvBills(text: String, zone: ZoneId = ZoneId.systemDefault()): List<CsvBill> {
    require(text.length <= 8 * 1024 * 1024) { "CSV 超过 8 Mi 字符。" }
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var quoted = false
    var closed = false
    var index = 0
    val input = text.removePrefix("\uFEFF")
    fun endField() { row.add(field.toString().trim()); field.setLength(0); closed = false }
    fun endRow() { endField(); if (row.any { it.isNotBlank() }) rows.add(row); row = mutableListOf(); require(rows.size <= 10_001) { "一次最多导入 10000 笔。" } }
    while (index < input.length) {
        val c = input[index++]
        if (quoted) {
            if (c == '"') {
                if (index < input.length && input[index] == '"') { field.append('"'); index++ }
                else { quoted = false; closed = true }
            } else field.append(c)
        } else when (c) {
            '"' -> { require(field.isEmpty() && !closed) { "CSV 引号位置无效。" }; quoted = true }
            ',' -> endField()
            '\r', '\n' -> { if (c == '\r' && index < input.length && input[index] == '\n') index++; endRow() }
            else -> { require(!closed || c.isWhitespace()) { "CSV 引号后有多余内容。" }; if (!closed) field.append(c) }
        }
    }
    require(!quoted) { "CSV 引号未闭合。" }
    if (field.isNotEmpty() || row.isNotEmpty() || closed) endRow()
    require(rows.size >= 2) { "CSV 没有账目，请使用模板表头。" }
    val headers = rows.first()
    require(headers.toSet().size == headers.size) { "CSV 存在重复表头。" }
    listOf("时间", "收支", "金额").forEach { require(it in headers) { "缺少「$it」列，请先转换为导入模板。" } }
    val dateTime = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT)
    val date = DateTimeFormatter.ISO_LOCAL_DATE.withResolverStyle(ResolverStyle.STRICT)
    return rows.drop(1).mapIndexed { i, columns ->
        val line = i + 2
        require(columns.size == headers.size) { "第 $line 行列数与表头不同。" }
        fun value(name: String) = headers.indexOf(name).takeIf { it >= 0 }?.let(columns::get).orEmpty()
        val type = when(value("收支")) { "支出" -> 0; "收入" -> 1; else -> error("第 $line 行收支必须为收入或支出。") }
        val amount = requireNotNull(parseAmount(value("金额"))) { "第 $line 行金额须为大于零、最多两位小数的元。" }
        val rawDate = value("时间")
        val occurred = try {
            if (rawDate.length == 10) LocalDate.parse(rawDate, date).atStartOfDay(zone).toInstant().toEpochMilli()
            else LocalDateTime.parse(rawDate, dateTime).atZone(zone).toInstant().toEpochMilli()
        } catch (e: java.time.DateTimeException) { throw IllegalArgumentException("第 $line 行日期格式应为 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss。", e) }
        require(occurred >= 0) { "第 $line 行日期不能早于 1970 年。" }
        CsvBill(line, occurred, type, amount, value("分类"), value("账户"), value("备注").takeIf { it.isNotBlank() }, value("来源ID").takeIf { it.isNotBlank() })
    }.also { bills ->
        val ids = bills.mapNotNull { it.sourceId }
        require(ids.toSet().size == ids.size) { "CSV 内来源 ID 重复，请先核对账单。" }
    }
}
