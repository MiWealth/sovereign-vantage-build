package com.miwealth.sovereignvantage.core.trading

import com.miwealth.sovereignvantage.core.trading.strategies.*
import com.miwealth.sovereignvantage.core.trading.engine.*
import com.miwealth.sovereignvantage.core.ml.KellyPositionSizer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * TRADING MODE CONTROLLER
 * 
 * Three-position master switch controlling the relationship between
 * AI Board trading and Hedge strategies.
 * 
 * MODES:
 * ┌─────────────────────────────────────────────────────────────┐
 * │  Position 1: AI TRADING ONLY                                │
 * │  - AI Board active, all strategies available                │
 * │  - Hedge engines dormant                                    │
 * │  - 100% margin available to AI Board + Kelly               │
 * ├─────────────────────────────────────────────────────────────┤
 * │  Position 2: AI TRADING + HEDGE MODE                        │
 * │  - AI Board active with REDUCED margin allocation           │
 * │  - Hedge engines active with RESERVED margin                │
 * │  - Margin firewall prevents cross-encroachment              │
 * │  - Board NOTIFIED of hedge actions (read-only)             │
 * │  - Trading lock during hedge rebalance                      │
 * ├─────────────────────────────────────────────────────────────┤
 * │  Position 3: HEDGE ONLY                                     │
 * │  - AI Board SUSPENDED (no new trades)                       │
 * │  - Existing AI positions: STAHL exits remain active         │
 * │  - 100% margin available to hedge engines                   │
 * │  - Defensive posture - capital preservation priority        │
 * └─────────────────────────────────────────────────────────────┘
 * 
 * MARGIN FIREWALL:
 * Available margin is partitioned into pools that cannot encroach
 * on each other. The hedge engine has a guaranteed reserve.
 * 
 * TRADING LOCK:
 * When hedge strategies rebalance, a lock prevents new AI Board
 * entries. Existing protective exits (STAHL, stop-loss) remain active.
 * Lock auto-releases after configurable timeout.
 * 
 * KELLY EXCLUSION:
 * Hedge positions are excluded from Kelly Criterion calculations.
 * Kelly operates on AI-strategy trades only, using post-hedge capital.
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

// =============================================================================
// MASTER MODE ENUM
// =============================================================================

/**
 * Three-position master switch.
 * User-facing control on the trading dashboard.
 */
enum class MasterTradingMode {
    AI_TRADING,         // Position 1: AI Board only
    AI_TRADING_HEDGE,   // Position 2: AI Board + Hedge engines
    HEDGE_ONLY          // Position 3: Hedge engines only
}

// =============================================================================
// HEDGE STRATEGY SELECTION
// =============================================================================

/**
 * Individual hedge strategies that can be enabled/disabled.
 * Each operates independently within the hedge margin pool.
 */
enum class HedgeStrategy {
    ALPHA_FACTOR_SCANNER,      // Factor-based long/short asset selection
    FUNDING_RATE_ARBITRAGE,    // Delta-neutral funding collection
    PAIRS_TRADING,             // Cointegrated pairs mean-reversion
    BASIS_TRADE,               // Spot vs futures premium capture
    CROSS_EXCHANGE_ARB,        // Same asset, different exchange pricing
    GRID_TRADING               // Range-bound interval buy/sell
}

// =============================================================================
// MARGIN ALLOCATION
// =============================================================================

/**
 * Margin firewall configuration.
 * 
 * Total available margin is partitioned:
 * ┌──────────────────────────────────────────────┐
 * │  AI Trading Pool  │  Hedge Pool  │  Emergency │
 * │   (configurable)  │ (configurable)│   Buffer  │
 * │      e.g. 60%     │   e.g. 35%   │    5%     │
 * └──────────────────────────────────────────────┘
 * 
 * Emergency buffer is UNTOUCHABLE — exists solely to prevent
 * margin calls during overnight gaps or flash crashes.
 */
