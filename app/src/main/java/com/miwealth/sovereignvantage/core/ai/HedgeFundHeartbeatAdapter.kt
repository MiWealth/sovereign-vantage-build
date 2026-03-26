/**
 * HEDGE FUND HEARTBEAT ADAPTER
 * 
 * Sovereign Vantage: Arthur Edition V5.18.21
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * PURPOSE:
 * Connects HedgeFundBoardOrchestrator to HeartbeatCoordinator for synchronized
 * market snapshots alongside the main TradingSystem.
 * 
 * CRITICAL BENEFIT:
 * Both trading system and hedge fund board see the SAME market snapshot,
 * preventing desynchronization where:
 * - Trading system sees BTC = $40,000
 * - Hedge fund board sees BTC = $39,500
 * 
 * This ensures hedge decisions are based on the same prices as trading decisions.
 * 
 * WORKFLOW:
 * 1. HeartbeatCoordinator creates snapshot (prices, positions, margin)
 * 2. Adapter receives snapshot via onSnapshot()
 * 3. Adapter convenes hedge fund board with snapshot context
 * 4. Board analyzes: Soros (macro), Guardian (risk), Draper (DeFi), etc.
 * 5. Board decision influences hedge fund strategy execution
 * 6. Adapter acknowledges receipt (heartbeat timestamp updated)
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */

package com.miwealth.sovereignvantage.core.ai

import android.util.Log
import com.miwealth.sovereignvantage.core.trading.engine.HeartbeatReceiver
import com.miwealth.sovereignvantage.core.trading.engine.MarketSnapshot
import com.miwealth.sovereignvantage.core.trading.HedgeFundExecutionBridge
import com.miwealth.sovereignvantage.core.ai.MarketContext
import com.miwealth.sovereignvantage.core.ai.HedgeFundBoardConsensus
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import com.miwealth.sovereignvantage.core.utils.SystemLogger

/**
 * Adapter that connects HedgeFundBoardOrchestrator to HeartbeatCoordinator.
 * 
 * This implements the HeartbeatReceiver interface so the hedge fund board
 * can receive synchronized market snapshots alongside the main trading system.
 * 
 * BUILD #173: Now wired to HedgeFundExecutionBridge - decisions are EXECUTED
 */
