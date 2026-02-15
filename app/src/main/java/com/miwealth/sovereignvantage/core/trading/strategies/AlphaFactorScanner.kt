package com.miwealth.sovereignvantage.core.trading.strategies

import com.miwealth.sovereignvantage.core.*
import com.miwealth.sovereignvantage.core.exchange.*
import com.miwealth.sovereignvantage.core.indicators.*
import com.miwealth.sovereignvantage.core.trading.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.*

/**
 * ALPHA FACTOR SCANNER
 * 
 * Multi-factor quantitative asset selection engine.
 * 
 * This is a "money printer" that scans a universe of assets and ranks them
 * by statistical factors proven to generate alpha:
 * 
 * - MOMENTUM: RSI, Rate of Change, Trend Strength
 * - QUALITY: Volume, Liquidity, Spread Quality  
 * - TREND: Price vs SMA200 (hard filter - NO trades below)
 * - VOLATILITY: ATR-based position sizing adjustment
 * 
 * STATE MACHINE DESIGN:
 * The software doesn't "hope" - it executes or idles.
 * No human emotion. Pure mathematical selection.
 * 
 * HARD KILL SWITCH: 5% strategy drawdown → 100% USDT
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

// =============================================================================
// DATA MODELS
// =============================================================================

/**
 * Market data snapshot for factor analysis
 */
