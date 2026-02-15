/*
 * Sovereign Vantage - Arthur Edition
 * AssetRepository.kt - Room DAO and caching layer for asset metadata
 * 
 * Copyright 2025-2026 MiWealth Pty Ltd
 * Creator: Mike Stahl
 * Co-Founder (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

package com.miwealth.sovereignvantage.data.repository

import androidx.room.*
import com.miwealth.sovereignvantage.data.models.AssetCategory
import com.miwealth.sovereignvantage.data.models.AssetSummary
import com.miwealth.sovereignvantage.data.models.CryptoAsset
import com.miwealth.sovereignvantage.data.models.MarketCapTier
import com.miwealth.sovereignvantage.data.models.TradableAsset
import com.miwealth.sovereignvantage.data.models.VolatilityTier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room DAO for crypto asset persistence.
 * Provides reactive queries via Flow for UI updates.
 */
@Dao
interface AssetDao {
    
    // ========== INSERT / UPDATE ==========
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(asset: CryptoAsset)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(assets: List<CryptoAsset>)
    
    @Update
    suspend fun update(asset: CryptoAsset)
    
    @Delete
    suspend fun delete(asset: CryptoAsset)
    
    // ========== QUERIES - SINGLE ASSET ==========
    
    @Query("SELECT * FROM crypto_assets WHERE symbol = :symbol")
    suspend fun getBySymbol(symbol: String): CryptoAsset?
    
    @Query("SELECT * FROM crypto_assets WHERE symbol = :symbol")
    fun observeBySymbol(symbol: String): Flow<CryptoAsset?>
    
    @Query("SELECT EXISTS(SELECT 1 FROM crypto_assets WHERE symbol = :symbol)")
    suspend fun exists(symbol: String): Boolean
    
    // ========== QUERIES - ALL ASSETS ==========
    
    @Query("SELECT * FROM crypto_assets ORDER BY symbol ASC")
    suspend fun getAll(): List<CryptoAsset>
    
    @Query("SELECT * FROM crypto_assets WHERE isActive = 1 ORDER BY symbol ASC")
    suspend fun getAllActive(): List<CryptoAsset>
    
    @Query("SELECT * FROM crypto_assets WHERE isActive = 1 ORDER BY symbol ASC")
    fun observeAllActive(): Flow<List<CryptoAsset>>
    
    @Query("SELECT COUNT(*) FROM crypto_assets")
    suspend fun count(): Int
    
    @Query("SELECT COUNT(*) FROM crypto_assets WHERE isActive = 1")
    suspend fun countActive(): Int
    
    // ========== QUERIES - BY CATEGORY ==========
    
    @Query("SELECT * FROM crypto_assets WHERE category = :category AND isActive = 1 ORDER BY symbol ASC")
    suspend fun getByCategory(category: AssetCategory): List<CryptoAsset>
    
    @Query("SELECT * FROM crypto_assets WHERE category = :category AND isActive = 1 ORDER BY symbol ASC")
    fun observeByCategory(category: AssetCategory): Flow<List<CryptoAsset>>
    
    @Query("SELECT * FROM crypto_assets WHERE category IN (:categories) AND isActive = 1 ORDER BY symbol ASC")
    suspend fun getByCategories(categories: List<AssetCategory>): List<CryptoAsset>
    
    // ========== QUERIES - BY EXCHANGE ==========
    
    @Query("SELECT * FROM crypto_assets WHERE onKraken = 1 AND isActive = 1 ORDER BY symbol ASC")
    suspend fun getKrakenAssets(): List<CryptoAsset>
    
    @Query("SELECT * FROM crypto_assets WHERE onBinance = 1 AND isActive = 1 ORDER BY symbol ASC")
    suspend fun getBinanceAssets(): List<CryptoAsset>
    
    @Query("SELECT * FROM crypto_assets WHERE onBybit = 1 AND isActive = 1 ORDER BY symbol ASC")
    suspend fun getBybitAssets(): List<CryptoAsset>
    
    @Query("SELECT * FROM crypto_assets WHERE onCoinbase = 1 AND isActive = 1 ORDER BY symbol ASC")
    suspend fun getCoinbaseAssets(): List<CryptoAsset>
    
    // ========== QUERIES - SPECIAL FILTERS ==========
    
    @Query("SELECT * FROM crypto_assets WHERE canShortFutures = 1 OR canShortMargin = 1 AND isActive = 1 ORDER BY symbol ASC")
    suspend fun getShortableAssets(): List<CryptoAsset>
    
    @Query("SELECT * FROM crypto_assets WHERE isNewLaunch = 1 AND isActive = 1 ORDER BY addedAt DESC")
    suspend fun getNewLaunches(): List<CryptoAsset>
    
    @Query("SELECT * FROM crypto_assets WHERE isStablecoin = 1 ORDER BY symbol ASC")
    suspend fun getStablecoins(): List<CryptoAsset>
    
