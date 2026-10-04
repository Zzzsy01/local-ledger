package com.localledger.app.data.repository

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import com.localledger.app.data.db.LedgerDatabase
import com.localledger.app.data.importing.CSV_TEMPLATE
import com.localledger.app.data.importing.parseCsvBills
import com.localledger.app.domain.*
import java.security.MessageDigest
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImportRepository(private val db: LedgerDatabase, private val ledger: LedgerRepository, private val resolver: ContentResolver) {
    suspend fun template(uri: Uri) = withContext(Dispatchers.IO) {
        requireNotNull(resolver.openOutputStream(uri, "wt")) { "无法创建模板。" }.bufferedWriter(Charsets.UTF_8).use { it.write("\uFEFF$CSV_TEMPLATE") }
    }
    suspend fun preview(uri: Uri, encoding: String, expense: String, income: String, account: String): ImportPreview = withContext(Dispatchers.IO) {
        require(encoding in listOf("UTF-8", "GB18030")) { "不支持此编码。" }
        val bytes = requireNotNull(resolver.openInputStream(uri)) { "无法读取 CSV。" }.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                require(output.size() + n <= 8 * 1024 * 1024) { "CSV 超过 8 MiB。" }
                output.write(buffer, 0, n)
            }
            output.toByteArray()
        }
        val text = try { Charset.forName(encoding).newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString() }
        catch (e: java.nio.charset.CharacterCodingException) { throw IllegalArgumentException("文件编码不匹配，请切换 UTF-8 / GB18030 后重选文件。", e) }
        val bills = parseCsvBills(text)
        val digest = sha(bytes)
        db.withTransaction {
            val categories = db.ledgerDao().allCategories().filter { !it.isDeleted }
            val accounts = db.ledgerDao().allAccounts().filter { !it.isDeleted }
            val existing = db.ledgerDao().allTransactions()
            val keys = existing.mapNotNull { it.importKey }.toSet()
            val matches = existing.filter { !it.isDeleted }.map { listOf(it.amountMinor, it.type, it.occurredAt, it.note.orEmpty()) }.toSet()
            val seen = mutableSetOf<List<Any>>()
            ImportPreview(bills.map { bill ->
                val category = if (bill.category.isBlank()) categories.singleOrNull { it.id == if (bill.type == 0) expense else income }
                    else categories.singleOrNull { it.type == bill.type && it.name == bill.category }
                val payAccount = if (bill.account.isBlank()) accounts.singleOrNull { it.id == account }
                    else accounts.singleOrNull { it.name == bill.account }
                requireNotNull(category) { "第 ${bill.row} 行分类不存在、停用或重名，请修改分类列或先创建分类。" }
                requireNotNull(payAccount) { "第 ${bill.row} 行账户不存在或停用，请使用现金、微信、支付宝、银行卡。" }
                val key = if (bill.sourceId == null) "csv-file:$digest:${bill.row}" else "csv-id:" + sha("${payAccount.id}:${bill.sourceId}".toByteArray(Charsets.UTF_8))
                val same = listOf<Any>(bill.amountMinor, bill.type, bill.occurredAt, bill.note.orEmpty())
                ImportItem(bill, category.id, payAccount.id, key, key in keys, same in matches || !seen.add(same))
            }, if ("收/支" in text && ("交易时间" in text || "交易创建时间" in text)) "支付账单 · 仅成功收支；退款、关闭和中性交易已跳过" else "CSV 账单")
        }
    }
    suspend fun confirm(preview: ImportPreview, skipSimilar: Boolean): Int = db.withTransaction {
        val existing = db.ledgerDao().allTransactions()
        val keys = existing.mapNotNull { it.importKey }.toMutableSet()
        val matches = existing.filter { !it.isDeleted }.map { listOf<Any>(it.amountMinor, it.type, it.occurredAt, it.note.orEmpty()) }.toMutableSet()
        var count = 0
        preview.items.filter { !it.alreadyImported && (!skipSimilar || !it.similar) }.forEach { item ->
            val same = listOf<Any>(item.bill.amountMinor, item.bill.type, item.bill.occurredAt, item.bill.note.orEmpty())
            if ((!skipSimilar || same !in matches) && keys.add(item.key)) {
                ledger.saveTransaction(null, item.bill.amountMinor, item.bill.type, item.categoryId, item.accountId,
                    item.bill.note, item.bill.occurredAt, source = 1, importKey = item.key)
                count++
                matches.add(same)
            }
        }
        count
    }
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
