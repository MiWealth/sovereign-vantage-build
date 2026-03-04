package com.miwealth.sovereignvantage.core.trading.assets

/**
 * Dynamic Risk Assigner - Universal Cryptocurrency Risk Parameter Assignment
 * 
 * Sovereign Vantage: Arthur Edition V5.17.0
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * Automatically assigns risk parameters for ANY cryptocurrency based on:
 * - Market capitalisation (from exchange or CoinGecko)
 * - Historical volatility (calculated from OHLC data)
 * - Liquidity (24h volume / market cap ratio)
 * - Exchange availability
 * 
 * This enables Sovereign Vantage to trade ANY cryptocurrency encountered,
 * not just those in the curated asset catalog.
 * 
 * CRITICAL: Initial stop loss is ALWAYS 3.5% (SACRED value from backtesting)
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */



import com.miwealth.sovereignvantage.data.models.AssetCategory
import com.miwealth.sovereignvantage.data.models.MarketCapTier
import com.miwealth.sovereignvantage.data.models.VolatilityTier
import kotlin.math.sqrt

/**
 * Dynamic risk profile assigned to unknown/new assets.
 */
data class DynamicRiskProfile(
    /** Symbol being assessed */
    val symbol: String,
    
    /** Kelly criterion multiplier (0.0 - 1.0) */
    val kellyMultiplier: Double,
    
    /** Maximum position size as fraction of portfolio (0.0 - 1.0) */
    val maxPositionPercent: Double,
    
    /** Initial stop loss percentage - ALWAYS 3.5% (SACRED) */
    val stopLossPercent: Double = 0.035,
    
    /** Recommended leverage multiplier */
    val recommendedLeverage: Double,
    
    /** Whether shorting is available */
    val canShort: Boolean,
    
    /** Inferred market cap tier */
    val marketCapTier: MarketCapTier,
    
    /** Inferred volatility tier */
    val volatilityTier: VolatilityTier,
    
    /** Inferred asset category */
    val inferredCategory: AssetCategory,
    
    /** Confidence in the assessment (0.0 - 1.0) */
    val confidence: Double,
    
    /** Reasoning for the assigned parameters */
    val reasoning: String
)

/**
 * Market data required for risk assessment.
 */
data class AssetMarketData(
    val symbol: String,
    val marketCapUsd: Double?,
    val volume24hUsd: Double?,
    val priceChangePercent24h: Double?,
    val priceHistory: List<Double>? = null,  // Recent prices for volatility calc
    val availableExchanges: List<String> = emptyList()
)

/**
 * Assigns risk parameters dynamically for any cryptocurrency.
 * 
 * Usage:
 * ```kotlin
 * val assigner = DynamicRiskAssigner()
 * val marketData = AssetMarketData(
 *     symbol = "NEWCOIN",
 *     marketCapUsd = 50_000_000.0,
 *     volume24hUsd = 5_000_000.0,
 *     priceChangePercent24h = 15.0,
 *     availableExchanges = listOf("BINANCE", "BYBIT")
 * )
 * val riskProfile = assigner.assignRiskParameters(marketData)
 * ```
 */
class DynamicRiskAssigner {
    
