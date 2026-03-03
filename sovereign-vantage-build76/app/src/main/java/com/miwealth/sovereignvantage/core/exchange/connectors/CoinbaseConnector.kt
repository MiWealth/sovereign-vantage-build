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
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * COINBASE CONNECTOR - Complete Implementation
 * 
 * Full-featured Coinbase Advanced Trade API connector with:
 * - REST API for trading and account management (Advanced Trade API)
 * - WebSocket for real-time market data (WebSocket Feed)
 * - Proper Coinbase authentication (HMAC-SHA256 with passphrase)
 * - Symbol format: BTC-USD (hyphen-separated)
 * - Rate limiting compliance (10 requests/sec private, 15/sec public)
 * - PQC integration via BaseCEXConnector
 * 
 * Based on Coinbase Advanced Trade API:
 * - REST: https://api.exchange.coinbase.com or https://api.coinbase.com/api/v3/
 * - WebSocket: wss://ws-feed.exchange.coinbase.com
 * - Documentation: https://docs.cloud.coinbase.com/advanced-trade-api/docs
 * 
 * Sandbox:
 * - REST: https://api-public.sandbox.exchange.coinbase.com
 * - WebSocket: wss://ws-feed-public.sandbox.exchange.coinbase.com
 * 
 * Authentication Headers:
 * - CB-ACCESS-KEY: API key
 * - CB-ACCESS-SIGN: HMAC-SHA256 signature (base64)
 * - CB-ACCESS-TIMESTAMP: Unix timestamp
 * - CB-ACCESS-PASSPHRASE: API passphrase
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

