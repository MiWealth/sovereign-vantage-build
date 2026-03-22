package com.miwealth.sovereignvantage.core.exchange.tick

/**
 * KRAKEN FUTURES DEMO TICK PROVIDER
 * 
 * Sovereign Vantage: Arthur Edition V5.19.241
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * Provides tick data from Kraken Futures Demo (testnet).
 * 
 * Setup:
 * 1. Visit: https://demo-futures.kraken.com
 * 2. Sign up (mobile-friendly, no verification)
 * 3. Fund account with test USDT (button in UI)
 * 4. Settings → API → Create New Key
 * 5. Copy API Key + Secret into Sovereign Vantage
 * 
 * Advantages:
 * - Best mobile-friendly testnet experience
 * - Unlimited test funds
 * - Perpetual futures (leverage, shorts)
 * - Identical to production API
 * - Perfect for testing STAHL Stair Stop on shorts
 * 
 * Data Source:
 * - WebSocket real-time stream
 * - Futures pairs: BTC/USD, ETH/USD (perpetuals)
 * - ~100-600 ticks per minute (high granularity)
 * 
 * Note:
 * - This is FUTURES only, not spot
 * - Pairs are derivatives (contracts), not actual crypto
 * - But perfect for testing short strategy and leverage
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */

import com.miwealth.sovereignvantage.core.utils.SystemLogger
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tick provider for Kraken Futures Demo.
 * 
 * Requires API keys but provides excellent testnet futures trading.
 */
class KrakenFuturesDemoTickProvider(
    symbols: List<String> = listOf("BTC/USD", "ETH/USD"),
    private val apiKey: String? = null,
    private val apiSecret: String? = null
) : BaseTickProvider(
    exchangeId = "kraken-futures-demo",
    symbols = symbols
) {
    companion object {
        private const val TAG = "KrakenFuturesDemoTickProvider"
        private const val WS_URL = "wss://demo-futures.kraken.com/ws/v1"
    }
    
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()
    
    private val _tickStream = MutableSharedFlow<UniversalTick>(
        replay = 0,
        extraBufferCapacity = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    
    override suspend fun connect(): Boolean {
        return try {
            SystemLogger.system("🔌 BUILD #241: Connecting KrakenFuturesDemoTickProvider for ${symbols.size} symbols")
            
            if (apiKey.isNullOrBlank() || apiSecret.isNullOrBlank()) {
                SystemLogger.w("SV-TICK", "⚠️ BUILD #241: KrakenFuturesDemoTickProvider: No API keys provided - using public feed only")
            }
            
            val request = Request.Builder()
                .url(WS_URL)
                .build()
            
            webSocket = client.newWebSocket(request, KrakenWebSocketListener())
            
            connected = true
            SystemLogger.system("✅ BUILD #241: KrakenFuturesDemoTickProvider connected")
            true
            
        } catch (e: Exception) {
            SystemLogger.error("❌ BUILD #241: KrakenFuturesDemoTickProvider connection failed: ${e.message}", e)
            trackError()
            connected = false
            false
        }
    }
    
    override fun getTickStream(): Flow<UniversalTick> = _tickStream.asSharedFlow()
    
    override suspend fun disconnect() {
        SystemLogger.system("🔌 BUILD #241: Disconnecting KrakenFuturesDemoTickProvider")
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        connected = false
    }
    
    override fun getTickSource(): TickSource = TickSource.TESTNET
    
    /**
     * WebSocket listener for Kraken Futures ticker channel.
     */
    private inner class KrakenWebSocketListener : WebSocketListener() {
        
        override fun onOpen(webSocket: WebSocket, response: Response) {
            SystemLogger.system("🔌 BUILD #241: Kraken WebSocket opened")
            
            // Subscribe to ticker channel for our symbols
            val productIds = symbols.map { 
                // Convert "BTC/USD" → "PI_XBTUSD" (Kraken futures format)
                when (it) {
                    "BTC/USD" -> "PI_XBTUSD"
                    "ETH/USD" -> "PI_ETHUSD"
                    else -> it.replace("/", "")
                }
            }
            
            val subscribe = JSONObject().apply {
                put("event", "subscribe")
                put("feed", "ticker")
                put("product_ids", JSONArray(productIds))
            }
            
            webSocket.send(subscribe.toString())
            SystemLogger.system("📡 BUILD #241: Subscribed to Kraken ticker for ${productIds.size} symbols")
        }
        
        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val json = JSONObject(text)
                val feed = json.optString("feed")
                
                if (feed == "ticker") {
                    val productId = json.getString("product_id")
                    
                    // Convert back: "PI_XBTUSD" → "BTC/USD"
                    val symbol = when (productId) {
                        "PI_XBTUSD" -> "BTC/USD"
                        "PI_ETHUSD" -> "ETH/USD"
                        else -> productId
                    }
                    
                    val tick = UniversalTick(
                        exchange = "kraken-futures-demo",
                        symbol = symbol,
                        price = json.getDouble("last"),
                        volume = json.optDouble("volume", 0.0),
                        bid = json.optDouble("bid", 0.0),
                        ask = json.optDouble("ask", 0.0),
                        timestamp = System.currentTimeMillis(),
                        source = TickSource.TESTNET
                    )
                    
                    trackTick()
                    _tickStream.tryEmit(tick)
                    
                    SystemLogger.d(TAG, "📊 BUILD #241: Kraken tick $symbol @ ${tick.price}")
                }
                
            } catch (e: Exception) {
                SystemLogger.error("❌ BUILD #241: Kraken message parse error: ${e.message}", e)
                trackError()
            }
        }
        
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            SystemLogger.error("❌ BUILD #241: Kraken WebSocket failure: ${t.message}", t)
            trackError()
            connected = false
        }
        
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            SystemLogger.system("🔌 BUILD #241: Kraken WebSocket closed: $reason")
            connected = false
        }
    }
}