    @Query("SELECT * FROM crypto_assets WHERE isPrivacyCoin = 1 AND isActive = 1 ORDER BY symbol ASC")
    suspend fun getPrivacyCoins(): List<CryptoAsset>
    
    @Query("SELECT * FROM crypto_assets WHERE marketCapTier = :tier AND isActive = 1 ORDER BY symbol ASC")
    suspend fun getByMarketCapTier(tier: MarketCapTier): List<CryptoAsset>
    
    @Query("SELECT * FROM crypto_assets WHERE volatilityTier = :tier AND isActive = 1 ORDER BY symbol ASC")
    suspend fun getByVolatilityTier(tier: VolatilityTier): List<CryptoAsset>
    
    // ========== QUERIES - SUMMARIES (Lightweight) ==========
    
    @Query("SELECT symbol, name, category, isActive FROM crypto_assets ORDER BY symbol ASC")
    suspend fun getAllSummaries(): List<AssetSummary>
    
    @Query("SELECT symbol, name, category, isActive FROM crypto_assets WHERE isActive = 1 ORDER BY symbol ASC")
    fun observeActiveSummaries(): Flow<List<AssetSummary>>
    
    @Query("SELECT symbol FROM crypto_assets WHERE isActive = 1")
    suspend fun getAllActiveSymbols(): List<String>
    
    // ========== QUERIES - SEARCH ==========
    
    @Query("""
        SELECT * FROM crypto_assets 
        WHERE isActive = 1 
        AND (symbol LIKE '%' || :query || '%' OR name LIKE '%' || :query || '%')
        ORDER BY 
            CASE WHEN symbol = :query THEN 0
                 WHEN symbol LIKE :query || '%' THEN 1
                 WHEN name LIKE :query || '%' THEN 2
                 ELSE 3
            END,
            symbol ASC
        LIMIT :limit
    """)
    suspend fun search(query: String, limit: Int = 20): List<CryptoAsset>
    
    // ========== BULK OPERATIONS ==========
    
    @Query("UPDATE crypto_assets SET isActive = :isActive WHERE symbol IN (:symbols)")
    suspend fun setActiveStatus(symbols: List<String>, isActive: Boolean)
    
    @Query("UPDATE crypto_assets SET isNewLaunch = 0 WHERE addedAt < :cutoffTime")
    suspend fun clearOldNewLaunchFlags(cutoffTime: Long)
    
    @Query("DELETE FROM crypto_assets WHERE symbol NOT IN (:keepSymbols)")
    suspend fun deleteExcept(keepSymbols: List<String>)
}

/**
 * Repository layer with in-memory caching for trading-speed lookups.
 * 
 * Cache strategy:
 * - Full cache loaded on first access
 * - Cache invalidated on writes
 * - Individual lookups hit cache first
 * - Flows bypass cache for real-time UI updates
 */
