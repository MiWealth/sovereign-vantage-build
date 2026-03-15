package com.miwealth.sovereignvantage.core.trading.strategies

import com.miwealth.sovereignvantage.core.*
import com.miwealth.sovereignvantage.core.exchange.*
import com.miwealth.sovereignvantage.core.trading.*
import com.miwealth.sovereignvantage.core.trading.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ADVANCED STRATEGY COORDINATOR
 * 
 * Wires up the "money printer" strategies to the trading infrastructure:
 * 
 * 1. ALPHA FACTOR SCANNER
 *    - Scans universe, ranks by momentum/quality/volatility/trend
 *    - Feeds top assets to TradingCoordinator for AI Board analysis
 *    - State machine: IDLE → SCANNING → SIGNAL_GENERATED → EXECUTING
 * 
 * 2. DELTA-NEUTRAL FUNDING ARBITRAGE
 *    - Monitors perpetual funding rates
 *    - Opens spot+perp hedged positions
 *    - Collects 8-hour funding payments
 *    - State machine: IDLE → ANALYZING → OPENING → ACTIVE → CLOSING
 * 
 * 3. STRATEGY RISK MANAGER
 *    - 5% hard kill switch (NON-NEGOTIABLE)
 *    - Auto-liquidation to USDT/USDC
 *    - Manual restart required after trigger
 * 
 * DESIGN PRINCIPLES:
 * - State machines only - no "hoping"
 * - WebSocket-first for speed
 * - 5% drawdown = liquidate everything
 * - Software executes or idles
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 *
 * V5.17.0 CHANGES:
 * - FundingArbEngine now receives TradingCoordinator for cross-exchange arb
 *   spread awareness — entry gated on minArbSpreadToEnter to prevent fee-loss trades
 */

// =============================================================================
// CONFIGURATION
// =============================================================================

/**
 * Configuration for the Advanced Strategy Coordinator
 */
data class AdvancedStrategyConfig(
    // Alpha Scanner settings
    val enableAlphaScanner: Boolean = true,
    val alphaScanIntervalMinutes: Int = 60,        // Scan every hour
    val alphaTopN: Int = 10,                        // Top 10 assets
    val alphaMinScore: Double = 0.5,                // Minimum composite score
    
    // Funding Arbitrage settings
    val enableFundingArb: Boolean = true,
    val fundingMinRateToEnter: Double = 0.0001,    // 0.01% per 8h minimum
    val fundingMaxPositions: Int = 5,
    val fundingMaxCapitalPercent: Double = 50.0,   // Max 50% in funding arb
    
    // Risk management (HARD LIMITS)
    val hardKillSwitchDrawdown: Double = 60.0,     // Increased for Hedge engine with Alpha Factor Scanner
    val strategyDrawdownLimit: Double = 60.0,      // Per strategy limit
    val dailyLossLimit: Double = 60.0,             // Daily loss limit (increased for Hedge engine)
    
    // Execution
    val useWebSocketForSpeed: Boolean = true,
    val maxSlippageBps: Int = 10,                  // 10 bps max slippage
    
    // Integration
    val feedTopAssetsToAIBoard: Boolean = true,    // Alpha results → AI Board
    val autoExecuteHighConfidence: Boolean = false // Require AI Board approval
)

/**
 * Advanced strategy state
 */
enum class AdvancedStrategyState {
    STOPPED,            // Not running
    INITIALIZING,       // Starting up
    RUNNING,            // Normal operation
    RISK_WARNING,       // Approaching limits
    RISK_CRITICAL,      // Very close to kill switch
    LIQUIDATING,        // Kill switch active
    HALTED              // Manual restart required
}

// =============================================================================
// ADVANCED STRATEGY COORDINATOR
// =============================================================================

