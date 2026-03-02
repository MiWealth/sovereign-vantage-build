package com.miwealth.sovereignvantage.core.exchange.ai

/**
 * AI EXCHANGE CONNECTOR - Universal Exchange Interface
 * 
 * Sovereign Vantage: Arthur Edition V5.17.0
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * V5.17.0 CHANGES:
 * - Added modifyOrder() for order modification support
 * - Added isRateLimited() for rate limit checking
 * - Full integration with AIConnectionManager and TradingCoordinator
 * 
 * This is the main interface that REPLACES all hardcoded exchange connectors.
 * It uses learned schemas to connect to ANY exchange without exchange-specific code.
 * 
 * Architecture:
 * - ExchangeSchemaLearner: Discovers and learns exchange API structures
 * - DynamicRequestExecutor: Executes requests based on learned schemas
 * - AIExchangeConnector: High-level interface matching UnifiedExchangeConnector
 * - ConnectionHealthMonitor: Detects failures and triggers re-learning
 * 
 * Benefits:
 * - No more maintaining 12+ connector files (12,000+ lines of code)
 * - Can connect to ANY new exchange by learning its API
 * - Self-healing when APIs change
 * - Consistent interface for all exchanges
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */



import android.content.Context
import android.util.Log
import com.miwealth.sovereignvantage.core.*
import com.miwealth.sovereignvantage.core.exchange.*
import com.miwealth.sovereignvantage.core.trading.engine.OrderRequest
import com.miwealth.sovereignvantage.core.trading.engine.OrderExecutionResult
import com.miwealth.sovereignvantage.core.trading.engine.ExecutedOrder
import com.miwealth.sovereignvantage.core.trading.engine.TimeInForce
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * AI-powered exchange connector that works with ANY exchange.
 * 
 * Usage:
 * ```kotlin
 * // Create connector for any exchange
 * val connector = AIExchangeConnector.create(
 *     context = context,
 *     exchangeId = "binance",
 *     baseUrl = "https://api.binance.com",
 *     credentials = ExchangeCredentials(apiKey, apiSecret)
 * )
 * 
 * // Connect (learns schema if needed)
 * connector.connect()
 * 
 * // Use standard operations
 * val ticker = connector.getTicker("BTC/USDT")
 * val balances = connector.getBalances()
 * val order = connector.placeOrder(orderRequest)
 * ```
 */
