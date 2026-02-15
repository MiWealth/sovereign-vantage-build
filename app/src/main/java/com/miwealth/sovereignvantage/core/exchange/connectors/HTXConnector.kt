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
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * HTX (HUOBI) CONNECTOR - Complete Implementation
 * 
 * Full-featured HTX exchange connector with spot + USDT-M futures.
 * Huobi rebranded to HTX in September 2023.
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

class HTXConnector(
    config: ExchangeConfig,
    secureHttpClient: HybridSecureHttpClient? = null,
    credentialVault: PQCCredentialVault? = null
) : BaseCEXConnector(config, secureHttpClient, credentialVault) {
    
    companion object {
        private const val SPOT_BASE = "https://api.huobi.pro"
        private const val FUTURES_BASE = "https://api.hbdm.com"
        private val TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC)
    }
    
    private var spotAccountId: Long? = null
    
    override val capabilities = ExchangeCapabilities(
        supportsSpotTrading = true, supportsFutures = true, supportsMargin = true,
        supportsOptions = false, supportsLending = true, supportsStaking = true,
        supportsWebSocket = true, supportsOrderbook = true, supportsMarketOrders = true,
        supportsLimitOrders = true, supportsStopOrders = true, supportsPostOnly = true,
        supportsCancelAll = true, maxOrdersPerSecond = 10, minOrderValue = 5.0,
        tradingFeeMaker = 0.002, tradingFeeTaker = 0.002, withdrawalEnabled = false,
        networks = listOf(BlockchainNetwork.BITCOIN, BlockchainNetwork.ETHEREUM, BlockchainNetwork.TRON)
    )
    
    override val endpoints = ExchangeEndpoints(
        ticker = "/market/detail/merged", orderBook = "/market/depth",
        trades = "/market/history/trade", candles = "/market/history/kline",
        pairs = "/v1/common/symbols", balances = "/v1/account/accounts/{id}/balance",
        placeOrder = "/v1/order/orders/place", cancelOrder = "/v1/order/orders/{id}/submitcancel",
        getOrder = "/v1/order/orders/{id}", openOrders = "/v1/order/openOrders",
        orderHistory = "/v1/order/orders", wsUrl = "wss://api.huobi.pro/ws"
    )
    
    override fun signRequest(method: String, path: String, params: Map<String, String>, body: String?, timestamp: Long): Map<String, String> {
        return mapOf("Content-Type" to "application/json")
    }
    
    private fun buildSignedUrl(base: String, path: String, params: Map<String, String> = emptyMap()): String {
        val ts = TS_FMT.format(Instant.now())
        val sp = (params + mapOf("AccessKeyId" to config.apiKey, "SignatureMethod" to "HmacSHA256", 
            "SignatureVersion" to "2", "Timestamp" to ts)).toSortedMap()
            .entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
        val host = java.net.URI(base).host
        val sig = b64Hmac("GET\n$host\n$path\n$sp", config.apiSecret)
        return "$base$path?$sp&Signature=${enc(sig)}"
    }
    
    private fun buildSignedPost(base: String, path: String): Pair<String, Map<String, String>> {
        val ts = TS_FMT.format(Instant.now())
        val sp = mapOf("AccessKeyId" to config.apiKey, "SignatureMethod" to "HmacSHA256",
            "SignatureVersion" to "2", "Timestamp" to ts).toSortedMap()
            .entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
        val host = java.net.URI(base).host
        val sig = b64Hmac("POST\n$host\n$path\n$sp", config.apiSecret)
        return "$base$path?$sp&Signature=${enc(sig)}" to mapOf("Content-Type" to "application/json")
    }
    
    private fun b64Hmac(data: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return Base64.encodeToString(mac.doFinal(data.toByteArray()), Base64.NO_WRAP)
    }
    
    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8").replace("+", "%20")
    
    suspend fun initAccount(): Boolean {
        if (spotAccountId != null) return true
        return try {
            val r = gson.fromJson(executeGet(buildSignedUrl(SPOT_BASE, "/v1/account/accounts")), JsonObject::class.java)
            r.getAsJsonArray("data")?.firstOrNull { it.asJsonObject.get("type")?.asString == "spot" }
                ?.asJsonObject?.get("id")?.asLong?.also { spotAccountId = it } != null
        } catch (e: Exception) { false }
    }
    
    override fun parseTicker(response: JsonObject, symbol: String): PriceTick? {
        val t = response.getAsJsonObject("tick") ?: return null
        return PriceTick(normaliseSymbol(symbol), t.getAsJsonArray("bid")?.get(0)?.asDouble ?: 0.0,
            t.getAsJsonArray("ask")?.get(0)?.asDouble ?: 0.0, t.get("close")?.asDouble ?: 0.0,
            t.get("vol")?.asDouble ?: 0.0, t.get("high")?.asDouble ?: 0.0, t.get("low")?.asDouble ?: 0.0,
            0.0, 0.0, System.currentTimeMillis(), "htx")
    }
    
    override fun parseOrderBook(response: JsonObject, symbol: String): OrderBook? {
        val t = response.getAsJsonObject("tick") ?: return null
        return OrderBook(normaliseSymbol(symbol),
            t.getAsJsonArray("bids")?.map { OrderBookLevel(it.asJsonArray[0].asDouble, it.asJsonArray[1].asDouble) } ?: emptyList(),
            t.getAsJsonArray("asks")?.map { OrderBookLevel(it.asJsonArray[0].asDouble, it.asJsonArray[1].asDouble) } ?: emptyList(),
            System.currentTimeMillis(), "htx")
    }
    
    override fun parseTradingPairs(response: JsonObject): List<TradingPair> {
        return response.getAsJsonArray("data")?.mapNotNull { s ->
            val o = s.asJsonObject
            if (o.get("state")?.asString != "online") return@mapNotNull null
            val b = o.get("base-currency")?.asString?.uppercase() ?: return@mapNotNull null
            val q = o.get("quote-currency")?.asString?.uppercase() ?: return@mapNotNull null
            TradingPair("$b/$q", b, q, o.get("symbol")?.asString ?: "", o.get("min-order-amt")?.asDouble ?: 0.0,
                o.get("max-order-amt")?.asDouble ?: 1e9, o.get("amount-precision")?.asInt ?: 8,
                o.get("price-precision")?.asInt ?: 8, o.get("min-order-value")?.asDouble ?: 5.0, true, "htx")
        } ?: emptyList()
    }
    
    override fun parseBalances(response: JsonObject): List<Balance> {
        val m = mutableMapOf<String, Pair<Double, Double>>()
        response.getAsJsonObject("data")?.getAsJsonArray("list")?.forEach { i ->
            val o = i.asJsonObject
            val c = o.get("currency")?.asString?.uppercase() ?: return@forEach
            val b = o.get("balance")?.asString?.toDoubleOrNull() ?: 0.0
            val (f, l) = m.getOrDefault(c, 0.0 to 0.0)
            m[c] = if (o.get("type")?.asString == "trade") b to l else f to b
        }
        return m.filter { it.value.first + it.value.second > 0 }
            .map { Balance(it.key, it.value.first, it.value.second, it.value.first + it.value.second, "htx") }
    }
    
    override fun parseOrder(response: JsonObject): ExecutedOrder? {
        val d = response.getAsJsonObject("data") ?: response
        val sym = d.get("symbol")?.asString ?: return null
        val id = d.get("id")?.asString ?: d.get("id")?.asLong?.toString() ?: return null
        val t = d.get("type")?.asString ?: "buy-limit"
        return ExecutedOrder(id, d.get("client-order-id")?.asString ?: "", normaliseSymbol(sym),
            if (t.startsWith("buy")) TradeSide.BUY else TradeSide.SELL,
            if (t.contains("market")) OrderType.MARKET else OrderType.LIMIT,
            when (d.get("state")?.asString) { "filled" -> OrderStatus.FILLED; "canceled" -> OrderStatus.CANCELLED
                "partial-filled" -> OrderStatus.PARTIALLY_FILLED; else -> OrderStatus.OPEN },
            d.get("price")?.asString?.toDoubleOrNull() ?: 0.0, d.get("amount")?.asString?.toDoubleOrNull() ?: 0.0,
            d.get("field-amount")?.asString?.toDoubleOrNull() ?: 0.0, 0.0, 0.0, "",
            d.get("created-at")?.asLong ?: System.currentTimeMillis(), System.currentTimeMillis(), "htx")
    }
    
    override fun parsePlaceOrderResponse(response: JsonObject, request: OrderRequest): OrderExecutionResult {
        if (response.get("status")?.asString != "ok") {
            return OrderExecutionResult.Error(Exception("HTX: ${response.get("err-msg")?.asString}"))
        }
        val id = response.get("data")?.asString ?: return OrderExecutionResult.Error(Exception("No order ID"))
        return OrderExecutionResult.Success(ExecutedOrder(id, request.clientOrderId ?: "", request.symbol,
            request.side, request.type, OrderStatus.OPEN, request.price ?: 0.0, request.quantity, 0.0, 0.0, 0.0, "",
            System.currentTimeMillis(), System.currentTimeMillis(), "htx"))
    }
    
    override fun buildOrderBody(request: OrderRequest): Map<String, String> {
        val t = when { request.type == OrderType.MARKET && request.side == TradeSide.BUY -> "buy-market"
            request.type == OrderType.MARKET -> "sell-market"; request.side == TradeSide.BUY -> "buy-limit"
            else -> "sell-limit" }
        return mutableMapOf("account-id" to (spotAccountId?.toString() ?: ""), "symbol" to toExchangeSymbol(request.symbol),
            "type" to t, "amount" to request.quantity.toString()).also { if (request.price != null) it["price"] = request.price.toString() }
    }
    
    override suspend fun getTicker(symbol: String): PriceTick? {
        val s = toExchangeSymbol(symbol)
        return try { parseTicker(gson.fromJson(executeGet("$SPOT_BASE${endpoints.ticker}?symbol=$s"), JsonObject::class.java), s) }
        catch (e: Exception) { null }
    }
    
    override suspend fun getOrderBook(symbol: String, limit: Int): OrderBook? {
        val s = toExchangeSymbol(symbol)
        return try { parseOrderBook(gson.fromJson(executeGet("$SPOT_BASE${endpoints.orderBook}?symbol=$s&type=step0"), JsonObject::class.java), s) }
        catch (e: Exception) { null }
    }
    
    override suspend fun getBalances(): List<Balance> {
        if (!initAccount()) return emptyList()
        return try { parseBalances(gson.fromJson(executeGet(buildSignedUrl(SPOT_BASE, "/v1/account/accounts/$spotAccountId/balance")), JsonObject::class.java)) }
        catch (e: Exception) { emptyList() }
    }
    
    override suspend fun placeOrder(request: OrderRequest): OrderExecutionResult {
        if (!initAccount()) return OrderExecutionResult.Error(Exception("Account init failed"))
        val (url, h) = buildSignedPost(SPOT_BASE, endpoints.placeOrder)
        return try { parsePlaceOrderResponse(gson.fromJson(executeSignedPost(url, gson.toJson(buildOrderBody(request)), h), JsonObject::class.java), request) }
        catch (e: Exception) { OrderExecutionResult.Error(e) }
    }
    
    override suspend fun cancelOrder(symbol: String, orderId: String): Boolean {
        val (url, h) = buildSignedPost(SPOT_BASE, "/v1/order/orders/$orderId/submitcancel")
        return try { gson.fromJson(executeSignedPost(url, "{}", h), JsonObject::class.java).get("status")?.asString == "ok" }
        catch (e: Exception) { false }
    }
    
    // Futures methods
    suspend fun getFuturesPositions(): List<FuturesPosition> {
        val (url, h) = buildSignedPost(FUTURES_BASE, "/linear-swap-api/v1/swap_position_info")
        return try {
            val r = gson.fromJson(executeSignedPost(url, "{}", h), JsonObject::class.java)
            r.getAsJsonArray("data")?.map { p ->
                val o = p.asJsonObject
                FuturesPosition(o.get("contract_code")?.asString ?: "",
                    if (o.get("direction")?.asString == "buy") PositionSide.LONG else PositionSide.SHORT,
                    o.get("volume")?.asDouble ?: 0.0, o.get("cost_open")?.asDouble ?: 0.0,
                    o.get("last_price")?.asDouble ?: 0.0, 0.0, o.get("profit_unreal")?.asDouble ?: 0.0,
                    o.get("lever_rate")?.asInt ?: 1, MarginType.CROSS)
            } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }
    
    suspend fun placeFuturesOrder(req: FuturesOrderRequest): OrderExecutionResult {
        val (url, h) = buildSignedPost(FUTURES_BASE, "/linear-swap-api/v1/swap_order")
        val body = gson.toJson(mapOf("contract_code" to req.symbol, "volume" to req.quantity.toLong(),
            "direction" to if (req.side == TradeSide.BUY) "buy" else "sell",
            "offset" to if (req.reduceOnly) "close" else "open", "lever_rate" to req.leverage,
            "order_price_type" to if (req.type == OrderType.MARKET) "opponent" else "limit").let {
                if (req.price != null) it + ("price" to req.price) else it })
        return try {
            val r = gson.fromJson(executeSignedPost(url, body, h), JsonObject::class.java)
            if (r.get("status")?.asString != "ok") OrderExecutionResult.Error(Exception(r.get("err_msg")?.asString))
            else OrderExecutionResult.Success(ExecutedOrder(r.getAsJsonObject("data")?.get("order_id")?.asString ?: "",
                "", req.symbol, req.side, req.type, OrderStatus.OPEN, req.price ?: 0.0, req.quantity, 0.0, 0.0, 0.0, "USDT",
                System.currentTimeMillis(), System.currentTimeMillis(), "htx"))
        } catch (e: Exception) { OrderExecutionResult.Error(e) }
    }
    
    override fun handleWsMessage(text: String) {
        try {
            val j = gson.fromJson(text, JsonObject::class.java)
            if (j.has("ping")) { sendWsMessage(gson.toJson(mapOf("pong" to j.get("ping").asLong))); return }
            val ch = j.get("ch")?.asString ?: return
            val t = j.getAsJsonObject("tick") ?: return
            when { ch.contains("depth") -> scope.launch { _orderBookUpdates.emit(parseOrderBook(j, ch.split(".")[1]) ?: return@launch) }
                ch.contains("detail") -> scope.launch { _priceUpdates.emit(parseTicker(j, ch.split(".")[1]) ?: return@launch) } }
        } catch (e: Exception) {}
    }
    
    override fun buildWsSubscription(channels: List<String>, symbols: List<String>): String {
        val s = symbols.firstOrNull()?.let { toExchangeSymbol(it) } ?: return ""
        val ch = when (channels.firstOrNull()) { "depth" -> "market.$s.depth.step0"; "trade" -> "market.$s.trade.detail"
            else -> "market.$s.detail" }
        return gson.toJson(mapOf("sub" to ch, "id" to "sv_${System.currentTimeMillis()}"))
    }
    
    override fun hasError(response: JsonObject) = response.get("status")?.asString != "ok"
    override fun toExchangeSymbol(s: String) = s.replace("/", "").lowercase()
    override fun normaliseSymbol(s: String): String {
        val u = s.uppercase()
        listOf("USDT", "USDC", "BTC", "ETH", "HT").forEach { if (u.endsWith(it)) return "${u.dropLast(it.length)}/$it" }
        return u
    }
}
