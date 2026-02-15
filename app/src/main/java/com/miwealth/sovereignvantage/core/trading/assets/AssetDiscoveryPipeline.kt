/**
 * ASSET DISCOVERY PIPELINE
 * 
 * Sovereign Vantage: Arthur Edition V5.5.70
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * Wires together the asset discovery → risk assignment → registry pipeline:
 * 
 * 1. UniversalAssetDiscovery - Discovers assets from exchanges + DeFiLlama
 * 2. DynamicRiskAssigner - Assigns risk parameters based on market data
 * 3. AssetRegistry - Stores tradable assets for TradingCoordinator
 * 
 * This enables Sovereign Vantage to trade ANY cryptocurrency dynamically,
 * not just those in the curated asset catalog.
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */
package com.miwealth.sovereignvantage.core.trading.assets

import android.content.Context
import android.util.Log
import com.miwealth.sovereignvantage.core.exchange.ai.AIConnectionManager
import com.miwealth.sovereignvantage.data.models.AssetCategory
import com.miwealth.sovereignvantage.data.models.MarketCapTier
import com.miwealth.sovereignvantage.data.models.VolatilityTier
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant

/**
 * Pipeline state for UI observation.
 */
data class PipelineState(
    val status: PipelineStatus = PipelineStatus.IDLE,
    val discoveredCount: Int = 0,
    val enrichedCount: Int = 0,
    val registeredCount: Int = 0,
    val currentAsset: String? = null,
    val lastRun: Instant? = null,
    val error: String? = null
)

enum class PipelineStatus {
    IDLE,
    DISCOVERING,
    ENRICHING,
    ASSIGNING_RISK,
    REGISTERING,
    COMPLETE,
    ERROR
}

/**
 * Pipeline events for logging and analytics.
 */
sealed class PipelineEvent {
    data class DiscoveryStarted(val exchanges: List<String>) : PipelineEvent()
    data class AssetsDiscovered(val count: Int) : PipelineEvent()
    data class EnrichmentComplete(val count: Int) : PipelineEvent()
    data class RiskAssigned(val symbol: String, val tier: MarketCapTier) : PipelineEvent()
    data class AssetsRegistered(val count: Int) : PipelineEvent()
    data class PipelineComplete(val totalAssets: Int, val durationMs: Long) : PipelineEvent()
    data class PipelineError(val message: String, val exception: Throwable?) : PipelineEvent()
}

/**
 * Configuration for the pipeline.
 */
data class PipelineConfig(
    /** Exchanges to discover from */
    val exchanges: List<String> = listOf("binance", "kraken", "coinbase"),
    
    /** Enable DeFiLlama enrichment (adds TVL, protocol data) */
    val enableDeFiLlamaEnrichment: Boolean = true,
    
    /** Enable CoinGecko enrichment (market cap, circulating supply) */
    val enableCoinGeckoEnrichment: Boolean = false,  // Rate limited
    
    /** Maximum assets to process (0 = unlimited) */
    val maxAssets: Int = 0,
    
    /** Skip stablecoins for risk assignment */
    val skipStablecoins: Boolean = true,
    
    /** Minimum 24h volume to include (USD) */
    val minVolume24h: Double = 100_000.0,
    
    /** Auto-run interval in milliseconds (0 = disabled) */
    val autoRunIntervalMs: Long = 0
)

/**
 * Central pipeline coordinating asset discovery → risk assignment → registry.
 * 
 * Usage:
 * ```kotlin
 * val pipeline = AssetDiscoveryPipeline(context)
 * 
 * // Run full pipeline
 * val result = pipeline.runFullPipeline(PipelineConfig(
 *     exchanges = listOf("binance", "kraken"),
 *     enableDeFiLlamaEnrichment = true
 * ))
 * 
 * // Observe state
 * pipeline.state.collect { state ->
 *     updateUI(state)
 * }
 * ```
 */
