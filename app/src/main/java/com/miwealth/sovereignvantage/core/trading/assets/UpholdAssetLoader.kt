package com.miwealth.sovereignvantage.core.trading.assets

/**
 * UPHOLD ASSET LOADER
 * 
 * Sovereign Vantage: Arthur Edition V5.17.0
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * Loads assets from Uphold exchange, which uniquely supports:
 * - Cryptocurrencies (200+)
 * - Fiat currencies (27 including USD, EUR, GBP, AUD)
 * - Precious metals (XAU, XAG, XPT, XPD)
 * - Equities (US stocks via fractional shares)
 * 
 * This enables Sovereign Vantage to offer multi-asset class trading
 * through a single exchange connection.
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */



import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.miwealth.sovereignvantage.data.models.CryptoAsset
import com.miwealth.sovereignvantage.data.models.CryptoCategory
import com.miwealth.sovereignvantage.data.models.AssetCategory
import com.miwealth.sovereignvantage.data.models.MarketCapTier
import com.miwealth.sovereignvantage.data.models.VolatilityTier
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Asset loader for Uphold exchange.
 * 
 * Uphold is unique in supporting multiple asset classes:
 * - Crypto: BTC, ETH, SOL, etc.
 * - Fiat: USD, EUR, GBP, AUD, etc.
 * - Metals: Gold (XAU), Silver (XAG), Platinum (XPT), Palladium (XPD)
 * - Equities: US stocks via fractional shares
 * 
 * Usage:
 * ```kotlin
 * val loader = UpholdAssetLoader(context)
 * 
 * // Load all assets
 * val assets = loader.loadAllAssets()
 * 
 * // Load by category
 * val crypto = loader.loadCryptoAssets()
 * val forex = loader.loadForexAssets()
 * val metals = loader.loadMetalAssets()
 * 
 * // Get ticker
 * val btcUsd = loader.getTicker("BTC", "USD")
 * ```
 */
