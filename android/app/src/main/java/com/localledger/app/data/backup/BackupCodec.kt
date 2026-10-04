package com.localledger.app.data.backup

import com.localledger.app.domain.*
import com.localledger.app.domain.Asset
import com.localledger.app.domain.Account
import com.localledger.app.domain.Category
import com.localledger.app.domain.LedgerSnapshot
import com.localledger.app.domain.Transaction
import com.localledger.app.domain.Memo
import com.localledger.app.domain.Budget
import com.localledger.app.domain.RecurringRule
import com.localledger.app.domain.Wish
import com.localledger.app.domain.validateSnapshot
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

object BackupCodec {
    fun encode(snapshot: LedgerSnapshot): String {
        validateSnapshot(snapshot)
        return JSONObject()
            .put("version", 5)
            .put("categories", JSONArray(snapshot.categories.map {
                JSONObject().put("id", it.id).put("name", it.name).put("type", it.type)
                    .put("icon", it.icon ?: JSONObject.NULL).put("sortOrder", it.sortOrder)
                    .put("isDeleted", it.isDeleted)
            }))
            .put("accounts", JSONArray(snapshot.accounts.map {
                JSONObject().put("id", it.id).put("name", it.name).put("isDeleted", it.isDeleted).put("initialBalanceMinor", it.initialBalanceMinor)
            }))
            .put("transactions", JSONArray(snapshot.transactions.map {
                JSONObject().put("id", it.id).put("amountMinor", it.amountMinor).put("type", it.type)
                    .put("categoryId", it.categoryId).put("accountId", it.accountId)
                    .put("note", it.note ?: JSONObject.NULL).put("occurredAt", it.occurredAt)
                    .put("createdAt", it.createdAt).put("updatedAt", it.updatedAt)
                    .put("isDeleted", it.isDeleted).put("source", it.source).put("importKey", it.importKey ?: JSONObject.NULL)
                    .put("transferAccountId", it.transferAccountId ?: JSONObject.NULL).put("merchant", it.merchant ?: JSONObject.NULL)
                    .put("location", it.location ?: JSONObject.NULL).put("isReimbursable", it.isReimbursable).put("reimbursementStatus", it.reimbursementStatus)
            }))
            .put("assets", JSONArray(snapshot.assets.map {
                JSONObject().put("id", it.id).put("name", it.name).put("kind", it.kind)
                    .put("purchaseMinor", it.purchaseMinor ?: JSONObject.NULL).put("valueMinor", it.valueMinor)
                    .put("pricedAt", it.pricedAt).put("referencePricesMinor", JSONArray(it.referencePricesMinor))
                    .put("note", it.note ?: JSONObject.NULL).put("createdAt", it.createdAt)
                    .put("updatedAt", it.updatedAt).put("isDeleted", it.isDeleted)
                    .put("quantity", it.quantity).put("acquisition", it.acquisition).put("purchaseDate", it.purchaseDate ?: JSONObject.NULL)
                    .put("channel", it.channel ?: JSONObject.NULL).put("location", it.location ?: JSONObject.NULL).put("warrantyUntil", it.warrantyUntil ?: JSONObject.NULL)
                    .put("status", it.status).put("disposedDate", it.disposedDate ?: JSONObject.NULL).put("photoPaths", JSONArray(it.photoPaths))
            }))
            .put("memos", JSONArray(snapshot.memos.map {
                JSONObject().put("id", it.id).put("title", it.title).put("content", it.content)
                    .put("isPinned", it.isPinned).put("isDone", it.isDone).put("createdAt", it.createdAt)
                    .put("updatedAt", it.updatedAt).put("isDeleted", it.isDeleted)
                    .put("kind", it.kind).put("color", it.color).put("folder", it.folder).put("dueEpochDay", it.dueEpochDay ?: JSONObject.NULL)
            }))
            .put("budgets", JSONArray(snapshot.budgets.map {
                JSONObject().put("month", it.month).put("amountMinor", it.amountMinor).put("updatedAt", it.updatedAt)
            }))
            .put("recurringRules", JSONArray(snapshot.recurringRules.map {
                JSONObject().put("id", it.id).put("name", it.name).put("amountMinor", it.amountMinor)
                    .put("type", it.type).put("categoryId", it.categoryId).put("accountId", it.accountId)
                    .put("nextDueAt", it.nextDueAt).put("dayOfMonth", it.dayOfMonth).put("note", it.note ?: JSONObject.NULL)
                    .put("createdAt", it.createdAt).put("updatedAt", it.updatedAt).put("isDeleted", it.isDeleted)
            }))
            .put("wishes", JSONArray(snapshot.wishes.map {
                JSONObject().put("id", it.id).put("name", it.name).put("targetMinor", it.targetMinor)
                    .put("savedMinor", it.savedMinor).put("note", it.note ?: JSONObject.NULL).put("isPurchased", it.isPurchased)
                    .put("createdAt", it.createdAt).put("updatedAt", it.updatedAt).put("isDeleted", it.isDeleted)
            }))
            .put("lifeItems", JSONArray(snapshot.lifeItems.map { item ->
                JSONObject().put("id", item.id).put("kind", item.kind.name).put("title", item.title).put("dateEpochDay", item.dateEpochDay)
                    .put("note", item.note ?: JSONObject.NULL).put("endDateEpochDay", item.endDateEpochDay ?: JSONObject.NULL)
                    .put("location", item.location ?: JSONObject.NULL).put("targetCount", item.targetCount ?: JSONObject.NULL)
                    .put("progressCount", item.progressCount).put("unit", item.unit ?: JSONObject.NULL).put("amountMinor", item.amountMinor ?: JSONObject.NULL)
                    .put("repeatYearly", item.repeatYearly).put("mood", item.mood ?: JSONObject.NULL)
                    .put("subscriptionCycleMonths", item.subscriptionCycleMonths ?: JSONObject.NULL).put("renewalDayOfMonth", item.renewalDayOfMonth ?: JSONObject.NULL)
                    .put("createdAt", item.createdAt).put("updatedAt", item.updatedAt).put("isDeleted", item.isDeleted)
            }))
            .put("lifeCheckIns", JSONArray(snapshot.lifeCheckIns.map {
                JSONObject().put("itemId", it.itemId).put("dateEpochDay", it.dateEpochDay).put("createdAt", it.createdAt).put("updatedAt", it.updatedAt).put("isDeleted", it.isDeleted)
            }))
            .put("focusSessions", JSONArray(snapshot.focusSessions.map {
                JSONObject().put("id", it.id).put("title", it.title).put("durationSeconds", it.durationSeconds).put("elapsedMillis", it.elapsedMillis)
                    .put("startedAt", it.startedAt ?: JSONObject.NULL).put("status", it.status.name).put("completedAt", it.completedAt ?: JSONObject.NULL)
                    .put("createdAt", it.createdAt).put("updatedAt", it.updatedAt).put("isDeleted", it.isDeleted)
            }))
            .put("studyCards", JSONArray(snapshot.studyCards.map {
                JSONObject().put("id", it.id).put("language", it.language).put("word", it.word).put("meaning", it.meaning).put("note", it.note ?: JSONObject.NULL)
                    .put("dueEpochDay", it.dueEpochDay).put("correctStreak", it.correctStreak).put("intervalDays", it.intervalDays)
                    .put("lastReviewEpochDay", it.lastReviewEpochDay ?: JSONObject.NULL).put("createdAt", it.createdAt).put("updatedAt", it.updatedAt).put("isDeleted", it.isDeleted)
            }))
            .put("paymentCandidates", JSONArray(snapshot.paymentCandidates.map {
                JSONObject().put("id", it.id).put("source", it.source).put("text", it.text).put("capturedAt", it.capturedAt).put("isDeleted", it.isDeleted)
            }))
            .put("assetPhotos", JSONObject(snapshot.assetPhotos)).toString()
    }

