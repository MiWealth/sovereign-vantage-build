package com.miwealth.sovereignvantage.core.trading.engine

import com.miwealth.sovereignvantage.core.*
import com.miwealth.sovereignvantage.core.trading.StahlStairStop
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Order Executor - Executes trades via exchange API
 * 
 * Handles order placement, modification, and cancellation with:
 * - Retry logic for transient failures
 * - Rate limiting compliance
 * - Order validation before submission
 * - Real-time order status updates
 * - Leverage support for futures trading (V5.17.0)
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO: Arthur Iain McManus (1966-2025)
 */

sealed class OrderExecutionResult {
    data class Success(val order: ExecutedOrder) : OrderExecutionResult()
    data class PartialFill(val order: ExecutedOrder, val remainingQuantity: Double) : OrderExecutionResult()
    data class Rejected(val reason: String, val code: String? = null) : OrderExecutionResult()
    data class Error(val exception: Throwable) : OrderExecutionResult()
}

data class ExecutedOrder(
    val orderId: String,
    val clientOrderId: String = "",
    val symbol: String,
    val side: TradeSide,
    val type: OrderType,
    // Primary price fields — use price/quantity as canonical names (most callers use these)
    val price: Double = 0.0,
    val executedPrice: Double = price,
    // Primary quantity fields
    val quantity: Double = 0.0,
    val executedQuantity: Double = quantity,
    val fee: Double = 0.0,
    val feeCurrency: String = "",
    val status: OrderStatus,
    val timestamp: Long = System.currentTimeMillis(),
    val updatedAt: Long = timestamp,
    val exchange: String = "",
    val stairLevel: Int = 0,
    val profitDollar: Double = 0.0,
    val exchangeSymbol: String = symbol,
    val summary: String = ""
) {
    // Backward-compat aliases for callers using requestedPrice/requestedQuantity
    val requestedPrice: Double get() = price
    val requestedQuantity: Double get() = quantity
    val averagePrice: Double get() = executedPrice
    val filledQuantity: Double get() = executedQuantity
    val createdAt: Long get() = timestamp
    val remainingQuantity: Double get() = quantity - executedQuantity
}

data class OrderRequest(
    val symbol: String,
    val side: TradeSide,
    val type: OrderType,
    val quantity: Double,
    val price: Double? = null,          // Required for LIMIT orders
    val stopPrice: Double? = null,       // Required for STOP orders
    val takeProfitPrice: Double? = null, // Optional TP
    val stopLossPrice: Double? = null,   // Optional SL
    val leverage: Int? = null,           // Leverage multiplier (1x default, up to 125x for futures)
    val timeInForce: TimeInForce = TimeInForce.GTC,
    val reduceOnly: Boolean = false,
    val postOnly: Boolean = false,
    val clientOrderId: String = generateClientOrderId(),
    val trailingDelta: Double? = null     // Trailing stop callback rate (e.g. 1.0 = 1%)
)

enum class TimeInForce {
    GTC,    // Good Till Cancelled
    IOC,    // Immediate or Cancel
    FOK,    // Fill or Kill
    GTD     // Good Till Date
}

interface ExchangeAdapter {
    val exchangeName: String
    suspend fun placeOrder(request: OrderRequest): OrderExecutionResult
    suspend fun cancelOrder(orderId: String, symbol: String): Boolean
    suspend fun modifyOrder(orderId: String, symbol: String, newPrice: Double?, newQuantity: Double?): OrderExecutionResult
    suspend fun getOrderStatus(orderId: String, symbol: String): ExecutedOrder?
    suspend fun getOpenOrders(symbol: String? = null): List<ExecutedOrder>
    fun isRateLimited(): Boolean
}

