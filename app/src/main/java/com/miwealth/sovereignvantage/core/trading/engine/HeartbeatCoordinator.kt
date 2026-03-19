/**
 * HEARTBEAT COORDINATOR
 * 
 * Sovereign Vantage: Arthur Edition V5.18.21
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * PURPOSE:
 * Ensures Trading System and Hedging Engine operate on synchronized market snapshots.
 * Prevents race conditions, desync issues, and provides health monitoring.
 * 
 * KEY FEATURES:
 * - Synchronized market snapshots distributed to both systems
 * - Health monitoring (detects frozen/lagging systems)
 * - Timestamp coordination (ensures both use same market state)
 * - Thread-safe snapshot distribution
 * - Heartbeat verification (both systems must ack within timeout)
 * 
 * WORKFLOW:
 * 1. Capture market snapshot every heartbeat interval (default 1 second)
 * 2. Freeze snapshot (immutable state)
 * 3. Distribute to Trading System and Hedging Engine simultaneously
 * 4. Verify both systems acknowledge receipt within timeout
 * 5. Alert if either system fails to respond (frozen/crashed)
 * 
 * WHY THIS MATTERS:
 * Without synchronization, one system might see:
 * - BTC = $40,000 (stale)
 * While the other sees:
 * - BTC = $39,500 (fresh)
 * 
 * This causes:
 * - Incorrect hedge ratios
 * - Wrong arbitrage spreads
 * - Desynchronized risk calculations
 * - Portfolio imbalance
 * 
 * The HeartbeatCoordinator ensures BOTH systems always work from the SAME snapshot.
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */

package com.miwealth.sovereignvantage.core.trading.engine

import android.util.Log
import com.miwealth.sovereignvantage.core.trading.engine.PositionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Market snapshot - frozen point-in-time view of the entire system state.
 * Both Trading and Hedging systems receive the SAME snapshot simultaneously.
 */
data class MarketSnapshot(
    val timestamp: Instant,
    val sequenceNumber: Long,  // Monotonically increasing snapshot ID
    val prices: Map<String, PriceData>,  // All symbol prices at this instant
    val positions: Map<String, PositionData>,  // All open positions
    val portfolioValue: Double,  // Total portfolio value in USD
    val marginHealth: MarginHealthData,  // Margin/leverage status
    val exchangeStatus: Map<String, ExchangeHealthData>  // Exchange connectivity
) {
    /**
     * Verify snapshot is fresh (not stale).
     * Stale data can cause incorrect trading decisions.
     */
    fun isFresh(maxAgeMs: Long = 5000): Boolean {
        val ageMs = System.currentTimeMillis() - timestamp.toEpochMilli()
        return ageMs < maxAgeMs
    }
}

/**
 * Price data for a single symbol at the snapshot timestamp.
 */
data class PriceData(
    val symbol: String,
    val bid: Double,
    val ask: Double,
    val last: Double,
    val volume24h: Double,
    val exchange: String,
    val timestamp: Long
)

/**
 * Position data at the snapshot timestamp.
 */
data class PositionData(
    val symbol: String,
    val quantity: Double,
    val entryPrice: Double,
    val currentPrice: Double,
    val unrealizedPnL: Double,
    val leverage: Double?,
    val stopLoss: Double?,
    val liquidationPrice: Double?
)

/**
 * Margin/leverage health at the snapshot timestamp.
 */
data class MarginHealthData(
    val totalEquity: Double,
    val usedMargin: Double,
    val availableMargin: Double,
    val marginRatio: Double,  // usedMargin / totalEquity
    val isHealthy: Boolean,   // marginRatio < 0.8 (safe threshold)
    val liquidationRisk: String  // "SAFE", "MODERATE", "HIGH", "CRITICAL"
)

/**
 * Exchange connectivity status at the snapshot timestamp.
 */
