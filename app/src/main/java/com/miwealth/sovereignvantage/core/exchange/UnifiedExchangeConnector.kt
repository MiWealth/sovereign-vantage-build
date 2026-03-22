package com.miwealth.sovereignvantage.core.exchange

import com.miwealth.sovereignvantage.core.*
import com.miwealth.sovereignvantage.core.trading.engine.OrderRequest
import com.miwealth.sovereignvantage.core.trading.engine.OrderExecutionResult
import com.miwealth.sovereignvantage.core.trading.engine.ExecutedOrder
import com.miwealth.sovereignvantage.core.trading.engine.TimeInForce
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * UNIFIED EXCHANGE CONNECTOR
 * 
 * Standardised interface for connecting to ANY cryptocurrency exchange,
 * whether CEX (Kraken, Coinbase, Binance) or DEX (Uniswap, dYdX, Jupiter).
 * 
 * This abstraction enables:
 * - Single codebase for all exchange integrations
 * - Easy addition of new exchanges
 * - Consistent error handling and rate limiting
 * - Aggregated price feeds across exchanges
 * - Smart order routing for best execution
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

// =============================================================================
// EXCHANGE TYPES
// =============================================================================

/**
 * Exchange type classification
 * 
 * V5.17.0: Unified enum merging base + AI exchange types.
 * CEX kept for backward compatibility (equivalent to CEX_SPOT).
 */
enum class ExchangeType {
    CEX,                // Centralised Exchange - generic (backward compat, same as CEX_SPOT)
    CEX_SPOT,           // Centralised spot exchange
    CEX_FUTURES,        // Centralised futures/derivatives
    CEX_MARGIN,         // Centralised margin trading
    DEX_AMM,            // Automated Market Maker (Uniswap, SushiSwap)
    DEX_ORDERBOOK,      // On-chain order book (dYdX, Serum)
    DEX_AGGREGATOR,     // DEX aggregator (1inch, Jupiter)
    FOREX_BROKER,       // Traditional forex/metals (Uphold)
    HYBRID;             // Hybrid model (some CEX features with self-custody)
    
    /** Whether this is any form of centralised exchange */
    val isCEX: Boolean get() = this in listOf(CEX, CEX_SPOT, CEX_FUTURES, CEX_MARGIN)
    
    /** Whether this is any form of decentralised exchange */
    val isDEX: Boolean get() = this in listOf(DEX_AMM, DEX_ORDERBOOK, DEX_AGGREGATOR)
}

/**
 * Blockchain network for DEX operations
 */
enum class BlockchainNetwork {
    ETHEREUM,
    ARBITRUM,
    OPTIMISM,
    POLYGON,
    BASE,
    BSC,
    BINANCE_SMART_CHAIN,  // Alias for BSC used by some connectors
    SOLANA,
    AVALANCHE,
    FANTOM,
    NEAR,
    COSMOS,
    BITCOIN,      // For wrapped BTC operations
    TRON,
    CRONOS,
    ALGORAND,
    DOGECOIN,
    LITECOIN
}

/**
 * Exchange capabilities - what the exchange supports
 */
data class ExchangeCapabilities(
    val supportsSpotTrading: Boolean = true,
    val supportsFutures: Boolean = false,
    val supportsMargin: Boolean = false,
    val supportsOptions: Boolean = false,
    val supportsLending: Boolean = false,
    val supportsStaking: Boolean = false,
    val supportsWebSocket: Boolean = true,
    val supportsOrderbook: Boolean = true,
    val supportsMarketOrders: Boolean = true,
    val supportsLimitOrders: Boolean = true,
    val supportsStopOrders: Boolean = true,
    val supportsPostOnly: Boolean = true,
    val supportsCancelAll: Boolean = true,
    val maxOrdersPerSecond: Int = 10,
    val maxSubscriptionsPerConnection: Int = 100,
    val supportedOrderTypes: List<OrderType> = emptyList(),  // Empty = all standard types supported
    val supportedTimeInForce: List<TimeInForce> = emptyList(),  // Empty = GTC,IOC,FOK supported
    val minOrderValue: Double = 0.0,
    val tradingFeeMaker: Double = 0.001,    // 0.1% default
    val tradingFeeTaker: Double = 0.001,
    val withdrawalEnabled: Boolean = false,  // We're non-custodial, but track capability
    val networks: List<BlockchainNetwork> = emptyList(),  // For DEX
    // V5.17.0: Added for connector compatibility
    val supportsOrderBook: Boolean = supportsOrderbook,  // Alias (capital B)
    val supportsTrades: Boolean = true,
    val supportsOHLCV: Boolean = true
)

