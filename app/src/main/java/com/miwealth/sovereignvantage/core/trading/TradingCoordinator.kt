package com.miwealth.sovereignvantage.core.trading

// BUILD #342: Clean rebuild to fix APK parsing issue

import android.util.Log
import com.miwealth.sovereignvantage.core.*
import com.miwealth.sovereignvantage.core.ai.*
import com.miwealth.sovereignvantage.core.gamification.*
import com.miwealth.sovereignvantage.core.ml.*
import com.miwealth.sovereignvantage.core.signals.*
import com.miwealth.sovereignvantage.core.trading.assets.*
import com.miwealth.sovereignvantage.core.trading.engine.*
import com.miwealth.sovereignvantage.core.trading.scalping.*
import com.miwealth.sovereignvantage.core.exchange.BinancePublicPriceFeed
import com.miwealth.sovereignvantage.core.utils.SystemLogger
import com.miwealth.sovereignvantage.data.local.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.miwealth.sovereignvantage.data.models.OrderBook
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt
// BUILD #425: Import for dual capital architecture
import com.miwealth.sovereignvantage.core.TradingSystemManager

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
    val mode: TradingMode = TradingMode.AUTONOMOUS,  // BUILD #236: AUTONOMOUS default
    val analysisIntervalMs: Long = 15_000,           // BUILD #236: 60s→15s for responsiveness
    val minConfidenceToTrade: Double = 0.30,         // BUILD #433: Raised to 30% to prevent over-trading with fresh DQNs
    val minBoardAgreement: Int = 4,                  // BUILD #447: Moderate consensus (4 of 8 members must agree)
    val useStahlStops: Boolean = true,               // Apply STAHL Stair Stop™
    val maxConcurrentPositions: Int = 5,             // Maximum open positions
    val defaultPositionSizePercent: Double = 10.0,   // Default position size (% of portfolio)
    val defaultRiskPercent: Double = 2.0,            // Default risk per trade (% of capital)
    val defaultLeverage: Int = 1,                    // BUILD #266: Leverage (1x = no leverage, safest default)
    val cooldownAfterTradeMs: Long = 30_000,         // BUILD #236: 5min→30s cooldown for paper testing
    val enabledSymbols: List<String>? = null,        // Explicit symbol list (null = use filter/registry)
    val assetFilter: AssetFilter? = null,            // Filter for dynamic symbol selection
    val paperTradingMode: Boolean = true,            // Paper trading by default for safety
    val initialCapital: Double = 100_000.0,          // BUILD #261: Starting capital for portfolio value calc
    val maxTradesPerHour: Int = 10,                  // Rate limit - hourly
    val maxTradesPerDay: Int = 50,                   // Rate limit - daily
    
    // BUILD #330: Phase 1 time-based position management
    val enableTimeBasedExits: Boolean = true,        // Enable lingering position exits
    val winnerTimeoutHours: Int = 24,                // Close profitable positions after X hours
    val loserTimeoutHours: Int = 48,                 // Close losing positions after X hours
    val winnerMinProfitPercent: Double = 0.5,        // Minimum profit % to trigger winner timeout
    
    // BUILD #349: TESTING - Disable drawdown limits (set to 1000% = effectively disabled)
    // BUILD #350: Clean rebuild to fix unparseable APK
    val mainBoardMaxDrawdownPercent: Double = 1000.0,  // TESTING ONLY - was 15.0
    val hedgeFundMaxDrawdownPercent: Double = 1000.0,  // TESTING ONLY - was 60.0
    
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
    val orderId: String,
    // BUILD #266: Real trading display fields
    val leverage: Int = 1,                          // Leverage multiplier applied
    val marginUsed: Double = 0.0,                   // USDT margin posted for this position
    val liquidationPrice: Double = 0.0,             // Price at which position force-closes
    val notionalValue: Double = 0.0,                // Full position value (quantity × entryPrice)
    val entryFeesPaid: Double = 0.0,                // Fees paid on entry (deducted from balance)
    val peakUnrealizedPnL: Double = 0.0,            // Highest P&L reached (for STAHL tracking)
    // BUILD #425: Board attribution for dual capital system
    val board: BoardType = BoardType.MAIN           // Which AI board owns this position
) {
    /** Time elapsed since position opened, in milliseconds */
    val elapsedMs: Long get() = System.currentTimeMillis() - entryTime
    
    /** Current position value marked to market (margin + unrealised P&L) */
    val currentValue: Double get() = marginUsed + unrealizedPnL
    
    /** Return on margin % — how much the posted margin has grown/shrunk */
    val returnOnMarginPercent: Double get() =
        if (marginUsed > 0.0) (unrealizedPnL / marginUsed) * 100.0 else 0.0
}

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
    // BUILD #288: Track realized P&L from closed trades for portfolio value
    val totalRealizedPnL: Double = 0.0,
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
    
    // BUILD #262: Synchronized to prevent race condition between bootstrap thread
    // and live OHLCV collector. Without sync, lists can have mismatched sizes
    // during concurrent addCandle() calls, causing NPE when indicators access
    // closes[i] while highs has one more element (ArrayList internal null gap).
    @Synchronized
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
    
    @Synchronized
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
    
    // BUILD #236: Lowered 50→20 for paper trading. 50pts = 4+min wait, 20pts = ~100s acceptable.
    fun hasEnoughData(): Boolean = closes.size >= 20
    
    // BUILD #262: Synchronized snapshot - prevents reading inconsistent list sizes
    // during concurrent addCandle() calls from bootstrap + live collector threads.
    @Synchronized
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
    private val context: android.content.Context,
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
    private val sentimentEngine: SentimentEngine? = null,
    // BUILD #337: Macro Sentiment Analyzer — provides global macro data to Soros (GlobalMacroAnalyst) ✅
    private val macroSentimentAnalyzer: com.miwealth.sovereignvantage.core.ai.macro.MacroSentimentAnalyzer? = null,
    // BUILD #336: DQN State DAO for persisting neural network weights
    private val dqnStateDao: com.miwealth.sovereignvantage.core.ml.DQNStateDao? = null
) {
    
    // BUILD #362: STAHL Emergency Close Configuration
    // Client-configurable retry behavior for emergency position closes
    // Can be updated via setSTAHLEmergencyConfig() at runtime
    var stahlEmergencyConfig: STAHLEmergencyConfig = STAHLEmergencyConfig.balanced()
        private set
    
    // BUILD #364: Multi-Position Decision Engine
    // AI Board logic for deciding when to open additional positions per symbol
    // Can be reconfigured at runtime for client sovereignty
    private var multiPositionEngine: MultiPositionDecisionEngine = MultiPositionDecisionEngine()
    
    // BUILD #424: Dual Capital Architecture
    // Reference to TradingSystemManager for capital management functions
    // Set by TradingSystemManager after construction
    var tradingSystemManager: TradingSystemManager? = null
    
    /**
     * Update multi-position configuration.
     * Allows client to control maximum positions per symbol and risk parameters.
     * Reinforces client sovereignty over portfolio construction.
     */
    fun setMultiPositionConfig(config: MultiPositionConfig) {
        multiPositionEngine = MultiPositionDecisionEngine(config)
        SystemLogger.i(TAG, "📊 Multi-Position Config updated:")
        SystemLogger.i(TAG, "   Max per symbol: ${config.maxPositionsPerSymbol?.toString() ?: "unlimited (AI decides)"}")
        SystemLogger.i(TAG, "   Max total: ${config.maxTotalPositions}")
        SystemLogger.i(TAG, "   Max concentration: ${config.maxConcentrationPercent}%")
        SystemLogger.i(TAG, "   Min confidence: ${config.minConfidenceForMultiple}%")
    }
    
    /**
     * Update STAHL emergency configuration.
     * Allows client to change retry behavior at runtime.
     * Reinforces client sovereignty over trading behavior.
     */
    fun setSTAHLEmergencyConfig(config: STAHLEmergencyConfig) {
        stahlEmergencyConfig = config.validate()
        SystemLogger.i(TAG, "📋 STAHL Emergency Config updated:")
        SystemLogger.i(TAG, "   Rapid attempts: ${config.rapidRetryAttempts}")
        SystemLogger.i(TAG, "   Rapid delay: ${config.rapidRetryDelayMs}ms")
        SystemLogger.i(TAG, "   Unlimited retries: ${config.enableUnlimitedRetries}")
        SystemLogger.i(TAG, "   Persistent delay: ${config.persistentRetryDelayMs}ms")
        SystemLogger.i(TAG, "   Max backoff: ${config.maxBackoffDelayMs}ms")
    }
    
    // BUILD #291: Hedge Fund Board — NOW WIRED ✅
    // Specialized board for advanced strategies: funding arb, cascade detection, DeFi, macro
    // 7 specialists (Soros, Guardian, Draper, Atlas, Theta) + 2 crossovers (Moby, Echo)
    private val hedgeFundBoard = HedgeFundBoardOrchestrator(
        configuration = BoardPresets.HEDGE_FUND_FULL,
        includeCrossovers = true
    )
    
    // BUILD #361: Hedge Fund Execution Bridge — AUTONOMOUS HEDGING ✅
    // Enables Hedge Fund Board to execute hedges independently from main trading decisions
    // Runs in PARALLEL: Trading decisions use both boards, hedging decisions use HF only
    private val hedgeFundExecutionBridge by lazy {
        HedgeFundExecutionBridge(
            orderExecutor = orderExecutor,
            tradingCoordinator = this,
            positionManager = positionManager,
            tradingSystemManager = tradingSystemManager 
                ?: throw IllegalStateException("BUILD #424: TradingSystemManager must be set before hedgeFundExecutionBridge is accessed"),
            config = HedgeFundExecutionConfig(
                maxPositionRiskPercent = 2.0,      // Conservative 2% per hedge
                // ⚠️ BUILD #367: TESTING THRESHOLD — matches HedgeFundExecutionConfig default
                // 20% allows trading during AI learning phase (DQN confidence 5-31%)
                // TODO BUILD #400+: Restore to 0.65 before live trading
                minConfidenceToTrade = 0.30,       // BUILD #433: Raised to 30% (matching Main Board)
                respectGuardianOverride = true,    // Guardian cascade detection
                enableFundingArb = true,           // Allow funding arbitrage hedges
                maxCascadeRiskLevel = 0.7,         // Block if cascade risk > 70%
                useRegimeBasedSizing = true        // Use Atlas regime for sizing
            ),
            scope = scope
        )
    }
    
    companion object {
        private const val TAG = "TradingCoordinator"
        // BUILD #271: ATR-scaled DQN learning rate bounds
        const val BASE_ALPHA = 0.001    // Baseline learning rate
        const val MIN_ALPHA  = 0.0005   // Floor — even the most stable symbol learns
        const val MAX_ALPHA  = 0.005    // Ceiling — prevent instability on volatile symbols
    }
    
    // =========================================================================
    // BUILD #271: PER-SYMBOL DQN LEARNING ENGINES
    // =========================================================================
    // Each symbol gets its own DQNTrader instance so it trains exclusively on
    // its own price history. Learning rates are ATR-scaled at analysis time:
    //   α = BASE_ALPHA × (symbolATR / medianATR)   clamped to [MIN_ALPHA, MAX_ALPHA]
    //
    // Why separate instances rather than a single shared model?
    // - BTC patterns (slow, mean-reverting) should not overwrite XRP patterns
    //   (fast, momentum-driven). A shared model converges to a weighted average
    //   that is optimal for no individual symbol.
    // - Per-symbol replay buffers mean each model sees only relevant experiences.
    // - Exploration/exploitation schedules decay independently — XRP may still be
    //   exploring when BTC has already converged.
    //
    // The AIBoardOrchestrator is constructed once (shared) but updateDqn() hot-swaps
    // the DQN instance before each symbol's board session (cheap: 8 object creations).

    // BUILD #449: PERMANENT DQN INSTANCES — Created once at startup, live forever
    // Each board member gets ONE dedicated DQN that receives continuous price stream
    // NO per-symbol DQNs — members learn from ALL symbols in their single DQN
    // This enables 24/7 learning instead of recreating DQNs every 15 seconds
    
    // MAIN BOARD (8 members)
    private val arthurDqn = DQNTrader(
        stateSize = 30,
        actionSize = 5,
        learningRate = BASE_ALPHA,
        discountFactor = 0.95,
        explorationRate = 0.20
    )
    
    private val helenaDqn = DQNTrader(
        stateSize = 30,
        actionSize = 5,
        learningRate = BASE_ALPHA,
        discountFactor = 0.95,
        explorationRate = 0.20
    )
    
    private val sentinelDqn = DQNTrader(
        stateSize = 30,
        actionSize = 5,
        learningRate = BASE_ALPHA,
        discountFactor = 0.95,
        explorationRate = 0.20
    )
    
    private val oracleDqn = DQNTrader(
        stateSize = 30,
        actionSize = 5,
        learningRate = BASE_ALPHA,
        discountFactor = 0.95,
        explorationRate = 0.20
    )
    
    private val nexusDqn = DQNTrader(
        stateSize = 30,
        actionSize = 5,
        learningRate = BASE_ALPHA,
        discountFactor = 0.95,
        explorationRate = 0.20
    )
    
    private val marcusDqn = DQNTrader(
        stateSize = 30,
        actionSize = 5,
        learningRate = BASE_ALPHA,
        discountFactor = 0.95,
        explorationRate = 0.20
    )
    
    private val cipherDqn = DQNTrader(
        stateSize = 30,
        actionSize = 5,
        learningRate = BASE_ALPHA,
        discountFactor = 0.95,
        explorationRate = 0.20
    )
    
    private val aegisDqn = DQNTrader(
        stateSize = 30,
        actionSize = 5,
        learningRate = BASE_ALPHA,
        discountFactor = 0.95,
        explorationRate = 0.20
    )
    
    // HEDGE FUND BOARD (7 members)
    private val sorosDqn = DQNTrader(
        stateSize = 30,
        actionSize = 5,
        learningRate = BASE_ALPHA,
        discountFactor = 0.95,
        explorationRate = 0.20
    )
    
    private val guardianDqn = DQNTrader(
        stateSize = 30,
        actionSize = 5,
        learningRate = BASE_ALPHA,
        discountFactor = 0.95,
        explorationRate = 0.20
    )
    
    private val draperDqn = DQNTrader(
        stateSize = 30,
        actionSize = 5,
        learningRate = BASE_ALPHA,
        discountFactor = 0.95,
        explorationRate = 0.20
    )
    
    private val atlasDqn = DQNTrader(
        stateSize = 30,
        actionSize = 5,
        learningRate = BASE_ALPHA,
        discountFactor = 0.95,
        explorationRate = 0.20
    )
    
    private val thetaDqn = DQNTrader(
        stateSize = 30,
        actionSize = 5,
        learningRate = BASE_ALPHA,
        discountFactor = 0.95,
        explorationRate = 0.20
    )
    
    private val mobyDqn = DQNTrader(
        stateSize = 30,
        actionSize = 5,
        learningRate = BASE_ALPHA,
        discountFactor = 0.95,
        explorationRate = 0.20
    )
    
    private val echoDqn = DQNTrader(
        stateSize = 30,
        actionSize = 5,
        learningRate = BASE_ALPHA,
        discountFactor = 0.95,
        explorationRate = 0.20
    )
    
    // BUILD #449: Log DQN creation at startup
    init {
        SystemLogger.i(TAG, "🧠 BUILD #449: Created 15 permanent DQN instances")
        SystemLogger.i(TAG, "   Main Board: Arthur, Helena, Sentinel, Oracle, Nexus, Marcus, Cipher, Aegis")
        SystemLogger.i(TAG, "   Hedge Fund: Soros, Guardian, Draper, Atlas, Theta, Moby, Echo")
        SystemLogger.i(TAG, "   Each DQN learns from ALL symbols continuously (no per-symbol recreation)")
        
        // BUILD #452: Load saved weights to restore learned intelligence
        loadPermanentDQNWeights()
    }
    
    /**
     * BUILD #452: Load saved DQN weights for the 15 permanent instances
     * Restores accumulated learning from previous sessions
     */
    private fun loadPermanentDQNWeights() {
        SystemLogger.i(TAG, "📚 BUILD #450: Loading DQN weights from previous sessions...")
        
        if (!dqnWeightsDir.exists()) {
            SystemLogger.i(TAG, "ℹ️ BUILD #450: No saved weights directory — all DQNs starting fresh")
            SystemLogger.i(TAG, "🧠 BUILD #450: Loaded 0 DQN weight files, 15 fresh DQNs")
            return
        }
        
        var loadedCount = 0
        var freshCount = 0
        
        // Main Board DQNs (8 members)
        val mainBoardDqns = listOf(
            arthurDqn to "Arthur",
            helenaDqn to "Helena",
            sentinelDqn to "Sentinel",
            oracleDqn to "Oracle",
            nexusDqn to "Nexus",
            marcusDqn to "Marcus",
            cipherDqn to "Cipher",
            aegisDqn to "Aegis"
        )
        
        for ((dqn, memberName) in mainBoardDqns) {
            if (loadWeightForMember(dqn, memberName)) {
                loadedCount++
            } else {
                freshCount++
            }
        }
        
        // Hedge Fund DQNs (7 members)
        val hedgeFundDqns = listOf(
            sorosDqn to "Soros",
            guardianDqn to "Guardian",
            draperDqn to "Draper",
            atlasDqn to "Atlas",
            thetaDqn to "Theta",
            mobyDqn to "Moby",
            echoDqn to "Echo"
        )
        
        for ((dqn, memberName) in hedgeFundDqns) {
            if (loadWeightForMember(dqn, memberName)) {
                loadedCount++
            } else {
                freshCount++
            }
        }
        
        SystemLogger.i(TAG, "🧠 BUILD #450: Loaded $loadedCount DQN weight files, $freshCount fresh DQNs")
        
        if (loadedCount > 0) {
            SystemLogger.i(TAG, "✅ BUILD #450: DQN learning restored — continuing from $loadedCount trained models")
        } else {
            SystemLogger.i(TAG, "ℹ️ BUILD #450: All DQNs starting fresh — no prior training data")
        }
    }
    
    /**
     * BUILD #452: Load weights for a single permanent DQN instance
     * Returns true if loaded successfully, false if fresh/failed
     */
    private fun loadWeightForMember(dqn: DQNTrader, memberName: String): Boolean {
        val weightFile = File(dqnWeightsDir, "${memberName}_weights.dat")
        val backupFile = File(dqnBackupDir, "${memberName}_weights.dat")
        
        // Try internal storage first
        if (weightFile.exists()) {
            try {
                val weightsMap = weightFile.readText()
                    .lines()
                    .filter { it.contains("=") }
                    .associate {
                        val (k, v) = it.split("=", limit = 2)
                        k to v
                    }
                
                // BUILD #452: DQNTrader.getPolicyNetwork().loadWeights() is the correct pattern
                dqn.getPolicyNetwork().loadWeights(weightsMap)
                SystemLogger.i(TAG, "📂 BUILD #450: Loaded DQN weights for $memberName from internal storage")
                return true
            } catch (e: Exception) {
                SystemLogger.w(TAG, "⚠️ BUILD #450: Internal weights corrupted for $memberName: ${e.message}")
            }
        }
        
        // Try backup if internal failed or missing
        if (backupFile.exists()) {
            try {
                val weightsMap = backupFile.readText()
                    .lines()
                    .filter { it.contains("=") }
                    .associate {
                        val (k, v) = it.split("=", limit = 2)
                        k to v
                    }
                
                // BUILD #452: DQNTrader.getPolicyNetwork().loadWeights() is the correct pattern
                dqn.getPolicyNetwork().loadWeights(weightsMap)
                SystemLogger.i(TAG, "✅ BUILD #450: Restored $memberName from backup (internal ${if (weightFile.exists()) "corrupted" else "missing"})")
                
                // Copy backup to internal for future speed
                try {
                    backupFile.copyTo(weightFile, overwrite = true)
                    SystemLogger.d(TAG, "💾 BUILD #450: Replaced internal file with backup for $memberName")
                } catch (e: Exception) {
                    SystemLogger.w(TAG, "⚠️ BUILD #450: Failed to copy backup to internal: ${e.message}")
                }
                
                return true
            } catch (e: Exception) {
                SystemLogger.e(TAG, "❌ BUILD #450: Both internal and backup failed for $memberName: ${e.message}")
            }
        }
        
        // No weights found - fresh DQN
        if (!weightFile.exists() && !backupFile.exists()) {
            SystemLogger.d(TAG, "ℹ️ BUILD #450: No saved weights for $memberName (fresh DQN)")
        }
        return false
    }
    
    // BUILD #295: DEPRECATED — Kept for reference only, not used
    // Key format: "BTC/USDT_Arthur", "BTC/USDT_Helena", etc.
    // Total: 60 models (4 symbols × 15 members)
    @Deprecated("BUILD #449: Use permanent DQN instances instead")
    private val perMemberDqn = ConcurrentHashMap<String, DQNTrader>()

    // BUILD #366: DQN weight persistence directory (internal storage - fast access)
    // Saves/loads neural network weights to device storage so intelligence persists across sessions
    // Storage: ~510KB total (60 DQNs × 8,500 params × 4 bytes)
    private val dqnWeightsDir = File(context.filesDir, "dqn_weights").apply { mkdirs() }
    
    // BUILD #440: External backup directory in Downloads (user-accessible, survives reinstall!)
    // Location: /storage/emulated/0/Download/DQN/
    // Simplified path for easy access. Files organized by symbol subdirectories.
    private val dqnBackupDir = File(
        android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
        "DQN"
    ).apply { mkdirs() }

    /**
     * BUILD #295: Generate DQN key for member-symbol pair.
     * BUILD #365: Individual DQN per member (no sharing).
     * 
     * Each board member gets their own dedicated DQN for every symbol.
     * This eliminates race conditions from concurrent access and enables
     * true parallel execution of both boards.
     * 
     * Total DQNs: 60 (8 Main × 4 symbols + 7 Hedge × 4 symbols)
     * Memory cost: ~2MB (vs 1.5MB for 44 shared) = 544KB extra
     * On 12GB device: 0.004% of RAM - TRIVIAL
     * 
     * Benefits:
     * - Zero concurrency issues (each DQN has one owner)
     * - True parallel board execution (no waiting)
     * - Simpler code (no handoff choreography)
     * - Easier debugging (isolated learning per member)
     */
    private fun dqnKey(symbol: String, memberName: String): String {
        // BUILD #365: Every member gets their own unique DQN
        return "${symbol}_${memberName}"
    }
    
    /**
     * BUILD #365: Get or create individual DQN model for specific board member + symbol.
     * Each member gets their own dedicated DQN to develop specialized pattern recognition.
     * No sharing between boards - eliminates race conditions and enables parallel execution.
     *
     * @param symbol Trading pair (e.g., "BTC/USDT")
     * @param memberName Board member name (e.g., "Arthur", "Helena")
     * @param currentAtr Current ATR value for learning rate scaling
     * @param medianAtr Median ATR across all symbols for normalization
     * @return Individual DQN instance for this member-symbol pair
     */
    private fun dqnForMember(
        symbol: String,
        memberName: String,
        currentAtr: Double = 0.0,
        medianAtr: Double = 0.0
    ): DQNTrader {
        val key = dqnKey(symbol, memberName)
        val existedBefore = perMemberDqn.containsKey(key)
        
        val trader = perMemberDqn.getOrPut(key) {
            SystemLogger.d(TAG, "🆕 BUILD #448: CREATING NEW DQN for $memberName on $symbol (key=$key)")
            
            DQNTrader(
                stateSize = 30,
                actionSize = 5,
                learningRate = BASE_ALPHA,
                discountFactor = 0.95,
                explorationRate = 0.20
            )
        }
        
        // BUILD #448: Log reuse for verification
        if (existedBefore) {
            val steps = trader.getExperienceStats().first
            SystemLogger.d(TAG, "♻️ BUILD #448: REUSING DQN for $memberName on $symbol (steps=$steps)")
        }
        
        // Scale learning rate based on symbol volatility (ATR)
        if (currentAtr > 0.0 && medianAtr > 0.0) {
            val scaled = (BASE_ALPHA * (currentAtr / medianAtr)).coerceIn(MIN_ALPHA, MAX_ALPHA)
            trader.updateLearningRate(scaled)
        }
        
        return trader
    }
    
    /**
     * BUILD #449: Return permanent Main Board DQN map (no creation, just map assembly)
     * Each member has ONE DQN that learns from ALL symbols continuously
     */
    private fun createGeneralBoardDqns(
        symbol: String,
        symbolAtr: Double,
        medianAtr: Double
    ): Map<String, DQNTrader> {
        // BUILD #449: Scale learning rates for current symbol's volatility
        val scaledLr = if (symbolAtr > 0.0 && medianAtr > 0.0) {
            (BASE_ALPHA * (symbolAtr / medianAtr)).coerceIn(MIN_ALPHA, MAX_ALPHA)
        } else {
            BASE_ALPHA
        }
        
        // Update all Main Board DQNs with scaled learning rate
        listOf(arthurDqn, helenaDqn, sentinelDqn, oracleDqn, nexusDqn, marcusDqn, cipherDqn, aegisDqn)
            .forEach { it.updateLearningRate(scaledLr) }
        
        // BUILD #449: Return permanent DQN map (no creation!)
        return mapOf(
            "Arthur" to arthurDqn,
            "Helena" to helenaDqn,
            "Sentinel" to sentinelDqn,
            "Oracle" to oracleDqn,
            "Nexus" to nexusDqn,
            "Marcus" to marcusDqn,
            "Cipher" to cipherDqn,
            "Aegis" to aegisDqn
        )
    }
    
    /**
     * BUILD #449: Return permanent Hedge Fund Board DQN map (no creation, just map assembly)
     * Each member has ONE DQN that learns from ALL symbols continuously
     */
    private fun createHedgeFundBoardDqns(
        symbol: String,
        symbolAtr: Double,
        medianAtr: Double
    ): Map<String, DQNTrader> {
        // BUILD #449: Scale learning rates for current symbol's volatility
        val scaledLr = if (symbolAtr > 0.0 && medianAtr > 0.0) {
            (BASE_ALPHA * (symbolAtr / medianAtr)).coerceIn(MIN_ALPHA, MAX_ALPHA)
        } else {
            BASE_ALPHA
        }
        
        // Update all Hedge Fund DQNs with scaled learning rate
        listOf(sorosDqn, guardianDqn, draperDqn, atlasDqn, thetaDqn, mobyDqn, echoDqn)
            .forEach { it.updateLearningRate(scaledLr) }
        
        // BUILD #449: Return permanent DQN map (no creation!)
        return mapOf(
            "Soros" to sorosDqn,
            "Guardian" to guardianDqn,
            "Draper" to draperDqn,
            "Atlas" to atlasDqn,
            "Theta" to thetaDqn,
            "Moby" to mobyDqn,
            "Echo" to echoDqn
        )
    }
    
    /**
     * BUILD #449 OPTION 2: Feed every candle to all 15 permanent DQNs for continuous learning.
     * This ensures DQNs receive a constant stream of market data, not just during analysis cycles.
     * 
     * Called from onPriceUpdate() for EVERY candle received (all symbols, all timeframes).
     * Each DQN processes the candle and updates its internal state/experience continuously.
     */
    private fun feedCandleToAllDqns(
        symbol: String, 
        open: Double, 
        high: Double, 
        low: Double, 
        close: Double, 
        volume: Double
    ) {
        // TODO BUILD #449: Implement candle→DQN feature extraction and feeding
        // For now, this is a placeholder to establish the architecture
        // Next step: Extract features from candle and call dqn.observeMarket() or similar
        
        // Example (to be implemented):
        // val features = extractFeaturesFromCandle(symbol, open, high, low, close, volume)
        // listOf(
        //     arthurDqn, helenaDqn, sentinelDqn, oracleDqn, nexusDqn, marcusDqn, cipherDqn, aegisDqn,
        //     sorosDqn, guardianDqn, draperDqn, atlasDqn, thetaDqn, mobyDqn, echoDqn
        // ).forEach { dqn ->
        //     dqn.observe(features)  // Update internal state with new market data
        // }
    }
    
    /**
     * BUILD #450: Train Main Board DQNs after board decision.
     * Each DQN performs one learning step based on the decision outcome.
     * This increments stepCount and enables continuous learning.
     */
    private fun trainMainBoardDqns(
        symbol: String,
        context: com.miwealth.sovereignvantage.core.ai.MarketContext,
        consensus: com.miwealth.sovereignvantage.core.ai.BoardConsensus
    ) {
        // BUILD #451: Extract features from current market state
        val (features, currentPosition) = com.miwealth.sovereignvantage.core.ai.buildDQNFeatures(context, 0.0)
        
        // BUILD #451: Create local FeatureNormalizer (featureNormalizer in DQNTrader is private)
        val normalizer = com.miwealth.sovereignvantage.core.ml.FeatureNormalizer()
        val normalizedState = normalizer.normalizeWithInteractions(features, currentPosition)
        
        // BUILD #451: Map board vote to DQN action (BoardConsensus uses BoardVote, not BoardDecision)
        val action = mapBoardVoteToAction(consensus.finalDecision)
        
        // BUILD #451: Calculate reward based on decision quality
        // Simple reward: confidence-weighted decision score
        // Positive for BUY/STRONG_BUY, negative for SELL/STRONG_SELL, neutral for HOLD
        val baseReward = when (consensus.finalDecision) {
            com.miwealth.sovereignvantage.core.ai.BoardVote.STRONG_BUY -> +0.5
            com.miwealth.sovereignvantage.core.ai.BoardVote.BUY -> +0.25
            com.miwealth.sovereignvantage.core.ai.BoardVote.HOLD -> 0.0
            com.miwealth.sovereignvantage.core.ai.BoardVote.SELL -> -0.25
            com.miwealth.sovereignvantage.core.ai.BoardVote.STRONG_SELL -> -0.5
        }
        val reward = baseReward * consensus.confidence
        
        // BUILD #450: Add experience to each Main Board DQN
        listOf(arthurDqn, helenaDqn, sentinelDqn, oracleDqn, nexusDqn, marcusDqn, cipherDqn, aegisDqn)
            .forEach { dqn ->
                // Add experience to replay buffer
                dqn.addExperience(
                    state = normalizedState,
                    action = action,
                    reward = reward,
                    nextState = normalizedState,  // Same state for now (will update next cycle)
                    done = false
                )
                
                // Train on replay buffer if we have enough samples
                if (dqn.getExperienceStats().second >= 32) {
                    dqn.trainOnReplayBuffer(batchSize = 16)
                }
            }
        
        SystemLogger.d(TAG, "🎓 BUILD #450: Main Board DQNs trained on $symbol ${consensus.finalDecision} | reward=${String.format("%.3f", reward)}")
    }
    
    /**
     * BUILD #450: Train Hedge Fund DQNs after board decision.
     * Each DQN performs one learning step based on the decision outcome.
     * This increments stepCount and enables continuous learning.
     */
    private fun trainHedgeFundDqns(
        symbol: String,
        context: com.miwealth.sovereignvantage.core.ai.MarketContext,
        consensus: com.miwealth.sovereignvantage.core.ai.HedgeFundBoardConsensus  // BUILD #450 FIX: Use correct type
    ) {
        // BUILD #451: Extract features from current market state
        val (features, currentPosition) = com.miwealth.sovereignvantage.core.ai.buildDQNFeatures(context, 0.0)
        
        // BUILD #451: Create local FeatureNormalizer (featureNormalizer in DQNTrader is private)
        val normalizer = com.miwealth.sovereignvantage.core.ml.FeatureNormalizer()
        val normalizedState = normalizer.normalizeWithInteractions(features, currentPosition)
        
        // BUILD #450: Map board vote to DQN action
        val action = mapBoardVoteToAction(consensus.finalDecision)  // BoardVote not BoardDecision
        
        // BUILD #450: Calculate reward based on decision quality
        val baseReward = when (consensus.finalDecision) {
            com.miwealth.sovereignvantage.core.ai.BoardVote.STRONG_BUY -> +0.5
            com.miwealth.sovereignvantage.core.ai.BoardVote.BUY -> +0.25
            com.miwealth.sovereignvantage.core.ai.BoardVote.HOLD -> 0.0
            com.miwealth.sovereignvantage.core.ai.BoardVote.SELL -> -0.25
            com.miwealth.sovereignvantage.core.ai.BoardVote.STRONG_SELL -> -0.5
        }
        val reward = baseReward * consensus.confidence
        
        // BUILD #450: Add experience to each Hedge Fund DQN
        listOf(sorosDqn, guardianDqn, draperDqn, atlasDqn, thetaDqn, mobyDqn, echoDqn)
            .forEach { dqn ->
                // Add experience to replay buffer
                dqn.addExperience(
                    state = normalizedState,
                    action = action,
                    reward = reward,
                    nextState = normalizedState,  // Same state for now (will update next cycle)
                    done = false
                )
                
                // Train on replay buffer if we have enough samples
                if (dqn.getExperienceStats().second >= 32) {
                    dqn.trainOnReplayBuffer(batchSize = 16)
                }
            }
        
        SystemLogger.d(TAG, "🎓 BUILD #450: Hedge Fund DQNs trained on $symbol ${consensus.finalDecision} | reward=${String.format("%.3f", reward)}")
    }
    
    /**
     * BUILD #450: Map BoardVote enum to TradingAction enum for DQN training.
     * Used by both Main Board and Hedge Fund Board.
     */
    private fun mapBoardVoteToAction(vote: com.miwealth.sovereignvantage.core.ai.BoardVote): com.miwealth.sovereignvantage.core.ml.TradingAction {
        return when (vote) {
            com.miwealth.sovereignvantage.core.ai.BoardVote.STRONG_BUY -> 
                com.miwealth.sovereignvantage.core.ml.TradingAction.STRONG_BUY
            com.miwealth.sovereignvantage.core.ai.BoardVote.BUY -> 
                com.miwealth.sovereignvantage.core.ml.TradingAction.BUY
            com.miwealth.sovereignvantage.core.ai.BoardVote.HOLD -> 
                com.miwealth.sovereignvantage.core.ml.TradingAction.HOLD
            com.miwealth.sovereignvantage.core.ai.BoardVote.SELL -> 
                com.miwealth.sovereignvantage.core.ml.TradingAction.SELL
            com.miwealth.sovereignvantage.core.ai.BoardVote.STRONG_SELL -> 
                com.miwealth.sovereignvantage.core.ml.TradingAction.STRONG_SELL
        }
    }

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
    
    // V5.17.0: AI Board — shared orchestrator; DQN hot-swapped per symbol via updateDqn()
    // V5.18.21: SentimentEngine wired so Oracle uses real Fear & Greed + CoinGecko data ✅
    // BUILD #271: No longer constructed with a fixed DQN — updateDqn() called per symbol
    private val aiBoard = AIBoardOrchestrator(dqn = null, sentimentEngine = sentimentEngine)
    private val signalGenerator = SignalGenerator()
    @Suppress("DEPRECATION")
    private val stahlStop = StahlStairStop()
    
    // V5.17.0: Dynamic Board Weights by Market Regime
    private val regimeDetector = MarketRegimeDetector()
    private var currentRegime: MarketRegime = MarketRegime.SIDEWAYS_RANGING
    
    // State
    private val isRunning = AtomicBoolean(false)
    private val isEmergencyStopped = AtomicBoolean(false)
    private var emergencyStopResetTime: Long = 0L  // BUILD #114: Track when emergency stop was reset
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
    
    // BUILD #288: Track cumulative realized P&L from all closed trades
    // This is the total profit/loss locked in from closed positions
    // Used for accurate portfolio value calculation: cash + realized + unrealized
    private var cumulativeRealizedPnL = 0.0
    
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
        
        // BUILD #291: Log Hedge Fund Board initialization
        SystemLogger.i(TAG, "🛡️ BUILD #291: Hedge Fund Board ACTIVE")
        SystemLogger.i(TAG, "   Members: ${hedgeFundBoard.getActiveMemberNames().joinToString()}")
        SystemLogger.i(TAG, "   Count: ${hedgeFundBoard.getMemberCount()} (${if (hedgeFundBoard.hasCrossovers()) "with" else "without"} crossovers)")
        SystemLogger.i(TAG, "   Specialties: Funding Arb, Cascade Detection, DeFi Analysis, Global Macro, Regime Meta-Strategy")
        
        // BUILD #349: Bootstrap MUST complete BEFORE analysis starts
        // Previous bug: analysis started immediately while bootstrap ran async
        // Result: Hedge Fund Board voting on empty/stale data!
        scope.launch {
            // Bootstrap historical candles for instant intelligent signals
            bootstrapHistoricalData()
            
            // BUILD #335: Load saved DQN Q-tables from database
            loadDQNStates()
            
            // BUILD #295/296: Checkpoint seed DQN weights as baseline
            try {
                val seedDqn = dqnForMember("BTC/USDT", "Arthur", 0.0, 0.0)
                healthMonitor.forceCheckpoint(seedDqn.getPolicyNetwork())
            } catch (e: Exception) {
                emitEvent(CoordinatorEvent.Error("Failed to checkpoint DQN weights: ${e.message}"))
            }
            
            SystemLogger.init("✅ BUILD #349: Bootstrap + DQN load COMPLETE — starting analysis")
            
            // NOW start analysis loop (after bootstrap completes)
            analysisJob = scope.launch {
                analysisLoop()
            }
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
        
        // BUILD #405: Subscribe to order updates to convert filled orders into positions
        scope.launch {
            orderExecutor.orderUpdates.collect { update ->
                handleOrderUpdate(update)
            }
        }
        
        // Subscribe to position manager events
        scope.launch {
            positionManager.positionEvents.collect { event ->
                handlePositionEvent(event)
            }
        }
        
        updateState { it.copy(isRunning = true, mode = config.mode, phase = CoordinatorPhase.IDLE) }
        
        // BUILD #261: Seed capital so portfolio value is correct from first analysis cycle
        positionManager.seedCapital(config.initialCapital)
        SystemLogger.init("💰 BUILD #261: Paper capital seeded — A$${String.format("%,.0f", config.initialCapital)} ready for trading")
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
        
        // BUILD #335: Save DQN Q-tables to database before stopping
        scope.launch {
            saveDQNStates()
        }
        
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
        
        // BUILD #335: Save DQN states before emergency shutdown
        saveDQNStates()
        
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
        
        // BUILD #114: Set cooldown to prevent immediate re-trigger
        emergencyStopResetTime = System.currentTimeMillis()
        Log.i(TAG, "🔄 BUILD #114: Emergency stop reset - 60 second cooldown before trading resumes")
        
        updateState { it.copy(emergencyStopActive = false, phase = CoordinatorPhase.IDLE) }
        emitEvent(CoordinatorEvent.EmergencyStopReset)
        emitEvent(CoordinatorEvent.RiskAlert(
            "Emergency stop reset - 60 second cooldown before trading resumes",
            AlertSeverity.INFO
        ))
    }
    
    /**
     * BUILD #117: Get remaining emergency stop cooldown time in seconds
     * Returns 0 if no cooldown is active
     */
    fun getEmergencyStopCooldownSecondsRemaining(): Int {
        if (emergencyStopResetTime == 0L) return 0
        
        val timeSinceReset = System.currentTimeMillis() - emergencyStopResetTime
        if (timeSinceReset >= 60_000) return 0
        
        return ((60_000 - timeSinceReset) / 1000).toInt()
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
                        
                        // BUILD #270: Remove by positionKey (symbol_orderId)
                        val key = "${position.symbol}_${position.orderId}"
                        managedPositions.remove(key)
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
     * BUILD #258: Bootstrap historical candles - Mike's transceiver architecture.
     * 
     * Rapidly pre-loads 500 1-minute candles (~8 hours of market data) for each symbol,
     * giving the DQN immediate context for intelligent analysis. User sees 60%+ confidence
     * signals within 30-60 seconds instead of waiting 2+ minutes for real-time data.
     * 
     * Timeline:
     * - T+0s: App starts
     * - T+2s: Historical bootstrap completes (500 candles × 4 symbols = 2000 candles loaded)
     * - T+5s: First analysis cycle with full context → board shows intelligent signals
     * - T+10s: Real-time candles start flowing, seamlessly appending to historical data
     * 
     * DQN has no idea it's getting accelerated bootstrap - it just sees continuous data flow.
     */
    private suspend fun bootstrapHistoricalData() {
        try {
            val activeSymbols = config.resolveSymbols()
            SystemLogger.init("🚀 BUILD #258: Starting historical bootstrap for ${activeSymbols.size} symbols...")
            
            // Fetch 500 1-minute candles for each symbol from Binance
            val feed = BinancePublicPriceFeed.getInstance()
            val historicalData = feed.fetchHistoricalKlinesForBootstrap(
                symbols = activeSymbols,
                limit = 500  // 500 × 1min = ~8 hours of market data
            )
            
            // Feed historical candles rapidly to price buffers
            for ((symbol, candles) in historicalData) {
                SystemLogger.init("📊 BUILD #258: Bootstrapping $symbol with ${candles.size} historical candles")
                
                for (candle in candles) {
                    onPriceUpdate(
                        symbol = symbol,
                        open = candle.open,
                        high = candle.high,
                        low = candle.low,
                        close = candle.close,
                        volume = candle.volume
                    )
                }
                
                val buffer = priceBuffers[symbol]
                SystemLogger.init("✅ BUILD #258: $symbol bootstrap complete — ${buffer?.closes?.size ?: 0} candles in buffer")
            }
            
            SystemLogger.init("✅ BUILD #258: Historical bootstrap COMPLETE — DQN ready with full market context")
            SystemLogger.init("🎯 BUILD #258: Board can now provide intelligent signals immediately (no 2-minute wait)")
            
            // BUILD #366: Load persisted DQN weights from previous sessions
            loadDQNWeights()
            
        } catch (e: Exception) {
            SystemLogger.error("❌ BUILD #258: Historical bootstrap failed: ${e.message}", e)
            SystemLogger.init("⚠️ BUILD #258: Falling back to real-time data accumulation")
        }
    }
    
    /**
     * Feed OHLCV candle data into the coordinator
     */
    fun onPriceUpdate(symbol: String, open: Double, high: Double, low: Double, close: Double, volume: Double) {
        val buffer = priceBuffers.getOrPut(symbol) { 
            SystemLogger.d(TAG, "📊 BUILD #257: Creating new PriceBuffer for $symbol")
            PriceBuffer(symbol) 
        }
        buffer.addCandle(open, high, low, close, volume)
        
        // BUILD #449 OPTION 2: Feed candle to ALL 15 permanent DQNs for continuous learning
        // Every candle → every DQN → 24/7 learning (not just during analysis)
        // This prevents wasting training time!
        feedCandleToAllDqns(symbol, open, high, low, close, volume)
        
        // BUILD #259: Only log during fill-up (≤500) at milestones; completely silent once at capacity
        val size = buffer.closes.size
        val atCapacity = size >= 500
        if (!atCapacity && (size in 1..5 || size == 20 || size == 100 || size == 500)) {
            SystemLogger.system("📊 BUILD #259: $symbol buffer milestone — $size candles${if (size >= 20) " ✅ READY" else " (need 20+)"}")
        }
        
        // BUILD #270: Update ALL positions for this symbol (multiple allowed)
        managedPositions.entries
            .filter { it.value.symbol == symbol }
            .forEach { (key, position) ->
                val updatedPosition = updatePositionPrice(position, close)
                managedPositions[key] = updatedPosition
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
        val buffer = priceBuffers[symbol]
        if (buffer == null) {
            Log.w(TAG, "⚠️ BUILD #121: Received price for $symbol but no buffer exists! Creating buffer now.")
            priceBuffers[symbol] = PriceBuffer(symbol)
            return
        }
        
        // BUILD #121: Log every 10th price tick to avoid log spam
        val tickCount = buffer.closes.size
        if (tickCount % 10 == 0) {
            Log.d(TAG, "💰 BUILD #121: Price tick for $symbol: ${price} from ${exchange ?: "unknown"} (buffer: $tickCount points)")
        }
        
        buffer.updateCurrentCandle(price, volume)
        
        // BUILD #270: Update ALL positions for this symbol (multiple allowed)
        managedPositions.entries
            .filter { it.value.symbol == symbol }
            .forEach { (key, position) ->
                val updatedPosition = updatePositionPrice(position, price)
                managedPositions[key] = updatedPosition
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
    /**
     * Close a specific position by positionKey (symbol_orderId).
     * BUILD #270: Primary close path — supports multiple positions per symbol.
     * BUILD #367: FIX - Check BOTH managedPositions (AI trades) AND PositionManager (manual trades)
     * Client always has absolute control regardless of STAHL level.
     */
    suspend fun closePositionById(positionKey: String): Result<Unit> {
        // BUILD #367: First try managedPositions (AI-opened positions)
        val managedPosition = managedPositions[positionKey]
        
        if (managedPosition != null) {
            // AI-opened position - use existing flow
            return try {
                val side = if (managedPosition.direction == TradeDirection.LONG) TradeSide.SELL else TradeSide.BUY
                val result = orderExecutor.executeMarketOrder(managedPosition.symbol, side, managedPosition.quantity)
                
                when (result) {
                    is OrderExecutionResult.Success -> {
                        val exitPrice = result.order.executedPrice
                        val pnl = calculatePnL(managedPosition, exitPrice)
                        val pnlPercent = calculatePnLPercent(managedPosition, exitPrice)
                        
                        // BUILD #288: Add this trade's P&L to cumulative realized P&L
                        cumulativeRealizedPnL += pnl
                        
                        // BUILD #428: RELEASE MARGIN when position closes
                        // This prevents margin staying locked forever!
                        val board = if (positionKey.contains("HEDGE")) BoardType.HEDGE_FUND else BoardType.MAIN
                        tradingSystemManager?.releaseMargin(managedPosition.symbol, board)
                        
                        recordClosedTrade(managedPosition, exitPrice, pnl, pnlPercent, "Manual Close")
                        managedPositions.remove(positionKey)
                        updatePositionsState()
                        
                        SystemLogger.trade("🔴 BUILD #367 MANUAL CLOSE (AI): ${managedPosition.symbol} | " +
                            "P&L=${String.format("%.2f", pnl)} (${String.format("%.1f", pnlPercent)}%) | " +
                            "Cumulative Realized=${String.format("%.2f", cumulativeRealizedPnL)} | " +
                            "key=$positionKey")
                        emitEvent(CoordinatorEvent.PositionClosed(managedPosition.symbol, pnl, pnlPercent))
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
        
        // BUILD #367: Not in managedPositions - try PositionManager (manual trades)
        // positionKey format is "symbol_orderId" or just positionId
        val manualPosition = try {
            positionManager.getPosition(positionKey)
        } catch (e: Exception) {
            null
        }
        
        if (manualPosition != null) {
            // Manual trade from TradingViewModel.executeTrade() → PositionManager
            return try {
                val side = when (manualPosition.side) {
                    TradeSide.BUY, TradeSide.LONG -> TradeSide.SELL
                    TradeSide.SELL, TradeSide.SHORT -> TradeSide.BUY
                    else -> TradeSide.SELL
                }
                
                val result = orderExecutor.executeMarketOrder(manualPosition.symbol, side, manualPosition.quantity)
                
                when (result) {
                    is OrderExecutionResult.Success -> {
                        val exitPrice = result.order.executedPrice
                        
                        // Close in PositionManager
                        positionManager.closePosition(positionKey, exitPrice)
                        
                        // Calculate P&L using averageEntryPrice (correct field name)
                        val pnl = (exitPrice - manualPosition.averageEntryPrice) * manualPosition.quantity *
                            (if (side == TradeSide.SELL) 1.0 else -1.0)
                        val pnlPercent = ((exitPrice - manualPosition.averageEntryPrice) / manualPosition.averageEntryPrice) * 100.0 *
                            (if (side == TradeSide.SELL) 1.0 else -1.0)
                        
                        cumulativeRealizedPnL += pnl
                        
                        // BUILD #428: RELEASE MARGIN when position closes
                        // Read board from position.board field (defaults to MAIN if null)
                        tradingSystemManager?.releaseMargin(manualPosition.symbol, manualPosition.board)
                        
                        SystemLogger.trade("🔴 BUILD #396 MANUAL CLOSE (USER): ${manualPosition.symbol} | " +
                            "P&L=${String.format("%.2f", pnl)} (${String.format("%.1f", pnlPercent)}%) | " +
                            "Cumulative Realized=${String.format("%.2f", cumulativeRealizedPnL)} | " +
                            "key=$positionKey")
                        
                        emitEvent(CoordinatorEvent.PositionClosed(manualPosition.symbol, pnl, pnlPercent))
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
        
        // BUILD #396: Position not found in EITHER store - provide helpful error
        val allManaged = managedPositions.keys.toList()
        val allPositionManagerIds = try {
            // Get all position IDs from PositionManager using getOpenPositions
            positionManager.getOpenPositions().map { it.id }
        } catch (e: Exception) {
            emptyList<String>()
        }
        
        SystemLogger.error("BUILD #396: Position not found for key: $positionKey")
        SystemLogger.error("  Managed positions (${allManaged.size}): $allManaged")
        SystemLogger.error("  PositionManager positions (${allPositionManagerIds.size}): $allPositionManagerIds")
        
        return Result.failure(IllegalArgumentException(
            "No position found for key: $positionKey\n" +
            "AI positions: ${allManaged.size} (${allManaged.joinToString(", ")})\n" +
            "Manual positions: ${allPositionManagerIds.size} (${allPositionManagerIds.joinToString(", ")})"
        ))
    }

    /**
     * Close any position for a symbol (legacy — picks first match).
     * Prefer closePositionById() for precise control.
     */
    suspend fun closePosition(symbol: String): Result<Unit> {
        // BUILD #270: find first open position for this symbol
        val entry = managedPositions.entries.firstOrNull { it.value.symbol == symbol }
            ?: return Result.failure(IllegalArgumentException("No position for: $symbol"))
        return closePositionById(entry.key)
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
            totalRealizedPnL = cumulativeRealizedPnL,  // BUILD #288: Include realized P&L
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
     * BUILD #298: CORRECT portfolio value calculation.
     * Formula: Starting Capital + Cumulative Realized P&L + Total Unrealized P&L
     * 
     * Previous bug: PositionManager.getTotalPortfolioValue() only counted margin + unrealized,
     * missing the starting cash and realized P&L from closed trades.
     * 
     * @return Current total portfolio value in base currency (USDT/AUD)
     */
    fun getPortfolioValue(): Double {
        val totalUnrealizedPnL = managedPositions.values.sumOf { it.unrealizedPnL }
        return config.initialCapital + cumulativeRealizedPnL + totalUnrealizedPnL
    }
    
    /**
     * BUILD #302: Increment trade counters when manual trades execute.
     * Called by TradingSystemIntegration.placeOrder() so manual trades update Dashboard counts.
     */
    fun incrementTradeCounters() {
        tradesThisHour++
        tradesThisDay++
    }
    
    /**
     * BUILD #146: Get price buffer sizes for data collection progress display.
     * Returns map of symbol -> current buffer size (number of price points collected).
     * Used by AI Board screen to show "Collecting data: 16/50 points" status.
     */
    fun getPriceBufferSizes(): Map<String, Int> {
        return priceBuffers.mapValues { (_, buffer) -> buffer.closes.size }
    }
    
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
        Log.i(TAG, "🔄 BUILD #121: Analysis loop STARTED - checking every ${config.analysisIntervalMs}ms")
        SystemLogger.init("🔄 BUILD #236: Analysis loop STARTED — interval=${config.analysisIntervalMs}ms mode=${config.mode}")
        while (isRunning.get() && !isEmergencyStopped.get()) {
            try {
                updateState { it.copy(phase = CoordinatorPhase.ANALYZING) }
                
                // Get active symbols from config
                val activeSymbols = config.resolveSymbols()
                Log.d(TAG, "🔄 BUILD #121: Analysis cycle - checking ${activeSymbols.size} symbols: $activeSymbols")
                
                // BUILD #257: Enhanced buffer diagnostics
                val bufferStatus = priceBuffers.map { (sym, buf) -> 
                    "$sym=${buf.closes.size}pts"
                }.joinToString(", ")
                SystemLogger.system("📊 BUILD #257: Analysis cycle — ${activeSymbols.size} symbols, buffers: [$bufferStatus]")
                if (priceBuffers.isEmpty()) {
                    SystemLogger.system("⚠️ BUILD #257: priceBuffers HashMap is EMPTY — no candles received yet")
                }
                
                for (symbol in activeSymbols) {
                    if (!isRunning.get() || isEmergencyStopped.get()) break

                    // BUILD #259: Per-symbol try/catch — one bad symbol never silences the rest
                    try {
                        // BUILD #114: Skip during emergency-stop cooldown
                        val timeSinceReset = System.currentTimeMillis() - emergencyStopResetTime
                        if (timeSinceReset < 60_000 && emergencyStopResetTime > 0) {
                            val remainingSec = (60_000 - timeSinceReset) / 1000
                            Log.i(TAG, "⏳ BUILD #114: Emergency stop cooldown - ${remainingSec}s remaining before trading resumes")
                            continue
                        }

                        // Check cooldown
                        val lastTrade = lastTradeTime[symbol] ?: 0
                        if (System.currentTimeMillis() - lastTrade < config.cooldownAfterTradeMs) {
                            SystemLogger.d(TAG, "⏳ BUILD #259: $symbol in post-trade cooldown — skipping")
                            continue
                        }

                        // BUILD #111 FIX #3: Check if we have enough data
                        val buffer = priceBuffers[symbol]
                        if (buffer == null) {
                            Log.w(TAG, "⚠️ BUILD #257: No price buffer for $symbol yet - creating empty buffer")
                            priceBuffers[symbol] = PriceBuffer(symbol)
                            SystemLogger.system("⏳ BUILD #257: $symbol buffer created, waiting for candles...")
                            continue
                        }

                        val bufferSize = buffer.closes.size
                        val hasEnough = buffer.hasEnoughData()
                        Log.d(TAG, "📊 BUILD #121: $symbol buffer status - ${bufferSize} points, hasEnoughData: $hasEnough")

                        if (!hasEnough) {
                            Log.w(TAG, "⚠️ BUILD #257: $symbol has $bufferSize candles (need 20+) — waiting...")
                            SystemLogger.system("⏳ BUILD #257: $symbol buffer $bufferSize/20 candles — ${20 - bufferSize} more needed")
                            continue
                        }

                        // Run analysis
                        analyzeSymbol(symbol, buffer)

                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // BUILD #259: Surface per-symbol failures so board silence is immediately visible in app log
                        SystemLogger.error("❌ BUILD #259: analyzeSymbol FAILED for $symbol — ${e.javaClass.simpleName}: ${e.message}", e)
                        emitEvent(CoordinatorEvent.Error("Analysis error for $symbol: ${e.message}", e))
                    }
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
                // BUILD #259: Surface loop-level errors to SystemLogger
                SystemLogger.error("❌ BUILD #259: Analysis loop ERROR — ${e.javaClass.simpleName}: ${e.message}", e)
                emitEvent(CoordinatorEvent.Error("Analysis error: ${e.message}", e))
                updateState { it.copy(phase = CoordinatorPhase.ERROR) }
            }
            
            delay(config.analysisIntervalMs)
        }
    }
    
    private suspend fun analyzeSymbol(symbol: String, buffer: PriceBuffer) {
        emitEvent(CoordinatorEvent.AnalysisStarted(symbol))
        
        // BUILD #262: Take a synchronized snapshot of the buffer before analysis.
        // Without this, the lists passed to indicators could change size mid-analysis
        // if the live OHLCV collector calls addCandle() concurrently.
        // toMarketContext() is @Synchronized and returns immutable List copies.
        val baseContext = buffer.toMarketContext()
        
        // V5.17.0: Enrich with SentimentEngine data if available
        // Extracts the base asset symbol (e.g. "BTC" from "BTC/USDT")
        val contextWithSentiment = sentimentEngine?.let { engine ->
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
        
        // BUILD #337: Macro enrichment — ENABLED ✅
        val context = macroSentimentAnalyzer?.let { analyzer ->
            try {
                val macroContext = analyzer.getMacroContext(forceRefresh = false)
                contextWithSentiment.copy(
                    macroSentiment = macroContext.globalSentiment.name,
                    macroScore = macroContext.globalScore,
                    macroRiskLevel = macroContext.riskLevel.name,
                    upcomingHighImpactEvents = macroContext.upcomingHighImpactEvents.size,
                    macroNarrative = macroContext.narrative
                )
            } catch (e: Exception) {
                SystemLogger.w(TAG, "⚠️ BUILD #337: Macro data fetch failed: ${e.message}")
                contextWithSentiment
            }
        } ?: contextWithSentiment
        
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

        // BUILD #295: ATR-SCALED PER-MEMBER DQN
        // Create dedicated DQN for each board member to restore specialty diversity
        val symbolAtr: Double = run {
            val vh = computeVolatilityHistory(buffer)
            if (vh.isNotEmpty()) vh.last() else 0.0
        }
        val medianAtr: Double = run {
            val atrs = priceBuffers.values.mapNotNull { buf ->
                val vh = computeVolatilityHistory(buf)
                if (vh.isNotEmpty()) vh.last() else null
            }.sorted()
            if (atrs.isEmpty()) 0.0
            else if (atrs.size % 2 == 0)
                (atrs[atrs.size / 2 - 1] + atrs[atrs.size / 2]) / 2.0
            else atrs[atrs.size / 2]
        }
        
        // BUILD #295: Create per-member DQN maps
        val generalBoardDqns = createGeneralBoardDqns(symbol, symbolAtr, medianAtr)
        val hedgeFundDqns = createHedgeFundBoardDqns(symbol, symbolAtr, medianAtr)
        
        SystemLogger.d(TAG, "🧠 BUILD #295: Analyzing $symbol with ${generalBoardDqns.size} General DQNs + ${hedgeFundDqns.size} Hedge Fund DQNs")
        
        // Log learning rates for visibility
        val sampleLr = generalBoardDqns.values.firstOrNull()?.getLearningRate() ?: 0.0
        SystemLogger.d(TAG, "🧠 BUILD #295 DQN: $symbol α=${String.format("%.5f", sampleLr)} " +
            "(ATR=${String.format("%.4f", symbolAtr)}, medianATR=${String.format("%.4f", medianAtr)})")

        //V5.17.0: Build regime-aware weight overrides for the board.
        // getBoardMemberWeight() returns regime-specific weights that sum to 1.0.
        // These dynamically shift voting influence based on current market conditions.
        val regimeWeights = buildRegimeWeightOverrides()
        
        // BUILD #334: Get current drawdown for board-specific risk checks
        val currentPortfolioValue = positionManager.getPortfolioValue()
        val riskStatus = riskManager.getRiskStatus(currentPortfolioValue)
        val currentDrawdown = riskStatus.currentDrawdown
        
        // BUILD #295: Get General Board consensus with per-member DQNs
        val consensus = aiBoard.conveneAndDecideWithDQNs(
            symbol = symbol,
            context = context,
            memberDqns = generalBoardDqns,
            weightOverrides = regimeWeights
        )
        
        // BUILD #449 OPTION 3: Train Main Board DQNs after decision
        // Each DQN learns from the outcome of this analysis cycle
        trainMainBoardDqns(symbol, context, consensus)
        
        // BUILD #344: Log Main Board decision (was missing!)
        SystemLogger.i(TAG, "📊 BUILD #344: MAIN BOARD (per-member DQN) for $symbol")
        SystemLogger.i(TAG, "   Decision: ${consensus.finalDecision}")
        SystemLogger.i(TAG, "   Confidence: ${String.format("%.1f", consensus.confidence * 100)}%")
        SystemLogger.i(TAG, "   Members: 8 active (Arthur, Helena, Sentinel, Oracle, Nexus, Marcus, Cipher, Aegis)")
        
        Log.i(TAG, "📊 MAIN BOARD DECISION:")
        Log.i(TAG, "   Decision: ${consensus.finalDecision}")
        Log.i(TAG, "   Confidence: ${String.format("%.1f", consensus.confidence * 100)}%")
        
        // BUILD #334: Check if Main Trading Board is blocked by drawdown limit
        val mainBoardAllowed = currentDrawdown < config.mainBoardMaxDrawdownPercent
        if (!mainBoardAllowed) {
            SystemLogger.w(TAG, "🛡️ BUILD #334: Main Board BLOCKED by drawdown (${String.format("%.2f", currentDrawdown)}% > ${config.mainBoardMaxDrawdownPercent}%)")
            Log.w(TAG, "Main Board blocked: Drawdown ${String.format("%.2f", currentDrawdown)}% exceeds limit ${config.mainBoardMaxDrawdownPercent}%")
        }
        
        // BUILD #295: Get Hedge Fund Board consensus with per-member DQNs
        val hedgeFundConsensus = try {
            val hfConsensus = hedgeFundBoard.conveneAndDecideWithDQNs(
                symbol = symbol,
                context = context,
                memberDqns = hedgeFundDqns
            )
            
            // BUILD #449 OPTION 3: Train Hedge Fund DQNs after decision
            // Each DQN learns from the outcome of this analysis cycle
            trainHedgeFundDqns(symbol, context, hfConsensus)
            
            SystemLogger.i(TAG, "💼 BUILD #295: HEDGE FUND BOARD (per-member DQN) for $symbol")
            SystemLogger.i(TAG, "   Decision: ${hfConsensus.finalDecision}")
            SystemLogger.i(TAG, "   Confidence: ${String.format("%.1f", hfConsensus.confidence * 100)}%")
            SystemLogger.i(TAG, "   Members: ${hedgeFundBoard.getMemberCount()} active (${hedgeFundBoard.getActiveMemberNames().joinToString(", ")})")
            
            Log.i(TAG, "💼 HEDGE FUND BOARD DECISION:")
            Log.i(TAG, "   Decision: ${hfConsensus.finalDecision}")
            Log.i(TAG, "   Confidence: ${String.format("%.1f", hfConsensus.confidence * 100)}%")
            Log.i(TAG, "   Members: ${hedgeFundBoard.getMemberCount()} active (${hedgeFundBoard.getActiveMemberNames().joinToString(", ")})")
            
            hfConsensus
        } catch (e: Exception) {
            Log.e(TAG, "❌ Hedge Fund Board error for $symbol", e)
            SystemLogger.e(TAG, "❌ Hedge Fund Board error for $symbol: ${e.message}")
            null
        }
        
        // BUILD #334: Check if Hedge Fund Board is blocked by drawdown limit
        val hedgeFundAllowed = currentDrawdown < config.hedgeFundMaxDrawdownPercent
        if (hedgeFundConsensus != null && !hedgeFundAllowed) {
            SystemLogger.w(TAG, "🛡️ BUILD #334: Hedge Fund Board BLOCKED by drawdown (${String.format("%.2f", currentDrawdown)}% > ${config.hedgeFundMaxDrawdownPercent}%)")
            Log.w(TAG, "Hedge Fund Board blocked: Drawdown ${String.format("%.2f", currentDrawdown)}% exceeds limit ${config.hedgeFundMaxDrawdownPercent}%")
        }
        
        // BUILD #361: AUTONOMOUS HEDGE EXECUTION ✅
        // Process hedge fund decision INDEPENDENTLY for autonomous hedging
        // Runs in PARALLEL to trade decisions (different purposes):
        // - Trade Decision: Both boards vote → execute trades
        // - Hedge Decision: Hedge Fund only → execute hedges autonomously
        if (hedgeFundConsensus != null && hedgeFundAllowed) {
            scope.launch {
                try {
                    val hedgeResult = hedgeFundExecutionBridge.processConsensus(
                        consensus = hedgeFundConsensus,
                        symbol = symbol,
                        currentPrice = context.currentPrice,
                        portfolioValue = getPortfolioValue()
                    )
                    
                    when (hedgeResult) {
                        is HedgeFundExecutionResult.OrderPlaced -> {
                            SystemLogger.system("💼 HEDGE FUND AUTONOMOUS EXECUTION: ${hedgeResult.side} ${hedgeResult.quantity} ${hedgeResult.symbol}")
                            Log.i(TAG, "Hedge Fund autonomous order: ${hedgeResult.orderId} | ${hedgeResult.reasoning}")
                        }
                        is HedgeFundExecutionResult.OrderRejected -> {
                            SystemLogger.w(TAG, "⚠️ Hedge Fund execution rejected: ${hedgeResult.reason}")
                        }
                        is HedgeFundExecutionResult.NoAction -> {
                            SystemLogger.d(TAG, "Hedge Fund: No action required (${hedgeResult.reason})")
                        }
                    }
                } catch (e: Exception) {
                    SystemLogger.e(TAG, "❌ Hedge Fund autonomous execution error: ${e.message}")
                    Log.e(TAG, "Hedge Fund execution error", e)
                }
            }
        }
        
        // BUILD #295: Combine both board decisions (same logic as before)
        val finalConsensus = if (hedgeFundConsensus != null) {
            // BUILD #334: Zero out boards that are blocked by drawdown
            val mainWeight = if (mainBoardAllowed) consensus.confidence else 0.0
            val hfWeight = if (hedgeFundAllowed) hedgeFundConsensus.confidence else 0.0
            val totalWeight = mainWeight + hfWeight
            
            // BUILD #334: If BOTH boards are blocked, reject the trade entirely
            if (totalWeight == 0.0) {
                SystemLogger.w(TAG, "🛑 BUILD #334: BOTH BOARDS BLOCKED by drawdown - rejecting trade")
                Log.w(TAG, "Trade rejected: All boards blocked by drawdown limits")
                emitEvent(CoordinatorEvent.TradeRejected("Both boards blocked by drawdown limits", symbol))
                return
            }
            
            // Weighted score combination (both use weightedScore)
            val combinedSentiment = if (totalWeight > 0) {
                (consensus.weightedScore * mainWeight + hedgeFundConsensus.weightedScore * hfWeight) / totalWeight
            } else {
                consensus.weightedScore
            }
            
            // BUILD #351: COMPREHENSIVE DEBUG LOGGING
            SystemLogger.i(TAG, "🔍 BUILD #351: AGREEMENT DIAGNOSIS for $symbol")
            SystemLogger.i(TAG, "   Main Board RAW:")
            SystemLogger.i(TAG, "     - finalDecision enum: ${consensus.finalDecision}")
            SystemLogger.i(TAG, "     - weightedScore: ${String.format("%.4f", consensus.weightedScore)}")
            SystemLogger.i(TAG, "     - confidence: ${String.format("%.1f", consensus.confidence * 100)}%")
            SystemLogger.i(TAG, "   Hedge Fund RAW:")
            SystemLogger.i(TAG, "     - finalDecision enum: ${hedgeFundConsensus.finalDecision}")
            SystemLogger.i(TAG, "     - weightedScore: ${String.format("%.4f", hedgeFundConsensus.weightedScore)}")
            SystemLogger.i(TAG, "     - confidence: ${String.format("%.1f", hedgeFundConsensus.confidence * 100)}%")
            
            // BUILD #343: If both boards agree on direction, boost confidence (only if both allowed)
            // CRITICAL FIX: Use finalDecision enum, NOT weightedScore!
            // The board's actual vote (HOLD) can differ from the math average (score=-0.08)
            
            val mainDecision = consensus.finalDecision
            val hedgeDecision = hedgeFundConsensus.finalDecision
            
            SystemLogger.i(TAG, "   Extracted decisions:")
            SystemLogger.i(TAG, "     - mainDecision = $mainDecision")
            SystemLogger.i(TAG, "     - hedgeDecision = $hedgeDecision")
            
            // Helper to classify decision as BUY/SELL/HOLD
            fun isBuyDecision(decision: BoardVote): Boolean = 
                decision == BoardVote.BUY || decision == BoardVote.STRONG_BUY
            fun isSellDecision(decision: BoardVote): Boolean = 
                decision == BoardVote.SELL || decision == BoardVote.STRONG_SELL
            fun isHoldDecision(decision: BoardVote): Boolean = 
                decision == BoardVote.HOLD
            
            SystemLogger.i(TAG, "   Classification:")
            SystemLogger.i(TAG, "     - isBuyDecision(main): ${isBuyDecision(mainDecision)}")
            SystemLogger.i(TAG, "     - isSellDecision(main): ${isSellDecision(mainDecision)}")
            SystemLogger.i(TAG, "     - isHoldDecision(main): ${isHoldDecision(mainDecision)}")
            SystemLogger.i(TAG, "     - isBuyDecision(hedge): ${isBuyDecision(hedgeDecision)}")
            SystemLogger.i(TAG, "     - isSellDecision(hedge): ${isSellDecision(hedgeDecision)}")
            SystemLogger.i(TAG, "     - isHoldDecision(hedge): ${isHoldDecision(hedgeDecision)}")
            
            val sameDirection = when {
                // Both boards BUY (including STRONG_BUY)
                isBuyDecision(mainDecision) && isBuyDecision(hedgeDecision) -> {
                    SystemLogger.i(TAG, "   Agreement path: BOTH BUY")
                    true
                }
                // Both boards SELL (including STRONG_SELL)
                isSellDecision(mainDecision) && isSellDecision(hedgeDecision) -> {
                    SystemLogger.i(TAG, "   Agreement path: BOTH SELL")
                    true
                }
                // Both boards HOLD
                isHoldDecision(mainDecision) && isHoldDecision(hedgeDecision) -> {
                    SystemLogger.i(TAG, "   Agreement path: BOTH HOLD")
                    true
                }
                // Different directions
                else -> {
                    SystemLogger.i(TAG, "   Agreement path: DISAGREE")
                    false
                }
            }
            
            // BUILD #361: PURE AVERAGE - No boost/penalty
            // Mike's design: Simple average = (Trading + Hedge) / 2
            // Hedge Board confidence naturally tempers Trading Board enthusiasm
            val combinedConfidence = ((mainWeight + hfWeight) / 2.0).coerceIn(0.0, 1.0)
            
            // BUILD #343: Debug logging shows both finalDecision AND weightedScore
            SystemLogger.i(TAG, "🔀 BUILD #343: COMBINED BOARD DECISION for $symbol")
            SystemLogger.i(TAG, "   Main Board: ${consensus.finalDecision} (${String.format("%.1f", consensus.confidence * 100)}%) | score=${String.format("%.4f", consensus.weightedScore)}")
            SystemLogger.i(TAG, "   Hedge Fund: ${hedgeFundConsensus.finalDecision} (${String.format("%.1f", hedgeFundConsensus.confidence * 100)}%) | score=${String.format("%.4f", hedgeFundConsensus.weightedScore)}")
            SystemLogger.i(TAG, "   Main=${mainDecision} Hedge=${hedgeDecision}")
            SystemLogger.i(TAG, "   Agreement: ${if (sameDirection) "✅ AGREE" else "⚠️ DISAGREE"}")
            SystemLogger.i(TAG, "   Combined Confidence: ${String.format("%.1f", combinedConfidence * 100)}%")
            
            Log.i(TAG, "🔀 COMBINED DECISION:")
            Log.i(TAG, "   Main Board: ${consensus.finalDecision} (${String.format("%.1f", consensus.confidence * 100)}%)")
            Log.i(TAG, "   Hedge Fund: ${hedgeFundConsensus.finalDecision} (${String.format("%.1f", hedgeFundConsensus.confidence * 100)}%)")
            Log.i(TAG, "   Agreement: ${if (sameDirection) "✅ AGREE" else "⚠️ DISAGREE"}")
            Log.i(TAG, "   Combined Confidence: ${String.format("%.1f", combinedConfidence * 100)}%")
            
            // Create combined consensus (preserve main board structure, update score/confidence)
            consensus.copy(
                weightedScore = combinedSentiment,
                confidence = combinedConfidence,
                recommendedPositionSize = consensus.recommendedPositionSize * if (sameDirection) 1.1 else 0.8
            )
        } else {
            // BUILD #334: Main board operating solo - check its drawdown limit
            if (!mainBoardAllowed) {
                SystemLogger.w(TAG, "🛑 BUILD #334: Main Board BLOCKED by drawdown (solo operation) - rejecting trade")
                Log.w(TAG, "Trade rejected: Main board blocked by drawdown limit")
                emitEvent(CoordinatorEvent.TradeRejected("Main board blocked by drawdown limit", symbol))
                return
            }
            consensus  // No hedge fund board, use main board decision
        }
        
        // BUILD #113: MASSIVE DIAGNOSTIC LOGGING
        Log.i(TAG, "═══════════════════════════════════════════════════════════")
        Log.i(TAG, "🎯 BUILD #113: AI BOARD DECISION FOR $symbol")
        Log.i(TAG, "   Decision: ${finalConsensus.finalDecision}")
        Log.i(TAG, "   Confidence: ${String.format("%.1f", finalConsensus.confidence * 100)}%")
        Log.i(TAG, "   Unanimous: ${finalConsensus.unanimousCount}/8 members")
        Log.i(TAG, "   Price: ${context.currentPrice}")
        Log.i(TAG, "   Paper Mode: ${config.paperTradingMode}")
        Log.i(TAG, "   Trading Mode: ${config.mode}")
        Log.i(TAG, "═══════════════════════════════════════════════════════════")
        // BUILD #236: Mirror board decision to SystemLogger so it appears in app log viewer
        SystemLogger.board("🎯 BUILD #236 BOARD: $symbol → ${finalConsensus.finalDecision} | conf=${String.format("%.0f", finalConsensus.confidence * 100)}% | agree=${finalConsensus.unanimousCount}/8 | price=\$${String.format("%.2f", context.currentPrice)}")
        
        emitEvent(CoordinatorEvent.AnalysisComplete(symbol, finalConsensus))
        
        // V5.17.0: Run disagreement analysis on board opinions
        // Map 8 board members into 4 model categories for disagreement detection
        val opinionMap = finalConsensus.opinions.associateBy { it.agentName }
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
        val boardMultiplier = finalConsensus.recommendedPositionSize
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
            if (!disagreementDetector.shouldTakeTrade(finalConsensus.confidence, disagreementAnalysis)) {
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
        
        // BUILD #357: CRITICAL FIX - Check COMBINED board decision (finalConsensus), not just Main board!
        // Previously checked 'consensus' (Main board only), which rejected trades even when
        // the combined decision was actionable. This caused zero trades to execute.
        if (!isSignalActionable(finalConsensus)) {
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
        
        // BUILD #270: Multiple positions per symbol allowed — global cap only
        if (managedPositions.size >= config.maxConcurrentPositions) {
            emitEvent(CoordinatorEvent.TradeRejected("Max positions reached (${config.maxConcurrentPositions})", symbol))
            return
        }
        
        // BUILD #357: Generate signal from COMBINED board decision (finalConsensus)
        val signal = generateTradeSignal(symbol, finalConsensus, buffer)
        
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
        val portfolioValue = getPortfolioValue()  // BUILD #298: Use correct formula
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
        // BUILD #126: Log trade execution with SystemLogger
        SystemLogger.i(TAG, "💰 TRADE EXECUTION: ${signal.symbol}")
        SystemLogger.i(TAG, "   Direction: ${signal.direction}")
        SystemLogger.i(TAG, "   Entry Price: $${String.format("%.2f", signal.suggestedEntry)}")
        SystemLogger.i(TAG, "   Stop Loss: $${String.format("%.2f", signal.suggestedStop)}")
        SystemLogger.i(TAG, "   Target: $${String.format("%.2f", signal.suggestedTarget)}")
        SystemLogger.i(TAG, "   Position Size: ${String.format("%.1f", signal.positionSizePercent)}%")
        SystemLogger.i(TAG, "   Confidence: ${String.format("%.1f", signal.confidence * 100)}%")
        
        updateState { it.copy(phase = CoordinatorPhase.EXECUTING) }
        
        return try {
            // Final risk check
            val riskCheck = performRiskCheck(signal)
            if (riskCheck != null) {
                SystemLogger.w(TAG, "⛔ TRADE REJECTED: $riskCheck")
                emitEvent(CoordinatorEvent.TradeRejected(riskCheck, signal.symbol))
                signal.status = SignalStatus.REJECTED
                updateState { it.copy(phase = CoordinatorPhase.IDLE) }
                return Result.failure(Exception(riskCheck))
            }
            
            // BUILD #364: Multi-position decision check
            // AI Board decides if we should open another position on this symbol
            val currentPositions = managedPositions.values.filter { it.symbol == signal.symbol }
            val currentCount = currentPositions.size
            val portfolioValue = getPortfolioValue()  // BUILD #298: Use correct formula
            val symbolExposure = currentPositions.sumOf { it.marginUsed + it.unrealizedPnL }
            val availableMargin = portfolioValue - managedPositions.values.sumOf { it.marginUsed }
            
            val multiPosDecision = multiPositionEngine.shouldOpenPosition(
                symbol = signal.symbol,
                signalConfidence = signal.confidence * 100.0,
                currentPositionCount = currentCount,
                totalPositions = managedPositions.size,
                availableMargin = availableMargin,
                portfolioValue = portfolioValue,
                symbolExposure = symbolExposure,
                marketRegime = "UNKNOWN"  // TODO: Add regime detection to signal
            )
            
            if (!multiPosDecision.canOpen) {
                SystemLogger.i(TAG, "⛔ MULTI-POSITION LIMIT: ${multiPosDecision.reason}")
                SystemLogger.i(TAG, "   Current positions for ${signal.symbol}: $currentCount")
                SystemLogger.i(TAG, "   Total positions: ${managedPositions.size}")
                emitEvent(CoordinatorEvent.TradeRejected(multiPosDecision.reason, signal.symbol))
                signal.status = SignalStatus.REJECTED
                updateState { it.copy(phase = CoordinatorPhase.IDLE) }
                return Result.failure(Exception(multiPosDecision.reason))
            }
            
            SystemLogger.i(TAG, "✅ MULTI-POSITION APPROVED: ${multiPosDecision.reason}")
            if (currentCount > 0) {
                SystemLogger.i(TAG, "   Position ${currentCount + 1} for ${signal.symbol}")
                SystemLogger.i(TAG, "   Size multiplier: ${String.format("%.1f", multiPosDecision.recommendedSize * 100)}%")
            }
            
            // Calculate position size
            // BUILD #364: Apply multi-position size multiplier
            // First position = 100%, second = 75%, third = 50%, etc.
            val adjustedPositionSizePercent = signal.positionSizePercent * multiPosDecision.recommendedSize
            val positionValue = portfolioValue * (adjustedPositionSizePercent / 100.0)
            val quantity = positionValue / signal.suggestedEntry
            
            SystemLogger.i(TAG, "   Portfolio Value: $${String.format("%,.2f", portfolioValue)}")
            SystemLogger.i(TAG, "   Position Value: $${String.format("%,.2f", positionValue)}")
            SystemLogger.i(TAG, "   Quantity: ${String.format("%.6f", quantity)}")
            
            // BUILD #428: POST MARGIN BEFORE ORDER EXECUTION
            // This prevents $0 margin ghost positions!
            // Starting Margin = position value × leverage
            val leverage = 1.0  // Main Board uses 1x leverage (can be made configurable later)
            val startingMargin = positionValue * leverage
            tradingSystemManager?.postMargin(signal.symbol, startingMargin, BoardType.MAIN)
            SystemLogger.system("📊 BUILD #428: Main Board margin posted: ${signal.symbol} = A\$${String.format("%.2f", startingMargin)} " +
                "(position=A\$${String.format("%.2f", positionValue)}, leverage=${leverage}x)")
            
            // Execute order through OrderExecutor
            // V5.19.0 BUILD #102 FIX: Remove paperTradingMode bypass.
            // Previously, paper trades called simulatePaperTrade() which created
            // fake orders without updating PaperTradingAdapter balances.
            // Now ALL trades route through orderExecutor, which correctly
            // delegates to PaperTradingAdapter when in paper mode.
            val side = if (signal.direction == TradeDirection.LONG) TradeSide.BUY else TradeSide.SELL
            
            val result = if (config.useStahlStops) {
                SystemLogger.i(TAG, "   Using STAHL Stair Stop™")
                // BUILD #436: Tag as MAIN board
                orderExecutor.executeWithStahlStop(
                    symbol = signal.symbol,
                    side = side,
                    quantity = quantity,
                    entryPrice = signal.suggestedEntry,
                    metadata = mapOf("board" to "MAIN")
                )
            } else {
                // BUILD #436: Tag as MAIN board
                orderExecutor.executeMarketOrder(signal.symbol, side, quantity, metadata = mapOf("board" to "MAIN"))
            }
            
            // BUILD #126: Log order execution result
            SystemLogger.i(TAG, "📊 Order execution result: ${result.javaClass.simpleName}")
            
            when (result) {
                is OrderExecutionResult.Success -> {
                    SystemLogger.i(TAG, "   ✅ SUCCESS! Order ID: ${result.order.orderId}")
                    SystemLogger.i(TAG, "   Executed Price: $${String.format("%.2f", result.order.executedPrice)}")
                    SystemLogger.i(TAG, "   Executed Qty: ${String.format("%.6f", result.order.executedQuantity)}")
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
                    // BUILD #261: Surface rejection reason in SystemLogger for visibility
                    SystemLogger.error("❌ BUILD #261 TRADE REJECTED: ${signal.symbol} — ${result.reason} | code=${result.code} | portfolioValue=A$${String.format("%,.2f", getPortfolioValue())}", null)  // BUILD #298: Correct formula
                    Log.e(TAG, "   ❌ REJECTED: ${result.reason}")
                    signal.status = SignalStatus.REJECTED
                    emitEvent(CoordinatorEvent.TradeRejected(result.reason, signal.symbol))
                    updateState { it.copy(phase = CoordinatorPhase.IDLE) }
                    Result.failure(Exception(result.reason))
                }
                is OrderExecutionResult.Error -> {
                    SystemLogger.error("❌ BUILD #261 TRADE ERROR: ${signal.symbol} — ${result.exception.message}", result.exception)
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
            orderId = result.order.orderId,
            // BUILD #266: Real trading display fields
            leverage = config.defaultLeverage.coerceAtLeast(1),
            notionalValue = result.order.executedPrice * result.order.executedQuantity,
            marginUsed = TradingCosts.initialMargin(
                notionalValue = result.order.executedPrice * result.order.executedQuantity,
                leverage = config.defaultLeverage.coerceAtLeast(1)
            ),
            liquidationPrice = when (signal.direction) {
                TradeDirection.LONG -> TradingCosts.liquidationPriceLong(
                    entryPrice = result.order.executedPrice,
                    leverage = config.defaultLeverage.coerceAtLeast(1),
                    symbol = signal.symbol
                )
                TradeDirection.SHORT -> TradingCosts.liquidationPriceShort(
                    entryPrice = result.order.executedPrice,
                    leverage = config.defaultLeverage.coerceAtLeast(1),
                    symbol = signal.symbol
                )
            },
            entryFeesPaid = TradingCosts.entryCost(
                symbol = signal.symbol,
                notionalValue = result.order.executedPrice * result.order.executedQuantity
            ),
            peakUnrealizedPnL = 0.0,
            // BUILD #447: FIX - Read board from order.board field
            board = if (result.order.board == "HEDGE_FUND") BoardType.HEDGE_FUND else BoardType.MAIN
        )
        
        // BUILD #412: Use orderId directly as key (it already contains symbol in format: SYMBOL-SIDE-TIMESTAMP)
        // Previous bug: "${signal.symbol}_${result.order.orderId}" created "BTC/USDT_BTC/USDT-BUY-..." keys
        // Fix: result.order.orderId already IS "BTC/USDT-BUY-timestamp" format, no prefix needed
        val positionKey = result.order.orderId
        managedPositions[positionKey] = managedPosition
        lastTradeTime[signal.symbol] = System.currentTimeMillis()
        signal.status = SignalStatus.EXECUTED
        pendingSignals.remove(signal.id)
        
        // Update gamification
        // TODO: gamification.onTradeOpened - wire to GamificationCoordinator
        
        // BUILD #261: Update tracked capital on each trade (deduct cost or add proceeds)
        val tradeCost = result.order.executedPrice * result.order.executedQuantity * 1.001 // incl 0.1% fee
        if (signal.direction == TradeDirection.LONG) {
            positionManager.updateRealizedCapital(-tradeCost) // USDT spent
        } else {
            positionManager.updateRealizedCapital(tradeCost)  // USDT received
        }
        
        emitEvent(CoordinatorEvent.TradeExecuted(executedTrade))
        // BUILD #236: Surface trade execution in SystemLogger
        SystemLogger.trade("🚀 BUILD #236 TRADE EXECUTED: ${executedTrade.symbol} ${executedTrade.direction} @ ${String.format("%.2f", executedTrade.entryPrice)} qty=${String.format("%.4f", executedTrade.quantity)} [PAPER]")
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
                    
                    // BUILD #330: Phase 1 time-based position management
                    if (config.enableTimeBasedExits) {
                        val positionAgeHours = (System.currentTimeMillis() - position.entryTime) / (1000.0 * 60 * 60)
                        val profitPercent = (position.unrealizedPnL / position.entryPrice / position.quantity) * 100
                        
                        // Lingering Winner Exit: Close profitable positions after timeout
                        if (positionAgeHours >= config.winnerTimeoutHours && 
                            profitPercent >= config.winnerMinProfitPercent) {
                            SystemLogger.trade("⏰ LINGERING WINNER EXIT: $symbol (${String.format("%.1f", positionAgeHours)}h old, +${String.format("%.2f", profitPercent)}%)")
                            closePositionOnStop(symbol, position, "Lingering Winner (${String.format("%.1f", positionAgeHours)}h)")
                            continue
                        }
                        
                        // Dead Loser Exit: Close losing positions after timeout
                        if (positionAgeHours >= config.loserTimeoutHours && profitPercent < 0) {
                            SystemLogger.trade("⏰ DEAD LOSER EXIT: $symbol (${String.format("%.1f", positionAgeHours)}h old, ${String.format("%.2f", profitPercent)}%)")
                            closePositionOnStop(symbol, position, "Dead Loser (${String.format("%.1f", positionAgeHours)}h)")
                            continue
                        }
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
        // BUILD #126: Log position close
        SystemLogger.i(TAG, "🔚 CLOSING POSITION: $symbol")
        SystemLogger.i(TAG, "   Reason: $reason")
        SystemLogger.i(TAG, "   Entry Price: $${String.format("%.2f", position.entryPrice)}")
        SystemLogger.i(TAG, "   Current Price: $${String.format("%.2f", position.currentPrice)}")
        SystemLogger.i(TAG, "   Quantity: ${String.format("%.6f", position.quantity)}")
        SystemLogger.i(TAG, "   STAHL Level: ${position.stahlLevel}")
        
        val side = if (position.direction == TradeDirection.LONG) TradeSide.SELL else TradeSide.BUY
        val config = stahlEmergencyConfig  // CLIENT-CONFIGURED BEHAVIOR
        
        // BUILD #362: CLIENT-CONFIGURABLE UNLIMITED RETRY SYSTEM ✅
        // User sets retry behavior via STAHLEmergencyConfig
        // System NEVER gives up trying to close position
        // Human can intervene anytime, but system fights autonomously
        //
        // PHASE 1: RAPID RESPONSE (client configured)
        // PHASE 2: UNLIMITED RETRIES (if client enabled)
        //
        // REGULATORY BENEFIT:
        // Client controls emergency behavior → software tool, not financial service
        
        var attempt = 0
        var inRapidPhase = true
        
        while (true) {
            attempt++
            
            // First attempt? Sound emergency alarm
            if (attempt == 1) {
                if (config.enableEmergencySound) {
                    emitEvent(CoordinatorEvent.RiskAlert(
                        message = "🚨 STAHL STOP TRIGGERED: Emergency close for $symbol",
                        severity = AlertSeverity.CRITICAL
                    ))
                    SystemLogger.w(TAG, "🚨 EMERGENCY: STAHL stop triggered - attempting to close position")
                    SystemLogger.w(TAG, "   Position will be closed with UNLIMITED RETRIES until successful")
                    SystemLogger.w(TAG, "   You can manually close via UI anytime")
                }
            }
            
            // Execute close attempt
            val result = orderExecutor.executeMarketOrder(symbol, side, position.quantity)
            
            when (result) {
                is OrderExecutionResult.Success, is OrderExecutionResult.PartialFill -> {
                    // ✅ SUCCESS - Position closed!
                    if (attempt > 1) {
                        SystemLogger.i(TAG, "✅ STAHL emergency close SUCCESS on attempt $attempt")
                    }
                    
                    // Stop emergency alarm
                    emitEvent(CoordinatorEvent.RiskAlert(
                        message = "✅ STAHL emergency close successful after $attempt attempt(s)",
                        severity = AlertSeverity.INFO
                    ))
                    
                    // Continue with normal position close handling below
                    val finalResult = result
                    val exitPrice = (finalResult as? OrderExecutionResult.Success)?.order?.executedPrice
                        ?: (finalResult as? OrderExecutionResult.PartialFill)?.order?.executedPrice
                        ?: position.currentPrice
                    
                    val pnl = calculatePnL(position, exitPrice)
                    val pnlPercent = calculatePnLPercent(position, exitPrice)
                    
                    // BUILD #126: Log exit results
                    SystemLogger.i(TAG, "   Exit Price: $${String.format("%.2f", exitPrice)}")
                    SystemLogger.i(TAG, "   P&L: $${String.format("%+,.2f", pnl)} (${String.format("%+.2f", pnlPercent)}%)")
                    
                    // BUILD #126: Close position in position manager
                    // TODO BUILD #365: Fix this call - needs positionId and exitPrice
                    // positionManager.closePosition(positionId, exitPrice, "STAHL_STOP")
                    
                    // Emit position closed event
                    emitEvent(CoordinatorEvent.PositionClosed(symbol, pnl, pnlPercent))
                    
                    // TODO BUILD #365: Fix Trade entity persistence
                    // The Trade entity structure has changed - need to update this code
                    /*
                    tradeDao?.let { dao ->
                        val trade = TradeEntity(
                            id = "TRD-${System.currentTimeMillis()}",
                            symbol = symbol,
                            side = if (side == TradeSide.BUY) "BUY" else "SELL",
                            orderType = "MARKET",
                            quantity = position.quantity,
                            price = exitPrice,
                            fee = 0.0,
                            feeCurrency = "USDT",
                            realizedPnl = pnl,
                            realizedPnlPercent = pnlPercent,
                            exchange = "binance",
                            orderId = position.orderId,
                            timestamp = System.currentTimeMillis(),
                            stahlLevel = position.stahlLevel,
                            exitReason = "STAHL_STOP_LEVEL_${position.stahlLevel}"
                        )
                        
                        scope.launch {
                            try {
                                dao.insertTrade(trade)
                                SystemLogger.d(TAG, "💾 Trade saved to database")
                            } catch (e: Exception) {
                                SystemLogger.e(TAG, "❌ Failed to save trade: ${e.message}")
                            }
                        }
                    }
                    */
                    
                    SystemLogger.i(TAG, "════════════════════════════════════════════════════════════")
                    return  // ✅ EXIT - Position successfully closed
                }
                
                is OrderExecutionResult.Rejected, is OrderExecutionResult.Error -> {
                    // ❌ FAILED - Retry based on config
                    SystemLogger.w(TAG, "⚠️ STAHL close attempt $attempt FAILED: ${result}")
                    
                    // Check phase transition
                    if (inRapidPhase && attempt >= config.rapidRetryAttempts) {
                        if (!config.enableUnlimitedRetries) {
                            // CLIENT CHOSE MANUAL-ONLY MODE - stop here
                            SystemLogger.e(TAG, "🚨 CRITICAL: STAHL STOP FAILED AFTER ${config.rapidRetryAttempts} RAPID ATTEMPTS")
                            SystemLogger.e(TAG, "   Symbol: $symbol | Position stuck open")
                            SystemLogger.e(TAG, "   Client config: unlimited retries DISABLED")
                            SystemLogger.e(TAG, "   Manual intervention required: Call manualClosePosition($symbol)")
                            
                            emitEvent(CoordinatorEvent.RiskAlert(
                                message = "⚠️ STAHL close failed after ${config.rapidRetryAttempts} rapid attempts. Manual intervention required (per your settings).",
                                severity = AlertSeverity.CRITICAL
                            ))
                            
                            return  // CLIENT CHOICE - stop trying
                        }
                        
                        // Switch to persistent phase
                        inRapidPhase = false
                        SystemLogger.i(TAG, "📍 Rapid phase complete. Switching to UNLIMITED RETRY mode...")
                        SystemLogger.i(TAG, "   System will NEVER give up until position is closed")
                    }
                    
                    // Calculate delay before next attempt (client configured)
                    val delayMs = if (inRapidPhase) {
                        config.rapidRetryDelayMs
                    } else {
                        // Exponential backoff in persistent phase, capped at max
                        val backoffMultiplier = (attempt - config.rapidRetryAttempts)
                        minOf(
                            backoffMultiplier * config.persistentRetryDelayMs,
                            config.maxBackoffDelayMs
                        )
                    }
                    
                    SystemLogger.w(TAG, "⏳ Retry attempt ${attempt + 1} in ${delayMs}ms...")
                    delay(delayMs)
                    
                    // Re-alert periodically (client configured)
                    if (attempt % config.reAlertInterval == 0) {
                        emitEvent(CoordinatorEvent.RiskAlert(
                            message = "🚨 Still attempting STAHL emergency close for $symbol (attempt $attempt)",
                            severity = AlertSeverity.CRITICAL
                        ))
                        SystemLogger.w(TAG, "🔄 STILL FIGHTING: Attempt $attempt - system will NEVER give up")
                    }
                    
                    // Continue loop - try again
                }
            }
        }
        
        // We never reach here - loop only exits on success (or client manual-only config)
    }
    
    /**
     * BUILD #405: Handle order updates from OrderExecutor
     * Converts filled orders into positions and emits TradeExecuted events
     */
    private suspend fun handleOrderUpdate(update: OrderUpdate) {
        when (update) {
            is OrderUpdate.Filled -> {
                val order = update.order
                val positionKey = order.orderId
                
                // BUILD #438: CRITICAL FIX - Check if position already exists
                // Problem: executeTradeSignal() AND handleOrderUpdate() both create positions
                // Result: Every Main Board trade created 2 positions (1 real + 1 ghost)
                // Solution: Skip position creation if already exists
                if (managedPositions.containsKey(positionKey)) {
                    SystemLogger.system("⏭️ BUILD #438: Position $positionKey already exists — skipping duplicate creation")
                    SystemLogger.system("   This order was already processed by executeTradeSignal()")
                    return
                }
                
                SystemLogger.system("✅ BUILD #438: Position $positionKey does NOT exist — creating new position")
                
                // Create position in PositionManager
                val position = positionManager.openPosition(
                    symbol = order.symbol,
                    side = order.side,
                    quantity = order.executedQuantity,
                    entryPrice = order.executedPrice,
                    leverage = 1.0, // Paper trading uses 1x leverage
                    exchange = order.exchange.ifEmpty { "PAPER" },
                    useStahl = true
                )
                
                // BUILD #409: Add to managedPositions so UI can see it
                // BUILD #412: Use orderId directly (already in format: SYMBOL-SIDE-TIMESTAMP)
                // BUILD #429: Read board from order.board field with diagnostic logging
                
                // Diagnostic: Log the raw order.board value
                SystemLogger.system("🔍 BUILD #429: Position creation for ${order.symbol}")
                SystemLogger.system("   order.orderId = ${order.orderId}")
                SystemLogger.system("   order.board (raw) = '${order.board}'")
                SystemLogger.system("   order.board == null? ${order.board == null}")
                
                val board = when {
                    // BUILD #447: FIX - Read from order.board field directly
                    order.board != null -> {
                        val detectedBoard = if (order.board == "HEDGE_FUND") BoardType.HEDGE_FUND else BoardType.MAIN
                        SystemLogger.system("   ✅ Board from order.board: $detectedBoard")
                        detectedBoard
                    }
                    // Fallback heuristic if board field not set
                    positionKey.contains("HEDGE", ignoreCase = true) -> {
                        SystemLogger.system("   ⚠️ Board from orderId heuristic: HEDGE_FUND")
                        BoardType.HEDGE_FUND
                    }
                    else -> {
                        SystemLogger.system("   ⚠️ Board defaulted to: MAIN")
                        BoardType.MAIN
                    }
                }
                
                SystemLogger.system("   🎯 FINAL BOARD ASSIGNMENT: $board")
                
                val managedPosition = ManagedPosition(
                    symbol = order.symbol,
                    direction = if (order.side == TradeSide.BUY || order.side == TradeSide.LONG) 
                        TradeDirection.LONG else TradeDirection.SHORT,
                    entryPrice = order.executedPrice,
                    currentPrice = order.executedPrice,
                    quantity = order.executedQuantity,
                    currentStop = position.currentStopPrice,
                    currentTarget = position.takeProfitPrice,
                    stahlLevel = 0,
                    unrealizedPnL = 0.0,
                    unrealizedPnLPercent = 0.0,
                    entryTime = order.timestamp,
                    orderId = order.orderId,
                    leverage = position.leverage.toInt().coerceAtLeast(1),
                    notionalValue = order.executedPrice * order.executedQuantity,
                    marginUsed = position.margin,
                    liquidationPrice = position.liquidationPrice ?: 0.0,
                    entryFeesPaid = position.fees,
                    peakUnrealizedPnL = 0.0,
                    board = board  // BUILD #428: Tag position with correct board
                )
                
                // BUILD #435: Enhanced diagnostics - track board attribution AND value calculation
                SystemLogger.system("✅ BUILD #435: Position CREATED with full details")
                SystemLogger.system("   positionKey = $positionKey")
                SystemLogger.system("   managedPosition.board = ${managedPosition.board}")
                SystemLogger.system("   managedPosition.symbol = ${managedPosition.symbol}")
                SystemLogger.system("   managedPosition.quantity = ${managedPosition.quantity}")
                SystemLogger.system("   managedPosition.entryPrice = ${managedPosition.entryPrice}")
                SystemLogger.system("   managedPosition.notionalValue = ${managedPosition.notionalValue}")
                SystemLogger.system("   managedPosition.marginUsed = ${managedPosition.marginUsed}")
                SystemLogger.system("   Total managedPositions count BEFORE add = ${managedPositions.size}")
                
                managedPositions[positionKey] = managedPosition
                
                SystemLogger.system("   Total managedPositions count AFTER add = ${managedPositions.size}")
                SystemLogger.system("   MAIN board positions = ${managedPositions.values.count { it.board == BoardType.MAIN }}")
                SystemLogger.system("   HEDGE board positions = ${managedPositions.values.count { it.board == BoardType.HEDGE_FUND }}")
                
                updatePositionsState()
                
                // Convert Position to ExecutedTrade for event emission
                val executedTrade = ExecutedTrade(
                    id = order.orderId,
                    symbol = order.symbol,
                    direction = if (order.side == TradeSide.BUY || order.side == TradeSide.LONG) 
                        TradeDirection.LONG else TradeDirection.SHORT,
                    entryPrice = order.executedPrice,
                    quantity = order.executedQuantity,
                    stopLoss = position.initialStopPrice,
                    takeProfit = position.takeProfitPrice,
                    orderId = order.orderId,
                    timestamp = order.timestamp,
                    fromSignalId = null,
                    wasAutonomous = true // Assume autonomous for now
                )
                
                // BUILD #437: CRITICAL FIX - Emit event AFTER position added to map
                // Previous bug: Event emitted before managedPositions.put() completed
                // Result: Dashboard update handler ran BEFORE position existed (race condition)
                // Fix: Guarantee position exists in map before dashboard reads it
                emitEvent(CoordinatorEvent.TradeExecuted(executedTrade))
                SystemLogger.system("🔔 BUILD #437: TradeExecuted event EMITTED (AFTER position added) — " +
                    "Dashboard will now see the position when it queries managedPositions")
                
                SystemLogger.system("✅ BUILD #405: Position created from filled order — ${order.symbol} ${order.side} " +
                    "${order.executedQuantity} @ \$${String.format("%.4f", order.executedPrice)}")
                SystemLogger.system("   Position ID: ${position.id} | Stop: \$${String.format("%.4f", position.initialStopPrice)} | " +
                    "Target: \$${String.format("%.4f", position.takeProfitPrice)}")
            }
            is OrderUpdate.PartiallyFilled -> {
                SystemLogger.system("⚠️ BUILD #405: Partial fill — ${update.order.symbol} " +
                    "${update.order.executedQuantity}/${update.order.quantity} filled")
                // TODO: Handle partial fills - for now, treat same as filled
                handleOrderUpdate(OrderUpdate.Filled(update.order))
            }
            is OrderUpdate.Rejected -> {
                SystemLogger.system("❌ BUILD #405: Order rejected — ${update.request.symbol} ${update.request.side}: ${update.reason}")
                emitEvent(CoordinatorEvent.TradeRejected(update.reason, update.request.symbol))
            }
            is OrderUpdate.Cancelled -> {
                SystemLogger.system("🚫 BUILD #405: Order cancelled — ${update.orderId}")
            }
            is OrderUpdate.Failed -> {
                SystemLogger.system("❌ BUILD #405: Order failed — ${update.request.symbol}: ${update.error}")
                emitEvent(CoordinatorEvent.TradeRejected(update.error, update.request.symbol))
            }
            else -> {
                // Submitted, etc. - just log
                SystemLogger.system("ℹ️ BUILD #405: Order update — $update")
            }
        }
    }
    
    private fun handlePositionEvent(event: PositionEvent) {
        when (event) {
            is PositionEvent.StopLossHit -> {
                scope.launch {
                    // BUILD #270: Look up by positionKey
                    val key270 = "${event.position.symbol}_${event.position.id}"
                    closePositionOnStop(event.position.symbol,
                        managedPositions[key270] ?: return@launch,
                        "STAHL Stop Hit")
                }
            }
            is PositionEvent.TakeProfitHit -> {
                scope.launch {
                    val key270 = "${event.position.symbol}_${event.position.id}"
                    closePositionOnStop(event.position.symbol,
                        managedPositions[key270] ?: return@launch,
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
            
            // BUILD #270: Update by positionKey
            val stahlKey = "${position.symbol}_${position.orderId}"
            managedPositions[stahlKey] = position.copy(
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
        val repository = boardDecisionRepository ?: run {
            // BUILD #263: Repository not wired — XAI decisions not persisted this cycle
            return
        }
        
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
            
            // BUILD #452: NaN guards - prevent SQL constraint failures
            val confidence = consensus.confidence
            val weightedScore = consensus.weightedScore
            
            if (!confidence.isFinite() || !weightedScore.isFinite()) {
                SystemLogger.w(TAG, "⚠️ BUILD #452: Skipping XAI persistence for $symbol — confidence or weightedScore is NaN/Infinite")
                SystemLogger.w(TAG, "   confidence=$confidence, weightedScore=$weightedScore")
                SystemLogger.w(TAG, "   This indicates DQN confidence calculation needs debugging")
                return
            }
            
            repository.save(decisionRecord)
            SystemLogger.d("TradingCoordinator", "🧠 BUILD #263 XAI: Board decision persisted — $symbol ${consensus.finalDecision} conf=${String.format("%.0f", consensus.confidence * 100)}% [${consensus.opinions.size} votes]")
            
        } catch (e: Exception) {
            SystemLogger.error("❌ BUILD #263 XAI: Failed to persist board decision for $symbol: ${e.message}", e)
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
        // BUILD #262: Synchronized copies to prevent race condition with addCandle()
        val highs = synchronized(buffer) { buffer.highs.toList() }
        val lows = synchronized(buffer) { buffer.lows.toList() }
        val closes = synchronized(buffer) { buffer.closes.toList() }
        
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
            managedPositions.size >= config.maxConcurrentPositions -> 
                "REJECTED" to "Maximum concurrent positions reached (${config.maxConcurrentPositions})"
            config.mode == TradingMode.AUTONOMOUS || config.mode == TradingMode.SCALPING -> 
                "TRADE_EXECUTED" to "Auto mode - executing ${consensus.finalDecision}"
            else -> 
                "SIGNAL_GENERATED" to "Signal generated for user confirmation"
        }
    }
    
    private fun isSignalActionable(consensus: BoardConsensus): Boolean {
        SystemLogger.d(TAG, "🔍 BUILD #400: isSignalActionable() check for ${consensus.finalDecision}")
        SystemLogger.d(TAG, "  confidence=${String.format("%.1f", consensus.confidence * 100)}% (need ${String.format("%.1f", config.minConfidenceToTrade * 100)}%)")
        SystemLogger.d(TAG, "  unanimousCount=${consensus.unanimousCount} (need ${config.minBoardAgreement})")
        SystemLogger.d(TAG, "  finalDecision=${consensus.finalDecision} (HOLD blocks)")
        
        if (consensus.confidence < config.minConfidenceToTrade) {
            SystemLogger.d(TAG, "  ❌ BLOCKED: Confidence too low")
            return false
        }
        if (consensus.unanimousCount < config.minBoardAgreement) {
            SystemLogger.d(TAG, "  ❌ BLOCKED: Not enough agreement")
            return false
        }
        if (consensus.finalDecision == BoardVote.HOLD) {
            SystemLogger.d(TAG, "  ❌ BLOCKED: Decision is HOLD")
            return false
        }
        
        SystemLogger.d(TAG, "  ✅ ACTIONABLE: All checks passed!")
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
        
        val portfolioValue = getPortfolioValue()  // BUILD #298: Use correct formula
        
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
        // BUILD #270: Multiple positions per symbol — board decides, no artificial limit
        // Max concurrent positions still applies globally (configurable)
        if (managedPositions.size >= config.maxConcurrentPositions) {
            return "Maximum concurrent positions reached (${config.maxConcurrentPositions})"
        }
        return null
    }
    
    private fun updatePositionPrice(position: ManagedPosition, newPrice: Double): ManagedPosition {
        val pnl = calculatePnL(position, newPrice)
        val pnlPercent = calculatePnLPercent(position, newPrice)
        // BUILD #266: Track peak P&L for STAHL Stair Stop™ progression
        val newPeak = maxOf(position.peakUnrealizedPnL, pnl)
        
        return position.copy(
            currentPrice = newPrice,
            unrealizedPnL = pnl,
            unrealizedPnLPercent = pnlPercent,
            peakUnrealizedPnL = newPeak
        )
    }
    
    private fun calculatePnL(position: ManagedPosition, exitPrice: Double): Double {
        // BUILD #266: Net P&L after fees and spread — real-world accurate
        val notionalValue = position.quantity * position.entryPrice
        return TradingCosts.netUnrealisedPnL(
            symbol = position.symbol,
            direction = position.direction,
            entryPrice = position.entryPrice,
            currentPrice = exitPrice,
            notionalValue = notionalValue
        )
    }
    
    private fun calculatePnLPercent(position: ManagedPosition, exitPrice: Double): Double {
        // BUILD #266: P&L % relative to initial margin posted (not notional)
        // This gives the trader a true picture of return on capital deployed
        val leverage = position.leverage.coerceAtLeast(1)
        val marginPosted = (position.quantity * position.entryPrice) / leverage
        if (marginPosted <= 0.0) return 0.0
        val pnl = calculatePnL(position, exitPrice)
        return (pnl / marginPosted) * 100.0
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
            
            // BUILD #441: Autosave DQN weights every cleanup cycle (every 5 minutes)
            // This ensures learned intelligence persists even if app crashes
            try {
                saveDQNWeights()
                SystemLogger.d(TAG, "💾 BUILD #441: DQN weights autosaved during cleanup cycle")
            } catch (e: Exception) {
                SystemLogger.e(TAG, "❌ BUILD #441: DQN autosave failed: ${e.message}")
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
    
    // ========================================================================
    // BUILD #335: DQN PERSISTENCE
    // ========================================================================
    
    /**
     * Save all DQN Q-tables to database.
     * Called on app pause/stop or periodically (every 5 min).
     */
    suspend fun saveDQNStates() {
        dqnStateDao ?: return  // No DAO = no persistence
        
        try {
            val states = mutableListOf<com.miwealth.sovereignvantage.core.ml.DQNStateEntity>()
            
            // Iterate through all DQN instances
            perMemberDqn.forEach { (key, dqn) ->
                // Extract symbol and member name from key
                val parts = key.split("_", limit = 2)
                if (parts.size != 2) return@forEach
                
                val symbol = parts[0]
                val memberName = parts[1]
                
                // BUILD #336: Get neural network state from DQN
                val dqnState = dqn.saveState()
                
                // BUILD #336: Convert state map to JSON
                val stateJson = dqnState.entries.joinToString(",", "{", "}") { (k, v) ->
                    "\"$k\":\"${v.replace("\"", "\\\"")}\""
                }
                
                // Create entity
                val entity = com.miwealth.sovereignvantage.core.ml.DQNStateEntity(
                    dqnKey = key,
                    symbol = symbol,
                    memberName = memberName,
                    qTableJson = stateJson,  // Contains neural network weights
                    lastUpdated = System.currentTimeMillis(),
                    trainingEpisodes = dqnState["episodeCount"]?.toIntOrNull() ?: 0,
                    learningRate = dqn.getLearningRate()
                )
                
                states.add(entity)
            }
            
            // Batch save to database
            if (states.isNotEmpty()) {
                dqnStateDao.saveStates(states)
                SystemLogger.i(TAG, "💾 BUILD #336: Saved ${states.size} DQN neural networks to database")
                Log.i(TAG, "DQN persistence: Saved ${states.size} neural network states")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save DQN states", e)
            SystemLogger.e(TAG, "❌ BUILD #335: DQN save failed: ${e.message}")
        }
    }
    
    /**
     * Load all DQN neural network states from database.
     * Called on TradingCoordinator initialization.
     */
    suspend fun loadDQNStates() {
        dqnStateDao ?: return  // No DAO = no persistence
        
        try {
            val states = dqnStateDao.getAllStates()
            var loadedCount = 0
            
            SystemLogger.system("📚 BUILD #408: Loading DQN weights from previous sessions...")
            SystemLogger.system("   Found ${states.size} saved neural networks in database")
            
            states.forEach { entity ->
                // Get DQN instance (creates if doesn't exist)
                val dqn = perMemberDqn.getOrPut(entity.dqnKey) {
                    // BUILD #336: Create fresh DQN (neural network will be loaded from saved state)
                    DQNTrader(
                        learningRate = entity.learningRate
                    )
                }
                
                // BUILD #336: Parse JSON state map
                try {
                    val stateMap = parseStateJson(entity.qTableJson)
                    
                    // BUILD #336: Load neural network weights + hyperparameters
                    dqn.loadState(stateMap)
                    loadedCount++
                    
                    SystemLogger.d(TAG, "📥 BUILD #336: Loaded neural network for ${entity.dqnKey} (${entity.trainingEpisodes} episodes)")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse DQN state for ${entity.dqnKey}", e)
                }
            }
            
            if (loadedCount > 0) {
                SystemLogger.system("✅ BUILD #408: Successfully loaded $loadedCount DQN neural networks!")
                SystemLogger.system("   Your AI has retained all learning from previous trading sessions")
                SystemLogger.i(TAG, "📥 BUILD #336: Loaded $loadedCount DQN neural networks from database")
                Log.i(TAG, "DQN persistence: Restored $loadedCount neural network states from previous session")
            } else {
                SystemLogger.system("ℹ️ BUILD #408: No previous DQN weights found - starting fresh")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load DQN states", e)
            SystemLogger.e(TAG, "❌ BUILD #335: DQN load failed: ${e.message}")
        }
    }
    
    /**
     * Parse JSON Q-table string back to Map<String, Double>
     */
    /**
     * BUILD #336: Parses JSON state map (neural network weights are strings)
     * Format: {"key":"value","key2":"value2"}
     */
    private fun parseStateJson(json: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        
        // Simple JSON parser for {"key":"value","key2":"value2"}
        val content = json.trim().removeSurrounding("{", "}")
        if (content.isEmpty()) return map
        
        // Split on commas, but handle escaped quotes in values
        var currentKey = ""
        var currentValue = StringBuilder()
        var inValue = false
        var escapeNext = false
        
        for (char in content) {
            when {
                escapeNext -> {
                    currentValue.append(char)
                    escapeNext = false
                }
                char == '\\' && inValue -> {
                    escapeNext = true
                }
                char == '"' -> {
                    if (!inValue && currentKey.isEmpty()) {
                        // Start of key
                        currentKey = ""
                    } else if (!inValue) {
                        // End of key, expect ":"
                        inValue = false
                    } else {
                        // End of value
                        map[currentKey] = currentValue.toString()
                        currentKey = ""
                        currentValue.clear()
                        inValue = false
                    }
                }
                char == ':' && !inValue -> {
                    // Skip colon and prepare for value
                    inValue = true
                }
                char == ',' && !inValue -> {
                    // Skip comma between pairs
                }
                inValue -> {
                    if (char != '"') {  // Skip opening quote of value
                        currentValue.append(char)
                    }
                }
                else -> {
                    if (currentKey.isEmpty() && char != '"') {
                        currentKey += char
                    }
                }
            }
        }
        
        return map
    }
    
    // ============================================================================
    // BUILD #366: DQN WEIGHT PERSISTENCE (FILE-BASED)
    // ============================================================================
    
    /**
     * BUILD #366: Save all DQN neural network weights to device storage.
     * Called when app stops to preserve learned intelligence across sessions.
     * 
     * Saves 60 weight files (~8.5KB each, 510KB total):
     * - 8 Main Board members × 4 symbols = 32 files
     * - 7 Hedge Fund members × 4 symbols = 28 files
     * 
     * File naming: "{symbol}_{memberName}.weights"
     * Example: "BTC/USDT_Arthur.weights", "ETH/USDT_Soros.weights"
     */
    fun saveDQNWeights() {
        try {
            var savedCount = 0
            var failedCount = 0
            var backupSavedCount = 0
            var backupFailedCount = 0
            
            perMemberDqn.forEach { (key, dqn) ->
                // BUILD #439: Extract symbol from key (format: "BTC/USDT_Nexus")
                val symbol = key.substringBefore("_").replace("/", "")  // "BTC/USDT" → "BTCUSDT"
                
                // BUILD #442: Sanitize key for filename (replace "/" with "_")
                // key = "BTC/USDT_Nexus" → safeKey = "BTC_USDT_Nexus"
                val safeKey = key.replace("/", "_")
                
                // Save to internal storage (primary location)
                val internalSymbolDir = File(dqnWeightsDir, symbol).apply { mkdirs() }
                val internalFile = File(internalSymbolDir, "$safeKey.weights")
                
                // BUILD #442: Also save to external backup (survives reinstall)
                val backupSymbolDir = File(dqnBackupDir, symbol).apply { mkdirs() }
                val backupFile = File(backupSymbolDir, "$safeKey.weights")
                
                try {
                    // Get weights from the neural network (not DQNTrader directly)
                    val weightsMap = dqn.getPolicyNetwork().saveWeights()
                    
                    // Convert to JSON-like format
                    val jsonContent = weightsMap.entries.joinToString("\n") { (k, v) ->
                        "$k=$v"
                    }
                    
                    // Save to internal storage
                    internalFile.writeText(jsonContent)
                    savedCount++
                    
                    // BUILD #434: Save to external backup
                    try {
                        backupFile.writeText(jsonContent)
                        backupSavedCount++
                    } catch (e: Exception) {
                        backupFailedCount++
                        SystemLogger.w(TAG, "⚠️ BUILD #434: Failed to save backup DQN $key: ${e.message}")
                    }
                    
                    SystemLogger.d(TAG, "💾 BUILD #434: Saved DQN weights for $key (internal + backup)")
                } catch (e: Exception) {
                    failedCount++
                    SystemLogger.e(TAG, "❌ BUILD #434: Failed to save DQN $key: ${e.message}")
                }
            }
            
            if (savedCount > 0) {
                SystemLogger.i(TAG, "💾 BUILD #434: Saved $savedCount DQN weight files to internal storage (${failedCount} failed)")
                SystemLogger.i(TAG, "💾 BUILD #434: Saved $backupSavedCount DQN backups to ${dqnBackupDir.absolutePath} (${backupFailedCount} failed)")
                Log.i(TAG, "DQN persistence: Dual-location save complete")
            }
        } catch (e: Exception) {
            SystemLogger.e(TAG, "❌ BUILD #434: DQN weight save failed: ${e.message}")
        }
    }
    
    /**
     * BUILD #366: Load all DQN neural network weights from device storage.
     * Called after bootstrap to restore learned intelligence from previous sessions.
     * 
     * DQNs start as "Novice (0 steps)" with random weights.
     * After loading, they become "Experienced" with learned patterns.
     * 
     * Intelligence compounds across sessions instead of resetting.
     */
    fun loadDQNWeights() {
        try {
            var loadedCount = 0
            var freshCount = 0
            var restoredFromBackupCount = 0
            
            perMemberDqn.forEach { (key, dqn) ->
                // BUILD #439: Extract symbol from key (format: "BTC/USDT_Nexus")
                val symbol = key.substringBefore("_").replace("/", "")  // "BTC/USDT" → "BTCUSDT"
                
                // BUILD #442: Sanitize key for filename (replace "/" with "_")
                val safeKey = key.replace("/", "_")
                
                val internalFile = File(File(dqnWeightsDir, symbol), "$safeKey.weights")
                val backupFile = File(File(dqnBackupDir, symbol), "$safeKey.weights")
                
                var loadSuccess = false
                var loadedFromBackup = false
                
                // BUILD #450: Try loading from internal storage first
                if (internalFile.exists()) {
                    try {
                        val weightsMap = internalFile.readText()
                            .lines()
                            .filter { it.contains("=") }
                            .associate {
                                val (k, v) = it.split("=", limit = 2)
                                k to v
                            }
                        
                        dqn.getPolicyNetwork().loadWeights(weightsMap)
                        loadedCount++
                        loadSuccess = true
                        SystemLogger.d(TAG, "📂 BUILD #450: Loaded DQN weights for $key from internal storage")
                    } catch (e: Exception) {
                        // BUILD #450: Internal file corrupted - try backup!
                        SystemLogger.w(TAG, "⚠️ BUILD #450: Internal weights corrupted for $key: ${e.message}")
                        SystemLogger.i(TAG, "🔄 BUILD #450: Attempting to restore from backup...")
                    }
                }
                
                // BUILD #450: If internal missing or corrupted, try backup
                if (!loadSuccess && backupFile.exists()) {
                    try {
                        val weightsMap = backupFile.readText()
                            .lines()
                            .filter { it.contains("=") }
                            .associate {
                                val (k, v) = it.split("=", limit = 2)
                                k to v
                            }
                        
                        dqn.getPolicyNetwork().loadWeights(weightsMap)
                        loadedCount++
                        loadSuccess = true
                        loadedFromBackup = true
                        restoredFromBackupCount++
                        SystemLogger.i(TAG, "✅ BUILD #450: Restored $key from backup (internal ${if (internalFile.exists()) "corrupted" else "missing"})")
                        
                        // Copy backup to internal for future speed
                        try {
                            backupFile.copyTo(internalFile, overwrite = true)  // BUILD #450: Overwrite corrupted file
                            SystemLogger.d(TAG, "💾 BUILD #450: Replaced ${if (internalFile.exists()) "corrupted" else "missing"} internal file with backup for $key")
                        } catch (e: Exception) {
                            SystemLogger.w(TAG, "⚠️ BUILD #450: Failed to copy backup to internal: ${e.message}")
                        }
                    } catch (e: Exception) {
                        // Both locations failed
                        freshCount++
                        SystemLogger.e(TAG, "❌ BUILD #450: Both internal and backup failed for $key: ${e.message}")
                    }
                }
                
                // BUILD #450: Neither location worked - start fresh
                if (!loadSuccess) {
                    if (!internalFile.exists() && !backupFile.exists()) {
                        SystemLogger.d(TAG, "ℹ️ BUILD #450: No saved weights for $key (fresh DQN)")
                    }
                    freshCount++
                }
            }
            
            if (loadedCount > 0) {
                SystemLogger.i(TAG, "🧠 BUILD #434: Loaded $loadedCount DQN weight files, $freshCount fresh DQNs")
                if (restoredFromBackupCount > 0) {
                    SystemLogger.i(TAG, "✨ BUILD #434: Restored $restoredFromBackupCount DQNs from backup (survived reinstall!)")
                }
                SystemLogger.i(TAG, "✨ BUILD #434: DQN intelligence restored - learning persists across sessions!")
                Log.i(TAG, "DQN persistence: Restored neural network weights")
            } else {
                SystemLogger.i(TAG, "🆕 BUILD #434: All DQNs starting fresh (no saved weights found)")
            }
        } catch (e: Exception) {
            SystemLogger.e(TAG, "❌ BUILD #434: DQN weight load failed: ${e.message}")
        }
    }
    
    /**
     * BUILD #443: Export DQN weights - returns path to /Download/DQN/ directory
     * 
     * DQN weights are ALREADY auto-saved to /Download/DQN/ on every pause/background.
     * This function just ensures current state is saved and returns the path for UI display.
     * 
     * Location: /storage/emulated/0/Download/DQN/
     * Structure: BTCUSDT/BTC_USDT_Arthur.weights, ETHUSDT/ETH_USDT_Sentinel.weights, etc.
     * 
     * @return Path to DQN directory or null if failed
     */
    suspend fun exportDQNWeights(): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Save current state to both internal and external (/Download/DQN/)
                saveDQNWeights()
                
                // Count total files
                var fileCount = 0
                fun countFiles(dir: File) {
                    dir.listFiles()?.forEach { file ->
                        when {
                            file.isDirectory -> countFiles(file)
                            file.extension == "weights" -> fileCount++
                        }
                    }
                }
                countFiles(dqnBackupDir)
                
                val sizeKB = dqnBackupDir.walkTopDown()
                    .filter { it.isFile && it.extension == "weights" }
                    .sumOf { it.length() } / 1024
                
                SystemLogger.system("💾 BUILD #443: DQN WEIGHTS EXPORTED!")
                SystemLogger.system("   Location: ${dqnBackupDir.absolutePath}")
                SystemLogger.system("   Size: ${sizeKB}KB | Files: $fileCount neural networks")
                SystemLogger.system("   ℹ️  These weights auto-save every time you pause/background the app")
                
                dqnBackupDir.absolutePath
            } catch (e: Exception) {
                SystemLogger.e(TAG, "❌ BUILD #443: Export failed: ${e.message}")
                null
            }
        }
    }
    
    /**
     * BUILD #443: Import DQN weights from ZIP backup or /Download/DQN/ folder
     * 
     * Supports two import sources:
     * 1. ZIP file (from share/backup)
     * 2. Direct /Download/DQN/ folder (from another device)
     * 
     * @param sourcePath Full path to ZIP file or /Download/DQN/ directory
     * @return Number of weight files successfully imported
     */
    suspend fun importDQNWeights(sourcePath: String): Int {
        return withContext(Dispatchers.IO) {
            try {
                var importedCount = 0
                val sourceFile = File(sourcePath)
                
                when {
                    sourceFile.extension == "zip" -> {
                        // Import from ZIP backup
                        // BUILD #443: Extract ZIP preserving subdirectory structure
                        java.util.zip.ZipInputStream(sourceFile.inputStream()).use { zip ->
                            var entry = zip.nextEntry
                            while (entry != null) {
                                if (entry.name.endsWith(".weights") && !entry.isDirectory) {
                                    // Preserve subdirectory structure (e.g., "BTCUSDT/BTC_USDT_Arthur.weights")
                                    val targetFile = File(dqnWeightsDir, entry.name)
                                    targetFile.parentFile?.mkdirs()  // Create subdirectories if needed
                                    targetFile.outputStream().use { output ->
                                        zip.copyTo(output)
                                    }
                                    importedCount++
                                }
                                zip.closeEntry()
                                entry = zip.nextEntry
                            }
                        }
                    }
                    sourceFile.isDirectory -> {
                        // Import from /Download/DQN/ folder (copy all .weights files)
                        fun copyWeights(srcDir: File, destDir: File) {
                            srcDir.listFiles()?.forEach { file ->
                                when {
                                    file.isDirectory -> {
                                        val newDest = File(destDir, file.name).apply { mkdirs() }
                                        copyWeights(file, newDest)
                                    }
                                    file.extension == "weights" -> {
                                        file.copyTo(File(destDir, file.name), overwrite = true)
                                        importedCount++
                                    }
                                }
                            }
                        }
                        copyWeights(sourceFile, dqnWeightsDir)
                    }
                }
                
                // Load the imported weights
                if (importedCount > 0) {
                    loadDQNWeights()
                    SystemLogger.system("✅ BUILD #443: DQN BACKUP RESTORED!")
                    SystemLogger.system("   Imported $importedCount neural networks")
                    SystemLogger.system("   Your AI has resumed learning from the backup state")
                }
                
                importedCount
            } catch (e: Exception) {
                SystemLogger.e(TAG, "❌ BUILD #443: Import failed: ${e.message}")
                0
            }
        }
    }
    
    /**
     * BUILD #443: Share DQN weights directory via Android share sheet
     * Creates a temporary ZIP of /Download/DQN/ and shares it
     * 
     * @param context Android context for share intent
     * @return URI of shared ZIP file or null if failed
     */
    suspend fun shareDQNWeights(context: android.content.Context): android.net.Uri? {
        return withContext(Dispatchers.IO) {
            try {
                // First ensure current state is saved
                saveDQNWeights()
                
                // Create temporary ZIP in cache for sharing
                val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                    .format(java.util.Date())
                val zipFile = File(context.cacheDir, "SovereignVantage_DQN_$timestamp.zip")
                
                // Zip the entire /Download/DQN/ directory
                var fileCount = 0
                java.util.zip.ZipOutputStream(zipFile.outputStream()).use { zip ->
                    fun addDirectoryToZip(dir: File, basePath: String = "") {
                        dir.listFiles()?.forEach { file ->
                            when {
                                file.isDirectory -> {
                                    addDirectoryToZip(file, "$basePath${file.name}/")
                                }
                                file.extension == "weights" -> {
                                    val entryName = "$basePath${file.name}"
                                    zip.putNextEntry(java.util.zip.ZipEntry(entryName))
                                    file.inputStream().use { it.copyTo(zip) }
                                    zip.closeEntry()
                                    fileCount++
                                }
                            }
                        }
                    }
                    addDirectoryToZip(dqnBackupDir)
                }
                
                // Create content URI using FileProvider
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    zipFile
                )
                
                SystemLogger.i(TAG, "📤 BUILD #443: DQN weights ready to share ($fileCount files, ${zipFile.length() / 1024}KB)")
                uri
            } catch (e: Exception) {
                SystemLogger.e(TAG, "❌ BUILD #443: Share preparation failed: ${e.message}")
                null
            }
        }
    }
}


// ============================================================================
// STAHL STAIR STOP EXTENSIONS
// ============================================================================

@Suppress("DEPRECATION")
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

@Suppress("DEPRECATION")
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

/**
 * BUILD #452: Extension function for safe Double operations
 * Checks if a Double is neither NaN nor Infinite
 */
private fun Double.isFinite(): Boolean {
    return !this.isNaN() && !this.isInfinite()
}
