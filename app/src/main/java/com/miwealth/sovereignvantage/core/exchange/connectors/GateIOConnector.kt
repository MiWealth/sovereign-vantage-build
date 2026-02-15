package com.miwealth.sovereignvantage.core.exchange.connectors

import com.miwealth.sovereignvantage.core.*
import com.miwealth.sovereignvantage.core.exchange.*
import com.miwealth.sovereignvantage.core.trading.engine.*
import com.miwealth.sovereignvantage.core.trading.routing.OrderBook
import com.miwealth.sovereignvantage.core.trading.routing.OrderBookLevel
import com.miwealth.sovereignvantage.core.security.pqc.hybrid.*
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.*
import java.security.MessageDigest

/**
 * GATE.IO CONNECTOR - Complete Implementation (PQC-Integrated)
 * 
 * Full-featured Gate.io exchange connector with:
 * - REST API V4 for spot trading
 * - WebSocket V4 for real-time market data
 * - HMAC-SHA512 authentication (Hex encoded)
 * - Request body SHA512 hashing
 * - PQC protection via HybridSecureHttpClient
 * 
 * API Documentation: https://www.gate.io/docs/developers/apiv4/
 * 
 * Authentication:
 * - KEY: API key
 * - SIGN: HexEncode(HMAC-SHA512(method\nurl\nquery\nSHA512(body)\ntimestamp))
 * - Timestamp: Unix timestamp in seconds
 * 
 * Base URLs:
 * - Production: https://api.gateio.ws
 * - WebSocket: wss://api.gateio.ws/ws/v4/
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

class GateIOConnector(
    config: ExchangeConfig,
    secureHttpClient: HybridSecureHttpClient? = null,
    credentialVault: PQCCredentialVault? = null
) : BaseCEXConnector(config, secureHttpClient, credentialVault) {
    
    companion object {
        // Gate.io uses BASE_QUOTE format (e.g., BTC_USDT)
        private val SYMBOL_MAP = mapOf(
            "BTC/USDT" to "BTC_USDT",
            "ETH/USDT" to "ETH_USDT",
            "SOL/USDT" to "SOL_USDT",
            "XRP/USDT" to "XRP_USDT",
            "ADA/USDT" to "ADA_USDT",
            "DOGE/USDT" to "DOGE_USDT",
            "AVAX/USDT" to "AVAX_USDT",
            "DOT/USDT" to "DOT_USDT",
            "LINK/USDT" to "LINK_USDT",
            "MATIC/USDT" to "MATIC_USDT",
            "ATOM/USDT" to "ATOM_USDT",
            "UNI/USDT" to "UNI_USDT",
            "LTC/USDT" to "LTC_USDT",
            "BCH/USDT" to "BCH_USDT",
            "NEAR/USDT" to "NEAR_USDT",
            "APT/USDT" to "APT_USDT",
            "ARB/USDT" to "ARB_USDT",
            "OP/USDT" to "OP_USDT",
            "INJ/USDT" to "INJ_USDT",
            "FIL/USDT" to "FIL_USDT",
            "SHIB/USDT" to "SHIB_USDT",
            "PEPE/USDT" to "PEPE_USDT",
            "GT/USDT" to "GT_USDT",  // Gate Token
            "BTC/USD" to "BTC_USD",
            "ETH/USD" to "ETH_USD",
            "ETH/BTC" to "ETH_BTC",
            "SOL/BTC" to "SOL_BTC"
        )
        
        private val REVERSE_SYMBOL_MAP = SYMBOL_MAP.entries.associate { (k, v) -> v to k }
        
        private val INTERVAL_MAP = mapOf(
            "10s" to "10s",
            "1m" to "1m",
            "5m" to "5m",
            "15m" to "15m",
            "30m" to "30m",
            "1h" to "1h",
            "4h" to "4h",
            "8h" to "8h",
            "1d" to "1d",
            "7d" to "7d",
            "30d" to "30d"
        )
        
        private const val API_PREFIX = "/api/v4"
    }
    
    // =========================================================================
    // CAPABILITIES
    // =========================================================================
    
    override val capabilities = ExchangeCapabilities(
        supportsSpotTrading = true,
        supportsFutures = true,
        supportsMargin = true,
        supportsOptions = true,
        supportsLending = true,
        supportsStaking = false,
        supportsWebSocket = true,
        supportsOrderBook = true,
        supportsOHLCV = true,
        supportsTrades = true,
        maxOrdersPerSecond = 10,
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
            TimeInForce.POC  // Pending or Cancelled
        )
    )
    
    // =========================================================================
    // ENDPOINTS
    // =========================================================================
    
    override val endpoints = ExchangeEndpoints(
        ticker = "$API_PREFIX/spot/tickers",
        orderBook = "$API_PREFIX/spot/order_book",
        trades = "$API_PREFIX/spot/trades",
        candles = "$API_PREFIX/spot/candlesticks",
        pairs = "$API_PREFIX/spot/currency_pairs",
        balances = "$API_PREFIX/spot/accounts",
        placeOrder = "$API_PREFIX/spot/orders",
        cancelOrder = "$API_PREFIX/spot/orders",
        getOrder = "$API_PREFIX/spot/orders",
        openOrders = "$API_PREFIX/spot/orders",
        orderHistory = "$API_PREFIX/spot/orders",
        wsUrl = "wss://api.gateio.ws/ws/v4/"
    )
    
    // =========================================================================
    // AUTHENTICATION - HMAC-SHA512 (Hex encoded)
    // =========================================================================
    
    /**
     * Gate.io V4 signature:
     * 1. Hash request body with SHA512 (hex)
     * 2. Build signature string: method\nurl\nquery\nbodyHash\ntimestamp
     * 3. Sign with HMAC-SHA512 (hex)
     */
    override fun signRequest(
        method: String,
        path: String,
        params: Map<String, String>,
        body: String?,
        timestamp: Long
    ): Map<String, String> {
        val credentials = runBlocking { getCredentials() }
        
        // Timestamp in seconds
        val timestampSec = (timestamp / 1000).toString()
        
        // Query string (for GET requests)
        val queryString = if (params.isNotEmpty()) {
            params.entries.joinToString("&") { "${it.key}=${it.value}" }
        } else {
            ""
        }
        
        // Hash the body with SHA512
        val bodyStr = body ?: ""
        val hashedBody = sha512Hex(bodyStr)
        
        // Build signature string: method\nurl\nquery\nbodyHash\ntimestamp
        val signatureString = buildString {
            append(method.uppercase())
            append("\n")
            append(path)
            append("\n")
            append(queryString)
            append("\n")
            append(hashedBody)
            append("\n")
            append(timestampSec)
        }
        
        // HMAC-SHA512 signature (hex)
        val signature = hmacSha512Hex(signatureString, credentials.apiSecret)
        
        return mapOf(
            "KEY" to credentials.apiKey,
            "SIGN" to signature,
            "Timestamp" to timestampSec,
            "Content-Type" to "application/json",
            "Accept" to "application/json"
        )
    }
    
    private fun sha512Hex(data: String): String {
        val md = MessageDigest.getInstance("SHA-512")
        val hash = md.digest(data.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
    
    private fun hmacSha512Hex(data: String, secret: String): String {
        val algorithm = "HmacSHA512"
        val mac = Mac.getInstance(algorithm)
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), algorithm))
        val hash = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
    
    // =========================================================================
    // SYMBOL CONVERSION
    // =========================================================================
    
    override fun toExchangeSymbol(normalisedSymbol: String): String {
        return SYMBOL_MAP[normalisedSymbol] ?: normalisedSymbol.replace("/", "_")
    }
    
    override fun normaliseSymbol(exchangeSymbol: String): String {
        return REVERSE_SYMBOL_MAP[exchangeSymbol] ?: run {
            // Try to parse: BTC_USDT -> BTC/USDT
            if (exchangeSymbol.contains("_")) {
                exchangeSymbol.replace("_", "/")
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
            // Gate.io returns array for single ticker query
            // For single symbol, response is the ticker object directly
            val data = if (response.has("currency_pair")) {
                response
            } else {
                // If wrapped in array
                response.getAsJsonArray("result")?.firstOrNull()?.asJsonObject ?: return null
            }
            
            PriceTick(
                symbol = normaliseSymbol(data.get("currency_pair")?.asString ?: symbol),
                bid = data.get("highest_bid")?.asString?.toDoubleOrNull() ?: 0.0,
                ask = data.get("lowest_ask")?.asString?.toDoubleOrNull() ?: 0.0,
                last = data.get("last")?.asString?.toDoubleOrNull() ?: 0.0,
                volume = data.get("base_volume")?.asString?.toDoubleOrNull() ?: 0.0,
                high24h = data.get("high_24h")?.asString?.toDoubleOrNull() ?: 0.0,
                low24h = data.get("low_24h")?.asString?.toDoubleOrNull() ?: 0.0,
                change24h = data.get("change_percentage")?.asString?.toDoubleOrNull() ?: 0.0,
                timestamp = System.currentTimeMillis(),
                exchange = "gateio"
            )
        } catch (e: Exception) {
            null
        }
    }
    
    override fun parseOrderBook(response: JsonObject, symbol: String): OrderBook? {
        return try {
            val bidsArray = response.getAsJsonArray("bids")
            val asksArray = response.getAsJsonArray("asks")
            
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
                symbol = normaliseSymbol(response.get("id")?.asString ?: symbol),
                bids = bids,
                asks = asks,
                timestamp = response.get("current")?.asLong ?: System.currentTimeMillis()
            )
        } catch (e: Exception) {
            null
        }
    }
    
    override fun parseTradingPairs(response: JsonObject): List<TradingPair> {
        return try {
            // Gate.io returns array directly for currency pairs
            val data = response.getAsJsonArray("result") 
                ?: response.entrySet().firstOrNull()?.value?.asJsonArray 
                ?: return emptyList()
            
            data.mapNotNull { item ->
                val obj = item.asJsonObject
                val id = obj.get("id")?.asString ?: return@mapNotNull null
                val base = obj.get("base")?.asString ?: return@mapNotNull null
                val quote = obj.get("quote")?.asString ?: return@mapNotNull null
                val tradeStatus = obj.get("trade_status")?.asString ?: "tradable"
                
                if (tradeStatus != "tradable") return@mapNotNull null
                
                TradingPair(
                    symbol = normaliseSymbol(id),
                    baseAsset = base,
                    quoteAsset = quote,
                    exchangeSymbol = id,
                    exchange = "gateio",
                    minQuantity = obj.get("min_base_amount")?.asString?.toDoubleOrNull() ?: 0.0,
                    maxQuantity = obj.get("max_base_amount")?.asString?.toDoubleOrNull() ?: Double.MAX_VALUE,
                    quantityStep = obj.get("amount_precision")?.asInt?.let { 1.0 / Math.pow(10.0, it.toDouble()) } ?: 0.00000001,
                    minPrice = obj.get("precision")?.asInt?.let { 1.0 / Math.pow(10.0, it.toDouble()) } ?: 0.0,
                    maxPrice = Double.MAX_VALUE,
                    priceStep = obj.get("precision")?.asInt?.let { 1.0 / Math.pow(10.0, it.toDouble()) } ?: 0.00000001,
                    minNotional = obj.get("min_quote_amount")?.asString?.toDoubleOrNull() ?: 0.0,
                    status = if (tradeStatus == "tradable") PairStatus.TRADING else PairStatus.HALTED
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    override fun parseBalances(response: JsonObject): List<Balance> {
        return try {
            // Gate.io returns array of balances
            val data = response.getAsJsonArray("result") 
                ?: response.entrySet().firstOrNull()?.value?.asJsonArray
                ?: return emptyList()
            
            data.mapNotNull { item ->
                val obj = item.asJsonObject
                val currency = obj.get("currency")?.asString ?: return@mapNotNull null
                val available = obj.get("available")?.asString?.toDoubleOrNull() ?: 0.0
                val locked = obj.get("locked")?.asString?.toDoubleOrNull() ?: 0.0
                
                if (available + locked <= 0) return@mapNotNull null
                
                Balance(
                    asset = currency,
                    free = available,
                    locked = locked,
                    total = available + locked
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    override fun parseOrder(response: JsonObject): ExecutedOrder? {
        return try {
            val data = if (response.has("id")) response else response.getAsJsonObject("result") ?: return null
            
            val id = data.get("id")?.asString ?: return null
            val currencyPair = data.get("currency_pair")?.asString ?: return null
            val side = data.get("side")?.asString ?: return null
            val type = data.get("type")?.asString ?: "limit"
            val price = data.get("price")?.asString?.toDoubleOrNull() ?: 0.0
            val amount = data.get("amount")?.asString?.toDoubleOrNull() ?: 0.0
            val filledAmount = data.get("filled_amount")?.asString?.toDoubleOrNull() ?: 0.0
            val fillPrice = data.get("fill_price")?.asString?.toDoubleOrNull() ?: price
            val fee = data.get("fee")?.asString?.toDoubleOrNull() ?: 0.0
            val feeCurrency = data.get("fee_currency")?.asString ?: ""
            val status = data.get("status")?.asString ?: "open"
            val createTime = data.get("create_time")?.asString?.toLongOrNull()?.times(1000) ?: System.currentTimeMillis()
            
            ExecutedOrder(
                orderId = id,
                clientOrderId = data.get("text")?.asString ?: "",  // Gate.io uses "text" for client order ID
                symbol = normaliseSymbol(currencyPair),
                side = if (side == "buy") TradeSide.BUY else TradeSide.SELL,
                type = when (type) {
                    "market" -> OrderType.MARKET
                    "limit" -> OrderType.LIMIT
                    else -> OrderType.LIMIT
                },
                price = price,
                averagePrice = if (filledAmount > 0) fillPrice else price,
                requestedQuantity = amount,
                executedQuantity = filledAmount,
                remainingQuantity = amount - filledAmount,
                fee = fee,
                feeCurrency = feeCurrency,
                status = when (status) {
                    "open" -> OrderStatus.OPEN
                    "closed" -> OrderStatus.FILLED
                    "cancelled" -> OrderStatus.CANCELLED
                    else -> OrderStatus.OPEN
                },
                exchange = "gateio",
                timestamp = createTime
            )
        } catch (e: Exception) {
            null
        }
    }
    
    override fun parsePlaceOrderResponse(response: JsonObject, request: OrderRequest): OrderExecutionResult {
        return try {
            // Check for error
            if (response.has("label")) {
                val label = response.get("label")?.asString ?: "UNKNOWN"
                val message = response.get("message")?.asString ?: "Unknown error"
                return OrderExecutionResult.Rejected(message, label)
            }
            
            val orderId = response.get("id")?.asString ?: return OrderExecutionResult.Rejected(
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
                exchange = "gateio",
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
        
        params["currency_pair"] = toExchangeSymbol(request.symbol)
        params["side"] = if (request.side == TradeSide.BUY || request.side == TradeSide.LONG) "buy" else "sell"
        params["type"] = when (request.type) {
            OrderType.MARKET -> "market"
            OrderType.LIMIT -> "limit"
            else -> "limit"
        }
        params["amount"] = request.quantity.toString()
        params["account"] = "spot"
        
        // Price for limit orders
        if (request.type == OrderType.LIMIT || request.type == OrderType.STOP_LIMIT) {
            request.price?.let { params["price"] = it.toString() }
        }
        
        // Time in force
        params["time_in_force"] = when (request.timeInForce) {
            TimeInForce.GTC -> "gtc"
            TimeInForce.IOC -> "ioc"
            TimeInForce.POC -> "poc"
            else -> "gtc"
        }
        
        // Client order ID (Gate.io calls it "text")
        if (request.clientOrderId.isNotEmpty()) {
            params["text"] = "t-${request.clientOrderId}"
        }
        
        // Iceberg order
        if (request.hidden) {
            params["iceberg"] = request.quantity.div(10).toString()  // Show 10% of order
        }
        
        return params
    }
    
    // =========================================================================
    // WEBSOCKET HANDLING
    // =========================================================================
    
    override fun handleWsMessage(text: String) {
        try {
            val json = gson.fromJson(text, JsonObject::class.java)
            
            val channel = json.get("channel")?.asString ?: return
            val event = json.get("event")?.asString ?: return
            
            when (event) {
                "subscribe" -> {
                    // Subscription confirmed
                    val status = json.get("result")?.asJsonObject?.get("status")?.asString
                    if (status != "success") {
                        emitError(Exception("Subscription failed: $text"))
                    }
                }
                "update" -> {
                    val result = json.get("result") ?: return
                    
                    when (channel) {
                        "spot.tickers" -> handleTickerMessage(result.asJsonObject)
                        "spot.order_book" -> handleOrderBookMessage(result.asJsonObject)
                        "spot.trades" -> handleTradeMessage(result.asJsonArray)
                        "spot.orders" -> handleOrderUpdateMessage(result.asJsonArray)
                    }
                }
                "pong" -> {
                    // Heartbeat response
                }
            }
        } catch (e: Exception) {
            emitError(e)
        }
    }
    
    private fun handleTickerMessage(data: JsonObject) {
        val currencyPair = data.get("currency_pair")?.asString ?: return
        
        val tick = PriceTick(
            symbol = normaliseSymbol(currencyPair),
            bid = data.get("highest_bid")?.asString?.toDoubleOrNull() ?: 0.0,
            ask = data.get("lowest_ask")?.asString?.toDoubleOrNull() ?: 0.0,
            last = data.get("last")?.asString?.toDoubleOrNull() ?: 0.0,
            volume = data.get("base_volume")?.asString?.toDoubleOrNull() ?: 0.0,
            timestamp = System.currentTimeMillis(),
            exchange = "gateio"
        )
        
        emitPriceTick(tick)
    }
    
    private fun handleOrderBookMessage(data: JsonObject) {
        val currencyPair = data.get("s")?.asString ?: return
        val bidsArray = data.getAsJsonArray("bids")
        val asksArray = data.getAsJsonArray("asks")
        
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
            symbol = normaliseSymbol(currencyPair),
            bids = bids,
            asks = asks,
            timestamp = data.get("t")?.asLong ?: System.currentTimeMillis()
        ))
    }
    
    private fun handleTradeMessage(data: JsonArray) {
        data.forEach { item ->
            val obj = item.asJsonObject
            val currencyPair = obj.get("currency_pair")?.asString ?: return@forEach
            
            val trade = PublicTrade(
                id = obj.get("id")?.asString ?: "",
                symbol = normaliseSymbol(currencyPair),
                price = obj.get("price")?.asString?.toDoubleOrNull() ?: 0.0,
                quantity = obj.get("amount")?.asString?.toDoubleOrNull() ?: 0.0,
                side = if (obj.get("side")?.asString == "sell") TradeSide.SELL else TradeSide.BUY,
                timestamp = obj.get("create_time")?.asLong?.times(1000) ?: System.currentTimeMillis()
            )
            
            emitPublicTrade(trade)
        }
    }
    
    private fun handleOrderUpdateMessage(data: JsonArray) {
        data.forEach { item ->
            val obj = item.asJsonObject
            
            val update = ExchangeOrderUpdate(
                orderId = obj.get("id")?.asString ?: return@forEach,
                clientOrderId = obj.get("text")?.asString?.removePrefix("t-") ?: "",
                symbol = normaliseSymbol(obj.get("currency_pair")?.asString ?: return@forEach),
                status = when (obj.get("event")?.asString) {
                    "put" -> OrderStatus.OPEN
                    "update" -> OrderStatus.PARTIALLY_FILLED
                    "finish" -> {
                        val finishAs = obj.get("finish_as")?.asString
                        when (finishAs) {
                            "filled" -> OrderStatus.FILLED
                            "cancelled" -> OrderStatus.CANCELLED
                            else -> OrderStatus.FILLED
                        }
                    }
                    else -> OrderStatus.OPEN
                },
                executedPrice = obj.get("fill_price")?.asString?.toDoubleOrNull(),
                executedQuantity = obj.get("filled_amount")?.asString?.toDoubleOrNull(),
                remainingQuantity = obj.get("left")?.asString?.toDoubleOrNull(),
                fee = obj.get("fee")?.asString?.toDoubleOrNull(),
                feeCurrency = obj.get("fee_currency")?.asString,
                timestamp = obj.get("create_time_ms")?.asLong ?: System.currentTimeMillis(),
                exchange = "gateio"
            )
            
            emitOrderUpdate(update)
        }
    }
    
    override fun buildWsSubscription(channels: List<String>, symbols: List<String>): String {
        val subscriptions = mutableListOf<JsonObject>()
        
        channels.forEach { channel ->
            val gateChannel = when (channel) {
                "ticker" -> "spot.tickers"
                "orderbook" -> "spot.order_book"
                "trade", "trades" -> "spot.trades"
                "orders" -> "spot.orders"  // Private channel
                else -> return@forEach
            }
            
            val timestamp = System.currentTimeMillis() / 1000
            
            val request = JsonObject().apply {
                addProperty("time", timestamp)
                addProperty("channel", gateChannel)
                addProperty("event", "subscribe")
                
                // Add payload with symbols
                val payload = JsonArray()
                symbols.forEach { symbol ->
                    payload.add(toExchangeSymbol(symbol))
                }
                add("payload", payload)
            }
            
            // For private channels, add authentication
            if (channel == "orders") {
                val credentials = runBlocking { getCredentials() }
                val signStr = "channel=$gateChannel&event=subscribe&time=$timestamp"
                val signature = hmacSha512Hex(signStr, credentials.apiSecret)
                
                val auth = JsonObject().apply {
                    addProperty("method", "api_key")
                    addProperty("KEY", credentials.apiKey)
                    addProperty("SIGN", signature)
                }
                request.add("auth", auth)
            }
            
            subscriptions.add(request)
        }
        
        // Return first subscription (multiple should be sent separately)
        return subscriptions.firstOrNull()?.toString() ?: "{}"
    }
    
    /**
     * Build WebSocket ping message
     */
    fun buildWsPing(): String {
        return JsonObject().apply {
            addProperty("time", System.currentTimeMillis() / 1000)
            addProperty("channel", "spot.ping")
        }.toString()
    }
    
    // =========================================================================
    // HELPER METHODS
    // =========================================================================
    
    /**
     * Get ticker for specific currency pair
     */
    suspend fun getTickerByPair(symbol: String): PriceTick? {
        val params = mapOf("currency_pair" to toExchangeSymbol(symbol))
        val response = publicGet(endpoints.ticker, params) ?: return null
        
        // Gate.io returns array even for single ticker
        return try {
            val arr = response.getAsJsonArray("result") 
                ?: gson.fromJson(response.toString(), JsonArray::class.java)
            val obj = arr.firstOrNull()?.asJsonObject ?: return null
            parseTicker(obj, symbol)
        } catch (e: Exception) {
            parseTicker(response, symbol)
        }
    }
    
    /**
     * Get all tickers at once
     */
    suspend fun getAllTickers(): Map<String, PriceTick> {
        val response = publicGet(endpoints.ticker) ?: return emptyMap()
        
        return try {
            val arr = response.getAsJsonArray("result")
                ?: gson.fromJson(response.toString(), JsonArray::class.java)
                ?: return emptyMap()
            
            arr.mapNotNull { item ->
                val obj = item.asJsonObject
                val currencyPair = obj.get("currency_pair")?.asString ?: return@mapNotNull null
                val tick = parseTicker(obj, currencyPair) ?: return@mapNotNull null
                normaliseSymbol(currencyPair) to tick
            }.toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    /**
     * Cancel order by ID
     */
    suspend fun cancelOrderById(orderId: String, symbol: String): Boolean {
        val params = mapOf("currency_pair" to toExchangeSymbol(symbol))
        val response = privateDelete("${endpoints.cancelOrder}/$orderId", params)
        return response != null && !response.has("label")
    }
    
    /**
     * Get order by ID
     */
    suspend fun getOrderById(orderId: String, symbol: String): ExecutedOrder? {
        val params = mapOf("currency_pair" to toExchangeSymbol(symbol))
        val response = privateGet("${endpoints.getOrder}/$orderId", params) ?: return null
        return parseOrder(response)
    }
    
    /**
     * Get trading fee for a currency pair
     */
    suspend fun getTradingFee(symbol: String? = null): Pair<Double, Double>? {
        val params = if (symbol != null) {
            mapOf("currency_pair" to toExchangeSymbol(symbol))
        } else {
            emptyMap()
        }
        
        val response = privateGet("$API_PREFIX/spot/fee", params) ?: return null
        
        return try {
            val makerFee = response.get("maker_fee")?.asString?.toDoubleOrNull() ?: 0.002
            val takerFee = response.get("taker_fee")?.asString?.toDoubleOrNull() ?: 0.002
            makerFee to takerFee
        } catch (e: Exception) {
            null
        }
    }
}