@Singleton
class AssetRepository @Inject constructor(
    private val assetDao: AssetDao
) {
    // In-memory cache for trading-speed lookups
    private val cache = ConcurrentHashMap<String, CryptoAsset>()
    private val cacheMutex = Mutex()
    private var cacheLoaded = false
    
    // ========== CACHE MANAGEMENT ==========
    
    /**
     * Ensure cache is loaded. Call at app startup.
     */
    suspend fun ensureCacheLoaded() {
        if (cacheLoaded) return
        cacheMutex.withLock {
            if (cacheLoaded) return@withLock
            val assets = assetDao.getAllActive()
            cache.clear()
            assets.forEach { cache[it.symbol] = it }
            cacheLoaded = true
        }
    }
    
    /**
     * Force cache refresh. Call after bulk updates.
     */
    suspend fun refreshCache() {
        cacheMutex.withLock {
            val assets = assetDao.getAllActive()
            cache.clear()
            assets.forEach { cache[it.symbol] = it }
            cacheLoaded = true
        }
    }
    
    /**
     * Invalidate cache. Next access will reload.
     */
    fun invalidateCache() {
        cache.clear()
        cacheLoaded = false
    }
    
    // ========== FAST LOOKUPS (Cache-first) ==========
    
    /**
     * Get asset by symbol. Returns from cache if available.
     * This is the primary lookup used during trading.
     */
    suspend fun getBySymbol(symbol: String): CryptoAsset? {
        ensureCacheLoaded()
        return cache[symbol.uppercase()] ?: assetDao.getBySymbol(symbol.uppercase())
    }
    
    /**
     * Get asset as TradableAsset with risk parameters applied.
     */
    suspend fun getTradableAsset(
        symbol: String,
        riskMultiplier: Double = 1.0,
        preferredExchange: String? = null
    ): TradableAsset? {
        val asset = getBySymbol(symbol) ?: return null
        return TradableAsset.from(asset, riskMultiplier, preferredExchange)
    }
    
    /**
     * Check if symbol exists.
     */
    suspend fun exists(symbol: String): Boolean {
        ensureCacheLoaded()
        return cache.containsKey(symbol.uppercase()) || assetDao.exists(symbol.uppercase())
    }
    
    /**
     * Get all active symbols (for pair loading).
     */
    suspend fun getAllActiveSymbols(): List<String> {
        ensureCacheLoaded()
        return cache.keys.toList()
    }
    
    // ========== WRITE OPERATIONS ==========
    
    /**
     * Insert or update single asset.
     */
    suspend fun save(asset: CryptoAsset) {
        val normalised = asset.copy(symbol = asset.symbol.uppercase())
        assetDao.insert(normalised)
        if (normalised.isActive) {
            cache[normalised.symbol] = normalised
        } else {
            cache.remove(normalised.symbol)
        }
    }
    
    /**
     * Bulk insert assets. Used by seeder.
     */
    suspend fun saveAll(assets: List<CryptoAsset>) {
        val normalised = assets.map { it.copy(symbol = it.symbol.uppercase()) }
        assetDao.insertAll(normalised)
        refreshCache()
    }
    
    /**
     * Soft delete - marks as inactive.
     */
    suspend fun deactivate(symbol: String) {
        val asset = assetDao.getBySymbol(symbol.uppercase())
        if (asset != null) {
            assetDao.update(asset.copy(isActive = false, updatedAt = System.currentTimeMillis()))
            cache.remove(symbol.uppercase())
        }
    }
    
    /**
     * Reactivate a deactivated asset.
     */
    suspend fun activate(symbol: String) {
        val asset = assetDao.getBySymbol(symbol.uppercase())
        if (asset != null) {
            val updated = asset.copy(isActive = true, updatedAt = System.currentTimeMillis())
            assetDao.update(updated)
            cache[symbol.uppercase()] = updated
        }
    }
    
    // ========== QUERY OPERATIONS (DB direct) ==========
    
    /**
     * Get all assets by category.
     */
    suspend fun getByCategory(category: AssetCategory): List<CryptoAsset> {
        return assetDao.getByCategory(category)
    }
    
    /**
     * Get assets available on specific exchange.
     */
    suspend fun getForExchange(exchange: String): List<CryptoAsset> {
        return when (exchange.lowercase()) {
            "kraken" -> assetDao.getKrakenAssets()
            "binance" -> assetDao.getBinanceAssets()
            "bybit" -> assetDao.getBybitAssets()
            "coinbase" -> assetDao.getCoinbaseAssets()
            else -> emptyList()
        }
    }
    
    /**
     * Get all assets that can be shorted.
     */
    suspend fun getShortableAssets(): List<CryptoAsset> {
        return assetDao.getShortableAssets()
    }
    
    /**
     * Get newly launched assets (for New Launch Strategy).
     */
    suspend fun getNewLaunches(): List<CryptoAsset> {
        return assetDao.getNewLaunches()
    }
    
    /**
     * Search assets by symbol or name.
     */
    suspend fun search(query: String, limit: Int = 20): List<CryptoAsset> {
        return assetDao.search(query.uppercase(), limit)
    }
    
    /**
     * Get count of active assets.
     */
    suspend fun countActive(): Int {
        return assetDao.countActive()
    }
    
    // ========== REACTIVE FLOWS (For UI) ==========
    
    /**
     * Observe all active assets. For portfolio/watchlist screens.
     */
    fun observeAllActive(): Flow<List<CryptoAsset>> {
        return assetDao.observeAllActive()
    }
    
    /**
     * Observe assets by category. For category filter UI.
     */
    fun observeByCategory(category: AssetCategory): Flow<List<CryptoAsset>> {
        return assetDao.observeByCategory(category)
    }
    
    /**
     * Observe lightweight summaries. For asset picker.
     */
    fun observeActiveSummaries(): Flow<List<AssetSummary>> {
        return assetDao.observeActiveSummaries()
    }
    
    /**
     * Observe single asset. For asset detail screen.
     */
    fun observeBySymbol(symbol: String): Flow<CryptoAsset?> {
        return assetDao.observeBySymbol(symbol.uppercase())
    }
    
    // ========== MAINTENANCE ==========
    
    /**
     * Clear new launch flags for assets older than specified time.
     * Typically called daily - new launches older than 7 days lose the flag.
     */
    suspend fun clearOldNewLaunchFlags(maxAgeDays: Int = 7) {
        val cutoff = System.currentTimeMillis() - (maxAgeDays * 24 * 60 * 60 * 1000L)
        assetDao.clearOldNewLaunchFlags(cutoff)
        refreshCache()
    }
    
    /**
     * Check if database needs seeding.
     */
    suspend fun needsSeeding(): Boolean {
        return assetDao.count() == 0
    }
}
