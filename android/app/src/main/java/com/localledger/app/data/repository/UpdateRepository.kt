package com.localledger.app.data.repository

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.localledger.app.data.update.UpdateCheckJob
import com.localledger.app.data.update.decodeUpdate
import com.localledger.app.data.update.requireHttps
import com.localledger.app.domain.AppUpdate
import java.io.File
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class UpdateRepository(private val context: Context) {
    private val preferences = context.getSharedPreferences("app-updates", Context.MODE_PRIVATE)
    val source: String get() = preferences.getString("source", DEFAULT_SOURCE).orEmpty()
    val automatic: Boolean get() = preferences.getBoolean("automatic", false)
    @Suppress("DEPRECATION")
    private fun installed() = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
    val versionName: String get() = installed().versionName.orEmpty()
    val versionCode: Long get() = if (Build.VERSION.SDK_INT >= 28) installed().longVersionCode else installed().versionCode.toLong()
    fun shouldCheckOnOpen(): Boolean = automatic && source.isNotBlank() && System.currentTimeMillis() - preferences.getLong("lastCheck", 0) >= DAY

    fun initializeAutomaticUpdates(): Boolean {
        if (preferences.getBoolean("automatic-default-initialized", false)) return false
        configure(source.ifBlank { DEFAULT_SOURCE }, true)
        preferences.edit().putBoolean("automatic-default-initialized", true).apply()
        return true
    }

    fun configure(value: String, auto: Boolean) {
        val url = value.trim()
        if (url.isNotEmpty()) requireHttps(url)
        require(url.isNotEmpty() || !auto) { "请先设置开发者提供的更新地址。" }
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (auto) {
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, UpdateCheckJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(DAY).setPersisted(true).build()
            check(scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS) { "无法安排自动更新检查。" }
        } else scheduler.cancel(JOB_ID)
        preferences.edit().putString("source", url).putBoolean("automatic", auto).remove("lastCheck").apply()
    }

    private fun connect(url: String): HttpsURLConnection {
        requireHttps(url)
        return (URL(url).openConnection() as HttpsURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 30_000
        }
    }
    suspend fun check(): AppUpdate? = withContext(Dispatchers.IO) {
        require(source.isNotBlank()) { "尚未接入发布源，请填写开发者提供的更新地址。" }
        val connection = connect(source)
        val text = try {
            require(connection.responseCode == 200) { "更新服务器返回 ${connection.responseCode}。" }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val buffer = CharArray(8192)
                val content = StringBuilder()
                while (true) {
                    ensureActive()
                    val count = reader.read(buffer)
                    if (count < 0) break
                    require(content.length + count <= 65536) { "更新信息文件过大。" }
                    content.append(buffer, 0, count)
                }
                content.toString()
            }
        } finally { connection.disconnect() }
        val update = decodeUpdate(text)
        preferences.edit().putLong("lastCheck", System.currentTimeMillis()).apply()
        if (update.versionCode <= versionCode) return@withContext null
        require(update.minSdk <= Build.VERSION.SDK_INT) { "新版本需要更高版本的 Android，当前版本仍可使用。" }
        update
    }

    @Suppress("DEPRECATION")
    private fun verifyArchive(file: File, update: AppUpdate) {
        val info = requireNotNull(context.packageManager.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNATURES)) { "下载的文件不是有效安装包。" }
        val downloadedVersion = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        require(info.packageName == context.packageName && downloadedVersion == update.versionCode) { "安装包应用或版本不匹配。" }
        val expected = installed().signatures?.map { it.toCharsString() }?.toSet().orEmpty()
        val actual = info.signatures?.map { it.toCharsString() }?.toSet().orEmpty()
        require(expected.isNotEmpty() && expected == actual) { "安装包签名不同，无法保留原数据覆盖安装。" }
    }

    suspend fun downloaded(update: AppUpdate): File? = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "updates/update.apk")
        if (!file.isFile || file.length() > 128L * 1024 * 1024) return@withContext null
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        if (digest.digest().joinToString("") { "%02x".format(it) } != update.sha256) return@withContext null
        verifyArchive(file, update)
        file
    }

    suspend fun download(update: AppUpdate): File = withContext(Dispatchers.IO) {
        require(update.versionCode > versionCode) { "只允许安装更新的版本。" }
        val folder = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File(folder, "update.apk")
        val partial = File(folder, "update.part")
        val connection = connect(update.apkUrl)
        try {
            require(connection.responseCode == 200) { "安装包下载失败：${connection.responseCode}。" }
            val digest = MessageDigest.getInstance("SHA-256")
            var bytes = 0L
            connection.inputStream.use { input -> partial.outputStream().use { output ->
                val buffer = ByteArray(8192)
                while (true) {
                    ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    bytes += count
                    require(bytes <= 128L * 1024 * 1024) { "安装包超过 128 MiB。" }
                    digest.update(buffer, 0, count); output.write(buffer, 0, count)
                }
            } }
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            require(sha == update.sha256) { "安装包校验失败，请联系发布者。" }
            verifyArchive(partial, update)
            check(!file.exists() || file.delete()) { "无法替换旧安装包。" }
            check(partial.renameTo(file)) { "无法保存安装包。" }
            file
        } finally { connection.disconnect(); partial.delete() }
    }

    companion object {
        const val DEFAULT_SOURCE = "https://github.com/Zzzsy01/local-ledger/releases/latest/download/update.json"
        private const val JOB_ID = 1101
        private const val DAY = 24 * 60 * 60 * 1000L
    }
}