/**
 * Exchange status
 */
enum class ExchangeStatus {
    CONNECTED,
    CONNECTING,
    DISCONNECTED,
    RATE_LIMITED,
    MAINTENANCE,
    ERROR
}

/**
 * Connection configuration
 */
data class ExchangeConfig(
    val exchangeId: String,
    val exchangeName: String,
    val exchangeType: ExchangeType,
    val apiKey: String = "",
    val apiSecret: String = "",
    val passphrase: String = "",      // For Coinbase
    val subaccountId: String = "",    // For exchange subaccounts
    val testnet: Boolean = false,
    val baseUrl: String = "",
    val wsUrl: String = "",
    val timeout: Long = 30000,
    val rateLimit: RateLimitConfig = RateLimitConfig()
)

data class RateLimitConfig(
    val requestsPerSecond: Int = 10,
    val requestsPerMinute: Int = 300,
    val burstLimit: Int = 20,
    val cooldownMs: Long = 1000
)

// =============================================================================
// MARKET DATA MODELS
// =============================================================================

// V5.17.0: OrderBook and OrderBookLevel consolidated into data.models.OrderBookModels.kt
// Import canonical definitions — eliminates duplication with SmartOrderRouter
typealias OrderBookLevel = com.miwealth.sovereignvantage.data.models.OrderBookLevel
typealias OrderBook = com.miwealth.sovereignvantage.data.models.OrderBook

/**
 * Trade executed on exchange (from public trade feed)
 */
data class PublicTrade(
    val symbol: String,
    val exchange: String,
    val price: Double,
    val quantity: Double,
    val side: TradeSide,
    val timestamp: Long,
    val tradeId: String = ""
)

/**
 * Trading pair information
 */
data class TradingPair(
    val symbol: String,              // Normalised: "BTC/USD"
    val baseAsset: String,           // "BTC"
    val quoteAsset: String,          // "USD"
    val exchangeSymbol: String,      // Exchange-specific: "XXBTZUSD" for Kraken
    val exchange: String,
    val minQuantity: Double = 0.0,
    val maxQuantity: Double = Double.MAX_VALUE,
    val quantityStep: Double = 0.00000001,  // Lot size increment
    val minPrice: Double = 0.0,
    val maxPrice: Double = Double.MAX_VALUE,
    val priceStep: Double = 0.00000001,     // Tick size
    val minNotional: Double = 0.0,          // Minimum order value
    val status: PairStatus = PairStatus.TRADING,
    // Optional extended fields — backward compatible with defaults
    val pricePrecision: Int = 8,
    val quantityPrecision: Int = 8,
    val isActive: Boolean = (status == PairStatus.TRADING),
    val supportedOrderTypes: List<String> = listOf("MARKET", "LIMIT"),
    val supportedTimeInForce: List<String> = listOf("GTC", "IOC"),
    val supportsOHLCV: Boolean = true
)

enum class PairStatus {
    TRADING,
    HALTED,
    AUCTION,
    BREAK
}

/**
 * Account balance
 */
data class Balance(
    val asset: String,
    val free: Double,               // Available for trading
    val locked: Double,             // In open orders
    val total: Double = free + locked,
    val usdValue: Double = 0.0      // Estimated USD value
)

// =============================================================================
// UNIFIED EXCHANGE CONNECTOR INTERFACE
// =============================================================================

/**
 * Core interface that ALL exchange connectors must implement
 */
interface UnifiedExchangeConnector {
    
    // =========================================================================
    // IDENTITY & STATUS
    // =========================================================================
    
    val config: ExchangeConfig
    val capabilities: ExchangeCapabilities
    val status: StateFlow<ExchangeStatus>
    
    // =========================================================================
    // CONNECTION MANAGEMENT
    // =========================================================================
    
    /**
     * Connect to the exchange
     * @return true if connection successful
     */
    suspend fun connect(): Boolean
    
