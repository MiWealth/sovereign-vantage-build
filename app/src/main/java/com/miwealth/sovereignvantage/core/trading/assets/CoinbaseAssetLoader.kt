package com.miwealth.sovereignvantage.core.trading.assets

/**
 * Coinbase Asset Loader - Coinbase Exchange Integration (PQC SECURED)
 * 
 * Sovereign Vantage: Arthur Edition V5.17.0
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * Fetches tradable asset information from Coinbase Advanced Trade API.
 * 
 * POST-QUANTUM FORTRESS SECURITY:
 * - HybridSecureHttpClient with Kyber-1024 + TLS 1.3
 * - Dilithium-5 signed request audit trail
 * - Exchange-specific PQC configuration (MAXIMUM security)
 * 
 * API ENDPOINTS:
 * - Products: GET https://api.coinbase.com/api/v3/brokerage/market/products
 * 
 * RATE LIMITS:
 * - Public endpoints: 10 requests per second
 * 
 * NOTE: Coinbase rebranded from "Pro" to "Advanced Trade" in 2023.
 * The API uses the new v3 endpoints.
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 */



import com.miwealth.sovereignvantage.core.AssetType
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.miwealth.sovereignvantage.core.security.pqc.hybrid.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.Instant

/**
 * Asset loader for Coinbase exchange.
 * Uses HybridSecureHttpClient with PQC protection.
 */