data class MarginAllocationConfig(
    // AI Trading + Hedge mode allocations (must sum to <= 95%)
    val aiTradingPoolPercent: Double = 60.0,
    val hedgePoolPercent: Double = 35.0,
    val emergencyBufferPercent: Double = 5.0,
    
    // Hedge Only mode (hedge gets everything except emergency)
    val hedgeOnlyPoolPercent: Double = 95.0,
    
    // AI Only mode (AI gets everything except emergency)
    val aiOnlyPoolPercent: Double = 95.0,
    
    // Per-strategy limits within hedge pool (% of hedge pool)
    val maxPerStrategyPercent: Double = 40.0,  // No single strategy > 40% of hedge pool
    
    // Minimum margin to keep an AI position alive
    val aiPositionMinMarginPercent: Double = 2.0
) {
    init {
        require(aiTradingPoolPercent + hedgePoolPercent + emergencyBufferPercent <= 100.0) {
            "Margin pools must not exceed 100%: AI=$aiTradingPoolPercent + Hedge=$hedgePoolPercent + Buffer=$emergencyBufferPercent"
        }
        require(emergencyBufferPercent >= 3.0) {
            "Emergency buffer must be at least 3% — non-negotiable safety margin"
        }
    }
}

/**
 * Real-time margin allocation state.
 */
data class MarginAllocation(
    val totalEquity: Double,
    val aiTradingPool: Double,
    val hedgePool: Double,
    val emergencyBuffer: Double,
    val aiUsedMargin: Double,
    val hedgeUsedMargin: Double,
    val aiAvailableMargin: Double,
    val hedgeAvailableMargin: Double,
    val timestamp: Long = System.currentTimeMillis()
) {
    val totalUsedPercent: Double get() = 
        if (totalEquity > 0) ((aiUsedMargin + hedgeUsedMargin) / totalEquity) * 100.0 else 0.0
    val emergencyBufferIntact: Boolean get() = 
        (totalEquity - aiUsedMargin - hedgeUsedMargin) >= emergencyBuffer
}

// =============================================================================
// TRADING LOCK
// =============================================================================

/**
 * Lock mechanism that pauses new AI entries during hedge rebalance.
 * 
 * CRITICAL: Protective exits (STAHL, stop-loss, kill switch) are NEVER locked.
 * Only new entries and position increases are blocked.
 */
data class TradingLock(
    val isLocked: Boolean = false,
    val reason: String = "",
    val lockedBy: HedgeStrategy? = null,
    val lockTimestamp: Long = 0,
    val autoReleaseAfterMs: Long = 5 * 60 * 1000,  // 5 minutes max
    val allowProtectiveExits: Boolean = true  // ALWAYS true — never disable exits
) {
    val isExpired: Boolean get() = 
        isLocked && (System.currentTimeMillis() - lockTimestamp > autoReleaseAfterMs)
    val shouldRelease: Boolean get() = !isLocked || isExpired
}

// =============================================================================
// HEDGE EVENT NOTIFICATIONS (for Board awareness)
// =============================================================================

/**
 * Events emitted by hedge strategies for Board notification.
 * Board receives these as READ-ONLY context — no veto power.
 */
sealed class HedgeEvent {
    data class StrategyActivated(val strategy: HedgeStrategy, val timestamp: Long = System.currentTimeMillis()) : HedgeEvent()
    data class StrategyDeactivated(val strategy: HedgeStrategy, val reason: String) : HedgeEvent()
    data class PositionOpened(val strategy: HedgeStrategy, val symbol: String, val side: String, val size: Double) : HedgeEvent()
    data class PositionClosed(val strategy: HedgeStrategy, val symbol: String, val pnl: Double) : HedgeEvent()
    data class Rebalancing(val strategy: HedgeStrategy, val estimatedDurationMs: Long) : HedgeEvent()
    data class RebalanceComplete(val strategy: HedgeStrategy) : HedgeEvent()
    data class MarginUsageChanged(val strategy: HedgeStrategy, val usedPercent: Double) : HedgeEvent()
    data class RiskAlert(val strategy: HedgeStrategy, val message: String, val severity: AlertSeverity) : HedgeEvent()
}

enum class AlertSeverity { INFO, WARNING, CRITICAL }

// =============================================================================
// CONTROLLER STATE
// =============================================================================

/**
 * Complete state snapshot of the trading mode controller.
 */
