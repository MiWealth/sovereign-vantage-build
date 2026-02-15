/**
 * Asset Loader - Exchange Asset Information Loading (PQC SECURED)
 * 
 * Sovereign Vantage: Arthur Edition V5.5.33
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * Defines the interface and base implementation for loading tradable asset
 * information from exchanges. Each exchange has its own loader implementation.
 * 
 * POST-QUANTUM FORTRESS SECURITY:
 * - HybridSecureHttpClient with Kyber-1024 + TLS 1.3
 * - Dilithium-5 signed request audit trail
 * - AES-256-GCM payload encryption (hybrid mode)
 * - Certificate pinning for exchange APIs
 * 
 * DESIGN RATIONALE:
 * - Interface allows multiple exchange implementations
 * - Category mapping centralizes exchange-specific classifications
 * - Caching reduces API calls and respects rate limits
 * - Coroutine-based for non-blocking network operations
 * 
 * USAGE:
 *   val loader = BinanceAssetLoader()
 *   val assets = loader.fetchAllAssets()
 *   AssetRegistry.registerAll(assets)
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 */
package com.miwealth.sovereignvantage.core.trading.assets

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.miwealth.sovereignvantage.core.security.pqc.hybrid.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Interface for loading asset information from exchanges.
 */
interface AssetLoader {
    
    /** Exchange identifier */
    val exchangeName: String
    
    /** Base URL for the exchange API */
    val baseUrl: String
    
    /**
     * Fetch all tradable assets from the exchange.
     * @return List of tradable assets with specifications
     */
    suspend fun fetchAllAssets(): List<TradableAsset>
    
    /**
     * Fetch a single asset by symbol.
     * @param symbol The trading pair symbol (e.g., "BTC/USDT")
     * @return The asset specification, or null if not found
     */
    suspend fun fetchAsset(symbol: String): TradableAsset?
    
    /**
     * Map exchange-specific category/type to our AssetCategory.
     * @param exchangeInfo Exchange-specific category information
     * @return Appropriate AssetCategory
     */
    fun mapToCategory(exchangeInfo: ExchangeAssetInfo): AssetCategory
    
    /**
     * Convert exchange symbol format to our standard format.
     * @param exchangeSymbol Symbol in exchange format (e.g., "BTCUSDT")
     * @return Symbol in our format (e.g., "BTC/USDT")
     */
    fun normalizeSymbol(exchangeSymbol: String): String
    
    /**
     * Check if the loader's cache is stale.
     */
    fun isCacheStale(maxAge: Duration = Duration.ofHours(1)): Boolean
}

/**
 * Intermediate data class for exchange-specific asset info before mapping.
 */
data class ExchangeAssetInfo(
    val symbol: String,
    val baseAsset: String,
    val quoteAsset: String,
    val status: String,
    val isSpot: Boolean = true,
    val isMargin: Boolean = false,
    val permissions: List<String> = emptyList(),
    val tags: List<String> = emptyList()
)

/**
 * Result of an asset loading operation.
 */
sealed class AssetLoadResult {
    data class Success(
        val assets: List<TradableAsset>,
        val loadTime: Instant = Instant.now()
    ) : AssetLoadResult()
    
    data class PartialSuccess(
        val assets: List<TradableAsset>,
        val errors: List<String>,
        val loadTime: Instant = Instant.now()
    ) : AssetLoadResult()
    
    data class Error(
        val message: String,
        val exception: Throwable? = null
    ) : AssetLoadResult()
}

/**
 * Abstract base class for asset loaders with common functionality.
 * Now uses HybridSecureHttpClient for PQC protection.
 */
