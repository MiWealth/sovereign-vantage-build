/**
 * Binance Asset Loader - Binance Exchange Integration (PQC SECURED)
 * 
 * Sovereign Vantage: Arthur Edition V5.5.33
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * Fetches tradable asset information from Binance's exchangeInfo API.
 * Supports both spot and futures markets.
 * 
 * POST-QUANTUM FORTRESS SECURITY:
 * - HybridSecureHttpClient with Kyber-1024 + TLS 1.3
 * - Dilithium-5 signed request audit trail
 * - Exchange-specific PQC configuration
 * 
 * API ENDPOINTS:
 * - Spot:    GET https://api.binance.com/api/v3/exchangeInfo
 * - Futures: GET https://fapi.binance.com/fapi/v1/exchangeInfo
 * 
 * RATE LIMITS:
 * - 1200 requests per minute (IP)
 * - exchangeInfo has weight of 10
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 */
package com.miwealth.sovereignvantage.core.trading.assets

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.miwealth.sovereignvantage.core.security.pqc.hybrid.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.Instant

/**
 * Asset loader for Binance exchange.
 * Loads both spot and perpetual futures markets.
 * Uses HybridSecureHttpClient with PQC protection.
 */
class BinanceAssetLoader(
    secureClient: HybridSecureHttpClient = HybridSecureHttpClient.create(
        HybridPQCConfig.forExchange("binance")
    )
) : BaseAssetLoader(secureClient) {
    
    override val exchangeName: String = "Binance"
    override val baseUrl: String = "https://api.binance.com"
    
    private val futuresBaseUrl = "https://fapi.binance.com"
    
    // Binance rate limit: be conservative
    override val rateLimitCalls: Int = 5
    override val rateLimitWindowMs: Long = 1000L
    
    /**
     * Fetch all tradable assets from both spot and futures markets.
     */
    override suspend fun fetchAllAssets(): List<TradableAsset> = withContext(Dispatchers.IO) {
        val results = mutableListOf<TradableAsset>()
        
        // Fetch spot and futures concurrently
        val (spotAssets, futuresAssets) = listOf(
            async { fetchSpotAssets() },
            async { fetchFuturesAssets() }
        ).awaitAll().let { it[0] to it[1] }
        
        results.addAll(spotAssets)
        results.addAll(futuresAssets)
        
        // Update cache
        results.forEach { asset ->
            assetCache[asset.symbol] = asset
        }
        lastLoadTime = Instant.now()
        
        android.util.Log.i(TAG, "Loaded ${results.size} assets from Binance " +
            "(${spotAssets.size} spot, ${futuresAssets.size} futures)")
        
        results
    }
    
    /**
     * Fetch a single asset by symbol.
     */
    override suspend fun fetchAsset(symbol: String): TradableAsset? {
        // Check cache first
        assetCache[symbol]?.let { return it }
        
        // If cache is stale, refresh all
        if (isCacheStale()) {
            fetchAllAssets()
        }
        
        return assetCache[symbol]
    }
    
    /**
     * Fetch spot market assets.
     */
    private suspend fun fetchSpotAssets(): List<TradableAsset> {
        val url = "$baseUrl/api/v3/exchangeInfo"
        val json = fetchJson(url) ?: return emptyList()
        
        val symbols = json.getAsJsonArray("symbols") ?: return emptyList()
        
        return symbols.mapNotNull { element ->
            parseSpotSymbol(element.asJsonObject)
        }
    }
    
    /**
     * Fetch perpetual futures assets.
     */
    private suspend fun fetchFuturesAssets(): List<TradableAsset> {
        val url = "$futuresBaseUrl/fapi/v1/exchangeInfo"
        val json = fetchJson(url) ?: return emptyList()
        
        val symbols = json.getAsJsonArray("symbols") ?: return emptyList()
        
        return symbols.mapNotNull { element ->
            parseFuturesSymbol(element.asJsonObject)
        }
    }
    
    /**
     * Parse a spot symbol from Binance exchangeInfo.
     */
    private fun parseSpotSymbol(symbolJson: JsonObject): TradableAsset? {
        try {
            val status = symbolJson.get("status")?.asString ?: return null
            if (status != "TRADING") return null
            
            val rawSymbol = symbolJson.get("symbol")?.asString ?: return null
            val baseAsset = symbolJson.get("baseAsset")?.asString ?: return null
            val quoteAsset = symbolJson.get("quoteAsset")?.asString ?: return null
            
            // Only include USDT, USD, USDC, BTC, ETH quote pairs
            if (quoteAsset !in SUPPORTED_QUOTES) return null
            
            // Parse filters
            val filters = symbolJson.getAsJsonArray("filters") ?: JsonArray()
            val filterMap = parseFilters(filters)
            
            // Get permissions
            val permissions = symbolJson.getAsJsonArray("permissions")
                ?.map { it.asString } ?: emptyList()
            
            val isMargin = "MARGIN" in permissions
            
            // Build exchange info for category mapping
            val exchangeInfo = ExchangeAssetInfo(
                symbol = rawSymbol,
                baseAsset = baseAsset,
                quoteAsset = quoteAsset,
                status = status,
                isSpot = true,
                isMargin = isMargin,
                permissions = permissions
            )
            
            return TradableAsset(
                symbol = normalizeSymbol(rawSymbol),
                baseAsset = baseAsset,
                quoteAsset = quoteAsset,
                assetType = AssetType.CRYPTO_SPOT,
                category = mapToCategory(exchangeInfo),
                exchange = exchangeName,
                minOrderSize = filterMap["minQty"] ?: BigDecimal("0.00001"),
                maxOrderSize = filterMap["maxQty"],
                tickSize = filterMap["tickSize"] ?: BigDecimal("0.01"),
                lotSize = filterMap["stepSize"] ?: BigDecimal("0.00001"),
                maxLeverage = if (isMargin) 10 else 1,
                makerFee = BigDecimal("0.001"),
                takerFee = BigDecimal("0.001"),
                scalpingEnabled = true,
                marginEnabled = isMargin,
                tradingHours = TradingHours.CRYPTO_24_7,
                status = AssetStatus.TRADING,
                lastUpdated = Instant.now()
            )
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to parse spot symbol: ${e.message}")
            return null
        }
    }
    
    /**
     * Parse a futures symbol from Binance futures exchangeInfo.
     */
    private fun parseFuturesSymbol(symbolJson: JsonObject): TradableAsset? {
        try {
            val status = symbolJson.get("status")?.asString ?: return null
            if (status != "TRADING") return null
            
            val rawSymbol = symbolJson.get("symbol")?.asString ?: return null
            val baseAsset = symbolJson.get("baseAsset")?.asString ?: return null
            val quoteAsset = symbolJson.get("quoteAsset")?.asString ?: return null
            val contractType = symbolJson.get("contractType")?.asString ?: ""
            
            // Only perpetuals for now
            if (contractType != "PERPETUAL") return null
            
            // Parse filters
            val filters = symbolJson.getAsJsonArray("filters") ?: JsonArray()
            val filterMap = parseFilters(filters)
            
            // Parse leverage brackets (simplified - just get max)
            val maxLeverage = 125 // Binance default max, can be restricted per symbol
            
            return TradableAsset(
                symbol = "${normalizeSymbol(rawSymbol)}-PERP",
                baseAsset = baseAsset,
                quoteAsset = quoteAsset,
                assetType = AssetType.CRYPTO_FUTURES,
                category = AssetCategory.CRYPTO_PERP,
                exchange = exchangeName,
                minOrderSize = filterMap["minQty"] ?: BigDecimal("0.001"),
                maxOrderSize = filterMap["maxQty"],
                tickSize = filterMap["tickSize"] ?: BigDecimal("0.01"),
                lotSize = filterMap["stepSize"] ?: BigDecimal("0.001"),
                maxLeverage = maxLeverage,
                makerFee = BigDecimal("0.0002"),
                takerFee = BigDecimal("0.0004"),
                scalpingEnabled = true,
                marginEnabled = true,
                tradingHours = TradingHours.CRYPTO_24_7,
                status = AssetStatus.TRADING,
                lastUpdated = Instant.now()
            )
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to parse futures symbol: ${e.message}")
            return null
        }
    }
    
    /**
     * Parse Binance filter array into map of filter values.
     */
    private fun parseFilters(filters: JsonArray): Map<String, BigDecimal?> {
        val result = mutableMapOf<String, BigDecimal?>()
        
        for (filter in filters) {
            val filterObj = filter.asJsonObject
            val filterType = filterObj.get("filterType")?.asString ?: continue
            
            when (filterType) {
                "PRICE_FILTER" -> {
                    result["tickSize"] = parseDecimal(filterObj.get("tickSize")?.asString)
                    result["minPrice"] = parseDecimal(filterObj.get("minPrice")?.asString)
                    result["maxPrice"] = parseDecimal(filterObj.get("maxPrice")?.asString)
                }
                "LOT_SIZE" -> {
                    result["stepSize"] = parseDecimal(filterObj.get("stepSize")?.asString)
                    result["minQty"] = parseDecimal(filterObj.get("minQty")?.asString)
                    result["maxQty"] = parseDecimal(filterObj.get("maxQty")?.asString)
                }
                "MIN_NOTIONAL", "NOTIONAL" -> {
                    result["minNotional"] = parseDecimal(filterObj.get("minNotional")?.asString)
                }
                "MARKET_LOT_SIZE" -> {
                    result["marketMinQty"] = parseDecimal(filterObj.get("minQty")?.asString)
                    result["marketMaxQty"] = parseDecimal(filterObj.get("maxQty")?.asString)
                }
            }
        }
        
        return result
    }
    
    /**
     * Map Binance asset info to our category system.
     */
    override fun mapToCategory(exchangeInfo: ExchangeAssetInfo): AssetCategory {
        // Use the base inference logic
        return inferCategory(exchangeInfo)
    }
    
    /**
     * Convert Binance symbol (BTCUSDT) to standard format (BTC/USDT).
     */
    override fun normalizeSymbol(exchangeSymbol: String): String {
        // Find the quote asset and split
        for (quote in SUPPORTED_QUOTES) {
            if (exchangeSymbol.endsWith(quote)) {
                val base = exchangeSymbol.removeSuffix(quote)
                return "$base/$quote"
            }
        }
        return exchangeSymbol
    }
    
    companion object {
        private const val TAG = "BinanceAssetLoader"
        
        // Supported quote currencies (in order of preference for matching)
        private val SUPPORTED_QUOTES = listOf(
            "USDT", "USDC", "BUSD", "USD", "BTC", "ETH", "BNB"
        )
    }
}
