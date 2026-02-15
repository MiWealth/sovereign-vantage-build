package com.miwealth.sovereignvantage.service

import android.content.Context
import com.miwealth.sovereignvantage.core.*
import com.miwealth.sovereignvantage.core.exchange.*
import com.miwealth.sovereignvantage.core.trading.TradingSystem
import com.miwealth.sovereignvantage.core.trading.engine.UnifiedExchangeAdapter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * UNIFIED PRICE FEED SERVICE
 * 
 * Bridges the WebSocket price streams from UnifiedExchangeConnector (PQC-enabled)
 * to the TradingSystem for real-time market data.
 * 
 * Features:
 * - Multi-exchange price aggregation
 * - Automatic failover between exchanges
 * - Best-price routing for order execution
 * - OHLCV bar generation from tick data
 * - Integration with TradingCoordinator.onPriceUpdate()
 * 
 * Architecture:
 * ┌─────────────────────────────────────────────────────────────┐
 * │                  UnifiedPriceFeedService                    │
 * ├─────────────────────────────────────────────────────────────┤
 * │  ExchangeRegistry ──► UnifiedExchangeConnector(s)          │
 * │         │                    │                              │
 * │         ▼                    ▼                              │
 * │  subscribeToPrices() ◄── WebSocket Streams                 │
 * │         │                                                   │
 * │         ▼                                                   │
 * │  PriceTick Flow ──► TradingSystem.onPriceUpdate()         │
 * │         │                    │                              │
 * │         ▼                    ▼                              │
 * │  OHLCVBar Flow  ──► TradingCoordinator (AI Analysis)       │
 * └─────────────────────────────────────────────────────────────┘
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

/**
 * Price feed event for status tracking
 */
sealed class UnifiedPriceFeedEvent {
    data class ExchangeConnected(val exchangeId: String) : UnifiedPriceFeedEvent()
    data class ExchangeDisconnected(val exchangeId: String) : UnifiedPriceFeedEvent()
    data class ExchangeError(val exchangeId: String, val error: String) : UnifiedPriceFeedEvent()
    data class SubscriptionActive(val exchangeId: String, val symbols: List<String>) : UnifiedPriceFeedEvent()
    data class PriceReceived(val symbol: String, val exchange: String, val price: Double) : UnifiedPriceFeedEvent()
    object AllExchangesDisconnected : UnifiedPriceFeedEvent()
}

/**
 * Aggregated market data across exchanges
 */
data class AggregatedMarketData(
    val symbol: String,
    val bestBid: Double,
    val bestBidExchange: String,
    val bestAsk: Double,
    val bestAskExchange: String,
    val lastPrice: Double,
    val lastPriceExchange: String,
    val volume24h: Double,
    val exchangePrices: Map<String, PriceTick>,
    val timestamp: Long
) {
    val spread: Double get() = bestAsk - bestBid
    val spreadPercent: Double get() = if (bestBid > 0) (spread / bestBid) * 100 else 0.0
    val midPrice: Double get() = (bestBid + bestAsk) / 2
}