class HedgeFundHeartbeatAdapter(
    private val hedgeFundBoard: HedgeFundBoardOrchestrator,
    private val executionBridge: HedgeFundExecutionBridge? = null  // Optional execution
) : HeartbeatReceiver {
    
    companion object {
        private const val TAG = "HedgeFundHB"
    }
    
    // BUILD #267: Persistent scope — never fire-and-forget with CoroutineScope(Dispatchers.Default)
    // Orphaned scopes created every 15s were accumulating and never being GC'd
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    /** Call when adapter is no longer needed to cancel all coroutines */
    fun shutdown() {
        scope.cancel()
    }
    
    // Track last heartbeat for health monitoring
    private val lastHeartbeatTimestamp = AtomicLong(System.currentTimeMillis())
    
    // Snapshot metrics
    private var snapshotsReceived = 0L
    private var snapshotsProcessed = 0L
    private var lastSequenceNumber = 0L
    
    // Board convene metrics
    private var boardConvenesTriggered = 0L
    private var lastBoardConsensus: HedgeFundBoardConsensus? = null
    
    /**
     * Called when HeartbeatCoordinator has a new market snapshot.
     * 
     * This is where we update the hedge fund board with fresh data
     * and optionally trigger board analysis.
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
            
            // Process snapshot for hedge fund board
            processSnapshotForHedgeFund(snapshot)
            
            // Update tracking
            lastSequenceNumber = snapshot.sequenceNumber
            snapshotsProcessed++
            lastHeartbeatTimestamp.set(System.currentTimeMillis())
            
            // Log every 10 snapshots (not too noisy)
            if (snapshotsProcessed % 10 == 0L) {
                Log.d(TAG, "✅ Processed $snapshotsProcessed snapshots " +
                    "(${hedgeFundBoard.getMemberCount()} members active, " +
                    "$boardConvenesTriggered convenes)")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error processing snapshot #${snapshot.sequenceNumber}", e)
        }
    }
    
    /**
     * Process snapshot for hedge fund board analysis.
     * 
     * NOTE: This is a simplified version that stores snapshot data.
     * Full integration requires adding snapshot-aware methods to
     * HedgeFundBoardOrchestrator.
     * 
     * Future enhancement: hedgeFundBoard.conveneWithSnapshot(snapshot)
     */
    private suspend fun processSnapshotForHedgeFund(snapshot: MarketSnapshot) {
        // For now: Log snapshot data for hedge fund board
        // This data will be used when board is convened for actual trading decisions
        
        // BUILD #269: Log snapshot receipt so we can confirm adapter is firing
        SystemLogger.d("⚡ HEDGE FUND HEARTBEAT: Snapshot #${snapshot.sequenceNumber} received | " +
            "${snapshot.prices.size} prices | portfolio=A\$${String.format("%.0f", snapshot.portfolioValue)} | " +
            "convenes so far=$boardConvenesTriggered")

        // Check if margin health is concerning (Guardian should be alerted)
        if (!snapshot.marginHealth.isHealthy) {
            SystemLogger.system("⚠️ HEDGE FUND ALERT: Margin at risk " +
                "(ratio: ${String.format("%.1f%%", snapshot.marginHealth.marginRatio * 100)})")
        }
        
        // Check for cascade risk conditions (Guardian's specialty)
        val volatilePositions = snapshot.positions.values.count { position ->
            val pnlPercent = (position.unrealizedPnL / (position.entryPrice * position.quantity)) * 100.0
            pnlPercent < -5.0  // Position down >5%
        }
        if (volatilePositions > 0) {
            SystemLogger.system("⚠️ HEDGE FUND ALERT: $volatilePositions positions with >5% drawdown")
        }
        
        // BUILD #173: EXECUTION WIRING - Convene board and execute trades!
        
        // Get primary symbol from snapshot (usually first price in map)
        val primarySymbol = snapshot.prices.keys.firstOrNull() ?: "BTC/USD"
        val priceData = snapshot.prices[primarySymbol]
        
        if (priceData == null) {
            Log.w(TAG, "⚠️ No price data for $primarySymbol in snapshot")
            return
        }
        
        // Build MarketContext from snapshot
        // Note: MarketContext expects OHLCV arrays for analysis
        // Since snapshot only has current price, create minimal arrays
        val marketContext = MarketContext(
            symbol = primarySymbol,
            currentPrice = priceData.last,
            opens = listOf(priceData.last),
            highs = listOf(priceData.last),
            lows = listOf(priceData.last),
            closes = listOf(priceData.last),
            volumes = listOf(priceData.volume24h),
            timeframe = "1m"
        )
        
        // Convene the hedge fund board
        boardConvenesTriggered++
        val consensus = hedgeFundBoard.conveneBoardroom(marketContext)
        lastBoardConsensus = consensus
        
        // BUILD #269: SystemLogger so visible in app log viewer
        SystemLogger.system("⚡ HEDGE FUND DECISION #$boardConvenesTriggered: " +
            "$primarySymbol → ${consensus.finalDecision} | " +
            "conf=${String.format("%.0f", consensus.confidence * 100)}% | " +
            "agree=${consensus.unanimousCount}/${hedgeFundBoard.getMemberCount()} | " +
            "cascade=${String.format("%.0f", consensus.cascadeRiskLevel * 100)}%" +
            if (consensus.guardianOverride) " 🛡️ GUARDIAN OVERRIDE" else "")
        
        // Execute trade if bridge is connected
        executionBridge?.let { bridge ->
        // BUILD #267: Use persistent scope — not a new CoroutineScope per call
        // Previously created an orphaned scope every 15s that was never cancelled
        scope.launch {
                val result = bridge.processConsensus(
                    consensus = consensus,
                    symbol = primarySymbol,
                    currentPrice = priceData.last,
                    portfolioValue = snapshot.portfolioValue
                )
                
                when (result) {
                    is com.miwealth.sovereignvantage.core.trading.HedgeFundExecutionResult.OrderPlaced -> {
                        Log.d(TAG, "✅ Hedge Fund Order EXECUTED:")
                        Log.d(TAG, "   Order ID: ${result.orderId}")
                        Log.d(TAG, "   ${result.side} ${result.quantity} ${result.symbol}")
                        Log.d(TAG, "   Reason: ${result.reasoning}")
                    }
                    is com.miwealth.sovereignvantage.core.trading.HedgeFundExecutionResult.OrderRejected -> {
                        Log.w(TAG, "⚠️ Hedge Fund Order REJECTED: ${result.reason}")
                    }
                    is com.miwealth.sovereignvantage.core.trading.HedgeFundExecutionResult.NoAction -> {
                        Log.v(TAG, "📊 Hedge Fund: No action (${result.reason})")
                    }
                }
            }
        } ?: run {
            Log.v(TAG, "📊 Hedge Fund decision made, but no execution bridge connected (decision-only mode)")
        }
        
        // Check margin health warnings
        if (!snapshot.marginHealth.isHealthy) {
            Log.w(TAG, "⚠️ HEDGE FUND ALERT: Margin at risk " +
                "(ratio: ${String.format("%.1f%%", snapshot.marginHealth.marginRatio * 100)})")
        }
        
        // Check cascade risk
        val volatilePositions2 = snapshot.positions.values.count { position ->
            val pnlPercent = (position.unrealizedPnL / (position.entryPrice * position.quantity)) * 100.0
            pnlPercent < -5.0
        }
        if (volatilePositions2 > 0) {
            Log.w(TAG, "⚠️ HEDGE FUND ALERT: $volatilePositions2 positions with >5% drawdown")
        }
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
    override fun getSystemName(): String = "HedgeFundBoard"
    
    /**
     * Get snapshot metrics for monitoring/debugging.
     */
    fun getMetrics(): HedgeFundHeartbeatMetrics {
        return HedgeFundHeartbeatMetrics(
            systemName = getSystemName(),
            snapshotsReceived = snapshotsReceived,
            snapshotsProcessed = snapshotsProcessed,
            boardConvenesTriggered = boardConvenesTriggered,
            lastSequenceNumber = lastSequenceNumber,
            lastHeartbeat = lastHeartbeatTimestamp.get(),
            activeMemberCount = hedgeFundBoard.getMemberCount(),
            activeMemberNames = hedgeFundBoard.getActiveMemberNames()
        )
    }
}

/**
 * Metrics for hedge fund heartbeat adapter monitoring.
 */
data class HedgeFundHeartbeatMetrics(
    val systemName: String,
    val snapshotsReceived: Long,
    val snapshotsProcessed: Long,
    val boardConvenesTriggered: Long,
    val lastSequenceNumber: Long,
    val lastHeartbeat: Long,
    val activeMemberCount: Int,
    val activeMemberNames: List<String>
) {
    val uptimeSeconds: Long
        get() = (System.currentTimeMillis() - lastHeartbeat) / 1000
    
    val processingRate: Double
        get() = if (snapshotsReceived > 0) {
            (snapshotsProcessed.toDouble() / snapshotsReceived.toDouble()) * 100.0
        } else 0.0
}
