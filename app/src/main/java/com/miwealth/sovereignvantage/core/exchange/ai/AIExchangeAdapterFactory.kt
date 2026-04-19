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
import com.miwealth.sovereignvantage.core.exchange.BinancePublicPriceFeed  // BUILD #111: For price fallback
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

    // BUILD #266: MARGIN-BASED MODEL — 100% USDT seed.
    // This is a TRADING platform, not a crypto wallet. We trade pairs
    // (BTC/USDT, ETH/USDT etc.) — LONG and SHORT are directional bets
    // settled entirely in USDT. No physical crypto ever changes hands.
    //
    // LONG  = bet price goes UP   → profit if price rises, loss if it falls
    // SHORT = bet price goes DOWN  → profit if price falls, loss if it rises
    //
    // All positions require only USDT margin. The previous crypto seeding
    // was incorrect — we never need to "hold" crypto to trade a crypto pair.
    //
    // Account equity   = USDT balance + sum of all unrealised P&L
    // Available margin = USDT balance - sum of all posted margins
    private val balances = ConcurrentHashMap<String, Double>().apply {
        put("USDT", initialBalance)
        Log.i("PaperTradingAdapter", "BUILD #266: Paper wallet seeded:")
        Log.i("PaperTradingAdapter", "  USDT: A$${String.format("%.0f", initialBalance)} (100% — margin-based trading)")
        Log.i("PaperTradingAdapter", "  LONG/SHORT both use USDT margin — no crypto holding required")
    }

    // Track posted margin per position for available margin calculation
    private val postedMargins = ConcurrentHashMap<String, Double>()
    
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
        Log.d(TAG, "📊 Portfolio value calculated: A$${String.format("%.2f", total)} (balances: ${balances.size} assets)")
        return total
    }
    
    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================
    
    private suspend fun getCurrentPrice(symbol: String): Double? {
        // BUILD #152: Normalize USD to USDT since Binance uses USDT
        val normalizedSymbol = if (symbol.endsWith("/USD")) {
            symbol.replace("/USD", "/USDT")
        } else {
            symbol
        }
        
        if (normalizedSymbol != symbol) {
            Log.i(TAG, "🔄 BUILD #152: Symbol normalized: $symbol → $normalizedSymbol")
        }
        
        // Try live provider first (AIExchangeConnector if connected)
        priceProvider?.let { provider ->
            try {
                val ticker = provider.getTicker(normalizedSymbol)
                ticker?.let { 
                    priceCache[normalizedSymbol] = it.last
                    priceCache[symbol] = it.last  // Cache both versions
                    return it.last 
                }
            } catch (e: Exception) {
                Log.w(TAG, "Live price provider failed for $normalizedSymbol: ${e.message}")
            }
        }
        
        // BUILD #111 FIX #2: Fallback to BinancePublicPriceFeed if provider failed
        // This is THE KEY FIX - even if AIExchangeConnector isn't working,
        // we can still get prices from the public Binance feed that's already running!
        try {
            val publicFeed = BinancePublicPriceFeed.getInstance()
            val tick = publicFeed.latestPrices.value[normalizedSymbol]  // BUILD #152: Use normalized symbol
            if (tick != null && tick.last > 0.0) {
                priceCache[normalizedSymbol] = tick.last
                priceCache[symbol] = tick.last  // Cache both versions
                Log.i(TAG, "✅ BUILD #111: Using BinancePublicPriceFeed fallback for $normalizedSymbol: ${tick.last}")
                return tick.last
            }
        } catch (e: Exception) {
            Log.w(TAG, "BinancePublicPriceFeed fallback failed: ${e.message}")
        }
        
        // Final fallback to cache (may be stale but better than nothing)
        val cachedPrice = priceCache[normalizedSymbol] ?: priceCache[symbol]  // BUILD #152: Check both
        if (cachedPrice == null) {
            Log.e(TAG, "⚠️ BUILD #111: NO PRICE AVAILABLE for $symbol (normalized: $normalizedSymbol) - all sources failed!")
            Log.e(TAG, "   - AIExchangeConnector: ${if (priceProvider != null) "connected but failed" else "not connected"}")
            Log.e(TAG, "   - BinancePublicPriceFeed: no data")
            Log.e(TAG, "   - Cache: empty")
        }
        return cachedPrice
    }
    
    private fun validateBalance(request: OrderRequest, currentPrice: Double): String? {
        // BUILD #266: MARGIN-BASED MODEL
        // Both LONG and SHORT only require USDT margin.
        // No crypto balance needed — these are pair trades, not spot purchases.
        val price = request.price ?: currentPrice
        val notionalValue = request.quantity * price
        val leverage: Int = (request.leverage).toInt()
        val marginRequired = com.miwealth.sovereignvantage.core.trading.TradingCosts
            .initialMargin(notionalValue, leverage)
        val entryCost = com.miwealth.sovereignvantage.core.trading.TradingCosts
            .entryCost(request.symbol, notionalValue)
        val totalRequired = marginRequired + entryCost

        val availableUsdt = balances["USDT"] ?: 0.0
        val usedMargin = postedMargins.values.sum()
        val freeMargin = availableUsdt - usedMargin

        if (freeMargin < totalRequired) {
            return "Insufficient margin. Required: ${String.format("%.2f", totalRequired)} USDT " +
                   "(margin: ${String.format("%.2f", marginRequired)} + " +
                   "fees: ${String.format("%.2f", entryCost)}), " +
                   "Available: ${String.format("%.2f", freeMargin)} USDT"
        }

        return null
    }
    
    private fun executeMarketOrder(
        orderId: String,
        request: OrderRequest,
        currentPrice: Double
    ): OrderExecutionResult {
        // BUILD #266: MARGIN-BASED MODEL
        // Apply half-spread as slippage at execution
        // LONG enters at ask (slightly above mid), SHORT enters at bid (slightly below)
        val symbol = request.symbol
        val halfSpread = when (symbol) {
            "BTC/USDT" -> 0.00005
            "ETH/USDT" -> 0.00010
            "SOL/USDT" -> 0.00015
            "XRP/USDT" -> 0.00015
            else       -> 0.00010
        }
        val slippageMultiplier = if (request.side == TradeSide.BUY || request.side == TradeSide.LONG) {
            1.0 + halfSpread   // Enter at ask
        } else {
            1.0 - halfSpread   // Enter at bid
        }
        val executedPrice = currentPrice * slippageMultiplier

        // Notional value and margin
        val leverage: Int = (request.leverage).toInt()
        val notionalValue = request.quantity * executedPrice
        val marginRequired = com.miwealth.sovereignvantage.core.trading.TradingCosts
            .initialMargin(notionalValue, leverage)
        val fee = notionalValue * com.miwealth.sovereignvantage.core.trading.TradingCosts.DEFAULT_FEE_RATE

        // Post margin from USDT — deduct fee immediately
        // Margin stays reserved until position closes
        subtractBalance("USDT", marginRequired + fee)
        postedMargins[orderId] = marginRequired

        val quoteAsset = getQuoteAsset(request.symbol)
        
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
            exchange = exchangeName,
            board = request.metadata["board"]  // BUILD #448: Transfer board from OrderRequest metadata
        )
        
        orderHistory.add(executedOrder)
        
        Log.i(TAG, "Executed ${request.side} ${request.quantity} ${request.symbol} @ $executedPrice (fee: $fee)")
        
        return OrderExecutionResult.Success(executedOrder)
    }
    
    private fun placeLimitOrder(orderId: String, request: OrderRequest): OrderExecutionResult {
        val price = request.price ?: return OrderExecutionResult.Rejected("Limit order requires price")
        
        // BUILD #266: Reserve USDT margin for limit orders (same as market orders)
        val leverage: Int = (request.leverage).toInt()
        val notionalValue = request.quantity * price
        val marginRequired = com.miwealth.sovereignvantage.core.trading.TradingCosts
            .initialMargin(notionalValue, leverage)
        val fee = notionalValue * com.miwealth.sovereignvantage.core.trading.TradingCosts.DEFAULT_FEE_RATE
        val reservedAmount = marginRequired + fee
        subtractBalance("USDT", reservedAmount)
        postedMargins[orderId] = marginRequired
        
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
            timestamp = System.currentTimeMillis(),
            board = request.metadata["board"]  // BUILD #448: Transfer board from OrderRequest metadata
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
            timestamp = System.currentTimeMillis(),
            board = request.metadata["board"]  // BUILD #448: Transfer board from OrderRequest metadata
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
    val timestamp: Long,
    val board: String? = null  // BUILD #448: Board attribution for dual capital tracking
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
            exchange = "Paper Trading",
            board = board  // BUILD #448: Transfer board from PaperOrder to ExecutedOrder
        )
    }
}
