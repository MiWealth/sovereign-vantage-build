package com.miwealth.sovereignvantage.core.indicators

import com.miwealth.sovereignvantage.core.trading.AssetType
import com.miwealth.sovereignvantage.core.trading.MarketContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import java.util.TimeZone

/**
 * Real-Time Indicator Service
 * 
 * Manages live indicator calculations across multiple symbols and timeframes.
 * Integrates with exchange WebSocket feeds to provide continuous MarketContext updates.
 * 
 * Features:
 * - Multi-symbol indicator tracking
 * - Automatic candle aggregation from tick data
 * - Thread-safe updates via coroutines
 * - StateFlow emission for UI/trading engine consumption
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage™: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to: Cathryn 💘
 */

// =============================================================================
// SYMBOL INDICATOR STATE
// =============================================================================

/**
 * Complete indicator state for a single symbol.
 */
data class SymbolIndicatorState(
    val symbol: String,
    val snapshot: IndicatorSnapshot,
    val context: MarketContext,
    val lastUpdate: Long,
    val isReady: Boolean,
    val dataPoints: Int
)

// =============================================================================
// REAL-TIME INDICATOR SERVICE
// =============================================================================

/**
 * Service for managing real-time indicator calculations.
 * 
 * Usage:
 * ```kotlin
 * val service = RealTimeIndicatorService()
 * 
 * // Subscribe to context updates for a symbol
 * service.getContextFlow("BTC/USD").collect { state ->
 *     val recommendation = selector.recommendPreset(state.context)
 * }
 * 
 * // Feed tick data from WebSocket
 * service.onTick("BTC/USD", price = 65000.0, volume = 1.5, timestamp = now)
 * 
 * // Or feed complete bars from exchange
 * service.onBar("BTC/USD", bar)
 * ```
 */
class RealTimeIndicatorService {
    
    // Calculator instances per symbol
    private val calculators = mutableMapOf<String, IndicatorCalculator>()
    
    // Current candle being built from ticks
    private val currentCandles = mutableMapOf<String, CandleBuilder>()
    
    // State flows per symbol
    private val stateFlows = mutableMapOf<String, MutableStateFlow<SymbolIndicatorState?>>()
    
    // Position stair tracking (bars at top stair)
    private val barsAtTopStair = mutableMapOf<String, Int>()
    
    // Thread safety
    private val mutex = Mutex()
    
    // Configuration
    private var candleIntervalMs: Long = 60_000  // 1 minute default
    
    // ==========================================================================
    // CONFIGURATION
    // ==========================================================================
    
    /**
     * Set candle interval for tick aggregation.
     */
    fun setCandleInterval(intervalMs: Long) {
        candleIntervalMs = intervalMs
    }
    
    /**
     * Set bars at top stair for expansion tracking.
     */
    suspend fun setBarsAtTopStair(symbol: String, bars: Int) {
        mutex.withLock {
            barsAtTopStair[symbol] = bars
        }
    }
    
    /**
     * Increment bars at top stair (call on each new bar while at top).
     */
    suspend fun incrementBarsAtTopStair(symbol: String) {
        mutex.withLock {
            barsAtTopStair[symbol] = (barsAtTopStair[symbol] ?: 0) + 1
        }
    }
    
    /**
     * Reset bars at top stair (call when position moves to new stair).
     */
    suspend fun resetBarsAtTopStair(symbol: String) {
        mutex.withLock {
            barsAtTopStair[symbol] = 0
        }
    }
    
    // ==========================================================================
    // DATA INPUT
    // ==========================================================================
    
    /**
     * Process a price tick from WebSocket feed.
     * Automatically aggregates into candles.
     */
    suspend fun onTick(
        symbol: String,
        price: Double,
        volume: Double,
        timestamp: Long = System.currentTimeMillis()
    ) {
        mutex.withLock {
            val builder = currentCandles.getOrPut(symbol) {
                CandleBuilder(symbol, candleIntervalMs)
            }
            
            val completedCandle = builder.addTick(price, volume, timestamp)
            
            if (completedCandle != null) {
                processBar(symbol, completedCandle)
            }
        }
    }
    
    /**
     * Process a complete OHLCV bar from exchange.
     */
    suspend fun onBar(symbol: String, bar: OHLCV) {
        mutex.withLock {
            processBar(symbol, bar)
        }
    }
    