    fun decode(text: String): LedgerSnapshot {
        try {
            val input = JSONTokener(text)
            val json = input.nextValue()
            require(json is JSONObject && input.nextClean() == '\u0000') { "备份必须是一个完整的 JSON 对象。" }
            val version = json.integer("version")
            require(version in 1..5) { "不支持此备份版本。" }
            return LedgerSnapshot(
                categories = json.objects("categories").map {
                    Category(it.string("id"), it.string("name"), it.integer("type"),
                        it.nullableString("icon"), it.integer("sortOrder"), it.boolean("isDeleted"))
                },
                accounts = json.objects("accounts").map {
                    Account(it.string("id"), it.string("name"), it.boolean("isDeleted"), if (version < 5) 0 else it.wholeLong("initialBalanceMinor"))
                },
                transactions = json.objects("transactions").map {
                    Transaction(
                        id = it.string("id"), amountMinor = it.wholeLong("amountMinor"), type = it.integer("type"),
                        categoryId = it.string("categoryId"), accountId = it.string("accountId"),
                        note = it.nullableString("note"), occurredAt = it.wholeLong("occurredAt"),
                        createdAt = it.wholeLong("createdAt"), updatedAt = it.wholeLong("updatedAt"),
                        isDeleted = it.boolean("isDeleted"), source = it.integer("source"),
                        importKey = if (version < 3) null else it.nullableString("importKey"),
                        transferAccountId = if (version < 5) null else it.nullableString("transferAccountId"),
                        merchant = if (version < 5) null else it.nullableString("merchant"), location = if (version < 5) null else it.nullableString("location"),
                        isReimbursable = version >= 5 && it.boolean("isReimbursable"), reimbursementStatus = if (version < 5) 0 else it.integer("reimbursementStatus"),
                    )
                },
                assets = if (version == 1) emptyList() else json.objects("assets").map {
                    val references = it.get("referencePricesMinor")
                    require(references is JSONArray && references.length() <= 30) { "参考报价必须是数组。" }
                    val prices = (0 until references.length()).map { index ->
                        val price = references.get(index)
                        require(price is Int || price is Long) { "参考报价必须是整数分。" }
                        (price as Number).toLong()
                    }
                    Asset(it.string("id"), it.string("name"), it.string("kind"),
                        if (it.get("purchaseMinor") === JSONObject.NULL) null else it.wholeLong("purchaseMinor"),
                        it.wholeLong("valueMinor"), it.wholeLong("pricedAt"), prices, it.nullableString("note"),
                        it.wholeLong("createdAt"), it.wholeLong("updatedAt"), it.boolean("isDeleted"),
                        quantity = if (version < 5) 1 else it.integer("quantity"), acquisition = if (version < 5) "购买" else it.string("acquisition"),
                        purchaseDate = if (version < 5) null else it.nullableString("purchaseDate"), channel = if (version < 5) null else it.nullableString("channel"),
                        location = if (version < 5) null else it.nullableString("location"), warrantyUntil = if (version < 5) null else it.nullableString("warrantyUntil"),
                        status = if (version < 5) "持有中" else it.string("status"), disposedDate = if (version < 5) null else it.nullableString("disposedDate"),
                        photoPaths = if (version < 5) emptyList() else it.strings("photoPaths"))
                },
                memos = if (version < 3) emptyList() else json.objects("memos").map {
                    Memo(it.string("id"), it.string("title"), it.string("content"), it.boolean("isPinned"),
                        it.boolean("isDone"), it.wholeLong("createdAt"), it.wholeLong("updatedAt"), it.boolean("isDeleted"),
                        kind = if (version < 5) "note" else it.string("kind"), color = if (version < 5) "yellow" else it.string("color"),
                        folder = if (version < 5) "默认" else it.string("folder"), dueEpochDay = if (version < 5) null else it.nullableLong("dueEpochDay"))
                },
                budgets = if (version < 3) emptyList() else json.objects("budgets").map {
                    Budget(it.string("month"), it.wholeLong("amountMinor"), it.wholeLong("updatedAt"))
                },
                recurringRules = if (version < 3) emptyList() else json.objects("recurringRules").map {
                    RecurringRule(it.string("id"), it.string("name"), it.wholeLong("amountMinor"), it.integer("type"),
                        it.string("categoryId"), it.string("accountId"), it.wholeLong("nextDueAt"), it.integer("dayOfMonth"),
                        it.nullableString("note"), it.wholeLong("createdAt"), it.wholeLong("updatedAt"), it.boolean("isDeleted"))
                },
                wishes = if (version < 4) emptyList() else json.objects("wishes").map {
                    Wish(it.string("id"), it.string("name"), it.wholeLong("targetMinor"), it.wholeLong("savedMinor"),
                        it.nullableString("note"), it.boolean("isPurchased"), it.wholeLong("createdAt"), it.wholeLong("updatedAt"), it.boolean("isDeleted"))
                },
                lifeItems = if (version < 5) emptyList() else json.objects("lifeItems").map {
                    LifeItem(it.string("id"), LifeKind.valueOf(it.string("kind")), it.string("title"), it.wholeLong("dateEpochDay"),
                        it.nullableString("note"), it.nullableLong("endDateEpochDay"), it.nullableString("location"), it.nullableLong("targetCount"),
                        it.wholeLong("progressCount"), it.nullableString("unit"), it.nullableLong("amountMinor"), it.boolean("repeatYearly"),
                        it.nullableInt("mood"), it.nullableInt("subscriptionCycleMonths"), it.nullableInt("renewalDayOfMonth"),
                        it.wholeLong("createdAt"), it.wholeLong("updatedAt"), it.boolean("isDeleted"))
                },
                lifeCheckIns = if (version < 5) emptyList() else json.objects("lifeCheckIns").map {
                    LifeCheckIn(it.string("itemId"), it.wholeLong("dateEpochDay"), it.wholeLong("createdAt"), it.wholeLong("updatedAt"), it.boolean("isDeleted"))
                },
                focusSessions = if (version < 5) emptyList() else json.objects("focusSessions").map {
                    FocusSession(it.string("id"), it.string("title"), it.integer("durationSeconds"), it.wholeLong("elapsedMillis"),
                        it.nullableLong("startedAt"), FocusStatus.valueOf(it.string("status")), it.nullableLong("completedAt"),
                        it.wholeLong("createdAt"), it.wholeLong("updatedAt"), it.boolean("isDeleted"))
                },
                studyCards = if (version < 5) emptyList() else json.objects("studyCards").map {
                    StudyCard(it.string("id"), it.string("language"), it.string("word"), it.string("meaning"), it.nullableString("note"),
                        it.wholeLong("dueEpochDay"), it.integer("correctStreak"), it.integer("intervalDays"), it.nullableLong("lastReviewEpochDay"),
                        it.wholeLong("createdAt"), it.wholeLong("updatedAt"), it.boolean("isDeleted"))
                },
                paymentCandidates = if (version < 5) emptyList() else json.objects("paymentCandidates").map {
                    PaymentCandidate(it.string("id"), it.string("source"), it.string("text"), it.wholeLong("capturedAt"), it.boolean("isDeleted"))
                },
                assetPhotos = if (version < 5) emptyMap() else json.get("assetPhotos").let { photos ->
                    require(photos is JSONObject) { "照片附件必须是对象。" }
                    photos.keys().asSequence().associateWith { photos.string(it) }
                },
            ).also(::validateSnapshot)
        } catch (error: JSONException) {
            throw IllegalArgumentException("备份 JSON 格式不正确或缺少字段。", error)
        }
    }

