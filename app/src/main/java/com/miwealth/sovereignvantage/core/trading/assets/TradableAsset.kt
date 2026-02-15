/**
 * Tradable Asset - Individual Symbol Data Model
 * 
 * Sovereign Vantage: Arthur Edition V5.5.12
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * Represents a single tradable instrument with all its trading rules,
 * constraints, and metadata. Used by AssetRegistry for dynamic symbol loading.
 * 
 * DESIGN RATIONALE:
 * - BigDecimal for all monetary/size values (precision for institutional use)
 * - Dual classification: AssetType (broad) + AssetCategory (granular)
 * - Exchange-specific constraints honored for compliance
 * - Scalping flag enables/disables high-frequency strategies per asset
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 */
package com.miwealth.sovereignvantage.core.trading.assets

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/**
 * Represents a tradable asset with its specifications and constraints.
 * 
 * @property symbol Trading pair symbol (e.g., "BTC/USDT", "EUR/USD")
 * @property baseAsset Base currency/asset (e.g., "BTC", "EUR")
 * @property quoteAsset Quote currency (e.g., "USDT", "USD")
 * @property assetType Broad asset classification
 * @property category Granular sub-classification
 * @property exchange Primary exchange for this asset
 * @property minOrderSize Minimum order quantity
 * @property maxOrderSize Maximum order quantity (null = unlimited)
 * @property tickSize Minimum price increment
 * @property lotSize Minimum quantity increment
 * @property maxLeverage Maximum allowed leverage (1 = spot only)
 * @property makerFee Maker fee as decimal (0.001 = 0.1%)
 * @property takerFee Taker fee as decimal
 * @property scalpingEnabled Whether high-frequency trading is allowed
 * @property marginEnabled Whether margin trading is available
 * @property tradingHours Market hours (null = use default for asset type)
 * @property status Current trading status
 * @property lastUpdated When this data was last refreshed
 */
