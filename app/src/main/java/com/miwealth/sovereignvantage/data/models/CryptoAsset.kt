/*
 * Sovereign Vantage - Arthur Edition
 * CryptoAsset.kt - Asset metadata schema with Room persistence
 * 
 * Copyright 2025-2026 MiWealth Pty Ltd
 * Creator: Mike Stahl
 * Co-Founder (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

package com.miwealth.sovereignvantage.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters

/**
 * Asset category classification for risk parameter lookup and UI filtering.
 * Categories align with CRYPTO_ASSET_EXPANSION_SPEC.md
 */
enum class AssetCategory {
    LAYER_1,            // Bitcoin, Ethereum, Solana, etc.
    LAYER_2,            // Arbitrum, Optimism, Polygon, etc.
    DEFI,               // Uniswap, Aave, Curve, etc.
    MEME,               // DOGE, SHIB, PEPE, WIF, etc.
    AI_ML,              // FET, TAO, RNDR, WLD, etc.
    GAMING_NFT,         // AXS, SAND, IMX, GALA, etc.
    INFRASTRUCTURE,     // LINK, GRT, AR, etc.
    PRIVACY,            // XMR, ZEC, DASH, etc.
    RWA_GOLD,           // PAXG, ONDO, etc.
    EXCHANGE_TOKEN,     // BNB, CRO, OKB, etc.
    STABLECOIN,         // USDT, USDC, DAI (quote currency, not traded)
    OTHER               // Uncategorised assets
}

/**
 * Market capitalisation tier for position sizing adjustments.
 * Thresholds based on crypto market standards.
 */
enum class MarketCapTier(val minUsd: Long, val maxUsd: Long?) {
    MEGA(100_000_000_000L, null),           // > $100B (BTC, ETH)
    LARGE(10_000_000_000L, 100_000_000_000L), // $10B - $100B
    MID(1_000_000_000L, 10_000_000_000L),     // $1B - $10B
    SMALL(100_000_000L, 1_000_000_000L),      // $100M - $1B
    MICRO(0L, 100_000_000L)                   // < $100M
}

/**
 * Volatility classification for risk management.
 * Based on annualised historical volatility.
 */
enum class VolatilityTier(val maxAnnualVol: Double?) {
    LOW(0.30),          // < 30% annual volatility (stables, gold-backed)
    MEDIUM(0.60),       // 30-60% (BTC, ETH in calm periods)
    HIGH(1.00),         // 60-100% (most altcoins)
    EXTREME(null)       // > 100% (memes, new launches)
}

/**
 * Room type converters for enum persistence.
 */
class AssetTypeConverters {
    @TypeConverter
    fun fromCategory(category: AssetCategory): String = category.name
    
    @TypeConverter
    fun toCategory(value: String): AssetCategory = AssetCategory.valueOf(value)
    
    @TypeConverter
    fun fromMarketCapTier(tier: MarketCapTier): String = tier.name
    
    @TypeConverter
    fun toMarketCapTier(value: String): MarketCapTier = MarketCapTier.valueOf(value)
    
    @TypeConverter
    fun fromVolatilityTier(tier: VolatilityTier): String = tier.name
    
    @TypeConverter
    fun toVolatilityTier(value: String): VolatilityTier = VolatilityTier.valueOf(value)
}

/**
 * Core asset metadata entity.
 * Stores all information needed for trading decisions, risk management,
 * and exchange routing.
 * 
 * Design principles:
 * - Symbol is base asset only (BTC, ETH) - connectors derive exchange-specific formats
 * - Risk parameters can override category defaults per asset
 * - Exchange availability flags enable multi-exchange routing
 * - Soft delete via isActive flag preserves history
 */
