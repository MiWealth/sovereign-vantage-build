/**
 * UNIVERSAL ASSET DISCOVERY SERVICE
 * 
 * Sovereign Vantage: Arthur Edition V5.5.69
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * Discovers and enriches cryptocurrency assets from multiple sources:
 * - Primary: Exchange APIs (Binance, Kraken, etc.) - live trading data
 * - Secondary: DeFiLlama - TVL, protocol health, chain data
 * - Tertiary: CoinGecko - market cap, circulating supply (rate-limited)
 * 
 * This service provides the data that DynamicRiskAssigner uses to
 * assign risk parameters to ANY cryptocurrency dynamically.
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */
package com.miwealth.sovereignvantage.core.trading.assets

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Discovers assets from multiple data sources and enriches with metadata.
 * 
 * Usage:
 * ```kotlin
 * val discovery = UniversalAssetDiscovery(context)
 * 
 * // Discover all assets from connected exchanges
 * val assets = discovery.discoverFromExchanges(listOf("binance", "kraken"))
 * 
 * // Enrich with DeFiLlama data
 * val enriched = discovery.enrichWithDeFiLlama(assets)
 * 
 * // Get full asset data for risk assignment
 * val marketData = discovery.getAssetMarketData("AAVE")
 * val risk = dynamicRiskAssigner.assignRiskParameters(marketData)
 * ```
 */
