package com.miwealth.sovereignvantage.core.trading.routing

import com.miwealth.sovereignvantage.core.*
import com.miwealth.sovereignvantage.core.exchange.*
import com.miwealth.sovereignvantage.core.trading.engine.*
import com.miwealth.sovereignvantage.service.UnifiedPriceFeedService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

/**
 * SMART ORDER ROUTER (SOR)
 * 
 * Institutional-grade order routing engine that:
 * - Routes orders to the best exchange based on price, liquidity, and fees
 * - Splits large orders across multiple exchanges
 * - Minimizes slippage through intelligent execution
 * - Optimizes for best execution (price improvement + fee savings)
 * 
 * Routing Strategies:
 * - BEST_PRICE: Route to exchange with best bid/ask
 * - LOWEST_FEE: Route to exchange with lowest effective fee
 * - BEST_EXECUTION: Optimize for price + fees combined
 * - SPLIT_ORDER: Split order across exchanges for large orders
 * - TWAP: Time-Weighted Average Price execution
 * - VWAP: Volume-Weighted Average Price execution
 * 
 * Architecture:
 * ┌─────────────────────────────────────────────────────────────────┐
 * │                    SMART ORDER ROUTER                          │
 * ├─────────────────────────────────────────────────────────────────┤
 * │  OrderRequest ──► RouteAnalyzer ──► ExecutionPlan             │
 * │       │               │                  │                     │
 * │       ▼               ▼                  ▼                     │
 * │  FeeOptimizer   SlippageProtector   OrderSplitter             │
 * │       │               │                  │                     │
 * │       └───────────────┼──────────────────┘                     │
 * │                       ▼                                        │
 * │              RouteExecutor ──► Exchange Adapters               │
 * │                       │                                        │
 * │                       ▼                                        │
 * │              ExecutionReport (with price improvement)          │
 * └─────────────────────────────────────────────────────────────────┘
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

// =============================================================================
// ROUTING STRATEGY
// =============================================================================

enum class RoutingStrategy {
    // =========================================================================
    // STANDARD STRATEGIES (Lit Markets)
    // =========================================================================
    BEST_PRICE,       // Route to exchange with best price
    LOWEST_FEE,       // Route to exchange with lowest fees
    BEST_EXECUTION,   // Optimise price + fees combined
    SPLIT_ORDER,      // Split across exchanges for size
    TWAP,             // Time-Weighted Average Price
    VWAP,             // Volume-Weighted Average Price
    ICEBERG,          // Hide order size with small visible chunks
    SMART_AUTO,       // Auto-select best strategy based on order
    
    // =========================================================================
    // INSTITUTIONAL STRATEGIES (Dark Pools / Block Trading)
    // Future implementation - scaffolding for institutional clients
    // =========================================================================
    DARK_POOL,        // Route to dark liquidity venues (hidden order books)
    BLOCK_TRADE,      // Negotiate block execution for large orders
    RFQ,              // Request-for-Quote from liquidity providers
    SMART_DARK,       // Try dark pools first, fall back to lit markets
    PEGGED_MIDPOINT,  // Peg to midpoint of NBBO for price improvement
    CONDITIONAL       // Conditional orders (e.g., trigger on external event)
}

/**
 * Routing configuration
 */
data class RoutingConfig(
    // =========================================================================
    // STANDARD ROUTING CONFIG
    // =========================================================================
    val defaultStrategy: RoutingStrategy = RoutingStrategy.BEST_EXECUTION,
    val maxSlippagePercent: Double = 0.5,        // Max allowed slippage
    val splitThresholdUsd: Double = 50000.0,     // Split orders above this USD value
    val minExchangeLiquidityPercent: Double = 5.0, // Min % of order an exchange must handle
    val enableFeeOptimization: Boolean = true,
    val enableSlippageProtection: Boolean = true,
    val twapIntervalSeconds: Int = 60,           // TWAP execution interval
    val twapDurationMinutes: Int = 30,           // Total TWAP duration
    val vwapParticipationRate: Double = 0.1,     // 10% of market volume
    val icebergVisiblePercent: Double = 0.1,     // Show 10% of order
    val maxRetries: Int = 3,
    val retryDelayMs: Long = 1000,
    
    // =========================================================================
    // INSTITUTIONAL / DARK POOL CONFIG (Future Implementation)
    // =========================================================================
    val darkPoolConfig: DarkPoolConfig = DarkPoolConfig(),
    val blockTradeConfig: BlockTradeConfig = BlockTradeConfig(),
    val rfqConfig: RFQConfig = RFQConfig()
)

/**
 * Dark Pool routing configuration
 * 
 * Dark pools provide hidden liquidity for large orders to minimize market impact.
 * Common venues: Kraken Dark, Coinbase Prime, B2C2, Cumberland, Circle Trade
 * 
 * Requirements for live implementation:
 * - Institutional account verification (KYB)
 * - Minimum volume commitments
 * - API access agreements with dark pool operators
 */
data class DarkPoolConfig(
    val enabled: Boolean = false,                    // Master toggle
    val preferredVenues: List<String> = emptyList(), // e.g., ["kraken_dark", "coinbase_prime"]
    val minOrderSizeUsd: Double = 100_000.0,         // Minimum order for dark routing
    val maxMarketImpactPercent: Double = 0.1,        // Max acceptable market impact
    val fallbackToLitMarkets: Boolean = true,        // Fall back if dark pools unavailable
    val priorityMode: DarkPoolPriority = DarkPoolPriority.PRICE_IMPROVEMENT,
    val allowPartialDarkFill: Boolean = true,        // Accept partial fills from dark pools
    val maxWaitTimeMs: Long = 5000                   // Max time to wait for dark pool match
)

/**
 * Dark pool priority mode
 */
enum class DarkPoolPriority {
    PRICE_IMPROVEMENT,  // Prioritise venues offering best price improvement
    FILL_PROBABILITY,   // Prioritise venues with highest fill probability
    ANONYMITY,          // Prioritise venues with best information leakage protection
    SPEED               // Prioritise fastest execution
}

/**
 * Block trade configuration for large institutional orders
 * 
 * Block trades are negotiated directly with counterparties, typically for
 * orders too large for standard order book execution.
 * 
 * Typical minimums: $200K - $1M depending on asset and venue
 */
data class BlockTradeConfig(
    val enabled: Boolean = false,
    val minBlockSizeUsd: Double = 200_000.0,         // Minimum for block consideration
    val preferredCounterparties: List<String> = emptyList(), // Approved block trading partners
    val allowNegotiation: Boolean = true,            // Allow price negotiation
    val maxNegotiationRounds: Int = 3,
    val acceptableSpreadBps: Int = 50,               // Max spread in basis points
    val requireTwoSidedQuote: Boolean = true         // Require bid and ask from counterparty
)

/**
 * Request-for-Quote (RFQ) configuration
 * 
 * RFQ solicits quotes from multiple liquidity providers simultaneously,
 * useful for large orders or illiquid assets.
 */
