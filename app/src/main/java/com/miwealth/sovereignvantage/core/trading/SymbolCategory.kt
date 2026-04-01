package com.miwealth.sovereignvantage.core.trading

/**
 * Symbol Category Classification & Filtering
 * 
 * Categorizes trading symbols across exchanges for:
 * - UI filtering and organization
 * - Risk profiling
 * - Default exclusions (stable-to-stable)
 * - Auto-enable rules (de-peg detection)
 * 
 * BUILD #364: Multi-exchange symbol support with intelligent filtering
 * 
 * © 2025 MiWealth Pty Ltd - Sovereign Vantage™: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO: Arthur Iain McManus (1966-2025)
 * Dedicated to: Cathryn 💘
 */

enum class SymbolCategory(
    val displayName: String,
    val defaultEnabled: Boolean,
    val riskLevel: RiskLevel
) {
    /**
     * Major cryptocurrencies - BTC, ETH, BNB
     * Always shown, lowest risk profile
     */
    MAJOR_CRYPTO(
        displayName = "Major Crypto",
        defaultEnabled = true,
        riskLevel = RiskLevel.LOW
    ),
    
    /**
     * Top altcoins by market cap (Top 100)
     * Default enabled, moderate risk
     */
    ALTCOINS(
        displayName = "Altcoins",
        defaultEnabled = true,
        riskLevel = RiskLevel.MODERATE
    ),
    
    /**
     * DeFi protocol tokens - UNI, AAVE, COMP, SUSHI
     * Default enabled, moderate-high risk
     */
    DEFI_TOKENS(
        displayName = "DeFi Tokens",
        defaultEnabled = true,
        riskLevel = RiskLevel.MODERATE_HIGH
    ),
    
    /**
     * Meme coins - DOGE, SHIB, PEPE, BONK
     * Default disabled, very high risk
     */
    MEME_COINS(
        displayName = "Meme Coins",
        defaultEnabled = false,
        riskLevel = RiskLevel.VERY_HIGH
    ),
    
    /**
     * Stablecoin pairs - BTC/USDT, ETH/USDC
     * Default enabled, low risk
     */
    STABLECOINS(
        displayName = "Stablecoin Pairs",
        defaultEnabled = true,
        riskLevel = RiskLevel.LOW
    ),
    
    /**
     * Stablecoin-to-stablecoin pairs - USDT/USDC, DAI/USDT
     * DEFAULT DISABLED - only profitable via arbitrage or de-peg events
     * Auto-enables on de-peg >2%
     */
    STABLE_TO_STABLE(
        displayName = "Stable-to-Stable",
        defaultEnabled = false,
        riskLevel = RiskLevel.LOW
    ),
    
    /**
     * Exchange native tokens - BNB, FTT (RIP), KCS, HT
     * Default enabled, moderate risk
     */
    EXCHANGE_TOKENS(
        displayName = "Exchange Tokens",
        defaultEnabled = true,
        riskLevel = RiskLevel.MODERATE
    ),
    
    /**
     * Layer 1 blockchains - SOL, ADA, AVAX, DOT
     * Default enabled, moderate risk
     */
    LAYER1_CHAINS(
        displayName = "Layer 1 Chains",
        defaultEnabled = true,
        riskLevel = RiskLevel.MODERATE
    ),
    
    /**
     * Layer 2 scaling solutions - MATIC, ARB, OP
     * Default enabled, moderate-high risk
     */
    LAYER2_SOLUTIONS(
        displayName = "Layer 2 Solutions",
        defaultEnabled = true,
        riskLevel = RiskLevel.MODERATE_HIGH
    )
}

enum class RiskLevel {
    LOW,
    MODERATE,
    MODERATE_HIGH,
    HIGH,
    VERY_HIGH
}

/**
 * Symbol filter configuration
 */
data class SymbolFilterConfig(
    val enabledCategories: Set<SymbolCategory> = SymbolCategory.values()
        .filter { it.defaultEnabled }
        .toSet(),
    
    // Advanced toggle: Enable stable-to-stable pairs
    val enableStableToStable: Boolean = false,
    
    // Auto-enable stable pairs on de-peg
    val autoEnableOnDepeg: Boolean = true,
    val depegThresholdPercent: Double = 2.0,
    
    // Minimum market cap filter (USD)
    val minMarketCap: Double? = null,
    
    // Minimum 24h volume filter (USD)
    val min24hVolume: Double? = null,
    
    // Exchange-specific filters
    val enabledExchanges: Set<String> = setOf("binance", "kraken", "coinbase")
)

/**
 * Stablecoin definitions for de-peg detection
 */
object Stablecoins {
    val MAJOR = setOf(
        "USDT", "USDC", "BUSD", "DAI", "TUSD", "USDD"
    )
    
    val ALL = MAJOR + setOf(
        "FRAX", "USDP", "GUSD", "LUSD", "SUSD", "UST" // UST = dead, keep for historical detection
    )
    
    /**
     * Check if a symbol is a stablecoin
     */
    fun isStablecoin(symbol: String): Boolean {
        return ALL.any { stable -> symbol.contains(stable, ignoreCase = true) }
    }
    
    /**
     * Check if a trading pair is stable-to-stable
     * Examples: USDT/USDC, DAI/USDT, BUSD/USDC
     */
    fun isStableToStable(tradingPair: String): Boolean {
        val parts = tradingPair.split("/", "-", "_")
        if (parts.size != 2) return false
        
        val base = parts[0].uppercase()
        val quote = parts[1].uppercase()
        
        return isStablecoin(base) && isStablecoin(quote)
    }
    