    companion object {
        private const val TAG = "DynamicRiskAssigner"
        
        // SACRED VALUE - 3.5% stop loss from backtest optimization
        // DO NOT CHANGE without extensive re-validation
        const val SACRED_STOP_LOSS_PERCENT = 0.035
        
        // Exchanges that support futures/shorting
        val FUTURES_EXCHANGES = setOf(
            "BINANCE", "BYBIT", "OKX", "KRAKEN", "BITGET", "GATEIO", "MEXC"
        )
        
        // Known meme coin symbols (partial list, expand as needed)
        val MEME_SYMBOLS = setOf(
            "DOGE", "SHIB", "PEPE", "FLOKI", "BONK", "WIF", "MEME", "BRETT",
            "WOJAK", "TURBO", "SPONGE", "BABYDOGE", "KISHU", "ELON", "SAMO",
            "MYRO", "BOME", "SLERF", "POPCAT", "MOG", "NEIRO"
        )
        
        // Known AI/ML tokens
        val AI_SYMBOLS = setOf(
            "FET", "TAO", "RNDR", "WLD", "AGIX", "OCEAN", "ARKM", "CTXC",
            "NMR", "ALI", "RSS3", "PHB", "MDT"
        )
        
        // Known DeFi tokens
        val DEFI_SYMBOLS = setOf(
            "UNI", "AAVE", "CRV", "SUSHI", "COMP", "MKR", "SNX", "YFI",
            "1INCH", "DYDX", "GMX", "LDO", "RPL", "PENDLE", "JUP", "RAY"
        )
        
        // Known Layer 2 tokens
        val LAYER2_SYMBOLS = setOf(
            "ARB", "OP", "MATIC", "IMX", "MNT", "STRK", "ZK", "MANTA",
            "METIS", "BOBA", "CELO", "SCROLL"
        )
        
        // Known gaming/NFT tokens
        val GAMING_SYMBOLS = setOf(
            "APE", "BLUR", "GALA", "SAND", "MANA", "AXS", "ENJ", "IMX",
            "PRIME", "PIXEL", "PORTAL", "BIGTIME", "MAGIC", "ILV", "GODS"
        )
    }
    
    /**
     * Assign risk parameters for an asset based on market data.
     * 
     * @param data Market data for the asset
     * @return DynamicRiskProfile with assigned parameters
     */
    fun assignRiskParameters(data: AssetMarketData): DynamicRiskProfile {
        val reasoning = StringBuilder()
        
        // Step 1: Determine market cap tier
        val marketCapTier = inferMarketCapTier(data.marketCapUsd)
        reasoning.append("Market cap tier: $marketCapTier. ")
        
        // Step 2: Calculate volatility tier
        val volatilityTier = inferVolatilityTier(data.priceChangePercent24h, data.priceHistory)
        reasoning.append("Volatility tier: $volatilityTier. ")
        
        // Step 3: Infer category
        val category = inferCategory(data.symbol)
        reasoning.append("Category: $category. ")
        
        // Step 4: Calculate liquidity ratio
        val liquidityRatio = calculateLiquidityRatio(data.volume24hUsd, data.marketCapUsd)
        reasoning.append("Liquidity ratio: ${"%.2f".format(liquidityRatio * 100)}%. ")
        
        // Step 5: Determine if shorting is available
        val canShort = data.availableExchanges.any { it.uppercase() in FUTURES_EXCHANGES }
        
        // Step 6: Calculate Kelly multiplier
        val kelly = calculateKellyMultiplier(marketCapTier, volatilityTier, category, liquidityRatio)
        
        // Step 7: Calculate max position
        val maxPosition = calculateMaxPosition(marketCapTier, volatilityTier, category, liquidityRatio)
        
        // Step 8: Calculate recommended leverage
        val leverage = calculateRecommendedLeverage(marketCapTier, volatilityTier, category)
        
        // Step 9: Calculate confidence
        val confidence = calculateConfidence(data)
        reasoning.append("Confidence: ${"%.0f".format(confidence * 100)}%.")
        
        return DynamicRiskProfile(
            symbol = data.symbol,
            kellyMultiplier = kelly,
            maxPositionPercent = maxPosition,
            stopLossPercent = SACRED_STOP_LOSS_PERCENT,  // ALWAYS 3.5%
            recommendedLeverage = leverage,
            canShort = canShort,
            marketCapTier = marketCapTier,
            volatilityTier = volatilityTier,
            inferredCategory = category,
            confidence = confidence,
            reasoning = reasoning.toString()
        )
    }
    
