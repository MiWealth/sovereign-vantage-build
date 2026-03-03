package com.miwealth.sovereignvantage.core.trading.assets

/**
 * Asset Registry - Central Asset Management
 * 
 * Sovereign Vantage: Arthur Edition V5.17.0
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * Central registry for all tradable assets. Supports dynamic loading from
 * exchanges, filtering, searching, and subscription to asset changes.
 * 
 * DESIGN RATIONALE:
 * - Singleton object for global access across trading system
 * - Flow-based observation for reactive UI updates
 * - Thread-safe concurrent map for high-frequency access
 * - Bootstrap symbols ensure basic functionality without network
 * - Exchange loaders (Binance, Kraken, Coinbase) integrated
 * 
 * USAGE:
 *   // Get all DeFi tokens
 *   val defiTokens = AssetRegistry.getByCategory(AssetCategory.DEFI)
 *   
 *   // Search for assets
 *   val results = AssetRegistry.search("BTC")
 *   
 *   // Load from exchange
 *   AssetRegistry.loadFromExchange("Binance")
 *   
 *   // Observe changes
 *   AssetRegistry.assets.collect { allAssets -> updateUI(allAssets) }
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 */



import com.miwealth.sovereignvantage.core.AssetType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Central registry for all tradable assets in Sovereign Vantage.
 * 
 * Maintains an in-memory cache of asset specifications loaded from exchanges
 * or configured manually. Provides efficient lookup, filtering, and search.
 */
object AssetRegistry {
    
    // ============================================================================
    // STATE
    // ============================================================================
    
    /** Primary storage - symbol to asset mapping */
    private val assetMap = ConcurrentHashMap<String, TradableAsset>()
    
    /** Observable state for UI binding */
    private val _assets = MutableStateFlow<List<TradableAsset>>(emptyList())
    val assets: StateFlow<List<TradableAsset>> = _assets.asStateFlow()
    
    /** Loading state */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    /** Last refresh timestamp per exchange */
    private val lastRefresh = ConcurrentHashMap<String, Instant>()
    
    /** Mutex for bulk operations */
    private val mutex = Mutex()
    
    // ============================================================================
    // INITIALIZATION
    // ============================================================================
    
    init {
        // Bootstrap with essential symbols for offline/startup use
        bootstrapAssets()
    }
    
    /**
     * Initialize registry with bootstrap assets.
     * Called automatically on first access.
     */
    private fun bootstrapAssets() {
        val bootstrap = buildBootstrapAssets()
        bootstrap.forEach { asset ->
            assetMap[asset.symbol] = asset
        }
        publishChanges()
    }
    
    /**
     * Build list of essential bootstrap assets.
     * Uses BootstrapAssets for comprehensive 500+ asset coverage including:
     * - Full crypto coverage (majors, L1, L2, DeFi, meme, NFT, AI, privacy, exchange tokens)
     * - Complete FOREX (majors, minors, exotics, Scandinavian, Asian, LATAM, EMEA)
     * - Precious metals (XAU, XAG, XPT, XPD)
     * - Industrial metals (XCU, XAL, XNI, XZN, XPB, XSN)
     * - Energy commodities (XTI, XBR, XNG)
     * - Agricultural commodities
     * - ETFs (indices, sectors, commodities, currencies, international)
     */
    private fun buildBootstrapAssets(): List<TradableAsset> = BootstrapAssets.buildAll()
    
    // ============================================================================
    // QUERY METHODS
    // ============================================================================
    
    /**
     * Get a specific asset by symbol.
     */
    fun get(symbol: String): TradableAsset? = assetMap[symbol]
    
    /**
     * Get asset or throw if not found.
     */
    fun require(symbol: String): TradableAsset =
        assetMap[symbol] ?: throw NoSuchElementException("Asset not found: $symbol")
    
    /**
     * Check if an asset exists.
     */
    fun contains(symbol: String): Boolean = assetMap.containsKey(symbol)
    
    /**
     * Get all registered assets.
     */
    fun getAll(): List<TradableAsset> = assetMap.values.toList()
    
    /**
     * Get assets by broad asset type.
     */
    fun getByType(type: AssetType): List<TradableAsset> =
        assetMap.values.filter { it.assetType == type }
    
