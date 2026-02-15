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
 * BITGET CONNECTOR - Complete Implementation
 * 
 * Full-featured Bitget exchange connector with:
 * - REST API for spot trading (V2 API)
 * - REST API for futures/USDT-M/COIN-M (Mix V2 API)
 * - Copy trading support
 * - WebSocket for real-time market data
 * - HMAC-SHA256 + Base64 authentication
 * - PQC integration via BaseCEXConnector
 * 
 * API Documentation:
 * - Spot: https://www.bitget.com/api-doc/spot/intro
 * - Mix (Futures): https://www.bitget.com/api-doc/mix/intro
 * - Copy Trading: https://www.bitget.com/api-doc/copytrading/intro
 * 
 * Endpoints:
 * - REST: https://api.bitget.com
 * - WebSocket: wss://ws.bitget.com/v2/ws/public or /private
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

class BitgetConnector(
    config: ExchangeConfig,
    secureHttpClient: HybridSecureHttpClient? = null,
    credentialVault: PQCCredentialVault? = null
) : BaseCEXConnector(config, secureHttpClient, credentialVault) {
    
    companion object {
        private const val API_BASE = "https://api.bitget.com"
        private const val WS_PUBLIC = "wss://ws.bitget.com/v2/ws/public"
        private const val WS_PRIVATE = "wss://ws.bitget.com/v2/ws/private"
        
        // Order type mapping
        private val ORDER_TYPE_MAP = mapOf(
            OrderType.MARKET to "market",
            OrderType.LIMIT to "limit",
            OrderType.STOP_LOSS to "market",  // With trigger price
            OrderType.STOP_LIMIT to "limit",   // With trigger price
            OrderType.TRAILING_STOP to "trailing"
        )
        
        // Time in force mapping
        private val TIF_MAP = mapOf(
            TimeInForce.GTC to "gtc",
            TimeInForce.IOC to "ioc",
            TimeInForce.FOK to "fok",
            TimeInForce.GTD to "post_only"
        )
        
        // Futures product types
        private const val USDT_FUTURES = "USDT-FUTURES"
        private const val COIN_FUTURES = "COIN-FUTURES"
        private const val USDC_FUTURES = "USDC-FUTURES"
    }
    
    // =========================================================================
    // EXCHANGE CAPABILITIES
    // =========================================================================
    
    override val capabilities = ExchangeCapabilities(
        supportsSpotTrading = true,
        supportsFutures = true,       // USDT-M, COIN-M, USDC-M perpetuals
        supportsMargin = true,        // Cross and isolated margin
        supportsOptions = false,
        supportsLending = false,
        supportsStaking = false,
        supportsWebSocket = true,
        supportsOrderbook = true,
        supportsMarketOrders = true,
        supportsLimitOrders = true,
        supportsStopOrders = true,
        supportsPostOnly = true,
        supportsCancelAll = true,
        maxOrdersPerSecond = 10,
        minOrderValue = 5.0,
        tradingFeeMaker = 0.001,      // 0.10% maker
        tradingFeeTaker = 0.001,      // 0.10% taker
        withdrawalEnabled = false,
        networks = listOf(
            BlockchainNetwork.BITCOIN,
            BlockchainNetwork.ETHEREUM,
            BlockchainNetwork.BINANCE_SMART_CHAIN,
            BlockchainNetwork.SOLANA,
            BlockchainNetwork.POLYGON,
            BlockchainNetwork.ARBITRUM,
            BlockchainNetwork.OPTIMISM,
            BlockchainNetwork.AVALANCHE,
            BlockchainNetwork.TRON,
            BlockchainNetwork.BASE
        )
    )
    
    // =========================================================================
    // API ENDPOINTS
    // =========================================================================
    
    override val endpoints = ExchangeEndpoints(
        ticker = "/api/v2/spot/market/tickers",
        orderBook = "/api/v2/spot/market/orderbook",
        trades = "/api/v2/spot/market/fills",
        candles = "/api/v2/spot/market/candles",
        pairs = "/api/v2/spot/public/symbols",
        balances = "/api/v2/spot/account/assets",
        placeOrder = "/api/v2/spot/trade/place-order",
        cancelOrder = "/api/v2/spot/trade/cancel-order",
        getOrder = "/api/v2/spot/trade/orderInfo",
        openOrders = "/api/v2/spot/trade/unfilled-orders",
        orderHistory = "/api/v2/spot/trade/history-orders",
        wsUrl = WS_PUBLIC
    )
    
    // Futures endpoints (Mix V2 API)
    private val futuresEndpoints = mapOf(
        "ticker" to "/api/v2/mix/market/ticker",
        "tickers" to "/api/v2/mix/market/tickers",
        "orderBook" to "/api/v2/mix/market/depth",
        "positions" to "/api/v2/mix/position/all-position",
        "placeOrder" to "/api/v2/mix/order/place-order",
        "cancelOrder" to "/api/v2/mix/order/cancel-order",
        "leverage" to "/api/v2/mix/account/set-leverage",
        "marginMode" to "/api/v2/mix/account/set-margin-mode",
        "account" to "/api/v2/mix/account/accounts"
    )
    
    // Copy trading endpoints
    private val copyTradingEndpoints = mapOf(
        "traders" to "/api/v2/copy/mix-trader/profit-summary-list",
        "followers" to "/api/v2/copy/mix-follower/query-current-orders",
        "follow" to "/api/v2/copy/mix-follower/set-follower-settings"
    )
    
    // =========================================================================
    // AUTHENTICATION - Bitget HMAC-SHA256 + Base64
    // =========================================================================
    
    override fun signRequest(
        method: String,
        path: String,
        params: Map<String, String>,
        body: String?,
        timestamp: Long
    ): Map<String, String> {
        // Bitget signature format:
        // timestamp + method + requestPath + body
        // Sign with HMAC-SHA256, then Base64 encode
        
        val queryString = if (params.isNotEmpty()) {
            "?" + params.entries.joinToString("&") { "${it.key}=${urlEncode(it.value)}" }
        } else ""
        
        val requestPath = path + queryString
        val preHash = "$timestamp${method.uppercase()}$requestPath${body ?: ""}"
        
        val signature = hmacSha256Base64(preHash, config.apiSecret)
        
        return mapOf(
            "ACCESS-KEY" to config.apiKey,
            "ACCESS-SIGN" to signature,
            "ACCESS-TIMESTAMP" to timestamp.toString(),
            "ACCESS-PASSPHRASE" to (config.passphrase ?: ""),
            "Content-Type" to "application/json",
            "locale" to "en-US"
        )
    }
    
    private fun hmacSha256Base64(data: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val hash = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }
    
    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
    }
    
    // =========================================================================
    // TICKER
    // =========================================================================
    
    override fun parseTicker(response: JsonObject, symbol: String): PriceTick? {
        return try {
            val data = response.getAsJsonArray("data")?.firstOrNull { 
                it.asJsonObject.get("symbol")?.asString == symbol 
            }?.asJsonObject ?: return null
            
            PriceTick(
                symbol = normaliseSymbol(symbol),
                bid = data.get("bidPr")?.asString?.toDoubleOrNull() ?: 0.0,
                ask = data.get("askPr")?.asString?.toDoubleOrNull() ?: 0.0,
                last = data.get("lastPr")?.asString?.toDoubleOrNull() ?: 0.0,
                volume24h = data.get("baseVolume")?.asString?.toDoubleOrNull() ?: 0.0,
                high24h = data.get("high24h")?.asString?.toDoubleOrNull() ?: 0.0,
                low24h = data.get("low24h")?.asString?.toDoubleOrNull() ?: 0.0,
                change24h = data.get("change24h")?.asString?.toDoubleOrNull() ?: 0.0,
                changePercent24h = data.get("changeUtc24h")?.asString?.toDoubleOrNull()?.times(100) ?: 0.0,
                timestamp = data.get("ts")?.asLong ?: System.currentTimeMillis(),
                exchange = "bitget"
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
            val data = response.getAsJsonObject("data") ?: return null
            
            val bids = data.getAsJsonArray("bids")?.map { bid ->
                val arr = bid.asJsonArray
                OrderBookLevel(
                    price = arr[0].asString.toDouble(),
                    quantity = arr[1].asString.toDouble()
                )
            } ?: emptyList()
            
            val asks = data.getAsJsonArray("asks")?.map { ask ->
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
                timestamp = data.get("ts")?.asLong ?: System.currentTimeMillis(),
                exchange = "bitget"
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
            val data = response.getAsJsonArray("data") ?: return emptyList()
            
            for (symbolJson in data) {
                val obj = symbolJson.asJsonObject
                val symbol = obj.get("symbol")?.asString ?: continue
                val status = obj.get("status")?.asString ?: ""
                
                if (status != "online") continue
                
                val baseCoin = obj.get("baseCoin")?.asString ?: continue
                val quoteCoin = obj.get("quoteCoin")?.asString ?: continue
                
                val minTradeAmount = obj.get("minTradeAmount")?.asString?.toDoubleOrNull() ?: 0.0
                val maxTradeAmount = obj.get("maxTradeAmount")?.asString?.toDoubleOrNull() ?: Double.MAX_VALUE
                val quantityPrecision = obj.get("quantityPrecision")?.asInt ?: 8
                val pricePrecision = obj.get("pricePrecision")?.asInt ?: 8
                val minTradeUSDT = obj.get("minTradeUSDT")?.asString?.toDoubleOrNull() ?: 5.0
                
                pairs.add(TradingPair(
                    symbol = "$baseCoin/$quoteCoin",
                    baseAsset = baseCoin,
                    quoteAsset = quoteCoin,
                    exchangeSymbol = symbol,
                    minQuantity = minTradeAmount,
                    maxQuantity = maxTradeAmount,
                    quantityPrecision = quantityPrecision,
                    pricePrecision = pricePrecision,
                    minNotional = minTradeUSDT,
                    isActive = true,
                    exchange = "bitget"
                ))
            }
        } catch (e: Exception) {
            // Return what we have
        }
        
        return pairs
    }
    
    // =========================================================================
    // BALANCES
    // =========================================================================
    
    override fun parseBalances(response: JsonObject): List<Balance> {
        val balances = mutableListOf<Balance>()
        
        try {
            val data = response.getAsJsonArray("data") ?: return emptyList()
            
            for (balanceJson in data) {
                val obj = balanceJson.asJsonObject
                val coin = obj.get("coin")?.asString ?: continue
                val available = obj.get("available")?.asString?.toDoubleOrNull() ?: 0.0
                val frozen = obj.get("frozen")?.asString?.toDoubleOrNull() ?: 0.0
                val locked = obj.get("locked")?.asString?.toDoubleOrNull() ?: 0.0
                
                val totalLocked = frozen + locked
                if (available > 0 || totalLocked > 0) {
                    balances.add(Balance(
                        asset = coin,
                        free = available,
                        locked = totalLocked,
                        total = available + totalLocked,
                        exchange = "bitget"
                    ))
                }
            }
        } catch (e: Exception) {
            // Return what we have
        }
        
        return balances
    }
    
    // =========================================================================
    // ORDER PARSING
    // =========================================================================
    
    override fun parseOrder(response: JsonObject): ExecutedOrder? {
        return try {
            val data = response.getAsJsonObject("data") ?: response
            
            val symbol = data.get("symbol")?.asString ?: return null
            val orderId = data.get("orderId")?.asString ?: return null
            
            ExecutedOrder(
                orderId = orderId,
                clientOrderId = data.get("clientOid")?.asString ?: "",
                symbol = normaliseSymbol(symbol),
                side = if (data.get("side")?.asString == "buy") TradeSide.BUY else TradeSide.SELL,
                type = parseBitgetOrderType(data.get("orderType")?.asString ?: "limit"),
                status = parseBitgetOrderStatus(data.get("status")?.asString ?: "new"),
                price = data.get("price")?.asString?.toDoubleOrNull() ?: 0.0,
                quantity = data.get("size")?.asString?.toDoubleOrNull() ?: 0.0,
                filledQuantity = data.get("baseVolume")?.asString?.toDoubleOrNull() ?: 0.0,
                averagePrice = data.get("priceAvg")?.asString?.toDoubleOrNull() ?: 0.0,
                fee = data.get("fee")?.asString?.toDoubleOrNull() ?: 0.0,
                feeCurrency = data.get("feeCcy")?.asString ?: "",
                createdAt = data.get("cTime")?.asLong ?: System.currentTimeMillis(),
                updatedAt = data.get("uTime")?.asLong ?: System.currentTimeMillis(),
                exchange = "bitget"
            )
        } catch (e: Exception) {
            null
        }
    }
    
    override fun parsePlaceOrderResponse(response: JsonObject, request: OrderRequest): OrderExecutionResult {
        return try {
            if (hasError(response)) {
                val code = response.get("code")?.asString ?: "-1"
                val msg = response.get("msg")?.asString ?: "Unknown error"
                return OrderExecutionResult.Error(Exception("Bitget error $code: $msg"))
            }
            
            val data = response.getAsJsonObject("data")
            val orderId = data?.get("orderId")?.asString
                ?: data?.get("data")?.asJsonObject?.get("orderId")?.asString
                ?: return OrderExecutionResult.Error(Exception("No order ID in response"))
            
            val clientOid = data.get("clientOid")?.asString ?: request.clientOrderId ?: ""
            
            OrderExecutionResult.Success(ExecutedOrder(
                orderId = orderId,
                clientOrderId = clientOid,
                symbol = request.symbol,
                side = request.side,
                type = request.type,
                status = OrderStatus.OPEN,
                price = request.price ?: 0.0,
                quantity = request.quantity,
                filledQuantity = 0.0,
                averagePrice = 0.0,
                fee = 0.0,
                feeCurrency = "",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                exchange = "bitget"
            ))
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
            "side" to if (request.side == TradeSide.BUY) "buy" else "sell",
            "orderType" to (ORDER_TYPE_MAP[request.type] ?: "limit"),
            "size" to request.quantity.toString(),
            "force" to (request.timeInForce?.let { TIF_MAP[it] } ?: "gtc")
        )
        
        // Price for limit orders
        if (request.type == OrderType.LIMIT || request.type == OrderType.STOP_LIMIT) {
            request.price?.let { params["price"] = it.toString() }
        }
        
        // Trigger price for stop orders
        if (request.type == OrderType.STOP_LOSS || request.type == OrderType.STOP_LIMIT) {
            request.stopPrice?.let { params["triggerPrice"] = it.toString() }
        }
        
        // Client order ID
        request.clientOrderId?.let { params["clientOid"] = it }
        
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
        val timestamp = System.currentTimeMillis()
        val headers = signRequest("GET", endpoints.balances, emptyMap(), null, timestamp)
        val url = "${config.baseUrl}${endpoints.balances}"
        
        return try {
            val response = executeSignedGet(url, headers)
            val json = gson.fromJson(response, JsonObject::class.java)
            parseBalances(json)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    override suspend fun placeOrder(request: OrderRequest): OrderExecutionResult {
        val timestamp = System.currentTimeMillis()
        val body = gson.toJson(buildOrderBody(request))
        val headers = signRequest("POST", endpoints.placeOrder, emptyMap(), body, timestamp)
        val url = "${config.baseUrl}${endpoints.placeOrder}"
        
        return try {
            val response = executeSignedPost(url, body, headers)
            val json = gson.fromJson(response, JsonObject::class.java)
            parsePlaceOrderResponse(json, request)
        } catch (e: Exception) {
            OrderExecutionResult.Error(e)
        }
    }
    
    override suspend fun cancelOrder(symbol: String, orderId: String): Boolean {
        val timestamp = System.currentTimeMillis()
        val body = gson.toJson(mapOf(
            "symbol" to toExchangeSymbol(symbol),
            "orderId" to orderId
        ))
        val headers = signRequest("POST", endpoints.cancelOrder, emptyMap(), body, timestamp)
        val url = "${config.baseUrl}${endpoints.cancelOrder}"
        
        return try {
            val response = executeSignedPost(url, body, headers)
            val json = gson.fromJson(response, JsonObject::class.java)
            !hasError(json)
        } catch (e: Exception) {
            false
        }
    }
    
    // =========================================================================
    // FUTURES API METHODS
    // =========================================================================
    
    /**
     * Get futures ticker
     */
    suspend fun getFuturesTicker(symbol: String, productType: String = USDT_FUTURES): FuturesTicker? {
        val url = "${config.baseUrl}${futuresEndpoints["ticker"]}?symbol=$symbol&productType=$productType"
        
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
                lastPrice = data.get("lastPr")?.asString?.toDoubleOrNull() ?: 0.0,
                markPrice = data.get("markPrice")?.asString?.toDoubleOrNull() ?: 0.0,
                indexPrice = data.get("indexPrice")?.asString?.toDoubleOrNull() ?: 0.0,
                fundingRate = data.get("fundingRate")?.asString?.toDoubleOrNull() ?: 0.0,
                nextFundingTime = data.get("nextFundingTime")?.asLong ?: 0L,
                volume24h = data.get("volume24h")?.asString?.toDoubleOrNull() ?: 0.0,
                openInterest = data.get("openInterest")?.asString?.toDoubleOrNull() ?: 0.0,
                high24h = data.get("high24h")?.asString?.toDoubleOrNull() ?: 0.0,
                low24h = data.get("low24h")?.asString?.toDoubleOrNull() ?: 0.0
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get futures positions
     */
    suspend fun getFuturesPositions(productType: String = USDT_FUTURES): List<FuturesPosition> {
        val timestamp = System.currentTimeMillis()
        val path = "${futuresEndpoints["positions"]}?productType=$productType"
        val headers = signRequest("GET", path, emptyMap(), null, timestamp)
        val url = "${config.baseUrl}$path"
        
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
                val holdSide = pos.get("holdSide")?.asString ?: "long"
                
                positions.add(FuturesPosition(
                    symbol = pos.get("symbol")?.asString ?: "",
                    side = if (holdSide == "long") PositionSide.LONG else PositionSide.SHORT,
                    quantity = pos.get("total")?.asString?.toDoubleOrNull() ?: 0.0,
                    entryPrice = pos.get("openPriceAvg")?.asString?.toDoubleOrNull() ?: 0.0,
                    markPrice = pos.get("markPrice")?.asString?.toDoubleOrNull() ?: 0.0,
                    liquidationPrice = pos.get("liquidationPrice")?.asString?.toDoubleOrNull() ?: 0.0,
                    unrealisedPnl = pos.get("unrealizedPL")?.asString?.toDoubleOrNull() ?: 0.0,
                    leverage = pos.get("leverage")?.asInt ?: 1,
                    marginType = if (pos.get("marginMode")?.asString == "isolated") 
                        MarginType.ISOLATED else MarginType.CROSS
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
    suspend fun placeFuturesOrder(
        request: FuturesOrderRequest,
        productType: String = USDT_FUTURES
    ): OrderExecutionResult {
        val timestamp = System.currentTimeMillis()
        
        val params = mutableMapOf(
            "symbol" to request.symbol,
            "productType" to productType,
            "marginMode" to if (request.marginType == MarginType.ISOLATED) "isolated" else "crossed",
            "marginCoin" to "USDT",
            "size" to request.quantity.toString(),
            "side" to if (request.side == TradeSide.BUY) "buy" else "sell",
            "tradeSide" to if (request.side == TradeSide.BUY) "open" else "close",
            "orderType" to if (request.type == OrderType.MARKET) "market" else "limit"
        )
        
        if (request.type == OrderType.LIMIT) {
            request.price?.let { params["price"] = it.toString() }
        }
        
        if (request.reduceOnly) {
            params["tradeSide"] = "close"
        }
        
        val body = gson.toJson(params)
        val path = futuresEndpoints["placeOrder"]!!
        val headers = signRequest("POST", path, emptyMap(), body, timestamp)
        val url = "${config.baseUrl}$path"
        
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
            if (hasError(response)) {
                val code = response.get("code")?.asString ?: "-1"
                val msg = response.get("msg")?.asString ?: "Unknown error"
                return OrderExecutionResult.Error(Exception("Bitget Futures error $code: $msg"))
            }
            
            val data = response.getAsJsonObject("data")
            val orderId = data?.get("orderId")?.asString ?: return OrderExecutionResult.Error(Exception("No order ID"))
            
            OrderExecutionResult.Success(ExecutedOrder(
                orderId = orderId,
                clientOrderId = data.get("clientOid")?.asString ?: "",
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
                exchange = "bitget"
            ))
        } catch (e: Exception) {
            OrderExecutionResult.Error(e)
        }
    }
    
    /**
     * Set futures leverage
     */
    suspend fun setFuturesLeverage(
        symbol: String,
        leverage: Int,
        productType: String = USDT_FUTURES,
        marginCoin: String = "USDT"
    ): Boolean {
        val timestamp = System.currentTimeMillis()
        val params = mapOf(
            "symbol" to symbol,
            "productType" to productType,
            "marginCoin" to marginCoin,
            "leverage" to leverage.toString()
        )
        
        val body = gson.toJson(params)
        val path = futuresEndpoints["leverage"]!!
        val headers = signRequest("POST", path, emptyMap(), body, timestamp)
        val url = "${config.baseUrl}$path"
        
        return try {
            val response = executeSignedPost(url, body, headers)
            val json = gson.fromJson(response, JsonObject::class.java)
            !hasError(json)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Set margin mode (cross/isolated)
     */
    suspend fun setMarginMode(
        symbol: String,
        marginMode: MarginType,
        productType: String = USDT_FUTURES,
        marginCoin: String = "USDT"
    ): Boolean {
        val timestamp = System.currentTimeMillis()
        val params = mapOf(
            "symbol" to symbol,
            "productType" to productType,
            "marginCoin" to marginCoin,
            "marginMode" to if (marginMode == MarginType.ISOLATED) "isolated" else "crossed"
        )
        
        val body = gson.toJson(params)
        val path = futuresEndpoints["marginMode"]!!
        val headers = signRequest("POST", path, emptyMap(), body, timestamp)
        val url = "${config.baseUrl}$path"
        
        return try {
            val response = executeSignedPost(url, body, headers)
            val json = gson.fromJson(response, JsonObject::class.java)
            !hasError(json)
        } catch (e: Exception) {
            false
        }
    }
    
    // =========================================================================
    // COPY TRADING API (Synergizes with Sovereign Vantage social features)
    // =========================================================================
    
    /**
     * Get top traders for copy trading
     */
    suspend fun getTopTraders(limit: Int = 20): List<CopyTrader> {
        val url = "${config.baseUrl}${copyTradingEndpoints["traders"]}?pageNo=1&pageSize=$limit"
        
        return try {
            val response = executeGet(url)
            val json = gson.fromJson(response, JsonObject::class.java)
            parseTopTraders(json)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun parseTopTraders(response: JsonObject): List<CopyTrader> {
        val traders = mutableListOf<CopyTrader>()
        
        try {
            val data = response.getAsJsonObject("data")?.getAsJsonArray("list") ?: return emptyList()
            
            for (traderJson in data) {
                val trader = traderJson.asJsonObject
                traders.add(CopyTrader(
                    traderId = trader.get("traderId")?.asString ?: "",
                    nickname = trader.get("nickName")?.asString ?: "",
                    totalProfit = trader.get("totalProfit")?.asString?.toDoubleOrNull() ?: 0.0,
                    profitRate = trader.get("profitRate")?.asString?.toDoubleOrNull() ?: 0.0,
                    winRate = trader.get("winRate")?.asString?.toDoubleOrNull() ?: 0.0,
                    followerCount = trader.get("followerNum")?.asInt ?: 0,
                    totalTrades = trader.get("totalOrderNum")?.asInt ?: 0
                ))
            }
        } catch (e: Exception) {
            // Return what we have
        }
        
        return traders
    }
    
    // =========================================================================
    // WEBSOCKET
    // =========================================================================
    
    override fun handleWsMessage(text: String) {
        try {
            val json = gson.fromJson(text, JsonObject::class.java)
            
            // Handle ping/pong
            if (json.has("event") && json.get("event").asString == "ping") {
                sendWsPong()
                return
            }
            
            // Handle data messages
            val action = json.get("action")?.asString ?: return
            val arg = json.getAsJsonObject("arg") ?: return
            val channel = arg.get("channel")?.asString ?: return
            val data = json.getAsJsonArray("data") ?: return
            
            when {
                channel == "ticker" -> handleWsTicker(data, arg)
                channel.startsWith("books") -> handleWsOrderBook(data, arg)
                channel == "trade" -> handleWsTrade(data, arg)
            }
        } catch (e: Exception) {
            // Ignore parse errors
        }
    }
    
    private fun sendWsPong() {
        val pong = gson.toJson(mapOf("event" to "pong"))
        sendWsMessage(pong)
    }
    
    private fun handleWsTicker(data: JsonArray, arg: JsonObject) {
        try {
            val symbol = arg.get("instId")?.asString ?: return
            val tickerData = data.firstOrNull()?.asJsonObject ?: return
            
            val tick = PriceTick(
                symbol = normaliseSymbol(symbol),
                bid = tickerData.get("bidPr")?.asString?.toDoubleOrNull() ?: 0.0,
                ask = tickerData.get("askPr")?.asString?.toDoubleOrNull() ?: 0.0,
                last = tickerData.get("lastPr")?.asString?.toDoubleOrNull() ?: 0.0,
                volume24h = tickerData.get("baseVolume")?.asString?.toDoubleOrNull() ?: 0.0,
                high24h = tickerData.get("high24h")?.asString?.toDoubleOrNull() ?: 0.0,
                low24h = tickerData.get("low24h")?.asString?.toDoubleOrNull() ?: 0.0,
                change24h = 0.0,
                changePercent24h = tickerData.get("change24h")?.asString?.toDoubleOrNull()?.times(100) ?: 0.0,
                timestamp = tickerData.get("ts")?.asLong ?: System.currentTimeMillis(),
                exchange = "bitget"
            )
            
            scope.launch { _priceUpdates.emit(tick) }
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    private fun handleWsOrderBook(data: JsonArray, arg: JsonObject) {
        try {
            val symbol = arg.get("instId")?.asString ?: return
            val bookData = data.firstOrNull()?.asJsonObject ?: return
            
            val bids = bookData.getAsJsonArray("bids")?.map { bid ->
                val arr = bid.asJsonArray
                OrderBookLevel(arr[0].asString.toDouble(), arr[1].asString.toDouble())
            } ?: emptyList()
            
            val asks = bookData.getAsJsonArray("asks")?.map { ask ->
                val arr = ask.asJsonArray
                OrderBookLevel(arr[0].asString.toDouble(), arr[1].asString.toDouble())
            } ?: emptyList()
            
            val orderBook = OrderBook(
                symbol = normaliseSymbol(symbol),
                bids = bids,
                asks = asks,
                timestamp = bookData.get("ts")?.asLong ?: System.currentTimeMillis(),
                exchange = "bitget"
            )
            
            scope.launch { _orderBookUpdates.emit(orderBook) }
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    private fun handleWsTrade(data: JsonArray, arg: JsonObject) {
        // Handle trade updates
    }
    
    override fun buildWsSubscription(channels: List<String>, symbols: List<String>): String {
        val args = mutableListOf<Map<String, String>>()
        
        symbols.forEach { symbol ->
            val instId = toExchangeSymbol(symbol)
            
            channels.forEach { channel ->
                val wsChannel = when (channel) {
                    "ticker" -> "ticker"
                    "depth", "orderbook" -> "books15"
                    "trade", "trades" -> "trade"
                    "candle", "kline" -> "candle1m"
                    else -> "ticker"
                }
                
                args.add(mapOf(
                    "instType" to "SPOT",
                    "channel" to wsChannel,
                    "instId" to instId
                ))
            }
        }
        
        return gson.toJson(mapOf(
            "op" to "subscribe",
            "args" to args
        ))
    }
    
    // =========================================================================
    // HELPERS
    // =========================================================================
    
    override fun hasError(response: JsonObject): Boolean {
        val code = response.get("code")?.asString
        return code != null && code != "00000"
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
    
    private fun parseBitgetOrderType(type: String): OrderType {
        return when (type.lowercase()) {
            "market" -> OrderType.MARKET
            "limit" -> OrderType.LIMIT
            else -> OrderType.LIMIT
        }
    }
    
    private fun parseBitgetOrderStatus(status: String): OrderStatus {
        return when (status.lowercase()) {
            "new", "init" -> OrderStatus.OPEN
            "partial_fill", "partial-fill" -> OrderStatus.PARTIALLY_FILLED
            "full_fill", "full-fill", "filled" -> OrderStatus.FILLED
            "cancelled", "canceled" -> OrderStatus.CANCELLED
            else -> OrderStatus.PENDING
        }
    }
}

// =========================================================================
// COPY TRADING DATA CLASS
// =========================================================================

data class CopyTrader(
    val traderId: String,
    val nickname: String,
    val totalProfit: Double,
    val profitRate: Double,
    val winRate: Double,
    val followerCount: Int,
    val totalTrades: Int
)