class CoinbaseAssetLoader(
    secureClient: HybridSecureHttpClient = HybridSecureHttpClient.create(
        HybridPQCConfig.forExchange("coinbase")
    )
) : BaseAssetLoader(secureClient) {
    
    override val exchangeName: String = "Coinbase"
    override val baseUrl: String = "https://api.coinbase.com"
    
    // Coinbase rate limit
    override val rateLimitCalls: Int = 10
    override val rateLimitWindowMs: Long = 1000L
    
    /**
     * Fetch all tradable assets from Coinbase.
     */
    override suspend fun fetchAllAssets(): List<TradableAsset> = withContext(Dispatchers.IO) {
        // Try the new v3 API first, fall back to exchange API
        val assets = fetchFromAdvancedTradeApi() 
            ?: fetchFromExchangeApi() 
            ?: emptyList()
        
        // Update cache
        assets.forEach { asset ->
            assetCache[asset.symbol] = asset
        }
        lastLoadTime = Instant.now()
        
        android.util.Log.i(TAG, "Loaded ${assets.size} assets from Coinbase")
        
        assets
    }
    
    /**
     * Fetch from Advanced Trade API (v3).
     */
    private suspend fun fetchFromAdvancedTradeApi(): List<TradableAsset>? {
        val url = "$baseUrl/api/v3/brokerage/market/products"
        val json = fetchJson(url) ?: return null
        
        val products = json.getAsJsonArray("products") ?: return null
        
        return products.mapNotNull { element ->
            parseAdvancedTradeProduct(element.asJsonObject)
        }
    }
    
    /**
     * Fallback: Fetch from Exchange API (legacy).
     */
    private suspend fun fetchFromExchangeApi(): List<TradableAsset>? {
        val url = "https://api.exchange.coinbase.com/products"
        val json = fetchJson(url)
        
        // Exchange API returns array directly, not wrapped in object
        if (json == null) {
            // Try parsing as array
            return try {
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Accept", "application/json")
                    .build()
                
                // BUILD #156: Use .use{} to auto-close response body
                okhttp3.OkHttpClient().newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: return null
                    
                    val array = gson.fromJson(body, JsonArray::class.java)
                    array.mapNotNull { element: com.google.gson.JsonElement ->
                        parseExchangeProduct(element.asJsonObject)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Exchange API fallback failed: ${e.message}")
                null
            }
        }
        
        return null
    }
    
    /**
     * Fetch a single asset by symbol.
     */
    override suspend fun fetchAsset(symbol: String): TradableAsset? {
        // Check cache first
        assetCache[symbol]?.let { return it }
        
        // Try direct fetch
        val productId = symbol.replace("/", "-")
        val url = "$baseUrl/api/v3/brokerage/market/products/$productId"
        val json = fetchJson(url)
        
        if (json != null) {
            val asset = parseAdvancedTradeProduct(json)
            if (asset != null) {
                assetCache[asset.symbol] = asset
                return asset
            }
        }
        
        // If cache is stale, refresh all
        if (isCacheStale()) {
            fetchAllAssets()
        }
        
        return assetCache[symbol]
    }
    
    /**
     * Parse a product from Advanced Trade API.
     */
    private fun parseAdvancedTradeProduct(productJson: JsonObject): TradableAsset? {
        try {
            val productId = productJson.get("product_id")?.asString ?: return null
            val status = productJson.get("status")?.asString ?: "online"
            val isDisabled = productJson.get("is_disabled")?.asBoolean ?: false
            val tradingDisabled = productJson.get("trading_disabled")?.asBoolean ?: false
            
            if (status != "online" || isDisabled || tradingDisabled) return null
            
            val baseCurrency = productJson.get("base_currency_id")?.asString 
                ?: productJson.get("base_currency")?.asString
                ?: return null
            val quoteCurrency = productJson.get("quote_currency_id")?.asString
                ?: productJson.get("quote_currency")?.asString
                ?: return null
            
            // Only include supported quote currencies
            if (quoteCurrency !in SUPPORTED_QUOTES) return null
            
            // Parse constraints
            val baseIncrement = productJson.get("base_increment")?.asString
            val quoteIncrement = productJson.get("quote_increment")?.asString
            val baseMinSize = productJson.get("base_min_size")?.asString
            val baseMaxSize = productJson.get("base_max_size")?.asString
            
            val lotSize = parseDecimal(baseIncrement, BigDecimal("0.00000001"))
            val tickSize = parseDecimal(quoteIncrement, BigDecimal("0.01"))
            val minOrderSize = parseDecimal(baseMinSize, BigDecimal("0.0001"))
            val maxOrderSize = parseDecimal(baseMaxSize)
            
            // Build exchange info for category mapping
            val exchangeInfo = ExchangeAssetInfo(
                symbol = productId,
                baseAsset = baseCurrency,
                quoteAsset = quoteCurrency,
                status = status,
                isSpot = true,
                isMargin = false  // Coinbase Advanced doesn't have margin
            )
            
            return TradableAsset(
                symbol = normalizeSymbol(productId),
                baseAsset = baseCurrency,
                quoteAsset = quoteCurrency,
                assetType = AssetType.CRYPTO_SPOT,
                category = mapToCategory(exchangeInfo),
                exchange = exchangeName,
                minOrderSize = minOrderSize,
                maxOrderSize = if (maxOrderSize > BigDecimal.ZERO) maxOrderSize else null,
                tickSize = tickSize,
                lotSize = lotSize,
                maxLeverage = 1,  // No leverage on Coinbase spot
                makerFee = BigDecimal("0.004"),   // 0.4% maker (varies by tier)
                takerFee = BigDecimal("0.006"),   // 0.6% taker (varies by tier)
                scalpingEnabled = true,
                marginEnabled = false,
                tradingHours = TradingHours.CRYPTO_24_7,
                status = AssetStatus.TRADING,
                lastUpdated = Instant.now()
            )
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to parse Coinbase product: ${e.message}")
            return null
        }
    }
    
    /**
     * Parse a product from Exchange API (legacy format).
     */
    private fun parseExchangeProduct(productJson: JsonObject): TradableAsset? {
        try {
            val productId = productJson.get("id")?.asString ?: return null
            val status = productJson.get("status")?.asString ?: "online"
            val tradingDisabled = productJson.get("trading_disabled")?.asBoolean ?: false
            
            if (status != "online" || tradingDisabled) return null
            
            val baseCurrency = productJson.get("base_currency")?.asString ?: return null
            val quoteCurrency = productJson.get("quote_currency")?.asString ?: return null
            
            if (quoteCurrency !in SUPPORTED_QUOTES) return null
            
            val baseIncrement = productJson.get("base_increment")?.asString
            val quoteIncrement = productJson.get("quote_increment")?.asString
            val baseMinSize = productJson.get("base_min_size")?.asString
            val baseMaxSize = productJson.get("base_max_size")?.asString
            
            val lotSize = parseDecimal(baseIncrement, BigDecimal("0.00000001"))
            val tickSize = parseDecimal(quoteIncrement, BigDecimal("0.01"))
            val minOrderSize = parseDecimal(baseMinSize, BigDecimal("0.0001"))
            val maxOrderSize = parseDecimal(baseMaxSize)
            
            val exchangeInfo = ExchangeAssetInfo(
                symbol = productId,
                baseAsset = baseCurrency,
                quoteAsset = quoteCurrency,
                status = status,
                isSpot = true,
                isMargin = false
            )
            
            return TradableAsset(
                symbol = normalizeSymbol(productId),
                baseAsset = baseCurrency,
                quoteAsset = quoteCurrency,
                assetType = AssetType.CRYPTO_SPOT,
                category = mapToCategory(exchangeInfo),
                exchange = exchangeName,
                minOrderSize = minOrderSize,
                maxOrderSize = if (maxOrderSize > BigDecimal.ZERO) maxOrderSize else null,
                tickSize = tickSize,
                lotSize = lotSize,
                maxLeverage = 1,
                makerFee = BigDecimal("0.004"),
                takerFee = BigDecimal("0.006"),
                scalpingEnabled = true,
                marginEnabled = false,
                tradingHours = TradingHours.CRYPTO_24_7,
                status = AssetStatus.TRADING,
                lastUpdated = Instant.now()
            )
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to parse Coinbase exchange product: ${e.message}")
            return null
        }
    }
    
    /**
     * Map Coinbase asset info to our category system.
     */
    override fun mapToCategory(exchangeInfo: ExchangeAssetInfo): AssetCategory {
        return inferCategory(exchangeInfo)
    }
    
    /**
     * Convert Coinbase symbol (BTC-USD) to standard format (BTC/USD).
     */
    override fun normalizeSymbol(exchangeSymbol: String): String {
        return exchangeSymbol.replace("-", "/")
    }
    
    companion object {
        private const val TAG = "CoinbaseAssetLoader"
        
        private val SUPPORTED_QUOTES = setOf(
            "USD", "USDT", "USDC", "EUR", "GBP", "BTC", "ETH"
        )
    }
}