data class ExchangeHealthData(
    val exchangeId: String,
    val isConnected: Boolean,
    val lastPingMs: Long,
    val isRateLimited: Boolean,
    val errorCount: Int
)

/**
 * System heartbeat status - tracks if a system is alive and responding.
 */
data class SystemHeartbeat(
    val systemName: String,
    val lastHeartbeatTime: Long,
    val lastAckSequence: Long,  // Last snapshot sequence number acknowledged
    val isAlive: Boolean,
    val missedHeartbeats: Int
)

/**
 * Heartbeat event types for monitoring.
 */
sealed class HeartbeatEvent {
    data class SnapshotCreated(val snapshot: MarketSnapshot) : HeartbeatEvent()
    data class SnapshotDistributed(val sequenceNumber: Long, val recipients: List<String>) : HeartbeatEvent()
    data class SystemAcknowledged(val systemName: String, val sequenceNumber: Long) : HeartbeatEvent()
    data class SystemTimeout(val systemName: String, val missedCount: Int) : HeartbeatEvent()
    data class SystemRecovered(val systemName: String) : HeartbeatEvent()
    data class StaleSnapshot(val sequenceNumber: Long, val ageMs: Long) : HeartbeatEvent()
}

/**
 * Interface for systems that receive heartbeat snapshots.
 */
interface HeartbeatReceiver {
    /**
     * Called when a new snapshot is available.
     * System must process and acknowledge within timeout.
     */
    suspend fun onSnapshot(snapshot: MarketSnapshot)
    
    /**
     * System's last heartbeat timestamp (for health monitoring).
     */
    fun getLastHeartbeat(): Long
    
    /**
     * System identifier (e.g. "TradingSystem", "HedgingEngine").
     */
    fun getSystemName(): String
}

/**
 * Heartbeat Coordinator - Synchronizes Trading and Hedging systems.
 * 
 * CRITICAL: This prevents race conditions where one system sees stale prices
 * while the other sees fresh prices, leading to incorrect hedge ratios and
 * desynchronized risk calculations.
 */
