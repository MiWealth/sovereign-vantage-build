package com.miwealth.sovereignvantage.core

import android.content.Context
import android.util.Log
import com.miwealth.sovereignvantage.core.exchange.*
import com.miwealth.sovereignvantage.core.exchange.BinancePublicPriceFeed
import com.miwealth.sovereignvantage.core.exchange.ai.AIConnectionManager
import com.miwealth.sovereignvantage.core.exchange.ai.ExchangeTestnetConfig
import com.miwealth.sovereignvantage.core.exchange.ai.TradingExecutionMode
import com.miwealth.sovereignvantage.core.security.EncryptedPrefsManager
import com.miwealth.sovereignvantage.core.trading.*
import com.miwealth.sovereignvantage.core.trading.assets.PipelineState
import com.miwealth.sovereignvantage.core.trading.engine.*
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
    @ApplicationContext private val context: Context
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
    
    // ========================================================================
    // DASHBOARD STATE (Aggregated for UI)
    // ========================================================================
    
    private val _dashboardState = MutableStateFlow(DashboardState())
    val dashboardState: StateFlow<DashboardState> = _dashboardState.asStateFlow()
    
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
        tradingSymbols: List<String> = listOf("BTC/USDT", "ETH/USDT"),
        tradingMode: TradingMode = TradingMode.SIGNAL_ONLY
    ): Result<Unit> {
        _initializationState.value = InitializationState.Initializing("Starting AI paper trading...")
        usingAIIntegration = true
        
        return try {
            aiIntegratedSystem = TradingSystemIntegration.getInstance(context)
            
            val config = TradingSystemConfig(
                executionMode = TradingExecutionMode.PAPER,
                tradingMode = tradingMode,
                paperTradingBalance = startingBalance,
                tradingSymbols = tradingSymbols,
                useStahlStops = true,
                enableAssetDiscovery = true  // Run discovery pipeline
            )
            
            val result = aiIntegratedSystem!!.initialize(config)
            
            if (result.isSuccess) {
                _initializationState.value = InitializationState.Ready
                _isReady.value = true
                startAIStateCollection()
                updateDashboardFromAISystem()
                
                // V5.17.0: AUTO-START PRICE FEED after paper trading init
                // This was the P0 blocker — prices never appeared because start()
                // was gated behind user tapping "AI Trading" toggle.
                // startPriceFeedOnly() starts price data flow WITHOUT starting
                // the trading coordinator. User controls coordinator via toggle.
                aiIntegratedSystem?.startPriceFeedOnly()
                
                Log.i(TAG, "AI paper trading initialized with balance: $startingBalance")
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
        _initializationState.value = InitializationState.Initializing("Starting paper trading with live prices from $primaryExchangeId...")
        usingAIIntegration = true
        
        return try {
            aiIntegratedSystem = TradingSystemIntegration.getInstance(context)
            
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
            
            val result = aiIntegratedSystem!!.initialize(config)
            
            if (result.isSuccess) {
                _initializationState.value = InitializationState.Ready
                _isReady.value = true
                startAIStateCollection()
                updateDashboardFromAISystem()
                
                // Auto-start price feed
                aiIntegratedSystem?.startPriceFeedOnly()
                
                Log.i(TAG, "Paper trading with LIVE prices initialized (Binance WS)")
            } else {
                _initializationState.value = InitializationState.Error(
                    result.exceptionOrNull()?.message ?: "Unknown error"
                )
            }
            result
        } catch (e: Exception) {
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
            aiIntegratedSystem = TradingSystemIntegration.getInstance(context)
            
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
            aiIntegratedSystem = TradingSystemIntegration.getInstance(context)
            
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
        
        // V5.18.0: Observe Binance public feed for dashboard market cards
        startPublicPriceFeedObservation()
    }
    
    private fun updateDashboardFromAIState(state: IntegratedTradingState) {
        _dashboardState.update { current ->
            current.copy(
                portfolioValue = state.portfolioValue,
                activePositionCount = state.coordinatorState.activePositions.size,
                isTradingActive = state.coordinatorState.isRunning,
                tradingMode = state.coordinatorState.mode,
                paperTradingMode = state.executionMode == TradingExecutionMode.PAPER ||
                                   state.executionMode == TradingExecutionMode.PAPER_WITH_LIVE_DATA,
                killSwitchActive = state.coordinatorState.emergencyStopActive,
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
                latestPrices = state.latestPrices
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
                _dashboardState.update { it.copy(killSwitchActive = true, riskWarning = event.reason) }
            }
            is TradingSystemEvent.KillSwitchReset -> {
                _dashboardState.update { it.copy(killSwitchActive = false, riskWarning = null) }
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
                current.copy(
                    portfolioValue = system.getPortfolioValue(),
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
                _dashboardState.update { current ->
                    current.copy(
                        tradesExecutedToday = current.tradesExecutedToday + 1,
                        lastTradeSymbol = event.trade.symbol,
                        lastTradeSide = event.trade.direction.name,
                        lastTradeTime = System.currentTimeMillis()
                    )
                }
            }
            is CoordinatorEvent.PositionUpdated -> {
                val positions = if (usingAIIntegration) {
                    aiIntegratedSystem?.getManagedPositions() ?: emptyList()
                } else {
                    legacyTradingSystem.getPositions()
                }
                _dashboardState.update { it.copy(activePositionCount = positions.size) }
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
     */
    private fun startPublicPriceFeedObservation() {
        scope.launch {
            val feed = BinancePublicPriceFeed.getInstance()
            feed.latestPrices.collect { priceMap ->
                val prices = priceMap.mapValues { it.value.last }
                val changes = priceMap.mapValues { it.value.change24hPercent }
                _dashboardState.update { current ->
                    current.copy(
                        latestPrices = prices,
                        priceChanges24h = changes
                    )
                }
            }
        }
    }
    
    // ========================================================================
    // TRADING CONTROL (ROUTED)
    // ========================================================================
    
    fun startTrading() {
        if (!_isReady.value) return
        
        if (usingAIIntegration) {
            aiIntegratedSystem?.start()
        } else {
            legacyTradingSystem.startTrading()
        }
    }
    
    fun stopTrading() {
        if (!_isReady.value) return
        
        if (usingAIIntegration) {
            aiIntegratedSystem?.stop()
        } else {
            legacyTradingSystem.stopTrading()
        }
    }
    
    fun setTradingMode(mode: TradingMode) {
        if (!_isReady.value) return
        
        if (usingAIIntegration) {
            aiIntegratedSystem?.setTradingMode(mode)
        } else {
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
        if (!_isReady.value) return
        
        if (usingAIIntegration) {
            aiIntegratedSystem?.resetEmergencyStop()
        } else {
            legacyTradingSystem.resetKillSwitch()
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
     * Returns asset -> amount map (e.g. "USDT" -> 100000.0, "BTC" -> 0.5)
     */
    fun getAIIntegratedSystemBalances(): Map<String, Double> {
        return if (usingAIIntegration) {
            aiIntegratedSystem?.getBalances() ?: emptyMap()
        } else {
            legacyTradingSystem.systemState.value.let { state ->
                if (state.paperTradingMode) {
                    mapOf("USDT" to state.portfolioValue)
                } else {
                    emptyMap()
                }
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
    
    // Trading status
    val isTradingActive: Boolean = false,
    val tradingMode: TradingMode = TradingMode.SIGNAL_ONLY,
    val paperTradingMode: Boolean = true,
    val killSwitchActive: Boolean = false,
    val riskWarning: String? = null,
    
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
    val dailyPnl: Double get() = realizedPnlToday + unrealizedPnl
    
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