data class TradableAsset(
    val symbol: String,
    val baseAsset: String,
    val quoteAsset: String,
    val assetType: AssetType,
    val category: AssetCategory,
    val exchange: String,
    val minOrderSize: BigDecimal,
    val maxOrderSize: BigDecimal? = null,
    val tickSize: BigDecimal,
    val lotSize: BigDecimal,
    val maxLeverage: Int = 1,
    val makerFee: BigDecimal = BigDecimal("0.001"),
    val takerFee: BigDecimal = BigDecimal("0.001"),
    val scalpingEnabled: Boolean = true,
    val marginEnabled: Boolean = false,
    val tradingHours: TradingHours? = null,
    val status: AssetStatus = AssetStatus.TRADING,
    val lastUpdated: Instant = Instant.now()
) {
    /**
     * Effective trading hours - uses default for asset type if not specified.
     */
    val effectiveTradingHours: TradingHours
        get() = tradingHours ?: when (assetType) {
            AssetType.CRYPTO_SPOT, AssetType.CRYPTO_FUTURES -> TradingHours.CRYPTO_24_7
            AssetType.FOREX -> TradingHours.FOREX_STANDARD
            AssetType.STOCKS, AssetType.ETFS -> TradingHours.US_EQUITIES
            AssetType.COMMODITIES, AssetType.OPTIONS -> TradingHours.CME_FUTURES
            else -> TradingHours.CRYPTO_24_7
        }
    
    /**
     * Check if market is currently open for trading.
     */
    fun isMarketOpen(): Boolean =
        status == AssetStatus.TRADING && effectiveTradingHours.isOpen()
    
    /**
     * Round a price to valid tick size.
     */
    fun roundPrice(price: BigDecimal): BigDecimal =
        price.divide(tickSize, 0, RoundingMode.HALF_UP).multiply(tickSize)
    
    /**
     * Round a quantity to valid lot size.
     */
    fun roundQuantity(quantity: BigDecimal): BigDecimal =
        quantity.divide(lotSize, 0, RoundingMode.DOWN).multiply(lotSize)
    
    /**
     * Validate an order quantity against constraints.
     * @return Pair of (isValid, errorMessage)
     */
    fun validateQuantity(quantity: BigDecimal): Pair<Boolean, String?> {
        if (quantity < minOrderSize) {
            return false to "Quantity $quantity below minimum $minOrderSize"
        }
        
        maxOrderSize?.let { max ->
            if (quantity > max) {
                return false to "Quantity $quantity exceeds maximum $max"
            }
        }
        
        val remainder = quantity.remainder(lotSize)
        if (remainder != BigDecimal.ZERO) {
            return false to "Quantity $quantity not a multiple of lot size $lotSize"
        }
        
        return true to null
    }
    
    /**
     * Calculate notional value of a position.
     */
    fun calculateNotional(quantity: BigDecimal, price: BigDecimal): BigDecimal =
        quantity.multiply(price)
    
    /**
     * Calculate required margin for a leveraged position.
     */
    fun calculateMargin(quantity: BigDecimal, price: BigDecimal, leverage: Int): BigDecimal {
        require(leverage in 1..maxLeverage) { 
            "Leverage $leverage exceeds max $maxLeverage for $symbol" 
        }
        val notional = calculateNotional(quantity, price)
        return notional.divide(BigDecimal(leverage), 8, RoundingMode.CEILING)
    }
    
    /**
     * Estimate trading fee for an order.
     * @param isMaker True if limit order likely to be maker
     */
    fun estimateFee(quantity: BigDecimal, price: BigDecimal, isMaker: Boolean): BigDecimal {
        val notional = calculateNotional(quantity, price)
        val feeRate = if (isMaker) makerFee else takerFee
        return notional.multiply(feeRate)
    }
    
    /**
     * Get display name with exchange suffix.
     */
    fun displayName(): String = "$symbol ($exchange)"
    
    /**
     * Check if this asset matches a search query.
     */
    fun matchesSearch(query: String): Boolean {
        val q = query.uppercase()
        return symbol.uppercase().contains(q) ||
               baseAsset.uppercase().contains(q) ||
               quoteAsset.uppercase().contains(q) ||
               category.displayName.uppercase().contains(q)
    }
    
    companion object {
        /**
         * Create a basic crypto spot asset with common defaults.
         */
        fun cryptoSpot(
            symbol: String,
            baseAsset: String,
            quoteAsset: String = "USDT",
            category: AssetCategory = AssetCategory.MAJOR_CRYPTO,
            exchange: String = "Binance",
            minOrderSize: BigDecimal = BigDecimal("0.0001"),
            tickSize: BigDecimal = BigDecimal("0.01"),
            lotSize: BigDecimal = BigDecimal("0.0001")
        ) = TradableAsset(
            symbol = symbol,
            baseAsset = baseAsset,
            quoteAsset = quoteAsset,
            assetType = AssetType.CRYPTO_SPOT,
            category = category,
            exchange = exchange,
            minOrderSize = minOrderSize,
            tickSize = tickSize,
            lotSize = lotSize,
            maxLeverage = 1,
            makerFee = BigDecimal("0.001"),
            takerFee = BigDecimal("0.001"),
            scalpingEnabled = true,
            marginEnabled = false
        )
        
        /**
         * Create a crypto perpetual futures asset.
         */
        fun cryptoPerp(
            symbol: String,
            baseAsset: String,
            quoteAsset: String = "USDT",
            exchange: String = "Binance",
            maxLeverage: Int = 20,
            minOrderSize: BigDecimal = BigDecimal("0.001"),
            tickSize: BigDecimal = BigDecimal("0.01"),
            lotSize: BigDecimal = BigDecimal("0.001")
        ) = TradableAsset(
            symbol = symbol,
            baseAsset = baseAsset,
            quoteAsset = quoteAsset,
            assetType = AssetType.CRYPTO_FUTURES,
            category = AssetCategory.CRYPTO_PERP,
            exchange = exchange,
            minOrderSize = minOrderSize,
            tickSize = tickSize,
            lotSize = lotSize,
            maxLeverage = maxLeverage,
            makerFee = BigDecimal("0.0002"),
            takerFee = BigDecimal("0.0004"),
            scalpingEnabled = true,
            marginEnabled = true
        )
        
        /**
         * Create a forex pair asset.
         */
        fun forexPair(
            symbol: String,
            baseAsset: String,
            quoteAsset: String,
            category: AssetCategory = AssetCategory.FOREX_MAJOR,
            exchange: String = "Uphold"
        ) = TradableAsset(
            symbol = symbol,
            baseAsset = baseAsset,
            quoteAsset = quoteAsset,
            assetType = AssetType.FOREX,
            category = category,
            exchange = exchange,
            minOrderSize = BigDecimal("1000"),      // 1 micro lot
            tickSize = BigDecimal("0.00001"),       // 1 pipette
            lotSize = BigDecimal("1000"),
            maxLeverage = 50,
            makerFee = BigDecimal("0.00003"),       // ~0.3 pips spread
            takerFee = BigDecimal("0.00003"),
            scalpingEnabled = true,
            marginEnabled = true,
            tradingHours = TradingHours.FOREX_STANDARD
        )
        
        /**
         * Create a precious metal asset (XAU, XAG, XPT, XPD).
         * Uses special lot sizes matching industry standards.
         */
        fun preciousMetal(
            symbol: String,
            baseAsset: String,
            quoteAsset: String = "USD",
            exchange: String = "Uphold"
        ): TradableAsset {
            // Industry standard lot sizes for metals
            val (minOrder, tick, lot) = when (baseAsset.uppercase()) {
                "XAU" -> Triple(BigDecimal("0.01"), BigDecimal("0.01"), BigDecimal("0.01"))      // Gold: 0.01 oz min
                "XAG" -> Triple(BigDecimal("1"), BigDecimal("0.001"), BigDecimal("1"))           // Silver: 1 oz min
                "XPT" -> Triple(BigDecimal("0.1"), BigDecimal("0.01"), BigDecimal("0.1"))        // Platinum: 0.1 oz min
                "XPD" -> Triple(BigDecimal("0.1"), BigDecimal("0.01"), BigDecimal("0.1"))        // Palladium: 0.1 oz min
                else -> Triple(BigDecimal("0.1"), BigDecimal("0.01"), BigDecimal("0.1"))
            }
            
            return TradableAsset(
                symbol = symbol,
                baseAsset = baseAsset,
                quoteAsset = quoteAsset,
                assetType = AssetType.COMMODITIES,
                category = AssetCategory.PRECIOUS_METALS,
                exchange = exchange,
                minOrderSize = minOrder,
                tickSize = tick,
                lotSize = lot,
                maxLeverage = 20,
                makerFee = BigDecimal("0.0005"),
                takerFee = BigDecimal("0.0005"),
                scalpingEnabled = true,
                marginEnabled = true,
                tradingHours = TradingHours.FOREX_STANDARD  // Metals trade forex hours
            )
        }
        
        /**
         * Create an industrial metal asset (XCU, XAL, XNI, XZN, XPB, XSN).
         */
        fun industrialMetal(
            symbol: String,
            baseAsset: String,
            quoteAsset: String = "USD",
            exchange: String = "Uphold"
        ) = TradableAsset(
            symbol = symbol,
            baseAsset = baseAsset,
            quoteAsset = quoteAsset,
            assetType = AssetType.COMMODITIES,
            category = AssetCategory.INDUSTRIAL_METALS,
            exchange = exchange,
            minOrderSize = BigDecimal("1"),
            tickSize = BigDecimal("0.01"),
            lotSize = BigDecimal("1"),
            maxLeverage = 10,
            makerFee = BigDecimal("0.0005"),
            takerFee = BigDecimal("0.0005"),
            scalpingEnabled = true,
            marginEnabled = true,
            tradingHours = TradingHours.CME_FUTURES
        )
        
        /**
         * Create an energy commodity asset (XTI, XBR, XNG).
         */
        fun energyCommodity(
            symbol: String,
            baseAsset: String,
            quoteAsset: String = "USD",
            exchange: String = "Uphold"
        ) = TradableAsset(
            symbol = symbol,
            baseAsset = baseAsset,
            quoteAsset = quoteAsset,
            assetType = AssetType.COMMODITIES,
            category = AssetCategory.ENERGY,
            exchange = exchange,
            minOrderSize = BigDecimal("1"),
            tickSize = BigDecimal("0.01"),
            lotSize = BigDecimal("1"),
            maxLeverage = 10,
            makerFee = BigDecimal("0.0005"),
            takerFee = BigDecimal("0.0005"),
            scalpingEnabled = true,
            marginEnabled = true,
            tradingHours = TradingHours.CME_FUTURES
        )
        
        /**
         * Create an agricultural commodity asset.
         */
        fun agriCommodity(
            symbol: String,
            baseAsset: String,
            quoteAsset: String = "USD",
            category: AssetCategory = AssetCategory.AGRICULTURE,
            exchange: String = "CME"
        ) = TradableAsset(
            symbol = symbol,
            baseAsset = baseAsset,
            quoteAsset = quoteAsset,
            assetType = AssetType.COMMODITIES,
            category = category,
            exchange = exchange,
            minOrderSize = BigDecimal("1"),
            tickSize = BigDecimal("0.01"),
            lotSize = BigDecimal("1"),
            maxLeverage = 10,
            makerFee = BigDecimal("0.0005"),
            takerFee = BigDecimal("0.0005"),
            scalpingEnabled = false,  // Agri commodities less suitable for scalping
            marginEnabled = true,
            tradingHours = TradingHours.CME_FUTURES
        )
        
        /**
         * Create an ETF asset.
         */
        fun etf(
            symbol: String,
            name: String,
            category: AssetCategory = AssetCategory.INDEX_MAJOR,
            exchange: String = "NYSE"
        ) = TradableAsset(
            symbol = "$symbol/USD",
            baseAsset = symbol,
            quoteAsset = "USD",
            assetType = AssetType.ETFS,
            category = category,
            exchange = exchange,
            minOrderSize = BigDecimal("1"),
            tickSize = BigDecimal("0.01"),
            lotSize = BigDecimal("1"),
            maxLeverage = 2,
            makerFee = BigDecimal("0.0001"),
            takerFee = BigDecimal("0.0001"),
            scalpingEnabled = true,
            marginEnabled = false,
            tradingHours = TradingHours.US_EQUITIES
        )
        
        /**
         * Create a gold-backed crypto asset (PAXG, XAUT).
         */
        fun goldBackedCrypto(
            symbol: String,
            baseAsset: String,
            quoteAsset: String = "USD",
            exchange: String = "Kraken"
        ) = TradableAsset(
            symbol = symbol,
            baseAsset = baseAsset,
            quoteAsset = quoteAsset,
            assetType = AssetType.CRYPTO_SPOT,
            category = AssetCategory.GOLD_BACKED_CRYPTO,
            exchange = exchange,
            minOrderSize = BigDecimal("0.001"),
            tickSize = BigDecimal("0.01"),
            lotSize = BigDecimal("0.001"),
            maxLeverage = 1,
            makerFee = BigDecimal("0.001"),
            takerFee = BigDecimal("0.001"),
            scalpingEnabled = true,
            marginEnabled = false
        )
    }
}

