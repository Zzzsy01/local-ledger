package com.localledger.app.domain

import java.math.BigInteger

data class Asset(
    val id: String,
    val name: String,
    val kind: String,
    val purchaseMinor: Long?,
    val valueMinor: Long,
    val pricedAt: Long,
    val referencePricesMinor: List<Long> = emptyList(),
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
)

val assetKinds = listOf("数码", "摄影", "家电", "家具", "运动", "饰品", "其他")

// Round the arithmetic mean to cents without overflowing or using floating point.
fun averagePrice(prices: List<Long>): Long {
    require(prices.isNotEmpty() && prices.size <= 30 && prices.all { it > 0 }) { "请填写 1–30 个有效参考报价。" }
    val count = BigInteger.valueOf(prices.size.toLong())
    return prices.fold(BigInteger.ZERO) { total, price -> total + BigInteger.valueOf(price) }
        .add(count.divide(BigInteger.valueOf(2))).divide(count).toLong()
}

fun parseReferencePrices(text: String): List<Long> = if (text.isBlank()) emptyList() else
    text.trim().split(Regex("[,，\\s]+")).map {
        requireNotNull(parseAmount(it)) { "参考报价请填正数，用逗号或空格分隔。" }
    }.also { require(it.size <= 30) { "最多填写 30 个参考报价。" } }

fun validateAsset(asset: Asset) {
    require(asset.name.isNotBlank() && asset.kind.isNotBlank()) { "请填写物品名称与类别。" }
    require(asset.valueMinor >= 0 && (asset.purchaseMinor == null || asset.purchaseMinor >= 0)) { "物品金额不能为负数。" }
    require(asset.pricedAt >= 0 && asset.createdAt >= 0 && asset.updatedAt >= asset.createdAt) { "物品日期无效。" }
    if (asset.referencePricesMinor.isNotEmpty()) {
        require(asset.valueMinor == averagePrice(asset.referencePricesMinor)) { "物品估值与参考报价均值不一致。" }
    }
}
