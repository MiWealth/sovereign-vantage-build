package com.miwealth.sovereignvantage.core.trading

import android.content.Context
import android.util.Log
import com.miwealth.sovereignvantage.core.*
import com.miwealth.sovereignvantage.core.ai.*
import com.miwealth.sovereignvantage.core.exchange.ai.AIConnectionManager
import com.miwealth.sovereignvantage.core.exchange.ai.AIExchangeAdapterFactory
import com.miwealth.sovereignvantage.core.exchange.ai.TradingExecutionMode
import com.miwealth.sovereignvantage.core.exchange.*
import com.miwealth.sovereignvantage.core.security.pqc.hybrid.*
import com.miwealth.sovereignvantage.core.trading.assets.*
import com.miwealth.sovereignvantage.core.trading.engine.*
import com.miwealth.sovereignvantage.core.trading.routing.*
import com.miwealth.sovereignvantage.core.trading.scalping.*
import com.miwealth.sovereignvantage.service.NotificationService
import com.miwealth.sovereignvantage.service.UnifiedPriceFeedService
import com.miwealth.sovereignvantage.service.UnifiedPriceFeedEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * SOVEREIGN VANTAGE V5.17.0 "ARTHUR EDITION"
 * UNIFIED TRADING SYSTEM
 * 
 * This is the master facade that initializes and coordinates all trading components:
 * - Exchange Registry (PQC-enabled Kraken, Binance, Coinbase, Bybit, OKX connectors)
 * - Unified Price Feed Service (WebSocket streaming)
 * - Smart Order Router (multi-exchange routing with 8 strategies)
 * - AI Board of Directors
 * - Trading Coordinator
 * - Risk Manager
 * - Position Manager
 * - Order Executor
 * - STAHL Stair Stop™
 * - Asset Registry (dynamic symbol loading)
 * - Scalping Engine (high-frequency trading)
 * - AI Board STAHL Indicator Integration (V5.17.0)
 * 
 * V5.17.0 CHANGES:
 * - Added TradingIndicatorIntegration for AI Board STAHL preset selection
 * - Real-time indicator calculation from exchange WebSocket feeds
 * - Automatic stair expansion for positions at top stair
 * - MarketContext-aware preset recommendations
 * 
 * V5.17.0 CHANGES:
 * - Fixed placeOrder() to use proper PositionManager methods (openPosition/addToPosition)
 * - Fixed PartialFill handling (was PartiallyFilled)
 * - Fixed Error result handling (exception not message)
 * - Added position manager updates for partial fills
 * 
 * V5.17.0 CHANGES:
 * - TradingSystem integration with ScalpingEngine
 * - Auto start/stop ScalpingEngine when switching to/from SCALPING mode
 * - ScalpingSignal → SmartOrderRouter execution bridge (PQC-secured)
 * - TradingCoordinator pauses during SCALPING mode
 * - Added autoExecute flag to ScalpingConfig for autonomous scalping
 * 
 * V5.17.0 CHANGES:
 * - Integrated SmartOrderRouter for multi-exchange order routing
 * - SmartOrderExecutor bridges SOR to existing OrderExecutor infrastructure
 * - Added dark pool scaffolding for future institutional features
 * - All orders now route through SOR for best execution
 * - Support for Bybit and OKX alongside Kraken, Binance, Coinbase
 * 
 * Provides a single entry point for the UI and services.
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * 
 * Dedicated to Cathryn for tolerating me and my quirks. 💘
 */

