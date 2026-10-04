package com.localledger.app.domain

import java.math.BigInteger
import java.time.LocalDate
import java.time.temporal.ChronoUnit

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
    val quantity: Int = 1,
    val acquisition: String = "购买",
    val purchaseDate: String? = null,
    val channel: String? = null,
    val location: String? = null,
    val warrantyUntil: String? = null,
    val status: String = "持有中",
    val disposedDate: String? = null,
    val photoPaths: List<String> = emptyList(),
)

val assetKinds = listOf("数码", "摄影", "家电", "家具", "运动", "饰品", "交通", "工具", "玩具", "宠物", "服饰", "图书", "美妆", "食品", "其他")
val assetAcquisitions = listOf("购买", "赠送", "抽奖", "奖品", "继承", "其他")
val assetStatuses = listOf("持有中", "闲置中", "已出", "丢失", "退役")
val Asset.isHeld: Boolean get() = status == "持有中" || status == "闲置中"

data class AssetSummary(val heldValue: Long = 0, val heldQuantity: Long = 0, val idleQuantity: Long = 0, val clearedQuantity: Long = 0)

fun summarizeAssets(assets: List<Asset>): AssetSummary = assets.filterNot { it.isDeleted }.fold(AssetSummary()) { summary, asset ->
    summary.copy(
        heldValue = if (asset.isHeld) Math.addExact(summary.heldValue, asset.valueMinor) else summary.heldValue,
        heldQuantity = if (asset.isHeld) Math.addExact(summary.heldQuantity, asset.quantity.toLong()) else summary.heldQuantity,
        idleQuantity = if (asset.status == "闲置中") Math.addExact(summary.idleQuantity, asset.quantity.toLong()) else summary.idleQuantity,
        clearedQuantity = if (!asset.isHeld) Math.addExact(summary.clearedQuantity, asset.quantity.toLong()) else summary.clearedQuantity,
    )
}

// Dates are ISO calendar dates, independent of device time zone and daylight saving changes.
fun assetHoldingDays(asset: Asset, today: LocalDate = LocalDate.now()): Long? {
    val purchased = asset.purchaseDate?.let(LocalDate::parse) ?: return null
    val end = if (asset.isHeld) today else asset.disposedDate?.let(LocalDate::parse)?.let { minOf(it, today) } ?: today
    return maxOf(1L, ChronoUnit.DAYS.between(purchased, end) + 1L)
}

fun assetDailyCostMinor(asset: Asset, today: LocalDate = LocalDate.now()): Long? {
    val purchase = asset.purchaseMinor ?: return null
    val days = assetHoldingDays(asset, today) ?: return null
    val divisor = BigInteger.valueOf(days)
    return BigInteger.valueOf(purchase).add(divisor.divide(BigInteger.valueOf(2))).divide(divisor).toLong()
}

fun isAssetPhotoPath(path: String): Boolean = Regex("asset-photos/[0-9a-fA-F-]{36}\\.img").matches(path)

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
    require(asset.quantity > 0) { "物品数量至少为 1。" }
    require(asset.acquisition in assetAcquisitions && asset.status in assetStatuses) { "物品获取方式或状态无效。" }
    val purchased = asset.purchaseDate?.let { LocalDate.parse(it) }
    val disposed = asset.disposedDate?.let { LocalDate.parse(it) }
    val warranty = asset.warrantyUntil?.let { LocalDate.parse(it) }
    require(purchased == null || purchased <= LocalDate.now()) { "购入日期不能晚于今天。" }
    require(disposed == null || disposed <= LocalDate.now()) { "处置日期不能晚于今天。" }
    require(purchased == null || disposed == null || disposed >= purchased) { "处置日期不能早于购入日期。" }
    require(purchased == null || warranty == null || warranty >= purchased) { "保修到期不能早于购入日期。" }
    require(asset.isHeld || disposed != null) { "请填写物品处置日期。" }
    require(asset.photoPaths.size <= 4 && asset.photoPaths.distinct().size == asset.photoPaths.size && asset.photoPaths.all(::isAssetPhotoPath)) { "物品照片路径无效，最多添加 4 张。" }
    if (asset.referencePricesMinor.isNotEmpty()) {
        require(asset.valueMinor == averagePrice(asset.referencePricesMinor)) { "物品估值与参考报价均值不一致。" }
    }
}
