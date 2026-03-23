package com.miwealth.sovereignvantage.core.exchange

/**
 * MULTI-EXCHANGE MANAGER
 * 
 * Sovereign Vantage: Arthur Edition V5.19.244
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * Manages multiple simultaneous exchange connections and aggregates
 * tick streams into a unified flow.
 * 
 * Architecture:
 * The manager maintains a collection of active tick providers, one for
 * each exchange the user has configured and enabled. Each provider runs
 * independently in its own coroutine, emitting ticks as they arrive from
 * that exchange. The manager aggregates all these tick streams into one
 * unified flow that the DQN and trading board consume.
 * 
 * Key Capabilities:
 * - Add/remove exchanges dynamically without restarting the system
 * - Automatic reconnection when connections fail
 * - Cross-exchange price comparison and spread calculation
 * - Arbitrage opportunity detection
 * - Per-exchange statistics and health monitoring
 * 
 * Data Flow:
 * Exchange 1 WebSocket → TickProvider 1 → \
 * Exchange 2 WebSocket → TickProvider 2 → → MultiExchangeManager → EnrichedTick stream → DQN/Board
 * Exchange 3 WebSocket → TickProvider 3 → /
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */

import android.content.Context
import com.miwealth.sovereignvantage.core.exchange.tick.*
import com.miwealth.sovereignvantage.core.utils.SystemLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

class MultiExchangeManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    companion object {
        private const val TAG = "MultiExchangeManager"
    }
    
    // Active tick providers (connectionId → provider)
    private val activeProviders = ConcurrentHashMap<String, TickProvider>()
    
    // Collection jobs for each provider (connectionId → job)
    private val collectionJobs = ConcurrentHashMap<String, Job>()
    
    // Connection status tracking (connectionId → status)
    private val connectionStatuses = ConcurrentHashMap<String, ExchangeConnectionStatus>()
    
    // Best prices across all exchanges (symbol → price)
    private val bestPrices = ConcurrentHashMap<String, Double>()
    
    // Aggregated tick stream (enriched with cross-exchange context)
    private val _aggregatedTicks = MutableSharedFlow<EnrichedTick>(
        replay = 0,
        extraBufferCapacity = 10000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val aggregatedTicks: SharedFlow<EnrichedTick> = _aggregatedTicks.asSharedFlow()
    
    // Arbitrage opportunities detected
    private val _arbitrageOpportunities = MutableSharedFlow<ArbitrageOpportunity>(
        replay = 1,
        extraBufferCapacity = 100
    )
    val arbitrageOpportunities: SharedFlow<ArbitrageOpportunity> = _arbitrageOpportunities.asSharedFlow()
    
    // Current configuration
    private var configuration: MultiExchangeConfiguration? = null
    
    /**
     * Initialize with user's multi-exchange configuration.
     * 
     * This connects to all enabled exchanges in the config.
     */
    suspend fun initialize(config: MultiExchangeConfiguration): Result<Unit> {
        SystemLogger.system("═══════════════════════════════════════════════════════")
        SystemLogger.system("🔧 BUILD #244: Initializing Multi-Exchange Manager")
        SystemLogger.system("   Total exchanges configured: ${config.exchanges.size}")
        SystemLogger.system("   Active exchanges: ${config.activeExchanges.size}")
        SystemLogger.system("   Arbitrage enabled: ${config.arbitrageSettings.enabled}")
        SystemLogger.system("═══════════════════════════════════════════════════════")
        
        configuration = config
        
        // Connect to each enabled exchange
        val results = config.activeExchanges.map { exchangeConfig ->
            addExchange(exchangeConfig)
        }
        
        // Check if at least one exchange connected successfully
        val successCount = results.count { it.isSuccess }
        
        return if (successCount > 0) {
            SystemLogger.system("✅ BUILD #244: Multi-Exchange Manager initialized")
            SystemLogger.system("   Successfully connected: $successCount/${config.activeExchanges.size}")
            Result.success(Unit)
        } else {
            SystemLogger.error("❌ BUILD #244: No exchanges connected successfully", null)
            Result.failure(Exception("Failed to connect to any exchanges"))
        }
    }
    
    /**
     * Add a new exchange connection.
     * 
     * Creates the appropriate tick provider based on the config,
     * connects to the exchange, and starts collecting ticks.
     */
    suspend fun addExchange(config: ExchangeConnectionConfig): Result<Unit> {
        SystemLogger.system("🔌 Adding exchange: ${config.displayName} (${config.connectionId})")
        
        return try {
            // Create tick provider based on provider type
            val provider = createTickProvider(config)
            
            // Connect to exchange
            val connected = provider.connect()
            
            if (!connected) {
                SystemLogger.error("❌ Failed to connect to ${config.displayName}", null)
                return Result.failure(Exception("Connection failed"))
            }
            
            // Store provider
            activeProviders[config.connectionId] = provider
            
            // Initialize status tracking
            connectionStatuses[config.connectionId] = ExchangeConnectionStatus(
                connectionId = config.connectionId,
                exchangeId = config.exchangeId,
                providerType = config.providerType,
                connected = true,
                symbols = config.symbols,
                ticksReceived = 0,
                errorsEncountered = 0,
                lastTickTimestamp = System.currentTimeMillis(),
                averageTickRate = 0.0,
                reconnectAttempts = 0,
                uptimeMs = 0
            )
            
            // Start tick collection
            startTickCollection(config, provider)
            
            SystemLogger.system("✅ Exchange added: ${config.displayName}")
            SystemLogger.system("   Provider type: ${config.providerType}")
            SystemLogger.system("   Symbols: ${config.symbols.joinToString()}")
            SystemLogger.system("   Expected tick rate: ${config.providerType.expectedTickRate.min}-${config.providerType.expectedTickRate.max} Hz")
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            SystemLogger.error("❌ Failed to add exchange ${config.displayName}: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Create the appropriate tick provider based on config.
     * 
     * This is the factory method that instantiates the correct
     * tick provider class for each exchange type.
     */
    private fun createTickProvider(config: ExchangeConnectionConfig): TickProvider {
        return when (config.providerType) {
            ExchangeProviderType.BINANCE_PUBLIC -> {
                BinancePublicTickProvider(symbols = config.symbols)
            }
            
            ExchangeProviderType.COINBASE_SANDBOX -> {
                // Extract credentials
                val creds = config.credentials
                    ?: throw IllegalArgumentException("Coinbase Sandbox requires credentials")
                
                CoinbaseSandboxTickProvider(
                    symbols = config.symbols,
                    apiKey = creds.apiKey,
                    apiSecret = creds.apiSecret
                )
            }
            
            ExchangeProviderType.KRAKEN_FUTURES_DEMO -> {
                val creds = config.credentials
                    ?: throw IllegalArgumentException("Kraken Futures Demo requires credentials")
                
                KrakenFuturesDemoTickProvider(
                    symbols = config.symbols,
                    apiKey = creds.apiKey,
                    apiSecret = creds.apiSecret
                )
            }
            
            else -> {
                throw IllegalArgumentException("Provider type ${config.providerType} not yet implemented")
            }
        }
    }
    
    /**
     * Start collecting ticks from a provider.
     * 
     * Launches a coroutine that collects ticks from the provider's
     * stream and enriches them with cross-exchange context before
     * emitting to the aggregated stream.
     */
    private fun startTickCollection(config: ExchangeConnectionConfig, provider: TickProvider) {
        collectionJobs[config.connectionId]?.cancel()
        
        collectionJobs[config.connectionId] = scope.launch {
            var tickCount = 0L
            val startTime = System.currentTimeMillis()
            
            try {
                provider.getTickStream().collect { tick ->
                    tickCount++
                    
                    // Update connection status
                    updateConnectionStatus(config.connectionId, tick, tickCount, startTime)
                    
                    // Enrich tick with cross-exchange context
                    val enriched = enrichTick(tick, config)
                    
                    // Emit enriched tick
                    _aggregatedTicks.emit(enriched)
                    
                    // Check for arbitrage opportunities
                    if (configuration?.arbitrageSettings?.enabled == true) {
                        checkArbitrageOpportunity(enriched)
                    }
                    
                    // Log periodically (every 100 ticks)
                    if (tickCount % 100 == 0L) {
                        val elapsed = System.currentTimeMillis() - startTime
                        val rate = if (elapsed > 0) (tickCount.toDouble() / (elapsed / 1000.0)) else 0.0
                        SystemLogger.d(TAG, "📊 ${config.displayName}: $tickCount ticks, ${String.format("%.2f", rate)} Hz")
                    }
                }
            } catch (e: CancellationException) {
                SystemLogger.d(TAG, "Collection cancelled for ${config.connectionId}")
                throw e
            } catch (e: Exception) {
                SystemLogger.error("❌ Collection error for ${config.displayName}: ${e.message}", e)
                updateErrorCount(config.connectionId)
            }
        }
    }
    
    /**
     * Enrich a tick with cross-exchange context.
     * 
     * This is where the magic happens. We take a raw tick from one
     * exchange and add information about how it compares to other
     * exchanges that are also connected.
     */
    private fun enrichTick(tick: UniversalTick, config: ExchangeConnectionConfig): EnrichedTick {
        // Update best price for this symbol
        val currentBest = bestPrices[tick.symbol] ?: tick.price
        val newBest = if (tick.price < currentBest) tick.price else currentBest
        bestPrices[tick.symbol] = newBest
        
        // Calculate spread from best price
        val spreadFromBest = tick.price - newBest
        val spreadFromBestPercent = if (newBest > 0) (spreadFromBest / newBest) * 100.0 else 0.0
        
        // Determine if this is the best price
        val isBestPrice = (tick.price == newBest)
        
        // Calculate exchange rank (simplified: based on tick rate for now)
        val exchangeRank = calculateExchangeRank(config.connectionId)
        
        // Estimate latency (time from tick to now)
        val latencyMs = System.currentTimeMillis() - tick.timestamp
        
        return EnrichedTick(
            baseTick = tick,
            exchangeId = config.exchangeId,
            connectionId = config.connectionId,
            spreadFromBest = spreadFromBest,
            spreadFromBestPercent = spreadFromBestPercent,
            exchangeRank = exchangeRank,
            latencyMs = latencyMs,
            isBestPrice = isBestPrice
        )
    }
    
    /**
     * Check if this tick represents an arbitrage opportunity.
     * 
     * An arbitrage opportunity exists when the same symbol trades at
     * significantly different prices on different exchanges.
     */
    private suspend fun checkArbitrageOpportunity(enrichedTick: EnrichedTick) {
        val settings = configuration?.arbitrageSettings ?: return
        
        if (enrichedTick.isArbitrageOpportunity(settings)) {
            // Find which exchange has the best (lowest) price
            val bestExchange = findBestPriceExchange(enrichedTick.baseTick.symbol)
            
            if (bestExchange != null && bestExchange != enrichedTick.connectionId) {
                val opportunity = ArbitrageOpportunity(
                    symbol = enrichedTick.baseTick.symbol,
                    buyExchange = bestExchange,
                    sellExchange = enrichedTick.connectionId,
                    buyPrice = bestPrices[enrichedTick.baseTick.symbol] ?: 0.0,
                    sellPrice = enrichedTick.baseTick.price,
                    spreadUsd = enrichedTick.spreadFromBest,
                    spreadPercent = enrichedTick.spreadFromBestPercent,
                    timestamp = System.currentTimeMillis(),
                    confidence = calculateArbitrageConfidence(enrichedTick)
                )
                
                _arbitrageOpportunities.emit(opportunity)
                
                SystemLogger.system("💰 ARBITRAGE OPPORTUNITY: ${opportunity.symbol}")
                SystemLogger.system("   Buy on $bestExchange @ \$${opportunity.buyPrice}")
                SystemLogger.system("   Sell on ${enrichedTick.connectionId} @ \$${opportunity.sellPrice}")
                SystemLogger.system("   Spread: \$${String.format("%.2f", opportunity.spreadUsd)} (${String.format("%.2f", opportunity.spreadPercent)}%)")
            }
        }
    }
    
    /**
     * Find which exchange has the best price for a symbol.
     */
    private fun findBestPriceExchange(symbol: String): String? {
        // Find connection with best price (lowest)
        var bestConnectionId: String? = null
        var bestPrice = Double.MAX_VALUE
        
        activeProviders.forEach { (connectionId, _) ->
            // This is simplified - in production, you'd track per-exchange prices
            val price = bestPrices[symbol] ?: return@forEach
            if (price < bestPrice) {
                bestPrice = price
                bestConnectionId = connectionId
            }
        }
        
        return bestConnectionId
    }
    
    /**
     * Calculate confidence score for arbitrage opportunity.
     * 
     * Higher confidence = larger spread, more stable price difference.
     */
    private fun calculateArbitrageConfidence(tick: EnrichedTick): Double {
        // Simple confidence: based on spread percentage
        // 0.5% spread = 50% confidence
        // 1.0% spread = 70% confidence
        // 2.0% spread = 90% confidence
        return when {
            tick.spreadFromBestPercent >= 2.0 -> 0.90
            tick.spreadFromBestPercent >= 1.0 -> 0.70
            tick.spreadFromBestPercent >= 0.5 -> 0.50
            else -> 0.30
        }
    }
    
    /**
     * Calculate exchange rank based on tick rate and volume.
     * 
     * Simplified implementation - returns priority order.
     */
    private fun calculateExchangeRank(connectionId: String): Int {
        val allConnections = activeProviders.keys.sorted()
        return allConnections.indexOf(connectionId) + 1
    }
    
    /**
     * Update connection status after receiving a tick.
     */
    private fun updateConnectionStatus(
        connectionId: String,
        tick: UniversalTick,
        tickCount: Long,
        startTime: Long
    ) {
        val status = connectionStatuses[connectionId] ?: return
        val now = System.currentTimeMillis()
        val uptimeMs = now - startTime
        val avgTickRate = if (uptimeMs > 0) (tickCount.toDouble() / (uptimeMs / 1000.0)) else 0.0
        
        connectionStatuses[connectionId] = status.copy(
            ticksReceived = tickCount,
            lastTickTimestamp = tick.timestamp,
            averageTickRate = avgTickRate,
            uptimeMs = uptimeMs
        )
    }
    
    /**
     * Increment error count for a connection.
     */
    private fun updateErrorCount(connectionId: String) {
        val status = connectionStatuses[connectionId] ?: return
        connectionStatuses[connectionId] = status.copy(
            errorsEncountered = status.errorsEncountered + 1
        )
        
        // Check if error threshold exceeded
        val config = configuration?.exchanges?.find { it.connectionId == connectionId }
        if (config != null) {
            if (status.errorsEncountered >= config.settings.errorThreshold) {
                SystemLogger.error("❌ Error threshold exceeded for ${config.displayName}, disconnecting", null)
                scope.launch { removeExchange(connectionId) }
            }
        }
    }
    
    /**
     * Remove an exchange connection.
     */
    suspend fun removeExchange(connectionId: String) {
        SystemLogger.system("🔌 Removing exchange: $connectionId")
        
        // Cancel collection job
        collectionJobs[connectionId]?.cancel()
        collectionJobs.remove(connectionId)
        
        // Disconnect provider
        activeProviders[connectionId]?.disconnect()
        activeProviders.remove(connectionId)
        
        // Remove status
        connectionStatuses.remove(connectionId)
        
        SystemLogger.system("✅ Exchange removed: $connectionId")
    }
    
    /**
     * Get status for all active exchanges.
     */
    fun getAllStatuses(): Map<String, ExchangeConnectionStatus> {
        return connectionStatuses.toMap()
    }
    
    /**
     * Get status for a specific exchange.
     */
    fun getStatus(connectionId: String): ExchangeConnectionStatus? {
        return connectionStatuses[connectionId]
    }
    
    /**
     * Check if multi-exchange arbitrage is possible.
     */
    fun isArbitragePossible(): Boolean {
        return activeProviders.size >= 2
    }
    
    /**
     * Shutdown the manager and disconnect all exchanges.
     */
    suspend fun shutdown() {
        SystemLogger.system("🛑 BUILD #244: Shutting down Multi-Exchange Manager")
        
        // Disconnect all exchanges
        activeProviders.keys.toList().forEach { connectionId ->
            removeExchange(connectionId)
        }
        
        // Cancel scope
        scope.cancel()
        
        SystemLogger.system("✅ Multi-Exchange Manager shutdown complete")
    }
}

/**
 * Represents a detected arbitrage opportunity.
 */
data class ArbitrageOpportunity(
    val symbol: String,
    val buyExchange: String,       // Exchange with lower price (buy here)
    val sellExchange: String,      // Exchange with higher price (sell here)
    val buyPrice: Double,
    val sellPrice: Double,
    val spreadUsd: Double,         // Dollar spread
    val spreadPercent: Double,     // Percentage spread
    val timestamp: Long,
    val confidence: Double         // 0.0-1.0 confidence score
) {
    /**
     * Estimated profit per unit (before fees).
     */
    val profitPerUnit: Double
        get() = spreadUsd
    
    /**
     * Is this a significant opportunity?
     */
    fun isSignificant(minSpreadUsd: Double = 30.0): Boolean {
        return spreadUsd >= minSpreadUsd && confidence >= 0.5
    }
}