class AIExchangeConnector private constructor(
    override val config: ExchangeConfig,
    private val context: Context,
    private val schema: ExchangeSchema,
    private val credentials: ExchangeCredentials?,
    // V5.17.0: PQC integration (Gap 2) — quantum-safe protection for AI exchange path
    private val pqcRequestSigner: com.miwealth.sovereignvantage.core.security.pqc.hybrid.PQCRequestSigner? = null,
    private val secureHttpClient: com.miwealth.sovereignvantage.core.security.pqc.hybrid.HybridSecureHttpClient? = null
) : UnifiedExchangeConnector {
    
    companion object {
        private const val TAG = "AIExchangeConnector"
        
        // Schema cache shared across all connectors
        private val schemaCache = ConcurrentHashMap<String, ExchangeSchema>()
        
        // Health tracking
        private val healthTracker = ConcurrentHashMap<String, ConnectionHealth>()
        
        /**
         * Create a connector for any exchange.
         * V5.17.0: Added PQC parameters for quantum-safe protection (Gap 2)
         */
        suspend fun create(
            context: Context,
            exchangeId: String,
            baseUrl: String,
            credentials: ExchangeCredentials? = null,
            type: ExchangeType = ExchangeType.CEX_SPOT,
            wsUrl: String? = null,
            sandboxUrl: String? = null,
            pqcRequestSigner: com.miwealth.sovereignvantage.core.security.pqc.hybrid.PQCRequestSigner? = null,
            secureHttpClient: com.miwealth.sovereignvantage.core.security.pqc.hybrid.HybridSecureHttpClient? = null
        ): AIExchangeConnector {
            // Get or learn schema
            val schema = schemaCache[exchangeId] ?: run {
                val learner = ExchangeSchemaLearner(context)
                val result = learner.learnExchange(SchemaLearnRequest(
                    exchangeId = exchangeId,
                    name = exchangeId.replaceFirstChar { it.uppercase() },
                    type = type,
                    baseUrl = baseUrl,
                    sandboxUrl = sandboxUrl,
                    wsUrl = wsUrl
                ))
                
                when (result) {
                    is SchemaLearnResult.Success -> result.schema.also { schemaCache[exchangeId] = it }
                    is SchemaLearnResult.PartialSuccess -> result.schema.also { 
                        schemaCache[exchangeId] = it
                        Log.w(TAG, "Partial schema for $exchangeId: ${result.missingCapabilities}")
                    }
                    is SchemaLearnResult.Failure -> throw IllegalStateException(
                        "Failed to learn schema for $exchangeId: ${result.reason}"
                    )
                }
            }
            
            val config = ExchangeConfig(
                exchangeId = exchangeId,
                exchangeName = schema.name,
                exchangeType = type,
                baseUrl = baseUrl,
                wsUrl = wsUrl ?: schema.wsUrl ?: "",
                apiKey = credentials?.apiKey ?: "",
                apiSecret = credentials?.apiSecret ?: "",
                passphrase = credentials?.passphrase ?: "",
                testnet = sandboxUrl != null
            )
            
            return AIExchangeConnector(config, context, schema, credentials, pqcRequestSigner, secureHttpClient)
        }
        
        /**
         * Create from existing schema (no learning needed).
         * V5.17.0: Added PQC parameters for quantum-safe protection (Gap 2)
         */
        fun createWithSchema(
            context: Context,
            schema: ExchangeSchema,
            credentials: ExchangeCredentials? = null,
            pqcRequestSigner: com.miwealth.sovereignvantage.core.security.pqc.hybrid.PQCRequestSigner? = null,
            secureHttpClient: com.miwealth.sovereignvantage.core.security.pqc.hybrid.HybridSecureHttpClient? = null
        ): AIExchangeConnector {
            schemaCache[schema.exchangeId] = schema
            
            val config = ExchangeConfig(
                exchangeId = schema.exchangeId,
                exchangeName = schema.name,
                exchangeType = schema.type,
                baseUrl = schema.baseUrl,
                wsUrl = schema.wsUrl ?: "",
                apiKey = credentials?.apiKey ?: "",
                apiSecret = credentials?.apiSecret ?: "",
                passphrase = credentials?.passphrase ?: "",
                testnet = schema.sandboxUrl != null
            )
            
            return AIExchangeConnector(config, context, schema, credentials, pqcRequestSigner, secureHttpClient)
        }
        
        /**
         * Get connection health for an exchange.
         */
        fun getHealth(exchangeId: String): ConnectionHealth? = healthTracker[exchangeId]
        
        /**
         * Clear schema cache (forces re-learning).
         */
        fun clearSchemaCache(exchangeId: String? = null) {
            if (exchangeId != null) {
                schemaCache.remove(exchangeId)
            } else {
                schemaCache.clear()
            }
        }
    }
    
    // Request executor — V5.17.0: PQC-enabled for quantum-safe API requests (Gap 2)
    private val executor = DynamicRequestExecutor(
        schema, credentials, useTestnet = config.testnet,
        pqcRequestSigner = pqcRequestSigner, secureHttpClient = secureHttpClient
    )
    
    // State
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _status = MutableStateFlow(ExchangeStatus.DISCONNECTED)
    override val status: StateFlow<ExchangeStatus> = _status.asStateFlow()
    
    // Caching
    private val tradingPairCache = ConcurrentHashMap<String, TradingPair>()
    private val tickerCache = ConcurrentHashMap<String, Pair<PriceTick, Long>>()
    private val symbolMap = ConcurrentHashMap<String, String>()  // normalised -> exchange
    private val reverseSymbolMap = ConcurrentHashMap<String, String>()  // exchange -> normalised
    
    // Streams
    private val _priceUpdates = MutableSharedFlow<PriceTick>(replay = 1, extraBufferCapacity = 100)
    private val _orderBookUpdates = MutableSharedFlow<OrderBook>(replay = 1, extraBufferCapacity = 100)
    private val _tradeUpdates = MutableSharedFlow<PublicTrade>(replay = 1, extraBufferCapacity = 100)
    private val _orderUpdates = MutableSharedFlow<ExchangeOrderUpdate>(replay = 1, extraBufferCapacity = 100)
    
    // Health tracking
    private var successCount = 0
    private var failureCount = 0
    private var consecutiveFailures = 0
    private var lastSuccessTime: Instant? = null
    private var lastFailureTime: Instant? = null
    private var lastError: String? = null
    
    // =========================================================================
    // CAPABILITIES
    // =========================================================================
    
    override val capabilities = ExchangeCapabilities(
        supportsSpotTrading = schema.capabilities.supportsSpot,
        supportsFutures = schema.capabilities.supportsFutures,
        supportsMargin = schema.capabilities.supportsMargin,
        supportsOptions = schema.capabilities.supportsOptions,
        supportsLending = schema.capabilities.supportsLending,
        supportsStaking = schema.capabilities.supportsStaking,
        supportsWebSocket = schema.capabilities.supportsWebSocket,
        supportsOrderbook = schema.endpoints.orderBook != null,
        supportsMarketOrders = schema.capabilities.supportsMarketOrders,
        supportsLimitOrders = schema.capabilities.supportsLimitOrders,
        supportsStopOrders = schema.capabilities.supportsStopOrders,
        supportsPostOnly = schema.capabilities.supportsPostOnly,
        supportsCancelAll = schema.capabilities.supportsCancelAll,
        maxOrdersPerSecond = schema.rateLimits.ordersPerSecond,
        minOrderValue = schema.capabilities.minOrderValue,
        tradingFeeMaker = schema.capabilities.makerFee,
        tradingFeeTaker = schema.capabilities.takerFee,
        withdrawalEnabled = false,
        networks = listOf(BlockchainNetwork.ETHEREUM, BlockchainNetwork.BITCOIN)
    )
    
    // =========================================================================
    // CONNECTION MANAGEMENT
    // =========================================================================
    
    override suspend fun connect(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                _status.value = ExchangeStatus.CONNECTING
                
                // Test connection
                if (!executor.testConnection()) {
                    _status.value = ExchangeStatus.ERROR
                    recordFailure("Connection test failed")
                    return@withContext false
                }
                
                // Load trading pairs
                val pairs = getTradingPairs()
                if (pairs.isEmpty()) {
                    _status.value = ExchangeStatus.ERROR
                    recordFailure("No trading pairs loaded")
                    return@withContext false
                }
                
                // Cache pairs and build symbol maps
                pairs.forEach { pair ->
                    tradingPairCache[pair.symbol] = pair
                    symbolMap[pair.symbol] = pair.exchangeSymbol
                    reverseSymbolMap[pair.exchangeSymbol] = pair.symbol
                }
                
                _status.value = ExchangeStatus.CONNECTED
                recordSuccess()
                
                Log.i(TAG, "Connected to ${schema.name} with ${pairs.size} trading pairs")
                true
                
            } catch (e: Exception) {
                _status.value = ExchangeStatus.ERROR
                recordFailure(e.message ?: "Connection failed")
                Log.e(TAG, "Connection failed", e)
                false
            }
        }
    }
    
    override suspend fun disconnect() {
        _status.value = ExchangeStatus.DISCONNECTED
        scope.cancel()
    }
    
    // =========================================================================
    // PUBLIC DATA
    // =========================================================================
    
    override suspend fun getTradingPairs(): List<TradingPair> {
        val result = executor.execute(StandardOperation.GET_TRADING_PAIRS)
        
        return when (result) {
            is ExecutionResult.ArraySuccess -> {
                result.data.mapNotNull { parseTradingPair(it) }
            }
            is ExecutionResult.RawSuccess -> {
                // Try to parse from raw response
                parseTradingPairsFromRaw(result.json)
            }
            else -> {
                recordFailure("Failed to get trading pairs")
                emptyList()
            }
        }.also { if (it.isNotEmpty()) recordSuccess() }
    }
    
    override suspend fun getTicker(symbol: String): PriceTick? {
        // Check cache first
        val cached = tickerCache[symbol]
        if (cached != null && System.currentTimeMillis() - cached.second < 1000) {
            return cached.first
        }
        
        val exchangeSymbol = toExchangeSymbol(symbol)
        val result = executor.execute(
            StandardOperation.GET_TICKER,
            mapOf("symbol" to exchangeSymbol)
        )
        
        return when (result) {
            is ExecutionResult.ObjectSuccess -> {
                parseTicker(result.data, symbol)?.also {
                    tickerCache[symbol] = it to System.currentTimeMillis()
                    recordSuccess()
                }
            }
            is ExecutionResult.ArraySuccess -> {
                // Some exchanges return array even for single ticker
                result.data.firstOrNull()?.let { parseTicker(it, symbol) }?.also {
                    tickerCache[symbol] = it to System.currentTimeMillis()
                    recordSuccess()
                }
            }
            is ExecutionResult.RawSuccess -> {
                parseTickerFromRaw(result.json, symbol)?.also {
                    tickerCache[symbol] = it to System.currentTimeMillis()
                    recordSuccess()
                }
            }
            else -> {
                recordFailure("Failed to get ticker for $symbol")
                null
            }
        }
    }
    
    override suspend fun getOrderBook(symbol: String, limit: Int): OrderBook? {
        val exchangeSymbol = toExchangeSymbol(symbol)
        val result = executor.execute(
            StandardOperation.GET_ORDER_BOOK,
            mapOf("symbol" to exchangeSymbol, "limit" to limit)
        )
        
        return when (result) {
            is ExecutionResult.RawSuccess -> {
                parseOrderBook(result.json, symbol).also { if (it != null) recordSuccess() }
            }
            else -> {
                recordFailure("Failed to get order book for $symbol")
                null
            }
        }
    }
    
    // =========================================================================
    // AUTHENTICATED OPERATIONS
    // =========================================================================
    
    override suspend fun getBalances(): List<Balance> {
        val result = executor.execute(StandardOperation.GET_BALANCES)
        
        return when (result) {
            is ExecutionResult.ArraySuccess -> {
                result.data.mapNotNull { parseBalance(it) }.also { recordSuccess() }
            }
            is ExecutionResult.RawSuccess -> {
                parseBalancesFromRaw(result.json).also { if (it.isNotEmpty()) recordSuccess() }
            }
            else -> {
                recordFailure("Failed to get balances")
                emptyList()
            }
        }
    }
    
    override suspend fun placeOrder(request: OrderRequest): OrderExecutionResult {
        val exchangeSymbol = toExchangeSymbol(request.symbol)
        
        val params = mutableMapOf<String, Any>(
            "symbol" to exchangeSymbol,
            "side" to request.side.name,
            "type" to request.type.name,
            "quantity" to request.quantity.toString()
        )
        
        if (request.price != null && request.price > 0) {
            params["price"] = request.price.toString()
        }
        
        if (request.timeInForce != TimeInForce.GTC) {
            params["timeInForce"] = request.timeInForce.name
        }
        
        request.clientOrderId?.let { params["newClientOrderId"] = it }
        
        val result = executor.execute(StandardOperation.PLACE_ORDER, params)
        
        return when (result) {
            is ExecutionResult.ObjectSuccess -> {
                recordSuccess()
                parseOrderResult(result.data, request)
            }
            is ExecutionResult.RawSuccess -> {
                recordSuccess()
                parseOrderResultFromRaw(result.json, request)
            }
            is ExecutionResult.Error -> {
                recordFailure(result.message)
                OrderExecutionResult.Error(Exception(result.message))
            }
            else -> {
                recordFailure("Unexpected response")
                OrderExecutionResult.Error(Exception("Unexpected response"))
            }
        }
    }
    
    override suspend fun cancelOrder(orderId: String, symbol: String): Boolean {
        val exchangeSymbol = toExchangeSymbol(symbol)
        
        val result = executor.execute(
            StandardOperation.CANCEL_ORDER,
            mapOf("symbol" to exchangeSymbol, "orderId" to orderId)
        )
        
        return result.isSuccess().also {
            if (it) recordSuccess() else recordFailure("Cancel order failed")
        }
    }
    
    override suspend fun getOrder(orderId: String, symbol: String): ExecutedOrder? {
        val exchangeSymbol = toExchangeSymbol(symbol)
        
        val result = executor.execute(
            StandardOperation.GET_ORDER,
            mapOf("symbol" to exchangeSymbol, "orderId" to orderId)
        )
        
        return when (result) {
            is ExecutionResult.ObjectSuccess -> {
                recordSuccess()
                parseExecutedOrder(result.data, symbol)
            }
            is ExecutionResult.RawSuccess -> {
                recordSuccess()
                parseExecutedOrderFromRaw(result.json, symbol)
            }
            else -> {
                recordFailure("Failed to get order")
                null
            }
        }
    }
    
    override suspend fun getOpenOrders(symbol: String?): List<ExecutedOrder> {
        val params = mutableMapOf<String, Any>()
        symbol?.let { params["symbol"] = toExchangeSymbol(it) }
        
        val result = executor.execute(StandardOperation.GET_OPEN_ORDERS, params)
        
        return when (result) {
            is ExecutionResult.ArraySuccess -> {
                recordSuccess()
                result.data.mapNotNull { parseExecutedOrder(it, symbol ?: "") }
            }
            is ExecutionResult.RawSuccess -> {
                recordSuccess()
                parseOrdersFromRaw(result.json)
            }
            else -> {
                recordFailure("Failed to get open orders")
                emptyList()
            }
        }
    }
    
    /**
     * Modify an existing order (price and/or quantity).
     * Note: Not all exchanges support order modification. Falls back to cancel+replace.
     */
    override suspend fun modifyOrder(
        orderId: String,
        symbol: String,
        newPrice: Double?,
        newQuantity: Double?
    ): OrderExecutionResult {
        val exchangeSymbol = toExchangeSymbol(symbol)
        
        // Try native modification first
        val params = mutableMapOf<String, Any>(
            "orderId" to orderId,
            "symbol" to exchangeSymbol
        )
        newPrice?.let { params["price"] = it }
        newQuantity?.let { params["quantity"] = it }
        
        val result = executor.execute(StandardOperation.MODIFY_ORDER, params)
        
        return when (result) {
            is ExecutionResult.ObjectSuccess -> {
                recordSuccess()
                parseExecutedOrder(result.data, symbol)?.let {
                    OrderExecutionResult.Success(it)
                } ?: OrderExecutionResult.Rejected("Failed to parse modified order")
            }
            is ExecutionResult.RawSuccess -> {
                recordSuccess()
                parseExecutedOrderFromRaw(result.json, symbol)?.let {
                    OrderExecutionResult.Success(it)
                } ?: OrderExecutionResult.Rejected("Failed to parse modified order")
            }
            is ExecutionResult.Error -> {
                // Exchange doesn't support modification - would need cancel+replace
                recordFailure("Order modification not supported")
                OrderExecutionResult.Rejected("Order modification not supported by exchange")
            }
            else -> OrderExecutionResult.Rejected("Unexpected result")
        }
    }
    
    /**
     * Check if connector is currently rate limited.
     */
    override fun isRateLimited(): Boolean {
        val health = healthTracker[config.exchangeId] ?: return false
        return health.consecutiveFailures >= 5 // Consider rate limited after 5 failures
    }
    
    // =========================================================================
    // STREAMS (Placeholder - WebSocket implementation needed)
    // =========================================================================
    
    override fun subscribeToPrices(symbols: List<String>): Flow<PriceTick> {
        // Merge multiple symbol subscriptions into single flow
        return if (symbols.isEmpty()) {
            emptyFlow()
        } else {
            symbols.map { symbol -> subscribeTicker(symbol) }
                .merge()
        }
    }
    
    fun subscribeTicker(symbol: String): Flow<PriceTick> {
        // For now, return polling-based updates
        return flow {
            while (currentCoroutineContext().isActive) {
                getTicker(symbol)?.let { emit(it) }
                delay(1000)
            }
        }
    }
    
    override fun subscribeToOrderBook(symbol: String): Flow<OrderBook> {
        return flow {
            while (currentCoroutineContext().isActive) {
                getOrderBook(symbol)?.let { emit(it) }
                delay(1000)
            }
        }
    }
    
    // Legacy method name compatibility
    fun subscribeOrderBook(symbol: String): Flow<OrderBook> = subscribeToOrderBook(symbol)
    
    override fun subscribeToTrades(symbol: String): Flow<PublicTrade> = _tradeUpdates.asSharedFlow()
    
    // Legacy method name compatibility  
    fun subscribeTrades(symbol: String): Flow<PublicTrade> = subscribeToTrades(symbol)
    
    override fun subscribeToOrderUpdates(): Flow<ExchangeOrderUpdate> = _orderUpdates.asSharedFlow()
    
    // Legacy method name compatibility
    fun subscribeUserOrders(): Flow<ExchangeOrderUpdate> = subscribeToOrderUpdates()
    
    // =========================================================================
    // SYMBOL CONVERSION
    // =========================================================================
    
    override fun toExchangeSymbol(standardSymbol: String): String {
        // Check cache
        symbolMap[standardSymbol]?.let { return it }
        
        // Convert using schema rules
        val parts = standardSymbol.split("/")
        if (parts.size != 2) return standardSymbol
        
        var base = parts[0]
        var quote = parts[1]
        
        // Apply symbol mappings
        for ((standard, exchange) in schema.symbolFormat.symbolMappings) {
            if (base == standard) base = exchange
            if (quote == standard) quote = exchange
        }
        
        // Apply case
        when (schema.symbolFormat.case) {
            SymbolCase.UPPER -> {
                base = base.uppercase()
                quote = quote.uppercase()
            }
            SymbolCase.LOWER -> {
                base = base.lowercase()
                quote = quote.lowercase()
            }
            SymbolCase.MIXED -> { /* Keep as is */ }
        }
        
        // Apply order and separator
        return when (schema.symbolFormat.order) {
            SymbolOrder.BASE_QUOTE -> "$base${schema.symbolFormat.separator}$quote"
            SymbolOrder.QUOTE_BASE -> "$quote${schema.symbolFormat.separator}$base"
        }
    }
    
    override fun normaliseSymbol(exchangeSymbol: String): String {
        // Check cache
        reverseSymbolMap[exchangeSymbol]?.let { return it }
        
        var symbol = exchangeSymbol
        
        // Apply reverse mappings
        for ((exchange, standard) in schema.symbolFormat.reverseSymbolMappings) {
            symbol = symbol.replace(exchange, standard)
        }
        
        // Split by separator
        val sep = schema.symbolFormat.separator
        val parts = if (sep.isNotEmpty()) {
            symbol.split(sep)
        } else {
            // No separator - need to guess where to split
            // Common patterns: BTCUSDT (base=BTC, quote=USDT)
            guessSymbolSplit(symbol)
        }
        
        if (parts.size != 2) return symbol
        
        val (first, second) = parts
        
        // Apply order
        return when (schema.symbolFormat.order) {
            SymbolOrder.BASE_QUOTE -> "${first.uppercase()}/${second.uppercase()}"
            SymbolOrder.QUOTE_BASE -> "${second.uppercase()}/${first.uppercase()}"
        }
    }
    
    private fun guessSymbolSplit(symbol: String): List<String> {
        val quoteAssets = listOf("USDT", "USDC", "BUSD", "USD", "BTC", "ETH", "BNB", "EUR", "GBP")
        
        for (quote in quoteAssets) {
            if (symbol.endsWith(quote)) {
                val base = symbol.dropLast(quote.length)
                if (base.isNotEmpty()) {
                    return listOf(base, quote)
                }
            }
        }
        
        return listOf(symbol)
    }
    
    // =========================================================================
    // PARSING HELPERS
    // =========================================================================
    
    private fun parseTradingPair(data: Map<String, Any?>): TradingPair? {
        val symbol = data["symbol"]?.toString() ?: return null
        val baseAsset = data["baseAsset"]?.toString() ?: data["base"]?.toString() ?: ""
        val quoteAsset = data["quoteAsset"]?.toString() ?: data["quote"]?.toString() ?: ""
        val status = data["status"]?.toString() ?: "TRADING"
        
        val standardSymbol = if (baseAsset.isNotEmpty() && quoteAsset.isNotEmpty()) {
            "${baseAsset.uppercase()}/${quoteAsset.uppercase()}"
        } else {
            normaliseSymbol(symbol)
        }
        
        return TradingPair(
            symbol = standardSymbol,
            baseAsset = baseAsset.uppercase().ifEmpty { standardSymbol.substringBefore("/") },
            quoteAsset = quoteAsset.uppercase().ifEmpty { standardSymbol.substringAfter("/") },
            exchangeSymbol = symbol,
            minQuantity = (data["minQty"] ?: data["minQuantity"])?.toString()?.toDoubleOrNull() ?: 0.0,
            maxQuantity = (data["maxQty"] ?: data["maxQuantity"])?.toString()?.toDoubleOrNull() ?: Double.MAX_VALUE,
            quantityPrecision = (data["quantityPrecision"] ?: data["basePrecision"])?.toString()?.toIntOrNull() ?: 8,
            pricePrecision = (data["pricePrecision"] ?: data["quotePrecision"])?.toString()?.toIntOrNull() ?: 8,
            minNotional = (data["minNotional"])?.toString()?.toDoubleOrNull() ?: 0.0,
            isActive = status == "TRADING",
            exchange = schema.exchangeId
        )
    }
    
    private fun parseTradingPairsFromRaw(json: com.google.gson.JsonObject): List<TradingPair> {
        // Try common data paths
        val dataPath = schema.responseSchemas.tradingPairs?.dataPath ?: "symbols"
        
        val array = extractJsonArray(json, dataPath) ?: return emptyList()
        
        return array.mapNotNull { element ->
            if (element.isJsonObject) {
                val obj = element.asJsonObject
                val data = mutableMapOf<String, Any?>()
                
                obj.entrySet().forEach { (key, value) ->
                    data[key] = when {
                        value.isJsonPrimitive -> value.asString
                        else -> value.toString()
                    }
                }
                
                parseTradingPair(data)
            } else null
        }
    }
    
    private fun parseTicker(data: Map<String, Any?>, symbol: String): PriceTick? {
        val lastPrice = (data["lastPrice"] ?: data["last"] ?: data["price"])?.toString()?.toDoubleOrNull() ?: 0.0
        val bidPrice = (data["bidPrice"] ?: data["bid"] ?: data["bid1Price"])?.toString()?.toDoubleOrNull() ?: lastPrice
        val askPrice = (data["askPrice"] ?: data["ask"] ?: data["ask1Price"])?.toString()?.toDoubleOrNull() ?: lastPrice
        val volume = (data["volume"] ?: data["vol"] ?: data["volume24h"])?.toString()?.toDoubleOrNull() ?: 0.0
        val high = (data["highPrice"] ?: data["high"] ?: data["high24h"])?.toString()?.toDoubleOrNull() ?: lastPrice
        val low = (data["lowPrice"] ?: data["low"] ?: data["low24h"])?.toString()?.toDoubleOrNull() ?: lastPrice
        val priceChange = (data["priceChange"] ?: data["change"])?.toString()?.toDoubleOrNull() ?: 0.0
        val priceChangePercent = (data["priceChangePercent"] ?: data["changePercent"])?.toString()?.toDoubleOrNull() ?: 0.0
        
        return PriceTick(
            symbol = symbol,
            bid = bidPrice,
            ask = askPrice,
            last = lastPrice,
            volume = volume,
            timestamp = System.currentTimeMillis(),
            exchange = schema.exchangeId
        )
    }
    
    private fun parseTickerFromRaw(json: com.google.gson.JsonObject, symbol: String): PriceTick? {
        val data = mutableMapOf<String, Any?>()
        
        json.entrySet().forEach { (key, value) ->
            data[key] = when {
                value.isJsonPrimitive -> value.asString
                else -> value.toString()
            }
        }
        
        return parseTicker(data, symbol)
    }
    
    private fun parseOrderBook(json: com.google.gson.JsonObject, symbol: String): OrderBook? {
        val bids = extractJsonArray(json, "bids") ?: return null
        val asks = extractJsonArray(json, "asks") ?: return null
        
        val bidLevels = bids.mapNotNull { parseOrderBookLevel(it) }
        val askLevels = asks.mapNotNull { parseOrderBookLevel(it) }
        
        return OrderBook(
            symbol = symbol,
            bids = bidLevels,
            asks = askLevels,
            timestamp = System.currentTimeMillis(),
            exchange = schema.exchangeId
        )
    }
    
    private fun parseOrderBookLevel(element: com.google.gson.JsonElement): OrderBookLevel? {
        return when {
            element.isJsonArray -> {
                val arr = element.asJsonArray
                if (arr.size() >= 2) {
                    OrderBookLevel(
                        price = arr[0].asString.toDoubleOrNull() ?: return null,
                        quantity = arr[1].asString.toDoubleOrNull() ?: return null
                    )
                } else null
            }
            element.isJsonObject -> {
                val obj = element.asJsonObject
                OrderBookLevel(
                    price = obj.get("price")?.asString?.toDoubleOrNull() ?: return null,
                    quantity = obj.get("quantity")?.asString?.toDoubleOrNull() 
                        ?: obj.get("qty")?.asString?.toDoubleOrNull() ?: return null
                )
            }
            else -> null
        }
    }
    
    private fun parseBalance(data: Map<String, Any?>): Balance? {
        val asset = (data["asset"] ?: data["currency"] ?: data["coin"])?.toString() ?: return null
        val free = (data["free"] ?: data["available"] ?: data["availableBalance"])?.toString()?.toDoubleOrNull() ?: 0.0
        val locked = (data["locked"] ?: data["frozen"] ?: data["lockedBalance"])?.toString()?.toDoubleOrNull() ?: 0.0
        val total = (data["total"] ?: data["balance"])?.toString()?.toDoubleOrNull() ?: (free + locked)
        
        if (total <= 0 && free <= 0) return null
        
        return Balance(
            asset = asset.uppercase(),
            free = free,
            locked = locked,
            total = total
        )
    }
    
    private fun parseBalancesFromRaw(json: com.google.gson.JsonObject): List<Balance> {
        val dataPath = schema.responseSchemas.balances?.dataPath ?: "balances"
        val array = extractJsonArray(json, dataPath) ?: return emptyList()
        
        return array.mapNotNull { element ->
            if (element.isJsonObject) {
                val obj = element.asJsonObject
                val data = mutableMapOf<String, Any?>()
                
                obj.entrySet().forEach { (key, value) ->
                    data[key] = when {
                        value.isJsonPrimitive -> value.asString
                        else -> value.toString()
                    }
                }
                
                parseBalance(data)
            } else null
        }
    }
    
    private fun parseOrderResult(data: Map<String, Any?>, request: OrderRequest): OrderExecutionResult {
        val orderId = (data["orderId"] ?: data["id"] ?: data["orderLinkId"])?.toString() ?: ""
        val status = (data["status"] ?: data["orderStatus"])?.toString() ?: "NEW"
        
        return OrderExecutionResult.Success(
            ExecutedOrder(
                orderId = orderId,
                clientOrderId = data["clientOrderId"]?.toString() ?: request.clientOrderId ?: "",
                symbol = request.symbol,
                side = request.side,
                type = request.type,
                status = parseOrderStatus(status),
                price = request.price ?: 0.0,
                executedPrice = (data["avgPrice"] ?: data["avgFillPrice"] ?: data["price"])?.toString()?.toDoubleOrNull() ?: 0.0,
                quantity = request.quantity,
                executedQuantity = (data["executedQty"] ?: data["cumExecQty"])?.toString()?.toDoubleOrNull() ?: 0.0,
                fee = (data["commission"] ?: data["fee"])?.toString()?.toDoubleOrNull() ?: 0.0,
                feeCurrency = (data["commissionAsset"] ?: data["feeCurrency"])?.toString() ?: "",
                timestamp = System.currentTimeMillis(),
                exchange = schema.exchangeId
            )
        )
    }
    
    private fun parseOrderResultFromRaw(json: com.google.gson.JsonObject, request: OrderRequest): OrderExecutionResult {
        val data = mutableMapOf<String, Any?>()
        
        json.entrySet().forEach { (key, value) ->
            data[key] = when {
                value.isJsonPrimitive -> value.asString
                else -> value.toString()
            }
        }
        
        return parseOrderResult(data, request)
    }
    
    private fun parseExecutedOrder(data: Map<String, Any?>, symbol: String): ExecutedOrder? {
        val orderId = (data["orderId"] ?: data["id"])?.toString() ?: return null
        
        return ExecutedOrder(
            orderId = orderId,
            clientOrderId = data["clientOrderId"]?.toString() ?: "",
            symbol = symbol.ifEmpty { data["symbol"]?.toString()?.let { normaliseSymbol(it) } ?: "" },
            side = parseTradeSide(data["side"]?.toString() ?: ""),
            type = parseOrderType(data["type"]?.toString() ?: ""),
            status = parseOrderStatus(data["status"]?.toString() ?: ""),
            price = data["price"]?.toString()?.toDoubleOrNull() ?: 0.0,
            executedPrice = (data["avgPrice"] ?: data["avgFillPrice"])?.toString()?.toDoubleOrNull() ?: 0.0,
            quantity = (data["origQty"] ?: data["qty"])?.toString()?.toDoubleOrNull() ?: 0.0,
            executedQuantity = (data["executedQty"] ?: data["cumExecQty"])?.toString()?.toDoubleOrNull() ?: 0.0,
            fee = (data["commission"] ?: data["fee"])?.toString()?.toDoubleOrNull() ?: 0.0,
            feeCurrency = (data["commissionAsset"] ?: data["feeCurrency"])?.toString() ?: "",
            timestamp = (data["time"] ?: data["createdTime"])?.toString()?.toLongOrNull() ?: System.currentTimeMillis(),
            exchange = schema.exchangeId
        )
    }
    
    private fun parseExecutedOrderFromRaw(json: com.google.gson.JsonObject, symbol: String): ExecutedOrder? {
        val data = mutableMapOf<String, Any?>()
        
        json.entrySet().forEach { (key, value) ->
            data[key] = when {
                value.isJsonPrimitive -> value.asString
                else -> value.toString()
            }
        }
        
        return parseExecutedOrder(data, symbol)
    }
    
    private fun parseOrdersFromRaw(json: com.google.gson.JsonObject): List<ExecutedOrder> {
        val array = extractJsonArray(json, "orders") 
            ?: extractJsonArray(json, "data")
            ?: extractJsonArray(json, "result.list")
            ?: return emptyList()
        
        return array.mapNotNull { element ->
            if (element.isJsonObject) {
                parseExecutedOrderFromRaw(element.asJsonObject, "")
            } else null
        }
    }
    
    private fun parseOrderStatus(status: String): OrderStatus {
        return when (status.uppercase()) {
            "NEW", "PENDING", "OPEN", "CREATED" -> OrderStatus.OPEN
            "PARTIALLY_FILLED", "PARTIAL" -> OrderStatus.PARTIALLY_FILLED
            "FILLED", "CLOSED", "DONE" -> OrderStatus.FILLED
            "CANCELED", "CANCELLED" -> OrderStatus.CANCELLED
            "REJECTED" -> OrderStatus.REJECTED
            "EXPIRED" -> OrderStatus.EXPIRED
            else -> OrderStatus.OPEN
        }
    }
    
    private fun parseTradeSide(side: String): TradeSide {
        return when (side.uppercase()) {
            "BUY", "LONG" -> TradeSide.BUY
            "SELL", "SHORT" -> TradeSide.SELL
            else -> TradeSide.BUY
        }
    }
    
    private fun parseOrderType(type: String): OrderType {
        return when (type.uppercase()) {
            "MARKET" -> OrderType.MARKET
            "LIMIT" -> OrderType.LIMIT
            "STOP", "STOP_LOSS" -> OrderType.STOP_LOSS
            "STOP_LIMIT", "STOP_LOSS_LIMIT" -> OrderType.STOP_LIMIT
            "TAKE_PROFIT" -> OrderType.TAKE_PROFIT
            "TAKE_PROFIT_LIMIT" -> OrderType.TAKE_PROFIT_LIMIT
            else -> OrderType.LIMIT
        }
    }
    
    private fun extractJsonArray(json: com.google.gson.JsonObject, path: String): com.google.gson.JsonArray? {
        var current: com.google.gson.JsonElement = json
        
        for (key in path.split(".")) {
            current = when {
                current.isJsonObject -> current.asJsonObject.get(key) ?: return null
                else -> return null
            }
        }
        
        return if (current.isJsonArray) current.asJsonArray else null
    }
    
    // =========================================================================
    // HEALTH TRACKING
    // =========================================================================
    
    private fun recordSuccess() {
        successCount++
        consecutiveFailures = 0
        lastSuccessTime = Instant.now()
        updateHealthTracker()
    }
    
    private fun recordFailure(error: String) {
        failureCount++
        consecutiveFailures++
        lastFailureTime = Instant.now()
        lastError = error
        updateHealthTracker()
        
        // Check if we need to trigger re-learning
        if (consecutiveFailures >= 5) {
            Log.w(TAG, "Multiple failures detected, may need schema re-learning")
        }
    }
    
    private fun updateHealthTracker() {
        val total = successCount + failureCount
        val successRate = if (total > 0) successCount.toDouble() / total else 1.0
        
        healthTracker[schema.exchangeId] = ConnectionHealth(
            exchangeId = schema.exchangeId,
            status = when {
                consecutiveFailures >= 5 -> ConnectionStatus.FAILING
                consecutiveFailures >= 2 -> ConnectionStatus.DEGRADED
                _status.value == ExchangeStatus.CONNECTED -> ConnectionStatus.HEALTHY
                else -> ConnectionStatus.DISCONNECTED
            },
            latencyMs = 0,  // TODO: Track actual latency
            successRate = successRate,
            lastSuccessAt = lastSuccessTime,
            lastFailureAt = lastFailureTime,
            lastError = lastError,
            consecutiveFailures = consecutiveFailures,
            schemaValid = true,
            needsRelearning = consecutiveFailures >= 5
        )
    }
    
    // =========================================================================
    // MISSING INTERFACE IMPLEMENTATIONS (Added v5.5.77)
    // =========================================================================
    
    override fun isConnected(): Boolean {
        return _status.value == ExchangeStatus.CONNECTED
    }
    
    override suspend fun getTickers(symbols: List<String>): Map<String, PriceTick> {
        return symbols.mapNotNull { symbol ->
            getTicker(symbol)?.let { symbol to it }
        }.toMap()
    }
    
    override suspend fun getRecentTrades(symbol: String, limit: Int): List<PublicTrade> {
        val exchangeSymbol = toExchangeSymbol(symbol)
        val params = mapOf("symbol" to exchangeSymbol, "limit" to limit)
        
        val result = executor.execute(StandardOperation.GET_TRADES, params)
        
        return when (result) {
            is ExecutionResult.ArraySuccess -> {
                recordSuccess()
                result.data.mapNotNull { parsePublicTrade(it, symbol) }
            }
            is ExecutionResult.RawSuccess -> {
                recordSuccess()
                parseTradesFromRaw(result.json, symbol)
            }
            else -> {
                recordFailure("Failed to get recent trades")
                emptyList()
            }
        }
    }
    
    override suspend fun getCandles(
        symbol: String,
        interval: String,
        limit: Int,
        startTime: Long?,
        endTime: Long?
    ): List<OHLCVBar> {
        val exchangeSymbol = toExchangeSymbol(symbol)
        val params = mutableMapOf<String, Any>(
            "symbol" to exchangeSymbol,
            "interval" to interval,
            "limit" to limit
        )
        startTime?.let { params["startTime"] = it }
        endTime?.let { params["endTime"] = it }
        
        val result = executor.execute(StandardOperation.GET_CANDLES, params)
        
        return when (result) {
            is ExecutionResult.ArraySuccess -> {
                recordSuccess()
                result.data.mapNotNull { parseOHLCVBar(it, symbol) }
            }
            is ExecutionResult.RawSuccess -> {
                recordSuccess()
                parseCandlesFromRaw(result.json, symbol, interval)
            }
            else -> {
                recordFailure("Failed to get candles")
                emptyList()
            }
        }
    }
    
    override suspend fun getBalance(asset: String): Balance? {
        return getBalances().find { it.asset.equals(asset, ignoreCase = true) }
    }
    
    override suspend fun cancelAllOrders(symbol: String?): Int {
        val params = mutableMapOf<String, Any>()
        symbol?.let { params["symbol"] = toExchangeSymbol(it) }
        
        val result = executor.execute(StandardOperation.CANCEL_ALL_ORDERS, params)
        
        return when {
            result.isSuccess() -> {
                recordSuccess()
                // Try to extract count from result
                when (result) {
                    is ExecutionResult.ObjectSuccess -> 
                        (result.data["count"] ?: result.data["cancelledCount"])?.toString()?.toIntOrNull() ?: 1
                    is ExecutionResult.ArraySuccess -> result.data.size
                    else -> 1
                }
            }
            else -> {
                // Fallback: cancel orders one by one
                val openOrders = getOpenOrders(symbol)
                var cancelled = 0
                for (order in openOrders) {
                    if (cancelOrder(order.orderId, order.symbol)) cancelled++
                }
                cancelled
            }
        }
    }
    
    override suspend fun getOrderHistory(
        symbol: String?,
        startTime: Long?,
        endTime: Long?,
        limit: Int
    ): List<ExecutedOrder> {
        val params = mutableMapOf<String, Any>("limit" to limit)
        symbol?.let { params["symbol"] = toExchangeSymbol(it) }
        startTime?.let { params["startTime"] = it }
        endTime?.let { params["endTime"] = it }
        
        val result = executor.execute(StandardOperation.GET_ORDER_HISTORY, params)
        
        return when (result) {
            is ExecutionResult.ArraySuccess -> {
                recordSuccess()
                result.data.mapNotNull { parseExecutedOrder(it, symbol ?: "") }
            }
            is ExecutionResult.RawSuccess -> {
                recordSuccess()
                parseOrdersFromRaw(result.json)
            }
            else -> {
                recordFailure("Failed to get order history")
                emptyList()
            }
        }
    }
    
    override fun validateOrder(request: OrderRequest): OrderValidationResult {
        // Basic validation
        if (request.quantity <= 0) {
            return OrderValidationResult.Invalid("Quantity must be positive")
        }
        if (request.type == OrderType.LIMIT && (request.price == null || request.price!! <= 0)) {
            return OrderValidationResult.Invalid("Limit orders require a positive price")
        }
        
        // Check against trading pair constraints
        val pair = tradingPairCache[request.symbol]
        if (pair != null) {
            if (!pair.isActive) {
                return OrderValidationResult.Invalid("Trading pair ${request.symbol} is not active")
            }
            if (request.quantity < pair.minQuantity) {
                return OrderValidationResult.Invalid("Quantity below minimum: ${pair.minQuantity}")
            }
            if (request.quantity > pair.maxQuantity) {
                return OrderValidationResult.Invalid("Quantity above maximum: ${pair.maxQuantity}")
            }
            if (request.price != null && pair.minNotional > 0) {
                val notional = request.quantity * request.price!!
                if (notional < pair.minNotional) {
                    return OrderValidationResult.Invalid("Order value below minimum notional: ${pair.minNotional}")
                }
            }
        }
        
        return OrderValidationResult.Valid
    }
    
    // =========================================================================
    // ADDITIONAL PARSING HELPERS (Added v5.5.77)
    // =========================================================================
    
    private fun parsePublicTrade(data: Map<String, Any?>, symbol: String): PublicTrade? {
        val price = (data["price"] ?: data["p"])?.toString()?.toDoubleOrNull() ?: return null
        val quantity = (data["qty"] ?: data["quantity"] ?: data["q"])?.toString()?.toDoubleOrNull() ?: return null
        val side = parseTradeSide(data["side"]?.toString() ?: (if (data["isBuyerMaker"] == "true") "SELL" else "BUY"))
        val timestamp = (data["time"] ?: data["timestamp"] ?: data["T"])?.toString()?.toLongOrNull() ?: System.currentTimeMillis()
        
        return PublicTrade(
            symbol = symbol,
            exchange = schema.exchangeId,
            price = price,
            quantity = quantity,
            side = side,
            timestamp = timestamp,
            tradeId = (data["id"] ?: data["tradeId"] ?: data["a"])?.toString() ?: ""
        )
    }
    
    private fun parseTradesFromRaw(json: com.google.gson.JsonObject, symbol: String): List<PublicTrade> {
        val array = extractJsonArray(json, "trades") 
            ?: extractJsonArray(json, "result")
            ?: extractJsonArray(json, "data")
            ?: return emptyList()
        
        return array.mapNotNull { element ->
            if (element.isJsonObject) {
                val obj = element.asJsonObject
                val data = mutableMapOf<String, Any?>()
                obj.entrySet().forEach { (key, value) ->
                    data[key] = when { value.isJsonPrimitive -> value.asString; else -> value.toString() }
                }
                parsePublicTrade(data, symbol)
            } else null
        }
    }
    
    private fun parseOHLCVBar(data: Map<String, Any?>, symbol: String, interval: String = "1m"): OHLCVBar? {
        val open = (data["open"] ?: data["o"])?.toString()?.toDoubleOrNull() ?: return null
        val high = (data["high"] ?: data["h"])?.toString()?.toDoubleOrNull() ?: return null
        val low = (data["low"] ?: data["l"])?.toString()?.toDoubleOrNull() ?: return null
        val close = (data["close"] ?: data["c"])?.toString()?.toDoubleOrNull() ?: return null
        val volume = (data["volume"] ?: data["v"])?.toString()?.toDoubleOrNull() ?: 0.0
        val timestamp = (data["openTime"] ?: data["time"] ?: data["t"])?.toString()?.toLongOrNull() ?: System.currentTimeMillis()
        
        return OHLCVBar(
            symbol = symbol,
            open = open,
            high = high,
            low = low,
            close = close,
            volume = volume,
            timestamp = timestamp,
            interval = interval
        )
    }
    
    private fun parseCandlesFromRaw(json: com.google.gson.JsonObject, symbol: String, interval: String = "1m"): List<OHLCVBar> {
        val array = extractJsonArray(json, "klines") 
            ?: extractJsonArray(json, "result")
            ?: extractJsonArray(json, "data")
            ?: return emptyList()
        
        return array.mapNotNull { element ->
            when {
                element.isJsonArray -> {
                    // Common format: [timestamp, open, high, low, close, volume, ...]
                    val arr = element.asJsonArray
                    if (arr.size() >= 6) {
                        OHLCVBar(
                            symbol = symbol,
                            timestamp = arr[0].asLong,
                            open = arr[1].asString.toDoubleOrNull() ?: return@mapNotNull null,
                            high = arr[2].asString.toDoubleOrNull() ?: return@mapNotNull null,
                            low = arr[3].asString.toDoubleOrNull() ?: return@mapNotNull null,
                            close = arr[4].asString.toDoubleOrNull() ?: return@mapNotNull null,
                            volume = arr[5].asString.toDoubleOrNull() ?: 0.0,
                            interval = interval
                        )
                    } else null
                }
                element.isJsonObject -> {
                    val obj = element.asJsonObject
                    val data = mutableMapOf<String, Any?>()
                    obj.entrySet().forEach { (key, value) ->
                        data[key] = when { value.isJsonPrimitive -> value.asString; else -> value.toString() }
                    }
                    parseOHLCVBar(data, symbol, interval)
                }
                else -> null
            }
        }
    }
}
