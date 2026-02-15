package com.miwealth.sovereignvantage.core.trading.assets

import java.time.Instant

class DerivativeAsset(
    override val id: String,
    override val symbol: String,
    val underlyingAssetId: String,
    val strikePrice: Double,
    val expirationDate: Instant,
    val optionType: OptionType // CALL or PUT
) : AssetClass {
    
    enum class OptionType { CALL, PUT }
    
    override fun getType(): String = "DERIVATIVE"
}
