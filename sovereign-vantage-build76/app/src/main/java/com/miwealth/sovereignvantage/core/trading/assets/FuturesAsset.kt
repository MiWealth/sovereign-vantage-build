package com.miwealth.sovereignvantage.core.trading.assets

import com.miwealth.sovereignvantage.core.AssetType
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Implementation for Futures Contracts (Crypto & Traditional).
 * Handles expiry, funding rates, and leverage logic.
 */
class FuturesAsset(
    override val symbol: String,
    override val exchange: String,
    val expiryDate: Long?, // Null for Perpetual
    val fundingIntervalHours: Int = 8
) : AssetClass {

    override val assetType = AssetType.CRYPTO_FUTURES
    override val tickSize = BigDecimal("0.50") // Example for BTC
    override val lotSize = BigDecimal("1.00")

    override fun calculateMargin(quantity: BigDecimal, price: BigDecimal, leverage: Int): BigDecimal {
        if (leverage <= 0) throw IllegalArgumentException("Leverage must be positive")
        val notionalValue = quantity.multiply(price)
        return notionalValue.divide(BigDecimal(leverage), 8, RoundingMode.HALF_UP)
    }

    override fun validateOrder(quantity: BigDecimal, price: BigDecimal): Boolean {
        if (quantity.remainder(lotSize).compareTo(BigDecimal.ZERO) != 0) {
            throw IllegalArgumentException("Quantity must be a multiple of lot size")
        }
        return true
    }

    fun calculateFundingRate(indexPrice: BigDecimal, markPrice: BigDecimal): BigDecimal {
        // Simplified funding rate calculation: (Mark - Index) / Index
        return markPrice.subtract(indexPrice).divide(indexPrice, 8, RoundingMode.HALF_UP)
    }
}
