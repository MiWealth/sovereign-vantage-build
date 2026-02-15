package com.miwealth.sovereignvantage.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.miwealth.sovereignvantage.core.trading.TradingSystem
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

/**
 * SOVEREIGN VANTAGE V5.5.17 "ARTHUR EDITION"
 * TRADING SERVICE
 * 
 * Background foreground service for continuous AI trading operations.
 * 
 * Features:
 * - Real-time price feed collection from WebSocket
 * - Price data routing to TradingSystem (swing + scalping)
 * - AI signal generation and execution
 * - STAHL Stair Stop™ monitoring
 * - DHT network participation
 * 
 * Data Flow:
 * PriceFeedService.priceTicks → TradingSystem.onPriceUpdateWithSpread()
 * PriceFeedService.ohlcvBars  → TradingSystem.onPriceUpdate()
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO: Arthur Iain McManus (1966-2025)
 * 
 * Dedicated to Cathryn for tolerating me and my quirks. 💘
 */
@AndroidEntryPoint
class TradingService : Service() {
    
    companion object {
        const val CHANNEL_ID = "sovereign_vantage_trading"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.miwealth.sovereignvantage.START_TRADING"
        const val ACTION_STOP = "com.miwealth.sovereignvantage.STOP_TRADING"
        const val ACTION_START_SCALPING = "com.miwealth.sovereignvantage.START_SCALPING"
        const val ACTION_STOP_SCALPING = "com.miwealth.sovereignvantage.STOP_SCALPING"
        
        // Extra keys
        const val EXTRA_EXCHANGE = "exchange"
        const val EXTRA_SYMBOLS = "symbols"
        
        private const val TAG = "TradingService"
        
        fun startService(context: Context, exchange: String = "kraken", symbols: List<String> = defaultSymbols()) {
            val intent = Intent(context, TradingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_EXCHANGE, exchange)
                putStringArrayListExtra(EXTRA_SYMBOLS, ArrayList(symbols))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, TradingService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
        
        fun startScalping(context: Context) {
            val intent = Intent(context, TradingService::class.java).apply {
                action = ACTION_START_SCALPING
            }
            context.startService(intent)
        }
        
        fun stopScalping(context: Context) {
            val intent = Intent(context, TradingService::class.java).apply {
                action = ACTION_STOP_SCALPING
            }
            context.startService(intent)
        }
        
        private fun defaultSymbols() = listOf(
            "BTC/USD", "ETH/USD", "SOL/USD", "XRP/USD", "ADA/USD"
        )
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    
    // Core components
    private lateinit var priceFeedService: PriceFeedService
    private lateinit var tradingSystem: TradingSystem
    private lateinit var notificationService: NotificationService
    
    // Price feed collection jobs
    private var priceTickCollectorJob: Job? = null
    private var ohlcvBarCollectorJob: Job? = null
    private var systemStateCollectorJob: Job? = null
    private var scalpingEventCollectorJob: Job? = null
    
    // State
    private var isTrading = false
    private var preferredExchange = "kraken"
    private var subscribedSymbols = listOf<String>()
    
    // Stats for notification (updated from SystemState)
    private var activeSignals = 0
    private var todayTrades = 0
    private var todayPnL = 0.0
    private var scalpingActive = false
    private var scalpsToday = 0
    private var scalpingPnL = 0.0
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
        
        // Initialize components
        priceFeedService = PriceFeedService.getInstance()
        tradingSystem = TradingSystem.getInstance(applicationContext)
        notificationService = NotificationService(applicationContext)
        notificationService.initialize()
        
        logEvent("TradingService created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                preferredExchange = intent.getStringExtra(EXTRA_EXCHANGE) ?: "kraken"
                subscribedSymbols = intent.getStringArrayListExtra(EXTRA_SYMBOLS) ?: ArrayList(defaultSymbols())
                startTradingEngine()
            }
            ACTION_STOP -> stopTradingEngine()
            ACTION_START_SCALPING -> {
                if (isTrading) {
                    tradingSystem.startScalping()
                    logEvent("Scalping started via service action")
                }
            }
            ACTION_STOP_SCALPING -> {
                tradingSystem.stopScalping()
                logEvent("Scalping stopped via service action")
            }
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        stopTradingEngine()
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }
    
    // ========================================================================
    // TRADING ENGINE LIFECYCLE
    // ========================================================================
    
    private fun startTradingEngine() {
        if (isTrading) return
        
        isTrading = true
        startForeground(NOTIFICATION_ID, createNotification())
        
        logEvent("Starting trading engine with exchange=$preferredExchange, symbols=$subscribedSymbols")
        
        // Start all components
        serviceScope.launch {
            // 1. Connect to price feeds
            connectPriceFeeds()
            
            // 2. Start price data collectors (routes to TradingSystem)
            startPriceCollectors()
            
            // 3. Start system state observer (updates notification)
            startSystemStateCollector()
            
            // 4. Start scalping event collector (routes to NotificationService)
            startScalpingEventCollector()
            
            // 5. Start supplementary loops
            launch { connectionHealthLoop() }
            launch { notificationUpdateLoop() }
        }
        
        logEvent("Trading engine started")
    }
    
    private fun stopTradingEngine() {
        if (!isTrading) return
        
        isTrading = false
        
        // Stop collectors
        priceTickCollectorJob?.cancel()
        ohlcvBarCollectorJob?.cancel()
        systemStateCollectorJob?.cancel()
        scalpingEventCollectorJob?.cancel()
        
        // Disconnect price feeds
        priceFeedService.disconnect()
        
        // Stop scalping if active
        tradingSystem.stopScalping()
        
        // Cancel all coroutines
        serviceScope.coroutineContext.cancelChildren()
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        
        logEvent("Trading engine stopped")
    }
    
    // ========================================================================
    // PRICE FEED CONNECTION & COLLECTION
    // ========================================================================
    
    private fun connectPriceFeeds() {
        // Connect to the preferred exchange
        priceFeedService.connect(preferredExchange)
        
        // Subscribe to symbols
        priceFeedService.subscribe(subscribedSymbols, preferredExchange)
        
        logEvent("Connected to $preferredExchange, subscribed to ${subscribedSymbols.size} symbols")
    }
    
    private fun startPriceCollectors() {
        // Collector 1: Price Ticks (real-time bid/ask/last) → TradingSystem
        // Used primarily for scalping with spread data
        priceTickCollectorJob = serviceScope.launch {
            priceFeedService.priceTicks.collect { tick ->
                try {
                    // Route to TradingSystem with spread data
                    // For ticks, we use last price as OHLC (since it's a single point)
                    tradingSystem.onPriceUpdateWithSpread(
                        symbol = tick.symbol,
                        open = tick.last,
                        high = tick.last,
                        low = tick.last,
                        close = tick.last,
                        volume = tick.volume,
                        bid = tick.bid,
                        ask = tick.ask
                    )
                } catch (e: Exception) {
                    logError("Price tick processing error: ${e.message}")
                }
            }
        }
        
        // Collector 2: OHLCV Bars (1-minute candles) → TradingSystem
        // Used for indicator calculations and swing trading
        ohlcvBarCollectorJob = serviceScope.launch {
            priceFeedService.ohlcvBars.collect { bar ->
                try {
                    // Route to TradingSystem for indicator calculations
                    tradingSystem.onPriceUpdate(
                        symbol = bar.symbol,
                        open = bar.open,
                        high = bar.high,
                        low = bar.low,
                        close = bar.close,
                        volume = bar.volume
                    )
                } catch (e: Exception) {
                    logError("OHLCV bar processing error: ${e.message}")
                }
            }
        }
        
        logEvent("Price collectors started")
    }
    
    private fun startSystemStateCollector() {
        // Observe TradingSystem state for notification updates
        systemStateCollectorJob = serviceScope.launch {
            tradingSystem.systemState.collect { state ->
                // Update local stats for notification
                activeSignals = state.activeSignalCount
                todayTrades = state.tradesExecutedToday
                todayPnL = state.portfolioValue - (state.initialPortfolioValue ?: 10000.0)
                scalpingActive = state.scalpingActive
                scalpsToday = state.scalpsToday
                scalpingPnL = state.scalpingPnlToday
            }
        }
    }
    
    private fun startScalpingEventCollector() {
        // Observe ScalpingEngine events for push notifications
        scalpingEventCollectorJob = serviceScope.launch {
            tradingSystem.getScalpingEvents()?.collect { event ->
                try {
                    // Route scalping events to notification service
                    notificationService.handleScalpingEvent(event)
                } catch (e: Exception) {
                    logError("Scalping event notification error: ${e.message}")
                }
            }
        }
        
        logEvent("Scalping event collector started")
    }
    
    // ========================================================================
    // HEALTH & MONITORING LOOPS
    // ========================================================================
    
    private suspend fun connectionHealthLoop() {
        while (isTrading) {
            try {
                // Monitor connection health and reconnect if needed
                priceFeedService.krakenState.first().let { state ->
                    if (state is PriceFeedState.Disconnected || state is PriceFeedState.Error) {
                        logEvent("Kraken connection lost, reconnecting...")
                        priceFeedService.connectKraken()
                    }
                }
                delay(30_000) // Check every 30 seconds
            } catch (e: Exception) {
                logError("Connection health check error: ${e.message}")
                delay(30_000)
            }
        }
    }
    
    private suspend fun notificationUpdateLoop() {
        while (isTrading) {
            try {
                updateNotification()
                delay(15_000) // Update notification every 15 seconds
            } catch (e: Exception) {
                logError("Notification update error: ${e.message}")
            }
        }
    }
    
    // ========================================================================
    // NOTIFICATION
    // ========================================================================
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Trading Engine",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Sovereign Vantage AI Trading Engine"
                setShowBadge(false)
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, TradingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        
        // Format P&L
        val swingPnlText = if (todayPnL >= 0) "+$${String.format("%.2f", todayPnL)}" 
                          else "-$${String.format("%.2f", -todayPnL)}"
        
        // Build content text
        val contentText = buildString {
            append("$activeSignals signals • $todayTrades trades • $swingPnlText")
            if (scalpingActive) {
                val scalpPnlText = if (scalpingPnL >= 0) "+${String.format("%.2f", scalpingPnL)}%"
                                   else "${String.format("%.2f", scalpingPnL)}%"
                append("\n⚡ Scalping: $scalpsToday trades • $scalpPnlText")
            }
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sovereign Vantage Trading Active")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setSmallIcon(android.R.drawable.ic_menu_manage) // Replace with actual icon
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification())
    }
    
    // ========================================================================
    // WAKE LOCK
    // ========================================================================
    
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SovereignVantage::TradingWakeLock"
        ).apply {
            acquire(10 * 60 * 1000L) // 10 minutes max, will be renewed
        }
    }
    
    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }
    
    // ========================================================================
    // LOGGING
    // ========================================================================
    
    private fun logEvent(message: String) {
        android.util.Log.i(TAG, message)
    }
    
    private fun logError(message: String) {
        android.util.Log.e(TAG, message)
    }
}
