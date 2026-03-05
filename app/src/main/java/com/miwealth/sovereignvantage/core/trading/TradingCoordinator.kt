package com.miwealth.sovereignvantage.core.trading

import android.util.Log
import com.miwealth.sovereignvantage.core.*
import com.miwealth.sovereignvantage.core.ai.*
import com.miwealth.sovereignvantage.core.gamification.*
import com.miwealth.sovereignvantage.core.ml.*
import com.miwealth.sovereignvantage.core.signals.*
import com.miwealth.sovereignvantage.core.trading.assets.*
import com.miwealth.sovereignvantage.core.trading.engine.*
import com.miwealth.sovereignvantage.core.trading.scalping.*
import com.miwealth.sovereignvantage.data.local.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.miwealth.sovereignvantage.data.models.OrderBook
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * SOVEREIGN VANTAGE V5.17.0 "ARTHUR EDITION"
 * TRADING COORDINATOR - MERGED & ENHANCED
 * 
 * THE BRAIN: Central nervous system connecting all trading components.
 * 
 * V5.17.0 CHANGES:
 * - WebSocket order book feed: onOrderBookUpdate(OrderBook) populates
 *   crossExchangePrices with real bid/ask from order book depth data
 * - crossExchangeOrderBooks map: stores latest OrderBook snapshot per
 *   exchange per symbol for strategy engines needing depth info
 * - getArbSpread() now uses real best-bid/best-ask from order books
 *   instead of approximating bid=ask=last from ticker
 * - getBestOrderBook(symbol) accessor for strategy engines
 * 
 * V5.17.0 CHANGES:
 * - Cross-exchange price map for arb/hedge spread detection
 * - Exchange-aware onPriceTick(symbol, price, volume, exchange) overload
 * - getCrossExchangePrices() and getArbSpread() for strategy engines
 * - connectToPriceFeed extension now passes exchange source through
 * 
 * V5.17.0 CHANGES:
 * - AI Exchange Interface fully wired to TradingSystem
 * - AIConnectionManager → AIExchangeAdapterFactory → OrderExecutor flow complete
 * - Added modifyOrder() and isRateLimited() to AIExchangeConnector
 * - Smart order routing through AIConnectionManager
 * 
 * This is the CRITICAL CONNECTOR that bridges:
 * 1. Real-time price feeds → AI Board analysis
 * 2. AI Board consensus → Signal generation  
 * 3. Signals → Order execution (with 5 modes)
 * 4. Position management → STAHL Stair Stop™ trailing
 * 5. Trade outcomes → Gamification system
 * 6. All decisions → XAI compliance persistence
 * 7. Advanced strategies → Alpha Scanner, Funding Arb (NEW)
 * 
 * FIVE OPERATING MODES:
 * - AUTONOMOUS: AI decides and executes automatically
 * - SIGNAL_ONLY: AI suggests, user confirms each trade
 * - HYBRID: Configurable auto-execute with threshold controls
 * - SCALPING: High-frequency mode with ultra-tight STAHL stops
 * - ALPHA_SCANNER: Multi-factor quantitative ranking (NEW)
 * 
 * HARD KILL SWITCH:
 * 5% drawdown = LIQUIDATE TO STABLECOIN
 * No negotiation. No hoping. Execute or idle.
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * 
 * Dedicated to Cathryn for tolerating me and my quirks. 💘
 */

// ============================================================================
// OPERATING MODES
// ============================================================================

enum class TradingMode {
    AUTONOMOUS,      // AI decides and executes automatically
    SIGNAL_ONLY,     // AI suggests, user must confirm each trade
    HYBRID,          // Configurable: auto small trades, confirm large (with bypass option)
    SCALPING,        // High-frequency mode with ultra-tight STAHL stops
    ALPHA_SCANNER,   // Multi-factor quantitative ranking (scans universe, ranks by factors)
    FUNDING_ARB      // Delta-neutral perpetual funding arbitrage (spot+perp hedge)
}

// ============================================================================
// HYBRID MODE CONFIGURATION
// ============================================================================

/**
 * Configuration for HYBRID mode threshold-based confirmation.
 * Gives users maximum control over when to require manual confirmation.
 */
data class HybridModeConfig(
    /** If true, bypasses ALL confirmation (acts like AUTONOMOUS) */
    val bypassAllConfirmation: Boolean = false,
    
    // ---- V5.17.0: Confidence-Based Gates (from SettingsViewModel) ----
    /** AI confidence % at or above which trades auto-execute (fast path) */
    val confidenceAutoExecuteThreshold: Double = 85.0,
    /** AI confidence % below which trades ALWAYS require confirmation (hard floor) */
    val confidenceRequireConfirmationBelow: Double = 70.0,
    /** Maximum auto-executed trades per hour in HYBRID mode (rate limiter) */
    val maxAutoTradesPerHour: Int = 3,
    
    // ---- Position Size Threshold ----
    /** Use position size as % of portfolio for threshold */
    val usePositionSizeThreshold: Boolean = true,
    /** Auto-execute if position is below this % of portfolio */
    val positionSizeThresholdPercent: Double = 5.0,
    
    // ---- Risk Amount Threshold ----
    /** Use risk amount as % of capital for threshold */
    val useRiskAmountThreshold: Boolean = false,
    /** Auto-execute if risk is below this % of capital */
    val riskAmountThresholdPercent: Double = 2.0,
    
    // ---- Absolute Value Threshold ----
    /** Use absolute dollar value for threshold */
    val useAbsoluteValueThreshold: Boolean = true,
    /** Auto-execute if trade value is below this amount (in account currency, AU$) */
    val absoluteValueThreshold: Double = 5_000.0,
    
    // ---- Threshold Logic ----
    /** 
     * When multiple thresholds are enabled:
     * - ALL: Trade must pass ALL enabled thresholds to auto-execute
     * - ANY: Trade auto-executes if it passes ANY enabled threshold
     */
    val multipleThresholdLogic: ThresholdLogic = ThresholdLogic.ALL
) {
    companion object {
        /** Conservative: Small position threshold, tight confidence, low rate */
        val CONSERVATIVE = HybridModeConfig(
            bypassAllConfirmation = false,
            confidenceAutoExecuteThreshold = 90.0,
            confidenceRequireConfirmationBelow = 80.0,
            maxAutoTradesPerHour = 2,
            usePositionSizeThreshold = true,
            positionSizeThresholdPercent = 2.0,
            useRiskAmountThreshold = true,
            riskAmountThresholdPercent = 1.0,
            useAbsoluteValueThreshold = true,
            absoluteValueThreshold = 2_000.0,
            multipleThresholdLogic = ThresholdLogic.ALL
        )
        
        /** Moderate: Balanced thresholds */
        val MODERATE = HybridModeConfig(
            bypassAllConfirmation = false,
            confidenceAutoExecuteThreshold = 85.0,
            confidenceRequireConfirmationBelow = 70.0,
            maxAutoTradesPerHour = 3,
            usePositionSizeThreshold = true,
            positionSizeThresholdPercent = 5.0,
            useRiskAmountThreshold = false,
            useAbsoluteValueThreshold = true,
            absoluteValueThreshold = 5_000.0,
            multipleThresholdLogic = ThresholdLogic.ALL
        )
        
        /** Aggressive: Higher thresholds, more auto-execution */
        val AGGRESSIVE = HybridModeConfig(
            bypassAllConfirmation = false,
            confidenceAutoExecuteThreshold = 75.0,
            confidenceRequireConfirmationBelow = 55.0,
            maxAutoTradesPerHour = 8,
            usePositionSizeThreshold = true,
            positionSizeThresholdPercent = 10.0,
            useRiskAmountThreshold = false,
            useAbsoluteValueThreshold = true,
            absoluteValueThreshold = 15_000.0,
            multipleThresholdLogic = ThresholdLogic.ANY
        )
        
        /** Full Auto: Bypass all confirmation (same as AUTONOMOUS but in HYBRID mode) */
        val FULL_AUTO = HybridModeConfig(
            bypassAllConfirmation = true
        )
        
        /** Whale: Use absolute value threshold for large portfolios */
        fun forLargePortfolio(absoluteThreshold: Double) = HybridModeConfig(
            bypassAllConfirmation = false,
            confidenceAutoExecuteThreshold = 85.0,
            confidenceRequireConfirmationBelow = 70.0,
            maxAutoTradesPerHour = 5,
            usePositionSizeThreshold = false,
            useAbsoluteValueThreshold = true,
            absoluteValueThreshold = absoluteThreshold,
            multipleThresholdLogic = ThresholdLogic.ALL
        )
    }
}

enum class ThresholdLogic {
    ALL,  // Must pass ALL enabled thresholds
    ANY   // Must pass ANY enabled threshold
}

// ============================================================================
// MAIN CONFIGURATION
// ============================================================================

data class TradingCoordinatorConfig(
    val mode: TradingMode = TradingMode.SIGNAL_ONLY,
    val analysisIntervalMs: Long = 60_000,           // 1 minute default
    val minConfidenceToTrade: Double = 0.01,         // BUILD #113: FORCE TRADING - was 0.6
    val minBoardAgreement: Int = 2,                  // BUILD #113: FORCE TRADING - was 5 (out of 8)
    val useStahlStops: Boolean = true,               // Apply STAHL Stair Stop™
    val maxConcurrentPositions: Int = 5,             // Maximum open positions
    val defaultPositionSizePercent: Double = 10.0,   // Default position size (% of portfolio)
    val defaultRiskPercent: Double = 2.0,            // Default risk per trade (% of capital)
    val cooldownAfterTradeMs: Long = 300_000,        // 5 minute cooldown after trade
    val enabledSymbols: List<String>? = null,        // Explicit symbol list (null = use filter/registry)
    val assetFilter: AssetFilter? = null,            // Filter for dynamic symbol selection
    val paperTradingMode: Boolean = true,            // Paper trading by default for safety
    val maxTradesPerHour: Int = 10,                  // Rate limit - hourly
    val maxTradesPerDay: Int = 50,                   // Rate limit - daily
    
    // HYBRID mode specific
    val hybridConfig: HybridModeConfig = HybridModeConfig.MODERATE,
    
    // Scalping-specific configuration (only used when mode = SCALPING)
    val scalpingConfig: ScalpingConfig? = null
) {
    /**
     * Resolve the actual symbols to trade.
     * Priority: explicit enabledSymbols > assetFilter > registry defaults
     */
    fun resolveSymbols(): List<String> {
        // 1. If explicit symbols provided, use them
        if (!enabledSymbols.isNullOrEmpty()) {
            return enabledSymbols
        }
        
        // 2. If filter provided, query registry with filter
        assetFilter?.let { filter ->
            val filtered = AssetRegistry.filter(filter)
            if (filtered.isNotEmpty()) {
                return filtered.map { it.symbol }
            }
        }
        
        // 3. Default: scalping-enabled crypto from registry
        val defaults = AssetRegistry.getScalpingEnabled()
            .filter { it.status == AssetStatus.TRADING }
            .filter { it.assetType == AssetType.CRYPTO_SPOT }
            .take(10)
            .map { it.symbol }
        
        // 4. Fallback if registry empty
        return defaults.ifEmpty { listOf("BTC/USDT", "ETH/USDT") }
    }
    
    companion object {
        /** Config for crypto spot trading only */
        fun cryptoSpot(
            mode: TradingMode = TradingMode.SIGNAL_ONLY,
            maxPositions: Int = 5
        ) = TradingCoordinatorConfig(
            mode = mode,
            maxConcurrentPositions = maxPositions,
            assetFilter = AssetFilter.CRYPTO_SPOT
        )
        
        /** Config for conservative trading */
        fun conservative(
            mode: TradingMode = TradingMode.SIGNAL_ONLY
        ) = TradingCoordinatorConfig(
            mode = mode,
            maxConcurrentPositions = 3,
            defaultPositionSizePercent = 5.0,
            assetFilter = AssetFilter.CONSERVATIVE,
            hybridConfig = HybridModeConfig.CONSERVATIVE
        )
        
        /** Config for HYBRID mode with custom thresholds */
        fun hybrid(
            hybridConfig: HybridModeConfig = HybridModeConfig.MODERATE,
            maxPositions: Int = 5
        ) = TradingCoordinatorConfig(
            mode = TradingMode.HYBRID,
            maxConcurrentPositions = maxPositions,
            hybridConfig = hybridConfig
        )
        
        /** Config for crypto scalping */
        fun scalping(
            scalpMode: ScalpMode = ScalpMode.STANDARD,
            maxConcurrent: Int = 3
        ) = TradingCoordinatorConfig(
            mode = TradingMode.SCALPING,
            maxConcurrentPositions = maxConcurrent,
            assetFilter = AssetFilter.SCALPING,
            scalpingConfig = ScalpingConfig.CRYPTO_DEFAULT.copy(
                mode = scalpMode,
                maxConcurrentScalps = maxConcurrent
            )
        )
        
        /** Config for paper trading (safe learning) */
        fun paperTrading(
            mode: TradingMode = TradingMode.SIGNAL_ONLY
        ) = TradingCoordinatorConfig(
            mode = mode,
            paperTradingMode = true,
            maxConcurrentPositions = 5
        )
    }
}

