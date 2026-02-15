package com.miwealth.sovereignvantage.core.trading

import android.content.Context
import com.miwealth.sovereignvantage.core.*
import com.miwealth.sovereignvantage.core.exchange.*
import com.miwealth.sovereignvantage.core.indicators.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * TRADING INDICATOR INTEGRATION
 * 
 * Bridges the exchange WebSocket feeds to the AI Board STAHL system:
 * 
 * Data Flow:
 * ```
 * KrakenConnector (WebSocket)
 *     → PriceTick events
 *     → RealTimeIndicatorService.onTick()
 *     → IndicatorCalculator updates
 *     → MarketContext generation
 *     → AIBoardStahlSelector (entry decisions)
 *     → AIBoardStairExpander (mid-trade decisions)
 *     → STAHL Stair Stop™ (profit protection)
 * ```
 * 
 * Features:
 * - Automatic historical data loading on startup
 * - Real-time tick processing from WebSocket
 * - Multi-symbol tracking
 * - Position-aware stair expansion monitoring
 * - Configurable candle intervals (1m for normal, 15s for scalping)
 * 
 * V5.5.51 - Initial integration
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage™: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to: Cathryn 💘
 */

class TradingIndicatorIntegration private constructor(
    private val context: Context,
    private val scope: CoroutineScope
) {
    
    companion object {
        @Volatile
        private var instance: TradingIndicatorIntegration? = null
        
        fun getInstance(context: Context): TradingIndicatorIntegration {
            return instance ?: synchronized(this) {
                instance ?: TradingIndicatorIntegration(
                    context.applicationContext,
                    CoroutineScope(Dispatchers.Default + SupervisorJob())
                ).also { instance = it }
            }
        }
        
        private const val HISTORY_BARS = 200  // Load 200 bars of history for reliable indicators
        private const val DEFAULT_TIMEFRAME = "1m"
    }
    
    // ==========================================================================
    // COMPONENTS
    // ==========================================================================
    
    // Indicator service (manages all symbol calculators)
    private var indicatorService: RealTimeIndicatorService? = null
    
    // AI Board components
    private var stahlSelector: AIBoardStahlSelector? = null
    private var stahlExpander: AIBoardStairExpander? = null
    
    // Active subscriptions
    private val subscribedSymbols = mutableSetOf<String>()
    private val subscriptionJobs = mutableMapOf<String, Job>()
    
    // Position tracking for expansion decisions
    private val activePositions = mutableMapOf<String, ExpandedStahlPosition>()
    
    // State flows
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()
    
    private val _recommendations = MutableSharedFlow<SymbolRecommendation>(
        replay = 1,
        extraBufferCapacity = 50
    )
    val recommendations: SharedFlow<SymbolRecommendation> = _recommendations.asSharedFlow()
    
    private val _expansionEvents = MutableSharedFlow<SymbolExpansionEvent>(
        replay = 1,
        extraBufferCapacity = 50
    )
    val expansionEvents: SharedFlow<SymbolExpansionEvent> = _expansionEvents.asSharedFlow()
    
    // Configuration
    private var userConstraints = UserTradeConstraints()
    
    // ==========================================================================
    // INITIALIZATION
    // ==========================================================================
    
    /**
     * Initialize the integration system.
     * 
     * @param mode Trading mode (SCALPING uses 15s candles, others use 1m)
     */
    suspend fun initialize(mode: TradingMode = TradingMode.AUTONOMOUS): Result<Unit> {
        return try {
            // Create indicator service based on mode
            indicatorService = when (mode) {
                TradingMode.SCALPING -> IndicatorServiceFactory.createForScalping()
                else -> IndicatorServiceFactory.createForCrypto()
            }
            
            // Create AI Board components
            val (selector, expander) = AIBoardStahlFactory.create()
            stahlSelector = selector
            stahlExpander = expander
            
            _isInitialized.value = true
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Update user constraints for AI recommendations.
     */
    fun setUserConstraints(constraints: UserTradeConstraints) {
        userConstraints = constraints
    }
    
    // ==========================================================================
    // EXCHANGE INTEGRATION
    // ==========================================================================
    
    /**
     * Subscribe to an exchange connector's price feed for a symbol.
     * Automatically loads historical data and starts real-time processing.
     * 
     * @param connector The exchange connector (e.g., KrakenConnector)
     * @param symbol Trading symbol (e.g., "BTC/USD")
     */
    suspend fun subscribeToExchange(
        connector: UnifiedExchangeConnector,
        symbol: String
    ): Result<Unit> {
        if (!_isInitialized.value) {
            return Result.failure(IllegalStateException("Integration not initialized"))
        }
        
        if (symbol in subscribedSymbols) {
            return Result.success(Unit)  // Already subscribed
        }
        
        return try {
            val service = indicatorService ?: return Result.failure(
                IllegalStateException("Indicator service not available")
            )
            
            // Step 1: Load historical data
            loadHistoricalData(connector, symbol, service)
            
            // Step 2: Subscribe to real-time price updates
            val job = subscribeToRealTimePrices(connector, symbol, service)
            subscriptionJobs[symbol] = job
            subscribedSymbols.add(symbol)
            
            // Step 3: Start monitoring for AI recommendations
            startContextMonitoring(symbol, service)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Load historical OHLCV data for indicator warmup.
     */
    private suspend fun loadHistoricalData(
        connector: UnifiedExchangeConnector,
        symbol: String,
        service: RealTimeIndicatorService
    ) {
        try {
            // Use getCandles from UnifiedExchangeConnector
            val candles = connector.getCandles(symbol, DEFAULT_TIMEFRAME, HISTORY_BARS)
            
            if (candles.isNotEmpty()) {
                // Convert OHLCVBar to OHLCV format expected by indicator service
                val bars = candles.map { bar ->
                    OHLCV(
                        timestamp = bar.timestamp,
                        open = bar.open,
                        high = bar.high,
                        low = bar.low,
                        close = bar.close,
                        volume = bar.volume
                    )
                }
                
                // Load into indicator service (most recent last for addBars)
                service.loadHistory(symbol, bars.reversed())
            }
        } catch (e: Exception) {
            // Log but don't fail - indicators will warm up from live data
            println("Warning: Could not load historical data for $symbol: ${e.message}")
        }
    }
    
    /**
     * Subscribe to real-time price updates via WebSocket.
     */
    private fun subscribeToRealTimePrices(
        connector: UnifiedExchangeConnector,
        symbol: String,
        service: RealTimeIndicatorService
    ): Job {
        return scope.launch {
            // Subscribe to price updates
            connector.subscribeToPrices(listOf(symbol)).collect { tick ->
                // Feed tick to indicator service
                service.onTick(
                    symbol = tick.symbol,
                    price = tick.last,
                    volume = tick.volume,
                    timestamp = tick.timestamp
                )
            }
        }
    }
    
    /**
     * Start monitoring context changes for AI recommendations.
     */
    private fun startContextMonitoring(
        symbol: String,
        service: RealTimeIndicatorService
    ) {
        scope.launch {
            service.getContextFlow(symbol).collect { state ->
                if (state?.isReady == true) {
                    processContextUpdate(symbol, state)
                }
            }
        }
    }
    
    /**
     * Process a context update - generate recommendations and check expansions.
     */
    private suspend fun processContextUpdate(
        symbol: String,
        state: SymbolIndicatorState
    ) {
        val selector = stahlSelector ?: return
        val expander = stahlExpander ?: return
        
        // Generate preset recommendation
        val recommendation = selector.recommendPreset(state.context)
        
        _recommendations.emit(SymbolRecommendation(
            symbol = symbol,
            preset = recommendation.preset,
            confidence = recommendation.confidence,
            reasoning = recommendation.reasoning,
            scores = emptyMap(),  // Scores not exposed in base PresetRecommendation
            context = state.context,
            timestamp = System.currentTimeMillis()
        ))
        
        // Check expansion for active positions
        val position = activePositions[symbol]
        if (position != null) {
            val expansionResult = expander.evaluateExpansion(
                position.basePosition,
                state.context
            )
            
            if (expansionResult.decision != ExpansionDecision.HOLD) {
                // Apply expansion
                position.applyExpansion(expansionResult)
                
                _expansionEvents.emit(SymbolExpansionEvent(
                    symbol = symbol,
                    decision = expansionResult.decision,
                    newStairs = expansionResult.newStairs,
                    reasoning = expansionResult.reasoning,
                    timestamp = System.currentTimeMillis()
                ))
            }
        }
    }
    
    // ==========================================================================
    // POSITION MANAGEMENT
    // ==========================================================================
    
    /**
     * Register an active position for expansion monitoring.
     */
    fun registerPosition(symbol: String, position: StahlPosition) {
        val expandedPosition = AIBoardStahlFactory.createExpandedPosition(position)
        activePositions[symbol] = expandedPosition
    }
    
    /**
     * Update position's current stair level (call when price moves).
     */
    suspend fun updatePositionStair(symbol: String, currentStairIndex: Int) {
        val position = activePositions[symbol] ?: return
        val service = indicatorService ?: return
        
        // Check if at top stair
        if (currentStairIndex >= position.totalStairCount - 1) {
            service.incrementBarsAtTopStair(symbol)
        } else {
            service.resetBarsAtTopStair(symbol)
        }
    }
    
    /**
     * Get the expanded position for a symbol.
     */
    fun getExpandedPosition(symbol: String): ExpandedStahlPosition? {
        return activePositions[symbol]
    }
    
    /**
     * Unregister a closed position.
     */
    fun unregisterPosition(symbol: String) {
        activePositions.remove(symbol)
        scope.launch {
            indicatorService?.resetBarsAtTopStair(symbol)
        }
    }
    
    // ==========================================================================
    // DIRECT ACCESS
    // ==========================================================================
    
    /**
     * Get current MarketContext for a symbol (one-shot, no subscription).
     */
    suspend fun getMarketContext(symbol: String): MarketContext? {
        return indicatorService?.getContext(symbol)
    }
    
    /**
     * Get preset recommendation for current market conditions.
     */
    suspend fun getRecommendation(symbol: String): PresetRecommendation? {
        val context = indicatorService?.getContext(symbol) ?: return null
        return stahlSelector?.recommendPreset(context)
    }
    
    /**
     * Get indicator snapshot for a symbol.
     */
    suspend fun getIndicatorSnapshot(symbol: String): IndicatorSnapshot? {
        return indicatorService?.getSnapshot(symbol)
    }
    
    /**
     * Check if a symbol has enough data for reliable indicators.
     */
    suspend fun isSymbolReady(symbol: String): Boolean {
        return indicatorService?.isReady(symbol) == true
    }
    
    // ==========================================================================
    // CLEANUP
    // ==========================================================================
    
    /**
     * Unsubscribe from a symbol's price feed.
     */
    fun unsubscribe(symbol: String) {
        subscriptionJobs[symbol]?.cancel()
        subscriptionJobs.remove(symbol)
        subscribedSymbols.remove(symbol)
        activePositions.remove(symbol)
    }
    
    /**
     * Shutdown the integration system.
     */
    fun shutdown() {
        subscriptionJobs.values.forEach { it.cancel() }
        subscriptionJobs.clear()
        subscribedSymbols.clear()
        activePositions.clear()
        
        indicatorService = null
        stahlSelector = null
        stahlExpander = null
        
        scope.cancel()
        instance = null
        
        _isInitialized.value = false
    }
}

// =============================================================================
// DATA CLASSES
// =============================================================================

/**
 * Symbol-specific preset recommendation with full context.
 * Wraps the AIBoardStahl PresetRecommendation with symbol and timestamp.
 */
data class SymbolRecommendation(
    val symbol: String,
    val preset: StahlPreset,
    val confidence: Double,
    val reasoning: String,
    val scores: Map<StahlPreset, Double>,
    val context: MarketContext,
    val timestamp: Long
)

/**
 * Stair expansion event with symbol tracking.
 */
data class SymbolExpansionEvent(
    val symbol: String,
    val decision: ExpansionDecision,
    val newStairs: List<StairLevel>,
    val reasoning: String,
    val timestamp: Long
)

// =============================================================================
// EXTENSION FUNCTIONS FOR TRADINGSYSTEM INTEGRATION
// =============================================================================

/**
 * Extension to integrate indicator system with TradingSystem.
 */
suspend fun TradingSystem.integrateIndicators(
    connector: UnifiedExchangeConnector,
    symbols: List<String>
): Result<TradingIndicatorIntegration> {
    val context = this.javaClass.getDeclaredField("context").apply {
        isAccessible = true
    }.get(this) as Context
    
    val integration = TradingIndicatorIntegration.getInstance(context)
    
    val initResult = integration.initialize()
    if (initResult.isFailure) {
        return Result.failure(initResult.exceptionOrNull()!!)
    }
    
    symbols.forEach { symbol ->
        val subscribeResult = integration.subscribeToExchange(connector, symbol)
        if (subscribeResult.isFailure) {
            return Result.failure(subscribeResult.exceptionOrNull()!!)
        }
    }
    
    return Result.success(integration)
}

// =============================================================================
// USAGE EXAMPLE
// =============================================================================

/**
 * Example integration with TradingSystem:
 * 
 * ```kotlin
 * // In TradingSystem or ViewModel
 * val integration = TradingIndicatorIntegration.getInstance(context)
 * 
 * // Initialize
 * integration.initialize(TradingMode.AUTONOMOUS)
 * 
 * // Set user preferences
 * integration.setUserConstraints(UserTradeConstraints(
 *     allowedPresets = setOf(StahlPreset.MODERATE, StahlPreset.CONSERVATIVE),
 *     allowStairExpansion = true
 * ))
 * 
 * // Subscribe to Kraken
 * val krakenConnector = exchangeRegistry.getConnector(SupportedExchange.KRAKEN)
 * integration.subscribeToExchange(krakenConnector, "BTC/USD")
 * 
 * // Listen for recommendations
 * integration.recommendations.collect { recommendation ->
 *     println("AI recommends ${recommendation.preset} for ${recommendation.symbol}")
 *     println("Confidence: ${recommendation.confidence}%")
 *     println("Reason: ${recommendation.reasoning}")
 * }
 * 
 * // When entering a trade, register position
 * val position = StahlPosition(...)
 * integration.registerPosition("BTC/USD", position)
 * 
 * // Listen for expansion events
 * integration.expansionEvents.collect { event ->
 *     if (event.decision == ExpansionDecision.EXPAND_ATR) {
 *         println("Expanded stairs: ${event.newStairs.size} new levels")
 *     }
 * }
 * ```
 */
