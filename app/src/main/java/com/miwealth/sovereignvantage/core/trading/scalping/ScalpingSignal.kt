/**
 * Scalping Signals - Entry/Exit Signal Types and Criteria
 * 
 * Sovereign Vantage: Arthur Edition V5.5.14
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * Defines the signal types, entry conditions, and exit triggers
 * for the scalping engine.
 * 
 * SIGNAL TYPES:
 * - Momentum Bounce: RSI oversold/overbought reversal
 * - Breakout: Price breaks key level with volume
 * - Mean Reversion: Price returns to VWAP/MA
 * - Order Flow: Imbalance in bid/ask (when available)
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 */
package com.miwealth.sovereignvantage.core.trading.scalping

import java.util.UUID

/**
 * Type of scalping signal.
 */
enum class ScalpSignalType {
    /** RSI bounce from oversold/overbought */
    MOMENTUM_BOUNCE,
    
    /** Price breaks support/resistance with volume */
    BREAKOUT,
    
    /** Price reverts to mean (VWAP, MA) */
    MEAN_REVERSION,
    
    /** Bid/ask imbalance detected */
    ORDER_FLOW,
    
    /** Multiple indicators align */
    CONFLUENCE,
    
    /** Trend continuation after pullback */
    PULLBACK_CONTINUATION
}

/**
 * Direction of the scalp trade.
 */
enum class ScalpDirection {
    LONG,
    SHORT
}

/**
 * Status of a scalping signal.
 */
enum class ScalpSignalStatus {
    /** Signal generated, awaiting confirmation */
    PENDING,
    
    /** Signal confirmed, ready to execute */
    CONFIRMED,
    
    /** Signal executed, position open */
    EXECUTED,
    
    /** Signal expired before execution */
    EXPIRED,
    
    /** Signal cancelled (e.g., conditions changed) */
    CANCELLED,
    
    /** Signal rejected by risk management */
    REJECTED
}

/**
 * Exit reason for completed scalps.
 */
enum class ScalpExitReason {
    /** Target profit reached */
    TARGET_HIT,
    
    /** Stop loss triggered */
    STOP_LOSS,
    
    /** STAHL stair stop triggered */
    STAHL_STOP,
    
    /** Maximum hold time exceeded */
    TIME_LIMIT,
    
    /** Momentum reversal detected */
    MOMENTUM_REVERSAL,
    
    /** Manual exit by user */
    MANUAL,
    
    /** Risk limit triggered (daily loss, etc.) */
    RISK_LIMIT,
    
    /** Breakeven stop triggered */
    BREAKEVEN,
    
    /** Trailing stop triggered */
    TRAILING_STOP
}

/**
 * Entry conditions that triggered a signal.
 */
data class ScalpEntryConditions(
    /** RSI value at signal time */
    val rsi: Double,
    
    /** MACD histogram value */
    val macdHistogram: Double,
    
    /** Volume ratio vs average */
    val volumeRatio: Double,
    
    /** Current spread percentage */
    val spreadPercent: Double,
    
    /** Price distance from VWAP (%) */
    val vwapDistance: Double,
    
    /** ATR value for volatility */
    val atr: Double,
    
    /** Higher timeframe trend (1 = up, -1 = down, 0 = neutral) */
    val higherTfTrend: Int,
    
    /** Momentum score (0-100) */
    val momentumScore: Int,
    
    /** Number of confirming indicators */
    val confirmingIndicators: Int,
    
    /** Key level being tested (support/resistance) */
    val keyLevel: Double? = null,
    
    /** Is this a bounce or break of key level */
    val keyLevelAction: String? = null  // "bounce" or "break"
)

/**
 * A scalping signal with all relevant data.
 */
data class ScalpingSignal(
    /** Unique signal identifier */
    val id: String = UUID.randomUUID().toString(),
    
    /** Trading symbol */
    val symbol: String,
    
    /** Signal type */
    val type: ScalpSignalType,
    
    /** Trade direction */
    val direction: ScalpDirection,
    
    /** Signal status */
    var status: ScalpSignalStatus = ScalpSignalStatus.PENDING,
    
    /** Entry price (suggested or executed) */
    val entryPrice: Double,
    
    /** Initial stop loss price */
    val stopLoss: Double,
    
    /** Target profit price */
    val targetPrice: Double,
    
    /** Stop loss percentage */
    val stopLossPercent: Double,
    
    /** Target profit percentage */
    val targetPercent: Double,
    
    /** Confidence score (0-100) */
    val confidence: Int,
    
    /** Entry conditions that triggered this signal */
    val entryConditions: ScalpEntryConditions,
    
    /** Timestamp when signal was generated */
    val generatedAt: Long = System.currentTimeMillis(),
    
    /** Timestamp when signal expires */
    val expiresAt: Long = System.currentTimeMillis() + 60_000,  // 1 minute default
    
    /** Timestamp when signal was executed (null if not executed) */
    var executedAt: Long? = null,
    
    /** Actual execution price (may differ from entry price) */
    var executionPrice: Double? = null,
    
    /** Reason if signal was rejected */
    var rejectionReason: String? = null
) {
    /** Check if signal has expired */
    fun isExpired(): Boolean = System.currentTimeMillis() > expiresAt
    
    /** Check if signal is still actionable */
    fun isActionable(): Boolean = 
        status == ScalpSignalStatus.PENDING && !isExpired()
    
    /** Risk/reward ratio */
    val riskRewardRatio: Double
        get() = if (stopLossPercent > 0) targetPercent / stopLossPercent else 0.0
    
    /** Time remaining until expiry in milliseconds */
    val timeToExpiry: Long
        get() = maxOf(0, expiresAt - System.currentTimeMillis())
    
    /** Create confirmed version of this signal */
    fun confirm(): ScalpingSignal = copy(status = ScalpSignalStatus.CONFIRMED)
    
    /** Create executed version with actual price */
    fun execute(actualPrice: Double): ScalpingSignal = copy(
        status = ScalpSignalStatus.EXECUTED,
        executedAt = System.currentTimeMillis(),
        executionPrice = actualPrice
    )
    
    /** Create rejected version with reason */
    fun reject(reason: String): ScalpingSignal = copy(
        status = ScalpSignalStatus.REJECTED,
        rejectionReason = reason
    )
}

