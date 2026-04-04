package com.miwealth.sovereignvantage.core.trading

/**
 * TRADING SYSTEM INTEGRATION
 * 
 * Sovereign Vantage: Arthur Edition V5.17.0
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * V5.17.0 CHANGES:
 * - NEW: Order book WebSocket feed via startOrderBookFeed()
 *   Subscribes to order books across all connected exchanges in parallel
 *   Routes OrderBook snapshots to TradingCoordinator.onOrderBookUpdate()
 *   Populates real best-bid/best-ask for accurate arb spread detection
 * - Price feed and order book feed run as parallel coroutine jobs
 * 
 * V5.17.0 CHANGES:
 * - Gap 2 COMPLETE: addExchange() now registers with SmartOrderRouter dynamically
 *   — late-joined exchanges participate in arb/hedge routing immediately
 * - NEW: removeExchange() cleanly deregisters from SOR + AIConnectionManager
 * - Gap 3 COMPLETE: startPriceFeed() uses multi-exchange aggregation via
 *   AIConnectionManager.subscribeToAllPrices() when >1 exchange connected
 * - Gap 4: isTestnetMode flag in TradingSystemConfig + IntegratedTradingState
 *   propagated through to UI DashboardState for safety indicators
 * 
 * V5.17.0 CHANGES:
 * - Wired SmartOrderRouter into LIVE_AI execution path (Gap 3 fix)
 * - AI exchange adapters now participate in multi-exchange smart routing
 * - SmartOrderExecutor bridges SOR to OrderExecutor infrastructure
 * - Paper trading mode unchanged (single adapter, no routing needed)
 * 
 * V5.17.0 CHANGES:
 * - Added placeOrder() method for direct order execution
 * - Added getPortfolioValue() and isTradingAllowed() helpers
 * - Added EmergencyStop and KillSwitchReset events
 * - Full wiring to TradingSystemManager complete
 * 
 * Central integration point that wires together:
 * - AIConnectionManager (exchange connections)
 * - TradingCoordinator (trading logic, AI Board, STAHL)
 * - SmartOrderRouter (multi-exchange routing — V5.17.0)
 * - OrderExecutor (order routing)
 * - Price feeds (real-time data)
 * 
 * This file serves as the "nervous system" connecting all trading components
 * and providing a clean API for the UI layer.
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */



import android.content.Context
import android.util.Log
import com.miwealth.sovereignvantage.core.*
import com.miwealth.sovereignvantage.core.ai.SentimentEngine
import com.miwealth.sovereignvantage.core.ai.macro.MacroSentimentAnalyzer
import com.miwealth.sovereignvantage.core.ai.BoardDecisionRepository
import com.miwealth.sovereignvantage.core.exchange.*
import com.miwealth.sovereignvantage.core.exchange.BinancePublicPriceFeed
import com.miwealth.sovereignvantage.core.exchange.ai.*
import com.miwealth.sovereignvantage.core.trading.assets.*
import com.miwealth.sovereignvantage.core.trading.engine.*
import com.miwealth.sovereignvantage.core.trading.routing.*
import com.miwealth.sovereignvantage.core.trading.TradeDirection  // BUILD #158: For getManagedPositions conversion
import com.miwealth.sovereignvantage.core.utils.SystemLogger
import com.miwealth.sovereignvantage.data.local.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Configuration for the integrated trading system.
 */
data class TradingSystemConfig(
    /** Execution mode: LIVE_AI, PAPER, etc. */
    val executionMode: TradingExecutionMode = TradingExecutionMode.PAPER,
    
    /** Trading mode: AUTONOMOUS, SIGNAL_ONLY, HYBRID, SCALPING */
    val tradingMode: TradingMode = TradingMode.SIGNAL_ONLY,
    
    /** Primary exchange ID for live trading */
    val primaryExchangeId: String = "binance",
    
    /** V5.17.0: Whether running against testnet/sandbox endpoints */
    val isTestnetMode: Boolean = false,
    
    /** Initial balance for paper trading */
    val paperTradingBalance: Double = 100_000.0,
    
    /** Enable live price feeds even in paper mode */
    val useLivePricesInPaperMode: Boolean = false,
    
    /** Analysis interval in milliseconds */
    val analysisIntervalMs: Long = 15_000, // BUILD #236: 60s→15s for paper trading responsiveness
    
    /** Minimum AI confidence to trade */
    val minConfidenceToTrade: Double = 0.45, // BUILD #236: 0.6→0.45 to generate signals in bear/sideways markets
    
    /** BUILD #273: Minimum board agreement (out of 8 members) */
    val minBoardAgreement: Int = 4, // BUILD #273: Default MODERATE (4/8 = 50%)
    
    /** Enable STAHL Stair Stop™ */
    val useStahlStops: Boolean = true,
    
    /** Maximum concurrent positions */
    val maxConcurrentPositions: Int = 5,
    
    /** Symbols to trade */
    val tradingSymbols: List<String> = listOf("BTC/USDT", "ETH/USDT"),
    
    /** Hybrid mode configuration */
    val hybridConfig: HybridModeConfig = HybridModeConfig.MODERATE,
    
    /** Risk management configuration */
    val riskConfig: RiskConfig = RiskConfig(),
    
    /** Enable asset discovery pipeline on startup */
    val enableAssetDiscovery: Boolean = true,
    
    /** Asset discovery configuration */
    val discoveryConfig: AssetDiscoveryConfig = AssetDiscoveryConfig(),
    
    /** V5.17.0: Enable smart order routing for multi-exchange execution */
    val enableSmartRouting: Boolean = true,
    
    /** V5.17.0: Smart order routing configuration */
    val routingConfig: RoutingConfig = RoutingConfig(),
    
    /** V5.17.0: Credentials for the primary exchange.
     *  Passed during setupLiveExchange() so the connector is created WITH auth
     *  from the start, avoiding the stale-adapter credential gap. */
    val primaryCredentials: ExchangeCredentials? = null
)

/**
 * Configuration for asset discovery pipeline.
 */
data class AssetDiscoveryConfig(
    /** Exchanges to discover from */
    val exchanges: List<String> = listOf("binance"),
    
    /** Enable DeFiLlama enrichment */
    val enableDeFiLlama: Boolean = false,  // Disabled by default for faster startup
    
    /** Minimum 24h volume filter (USD) */
    val minVolume24h: Double = 100_000.0
)

/**
 * Integrated trading system state.
 */
data class IntegratedTradingState(
    val isInitialized: Boolean = false,
    val executionMode: TradingExecutionMode = TradingExecutionMode.PAPER,
    /** V5.17.0: Whether running against testnet/sandbox endpoints */
    val isTestnetMode: Boolean = false,
    val connectedExchanges: Set<String> = emptySet(),
    val exchangeHealth: Map<String, ConnectionStatus> = emptyMap(),
    val coordinatorState: CoordinatorState = CoordinatorState(),
    val portfolioValue: Double = 0.0,
    val balances: Map<String, Double> = emptyMap(),
    /** V5.18.0: Latest prices from feed (symbol -> price) for dashboard/wallet */
    val latestPrices: Map<String, Double> = emptyMap(),
    val lastError: String? = null
)

/**
 * Events from the integrated trading system.
 */
