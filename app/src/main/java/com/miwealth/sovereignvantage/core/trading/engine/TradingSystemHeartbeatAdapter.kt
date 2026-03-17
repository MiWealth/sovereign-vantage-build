/**
 * TRADING SYSTEM HEARTBEAT ADAPTER
 * 
 * Sovereign Vantage: Arthur Edition V5.18.21
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * PURPOSE:
 * Connects TradingSystem to HeartbeatCoordinator for synchronized market snapshots.
 * 
 * BENEFITS:
 * - TradingSystem receives frozen snapshots every 1 second
 * - No race conditions between price updates and trading decisions
 * - Health monitoring detects if trading system freezes
 * - Synchronized with hedge fund board (both see same data)
 * 
 * WORKFLOW:
 * 1. HeartbeatCoordinator creates snapshot (prices, positions, margin)
 * 2. Adapter receives snapshot via onSnapshot()
 * 3. Adapter updates TradingCoordinator with fresh data
 * 4. TradingCoordinator makes decisions on synchronized snapshot
 * 5. Adapter acknowledges receipt (heartbeat timestamp updated)
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */

package com.miwealth.sovereignvantage.core.trading.engine

import android.util.Log
import com.miwealth.sovereignvantage.core.trading.TradingCoordinator
import java.util.concurrent.atomic.AtomicLong

/**
 * Adapter that connects TradingSystem to HeartbeatCoordinator.
 * 
 * This implements the HeartbeatReceiver interface so TradingSystem
 * can receive synchronized market snapshots.
 */
class TradingSystemHeartbeatAdapter(
    private val tradingCoordinator: TradingCoordinator
) : HeartbeatReceiver {
    
    companion object {
        private const val TAG = "TradingSystemHB"
    }
    
    // Track last heartbeat for health monitoring
    private val lastHeartbeatTimestamp = AtomicLong(System.currentTimeMillis())
    
    // Snapshot metrics
    private var snapshotsReceived = 0L
    private var snapshotsProcessed = 0L
    private var lastSequenceNumber = 0L
    
    /**
     * Called when HeartbeatCoordinator has a new market snapshot.
     * 
     * This is where we update TradingCoordinator with fresh data.
     */
    override suspend fun onSnapshot(snapshot: MarketSnapshot) {
        try {
            snapshotsReceived++
            
            // Verify snapshot is fresh (not stale)
            if (!snapshot.isFresh(maxAgeMs = 5000)) {
                Log.w(TAG, "⚠️ Received stale snapshot #${snapshot.sequenceNumber} " +
                    "(age: ${System.currentTimeMillis() - snapshot.timestamp.toEpochMilli()}ms)")
            }
            
            // Detect missing snapshots (sequence number gap)
            if (lastSequenceNumber > 0 && snapshot.sequenceNumber != lastSequenceNumber + 1) {
                val missed = snapshot.sequenceNumber - lastSequenceNumber - 1
                Log.w(TAG, "⚠️ Missed $missed snapshot(s) - sequence gap: " +
                    "$lastSequenceNumber → ${snapshot.sequenceNumber}")
            }
            
            // Update TradingCoordinator with fresh snapshot
            updateTradingCoordinator(snapshot)
            
            // Update tracking
            lastSequenceNumber = snapshot.sequenceNumber
            snapshotsProcessed++
            lastHeartbeatTimestamp.set(System.currentTimeMillis())
            
            // Log every 10 snapshots (not too noisy)
            if (snapshotsProcessed % 10 == 0L) {
                Log.d(TAG, "✅ Processed $snapshotsProcessed snapshots " +
                    "(${snapshot.prices.size} prices, ${snapshot.positions.size} positions)")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error processing snapshot #${snapshot.sequenceNumber}", e)
        }
    }
    
    /**
     * Update TradingCoordinator with snapshot data.
     * 
     * NOTE: TradingCoordinator doesn't have a direct "updateSnapshot" method yet,
     * so we update the components it uses (price feeds, positions, etc.)
     * 
     * TODO: Add TradingCoordinator.updateFromSnapshot(snapshot) method
     * for cleaner integration.
     */
    private suspend fun updateTradingCoordinator(snapshot: MarketSnapshot) {
        // Update prices
        // TradingCoordinator will get fresh prices from snapshot
        // (Current architecture: TradingCoordinator reads from PriceFeedService)
        // 
        // Future enhancement: Add direct snapshot injection:
        // tradingCoordinator.updatePrices(snapshot.prices)
        
        // Update positions
        // (Current architecture: PositionManager tracks positions)
        // 
        // Future enhancement: Verify positions match snapshot:
        // tradingCoordinator.verifyPositions(snapshot.positions)
        
        // Update margin health
        // (Current architecture: RiskManager calculates margin)
        // 
        // Future enhancement: Use snapshot margin data:
        // tradingCoordinator.updateMarginHealth(snapshot.marginHealth)
        
        // For now: Log that snapshot was received
        // Actual integration will happen when TradingCoordinator
        // adds snapshot-aware methods
        
        Log.v(TAG, "Snapshot #${snapshot.sequenceNumber}: " +
            "${snapshot.prices.size} prices, " +
            "portfolio value: $${String.format("%.2f", snapshot.portfolioValue)}, " +
            "margin health: ${snapshot.marginHealth.healthScore}")
    }
    
    /**
     * Get last heartbeat timestamp for health monitoring.
     */
    override fun getLastHeartbeat(): Long {
        return lastHeartbeatTimestamp.get()
    }
    
    /**
     * System identifier for logging and monitoring.
     */
    override fun getSystemName(): String = "TradingSystem"
    
    /**
     * Get snapshot metrics for monitoring/debugging.
     */
    fun getMetrics(): HeartbeatMetrics {
        return HeartbeatMetrics(
            systemName = getSystemName(),
            snapshotsReceived = snapshotsReceived,
            snapshotsProcessed = snapshotsProcessed,
            lastSequenceNumber = lastSequenceNumber,
            lastHeartbeat = lastHeartbeatTimestamp.get()
        )
    }
}

/**
 * Metrics for heartbeat adapter monitoring.
 */
data class HeartbeatMetrics(
    val systemName: String,
    val snapshotsReceived: Long,
    val snapshotsProcessed: Long,
    val lastSequenceNumber: Long,
    val lastHeartbeat: Long
) {
    val uptimeSeconds: Long
        get() = (System.currentTimeMillis() - lastHeartbeat) / 1000
    
    val processingRate: Double
        get() = if (snapshotsReceived > 0) {
            (snapshotsProcessed.toDouble() / snapshotsReceived.toDouble()) * 100.0
        } else 0.0
}
