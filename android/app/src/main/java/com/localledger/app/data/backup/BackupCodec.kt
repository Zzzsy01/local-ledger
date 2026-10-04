package com.localledger.app.data.backup

import com.localledger.app.domain.Asset
import com.localledger.app.domain.Account
import com.localledger.app.domain.Category
import com.localledger.app.domain.LedgerSnapshot
import com.localledger.app.domain.Transaction
import com.localledger.app.domain.Memo
import com.localledger.app.domain.Budget
import com.localledger.app.domain.RecurringRule
import com.localledger.app.domain.validateSnapshot
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

object BackupCodec {
    fun encode(snapshot: LedgerSnapshot): String {
        validateSnapshot(snapshot)
        return JSONObject()
            .put("version", 3)
            .put("categories", JSONArray(snapshot.categories.map {
                JSONObject().put("id", it.id).put("name", it.name).put("type", it.type)
                    .put("icon", it.icon ?: JSONObject.NULL).put("sortOrder", it.sortOrder)
                    .put("isDeleted", it.isDeleted)
            }))
            .put("accounts", JSONArray(snapshot.accounts.map {
                JSONObject().put("id", it.id).put("name", it.name).put("isDeleted", it.isDeleted)
            }))
            .put("transactions", JSONArray(snapshot.transactions.map {
                JSONObject().put("id", it.id).put("amountMinor", it.amountMinor).put("type", it.type)
                    .put("categoryId", it.categoryId).put("accountId", it.accountId)
                    .put("note", it.note ?: JSONObject.NULL).put("occurredAt", it.occurredAt)
                    .put("createdAt", it.createdAt).put("updatedAt", it.updatedAt)
                    .put("isDeleted", it.isDeleted).put("source", it.source).put("importKey", it.importKey ?: JSONObject.NULL)
            }))
            .put("assets", JSONArray(snapshot.assets.map {
                JSONObject().put("id", it.id).put("name", it.name).put("kind", it.kind)
                    .put("purchaseMinor", it.purchaseMinor ?: JSONObject.NULL).put("valueMinor", it.valueMinor)
                    .put("pricedAt", it.pricedAt).put("referencePricesMinor", JSONArray(it.referencePricesMinor))
                    .put("note", it.note ?: JSONObject.NULL).put("createdAt", it.createdAt)
                    .put("updatedAt", it.updatedAt).put("isDeleted", it.isDeleted)
            }))
            .put("memos", JSONArray(snapshot.memos.map {
                JSONObject().put("id", it.id).put("title", it.title).put("content", it.content)
                    .put("isPinned", it.isPinned).put("isDone", it.isDone).put("createdAt", it.createdAt)
                    .put("updatedAt", it.updatedAt).put("isDeleted", it.isDeleted)
            }))
            .put("budgets", JSONArray(snapshot.budgets.map {
                JSONObject().put("month", it.month).put("amountMinor", it.amountMinor).put("updatedAt", it.updatedAt)
            }))
            .put("recurringRules", JSONArray(snapshot.recurringRules.map {
                JSONObject().put("id", it.id).put("name", it.name).put("amountMinor", it.amountMinor)
                    .put("type", it.type).put("categoryId", it.categoryId).put("accountId", it.accountId)
                    .put("nextDueAt", it.nextDueAt).put("dayOfMonth", it.dayOfMonth).put("note", it.note ?: JSONObject.NULL)
                    .put("createdAt", it.createdAt).put("updatedAt", it.updatedAt).put("isDeleted", it.isDeleted)
            })).toString()
    }

    fun decode(text: String): LedgerSnapshot {
        try {
            val input = JSONTokener(text)
            val json = input.nextValue()
            require(json is JSONObject && input.nextClean() == '\u0000') { "备份必须是一个完整的 JSON 对象。" }
            val version = json.integer("version")
            require(version in 1..3) { "不支持此备份版本。" }
            return LedgerSnapshot(
                categories = json.objects("categories").map {
                    Category(it.string("id"), it.string("name"), it.integer("type"),
                        it.nullableString("icon"), it.integer("sortOrder"), it.boolean("isDeleted"))
                },
                accounts = json.objects("accounts").map {
                    Account(it.string("id"), it.string("name"), it.boolean("isDeleted"))
                },
                transactions = json.objects("transactions").map {
                    Transaction(
                        id = it.string("id"), amountMinor = it.wholeLong("amountMinor"), type = it.integer("type"),
                        categoryId = it.string("categoryId"), accountId = it.string("accountId"),
                        note = it.nullableString("note"), occurredAt = it.wholeLong("occurredAt"),
                        createdAt = it.wholeLong("createdAt"), updatedAt = it.wholeLong("updatedAt"),
                        isDeleted = it.boolean("isDeleted"), source = it.integer("source"),
                        importKey = if (version < 3) null else it.nullableString("importKey"),
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
                        it.wholeLong("createdAt"), it.wholeLong("updatedAt"), it.boolean("isDeleted"))
                },
                memos = if (version < 3) emptyList() else json.objects("memos").map {
                    Memo(it.string("id"), it.string("title"), it.string("content"), it.boolean("isPinned"),
                        it.boolean("isDone"), it.wholeLong("createdAt"), it.wholeLong("updatedAt"), it.boolean("isDeleted"))
                },
                budgets = if (version < 3) emptyList() else json.objects("budgets").map {
                    Budget(it.string("month"), it.wholeLong("amountMinor"), it.wholeLong("updatedAt"))
                },
                recurringRules = if (version < 3) emptyList() else json.objects("recurringRules").map {
                    RecurringRule(it.string("id"), it.string("name"), it.wholeLong("amountMinor"), it.integer("type"),
                        it.string("categoryId"), it.string("accountId"), it.wholeLong("nextDueAt"), it.integer("dayOfMonth"),
                        it.nullableString("note"), it.wholeLong("createdAt"), it.wholeLong("updatedAt"), it.boolean("isDeleted"))
                },
            ).also(::validateSnapshot)
        } catch (error: JSONException) {
            throw IllegalArgumentException("备份 JSON 格式不正确或缺少字段。", error)
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
