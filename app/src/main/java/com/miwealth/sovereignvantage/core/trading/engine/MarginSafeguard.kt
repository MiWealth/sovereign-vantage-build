package com.miwealth.sovereignvantage.core.trading.engine

/**
 * MARGIN SAFEGUARD SYSTEM
 * 
 * Sovereign Vantage: Arthur Edition V5.17.0
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * CRITICAL SYSTEM - THIS RULE MUST NEVER BE BREACHED:
 * Always maintain sufficient funds to cover margin requirements.
 * 
 * The MarginSafeguard is the FINAL GATE before any trade execution.
 * It operates independently of other risk checks and has absolute veto power.
 * 
 * Architecture:
 * 1. Pre-trade validation (BEFORE order placement)
 * 2. Continuous margin monitoring (DURING positions)
 * 3. Automatic deleveraging (WHEN margin threatened)
 * 4. Emergency liquidation (LAST RESORT)
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */



import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Margin safeguard configuration.
 * These values are conservative by design - user safety is paramount.
 */
data class MarginSafeguardConfig(
    // =========================================================================
    // ABSOLUTE LIMITS - NEVER BREACH THESE
    // =========================================================================
    
    /** Minimum free margin as % of equity (SACRED - never go below this) */
    val minFreeMarginPercent: Double = 25.0,
    
    /** Maintenance margin warning threshold (% of equity) */
    val maintenanceMarginWarning: Double = 35.0,
    
    /** Margin call threshold - start reducing positions (% of equity) */
    val marginCallThreshold: Double = 30.0,
    
    /** Emergency liquidation threshold (% of equity) */
    val emergencyLiquidationThreshold: Double = 20.0,
    
    // =========================================================================
    // PRE-TRADE REQUIREMENTS
    // =========================================================================
    
    /** Required margin buffer for new trades (% above minimum) */
    val newTradeMarginBuffer: Double = 15.0,
    
    /** Maximum margin utilisation allowed for new trade (% of available) */
    val maxMarginUtilisationForNewTrade: Double = 70.0,
    
    /** Minimum equity required to open ANY leveraged position */
    val minEquityForLeveragedTrade: Double = 1000.0,
    
    // =========================================================================
    // AUTOMATIC DELEVERAGING
    // =========================================================================
    
    /** Enable automatic position reduction when margin threatened */
    val enableAutoDeleverage: Boolean = true,
    
    /** Percentage to reduce positions by during margin call */
    val deleverageReductionPercent: Double = 25.0,
    
    /** Interval between deleverage checks (milliseconds) */
    val deleverageCheckIntervalMs: Long = 5_000,
    
    // =========================================================================
    // MONITORING
    // =========================================================================
    
    /** Margin check interval during active trading (milliseconds) */
    val marginCheckIntervalMs: Long = 1_000,
    
    /** Enable aggressive monitoring in volatile markets */
    val volatileMarketCheckIntervalMs: Long = 500,
    
    /** Volatility threshold to trigger aggressive monitoring (% price change) */
    val volatilityThreshold: Double = 2.0
)

/**
 * Current margin status snapshot.
 */