/**
 * Trading status for an asset.
 */
enum class AssetStatus {
    /** Normal trading */
    TRADING,
    
    /** Trading temporarily halted */
    HALTED,
    
    /** Asset delisted, close-only mode */
    CLOSE_ONLY,
    
    /** Pre-market or upcoming listing */
    PRE_TRADING,
    
    /** Asset scheduled for removal */
    DELISTING,
    
    /** Unknown or error state */
    UNKNOWN
}

/**
 * Filter criteria for querying assets.
 */
data class AssetFilter(
    val assetTypes: Set<AssetType>? = null,
    val categories: Set<AssetCategory>? = null,
    val exchanges: Set<String>? = null,
    val quoteAssets: Set<String>? = null,
    val scalpingOnly: Boolean = false,
    val marginOnly: Boolean = false,
    val maxRiskTier: Int? = null,
    val minLeverage: Int? = null,
    val statusFilter: Set<AssetStatus> = setOf(AssetStatus.TRADING),
    val searchQuery: String? = null
) {
    /**
     * Check if an asset matches this filter.
     */
    fun matches(asset: TradableAsset): Boolean {
        assetTypes?.let { if (asset.assetType !in it) return false }
        categories?.let { if (asset.category !in it) return false }
        exchanges?.let { if (asset.exchange !in it) return false }
        quoteAssets?.let { if (asset.quoteAsset !in it) return false }
        
        if (scalpingOnly && !asset.scalpingEnabled) return false
        if (marginOnly && !asset.marginEnabled) return false
        
        maxRiskTier?.let { 
            if (asset.category.riskTier > it) return false 
        }
        
        minLeverage?.let { 
            if (asset.maxLeverage < it) return false 
        }
        
        if (asset.status !in statusFilter) return false
        
        searchQuery?.let { 
            if (!asset.matchesSearch(it)) return false 
        }
        
        return true
    }
    
    companion object {
        /** All crypto spot assets */
        val CRYPTO_SPOT = AssetFilter(assetTypes = setOf(AssetType.CRYPTO_SPOT))
        
        /** All leveraged perpetuals */
        val CRYPTO_PERPS = AssetFilter(
            assetTypes = setOf(AssetType.CRYPTO_FUTURES),
            marginOnly = true
        )
        
        /** Conservative assets (risk tier 1-2) */
        val CONSERVATIVE = AssetFilter(maxRiskTier = 2)
        
        /** Scalping-enabled only */
        val SCALPING = AssetFilter(scalpingOnly = true)
        
        /** USDT quote pairs only */
        val USDT_PAIRS = AssetFilter(quoteAssets = setOf("USDT"))
        
        /** USD quote pairs (crypto + forex) */
        val USD_PAIRS = AssetFilter(quoteAssets = setOf("USD", "USDT", "USDC"))
        
        /** All forex pairs */
        val FOREX = AssetFilter(assetTypes = setOf(AssetType.FOREX))
        
        /** Forex majors only (lower spreads, higher liquidity) */
        val FOREX_MAJORS = AssetFilter(
            assetTypes = setOf(AssetType.FOREX),
            categories = setOf(AssetCategory.FOREX_MAJOR)
        )
    }
}