// ============================================================================
// EVENTS
// ============================================================================

sealed class CoordinatorEvent {
    data class ModeChanged(val newMode: TradingMode) : CoordinatorEvent()
    data class AnalysisStarted(val symbol: String) : CoordinatorEvent()
    data class AnalysisComplete(val symbol: String, val consensus: BoardConsensus) : CoordinatorEvent()
    data class SignalGenerated(val signal: PendingTradeSignal) : CoordinatorEvent()
    data class ConfirmationRequired(val signal: PendingTradeSignal, val reason: String) : CoordinatorEvent()
    data class TradeExecuted(val trade: ExecutedTrade) : CoordinatorEvent()
    data class TradeRejected(val reason: String, val symbol: String) : CoordinatorEvent()
    data class PositionUpdated(val position: ManagedPosition) : CoordinatorEvent()
    data class PositionClosed(val symbol: String, val pnl: Double, val pnlPercent: Double) : CoordinatorEvent()
    data class StopAdjusted(val symbol: String, val newStop: Double, val level: Int) : CoordinatorEvent()
    data class RiskAlert(val message: String, val severity: AlertSeverity) : CoordinatorEvent()
    data class Error(val message: String, val exception: Throwable? = null) : CoordinatorEvent()
    object TradingStarted : CoordinatorEvent()
    object TradingStopped : CoordinatorEvent()
    data class RiskLimitHit(val reason: String) : CoordinatorEvent()
    data class EmergencyStopActivated(val reason: String, val positionsClosed: Int) : CoordinatorEvent()
    object EmergencyStopReset : CoordinatorEvent()
    // V5.17.0: ML health monitoring events
    data class MLHealthUpdate(val status: HealthStatus, val summary: String) : CoordinatorEvent()
    // V5.17.0: Disagreement detection events
    data class DisagreementUpdate(val level: String, val score: Double, val explanation: String) : CoordinatorEvent()
}

enum class AlertSeverity { INFO, WARNING, CRITICAL }

// ============================================================================
// DATA CLASSES
// ============================================================================

data class PendingTradeSignal(
    val id: String,
    val symbol: String,
    val direction: TradeDirection,
    val suggestedEntry: Double,
    val suggestedStop: Double,
    val suggestedTarget: Double,
    val positionSizePercent: Double,
    val riskPercent: Double,
    val estimatedValue: Double,
    val confidence: Double,
    val boardConsensus: BoardConsensus,
    val timestamp: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 300_000, // 5 min expiry
    var status: SignalStatus = SignalStatus.PENDING,
    val requiresConfirmationReason: String? = null
)

// TradeDirection defined in StahlStairStop.kt
enum class SignalStatus { PENDING, CONFIRMED, REJECTED, EXPIRED, EXECUTED }

data class ExecutedTrade(
    val id: String,
    val symbol: String,
    val direction: TradeDirection,
    val entryPrice: Double,
    val quantity: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val orderId: String,
    val timestamp: Long,
    val fromSignalId: String?,
    val wasAutonomous: Boolean,
    // XAI Compliance - Full audit trail
    val xaiSessionId: String? = null,
    val xaiDecision: String? = null,
    val xaiConfidence: Double? = null,
    val xaiBoardAgreement: Int? = null,
    val xaiSynthesis: String? = null,
    val xaiAuditJson: String? = null
)

data class ManagedPosition(
    val symbol: String,
    val direction: TradeDirection,
    val entryPrice: Double,
    val currentPrice: Double,
    val quantity: Double,
    var currentStop: Double,
    var currentTarget: Double,
    var stahlLevel: Int = 0,
    val unrealizedPnL: Double,
    val unrealizedPnLPercent: Double,
    val entryTime: Long,
    val orderId: String
)

data class CoordinatorState(
    val isRunning: Boolean = false,
    val mode: TradingMode = TradingMode.SIGNAL_ONLY,
    val phase: CoordinatorPhase = CoordinatorPhase.IDLE,
    val activePositions: Map<String, ManagedPosition> = emptyMap(),
    val pendingSignals: List<PendingTradeSignal> = emptyList(),
    val lastAnalysisTime: Map<String, Long> = emptyMap(),
    val tradesToday: Int = 0,
    val tradesThisHour: Int = 0,
    val pnlToday: Double = 0.0,
    val emergencyStopActive: Boolean = false,
    // V5.17.0: ML health status
    val mlHealthStatus: HealthStatus = HealthStatus.HEALTHY,
    val mlHealthSummary: String = "Initialising",
    val mlRollbackCount: Int = 0,
    // V5.17.0: Ensemble disagreement status
    val disagreementLevel: String = "STRONG_AGREEMENT",
    val disagreementScore: Double = 0.0,
    val positionSizeMultiplier: Double = 1.0,
    // V5.17.0: Board regime-aware position sizing
    val boardPositionMultiplier: Double = 1.0,      // From board consensus (confidence + agreement)
    val effectivePositionMultiplier: Double = 1.0    // boardPositionMultiplier × positionSizeMultiplier (disagreement)
)

enum class CoordinatorPhase { 
    IDLE, 
    ANALYZING, 
    WAITING_CONFIRMATION, 
    EXECUTING, 
    EMERGENCY_STOPPED,
    ERROR 
}

data class CoordinatorStatus(
    val isRunning: Boolean,
    val mode: TradingMode,
    val phase: CoordinatorPhase,
    val tradesThisHour: Int,
    val tradesThisDay: Int,
    val watchlistSize: Int,
    val symbolsWithData: Int,
    val pendingConfirmations: Int,
    val openPositions: Int,
    val emergencyStopActive: Boolean,
    val totalUnrealizedPnL: Double,
    // V5.17.0: ML health
    val mlHealthStatus: HealthStatus = HealthStatus.HEALTHY,
    val mlRollbackCount: Int = 0,
    // V5.17.0: Disagreement detection
    val disagreementLevel: String = "STRONG_AGREEMENT",
    val positionSizeMultiplier: Double = 1.0
)

// ============================================================================
// PRICE DATA BUFFER
// ============================================================================

data class PriceBuffer(
    val symbol: String,
    val opens: MutableList<Double> = mutableListOf(),
    val highs: MutableList<Double> = mutableListOf(),
    val lows: MutableList<Double> = mutableListOf(),
    val closes: MutableList<Double> = mutableListOf(),
    val volumes: MutableList<Double> = mutableListOf(),
    var lastUpdate: Long = 0
) {
    companion object {
        const val MAX_CANDLES = 500  // Keep 500 candles for analysis
    }
    
    fun addCandle(open: Double, high: Double, low: Double, close: Double, volume: Double) {
        opens.add(open)
        highs.add(high)
        lows.add(low)
        closes.add(close)
        volumes.add(volume)
        lastUpdate = System.currentTimeMillis()
        
        // Trim to max size
        while (opens.size > MAX_CANDLES) {
            opens.removeAt(0)
            highs.removeAt(0)
            lows.removeAt(0)
            closes.removeAt(0)
            volumes.removeAt(0)
        }
    }
    
    fun updateCurrentCandle(price: Double, volume: Double) {
        if (closes.isNotEmpty()) {
            val lastIdx = closes.size - 1
            closes[lastIdx] = price
            if (price > highs[lastIdx]) highs[lastIdx] = price
            if (price < lows[lastIdx]) lows[lastIdx] = price
            volumes[lastIdx] = volumes[lastIdx] + volume
            lastUpdate = System.currentTimeMillis()
        }
    }
    
    fun hasEnoughData(): Boolean = closes.size >= 50
    
    fun toMarketContext(): MarketContext {
        return MarketContext(
            symbol = symbol,
            currentPrice = closes.lastOrNull() ?: 0.0,
            opens = opens.toList(),
            highs = highs.toList(),
            lows = lows.toList(),
            closes = closes.toList(),
            volumes = volumes.toList()
        )
    }
}

// ============================================================================
// TRADING COORDINATOR - MERGED IMPLEMENTATION
// ============================================================================