    /**
     * Infer market cap tier from USD market cap.
     */
    private fun inferMarketCapTier(marketCapUsd: Double?): MarketCapTier {
        return when {
            marketCapUsd == null -> MarketCapTier.MICRO  // Unknown = most conservative
            marketCapUsd >= 100_000_000_000 -> MarketCapTier.MEGA      // > $100B
            marketCapUsd >= 10_000_000_000 -> MarketCapTier.LARGE      // $10B - $100B
            marketCapUsd >= 1_000_000_000 -> MarketCapTier.MID         // $1B - $10B
            marketCapUsd >= 100_000_000 -> MarketCapTier.SMALL         // $100M - $1B
            else -> MarketCapTier.MICRO                                 // < $100M
        }
    }
    
    /**
     * Infer volatility tier from price change and/or price history.
     */
    private fun inferVolatilityTier(
        priceChangePercent24h: Double?,
        priceHistory: List<Double>?
    ): VolatilityTier {
        // If we have price history, calculate realized volatility
        if (priceHistory != null && priceHistory.size >= 2) {
            val annualizedVol = calculateAnnualizedVolatility(priceHistory)
            return when {
                annualizedVol <= 0.30 -> VolatilityTier.LOW
                annualizedVol <= 0.60 -> VolatilityTier.MEDIUM
                annualizedVol <= 1.00 -> VolatilityTier.HIGH
                else -> VolatilityTier.EXTREME
            }
        }
        
        // Fallback: estimate from 24h price change
        val absChange = kotlin.math.abs(priceChangePercent24h ?: 0.0)
        return when {
            absChange <= 2.0 -> VolatilityTier.LOW           // < 2% daily = stable
            absChange <= 5.0 -> VolatilityTier.MEDIUM        // 2-5% = normal
            absChange <= 10.0 -> VolatilityTier.HIGH         // 5-10% = volatile
            else -> VolatilityTier.EXTREME                   // > 10% = extreme
        }
    }
    
    /**
     * Calculate annualized volatility from price history.
     * Assumes daily prices.
     */
    private fun calculateAnnualizedVolatility(prices: List<Double>): Double {
        if (prices.size < 2) return 0.0
        
        // Calculate log returns
        val returns = mutableListOf<Double>()
        for (i in 1 until prices.size) {
            if (prices[i - 1] > 0) {
                returns.add(kotlin.math.ln(prices[i] / prices[i - 1]))
            }
        }
        
        if (returns.isEmpty()) return 0.0
        
        // Calculate standard deviation
        val mean = returns.average()
        val variance = returns.map { (it - mean) * (it - mean) }.average()
        val dailyVol = sqrt(variance)
        
        // Annualize (assuming 365 trading days for crypto)
        return dailyVol * sqrt(365.0)
    }
    
    /**
     * Infer asset category from symbol.
     */
    private fun inferCategory(symbol: String): AssetCategory {
        val upperSymbol = symbol.uppercase()
        
        return when {
            upperSymbol in MEME_SYMBOLS -> AssetCategory.MEME
            upperSymbol in AI_SYMBOLS -> AssetCategory.AI_ML
            upperSymbol in DEFI_SYMBOLS -> AssetCategory.DEFI
            upperSymbol in LAYER2_SYMBOLS -> AssetCategory.LAYER_2
            upperSymbol in GAMING_SYMBOLS -> AssetCategory.GAMING_NFT
            upperSymbol in listOf("BTC", "ETH") -> AssetCategory.LAYER_1
            upperSymbol in listOf("USDT", "USDC", "DAI", "BUSD") -> AssetCategory.STABLECOIN
            else -> AssetCategory.OTHER  // Default for unknown
        }
    }
    
    /**
     * Calculate liquidity ratio (volume / market cap).
     * Higher is better - indicates the asset can be traded without significant slippage.
     */
    private fun calculateLiquidityRatio(volume24hUsd: Double?, marketCapUsd: Double?): Double {
        if (volume24hUsd == null || marketCapUsd == null || marketCapUsd <= 0) {
            return 0.01  // Assume poor liquidity if unknown
        }
        return (volume24hUsd / marketCapUsd).coerceIn(0.0, 1.0)
    }
    
