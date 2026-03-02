package com.miwealth.sovereignvantage.core.exchange.ai

/**
 * AI EXCHANGE ADAPTER FACTORY
 * 
 * Sovereign Vantage: Arthur Edition V5.17.0
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * Factory for creating exchange adapters that work with the TradingSystem.
 * Supports three modes:
 * 
 * 1. AI Mode: Uses AIConnectionManager for dynamic exchange connections
 * 2. Paper Trading Mode: Simulated trading for practice/testing
 * 3. Hybrid Mode: Paper trading with live price feeds
 * 
 * This bridges the AI Exchange Interface to the existing TradingCoordinator
 * and OrderExecutor infrastructure.
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */



import android.content.Context
import android.util.Log
import com.miwealth.sovereignvantage.core.*
import com.miwealth.sovereignvantage.core.exchange.*
import com.miwealth.sovereignvantage.core.trading.engine.*
import com.miwealth.sovereignvantage.core.trading.routing.RoutableExchangeAdapter
import com.miwealth.sovereignvantage.core.trading.routing.OrderBook as RoutingOrderBook
import com.miwealth.sovereignvantage.core.trading.routing.OrderBookLevel as RoutingOrderBookLevel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Trading execution mode
 */
enum class TradingExecutionMode {
    /** Live trading via AI exchange connectors */
    LIVE_AI,
    
    /** Live trading via hardcoded connectors (fallback) */
    LIVE_HARDCODED,
    
    /** Paper trading with simulated orders */
    PAPER,
    
    /** Paper trading with live market data feeds */
    PAPER_WITH_LIVE_DATA
}

/**
 * Factory for creating exchange adapters based on execution mode.
 */
