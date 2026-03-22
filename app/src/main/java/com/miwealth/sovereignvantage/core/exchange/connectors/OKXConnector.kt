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
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.*

/**
 * OKX CONNECTOR - Complete Implementation
 * 
 * Full-featured OKX exchange connector with:
 * - REST API V5 for unified trading
 * - WebSocket for real-time market data
 * - HMAC-SHA256 + Base64 authentication
 * - Support for spot, futures, perpetuals, options
 * 
 * Based on OKX API V5:
 * - REST: https://www.okx.com
 * - WebSocket Public: wss://ws.okx.com:8443/ws/v5/public
 * - WebSocket Private: wss://ws.okx.com:8443/ws/v5/private
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

class OKXConnector(
    config: ExchangeConfig,
    secureHttpClient: HybridSecureHttpClient? = null,
    credentialVault: PQCCredentialVault? = null
) : BaseCEXConnector(config, secureHttpClient, credentialVault) {
    
    companion object {
        // OKX uses hyphenated format for spot: BTC-USDT
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
            "BTC/USD" to "BTC-USD",
            "ETH/USD" to "ETH-USD"
        )
        
        private val REVERSE_SYMBOL_MAP = SYMBOL_MAP.entries.associate { (k, v) -> v to k }
        
        private val INTERVAL_MAP = mapOf(
            "1m" to "1m",
            "3m" to "3m",
            "5m" to "5m",
            "15m" to "15m",
            "30m" to "30m",
            "1h" to "1H",
            "2h" to "2H",
            "4h" to "4H",
            "1d" to "1D",
            "1w" to "1W"
        )
    }
    
    // =========================================================================
    // ABSTRACT OVERRIDES - Wire base class flow to OKX V5 API
    // =========================================================================
    
    override val endpoints = ExchangeEndpoints(
        ticker = "/api/v5/market/ticker",
        orderBook = "/api/v5/market/books",
        trades = "/api/v5/market/trades",
        candles = "/api/v5/market/candles",
        pairs = "/api/v5/public/instruments",
        balances = "/api/v5/account/balance",
        placeOrder = "/api/v5/trade/order",
        cancelOrder = "/api/v5/trade/cancel-order",
        getOrder = "/api/v5/trade/order",
        openOrders = "/api/v5/trade/orders-pending",
        orderHistory = "/api/v5/trade/orders-history-archive",
        wsUrl = "wss://ws.okx.com:8443/ws/v5/public"
    )
    
    override fun signRequest(
        method: String, path: String, params: Map<String, String>, body: String?, timestamp: Long
    ): Map<String, String> = signRequest(method, path, params, body)
    
    override fun parsePlaceOrderResponse(response: JsonObject, request: OrderRequest): OrderExecutionResult {
        return try {
            val code = response.get("code")?.asString ?: "-1"
            if (code != "0") {
                val msg = response.get("msg")?.asString ?: "Unknown error"
                return OrderExecutionResult.Rejected(msg, code)
            }
            val data = response.getAsJsonArray("data")?.firstOrNull()?.asJsonObject
            val orderId = data?.get("ordId")?.asString ?: ""
            val sCode = data?.get("sCode")?.asString ?: "0"
            if (sCode != "0") {
                val sMsg = data?.get("sMsg")?.asString ?: "Order rejected"
                return OrderExecutionResult.Rejected(sMsg, sCode)
            }
            val order = ExecutedOrder(
                orderId = orderId,
                clientOrderId = data?.get("clOrdId")?.asString ?: "",
                symbol = request.symbol,
                side = request.side,
                type = request.type,
                price = request.price ?: 0.0,
                executedPrice = 0.0,
                quantity = request.quantity,
                executedQuantity = 0.0,
                fee = 0.0,
                feeCurrency = "USDT",
                status = OrderStatus.OPEN,
                exchange = "okx"
            )
            OrderExecutionResult.Success(order)
        } catch (e: Exception) {
            OrderExecutionResult.Error(e)
        }
    }
    
    override fun buildOrderBody(request: OrderRequest): Map<String, String> {
        val params = mutableMapOf<String, String>()
        params["instId"] = toExchangeSymbol(request.symbol)
        params["tdMode"] = "cash"
        params["side"] = if (request.side == TradeSide.BUY || request.side == TradeSide.LONG) "buy" else "sell"
        params["ordType"] = when (request.type) {
            OrderType.MARKET -> "market"
            OrderType.LIMIT -> "limit"
            OrderType.STOP_LOSS -> "trigger"
            OrderType.STOP_LIMIT -> "trigger"
            else -> "market"
        }
        params["sz"] = request.quantity.toString()
        if (request.type == OrderType.LIMIT || request.type == OrderType.STOP_LIMIT) {
            request.price?.let { params["px"] = it.toString() }
        }
        if (request.type == OrderType.STOP_LOSS || request.type == OrderType.STOP_LIMIT) {
            request.stopPrice?.let {
                params["triggerPx"] = it.toString()
                params["triggerPxType"] = "last"
            }
        }
        if (request.clientOrderId.isNotEmpty()) params["clOrdId"] = request.clientOrderId
        return params
    }
    
    override fun buildWsSubscription(channels: List<String>, symbols: List<String>): String {
        val args = mutableListOf<JsonObject>()
        for (channel in channels) {
            for (symbol in symbols) {
                val instId = toExchangeSymbol(symbol)
                when (channel) {
                    "ticker" -> args.add(JsonObject().apply {
                        addProperty("channel", "tickers")
                        addProperty("instId", instId)
                    })
                    "orderbook" -> args.add(JsonObject().apply {
                        addProperty("channel", "books5")
                        addProperty("instId", instId)
                    })
                    "trade" -> args.add(JsonObject().apply {
                        addProperty("channel", "trades")
                        addProperty("instId", instId)
                    })
                }
            }
        }
        val request = JsonObject().apply {
            addProperty("op", "subscribe")
            add("args", JsonArray().apply { args.forEach { add(it) } })
        }
        return request.toString()
    }
    
    override val capabilities = ExchangeCapabilities(
        supportsSpotTrading = true,
        supportsFutures = true,
        supportsMargin = true,
        supportsOptions = true,
        supportsLending = true,
        supportsStaking = true,
        supportsWebSocket = true,
        supportsOrderBook = true,
        supportsOHLCV = true,
        supportsTrades = true,
        maxOrdersPerSecond = 20,
        maxSubscriptionsPerConnection = 100,
        supportedOrderTypes = listOf(
            OrderType.MARKET,
            OrderType.LIMIT,
            OrderType.STOP_LOSS,
            OrderType.STOP_LIMIT,
            OrderType.TRAILING_STOP
        ),
        supportedTimeInForce = listOf(
            TimeInForce.GTC,
            TimeInForce.IOC,
            TimeInForce.FOK
        )
    )
    
    // =========================================================================
    // AUTHENTICATION - HMAC-SHA256 + Base64
    // =========================================================================
    
    fun signRequest(
        method: String,
        endpoint: String,
        params: Map<String, String>,
        body: String?
    ): Map<String, String> {
        val timestamp = Instant.now().toString()  // ISO 8601 format
        
        // Build the prehash string: timestamp + method + requestPath + body
        val requestPath = if (params.isNotEmpty() && method == "GET") {
            "$endpoint?${params.entries.joinToString("&") { "${it.key}=${it.value}" }}"
        } else {
            endpoint
        }
        
        val prehash = "$timestamp${method.uppercase()}$requestPath${body ?: ""}"
        
        // Sign with HMAC-SHA256 and encode to Base64
        val signature = hmacSha256Base64(prehash, config.apiSecret)
        
        return mapOf(
            "OK-ACCESS-KEY" to config.apiKey,
            "OK-ACCESS-SIGN" to signature,
            "OK-ACCESS-TIMESTAMP" to timestamp,
            "OK-ACCESS-PASSPHRASE" to (config.passphrase ?: ""),
            "Content-Type" to "application/json"
        )
    }
    
    private fun hmacSha256Base64(data: String, secret: String): String {
        val algorithm = "HmacSHA256"
        val mac = Mac.getInstance(algorithm)
        mac.init(SecretKeySpec(secret.toByteArray(), algorithm))
        val hash = mac.doFinal(data.toByteArray())
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }
    
    // =========================================================================
    // SYMBOL CONVERSION
    // =========================================================================
    
    override fun toExchangeSymbol(normalisedSymbol: String): String {
        return SYMBOL_MAP[normalisedSymbol] ?: normalisedSymbol.replace("/", "-")
    }
    
    override fun normaliseSymbol(exchangeSymbol: String): String {
        return REVERSE_SYMBOL_MAP[exchangeSymbol] ?: exchangeSymbol.replace("-", "/")
    }
    
    // =========================================================================
    // PARSING IMPLEMENTATIONS
    // =========================================================================
    
    override fun parseTicker(json: JsonObject, symbol: String): PriceTick? {
        return try {
            val data = json.getAsJsonArray("data")?.firstOrNull()?.asJsonObject ?: return null
            
            PriceTick(
                symbol = normaliseSymbol(symbol),
                bid = data.get("bidPx")?.asString?.toDoubleOrNull() ?: 0.0,
                ask = data.get("askPx")?.asString?.toDoubleOrNull() ?: 0.0,
                last = data.get("last")?.asString?.toDoubleOrNull() ?: 0.0,
                volume = data.get("vol24h")?.asString?.toDoubleOrNull() ?: 0.0,
                timestamp = data.get("ts")?.asString?.toLongOrNull() ?: System.currentTimeMillis(),
                exchange = "okx"
            )
        } catch (e: Exception) {
            null
        }
    }
    
    override fun parseOrderBook(json: JsonObject, symbol: String): OrderBook? {
        return try {
            val data = json.getAsJsonArray("data")?.firstOrNull()?.asJsonObject ?: return null
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
                timestamp = data.get("ts")?.asString?.toLongOrNull() ?: System.currentTimeMillis()
            )
        } catch (e: Exception) {
            null
        }
    }
    
    override fun parseTradingPairs(json: JsonObject): List<TradingPair> {
        return try {
            val data = json.getAsJsonArray("data") ?: return emptyList()
            
            data.mapNotNull { item ->
                val obj = item.asJsonObject
                val instId = obj.get("instId")?.asString ?: return@mapNotNull null
                
                // Only process SPOT instruments
                if (obj.get("instType")?.asString != "SPOT") return@mapNotNull null
                
                TradingPair(
                    symbol = normaliseSymbol(instId),
                    baseAsset = obj.get("baseCcy")?.asString ?: "",
                    quoteAsset = obj.get("quoteCcy")?.asString ?: "",
                    exchangeSymbol = instId,
                    exchange = "okx",
                    status = if (obj.get("state")?.asString == "live") PairStatus.TRADING else PairStatus.HALTED,
                    minQuantity = obj.get("minSz")?.asString?.toDoubleOrNull() ?: 0.0,
                    maxQuantity = obj.get("maxSz")?.asString?.toDoubleOrNull() ?: Double.MAX_VALUE,
                    quantityStep = obj.get("lotSz")?.asString?.toDoubleOrNull() ?: 0.00001,
                    minPrice = 0.0,
                    maxPrice = Double.MAX_VALUE,
                    priceStep = obj.get("tickSz")?.asString?.toDoubleOrNull() ?: 0.01
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    override fun parseBalances(json: JsonObject): List<Balance> {
        return try {
            val data = json.getAsJsonArray("data")?.firstOrNull()?.asJsonObject ?: return emptyList()
            val details = data.getAsJsonArray("details") ?: return emptyList()
            
            details.mapNotNull { item ->
                val obj = item.asJsonObject
                val ccy = obj.get("ccy")?.asString ?: return@mapNotNull null
                
                Balance(
                    asset = ccy,
                    free = obj.get("availBal")?.asString?.toDoubleOrNull() ?: 0.0,
                    locked = obj.get("frozenBal")?.asString?.toDoubleOrNull() ?: 0.0,
                    total = obj.get("cashBal")?.asString?.toDoubleOrNull() ?: 0.0
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    override fun parseOrder(json: JsonObject): ExecutedOrder? {
        return try {
            val data = json.getAsJsonArray("data")?.firstOrNull()?.asJsonObject ?: return null
            parseOrderFromObject(data)
        } catch (e: Exception) {
            null
        }
    }
    
    private fun parseOrderFromObject(obj: JsonObject): ExecutedOrder {
        val instId = obj.get("instId")?.asString ?: ""
        val side = obj.get("side")?.asString?.uppercase() ?: "BUY"
        val orderType = obj.get("ordType")?.asString?.uppercase() ?: "MARKET"
        val state = obj.get("state")?.asString?.lowercase() ?: "live"
        
        return ExecutedOrder(
            orderId = obj.get("ordId")?.asString ?: "",
            clientOrderId = obj.get("clOrdId")?.asString ?: "",
            symbol = normaliseSymbol(instId),
            side = if (side == "SELL") TradeSide.SELL else TradeSide.BUY,
            type = when (orderType) {
                "LIMIT" -> OrderType.LIMIT
                "STOP", "TRIGGER" -> OrderType.STOP_LOSS
                else -> OrderType.MARKET
            },
            price = obj.get("px")?.asString?.toDoubleOrNull() ?: 0.0,
            executedPrice = obj.get("avgPx")?.asString?.toDoubleOrNull() ?: 0.0,
            quantity = obj.get("sz")?.asString?.toDoubleOrNull() ?: 0.0,
            executedQuantity = obj.get("accFillSz")?.asString?.toDoubleOrNull() ?: 0.0,
            fee = obj.get("fee")?.asString?.toDoubleOrNull()?.let { kotlin.math.abs(it) } ?: 0.0,
            feeCurrency = obj.get("feeCcy")?.asString ?: "USDT",
            status = when (state) {
                "live" -> OrderStatus.OPEN
                "partially_filled" -> OrderStatus.PARTIALLY_FILLED
                "filled" -> OrderStatus.FILLED
                "canceled", "cancelled" -> OrderStatus.CANCELLED
                else -> OrderStatus.PENDING
            },
            timestamp = obj.get("cTime")?.asString?.toLongOrNull() ?: System.currentTimeMillis(),
            exchange = "okx"
        )
    }
    
    // parsePlaceOrderResponse logic merged into 2-param override above
    
    // =========================================================================
    // buildOrderBody logic merged into Map<String,String> override above
    
    // =========================================================================
    // WEBSOCKET HANDLING
    // =========================================================================
    
    override fun handleWsMessage(message: String) {
        try {
            val json = gson.fromJson(message, JsonObject::class.java)
            
            // Check for event messages (login, subscribe, etc.)
            if (json.has("event")) {
                val event = json.get("event")?.asString
                if (event == "error") {
                    val msg = json.get("msg")?.asString ?: "WebSocket error"
                    emitError(Exception(msg))
                }
                return
            }
            
            // Check for data messages
            val arg = json.getAsJsonObject("arg") ?: return
            val channel = arg.get("channel")?.asString ?: return
            val data = json.getAsJsonArray("data") ?: return
            
            when (channel) {
                "tickers" -> handleTickerMessage(data, arg)
                "books5", "books" -> handleOrderBookMessage(data, arg)
                "trades" -> handleTradeMessage(data, arg)
            }
        } catch (e: Exception) {
            emitError(e)
        }
    }
    
    private fun handleTickerMessage(data: JsonArray, arg: JsonObject) {
        val instId = arg.get("instId")?.asString ?: return
        val tickerData = data.firstOrNull()?.asJsonObject ?: return
        
        val tick = PriceTick(
            symbol = normaliseSymbol(instId),
            bid = tickerData.get("bidPx")?.asString?.toDoubleOrNull() ?: 0.0,
            ask = tickerData.get("askPx")?.asString?.toDoubleOrNull() ?: 0.0,
            last = tickerData.get("last")?.asString?.toDoubleOrNull() ?: 0.0,
            volume = tickerData.get("vol24h")?.asString?.toDoubleOrNull() ?: 0.0,
            timestamp = tickerData.get("ts")?.asString?.toLongOrNull() ?: System.currentTimeMillis(),
            exchange = "okx"
        )
        
        emitPriceTick(tick)
    }
    
    private fun handleOrderBookMessage(data: JsonArray, arg: JsonObject) {
        val instId = arg.get("instId")?.asString ?: return
        val bookData = data.firstOrNull()?.asJsonObject ?: return
        
        val bidsArray = bookData.getAsJsonArray("bids")
        val asksArray = bookData.getAsJsonArray("asks")
        
        val bids = bidsArray?.map { item ->
            val arr = item.asJsonArray
            OrderBookLevel(arr[0].asString.toDouble(), arr[1].asString.toDouble())
        } ?: emptyList()
        
        val asks = asksArray?.map { item ->
            val arr = item.asJsonArray
            OrderBookLevel(arr[0].asString.toDouble(), arr[1].asString.toDouble())
        } ?: emptyList()
        
        emitOrderBook(OrderBook(
            symbol = normaliseSymbol(instId),
            bids = bids,
            asks = asks,
            timestamp = bookData.get("ts")?.asString?.toLongOrNull() ?: System.currentTimeMillis()
        ))
    }
    
    private fun handleTradeMessage(data: JsonArray, arg: JsonObject) {
        val instId = arg.get("instId")?.asString ?: return
        
        data.forEach { item ->
            val trade = item.asJsonObject
            
            val publicTrade = PublicTrade(
                tradeId = trade.get("tradeId")?.asString ?: "",
                exchange = "okx",
                symbol = normaliseSymbol(instId),
                price = trade.get("px")?.asString?.toDoubleOrNull() ?: 0.0,
                quantity = trade.get("sz")?.asString?.toDoubleOrNull() ?: 0.0,
                side = if (trade.get("side")?.asString == "sell") TradeSide.SELL else TradeSide.BUY,
                timestamp = trade.get("ts")?.asString?.toLongOrNull() ?: System.currentTimeMillis()
            )
            
            emitPublicTrade(publicTrade)
        }
    }
    
    fun buildWsSubscription(channel: String, symbol: String?): String {
        val args = mutableListOf<JsonObject>()
        
        when (channel) {
            "ticker" -> symbol?.let { 
                args.add(JsonObject().apply {
                    addProperty("channel", "tickers")
                    addProperty("instId", toExchangeSymbol(it))
                })
            }
            "orderbook" -> symbol?.let {
                args.add(JsonObject().apply {
                    addProperty("channel", "books5")  // 5-level order book
                    addProperty("instId", toExchangeSymbol(it))
                })
            }
            "trade" -> symbol?.let {
                args.add(JsonObject().apply {
                    addProperty("channel", "trades")
                    addProperty("instId", toExchangeSymbol(it))
                })
            }
        }
        
        val request = JsonObject().apply {
            addProperty("op", "subscribe")
            add("args", JsonArray().apply { args.forEach { add(it) } })
        }
        
        return request.toString()
    }
    
    // =========================================================================
    // REST ENDPOINTS
    // =========================================================================
    
    fun getTickerEndpoint(symbol: String): String =
        "/api/v5/market/ticker?instId=${toExchangeSymbol(symbol)}"
    
    fun getOrderBookEndpoint(symbol: String, depth: Int): String =
        "/api/v5/market/books?instId=${toExchangeSymbol(symbol)}&sz=$depth"
    
    fun getTradesEndpoint(symbol: String, limit: Int): String =
        "/api/v5/market/trades?instId=${toExchangeSymbol(symbol)}&limit=$limit"
    
    fun getOHLCVEndpoint(symbol: String, interval: String, limit: Int): String {
        val okxInterval = INTERVAL_MAP[interval] ?: "1m"
        return "/api/v5/market/candles?instId=${toExchangeSymbol(symbol)}&bar=$okxInterval&limit=$limit"
    }
    
    fun getTradingPairsEndpoint(): String = "/api/v5/public/instruments?instType=SPOT"
    
    fun getBalancesEndpoint(): String = "/api/v5/account/balance"
    
    fun getPlaceOrderEndpoint(): String = "/api/v5/trade/order"
    
    fun getCancelOrderEndpoint(orderId: String, symbol: String): String = "/api/v5/trade/cancel-order"
    
    fun getOrderEndpoint(orderId: String, symbol: String): String =
        "/api/v5/trade/order?instId=${toExchangeSymbol(symbol)}&ordId=$orderId"
    
    fun getOpenOrdersEndpoint(symbol: String?): String {
        val base = "/api/v5/trade/orders-pending"
        return if (symbol != null) "$base?instId=${toExchangeSymbol(symbol)}" else base
    }
    
    fun getOrderHistoryEndpoint(symbol: String?, limit: Int): String {
        val base = "/api/v5/trade/orders-history-archive?instType=SPOT&limit=$limit"
        return if (symbol != null) "$base&instId=${toExchangeSymbol(symbol)}" else base
    }
}