sealed class TradingSystemEvent {
    data class Initialized(val mode: TradingExecutionMode) : TradingSystemEvent()
    data class ExchangeConnected(val exchangeId: String) : TradingSystemEvent()
    data class ExchangeDisconnected(val exchangeId: String) : TradingSystemEvent()
    data class ExchangeError(val exchangeId: String, val error: String) : TradingSystemEvent()
    data class TradeExecuted(val trade: ExecutedTrade) : TradingSystemEvent()
    data class SignalGenerated(val signal: PendingTradeSignal) : TradingSystemEvent()
    data class PositionUpdated(val position: ManagedPosition) : TradingSystemEvent()
    data class BalanceUpdated(val asset: String, val amount: Double) : TradingSystemEvent()
    data class Error(val message: String, val exception: Throwable? = null) : TradingSystemEvent()
    object TradingStarted : TradingSystemEvent()
    object TradingStopped : TradingSystemEvent()
    data class ModeChanged(val newMode: TradingMode) : TradingSystemEvent()
    data class EmergencyStop(val reason: String) : TradingSystemEvent()
    data class KillSwitchReset(val timestamp: Long = System.currentTimeMillis()) : TradingSystemEvent()
}

/**
 * Integrated Trading System - The brain of Sovereign Vantage.
 * 
 * Usage:
 * ```kotlin
 * val system = TradingSystemIntegration(context)
 * 
 * // Initialize with config
 * system.initialize(TradingSystemConfig(
 *     executionMode = TradingExecutionMode.PAPER,
 *     tradingMode = TradingMode.SIGNAL_ONLY
 * ))
 * 
 * // Start trading
 * system.start()
 * 
 * // Observe state
 * system.state.collect { state ->
 *     updateUI(state)
 * }
 * ```
 */