class UnifiedPriceFeedService(
    private val context: Context,
    private val exchangeRegistry: ExchangeRegistry,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    
    companion object {
        private const val OHLCV_INTERVAL_MS = 60_000L  // 1-minute bars
        private const val PRICE_STALE_THRESHOLD_MS = 30_000L  // Consider price stale after 30s
        
        @Volatile
        private var instance: UnifiedPriceFeedService? = null
        
        fun getInstance(context: Context, registry: ExchangeRegistry): UnifiedPriceFeedService {
            return instance ?: synchronized(this) {
                instance ?: UnifiedPriceFeedService(context, registry).also { instance = it }
            }
        }
    }
    
    // =========================================================================
    // STATE
    // =========================================================================
    
    private val subscribedSymbols = mutableSetOf<String>()
    private val exchangeAdapters = mutableMapOf<String, UnifiedExchangeAdapter>()
    private val priceCollectionJobs = mutableMapOf<String, Job>()
    
    // Latest prices per symbol per exchange
    private val latestPrices = mutableMapOf<String, MutableMap<String, PriceTick>>()
    
    // OHLCV bar generation
    private val ohlcvBuilders = mutableMapOf<String, OHLCVBarBuilder>()
    
    // =========================================================================
    // FLOWS
    // =========================================================================
    
    // Raw price ticks from all exchanges
    private val _priceTicks = MutableSharedFlow<PriceTick>(
        replay = 1,
        extraBufferCapacity = 500
    )
    val priceTicks: SharedFlow<PriceTick> = _priceTicks.asSharedFlow()
    
    // Generated OHLCV bars (1-minute)
    private val _ohlcvBars = MutableSharedFlow<OHLCVBar>(
        replay = 1,
        extraBufferCapacity = 100
    )
    val ohlcvBars: SharedFlow<OHLCVBar> = _ohlcvBars.asSharedFlow()
    
    // Aggregated best prices across exchanges
    private val _aggregatedPrices = MutableSharedFlow<AggregatedMarketData>(
        replay = 1,
        extraBufferCapacity = 100
    )
    val aggregatedPrices: SharedFlow<AggregatedMarketData> = _aggregatedPrices.asSharedFlow()
    
    // Service events
    private val _events = MutableSharedFlow<UnifiedPriceFeedEvent>(
        replay = 0,
        extraBufferCapacity = 50
    )
    val events: SharedFlow<UnifiedPriceFeedEvent> = _events.asSharedFlow()
    
    // Connection state
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    // =========================================================================
    // EXCHANGE MANAGEMENT
    // =========================================================================
    
    /**
     * Add an exchange adapter to the price feed
     */
    fun addExchange(adapter: UnifiedExchangeAdapter) {
        val exchangeId = adapter.getConfig().exchangeId
        exchangeAdapters[exchangeId] = adapter
        
        // Monitor connection status
        scope.launch {
            adapter.getStatus().collect { status ->
                when (status) {
                    ExchangeStatus.CONNECTED -> {
                        emitEvent(UnifiedPriceFeedEvent.ExchangeConnected(exchangeId))
                        updateConnectionState()
                        // Resubscribe to symbols
                        if (subscribedSymbols.isNotEmpty()) {
                            startPriceCollection(exchangeId, subscribedSymbols.toList())
                        }
                    }
                    ExchangeStatus.DISCONNECTED -> {
                        emitEvent(UnifiedPriceFeedEvent.ExchangeDisconnected(exchangeId))
                        updateConnectionState()
                    }
                    ExchangeStatus.ERROR -> {
                        emitEvent(UnifiedPriceFeedEvent.ExchangeError(exchangeId, "Connection error"))
                    }
                    else -> {}
                }
            }
        }
    }
    
    /**
     * Remove an exchange from the price feed
     */
    fun removeExchange(exchangeId: String) {
        priceCollectionJobs[exchangeId]?.cancel()
        priceCollectionJobs.remove(exchangeId)
        exchangeAdapters.remove(exchangeId)
        updateConnectionState()
    }
    
    /**
     * Get all connected exchanges
     */
    fun getConnectedExchanges(): List<String> {
        return exchangeAdapters.filter { it.value.isConnected() }.keys.toList()
    }
    
    // =========================================================================
    // SUBSCRIPTION MANAGEMENT
    // =========================================================================
    
    /**
     * Subscribe to price updates for symbols across all connected exchanges
     */
    fun subscribe(symbols: List<String>) {
        subscribedSymbols.addAll(symbols)
        
        // Initialize OHLCV builders for new symbols
        symbols.forEach { symbol ->
            if (!ohlcvBuilders.containsKey(symbol)) {
                ohlcvBuilders[symbol] = OHLCVBarBuilder(symbol)
            }
        }
        
        // Start collection on all connected exchanges
        exchangeAdapters.forEach { (exchangeId, adapter) ->
            if (adapter.isConnected()) {
                startPriceCollection(exchangeId, symbols)
            }
        }
    }
    
    /**
     * Unsubscribe from symbols
     */
    fun unsubscribe(symbols: List<String>) {
        subscribedSymbols.removeAll(symbols.toSet())
        symbols.forEach { symbol ->
            ohlcvBuilders.remove(symbol)
            latestPrices.remove(symbol)
        }
    }
    
    /**
     * Get currently subscribed symbols
     */
    fun getSubscribedSymbols(): Set<String> = subscribedSymbols.toSet()
    
    // =========================================================================
    // PRICE COLLECTION
    // =========================================================================
    
    private fun startPriceCollection(exchangeId: String, symbols: List<String>) {
        // Cancel existing job for this exchange
        priceCollectionJobs[exchangeId]?.cancel()
        
        val adapter = exchangeAdapters[exchangeId] ?: return
        
        priceCollectionJobs[exchangeId] = scope.launch {
            try {
                adapter.subscribeToPrices(symbols).collect { tick ->
                    processPriceTick(tick)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emitEvent(UnifiedPriceFeedEvent.ExchangeError(exchangeId, e.message ?: "Price collection error"))
            }
        }
        
        emitEvent(UnifiedPriceFeedEvent.SubscriptionActive(exchangeId, symbols))
    }
    
    private suspend fun processPriceTick(tick: PriceTick) {
        // Store latest price
        latestPrices.getOrPut(tick.symbol) { mutableMapOf() }[tick.exchange] = tick
        
        // Emit raw tick
        _priceTicks.emit(tick)
        
        // Update OHLCV bar builder
        ohlcvBuilders[tick.symbol]?.addTick(tick)
        
        // Generate aggregated price
        generateAggregatedPrice(tick.symbol)
        
        // Emit event
        emitEvent(UnifiedPriceFeedEvent.PriceReceived(tick.symbol, tick.exchange, tick.last))
    }
    
    private suspend fun generateAggregatedPrice(symbol: String) {
        val exchangePrices = latestPrices[symbol] ?: return
        if (exchangePrices.isEmpty()) return
        
        val now = System.currentTimeMillis()
        val validPrices = exchangePrices.filter { 
            now - it.value.timestamp < PRICE_STALE_THRESHOLD_MS 
        }
        
        if (validPrices.isEmpty()) return
        
        var bestBid = 0.0
        var bestBidExchange = ""
        var bestAsk = Double.MAX_VALUE
        var bestAskExchange = ""
        var lastPrice = 0.0
        var lastPriceExchange = ""
        var latestTimestamp = 0L
        var totalVolume = 0.0
        
        validPrices.forEach { (exchange, tick) ->
            // Best bid (highest)
            if (tick.bid > bestBid) {
                bestBid = tick.bid
                bestBidExchange = exchange
            }
            // Best ask (lowest)
            if (tick.ask > 0 && tick.ask < bestAsk) {
                bestAsk = tick.ask
                bestAskExchange = exchange
            }
            // Most recent last price
            if (tick.timestamp > latestTimestamp) {
                lastPrice = tick.last
                lastPriceExchange = exchange
                latestTimestamp = tick.timestamp
            }
            totalVolume += tick.volume
        }
        
        if (bestAsk == Double.MAX_VALUE) bestAsk = lastPrice
        
        val aggregated = AggregatedMarketData(
            symbol = symbol,
            bestBid = bestBid,
            bestBidExchange = bestBidExchange,
            bestAsk = bestAsk,
            bestAskExchange = bestAskExchange,
            lastPrice = lastPrice,
            lastPriceExchange = lastPriceExchange,
            volume24h = totalVolume,
            exchangePrices = validPrices.mapValues { it.value },
            timestamp = now
        )
        
        _aggregatedPrices.emit(aggregated)
    }
    
    // =========================================================================
    // OHLCV BAR GENERATION
    // =========================================================================
    
    /**
     * Start OHLCV bar generation loop
     */
    fun startOHLCVGeneration() {
        scope.launch {
            while (isActive) {
                delay(OHLCV_INTERVAL_MS)
                generateOHLCVBars()
            }
        }
    }
    
    private suspend fun generateOHLCVBars() {
        ohlcvBuilders.forEach { (symbol, builder) ->
            builder.finishBar()?.let { bar ->
                _ohlcvBars.emit(bar)
            }
        }
    }
    
    // =========================================================================
    // TRADING SYSTEM INTEGRATION
    // =========================================================================
    
    /**
     * Wire price feeds to TradingSystem.
     * This connects WebSocket streams to TradingCoordinator.onPriceUpdate()
     */
    fun wireToTradingSystem(tradingSystem: TradingSystem) {
        // Connect OHLCV bars to TradingSystem for indicator calculations
        scope.launch {
            ohlcvBars.collect { bar ->
                tradingSystem.onPriceUpdate(
                    symbol = bar.symbol,
                    open = bar.open,
                    high = bar.high,
                    low = bar.low,
                    close = bar.close,
                    volume = bar.volume
                )
            }
        }
        
        // Connect price ticks with spread for scalping
        scope.launch {
            priceTicks.collect { tick ->
                // Use the tick data to generate a synthetic OHLCV for real-time updates
                tradingSystem.onPriceUpdateWithSpread(
                    symbol = tick.symbol,
                    open = tick.last,  // For tick data, all OHLC are the same
                    high = tick.last,
                    low = tick.last,
                    close = tick.last,
                    volume = tick.volume,
                    bid = tick.bid,
                    ask = tick.ask
                )
            }
        }
    }
    
    /**
     * Get best price for order routing
     */
    fun getBestPriceForOrder(symbol: String, side: TradeSide): Pair<String, Double>? {
        val prices = latestPrices[symbol] ?: return null
        
        return when (side) {
            TradeSide.BUY, TradeSide.LONG -> {
                // For buys, we want the lowest ask
                prices.minByOrNull { it.value.ask }?.let { 
                    it.key to it.value.ask 
                }
            }
            TradeSide.SELL, TradeSide.SHORT -> {
                // For sells, we want the highest bid
                prices.maxByOrNull { it.value.bid }?.let { 
                    it.key to it.value.bid 
                }
            }
        }
    }
    
    /**
     * Get exchange adapter with best price for a symbol and side
     */
    fun getBestExchangeForOrder(symbol: String, side: TradeSide): UnifiedExchangeAdapter? {
        val (exchangeId, _) = getBestPriceForOrder(symbol, side) ?: return null
        return exchangeAdapters[exchangeId]
    }
    
    // =========================================================================
    // LIFECYCLE
    // =========================================================================
    
    /**
     * Connect to all registered exchanges
     */
    suspend fun connectAll() {
        exchangeAdapters.forEach { (_, adapter) ->
            if (!adapter.isConnected()) {
                adapter.connect()
            }
        }
    }
    
    /**
     * Disconnect from all exchanges
     */
    suspend fun disconnectAll() {
        priceCollectionJobs.values.forEach { it.cancel() }
        priceCollectionJobs.clear()
        
        exchangeAdapters.forEach { (_, adapter) ->
            adapter.disconnect()
        }
    }
    
    /**
     * Shutdown the service
     */
    fun shutdown() {
        scope.cancel()
        priceCollectionJobs.clear()
        exchangeAdapters.clear()
        subscribedSymbols.clear()
        latestPrices.clear()
        ohlcvBuilders.clear()
        instance = null
    }
    
    // =========================================================================
    // HELPERS
    // =========================================================================
    
    private fun updateConnectionState() {
        _isConnected.value = exchangeAdapters.any { it.value.isConnected() }
        
        if (!_isConnected.value && exchangeAdapters.isNotEmpty()) {
            scope.launch {
                emitEvent(UnifiedPriceFeedEvent.AllExchangesDisconnected)
            }
        }
    }
    
    private fun emitEvent(event: UnifiedPriceFeedEvent) {
        scope.launch {
            _events.emit(event)
        }
    }
}

// =============================================================================
// OHLCV BAR BUILDER
// =============================================================================

/**
 * Builds OHLCV bars from price ticks
 */
class OHLCVBarBuilder(private val symbol: String) {
    private var open: Double? = null
    private var high: Double = Double.MIN_VALUE
    private var low: Double = Double.MAX_VALUE
    private var close: Double = 0.0
    private var volume: Double = 0.0
    private var startTime: Long = 0
    private var tickCount: Int = 0
    
    fun addTick(tick: PriceTick) {
        if (open == null) {
            open = tick.last
            startTime = System.currentTimeMillis()
        }
        
        if (tick.last > high) high = tick.last
        if (tick.last < low) low = tick.last
        close = tick.last
        volume += tick.volume
        tickCount++
    }
    
    fun finishBar(): OHLCVBar? {
        val currentOpen = open ?: return null
        if (tickCount == 0) return null
        
        val bar = OHLCVBar(
            symbol = symbol,
            open = currentOpen,
            high = if (high == Double.MIN_VALUE) currentOpen else high,
            low = if (low == Double.MAX_VALUE) currentOpen else low,
            close = close,
            volume = volume,
            timestamp = startTime,
            interval = "1m"
        )
        
        // Reset for next bar
        reset()
        
        return bar
    }
    
    fun reset() {
        open = null
        high = Double.MIN_VALUE
        low = Double.MAX_VALUE
        close = 0.0
        volume = 0.0
        startTime = 0
        tickCount = 0
    }
}
