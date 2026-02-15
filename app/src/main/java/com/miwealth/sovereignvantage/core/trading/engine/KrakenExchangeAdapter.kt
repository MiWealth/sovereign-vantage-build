package com.miwealth.sovereignvantage.core.trading.engine

import com.miwealth.sovereignvantage.core.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

/**
 * Kraken Exchange Adapter - Production-ready Kraken API integration
 * 
 * Implements the ExchangeAdapter interface for Kraken exchange.
 * Handles authentication, rate limiting, and order management.
 * 
 * © 2025 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO: Arthur Iain McManus (1966-2025)
 */

class KrakenExchangeAdapter(
    private val apiKey: String,
    private val apiSecret: String,
    private val client: OkHttpClient = com.miwealth.sovereignvantage.core.network.SharedHttpClient.baseClient
) : ExchangeAdapter {
    
    companion object {
        private const val BASE_URL = "https://api.kraken.com"
        private const val API_VERSION = "0"
        
        // Rate limiting: 15 calls per 3 seconds for private endpoints
        private const val RATE_LIMIT_CALLS = 15
        private const val RATE_LIMIT_WINDOW_MS = 3000L
    }
    
    override val exchangeName: String = "Kraken"
    
    private val gson = Gson()
    private val callTimestamps = mutableListOf<Long>()
    private var rateLimitedUntil: Long = 0
    
    // Symbol mapping: Our format -> Kraken format
    private val symbolMap = mapOf(
        "BTC/USD" to "XXBTZUSD",
        "ETH/USD" to "XETHZUSD",
        "SOL/USD" to "SOLUSD",
        "XRP/USD" to "XXRPZUSD",
        "ADA/USD" to "ADAUSD",
        "AVAX/USD" to "AVAXUSD",
        "LINK/USD" to "LINKUSD",
        "DOT/USD" to "DOTUSD",
        "MATIC/USD" to "MATICUSD",
        "DOGE/USD" to "XDGUSD",
        "BTC/EUR" to "XXBTZEUR",
        "ETH/EUR" to "XETHZEUR"
    )
    
    override fun isRateLimited(): Boolean {
        val now = System.currentTimeMillis()
        
        // Check if we're in a rate limit cooldown
        if (now < rateLimitedUntil) {
            return true
        }
        
        // Clean old timestamps
        callTimestamps.removeAll { it < now - RATE_LIMIT_WINDOW_MS }
        
        return callTimestamps.size >= RATE_LIMIT_CALLS
    }
    
    private fun recordApiCall() {
        callTimestamps.add(System.currentTimeMillis())
    }
    
    override suspend fun placeOrder(request: OrderRequest): OrderExecutionResult {
        return withContext(Dispatchers.IO) {
            try {
                val krakenSymbol = symbolMap[request.symbol] ?: request.symbol.replace("/", "")
                
                val params = mutableMapOf(
                    "pair" to krakenSymbol,
                    "type" to if (request.side == TradeSide.BUY || request.side == TradeSide.LONG) "buy" else "sell",
                    "ordertype" to mapOrderType(request.type),
                    "volume" to request.quantity.toString()
                )
                
                // Add price for limit orders
                if (request.type == OrderType.LIMIT && request.price != null) {
                    params["price"] = request.price.toString()
                }
                
                // Add stop price for stop orders
                if (request.type == OrderType.STOP_LOSS && request.stopPrice != null) {
                    params["price"] = request.stopPrice.toString()
                }
                
                // Add leverage if specified
                // params["leverage"] = "2:1"  // Example
                
                // Add time in force
                when (request.timeInForce) {
                    TimeInForce.IOC -> params["timeinforce"] = "IOC"
                    TimeInForce.FOK -> params["timeinforce"] = "FOK"
                    else -> {} // GTC is default
                }
                
                // Post-only flag
                if (request.postOnly) {
                    params["oflags"] = "post"
                }
                
                // Add client order ID
                params["userref"] = request.clientOrderId.hashCode().toString()
                
                val response = makePrivateRequest("/0/private/AddOrder", params)
                
                if (response.has("error") && response.getAsJsonArray("error").size() > 0) {
                    val errors = response.getAsJsonArray("error")
                    val errorMsg = errors.joinToString(", ") { it.asString }
                    return@withContext OrderExecutionResult.Rejected(errorMsg)
                }
                
                val result = response.getAsJsonObject("result")
                val txids = result.getAsJsonArray("txid")
                val orderId = if (txids.size() > 0) txids[0].asString else ""
                
                OrderExecutionResult.Success(
                    ExecutedOrder(
                        orderId = orderId,
                        clientOrderId = request.clientOrderId,
                        symbol = request.symbol,
                        side = request.side,
                        type = request.type,
                        requestedPrice = request.price ?: 0.0,
                        executedPrice = request.price ?: 0.0, // Will be updated by getOrderStatus
                        requestedQuantity = request.quantity,
                        executedQuantity = request.quantity, // Will be updated
                        fee = 0.0,
                        feeCurrency = "USD",
                        status = OrderStatus.OPEN,
                        exchange = exchangeName
                    )
                )
                
            } catch (e: Exception) {
                OrderExecutionResult.Error(e)
            }
        }
    }
    
    override suspend fun cancelOrder(orderId: String, symbol: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val params = mapOf("txid" to orderId)
                val response = makePrivateRequest("/0/private/CancelOrder", params)
                
                !response.has("error") || response.getAsJsonArray("error").size() == 0
            } catch (e: Exception) {
                false
            }
        }
    }
    
    override suspend fun modifyOrder(
        orderId: String,
        symbol: String,
        newPrice: Double?,
        newQuantity: Double?
    ): OrderExecutionResult {
        // Kraken doesn't support order modification - cancel and replace
        return withContext(Dispatchers.IO) {
            try {
                // Get current order
                val currentOrder = getOrderStatus(orderId, symbol)
                    ?: return@withContext OrderExecutionResult.Rejected("Order not found")
                
                // Cancel existing order
                if (!cancelOrder(orderId, symbol)) {
                    return@withContext OrderExecutionResult.Rejected("Failed to cancel existing order")
                }
                
                // Place new order
                val request = OrderRequest(
                    symbol = symbol,
                    side = currentOrder.side,
                    type = currentOrder.type,
                    quantity = newQuantity ?: currentOrder.requestedQuantity,
                    price = newPrice ?: currentOrder.requestedPrice
                )
                
                placeOrder(request)
                
            } catch (e: Exception) {
                OrderExecutionResult.Error(e)
            }
        }
    }
    
    override suspend fun getOrderStatus(orderId: String, symbol: String): ExecutedOrder? {
        return withContext(Dispatchers.IO) {
            try {
                val params = mapOf("txid" to orderId)
                val response = makePrivateRequest("/0/private/QueryOrders", params)
                
                if (response.has("error") && response.getAsJsonArray("error").size() > 0) {
                    return@withContext null
                }
                
                val result = response.getAsJsonObject("result")
                val orderData = result.getAsJsonObject(orderId) ?: return@withContext null
                
                val descr = orderData.getAsJsonObject("descr")
                val status = orderData.get("status").asString
                
                ExecutedOrder(
                    orderId = orderId,
                    clientOrderId = orderData.get("userref")?.asString ?: "",
                    symbol = symbol,
                    side = if (descr.get("type").asString == "buy") TradeSide.BUY else TradeSide.SELL,
                    type = parseOrderType(descr.get("ordertype").asString),
                    requestedPrice = descr.get("price")?.asDouble ?: 0.0,
                    executedPrice = orderData.get("price")?.asDouble ?: 0.0,
                    requestedQuantity = orderData.get("vol").asDouble,
                    executedQuantity = orderData.get("vol_exec").asDouble,
                    fee = orderData.get("fee")?.asDouble ?: 0.0,
                    feeCurrency = "USD",
                    status = parseOrderStatus(status),
                    timestamp = (orderData.get("opentm")?.asDouble?.times(1000))?.toLong() ?: System.currentTimeMillis(),
                    exchange = exchangeName
                )
                
            } catch (e: Exception) {
                null
            }
        }
    }
    
    override suspend fun getOpenOrders(symbol: String?): List<ExecutedOrder> {
        return withContext(Dispatchers.IO) {
            try {
                val response = makePrivateRequest("/0/private/OpenOrders", emptyMap())
                
                if (response.has("error") && response.getAsJsonArray("error").size() > 0) {
                    return@withContext emptyList()
                }
                
                val result = response.getAsJsonObject("result")
                val open = result.getAsJsonObject("open") ?: return@withContext emptyList()
                
                open.entrySet().mapNotNull { (orderId, orderJson) ->
                    try {
                        val orderData = orderJson.asJsonObject
                        val descr = orderData.getAsJsonObject("descr")
                        val pair = descr.get("pair").asString
                        
                        // Filter by symbol if provided
                        if (symbol != null) {
                            val krakenSymbol = symbolMap[symbol] ?: symbol.replace("/", "")
                            if (pair != krakenSymbol) return@mapNotNull null
                        }
                        
                        ExecutedOrder(
                            orderId = orderId,
                            clientOrderId = orderData.get("userref")?.asString ?: "",
                            symbol = reverseSymbolMap(pair),
                            side = if (descr.get("type").asString == "buy") TradeSide.BUY else TradeSide.SELL,
                            type = parseOrderType(descr.get("ordertype").asString),
                            requestedPrice = descr.get("price")?.asDouble ?: 0.0,
                            executedPrice = 0.0,
                            requestedQuantity = orderData.get("vol").asDouble,
                            executedQuantity = orderData.get("vol_exec").asDouble,
                            fee = 0.0,
                            feeCurrency = "USD",
                            status = OrderStatus.OPEN,
                            exchange = exchangeName
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    /**
     * Get account balance
     */
    suspend fun getBalance(): Map<String, Double> {
        return withContext(Dispatchers.IO) {
            try {
                val response = makePrivateRequest("/0/private/Balance", emptyMap())
                
                if (response.has("error") && response.getAsJsonArray("error").size() > 0) {
                    return@withContext emptyMap()
                }
                
                val result = response.getAsJsonObject("result")
                result.entrySet().associate { (asset, balance) ->
                    asset to balance.asDouble
                }
                
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }
    
    /**
     * Get ticker data
     */
    suspend fun getTicker(symbol: String): TickerData? {
        return withContext(Dispatchers.IO) {
            try {
                val krakenSymbol = symbolMap[symbol] ?: symbol.replace("/", "")
                val url = "$BASE_URL/0/public/Ticker?pair=$krakenSymbol"
                
                val request = Request.Builder().url(url).get().build()
                val response = client.newCall(request).execute()
                val json = gson.fromJson(response.body?.string(), JsonObject::class.java)
                
                if (json.has("error") && json.getAsJsonArray("error").size() > 0) {
                    return@withContext null
                }
                
                val result = json.getAsJsonObject("result")
                val tickerData = result.entrySet().firstOrNull()?.value?.asJsonObject
                    ?: return@withContext null
                
                TickerData(
                    symbol = symbol,
                    bid = tickerData.getAsJsonArray("b")[0].asDouble,
                    ask = tickerData.getAsJsonArray("a")[0].asDouble,
                    last = tickerData.getAsJsonArray("c")[0].asDouble,
                    volume = tickerData.getAsJsonArray("v")[1].asDouble,
                    high = tickerData.getAsJsonArray("h")[1].asDouble,
                    low = tickerData.getAsJsonArray("l")[1].asDouble
                )
                
            } catch (e: Exception) {
                null
            }
        }
    }
    
    // ========================================================================
    // PRIVATE HELPERS
    // ========================================================================
    
    private suspend fun makePrivateRequest(path: String, params: Map<String, String>): JsonObject {
        recordApiCall()
        
        val nonce = System.currentTimeMillis()
        val postData = params.toMutableMap()
        postData["nonce"] = nonce.toString()
        
        val postBody = postData.entries.joinToString("&") { "${it.key}=${it.value}" }
        
        // Create signature
        val signature = createSignature(path, nonce, postBody)
        
        val request = Request.Builder()
            .url("$BASE_URL$path")
            .post(postBody.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .addHeader("API-Key", apiKey)
            .addHeader("API-Sign", signature)
            .build()
        
        val response = client.newCall(request).execute()
        return gson.fromJson(response.body?.string(), JsonObject::class.java)
    }
    
    private fun createSignature(path: String, nonce: Long, postData: String): String {
        val sha256 = MessageDigest.getInstance("SHA-256")
        val noncePost = nonce.toString() + postData
        val sha256Hash = sha256.digest(noncePost.toByteArray())
        
        val pathBytes = path.toByteArray()
        val message = pathBytes + sha256Hash
        
        val secretDecoded = Base64.decode(apiSecret, Base64.DEFAULT)
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(secretDecoded, "HmacSHA512"))
        
        val signature = mac.doFinal(message)
        return Base64.encodeToString(signature, Base64.NO_WRAP)
    }
    
    private fun mapOrderType(type: OrderType): String {
        return when (type) {
            OrderType.MARKET -> "market"
            OrderType.LIMIT -> "limit"
            OrderType.STOP_LOSS -> "stop-loss"
            OrderType.STOP_LIMIT -> "stop-loss-limit"
            OrderType.TRAILING_STOP -> "trailing-stop"
        }
    }
    
    private fun parseOrderType(type: String): OrderType {
        return when (type.lowercase()) {
            "market" -> OrderType.MARKET
            "limit" -> OrderType.LIMIT
            "stop-loss" -> OrderType.STOP_LOSS
            "stop-loss-limit" -> OrderType.STOP_LIMIT
            "trailing-stop" -> OrderType.TRAILING_STOP
            else -> OrderType.LIMIT
        }
    }
    
    private fun parseOrderStatus(status: String): OrderStatus {
        return when (status.lowercase()) {
            "pending" -> OrderStatus.PENDING
            "open" -> OrderStatus.OPEN
            "closed" -> OrderStatus.FILLED
            "canceled", "cancelled" -> OrderStatus.CANCELLED
            "expired" -> OrderStatus.EXPIRED
            else -> OrderStatus.PENDING
        }
    }
    
    private fun reverseSymbolMap(krakenSymbol: String): String {
        return symbolMap.entries.find { it.value == krakenSymbol }?.key ?: krakenSymbol
    }
}

data class TickerData(
    val symbol: String,
    val bid: Double,
    val ask: Double,
    val last: Double,
    val volume: Double,
    val high: Double,
    val low: Double
)