class HeartbeatCoordinator(
    private val positionManager: PositionManager? = null,  // For position snapshots
    private val heartbeatIntervalMs: Long = 1000L,  // 1 second heartbeat
    private val healthCheckTimeoutMs: Long = 5000L,  // 5 seconds to detect frozen system
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    
    companion object {
        private const val TAG = "HeartbeatCoordinator"
    }
    
    // State
    private val isRunning = AtomicBoolean(false)
    private var heartbeatJob: Job? = null
    private var healthCheckJob: Job? = null
    
    // Sequence counter for snapshots (monotonically increasing)
    private val sequenceCounter = AtomicLong(0)
    
    // Registered systems (Trading, Hedging, etc.)
    private val receivers = ConcurrentHashMap<String, HeartbeatReceiver>()
    
    // System health tracking
    private val systemHeartbeats = ConcurrentHashMap<String, SystemHeartbeat>()
    
    // Current snapshot (thread-safe)
    private val _currentSnapshot = MutableStateFlow<MarketSnapshot?>(null)
    val currentSnapshot: StateFlow<MarketSnapshot?> = _currentSnapshot.asStateFlow()
    
    // Price cache (updated from external sources)
    private val priceCache = ConcurrentHashMap<String, PriceData>()
    
    // Health status
    private val _healthStatus = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val healthStatus: StateFlow<Map<String, Boolean>> = _healthStatus.asStateFlow()
    
    /**
     * Register a system to receive heartbeat snapshots.
     */
    fun registerReceiver(receiver: HeartbeatReceiver) {
        val systemName = receiver.getSystemName()
        receivers[systemName] = receiver
        
        // Initialize heartbeat tracking
        systemHeartbeats[systemName] = SystemHeartbeat(
            systemName = systemName,
            lastHeartbeatTime = System.currentTimeMillis(),
            lastAckSequence = 0,
            isAlive = true,
            missedHeartbeats = 0
        )
        
        Log.d(TAG, "Registered receiver: $systemName")
    }
    
    /**
     * Unregister a system.
     */
    fun unregisterReceiver(systemName: String) {
        receivers.remove(systemName)
        systemHeartbeats.remove(systemName)
        Log.d(TAG, "Unregistered receiver: $systemName")
    }
    
    /**
     * Update price data (called by exchange connectors).
     */
    fun updatePrice(priceData: PriceData) {
        priceCache[priceData.symbol] = priceData
    }
    
    /**
     * Start the heartbeat coordinator.
     */
    fun start() {
        if (isRunning.compareAndSet(false, true)) {
            Log.i(TAG, "Starting HeartbeatCoordinator (interval=${heartbeatIntervalMs}ms)")
            
            // Start heartbeat generation
            heartbeatJob = scope.launch {
                while (isActive && isRunning.get()) {
                    try {
                        // 1. Capture snapshot
                        val snapshot = captureSnapshot()
                        
                        // 2. Store as current
                        _currentSnapshot.value = snapshot
                        
                        // 3. Distribute to all receivers
                        distributeSnapshot(snapshot)
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Heartbeat error: ${e.message}", e)
                    }
                    
                    delay(heartbeatIntervalMs)
                }
            }
            
            // Start health monitoring
            healthCheckJob = scope.launch {
                while (isActive && isRunning.get()) {
                    try {
                        checkSystemHealth()
                    } catch (e: Exception) {
                        Log.e(TAG, "Health check error: ${e.message}", e)
                    }
                    delay(healthCheckTimeoutMs / 2)  // Check twice per timeout period
                }
            }
        }
    }
    
    /**
     * Stop the heartbeat coordinator.
     */
    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            Log.i(TAG, "Stopping HeartbeatCoordinator")
            heartbeatJob?.cancel()
            healthCheckJob?.cancel()
        }
    }
    
    /**
     * Capture a point-in-time snapshot of the entire system.
     */
    private suspend fun captureSnapshot(): MarketSnapshot {
        val sequence = sequenceCounter.incrementAndGet()
        val timestamp = Instant.now()
        
        // Capture prices (from cache)
        val prices = priceCache.toMap()
        
        // Capture positions (from PositionManager if available)
        val positions = positionManager?.let { pm ->
            try {
                pm.getOpenPositions().associate { position ->
                    position.symbol to PositionData(
                        symbol = position.symbol,
                        quantity = position.quantity,
                        entryPrice = position.averageEntryPrice,
                        currentPrice = position.currentPrice,
                        unrealizedPnL = position.unrealizedPnl,
                        leverage = position.leverage,
                        stopLoss = position.currentStopPrice,
                        liquidationPrice = position.liquidationPrice
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to capture positions: ${e.message}")
                emptyMap()
            }
        } ?: emptyMap()
        
        // Calculate portfolio value
        val portfolioValue = positions.values.sumOf { pos ->
            pos.quantity * pos.currentPrice
        }
        
        // Calculate margin health
        val marginHealth = calculateMarginHealth(positions, portfolioValue)
        
        // Exchange status (placeholder - wire to actual exchange monitors)
        val exchangeStatus = emptyMap<String, ExchangeHealthData>()
        
        return MarketSnapshot(
            timestamp = timestamp,
            sequenceNumber = sequence,
            prices = prices,
            positions = positions,
            portfolioValue = portfolioValue,
            marginHealth = marginHealth,
            exchangeStatus = exchangeStatus
        )
    }
    
    /**
     * Calculate margin health from current positions.
     */
    private fun calculateMarginHealth(
        positions: Map<String, PositionData>,
        portfolioValue: Double
    ): MarginHealthData {
        // Calculate total margin used
        val usedMargin = positions.values.sumOf { pos ->
            if (pos.leverage != null && pos.leverage > 1.0) {
                (pos.quantity * pos.currentPrice) / pos.leverage
            } else {
                pos.quantity * pos.currentPrice
            }
        }
        
        val totalEquity = portfolioValue
        val availableMargin = (totalEquity - usedMargin).coerceAtLeast(0.0)
        val marginRatio = if (totalEquity > 0) usedMargin / totalEquity else 0.0
        
        val liquidationRisk = when {
            marginRatio < 0.5 -> "SAFE"
            marginRatio < 0.7 -> "MODERATE"
            marginRatio < 0.9 -> "HIGH"
            else -> "CRITICAL"
        }
        
        return MarginHealthData(
            totalEquity = totalEquity,
            usedMargin = usedMargin,
            availableMargin = availableMargin,
            marginRatio = marginRatio,
            isHealthy = marginRatio < 0.8,
            liquidationRisk = liquidationRisk
        )
    }
    
    /**
     * Distribute snapshot to all registered receivers.
     */
    private suspend fun distributeSnapshot(snapshot: MarketSnapshot) {
        val recipientNames = mutableListOf<String>()
        
        receivers.forEach { (systemName, receiver) ->
            try {
                // Send snapshot to receiver
                receiver.onSnapshot(snapshot)
                
                // Update heartbeat tracking (system acknowledged)
                val currentHeartbeat = systemHeartbeats[systemName]
                if (currentHeartbeat != null) {
                    systemHeartbeats[systemName] = currentHeartbeat.copy(
                        lastHeartbeatTime = System.currentTimeMillis(),
                        lastAckSequence = snapshot.sequenceNumber,
                        isAlive = true,
                        missedHeartbeats = 0
                    )
                }
                
                recipientNames.add(systemName)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to distribute to $systemName: ${e.message}", e)
            }
        }
        
        if (recipientNames.isNotEmpty()) {
            Log.d(TAG, "Snapshot #${snapshot.sequenceNumber} distributed to: ${recipientNames.joinToString()}")
        }
    }
    
    /**
     * Check health of all registered systems.
     * Detects frozen/crashed systems that fail to acknowledge snapshots.
     */
    private fun checkSystemHealth() {
        val now = System.currentTimeMillis()
        val healthMap = mutableMapOf<String, Boolean>()
        
        systemHeartbeats.forEach { (systemName, heartbeat) ->
            val timeSinceLastHeartbeat = now - heartbeat.lastHeartbeatTime
            
            if (timeSinceLastHeartbeat > healthCheckTimeoutMs) {
                // System is not responding
                val missedCount = heartbeat.missedHeartbeats + 1
                
                systemHeartbeats[systemName] = heartbeat.copy(
                    isAlive = false,
                    missedHeartbeats = missedCount
                )
                
                Log.w(TAG, "⚠️ SYSTEM TIMEOUT: $systemName (missed $missedCount heartbeats, " +
                        "last seen ${timeSinceLastHeartbeat}ms ago)")
                
                healthMap[systemName] = false
                
            } else {
                // System is healthy
                if (!heartbeat.isAlive) {
                    Log.i(TAG, "✅ SYSTEM RECOVERED: $systemName")
                }
                
                healthMap[systemName] = true
            }
        }
        
        _healthStatus.value = healthMap
    }
    
    /**
     * Get the latest snapshot (thread-safe).
     */
    fun getLatestSnapshot(): MarketSnapshot? = _currentSnapshot.value
    
    /**
     * Get health status for a specific system.
     */
    fun getSystemHealth(systemName: String): SystemHeartbeat? = systemHeartbeats[systemName]
    
    /**
     * Get health status for all systems.
     */
    fun getAllSystemHealth(): Map<String, SystemHeartbeat> = systemHeartbeats.toMap()
    
    /**
     * Check if all systems are healthy.
     */
    fun areAllSystemsHealthy(): Boolean {
        return systemHeartbeats.values.all { it.isAlive }
    }
}