class UpholdAssetLoader(
    private val context: Context,
    private val useSandbox: Boolean = false
) {
    
    companion object {
        private const val TAG = "UpholdAssetLoader"
        
        // API endpoints
        private const val PRODUCTION_URL = "https://api.uphold.com"
        private const val SANDBOX_URL = "https://api-sandbox.uphold.com"
        
        // Asset types
        private val FIAT_CURRENCIES = setOf(
            "USD", "EUR", "GBP", "AUD", "CAD", "CHF", "CNY", "DKK", "HKD", "ILS",
            "INR", "JPY", "KES", "MXN", "NOK", "NZD", "PHP", "PLN", "SEK", "SGD",
            "ARS", "BRL", "COP", "CZK", "HUF", "ZAR", "AED"
        )
        
        private val PRECIOUS_METALS = setOf("XAU", "XAG", "XPT", "XPD")
        
        // Uphold-specific quirks
        private val SYMBOL_MAPPINGS = mapOf(
            "BTC" to "BTC",  // No mapping needed, but kept for documentation
            "ETH" to "ETH",
            "GOLD" to "XAU",
            "SILVER" to "XAG"
        )
        
        // Default risk parameters for different asset classes (using SACRED 3.5% stop loss)
        private const val SACRED_STOP_LOSS = 0.035
    }
    
    private val baseUrl = if (useSandbox) SANDBOX_URL else PRODUCTION_URL
    private val gson = Gson()
    
    private val httpClient = com.miwealth.sovereignvantage.core.network.SharedHttpClient.fastClient
    
    // Cached assets
    private var cachedAssets: List<UpholdAsset>? = null
    private var cacheTimestamp: Long = 0
    private val cacheTtlMs = 5 * 60 * 1000L  // 5 minutes
    
    val exchangeId: String = "uphold"
    val exchangeName: String = "Uphold"
    
    // =========================================================================
    // PUBLIC API
    // =========================================================================
    
    suspend fun loadAllAssets(): List<CryptoAsset> {
        return withContext(Dispatchers.IO) {
            val upholdAssets = fetchAssets()
            upholdAssets.mapNotNull { convertToTradableAsset(it) }
        }
    }
    
    suspend fun loadAsset(symbol: String): CryptoAsset? {
        return withContext(Dispatchers.IO) {
            val upholdAssets = fetchAssets()
            upholdAssets.find { it.code == symbol }?.let { convertToTradableAsset(it) }
        }
    }
    
    suspend fun getTradingPairs(): List<TradingPairInfo> {
        return withContext(Dispatchers.IO) {
            val assets = fetchAssets()
            val pairs = mutableListOf<TradingPairInfo>()
            
            // Uphold allows trading any asset against any other
            // We'll generate pairs for common quote currencies
            val quoteAssets = listOf("USD", "EUR", "BTC", "ETH")
            
            for (asset in assets) {
                if (asset.code in quoteAssets) continue
                if (asset.status != "open") continue
                
                for (quote in quoteAssets) {
                    if (asset.code == quote) continue
                    
                    pairs.add(TradingPairInfo(
                        symbol = "${asset.code}-$quote",
                        baseAsset = asset.code,
                        quoteAsset = quote,
                        exchange = "UPHOLD",
                        minQuantity = 0.0,  // Uphold supports fractional
                        minNotional = 1.0   // Minimum $1 equivalent
                    ))
                }
            }
            
            pairs
        }
    }
    
    suspend fun refreshAssets(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                cachedAssets = null
                cacheTimestamp = 0
                fetchAssets()
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh assets", e)
                false
            }
        }
    }
    
    /**
     * Load only cryptocurrency assets.
     */
    suspend fun loadCryptoAssets(): List<CryptoAsset> {
        return loadAllAssets().filter { 
            it.category != AssetCategory.STABLECOIN &&
            it.symbol !in FIAT_CURRENCIES &&
            it.symbol !in PRECIOUS_METALS
        }
    }
    
    /**
     * Load only FOREX (fiat currency) assets.
     */
    suspend fun loadForexAssets(): List<UpholdForexAsset> {
        return withContext(Dispatchers.IO) {
            val upholdAssets = fetchAssets()
            upholdAssets
                .filter { it.code in FIAT_CURRENCIES }
                .map { convertToForexAsset(it) }
        }
    }
    
    /**
     * Load only precious metal assets.
     */
    suspend fun loadMetalAssets(): List<MetalAsset> {
        return withContext(Dispatchers.IO) {
            val upholdAssets = fetchAssets()
            upholdAssets
                .filter { it.code in PRECIOUS_METALS }
                .map { convertToMetalAsset(it) }
        }
    }
    
    /**
     * Get ticker price for a specific pair.
     */
    suspend fun getTicker(base: String, quote: String): UpholdTicker? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl/v0/ticker/$base-$quote"
                val request = Request.Builder().url(url).build()
                val response = httpClient.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        val json = JsonParser.parseString(body).asJsonObject
                        UpholdTicker(
                            pair = "$base-$quote",
                            ask = json.get("ask")?.asString?.toDoubleOrNull() ?: 0.0,
                            bid = json.get("bid")?.asString?.toDoubleOrNull() ?: 0.0,
                            currency = json.get("currency")?.asString ?: quote
                        )
                    } else null
                } else null
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get ticker for $base-$quote", e)
                null
            }
        }
    }
    
    /**
     * Get all tickers (bulk fetch).
     */
    suspend fun getAllTickers(): List<UpholdTicker> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl/v0/ticker"
                val request = Request.Builder().url(url).build()
                val response = httpClient.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        val json = JsonParser.parseString(body)
                        if (json.isJsonArray) {
                            json.asJsonArray.mapNotNull { element ->
                                try {
                                    val obj = element.asJsonObject
                                    UpholdTicker(
                                        pair = obj.get("pair")?.asString ?: return@mapNotNull null,
                                        ask = obj.get("ask")?.asString?.toDoubleOrNull() ?: 0.0,
                                        bid = obj.get("bid")?.asString?.toDoubleOrNull() ?: 0.0,
                                        currency = obj.get("currency")?.asString ?: ""
                                    )
                                } catch (e: Exception) {
                                    null
                                }
                            }
                        } else emptyList()
                    } else emptyList()
                } else emptyList()
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get all tickers", e)
                emptyList()
            }
        }
    }
    
    // =========================================================================
    // PRIVATE METHODS
    // =========================================================================
    
    private suspend fun fetchAssets(): List<UpholdAsset> {
        // Check cache
        val now = System.currentTimeMillis()
        if (cachedAssets != null && now - cacheTimestamp < cacheTtlMs) {
            return cachedAssets!!
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl/v0/assets"
                val request = Request.Builder().url(url).build()
                val response = httpClient.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        val json = JsonParser.parseString(body)
                        if (json.isJsonArray) {
                            val assets = json.asJsonArray.mapNotNull { element ->
                                parseAsset(element.asJsonObject)
                            }
                            
                            cachedAssets = assets
                            cacheTimestamp = now
                            
                            Log.i(TAG, "Loaded ${assets.size} assets from Uphold")
                            assets
                        } else emptyList()
                    } else emptyList()
                } else {
                    Log.e(TAG, "Failed to fetch assets: ${response.code}")
                    emptyList()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch Uphold assets", e)
                emptyList()
            }
        }
    }
    
    private fun parseAsset(json: JsonObject): UpholdAsset? {
        return try {
            UpholdAsset(
                code = json.get("code")?.asString ?: return null,
                name = json.get("name")?.asString ?: "",
                status = json.get("status")?.asString ?: "open",
                type = json.get("type")?.asString ?: "crypto",
                symbol = json.get("symbol")?.asString
            )
        } catch (e: Exception) {
            null
        }
    }
    
    private fun convertToTradableAsset(asset: UpholdAsset): CryptoAsset? {
        // Skip fiat and metals (they get their own asset types)
        if (asset.code in FIAT_CURRENCIES) return null
        if (asset.code in PRECIOUS_METALS) return null
        if (asset.status != "open") return null
        
        val cryptoCat = inferCategory(asset.code)
        val assetCat = mapCryptoToAssetCategory(cryptoCat)
        val riskParams = getRiskParamsForCategory(cryptoCat)
        
        return CryptoAsset(
            symbol = asset.code,
            name = asset.name,
            category = assetCat,
            primaryChain = inferPrimaryChain(asset.code),
            marketCapTier = MarketCapTier.SMALL,  // Default; updated from live data later
            volatilityTier = inferVolatilityTier(cryptoCat),
            kellyMultiplier = riskParams.kelly,
            maxPositionPercent = riskParams.maxPosition,
            defaultStopLossPercent = SACRED_STOP_LOSS,  // Always 3.5%
            recommendedLeverage = riskParams.leverage,
            launchDate = null
        )
    }
    
    private fun mapCryptoToAssetCategory(cat: CryptoCategory): AssetCategory = when (cat) {
        CryptoCategory.LAYER1 -> AssetCategory.LAYER_1
        CryptoCategory.LAYER2 -> AssetCategory.LAYER_2
        CryptoCategory.DEFI -> AssetCategory.DEFI
        CryptoCategory.MEME -> AssetCategory.MEME
        CryptoCategory.AI -> AssetCategory.AI_ML
        CryptoCategory.GAMING -> AssetCategory.GAMING_NFT
        CryptoCategory.STABLECOIN -> AssetCategory.STABLECOIN
        CryptoCategory.EXCHANGE_TOKEN -> AssetCategory.EXCHANGE_TOKEN
        CryptoCategory.INFRASTRUCTURE -> AssetCategory.INFRASTRUCTURE
        CryptoCategory.ORACLE -> AssetCategory.INFRASTRUCTURE
        CryptoCategory.PRIVACY -> AssetCategory.PRIVACY
        CryptoCategory.OTHER -> AssetCategory.OTHER
    }
    
    private fun inferPrimaryChain(symbol: String): String = when (symbol) {
        "BTC" -> "Bitcoin"
        "ETH" -> "Ethereum"
        "SOL" -> "Solana"
        "ADA" -> "Cardano"
        "AVAX" -> "Avalanche"
        "DOT" -> "Polkadot"
        "MATIC", "POL" -> "Polygon"
        "ARB" -> "Arbitrum"
        "OP" -> "Optimism"
        else -> "Ethereum"  // Default: most tokens are ERC-20
    }
    
    private fun inferVolatilityTier(cat: CryptoCategory): VolatilityTier = when (cat) {
        CryptoCategory.STABLECOIN -> VolatilityTier.LOW
        CryptoCategory.LAYER1 -> VolatilityTier.MEDIUM
        CryptoCategory.LAYER2 -> VolatilityTier.HIGH
        CryptoCategory.DEFI -> VolatilityTier.HIGH
        CryptoCategory.MEME -> VolatilityTier.EXTREME
        else -> VolatilityTier.HIGH
    }
    
    private fun convertToForexAsset(asset: UpholdAsset): UpholdForexAsset {
        return UpholdForexAsset(
            symbol = asset.code,
            name = asset.name,
            currencyPair = "${asset.code}/USD",
            region = inferRegion(asset.code),
            // FOREX uses tighter risk parameters
            kellyMultiplier = 0.8,
            maxPositionPercent = 0.15,
            maxLeverage = 10.0,  // FOREX typically allows higher leverage
            sacredStopLoss = 0.02  // Tighter for FOREX
        )
    }
    
    private fun convertToMetalAsset(asset: UpholdAsset): MetalAsset {
        val metalName = when (asset.code) {
            "XAU" -> "Gold"
            "XAG" -> "Silver"
            "XPT" -> "Platinum"
            "XPD" -> "Palladium"
            else -> asset.name
        }
        
        return MetalAsset(
            symbol = asset.code,
            name = metalName,
            metalType = asset.code,
            // Metals are relatively stable
            kellyMultiplier = 0.7,
            maxPositionPercent = 0.20,
            maxLeverage = 5.0,
            sacredStopLoss = 0.025  // Tighter for metals
        )
    }
    
    private fun inferCategory(symbol: String): CryptoCategory {
        return when {
            symbol in setOf("USDT", "USDC", "DAI", "BUSD", "TUSD") -> CryptoCategory.STABLECOIN
            symbol in setOf("BTC", "ETH", "SOL", "ADA", "AVAX", "DOT") -> CryptoCategory.LAYER1
            symbol in setOf("MATIC", "ARB", "OP", "IMX") -> CryptoCategory.LAYER2
            symbol in setOf("AAVE", "UNI", "LINK", "MKR", "CRV") -> CryptoCategory.DEFI
            symbol in setOf("DOGE", "SHIB", "PEPE", "FLOKI", "BONK") -> CryptoCategory.MEME
            symbol in setOf("FET", "AGIX", "OCEAN", "RNDR") -> CryptoCategory.AI
            symbol in setOf("AXS", "SAND", "MANA", "GALA") -> CryptoCategory.GAMING
            else -> CryptoCategory.OTHER
        }
    }
    
    private data class RiskParams(
        val kelly: Double,
        val maxPosition: Double,
        val leverage: Double
    )
    
    private fun getRiskParamsForCategory(category: CryptoCategory): RiskParams {
        return when (category) {
            CryptoCategory.LAYER1 -> RiskParams(1.0, 0.20, 5.5)
            CryptoCategory.LAYER2 -> RiskParams(0.9, 0.15, 5.0)
            CryptoCategory.DEFI -> RiskParams(0.8, 0.12, 4.5)
            CryptoCategory.STABLECOIN -> RiskParams(0.5, 0.30, 1.0)
            CryptoCategory.MEME -> RiskParams(0.4, 0.05, 2.0)
            CryptoCategory.AI -> RiskParams(0.7, 0.10, 4.0)
            CryptoCategory.GAMING -> RiskParams(0.6, 0.08, 3.5)
            CryptoCategory.INFRASTRUCTURE -> RiskParams(0.8, 0.12, 4.5)
            CryptoCategory.ORACLE -> RiskParams(0.8, 0.12, 4.5)
            CryptoCategory.PRIVACY -> RiskParams(0.5, 0.05, 2.5)
            CryptoCategory.EXCHANGE_TOKEN -> RiskParams(0.7, 0.10, 4.0)
            CryptoCategory.OTHER -> RiskParams(0.5, 0.05, 3.0)
        }
    }
    
    private fun inferRegion(currencyCode: String): String {
        return when (currencyCode) {
            "USD" -> "North America"
            "EUR" -> "Europe"
            "GBP" -> "Europe"
            "AUD" -> "Oceania"
            "CAD" -> "North America"
            "CHF" -> "Europe"
            "JPY" -> "Asia"
            "CNY" -> "Asia"
            "HKD" -> "Asia"
            "SGD" -> "Asia"
            "INR" -> "Asia"
            "KES" -> "Africa"
            "ZAR" -> "Africa"
            "MXN" -> "Latin America"
            "BRL" -> "Latin America"
            "ARS" -> "Latin America"
            "COP" -> "Latin America"
            "AED" -> "Middle East"
            "ILS" -> "Middle East"
            else -> "Global"
        }
    }
}

// =========================================================================
// DATA CLASSES
// =========================================================================

data class UpholdAsset(
    val code: String,
    val name: String,
    val status: String,
    val type: String,
    val symbol: String?
)

data class UpholdTicker(
    val pair: String,
    val ask: Double,
    val bid: Double,
    val currency: String
) {
    val mid: Double get() = (ask + bid) / 2
    val spread: Double get() = ask - bid
    val spreadPercent: Double get() = if (mid > 0) (spread / mid) * 100 else 0.0
}

data class UpholdForexAsset(
    val symbol: String,
    val name: String,
    val currencyPair: String,
    val region: String,
    val kellyMultiplier: Double,
    val maxPositionPercent: Double,
    val maxLeverage: Double,
    val sacredStopLoss: Double
)

data class MetalAsset(
    val symbol: String,
    val name: String,
    val metalType: String,
    val kellyMultiplier: Double,
    val maxPositionPercent: Double,
    val maxLeverage: Double,
    val sacredStopLoss: Double
)