class AIExchangeAdapterFactory(
    private val context: Context,
    private val aiConnectionManager: AIConnectionManager? = null
) {
    companion object {
        private const val TAG = "AIExchangeAdapterFactory"
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Create an exchange adapter based on execution mode.
     */
    suspend fun createAdapter(
        mode: TradingExecutionMode,
        exchangeId: String? = null,
        initialBalance: Double = 100_000.0
    ): ExchangeAdapter {
        return when (mode) {
            TradingExecutionMode.LIVE_AI -> {
                createLiveAIAdapter(exchangeId ?: "binance")
            }
            TradingExecutionMode.LIVE_HARDCODED -> {
                throw NotImplementedError("Use ExchangeRegistry for hardcoded connectors")
            }
            TradingExecutionMode.PAPER -> {
                createPaperTradingAdapter(initialBalance, null)
            }
            TradingExecutionMode.PAPER_WITH_LIVE_DATA -> {
                val priceProvider = aiConnectionManager?.let { manager ->
                    exchangeId?.let { id ->
                        manager.getConnector(id)
                    }
                }
                createPaperTradingAdapter(initialBalance, priceProvider)
            }
        }
    }
    
    /**
     * Create adapter using AIConnectionManager.
     */
    private suspend fun createLiveAIAdapter(exchangeId: String): ExchangeAdapter {
        val manager = aiConnectionManager
            ?: throw IllegalStateException("AIConnectionManager not initialized")
        
        val connector = manager.getConnector(exchangeId)
            ?: throw IllegalStateException("Exchange $exchangeId not connected")
        
        // AIExchangeConnector implements UnifiedExchangeConnector
        // We can use the existing UnifiedExchangeAdapter
        return AIUnifiedExchangeAdapter(connector, scope)
    }
    
    /**
     * Create paper trading adapter.
     */
    private fun createPaperTradingAdapter(
        initialBalance: Double,
        livePriceProvider: AIExchangeConnector?
    ): ExchangeAdapter {
        return PaperTradingAdapter(
            initialBalance = initialBalance,
            priceProvider = livePriceProvider,
            scope = scope
        )
    }
    
    /**
     * Shutdown factory and cleanup resources.
     */
    fun shutdown() {
        scope.cancel()
    }
}

/**
 * Adapter that wraps AIExchangeConnector for use with OrderExecutor and SmartOrderRouter.
 * 
 * V5.17.0 CHANGES:
 * - Now implements RoutableExchangeAdapter (extends ExchangeAdapter)
 * - Added getExchangeId() for SmartOrderRouter identity
 * - Added getOrderBook() with conversion to routing types
 * - isConnected() and getTicker() now override RoutableExchangeAdapter
 * - Enables AI connectors to participate in multi-exchange smart routing
 * 
 * Similar to UnifiedExchangeAdapter but specifically for AI connectors.
 */
class AIUnifiedExchangeAdapter(
    private val connector: AIExchangeConnector,
    private val scope: CoroutineScope
) : RoutableExchangeAdapter {
    
    override val exchangeName: String
        get() = connector.config.exchangeName
    
    override suspend fun placeOrder(request: OrderRequest): OrderExecutionResult {
        return try {
            val validation = connector.validateOrder(request)
            if (validation is OrderValidationResult.Invalid) {
                return OrderExecutionResult.Rejected(validation.reason)
            }
            connector.placeOrder(request)
        } catch (e: Exception) {
            OrderExecutionResult.Error(e)
        }
    }
    
    override suspend fun cancelOrder(orderId: String, symbol: String): Boolean {
        return try {
            connector.cancelOrder(orderId, symbol)
        } catch (e: Exception) {
            Log.e("AIUnifiedExchangeAdapter", "Cancel order failed", e)
            false
        }
    }
    
    override suspend fun modifyOrder(
        orderId: String,
        symbol: String,
        newPrice: Double?,
        newQuantity: Double?
    ): OrderExecutionResult {
        return try {
            connector.modifyOrder(orderId, symbol, newPrice, newQuantity)
        } catch (e: Exception) {
            OrderExecutionResult.Error(e)
        }
    }
    
    override suspend fun getOrderStatus(orderId: String, symbol: String): ExecutedOrder? {
        return try {
            connector.getOrder(orderId, symbol)
        } catch (e: Exception) {
            Log.e("AIUnifiedExchangeAdapter", "Get order status failed", e)
            null
        }
    }
    
    override suspend fun getOpenOrders(symbol: String?): List<ExecutedOrder> {
        return try {
            connector.getOpenOrders(symbol)
        } catch (e: Exception) {
            Log.e("AIUnifiedExchangeAdapter", "Get open orders failed", e)
            emptyList()
        }
    }
    
    override fun isRateLimited(): Boolean {
        return connector.isRateLimited()
    }
    
    // =========================================================================
    // ROUTABLE EXCHANGE ADAPTER — SmartOrderRouter compatibility (V5.17.0)
    // =========================================================================
    
    /**
     * Exchange identity for routing decisions and fee lookups.
     */
    override fun getExchangeId(): String = connector.config.exchangeId
    
    /**
     * Whether this AI connector is currently connected.
     */
    override fun isConnected(): Boolean = connector.isConnected()
    
    /**
     * Get current ticker for SmartOrderRouter price-based routing.
     */
    override suspend fun getTicker(symbol: String): PriceTick? = connector.getTicker(symbol)
    
    /**
     * Get order book for SmartOrderRouter liquidity analysis.
     * Converts core.exchange.OrderBook → core.trading.routing.OrderBook.
     * Returns null if AI connector cannot provide order book data
     * (router gracefully degrades to ticker-price-only routing).
     */
    override suspend fun getOrderBook(symbol: String, depth: Int): RoutingOrderBook? {
        return try {
            val exchangeOrderBook = connector.getOrderBook(symbol, depth) ?: return null
            RoutingOrderBook(
                symbol = exchangeOrderBook.symbol,
                bids = exchangeOrderBook.bids.map { RoutingOrderBookLevel(it.price, it.quantity) },
                asks = exchangeOrderBook.asks.map { RoutingOrderBookLevel(it.price, it.quantity) },
                timestamp = exchangeOrderBook.timestamp
            )
        } catch (e: Exception) {
            Log.w("AIUnifiedExchangeAdapter", "Order book unavailable for $symbol: ${e.message}")
            null  // Graceful degradation — router uses ticker price only
        }
    }
    
    // =========================================================================
    // EXTENDED API - AI connector capabilities beyond RoutableExchangeAdapter
    // =========================================================================
    
    suspend fun getBalances(): List<Balance> = connector.getBalances()
    
    fun subscribeToPrices(symbols: List<String>): Flow<PriceTick> {
        return connector.subscribeToPrices(symbols)
    }
    
    suspend fun connect(): Boolean = connector.connect()
    
    suspend fun disconnect() = connector.disconnect()
    
    fun getUnderlyingConnector(): AIExchangeConnector = connector
}

/**
 * Paper trading adapter for simulated order execution.
 * 
 * Features:
 * - Simulated order fills with realistic latency
 * - Virtual balance tracking
 * - Optional live price feeds for realistic pricing
 * - Slippage simulation
 * - Fee simulation
 */
class PaperTradingAdapter(
    private val initialBalance: Double = 100_000.0,
    private val priceProvider: AIExchangeConnector? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val simulatedLatencyMs: Long = 50,
    private val slippageBps: Int = 5,  // 5 basis points = 0.05%
    private val feeRateBps: Int = 10   // 10 basis points = 0.1%
) : ExchangeAdapter {
    
    companion object {
        private const val TAG = "PaperTradingAdapter"
    }
    
    override val exchangeName: String = "Paper Trading"
    
    // Virtual balances: asset -> amount
    // V5.17.0: Only initialise USDT. Previously both USDT and USD were set to
    // initialBalance, causing getPortfolioValue() to return 2x the actual balance.
    // Crypto pairs trade against USDT; if a user trades BTC/USD the adapter will
    // allocate from USDT and the UI will show A$ regardless.
    private val balances = ConcurrentHashMap<String, Double>().apply {
        put("USDT", initialBalance)
    }
    
    // Open orders
    private val openOrders = ConcurrentHashMap<String, PaperOrder>()
    
    // Order history
    private val orderHistory = mutableListOf<ExecutedOrder>()
    
    // Price cache (for when no live provider)
    private val priceCache = ConcurrentHashMap<String, Double>()
    
    // Order ID counter
    private val orderIdCounter = AtomicLong(System.currentTimeMillis())
    
    // =========================================================================
    // EXCHANGE ADAPTER IMPLEMENTATION
    // =========================================================================
    
    override suspend fun placeOrder(request: OrderRequest): OrderExecutionResult {
        // Simulate network latency
        delay(simulatedLatencyMs)
        
        return try {
            // Get current price
            val currentPrice = getCurrentPrice(request.symbol)
                ?: return OrderExecutionResult.Rejected("No price available for ${request.symbol}")
            
            // Validate balance
            val validationResult = validateBalance(request, currentPrice)
            if (validationResult != null) {
                return OrderExecutionResult.Rejected(validationResult)
            }
            
            // Generate order ID
            val orderId = "PAPER-${orderIdCounter.incrementAndGet()}"
            
            when (request.type) {
                OrderType.MARKET -> executeMarketOrder(orderId, request, currentPrice)
                OrderType.LIMIT -> placeLimitOrder(orderId, request)
                OrderType.STOP_LOSS, OrderType.STOP_LIMIT -> placeStopOrder(orderId, request)
                else -> OrderExecutionResult.Rejected("Order type ${request.type} not supported in paper trading")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Paper order failed", e)
            OrderExecutionResult.Error(e)
        }
    }
    
    override suspend fun cancelOrder(orderId: String, symbol: String): Boolean {
        delay(simulatedLatencyMs / 2)
        
        val order = openOrders.remove(orderId)
        if (order != null) {
            // Return reserved funds
            if (order.side == TradeSide.BUY || order.side == TradeSide.LONG) {
                val quoteAsset = getQuoteAsset(symbol)
                addBalance(quoteAsset, order.reservedAmount)
            } else {
                val baseAsset = getBaseAsset(symbol)
                addBalance(baseAsset, order.quantity)
            }
            Log.i(TAG, "Cancelled order $orderId")
            return true
        }
        return false
    }
    
    override suspend fun modifyOrder(
        orderId: String,
        symbol: String,
        newPrice: Double?,
        newQuantity: Double?
    ): OrderExecutionResult {
        delay(simulatedLatencyMs)
        
        val existingOrder = openOrders[orderId]
            ?: return OrderExecutionResult.Rejected("Order not found: $orderId")
        
        // Cancel and replace
        cancelOrder(orderId, symbol)
        
        val newRequest = OrderRequest(
            symbol = existingOrder.symbol,
            side = existingOrder.side,
            type = existingOrder.type,
            quantity = newQuantity ?: existingOrder.quantity,
            price = newPrice ?: existingOrder.price,
            clientOrderId = existingOrder.clientOrderId
        )
        
        return placeOrder(newRequest)
    }
    
    override suspend fun getOrderStatus(orderId: String, symbol: String): ExecutedOrder? {
        // Check open orders
        openOrders[orderId]?.let { return it.toExecutedOrder() }
        
        // Check history
        return orderHistory.find { it.orderId == orderId }
    }
    
    override suspend fun getOpenOrders(symbol: String?): List<ExecutedOrder> {
        return if (symbol != null) {
            openOrders.values.filter { it.symbol == symbol }.map { it.toExecutedOrder() }
        } else {
            openOrders.values.map { it.toExecutedOrder() }
        }
    }
    
    override fun isRateLimited(): Boolean = false
    
    // =========================================================================
    // PAPER TRADING SPECIFIC
    // =========================================================================
    
    /**
     * Get current virtual balance for an asset.
     */
    fun getBalance(asset: String): Double {
        return balances[asset] ?: 0.0
    }
    
    /**
     * Get all balances.
     */
    fun getAllBalances(): Map<String, Double> {
        return balances.toMap()
    }
    
    /**
     * Reset paper trading account.
     */
    fun resetAccount(newBalance: Double = initialBalance) {
        balances.clear()
        balances["USDT"] = newBalance
        balances["USD"] = newBalance
        openOrders.clear()
        orderHistory.clear()
        Log.i(TAG, "Paper trading account reset with balance: $newBalance")
    }
    
    /**
     * Set price for a symbol (when no live provider).
     */
    fun setPrice(symbol: String, price: Double) {
        priceCache[symbol] = price
    }
    
    /**
     * Get order history.
     */
    fun getOrderHistory(): List<ExecutedOrder> {
        return orderHistory.toList()
    }
    
    /**
     * Calculate total portfolio value.
     */
    suspend fun getPortfolioValue(): Double {
        var total = 0.0
        for ((asset, amount) in balances) {
            if (asset == "USDT" || asset == "USD") {
                total += amount
            } else {
                val price = getCurrentPrice("$asset/USDT") ?: getCurrentPrice("$asset/USD") ?: 0.0
                total += amount * price
            }
        }
        return total
    }
    
    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================
    
    private suspend fun getCurrentPrice(symbol: String): Double? {
        // Try live provider first
        priceProvider?.let { provider ->
            try {
                val ticker = provider.getTicker(symbol)
                ticker?.let { 
                    priceCache[symbol] = it.last
                    return it.last 
                }
            } catch (e: Exception) {
                Log.w(TAG, "Live price fetch failed for $symbol", e)
            }
        }
        
        // Fall back to cache
        return priceCache[symbol]
    }
    
    private fun validateBalance(request: OrderRequest, currentPrice: Double): String? {
        val cost = if (request.side == TradeSide.BUY || request.side == TradeSide.LONG) {
            val price = request.price ?: currentPrice
            request.quantity * price * (1 + feeRateBps / 10000.0)
        } else {
            request.quantity
        }
        
        val asset = if (request.side == TradeSide.BUY || request.side == TradeSide.LONG) {
            getQuoteAsset(request.symbol)
        } else {
            getBaseAsset(request.symbol)
        }
        
        val balance = balances[asset] ?: 0.0
        
        if (balance < cost) {
            return "Insufficient $asset balance. Required: $cost, Available: $balance"
        }
        
        return null
    }
    
    private fun executeMarketOrder(
        orderId: String,
        request: OrderRequest,
        currentPrice: Double
    ): OrderExecutionResult {
        // Apply slippage
        val slippageMultiplier = if (request.side == TradeSide.BUY || request.side == TradeSide.LONG) {
            1 + slippageBps / 10000.0
        } else {
            1 - slippageBps / 10000.0
        }
        val executedPrice = currentPrice * slippageMultiplier
        
        // Calculate fee
        val fee = request.quantity * executedPrice * (feeRateBps / 10000.0)
        
        // Update balances
        val baseAsset = getBaseAsset(request.symbol)
        val quoteAsset = getQuoteAsset(request.symbol)
        
        if (request.side == TradeSide.BUY || request.side == TradeSide.LONG) {
            val cost = request.quantity * executedPrice + fee
            subtractBalance(quoteAsset, cost)
            addBalance(baseAsset, request.quantity)
        } else {
            subtractBalance(baseAsset, request.quantity)
            val proceeds = request.quantity * executedPrice - fee
            addBalance(quoteAsset, proceeds)
        }
        
        // Create executed order
        val executedOrder = ExecutedOrder(
            orderId = orderId,
            clientOrderId = request.clientOrderId,
            symbol = request.symbol,
            side = request.side,
            type = request.type,
            price = currentPrice,
            executedPrice = executedPrice,
            quantity = request.quantity,
            executedQuantity = request.quantity,
            fee = fee,
            feeCurrency = quoteAsset,
            status = OrderStatus.FILLED,
            timestamp = System.currentTimeMillis(),
            exchange = exchangeName
        )
        
        orderHistory.add(executedOrder)
        
        Log.i(TAG, "Executed ${request.side} ${request.quantity} ${request.symbol} @ $executedPrice (fee: $fee)")
        
        return OrderExecutionResult.Success(executedOrder)
    }
    
    private fun placeLimitOrder(orderId: String, request: OrderRequest): OrderExecutionResult {
        val price = request.price ?: return OrderExecutionResult.Rejected("Limit order requires price")
        
        // Reserve funds
        val reservedAmount = if (request.side == TradeSide.BUY || request.side == TradeSide.LONG) {
            val quoteAsset = getQuoteAsset(request.symbol)
            val cost = request.quantity * price * (1 + feeRateBps / 10000.0)
            subtractBalance(quoteAsset, cost)
            cost
        } else {
            val baseAsset = getBaseAsset(request.symbol)
            subtractBalance(baseAsset, request.quantity)
            request.quantity
        }
        
        // Store open order
        val paperOrder = PaperOrder(
            orderId = orderId,
            clientOrderId = request.clientOrderId,
            symbol = request.symbol,
            side = request.side,
            type = request.type,
            quantity = request.quantity,
            price = price,
            reservedAmount = reservedAmount,
            timestamp = System.currentTimeMillis()
        )
        
        openOrders[orderId] = paperOrder
        
        Log.i(TAG, "Placed limit order $orderId: ${request.side} ${request.quantity} ${request.symbol} @ $price")
        
        return OrderExecutionResult.Success(paperOrder.toExecutedOrder(OrderStatus.OPEN))
    }
    
    private fun placeStopOrder(orderId: String, request: OrderRequest): OrderExecutionResult {
        val stopPrice = request.stopPrice ?: return OrderExecutionResult.Rejected("Stop order requires stop price")
        
        // Reserve funds (same as limit order)
        val reservedAmount = if (request.side == TradeSide.BUY || request.side == TradeSide.LONG) {
            val quoteAsset = getQuoteAsset(request.symbol)
            val cost = request.quantity * stopPrice * (1 + feeRateBps / 10000.0)
            subtractBalance(quoteAsset, cost)
            cost
        } else {
            val baseAsset = getBaseAsset(request.symbol)
            subtractBalance(baseAsset, request.quantity)
            request.quantity
        }
        
        val paperOrder = PaperOrder(
            orderId = orderId,
            clientOrderId = request.clientOrderId,
            symbol = request.symbol,
            side = request.side,
            type = request.type,
            quantity = request.quantity,
            price = request.price ?: stopPrice,
            stopPrice = stopPrice,
            reservedAmount = reservedAmount,
            timestamp = System.currentTimeMillis()
        )
        
        openOrders[orderId] = paperOrder
        
        Log.i(TAG, "Placed stop order $orderId: ${request.side} ${request.quantity} ${request.symbol} @ stop $stopPrice")
        
        return OrderExecutionResult.Success(paperOrder.toExecutedOrder(OrderStatus.OPEN))
    }
    
    private fun addBalance(asset: String, amount: Double) {
        balances.compute(asset) { _, current -> (current ?: 0.0) + amount }
    }
    
    private fun subtractBalance(asset: String, amount: Double) {
        balances.compute(asset) { _, current -> (current ?: 0.0) - amount }
    }
    
    private fun getBaseAsset(symbol: String): String {
        return symbol.split("/").firstOrNull() ?: symbol.take(3)
    }
    
    private fun getQuoteAsset(symbol: String): String {
        return symbol.split("/").lastOrNull() ?: "USDT"
    }
}

/**
 * Internal representation of a paper trading order.
 */
private data class PaperOrder(
    val orderId: String,
    val clientOrderId: String,
    val symbol: String,
    val side: TradeSide,
    val type: OrderType,
    val quantity: Double,
    val price: Double,
    val stopPrice: Double? = null,
    val reservedAmount: Double,
    val timestamp: Long
) {
    fun toExecutedOrder(status: OrderStatus = OrderStatus.OPEN): ExecutedOrder {
        return ExecutedOrder(
            orderId = orderId,
            clientOrderId = clientOrderId,
            symbol = symbol,
            side = side,
            type = type,
            price = price,
            executedPrice = price,
            quantity = quantity,
            executedQuantity = if (status == OrderStatus.FILLED) quantity else 0.0,
            fee = 0.0,
            feeCurrency = symbol.split("/").lastOrNull() ?: "USDT",
            status = status,
            timestamp = timestamp,
            exchange = "Paper Trading"
        )
    }
}