@Entity(tableName = "crypto_assets")
@TypeConverters(AssetTypeConverters::class)
data class CryptoAsset(
    /**
     * Canonical symbol (uppercase). e.g., "BTC", "ETH", "SOL"
     * This is the base asset - quote currency handled separately.
     */
    @PrimaryKey 
    val symbol: String,
    
    /**
     * Human-readable name. e.g., "Bitcoin", "Ethereum"
     */
    val name: String,
    
    /**
     * Primary category for risk parameter defaults and UI grouping.
     */
    val category: AssetCategory,
    
    /**
     * Optional subcategory for finer classification.
     * e.g., "DEX", "Lending", "Optimistic Rollup", "ZK Rollup"
     */
    val subcategory: String? = null,
    
    /**
     * Primary blockchain/network. e.g., "Bitcoin", "Ethereum", "Solana"
     * For tokens, this is the chain they primarily exist on.
     */
    val primaryChain: String,
    
    /**
     * Market cap tier at time of data entry.
     * Used for position sizing adjustments.
     */
    val marketCapTier: MarketCapTier,
    
    /**
     * Volatility classification.
     * Used for stop loss and STAHL parameter selection.
     */
    val volatilityTier: VolatilityTier,
    
    // ========== RISK PARAMETERS ==========
    // These can override category defaults on a per-asset basis
    
    /**
     * Kelly criterion multiplier (0.0 - 1.0).
     * Lower = more conservative position sizing.
     * Reference: LAYER_1 Tier 1 = 1.0, MEME = 0.3, NEW_LAUNCH = 0.2
     */
    val kellyMultiplier: Double,
    
    /**
     * Maximum position size as fraction of portfolio (0.0 - 1.0).
     * e.g., 0.15 = max 15% of portfolio in this asset.
     */
    val maxPositionPercent: Double,
    
    /**
     * Default initial stop loss as fraction (0.0 - 1.0).
     * e.g., 0.08 = 8% stop loss.
     * Note: STAHL may override this dynamically based on ATR.
     */
    val defaultStopLossPercent: Double,
    
    /**
     * Recommended leverage for this asset class.
     * From backtest: Crypto 5-6x, Stocks 2.5-3x, ETFs 3-4x
     */
    val recommendedLeverage: Double = 1.0,
    
    // ========== EXCHANGE AVAILABILITY ==========
    // Flags for routing and feature support
    
    val onKraken: Boolean = false,
    val onBinance: Boolean = false,
    val onBybit: Boolean = false,
    val onCoinbase: Boolean = false,
    val onOkx: Boolean = false,
    
    /**
     * Can this asset be shorted via perpetual futures?
     */
    val canShortFutures: Boolean = false,
    
    /**
     * Can this asset be shorted via margin trading?
     */
    val canShortMargin: Boolean = false,
    
    // ========== FLAGS ==========
    
    /**
     * Is this a stablecoin? Used as quote currency, not for directional trading.
     */
    val isStablecoin: Boolean = false,
    
    /**
     * Is this a privacy coin? May have exchange restrictions.
     */
    val isPrivacyCoin: Boolean = false,
    
    /**
     * Is this asset enabled for trading?
     * User can disable; soft delete preserves data.
     */
    val isActive: Boolean = true,
    
    /**
     * Is this a newly launched asset? Triggers special risk handling.
     */
    val isNewLaunch: Boolean = false,
    
    // ========== METADATA ==========
    
    /**
     * Launch/listing date (ISO format: "2024-01-15").
     * Null for assets predating our tracking.
     */
    val launchDate: String? = null,
    
    /**
     * CoinGecko ID for API lookups. e.g., "bitcoin", "ethereum"
     */
    val coingeckoId: String? = null,
    
    /**
     * Timestamp when this asset was added to our database.
     */
    val addedAt: Long = System.currentTimeMillis(),
    
    /**
     * Timestamp of last metadata update.
     */
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Check if asset can be shorted on any exchange.
     */
    fun canShort(): Boolean = canShortFutures || canShortMargin
    
    /**
     * Check if asset is available on at least one supported exchange.
     */
    fun isAvailable(): Boolean = onKraken || onBinance || onBybit || onCoinbase || onOkx
    
    /**
     * Get list of exchanges where this asset is available.
     */
    fun availableExchanges(): List<String> = buildList {
        if (onKraken) add("Kraken")
        if (onBinance) add("Binance")
        if (onBybit) add("Bybit")
        if (onCoinbase) add("Coinbase")
        if (onOkx) add("OKX")
    }
    
    /**
     * Check if this asset is suitable for the New Launch Strategy.
     */
    fun eligibleForNewLaunchStrategy(): Boolean = 
        isNewLaunch && canShort() && !isStablecoin
    
    companion object {
        /**
         * Default risk parameters by category.
         * Used when creating assets without explicit overrides.
         * 
         * IMPORTANT: Stop loss is ALWAYS 3.5% (0.035) - SACRED value from backtest optimization.
         * This applies to ALL crypto and forex assets regardless of category.
         * Do NOT change without extensive re-validation.
         */
        fun defaultRiskParams(category: AssetCategory, tier: MarketCapTier = MarketCapTier.MID): Triple<Double, Double, Double> {
            // Returns: (kellyMultiplier, maxPositionPercent, defaultStopLossPercent)
            // SACRED: Stop loss is ALWAYS 3.5% for all tradeable assets
            val sacredStopLoss = 0.035
            
            return when (category) {
                AssetCategory.LAYER_1 -> when (tier) {
                    MarketCapTier.MEGA, MarketCapTier.LARGE -> Triple(1.0, 0.15, sacredStopLoss)
                    MarketCapTier.MID -> Triple(0.8, 0.10, sacredStopLoss)
                    else -> Triple(0.6, 0.08, sacredStopLoss)
                }
                AssetCategory.LAYER_2 -> Triple(0.7, 0.08, sacredStopLoss)
                AssetCategory.DEFI -> Triple(0.6, 0.08, sacredStopLoss)
                AssetCategory.MEME -> Triple(0.3, 0.03, sacredStopLoss)
                AssetCategory.AI_ML -> Triple(0.5, 0.05, sacredStopLoss)
                AssetCategory.GAMING_NFT -> Triple(0.4, 0.05, sacredStopLoss)
                AssetCategory.INFRASTRUCTURE -> Triple(0.6, 0.08, sacredStopLoss)
                AssetCategory.PRIVACY -> Triple(0.5, 0.05, sacredStopLoss)
                AssetCategory.RWA_GOLD -> Triple(0.8, 0.10, sacredStopLoss)
                AssetCategory.EXCHANGE_TOKEN -> Triple(0.5, 0.05, sacredStopLoss)
                AssetCategory.STABLECOIN -> Triple(0.0, 0.0, 0.0) // Not traded
                AssetCategory.OTHER -> Triple(0.4, 0.05, sacredStopLoss)
            }
        }
        
        /**
         * Default leverage by category.
         * From backtesting: Crypto 5-6x optimal with 3.5% initial stop (SACRED value).
         */
        fun defaultLeverage(category: AssetCategory): Double {
            return when (category) {
                AssetCategory.LAYER_1 -> 5.5
                AssetCategory.LAYER_2 -> 5.0
                AssetCategory.DEFI -> 4.0
                AssetCategory.MEME -> 3.0  // Lower due to extreme volatility
                AssetCategory.AI_ML -> 4.0
                AssetCategory.GAMING_NFT -> 3.5
                AssetCategory.INFRASTRUCTURE -> 4.5
                AssetCategory.PRIVACY -> 4.0
                AssetCategory.RWA_GOLD -> 3.0  // More conservative
                AssetCategory.EXCHANGE_TOKEN -> 4.0
                AssetCategory.STABLECOIN -> 1.0
                AssetCategory.OTHER -> 3.0
            }
        }
    }
}