/**
 * Represents an active scalp position being managed.
 */
data class ActiveScalp(
    /** Reference to original signal */
    val signal: ScalpingSignal,
    
    /** Order ID from exchange */
    val orderId: String,
    
    /** Position size */
    val quantity: Double,
    
    /** Notional value at entry */
    val notionalValue: Double,
    
    /** Current price */
    var currentPrice: Double,
    
    /** Current stop loss (may be adjusted by STAHL) */
    var currentStop: Double,
    
    /** Current STAHL level */
    var stahlLevel: Int = 0,
    
    /** Maximum profit achieved */
    var maxProfitPercent: Double = 0.0,
    
    /** Current unrealized P&L percentage */
    var unrealizedPnlPercent: Double = 0.0,
    
    /** Entry timestamp */
    val entryTime: Long = System.currentTimeMillis()
) {
    /** Hold time in seconds */
    val holdTimeSeconds: Int
        get() = ((System.currentTimeMillis() - entryTime) / 1000).toInt()
    
    /** Unrealized P&L in quote currency */
    val unrealizedPnl: Double
        get() = notionalValue * (unrealizedPnlPercent / 100)
    
    /** Check if max hold time exceeded */
    fun isTimeExpired(maxSeconds: Int): Boolean = holdTimeSeconds >= maxSeconds
}

/**
 * Result of a completed scalp trade.
 */
data class ScalpResult(
    /** Original signal */
    val signal: ScalpingSignal,
    
    /** Entry price */
    val entryPrice: Double,
    
    /** Exit price */
    val exitPrice: Double,
    
    /** Quantity traded */
    val quantity: Double,
    
    /** Realized P&L percentage */
    val pnlPercent: Double,
    
    /** Realized P&L in quote currency */
    val pnlAmount: Double,
    
    /** Exit reason */
    val exitReason: ScalpExitReason,
    
    /** Hold time in seconds */
    val holdTimeSeconds: Int,
    
    /** Final STAHL level reached */
    val finalStahlLevel: Int,
    
    /** Maximum profit during trade */
    val maxProfitPercent: Double,
    
    /** Entry timestamp */
    val entryTime: Long,
    
    /** Exit timestamp */
    val exitTime: Long = System.currentTimeMillis(),
    
    /** Fees paid */
    val fees: Double = 0.0
) {
    /** Net P&L after fees */
    val netPnl: Double
        get() = pnlAmount - fees
    
    /** Was this a winning trade */
    val isWin: Boolean
        get() = pnlPercent > 0
    
    /** Trade efficiency (actual vs potential profit) */
    val efficiency: Double
        get() = if (maxProfitPercent > 0) pnlPercent / maxProfitPercent * 100 else 0.0
}

/**
 * Builder for creating scalping signals.
 */
class ScalpSignalBuilder(private val symbol: String) {
    private var type: ScalpSignalType = ScalpSignalType.MOMENTUM_BOUNCE
    private var direction: ScalpDirection = ScalpDirection.LONG
    private var entryPrice: Double = 0.0
    private var stopLossPercent: Double = 1.0
    private var targetPercent: Double = 0.8
    private var confidence: Int = 50
    private var conditions: ScalpEntryConditions? = null
    private var expiryMs: Long = 60_000
    
    fun type(type: ScalpSignalType) = apply { this.type = type }
    fun direction(direction: ScalpDirection) = apply { this.direction = direction }
    fun long() = apply { this.direction = ScalpDirection.LONG }
    fun short() = apply { this.direction = ScalpDirection.SHORT }
    fun entry(price: Double) = apply { this.entryPrice = price }
    fun stopPercent(percent: Double) = apply { this.stopLossPercent = percent }
    fun targetPercent(percent: Double) = apply { this.targetPercent = percent }
    fun confidence(score: Int) = apply { this.confidence = score.coerceIn(0, 100) }
    fun conditions(conditions: ScalpEntryConditions) = apply { this.conditions = conditions }
    fun expiresIn(ms: Long) = apply { this.expiryMs = ms }
    
    fun build(): ScalpingSignal {
        require(entryPrice > 0) { "Entry price must be positive" }
        require(conditions != null) { "Entry conditions required" }
        
        val stopPrice = if (direction == ScalpDirection.LONG) {
            entryPrice * (1 - stopLossPercent / 100)
        } else {
            entryPrice * (1 + stopLossPercent / 100)
        }
        
        val targetPrice = if (direction == ScalpDirection.LONG) {
            entryPrice * (1 + targetPercent / 100)
        } else {
            entryPrice * (1 - targetPercent / 100)
        }
        
        return ScalpingSignal(
            symbol = symbol,
            type = type,
            direction = direction,
            entryPrice = entryPrice,
            stopLoss = stopPrice,
            targetPrice = targetPrice,
            stopLossPercent = stopLossPercent,
            targetPercent = targetPercent,
            confidence = confidence,
            entryConditions = conditions!!,
            expiresAt = System.currentTimeMillis() + expiryMs
        )
    }
}
