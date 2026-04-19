package com.miwealth.sovereignvantage.core.trading.routing

import com.miwealth.sovereignvantage.core.*
import com.miwealth.sovereignvantage.core.exchange.*
import com.miwealth.sovereignvantage.core.trading.engine.*
import com.miwealth.sovereignvantage.service.UnifiedPriceFeedService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * SMART ORDER EXECUTOR
 * 
 * Bridge adapter that wraps SmartOrderRouter and implements the ExchangeAdapter
 * interface, enabling the existing TradingSystem/OrderExecutor infrastructure
 * to leverage intelligent multi-exchange order routing.
 * 
 * This is the integration point between:
 * - OLD: OrderExecutor → single ExchangeAdapter → single exchange
 * - NEW: OrderExecutor → SmartOrderExecutor → SmartOrderRouter → best exchange(s)
 * 
 * Features:
 * - Implements ExchangeAdapter for drop-in replacement
 * - Routes orders through SmartOrderRouter for optimal execution
 * - Aggregates order status across all connected exchanges
 * - Preserves paper trading mode (routes to simulated fills)
 * - Tracks execution statistics for performance monitoring
 * 
 * Architecture:
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │                      TRADING SYSTEM                                     │
 * │                           │                                             │
 * │                    OrderExecutor                                        │
 * │                           │                                             │
 * │                  SmartOrderExecutor ◄── implements ExchangeAdapter      │
 * │                           │                                             │
 * │                   SmartOrderRouter                                      │
 * │                    /      |      \                                      │
 * │              Kraken   Binance   Coinbase   Bybit   OKX   ...           │
 * └─────────────────────────────────────────────────────────────────────────┘
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

