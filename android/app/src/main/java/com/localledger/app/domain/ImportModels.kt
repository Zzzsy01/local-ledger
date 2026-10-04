package com.localledger.app.domain

data class CsvBill(val row: Int, val occurredAt: Long, val type: Int, val amountMinor: Long,
    val category: String, val account: String, val note: String?, val sourceId: String?)
data class ImportItem(val bill: CsvBill, val categoryId: String, val accountId: String,
    val key: String, val alreadyImported: Boolean, val similar: Boolean)
data class ImportPreview(val items: List<ImportItem>, val filename: String)
