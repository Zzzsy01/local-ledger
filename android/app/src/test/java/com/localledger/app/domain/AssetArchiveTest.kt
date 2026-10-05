package com.localledger.app.domain

import com.localledger.app.data.backup.BackupCodec
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AssetArchiveTest {
    private val id = "00000000-0000-4000-8000-000000000001"
    private val asset = Asset(id, "旅行相机", "摄影", 500000, 200000, 0, createdAt = 0, updatedAt = 1,
        location = "书房 / 白色柜子 / 第二层", purchaseDate = "2026-01-01", tags = listOf("Sony", "随身"), serialNumber = "SN-123")
    private val record = AssetRecord("00000000-0000-4000-8000-000000000002", id, "位置变更", 1, "更新位置", "办公室", asset.location, createdAt = 1, updatedAt = 1)
    @Test fun searchUsesAllTermsAndHistoryWithoutConfusingCurrentLocation() {
        assertTrue(assetMatches(asset, "相机 第二层"))
        assertTrue(assetMatches(asset, "ＳＯＮＹ sn-123"))
        assertTrue(assetMatches(asset, "2026-01"))
        assertFalse(assetMatches(asset, "相机 办公室"))
        assertTrue(assetMatches(asset, "相机 办公室", listOf(record)))
        assertFalse(assetMatches(asset, "相机 厨房", listOf(record)))
        assertEquals("书房 / 白色柜子 / 第二层", asset.location)
        assertFalse(assetMatches(asset, "办公室", listOf(record.copy(assetId = "unrelated"))))
    }
    @Test fun archiveBackupIsStrictAndVersionFiveGetsExplicitDefaults() {
        val snapshot = LedgerSnapshot(emptyList(), listOf(Account(id, "现金")), emptyList(), assets = listOf(asset.copy(isFavorite = true)), assetRecords = listOf(record))
        assertEquals(snapshot, BackupCodec.decode(BackupCodec.encode(snapshot)))
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.encode(snapshot.copy(assets = emptyList())) }
        val damaged = JSONObject(BackupCodec.encode(snapshot)).apply { remove("assetRecords") }
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(damaged.toString()) }
        val older = JSONObject(BackupCodec.encode(snapshot)).apply {
            put("version", 5); remove("assetRecords")
            getJSONArray("assets").getJSONObject(0).apply { remove("tags"); remove("serialNumber"); remove("isFavorite"); remove("isValueKnown") }
        }
        val migrated = BackupCodec.decode(older.toString())
        assertEquals(asset.copy(tags = emptyList(), serialNumber = null), migrated.assets.single())
        assertTrue(migrated.assetRecords.isEmpty())
    }
    @Test fun unknownValueIsDifferentFromConfirmedZeroAndTagsAreBounded() {
        val unknown = asset.copy(valueMinor = 0, isValueKnown = false)
        validateAsset(unknown)
        assertThrows(IllegalArgumentException::class.java) { validateAsset(unknown.copy(valueMinor = 1)) }
        validateAsset(unknown.copy(isValueKnown = true))
        assertEquals(listOf("相机", "旅行", "Sony"), parseAssetTags("相机,旅行，Sony #相机"))
        assertThrows(IllegalArgumentException::class.java) { validateAsset(asset.copy(tags = List(13) { "标签$it" })) }
    }
}
