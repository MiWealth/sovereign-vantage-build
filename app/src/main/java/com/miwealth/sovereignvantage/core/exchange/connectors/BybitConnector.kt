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

/**
 * BYBIT CONNECTOR - Complete Implementation
 * 
 * Full-featured Bybit exchange connector with:
 * - REST API V5 for unified trading
 * - WebSocket for real-time market data
 * - HMAC-SHA256 authentication
 * - Support for spot, linear, inverse perpetuals
 * 
 * Based on Bybit API V5:
 * - REST: https://api.bybit.com
 * - WebSocket: wss://stream.bybit.com/v5/public/spot
 * - Testnet: https://api-testnet.bybit.com
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

class BybitConnector(
    config: ExchangeConfig,
    secureHttpClient: HybridSecureHttpClient? = null,
    credentialVault: PQCCredentialVault? = null
) : BaseCEXConnector(config, secureHttpClient, credentialVault) {
    
    companion object {
        // Bybit uses simple concatenated symbol format
        private val SYMBOL_MAP = mapOf(
            "BTC/USDT" to "BTCUSDT",
            "ETH/USDT" to "ETHUSDT",
            "SOL/USDT" to "SOLUSDT",
            "XRP/USDT" to "XRPUSDT",
            "ADA/USDT" to "ADAUSDT",
            "DOGE/USDT" to "DOGEUSDT",
            "AVAX/USDT" to "AVAXUSDT",
            "DOT/USDT" to "DOTUSDT",
            "LINK/USDT" to "LINKUSDT",
            "MATIC/USDT" to "MATICUSDT",
            "ATOM/USDT" to "ATOMUSDT",
            "UNI/USDT" to "UNIUSDT",
            "BTC/USD" to "BTCUSD",
            "ETH/USD" to "ETHUSD"
        )
        
        private val REVERSE_SYMBOL_MAP = SYMBOL_MAP.entries.associate { (k, v) -> v to k }
        
        private val INTERVAL_MAP = mapOf(
            "1m" to "1",
            "3m" to "3",
            "5m" to "5",
            "15m" to "15",
            "30m" to "30",
            "1h" to "60",
            "2h" to "120",
            "4h" to "240",
            "1d" to "D",
            "1w" to "W"
        )
    }
    
    // =========================================================================
    // ABSTRACT OVERRIDES - Wire base class flow to Bybit V5 API
    // =========================================================================
    
    override val endpoints = ExchangeEndpoints(
        ticker = "/v5/market/tickers",
        orderBook = "/v5/market/orderbook",
        trades = "/v5/market/recent-trade",
        candles = "/v5/market/kline",
        pairs = "/v5/market/instruments-info",
        balances = "/v5/account/wallet-balance",
        placeOrder = "/v5/order/create",
        cancelOrder = "/v5/order/cancel",
        getOrder = "/v5/order/realtime",
        openOrders = "/v5/order/realtime",
        orderHistory = "/v5/order/history",
        wsUrl = "wss://stream.bybit.com/v5/public/spot"
    )
    
    override fun parsePlaceOrderResponse(response: JsonObject, request: OrderRequest): OrderExecutionResult {
        return try {
            val retCode = response.get("retCode")?.asInt ?: -1
            if (retCode != 0) {
                val retMsg = response.get("retMsg")?.asString ?: "Unknown error"
                return OrderExecutionResult.Rejected(retMsg, retCode.toString())
            }
            val result = response.getAsJsonObject("result") ?: return OrderExecutionResult.Error(Exception("No result"))
            val order = ExecutedOrder(
                orderId = result.get("orderId")?.asString ?: "",
                clientOrderId = result.get("orderLinkId")?.asString ?: "",
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
                exchange = "bybit"
            )
            OrderExecutionResult.Success(order)
        } catch (e: Exception) {
            OrderExecutionResult.Error(e)
        }
    }
    
    override fun buildOrderBody(request: OrderRequest): Map<String, String> {
        val params = mutableMapOf<String, String>()
        params["category"] = "spot"
        params["symbol"] = toExchangeSymbol(request.symbol)
        params["side"] = if (request.side == TradeSide.BUY || request.side == TradeSide.LONG) "Buy" else "Sell"
        params["orderType"] = when (request.type) {
            OrderType.MARKET -> "Market"
            OrderType.LIMIT -> "Limit"
            OrderType.STOP_LOSS -> "Market"
            OrderType.STOP_LIMIT -> "Limit"
            else -> "Market"
        }
        params["qty"] = request.quantity.toString()
        if (request.type == OrderType.LIMIT || request.type == OrderType.STOP_LIMIT) {
            request.price?.let { params["price"] = it.toString() }
        }
        if (request.type == OrderType.STOP_LOSS || request.type == OrderType.STOP_LIMIT) {
            request.stopPrice?.let { params["triggerPrice"] = it.toString() }
        }
        params["timeInForce"] = when (request.timeInForce) {
            TimeInForce.GTC -> "GTC"
            TimeInForce.IOC -> "IOC"
            TimeInForce.FOK -> "FOK"
            else -> "GTC"
        }
        if (request.clientOrderId.isNotEmpty()) params["orderLinkId"] = request.clientOrderId
        if (request.reduceOnly) params["reduceOnly"] = "true"
        return params
    }
    
    override fun buildWsSubscription(channels: List<String>, symbols: List<String>): String {
        val args = mutableListOf<String>()
        for (channel in channels) {
            for (symbol in symbols) {
                val exSym = toExchangeSymbol(symbol)
                when (channel) {
                    "ticker" -> args.add("tickers.$exSym")
                    "orderbook" -> args.add("orderbook.50.$exSym")
                    "trade" -> args.add("publicTrade.$exSym")
                    "kline" -> args.add("kline.1.$exSym")
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
        supportsLending = false,
        supportsStaking = false,
        supportsWebSocket = true,
        supportsOrderBook = true,
        supportsOHLCV = true,
        supportsTrades = true,
        maxOrdersPerSecond = 10,
        maxSubscriptionsPerConnection = 200,
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
    // AUTHENTICATION - HMAC-SHA256
    // =========================================================================
    
    override fun signRequest(
        method: String,
        endpoint: String,
        params: Map<String, String>,
        body: String?,
        timestamp: Long
    ): Map<String, String> {
        val ts = timestamp.toString()
        val recvWindow = "5000"
        
        // Build param string for signing
        val paramString = if (method == "GET") {
            params.entries.sortedBy { it.key }.joinToString("&") { "${it.key}=${it.value}" }
        } else {
            body ?: ""
        }
        
        // Signature payload: timestamp + api_key + recv_window + param_string
        val signPayload = "$ts${credentials.apiKey}$recvWindow$paramString"
        
        // HMAC-SHA256 signature
        val signature = hmacSha256(signPayload, credentials.apiSecret)
        
        return mapOf(
            "X-BAPI-API-KEY" to (credentials.apiKey),
            "X-BAPI-SIGN" to signature,
            "X-BAPI-TIMESTAMP" to ts,
            "X-BAPI-RECV-WINDOW" to recvWindow,
            "Content-Type" to "application/json"
        )
    }
    
    private fun hmacSha256(data: String, secret: String): String {
        val algorithm = "HmacSHA256"
        val mac = Mac.getInstance(algorithm)
        mac.init(SecretKeySpec(secret.toByteArray(), algorithm))
        val hash = mac.doFinal(data.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
    
    // =========================================================================
    // SYMBOL CONVERSION
    // =========================================================================
    
    override fun toExchangeSymbol(normalisedSymbol: String): String {
        return SYMBOL_MAP[normalisedSymbol] ?: normalisedSymbol.replace("/", "")
    }
    
    override fun normaliseSymbol(exchangeSymbol: String): String {
        return REVERSE_SYMBOL_MAP[exchangeSymbol] ?: run {
            // Try to parse: BTCUSDT -> BTC/USDT
            val match = Regex("([A-Z]+)(USDT|USD|BTC|ETH)").find(exchangeSymbol)
            if (match != null) {
                "${match.groupValues[1]}/${match.groupValues[2]}"
            } else {
                exchangeSymbol
            }
        }
    }
    
    // =========================================================================
    // PARSING IMPLEMENTATIONS
    // =========================================================================
    
    override fun parseTicker(json: JsonObject, symbol: String): PriceTick? {
        return try {
            val result = json.getAsJsonObject("result")
            val list = result?.getAsJsonArray("list")?.firstOrNull()?.asJsonObject
            
            if (list == null) return null
            
            PriceTick(
                symbol = normaliseSymbol(symbol),
                bid = list.get("bid1Price")?.asString?.toDoubleOrNull() ?: 0.0,
                ask = list.get("ask1Price")?.asString?.toDoubleOrNull() ?: 0.0,
                last = list.get("lastPrice")?.asString?.toDoubleOrNull() ?: 0.0,
                volume = list.get("volume24h")?.asString?.toDoubleOrNull() ?: 0.0,
                timestamp = System.currentTimeMillis(),
                exchange = "bybit"
            )
        } catch (e: Exception) {
            null
        }
    }
    
    override fun parseOrderBook(json: JsonObject, symbol: String): OrderBook? {
        return try {
            val result = json.getAsJsonObject("result")
            val bidsArray = result?.getAsJsonArray("b")
            val asksArray = result?.getAsJsonArray("a")
            
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
                timestamp = result?.get("ts")?.asLong ?: System.currentTimeMillis()
            )
        } catch (e: Exception) {
            null
        }
    }
    
    override fun parseTradingPairs(json: JsonObject): List<TradingPair> {
        return try {
            val result = json.getAsJsonObject("result")
            val list = result?.getAsJsonArray("list") ?: return emptyList()
            
            list.mapNotNull { item ->
                val obj = item.asJsonObject
                val symbol = obj.get("symbol")?.asString ?: return@mapNotNull null
                
                TradingPair(
                    symbol = normaliseSymbol(symbol),
                    baseAsset = obj.get("baseCoin")?.asString ?: "",
                    quoteAsset = obj.get("quoteCoin")?.asString ?: "",
                    exchangeSymbol = symbol,
                    exchange = "bybit",
                    status = if (obj.get("status")?.asString == "Trading") PairStatus.TRADING else PairStatus.HALTED,
                    minQuantity = obj.getAsJsonObject("lotSizeFilter")?.get("minOrderQty")?.asString?.toDoubleOrNull() ?: 0.0,
                    maxQuantity = obj.getAsJsonObject("lotSizeFilter")?.get("maxOrderQty")?.asString?.toDoubleOrNull() ?: Double.MAX_VALUE,
                    quantityStep = obj.getAsJsonObject("lotSizeFilter")?.get("qtyStep")?.asString?.toDoubleOrNull() ?: 0.00001,
                    minPrice = obj.getAsJsonObject("priceFilter")?.get("minPrice")?.asString?.toDoubleOrNull() ?: 0.0,
                    maxPrice = obj.getAsJsonObject("priceFilter")?.get("maxPrice")?.asString?.toDoubleOrNull() ?: Double.MAX_VALUE,
                    priceStep = obj.getAsJsonObject("priceFilter")?.get("tickSize")?.asString?.toDoubleOrNull() ?: 0.01
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    override fun parseBalances(json: JsonObject): List<Balance> {
        return try {
            val result = json.getAsJsonObject("result")
            val list = result?.getAsJsonObject("list")?.getAsJsonArray("coin") ?: return emptyList()
            
            list.mapNotNull { item ->
                val obj = item.asJsonObject
                val coin = obj.get("coin")?.asString ?: return@mapNotNull null
                
                Balance(
                    asset = coin,
                    free = obj.get("availableToWithdraw")?.asString?.toDoubleOrNull() ?: 0.0,
                    locked = obj.get("locked")?.asString?.toDoubleOrNull() ?: 0.0,
                    total = obj.get("walletBalance")?.asString?.toDoubleOrNull() ?: 0.0
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    override fun parseOrder(json: JsonObject): ExecutedOrder? {
        return try {
            val result = json.getAsJsonObject("result")
            val list = result?.getAsJsonArray("list")?.firstOrNull()?.asJsonObject ?: return null
            
            parseOrderFromObject(list)
        } catch (e: Exception) {
            null
        }
    }
    
    private fun parseOrderFromObject(obj: JsonObject): ExecutedOrder {
        val symbol = obj.get("symbol")?.asString ?: ""
        val side = obj.get("side")?.asString?.uppercase() ?: "BUY"
        val orderType = obj.get("orderType")?.asString?.uppercase() ?: "MARKET"
        val status = obj.get("orderStatus")?.asString?.uppercase() ?: "NEW"
        
        return ExecutedOrder(
            orderId = obj.get("orderId")?.asString ?: "",
            clientOrderId = obj.get("orderLinkId")?.asString ?: "",
            symbol = normaliseSymbol(symbol),
            side = if (side == "SELL") TradeSide.SELL else TradeSide.BUY,
            type = when (orderType) {
                "LIMIT" -> OrderType.LIMIT
                "STOP_LOSS" -> OrderType.STOP_LOSS
                else -> OrderType.MARKET
            },
            price = obj.get("price")?.asString?.toDoubleOrNull() ?: 0.0,
            executedPrice = obj.get("avgPrice")?.asString?.toDoubleOrNull() ?: 0.0,
            quantity = obj.get("qty")?.asString?.toDoubleOrNull() ?: 0.0,
            executedQuantity = obj.get("cumExecQty")?.asString?.toDoubleOrNull() ?: 0.0,
            fee = obj.get("cumExecFee")?.asString?.toDoubleOrNull() ?: 0.0,
            feeCurrency = "USDT",
            status = when (status) {
                "NEW", "CREATED" -> OrderStatus.OPEN
                "PARTIALLY_FILLED" -> OrderStatus.PARTIALLY_FILLED
                "FILLED" -> OrderStatus.FILLED
                "CANCELLED", "CANCELED" -> OrderStatus.CANCELLED
                "REJECTED" -> OrderStatus.REJECTED
                else -> OrderStatus.PENDING
            },
            timestamp = obj.get("createdTime")?.asString?.toLongOrNull() ?: System.currentTimeMillis(),
            exchange = "bybit"
        )
    }
    
    // parsePlaceOrderResponse logic merged into 2-param override above
    
    // buildOrderBody logic merged into Map<String,String> override above
    
    // =========================================================================
    // WEBSOCKET HANDLING
    // =========================================================================
    
    override fun handleWsMessage(message: String) {
        try {
            val json = gson.fromJson(message, JsonObject::class.java)
            
            // Check for subscription confirmation
            if (json.has("success")) {
                val success = json.get("success")?.asBoolean ?: false
                if (!success) {
                    val msg = json.get("ret_msg")?.asString ?: "Subscription failed"
                    emitError(Exception("WebSocket subscription error: $msg"))
                }
                return
            }
            
            // Check for data messages
            val topic = json.get("topic")?.asString ?: return
            val data = json.get("data") ?: return
            
            when {
                topic.startsWith("tickers.") -> handleTickerMessage(data.asJsonObject)
                topic.startsWith("orderbook.") -> handleOrderBookMessage(data.asJsonObject)
                topic.startsWith("publicTrade.") -> handleTradeMessage(data.asJsonArray)
            }
        } catch (e: Exception) {
            emitError(e)
        }
    }
    
    private fun handleTickerMessage(data: JsonObject) {
        val symbol = data.get("symbol")?.asString ?: return
        
        val tick = PriceTick(
            symbol = normaliseSymbol(symbol),
            bid = data.get("bid1Price")?.asString?.toDoubleOrNull() ?: 0.0,
            ask = data.get("ask1Price")?.asString?.toDoubleOrNull() ?: 0.0,
            last = data.get("lastPrice")?.asString?.toDoubleOrNull() ?: 0.0,
            volume = data.get("volume24h")?.asString?.toDoubleOrNull() ?: 0.0,
            timestamp = System.currentTimeMillis(),
            exchange = "bybit"
        )
        
        emitPriceTick(tick)
    }
    
    private fun handleOrderBookMessage(data: JsonObject) {
        val symbol = data.get("s")?.asString ?: return
        val bidsArray = data.getAsJsonArray("b")
        val asksArray = data.getAsJsonArray("a")
        
        val bids = bidsArray?.map { item ->
            val arr = item.asJsonArray
            OrderBookLevel(arr[0].asString.toDouble(), arr[1].asString.toDouble())
        } ?: emptyList()
        
        val asks = asksArray?.map { item ->
            val arr = item.asJsonArray
            OrderBookLevel(arr[0].asString.toDouble(), arr[1].asString.toDouble())
        } ?: emptyList()
        
        emitOrderBook(OrderBook(
            symbol = normaliseSymbol(symbol),
            bids = bids,
            asks = asks,
            timestamp = data.get("ts")?.asLong ?: System.currentTimeMillis()
        ))
    }
    
    private fun handleTradeMessage(data: JsonArray) {
        data.forEach { item ->
            val trade = item.asJsonObject
            val symbol = trade.get("s")?.asString ?: return@forEach
            
            val publicTrade = PublicTrade(
                tradeId = trade.get("i")?.asString ?: "",
                exchange = "bybit",
                symbol = normaliseSymbol(symbol),
                price = trade.get("p")?.asString?.toDoubleOrNull() ?: 0.0,
                quantity = trade.get("v")?.asString?.toDoubleOrNull() ?: 0.0,
                side = if (trade.get("S")?.asString == "Sell") TradeSide.SELL else TradeSide.BUY,
                timestamp = trade.get("T")?.asLong ?: System.currentTimeMillis()
            )
            
            emitPublicTrade(publicTrade)
        }
    }
    
    fun buildWsSubscription(channel: String, symbol: String?): String {
        val args = mutableListOf<String>()
        
        when (channel) {
            "ticker" -> symbol?.let { args.add("tickers.${toExchangeSymbol(it)}") }
            "orderbook" -> symbol?.let { args.add("orderbook.50.${toExchangeSymbol(it)}") }
            "trade" -> symbol?.let { args.add("publicTrade.${toExchangeSymbol(it)}") }
            "kline" -> symbol?.let { args.add("kline.1.${toExchangeSymbol(it)}") }
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
        "/v5/market/tickers?category=spot&symbol=${toExchangeSymbol(symbol)}"
    
    fun getOrderBookEndpoint(symbol: String, depth: Int): String =
        "/v5/market/orderbook?category=spot&symbol=${toExchangeSymbol(symbol)}&limit=$depth"
    
    fun getTradesEndpoint(symbol: String, limit: Int): String =
        "/v5/market/recent-trade?category=spot&symbol=${toExchangeSymbol(symbol)}&limit=$limit"
    
    fun getOHLCVEndpoint(symbol: String, interval: String, limit: Int): String {
        val bybitInterval = INTERVAL_MAP[interval] ?: "1"
        return "/v5/market/kline?category=spot&symbol=${toExchangeSymbol(symbol)}&interval=$bybitInterval&limit=$limit"
    }
    
    fun getTradingPairsEndpoint(): String = "/v5/market/instruments-info?category=spot"
    
    fun getBalancesEndpoint(): String = "/v5/account/wallet-balance?accountType=UNIFIED"
    
    fun getPlaceOrderEndpoint(): String = "/v5/order/create"
    
    fun getCancelOrderEndpoint(orderId: String, symbol: String): String = "/v5/order/cancel"
    
    fun getOrderEndpoint(orderId: String, symbol: String): String =
        "/v5/order/realtime?category=spot&orderId=$orderId"
    
    fun getOpenOrdersEndpoint(symbol: String?): String {
        val base = "/v5/order/realtime?category=spot"
        return if (symbol != null) "$base&symbol=${toExchangeSymbol(symbol)}" else base
    }
    
    fun getOrderHistoryEndpoint(symbol: String?, limit: Int): String {
        val base = "/v5/order/history?category=spot&limit=$limit"
        return if (symbol != null) "$base&symbol=${toExchangeSymbol(symbol)}" else base
    }
}