abstract class BaseAssetLoader(
    protected val secureClient: HybridSecureHttpClient = HybridSecureHttpClient.create(
        HybridPQCConfig.default()
    ),
    protected val gson: Gson = Gson()
) : AssetLoader {
    
    /** Cache of loaded assets */
    protected val assetCache = ConcurrentHashMap<String, TradableAsset>()
    
    /** Last successful load time */
    protected var lastLoadTime: Instant? = null
    
    /** Rate limiting */
    protected val callTimestamps = mutableListOf<Long>()
    protected open val rateLimitCalls: Int = 10
    protected open val rateLimitWindowMs: Long = 1000L
    
    override fun isCacheStale(maxAge: Duration): Boolean {
        val last = lastLoadTime ?: return true
        return Instant.now().isAfter(last.plus(maxAge))
    }
    
    /**
     * Get cached assets without network call.
     */
    fun getCachedAssets(): List<TradableAsset> = assetCache.values.toList()
    
    /**
     * Clear the asset cache.
     */
    fun clearCache() {
        assetCache.clear()
        lastLoadTime = null
    }
    
    /**
     * Check if rate limited.
     */
    protected fun isRateLimited(): Boolean {
        val now = System.currentTimeMillis()
        callTimestamps.removeAll { it < now - rateLimitWindowMs }
        return callTimestamps.size >= rateLimitCalls
    }
    
    /**
     * Record an API call for rate limiting.
     */
    protected fun recordApiCall() {
        callTimestamps.add(System.currentTimeMillis())
    }
    
    /**
     * Make a secure GET request and parse JSON response.
     * Uses HybridSecureHttpClient with PQC protection.
     */
    protected suspend fun fetchJson(url: String): JsonObject? = withContext(Dispatchers.IO) {
        if (isRateLimited()) {
            android.util.Log.w(TAG, "Rate limited, waiting...")
            kotlinx.coroutines.delay(rateLimitWindowMs)
        }
        
        recordApiCall()
        
        try {
            // Use PQC-secured HTTP client
            val response = secureClient.secureGet(
                url = url,
                exchangeId = exchangeName.lowercase(),
                headers = mapOf("Accept" to "application/json"),
                authenticated = false  // Public endpoint
            )
            
            if (!response.success) {
                android.util.Log.e(TAG, "HTTP ${response.statusCode}: PQC request failed")
                return@withContext null
            }
            
            val body = response.bodyAsString()
            if (body.isNullOrEmpty()) {
                android.util.Log.e(TAG, "Empty response body")
                return@withContext null
            }
            
            android.util.Log.d(TAG, "PQC-secured fetch complete: ${response.requestId}, " +
                "latency=${response.latencyMs}ms, protected=${response.pqcProtected}")
            
            gson.fromJson(body, JsonObject::class.java)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Fetch error: ${e.message}", e)
            null
        }
    }
    
    /**
     * Get security report for this loader's HTTP client
     */
    fun getSecurityReport(): ClientSecurityReport = secureClient.getSecurityReport()
    
    /**
     * Parse a BigDecimal from JSON, with fallback.
     */
    protected fun parseDecimal(value: String?, fallback: BigDecimal = BigDecimal.ZERO): BigDecimal {
        return try {
            if (value.isNullOrEmpty()) fallback else BigDecimal(value)
        } catch (e: NumberFormatException) {
            fallback
        }
    }
    
    /**
     * Determine category based on common heuristics.
     * Subclasses should override mapToCategory for exchange-specific logic.
     */
    protected fun inferCategory(info: ExchangeAssetInfo): AssetCategory {
        val base = info.baseAsset.uppercase()
        val tags = info.tags.map { it.uppercase() }
        
        // Major crypto
        if (base in MAJOR_CRYPTO_SYMBOLS) return AssetCategory.MAJOR_CRYPTO
        
        // Layer 1 alts
        if (base in LAYER1_SYMBOLS) return AssetCategory.ALT_LAYER1
        
        // DeFi
        if (base in DEFI_SYMBOLS || "DEFI" in tags) return AssetCategory.DEFI
        
        // Layer 2
        if (base in LAYER2_SYMBOLS || "LAYER2" in tags || "L2" in tags) return AssetCategory.LAYER2
        
        // Meme
        if (base in MEME_SYMBOLS || "MEME" in tags) return AssetCategory.MEME
        
        // NFT/Gaming
        if (base in NFT_GAMING_SYMBOLS || "NFT" in tags || "GAMING" in tags) return AssetCategory.NFT_GAMING
        
        // Infrastructure
        if (base in INFRASTRUCTURE_SYMBOLS) return AssetCategory.INFRASTRUCTURE
        
        // Stablecoins
        if (base in STABLECOIN_SYMBOLS || "STABLECOIN" in tags) return AssetCategory.STABLECOIN
        
        // Default to Layer 1 alt for unknown crypto
        return AssetCategory.ALT_LAYER1
    }
    
    companion object {
        private const val TAG = "AssetLoader"
        
        // Symbol classification sets
        val MAJOR_CRYPTO_SYMBOLS = setOf("BTC", "ETH")
        
        val LAYER1_SYMBOLS = setOf(
            "SOL", "BNB", "XRP", "ADA", "AVAX", "DOT", "ATOM", "NEAR",
            "TRX", "TON", "ICP", "APT", "SUI", "SEI", "INJ"
        )
        
        val DEFI_SYMBOLS = setOf(
            "UNI", "AAVE", "CRV", "SUSHI", "COMP", "MKR", "SNX", "YFI",
            "1INCH", "DYDX", "GMX", "LDO", "RPL", "PENDLE", "JUP"
        )
        
        val LAYER2_SYMBOLS = setOf(
            "ARB", "OP", "MATIC", "IMX", "MNT", "STRK", "ZK", "MANTA"
        )
        
        val MEME_SYMBOLS = setOf(
            "DOGE", "SHIB", "PEPE", "FLOKI", "BONK", "WIF", "MEME", "BRETT"
        )
        
        val NFT_GAMING_SYMBOLS = setOf(
            "APE", "BLUR", "GALA", "SAND", "MANA", "AXS", "ENJ", "IMX",
            "PRIME", "PIXEL", "PORTAL", "BIGTIME"
        )
        
        val INFRASTRUCTURE_SYMBOLS = setOf(
            "LINK", "GRT", "FIL", "AR", "RENDER", "THETA", "HNT", "RNDR",
            "OCEAN", "FET", "AGIX", "TAO"
        )
        
        val STABLECOIN_SYMBOLS = setOf(
            "USDT", "USDC", "DAI", "BUSD", "TUSD", "USDP", "FRAX", "USDD"
        )
    }
}

