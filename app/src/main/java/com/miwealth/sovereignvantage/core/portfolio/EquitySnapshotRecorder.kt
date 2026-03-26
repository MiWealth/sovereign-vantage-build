package com.miwealth.sovereignvantage.core.portfolio

import com.miwealth.sovereignvantage.core.TradingSystemManager
import com.miwealth.sovereignvantage.data.local.EquitySnapshotDao
import com.miwealth.sovereignvantage.data.local.EquitySnapshotEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BUILD #274: EQUITY SNAPSHOT RECORDER
 * 
 * Records periodic equity snapshots for time-series portfolio analytics.
 * Enables calculation of:
 * - Sharpe Ratio (requires time-series returns)
 * - Sortino Ratio (requires time-series returns)
 * - Max Drawdown (requires equity curve)
 * - Weekly/Monthly returns
 * 
 * Snapshots are recorded:
 * - Every 15 minutes during active trading
 * - Daily at market close (when AUTONOMOUS mode is running)
 * 
 * © 2025 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO: Arthur Iain McManus (1966-2025)
 * 
 * Dedicated to Cathryn for tolerating me and my quirks. 💘
 */

@Singleton
class EquitySnapshotRecorder @Inject constructor(
    private val equitySnapshotDao: EquitySnapshotDao,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    
    private var isActive = false
    private var recordingJob: Job? = null
    
    companion object {
        private const val SNAPSHOT_INTERVAL_MS = 15 * 60 * 1000L  // 15 minutes
        private const val TAG = "EquitySnapshotRecorder"
    }
    
    /**
     * Start recording equity snapshots
     */
    fun start(tradingSystemManager: TradingSystemManager) {
        if (isActive) return
        isActive = true
        
        recordingJob = scope.launch {
            while (isActive) {
                try {
                    recordSnapshot(tradingSystemManager)
                } catch (e: Exception) {
                    println("⚠️ $TAG: Failed to record snapshot: ${e.message}")
                }
                
                delay(SNAPSHOT_INTERVAL_MS)
            }
        }
    }
    
    /**
     * Stop recording snapshots
     */
    fun stop() {
        isActive = false
        recordingJob?.cancel()
        recordingJob = null
    }
    
    /**
     * Record an equity snapshot
     */
    private suspend fun recordSnapshot(tradingSystemManager: TradingSystemManager) {
        val dashboardState = tradingSystemManager.dashboardState.value
        
        // Calculate high water mark (for drawdown tracking)
        val latestSnapshot = equitySnapshotDao.getLatestSnapshot("INTRADAY")
        val currentHighWaterMark = latestSnapshot?.highWaterMark ?: dashboardState.portfolioValue
        val highWaterMark = maxOf(currentHighWaterMark, dashboardState.portfolioValue)
        
        // Calculate drawdown
        val drawdown = highWaterMark - dashboardState.portfolioValue
        val drawdownPercent = if (highWaterMark > 0) (drawdown / highWaterMark) * 100 else 0.0
        
        // Get positions for allocation JSON
        val positions = tradingSystemManager.getPositions()
        val allocationJson = positions.joinToString(",") { position ->
            """{"symbol":"${position.symbol}","value":${position.quantity * position.currentPrice}}"""
        }
        
        // BUILD #276: Calculate period metrics from previous snapshot
        val previousEquity = latestSnapshot?.totalEquity ?: 100000.0
        val periodPnl = dashboardState.portfolioValue - previousEquity
        val periodPnlPercent = if (previousEquity > 0) (periodPnl / previousEquity) * 100 else 0.0
        
        // BUILD #276: Calculate cumulative metrics
        val cumulativePnl = dashboardState.portfolioValue - 100000.0  // Initial balance
        val cumulativePnlPercent = if (100000.0 > 0) (cumulativePnl / 100000.0) * 100 else 0.0
        
        // BUILD #276: Get trade counts from dashboard state
        val cashBalance = 100000.0  // Hardcoded USDT balance (Build #266)
        val positionsValue = dashboardState.portfolioValue - cashBalance
        
        val snapshot = EquitySnapshotEntity(
            id = 0,  // Auto-generated
            snapshotType = "INTRADAY",  // BUILD #276: Fixed - was periodType
            totalEquity = dashboardState.portfolioValue,
            cashBalance = cashBalance,
            positionsValue = positionsValue,  // BUILD #276: Fixed - was investedValue
            unrealizedPnl = dashboardState.unrealizedPnl,
            periodPnl = periodPnl,  // BUILD #276: Added
            periodPnlPercent = periodPnlPercent,  // BUILD #276: Added
            periodTrades = 0,  // BUILD #276: Added - TODO: get from TradeRecorder
            periodWins = 0,  // BUILD #276: Added - TODO: get from TradeRecorder
            periodLosses = 0,  // BUILD #276: Added - TODO: get from TradeRecorder
            cumulativePnl = cumulativePnl,  // BUILD #276: Added
            cumulativePnlPercent = cumulativePnlPercent,  // BUILD #276: Added
            highWaterMark = highWaterMark,
            drawdown = drawdown,
            drawdownPercent = drawdownPercent,
            sharpeRatio = null,  // Calculated by PortfolioAnalytics
            sortinoRatio = null,  // Calculated by PortfolioAnalytics
            winRate = null,  // Calculated by PortfolioAnalytics
            profitFactor = null,  // Calculated by PortfolioAnalytics
            maxDrawdown = null,  // Calculated by PortfolioAnalytics
            allocationJson = "[$allocationJson]",
            timestamp = System.currentTimeMillis()
        )
        
        withContext(Dispatchers.IO) {
            equitySnapshotDao.insertSnapshot(snapshot)
            println("📊 $TAG: Recorded snapshot - Equity: ${String.format("%.2f", dashboardState.portfolioValue)}, DD: ${String.format("%.2f", drawdownPercent)}%")
        }
    }
    
    /**
     * Record a daily snapshot (called at market close or when trading stops)
     */
    suspend fun recordDailySnapshot(tradingSystemManager: TradingSystemManager) {
        val dashboardState = tradingSystemManager.dashboardState.value
        
        // Get latest daily snapshot for high water mark tracking
        val latestDaily = equitySnapshotDao.getLatestSnapshot("DAILY")
        val currentHighWaterMark = latestDaily?.highWaterMark ?: dashboardState.portfolioValue
        val highWaterMark = maxOf(currentHighWaterMark, dashboardState.portfolioValue)
        
        val drawdown = highWaterMark - dashboardState.portfolioValue
        val drawdownPercent = if (highWaterMark > 0) (drawdown / highWaterMark) * 100 else 0.0
        
        // BUILD #276: Calculate period metrics from previous daily snapshot
        val previousEquity = latestDaily?.totalEquity ?: 100000.0
        val periodPnl = dashboardState.portfolioValue - previousEquity
        val periodPnlPercent = if (previousEquity > 0) (periodPnl / previousEquity) * 100 else 0.0
        
        // BUILD #276: Calculate cumulative metrics
        val cumulativePnl = dashboardState.portfolioValue - 100000.0
        val cumulativePnlPercent = if (100000.0 > 0) (cumulativePnl / 100000.0) * 100 else 0.0
        
        // BUILD #276: Get trade counts
        val cashBalance = 100000.0  // Hardcoded USDT balance (Build #266)
        val positionsValue = dashboardState.portfolioValue - cashBalance
        
        val snapshot = EquitySnapshotEntity(
            id = 0,  // Auto-generated
            snapshotType = "DAILY",  // BUILD #276: Fixed - was periodType
            totalEquity = dashboardState.portfolioValue,
            cashBalance = cashBalance,
            positionsValue = positionsValue,  // BUILD #276: Fixed - was investedValue
            unrealizedPnl = dashboardState.unrealizedPnl,
            periodPnl = periodPnl,  // BUILD #276: Added
            periodPnlPercent = periodPnlPercent,  // BUILD #276: Added
            periodTrades = 0,  // BUILD #276: Added - TODO: get from TradeRecorder
            periodWins = 0,  // BUILD #276: Added - TODO: get from TradeRecorder
            periodLosses = 0,  // BUILD #276: Added - TODO: get from TradeRecorder
            cumulativePnl = cumulativePnl,  // BUILD #276: Added
            cumulativePnlPercent = cumulativePnlPercent,  // BUILD #276: Added
            highWaterMark = highWaterMark,
            drawdown = drawdown,
            drawdownPercent = drawdownPercent,
            sharpeRatio = null,
            sortinoRatio = null,
            winRate = null,
            profitFactor = null,
            maxDrawdown = null,
            allocationJson = null,
            timestamp = System.currentTimeMillis()
        )
        
        withContext(Dispatchers.IO) {
            equitySnapshotDao.insertSnapshot(snapshot)
            println("📊 $TAG: Recorded DAILY snapshot - Equity: ${String.format("%.2f", dashboardState.portfolioValue)}")
        }
    }
}
