package com.miwealth.sovereignvantage.core.exchange

import android.util.Log
import com.miwealth.sovereignvantage.core.network.SharedHttpClient
import com.miwealth.sovereignvantage.core.utils.SystemLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * SOVEREIGN VANTAGE V5.18.0 "ARTHUR EDITION"
 * BINANCE PUBLIC PRICE FEED
 *
 * Fetches real-time(ish) prices from Binance's PUBLIC REST API.
 * NO API key, NO credentials, NO account required.
 *
 * Endpoints used:
 *   GET https://api.binance.com/api/v3/ticker/24hr          (price + 24h change)
 *   GET https://api.binance.com/api/v3/klines?symbol=X&...  (OHLCV candles)
 *
 * Prices may be slightly delayed vs WebSocket but are perfectly
 * adequate for paper trading, demo mode, and chart display.
 *
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

data class PublicPriceTick(
    val symbol: String,          // "BTC/USDT"
    val binanceSymbol: String,   // "BTCUSDT"
    val last: Double,
    val bid: Double,
    val ask: Double,
    val volume24h: Double,
    val change24hPercent: Double,
    val high24h: Double,
    val low24h: Double,
    val timestamp: Long
)

data class OHLCVCandle(
    val openTime: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val closeTime: Long
)

class BinancePublicPriceFeed(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    companion object {
        private const val TAG = "BinancePublicPriceFeed"
        private const val BASE_URL = "https://api.binance.com/api/v3"
        private const val PRICE_POLL_INTERVAL_MS = 5_000L   // 5 seconds
        private const val CANDLE_POLL_INTERVAL_MS = 30_000L  // 30 seconds

        // Standard trading pairs we always want prices for
        val DEFAULT_SYMBOLS = listOf(
            "BTC/USDT", "ETH/USDT", "SOL/USDT", "XRP/USDT", "DOGE/USDT",
            "ADA/USDT", "AVAX/USDT", "DOT/USDT", "LINK/USDT", "MATIC/USDT",
            "BNB/USDT", "LTC/USDT"
        )

        // Map SV symbol format to Binance format
        fun toBinanceSymbol(svSymbol: String): String {
            return svSymbol.replace("/", "")
        }

        // Map Binance format back to SV format
        fun toSVSymbol(binanceSymbol: String): String {
            // Find the quote asset (USDT, USDC, BTC, etc.)
            val quoteAssets = listOf("USDT", "USDC", "BUSD", "BTC", "ETH", "USD")
            for (quote in quoteAssets) {
                if (binanceSymbol.endsWith(quote) && binanceSymbol.length > quote.length) {
                    val base = binanceSymbol.substring(0, binanceSymbol.length - quote.length)
                    return "$base/$quote"
                }
            }
            return binanceSymbol
        }

        // Binance kline interval strings
        fun toKlineInterval(timeframeMinutes: Int): String = when (timeframeMinutes) {
            1 -> "1m"
            5 -> "5m"
            15 -> "15m"
            60 -> "1h"
            240 -> "4h"
            1440 -> "1d"
            else -> "1h"
        }

        @Volatile
        private var instance: BinancePublicPriceFeed? = null

        fun getInstance(): BinancePublicPriceFeed {
            return instance ?: synchronized(this) {
                instance ?: BinancePublicPriceFeed().also { instance = it }
            }
        }
    }

    private val client = SharedHttpClient.fastClient

    // Latest prices - symbol -> tick
    private val _latestPrices = MutableStateFlow<Map<String, PublicPriceTick>>(emptyMap())
    val latestPrices: StateFlow<Map<String, PublicPriceTick>> = _latestPrices.asStateFlow()

    // Price ticks stream for subscribers
    private val _priceTicks = MutableSharedFlow<PublicPriceTick>(
        replay = 1,
        extraBufferCapacity = 100
    )
    val priceTicks: SharedFlow<PublicPriceTick> = _priceTicks.asSharedFlow()

    // Candle data - symbol -> timeframe -> candles
    private val _candleData = MutableStateFlow<Map<String, List<OHLCVCandle>>>(emptyMap())
    val candleData: StateFlow<Map<String, List<OHLCVCandle>>> = _candleData.asStateFlow()

    // BUILD #240: Emit symbol+candle pairs so coordinator gets real OHLCV per symbol.
    // Gives DQN genuine high/low/open/close with actual wicks — not flat lines.
    private val _ohlcvCandles = MutableSharedFlow<Pair<String, OHLCVCandle>>(extraBufferCapacity = 200)
    val ohlcvCandles: SharedFlow<Pair<String, OHLCVCandle>> = _ohlcvCandles.asSharedFlow()

    // Feed status
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var pricePollJob: Job? = null
    private var candlePollJob: Job? = null
    private var subscribedSymbols = DEFAULT_SYMBOLS.toMutableList()
    private var candleTimeframe = 1   // BUILD #240: 1-minute candles for DQN signal quality

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Start polling Binance for prices.
     * @param symbols List of SV-format symbols e.g. ["BTC/USDT", "ETH/USDT"]
     */
    fun start(symbols: List<String> = DEFAULT_SYMBOLS) {
        if (_isRunning.value) return

        subscribedSymbols = symbols.toMutableList()
        _isRunning.value = true

        SystemLogger.i(TAG, "🚀 Starting Binance public price feed for ${symbols.size} symbols: $symbols")

        pricePollJob = scope.launch {
            var iteration = 0
            SystemLogger.i(TAG, "🔄 BUILD #130: Price poll job launched, entering loop...")
            while (isActive) {
                iteration++
                SystemLogger.i(TAG, "🔄 BUILD #130: Poll iteration #$iteration - calling fetchPrices()")
                try {
                    fetchPrices()
                    SystemLogger.i(TAG, "✅ BUILD #130: fetchPrices() completed for iteration #$iteration")
                } catch (e: Exception) {
                    SystemLogger.w(TAG, "⚠️ BUILD #130: Poll iteration #$iteration failed: ${e.message}")
                }
                SystemLogger.d(TAG, "⏳ BUILD #130: Sleeping for ${PRICE_POLL_INTERVAL_MS}ms before next poll...")
                delay(PRICE_POLL_INTERVAL_MS)
            }
            SystemLogger.w(TAG, "⚠️ BUILD #130: Price poll loop EXITED (isActive=false)")
        }

        candlePollJob = scope.launch {
            // Small delay to stagger with price fetch
            delay(2000)
            while (isActive) {
                try {
                    fetchCandlesForAll()
                } catch (e: Exception) {
                    SystemLogger.w(TAG, "⚠️ Candle fetch failed (will retry): ${e.message}")
                }
                delay(CANDLE_POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * Stop polling.
     */
    fun stop() {
        pricePollJob?.cancel()
        candlePollJob?.cancel()
        _isRunning.value = false
        Log.i(TAG, "Binance public price feed stopped")
    }

    /**
     * Set candle timeframe for chart data.
     * @param minutes Timeframe in minutes (1, 5, 15, 60, 240, 1440)
     */
    fun setCandleTimeframe(minutes: Int) {
        candleTimeframe = minutes
        // Immediately fetch new candles for the new timeframe
        scope.launch { fetchCandlesForAll() }
    }

    /**
     * Get latest price for a symbol. Returns null if not yet fetched.
     */
    fun getPrice(symbol: String): Double? {
        return _latestPrices.value[symbol]?.last
    }

    /**
     * Get latest tick for a symbol.
     */
    fun getTick(symbol: String): PublicPriceTick? {
        return _latestPrices.value[symbol]
    }

    /**
     * Get candles for a symbol.
     */
    fun getCandles(symbol: String): List<OHLCVCandle> {
        return _candleData.value[symbol] ?: emptyList()
    }

    /**
     * One-shot price fetch for a single symbol.
     * Useful when you need a price immediately without starting the feed.
     */
    suspend fun fetchSinglePrice(symbol: String): Double? {
        return try {
            val binanceSymbol = toBinanceSymbol(symbol)
            val url = "$BASE_URL/ticker/price?symbol=$binanceSymbol"
            val request = Request.Builder().url(url).build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val json = JSONObject(response.body?.string() ?: return@withContext null)
                    json.getDouble("price")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Single price fetch failed for $symbol: ${e.message}")
            null
        }
    }

    /**
     * One-shot candle fetch for a symbol.
     */
    suspend fun fetchCandles(
        symbol: String,
        timeframeMinutes: Int = 60,
        limit: Int = 100
    ): List<OHLCVCandle> {
        return try {
            val binanceSymbol = toBinanceSymbol(symbol)
            val interval = toKlineInterval(timeframeMinutes)
            val url = "$BASE_URL/klines?symbol=$binanceSymbol&interval=$interval&limit=$limit"
            val request = Request.Builder().url(url).build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()
                    val array = JSONArray(response.body?.string() ?: return@withContext emptyList())
                    parseKlines(array)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Candle fetch failed for $symbol: ${e.message}")
            emptyList()
        }
    }

    /**
     * BUILD #258: Historical candle bootstrap - fetch 500 1-minute candles for rapid DQN training.
     * This creates a "transceiver" system: pre-load historical data at startup, then seamlessly
     * transition to real-time feed. DQN gets 8+ hours of market context immediately, enabling
     * intelligent 60%+ confidence signals from minute one instead of waiting 2+ minutes for
     * 20 candles to accumulate.
     * 
     * Mike's brilliant idea: "Why wait for real-time data when we can bootstrap with history?"
     */
    suspend fun fetchHistoricalKlinesForBootstrap(
        symbols: List<String>,
        limit: Int = 500
    ): Map<String, List<OHLCVCandle>> {
        val results = mutableMapOf<String, List<OHLCVCandle>>()
        
        SystemLogger.system("🚀 BUILD #258: Bootstrapping historical candles for ${symbols.size} symbols (${limit} candles each)")
        val startTime = System.currentTimeMillis()
        
        for (symbol in symbols) {
            try {
                val binanceSymbol = toBinanceSymbol(symbol)
                val url = "$BASE_URL/klines?symbol=$binanceSymbol&interval=1m&limit=$limit"
                val request = Request.Builder().url(url).build()
                
                val candles = withContext(Dispatchers.IO) {
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            SystemLogger.error("❌ BUILD #258: HTTP ${response.code} for $symbol bootstrap", null)
                            return@withContext emptyList()
                        }
                        val array = JSONArray(response.body?.string() ?: return@withContext emptyList())
                        parseKlines(array)
                    }
                }
                
                results[symbol] = candles
                SystemLogger.system("✅ BUILD #258: $symbol bootstrap: ${candles.size} candles loaded")
                
            } catch (e: Exception) {
                SystemLogger.error("❌ BUILD #258: Bootstrap failed for $symbol: ${e.message}", e)
                results[symbol] = emptyList()
            }
        }
        
        val elapsed = System.currentTimeMillis() - startTime
        SystemLogger.system("✅ BUILD #258: Historical bootstrap complete in ${elapsed}ms — ${results.values.sumOf { it.size }} total candles")
        
        return results
    }

    // =========================================================================
    // INTERNALS
    // =========================================================================

    private suspend fun fetchPrices() {
        SystemLogger.i(TAG, "🔍 BUILD #130: fetchPrices() START - ${subscribedSymbols.size} symbols")
        
        // Batch fetch: get all 24hr tickers in one call (more efficient)
        // Then filter to only our subscribed symbols
        val binanceSymbols = subscribedSymbols.map { toBinanceSymbol(it) }
        SystemLogger.d(TAG, "🔍 BUILD #130: Converted to Binance symbols: $binanceSymbols")

        // Use individual ticker calls if few symbols, batch if many
        if (binanceSymbols.size <= 5) {
            SystemLogger.i(TAG, "🔍 BUILD #130: Using individual ticker calls (${binanceSymbols.size} <= 5)")
            // Individual calls - lighter weight
            for (i in subscribedSymbols.indices) {
                fetchSingleTicker(subscribedSymbols[i], binanceSymbols[i])
            }
        } else {
            SystemLogger.i(TAG, "🔍 BUILD #130: Using batch ticker call (${binanceSymbols.size} > 5)")
            // Batch call - one request for all
            fetchBatchTickers(subscribedSymbols, binanceSymbols)
        }
        
        SystemLogger.i(TAG, "🔍 BUILD #130: fetchPrices() END")
    }

    private suspend fun fetchSingleTicker(svSymbol: String, binanceSymbol: String) {
        try {
            SystemLogger.d(TAG, "🔍 BUILD #130: fetchSingleTicker($svSymbol, $binanceSymbol)")
            val url = "$BASE_URL/ticker/24hr?symbol=$binanceSymbol"
            SystemLogger.d(TAG, "🔍 BUILD #130: HTTP GET $url")
            val request = Request.Builder().url(url).build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    SystemLogger.i(TAG, "✅ BUILD #130: HTTP ${response.code} for $svSymbol")
                    
                    if (!response.isSuccessful) {
                        SystemLogger.w(TAG, "⚠️ BUILD #130: HTTP error ${response.code} - ${response.message}")
                        return@withContext
                    }
                    
                    val bodyString = response.body?.string()
                    if (bodyString == null) {
                        SystemLogger.w(TAG, "⚠️ BUILD #130: Response body is null")
                        return@withContext
                    }
                    
                    SystemLogger.d(TAG, "💰 BUILD #130: Response body (first 100 chars): ${bodyString.take(100)}")
                    val json = JSONObject(bodyString)

                    val tick = PublicPriceTick(
                        symbol = svSymbol,
                        binanceSymbol = binanceSymbol,
                        last = json.optDouble("lastPrice", 0.0),
                        bid = json.optDouble("bidPrice", 0.0),
                        ask = json.optDouble("askPrice", 0.0),
                        volume24h = json.optDouble("volume", 0.0),
                        change24hPercent = json.optDouble("priceChangePercent", 0.0),
                        high24h = json.optDouble("highPrice", 0.0),
                        low24h = json.optDouble("lowPrice", 0.0),
                        timestamp = System.currentTimeMillis()
                    )

                    SystemLogger.i(TAG, "💰 BUILD #130: Created tick for $svSymbol: price=${tick.last}")
                    updatePrice(tick)
                    SystemLogger.i(TAG, "✅ BUILD #130: updatePrice() called for $svSymbol")
                }
            }
        } catch (e: Exception) {
            SystemLogger.e(TAG, "❌ BUILD #130: fetchSingleTicker failed for $svSymbol: ${e.message}", e)
        }
    }

    private suspend fun fetchBatchTickers(svSymbols: List<String>, binanceSymbols: List<String>) {
        try {
            SystemLogger.d(TAG, "🔍 BUILD #130: fetchBatchTickers for ${svSymbols.size} symbols")
            // Build symbols parameter: ["BTCUSDT","ETHUSDT",...]
            val symbolsJson = binanceSymbols.joinToString(",") { "\"$it\"" }
            val url = "$BASE_URL/ticker/24hr?symbols=[$symbolsJson]"
            SystemLogger.d(TAG, "🔍 BUILD #130: Batch HTTP GET $url")
            val request = Request.Builder().url(url).build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    SystemLogger.i(TAG, "✅ BUILD #130: Batch HTTP ${response.code}")
                    
                    if (!response.isSuccessful) {
                        SystemLogger.w(TAG, "⚠️ BUILD #130: Batch ticker failed: ${response.code} - ${response.message}")
                        return@withContext
                    }
                    
                    val bodyString = response.body?.string()
                    if (bodyString == null) {
                        SystemLogger.w(TAG, "⚠️ BUILD #130: Batch response body is null")
                        return@withContext
                    }
                    
                    SystemLogger.d(TAG, "💰 BUILD #130: Batch response length: ${bodyString.length} chars")
                    val array = JSONArray(bodyString)
                    SystemLogger.i(TAG, "✅ BUILD #130: Parsed ${array.length()} tickers from batch")

                    for (i in 0 until array.length()) {
                        val json = array.getJSONObject(i)
                        val bSymbol = json.getString("symbol")
                        val svSymbol = toSVSymbol(bSymbol)

                        val tick = PublicPriceTick(
                            symbol = svSymbol,
                            binanceSymbol = bSymbol,
                            last = json.optDouble("lastPrice", 0.0),
                            bid = json.optDouble("bidPrice", 0.0),
                            ask = json.optDouble("askPrice", 0.0),
                            volume24h = json.optDouble("volume", 0.0),
                            change24hPercent = json.optDouble("priceChangePercent", 0.0),
                            high24h = json.optDouble("highPrice", 0.0),
                            low24h = json.optDouble("lowPrice", 0.0),
                            timestamp = System.currentTimeMillis()
                        )

                        SystemLogger.d(TAG, "💰 BUILD #130: Batch tick[$i]: $svSymbol = ${tick.last}")
                        updatePrice(tick)
                    }
                    
                    SystemLogger.i(TAG, "✅ BUILD #130: Batch processing complete")
                }
            }
        } catch (e: Exception) {
            SystemLogger.e(TAG, "❌ BUILD #130: Batch ticker fetch failed: ${e.message}", e)
        }
    }

    private suspend fun fetchCandlesForAll() {
        // Fetch candles for the first 5 subscribed symbols (most likely viewed)
        val symbolsToFetch = subscribedSymbols.take(5)
        for (symbol in symbolsToFetch) {
            try {
                val candles = fetchCandles(symbol, candleTimeframe, 100)
                if (candles.isNotEmpty()) {
                    val current = _candleData.value.toMutableMap()
                    current[symbol] = candles
                    _candleData.value = current
                    // BUILD #240: Emit symbol+candle so coordinator gets real OHLCV
                    candles.lastOrNull()?.let { latest ->
                        _ohlcvCandles.tryEmit(Pair(symbol, latest))
                    }
                }
            } catch (e: Exception) {
                SystemLogger.w(TAG, "⚠️ Candle fetch failed for $symbol: ${e.message}")
            }
            // Small delay between requests to avoid rate limiting
            delay(200)
        }
    }

    private suspend fun updatePrice(tick: PublicPriceTick) {
        val updated = _latestPrices.value.toMutableMap()
        updated[tick.symbol] = tick
        _latestPrices.value = updated
        
        SystemLogger.i(TAG, "📡 BUILD #130: About to emit price tick for ${tick.symbol} = ${tick.last}")
        _priceTicks.emit(tick)
        SystemLogger.i(TAG, "✅ BUILD #130: Price tick emitted successfully! ${tick.symbol} = $${String.format("%.2f", tick.last)} (collectors: ${_priceTicks.subscriptionCount.value})")
    }

    private fun parseKlines(array: JSONArray): List<OHLCVCandle> {
        val candles = mutableListOf<OHLCVCandle>()
        for (i in 0 until array.length()) {
            val kline = array.getJSONArray(i)
            candles.add(
                OHLCVCandle(
                    openTime = kline.getLong(0),
                    open = kline.getString(1).toDouble(),
                    high = kline.getString(2).toDouble(),
                    low = kline.getString(3).toDouble(),
                    close = kline.getString(4).toDouble(),
                    volume = kline.getString(5).toDouble(),
                    closeTime = kline.getLong(6)
                )
            )
        }
        return candles
    }
}