    /**
     * Load historical data for a symbol.
     */
    suspend fun loadHistory(symbol: String, bars: List<OHLCV>) {
        mutex.withLock {
            val calculator = calculators.getOrPut(symbol) { IndicatorCalculator() }
            calculator.addBars(bars)
            emitState(symbol)
        }
    }
    
    /**
     * Internal bar processing.
     */
    private fun processBar(symbol: String, bar: OHLCV) {
        val calculator = calculators.getOrPut(symbol) { IndicatorCalculator() }
        calculator.addBar(bar)
        emitState(symbol)
    }
    
    // ==========================================================================
    // STATE ACCESS
    // ==========================================================================
    
    /**
     * Get StateFlow for a symbol's indicator state.
     */
    fun getContextFlow(symbol: String): StateFlow<SymbolIndicatorState?> {
        return stateFlows.getOrPut(symbol) {
            MutableStateFlow(null)
        }.asStateFlow()
    }
    
    /**
     * Get current MarketContext for a symbol (one-shot).
     */
    suspend fun getContext(symbol: String): MarketContext? {
        mutex.withLock {
            val calculator = calculators[symbol] ?: return null
            if (!calculator.hasEnoughData(50)) return null
            
            val snapshot = calculator.generateSnapshot(barsAtTopStair[symbol] ?: 0)
            return MarketContext.build(
                indicators = snapshot,
                hourUTC = getCurrentHourUTC(),
                assetType = inferAssetType(symbol)
            )
        }
    }
    
    /**
     * Get current IndicatorSnapshot for a symbol.
     */
    suspend fun getSnapshot(symbol: String): IndicatorSnapshot? {
        mutex.withLock {
            val calculator = calculators[symbol] ?: return null
            if (!calculator.hasEnoughData(50)) return null
            return calculator.generateSnapshot(barsAtTopStair[symbol] ?: 0)
        }
    }
    
    /**
     * Check if a symbol has enough data for reliable indicators.
     */
    suspend fun isReady(symbol: String): Boolean {
        mutex.withLock {
            return calculators[symbol]?.hasEnoughData(50) == true
        }
    }
    
    /**
     * Get all tracked symbols.
     */
    fun getTrackedSymbols(): Set<String> = calculators.keys.toSet()
    
    // ==========================================================================
    // INTERNAL HELPERS
    // ==========================================================================
    
    private fun emitState(symbol: String) {
        val calculator = calculators[symbol] ?: return
        val flow = stateFlows.getOrPut(symbol) { MutableStateFlow(null) }
        
        val isReady = calculator.hasEnoughData(50)
        
        if (isReady) {
            val snapshot = calculator.generateSnapshot(barsAtTopStair[symbol] ?: 0)
            val context = MarketContext.build(
                indicators = snapshot,
                hourUTC = getCurrentHourUTC(),
                assetType = inferAssetType(symbol)
            )
            
            flow.value = SymbolIndicatorState(
                symbol = symbol,
                snapshot = snapshot,
                context = context,
                lastUpdate = System.currentTimeMillis(),
                isReady = true,
                dataPoints = 50  // Minimum ready threshold
            )
        } else {
            // Emit partial state
            flow.value = SymbolIndicatorState(
                symbol = symbol,
                snapshot = calculator.generateSnapshot(0),
                context = MarketContext.build(
                    indicators = calculator.generateSnapshot(0),
                    hourUTC = getCurrentHourUTC(),
                    assetType = inferAssetType(symbol)
                ),
                lastUpdate = System.currentTimeMillis(),
                isReady = false,
                dataPoints = 0  // TODO: track actual count
            )
        }
    }
    
    private fun getCurrentHourUTC(): Int {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        return calendar.get(Calendar.HOUR_OF_DAY)
    }
    
    private fun inferAssetType(symbol: String): AssetType {
        return when {
            symbol.contains("USD") && !symbol.contains("/") -> AssetType.FOREX
            symbol.contains("EUR") && !symbol.contains("/") -> AssetType.FOREX
            symbol.contains("GBP") && !symbol.contains("/") -> AssetType.FOREX
            symbol.contains("JPY") && !symbol.contains("/") -> AssetType.FOREX
            symbol.endsWith("USD") || symbol.endsWith("USDT") || symbol.endsWith("USDC") -> AssetType.CRYPTO
            symbol.contains("BTC") || symbol.contains("ETH") -> AssetType.CRYPTO
            else -> AssetType.CRYPTO  // Default to crypto
        }
    }
}

