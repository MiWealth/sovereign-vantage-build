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
import kotlinx.coroutines.*

/**
 * UPHOLD CONNECTOR - Multi-Asset (Crypto + FOREX + Metals)
 * 
 * Unique connector providing:
 * - Direct FOREX pairs (EUR/USD, GBP/USD, etc.)
 * - Precious metals (XAU, XAG, XPT, XPD)
 * - 200+ assets including 30+ fiat currencies
 * - Card-based transaction model
 * 
 * Regulatory: US FinCEN registered, UK FCA, EU compliant
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 */

class UpholdConnector(
    config: ExchangeConfig,
    secureHttpClient: HybridSecureHttpClient? = null,
    credentialVault: PQCCredentialVault? = null
) : BaseCEXConnector(config, secureHttpClient, credentialVault) {
    
    companion object {
        private const val API_BASE = "https://api.uphold.com"
        private const val SANDBOX = "https://api-sandbox.uphold.com"
        
        val FIAT = listOf("USD","EUR","GBP","JPY","CHF","AUD","CAD","NZD","HKD","SGD","SEK","NOK","DKK","PLN","MXN","BRL","INR","ZAR","AED","CNY")
        val METALS = listOf("XAU","XAG","XPT","XPD")
        val FOREX_PAIRS = listOf("EUR/USD","GBP/USD","USD/JPY","USD/CHF","AUD/USD","USD/CAD","NZD/USD","EUR/GBP","EUR/JPY","GBP/JPY")
    }
    
    private val cardCache = mutableMapOf<String, String>()
    
    override val capabilities = ExchangeCapabilities(
        supportsSpotTrading = true, supportsFutures = false, supportsMargin = false,
        supportsOptions = false, supportsLending = false, supportsStaking = true,
        supportsWebSocket = false, supportsOrderbook = false, supportsMarketOrders = true,
        supportsLimitOrders = false, supportsStopOrders = false, supportsPostOnly = false,
        supportsCancelAll = false, maxOrdersPerSecond = 5, minOrderValue = 1.0,
        tradingFeeMaker = 0.0, tradingFeeTaker = 0.0, withdrawalEnabled = false,
        networks = listOf(BlockchainNetwork.BITCOIN, BlockchainNetwork.ETHEREUM, BlockchainNetwork.SOLANA)
    )
    
    override val endpoints = ExchangeEndpoints(
        ticker = "/v0/ticker/{pair}", orderBook = "", trades = "", candles = "",
        pairs = "/v0/assets", balances = "/v0/me/cards", placeOrder = "/v0/me/cards/{cardId}/transactions",
        cancelOrder = "", getOrder = "/v0/me/cards/{cardId}/transactions/{txId}",
        openOrders = "/v0/me/cards/{cardId}/transactions", orderHistory = "", wsUrl = ""
    )
    
    override fun signRequest(method: String, path: String, params: Map<String, String>, body: String?, timestamp: Long): Map<String, String> {
        val creds = "${config.apiKey}:${config.apiSecret}"
        return mapOf("Authorization" to "Basic ${Base64.encodeToString(creds.toByteArray(), Base64.NO_WRAP)}", "Content-Type" to "application/json")
    }
    
    private fun authHeaders() = signRequest("", "", emptyMap(), null, 0)
    
    // Rate fetching for FOREX/metals/crypto
    suspend fun getRate(base: String, quote: String): ForexRate? {
        val url = "${if (config.testnet) SANDBOX else API_BASE}/v0/ticker/${base.uppercase()}-${quote.uppercase()}"
        return try {
            val j = gson.fromJson(executeGet(url), JsonObject::class.java)
            val bid = j.get("bid")?.asString?.toDoubleOrNull() ?: 0.0
            val ask = j.get("ask")?.asString?.toDoubleOrNull() ?: 0.0
            ForexRate("$base/$quote", base, quote, bid, ask, ask - bid, System.currentTimeMillis())
        } catch (e: Exception) { null }
    }
    
    suspend fun getAllRates(): List<ForexRate> {
        val url = "${if (config.testnet) SANDBOX else API_BASE}/v0/ticker"
        return try {
            gson.fromJson(executeGet(url), JsonArray::class.java).mapNotNull { item ->
                val o = item.asJsonObject
                val pair = o.get("pair")?.asString?.split("-") ?: return@mapNotNull null
                if (pair.size != 2) return@mapNotNull null
                val bid = o.get("bid")?.asString?.toDoubleOrNull() ?: 0.0
                val ask = o.get("ask")?.asString?.toDoubleOrNull() ?: 0.0
                ForexRate("${pair[0]}/${pair[1]}", pair[0], pair[1], bid, ask, ask - bid, System.currentTimeMillis())
            }
        } catch (e: Exception) { emptyList() }
    }
    
    override fun parseTicker(response: JsonObject, symbol: String): PriceTick? {
        val bid = response.get("bid")?.asString?.toDoubleOrNull() ?: 0.0
        val ask = response.get("ask")?.asString?.toDoubleOrNull() ?: 0.0
        return PriceTick(normaliseSymbol(symbol), bid, ask, (bid+ask)/2, 0.0, 0.0, 0.0, 0.0, 0.0, System.currentTimeMillis(), "uphold")
    }
    
    override fun parseOrderBook(response: JsonObject, symbol: String): OrderBook? = null
    
    override fun parseTradingPairs(response: JsonObject): List<TradingPair> {
        val pairs = mutableListOf<TradingPair>()
        // Add FOREX pairs
        FOREX_PAIRS.forEach { pair ->
            val (b, q) = pair.split("/")
            pairs.add(TradingPair(pair, b, q, "$b-$q", 1.0, 1e9, 2, 5, 1.0, true, "uphold"))
        }
        // Add metal pairs
        METALS.forEach { m ->
            listOf("USD", "EUR", "GBP").forEach { f ->
                pairs.add(TradingPair("$m/$f", m, f, "$m-$f", 0.001, 1e9, 4, 2, 1.0, true, "uphold"))
            }
        }
        return pairs
    }
    
    override fun parseBalances(response: JsonObject): List<Balance> {
        return response.getAsJsonArray("cards")?.mapNotNull { c ->
            val o = c.asJsonObject
            val cur = o.get("currency")?.asString ?: return@mapNotNull null
            val avail = o.get("available")?.asString?.toDoubleOrNull() ?: 0.0
            val bal = o.get("balance")?.asString?.toDoubleOrNull() ?: avail
            o.get("id")?.asString?.let { cardCache[cur] = it }
            if (bal > 0) Balance(cur, avail, bal - avail, bal, "uphold") else null
        } ?: emptyList()
    }
    
    override fun parseOrder(response: JsonObject): ExecutedOrder? {
        val id = response.get("id")?.asString ?: return null
        val status = response.get("status")?.asString ?: "pending"
        val orig = response.getAsJsonObject("origin")
        val dest = response.getAsJsonObject("destination")
        val oCur = orig?.get("currency")?.asString ?: ""
        val dCur = dest?.get("currency")?.asString ?: ""
        val oAmt = orig?.get("amount")?.asString?.toDoubleOrNull() ?: 0.0
        val dAmt = dest?.get("amount")?.asString?.toDoubleOrNull() ?: 0.0
        return ExecutedOrder(id, "", "$oCur/$dCur", TradeSide.SELL, OrderType.MARKET,
            when(status) { "completed" -> OrderStatus.FILLED; "cancelled" -> OrderStatus.CANCELLED; else -> OrderStatus.OPEN },
            if (oAmt > 0) dAmt/oAmt else 0.0, oAmt, if (status == "completed") oAmt else 0.0,
            if (oAmt > 0) dAmt/oAmt else 0.0, 0.0, dCur, System.currentTimeMillis(), System.currentTimeMillis(), "uphold")
    }
    
    override fun parsePlaceOrderResponse(response: JsonObject, request: OrderRequest): OrderExecutionResult {
        if (response.has("code")) return OrderExecutionResult.Error(Exception("Uphold: ${response.get("message")?.asString}"))
        return parseOrder(response)?.let { OrderExecutionResult.Success(it) }
            ?: OrderExecutionResult.Error(Exception("Parse failed"))
    }
    
    override fun buildOrderBody(request: OrderRequest): Map<String, String> {
        val (b, q) = request.symbol.split("/")
        return mapOf("denomination" to gson.toJson(mapOf("amount" to request.quantity.toString(), "currency" to b)), "destination" to q)
    }
    
    override suspend fun getTicker(symbol: String): PriceTick? {
        val (b, q) = symbol.split("/")
        return getRate(b, q)?.let { PriceTick(symbol, it.bid, it.ask, it.mid, 0.0, 0.0, 0.0, 0.0, 0.0, it.timestamp, "uphold") }
    }
    
    override suspend fun getOrderBook(symbol: String, limit: Int): OrderBook? = null
    
    override suspend fun getBalances(): List<Balance> {
        val base = if (config.testnet) SANDBOX else API_BASE
        return try {
            val j = JsonObject().also { it.add("cards", gson.fromJson(executeSignedGet("$base/v0/me/cards", authHeaders()), JsonArray::class.java)) }
            parseBalances(j)
        } catch (e: Exception) { emptyList() }
    }
    
    private suspend fun getOrCreateCard(currency: String): String? {
        cardCache[currency]?.let { return it }
        getBalances()
        cardCache[currency]?.let { return it }
        val base = if (config.testnet) SANDBOX else API_BASE
        return try {
            val j = gson.fromJson(executeSignedPost("$base/v0/me/cards", gson.toJson(mapOf("label" to "SV $currency", "currency" to currency)), authHeaders()), JsonObject::class.java)
            j.get("id")?.asString?.also { cardCache[currency] = it }
        } catch (e: Exception) { null }
    }
    
    override suspend fun placeOrder(request: OrderRequest): OrderExecutionResult {
        val (b, q) = request.symbol.split("/")
        val cardId = getOrCreateCard(b) ?: return OrderExecutionResult.Error(Exception("No card for $b"))
        val base = if (config.testnet) SANDBOX else API_BASE
        val body = gson.toJson(mapOf("denomination" to mapOf("amount" to request.quantity.toString(), "currency" to b), "destination" to q))
        return try {
            val create = gson.fromJson(executeSignedPost("$base/v0/me/cards/$cardId/transactions", body, authHeaders()), JsonObject::class.java)
            val txId = create.get("id")?.asString ?: return OrderExecutionResult.Error(Exception("No tx ID"))
            val commit = gson.fromJson(executeSignedPost("$base/v0/me/cards/$cardId/transactions/$txId/commit", "{}", authHeaders()), JsonObject::class.java)
            parsePlaceOrderResponse(commit, request)
        } catch (e: Exception) { OrderExecutionResult.Error(e) }
    }
    
    override suspend fun cancelOrder(symbol: String, orderId: String) = false
    
    // FOREX convenience method
    suspend fun executeForexTrade(from: String, to: String, amount: Double): ForexTradeResult {
        return when (val r = placeOrder(OrderRequest("$from/$to", TradeSide.SELL, OrderType.MARKET, amount))) {
            is OrderExecutionResult.Success -> ForexTradeResult.Success(from, to, amount, r.order.filledQuantity * r.order.averagePrice, r.order.averagePrice, r.order.fee, r.order.orderId)
            is OrderExecutionResult.Error -> ForexTradeResult.Failure(r.error.message ?: "Failed")
            else -> ForexTradeResult.Failure("Unknown")
        }
    }
    
    // Metal trading
    suspend fun buyMetal(metal: String, qty: Double, payCurrency: String = "USD"): OrderExecutionResult {
        require(metal in METALS) { "Invalid metal" }
        val rate = getRate(metal, payCurrency) ?: return OrderExecutionResult.Error(Exception("No rate"))
        return placeOrder(OrderRequest("$payCurrency/$metal", TradeSide.SELL, OrderType.MARKET, qty * rate.ask))
    }
    
    override fun handleWsMessage(text: String) {} // No WS
    override fun buildWsSubscription(channels: List<String>, symbols: List<String>) = ""
    override fun hasError(response: JsonObject) = response.has("code")
    override fun toExchangeSymbol(s: String) = s.replace("/", "-")
    override fun normaliseSymbol(s: String) = s.replace("-", "/")
    
    fun isForex(c: String) = c.uppercase() in FIAT
    fun isMetal(c: String) = c.uppercase() in METALS
}

data class ForexRate(val pair: String, val baseCurrency: String, val quoteCurrency: String, val bid: Double, val ask: Double, val spread: Double, val timestamp: Long) {
    val mid get() = (bid + ask) / 2
    val spreadPips get() = spread * 10000
}

sealed class ForexTradeResult {
    data class Success(val fromCurrency: String, val toCurrency: String, val fromAmount: Double, val toAmount: Double, val rate: Double, val fee: Double, val transactionId: String) : ForexTradeResult()
    data class Failure(val message: String) : ForexTradeResult()
}
