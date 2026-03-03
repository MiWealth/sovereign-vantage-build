package com.miwealth.sovereignvantage.core.trading.assets

import com.miwealth.sovereignvantage.core.AssetType
import java.math.BigDecimal
import java.time.Instant

class DerivativeAsset(
    val id: String,
    override val symbol: String,
    val underlyingAssetId: String,
    val strikePrice: Double,
    val expirationDate: Instant,
    val optionType: OptionType,
    override val exchange: String = "DERIVATIVES",
    override val assetType: AssetType = AssetType.DERIVATIVES,
    override val tickSize: BigDecimal = BigDecimal("0.01"),
    override val lotSize: BigDecimal = BigDecimal("1")
) : AssetClass {
    
    enum class OptionType { CALL, PUT }
    
    fun getType(): String = "DERIVATIVE"
    
    override fun calculateMargin(quantity: BigDecimal, price: BigDecimal, leverage: Int): BigDecimal {
        return (quantity * price) / BigDecimal(leverage)
    }
    
    override fun validateOrder(quantity: BigDecimal, price: BigDecimal): Boolean {
        return quantity >= lotSize && price > BigDecimal.ZERO
    }
}