data class FactorMarketData(
    val symbol: String,
    val price: Double,
    val rsi: Double,                    // Momentum factor (0-100)
    val roc: Double,                    // Rate of change (%)
    val volume24h: Double,              // 24h volume in quote currency
    val avgVolume7d: Double,            // 7-day average volume
    val sma200: Double,                 // 200-period SMA (trend filter)
    val sma50: Double,                  // 50-period SMA
    val ema21: Double,                  // 21-period EMA
    val atr: Double,                    // Average True Range (volatility)
    val spreadPercent: Double,          // Bid-ask spread %
    val marketCap: Double?,             // Market cap if available
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Factor scores for an asset
 */
data class FactorScore(
    val symbol: String,
    val momentumScore: Double,          // 0-1 scale
    val qualityScore: Double,           // 0-1 scale
    val volatilityScore: Double,        // 0-1 scale (lower = better for stability)
    val trendScore: Double,             // 0-1 scale
    val compositeScore: Double,         // Weighted combination
    val rank: Int = 0,
    val passesTrendFilter: Boolean,     // Hard filter: price > SMA200
    val disqualificationReason: String? = null
)

/**
 * Scanner configuration
 */
data class AlphaScannerConfig(
    // Factor weights (must sum to 1.0)
    val momentumWeight: Double = 0.35,
    val qualityWeight: Double = 0.25,
    val volatilityWeight: Double = 0.15,
    val trendWeight: Double = 0.25,
    
    // Filters
    val requireAboveSMA200: Boolean = true,     // HARD FILTER - no exceptions
    val requireAboveSMA50: Boolean = false,
    val minVolume24h: Double = 100_000.0,       // Minimum $100K daily volume
    val maxSpreadPercent: Double = 0.5,         // Maximum 0.5% spread
    val minRSI: Double = 30.0,                  // Oversold filter
    val maxRSI: Double = 80.0,                  // Overbought filter
    
    // Risk management
    val hardKillSwitchDrawdown: Double = 5.0,   // 5% drawdown = liquidate to USDT
    val maxPositionsFromScanner: Int = 10,
    val rebalanceIntervalMinutes: Int = 60,
    
    // Scoring parameters
    val rsiOptimal: Double = 60.0,              // RSI sweet spot
    val volumeMultiplierThreshold: Double = 1.5 // Volume vs average for bonus
)

/**
 * Scanner state machine
 */
enum class ScannerState {
    IDLE,               // Waiting for scan trigger
    SCANNING,           // Actively analyzing universe
    SIGNAL_GENERATED,   // Found opportunities, awaiting execution
    EXECUTING,          // Placing orders
    MONITORING,         // Positions active, monitoring
    LIQUIDATING,        // Kill switch triggered, exiting all
    HALTED              // Manual restart required
}

// =============================================================================
// ALPHA FACTOR SCANNER ENGINE
// =============================================================================

class AlphaFactorScanner(
    private val exchangeAggregator: ExchangeAggregator,
    private val riskManager: RiskManager,
    private val config: AlphaScannerConfig = AlphaScannerConfig(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    
    // State
    private val _state = MutableStateFlow(ScannerState.IDLE)
    val state: StateFlow<ScannerState> = _state.asStateFlow()
    
    private val _topAssets = MutableStateFlow<List<FactorScore>>(emptyList())
    val topAssets: StateFlow<List<FactorScore>> = _topAssets.asStateFlow()
    
    private val _events = MutableSharedFlow<ScannerEvent>(replay = 0, extraBufferCapacity = 64)
    val events: SharedFlow<ScannerEvent> = _events.asSharedFlow()
    
    // Performance tracking
    private var strategyHighWaterMark: Double = 0.0
    private var strategyStartValue: Double = 0.0
    private var isKillSwitchActive: Boolean = false
    
    // Universe of assets to scan
    private val assetUniverse = mutableListOf<String>()
    
    // Indicator calculator (inject or create)
    private val indicatorEngine = IndicatorEngine()
    
    /**
     * Initialize scanner with asset universe
     */
    fun initialize(symbols: List<String>, startingValue: Double) {
        assetUniverse.clear()
        assetUniverse.addAll(symbols)
        strategyStartValue = startingValue
        strategyHighWaterMark = startingValue
        isKillSwitchActive = false
        _state.value = ScannerState.IDLE
        
        scope.launch {
            _events.emit(ScannerEvent.Initialized(symbols.size, startingValue))
        }
    }
    
    /**
     * Run a full universe scan and return ranked assets
     * 
     * THIS IS THE MONEY PRINTER - Pure mathematics, no emotion
     */
    suspend fun scanUniverse(): List<FactorScore> {
        if (isKillSwitchActive) {
            _events.emit(ScannerEvent.ScanRejected("Kill switch active - manual restart required"))
            return emptyList()
        }
        
        _state.value = ScannerState.SCANNING
        _events.emit(ScannerEvent.ScanStarted(assetUniverse.size))
        
        val scores = mutableListOf<FactorScore>()
        
        // Scan each asset in parallel for speed
        val deferredScores = assetUniverse.map { symbol ->
            scope.async {
                try {
                    val marketData = fetchMarketData(symbol)
                    marketData?.let { calculateFactorScore(it) }
                } catch (e: Exception) {
                    null
                }
            }
        }
        
        // Collect results
        deferredScores.awaitAll().filterNotNull().forEach { score ->
            scores.add(score)
        }
        
        // Filter: Only assets that pass the hard trend filter
        val qualifiedScores = scores
            .filter { it.passesTrendFilter }
            .filter { it.disqualificationReason == null }
            .sortedByDescending { it.compositeScore }
            .mapIndexed { index, score -> score.copy(rank = index + 1) }
        
        // Take top N
        val topN = qualifiedScores.take(config.maxPositionsFromScanner)
        
        _topAssets.value = topN
        _state.value = if (topN.isNotEmpty()) ScannerState.SIGNAL_GENERATED else ScannerState.IDLE
        
        _events.emit(ScannerEvent.ScanComplete(
            totalScanned = assetUniverse.size,
            qualified = qualifiedScores.size,
            selected = topN.size
        ))
        
        return topN
    }
    
    /**
     * Get top performers without running a full scan (use cached results)
     */
    fun getTopPerformers(topN: Int = 10): List<String> {
        return _topAssets.value
            .take(topN)
            .map { it.symbol }
    }
    
    /**
     * Calculate factor score for a single asset
     */
    private fun calculateFactorScore(data: FactorMarketData): FactorScore {
        // =================================================================
        // RULE 1: HARD TREND FILTER - ABSOLUTE NO-GO IF BELOW SMA200
        // =================================================================
        val passesTrendFilter = if (config.requireAboveSMA200) {
            data.price > data.sma200
        } else true
        
        // Check disqualification reasons
        val disqualificationReason = when {
            !passesTrendFilter -> "Price below SMA200 - trend filter rejection"
            data.volume24h < config.minVolume24h -> "Insufficient volume: ${data.volume24h}"
            data.spreadPercent > config.maxSpreadPercent -> "Spread too wide: ${data.spreadPercent}%"
            data.rsi < config.minRSI -> "RSI oversold: ${data.rsi}"
            data.rsi > config.maxRSI -> "RSI overbought: ${data.rsi}"
            config.requireAboveSMA50 && data.price < data.sma50 -> "Price below SMA50"
            else -> null
        }
        
        // =================================================================
        // FACTOR 1: MOMENTUM SCORE
        // =================================================================
        // RSI near optimal (not overbought, not oversold)
        // ROC positive = momentum building
        val rsiNormalized = when {
            data.rsi < 30 -> 0.0  // Oversold = low momentum score
            data.rsi > 70 -> 0.3  // Overbought = reduced momentum
            data.rsi in 50.0..65.0 -> 1.0  // Sweet spot
            data.rsi in 40.0..50.0 -> 0.7
            data.rsi in 65.0..70.0 -> 0.8
            else -> 0.5
        }
        
        // Rate of Change bonus
        val rocBonus = when {
            data.roc > 10 -> 0.3
            data.roc > 5 -> 0.2
            data.roc > 2 -> 0.1
            data.roc > 0 -> 0.05
            else -> 0.0
        }
        
        val momentumScore = min(1.0, (rsiNormalized * 0.7) + (rocBonus * 0.3))
        
        // =================================================================
        // FACTOR 2: QUALITY SCORE (Liquidity + Spread)
        // =================================================================
        val volumeRatio = if (data.avgVolume7d > 0) data.volume24h / data.avgVolume7d else 1.0
        val volumeScore = when {
            volumeRatio > config.volumeMultiplierThreshold -> 1.0
            volumeRatio > 1.0 -> 0.8
            volumeRatio > 0.5 -> 0.5
            else -> 0.2
        }
        
        val spreadScore = when {
            data.spreadPercent < 0.1 -> 1.0
            data.spreadPercent < 0.2 -> 0.8
            data.spreadPercent < 0.3 -> 0.6
            data.spreadPercent < 0.5 -> 0.4
            else -> 0.1
        }
        
        val qualityScore = (volumeScore * 0.6) + (spreadScore * 0.4)
        
        // =================================================================
        // FACTOR 3: VOLATILITY SCORE (Lower = More Stable)
        // =================================================================
        val atrPercent = if (data.price > 0) (data.atr / data.price) * 100 else 0.0
        val volatilityScore = when {
            atrPercent < 2.0 -> 1.0   // Very stable
            atrPercent < 4.0 -> 0.8
            atrPercent < 6.0 -> 0.6
            atrPercent < 10.0 -> 0.4
            else -> 0.2               // Too volatile
        }
        
        // =================================================================
        // FACTOR 4: TREND SCORE
        // =================================================================
        // Multiple trend confirmations
        val aboveSma200 = if (data.price > data.sma200) 0.4 else 0.0
        val aboveSma50 = if (data.price > data.sma50) 0.3 else 0.0
        val aboveEma21 = if (data.price > data.ema21) 0.2 else 0.0
        val sma50AboveSma200 = if (data.sma50 > data.sma200) 0.1 else 0.0  // Golden cross
        
        val trendScore = aboveSma200 + aboveSma50 + aboveEma21 + sma50AboveSma200
        
        // =================================================================
        // COMPOSITE SCORE - Weighted Combination
        // =================================================================
        val compositeScore = 
            (momentumScore * config.momentumWeight) +
            (qualityScore * config.qualityWeight) +
            (volatilityScore * config.volatilityWeight) +
            (trendScore * config.trendWeight)
        
        return FactorScore(
            symbol = data.symbol,
            momentumScore = momentumScore,
            qualityScore = qualityScore,
            volatilityScore = volatilityScore,
            trendScore = trendScore,
            compositeScore = compositeScore,
            passesTrendFilter = passesTrendFilter,
            disqualificationReason = disqualificationReason
        )
    }
    
    /**
     * Fetch market data for a symbol from aggregated exchanges
     */
    private suspend fun fetchMarketData(symbol: String): FactorMarketData? {
        return try {
            val aggregatedPrice = exchangeAggregator.getBestPrice(symbol) ?: return null
            
            // Get from primary exchange with best liquidity
            val primaryExchange = exchangeAggregator.getConnectedExchanges()
                .firstOrNull { it.config.exchangeId == aggregatedPrice.bestBidExchange }
                ?: return null
            
            // Fetch candles for indicator calculation
            val candles = primaryExchange.getCandles(symbol, "1d", 250) // 250 days for SMA200
            if (candles.isEmpty()) return null
            
            // Calculate indicators
            val closes = candles.map { it.close }
            val highs = candles.map { it.high }
            val lows = candles.map { it.low }
            val volumes = candles.map { it.volume }
            
            val currentPrice = aggregatedPrice.midPrice
            val rsi = indicatorEngine.calculateRSI(closes, 14).lastOrNull() ?: 50.0
            val roc = indicatorEngine.calculateROC(closes, 12).lastOrNull() ?: 0.0
            val sma200 = indicatorEngine.calculateSMA(closes, 200).lastOrNull() ?: currentPrice
            val sma50 = indicatorEngine.calculateSMA(closes, 50).lastOrNull() ?: currentPrice
            val ema21 = indicatorEngine.calculateEMA(closes, 21).lastOrNull() ?: currentPrice
            val atr = indicatorEngine.calculateATR(highs, lows, closes, 14).lastOrNull() ?: 0.0
            
            val volume24h = volumes.lastOrNull()?.times(currentPrice) ?: 0.0
            val avgVolume7d = if (volumes.size >= 7) {
                volumes.takeLast(7).average() * currentPrice
            } else volume24h
            
            FactorMarketData(
                symbol = symbol,
                price = currentPrice,
                rsi = rsi,
                roc = roc,
                volume24h = volume24h,
                avgVolume7d = avgVolume7d,
                sma200 = sma200,
                sma50 = sma50,
                ema21 = ema21,
                atr = atr,
                spreadPercent = aggregatedPrice.bestSpread / currentPrice * 100
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Update strategy value and check kill switch
     */
    suspend fun updateStrategyValue(currentValue: Double) {
        // Update high water mark
        if (currentValue > strategyHighWaterMark) {
            strategyHighWaterMark = currentValue
        }
        
        // Calculate drawdown from high water mark
        val drawdownPercent = if (strategyHighWaterMark > 0) {
            ((strategyHighWaterMark - currentValue) / strategyHighWaterMark) * 100
        } else 0.0
        
        // =================================================================
        // HARD KILL SWITCH: 5% DRAWDOWN = LIQUIDATE TO USDT
        // =================================================================
        if (drawdownPercent >= config.hardKillSwitchDrawdown && !isKillSwitchActive) {
            activateKillSwitch(drawdownPercent)
        }
    }
    
    /**
     * HARD KILL SWITCH - No negotiation, no hope
     * Liquidate EVERYTHING to USDT/USDC
     */
    private suspend fun activateKillSwitch(drawdownPercent: Double) {
        isKillSwitchActive = true
        _state.value = ScannerState.LIQUIDATING
        
        _events.emit(ScannerEvent.KillSwitchActivated(
            drawdownPercent = drawdownPercent,
            message = "HARD KILL SWITCH: ${String.format("%.2f", drawdownPercent)}% drawdown exceeded ${config.hardKillSwitchDrawdown}% limit. LIQUIDATING ALL POSITIONS TO STABLECOIN."
        ))
        
        // Signal to trading system to liquidate all positions
        // The actual liquidation is handled by TradingCoordinator
        riskManager.activateKillSwitch("Alpha Scanner: ${drawdownPercent}% drawdown")
        
        _state.value = ScannerState.HALTED
        
        _events.emit(ScannerEvent.HaltedForManualRestart(
            "Strategy halted after kill switch. Manual restart required via Settings."
        ))
    }
    
    /**
     * Manual restart after kill switch (requires user action)
     */
    fun manualRestart(newStartingValue: Double) {
        if (!isKillSwitchActive) return
        
        isKillSwitchActive = false
        strategyStartValue = newStartingValue
        strategyHighWaterMark = newStartingValue
        _state.value = ScannerState.IDLE
        
        scope.launch {
            _events.emit(ScannerEvent.ManualRestartComplete(newStartingValue))
        }
    }
    
    /**
     * Check if strategy is tradeable
     */
    fun canTrade(): Boolean {
        return !isKillSwitchActive && _state.value != ScannerState.HALTED
    }
    
    /**
     * Shutdown scanner
     */
    fun shutdown() {
        scope.cancel()
    }
}

// =============================================================================
// INDICATOR ENGINE (Simplified - integrates with existing IndicatorCalculator)
// =============================================================================

/**
 * Lightweight indicator calculations for scanner
 * In production, this would delegate to the full IndicatorCalculator
 */
class IndicatorEngine {
    
    fun calculateSMA(prices: List<Double>, period: Int): List<Double> {
        if (prices.size < period) return emptyList()
        return prices.windowed(period) { it.average() }
    }
    
    fun calculateEMA(prices: List<Double>, period: Int): List<Double> {
        if (prices.isEmpty()) return emptyList()
        val multiplier = 2.0 / (period + 1)
        val result = mutableListOf<Double>()
        var ema = prices.take(period).average()
        result.add(ema)
        
        for (i in period until prices.size) {
            ema = (prices[i] - ema) * multiplier + ema
            result.add(ema)
        }
        return result
    }
    
    fun calculateRSI(prices: List<Double>, period: Int = 14): List<Double> {
        if (prices.size < period + 1) return emptyList()
        
        val changes = prices.zipWithNext { a, b -> b - a }
        val gains = changes.map { maxOf(it, 0.0) }
        val losses = changes.map { maxOf(-it, 0.0) }
        
        val result = mutableListOf<Double>()
        var avgGain = gains.take(period).average()
        var avgLoss = losses.take(period).average()
        
        for (i in period until changes.size) {
            avgGain = (avgGain * (period - 1) + gains[i]) / period
            avgLoss = (avgLoss * (period - 1) + losses[i]) / period
            
            val rs = if (avgLoss > 0) avgGain / avgLoss else 100.0
            val rsi = 100 - (100 / (1 + rs))
            result.add(rsi)
        }
        return result
    }
    
    fun calculateROC(prices: List<Double>, period: Int = 12): List<Double> {
        if (prices.size <= period) return emptyList()
        return prices.drop(period).mapIndexed { index, price ->
            val prevPrice = prices[index]
            if (prevPrice > 0) ((price - prevPrice) / prevPrice) * 100 else 0.0
        }
    }
    
    fun calculateATR(highs: List<Double>, lows: List<Double>, closes: List<Double>, period: Int = 14): List<Double> {
        if (highs.size < period + 1) return emptyList()
        
        val trueRanges = mutableListOf<Double>()
        for (i in 1 until highs.size) {
            val tr = maxOf(
                highs[i] - lows[i],
                abs(highs[i] - closes[i - 1]),
                abs(lows[i] - closes[i - 1])
            )
            trueRanges.add(tr)
        }
        
        // Simple moving average of TR
        return trueRanges.windowed(period) { it.average() }
    }
}

// =============================================================================
// EVENTS
// =============================================================================

sealed class ScannerEvent {
    data class Initialized(val universeSize: Int, val startingValue: Double) : ScannerEvent()
    data class ScanStarted(val assetCount: Int) : ScannerEvent()
    data class ScanComplete(val totalScanned: Int, val qualified: Int, val selected: Int) : ScannerEvent()
    data class ScanRejected(val reason: String) : ScannerEvent()
    data class KillSwitchActivated(val drawdownPercent: Double, val message: String) : ScannerEvent()
    data class HaltedForManualRestart(val reason: String) : ScannerEvent()
    data class ManualRestartComplete(val newStartingValue: Double) : ScannerEvent()
}