    /**
     * Disconnect from the exchange
     */
    suspend fun disconnect()
    
    /**
     * Check if currently connected
     */
    fun isConnected(): Boolean
    
    /**
     * Check rate limit status
     */
    fun isRateLimited(): Boolean
    
    // =========================================================================
    // MARKET DATA (Public - No Auth Required)
    // =========================================================================
    
    /**
     * Get current ticker/price for a symbol
     */
    suspend fun getTicker(symbol: String): PriceTick?
    
    /**
     * Get tickers for multiple symbols
     */
    suspend fun getTickers(symbols: List<String>): Map<String, PriceTick>
    
    /**
     * Get order book snapshot
     */
    suspend fun getOrderBook(symbol: String, depth: Int = 20): OrderBook?
    
    /**
     * Get recent public trades
     */
    suspend fun getRecentTrades(symbol: String, limit: Int = 100): List<PublicTrade>
    
    /**
     * Get OHLCV candles
     */
    suspend fun getCandles(
        symbol: String,
        interval: String = "1m",
        limit: Int = 100,
        startTime: Long? = null,
        endTime: Long? = null
    ): List<OHLCVBar>
    
    /**
     * Get available trading pairs
     */
    suspend fun getTradingPairs(): List<TradingPair>
    
    // =========================================================================
    // REAL-TIME STREAMS (WebSocket)
    // =========================================================================
    
    /**
     * Subscribe to real-time price ticks
     */
    fun subscribeToPrices(symbols: List<String>): Flow<PriceTick>
    
    /**
     * Subscribe to real-time order book updates
     */
    fun subscribeToOrderBook(symbol: String): Flow<OrderBook>
    
    /**
     * Subscribe to public trade stream
     */
    fun subscribeToTrades(symbol: String): Flow<PublicTrade>
    
    // =========================================================================
    // ACCOUNT DATA (Private - Auth Required)
    // =========================================================================
    
    /**
     * Get account balances
     */
    suspend fun getBalances(): List<Balance>
    
    /**
     * Get balance for specific asset
     */
    suspend fun getBalance(asset: String): Balance?
    
    // =========================================================================
    // ORDER MANAGEMENT (Private - Auth Required)
    // =========================================================================
    
    /**
     * Place a new order
     */
    suspend fun placeOrder(request: OrderRequest): OrderExecutionResult
    
    /**
     * Cancel an existing order
     */
    suspend fun cancelOrder(orderId: String, symbol: String): Boolean
    
    /**
     * Cancel all orders for a symbol (or all if symbol is null)
     */
    suspend fun cancelAllOrders(symbol: String? = null): Int
    
    /**
     * Modify an existing order (cancel and replace if not supported)
     */
    suspend fun modifyOrder(
        orderId: String,
        symbol: String,
        newPrice: Double? = null,
        newQuantity: Double? = null
    ): OrderExecutionResult
    
    /**
     * Get order status
     */
    suspend fun getOrder(orderId: String, symbol: String): ExecutedOrder?
    
    /**
     * Get all open orders
     */
    suspend fun getOpenOrders(symbol: String? = null): List<ExecutedOrder>
    
    /**
     * Get order history
     */
    suspend fun getOrderHistory(
        symbol: String? = null,
        startTime: Long? = null,
        endTime: Long? = null,
        limit: Int = 100
    ): List<ExecutedOrder>
    
    /**
     * Subscribe to order updates (fills, cancellations)
     */
    fun subscribeToOrderUpdates(): Flow<ExchangeOrderUpdate>
    
    // =========================================================================
    // UTILITY
    // =========================================================================
    
    /**
     * Convert symbol to exchange-specific format
     */
    fun normaliseSymbol(exchangeSymbol: String): String
    
    /**
     * Convert symbol from normalised to exchange-specific format
     */
    fun toExchangeSymbol(normalisedSymbol: String): String
    
    /**
     * Validate order before submission
     */
    fun validateOrder(request: OrderRequest): OrderValidationResult
}

/**
 * Order validation result
 */
sealed class OrderValidationResult {
    object Valid : OrderValidationResult()
    data class Invalid(val reason: String) : OrderValidationResult()
}