class SmartOrderExecutor(
    private val router: SmartOrderRouter,
    private val config: SmartOrderExecutorConfig = SmartOrderExecutorConfig(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : ExchangeAdapter {
    
    companion object {
        private const val TAG = "SmartOrderExecutor"
    }
    
    override val exchangeName: String = "SmartRouter"
    
    // =========================================================================
    // STATE TRACKING
    // =========================================================================
    
    // Track all orders placed through this executor (aggregated across exchanges)
    private val orderRegistry = ConcurrentHashMap<String, SmartOrderRecord>()
    
    // Track which exchange each order was routed to
    private val orderExchangeMap = ConcurrentHashMap<String, List<String>>() // clientOrderId -> exchangeIds
    
    // Rate limiting (aggregated across all exchanges)
    private val rateLimitedUntil = AtomicLong(0)
    
    // Paper trading mode flag
    private val paperTradingMode = AtomicBoolean(false)
    
    // Events
    private val _executorEvents = MutableSharedFlow<SmartExecutorEvent>(
        replay = 0,
        extraBufferCapacity = 100
    )
    val executorEvents: SharedFlow<SmartExecutorEvent> = _executorEvents.asSharedFlow()
    
    // Stats
    private val _stats = MutableStateFlow(SmartExecutorStats())
    val stats: StateFlow<SmartExecutorStats> = _stats.asStateFlow()
    
    // =========================================================================
    // CONFIGURATION
    // =========================================================================
    
    /**
     * Set paper trading mode
     */
    fun setPaperTradingMode(enabled: Boolean) {
        paperTradingMode.set(enabled)
    }
    
    /**
     * Check if in paper trading mode
     */
    fun isPaperTradingMode(): Boolean = paperTradingMode.get()
    
    // =========================================================================
    // EXCHANGE ADAPTER IMPLEMENTATION
    // =========================================================================
    
    /**
     * Place an order through the smart routing system
     * 
     * This is the primary entry point. The order is analyzed, routed to the
     * optimal exchange(s), and executed according to the configured strategy.
     */
    override suspend fun placeOrder(request: OrderRequest): OrderExecutionResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            // Paper trading bypass
            if (paperTradingMode.get()) {
                return executePaperOrder(request)
            }
            
            // Check if any exchanges are available
            val registeredExchanges = router.getRegisteredExchanges()
            if (registeredExchanges.isEmpty()) {
                return OrderExecutionResult.Rejected(
                    reason = "No exchanges registered with SmartOrderRouter",
                    code = "NO_EXCHANGES"
                )
            }
            
            // Determine routing strategy
            val strategy = config.defaultStrategy
            
            // Create execution plan
            val plan = try {
                router.createExecutionPlan(request, strategy)
            } catch (e: InstitutionalStrategyNotAvailableException) {
                // Handle institutional strategy fallback
                emitEvent(SmartExecutorEvent.StrategyFallback(
                    request = request,
                    requestedStrategy = e.strategy,
                    fallbackStrategy = e.fallbackStrategy,
                    reason = e.reason
                ))
                
                if (e.fallbackStrategy != null) {
                    router.createExecutionPlan(request, e.fallbackStrategy)
                } else {
                    return OrderExecutionResult.Rejected(
                        reason = e.reason,
                        code = "STRATEGY_UNAVAILABLE"
                    )
                }
            }
            
            // Execute the plan
            val report = router.executeOrder(request, strategy)
            
            // Record order info
            recordOrder(request, report)
            
            // Update stats
            updateStats(report, System.currentTimeMillis() - startTime)
            
            // Convert execution report to OrderExecutionResult
            convertReportToResult(report)
            
        } catch (e: Exception) {
            emitEvent(SmartExecutorEvent.Error("Order placement failed: ${e.message}", e))
            OrderExecutionResult.Error(e)
        }
    }
    
    /**
     * Cancel an order
     * 
     * Since orders may be split across exchanges, we need to cancel on all
     * exchanges where the order has legs.
     */
    override suspend fun cancelOrder(orderId: String, symbol: String): Boolean {
        // Check if this is a smart-routed order
        val exchangeIds = orderExchangeMap[orderId]
        
        if (exchangeIds.isNullOrEmpty()) {
            // Not a tracked order - may be legacy, try all exchanges
            return cancelOnAllExchanges(orderId, symbol)
        }
        
        // Cancel on all exchanges where the order was placed
        var allCancelled = true
        for (exchangeId in exchangeIds) {
            val cancelled = router.cancelOrderOnExchange(exchangeId, orderId, symbol)
            if (!cancelled) {
                allCancelled = false
            }
        }
        
        // Update order record
        orderRegistry[orderId]?.let { record ->
            orderRegistry[orderId] = record.copy(
                status = if (allCancelled) OrderStatus.CANCELLED else OrderStatus.PARTIALLY_FILLED
            )
        }
        
        return allCancelled
    }
    
    /**
     * Modify an order
     * 
     * For split orders, this is complex - we may need to modify multiple legs.
     * Current implementation: Cancel and replace.
     */
    override suspend fun modifyOrder(
        orderId: String,
        symbol: String,
        newPrice: Double?,
        newQuantity: Double?
    ): OrderExecutionResult {
        // Get the original order record
        val record = orderRegistry[orderId]
            ?: return OrderExecutionResult.Rejected(
                reason = "Order not found: $orderId",
                code = "ORDER_NOT_FOUND"
            )
        
        // Cancel the existing order
        val cancelled = cancelOrder(orderId, symbol)
        if (!cancelled) {
            return OrderExecutionResult.Rejected(
                reason = "Failed to cancel original order for modification",
                code = "CANCEL_FAILED"
            )
        }
        
        // Create new order with modified parameters
        val remainingQuantity = record.request.quantity - (record.filledQuantity ?: 0.0)
        val newRequest = record.request.copy(
            price = newPrice ?: record.request.price,
            quantity = newQuantity ?: remainingQuantity,
            clientOrderId = "SV-${System.currentTimeMillis()}-${(1000..9999).random()}" // New order ID
        )
        
        // Place the new order
        return placeOrder(newRequest)
    }
    
    /**
     * Get order status
     * 
     * Aggregates status from all exchanges where the order has legs.
     */
    override suspend fun getOrderStatus(orderId: String, symbol: String): ExecutedOrder? {
        // Check our local record first
        val record = orderRegistry[orderId]
        if (record != null) {
            return record.toExecutedOrder()
        }
        
        // Not in our registry - search all exchanges
        for (exchangeId in router.getRegisteredExchanges()) {
            val status = router.getOrderStatusOnExchange(exchangeId, orderId, symbol)
            if (status != null) {
                return status
            }
        }
        
        return null
    }
    
    /**
     * Get all open orders
     * 
     * Aggregates open orders from all connected exchanges.
     */
    override suspend fun getOpenOrders(symbol: String?): List<ExecutedOrder> {
        val allOpenOrders = mutableListOf<ExecutedOrder>()
        
        for (exchangeId in router.getRegisteredExchanges()) {
            val orders = router.getOpenOrdersOnExchange(exchangeId, symbol)
            allOpenOrders.addAll(orders)
        }
        
        return allOpenOrders
    }
    
    /**
     * Check if rate limited
     * 
     * Returns true if ANY connected exchange is rate limited.
     */
    override fun isRateLimited(): Boolean {
        return System.currentTimeMillis() < rateLimitedUntil.get()
    }
    
    // =========================================================================
    // PAPER TRADING
    // =========================================================================
    
    private var paperOrderCounter = 0
    
    /**
     * Execute a paper (simulated) order
     */
    private suspend fun executePaperOrder(request: OrderRequest): OrderExecutionResult {
        val orderId = "SMART-PAPER-${++paperOrderCounter}"
        val simulatedPrice = request.price ?: request.stopPrice ?: 0.0
        val simulatedFee = request.quantity * simulatedPrice * 0.001 // 0.1% fee
        
        val executedOrder = ExecutedOrder(
            orderId = orderId,
            clientOrderId = request.clientOrderId,
            symbol = request.symbol,
            side = request.side,
            type = request.type,
            price = simulatedPrice,
            executedPrice = simulatedPrice,
            quantity = request.quantity,
            executedQuantity = request.quantity,
            fee = simulatedFee,
            feeCurrency = "USD",
            status = OrderStatus.FILLED,
            exchange = "SmartRouter-Paper",
            board = request.metadata["board"]  // BUILD #448: Transfer board attribution from request
        )
        
        // Record the paper order
        orderRegistry[request.clientOrderId] = SmartOrderRecord(
            request = request,
            orderId = orderId,
            status = OrderStatus.FILLED,
            exchanges = listOf("paper"),
            filledQuantity = request.quantity,
            avgPrice = simulatedPrice,
            totalFees = simulatedFee
        )
        
        emitEvent(SmartExecutorEvent.PaperOrderExecuted(executedOrder))
        
        return OrderExecutionResult.Success(executedOrder)
    }
    
    // =========================================================================
    // HELPER METHODS
    // =========================================================================
    
    /**
     * Record an order and its execution details
     */
    private fun recordOrder(request: OrderRequest, report: ExecutionReport) {
        val exchangeIds = report.legResults.map { it.leg.exchangeId }.distinct()
        
        orderRegistry[request.clientOrderId] = SmartOrderRecord(
            request = request,
            orderId = report.legResults.firstOrNull()?.let { 
                (it.result as? OrderExecutionResult.Success)?.order?.orderId 
            } ?: request.clientOrderId,
            status = when (report.status) {
                ExecutionStatus.COMPLETE -> OrderStatus.FILLED
                ExecutionStatus.PARTIAL_FILL -> OrderStatus.PARTIALLY_FILLED
                ExecutionStatus.FAILED -> OrderStatus.REJECTED
                ExecutionStatus.CANCELLED -> OrderStatus.CANCELLED
            },
            exchanges = exchangeIds,
            filledQuantity = report.totalFilledQuantity,
            avgPrice = report.averageFilledPrice,
            totalFees = report.totalFeesPaid,
            priceImprovement = report.priceImprovement,
            executionTimeMs = report.totalExecutionTimeMs
        )
        
        orderExchangeMap[request.clientOrderId] = exchangeIds
    }
    
    /**
     * Convert ExecutionReport to OrderExecutionResult
     */
    private fun convertReportToResult(report: ExecutionReport): OrderExecutionResult {
        return when (report.status) {
            ExecutionStatus.COMPLETE -> {
                // Find the primary executed order
                val firstSuccess = report.legResults.firstOrNull { 
                    it.result is OrderExecutionResult.Success 
                }
                
                if (firstSuccess != null) {
                    val successResult = firstSuccess.result as OrderExecutionResult.Success
                    
                    // Create aggregated order for multi-leg execution
                    val aggregatedOrder = successResult.order.copy(
                        executedQuantity = report.totalFilledQuantity,
                        executedPrice = report.averageFilledPrice,
                        fee = report.totalFeesPaid,
                        exchange = "SmartRouter(${report.plan.legs.map { it.exchangeId }.joinToString(",")})"
                    )
                    
                    OrderExecutionResult.Success(aggregatedOrder)
                } else {
                    OrderExecutionResult.Rejected(
                        reason = "Execution completed but no successful legs",
                        code = "NO_SUCCESS_LEGS"
                    )
                }
            }
            
            ExecutionStatus.PARTIAL_FILL -> {
                val firstSuccess = report.legResults.firstOrNull { 
                    it.result is OrderExecutionResult.Success 
                }
                
                if (firstSuccess != null) {
                    val successResult = firstSuccess.result as OrderExecutionResult.Success
                    val partialOrder = successResult.order.copy(
                        executedQuantity = report.totalFilledQuantity,
                        executedPrice = report.averageFilledPrice,
                        fee = report.totalFeesPaid,
                        status = OrderStatus.PARTIALLY_FILLED,
                        exchange = "SmartRouter"
                    )
                    
                    val remaining = report.plan.totalQuantity - report.totalFilledQuantity
                    OrderExecutionResult.PartialFill(partialOrder, remaining)
                } else {
                    OrderExecutionResult.Rejected(
                        reason = "Partial fill reported but no successful legs",
                        code = "PARTIAL_NO_SUCCESS"
                    )
                }
            }
            
            ExecutionStatus.FAILED -> {
                val errors = report.errors.joinToString("; ")
                OrderExecutionResult.Rejected(
                    reason = "Execution failed: $errors",
                    code = "EXECUTION_FAILED"
                )
            }
            
            ExecutionStatus.CANCELLED -> {
                OrderExecutionResult.Rejected(
                    reason = "Order was cancelled",
                    code = "CANCELLED"
                )
            }
        }
    }
    
    /**
     * Cancel order on all exchanges (fallback for untracked orders)
     */
    private suspend fun cancelOnAllExchanges(orderId: String, symbol: String): Boolean {
        var cancelled = false
        
        for (exchangeId in router.getRegisteredExchanges()) {
            if (router.cancelOrderOnExchange(exchangeId, orderId, symbol)) {
                cancelled = true
            }
        }
        
        return cancelled
    }
    
    /**
     * Update statistics
     */
    private fun updateStats(report: ExecutionReport, latencyMs: Long) {
        _stats.update { current ->
            current.copy(
                totalOrdersExecuted = current.totalOrdersExecuted + 1,
                totalVolumeUsd = current.totalVolumeUsd + (report.averageFilledPrice * report.totalFilledQuantity),
                totalFeesPaid = current.totalFeesPaid + report.totalFeesPaid,
                totalPriceImprovement = current.totalPriceImprovement + report.priceImprovement,
                avgExecutionLatencyMs = (current.avgExecutionLatencyMs * current.totalOrdersExecuted + latencyMs) / 
                    (current.totalOrdersExecuted + 1),
                splitOrderCount = current.splitOrderCount + if (report.plan.isSplitOrder) 1 else 0,
                successRate = (current.successRate * current.totalOrdersExecuted + 
                    (if (report.status == ExecutionStatus.COMPLETE) 1.0 else 0.0)) / 
                    (current.totalOrdersExecuted + 1)
            )
        }
    }
    
    /**
     * Emit an event
     */
    private fun emitEvent(event: SmartExecutorEvent) {
        scope.launch {
            _executorEvents.emit(event)
        }
    }
    
    /**
     * Shutdown
     */
    fun shutdown() {
        scope.cancel()
    }
}