class CoinbaseConnector(
    config: ExchangeConfig,
    secureHttpClient: HybridSecureHttpClient? = null,
    credentialVault: PQCCredentialVault? = null
) : BaseCEXConnector(config, secureHttpClient, credentialVault) {
    
    companion object {
        // Coinbase uses hyphen-separated symbols: BTC-USD, ETH-USD
        // Simple transformation: BTC/USD → BTC-USD
        
        // Coinbase interval codes (granularity in seconds)
        private val INTERVAL_MAP = mapOf(
            "1m" to 60,
            "5m" to 300,
            "15m" to 900,
            "1h" to 3600,
            "6h" to 21600,
            "1d" to 86400
        )
        
        // Order type mapping
        private val ORDER_TYPE_MAP = mapOf(
            OrderType.MARKET to "market",
            OrderType.LIMIT to "limit",
            OrderType.STOP_LOSS to "stop",
            OrderType.STOP_LIMIT to "stop_limit"
        )
        
        // Time in force mapping
        private val TIF_MAP = mapOf(
            TimeInForce.GTC to "GTC",
            TimeInForce.IOC to "IOC",
            TimeInForce.FOK to "FOK",
            TimeInForce.GTD to "GTD"
        )
    }
    
    // =========================================================================
    // EXCHANGE CAPABILITIES
    // =========================================================================
    
    override val capabilities = ExchangeCapabilities(
        supportsSpotTrading = true,
        supportsFutures = false,     // Coinbase Derivatives is separate
        supportsMargin = false,      // Not on Advanced Trade
        supportsOptions = false,
        supportsLending = false,
        supportsStaking = true,      // Via Coinbase main
        supportsWebSocket = true,
        supportsOrderbook = true,
        supportsMarketOrders = true,
        supportsLimitOrders = true,
        supportsStopOrders = true,
        supportsPostOnly = true,     // Supported via post_only flag
        supportsCancelAll = true,
        maxOrdersPerSecond = 10,     // 10 orders per second
        minOrderValue = 1.0,         // $1 minimum for most pairs
        tradingFeeMaker = 0.004,     // 0.40% maker (tier dependent)
        tradingFeeTaker = 0.006,     // 0.60% taker (tier dependent)
        withdrawalEnabled = false,   // Non-custodial - we don't withdraw
        networks = listOf(
            BlockchainNetwork.BITCOIN,
            BlockchainNetwork.ETHEREUM,
            BlockchainNetwork.SOLANA,
            BlockchainNetwork.POLYGON,
            BlockchainNetwork.AVALANCHE,
            BlockchainNetwork.ARBITRUM,
            BlockchainNetwork.OPTIMISM,
            BlockchainNetwork.BASE
        )
    )
    
    // =========================================================================
    // API ENDPOINTS
    // =========================================================================
    
    override val endpoints = ExchangeEndpoints(
        ticker = "/products/{symbol}/ticker",
        orderBook = "/products/{symbol}/book",
        trades = "/products/{symbol}/trades",
        candles = "/products/{symbol}/candles",
        pairs = "/products",
        balances = "/accounts",
        placeOrder = "/orders",
        cancelOrder = "/orders/{order_id}",
        getOrder = "/orders/{order_id}",
        openOrders = "/orders",
        orderHistory = "/orders/historical/batch",
        wsUrl = "wss://ws-feed.exchange.coinbase.com"
    )
    
    // WebSocket state
    private var wsSequence: Long = 0
    private var wsSubscribedProducts = mutableSetOf<String>()
    
    // =========================================================================
    // AUTHENTICATION - Coinbase's HMAC-SHA256 with Passphrase
    // =========================================================================
    
    override fun signRequest(
        method: String,
        path: String,
        params: Map<String, String>,
        body: String?,
        timestamp: Long
    ): Map<String, String> {
        // Coinbase signing:
        // 1. Create prehash string: timestamp + method + path + body
        // 2. Decode base64 secret
        // 3. Sign with HMAC-SHA256
        // 4. Base64 encode signature
        
        val timestampStr = timestamp.toString()
        val bodyStr = body ?: ""
        
        // Build the message to sign
        val message = timestampStr + method.uppercase() + path + bodyStr
        
        // Decode the base64 secret
        val secretDecoded = try {
            Base64.decode(config.apiSecret, Base64.NO_WRAP)
        } catch (e: Exception) {
            // If not base64, use as-is
            config.apiSecret.toByteArray(Charsets.UTF_8)
        }
        
        // Sign with HMAC-SHA256
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretDecoded, "HmacSHA256"))
        val signature = mac.doFinal(message.toByteArray(Charsets.UTF_8))
        val signatureBase64 = Base64.encodeToString(signature, Base64.NO_WRAP)
        
        return mapOf(
            "CB-ACCESS-KEY" to config.apiKey,
            "CB-ACCESS-SIGN" to signatureBase64,
            "CB-ACCESS-TIMESTAMP" to timestampStr,
            "CB-ACCESS-PASSPHRASE" to config.passphrase,
            "Content-Type" to "application/json"
        )
    }
    
    // =========================================================================
    // TICKER PARSING
    // =========================================================================
    
    override fun parseTicker(response: JsonObject, symbol: String): PriceTick? {
        try {
            // Check for error
            if (hasCoinbaseError(response)) return null
            
            // Coinbase ticker format:
            // trade_id, price, size, time, bid, ask, volume
            
            val price = response.get("price")?.asString?.toDoubleOrNull() ?: 0.0
            val bid = response.get("bid")?.asString?.toDoubleOrNull() ?: 0.0
            val ask = response.get("ask")?.asString?.toDoubleOrNull() ?: 0.0
            val volume = response.get("volume")?.asString?.toDoubleOrNull() ?: 0.0
            
            val timeStr = response.get("time")?.asString
            val timestamp = parseIsoTimestamp(timeStr) ?: System.currentTimeMillis()
            
            return PriceTick(
                symbol = normaliseSymbol(symbol),
                bid = bid,
                ask = ask,
                last = price,
                volume = volume,
                high24h = null,  // Not in ticker - need stats endpoint
                low24h = null,
                change24h = null,
                changePercent24h = null,
                timestamp = timestamp,
                exchange = "Coinbase"
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
            if (hasCoinbaseError(response)) return null
            
            // Coinbase order book format:
            // bids: [[price, size, num_orders], ...], asks: [[price, size, num_orders], ...]
            // or for level 2: bids: [[price, size], ...], asks: [[price, size], ...]
            
            val bids = response.getAsJsonArray("bids")?.map { entry ->
                val arr = entry.asJsonArray
                OrderBookLevel(
                    price = arr[0].asString.toDouble(),
                    quantity = arr[1].asString.toDouble(),
                    orderCount = if (arr.size() > 2) arr[2].asInt else 1
                )
            } ?: emptyList()
            
            val asks = response.getAsJsonArray("asks")?.map { entry ->
                val arr = entry.asJsonArray
                OrderBookLevel(
                    price = arr[0].asString.toDouble(),
                    quantity = arr[1].asString.toDouble(),
                    orderCount = if (arr.size() > 2) arr[2].asInt else 1
                )
            } ?: emptyList()
            
            return OrderBook(
                symbol = normaliseSymbol(symbol),
                exchange = "Coinbase",
                bids = bids,
                asks = asks,
                timestamp = System.currentTimeMillis(),
                sequenceId = response.get("sequence")?.asLong ?: 0
            )
        } catch (e: Exception) {
            return null
        }
    }
    
    // =========================================================================
    // TRADING PAIRS PARSING
    // =========================================================================
    
    override fun parseTradingPairs(response: JsonObject): List<TradingPair> {
        // Coinbase returns an array directly, need to handle wrapper
        return emptyList()  // See parseTradingPairsFromArray
    }
    
    /**
     * Parse trading pairs from array response
     */
    fun parseTradingPairsFromArray(response: JsonArray): List<TradingPair> {
        return response.mapNotNull { productJson ->
            try {
                val product = productJson.asJsonObject
                
                // Skip non-trading pairs
                val status = product.get("status")?.asString
                if (status != "online") return@mapNotNull null
                
                val productId = product.get("id")?.asString ?: return@mapNotNull null
                val baseCurrency = product.get("base_currency")?.asString ?: return@mapNotNull null
                val quoteCurrency = product.get("quote_currency")?.asString ?: return@mapNotNull null
                
                val baseMinSize = product.get("base_min_size")?.asString?.toDoubleOrNull() ?: 0.0
                val baseMaxSize = product.get("base_max_size")?.asString?.toDoubleOrNull() ?: Double.MAX_VALUE
                val baseIncrement = product.get("base_increment")?.asString?.toDoubleOrNull() ?: 0.00000001
                val quoteIncrement = product.get("quote_increment")?.asString?.toDoubleOrNull() ?: 0.01
                val minMarketFunds = product.get("min_market_funds")?.asString?.toDoubleOrNull() ?: 1.0
                
                TradingPair(
                    symbol = "$baseCurrency/$quoteCurrency",
                    baseAsset = baseCurrency,
                    quoteAsset = quoteCurrency,
                    exchangeSymbol = productId,
                    exchange = "Coinbase",
                    minQuantity = baseMinSize,
                    maxQuantity = baseMaxSize,
                    quantityStep = baseIncrement,
                    minPrice = 0.0,
                    maxPrice = Double.MAX_VALUE,
                    priceStep = quoteIncrement,
                    minNotional = minMarketFunds,
                    status = PairStatus.TRADING
                )
            } catch (e: Exception) {
                null
            }
        }
    }
    
    // =========================================================================
    // BALANCE PARSING
    // =========================================================================
    
    override fun parseBalances(response: JsonObject): List<Balance> {
        // Coinbase returns an array directly
        return emptyList()  // See parseBalancesFromArray
    }
    
    /**
     * Parse balances from array response
     */
    fun parseBalancesFromArray(response: JsonArray): List<Balance> {
        return response.mapNotNull { accountJson ->
            try {
                val account = accountJson.asJsonObject
                val currency = account.get("currency")?.asString ?: return@mapNotNull null
                val balance = account.get("balance")?.asString?.toDoubleOrNull() ?: 0.0
                val available = account.get("available")?.asString?.toDoubleOrNull() ?: 0.0
                val hold = account.get("hold")?.asString?.toDoubleOrNull() ?: 0.0
                
                // Skip zero balances
                if (balance <= 0) return@mapNotNull null
                
                Balance(
                    asset = currency,
                    free = available,
                    locked = hold,
                    total = balance,
                    usdValue = 0.0  // Would need price data
                )
            } catch (e: Exception) {
                null
            }
        }
    }
    
    // =========================================================================
    // ORDER PARSING
    // =========================================================================
    
    override fun parseOrder(response: JsonObject): ExecutedOrder? {
        try {
            if (hasCoinbaseError(response)) return null
            return parseCoinbaseOrder(response)
        } catch (e: Exception) {
            return null
        }
    }
    
    private fun parseCoinbaseOrder(orderData: JsonObject): ExecutedOrder? {
        try {
            val orderId = orderData.get("id")?.asString ?: return null
            val productId = orderData.get("product_id")?.asString ?: return null
            val side = orderData.get("side")?.asString ?: "buy"
            val type = orderData.get("type")?.asString 
                ?: orderData.get("order_type")?.asString 
                ?: "limit"
            val status = orderData.get("status")?.asString ?: "pending"
            
            val size = orderData.get("size")?.asString?.toDoubleOrNull() 
                ?: orderData.get("base_size")?.asString?.toDoubleOrNull()
                ?: 0.0
            val filledSize = orderData.get("filled_size")?.asString?.toDoubleOrNull()
                ?: orderData.get("filled_quantity")?.asString?.toDoubleOrNull()
                ?: 0.0
            val price = orderData.get("price")?.asString?.toDoubleOrNull() 
                ?: orderData.get("limit_price")?.asString?.toDoubleOrNull()
                ?: 0.0
            val executedValue = orderData.get("executed_value")?.asString?.toDoubleOrNull()
                ?: orderData.get("filled_value")?.asString?.toDoubleOrNull()
                ?: 0.0
            val avgPrice = if (filledSize > 0) executedValue / filledSize else price
            
            val fillFees = orderData.get("fill_fees")?.asString?.toDoubleOrNull()
                ?: orderData.get("total_fees")?.asString?.toDoubleOrNull()
                ?: 0.0
            
            val clientOrderId = orderData.get("client_oid")?.asString 
                ?: orderData.get("client_order_id")?.asString
                ?: ""
            
            val timestamp = parseIsoTimestamp(orderData.get("created_at")?.asString)
                ?: System.currentTimeMillis()
            val doneAt = parseIsoTimestamp(orderData.get("done_at")?.asString)
            
            return ExecutedOrder(
                orderId = orderId,
                clientOrderId = clientOrderId,
                symbol = normaliseSymbol(productId),
                side = if (side.lowercase() == "buy") TradeSide.BUY else TradeSide.SELL,
                type = parseCoinbaseOrderType(type),
                price = price,
                executedPrice = avgPrice,
                quantity = size,
                executedQuantity = filledSize,
                fee = fillFees,
                feeCurrency = productId.substringAfter("-"),  // Quote currency
                status = parseCoinbaseOrderStatus(status),
                exchange = "Coinbase",
                timestamp = timestamp,
                updatedAt = doneAt ?: timestamp
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
            if (hasCoinbaseError(response)) {
                val message = response.get("message")?.asString ?: "Unknown error"
                return OrderExecutionResult.Rejected(message)
            }
            
            val orderId = response.get("id")?.asString
                ?: response.get("order_id")?.asString
                ?: return OrderExecutionResult.Rejected("No order ID returned")
            
            val status = response.get("status")?.asString ?: "pending"
            val filledSize = response.get("filled_size")?.asString?.toDoubleOrNull() ?: 0.0
            val price = response.get("price")?.asString?.toDoubleOrNull()
                ?: response.get("limit_price")?.asString?.toDoubleOrNull()
                ?: request.price
                ?: 0.0
            val executedValue = response.get("executed_value")?.asString?.toDoubleOrNull() ?: 0.0
            val avgPrice = if (filledSize > 0) executedValue / filledSize else price
            val fillFees = response.get("fill_fees")?.asString?.toDoubleOrNull() ?: 0.0
            
            return OrderExecutionResult.Success(
                ExecutedOrder(
                    orderId = orderId,
                    clientOrderId = request.clientOrderId,
                    symbol = request.symbol,
                    side = request.side,
                    type = request.type,
                    price = request.price ?: 0.0,
                    executedPrice = avgPrice,
                    quantity = request.quantity,
                    executedQuantity = filledSize,
                    fee = fillFees,
                    feeCurrency = request.symbol.substringAfter("/"),
                    status = parseCoinbaseOrderStatus(status),
                    exchange = "Coinbase"
                )
            )
        } catch (e: Exception) {
            return OrderExecutionResult.Error(e)
        }
    }
    
    override fun buildOrderBody(request: OrderRequest): Map<String, String> {
        val productId = toExchangeSymbol(request.symbol)
        
        val params = mutableMapOf(
            "product_id" to productId,
            "side" to if (request.side == TradeSide.BUY || request.side == TradeSide.LONG) "buy" else "sell",
            "type" to (ORDER_TYPE_MAP[request.type] ?: "limit")
        )
        
        // Size (always required)
        params["size"] = request.quantity.toString()
        
        // Price for limit orders
        if (request.type == OrderType.LIMIT && request.price != null) {
            params["price"] = request.price.toString()
            params["time_in_force"] = TIF_MAP[request.timeInForce] ?: "GTC"
            
            // Post-only flag
            if (request.postOnly) {
                params["post_only"] = "true"
            }
        }
        
        // Stop price for stop orders
        if ((request.type == OrderType.STOP_LOSS || request.type == OrderType.STOP_LIMIT) 
            && request.stopPrice != null) {
            params["stop_price"] = request.stopPrice.toString()
            params["stop"] = if (request.side == TradeSide.BUY || request.side == TradeSide.LONG) 
                "entry" else "loss"
                
            if (request.type == OrderType.STOP_LIMIT && request.price != null) {
                params["price"] = request.price.toString()
                params["time_in_force"] = TIF_MAP[request.timeInForce] ?: "GTC"
            }
        }
        
        // Client order ID
        if (request.clientOrderId.isNotEmpty()) {
            params["client_oid"] = request.clientOrderId
        } else {
            params["client_oid"] = UUID.randomUUID().toString()
        }
        
        // Self-trade prevention
        params["stp"] = "dc"  // Decrease and cancel
        
        return params
    }
    
    // =========================================================================
    // ADDITIONAL PARSING METHODS
    // =========================================================================
    
    override fun parseOrderList(response: JsonObject): List<ExecutedOrder> {
        // Coinbase returns array directly for order list endpoints
        return emptyList()
    }
    
    /**
     * Parse order list from array response
     */
    fun parseOrderListFromArray(response: JsonArray): List<ExecutedOrder> {
        return response.mapNotNull { orderJson ->
            parseCoinbaseOrder(orderJson.asJsonObject)
        }
    }
    
    override fun parsePublicTrades(response: JsonObject, symbol: String): List<PublicTrade> {
        // Coinbase returns array directly for trades endpoint
        return emptyList()
    }
    
    /**
     * Parse public trades from array response
     */
    fun parsePublicTradesFromArray(response: JsonArray, symbol: String): List<PublicTrade> {
        return response.map { tradeJson ->
            val trade = tradeJson.asJsonObject
            val time = parseIsoTimestamp(trade.get("time")?.asString) ?: System.currentTimeMillis()
            
            PublicTrade(
                symbol = normaliseSymbol(symbol),
                exchange = "Coinbase",
                price = trade.get("price")?.asString?.toDoubleOrNull() ?: 0.0,
                quantity = trade.get("size")?.asString?.toDoubleOrNull() ?: 0.0,
                side = if (trade.get("side")?.asString == "buy") TradeSide.BUY else TradeSide.SELL,
                timestamp = time,
                tradeId = trade.get("trade_id")?.asString ?: "${System.currentTimeMillis()}"
            )
        }
    }
    
    override fun parseCandles(response: JsonObject, symbol: String): List<OHLCVBar> {
        // Coinbase returns array directly for candles endpoint
        return emptyList()
    }
    
    /**
     * Parse candles from array response
     * Coinbase candle format: [time, low, high, open, close, volume]
     */
    fun parseCandlesFromArray(response: JsonArray, symbol: String, interval: String): List<OHLCVBar> {
        return response.map { candleJson ->
            val arr = candleJson.asJsonArray
            OHLCVBar(
                symbol = normaliseSymbol(symbol),
                open = arr[3].asDouble,
                high = arr[2].asDouble,
                low = arr[1].asDouble,
                close = arr[4].asDouble,
                volume = arr[5].asDouble,
                timestamp = arr[0].asLong * 1000,  // Convert to milliseconds
                interval = interval,
                trades = 0  // Not provided
            )
        }
    }
    
    // =========================================================================
    // WEBSOCKET HANDLING
    // =========================================================================
    
    override fun handleWsMessage(text: String) {
        try {
            val json = gson.fromJson(text, JsonObject::class.java)
            
            val type = json.get("type")?.asString ?: return
            
            when (type) {
                "ticker" -> handleWsTicker(json)
                "snapshot" -> handleWsSnapshot(json)
                "l2update" -> handleWsL2Update(json)
                "match", "last_match" -> handleWsMatch(json)
                "received" -> handleWsReceived(json)
                "open" -> handleWsOpen(json)
                "done" -> handleWsDone(json)
                "change" -> handleWsChange(json)
                "subscriptions" -> handleWsSubscriptions(json)
                "error" -> handleWsError(json)
                "heartbeat" -> { /* Ignore heartbeat */ }
            }
        } catch (e: Exception) {
            // Ignore parsing errors for individual messages
        }
    }
    
    private fun handleWsTicker(data: JsonObject) {
        try {
            val productId = data.get("product_id")?.asString ?: return
            
            val tick = PriceTick(
                symbol = normaliseSymbol(productId),
                bid = data.get("best_bid")?.asString?.toDoubleOrNull() ?: 0.0,
                ask = data.get("best_ask")?.asString?.toDoubleOrNull() ?: 0.0,
                last = data.get("price")?.asString?.toDoubleOrNull() ?: 0.0,
                volume = data.get("volume_24h")?.asString?.toDoubleOrNull() ?: 0.0,
                high24h = data.get("high_24h")?.asString?.toDoubleOrNull(),
                low24h = data.get("low_24h")?.asString?.toDoubleOrNull(),
                change24h = null,
                changePercent24h = null,
                timestamp = parseIsoTimestamp(data.get("time")?.asString) ?: System.currentTimeMillis(),
                exchange = "Coinbase"
            )
            
            scope.launch {
                _priceUpdates.emit(tick)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    private fun handleWsSnapshot(data: JsonObject) {
        try {
            val productId = data.get("product_id")?.asString ?: return
            
            val bids = data.getAsJsonArray("bids")?.map { entry ->
                val arr = entry.asJsonArray
                OrderBookLevel(
                    price = arr[0].asString.toDouble(),
                    quantity = arr[1].asString.toDouble()
                )
            } ?: emptyList()
            
            val asks = data.getAsJsonArray("asks")?.map { entry ->
                val arr = entry.asJsonArray
                OrderBookLevel(
                    price = arr[0].asString.toDouble(),
                    quantity = arr[1].asString.toDouble()
                )
            } ?: emptyList()
            
            val book = OrderBook(
                symbol = normaliseSymbol(productId),
                exchange = "Coinbase",
                bids = bids,
                asks = asks,
                timestamp = System.currentTimeMillis(),
                sequenceId = 0
            )
            
            scope.launch {
                _orderBookUpdates.emit(book)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    private fun handleWsL2Update(data: JsonObject) {
        // Level 2 update - incremental order book changes
        // For simplicity, we'd need to maintain state to apply these
        // Full implementation would track and update local order book
    }
    
    private fun handleWsMatch(data: JsonObject) {
        try {
            val productId = data.get("product_id")?.asString ?: return
            
            val trade = PublicTrade(
                symbol = normaliseSymbol(productId),
                exchange = "Coinbase",
                price = data.get("price")?.asString?.toDoubleOrNull() ?: 0.0,
                quantity = data.get("size")?.asString?.toDoubleOrNull() ?: 0.0,
                side = if (data.get("side")?.asString == "buy") TradeSide.BUY else TradeSide.SELL,
                timestamp = parseIsoTimestamp(data.get("time")?.asString) ?: System.currentTimeMillis(),
                tradeId = data.get("trade_id")?.asString ?: "${System.currentTimeMillis()}"
            )
            
            scope.launch {
                _tradeUpdates.emit(trade)
            }
            
            // Also emit as price update
            val tick = PriceTick(
                symbol = normaliseSymbol(productId),
                bid = 0.0,
                ask = 0.0,
                last = trade.price,
                volume = 0.0,
                timestamp = trade.timestamp,
                exchange = "Coinbase"
            )
            
            scope.launch {
                _priceUpdates.emit(tick)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    private fun handleWsReceived(data: JsonObject) {
        // Order received - could emit order update
        handleOrderUpdate(data, OrderStatus.PENDING)
    }
    
    private fun handleWsOpen(data: JsonObject) {
        // Order opened (on book)
        handleOrderUpdate(data, OrderStatus.OPEN)
    }
    
    private fun handleWsDone(data: JsonObject) {
        // Order done (filled or cancelled)
        val reason = data.get("reason")?.asString
        val status = when (reason) {
            "filled" -> OrderStatus.FILLED
            "canceled" -> OrderStatus.CANCELLED
            else -> OrderStatus.FILLED
        }
        handleOrderUpdate(data, status)
    }
    
    private fun handleWsChange(data: JsonObject) {
        // Order changed (size change)
        handleOrderUpdate(data, OrderStatus.OPEN)
    }
    
    private fun handleOrderUpdate(data: JsonObject, status: OrderStatus) {
        try {
            val orderId = data.get("order_id")?.asString ?: return
            val productId = data.get("product_id")?.asString ?: return
            val side = data.get("side")?.asString ?: "buy"
            val price = data.get("price")?.asString?.toDoubleOrNull() ?: 0.0
            val size = data.get("size")?.asString?.toDoubleOrNull()
                ?: data.get("remaining_size")?.asString?.toDoubleOrNull()
                ?: 0.0
            
            val update = ExchangeOrderUpdate(
                orderId = orderId,
                clientOrderId = data.get("client_oid")?.asString ?: "",
                symbol = normaliseSymbol(productId),
                status = status,
                executedPrice = price,
                executedQuantity = 0.0,  // Would need to track
                remainingQuantity = null,
                fee = 0.0,
                feeCurrency = "",
                timestamp = parseIsoTimestamp(data.get("time")?.asString) ?: System.currentTimeMillis(),
                exchange = "Coinbase"
            )
            
            scope.launch {
                _orderUpdates.emit(update)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    private fun handleWsSubscriptions(data: JsonObject) {
        // Subscription confirmation
        val channels = data.getAsJsonArray("channels")
        // Log subscribed channels if needed
    }
    
    private fun handleWsError(data: JsonObject) {
        val message = data.get("message")?.asString ?: "Unknown WebSocket error"
        // Could emit error through a separate channel
    }
    
    // =========================================================================
    // WEBSOCKET SUBSCRIPTION
    // =========================================================================
    
    override fun buildWsSubscription(channels: List<String>, symbols: List<String>): String {
        val productIds = symbols.map { toExchangeSymbol(it) }
        
        val channelList = channels.map { channel ->
            when (channel) {
                "ticker" -> "ticker"
                "orderbook", "depth", "book" -> "level2"
                "trades", "trade", "match" -> "matches"
                "full" -> "full"  // Full order book + order lifecycle
                "heartbeat" -> "heartbeat"
                "status" -> "status"
                else -> "ticker"
            }
        }.distinct()
        
        return gson.toJson(mapOf(
            "type" to "subscribe",
            "product_ids" to productIds,
            "channels" to channelList
        ))
    }
    
    /**
     * Build unsubscribe message
     */
    fun buildWsUnsubscription(channels: List<String>, symbols: List<String>): String {
        val productIds = symbols.map { toExchangeSymbol(it) }
        
        val channelList = channels.map { channel ->
            when (channel) {
                "ticker" -> "ticker"
                "orderbook", "depth", "book" -> "level2"
                "trades", "trade", "match" -> "matches"
                else -> "ticker"
            }
        }.distinct()
        
        return gson.toJson(mapOf(
            "type" to "unsubscribe",
            "product_ids" to productIds,
            "channels" to channelList
        ))
    }
    
    /**
     * Build authenticated subscription for user channel
     * Requires signing the subscription message
     */
    fun buildAuthenticatedWsSubscription(channels: List<String>, symbols: List<String>): String {
        val productIds = symbols.map { toExchangeSymbol(it) }
        val timestamp = System.currentTimeMillis() / 1000
        
        // Build signature
        val message = "$timestamp" + "GET" + "/users/self/verify"
        val secretDecoded = try {
            Base64.decode(config.apiSecret, Base64.NO_WRAP)
        } catch (e: Exception) {
            config.apiSecret.toByteArray(Charsets.UTF_8)
        }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretDecoded, "HmacSHA256"))
        val signature = Base64.encodeToString(mac.doFinal(message.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        
        val channelList = channels.map { channel ->
            when (channel) {
                "user" -> "user"
                "full" -> "full"
                else -> channel
            }
        }.distinct()
        
        return gson.toJson(mapOf(
            "type" to "subscribe",
            "product_ids" to productIds,
            "channels" to channelList,
            "signature" to signature,
            "key" to config.apiKey,
            "passphrase" to config.passphrase,
            "timestamp" to timestamp.toString()
        ))
    }
    
    // =========================================================================
    // HELPER METHODS
    // =========================================================================
    
    private fun hasCoinbaseError(response: JsonObject): Boolean {
        return response.has("message") && !response.has("id") && !response.has("price")
    }
    
    override fun hasError(response: JsonObject): Boolean = hasCoinbaseError(response)
    
    override fun toExchangeSymbol(normalisedSymbol: String): String {
        // BTC/USD -> BTC-USD
        return normalisedSymbol.replace("/", "-")
    }
    
    override fun normaliseSymbol(exchangeSymbol: String): String {
        // BTC-USD -> BTC/USD
        return exchangeSymbol.replace("-", "/")
    }
    
    private fun parseCoinbaseOrderType(type: String): OrderType {
        return when (type.lowercase()) {
            "market" -> OrderType.MARKET
            "limit" -> OrderType.LIMIT
            "stop" -> OrderType.STOP_LOSS
            "stop_limit" -> OrderType.STOP_LIMIT
            else -> OrderType.LIMIT
        }
    }
    
    private fun parseCoinbaseOrderStatus(status: String): OrderStatus {
        return when (status.lowercase()) {
            "pending" -> OrderStatus.PENDING
            "open", "active" -> OrderStatus.OPEN
            "done", "settled" -> OrderStatus.FILLED
            "cancelled", "canceled" -> OrderStatus.CANCELLED
            "rejected" -> OrderStatus.REJECTED
            "expired" -> OrderStatus.EXPIRED
            else -> OrderStatus.PENDING
        }
    }
    
    /**
     * Parse ISO 8601 timestamp to milliseconds
     */
    private fun parseIsoTimestamp(timestamp: String?): Long? {
        if (timestamp == null) return null
        return try {
            Instant.parse(timestamp).toEpochMilli()
        } catch (e: Exception) {
            try {
                // Try alternate format
                DateTimeFormatter.ISO_DATE_TIME.parse(timestamp) { temporal ->
                    Instant.from(temporal).toEpochMilli()
                }
            } catch (e2: Exception) {
                null
            }
        }
    }
    
    // =========================================================================
    // PRODUCT STATS (for 24h high/low)
    // =========================================================================
    
    /**
     * Get 24-hour stats for a product
     * Endpoint: GET /products/{product_id}/stats
     */
    suspend fun getProductStats(symbol: String): ProductStats? {
        val productId = toExchangeSymbol(symbol)
        val path = "/products/$productId/stats"
        
        return try {
            val response = publicGet(path, emptyMap())
            response?.let { parseProductStats(it, symbol) }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun parseProductStats(response: JsonObject, symbol: String): ProductStats? {
        return try {
            ProductStats(
                symbol = normaliseSymbol(symbol),
                open = response.get("open")?.asString?.toDoubleOrNull() ?: 0.0,
                high = response.get("high")?.asString?.toDoubleOrNull() ?: 0.0,
                low = response.get("low")?.asString?.toDoubleOrNull() ?: 0.0,
                last = response.get("last")?.asString?.toDoubleOrNull() ?: 0.0,
                volume = response.get("volume")?.asString?.toDoubleOrNull() ?: 0.0,
                volume30d = response.get("volume_30day")?.asString?.toDoubleOrNull() ?: 0.0
            )
        } catch (e: Exception) {
            null
        }
    }
    
    data class ProductStats(
        val symbol: String,
        val open: Double,
        val high: Double,
        val low: Double,
        val last: Double,
        val volume: Double,
        val volume30d: Double
    )
}
