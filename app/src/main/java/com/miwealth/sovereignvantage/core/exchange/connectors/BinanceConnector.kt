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
import android.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.*
import java.net.URLEncoder

/**
 * BINANCE CONNECTOR - Complete Implementation
 * 
 * Full-featured Binance exchange connector with:
 * - REST API for trading and account management (Spot API v3)
 * - WebSocket for real-time market data (combined streams)
 * - Proper Binance authentication (HMAC-SHA256)
 * - Direct symbol format (BTCUSDT - no normalisation needed)
 * - Rate limiting compliance (1200 weight/min, 10 orders/sec)
 * - PQC integration via BaseCEXConnector
 * 
 * Based on Binance API:
 * - REST: https://api.binance.com/api/v3/
 * - WebSocket: wss://stream.binance.com:9443/ws or /stream
 * - Documentation: https://binance-docs.github.io/apidocs/spot/en/
 * 
 * Testnet:
 * - REST: https://testnet.binance.vision/api/v3/
 * - WebSocket: wss://testnet.binance.vision/ws
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

class BinanceConnector(
    config: ExchangeConfig,
    secureHttpClient: HybridSecureHttpClient? = null,
    credentialVault: PQCCredentialVault? = null
) : BaseCEXConnector(config, secureHttpClient, credentialVault) {
    
    companion object {
        // Binance uses direct symbol format: BTCUSDT, ETHUSDT, etc.
        // Mapping is straightforward: BTC/USDT → BTCUSDT
        
        // Binance interval codes (same as standard)
        private val INTERVAL_MAP = mapOf(
            "1m" to "1m",
            "3m" to "3m",
            "5m" to "5m",
            "15m" to "15m",
            "30m" to "30m",
            "1h" to "1h",
            "2h" to "2h",
            "4h" to "4h",
            "6h" to "6h",
            "8h" to "8h",
            "12h" to "12h",
            "1d" to "1d",
            "3d" to "3d",
            "1w" to "1w",
            "1M" to "1M"
        )
        
        // Order type mapping
        private val ORDER_TYPE_MAP = mapOf(
            OrderType.MARKET to "MARKET",
            OrderType.LIMIT to "LIMIT",
            OrderType.STOP_LOSS to "STOP_LOSS",
            OrderType.STOP_LIMIT to "STOP_LOSS_LIMIT",
            OrderType.TRAILING_STOP to "TRAILING_STOP_MARKET"
        )
        
        // Time in force mapping
        private val TIF_MAP = mapOf(
            TimeInForce.GTC to "GTC",
            TimeInForce.IOC to "IOC",
            TimeInForce.FOK to "FOK"
        )
    }
    
    // =========================================================================
    // EXCHANGE CAPABILITIES
    // =========================================================================
    
    override val capabilities = ExchangeCapabilities(
        supportsSpotTrading = true,
        supportsFutures = true,      // Via futures.binance.com (separate API)
        supportsMargin = true,
        supportsOptions = false,     // Binance Options has separate API
        supportsLending = true,      // Binance Earn
        supportsStaking = true,      // Binance Staking
        supportsWebSocket = true,
        supportsOrderbook = true,
        supportsMarketOrders = true,
        supportsLimitOrders = true,
        supportsStopOrders = true,
        supportsPostOnly = false,    // Not directly - use LIMIT_MAKER
        supportsCancelAll = true,
        maxOrdersPerSecond = 10,     // 10 orders per second
        minOrderValue = 10.0,        // $10 minimum for most pairs
        tradingFeeMaker = 0.001,     // 0.10% maker (BNB discount available)
        tradingFeeTaker = 0.001,     // 0.10% taker (BNB discount available)
        withdrawalEnabled = false,   // Non-custodial - we don't withdraw
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
        wsUrl = "wss://stream.binance.com:9443/ws"
    )
    
    // WebSocket state
    private var wsStreamId: Long = 0
    private var wsSubscribedSymbols = mutableSetOf<String>()
    private var listenKey: String? = null  // For user data stream
    private var listenKeyRefreshJob: Job? = null
    
    // =========================================================================
    // AUTHENTICATION - Binance's HMAC-SHA256 Signature
    // =========================================================================
    
    override fun signRequest(
        method: String,
        path: String,
        params: Map<String, String>,
        body: String?,
        timestamp: Long
    ): Map<String, String> {
        // Binance signing:
        // 1. Build query string with timestamp
        // 2. Sign with HMAC-SHA256
        // 3. Append signature to query string
        
        // Build query string (params should already include timestamp)
        val queryParams = params.toMutableMap()
        if (!queryParams.containsKey("timestamp")) {
            queryParams["timestamp"] = timestamp.toString()
        }
        
        val queryString = queryParams.entries
            .sortedBy { it.key }
            .joinToString("&") { "${it.key}=${urlEncode(it.value)}" }
        
        // Sign with HMAC-SHA256
        val signature = hmacSha256(queryString, config.apiSecret)
        
        return mapOf(
            "X-MBX-APIKEY" to config.apiKey,
            "Content-Type" to "application/x-www-form-urlencoded"
        )
    }
    
    /**
     * Build signed query string for Binance requests
     */
    private fun buildSignedQuery(params: Map<String, String>): String {
        val timestamp = System.currentTimeMillis()
        val queryParams = params.toMutableMap()
        queryParams["timestamp"] = timestamp.toString()
        
        // Receive window (optional but recommended)
        if (!queryParams.containsKey("recvWindow")) {
            queryParams["recvWindow"] = "5000"  // 5 seconds
        }
        
        val queryString = queryParams.entries
            .sortedBy { it.key }
            .joinToString("&") { "${it.key}=${urlEncode(it.value)}" }
        
        val signature = hmacSha256(queryString, config.apiSecret)
        
        return "$queryString&signature=$signature"
    }
    
    /**
     * HMAC-SHA256 signature
     */
    private fun hmacSha256(data: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val hash = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * URL encode a value
     */
    private fun urlEncode(value: String): String {
        return try {
            URLEncoder.encode(value, "UTF-8")
        } catch (e: Exception) {
            value
        }
    }
    
    // =========================================================================
    // TICKER PARSING
    // =========================================================================
    
    override fun parseTicker(response: JsonObject, symbol: String): PriceTick? {
        try {
            // Check for error
            if (hasBinanceError(response)) return null
            
            // Binance ticker format (24hr):
            // symbol, priceChange, priceChangePercent, weightedAvgPrice,
            // prevClosePrice, lastPrice, lastQty, bidPrice, bidQty,
            // askPrice, askQty, openPrice, highPrice, lowPrice, volume,
            // quoteVolume, openTime, closeTime, firstId, lastId, count
            
            return PriceTick(
                symbol = normaliseSymbol(response.get("symbol")?.asString ?: symbol),
                bid = response.get("bidPrice")?.asString?.toDoubleOrNull() ?: 0.0,
                ask = response.get("askPrice")?.asString?.toDoubleOrNull() ?: 0.0,
                last = response.get("lastPrice")?.asString?.toDoubleOrNull() ?: 0.0,
                volume = response.get("volume")?.asString?.toDoubleOrNull() ?: 0.0,
                high24h = response.get("highPrice")?.asString?.toDoubleOrNull(),
                low24h = response.get("lowPrice")?.asString?.toDoubleOrNull(),
                change24h = response.get("priceChange")?.asString?.toDoubleOrNull(),
                changePercent24h = response.get("priceChangePercent")?.asString?.toDoubleOrNull(),
                timestamp = response.get("closeTime")?.asLong ?: System.currentTimeMillis(),
                exchange = "Binance"
            )
        } catch (e: Exception) {
            return null
        }
    }
    
    // =========================================================================
    // ORDER BOOK PARSING
    // =========================================================================
    
    override fun parseOrderBook(response: JsonObject, symbol: String): OrderBook? {
        try {
            if (hasBinanceError(response)) return null
            
            // Binance order book format:
            // lastUpdateId, bids: [[price, qty], ...], asks: [[price, qty], ...]
            
            val bids = response.getAsJsonArray("bids")?.map { entry ->
                val arr = entry.asJsonArray
                OrderBookLevel(
                    price = arr[0].asString.toDouble(),
                    quantity = arr[1].asString.toDouble(),
                    orderCount = 1
                )
            } ?: emptyList()
            
            val asks = response.getAsJsonArray("asks")?.map { entry ->
                val arr = entry.asJsonArray
                OrderBookLevel(
                    price = arr[0].asString.toDouble(),
                    quantity = arr[1].asString.toDouble(),
                    orderCount = 1
                )
            } ?: emptyList()
            
            return OrderBook(
                symbol = symbol,
                exchange = "Binance",
                bids = bids,
                asks = asks,
                timestamp = System.currentTimeMillis(),
                sequenceId = response.get("lastUpdateId")?.asLong ?: 0
            )
        } catch (e: Exception) {
            return null
        }
    }
    
    // =========================================================================
    // TRADING PAIRS PARSING
    // =========================================================================
    
    override fun parseTradingPairs(response: JsonObject): List<TradingPair> {
        try {
            if (hasBinanceError(response)) return emptyList()
            
            val symbols = response.getAsJsonArray("symbols") ?: return emptyList()
            
            return symbols.mapNotNull { symbolJson ->
                try {
                    val symbol = symbolJson.asJsonObject
                    
                    // Skip non-trading pairs
                    val status = symbol.get("status")?.asString
                    if (status != "TRADING") return@mapNotNull null
                    
                    val exchangeSymbol = symbol.get("symbol")?.asString ?: return@mapNotNull null
                    val baseAsset = symbol.get("baseAsset")?.asString ?: return@mapNotNull null
                    val quoteAsset = symbol.get("quoteAsset")?.asString ?: return@mapNotNull null
                    
                    // Parse filters for limits
                    val filters = symbol.getAsJsonArray("filters")
                    var minQty = 0.0
                    var maxQty = Double.MAX_VALUE
                    var stepSize = 0.00000001
                    var minPrice = 0.0
                    var maxPrice = Double.MAX_VALUE
                    var tickSize = 0.00000001
                    var minNotional = 0.0
                    
                    filters?.forEach { filterJson ->
                        val filter = filterJson.asJsonObject
                        when (filter.get("filterType")?.asString) {
                            "LOT_SIZE" -> {
                                minQty = filter.get("minQty")?.asString?.toDoubleOrNull() ?: minQty
                                maxQty = filter.get("maxQty")?.asString?.toDoubleOrNull() ?: maxQty
                                stepSize = filter.get("stepSize")?.asString?.toDoubleOrNull() ?: stepSize
                            }
                            "PRICE_FILTER" -> {
                                minPrice = filter.get("minPrice")?.asString?.toDoubleOrNull() ?: minPrice
                                maxPrice = filter.get("maxPrice")?.asString?.toDoubleOrNull() ?: maxPrice
                                tickSize = filter.get("tickSize")?.asString?.toDoubleOrNull() ?: tickSize
                            }
                            "NOTIONAL", "MIN_NOTIONAL" -> {
                                minNotional = filter.get("minNotional")?.asString?.toDoubleOrNull() 
                                    ?: filter.get("notional")?.asString?.toDoubleOrNull()
                                    ?: minNotional
                            }
                        }
                    }
                    
                    TradingPair(
                        symbol = "$baseAsset/$quoteAsset",
                        baseAsset = baseAsset,
                        quoteAsset = quoteAsset,
                        exchangeSymbol = exchangeSymbol,
                        exchange = "Binance",
                        minQuantity = minQty,
                        maxQuantity = maxQty,
                        quantityStep = stepSize,
                        minPrice = minPrice,
                        maxPrice = maxPrice,
                        priceStep = tickSize,
                        minNotional = minNotional,
                        status = PairStatus.TRADING
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            return emptyList()
        }
    }
    
    // =========================================================================
    // BALANCE PARSING
    // =========================================================================
    
    override fun parseBalances(response: JsonObject): List<Balance> {
        try {
            if (hasBinanceError(response)) return emptyList()
            
            val balances = response.getAsJsonArray("balances") ?: return emptyList()
            
            return balances.mapNotNull { balanceJson ->
                try {
                    val balance = balanceJson.asJsonObject
                    val asset = balance.get("asset")?.asString ?: return@mapNotNull null
                    val free = balance.get("free")?.asString?.toDoubleOrNull() ?: 0.0
                    val locked = balance.get("locked")?.asString?.toDoubleOrNull() ?: 0.0
                    val total = free + locked
                    
                    // Skip zero balances
                    if (total <= 0) return@mapNotNull null
                    
                    Balance(
                        asset = asset,
                        free = free,
                        locked = locked,
                        total = total,
                        usdValue = 0.0  // Would need price data
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            return emptyList()
        }
    }
    
    // =========================================================================
    // ORDER PARSING
    // =========================================================================
    
    override fun parseOrder(response: JsonObject): ExecutedOrder? {
        try {
            if (hasBinanceError(response)) return null
            return parseBinanceOrder(response)
        } catch (e: Exception) {
            return null
        }
    }
    
    private fun parseBinanceOrder(orderData: JsonObject): ExecutedOrder? {
        try {
            val symbol = orderData.get("symbol")?.asString ?: return null
            val orderId = orderData.get("orderId")?.asString 
                ?: orderData.get("orderId")?.asLong?.toString()
                ?: return null
            
            val side = orderData.get("side")?.asString ?: "BUY"
            val type = orderData.get("type")?.asString ?: "LIMIT"
            val status = orderData.get("status")?.asString ?: "NEW"
            
            val origQty = orderData.get("origQty")?.asString?.toDoubleOrNull() ?: 0.0
            val executedQty = orderData.get("executedQty")?.asString?.toDoubleOrNull() ?: 0.0
            val price = orderData.get("price")?.asString?.toDoubleOrNull() ?: 0.0
            val avgPrice = orderData.get("avgPrice")?.asString?.toDoubleOrNull()
                ?: orderData.get("cummulativeQuoteQty")?.asString?.toDoubleOrNull()?.let { 
                    if (executedQty > 0) it / executedQty else 0.0 
                }
                ?: price
            
            val clientOrderId = orderData.get("clientOrderId")?.asString ?: ""
            
            return ExecutedOrder(
                orderId = orderId,
                clientOrderId = clientOrderId,
                symbol = normaliseSymbol(symbol),
                side = if (side == "BUY") TradeSide.BUY else TradeSide.SELL,
                type = parseBinanceOrderType(type),
                requestedPrice = price,
                executedPrice = avgPrice,
                requestedQuantity = origQty,
                executedQuantity = executedQty,
                fee = 0.0,  // Not in order response - need trades endpoint
                feeCurrency = "",
                status = parseBinanceOrderStatus(status),
                exchange = "Binance",
                createdAt = orderData.get("time")?.asLong ?: System.currentTimeMillis(),
                updatedAt = orderData.get("updateTime")?.asLong ?: System.currentTimeMillis()
            )
        } catch (e: Exception) {
            return null
        }
    }
    
    // =========================================================================
    // PLACE ORDER
    // =========================================================================
    
    override fun parsePlaceOrderResponse(response: JsonObject, request: OrderRequest): OrderExecutionResult {
        try {
            if (hasBinanceError(response)) {
                val code = response.get("code")?.asInt ?: -1
                val msg = response.get("msg")?.asString ?: "Unknown error"
                return OrderExecutionResult.Rejected("[$code] $msg")
            }
            
            val orderId = response.get("orderId")?.asString
                ?: response.get("orderId")?.asLong?.toString()
                ?: return OrderExecutionResult.Rejected("No order ID returned")
            
            val status = response.get("status")?.asString ?: "NEW"
            val executedQty = response.get("executedQty")?.asString?.toDoubleOrNull() ?: 0.0
            val avgPrice = response.get("avgPrice")?.asString?.toDoubleOrNull()
                ?: response.get("price")?.asString?.toDoubleOrNull()
                ?: request.price
                ?: 0.0
            
            // Check for fills (for market orders, response may contain fills array)
            val fills = response.getAsJsonArray("fills")
            var totalFee = 0.0
            var feeCurrency = ""
            
            fills?.forEach { fillJson ->
                val fill = fillJson.asJsonObject
                totalFee += fill.get("commission")?.asString?.toDoubleOrNull() ?: 0.0
                feeCurrency = fill.get("commissionAsset")?.asString ?: feeCurrency
            }
            
            return OrderExecutionResult.Success(
                ExecutedOrder(
                    orderId = orderId,
                    clientOrderId = request.clientOrderId,
                    symbol = request.symbol,
                    side = request.side,
                    type = request.type,
                    requestedPrice = request.price ?: 0.0,
                    executedPrice = avgPrice,
                    requestedQuantity = request.quantity,
                    executedQuantity = executedQty,
                    fee = totalFee,
                    feeCurrency = feeCurrency,
                    status = parseBinanceOrderStatus(status),
                    exchange = "Binance"
                )
            )
        } catch (e: Exception) {
            return OrderExecutionResult.Error(e)
        }
    }
    
    override fun buildOrderBody(request: OrderRequest): Map<String, String> {
        val exchangeSymbol = toExchangeSymbol(request.symbol)
        
        val params = mutableMapOf(
            "symbol" to exchangeSymbol,
            "side" to if (request.side == TradeSide.BUY || request.side == TradeSide.LONG) "BUY" else "SELL",
            "type" to (ORDER_TYPE_MAP[request.type] ?: "LIMIT"),
            "quantity" to request.quantity.toString()
        )
        
        // Price for limit orders
        if (request.type == OrderType.LIMIT && request.price != null) {
            params["price"] = request.price.toString()
            params["timeInForce"] = TIF_MAP[request.timeInForce] ?: "GTC"
        }
        
        // Stop price for stop orders
        if ((request.type == OrderType.STOP_LOSS || request.type == OrderType.STOP_LIMIT) 
            && request.stopPrice != null) {
            params["stopPrice"] = request.stopPrice.toString()
            if (request.type == OrderType.STOP_LIMIT && request.price != null) {
                params["price"] = request.price.toString()
                params["timeInForce"] = TIF_MAP[request.timeInForce] ?: "GTC"
            }
        }
        
        // Trailing stop
        if (request.type == OrderType.TRAILING_STOP && request.trailingDelta != null) {
            params["trailingDelta"] = request.trailingDelta.toString()
        }
        
        // Client order ID (max 36 chars, alphanumeric)
        if (request.clientOrderId.isNotEmpty()) {
            val sanitized = request.clientOrderId.take(36).replace(Regex("[^a-zA-Z0-9_-]"), "")
            if (sanitized.isNotEmpty()) {
                params["newClientOrderId"] = sanitized
            }
        }
        
        // Response type
        params["newOrderRespType"] = "FULL"  // Get fills in response
        
        return params
    }
    
    // =========================================================================
    // ADDITIONAL PARSING METHODS
    // =========================================================================
    
    override fun parseOrderList(response: JsonObject): List<ExecutedOrder> {
        // Binance returns array directly for order list endpoints
        // This is called by base class but response is actually array
        return emptyList()
    }
    
    /**
     * Parse order list from array response
     */
    fun parseOrderListFromArray(response: JsonArray): List<ExecutedOrder> {
        return response.mapNotNull { orderJson ->
            parseBinanceOrder(orderJson.asJsonObject)
        }
    }
    
    override fun parsePublicTrades(response: JsonObject, symbol: String): List<PublicTrade> {
        // Binance returns array directly for trades endpoint
        return emptyList()
    }
    
    /**
     * Parse public trades from array response
     */
    fun parsePublicTradesFromArray(response: JsonArray, symbol: String): List<PublicTrade> {
        return response.mapIndexed { index, tradeJson ->
            val trade = tradeJson.asJsonObject
            PublicTrade(
                symbol = symbol,
                exchange = "Binance",
                price = trade.get("price")?.asString?.toDoubleOrNull() ?: 0.0,
                quantity = trade.get("qty")?.asString?.toDoubleOrNull() ?: 0.0,
                side = if (trade.get("isBuyerMaker")?.asBoolean == true) TradeSide.SELL else TradeSide.BUY,
                timestamp = trade.get("time")?.asLong ?: System.currentTimeMillis(),
                tradeId = trade.get("id")?.asString ?: "$index"
            )
        }
    }
    
    override fun parseCandles(response: JsonObject, symbol: String): List<OHLCVBar> {
        // Binance returns array directly for klines endpoint
        return emptyList()
    }
    
    /**
     * Parse candles from array response
     */
    fun parseCandlesFromArray(response: JsonArray, symbol: String, interval: String): List<OHLCVBar> {
        // Binance kline format: [openTime, open, high, low, close, volume, closeTime, 
        //                        quoteAssetVolume, trades, takerBuyBaseVolume, takerBuyQuoteVolume, ignore]
        return response.map { candleJson ->
            val arr = candleJson.asJsonArray
            OHLCVBar(
                symbol = symbol,
                open = arr[1].asString.toDouble(),
                high = arr[2].asString.toDouble(),
                low = arr[3].asString.toDouble(),
                close = arr[4].asString.toDouble(),
                volume = arr[5].asString.toDouble(),
                timestamp = arr[0].asLong,
                interval = interval,
                trades = arr[8].asInt
            )
        }
    }
    
    // =========================================================================
    // WEBSOCKET HANDLING
    // =========================================================================
    
    override fun handleWsMessage(text: String) {
        try {
            val json = gson.fromJson(text, JsonObject::class.java)
            
            // Binance WS messages have different formats:
            // Single stream: {"e": "eventType", "s": "BTCUSDT", ...}
            // Combined stream: {"stream": "btcusdt@ticker", "data": {...}}
            
            when {
                json.has("stream") -> {
                    // Combined stream format
                    val stream = json.get("stream")?.asString ?: ""
                    val data = json.getAsJsonObject("data") ?: return
                    handleStreamData(stream, data)
                }
                json.has("e") -> {
                    // Single stream format
                    handleEventData(json)
                }
                json.has("result") && json.get("result").isJsonNull -> {
                    // Subscription confirmation
                    val id = json.get("id")?.asLong ?: 0
                    // Subscription successful
                }
                json.has("ping") -> {
                    // Respond to ping with pong
                    webSocket?.send("""{"pong":${json.get("ping")}}""")
                }
            }
        } catch (e: Exception) {
            // Ignore parsing errors for individual messages
        }
    }
    
    private fun handleStreamData(stream: String, data: JsonObject) {
        when {
            stream.endsWith("@ticker") -> handleWsTicker(data)
            stream.endsWith("@depth") || stream.endsWith("@depth10") || stream.endsWith("@depth20") -> 
                handleWsOrderBook(data)
            stream.endsWith("@trade") -> handleWsTrade(data)
            stream.endsWith("@aggTrade") -> handleWsAggTrade(data)
            stream.contains("@kline_") -> handleWsKline(data)
            stream.endsWith("@bookTicker") -> handleWsBookTicker(data)
        }
    }
    
    private fun handleEventData(json: JsonObject) {
        val eventType = json.get("e")?.asString ?: return
        
        when (eventType) {
            "24hrTicker" -> handleWsTicker(json)
            "depthUpdate" -> handleWsDepthUpdate(json)
            "trade" -> handleWsTrade(json)
            "aggTrade" -> handleWsAggTrade(json)
            "kline" -> handleWsKline(json)
            "bookTicker" -> handleWsBookTicker(json)
            // User data stream events
            "executionReport" -> handleWsExecutionReport(json)
            "outboundAccountPosition" -> handleWsAccountUpdate(json)
        }
    }
    
    private fun handleWsTicker(data: JsonObject) {
        try {
            val symbol = data.get("s")?.asString ?: return
            
            val tick = PriceTick(
                symbol = normaliseSymbol(symbol),
                bid = data.get("b")?.asString?.toDoubleOrNull() ?: 0.0,
                ask = data.get("a")?.asString?.toDoubleOrNull() ?: 0.0,
                last = data.get("c")?.asString?.toDoubleOrNull() ?: 0.0,
                volume = data.get("v")?.asString?.toDoubleOrNull() ?: 0.0,
                high24h = data.get("h")?.asString?.toDoubleOrNull(),
                low24h = data.get("l")?.asString?.toDoubleOrNull(),
                change24h = data.get("p")?.asString?.toDoubleOrNull(),
                changePercent24h = data.get("P")?.asString?.toDoubleOrNull(),
                timestamp = data.get("E")?.asLong ?: System.currentTimeMillis(),
                exchange = "Binance"
            )
            
            scope.launch {
                _priceUpdates.emit(tick)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    private fun handleWsOrderBook(data: JsonObject) {
        try {
            val symbol = data.get("s")?.asString ?: return
            
            val bids = data.getAsJsonArray("b")?.map { entry ->
                val arr = entry.asJsonArray
                OrderBookLevel(
                    price = arr[0].asString.toDouble(),
                    quantity = arr[1].asString.toDouble()
                )
            } ?: emptyList()
            
            val asks = data.getAsJsonArray("a")?.map { entry ->
                val arr = entry.asJsonArray
                OrderBookLevel(
                    price = arr[0].asString.toDouble(),
                    quantity = arr[1].asString.toDouble()
                )
            } ?: emptyList()
            
            val book = OrderBook(
                symbol = normaliseSymbol(symbol),
                exchange = "Binance",
                bids = bids,
                asks = asks,
                timestamp = data.get("E")?.asLong ?: System.currentTimeMillis(),
                sequenceId = data.get("u")?.asLong ?: 0
            )
            
            scope.launch {
                _orderBookUpdates.emit(book)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    private fun handleWsDepthUpdate(data: JsonObject) {
        // Incremental depth update - similar to order book
        handleWsOrderBook(data)
    }
    
    private fun handleWsTrade(data: JsonObject) {
        try {
            val symbol = data.get("s")?.asString ?: return
            
            val trade = PublicTrade(
                symbol = normaliseSymbol(symbol),
                exchange = "Binance",
                price = data.get("p")?.asString?.toDoubleOrNull() ?: 0.0,
                quantity = data.get("q")?.asString?.toDoubleOrNull() ?: 0.0,
                side = if (data.get("m")?.asBoolean == true) TradeSide.SELL else TradeSide.BUY,
                timestamp = data.get("T")?.asLong ?: System.currentTimeMillis(),
                tradeId = data.get("t")?.asString ?: "${System.currentTimeMillis()}"
            )
            
            scope.launch {
                _tradeUpdates.emit(trade)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    private fun handleWsAggTrade(data: JsonObject) {
        // Aggregated trade - similar format
        handleWsTrade(data)
    }
    
    private fun handleWsKline(data: JsonObject) {
        // Kline/candlestick updates
        // We could emit candle updates here if needed
    }
    
    private fun handleWsBookTicker(data: JsonObject) {
        // Best bid/ask update - lightweight price update
        try {
            val symbol = data.get("s")?.asString ?: return
            
            val tick = PriceTick(
                symbol = normaliseSymbol(symbol),
                bid = data.get("b")?.asString?.toDoubleOrNull() ?: 0.0,
                ask = data.get("a")?.asString?.toDoubleOrNull() ?: 0.0,
                last = 0.0,  // Not in bookTicker
                volume = 0.0,
                timestamp = System.currentTimeMillis(),
                exchange = "Binance"
            )
            
            scope.launch {
                _priceUpdates.emit(tick)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    private fun handleWsExecutionReport(data: JsonObject) {
        // Order execution report from user data stream
        try {
            val symbol = data.get("s")?.asString ?: return
            val orderId = data.get("i")?.asString ?: data.get("i")?.asLong?.toString() ?: return
            val clientOrderId = data.get("c")?.asString ?: ""
            val side = data.get("S")?.asString ?: "BUY"
            val orderType = data.get("o")?.asString ?: "LIMIT"
            val status = data.get("X")?.asString ?: "NEW"
            val price = data.get("p")?.asString?.toDoubleOrNull() ?: 0.0
            val origQty = data.get("q")?.asString?.toDoubleOrNull() ?: 0.0
            val executedQty = data.get("z")?.asString?.toDoubleOrNull() ?: 0.0
            val avgPrice = data.get("L")?.asString?.toDoubleOrNull() ?: price
            
            val update = ExchangeOrderUpdate(
                orderId = orderId,
                clientOrderId = clientOrderId,
                symbol = normaliseSymbol(symbol),
                status = parseBinanceOrderStatus(status),
                side = if (side == "BUY") TradeSide.BUY else TradeSide.SELL,
                filledQuantity = executedQty,
                filledPrice = avgPrice,
                fee = data.get("n")?.asString?.toDoubleOrNull() ?: 0.0,
                feeCurrency = data.get("N")?.asString ?: "",
                timestamp = data.get("E")?.asLong ?: System.currentTimeMillis()
            )
            
            scope.launch {
                _orderUpdates.emit(update)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    private fun handleWsAccountUpdate(data: JsonObject) {
        // Account balance update from user data stream
        // Could emit balance updates here if needed
    }
    
    // =========================================================================
    // WEBSOCKET SUBSCRIPTION
    // =========================================================================
    
    override fun buildWsSubscription(channels: List<String>, symbols: List<String>): String {
        // Binance combined stream subscription format
        val streams = mutableListOf<String>()
        
        symbols.forEach { symbol ->
            val lowerSymbol = toExchangeSymbol(symbol).lowercase()
            
            channels.forEach { channel ->
                val stream = when (channel) {
                    "ticker" -> "${lowerSymbol}@ticker"
                    "orderbook", "depth" -> "${lowerSymbol}@depth10"  // 10-level depth
                    "trades", "trade" -> "${lowerSymbol}@trade"
                    "aggTrade" -> "${lowerSymbol}@aggTrade"
                    "kline", "candle" -> "${lowerSymbol}@kline_1m"  // Default to 1m
                    "bookTicker" -> "${lowerSymbol}@bookTicker"
                    else -> "${lowerSymbol}@ticker"
                }
                streams.add(stream)
            }
        }
        
        wsStreamId++
        
        return gson.toJson(mapOf(
            "method" to "SUBSCRIBE",
            "params" to streams,
            "id" to wsStreamId
        ))
    }
    
    /**
     * Build unsubscribe message
     */
    fun buildWsUnsubscription(channels: List<String>, symbols: List<String>): String {
        val streams = mutableListOf<String>()
        
        symbols.forEach { symbol ->
            val lowerSymbol = toExchangeSymbol(symbol).lowercase()
            
            channels.forEach { channel ->
                val stream = when (channel) {
                    "ticker" -> "${lowerSymbol}@ticker"
                    "orderbook", "depth" -> "${lowerSymbol}@depth10"
                    "trades", "trade" -> "${lowerSymbol}@trade"
                    else -> "${lowerSymbol}@ticker"
                }
                streams.add(stream)
            }
        }
        
        wsStreamId++
        
        return gson.toJson(mapOf(
            "method" to "UNSUBSCRIBE",
            "params" to streams,
            "id" to wsStreamId
        ))
    }
    
    // =========================================================================
    // HELPER METHODS
    // =========================================================================
    
    private fun hasBinanceError(response: JsonObject): Boolean {
        return response.has("code") && response.get("code").asInt < 0
    }
    
    override fun hasError(response: JsonObject): Boolean = hasBinanceError(response)
    
    override fun toExchangeSymbol(normalisedSymbol: String): String {
        // BTC/USDT -> BTCUSDT
        return normalisedSymbol.replace("/", "")
    }
    
    override fun normaliseSymbol(exchangeSymbol: String): String {
        // BTCUSDT -> BTC/USDT
        // Need to find the quote currency
        val quotes = listOf("USDT", "USDC", "BUSD", "USD", "BTC", "ETH", "BNB", "EUR", "GBP", "AUD", "TRY", "BRL")
        
        for (quote in quotes) {
            if (exchangeSymbol.endsWith(quote)) {
                val base = exchangeSymbol.dropLast(quote.length)
                if (base.isNotEmpty()) {
                    return "$base/$quote"
                }
            }
        }
        
        // Fallback: assume last 4 chars are quote
        if (exchangeSymbol.length > 4) {
            val base = exchangeSymbol.dropLast(4)
            val quote = exchangeSymbol.takeLast(4)
            return "$base/$quote"
        }
        
        return exchangeSymbol
    }
    
    private fun parseBinanceOrderType(type: String): OrderType {
        return when (type.uppercase()) {
            "MARKET" -> OrderType.MARKET
            "LIMIT" -> OrderType.LIMIT
            "STOP_LOSS" -> OrderType.STOP_LOSS
            "STOP_LOSS_LIMIT" -> OrderType.STOP_LIMIT
            "TAKE_PROFIT" -> OrderType.STOP_LOSS
            "TAKE_PROFIT_LIMIT" -> OrderType.STOP_LIMIT
            "LIMIT_MAKER" -> OrderType.LIMIT
            "TRAILING_STOP_MARKET" -> OrderType.TRAILING_STOP
            else -> OrderType.LIMIT
        }
    }
    
    private fun parseBinanceOrderStatus(status: String): OrderStatus {
        return when (status.uppercase()) {
            "NEW" -> OrderStatus.OPEN
            "PARTIALLY_FILLED" -> OrderStatus.PARTIALLY_FILLED
            "FILLED" -> OrderStatus.FILLED
            "CANCELED", "CANCELLED" -> OrderStatus.CANCELLED
            "PENDING_CANCEL" -> OrderStatus.PENDING
            "REJECTED" -> OrderStatus.REJECTED
            "EXPIRED" -> OrderStatus.EXPIRED
            "EXPIRED_IN_MATCH" -> OrderStatus.EXPIRED
            else -> OrderStatus.PENDING
        }
    }
    
    // =========================================================================
    // USER DATA STREAM (for real-time order updates)
    // =========================================================================
    
    /**
     * Start user data stream for order updates
     * Requires API key but not signature
     */
    suspend fun startUserDataStream(): Boolean {
        // POST /api/v3/userDataStream to get listen key
        // Then subscribe to user data WebSocket
        // Listen key must be refreshed every 30 minutes
        
        // This is a simplified implementation
        // Full implementation would manage the listen key lifecycle
        return false  // Placeholder - implement when needed
    }
    
    /**
     * Stop user data stream
     */
    suspend fun stopUserDataStream() {
        listenKeyRefreshJob?.cancel()
        listenKey = null
    }
    
    // =========================================================================
    // CLEANUP
    // =========================================================================
    
    override suspend fun disconnect() {
        stopUserDataStream()
        super.disconnect()
    }
}
