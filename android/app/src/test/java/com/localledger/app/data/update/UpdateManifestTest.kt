package com.localledger.app.data.update

import org.junit.Assert.*
import org.junit.Test

class UpdateManifestTest {
    private val valid = """{"versionCode":3,"versionName":"1.2.0","apkUrl":"https://example.com/app.apk","sha256":"${"a".repeat(64)}","notes":"Changes","minSdk":26}"""
    @Test fun acceptsHttpsManifestAndRejectsUnsafeOrAmbiguousMetadata() {
        assertEquals(3L, decodeUpdate(valid).versionCode)
        listOf(
            valid.replace("https:", "http:"), valid.replace("\"versionCode\":3", "\"versionCode\":3.5"),
            valid.replace("\"versionCode\":3", "\"versionCode\":\"3\""), valid.replace("a".repeat(64), "invalid"),
            valid + " junk", valid.replace("https://example.com", "https://user:pass@example.com"),
        ).forEach { assertThrows(Exception::class.java) { decodeUpdate(it) } }
    }
}