data class RFQConfig(
    val enabled: Boolean = false,
    val minLiquidityProviders: Int = 3,              // Min LPs to query
    val maxLiquidityProviders: Int = 10,             // Max LPs to query
    val quoteValidityMs: Long = 3000,                // How long quotes are valid
    val allowPartialQuotes: Boolean = true,          // Accept quotes for partial size
    val preferredProviders: List<String> = emptyList(), // Priority LP list
    val requireFirmQuotes: Boolean = true            // Require executable quotes (not indicative)
)

/**
 * Venue type classification for routing decisions
 */
enum class VenueType {
    LIT_EXCHANGE,      // Public order book (Kraken, Binance, Coinbase)
    DARK_POOL,         // Hidden liquidity venue
    OTC_DESK,          // Over-the-counter / voice trading
    BLOCK_FACILITY,    // Block trading facility
    RFQ_PLATFORM,      // Request-for-quote platform
    DEX_AMM,           // Decentralised AMM (Uniswap, etc.)
    DEX_ORDERBOOK,     // Decentralised order book (dYdX, etc.)
    DEX_AGGREGATOR,    // DEX aggregator (1inch, Jupiter)
    INTERNAL_CROSS     // Internal order matching (future: MiWealth matching engine)
}

// =============================================================================
// ROUTE ANALYSIS
// =============================================================================

/**
 * Exchange route with pricing and cost analysis
 */
data class ExchangeRoute(
    val exchangeId: String,
    val adapter: RoutableExchangeAdapter,
    val price: Double,               // Best price for this side
    val availableLiquidity: Double,  // Available at this price level
    val fees: RouteFees,
    val latencyMs: Long,             // Recent latency to this exchange
    val score: Double,               // Overall routing score (higher = better)
    val venueType: VenueType = VenueType.LIT_EXCHANGE,  // Classification for routing logic
    val darkPoolMetadata: DarkPoolMetadata? = null       // Additional info for dark venues
) {
    val effectivePrice: Double
        get() = price + fees.totalFeeUsd  // Price including all fees
    
    val isDarkVenue: Boolean
        get() = venueType == VenueType.DARK_POOL || venueType == VenueType.BLOCK_FACILITY
    
    val isLitVenue: Boolean
        get() = venueType == VenueType.LIT_EXCHANGE || venueType == VenueType.DEX_ORDERBOOK
}

/**
 * Metadata for dark pool venues
 * 
 * Used when routing to hidden liquidity sources. Contains information
 * about the venue's characteristics that affect routing decisions.
 */
