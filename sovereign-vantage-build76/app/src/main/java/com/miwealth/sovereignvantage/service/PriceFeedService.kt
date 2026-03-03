package com.miwealth.sovereignvantage.service

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.miwealth.sovereignvantage.core.OHLCVBar
import com.miwealth.sovereignvantage.core.PriceTick
import com.miwealth.sovereignvantage.core.security.pqc.hybrid.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit

/**
 * SOVEREIGN VANTAGE V5.17.0 "ARTHUR EDITION"
 * PRICE FEED SERVICE - PQC SECURED
 * 
 * Real-time WebSocket price feeds from multiple exchanges:
 * - Kraken (primary) - ticker + OHLC data
 * - Coinbase (secondary) - ticker data
 * 
 * POST-QUANTUM FORTRESS SECURITY:
 * - HybridSecureWebSocket with Kyber-1024 session keys
 * - Dilithium-5 signed audit trail for all subscriptions
 * - AES-256-GCM local encryption of received price data
 * - Forward secrecy via automatic key rotation (15 min)
 * - Replay attack protection with nonce tracking
 * 
 * Emits:
 * - PriceTick: Real-time bid/ask/last for scalping (sub-second)
 * - OHLCVBar: OHLC candles for swing trading indicators
 * 
 * Self-sovereign design: All connections are direct from user's device.
 * No MiWealth servers involved in price feed routing.
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO: Arthur Iain McManus (1966-2025)
 * 
 * Dedicated to Cathryn for tolerating me and my quirks. 💘
 */

/**
 * Connection state for price feed WebSockets
 */
sealed class PriceFeedState {
    object Disconnected : PriceFeedState()
    object Connecting : PriceFeedState()
    object Connected : PriceFeedState()
    data class Error(val message: String) : PriceFeedState()
}

/**
 * Price feed service managing real-time WebSocket connections
 */
