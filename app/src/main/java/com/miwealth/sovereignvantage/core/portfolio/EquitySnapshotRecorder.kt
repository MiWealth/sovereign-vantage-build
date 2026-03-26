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
        
        val snapshot = EquitySnapshotEntity(
            id = 0,  // Auto-generated
            periodType = "INTRADAY",  // 15-minute snapshots
            totalEquity = dashboardState.portfolioValue,
            cashBalance = tradingSystemManager.getAIIntegratedSystemBalances()["USDT"] ?: 0.0,
            investedValue = dashboardState.portfolioValue - (tradingSystemManager.getAIIntegratedSystemBalances()["USDT"] ?: 0.0),
            unrealizedPnl = dashboardState.unrealizedPnl,
            realizedPnl = dashboardState.realizedPnl,
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
        
        val snapshot = EquitySnapshotEntity(
            id = 0,  // Auto-generated
            periodType = "DAILY",
            totalEquity = dashboardState.portfolioValue,
            cashBalance = tradingSystemManager.getAIIntegratedSystemBalances()["USDT"] ?: 0.0,
            investedValue = dashboardState.portfolioValue - (tradingSystemManager.getAIIntegratedSystemBalances()["USDT"] ?: 0.0),
            unrealizedPnl = dashboardState.unrealizedPnl,
            realizedPnl = dashboardState.realizedPnl,
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
