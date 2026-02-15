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
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.*

/**
 * KRAKEN CONNECTOR - Complete Implementation
 * 
 * Full-featured Kraken exchange connector with:
 * - REST API for trading and account management
 * - WebSocket for real-time market data
 * - Proper Kraken authentication (HMAC-SHA512)
 * - Symbol normalisation (XXBTZUSD ↔ BTC/USD)
 * - Rate limiting compliance
 * 
 * Based on Kraken API v0:
 * - REST: https://api.kraken.com/0/
 * - WebSocket: wss://ws.kraken.com (public) / wss://ws-auth.kraken.com (private)
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

class KrakenConnector(
    config: ExchangeConfig,
    secureHttpClient: HybridSecureHttpClient? = null,
    credentialVault: PQCCredentialVault? = null
) : BaseCEXConnector(config, secureHttpClient, credentialVault) {
    
    companion object {
        // Kraken uses non-standard symbol formats
        // Major cryptos have X prefix, fiat has Z prefix
        private val SYMBOL_MAP = mapOf(
            "BTC/USD" to "XXBTZUSD",
            "BTC/EUR" to "XXBTZEUR",
            "BTC/GBP" to "XXBTZGBP",
            "BTC/AUD" to "XXBTZAUD",
            "ETH/USD" to "XETHZUSD",
            "ETH/EUR" to "XETHZEUR",
            "ETH/BTC" to "XETHXXBT",
            "XRP/USD" to "XXRPZUSD",
            "XRP/EUR" to "XXRPZEUR",
            "LTC/USD" to "XLTCZUSD",
            "LTC/EUR" to "XLTCZEUR",
            "SOL/USD" to "SOLUSD",
            "SOL/EUR" to "SOLEUR",
            "ADA/USD" to "ADAUSD",
            "ADA/EUR" to "ADAEUR",
            "AVAX/USD" to "AVAXUSD",
            "LINK/USD" to "LINKUSD",
            "DOT/USD" to "DOTUSD",
            "MATIC/USD" to "MATICUSD",
            "DOGE/USD" to "XDGUSD",
            "ATOM/USD" to "ATOMUSD",
            "UNI/USD" to "UNIUSD",
            "AAVE/USD" to "AAVEUSD"
        )
        
        // Reverse mapping for normalisation
        private val REVERSE_SYMBOL_MAP = SYMBOL_MAP.entries.associate { (k, v) -> v to k }
        
        // Kraken interval codes
        private val INTERVAL_MAP = mapOf(
            "1m" to 1,
            "5m" to 5,
            "15m" to 15,
            "30m" to 30,
            "1h" to 60,
            "4h" to 240,
            "1d" to 1440,
            "1w" to 10080
        )
    }
    
    // =========================================================================
    // EXCHANGE CAPABILITIES
    // =========================================================================
    
    override val capabilities = ExchangeCapabilities(
        supportsSpotTrading = true,
        supportsFutures = true,      // Via Kraken Futures (separate API)
        supportsMargin = true,
        supportsOptions = false,
        supportsLending = false,
        supportsStaking = true,      // Native staking available
        supportsWebSocket = true,
        supportsOrderbook = true,
        supportsMarketOrders = true,
        supportsLimitOrders = true,
        supportsStopOrders = true,
        supportsPostOnly = true,
        supportsCancelAll = true,
        maxOrdersPerSecond = 5,      // Conservative for private endpoints
        minOrderValue = 5.0,         // $5 minimum
        tradingFeeMaker = 0.0016,    // 0.16% maker
        tradingFeeTaker = 0.0026,    // 0.26% taker (volume-dependent)
        withdrawalEnabled = false,   // Non-custodial - we don't withdraw
        networks = listOf(
            BlockchainNetwork.BITCOIN,
            BlockchainNetwork.ETHEREUM,
            BlockchainNetwork.SOLANA,
            BlockchainNetwork.POLYGON,
            BlockchainNetwork.ARBITRUM,
            BlockchainNetwork.OPTIMISM
        )
    )
    
    // =========================================================================
    // API ENDPOINTS
    // =========================================================================
    
    override val endpoints = ExchangeEndpoints(
        ticker = "/0/public/Ticker",
        orderBook = "/0/public/Depth",
        trades = "/0/public/Trades",
        candles = "/0/public/OHLC",
        pairs = "/0/public/AssetPairs",
        balances = "/0/private/Balance",
        placeOrder = "/0/private/AddOrder",
        cancelOrder = "/0/private/CancelOrder",
        getOrder = "/0/private/QueryOrders",
        openOrders = "/0/private/OpenOrders",
        orderHistory = "/0/private/ClosedOrders",
        wsUrl = "wss://ws.kraken.com"
    )
    
    // WebSocket state
    private var wsSubscribedSymbols = mutableSetOf<String>()
    private var wsToken: String? = null  // For authenticated WS (optional)
    
    // =========================================================================
    // AUTHENTICATION - Kraken's HMAC-SHA512 Signature
    // =========================================================================
    
    override fun signRequest(
        method: String,
        path: String,
        params: Map<String, String>,
        body: String?,
        timestamp: Long
    ): Map<String, String> {
        // Kraken uses nonce-based signing
        val nonce = timestamp * 1000  // Microseconds for extra uniqueness
        
        // Build POST data with nonce
        val postData = buildString {
            append("nonce=$nonce")
            params.forEach { (key, value) ->
                append("&$key=$value")
            }
        }
        
        // Step 1: SHA256(nonce + postData)
        val sha256 = MessageDigest.getInstance("SHA-256")
        val noncePost = nonce.toString() + postData
        val sha256Hash = sha256.digest(noncePost.toByteArray(Charsets.UTF_8))
        
        // Step 2: HMAC-SHA512(path + SHA256hash) using API secret
        val message = path.toByteArray(Charsets.UTF_8) + sha256Hash
        val secretDecoded = Base64.decode(config.apiSecret, Base64.DEFAULT)
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(secretDecoded, "HmacSHA512"))
        val signature = mac.doFinal(message)
        
        return mapOf(
            "API-Key" to config.apiKey,
            "API-Sign" to Base64.encodeToString(signature, Base64.NO_WRAP),
            "Content-Type" to "application/x-www-form-urlencoded"
        )
    }
    
    // =========================================================================
    // TICKER PARSING
    // =========================================================================
    
    override fun parseTicker(response: JsonObject, symbol: String): PriceTick? {
        try {
            // Check for errors
            if (hasKrakenError(response)) return null
            
            val result = response.getAsJsonObject("result") ?: return null
            
            // Kraken returns ticker data keyed by exchange symbol
            val exchangeSymbol = toExchangeSymbol(symbol)
            val tickerData = result.getAsJsonObject(exchangeSymbol) 
                ?: result.entrySet().firstOrNull()?.value?.asJsonObject
                ?: return null
            
            // Kraken ticker format:
            // a = ask [price, whole lot volume, lot volume]
            // b = bid [price, whole lot volume, lot volume]
            // c = last trade [price, lot volume]
            // v = volume [today, last 24 hours]
            // h = high [today, last 24 hours]
            // l = low [today, last 24 hours]
            
            val askArray = tickerData.getAsJsonArray("a")
            val bidArray = tickerData.getAsJsonArray("b")
            val lastArray = tickerData.getAsJsonArray("c")
            val volumeArray = tickerData.getAsJsonArray("v")
            
            return PriceTick(
                symbol = symbol,
                bid = bidArray[0].asString.toDouble(),
                ask = askArray[0].asString.toDouble(),
                last = lastArray[0].asString.toDouble(),
                volume = volumeArray[1].asString.toDouble(),  // 24h volume
                timestamp = System.currentTimeMillis(),
                exchange = "Kraken"
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
            if (hasKrakenError(response)) return null
            
            val result = response.getAsJsonObject("result") ?: return null
            val exchangeSymbol = toExchangeSymbol(symbol)
            val bookData = result.getAsJsonObject(exchangeSymbol)
                ?: result.entrySet().firstOrNull()?.value?.asJsonObject
                ?: return null
            
            // Kraken order book format:
            // asks = [[price, volume, timestamp], ...]
            // bids = [[price, volume, timestamp], ...]
            
            val asks = bookData.getAsJsonArray("asks")?.map { entry ->
                val arr = entry.asJsonArray
                OrderBookLevel(
                    price = arr[0].asString.toDouble(),
                    quantity = arr[1].asString.toDouble(),
                    orderCount = 1
                )
            } ?: emptyList()
            
            val bids = bookData.getAsJsonArray("bids")?.map { entry ->
                val arr = entry.asJsonArray
                OrderBookLevel(
                    price = arr[0].asString.toDouble(),
                    quantity = arr[1].asString.toDouble(),
                    orderCount = 1
                )
            } ?: emptyList()
            
            return OrderBook(
                symbol = symbol,
                exchange = "Kraken",
                bids = bids,
                asks = asks,
                timestamp = System.currentTimeMillis(),
                sequenceId = 0
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
            if (hasKrakenError(response)) return emptyList()
            
            val result = response.getAsJsonObject("result") ?: return emptyList()
            
            return result.entrySet().mapNotNull { (exchangeSymbol, pairJson) ->
                try {
                    val pair = pairJson.asJsonObject
                    
                    // Skip dark pools and .d pairs
                    if (exchangeSymbol.endsWith(".d")) return@mapNotNull null
                    
                    val wsName = pair.get("wsname")?.asString ?: return@mapNotNull null
                    val base = pair.get("base")?.asString ?: return@mapNotNull null
                    val quote = pair.get("quote")?.asString ?: return@mapNotNull null
                    
                    // Normalise symbol: "XBT/USD" -> "BTC/USD"
                    val normalisedSymbol = normaliseKrakenPair(wsName)
                    
                    // Parse lot size and price precision
                    val lotDecimals = pair.get("lot_decimals")?.asInt ?: 8
                    val pairDecimals = pair.get("pair_decimals")?.asInt ?: 5
                    val orderMin = pair.get("ordermin")?.asString?.toDoubleOrNull() ?: 0.0001
                    val costMin = pair.get("costmin")?.asString?.toDoubleOrNull() ?: 0.5
                    
                    TradingPair(
                        symbol = normalisedSymbol,
                        baseAsset = normaliseAsset(base),
                        quoteAsset = normaliseAsset(quote),
                        exchangeSymbol = exchangeSymbol,
                        exchange = "Kraken",
                        minQuantity = orderMin,
                        maxQuantity = 100000.0,  // Kraken doesn't specify max
                        quantityStep = 1.0 / Math.pow(10.0, lotDecimals.toDouble()),
                        minPrice = 0.0,
                        maxPrice = Double.MAX_VALUE,
                        priceStep = 1.0 / Math.pow(10.0, pairDecimals.toDouble()),
                        minNotional = costMin,
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
            if (hasKrakenError(response)) return emptyList()
            
            val result = response.getAsJsonObject("result") ?: return emptyList()
            
            return result.entrySet().mapNotNull { (asset, balanceValue) ->
                try {
                    val amount = balanceValue.asString.toDouble()
                    if (amount <= 0) return@mapNotNull null
                    
                    Balance(
                        asset = normaliseAsset(asset),
                        free = amount,
                        locked = 0.0,  // Would need TradeBalance endpoint for locked
                        total = amount,
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
            if (hasKrakenError(response)) return null
            
            val result = response.getAsJsonObject("result") ?: return null
            
            // QueryOrders returns {orderId: orderData}
            val entry = result.entrySet().firstOrNull() ?: return null
            val orderId = entry.key
            val orderData = entry.value.asJsonObject
            
            return parseKrakenOrder(orderId, orderData)
        } catch (e: Exception) {
            return null
        }
    }
    
    private fun parseKrakenOrder(orderId: String, orderData: JsonObject): ExecutedOrder? {
        try {
            val descr = orderData.getAsJsonObject("descr") ?: return null
            
            val pair = descr.get("pair")?.asString ?: ""
            val type = descr.get("type")?.asString ?: "buy"
            val orderType = descr.get("ordertype")?.asString ?: "limit"
            val priceStr = descr.get("price")?.asString ?: "0"
            
            val volume = orderData.get("vol")?.asString?.toDoubleOrNull() ?: 0.0
            val volumeExec = orderData.get("vol_exec")?.asString?.toDoubleOrNull() ?: 0.0
            val cost = orderData.get("cost")?.asString?.toDoubleOrNull() ?: 0.0
            val fee = orderData.get("fee")?.asString?.toDoubleOrNull() ?: 0.0
            val statusStr = orderData.get("status")?.asString ?: "open"
            val userRef = orderData.get("userref")?.asString ?: ""
            
            val executedPrice = if (volumeExec > 0) cost / volumeExec else priceStr.toDoubleOrNull() ?: 0.0
            
            return ExecutedOrder(
                orderId = orderId,
                clientOrderId = userRef,
                symbol = normaliseKrakenPair(pair),
                side = if (type == "buy") TradeSide.BUY else TradeSide.SELL,
                type = parseKrakenOrderType(orderType),
                requestedPrice = priceStr.toDoubleOrNull() ?: 0.0,
                executedPrice = executedPrice,
                requestedQuantity = volume,
                executedQuantity = volumeExec,
                fee = fee,
                feeCurrency = "USD",
                status = parseKrakenOrderStatus(statusStr),
                exchange = "Kraken"
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
            if (hasKrakenError(response)) {
                val errors = response.getAsJsonArray("error")
                val errorMsg = errors?.joinToString(", ") { it.asString } ?: "Unknown error"
                return OrderExecutionResult.Rejected(errorMsg)
            }
            
            val result = response.getAsJsonObject("result") ?: 
                return OrderExecutionResult.Rejected("No result in response")
            
            val txids = result.getAsJsonArray("txid")
            val orderId = txids?.firstOrNull()?.asString ?: ""
            
            if (orderId.isEmpty()) {
                return OrderExecutionResult.Rejected("No order ID returned")
            }
            
            return OrderExecutionResult.Success(
                ExecutedOrder(
                    orderId = orderId,
                    clientOrderId = request.clientOrderId,
                    symbol = request.symbol,
                    side = request.side,
                    type = request.type,
                    requestedPrice = request.price ?: 0.0,
                    executedPrice = request.price ?: 0.0,  // Updated by fills
                    requestedQuantity = request.quantity,
                    executedQuantity = 0.0,  // Updated by fills
                    fee = 0.0,
                    feeCurrency = "USD",
                    status = OrderStatus.OPEN,
                    exchange = "Kraken"
                )
            )
        } catch (e: Exception) {
            return OrderExecutionResult.Error(e)
        }
    }
    
    override fun buildOrderBody(request: OrderRequest): Map<String, String> {
        val exchangeSymbol = toExchangeSymbol(request.symbol)
        
        val params = mutableMapOf(
            "pair" to exchangeSymbol,
            "type" to if (request.side == TradeSide.BUY || request.side == TradeSide.LONG) "buy" else "sell",
            "ordertype" to mapOrderTypeToKraken(request.type),
            "volume" to request.quantity.toString()
        )
        
        // Price for limit orders
        if (request.type == OrderType.LIMIT && request.price != null) {
            params["price"] = request.price.toString()
        }
        
        // Stop price for stop orders
        if ((request.type == OrderType.STOP_LOSS || request.type == OrderType.STOP_LIMIT) 
            && request.stopPrice != null) {
            params["price"] = request.stopPrice.toString()
            if (request.type == OrderType.STOP_LIMIT && request.price != null) {
                params["price2"] = request.price.toString()  // Limit price
            }
        }
        
        // Time in force
        when (request.timeInForce) {
            TimeInForce.IOC -> params["timeinforce"] = "IOC"
            TimeInForce.FOK -> params["timeinforce"] = "FOK"
            TimeInForce.GTD -> {
                // Kraken uses expiretm for GTD
                request.expireTime?.let { params["expiretm"] = (it / 1000).toString() }
            }
            else -> { } // GTC is default
        }
        
        // Post-only flag
        if (request.postOnly) {
            params["oflags"] = "post"
        }
        
        // Client order ID (userref in Kraken - must be 32-bit signed int)
        if (request.clientOrderId.isNotEmpty()) {
            params["userref"] = (request.clientOrderId.hashCode() and 0x7FFFFFFF).toString()
        }
        
        return params
    }
    
    // =========================================================================
    // ADDITIONAL PARSING METHODS
    // =========================================================================
    
    override fun parseOrderList(response: JsonObject): List<ExecutedOrder> {
        try {
            if (hasKrakenError(response)) return emptyList()
            
            val result = response.getAsJsonObject("result") ?: return emptyList()
            
            // OpenOrders format: { "open": { orderId: orderData, ... } }
            // ClosedOrders format: { "closed": { orderId: orderData, ... } }
            val orders = result.getAsJsonObject("open") 
                ?: result.getAsJsonObject("closed")
                ?: result
            
            return orders.entrySet().mapNotNull { (orderId, orderJson) ->
                parseKrakenOrder(orderId, orderJson.asJsonObject)
            }
        } catch (e: Exception) {
            return emptyList()
        }
    }
    
    override fun parsePublicTrades(response: JsonObject, symbol: String): List<PublicTrade> {
        try {
            if (hasKrakenError(response)) return emptyList()
            
            val result = response.getAsJsonObject("result") ?: return emptyList()
            val exchangeSymbol = toExchangeSymbol(symbol)
            val trades = result.getAsJsonArray(exchangeSymbol) ?: return emptyList()
            
            // Kraken trade format: [price, volume, time, side, ordertype, misc]
            return trades.mapIndexed { index, tradeJson ->
                val arr = tradeJson.asJsonArray
                PublicTrade(
                    symbol = symbol,
                    exchange = "Kraken",
                    price = arr[0].asString.toDouble(),
                    quantity = arr[1].asString.toDouble(),
                    side = if (arr[3].asString == "b") TradeSide.BUY else TradeSide.SELL,
                    timestamp = (arr[2].asDouble * 1000).toLong(),
                    tradeId = "${symbol}_${index}"
                )
            }
        } catch (e: Exception) {
            return emptyList()
        }
    }
    
    override fun parseCandles(response: JsonObject, symbol: String): List<OHLCVBar> {
        try {
            if (hasKrakenError(response)) return emptyList()
            
            val result = response.getAsJsonObject("result") ?: return emptyList()
            val exchangeSymbol = toExchangeSymbol(symbol)
            val candles = result.getAsJsonArray(exchangeSymbol) ?: return emptyList()
            
            // Kraken OHLC format: [time, open, high, low, close, vwap, volume, count]
            return candles.map { candleJson ->
                val arr = candleJson.asJsonArray
                OHLCVBar(
                    symbol = symbol,
                    open = arr[1].asString.toDouble(),
                    high = arr[2].asString.toDouble(),
                    low = arr[3].asString.toDouble(),
                    close = arr[4].asString.toDouble(),
                    volume = arr[6].asString.toDouble(),
                    timestamp = arr[0].asLong * 1000,
                    interval = "1m"  // Would need to track from request
                )
            }
        } catch (e: Exception) {
            return emptyList()
        }
    }
    
    // =========================================================================
    // WEBSOCKET HANDLING
    // =========================================================================
    
    override fun handleWsMessage(text: String) {
        try {
            val json = gson.fromJson(text, JsonObject::class.java)
            
            // Kraken WS messages can be:
            // 1. Object with "event" field (system messages)
            // 2. Array with channel data [channelID, data, channelName, pair]
            
            when {
                json.has("event") -> handleWsEvent(json)
                else -> {
                    // Try parsing as array (market data)
                    val arr = gson.fromJson(text, JsonArray::class.java)
                    if (arr != null && arr.size() >= 4) {
                        handleWsChannelData(arr)
                    }
                }
            }
        } catch (e: Exception) {
            // Try parsing as array directly
            try {
                val arr = gson.fromJson(text, JsonArray::class.java)
                if (arr != null && arr.size() >= 4) {
                    handleWsChannelData(arr)
                }
            } catch (e2: Exception) {
                // Ignore unparseable messages (like heartbeats)
            }
        }
    }
    
    private fun handleWsEvent(json: JsonObject) {
        val event = json.get("event")?.asString ?: return
        
        when (event) {
            "systemStatus" -> {
                val status = json.get("status")?.asString
                if (status == "online") {
                    // System is ready
                }
            }
            "subscriptionStatus" -> {
                val subStatus = json.get("status")?.asString
                val pair = json.get("pair")?.asString
                if (subStatus == "subscribed" && pair != null) {
                    wsSubscribedSymbols.add(normaliseKrakenPair(pair))
                }
            }
            "heartbeat" -> {
                // Kraken sends heartbeats - no action needed
            }
            "error" -> {
                val errorMessage = json.get("errorMessage")?.asString
                // Log error but don't crash
            }
        }
    }
    
    private fun handleWsChannelData(arr: JsonArray) {
        try {
            // Format: [channelID, data, channelName, pair]
            // Or for ticker: [channelID, tickerData, "ticker", "XBT/USD"]
            
            val channelName = arr[arr.size() - 2].asString
            val pair = arr[arr.size() - 1].asString
            val normalisedSymbol = normaliseKrakenPair(pair)
            
            when (channelName) {
                "ticker" -> handleWsTicker(arr[1].asJsonObject, normalisedSymbol)
                "book-10", "book-25", "book-100", "book-500", "book-1000" -> 
                    handleWsOrderBook(arr[1].asJsonObject, normalisedSymbol)
                "trade" -> handleWsTrades(arr[1].asJsonArray, normalisedSymbol)
                "ohlc", "ohlc-1", "ohlc-5", "ohlc-15", "ohlc-60" -> 
                    handleWsCandle(arr[1].asJsonArray, normalisedSymbol)
                "spread" -> handleWsSpread(arr[1].asJsonArray, normalisedSymbol)
            }
        } catch (e: Exception) {
            // Ignore parsing errors for individual messages
        }
    }
    
    private fun handleWsTicker(data: JsonObject, symbol: String) {
        try {
            // Kraken WS ticker format similar to REST
            val ask = data.getAsJsonArray("a")
            val bid = data.getAsJsonArray("b")
            val last = data.getAsJsonArray("c")
            val volume = data.getAsJsonArray("v")
            
            val tick = PriceTick(
                symbol = symbol,
                bid = bid[0].asString.toDouble(),
                ask = ask[0].asString.toDouble(),
                last = last[0].asString.toDouble(),
                volume = volume[1].asString.toDouble(),
                timestamp = System.currentTimeMillis(),
                exchange = "Kraken"
            )
            
            // Emit to flow
            scope.launch {
                _priceUpdates.emit(tick)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    private fun handleWsOrderBook(data: JsonObject, symbol: String) {
        try {
            // Parse bids and asks from snapshot or update
            val asks = (data.getAsJsonArray("as") ?: data.getAsJsonArray("a"))?.map { entry ->
                val arr = entry.asJsonArray
                OrderBookLevel(
                    price = arr[0].asString.toDouble(),
                    quantity = arr[1].asString.toDouble()
                )
            } ?: emptyList()
            
            val bids = (data.getAsJsonArray("bs") ?: data.getAsJsonArray("b"))?.map { entry ->
                val arr = entry.asJsonArray
                OrderBookLevel(
                    price = arr[0].asString.toDouble(),
                    quantity = arr[1].asString.toDouble()
                )
            } ?: emptyList()
            
            val book = OrderBook(
                symbol = symbol,
                exchange = "Kraken",
                bids = bids,
                asks = asks,
                timestamp = System.currentTimeMillis()
            )
            
            scope.launch {
                _orderBookUpdates.emit(book)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    private fun handleWsTrades(trades: JsonArray, symbol: String) {
        try {
            // Kraken WS trade format: [[price, volume, time, side, orderType, misc], ...]
            trades.forEach { tradeJson ->
                val arr = tradeJson.asJsonArray
                val trade = PublicTrade(
                    symbol = symbol,
                    exchange = "Kraken",
                    price = arr[0].asString.toDouble(),
                    quantity = arr[1].asString.toDouble(),
                    side = if (arr[3].asString == "b") TradeSide.BUY else TradeSide.SELL,
                    timestamp = (arr[2].asString.toDouble() * 1000).toLong(),
                    tradeId = "${System.currentTimeMillis()}"
                )
                
                scope.launch {
                    _tradeUpdates.emit(trade)
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    private fun handleWsCandle(data: JsonArray, symbol: String) {
        // OHLC update: [time, etime, open, high, low, close, vwap, volume, count]
        // We could emit candle updates if needed
    }
    
    private fun handleWsSpread(data: JsonArray, symbol: String) {
        // Spread update: [bid, ask, timestamp, bidVolume, askVolume]
        // Can be used for lightweight price updates
        try {
            val tick = PriceTick(
                symbol = symbol,
                bid = data[0].asString.toDouble(),
                ask = data[1].asString.toDouble(),
                last = (data[0].asString.toDouble() + data[1].asString.toDouble()) / 2,
                volume = 0.0,
                timestamp = (data[2].asString.toDouble() * 1000).toLong(),
                exchange = "Kraken"
            )
            
            scope.launch {
                _priceUpdates.emit(tick)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    // =========================================================================
    // WEBSOCKET SUBSCRIPTION
    // =========================================================================
    
    override fun buildWsSubscription(channels: List<String>, symbols: List<String>): String {
        // Kraken WS subscription format
        val channelName = when {
            "ticker" in channels -> "ticker"
            "orderbook" in channels -> "book"
            "trades" in channels -> "trade"
            "ohlc" in channels -> "ohlc"
            "spread" in channels -> "spread"
            else -> "ticker"
        }
        
        // Convert symbols to Kraken WS format (XBT/USD not XXBTZUSD)
        val wsPairs = symbols.map { symbol ->
            // If it's already in WS format, use it
            if (symbol.contains("/")) {
                symbol.replace("BTC", "XBT")
            } else {
                // Convert from exchange format
                normaliseKrakenPair(symbol).replace("BTC", "XBT")
            }
        }
        
        return gson.toJson(mapOf(
            "event" to "subscribe",
            "pair" to wsPairs,
            "subscription" to mapOf(
                "name" to channelName,
                "depth" to if (channelName == "book") 10 else null
            ).filterValues { it != null }
        ))
    }
    
    // =========================================================================
    // HELPER METHODS
    // =========================================================================
    
    private fun hasKrakenError(response: JsonObject): Boolean {
        val error = response.getAsJsonArray("error")
        return error != null && error.size() > 0
    }
    
    override fun hasError(response: JsonObject): Boolean = hasKrakenError(response)
    
    private fun normaliseKrakenPair(pair: String): String {
        // Try reverse map first
        REVERSE_SYMBOL_MAP[pair]?.let { return it }
        
        // Handle WS format (XBT/USD -> BTC/USD)
        if (pair.contains("/")) {
            return pair
                .replace("XBT", "BTC")
                .replace("XXBT", "BTC")
                .replace("XETH", "ETH")
                .replace("ZUSD", "USD")
                .replace("ZEUR", "EUR")
                .replace("ZGBP", "GBP")
                .replace("ZAUD", "AUD")
        }
        
        // Try to parse exchange symbol (XXBTZUSD -> BTC/USD)
        val normalised = pair
            .replace("XXBT", "BTC")
            .replace("XETH", "ETH")
            .replace("XDG", "DOGE")
            .replace("XLTC", "LTC")
            .replace("XXRP", "XRP")
            .replace("ZUSD", "/USD")
            .replace("ZEUR", "/EUR")
            .replace("ZGBP", "/GBP")
            .replace("ZAUD", "/AUD")
        
        // If no slash, try adding one
        if (!normalised.contains("/")) {
            // Guess: last 3 chars are quote currency
            if (normalised.length > 3) {
                val base = normalised.dropLast(3)
                val quote = normalised.takeLast(3)
                return "$base/$quote"
            }
        }
        
        return normalised
    }
    
    private fun normaliseAsset(asset: String): String {
        return asset
            .replace("XXBT", "BTC")
            .replace("XBT", "BTC")
            .replace("XETH", "ETH")
            .replace("XLTC", "LTC")
            .replace("XXRP", "XRP")
            .replace("XDG", "DOGE")
            .replace("ZUSD", "USD")
            .replace("ZEUR", "EUR")
            .replace("ZGBP", "GBP")
            .replace("ZAUD", "AUD")
    }
    
    override fun toExchangeSymbol(normalisedSymbol: String): String {
        return SYMBOL_MAP[normalisedSymbol] ?: normalisedSymbol.replace("/", "")
    }
    
    override fun normaliseSymbol(exchangeSymbol: String): String {
        return REVERSE_SYMBOL_MAP[exchangeSymbol] ?: normaliseKrakenPair(exchangeSymbol)
    }
    
    private fun mapOrderTypeToKraken(type: OrderType): String {
        return when (type) {
            OrderType.MARKET -> "market"
            OrderType.LIMIT -> "limit"
            OrderType.STOP_LOSS -> "stop-loss"
            OrderType.STOP_LIMIT -> "stop-loss-limit"
            OrderType.TRAILING_STOP -> "trailing-stop"
        }
    }
    
    private fun parseKrakenOrderType(type: String): OrderType {
        return when (type.lowercase()) {
            "market" -> OrderType.MARKET
            "limit" -> OrderType.LIMIT
            "stop-loss" -> OrderType.STOP_LOSS
            "stop-loss-limit" -> OrderType.STOP_LIMIT
            "trailing-stop" -> OrderType.TRAILING_STOP
            else -> OrderType.LIMIT
        }
    }
    
    private fun parseKrakenOrderStatus(status: String): OrderStatus {
        return when (status.lowercase()) {
            "pending" -> OrderStatus.PENDING
            "open" -> OrderStatus.OPEN
            "closed" -> OrderStatus.FILLED
            "canceled", "cancelled" -> OrderStatus.CANCELLED
            "expired" -> OrderStatus.EXPIRED
            else -> OrderStatus.PENDING
        }
    }
}