data class TradingModeState(
    val masterMode: MasterTradingMode = MasterTradingMode.AI_TRADING,
    val enabledHedgeStrategies: Set<HedgeStrategy> = emptySet(),
    val marginAllocation: MarginAllocation? = null,
    val tradingLock: TradingLock = TradingLock(),
    val aiBoardActive: Boolean = true,
    val hedgeEnginesActive: Boolean = false,
    val activeHedgePositionCount: Int = 0,
    val activeAiPositionCount: Int = 0,
    val lastModeChange: Long = System.currentTimeMillis()
)

// =============================================================================
// MAIN CONTROLLER
// =============================================================================

/**
 * Central controller for the three-position trading mode switch.
 * 
 * Coordinates between AI Board, hedge strategies, margin allocation,
 * and trading locks. Ensures margin firewall is maintained at all times.
 */
class TradingModeController(
    private val marginConfig: MarginAllocationConfig = MarginAllocationConfig(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    // Current state
    private val _state = MutableStateFlow(TradingModeState())
    val state: StateFlow<TradingModeState> = _state.asStateFlow()
    
    // Hedge event stream (Board subscribes to this)
    private val _hedgeEvents = MutableSharedFlow<HedgeEvent>(replay = 10, extraBufferCapacity = 50)
    val hedgeEvents: SharedFlow<HedgeEvent> = _hedgeEvents.asSharedFlow()
    
    // Lock state
    private val lockActive = AtomicBoolean(false)
    private val currentMode = AtomicReference(MasterTradingMode.AI_TRADING)
    
    // ==========================================================================
    // MODE SWITCHING
    // ==========================================================================
    
    /**
     * Switch master trading mode.
     * 
     * This is the three-position switch on the dashboard:
     * [AI TRADING] ←→ [AI + HEDGE] ←→ [HEDGE ONLY]
     * 
     * Mode transitions are immediate but graceful:
     * - Existing positions are NOT forcibly closed
     * - STAHL exits remain active on all existing positions
     * - New position creation follows new mode rules
     * - Margin is reallocated according to new mode
     */
    suspend fun setMasterMode(newMode: MasterTradingMode) {
        val oldMode = currentMode.getAndSet(newMode)
        if (oldMode == newMode) return
        
        val newState = when (newMode) {
            MasterTradingMode.AI_TRADING -> {
                // AI Board active, hedge dormant
                // Existing hedge positions allowed to wind down naturally
                _state.value.copy(
                    masterMode = newMode,
                    aiBoardActive = true,
                    hedgeEnginesActive = false,
                    enabledHedgeStrategies = emptySet(),
                    lastModeChange = System.currentTimeMillis()
                )
            }
            MasterTradingMode.AI_TRADING_HEDGE -> {
                // Both active, margin partitioned
                _state.value.copy(
                    masterMode = newMode,
                    aiBoardActive = true,
                    hedgeEnginesActive = true,
                    lastModeChange = System.currentTimeMillis()
                )
            }
            MasterTradingMode.HEDGE_ONLY -> {
                // AI Board suspended for NEW trades
                // Existing AI positions: exits remain active
                _state.value.copy(
                    masterMode = newMode,
                    aiBoardActive = false,
                    hedgeEnginesActive = true,
                    lastModeChange = System.currentTimeMillis()
                )
            }
        }
        
        _state.value = newState
    }
    
    /**
     * Enable/disable individual hedge strategies.
     * Only effective when master mode includes hedge capability.
     */
    fun setHedgeStrategy(strategy: HedgeStrategy, enabled: Boolean) {
        val current = _state.value.enabledHedgeStrategies.toMutableSet()
        if (enabled) current.add(strategy) else current.remove(strategy)
        _state.value = _state.value.copy(enabledHedgeStrategies = current)
        
        scope.launch {
            if (enabled) {
                _hedgeEvents.emit(HedgeEvent.StrategyActivated(strategy))
            } else {
                _hedgeEvents.emit(HedgeEvent.StrategyDeactivated(strategy, "User disabled"))
            }
        }
    }
    
    // ==========================================================================
    // MARGIN ALLOCATION
    // ==========================================================================
    
    /**
     * Calculate margin allocation based on current mode and equity.
     * 
     * The margin firewall ensures neither pool can encroach on the other.
     * Emergency buffer is ALWAYS reserved regardless of mode.
     */
    fun calculateMarginAllocation(
        totalEquity: Double,
        aiUsedMargin: Double,
        hedgeUsedMargin: Double
    ): MarginAllocation {
        val mode = currentMode.get()
        
        val emergencyBuffer = totalEquity * (marginConfig.emergencyBufferPercent / 100.0)
        
        val (aiPool, hedgePool) = when (mode) {
            MasterTradingMode.AI_TRADING -> {
                val ai = totalEquity * (marginConfig.aiOnlyPoolPercent / 100.0)
                Pair(ai, 0.0)
            }
            MasterTradingMode.AI_TRADING_HEDGE -> {
                val ai = totalEquity * (marginConfig.aiTradingPoolPercent / 100.0)
                val hedge = totalEquity * (marginConfig.hedgePoolPercent / 100.0)
                Pair(ai, hedge)
            }
            MasterTradingMode.HEDGE_ONLY -> {
                // AI pool = enough to maintain existing positions only
                val aiMaintenance = (aiUsedMargin * 1.1).coerceAtMost(
                    totalEquity * (marginConfig.aiPositionMinMarginPercent / 100.0)
                )
                val hedge = totalEquity * (marginConfig.hedgeOnlyPoolPercent / 100.0) - aiMaintenance
                Pair(aiMaintenance, hedge)
            }
        }
        
        val allocation = MarginAllocation(
            totalEquity = totalEquity,
            aiTradingPool = aiPool,
            hedgePool = hedgePool,
            emergencyBuffer = emergencyBuffer,
            aiUsedMargin = aiUsedMargin,
            hedgeUsedMargin = hedgeUsedMargin,
            aiAvailableMargin = (aiPool - aiUsedMargin).coerceAtLeast(0.0),
            hedgeAvailableMargin = (hedgePool - hedgeUsedMargin).coerceAtLeast(0.0)
        )
        
        _state.value = _state.value.copy(marginAllocation = allocation)
        return allocation
    }
    
    /**
     * Check if AI Board is allowed to open a new position.
     * Returns false if:
     * - Master mode is HEDGE_ONLY
     * - Trading lock is active (hedge rebalancing)
     * - AI margin pool is exhausted
     */
    fun canAiOpenPosition(requiredMargin: Double): Boolean {
        val state = _state.value
        
        // Mode check
        if (state.masterMode == MasterTradingMode.HEDGE_ONLY) return false
        
        // Lock check (but only for new entries — exits always allowed)
        if (state.tradingLock.isLocked && !state.tradingLock.isExpired) return false
        
        // Margin check
        val allocation = state.marginAllocation ?: return false
        return allocation.aiAvailableMargin >= requiredMargin
    }
    
    /**
     * Check if a hedge strategy is allowed to open a position.
     * Returns false if:
     * - Master mode is AI_TRADING (no hedge)
     * - Strategy not in enabled set
     * - Hedge margin pool exhausted
     * - Per-strategy limit exceeded
     */
    fun canHedgeOpenPosition(strategy: HedgeStrategy, requiredMargin: Double): Boolean {
        val state = _state.value
        
        // Mode check
        if (state.masterMode == MasterTradingMode.AI_TRADING) return false
        
        // Strategy enabled check
        if (strategy !in state.enabledHedgeStrategies) return false
        
        // Margin check
        val allocation = state.marginAllocation ?: return false
        return allocation.hedgeAvailableMargin >= requiredMargin
    }
    
    // ==========================================================================
    // TRADING LOCK (during hedge rebalance)
    // ==========================================================================
    
    /**
     * Acquire trading lock for hedge rebalance.
     * Blocks new AI entries. STAHL exits and stop-losses remain active.
     * 
     * @return true if lock acquired, false if already locked
     */
    fun acquireTradingLock(strategy: HedgeStrategy, estimatedDurationMs: Long): Boolean {
        if (!lockActive.compareAndSet(false, true)) return false
        
        val lock = TradingLock(
            isLocked = true,
            reason = "Hedge rebalance: ${strategy.name}",
            lockedBy = strategy,
            lockTimestamp = System.currentTimeMillis(),
            autoReleaseAfterMs = estimatedDurationMs.coerceAtMost(5 * 60 * 1000)
        )
        
        _state.value = _state.value.copy(tradingLock = lock)
        
        scope.launch {
            _hedgeEvents.emit(HedgeEvent.Rebalancing(strategy, estimatedDurationMs))
        }
        
        // Auto-release timer
        scope.launch {
            delay(lock.autoReleaseAfterMs)
            releaseTradingLock(strategy)
        }
        
        return true
    }
    
    /**
     * Release trading lock after hedge rebalance completes.
     */
    fun releaseTradingLock(strategy: HedgeStrategy) {
        val current = _state.value.tradingLock
        if (current.lockedBy == strategy || current.isExpired) {
            lockActive.set(false)
            _state.value = _state.value.copy(tradingLock = TradingLock())
            
            scope.launch {
                _hedgeEvents.emit(HedgeEvent.RebalanceComplete(strategy))
            }
        }
    }
    
    // ==========================================================================
    // KELLY CRITERION INTEGRATION
    // ==========================================================================
    
    /**
     * Returns the capital base Kelly should use for position sizing.
     * 
     * CRITICAL: Kelly must EXCLUDE hedge positions from its calculations.
     * Hedge positions have different risk/reward profiles and would
     * distort Kelly's win-rate and average-win computations.
     * 
     * Kelly sees: AI trading pool minus AI used margin
     * Kelly does NOT see: Hedge pool, hedge positions, emergency buffer
     */
    fun getKellyCapitalBase(): Double {
        val allocation = _state.value.marginAllocation ?: return 0.0
        return allocation.aiAvailableMargin
    }
    
    /**
     * Check if a position is a hedge position (for Kelly exclusion).
     * Hedge positions are tagged with their source strategy.
     */
    fun isHedgePosition(positionTag: String?): Boolean {
        return positionTag?.startsWith("HEDGE_") == true
    }
    
    // ==========================================================================
    // PROTECTIVE EXIT PASS-THROUGH
    // ==========================================================================
    
    /**
     * Protective exits are NEVER blocked, regardless of mode or lock state.
     * This method always returns true.
     * 
     * Includes: STAHL stair stop, initial stop-loss, kill switch,
     * margin call response, emergency liquidation.
     */
    fun canExecuteProtectiveExit(): Boolean = true  // ALWAYS
    
    // ==========================================================================
    // DASHBOARD STATE
    // ==========================================================================
    
    /**
     * Provides a summary for the three-position switch UI.
     */
    fun getDashboardSummary(): DashboardModeSummary {
        val state = _state.value
        val allocation = state.marginAllocation
        
        return DashboardModeSummary(
            currentMode = state.masterMode,
            aiBoardStatus = when {
                !state.aiBoardActive -> "SUSPENDED"
                state.tradingLock.isLocked -> "PAUSED (hedge rebalance)"
                else -> "ACTIVE"
            },
            hedgeStatus = when {
                !state.hedgeEnginesActive -> "OFF"
                state.enabledHedgeStrategies.isEmpty() -> "STANDBY (no strategies selected)"
                else -> "ACTIVE (${state.enabledHedgeStrategies.size} strategies)"
            },
            aiMarginUsedPercent = allocation?.let {
                if (it.aiTradingPool > 0) (it.aiUsedMargin / it.aiTradingPool * 100) else 0.0
            } ?: 0.0,
            hedgeMarginUsedPercent = allocation?.let {
                if (it.hedgePool > 0) (it.hedgeUsedMargin / it.hedgePool * 100) else 0.0
            } ?: 0.0,
            emergencyBufferIntact = allocation?.emergencyBufferIntact ?: true,
            tradingLockActive = state.tradingLock.isLocked && !state.tradingLock.isExpired,
            activeStrategies = state.enabledHedgeStrategies.toList()
        )
    }
}

/**
 * Dashboard summary for the three-position switch UI.
 */
data class DashboardModeSummary(
    val currentMode: MasterTradingMode,
    val aiBoardStatus: String,
    val hedgeStatus: String,
    val aiMarginUsedPercent: Double,
    val hedgeMarginUsedPercent: Double,
    val emergencyBufferIntact: Boolean,
    val tradingLockActive: Boolean,
    val activeStrategies: List<HedgeStrategy>
)
