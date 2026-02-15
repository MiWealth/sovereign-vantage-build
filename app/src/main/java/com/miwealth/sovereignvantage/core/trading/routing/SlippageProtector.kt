package com.miwealth.sovereignvantage.core.trading.routing

import com.miwealth.sovereignvantage.core.*
import com.miwealth.sovereignvantage.core.trading.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * SLIPPAGE PROTECTOR
 * 
 * Protects orders from excessive slippage through pre-execution
 * estimation, real-time monitoring, and historical tracking.
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 */

data class SlippageEstimate(
    val symbol: String,
    val side: TradeSide,
    val quantity: Double,
    val estimatedSlippage: Double,
    val estimatedSlippagePercent: Double,
    val spreadComponent: Double,
    val impactComponent: Double,
    val volatilityComponent: Double,
    val confidence: SlippageConfidence,
    val isAcceptable: Boolean,
    val maxAcceptableSlippage: Double,
    val recommendation: String
)

enum class SlippageConfidence { HIGH, MEDIUM, LOW }

data class PreExecutionCheck(
    val symbol: String,
    val side: TradeSide,
    val quantity: Double,
    val estimatedSlippage: Double,
    val isAcceptable: Boolean,
    val reason: String,
    val suggestedAction: SuggestedAction
)

enum class SuggestedAction { PROCEED, REDUCE_SIZE, USE_LIMIT_ORDER, WAIT, ABORT }

data class SlippageMonitor(
    val orderId: String,
    val symbol: String,
    val side: TradeSide,
    val expectedPrice: Double,
    val maxSlippagePercent: Double,
    val startTime: Long = System.currentTimeMillis(),
    var currentSlippage: Double = 0.0,
    var triggered: Boolean = false
)

data class SlippageRecord(
    val symbol: String,
    val exchangeId: String,
    val side: TradeSide,
    val orderType: OrderType,
    val quantity: Double,
    val expectedPrice: Double,
    val actualPrice: Double,
    val slippage: Double,
    val slippagePercent: Double,
    val timestamp: Long,
    val marketCondition: MarketCondition
)

enum class MarketCondition { CALM, NORMAL, VOLATILE, EXTREME }

