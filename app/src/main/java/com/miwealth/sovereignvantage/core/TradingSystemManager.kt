package com.miwealth.sovereignvantage.core

import android.content.Context
import android.util.Log
import com.miwealth.sovereignvantage.core.exchange.*
import com.miwealth.sovereignvantage.core.exchange.BinancePublicPriceFeed
import com.miwealth.sovereignvantage.core.exchange.ai.AIConnectionManager
import com.miwealth.sovereignvantage.core.exchange.ai.ExchangeTestnetConfig
import com.miwealth.sovereignvantage.core.exchange.ai.TradingExecutionMode
import com.miwealth.sovereignvantage.core.exchange.tick.*  // BUILD #241: Universal Tick Buffer (deprecated in #242)
// BUILD #254: Removed Build #242-244 imports (classes deleted):
// - RealtimeDQNLearner (deleted)
// - EnhancedFeatureExtractor (deleted)
// - RollingTickWindow (deleted)
// - TickData (deleted)
import com.miwealth.sovereignvantage.core.ml.DQNTrader  // BUILD #242: DQN agent
import com.miwealth.sovereignvantage.core.security.EncryptedPrefsManager
import com.miwealth.sovereignvantage.core.trading.*
import com.miwealth.sovereignvantage.core.trading.assets.PipelineState
import com.miwealth.sovereignvantage.core.trading.engine.*
import com.miwealth.sovereignvantage.core.ai.BoardDecisionRepositoryImpl
import com.miwealth.sovereignvantage.core.utils.SystemLogger
import com.miwealth.sovereignvantage.data.local.TradeDatabase
import com.miwealth.sovereignvantage.data.repository.SettingsPreferencesManager  // BUILD #273
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

// V5.17.0: ExchangeCredentials unified - single definition in core.exchange package

/**
 * SOVEREIGN VANTAGE V5.17.0 "ARTHUR EDITION"
 * TRADING SYSTEM MANAGER
 * 
 * Hilt-injectable wrapper that manages trading system lifecycle.
 * Provides a clean interface for ViewModels to interact with the trading engine.
 * 
 * V5.17.0 CHANGES:
 * - Version bump for UI testnet banner, FundingArbEngine cross-exchange wiring,
 *   and WebSocket order book feed integration
 * 
 * V5.17.0 CHANGES:
 * - Gap 1 COMPLETE: initializeWithPQC() now registers ALL exchange credentials
 *   (not just primary) via AI Exchange Interface for arb/hedge strategies
 * - Gap 4 NEW: initializeTestnetTrading() and initializeMultiExchangeTestnet()
 *   enable sandbox trading via testnet endpoints (Binance, Bybit, etc.)
 * - isTestnetMode propagated to DashboardState for UI safety indicators
 * - Gate.io: production-only with allowProductionFallback flag
 * 
 * V5.17.0 CHANGES (GAP FIXES):
 * - Gap 1 (P0): initializeWithCredentials() now routes through AI Exchange Interface
 *   when USE_AI_INTEGRATION is true. Bronze+ paid users hit the AI path correctly.
 * - Gap 2 (P2): initializeWithPQC() scaffolded with same AI routing pattern.
 * - Both legacy paths preserved as fallback (BRIDGE mode).
 * 
 * V5.17.0 CHANGES:
 * - Implemented Vintage Theme (luxury aesthetic: leather, gold, burr walnut)
 * - Created VintageColors.kt (292 lines) - Full color palette
 * - Created VintageTheme.kt (415 lines) - Theme provider with mode switching
 * - Created VintageComponents.kt (585 lines) - Styled UI components
 * - Created ZenModeOverlay.kt (548 lines) - Full-screen animated clock
 * - Theme supports Vintage Mode (luxury) and Basic Mode (clean/fast)
 * 
 * V5.17.0 CHANGES:
 * - Added Alpha Factor Scanner (multi-factor quantitative ranking)
 * - Added Delta-Neutral Funding Arbitrage Engine (funding rate printer)
 * - Added Strategy Risk Manager with 5% hard kill switch
 * - All strategies implemented as state machines (no human emotion)
 * - WebSocket-first for fastest execution
 * 
 * V5.17.0 CHANGES:
 * - Pipeline status now exposed via observePipelineState() for Settings UI
 * 
 * V5.17.0 CHANGES:
 * - Added support for TradingSystemIntegration (AI Exchange Interface)
 * - Configurable backend: USE_AI_INTEGRATION flag switches between systems
 * - Paper trading now uses AI-integrated paper adapter
 * - Full backward compatibility with legacy TradingSystem
 * 
 * This is the bridge between the UI layer and the core trading infrastructure.
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * 
 * Dedicated to Cathryn for tolerating me and my quirks. 💘
 */