/**
 * Factory for creating exchange-specific asset loaders.
 * All loaders now use HybridSecureHttpClient with PQC protection.
 */
object AssetLoaderFactory {
    
    private val loaders = ConcurrentHashMap<String, AssetLoader>()
    
    /**
     * Get or create a PQC-secured loader for the specified exchange.
     * 
     * @param exchangeName Exchange identifier (binance, kraken, coinbase)
     * @param secureClient Optional custom HybridSecureHttpClient (uses default if null)
     * @return AssetLoader with PQC protection, or null if exchange not supported
     */
    fun getLoader(
        exchangeName: String, 
        secureClient: HybridSecureHttpClient? = null
    ): AssetLoader? {
        return loaders.getOrPut(exchangeName.uppercase()) {
            val client = secureClient ?: HybridSecureHttpClient.create(
                HybridPQCConfig.forExchange(exchangeName)
            )
            when (exchangeName.uppercase()) {
                "BINANCE" -> BinanceAssetLoader(client)
                "KRAKEN" -> KrakenAssetLoader(client)
                "COINBASE" -> CoinbaseAssetLoader(client)
                else -> return null
            }
        }
    }
    
    /**
     * Get all registered loaders.
     */
    fun getAllLoaders(): List<AssetLoader> = loaders.values.toList()
    
    /**
     * Get security reports for all loaders.
     */
    fun getSecurityReports(): Map<String, ClientSecurityReport> {
        return loaders.mapValues { (_, loader) ->
            (loader as? BaseAssetLoader)?.getSecurityReport()
                ?: throw IllegalStateException("Loader is not a BaseAssetLoader")
        }
    }
    
    /**
     * Supported exchanges.
     */
    val supportedExchanges = listOf("BINANCE", "KRAKEN", "COINBASE")
}
