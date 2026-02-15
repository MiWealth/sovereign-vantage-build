/**
 * AI EXCHANGE CONNECTION MANAGER
 * 
 * Sovereign Vantage: Arthur Edition V5.6.0
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * V5.6.0 CHANGES:
 * - Added subscribeToAllOrderBooks() for multi-exchange order book streaming
 *   Parallel WebSocket subscription to order books across all connected exchanges
 *   Provides real bid/ask depth to TradingCoordinator for accurate arb spread
 * 
 * V5.5.76 CHANGES:
 * - Added cancelOrder(), modifyOrder(), getOrderStatus() for order management
 * - Added getAllOpenOrders() for cross-exchange order tracking
 * - Added isAnyExchangeRateLimited() for rate limit awareness
 * - Added subscribeToAllPrices() for multi-exchange price streaming
 * - Added subscribeToOrderBook() and getAvailablePairs()
 * - Added getAggregatedBalance() for per-asset balance lookup
 * 
 * Central manager for all exchange connections. Handles:
 * - Connection lifecycle (connect, disconnect, reconnect)
 * - Health monitoring across all connected exchanges
 * - Automatic re-learning when APIs change
 * - Smart routing to best exchange for each asset
 * - Credential management
 * 
 * This replaces the need for maintaining separate connectors and
 * provides a unified interface for all exchange operations.
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */
package com.miwealth.sovereignvantage.core.exchange.ai

import android.content.Context
import android.util.Log
import com.miwealth.sovereignvantage.core.*
import com.miwealth.sovereignvantage.core.exchange.*
import com.miwealth.sovereignvantage.core.trading.engine.OrderRequest
import com.miwealth.sovereignvantage.core.trading.engine.OrderExecutionResult
import com.miwealth.sovereignvantage.core.trading.engine.ExecutedOrder
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Central manager for all exchange connections.
 * 
 * Usage:
 * ```kotlin
 * val manager = AIConnectionManager(context)
 * 
 * // Add exchanges
 * manager.addExchange(
 *     exchangeId = "binance",
 *     baseUrl = "https://api.binance.com",
 *     credentials = ExchangeCredentials(apiKey, apiSecret)
 * )
 * 
 * // Connect all
 * manager.connectAll()
 * 
 * // Use unified operations
 * val ticker = manager.getBestTicker("BTC/USDT")
 * val balances = manager.getAllBalances()
 * 
 * // Smart order routing
 * val result = manager.placeOrderSmart(orderRequest)
 * ```
 */
class AIConnectionManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "AIConnectionManager"
        
        // Well-known exchange configurations
        val KNOWN_EXCHANGES = mapOf(
            "binance" to ExchangeInfo("Binance", "https://api.binance.com", "wss://stream.binance.com:9443/ws", ExchangeType.CEX_SPOT),
            "binance_testnet" to ExchangeInfo("Binance Testnet", "https://testnet.binance.vision", "wss://testnet.binance.vision/ws", ExchangeType.CEX_SPOT),
            "kraken" to ExchangeInfo("Kraken", "https://api.kraken.com", "wss://ws.kraken.com", ExchangeType.CEX_SPOT),
            "coinbase" to ExchangeInfo("Coinbase", "https://api.exchange.coinbase.com", "wss://ws-feed.exchange.coinbase.com", ExchangeType.CEX_SPOT),
            "coinbase_sandbox" to ExchangeInfo("Coinbase Sandbox", "https://api-public.sandbox.exchange.coinbase.com", "wss://ws-feed-public.sandbox.exchange.coinbase.com", ExchangeType.CEX_SPOT),
            "bybit" to ExchangeInfo("Bybit", "https://api.bybit.com", "wss://stream.bybit.com/v5/public/spot", ExchangeType.CEX_SPOT),
            "bybit_testnet" to ExchangeInfo("Bybit Testnet", "https://api-testnet.bybit.com", "wss://stream-testnet.bybit.com/v5/public/spot", ExchangeType.CEX_SPOT),
            "okx" to ExchangeInfo("OKX", "https://www.okx.com", "wss://ws.okx.com:8443/ws/v5/public", ExchangeType.CEX_SPOT),
            "kucoin" to ExchangeInfo("KuCoin", "https://api.kucoin.com", null, ExchangeType.CEX_SPOT),
            "kucoin_sandbox" to ExchangeInfo("KuCoin Sandbox", "https://openapi-sandbox.kucoin.com", null, ExchangeType.CEX_SPOT),
            "gateio" to ExchangeInfo("Gate.io", "https://api.gateio.ws", "wss://api.gateio.ws/ws/v4/", ExchangeType.CEX_SPOT),
            "mexc" to ExchangeInfo("MEXC", "https://api.mexc.com", "wss://wbs.mexc.com/ws", ExchangeType.CEX_SPOT),
            "bitget" to ExchangeInfo("Bitget", "https://api.bitget.com", "wss://ws.bitget.com/spot/v1/stream", ExchangeType.CEX_SPOT),
            "gemini" to ExchangeInfo("Gemini", "https://api.gemini.com", "wss://api.gemini.com/v1/marketdata", ExchangeType.CEX_SPOT),
            "gemini_sandbox" to ExchangeInfo("Gemini Sandbox", "https://api.sandbox.gemini.com", "wss://api.sandbox.gemini.com/v1/marketdata", ExchangeType.CEX_SPOT),
            "uphold" to ExchangeInfo("Uphold", "https://api.uphold.com", null, ExchangeType.FOREX_BROKER),
            "uphold_sandbox" to ExchangeInfo("Uphold Sandbox", "https://api-sandbox.uphold.com", null, ExchangeType.FOREX_BROKER)
        )
    }
    
    // Connected exchanges
    private val connectors = ConcurrentHashMap<String, AIExchangeConnector>()
    
    // Credentials storage (encrypted in production)
    private val credentialStore = ConcurrentHashMap<String, ExchangeCredentials>()
    
    // Health monitoring
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var healthMonitorJob: Job? = null
    
    // State
    private val _connectionState = MutableStateFlow<Map<String, ConnectionStatus>>(emptyMap())
    val connectionState: StateFlow<Map<String, ConnectionStatus>> = _connectionState.asStateFlow()
    
    private val _healthUpdates = MutableSharedFlow<ConnectionHealth>(extraBufferCapacity = 50)
    val healthUpdates: SharedFlow<ConnectionHealth> = _healthUpdates.asSharedFlow()
    
    // Asset availability cache
    private val assetExchangeMap = ConcurrentHashMap<String, MutableSet<String>>()
    
    // =========================================================================
    // EXCHANGE MANAGEMENT
    // =========================================================================
    
    /**
     * Add an exchange to manage.
     */
    suspend fun addExchange(
        exchangeId: String,
        baseUrl: String? = null,
        wsUrl: String? = null,
        sandboxUrl: String? = null,
        credentials: ExchangeCredentials? = null,
        type: ExchangeType = ExchangeType.CEX_SPOT,
        autoConnect: Boolean = false
    ): Boolean {
        try {
            // Get URL from known exchanges if not provided
            val info = KNOWN_EXCHANGES[exchangeId.lowercase()]
            val finalBaseUrl = baseUrl ?: info?.baseUrl ?: return false
            val finalWsUrl = wsUrl ?: info?.wsUrl
            val finalType = info?.type ?: type
            
            // Store credentials
            credentials?.let { credentialStore[exchangeId] = it }
            
            // Create connector
            val connector = AIExchangeConnector.create(
                context = context,
                exchangeId = exchangeId,
                baseUrl = finalBaseUrl,
                credentials = credentials,
                type = finalType,
                wsUrl = finalWsUrl,
                sandboxUrl = sandboxUrl
            )
            
            connectors[exchangeId] = connector
            
            Log.i(TAG, "Added exchange: $exchangeId")
            
            // Auto-connect if requested
            if (autoConnect) {
                return connect(exchangeId)
            }
            
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add exchange $exchangeId", e)
            return false
        }
    }
    
    /**
     * Add a known exchange by ID.
     */
    suspend fun addKnownExchange(
        exchangeId: String,
        credentials: ExchangeCredentials? = null,
        autoConnect: Boolean = false
    ): Boolean {
        val info = KNOWN_EXCHANGES[exchangeId.lowercase()] ?: return false
        return addExchange(
            exchangeId = exchangeId,
            baseUrl = info.baseUrl,
            wsUrl = info.wsUrl,
            credentials = credentials,
            type = info.type,
            autoConnect = autoConnect
        )
    }
    
    /**
     * Remove an exchange.
     */
    suspend fun removeExchange(exchangeId: String) {
        connectors[exchangeId]?.disconnect()
        connectors.remove(exchangeId)
        credentialStore.remove(exchangeId)
        
        updateConnectionState()
        Log.i(TAG, "Removed exchange: $exchangeId")
    }
    
    /**
     * Get a specific connector.
     */
    fun getConnector(exchangeId: String): AIExchangeConnector? = connectors[exchangeId]
    
    /**
     * Get all connector IDs.
     */
    fun getExchangeIds(): Set<String> = connectors.keys.toSet()
    
    /**
     * Get all connected exchange connectors.
     */
    fun getConnectedExchanges(): Map<String, AIExchangeConnector> = connectors.toMap()
    
    /**
     * Check if exchange is added.
     */
    fun hasExchange(exchangeId: String): Boolean = connectors.containsKey(exchangeId)
    
    // =========================================================================
    // CONNECTION MANAGEMENT
    // =========================================================================
    
    /**
     * Connect a specific exchange.
     */
    suspend fun connect(exchangeId: String): Boolean {
        val connector = connectors[exchangeId] ?: return false
        
        val success = connector.connect()
        
        if (success) {
            // Update asset availability
            updateAssetAvailability(exchangeId, connector)
        }
        
        updateConnectionState()
        return success
    }
    
    /**
     * Connect all exchanges.
     */
    suspend fun connectAll(): Map<String, Boolean> {
        return coroutineScope {
            connectors.map { (id, connector) ->
                async { id to connect(id) }
            }.awaitAll().toMap()
        }
    }
    
    /**
     * Disconnect a specific exchange.
     */
    suspend fun disconnect(exchangeId: String) {
        connectors[exchangeId]?.disconnect()
        updateConnectionState()
    }
    
    /**
     * Disconnect all exchanges.
     */
    suspend fun disconnectAll() {
        connectors.values.forEach { it.disconnect() }
        updateConnectionState()
    }
    
    /**
     * Reconnect a failing exchange.
     */
    suspend fun reconnect(exchangeId: String): Boolean {
        disconnect(exchangeId)
        delay(1000)
        return connect(exchangeId)
    }
    
    // =========================================================================
    // HEALTH MONITORING
    // =========================================================================
    
    /**
     * Start health monitoring.
     */
    fun startHealthMonitoring(intervalMs: Long = 30000) {
        healthMonitorJob?.cancel()
        
        healthMonitorJob = scope.launch {
            while (isActive) {
                checkAllHealth()
                delay(intervalMs)
            }
        }
        
        Log.i(TAG, "Started health monitoring with ${intervalMs}ms interval")
    }
    
    /**
     * Stop health monitoring.
     */
    fun stopHealthMonitoring() {
        healthMonitorJob?.cancel()
        healthMonitorJob = null
    }
    
    /**
     * Check health of all exchanges.
     */
    suspend fun checkAllHealth(): Map<String, ConnectionHealth> {
        val results = mutableMapOf<String, ConnectionHealth>()
        
        for ((exchangeId, connector) in connectors) {
            val health = AIExchangeConnector.getHealth(exchangeId)
            
            if (health != null) {
                results[exchangeId] = health
                _healthUpdates.emit(health)
                
                // Auto-reconnect if needed
                if (health.needsRelearning || health.status == ConnectionStatus.FAILING) {
                    Log.w(TAG, "Exchange $exchangeId needs attention: ${health.status}")
                    
                    if (health.consecutiveFailures >= 10) {
                        // Clear schema and re-learn
                        Log.i(TAG, "Triggering re-learning for $exchangeId")
                        AIExchangeConnector.clearSchemaCache(exchangeId)
                        reconnect(exchangeId)
                    }
                }
            }
        }
        
        return results
    }
    
    /**
     * Get current health for an exchange.
     */
    fun getHealth(exchangeId: String): ConnectionHealth? = AIExchangeConnector.getHealth(exchangeId)
    
    /**
     * Get health for all exchanges.
     */
    fun getAllHealth(): Map<String, ConnectionHealth?> {
        return connectors.keys.associateWith { AIExchangeConnector.getHealth(it) }
    }
    
    // =========================================================================
    // SMART ROUTING
    // =========================================================================
    
    /**
     * Get the best exchange for a given asset.
     */
    fun getBestExchangeFor(symbol: String): String? {
        val exchanges = assetExchangeMap[symbol] ?: return null
        
        // Prefer healthy exchanges
        return exchanges
            .mapNotNull { exchangeId ->
                val health = getHealth(exchangeId)
                if (health?.status == ConnectionStatus.HEALTHY) {
                    exchangeId to health.successRate
                } else null
            }
            .maxByOrNull { it.second }
            ?.first
            ?: exchanges.firstOrNull()
    }
    
    /**
     * Get all exchanges that support an asset.
     */
    fun getExchangesFor(symbol: String): Set<String> {
        return assetExchangeMap[symbol]?.toSet() ?: emptySet()
    }
    
    /**
     * Get best ticker across all exchanges.
     */
    suspend fun getBestTicker(symbol: String): PriceTick? {
        val exchangeId = getBestExchangeFor(symbol) ?: return null
        return connectors[exchangeId]?.getTicker(symbol)
    }
    
    /**
     * Get ticker from all exchanges.
     */
    suspend fun getAllTickers(symbol: String): Map<String, PriceTick?> {
        val exchanges = getExchangesFor(symbol)
        
        return coroutineScope {
            exchanges.map { exchangeId ->
                async { exchangeId to connectors[exchangeId]?.getTicker(symbol) }
            }.awaitAll().toMap()
        }
    }
    
    // =========================================================================
    // UNIFIED OPERATIONS
    // =========================================================================
    
    /**
     * Get balances from all connected exchanges.
     */
    suspend fun getAllBalances(): Map<String, List<Balance>> {
        return coroutineScope {
            connectors
                .filter { it.value.status.value == ExchangeStatus.CONNECTED }
                .map { (exchangeId, connector) ->
                    async { exchangeId to connector.getBalances() }
                }
                .awaitAll()
                .toMap()
        }
    }
    
    /**
     * Get aggregated balance across all exchanges.
     */
    suspend fun getAggregatedBalances(): List<AggregatedBalance> {
        val allBalances = getAllBalances()
        
        val aggregated = mutableMapOf<String, AggregatedBalance>()
        
        for ((exchangeId, balances) in allBalances) {
            for (balance in balances) {
                val existing = aggregated[balance.asset]
                if (existing != null) {
                    aggregated[balance.asset] = existing.copy(
                        total = existing.total + balance.total,
                        available = existing.available + balance.available,
                        locked = existing.locked + balance.locked,
                        byExchange = existing.byExchange + (exchangeId to balance)
                    )
                } else {
                    aggregated[balance.asset] = AggregatedBalance(
                        asset = balance.asset,
                        total = balance.total,
                        available = balance.available,
                        locked = balance.locked,
                        byExchange = mapOf(exchangeId to balance)
                    )
                }
            }
        }
        
        return aggregated.values.toList().sortedByDescending { it.total }
    }
    
    /**
     * Place order with smart routing.
     */
    suspend fun placeOrderSmart(request: OrderRequest): OrderExecutionResult {
        // Find best exchange for this symbol
        val exchangeId = getBestExchangeFor(request.symbol)
            ?: return OrderExecutionResult.Error(Exception("No exchange available for ${request.symbol}"))
        
        val connector = connectors[exchangeId]
            ?: return OrderExecutionResult.Error(Exception("Connector not found for $exchangeId"))
        
        return connector.placeOrder(request)
    }
    
    /**
     * Place order on specific exchange.
     */
    suspend fun placeOrder(exchangeId: String, request: OrderRequest): OrderExecutionResult {
        val connector = connectors[exchangeId]
            ?: return OrderExecutionResult.Error(Exception("Exchange $exchangeId not connected"))
        
        return connector.placeOrder(request)
    }
    
    // =========================================================================
    // ORDER MANAGEMENT
    // =========================================================================
    
    /**
     * Cancel an order on a specific exchange.
     */
    suspend fun cancelOrder(exchangeId: String, orderId: String, symbol: String): Boolean {
        val connector = connectors[exchangeId] ?: return false
        return try {
            connector.cancelOrder(symbol, orderId)  // Note: AIExchangeConnector takes (symbol, orderId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel order $orderId on $exchangeId", e)
            false
        }
    }
    
    /**
     * Modify an order on a specific exchange.
     */
    suspend fun modifyOrder(
        exchangeId: String,
        orderId: String,
        symbol: String,
        newPrice: Double?,
        newQuantity: Double?
    ): OrderExecutionResult {
        val connector = connectors[exchangeId]
            ?: return OrderExecutionResult.Error(Exception("Exchange $exchangeId not connected"))
        
        return try {
            connector.modifyOrder(orderId, symbol, newPrice, newQuantity)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to modify order $orderId on $exchangeId", e)
            OrderExecutionResult.Error(e)
        }
    }
    
    /**
     * Get order status from a specific exchange.
     */
    suspend fun getOrderStatus(exchangeId: String, orderId: String, symbol: String): ExecutedOrder? {
        val connector = connectors[exchangeId] ?: return null
        return try {
            connector.getOrder(symbol, orderId)  // Note: AIExchangeConnector takes (symbol, orderId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get order status for $orderId on $exchangeId", e)
            null
        }
    }
    
    /**
     * Get all open orders across all connected exchanges.
     */
    suspend fun getAllOpenOrders(symbol: String? = null): List<ExecutedOrder> {
        return coroutineScope {
            connectors
                .filter { it.value.status.value == ExchangeStatus.CONNECTED }
                .map { (_, connector) ->
                    async { 
                        try { connector.getOpenOrders(symbol) } 
                        catch (e: Exception) { emptyList() }
                    }
                }
                .awaitAll()
                .flatten()
        }
    }
    
    /**
     * Check if any connected exchange is rate limited.
     */
    fun isAnyExchangeRateLimited(): Boolean {
        return connectors.values.any { it.isRateLimited() }
    }
    
    /**
     * Get aggregated balance for a specific asset across all exchanges.
     */
    suspend fun getAggregatedBalance(asset: String): Double {
        val aggregated = getAggregatedBalances()
        return aggregated.find { it.asset == asset }?.total ?: 0.0
    }
    
    // =========================================================================
    // PRICE STREAMING
    // =========================================================================
    
    /**
     * Subscribe to real-time prices for multiple symbols across best exchanges.
     */
    fun subscribeToAllPrices(symbols: List<String>): Flow<PriceTick> = channelFlow {
        // Group symbols by best exchange
        val symbolsByExchange = mutableMapOf<String, MutableList<String>>()
        
        for (symbol in symbols) {
            val exchangeId = getBestExchangeFor(symbol) ?: continue
            symbolsByExchange.getOrPut(exchangeId) { mutableListOf() }.add(symbol)
        }
        
        // Subscribe to each exchange
        val jobs = symbolsByExchange.map { (exchangeId, exchangeSymbols) ->
            launch {
                connectors[exchangeId]?.let { connector ->
                    connector.subscribeToPrices(exchangeSymbols).collect { tick ->
                        send(tick)
                    }
                }
            }
        }
        
        // Keep channel open until cancelled
        awaitClose { jobs.forEach { it.cancel() } }
    }
    
    /**
     * Subscribe to order book updates for a symbol.
     */
    fun subscribeToOrderBook(exchangeId: String, symbol: String, depth: Int = 10): Flow<OrderBook> {
        return connectors[exchangeId]?.subscribeToOrderBook(symbol) ?: emptyFlow()
    }
    
    /**
     * V5.6.0: Subscribe to order book updates for multiple symbols across ALL connected exchanges.
     * 
     * Returns a merged flow of OrderBook snapshots from every exchange's WebSocket feed.
     * Each OrderBook includes the exchange field, enabling TradingCoordinator to update
     * crossExchangePrices with real best-bid/best-ask for accurate arb spread calculation.
     * 
     * Similar pattern to subscribeToAllPrices() — parallel subscriptions merged into single flow.
     */
    fun subscribeToAllOrderBooks(symbols: List<String>): Flow<OrderBook> = channelFlow {
        val exchangeIds = connectors.keys.toList()
        
        if (exchangeIds.isEmpty()) {
            Log.w(TAG, "subscribeToAllOrderBooks: No exchanges connected")
            return@channelFlow
        }
        
        Log.i(TAG, "Starting order book feeds: ${symbols.size} symbols × ${exchangeIds.size} exchanges")
        
        val jobs = exchangeIds.flatMap { exchangeId ->
            symbols.map { symbol ->
                launch {
                    try {
                        connectors[exchangeId]?.subscribeToOrderBook(symbol)?.collect { book ->
                            send(book)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Order book subscription failed: $exchangeId/$symbol: ${e.message}")
                    }
                }
            }
        }
        
        awaitClose { jobs.forEach { it.cancel() } }
    }
    
    /**
     * Get available trading pairs from a specific exchange.
     */
    suspend fun getAvailablePairs(exchangeId: String): List<String> {
        return try {
            connectors[exchangeId]?.getTradingPairs()?.map { it.symbol } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get trading pairs from $exchangeId", e)
            emptyList()
        }
    }
    
    // =========================================================================
    // ASSET DISCOVERY
    // =========================================================================
    
    /**
     * Discover all available assets across exchanges.
     */
    suspend fun discoverAllAssets(): Map<String, Set<String>> {
        for ((exchangeId, connector) in connectors) {
            if (connector.status.value == ExchangeStatus.CONNECTED) {
                updateAssetAvailability(exchangeId, connector)
            }
        }
        
        return assetExchangeMap.mapValues { it.value.toSet() }
    }
    
    private suspend fun updateAssetAvailability(exchangeId: String, connector: AIExchangeConnector) {
        try {
            val pairs = connector.getTradingPairs()
            
            for (pair in pairs) {
                assetExchangeMap.getOrPut(pair.symbol) { mutableSetOf() }.add(exchangeId)
            }
            
            Log.d(TAG, "Updated asset availability for $exchangeId: ${pairs.size} pairs")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update asset availability for $exchangeId", e)
        }
    }
    
    // =========================================================================
    // HELPERS
    // =========================================================================
    
    private fun updateConnectionState() {
        _connectionState.value = connectors.mapValues { (_, connector) ->
            when (connector.status.value) {
                ExchangeStatus.CONNECTED -> ConnectionStatus.HEALTHY
                ExchangeStatus.CONNECTING -> ConnectionStatus.DEGRADED
                ExchangeStatus.DISCONNECTED -> ConnectionStatus.DISCONNECTED
                ExchangeStatus.ERROR -> ConnectionStatus.FAILING
                else -> ConnectionStatus.DISCONNECTED
            }
        }
    }
    
    /**
     * Cleanup resources.
     */
    fun shutdown() {
        stopHealthMonitoring()
        scope.cancel()
        
        runBlocking {
            disconnectAll()
        }
    }
}

/**
 * Exchange info for known exchanges.
 */
data class ExchangeInfo(
    val name: String,
    val baseUrl: String,
    val wsUrl: String?,
    val type: ExchangeType
)

/**
 * Aggregated balance across exchanges.
 */
data class AggregatedBalance(
    val asset: String,
    val total: Double,
    val available: Double,
    val locked: Double,
    val byExchange: Map<String, Balance>
)