/**
 * Order update event from exchange (distinct from core OrderUpdate sealed class)
 */
data class ExchangeOrderUpdate(
    val orderId: String,
    val clientOrderId: String,
    val symbol: String,
    val status: OrderStatus,
    val executedPrice: Double?,
    val executedQuantity: Double?,
    val remainingQuantity: Double?,
    val fee: Double?,
    val feeCurrency: String?,
    val timestamp: Long,
    val exchange: String
)

// =============================================================================
// DEX-SPECIFIC EXTENSIONS
// =============================================================================

/**
 * Extended interface for DEX connectors
 */
interface DEXConnector : UnifiedExchangeConnector {
    
    /**
     * Get network for this DEX
     */
    val network: BlockchainNetwork
    
    /**
     * Estimate gas for a swap
     */
    suspend fun estimateGas(
        fromToken: String,
        toToken: String,
        amount: Double
    ): GasEstimate?
    
    /**
     * Get swap quote
     */
    suspend fun getSwapQuote(
        fromToken: String,
        toToken: String,
        amount: Double,
        slippageTolerance: Double = 0.005  // 0.5%
    ): SwapQuote?
    
    /**
     * Execute swap
     */
    suspend fun executeSwap(quote: SwapQuote): SwapResult
    
    /**
     * Get token approval status
     */
    suspend fun getApprovalStatus(token: String, spender: String): ApprovalStatus
    
    /**
     * Approve token for trading
     */
    suspend fun approveToken(token: String, spender: String, amount: Double? = null): Boolean
}

data class GasEstimate(
    val gasLimit: Long,
    val gasPrice: Double,
    val estimatedCost: Double,
    val estimatedCostUsd: Double
)

data class SwapQuote(
    val fromToken: String,
    val toToken: String,
    val fromAmount: Double,
    val toAmount: Double,
    val exchangeRate: Double,
    val priceImpact: Double,
    val route: List<String>,        // Token path for multi-hop
    val estimatedGas: GasEstimate,
    val validUntil: Long,
    val quoteId: String
)

sealed class SwapResult {
    data class Success(
        val transactionHash: String,
        val fromAmount: Double,
        val toAmount: Double,
        val gasUsed: Long,
        val gasCost: Double
    ) : SwapResult()
    
    data class Failed(val reason: String, val transactionHash: String? = null) : SwapResult()
    data class Pending(val transactionHash: String) : SwapResult()
}

data class ApprovalStatus(
    val token: String,
    val spender: String,
    val allowance: Double,
    val isUnlimited: Boolean
)

// =============================================================================
// AGGREGATED FEED
// =============================================================================

/**
 * Aggregated price across multiple exchanges
 */
data class AggregatedPrice(
    val symbol: String,
    val bestBid: Double,
    val bestBidExchange: String,
    val bestAsk: Double,
    val bestAskExchange: String,
    val vwap: Double,               // Volume-weighted average price
    val totalVolume24h: Double,
    val exchanges: Map<String, PriceTick>,
    val timestamp: Long
) {
    val bestSpread: Double get() = bestAsk - bestBid
    val midPrice: Double get() = (bestBid + bestAsk) / 2
}

/**
 * Interface for aggregating data across multiple exchanges
 */
interface ExchangeAggregator {
    
    /**
     * Add an exchange to the aggregator
     */
    fun addExchange(connector: UnifiedExchangeConnector)
    
    /**
     * Remove an exchange from the aggregator
     */
    fun removeExchange(exchangeId: String)
    
    /**
     * Get connected exchanges
     */
    fun getConnectedExchanges(): List<UnifiedExchangeConnector>
    
    /**
     * Get best price across all exchanges
     */
    suspend fun getBestPrice(symbol: String): AggregatedPrice?
    
    /**
     * Subscribe to aggregated price feed
     */
    fun subscribeToAggregatedPrices(symbols: List<String>): Flow<AggregatedPrice>
    
    /**
     * Find best exchange for an order (smart order routing)
     */
    suspend fun findBestExchange(request: OrderRequest): UnifiedExchangeConnector?
    
    /**
     * Split order across exchanges for best execution
     */
    suspend fun splitOrder(request: OrderRequest): List<Pair<UnifiedExchangeConnector, OrderRequest>>
}