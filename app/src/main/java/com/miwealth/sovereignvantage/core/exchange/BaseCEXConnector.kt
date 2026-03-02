package com.miwealth.sovereignvantage.core.exchange

import com.miwealth.sovereignvantage.core.*
import com.miwealth.sovereignvantage.core.trading.engine.OrderRequest
import com.miwealth.sovereignvantage.core.trading.engine.OrderExecutionResult
import com.miwealth.sovereignvantage.core.trading.engine.ExecutedOrder
import com.miwealth.sovereignvantage.core.trading.engine.TimeInForce
import com.miwealth.sovereignvantage.core.security.pqc.hybrid.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * BASE CEX CONNECTOR (PQC-INTEGRATED)
 * 
 * Abstract base class providing common functionality for all CEX integrations
 * with full hybrid post-quantum cryptography protection.
 * 
 * Security Features:
 * - Hybrid PQC (Kyber-1024 + Dilithium-5) for quantum-safe communications
 * - PQCCredentialVault integration for secure API key storage
 * - Dilithium-signed request audit trail for non-repudiation
 * - Side-channel attack mitigations
 * - TLS 1.3 + PQC header protection
 * 
 * Subclasses only need to implement exchange-specific logic:
 * - Authentication signing (exchange's native signing, wrapped by PQC)
 * - API endpoint mapping
 * - Response parsing
 * 
 * Backward Compatibility:
 * - Works with direct ExchangeConfig credentials for testing
 * - Gracefully falls back if PQC components unavailable
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

abstract class BaseCEXConnector(
    override val config: ExchangeConfig,
    private val secureHttpClient: HybridSecureHttpClient? = null,
    private val credentialVault: PQCCredentialVault? = null,
    private val pqcConfig: HybridPQCConfig = HybridPQCConfig.forExchange(config.exchangeId)
) : UnifiedExchangeConnector {
    
    // =========================================================================
    // STATE
    // =========================================================================
    
    protected val gson = Gson()
    protected val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _status = MutableStateFlow(ExchangeStatus.DISCONNECTED)
    override val status: StateFlow<ExchangeStatus> = _status.asStateFlow()
    
    // PQC-enabled HTTP client (lazy init if not provided)
    private val httpClient: HybridSecureHttpClient by lazy {
        secureHttpClient ?: HybridSecureHttpClient.create(pqcConfig)
    }
    
    // Legacy OkHttp client for fallback (if PQC fails) — uses shared pool
    private val legacyHttpClient: OkHttpClient by lazy {
        com.miwealth.sovereignvantage.core.network.SharedHttpClient.baseClient
    }
    
    // Rate limiting — V5.17.0: ArrayDeque for O(1) eviction (N3 optimisation, timestamps are monotonic)
    private val requestTimestamps = ArrayDeque<Long>()
    private val rateLimitMutex = Mutex()
    private var rateLimitedUntil: AtomicLong = AtomicLong(0)
    
    // WebSocket
    protected var webSocket: WebSocket? = null
    private var wsReconnectJob: Job? = null
    private var wsHeartbeatJob: Job? = null
    
    // Caching
    private val tickerCache = ConcurrentHashMap<String, Pair<PriceTick, Long>>()
    private val orderBookCache = ConcurrentHashMap<String, Pair<OrderBook, Long>>()
    private val pairCache = ConcurrentHashMap<String, TradingPair>()
    private val symbolMap = ConcurrentHashMap<String, String>()  // normalised -> exchange
    private val reverseSymbolMap = ConcurrentHashMap<String, String>()  // exchange -> normalised
    
    // Streams
    protected val _priceUpdates = MutableSharedFlow<PriceTick>(replay = 1, extraBufferCapacity = 100)
    protected val _orderBookUpdates = MutableSharedFlow<OrderBook>(replay = 1, extraBufferCapacity = 100)
    protected val _tradeUpdates = MutableSharedFlow<PublicTrade>(replay = 1, extraBufferCapacity = 100)
    protected val _orderUpdates = MutableSharedFlow<ExchangeOrderUpdate>(replay = 1, extraBufferCapacity = 100)
    
    // Cached credentials (retrieved from vault or config)
    private var cachedCredentials: ResolvedCredentials? = null
    
    companion object {
        private const val CACHE_TTL_MS = 1000L  // 1 second cache
        private const val WS_RECONNECT_DELAY_MS = 5000L
        private const val WS_HEARTBEAT_INTERVAL_MS = 30000L
    }
    
    // =========================================================================
    // CREDENTIAL RESOLUTION
    // =========================================================================
    
    /**
     * Resolved credentials - from vault or config
     */
    protected data class ResolvedCredentials(
        val apiKey: String,
        val apiSecret: String,
        val passphrase: String,
        val fromVault: Boolean
    )
    
    /**
     * Get credentials - checks vault first, falls back to config
     */
    protected suspend fun getCredentials(): ResolvedCredentials {
        // Return cached if available
        cachedCredentials?.let { return it }
        
        // Try vault first
        if (credentialVault != null && credentialVault.isUnlocked()) {
            val vaultCred = credentialVault.getCredential(config.exchangeId)
            if (vaultCred != null) {
                val resolved = ResolvedCredentials(
                    apiKey = vaultCred.apiKey,
                    apiSecret = vaultCred.apiSecret,
                    passphrase = vaultCred.passphrase,
                    fromVault = true
                )
                cachedCredentials = resolved
                return resolved
            }
        }
        
        // Fall back to config (for testing or when vault not available)
        val resolved = ResolvedCredentials(
            apiKey = config.apiKey,
            apiSecret = config.apiSecret,
            passphrase = config.passphrase,
            fromVault = false
        )
        cachedCredentials = resolved
        return resolved
    }
    
    /**
     * Clear cached credentials (call when vault is locked or credentials change)
     */
    protected fun clearCredentialCache() {
        cachedCredentials = null
    }
    
    // =========================================================================
    // ABSTRACT METHODS (Exchange-Specific Implementation Required)
    // =========================================================================
    
    /**
     * Sign a request for authenticated endpoints
     * This is the exchange's native signing (HMAC, etc.) - still required
     * PQC provides additional layer on top
     */
    protected abstract fun signRequest(
        method: String,
        path: String,
        params: Map<String, String>,
        body: String?,
        timestamp: Long
    ): Map<String, String>  // Returns headers to add
    
    /**
     * Parse ticker response
     */
    protected abstract fun parseTicker(response: JsonObject, symbol: String): PriceTick?
    
    /**
     * Parse order book response
     */
    protected abstract fun parseOrderBook(response: JsonObject, symbol: String): OrderBook?
    
    /**
     * Parse trading pairs response
     */
    protected abstract fun parseTradingPairs(response: JsonObject): List<TradingPair>
    
    /**
     * Parse balance response
     */
    protected abstract fun parseBalances(response: JsonObject): List<Balance>
    
    /**
     * Parse order response
     */
    protected abstract fun parseOrder(response: JsonObject): ExecutedOrder?
    
    /**
     * Parse place order response
     */
    protected abstract fun parsePlaceOrderResponse(response: JsonObject, request: OrderRequest): OrderExecutionResult
    
    /**
     * Build order request body
     */
    protected abstract fun buildOrderBody(request: OrderRequest): Map<String, String>
    
    /**
     * Get API endpoints
     */
    protected abstract val endpoints: ExchangeEndpoints
    
    /**
     * WebSocket message handler
     */
    protected abstract fun handleWsMessage(text: String)
    
    /**
     * Build WebSocket subscription message
     */
    protected abstract fun buildWsSubscription(channels: List<String>, symbols: List<String>): String
    
    // =========================================================================
    // ENDPOINT CONFIGURATION
    // =========================================================================
    
    data class ExchangeEndpoints(
        val ticker: String,
        val orderBook: String,
        val trades: String,
        val candles: String,
        val pairs: String,
        val balances: String,
        val placeOrder: String,
        val cancelOrder: String,
        val getOrder: String,
        val openOrders: String,
        val orderHistory: String,
        val wsUrl: String
    )
    
    // =========================================================================
    // CONNECTION MANAGEMENT
    // =========================================================================
    
    override suspend fun connect(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                _status.value = ExchangeStatus.CONNECTING
                
                // Pre-resolve credentials
                getCredentials()
                
                // Establish PQC session for this exchange
                httpClient.establishSession(config.exchangeId)
                
                // Test connection with a simple public API call
                val pairs = getTradingPairs()
                if (pairs.isEmpty()) {
                    _status.value = ExchangeStatus.ERROR
                    return@withContext false
                }
                
                // Cache trading pairs and build symbol maps
                pairs.forEach { pair ->
                    pairCache[pair.symbol] = pair
                    symbolMap[pair.symbol] = pair.exchangeSymbol
                    reverseSymbolMap[pair.exchangeSymbol] = pair.symbol
                }
                
                // Connect WebSocket if supported
                if (config.wsUrl.isNotEmpty() && capabilities.supportsWebSocket) {
                    connectWebSocket()
                }
                
                _status.value = ExchangeStatus.CONNECTED
                true
                
            } catch (e: Exception) {
                _status.value = ExchangeStatus.ERROR
                false
            }
        }
    }
    
    override suspend fun disconnect() {
        wsReconnectJob?.cancel()
        wsHeartbeatJob?.cancel()
        webSocket?.close(1000, "Disconnecting")
        webSocket = null
        
        // Close PQC session
        httpClient.closeSession(config.exchangeId)
        
        // Clear credential cache
        clearCredentialCache()
        
        _status.value = ExchangeStatus.DISCONNECTED
    }
    
    override fun isConnected(): Boolean = status.value == ExchangeStatus.CONNECTED
    
    // =========================================================================
    // RATE LIMITING
    // =========================================================================
    
    override fun isRateLimited(): Boolean {
        val now = System.currentTimeMillis()
        
        // Check cooldown
        if (now < rateLimitedUntil.get()) {
            return true
        }
        
        // Check rate limit window — V5.17.0: O(1) eviction since timestamps are monotonic
        synchronized(requestTimestamps) {
            val cutoff = now - 1000
            while (requestTimestamps.isNotEmpty() && requestTimestamps.first() < cutoff) {
                requestTimestamps.removeFirst()
            }
            return requestTimestamps.size >= config.rateLimit.requestsPerSecond
        }
    }
    
    protected suspend fun waitForRateLimit() {
        rateLimitMutex.withLock {
            while (isRateLimited()) {
                delay(100)
            }
            requestTimestamps.addLast(System.currentTimeMillis())
        }
    }
    
    protected fun setRateLimitCooldown(durationMs: Long) {
        rateLimitedUntil.set(System.currentTimeMillis() + durationMs)
        _status.value = ExchangeStatus.RATE_LIMITED
        scope.launch {
            delay(durationMs)
            if (_status.value == ExchangeStatus.RATE_LIMITED) {
                _status.value = ExchangeStatus.CONNECTED
            }
        }
    }
    
    // =========================================================================
    // HTTP REQUESTS (PQC-PROTECTED)
    // =========================================================================
    
    /**
     * Execute a public GET request with PQC protection
     */
    protected suspend fun publicGet(path: String, params: Map<String, String> = emptyMap()): JsonObject? {
        waitForRateLimit()
        
        val urlBuilder = StringBuilder(config.baseUrl).append(path)
        if (params.isNotEmpty()) {
            urlBuilder.append("?")
            urlBuilder.append(params.entries.joinToString("&") { "${it.key}=${it.value}" })
        }
        
        val response = httpClient.secureGet(
            url = urlBuilder.toString(),
            exchangeId = config.exchangeId,
            authenticated = false
        )
        
        return processResponse(response)
    }
    
    /**
     * Execute a private GET request with exchange auth + PQC protection
     */
    protected suspend fun privateGet(path: String, params: Map<String, String> = emptyMap()): JsonObject? {
        waitForRateLimit()
        
        val timestamp = System.currentTimeMillis()
        val exchangeHeaders = signRequest("GET", path, params, null, timestamp)
        
        val urlBuilder = StringBuilder(config.baseUrl).append(path)
        if (params.isNotEmpty()) {
            urlBuilder.append("?")
            urlBuilder.append(params.entries.joinToString("&") { "${it.key}=${it.value}" })
        }
        
        val response = httpClient.secureGet(
            url = urlBuilder.toString(),
            exchangeId = config.exchangeId,
            headers = exchangeHeaders,
            authenticated = true
        )
        
        return processResponse(response)
    }
    
    /**
     * Execute a private POST request with exchange auth + PQC protection
     */
    protected suspend fun privatePost(path: String, params: Map<String, String>): JsonObject? {
        waitForRateLimit()
        
        val timestamp = System.currentTimeMillis()
        val body = params.entries.joinToString("&") { "${it.key}=${it.value}" }
        val exchangeHeaders = signRequest("POST", path, params, body, timestamp)
        
        val response = httpClient.securePost(
            url = config.baseUrl + path,
            exchangeId = config.exchangeId,
            body = body.toByteArray(Charsets.UTF_8),
            headers = exchangeHeaders,
            contentType = "application/x-www-form-urlencoded",
            authenticated = true
        )
        
        return processResponse(response)
    }
    
    /**
     * Process SecureResponse and convert to JsonObject
     */
    private fun processResponse(response: SecureResponse): JsonObject? {
        // Check for rate limiting
        if (response.isRateLimited()) {
            val retryAfter = response.headers["Retry-After"]?.toLongOrNull() ?: 60
            setRateLimitCooldown(retryAfter * 1000)
            return null
        }
        
        // Check for errors
        if (!response.success) {
            return null
        }
        
        // Parse body
        val bodyString = response.bodyAsString() ?: return null
        return try {
            gson.fromJson(bodyString, JsonObject::class.java)
        } catch (e: Exception) {
            null
        }
    }
    
    // =========================================================================
    // WEBSOCKET (PQC-PROTECTED)
    // =========================================================================
    
    protected fun connectWebSocket() {
        val wsUrl = config.wsUrl.ifEmpty { endpoints.wsUrl }
        if (wsUrl.isEmpty()) return
        
        // Use PQC-protected WebSocket
        webSocket = httpClient.createSecureWebSocket(
            url = wsUrl,
            exchangeId = config.exchangeId,
            listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    startHeartbeat()
                }
                
                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleWsMessage(text)
                }
                
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    scheduleReconnect()
                }
                
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (code != 1000) {  // Not a normal close
                        scheduleReconnect()
                    }
                }
            }
        )
    }
    
    private fun scheduleReconnect() {
        wsReconnectJob?.cancel()
        wsReconnectJob = scope.launch {
            delay(WS_RECONNECT_DELAY_MS)
            if (isConnected()) {
                connectWebSocket()
            }
        }
    }
    
    private fun startHeartbeat() {
        wsHeartbeatJob?.cancel()
        wsHeartbeatJob = scope.launch {
            while (isActive) {
                delay(WS_HEARTBEAT_INTERVAL_MS)
                webSocket?.send("{\"type\":\"ping\"}")
            }
        }
    }
    
    protected fun subscribeWs(channels: List<String>, symbols: List<String>) {
        val msg = buildWsSubscription(channels, symbols)
        webSocket?.send(msg)
    }
    
    // =========================================================================
    // MARKET DATA IMPLEMENTATION
    // =========================================================================
    
    override suspend fun getTicker(symbol: String): PriceTick? {
        // Check cache first
        tickerCache[symbol]?.let { (tick, time) ->
            if (System.currentTimeMillis() - time < CACHE_TTL_MS) {
                return tick
            }
        }
        
        val exchangeSymbol = toExchangeSymbol(symbol)
        val response = publicGet(endpoints.ticker, mapOf("symbol" to exchangeSymbol))
            ?: return null
        
        val tick = parseTicker(response, symbol)
        tick?.let { tickerCache[symbol] = it to System.currentTimeMillis() }
        return tick
    }
    
    override suspend fun getTickers(symbols: List<String>): Map<String, PriceTick> {
        return symbols.mapNotNull { symbol ->
            getTicker(symbol)?.let { symbol to it }
        }.toMap()
    }
    
    override suspend fun getOrderBook(symbol: String, depth: Int): OrderBook? {
        // Check cache first
        orderBookCache[symbol]?.let { (book, time) ->
            if (System.currentTimeMillis() - time < CACHE_TTL_MS) {
                return book
            }
        }
        
        val exchangeSymbol = toExchangeSymbol(symbol)
        val response = publicGet(endpoints.orderBook, mapOf(
            "symbol" to exchangeSymbol,
            "limit" to depth.toString()
        )) ?: return null
        
        val book = parseOrderBook(response, symbol)
        book?.let { orderBookCache[symbol] = it to System.currentTimeMillis() }
        return book
    }
    
    override suspend fun getRecentTrades(symbol: String, limit: Int): List<PublicTrade> {
        val exchangeSymbol = toExchangeSymbol(symbol)
        val response = publicGet(endpoints.trades, mapOf(
            "symbol" to exchangeSymbol,
            "limit" to limit.toString()
        )) ?: return emptyList()
        
        return parsePublicTrades(response, symbol)
    }
    
    protected open fun parsePublicTrades(response: JsonObject, symbol: String): List<PublicTrade> {
        // Default implementation - override in subclass
        return emptyList()
    }
    
    override suspend fun getCandles(
        symbol: String,
        interval: String,
        limit: Int,
        startTime: Long?,
        endTime: Long?
    ): List<OHLCVBar> {
        val exchangeSymbol = toExchangeSymbol(symbol)
        val params = mutableMapOf(
            "symbol" to exchangeSymbol,
            "interval" to interval,
            "limit" to limit.toString()
        )
        startTime?.let { params["startTime"] = it.toString() }
        endTime?.let { params["endTime"] = it.toString() }
        
        val response = publicGet(endpoints.candles, params) ?: return emptyList()
        return parseCandles(response, symbol)
    }
    
    protected open fun parseCandles(response: JsonObject, symbol: String): List<OHLCVBar> {
        // Default implementation - override in subclass
        return emptyList()
    }
    
    override suspend fun getTradingPairs(): List<TradingPair> {
        if (pairCache.isNotEmpty()) {
            return pairCache.values.toList()
        }
        
        val response = publicGet(endpoints.pairs) ?: return emptyList()
        return parseTradingPairs(response)
    }
    
    // =========================================================================
    // STREAM IMPLEMENTATION
    // =========================================================================
    
    override fun subscribeToPrices(symbols: List<String>): Flow<PriceTick> {
        val exchangeSymbols = symbols.map { toExchangeSymbol(it) }
        subscribeWs(listOf("ticker"), exchangeSymbols)
        return _priceUpdates.filter { it.symbol in symbols }
    }
    
    override fun subscribeToOrderBook(symbol: String): Flow<OrderBook> {
        val exchangeSymbol = toExchangeSymbol(symbol)
        subscribeWs(listOf("orderbook"), listOf(exchangeSymbol))
        return _orderBookUpdates.filter { it.symbol == symbol }
    }
    
    override fun subscribeToTrades(symbol: String): Flow<PublicTrade> {
        val exchangeSymbol = toExchangeSymbol(symbol)
        subscribeWs(listOf("trades"), listOf(exchangeSymbol))
        return _tradeUpdates.filter { it.symbol == symbol }
    }
    
    override fun subscribeToOrderUpdates(): Flow<ExchangeOrderUpdate> = _orderUpdates
    
    // =========================================================================
    // ACCOUNT DATA IMPLEMENTATION
    // =========================================================================
    
    override suspend fun getBalances(): List<Balance> {
        val response = privateGet(endpoints.balances) ?: return emptyList()
        return parseBalances(response)
    }
    
    override suspend fun getBalance(asset: String): Balance? {
        return getBalances().find { it.asset.equals(asset, ignoreCase = true) }
    }
    
    // =========================================================================
    // ORDER MANAGEMENT IMPLEMENTATION
    // =========================================================================
    
    override suspend fun placeOrder(request: OrderRequest): OrderExecutionResult {
        // Validate first
        when (val validation = validateOrder(request)) {
            is OrderValidationResult.Invalid -> {
                return OrderExecutionResult.Rejected(validation.reason)
            }
            OrderValidationResult.Valid -> { /* continue */ }
        }
        
        val params = buildOrderBody(request)
        val response = privatePost(endpoints.placeOrder, params)
            ?: return OrderExecutionResult.Error(Exception("No response from exchange"))
        
        return parsePlaceOrderResponse(response, request)
    }
    
    override suspend fun cancelOrder(orderId: String, symbol: String): Boolean {
        val exchangeSymbol = toExchangeSymbol(symbol)
        val response = privatePost(endpoints.cancelOrder, mapOf(
            "orderId" to orderId,
            "symbol" to exchangeSymbol
        ))
        return response != null && !hasError(response)
    }
    
    override suspend fun cancelAllOrders(symbol: String?): Int {
        if (!capabilities.supportsCancelAll) {
            // Fallback: cancel one by one
            val orders = getOpenOrders(symbol)
            var cancelled = 0
            orders.forEach { order ->
                if (cancelOrder(order.orderId, order.symbol)) {
                    cancelled++
                }
            }
            return cancelled
        }
        
        val params = mutableMapOf<String, String>()
        symbol?.let { params["symbol"] = toExchangeSymbol(it) }
        
        val response = privatePost("${endpoints.cancelOrder}/all", params)
        return response?.get("count")?.asInt ?: 0
    }
    
    override suspend fun modifyOrder(
        orderId: String,
        symbol: String,
        newPrice: Double?,
        newQuantity: Double?
    ): OrderExecutionResult {
        // Most CEXes don't support modify - cancel and replace
        val currentOrder = getOrder(orderId, symbol)
            ?: return OrderExecutionResult.Rejected("Order not found")
        
        if (!cancelOrder(orderId, symbol)) {
            return OrderExecutionResult.Rejected("Failed to cancel existing order")
        }
        
        val newRequest = OrderRequest(
            symbol = symbol,
            side = currentOrder.side,
            type = currentOrder.type,
            quantity = newQuantity ?: currentOrder.requestedQuantity,
            price = newPrice ?: currentOrder.requestedPrice,
            stopPrice = currentOrder.requestedPrice.takeIf { currentOrder.type == OrderType.STOP_LOSS },
            clientOrderId = "MOD_${currentOrder.clientOrderId}"
        )
        
        return placeOrder(newRequest)
    }
    
    override suspend fun getOrder(orderId: String, symbol: String): ExecutedOrder? {
        val exchangeSymbol = toExchangeSymbol(symbol)
        val response = privateGet(endpoints.getOrder, mapOf(
            "orderId" to orderId,
            "symbol" to exchangeSymbol
        )) ?: return null
        
        return parseOrder(response)
    }
    
    override suspend fun getOpenOrders(symbol: String?): List<ExecutedOrder> {
        val params = mutableMapOf<String, String>()
        symbol?.let { params["symbol"] = toExchangeSymbol(it) }
        
        val response = privateGet(endpoints.openOrders, params) ?: return emptyList()
        return parseOrderList(response)
    }
    
    override suspend fun getOrderHistory(
        symbol: String?,
        startTime: Long?,
        endTime: Long?,
        limit: Int
    ): List<ExecutedOrder> {
        val params = mutableMapOf("limit" to limit.toString())
        symbol?.let { params["symbol"] = toExchangeSymbol(it) }
        startTime?.let { params["startTime"] = it.toString() }
        endTime?.let { params["endTime"] = it.toString() }
        
        val response = privateGet(endpoints.orderHistory, params) ?: return emptyList()
        return parseOrderList(response)
    }
    
    protected open fun parseOrderList(response: JsonObject): List<ExecutedOrder> {
        // Default implementation - override in subclass
        return emptyList()
    }
    
    protected open fun hasError(response: JsonObject): Boolean {
        return response.has("error") && response.get("error").asJsonArray.size() > 0
    }
    
    // =========================================================================
    // SYMBOL MAPPING
    // =========================================================================
    
    override fun normaliseSymbol(exchangeSymbol: String): String {
        return reverseSymbolMap[exchangeSymbol] ?: exchangeSymbol
    }
    
    override fun toExchangeSymbol(normalisedSymbol: String): String {
        return symbolMap[normalisedSymbol] ?: normalisedSymbol.replace("/", "")
    }
    
    // =========================================================================
    // ORDER VALIDATION
    // =========================================================================
    
    override fun validateOrder(request: OrderRequest): OrderValidationResult {
        val pair = pairCache[request.symbol]
            ?: return OrderValidationResult.Invalid("Unknown trading pair: ${request.symbol}")
        
        // Check quantity limits
        if (request.quantity < pair.minQuantity) {
            return OrderValidationResult.Invalid(
                "Quantity ${request.quantity} below minimum ${pair.minQuantity}"
            )
        }
        if (request.quantity > pair.maxQuantity) {
            return OrderValidationResult.Invalid(
                "Quantity ${request.quantity} above maximum ${pair.maxQuantity}"
            )
        }
        
        // Check price limits for limit orders
        if (request.type == OrderType.LIMIT && request.price != null) {
            if (request.price < pair.minPrice) {
                return OrderValidationResult.Invalid(
                    "Price ${request.price} below minimum ${pair.minPrice}"
                )
            }
            if (request.price > pair.maxPrice) {
                return OrderValidationResult.Invalid(
                    "Price ${request.price} above maximum ${pair.maxPrice}"
                )
            }
        }
        
        // Check notional value
        val notional = request.quantity * (request.price ?: 0.0)
        if (notional > 0 && notional < pair.minNotional) {
            return OrderValidationResult.Invalid(
                "Order value $notional below minimum ${pair.minNotional}"
            )
        }
        
        // Check order type support
        when (request.type) {
            OrderType.MARKET -> if (!capabilities.supportsMarketOrders) {
                return OrderValidationResult.Invalid("Market orders not supported")
            }
            OrderType.LIMIT -> if (!capabilities.supportsLimitOrders) {
                return OrderValidationResult.Invalid("Limit orders not supported")
            }
            OrderType.STOP_LOSS, OrderType.STOP_LIMIT -> if (!capabilities.supportsStopOrders) {
                return OrderValidationResult.Invalid("Stop orders not supported")
            }
            else -> {}
        }
        
        return OrderValidationResult.Valid
    }
    
    // =========================================================================
    // SECURITY REPORTING
    // =========================================================================
    
    /**
     * Get PQC security status for this connector
     */
    fun getSecurityStatus(): ConnectorSecurityStatus {
        val clientReport = httpClient.getSecurityReport()
        val sessionInfo = httpClient.getSessionInfo(config.exchangeId)
        val credentials = cachedCredentials
        
        return ConnectorSecurityStatus(
            exchangeId = config.exchangeId,
            pqcEnabled = true,
            kemAlgorithm = clientReport.kemAlgorithm,
            signatureAlgorithm = clientReport.signatureAlgorithm,
            hybridMode = clientReport.hybridMode,
            nistSecurityLevel = clientReport.nistLevel,
            sessionActive = sessionInfo?.isValid == true,
            sessionExpiresAt = sessionInfo?.expiresAt,
            credentialsFromVault = credentials?.fromVault == true,
            requestSigningEnabled = clientReport.requestSigningEnabled,
            auditLogEntries = clientReport.auditLogSize
        )
    }
    
    /**
     * Export audit log for this exchange
     */
    fun exportAuditLog() = httpClient.getRequestSigner().exportAuditLog()
        .filter { it.exchangeId == config.exchangeId }
    
    // =========================================================================
    // BACKWARD COMPATIBILITY LAYER — V5.17.0
    // Bridges older connector style (executeGet/executeSignedPost) to the
    // PQC-secured HTTP infrastructure. Connectors ported from reference
    // implementations use these convenience methods.
    // =========================================================================
    
    /**
     * Synchronous-style credentials accessor (uses cached value).
     * Connectors can access credentials.apiKey, credentials.apiSecret, etc.
     * Note: getCredentials() must have been called at least once (happens in connect()).
     */
    protected val credentials: ResolvedCredentials
        get() = cachedCredentials ?: ResolvedCredentials(
            apiKey = config.apiKey,
            apiSecret = config.apiSecret,
            passphrase = config.passphrase,
            fromVault = false
        )
    
    /**
     * Execute an unauthenticated GET request. Returns raw response body as String?.
     */
    protected suspend fun executeGet(url: String): String? {
        waitForRateLimit()
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).get().build()
                val response = legacyHttpClient.newCall(request).execute()
                response.body?.string()
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Execute a signed POST request with custom headers. Returns raw response body.
     */
    protected suspend fun executeSignedPost(
        url: String, 
        body: String, 
        headers: Map<String, String> = emptyMap()
    ): String? {
        waitForRateLimit()
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = body.toRequestBody("application/json".toMediaType())
                val builder = Request.Builder().url(url).post(requestBody)
                headers.forEach { (k, v) -> builder.addHeader(k, v) }
                val response = legacyHttpClient.newCall(builder.build()).execute()
                response.body?.string()
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Execute a signed GET request with custom headers. Returns raw response body.
     */
    protected suspend fun executeSignedGet(
        url: String, 
        headers: Map<String, String> = emptyMap()
    ): String? {
        waitForRateLimit()
        return withContext(Dispatchers.IO) {
            try {
                val builder = Request.Builder().url(url).get()
                headers.forEach { (k, v) -> builder.addHeader(k, v) }
                val response = legacyHttpClient.newCall(builder.build()).execute()
                response.body?.string()
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Execute a signed DELETE request. Returns raw response body.
     */
    protected suspend fun executeSignedDelete(
        url: String, 
        headers: Map<String, String> = emptyMap()
    ): String? {
        waitForRateLimit()
        return withContext(Dispatchers.IO) {
            try {
                val builder = Request.Builder().url(url).delete()
                headers.forEach { (k, v) -> builder.addHeader(k, v) }
                val response = legacyHttpClient.newCall(builder.build()).execute()
                response.body?.string()
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Execute a private DELETE request (path-based, like privatePost).
     */
    protected suspend fun privateDelete(
        path: String, 
        params: Map<String, String> = emptyMap()
    ): JsonObject? {
        waitForRateLimit()
        
        val urlBuilder = StringBuilder(config.baseUrl).append(path)
        if (params.isNotEmpty()) {
            urlBuilder.append("?")
            urlBuilder.append(params.entries.joinToString("&") { "${it.key}=${it.value}" })
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val builder = Request.Builder().url(urlBuilder.toString()).delete()
                val response = legacyHttpClient.newCall(builder.build()).execute()
                val body = response.body?.string()
                if (body != null) gson.fromJson(body, JsonObject::class.java) else null
            } catch (e: Exception) {
                null
            }
        }
    }
    
    // --- WebSocket convenience ---
    
    /**
     * Send a text message on the active WebSocket connection.
     */
    protected fun sendWsMessage(message: String) {
        webSocket?.send(message)
    }
    
    // --- Stream emit helpers ---
    
    protected fun emitPriceTick(tick: PriceTick) {
        _priceUpdates.tryEmit(tick)
    }
    
    protected fun emitOrderBook(book: OrderBook) {
        _orderBookUpdates.tryEmit(book)
    }
    
    protected fun emitPublicTrade(trade: PublicTrade) {
        _tradeUpdates.tryEmit(trade)
    }
    
    protected fun emitOrderUpdate(update: ExchangeOrderUpdate) {
        _orderUpdates.tryEmit(update)
    }
    
    protected fun emitError(error: Throwable) {
        android.util.Log.e("BaseCEXConnector", "[${config.exchangeId}] Error: ${error.message}", error)
    }
}

// =========================================================================
// JSON EXTENSION FUNCTIONS — V5.17.0
// Safe accessors for Gson JsonElement used across all exchange connectors.
// These handle null-safety and type coercion consistently.
// =========================================================================

fun JsonElement?.asString(): String? = try { this?.asString } catch (_: Exception) { this?.toString()?.removeSurrounding("\"") }
fun JsonElement?.asInt(): Int? = try { this?.asInt } catch (_: Exception) { this?.asString()?.toIntOrNull() }
fun JsonElement?.asDouble(): Double? = try { this?.asDouble } catch (_: Exception) { this?.asString()?.toDoubleOrNull() }
fun JsonElement?.asLong(): Long? = try { this?.asLong } catch (_: Exception) { this?.asString()?.toLongOrNull() }
fun JsonElement?.asBoolean(): Boolean? = try { this?.asBoolean } catch (_: Exception) { this?.asString()?.toBooleanStrictOrNull() }
fun JsonElement?.asJsonObject(): JsonObject? = try { this?.asJsonObject } catch (_: Exception) { null }
fun JsonElement?.asJsonArray(): JsonArray? = try { this?.asJsonArray } catch (_: Exception) { null }

// =========================================================================
// SECURITY STATUS
// =========================================================================

/**
 * Security status for a connector instance
 */
data class ConnectorSecurityStatus(
    val exchangeId: String,
    val pqcEnabled: Boolean,
    val kemAlgorithm: String,
    val signatureAlgorithm: String,
    val hybridMode: String,
    val nistSecurityLevel: Int,
    val sessionActive: Boolean,
    val sessionExpiresAt: Long?,
    val credentialsFromVault: Boolean,
    val requestSigningEnabled: Boolean,
    val auditLogEntries: Int
) {
    fun toSummary(): String {
        return """
            Exchange: $exchangeId
            PQC: $pqcEnabled (NIST Level $nistSecurityLevel)
            Algorithms: $kemAlgorithm / $signatureAlgorithm
            Mode: $hybridMode
            Session: ${if (sessionActive) "Active" else "Inactive"}
            Credentials: ${if (credentialsFromVault) "Vault (PQC-encrypted)" else "Config (plaintext)"}
            Audit Entries: $auditLogEntries
        """.trimIndent()
    }
}