    private fun JSONObject.nullableLong(name: String): Long? = if (get(name) === JSONObject.NULL) null else wholeLong(name)
    private fun JSONObject.nullableInt(name: String): Int? = if (get(name) === JSONObject.NULL) null else integer(name)
    private fun JSONObject.strings(name: String): List<String> {
        val array = get(name)
        require(array is JSONArray && array.length() <= 4) { "$name 必须是至多四项的字符串数组。" }
        return (0 until array.length()).map { index ->
            val item = array.get(index); require(item is String) { "$name 必须为字符串。" }; item
        }
    }

    private fun JSONObject.string(name: String): String {
        val value = get(name)
        require(value is String) { "$name 必须是字符串。" }
        return value
    }

    private fun JSONObject.nullableString(name: String): String? {
        val value = get(name)
        require(value === JSONObject.NULL || value is String) { "$name 必须是字符串或 null。" }
        return value as? String
    }

    private fun JSONObject.boolean(name: String): Boolean {
        val value = get(name)
        require(value is Boolean) { "$name 必须是布尔值。" }
        return value
    }

    private fun JSONObject.wholeLong(name: String): Long {
        val value = get(name)
        require(value is Int || value is Long) { "$name 必须是 64 位整数。" }
        return (value as Number).toLong()
    }

    private fun JSONObject.integer(name: String): Int {
        val value = wholeLong(name)
        require(value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "$name 超出整数范围。" }
        return value.toInt()
    }

    private fun JSONObject.objects(name: String): List<JSONObject> {
        val value = get(name)
        require(value is JSONArray) { "$name 必须是数组。" }
        return (0 until value.length()).map {
            val item = value.get(it)
            require(item is JSONObject) { "$name 的记录必须是对象。" }
            item
        }
    }
}