class PriceFeedService(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    
    companion object {
        // Kraken WebSocket (public, no auth required for market data)
        private const val KRAKEN_WS_URL = "wss://ws.kraken.com"
        
        // Coinbase WebSocket (public market data)
        private const val COINBASE_WS_URL = "wss://ws-feed.exchange.coinbase.com"
        
        // Reconnection settings
        private const val RECONNECT_DELAY_MS = 5000L
        private const val PING_INTERVAL_MS = 30000L
        
        @Volatile
        private var instance: PriceFeedService? = null
        
        fun getInstance(): PriceFeedService {
            return instance ?: synchronized(this) {
                instance ?: PriceFeedService().also { instance = it }
            }
        }
    }
    
    // PQC-secured WebSocket clients
    private val krakenSecureWs = HybridSecureWebSocket.builder()
        .withExchangeId("kraken")
        .withConfig(HybridPQCConfig.forExchange("kraken"))
        .withPingInterval(PING_INTERVAL_MS)
        .build()
    
    private val coinbaseSecureWs = HybridSecureWebSocket.builder()
        .withExchangeId("coinbase")
        .withConfig(HybridPQCConfig.forExchange("coinbase"))
        .withPingInterval(PING_INTERVAL_MS)
        .build()
    
    private val gson = Gson()
    
    // WebSocket connection IDs (from HybridSecureWebSocket)
    private var krakenConnectionId: String? = null
    private var coinbaseConnectionId: String? = null
    
    // ========================================================================
    // PRICE DATA FLOWS
    // ========================================================================
    
    // Real-time price ticks (bid/ask/last) - used for scalping
    private val _priceTicks = MutableSharedFlow<PriceTick>(
        replay = 1,
        extraBufferCapacity = 100
    )
    val priceTicks: SharedFlow<PriceTick> = _priceTicks.asSharedFlow()
    
    // OHLCV bars - used for indicator calculations
    private val _ohlcvBars = MutableSharedFlow<OHLCVBar>(
        replay = 1,
        extraBufferCapacity = 100
    )
    val ohlcvBars: SharedFlow<OHLCVBar> = _ohlcvBars.asSharedFlow()
    
    // ========================================================================
    // CONNECTION STATE FLOWS
    // ========================================================================
    
    private val _krakenState = MutableStateFlow<PriceFeedState>(PriceFeedState.Disconnected)
    val krakenState: StateFlow<PriceFeedState> = _krakenState.asStateFlow()
    
    private val _coinbaseState = MutableStateFlow<PriceFeedState>(PriceFeedState.Disconnected)
    val coinbaseState: StateFlow<PriceFeedState> = _coinbaseState.asStateFlow()
    
    // Combined connection state
    val isConnected: Flow<Boolean> = combine(krakenState, coinbaseState) { kraken, coinbase ->
        kraken == PriceFeedState.Connected || coinbase == PriceFeedState.Connected
    }
    
    // ========================================================================
    // SUBSCRIPTIONS
    // ========================================================================
    
    private val subscribedSymbols = mutableSetOf<String>()
    
    // ========================================================================
    // KRAKEN WEBSOCKET (PQC SECURED)
    // ========================================================================
    
    fun connectKraken() {
        if (_krakenState.value == PriceFeedState.Connected || 
            _krakenState.value == PriceFeedState.Connecting) {
            return
        }
        
        _krakenState.value = PriceFeedState.Connecting
        
        krakenConnectionId = krakenSecureWs.connect(KRAKEN_WS_URL, object : SecureWebSocketListener {
            override fun onSecureOpen(connectionId: String, response: Response) {
                _krakenState.value = PriceFeedState.Connected
                android.util.Log.i("PriceFeedService", "Kraken PQC WebSocket connected: $connectionId")
                resubscribeKraken()
            }
            
            override fun onSecureMessage(text: String, audit: MessageAuditRecord) {
                // Message received with cryptographic audit trail
                parseKrakenMessage(text)
            }
            
            override fun onSecureFailure(connectionId: String, t: Throwable, response: Response?) {
                _krakenState.value = PriceFeedState.Error(t.message ?: "Connection failed")
                android.util.Log.e("PriceFeedService", "Kraken PQC WebSocket failed: ${t.message}")
                scheduleReconnect { connectKraken() }
            }
            
            override fun onSecureClosed(connectionId: String, code: Int, reason: String) {
                _krakenState.value = PriceFeedState.Disconnected
                krakenConnectionId = null
            }
        })
    }
    
    fun subscribeKraken(symbols: List<String>) {
        subscribedSymbols.addAll(symbols)
        
        if (_krakenState.value != PriceFeedState.Connected) {
            connectKraken()
            return
        }
        
        val connectionId = krakenConnectionId ?: return
        val krakenPairs = symbols.map { convertToKrakenPair(it) }
        
        // Subscribe to ticker (sent with Dilithium signature for audit)
        val tickerSub = JsonObject().apply {
            addProperty("event", "subscribe")
            add("pair", gson.toJsonTree(krakenPairs))
            add("subscription", JsonObject().apply {
                addProperty("name", "ticker")
            })
        }
        krakenSecureWs.send(connectionId, tickerSub.toString())
        
        // Subscribe to OHLC (1 minute)
        val ohlcSub = JsonObject().apply {
            addProperty("event", "subscribe")
            add("pair", gson.toJsonTree(krakenPairs))
            add("subscription", JsonObject().apply {
                addProperty("name", "ohlc")
                addProperty("interval", 1)
            })
        }
        krakenSecureWs.send(connectionId, ohlcSub.toString())
    }
    
    private fun resubscribeKraken() {
        if (subscribedSymbols.isNotEmpty()) {
            subscribeKraken(subscribedSymbols.toList())
        }
    }
    
    private fun parseKrakenMessage(text: String) {
        try {
            // Kraken sends array for data, object for events
            if (text.startsWith("[")) {
                val array = gson.fromJson(text, com.google.gson.JsonArray::class.java)
                
                // Format: [channelID, data, channelName, pair]
                if (array.size() >= 4) {
                    val channelName = array[2].asString
                    val pair = array[3].asString
                    val symbol = convertFromKrakenPair(pair)
                    
                    when {
                        channelName == "ticker" -> {
                            val data = array[1].asJsonObject
                            val tick = PriceTick(
                                symbol = symbol,
                                bid = data.getAsJsonArray("b")[0].asDouble,
                                ask = data.getAsJsonArray("a")[0].asDouble,
                                last = data.getAsJsonArray("c")[0].asDouble,
                                volume = data.getAsJsonArray("v")[1].asDouble,
                                timestamp = System.currentTimeMillis(),
                                exchange = "Kraken"
                            )
                            scope.launch { _priceTicks.emit(tick) }
                        }
                        channelName.startsWith("ohlc") -> {
                            val data = array[1].asJsonArray
                            val bar = OHLCVBar(
                                symbol = symbol,
                                open = data[2].asDouble,
                                high = data[3].asDouble,
                                low = data[4].asDouble,
                                close = data[5].asDouble,
                                volume = data[7].asDouble,
                                timestamp = (data[0].asDouble * 1000).toLong(),
                                interval = "1m"
                            )
                            scope.launch { _ohlcvBars.emit(bar) }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PriceFeedService", "Kraken parse error: ${e.message}")
        }
    }
    
    // ========================================================================
    // COINBASE WEBSOCKET
    // ========================================================================
    // COINBASE WEBSOCKET (PQC SECURED)
    // ========================================================================
    
    fun connectCoinbase() {
        if (_coinbaseState.value == PriceFeedState.Connected || 
            _coinbaseState.value == PriceFeedState.Connecting) {
            return
        }
        
        _coinbaseState.value = PriceFeedState.Connecting
        
        coinbaseConnectionId = coinbaseSecureWs.connect(COINBASE_WS_URL, object : SecureWebSocketListener {
            override fun onSecureOpen(connectionId: String, response: Response) {
                _coinbaseState.value = PriceFeedState.Connected
                android.util.Log.i("PriceFeedService", "Coinbase PQC WebSocket connected: $connectionId")
                resubscribeCoinbase()
            }
            
            override fun onSecureMessage(text: String, audit: MessageAuditRecord) {
                // Message received with cryptographic audit trail
                parseCoinbaseMessage(text)
            }
            
            override fun onSecureFailure(connectionId: String, t: Throwable, response: Response?) {
                _coinbaseState.value = PriceFeedState.Error(t.message ?: "Connection failed")
                android.util.Log.e("PriceFeedService", "Coinbase PQC WebSocket failed: ${t.message}")
                scheduleReconnect { connectCoinbase() }
            }
            
            override fun onSecureClosed(connectionId: String, code: Int, reason: String) {
                _coinbaseState.value = PriceFeedState.Disconnected
                coinbaseConnectionId = null
            }
        })
    }
    
    fun subscribeCoinbase(symbols: List<String>) {
        subscribedSymbols.addAll(symbols)
        
        if (_coinbaseState.value != PriceFeedState.Connected) {
            connectCoinbase()
            return
        }
        
        val connectionId = coinbaseConnectionId ?: return
        val productIds = symbols.map { it.replace("/", "-") }
        
        // Subscribe (sent with Dilithium signature for audit)
        val sub = JsonObject().apply {
            addProperty("type", "subscribe")
            add("product_ids", gson.toJsonTree(productIds))
            add("channels", gson.toJsonTree(listOf("ticker", "matches")))
        }
        coinbaseSecureWs.send(connectionId, sub.toString())
    }
    
    private fun resubscribeCoinbase() {
        if (subscribedSymbols.isNotEmpty()) {
            subscribeCoinbase(subscribedSymbols.toList())
        }
    }
    
    private fun parseCoinbaseMessage(text: String) {
        try {
            val json = gson.fromJson(text, JsonObject::class.java)
            val type = json.get("type")?.asString ?: return
            
            when (type) {
                "ticker" -> {
                    val productId = json.get("product_id")?.asString ?: return
                    val symbol = productId.replace("-", "/")
                    
                    val tick = PriceTick(
                        symbol = symbol,
                        bid = json.get("best_bid")?.asDouble ?: 0.0,
                        ask = json.get("best_ask")?.asDouble ?: 0.0,
                        last = json.get("price")?.asDouble ?: 0.0,
                        volume = json.get("volume_24h")?.asDouble ?: 0.0,
                        timestamp = System.currentTimeMillis(),
                        exchange = "Coinbase"
                    )
                    scope.launch { _priceTicks.emit(tick) }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PriceFeedService", "Coinbase parse error: ${e.message}")
        }
    }
    
    // ========================================================================
    // PUBLIC API
    // ========================================================================
    
    /**
     * Subscribe to symbols on the preferred exchange
     */
    fun subscribe(symbols: List<String>, exchange: String = "kraken") {
        when (exchange.lowercase()) {
            "kraken" -> subscribeKraken(symbols)
            "coinbase" -> subscribeCoinbase(symbols)
            "both" -> {
                subscribeKraken(symbols)
                subscribeCoinbase(symbols)
            }
        }
    }
    
    /**
     * Connect to the preferred exchange
     */
    fun connect(exchange: String = "kraken") {
        when (exchange.lowercase()) {
            "kraken" -> connectKraken()
            "coinbase" -> connectCoinbase()
            "both" -> {
                connectKraken()
                connectCoinbase()
            }
        }
    }
    
    /**
     * Get currently subscribed symbols
     */
    fun getSubscribedSymbols(): Set<String> = subscribedSymbols.toSet()
    
    // ========================================================================
    // HELPERS
    // ========================================================================
    
    private fun convertToKrakenPair(symbol: String): String {
        return when (symbol) {
            "BTC/USD" -> "XBT/USD"
            "BTC/EUR" -> "XBT/EUR"
            "BTC/USDT" -> "XBT/USDT"
            else -> symbol
        }
    }
    
    private fun convertFromKrakenPair(pair: String): String {
        return when {
            pair.contains("XBT") -> pair.replace("XBT", "BTC")
            else -> pair
        }
    }
    
    private fun scheduleReconnect(reconnect: () -> Unit) {
        scope.launch {
            delay(RECONNECT_DELAY_MS)
            reconnect()
        }
    }
    
    fun disconnect() {
        krakenConnectionId?.let { krakenSecureWs.close(it, 1000, "Client disconnect") }
        coinbaseConnectionId?.let { coinbaseSecureWs.close(it, 1000, "Client disconnect") }
        krakenConnectionId = null
        coinbaseConnectionId = null
        _krakenState.value = PriceFeedState.Disconnected
        _coinbaseState.value = PriceFeedState.Disconnected
    }
    
    fun shutdown() {
        disconnect()
        subscribedSymbols.clear()
        krakenSecureWs.shutdown()
        coinbaseSecureWs.shutdown()
        scope.cancel()
    }
    
    // ========================================================================
    // PQC SECURITY REPORTING
    // ========================================================================
    
    /**
     * Get PQC security report for Kraken WebSocket
     */
    fun getKrakenSecurityReport(): WebSocketSecurityReport = krakenSecureWs.getSecurityReport()
    
    /**
     * Get PQC security report for Coinbase WebSocket
     */
    fun getCoinbaseSecurityReport(): WebSocketSecurityReport = coinbaseSecureWs.getSecurityReport()
    
    /**
     * Get audit log for all WebSocket connections
     */
    fun getAuditLog(): List<MessageAuditRecord> {
        return krakenSecureWs.exportAuditLog() + coinbaseSecureWs.exportAuditLog()
    }
}
