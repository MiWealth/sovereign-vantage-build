package com.miwealth.sovereignvantage.core.trading.scalping

/**
 * Scalping Engine - High-Frequency Trading Engine
 * 
 * Sovereign Vantage: Arthur Edition V5.17.0
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * Core engine for executing scalping strategies with tight stops,
 * quick targets, and STAHL-based profit locking.
 * 
 * SIGNAL DETECTION:
 * 1. RSI oversold/overbought bounces
 * 2. Volume spike confirmation
 * 3. MACD histogram divergence
 * 4. Higher timeframe trend alignment
 * 5. Key level interaction (support/resistance)
 * 
 * POSITION MANAGEMENT:
 * - Ultra-tight STAHL levels for quick profit locking
 * - Time-based exits for stale positions
 * - Momentum reversal detection
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 */



import com.miwealth.sovereignvantage.core.indicators.*
import com.miwealth.sovereignvantage.core.trading.StahlStairStop
import com.miwealth.sovereignvantage.core.trading.assets.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * Events emitted by the scalping engine.
 */
sealed class ScalpingEvent {
    data class SignalGenerated(val signal: ScalpingSignal) : ScalpingEvent()
    data class SignalConfirmed(val signal: ScalpingSignal) : ScalpingEvent()
    data class SignalExpired(val signal: ScalpingSignal) : ScalpingEvent()
    data class SignalRejected(val signal: ScalpingSignal, val reason: String) : ScalpingEvent()
    data class PositionOpened(val scalp: ActiveScalp) : ScalpingEvent()
    data class PositionUpdated(val scalp: ActiveScalp) : ScalpingEvent()
    data class StahlLevelReached(val scalp: ActiveScalp, val level: Int) : ScalpingEvent()
    data class PositionClosed(val result: ScalpResult) : ScalpingEvent()
    data class RiskLimitHit(val reason: String) : ScalpingEvent()
    data class EngineStarted(val config: ScalpingConfig) : ScalpingEvent()
    data class EngineStopped(val stats: ScalpingStats) : ScalpingEvent()
    data class Error(val message: String, val exception: Throwable? = null) : ScalpingEvent()
}

/**
 * Price data for scalping analysis.
 */
data class ScalpPriceData(
    val symbol: String,
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val bid: Double? = null,
    val ask: Double? = null
) {
    val spread: Double?
        get() = if (bid != null && ask != null) ask - bid else null
    
    val spreadPercent: Double?
        get() = spread?.let { it / close * 100 }
    
    val midPrice: Double
        get() = if (bid != null && ask != null) (bid + ask) / 2 else close
}

/**
 * Core scalping engine.
 */