    /**
     * Get assets by granular category.
     */
    fun getByCategory(category: AssetCategory): List<TradableAsset> =
        assetMap.values.filter { it.category == category }
    
    /**
     * Get assets by exchange.
     */
    fun getByExchange(exchange: String): List<TradableAsset> =
        assetMap.values.filter { it.exchange.equals(exchange, ignoreCase = true) }
    
    /**
     * Get assets by quote currency.
     */
    fun getByQuote(quoteAsset: String): List<TradableAsset> =
        assetMap.values.filter { it.quoteAsset.equals(quoteAsset, ignoreCase = true) }
    
    /**
     * Get scalping-enabled assets only.
     */
    fun getScalpingEnabled(): List<TradableAsset> =
        assetMap.values.filter { it.scalpingEnabled }
    
    /**
     * Get margin-enabled assets only.
     */
    fun getMarginEnabled(): List<TradableAsset> =
        assetMap.values.filter { it.marginEnabled }
    
    /**
     * Get assets within a maximum risk tier.
     */
    fun getByMaxRisk(maxTier: Int): List<TradableAsset> =
        assetMap.values.filter { it.category.riskTier <= maxTier }
    
    /**
     * Get assets currently open for trading.
     */
    fun getOpenMarkets(): List<TradableAsset> =
        assetMap.values.filter { it.isMarketOpen() }
    
    /**
     * Apply a filter to get matching assets.
     */
    fun filter(filter: AssetFilter): List<TradableAsset> =
        assetMap.values.filter { filter.matches(it) }
    
    /**
     * Search assets by query string.
     * Matches against symbol, base asset, quote asset, and category name.
     */
    fun search(query: String): List<TradableAsset> {
        if (query.isBlank()) return getAll()
        return assetMap.values.filter { it.matchesSearch(query) }
    }
    
    /**
     * Get distinct quote assets available.
     */
    fun getAvailableQuotes(): Set<String> =
        assetMap.values.map { it.quoteAsset }.toSet()
    
    /**
     * Get distinct exchanges.
     */
    fun getAvailableExchanges(): Set<String> =
        assetMap.values.map { it.exchange }.toSet()
    
    /**
     * Get count by category for UI display.
     */
    fun countByCategory(): Map<AssetCategory, Int> =
        assetMap.values.groupingBy { it.category }.eachCount()
    
    /**
     * Get count by asset type.
     */
    fun countByType(): Map<AssetType, Int> =
        assetMap.values.groupingBy { it.assetType }.eachCount()
    
    // ============================================================================
    // MUTATION METHODS
    // ============================================================================
    
    /**
     * Register a single asset.
     * Overwrites existing asset with same symbol.
     */
    suspend fun register(asset: TradableAsset) {
        mutex.withLock {
            assetMap[asset.symbol] = asset
            publishChanges()
        }
    }
    
    /**
     * Register multiple assets in bulk.
     */
    suspend fun registerAll(assets: List<TradableAsset>) {
        mutex.withLock {
            assets.forEach { asset ->
                assetMap[asset.symbol] = asset
            }
            publishChanges()
        }
    }
    
    /**
     * Remove an asset by symbol.
     */
    suspend fun unregister(symbol: String) {
        mutex.withLock {
            assetMap.remove(symbol)
            publishChanges()
        }
    }
    
    /**
     * Update an existing asset.
     * Returns false if asset doesn't exist.
     */
    suspend fun update(symbol: String, updater: (TradableAsset) -> TradableAsset): Boolean {
        mutex.withLock {
            val existing = assetMap[symbol] ?: return false
            assetMap[symbol] = updater(existing)
            publishChanges()
            return true
        }
    }
    
    /**
     * Update asset status (e.g., when trading halted).
     */
    suspend fun updateStatus(symbol: String, status: AssetStatus) {
        update(symbol) { it.copy(status = status, lastUpdated = Instant.now()) }
    }
    
    /**
     * Clear all assets and reload bootstrap.
     */
    suspend fun reset() {
        mutex.withLock {
            assetMap.clear()
            lastRefresh.clear()
            bootstrapAssets()
        }
    }
    
    // ============================================================================
    // EXCHANGE LOADING
    // ============================================================================
    