    /**
     * Calculate Kelly criterion multiplier based on risk factors.
     * Range: 0.1 (very conservative) to 1.0 (full Kelly)
     */
    private fun calculateKellyMultiplier(
        marketCapTier: MarketCapTier,
        volatilityTier: VolatilityTier,
        category: AssetCategory,
        liquidityRatio: Double
    ): Double {
        // Base Kelly by market cap
        var kelly = when (marketCapTier) {
            MarketCapTier.MEGA -> 1.0
            MarketCapTier.LARGE -> 0.8
            MarketCapTier.MID -> 0.6
            MarketCapTier.SMALL -> 0.4
            MarketCapTier.MICRO -> 0.2
        }
        
        // Adjust for volatility (higher vol = lower Kelly)
        kelly *= when (volatilityTier) {
            VolatilityTier.LOW -> 1.0
            VolatilityTier.MEDIUM -> 0.9
            VolatilityTier.HIGH -> 0.75
            VolatilityTier.EXTREME -> 0.5
        }
        
        // Adjust for category
        kelly *= when (category) {
            AssetCategory.LAYER_1 -> 1.0
            AssetCategory.LAYER_2 -> 0.9
            AssetCategory.DEFI -> 0.8
            AssetCategory.INFRASTRUCTURE -> 0.85
            AssetCategory.AI_ML -> 0.75
            AssetCategory.GAMING_NFT -> 0.7
            AssetCategory.MEME -> 0.4      // Very conservative for memes
            AssetCategory.PRIVACY -> 0.7
            AssetCategory.RWA_GOLD -> 0.9
            AssetCategory.EXCHANGE_TOKEN -> 0.75
            AssetCategory.STABLECOIN -> 0.0  // Don't trade stablecoins directionally
            AssetCategory.OTHER -> 0.5       // Unknown = conservative
        }
        
        // Adjust for liquidity (poor liquidity = lower Kelly)
        if (liquidityRatio < 0.01) {
            kelly *= 0.5  // Very illiquid
        } else if (liquidityRatio < 0.05) {
            kelly *= 0.75  // Moderately illiquid
        }
        
        return kelly.coerceIn(0.1, 1.0)
    }
    
    /**
     * Calculate maximum position size as fraction of portfolio.
     */
    private fun calculateMaxPosition(
        marketCapTier: MarketCapTier,
        volatilityTier: VolatilityTier,
        category: AssetCategory,
        liquidityRatio: Double
    ): Double {
        // Base position by market cap
        var maxPos = when (marketCapTier) {
            MarketCapTier.MEGA -> 0.20      // Up to 20% in BTC/ETH
            MarketCapTier.LARGE -> 0.15     // Up to 15% in large caps
            MarketCapTier.MID -> 0.10       // Up to 10% in mid caps
            MarketCapTier.SMALL -> 0.05     // Up to 5% in small caps
            MarketCapTier.MICRO -> 0.02     // Max 2% in micro caps
        }
        
        // Reduce for high volatility
        maxPos *= when (volatilityTier) {
            VolatilityTier.LOW -> 1.0
            VolatilityTier.MEDIUM -> 0.9
            VolatilityTier.HIGH -> 0.75
            VolatilityTier.EXTREME -> 0.5
        }
        
        // Reduce for risky categories
        maxPos *= when (category) {
            AssetCategory.MEME -> 0.5       // Max 1% even for large memes
            AssetCategory.OTHER -> 0.6      // Unknown = conservative
            else -> 1.0
        }
        
        // Reduce for poor liquidity
        if (liquidityRatio < 0.01) {
            maxPos *= 0.5
        }
        
        return maxPos.coerceIn(0.01, 0.20)
    }
    
