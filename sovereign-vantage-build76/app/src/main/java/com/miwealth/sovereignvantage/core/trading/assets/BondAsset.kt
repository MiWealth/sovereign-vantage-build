package com.miwealth.sovereignvantage.core.trading.assets

import com.miwealth.sovereignvantage.core.AssetType
import java.math.BigDecimal
import java.time.LocalDate

class BondAsset(
    val id: String,
    override val symbol: String,
    val couponRate: Double,
    val maturityDate: LocalDate,
    val faceValue: Double,
    override val exchange: String = "OTC",
    override val assetType: AssetType = AssetType.BONDS,
    override val tickSize: BigDecimal = BigDecimal("0.01"),
    override val lotSize: BigDecimal = BigDecimal("1")
) : AssetClass {
    
    fun getType(): String = "BOND"
    
    fun calculateYield(currentPrice: Double): Double {
        return (couponRate * faceValue) / currentPrice
    }
    
    override fun calculateMargin(quantity: BigDecimal, price: BigDecimal, leverage: Int): BigDecimal {
        return (quantity * price) / BigDecimal(leverage)
    }
    
    override fun validateOrder(quantity: BigDecimal, price: BigDecimal): Boolean {
        return quantity >= lotSize && price > BigDecimal.ZERO
    }
}