class TradingCoordinator(
    private val orderExecutor: OrderExecutor,
    private val riskManager: RiskManager,
    private val positionManager: PositionManager,
    private var config: TradingCoordinatorConfig = TradingCoordinatorConfig(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    // XAI Compliance: Repository for persisting AI Board decisions
    private val boardDecisionRepository: BoardDecisionRepository? = null,
    // Gamification: Track user achievements
    private val gamification: UserProfileManager? = null,
    // Database: Record trades
    private val tradeDao: TradeDao? = null,
    // V5.17.0: Sentiment Engine — provides socialVolume + newsImpact to AI Board
    private val sentimentEngine: SentimentEngine? = null
) {
    
    companion object {
        private const val TAG = "TradingCoordinator"
    }
    
    // AI Systems
    // V5.17.0: DQN Learning Engine - teaches board members what actually works
    // V5.17.0: stateSize upgraded from 6 → 30 (uses v5.5.83 enhanced 30-feature vector)
    // 19 base features + 10 cross-feature interactions + 1 position = 30
    private val dqn = DQNTrader(
        stateSize = 30,          // V5.17.0 EnhancedFeatureVector (was 6 prior to v5.5.89)
        actionSize = 5,          // 5 DQN-eligible actions (excludes CLOSE_ALL emergency action)
        learningRate = 0.001,
        discountFactor = 0.95,
        explorationRate = 0.20   // 20% exploration
    )
    
    // V5.17.0: Health monitor for DQN — tracks gradient/weight/loss health
    // V5.17.0: Dimensions match the DQN's upgraded network (30→64→32→5)
    private val healthMonitor = DQNHealthMonitor(
        inputSize = 30,
        hidden1Size = 64,
        hidden2Size = 32,
        outputSize = 5
    )
    
    // V5.17.0/88: Ensemble disagreement detector — reduces position size when models conflict
    private val disagreementDetector = EnsembleDisagreementDetector()
    
    // V5.17.0: AI Board now receives DQN to blend learned patterns with heuristics
    private val aiBoard = AIBoardOrchestrator(dqn)
    private val signalGenerator = SignalGenerator()
    private val stahlStop = StahlStairStop()
    
    // V5.17.0: Dynamic Board Weights by Market Regime
    private val regimeDetector = MarketRegimeDetector()
    private var currentRegime: MarketRegime = MarketRegime.SIDEWAYS_RANGING
    
    // State
    private val isRunning = AtomicBoolean(false)
    private val isEmergencyStopped = AtomicBoolean(false)
    private var analysisJob: Job? = null
    private var positionMonitorJob: Job? = null
    private var rateLimitResetJob: Job? = null
    private var memoryCleanupJob: Job? = null  // BUILD #104: Periodic memory cleanup
    
    private val priceBuffers = ConcurrentHashMap<String, PriceBuffer>()
    private val pendingSignals = ConcurrentHashMap<String, PendingTradeSignal>()
    private val managedPositions = ConcurrentHashMap<String, ManagedPosition>()
    private val lastTradeTime = ConcurrentHashMap<String, Long>()
    
    // V5.17.0: Cross-exchange price map for arb/hedge strategies
    // Key: symbol, Value: Map<exchangeId, PriceTick>
    // Enables spread detection across exchanges (e.g. BTC/USDT on Binance vs Kraken)
    private val crossExchangePrices = ConcurrentHashMap<String, ConcurrentHashMap<String, PriceTick>>()
    
    // V5.17.0: Order book snapshots per symbol per exchange for real bid/ask depth
    private val crossExchangeOrderBooks = ConcurrentHashMap<String, ConcurrentHashMap<String, OrderBook>>()
    
    // Rate limiting
    private var tradesThisHour = 0
    private var tradesThisDay = 0
    private var lastHourReset = System.currentTimeMillis()
    
    // V5.17.0: HYBRID mode rate limiting — separate from global rate limits
    // Tracks auto-executed trades only (user-confirmed trades don't count against this)
    private var hybridAutoTradesThisHour = 0
    private var lastHybridHourReset = System.currentTimeMillis()
    private var lastDayReset = System.currentTimeMillis()
    
    // Flows
    private val _events = MutableSharedFlow<CoordinatorEvent>(replay = 0, extraBufferCapacity = 64)
    val events: SharedFlow<CoordinatorEvent> = _events.asSharedFlow()
    
    private val _state = MutableStateFlow(CoordinatorState())
    val state: StateFlow<CoordinatorState> = _state.asStateFlow()
    
    // ========================================================================
    // PUBLIC API - LIFECYCLE
    // ========================================================================
    
    /**
     * Start the trading coordinator
     */
    fun start() {
        if (isRunning.getAndSet(true)) {
            Log.w(TAG, "⚠️ Coordinator already running - ignoring duplicate start()")
            return // Already running
        }
        
        if (isEmergencyStopped.get()) {
            emitEvent(CoordinatorEvent.Error("Cannot start: Emergency stop is active. Call resetEmergencyStop() first."))
            isRunning.set(false)
            return
        }
        
        Log.i(TAG, "🚀 TRADING COORDINATOR STARTED - Mode: ${config.mode}, Analysis interval: ${config.analysisIntervalMs}ms")
        emitEvent(CoordinatorEvent.TradingStarted)
        
        // V5.17.0: Checkpoint DQN weights at startup as baseline rollback point
        try {
            healthMonitor.forceCheckpoint(dqn.getPolicyNetwork())
        } catch (e: Exception) {
            emitEvent(CoordinatorEvent.Error("Failed to checkpoint DQN weights: ${e.message}"))
        }
        
        // Start analysis loop
        analysisJob = scope.launch {
            analysisLoop()
        }
        
        // Start position monitor
        positionMonitorJob = scope.launch {
            positionMonitorLoop()
        }
        
        // Start rate limit reset loop
        rateLimitResetJob = scope.launch {
            rateLimitResetLoop()
        }
        
        // BUILD #104: Start periodic memory cleanup (every 5 minutes)
        // Keeps WebSockets alive but prevents unbounded memory growth
        memoryCleanupJob = scope.launch {
            while (isActive) {
                delay(5 * 60 * 1000L) // 5 minutes
                cleanupOldData()
            }
        }
        
        // Subscribe to position manager events
        scope.launch {
            positionManager.positionEvents.collect { event ->
                handlePositionEvent(event)
            }
        }
        
        updateState { it.copy(isRunning = true, mode = config.mode, phase = CoordinatorPhase.IDLE) }
    }
    
    /**
     * Stop the trading coordinator (graceful)
     */
    fun stop() {
        if (!isRunning.getAndSet(false)) {
            return // Already stopped
        }
        
        analysisJob?.cancel()
        positionMonitorJob?.cancel()
        rateLimitResetJob?.cancel()
        memoryCleanupJob?.cancel()  // BUILD #104: Cancel cleanup job
        
        emitEvent(CoordinatorEvent.TradingStopped)
        updateState { it.copy(isRunning = false, phase = CoordinatorPhase.IDLE) }
    }
    
    /**
     * EMERGENCY STOP - Immediately halt all trading and close all positions
     * This is the user-activated kill switch.
     * 
     * @param reason Why the emergency stop was activated
     * @return Number of positions that were closed
     */
    suspend fun emergencyStop(reason: String = "Manual emergency stop"): Int {
        isEmergencyStopped.set(true)
        
        // Stop all trading activity
        stop()
        
        // Close all open positions at market
        val closedCount = closeAllPositions()
        
        // Cancel all pending orders
        pendingSignals.clear()
        
        // Activate risk manager kill switch
        riskManager.activateKillSwitch(reason)
        
        updateState { it.copy(
            emergencyStopActive = true, 
            phase = CoordinatorPhase.EMERGENCY_STOPPED,
            pendingSignals = emptyList(),
            activePositions = emptyMap()
        )}
        
        emitEvent(CoordinatorEvent.EmergencyStopActivated(reason, closedCount))
        emitEvent(CoordinatorEvent.RiskAlert(
            "🚨 EMERGENCY STOP ACTIVATED: $reason - $closedCount positions closed",
            AlertSeverity.CRITICAL
        ))
        
        return closedCount
    }
    
    /**
     * Reset emergency stop - allows trading to resume
     * Requires explicit user action.
     */
    fun resetEmergencyStop() {
        if (!isEmergencyStopped.get()) return
        
        isEmergencyStopped.set(false)
        riskManager.resetKillSwitch()
        
        updateState { it.copy(emergencyStopActive = false, phase = CoordinatorPhase.IDLE) }
        emitEvent(CoordinatorEvent.EmergencyStopReset)
        emitEvent(CoordinatorEvent.RiskAlert(
            "Emergency stop reset - trading can resume",
            AlertSeverity.INFO
        ))
    }
    
    /**
     * Close all open positions at market price
     * Used by emergency stop and can be called independently.
     * 
     * @return Number of positions successfully closed
     */
    suspend fun closeAllPositions(): Int {
        var closedCount = 0
        val positions = managedPositions.values.toList()
        
        for (position in positions) {
            try {
                val side = if (position.direction == TradeDirection.LONG) TradeSide.SELL else TradeSide.BUY
                val result = orderExecutor.executeMarketOrder(position.symbol, side, position.quantity)
                
                when (result) {
                    is OrderExecutionResult.Success, is OrderExecutionResult.PartialFill -> {
                        val exitPrice = (result as? OrderExecutionResult.Success)?.order?.executedPrice
                            ?: (result as? OrderExecutionResult.PartialFill)?.order?.executedPrice
                            ?: position.currentPrice
                        
                        val pnl = calculatePnL(position, exitPrice)
                        val pnlPercent = calculatePnLPercent(position, exitPrice)
                        
                        // Record closed trade
                        recordClosedTrade(position, exitPrice, pnl, pnlPercent, "Emergency Close")
                        
                        managedPositions.remove(position.symbol)
                        closedCount++
                        
                        emitEvent(CoordinatorEvent.PositionClosed(position.symbol, pnl, pnlPercent))
                    }
                    else -> {
                        emitEvent(CoordinatorEvent.Error("Failed to close ${position.symbol}: ${result}"))
                    }
                }
            } catch (e: Exception) {
                emitEvent(CoordinatorEvent.Error("Error closing ${position.symbol}: ${e.message}", e))
            }
        }
        
        updatePositionsState()
        return closedCount
    }
    
    // ========================================================================
    // PUBLIC API - MODE & CONFIG
    // ========================================================================
    
    /**
     * Switch operating mode
     */
    fun setMode(mode: TradingMode) {
        config = config.copy(mode = mode)
        emitEvent(CoordinatorEvent.ModeChanged(mode))
        updateState { it.copy(mode = mode) }
    }
    
    /**
     * Update HYBRID mode configuration
     */
    fun updateHybridConfig(hybridConfig: HybridModeConfig) {
        config = config.copy(hybridConfig = hybridConfig)
    }
    
    /**
     * Update full configuration
     */
    fun updateConfig(newConfig: TradingCoordinatorConfig) {
        config = newConfig
        updateState { it.copy(mode = newConfig.mode) }
    }
    
    /**
     * Update watchlist dynamically (from Alpha Scanner or other sources)
     * This allows external strategy engines to feed qualified assets.
     */
    fun updateWatchlist(symbols: List<String>) {
        // Update config with new symbol list
        config = config.copy(enabledSymbols = symbols)
        
        // Initialize price buffers for new symbols
        symbols.forEach { symbol ->
            if (!priceBuffers.containsKey(symbol)) {
                priceBuffers[symbol] = PriceBuffer(symbol)
            }
        }
        
        emitEvent(CoordinatorEvent.RiskAlert(
            "Watchlist updated: ${symbols.size} symbols",
            AlertSeverity.INFO
        ))
    }
    
    /**
     * Get current watchlist symbols
     */
    fun getWatchlist(): List<String> {
        return config.resolveSymbols()
    }
    
    // ========================================================================
    // PUBLIC API - PRICE DATA
    // ========================================================================
    
    /**
     * Feed OHLCV candle data into the coordinator
     */
    fun onPriceUpdate(symbol: String, open: Double, high: Double, low: Double, close: Double, volume: Double) {
        val buffer = priceBuffers.getOrPut(symbol) { PriceBuffer(symbol) }
        buffer.addCandle(open, high, low, close, volume)
        
        // Update managed positions with new price
        managedPositions[symbol]?.let { position ->
            val updatedPosition = updatePositionPrice(position, close)
            managedPositions[symbol] = updatedPosition
            emitEvent(CoordinatorEvent.PositionUpdated(updatedPosition))
        }
    }
    
    /**
     * Feed real-time tick data (updates current candle)
     */
    fun onPriceTick(symbol: String, price: Double, volume: Double) {
        onPriceTick(symbol, price, volume, exchange = null)
    }
    
    /**
     * V5.17.0: Exchange-aware price tick handler.
     * Updates both the standard price buffer (for AI Board analysis) AND the
     * cross-exchange price map (for arb/hedge spread detection).
     * 
     * When exchange is provided, the cross-exchange map is updated, enabling
     * FundingArbEngine, PairsTradingEngine, and SmartOrderRouter to detect
     * spread opportunities across venues.
     */
    fun onPriceTick(symbol: String, price: Double, volume: Double, exchange: String?) {
        val buffer = priceBuffers[symbol] ?: return
        buffer.updateCurrentCandle(price, volume)
        
        // Update managed positions with new price
        managedPositions[symbol]?.let { position ->
            val updatedPosition = updatePositionPrice(position, price)
            managedPositions[symbol] = updatedPosition
        }
        
        // V5.17.0: Update cross-exchange price map for arb/hedge strategies
        if (exchange != null) {
            val exchangeMap = crossExchangePrices.getOrPut(symbol) { ConcurrentHashMap() }
            exchangeMap[exchange] = PriceTick(
                symbol = symbol,
                bid = price,  // Approx — full bid/ask from order book
                ask = price,
                last = price,
                volume = volume,
                timestamp = System.currentTimeMillis(),
                exchange = exchange
            )
        }
    }
    
    /**
     * V5.17.0: Order book update handler.
     * Receives full order book snapshots from WebSocket subscription and:
     * 1. Stores in crossExchangeOrderBooks for depth-aware strategies
     * 2. Updates crossExchangePrices with REAL best-bid/best-ask
     *    (replacing the bid=ask=last approximation from ticker polls)
     * 
     * This is the primary source of truth for arb spread calculation.
     */
    fun onOrderBookUpdate(book: OrderBook) {
        if (book.exchange.isEmpty()) return
        
        // Store full order book snapshot
        val bookMap = crossExchangeOrderBooks.getOrPut(book.symbol) { ConcurrentHashMap() }
        bookMap[book.exchange] = book
        
        // Update crossExchangePrices with real bid/ask from order book
        val exchangeMap = crossExchangePrices.getOrPut(book.symbol) { ConcurrentHashMap() }
        val existingTick = exchangeMap[book.exchange]
        
        exchangeMap[book.exchange] = PriceTick(
            symbol = book.symbol,
            bid = book.bestBid,
            ask = book.bestAsk,
            last = existingTick?.last ?: book.midPrice,  // Keep last from ticker if available
            volume = existingTick?.volume ?: 0.0,
            timestamp = book.timestamp,
            exchange = book.exchange
        )
    }
    
    /**
     * V5.17.0: Get the latest order book for a symbol on a specific exchange.
     * Returns null if no order book data available for that exchange.
     */
    fun getOrderBook(symbol: String, exchangeId: String): OrderBook? {
        return crossExchangeOrderBooks[symbol]?.get(exchangeId)
    }
    
    /**
     * V5.17.0: Get all order books for a symbol across exchanges.
     * Returns map of exchangeId → OrderBook for depth-aware strategies.
     */
    fun getCrossExchangeOrderBooks(symbol: String): Map<String, OrderBook> {
        return crossExchangeOrderBooks[symbol]?.toMap() ?: emptyMap()
    }
    
    /**
     * V5.17.0: Get cross-exchange prices for a symbol.
     * Returns a map of exchangeId → latest PriceTick.
     * Used by arb/hedge strategies to detect spread opportunities.
     *
     * V5.17.0: PriceTick bid/ask now populated from real order book data
     * when order book feed is active, instead of approximating from last price.
     */
    fun getCrossExchangePrices(symbol: String): Map<String, PriceTick> {
        return crossExchangePrices[symbol]?.toMap() ?: emptyMap()
    }
    
    /**
     * V5.17.0: Calculate the current arb spread for a symbol across exchanges.
     * Returns the percentage spread between lowest ask and highest bid across venues.
     * Positive spread = potential arb opportunity.
     *
     * V5.17.0: Now uses real best-bid/best-ask from order book depth data
     * instead of approximating bid=ask=last from ticker polls.
     */
    fun getArbSpread(symbol: String): Double {
        val prices = crossExchangePrices[symbol] ?: return 0.0
        if (prices.size < 2) return 0.0
        
        val ticks = prices.values.toList()
        val lowestAsk = ticks.minOf { it.ask }
        val highestBid = ticks.maxOf { it.bid }
        
        return if (lowestAsk > 0) {
            ((highestBid - lowestAsk) / lowestAsk) * 100.0
        } else 0.0
    }
    
    /**
     * Load historical data for a symbol
     */
    fun loadHistoricalData(symbol: String, bars: List<OHLCVBar>) {
        val buffer = priceBuffers.getOrPut(symbol) { PriceBuffer(symbol) }
        for (bar in bars) {
            buffer.addCandle(bar.open, bar.high, bar.low, bar.close, bar.volume)
        }
    }
    
    // ========================================================================
    // PUBLIC API - SIGNALS & CONFIRMATION
    // ========================================================================
    
    /**
     * Confirm a pending signal (for SIGNAL_ONLY and HYBRID modes)
     */
    suspend fun confirmSignal(signalId: String): Result<ExecutedTrade> {
        val signal = pendingSignals[signalId]
            ?: return Result.failure(IllegalArgumentException("Signal not found: $signalId"))
        
        if (signal.status != SignalStatus.PENDING) {
            return Result.failure(IllegalStateException("Signal is not pending: ${signal.status}"))
        }
        
        signal.status = SignalStatus.CONFIRMED
        return executeTrade(signal)
    }
    
    /**
     * Reject a pending signal
     */
    fun rejectSignal(signalId: String) {
        pendingSignals[signalId]?.let { signal ->
            signal.status = SignalStatus.REJECTED
            pendingSignals.remove(signalId)
            emitEvent(CoordinatorEvent.TradeRejected("User rejected", signal.symbol))
            updatePendingSignalsState()
        }
    }
    
    /**
     * Get all pending signals awaiting confirmation
     */
    fun getPendingSignals(): List<PendingTradeSignal> {
        return pendingSignals.values
            .filter { it.status == SignalStatus.PENDING && it.expiresAt > System.currentTimeMillis() }
            .sortedByDescending { it.confidence }
    }
    
    /**
     * Get all managed positions
     */
    fun getManagedPositions(): List<ManagedPosition> {
        return managedPositions.values.toList()
    }
    
    /**
     * Manually close a specific position
     */
    suspend fun closePosition(symbol: String): Result<Unit> {
        val position = managedPositions[symbol]
            ?: return Result.failure(IllegalArgumentException("No position for: $symbol"))
        
        return try {
            val side = if (position.direction == TradeDirection.LONG) TradeSide.SELL else TradeSide.BUY
            val result = orderExecutor.executeMarketOrder(symbol, side, position.quantity)
            
            when (result) {
                is OrderExecutionResult.Success -> {
                    val exitPrice = result.order.executedPrice
                    val pnl = calculatePnL(position, exitPrice)
                    val pnlPercent = calculatePnLPercent(position, exitPrice)
                    
                    recordClosedTrade(position, exitPrice, pnl, pnlPercent, "Manual Close")
                    managedPositions.remove(symbol)
                    updatePositionsState()
                    
                    emitEvent(CoordinatorEvent.PositionClosed(symbol, pnl, pnlPercent))
                    Result.success(Unit)
                }
                is OrderExecutionResult.Rejected -> Result.failure(Exception(result.reason))
                is OrderExecutionResult.Error -> Result.failure(result.exception)
                else -> Result.failure(Exception("Unexpected result"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get current coordinator status
     */
    fun getStatus(): CoordinatorStatus {
        val totalPnL = managedPositions.values.sumOf { it.unrealizedPnL }
        val currentState = _state.value
        return CoordinatorStatus(
            isRunning = isRunning.get(),
            mode = config.mode,
            phase = currentState.phase,
            tradesThisHour = tradesThisHour,
            tradesThisDay = tradesThisDay,
            watchlistSize = config.resolveSymbols().size,
            symbolsWithData = priceBuffers.count { it.value.hasEnoughData() },
            pendingConfirmations = pendingSignals.count { it.value.status == SignalStatus.PENDING },
            openPositions = managedPositions.size,
            emergencyStopActive = isEmergencyStopped.get(),
            totalUnrealizedPnL = totalPnL,
            mlHealthStatus = currentState.mlHealthStatus,
            mlRollbackCount = currentState.mlRollbackCount,
            disagreementLevel = currentState.disagreementLevel,
            positionSizeMultiplier = currentState.positionSizeMultiplier
        )
    }
    
    /**
     * V5.17.0: Get current ML health report for UI display.
     * Returns null if no training steps have been recorded yet.
     */
    fun getMLHealthReport(): HealthReport? = healthMonitor.getHealthReport()
    
    /**
     * Shutdown and cleanup
     */
    fun shutdown() {
        stop()
        scope.cancel()
    }
    
    // ========================================================================
    // ANALYSIS LOOP
    // ========================================================================
    
    private suspend fun analysisLoop() {
        while (isRunning.get() && !isEmergencyStopped.get()) {
            try {
                updateState { it.copy(phase = CoordinatorPhase.ANALYZING) }
                
                // Get active symbols from config
                val activeSymbols = config.resolveSymbols()
                
                for (symbol in activeSymbols) {
                    if (!isRunning.get() || isEmergencyStopped.get()) break
                    
                    // Check cooldown
                    val lastTrade = lastTradeTime[symbol] ?: 0
                    if (System.currentTimeMillis() - lastTrade < config.cooldownAfterTradeMs) {
                        continue
                    }
                    
                    // BUILD #111 FIX #3: Check if we have enough data (with diagnostics)
                    val buffer = priceBuffers[symbol]
                    if (buffer == null) {
                        Log.w(TAG, "⚠️ BUILD #111: No price buffer for $symbol - skipping analysis (feed not connected?)")
                        continue
                    }
                    if (!buffer.hasEnoughData()) {
                        Log.w(TAG, "⚠️ BUILD #111: Price buffer for $symbol has insufficient data (${buffer.closes.size} points, need ~20+) - waiting for more prices...")
                        continue
                    }
                    
                    // Run analysis
                    analyzeSymbol(symbol, buffer)
                }
                
                // Clean expired signals
                cleanExpiredSignals()
                
                updateState { 
                    it.copy(phase = if (pendingSignals.isNotEmpty()) 
                        CoordinatorPhase.WAITING_CONFIRMATION 
                    else 
                        CoordinatorPhase.IDLE
                    )
                }
                
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emitEvent(CoordinatorEvent.Error("Analysis error: ${e.message}", e))
                updateState { it.copy(phase = CoordinatorPhase.ERROR) }
            }
            
            delay(config.analysisIntervalMs)
        }
    }
    
    private suspend fun analyzeSymbol(symbol: String, buffer: PriceBuffer) {
        emitEvent(CoordinatorEvent.AnalysisStarted(symbol))
        
        // Build base MarketContext from OHLCV
        val baseContext = buffer.toMarketContext()
        
        // V5.17.0: Enrich with SentimentEngine data if available
        // Extracts the base asset symbol (e.g. "BTC" from "BTC/USDT")
        val context = sentimentEngine?.let { engine ->
            val asset = symbol.substringBefore("/").uppercase()
            val sentiment = engine.getSentiment(asset)
            if (sentiment != null) {
                // volume: normalize mention count to a 0-5 scale (1000 = average = 1.0)
                val normalizedVolume = (sentiment.volume / 1000.0).coerceIn(0.0, 5.0)
                baseContext.copy(
                    socialVolume = normalizedVolume,
                    newsImpactScore = sentiment.score  // Already -1.0 to +1.0
                )
            } else baseContext
        } ?: baseContext
        
        // V5.17.0: Update market regime BEFORE board convenes — this ensures
        // getBoardMemberWeight() uses the CURRENT regime, not stale SIDEWAYS_RANGING default.
        // Regime detection shapes which board members get the most influence.
        if (buffer.closes.size >= 100) {
            val volatilityHistory = computeVolatilityHistory(buffer)
            if (volatilityHistory.isNotEmpty()) {
                updateMarketRegime(
                    priceHistory = buffer.closes.toList(),
                    volumeHistory = buffer.volumes.toList(),
                    volatilityHistory = volatilityHistory
                )
            }
        }
        
        // V5.17.0: Build regime-aware weight overrides for the board.
        // getBoardMemberWeight() returns regime-specific weights that sum to 1.0.
        // These dynamically shift voting influence based on current market conditions.
        val regimeWeights = buildRegimeWeightOverrides()
        
        // Get AI Board consensus — now with regime-aware voting weights
        val consensus = aiBoard.conveneBoardroom(context, regimeWeights)
        
        // BUILD #113: MASSIVE DIAGNOSTIC LOGGING
        Log.i(TAG, "═══════════════════════════════════════════════════════════")
        Log.i(TAG, "🎯 BUILD #113: AI BOARD DECISION FOR $symbol")
        Log.i(TAG, "   Decision: ${consensus.finalDecision}")
        Log.i(TAG, "   Confidence: ${String.format("%.1f", consensus.confidence * 100)}%")
        Log.i(TAG, "   Unanimous: ${consensus.unanimousCount}/8 members")
        Log.i(TAG, "   Price: ${context.currentPrice}")
        Log.i(TAG, "   Paper Mode: ${config.paperTradingMode}")
        Log.i(TAG, "   Trading Mode: ${config.mode}")
        Log.i(TAG, "═══════════════════════════════════════════════════════════")
        
        emitEvent(CoordinatorEvent.AnalysisComplete(symbol, consensus))
        
        // V5.17.0: Run disagreement analysis on board opinions
        // Map 8 board members into 4 model categories for disagreement detection
        val opinionMap = consensus.opinions.associateBy { it.agentName }
        val trendSentiment = opinionMap["TrendFollower"]?.sentiment ?: 0.0
        val momentumSentiment = listOfNotNull(
            opinionMap["MeanReverter"]?.sentiment,
            opinionMap["MacroStrategist"]?.sentiment
        ).average().takeIf { !it.isNaN() } ?: 0.0
        val sentimentSentiment = opinionMap["SentimentAnalyst"]?.sentiment ?: 0.0
        val technicalSentiment = listOfNotNull(
            opinionMap["VolatilityTrader"]?.sentiment,
            opinionMap["PatternRecognizer"]?.sentiment,
            opinionMap["OnChainAnalyst"]?.sentiment
        ).average().takeIf { !it.isNaN() } ?: 0.0
        
        val disagreementAnalysis = disagreementDetector.analyzeFromBoard(
            trendFollower = trendSentiment,
            momentumTrader = momentumSentiment,
            sentimentAnalyst = sentimentSentiment,
            technicalAnalyst = technicalSentiment
        )
        disagreementDetector.trackDisagreement(disagreementAnalysis)
        
        // Update state with disagreement info and V5.17.0 board position sizing
        val boardMultiplier = consensus.recommendedPositionSize
        val disagreementMult = disagreementAnalysis.level.positionSizeMultiplier
        updateState { it.copy(
            disagreementLevel = disagreementAnalysis.level.name,
            disagreementScore = disagreementAnalysis.disagreementScore,
            positionSizeMultiplier = disagreementMult,
            boardPositionMultiplier = boardMultiplier,
            effectivePositionMultiplier = boardMultiplier * disagreementMult
        )}
        
        // Emit disagreement event (only on elevated levels to avoid noise)
        if (disagreementAnalysis.level != EnsembleDisagreementDetector.DisagreementLevel.STRONG_AGREEMENT) {
            emitEvent(CoordinatorEvent.DisagreementUpdate(
                level = disagreementAnalysis.level.name,
                score = disagreementAnalysis.disagreementScore,
                explanation = disagreementAnalysis.explanation
            ))
            
            if (disagreementAnalysis.level.ordinal >= EnsembleDisagreementDetector.DisagreementLevel.HIGH_DISAGREEMENT.ordinal) {
                emitEvent(CoordinatorEvent.RiskAlert(
                    "⚡ Board disagreement: ${disagreementAnalysis.level.description} — " +
                    "position size ×${String.format("%.0f", disagreementAnalysis.level.positionSizeMultiplier * 100)}%",
                    AlertSeverity.WARNING
                ))
            }
        }
        
        // BUILD #113: SKIP DISAGREEMENT GATE FOR PAPER TRADING (too conservative)
        // In paper mode, let ALL signals through to test the system
        if (!config.paperTradingMode) {
            // V5.17.0: Gate check — skip trade if confidence doesn't meet disagreement-adjusted threshold
            if (!disagreementDetector.shouldTakeTrade(consensus.confidence, disagreementAnalysis)) {
                emitEvent(CoordinatorEvent.TradeRejected(
                    "Confidence ${String.format("%.0f", consensus.confidence * 100)}% below " +
                    "disagreement threshold ${String.format("%.0f", disagreementAnalysis.level.minConfidenceRequired * 100)}%",
                    symbol
                ))
                // Still persist the decision for audit trail
                val (actionTaken, reasonForAction) = "REJECTED_DISAGREEMENT" to 
                    "Board disagreement ${disagreementAnalysis.level.name}: confidence too low"
                persistBoardDecision(symbol, context, consensus, actionTaken, reasonForAction)
                return
            }
        } else {
            Log.i(TAG, "🎯 BUILD #113: Paper mode - SKIPPING disagreement gate (confidence: ${String.format("%.0f", consensus.confidence * 100)}%)")
        }
        
        // Determine action for XAI record
        val (actionTaken, reasonForAction) = determineAction(symbol, consensus)
        
        // XAI Compliance: Persist board decision record
        persistBoardDecision(symbol, context, consensus, actionTaken, reasonForAction)
        
        // Check if signal is actionable
        if (!isSignalActionable(consensus)) {
            return
        }
        
        // Check risk limits
        if (!riskManager.isTradingAllowed.value) {
            emitEvent(CoordinatorEvent.RiskLimitHit("Trading halted by risk manager"))
            return
        }
        
        // Check rate limits
        if (tradesThisHour >= config.maxTradesPerHour) {
            emitEvent(CoordinatorEvent.TradeRejected("Hourly trade limit reached", symbol))
            return
        }
        if (tradesThisDay >= config.maxTradesPerDay) {
            emitEvent(CoordinatorEvent.TradeRejected("Daily trade limit reached", symbol))
            return
        }
        
        // Check position limits
        if (managedPositions.size >= config.maxConcurrentPositions && !managedPositions.containsKey(symbol)) {
            emitEvent(CoordinatorEvent.TradeRejected("Max positions reached", symbol))
            return
        }
        
        // Generate signal
        val signal = generateTradeSignal(symbol, consensus, buffer)
        
        // BUILD #113: Log before execution
        Log.i(TAG, "🚀 BUILD #113: EXECUTING TRADE in ${config.mode} mode")
        Log.i(TAG, "   Signal: ${signal.direction} $symbol @ ${signal.suggestedEntry}")
        Log.i(TAG, "   Confidence: ${String.format("%.1f", signal.confidence * 100)}%")
        
        // Act based on mode
        when (config.mode) {
            TradingMode.AUTONOMOUS -> {
                Log.i(TAG, "   → AUTONOMOUS: Auto-executing now!")
                executeTrade(signal)
            }
            TradingMode.SIGNAL_ONLY -> {
                storeSignalForConfirmation(signal, "SIGNAL_ONLY mode: User confirmation required")
            }
            TradingMode.HYBRID -> {
                handleHybridMode(signal)
            }
            TradingMode.SCALPING -> {
                // Scalping always auto-executes (speed is critical)
                executeTrade(signal)
            }
            else -> { /* no-op */ }
        }
    }
    
    // ========================================================================
    // HYBRID MODE LOGIC
    // ========================================================================
    
    private suspend fun handleHybridMode(signal: PendingTradeSignal) {
        val hybridConfig = config.hybridConfig
        
        // Check for full bypass
        if (hybridConfig.bypassAllConfirmation) {
            executeTrade(signal)
            return
        }
        
        // V5.17.0: Reset hybrid hourly counter if needed
        val now = System.currentTimeMillis()
        if (now - lastHybridHourReset >= 3_600_000) {
            hybridAutoTradesThisHour = 0
            lastHybridHourReset = now
        }
        
        // V5.17.0: LAYERED HYBRID DECISION FLOW
        // Layer 1: Hard confidence floor — below this, ALWAYS confirm regardless of value
        val confidence = signal.confidence
        if (confidence < hybridConfig.confidenceRequireConfirmationBelow) {
            storeSignalForConfirmation(signal, 
                "HYBRID: Confidence ${String.format("%.1f", confidence)}% below hard floor " +
                "${hybridConfig.confidenceRequireConfirmationBelow}% — confirmation required")
            return
        }
        
        // Layer 2: Hourly rate limit — prevent runaway auto-execution
        if (hybridAutoTradesThisHour >= hybridConfig.maxAutoTradesPerHour) {
            storeSignalForConfirmation(signal, 
                "HYBRID: Hourly auto-trade limit reached ($hybridAutoTradesThisHour/" +
                "${hybridConfig.maxAutoTradesPerHour}) — confirmation required for safety")
            return
        }
        
        // Layer 3: Absolute value gate — large trades always need confirmation (AU$)
        if (hybridConfig.useAbsoluteValueThreshold && 
            signal.estimatedValue > hybridConfig.absoluteValueThreshold) {
            storeSignalForConfirmation(signal, 
                "HYBRID: Trade value A\$${String.format("%.0f", signal.estimatedValue)} exceeds " +
                "A\$${String.format("%.0f", hybridConfig.absoluteValueThreshold)} threshold — confirmation required")
            return
        }
        
        // Layer 4: High confidence fast-path — above threshold, auto-execute
        if (confidence >= hybridConfig.confidenceAutoExecuteThreshold) {
            hybridAutoTradesThisHour++
            executeTrade(signal)
            return
        }
        
        // Layer 5: Fall through to existing position-size/risk threshold evaluation
        // (confidence is between floor and auto-execute — use value-based thresholds)
        val portfolioValue = positionManager.getPortfolioValue()
        val thresholdResults = mutableListOf<Pair<String, Boolean>>()
        
        // Position size threshold
        if (hybridConfig.usePositionSizeThreshold) {
            val positionPercent = (signal.estimatedValue / portfolioValue) * 100
            val passes = positionPercent <= hybridConfig.positionSizeThresholdPercent
            thresholdResults.add("Position size ${String.format("%.1f", positionPercent)}% vs threshold ${hybridConfig.positionSizeThresholdPercent}%" to passes)
        }
        
        // Risk amount threshold
        if (hybridConfig.useRiskAmountThreshold) {
            val riskPercent = signal.riskPercent
            val passes = riskPercent <= hybridConfig.riskAmountThresholdPercent
            thresholdResults.add("Risk ${String.format("%.1f", riskPercent)}% vs threshold ${hybridConfig.riskAmountThresholdPercent}%" to passes)
        }
        
        // Determine if auto-execute based on threshold logic
        val shouldAutoExecute = when {
            thresholdResults.isEmpty() -> false  // No thresholds enabled = always confirm
            hybridConfig.multipleThresholdLogic == ThresholdLogic.ALL -> thresholdResults.all { it.second }
            hybridConfig.multipleThresholdLogic == ThresholdLogic.ANY -> thresholdResults.any { it.second }
            else -> false
        }
        
        if (shouldAutoExecute) {
            hybridAutoTradesThisHour++
            executeTrade(signal)
        } else {
            // Build reason string
            val failedThresholds = thresholdResults.filter { !it.second }.map { it.first }
            val reason = "HYBRID: Confidence ${String.format("%.1f", confidence)}% (mid-range) " +
                "and exceeds threshold(s) — ${failedThresholds.joinToString("; ")}"
            storeSignalForConfirmation(signal, reason)
        }
    }
    
    private fun storeSignalForConfirmation(signal: PendingTradeSignal, reason: String) {
        val signalWithReason = signal.copy(requiresConfirmationReason = reason)
        pendingSignals[signal.id] = signalWithReason
        emitEvent(CoordinatorEvent.SignalGenerated(signalWithReason))
        emitEvent(CoordinatorEvent.ConfirmationRequired(signalWithReason, reason))
        updatePendingSignalsState()
    }
    
    // ========================================================================
    // TRADE EXECUTION
    // ========================================================================
    
    private suspend fun executeTrade(signal: PendingTradeSignal): Result<ExecutedTrade> {
        // BUILD #113: Log trade execution attempt
        Log.i(TAG, "💰 BUILD #113: executeTrade() called for ${signal.symbol}")
        Log.i(TAG, "   Direction: ${signal.direction}")
        Log.i(TAG, "   Entry Price: ${signal.suggestedEntry}")
        Log.i(TAG, "   Confidence: ${String.format("%.1f", signal.confidence * 100)}%")
        
        updateState { it.copy(phase = CoordinatorPhase.EXECUTING) }
        
        return try {
            // Final risk check
            val riskCheck = performRiskCheck(signal)
            if (riskCheck != null) {
                emitEvent(CoordinatorEvent.TradeRejected(riskCheck, signal.symbol))
                signal.status = SignalStatus.REJECTED
                updateState { it.copy(phase = CoordinatorPhase.IDLE) }
                return Result.failure(Exception(riskCheck))
            }
            
            // Calculate position size
            val portfolioValue = positionManager.getPortfolioValue()
            val positionValue = portfolioValue * (signal.positionSizePercent / 100.0)
            val quantity = positionValue / signal.suggestedEntry
            
            // Execute order through OrderExecutor
            // V5.19.0 BUILD #102 FIX: Remove paperTradingMode bypass.
            // Previously, paper trades called simulatePaperTrade() which created
            // fake orders without updating PaperTradingAdapter balances.
            // Now ALL trades route through orderExecutor, which correctly
            // delegates to PaperTradingAdapter when in paper mode.
            val side = if (signal.direction == TradeDirection.LONG) TradeSide.BUY else TradeSide.SELL
            
            val result = if (config.useStahlStops) {
                orderExecutor.executeWithStahlStop(
                    symbol = signal.symbol,
                    side = side,
                    quantity = quantity,
                    entryPrice = signal.suggestedEntry
                )
            } else {
                orderExecutor.executeMarketOrder(signal.symbol, side, quantity)
            }
            
            // BUILD #113: Log order execution result
            Log.i(TAG, "📊 BUILD #113: Order execution result: ${result.javaClass.simpleName}")
            
            when (result) {
                is OrderExecutionResult.Success -> {
                    Log.i(TAG, "   ✅ SUCCESS! Order ID: ${result.order.orderId}")
                    handleSuccessfulTrade(result, signal)
                }
                is OrderExecutionResult.PartialFill -> {
                    Log.i(TAG, "   ⚠️ PARTIAL FILL: ${result.order.filledQuantity}/${result.order.quantity}")
                    // Treat partial fill as success for now
                    handleSuccessfulTrade(
                        OrderExecutionResult.Success(result.order),
                        signal
                    )
                }
                is OrderExecutionResult.Rejected -> {
                    Log.e(TAG, "   ❌ REJECTED: ${result.reason}")
                    signal.status = SignalStatus.REJECTED
                    emitEvent(CoordinatorEvent.TradeRejected(result.reason, signal.symbol))
                    updateState { it.copy(phase = CoordinatorPhase.IDLE) }
                    Result.failure(Exception(result.reason))
                }
                is OrderExecutionResult.Error -> {
                    Log.e(TAG, "   ❌ ERROR: ${result.exception.message}")
                    emitEvent(CoordinatorEvent.Error("Trade execution failed", result.exception))
                    updateState { it.copy(phase = CoordinatorPhase.IDLE) }
                    Result.failure(result.exception)
                }
            }
        } catch (e: Exception) {
            emitEvent(CoordinatorEvent.Error("Trade execution error: ${e.message}", e))
            updateState { it.copy(phase = CoordinatorPhase.IDLE) }
            Result.failure(e)
        }
    }
    
    private suspend fun handleSuccessfulTrade(
        result: OrderExecutionResult.Success,
        signal: PendingTradeSignal
    ): Result<ExecutedTrade> {
        // Update rate limits
        tradesThisHour++
        tradesThisDay++
        
        // Create XAI audit JSON
        val xaiAudit = try {
            signal.boardConsensus.toAuditJson()
        } catch (e: Exception) {
            null
        }
        
        val executedTrade = ExecutedTrade(
            id = "TRD-${System.currentTimeMillis()}",
            symbol = signal.symbol,
            direction = signal.direction,
            entryPrice = result.order.executedPrice,
            quantity = result.order.executedQuantity,
            stopLoss = signal.suggestedStop,
            takeProfit = signal.suggestedTarget,
            orderId = result.order.orderId,
            timestamp = System.currentTimeMillis(),
            fromSignalId = signal.id,
            wasAutonomous = config.mode == TradingMode.AUTONOMOUS || config.mode == TradingMode.SCALPING,
            xaiSessionId = signal.boardConsensus.sessionId,
            xaiDecision = signal.boardConsensus.finalDecision.name,
            xaiConfidence = signal.boardConsensus.confidence,
            xaiBoardAgreement = signal.boardConsensus.unanimousCount,
            xaiSynthesis = signal.boardConsensus.synthesis,
            xaiAuditJson = xaiAudit
        )
        
        // Record trade to database
        recordTrade(result.order, signal)
        
        // Create managed position
        val managedPosition = ManagedPosition(
            symbol = signal.symbol,
            direction = signal.direction,
            entryPrice = result.order.executedPrice,
            currentPrice = result.order.executedPrice,
            quantity = result.order.executedQuantity,
            currentStop = signal.suggestedStop,
            currentTarget = signal.suggestedTarget,
            stahlLevel = 0,
            unrealizedPnL = 0.0,
            unrealizedPnLPercent = 0.0,
            entryTime = System.currentTimeMillis(),
            orderId = result.order.orderId
        )
        
        managedPositions[signal.symbol] = managedPosition
        lastTradeTime[signal.symbol] = System.currentTimeMillis()
        signal.status = SignalStatus.EXECUTED
        pendingSignals.remove(signal.id)
        
        // Update gamification
        // TODO: gamification.onTradeOpened - wire to GamificationCoordinator
        
        emitEvent(CoordinatorEvent.TradeExecuted(executedTrade))
        updatePositionsState()
        updatePendingSignalsState()
        updateState { it.copy(
            phase = CoordinatorPhase.IDLE,
            tradesToday = tradesThisDay,
            tradesThisHour = tradesThisHour
        )}
        
        return Result.success(executedTrade)
    }
    
    // V5.19.0 BUILD #102: simulatePaperTrade() removed.
    // This method was bypassing the PaperTradingAdapter, causing balances
    // to never update. All trades now route through orderExecutor.
    
    // ========================================================================
    // POSITION MONITORING
    // ========================================================================
    
    private suspend fun positionMonitorLoop() {
        while (isRunning.get() && !isEmergencyStopped.get()) {
            try {
                for ((symbol, position) in managedPositions) {
                    if (!isRunning.get() || isEmergencyStopped.get()) break
                    
                    // Check for stop loss hit
                    if (isStopHit(position)) {
                        closePositionOnStop(symbol, position, "STAHL Stop Hit")
                        continue
                    }
                    
                    // Check for take profit hit
                    if (isTakeProfitHit(position)) {
                        closePositionOnStop(symbol, position, "Take Profit Hit")
                        continue
                    }
                    
                    // Check STAHL stair step adjustment
                    if (config.useStahlStops) {
                        checkStahlAdjustment(position)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emitEvent(CoordinatorEvent.Error("Position monitor error: ${e.message}", e))
            }
            
            delay(1000) // Check every second
        }
    }
    
    private suspend fun closePositionOnStop(symbol: String, position: ManagedPosition, reason: String) {
        val side = if (position.direction == TradeDirection.LONG) TradeSide.SELL else TradeSide.BUY
        
        val result = orderExecutor.executeMarketOrder(symbol, side, position.quantity)
        
        when (result) {
            is OrderExecutionResult.Success, is OrderExecutionResult.PartialFill -> {
                val exitPrice = (result as? OrderExecutionResult.Success)?.order?.executedPrice
                    ?: (result as? OrderExecutionResult.PartialFill)?.order?.executedPrice
                    ?: position.currentPrice
                
                val pnl = calculatePnL(position, exitPrice)
                val pnlPercent = calculatePnLPercent(position, exitPrice)
                
                // V5.17.0: Train DQN on trade outcome
                // DQN learns from actual profit/loss to improve future predictions
                // V5.17.0: Now monitored by DQNHealthMonitor with auto-rollback
                try {
                    // Convert PnL to reward (-1 to +1 scale)
                    val reward = when {
                        pnlPercent > 5.0 -> 1.0      // Excellent trade
                        pnlPercent > 2.0 -> 0.5      // Good trade
                        pnlPercent > 0.0 -> 0.2      // Small win
                        pnlPercent > -2.0 -> -0.2    // Small loss
                        pnlPercent > -5.0 -> -0.5    // Bad trade
                        else -> -1.0                 // Terrible trade
                    }
                    
                    // Record reward for learning
                    dqn.recordReward(reward)
                    
                    // V5.17.0: Train with metrics and monitor health
                    val tdError = dqn.replayWithMetrics()
                    val report = healthMonitor.recordTrainingStep(
                        network = dqn.getPolicyNetwork(),
                        tdError = tdError,
                        reward = reward,
                        wasWin = pnl > 0
                    )
                    
                    // Update state with ML health
                    updateState { it.copy(
                        mlHealthStatus = report.status,
                        mlHealthSummary = report.summary(),
                        mlRollbackCount = report.rollbackCount
                    )}
                    
                    // Emit health events for UI
                    when (report.status) {
                        HealthStatus.WARNING -> {
                            emitEvent(CoordinatorEvent.MLHealthUpdate(
                                report.status, report.summary()
                            ))
                            emitEvent(CoordinatorEvent.RiskAlert(
                                "⚠️ ML Health: ${report.issues.firstOrNull()?.description ?: "Degraded"}",
                                AlertSeverity.WARNING
                            ))
                        }
                        HealthStatus.CRITICAL -> {
                            emitEvent(CoordinatorEvent.MLHealthUpdate(
                                report.status, report.summary()
                            ))
                            emitEvent(CoordinatorEvent.RiskAlert(
                                "🚨 ML CRITICAL: Auto-rolled back to last good weights (rollback #${report.rollbackCount})",
                                AlertSeverity.CRITICAL
                            ))
                        }
                        HealthStatus.HEALTHY -> {
                            // Silent update — no event spam
                            emitEvent(CoordinatorEvent.MLHealthUpdate(
                                report.status, report.summary()
                            ))
                        }
                    }
                    
                    // Every 10 trades, decay exploration to focus more on exploitation
                    if (managedPositions.isEmpty()) {
                        dqn.decayExploration()
                    }
                } catch (e: Exception) {
                    // Don't let DQN training failures break trade execution
                    emitEvent(CoordinatorEvent.Error("DQN training error: ${e.message}"))
                }
                
                recordClosedTrade(position, exitPrice, pnl, pnlPercent, reason)
                managedPositions.remove(symbol)
                
                // Update gamification
                // TODO: gamification.onTradeClosed - wire to GamificationCoordinator
                
                emitEvent(CoordinatorEvent.PositionClosed(symbol, pnl, pnlPercent))
                updatePositionsState()
            }
            else -> {
                emitEvent(CoordinatorEvent.Error("Failed to close position: $result"))
            }
        }
    }
    
    private fun handlePositionEvent(event: PositionEvent) {
        when (event) {
            is PositionEvent.StopLossHit -> {
                scope.launch {
                    closePositionOnStop(event.position.symbol, 
                        managedPositions[event.position.symbol] ?: return@launch, 
                        "STAHL Stop Hit")
                }
            }
            is PositionEvent.TakeProfitHit -> {
                scope.launch {
                    closePositionOnStop(event.position.symbol,
                        managedPositions[event.position.symbol] ?: return@launch,
                        "Take Profit Hit")
                }
            }
            is PositionEvent.LiquidationWarning -> {
                emitEvent(CoordinatorEvent.RiskAlert(
                    "⚠️ LIQUIDATION WARNING: ${event.position.symbol} approaching liquidation",
                    AlertSeverity.CRITICAL
                ))
            }
            is PositionEvent.StopUpdated -> {
                emitEvent(CoordinatorEvent.StopAdjusted(
                    event.position.symbol,
                    event.newStop,
                    0 // Level info not available from PositionEvent
                ))
            }
            is PositionEvent.BreakevenReached -> {
                emitEvent(CoordinatorEvent.RiskAlert(
                    "${event.position.symbol}: Position now at breakeven - stop moved to entry",
                    AlertSeverity.INFO
                ))
            }
            is PositionEvent.Closed -> {
                emitEvent(CoordinatorEvent.PositionClosed(
                    event.position.symbol,
                    event.pnl,
                    event.position.unrealizedPnlPercent
                ))
            }
            else -> { /* Other events */ }
        }
    }
    
    private fun isStopHit(position: ManagedPosition): Boolean {
        return if (position.direction == TradeDirection.LONG) {
            position.currentPrice <= position.currentStop
        } else {
            position.currentPrice >= position.currentStop
        }
    }
    
    private fun isTakeProfitHit(position: ManagedPosition): Boolean {
        return if (position.direction == TradeDirection.LONG) {
            position.currentPrice >= position.currentTarget
        } else {
            position.currentPrice <= position.currentTarget
        }
    }
    
    private fun checkStahlAdjustment(position: ManagedPosition) {
        val direction = if (position.direction == TradeDirection.LONG) "long" else "short"
        
        val shouldAdjust = stahlStop.shouldAdjustStop(
            entryPrice = position.entryPrice,
            currentPrice = position.currentPrice,
            currentStopLevel = position.stahlLevel,
            direction = direction
        )
        
        if (shouldAdjust) {
            val nextLevel = position.stahlLevel + 1
            val newStop = stahlStop.getStopForLevel(position.entryPrice, nextLevel, direction)
            
            managedPositions[position.symbol] = position.copy(
                currentStop = newStop,
                stahlLevel = nextLevel
            )
            
            emitEvent(CoordinatorEvent.StopAdjusted(position.symbol, newStop, nextLevel))
        }
    }
    
    // ========================================================================
    // XAI COMPLIANCE - BOARD DECISION PERSISTENCE
    // ========================================================================
    
    private suspend fun persistBoardDecision(
        symbol: String,
        context: MarketContext,
        consensus: BoardConsensus,
        actionTaken: String,
        reasonForAction: String
    ) {
        val repository = boardDecisionRepository ?: return
        
        try {
            val contextSnapshot = MarketContextSnapshot(
                currentPrice = context.currentPrice,
                change24h = if (context.closes.size >= 2) {
                    ((context.closes.last() - context.closes.first()) / context.closes.first()) * 100
                } else 0.0,
                volume24h = context.volumes.sum(),
                high24h = context.highs.maxOrNull() ?: context.currentPrice,
                low24h = context.lows.minOrNull() ?: context.currentPrice
            )
            
            val voteRecords = consensus.opinions.map { opinion ->
                MemberVoteRecord(
                    memberId = opinion.agentName,
                    displayName = opinion.displayName,
                    role = opinion.role,
                    vote = opinion.vote,
                    sentiment = opinion.sentiment,
                    confidence = opinion.confidence,
                    weight = getBoardMemberWeight(opinion.agentName),
                    reasoning = opinion.reasoning,
                    keyIndicators = opinion.keyIndicators
                )
            }
            
            val decisionRecord = BoardDecisionRecord(
                symbol = symbol,
                timeframe = "1H",
                marketContext = contextSnapshot,
                individualVotes = voteRecords,
                consensus = consensus,
                actionTaken = actionTaken,
                reasonForAction = reasonForAction
            )
            
            repository.save(decisionRecord)
            
        } catch (e: Exception) {
            emitEvent(CoordinatorEvent.Error("Failed to persist board decision: ${e.message}", e))
        }
    }
    
    /**
     * V5.17.0: Dynamic Board Weights by Market Regime
     * 
     * In different market conditions, different board members deserve more influence:
     * - BULL_TRENDING: TrendFollower (Arthur) and MacroStrategist (Marcus) dominate
     * - BEAR_TRENDING: MeanReverter (Helena) and VolatilityTrader (Sentinel) get more weight
     * - HIGH_VOLATILITY: VolatilityTrader (Sentinel) and LiquidityHunter (Aegis) step up
     * - CRASH_MODE: LiquidityHunter (Aegis) as CRO gets highest weight — capital preservation
     * - SIDEWAYS_RANGING: MeanReverter (Helena) and PatternRecognizer (Cipher) excel
     * - BREAKOUT_PENDING: OnChainAnalyst (Nexus) and PatternRecognizer (Cipher) spot breakouts
     * - LOW_VOLATILITY: SentimentAnalyst (Oracle) and MacroStrategist (Marcus) guide timing
     * 
     * Falls back to static weights if regime is unknown or analysis fails.
     */
    /**
     * V5.17.0: Build a complete regime-aware weight override map for all 8 board members.
     * These weights are passed to AIBoardOrchestrator.conveneBoardroom() so the actual
     * consensus vote uses regime-adaptive influence rather than static 12.5% each.
     * 
     * All 8 agent names must be present and weights must sum to ~1.0.
     */
    private fun buildRegimeWeightOverrides(): Map<String, Double> {
        val agentNames = listOf(
            "TrendFollower", "MeanReverter", "VolatilityTrader", "SentimentAnalyst",
            "OnChainAnalyst", "MacroStrategist", "PatternRecognizer", "LiquidityHunter"
        )
        return agentNames.associateWith { getBoardMemberWeight(it) }
    }
    
    private fun getBoardMemberWeight(agentName: String): Double {
        return when (currentRegime) {
            MarketRegime.BULL_TRENDING -> when (agentName) {
                "TrendFollower" -> 0.22      // Arthur — primary signal in trends
                "MacroStrategist" -> 0.16    // Marcus — macro confirms trend
                "OnChainAnalyst" -> 0.14     // Nexus — on-chain confirms flows
                "LiquidityHunter" -> 0.12    // Aegis — execution quality
                "PatternRecognizer" -> 0.10  // Cipher — pattern continuation
                "SentimentAnalyst" -> 0.10   // Oracle — sentiment confirms
                "MeanReverter" -> 0.08       // Helena — less relevant in trend
                "VolatilityTrader" -> 0.08   // Sentinel — less relevant in calm trend
                else -> 0.125
            }
            MarketRegime.BEAR_TRENDING -> when (agentName) {
                "TrendFollower" -> 0.18      // Arthur — still valid for short direction
                "MeanReverter" -> 0.16       // Helena — dead cat bounces
                "VolatilityTrader" -> 0.15   // Sentinel — vol spikes in bear
                "LiquidityHunter" -> 0.14    // Aegis — capital preservation
                "MacroStrategist" -> 0.12    // Marcus — macro bearish confirmation
                "SentimentAnalyst" -> 0.10   // Oracle — fear/capitulation signals
                "OnChainAnalyst" -> 0.08     // Nexus — whale movements
                "PatternRecognizer" -> 0.07  // Cipher — reversal patterns
                else -> 0.125
            }
            MarketRegime.HIGH_VOLATILITY -> when (agentName) {
                "VolatilityTrader" -> 0.22   // Sentinel — born for this
                "LiquidityHunter" -> 0.18    // Aegis — execution and safety
                "MeanReverter" -> 0.15       // Helena — reversion in vol
                "TrendFollower" -> 0.12      // Arthur — trend may whipsaw
                "MacroStrategist" -> 0.10    // Marcus — context matters
                "PatternRecognizer" -> 0.09  // Cipher — vol patterns
                "OnChainAnalyst" -> 0.07     // Nexus — on-chain divergences
                "SentimentAnalyst" -> 0.07   // Oracle — extreme readings
                else -> 0.125
            }
            MarketRegime.CRASH_MODE -> when (agentName) {
                "LiquidityHunter" -> 0.25    // Aegis as CRO — CAPITAL PRESERVATION
                "VolatilityTrader" -> 0.20   // Sentinel — extreme vol management
                "MacroStrategist" -> 0.15    // Marcus — systemic risk assessment
                "MeanReverter" -> 0.12       // Helena — oversold bounces
                "TrendFollower" -> 0.10      // Arthur — downtrend confirmation
                "SentimentAnalyst" -> 0.08   // Oracle — panic/capitulation
                "OnChainAnalyst" -> 0.05     // Nexus — exchange flows
                "PatternRecognizer" -> 0.05  // Cipher — limited value in crash
                else -> 0.125
            }
            MarketRegime.SIDEWAYS_RANGING -> when (agentName) {
                "MeanReverter" -> 0.20       // Helena — primary in range
                "PatternRecognizer" -> 0.18  // Cipher — range patterns
                "LiquidityHunter" -> 0.14    // Aegis — tight execution
                "TrendFollower" -> 0.12      // Arthur — trend breakout watch
                "MacroStrategist" -> 0.10    // Marcus — macro context
                "VolatilityTrader" -> 0.10   // Sentinel — range boundaries
                "OnChainAnalyst" -> 0.08     // Nexus — accumulation signals
                "SentimentAnalyst" -> 0.08   // Oracle — sentiment extremes
                else -> 0.125
            }
            MarketRegime.BREAKOUT_PENDING -> when (agentName) {
                "PatternRecognizer" -> 0.22  // Cipher — breakout patterns
                "OnChainAnalyst" -> 0.18     // Nexus — on-chain precursors
                "VolatilityTrader" -> 0.15   // Sentinel — vol expansion
                "TrendFollower" -> 0.12      // Arthur — direction confirmation
                "LiquidityHunter" -> 0.10    // Aegis — liquidity voids
                "SentimentAnalyst" -> 0.10   // Oracle — sentiment shift
                "MacroStrategist" -> 0.07    // Marcus — macro catalyst
                "MeanReverter" -> 0.06       // Helena — less relevant pre-breakout
                else -> 0.125
            }
            MarketRegime.LOW_VOLATILITY -> when (agentName) {
                "SentimentAnalyst" -> 0.18   // Oracle — sentiment drives low-vol moves
                "MacroStrategist" -> 0.18    // Marcus — macro catalysts pending
                "MeanReverter" -> 0.16       // Helena — tight ranges
                "PatternRecognizer" -> 0.12  // Cipher — compression patterns
                "TrendFollower" -> 0.10      // Arthur — subtle trends
                "OnChainAnalyst" -> 0.10     // Nexus — accumulation
                "LiquidityHunter" -> 0.08    // Aegis — thin books
                "VolatilityTrader" -> 0.08   // Sentinel — waiting for vol
                else -> 0.125
            }
        }
    }
    
    /**
     * V5.17.0: Compute volatility history from PriceBuffer for MarketRegimeDetector.
     * 
     * Primary: Rolling ATR (Average True Range) from high/low/close — captures full candle
     * range including wicks and gaps, giving a richer volatility signal.
     * Fallback: Rolling standard deviation of log-returns from closes — used when
     * high/low data is insufficient.
     * 
     * @param buffer PriceBuffer with OHLCV data
     * @param period ATR lookback period (default 14)
     * @return List of volatility values suitable for MarketRegimeDetector
     */
    private fun computeVolatilityHistory(buffer: PriceBuffer, period: Int = 14): List<Double> {
        val highs = buffer.highs
        val lows = buffer.lows
        val closes = buffer.closes
        
        // Primary: Rolling ATR from high/low/close
        if (highs.size >= period + 1 && lows.size >= period + 1 && closes.size >= period + 1) {
            val atrHistory = mutableListOf<Double>()
            
            for (i in 1 until closes.size) {
                // True Range = max(H-L, |H-prevC|, |L-prevC|)
                val tr = maxOf(
                    highs[i] - lows[i],
                    kotlin.math.abs(highs[i] - closes[i - 1]),
                    kotlin.math.abs(lows[i] - closes[i - 1])
                )
                atrHistory.add(tr)
            }
            
            // Smooth into rolling ATR (EMA-style, Wilder's method)
            if (atrHistory.size >= period) {
                val smoothedAtr = mutableListOf<Double>()
                // Seed with SMA of first 'period' TR values
                var currentAtr = atrHistory.take(period).average()
                smoothedAtr.add(currentAtr)
                
                for (i in period until atrHistory.size) {
                    currentAtr = (currentAtr * (period - 1) + atrHistory[i]) / period
                    smoothedAtr.add(currentAtr)
                }
                return smoothedAtr
            }
        }
        
        // Fallback: Rolling std dev of log-returns from closes
        if (closes.size >= period + 1) {
            val logReturns = (1 until closes.size).map { i ->
                if (closes[i - 1] > 0) kotlin.math.ln(closes[i] / closes[i - 1]) else 0.0
            }
            
            val rollingStdDev = mutableListOf<Double>()
            for (i in period until logReturns.size) {
                val window = logReturns.subList(i - period, i)
                val mean = window.average()
                val variance = window.map { (it - mean).pow(2) }.average()
                rollingStdDev.add(kotlin.math.sqrt(variance))
            }
            return rollingStdDev
        }
        
        return emptyList()
    }
    
    /**
     * V5.17.0: Update current market regime from price data.
     * Called during the OODA loop analysis phase.
     */
    fun updateMarketRegime(
        priceHistory: List<Double>,
        volumeHistory: List<Double>,
        volatilityHistory: List<Double>
    ) {
        if (priceHistory.size >= 100) {
            try {
                val analysis = regimeDetector.detectRegime(priceHistory, volumeHistory, volatilityHistory)
                currentRegime = analysis.currentRegime
            } catch (e: Exception) {
                // Keep existing regime on failure — graceful degradation
            }
        }
    }
    
    // ========================================================================
    // DATABASE RECORDING
    // ========================================================================
    
    private suspend fun recordTrade(order: ExecutedOrder, signal: PendingTradeSignal) {
        val dao = tradeDao ?: return
        
        withContext(Dispatchers.IO) {
            try {
                val trade = TradeEntity(
                    id = order.orderId,
                    symbol = order.symbol,
                    side = order.side.name,
                    orderType = order.type.name,
                    quantity = order.executedQuantity,
                    price = order.executedPrice,
                    fee = order.fee,
                    feeCurrency = order.feeCurrency.ifEmpty { "USD" },
                    realizedPnl = null,
                    realizedPnlPercent = null,
                    exchange = order.exchange,
                    orderId = order.orderId,
                    timestamp = System.currentTimeMillis(),
                    notes = "sl=${signal.suggestedStop} tp=${signal.suggestedTarget} conf=${signal.confidence.toInt()}"
                )
                dao.insertTrade(trade)
            } catch (e: Exception) {
                // Log but don't fail the trade
            }
        }
    }
    
    private suspend fun recordClosedTrade(
        position: ManagedPosition,
        exitPrice: Double,
        pnl: Double,
        pnlPercent: Double,
        reason: String
    ) {
        val dao = tradeDao ?: return
        
        withContext(Dispatchers.IO) {
            try {
                // Fetch the open trade and update it with exit info
                val existingTrades = dao.getRecentTrades(100)
                val openTrade = existingTrades.firstOrNull { it.symbol == position.symbol && it.realizedPnl == null }
                if (openTrade != null) {
                    dao.updateTrade(openTrade.copy(
                        realizedPnl = pnl,
                        realizedPnlPercent = pnlPercent,
                        exitReason = reason,
                        notes = openTrade.notes + " exitPrice=$exitPrice"
                    ))
                }
            } catch (e: Exception) {
                // Log but don't fail
            }
        }
    }
    
    // ========================================================================
    // UTILITY METHODS
    // ========================================================================
    
    private fun determineAction(symbol: String, consensus: BoardConsensus): Pair<String, String> {
        return when {
            !isSignalActionable(consensus) -> 
                "HOLD" to "Signal not actionable: confidence=${String.format("%.2f", consensus.confidence)}, agreement=${consensus.unanimousCount}/8"
            !riskManager.isTradingAllowed.value -> 
                "REJECTED" to "Trading halted by risk manager"
            managedPositions.size >= config.maxConcurrentPositions && !managedPositions.containsKey(symbol) -> 
                "REJECTED" to "Maximum concurrent positions reached (${config.maxConcurrentPositions})"
            config.mode == TradingMode.AUTONOMOUS || config.mode == TradingMode.SCALPING -> 
                "TRADE_EXECUTED" to "Auto mode - executing ${consensus.finalDecision}"
            else -> 
                "SIGNAL_GENERATED" to "Signal generated for user confirmation"
        }
    }
    
    private fun isSignalActionable(consensus: BoardConsensus): Boolean {
        if (consensus.confidence < config.minConfidenceToTrade) return false
        if (consensus.unanimousCount < config.minBoardAgreement) return false
        if (consensus.finalDecision == BoardVote.HOLD) return false
        return true
    }
    
    private fun generateTradeSignal(
        symbol: String,
        consensus: BoardConsensus,
        buffer: PriceBuffer
    ): PendingTradeSignal {
        val currentPrice = buffer.closes.last()
        val direction = if (consensus.finalDecision == BoardVote.BUY ||
            consensus.finalDecision == BoardVote.STRONG_BUY) {
            TradeDirection.LONG
        } else {
            TradeDirection.SHORT
        }
        
        val directionStr = if (direction == TradeDirection.LONG) "long" else "short"
        val stopLoss = stahlStop.calculateInitialStop(currentPrice, directionStr)
        val takeProfit = stahlStop.calculateTakeProfit(currentPrice, directionStr)
        
        val portfolioValue = positionManager.getPortfolioValue()
        
        // V5.17.0: POSITION SIZING HIERARCHY
        // 1. Kelly ceiling: config.defaultPositionSizePercent (default 10%) — NEVER exceed this
        // 2. Board recommendation: consensus.recommendedPositionSize (0.0–1.0 multiplier)
        //    Reduces below Kelly based on board confidence and agreement
        // 3. Disagreement multiplier: further reduces when board models disagree
        //    When ensemble detection flags high disagreement, position shrinks further
        //
        // The board can only REDUCE below Kelly, never increase above it.
        val kellyCeiling = config.defaultPositionSizePercent
        val boardMultiplier = consensus.recommendedPositionSize  // 0.0–1.0
        val disagreementMultiplier = _state.value.positionSizeMultiplier  // 0.0–1.0
        
        val adjustedPositionPercent = kellyCeiling * boardMultiplier * disagreementMultiplier
        val positionValue = portfolioValue * (adjustedPositionPercent / 100.0)
        val stopDistance = abs(currentPrice - stopLoss)
        val riskAmount = (stopDistance / currentPrice) * positionValue
        val riskPercent = (riskAmount / portfolioValue) * 100
        
        return PendingTradeSignal(
            id = "SIG-${System.currentTimeMillis()}-${(1000..9999).random()}",
            symbol = symbol,
            direction = direction,
            suggestedEntry = currentPrice,
            suggestedStop = stopLoss,
            suggestedTarget = takeProfit,
            positionSizePercent = adjustedPositionPercent,
            riskPercent = riskPercent,
            estimatedValue = positionValue,
            confidence = consensus.confidence,
            boardConsensus = consensus
        )
    }
    
    private fun performRiskCheck(signal: PendingTradeSignal): String? {
        if (!riskManager.isTradingAllowed.value) {
            return "Trading halted by risk manager"
        }
        if (managedPositions.size >= config.maxConcurrentPositions &&
            !managedPositions.containsKey(signal.symbol)) {
            return "Maximum concurrent positions reached"
        }
        if (managedPositions.containsKey(signal.symbol)) {
            return "Already have position in ${signal.symbol}"
        }
        return null
    }
    
    private fun updatePositionPrice(position: ManagedPosition, newPrice: Double): ManagedPosition {
        val pnl = calculatePnL(position, newPrice)
        val pnlPercent = calculatePnLPercent(position, newPrice)
        
        return position.copy(
            currentPrice = newPrice,
            unrealizedPnL = pnl,
            unrealizedPnLPercent = pnlPercent
        )
    }
    
    private fun calculatePnL(position: ManagedPosition, exitPrice: Double): Double {
        return if (position.direction == TradeDirection.LONG) {
            (exitPrice - position.entryPrice) * position.quantity
        } else {
            (position.entryPrice - exitPrice) * position.quantity
        }
    }
    
    private fun calculatePnLPercent(position: ManagedPosition, exitPrice: Double): Double {
        return if (position.direction == TradeDirection.LONG) {
            ((exitPrice - position.entryPrice) / position.entryPrice) * 100
        } else {
            ((position.entryPrice - exitPrice) / position.entryPrice) * 100
        }
    }
    
    private fun cleanExpiredSignals() {
        val now = System.currentTimeMillis()
        pendingSignals.entries.removeIf { (_, signal) ->
            if (signal.expiresAt < now && signal.status == SignalStatus.PENDING) {
                signal.status = SignalStatus.EXPIRED
                true
            } else {
                false
            }
        }
        updatePendingSignalsState()
    }
    
    private suspend fun rateLimitResetLoop() {
        while (isRunning.get()) {
            val now = System.currentTimeMillis()
            if (now - lastHourReset > 3600_000) {
                tradesThisHour = 0
                lastHourReset = now
            }
            if (now - lastDayReset > 86400_000) {
                tradesThisDay = 0
                lastDayReset = now
            }
            delay(60_000)
        }
    }
    
    /**
     * BUILD #104: Periodic memory cleanup to prevent unbounded growth.
     * 
     * Runs every 5 minutes to clean up:
     * - Old pending signals (>15 minutes)
     * - Stale cross-exchange price data (>5 minutes)
     * - Inactive price buffers (no updates >30 minutes)
     * 
     * Keeps WebSockets alive but prevents memory leaks from data accumulation.
     */
    private fun cleanupOldData() {
        val now = System.currentTimeMillis()
        val cleanupThreshold = 15 * 60 * 1000L // 15 minutes
        val staleDataThreshold = 5 * 60 * 1000L // 5 minutes
        val inactiveBufferThreshold = 30 * 60 * 1000L // 30 minutes
        
        try {
            // Clean up old pending signals
            val oldSignals = pendingSignals.filter { (_, signal) ->
                (now - signal.timestamp) > cleanupThreshold
            }
            oldSignals.forEach { (symbol, _) ->
                pendingSignals.remove(symbol)
            }
            
            // Clean up stale cross-exchange prices
            crossExchangePrices.forEach { (symbol, exchangeMap) ->
                val staleExchanges = exchangeMap.filter { (_, tick) ->
                    (now - tick.timestamp) > staleDataThreshold
                }
                staleExchanges.forEach { (exchange, _) ->
                    exchangeMap.remove(exchange)
                }
            }
            
            // Clean up stale order books
            crossExchangeOrderBooks.forEach { (symbol, exchangeMap) ->
                val staleExchanges = exchangeMap.filter { (_, book) ->
                    (now - book.timestamp) > staleDataThreshold
                }
                staleExchanges.forEach { (exchange, _) ->
                    exchangeMap.remove(exchange)
                }
            }
            
            // Clean up inactive price buffers (no updates in 30 minutes)
            val inactiveBuffers = priceBuffers.filter { (_, buffer) ->
                (now - buffer.lastUpdate) > inactiveBufferThreshold
            }
            inactiveBuffers.forEach { (symbol, _) ->
                priceBuffers.remove(symbol)
            }
            
            if (oldSignals.isNotEmpty() || inactiveBuffers.isNotEmpty()) {
                Log.d(TAG, "🧹 Memory cleanup: Removed ${oldSignals.size} old signals, ${inactiveBuffers.size} inactive buffers")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during memory cleanup: ${e.message}", e)
        }
    }
    
    private fun emitEvent(event: CoordinatorEvent) {
        scope.launch {
            _events.emit(event)
        }
    }
    
    private fun updateState(updater: (CoordinatorState) -> CoordinatorState) {
        _state.update(updater)
    }
    
    private fun updatePositionsState() {
        updateState { it.copy(activePositions = managedPositions.toMap()) }
    }
    
    private fun updatePendingSignalsState() {
        updateState { it.copy(pendingSignals = pendingSignals.values.toList()) }
    }
}

// ============================================================================
// STAHL STAIR STOP EXTENSIONS
// ============================================================================

fun StahlStairStop.shouldAdjustStop(
    entryPrice: Double,
    currentPrice: Double,
    currentStopLevel: Int,
    direction: String
): Boolean {
    val levelThresholds = listOf(0.02, 0.04, 0.06, 0.08, 0.10, 0.12, 0.15, 0.18, 0.22, 0.27)
    
    if (currentStopLevel >= levelThresholds.size) return false
    
    val requiredMove = levelThresholds[currentStopLevel]
    val actualMove = if (direction == "long") {
        (currentPrice - entryPrice) / entryPrice
    } else {
        (entryPrice - currentPrice) / entryPrice
    }
    
    return actualMove >= requiredMove
}

fun StahlStairStop.getStopForLevel(entryPrice: Double, level: Int, direction: String): Double {
    val stopMultipliers = listOf(
        0.02,   // Level 0: 2% initial stop
        0.01,   // Level 1: Move to 1% stop
        0.00,   // Level 2: Breakeven
        -0.01,  // Level 3: Lock 1% profit
        -0.02,  // Level 4: Lock 2% profit
        -0.03,  // Level 5: Lock 3% profit
        -0.05,  // Level 6: Lock 5% profit
        -0.07,  // Level 7: Lock 7% profit
        -0.10,  // Level 8: Lock 10% profit
        -0.15,  // Level 9: Lock 15% profit
        -0.20   // Level 10: Lock 20% profit
    )
    
    val multiplier = stopMultipliers.getOrElse(level) { stopMultipliers.last() }
    
    return if (direction == "long") {
        entryPrice * (1 - multiplier)
    } else {
        entryPrice * (1 + multiplier)
    }
}

// ============================================================================
// PRICE FEED EXTENSION
// ============================================================================

/**
 * Connect coordinator to price feed service
 */
fun TradingCoordinator.connectToPriceFeed(
    priceTickFlow: SharedFlow<PriceTick>,
    ohlcvFlow: SharedFlow<OHLCVBar>,
    scope: CoroutineScope
) {
    scope.launch {
        priceTickFlow.collect { tick ->
            onPriceTick(tick.symbol, tick.last, tick.volume, tick.exchange)
        }
    }
    
    scope.launch {
        ohlcvFlow.collect { bar ->
            onPriceUpdate(bar.symbol, bar.open, bar.high, bar.low, bar.close, bar.volume)
        }
    }
}