data class DarkPoolMetadata(
    val venueName: String,
    val estimatedFillProbability: Double,    // 0.0-1.0, historical fill rate
    val avgPriceImprovement: Double,         // Historical price improvement in bps
    val minOrderSizeUsd: Double,             // Venue minimum
    val maxOrderSizeUsd: Double?,            // Venue maximum (null = unlimited)
    val supportsPartialFill: Boolean,
    val avgMatchTimeMs: Long,                // Average time to find match
    val informationLeakageRisk: LeakageRisk, // How well venue protects order info
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Information leakage risk classification
 * 
 * Institutional clients care deeply about information leakage -
 * if a venue "leaks" order information, front-runners can trade ahead.
 */
enum class LeakageRisk {
    VERY_LOW,   // Excellent information protection
    LOW,        // Good protection, minimal risk
    MEDIUM,     // Some risk of information leakage
    HIGH,       // Significant leakage risk (avoid for large orders)
    UNKNOWN     // Insufficient data to assess
}

/**
 * Fee breakdown for a route
 */
data class RouteFees(
    val makerFeePercent: Double,
    val takerFeePercent: Double,
    val estimatedFeePercent: Double,  // Expected fee based on order type
    val totalFeeUsd: Double,          // Estimated total fee in USD
    val networkFeeUsd: Double = 0.0,  // For DEX: gas fees
    val withdrawalFeeUsd: Double = 0.0
) {
    val totalCostPercent: Double
        get() = estimatedFeePercent + (networkFeeUsd / 10000.0) * 100  // Normalize
}

/**
 * Result of route analysis
 */
data class RouteAnalysis(
    val symbol: String,
    val side: TradeSide,
    val quantity: Double,
    val notionalValueUsd: Double,
    val routes: List<ExchangeRoute>,
    val bestRoute: ExchangeRoute?,
    val recommendedStrategy: RoutingStrategy,
    val estimatedSlippage: Double,
    val timestamp: Long = System.currentTimeMillis()
) {
    val hasSufficientLiquidity: Boolean
        get() = routes.sumOf { it.availableLiquidity } >= quantity
    
    val bestPrice: Double?
        get() = bestRoute?.price
    
    val worstPrice: Double?
        get() = routes.maxByOrNull { 
            if (side == TradeSide.BUY || side == TradeSide.LONG) it.price else -it.price 
        }?.price
}

// =============================================================================
// EXECUTION PLAN
// =============================================================================

/**
 * Planned execution leg (portion of order to an exchange)
 */
data class ExecutionLeg(
    val exchangeId: String,
    val adapter: RoutableExchangeAdapter,
    val quantity: Double,
    val limitPrice: Double?,          // For limit orders
    val expectedPrice: Double,
    val expectedFeeUsd: Double,
    val priority: Int,                // Execution order (1 = first)
    val delayMs: Long = 0             // Delay before executing (for TWAP/staged)
)

/**
 * Complete execution plan for an order
 */
data class ExecutionPlan(
    val originalRequest: OrderRequest,
    val strategy: RoutingStrategy,
    val legs: List<ExecutionLeg>,
    val totalQuantity: Double,
    val expectedAveragePrice: Double,
    val expectedTotalFeeUsd: Double,
    val expectedSlippage: Double,
    val estimatedDurationMs: Long,
    val created: Long = System.currentTimeMillis()
) {
    val legCount: Int get() = legs.size
    val isSplitOrder: Boolean get() = legs.size > 1
    
    fun getExpectedCost(): Double {
        return (expectedAveragePrice * totalQuantity) + expectedTotalFeeUsd
    }
}

// =============================================================================
// EXECUTION RESULT
// =============================================================================

/**
 * Result of executing a single leg
 */
data class LegExecutionResult(
    val leg: ExecutionLeg,
    val result: OrderExecutionResult,
    val actualPrice: Double?,
    val actualQuantity: Double?,
    val actualFeeUsd: Double?,
    val slippage: Double?,           // Actual vs expected price
    val executionTimeMs: Long
)

/**
 * Complete execution report
 */
data class ExecutionReport(
    val plan: ExecutionPlan,
    val legResults: List<LegExecutionResult>,
    val totalFilledQuantity: Double,
    val averageFilledPrice: Double,
    val totalFeesPaid: Double,
    val priceImprovement: Double,    // Positive = saved money
    val actualSlippage: Double,
    val totalExecutionTimeMs: Long,
    val status: ExecutionStatus,
    val errors: List<String> = emptyList()
) {
    val wasFullyFilled: Boolean
        get() = totalFilledQuantity >= plan.totalQuantity * 0.9999
    
    val savingsUsd: Double
        get() = priceImprovement * totalFilledQuantity
}

enum class ExecutionStatus {
    COMPLETE,
    PARTIAL_FILL,
    FAILED,
    CANCELLED
}

// =============================================================================
// SMART ORDER ROUTER
// =============================================================================

class SmartOrderRouter(
    // V5.17.0: Made nullable — not currently used by any routing logic.
    // Kept for future price feed integration. AI path passes null.
    private val priceFeedService: UnifiedPriceFeedService? = null,
    private val feeOptimizer: FeeOptimizer,
    private val slippageProtector: SlippageProtector,
    private val orderSplitter: OrderSplitter,
    private val config: RoutingConfig = RoutingConfig(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    
    companion object {
        private const val TAG = "SmartOrderRouter"
        const val MIN_ORDER_VALUE_USD = 10.0
        const val DEFAULT_LATENCY_MS = 100L
    }
    
    // Connected exchange adapters (V5.17.0: widened from UnifiedExchangeAdapter to RoutableExchangeAdapter)
    private val exchangeAdapters = ConcurrentHashMap<String, RoutableExchangeAdapter>()
    
    // Exchange latency tracking
    private val exchangeLatency = ConcurrentHashMap<String, Long>()
    
    // Fee cache (refreshed periodically)
    private val feeCache = ConcurrentHashMap<String, ExchangeFeeSchedule>()
    
    // Events
    private val _routingEvents = MutableSharedFlow<RoutingEvent>(
        replay = 0,
        extraBufferCapacity = 100
    )
    val routingEvents: SharedFlow<RoutingEvent> = _routingEvents.asSharedFlow()
    
    // Stats
    private val _stats = MutableStateFlow(RoutingStats())
    val stats: StateFlow<RoutingStats> = _stats.asStateFlow()
    
    // =========================================================================
    // EXCHANGE MANAGEMENT
    // =========================================================================
    
    /**
     * Register an exchange adapter for routing.
     * V5.17.0: Accepts RoutableExchangeAdapter — works with both legacy PQC
     * adapters and AI Exchange Interface adapters.
     */
    fun registerExchange(adapter: RoutableExchangeAdapter) {
        val exchangeId = adapter.getExchangeId()
        exchangeAdapters[exchangeId] = adapter
        exchangeLatency[exchangeId] = DEFAULT_LATENCY_MS
        
        // Load fee schedule
        scope.launch {
            loadFeeSchedule(exchangeId, adapter)
        }
        
        emitEvent(RoutingEvent.ExchangeRegistered(exchangeId))
    }
    
    /**
     * Unregister an exchange
     */
    fun unregisterExchange(exchangeId: String) {
        exchangeAdapters.remove(exchangeId)
        exchangeLatency.remove(exchangeId)
        feeCache.remove(exchangeId)
        emitEvent(RoutingEvent.ExchangeUnregistered(exchangeId))
    }
    
    /**
     * Get registered exchanges
     */
    fun getRegisteredExchanges(): List<String> = exchangeAdapters.keys.toList()
    
    /**
     * Update exchange latency measurement
     */
    fun updateLatency(exchangeId: String, latencyMs: Long) {
        exchangeLatency[exchangeId] = latencyMs
    }
    
    // =========================================================================
    // ROUTE ANALYSIS
    // =========================================================================
    
    /**
     * Analyse routing options for an order
     */
    suspend fun analyzeRoutes(
        symbol: String,
        side: TradeSide,
        quantity: Double,
        orderType: OrderType = OrderType.MARKET
    ): RouteAnalysis {
        val routes = mutableListOf<ExchangeRoute>()
        
        // Get current prices from all exchanges
        for ((exchangeId, adapter) in exchangeAdapters) {
            if (!adapter.isConnected()) continue
            
            try {
                val ticker = adapter.getTicker(symbol) ?: continue
                val orderBook = adapter.getOrderBook(symbol, depth = 20)
                
                val price = when (side) {
                    TradeSide.BUY, TradeSide.LONG -> ticker.ask
                    TradeSide.SELL, TradeSide.SHORT -> ticker.bid
                    else -> ticker.last
                }
                
                // Calculate available liquidity at this price level
                val liquidity = calculateAvailableLiquidity(orderBook, side, price, quantity)
                
                // Get fees
                val fees = calculateFees(exchangeId, quantity, price, orderType)
                
                // Calculate route score
                val score = calculateRouteScore(price, liquidity, fees, exchangeLatency[exchangeId] ?: DEFAULT_LATENCY_MS, side)
                
                routes.add(ExchangeRoute(
                    exchangeId = exchangeId,
                    adapter = adapter,
                    price = price,
                    availableLiquidity = liquidity,
                    fees = fees,
                    latencyMs = exchangeLatency[exchangeId] ?: DEFAULT_LATENCY_MS,
                    score = score
                ))
            } catch (e: Exception) {
                // Skip this exchange if we can't get data
                continue
            }
        }
        
        // Sort by score (highest first)
        val sortedRoutes = routes.sortedByDescending { it.score }
        val bestRoute = sortedRoutes.firstOrNull()
        
        // Calculate notional value
        val notionalValue = (bestRoute?.price ?: 0.0) * quantity
        
        // Determine recommended strategy
        val recommendedStrategy = determineStrategy(sortedRoutes, quantity, notionalValue)
        
        // Estimate slippage
        val estimatedSlippage = slippageProtector.estimateSlippage(symbol, side, quantity, sortedRoutes).estimatedSlippagePercent
        
        return RouteAnalysis(
            symbol = symbol,
            side = side,
            quantity = quantity,
            notionalValueUsd = notionalValue,
            routes = sortedRoutes,
            bestRoute = bestRoute,
            recommendedStrategy = recommendedStrategy,
            estimatedSlippage = estimatedSlippage
        )
    }
    
    /**
     * Calculate available liquidity from order book
     */
    private fun calculateAvailableLiquidity(
        orderBook: OrderBook?,
        side: TradeSide,
        targetPrice: Double,
        orderQuantity: Double
    ): Double {
        if (orderBook == null) return 0.0
        
        val levels = when (side) {
            TradeSide.BUY, TradeSide.LONG -> orderBook.asks
            TradeSide.SELL, TradeSide.SHORT -> orderBook.bids
            else -> emptyList()
        }
        
        // Sum liquidity within acceptable price range (0.5% from target)
        val priceThreshold = targetPrice * 0.005
        return levels
            .filter { 
                when (side) {
                    TradeSide.BUY, TradeSide.LONG -> it.price <= targetPrice + priceThreshold
                    TradeSide.SELL, TradeSide.SHORT -> it.price >= targetPrice - priceThreshold
                    else -> false
                }
            }
            .sumOf { it.quantity }
    }
    
    /**
     * Calculate fees for a route
     */
    private fun calculateFees(
        exchangeId: String,
        quantity: Double,
        price: Double,
        orderType: OrderType
    ): RouteFees {
        val schedule = feeCache[exchangeId] ?: ExchangeFeeSchedule.default(exchangeId)
        
        val feePercent = when (orderType) {
            OrderType.LIMIT -> schedule.makerFee
            else -> schedule.takerFee
        }
        
        val notional = quantity * price
        val feeUsd = notional * feePercent
        
        return RouteFees(
            makerFeePercent = schedule.makerFee * 100,
            takerFeePercent = schedule.takerFee * 100,
            estimatedFeePercent = feePercent * 100,
            totalFeeUsd = feeUsd,
            networkFeeUsd = schedule.networkFee,
            withdrawalFeeUsd = schedule.withdrawalFee
        )
    }
    
    /**
     * Calculate route score (higher = better)
     */
    private fun calculateRouteScore(
        price: Double,
        liquidity: Double,
        fees: RouteFees,
        latencyMs: Long,
        side: TradeSide
    ): Double {
        // Normalize each factor to 0-100 scale
        
        // Price score (better price = higher score)
        // For buys: lower price is better; for sells: higher price is better
        val priceScore = 50.0  // Base score, adjusted by relative comparison
        
        // Liquidity score (more liquidity = higher score)
        val liquidityScore = minOf(liquidity * 10, 100.0)
        
        // Fee score (lower fees = higher score)
        val feeScore = maxOf(0.0, 100.0 - (fees.estimatedFeePercent * 100))
        
        // Latency score (lower latency = higher score)
        val latencyScore = maxOf(0.0, 100.0 - (latencyMs / 10.0))
        
        // Weighted combination
        return (priceScore * 0.4) + (liquidityScore * 0.25) + (feeScore * 0.25) + (latencyScore * 0.1)
    }
    
    /**
     * Determine best routing strategy based on order characteristics
     */
    private fun determineStrategy(
        routes: List<ExchangeRoute>,
        quantity: Double,
        notionalValueUsd: Double
    ): RoutingStrategy {
        // Large orders should be split
        if (notionalValueUsd > config.splitThresholdUsd) {
            return RoutingStrategy.SPLIT_ORDER
        }
        
        // If one exchange has overwhelming liquidity, use best price
        val totalLiquidity = routes.sumOf { it.availableLiquidity }
        val topExchangeLiquidity = routes.firstOrNull()?.availableLiquidity ?: 0.0
        if (topExchangeLiquidity > totalLiquidity * 0.8) {
            return RoutingStrategy.BEST_PRICE
        }
        
        // Default to best execution (optimizes price + fees)
        return config.defaultStrategy
    }
    
    // =========================================================================
    // EXECUTION PLANNING
    // =========================================================================
    
    /**
     * Create an execution plan for an order
     */
    suspend fun createExecutionPlan(
        request: OrderRequest,
        strategy: RoutingStrategy = config.defaultStrategy
    ): ExecutionPlan {
        val analysis = analyzeRoutes(request.symbol, request.side, request.quantity, request.type)
        
        val effectiveStrategy = if (strategy == RoutingStrategy.SMART_AUTO) {
            analysis.recommendedStrategy
        } else {
            strategy
        }
        
        return when (effectiveStrategy) {
            RoutingStrategy.BEST_PRICE -> createBestPricePlan(request, analysis)
            RoutingStrategy.LOWEST_FEE -> createLowestFeePlan(request, analysis)
            RoutingStrategy.BEST_EXECUTION -> createBestExecutionPlan(request, analysis)
            RoutingStrategy.SPLIT_ORDER -> createSplitOrderPlan(request, analysis)
            RoutingStrategy.TWAP -> createTWAPPlan(request, analysis)
            RoutingStrategy.VWAP -> createVWAPPlan(request, analysis)
            RoutingStrategy.ICEBERG -> createIcebergPlan(request, analysis)
            RoutingStrategy.SMART_AUTO -> createBestExecutionPlan(request, analysis)
            
            // Institutional strategies (scaffolding for future implementation)
            RoutingStrategy.DARK_POOL -> createDarkPoolPlan(request, analysis)
            RoutingStrategy.BLOCK_TRADE -> createBlockTradePlan(request, analysis)
            RoutingStrategy.RFQ -> createRFQPlan(request, analysis)
            RoutingStrategy.SMART_DARK -> createSmartDarkPlan(request, analysis)
            RoutingStrategy.PEGGED_MIDPOINT -> createPeggedMidpointPlan(request, analysis)
            RoutingStrategy.CONDITIONAL -> createConditionalPlan(request, analysis)
        }
    }
    
    // =========================================================================
    // INSTITUTIONAL STRATEGY STUBS (Future Implementation)
    // These methods provide clear integration points for dark pool routing
    // =========================================================================
    
    /**
     * Create dark pool execution plan
     * 
     * Dark pools provide hidden liquidity to minimize market impact for large orders.
     * Implementation will require:
     * - Integration with dark pool APIs (Kraken Dark, Coinbase Prime, etc.)
     * - Institutional account verification
     * - Real-time hidden liquidity assessment
     * 
     * @throws InstitutionalStrategyNotAvailableException when dark pools not configured
     */
    private suspend fun createDarkPoolPlan(
        request: OrderRequest,
        analysis: RouteAnalysis
    ): ExecutionPlan {
        if (!config.darkPoolConfig.enabled) {
            throw InstitutionalStrategyNotAvailableException(
                strategy = RoutingStrategy.DARK_POOL,
                reason = "Dark pool routing not enabled. Configure darkPoolConfig and connect institutional venues.",
                fallbackStrategy = if (config.darkPoolConfig.fallbackToLitMarkets) RoutingStrategy.BEST_EXECUTION else null
            )
        }
        
        // TODO: Implement dark pool routing
        // 1. Query configured dark pool venues for hidden liquidity
        // 2. Assess fill probability and price improvement potential
        // 3. Route to best dark pool or split across multiple
        // 4. Handle partial fills with lit market fallback
        
        throw InstitutionalStrategyNotAvailableException(
            strategy = RoutingStrategy.DARK_POOL,
            reason = "Dark pool venues not connected. Implementation pending institutional partnerships.",
            fallbackStrategy = RoutingStrategy.BEST_EXECUTION
        )
    }
    
    /**
     * Create block trade execution plan
     * 
     * Block trades are negotiated directly with counterparties for very large orders.
     * Typical minimum: $200K - $1M depending on asset.
     */
    private suspend fun createBlockTradePlan(
        request: OrderRequest,
        analysis: RouteAnalysis
    ): ExecutionPlan {
        if (!config.blockTradeConfig.enabled) {
            throw InstitutionalStrategyNotAvailableException(
                strategy = RoutingStrategy.BLOCK_TRADE,
                reason = "Block trading not enabled. Configure blockTradeConfig with approved counterparties.",
                fallbackStrategy = RoutingStrategy.SPLIT_ORDER
            )
        }
        
        val notionalValue = analysis.notionalValueUsd
        if (notionalValue < config.blockTradeConfig.minBlockSizeUsd) {
            throw InstitutionalStrategyNotAvailableException(
                strategy = RoutingStrategy.BLOCK_TRADE,
                reason = "Order size \$${notionalValue.toLong()} below block minimum \$${config.blockTradeConfig.minBlockSizeUsd.toLong()}",
                fallbackStrategy = RoutingStrategy.SPLIT_ORDER
            )
        }
        
        // TODO: Implement block trade negotiation
        // 1. Contact configured counterparties for block quotes
        // 2. Compare quotes and negotiate if enabled
        // 3. Execute with best counterparty
        
        throw InstitutionalStrategyNotAvailableException(
            strategy = RoutingStrategy.BLOCK_TRADE,
            reason = "Block trade counterparties not connected. Implementation pending.",
            fallbackStrategy = RoutingStrategy.SPLIT_ORDER
        )
    }
    
    /**
     * Create RFQ (Request-for-Quote) execution plan
     * 
     * RFQ solicits quotes from multiple liquidity providers simultaneously.
     */
    private suspend fun createRFQPlan(
        request: OrderRequest,
        analysis: RouteAnalysis
    ): ExecutionPlan {
        if (!config.rfqConfig.enabled) {
            throw InstitutionalStrategyNotAvailableException(
                strategy = RoutingStrategy.RFQ,
                reason = "RFQ not enabled. Configure rfqConfig with liquidity providers.",
                fallbackStrategy = RoutingStrategy.BEST_EXECUTION
            )
        }
        
        // TODO: Implement RFQ flow
        // 1. Broadcast quote request to configured LPs
        // 2. Collect and validate quotes within validity window
        // 3. Select best quote(s) and execute
        
        throw InstitutionalStrategyNotAvailableException(
            strategy = RoutingStrategy.RFQ,
            reason = "RFQ liquidity providers not connected. Implementation pending.",
            fallbackStrategy = RoutingStrategy.BEST_EXECUTION
        )
    }
    
    /**
     * Create smart dark execution plan
     * 
     * Attempts dark pool execution first, falls back to lit markets if unavailable.
     * Best for large orders where minimizing market impact is priority.
     */
    private suspend fun createSmartDarkPlan(
        request: OrderRequest,
        analysis: RouteAnalysis
    ): ExecutionPlan {
        // Try dark pool first
        return try {
            createDarkPoolPlan(request, analysis)
        } catch (e: InstitutionalStrategyNotAvailableException) {
            // Fall back to best lit market execution
            if (e.fallbackStrategy != null) {
                emitEvent(RoutingEvent.DarkPoolFallback(
                    request = request,
                    reason = e.reason,
                    fallbackStrategy = e.fallbackStrategy
                ))
                createBestExecutionPlan(request, analysis)
            } else {
                throw e
            }
        }
    }
    
    /**
     * Create pegged midpoint execution plan
     * 
     * Pegs order to midpoint of NBBO (National Best Bid/Offer) for price improvement.
     * Common in equities, increasingly available in crypto.
     */
    private suspend fun createPeggedMidpointPlan(
        request: OrderRequest,
        analysis: RouteAnalysis
    ): ExecutionPlan {
        // TODO: Implement midpoint pegging
        // 1. Calculate midpoint from best bid/ask across venues
        // 2. Place pegged order that tracks midpoint
        // 3. Handle repricing as market moves
        
        throw InstitutionalStrategyNotAvailableException(
            strategy = RoutingStrategy.PEGGED_MIDPOINT,
            reason = "Pegged midpoint orders not yet implemented.",
            fallbackStrategy = RoutingStrategy.BEST_EXECUTION
        )
    }
    
    /**
     * Create conditional execution plan
     * 
     * Executes based on external conditions (e.g., price triggers, time, events).
     */
    private suspend fun createConditionalPlan(
        request: OrderRequest,
        analysis: RouteAnalysis
    ): ExecutionPlan {
        // TODO: Implement conditional orders
        // 1. Validate condition parameters
        // 2. Set up condition monitoring
        // 3. Execute when conditions met
        
        throw InstitutionalStrategyNotAvailableException(
            strategy = RoutingStrategy.CONDITIONAL,
            reason = "Conditional orders not yet implemented.",
            fallbackStrategy = RoutingStrategy.BEST_EXECUTION
        )
    }
    
    /**
     * Create plan routing to best price
     */
    private fun createBestPricePlan(request: OrderRequest, analysis: RouteAnalysis): ExecutionPlan {
        val bestRoute = analysis.bestRoute ?: throw IllegalStateException("No routes available")
        
        val leg = ExecutionLeg(
            exchangeId = bestRoute.exchangeId,
            adapter = bestRoute.adapter,
            quantity = request.quantity,
            limitPrice = request.price,
            expectedPrice = bestRoute.price,
            expectedFeeUsd = bestRoute.fees.totalFeeUsd,
            priority = 1
        )
        
        return ExecutionPlan(
            originalRequest = request,
            strategy = RoutingStrategy.BEST_PRICE,
            legs = listOf(leg),
            totalQuantity = request.quantity,
            expectedAveragePrice = bestRoute.price,
            expectedTotalFeeUsd = bestRoute.fees.totalFeeUsd,
            expectedSlippage = analysis.estimatedSlippage,
            estimatedDurationMs = bestRoute.latencyMs * 2
        )
    }
    
    /**
     * Create plan routing to lowest fee exchange
     */
    private fun createLowestFeePlan(request: OrderRequest, analysis: RouteAnalysis): ExecutionPlan {
        val lowestFeeRoute = analysis.routes
            .filter { it.availableLiquidity >= request.quantity }
            .minByOrNull { it.fees.totalCostPercent }
            ?: analysis.bestRoute
            ?: throw IllegalStateException("No routes available")
        
        val leg = ExecutionLeg(
            exchangeId = lowestFeeRoute.exchangeId,
            adapter = lowestFeeRoute.adapter,
            quantity = request.quantity,
            limitPrice = request.price,
            expectedPrice = lowestFeeRoute.price,
            expectedFeeUsd = lowestFeeRoute.fees.totalFeeUsd,
            priority = 1
        )
        
        return ExecutionPlan(
            originalRequest = request,
            strategy = RoutingStrategy.LOWEST_FEE,
            legs = listOf(leg),
            totalQuantity = request.quantity,
            expectedAveragePrice = lowestFeeRoute.price,
            expectedTotalFeeUsd = lowestFeeRoute.fees.totalFeeUsd,
            expectedSlippage = analysis.estimatedSlippage,
            estimatedDurationMs = lowestFeeRoute.latencyMs * 2
        )
    }
    
    /**
     * Create plan optimizing for best execution (price + fees)
     */
    private fun createBestExecutionPlan(request: OrderRequest, analysis: RouteAnalysis): ExecutionPlan {
        // Score routes by effective price (price + fees)
        val scoredRoutes = analysis.routes
            .filter { it.availableLiquidity >= request.quantity * config.minExchangeLiquidityPercent / 100 }
            .sortedBy { it.effectivePrice }
        
        if (scoredRoutes.isEmpty()) {
            // Fallback to best price
            return createBestPricePlan(request, analysis)
        }
        
        val bestEffectiveRoute = when (request.side) {
            TradeSide.BUY, TradeSide.LONG -> scoredRoutes.first()  // Lowest effective price
            TradeSide.SELL, TradeSide.SHORT -> scoredRoutes.last()  // Highest effective price
            else -> scoredRoutes.first()
        }
        
        val leg = ExecutionLeg(
            exchangeId = bestEffectiveRoute.exchangeId,
            adapter = bestEffectiveRoute.adapter,
            quantity = request.quantity,
            limitPrice = request.price,
            expectedPrice = bestEffectiveRoute.price,
            expectedFeeUsd = bestEffectiveRoute.fees.totalFeeUsd,
            priority = 1
        )
        
        return ExecutionPlan(
            originalRequest = request,
            strategy = RoutingStrategy.BEST_EXECUTION,
            legs = listOf(leg),
            totalQuantity = request.quantity,
            expectedAveragePrice = bestEffectiveRoute.price,
            expectedTotalFeeUsd = bestEffectiveRoute.fees.totalFeeUsd,
            expectedSlippage = analysis.estimatedSlippage,
            estimatedDurationMs = bestEffectiveRoute.latencyMs * 2
        )
    }
    
    /**
     * Create plan splitting order across exchanges
     */
    private fun createSplitOrderPlan(request: OrderRequest, analysis: RouteAnalysis): ExecutionPlan {
        val splits = orderSplitter.calculateSplits(
            totalQuantity = request.quantity,
            routes = analysis.routes,
            side = request.side,
            maxSlippagePercent = config.maxSlippagePercent
        )
        
        val legs = splits.mapIndexed { index, split ->
            ExecutionLeg(
                exchangeId = split.exchangeId,
                adapter = exchangeAdapters[split.exchangeId]!!,
                quantity = split.quantity,
                limitPrice = split.limitPrice,
                expectedPrice = split.expectedPrice,
                expectedFeeUsd = split.expectedFeeUsd,
                priority = index + 1,
                delayMs = split.delayMs
            )
        }
        
        val totalFees = legs.sumOf { it.expectedFeeUsd }
        val avgPrice = legs.sumOf { it.expectedPrice * it.quantity } / request.quantity
        val maxDelay = legs.maxOfOrNull { it.delayMs } ?: 0L
        val maxLatency = legs.maxOfOrNull { exchangeLatency[it.exchangeId] ?: DEFAULT_LATENCY_MS } ?: DEFAULT_LATENCY_MS
        
        return ExecutionPlan(
            originalRequest = request,
            strategy = RoutingStrategy.SPLIT_ORDER,
            legs = legs,
            totalQuantity = request.quantity,
            expectedAveragePrice = avgPrice,
            expectedTotalFeeUsd = totalFees,
            expectedSlippage = analysis.estimatedSlippage,
            estimatedDurationMs = maxDelay + (maxLatency * 2)
        )
    }
    
    /**
     * Create TWAP execution plan
     */
    private fun createTWAPPlan(request: OrderRequest, analysis: RouteAnalysis): ExecutionPlan {
        val bestRoute = analysis.bestRoute ?: throw IllegalStateException("No routes available")
        
        val intervalMs = config.twapIntervalSeconds * 1000L
        val totalDurationMs = config.twapDurationMinutes * 60 * 1000L
        val numSlices = (totalDurationMs / intervalMs).toInt()
        val sliceQuantity = request.quantity / numSlices
        
        val legs = (0 until numSlices).map { i ->
            ExecutionLeg(
                exchangeId = bestRoute.exchangeId,
                adapter = bestRoute.adapter,
                quantity = sliceQuantity,
                limitPrice = null,  // Market orders for TWAP
                expectedPrice = bestRoute.price,
                expectedFeeUsd = bestRoute.fees.totalFeeUsd / numSlices,
                priority = i + 1,
                delayMs = i * intervalMs
            )
        }
        
        return ExecutionPlan(
            originalRequest = request,
            strategy = RoutingStrategy.TWAP,
            legs = legs,
            totalQuantity = request.quantity,
            expectedAveragePrice = bestRoute.price,
            expectedTotalFeeUsd = bestRoute.fees.totalFeeUsd,
            expectedSlippage = analysis.estimatedSlippage * 0.5,  // TWAP reduces slippage
            estimatedDurationMs = totalDurationMs
        )
    }
    
    /**
     * Create VWAP execution plan
     */
    private fun createVWAPPlan(request: OrderRequest, analysis: RouteAnalysis): ExecutionPlan {
        // VWAP execution matches historical volume patterns
        // Simplified: distribute order based on typical hourly volume
        val bestRoute = analysis.bestRoute ?: throw IllegalStateException("No routes available")
        
        // Create 10 slices over 30 minutes, weighted by typical volume curve
        val volumeWeights = listOf(0.15, 0.12, 0.10, 0.08, 0.08, 0.08, 0.09, 0.10, 0.10, 0.10)
        val intervalMs = 3 * 60 * 1000L  // 3-minute intervals
        
        val legs = volumeWeights.mapIndexed { i, weight ->
            ExecutionLeg(
                exchangeId = bestRoute.exchangeId,
                adapter = bestRoute.adapter,
                quantity = request.quantity * weight,
                limitPrice = null,
                expectedPrice = bestRoute.price,
                expectedFeeUsd = bestRoute.fees.totalFeeUsd * weight,
                priority = i + 1,
                delayMs = i * intervalMs
            )
        }
        
        return ExecutionPlan(
            originalRequest = request,
            strategy = RoutingStrategy.VWAP,
            legs = legs,
            totalQuantity = request.quantity,
            expectedAveragePrice = bestRoute.price,
            expectedTotalFeeUsd = bestRoute.fees.totalFeeUsd,
            expectedSlippage = analysis.estimatedSlippage * 0.4,  // VWAP reduces slippage more
            estimatedDurationMs = 30 * 60 * 1000L
        )
    }
    
    /**
     * Create iceberg order plan
     */
    private fun createIcebergPlan(request: OrderRequest, analysis: RouteAnalysis): ExecutionPlan {
        val bestRoute = analysis.bestRoute ?: throw IllegalStateException("No routes available")
        
        val visibleQuantity = request.quantity * config.icebergVisiblePercent
        val numChunks = (1 / config.icebergVisiblePercent).toInt()
        
        val legs = (0 until numChunks).map { i ->
            ExecutionLeg(
                exchangeId = bestRoute.exchangeId,
                adapter = bestRoute.adapter,
                quantity = visibleQuantity,
                limitPrice = request.price,
                expectedPrice = bestRoute.price,
                expectedFeeUsd = bestRoute.fees.totalFeeUsd / numChunks,
                priority = i + 1,
                delayMs = 0  // Execute as previous chunks fill
            )
        }
        
        return ExecutionPlan(
            originalRequest = request,
            strategy = RoutingStrategy.ICEBERG,
            legs = legs,
            totalQuantity = request.quantity,
            expectedAveragePrice = bestRoute.price,
            expectedTotalFeeUsd = bestRoute.fees.totalFeeUsd,
            expectedSlippage = analysis.estimatedSlippage,
            estimatedDurationMs = numChunks * bestRoute.latencyMs * 5  // Estimate
        )
    }
    
    // =========================================================================
    // EXECUTION
    // =========================================================================
    
    /**
     * Execute an order with smart routing
     */
    suspend fun executeOrder(
        request: OrderRequest,
        strategy: RoutingStrategy = config.defaultStrategy
    ): ExecutionReport {
        val startTime = System.currentTimeMillis()
        
        // Pre-execution slippage check
        if (config.enableSlippageProtection) {
            val slippageCheck = slippageProtector.checkPreExecution(request.symbol, request.side, request.quantity)
            if (!slippageCheck.isAcceptable) {
                emitEvent(RoutingEvent.SlippageRejection(request, slippageCheck.estimatedSlippage))
                return ExecutionReport(
                    plan = ExecutionPlan(
                        originalRequest = request,
                        strategy = strategy,
                        legs = emptyList(),
                        totalQuantity = request.quantity,
                        expectedAveragePrice = 0.0,
                        expectedTotalFeeUsd = 0.0,
                        expectedSlippage = slippageCheck.estimatedSlippage,
                        estimatedDurationMs = 0
                    ),
                    legResults = emptyList(),
                    totalFilledQuantity = 0.0,
                    averageFilledPrice = 0.0,
                    totalFeesPaid = 0.0,
                    priceImprovement = 0.0,
                    actualSlippage = 0.0,
                    totalExecutionTimeMs = System.currentTimeMillis() - startTime,
                    status = ExecutionStatus.CANCELLED,
                    errors = listOf("Slippage exceeds threshold: ${slippageCheck.estimatedSlippage}%")
                )
            }
        }
        
        // Create execution plan
        val plan = createExecutionPlan(request, strategy)
        emitEvent(RoutingEvent.PlanCreated(plan))
        
        // Execute legs
        val legResults = mutableListOf<LegExecutionResult>()
        var totalFilledQty = 0.0
        var totalFeesPaid = 0.0
        var weightedPriceSum = 0.0
        val errors = mutableListOf<String>()
        
        for (leg in plan.legs.sortedBy { it.priority }) {
            // Apply delay if specified
            if (leg.delayMs > 0) {
                delay(leg.delayMs)
            }
            
            val legStartTime = System.currentTimeMillis()
            
            // Create order request for this leg
            val legRequest = request.copy(
                quantity = leg.quantity,
                price = leg.limitPrice ?: request.price
            )
            
            // Execute on the target exchange
            var result: OrderExecutionResult = OrderExecutionResult.Error(Exception("Not executed"))
            var retries = 0
            
            while (retries < config.maxRetries) {
                try {
                    result = leg.adapter.placeOrder(legRequest)
                    if (result is OrderExecutionResult.Success || result is OrderExecutionResult.PartialFill) {
                        break
                    }
                } catch (e: Exception) {
                    result = OrderExecutionResult.Error(e)
                }
                retries++
                if (retries < config.maxRetries) {
                    delay(config.retryDelayMs * retries)
                }
            }
            
            val legEndTime = System.currentTimeMillis()
            
            // Process result
            val (actualPrice, actualQty, actualFee) = when (result) {
                is OrderExecutionResult.Success -> Triple(
                    result.order.executedPrice,
                    result.order.executedQuantity,
                    result.order.fee
                )
                is OrderExecutionResult.PartialFill -> Triple(
                    result.order.executedPrice,
                    result.order.executedQuantity,
                    result.order.fee
                )
                else -> Triple(null, null, null)
            }
            
            val slippage = if (actualPrice != null) {
                ((actualPrice - leg.expectedPrice) / leg.expectedPrice) * 100
            } else null
            
            legResults.add(LegExecutionResult(
                leg = leg,
                result = result,
                actualPrice = actualPrice,
                actualQuantity = actualQty,
                actualFeeUsd = actualFee,
                slippage = slippage,
                executionTimeMs = legEndTime - legStartTime
            ))
            
            // Update totals
            if (actualQty != null && actualPrice != null) {
                totalFilledQty += actualQty
                totalFeesPaid += actualFee ?: 0.0
                weightedPriceSum += actualPrice * actualQty
            }
            
            // Check for errors
            when (result) {
                is OrderExecutionResult.Rejected -> errors.add("Leg ${leg.priority} rejected: ${result.reason}")
                is OrderExecutionResult.Error -> errors.add("Leg ${leg.priority} error: ${result.exception.message}")
                else -> {}
            }
            
            emitEvent(RoutingEvent.LegExecuted(leg, result))
        }
        
        val endTime = System.currentTimeMillis()
        
        // Calculate final metrics
        val avgFilledPrice = if (totalFilledQty > 0) weightedPriceSum / totalFilledQty else 0.0
        val priceImprovement = when (request.side) {
            TradeSide.BUY, TradeSide.LONG -> plan.expectedAveragePrice - avgFilledPrice
            TradeSide.SELL, TradeSide.SHORT -> avgFilledPrice - plan.expectedAveragePrice
            else -> 0.0
        }
        val actualSlippage = if (plan.expectedAveragePrice > 0) {
            ((avgFilledPrice - plan.expectedAveragePrice) / plan.expectedAveragePrice) * 100
        } else 0.0
        
        val status = when {
            totalFilledQty >= plan.totalQuantity * 0.9999 -> ExecutionStatus.COMPLETE
            totalFilledQty > 0 -> ExecutionStatus.PARTIAL_FILL
            errors.isNotEmpty() -> ExecutionStatus.FAILED
            else -> ExecutionStatus.CANCELLED
        }
        
        // Update stats
        updateStats(plan, totalFilledQty, totalFeesPaid, priceImprovement)
        
        val report = ExecutionReport(
            plan = plan,
            legResults = legResults,
            totalFilledQuantity = totalFilledQty,
            averageFilledPrice = avgFilledPrice,
            totalFeesPaid = totalFeesPaid,
            priceImprovement = priceImprovement,
            actualSlippage = actualSlippage,
            totalExecutionTimeMs = endTime - startTime,
            status = status,
            errors = errors
        )
        
        emitEvent(RoutingEvent.ExecutionComplete(report))
        
        return report
    }
    
    // =========================================================================
    // HELPERS
    // =========================================================================
    
    private suspend fun loadFeeSchedule(exchangeId: String, adapter: RoutableExchangeAdapter) {
        try {
            // In production, would fetch from exchange API
            // Using defaults for now
            feeCache[exchangeId] = feeOptimizer.getFeeSchedule(exchangeId)
        } catch (e: Exception) {
            feeCache[exchangeId] = ExchangeFeeSchedule.default(exchangeId)
        }
    }
    
    private fun updateStats(plan: ExecutionPlan, filledQty: Double, feesPaid: Double, priceImprovement: Double) {
        _stats.update { current ->
            current.copy(
                totalOrdersRouted = current.totalOrdersRouted + 1,
                totalVolumeRouted = current.totalVolumeRouted + filledQty,
                totalFeesOptimized = current.totalFeesOptimized + (plan.expectedTotalFeeUsd - feesPaid),
                totalPriceImprovement = current.totalPriceImprovement + priceImprovement,
                avgSlippage = (current.avgSlippage * current.totalOrdersRouted + plan.expectedSlippage) / (current.totalOrdersRouted + 1)
            )
        }
    }
    
    private fun emitEvent(event: RoutingEvent) {
        scope.launch {
            _routingEvents.emit(event)
        }
    }
    
    // =========================================================================
    // EXCHANGE ACCESS METHODS (for SmartOrderExecutor integration)
    // =========================================================================
    
    /**
     * Cancel an order on a specific exchange
     */
    suspend fun cancelOrderOnExchange(
        exchangeId: String,
        orderId: String,
        symbol: String
    ): Boolean {
        val adapter = exchangeAdapters[exchangeId] ?: return false
        return try {
            adapter.cancelOrder(orderId, symbol)
        } catch (e: Exception) {
            emitEvent(RoutingEvent.Error("Failed to cancel order $orderId on $exchangeId: ${e.message}", e))
            false
        }
    }
    
    /**
     * Get order status on a specific exchange
     */
    suspend fun getOrderStatusOnExchange(
        exchangeId: String,
        orderId: String,
        symbol: String
    ): ExecutedOrder? {
        val adapter = exchangeAdapters[exchangeId] ?: return null
        return try {
            adapter.getOrderStatus(orderId, symbol)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get open orders on a specific exchange
     */
    suspend fun getOpenOrdersOnExchange(
        exchangeId: String,
        symbol: String? = null
    ): List<ExecutedOrder> {
        val adapter = exchangeAdapters[exchangeId] ?: return emptyList()
        return try {
            adapter.getOpenOrders(symbol)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get an exchange adapter by ID.
     * V5.17.0: Returns RoutableExchangeAdapter (was UnifiedExchangeAdapter).
     */
    fun getExchangeAdapter(exchangeId: String): RoutableExchangeAdapter? {
        return exchangeAdapters[exchangeId]
    }
    
    /**
     * Check if any exchange is connected and available
     */
    fun hasConnectedExchanges(): Boolean {
        return exchangeAdapters.values.any { it.isConnected() }
    }
    
    /**
     * Shutdown the router
     */
    fun shutdown() {
        scope.cancel()
        exchangeAdapters.clear()
        exchangeLatency.clear()
        feeCache.clear()
    }
}

// =============================================================================
// EVENTS
// =============================================================================

sealed class RoutingEvent {
    data class ExchangeRegistered(val exchangeId: String) : RoutingEvent()
    data class ExchangeUnregistered(val exchangeId: String) : RoutingEvent()
    data class PlanCreated(val plan: ExecutionPlan) : RoutingEvent()
    data class LegExecuted(val leg: ExecutionLeg, val result: OrderExecutionResult) : RoutingEvent()
    data class ExecutionComplete(val report: ExecutionReport) : RoutingEvent()
    data class SlippageRejection(val request: OrderRequest, val slippage: Double) : RoutingEvent()
    data class Error(val message: String, val exception: Throwable? = null) : RoutingEvent()
    
    // Institutional / Dark Pool Events
    data class DarkPoolFallback(
        val request: OrderRequest,
        val reason: String,
        val fallbackStrategy: RoutingStrategy
    ) : RoutingEvent()
    
    data class DarkPoolMatch(
        val request: OrderRequest,
        val venue: String,
        val matchedQuantity: Double,
        val priceImprovement: Double
    ) : RoutingEvent()
    
    data class BlockTradeNegotiation(
        val request: OrderRequest,
        val counterparty: String,
        val status: BlockTradeStatus,
        val quotedPrice: Double?
    ) : RoutingEvent()
    
    data class RFQQuoteReceived(
        val request: OrderRequest,
        val provider: String,
        val bidPrice: Double?,
        val askPrice: Double?,
        val validityMs: Long
    ) : RoutingEvent()
    
    data class InstitutionalStrategyUnavailable(
        val request: OrderRequest,
        val strategy: RoutingStrategy,
        val reason: String
    ) : RoutingEvent()
}

/**
 * Block trade negotiation status
 */
enum class BlockTradeStatus {
    QUOTE_REQUESTED,
    QUOTE_RECEIVED,
    NEGOTIATING,
    AGREED,
    REJECTED,
    EXPIRED,
    EXECUTED
}

// =============================================================================
// EXCEPTIONS
// =============================================================================

/**
 * Exception thrown when an institutional strategy is not available
 * 
 * This provides clear feedback on why a strategy failed and what fallback
 * is available. Calling code can catch this and either use the fallback
 * or surface the reason to the user.
 */
class InstitutionalStrategyNotAvailableException(
    val strategy: RoutingStrategy,
    val reason: String,
    val fallbackStrategy: RoutingStrategy? = null
) : Exception("Strategy ${strategy.name} not available: $reason" +
    (fallbackStrategy?.let { " (fallback: ${it.name})" } ?: ""))

// =============================================================================
// STATS
// =============================================================================

data class RoutingStats(
    val totalOrdersRouted: Long = 0,
    val totalVolumeRouted: Double = 0.0,
    val totalFeesOptimized: Double = 0.0,
    val totalPriceImprovement: Double = 0.0,
    val avgSlippage: Double = 0.0
)

// =============================================================================
// ORDER BOOK
// =============================================================================

// V5.17.0: OrderBook and OrderBookLevel consolidated into data.models.OrderBookModels.kt
// Re-exported here for backward compatibility — no import aliasing needed
typealias OrderBook = com.miwealth.sovereignvantage.data.models.OrderBook
typealias OrderBookLevel = com.miwealth.sovereignvantage.data.models.OrderBookLevel

// =============================================================================
// FEE SCHEDULE
// =============================================================================

data class ExchangeFeeSchedule(
    val exchangeId: String,
    val makerFee: Double,      // As decimal (0.001 = 0.1%)
    val takerFee: Double,
    val networkFee: Double = 0.0,
    val withdrawalFee: Double = 0.0,
    val volumeTiers: List<FeeTier> = emptyList()
) {
    companion object {
        fun default(exchangeId: String): ExchangeFeeSchedule {
            return when (exchangeId.lowercase()) {
                "kraken" -> ExchangeFeeSchedule(exchangeId, 0.0016, 0.0026)
                "binance" -> ExchangeFeeSchedule(exchangeId, 0.001, 0.001)
                "coinbase" -> ExchangeFeeSchedule(exchangeId, 0.004, 0.006)
                "bybit" -> ExchangeFeeSchedule(exchangeId, 0.001, 0.001)
                "okx" -> ExchangeFeeSchedule(exchangeId, 0.0008, 0.001)
                "kucoin" -> ExchangeFeeSchedule(exchangeId, 0.001, 0.001)
                else -> ExchangeFeeSchedule(exchangeId, 0.002, 0.002)
            }
        }
    }
}

data class FeeTier(
    val minVolume30d: Double,
    val makerFee: Double,
    val takerFee: Double
)
