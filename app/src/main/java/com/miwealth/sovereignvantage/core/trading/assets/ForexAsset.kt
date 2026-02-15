package com.miwealth.sovereignvantage.core.trading.assets

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Implementation for Forex Pairs (Spot & CFDs).
 * Handles pip calculations and standard lot sizes.
 */
class ForexAsset(
    override val symbol: String, // e.g., "EUR/USD"
    override val exchange: String = "LMAX"
) : AssetClass {

    override val assetType = AssetType.FOREX
    override val tickSize = BigDecimal("0.00001") // 1 Pipette
    override val lotSize = BigDecimal("1000") // Micro Lot

    override fun calculateMargin(quantity: BigDecimal, price: BigDecimal, leverage: Int): BigDecimal {
        // Forex margin is typically fixed per lot based on leverage
        // Standard Lot (100,000 units) at 100:1 leverage = 1,000 units margin
        val notionalValue = quantity // Base currency units
        return notionalValue.divide(BigDecimal(leverage), 2, RoundingMode.HALF_UP)
    }

    override fun validateOrder(quantity: BigDecimal, price: BigDecimal): Boolean {
        if (quantity < BigDecimal("1000")) {
            throw IllegalArgumentException("Minimum order size is 1 Micro Lot (1000 units)")
        }
        return true
    }

    fun calculatePipValue(pipAmount: Int, exchangeRate: BigDecimal): BigDecimal {
        return BigDecimal(pipAmount).multiply(tickSize).multiply(BigDecimal("10")) // Standard pip value logic
    }
}
