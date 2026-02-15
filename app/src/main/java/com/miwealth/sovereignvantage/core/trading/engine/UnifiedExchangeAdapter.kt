package com.miwealth.sovereignvantage.core.trading.engine

import com.miwealth.sovereignvantage.core.*
import com.miwealth.sovereignvantage.core.exchange.*
import com.miwealth.sovereignvantage.core.trading.routing.RoutableExchangeAdapter
import com.miwealth.sovereignvantage.core.trading.routing.OrderBook as RoutingOrderBook
import com.miwealth.sovereignvantage.core.trading.routing.OrderBookLevel as RoutingOrderBookLevel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * UNIFIED EXCHANGE ADAPTER
 * 
 * Sovereign Vantage: Arthur Edition V5.5.94
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * V5.5.94 CHANGES:
 * - Now implements RoutableExchangeAdapter (extends ExchangeAdapter)
 * - Enables SmartOrderRouter to accept both legacy PQC and AI adapters
 * - Added getExchangeId() for routing identity
 * - getOrderBook() converts core.exchange.OrderBook → core.trading.routing.OrderBook
 * 
 * Bridge class that wraps a UnifiedExchangeConnector (PQC-enabled, new architecture)
 * and implements the RoutableExchangeAdapter interface (used by SmartOrderRouter
 * and OrderExecutor/TradingSystem).
 * 
 * This enables the TradingSystem to use the new PQC-protected exchange connectors
 * (Kraken, Binance, Coinbase) while maintaining backward compatibility with the
 * existing order execution infrastructure.
 * 
 * Features:
 * - Full PQC protection via underlying connector
 * - WebSocket price streaming integration
 * - Automatic symbol normalisation
 * - Rate limit awareness
 * - Order status tracking
 * - SmartOrderRouter compatible (V5.5.94)
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */
class UnifiedExchangeAdapter(
    private val connector: UnifiedExchangeConnector,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : RoutableExchangeAdapter {
    
    override val exchangeName: String
        get() = connector.config.exchangeName
    
    // =========================================================================
    // EXCHANGE ADAPTER IMPLEMENTATION
    // =========================================================================
    
    override suspend fun placeOrder(request: OrderRequest): OrderExecutionResult {
        return try {
            // Validate order first
            val validation = connector.validateOrder(request)
            if (validation is OrderValidationResult.Invalid) {
                return OrderExecutionResult.Rejected(validation.reason)
            }
            
            // Place order via unified connector
            connector.placeOrder(request)
        } catch (e: Exception) {
            OrderExecutionResult.Error(e)
        }
    }
    
    override suspend fun cancelOrder(orderId: String, symbol: String): Boolean {
        return try {
            connector.cancelOrder(orderId, symbol)
        } catch (e: Exception) {
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
            null
        }
    }
    
    override suspend fun getOpenOrders(symbol: String?): List<ExecutedOrder> {
        return try {
            connector.getOpenOrders(symbol)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    override fun isRateLimited(): Boolean {
        return connector.isRateLimited()
    }
    
    // =========================================================================
    // EXTENDED API - PRICE FEEDS
    // =========================================================================
    
    /**
     * Subscribe to real-time price updates for symbols.
     * Returns a Flow of PriceTick that can be collected by TradingCoordinator.
     */
    fun subscribeToPrices(symbols: List<String>): Flow<PriceTick> {
        return connector.subscribeToPrices(symbols)
    }
    
    /**
     * Get current ticker for a symbol.
     * V5.5.94: Now overrides RoutableExchangeAdapter for SmartOrderRouter compatibility.
     */
    override suspend fun getTicker(symbol: String): PriceTick? {
        return connector.getTicker(symbol)
    }
    
    /**
     * Get tickers for multiple symbols
     */
    suspend fun getTickers(symbols: List<String>): Map<String, PriceTick> {
        return connector.getTickers(symbols)
    }
    
    /**
     * Get order book for a symbol.
     * V5.5.94: Now overrides RoutableExchangeAdapter.
     * Converts core.exchange.OrderBook → core.trading.routing.OrderBook for
     * SmartOrderRouter compatibility. Returns null if order book unavailable
     * (router falls back to ticker-price-only routing).
     */
    override suspend fun getOrderBook(symbol: String, depth: Int): RoutingOrderBook? {
        val exchangeOrderBook = connector.getOrderBook(symbol, depth) ?: return null
        return RoutingOrderBook(
            symbol = exchangeOrderBook.symbol,
            bids = exchangeOrderBook.bids.map { RoutingOrderBookLevel(it.price, it.quantity) },
            asks = exchangeOrderBook.asks.map { RoutingOrderBookLevel(it.price, it.quantity) },
            timestamp = exchangeOrderBook.timestamp
        )
    }
    
    /**
     * Get OHLCV candles for a symbol
     */
    suspend fun getCandles(
        symbol: String,
        interval: String = "1m",
        limit: Int = 100,
        startTime: Long? = null,
        endTime: Long? = null
    ): List<OHLCVBar> {
        return connector.getCandles(symbol, interval, limit, startTime, endTime)
    }
    
    /**
     * Get available trading pairs
     */
    suspend fun getTradingPairs(): List<TradingPair> {
        return connector.getTradingPairs()
    }
    
    // =========================================================================
    // EXTENDED API - ACCOUNT DATA
    // =========================================================================
    
    /**
     * Get account balances
     */
    suspend fun getBalances(): List<Balance> {
        return connector.getBalances()
    }
    
    /**
     * Get balance for specific asset
     */
    suspend fun getBalance(asset: String): Balance? {
        return connector.getBalance(asset)
    }
    
    /**
     * Get order history
     */
    suspend fun getOrderHistory(
        symbol: String? = null,
        startTime: Long? = null,
        endTime: Long? = null,
        limit: Int = 100
    ): List<ExecutedOrder> {
        return connector.getOrderHistory(symbol, startTime, endTime, limit)
    }
    
    /**
     * Cancel all open orders for a symbol (or all if null)
     */
    suspend fun cancelAllOrders(symbol: String? = null): Int {
        return connector.cancelAllOrders(symbol)
    }
    
    // =========================================================================
    // EXTENDED API - WEBSOCKET STREAMS
    // =========================================================================
    
    /**
     * Subscribe to order book updates
     */
    fun subscribeToOrderBook(symbol: String): Flow<OrderBook> {
        return connector.subscribeToOrderBook(symbol)
    }
    
    /**
     * Subscribe to public trade stream
     */
    fun subscribeToTrades(symbol: String): Flow<PublicTrade> {
        return connector.subscribeToTrades(symbol)
    }
    
    /**
     * Subscribe to order updates (fills, cancellations)
     */
    fun subscribeToOrderUpdates(): Flow<ExchangeOrderUpdate> {
        return connector.subscribeToOrderUpdates()
    }
    
    // =========================================================================
    // CONNECTION MANAGEMENT
    // =========================================================================
    
    /**
     * Connect to the exchange
     */
    suspend fun connect(): Boolean {
        return connector.connect()
    }
    
    /**
     * Disconnect from the exchange
     */
    suspend fun disconnect() {
        connector.disconnect()
    }
    
    /**
     * Check if connected.
     * V5.5.94: Now overrides RoutableExchangeAdapter for SmartOrderRouter compatibility.
     */
    override fun isConnected(): Boolean {
        return connector.isConnected()
    }
    
    /**
     * Get exchange status
     */
    fun getStatus(): StateFlow<ExchangeStatus> {
        return connector.status
    }
    
    /**
     * Get exchange capabilities
     */
    fun getCapabilities(): ExchangeCapabilities {
        return connector.capabilities
    }
    
    // =========================================================================
    // SYMBOL UTILITIES
    // =========================================================================
    
    /**
     * Convert symbol to normalised format (e.g., "XXBTZUSD" -> "BTC/USD")
     */
    fun normaliseSymbol(exchangeSymbol: String): String {
        return connector.normaliseSymbol(exchangeSymbol)
    }
    
    /**
     * Convert symbol to exchange-specific format (e.g., "BTC/USD" -> "XXBTZUSD")
     */
    fun toExchangeSymbol(normalisedSymbol: String): String {
        return connector.toExchangeSymbol(normalisedSymbol)
    }
    
    // =========================================================================
    // UNDERLYING CONNECTOR ACCESS
    // =========================================================================
    
    /**
     * Get the underlying UnifiedExchangeConnector for advanced operations.
     * Use with caution - prefer the wrapped methods where possible.
     */
    fun getUnderlyingConnector(): UnifiedExchangeConnector = connector
    
    /**
     * Get exchange identity for routing decisions.
     * V5.5.94: RoutableExchangeAdapter implementation.
     */
    override fun getExchangeId(): String = connector.config.exchangeId
    
    /**
     * Get exchange configuration
     */
    fun getConfig(): ExchangeConfig = connector.config
    
    /**
     * Shutdown adapter — cancel scope.
     */
    fun shutdown() {
        scope.cancel()
    }
}

/**
 * Factory function to create UnifiedExchangeAdapter from an exchange registry connection.
 */
fun UnifiedExchangeConnector.toExchangeAdapter(
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
): UnifiedExchangeAdapter {
    return UnifiedExchangeAdapter(this, scope)
}
