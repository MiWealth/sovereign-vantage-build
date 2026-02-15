package com.miwealth.sovereignvantage.core.trading.assets

class StockAsset(
    override val id: String,
    override val symbol: String,
    val exchange: String,
    val dividendYield: Double
) : AssetClass {
    
    override fun getType(): String = "STOCK"
}
