package com.miwealth.sovereignvantage.core.exchange

import com.miwealth.sovereignvantage.core.*
import com.miwealth.sovereignvantage.core.trading.engine.OrderRequest
import com.miwealth.sovereignvantage.core.trading.engine.TimeInForce
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

/**
 * EXCHANGE AGGREGATOR IMPLEMENTATION
 * 
 * Aggregates data and routing across multiple exchanges:
 * - Best price discovery across all connected exchanges
 * - Smart order routing for optimal execution
 * - Order splitting for large orders (reduce slippage)
 * - Unified price feed combining all sources
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

class ExchangeAggregatorImpl(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : ExchangeAggregator {
    
    private val exchanges = ConcurrentHashMap<String, UnifiedExchangeConnector>()
    private val priceCache = ConcurrentHashMap<String, MutableMap<String, PriceTick>>()
    
    private val _aggregatedPrices = MutableSharedFlow<AggregatedPrice>(
        replay = 1,
        extraBufferCapacity = 100
    )
    
    private var aggregationJob: Job? = null
    
    companion object {
        private const val PRICE_STALE_MS = 5000L  // Consider price stale after 5s
        private const val MIN_SPLIT_VALUE_USD = 10000.0  // Minimum order size to consider splitting
        private const val MAX_SPLIT_EXCHANGES = 3  // Maximum exchanges to split across
    }
    
    // =========================================================================
    // EXCHANGE MANAGEMENT
    // =========================================================================
    
    override fun addExchange(connector: UnifiedExchangeConnector) {
        val id = connector.config.exchangeId
        exchanges[id] = connector
        
        // Start collecting prices from this exchange
        scope.launch {
            connector.status.collect { status ->
                if (status == ExchangeStatus.CONNECTED) {
                    startPriceCollection(connector)
                }
            }
        }
    }
    
    override fun removeExchange(exchangeId: String) {
        exchanges.remove(exchangeId)
        priceCache.values.forEach { it.remove(exchangeId) }
    }
    
    /**
     * Shutdown aggregator — cancel scope, disconnect all exchanges, clear caches.
     */
    fun shutdown() {
        scope.cancel()
        exchanges.values.forEach { runCatching { it.disconnect() } }
        exchanges.clear()
        priceCache.clear()
    }
    
    override fun getConnectedExchanges(): List<UnifiedExchangeConnector> {
        return exchanges.values.filter { it.isConnected() }
    }
    
    // =========================================================================
    // PRICE AGGREGATION
    // =========================================================================
    
    private fun startPriceCollection(connector: UnifiedExchangeConnector) {
        scope.launch {
            // Get all trading pairs from this exchange
            val pairs = connector.getTradingPairs()
            val symbols = pairs.map { it.symbol }
            
            // Subscribe to price updates
            connector.subscribeToPrices(symbols).collect { tick ->
                updatePriceCache(tick)
            }
        }
    }
    
    private fun updatePriceCache(tick: PriceTick) {
        val symbolCache = priceCache.getOrPut(tick.symbol) { ConcurrentHashMap() }
        symbolCache[tick.exchange] = tick
        
        // Emit aggregated price
        val aggregated = calculateAggregatedPrice(tick.symbol)
        aggregated?.let {
            scope.launch { _aggregatedPrices.emit(it) }
        }
    }
    
    private fun calculateAggregatedPrice(symbol: String): AggregatedPrice? {
        val symbolCache = priceCache[symbol] ?: return null
        val now = System.currentTimeMillis()
        
        // Filter out stale prices
        val freshPrices = symbolCache.values.filter { 
            now - it.timestamp < PRICE_STALE_MS 
        }
        
        if (freshPrices.isEmpty()) return null
        
        // Find best bid and ask
        val bestBid = freshPrices.maxByOrNull { it.bid }
        val bestAsk = freshPrices.minByOrNull { it.ask }
        
        if (bestBid == null || bestAsk == null) return null
        
        // Calculate VWAP (simplified - using last prices weighted by volume)
        val totalVolume = freshPrices.sumOf { it.volume }
        val vwap = if (totalVolume > 0) {
            freshPrices.sumOf { it.last * it.volume } / totalVolume
        } else {
            freshPrices.map { it.last }.average()
        }
        
        return AggregatedPrice(
            symbol = symbol,
            bestBid = bestBid.bid,
            bestBidExchange = bestBid.exchange,
            bestAsk = bestAsk.ask,
            bestAskExchange = bestAsk.exchange,
            vwap = vwap,
            totalVolume24h = totalVolume,
            exchanges = freshPrices.associateBy { it.exchange },
            timestamp = now
        )
    }
    
    override suspend fun getBestPrice(symbol: String): AggregatedPrice? {
        // First check cache
        calculateAggregatedPrice(symbol)?.let { return it }
        
        // Fetch fresh prices from all exchanges
        val tickers = mutableMapOf<String, PriceTick>()
        
        getConnectedExchanges().forEach { exchange ->
            try {
                exchange.getTicker(symbol)?.let { tick ->
                    tickers[exchange.config.exchangeId] = tick
                    updatePriceCache(tick)
                }
            } catch (e: Exception) {
                // Skip failed exchanges
            }
        }
        
        return calculateAggregatedPrice(symbol)
    }
    
    override fun subscribeToAggregatedPrices(symbols: List<String>): Flow<AggregatedPrice> {
        return _aggregatedPrices.filter { it.symbol in symbols }
    }
    
    // =========================================================================
    // SMART ORDER ROUTING
    // =========================================================================
    
    override suspend fun findBestExchange(request: OrderRequest): UnifiedExchangeConnector? {
        val connectedExchanges = getConnectedExchanges()
        if (connectedExchanges.isEmpty()) return null
        
        // Get order books from all exchanges
        data class ExchangeScore(
            val exchange: UnifiedExchangeConnector,
            val price: Double,
            val availableLiquidity: Double,
            val fee: Double,
            val score: Double
        )
        
        val scores = connectedExchanges.mapNotNull { exchange ->
            try {
                val orderBook = exchange.getOrderBook(request.symbol, 10) ?: return@mapNotNull null
                
                // Calculate effective price including slippage
                val (effectivePrice, liquidity) = if (request.side == TradeSide.BUY || request.side == TradeSide.LONG) {
                    calculateEffectiveBuyPrice(orderBook, request.quantity)
                } else {
                    calculateEffectiveSellPrice(orderBook, request.quantity)
                }
                
                // Factor in trading fees
                val fee = if (request.type == OrderType.LIMIT && request.postOnly) {
                    exchange.capabilities.tradingFeeMaker
                } else {
                    exchange.capabilities.tradingFeeTaker
                }
                
                val totalCost = effectivePrice * (1 + fee)
                
                // Score: lower is better for buys, higher for sells
                val score = if (request.side == TradeSide.BUY || request.side == TradeSide.LONG) {
                    -totalCost  // Negative because we want lowest cost
                } else {
                    totalCost * (1 - fee)  // Highest net proceeds
                }
                
                ExchangeScore(exchange, effectivePrice, liquidity, fee, score)
                
            } catch (e: Exception) {
                null
            }
        }
        
        if (scores.isEmpty()) return connectedExchanges.firstOrNull()
        
        // Return exchange with best score
        return scores.maxByOrNull { it.score }?.exchange
    }
    
    private fun calculateEffectiveBuyPrice(orderBook: OrderBook, quantity: Double): Pair<Double, Double> {
        var remaining = quantity
        var totalCost = 0.0
        var filledQty = 0.0
        
        for (ask in orderBook.asks) {
            if (remaining <= 0) break
            
            val fillQty = minOf(remaining, ask.quantity)
            totalCost += fillQty * ask.price
            filledQty += fillQty
            remaining -= fillQty
        }
        
        val effectivePrice = if (filledQty > 0) totalCost / filledQty else orderBook.bestAsk
        return effectivePrice to filledQty
    }
    
    private fun calculateEffectiveSellPrice(orderBook: OrderBook, quantity: Double): Pair<Double, Double> {
        var remaining = quantity
        var totalProceeds = 0.0
        var filledQty = 0.0
        
        for (bid in orderBook.bids) {
            if (remaining <= 0) break
            
            val fillQty = minOf(remaining, bid.quantity)
            totalProceeds += fillQty * bid.price
            filledQty += fillQty
            remaining -= fillQty
        }
        
        val effectivePrice = if (filledQty > 0) totalProceeds / filledQty else orderBook.bestBid
        return effectivePrice to filledQty
    }
    
    override suspend fun splitOrder(
        request: OrderRequest
    ): List<Pair<UnifiedExchangeConnector, OrderRequest>> {
        val aggregatedPrice = getBestPrice(request.symbol) ?: return emptyList()
        val orderValue = request.quantity * aggregatedPrice.midPrice
        
        // Don't split small orders
        if (orderValue < MIN_SPLIT_VALUE_USD) {
            val bestExchange = findBestExchange(request) ?: return emptyList()
            return listOf(bestExchange to request)
        }
        
        // Get liquidity from each exchange
        data class ExchangeLiquidity(
            val exchange: UnifiedExchangeConnector,
            val availableQty: Double,
            val effectivePrice: Double
        )
        
        val exchangeLiquidity = getConnectedExchanges().mapNotNull { exchange ->
            try {
                val orderBook = exchange.getOrderBook(request.symbol, 20) ?: return@mapNotNull null
                
                val (price, qty) = if (request.side == TradeSide.BUY || request.side == TradeSide.LONG) {
                    calculateEffectiveBuyPrice(orderBook, request.quantity)
                } else {
                    calculateEffectiveSellPrice(orderBook, request.quantity)
                }
                
                ExchangeLiquidity(exchange, qty, price)
                
            } catch (e: Exception) {
                null
            }
        }.sortedBy { 
            // Sort by best price
            if (request.side == TradeSide.BUY || request.side == TradeSide.LONG) {
                it.effectivePrice  // Lowest for buys
            } else {
                -it.effectivePrice  // Highest for sells
            }
        }
        
        if (exchangeLiquidity.isEmpty()) return emptyList()
        
        // Allocate quantity across exchanges
        val splits = mutableListOf<Pair<UnifiedExchangeConnector, OrderRequest>>()
        var remainingQty = request.quantity
        
        for (el in exchangeLiquidity.take(MAX_SPLIT_EXCHANGES)) {
            if (remainingQty <= 0) break
            
            val allocatedQty = minOf(remainingQty, el.availableQty * 0.8)  // Take max 80% of available
            if (allocatedQty > 0) {
                val splitRequest = request.copy(
                    quantity = allocatedQty,
                    clientOrderId = "${request.clientOrderId}_${el.exchange.config.exchangeId}"
                )
                splits.add(el.exchange to splitRequest)
                remainingQty -= allocatedQty
            }
        }
        
        // If we couldn't allocate all, put remainder on best exchange
        if (remainingQty > 0 && splits.isNotEmpty()) {
            val (bestExchange, bestRequest) = splits.first()
            splits[0] = bestExchange to bestRequest.copy(
                quantity = bestRequest.quantity + remainingQty
            )
        }
        
        return splits
    }
}

/**
 * Extension to copy OrderRequest with modifications
 */
private fun OrderRequest.copy(
    symbol: String = this.symbol,
    side: TradeSide = this.side,
    type: OrderType = this.type,
    quantity: Double = this.quantity,
    price: Double? = this.price,
    stopPrice: Double? = this.stopPrice,
    timeInForce: TimeInForce = this.timeInForce,
    postOnly: Boolean = this.postOnly,
    reduceOnly: Boolean = this.reduceOnly,
    clientOrderId: String = this.clientOrderId
): OrderRequest = OrderRequest(
    symbol = symbol,
    side = side,
    type = type,
    quantity = quantity,
    price = price,
    stopPrice = stopPrice,
    timeInForce = timeInForce,
    postOnly = postOnly,
    reduceOnly = reduceOnly,
    clientOrderId = clientOrderId
)