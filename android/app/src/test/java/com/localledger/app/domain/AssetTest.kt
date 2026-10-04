package com.localledger.app.domain

import org.junit.Assert.*
import org.junit.Test

class AssetTest {
    @Test fun referencePricesAreExactAndRoundToNearestCentWithoutOverflow() {
        assertEquals(listOf(230000L, 250000L, 240000L), parseReferencePrices("2300, 2500，2400"))
        assertEquals(240000L, averagePrice(parseReferencePrices("2300 2500 2400")))
        assertEquals(2L, averagePrice(listOf(1, 2)))
        assertEquals(Long.MAX_VALUE, averagePrice(List(30) { Long.MAX_VALUE }))
        assertThrows(IllegalArgumentException::class.java) { parseReferencePrices("123,wrong") }
        assertThrows(IllegalArgumentException::class.java) { parseReferencePrices("1.001") }
        assertThrows(IllegalArgumentException::class.java) { averagePrice(listOf(0)) }
        assertThrows(IllegalArgumentException::class.java) { averagePrice(List(31) { 1 }) }
    }
    @Test fun zeroIsAllowedForAssetValueButNotLedgerTransactions() {
        assertNull(parseAmount("0"))
        assertEquals(0L, parseAmount("0.00", allowZero = true))
        assertNull(parseAmount("-0.01", allowZero = true))
    }
}
