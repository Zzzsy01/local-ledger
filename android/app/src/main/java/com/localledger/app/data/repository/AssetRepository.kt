package com.localledger.app.data.repository

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.room.withTransaction
import com.localledger.app.data.db.LedgerDatabase
import com.localledger.app.data.db.toDomain
import com.localledger.app.data.db.toEntity
import com.localledger.app.domain.Asset
import com.localledger.app.domain.isAssetPhotoPath
import com.localledger.app.domain.isHeld
import com.localledger.app.domain.validateAsset
import com.localledger.app.domain.*
import java.io.File
import java.util.UUID
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AssetRepository(private val db: LedgerDatabase, context: Context? = null) {
    private val appContext = context?.applicationContext
    private val dao = db.assetDao()
    val assets: Flow<List<Asset>> = dao.observeAssets().map { rows -> rows.map { it.toDomain() } }
    val records: Flow<List<AssetRecord>> = dao.observeRecords().map { rows -> rows.map { it.toDomain() } }
    suspend fun asset(id: String): Asset? = dao.asset(id)?.takeUnless { it.isDeleted }?.toDomain()

    suspend fun save(id: String?, name: String, kind: String, purchase: Long?, value: Long, pricedAt: Long, references: List<Long>, note: String?,
        quantity: Int = 1, acquisition: String = "购买", purchaseDate: String? = null, channel: String? = null,
        location: String? = null, warrantyUntil: String? = null, status: String = "持有中", disposedDate: String? = null,
        photoPaths: List<String> = emptyList(), tags: List<String> = emptyList(), serialNumber: String? = null,
        isFavorite: Boolean = false, isValueKnown: Boolean = true) = db.withTransaction {
        val existing = id?.let { requireNotNull(dao.asset(it)) { "物品已不存在。" } }
        require(existing?.isDeleted != true) { "物品已移除。" }
        val now = System.currentTimeMillis()
        val item = Asset(existing?.id ?: UUID.randomUUID().toString(), name.trim(), kind, purchase, value, pricedAt,
            references, note?.trim()?.takeIf { it.isNotEmpty() }, existing?.createdAt ?: now,
            maxOf(now, existing?.updatedAt ?: now), false, quantity, acquisition, purchaseDate,
            channel?.trim()?.takeIf { it.isNotEmpty() }, location?.trim()?.takeIf { it.isNotEmpty() },
            warrantyUntil, status, disposedDate, photoPaths, tags, serialNumber?.trim()?.takeIf { it.isNotEmpty() }, isFavorite, isValueKnown)
        persist(existing?.toDomain(), item)
    }

    private suspend fun persist(existing: Asset?, item: Asset) {
        validateAsset(item)
        val previousValue = existing?.let { if (it.isHeld) it.valueMinor else 0L } ?: 0L
        try { Math.addExact(dao.total() - previousValue, if (item.isHeld) item.valueMinor else 0L) }
        catch (error: ArithmeticException) { throw IllegalArgumentException("物品估值总额超出范围。", error) }
        dao.save(item.toEntity())
        val now = item.updatedAt
        suspend fun record(kind: String, content: String, from: String? = null, to: String? = null,
            amount: Long? = null, previous: Long? = null, occurred: Long = now) {
            val record = AssetRecord(UUID.randomUUID().toString(), item.id, kind, occurred, content, from, to, amount, previous, now, now)
            validateAssetRecord(record); dao.saveRecord(record.toEntity())
        }
        if (existing == null) record("建立档案", "加入我的博物馆" + if (item.isValueKnown) " · 初始估值" else " · 估值待补充", amount = item.valueMinor.takeIf { item.isValueKnown })
        else {
            if (existing.location != item.location) record("位置变更", "更新存放位置／保管人", existing.location, item.location)
            if (existing.isValueKnown != item.isValueKnown || (item.isValueKnown && (existing.valueMinor != item.valueMinor || existing.pricedAt != item.pricedAt || existing.referencePricesMinor != item.referencePricesMinor)))
                record("估值更新", if (!item.isValueKnown) "清除估值，待重新核对" else if (item.referencePricesMinor.isEmpty()) "手动估值" else "参考报价均值", amount = item.valueMinor.takeIf { item.isValueKnown }, previous = existing.valueMinor.takeIf { existing.isValueKnown }, occurred = item.pricedAt)
            if (existing.purchaseMinor != item.purchaseMinor || existing.purchaseDate != item.purchaseDate)
                record("购入信息", "购入日期：${existing.purchaseDate ?: "未填写"} → ${item.purchaseDate ?: "未填写"}", amount = item.purchaseMinor, previous = existing.purchaseMinor)
            if (existing.status != item.status || existing.disposedDate != item.disposedDate) record("状态变更", "${existing.status} → ${item.status}" + item.disposedDate?.let { " · $it" }.orEmpty())
            if (existing.copy(updatedAt = item.updatedAt, location = item.location, valueMinor = item.valueMinor, pricedAt = item.pricedAt,
                    referencePricesMinor = item.referencePricesMinor, purchaseMinor = item.purchaseMinor, purchaseDate = item.purchaseDate,
                    status = item.status, disposedDate = item.disposedDate, isFavorite = item.isFavorite, isValueKnown = item.isValueKnown) != item)
                record("档案更新", "更新「${existing.name}」的档案 · 当前名称「${item.name}」")
        }
    }

    suspend fun move(id: String, location: String) = db.withTransaction {
        require(location.isNotBlank()) { "请填写新的位置或保管人。" }
        val existing = requireNotNull(asset(id)) { "物品已不存在。" }
        persist(existing, existing.copy(location = location.trim(), updatedAt = maxOf(System.currentTimeMillis(), existing.updatedAt)))
    }
    suspend fun revalue(id: String, amount: Long, day: LocalDate) = db.withTransaction {
        require(day <= LocalDate.now()) { "估值日期不能晚于今天。" }
        val existing = requireNotNull(asset(id)) { "物品已不存在。" }
        persist(existing, existing.copy(valueMinor = amount, isValueKnown = true, referencePricesMinor = emptyList(),
            pricedAt = day.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), updatedAt = maxOf(System.currentTimeMillis(), existing.updatedAt)))
    }
    suspend fun favorite(id: String, favorite: Boolean) = db.withTransaction {
        val item = requireNotNull(asset(id)) { "物品已不存在。" }
        dao.save(item.copy(isFavorite = favorite, updatedAt = maxOf(System.currentTimeMillis(), item.updatedAt)).toEntity())
    }
    suspend fun saveRecord(assetId: String, id: String?, kind: String, content: String, day: LocalDate) = db.withTransaction {
        requireNotNull(asset(assetId)) { "物品已不存在。" }
        require(kind in manualAssetRecordKinds && content.isNotBlank() && content.length <= 4000) { "请选择记录类型并填写 1–4000 字内容。" }
        require(day <= LocalDate.now()) { "记录日期不能晚于今天。" }
        val existing = id?.let { requireNotNull(dao.record(it)) { "记录已不存在。" } }
        require(existing == null || (existing.assetId == assetId && existing.kind in manualAssetRecordKinds)) { "自动记录不能修改。" }
        val now = System.currentTimeMillis()
        val record = AssetRecord(existing?.id ?: UUID.randomUUID().toString(), assetId, kind,
            day.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), content.trim(),
            createdAt = existing?.createdAt ?: now, updatedAt = maxOf(now, existing?.updatedAt ?: now))
        validateAssetRecord(record); dao.saveRecord(record.toEntity())
    }

    fun photoFile(relativePath: String): File? = if (isAssetPhotoPath(relativePath)) appContext?.let { File(it.filesDir, relativePath) } else null

    // Copy while the picker grant is available; database and backups never retain external content URIs.
    suspend fun importPhoto(uri: Uri): String = withContext(Dispatchers.IO) {
        val context = requireNotNull(appContext) { "物品图片存储尚未初始化。" }
        val relativePath = "asset-photos/${UUID.randomUUID()}.img"
        val file = File(context.filesDir, relativePath)
        check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs()) { "无法创建物品图片目录。" }
        var copied = false
        try {
            val input = requireNotNull(context.contentResolver.openInputStream(uri)) { "无法读取所选照片。" }
            input.use { source -> file.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val size = source.read(buffer)
                    if (size < 0) break
                    total += size
                    require(total <= 20L * 1024 * 1024) { "单张照片不能超过 20 MB。" }
                    output.write(buffer, 0, size)
                }
            } }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "请选择可读取的图片文件。" }
            copied = true
            relativePath
        } finally { if (!copied) file.delete() }
    }

    suspend fun delete(id: String) = db.withTransaction {
        val item = dao.asset(id) ?: return@withTransaction
        dao.save(item.copy(isDeleted = true, updatedAt = maxOf(System.currentTimeMillis(), item.updatedAt)))
    }
}