@Singleton
class TradingSystemManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsManager: SettingsPreferencesManager,  // BUILD #273: For trading aggressiveness
    private val tradeRecorder: com.miwealth.sovereignvantage.core.portfolio.TradeRecorder,  // BUILD #274: Portfolio analytics
    private val equitySnapshotRecorder: com.miwealth.sovereignvantage.core.portfolio.EquitySnapshotRecorder  // BUILD #274: Equity snapshots
) {
    companion object {
        private const val TAG = "TradingSystemManager"
        
        /**
         * Feature flag: Use AI-integrated trading system.
         * 
         * true  = Use TradingSystemIntegration (new AI Exchange adapters)
         * false = Use legacy TradingSystem (hardcoded exchange adapters)
         * 
         * Set to true for paper trading tests and AI exchange interface work.
         * Set to false for legacy exchange connections (Kraken, Coinbase direct).
         */
        const val USE_AI_INTEGRATION = true
    }
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Legacy system (backward compatibility)
    private val legacyTradingSystem: TradingSystem by lazy {
        TradingSystem.getInstance(context)
    }
    
    // New AI-integrated system
    private var aiIntegratedSystem: TradingSystemIntegration? = null
    
    // Track which system is active
    private var usingAIIntegration = USE_AI_INTEGRATION
    
    // ========================================================================
    // INITIALIZATION STATE
    // ========================================================================
    
    private val _initializationState = MutableStateFlow<InitializationState>(InitializationState.NotInitialized)
    val initializationState: StateFlow<InitializationState> = _initializationState.asStateFlow()
    
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()
    
    // BUILD #136: Job storage to prevent duplicate price feed collectors
    // When init runs multiple times, we cancel old jobs before starting new ones
    private var priceFeedToCoordinatorJob: Job? = null
    private var ohlcvToCoordinatorJob: Job? = null  // BUILD #240: Real OHLCV candle feed
    private var priceFeedToDashboardJob: Job? = null
    
    // ========================================================================
    // BUILD #254: BUILD #242-244 VARIABLES COMMENTED OUT
    // ========================================================================
    // These variables reference classes from Build #242-244 that were reverted.
    // Commented out until those features are properly re-integrated.
    // ========================================================================
    /*
    // BUILD #242: Real-Time DQN Learning System
    // DQN learns from each tick as it arrives (no replay, no compression)
    // RollingTickWindow maintains 5-min context buffer for board analysis
    private var realTimeDQNLearner: RealtimeDQNLearner? = null
    private var tickWindow: RollingTickWindow? = null
    private var tickProvider: TickProvider? = null
    private var tickCollectorJob: Job? = null
    private var lastAnalysisTime: Long = 0L
    private val analysisIntervalMs = 15000L  // Board analyzes every 15 seconds
    
    // BUILD #244: Multi-Exchange System
    // Manages connections to multiple exchanges simultaneously for arbitrage
    private var multiExchangeManager: MultiExchangeManager? = null
    private var multiExchangeCollectorJob: Job? = null
    private var useMultiExchange: Boolean = false  // Toggle between single/multi mode
    */
    
    // ========================================================================
    // DASHBOARD STATE (Aggregated for UI)
    // ========================================================================
    
    // BUILD #107: Initialize with proper starting balance immediately
    private val _dashboardState = MutableStateFlow(DashboardState(
        portfolioValue = 100000.0,
        initialPortfolioValue = 100000.0
    ))
    val dashboardState: StateFlow<DashboardState> = _dashboardState.asStateFlow()
    
    /**
     * BUILD #117 FIX 4: Public coordinator events for ViewModels
     * 
     * Exposes TradingCoordinator events so UI components can:
     * - Display real-time AI Board decisions (AIBoardViewModel)
     * - Show trade history (DashboardViewModel)
     * - Update position status (PortfolioViewModel)
     * 
     * Events include:
     * - TradeExecuted (for trade history)
     * - BoardDecisionMade (for AI Board UI)
     * - PositionUpdated (for portfolio)
     * - SignalGenerated (for pending signals)
     */
    private val _coordinatorEvents = MutableSharedFlow<CoordinatorEvent>(
        replay = 10,  // Keep last 10 events for late subscribers
        extraBufferCapacity = 100
    )
    val coordinatorEvents: SharedFlow<CoordinatorEvent> = _coordinatorEvents.asSharedFlow()
    
    init {
        SystemLogger.init("TradingSystemManager created")
        SystemLogger.init("Initial portfolio value: A$100,000.00")
        SystemLogger.init("Using AI Integration: $USE_AI_INTEGRATION")
    }
    
    // ========================================================================
    // INITIALIZATION - AI INTEGRATED SYSTEM (NEW)
    // ========================================================================
    
    /**
     * Initialize with AI-integrated paper trading.
     * Uses TradingSystemIntegration for dynamic exchange adapters.
     * 
     * This is the PREFERRED method for paper trading tests.
     */
    suspend fun initializeAIPaperTrading(
        startingBalance: Double = 100_000.0,
        tradingSymbols: List<String> = listOf("BTC/USDT", "ETH/USDT", "SOL/USDT", "XRP/USDT"), // BUILD #236: 4 symbols
        tradingMode: TradingMode = TradingMode.AUTONOMOUS // BUILD #236: AUTONOMOUS so board actually trades
    ): Result<Unit> {
        // BUILD #239: Guard against double initialization — if already ready, return success immediately.
        // Double init creates two coordinator instances; data goes to one, analysis runs on the other.
        if (_isReady.value && aiIntegratedSystem != null) {
            SystemLogger.system("⏭️ BUILD #239: Already initialized — skipping re-init to prevent dual coordinator")
            return Result.success(Unit)
        }
        _initializationState.value = InitializationState.Initializing("Starting AI paper trading...")
        usingAIIntegration = true
        
        return try {
            // BUILD #263: Wire XAI board decision repository for full regulatory audit trail
            val db = TradeDatabase.getInstance(context)
            val boardDecisionRepo = BoardDecisionRepositoryImpl(db.boardDecisionDao())
            aiIntegratedSystem = TradingSystemIntegration.getInstance(
                context, 
                boardDecisionRepo,
                null,  // tradeDao
                tradeRecorder,  // BUILD #274
                equitySnapshotRecorder  // BUILD #274
            )
            
            val config = TradingSystemConfig(
                executionMode = TradingExecutionMode.PAPER,
                tradingMode = tradingMode,
                paperTradingBalance = startingBalance,
                tradingSymbols = tradingSymbols,
                useStahlStops = true,
                enableAssetDiscovery = true,  // Run discovery pipeline
                minConfidenceToTrade = settingsManager.getMinConfidenceThreshold(),  // BUILD #273
                minBoardAgreement = settingsManager.getMinBoardAgreement()  // BUILD #273
            )
            
            val result = aiIntegratedSystem!!.initialize(config)
            
            if (result.isSuccess) {
                _initializationState.value = InitializationState.Ready
                _isReady.value = true
                startAIStateCollection()
                updateDashboardFromAISystem()
                
                // BUILD #274: Start equity snapshot recorder for portfolio analytics
                equitySnapshotRecorder.start(this)
                Log.i(TAG, "📊 BUILD #274: EquitySnapshotRecorder started - recording every 15 minutes")
                
                // V5.19.0 BUILD #99 FIX: AUTO-START TRADING COORDINATOR
                // Paper trading is in SIGNAL_ONLY mode (safe - won't auto-trade).
                // Without starting coordinator, no signals are generated and system
                // appears dead. Starting coordinator enables signal generation while
                // keeping user in control (must confirm each trade in SIGNAL_ONLY mode).
                aiIntegratedSystem?.start()  // Starts coordinator + price feed
                
                Log.i(TAG, "✅ Trading coordinator started in SIGNAL_ONLY mode for paper trading")
                
                // V5.18.20 FIX: START BINANCE PUBLIC FEED
                // BinancePublicPriceFeed is a separate singleton that provides
                // free public price data. It must be started explicitly.
                val feed = BinancePublicPriceFeed.getInstance()
                feed.start(tradingSymbols)
                
                Log.i(TAG, "AI paper trading initialized with balance: $startingBalance")
                Log.i(TAG, "BinancePublicPriceFeed started for: $tradingSymbols")
                SystemLogger.system("✅ BUILD #236: Paper trading initialized — ${tradingSymbols.size} symbols, AUTONOMOUS mode")
                
                // BUILD #274: Start equity snapshot recorder for portfolio analytics
                equitySnapshotRecorder.start(this@TradingSystemManager)
                Log.i(TAG, "📊 BUILD #274: EquitySnapshotRecorder started - will record equity every 15 minutes")
                SystemLogger.system("📊 BUILD #274: Equity snapshots enabled for Sharpe/Sortino/Drawdown tracking")
                
                // BUILD #251: Reverted multi-exchange wiring (Build #242-250 had architectural issues)
                // TODO Build #252: Implement proper multi-exchange integration with consolidated API
                SystemLogger.system("ℹ️ BUILD #251: Multi-exchange disabled pending architecture consolidation")
                SystemLogger.system("   Single exchange (Binance) stable. Device testing enabled.")
            } else {
                _initializationState.value = InitializationState.Error(
                    result.exceptionOrNull()?.message ?: "Unknown error"
                )
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "AI paper trading initialization failed", e)
            _initializationState.value = InitializationState.Error(e.message ?: "Initialization failed")
            Result.failure(e)
        }
    }
    
    /**
     * V5.17.0: Initialize paper trading with LIVE price data.
     * Uses Binance public WebSocket for real prices — no API key needed.
     * Trades are still simulated via PaperTradingAdapter.
     */
    suspend fun initializeAIPaperTradingWithLiveData(
        startingBalance: Double = 100_000.0,
        tradingSymbols: List<String> = listOf("BTC/USDT", "ETH/USDT"),
        tradingMode: TradingMode = TradingMode.SIGNAL_ONLY,
        primaryExchangeId: String = "binance"
    ): Result<Unit> {
        SystemLogger.error("🚨 BUILD #145: initializeAIPaperTradingWithLiveData() CALLED!", null)
        Log.e(TAG, "🚨 BUILD #145: initializeAIPaperTradingWithLiveData() CALLED!")
        SystemLogger.init("═══════════════════════════════════════════════════════════")
        SystemLogger.init("📊 BUILD #107: Starting AI paper trading initialization")
        SystemLogger.init("   Balance: A$${startingBalance}")
        SystemLogger.init("   Symbols: $tradingSymbols")
        SystemLogger.init("   Mode: $tradingMode")
        SystemLogger.init("   Exchange: $primaryExchangeId")
        SystemLogger.init("═══════════════════════════════════════════════════════════")
        
        Log.i(TAG, "═══════════════════════════════════════════════════════════")
        Log.i(TAG, "📊 BUILD #107 DIAGNOSTIC: Starting AI paper trading initialization")
        Log.i(TAG, "   Balance: A$${startingBalance}")
        Log.i(TAG, "   Symbols: $tradingSymbols")
        Log.i(TAG, "   Mode: $tradingMode")
        Log.i(TAG, "   Exchange: $primaryExchangeId")
        Log.i(TAG, "═══════════════════════════════════════════════════════════")
        
        _initializationState.value = InitializationState.Initializing("Starting paper trading with live prices from $primaryExchangeId...")
        usingAIIntegration = true
        
        // BUILD #107: Ensure portfolio value is set immediately
        _dashboardState.update { it.copy(
            portfolioValue = startingBalance,
            initialPortfolioValue = startingBalance,
            paperTradingMode = true
        ) }
        SystemLogger.init("Portfolio value initialized to A$${startingBalance}")
        
        return try {
            // BUILD #137 FIX: Cancel and restart collectors IMMEDIATELY
            // BUILD #162: Use SupervisorScope to prevent collector cancellation issues
            // DON'T wait until after initialize() - that creates an 11-second gap!
            SystemLogger.init("🔧 Step 0: Restarting dashboard collector ONLY (coordinator doesn't exist yet)")
            Log.d(TAG, "🔧 BUILD #139: Step 0 - Dashboard collector only")
            
            // Cancel old dashboard collector if exists
            priceFeedToDashboardJob?.cancel()
            
            // Restart dashboard collector IMMEDIATELY (before slow initialize())
            val feed = BinancePublicPriceFeed.getInstance()
            priceFeedToDashboardJob = scope.launch {
                try {
                    SystemLogger.i(TAG, "🚀 BUILD #162: Dashboard collector started at Step 0")
                    feed.priceTicks.collect { tick ->
                        SystemLogger.d(TAG, "💰 BUILD #162: Dashboard received tick: ${tick.symbol} = ${tick.last}")
                        
                        // BUILD #139 FIX: MERGE into existing map, don't overwrite!
                        _dashboardState.update { current ->
                            val updatedPrices = current.latestPrices.toMutableMap()
                            val updatedChanges = current.priceChanges24h.toMutableMap()
                            
                            // Add the tick symbol
                            updatedPrices[tick.symbol] = tick.last
                            updatedChanges[tick.symbol] = tick.change24hPercent
                            
                            // BUILD #152: Removed phantom /USD creation - use only real Binance /USDT symbols
                            
                            SystemLogger.d(TAG, "📊 BUILD #162: Dashboard now has ${updatedPrices.size} symbols: ${updatedPrices.keys.joinToString()}")
                            
                            current.copy(
                                latestPrices = updatedPrices,
                                priceChanges24h = updatedChanges
                            )
                        }
                    }
                } catch (e: Exception) {
                    SystemLogger.error("🚨 BUILD #162: Dashboard collector exception: ${e.message}", e)
                }
            }
            
            // DON'T start coordinator collector here - aiIntegratedSystem doesn't exist yet!
            // Coordinator wiring happens in Step 6.5 AFTER aiIntegratedSystem is created
            
            SystemLogger.init("🔧 Step 1: Creating TradingSystemIntegration instance")
            Log.d(TAG, "🔧 Step 1: Creating TradingSystemIntegration instance")
            // BUILD #263: Wire XAI board decision repository for full regulatory audit trail
            val db = TradeDatabase.getInstance(context)
            val boardDecisionRepo = BoardDecisionRepositoryImpl(db.boardDecisionDao())
            aiIntegratedSystem = TradingSystemIntegration.getInstance(
                context, 
                boardDecisionRepo,
                null,  // tradeDao
                tradeRecorder,  // BUILD #274
                equitySnapshotRecorder  // BUILD #274
            )
            
            val config = TradingSystemConfig(
                executionMode = TradingExecutionMode.PAPER_WITH_LIVE_DATA,
                tradingMode = tradingMode,
                primaryExchangeId = primaryExchangeId,
                paperTradingBalance = startingBalance,
                useLivePricesInPaperMode = true,
                tradingSymbols = tradingSymbols,
                useStahlStops = true,
                enableAssetDiscovery = true
            )
            
            SystemLogger.init("🔧 Step 2: Calling TradingSystemIntegration.initialize()")
            Log.d(TAG, "🔧 Step 2: Calling TradingSystemIntegration.initialize()")
            val result = aiIntegratedSystem!!.initialize(config)
            
            if (result.isSuccess) {
                SystemLogger.init("✅ TradingSystemIntegration initialized successfully")
                Log.i(TAG, "✅ TradingSystemIntegration initialized successfully")
                
                _initializationState.value = InitializationState.Ready
                _isReady.value = true
                
                SystemLogger.init("🔧 Step 3: Starting AI state collection")
                Log.d(TAG, "🔧 Step 3: Starting AI state collection")
                startAIStateCollection()
                
                SystemLogger.init("🔧 Step 4: Updating dashboard from AI system")
                Log.d(TAG, "🔧 Step 4: Updating dashboard from AI system")
                updateDashboardFromAISystem()
                
                SystemLogger.init("🔧 Step 5: Starting trading coordinator")
                Log.d(TAG, "🔧 Step 5: Starting trading coordinator")
                // V5.19.0 BUILD #99: Start coordinator for paper trading
                aiIntegratedSystem?.start()
                
                SystemLogger.init("✅ Trading coordinator started for paper trading with live prices")
                Log.i(TAG, "✅ Trading coordinator started for paper trading with live prices")
                
                // V5.18.20 FIX: START BINANCE PUBLIC FEED
                SystemLogger.init("🔧 Step 6: Starting BinancePublicPriceFeed")
                Log.d(TAG, "🔧 Step 6: Starting BinancePublicPriceFeed")
                val feed = BinancePublicPriceFeed.getInstance()
                feed.start(tradingSymbols)
                
                // BUILD #111 FIX #1: Wire price feed to trading coordinator
                // BinancePublicPriceFeed was running in isolation - nothing consumed its prices!
                // Now we forward every price tick to the coordinator's price buffers.
                SystemLogger.init("🔧 Step 6.5: Wiring BinancePublicPriceFeed to TradingCoordinator")
                SystemLogger.error("🚨 BUILD #162: STEP 6.5 EXECUTING! Feed instance: $feed", null)
                SystemLogger.error("🚨 BUILD #162: Scope is active: ${scope.isActive}", null)
                SystemLogger.error("🚨 BUILD #162: About to launch coordinator collector", null)
                Log.e(TAG, "🚨 BUILD #162: STEP 6.5 EXECUTING!")
                Log.e(TAG, "🚨 BUILD #162: Scope is active: ${scope.isActive}")
                Log.e(TAG, "🚨 BUILD #162: About to launch coordinator collector")
                Log.d(TAG, "🔧 BUILD #162: Step 6.5 - Starting coordinator collector")
                
                // BUILD #139: Always start coordinator collector (Step 0 doesn't start it anymore)
                try {
                    priceFeedToCoordinatorJob?.cancel()
                    SystemLogger.error("🚨 BUILD #162: About to launch coordinator collector", null)
                    priceFeedToCoordinatorJob = scope.launch {
                        try {
                            SystemLogger.error("🚨 BUILD #162: Coordinator collector coroutine STARTED!", null)
                            SystemLogger.i(TAG, "🚀 BUILD #162: Coordinator collector started")
                            feed.priceTicks.collect { tick ->
                                SystemLogger.error("🚨 BUILD #162: Coordinator tick: ${tick.symbol} = ${tick.last}", null)
                                SystemLogger.i(TAG, "💰 BUILD #162: Coordinator received tick: ${tick.symbol} = ${tick.last}")
                                val coordinator = aiIntegratedSystem?.getTradingCoordinator()
                                if (coordinator != null) {
                                    coordinator.onPriceTick(
                                        symbol = tick.symbol,
                                        price = tick.last,
                                        volume = tick.volume24h,
                                        exchange = "binance"
                                    )
                                    
                                    // BUILD #288: Recalculate portfolio value INCLUDING realized P&L
                                    // OLD BUG: Only counted unrealized P&L, balance stayed at $100k even with closed profits!
                                    // NEW: Cash + Realized P&L (locked-in profits) + Unrealized P&L (open positions)
                                    val positions = coordinator.getManagedPositions()
                                    val status = coordinator.getStatus()
                                    
                                    // Starting cash balance
                                    val cashBalance = 100000.0
                                    
                                    // Realized P&L from all closed trades (cumulative profits/losses)
                                    val totalRealizedPnL = status.totalRealizedPnL
                                    
                                    // Unrealized P&L from currently open positions
                                    val totalUnrealizedPnL = status.totalUnrealizedPnL
                                    
                                    // TRUE portfolio value = cash + all realized profits + all unrealized profits
                                    val newPortfolioValue = cashBalance + totalRealizedPnL + totalUnrealizedPnL
                                    
                                    _dashboardState.update { current ->
                                        current.copy(
                                            portfolioValue = newPortfolioValue,
                                            unrealizedPnl = totalUnrealizedPnL,
                                            activePositionCount = positions.size
                                        )
                                    }
                                } else {
                                    SystemLogger.e(TAG, "❌ BUILD #162: Coordinator is NULL! aiIntegratedSystem not initialized?")
                                }
                            }
                        } catch (e: Exception) {
                            SystemLogger.error("🚨 BUILD #162: Coordinator collector exception: ${e.message}", e)
                        }
                    }
                    SystemLogger.error("🚨 BUILD #162: Coordinator collector job created successfully!", null)
                } catch (e: Exception) {
                    SystemLogger.error("🚨 BUILD #162: EXCEPTION in Step 6.5! ${e.message}", e)
                    Log.e(TAG, "🚨 BUILD #162: EXCEPTION in Step 6.5!", e)
                }
                
                SystemLogger.init("✅ BinancePublicPriceFeed wired to coordinator")
                Log.i(TAG, "✅ BUILD #139: BinancePublicPriceFeed wired to coordinator")
                
                // BUILD #283: Start balance polling to update portfolio value in UI
                // Without this, balance stays at static $100,000 even with active trading
                SystemLogger.init("🔧 Step 7: Starting balance polling")
                Log.i(TAG, "🔧 BUILD #283: Starting balance polling for portfolio updates")
                aiIntegratedSystem?.startPriceFeedOnly()
                
                SystemLogger.init("✅ BinancePublicPriceFeed started for: $tradingSymbols")
                SystemLogger.init("═══════════════════════════════════════════════════════════")
                SystemLogger.init("🎉 AI paper trading initialization COMPLETE")
                SystemLogger.init("   AI Integration: ${if (USE_AI_INTEGRATION) "ENABLED" else "DISABLED"}")
                SystemLogger.init("   Data Source: Binance WebSocket (LIVE)")
                SystemLogger.init("   Execution: Paper Trading (Simulated)")
                SystemLogger.init("   Portfolio Value: A$${_dashboardState.value.portfolioValue}")
                SystemLogger.init("═══════════════════════════════════════════════════════════")
                
                Log.i(TAG, "✅ BinancePublicPriceFeed started for: $tradingSymbols")
                Log.i(TAG, "═══════════════════════════════════════════════════════════")
                Log.i(TAG, "🎉 AI paper trading initialization COMPLETE")
                Log.i(TAG, "   AI Integration: ${if (USE_AI_INTEGRATION) "ENABLED" else "DISABLED"}")
                Log.i(TAG, "   Data Source: Binance WebSocket (LIVE)")
                Log.i(TAG, "   Execution: Paper Trading (Simulated)")
                Log.i(TAG, "═══════════════════════════════════════════════════════════")
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                SystemLogger.error("❌ TradingSystemIntegration initialization FAILED: $error", result.exceptionOrNull())
                Log.e(TAG, "❌ TradingSystemIntegration initialization FAILED: $error")
                _initializationState.value = InitializationState.Error(error)
            }
            result
        } catch (e: Exception) {
            SystemLogger.error("Paper trading with live data initialization failed", e)
            Log.e(TAG, "Paper trading with live data initialization failed", e)
            _initializationState.value = InitializationState.Error(e.message ?: "Initialization failed")
            Result.failure(e)
        }
    }
    
    /**
     * Initialize with AI-integrated live trading via AI Exchange Interface.
     * Uses dynamic exchange adapters learned from exchange APIs.
     */
    suspend fun initializeAILiveTrading(
        primaryExchangeId: String = "binance",
        credentials: ExchangeCredentials? = null,
        tradingSymbols: List<String> = listOf("BTC/USDT", "ETH/USDT"),
        tradingMode: TradingMode = TradingMode.SIGNAL_ONLY
    ): Result<Unit> {
        _initializationState.value = InitializationState.Initializing("Connecting to $primaryExchangeId via AI interface...")
        usingAIIntegration = true
        
        return try {
            // BUILD #263: Wire XAI board decision repository for full regulatory audit trail
            val db = TradeDatabase.getInstance(context)
            val boardDecisionRepo = BoardDecisionRepositoryImpl(db.boardDecisionDao())
            aiIntegratedSystem = TradingSystemIntegration.getInstance(
                context, 
                boardDecisionRepo,
                null,  // tradeDao
                tradeRecorder,  // BUILD #274
                equitySnapshotRecorder  // BUILD #274
            )
            
            val config = TradingSystemConfig(
                executionMode = TradingExecutionMode.LIVE_AI,
                tradingMode = tradingMode,
                primaryExchangeId = primaryExchangeId,
                tradingSymbols = tradingSymbols,
                useStahlStops = true,
                enableAssetDiscovery = true,
                primaryCredentials = credentials  // V5.17.0: Pass creds so connector is created WITH auth
            )
            
            val result = aiIntegratedSystem!!.initialize(config)
            
            // V5.17.0: Credentials now flow through config → setupLiveExchange() → addKnownExchange()
            // No separate addExchange() needed — fixes stale-adapter credential gap
            
            if (result.isSuccess) {
                _initializationState.value = InitializationState.Ready
                _isReady.value = true
                startAIStateCollection()
                updateDashboardFromAISystem()
                
                // V5.17.0: Auto-start price feed for live mode too
                aiIntegratedSystem?.startPriceFeedOnly()
                
                Log.i(TAG, "AI live trading initialized for: $primaryExchangeId")
            } else {
                _initializationState.value = InitializationState.Error(
                    result.exceptionOrNull()?.message ?: "Unknown error"
                )
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "AI live trading initialization failed", e)
            _initializationState.value = InitializationState.Error(e.message ?: "Initialization failed")
            Result.failure(e)
        }
    }
    
    // ========================================================================
    // TESTNET TRADING (V5.17.0)
    // ========================================================================
    
    /**
     * Initialize testnet/sandbox trading.
     * 
     * Uses LIVE_AI execution mode against testnet endpoints — real API calls
     * to sandbox servers. This validates the full order flow (AI Board → signal
     * → OrderExecutor → exchange API) without risking production funds.
     * 
     * Exchange ID resolution:
     * - Exchanges with testnet: resolved via ExchangeTestnetConfig
     *   (e.g. "binance" → "binance_testnet")
     * - Exchanges without testnet (e.g. Gate.io): connects to PRODUCTION
     *   with read-only validation. A warning is logged. User must accept
     *   production risk explicitly via [allowProductionFallback].
     * 
     * @param exchangeId Production exchange ID (e.g. "binance", "bybit", "gateio")
     * @param credentials Exchange API credentials (testnet keys for testnet exchanges)
     * @param tradingSymbols Symbols to trade on testnet
     * @param tradingMode Trading mode (defaults to SIGNAL_ONLY for safety)
     * @param allowProductionFallback If true, allows production connection for exchanges
     *        without testnet. If false (default), returns failure for such exchanges.
     * @return Result indicating success or failure
     */
    suspend fun initializeTestnetTrading(
        exchangeId: String,
        credentials: ExchangeCredentials,
        tradingSymbols: List<String> = listOf("BTC/USDT", "ETH/USDT"),
        tradingMode: TradingMode = TradingMode.SIGNAL_ONLY,
        allowProductionFallback: Boolean = false
    ): Result<Unit> {
        val hasTestnet = ExchangeTestnetConfig.hasTestnet(exchangeId)
        
        if (!hasTestnet && !allowProductionFallback) {
            val msg = "$exchangeId does not have a testnet environment. " +
                "Set allowProductionFallback=true to connect to production, or use " +
                "testAIConnection() for read-only validation."
            Log.w(TAG, "Testnet init blocked: $msg")
            return Result.failure(IllegalArgumentException(msg))
        }
        
        val connectionId = ExchangeTestnetConfig.resolveConnectionId(exchangeId, isTestnet = hasTestnet)
        val envLabel = if (hasTestnet) "TESTNET" else "PRODUCTION (no testnet available)"
        
        Log.i(TAG, "Initializing testnet trading: $connectionId [$envLabel]")
        
        if (!hasTestnet) {
            Log.w(TAG, "⚠️ $exchangeId has no testnet — connecting to PRODUCTION. " +
                "Ensure credentials have appropriate permissions.")
        }
        
        _initializationState.value = InitializationState.Initializing(
            "Connecting to $connectionId [$envLabel]..."
        )
        usingAIIntegration = true
        
        return try {
            // BUILD #263: Wire XAI board decision repository for full regulatory audit trail
            val db = TradeDatabase.getInstance(context)
            val boardDecisionRepo = BoardDecisionRepositoryImpl(db.boardDecisionDao())
            aiIntegratedSystem = TradingSystemIntegration.getInstance(
                context, 
                boardDecisionRepo,
                null,  // tradeDao
                tradeRecorder,  // BUILD #274
                equitySnapshotRecorder  // BUILD #274
            )
            
            val config = TradingSystemConfig(
                executionMode = TradingExecutionMode.LIVE_AI,
                tradingMode = tradingMode,
                primaryExchangeId = connectionId,
                isTestnetMode = true,  // V5.17.0: Flag for UI and safety checks
                tradingSymbols = tradingSymbols,
                useStahlStops = true,
                enableAssetDiscovery = true,
                enableSmartRouting = false,  // Single exchange for testnet — no SOR needed
                primaryCredentials = credentials  // V5.17.0: Pass creds so connector is created WITH auth
            )
            
            val result = aiIntegratedSystem!!.initialize(config)
            
            if (result.isSuccess) {
                // V5.17.0: Credentials now flow through config → setupLiveExchange() → addKnownExchange()
                // No separate addExchange() needed — fixes stale-adapter credential gap
                
                _initializationState.value = InitializationState.Ready
                _isReady.value = true
                startAIStateCollection()
                updateDashboardFromAISystem()
                
                // V5.17.0: Auto-start price feed for testnet mode too
                aiIntegratedSystem?.startPriceFeedOnly()
                
                Log.i(TAG, "✅ Testnet trading initialized: $connectionId [$envLabel]")
            } else {
                _initializationState.value = InitializationState.Error(
                    result.exceptionOrNull()?.message ?: "Testnet initialization failed"
                )
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Testnet trading initialization failed", e)
            _initializationState.value = InitializationState.Error(e.message ?: "Testnet init failed")
            Result.failure(e)
        }
    }
    
    /**
     * Initialize testnet trading with multiple exchanges.
     * 
     * V5.17.0: For arb/hedge strategy testing across testnet exchanges.
     * Primary exchange initialised first, then secondaries are added
     * and registered with SmartOrderRouter.
     * 
     * @param credentials Map of exchangeId → credentials (use testnet API keys)
     * @param primaryExchangeId Which exchange to use as primary
     * @param tradingSymbols Symbols to trade
     * @param tradingMode Trading mode
     * @return Result indicating success or failure
     */
    suspend fun initializeMultiExchangeTestnet(
        credentials: Map<String, ExchangeCredentials>,
        primaryExchangeId: String = "binance",
        tradingSymbols: List<String> = listOf("BTC/USDT", "ETH/USDT"),
        tradingMode: TradingMode = TradingMode.SIGNAL_ONLY
    ): Result<Unit> {
        if (credentials.isEmpty()) {
            return Result.failure(IllegalArgumentException("No credentials provided"))
        }
        
        // Resolve all testnet IDs
        val resolvedCredentials = credentials.map { (exchangeId, creds) ->
            val hasTestnet = ExchangeTestnetConfig.hasTestnet(exchangeId)
            val connectionId = ExchangeTestnetConfig.resolveConnectionId(exchangeId, isTestnet = hasTestnet)
            val envLabel = if (hasTestnet) "testnet" else "PRODUCTION"
            Log.i(TAG, "Multi-testnet: $exchangeId → $connectionId [$envLabel]")
            connectionId to creds
        }
        
        val primaryConnectionId = ExchangeTestnetConfig.resolveConnectionId(
            primaryExchangeId, 
            isTestnet = ExchangeTestnetConfig.hasTestnet(primaryExchangeId)
        )
        
        // Initialize with primary
        val primaryCreds = resolvedCredentials.find { it.first == primaryConnectionId }?.second
            ?: resolvedCredentials.first().second
        
        val primaryResult = initializeTestnetTrading(
            exchangeId = primaryExchangeId,
            credentials = primaryCreds,
            tradingSymbols = tradingSymbols,
            tradingMode = tradingMode,
            allowProductionFallback = true  // Multi-exchange allows production fallback
        )
        
        if (primaryResult.isFailure) return primaryResult
        
        // Add secondary exchanges
        val secondaries = resolvedCredentials.filter { it.first != primaryConnectionId }
        for ((connectionId, creds) in secondaries) {
            try {
                val added = aiIntegratedSystem?.addExchange(connectionId, creds) ?: false
                if (added) {
                    Log.i(TAG, "Multi-testnet: Added $connectionId")
                } else {
                    Log.w(TAG, "Multi-testnet: Failed to add $connectionId — continuing")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Multi-testnet: $connectionId error — ${e.message}")
            }
        }
        
        Log.i(TAG, "Multi-exchange testnet initialized: ${resolvedCredentials.size} exchange(s)")
        return primaryResult
    }
    
    // ========================================================================
    // AI CONNECTION TESTING
    // ========================================================================
    
    /**
     * Test an exchange connection via the AI Exchange Interface.
     * 
     * Creates a temporary AIConnectionManager, connects to the exchange,
     * and verifies the connection by loading trading pairs. The temporary
     * manager is torn down after the test regardless of outcome.
     * 
     * For testnet connections, resolves the correct testnet variant ID
     * (e.g. "binance" → "binance_testnet") via ExchangeTestnetConfig.
     * 
     * @param exchangeId Production exchange ID (e.g. "binance")
     * @param credentials Exchange API credentials
     * @param isTestnet Whether to connect to testnet/sandbox
     * @return Result with pair count on success, error message on failure
     */
    suspend fun testAIConnection(
        exchangeId: String,
        credentials: ExchangeCredentials,
        isTestnet: Boolean = false
    ): Result<AIConnectionTestResult> {
        return withContext(Dispatchers.IO) {
            val connectionId = ExchangeTestnetConfig.resolveConnectionId(exchangeId, isTestnet)
            val envLabel = if (isTestnet) "testnet" else "production"
            
            Log.i(TAG, "Testing AI connection to $connectionId ($envLabel)")
            
            // Use a temporary manager so we don't pollute the live system
            val testManager = AIConnectionManager(context)
            
            try {
                // Add and auto-connect — this triggers schema learning + connection test
                val added = testManager.addKnownExchange(
                    exchangeId = connectionId,
                    credentials = credentials,
                    autoConnect = true
                )
                
                if (!added) {
                    return@withContext Result.failure(
                        IllegalStateException(
                            "Failed to connect to $connectionId. " +
                            if (isTestnet && !ExchangeTestnetConfig.hasTestnet(exchangeId))
                                "This exchange does not have a testnet environment."
                            else
                                "Check your credentials and try again."
                        )
                    )
                }
                
                // Verify we actually connected by checking connector status
                val connector = testManager.getConnector(connectionId)
                if (connector == null) {
                    return@withContext Result.failure(
                        IllegalStateException("Connector created but not found — unexpected state")
                    )
                }
                
                // Get pair count as a health indicator
                val pairs = connector.getTradingPairs()
                
                Log.i(TAG, "AI connection test SUCCESS: $connectionId — ${pairs.size} pairs loaded")
                
                Result.success(
                    AIConnectionTestResult(
                        exchangeId = connectionId,
                        isTestnet = isTestnet,
                        pairsLoaded = pairs.size,
                        message = "Connected to ${connectionId.replaceFirstChar { it.uppercase() }} " +
                                  "($envLabel) — ${pairs.size} trading pairs available"
                    )
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "AI connection test FAILED: $connectionId", e)
                Result.failure(e)
            } finally {
                // Always clean up the temporary manager
                try {
                    testManager.disconnectAll()
                } catch (e: Exception) {
                    Log.w(TAG, "Cleanup warning during test teardown", e)
                }
            }
        }
    }
    
    // ========================================================================
    // INITIALIZATION - LEGACY SYSTEM (BACKWARD COMPATIBILITY)
    // ========================================================================
    
    /**
     * Initialize with paper trading only (no exchange credentials required).
     * This is the default for FREE tier users.
     * 
     * ROUTING: If USE_AI_INTEGRATION is true, routes to AI system.
     */
    suspend fun initializePaperTrading(startingBalance: Double = 100_000.0): Result<Unit> {
        // Route to AI system if enabled
        if (USE_AI_INTEGRATION) {
            return initializeAIPaperTrading(startingBalance)
        }
        
        // Legacy path
        _initializationState.value = InitializationState.Initializing("Starting paper trading mode...")
        usingAIIntegration = false
        
        return try {
            val result = legacyTradingSystem.initializePaperTrading(startingBalance)
            if (result.isSuccess) {
                _initializationState.value = InitializationState.Ready
                _isReady.value = true
                startLegacyStateCollection()
                updateDashboardState()
            } else {
                _initializationState.value = InitializationState.Error(
                    result.exceptionOrNull()?.message ?: "Unknown error"
                )
            }
            result
        } catch (e: Exception) {
            _initializationState.value = InitializationState.Error(e.message ?: "Initialization failed")
            Result.failure(e)
        }
    }
    
    /**
     * Initialize with exchange credentials.
     * For BRONZE tier and above.
     * 
     * V5.17.0 CHANGE (Gap 1 fix): When USE_AI_INTEGRATION is true, routes
     * credentials through the AI Exchange Interface path instead of legacy.
     * This ensures paid users get AI-powered smart routing.
     * Legacy path preserved as fallback — both compile and run independently.
     */
    suspend fun initializeWithCredentials(
        krakenApiKey: String? = null,
        krakenApiSecret: String? = null,
        coinbaseApiKey: String? = null,
        coinbaseApiSecret: String? = null,
        preferredExchange: String = "kraken"
    ): Result<Unit> {
        // V5.17.0: Route through AI path if enabled (BRIDGE — legacy preserved below)
        if (USE_AI_INTEGRATION) {
            // Map legacy credential pairs to ExchangeCredentials for AI path
            val credentials = when {
                krakenApiKey != null && krakenApiSecret != null -> ExchangeCredentials(
                    exchangeId = "kraken",
                    apiKey = krakenApiKey,
                    apiSecret = krakenApiSecret
                )
                coinbaseApiKey != null && coinbaseApiSecret != null -> ExchangeCredentials(
                    exchangeId = "coinbase",
                    apiKey = coinbaseApiKey,
                    apiSecret = coinbaseApiSecret
                )
                else -> null
            }
            
            return initializeAILiveTrading(
                primaryExchangeId = preferredExchange,
                credentials = credentials,
                tradingMode = TradingMode.SIGNAL_ONLY
            )
        }
        
        // Legacy path (fallback when USE_AI_INTEGRATION = false)
        _initializationState.value = InitializationState.Initializing("Connecting to exchanges...")
        usingAIIntegration = false  // Use legacy for hardcoded exchanges
        
        return try {
            val result = legacyTradingSystem.initialize(
                krakenApiKey = krakenApiKey,
                krakenApiSecret = krakenApiSecret,
                coinbaseApiKey = coinbaseApiKey,
                coinbaseApiSecret = coinbaseApiSecret,
                preferredExchange = preferredExchange
            )
            if (result.isSuccess) {
                _initializationState.value = InitializationState.Ready
                _isReady.value = true
                startLegacyStateCollection()
                updateDashboardState()
            } else {
                _initializationState.value = InitializationState.Error(
                    result.exceptionOrNull()?.message ?: "Unknown error"
                )
            }
            result
        } catch (e: Exception) {
            _initializationState.value = InitializationState.Error(e.message ?: "Initialization failed")
            Result.failure(e)
        }
    }
    
    /**
     * Initialize with PQC-enabled exchange registry.
     * For production use with full security.
     * 
     * V5.17.0 SCAFFOLD (Gap 2): When USE_AI_INTEGRATION is true, routes through
     * the AI Exchange Interface path. PQC security is maintained at the AI connector
     * level. Legacy PQC path preserved as fallback.
     */
    suspend fun initializeWithPQC(
        credentials: Map<SupportedExchange, ExchangeCredentials> = emptyMap(),
        preferredExchange: SupportedExchange = SupportedExchange.KRAKEN
    ): Result<Unit> {
        // V5.17.0: Route through AI path if enabled (BRIDGE — legacy preserved below)
        if (USE_AI_INTEGRATION) {
            // 1. Initialize with preferred exchange as primary
            val primaryCreds = credentials[preferredExchange]
            
            val primaryResult = initializeAILiveTrading(
                primaryExchangeId = preferredExchange.id,
                credentials = primaryCreds,
                tradingMode = TradingMode.SIGNAL_ONLY
            )
            
            if (primaryResult.isFailure) return primaryResult
            
            // 2. V5.17.0 (Gap 2 complete): Register ALL remaining exchanges
            // Required for multi-exchange arb/hedge strategies (FundingArb, PairsTrading)
            val secondaryExchanges = credentials.filter { it.key != preferredExchange }
            
            for ((exchange, creds) in secondaryExchanges) {
                try {
                    val added = aiIntegratedSystem?.addExchange(
                        exchangeId = exchange.id,
                        credentials = creds
                    ) ?: false
                    
                    if (added) {
                        Log.i(TAG, "PQC multi-exchange: Added ${exchange.id} to AI path")
                    } else {
                        Log.w(TAG, "PQC multi-exchange: Failed to add ${exchange.id} — continuing with remaining")
                    }
                } catch (e: Exception) {
                    // Non-fatal: primary exchange is connected, secondary failures are logged
                    Log.w(TAG, "PQC multi-exchange: ${exchange.id} error — ${e.message}")
                }
            }
            
            if (secondaryExchanges.isNotEmpty()) {
                Log.i(TAG, "PQC multi-exchange: ${secondaryExchanges.size} secondary exchange(s) registered")
            }
            
            return primaryResult
        }
        
        // Legacy path (fallback when USE_AI_INTEGRATION = false)
        _initializationState.value = InitializationState.Initializing("Initializing PQC security...")
        usingAIIntegration = false  // Use legacy for PQC
        
        return try {
            val result = legacyTradingSystem.initializeWithPQC(
                credentials = credentials,
                preferredExchange = preferredExchange
            )
            if (result.isSuccess) {
                _initializationState.value = InitializationState.Ready
                _isReady.value = true
                startLegacyStateCollection()
                updateDashboardState()
            } else {
                _initializationState.value = InitializationState.Error(
                    result.exceptionOrNull()?.message ?: "Unknown error"
                )
            }
            result
        } catch (e: Exception) {
            _initializationState.value = InitializationState.Error(e.message ?: "Initialization failed")
            Result.failure(e)
        }
    }
    
    // ========================================================================
    // STATE COLLECTION - AI INTEGRATED SYSTEM
    // ========================================================================
    
    private fun startAIStateCollection() {
        // Collect AI system state
        scope.launch {
            aiIntegratedSystem?.state?.collect { state ->
                updateDashboardFromAIState(state)
            }
        }
        
        // Collect AI system events
        scope.launch {
            aiIntegratedSystem?.events?.collect { event ->
                handleAISystemEvent(event)
            }
        }
        
        // BUILD #117 FIX 4: Forward coordinator events to public flow for ViewModels
        scope.launch {
            aiIntegratedSystem?.getTradingCoordinator()?.events?.collect { event ->
                // Forward to public flow for UI components
                _coordinatorEvents.emit(event)
                // Also handle internally for dashboard state updates
                handleCoordinatorEvent(event)
            }
        }
        
        // V5.18.0: Observe Binance public feed for dashboard market cards
        startPublicPriceFeedObservation()
    }
    
    private fun updateDashboardFromAIState(state: IntegratedTradingState) {
        // BUILD #117: Get emergency stop cooldown countdown
        val coordinator = aiIntegratedSystem?.getTradingCoordinator()
        val cooldownSeconds = coordinator?.getEmergencyStopCooldownSecondsRemaining() ?: 0
        
        _dashboardState.update { current ->
            // BUILD #108: Don't overwrite portfolio value with 0.0 from uninitialized state
            val newPortfolioValue = if (state.portfolioValue > 0.0) state.portfolioValue else current.portfolioValue
            
            // BUILD #302: Count ALL positions (coordinator + manual), not just coordinator
            val totalPositionCount = aiIntegratedSystem?.getManagedPositions()?.size 
                ?: state.coordinatorState.activePositions.size
            
            current.copy(
                portfolioValue = newPortfolioValue,
                activePositionCount = totalPositionCount,
                isTradingActive = state.coordinatorState.isRunning,
                tradingMode = state.coordinatorState.mode,
                paperTradingMode = state.executionMode == TradingExecutionMode.PAPER ||
                                   state.executionMode == TradingExecutionMode.PAPER_WITH_LIVE_DATA,
                killSwitchActive = state.coordinatorState.emergencyStopActive,
                emergencyStopCooldownSecondsRemaining = cooldownSeconds,  // BUILD #117
                connectedExchangeCount = state.connectedExchanges.size,
                pendingSignalCount = state.coordinatorState.pendingSignals.size,
                tradesExecutedToday = state.coordinatorState.tradesToday,
                realizedPnlToday = state.coordinatorState.pnlToday,
                // Mark that we're using AI integration
                smartRoutingEnabled = true,  // AI adapters are "smart" by nature
                // V5.17.0: Testnet mode propagation to UI
                isTestnetMode = state.isTestnetMode,
                // V5.17.0: ML Health + Disagreement from CoordinatorState
                mlHealthStatus = state.coordinatorState.mlHealthStatus.name,
                mlRollbackCount = state.coordinatorState.mlRollbackCount,
                disagreementLevel = state.coordinatorState.disagreementLevel,
                positionSizeMultiplier = state.coordinatorState.positionSizeMultiplier,
                effectivePositionMultiplier = state.coordinatorState.effectivePositionMultiplier,
                // V5.18.0: Latest prices from feed
                // BUILD #272 FIX: Merge rather than replace — updateDashboardFromAIState() fires
                // mid-poll-cycle when only 1–2 symbols have arrived, clobbering the other symbols
                // that the dashboard's own priceTicks collector had already accumulated.
                // MutableStateFlow.update() is atomic; we read current.latestPrices and merge.
                latestPrices = current.latestPrices.toMutableMap().apply {
                    putAll(state.latestPrices)
                },
                // BUILD #266: Margin-based equity display
                unrealizedPnl = state.coordinatorState.activePositions.values
                    .sumOf { it.unrealizedPnL },
                usedMargin = state.coordinatorState.activePositions.values
                    .sumOf { it.marginUsed },
                totalEquity = run {
                    val cash = newPortfolioValue
                    val unrealised = state.coordinatorState.activePositions.values
                        .sumOf { it.unrealizedPnL }
                    if (cash > 0.0) cash + unrealised else current.totalEquity
                },
                availableMargin = run {
                    val cash = newPortfolioValue
                    val used = state.coordinatorState.activePositions.values
                        .sumOf { it.marginUsed }
                    if (cash > 0.0) (cash - used).coerceAtLeast(0.0) else current.availableMargin
                },
                marginUtilisationPct = run {
                    val equity = newPortfolioValue
                    val used = state.coordinatorState.activePositions.values
                        .sumOf { it.marginUsed }
                    if (equity > 0.0) (used / equity * 100.0).coerceIn(0.0, 100.0) else 0.0
                }
            )
        }
    }
    
    private fun handleAISystemEvent(event: TradingSystemEvent) {
        when (event) {
            is TradingSystemEvent.SignalGenerated -> {
                _dashboardState.update { it.copy(pendingSignalCount = it.pendingSignalCount + 1) }
            }
            is TradingSystemEvent.TradeExecuted -> {
                _dashboardState.update { current ->
                    current.copy(
                        tradesExecutedToday = current.tradesExecutedToday + 1,
                        lastTradeSymbol = event.trade.symbol,
                        lastTradeSide = event.trade.direction.name,
                        lastTradeTime = System.currentTimeMillis()
                    )
                }
            }
            is TradingSystemEvent.TradingStarted -> {
                _dashboardState.update { it.copy(isTradingActive = true) }
            }
            is TradingSystemEvent.TradingStopped -> {
                _dashboardState.update { it.copy(isTradingActive = false) }
            }
            is TradingSystemEvent.EmergencyStop -> {
                _dashboardState.update { it.copy(
                    killSwitchActive = true, 
                    riskWarning = event.reason,
                    emergencyStopCooldownSecondsRemaining = 0  // BUILD #117: Reset on activation
                )}
            }
            is TradingSystemEvent.KillSwitchReset -> {
                // BUILD #117: Set initial cooldown to 60 seconds on reset
                _dashboardState.update { it.copy(
                    killSwitchActive = false, 
                    riskWarning = null,
                    emergencyStopCooldownSecondsRemaining = 60  // BUILD #117: Start 60s cooldown
                )}
            }
            is TradingSystemEvent.Error -> {
                Log.e(TAG, "AI System Error: ${event.message}", event.exception)
            }
            else -> { /* Other events */ }
        }
    }
    
    private fun updateDashboardFromAISystem() {
        aiIntegratedSystem?.let { system ->
            _dashboardState.update { current ->
                // BUILD #108: Don't overwrite portfolio value with 0.0 from uninitialized AI system
                val aiPortfolioValue = system.getPortfolioValue()
                current.copy(
                    portfolioValue = if (aiPortfolioValue > 0.0) aiPortfolioValue else current.portfolioValue,
                    paperTradingMode = system.state.value.executionMode == TradingExecutionMode.PAPER
                )
            }
        }
    }
    
    // ========================================================================
    // STATE COLLECTION - LEGACY SYSTEM
    // ========================================================================
    
    private fun startLegacyStateCollection() {
        // Collect system state changes
        scope.launch {
            legacyTradingSystem.systemState.collect { systemState ->
                updateDashboardFromSystemState(systemState)
            }
        }
        
        // Collect coordinator events
        scope.launch {
            try {
                legacyTradingSystem.getCoordinatorEvents().collect { event ->
                    handleCoordinatorEvent(event)
                }
            } catch (e: Exception) {
                // Coordinator not initialized yet - ignore
            }
        }
        
        // Collect risk events
        scope.launch {
            try {
                legacyTradingSystem.getRiskEvents().collect { event ->
                    handleRiskEvent(event)
                }
            } catch (e: Exception) {
                // Risk manager not initialized yet - ignore
            }
        }
    }
    
    private fun updateDashboardFromSystemState(systemState: SystemState) {
        _dashboardState.update { current ->
            current.copy(
                portfolioValue = systemState.portfolioValue,
                realizedPnlToday = systemState.realizedPnlToday,
                unrealizedPnl = systemState.unrealizedPnl,
                activePositionCount = systemState.activePositionCount,
                tradesExecutedToday = systemState.tradesExecutedToday,
                isTradingActive = systemState.isTradingActive,
                tradingMode = systemState.tradingMode,
                paperTradingMode = systemState.paperTradingMode,
                killSwitchActive = systemState.killSwitchActive,
                activeExchange = systemState.activeExchange,
                pqcSecurityEnabled = systemState.pqcSecurityEnabled,
                smartRoutingEnabled = systemState.smartRoutingEnabled,
                connectedExchangeCount = systemState.connectedExchangeCount,
                // Scalping state
                scalpingActive = systemState.scalpingActive,
                scalpsToday = systemState.scalpsToday,
                scalpingPnlToday = systemState.scalpingPnlToday
            )
        }
    }

    private fun handleCoordinatorEvent(event: CoordinatorEvent) {
        when (event) {
            is CoordinatorEvent.SignalGenerated -> {
                _dashboardState.update { it.copy(pendingSignalCount = it.pendingSignalCount + 1) }
            }
            is CoordinatorEvent.TradeExecuted -> {
                // BUILD #403: Also update portfolio value when trade executes
                val currentPortfolioValue = if (usingAIIntegration) {
                    aiIntegratedSystem?.getPortfolioValue() ?: _dashboardState.value.portfolioValue
                } else {
                    legacyTradingSystem.getPortfolioValue()
                }
                
                _dashboardState.update { current ->
                    current.copy(
                        tradesExecutedToday = current.tradesExecutedToday + 1,
                        lastTradeSymbol = event.trade.symbol,
                        lastTradeSide = event.trade.direction.name,
                        lastTradeTime = System.currentTimeMillis(),
                        portfolioValue = currentPortfolioValue  // BUILD #403: Update from actual positions
                    )
                }
            }
            is CoordinatorEvent.PositionUpdated -> {
                // BUILD #403: Update both position count AND portfolio value
                val positions = if (usingAIIntegration) {
                    aiIntegratedSystem?.getManagedPositions() ?: emptyList()
                } else {
                    legacyTradingSystem.getPositions()
                }
                
                val currentPortfolioValue = if (usingAIIntegration) {
                    aiIntegratedSystem?.getPortfolioValue() ?: _dashboardState.value.portfolioValue
                } else {
                    legacyTradingSystem.getPortfolioValue()
                }
                
                _dashboardState.update { it.copy(
                    activePositionCount = positions.size,
                    portfolioValue = currentPortfolioValue  // BUILD #403: Update from actual positions
                ) }
            }
            is CoordinatorEvent.RiskLimitHit -> {
                _dashboardState.update { it.copy(riskWarning = event.reason) }
            }
            is CoordinatorEvent.TradingStarted -> {
                _dashboardState.update { it.copy(isTradingActive = true) }
            }
            is CoordinatorEvent.TradingStopped -> {
                _dashboardState.update { it.copy(isTradingActive = false) }
            }
            // V5.17.0: Bridge ML health events from TradingCoordinator to DashboardState → ViewModel → UI
            is CoordinatorEvent.MLHealthUpdate -> {
                _dashboardState.update { current ->
                    current.copy(
                        mlHealthStatus = event.status.name,
                        mlHealthSummary = event.summary,
                        // Increment rollback count on CRITICAL (auto-rollback occurred)
                        mlRollbackCount = if (event.status.name == "CRITICAL") 
                            current.mlRollbackCount + 1 else current.mlRollbackCount
                    )
                }
            }
            is CoordinatorEvent.DisagreementUpdate -> {
                _dashboardState.update { it.copy(
                    disagreementLevel = event.level,
                    disagreementScore = event.score,
                    // Map level to position size multiplier
                    positionSizeMultiplier = when (event.level) {
                        "STRONG_AGREEMENT" -> 1.0
                        "MILD_DISAGREEMENT" -> 0.85
                        "MODERATE_DISAGREEMENT" -> 0.60
                        "HIGH_DISAGREEMENT" -> 0.40
                        "EXTREME_DISAGREEMENT" -> 0.25
                        else -> 1.0
                    }
                )}
            }
            else -> { /* Other events */ }
        }
    }
    
    private fun handleRiskEvent(event: RiskEvent) {
        when (event) {
            is RiskEvent.DrawdownWarning -> {
                _dashboardState.update { it.copy(riskWarning = "Drawdown warning: ${event.currentDrawdown}%") }
            }
            is RiskEvent.DailyLossWarning -> {
                _dashboardState.update { it.copy(riskWarning = "Daily loss warning: ${event.currentLoss}%") }
            }
            is RiskEvent.KillSwitchActivated -> {
                _dashboardState.update { it.copy(killSwitchActive = true, riskWarning = event.reason) }
            }
            is RiskEvent.TradingResumed -> {
                _dashboardState.update { it.copy(killSwitchActive = false, riskWarning = null) }
            }
            else -> { /* Other events */ }
        }
    }
    
    private fun updateDashboardState() {
        // Initial update after initialization (legacy)
        _dashboardState.update { current ->
            current.copy(
                portfolioValue = legacyTradingSystem.getPortfolioValue(),
                isTradingActive = legacyTradingSystem.systemState.value.isTradingActive,
                tradingMode = legacyTradingSystem.systemState.value.tradingMode,
                paperTradingMode = legacyTradingSystem.systemState.value.paperTradingMode
            )
        }
    }
    
    // ========================================================================
    // V5.18.0: PUBLIC PRICE FEED (Binance free API - no credentials)
    // ========================================================================
    
    /**
     * Get the BinancePublicPriceFeed singleton for direct price access.
     * Used by WalletViewModel and chart components.
     */
    fun getPublicPriceFeed(): BinancePublicPriceFeed {
        return BinancePublicPriceFeed.getInstance()
    }
    
    /**
     * Get latest prices map (symbol -> price).
     * Aggregates from AI system state or public feed.
     */
    fun getLatestPrices(): Map<String, Double> {
        return _dashboardState.value.latestPrices
    }
    
    /**
     * Start observing public price feed and updating dashboard state.
     * Called automatically when AI integration initializes with paper trading.
     *
     * BUILD #234: Also wires TradingCoordinator collector here as a guaranteed
     * path — previous approach relied on Step 6.5 inside initializeAIPaperTradingWithLiveData()
     * which was never reached when TradingSystemIntegration.initialize() failed/timed out.
     * Now coordinator wiring happens unconditionally after the feed is confirmed live.
     */
    private fun startPublicPriceFeedObservation() {
        // BUILD #137: Skip if dashboard collector already running
        if (priceFeedToDashboardJob?.isActive == true) {
            SystemLogger.d(TAG, "⏭️ BUILD #137: Dashboard collector already active, skipping restart")
            // BUILD #234: Even if dashboard collector is already running, still ensure
            // coordinator is wired (it may not have been started yet)
            startCoordinatorCollectorIfNeeded()
            return
        }
        
        // BUILD #136: Cancel previous job if exists (prevents duplicate collectors)
        priceFeedToDashboardJob?.cancel()
        priceFeedToDashboardJob = scope.launch {
            val feed = BinancePublicPriceFeed.getInstance()
            // BUILD #134 FIX: Collect from priceTicks (SharedFlow) not latestPrices (StateFlow)
            SystemLogger.i(TAG, "🚀 BUILD #142: Starting priceTicks observation for dashboard...")
            feed.priceTicks.collect { tick ->
                SystemLogger.d(TAG, "💰 BUILD #142: Dashboard received tick: ${tick.symbol} = ${tick.last}")
                
                // BUILD #142 FIX: MERGE into existing map, don't overwrite!
                _dashboardState.update { current ->
                    val updatedPrices = current.latestPrices.toMutableMap()
                    val updatedChanges = current.priceChanges24h.toMutableMap()
                    
                    // Add the tick symbol
                    updatedPrices[tick.symbol] = tick.last
                    updatedChanges[tick.symbol] = tick.change24hPercent
                    
                    // BUILD #152: Removed phantom /USD creation - use only real Binance /USDT symbols
                    
                    SystemLogger.d(TAG, "📊 BUILD #152: Dashboard now has ${updatedPrices.size} symbols: ${updatedPrices.keys.joinToString()}")
                    
                    current.copy(
                        latestPrices = updatedPrices,
                        priceChanges24h = updatedChanges
                    )
                }
            }
        }

        // BUILD #234: Wire coordinator collector unconditionally here.
        // This is the guaranteed path — startPublicPriceFeedObservation() is always
        // called from startAIStateCollection() which runs on every successful init path.
        startCoordinatorCollectorIfNeeded()
    }

    /**
     * BUILD #234: Wire BinancePublicPriceFeed → TradingCoordinator.
     *
     * Guards against:
     * - Duplicate collectors (checks isActive before launching)
     * - Null coordinator (logs clearly, retries after short delay)
     *
     * This replaces the Step 6.5 approach which was skipped when
     * TradingSystemIntegration.initialize() did not complete successfully.
     */
    
    // ========================================================================
    // BUILD #253: Multi-Exchange architecture removed in Build #251 revert
    // ========================================================================
    // Multi-exchange wiring has been removed to stabilize the build.
    // This will be re-implemented properly in a future build with proper testing.
    // ========================================================================
    
    // ========================================================================
    // BUILD #234: WIRE COORDINATOR COLLECTOR
    // ========================================================================
    
    /**
     * BUILD #234: Wire BinancePublicPriceFeed → TradingCoordinator.
     *
     * Guards against:
     * - Duplicate collectors (checks isActive before launching)
     * - Null coordinator (logs clearly, retries after short delay)
     *
     * This replaces the Step 6.5 approach which was skipped when
     * TradingSystemIntegration.initialize() did not complete successfully.
     */
    private fun startCoordinatorCollectorIfNeeded() {
        // BUILD #256: Enhanced diagnostics to understand why coordinator isn't receiving prices
        SystemLogger.system("🔍 BUILD #256: startCoordinatorCollectorIfNeeded() called")
        SystemLogger.system("🔍 BUILD #256: aiIntegratedSystem = ${aiIntegratedSystem != null}")
        SystemLogger.system("🔍 BUILD #256: coordinator = ${aiIntegratedSystem?.getTradingCoordinator() != null}")
        
        if (priceFeedToCoordinatorJob?.isActive == true) {
            SystemLogger.d(TAG, "⏭️ BUILD #234: Coordinator collector already active, skipping")
            return
        }

        priceFeedToCoordinatorJob?.cancel()
        priceFeedToCoordinatorJob = scope.launch {
            val feed = BinancePublicPriceFeed.getInstance()
            SystemLogger.system("🚀 BUILD #256: Starting coordinator collector (guaranteed path)")
            SystemLogger.system("🔍 BUILD #256: Before wait - coordinator = ${aiIntegratedSystem?.getTradingCoordinator() != null}")

            // If coordinator is null immediately, wait up to 10s for aiIntegratedSystem to finish init
            var retries = 0
            while (aiIntegratedSystem?.getTradingCoordinator() == null && retries < 20) {
                SystemLogger.system("⏳ BUILD #256: Coordinator not ready yet, waiting... (attempt ${retries + 1}/20)")
                delay(500)
                retries++
            }

            val coordinator = aiIntegratedSystem?.getTradingCoordinator()
            if (coordinator == null) {
                SystemLogger.error("❌ BUILD #256: TradingCoordinator still NULL after 10s wait. " +
                    "aiIntegratedSystem=${aiIntegratedSystem != null}, " +
                    "isInitialized=${aiIntegratedSystem?.state?.value?.isInitialized}, " +
                    "Price feed running for dashboard only.", null)
                return@launch
            }

            SystemLogger.system("✅ BUILD #256: TradingCoordinator obtained — wiring OHLCV candle feed (priceTicks→dashboard only; coordinator uses ohlcvCandles)")

            // BUILD #240: Wire real OHLCV candles to coordinator (runs in parallel).
            // candleData StateFlow emits Map<symbol, List<OHLCVCandle>> every 30s.
            // We take the latest candle per symbol and call onPriceUpdate() with
            // genuine open/high/low/close — giving the DQN real market structure.
            if (ohlcvToCoordinatorJob?.isActive != true) {
                ohlcvToCoordinatorJob = scope.launch {
                    SystemLogger.system("🕯️ BUILD #240: OHLCV candle collector started — real OHLCV data for DQN")
                    feed.ohlcvCandles.collect { (symbol, candle) ->
                        try {
                            coordinator.onPriceUpdate(
                                symbol = symbol,
                                open = candle.open,
                                high = candle.high,
                                low = candle.low,
                                close = candle.close,
                                volume = candle.volume
                            )
                            SystemLogger.d(TAG, "🕯️ BUILD #240: OHLCV candle: $symbol O=${candle.open} H=${candle.high} L=${candle.low} C=${candle.close}")
                        } catch (e: Exception) {
                            SystemLogger.error("BUILD #240: OHLCV candle error: ${e.message}", e)
                        }
                    }
                }
            }

            // BUILD #235: Start the coordinator analysis loop directly.
            // TradingSystemIntegration.start() guards on isInitialized, which is
            // never set when the exchange connect fails (no API key for paper trading).
            // The coordinator is fully constructed and safe to start — it operates
            // in paper trading mode and needs no live exchange connection to run.
            if (!coordinator.state.value.isRunning) {
                SystemLogger.system("🚀 BUILD #235: Starting TradingCoordinator analysis loop directly (bypassing isInitialized gate)")
                coordinator.start()
                SystemLogger.system("✅ BUILD #235: TradingCoordinator.start() called — analysis loop, AI Board, STAHL stops now active")
            } else {
                SystemLogger.system("ℹ️ BUILD #235: TradingCoordinator already running")
            }

            // BUILD #240: DISABLED flat candle feed from priceTicks.
            // Previously this created flat candles (open=high=low=close=last) every 5s,
            // overwriting the real OHLCV data from ohlcvCandles/candleData feeds.
            // Result: DQN saw only flat lines, board confidence stayed 14-27%.
            // 
            // NOW: Only real OHLCV data flows through (from ohlcvCandles + candleData).
            // DQN gets genuine high/low/wicks → learns real market structure →
            // board confidence should rise to 60-80% on strong moves.
            //
            // NOTE: This means coordinator gets data ~every 30s instead of every 5s.
            // That's acceptable for 15s analysis intervals. If real-time ticks are
            // needed later, we can re-enable this BUT feed to a separate buffer
            // that doesn't interfere with OHLCV analysis.
            
            /*
            // COMMENTED OUT: Flat candle feed (BUILD #239 code preserved for reference)
            var restartCount = 0
            while (isActive && restartCount < 10) {
                try {
                    SystemLogger.system("🔄 BUILD #239: Coordinator collector active (restart #$restartCount)")
                    feed.priceTicks.collect { tick ->
                        try {
                            coordinator.onPriceUpdate(
                                symbol = tick.symbol,
                                open = tick.last,
                                high = tick.last,
                                low = tick.last,
                                close = tick.last,
                                volume = tick.volume24h
                            )
                        } catch (e: Exception) {
                            SystemLogger.error("BUILD #239: Candle error: ${e.message}", e)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    restartCount++
                    SystemLogger.error("BUILD #239: Coordinator collector crashed (restart $restartCount/10): ${e.message}", e)
                    delay(2000)
                }
            }
            if (restartCount >= 10) {
                SystemLogger.error("❌ BUILD #239: Coordinator collector exhausted restarts — giving up", null)
            }
            */
            
            SystemLogger.system("✅ BUILD #240: Flat candle feed DISABLED — using only real OHLCV data for analysis")

            // ================================================================
            // BUILD #254: BUILD #242 CODE COMMENTED OUT
            // ================================================================
            // Build #251 reverted to Build #241 baseline, removing RealtimeDQNLearner,
            // RollingTickWindow, and BinancePublicTickProvider.
            // Haiku (in Build #252) re-added this code but it's incompatible with Build #241.
            // This section is commented out until Build #242 work is properly re-integrated.
            // ================================================================
            
            /*
            // BUILD #242: Initialize Real-Time DQN Learning System
            // DQN learns from each tick as it arrives (no replay, no buffering delay)
            // Tick window maintains 5-min context for board analysis
            if (realTimeDQNLearner == null && tickProvider == null) {
                SystemLogger.system("🎬 BUILD #242: Initializing Real-Time DQN Learning System")
                
                // Get DQN from coordinator
                val dqn = coordinator.getDQNTrader()  // ERROR: This method doesn't exist
                
                // Initialize real-time learner
                realTimeDQNLearner = RealtimeDQNLearner(  // ERROR: Class doesn't exist in Build #241
                    dqnAgent = dqn,
                    featureExtractor = EnhancedFeatureExtractor()
                )
                
                // Initialize rolling tick window (300 ticks = ~25 min at 5s/tick)
                tickWindow = RollingTickWindow(windowSize = 300)  // ERROR: Class doesn't exist
                
                // Create tick provider (Binance public for now - easy to swap)
                val provider = BinancePublicTickProvider(  // ERROR: Class doesn't exist
                    symbols = listOf("BTC/USDT", "ETH/USDT", "SOL/USDT", "XRP/USDT")
                )
                
                tickProvider = provider
                
                // Connect provider and start real-time learning
                tickCollectorJob = scope.launch {
                    try {
                        if (provider.connect()) {
                            SystemLogger.system("✅ BUILD #242: Tick provider connected (${provider.exchangeId})")
                            
                            // Collect ticks and feed to real-time learner + context buffer
                            provider.getTickStream().collect { tick ->
                                // Path 1: Real-time DQN learning (every tick)
                                realTimeDQNLearner?.processTickRealtime(
                                    symbol = tick.symbol,
                                    price = tick.price,
                                    bid = tick.bid ?: (tick.price * 0.999),  // Estimate if not available
                                    ask = tick.ask ?: (tick.price * 1.001),
                                    volume = tick.volume,
                                    timestamp = tick.timestamp,
                                    historicalContext = tickWindow?.getContext(tick.symbol) ?: emptyList()
                                )
                                
                                // Path 2: Add to rolling context window (every tick)
                                tickWindow?.addTick(
                                    symbol = tick.symbol,
                                    timestamp = tick.timestamp,
                                    price = tick.price,
                                    bid = tick.bid ?: (tick.price * 0.999),
                                    ask = tick.ask ?: (tick.price * 1.001),
                                    volume = tick.volume
                                )
                                
                                // Path 3: Check if board should analyze
                                val now = System.currentTimeMillis()
                                val timeSinceLastAnalysis = now - lastAnalysisTime
                                val hasEnoughData = tickWindow?.hasEnoughData(tick.symbol, minTicks = 20) ?: false
                                
                                if (timeSinceLastAnalysis >= analysisIntervalMs && hasEnoughData) {
                                    // Trigger board analysis with fresh DQN + context
                                    val dqnState = realTimeDQNLearner?.getCurrentDQNState()
                                    val tickContext = tickWindow?.getContext(tick.symbol)
                                    val tickStats = tickWindow?.getStatistics(tick.symbol)
                                    
                                    // Feed coordinator with enriched data
                                    coordinator.onPriceUpdate(
                                        symbol = tick.symbol,
                                        price = tick.price,  // ERROR: Wrong signature
                                        timestamp = tick.timestamp  // ERROR: Wrong signature
                                    )
                                    
                                    lastAnalysisTime = now
                                    
                                    if (tickStats != null) {
                                        SystemLogger.system(
                                            "📊 BUILD #242 BOARD ANALYSIS: ${tick.symbol} | " +
                                            "DQN updates=${dqnState?.updatesPerformed ?: 0} | " +
                                            "Ticks buffered=${tickStats.bufferSize} | " +
                                            "Momentum=${"%.2f".format(tickStats.momentumPercent)}% | " +
                                            "Volatility=${"%.4f".format(tickStats.volatility)}"
                                        )
                                    }
                                }
                            }
                        } else {
                            SystemLogger.error("❌ BUILD #242: Tick provider failed to connect", null)
                        }
                    } catch (e: Exception) {
                        SystemLogger.error("❌ BUILD #242: Real-time learning error: ${e.message}", e)
                    }
                }
                
                SystemLogger.system("🎬 BUILD #242: Real-Time DQN Learning initialized — learning at market speed")
            } else {
                SystemLogger.d(TAG, "⏭️ BUILD #242: Real-Time DQN already initialized, skipping")
            }
            */

            // BUILD #240: Also feed real OHLCV kline candles to the coordinator.
            // The ticker feed above creates flat candles (open=high=low=close=last).
            // Klines give genuine wicks and spread, enabling meaningful RSI/MACD/
            // Bollinger calculations. This is what the DQN needs for bear market
            // pattern recognition and proper short entry detection.
            //
            // candleData emits every 30s when BinancePublicPriceFeed fetches new klines.
            // We take the most recent closed candle per symbol and feed it to the coordinator.
            scope.launch {
                feed.candleData.collect { candleMap ->
                    for ((symbol, candles) in candleMap) {
                        // Use second-to-last candle (last is still forming)
                        val closedCandle = if (candles.size >= 2) candles[candles.size - 2] else candles.lastOrNull()
                        closedCandle?.let { candle ->
                            try {
                                coordinator.onPriceUpdate(
                                    symbol = symbol,
                                    open = candle.open,
                                    high = candle.high,
                                    low = candle.low,
                                    close = candle.close,
                                    volume = candle.volume
                                )
                                SystemLogger.d(TAG, "📊 BUILD #240: Real kline candle fed: $symbol O=${String.format("%.2f", candle.open)} H=${String.format("%.2f", candle.high)} L=${String.format("%.2f", candle.low)} C=${String.format("%.2f", candle.close)}")
                            } catch (e: Exception) {
                                SystemLogger.error("BUILD #240: Kline feed error: ${e.message}", e)
                            }
                        }
                    }
                }
            }
        }
    }
    
    // ========================================================================
    // TRADING CONTROL (ROUTED)
    // ========================================================================
    
    fun startTrading() {
        SystemLogger.system("═══════════════════════════════════════════════════════════")
        SystemLogger.system("🎯 BUILD #107: startTrading() called")
        SystemLogger.system("   Ready: ${_isReady.value}")
        SystemLogger.system("   Using AI Integration: $usingAIIntegration")
        SystemLogger.system("   Kill Switch Active: ${_dashboardState.value.killSwitchActive}")
        SystemLogger.system("═══════════════════════════════════════════════════════════")
        
        Log.i(TAG, "═══════════════════════════════════════════════════════════")
        Log.i(TAG, "🎯 BUILD #107 DIAGNOSTIC: startTrading() called")
        Log.i(TAG, "   Ready: ${_isReady.value}")
        Log.i(TAG, "   Using AI Integration: $usingAIIntegration")
        Log.i(TAG, "   Kill Switch Active: ${_dashboardState.value.killSwitchActive}")
        Log.i(TAG, "═══════════════════════════════════════════════════════════")
        
        if (!_isReady.value) {
            SystemLogger.system("⚠️ Cannot start trading - system not ready")
            Log.w(TAG, "⚠️ Cannot start trading - system not ready")
            return
        }
        
        if (_dashboardState.value.killSwitchActive) {
            SystemLogger.killswitch("⚠️ Cannot start trading - kill switch is active")
            Log.w(TAG, "⚠️ Cannot start trading - kill switch is active")
            return
        }
        
        if (usingAIIntegration) {
            SystemLogger.system("▶️ Starting AI integrated trading system")
            Log.i(TAG, "▶️ Starting AI integrated trading system")
            aiIntegratedSystem?.start()
            SystemLogger.system("✅ AI system start() called")
            Log.i(TAG, "✅ AI system start() called")
        } else {
            SystemLogger.system("▶️ Starting legacy trading system")
            Log.i(TAG, "▶️ Starting legacy trading system")
            legacyTradingSystem.startTrading()
            SystemLogger.system("✅ Legacy system startTrading() called")
            Log.i(TAG, "✅ Legacy system startTrading() called")
        }
        
        SystemLogger.system("═══════════════════════════════════════════════════════════")
        Log.i(TAG, "═══════════════════════════════════════════════════════════")
    }
    
    fun stopTrading() {
        SystemLogger.system("⏸️ BUILD #107: stopTrading() called")
        Log.i(TAG, "⏸️ BUILD #107 DIAGNOSTIC: stopTrading() called")
        if (!_isReady.value) {
            SystemLogger.system("⚠️ System not ready")
            return
        }
        
        if (usingAIIntegration) {
            SystemLogger.system("⏹️ Stopping AI integrated system")
            aiIntegratedSystem?.stop()
            SystemLogger.system("✅ AI system stopped")
        } else {
            SystemLogger.system("⏹️ Stopping legacy system")
            legacyTradingSystem.stopTrading()
            SystemLogger.system("✅ Legacy system stopped")
        }
    }
    
    fun setTradingMode(mode: TradingMode) {
        Log.i(TAG, "🔧 BUILD #105 DIAGNOSTIC: setTradingMode() called - mode: $mode")
        if (!_isReady.value) {
            Log.w(TAG, "⚠️ Cannot set trading mode - system not ready")
            return
        }
        
        if (usingAIIntegration) {
            Log.d(TAG, "   Routing to AI integrated system")
            aiIntegratedSystem?.setTradingMode(mode)
        } else {
            Log.d(TAG, "   Routing to legacy system")
            legacyTradingSystem.setTradingMode(mode)
        }
    }
    
    /**
     * V5.17.0: Update HYBRID mode configuration.
     * Routes confidence gates, rate limits, and value thresholds
     * from SettingsViewModel through to the active trading coordinator.
     */
    fun updateHybridConfig(hybridConfig: HybridModeConfig) {
        if (!_isReady.value) return
        
        if (usingAIIntegration) {
            aiIntegratedSystem?.updateHybridConfig(hybridConfig)
        } else {
            legacyTradingSystem.updateHybridConfig(hybridConfig)
        }
    }
    
    fun activateKillSwitch(reason: String) {
        if (!_isReady.value) return
        
        if (usingAIIntegration) {
            scope.launch {
                aiIntegratedSystem?.emergencyStop(reason)
            }
        } else {
            legacyTradingSystem.activateKillSwitch(reason)
        }
    }
    
    fun resetKillSwitch() {
        SystemLogger.killswitch("🔄 Reset kill switch requested")
        Log.i(TAG, "🔄 BUILD #107: Reset kill switch requested")
        
        if (!_isReady.value) {
            SystemLogger.killswitch("⚠️ System not ready, forcing dashboard state reset anyway")
            Log.w(TAG, "⚠️ System not ready, forcing dashboard state reset anyway")
            
            // BUILD #107: Force reset dashboard state even if system not ready
            _dashboardState.update { current ->
                current.copy(
                    killSwitchActive = false,
                    riskWarning = null,
                    realizedPnlToday = 0.0,
                    unrealizedPnl = 0.0
                )
            }
            SystemLogger.killswitch("✅ Kill switch cleared in dashboard state")
            return
        }
        
        if (usingAIIntegration) {
            SystemLogger.killswitch("Resetting AI integrated system emergency stop")
            aiIntegratedSystem?.resetEmergencyStop()
        } else {
            SystemLogger.killswitch("Resetting legacy system kill switch")
            legacyTradingSystem.resetKillSwitch()
        }
        
        // BUILD #107: Also force reset dashboard state to be sure
        _dashboardState.update { current ->
            current.copy(
                killSwitchActive = false,
                riskWarning = null
            )
        }
        SystemLogger.killswitch("✅ Kill switch reset complete")
        Log.i(TAG, "✅ Kill switch reset complete")
    }
    
    /**
     * BUILD #107: Force restart the entire trading system.
     * Use this when the system is in a bad state and needs full reinitialization.
     */
    fun forceRestartSystem(startingBalance: Double = 100_000.0) {
        SystemLogger.system("🔄 FORCE RESTART SYSTEM requested")
        Log.w(TAG, "🔄 BUILD #107: FORCE RESTART SYSTEM")
        
        scope.launch {
            try {
                // Stop everything
                SystemLogger.system("⏹️ Stopping all trading activity")
                stopTrading()
                
                // Reset dashboard state to known good values
                SystemLogger.system("📊 Resetting dashboard state to default values")
                _dashboardState.value = DashboardState(
                    portfolioValue = startingBalance,
                    initialPortfolioValue = startingBalance,
                    paperTradingMode = true,
                    killSwitchActive = false,
                    riskWarning = null,
                    isTradingActive = false
                )
                
                // Mark as not ready
                _isReady.value = false
                _initializationState.value = InitializationState.NotInitialized
                
                // Small delay
                delay(1000)
                
                // Reinitialize
                SystemLogger.system("🔄 Reinitializing AI paper trading with live data")
                val result = initializeAIPaperTradingWithLiveData(
                    startingBalance = startingBalance,
                    tradingSymbols = listOf("BTC/USDT", "ETH/USDT", "SOL/USDT", "XRP/USDT"),
                    tradingMode = TradingMode.AUTONOMOUS,
                    primaryExchangeId = "binance"
                )
                
                if (result.isSuccess) {
                    SystemLogger.system("✅ System restart complete")
                    Log.i(TAG, "✅ System restart complete")
                    
                    // Start trading
                    startTrading()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    SystemLogger.error("❌ System restart failed: $error", result.exceptionOrNull())
                    Log.e(TAG, "❌ System restart failed: $error")
                }
            } catch (e: Exception) {
                SystemLogger.error("Force restart failed", e)
                Log.e(TAG, "Force restart failed", e)
            }
        }
    }
    
    // ========================================================================
    // POSITIONS & SIGNALS (ROUTED)
    // ========================================================================
    
    fun getPositions(): List<ManagedPosition> {
        if (!_isReady.value) return emptyList()
        
        return if (usingAIIntegration) {
            aiIntegratedSystem?.getManagedPositions() ?: emptyList()
        } else {
            legacyTradingSystem.getPositions()
        }
    }
    
    /**
     * V5.18.0: Get paper trading balances (asset -> amount).
     * Used by WalletViewModel to show unified balance.
     * Handles both AI and legacy systems.
     * BUILD #265: Always returns seeded balances — falls back to dashboard portfolioValue
     * as USDT if AI system not yet ready, so wallet never shows empty.
     */
    fun getAIIntegratedSystemBalances(): Map<String, Double> {
        // Try AI system first (has full seeded BTC/ETH/SOL/XRP + USDT)
        if (usingAIIntegration) {
            val aiBalances = aiIntegratedSystem?.getBalances()
            if (!aiBalances.isNullOrEmpty()) return aiBalances
        }
        
        // Fallback: use state balances from integration if available
        val stateBalances = aiIntegratedSystem?.state?.value?.balances
        if (!stateBalances.isNullOrEmpty()) return stateBalances
        
        // Final fallback: show portfolio value as USDT so wallet is never empty
        val portfolioVal = _dashboardState.value.portfolioValue
        return if (portfolioVal > 0.0) {
            mapOf("USDT" to portfolioVal)
        } else {
            legacyTradingSystem.systemState.value.let { state ->
                if (state.paperTradingMode) mapOf("USDT" to state.portfolioValue)
                else emptyMap()
            }
        }
    }
    
    fun getPendingSignals(): List<PendingTradeSignal> {
        if (!_isReady.value) return emptyList()
        
        return if (usingAIIntegration) {
            aiIntegratedSystem?.getPendingSignals() ?: emptyList()
        } else {
            legacyTradingSystem.getPendingSignals()
        }
    }
    
    suspend fun confirmSignal(signalId: String): Result<ExecutedTrade> {
        if (!_isReady.value) {
            return Result.failure(Exception("Trading system not initialized"))
        }
        
        return if (usingAIIntegration) {
            aiIntegratedSystem?.confirmSignal(signalId)
                ?: Result.failure(Exception("AI system not initialized"))
        } else {
            legacyTradingSystem.confirmSignal(signalId)
        }
    }
    
    fun rejectSignal(signalId: String) {
        if (!_isReady.value) return
        
        if (usingAIIntegration) {
            aiIntegratedSystem?.rejectSignal(signalId)
        } else {
            legacyTradingSystem.rejectSignal(signalId)
        }
    }
    
    suspend fun closePosition(symbol: String): Result<Unit> {
        if (!_isReady.value) {
            return Result.failure(Exception("Trading system not initialized"))
        }
        
        return if (usingAIIntegration) {
            aiIntegratedSystem?.closePosition(symbol)
                ?: Result.failure(Exception("AI system not initialized"))
        } else {
            legacyTradingSystem.closePosition(symbol)
        }
    }

    /**
     * BUILD #270: Close a specific position by its unique key (symbol_orderId).
     * Supports multiple positions per symbol — client has absolute control.
     */
    suspend fun closePositionById(positionKey: String): Result<Unit> {
        if (!_isReady.value) {
            return Result.failure(Exception("Trading system not initialized"))
        }
        
        return if (usingAIIntegration) {
            aiIntegratedSystem?.closePositionById(positionKey)
                ?: Result.failure(Exception("AI system not initialized"))
        } else {
            // Legacy TradingSystem only has closePosition(symbol).
            // positionKey format is "SYMBOL_orderId" — extract the symbol portion.
            // BUILD #270: Multiple positions per symbol not supported in legacy path;
            // closes the first open position for that symbol.
            val symbol = positionKey.substringBefore("_")
            legacyTradingSystem.closePosition(symbol)
        }
    }
    
    /**
     * Place an order through the trading system.
     * Supports both paper trading and live trading modes.
     */
    suspend fun placeOrder(orderRequest: OrderRequest): Result<ExecutedTrade> {
        if (!_isReady.value) {
            return Result.failure(Exception("Trading system not initialized"))
        }
        
        return if (usingAIIntegration) {
            aiIntegratedSystem?.placeOrder(orderRequest)
                ?: Result.failure(Exception("AI system not initialized"))
        } else {
            legacyTradingSystem.placeOrder(orderRequest)
        }
    }
    
    // ========================================================================
    // EXCHANGE INFO (ROUTED)
    // ========================================================================
    
    fun getConnectedExchanges(): List<String> {
        if (!_isReady.value) return emptyList()
        
        return if (usingAIIntegration) {
            aiIntegratedSystem?.getConnectedExchanges()?.toList() ?: emptyList()
        } else {
            legacyTradingSystem.getConnectedExchanges()
        }
    }
    
    /**
     * V5.17.0: Remove an exchange from the trading system.
     * Cleanly deregisters from SmartOrderRouter and AIConnectionManager.
     */
    suspend fun removeExchange(exchangeId: String) {
        if (usingAIIntegration) {
            aiIntegratedSystem?.removeExchange(exchangeId)
        }
    }
    
    fun getPQCSecurityStatus(): Map<String, ConnectorSecurityStatus> {
        if (!_isReady.value) return emptyMap()
        
        // PQC status only available in legacy system
        return if (!usingAIIntegration) {
            legacyTradingSystem.getPQCSecurityStatus()
        } else {
            emptyMap()
        }
    }
    
    // ========================================================================
    // PRICE SUBSCRIPTIONS
    // ========================================================================
    
    fun subscribeToPrices(symbols: List<String>) {
        if (!_isReady.value) return
        
        if (usingAIIntegration) {
            // AI system handles price subscriptions internally
            Log.d(TAG, "AI system manages price feeds automatically")
        } else {
            legacyTradingSystem.subscribeToPrices(symbols)
        }
    }
    
    // ========================================================================
    // AI SYSTEM SPECIFIC
    // ========================================================================
    
    /**
     * Get the AI integrated system for advanced operations.
     * Returns null if using legacy system.
     */
    fun getAISystem(): TradingSystemIntegration? {
        return if (usingAIIntegration) aiIntegratedSystem else null
    }
    
    /**
     * Check if using AI integration.
     */
    fun isUsingAIIntegration(): Boolean = usingAIIntegration
    
    /**
     * Get execution mode (AI system only).
     */
    fun getExecutionMode(): TradingExecutionMode? {
        return aiIntegratedSystem?.state?.value?.executionMode
    }
    
    /**
     * V5.17.0: Check if running in testnet/sandbox mode.
     */
    fun isTestnetMode(): Boolean {
        return aiIntegratedSystem?.isTestnetMode() ?: false
    }
    
    /**
     * Get margin status (AI system only).
     */
    fun getMarginStatus(): MarginStatus? {
        return aiIntegratedSystem?.getMarginStatus()
    }
    
    /**
     * Reset paper trading account (AI system only).
     */
    fun resetPaperTrading(newBalance: Double = 100_000.0) {
        aiIntegratedSystem?.resetPaperTrading(newBalance)
    }
    
    // ========================================================================
    // ASSET DISCOVERY PIPELINE
    // ========================================================================
    
    /**
     * Get pipeline state for UI display.
     */
    fun getPipelineState(): PipelineState? {
        return aiIntegratedSystem?.getAssetDiscoveryPipeline()?.state?.value
    }
    
    /**
     * Observe pipeline state changes.
     */
    fun observePipelineState(): StateFlow<PipelineState>? {
        return aiIntegratedSystem?.getAssetDiscoveryPipeline()?.state
    }
    
    /**
     * Get total registered assets from AssetRegistry.
     */
    fun getRegisteredAssetCount(): Int {
        return com.miwealth.sovereignvantage.core.trading.assets.AssetRegistry.getAll().size
    }
    
    /**
     * Manually trigger asset discovery pipeline.
     */
    suspend fun runAssetDiscovery(): Int {
        return aiIntegratedSystem?.runAssetDiscoveryPipeline() ?: 0
    }
    
    // ========================================================================
    // CLEANUP
    // ========================================================================
    
    fun shutdown() {
        scope.cancel()
        
        if (usingAIIntegration) {
            aiIntegratedSystem?.shutdown()
        } else {
            legacyTradingSystem.shutdown()
        }
        
        _isReady.value = false
        _initializationState.value = InitializationState.NotInitialized
    }
}

// ============================================================================
// STATE CLASSES
// ============================================================================

sealed class InitializationState {
    object NotInitialized : InitializationState()
    data class Initializing(val message: String) : InitializationState()
    object Ready : InitializationState()
    data class Error(val message: String) : InitializationState()
}

data class DashboardState(
    // Portfolio
    val portfolioValue: Double = 100000.0,
    val initialPortfolioValue: Double = 100000.0,
    val realizedPnlToday: Double = 0.0,
    val unrealizedPnl: Double = 0.0,
    // BUILD #266: Margin-based account display
    val totalEquity: Double = 100000.0,          // Cash + all unrealised P&L (mark-to-market)
    val availableMargin: Double = 100000.0,      // Equity minus posted margin
    val usedMargin: Double = 0.0,                // Sum of all posted margins across open positions
    val marginUtilisationPct: Double = 0.0,      // usedMargin / totalEquity × 100
    
    // Trading status
    val isTradingActive: Boolean = false,
    val tradingMode: TradingMode = TradingMode.SIGNAL_ONLY,
    val paperTradingMode: Boolean = true,
    val killSwitchActive: Boolean = false,
    val riskWarning: String? = null,
    val emergencyStopCooldownSecondsRemaining: Int = 0,  // BUILD #117: Cooldown countdown for UI
    
    // Positions & signals
    val activePositionCount: Int = 0,
    val pendingSignalCount: Int = 0,
    val tradesExecutedToday: Int = 0,
    
    // Last trade info
    val lastTradeSymbol: String? = null,
    val lastTradeSide: String? = null,
    val lastTradeTime: Long? = null,
    
    // Exchange info
    val activeExchange: String? = null,
    val connectedExchangeCount: Int = 0,
    val pqcSecurityEnabled: Boolean = false,
    val smartRoutingEnabled: Boolean = false,
    /** V5.17.0: Whether running against testnet/sandbox endpoints */
    val isTestnetMode: Boolean = false,
    
    // Scalping
    val scalpingActive: Boolean = false,
    val scalpsToday: Int = 0,
    val scalpingPnlToday: Double = 0.0,
    
    // V5.17.0: ML Health Monitor (from TradingCoordinator)
    val mlHealthStatus: String = "HEALTHY",       // HEALTHY, WARNING, CRITICAL
    val mlHealthSummary: String? = null,
    val mlRollbackCount: Int = 0,
    // V5.17.0: Disagreement Detection (from TradingCoordinator)
    val disagreementLevel: String = "STRONG_AGREEMENT",
    val disagreementScore: Double = 0.0,
    val positionSizeMultiplier: Double = 1.0,
    // V5.17.0: Board regime-aware position sizing
    val effectivePositionMultiplier: Double = 1.0,  // boardRec × disagreement
    // V5.18.0: Latest prices from feed (symbol -> price) for dashboard & wallet
    val latestPrices: Map<String, Double> = emptyMap(),
    // V5.18.0: 24h price change percentages (symbol -> change%)
    val priceChanges24h: Map<String, Double> = emptyMap()
) {
    // BUILD #300: Daily P&L = current portfolio - starting balance
    // Previous bug: used realizedPnlToday (always 0.0) + unrealizedPnl (incomplete)
    val dailyPnl: Double get() = portfolioValue - initialPortfolioValue
    
    val dailyPnlPercent: Double get() = if (initialPortfolioValue > 0) {
        (dailyPnl / initialPortfolioValue) * 100.0
    } else 0.0
    
    val totalReturn: Double get() = portfolioValue - initialPortfolioValue
    
    val totalReturnPercent: Double get() = if (initialPortfolioValue > 0) {
        (totalReturn / initialPortfolioValue) * 100.0
    } else 0.0
}

/**
 * Result of an AI Exchange Interface connection test.
 */
data class AIConnectionTestResult(
    val exchangeId: String,
    val isTestnet: Boolean,
    val pairsLoaded: Int,
    val message: String
)