// =============================================================================
// CONFIGURATION
// =============================================================================

/**
 * Configuration for SmartOrderExecutor
 */
data class SmartOrderExecutorConfig(
    val defaultStrategy: RoutingStrategy = RoutingStrategy.BEST_EXECUTION,
    val enableStrategyFallback: Boolean = true,
    val maxOrderAgeMs: Long = 300_000,  // 5 minutes - cleanup old order records
    val trackOrderHistory: Boolean = true
)

// =============================================================================
// ORDER RECORD
// =============================================================================

/**
 * Internal record of a smart-routed order
 */
data class SmartOrderRecord(
    val request: OrderRequest,
    val orderId: String,
    val status: OrderStatus,
    val exchanges: List<String>,
    val filledQuantity: Double?,
    val avgPrice: Double?,
    val totalFees: Double?,
    val priceImprovement: Double? = null,
    val executionTimeMs: Long? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toExecutedOrder(): ExecutedOrder {
        return ExecutedOrder(
            orderId = orderId,
            clientOrderId = request.clientOrderId,
            symbol = request.symbol,
            side = request.side,
            type = request.type,
            price = request.price ?: 0.0,
            executedPrice = avgPrice ?: 0.0,
            quantity = request.quantity,
            executedQuantity = filledQuantity ?: 0.0,
            fee = totalFees ?: 0.0,
            feeCurrency = "USD",
            status = status,
            timestamp = timestamp,
            exchange = "SmartRouter(${exchanges.joinToString(",")})"
        )
    }
}

// =============================================================================
// EVENTS
// =============================================================================

/**
 * Events emitted by SmartOrderExecutor
 */
sealed class SmartExecutorEvent {
    data class OrderRouted(
        val request: OrderRequest,
        val strategy: RoutingStrategy,
        val exchanges: List<String>
    ) : SmartExecutorEvent()
    
    data class StrategyFallback(
        val request: OrderRequest,
        val requestedStrategy: RoutingStrategy,
        val fallbackStrategy: RoutingStrategy?,
        val reason: String
    ) : SmartExecutorEvent()
    
    data class PaperOrderExecuted(
        val order: ExecutedOrder
    ) : SmartExecutorEvent()
    
    data class Error(
        val message: String,
        val exception: Throwable? = null
    ) : SmartExecutorEvent()
}

// =============================================================================
// STATS
// =============================================================================

/**
 * Execution statistics
 */
data class SmartExecutorStats(
    val totalOrdersExecuted: Long = 0,
    val totalVolumeUsd: Double = 0.0,
    val totalFeesPaid: Double = 0.0,
    val totalPriceImprovement: Double = 0.0,
    val avgExecutionLatencyMs: Double = 0.0,
    val splitOrderCount: Long = 0,
    val successRate: Double = 1.0
)