class SlippageProtector(
    private val defaultMaxSlippagePercent: Double = 0.5,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    
    private var maxSlippagePercent = defaultMaxSlippagePercent
    private var volatilityMultiplier = 1.5
    
    private val activeMonitors = ConcurrentHashMap<String, SlippageMonitor>()
    private val slippageHistory = ConcurrentHashMap<String, MutableList<SlippageRecord>>()
    private val priceCache = ConcurrentHashMap<String, PriceTick>()
    private val volatilityCache = ConcurrentHashMap<String, Double>()
    private val orderBookCache = ConcurrentHashMap<String, OrderBook>()
    
    private val _slippageEvents = MutableSharedFlow<SlippageEvent>(extraBufferCapacity = 100)
    val slippageEvents: SharedFlow<SlippageEvent> = _slippageEvents.asSharedFlow()
    
    private val _stats = MutableStateFlow(SlippageStats())
    val stats: StateFlow<SlippageStats> = _stats.asStateFlow()
    
    fun setMaxSlippage(percent: Double) { maxSlippagePercent = percent }
    
    fun getEffectiveMaxSlippage(symbol: String): Double {
        val volatility = volatilityCache[symbol] ?: return maxSlippagePercent
        val condition = getMarketCondition(volatility)
        return when (condition) {
            MarketCondition.CALM -> maxSlippagePercent * 0.8
            MarketCondition.NORMAL -> maxSlippagePercent
            MarketCondition.VOLATILE -> maxSlippagePercent * volatilityMultiplier
            MarketCondition.EXTREME -> maxSlippagePercent * 2.0
        }
    }
    
    fun checkPreExecution(symbol: String, side: TradeSide, quantity: Double): PreExecutionCheck {
        val estimate = estimateSlippage(symbol, side, quantity, emptyList())
        val effectiveMax = getEffectiveMaxSlippage(symbol)
        val isAcceptable = estimate.estimatedSlippagePercent <= effectiveMax
        
        val (action, reason) = when {
            isAcceptable -> SuggestedAction.PROCEED to "Slippage within acceptable range"
            estimate.estimatedSlippagePercent <= effectiveMax * 1.5 -> 
                SuggestedAction.USE_LIMIT_ORDER to "Consider limit order to reduce slippage"
            estimate.estimatedSlippagePercent <= effectiveMax * 2.0 -> 
                SuggestedAction.REDUCE_SIZE to "High slippage - consider splitting order"
            estimate.confidence == SlippageConfidence.LOW -> 
                SuggestedAction.WAIT to "Insufficient market data"
            else -> SuggestedAction.ABORT to "Slippage too high"
        }
        
        return PreExecutionCheck(symbol, side, quantity, estimate.estimatedSlippagePercent, isAcceptable, reason, action)
    }
    
    fun estimateSlippage(symbol: String, side: TradeSide, quantity: Double, routes: List<ExchangeRoute>): SlippageEstimate {
        val priceTick = priceCache[symbol]
        val orderBook = orderBookCache[symbol]
        val volatility = volatilityCache[symbol] ?: 0.0
        
        val spreadComponent = priceTick?.spread?.div(2) ?: 0.0
        val impactComponent = calculateMarketImpact(symbol, side, quantity, orderBook)
        val volatilityComponent = volatility * 0.001
        
        val totalSlippage = spreadComponent + impactComponent + volatilityComponent
        val basePrice = priceTick?.mid ?: routes.firstOrNull()?.price ?: 1.0
        val slippagePercent = if (basePrice > 0) (totalSlippage / basePrice) * 100 else 0.0
        
        val confidence = when {
            priceTick != null && orderBook != null -> SlippageConfidence.HIGH
            priceTick != null || orderBook != null -> SlippageConfidence.MEDIUM
            else -> SlippageConfidence.LOW
        }
        
        val effectiveMax = getEffectiveMaxSlippage(symbol)
        
        return SlippageEstimate(
            symbol = symbol, side = side, quantity = quantity,
            estimatedSlippage = totalSlippage, estimatedSlippagePercent = slippagePercent,
            spreadComponent = spreadComponent, impactComponent = impactComponent,
            volatilityComponent = volatilityComponent, confidence = confidence,
            isAcceptable = slippagePercent <= effectiveMax,
            maxAcceptableSlippage = effectiveMax,
            recommendation = if (slippagePercent <= effectiveMax) "Proceed" else "Consider limit order"
        )
    }
    
    private fun calculateMarketImpact(symbol: String, side: TradeSide, quantity: Double, orderBook: OrderBook?): Double {
        if (orderBook == null) return estimateImpactFromHistory(symbol, quantity)
        
        val levels = when (side) {
            TradeSide.BUY, TradeSide.LONG -> orderBook.asks
            TradeSide.SELL, TradeSide.SHORT -> orderBook.bids
        }
        
        if (levels.isEmpty()) return 0.0
        
        var remainingQty = quantity
        var totalCost = 0.0
        val bestPrice = levels.first().price
        
        for (level in levels) {
            val fillQty = minOf(remainingQty, level.quantity)
            totalCost += fillQty * level.price
            remainingQty -= fillQty
            if (remainingQty <= 0) break
        }
        
        if (remainingQty > 0) {
            val lastPrice = levels.lastOrNull()?.price ?: bestPrice
            totalCost += remainingQty * lastPrice * 1.01
        }
        
        return abs((totalCost / quantity) - bestPrice)
    }
    
    private fun estimateImpactFromHistory(symbol: String, quantity: Double): Double {
        val history = slippageHistory[symbol] ?: return 0.0
        val similar = history.filter { it.quantity >= quantity * 0.5 && it.quantity <= quantity * 2.0 }.takeLast(20)
        if (similar.isEmpty()) return 0.0
        return similar.map { it.slippage }.average() * sqrt(quantity / similar.map { it.quantity }.average())
    }
    
    fun startMonitoring(orderId: String, symbol: String, side: TradeSide, expectedPrice: Double): SlippageMonitor {
        val monitor = SlippageMonitor(orderId, symbol, side, expectedPrice, getEffectiveMaxSlippage(symbol))
        activeMonitors[orderId] = monitor
        return monitor
    }
    
    fun updateMonitor(orderId: String, currentPrice: Double): Boolean {
        val monitor = activeMonitors[orderId] ?: return false
        val slippage = when (monitor.side) {
            TradeSide.BUY, TradeSide.LONG -> (currentPrice - monitor.expectedPrice) / monitor.expectedPrice * 100
            TradeSide.SELL, TradeSide.SHORT -> (monitor.expectedPrice - currentPrice) / monitor.expectedPrice * 100
        }
        monitor.currentSlippage = slippage
        
        if (slippage > monitor.maxSlippagePercent && !monitor.triggered) {
            monitor.triggered = true
            scope.launch { _slippageEvents.emit(SlippageEvent.ThresholdBreached(monitor)) }
            return true
        }
        return false
    }
    
    fun stopMonitoring(orderId: String) { activeMonitors.remove(orderId) }
    
    fun recordSlippage(record: SlippageRecord) {
        val history = slippageHistory.getOrPut(record.symbol) { mutableListOf() }
        history.add(record)
        if (history.size > 1000) history.removeAt(0)
        
        _stats.update { it.copy(
            totalSlippageRecorded = it.totalSlippageRecorded + 1,
            avgSlippagePercent = (it.avgSlippagePercent * it.totalSlippageRecorded + record.slippagePercent) / (it.totalSlippageRecorded + 1)
        )}
    }
    
    fun updatePrice(symbol: String, tick: PriceTick) { priceCache[symbol] = tick }
    fun updateOrderBook(symbol: String, book: OrderBook) { orderBookCache[symbol] = book }
    fun updateVolatility(symbol: String, volatility: Double) { volatilityCache[symbol] = volatility }
    
    private fun getMarketCondition(volatility: Double): MarketCondition = when {
        volatility < 0.5 -> MarketCondition.CALM
        volatility < 1.5 -> MarketCondition.NORMAL
        volatility < 3.0 -> MarketCondition.VOLATILE
        else -> MarketCondition.EXTREME
    }
    
    fun getHistoricalStats(symbol: String): SlippageHistoryStats? {
        val history = slippageHistory[symbol] ?: return null
        if (history.isEmpty()) return null
        
        val slippages = history.map { it.slippagePercent }
        return SlippageHistoryStats(
            symbol = symbol, sampleCount = history.size,
            avgSlippage = slippages.average(),
            minSlippage = slippages.minOrNull() ?: 0.0,
            maxSlippage = slippages.maxOrNull() ?: 0.0,
            stdDevSlippage = calculateStdDev(slippages)
        )
    }
    
    private fun calculateStdDev(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        return sqrt(values.map { (it - mean) * (it - mean) }.average())
    }
    
    fun shutdown() { scope.cancel() }
}

sealed class SlippageEvent {
    data class ThresholdBreached(val monitor: SlippageMonitor) : SlippageEvent()
    data class RecordAdded(val record: SlippageRecord) : SlippageEvent()
}

data class SlippageStats(
    val totalSlippageRecorded: Long = 0,
    val avgSlippagePercent: Double = 0.0,
    val ordersAborted: Int = 0
)

data class SlippageHistoryStats(
    val symbol: String,
    val sampleCount: Int,
    val avgSlippage: Double,
    val minSlippage: Double,
    val maxSlippage: Double,
    val stdDevSlippage: Double
)
