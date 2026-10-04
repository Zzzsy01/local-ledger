package com.localledger.app.data.backup

import android.content.ContentResolver
import android.net.Uri
import com.localledger.app.domain.LedgerSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Base64
import java.util.UUID
import android.graphics.BitmapFactory

class BackupFiles(private val resolver: ContentResolver, private val filesDir: File? = null) {
    suspend fun withPhotos(snapshot: LedgerSnapshot): LedgerSnapshot = withContext(Dispatchers.IO) {
        var total = 0L
        val photos = snapshot.assets.flatMap { it.photoPaths }.distinct().associateWith { path ->
            require(com.localledger.app.domain.isAssetPhotoPath(path)) { "物品照片路径无效。" }
            val file = File(requireNotNull(filesDir) { "照片存储尚未初始化。" }, path)
            require(file.isFile && file.length() in 1..20L * 1024 * 1024) { "物品照片缺失或过大，无法生成完整备份。" }
            total += file.length()
            require(total <= 48L * 1024 * 1024) { "完整备份的照片总量不能超过 48 MB。" }
            Base64.getEncoder().encodeToString(file.readBytes())
        }
        snapshot.copy(assetPhotos = photos)
    }

    suspend fun restorePhotos(snapshot: LedgerSnapshot, restore: suspend (LedgerSnapshot) -> Unit) = withContext(Dispatchers.IO) {
        val created = mutableListOf<File>()
        try {
            val mapping = snapshot.assetPhotos.mapValues { (_, encoded) ->
                val bytes = Base64.getDecoder().decode(encoded)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "备份照片无法读取。" }
                val path = "asset-photos/${UUID.randomUUID()}.img"
                val file = File(requireNotNull(filesDir), path)
                check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs()) { "无法创建照片目录。" }
                created.add(file)
                file.writeBytes(bytes)
                path
            }
            restore(snapshot.copy(assets = snapshot.assets.map { asset -> asset.copy(photoPaths = asset.photoPaths.map { mapping.getValue(it) }) },
                assetPhotos = snapshot.assetPhotos.mapKeys { mapping.getValue(it.key) }))
            created.clear()
        } finally { created.forEach { it.delete() } }
    }
    suspend fun write(uri: Uri, snapshot: LedgerSnapshot) = withContext(Dispatchers.IO) {
        val json = BackupCodec.encode(snapshot)
        require(json.length <= MAX_CHARACTERS) { "备份文件超过 64 Mi 个字符，无法导出。" }
        val output = requireNotNull(resolver.openOutputStream(uri, "wt")) { "无法打开备份文件。" }
        output.bufferedWriter(Charsets.UTF_8).use { it.write(json) }
    }

    suspend fun read(uri: Uri): LedgerSnapshot = withContext(Dispatchers.IO) {
        val input = requireNotNull(resolver.openInputStream(uri)) { "无法读取备份文件。" }
        // Bound untrusted input before allocating or parsing a complete JSON document.
        val text = input.bufferedReader(Charsets.UTF_8).use { reader ->
            val result = StringBuilder()
            val buffer = CharArray(8192)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                require(result.length.toLong() + count <= MAX_CHARACTERS) { "备份文件超过 64 Mi 个字符，无法读取。" }
                result.append(buffer, 0, count)
            }
            result.toString()
        }
        BackupCodec.decode(text)
    }

    private companion object { const val MAX_CHARACTERS = 64 * 1024 * 1024 }
}