class TradingSystemIntegration(
    private val context: Context,
    private val boardDecisionRepository: BoardDecisionRepository? = null,
    private val tradeDao: TradeDao? = null,
    private val tradeRecorder: com.miwealth.sovereignvantage.core.portfolio.TradeRecorder? = null,  // BUILD #274
    private val equitySnapshotRecorder: com.miwealth.sovereignvantage.core.portfolio.EquitySnapshotRecorder? = null  // BUILD #274
) {
    companion object {
        private const val TAG = "TradingSystemIntegration"
        
        @Volatile
        private var INSTANCE: TradingSystemIntegration? = null
        
        /**
         * Get singleton instance.
         * BUILD #274: Added TradeRecorder and EquitySnapshotRecorder for portfolio analytics
         */
        fun getInstance(
            context: Context,
            boardDecisionRepository: BoardDecisionRepository? = null,
            tradeDao: TradeDao? = null,
            tradeRecorder: com.miwealth.sovereignvantage.core.portfolio.TradeRecorder? = null,  // BUILD #274
            equitySnapshotRecorder: com.miwealth.sovereignvantage.core.portfolio.EquitySnapshotRecorder? = null  // BUILD #274
        ): TradingSystemIntegration {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TradingSystemIntegration(
                    context.applicationContext,
                    boardDecisionRepository,
                    tradeDao,
                    tradeRecorder,  // BUILD #274
                    equitySnapshotRecorder  // BUILD #274
                ).also { INSTANCE = it }
            }
        }
    }
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Core components
    private var aiConnectionManager: AIConnectionManager? = null
    private var adapterFactory: AIExchangeAdapterFactory? = null
    private var exchangeAdapter: ExchangeAdapter? = null
    private var orderExecutor: OrderExecutor? = null
    private var riskManager: RiskManager? = null
    private var positionManager: PositionManager? = null
    private var tradingCoordinator: TradingCoordinator? = null
    // V5.17.0: Sentiment Engine — feeds socialVolume + newsImpact to AI Board
    private val sentimentEngine: SentimentEngine by lazy { SentimentEngine.getInstance(context) }
    // BUILD #353: MacroSentimentAnalyzer NOW WIRED ✅ (provides global macro data to Soros)
    private val macroSentimentAnalyzer: MacroSentimentAnalyzer by lazy { 
        MacroSentimentAnalyzer.getInstance(context) 
    }
    private var assetDiscoveryPipeline: AssetDiscoveryPipeline? = null
    private var marginSafeguard: MarginSafeguard? = null  // CRITICAL - NEVER BYPASS
    private var portfolioMarginManager: PortfolioMarginManager? = null  // Real-time margin sync
    
    // V5.17.0: Smart Order Routing — multi-exchange routing for AI path (Gap 3 fix)
    private var smartOrderRouter: SmartOrderRouter? = null
    private var smartOrderExecutor: SmartOrderExecutor? = null
    private var feeOptimizer: FeeOptimizer? = null
    private var slippageProtector: SlippageProtector? = null
    private var orderSplitter: OrderSplitter? = null
    private var _smartRoutingEnabled = MutableStateFlow(false)
    val smartRoutingEnabled: StateFlow<Boolean> = _smartRoutingEnabled.asStateFlow()
    
    // Current configuration
    private var config: TradingSystemConfig = TradingSystemConfig()
    
    // State
    private val _state = MutableStateFlow(IntegratedTradingState())
    val state: StateFlow<IntegratedTradingState> = _state.asStateFlow()
    
    private val _events = MutableSharedFlow<TradingSystemEvent>(replay = 0, extraBufferCapacity = 64)
    val events: SharedFlow<TradingSystemEvent> = _events.asSharedFlow()
    
    // Price feed subscription
    private var priceFeedJob: Job? = null
    private var orderBookFeedJob: Job? = null  // V5.17.0: WebSocket order book subscription
    
    // =========================================================================
    // INITIALIZATION
    // =========================================================================
    
    /**
     * Initialize the trading system with configuration.
     */
    suspend fun initialize(config: TradingSystemConfig): Result<Unit> {
        // BUILD #239: Guard against double-init. If already initialized and coordinator
        // is running, return success immediately rather than creating a new coordinator
        // that overwrites the running one and leaves collectors pointing at stale instance.
        if (_state.value.isInitialized && tradingCoordinator?.state?.value?.isRunning == true) {
            Log.w(TAG, "BUILD #239: Already initialized with running coordinator — skipping re-init")
            SystemLogger.system("⚠️ BUILD #239: Re-init blocked — coordinator already running. Using existing instance.")
            return Result.success(Unit)
        }
        
        SystemLogger.system("🚀 BUILD #256: Starting TradingSystemIntegration.initialize()")
        SystemLogger.system("🔍 BUILD #256: Execution mode = ${config.executionMode}")
        
        return try {
            Log.i(TAG, "Initializing trading system with mode: ${config.executionMode}")
            
            this.config = config
            
            // 1. Create AIConnectionManager
            aiConnectionManager = AIConnectionManager(context)
            
            // 2. Create adapter factory
            adapterFactory = AIExchangeAdapterFactory(context, aiConnectionManager)
            
            // 3. Create exchange adapter based on mode
            exchangeAdapter = when (config.executionMode) {
                TradingExecutionMode.PAPER -> {
                    adapterFactory!!.createAdapter(
                        mode = TradingExecutionMode.PAPER,
                        initialBalance = config.paperTradingBalance
                    )
                }
                TradingExecutionMode.PAPER_WITH_LIVE_DATA -> {
                    // First connect to exchange for price data
                    setupLiveExchange(config.primaryExchangeId)
                    adapterFactory!!.createAdapter(
                        mode = TradingExecutionMode.PAPER_WITH_LIVE_DATA,
                        exchangeId = config.primaryExchangeId,
                        initialBalance = config.paperTradingBalance
                    )
                }
                TradingExecutionMode.LIVE_AI -> {
                    setupLiveExchange(config.primaryExchangeId)
                    adapterFactory!!.createAdapter(
                        mode = TradingExecutionMode.LIVE_AI,
                        exchangeId = config.primaryExchangeId
                    )
                }
                TradingExecutionMode.LIVE_HARDCODED -> {
                    throw NotImplementedError("Use ExchangeRegistry for hardcoded mode")
                }
            }
            
            // 4. Create margin safeguard (CRITICAL - MUST NEVER BE BYPASSED)
            // BUILD #154: SKIP for paper trading - no real money, no margin risk!
            if (config.executionMode != TradingExecutionMode.PAPER &&
                config.executionMode != TradingExecutionMode.PAPER_WITH_LIVE_DATA) {
                marginSafeguard = MarginSafeguard.getInstance()
                Log.i(TAG, "🛡️ BUILD #154: MarginSafeguard ENABLED for ${config.executionMode}")
            } else {
                Log.w(TAG, "⚠️ BUILD #154: MarginSafeguard DISABLED for paper trading (no real money)")
            }
            
            // ================================================================
            // V5.17.0: SMART ORDER ROUTING SETUP (Gap 3 fix)
            // For LIVE_AI mode with multiple exchanges, route through SOR
            // for best execution. Paper mode uses direct adapter (no SOR needed).
            // Mirrors legacy TradingSystem.initializeWithPQC() SOR setup.
            // ================================================================
            
            val effectiveAdapter: ExchangeAdapter = if (
                config.enableSmartRouting && 
                config.executionMode == TradingExecutionMode.LIVE_AI
            ) {
                // Initialize SOR components
                feeOptimizer = FeeOptimizer()
                slippageProtector = SlippageProtector()
                orderSplitter = OrderSplitter()
                
                // Create Smart Order Router (priceFeedService=null — unused, AI path
                // handles price feeds via AIConnectionManager)
                smartOrderRouter = SmartOrderRouter(
                    priceFeedService = null,
                    feeOptimizer = feeOptimizer!!,
                    slippageProtector = slippageProtector!!,
                    orderSplitter = orderSplitter!!,
                    config = config.routingConfig,
                    scope = scope
                )
                
                // Register AI exchange adapter with the router
                // AIUnifiedExchangeAdapter implements RoutableExchangeAdapter (V5.17.0)
                (exchangeAdapter as? RoutableExchangeAdapter)?.let { routable ->
                    smartOrderRouter!!.registerExchange(routable)
                }
                
                // Register any additional connected exchanges
                aiConnectionManager?.getConnectedExchanges()?.forEach { (exchangeId, connector) ->
                    val aiAdapter = AIUnifiedExchangeAdapter(connector, scope)
                    if (aiAdapter.getExchangeId() != (exchangeAdapter as? RoutableExchangeAdapter)?.getExchangeId()) {
                        smartOrderRouter!!.registerExchange(aiAdapter)
                    }
                }
                
                // Create SmartOrderExecutor (implements ExchangeAdapter)
                smartOrderExecutor = SmartOrderExecutor(
                    router = smartOrderRouter!!,
                    config = SmartOrderExecutorConfig(
                        defaultStrategy = config.routingConfig.defaultStrategy
                    ),
                    scope = scope
                )
                
                _smartRoutingEnabled.value = true
                Log.i(TAG, "Smart order routing ENABLED for AI path — ${smartOrderRouter!!.getRegisteredExchanges().size} exchange(s)")
                
                // Use SmartOrderExecutor as the adapter for OrderExecutor
                smartOrderExecutor!!
            } else {
                // Paper trading or smart routing disabled — direct adapter
                _smartRoutingEnabled.value = false
                Log.i(TAG, "Smart order routing DISABLED — direct adapter mode")
                exchangeAdapter!!
            }
            
            // 5. Create order executor with margin safeguard as FINAL GATE
            // BUILD #154: marginSafeguard is null for paper trading (no real money risk)
            orderExecutor = OrderExecutor(effectiveAdapter, marginSafeguard, scope)
            
            // 6. Create position manager first (needed by risk manager)
            positionManager = PositionManager(orderExecutor, scope)
            
            // BUILD #274: Start trade recorder to capture closed positions for analytics
            tradeRecorder?.start(positionManager!!)
            Log.i(TAG, "📊 BUILD #274: TradeRecorder started - will persist closed trades for analytics")
            
            // 7. Create risk manager with position manager
            riskManager = RiskManager(positionManager!!, config.riskConfig, scope)
            
            // 8. Initialize margin safeguard with callbacks
            // BUILD #154: SKIP for paper trading - no margin monitoring needed!
            if (marginSafeguard != null) {
                marginSafeguard!!.initialize(
                    equity = config.paperTradingBalance,
                    usedMargin = 0.0,
                    getPositions = { getPositionSnapshots() },
                    reducePosition = { symbol, percent -> reducePositionByPercent(symbol, percent) },
                    closeAllPositions = { reason -> closeAllPositionsEmergency(reason) }
                )
                marginSafeguard!!.startMonitoring()
                Log.i(TAG, "🛡️ BUILD #154: MarginSafeguard monitoring STARTED")
            } else {
                Log.w(TAG, "⚠️ BUILD #154: MarginSafeguard monitoring SKIPPED (paper trading)")
            }
            
            // 8.5. Create portfolio margin manager for real-time sync
            // BUILD #154: SKIP for paper trading - no margin sync needed!
            if (marginSafeguard != null) {
                portfolioMarginManager = PortfolioMarginManager.getInstance()
                
                // Get AI connector for live data (if available)
                val aiConnector = aiConnectionManager?.getConnectedExchanges()?.values?.firstOrNull()
                
                portfolioMarginManager!!.initialize(
                    connector = null,  // Will use AI connector
                    aiConnector = aiConnector,
                    marginSafeguard = marginSafeguard!!
                )
                
                // Start sync for live trading modes
                if (config.executionMode == TradingExecutionMode.LIVE_AI ||
                    config.executionMode == TradingExecutionMode.PAPER_WITH_LIVE_DATA) {
                    portfolioMarginManager!!.startSync()
                    Log.i(TAG, "🛡️ BUILD #154: PortfolioMarginManager sync STARTED")
                }
            } else {
                Log.w(TAG, "⚠️ BUILD #154: PortfolioMarginManager SKIPPED (paper trading)")
            }
            
            // 9. Create trading coordinator
            val coordinatorConfig = TradingCoordinatorConfig(
                mode = config.tradingMode,
                analysisIntervalMs = config.analysisIntervalMs,
                minConfidenceToTrade = config.minConfidenceToTrade,
                minBoardAgreement = config.minBoardAgreement,  // BUILD #273
                useStahlStops = config.useStahlStops,
                maxConcurrentPositions = config.maxConcurrentPositions,
                enabledSymbols = config.tradingSymbols,
                paperTradingMode = config.executionMode == TradingExecutionMode.PAPER ||
                                   config.executionMode == TradingExecutionMode.PAPER_WITH_LIVE_DATA,
                initialCapital = config.paperTradingBalance,
                hybridConfig = config.hybridConfig
            )
            
            tradingCoordinator = TradingCoordinator(
                context = context,  // BUILD #366: Need Context for DQN weight persistence
                orderExecutor = orderExecutor!!,
                riskManager = riskManager!!,
                positionManager = positionManager!!,
                config = coordinatorConfig,
                scope = scope,
                boardDecisionRepository = boardDecisionRepository,
                tradeDao = tradeDao,
                sentimentEngine = sentimentEngine,
                macroSentimentAnalyzer = macroSentimentAnalyzer  // BUILD #353: NOW WIRED ✅
            )
            
            SystemLogger.system("✅ BUILD #256: TradingCoordinator created successfully")
            
            // 10. Wire up event forwarding
            setupEventForwarding()
            
            // 9. Run asset discovery pipeline (background)
            if (config.enableAssetDiscovery) {
                scope.launch {
                    runAssetDiscoveryPipeline()
                }
            }
            
            // 10. Update state
            updateState { it.copy(
                isInitialized = true,
                executionMode = config.executionMode,
                isTestnetMode = config.isTestnetMode
            )}
            
            SystemLogger.system("✅ BUILD #256: isInitialized=true, coordinator=${tradingCoordinator != null}")
            
            emitEvent(TradingSystemEvent.Initialized(config.executionMode))
            
            val envLabel = if (config.isTestnetMode) "TESTNET" else "PRODUCTION"
            Log.i(TAG, "Trading system initialized successfully [$envLabel]")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize trading system", e)
            updateState { it.copy(lastError = e.message) }
            emitEvent(TradingSystemEvent.Error("Initialization failed: ${e.message}", e))
            Result.failure(e)
        }
    }
    
    /**
     * Setup live exchange connection.
     */
    private suspend fun setupLiveExchange(exchangeId: String) {
        aiConnectionManager?.let { manager ->
            // V5.17.0: Pass credentials from config so the connector is created
            // with auth from the start. Previously credentials arrived post-init
            // via addExchange(), which created a NEW connector — leaving the
            // adapter pointing to the old credential-less one.
            val added = manager.addKnownExchange(
                exchangeId = exchangeId,
                credentials = config.primaryCredentials,
                autoConnect = true
            )
            
            if (added) {
                updateState { it.copy(
                    connectedExchanges = it.connectedExchanges + exchangeId
                )}
                emitEvent(TradingSystemEvent.ExchangeConnected(exchangeId))
                
                // Subscribe to health updates
                scope.launch {
                    manager.healthUpdates.collect { health ->
                        updateState { it.copy(
                            exchangeHealth = it.exchangeHealth + (health.exchangeId to health.status)
                        )}
                    }
                }
            }
        }
    }
    
    /**
     * Wire up event forwarding from coordinator to integrated events.
     */
    private fun setupEventForwarding() {
        // Forward coordinator events
        scope.launch {
            tradingCoordinator?.events?.collect { event ->
                when (event) {
                    is CoordinatorEvent.TradeExecuted -> {
                        emitEvent(TradingSystemEvent.TradeExecuted(event.trade))
                    }
                    is CoordinatorEvent.SignalGenerated -> {
                        emitEvent(TradingSystemEvent.SignalGenerated(event.signal))
                    }
                    is CoordinatorEvent.PositionUpdated -> {
                        emitEvent(TradingSystemEvent.PositionUpdated(event.position))
                    }
                    is CoordinatorEvent.TradingStarted -> {
                        emitEvent(TradingSystemEvent.TradingStarted)
                    }
                    is CoordinatorEvent.TradingStopped -> {
                        emitEvent(TradingSystemEvent.TradingStopped)
                    }
                    is CoordinatorEvent.ModeChanged -> {
                        emitEvent(TradingSystemEvent.ModeChanged(event.newMode))
                    }
                    is CoordinatorEvent.Error -> {
                        emitEvent(TradingSystemEvent.Error(event.message, event.exception))
                    }
                    else -> { /* Ignore other events */ }
                }
            }
        }
        
        // Forward coordinator state changes
        // BUILD #299: Also update portfolioValue from coordinator's correct calculation
        scope.launch {
            tradingCoordinator?.state?.collect { coordState ->
                val portfolioValue = tradingCoordinator?.getPortfolioValue() ?: _state.value.portfolioValue
                updateState { it.copy(
                    coordinatorState = coordState,
                    portfolioValue = portfolioValue  // BUILD #299: Use coordinator's correct formula
                )}
            }
        }
        
        // V5.17.0: Balance polling extracted to startBalancePolling() so it can
        // also be called from startPriceFeedOnly(). This was the root cause of
        // the A$0.00 balance bug — polling only ran inside start(), which was
        // never called for auto-start paper trading.
        startBalancePolling()
        startBoardDecisionPruning()  // BUILD #267: Prevent unbounded XAI DB growth
    }
    
    /**
     * V5.17.0: Poll paper trading balances on a 5-second cycle.
     * 
     * Extracted from start() so that startPriceFeedOnly() can also invoke it.
     * Without this, paper trading balance stays at A$0.00 because the
     * IntegratedTradingState default is 0.0 and nothing ever updates it.
     */
    private var balancePollingJob: Job? = null
    private var boardDecisionPruneJob: Job? = null  // BUILD #267: Prevent unbounded XAI DB growth

    private fun startBalancePolling() {
        if (exchangeAdapter !is PaperTradingAdapter) return
        if (balancePollingJob?.isActive == true) return  // Don't double-start
        
        balancePollingJob = scope.launch {
            // V5.17.0: Set initial balance immediately so UI never shows A$0.00
            val paperAdapter = exchangeAdapter as PaperTradingAdapter
            val initialBalances = paperAdapter.getAllBalances()
            updateState { it.copy(
                balances = initialBalances,
                portfolioValue = config.paperTradingBalance
            )}
            
            while (true) {
                delay(5000)
                val balances = paperAdapter.getAllBalances()
                val portfolioValue = paperAdapter.getPortfolioValue()
                updateState { it.copy(
                    balances = balances,
                    portfolioValue = portfolioValue
                )}
            }
        }
    }
    
    // =========================================================================
    // TRADING CONTROL
    // =========================================================================
    
    /**
     * V5.17.0: Start price feed only, without starting trading coordinator.
     * 
     * Called automatically after paper trading initialization so that
     * price data flows immediately. The user can then toggle the AI
     * trading coordinator on/off independently via the Dashboard toggle.
     * 
     * Without this, paper trading was broken: prices never appeared
     * because start() was gated behind the user tapping "AI Trading".
     */
    fun startPriceFeedOnly() {
        if (!_state.value.isInitialized) {
            Log.w(TAG, "Cannot start price feed: System not initialized")
            return
        }
        
        Log.i(TAG, "Starting price feed + balance polling (trading coordinator NOT started)")
        startPriceFeed()
        startOrderBookFeed()
        // V5.17.0: Start balance polling so portfolio value appears on dashboard.
        // Without this, paper trading balance stays at A$0.00.
        startBalancePolling()
    }
    
    /**
     * Start trading.
     */
    fun start() {
        if (!_state.value.isInitialized) {
            Log.w(TAG, "Cannot start: System not initialized")
            return
        }
        
        sentimentEngine.start()  // V5.17.0: Start sentiment analysis feed
        tradingCoordinator?.start()
        startPriceFeed()
        startOrderBookFeed()  // V5.17.0: WebSocket order book for real bid/ask
        startBalancePolling()  // V5.19.0 BUILD #101 FIX: Start balance polling for paper trading
    }
    
    /**
     * Stop trading.
     */
    fun stop() {
        tradingCoordinator?.stop()
        sentimentEngine.stop()  // V5.17.0: Stop sentiment analysis feed
        stopPriceFeed()
        stopOrderBookFeed()  // V5.17.0
        
        // BUILD #103: Cancel balance polling to prevent memory leak
        balancePollingJob?.cancel()
        balancePollingJob = null
        
        // BUILD #267: Cancel board decision pruning job
        boardDecisionPruneJob?.cancel()
        boardDecisionPruneJob = null
        
        Log.i(TAG, "🛑 All jobs cancelled, trading system stopped")
    }
    
    /**
     * BUILD #267: Periodically prune old board decisions from Room DB.
     *
     * The board writes 16 decisions/minute (4 symbols × every 15s).
     * Each decision = 1 BoardDecisionEntity + 8 MemberVoteEntities.
     * Without pruning = ~8,640 rows/hour = unbounded DB + WAL memory growth.
     *
     * Policy: keep last 24 hours, prune everything older every 60 minutes.
     * Max retained rows ≈ 16/min × 60min × 24h × 9 rows = ~207,360 rows.
     * Pruned hourly so in-memory WAL never accumulates more than 1hr of writes.
     */
    private fun startBoardDecisionPruning() {
        if (boardDecisionPruneJob?.isActive == true) return
        boardDecisionPruneJob = scope.launch {
            // First prune after 5 minutes (let system settle)
            delay(5 * 60 * 1000L)
            while (isActive) {
                try {
                    boardDecisionRepository?.let { repo ->
                        val pruned = repo.purgeOlderThan(days = 1)
                        if (pruned > 0) {
                            Log.i(TAG, "🧹 BUILD #267: Pruned $pruned old board decisions (keeping 24h)")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "BUILD #267: Board decision pruning failed: ${e.message}")
                }
                // Run every 60 minutes
                delay(60 * 60 * 1000L)
            }
        }
    }
    
    /**
     * BUILD #111: Expose coordinator for external price feed integration.
     * Allows TradingSystemManager to wire BinancePublicPriceFeed directly
     * to the coordinator's onPriceTick() callback.
     */
    fun getTradingCoordinator(): TradingCoordinator? {
        SystemLogger.d(TAG, "🔍 BUILD #256: getTradingCoordinator() called - returning ${tradingCoordinator != null}")
        return tradingCoordinator
    }
    
    /**
     * Start price feed subscription.
     * 
     * V5.17.0 CHANGE: When multiple exchanges are connected, uses
     * AIConnectionManager.subscribeToAllPrices() for cross-exchange
     * price aggregation. Essential for arb/hedge spread detection.
     * Falls back to single-connector subscription when only one exchange.
     */
    private fun startPriceFeed() {
        priceFeedJob?.cancel()
        
        priceFeedJob = scope.launch {
            when (val adapter = exchangeAdapter) {
                is AIUnifiedExchangeAdapter -> {
                    val connectedCount = aiConnectionManager?.getExchangeIds()?.size ?: 0
                    
                    if (connectedCount > 1 && aiConnectionManager != null) {
                        // V5.17.0: Multi-exchange price aggregation for arb/hedge
                        Log.i(TAG, "Starting multi-exchange price feed ($connectedCount exchanges)")
                        aiConnectionManager!!.subscribeToAllPrices(config.tradingSymbols).collect { tick ->
                            // Route to coordinator with exchange source for arb spread tracking
                            tradingCoordinator?.onPriceTick(tick.symbol, tick.last, tick.volume, tick.exchange)
                        }
                    } else {
                        // Single exchange — direct subscription
                        adapter.subscribeToPrices(config.tradingSymbols).collect { tick ->
                            tradingCoordinator?.onPriceTick(tick.symbol, tick.last, tick.volume, tick.exchange)
                        }
                    }
                }
                is PaperTradingAdapter -> {
                    // For paper trading, use simulated prices or poll from AI connector
                    SystemLogger.d(TAG, "🔍 BUILD #123 DIAGNOSTIC: Paper trading adapter detected")
                    SystemLogger.d(TAG, "🔍 BUILD #123: primaryExchangeId = ${config.primaryExchangeId}")
                    SystemLogger.d(TAG, "🔍 BUILD #123: aiConnectionManager = $aiConnectionManager")
                    
                    val connector = aiConnectionManager?.getConnector(config.primaryExchangeId)
                    SystemLogger.d(TAG, "🔍 BUILD #123: getConnector returned: $connector")
                    
                    connector?.let { conn ->
                        SystemLogger.i(TAG, "✅ BUILD #123: Using AI connector for prices (this should NOT happen in pure paper mode!)")
                        conn.subscribeToPrices(config.tradingSymbols).collect { tick ->
                            SystemLogger.d(TAG, "💰 BUILD #123: Price from AI connector: ${tick.symbol} = ${tick.last}")
                            adapter.setPrice(tick.symbol, tick.last)
                            tradingCoordinator?.onPriceTick(tick.symbol, tick.last, tick.volume, tick.exchange)
                        }
                    } ?: run {
                        // Simulated price updates for pure paper trading
                        SystemLogger.i(TAG, "✅ BUILD #123: No AI connector found, using simulatePriceUpdates() → BinancePublicPriceFeed")
                        // BUILD #132: Launch in separate coroutine to prevent blocking
                        scope.launch {
                            SystemLogger.i(TAG, "🚀 BUILD #132: Launched simulatePriceUpdates() coroutine")
                            simulatePriceUpdates()
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Stop price feed subscription.
     */
    private fun stopPriceFeed() {
        priceFeedJob?.cancel()
        priceFeedJob = null
    }
    
    /**
     * V5.17.0: Start WebSocket order book feed across all connected exchanges.
     * 
     * Subscribes to order book depth for all trading symbols on every connected
     * exchange. OrderBook snapshots are routed to TradingCoordinator.onOrderBookUpdate()
     * which updates crossExchangePrices with real best-bid/best-ask — replacing the
     * bid=ask=last approximation from ticker polls.
     * 
     * Only activates when >1 exchange is connected (arb/hedge use case).
     * Single-exchange setups get bid/ask from the ticker feed which is sufficient.
     */
    private fun startOrderBookFeed() {
        orderBookFeedJob?.cancel()
        
        val connectedCount = aiConnectionManager?.getExchangeIds()?.size ?: 0
        if (connectedCount <= 1) {
            Log.d(TAG, "Order book feed skipped: single exchange (no arb use case)")
            return
        }
        
        val manager = aiConnectionManager ?: return
        
        orderBookFeedJob = scope.launch {
            Log.i(TAG, "Starting order book feed ($connectedCount exchanges, ${config.tradingSymbols.size} symbols)")
            try {
                manager.subscribeToAllOrderBooks(config.tradingSymbols).collect { book ->
                    tradingCoordinator?.onOrderBookUpdate(book)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Order book feed error: ${e.message}", e)
            }
        }
    }
    
    /**
     * V5.17.0: Stop order book feed subscription.
     */
    private fun stopOrderBookFeed() {
        orderBookFeedJob?.cancel()
        orderBookFeedJob = null
    }
    
    /**
     * Simulate price updates for pure paper trading mode.
     */
    private suspend fun simulatePriceUpdates() {
        // V5.18.0: Use Binance public REST API for REAL prices (no auth needed).
        // Falls back to random-walk mock if Binance is unreachable.
        SystemLogger.i(TAG, "🚀 BUILD #123: simulatePriceUpdates() called - starting BinancePublicPriceFeed")
        
        val priceFeed = BinancePublicPriceFeed.getInstance()
        val symbols = config.tradingSymbols.ifEmpty {
            BinancePublicPriceFeed.DEFAULT_SYMBOLS
        }
        SystemLogger.i(TAG, "🚀 BUILD #123: Starting price feed for ${symbols.size} symbols: $symbols")
        priceFeed.start(symbols)
        SystemLogger.i(TAG, "✅ BUILD #128: priceFeed.start() called successfully")

        // Collect real price ticks and route to paper adapter + coordinator
        try {
            SystemLogger.i(TAG, "🚀 BUILD #128: Now entering priceFeed.priceTicks.collect{} loop...")
            priceFeed.priceTicks.collect { tick ->
                SystemLogger.i(TAG, "💰 BUILD #132: PRICE TICK COLLECTED! ${tick.symbol} = ${tick.last} (THIS LOG CONFIRMS COLLECT WORKS!)")
                (exchangeAdapter as? PaperTradingAdapter)?.setPrice(tick.symbol, tick.last)
                tradingCoordinator?.onPriceTick(tick.symbol, tick.last, tick.volume24h, "binance")
                
                // Also update state with latest prices for dashboard
                updateState { state ->
                    val updatedPrices = state.latestPrices.toMutableMap()
                    updatedPrices[tick.symbol] = tick.last
                    state.copy(latestPrices = updatedPrices)
                }
            }
        } catch (e: CancellationException) {
            SystemLogger.w(TAG, "⚠️ BUILD #128: Price feed collection cancelled")
            priceFeed.stop()
            throw e
        } catch (e: Exception) {
            // Binance unreachable — fall back to random walk with reasonable seed prices
            SystemLogger.w(TAG, "⚠️ BUILD #128: Binance public feed failed: ${e.message}")
            priceFeed.stop()
            fallbackSimulatedPrices()
        }
    }

    /**
     * V5.18.0: Fallback random-walk price simulation when Binance is unreachable.
     * Uses 2026-era seed prices so the demo doesn't look stale.
     */
    private suspend fun fallbackSimulatedPrices() {
        val prices = mutableMapOf(
            "BTC/USDT" to 85000.0,
            "ETH/USDT" to 3200.0,
            "SOL/USDT" to 145.0,
            "XRP/USDT" to 2.30,
            "DOGE/USDT" to 0.25
        )

        while (currentCoroutineContext().isActive) {
            for ((symbol, price) in prices) {
                val change = (Math.random() - 0.5) * price * 0.001
                val newPrice = price + change
                prices[symbol] = newPrice

                (exchangeAdapter as? PaperTradingAdapter)?.setPrice(symbol, newPrice)
                tradingCoordinator?.onPriceTick(symbol, newPrice, 100.0)
                
                updateState { state ->
                    val updatedPrices = state.latestPrices.toMutableMap()
                    updatedPrices[symbol] = newPrice
                    state.copy(latestPrices = updatedPrices)
                }
            }
            delay(1000)
        }
    }
    
    // =========================================================================
    // TRADING OPERATIONS
    // =========================================================================
    
    /**
     * Change trading mode.
     */
    fun setTradingMode(mode: TradingMode) {
        tradingCoordinator?.setMode(mode)
    }
    
    /**
     * Update hybrid mode configuration.
     */
    fun updateHybridConfig(hybridConfig: HybridModeConfig) {
        tradingCoordinator?.updateHybridConfig(hybridConfig)
    }
    
    /**
     * Confirm a pending trade signal.
     */
    suspend fun confirmSignal(signalId: String): Result<ExecutedTrade> {
        return tradingCoordinator?.confirmSignal(signalId)
            ?: Result.failure(IllegalStateException("Trading coordinator not initialized"))
    }
    
    /**
     * Reject a pending trade signal.
     */
    fun rejectSignal(signalId: String) {
        tradingCoordinator?.rejectSignal(signalId)
    }
    
    /**
     * Get pending signals.
     */
    fun getPendingSignals(): List<PendingTradeSignal> {
        return tradingCoordinator?.getPendingSignals() ?: emptyList()
    }
    
    /**
     * Get managed positions.
     * 
     * BUILD #158 FIX: Combines positions from BOTH PositionManager (manual trades)
     * and TradingCoordinator (AI trades) so UI shows ALL positions.
     * 
     * ISSUE: Manual trades from BUY/SELL buttons were being added to PositionManager,
     * but UI was only reading from TradingCoordinator, causing "trades in logs but 
     * not showing in UI" bug reported by Mike.
     */
    fun getManagedPositions(): List<ManagedPosition> {
        val coordinatorPositions = tradingCoordinator?.getManagedPositions() ?: emptyList()
        
        // BUILD #158: Also get positions from PositionManager (manual trades)
        val manualPositions = positionManager?.allPositions?.value?.map { pos ->
            // Convert PositionManager.Position to ManagedPosition for UI
            ManagedPosition(
                symbol = pos.symbol,
                direction = if (pos.side == TradeSide.BUY || pos.side == TradeSide.LONG) 
                    TradeDirection.LONG else TradeDirection.SHORT,
                entryPrice = pos.averageEntryPrice,
                currentPrice = pos.currentPrice,
                quantity = pos.quantity,
                currentStop = pos.currentStopPrice,
                currentTarget = pos.takeProfitPrice,
                stahlLevel = pos.stahlLevel,
                unrealizedPnL = pos.unrealizedPnl,
                unrealizedPnLPercent = pos.unrealizedPnlPercent,
                entryTime = pos.openTime,
                orderId = pos.id  // Use position ID as order ID
            )
        } ?: emptyList()
        
        // BUILD #301: Don't deduplicate by symbol - coordinator supports multiple positions per symbol
        // Previous bug: .groupBy { it.symbol } threw away all but first position per symbol
        // Fix: Deduplicate by orderId instead (each position has unique orderId)
        val allPositions = (coordinatorPositions + manualPositions)
            .distinctBy { it.orderId }  // Remove true duplicates, not same-symbol positions
        
        return allPositions
    }
    
    /**
     * V5.18.0: Get paper trading balances (asset -> amount).
     */
    fun getBalances(): Map<String, Double> {
        return (exchangeAdapter as? PaperTradingAdapter)?.getAllBalances() ?: _state.value.balances
    }
    
    /**
     * Close a specific position.
     */
    suspend fun closePosition(symbol: String): Result<Unit> {
        return tradingCoordinator?.closePosition(symbol)
            ?: Result.failure(IllegalStateException("Trading coordinator not initialized"))
    }

    /**
     * BUILD #270: Close a specific position by unique key (symbol_orderId).
     */
    suspend fun closePositionById(positionKey: String): Result<Unit> {
        return tradingCoordinator?.closePositionById(positionKey)
            ?: Result.failure(IllegalStateException("Trading coordinator not initialized"))
    }
    
    /**
     * Place an order through the integrated trading system.
     * Supports both paper trading and live trading modes.
     * 
     * CRITICAL: This goes through MarginSafeguard (FINAL GATE) before execution.
     */
    suspend fun placeOrder(orderRequest: OrderRequest): Result<ExecutedTrade> {
        if (!_state.value.isInitialized) {
            return Result.failure(IllegalStateException("Trading system not initialized"))
        }
        
        // Check if trading is allowed (margin health, kill switch, etc.)
        if (marginSafeguard?.isTradingAllowed?.value == false) {
            return Result.failure(IllegalStateException("Trading not allowed - margin health critical or kill switch active"))
        }
        
        return try {
            // Execute through OrderExecutor (which has MarginSafeguard as FINAL GATE)
            val result = orderExecutor?.executeOrder(orderRequest)
                ?: return Result.failure(IllegalStateException("Order executor not initialized"))
            
            when (result) {
                is OrderExecutionResult.Success -> {
                    // Convert to ExecutedTrade
                    val direction = when (orderRequest.side) {
                        TradeSide.BUY, TradeSide.LONG -> TradeDirection.LONG
                        TradeSide.SELL, TradeSide.SHORT -> TradeDirection.SHORT
                        else -> TradeDirection.LONG
                    }
                    
                    val trade = ExecutedTrade(
                        id = java.util.UUID.randomUUID().toString(),
                        symbol = orderRequest.symbol,
                        direction = direction,
                        entryPrice = result.order.executedPrice,
                        quantity = result.order.executedQuantity,
                        stopLoss = orderRequest.stopLossPrice ?: (result.order.executedPrice * 0.95),
                        takeProfit = orderRequest.takeProfitPrice ?: (result.order.executedPrice * 1.10),
                        orderId = result.order.orderId,
                        timestamp = result.order.timestamp,
                        fromSignalId = null,
                        wasAutonomous = false
                    )
                    
                    // Update position manager
                    positionManager?.let { pm ->
                        val existingPositions = pm.getPositionsForSymbol(orderRequest.symbol)
                        val matchingSide = existingPositions.find { pos ->
                            (pos.side == orderRequest.side) ||
                            (pos.side == TradeSide.BUY && orderRequest.side == TradeSide.LONG) ||
                            (pos.side == TradeSide.LONG && orderRequest.side == TradeSide.BUY) ||
                            (pos.side == TradeSide.SELL && orderRequest.side == TradeSide.SHORT) ||
                            (pos.side == TradeSide.SHORT && orderRequest.side == TradeSide.SELL)
                        }
                        
                        if (matchingSide != null) {
                            pm.addToPosition(
                                positionId = matchingSide.id,
                                quantity = result.order.executedQuantity,
                                price = result.order.executedPrice
                            )
                        } else {
                            pm.openPosition(
                                symbol = orderRequest.symbol,
                                side = orderRequest.side,
                                quantity = result.order.executedQuantity,
                                entryPrice = result.order.executedPrice,
                                leverage = orderRequest.leverage?.toDouble() ?: 1.0,
                                exchange = result.order.exchange,
                                useStahl = config.useStahlStops
                            )
                        }
                    }
                    
                    // BUILD #333: Increment trade counters for Dashboard display (FIXED - use actual coordinator)
                    tradingCoordinator?.incrementTradeCounters()
                    
                    // Emit trade event
                    emitEvent(TradingSystemEvent.TradeExecuted(trade))
                    
                    Result.success(trade)
                }
                is OrderExecutionResult.PartialFill -> {
                    // Partial fills treated as success with reduced quantity
                    val direction = when (orderRequest.side) {
                        TradeSide.BUY, TradeSide.LONG -> TradeDirection.LONG
                        TradeSide.SELL, TradeSide.SHORT -> TradeDirection.SHORT
                        else -> TradeDirection.LONG
                    }
                    
                    val trade = ExecutedTrade(
                        id = java.util.UUID.randomUUID().toString(),
                        symbol = orderRequest.symbol,
                        direction = direction,
                        entryPrice = result.order.executedPrice,
                        quantity = result.order.executedQuantity,
                        stopLoss = orderRequest.stopLossPrice ?: (result.order.executedPrice * 0.95),
                        takeProfit = orderRequest.takeProfitPrice ?: (result.order.executedPrice * 1.10),
                        orderId = result.order.orderId,
                        timestamp = result.order.timestamp,
                        fromSignalId = null,
                        wasAutonomous = false
                    )
                    
                    Log.w(TAG, "Partial fill: ${result.order.executedQuantity}/${orderRequest.quantity} filled")
                    emitEvent(TradingSystemEvent.TradeExecuted(trade))
                    
                    Result.success(trade)
                }
                is OrderExecutionResult.Rejected -> {
                    Log.w(TAG, "Order rejected: ${result.reason}")
                    Result.failure(Exception("Order rejected: ${result.reason}"))
                }
                is OrderExecutionResult.Error -> {
                    Log.e(TAG, "Order error", result.exception)
                    Result.failure(result.exception)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "placeOrder failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get portfolio value.
     */
    fun getPortfolioValue(): Double {
        return _state.value.portfolioValue
    }
    
    /**
     * Check if trading is allowed.
     */
    fun isTradingAllowed(): Boolean {
        return marginSafeguard?.isTradingAllowed?.value ?: true
    }
    
    /**
     * Trigger emergency stop - close all positions.
     */
    suspend fun emergencyStop(reason: String) {
        tradingCoordinator?.emergencyStop(reason)
    }
    
    /**
     * Reset emergency stop.
     */
    fun resetEmergencyStop() {
        tradingCoordinator?.resetEmergencyStop()
    }
    
    // =========================================================================
    // EXCHANGE OPERATIONS
    // =========================================================================
    
    /**
     * Add, connect, and register an exchange with smart routing.
     * 
     * V5.17.0 CHANGE: After connecting via AIConnectionManager, the new
     * exchange is wrapped in AIUnifiedExchangeAdapter and registered with
     * SmartOrderRouter (if SOR is active). This enables late-joined
     * exchanges to participate in arb/hedge routing immediately.
     */
    suspend fun addExchange(
        exchangeId: String,
        credentials: ExchangeCredentials? = null
    ): Boolean {
        val added = aiConnectionManager?.addKnownExchange(
            exchangeId = exchangeId,
            credentials = credentials,
            autoConnect = true
        ) ?: false
        
        if (!added) return false
        
        // Update connected exchanges state
        updateState { it.copy(
            connectedExchanges = it.connectedExchanges + exchangeId
        )}
        emitEvent(TradingSystemEvent.ExchangeConnected(exchangeId))
        
        // V5.17.0: Register with SmartOrderRouter for arb/hedge routing
        if (_smartRoutingEnabled.value && smartOrderRouter != null) {
            aiConnectionManager?.getConnector(exchangeId)?.let { connector ->
                val aiAdapter = AIUnifiedExchangeAdapter(connector, scope)
                smartOrderRouter!!.registerExchange(aiAdapter)
                Log.i(TAG, "Dynamic SOR registration: $exchangeId added to smart routing")
            }
        }
        
        // Subscribe to health updates for this exchange
        scope.launch {
            aiConnectionManager?.healthUpdates
                ?.filter { it.exchangeId == exchangeId }
                ?.collect { health ->
                    updateState { it.copy(
                        exchangeHealth = it.exchangeHealth + (health.exchangeId to health.status)
                    )}
                }
        }
        
        Log.i(TAG, "Exchange added: $exchangeId (SOR: ${_smartRoutingEnabled.value})")
        return true
    }
    
    /**
     * Remove an exchange from the trading system.
     * 
     * V5.17.0: Cleanly removes from AIConnectionManager AND SmartOrderRouter.
     */
    suspend fun removeExchange(exchangeId: String) {
        // Remove from SOR first (while connector still exists)
        if (_smartRoutingEnabled.value && smartOrderRouter != null) {
            smartOrderRouter!!.unregisterExchange(exchangeId)
            Log.i(TAG, "Dynamic SOR removal: $exchangeId removed from smart routing")
        }
        
        // Disconnect and remove from connection manager
        aiConnectionManager?.removeExchange(exchangeId)
        
        // Update state
        updateState { it.copy(
            connectedExchanges = it.connectedExchanges - exchangeId,
            exchangeHealth = it.exchangeHealth - exchangeId
        )}
        emitEvent(TradingSystemEvent.ExchangeDisconnected(exchangeId))
        
        Log.i(TAG, "Exchange removed: $exchangeId")
    }
    
    /**
     * Get all connected exchanges.
     */
    fun getConnectedExchanges(): Set<String> {
        return _state.value.connectedExchanges
    }
    
    /**
     * Get exchange health status.
     */
    fun getExchangeHealth(): Map<String, ConnectionHealth?> {
        return aiConnectionManager?.getAllHealth() ?: emptyMap()
    }
    
    // =========================================================================
    // PAPER TRADING SPECIFIC
    // =========================================================================
    
    /**
     * Reset paper trading account.
     */
    fun resetPaperTrading(newBalance: Double = config.paperTradingBalance) {
        (exchangeAdapter as? PaperTradingAdapter)?.resetAccount(newBalance)
    }
    
    /**
     * Get paper trading balance.
     */
    fun getPaperTradingBalance(asset: String = "USDT"): Double {
        return (exchangeAdapter as? PaperTradingAdapter)?.getBalance(asset) ?: 0.0
    }
    
    /**
     * Get paper trading order history.
     */
    fun getPaperTradingHistory(): List<ExecutedOrder> {
        return (exchangeAdapter as? PaperTradingAdapter)?.getOrderHistory() ?: emptyList()
    }
    
    // =========================================================================
    // MARGIN SAFEGUARD (CRITICAL - NEVER BYPASS)
    // =========================================================================
    
    /**
     * Get margin status - the health of our margin position.
     */
    fun getMarginStatus(): MarginStatus? = marginSafeguard?.marginStatus?.value
    
    /**
     * Get margin safeguard instance.
     */
    fun getMarginSafeguard(): MarginSafeguard? = marginSafeguard
    
    /**
     * Get position snapshots for margin calculations.
     */
    private fun getPositionSnapshots(): List<PositionSnapshot> {
        return positionManager?.allPositions?.value?.map { position ->
            PositionSnapshot(
                symbol = position.symbol,
                quantity = position.quantity,
                entryPrice = position.averageEntryPrice,
                currentPrice = position.currentPrice,
                leverage = position.leverage,
                margin = position.margin,
                unrealizedPnl = position.unrealizedPnl,
                side = if (position.side == TradeSide.BUY || position.side == TradeSide.LONG) "long" else "short"
            )
        } ?: emptyList()
    }
    
    /**
     * Reduce a position by percentage (for auto-deleverage).
     */
    private suspend fun reducePositionByPercent(symbol: String, percent: Double): Boolean {
        val positions = positionManager?.getPositionsForSymbol(symbol) ?: return false
        val position = positions.firstOrNull() ?: return false
        val quantityToClose = position.quantity * (percent / 100)
        
        val closeSide = if (position.side == TradeSide.BUY || position.side == TradeSide.LONG) {
            TradeSide.SELL
        } else {
            TradeSide.BUY
        }
        
        val result = orderExecutor?.executeMarketOrder(symbol, closeSide, quantityToClose)
        return result is OrderExecutionResult.Success
    }
    
    /**
     * Close all positions in emergency (margin liquidation).
     */
    private suspend fun closeAllPositionsEmergency(reason: String) {
        Log.e(TAG, "🚨 EMERGENCY CLOSE ALL: $reason")
        
        val positions = positionManager?.allPositions?.value ?: return
        
        for (position in positions) {
            val closeSide = if (position.side == TradeSide.BUY || position.side == TradeSide.LONG) {
                TradeSide.SELL
            } else {
                TradeSide.BUY
            }
            
            try {
                orderExecutor?.executeMarketOrder(position.symbol, closeSide, position.quantity)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to close ${position.symbol}: ${e.message}")
            }
        }
        
        emitEvent(TradingSystemEvent.EmergencyStop(reason))
    }
    
    // =========================================================================
    // ASSET DISCOVERY PIPELINE
    // =========================================================================
    
    /**
     * Run asset discovery pipeline.
     * Discovers assets from exchanges, assigns risk parameters, registers in AssetRegistry.
     */
    suspend fun runAssetDiscoveryPipeline(): Int {
        assetDiscoveryPipeline = AssetDiscoveryPipeline.getInstance(context)
        
        val pipelineConfig = PipelineConfig(
            exchanges = config.discoveryConfig.exchanges,
            enableDeFiLlamaEnrichment = config.discoveryConfig.enableDeFiLlama,
            minVolume24h = config.discoveryConfig.minVolume24h
        )
        
        return assetDiscoveryPipeline!!.runFullPipeline(pipelineConfig)
    }
    
    /**
     * Run discovery for connected exchanges via AI connection manager.
     */
    suspend fun runDiscoveryForConnectedExchanges(): Int {
        aiConnectionManager?.let { manager ->
            assetDiscoveryPipeline = AssetDiscoveryPipeline.getInstance(context)
            return assetDiscoveryPipeline!!.runFromConnectionManager(manager)
        }
        return 0
    }
    
    /**
     * Get asset discovery pipeline instance.
     */
    fun getAssetDiscoveryPipeline(): AssetDiscoveryPipeline? = assetDiscoveryPipeline
    
    /**
     * Get asset discovery progress.
     */
    fun getDiscoveryProgress(): StateFlow<DiscoveryProgress>? {
        return assetDiscoveryPipeline?.getDiscoveryProgress()
    }
    
    // =========================================================================
    // HELPERS
    // =========================================================================
    
    private fun updateState(updater: (IntegratedTradingState) -> IntegratedTradingState) {
        _state.update(updater)
    }
    
    private fun emitEvent(event: TradingSystemEvent) {
        scope.launch {
            _events.emit(event)
        }
    }
    
    /**
     * Get the underlying trading coordinator for advanced operations.
     */
    fun getCoordinator(): TradingCoordinator? = tradingCoordinator
    
    /**
     * Get the AI connection manager.
     */
    fun getConnectionManager(): AIConnectionManager? = aiConnectionManager
    
    /**
     * V5.17.0: Check if running in testnet/sandbox mode.
     */
    fun isTestnetMode(): Boolean = _state.value.isTestnetMode
    
    /**
     * Get the portfolio margin manager.
     */
    fun getPortfolioManager(): PortfolioMarginManager? = portfolioMarginManager
    
    /**
     * Get current portfolio state.
     */
    fun getPortfolioState(): PortfolioState? = portfolioMarginManager?.portfolioState?.value
    
    /**
     * Observe portfolio state changes.
     */
    fun observePortfolio(): StateFlow<PortfolioState>? = portfolioMarginManager?.portfolioState
    
    /**
     * Observe margin status changes.
     */
    fun observeMarginStatus(): StateFlow<MarginStatus?>? = marginSafeguard?.marginStatus
    
    /**
     * Check if a trade can be afforded.
     */
    fun canAffordTrade(quantity: Double, price: Double, leverage: Double): Boolean {
        return portfolioMarginManager?.canAffordTrade(quantity, price, leverage) ?: false
    }
    
    /**
     * Get maximum affordable quantity.
     */
    fun getMaxAffordableQuantity(price: Double, leverage: Double): Double {
        return portfolioMarginManager?.getMaxAffordableQuantity(price, leverage) ?: 0.0
    }

    /**
     * Shutdown the entire trading system.
     */
    fun shutdown() {
        stop()
        tradingCoordinator = null
        orderExecutor?.shutdown()
        // V5.17.0: Smart Order Router cleanup
        smartOrderExecutor?.shutdown()
        smartOrderExecutor = null
        smartOrderRouter?.shutdown()
        smartOrderRouter = null
        feeOptimizer = null
        slippageProtector = null
        orderSplitter = null
        _smartRoutingEnabled.value = false
        // Core component cleanup
        marginSafeguard?.shutdown()
        portfolioMarginManager?.shutdown()
        aiConnectionManager?.shutdown()
        adapterFactory?.shutdown()
        assetDiscoveryPipeline?.shutdown()
        scope.cancel()
        INSTANCE = null
        Log.i(TAG, "Trading system shutdown complete")
    }
}

/**
 * Extension to get trading system instance from context.
 */
fun Context.getTradingSystem(): TradingSystemIntegration {
    return TradingSystemIntegration.getInstance(this)
}