/**
 * Lightweight asset reference for UI lists and quick lookups.
 * Avoids loading full entity when only basic info needed.
 */
data class AssetSummary(
    val symbol: String,
    val name: String,
    val category: AssetCategory,
    val isActive: Boolean
)

/**
 * Asset with calculated trading parameters.
 * Used when preparing to execute trades.
 */
data class TradableAsset(
    val asset: CryptoAsset,
    val effectiveKelly: Double,
    val effectiveMaxPosition: Double,
    val effectiveStopLoss: Double,
    val effectiveLeverage: Double,
    val preferredExchange: String
) {
    companion object {
        /**
         * Create TradableAsset with risk adjustments applied.
         * @param asset Base asset metadata
         * @param riskMultiplier Global risk multiplier from user settings (0.0-2.0)
         * @param preferredExchange User's preferred exchange or best available
         */
        fun from(
            asset: CryptoAsset,
            riskMultiplier: Double = 1.0,
            preferredExchange: String? = null
        ): TradableAsset {
            val exchange = preferredExchange 
                ?: asset.availableExchanges().firstOrNull() 
                ?: "Unknown"
            
            return TradableAsset(
                asset = asset,
                effectiveKelly = asset.kellyMultiplier * riskMultiplier,
                effectiveMaxPosition = asset.maxPositionPercent * riskMultiplier,
                effectiveStopLoss = asset.defaultStopLossPercent,
                effectiveLeverage = asset.recommendedLeverage,
                preferredExchange = exchange
            )
        }
    }
}