class TradingSystem private constructor(
    private val context: Context,
    private val scope: CoroutineScope
) {
    
    companion object {
        private const val TAG = "TradingSystem"
        
        @Volatile
        private var instance: TradingSystem? = null
        
        fun getInstance(context: Context): TradingSystem {
            return instance ?: synchronized(this) {
                instance ?: TradingSystem(
                    context.applicationContext,
                    CoroutineScope(Dispatchers.Default + SupervisorJob())
                ).also { instance = it }
            }
        }
    }
    
    // ========================================================================
    // COMPONENTS
    // ========================================================================
    
    // PQC-Enabled Exchange Infrastructure (V5.17.0)
    private var exchangeRegistry: ExchangeRegistry? = null
    private var priceFeedService: UnifiedPriceFeedService? = null
    private val connectedExchanges = mutableMapOf<String, UnifiedExchangeAdapter>()
    
    // Smart Order Routing (V5.17.0)
    private var smartOrderRouter: SmartOrderRouter? = null
    private var smartOrderExecutor: SmartOrderExecutor? = null
    private var feeOptimizer: FeeOptimizer? = null
    private var slippageProtector: SlippageProtector? = null
    private var orderSplitter: OrderSplitter? = null
    
    // Legacy exchange adapters (fallback - kept for backward compatibility)
    private lateinit var krakenAdapter: KrakenExchangeAdapter
    private lateinit var coinbaseAdapter: CoinbaseExchangeAdapter
    private var activeExchange: ExchangeAdapter? = null
    
    // AI Exchange Interface (V5.17.0)
    private var aiConnectionManager: AIConnectionManager? = null
    private var aiAdapterFactory: AIExchangeAdapterFactory? = null
    private var aiExecutionMode: TradingExecutionMode = TradingExecutionMode.PAPER
    
    // V5.17.0: AI price feed → TradingCoordinator wiring jobs
    private var aiPriceFeedJob: Job? = null
    private var aiOrderBookFeedJob: Job? = null
    
    // Core trading components
    private lateinit var positionManager: PositionManager
    private lateinit var riskManager: RiskManager
    private lateinit var orderExecutor: OrderExecutor
    private lateinit var tradingCoordinator: TradingCoordinator
    
    // BUILD #425: Reference to parent manager for capital management
    internal var tradingSystemManager: com.miwealth.sovereignvantage.core.TradingSystemManager? = null
    // V5.17.0: Sentiment Engine — feeds socialVolume + newsImpact to AI Board via TradingCoordinator
    private val sentimentEngine: SentimentEngine by lazy { SentimentEngine.getInstance(context) }
    
    // V5.18.22: Heartbeat synchronization system
    private var heartbeatCoordinator: HeartbeatCoordinator? = null
    private var tradingHeartbeatAdapter: TradingSystemHeartbeatAdapter? = null
    private var hedgeFundHeartbeatAdapter: HedgeFundHeartbeatAdapter? = null
    private var heartbeatJob: Job? = null
    
    // Scalping engine (high-frequency trading)
    private var scalpingEngine: ScalpingEngine? = null
    
    // AI Board STAHL Indicator Integration (V5.17.0)
    private var indicatorIntegration: TradingIndicatorIntegration? = null
    
    // State
    private val _systemState = MutableStateFlow(SystemState())
    val systemState: StateFlow<SystemState> = _systemState.asStateFlow()
    
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()
    
    // PQC Security state
    private val _pqcEnabled = MutableStateFlow(false)
    val pqcEnabled: StateFlow<Boolean> = _pqcEnabled.asStateFlow()
    
    // Smart Routing state (V5.17.0)
    private val _smartRoutingEnabled = MutableStateFlow(false)
    val smartRoutingEnabled: StateFlow<Boolean> = _smartRoutingEnabled.asStateFlow()
    
    // AI Exchange Interface state (V5.17.0)
    private val _aiExchangeEnabled = MutableStateFlow(false)
    val aiExchangeEnabled: StateFlow<Boolean> = _aiExchangeEnabled.asStateFlow()
    
    // ========================================================================
    // INITIALIZATION
    // ========================================================================
    
    /**
     * Initialize the trading system with API credentials
     */
    suspend fun initialize(
        krakenApiKey: String? = null,
        krakenApiSecret: String? = null,
        coinbaseApiKey: String? = null,
        coinbaseApiSecret: String? = null,
        preferredExchange: String = "kraken"
    ): Result<Unit> {
        return try {
            // Initialize Kraken adapter
            if (krakenApiKey != null && krakenApiSecret != null) {
                krakenAdapter = KrakenExchangeAdapter(
                    apiKey = krakenApiKey,
                    apiSecret = krakenApiSecret
                )
            }
            
            // Initialize Coinbase adapter
            if (coinbaseApiKey != null && coinbaseApiSecret != null) {
                coinbaseAdapter = CoinbaseExchangeAdapter(
                    apiKey = coinbaseApiKey,
                    apiSecret = coinbaseApiSecret
                )
            }
            
            // Select active exchange
            activeExchange = when (preferredExchange.lowercase()) {
                "kraken" -> if (::krakenAdapter.isInitialized) krakenAdapter else null
                "coinbase" -> if (::coinbaseAdapter.isInitialized) coinbaseAdapter else null
                else -> null
            }
            
            if (activeExchange == null) {
                // No exchange configured - use paper trading mode
                updateState { it.copy(paperTradingMode = true) }
            }
            
            // Initialize position manager
            positionManager = PositionManager()
            
            // Initialize risk manager
            riskManager = RiskManager(positionManager)
            riskManager.initialize(10000.0) // Default starting portfolio value
            
            // Initialize order executor
            orderExecutor = OrderExecutor(
                exchangeAdapter = activeExchange ?: createPaperTradingAdapter()
            )
            
            // Initialize trading coordinator
            tradingCoordinator = TradingCoordinator(
                context = context,  // BUILD #366: DQN weight persistence
                orderExecutor = orderExecutor,
                riskManager = riskManager,
                positionManager = positionManager,
                config = TradingCoordinatorConfig(
                    mode = TradingMode.SIGNAL_ONLY,  // Default to signal-only for safety
                    paperTradingMode = activeExchange == null
                ),
                sentimentEngine = sentimentEngine
            )
            
            // Initialize HeartbeatCoordinator (V5.18.22)
            // Synchronizes Trading and Hedge Fund boards with same market snapshot
            initializeHeartbeatSystem()
            
            // Subscribe to events
            subscribeToEvents()
            
            _isInitialized.value = true
            updateState { it.copy(isInitialized = true) }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Initialize for paper trading only (no exchange credentials needed)
     */
    suspend fun initializePaperTrading(startingBalance: Double = 10000.0): Result<Unit> {
        return try {
            positionManager = PositionManager()
            riskManager = RiskManager(positionManager)
            riskManager.initialize(startingBalance)
            
            orderExecutor = OrderExecutor(
                exchangeAdapter = createPaperTradingAdapter()
            )
            
            tradingCoordinator = TradingCoordinator(
                context = context,  // BUILD #366: DQN weight persistence
                orderExecutor = orderExecutor,
                riskManager = riskManager,
                positionManager = positionManager,
                config = TradingCoordinatorConfig(
                    mode = TradingMode.SIGNAL_ONLY,
                    paperTradingMode = true
                ),
                sentimentEngine = sentimentEngine
            )
            
            // Initialize HeartbeatCoordinator (V5.18.22)
            initializeHeartbeatSystem()
            
            subscribeToEvents()
            
            _isInitialized.value = true
            updateState { it.copy(isInitialized = true, paperTradingMode = true) }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ========================================================================
    // AI EXCHANGE INTERFACE INITIALIZATION (V5.17.0)
    // ========================================================================
    
    /**
     * Initialize with AI Exchange Interface via AIConnectionManager.
     * This is the next-generation initialization method that uses AI-driven
     * exchange connectors which learn exchange APIs dynamically.
     *
     * Feature-flagged: Legacy hardcoded connectors continue to operate in
     * parallel until AI exchange path is validated on testnet.
     *
     * V5.17.0: Initial wiring — AIConnectionManager → AIExchangeAdapterFactory → OrderExecutor
     *
     * @param executionMode AI trading execution mode (PAPER, LIVE_AI, etc.)
     * @param exchangeCredentials Map of exchangeId → ExchangeCredentials for AI connectors
     * @param initialBalance Starting balance for paper trading modes
     * @param enableSmartRouting Whether to use SmartOrderRouter with AI adapters
     * @param preferredExchangeId Default exchange ID for order routing
     */
    suspend fun initializeWithAI(
        executionMode: TradingExecutionMode = TradingExecutionMode.PAPER,
        exchangeCredentials: Map<String, com.miwealth.sovereignvantage.core.exchange.ExchangeCredentials> = emptyMap(),
        initialBalance: Double = 10_000.0,
        enableSmartRouting: Boolean = false,
        preferredExchangeId: String = "binance"
    ): Result<Unit> {
        return try {
            aiExecutionMode = executionMode
            
            // Initialize AIConnectionManager
            aiConnectionManager = AIConnectionManager(context)
            
            // Register and connect exchanges
            exchangeCredentials.forEach { (exchangeId, creds) ->
                aiConnectionManager!!.addKnownExchange(
                    exchangeId = exchangeId,
                    credentials = creds,
                    autoConnect = true
                )
            }
            
            // Initialize AIExchangeAdapterFactory
            aiAdapterFactory = AIExchangeAdapterFactory(
                context = context,
                aiConnectionManager = aiConnectionManager
            )
            
            // Create the exchange adapter via the factory
            val aiAdapter = aiAdapterFactory!!.createAdapter(
                mode = executionMode,
                exchangeId = preferredExchangeId,
                initialBalance = initialBalance
            )
            
            val isPaperMode = executionMode == TradingExecutionMode.PAPER ||
                    executionMode == TradingExecutionMode.PAPER_WITH_LIVE_DATA
            
            activeExchange = aiAdapter
            
            // Initialize core trading components
            positionManager = PositionManager()
            riskManager = RiskManager(positionManager)
            riskManager.initialize(initialBalance)
            
            // Initialize OrderExecutor with AI-created adapter
            orderExecutor = OrderExecutor(
                exchangeAdapter = activeExchange!!
            )
            
            // Initialize TradingCoordinator
            tradingCoordinator = TradingCoordinator(
                context = context,  // BUILD #366: DQN weight persistence
                orderExecutor = orderExecutor,
                riskManager = riskManager,
                positionManager = positionManager,
                config = TradingCoordinatorConfig(
                    mode = TradingMode.SIGNAL_ONLY,  // Default safe mode
                    paperTradingMode = isPaperMode
                ),
                sentimentEngine = sentimentEngine
            )
            
            // Start AI health monitoring
            aiConnectionManager!!.startHealthMonitoring()
            
            // AI Board STAHL Indicator Integration
            indicatorIntegration = TradingIndicatorIntegration.getInstance(context)
            indicatorIntegration!!.initialize(_systemState.value.tradingMode)
            subscribeToIndicatorEvents()
            
            // V5.17.0: Wire AIConnectionManager price/orderbook feeds → TradingCoordinator
            // This is the CRITICAL missing link: without this, the coordinator's OODA loop
            // has no price data to analyse and will never generate signals.
            wireAIPriceFeedsToCoordinator(
                symbols = tradingCoordinator.getWatchlist(),
                backfillCandles = (executionMode != TradingExecutionMode.PAPER)
            )
            
            // Subscribe to events
            subscribeToEvents()
            
            _isInitialized.value = true
            _aiExchangeEnabled.value = true
            updateState {
                it.copy(
                    isInitialized = true,
                    paperTradingMode = isPaperMode,
                    activeExchange = when (executionMode) {
                        TradingExecutionMode.LIVE_AI -> "AI:$preferredExchangeId"
                        TradingExecutionMode.PAPER -> "Paper"
                        TradingExecutionMode.PAPER_WITH_LIVE_DATA -> "Paper+LiveData:$preferredExchangeId"
                        TradingExecutionMode.LIVE_HARDCODED -> preferredExchangeId
                    },
                    aiExchangeEnabled = true,
                    aiExecutionMode = executionMode.name
                )
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            // Cleanup on failure
            aiAdapterFactory?.shutdown()
            aiAdapterFactory = null
            aiConnectionManager?.let {
                it.stopHealthMonitoring()
                it.disconnectAll()
            }
            aiConnectionManager = null
            _aiExchangeEnabled.value = false
            Result.failure(e)
        }
    }
    
    // ========================================================================
    // AI PRICE FEED WIRING (V5.17.0)
    // ========================================================================
    
    /**
     * V5.17.0: Wire AIConnectionManager real-time feeds → TradingCoordinator.
     *
     * This bridges the gap that existed since V5.17.0: initializeWithAI() created the
     * AIConnectionManager → OrderExecutor chain for ORDER EXECUTION, but never piped
     * PRICE DATA into the TradingCoordinator's OODA loop. Without this, the coordinator's
     * analysisLoop() never sees hasEnoughData() == true and never generates signals.
     *
     * Two parallel flows are established:
     * 1. subscribeToAllPrices()     → onPriceTick(symbol, price, volume, exchange)
     * 2. subscribeToAllOrderBooks() → onOrderBookUpdate(book)
     *
     * Plus optional historical OHLCV backfill so the coordinator can start analysing
     * immediately (needs 50+ candles before hasEnoughData() returns true).
     *
     * @param symbols Initial watchlist symbols to subscribe
     * @param backfillCandles Whether to fetch historical candles on startup
     */
    private suspend fun wireAIPriceFeedsToCoordinator(
        symbols: List<String>,
        backfillCandles: Boolean = true
    ) {
        val manager = aiConnectionManager ?: return
        
        android.util.Log.i("TradingSystem",
            "V5.17.0: Wiring AI price feeds → TradingCoordinator for ${symbols.size} symbols")
        
        // ------------------------------------------------------------------
        // 1. HISTORICAL BACKFILL: Fetch recent candles so coordinator can
        //    start analysing immediately (needs 50+ candles per symbol).
        //    Uses 100 × 1-minute candles ≈ 1h40m of data per symbol.
        // ------------------------------------------------------------------
        if (backfillCandles) {
            backfillHistoricalOHLCV(manager, symbols, limit = 100, interval = "1m")
        }
        
        // ------------------------------------------------------------------
        // 2. REAL-TIME PRICE TICKS: Stream live prices from all connected
        //    exchanges into the coordinator. Each tick updates the current
        //    candle and cross-exchange price map for arb detection.
        // ------------------------------------------------------------------
        aiPriceFeedJob?.cancel()
        aiPriceFeedJob = scope.launch {
            try {
                manager.subscribeToAllPrices(symbols).collect { tick ->
                    if (_isInitialized.value) {
                        tradingCoordinator.onPriceTick(
                            tick.symbol,
                            tick.last,
                            tick.volume,
                            tick.exchange
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("TradingSystem",
                    "AI price feed error: ${e.message}", e)
            }
        }
        
        // ------------------------------------------------------------------
        // 3. REAL-TIME ORDER BOOKS: Stream depth data from all connected
        //    exchanges. Feeds real best-bid/best-ask into the coordinator's
        //    crossExchangePrices map (replacing the bid=ask=last approximation
        //    that existed before V5.17.0).
        // ------------------------------------------------------------------
        aiOrderBookFeedJob?.cancel()
        aiOrderBookFeedJob = scope.launch {
            try {
                manager.subscribeToAllOrderBooks(symbols).collect { book ->
                    if (_isInitialized.value) {
                        tradingCoordinator.onOrderBookUpdate(book)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("TradingSystem",
                    "AI order book feed error: ${e.message}", e)
            }
        }
        
        android.util.Log.i("TradingSystem",
            "V5.17.0: AI price feeds wired ✓ (${symbols.size} symbols, " +
            "backfill=${backfillCandles})")
    }
    
    /**
     * V5.17.0: Fetch historical OHLCV candles from the best available exchange
     * for each symbol and load them into TradingCoordinator's price buffers.
     *
     * The coordinator requires 50+ candles per symbol before hasEnoughData()
     * returns true. Without backfill, it would take 50+ minutes of live data
     * collection before the first signal could be generated.
     *
     * Fetches in parallel across symbols (but serially per symbol to be kind
     * to exchange rate limits). Failures are logged but non-fatal — the
     * coordinator will eventually accumulate enough data from the live feed.
     */
    private suspend fun backfillHistoricalOHLCV(
        manager: AIConnectionManager,
        symbols: List<String>,
        limit: Int = 100,
        interval: String = "1m"
    ) {
        android.util.Log.i("TradingSystem",
            "V5.17.0: Backfilling $limit × $interval candles for ${symbols.size} symbols")
        
        var successCount = 0
        var failCount = 0
        
        // Parallel fetch with structured concurrency
        coroutineScope {
            symbols.map { symbol ->
                async {
                    try {
                        // Find the best exchange that supports this symbol
                        val exchangeId = manager.getBestExchangeFor(symbol)
                        val connector = exchangeId?.let { manager.getConnector(it) }
                        
                        if (connector == null) {
                            android.util.Log.w("TradingSystem",
                                "No exchange available for backfill: $symbol")
                            failCount++
                            return@async
                        }
                        
                        val candles = connector.getCandles(
                            symbol = symbol,
                            interval = interval,
                            limit = limit
                        )
                        
                        if (candles.isNotEmpty()) {
                            tradingCoordinator.loadHistoricalData(symbol, candles)
                            successCount++
                            android.util.Log.d("TradingSystem",
                                "Backfilled $symbol: ${candles.size} candles from $exchangeId")
                        } else {
                            failCount++
                            android.util.Log.w("TradingSystem",
                                "Empty candle response for $symbol from $exchangeId")
                        }
                    } catch (e: Exception) {
                        failCount++
                        android.util.Log.w("TradingSystem",
                            "Backfill failed for $symbol: ${e.message}")
                    }
                }
            }.awaitAll()
        }
        
        android.util.Log.i("TradingSystem",
            "V5.17.0: Backfill complete — $successCount succeeded, $failCount failed")
    }
    
    /**
     * V5.17.0: Update the AI price feed subscriptions when watchlist changes.
     * Called when setTradingMode() or updateWatchlist() modifies the symbol list.
     * Re-subscribes to prices and order books for the new symbol set.
     */
    suspend fun refreshAIPriceFeeds(symbols: List<String>) {
        if (aiConnectionManager == null || !_aiExchangeEnabled.value) return
        wireAIPriceFeedsToCoordinator(symbols, backfillCandles = true)
    }
    
    // ========================================================================
    // PQC-ENABLED INITIALIZATION (V5.17.0 - with Smart Order Router)
    // ========================================================================
    
    /**
     * Initialize with PQC-enabled exchange connectors via ExchangeRegistry.
     * This is the preferred initialization method for production use.
     * 
     * V5.17.0: Now initializes Smart Order Router for multi-exchange routing.
     * All orders are routed through SmartOrderExecutor for optimal execution.
     * 
     * @param credentialStore Persistent storage for encrypted credentials
     * @param pqcConfig PQC configuration (defaults to MAXIMUM security)
     * @param credentials Map of exchange credentials to connect
     * @param preferredExchange Default exchange for order routing (used as fallback)
     * @param enableSmartRouting Enable smart order routing (default: true)
     * @param routingConfig Configuration for smart order routing
     */
    suspend fun initializeWithPQC(
        credentialStore: ExchangeCredentialStore? = null,
        pqcConfig: HybridPQCConfig = HybridPQCConfig.fromPreset(SecurityPreset.MAXIMUM),
        credentials: Map<SupportedExchange, ExchangeCredentials> = emptyMap(),
        preferredExchange: SupportedExchange = SupportedExchange.KRAKEN,
        enableSmartRouting: Boolean = true,
        routingConfig: RoutingConfig = RoutingConfig()
    ): Result<Unit> {
        return try {
            // Initialize ExchangeRegistry with PQC
            exchangeRegistry = ExchangeRegistry(
                context = context,
                credentialStore = credentialStore ?: throw IllegalStateException("credentialStore required"),
                pqcConfig = pqcConfig
            )
            
            // Initialize price feed service
            priceFeedService = UnifiedPriceFeedService.getInstance(context, exchangeRegistry!!)
            
            // Connect to exchanges with provided credentials
            credentials.forEach { (exchange, creds) ->
                val connector = exchangeRegistry!!.connectExchange(exchange, creds)
                if (connector != null) {
                    val adapter = connector.toExchangeAdapter(scope)
                    connectedExchanges[exchange.id] = adapter
                    priceFeedService!!.addExchange(adapter)
                }
            }
            
            val isPaperMode = connectedExchanges.isEmpty()
            
            // Initialize core trading components
            positionManager = PositionManager()
            riskManager = RiskManager(positionManager)
            riskManager.initialize(10000.0)
            
            // ================================================================
            // SMART ORDER ROUTING SETUP (V5.17.0)
            // ================================================================
            
            if (enableSmartRouting && connectedExchanges.isNotEmpty()) {
                // Initialize SOR components
                feeOptimizer = FeeOptimizer()
                slippageProtector = SlippageProtector()
                orderSplitter = OrderSplitter()
                
                // Create Smart Order Router
                smartOrderRouter = SmartOrderRouter(
                    priceFeedService = priceFeedService!!,
                    feeOptimizer = feeOptimizer!!,
                    slippageProtector = slippageProtector!!,
                    orderSplitter = orderSplitter!!,
                    config = routingConfig,
                    scope = scope
                )
                
                // Register all connected exchanges with the router
                connectedExchanges.forEach { (exchangeId, adapter) ->
                    smartOrderRouter!!.registerExchange(adapter)
                }
                
                // Create SmartOrderExecutor (implements ExchangeAdapter)
                smartOrderExecutor = SmartOrderExecutor(
                    router = smartOrderRouter!!,
                    config = SmartOrderExecutorConfig(
                        defaultStrategy = routingConfig.defaultStrategy
                    ),
                    scope = scope
                )
                smartOrderExecutor!!.setPaperTradingMode(isPaperMode)
                
                // Use SmartOrderExecutor for order execution
                activeExchange = smartOrderExecutor
                _smartRoutingEnabled.value = true
            } else {
                // Fallback to single exchange mode
                activeExchange = connectedExchanges[preferredExchange.id]
                    ?: connectedExchanges.values.firstOrNull()
                    ?: createPaperTradingAdapter()
                _smartRoutingEnabled.value = false
            }
            
            // Initialize OrderExecutor with the chosen adapter
            orderExecutor = OrderExecutor(
                exchangeAdapter = activeExchange!!
            )
            
            tradingCoordinator = TradingCoordinator(
                context = context,  // BUILD #366: DQN weight persistence
                orderExecutor = orderExecutor,
                riskManager = riskManager,
                positionManager = positionManager,
                config = TradingCoordinatorConfig(
                    mode = TradingMode.SIGNAL_ONLY,
                    paperTradingMode = isPaperMode
                ),
                sentimentEngine = sentimentEngine
            )
            
            // Wire price feeds to trading system
            priceFeedService!!.wireToTradingSystem(this)
            
            // Start OHLCV bar generation
            priceFeedService!!.startOHLCVGeneration()
            
            // ================================================================
            // AI BOARD STAHL INDICATOR INTEGRATION (V5.17.0)
            // ================================================================
            
            indicatorIntegration = TradingIndicatorIntegration.getInstance(context)
            indicatorIntegration!!.initialize(_systemState.value.tradingMode)
            
            // Subscribe to AI recommendations for logging/UI
            subscribeToIndicatorEvents()
            
            // Subscribe to events
            subscribeToEvents()
            subscribeToPriceFeedEvents()
            subscribeToSmartRoutingEvents()
            
            _isInitialized.value = true
            _pqcEnabled.value = true
            updateState { 
                it.copy(
                    isInitialized = true, 
                    paperTradingMode = isPaperMode,
                    activeExchange = if (_smartRoutingEnabled.value) "SmartRouter" else activeExchange?.exchangeName,
                    pqcSecurityEnabled = true,
                    smartRoutingEnabled = _smartRoutingEnabled.value,
                    connectedExchangeCount = connectedExchanges.size
                ) 
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Connect to an additional exchange (after initial setup)
     */
    suspend fun connectExchange(
        exchange: SupportedExchange,
        credentials: ExchangeCredentials
    ): Result<UnifiedExchangeAdapter> {
        return try {
            val registry = exchangeRegistry 
                ?: return Result.failure(Exception("ExchangeRegistry not initialized. Call initializeWithPQC first."))
            
            val connector = registry.connectExchange(exchange, credentials)
                ?: return Result.failure(Exception("Failed to create connector for ${exchange.displayName}"))
            
            val adapter = connector.toExchangeAdapter(scope)
            connectedExchanges[exchange.id] = adapter
            priceFeedService?.addExchange(adapter)
            
            // If no active exchange, set this one
            if (activeExchange is PaperTradingAdapter) {
                setActiveExchange(exchange.id)
            }
            
            Result.success(adapter)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Disconnect from an exchange
     */
    suspend fun disconnectExchange(exchangeId: String) {
        connectedExchanges[exchangeId]?.let { adapter ->
            adapter.disconnect()
            priceFeedService?.removeExchange(exchangeId)
            connectedExchanges.remove(exchangeId)
            exchangeRegistry?.disconnectExchange(exchangeId)
        }
    }
    
    /**
     * Set the active exchange for order routing
     */
    fun setActiveExchange(exchangeId: String): Boolean {
        val adapter = connectedExchanges[exchangeId] ?: return false
        activeExchange = adapter
        
        // Update order executor with new exchange
        orderExecutor = OrderExecutor(exchangeAdapter = adapter)
        tradingCoordinator = TradingCoordinator(
            context = context,  // BUILD #366: DQN weight persistence
            orderExecutor = orderExecutor,
            riskManager = riskManager,
            positionManager = positionManager,
            config = TradingCoordinatorConfig(
                mode = _systemState.value.tradingMode,
                paperTradingMode = false
            ),
            sentimentEngine = sentimentEngine
        )
        
        updateState { it.copy(activeExchange = exchangeId, paperTradingMode = false) }
        return true
    }
    
    /**
     * Get list of connected exchanges
     */
    fun getConnectedExchanges(): List<String> = connectedExchanges.keys.toList()
    
    /**
     * Get exchange adapter by ID
     */
    fun getExchangeAdapter(exchangeId: String): UnifiedExchangeAdapter? = connectedExchanges[exchangeId]
    
    /**
     * Get PQC security status for all exchanges
     */
    fun getPQCSecurityStatus(): Map<String, ConnectorSecurityStatus> {
        return exchangeRegistry?.getSecurityStatus() ?: emptyMap()
    }
    
    /**
     * Subscribe to price updates for symbols
     */
    fun subscribeToPrices(symbols: List<String>) {
        priceFeedService?.subscribe(symbols)
        updateState { it.copy(enabledSymbols = symbols) }
    }
    
    /**
     * Get aggregated price for a symbol (best bid/ask across exchanges)
     */
    fun getBestPrice(symbol: String, side: TradeSide): Pair<String, Double>? {
        return priceFeedService?.getBestPriceForOrder(symbol, side)
    }
    
    /**
     * Subscribe to price feed events
     */
    private fun subscribeToPriceFeedEvents() {
        priceFeedService?.let { service ->
            scope.launch {
                service.events.collect { event ->
                    when (event) {
                        is UnifiedPriceFeedEvent.ExchangeConnected -> {
                            android.util.Log.i("TradingSystem", "Exchange connected: ${event.exchangeId}")
                        }
                        is UnifiedPriceFeedEvent.ExchangeDisconnected -> {
                            android.util.Log.w("TradingSystem", "Exchange disconnected: ${event.exchangeId}")
                        }
                        is UnifiedPriceFeedEvent.ExchangeError -> {
                            android.util.Log.e("TradingSystem", "Exchange error (${event.exchangeId}): ${event.error}")
                        }
                        is UnifiedPriceFeedEvent.AllExchangesDisconnected -> {
                            // Potential failover to paper trading or notification
                            android.util.Log.w("TradingSystem", "All exchanges disconnected!")
                        }
                        else -> {}
                    }
                }
            }
            
            // Track connection state
            scope.launch {
                service.isConnected.collect { connected ->
                    updateState { it.copy(exchangeConnected = connected) }
                }
            }
        }
    }
    
    // ========================================================================
    // PUBLIC API - TRADING CONTROL
    // ========================================================================
    
    /**
     * Start autonomous/signal trading
     */
    fun startTrading() {
        if (!_isInitialized.value) {
            throw IllegalStateException("Trading system not initialized")
        }
        sentimentEngine.start()  // V5.17.0: Start sentiment analysis feed
        tradingCoordinator.start()
        startHeartbeat()  // BUILD #173: Start synchronized market snapshots
        updateState { it.copy(isTradingActive = true) }
    }
    
    /**
     * Stop trading
     */
    fun stopTrading() {
        tradingCoordinator.stop()
        sentimentEngine.stop()  // V5.17.0: Stop sentiment analysis feed
        stopHeartbeat()  // BUILD #173: Stop synchronized market snapshots
        updateState { it.copy(isTradingActive = false) }
    }
    
    /**
     * Switch trading mode.
     * 
     * V5.17.0 CHANGES:
     * - Auto start/stop ScalpingEngine when switching to/from SCALPING mode
     * - Pause TradingCoordinator analysis when in SCALPING mode (scalping handles its own signals)
     * - Resume TradingCoordinator when switching back to AUTONOMOUS or SIGNAL_ONLY
     */
    fun setTradingMode(mode: TradingMode) {
        val previousMode = _systemState.value.tradingMode
        
        // Update coordinator mode
        tradingCoordinator.setMode(mode)
        
        // Handle mode-specific transitions
        when (mode) {
            TradingMode.SCALPING -> {
                // Pause coordinator's swing analysis (scalping engine handles its own signals)
                if (previousMode != TradingMode.SCALPING) {
                    tradingCoordinator.stop()
                }
                
                // Auto-start scalping engine if not already active
                if (!_systemState.value.scalpingActive) {
                    startScalping(_systemState.value.scalpingConfig)
                }
            }
            
            TradingMode.AUTONOMOUS, TradingMode.SIGNAL_ONLY -> {
                // Stop scalping if we were in SCALPING mode
                if (previousMode == TradingMode.SCALPING && _systemState.value.scalpingActive) {
                    stopScalping()
                }
                
                // Resume coordinator's swing analysis
                if (previousMode == TradingMode.SCALPING) {
                    tradingCoordinator.start()
                }
            }
            
            // V5.17.0: HYBRID mode — coordinator handles the confidence/value layering
            TradingMode.HYBRID -> {
                // Stop scalping if transitioning from SCALPING
                if (previousMode == TradingMode.SCALPING && _systemState.value.scalpingActive) {
                    stopScalping()
                }
                
                // Ensure coordinator is running (HYBRID needs the OODA loop)
                if (previousMode == TradingMode.SCALPING) {
                    tradingCoordinator.start()
                }
            }
            
            // V5.17.0: ALPHA_SCANNER — coordinator runs but AdvancedStrategyCoordinator 
            // feeds the watchlist dynamically via updateWatchlist()
            TradingMode.ALPHA_SCANNER -> {
                if (previousMode == TradingMode.SCALPING && _systemState.value.scalpingActive) {
                    stopScalping()
                }
                if (previousMode == TradingMode.SCALPING) {
                    tradingCoordinator.start()
                }
            }
            
            // V5.17.0: FUNDING_ARB — delta-neutral mode, coordinator handles spot+perp hedging
            TradingMode.FUNDING_ARB -> {
                if (previousMode == TradingMode.SCALPING && _systemState.value.scalpingActive) {
                    stopScalping()
                }
                if (previousMode == TradingMode.SCALPING) {
                    tradingCoordinator.start()
                }
            }
        }
        
        updateState { it.copy(tradingMode = mode) }
    }
    
    /**
     * Get current trading mode
     */
    fun getTradingMode(): TradingMode {
        return _systemState.value.tradingMode
    }
    
    /**
     * V5.17.0: Update HYBRID mode configuration.
     * Propagates confidence gates, rate limits, and value thresholds
     * from SettingsViewModel through to TradingCoordinator.
     */
    fun updateHybridConfig(hybridConfig: HybridModeConfig) {
        tradingCoordinator.updateHybridConfig(hybridConfig)
    }
    
    /**
     * Feed price data (called by PriceFeedService)
     */
    fun onPriceUpdate(symbol: String, open: Double, high: Double, low: Double, close: Double, volume: Double) {
        if (_isInitialized.value) {
            // Feed to trading coordinator (swing/position trading)
            tradingCoordinator.onPriceUpdate(symbol, open, high, low, close, volume)
            
            // Feed to scalping engine if active
            if (_systemState.value.scalpingActive) {
                scalpingEngine?.onPriceUpdate(
                    ScalpPriceData(
                        symbol = symbol,
                        timestamp = System.currentTimeMillis(),
                        open = open,
                        high = high,
                        low = low,
                        close = close,
                        volume = volume
                    )
                )
            }
        }
    }
    
    /**
     * Feed price data with bid/ask spread (preferred for scalping)
     */
    fun onPriceUpdateWithSpread(
        symbol: String,
        open: Double,
        high: Double,
        low: Double,
        close: Double,
        volume: Double,
        bid: Double,
        ask: Double
    ) {
        if (_isInitialized.value) {
            // Feed to trading coordinator
            tradingCoordinator.onPriceUpdate(symbol, open, high, low, close, volume)
            
            // Feed to scalping engine with spread data
            if (_systemState.value.scalpingActive) {
                scalpingEngine?.onPriceUpdate(
                    ScalpPriceData(
                        symbol = symbol,
                        timestamp = System.currentTimeMillis(),
                        open = open,
                        high = high,
                        low = low,
                        close = close,
                        volume = volume,
                        bid = bid,
                        ask = ask
                    )
                )
            }
        }
    }
    
    // ========================================================================
    // PUBLIC API - SIGNALS
    // ========================================================================
    
    /**
     * Get pending trade signals
     */
    fun getPendingSignals(): List<PendingTradeSignal> {
        return tradingCoordinator.getPendingSignals()
    }
    
    /**
     * Confirm a trade signal (execute the trade)
     */
    suspend fun confirmSignal(signalId: String): Result<ExecutedTrade> {
        return tradingCoordinator.confirmSignal(signalId)
    }
    
    /**
     * Reject a trade signal
     */
    fun rejectSignal(signalId: String) {
        tradingCoordinator.rejectSignal(signalId)
    }
    
    // ========================================================================
    // PUBLIC API - POSITIONS
    // ========================================================================
    
    /**
     * Get all managed positions
     */
    fun getPositions(): List<ManagedPosition> {
        return tradingCoordinator.getManagedPositions()
    }
    
    /**
     * Close a position
     */
    suspend fun closePosition(symbol: String): Result<Unit> {
        return tradingCoordinator.closePosition(symbol)
    }
    
    /**
     * Place an order directly (bypasses AI signals).
     * For manual trades from the Trading screen.
     */
    suspend fun placeOrder(orderRequest: com.miwealth.sovereignvantage.core.trading.engine.OrderRequest): Result<ExecutedTrade> {
        // Check if trading is allowed
        if (!isTradingAllowed()) {
            return Result.failure(Exception("Trading not allowed - risk limits hit or kill switch active"))
        }
        
        // BUILD #169: Validate stop loss vs liquidation price for leveraged positions
        if (orderRequest.leverage > 1.0 && orderRequest.stopLossPrice != null) {
            // Get current market price (use entry price as proxy)
            val currentPrice = orderRequest.price ?: run {
                // If no price specified, fetch current market price
                // For now, we'll skip validation if price is unknown
                // TODO: Fetch current price from exchange
                null
            }
            
            if (currentPrice != null) {
                val (isValid, error) = com.miwealth.sovereignvantage.core.trading.utils.LiquidationValidator.validateStopLoss(
                    entryPrice = currentPrice,
                    stopLossPrice = orderRequest.stopLossPrice!!,
                    leverage = orderRequest.leverage,
                    side = orderRequest.side
                )
                
                if (!isValid) {
                    return Result.failure(IllegalArgumentException("⚠️ LIQUIDATION RISK: $error"))
                }
            }
        }
        
        // Execute through the order executor
        return try {
            val result = orderExecutor.executeOrder(orderRequest)
            when (result) {
                is OrderExecutionResult.Success -> {
                    // Convert side to direction
                    val direction: TradeDirection = when (orderRequest.side) {
                        TradeSide.BUY, TradeSide.LONG -> TradeDirection.LONG
                        TradeSide.SELL, TradeSide.SHORT -> TradeDirection.SHORT
                        else -> TradeDirection.LONG  // default
                    }
                    
                    // Create ExecutedTrade from the order result
                    val trade = ExecutedTrade(
                        id = java.util.UUID.randomUUID().toString(),
                        symbol = orderRequest.symbol,
                        direction = direction,
                        entryPrice = result.order.executedPrice,
                        quantity = result.order.executedQuantity,
                        stopLoss = orderRequest.stopLossPrice ?: (result.order.executedPrice * 0.965), // Default 3.5% SL (BUILD #169: was 0.95/5%, now matches STAHL)
                        takeProfit = orderRequest.takeProfitPrice ?: (result.order.executedPrice * 1.10), // Default 10% TP
                        orderId = result.order.orderId,
                        timestamp = result.order.timestamp,
                        fromSignalId = null,
                        wasAutonomous = false
                    )
                    
                    // Update position manager
                    if (::positionManager.isInitialized) {
                        val existingPositions = positionManager.getPositionsForSymbol(orderRequest.symbol)
                        val matchingSide = existingPositions.find { pos ->
                            (pos.side == orderRequest.side) ||
                            (pos.side == TradeSide.BUY && orderRequest.side == TradeSide.LONG) ||
                            (pos.side == TradeSide.LONG && orderRequest.side == TradeSide.BUY) ||
                            (pos.side == TradeSide.SELL && orderRequest.side == TradeSide.SHORT) ||
                            (pos.side == TradeSide.SHORT && orderRequest.side == TradeSide.SELL)
                        }
                        
                        if (matchingSide != null) {
                            // Add to existing position
                            positionManager.addToPosition(
                                positionId = matchingSide.id,
                                quantity = result.order.executedQuantity,
                                price = result.order.executedPrice
                            )
                        } else {
                            // Open new position
                            positionManager.openPosition(
                                symbol = orderRequest.symbol,
                                side = orderRequest.side,
                                quantity = result.order.executedQuantity,
                                entryPrice = result.order.executedPrice,
                                leverage = orderRequest.leverage?.toDouble() ?: 1.0,
                                exchange = result.order.exchange,
                                useStahl = true  // MANDATORY: 3.5% sacred stop + progressive profit locking (optimized from extensive research)
                            )
                        }
                    }
                    
                    Result.success(trade)
                }
                is OrderExecutionResult.Rejected -> {
                    Result.failure(Exception("Order rejected: ${result.reason}"))
                }
                is OrderExecutionResult.PartialFill -> {
                    // Convert side to direction
                    val direction: TradeDirection = when (orderRequest.side) {
                        TradeSide.BUY, TradeSide.LONG -> TradeDirection.LONG
                        TradeSide.SELL, TradeSide.SHORT -> TradeDirection.SHORT
                        else -> TradeDirection.LONG  // default
                    }
                    
                    // Calculate filled quantity from remaining
                    val executedQuantity = orderRequest.quantity - result.remainingQuantity
                    
                    // Partial fill - still create the trade
                    val trade = ExecutedTrade(
                        id = java.util.UUID.randomUUID().toString(),
                        symbol = orderRequest.symbol,
                        direction = direction,
                        entryPrice = result.order.executedPrice,
                        quantity = executedQuantity,
                        stopLoss = orderRequest.stopLossPrice ?: (result.order.executedPrice * 0.965),  // Default 3.5% SL (BUILD #169: matches STAHL)
                        takeProfit = orderRequest.takeProfitPrice ?: (result.order.executedPrice * 1.10), // Default 10% TP
                        orderId = result.order.orderId,
                        timestamp = result.order.timestamp,
                        fromSignalId = null,
                        wasAutonomous = false
                    )
                    
                    // Update position manager for partial fill
                    if (::positionManager.isInitialized && executedQuantity > 0) {
                        val existingPositions = positionManager.getPositionsForSymbol(orderRequest.symbol)
                        val matchingSide = existingPositions.find { pos ->
                            (pos.side == orderRequest.side) ||
                            (pos.side == TradeSide.BUY && orderRequest.side == TradeSide.LONG) ||
                            (pos.side == TradeSide.LONG && orderRequest.side == TradeSide.BUY)
                        }
                        
                        if (matchingSide != null) {
                            positionManager.addToPosition(
                                positionId = matchingSide.id,
                                quantity = executedQuantity,
                                price = result.order.executedPrice
                            )
                        } else {
                            positionManager.openPosition(
                                symbol = orderRequest.symbol,
                                side = orderRequest.side,
                                quantity = executedQuantity,
                                entryPrice = result.order.executedPrice,
                                leverage = orderRequest.leverage?.toDouble() ?: 1.0,
                                exchange = result.order.exchange,
                                useStahl = true  // MANDATORY: 3.5% sacred stop + progressive profit locking (optimized from extensive research)
                            )
                        }
                    }
                    
                    Result.success(trade)
                }
                is OrderExecutionResult.Error -> {
                    Result.failure(result.exception)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get portfolio value
     */
    fun getPortfolioValue(): Double {
        return if (::positionManager.isInitialized) {
            positionManager.getPortfolioValue()
        } else {
            0.0
        }
    }
    
    // ========================================================================
    // PUBLIC API - RISK
    // ========================================================================
    
    /**
     * Check if trading is allowed (risk limits not hit)
     */
    fun isTradingAllowed(): Boolean {
        return if (::riskManager.isInitialized) {
            riskManager.isTradingAllowed.value
        } else {
            false
        }
    }
    
    /**
     * Activate emergency kill switch
     */
    fun activateKillSwitch(reason: String) {
        if (::riskManager.isInitialized) {
            scope.launch { riskManager.activateKillSwitch(reason) }
        }
        stopTrading()
        updateState { it.copy(killSwitchActive = true) }
    }
    
    /**
     * Reset kill switch (manual reset required)
     */
    fun resetKillSwitch() {
        if (::riskManager.isInitialized) {
            riskManager.resetKillSwitch()
        }
        updateState { it.copy(killSwitchActive = false) }
    }
    
    // ========================================================================
    // PUBLIC API - EVENTS
    // ========================================================================
    
    /**
     * Get trading coordinator events
     */
    fun getCoordinatorEvents(): SharedFlow<CoordinatorEvent> {
        return tradingCoordinator.events
    }
    
    /**
     * Get trading coordinator state
     */
    fun getCoordinatorState(): StateFlow<CoordinatorState> {
        return tradingCoordinator.state
    }
    
    /**
     * Get risk events
     */
    fun getRiskEvents(): SharedFlow<RiskEvent> {
        return if (::riskManager.isInitialized) {
            riskManager.riskEvents
        } else {
            MutableSharedFlow()
        }
    }
    
    // ========================================================================
    // PUBLIC API - SCALPING
    // ========================================================================
    
    /**
     * Initialize scalping engine with configuration.
     * Can be called anytime after system initialization.
     */
    fun initializeScalping(config: ScalpingConfig = ScalpingConfig.CRYPTO_DEFAULT) {
        if (!_isInitialized.value) {
            throw IllegalStateException("Trading system not initialized")
        }
        
        scalpingEngine = ScalpingEngine(config = config, scope = scope)
        subscribeToScalpingEvents()
        updateState { it.copy(scalpingInitialized = true, scalpingConfig = config) }
    }
    
    /**
     * Start scalping engine.
     * Automatically initializes with default config if not already initialized.
     */
    fun startScalping(config: ScalpingConfig? = null) {
        if (!_isInitialized.value) {
            throw IllegalStateException("Trading system not initialized")
        }
        
        // Initialize if not already done
        if (scalpingEngine == null) {
            initializeScalping(config ?: ScalpingConfig.CRYPTO_DEFAULT)
        } else if (config != null) {
            // Update config if provided
            scalpingEngine?.updateConfig(config)
        }
        
        scalpingEngine?.start()
        updateState { it.copy(scalpingActive = true) }
    }
    
    /**
     * Stop scalping engine.
     */
    fun stopScalping() {
        scalpingEngine?.stop()
        updateState { it.copy(scalpingActive = false) }
    }
    
    /**
     * Check if scalping is active
     */
    fun isScalpingActive(): Boolean = _systemState.value.scalpingActive
    
    /**
     * Get scalping statistics
     */
    fun getScalpingStats(): ScalpingStats {
        return scalpingEngine?.stats?.value ?: ScalpingStats()
    }
    
    /**
     * Get scalping events stream
     */
    fun getScalpingEvents(): SharedFlow<ScalpingEvent>? {
        return scalpingEngine?.events
    }
    
    /**
     * Get pending scalping signals
     */
    fun getPendingScalpSignals(): List<ScalpingSignal> {
        return scalpingEngine?.getPendingSignals() ?: emptyList()
    }
    
    /**
     * Confirm a scalping signal (execute the trade)
     */
    fun confirmScalpSignal(
        signalId: String,
        executionPrice: Double,
        quantity: Double,
        orderId: String
    ) {
        scalpingEngine?.confirmSignal(signalId, executionPrice, quantity, orderId)
    }
    
    /**
     * Cancel a pending scalping signal
     */
    fun cancelScalpSignal(signalId: String) {
        scalpingEngine?.cancelSignal(signalId)
    }
    
    /**
     * Get active scalp positions
     */
    fun getActiveScalps(): List<ActiveScalp> {
        return scalpingEngine?.getActiveScalps() ?: emptyList()
    }
    
    /**
     * Update scalping configuration
     */
    fun updateScalpingConfig(config: ScalpingConfig) {
        scalpingEngine?.updateConfig(config)
        updateState { it.copy(scalpingConfig = config) }
    }
    
    /**
     * Set scalping mode (Conservative, Standard, Aggressive, Turbo)
     */
    fun setScalpingMode(mode: ScalpMode) {
        val currentConfig = _systemState.value.scalpingConfig ?: ScalpingConfig.CRYPTO_DEFAULT
        val newConfig = currentConfig.copy(mode = mode)
        updateScalpingConfig(newConfig)
    }
    
    // ========================================================================
    // CONFIGURATION
    // ========================================================================
    
    /**
     * Update trading configuration
     */
    fun updateConfig(config: TradingCoordinatorConfig) {
        tradingCoordinator.updateConfig(config)
    }
    
    /**
     * Add trading symbol
     */
    fun addSymbol(symbol: String) {
        val currentConfig = _systemState.value
        val newSymbols = currentConfig.enabledSymbols + symbol
        tradingCoordinator.updateConfig(
            TradingCoordinatorConfig(enabledSymbols = newSymbols)
        )
        updateState { it.copy(enabledSymbols = newSymbols) }
    }
    
    /**
     * Remove trading symbol
     */
    fun removeSymbol(symbol: String) {
        val currentConfig = _systemState.value
        val newSymbols = currentConfig.enabledSymbols - symbol
        tradingCoordinator.updateConfig(
            TradingCoordinatorConfig(enabledSymbols = newSymbols)
        )
        updateState { it.copy(enabledSymbols = newSymbols) }
    }
    
    // ========================================================================
    // ASSET REGISTRY INTEGRATION
    // ========================================================================
    
    /**
     * Load assets from exchange(s) into the registry.
     * Call this during app startup or when refreshing available symbols.
     * 
     * @param exchanges List of exchange names (default: all supported)
     * @param forceRefresh If true, reload even if cache is fresh
     */
    suspend fun loadAssets(
        exchanges: List<String> = AssetLoaderFactory.supportedExchanges,
        forceRefresh: Boolean = false
    ): Map<String, AssetLoadResult> {
        return AssetRegistry.loadFromExchanges(exchanges, forceRefresh)
    }
    
    /**
     * Load assets from a single exchange.
     */
    suspend fun loadAssetsFromExchange(
        exchange: String,
        forceRefresh: Boolean = false
    ): AssetLoadResult {
        return AssetRegistry.loadFromExchange(exchange, forceRefresh)
    }
    
    /**
     * Get available symbols from registry, optionally filtered.
     */
    fun getAvailableSymbols(filter: AssetFilter? = null): List<String> {
        return if (filter != null) {
            AssetRegistry.filter(filter).map { it.symbol }
        } else {
            AssetRegistry.getAll().map { it.symbol }
        }
    }
    
    /**
     * Get available symbols by category.
     */
    fun getSymbolsByCategory(category: AssetCategory): List<String> {
        return AssetRegistry.getByCategory(category).map { it.symbol }
    }
    
    /**
     * Search for symbols in registry.
     */
    fun searchSymbols(query: String): List<TradableAsset> {
        return AssetRegistry.search(query)
    }
    
    /**
     * Get asset details from registry.
     */
    fun getAssetDetails(symbol: String): TradableAsset? {
        return AssetRegistry.get(symbol)
    }
    
    /**
     * Validate a symbol exists in registry and is tradable.
     */
    fun isSymbolTradable(symbol: String): Boolean {
        val asset = AssetRegistry.get(symbol) ?: return false
        return asset.status == AssetStatus.TRADING && asset.isMarketOpen()
    }
    
    /**
     * Add symbol with validation against registry.
     * Returns false if symbol not found or not tradable.
     */
    fun addSymbolValidated(symbol: String): Boolean {
        val asset = AssetRegistry.get(symbol)
        if (asset == null) {
            android.util.Log.w("TradingSystem", "Symbol not found in registry: $symbol")
            return false
        }
        if (asset.status != AssetStatus.TRADING) {
            android.util.Log.w("TradingSystem", "Symbol not trading: $symbol (${asset.status})")
            return false
        }
        addSymbol(symbol)
        return true
    }
    
    /**
     * Set symbols from a filter (replaces current symbols).
     */
    fun setSymbolsFromFilter(filter: AssetFilter) {
        val filtered = AssetRegistry.filter(filter)
            .map { it.symbol }
        
        tradingCoordinator.updateConfig(
            TradingCoordinatorConfig(enabledSymbols = filtered)
        )
        updateState { it.copy(enabledSymbols = filtered) }
    }
    
    /**
     * Set symbols by category.
     */
    fun setSymbolsByCategory(category: AssetCategory, maxSymbols: Int = 10) {
        val symbols = AssetRegistry.getByCategory(category)
            .filter { it.status == AssetStatus.TRADING }
            .take(maxSymbols)
            .map { it.symbol }
        
        tradingCoordinator.updateConfig(
            TradingCoordinatorConfig(enabledSymbols = symbols)
        )
        updateState { it.copy(enabledSymbols = symbols) }
    }
    
    /**
     * Observe registry updates (for UI binding).
     */
    fun observeAssets(): StateFlow<List<TradableAsset>> = AssetRegistry.assets
    
    /**
     * Observe registry loading state.
     */
    fun observeAssetsLoading(): StateFlow<Boolean> = AssetRegistry.isLoading
    
    /**
     * Get registry statistics.
     */
    fun getAssetStats(): RegistryStats = AssetRegistry.getStats()
    
    // ========================================================================
    // PRIVATE HELPERS
    // ========================================================================
    
    private fun subscribeToEvents() {
        // Subscribe to coordinator events for logging/notifications
        scope.launch {
            tradingCoordinator.events.collect { event ->
                handleCoordinatorEvent(event)
            }
        }
        
        // Subscribe to risk events
        scope.launch {
            riskManager.riskEvents.collect { event ->
                handleRiskEvent(event)
            }
        }
    }
    
    private fun handleCoordinatorEvent(event: CoordinatorEvent) {
        when (event) {
            is CoordinatorEvent.SignalGenerated -> {
                // Could trigger notification
                updateState { state ->
                    state.copy(pendingSignalCount = tradingCoordinator.getPendingSignals().size)
                }
            }
            is CoordinatorEvent.TradeExecuted -> {
                updateState { state ->
                    state.copy(
                        tradesExecutedToday = state.tradesExecutedToday + 1,
                        activePositionCount = tradingCoordinator.getManagedPositions().size
                    )
                }
            }
            is CoordinatorEvent.RiskLimitHit -> {
                // Risk limit hit - potentially stop trading
            }
            else -> { /* Handle other events as needed */ }
        }
    }
    
    private fun handleRiskEvent(event: RiskEvent) {
        when (event) {
            is RiskEvent.KillSwitchActivated -> {
                stopTrading()
                stopScalping() // Also stop scalping when kill switch activates
                updateState { it.copy(killSwitchActive = true) }
            }
            is RiskEvent.TradingResumed -> {
                updateState { it.copy(killSwitchActive = false) }
            }
            else -> { /* Handle other risk events */ }
        }
    }
    
    /**
     * Subscribe to Smart Order Router events (V5.17.0)
     */
    private fun subscribeToSmartRoutingEvents() {
        // Subscribe to router events
        smartOrderRouter?.let { router ->
            scope.launch {
                router.routingEvents.collect { event ->
                    handleRoutingEvent(event)
                }
            }
        }
        
        // Subscribe to executor events
        smartOrderExecutor?.let { executor ->
            scope.launch {
                executor.executorEvents.collect { event ->
                    handleExecutorEvent(event)
                }
            }
        }
    }
    
    /**
     * Handle Smart Order Router events
     */
    private fun handleRoutingEvent(event: RoutingEvent) {
        when (event) {
            is RoutingEvent.ExecutionComplete -> {
                val report = event.report
                updateState { state ->
                    state.copy(
                        totalPriceImprovement = state.totalPriceImprovement + report.priceImprovement,
                        totalFeesOptimized = state.totalFeesOptimized + 
                            (report.plan.expectedTotalFeeUsd - report.totalFeesPaid).coerceAtLeast(0.0)
                    )
                }
            }
            is RoutingEvent.SlippageRejection -> {
                // Log slippage rejection for monitoring
            }
            is RoutingEvent.DarkPoolFallback -> {
                // Log dark pool fallback (future: track for analytics)
            }
            is RoutingEvent.Error -> {
                // Log error
            }
            else -> { /* Other routing events */ }
        }
    }
    
    /**
     * Handle SmartOrderExecutor events
     */
    private fun handleExecutorEvent(event: SmartExecutorEvent) {
        when (event) {
            is SmartExecutorEvent.StrategyFallback -> {
                // Log strategy fallback for monitoring
            }
            is SmartExecutorEvent.Error -> {
                // Log error
            }
            else -> { /* Other executor events */ }
        }
    }
    
    /**
     * Subscribe to scalping engine events.
     * Called when scalping engine is initialized.
     */
    private fun subscribeToScalpingEvents() {
        scalpingEngine?.let { engine ->
            scope.launch {
                engine.events.collect { event ->
                    handleScalpingEvent(event)
                }
            }
        }
    }
    
    private fun handleScalpingEvent(event: ScalpingEvent) {
        when (event) {
            is ScalpingEvent.SignalGenerated -> {
                // Update pending signal count
                updateState { state ->
                    state.copy(pendingScalpSignalCount = scalpingEngine?.getPendingSignals()?.size ?: 0)
                }
                
                // V5.17.0: Auto-execute in AUTONOMOUS mode via SmartOrderRouter
                if (_systemState.value.tradingMode == TradingMode.SCALPING) {
                    // Check if we're configured for autonomous execution
                    val scalpConfig = _systemState.value.scalpingConfig
                    if (scalpConfig?.autoExecute == true && !_systemState.value.paperTradingMode) {
                        scope.launch {
                            executeScalpSignalViaSmartRouter(event.signal)
                        }
                    }
                }
            }
            is ScalpingEvent.SignalConfirmed -> {
                // V5.17.0: Execute confirmed signal through SmartOrderRouter
                if (!_systemState.value.paperTradingMode) {
                    scope.launch {
                        executeScalpSignalViaSmartRouter(event.signal)
                    }
                }
            }
            is ScalpingEvent.PositionOpened -> {
                updateState { state ->
                    state.copy(activeScalpCount = scalpingEngine?.getActiveScalps()?.size ?: 0)
                }
            }
            is ScalpingEvent.PositionClosed -> {
                // Update stats and counts
                val stats = scalpingEngine?.stats?.value ?: ScalpingStats()
                updateState { state ->
                    state.copy(
                        activeScalpCount = scalpingEngine?.getActiveScalps()?.size ?: 0,
                        scalpsToday = stats.scalpsToday,
                        scalpingPnlToday = stats.dailyPnlPercent
                    )
                }
            }
            is ScalpingEvent.RiskLimitHit -> {
                // Scalping-specific risk limit - may want to pause scalping only
                stopScalping()
                updateState { it.copy(scalpingActive = false) }
            }
            is ScalpingEvent.StahlLevelReached -> {
                // Could trigger notification for profit lock
            }
            is ScalpingEvent.EngineStarted -> {
                updateState { it.copy(scalpingActive = true) }
            }
            is ScalpingEvent.EngineStopped -> {
                updateState { it.copy(scalpingActive = false) }
            }
            is ScalpingEvent.Error -> {
                // Log error, potentially stop scalping
            }
            else -> { /* Handle other scalping events */ }
        }
    }
    
    // ========================================================================
    // INDICATOR INTEGRATION (V5.17.0)
    // ========================================================================
    
    /**
     * Subscribe to AI Board STAHL indicator events for logging and UI updates.
     */
    private fun subscribeToIndicatorEvents() {
        indicatorIntegration?.let { integration ->
            // Subscribe to preset recommendations
            scope.launch {
                integration.recommendations.collect { recommendation ->
                    handleRecommendation(recommendation)
                }
            }
            
            // Subscribe to stair expansion events
            scope.launch {
                integration.expansionEvents.collect { event ->
                    handleExpansionEvent(event)
                }
            }
        }
    }
    
    private fun handleRecommendation(recommendation: SymbolRecommendation) {
        // Log for debugging
        println("[AI Board] ${recommendation.symbol}: ${recommendation.preset} (${(recommendation.confidence * 100).toInt()}%)")
        println("  Reason: ${recommendation.reasoning}")
        
        // Update state with latest recommendation
        updateState { state ->
            state.copy(
                latestAIRecommendation = recommendation.preset.name,
                aiRecommendationConfidence = recommendation.confidence
            )
        }
        
        // TODO: Could trigger notification for high-confidence recommendations
    }
    
    private fun handleExpansionEvent(event: SymbolExpansionEvent) {
        when (event.decision) {
            ExpansionDecision.EXPAND_BORROW, ExpansionDecision.EXPAND_ATR -> {
                println("[AI Board] ${event.symbol}: Stairs expanded! +${event.newStairs.size} levels")
                println("  Reason: ${event.reasoning}")
                
                updateState { state ->
                    state.copy(lastStairExpansion = System.currentTimeMillis())
                }
            }
            ExpansionDecision.HOLD -> {
                // No action needed
            }
            else -> { /* no-op */ }
        }
    }
    
    /**
     * Subscribe a symbol to indicator tracking.
     * Call this when starting to trade a new symbol.
     */
    suspend fun subscribeSymbolToIndicators(symbol: String): Result<Unit> {
        val integration = indicatorIntegration 
            ?: return Result.failure(IllegalStateException("Indicator integration not initialized"))
        
        val connector = getPreferredConnector()
            ?: return Result.failure(IllegalStateException("No exchange connector available"))
        
        return integration.subscribeToExchange(connector, symbol)
    }
    
    /**
     * Get AI Board STAHL preset recommendation for a symbol.
     */
    suspend fun getAIRecommendation(symbol: String): PresetRecommendation? {
        return indicatorIntegration?.getRecommendation(symbol)
    }
    
    /**
     * Get current market context for a symbol.
     */
    suspend fun getMarketContext(symbol: String): StahlMarketContext? {
        return indicatorIntegration?.getMarketContext(symbol)
    }
    
    /**
     * Register a position for STAHL expansion monitoring.
     */
    fun registerPositionForExpansion(symbol: String, position: StahlPosition) {
        indicatorIntegration?.registerPosition(symbol, position)
    }
    
    /**
     * Update position stair index for expansion tracking.
     */
    suspend fun updatePositionStair(symbol: String, stairIndex: Int) {
        indicatorIntegration?.updatePositionStair(symbol, stairIndex)
    }
    
    /**
     * Get expanded position (with dynamically added stairs).
     */
    fun getExpandedPosition(symbol: String): ExpandedStahlPosition? {
        return indicatorIntegration?.getExpandedPosition(symbol)
    }
    
    /**
     * Unregister a closed position from expansion monitoring.
     */
    fun unregisterPosition(symbol: String) {
        indicatorIntegration?.unregisterPosition(symbol)
    }
    
    // ========================================================================
    // HEARTBEAT COORDINATOR INTEGRATION (V5.18.22)
    // ========================================================================
    
    /**
     * Initialize HeartbeatCoordinator system.
     * 
     * Wires up synchronized market snapshots for:
     * 1. TradingSystem (via TradingCoordinator)
     * 2. HedgeFundBoardOrchestrator (parallel decision system)
     * 
     * Both systems receive IDENTICAL snapshots every 1 second, preventing:
     * - Race conditions (one sees stale prices while other sees fresh)
     * - Incorrect hedge ratios (price desync between systems)
     * - Desynchronized risk calculations
     * 
     * BUILD #173: This method implements Option A - Full integration of all 3 systems
     */
    private suspend fun initializeHeartbeatSystem() {
        try {
            Log.d(TAG, "🎯 Initializing HeartbeatCoordinator system...")
            
            // Create HeartbeatCoordinator with 1-second interval
            heartbeatCoordinator = HeartbeatCoordinator(
                positionManager = positionManager,
                heartbeatIntervalMs = 1000L,  // 1 second snapshots
                healthCheckTimeoutMs = 5000L  // 5 seconds to detect frozen system
            )
            
            // Create Trading System adapter
            tradingHeartbeatAdapter = TradingSystemHeartbeatAdapter(
                tradingCoordinator = tradingCoordinator
            )
            
            // Create Hedge Fund Board adapter (if board exists)
            // Note: HedgeFundBoardOrchestrator is created on-demand in Phase 3
            // hedgeFundHeartbeatAdapter will be created when hedge fund board is activated
            
            // Register Trading System with coordinator
            heartbeatCoordinator?.registerReceiver(tradingHeartbeatAdapter!!)
            
            Log.d(TAG, "✅ HeartbeatCoordinator initialized:")
            Log.d(TAG, "   - Interval: 1000ms")
            Log.d(TAG, "   - Health timeout: 5000ms")
            Log.d(TAG, "   - Trading adapter: REGISTERED")
            Log.d(TAG, "   - Hedge fund adapter: Pending (created on hedge fund activation)")
            
            // Note: HeartbeatCoordinator.start() will be called when trading starts
            // This allows paper trading to initialize without starting heartbeat loop
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize HeartbeatCoordinator: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Start the HeartbeatCoordinator.
     * Called when trading system becomes active.
     */
    private fun startHeartbeat() {
        heartbeatCoordinator?.start()
        Log.d(TAG, "💚 HeartbeatCoordinator STARTED - 1-second synchronized snapshots active")
    }
    
    /**
     * Stop the HeartbeatCoordinator.
     * Called when trading system stops.
     */
    private fun stopHeartbeat() {
        heartbeatCoordinator?.stop()
        Log.d(TAG, "⏸️ HeartbeatCoordinator STOPPED")
    }
    
    /**
     * Activate Hedge Fund Board and wire to HeartbeatCoordinator.
     * 
     * This creates the HedgeFundBoardOrchestrator (if not exists) and wires it
     * to receive synchronized snapshots alongside the main trading system.
     * 
     * BUILD #173: Phase 2 - HedgeFund → HeartbeatCoordinator integration
     */
    suspend fun activateHedgeFundBoard(): Result<Unit> {
        return try {
            Log.d(TAG, "🎯 Activating Hedge Fund Board...")
            
            // Create Hedge Fund Board if not exists
            val hedgeFundBoard = HedgeFundBoardOrchestrator(
                configuration = BoardPresets.HEDGE_FUND_FULL,
                includeCrossovers = true
            )
            
            // Create execution bridge for live trading
            val executionBridge = HedgeFundExecutionBridge(
                orderExecutor = orderExecutor,
                tradingCoordinator = tradingCoordinator,
                positionManager = positionManager,
                tradingSystemManager = tradingSystemManager 
                    ?: throw IllegalStateException("TradingSystemManager not set before Hedge Fund initialization")
            )
            
            // Create adapter for hedge fund (with execution)
            hedgeFundHeartbeatAdapter = HedgeFundHeartbeatAdapter(
                hedgeFundBoard = hedgeFundBoard,
                executionBridge = executionBridge
            )
            
            // Register with HeartbeatCoordinator
            heartbeatCoordinator?.registerReceiver(hedgeFundHeartbeatAdapter!!)
            
            Log.d(TAG, "✅ Hedge Fund Board activated:")
            Log.d(TAG, "   - Members: ${hedgeFundBoard.getMemberCount()}")
            Log.d(TAG, "   - Active: ${hedgeFundBoard.getActiveMemberNames().joinToString(", ")}")
            Log.d(TAG, "   - Heartbeat adapter: REGISTERED")
            Log.d(TAG, "   - Execution bridge: WIRED")
            Log.d(TAG, "   - Receiving synchronized snapshots: YES")
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to activate Hedge Fund Board: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get the preferred exchange connector for indicator data.
     */
    private fun getPreferredConnector(): UnifiedExchangeConnector? {
        // Try to get from registry first
        exchangeRegistry?.let { registry ->
            return registry.getConnector(SupportedExchange.KRAKEN.id)
                ?: registry.getConnector(SupportedExchange.BINANCE.id)
                ?: registry.getConnector(SupportedExchange.COINBASE.id)
        }
        return null
    }

    /**
     * Execute a scalping signal through the SmartOrderRouter (PQC-secured).
     * 
     * V5.17.0: Bridges ScalpingSignal → OrderRequest → SmartOrderRouter
     * Uses IOC (Immediate or Cancel) for scalping to ensure quick fills.
     */
    private suspend fun executeScalpSignalViaSmartRouter(signal: ScalpingSignal) {
        val router = smartOrderRouter ?: run {
            // Fallback to legacy execution if SOR not available
            executeScalpSignalLegacy(signal)
            return
        }
        
        try {
            // Convert ScalpDirection to TradeSide
            val side = when (signal.direction) {
                ScalpDirection.LONG -> TradeSide.BUY
                ScalpDirection.SHORT -> TradeSide.SELL
            }
            
            // Calculate position size based on config
            val positionSize = calculateScalpPositionSize(signal)
            
            // Create order request
            val orderRequest = OrderRequest(
                symbol = signal.symbol,
                side = side,
                type = OrderType.LIMIT,  // Use limit for better fill price
                quantity = positionSize,
                price = signal.entryPrice,
                stopLossPrice = signal.stopLoss,
                takeProfitPrice = signal.targetPrice,
                timeInForce = TimeInForce.IOC,  // Immediate or Cancel for scalping
                clientOrderId = "SCALP-${signal.id}"
            )
            
            // Execute through SmartOrderRouter (PQC-secured)
            val executionReport = router.executeOrder(
                request = orderRequest,
                strategy = RoutingStrategy.BEST_EXECUTION  // Optimize for price + fees for scalping
            )
            
            // Handle execution result
            when (executionReport.status) {
                ExecutionStatus.COMPLETE, ExecutionStatus.PARTIAL_FILL -> {
                    // Confirm the signal with actual execution details
                    scalpingEngine?.confirmSignal(
                        signalId = signal.id,
                        executionPrice = executionReport.averageFilledPrice,
                        quantity = executionReport.totalFilledQuantity,
                        orderId = orderRequest.clientOrderId
                    )
                    
                    // Update SOR stats
                    updateState { state ->
                        state.copy(
                            totalPriceImprovement = state.totalPriceImprovement + executionReport.priceImprovement,
                            totalFeesOptimized = state.totalFeesOptimized + 
                                (executionReport.plan.expectedTotalFeeUsd - executionReport.totalFeesPaid)
                        )
                    }
                }
                ExecutionStatus.CANCELLED, ExecutionStatus.FAILED -> {
                    // Signal couldn't be executed - cancel it
                    scalpingEngine?.cancelSignal(signal.id)
                }
            }
        } catch (e: Exception) {
            // Log error and cancel signal
            scalpingEngine?.cancelSignal(signal.id)
        }
    }
    
    /**
     * Legacy execution path when SmartOrderRouter is not available.
     */
    private suspend fun executeScalpSignalLegacy(signal: ScalpingSignal) {
        val exchange = activeExchange ?: return
        
        val side = when (signal.direction) {
            ScalpDirection.LONG -> TradeSide.BUY
            ScalpDirection.SHORT -> TradeSide.SELL
        }
        
        val positionSize = calculateScalpPositionSize(signal)
        
        val request = OrderRequest(
            symbol = signal.symbol,
            side = side,
            type = OrderType.LIMIT,
            quantity = positionSize,
            price = signal.entryPrice,
            timeInForce = TimeInForce.IOC,
            clientOrderId = "SCALP-${signal.id}"
        )
        
        when (val result = exchange.placeOrder(request)) {
            is OrderExecutionResult.Success -> {
                scalpingEngine?.confirmSignal(
                    signalId = signal.id,
                    executionPrice = result.order.price,
                    quantity = result.order.filledQuantity,
                    orderId = result.order.orderId
                )
            }
            else -> {
                scalpingEngine?.cancelSignal(signal.id)
            }
        }
    }
    
    /**
     * Calculate position size for a scalp based on config and risk parameters.
     */
    private fun calculateScalpPositionSize(signal: ScalpingSignal): Double {
        val config = _systemState.value.scalpingConfig ?: ScalpingConfig.CRYPTO_DEFAULT
        val portfolioValue = _systemState.value.portfolioValue
        
        // Use config's max position size percentage
        val maxPositionValue = portfolioValue * (config.maxPositionSizePercent / 100.0)
        
        // Calculate quantity based on entry price
        return if (signal.entryPrice > 0) {
            maxPositionValue / signal.entryPrice
        } else {
            0.0
        }
    }
    
    private fun createPaperTradingAdapter(): ExchangeAdapter {
        return PaperTradingAdapter()
    }
    
    private fun updateState(updater: (SystemState) -> SystemState) {
        _systemState.update(updater)
    }
    
    /**
     * Shutdown and cleanup
     */
    fun shutdown() {
        // Stop scalping
        scalpingEngine?.stop()
        
        // Stop indicator integration (V5.17.0)
        indicatorIntegration?.shutdown()
        indicatorIntegration = null
        
        // Stop trading coordinator
        if (::tradingCoordinator.isInitialized) {
            tradingCoordinator.shutdown()
        }
        
        // V5.17.0: Stop sentiment engine
        sentimentEngine.stop()
        
        // V5.17.0: Cancel AI price feed jobs
        aiPriceFeedJob?.cancel()
        aiPriceFeedJob = null
        aiOrderBookFeedJob?.cancel()
        aiOrderBookFeedJob = null
        
        // V5.17.0: Shutdown AI Exchange Interface
        aiAdapterFactory?.shutdown()
        aiAdapterFactory = null
        aiConnectionManager?.let {
            it.stopHealthMonitoring()
            scope.launch { it.disconnectAll() }
        }
        aiConnectionManager = null
        _aiExchangeEnabled.value = false
        
        // Shutdown Smart Order Routing (V5.17.0)
        smartOrderExecutor?.shutdown()
        smartOrderExecutor = null
        smartOrderRouter?.shutdown()
        smartOrderRouter = null
        feeOptimizer = null
        slippageProtector = null
        orderSplitter = null
        
        // Shutdown price feed service
        priceFeedService?.shutdown()
        priceFeedService = null
        
        // Disconnect all exchanges
        scope.launch {
            connectedExchanges.values.forEach { it.disconnect() }
            connectedExchanges.clear()
        }
        
        // Shutdown exchange registry
        exchangeRegistry?.shutdown()
        exchangeRegistry = null
        
        // Reset state
        _smartRoutingEnabled.value = false
        
        // Cancel scope
        scope.cancel()
        
        // Clear singleton
        instance = null
    }
}

// ============================================================================
// SYSTEM STATE
// ============================================================================

data class SystemState(
    val isInitialized: Boolean = false,
    val isTradingActive: Boolean = false,
    val tradingMode: TradingMode = TradingMode.SIGNAL_ONLY,
    val paperTradingMode: Boolean = true,
    val killSwitchActive: Boolean = false,
    val activeExchange: String? = null,
    val enabledSymbols: List<String> = getDefaultSymbols(),
    val pendingSignalCount: Int = 0,
    val activePositionCount: Int = 0,
    val tradesExecutedToday: Int = 0,
    
    // Portfolio state
    val portfolioValue: Double = 10000.0,
    val initialPortfolioValue: Double? = 10000.0,
    val activeSignalCount: Int = 0,
    val realizedPnlToday: Double = 0.0,
    val unrealizedPnl: Double = 0.0,
    
    // Scalping state
    val scalpingInitialized: Boolean = false,
    val scalpingActive: Boolean = false,
    val scalpingConfig: ScalpingConfig? = null,
    val pendingScalpSignalCount: Int = 0,
    val activeScalpCount: Int = 0,
    val scalpsToday: Int = 0,
    val scalpingPnlToday: Double = 0.0,
    
    // PQC Security state (V5.17.0)
    val pqcSecurityEnabled: Boolean = false,
    val exchangeConnected: Boolean = false,
    
    // Smart Order Routing state (V5.17.0)
    val smartRoutingEnabled: Boolean = false,
    val connectedExchangeCount: Int = 0,
    val totalPriceImprovement: Double = 0.0,  // Cumulative price improvement from SOR
    val totalFeesOptimized: Double = 0.0,      // Cumulative fee savings from SOR
    
    // AI Board STAHL state (V5.17.0)
    val latestAIRecommendation: String? = null,
    val aiRecommendationConfidence: Double = 0.0,
    val lastStairExpansion: Long = 0L,
    
    // AI Exchange Interface state (V5.17.0)
    val aiExchangeEnabled: Boolean = false,
    val aiExecutionMode: String = "PAPER"
) {
    companion object {
        /**
         * Get default symbols from registry.
         * Falls back to hardcoded list if registry is empty.
         */
        fun getDefaultSymbols(): List<String> {
            val registrySymbols = AssetRegistry.getScalpingEnabled()
                .filter { it.status == AssetStatus.TRADING }
                .filter { it.category == AssetCategory.MAJOR_CRYPTO }
                .take(5)
                .map { it.symbol }
            
            return registrySymbols.ifEmpty { 
                listOf("BTC/USDT", "ETH/USDT") 
            }
        }
    }
}

// ============================================================================
// PAPER TRADING ADAPTER
// ============================================================================

/**
 * Paper trading adapter for simulation without real exchange
 */
class PaperTradingAdapter : ExchangeAdapter {
    override val exchangeName: String = "Paper"
    
    private val openOrders = mutableListOf<ExecutedOrder>()
    private var orderIdCounter = 0
    private val balances = mutableMapOf<String, Double>().apply { put("USDT", 10000.0) }
    private val prices = mutableMapOf<String, Double>()
    
    fun setPrice(symbol: String, price: Double) { prices[symbol] = price }
    fun getBalance(asset: String): Double = balances[asset] ?: 0.0
    fun getAllBalances(): Map<String, Double> = balances.toMap()
    
    /**
     * Calculate total portfolio value in USDT
     * 
     * BUILD #399: Fixed calculation - multiply crypto balances by prices
     * BEFORE: Just summed raw balances (1 BTC + 10000 USDT = 10001)
     * NOW: Converts crypto to USDT value (1 BTC @ 104k + 10000 USDT = 114000)
     */
    fun getPortfolioValue(): Double {
        var totalValue = 0.0
        
        balances.forEach { (asset, quantity) ->
            if (asset == "USDT" || asset == "USD") {
                // USDT/USD balances are already in USD
                totalValue += quantity
            } else {
                // Crypto balances need to be multiplied by price
                val symbol = "$asset/USDT"  // e.g., "BTC/USDT"
                val price = prices[symbol] ?: 0.0
                totalValue += quantity * price
            }
        }
        
        return totalValue
    }
    
    fun resetAccount(newBalance: Double = 10000.0) { balances.clear(); balances["USDT"] = newBalance; openOrders.clear() }
    fun getOrderHistory(): List<ExecutedOrder> = openOrders.toList()
    
    override suspend fun placeOrder(request: OrderRequest): OrderExecutionResult {
        val orderId = "PAPER-${++orderIdCounter}"
        
        // Get current price (use request price, or current market price)
        val executedPrice = request.price ?: prices[request.symbol] ?: (request.stopPrice ?: 0.0)
        
        // Calculate trade value and fees
        val tradeValue = request.quantity * executedPrice
        val fee = tradeValue * 0.001 // 0.1% fee
        val totalCost = tradeValue + fee
        
        // Extract base and quote assets from symbol (e.g., "BTC/USDT" -> "BTC", "USDT")
        val parts = request.symbol.split("/")
        val baseAsset = parts.getOrNull(0) ?: "BTC"
        val quoteAsset = parts.getOrNull(1) ?: "USDT"
        
        // BUILD #147: Update balances based on trade side
        when (request.side) {
            TradeSide.BUY, TradeSide.LONG -> {
                // Check if we have enough quote asset (USDT)
                val currentQuote = balances[quoteAsset] ?: 0.0
                if (currentQuote < totalCost) {
                    return OrderExecutionResult.Rejected(
                        reason = "Insufficient $quoteAsset: have ${String.format("%.2f", currentQuote)}, need ${String.format("%.2f", totalCost)}",
                        code = "INSUFFICIENT_BALANCE"
                    )
                }
                
                // Deduct quote asset (USDT) and add base asset (BTC)
                balances[quoteAsset] = currentQuote - totalCost
                balances[baseAsset] = (balances[baseAsset] ?: 0.0) + request.quantity
            }
            
            TradeSide.SELL, TradeSide.SHORT -> {
                // Check if we have enough base asset (BTC)
                val currentBase = balances[baseAsset] ?: 0.0
                if (currentBase < request.quantity) {
                    return OrderExecutionResult.Rejected(
                        reason = "Insufficient $baseAsset: have ${String.format("%.6f", currentBase)}, need ${String.format("%.6f", request.quantity)}",
                        code = "INSUFFICIENT_BALANCE"
                    )
                }
                
                // Deduct base asset (BTC) and add quote asset (USDT)
                val proceeds = tradeValue - fee
                balances[baseAsset] = currentBase - request.quantity
                balances[quoteAsset] = (balances[quoteAsset] ?: 0.0) + proceeds
            }
            
            TradeSide.DEPOSIT -> {
                // Add to balance directly (no fee for deposits)
                balances[baseAsset] = (balances[baseAsset] ?: 0.0) + request.quantity
            }
            
            TradeSide.WITHDRAWAL -> {
                // Deduct from balance
                val currentBase = balances[baseAsset] ?: 0.0
                if (currentBase < request.quantity) {
                    return OrderExecutionResult.Rejected(
                        reason = "Insufficient $baseAsset: have ${String.format("%.6f", currentBase)}, need ${String.format("%.6f", request.quantity)}",
                        code = "INSUFFICIENT_BALANCE"
                    )
                }
                balances[baseAsset] = currentBase - request.quantity
            }
        }
        
        val order = ExecutedOrder(
            orderId = orderId,
            clientOrderId = request.clientOrderId,
            symbol = request.symbol,
            side = request.side,
            type = request.type,
            price = executedPrice,
            executedPrice = executedPrice,
            quantity = request.quantity,
            executedQuantity = request.quantity,
            fee = fee,
            feeCurrency = quoteAsset,
            status = OrderStatus.FILLED,
            exchange = "Paper"
        )
        
        openOrders.add(order)  // Track order in history
        
        return OrderExecutionResult.Success(order)
    }
    
    override suspend fun cancelOrder(orderId: String, symbol: String): Boolean {
        return openOrders.removeIf { it.orderId == orderId }
    }
    
    override suspend fun modifyOrder(
        orderId: String,
        symbol: String,
        newPrice: Double?,
        newQuantity: Double?
    ): OrderExecutionResult {
        return OrderExecutionResult.Rejected("Paper trading does not support order modification")
    }
    
    override suspend fun getOrderStatus(orderId: String, symbol: String): ExecutedOrder? {
        return openOrders.find { it.orderId == orderId }
    }
    
    override suspend fun getOpenOrders(symbol: String?): List<ExecutedOrder> {
        return if (symbol != null) {
            openOrders.filter { it.symbol == symbol }
        } else {
            openOrders.toList()
        }
    }
    
    override fun isRateLimited(): Boolean = false
}

// ============================================================================
// EXTENSIONS
// ============================================================================

fun RiskManager.activateKillSwitch(reason: String) {
    // Implementation would trigger kill switch in RiskManager
}

fun RiskManager.resetKillSwitch() {
    // Implementation would reset kill switch in RiskManager
}

/**
 * Extension to get portfolio value from PositionManager.
 * BUILD #261: When no positions are open, totalMargin+totalUnrealizedPnl = 0.
 * We track realizedPnl to maintain running capital across trades.
 */
// BUILD #261: Thread-safe capital tracker using AtomicLong (stores as bits of Double)
// Extension functions on PositionManager are top-level — use atomic for concurrent safety.
private val _trackedCapitalBits = java.util.concurrent.atomic.AtomicLong(
    java.lang.Double.doubleToRawLongBits(100_000.0)
)

private var _trackedCapital: Double
    get() = java.lang.Double.longBitsToDouble(_trackedCapitalBits.get())
    set(v) { _trackedCapitalBits.set(java.lang.Double.doubleToRawLongBits(v)) }

fun PositionManager.getPortfolioValue(): Double {
    val summary = this.getPositionSummary()
    val positionValue = summary.totalMargin + summary.totalUnrealizedPnl
    // When positions exist, add their value to the remaining cash capital
    // When no positions, return pure cash capital
    return _trackedCapital + if (positionValue > 0) positionValue else 0.0
}

fun PositionManager.seedCapital(capital: Double) {
    _trackedCapital = capital
}

fun PositionManager.updateRealizedCapital(delta: Double) {
    // Atomic compare-and-swap loop for thread-safe floating point update
    while (true) {
        val current = _trackedCapitalBits.get()
        val updated = java.lang.Double.doubleToRawLongBits(
            java.lang.Double.longBitsToDouble(current) + delta
        )
        if (_trackedCapitalBits.compareAndSet(current, updated)) break
    }
}
