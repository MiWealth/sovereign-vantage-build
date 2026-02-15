/**
 * Kraken Asset Loader - Kraken Exchange Integration (PQC SECURED)
 * 
 * Sovereign Vantage: Arthur Edition V5.5.33
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * Fetches tradable asset information from Kraken's public API.
 * 
 * POST-QUANTUM FORTRESS SECURITY:
 * - HybridSecureHttpClient with Kyber-1024 + TLS 1.3
 * - Dilithium-5 signed request audit trail
 * - Exchange-specific PQC configuration (MAXIMUM security)
 * 
 * API ENDPOINTS:
 * - Asset Pairs: GET https://api.kraken.com/0/public/AssetPairs
 * - Assets:      GET https://api.kraken.com/0/public/Assets
 * 
 * RATE LIMITS:
 * - Public endpoints: 1 request per second recommended
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 */
package com.miwealth.sovereignvantage.core.trading.assets

import com.google.gson.JsonObject
import com.miwealth.sovereignvantage.core.security.pqc.hybrid.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.Instant

/**
 * Asset loader for Kraken exchange.
 * Uses HybridSecureHttpClient with PQC protection.
 */
class KrakenAssetLoader(
    secureClient: HybridSecureHttpClient = HybridSecureHttpClient.create(
        HybridPQCConfig.forExchange("kraken")
    )
) : BaseAssetLoader(secureClient) {
    
    override val exchangeName: String = "Kraken"
    override val baseUrl: String = "https://api.kraken.com"
    
    // Kraken rate limit: conservative
    override val rateLimitCalls: Int = 1
    override val rateLimitWindowMs: Long = 1000L
    
    // Kraken uses non-standard symbol naming
    private val symbolNormalization = mapOf(
        "XBT" to "BTC",
        "XDG" to "DOGE"
    )
    
    /**
     * Fetch all tradable assets from Kraken.
     */
    override suspend fun fetchAllAssets(): List<TradableAsset> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/0/public/AssetPairs"
        val json = fetchJson(url)
        
        if (json == null) {
            android.util.Log.e(TAG, "Failed to fetch Kraken asset pairs")
            return@withContext emptyList()
        }
        
        // Check for errors
        val errors = json.getAsJsonArray("error")
        if (errors != null && errors.size() > 0) {
            android.util.Log.e(TAG, "Kraken API error: ${errors.joinToString()}")
            return@withContext emptyList()
        }
        
        val result = json.getAsJsonObject("result") ?: return@withContext emptyList()
        
        val assets = result.entrySet().mapNotNull { (pairName, pairData) ->
            parseAssetPair(pairName, pairData.asJsonObject)
        }
        
        // Update cache
        assets.forEach { asset ->
            assetCache[asset.symbol] = asset
        }
        lastLoadTime = Instant.now()
        
        android.util.Log.i(TAG, "Loaded ${assets.size} assets from Kraken")
        
        assets
    }
    
    /**
     * Fetch a single asset by symbol.
     */
    override suspend fun fetchAsset(symbol: String): TradableAsset? {
        // Check cache first
        assetCache[symbol]?.let { return it }
        
        // If cache is stale, refresh all (Kraken doesn't support single-pair queries efficiently)
        if (isCacheStale()) {
            fetchAllAssets()
        }
        
        return assetCache[symbol]
    }
    
    /**
     * Parse a Kraken asset pair.
     */
    private fun parseAssetPair(pairName: String, pairJson: JsonObject): TradableAsset? {
        try {
            // Skip darkpool pairs
            if (pairName.endsWith(".d")) return null
            
            val status = pairJson.get("status")?.asString ?: "online"
            if (status != "online") return null
            
            // Get the websocket name which is cleaner
            val wsName = pairJson.get("wsname")?.asString ?: return null
            
            // Parse base and quote from wsname (e.g., "XBT/USD")
            val parts = wsName.split("/")
            if (parts.size != 2) return null
            
            val rawBase = parts[0]
            val rawQuote = parts[1]
            
            // Normalize Kraken's non-standard symbols
            val baseAsset = normalizeKrakenSymbol(rawBase)
            val quoteAsset = normalizeKrakenSymbol(rawQuote)
            
            // Only include supported quote currencies
            if (quoteAsset !in SUPPORTED_QUOTES) return null
            
            // Parse trading constraints
            val pairDecimals = pairJson.get("pair_decimals")?.asInt ?: 8
            val lotDecimals = pairJson.get("lot_decimals")?.asInt ?: 8
            val orderMin = pairJson.get("ordermin")?.asString
            val costMin = pairJson.get("costmin")?.asString
            
            // Calculate tick and lot sizes
            val tickSize = BigDecimal.ONE.movePointLeft(pairDecimals)
            val lotSize = BigDecimal.ONE.movePointLeft(lotDecimals)
            val minOrderSize = parseDecimal(orderMin, BigDecimal("0.0001"))
            
            // Check margin availability
            val leverageBuy = pairJson.getAsJsonArray("leverage_buy")
            val maxLeverage = if (leverageBuy != null && leverageBuy.size() > 0) {
                leverageBuy.maxOfOrNull { it.asInt } ?: 1
            } else 1
            
            // Build exchange info for category mapping
            val exchangeInfo = ExchangeAssetInfo(
                symbol = wsName,
                baseAsset = baseAsset,
                quoteAsset = quoteAsset,
                status = status,
                isSpot = true,
                isMargin = maxLeverage > 1
            )
            
            return TradableAsset(
                symbol = "$baseAsset/$quoteAsset",
                baseAsset = baseAsset,
                quoteAsset = quoteAsset,
                assetType = AssetType.CRYPTO_SPOT,
                category = mapToCategory(exchangeInfo),
                exchange = exchangeName,
                minOrderSize = minOrderSize,
                maxOrderSize = null,
                tickSize = tickSize,
                lotSize = lotSize,
                maxLeverage = maxLeverage,
                makerFee = BigDecimal("0.0016"),  // Kraken maker fee
                takerFee = BigDecimal("0.0026"),  // Kraken taker fee
                scalpingEnabled = true,
                marginEnabled = maxLeverage > 1,
                tradingHours = TradingHours.CRYPTO_24_7,
                status = AssetStatus.TRADING,
                lastUpdated = Instant.now()
            )
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to parse Kraken pair $pairName: ${e.message}")
            return null
        }
    }
    
    /**
     * Normalize Kraken's non-standard symbol names.
     */
    private fun normalizeKrakenSymbol(symbol: String): String {
        // Remove leading X or Z (Kraken's legacy prefixes)
        val cleaned = when {
            symbol.startsWith("X") && symbol.length == 4 -> symbol.substring(1)
            symbol.startsWith("Z") && symbol.length == 4 -> symbol.substring(1)
            else -> symbol
        }
        
        return symbolNormalization[cleaned] ?: cleaned
    }
    
    /**
     * Map Kraken asset info to our category system.
     */
    override fun mapToCategory(exchangeInfo: ExchangeAssetInfo): AssetCategory {
        return inferCategory(exchangeInfo)
    }
    
    /**
     * Convert Kraken symbol to standard format.
     */
    override fun normalizeSymbol(exchangeSymbol: String): String {
        // Kraken websocket names are already in BASE/QUOTE format
        val parts = exchangeSymbol.split("/")
        if (parts.size == 2) {
            val base = normalizeKrakenSymbol(parts[0])
            val quote = normalizeKrakenSymbol(parts[1])
            return "$base/$quote"
        }
        return exchangeSymbol
    }
    
    companion object {
        private const val TAG = "KrakenAssetLoader"
        
        private val SUPPORTED_QUOTES = setOf(
            "USD", "EUR", "GBP", "CAD", "AUD", "JPY", "CHF",
            "USDT", "USDC", "DAI", "BTC", "ETH"
        )
    }
}
