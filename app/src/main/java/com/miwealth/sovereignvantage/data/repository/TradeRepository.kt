package com.miwealth.sovereignvantage.data.repository

import com.miwealth.sovereignvantage.data.local.*
import com.miwealth.sovereignvantage.core.trading.engine.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Trade Repository - Single source of truth for trade data
 * 
 * Abstracts data access from database and provides clean API for:
 * - Trade history
 * - Position tracking
 * - Portfolio snapshots
 * - AI signals
 * 
 * © 2025 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO: Arthur Iain McManus (1966-2025)
 */

@Singleton
class TradeRepository @Inject constructor(
    private val database: TradeDatabase
) {
    private val tradeDao = database.tradeDao()
    private val positionDao = database.positionDao()
    private val snapshotDao = database.portfolioSnapshotDao()
    private val alertDao = database.priceAlertDao()
    private val signalDao = database.aiSignalDao()
    
    // ========================================================================
    // TRADES
    // ========================================================================
    
    fun getAllTrades(): Flow<List<TradeEntity>> = tradeDao.getAllTrades()
    
    fun getTradesForSymbol(symbol: String): Flow<List<TradeEntity>> = 
        tradeDao.getTradesForSymbol(symbol)
    
    fun getTradesInRange(startMs: Long, endMs: Long): Flow<List<TradeEntity>> =
        tradeDao.getTradesInRange(startMs, endMs)
    
    suspend fun getRecentTrades(limit: Int = 50): List<TradeEntity> =
        tradeDao.getRecentTrades(limit)
    
    suspend fun getTotalRealizedPnl(): Double = tradeDao.getTotalRealizedPnl() ?: 0.0
    
    suspend fun getTotalFees(): Double = tradeDao.getTotalFees() ?: 0.0
    
    suspend fun getTradeStats(): TradeStats {
        val buyCount = tradeDao.getTradeCountBySide("BUY")
        val sellCount = tradeDao.getTradeCountBySide("SELL")
        val totalPnl = getTotalRealizedPnl()
        val totalFees = getTotalFees()
        
        return TradeStats(
            totalTrades = buyCount + sellCount,
            buyTrades = buyCount,
            sellTrades = sellCount,
            totalRealizedPnl = totalPnl,
            totalFees = totalFees,
            netPnl = totalPnl - totalFees
        )
    }
    
    suspend fun recordTrade(
        executedOrder: ExecutedOrder,
        realizedPnl: Double? = null,
        realizedPnlPercent: Double? = null,
        stahlLevel: Int = 0,
        exitReason: String? = null,
        openingTradeId: String? = null
    ) {
        val trade = TradeEntity(
            id = executedOrder.orderId,
            symbol = executedOrder.symbol,
            side = executedOrder.side.name,
            orderType = executedOrder.type.name,
            quantity = executedOrder.executedQuantity,
            price = executedOrder.executedPrice,
            fee = executedOrder.fee,
            feeCurrency = executedOrder.feeCurrency,
            realizedPnl = realizedPnl,
            realizedPnlPercent = realizedPnlPercent,
            exchange = executedOrder.exchange,
            orderId = executedOrder.orderId,
            timestamp = executedOrder.timestamp,
            stahlLevel = stahlLevel,
            exitReason = exitReason,
            openingTradeId = openingTradeId
        )
        tradeDao.insertTrade(trade)
    }
    
    // ========================================================================
    // POSITIONS
    // ========================================================================
    
    fun getActivePositions(): Flow<List<PositionEntity>> = positionDao.getActivePositions()
    
    fun getClosedPositions(): Flow<List<PositionEntity>> = positionDao.getClosedPositions()
    
    suspend fun getPositionById(id: String): PositionEntity? = positionDao.getPositionById(id)
    
    suspend fun savePosition(position: Position) {
        val entity = PositionEntity(
            id = position.id,
            symbol = position.symbol,
            side = position.side.name,
            quantity = position.quantity,
            averageEntry = position.averageEntryPrice,
            currentPrice = position.currentPrice,
            unrealizedPnl = position.unrealizedPnl,
            unrealizedPnlPercent = position.unrealizedPnlPercent,
            leverage = position.leverage,
            margin = position.margin,
            initialStop = position.initialStopPrice,
            currentStop = position.currentStopPrice,
            takeProfit = position.takeProfitPrice,
            stahlLevel = position.stahlLevel,
            maxProfitPercent = position.maxProfitPercent,
            exchange = position.exchange,
            openTime = position.openTime,
            lastUpdate = position.lastUpdateTime,
            isActive = true
        )
        positionDao.insertPosition(entity)
    }
    
    suspend fun closePosition(positionId: String) {
        positionDao.closePosition(positionId, System.currentTimeMillis())
    }
    
    // ========================================================================
    // PORTFOLIO SNAPSHOTS
    // ========================================================================
    
    suspend fun getRecentSnapshots(limit: Int = 30): List<PortfolioSnapshotEntity> =
        snapshotDao.getRecentSnapshots(limit)
    
    suspend fun getSnapshotsInRange(startMs: Long, endMs: Long): List<PortfolioSnapshotEntity> =
        snapshotDao.getSnapshotsInRange(startMs, endMs)
    
    suspend fun getLatestSnapshot(): PortfolioSnapshotEntity? = snapshotDao.getLatestSnapshot()
    
    suspend fun saveSnapshot(
        totalValue: Double,
        cashBalance: Double,
        investedValue: Double,
        unrealizedPnl: Double,
        realizedPnl: Double,
        totalPositions: Int,
        dailyPnl: Double,
        dailyPnlPercent: Double
    ) {
        val snapshot = PortfolioSnapshotEntity(
            timestamp = System.currentTimeMillis(),
            totalValue = totalValue,
            cashBalance = cashBalance,
            investedValue = investedValue,
            unrealizedPnl = unrealizedPnl,
            realizedPnl = realizedPnl,
            totalPositions = totalPositions,
            dailyPnl = dailyPnl,
            dailyPnlPercent = dailyPnlPercent
        )
        snapshotDao.insertSnapshot(snapshot)
    }
    
    // ========================================================================
    // PRICE ALERTS
    // ========================================================================
    
    fun getActiveAlerts(): Flow<List<PriceAlertEntity>> = alertDao.getActiveAlerts()
    
    suspend fun getAlertsForSymbol(symbol: String): List<PriceAlertEntity> =
        alertDao.getAlertsForSymbol(symbol)
    
    suspend fun createAlert(
        symbol: String,
        condition: String,
        targetPrice: Double,
        notes: String = ""
    ) {
        val alert = PriceAlertEntity(
            id = "alert-${System.currentTimeMillis()}",
            symbol = symbol,
            condition = condition,
            targetPrice = targetPrice,
            createdAt = System.currentTimeMillis(),
            notes = notes
        )
        alertDao.insertAlert(alert)
    }
    
    suspend fun triggerAlert(alertId: String) {
        alertDao.triggerAlert(alertId, System.currentTimeMillis())
    }
    
    suspend fun deleteAlert(alert: PriceAlertEntity) = alertDao.deleteAlert(alert)
    
    // ========================================================================
    // AI SIGNALS
    // ========================================================================
    
    suspend fun getRecentSignals(limit: Int = 20): List<AISignalEntity> =
        signalDao.getRecentSignals(limit)
    
    suspend fun getSignalsForSymbol(symbol: String, limit: Int = 10): List<AISignalEntity> =
        signalDao.getSignalsForSymbol(symbol, limit)
    
    suspend fun recordSignal(
        symbol: String,
        signal: String,
        score: Double,
        confidence: Double,
        unanimousCount: Int,
        reasoning: String
    ) {
        val entity = AISignalEntity(
            symbol = symbol,
            signal = signal,
            score = score,
            confidence = confidence,
            unanimousCount = unanimousCount,
            reasoning = reasoning,
            timestamp = System.currentTimeMillis()
        )
        signalDao.insertSignal(entity)
    }
    
    suspend fun markSignalActedUpon(signalId: Long) = signalDao.markActedUpon(signalId)
    
    // ========================================================================
    // CLEANUP
    // ========================================================================
    
    suspend fun cleanupOldData(daysToKeep: Int = 365) {
        val cutoff = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
        tradeDao.deleteOldTrades(cutoff)
        snapshotDao.deleteOldSnapshots(cutoff)
        signalDao.deleteOldSignals(cutoff)
    }
}

data class TradeStats(
    val totalTrades: Int,
    val buyTrades: Int,
    val sellTrades: Int,
    val totalRealizedPnl: Double,
    val totalFees: Double,
    val netPnl: Double
)
