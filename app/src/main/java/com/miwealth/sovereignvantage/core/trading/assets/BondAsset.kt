package com.miwealth.sovereignvantage.core.trading.assets

import java.time.LocalDate

class BondAsset(
    override val id: String,
    override val symbol: String,
    val couponRate: Double,
    val maturityDate: LocalDate,
    val faceValue: Double
) : AssetClass {
    
    override fun getType(): String = "BOND"
    
    fun calculateYield(currentPrice: Double): Double {
        return (couponRate * faceValue) / currentPrice
    }
}
