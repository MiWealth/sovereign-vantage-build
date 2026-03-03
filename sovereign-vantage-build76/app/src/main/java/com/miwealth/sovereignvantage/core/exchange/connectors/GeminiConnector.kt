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
import java.security.MessageDigest

/**
 * GEMINI CONNECTOR - Complete Implementation
 * 
 * Full-featured Gemini exchange connector with:
 * - REST API for spot trading (V1 API)
 * - WebSocket for real-time market data (V2)
 * - HMAC-SHA384 + Base64 authentication (unique to Gemini)
 * - PQC integration via BaseCEXConnector
 * 
 * Regulatory Status: NYDFS Licensed - Institutional Grade
 * - New York Department of Financial Services regulated
 * - SOC 2 Type 2 certified
 * - Insurance coverage for digital assets
 * 
 * API Documentation: https://docs.gemini.com/rest-api/
 * 
 * Endpoints:
 * - REST: https://api.gemini.com
 * - Sandbox: https://api.sandbox.gemini.com
 * - WebSocket: wss://api.gemini.com/v2/marketdata
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

class GeminiConnector(
    config: ExchangeConfig,
    secureHttpClient: HybridSecureHttpClient? = null,
    credentialVault: PQCCredentialVault? = null
) : BaseCEXConnector(config, secureHttpClient, credentialVault) {
    
    companion object {
        private const val API_BASE = "https://api.gemini.com"
        private const val SANDBOX_BASE = "https://api.sandbox.gemini.com"
        private const val WS_URL = "wss://api.gemini.com/v2/marketdata"
        private const val WS_SANDBOX = "wss://api.sandbox.gemini.com/v2/marketdata"
        
        // Gemini order types
        private val ORDER_TYPE_MAP = mapOf(
            OrderType.MARKET to "exchange limit",  // Gemini doesn't have true market orders
            OrderType.LIMIT to "exchange limit",
            OrderType.STOP_LOSS to "exchange stop limit",
            OrderType.STOP_LIMIT to "exchange stop limit"
        )
        
        // Order execution options
        private val EXECUTION_OPTIONS = mapOf(
            TimeInForce.IOC to listOf("immediate-or-cancel"),
            TimeInForce.FOK to listOf("fill-or-kill"),
            TimeInForce.GTD to listOf("maker-or-cancel")  // Post-only
        )
    }
    
    // =========================================================================
    // EXCHANGE CAPABILITIES
    // =========================================================================
    
    override val capabilities = ExchangeCapabilities(
        supportsSpotTrading = true,
        supportsFutures = false,      // Gemini is spot-only (regulatory clarity)
        supportsMargin = false,       // No margin trading
        supportsOptions = false,
        supportsLending = true,       // Gemini Earn
        supportsStaking = true,       // Staking available
        supportsWebSocket = true,
        supportsOrderbook = true,
        supportsMarketOrders = false, // Only limit orders (more precise)
        supportsLimitOrders = true,
        supportsStopOrders = true,
        supportsPostOnly = true,      // maker-or-cancel option
        supportsCancelAll = true,
        maxOrdersPerSecond = 5,       // Conservative rate limit
        minOrderValue = 1.0,          // $1 minimum for most pairs
        tradingFeeMaker = 0.001,      // 0.10% maker (ActiveTrader)
        tradingFeeTaker = 0.0035,     // 0.35% taker (ActiveTrader)
        withdrawalEnabled = false,
        networks = listOf(
            BlockchainNetwork.BITCOIN,
            BlockchainNetwork.ETHEREUM,
            BlockchainNetwork.SOLANA,
            BlockchainNetwork.POLYGON,
            BlockchainNetwork.DOGECOIN
        )
    )
    
    // =========================================================================
    // API ENDPOINTS
    // =========================================================================
    
    override val endpoints = ExchangeEndpoints(
        ticker = "/v2/ticker/{symbol}",
        orderBook = "/v1/book/{symbol}",
        trades = "/v1/trades/{symbol}",
        candles = "/v2/candles/{symbol}/{timeframe}",
        pairs = "/v1/symbols",
        balances = "/v1/balances",
        placeOrder = "/v1/order/new",
        cancelOrder = "/v1/order/cancel",
        getOrder = "/v1/order/status",
        openOrders = "/v1/orders",
        orderHistory = "/v1/orders/history",
        wsUrl = WS_URL
    )
    
    // =========================================================================
    // AUTHENTICATION - Gemini's HMAC-SHA384 + Base64
    // =========================================================================
    
    override fun signRequest(
        method: String,
        path: String,
        params: Map<String, String>,
        body: String?,
        timestamp: Long
    ): Map<String, String> {
        // Gemini uses HMAC-SHA384 with Base64 encoded payload
        // Payload is JSON object encoded as Base64
        // Signature is HMAC-SHA384 of the Base64 payload
        
        val nonce = timestamp
        val payload = gson.toJson(mapOf(
            "request" to path,
            "nonce" to nonce
        ) + params)
        
        val b64Payload = Base64.encodeToString(payload.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val signature = hmacSha384(b64Payload, config.apiSecret)
        
        return mapOf(
            "Content-Type" to "text/plain",
            "Content-Length" to "0",
            "X-GEMINI-APIKEY" to config.apiKey,
            "X-GEMINI-PAYLOAD" to b64Payload,
            "X-GEMINI-SIGNATURE" to signature,
            "Cache-Control" to "no-cache"
        )
    }
    
    /**
     * Build signed request for Gemini private API
     */
    private fun buildSignedRequest(path: String, params: Map<String, Any> = emptyMap()): Pair<String, Map<String, String>> {
        val nonce = System.currentTimeMillis()
        val baseUrl = if (config.testnet) SANDBOX_BASE else API_BASE
        
        val fullPayload = mapOf(
            "request" to path,
            "nonce" to nonce
        ) + params
        
        val payloadJson = gson.toJson(fullPayload)
        val b64Payload = Base64.encodeToString(payloadJson.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val signature = hmacSha384(b64Payload, config.apiSecret)
        
        val headers = mapOf(
            "Content-Type" to "text/plain",
            "Content-Length" to "0",
            "X-GEMINI-APIKEY" to config.apiKey,
            "X-GEMINI-PAYLOAD" to b64Payload,
            "X-GEMINI-SIGNATURE" to signature,
            "Cache-Control" to "no-cache"
        )
        
        return "$baseUrl$path" to headers
    }
    
    private fun hmacSha384(data: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA384")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA384"))
        val hash = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
    
    // =========================================================================
    // TICKER
    // =========================================================================
    
    override fun parseTicker(response: JsonObject, symbol: String): PriceTick? {
        return try {
            PriceTick(
                symbol = normaliseSymbol(symbol),
                bid = response.get("bid")?.asString?.toDoubleOrNull() ?: 0.0,
                ask = response.get("ask")?.asString?.toDoubleOrNull() ?: 0.0,
                last = response.get("last")?.asString?.toDoubleOrNull() ?: 0.0,
                volume24h = response.getAsJsonObject("volume")?.get(symbol.take(3).uppercase())?.asDouble ?: 0.0,
                high24h = response.get("high")?.asString?.toDoubleOrNull() ?: 0.0,
                low24h = response.get("low")?.asString?.toDoubleOrNull() ?: 0.0,
                change24h = 0.0,
                changePercent24h = response.getAsJsonArray("changes")?.firstOrNull()?.asString?.toDoubleOrNull() ?: 0.0,
                timestamp = System.currentTimeMillis(),
                exchange = "gemini"
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
                val obj = bid.asJsonObject
                OrderBookLevel(
                    price = obj.get("price")?.asString?.toDouble() ?: 0.0,
                    quantity = obj.get("amount")?.asString?.toDouble() ?: 0.0
                )
            } ?: emptyList()
            
            val asks = response.getAsJsonArray("asks")?.map { ask ->
                val obj = ask.asJsonObject
                OrderBookLevel(
                    price = obj.get("price")?.asString?.toDouble() ?: 0.0,
                    quantity = obj.get("amount")?.asString?.toDouble() ?: 0.0
                )
            } ?: emptyList()
            
            OrderBook(
                symbol = normaliseSymbol(symbol),
                bids = bids,
                asks = asks,
                timestamp = System.currentTimeMillis(),
                exchange = "gemini"
            )
        } catch (e: Exception) {
            null
        }
    }
    
    // =========================================================================
    // TRADING PAIRS
    // =========================================================================
    
    override fun parseTradingPairs(response: JsonObject): List<TradingPair> {
        // Gemini returns array at root level, not in "data" field
        // This method handles the case where we wrap the array response
        val pairs = mutableListOf<TradingPair>()
        
        try {
            val symbols = response.getAsJsonArray("symbols") ?: return emptyList()
            
            for (symbolJson in symbols) {
                val symbol = symbolJson.asString
                val normalized = normaliseSymbol(symbol)
                val parts = normalized.split("/")
                if (parts.size != 2) continue
                
                pairs.add(TradingPair(
                    symbol = normalized,
                    baseAsset = parts[0],
                    quoteAsset = parts[1],
                    exchangeSymbol = symbol,
                    minQuantity = 0.00001,  // Gemini has low minimums
                    maxQuantity = Double.MAX_VALUE,
                    quantityPrecision = 8,
                    pricePrecision = 2,
                    minNotional = 1.0,
                    isActive = true,
                    exchange = "gemini"
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
            // Response is wrapped in "balances" array from our API call
            val balancesArray = response.getAsJsonArray("balances") ?: return emptyList()
            
            for (balanceJson in balancesArray) {
                val obj = balanceJson.asJsonObject
                val currency = obj.get("currency")?.asString?.uppercase() ?: continue
                val available = obj.get("available")?.asString?.toDoubleOrNull() ?: 0.0
                val availableForWithdrawal = obj.get("availableForWithdrawal")?.asString?.toDoubleOrNull() ?: available
                val amount = obj.get("amount")?.asString?.toDoubleOrNull() ?: available
                
                val locked = amount - available
                
                if (available > 0 || locked > 0) {
                    balances.add(Balance(
                        asset = currency,
                        free = available,
                        locked = locked,
                        total = amount))
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
            val orderId = response.get("order_id")?.asString ?: return null
            val symbol = response.get("symbol")?.asString ?: return null
            
            val side = when (response.get("side")?.asString) {
                "buy" -> TradeSide.BUY
                "sell" -> TradeSide.SELL
                else -> TradeSide.BUY
            }
            
            val executedAmount = response.get("executed_amount")?.asString?.toDoubleOrNull() ?: 0.0
            val originalAmount = response.get("original_amount")?.asString?.toDoubleOrNull() ?: 0.0
            val remainingAmount = response.get("remaining_amount")?.asString?.toDoubleOrNull() ?: 0.0
            val avgPrice = response.get("avg_execution_price")?.asString?.toDoubleOrNull() ?: 0.0
            
            val status = when {
                response.get("is_cancelled")?.asBoolean == true -> OrderStatus.CANCELLED
                remainingAmount == 0.0 && executedAmount > 0 -> OrderStatus.FILLED
                executedAmount > 0 -> OrderStatus.PARTIALLY_FILLED
                response.get("is_live")?.asBoolean == true -> OrderStatus.OPEN
                else -> OrderStatus.PENDING
            }
            
            ExecutedOrder(
                orderId = orderId,
                clientOrderId = response.get("client_order_id")?.asString ?: "",
                symbol = normaliseSymbol(symbol),
                side = side,
                type = if (response.get("type")?.asString?.contains("stop") == true) OrderType.STOP_LIMIT else OrderType.LIMIT,
                status = status,
                price = response.get("price")?.asString?.toDoubleOrNull() ?: 0.0,
                quantity = originalAmount,
                executedQuantity = executedAmount,
                executedPrice = avgPrice,
                fee = 0.0,  // Gemini returns fees separately
                feeCurrency = "",
                timestamp = response.get("timestampms")?.asLong ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                exchange = "gemini"
            )
        } catch (e: Exception) {
            null
        }
    }
    
    override fun parsePlaceOrderResponse(response: JsonObject, request: OrderRequest): OrderExecutionResult {
        return try {
            // Check for error
            if (response.has("result") && response.get("result").asString == "error") {
                val reason = response.get("reason")?.asString ?: "Unknown error"
                val message = response.get("message")?.asString ?: reason
                return OrderExecutionResult.Error(Exception("Gemini error: $message"))
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
            "amount" to request.quantity.toString(),
            "side" to if (request.side == TradeSide.BUY) "buy" else "sell",
            "type" to (ORDER_TYPE_MAP[request.type] ?: "exchange limit")
        )
        
        // Price is required for limit orders
        // For market orders, use a price far from current (Gemini doesn't support true market orders)
        params["price"] = (request.price ?: 0.0).toString()
        
        // Client order ID
        request.clientOrderId?.let { params["client_order_id"] = it }
        
        // Execution options
        request.timeInForce?.let { tif ->
            EXECUTION_OPTIONS[tif]?.let { options ->
                params["options"] = options.joinToString(",")
            }
        }
        
        // Stop price for stop orders
        if (request.type == OrderType.STOP_LOSS || request.type == OrderType.STOP_LIMIT) {
            request.stopPrice?.let { params["stop_price"] = it.toString() }
        }
        
        return params
    }
    
    // =========================================================================
    // API METHODS
    // =========================================================================
    
    override suspend fun getTicker(symbol: String): PriceTick? {
        val exchangeSymbol = toExchangeSymbol(symbol)
        val baseUrl = if (config.testnet) SANDBOX_BASE else API_BASE
        val url = "$baseUrl/v2/ticker/$exchangeSymbol"
        
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
        val baseUrl = if (config.testnet) SANDBOX_BASE else API_BASE
        val url = "$baseUrl/v1/book/$exchangeSymbol?limit_bids=$limit&limit_asks=$limit"
        
        return try {
            val response = executeGet(url)
            val json = gson.fromJson(response, JsonObject::class.java)
            parseOrderBook(json, exchangeSymbol)
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun getTradingPairs(): List<TradingPair> {
        val baseUrl = if (config.testnet) SANDBOX_BASE else API_BASE
        val url = "$baseUrl/v1/symbols"
        
        return try {
            val response = executeGet(url)
            // Gemini returns array directly, wrap it for parsing
            val json = JsonObject()
            json.add("symbols", gson.fromJson(response, JsonArray::class.java))
            parseTradingPairs(json)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    override suspend fun getBalances(): List<Balance> {
        val (url, headers) = buildSignedRequest("/v1/balances")
        
        return try {
            val response = executeSignedPost(url, "", headers)
            // Wrap array response
            val json = JsonObject()
            json.add("balances", gson.fromJson(response, JsonArray::class.java))
            parseBalances(json)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    override suspend fun placeOrder(request: OrderRequest): OrderExecutionResult {
        val params = buildOrderBody(request).mapValues { it.value as Any }
        val (url, headers) = buildSignedRequest("/v1/order/new", params)
        
        return try {
            val response = executeSignedPost(url, "", headers)
            val json = gson.fromJson(response, JsonObject::class.java)
            parsePlaceOrderResponse(json, request)
        } catch (e: Exception) {
            OrderExecutionResult.Error(e)
        }
    }
    
    override suspend fun cancelOrder(symbol: String, orderId: String): Boolean {
        val (url, headers) = buildSignedRequest("/v1/order/cancel", mapOf("order_id" to orderId))
        
        return try {
            val response = executeSignedPost(url, "", headers)
            val json = gson.fromJson(response, JsonObject::class.java)
            json.get("is_cancelled")?.asBoolean == true
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun getOrder(symbol: String, orderId: String): ExecutedOrder? {
        val (url, headers) = buildSignedRequest("/v1/order/status", mapOf("order_id" to orderId))
        
        return try {
            val response = executeSignedPost(url, "", headers)
            val json = gson.fromJson(response, JsonObject::class.java)
            parseOrder(json)
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun getOpenOrders(symbol: String?): List<ExecutedOrder> {
        val (url, headers) = buildSignedRequest("/v1/orders")
        
        return try {
            val response = executeSignedPost(url, "", headers)
            val json = gson.fromJson(response, JsonArray::class.java)
            json.mapNotNull { parseOrder(it.asJsonObject) }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Cancel all orders for a symbol
     */
    suspend fun cancelAllOrders(): List<CancelResult> {
        val (url, headers) = buildSignedRequest("/v1/order/cancel/all")
        
        return try {
            val response = executeSignedPost(url, "", headers)
            val json = gson.fromJson(response, JsonObject::class.java)
            val details = json.getAsJsonObject("details") ?: return emptyList()
            
            val cancelled = details.getAsJsonArray("cancelledOrders")?.map {
                CancelResult(it.asString, true)
            } ?: emptyList()
            
            val rejected = details.getAsJsonArray("cancelRejects")?.map {
                CancelResult(it.asString, false)
            } ?: emptyList()
            
            cancelled + rejected
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get notional volume (for fee tier calculation)
     */
    suspend fun getNotionalVolume(): NotionalVolume? {
        val (url, headers) = buildSignedRequest("/v1/notionalvolume")
        
        return try {
            val response = executeSignedPost(url, "", headers)
            val json = gson.fromJson(response, JsonObject::class.java)
            
            NotionalVolume(
                date = json.get("date")?.asString ?: "",
                lastUpdated = json.get("last_updated_ms")?.asLong ?: 0L,
                webMakerFeeBps = json.get("web_maker_fee_bps")?.asInt ?: 0,
                webTakerFeeBps = json.get("web_taker_fee_bps")?.asInt ?: 0,
                apiMakerFeeBps = json.get("api_maker_fee_bps")?.asInt ?: 0,
                apiTakerFeeBps = json.get("api_taker_fee_bps")?.asInt ?: 0,
                notionalVolume30d = json.get("notional_30d_volume")?.asDouble ?: 0.0
            )
        } catch (e: Exception) {
            null
        }
    }
    
    // =========================================================================
    // WEBSOCKET
    // =========================================================================
    
    override fun handleWsMessage(text: String) {
        try {
            val json = gson.fromJson(text, JsonObject::class.java)
            
            val type = json.get("type")?.asString ?: return
            
            when (type) {
                "l2_updates" -> handleWsOrderBook(json)
                "trade" -> handleWsTrade(json)
                "candles" -> handleWsCandle(json)
                "heartbeat" -> { /* Ignore heartbeat */ }
            }
        } catch (e: Exception) {
            // Ignore parse errors
        }
    }
    
    private fun handleWsOrderBook(json: JsonObject) {
        try {
            val symbol = json.get("symbol")?.asString ?: return
            val changes = json.getAsJsonArray("changes") ?: return
            
            val bids = mutableListOf<OrderBookLevel>()
            val asks = mutableListOf<OrderBookLevel>()
            
            for (change in changes) {
                val arr = change.asJsonArray
                val side = arr[0].asString
                val price = arr[1].asString.toDouble()
                val quantity = arr[2].asString.toDouble()
                
                val level = OrderBookLevel(price, quantity)
                if (side == "buy") bids.add(level) else asks.add(level)
            }
            
            val orderBook = OrderBook(
                symbol = normaliseSymbol(symbol),
                bids = bids.sortedByDescending { it.price },
                asks = asks.sortedBy { it.price },
                timestamp = json.get("timestampms")?.asLong ?: System.currentTimeMillis(),
                exchange = "gemini"
            )
            
            scope.launch { _orderBookUpdates.emit(orderBook) }
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    private fun handleWsTrade(json: JsonObject) {
        try {
            val symbol = json.get("symbol")?.asString ?: return
            val price = json.get("price")?.asString?.toDoubleOrNull() ?: return
            
            val tick = PriceTick(
                symbol = normaliseSymbol(symbol),
                bid = 0.0,
                ask = 0.0,
                last = price,
                volume24h = 0.0,
                high24h = 0.0,
                low24h = 0.0,
                change24h = 0.0,
                changePercent24h = 0.0,
                timestamp = json.get("timestampms")?.asLong ?: System.currentTimeMillis(),
                exchange = "gemini"
            )
            
            scope.launch { _priceUpdates.emit(tick) }
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    private fun handleWsCandle(json: JsonObject) {
        // Handle candle updates if needed
    }
    
    override fun buildWsSubscription(channels: List<String>, symbols: List<String>): String {
        val subscriptions = mutableListOf<Map<String, Any>>()
        
        symbols.forEach { symbol ->
            val geminiSymbol = toExchangeSymbol(symbol)
            
            channels.forEach { channel ->
                val sub = when (channel) {
                    "ticker", "trade", "trades" -> mapOf(
                        "name" to "l2",
                        "symbols" to listOf(geminiSymbol)
                    )
                    "depth", "orderbook" -> mapOf(
                        "name" to "l2",
                        "symbols" to listOf(geminiSymbol)
                    )
                    "candle", "kline" -> mapOf(
                        "name" to "candles_1m",
                        "symbols" to listOf(geminiSymbol)
                    )
                    else -> mapOf(
                        "name" to "l2",
                        "symbols" to listOf(geminiSymbol)
                    )
                }
                subscriptions.add(sub)
            }
        }
        
        return gson.toJson(mapOf(
            "type" to "subscribe",
            "subscriptions" to subscriptions
        ))
    }
    
    // =========================================================================
    // HELPERS
    // =========================================================================
    
    override fun hasError(response: JsonObject): Boolean {
        return response.has("result") && response.get("result").asString == "error"
    }
    
    override fun toExchangeSymbol(normalisedSymbol: String): String {
        // BTC/USD -> btcusd (lowercase)
        return normalisedSymbol.replace("/", "").lowercase()
    }
    
    override fun normaliseSymbol(exchangeSymbol: String): String {
        // btcusd -> BTC/USD
        val upper = exchangeSymbol.uppercase()
        val quotes = listOf("USD", "USDT", "BTC", "ETH", "EUR", "GBP", "SGD")
        
        for (quote in quotes) {
            if (upper.endsWith(quote)) {
                val base = upper.dropLast(quote.length)
                if (base.isNotEmpty()) return "$base/$quote"
            }
        }
        
        return upper
    }
}

// =========================================================================
// GEMINI-SPECIFIC DATA CLASSES
// =========================================================================

data class CancelResult(
    val orderId: String,
    val cancelled: Boolean
)

data class NotionalVolume(
    val date: String,
    val lastUpdated: Long,
    val webMakerFeeBps: Int,
    val webTakerFeeBps: Int,
    val apiMakerFeeBps: Int,
    val apiTakerFeeBps: Int,
    val notionalVolume30d: Double
)
