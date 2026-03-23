package com.miwealth.sovereignvantage.core.exchange.tick

/**
 * MULTI-EXCHANGE MANAGER
 * 
 * Sovereign Vantage: Arthur Edition V5.19.244
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * Purpose:
 * Manages simultaneous connections to multiple cryptocurrency exchanges.
 * Aggregates tick streams from all exchanges into a unified flow with
 * cross-exchange metadata for arbitrage detection and price comparison.
 * 
 * Key Features:
 * - Connect to 1-5 exchanges simultaneously
 * - Each exchange runs its own tick provider (Binance, Kraken, Coinbase, etc.)
 * - Enriches ticks with cross-exchange spread information
 * - Detects arbitrage opportunities automatically
 * - User-configurable via Settings → Trading Setup
 * - Self-sovereign: user controls all connections and credentials
 * 
 * Architecture:
 * Each exchange has its own TickProvider running in parallel.
 * All ticks flow into a shared MutableSharedFlow that enriches them
 * with cross-exchange metadata. The DQN and board consume the enriched stream.
 * 
 * Example with 3 exchanges:
 * - Binance: REST polling, 0.2 Hz → 12 ticks/min
 * - Kraken: WebSocket, 2 Hz → 120 ticks/min
 * - Coinbase: WebSocket, 1 Hz → 60 ticks/min
 * Total: 192 ticks/min combined → DQN sees 3.2 ticks/sec
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */

import android.content.Context
import android.util.Log
import com.miwealth.sovereignvantage.core.utils.SystemLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

class MultiExchangeManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    companion object {
        private const val TAG = "MultiExchangeManager"
    }
    
    // Active tick providers (exchangeId -> provider)
    private val activeProviders = ConcurrentHashMap<String, TickProvider>()
    
    // Coroutine jobs for each provider's tick collection (exchangeId -> job)
    private val providerJobs = ConcurrentHashMap<String, Job>()
    
    // Latest tick from each exchange per symbol (exchangeId-symbol -> tick)
    private val latestTicks = ConcurrentHashMap<String, UniversalTick>()
    
    // Combined tick stream from ALL exchanges
    private val _aggregatedTicks = MutableSharedFlow<EnrichedTick>(
        replay = 0,
        extraBufferCapacity = 10000  // Buffer up to 10k ticks during bursts
    )
    val aggregatedTicks: SharedFlow<EnrichedTick> = _aggregatedTicks.asSharedFlow()
    
    // Exchange statistics for monitoring
    private val exchangeStats = ConcurrentHashMap<String, ExchangeStats>()
    
    /**
     * Add a new exchange connection.
     * 
     * Creates the appropriate tick provider based on config.providerType,
     * connects to the exchange, and starts collecting ticks.
     * 
     * @param config Exchange connection configuration (provider type, symbols, credentials)
     * @return Result.success if connected, Result.failure with error if connection failed
     */
    suspend fun addExchange(config: ExchangeConnectionConfig): Result<Unit> {
        SystemLogger.system("🔌 BUILD #244: Adding exchange: ${config.exchangeId} (${config.providerType})")
        
        // Check if already connected
        if (activeProviders.containsKey(config.exchangeId)) {
            val error = "Exchange ${config.exchangeId} is already connected"
            SystemLogger.error(error, null)
            return Result.failure(Exception(error))
        }
        
        // Create appropriate tick provider based on type
        val provider = try {
            createTickProvider(config)
        } catch (e: Exception) {
            SystemLogger.error("Failed to create tick provider for ${config.exchangeId}: ${e.message}", e)
            return Result.failure(e)
        }
        
        // Attempt to connect
        val connected = try {
            provider.connect()
        } catch (e: Exception) {
            SystemLogger.error("Connection failed for ${config.exchangeId}: ${e.message}", e)
            return Result.failure(e)
        }
        
        if (!connected) {
            val error = "Failed to connect to ${config.exchangeId}"
            SystemLogger.error(error, null)
            return Result.failure(Exception(error))
        }
        
        // Store provider
        activeProviders[config.exchangeId] = provider
        
        // Initialize stats
        exchangeStats[config.exchangeId] = ExchangeStats(
            exchangeId = config.exchangeId,
            providerType = config.providerType,
            connectedAt = System.currentTimeMillis()
        )
        
        // Start collecting ticks from this provider
        startTickCollection(config.exchangeId, provider, config)
        
        SystemLogger.system("✅ BUILD #244: Exchange ${config.exchangeId} connected and active")
        return Result.success(Unit)
    }
    
    /**
     * Create the appropriate tick provider based on configuration.
     */
    private fun createTickProvider(config: ExchangeConnectionConfig): TickProvider {
        return when (config.providerType) {
            "binance-public" -> {
                BinancePublicTickProvider(symbols = config.symbols)
            }
            
            "kraken-demo" -> {
                if (config.apiKey == null || config.apiSecret == null) {
                    throw IllegalArgumentException("Kraken requires API key and secret")
                }
                KrakenFuturesDemoTickProvider(
                    symbols = config.symbols,
                    apiKey = config.apiKey,
                    apiSecret = config.apiSecret
                )
            }
            
            "coinbase-sandbox" -> {
                if (config.apiKey == null || config.apiSecret == null) {
                    throw IllegalArgumentException("Coinbase requires API key and secret")
                }
                CoinbaseSandboxTickProvider(
                    symbols = config.symbols,
                    apiKey = config.apiKey,
                    apiSecret = config.apiSecret,
                    passphrase = config.passphrase
                )
            }
            
            else -> {
                throw IllegalArgumentException("Unknown provider type: ${config.providerType}")
            }
        }
    }
    
    /**
     * Start collecting ticks from a provider and feeding them to the aggregated stream.
     * 
     * Each provider runs in its own coroutine. If the provider's stream fails,
     * the coroutine handles reconnection (if auto-reconnect is enabled).
     */
    private fun startTickCollection(
        exchangeId: String,
        provider: TickProvider,
        config: ExchangeConnectionConfig
    ) {
        // Cancel existing job if any (shouldn't happen, but be safe)
        providerJobs[exchangeId]?.cancel()
        
        // Launch new collection job
        providerJobs[exchangeId] = scope.launch {
            var reconnectAttempts = 0
            
            while (isActive && reconnectAttempts < config.maxReconnectAttempts) {
                try {
                    SystemLogger.d(TAG, "📡 Starting tick collection for $exchangeId")
                    
                    // Collect ticks from this provider
                    provider.getTickStream().collect { tick ->
                        // Update latest tick for this exchange-symbol pair
                        val key = "$exchangeId-${tick.symbol}"
                        latestTicks[key] = tick
                        
                        // Update stats
                        exchangeStats[exchangeId]?.let { stats ->
                            stats.ticksReceived++
                            stats.lastTickTime = System.currentTimeMillis()
                        }
                        
                        // Enrich the tick with cross-exchange metadata
                        val enrichedTick = enrichTick(tick, exchangeId, provider)
                        
                        // Emit to aggregated stream
                        _aggregatedTicks.emit(enrichedTick)
                        
                        // Log occasionally for monitoring
                        if (exchangeStats[exchangeId]?.ticksReceived?.rem(100) == 0L) {
                            SystemLogger.d(TAG, "📊 $exchangeId: ${exchangeStats[exchangeId]?.ticksReceived} ticks received")
                        }
                    }
                    
                    // If we reach here, the stream ended gracefully
                    SystemLogger.d(TAG, "📡 Tick stream ended for $exchangeId")
                    break
                    
                } catch (e: CancellationException) {
                    // Job was cancelled (user removed exchange or app shutdown)
                    SystemLogger.d(TAG, "🛑 Tick collection cancelled for $exchangeId")
                    throw e
                    
                } catch (e: Exception) {
                    // Stream failed - attempt reconnection if enabled
                    exchangeStats[exchangeId]?.errors++
                    SystemLogger.error("❌ Tick collection error for $exchangeId: ${e.message}", e)
                    
                    if (config.autoReconnect) {
                        reconnectAttempts++
                        SystemLogger.system("🔄 Reconnecting to $exchangeId (attempt $reconnectAttempts/${config.maxReconnectAttempts})")
                        
                        delay(config.reconnectDelayMs)
                        
                        // Attempt to reconnect
                        val reconnected = try {
                            provider.connect()
                        } catch (reconnectError: Exception) {
                            SystemLogger.error("Reconnection failed for $exchangeId: ${reconnectError.message}", reconnectError)
                            false
                        }
                        
                        if (!reconnected) {
                            SystemLogger.error("Failed to reconnect to $exchangeId", null)
                            if (reconnectAttempts >= config.maxReconnectAttempts) {
                                SystemLogger.system("🛑 Max reconnect attempts reached for $exchangeId - giving up")
                                exchangeStats[exchangeId]?.connected = false
                            }
                        } else {
                            SystemLogger.system("✅ Reconnected to $exchangeId")
                            exchangeStats[exchangeId]?.reconnects++
                            reconnectAttempts = 0  // Reset counter on successful reconnect
                        }
                    } else {
                        // Auto-reconnect disabled - stop trying
                        SystemLogger.system("🛑 Auto-reconnect disabled for $exchangeId - stopping")
                        exchangeStats[exchangeId]?.connected = false
                        break
                    }
                }
            }
            
            SystemLogger.d(TAG, "📡 Tick collection stopped for $exchangeId")
        }
    }
    
    /**
     * Enrich a tick with cross-exchange metadata.
     * 
     * Calculates:
     * - Spread from best price across all exchanges
     * - Exchange liquidity rank
     * - Latency from exchange to app
     * - Whether this is the best bid/ask
     */
    private fun enrichTick(
        tick: UniversalTick,
        exchangeId: String,
        provider: TickProvider
    ): EnrichedTick {
        // Find best bid and ask across ALL exchanges for this symbol
        val (bestBid, bestAsk) = findBestPrices(tick.symbol)
        
        // Calculate spread from best price
        // For a buy (we pay the ask), we want the lowest ask
        // For a sell (we receive the bid), we want the highest bid
        val spreadFromBestAsk = tick.ask - bestAsk
        val spreadFromBestBid = bestBid - tick.bid
        
        // Use the worse of the two spreads (more conservative)
        val spreadFromBest = maxOf(spreadFromBestAsk, spreadFromBestBid)
        
        // Calculate latency (how old is this tick)
        val latencyMs = System.currentTimeMillis() - tick.timestamp
        
        // Determine if this is the best bid/ask
        val isBestBid = (tick.bid >= bestBid - 0.01)  // Allow 1 cent tolerance
        val isBestAsk = (tick.ask <= bestAsk + 0.01)
        
        // Get exchange rank (for now, simple: Binance=1, Kraken=2, Coinbase=3)
        // TODO: Make this dynamic based on actual liquidity/volume
        val exchangeRank = when {
            exchangeId.contains("binance") -> 1
            exchangeId.contains("kraken") -> 2
            exchangeId.contains("coinbase") -> 3
            else -> 4
        }
        
        return EnrichedTick(
            baseTick = tick,
            exchangeId = exchangeId,
            spreadFromBest = spreadFromBest,
            exchangeRank = exchangeRank,
            latencyMs = latencyMs,
            isBestBid = isBestBid,
            isBestAsk = isBestAsk
        )
    }
    
    /**
     * Find the best bid and ask prices across all exchanges for a symbol.
     * 
     * Best bid = highest bid (where we can sell)
     * Best ask = lowest ask (where we can buy)
     * 
     * @return Pair(bestBid, bestAsk)
     */
    private fun findBestPrices(symbol: String): Pair<Double, Double> {
        var bestBid = 0.0
        var bestAsk = Double.MAX_VALUE
        
        // Search all latest ticks for this symbol across all exchanges
        latestTicks.forEach { (key, tick) ->
            if (tick.symbol == symbol) {
                if (tick.bid > bestBid) bestBid = tick.bid
                if (tick.ask < bestAsk) bestAsk = tick.ask
            }
        }
        
        // If no ticks found, return the current tick's prices
        if (bestBid == 0.0) bestBid = 0.0
        if (bestAsk == Double.MAX_VALUE) bestAsk = 0.0
        
        return Pair(bestBid, bestAsk)
    }
    
    /**
     * Remove an exchange connection.
     * 
     * Disconnects the provider, cancels the collection job, and removes
     * all state for this exchange.
     */
    suspend fun removeExchange(exchangeId: String): Result<Unit> {
        SystemLogger.system("🔌 BUILD #244: Removing exchange: $exchangeId")
        
        // Cancel tick collection job
        providerJobs[exchangeId]?.cancel()
        providerJobs.remove(exchangeId)
        
        // Disconnect provider
        activeProviders[exchangeId]?.disconnect()
        activeProviders.remove(exchangeId)
        
        // Clean up stats
        exchangeStats.remove(exchangeId)
        
        // Remove latest ticks for this exchange
        latestTicks.keys.removeAll { it.startsWith("$exchangeId-") }
        
        SystemLogger.system("✅ BUILD #244: Exchange $exchangeId removed")
        return Result.success(Unit)
    }
    
    /**
     * Get status of all connected exchanges.
     * 
     * Returns a map of exchangeId -> status information.
     * Used by the Settings UI to show connection health.
     */
    fun getExchangeStatuses(): Map<String, ExchangeStats> {
        return exchangeStats.toMap()
    }
    
    /**
     * Get the tick provider for a specific exchange (for advanced use).
     */
    fun getProvider(exchangeId: String): TickProvider? {
        return activeProviders[exchangeId]
    }
    
    /**
     * Get count of active exchanges.
     */
    fun getActiveExchangeCount(): Int {
        return activeProviders.size
    }
    
    /**
     * Check if a specific exchange is connected.
     */
    fun isExchangeConnected(exchangeId: String): Boolean {
        return activeProviders.containsKey(exchangeId) && 
               activeProviders[exchangeId]?.isConnected() == true
    }
    
    /**
     * Shutdown all exchange connections.
     * Call this when the app is closing or trading is stopped.
     */
    suspend fun shutdown() {
        SystemLogger.system("🛑 BUILD #244: Shutting down MultiExchangeManager")
        
        // Disconnect all providers
        activeProviders.keys.forEach { exchangeId ->
            removeExchange(exchangeId)
        }
        
        // Cancel the scope
        scope.cancel()
        
        SystemLogger.system("✅ BUILD #244: MultiExchangeManager shutdown complete")
    }
}

/**
 * Statistics for one exchange connection.
 * Used for monitoring and display in Settings UI.
 */
data class ExchangeStats(
    val exchangeId: String,
    val providerType: String,
    val connectedAt: Long,
    var connected: Boolean = true,
    var ticksReceived: Long = 0,
    var errors: Int = 0,
    var reconnects: Int = 0,
    var lastTickTime: Long = 0
) {
    /**
     * Calculate current tick rate (ticks per second).
     */
    fun getCurrentTickRate(): Double {
        val elapsed = System.currentTimeMillis() - connectedAt
        if (elapsed == 0L) return 0.0
        return (ticksReceived.toDouble() / elapsed) * 1000.0
    }
    
    /**
     * Get uptime in seconds.
     */
    fun getUptimeSeconds(): Long {
        return (System.currentTimeMillis() - connectedAt) / 1000
    }
    
    /**
     * Is this connection healthy?
     * Healthy = connected, receiving ticks, few errors
     */
    fun isHealthy(): Boolean {
        val timeSinceLastTick = System.currentTimeMillis() - lastTickTime
        return connected && 
               ticksReceived > 0 && 
               errors < 10 && 
               timeSinceLastTick < 60_000  // Received tick in last minute
    }
}
