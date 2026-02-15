package com.miwealth.sovereignvantage.core.trading.engine

import com.miwealth.sovereignvantage.core.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import android.util.Base64
import java.time.Instant

/**
 * Coinbase Exchange Adapter - Production-ready Coinbase Advanced Trade API
 * 
 * Implements the ExchangeAdapter interface for Coinbase exchange.
 * Uses the new Advanced Trade API (not deprecated Pro API).
 * 
 * © 2025 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO: Arthur Iain McManus (1966-2025)
 */

class CoinbaseExchangeAdapter(
    private val apiKey: String,
    private val apiSecret: String,
    private val useSandbox: Boolean = false,
    private val client: OkHttpClient = com.miwealth.sovereignvantage.core.network.SharedHttpClient.baseClient
) : ExchangeAdapter {
    
    companion object {
        private const val PRODUCTION_URL = "https://api.coinbase.com"
        private const val SANDBOX_URL = "https://api-public.sandbox.exchange.coinbase.com"
        
        // Rate limiting: 10 requests per second
        private const val RATE_LIMIT_CALLS = 10
        private const val RATE_LIMIT_WINDOW_MS = 1000L
    }
    
    private val baseUrl: String = if (useSandbox) SANDBOX_URL else PRODUCTION_URL
    override val exchangeName: String = if (useSandbox) "Coinbase (Sandbox)" else "Coinbase"
    
    private val gson = Gson()
    private val callTimestamps = mutableListOf<Long>()
    
    override fun isRateLimited(): Boolean {
        val now = System.currentTimeMillis()
        callTimestamps.removeAll { it < now - RATE_LIMIT_WINDOW_MS }
        return callTimestamps.size >= RATE_LIMIT_CALLS
    }
    
    private fun recordApiCall() {
        callTimestamps.add(System.currentTimeMillis())
    }
    
    override suspend fun placeOrder(request: OrderRequest): OrderExecutionResult {
        return withContext(Dispatchers.IO) {
            try {
                val productId = formatProductId(request.symbol)
                
                val orderConfig = JsonObject().apply {
                    when (request.type) {
                        OrderType.MARKET -> {
                            if (request.side == TradeSide.BUY || request.side == TradeSide.LONG) {
                                addProperty("quote_size", (request.quantity * (request.price ?: 0.0)).toString())
                            } else {
                                addProperty("base_size", request.quantity.toString())
                            }
                        }
                        OrderType.LIMIT -> {
                            addProperty("base_size", request.quantity.toString())
                            addProperty("limit_price", request.price.toString())
                            if (request.postOnly) {
                                addProperty("post_only", true)
                            }
                        }
                        OrderType.STOP_LOSS -> {
                            addProperty("base_size", request.quantity.toString())
                            addProperty("stop_price", request.stopPrice.toString())
                            addProperty("stop_direction", 
                                if (request.side == TradeSide.SELL) "STOP_DIRECTION_STOP_DOWN" 
                                else "STOP_DIRECTION_STOP_UP"
                            )
                        }
                        else -> {
                            addProperty("base_size", request.quantity.toString())
                        }
                    }
                }
                
                val body = JsonObject().apply {
                    addProperty("client_order_id", request.clientOrderId)
                    addProperty("product_id", productId)
                    addProperty("side", if (request.side == TradeSide.BUY || request.side == TradeSide.LONG) "BUY" else "SELL")
                    add("order_configuration", JsonObject().apply {
                        when (request.type) {
                            OrderType.MARKET -> add("market_market_ioc", orderConfig)
                            OrderType.LIMIT -> add("limit_limit_gtc", orderConfig)
                            OrderType.STOP_LOSS -> add("stop_limit_stop_limit_gtc", orderConfig)
                            else -> add("market_market_ioc", orderConfig)
                        }
                    })
                }
                
                val response = makeAuthenticatedRequest(
                    method = "POST",
                    path = "/api/v3/brokerage/orders",
                    body = body.toString()
                )
                
                if (response.has("error_response")) {
                    val error = response.getAsJsonObject("error_response")
                    return@withContext OrderExecutionResult.Rejected(
                        error.get("message")?.asString ?: "Unknown error",
                        error.get("error")?.asString
                    )
                }
                
                val successResponse = response.getAsJsonObject("success_response")
                val orderId = successResponse?.get("order_id")?.asString ?: ""
                
                OrderExecutionResult.Success(
                    ExecutedOrder(
                        orderId = orderId,
                        clientOrderId = request.clientOrderId,
                        symbol = request.symbol,
                        side = request.side,
                        type = request.type,
                        requestedPrice = request.price ?: 0.0,
                        executedPrice = 0.0, // Updated by getOrderStatus
                        requestedQuantity = request.quantity,
                        executedQuantity = 0.0,
                        fee = 0.0,
                        feeCurrency = "USD",
                        status = OrderStatus.PENDING,
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
                val body = JsonObject().apply {
                    add("order_ids", JsonArray().apply { add(orderId) })
                }
                
                val response = makeAuthenticatedRequest(
                    method = "POST",
                    path = "/api/v3/brokerage/orders/batch_cancel",
                    body = body.toString()
                )
                
                val results = response.getAsJsonArray("results")
                results?.firstOrNull()?.asJsonObject?.get("success")?.asBoolean ?: false
                
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
        return withContext(Dispatchers.IO) {
            try {
                val body = JsonObject().apply {
                    addProperty("order_id", orderId)
                    newPrice?.let { addProperty("price", it.toString()) }
                    newQuantity?.let { addProperty("size", it.toString()) }
                }
                
                val response = makeAuthenticatedRequest(
                    method = "POST",
                    path = "/api/v3/brokerage/orders/edit",
                    body = body.toString()
                )
                
                if (response.has("errors")) {
                    return@withContext OrderExecutionResult.Rejected("Failed to modify order")
                }
                
                // Return success - actual details from getOrderStatus
                OrderExecutionResult.Success(
                    ExecutedOrder(
                        orderId = orderId,
                        clientOrderId = "",
                        symbol = symbol,
                        side = TradeSide.BUY,
                        type = OrderType.LIMIT,
                        requestedPrice = newPrice ?: 0.0,
                        executedPrice = 0.0,
                        requestedQuantity = newQuantity ?: 0.0,
                        executedQuantity = 0.0,
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
    
    override suspend fun getOrderStatus(orderId: String, symbol: String): ExecutedOrder? {
        return withContext(Dispatchers.IO) {
            try {
                val response = makeAuthenticatedRequest(
                    method = "GET",
                    path = "/api/v3/brokerage/orders/historical/$orderId"
                )
                
                val order = response.getAsJsonObject("order") ?: return@withContext null
                
                parseOrder(order)
                
            } catch (e: Exception) {
                null
            }
        }
    }
    
    override suspend fun getOpenOrders(symbol: String?): List<ExecutedOrder> {
        return withContext(Dispatchers.IO) {
            try {
                var path = "/api/v3/brokerage/orders/historical?order_status=OPEN"
                symbol?.let { path += "&product_id=${formatProductId(it)}" }
                
                val response = makeAuthenticatedRequest(method = "GET", path = path)
                
                val orders = response.getAsJsonArray("orders") ?: return@withContext emptyList()
                
                orders.mapNotNull { orderJson ->
                    try {
                        parseOrder(orderJson.asJsonObject)
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
     * Get account balances
     */
    suspend fun getAccounts(): Map<String, AccountBalance> {
        return withContext(Dispatchers.IO) {
            try {
                val response = makeAuthenticatedRequest(
                    method = "GET",
                    path = "/api/v3/brokerage/accounts"
                )
                
                val accounts = response.getAsJsonArray("accounts") ?: return@withContext emptyMap()
                
                accounts.associate { acc ->
                    val account = acc.asJsonObject
                    val currency = account.get("currency")?.asString ?: ""
                    val balance = account.getAsJsonObject("available_balance")
                    
                    currency to AccountBalance(
                        currency = currency,
                        available = balance?.get("value")?.asDouble ?: 0.0,
                        hold = account.getAsJsonObject("hold")?.get("value")?.asDouble ?: 0.0
                    )
                }
                
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }
    
    /**
     * Get product ticker
     */
    suspend fun getTicker(symbol: String): TickerData? {
        return withContext(Dispatchers.IO) {
            try {
                val productId = formatProductId(symbol)
                val response = makeAuthenticatedRequest(
                    method = "GET",
                    path = "/api/v3/brokerage/products/$productId"
                )
                
                TickerData(
                    symbol = symbol,
                    bid = response.get("quote_increment")?.asDouble ?: 0.0,
                    ask = response.get("quote_increment")?.asDouble ?: 0.0,
                    last = response.get("price")?.asDouble ?: 0.0,
                    volume = response.get("volume_24h")?.asDouble ?: 0.0,
                    high = response.get("high_24h")?.asDouble ?: 0.0,
                    low = response.get("low_24h")?.asDouble ?: 0.0
                )
                
            } catch (e: Exception) {
                null
            }
        }
    }
    
    // ========================================================================
    // PRIVATE HELPERS
    // ========================================================================
    
    private suspend fun makeAuthenticatedRequest(
        method: String,
        path: String,
        body: String? = null
    ): JsonObject {
        recordApiCall()
        
        val timestamp = Instant.now().epochSecond.toString()
        val message = timestamp + method + path + (body ?: "")
        val signature = sign(message)
        
        val requestBuilder = Request.Builder()
            .url("$baseUrl$path")
            .addHeader("CB-ACCESS-KEY", apiKey)
            .addHeader("CB-ACCESS-SIGN", signature)
            .addHeader("CB-ACCESS-TIMESTAMP", timestamp)
            .addHeader("Content-Type", "application/json")
        
        when (method) {
            "GET" -> requestBuilder.get()
            "POST" -> requestBuilder.post(
                (body ?: "").toRequestBody("application/json".toMediaType())
            )
            "DELETE" -> requestBuilder.delete()
        }
        
        val response = client.newCall(requestBuilder.build()).execute()
        return gson.fromJson(response.body?.string() ?: "{}", JsonObject::class.java)
    }
    
    private fun sign(message: String): String {
        val secretDecoded = Base64.decode(apiSecret, Base64.DEFAULT)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretDecoded, "HmacSHA256"))
        val signature = mac.doFinal(message.toByteArray())
        return Base64.encodeToString(signature, Base64.NO_WRAP)
    }
    
    private fun formatProductId(symbol: String): String {
        // Convert "BTC/USD" to "BTC-USD"
        return symbol.replace("/", "-")
    }
    
    private fun parseSymbol(productId: String): String {
        // Convert "BTC-USD" to "BTC/USD"
        return productId.replace("-", "/")
    }
    
    private fun parseOrder(order: JsonObject): ExecutedOrder {
        val side = when (order.get("side")?.asString) {
            "BUY" -> TradeSide.BUY
            else -> TradeSide.SELL
        }
        
        val status = when (order.get("status")?.asString) {
            "PENDING" -> OrderStatus.PENDING
            "OPEN" -> OrderStatus.OPEN
            "FILLED" -> OrderStatus.FILLED
            "CANCELLED" -> OrderStatus.CANCELLED
            "EXPIRED" -> OrderStatus.EXPIRED
            else -> OrderStatus.PENDING
        }
        
        val orderConfig = order.getAsJsonObject("order_configuration")
        val isMarket = orderConfig?.has("market_market_ioc") ?: false
        val type = if (isMarket) OrderType.MARKET else OrderType.LIMIT
        
        return ExecutedOrder(
            orderId = order.get("order_id")?.asString ?: "",
            clientOrderId = order.get("client_order_id")?.asString ?: "",
            symbol = parseSymbol(order.get("product_id")?.asString ?: ""),
            side = side,
            type = type,
            requestedPrice = order.get("average_filled_price")?.asDouble ?: 0.0,
            executedPrice = order.get("average_filled_price")?.asDouble ?: 0.0,
            requestedQuantity = order.get("filled_size")?.asDouble ?: 0.0,
            executedQuantity = order.get("filled_size")?.asDouble ?: 0.0,
            fee = order.get("total_fees")?.asDouble ?: 0.0,
            feeCurrency = "USD",
            status = status,
            timestamp = System.currentTimeMillis(),
            exchange = exchangeName
        )
    }
}

data class AccountBalance(
    val currency: String,
    val available: Double,
    val hold: Double
) {
    val total: Double get() = available + hold
}