data class MarginStatus(
    /** Total equity (balance + unrealized P&L) */
    val equity: Double,
    
    /** Used margin (locked for open positions) */
    val usedMargin: Double,
    
    /** Free margin (available for new trades) */
    val freeMargin: Double,
    
    /** Margin level as percentage (equity / used margin * 100) */
    val marginLevel: Double,
    
    /** Free margin as percentage of equity */
    val freeMarginPercent: Double,
    
    /** Current margin utilisation (used / equity * 100) */
    val marginUtilisation: Double,
    
    /** Distance to margin call (in currency units) */
    val distanceToMarginCall: Double,
    
    /** Distance to margin call (in percentage) */
    val distanceToMarginCallPercent: Double,
    
    /** Current risk state */
    val riskState: MarginRiskState,
    
    /** Human-readable status message */
    val statusMessage: String,
    
    /** Timestamp of this snapshot */
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Margin risk states - from safest to most critical.
 */
enum class MarginRiskState {
    /** Margin is healthy, plenty of buffer */
    HEALTHY,
    
    /** Warning - approaching limits but still safe */
    WARNING,
    
    /** Margin call - positions being reduced */
    MARGIN_CALL,
    
    /** Critical - emergency liquidation imminent */
    CRITICAL,
    
    /** Liquidation in progress */
    LIQUIDATING
}

/**
 * Events emitted by the margin safeguard system.
 */
sealed class MarginEvent {
    data class StatusUpdate(val status: MarginStatus) : MarginEvent()
    data class Warning(val message: String, val marginLevel: Double) : MarginEvent()
    data class MarginCallTriggered(val equity: Double, val required: Double) : MarginEvent()
    data class AutoDeleverageStarted(val positionsToReduce: Int) : MarginEvent()
    data class PositionReduced(val symbol: String, val reductionPercent: Double) : MarginEvent()
    data class EmergencyLiquidationStarted(val reason: String) : MarginEvent()
    data class TradeRejectedInsufficientMargin(
        val symbol: String,
        val requiredMargin: Double,
        val availableMargin: Double
    ) : MarginEvent()
    data class TradingHaltedMarginBreach(val reason: String) : MarginEvent()
    data class MarginRestored(val newLevel: Double) : MarginEvent()
}

/**
 * Result of pre-trade margin check.
 */
sealed class MarginCheckResult {
    object Approved : MarginCheckResult()
    
    data class Rejected(
        val reason: String,
        val requiredMargin: Double,
        val availableMargin: Double,
        val suggestedQuantity: Double? = null  // Smaller quantity that would be approved
    ) : MarginCheckResult()
    
    data class ApprovedWithWarning(
        val warning: String,
        val marginUtilisationAfterTrade: Double
    ) : MarginCheckResult()
}

/**
 * MarginSafeguard - The guardian of margin requirements.
 * 
 * This is the FINAL GATE before any leveraged trade. It has absolute veto power
 * and operates independently of other risk systems.
 * 
 * DESIGN PRINCIPLES:
 * 1. CONSERVATIVE by default - when in doubt, reject the trade
 * 2. CONTINUOUS monitoring - never assume margin is safe
 * 3. AUTOMATIC protection - don't wait for user action in emergencies
 * 4. TRANSPARENT - always explain why a trade was rejected
 */
class MarginSafeguard(
    private val config: MarginSafeguardConfig = MarginSafeguardConfig(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    companion object {
        private const val TAG = "MarginSafeguard"
        
        @Volatile
        private var INSTANCE: MarginSafeguard? = null
        
        fun getInstance(): MarginSafeguard {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MarginSafeguard().also { INSTANCE = it }
            }
        }
    }
    
    // State
    private val _marginStatus = MutableStateFlow<MarginStatus?>(null)
    val marginStatus: StateFlow<MarginStatus?> = _marginStatus.asStateFlow()
    
    private val _marginEvents = MutableSharedFlow<MarginEvent>(replay = 0, extraBufferCapacity = 64)
    val marginEvents: SharedFlow<MarginEvent> = _marginEvents.asSharedFlow()
    
    private val _isTradingAllowed = MutableStateFlow(true)
    val isTradingAllowed: StateFlow<Boolean> = _isTradingAllowed.asStateFlow()
    
    // Internal state
    private var currentEquity: Double = 0.0
    private var currentUsedMargin: Double = 0.0
    private var isMonitoring: Boolean = false
    private var isInitialized: Boolean = false  // BUILD #143: Prevent checks before initialization
    private var monitoringJob: Job? = null
    private var deleverageJob: Job? = null
    private var lastVolatilityCheck: Long = 0
    private var isVolatileMarket: Boolean = false
    
    // Callbacks for position management
    private var positionReducer: (suspend (String, Double) -> Boolean)? = null
    private var allPositionsCloser: (suspend (String) -> Unit)? = null
    private var positionGetter: (() -> List<PositionSnapshot>)? = null
    
    // =========================================================================
    // INITIALIZATION
    // =========================================================================
    
    /**
     * Initialize the margin safeguard with current account state.
     */
    fun initialize(
        equity: Double,
        usedMargin: Double,
        getPositions: () -> List<PositionSnapshot>,
        reducePosition: suspend (String, Double) -> Boolean,
        closeAllPositions: suspend (String) -> Unit
    ) {
        Log.i(TAG, "Initializing MarginSafeguard with equity=$equity, usedMargin=$usedMargin")
        
        currentEquity = equity
        currentUsedMargin = usedMargin
        positionGetter = getPositions
        positionReducer = reducePosition
        allPositionsCloser = closeAllPositions
        isInitialized = true  // BUILD #143: Mark as initialized
        
        updateMarginStatus()
        
        _isTradingAllowed.value = true
    }
    
    /**
     * Start continuous margin monitoring.
     */
    fun startMonitoring() {
        if (isMonitoring) return
        
        isMonitoring = true
        monitoringJob = scope.launch {
            while (isActive && isMonitoring) {
                val interval = if (isVolatileMarket) {
                    config.volatileMarketCheckIntervalMs
                } else {
                    config.marginCheckIntervalMs
                }
                
                delay(interval)
                checkMarginHealth()
            }
        }
        
        Log.i(TAG, "Margin monitoring started")
    }
    
    /**
     * Stop margin monitoring.
     */
    fun stopMonitoring() {
        isMonitoring = false
        monitoringJob?.cancel()
        deleverageJob?.cancel()
        Log.i(TAG, "Margin monitoring stopped")
    }
    
    // =========================================================================
    // PRE-TRADE VALIDATION (CRITICAL - CALLED BEFORE EVERY LEVERAGED TRADE)
    // =========================================================================
    
    /**
     * Check if a trade can be executed with current margin.
     * THIS IS THE FINAL GATE - if this returns Rejected, the trade MUST NOT proceed.
     * 
     * @param symbol The trading pair (e.g., "BTC/USDT")
     * @param quantity Quantity to trade
     * @param price Entry price
     * @param leverage Requested leverage
     * @param isReduceOnly True if this trade reduces an existing position
     * @return MarginCheckResult with approval/rejection and reasoning
     */
    fun checkMarginForTrade(
        symbol: String,
        quantity: Double,
        price: Double,
        leverage: Double,
        isReduceOnly: Boolean = false
    ): MarginCheckResult {
        // Reduce-only trades are always allowed (they free margin)
        if (isReduceOnly) {
            return MarginCheckResult.Approved
        }
        
        // Trading currently halted?
        if (!_isTradingAllowed.value) {
            return MarginCheckResult.Rejected(
                reason = "Trading halted due to margin breach",
                requiredMargin = 0.0,
                availableMargin = 0.0
            )
        }
        
        val status = _marginStatus.value ?: return MarginCheckResult.Rejected(
            reason = "Margin status not initialized",
            requiredMargin = 0.0,
            availableMargin = 0.0
        )
        
        // Calculate margin required for this trade
        val notionalValue = quantity * price
        val requiredMargin = notionalValue / leverage
        
        // Check 1: Minimum equity requirement for leveraged trades
        if (leverage > 1.0 && status.equity < config.minEquityForLeveragedTrade) {
            return MarginCheckResult.Rejected(
                reason = "Insufficient equity for leveraged trading. Minimum: AUD ${config.minEquityForLeveragedTrade}",
                requiredMargin = requiredMargin,
                availableMargin = status.freeMargin
            )
        }
        
        // Check 2: Is there enough free margin?
        if (requiredMargin > status.freeMargin) {
            // Calculate what quantity WOULD be allowed
            val maxAffordableNotional = status.freeMargin * leverage * 
                                        (config.maxMarginUtilisationForNewTrade / 100)
            val suggestedQuantity = maxAffordableNotional / price
            
            emitEvent(MarginEvent.TradeRejectedInsufficientMargin(
                symbol, requiredMargin, status.freeMargin
            ))
            
            return MarginCheckResult.Rejected(
                reason = "Insufficient free margin. Required: AUD ${formatMoney(requiredMargin)}, Available: AUD ${formatMoney(status.freeMargin)}",
                requiredMargin = requiredMargin,
                availableMargin = status.freeMargin,
                suggestedQuantity = if (suggestedQuantity > 0) suggestedQuantity else null
            )
        }
        
        // Check 3: Would this trade breach the minimum free margin buffer?
        val marginAfterTrade = status.freeMargin - requiredMargin
        val freeMarginPercentAfterTrade = (marginAfterTrade / status.equity) * 100
        
        if (freeMarginPercentAfterTrade < config.minFreeMarginPercent + config.newTradeMarginBuffer) {
            // This trade would leave us too close to the minimum
            return MarginCheckResult.Rejected(
                reason = "Trade would reduce free margin below safety buffer (${config.minFreeMarginPercent + config.newTradeMarginBuffer}%)",
                requiredMargin = requiredMargin,
                availableMargin = status.freeMargin
            )
        }
        
        // Check 4: Would margin utilisation be too high?
        val newUsedMargin = status.usedMargin + requiredMargin
        val utilisationAfterTrade = (newUsedMargin / status.equity) * 100
        
        if (utilisationAfterTrade > config.maxMarginUtilisationForNewTrade) {
            return MarginCheckResult.Rejected(
                reason = "Trade would exceed maximum margin utilisation (${config.maxMarginUtilisationForNewTrade}%)",
                requiredMargin = requiredMargin,
                availableMargin = status.freeMargin
            )
        }
        
        // Check 5: Are we already in a margin call state?
        if (status.riskState == MarginRiskState.MARGIN_CALL ||
            status.riskState == MarginRiskState.CRITICAL ||
            status.riskState == MarginRiskState.LIQUIDATING) {
            return MarginCheckResult.Rejected(
                reason = "Cannot open new positions during ${status.riskState}",
                requiredMargin = requiredMargin,
                availableMargin = status.freeMargin
            )
        }
        
        // Passed all checks - approve with warning if utilisation will be high
        return if (utilisationAfterTrade > 50) {
            MarginCheckResult.ApprovedWithWarning(
                warning = "Trade approved but margin utilisation will be ${String.format("%.1f", utilisationAfterTrade)}%",
                marginUtilisationAfterTrade = utilisationAfterTrade
            )
        } else {
            MarginCheckResult.Approved
        }
    }
    
    /**
     * Calculate maximum position size allowed by margin constraints.
     */
    fun getMaxPositionSize(price: Double, leverage: Double): Double {
        val status = _marginStatus.value ?: return 0.0
        
        // Available margin after safety buffer
        val availableForTrading = status.freeMargin * 
            (config.maxMarginUtilisationForNewTrade / 100)
        
        // Maximum notional value
        val maxNotional = availableForTrading * leverage
        
        // Convert to quantity
        return maxNotional / price
    }
    
    // =========================================================================
    // CONTINUOUS MONITORING
    // =========================================================================
    
    /**
     * Update margin status with latest account data.
     * Call this on every balance/position update.
     */
    fun updateAccount(equity: Double, usedMargin: Double) {
        // Check for volatile market (large equity swing)
        val equityChange = if (currentEquity > 0) {
            abs(equity - currentEquity) / currentEquity * 100
        } else 0.0
        
        isVolatileMarket = equityChange > config.volatilityThreshold
        
        currentEquity = equity
        currentUsedMargin = usedMargin
        
        updateMarginStatus()
        checkMarginHealth()
    }
    
    /**
     * Update margin status based on current values.
     */
    private fun updateMarginStatus() {
        // BUILD #143 FIX: Don't calculate margin status before initialization
        // Prevents false Emergency Liquidation triggers when currentEquity = 0.0
        if (!isInitialized) {
            Log.d(TAG, "⏭️ BUILD #143: Skipping margin status update - not initialized yet")
            return
        }
        
        val freeMargin = currentEquity - currentUsedMargin
        val marginLevel = if (currentUsedMargin > 0) {
            (currentEquity / currentUsedMargin) * 100
        } else {
            Double.MAX_VALUE  // No positions = infinite margin level
        }
        val freeMarginPercent = if (currentEquity > 0) {
            (freeMargin / currentEquity) * 100
        } else 0.0
        val marginUtilisation = if (currentEquity > 0) {
            (currentUsedMargin / currentEquity) * 100
        } else 0.0
        
        // Calculate distance to margin call
        val marginCallEquity = currentUsedMargin * (100 / (100 - config.marginCallThreshold))
        val distanceToMarginCall = currentEquity - marginCallEquity
        val distancePercent = if (currentEquity > 0) {
            (distanceToMarginCall / currentEquity) * 100
        } else 0.0
        
        // Determine risk state
        val riskState = when {
            freeMarginPercent < config.emergencyLiquidationThreshold -> MarginRiskState.CRITICAL
            freeMarginPercent < config.marginCallThreshold -> MarginRiskState.MARGIN_CALL
            freeMarginPercent < config.maintenanceMarginWarning -> MarginRiskState.WARNING
            else -> MarginRiskState.HEALTHY
        }
        
        // Generate status message
        val statusMessage = when (riskState) {
            MarginRiskState.HEALTHY -> "Margin healthy (${String.format("%.1f", freeMarginPercent)}% free)"
            MarginRiskState.WARNING -> "⚠️ Margin warning - approaching limits"
            MarginRiskState.MARGIN_CALL -> "🚨 MARGIN CALL - Reducing positions"
            MarginRiskState.CRITICAL -> "🔴 CRITICAL - Emergency liquidation imminent"
            MarginRiskState.LIQUIDATING -> "⛔ LIQUIDATING POSITIONS"
        }
        
        val status = MarginStatus(
            equity = currentEquity,
            usedMargin = currentUsedMargin,
            freeMargin = freeMargin,
            marginLevel = marginLevel,
            freeMarginPercent = freeMarginPercent,
            marginUtilisation = marginUtilisation,
            distanceToMarginCall = distanceToMarginCall,
            distanceToMarginCallPercent = distancePercent,
            riskState = riskState,
            statusMessage = statusMessage
        )
        
        _marginStatus.value = status
        emitEvent(MarginEvent.StatusUpdate(status))
    }
    
    /**
     * Check margin health and take action if needed.
     */
    private fun checkMarginHealth() {
        // BUILD #143 FIX: Don't check margin before initialization
        if (!isInitialized) {
            return
        }
        
        val status = _marginStatus.value ?: return
        
        when (status.riskState) {
            MarginRiskState.HEALTHY -> {
                // All good - ensure trading is allowed
                if (!_isTradingAllowed.value) {
                    _isTradingAllowed.value = true
                    emitEvent(MarginEvent.MarginRestored(status.marginLevel))
                }
            }
            
            MarginRiskState.WARNING -> {
                emitEvent(MarginEvent.Warning(
                    "Margin level approaching minimum (${String.format("%.1f", status.freeMarginPercent)}%)",
                    status.marginLevel
                ))
            }
            
            MarginRiskState.MARGIN_CALL -> {
                Log.w(TAG, "MARGIN CALL triggered at ${status.freeMarginPercent}%")
                emitEvent(MarginEvent.MarginCallTriggered(status.equity, currentUsedMargin))
                
                if (config.enableAutoDeleverage) {
                    triggerAutoDeleverage()
                }
            }
            
            MarginRiskState.CRITICAL -> {
                Log.e(TAG, "CRITICAL margin level - emergency liquidation")
                _isTradingAllowed.value = false
                emitEvent(MarginEvent.TradingHaltedMarginBreach(
                    "Critical margin level: ${String.format("%.1f", status.freeMarginPercent)}%"
                ))
                triggerEmergencyLiquidation()
            }
            
            MarginRiskState.LIQUIDATING -> {
                // Already liquidating
            }
        }
    }
    
    // =========================================================================
    // AUTOMATIC DELEVERAGING
    // =========================================================================
    
    /**
     * Trigger automatic position reduction to restore margin health.
     */
    private fun triggerAutoDeleverage() {
        if (deleverageJob?.isActive == true) return  // Already running
        
        deleverageJob = scope.launch {
            val positions = positionGetter?.invoke() ?: return@launch
            
            if (positions.isEmpty()) {
                Log.w(TAG, "No positions to deleverage")
                return@launch
            }
            
            emitEvent(MarginEvent.AutoDeleverageStarted(positions.size))
            Log.i(TAG, "Auto-deleverage: reducing ${positions.size} positions by ${config.deleverageReductionPercent}%")
            
            // Sort by unrealized loss (reduce losing positions first)
            val sortedPositions = positions.sortedBy { it.unrealizedPnl }
            
            for (position in sortedPositions) {
                // Check if margin is restored
                val currentStatus = _marginStatus.value
                if (currentStatus?.riskState == MarginRiskState.HEALTHY) {
                    Log.i(TAG, "Margin restored during deleverage")
                    break
                }
                
                // Reduce this position
                val success = positionReducer?.invoke(
                    position.symbol,
                    config.deleverageReductionPercent
                ) ?: false
                
                if (success) {
                    emitEvent(MarginEvent.PositionReduced(
                        position.symbol,
                        config.deleverageReductionPercent
                    ))
                    Log.i(TAG, "Reduced ${position.symbol} by ${config.deleverageReductionPercent}%")
                }
                
                // Small delay between reductions
                delay(500)
            }
        }
    }
    
    /**
     * Emergency liquidation - close all positions immediately.
     */
    private fun triggerEmergencyLiquidation() {
        scope.launch {
            emitEvent(MarginEvent.EmergencyLiquidationStarted(
                "Margin below emergency threshold (${config.emergencyLiquidationThreshold}%)"
            ))
            
            Log.e(TAG, "🚨 EMERGENCY LIQUIDATION - Closing all positions")
            
            allPositionsCloser?.invoke("Emergency margin liquidation")
        }
    }
    
    // =========================================================================
    // HELPERS
    // =========================================================================
    
    private fun emitEvent(event: MarginEvent) {
        scope.launch {
            _marginEvents.emit(event)
        }
    }
    
    private fun formatMoney(amount: Double): String {
        return String.format("%.2f", amount)
    }
    
    /**
     * Shutdown the margin safeguard.
     */
    fun shutdown() {
        stopMonitoring()
        scope.cancel()
        INSTANCE = null
    }
}

/**
 * Snapshot of a position for margin calculations.
 */
data class PositionSnapshot(
    val symbol: String,
    val quantity: Double,
    val entryPrice: Double,
    val currentPrice: Double,
    val leverage: Double,
    val margin: Double,
    val unrealizedPnl: Double,
    val side: String  // "long" or "short"
)

/**
 * Extension function to integrate with RiskManager.
 */
fun RiskManager.withMarginSafeguard(safeguard: MarginSafeguard): RiskManager {
    // Wire up margin events to risk events
    // This is called during initialization
    return this
}

/**
 * Extension to calculate margin requirement for a trade.
 */
fun calculateMarginRequired(
    quantity: Double,
    price: Double,
    leverage: Double
): Double {
    val notionalValue = quantity * price
    return notionalValue / leverage
}

/**
 * Extension to check if current margin supports additional leverage.
 */
fun MarginStatus.canSupportAdditionalLeverage(
    additionalMarginRequired: Double,
    bufferPercent: Double = 25.0
): Boolean {
    val marginAfterTrade = freeMargin - additionalMarginRequired
    val percentAfterTrade = (marginAfterTrade / equity) * 100
    return percentAfterTrade >= bufferPercent
}