class OrderExecutor(
    private val exchangeAdapter: ExchangeAdapter,
    private val marginSafeguard: MarginSafeguard? = null,  // FINAL GATE for margin protection
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    
    companion object {
        const val MAX_RETRIES = 3
        const val RETRY_DELAY_MS = 1000L
        const val ORDER_TIMEOUT_MS = 30000L
    }
    
    private val pendingOrders = ConcurrentHashMap<String, OrderRequest>()
    private val _orderUpdates = MutableSharedFlow<OrderUpdate>(replay = 1)
    val orderUpdates: SharedFlow<OrderUpdate> = _orderUpdates.asSharedFlow()
    
    private val stahl = StahlStairStop()
    
    /**
     * Execute a market order with immediate fill
     */
    suspend fun executeMarketOrder(
        symbol: String,
        side: TradeSide,
        quantity: Double
    ): OrderExecutionResult {
        val request = OrderRequest(
            symbol = symbol,
            side = side,
            type = OrderType.MARKET,
            quantity = quantity,
            timeInForce = TimeInForce.IOC
        )
        return executeOrder(request)
    }
    
    /**
     * Execute a limit order
     */
    suspend fun executeLimitOrder(
        symbol: String,
        side: TradeSide,
        quantity: Double,
        price: Double,
        postOnly: Boolean = false
    ): OrderExecutionResult {
        val request = OrderRequest(
            symbol = symbol,
            side = side,
            type = OrderType.LIMIT,
            quantity = quantity,
            price = price,
            postOnly = postOnly
        )
        return executeOrder(request)
    }
    
    /**
     * Execute a stop-loss order
     */
    suspend fun executeStopLossOrder(
        symbol: String,
        side: TradeSide,
        quantity: Double,
        stopPrice: Double
    ): OrderExecutionResult {
        val request = OrderRequest(
            symbol = symbol,
            side = side,
            type = OrderType.STOP_LOSS,
            quantity = quantity,
            stopPrice = stopPrice
        )
        return executeOrder(request)
    }
    
    /**
     * Execute order with STAHL Stair Stop™ attached
     */
    suspend fun executeWithStahlStop(
        symbol: String,
        side: TradeSide,
        quantity: Double,
        entryPrice: Double,
        useExtendedLevels: Boolean = false
    ): OrderExecutionResult {
        // Calculate initial stop and take profit
        val direction = if (side == TradeSide.BUY || side == TradeSide.LONG) "long" else "short"
        val initialStop = stahl.calculateInitialStop(entryPrice, direction)
        val takeProfit = stahl.calculateTakeProfit(entryPrice, direction)
        
        val request = OrderRequest(
            symbol = symbol,
            side = side,
            type = OrderType.MARKET,
            quantity = quantity,
            stopLossPrice = initialStop,
            takeProfitPrice = takeProfit
        )
        
        return executeOrder(request)
    }
    
    /**
     * Main order execution with retry logic
     * 
     * CRITICAL: MarginSafeguard is the FINAL GATE before any leveraged trade.
     * If margin check fails, the trade MUST NOT proceed.
     */
    suspend fun executeOrder(request: OrderRequest): OrderExecutionResult {
        // Validate order before submission
        val validationError = validateOrder(request)
        if (validationError != null) {
            return OrderExecutionResult.Rejected(validationError)
        }
        
        // =========================================================================
        // MARGIN SAFEGUARD - FINAL GATE (CRITICAL - THIS MUST NEVER BE BYPASSED)
        // =========================================================================
        if (marginSafeguard != null && !request.reduceOnly) {
            val leverage = request.leverage?.toDouble() ?: 1.0
            val price = request.price ?: request.stopPrice ?: 0.0
            
            if (price > 0 && leverage > 1.0) {
                val marginCheck = marginSafeguard.checkMarginForTrade(
                    symbol = request.symbol,
                    quantity = request.quantity,
                    price = price,
                    leverage = leverage,
                    isReduceOnly = request.reduceOnly
                )
                
                when (marginCheck) {
                    is MarginCheckResult.Rejected -> {
                        android.util.Log.w("OrderExecutor", 
                            "MARGIN GATE BLOCKED: ${marginCheck.reason}")
                        emitUpdate(OrderUpdate.Rejected(request, 
                            "Margin protection: ${marginCheck.reason}"))
                        return OrderExecutionResult.Rejected(
                            reason = marginCheck.reason,
                            code = "MARGIN_INSUFFICIENT"
                        )
                    }
                    is MarginCheckResult.ApprovedWithWarning -> {
                        android.util.Log.w("OrderExecutor",
                            "MARGIN WARNING: ${marginCheck.warning}")
                        // Continue but log the warning
                    }
                    is MarginCheckResult.Approved -> {
                        // All good, proceed
                    }
                }
            }
        }
        // =========================================================================
        // END MARGIN SAFEGUARD
        // =========================================================================
        
        // Check rate limits
        if (exchangeAdapter.isRateLimited()) {
            delay(1000) // Wait for rate limit to clear
        }
        
        pendingOrders[request.clientOrderId] = request
        emitUpdate(OrderUpdate.Submitted(request))
        
        var lastResult: OrderExecutionResult = OrderExecutionResult.Error(Exception("No attempts made"))
        var attempt = 0
        
        while (attempt < MAX_RETRIES) {
            attempt++
            
            try {
                val result = withTimeout(ORDER_TIMEOUT_MS) {
                    exchangeAdapter.placeOrder(request)
                }
                
                when (result) {
                    is OrderExecutionResult.Success -> {
                        pendingOrders.remove(request.clientOrderId)
                        emitUpdate(OrderUpdate.Filled(result.order))
                        return result
                    }
                    is OrderExecutionResult.PartialFill -> {
                        emitUpdate(OrderUpdate.PartiallyFilled(result.order, result.remainingQuantity))
                        return result
                    }
                    is OrderExecutionResult.Rejected -> {
                        // Don't retry rejected orders
                        pendingOrders.remove(request.clientOrderId)
                        emitUpdate(OrderUpdate.Rejected(request, result.reason))
                        return result
                    }
                    is OrderExecutionResult.Error -> {
                        lastResult = result
                        if (attempt < MAX_RETRIES) {
                            delay(RETRY_DELAY_MS * attempt) // Exponential backoff
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                lastResult = OrderExecutionResult.Error(e)
                if (attempt < MAX_RETRIES) {
                    delay(RETRY_DELAY_MS * attempt)
                }
            } catch (e: Exception) {
                lastResult = OrderExecutionResult.Error(e)
                if (attempt < MAX_RETRIES) {
                    delay(RETRY_DELAY_MS * attempt)
                }
            }
        }
        
        pendingOrders.remove(request.clientOrderId)
        emitUpdate(OrderUpdate.Failed(request, (lastResult as? OrderExecutionResult.Error)?.exception?.message ?: "Unknown error"))
        return lastResult
    }
    
    /**
     * Cancel an open order
     */
    suspend fun cancelOrder(orderId: String, symbol: String): Boolean {
        return try {
            val success = exchangeAdapter.cancelOrder(orderId, symbol)
            if (success) {
                emitUpdate(OrderUpdate.Cancelled(orderId))
            }
            success
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Cancel all open orders for a symbol
     */
    suspend fun cancelAllOrders(symbol: String): Int {
        val openOrders = exchangeAdapter.getOpenOrders(symbol)
        var cancelledCount = 0
        
        for (order in openOrders) {
            if (cancelOrder(order.orderId, symbol)) {
                cancelledCount++
            }
        }
        
        return cancelledCount
    }
    
    /**
     * Modify an existing order
     */
    suspend fun modifyOrder(
        orderId: String,
        symbol: String,
        newPrice: Double? = null,
        newQuantity: Double? = null
    ): OrderExecutionResult {
        return exchangeAdapter.modifyOrder(orderId, symbol, newPrice, newQuantity)
    }
    
    /**
     * Get status of a specific order
     */
    suspend fun getOrderStatus(orderId: String, symbol: String): ExecutedOrder? {
        return exchangeAdapter.getOrderStatus(orderId, symbol)
    }
    
    /**
     * Validate order before submission
     */
    private fun validateOrder(request: OrderRequest): String? {
        // Basic validation
        if (request.quantity <= 0) {
            return "Invalid quantity: must be positive"
        }
        
        if (request.type == OrderType.LIMIT && request.price == null) {
            return "Limit orders require a price"
        }
        
        if (request.type == OrderType.STOP_LOSS && request.stopPrice == null) {
            return "Stop orders require a stop price"
        }
        
        // Symbol format validation
        if (request.symbol.isBlank()) {
            return "Symbol cannot be empty"
        }
        
        return null
    }
    
    private suspend fun emitUpdate(update: OrderUpdate) {
        _orderUpdates.emit(update)
    }
    
    /**
     * Clean up resources
     */
    fun shutdown() {
        scope.cancel()
    }
}

sealed class OrderUpdate {
    data class Submitted(val request: OrderRequest) : OrderUpdate()
    data class Filled(val order: ExecutedOrder) : OrderUpdate()
    data class PartiallyFilled(val order: ExecutedOrder, val remainingQuantity: Double) : OrderUpdate()
    data class Cancelled(val orderId: String) : OrderUpdate()
    data class Rejected(val request: OrderRequest, val reason: String) : OrderUpdate()
    data class Failed(val request: OrderRequest, val error: String) : OrderUpdate()
    data class Modified(val orderId: String, val newPrice: Double?, val newQuantity: Double?) : OrderUpdate()
}

private fun generateClientOrderId(): String {
    return "SV-${System.currentTimeMillis()}-${(1000..9999).random()}"
}
