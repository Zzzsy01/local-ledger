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
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AssetRepository(private val db: LedgerDatabase, context: Context? = null) {
    private val appContext = context?.applicationContext
    private val dao = db.assetDao()
    val assets: Flow<List<Asset>> = dao.observeAssets().map { rows -> rows.map { it.toDomain() } }
    suspend fun asset(id: String): Asset? = dao.asset(id)?.takeUnless { it.isDeleted }?.toDomain()

    suspend fun save(id: String?, name: String, kind: String, purchase: Long?, value: Long, pricedAt: Long, references: List<Long>, note: String?,
        quantity: Int = 1, acquisition: String = "购买", purchaseDate: String? = null, channel: String? = null,
        location: String? = null, warrantyUntil: String? = null, status: String = "持有中", disposedDate: String? = null,
        photoPaths: List<String> = emptyList()) = db.withTransaction {
        val existing = id?.let { requireNotNull(dao.asset(it)) { "物品已不存在。" } }
        require(existing?.isDeleted != true) { "物品已移除。" }
        val now = System.currentTimeMillis()
        val item = Asset(existing?.id ?: UUID.randomUUID().toString(), name.trim(), kind, purchase, value, pricedAt,
            references, note?.trim()?.takeIf { it.isNotEmpty() }, existing?.createdAt ?: now,
            maxOf(now, existing?.updatedAt ?: now), false, quantity, acquisition, purchaseDate,
            channel?.trim()?.takeIf { it.isNotEmpty() }, location?.trim()?.takeIf { it.isNotEmpty() },
            warrantyUntil, status, disposedDate, photoPaths)
        validateAsset(item)
        val previousValue = existing?.toDomain()?.let { if (it.isHeld) it.valueMinor else 0L } ?: 0L
        try { Math.addExact(dao.total() - previousValue, if (item.isHeld) value else 0L) }
        catch (error: ArithmeticException) { throw IllegalArgumentException("物品估值总额超出范围。", error) }
        dao.save(item.toEntity())
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