class UniversalAssetDiscovery(
    private val context: Context
) {
    companion object {
        private const val TAG = "UniversalAssetDiscovery"
        
        // API endpoints
        private const val DEFILLAMA_BASE = "https://api.llama.fi"
        private const val COINGECKO_BASE = "https://api.coingecko.com/api/v3"
        private const val BINANCE_BASE = "https://api.binance.com"
        
        // Rate limiting
        private const val COINGECKO_CALLS_PER_MINUTE = 10  // Free tier limit
        private const val DEFILLAMA_CALLS_PER_MINUTE = 30  // Generous free tier
        private const val CACHE_TTL_MS = 5 * 60 * 1000L    // 5 minutes
        
        // Known stablecoins (skip for TVL/volatility analysis)
        private val STABLECOINS = setOf(
            "USDT", "USDC", "BUSD", "DAI", "TUSD", "USDP", "GUSD", "FRAX",
            "LUSD", "SUSD", "MIM", "UST", "USTC", "USDD", "EURC", "EURT"
        )
    }
    
    private val gson = Gson()
    private val httpClient = com.miwealth.sovereignvantage.core.network.SharedHttpClient.fastClient
    
    // Caches
    private val assetCache = ConcurrentHashMap<String, CachedAsset>()
    private val protocolCache = ConcurrentHashMap<String, DeFiLlamaProtocol>()
    private val chainCache = ConcurrentHashMap<String, DeFiLlamaChain>()
    
    // Rate limiting
    private val coingeckoMutex = Mutex()
    private val defillamaMutex = Mutex()
    private var lastCoingeckoCall = AtomicLong(0)
    private var lastDefillamaCall = AtomicLong(0)
    
    // State
    private val _discoveryProgress = MutableStateFlow<DiscoveryProgress>(DiscoveryProgress())
    val discoveryProgress: StateFlow<DiscoveryProgress> = _discoveryProgress.asStateFlow()
    
    // =========================================================================
    // PUBLIC API
    // =========================================================================
    
    /**
     * Discover all tradeable assets from exchange APIs.
     */
    suspend fun discoverFromExchanges(
        exchangeIds: List<String> = listOf("binance")
    ): List<DiscoveredAsset> {
        return withContext(Dispatchers.IO) {
            val discovered = mutableListOf<DiscoveredAsset>()
            val seenSymbols = mutableSetOf<String>()
            
            for (exchangeId in exchangeIds) {
                try {
                    _discoveryProgress.value = _discoveryProgress.value.copy(
                        currentExchange = exchangeId,
                        status = "Fetching from $exchangeId..."
                    )
                    
                    val assets = when (exchangeId.lowercase()) {
                        "binance" -> discoverFromBinance()
                        "kraken" -> discoverFromKraken()
                        "coinbase" -> discoverFromCoinbase()
                        else -> emptyList()
                    }
                    
                    // Deduplicate by base asset
                    for (asset in assets) {
                        if (asset.baseAsset !in seenSymbols) {
                            seenSymbols.add(asset.baseAsset)
                            discovered.add(asset)
                        } else {
                            // Add exchange to existing asset's list
                            discovered.find { it.baseAsset == asset.baseAsset }?.let {
                                it.availableExchanges.add(exchangeId)
                            }
                        }
                    }
                    
                    Log.i(TAG, "Discovered ${assets.size} assets from $exchangeId")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to discover from $exchangeId", e)
                }
            }
            
            _discoveryProgress.value = _discoveryProgress.value.copy(
                totalAssets = discovered.size,
                status = "Discovered ${discovered.size} unique assets"
            )
            
            discovered
        }
    }
    
    /**
     * Enrich assets with DeFiLlama protocol data.
     */
    suspend fun enrichWithDeFiLlama(
        assets: List<DiscoveredAsset>
    ): List<EnrichedAsset> {
        return withContext(Dispatchers.IO) {
            // Load all protocols once
            val protocols = fetchDeFiLlamaProtocols()
            val chains = fetchDeFiLlamaChains()
            
            val enriched = mutableListOf<EnrichedAsset>()
            
            for ((index, asset) in assets.withIndex()) {
                _discoveryProgress.value = _discoveryProgress.value.copy(
                    enrichedCount = index,
                    status = "Enriching ${asset.baseAsset}..."
                )
                
                // Find matching protocol
                val protocol = findMatchingProtocol(asset.baseAsset, protocols)
                val chain = findMatchingChain(asset.baseAsset, chains)
                
                enriched.add(EnrichedAsset(
                    baseAsset = asset.baseAsset,
                    name = asset.name,
                    availableExchanges = asset.availableExchanges,
                    lastPrice = asset.lastPrice,
                    volume24h = asset.volume24h,
                    priceChange24h = asset.priceChange24h,
                    // DeFiLlama data
                    tvlUsd = protocol?.tvl,
                    tvlChange24h = protocol?.change_1d,
                    tvlChange7d = protocol?.change_7d,
                    category = protocol?.category ?: inferCategory(asset.baseAsset),
                    chains = protocol?.chains ?: listOfNotNull(chain?.name),
                    mcapTvlRatio = protocol?.mcap?.let { mcap ->
                        protocol.tvl?.let { tvl -> if (tvl > 0) mcap / tvl else null }
                    },
                    // Chain data
                    chainTvl = chain?.tvl,
                    isLayer1 = chain != null,
                    isLayer2 = asset.baseAsset in LAYER2_TOKENS,
                    isStablecoin = asset.baseAsset in STABLECOINS
                ))
            }
            
            _discoveryProgress.value = _discoveryProgress.value.copy(
                enrichedCount = enriched.size,
                status = "Enriched ${enriched.size} assets"
            )
            
            enriched
        }
    }
    
    /**
     * Get full market data for a specific asset (for DynamicRiskAssigner).
     */
    suspend fun getAssetMarketData(symbol: String): AssetMarketData? {
        return withContext(Dispatchers.IO) {
            // Check cache first
            val cached = assetCache[symbol]
            if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
                return@withContext cached.data
            }
            
            try {
                // Try Binance first (most comprehensive)
                val binanceData = fetchBinanceTicker(symbol)
                
                // Enrich with DeFiLlama if available
                val protocols = protocolCache.values.toList().ifEmpty { fetchDeFiLlamaProtocols() }
                val protocol = findMatchingProtocol(symbol, protocols)
                
                val marketData = AssetMarketData(
                    symbol = symbol,
                    name = binanceData?.name ?: symbol,
                    marketCapUsd = protocol?.mcap ?: estimateMarketCap(binanceData),
                    volume24hUsd = binanceData?.volume24h ?: 0.0,
                    priceChangePercent24h = binanceData?.priceChange24h ?: 0.0,
                    tvlUsd = protocol?.tvl,
                    availableExchanges = binanceData?.exchanges ?: listOf("BINANCE"),
                    category = protocol?.category ?: inferCategory(symbol),
                    isStablecoin = symbol in STABLECOINS,
                    priceHistory = emptyList()  // Would need historical API call
                )
                
                // Cache it
                assetCache[symbol] = CachedAsset(marketData, System.currentTimeMillis())
                
                marketData
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get market data for $symbol", e)
                null
            }
        }
    }
    
    /**
     * Batch fetch market data for multiple assets.
     */
    suspend fun batchGetMarketData(
        symbols: List<String>,
        concurrency: Int = 5
    ): Map<String, AssetMarketData> {
        return withContext(Dispatchers.IO) {
            val results = ConcurrentHashMap<String, AssetMarketData>()
            
            symbols.chunked(concurrency).forEach { batch ->
                batch.map { symbol ->
                    async {
                        getAssetMarketData(symbol)?.let { results[symbol] = it }
                    }
                }.awaitAll()
                
                // Small delay between batches to respect rate limits
                delay(100)
            }
            
            results.toMap()
        }
    }
    
    /**
     * Search for assets by name or symbol.
     */
    suspend fun searchAssets(query: String, limit: Int = 20): List<AssetSearchResult> {
        return withContext(Dispatchers.IO) {
            val results = mutableListOf<AssetSearchResult>()
            val queryLower = query.lowercase()
            
            // Search cached assets first
            for ((symbol, cached) in assetCache) {
                if (symbol.lowercase().contains(queryLower) ||
                    cached.data.name.lowercase().contains(queryLower)) {
                    results.add(AssetSearchResult(
                        symbol = symbol,
                        name = cached.data.name,
                        marketCap = cached.data.marketCapUsd,
                        source = "cache"
                    ))
                }
                if (results.size >= limit) break
            }
            
            // If not enough results, search CoinGecko
            if (results.size < limit) {
                try {
                    val geckoResults = searchCoinGecko(query, limit - results.size)
                    results.addAll(geckoResults)
                } catch (e: Exception) {
                    Log.w(TAG, "CoinGecko search failed", e)
                }
            }
            
            results.take(limit)
        }
    }
    
    /**
     * Get all available trading pairs across exchanges.
     */
    suspend fun getAllTradingPairs(): List<TradingPairInfo> {
        return withContext(Dispatchers.IO) {
            val pairs = mutableListOf<TradingPairInfo>()
            
            // Binance pairs
            try {
                val binancePairs = fetchBinancePairs()
                pairs.addAll(binancePairs)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch Binance pairs", e)
            }
            
            pairs
        }
    }
    
    // =========================================================================
    // EXCHANGE DISCOVERY
    // =========================================================================
    
    private suspend fun discoverFromBinance(): List<DiscoveredAsset> {
        val url = "$BINANCE_BASE/api/v3/ticker/24hr"
        val json = fetchJson(url) ?: return emptyList()
        
        if (!json.isJsonArray) return emptyList()
        
        val assets = mutableMapOf<String, DiscoveredAsset>()
        
        for (element in json.asJsonArray) {
            val ticker = element.asJsonObject
            val symbol = ticker.get("symbol")?.asString ?: continue
            
            // Extract base asset (remove quote: BTCUSDT -> BTC)
            val baseAsset = extractBaseAsset(symbol) ?: continue
            
            // Skip if already have this base asset with better data
            if (assets.containsKey(baseAsset)) continue
            
            assets[baseAsset] = DiscoveredAsset(
                baseAsset = baseAsset,
                name = baseAsset,  // Binance doesn't provide full names
                availableExchanges = mutableListOf("BINANCE"),
                lastPrice = ticker.get("lastPrice")?.asString?.toDoubleOrNull() ?: 0.0,
                volume24h = ticker.get("quoteVolume")?.asString?.toDoubleOrNull() ?: 0.0,
                priceChange24h = ticker.get("priceChangePercent")?.asString?.toDoubleOrNull() ?: 0.0
            )
        }
        
        return assets.values.toList()
    }
    
    private suspend fun discoverFromKraken(): List<DiscoveredAsset> {
        val url = "https://api.kraken.com/0/public/Ticker"
        val json = fetchJson(url)?.getAsJsonObject("result") ?: return emptyList()
        
        val assets = mutableMapOf<String, DiscoveredAsset>()
        
        for ((pair, data) in json.entrySet()) {
            val ticker = data.asJsonObject
            
            // Kraken uses XBT for BTC, XDG for DOGE, etc.
            val baseAsset = krakenToStandardSymbol(pair.take(pair.length - 3))
            
            if (assets.containsKey(baseAsset)) continue
            
            val lastTrade = ticker.getAsJsonArray("c")?.get(0)?.asString?.toDoubleOrNull() ?: 0.0
            val volume = ticker.getAsJsonArray("v")?.get(1)?.asString?.toDoubleOrNull() ?: 0.0
            
            assets[baseAsset] = DiscoveredAsset(
                baseAsset = baseAsset,
                name = baseAsset,
                availableExchanges = mutableListOf("KRAKEN"),
                lastPrice = lastTrade,
                volume24h = volume * lastTrade,
                priceChange24h = 0.0  // Kraken doesn't provide in ticker
            )
        }
        
        return assets.values.toList()
    }
    
    private suspend fun discoverFromCoinbase(): List<DiscoveredAsset> {
        val url = "https://api.exchange.coinbase.com/products"
        val json = fetchJson(url) ?: return emptyList()
        
        if (!json.isJsonArray) return emptyList()
        
        val assets = mutableMapOf<String, DiscoveredAsset>()
        
        for (element in json.asJsonArray) {
            val product = element.asJsonObject
            val baseAsset = product.get("base_currency")?.asString ?: continue
            val status = product.get("status")?.asString
            
            if (status != "online") continue
            if (assets.containsKey(baseAsset)) continue
            
            assets[baseAsset] = DiscoveredAsset(
                baseAsset = baseAsset,
                name = product.get("base_name")?.asString ?: baseAsset,
                availableExchanges = mutableListOf("COINBASE"),
                lastPrice = 0.0,  // Would need separate ticker call
                volume24h = 0.0,
                priceChange24h = 0.0
            )
        }
        
        return assets.values.toList()
    }
    
    private suspend fun fetchBinanceTicker(symbol: String): BinanceTickerData? {
        // Try common quote currencies
        for (quote in listOf("USDT", "BUSD", "USD", "BTC")) {
            val pair = "$symbol$quote"
            val url = "$BINANCE_BASE/api/v3/ticker/24hr?symbol=$pair"
            
            try {
                val json = fetchJson(url)?.asJsonObject ?: continue
                
                return BinanceTickerData(
                    symbol = symbol,
                    name = symbol,
                    lastPrice = json.get("lastPrice")?.asString?.toDoubleOrNull() ?: 0.0,
                    volume24h = json.get("quoteVolume")?.asString?.toDoubleOrNull() ?: 0.0,
                    priceChange24h = json.get("priceChangePercent")?.asString?.toDoubleOrNull() ?: 0.0,
                    exchanges = listOf("BINANCE")
                )
            } catch (e: Exception) {
                // Try next quote currency
            }
        }
        
        return null
    }
    
    private suspend fun fetchBinancePairs(): List<TradingPairInfo> {
        val url = "$BINANCE_BASE/api/v3/exchangeInfo"
        val json = fetchJson(url)?.asJsonObject ?: return emptyList()
        
        val symbols = json.getAsJsonArray("symbols") ?: return emptyList()
        
        return symbols.mapNotNull { element ->
            val symbol = element.asJsonObject
            val status = symbol.get("status")?.asString
            
            if (status != "TRADING") return@mapNotNull null
            
            TradingPairInfo(
                symbol = symbol.get("symbol")?.asString ?: return@mapNotNull null,
                baseAsset = symbol.get("baseAsset")?.asString ?: "",
                quoteAsset = symbol.get("quoteAsset")?.asString ?: "",
                exchange = "BINANCE",
                minQuantity = symbol.getAsJsonArray("filters")
                    ?.firstOrNull { it.asJsonObject.get("filterType")?.asString == "LOT_SIZE" }
                    ?.asJsonObject?.get("minQty")?.asString?.toDoubleOrNull() ?: 0.0,
                minNotional = symbol.getAsJsonArray("filters")
                    ?.firstOrNull { it.asJsonObject.get("filterType")?.asString == "NOTIONAL" }
                    ?.asJsonObject?.get("minNotional")?.asString?.toDoubleOrNull() ?: 0.0
            )
        }
    }
    
    // =========================================================================
    // DEFILLAMA API
    // =========================================================================
    
    private suspend fun fetchDeFiLlamaProtocols(): List<DeFiLlamaProtocol> {
        enforceDefiLlamaRateLimit()
        
        val url = "$DEFILLAMA_BASE/protocols"
        val json = fetchJson(url) ?: return emptyList()
        
        if (!json.isJsonArray) return emptyList()
        
        val protocols = json.asJsonArray.mapNotNull { element ->
            try {
                val obj = element.asJsonObject
                DeFiLlamaProtocol(
                    name = obj.get("name")?.asString ?: return@mapNotNull null,
                    symbol = obj.get("symbol")?.asString,
                    tvl = obj.get("tvl")?.asDouble,
                    change_1d = obj.get("change_1d")?.asDouble,
                    change_7d = obj.get("change_7d")?.asDouble,
                    mcap = obj.get("mcap")?.asDouble,
                    category = obj.get("category")?.asString,
                    chains = obj.getAsJsonArray("chains")?.map { it.asString } ?: emptyList()
                )
            } catch (e: Exception) {
                null
            }
        }
        
        // Cache protocols
        protocols.forEach { protocol ->
            protocol.symbol?.let { protocolCache[it.uppercase()] = protocol }
        }
        
        Log.i(TAG, "Fetched ${protocols.size} protocols from DeFiLlama")
        return protocols
    }
    
    private suspend fun fetchDeFiLlamaChains(): List<DeFiLlamaChain> {
        enforceDefiLlamaRateLimit()
        
        val url = "$DEFILLAMA_BASE/v2/chains"
        val json = fetchJson(url) ?: return emptyList()
        
        if (!json.isJsonArray) return emptyList()
        
        val chains = json.asJsonArray.mapNotNull { element ->
            try {
                val obj = element.asJsonObject
                DeFiLlamaChain(
                    name = obj.get("name")?.asString ?: return@mapNotNull null,
                    tvl = obj.get("tvl")?.asDouble ?: 0.0,
                    tokenSymbol = obj.get("tokenSymbol")?.asString,
                    chainId = obj.get("chainId")?.asInt
                )
            } catch (e: Exception) {
                null
            }
        }
        
        // Cache chains by token symbol
        chains.forEach { chain ->
            chain.tokenSymbol?.let { chainCache[it.uppercase()] = chain }
        }
        
        Log.i(TAG, "Fetched ${chains.size} chains from DeFiLlama")
        return chains
    }
    
    private fun findMatchingProtocol(symbol: String, protocols: List<DeFiLlamaProtocol>): DeFiLlamaProtocol? {
        val symbolUpper = symbol.uppercase()
        
        // Exact match first
        protocols.find { it.symbol?.uppercase() == symbolUpper }?.let { return it }
        
        // Name match
        protocols.find { it.name.uppercase() == symbolUpper }?.let { return it }
        
        // Partial match
        protocols.find { 
            it.symbol?.uppercase()?.contains(symbolUpper) == true ||
            it.name.uppercase().contains(symbolUpper)
        }?.let { return it }
        
        return null
    }
    
    private fun findMatchingChain(symbol: String, chains: List<DeFiLlamaChain>): DeFiLlamaChain? {
        val symbolUpper = symbol.uppercase()
        
        // Token symbol match
        chains.find { it.tokenSymbol?.uppercase() == symbolUpper }?.let { return it }
        
        // Name match
        chains.find { it.name.uppercase() == symbolUpper }?.let { return it }
        
        return null
    }
    
    // =========================================================================
    // COINGECKO API (Rate-limited)
    // =========================================================================
    
    private suspend fun searchCoinGecko(query: String, limit: Int): List<AssetSearchResult> {
        enforceCoingeckoRateLimit()
        
        val url = "$COINGECKO_BASE/search?query=$query"
        val json = fetchJson(url)?.asJsonObject ?: return emptyList()
        
        val coins = json.getAsJsonArray("coins") ?: return emptyList()
        
        return coins.take(limit).mapNotNull { element ->
            try {
                val coin = element.asJsonObject
                AssetSearchResult(
                    symbol = coin.get("symbol")?.asString?.uppercase() ?: return@mapNotNull null,
                    name = coin.get("name")?.asString ?: "",
                    marketCap = coin.get("market_cap_rank")?.asInt?.toDouble(),
                    source = "coingecko"
                )
            } catch (e: Exception) {
                null
            }
        }
    }
    
    // =========================================================================
    // HELPERS
    // =========================================================================
    
    private suspend fun fetchJson(url: String): com.google.gson.JsonElement? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                val response = httpClient.newCall(request).execute()
                
                if (response.isSuccessful) {
                    response.body?.string()?.let { JsonParser.parseString(it) }
                } else {
                    Log.w(TAG, "HTTP ${response.code} for $url")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch $url", e)
                null
            }
        }
    }
    
    private fun extractBaseAsset(symbol: String): String? {
        val quoteAssets = listOf("USDT", "BUSD", "USDC", "USD", "BTC", "ETH", "BNB", "EUR", "GBP", "AUD")
        
        for (quote in quoteAssets) {
            if (symbol.endsWith(quote)) {
                val base = symbol.dropLast(quote.length)
                if (base.isNotEmpty()) return base
            }
        }
        
        return null
    }
    
    private fun krakenToStandardSymbol(krakenSymbol: String): String {
        return when (krakenSymbol.uppercase()) {
            "XBT" -> "BTC"
            "XDG" -> "DOGE"
            "XXBT" -> "BTC"
            "XETH" -> "ETH"
            "XXRP" -> "XRP"
            "XLTC" -> "LTC"
            else -> krakenSymbol.removePrefix("X").removePrefix("Z")
        }
    }
    
    private fun inferCategory(symbol: String): String {
        return when {
            symbol in STABLECOINS -> "Stablecoin"
            symbol in LAYER2_TOKENS -> "Layer 2"
            symbol in DEFI_TOKENS -> "DeFi"
            symbol in MEME_TOKENS -> "Meme"
            symbol in AI_TOKENS -> "AI"
            symbol in GAMING_TOKENS -> "Gaming"
            symbol in LAYER1_TOKENS -> "Layer 1"
            else -> "Other"
        }
    }
    
    private fun estimateMarketCap(data: BinanceTickerData?): Double? {
        // Without circulating supply, we can't calculate market cap
        // This would need CoinGecko or similar
        return null
    }
    
    private suspend fun enforceCoingeckoRateLimit() {
        coingeckoMutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastCoingeckoCall.get()
            val minInterval = 60_000L / COINGECKO_CALLS_PER_MINUTE
            
            if (elapsed < minInterval) {
                delay(minInterval - elapsed)
            }
            
            lastCoingeckoCall.set(System.currentTimeMillis())
        }
    }
    
    private suspend fun enforceDefiLlamaRateLimit() {
        defillamaMutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastDefillamaCall.get()
            val minInterval = 60_000L / DEFILLAMA_CALLS_PER_MINUTE
            
            if (elapsed < minInterval) {
                delay(minInterval - elapsed)
            }
            
            lastDefillamaCall.set(System.currentTimeMillis())
        }
    }
    
    // =========================================================================
    // TOKEN CATEGORIES (For inference when DeFiLlama doesn't have data)
    // =========================================================================
    
    private val LAYER1_TOKENS = setOf(
        "BTC", "ETH", "SOL", "ADA", "AVAX", "DOT", "ATOM", "NEAR", "FTM", "ALGO",
        "XTZ", "EGLD", "HBAR", "ICP", "FLOW", "KAVA", "ONE", "CELO", "ROSE", "MINA",
        "SUI", "APT", "SEI", "INJ", "TIA", "OSMO", "RUNE"
    )
    
    private val LAYER2_TOKENS = setOf(
        "MATIC", "ARB", "OP", "IMX", "LRC", "METIS", "BOBA", "ZK", "STRK", "MANTA",
        "BLAST", "SCROLL", "LINEA", "BASE", "ZKSYNC", "STARKNET", "MODE"
    )
    
    private val DEFI_TOKENS = setOf(
        "UNI", "AAVE", "LINK", "MKR", "SNX", "COMP", "CRV", "SUSHI", "YFI", "1INCH",
        "BAL", "DYDX", "GMX", "GNS", "PERP", "LQTY", "SPELL", "CVX", "FXS", "PENDLE",
        "RDNT", "JOE", "CAKE", "VELO", "AERO"
    )
    
    private val MEME_TOKENS = setOf(
        "DOGE", "SHIB", "PEPE", "FLOKI", "BONK", "WIF", "MEME", "TURBO", "LADYS",
        "WOJAK", "MILADY", "BITCOIN", "COQ", "MYRO", "POPCAT", "MEW", "BRETT"
    )
    
    private val AI_TOKENS = setOf(
        "FET", "AGIX", "OCEAN", "RNDR", "TAO", "ARKM", "PRIME", "AKT", "NMR", "GRT",
        "CTXC", "DBC", "ALI", "CGPT", "RSS3", "PAAL", "0X0"
    )
    
    private val GAMING_TOKENS = setOf(
        "AXS", "SAND", "MANA", "GALA", "ENJ", "IMX", "MAGIC", "PRIME", "BEAM", "PIXEL",
        "PORTAL", "SUPER", "YGG", "PYR", "GODS", "ILV", "ALICE"
    )
}