    /**
     * Calculate recommended leverage based on risk factors.
     */
    private fun calculateRecommendedLeverage(
        marketCapTier: MarketCapTier,
        volatilityTier: VolatilityTier,
        category: AssetCategory
    ): Double {
        // Base leverage by market cap
        var leverage = when (marketCapTier) {
            MarketCapTier.MEGA -> 5.5       // Full leverage for BTC/ETH
            MarketCapTier.LARGE -> 5.0
            MarketCapTier.MID -> 4.0
            MarketCapTier.SMALL -> 3.0
            MarketCapTier.MICRO -> 2.0      // Very conservative for micro caps
        }
        
        // Reduce for high volatility
        leverage *= when (volatilityTier) {
            VolatilityTier.LOW -> 1.0
            VolatilityTier.MEDIUM -> 0.9
            VolatilityTier.HIGH -> 0.75
            VolatilityTier.EXTREME -> 0.5
        }
        
        // Reduce for risky categories
        leverage *= when (category) {
            AssetCategory.MEME -> 0.6       // Max ~2x for memes
            AssetCategory.OTHER -> 0.7      // Conservative for unknown
            else -> 1.0
        }
        
        return leverage.coerceIn(1.0, 5.5)
    }
    
    /**
     * Calculate confidence in the risk assessment.
     * Higher confidence when we have more data.
     */
    private fun calculateConfidence(data: AssetMarketData): Double {
        var confidence = 0.0
        
        // Market cap data adds confidence
        if (data.marketCapUsd != null) {
            confidence += 0.3
        }
        
        // Volume data adds confidence
        if (data.volume24hUsd != null) {
            confidence += 0.2
        }
        
        // Price change data adds confidence
        if (data.priceChangePercent24h != null) {
            confidence += 0.1
        }
        
        // Price history adds significant confidence
        if (data.priceHistory != null && data.priceHistory.size >= 30) {
            confidence += 0.3
        } else if (data.priceHistory != null && data.priceHistory.size >= 7) {
            confidence += 0.2
        }
        
        // Exchange availability adds confidence
        if (data.availableExchanges.isNotEmpty()) {
            confidence += 0.1
        }
        
        return confidence.coerceIn(0.0, 1.0)
    }
    
    /**
     * Quick assessment for assets with minimal data.
     * Used when only symbol and exchange list are known.
     */
    fun quickAssessment(symbol: String, exchanges: List<String>): DynamicRiskProfile {
        return assignRiskParameters(AssetMarketData(
            symbol = symbol,
            marketCapUsd = null,  // Unknown
            volume24hUsd = null,
            priceChangePercent24h = null,
            availableExchanges = exchanges
        ))
    }
    
    /**
     * Batch assess multiple assets.
     */
    fun assessBatch(assets: List<AssetMarketData>): List<DynamicRiskProfile> {
        return assets.map { assignRiskParameters(it) }
    }
}

/**
 * Extension to convert DynamicRiskProfile to CryptoAsset for persistence.
 */
fun DynamicRiskProfile.toCryptoAsset(
    name: String = symbol,
    primaryChain: String = "Unknown",
    exchanges: Map<String, Boolean> = emptyMap()
): com.miwealth.sovereignvantage.data.models.CryptoAsset {
    return com.miwealth.sovereignvantage.data.models.CryptoAsset(
        symbol = symbol,
        name = name,
        category = inferredCategory,
        subcategory = "Dynamically Assessed",
        primaryChain = primaryChain,
        marketCapTier = marketCapTier,
        volatilityTier = volatilityTier,
        kellyMultiplier = kellyMultiplier,
        maxPositionPercent = maxPositionPercent,
        defaultStopLossPercent = stopLossPercent,
        recommendedLeverage = recommendedLeverage,
        onKraken = exchanges["KRAKEN"] ?: false,
        onBinance = exchanges["BINANCE"] ?: false,
        onBybit = exchanges["BYBIT"] ?: false,
        onCoinbase = exchanges["COINBASE"] ?: false,
        onOkx = exchanges["OKX"] ?: false,
        canShortFutures = canShort,
        canShortMargin = false
    )
}