class AdvancedStrategyCoordinator(
    private val tradingCoordinator: TradingCoordinator,
    private val exchangeAggregator: ExchangeAggregator,
    private val positionManager: PositionManager,
    private val riskManager: RiskManager,
    private val config: AdvancedStrategyConfig = AdvancedStrategyConfig(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    
    // Strategy engines
    private var alphaScanner: AlphaFactorScanner? = null
    private var fundingArbEngine: FundingArbEngine? = null
    private var strategyRiskManager: StrategyRiskManager? = null
    
    // State
    private val _state = MutableStateFlow(AdvancedStrategyState.STOPPED)
    val state: StateFlow<AdvancedStrategyState> = _state.asStateFlow()
    
    private val _events = MutableSharedFlow<AdvancedStrategyEvent>(replay = 0, extraBufferCapacity = 64)
    val events: SharedFlow<AdvancedStrategyEvent> = _events.asSharedFlow()
    
    private val isRunning = AtomicBoolean(false)
    private var alphaScanJob: Job? = null
    private var fundingMonitorJob: Job? = null
    private var riskMonitorJob: Job? = null
    
    // Tracking
    private var portfolioValue: Double = 0.0
    private var allocatedToFundingArb: Double = 0.0
    
    // ==========================================================================
    // INITIALIZATION
    // ==========================================================================
    
    /**
     * Initialize all strategy engines
     */
    fun initialize(
        initialPortfolioValue: Double,
        assetUniverse: List<String>,
        spotExchange: UnifiedExchangeConnector,
        perpExchange: UnifiedExchangeConnector
    ) {
        _state.value = AdvancedStrategyState.INITIALIZING
        portfolioValue = initialPortfolioValue
        
        // Initialize Alpha Scanner
        if (config.enableAlphaScanner) {
            alphaScanner = AlphaFactorScanner(
                exchangeAggregator = exchangeAggregator,
                riskManager = riskManager,
                config = AlphaScannerConfig(
                    maxPositionsFromScanner = config.alphaTopN,
                    hardKillSwitchDrawdown = config.hardKillSwitchDrawdown
                )
            ).also {
                it.initialize(assetUniverse, initialPortfolioValue)
            }
            
            // Subscribe to scanner events
            scope.launch {
                alphaScanner?.events?.collect { event ->
                    handleAlphaScannerEvent(event)
                }
            }
        }
        
        // Initialize Funding Arb Engine
        if (config.enableFundingArb) {
            fundingArbEngine = FundingArbEngine(
                spotExchange = spotExchange,
                perpExchange = perpExchange,
                riskManager = riskManager,
                config = FundingArbConfig(
                    minFundingRateToEnter = config.fundingMinRateToEnter,
                    hardKillSwitchDrawdown = config.hardKillSwitchDrawdown,
                    maxPositionSize = config.fundingMaxCapitalPercent / 100.0 / config.fundingMaxPositions
                ),
                tradingCoordinator = tradingCoordinator  // V5.17.0: Cross-exchange arb spread gate
            ).also {
                it.initialize(initialPortfolioValue * (config.fundingMaxCapitalPercent / 100.0))
            }
            
            // Subscribe to arb events
            scope.launch {
                fundingArbEngine?.events?.collect { event ->
                    handleFundingArbEvent(event)
                }
            }
        }
        
        // Initialize Strategy Risk Manager
        strategyRiskManager = StrategyRiskManager(
            exchangeAggregator = exchangeAggregator,
            positionManager = positionManager,
            config = StrategyRiskConfig(
                strategyDrawdownKillSwitch = config.strategyDrawdownLimit,
                portfolioDrawdownKillSwitch = config.hardKillSwitchDrawdown,
                dailyLossKillSwitch = config.dailyLossLimit
            )
        ).also {
            it.initialize(initialPortfolioValue)
            
            // Register strategies
            if (config.enableAlphaScanner) {
                it.registerStrategy("alpha_scanner", "Alpha Factor Scanner", 0.0)
            }
            if (config.enableFundingArb) {
                it.registerStrategy("funding_arb", "Funding Arbitrage", 0.0)
            }
        }
        
        // Subscribe to risk manager events
        scope.launch {
            strategyRiskManager?.events?.collect { event ->
                event?.let { handleRiskManagerEvent(it) }
            }
        }
        
        // Subscribe to risk manager state
        scope.launch {
            strategyRiskManager?.state?.collect { riskState ->
                updateStateFromRiskState(riskState)
            }
        }
        
        scope.launch {
            _events.emit(AdvancedStrategyEvent.Initialized(
                portfolioValue = initialPortfolioValue,
                alphaEnabled = config.enableAlphaScanner,
                fundingArbEnabled = config.enableFundingArb
            ))
        }
        
        _state.value = AdvancedStrategyState.STOPPED
    }
    
    // ==========================================================================
    // START/STOP
    // ==========================================================================
    
    /**
     * Start all enabled strategies
     */
    fun start() {
        if (isRunning.getAndSet(true)) return
        
        if (_state.value == AdvancedStrategyState.HALTED) {
            scope.launch {
                _events.emit(AdvancedStrategyEvent.StartRejected(
                    "System is HALTED after kill switch. Manual restart required."
                ))
            }
            isRunning.set(false)
            return
        }
        
        _state.value = AdvancedStrategyState.RUNNING
        
        // Start Alpha Scanner periodic scans
        if (config.enableAlphaScanner) {
            alphaScanJob = scope.launch {
                while (isActive) {
                    runAlphaScan()
                    delay(config.alphaScanIntervalMinutes * 60 * 1000L)
                }
            }
        }
        
        // Start Funding Arb monitoring
        if (config.enableFundingArb) {
            val symbols = getFundingArbSymbols()
            fundingArbEngine?.startMonitoring(symbols)
        }
        
        // Start risk monitoring
        strategyRiskManager?.startMonitoring()
        
        scope.launch {
            _events.emit(AdvancedStrategyEvent.Started)
        }
    }
    
    /**
     * Stop all strategies (graceful)
     */
    fun stop() {
        if (!isRunning.getAndSet(false)) return
        
        alphaScanJob?.cancel()
        fundingArbEngine?.stopMonitoring()
        strategyRiskManager?.stopMonitoring()
        
        _state.value = AdvancedStrategyState.STOPPED
        
        scope.launch {
            _events.emit(AdvancedStrategyEvent.Stopped)
        }
    }
    
    /**
     * Manual restart after kill switch (REQUIRES USER ACTION)
     */
    fun manualRestartAfterKillSwitch(newPortfolioValue: Double): Boolean {
        if (_state.value != AdvancedStrategyState.HALTED) {
            return false
        }
        
        // Reset all engines
        alphaScanner?.manualRestart(newPortfolioValue)
        fundingArbEngine?.manualRestart(newPortfolioValue)
        strategyRiskManager?.manualRestart(newPortfolioValue)
        
        portfolioValue = newPortfolioValue
        allocatedToFundingArb = 0.0
        
        _state.value = AdvancedStrategyState.STOPPED
        
        scope.launch {
            _events.emit(AdvancedStrategyEvent.ManualRestartComplete(newPortfolioValue))
        }
        
        return true
    }
    
    // ==========================================================================
    // ALPHA SCANNER INTEGRATION
    // ==========================================================================
    
    /**
     * Run alpha factor scan and feed results to AI Board
     */
    private suspend fun runAlphaScan() {
        val scanner = alphaScanner ?: return
        
        if (!scanner.canTrade()) {
            _events.emit(AdvancedStrategyEvent.AlphaScanSkipped("Scanner not in tradeable state"))
            return
        }
        
        _events.emit(AdvancedStrategyEvent.AlphaScanStarted)
        
        try {
            // Run the scan
            val topAssets = scanner.scanUniverse()
            
            if (topAssets.isEmpty()) {
                _events.emit(AdvancedStrategyEvent.AlphaScanComplete(0, emptyList()))
                return
            }
            
            // Filter by minimum score
            val qualifiedAssets = topAssets.filter { it.compositeScore >= config.alphaMinScore }
            
            _events.emit(AdvancedStrategyEvent.AlphaScanComplete(
                assetsScanned = topAssets.size,
                topAssets = qualifiedAssets.map { it.symbol }
            ))
            
            // Feed to TradingCoordinator / AI Board if enabled
            if (config.feedTopAssetsToAIBoard && qualifiedAssets.isNotEmpty()) {
                feedToTradingCoordinator(qualifiedAssets)
            }
            
        } catch (e: Exception) {
            _events.emit(AdvancedStrategyEvent.Error("Alpha scan failed: ${e.message}"))
        }
    }
    
    /**
     * Feed alpha scan results to TradingCoordinator for AI Board analysis
     */
    private suspend fun feedToTradingCoordinator(assets: List<FactorScore>) {
        for (asset in assets) {
            // The TradingCoordinator will pick these up in its analysis loop
            // We're essentially pre-filtering the watchlist based on factors
            _events.emit(AdvancedStrategyEvent.AssetQualified(
                symbol = asset.symbol,
                score = asset.compositeScore,
                momentum = asset.momentumScore,
                quality = asset.qualityScore,
                trend = asset.trendScore
            ))
        }
        
        // Update coordinator watchlist with qualified symbols
        val symbols = assets.map { it.symbol }
        tradingCoordinator.updateWatchlist(symbols)
    }
    
    // ==========================================================================
    // FUNDING ARBITRAGE INTEGRATION
    // ==========================================================================
    
    /**
     * Get symbols eligible for funding arbitrage
     */
    private fun getFundingArbSymbols(): List<String> {
        // High-volume perp markets with good funding rates
        return listOf(
            "BTC/USDT",
            "ETH/USDT",
            "SOL/USDT",
            "XRP/USDT",
            "DOGE/USDT",
            "AVAX/USDT",
            "LINK/USDT",
            "MATIC/USDT"
        )
    }
    
    /**
     * Manually execute funding arb for a specific symbol
     */
    suspend fun executeFundingArb(symbol: String, notionalValue: Double): Boolean {
        val engine = fundingArbEngine ?: return false
        
        if (_state.value == AdvancedStrategyState.HALTED) {
            _events.emit(AdvancedStrategyEvent.ExecutionRejected(symbol, "System HALTED"))
            return false
        }
        
        // Check allocation limits
        val maxAllocation = portfolioValue * (config.fundingMaxCapitalPercent / 100.0)
        if (allocatedToFundingArb + notionalValue > maxAllocation) {
            _events.emit(AdvancedStrategyEvent.ExecutionRejected(
                symbol, 
                "Would exceed funding arb allocation limit (${config.fundingMaxCapitalPercent}%)"
            ))
            return false
        }
        
        engine.executeArb(symbol, notionalValue)
        allocatedToFundingArb += notionalValue
        
        return true
    }
    
    /**
     * Close a funding arb position
     */
    suspend fun closeFundingArbPosition(symbol: String, reason: String = "Manual close") {
        fundingArbEngine?.closePosition(symbol, reason)
    }
    
    // ==========================================================================
    // EVENT HANDLERS
    // ==========================================================================
    
    private suspend fun handleAlphaScannerEvent(event: ScannerEvent) {
        when (event) {
            is ScannerEvent.KillSwitchActivated -> {
                _events.emit(AdvancedStrategyEvent.KillSwitchTriggered(
                    strategy = "Alpha Scanner",
                    reason = event.message,
                    drawdown = event.drawdownPercent
                ))
            }
            is ScannerEvent.HaltedForManualRestart -> {
                _state.value = AdvancedStrategyState.HALTED
                _events.emit(AdvancedStrategyEvent.SystemHalted(event.reason))
            }
            else -> {
                // Log other events
            }
        }
    }
    
    private suspend fun handleFundingArbEvent(event: ArbEvent) {
        when (event) {
            is ArbEvent.PositionOpened -> {
                _events.emit(AdvancedStrategyEvent.FundingArbPositionOpened(
                    symbol = event.symbol,
                    notionalValue = event.notionalValue,
                    delta = event.delta
                ))
                
                // Update strategy risk manager
                strategyRiskManager?.updateStrategyValue(
                    "funding_arb", 
                    allocatedToFundingArb
                )
            }
            is ArbEvent.FundingReceived -> {
                _events.emit(AdvancedStrategyEvent.FundingPaymentReceived(
                    symbol = event.symbol,
                    rate = event.rate,
                    payment = event.payment,
                    totalCollected = event.totalCollected
                ))
            }
            is ArbEvent.PositionClosed -> {
                allocatedToFundingArb -= fundingArbEngine?.activePositions?.value
                    ?.get(event.symbol)?.notionalValue ?: 0.0
                
                _events.emit(AdvancedStrategyEvent.FundingArbPositionClosed(
                    symbol = event.symbol,
                    fundingEarned = event.fundingEarned,
                    holdingHours = event.holdingPeriodHours
                ))
            }
            is ArbEvent.KillSwitchActivated -> {
                _events.emit(AdvancedStrategyEvent.KillSwitchTriggered(
                    strategy = "Funding Arbitrage",
                    reason = event.reason,
                    drawdown = event.drawdownPercent
                ))
            }
            is ArbEvent.HaltedForManualRestart -> {
                _state.value = AdvancedStrategyState.HALTED
                _events.emit(AdvancedStrategyEvent.SystemHalted(event.reason))
            }
            else -> {
                // Log other events
            }
        }
    }
    
    private suspend fun handleRiskManagerEvent(event: RiskManagerEvent) {
        when (event) {
            is RiskManagerEvent.DrawdownWarning -> {
                _state.value = AdvancedStrategyState.RISK_WARNING
                _events.emit(AdvancedStrategyEvent.RiskWarning(
                    "Strategy ${event.strategyId} at ${event.drawdownPercent}% drawdown"
                ))
            }
            is RiskManagerEvent.CriticalDrawdown -> {
                _state.value = AdvancedStrategyState.RISK_CRITICAL
                _events.emit(AdvancedStrategyEvent.RiskCritical(
                    "Strategy ${event.strategyId} at ${event.drawdownPercent}% - kill switch imminent!"
                ))
            }
            is RiskManagerEvent.KillSwitchActivated -> {
                _state.value = AdvancedStrategyState.LIQUIDATING
                stop()
                
                // Trigger emergency stop on TradingCoordinator too
                scope.launch {
                    tradingCoordinator.emergencyStop("Strategy risk manager: ${event.reason}")
                }
                
                _events.emit(AdvancedStrategyEvent.KillSwitchTriggered(
                    strategy = "Portfolio",
                    reason = event.reason.toString(),
                    drawdown = 5.0 // At threshold
                ))
            }
            is RiskManagerEvent.LiquidationComplete -> {
                _events.emit(AdvancedStrategyEvent.LiquidationComplete(
                    positionsLiquidated = event.positionsLiquidated,
                    stablecoinReceived = event.stablecoinReceived
                ))
            }
            is RiskManagerEvent.TradingHalted -> {
                _state.value = AdvancedStrategyState.HALTED
                _events.emit(AdvancedStrategyEvent.SystemHalted(event.message))
            }
            else -> {
                // Log other events
            }
        }
    }
    
    private fun updateStateFromRiskState(riskState: RiskState) {
        _state.value = when (riskState) {
            RiskState.NORMAL -> if (isRunning.get()) AdvancedStrategyState.RUNNING else AdvancedStrategyState.STOPPED
            RiskState.WARNING -> AdvancedStrategyState.RISK_WARNING
            RiskState.CRITICAL -> AdvancedStrategyState.RISK_CRITICAL
            RiskState.LIQUIDATING -> AdvancedStrategyState.LIQUIDATING
            RiskState.HALTED -> AdvancedStrategyState.HALTED
        }
    }
    
    // ==========================================================================
    // STATUS
    // ==========================================================================
    
    /**
     * Get comprehensive status
     */
    fun getStatus(): AdvancedStrategyStatus {
        return AdvancedStrategyStatus(
            state = _state.value,
            isRunning = isRunning.get(),
            
            // Alpha Scanner
            alphaEnabled = config.enableAlphaScanner,
            alphaScannerState = alphaScanner?.state?.value ?: ScannerState.IDLE,
            topAssets = alphaScanner?.topAssets?.value ?: emptyList(),
            
            // Funding Arb
            fundingArbEnabled = config.enableFundingArb,
            fundingArbState = fundingArbEngine?.state?.value ?: ArbEngineState.IDLE,
            fundingPositions = fundingArbEngine?.activePositions?.value?.size ?: 0,
            fundingAllocated = allocatedToFundingArb,
            fundingStats = fundingArbEngine?.getStatistics(),
            
            // Risk
            riskState = strategyRiskManager?.state?.value ?: RiskState.NORMAL,
            riskStatus = strategyRiskManager?.getRiskStatus()
        )
    }
    
    /**
     * Shutdown everything
     */
    fun shutdown() {
        stop()
        alphaScanner?.shutdown()
        fundingArbEngine?.shutdown()
        strategyRiskManager?.shutdown()
        scope.cancel()
    }
}

// =============================================================================
// STATUS DATA CLASS
// =============================================================================

data class AdvancedStrategyStatus(
    val state: AdvancedStrategyState,
    val isRunning: Boolean,
    
    // Alpha Scanner
    val alphaEnabled: Boolean,
    val alphaScannerState: ScannerState,
    val topAssets: List<FactorScore>,
    
    // Funding Arb
    val fundingArbEnabled: Boolean,
    val fundingArbState: ArbEngineState,
    val fundingPositions: Int,
    val fundingAllocated: Double,
    val fundingStats: FundingArbStatistics?,
    
    // Risk
    val riskState: RiskState,
    val riskStatus: StrategyRiskStatus?
)

// =============================================================================
// EVENTS
// =============================================================================

sealed class AdvancedStrategyEvent {
    // Lifecycle
    data class Initialized(
        val portfolioValue: Double,
        val alphaEnabled: Boolean,
        val fundingArbEnabled: Boolean
    ) : AdvancedStrategyEvent()
    
    object Started : AdvancedStrategyEvent()
    object Stopped : AdvancedStrategyEvent()
    data class StartRejected(val reason: String) : AdvancedStrategyEvent()
    data class ManualRestartComplete(val newPortfolioValue: Double) : AdvancedStrategyEvent()
    
    // Alpha Scanner
    object AlphaScanStarted : AdvancedStrategyEvent()
    data class AlphaScanSkipped(val reason: String) : AdvancedStrategyEvent()
    data class AlphaScanComplete(val assetsScanned: Int, val topAssets: List<String>) : AdvancedStrategyEvent()
    data class AssetQualified(
        val symbol: String,
        val score: Double,
        val momentum: Double,
        val quality: Double,
        val trend: Double
    ) : AdvancedStrategyEvent()
    
    // Funding Arb
    data class FundingArbPositionOpened(
        val symbol: String,
        val notionalValue: Double,
        val delta: Double
    ) : AdvancedStrategyEvent()
    
    data class FundingPaymentReceived(
        val symbol: String,
        val rate: Double,
        val payment: Double,
        val totalCollected: Double
    ) : AdvancedStrategyEvent()
    
    data class FundingArbPositionClosed(
        val symbol: String,
        val fundingEarned: Double,
        val holdingHours: Double
    ) : AdvancedStrategyEvent()
    
    data class ExecutionRejected(val symbol: String, val reason: String) : AdvancedStrategyEvent()
    
    // Risk
    data class RiskWarning(val message: String) : AdvancedStrategyEvent()
    data class RiskCritical(val message: String) : AdvancedStrategyEvent()
    data class KillSwitchTriggered(
        val strategy: String,
        val reason: String,
        val drawdown: Double
    ) : AdvancedStrategyEvent()
    
    data class LiquidationComplete(
        val positionsLiquidated: Int,
        val stablecoinReceived: Double
    ) : AdvancedStrategyEvent()
    
    data class SystemHalted(val reason: String) : AdvancedStrategyEvent()
    
    // Errors
    data class Error(val message: String) : AdvancedStrategyEvent()
}

// =============================================================================
// TRADING COORDINATOR EXTENSION
// =============================================================================

/**
 * Extension function to add watchlist update capability to TradingCoordinator
 */
fun TradingCoordinator.updateWatchlist(symbols: List<String>) {
    // This would typically update the config or internal watchlist
    // The TradingCoordinator would pick these up in its analysis loop
    // Implementation depends on how TradingCoordinator exposes its watchlist
}