class AssetDiscoveryPipeline(
    private val context: Context
) {
    companion object {
        private const val TAG = "AssetDiscoveryPipeline"
        
        // Known stablecoins to skip for risk assignment
        private val STABLECOINS = setOf(
            "USDT", "USDC", "BUSD", "DAI", "TUSD", "USDP", "GUSD", "FRAX",
            "LUSD", "SUSD", "MIM", "USDD", "EURC", "EURT", "PYUSD", "FDUSD"
        )
        
        @Volatile
        private var INSTANCE: AssetDiscoveryPipeline? = null
        
        fun getInstance(context: Context): AssetDiscoveryPipeline {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AssetDiscoveryPipeline(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Components
    private val discovery = UniversalAssetDiscovery(context)
    private val riskAssigner = DynamicRiskAssigner()
    
    // State
    private val _state = MutableStateFlow(PipelineState())
    val state: StateFlow<PipelineState> = _state.asStateFlow()
    
    private val _events = MutableSharedFlow<PipelineEvent>(replay = 0, extraBufferCapacity = 64)
    val events: SharedFlow<PipelineEvent> = _events.asSharedFlow()
    
    // Auto-run job
    private var autoRunJob: Job? = null
    
    // =========================================================================
    // PUBLIC API
    // =========================================================================
    
    /**
     * Run the full discovery → risk → registry pipeline.
     * 
     * @param config Pipeline configuration
     * @return Number of assets registered
     */
    suspend fun runFullPipeline(config: PipelineConfig = PipelineConfig()): Int {
        val startTime = System.currentTimeMillis()
        
        return try {
            Log.i(TAG, "Starting pipeline with exchanges: ${config.exchanges}")
            
            // Step 1: Discover assets from exchanges
            updateState { it.copy(status = PipelineStatus.DISCOVERING) }
            emitEvent(PipelineEvent.DiscoveryStarted(config.exchanges))
            
            val discovered = discovery.discoverFromExchanges(config.exchanges)
            Log.i(TAG, "Discovered ${discovered.size} assets")
            
            updateState { it.copy(discoveredCount = discovered.size) }
            emitEvent(PipelineEvent.AssetsDiscovered(discovered.size))
            
            // Step 2: Enrich with DeFiLlama (optional)
            val enriched = if (config.enableDeFiLlamaEnrichment) {
                updateState { it.copy(status = PipelineStatus.ENRICHING) }
                val result = discovery.enrichWithDeFiLlama(discovered)
                updateState { it.copy(enrichedCount = result.size) }
                emitEvent(PipelineEvent.EnrichmentComplete(result.size))
                result
            } else {
                // Convert to EnrichedAsset without DeFiLlama data
                discovered.map { asset ->
                    EnrichedAsset(
                        baseAsset = asset.baseAsset,
                        name = asset.name,
                        availableExchanges = asset.availableExchanges,
                        lastPrice = asset.lastPrice,
                        volume24h = asset.volume24h,
                        priceChange24h = asset.priceChange24h,
                        tvlUsd = null,
                        tvlChange24h = null,
                        tvlChange7d = null,
                        category = inferCategory(asset.baseAsset),
                        chains = emptyList(),
                        mcapTvlRatio = null,
                        chainTvl = null,
                        isLayer1 = false,
                        isLayer2 = false,
                        isStablecoin = asset.baseAsset in STABLECOINS
                    )
                }
            }
            
            // Step 3: Filter assets
            val filtered = enriched.filter { asset ->
                // Apply volume filter
                (asset.volume24h ?: 0.0) >= config.minVolume24h &&
                // Skip stablecoins if configured
                !(config.skipStablecoins && asset.isStablecoin) &&
                // Apply max assets limit
                (config.maxAssets == 0 || enriched.indexOf(asset) < config.maxAssets)
            }
            
            Log.i(TAG, "Filtered to ${filtered.size} assets")
            
            // Step 4: Assign risk parameters and register
            updateState { it.copy(status = PipelineStatus.ASSIGNING_RISK) }
            
            var registeredCount = 0
            for (asset in filtered) {
                try {
                    updateState { it.copy(currentAsset = asset.baseAsset) }
                    
                    // Create market data for risk assignment
                    val marketData = AssetMarketData(
                        symbol = asset.baseAsset,
                        marketCapUsd = estimateMarketCap(asset),
                        volume24hUsd = asset.volume24h,
                        priceChangePercent24h = asset.priceChange24h,
                        availableExchanges = asset.availableExchanges.toList()
                    )
                    
                    // Assign risk parameters
                    val riskProfile = riskAssigner.assignRiskParameters(marketData)
                    emitEvent(PipelineEvent.RiskAssigned(asset.baseAsset, riskProfile.marketCapTier))
                    
                    // Create TradableAsset with risk profile
                    val tradableAsset = createTradableAsset(asset, riskProfile)
                    
                    // Register in AssetRegistry
                    AssetRegistry.register(tradableAsset)
                    registeredCount++
                    
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to process ${asset.baseAsset}", e)
                }
            }
            
            updateState { it.copy(
                status = PipelineStatus.COMPLETE,
                registeredCount = registeredCount,
                lastRun = Instant.now(),
                currentAsset = null
            )}
            
            val durationMs = System.currentTimeMillis() - startTime
            emitEvent(PipelineEvent.AssetsRegistered(registeredCount))
            emitEvent(PipelineEvent.PipelineComplete(registeredCount, durationMs))
            
            Log.i(TAG, "Pipeline complete: $registeredCount assets in ${durationMs}ms")
            
            registeredCount
            
        } catch (e: Exception) {
            Log.e(TAG, "Pipeline failed", e)
            updateState { it.copy(
                status = PipelineStatus.ERROR,
                error = e.message
            )}
            emitEvent(PipelineEvent.PipelineError(e.message ?: "Unknown error", e))
            0
        }
    }
    
    /**
     * Run pipeline for a single exchange (faster startup).
     */
    suspend fun runForExchange(exchangeId: String): Int {
        return runFullPipeline(PipelineConfig(
            exchanges = listOf(exchangeId),
            enableDeFiLlamaEnrichment = false  // Skip for speed
        ))
    }
    
    /**
     * Run pipeline using AI connection manager for connected exchanges.
     */
    suspend fun runFromConnectionManager(
        connectionManager: AIConnectionManager,
        config: PipelineConfig = PipelineConfig()
    ): Int {
        val connectedExchanges = connectionManager.getConnectedExchangeIds()
        if (connectedExchanges.isEmpty()) {
            Log.w(TAG, "No exchanges connected")
            return 0
        }
        
        return runFullPipeline(config.copy(exchanges = connectedExchanges))
    }
    
    /**
     * Enable automatic periodic discovery.
     */
    fun enableAutoRun(config: PipelineConfig) {
        if (config.autoRunIntervalMs <= 0) return
        
        autoRunJob?.cancel()
        autoRunJob = scope.launch {
            while (isActive) {
                runFullPipeline(config)
                delay(config.autoRunIntervalMs)
            }
        }
        
        Log.i(TAG, "Auto-run enabled: every ${config.autoRunIntervalMs}ms")
    }
    
    /**
     * Disable automatic discovery.
     */
    fun disableAutoRun() {
        autoRunJob?.cancel()
        autoRunJob = null
        Log.i(TAG, "Auto-run disabled")
    }
    
    /**
     * Get discovery progress from UniversalAssetDiscovery.
     */
    fun getDiscoveryProgress(): StateFlow<DiscoveryProgress> {
        return discovery.discoveryProgress
    }
    
    /**
     * Shutdown pipeline and cleanup.
     */
    fun shutdown() {
        autoRunJob?.cancel()
        scope.cancel()
        INSTANCE = null
    }
    
    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================
    
    /**
     * Create TradableAsset from enriched data and risk profile.
     */
    private fun createTradableAsset(
        asset: EnrichedAsset,
        riskProfile: DynamicRiskProfile
    ): TradableAsset {
        // Determine asset type
        val assetType = when {
            asset.isStablecoin -> AssetType.STABLECOIN
            asset.isLayer1 -> AssetType.CRYPTO
            asset.isLayer2 -> AssetType.CRYPTO
            else -> AssetType.CRYPTO
        }
        
        // Map to AssetCategory
        val category = mapToAssetCategory(asset.category, riskProfile.inferredCategory)
        
        // Create trading pair symbol (e.g., BTC/USDT)
        val symbol = "${asset.baseAsset}/USDT"
        
        return TradableAsset(
            symbol = symbol,
            baseAsset = asset.baseAsset,
            quoteAsset = "USDT",
            assetType = assetType,
            category = category,
            exchange = asset.availableExchanges.firstOrNull() ?: "unknown",
            status = AssetStatus.ACTIVE,
            lastPrice = asset.lastPrice ?: 0.0,
            lastUpdated = Instant.now(),
            // Risk parameters from DynamicRiskAssigner
            kellyMultiplier = riskProfile.kellyMultiplier,
            maxPositionPercent = riskProfile.maxPositionPercent,
            stopLossPercent = riskProfile.stopLossPercent,
            marginEnabled = riskProfile.canShort,
            maxLeverage = riskProfile.recommendedLeverage,
            // Metadata
            scalpingEnabled = riskProfile.volatilityTier == VolatilityTier.HIGH ||
                              riskProfile.volatilityTier == VolatilityTier.EXTREME,
            tradingHours = TradingHours.CRYPTO_247,  // Crypto trades 24/7
            precision = TradingPrecision(
                pricePrecision = 8,
                quantityPrecision = 8,
                minQuantity = 0.00001,
                maxQuantity = 100000.0,
                minNotional = 10.0
            ),
            discoverySource = DiscoverySource.AI_PIPELINE,
            riskConfidence = riskProfile.confidence
        )
    }
    
    /**
     * Estimate market cap from available data.
     */
    private fun estimateMarketCap(asset: EnrichedAsset): Double? {
        // If we have TVL and mcap/tvl ratio, calculate
        asset.mcapTvlRatio?.let { ratio ->
            asset.tvlUsd?.let { tvl ->
                return tvl * ratio
            }
        }
        
        // Fallback: estimate from volume (rough heuristic)
        // Average crypto mcap/volume ratio is ~20-50x
        asset.volume24h?.let { volume ->
            return volume * 30.0  // Conservative multiplier
        }
        
        return null
    }
    
    /**
     * Map category string to AssetCategory enum.
     */
    private fun mapToAssetCategory(
        enrichedCategory: String?,
        inferredCategory: AssetCategory
    ): AssetCategory {
        // Use enriched category if available
        when (enrichedCategory?.lowercase()) {
            "dexes", "dex" -> return AssetCategory.DEFI
            "lending" -> return AssetCategory.DEFI
            "yield" -> return AssetCategory.DEFI
            "derivatives" -> return AssetCategory.DEFI
            "bridge" -> return AssetCategory.INFRASTRUCTURE
            "chain", "layer1", "layer-1" -> return AssetCategory.LAYER_1
            "layer2", "layer-2" -> return AssetCategory.LAYER_2
            "meme", "memes" -> return AssetCategory.MEME
            "gaming" -> return AssetCategory.GAMING
            "nft" -> return AssetCategory.NFT
            "ai" -> return AssetCategory.AI
            "privacy" -> return AssetCategory.PRIVACY
            "exchange" -> return AssetCategory.EXCHANGE_TOKEN
        }
        
        // Fall back to inferred category
        return inferredCategory
    }
    
    /**
     * Infer category for assets without DeFiLlama data.
     */
    private fun inferCategory(symbol: String): String {
        return when {
            symbol in setOf("BTC", "ETH", "SOL", "AVAX", "ADA", "DOT", "ATOM") -> "chain"
            symbol in setOf("ARB", "OP", "MATIC", "IMX", "MNT") -> "layer2"
            symbol in setOf("DOGE", "SHIB", "PEPE", "FLOKI", "BONK") -> "meme"
            symbol in setOf("AAVE", "UNI", "LINK", "MKR", "CRV") -> "dexes"
            symbol in setOf("APE", "SAND", "MANA", "AXS", "ENJ") -> "gaming"
            else -> "unknown"
        }
    }
    
    private fun updateState(updater: (PipelineState) -> PipelineState) {
        _state.update(updater)
    }
    
    private fun emitEvent(event: PipelineEvent) {
        scope.launch {
            _events.emit(event)
        }
    }
}

/**
 * Discovery source tracking.
 */
enum class DiscoverySource {
    BOOTSTRAP,       // From hardcoded bootstrap list
    EXCHANGE_API,    // Directly from exchange
    AI_PIPELINE,     // From AI discovery pipeline
    MANUAL           // Manually added
}

/**
 * Asset status.
 */
enum class AssetStatus {
    ACTIVE,          // Tradeable
    HALTED,          // Trading suspended
    DELISTED,        // Removed from exchange
    MAINTENANCE      // Under maintenance
}

/**
 * Asset type classification.
 */
enum class AssetType {
    CRYPTO,
    STABLECOIN,
    FOREX,
    COMMODITY,
    STOCK,
    ETF,
    BOND,
    DERIVATIVE
}

/**
 * Trading precision rules.
 */
data class TradingPrecision(
    val pricePrecision: Int,
    val quantityPrecision: Int,
    val minQuantity: Double,
    val maxQuantity: Double,
    val minNotional: Double
)

/**
 * Extension function to wire pipeline into TradingSystemIntegration.
 */
suspend fun AssetDiscoveryPipeline.wireToTradingSystem(
    connectionManager: AIConnectionManager,
    config: PipelineConfig = PipelineConfig()
): Int {
    Log.i("AssetDiscoveryPipeline", "Wiring pipeline to trading system...")
    return runFromConnectionManager(connectionManager, config)
}
