package com.localledger.app.data.backup

import android.content.ContentResolver
import android.net.Uri
import com.localledger.app.domain.LedgerSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackupFiles(private val resolver: ContentResolver) {
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