class ScalpingEngine(
    config: ScalpingConfig = ScalpingConfig.CRYPTO_DEFAULT,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    
    // ========================================================================
    // STATE
    // ========================================================================
    
    /** Current configuration (can be updated at runtime) */
    private var config: ScalpingConfig = config
        private set
    
    private val isRunning = AtomicBoolean(false)
    
    private val _events = MutableSharedFlow<ScalpingEvent>(replay = 1)
    val events: SharedFlow<ScalpingEvent> = _events.asSharedFlow()
    
    private val _stats = MutableStateFlow(ScalpingStats())
    val stats: StateFlow<ScalpingStats> = _stats.asStateFlow()
    
    /** Pending signals awaiting execution */
    private val pendingSignals = ConcurrentHashMap<String, ScalpingSignal>()
    
    /** Active scalp positions */
    private val activeScalps = ConcurrentHashMap<String, ActiveScalp>()
    
    /** Price history per symbol (for indicator calculation) */
    private val priceHistory = ConcurrentHashMap<String, MutableList<ScalpPriceData>>()
    
    /** STAHL stop calculator */
    private val stahl = StahlStairStop()
    
    /** Hourly trade counter */
    private var tradesThisHour = 0
    private var hourStartTime = System.currentTimeMillis()
    
    /** Daily stats */
    private var dailyPnl = 0.0
    private var dayStartTime = System.currentTimeMillis()
    
    /** Cooldown tracking */
    private var cooldownUntil: Long = 0
    
    companion object {
        private const val TAG = "ScalpingEngine"
        private const val MAX_PRICE_HISTORY = 200
        private const val HOUR_MS = 3600_000L
        private const val DAY_MS = 86400_000L
    }
    
    // ========================================================================
    // LIFECYCLE
    // ========================================================================
    
    /**
     * Start the scalping engine.
     */
    fun start() {
        if (isRunning.compareAndSet(false, true)) {
            android.util.Log.i(TAG, "Starting scalping engine with mode: ${config.mode}")
            resetDailyStats()
            emitEvent(ScalpingEvent.EngineStarted(config))
            
            // Start position monitoring loop
            scope.launch {
                monitorPositions()
            }
            
            // Start signal expiry cleanup loop
            scope.launch {
                cleanupExpiredSignals()
            }
        }
    }
    
    /**
     * Stop the scalping engine.
     */
    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            android.util.Log.i(TAG, "Stopping scalping engine")
            emitEvent(ScalpingEvent.EngineStopped(_stats.value))
        }
    }
    
    /**
     * Check if engine is running.
     */
    fun isRunning(): Boolean = isRunning.get()
    
    // ========================================================================
    // PRICE FEED
    // ========================================================================
    
    /**
     * Feed price data to the engine.
     * Call this with each new candle/tick.
     */
    fun onPriceUpdate(data: ScalpPriceData) {
        if (!isRunning.get()) return
        
        // Store price history
        val history = priceHistory.getOrPut(data.symbol) { mutableListOf() }
        history.add(data)
        if (history.size > MAX_PRICE_HISTORY) {
            history.removeAt(0)
        }
        
        // Update active positions
        updateActiveScalps(data)
        
        // Check for new signals
        if (canGenerateSignal()) {
            analyzeForSignals(data.symbol, history)
        }
    }
    
    /**
     * Feed bid/ask data for spread monitoring.
     */
    fun onQuoteUpdate(symbol: String, bid: Double, ask: Double) {
        priceHistory[symbol]?.lastOrNull()?.let { last ->
            // Update last price with bid/ask
            val updated = last.copy(bid = bid, ask = ask)
            priceHistory[symbol]?.let { history ->
                if (history.isNotEmpty()) {
                    history[history.lastIndex] = updated
                }
            }
        }
    }
    
    // ========================================================================
    // SIGNAL GENERATION
    // ========================================================================
    
    /**
     * Analyze price data for scalping opportunities.
     */
    private fun analyzeForSignals(symbol: String, history: List<ScalpPriceData>) {
        if (history.size < 50) return  // Need enough data
        
        val closes = history.map { it.close }.toDoubleArray()
        val highs = history.map { it.high }.toDoubleArray()
        val lows = history.map { it.low }.toDoubleArray()
        val volumes = history.map { it.volume }.toDoubleArray()
        val current = history.last()
        
        // Check spread constraint
        current.spreadPercent?.let { spread ->
            if (spread > config.maxSpreadPercent) {
                return  // Spread too wide
            }
        }
        
        // Calculate indicators
        val rsi = calculateRSI(closes, 14)
        val macd = calculateMACD(closes)
        val atr = calculateATR(highs, lows, closes, 14)
        val volumeAvg = volumes.takeLast(20).average()
        val volumeRatio = if (volumeAvg > 0) current.volume / volumeAvg else 1.0
        val vwap = calculateVWAP(highs, lows, closes, volumes)
        val vwapDistance = (current.close - vwap) / vwap * 100
        
        // Higher timeframe trend (simplified - use last 50 bars)
        val higherTfTrend = determineHigherTfTrend(closes)
        
        // Calculate momentum score
        val momentumScore = calculateMomentumScore(rsi, macd.histogram, volumeRatio, higherTfTrend)
        
        // Build entry conditions
        val conditions = ScalpEntryConditions(
            rsi = rsi,
            macdHistogram = macd.histogram,
            volumeRatio = volumeRatio,
            spreadPercent = current.spreadPercent ?: 0.0,
            vwapDistance = vwapDistance,
            atr = atr,
            higherTfTrend = higherTfTrend,
            momentumScore = momentumScore,
            confirmingIndicators = countConfirmingIndicators(rsi, macd, volumeRatio, higherTfTrend)
        )
        
        // Check for momentum bounce signals
        checkMomentumBounce(symbol, current, conditions)
        
        // Check for mean reversion signals
        checkMeanReversion(symbol, current, conditions, vwap)
    }
    
    /**
     * Check for RSI momentum bounce signal.
     */
    private fun checkMomentumBounce(
        symbol: String,
        current: ScalpPriceData,
        conditions: ScalpEntryConditions
    ) {
        // Long signal: RSI oversold and turning up
        if (conditions.rsi < config.rsiOversold && 
            conditions.macdHistogram > 0 &&
            conditions.momentumScore >= config.minMomentumScore &&
            (!config.requireTrendAlignment || conditions.higherTfTrend >= 0)) {
            
            generateSignal(
                symbol = symbol,
                type = ScalpSignalType.MOMENTUM_BOUNCE,
                direction = ScalpDirection.LONG,
                entryPrice = current.close,
                conditions = conditions,
                confidence = conditions.momentumScore
            )
        }
        
        // Short signal: RSI overbought and turning down
        if (conditions.rsi > config.rsiOverbought && 
            conditions.macdHistogram < 0 &&
            conditions.momentumScore >= config.minMomentumScore &&
            (!config.requireTrendAlignment || conditions.higherTfTrend <= 0)) {
            
            generateSignal(
                symbol = symbol,
                type = ScalpSignalType.MOMENTUM_BOUNCE,
                direction = ScalpDirection.SHORT,
                entryPrice = current.close,
                conditions = conditions,
                confidence = conditions.momentumScore
            )
        }
    }
    
    /**
     * Check for mean reversion to VWAP signal.
     */
    private fun checkMeanReversion(
        symbol: String,
        current: ScalpPriceData,
        conditions: ScalpEntryConditions,
        vwap: Double
    ) {
        val distance = abs(conditions.vwapDistance)
        
        // Price far from VWAP with momentum reversing
        if (distance > 0.5 && conditions.volumeRatio >= config.minVolumeRatio) {
            val direction = if (current.close < vwap) ScalpDirection.LONG else ScalpDirection.SHORT
            
            // Require momentum aligning with reversion
            val momentumAligned = (direction == ScalpDirection.LONG && conditions.macdHistogram > 0) ||
                                 (direction == ScalpDirection.SHORT && conditions.macdHistogram < 0)
            
            if (momentumAligned && conditions.momentumScore >= config.minMomentumScore - 10) {
                generateSignal(
                    symbol = symbol,
                    type = ScalpSignalType.MEAN_REVERSION,
                    direction = direction,
                    entryPrice = current.close,
                    conditions = conditions,
                    confidence = (conditions.momentumScore * 0.8 + distance * 10).toInt().coerceIn(0, 100)
                )
            }
        }
    }
    
    /**
     * Generate and emit a scalping signal.
     */
    private fun generateSignal(
        symbol: String,
        type: ScalpSignalType,
        direction: ScalpDirection,
        entryPrice: Double,
        conditions: ScalpEntryConditions,
        confidence: Int
    ) {
        // Check if we already have a pending signal for this symbol/direction
        val existingKey = pendingSignals.keys.find { 
            pendingSignals[it]?.symbol == symbol && 
            pendingSignals[it]?.direction == direction &&
            pendingSignals[it]?.isActionable() == true
        }
        if (existingKey != null) return  // Already have a pending signal
        
        val signal = ScalpSignalBuilder(symbol)
            .type(type)
            .direction(direction)
            .entry(entryPrice)
            .stopPercent(config.getEffectiveStop())
            .targetPercent(config.getEffectiveTarget())
            .confidence(confidence)
            .conditions(conditions)
            .expiresIn(60_000)  // 1 minute expiry
            .build()
        
        pendingSignals[signal.id] = signal
        
        android.util.Log.i(TAG, "Signal generated: ${signal.type} ${signal.direction} $symbol @ $entryPrice")
        emitEvent(ScalpingEvent.SignalGenerated(signal))
    }
    
    // ========================================================================
    // SIGNAL EXECUTION
    // ========================================================================
    
    /**
     * Confirm and execute a pending signal.
     * Call this when user approves or in auto mode.
     */
    fun confirmSignal(signalId: String, executionPrice: Double, quantity: Double, orderId: String): Boolean {
        val signal = pendingSignals[signalId] ?: return false
        
        if (!signal.isActionable()) {
            emitEvent(ScalpingEvent.SignalRejected(signal, "Signal expired or not actionable"))
            pendingSignals.remove(signalId)
            return false
        }
        
        // Validate against risk limits
        val rejection = validateRiskLimits()
        if (rejection != null) {
            val rejectedSignal = signal.reject(rejection)
            pendingSignals[signalId] = rejectedSignal
            emitEvent(ScalpingEvent.SignalRejected(rejectedSignal, rejection))
            pendingSignals.remove(signalId)
            return false
        }
        
        // Execute
        val executedSignal = signal.execute(executionPrice)
        pendingSignals.remove(signalId)
        
        // Create active scalp position
        val scalp = ActiveScalp(
            signal = executedSignal,
            orderId = orderId,
            quantity = quantity,
            notionalValue = executionPrice * quantity,
            currentPrice = executionPrice,
            currentStop = executedSignal.stopLoss
        )
        
        activeScalps[signalId] = scalp
        tradesThisHour++
        updateStats { it.copy(scalpsThisHour = tradesThisHour, scalpsToday = it.scalpsToday + 1) }
        
        emitEvent(ScalpingEvent.SignalConfirmed(executedSignal))
        emitEvent(ScalpingEvent.PositionOpened(scalp))
        
        return true
    }
    
    /**
     * Cancel a pending signal.
     */
    fun cancelSignal(signalId: String) {
        pendingSignals[signalId]?.let { signal ->
            val cancelled = signal.copy(status = ScalpSignalStatus.CANCELLED)
            pendingSignals.remove(signalId)
            emitEvent(ScalpingEvent.SignalExpired(cancelled))
        }
    }
    
    // ========================================================================
    // POSITION MANAGEMENT
    // ========================================================================
    
    /**
     * Update active scalp positions with new price.
     */
    private fun updateActiveScalps(data: ScalpPriceData) {
        activeScalps.values.filter { it.signal.symbol == data.symbol }.forEach { scalp ->
            updateScalpPosition(scalp, data)
        }
    }
    
    /**
     * Update a single scalp position.
     */
    private fun updateScalpPosition(scalp: ActiveScalp, data: ScalpPriceData) {
        val direction = scalp.signal.direction
        val entryPrice = scalp.signal.executionPrice ?: scalp.signal.entryPrice
        
        // Update current price
        scalp.currentPrice = data.close
        
        // Calculate current P&L
        scalp.unrealizedPnlPercent = if (direction == ScalpDirection.LONG) {
            (data.close - entryPrice) / entryPrice * 100
        } else {
            (entryPrice - data.close) / entryPrice * 100
        }
        
        // Update max profit
        if (scalp.unrealizedPnlPercent > scalp.maxProfitPercent) {
            scalp.maxProfitPercent = scalp.unrealizedPnlPercent
        }
        
        // Update STAHL stop
        val stahlResult = stahl.calculateStairStop(
            entryPrice = entryPrice,
            maxProfitPercent = scalp.maxProfitPercent,
            direction = if (direction == ScalpDirection.LONG) "long" else "short",
            levels = config.getStahlLevels()
        )
        
        // Check for STAHL level advancement
        if (stahlResult.currentLevel > scalp.stahlLevel) {
            scalp.stahlLevel = stahlResult.currentLevel
            scalp.currentStop = stahlResult.stopPrice
            emitEvent(ScalpingEvent.StahlLevelReached(scalp, stahlResult.currentLevel))
        }
        
        // Check exit conditions
        val exitReason = checkExitConditions(scalp, data, stahlResult.stopPrice)
        
        if (exitReason != null) {
            closeScalp(scalp, data.close, exitReason)
        } else {
            emitEvent(ScalpingEvent.PositionUpdated(scalp))
        }
    }
    
    /**
     * Check if scalp should be exited.
     */
    private fun checkExitConditions(
        scalp: ActiveScalp, 
        data: ScalpPriceData,
        currentStop: Double
    ): ScalpExitReason? {
        val direction = scalp.signal.direction
        
        // Target hit
        if (direction == ScalpDirection.LONG && data.high >= scalp.signal.targetPrice) {
            return ScalpExitReason.TARGET_HIT
        }
        if (direction == ScalpDirection.SHORT && data.low <= scalp.signal.targetPrice) {
            return ScalpExitReason.TARGET_HIT
        }
        
        // Stop hit (STAHL or initial)
        if (direction == ScalpDirection.LONG && data.low <= currentStop) {
            return if (scalp.stahlLevel > 0) ScalpExitReason.STAHL_STOP else ScalpExitReason.STOP_LOSS
        }
        if (direction == ScalpDirection.SHORT && data.high >= currentStop) {
            return if (scalp.stahlLevel > 0) ScalpExitReason.STAHL_STOP else ScalpExitReason.STOP_LOSS
        }
        
        // Time limit
        if (config.maxHoldTimeSeconds > 0 && scalp.isTimeExpired(config.maxHoldTimeSeconds)) {
            return ScalpExitReason.TIME_LIMIT
        }
        
        // Momentum reversal (if enabled)
        if (config.exitOnMomentumReversal) {
            val history = priceHistory[scalp.signal.symbol]
            if (history != null && history.size >= 20) {
                val closes = history.takeLast(20).map { it.close }.toDoubleArray()
                val rsi = calculateRSI(closes, 14)
                
                // Exit long if RSI now overbought
                if (direction == ScalpDirection.LONG && rsi > config.rsiOverbought) {
                    return ScalpExitReason.MOMENTUM_REVERSAL
                }
                // Exit short if RSI now oversold
                if (direction == ScalpDirection.SHORT && rsi < config.rsiOversold) {
                    return ScalpExitReason.MOMENTUM_REVERSAL
                }
            }
        }
        
        return null
    }
    
    /**
     * Close a scalp position.
     */
    private fun closeScalp(scalp: ActiveScalp, exitPrice: Double, reason: ScalpExitReason) {
        val entryPrice = scalp.signal.executionPrice ?: scalp.signal.entryPrice
        val direction = scalp.signal.direction
        
        val pnlPercent = if (direction == ScalpDirection.LONG) {
            (exitPrice - entryPrice) / entryPrice * 100
        } else {
            (entryPrice - exitPrice) / entryPrice * 100
        }
        
        val result = ScalpResult(
            signal = scalp.signal,
            entryPrice = entryPrice,
            exitPrice = exitPrice,
            quantity = scalp.quantity,
            pnlPercent = pnlPercent,
            pnlAmount = scalp.notionalValue * (pnlPercent / 100),
            exitReason = reason,
            holdTimeSeconds = scalp.holdTimeSeconds,
            finalStahlLevel = scalp.stahlLevel,
            maxProfitPercent = scalp.maxProfitPercent,
            entryTime = scalp.entryTime
        )
        
        activeScalps.remove(scalp.signal.id)
        
        // Update stats
        dailyPnl += pnlPercent
        updateStatsAfterTrade(result)
        
        // Check for cooldown
        if (pnlPercent < 0) {
            cooldownUntil = System.currentTimeMillis() + (config.cooldownAfterLossSeconds * 1000L)
        }
        
        // Check daily loss limit
        if (dailyPnl < -config.maxDailyLossPercent) {
            emitEvent(ScalpingEvent.RiskLimitHit("Daily loss limit reached: $dailyPnl%"))
        }
        
        android.util.Log.i(TAG, "Scalp closed: ${scalp.signal.symbol} ${reason.name} P&L: $pnlPercent%")
        emitEvent(ScalpingEvent.PositionClosed(result))
    }
    
    /**
     * Manually close a scalp position.
     */
    fun closeScalpManual(signalId: String, exitPrice: Double) {
        activeScalps[signalId]?.let { scalp ->
            closeScalp(scalp, exitPrice, ScalpExitReason.MANUAL)
        }
    }
    
    // ========================================================================
    // INDICATOR CALCULATIONS
    // ========================================================================
    
    private fun calculateRSI(closes: DoubleArray, period: Int = 14): Double {
        if (closes.size < period + 1) return 50.0
        
        var gains = 0.0
        var losses = 0.0
        
        for (i in closes.size - period until closes.size) {
            val change = closes[i] - closes[i - 1]
            if (change > 0) gains += change else losses -= change
        }
        
        val avgGain = gains / period
        val avgLoss = losses / period
        
        return if (avgLoss == 0.0) 100.0 else 100 - (100 / (1 + avgGain / avgLoss))
    }
    
    private data class MACDResult(val macd: Double, val signal: Double, val histogram: Double)
    
    private fun calculateMACD(closes: DoubleArray): MACDResult {
        if (closes.size < 26) return MACDResult(0.0, 0.0, 0.0)
        
        val ema12 = calculateEMA(closes, 12)
        val ema26 = calculateEMA(closes, 26)
        val macd = ema12 - ema26
        
        // Simplified signal line
        val signal = macd * 0.9  // Approximation
        val histogram = macd - signal
        
        return MACDResult(macd, signal, histogram)
    }
    
    private fun calculateEMA(data: DoubleArray, period: Int): Double {
        if (data.isEmpty()) return 0.0
        val multiplier = 2.0 / (period + 1)
        var ema = data.take(period).average()
        
        for (i in period until data.size) {
            ema = (data[i] - ema) * multiplier + ema
        }
        return ema
    }
    
    private fun calculateATR(highs: DoubleArray, lows: DoubleArray, closes: DoubleArray, period: Int): Double {
        if (highs.size < period + 1) return 0.0
        
        val trueRanges = mutableListOf<Double>()
        for (i in 1 until highs.size) {
            val tr = maxOf(
                highs[i] - lows[i],
                abs(highs[i] - closes[i - 1]),
                abs(lows[i] - closes[i - 1])
            )
            trueRanges.add(tr)
        }
        
        return trueRanges.takeLast(period).average()
    }
    
    private fun calculateVWAP(highs: DoubleArray, lows: DoubleArray, closes: DoubleArray, volumes: DoubleArray): Double {
        if (closes.isEmpty()) return 0.0
        
        var cumulativeTPV = 0.0
        var cumulativeVolume = 0.0
        
        for (i in closes.indices) {
            val typicalPrice = (highs[i] + lows[i] + closes[i]) / 3
            cumulativeTPV += typicalPrice * volumes[i]
            cumulativeVolume += volumes[i]
        }
        
        return if (cumulativeVolume > 0) cumulativeTPV / cumulativeVolume else closes.last()
    }
    
    private fun determineHigherTfTrend(closes: DoubleArray): Int {
        if (closes.size < 50) return 0
        
        val sma20 = closes.takeLast(20).average()
        val sma50 = closes.takeLast(50).average()
        val current = closes.last()
        
        return when {
            current > sma20 && sma20 > sma50 -> 1   // Uptrend
            current < sma20 && sma20 < sma50 -> -1  // Downtrend
            else -> 0  // Neutral
        }
    }
    
    private fun calculateMomentumScore(rsi: Double, macdHist: Double, volumeRatio: Double, trend: Int): Int {
        var score = 50
        
        // RSI contribution (oversold/overbought = higher score for reversal)
        score += when {
            rsi < 30 || rsi > 70 -> 15
            rsi < 40 || rsi > 60 -> 5
            else -> 0
        }
        
        // MACD contribution
        score += when {
            abs(macdHist) > 0.5 -> 10
            abs(macdHist) > 0.2 -> 5
            else -> 0
        }
        
        // Volume contribution
        score += when {
            volumeRatio > 2.0 -> 15
            volumeRatio > 1.5 -> 10
            volumeRatio > 1.2 -> 5
            else -> 0
        }
        
        // Trend alignment
        score += if (trend != 0) 10 else 0
        
        return score.coerceIn(0, 100)
    }
    
    private fun countConfirmingIndicators(rsi: Double, macd: MACDResult, volumeRatio: Double, trend: Int): Int {
        var count = 0
        if (rsi < 30 || rsi > 70) count++
        if (abs(macd.histogram) > 0.1) count++
        if (volumeRatio > 1.2) count++
        if (trend != 0) count++
        return count
    }
    
    // ========================================================================
    // RISK MANAGEMENT
    // ========================================================================
    
    private fun canGenerateSignal(): Boolean {
        // Check cooldown
        if (System.currentTimeMillis() < cooldownUntil) return false
        
        // Check max concurrent
        if (activeScalps.size >= config.maxConcurrentScalps) return false
        
        // Reset hourly counter if needed
        if (System.currentTimeMillis() - hourStartTime > HOUR_MS) {
            tradesThisHour = 0
            hourStartTime = System.currentTimeMillis()
        }
        
        // Check hourly limit
        if (tradesThisHour >= config.maxScalpsPerHour) return false
        
        // Check daily loss limit
        if (dailyPnl < -config.maxDailyLossPercent) return false
        
        return true
    }
    
    private fun validateRiskLimits(): String? {
        if (activeScalps.size >= config.maxConcurrentScalps) {
            return "Maximum concurrent scalps reached"
        }
        if (tradesThisHour >= config.maxScalpsPerHour) {
            return "Hourly trade limit reached"
        }
        if (dailyPnl < -config.maxDailyLossPercent) {
            return "Daily loss limit reached"
        }
        if (System.currentTimeMillis() < cooldownUntil) {
            return "In cooldown period"
        }
        return null
    }
    
    // ========================================================================
    // BACKGROUND TASKS
    // ========================================================================
    
    private suspend fun monitorPositions() {
        while (isRunning.get()) {
            delay(1000)  // Check every second
            
            // Reset daily stats if new day
            if (System.currentTimeMillis() - dayStartTime > DAY_MS) {
                resetDailyStats()
            }
        }
    }
    
    private suspend fun cleanupExpiredSignals() {
        while (isRunning.get()) {
            delay(5000)  // Check every 5 seconds
            
            val expired = pendingSignals.values.filter { it.isExpired() }
            expired.forEach { signal ->
                pendingSignals.remove(signal.id)
                val expiredSignal = signal.copy(status = ScalpSignalStatus.EXPIRED)
                emitEvent(ScalpingEvent.SignalExpired(expiredSignal))
            }
        }
    }
    
    // ========================================================================
    // HELPERS
    // ========================================================================
    
    private fun emitEvent(event: ScalpingEvent) {
        scope.launch {
            _events.emit(event)
        }
    }
    
    private fun updateStats(updater: (ScalpingStats) -> ScalpingStats) {
        _stats.update(updater)
    }
    
    private fun updateStatsAfterTrade(result: ScalpResult) {
        updateStats { stats ->
            val newTotal = stats.totalScalps + 1
            val newWins = if (result.isWin) stats.winningScalps + 1 else stats.winningScalps
            val newLosses = if (!result.isWin) stats.losingScalps + 1 else stats.losingScalps
            
            val newStreak = if (result.isWin) {
                if (stats.currentStreak >= 0) stats.currentStreak + 1 else 1
            } else {
                if (stats.currentStreak <= 0) stats.currentStreak - 1 else -1
            }
            
            stats.copy(
                totalScalps = newTotal,
                winningScalps = newWins,
                losingScalps = newLosses,
                totalProfitPercent = stats.totalProfitPercent + result.pnlPercent,
                averageHoldTimeSeconds = ((stats.averageHoldTimeSeconds * stats.totalScalps) + result.holdTimeSeconds) / newTotal,
                averageProfitPercent = if (newWins > 0) (stats.averageProfitPercent * stats.winningScalps + if (result.isWin) result.pnlPercent else 0.0) / newWins else 0.0,
                averageLossPercent = if (newLosses > 0) (stats.averageLossPercent * stats.losingScalps + if (!result.isWin) abs(result.pnlPercent) else 0.0) / newLosses else 0.0,
                maxConsecutiveWins = if (newStreak > stats.maxConsecutiveWins) newStreak else stats.maxConsecutiveWins,
                maxConsecutiveLosses = if (-newStreak > stats.maxConsecutiveLosses) -newStreak else stats.maxConsecutiveLosses,
                currentStreak = newStreak,
                dailyPnlPercent = dailyPnl
            )
        }
    }
    
    private fun resetDailyStats() {
        dailyPnl = 0.0
        dayStartTime = System.currentTimeMillis()
        tradesThisHour = 0
        hourStartTime = System.currentTimeMillis()
        updateStats { it.copy(scalpsToday = 0, dailyPnlPercent = 0.0) }
    }
    
    // ========================================================================
    // PUBLIC ACCESSORS
    // ========================================================================
    
    fun getPendingSignals(): List<ScalpingSignal> = pendingSignals.values.toList()
    fun getActiveScalps(): List<ActiveScalp> = activeScalps.values.toList()
    fun getConfig(): ScalpingConfig = config
    
    /**
     * Update engine configuration at runtime.
     * Note: Some settings only take effect on new signals/positions.
     */
    fun updateConfig(newConfig: ScalpingConfig) {
        android.util.Log.i(TAG, "Updating scalping config: mode=${newConfig.mode}")
        this.config = newConfig
    }
}
