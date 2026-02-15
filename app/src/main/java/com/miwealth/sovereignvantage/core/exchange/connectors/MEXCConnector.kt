package com.miwealth.sovereignvantage.core.exchange.connectors

import com.miwealth.sovereignvantage.core.*
import com.miwealth.sovereignvantage.core.exchange.*
import com.miwealth.sovereignvantage.core.trading.engine.OrderRequest
import com.miwealth.sovereignvantage.core.trading.engine.OrderExecutionResult
import com.miwealth.sovereignvantage.core.trading.engine.ExecutedOrder
import com.miwealth.sovereignvantage.core.trading.engine.TimeInForce
import com.miwealth.sovereignvantage.core.security.pqc.hybrid.*
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.*
import java.net.URLEncoder

/**
 * MEXC CONNECTOR - Complete Implementation
 * 
 * Full-featured MEXC exchange connector with:
 * - REST API for spot trading (V3 API)
 * - REST API for futures trading (Contract V1 API)
 * - WebSocket for real-time market data
 * - HMAC-SHA256 authentication
 * - Direct symbol format (BTCUSDT)
 * - PQC integration via BaseCEXConnector
 * 
 * API Documentation:
 * - Spot: https://mexcdevelop.github.io/apidocs/spot_v3_en/
 * - Futures: https://mexcdevelop.github.io/apidocs/contract_v1_en/
 * 
 * Endpoints:
 * - Spot REST: https://api.mexc.com
 * - Futures REST: https://contract.mexc.com
 * - WebSocket: wss://wbs.mexc.com/ws
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

class MEXCConnector(
    config: ExchangeConfig,
    secureHttpClient: HybridSecureHttpClient? = null,
    credentialVault: PQCCredentialVault? = null
) : BaseCEXConnector(config, secureHttpClient, credentialVault) {
    
    companion object {
        private const val SPOT_API_BASE = "https://api.mexc.com"
        private const val FUTURES_API_BASE = "https://contract.mexc.com"
        private const val WS_URL = "wss://wbs.mexc.com/ws"
        
        // Order type mapping
        private val ORDER_TYPE_MAP = mapOf(
            OrderType.MARKET to "MARKET",
            OrderType.LIMIT to "LIMIT",
            OrderType.STOP_LOSS to "STOP",
            OrderType.STOP_LIMIT to "STOP_LIMIT",
            OrderType.TRAILING_STOP to "TRAILING_STOP"
        )
        
        // Time in force mapping
        private val TIF_MAP = mapOf(
            TimeInForce.GTC to "GTC",
            TimeInForce.IOC to "IOC",
            TimeInForce.FOK to "FOK",
            TimeInForce.GTD to "GTX"  // Post-only
        )
        
        // Interval mapping
        private val INTERVAL_MAP = mapOf(
            "1m" to "1m", "5m" to "5m", "15m" to "15m", "30m" to "30m",
            "1h" to "60m", "4h" to "4h", "1d" to "1d", "1w" to "1W", "1M" to "1M"
        )
    }
    
    // =========================================================================
    // EXCHANGE CAPABILITIES
    // =========================================================================
    
    override val capabilities = ExchangeCapabilities(
        supportsSpotTrading = true,
        supportsFutures = true,       // Full USDT-M and COIN-M futures
        supportsMargin = true,        // Cross and isolated margin
        supportsOptions = false,
        supportsLending = false,
        supportsStaking = true,       // MX staking
        supportsWebSocket = true,
        supportsOrderbook = true,
        supportsMarketOrders = true,
        supportsLimitOrders = true,
        supportsStopOrders = true,
        supportsPostOnly = true,
        supportsCancelAll = true,
        maxOrdersPerSecond = 20,      // 20 orders per second
        minOrderValue = 5.0,          // $5 minimum for most pairs
        tradingFeeMaker = 0.0,        // 0% maker (promotional)
        tradingFeeTaker = 0.001,      // 0.10% taker
        withdrawalEnabled = false,    // Non-custodial
        networks = listOf(
            BlockchainNetwork.BITCOIN,
            BlockchainNetwork.ETHEREUM,
            BlockchainNetwork.BINANCE_SMART_CHAIN,
            BlockchainNetwork.SOLANA,
            BlockchainNetwork.POLYGON,
            BlockchainNetwork.ARBITRUM,
            BlockchainNetwork.OPTIMISM,
            BlockchainNetwork.AVALANCHE,
            BlockchainNetwork.TRON
        )
    )
    
    // =========================================================================
    // API ENDPOINTS
    // =========================================================================
    
    override val endpoints = ExchangeEndpoints(
        ticker = "/api/v3/ticker/24hr",
        orderBook = "/api/v3/depth",
        trades = "/api/v3/trades",
        candles = "/api/v3/klines",
        pairs = "/api/v3/exchangeInfo",
        balances = "/api/v3/account",
        placeOrder = "/api/v3/order",
        cancelOrder = "/api/v3/order",
        getOrder = "/api/v3/order",
        openOrders = "/api/v3/openOrders",
        orderHistory = "/api/v3/allOrders",
        wsUrl = WS_URL
    )
    
    // Futures endpoints
    private val futuresEndpoints = mapOf(
        "ticker" to "/api/v1/contract/ticker",
        "orderBook" to "/api/v1/contract/depth/{symbol}",
        "positions" to "/api/v1/private/position/open_positions",
        "placeOrder" to "/api/v1/private/order/submit",
        "cancelOrder" to "/api/v1/private/order/cancel",
        "leverage" to "/api/v1/private/position/change_leverage"
    )
    
    // WebSocket state
    private var wsSubscriptionId = 0
    
    // =========================================================================
    // AUTHENTICATION - MEXC HMAC-SHA256
    // =========================================================================
    
    override fun signRequest(
        method: String,
        path: String,
        params: Map<String, String>,
        body: String?,
        timestamp: Long
    ): Map<String, String> {
        // MEXC signing is similar to Binance:
        // 1. Build query string with timestamp
        // 2. Sign entire query string with HMAC-SHA256
        // 3. Return headers with API key
        
        return mapOf(
            "X-MEXC-APIKEY" to config.apiKey,
            "Content-Type" to "application/json"
        )
    }
    
    /**
     * Build signed query string for MEXC spot API
     */
    private fun buildSignedQuery(params: Map<String, String>): String {
        val timestamp = System.currentTimeMillis()
        val queryParams = params.toMutableMap()
        queryParams["timestamp"] = timestamp.toString()
        queryParams["recvWindow"] = "5000"
        
        val queryString = queryParams.entries
            .sortedBy { it.key }
            .joinToString("&") { "${it.key}=${urlEncode(it.value)}" }
        
        val signature = hmacSha256(queryString, config.apiSecret)
        
        return "$queryString&signature=$signature"
    }
    
    /**
     * Build signed request for MEXC futures API
     */
    private fun buildFuturesSignature(
        timestamp: Long,
        params: Map<String, String>
    ): String {
        // Futures signature: timestamp + apiKey + (params in alphabetical order)
        val paramString = params.entries
            .sortedBy { it.key }
            .joinToString("&") { "${it.key}=${it.value}" }
        
        val signaturePayload = "$timestamp${config.apiKey}$paramString"
        return hmacSha256(signaturePayload, config.apiSecret)
    }
    
    private fun hmacSha256(data: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val hash = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
    
    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
    }
    
    // =========================================================================
    // TICKER - Spot
    // =========================================================================
    
    override fun parseTicker(response: JsonObject, symbol: String): PriceTick? {
        return try {
            PriceTick(
                symbol = normaliseSymbol(symbol),
                bid = response.get("bidPrice")?.asString?.toDoubleOrNull() ?: 0.0,
                ask = response.get("askPrice")?.asString?.toDoubleOrNull() ?: 0.0,
                last = response.get("lastPrice")?.asString?.toDoubleOrNull() ?: 0.0,
                volume24h = response.get("volume")?.asString?.toDoubleOrNull() ?: 0.0,
                high24h = response.get("highPrice")?.asString?.toDoubleOrNull() ?: 0.0,
                low24h = response.get("lowPrice")?.asString?.toDoubleOrNull() ?: 0.0,
                change24h = response.get("priceChange")?.asString?.toDoubleOrNull() ?: 0.0,
                changePercent24h = response.get("priceChangePercent")?.asString?.toDoubleOrNull() ?: 0.0,
                timestamp = System.currentTimeMillis(),
                exchange = "mexc"
            )
        } catch (e: Exception) {
            null
        }
    }
    
    // =========================================================================
    // ORDER BOOK
    // =========================================================================
    
    override fun parseOrderBook(response: JsonObject, symbol: String): OrderBook? {
        return try {
            val bids = response.getAsJsonArray("bids")?.map { bid ->
                val arr = bid.asJsonArray
                OrderBookLevel(
                    price = arr[0].asString.toDouble(),
                    quantity = arr[1].asString.toDouble()
                )
            } ?: emptyList()
            
            val asks = response.getAsJsonArray("asks")?.map { ask ->
                val arr = ask.asJsonArray
                OrderBookLevel(
                    price = arr[0].asString.toDouble(),
                    quantity = arr[1].asString.toDouble()
                )
            } ?: emptyList()
            
            OrderBook(
                symbol = normaliseSymbol(symbol),
                bids = bids,
                asks = asks,
                timestamp = response.get("lastUpdateId")?.asLong ?: System.currentTimeMillis(),
                exchange = "mexc"
            )
        } catch (e: Exception) {
            null
        }
    }
    
    // =========================================================================
    // TRADING PAIRS
    // =========================================================================
    
    override fun parseTradingPairs(response: JsonObject): List<TradingPair> {
        val pairs = mutableListOf<TradingPair>()
        
        try {
            val symbols = response.getAsJsonArray("symbols") ?: return emptyList()
            
            for (symbolJson in symbols) {
                val obj = symbolJson.asJsonObject
                val symbol = obj.get("symbol")?.asString ?: continue
                val status = obj.get("status")?.asString ?: ""
                
                if (status != "ENABLED") continue
                
                val baseAsset = obj.get("baseAsset")?.asString ?: continue
                val quoteAsset = obj.get("quoteAsset")?.asString ?: continue
                
                // Extract filters for precision
                val filters = obj.getAsJsonArray("filters")
                var minQty = 0.0
                var maxQty = Double.MAX_VALUE
                var stepSize = 0.00000001
                var tickSize = 0.00000001
                var minNotional = 5.0
                
                filters?.forEach { filterJson ->
                    val filter = filterJson.asJsonObject
                    when (filter.get("filterType")?.asString) {
                        "LOT_SIZE" -> {
                            minQty = filter.get("minQty")?.asString?.toDoubleOrNull() ?: minQty
                            maxQty = filter.get("maxQty")?.asString?.toDoubleOrNull() ?: maxQty
                            stepSize = filter.get("stepSize")?.asString?.toDoubleOrNull() ?: stepSize
                        }
                        "PRICE_FILTER" -> {
                            tickSize = filter.get("tickSize")?.asString?.toDoubleOrNull() ?: tickSize
                        }
                        "MIN_NOTIONAL" -> {
                            minNotional = filter.get("minNotional")?.asString?.toDoubleOrNull() ?: minNotional
                        }
                    }
                }
                
                pairs.add(TradingPair(
                    symbol = "$baseAsset/$quoteAsset",
                    baseAsset = baseAsset,
                    quoteAsset = quoteAsset,
                    exchangeSymbol = symbol,
                    minQuantity = minQty,
                    maxQuantity = maxQty,
                    quantityPrecision = calculatePrecision(stepSize),
                    pricePrecision = calculatePrecision(tickSize),
                    minNotional = minNotional,
                    isActive = true,
                    exchange = "mexc"
                ))
            }
        } catch (e: Exception) {
            // Return whatever we've parsed
        }
        
        return pairs
    }
    
    private fun calculatePrecision(stepSize: Double): Int {
        if (stepSize >= 1) return 0
        var precision = 0
        var size = stepSize
        while (size < 1 && precision < 10) {
            size *= 10
            precision++
        }
        return precision
    }
    
    // =========================================================================
    // BALANCES
    // =========================================================================
    
    override fun parseBalances(response: JsonObject): List<Balance> {
        val balances = mutableListOf<Balance>()
        
        try {
            val balancesArray = response.getAsJsonArray("balances") ?: return emptyList()
            
            for (balanceJson in balancesArray) {
                val obj = balanceJson.asJsonObject
                val asset = obj.get("asset")?.asString ?: continue
                val free = obj.get("free")?.asString?.toDoubleOrNull() ?: 0.0
                val locked = obj.get("locked")?.asString?.toDoubleOrNull() ?: 0.0
                
                if (free > 0 || locked > 0) {
                    balances.add(Balance(
                        asset = asset,
                        free = free,
                        locked = locked,
                        total = free + locked,
                        exchange = "mexc"
                    ))
                }
            }
        } catch (e: Exception) {
            // Return whatever we've parsed
        }
        
        return balances
    }
    
    // =========================================================================
    // ORDER PARSING
    // =========================================================================
    
    override fun parseOrder(response: JsonObject): ExecutedOrder? {
        return try {
            val symbol = response.get("symbol")?.asString ?: return null
            val orderId = response.get("orderId")?.asString ?: return null
            
            ExecutedOrder(
                orderId = orderId,
                clientOrderId = response.get("clientOrderId")?.asString ?: "",
                symbol = normaliseSymbol(symbol),
                side = if (response.get("side")?.asString == "BUY") TradeSide.BUY else TradeSide.SELL,
                type = parseMEXCOrderType(response.get("type")?.asString ?: "LIMIT"),
                status = parseMEXCOrderStatus(response.get("status")?.asString ?: "NEW"),
                price = response.get("price")?.asString?.toDoubleOrNull() ?: 0.0,
                quantity = response.get("origQty")?.asString?.toDoubleOrNull() ?: 0.0,
                filledQuantity = response.get("executedQty")?.asString?.toDoubleOrNull() ?: 0.0,
                averagePrice = response.get("cummulativeQuoteQty")?.asString?.toDoubleOrNull()?.let { cumQty ->
                    val execQty = response.get("executedQty")?.asString?.toDoubleOrNull() ?: 0.0
                    if (execQty > 0) cumQty / execQty else 0.0
                } ?: 0.0,
                fee = 0.0,  // Not in this response
                feeCurrency = "",
                createdAt = response.get("time")?.asLong ?: System.currentTimeMillis(),
                updatedAt = response.get("updateTime")?.asLong ?: System.currentTimeMillis(),
                exchange = "mexc"
            )
        } catch (e: Exception) {
            null
        }
    }
    
    override fun parsePlaceOrderResponse(response: JsonObject, request: OrderRequest): OrderExecutionResult {
        return try {
            if (hasError(response)) {
                val code = response.get("code")?.asInt ?: -1
                val msg = response.get("msg")?.asString ?: "Unknown error"
                return OrderExecutionResult.Error(Exception("MEXC error $code: $msg"))
            }
            
            val order = parseOrder(response)
            if (order != null) {
                OrderExecutionResult.Success(order)
            } else {
                OrderExecutionResult.Error(Exception("Failed to parse order response"))
            }
        } catch (e: Exception) {
            OrderExecutionResult.Error(e)
        }
    }
    
    // =========================================================================
    // ORDER BUILDING
    // =========================================================================
    
    override fun buildOrderBody(request: OrderRequest): Map<String, String> {
        val params = mutableMapOf(
            "symbol" to toExchangeSymbol(request.symbol),
            "side" to if (request.side == TradeSide.BUY) "BUY" else "SELL",
            "type" to (ORDER_TYPE_MAP[request.type] ?: "LIMIT"),
            "quantity" to request.quantity.toString()
        )
        
        // Price for limit orders
        if (request.type == OrderType.LIMIT || request.type == OrderType.STOP_LIMIT) {
            request.price?.let { params["price"] = it.toString() }
        }
        
        // Stop price for stop orders
        if (request.type == OrderType.STOP_LOSS || request.type == OrderType.STOP_LIMIT) {
            request.stopPrice?.let { params["stopPrice"] = it.toString() }
        }
        
        // Time in force
        request.timeInForce?.let { tif ->
            TIF_MAP[tif]?.let { params["timeInForce"] = it }
        }
        
        // Client order ID
        request.clientOrderId?.let { params["newClientOrderId"] = it }
        
        return params
    }
    
    // =========================================================================
    // SPOT API METHODS
    // =========================================================================
    
    override suspend fun getTicker(symbol: String): PriceTick? {
        val exchangeSymbol = toExchangeSymbol(symbol)
        val url = "${config.baseUrl}${endpoints.ticker}?symbol=$exchangeSymbol"
        
        return try {
            val response = executeGet(url)
            val json = gson.fromJson(response, JsonObject::class.java)
            parseTicker(json, exchangeSymbol)
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun getOrderBook(symbol: String, limit: Int): OrderBook? {
        val exchangeSymbol = toExchangeSymbol(symbol)
        val url = "${config.baseUrl}${endpoints.orderBook}?symbol=$exchangeSymbol&limit=$limit"
        
        return try {
            val response = executeGet(url)
            val json = gson.fromJson(response, JsonObject::class.java)
            parseOrderBook(json, exchangeSymbol)
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun getBalances(): List<Balance> {
        val query = buildSignedQuery(emptyMap())
        val url = "${config.baseUrl}${endpoints.balances}?$query"
        
        return try {
            val response = executeSignedGet(url)
            val json = gson.fromJson(response, JsonObject::class.java)
            parseBalances(json)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    override suspend fun placeOrder(request: OrderRequest): OrderExecutionResult {
        val params = buildOrderBody(request)
        val query = buildSignedQuery(params)
        val url = "${config.baseUrl}${endpoints.placeOrder}?$query"
        
        return try {
            val response = executeSignedPost(url, "")
            val json = gson.fromJson(response, JsonObject::class.java)
            parsePlaceOrderResponse(json, request)
        } catch (e: Exception) {
            OrderExecutionResult.Error(e)
        }
    }
    
    override suspend fun cancelOrder(symbol: String, orderId: String): Boolean {
        val params = mapOf(
            "symbol" to toExchangeSymbol(symbol),
            "orderId" to orderId
        )
        val query = buildSignedQuery(params)
        val url = "${config.baseUrl}${endpoints.cancelOrder}?$query"
        
        return try {
            val response = executeSignedDelete(url)
            val json = gson.fromJson(response, JsonObject::class.java)
            !hasError(json)
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun getOrder(symbol: String, orderId: String): ExecutedOrder? {
        val params = mapOf(
            "symbol" to toExchangeSymbol(symbol),
            "orderId" to orderId
        )
        val query = buildSignedQuery(params)
        val url = "${config.baseUrl}${endpoints.getOrder}?$query"
        
        return try {
            val response = executeSignedGet(url)
            val json = gson.fromJson(response, JsonObject::class.java)
            parseOrder(json)
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun getOpenOrders(symbol: String?): List<ExecutedOrder> {
        val params = symbol?.let { mapOf("symbol" to toExchangeSymbol(it)) } ?: emptyMap()
        val query = buildSignedQuery(params)
        val url = "${config.baseUrl}${endpoints.openOrders}?$query"
        
        return try {
            val response = executeSignedGet(url)
            val json = gson.fromJson(response, JsonArray::class.java)
            json.mapNotNull { parseOrder(it.asJsonObject) }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    // =========================================================================
    // FUTURES API METHODS
    // =========================================================================
    
    /**
     * Get futures ticker
     */
    suspend fun getFuturesTicker(symbol: String): FuturesTicker? {
        val url = "$FUTURES_API_BASE${futuresEndpoints["ticker"]}?symbol=$symbol"
        
        return try {
            val response = executeGet(url)
            val json = gson.fromJson(response, JsonObject::class.java)
            parseFuturesTicker(json)
        } catch (e: Exception) {
            null
        }
    }
    
    private fun parseFuturesTicker(response: JsonObject): FuturesTicker? {
        return try {
            val data = response.getAsJsonObject("data") ?: return null
            
            FuturesTicker(
                symbol = data.get("symbol")?.asString ?: "",
                lastPrice = data.get("lastPrice")?.asDouble ?: 0.0,
                markPrice = data.get("fairPrice")?.asDouble ?: 0.0,
                indexPrice = data.get("indexPrice")?.asDouble ?: 0.0,
                fundingRate = data.get("fundingRate")?.asDouble ?: 0.0,
                nextFundingTime = data.get("nextSettleTime")?.asLong ?: 0L,
                volume24h = data.get("volume24")?.asDouble ?: 0.0,
                openInterest = data.get("holdVol")?.asDouble ?: 0.0,
                high24h = data.get("high24Price")?.asDouble ?: 0.0,
                low24h = data.get("lower24Price")?.asDouble ?: 0.0
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get futures positions
     */
    suspend fun getFuturesPositions(): List<FuturesPosition> {
        val timestamp = System.currentTimeMillis()
        val signature = buildFuturesSignature(timestamp, emptyMap())
        
        val headers = mapOf(
            "ApiKey" to config.apiKey,
            "Request-Time" to timestamp.toString(),
            "Signature" to signature,
            "Content-Type" to "application/json"
        )
        
        val url = "$FUTURES_API_BASE${futuresEndpoints["positions"]}"
        
        return try {
            val response = executeSignedGet(url, headers)
            val json = gson.fromJson(response, JsonObject::class.java)
            parseFuturesPositions(json)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun parseFuturesPositions(response: JsonObject): List<FuturesPosition> {
        val positions = mutableListOf<FuturesPosition>()
        
        try {
            val data = response.getAsJsonArray("data") ?: return emptyList()
            
            for (posJson in data) {
                val pos = posJson.asJsonObject
                positions.add(FuturesPosition(
                    symbol = pos.get("symbol")?.asString ?: "",
                    side = if (pos.get("positionType")?.asInt == 1) PositionSide.LONG else PositionSide.SHORT,
                    quantity = pos.get("holdVol")?.asDouble ?: 0.0,
                    entryPrice = pos.get("openAvgPrice")?.asDouble ?: 0.0,
                    markPrice = pos.get("fairPrice")?.asDouble ?: 0.0,
                    liquidationPrice = pos.get("liquidatePrice")?.asDouble ?: 0.0,
                    unrealisedPnl = pos.get("unrealised")?.asDouble ?: 0.0,
                    leverage = pos.get("leverage")?.asInt ?: 1,
                    marginType = if (pos.get("openType")?.asInt == 1) MarginType.ISOLATED else MarginType.CROSS
                ))
            }
        } catch (e: Exception) {
            // Return what we have
        }
        
        return positions
    }
    
    /**
     * Place futures order
     */
    suspend fun placeFuturesOrder(request: FuturesOrderRequest): OrderExecutionResult {
        val timestamp = System.currentTimeMillis()
        
        val params = mutableMapOf(
            "symbol" to request.symbol,
            "vol" to request.quantity.toString(),
            "side" to if (request.side == TradeSide.BUY) "1" else "2",  // 1=open long, 2=open short
            "type" to if (request.type == OrderType.MARKET) "5" else "1",  // 1=limit, 5=market
            "openType" to if (request.marginType == MarginType.ISOLATED) "1" else "2",
            "leverage" to request.leverage.toString()
        )
        
        if (request.type == OrderType.LIMIT) {
            request.price?.let { params["price"] = it.toString() }
        }
        
        val signature = buildFuturesSignature(timestamp, params)
        
        val headers = mapOf(
            "ApiKey" to config.apiKey,
            "Request-Time" to timestamp.toString(),
            "Signature" to signature,
            "Content-Type" to "application/json"
        )
        
        val url = "$FUTURES_API_BASE${futuresEndpoints["placeOrder"]}"
        val body = gson.toJson(params)
        
        return try {
            val response = executeSignedPost(url, body, headers)
            val json = gson.fromJson(response, JsonObject::class.java)
            parseFuturesOrderResponse(json, request)
        } catch (e: Exception) {
            OrderExecutionResult.Error(e)
        }
    }
    
    private fun parseFuturesOrderResponse(response: JsonObject, request: FuturesOrderRequest): OrderExecutionResult {
        return try {
            val code = response.get("code")?.asInt ?: -1
            if (code != 0) {
                val msg = response.get("msg")?.asString ?: "Unknown error"
                return OrderExecutionResult.Error(Exception("MEXC Futures error $code: $msg"))
            }
            
            val data = response.getAsJsonObject("data")
            val orderId = data?.get("orderId")?.asString ?: return OrderExecutionResult.Error(Exception("No order ID"))
            
            OrderExecutionResult.Success(ExecutedOrder(
                orderId = orderId,
                clientOrderId = "",
                symbol = request.symbol,
                side = request.side,
                type = request.type,
                status = OrderStatus.OPEN,
                price = request.price ?: 0.0,
                quantity = request.quantity,
                filledQuantity = 0.0,
                averagePrice = 0.0,
                fee = 0.0,
                feeCurrency = "USDT",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                exchange = "mexc"
            ))
        } catch (e: Exception) {
            OrderExecutionResult.Error(e)
        }
    }
    
    /**
     * Set futures leverage
     */
    suspend fun setFuturesLeverage(symbol: String, leverage: Int): Boolean {
        val timestamp = System.currentTimeMillis()
        val params = mapOf(
            "symbol" to symbol,
            "leverage" to leverage.toString(),
            "openType" to "1"  // Isolated
        )
        
        val signature = buildFuturesSignature(timestamp, params)
        
        val headers = mapOf(
            "ApiKey" to config.apiKey,
            "Request-Time" to timestamp.toString(),
            "Signature" to signature,
            "Content-Type" to "application/json"
        )
        
        val url = "$FUTURES_API_BASE${futuresEndpoints["leverage"]}"
        val body = gson.toJson(params)
        
        return try {
            val response = executeSignedPost(url, body, headers)
            val json = gson.fromJson(response, JsonObject::class.java)
            json.get("code")?.asInt == 0
        } catch (e: Exception) {
            false
        }
    }
    
    // =========================================================================
    // WEBSOCKET
    // =========================================================================
    
    override fun handleWsMessage(text: String) {
        try {
            val json = gson.fromJson(text, JsonObject::class.java)
            
            // Handle ping
            if (json.has("ping")) {
                sendWsPong(json.get("ping").asLong)
                return
            }
            
            // Handle channel data
            val channel = json.get("c")?.asString ?: json.get("channel")?.asString ?: return
            val data = json.getAsJsonObject("d") ?: json.getAsJsonObject("data") ?: return
            
            when {
                channel.contains("ticker") -> handleWsTicker(data)
                channel.contains("depth") -> handleWsOrderBook(data, channel)
                channel.contains("trade") -> handleWsTrade(data)
            }
        } catch (e: Exception) {
            // Ignore parse errors
        }
    }
    
    private fun sendWsPong(timestamp: Long) {
        val pong = gson.toJson(mapOf("pong" to timestamp))
        sendWsMessage(pong)
    }
    
    private fun handleWsTicker(data: JsonObject) {
        try {
            val symbol = data.get("s")?.asString ?: return
            val tick = PriceTick(
                symbol = normaliseSymbol(symbol),
                bid = data.get("b")?.asString?.toDoubleOrNull() ?: 0.0,
                ask = data.get("a")?.asString?.toDoubleOrNull() ?: 0.0,
                last = data.get("c")?.asString?.toDoubleOrNull() ?: 0.0,
                volume24h = data.get("v")?.asString?.toDoubleOrNull() ?: 0.0,
                high24h = data.get("h")?.asString?.toDoubleOrNull() ?: 0.0,
                low24h = data.get("l")?.asString?.toDoubleOrNull() ?: 0.0,
                change24h = 0.0,
                changePercent24h = data.get("r")?.asString?.toDoubleOrNull()?.times(100) ?: 0.0,
                timestamp = System.currentTimeMillis(),
                exchange = "mexc"
            )
            
            scope.launch { _priceUpdates.emit(tick) }
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    private fun handleWsOrderBook(data: JsonObject, channel: String) {
        try {
            val symbol = channel.substringAfter("spot@public.increase.depth.v3.api@").substringBefore("@")
            
            val bids = data.getAsJsonArray("bids")?.map { bid ->
                val arr = bid.asJsonArray
                OrderBookLevel(arr[0].asString.toDouble(), arr[1].asString.toDouble())
            } ?: emptyList()
            
            val asks = data.getAsJsonArray("asks")?.map { ask ->
                val arr = ask.asJsonArray
                OrderBookLevel(arr[0].asString.toDouble(), arr[1].asString.toDouble())
            } ?: emptyList()
            
            val orderBook = OrderBook(
                symbol = normaliseSymbol(symbol),
                bids = bids,
                asks = asks,
                timestamp = System.currentTimeMillis(),
                exchange = "mexc"
            )
            
            scope.launch { _orderBookUpdates.emit(orderBook) }
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    private fun handleWsTrade(data: JsonObject) {
        // Handle trade updates if needed
    }
    
    override fun buildWsSubscription(channels: List<String>, symbols: List<String>): String {
        wsSubscriptionId++
        
        val params = mutableListOf<String>()
        
        symbols.forEach { symbol ->
            val exchangeSymbol = toExchangeSymbol(symbol)
            
            channels.forEach { channel ->
                val param = when (channel) {
                    "ticker" -> "spot@public.miniTicker.v3.api@$exchangeSymbol"
                    "depth", "orderbook" -> "spot@public.increase.depth.v3.api@$exchangeSymbol"
                    "trade", "trades" -> "spot@public.deals.v3.api@$exchangeSymbol"
                    "kline" -> "spot@public.kline.v3.api@$exchangeSymbol@Min1"
                    else -> "spot@public.miniTicker.v3.api@$exchangeSymbol"
                }
                params.add(param)
            }
        }
        
        return gson.toJson(mapOf(
            "method" to "SUBSCRIPTION",
            "params" to params,
            "id" to wsSubscriptionId
        ))
    }
    
    // =========================================================================
    // HELPERS
    // =========================================================================
    
    override fun hasError(response: JsonObject): Boolean {
        val code = response.get("code")?.asInt
        return code != null && code != 0 && code != 200
    }
    
    override fun toExchangeSymbol(normalisedSymbol: String): String {
        // BTC/USDT -> BTCUSDT
        return normalisedSymbol.replace("/", "")
    }
    
    override fun normaliseSymbol(exchangeSymbol: String): String {
        // BTCUSDT -> BTC/USDT
        val quotes = listOf("USDT", "USDC", "USD", "BTC", "ETH", "EUR", "GBP")
        
        for (quote in quotes) {
            if (exchangeSymbol.endsWith(quote)) {
                val base = exchangeSymbol.dropLast(quote.length)
                if (base.isNotEmpty()) return "$base/$quote"
            }
        }
        
        return exchangeSymbol
    }
    
    private fun parseMEXCOrderType(type: String): OrderType {
        return when (type.uppercase()) {
            "MARKET" -> OrderType.MARKET
            "LIMIT" -> OrderType.LIMIT
            "STOP" -> OrderType.STOP_LOSS
            "STOP_LIMIT" -> OrderType.STOP_LIMIT
            else -> OrderType.LIMIT
        }
    }
    
    private fun parseMEXCOrderStatus(status: String): OrderStatus {
        return when (status.uppercase()) {
            "NEW" -> OrderStatus.OPEN
            "PARTIALLY_FILLED" -> OrderStatus.PARTIALLY_FILLED
            "FILLED" -> OrderStatus.FILLED
            "CANCELED", "CANCELLED" -> OrderStatus.CANCELLED
            "REJECTED" -> OrderStatus.REJECTED
            "EXPIRED" -> OrderStatus.EXPIRED
            else -> OrderStatus.PENDING
        }
    }
}

// =========================================================================
// FUTURES DATA CLASSES
// =========================================================================

data class FuturesTicker(
    val symbol: String,
    val lastPrice: Double,
    val markPrice: Double,
    val indexPrice: Double,
    val fundingRate: Double,
    val nextFundingTime: Long,
    val volume24h: Double,
    val openInterest: Double,
    val high24h: Double,
    val low24h: Double
)

data class FuturesPosition(
    val symbol: String,
    val side: PositionSide,
    val quantity: Double,
    val entryPrice: Double,
    val markPrice: Double,
    val liquidationPrice: Double,
    val unrealisedPnl: Double,
    val leverage: Int,
    val marginType: MarginType
)

data class FuturesOrderRequest(
    val symbol: String,
    val side: TradeSide,
    val type: OrderType,
    val quantity: Double,
    val price: Double? = null,
    val leverage: Int = 1,
    val marginType: MarginType = MarginType.CROSS,
    val reduceOnly: Boolean = false
)

enum class PositionSide { LONG, SHORT }
enum class MarginType { CROSS, ISOLATED }
