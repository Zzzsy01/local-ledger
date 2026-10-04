package com.localledger.app.data.update

import com.localledger.app.domain.AppUpdate
import java.net.URI
import org.json.JSONObject
import org.json.JSONTokener

fun requireHttps(value: String) {
    val uri = URI(value)
    require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null) { "更新地址必须是有效的 HTTPS 地址。" }
}

fun decodeUpdate(text: String): AppUpdate {
    val tokener = JSONTokener(text)
    val json = tokener.nextValue()
    require(json is JSONObject && tokener.nextClean() == '\u0000') { "更新信息格式不正确。" }
    fun number(key: String): Long {
        val value = json.get(key)
        require(value is Int || value is Long) { "更新字段 $key 必须为整数。" }
        return (value as Number).toLong()
    }
    fun string(key: String): String = (json.get(key) as? String) ?: error("更新字段 $key 必须为字符串。")
    val version = number("versionCode")
    val minSdk = number("minSdk")
    val sha = string("sha256").lowercase()
    val url = string("apkUrl")
    requireHttps(url)
    require(version > 0 && minSdk in 1..Int.MAX_VALUE && Regex("[0-9a-f]{64}").matches(sha)) { "更新版本、系统要求或校验值无效。" }
    val name = string("versionName")
    require(name.isNotBlank()) { "缺少更新版本名称。" }
    return AppUpdate(version, name, url, sha, string("notes"), minSdk.toInt())
}
