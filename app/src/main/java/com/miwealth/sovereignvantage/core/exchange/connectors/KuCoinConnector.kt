package com.miwealth.sovereignvantage.core.exchange.connectors

import com.miwealth.sovereignvantage.core.*
import com.miwealth.sovereignvantage.core.exchange.*
import com.miwealth.sovereignvantage.core.trading.engine.*
import com.miwealth.sovereignvantage.core.trading.routing.OrderBook
import com.miwealth.sovereignvantage.core.trading.routing.OrderBookLevel
import com.miwealth.sovereignvantage.core.security.pqc.hybrid.*
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import android.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.*
import java.security.MessageDigest

/**
 * KUCOIN CONNECTOR - Complete Implementation (PQC-Integrated)
 * 
 * Full-featured KuCoin exchange connector with:
 * - REST API V1/V2 for spot trading
 * - WebSocket V1 for real-time market data
 * - HMAC-SHA256 + Base64 authentication (API Key V2)
 * - Passphrase encryption with API secret
 * - PQC protection via HybridSecureHttpClient
 * 
 * API Documentation: https://www.kucoin.com/docs-new
 * 
 * Authentication (V2 API Key):
 * - KC-API-KEY: API key
 * - KC-API-SIGN: Base64(HMAC-SHA256(timestamp + method + endpoint + body))
 * - KC-API-TIMESTAMP: Unix timestamp in milliseconds
 * - KC-API-PASSPHRASE: Base64(HMAC-SHA256(passphrase))
 * - KC-API-KEY-VERSION: "2"
 * 
 * Base URLs:
 * - Production: https://api.kucoin.com
 * - Sandbox: https://openapi-sandbox.kucoin.com
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

class KuCoinConnector(
    config: ExchangeConfig,
    secureHttpClient: HybridSecureHttpClient? = null,
    credentialVault: PQCCredentialVault? = null
) : BaseCEXConnector(config, secureHttpClient, credentialVault) {
    
    companion object {
        // KuCoin uses BASE-QUOTE format (e.g., BTC-USDT)
        private val SYMBOL_MAP = mapOf(
            "BTC/USDT" to "BTC-USDT",
            "ETH/USDT" to "ETH-USDT",
            "SOL/USDT" to "SOL-USDT",
            "XRP/USDT" to "XRP-USDT",
            "ADA/USDT" to "ADA-USDT",
            "DOGE/USDT" to "DOGE-USDT",
            "AVAX/USDT" to "AVAX-USDT",
            "DOT/USDT" to "DOT-USDT",
            "LINK/USDT" to "LINK-USDT",
            "MATIC/USDT" to "MATIC-USDT",
            "ATOM/USDT" to "ATOM-USDT",
            "UNI/USDT" to "UNI-USDT",
            "LTC/USDT" to "LTC-USDT",
            "BCH/USDT" to "BCH-USDT",
            "NEAR/USDT" to "NEAR-USDT",
            "APT/USDT" to "APT-USDT",
            "ARB/USDT" to "ARB-USDT",
            "OP/USDT" to "OP-USDT",
            "INJ/USDT" to "INJ-USDT",
            "FIL/USDT" to "FIL-USDT",
            "BTC/USD" to "BTC-USDC",
            "ETH/USD" to "ETH-USDC",
            "ETH/BTC" to "ETH-BTC",
            "SOL/BTC" to "SOL-BTC"
        )
        
        private val REVERSE_SYMBOL_MAP = SYMBOL_MAP.entries.associate { (k, v) -> v to k }
        
        private val INTERVAL_MAP = mapOf(
            "1m" to "1min",
            "3m" to "3min",
            "5m" to "5min",
            "15m" to "15min",
            "30m" to "30min",
            "1h" to "1hour",
            "2h" to "2hour",
            "4h" to "4hour",
            "6h" to "6hour",
            "8h" to "8hour",
            "12h" to "12hour",
            "1d" to "1day",
            "1w" to "1week"
        )
        
        private const val API_VERSION = "2"  // V2 API Key
    }
    
    // =========================================================================
    // CAPABILITIES
    // =========================================================================
    
    override val capabilities = ExchangeCapabilities(
        supportsSpotTrading = true,
        supportsFutures = true,  // Via KuCoin Futures separate API
        supportsMargin = true,
        supportsOptions = false,
        supportsLending = true,
        supportsStaking = true,
        supportsWebSocket = true,
        supportsOrderBook = true,
        supportsOHLCV = true,
        supportsTrades = true,
        maxOrdersPerSecond = 30,
        maxSubscriptionsPerConnection = 100,
        supportedOrderTypes = listOf(
            OrderType.MARKET,
            OrderType.LIMIT,
            OrderType.STOP_LOSS,
            OrderType.STOP_LIMIT
        ),
        supportedTimeInForce = listOf(
            TimeInForce.GTC,
            TimeInForce.IOC,
            TimeInForce.FOK,
            TimeInForce.GTT  // Good Till Time
        )
    )
    
    // =========================================================================
    // ENDPOINTS
    // =========================================================================
    
    override val endpoints = ExchangeEndpoints(
        ticker = "/api/v1/market/stats",
        orderBook = "/api/v1/market/orderbook/level2_100",
        trades = "/api/v1/market/histories",
        candles = "/api/v1/market/candles",
        pairs = "/api/v2/symbols",
        balances = "/api/v1/accounts",
        placeOrder = "/api/v1/orders",
        cancelOrder = "/api/v1/orders",
        getOrder = "/api/v1/orders",
        openOrders = "/api/v1/orders",
        orderHistory = "/api/v1/orders",
        wsUrl = ""  // WebSocket URL obtained dynamically via POST /api/v1/bullet-public
    )
    
    // =========================================================================
    // AUTHENTICATION - HMAC-SHA256 + Base64 (V2 API Key)
    // =========================================================================
    
    override fun signRequest(
        method: String,
        path: String,
        params: Map<String, String>,
        body: String?,
        timestamp: Long
    ): Map<String, String> {
        val credentials = runBlocking { getCredentials() }
        
        // Build endpoint with query string for GET requests
        val endpoint = if (method == "GET" && params.isNotEmpty()) {
            "$path?" + params.entries.joinToString("&") { "${it.key}=${it.value}" }
        } else {
            path
        }
        
        // Build string to sign: timestamp + method + endpoint + body
        val bodyStr = body ?: ""
        val stringToSign = "$timestamp$method$endpoint$bodyStr"
        
        // HMAC-SHA256 + Base64
        val signature = hmacSha256Base64(stringToSign, credentials.apiSecret)
        
        // Encrypt passphrase with HMAC-SHA256 + Base64 (V2 requirement)
        val encryptedPassphrase = hmacSha256Base64(credentials.passphrase, credentials.apiSecret)
        
        return mapOf(
            "KC-API-KEY" to credentials.apiKey,
            "KC-API-SIGN" to signature,
            "KC-API-TIMESTAMP" to timestamp.toString(),
            "KC-API-PASSPHRASE" to encryptedPassphrase,
            "KC-API-KEY-VERSION" to API_VERSION,
            "Content-Type" to "application/json"
        )
    }
    
    private fun hmacSha256Base64(data: String, secret: String): String {
        val algorithm = "HmacSHA256"
        val mac = Mac.getInstance(algorithm)
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), algorithm))
        val hash = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }
    
    // =========================================================================
    // SYMBOL CONVERSION
    // =========================================================================
    
    override fun toExchangeSymbol(normalisedSymbol: String): String {
        return SYMBOL_MAP[normalisedSymbol] ?: normalisedSymbol.replace("/", "-")
    }
    
    override fun normaliseSymbol(exchangeSymbol: String): String {
        return REVERSE_SYMBOL_MAP[exchangeSymbol] ?: run {
            // Try to parse: BTC-USDT -> BTC/USDT
            if (exchangeSymbol.contains("-")) {
                exchangeSymbol.replace("-", "/")
            } else {
                exchangeSymbol
            }
        }
    }
    
    // =========================================================================
    // PARSING IMPLEMENTATIONS
    // =========================================================================
    
    override fun parseTicker(response: JsonObject, symbol: String): PriceTick? {
        return try {
            // KuCoin wraps response in "data" object
            val data = response.getAsJsonObject("data") ?: return null
            
            PriceTick(
                symbol = normaliseSymbol(symbol),
                bid = data.get("buy")?.asString?.toDoubleOrNull() ?: 0.0,
                ask = data.get("sell")?.asString?.toDoubleOrNull() ?: 0.0,
                last = data.get("last")?.asString?.toDoubleOrNull() ?: 0.0,
                volume = data.get("vol")?.asString?.toDoubleOrNull() ?: 0.0,
                high24h = data.get("high")?.asString?.toDoubleOrNull() ?: 0.0,
                low24h = data.get("low")?.asString?.toDoubleOrNull() ?: 0.0,
                change24h = data.get("changeRate")?.asString?.toDoubleOrNull()?.times(100) ?: 0.0,
                timestamp = System.currentTimeMillis(),
                exchange = "kucoin"
            )
        } catch (e: Exception) {
            null
        }
    }
    
    override fun parseOrderBook(response: JsonObject, symbol: String): OrderBook? {
        return try {
            val data = response.getAsJsonObject("data") ?: return null
            val bidsArray = data.getAsJsonArray("bids")
            val asksArray = data.getAsJsonArray("asks")
            
            val bids = bidsArray?.map { item ->
                val arr = item.asJsonArray
                OrderBookLevel(
                    price = arr[0].asString.toDouble(),
                    quantity = arr[1].asString.toDouble()
                )
            } ?: emptyList()
            
            val asks = asksArray?.map { item ->
                val arr = item.asJsonArray
                OrderBookLevel(
                    price = arr[0].asString.toDouble(),
                    quantity = arr[1].asString.toDouble()
                )
            } ?: emptyList()
            
            OrderBook(
                symbol = normaliseSymbol(symbol),
                bids = bids,
                asks = asks,
                timestamp = data.get("time")?.asLong ?: System.currentTimeMillis()
            )
        } catch (e: Exception) {
            null
        }
    }
    
    override fun parseTradingPairs(response: JsonObject): List<TradingPair> {
        return try {
            val data = response.getAsJsonArray("data") ?: return emptyList()
            
            data.mapNotNull { item ->
                val obj = item.asJsonObject
                val symbol = obj.get("symbol")?.asString ?: return@mapNotNull null
                val baseCurrency = obj.get("baseCurrency")?.asString ?: return@mapNotNull null
                val quoteCurrency = obj.get("quoteCurrency")?.asString ?: return@mapNotNull null
                val enableTrading = obj.get("enableTrading")?.asBoolean ?: false
                
                if (!enableTrading) return@mapNotNull null
                
                TradingPair(
                    symbol = normaliseSymbol(symbol),
                    baseAsset = baseCurrency,
                    quoteAsset = quoteCurrency,
                    exchangeSymbol = symbol,
                    exchange = "kucoin",
                    minQuantity = obj.get("baseMinSize")?.asString?.toDoubleOrNull() ?: 0.0,
                    maxQuantity = obj.get("baseMaxSize")?.asString?.toDoubleOrNull() ?: Double.MAX_VALUE,
                    quantityStep = obj.get("baseIncrement")?.asString?.toDoubleOrNull() ?: 0.00000001,
                    minPrice = obj.get("priceIncrement")?.asString?.toDoubleOrNull() ?: 0.0,
                    maxPrice = Double.MAX_VALUE,
                    priceStep = obj.get("priceIncrement")?.asString?.toDoubleOrNull() ?: 0.00000001,
                    minNotional = obj.get("quoteMinSize")?.asString?.toDoubleOrNull() ?: 0.0,
                    status = if (enableTrading) PairStatus.TRADING else PairStatus.HALTED
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    override fun parseBalances(response: JsonObject): List<Balance> {
        return try {
            val data = response.getAsJsonArray("data") ?: return emptyList()
            
            // Group by currency and sum across account types (trade, main, margin)
            val balanceMap = mutableMapOf<String, Balance>()
            
            data.forEach { item ->
                val obj = item.asJsonObject
                val currency = obj.get("currency")?.asString ?: return@forEach
                val type = obj.get("type")?.asString ?: "trade"
                
                // We mainly care about trade accounts for trading
                if (type != "trade") return@forEach
                
                val available = obj.get("available")?.asString?.toDoubleOrNull() ?: 0.0
                val holds = obj.get("holds")?.asString?.toDoubleOrNull() ?: 0.0
                
                balanceMap[currency] = Balance(
                    asset = currency,
                    free = available,
                    locked = holds,
                    total = available + holds
                )
            }
            
            balanceMap.values.filter { it.total > 0 }.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    override fun parseOrder(response: JsonObject): ExecutedOrder? {
        return try {
            val data = response.getAsJsonObject("data") ?: return null
            
            val orderId = data.get("id")?.asString ?: return null
            val symbol = data.get("symbol")?.asString ?: return null
            val side = data.get("side")?.asString ?: return null
            val type = data.get("type")?.asString ?: "limit"
            val price = data.get("price")?.asString?.toDoubleOrNull() ?: 0.0
            val size = data.get("size")?.asString?.toDoubleOrNull() ?: 0.0
            val dealSize = data.get("dealSize")?.asString?.toDoubleOrNull() ?: 0.0
            val dealFunds = data.get("dealFunds")?.asString?.toDoubleOrNull() ?: 0.0
            val fee = data.get("fee")?.asString?.toDoubleOrNull() ?: 0.0
            val feeCurrency = data.get("feeCurrency")?.asString ?: ""
            val isActive = data.get("isActive")?.asBoolean ?: false
            val cancelExist = data.get("cancelExist")?.asBoolean ?: false
            val createdAt = data.get("createdAt")?.asLong ?: System.currentTimeMillis()
            
            val status = when {
                cancelExist -> OrderStatus.CANCELLED
                dealSize >= size -> OrderStatus.FILLED
                dealSize > 0 -> OrderStatus.PARTIALLY_FILLED
                isActive -> OrderStatus.OPEN
                else -> OrderStatus.PENDING
            }
            
            ExecutedOrder(
                orderId = orderId,
                clientOrderId = data.get("clientOid")?.asString ?: "",
                symbol = normaliseSymbol(symbol),
                side = if (side == "buy") TradeSide.BUY else TradeSide.SELL,
                type = when (type) {
                    "market" -> OrderType.MARKET
                    "limit" -> OrderType.LIMIT
                    "stop" -> OrderType.STOP_LOSS
                    else -> OrderType.LIMIT
                },
                price = price,
                averagePrice = if (dealSize > 0) dealFunds / dealSize else price,
                requestedQuantity = size,
                executedQuantity = dealSize,
                remainingQuantity = size - dealSize,
                fee = fee,
                feeCurrency = feeCurrency,
                status = status,
                exchange = "kucoin",
                timestamp = createdAt
            )
        } catch (e: Exception) {
            null
        }
    }
    
    override fun parsePlaceOrderResponse(response: JsonObject, request: OrderRequest): OrderExecutionResult {
        return try {
            val code = response.get("code")?.asString
            
            if (code != "200000") {
                val msg = response.get("msg")?.asString ?: "Unknown error"
                return OrderExecutionResult.Rejected(msg, code ?: "UNKNOWN")
            }
            
            val data = response.getAsJsonObject("data")
            val orderId = data?.get("orderId")?.asString ?: return OrderExecutionResult.Rejected(
                "No order ID in response", "NO_ORDER_ID"
            )
            
            val order = ExecutedOrder(
                orderId = orderId,
                clientOrderId = request.clientOrderId,
                symbol = request.symbol,
                side = request.side,
                type = request.type,
                price = request.price ?: 0.0,
                averagePrice = 0.0,
                requestedQuantity = request.quantity,
                executedQuantity = 0.0,
                remainingQuantity = request.quantity,
                fee = 0.0,
                feeCurrency = "",
                status = OrderStatus.PENDING,
                exchange = "kucoin",
                timestamp = System.currentTimeMillis()
            )
            
            OrderExecutionResult.Success(order)
        } catch (e: Exception) {
            OrderExecutionResult.Error(e)
        }
    }
    
    // =========================================================================
    // ORDER BUILDING
    // =========================================================================
    
    override fun buildOrderBody(request: OrderRequest): Map<String, String> {
        val params = mutableMapOf<String, String>()
        
        // Generate client order ID if not provided
        val clientOid = if (request.clientOrderId.isNotEmpty()) {
            request.clientOrderId
        } else {
            java.util.UUID.randomUUID().toString()
        }
        
        params["clientOid"] = clientOid
        params["symbol"] = toExchangeSymbol(request.symbol)
        params["side"] = if (request.side == TradeSide.BUY || request.side == TradeSide.LONG) "buy" else "sell"
        params["type"] = when (request.type) {
            OrderType.MARKET -> "market"
            OrderType.LIMIT -> "limit"
            OrderType.STOP_LOSS, OrderType.STOP_LIMIT -> "limit"  // Stop handled separately
            else -> "market"
        }
        
        // For market orders: specify funds (quote) or size (base)
        if (request.type == OrderType.MARKET) {
            params["size"] = request.quantity.toString()
        } else {
            params["size"] = request.quantity.toString()
            request.price?.let { params["price"] = it.toString() }
        }
        
        // Time in force
        params["timeInForce"] = when (request.timeInForce) {
            TimeInForce.GTC -> "GTC"
            TimeInForce.IOC -> "IOC"
            TimeInForce.FOK -> "FOK"
            TimeInForce.GTT -> "GTT"
            else -> "GTC"
        }
        
        // Post-only (maker only)
        if (request.postOnly) {
            params["postOnly"] = "true"
        }
        
        // Hidden (iceberg orders)
        if (request.hidden) {
            params["hidden"] = "true"
        }
        
        // Trade type (always TRADE for spot)
        params["tradeType"] = "TRADE"
        
        return params
    }
    
    // =========================================================================
    // WEBSOCKET HANDLING
    // =========================================================================
    
    /**
     * KuCoin WebSocket requires obtaining a token first via POST /api/v1/bullet-public
     * The token is then used to construct the WebSocket URL
     */
    suspend fun getWebSocketToken(): String? {
        val response = publicPost("/api/v1/bullet-public", emptyMap())
        val data = response?.getAsJsonObject("data") ?: return null
        val token = data.get("token")?.asString ?: return null
        val servers = data.getAsJsonArray("instanceServers")?.firstOrNull()?.asJsonObject
        val endpoint = servers?.get("endpoint")?.asString ?: return null
        
        // Return full WebSocket URL with token
        return "$endpoint?token=$token"
    }
    
    override fun handleWsMessage(text: String) {
        try {
            val json = gson.fromJson(text, JsonObject::class.java)
            
            val type = json.get("type")?.asString ?: return
            
            when (type) {
                "welcome" -> {
                    // Connection established
                }
                "ack" -> {
                    // Subscription confirmed
                }
                "pong" -> {
                    // Heartbeat response
                }
                "message" -> {
                    val topic = json.get("topic")?.asString ?: return
                    val data = json.getAsJsonObject("data") ?: return
                    
                    when {
                        topic.startsWith("/market/ticker:") -> handleTickerMessage(data, topic)
                        topic.startsWith("/market/level2:") -> handleOrderBookMessage(data, topic)
                        topic.startsWith("/market/match:") -> handleTradeMessage(data, topic)
                        topic.startsWith("/spotMarket/tradeOrders") -> handleOrderUpdateMessage(data)
                    }
                }
            }
        } catch (e: Exception) {
            emitError(e)
        }
    }
    
    private fun handleTickerMessage(data: JsonObject, topic: String) {
        val symbolMatch = Regex("""/market/ticker:(.+)""").find(topic)
        val exchangeSymbol = symbolMatch?.groupValues?.get(1) ?: return
        
        val tick = PriceTick(
            symbol = normaliseSymbol(exchangeSymbol),
            bid = data.get("bestBid")?.asString?.toDoubleOrNull() ?: 0.0,
            ask = data.get("bestAsk")?.asString?.toDoubleOrNull() ?: 0.0,
            last = data.get("price")?.asString?.toDoubleOrNull() ?: 0.0,
            volume = data.get("size")?.asString?.toDoubleOrNull() ?: 0.0,
            timestamp = data.get("time")?.asLong ?: System.currentTimeMillis(),
            exchange = "kucoin"
        )
        
        emitPriceTick(tick)
    }
    
    private fun handleOrderBookMessage(data: JsonObject, topic: String) {
        val symbolMatch = Regex("""/market/level2:(.+)""").find(topic)
        val exchangeSymbol = symbolMatch?.groupValues?.get(1) ?: return
        
        val changes = data.getAsJsonObject("changes") ?: return
        val bidsArray = changes.getAsJsonArray("bids")
        val asksArray = changes.getAsJsonArray("asks")
        
        val bids = bidsArray?.mapNotNull { item ->
            val arr = item.asJsonArray
            if (arr.size() >= 2) {
                OrderBookLevel(
                    price = arr[0].asString.toDoubleOrNull() ?: return@mapNotNull null,
                    quantity = arr[1].asString.toDoubleOrNull() ?: return@mapNotNull null
                )
            } else null
        } ?: emptyList()
        
        val asks = asksArray?.mapNotNull { item ->
            val arr = item.asJsonArray
            if (arr.size() >= 2) {
                OrderBookLevel(
                    price = arr[0].asString.toDoubleOrNull() ?: return@mapNotNull null,
                    quantity = arr[1].asString.toDoubleOrNull() ?: return@mapNotNull null
                )
            } else null
        } ?: emptyList()
        
        emitOrderBook(OrderBook(
            symbol = normaliseSymbol(exchangeSymbol),
            bids = bids,
            asks = asks,
            timestamp = data.get("sequenceEnd")?.asLong ?: System.currentTimeMillis()
        ))
    }
    
    private fun handleTradeMessage(data: JsonObject, topic: String) {
        val symbolMatch = Regex("""/market/match:(.+)""").find(topic)
        val exchangeSymbol = symbolMatch?.groupValues?.get(1) ?: return
        
        val trade = PublicTrade(
            id = data.get("tradeId")?.asString ?: "",
            symbol = normaliseSymbol(exchangeSymbol),
            price = data.get("price")?.asString?.toDoubleOrNull() ?: 0.0,
            quantity = data.get("size")?.asString?.toDoubleOrNull() ?: 0.0,
            side = if (data.get("side")?.asString == "sell") TradeSide.SELL else TradeSide.BUY,
            timestamp = data.get("time")?.asLong?.div(1000000) ?: System.currentTimeMillis()  // nanoseconds to ms
        )
        
        emitPublicTrade(trade)
    }
    
    private fun handleOrderUpdateMessage(data: JsonObject) {
        val update = ExchangeOrderUpdate(
            orderId = data.get("orderId")?.asString ?: return,
            clientOrderId = data.get("clientOid")?.asString ?: "",
            symbol = normaliseSymbol(data.get("symbol")?.asString ?: return),
            status = when (data.get("status")?.asString) {
                "open" -> OrderStatus.OPEN
                "done" -> {
                    if (data.get("doneReason")?.asString == "canceled") {
                        OrderStatus.CANCELLED
                    } else {
                        OrderStatus.FILLED
                    }
                }
                else -> OrderStatus.OPEN
            },
            executedPrice = data.get("matchPrice")?.asString?.toDoubleOrNull(),
            executedQuantity = data.get("matchSize")?.asString?.toDoubleOrNull(),
            remainingQuantity = data.get("remainSize")?.asString?.toDoubleOrNull(),
            fee = null,
            feeCurrency = null,
            timestamp = data.get("ts")?.asLong?.div(1000000) ?: System.currentTimeMillis(),
            exchange = "kucoin"
        )
        
        emitOrderUpdate(update)
    }
    
    override fun buildWsSubscription(channels: List<String>, symbols: List<String>): String {
        val topics = mutableListOf<String>()
        
        channels.forEach { channel ->
            when (channel) {
                "ticker" -> symbols.forEach { symbol ->
                    topics.add("/market/ticker:${toExchangeSymbol(symbol)}")
                }
                "orderbook" -> symbols.forEach { symbol ->
                    topics.add("/market/level2:${toExchangeSymbol(symbol)}")
                }
                "trade", "trades" -> symbols.forEach { symbol ->
                    topics.add("/market/match:${toExchangeSymbol(symbol)}")
                }
                "orders" -> {
                    // Private channel - requires authentication in message
                    topics.add("/spotMarket/tradeOrders")
                }
            }
        }
        
        val subscriptionId = System.currentTimeMillis()
        val request = JsonObject().apply {
            addProperty("id", subscriptionId.toString())
            addProperty("type", "subscribe")
            addProperty("topic", topics.joinToString(","))
            addProperty("privateChannel", channels.contains("orders"))
            addProperty("response", true)
        }
        
        return request.toString()
    }
    
    // =========================================================================
    // HELPER METHODS
    // =========================================================================
    
    /**
     * Send WebSocket ping to keep connection alive
     * KuCoin requires ping every 30 seconds
     */
    fun buildWsPing(): String {
        return JsonObject().apply {
            addProperty("id", System.currentTimeMillis().toString())
            addProperty("type", "ping")
        }.toString()
    }
    
    /**
     * KuCoin-specific: Cancel order with different endpoint structure
     */
    suspend fun cancelOrderById(orderId: String): Boolean {
        val response = privateDelete("/api/v1/orders/$orderId")
        val code = response?.get("code")?.asString
        return code == "200000"
    }
    
    /**
     * KuCoin-specific: Cancel order by clientOid
     */
    suspend fun cancelOrderByClientOid(clientOid: String): Boolean {
        val response = privateDelete("/api/v1/order/client-order/$clientOid")
        val code = response?.get("code")?.asString
        return code == "200000"
    }
    
    /**
     * Get recent fills/trades for the account
     */
    suspend fun getRecentFills(symbol: String? = null, limit: Int = 50): List<ExecutedOrder> {
        val params = mutableMapOf<String, String>(
            "pageSize" to limit.toString()
        )
        symbol?.let { params["symbol"] = toExchangeSymbol(it) }
        
        val response = privateGet("/api/v1/fills", params) ?: return emptyList()
        val data = response.getAsJsonObject("data")?.getAsJsonArray("items") ?: return emptyList()
        
        return data.mapNotNull { item ->
            val obj = item.asJsonObject
            ExecutedOrder(
                orderId = obj.get("orderId")?.asString ?: return@mapNotNull null,
                clientOrderId = "",
                symbol = normaliseSymbol(obj.get("symbol")?.asString ?: ""),
                side = if (obj.get("side")?.asString == "buy") TradeSide.BUY else TradeSide.SELL,
                type = OrderType.LIMIT,
                price = obj.get("price")?.asString?.toDoubleOrNull() ?: 0.0,
                averagePrice = obj.get("price")?.asString?.toDoubleOrNull() ?: 0.0,
                requestedQuantity = obj.get("size")?.asString?.toDoubleOrNull() ?: 0.0,
                executedQuantity = obj.get("size")?.asString?.toDoubleOrNull() ?: 0.0,
                remainingQuantity = 0.0,
                fee = obj.get("fee")?.asString?.toDoubleOrNull() ?: 0.0,
                feeCurrency = obj.get("feeCurrency")?.asString ?: "",
                status = OrderStatus.FILLED,
                exchange = "kucoin",
                timestamp = obj.get("createdAt")?.asLong ?: System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Get 24hr statistics for all symbols
     */
    suspend fun getAll24hrStats(): Map<String, PriceTick> {
        val response = publicGet("/api/v1/market/allTickers") ?: return emptyMap()
        val data = response.getAsJsonObject("data") ?: return emptyMap()
        val ticker = data.getAsJsonArray("ticker") ?: return emptyMap()
        
        return ticker.mapNotNull { item ->
            val obj = item.asJsonObject
            val symbol = obj.get("symbol")?.asString ?: return@mapNotNull null
            
            normaliseSymbol(symbol) to PriceTick(
                symbol = normaliseSymbol(symbol),
                bid = obj.get("buy")?.asString?.toDoubleOrNull() ?: 0.0,
                ask = obj.get("sell")?.asString?.toDoubleOrNull() ?: 0.0,
                last = obj.get("last")?.asString?.toDoubleOrNull() ?: 0.0,
                volume = obj.get("vol")?.asString?.toDoubleOrNull() ?: 0.0,
                high24h = obj.get("high")?.asString?.toDoubleOrNull() ?: 0.0,
                low24h = obj.get("low")?.asString?.toDoubleOrNull() ?: 0.0,
                change24h = obj.get("changeRate")?.asString?.toDoubleOrNull()?.times(100) ?: 0.0,
                timestamp = System.currentTimeMillis(),
                exchange = "kucoin"
            )
        }.toMap()
    }
}
