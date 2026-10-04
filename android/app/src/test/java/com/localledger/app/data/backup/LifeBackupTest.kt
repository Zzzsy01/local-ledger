package com.localledger.app.data.backup

import com.localledger.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.time.LocalDate

class LifeBackupTest {
    private val id = "00000000-0000-4000-8000-000000000001"
    @Test fun newFeaturesAndAttachmentsRoundTripAndRejectMissingData() {
        val day = LocalDate.of(2026,10,4).toEpochDay()
        val path = "asset-photos/$id.img"
        val snapshot = LedgerSnapshot(emptyList(),listOf(Account(id,"现金",initialBalanceMinor = -100)),emptyList(),
            assets = listOf(Asset(id,"书","图书",1000,200,0,createdAt = 0,updatedAt = 1,quantity = 2,photoPaths = listOf(path))),
            memos = listOf(Memo(id,"待办","内容",createdAt = 0,updatedAt = 1,kind = "todo",color = "blue",folder = "学习",dueEpochDay = day)),
            lifeItems = listOf(LifeItem(id,LifeKind.HABIT,"阅读",day,createdAt = 0,updatedAt = 1)),
            lifeCheckIns = listOf(LifeCheckIn(id,day,0,1)),
            focusSessions = listOf(FocusSession(id,"专注",60,createdAt = 0,updatedAt = 1)),
            studyCards = listOf(StudyCard(id,"ja","本","书",dueEpochDay = day,createdAt = 0,updatedAt = 1)),
            assetPhotos = mapOf(path to "AQID"),paymentCandidates = listOf(PaymentCandidate(id,"微信","支付成功 ¥12.00",1)))
        assertEquals(snapshot,BackupCodec.decode(BackupCodec.encode(snapshot)))
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.encode(snapshot.copy(assetPhotos = emptyMap())) }
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.encode(snapshot.copy(lifeCheckIns = snapshot.lifeCheckIns + snapshot.lifeCheckIns)) }
        val damaged = JSONObject(BackupCodec.encode(snapshot)).apply { remove("focusSessions") }
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(damaged.toString()) }
    }
}
