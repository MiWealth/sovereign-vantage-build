package com.miwealth.sovereignvantage.core.exchange.tick

/**
 * COINBASE SANDBOX TICK PROVIDER
 * 
 * Sovereign Vantage: Arthur Edition V5.19.241
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * Provides tick data from Coinbase Advanced Trade Sandbox (testnet).
 * 
 * Setup:
 * 1. Visit: https://public.sandbox.exchange.coinbase.com
 * 2. Sign up (mobile-friendly, no verification)
 * 3. Settings → API → Create New Key
 * 4. Enable: View + Trade permissions
 * 5. Copy API Key + Secret into Sovereign Vantage
 * 
 * Advantages:
 * - Real spot testnet with free funds
 * - Mobile-friendly web UI for key generation
 * - Professional-grade API
 * - Perfect for order execution testing
 * 
 * Data Source:
 * - WebSocket real-time stream
 * - Spot pairs: BTC/USD, ETH/USD, SOL/USD
 * - ~100-600 ticks per minute (high granularity)
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
import org.json.JSONObject

/**
 * Tick provider for Coinbase Advanced Trade Sandbox.
 * 
 * Requires API keys but provides real testnet spot trading.
 */
class CoinbaseSandboxTickProvider(
    symbols: List<String> = listOf("BTC/USD", "ETH/USD", "SOL/USD"),
    private val apiKey: String? = null,
    private val apiSecret: String? = null
) : BaseTickProvider(
    exchangeId = "coinbase-sandbox",
    symbols = symbols
) {
    companion object {
        private const val TAG = "CoinbaseSandboxTickProvider"
        private const val WS_URL = "wss://ws-feed-public.sandbox.exchange.coinbase.com"
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
            SystemLogger.system("🔌 BUILD #241: Connecting CoinbaseSandboxTickProvider for ${symbols.size} symbols")
            
            if (apiKey.isNullOrBlank() || apiSecret.isNullOrBlank()) {
                SystemLogger.warn("⚠️ BUILD #241: CoinbaseSandboxTickProvider: No API keys provided - using public feed only")
            }
            
            val request = Request.Builder()
                .url(WS_URL)
                .build()
            
            webSocket = client.newWebSocket(request, CoinbaseWebSocketListener())
            
            // Subscribe after connection
            connected = true
            SystemLogger.system("✅ BUILD #241: CoinbaseSandboxTickProvider connected")
            true
            
        } catch (e: Exception) {
            SystemLogger.error("❌ BUILD #241: CoinbaseSandboxTickProvider connection failed: ${e.message}", e)
            trackError()
            connected = false
            false
        }
    }
    
    override fun getTickStream(): Flow<UniversalTick> = _tickStream.asSharedFlow()
    
    override suspend fun disconnect() {
        SystemLogger.system("🔌 BUILD #241: Disconnecting CoinbaseSandboxTickProvider")
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        connected = false
    }
    
    override fun getTickSource(): TickSource = TickSource.TESTNET
    
    /**
     * WebSocket listener for Coinbase ticker channel.
     */
    private inner class CoinbaseWebSocketListener : WebSocketListener() {
        
        override fun onOpen(webSocket: WebSocket, response: Response) {
            SystemLogger.system("🔌 BUILD #241: Coinbase WebSocket opened")
            
            // Subscribe to ticker channel for our symbols
            val productIds = symbols.map { 
                // Convert "BTC/USD" → "BTC-USD" (Coinbase format)
                it.replace("/", "-")
            }
            
            val subscribe = JSONObject().apply {
                put("type", "subscribe")
                put("product_ids", productIds)
                put("channels", listOf("ticker"))
            }
            
            webSocket.send(subscribe.toString())
            SystemLogger.system("📡 BUILD #241: Subscribed to Coinbase ticker for ${productIds.size} symbols")
        }
        
        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val json = JSONObject(text)
                val type = json.optString("type")
                
                if (type == "ticker") {
                    val productId = json.getString("product_id")
                    val symbol = productId.replace("-", "/")  // "BTC-USD" → "BTC/USD"
                    
                    val tick = UniversalTick(
                        exchange = "coinbase-sandbox",
                        symbol = symbol,
                        price = json.getDouble("price"),
                        volume = json.optDouble("volume_24h", 0.0),
                        bid = json.optDouble("best_bid", 0.0),
                        ask = json.optDouble("best_ask", 0.0),
                        timestamp = System.currentTimeMillis(),
                        source = TickSource.TESTNET
                    )
                    
                    trackTick()
                    _tickStream.tryEmit(tick)
                    
                    SystemLogger.d(TAG, "📊 BUILD #241: Coinbase tick $symbol @ ${tick.price}")
                }
                
            } catch (e: Exception) {
                SystemLogger.error("❌ BUILD #241: Coinbase message parse error: ${e.message}", e)
                trackError()
            }
        }
        
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            SystemLogger.error("❌ BUILD #241: Coinbase WebSocket failure: ${t.message}", t)
            trackError()
            connected = false
        }
        
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            SystemLogger.system("🔌 BUILD #241: Coinbase WebSocket closed: $reason")
            connected = false
        }
    }
}
