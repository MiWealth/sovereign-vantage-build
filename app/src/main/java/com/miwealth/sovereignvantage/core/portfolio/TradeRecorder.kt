package com.miwealth.sovereignvantage.core.portfolio

import com.miwealth.sovereignvantage.core.trading.engine.Position
import com.miwealth.sovereignvantage.core.trading.engine.PositionEvent
import com.miwealth.sovereignvantage.core.trading.engine.PositionManager
import com.miwealth.sovereignvantage.data.local.EnhancedTradeDao
import com.miwealth.sovereignvantage.data.local.EnhancedTradeEntity
import com.miwealth.sovereignvantage.domain.TradeSide
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BUILD #274: TRADE RECORDER
 * 
 * Automatically records all closed positions to the database for portfolio analytics.
 * Listens to PositionManager events and persists EnhancedTradeEntity records.
 * 
 * This enables real Sharpe/Sortino/Win Rate calculations from actual trade history.
 * 
 * © 2025 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO: Arthur Iain McManus (1966-2025)
 * 
 * Dedicated to Cathryn for tolerating me and my quirks. 💘
 */

@Singleton
class TradeRecorder @Inject constructor(
    private val enhancedTradeDao: EnhancedTradeDao,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    
    private var isActive = false
    private var eventCollectionJob: Job? = null
    
    /**
     * Start listening to position close events
     */
    fun start(positionManager: PositionManager) {
        if (isActive) return
        isActive = true
        
        eventCollectionJob = scope.launch {
            positionManager.positionEvents
                .filterIsInstance<PositionEvent.Closed>()
                .collect { event ->
                    try {
                        recordClosedTrade(event)
                    } catch (e: Exception) {
                        // Log error but don't crash the app
                        println("⚠️ TradeRecorder: Failed to record trade: ${e.message}")
                    }
                }
        }
    }
    
    /**
     * Stop listening to events
     */
    fun stop() {
        isActive = false
        eventCollectionJob?.cancel()
        eventCollectionJob = null
    }
    
    /**
     * Record a closed position as an EnhancedTradeEntity
     */
    private suspend fun recordClosedTrade(event: PositionEvent.Closed) {
        val position = event.position
        val exitPrice = event.exitPrice
        val realizedPnl = event.realizedPnl
        
        // Extract base and quote assets from symbol (e.g., "BTC/USDT" -> "BTC", "USDT")
        val parts = position.symbol.split("/")
        val baseAsset = parts.getOrNull(0) ?: "BTC"
        val quoteAsset = parts.getOrNull(1) ?: "USDT"
        
        val now = System.currentTimeMillis()
        val holdingPeriodMs = now - position.openTime
        val holdingPeriodDays = (holdingPeriodMs / 86_400_000).toInt()
        
        // Calculate quantities
        val quoteQuantity = position.quantity * exitPrice
        val costBasis = position.quantity * position.averageEntryPrice + position.fees
        val costBasisPerUnit = costBasis / position.quantity
        val proceeds = quoteQuantity - position.fees  // Assuming exit fees = entry fees
        val gainLoss = proceeds - costBasis
        val realizedPnlPercent = if (costBasis > 0) (realizedPnl / costBasis) * 100 else 0.0
        
        // Determine if this is long-term (> 365 days for tax purposes)
        val isLongTerm = holdingPeriodDays > 365
        
        val trade = EnhancedTradeEntity(
            id = UUID.randomUUID().toString(),
            
            // Basic trade info
            symbol = position.symbol,
            baseAsset = baseAsset,
            quoteAsset = quoteAsset,
            side = position.side.toString(),
            orderType = "MARKET",  // Most paper trades are market orders
            
            // Quantities and prices
            quantity = position.quantity,
            executedPrice = exitPrice,
            requestedPrice = exitPrice,
            quoteQuantity = quoteQuantity,
            
            // Cost basis (CRITICAL for taxes)
            costBasis = costBasis,
            costBasisPerUnit = costBasisPerUnit,
            acquisitionDate = position.openTime,
            disposalDate = now,
            holdingPeriodDays = holdingPeriodDays,
            isLongTerm = isLongTerm,
            
            // Fees breakdown
            tradingFee = position.fees,
            tradingFeeCurrency = quoteAsset,
            networkFee = null,
            totalFees = position.fees,
            
            // P&L tracking
            realizedPnl = realizedPnl,
            realizedPnlPercent = realizedPnlPercent,
            proceeds = proceeds,
            gainLoss = gainLoss,
            
            // Tax lot linkage
            taxLotId = null,  // TODO: Link to tax lot system
            openingTradeId = position.id,
            closingTradeIds = position.id,
            remainingQuantity = 0.0,
            isFullyClosed = true,
            
            // Exchange info
            exchange = "Paper",
            exchangeOrderId = position.id,
            clientOrderId = position.id,
            
            // STAHL tracking
            entryStopLoss = position.stopLoss,
            entryTakeProfit = position.takeProfit,
            exitStopLevel = null,  // TODO: Track STAHL stop level at exit
            exitReason = "Position Closed",
            maxProfitDuringTrade = position.unrealizedPnL,  // Approximate
            
            // AI reasoning reference
            aiDecisionId = null,  // TODO: Link to AIBoardDecisionEntity
            signalConfidence = null,
            boardConsensusScore = null,
            
            // Timestamps
            createdAt = position.openTime,
            updatedAt = now,
            
            // User notes
            notes = null,
            tags = null
        )
        
        // Save to database
        withContext(Dispatchers.IO) {
            enhancedTradeDao.insertTrade(trade)
            println("✅ TradeRecorder: Recorded ${position.symbol} trade - P&L: ${String.format("%.2f", realizedPnl)}")
        }
    }
}
