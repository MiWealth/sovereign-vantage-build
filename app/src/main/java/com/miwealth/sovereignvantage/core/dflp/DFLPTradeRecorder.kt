package com.miwealth.sovereignvantage.core.dflp

import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * DFLP Trade Recorder
 * Captures all trades (paper and live) for contribution to Decentralized Federated Learning
 */

enum class TradeType {
    PAPER,   // Simulated trade
    LIVE     // Real money trade
}

enum class TradeAction {
    BUY,
    SELL
}

enum class TradeOutcome {
    SUCCESS,
    LOSS,
    PENDING
}

data class MarketSnapshot(
    val timestamp: Instant,
    val price: Double,
    val volume: Double,
    val volatility: Double,
    val trend: String,  // BULLISH, BEARISH, NEUTRAL
    val rsi: Double,
    val macd: Double
)

data class TradeRecord(
    val tradeId: String,
    val userId: String,
    val timestamp: Instant,
    val tradeType: TradeType,
    val asset: String,
    val action: TradeAction,
    val quantity: Double,
    val entryPrice: Double,
    val exitPrice: Double?,
    val profitLoss: Double?,
    val strategyUsed: String,
    val aiModelVersion: String,
    val marketConditions: MarketSnapshot,
    val outcome: TradeOutcome
)

class DFLPTradeRecorder {
    
    private val tradeDatabase = mutableListOf<TradeRecord>()
    private val trainingQueue = mutableListOf<TradeRecord>()
    
    /**
     * Record a trade for DFLP contribution
     * Called after every trade execution (paper or live)
     */
    fun recordTrade(
        tradeId: String,
        userId: String,
        tradeType: TradeType,
        asset: String,
        action: TradeAction,
        quantity: Double,
        entryPrice: Double,
        exitPrice: Double?,
        strategyUsed: String,
        aiModelVersion: String,
        marketConditions: MarketSnapshot
    ): TradeRecord {
        val profitLoss = if (exitPrice != null) {
            calculateProfitLoss(action, entryPrice, exitPrice, quantity)
        } else null
        
        val outcome = determineOutcome(profitLoss)
        
        val record = TradeRecord(
            tradeId = tradeId,
            userId = userId,
            timestamp = Instant.now(),
            tradeType = tradeType,
            asset = asset,
            action = action,
            quantity = quantity,
            entryPrice = entryPrice,
            exitPrice = exitPrice,
            profitLoss = profitLoss,
            strategyUsed = strategyUsed,
            aiModelVersion = aiModelVersion,
            marketConditions = marketConditions,
            outcome = outcome
        )
        
        // Store locally
        tradeDatabase.add(record)
        
        // Queue for local training
        trainingQueue.add(record)
        
        // Trigger training if queue is large enough
        if (trainingQueue.size >= 10) {
            triggerLocalTraining()
        }
        
        return record
    }
    
    private fun calculateProfitLoss(
        action: TradeAction,
        entryPrice: Double,
        exitPrice: Double,
        quantity: Double
    ): Double {
        return when (action) {
            TradeAction.BUY -> (exitPrice - entryPrice) * quantity
            TradeAction.SELL -> (entryPrice - exitPrice) * quantity
        }
    }
    
    private fun determineOutcome(profitLoss: Double?): TradeOutcome {
        return when {
            profitLoss == null -> TradeOutcome.PENDING
            profitLoss > 0 -> TradeOutcome.SUCCESS
            else -> TradeOutcome.LOSS
        }
    }
    
    private fun triggerLocalTraining() {
        // Trigger local model training with queued trades
        val trainer = LocalModelTrainer()
        trainer.trainOnNewTrades(trainingQueue.toList())
        trainingQueue.clear()
    }
    
    fun getTradeHistory(userId: String, limit: Int = 100): List<TradeRecord> {
        return tradeDatabase
            .filter { it.userId == userId }
            .sortedByDescending { it.timestamp }
            .take(limit)
    }
    
    fun getTradesForAggregation(): List<TradeRecord> {
        // Get trades from last 24 hours for DFLP aggregation
        val yesterday = Instant.now().minus(24, ChronoUnit.HOURS)
        return tradeDatabase.filter { it.timestamp.isAfter(yesterday) }
    }
}
