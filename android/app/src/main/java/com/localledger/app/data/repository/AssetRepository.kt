package com.localledger.app.data.repository

import androidx.room.withTransaction
import com.localledger.app.data.db.LedgerDatabase
import com.localledger.app.data.db.toDomain
import com.localledger.app.data.db.toEntity
import com.localledger.app.domain.Asset
import com.localledger.app.domain.validateAsset
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AssetRepository(private val db: LedgerDatabase) {
    private val dao = db.assetDao()
    val assets: Flow<List<Asset>> = dao.observeAssets().map { rows -> rows.map { it.toDomain() } }
    suspend fun asset(id: String): Asset? = dao.asset(id)?.takeUnless { it.isDeleted }?.toDomain()

    suspend fun save(id: String?, name: String, kind: String, purchase: Long?, value: Long, pricedAt: Long, references: List<Long>, note: String?) = db.withTransaction {
        val existing = id?.let { requireNotNull(dao.asset(it)) { "物品已不存在。" } }
        require(existing?.isDeleted != true) { "物品已移除。" }
        val now = System.currentTimeMillis()
        val item = Asset(existing?.id ?: UUID.randomUUID().toString(), name.trim(), kind, purchase, value, pricedAt,
            references, note?.trim()?.takeIf { it.isNotEmpty() }, existing?.createdAt ?: now,
            maxOf(now, existing?.updatedAt ?: now))
        validateAsset(item)
        try { Math.addExact(dao.total() - (existing?.valueMinor ?: 0), value) }
        catch (error: ArithmeticException) { throw IllegalArgumentException("物品估值总额超出范围。", error) }
        dao.save(item.toEntity())
    }

    suspend fun delete(id: String) = db.withTransaction {
        val item = dao.asset(id) ?: return@withTransaction
        dao.save(item.copy(isDeleted = true, updatedAt = maxOf(System.currentTimeMillis(), item.updatedAt)))
    }
}