// =========================================================================
// DATA CLASSES
// =========================================================================

data class DiscoveredAsset(
    val baseAsset: String,
    val name: String,
    val availableExchanges: MutableList<String>,
    val lastPrice: Double,
    val volume24h: Double,
    val priceChange24h: Double
)

data class EnrichedAsset(
    val baseAsset: String,
    val name: String,
    val availableExchanges: List<String>,
    val lastPrice: Double,
    val volume24h: Double,
    val priceChange24h: Double,
    // DeFiLlama data
    val tvlUsd: Double?,
    val tvlChange24h: Double?,
    val tvlChange7d: Double?,
    val category: String,
    val chains: List<String>,
    val mcapTvlRatio: Double?,
    // Chain data
    val chainTvl: Double?,
    val isLayer1: Boolean,
    val isLayer2: Boolean,
    val isStablecoin: Boolean
)

data class AssetSearchResult(
    val symbol: String,
    val name: String,
    val marketCap: Double?,
    val source: String
)

data class TradingPairInfo(
    val symbol: String,
    val baseAsset: String,
    val quoteAsset: String,
    val exchange: String,
    val minQuantity: Double,
    val minNotional: Double
)

data class DiscoveryProgress(
    val currentExchange: String = "",
    val totalAssets: Int = 0,
    val enrichedCount: Int = 0,
    val status: String = "Idle"
)

data class DeFiLlamaProtocol(
    val name: String,
    val symbol: String?,
    val tvl: Double?,
    val change_1d: Double?,
    val change_7d: Double?,
    val mcap: Double?,
    val category: String?,
    val chains: List<String>
)

data class DeFiLlamaChain(
    val name: String,
    val tvl: Double,
    val tokenSymbol: String?,
    val chainId: Int?
)

data class BinanceTickerData(
    val symbol: String,
    val name: String,
    val lastPrice: Double,
    val volume24h: Double,
    val priceChange24h: Double,
    val exchanges: List<String>
)

data class CachedAsset(
    val data: AssetMarketData,
    val timestamp: Long
)
