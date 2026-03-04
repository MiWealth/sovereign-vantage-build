package com.miwealth.sovereignvantage.core.trading.assets

import java.math.BigDecimal
import java.time.Instant
import com.miwealth.sovereignvantage.core.AssetType

/**
 * Base interface for all 11 asset classes in the Sovereign Vantage.
 * Defines the standard behavior for pricing, execution, and risk calculation.
 */
interface AssetClass {
    val symbol: String
    val assetType: AssetType
    val exchange: String
    val tickSize: BigDecimal
    val lotSize: BigDecimal

    /**
     * Calculates the required margin for a given position size.
     * @param quantity The size of the position.
     * @param price The current market price.
     * @param leverage The leverage applied (1x to 100x).
     * @return The margin requirement in base currency.
     */
    fun calculateMargin(quantity: BigDecimal, price: BigDecimal, leverage: Int): BigDecimal

    /**
     * Validates if an order complies with asset-specific rules.
     * @param quantity Order quantity.
     * @param price Order price.
     * @return True if valid, throws ValidationException otherwise.
     */
    fun validateOrder(quantity: BigDecimal, price: BigDecimal): Boolean
}