    /**
     * Load assets from an exchange using the appropriate AssetLoader.
     * 
     * @param exchangeName Name of exchange to load from (Binance, Kraken, Coinbase)
     * @param forceRefresh If true, refresh even if recently loaded
     * @return AssetLoadResult indicating success or failure
     */
    suspend fun loadFromExchange(
        exchangeName: String, 
        forceRefresh: Boolean = false
    ): AssetLoadResult {
        val normalizedName = exchangeName.uppercase()
        
        // Check if refresh is needed
        if (!forceRefresh && !isStale(normalizedName, java.time.Duration.ofHours(1))) {
            android.util.Log.d("AssetRegistry", 
                "$normalizedName cache is fresh, skipping load")
            return AssetLoadResult.Success(getByExchange(normalizedName))
        }
        
        // Get the loader
        val loader = AssetLoaderFactory.getLoader(normalizedName)
        if (loader == null) {
            android.util.Log.e("AssetRegistry", "No loader available for $normalizedName")
            return AssetLoadResult.Error("Unsupported exchange: $exchangeName")
        }
        
        return try {
            android.util.Log.i("AssetRegistry", "Loading assets from $normalizedName...")
            
            val assets = loader.fetchAllAssets()
            
            if (assets.isEmpty()) {
                android.util.Log.w("AssetRegistry", "No assets loaded from $normalizedName")
                return AssetLoadResult.Error("No assets returned from $normalizedName")
            }
            
            // Register the assets
            registerAll(assets)
            lastRefresh[normalizedName] = Instant.now()
            
            android.util.Log.i("AssetRegistry", 
                "Successfully loaded ${assets.size} assets from $normalizedName")
            
            AssetLoadResult.Success(assets)
            
        } catch (e: Exception) {
            android.util.Log.e("AssetRegistry", "Failed to load from $normalizedName: ${e.message}", e)
            AssetLoadResult.Error("Failed to load from $normalizedName: ${e.message}", e)
        }
    }
    
    /**
     * Load assets from multiple exchanges concurrently.
     * 
     * @param exchanges List of exchange names to load from
     * @param forceRefresh If true, refresh even if recently loaded
     * @return Map of exchange name to load result
     */
    suspend fun loadFromExchanges(
        exchanges: List<String>, 
        forceRefresh: Boolean = false
    ): Map<String, AssetLoadResult> {
        _isLoading.value = true
        return try {
            coroutineScope {
                exchanges.map { exchange ->
                    async {
                        exchange.uppercase() to loadFromExchange(exchange, forceRefresh)
                    }
                }.awaitAll().toMap()
            }
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Load assets from all supported exchanges.
     */
    suspend fun loadFromAllExchanges(forceRefresh: Boolean = false): Map<String, AssetLoadResult> {
        return loadFromExchanges(AssetLoaderFactory.supportedExchanges, forceRefresh)
    }
    
    /**
     * Check when an exchange was last refreshed.
     */
    fun getLastRefresh(exchange: String): Instant? = lastRefresh[exchange]
    
    /**
     * Check if exchange data is stale (older than threshold).
     */
    fun isStale(exchange: String, maxAge: java.time.Duration): Boolean {
        val last = lastRefresh[exchange] ?: return true
        return Instant.now().isAfter(last.plus(maxAge))
    }
    
    // ============================================================================
    // INTERNAL
    // ============================================================================
    
    /**
     * Publish current state to observers.
     */
    private fun publishChanges() {
        _assets.value = assetMap.values.toList()
    }
    
    /**
     * Get registry statistics for debugging/monitoring.
     */
    fun getStats(): RegistryStats = RegistryStats(
        totalAssets = assetMap.size,
        byType = countByType(),
        byCategory = countByCategory(),
        byExchange = assetMap.values.groupingBy { it.exchange }.eachCount(),
        scalpingEnabled = getScalpingEnabled().size,
        marginEnabled = getMarginEnabled().size,
        lastRefreshTimes = lastRefresh.toMap()
    )
}

/**
 * Registry statistics for monitoring and debugging.
 */
data class RegistryStats(
    val totalAssets: Int,
    val byType: Map<AssetType, Int>,
    val byCategory: Map<AssetCategory, Int>,
    val byExchange: Map<String, Int>,
    val scalpingEnabled: Int,
    val marginEnabled: Int,
    val lastRefreshTimes: Map<String, Instant>
)