// =============================================================================
// CANDLE BUILDER - Aggregates ticks into OHLCV
// =============================================================================

/**
 * Builds OHLCV candles from tick data.
 */
class CandleBuilder(
    private val symbol: String,
    private val intervalMs: Long
) {
    private var currentOpen: Double? = null
    private var currentHigh: Double = Double.MIN_VALUE
    private var currentLow: Double = Double.MAX_VALUE
    private var currentClose: Double = 0.0
    private var currentVolume: Double = 0.0
    private var candleStartTime: Long = 0
    
    /**
     * Add a tick. Returns completed candle if interval elapsed.
     */
    fun addTick(price: Double, volume: Double, timestamp: Long): OHLCV? {
        // Start new candle if needed
        if (currentOpen == null || timestamp >= candleStartTime + intervalMs) {
            val completed = if (currentOpen != null) {
                OHLCV(
                    timestamp = candleStartTime,
                    open = currentOpen!!,
                    high = currentHigh,
                    low = currentLow,
                    close = currentClose,
                    volume = currentVolume
                )
            } else null
            
            // Reset for new candle
            currentOpen = price
            currentHigh = price
            currentLow = price
            currentClose = price
            currentVolume = volume
            candleStartTime = (timestamp / intervalMs) * intervalMs
            
            return completed
        }
        
        // Update current candle
        currentHigh = maxOf(currentHigh, price)
        currentLow = minOf(currentLow, price)
        currentClose = price
        currentVolume += volume
        
        return null
    }
    
    /**
     * Force close current candle (for end of session, etc.).
     */
    fun forceClose(timestamp: Long): OHLCV? {
        if (currentOpen == null) return null
        
        val candle = OHLCV(
            timestamp = candleStartTime,
            open = currentOpen!!,
            high = currentHigh,
            low = currentLow,
            close = currentClose,
            volume = currentVolume
        )
        
        currentOpen = null
        currentHigh = Double.MIN_VALUE
        currentLow = Double.MAX_VALUE
        currentVolume = 0.0
        
        return candle
    }
}

// =============================================================================
// INDICATOR SERVICE FACTORY
// =============================================================================

/**
 * Factory for creating configured indicator services.
 */
object IndicatorServiceFactory {
    
    /**
     * Create service configured for crypto trading.
     */
    fun createForCrypto(): RealTimeIndicatorService {
        return RealTimeIndicatorService().apply {
            setCandleInterval(60_000)  // 1 minute candles
        }
    }
    
    /**
     * Create service configured for scalping.
     */
    fun createForScalping(): RealTimeIndicatorService {
        return RealTimeIndicatorService().apply {
            setCandleInterval(15_000)  // 15 second candles
        }
    }
    
    /**
     * Create service configured for swing trading.
     */
    fun createForSwing(): RealTimeIndicatorService {
        return RealTimeIndicatorService().apply {
            setCandleInterval(3_600_000)  // 1 hour candles
        }
    }
}

// =============================================================================
// INTEGRATION EXAMPLE
// =============================================================================

/**
 * Example showing full integration with AI Board.
 * 
 * ```kotlin
 * // Setup
 * val indicatorService = IndicatorServiceFactory.createForCrypto()
 * val (selector, expander) = AIBoardStahlFactory.create()
 * 
 * // Load historical data
 * val history = exchange.getOHLCV("BTC/USD", "1m", limit = 200)
 * indicatorService.loadHistory("BTC/USD", history)
 * 
 * // Subscribe to WebSocket
 * exchange.subscribeTrades("BTC/USD") { trade ->
 *     indicatorService.onTick("BTC/USD", trade.price, trade.volume, trade.timestamp)
 * }
 * 
 * // React to context changes
 * indicatorService.getContextFlow("BTC/USD").collect { state ->
 *     if (state?.isReady == true) {
 *         // Entry decision
 *         val recommendation = selector.recommendPreset(state.context)
 *         println("AI recommends: ${recommendation.preset}")
 *         
 *         // Or expansion decision for existing position
 *         val expansionResult = expander.evaluateExpansion(position, state.context)
 *         if (expansionResult.decision != ExpansionDecision.HOLD) {
 *             expandedPosition.applyExpansion(expansionResult)
 *         }
 *     }
 * }
 * ```
 */
