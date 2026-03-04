package com.miwealth.sovereignvantage.core.trading.assets

import com.miwealth.sovereignvantage.core.AssetType
import java.math.BigDecimal

class StockAsset(
    val id: String,
    override val symbol: String,
    override val exchange: String,
    val dividendYield: Double,
    override val assetType: AssetType = AssetType.STOCKS,
    override val tickSize: BigDecimal = BigDecimal("0.01"),
    override val lotSize: BigDecimal = BigDecimal("1")
) : AssetClass {
    
    fun getType(): String = "STOCK"
    
    override fun calculateMargin(quantity: BigDecimal, price: BigDecimal, leverage: Int): BigDecimal {
        return (quantity * price) / BigDecimal(leverage)
    }
    
    override fun validateOrder(quantity: BigDecimal, price: BigDecimal): Boolean {
        return quantity >= lotSize && price > BigDecimal.ZERO
    }
}