    /**
     * Extract stablecoin from trading pair (if any)
     * Examples: "BTC/USDT" -> "USDT", "ETH/USD" -> null
     */
    fun extractStablecoin(tradingPair: String): String? {
        val parts = tradingPair.split("/", "-", "_")
        if (parts.size != 2) return null
        
        val quote = parts[1].uppercase()
        return if (isStablecoin(quote)) quote else null
    }
}

/**
 * Symbol categorizer - classifies symbols into categories
 */
object SymbolCategorizer {
    
    // Major crypto symbols
    private val MAJOR_CRYPTO = setOf(
        "BTC", "ETH", "BNB"
    )
    
    // Exchange native tokens
    private val EXCHANGE_TOKENS = setOf(
        "BNB", "FTT", "KCS", "HT", "OKB", "CRO", "LEO", "GT"
    )
    
    // DeFi tokens
    private val DEFI_TOKENS = setOf(
        "UNI", "AAVE", "COMP", "SUSHI", "CRV", "BAL", "SNX", "MKR",
        "YFI", "1INCH", "LDO", "RPL", "FXS"
    )
    
    // Meme coins
    private val MEME_COINS = setOf(
        "DOGE", "SHIB", "PEPE", "BONK", "FLOKI", "ELON", "BABYDOGE",
        "WIF", "MEME", "LADYS", "TURBO"
    )
    
    // Layer 1 chains
    private val LAYER1_CHAINS = setOf(
        "SOL", "ADA", "AVAX", "DOT", "ATOM", "ALGO", "XTZ", "EOS",
        "TRX", "XLM", "NEAR", "FTM", "APT", "SUI", "SEI"
    )
    
    // Layer 2 solutions
    private val LAYER2_SOLUTIONS = setOf(
        "MATIC", "ARB", "OP", "IMX", "LRC", "METIS", "BOBA"
    )
    
    /**
     * Categorize a trading symbol
     * 
     * @param symbol Trading pair (e.g. "BTC/USDT", "ETH/USDC")
     * @return SymbolCategory classification
     */
    fun categorize(symbol: String): SymbolCategory {
        // Extract base asset
        val base = symbol.split("/", "-", "_").firstOrNull()?.uppercase() ?: return SymbolCategory.ALTCOINS
        
        // Check stable-to-stable first
        if (Stablecoins.isStableToStable(symbol)) {
            return SymbolCategory.STABLE_TO_STABLE
        }
        
        // Categorize by base asset
        return when {
            base in MAJOR_CRYPTO -> SymbolCategory.MAJOR_CRYPTO
            base in EXCHANGE_TOKENS -> SymbolCategory.EXCHANGE_TOKENS
            base in DEFI_TOKENS -> SymbolCategory.DEFI_TOKENS
            base in MEME_COINS -> SymbolCategory.MEME_COINS
            base in LAYER1_CHAINS -> SymbolCategory.LAYER1_CHAINS
            base in LAYER2_SOLUTIONS -> SymbolCategory.LAYER2_SOLUTIONS
            Stablecoins.isStablecoin(base) -> SymbolCategory.STABLECOINS
            else -> SymbolCategory.ALTCOINS // Default to altcoins
        }
    }
    
    /**
     * Check if symbol should be shown based on filter config
     */
    fun shouldShow(symbol: String, config: SymbolFilterConfig): Boolean {
        val category = categorize(symbol)
        
        // Special handling for stable-to-stable
        if (category == SymbolCategory.STABLE_TO_STABLE) {
            return config.enableStableToStable
        }
        
        return category in config.enabledCategories
    }
}

/**
 * De-peg detector for automatic stable-to-stable pair enabling
 */
object DepegDetector {
    
    // Target peg price (USD)
    private const val TARGET_PEG = 1.0
    
    /**
     * Check if a stablecoin is de-pegged
     * 
     * @param symbol Stablecoin symbol (e.g. "USDC", "DAI")
     * @param currentPrice Current market price in USD
     * @param thresholdPercent De-peg threshold (default 2%)
     * @return true if de-pegged beyond threshold
     */
    fun isDepegged(
        symbol: String,
        currentPrice: Double,
        thresholdPercent: Double = 2.0
    ): Boolean {
        if (!Stablecoins.isStablecoin(symbol)) return false
        
        val deviationPercent = kotlin.math.abs((currentPrice - TARGET_PEG) / TARGET_PEG) * 100.0
        return deviationPercent >= thresholdPercent
    }
    
    /**
     * Calculate de-peg severity
     * 
     * @return De-peg percentage (e.g. 5.2 for 5.2% deviation from $1.00)
     */
    fun getDepegPercent(currentPrice: Double): Double {
        return kotlin.math.abs((currentPrice - TARGET_PEG) / TARGET_PEG) * 100.0
    }
    
    /**
     * Get trading opportunity signal from de-peg
     * 
     * @return Pair of (direction, confidence)
     *         Direction: "SHORT" if above peg, "LONG" if below peg
     *         Confidence: 0-100 based on de-peg severity
     */
    fun getDepegSignal(symbol: String, currentPrice: Double): Pair<String, Double>? {
        if (!isDepegged(symbol, currentPrice)) return null
        
        val depegPercent = getDepegPercent(currentPrice)
        val direction = if (currentPrice > TARGET_PEG) "SHORT" else "LONG"
        
        // Confidence scales with de-peg severity
        // 2% de-peg = 50% confidence
        // 5% de-peg = 80% confidence
        // 10% de-peg = 95% confidence
        val confidence = kotlin.math.min(95.0, (depegPercent / 10.0) * 95.0)
        
        return Pair(direction, confidence)
    }
}
