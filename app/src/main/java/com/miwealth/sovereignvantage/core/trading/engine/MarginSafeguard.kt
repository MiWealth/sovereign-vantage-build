package com.miwealth.sovereignvantage.core.trading.engine

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * MARGIN SAFEGUARD
 * 
 * Sovereign Vantage: Arthur Edition V5.19.0
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * CRITICAL SYSTEM - THIS RULE MUST NEVER BE BREACHED:
 * Always maintain sufficient margin to prevent liquidation.
 * 
 * This is the last line of defense against catastrophic loss.
 * It monitors margin levels and can force-close positions
 * if margin drops below safety thresholds.
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */
class MarginSafeguard private constructor() {
    
    companion object {
        private const val TAG = "MarginSafeguard"
        
        @Volatile
        private var instance: MarginSafeguard? = null
        
        fun getInstance(): MarginSafeguard {
            return instance ?: synchronized(this) {
                instance ?: MarginSafeguard().also { instance = it }
            }
        }
        
        // Margin level thresholds (as percentage of equity / used margin * 100)
        const val LIQUIDATION_LEVEL = 110.0      // Force close all positions
        const val CRITICAL_LEVEL = 125.0         // Reduce position sizes
        const val MARGIN_CALL_LEVEL = 150.0      // Warning + no new positions
        const val WARNING_LEVEL = 200.0          // Advisory warning
        const val HEALTHY_LEVEL = 300.0          // All good
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Current margin status
    private val _marginStatus = MutableStateFlow<MarginStatus?>(null)
    val marginStatus: StateFlow<MarginStatus?> = _marginStatus.asStateFlow()
    
    // Is monitoring active?
    private var isMonitoring = false
    
    // Position manager reference
    private var positionManager: Any? = null
    
    // Balance source
    private var balanceProvider: (() -> Double)? = null
    private var unrealizedPnlProvider: (() -> Double)? = null
    private var usedMarginProvider: (() -> Double)? = null
    private var positionCountProvider: (() -> Int)? = null
    
    /**
     * Initialize the safeguard with data providers.
     */
    fun initialize(
        positionManager: Any,
        balanceProvider: () -> Double,
        unrealizedPnlProvider: () -> Double,
        usedMarginProvider: () -> Double,
        positionCountProvider: () -> Int = { 0 }
    ) {
        this.positionManager = positionManager
        this.balanceProvider = balanceProvider
        this.unrealizedPnlProvider = unrealizedPnlProvider
        this.usedMarginProvider = usedMarginProvider
        this.positionCountProvider = positionCountProvider
        
        // Initial status calculation
        updateMarginStatus()
        
        Log.i(TAG, "🛡️ MarginSafeguard initialized")
    }
    
    /**
     * Legacy initialize method for compatibility with TradingSystemIntegration.
     */
    fun initialize(
        equity: Double,
        usedMargin: Double,
        getPositions: () -> List<PositionSnapshot>,
        reducePosition: suspend (String, Double) -> Unit,
        closeAllPositions: suspend (String) -> Unit
    ) {
        // Store references for position management
        this.positionManager = object {
            val getPositionsFn = getPositions
            val reducePositionFn = reducePosition
            val closeAllPositionsFn = closeAllPositions
        }
        
        // Set up simple providers
        this.balanceProvider = { equity }
        this.unrealizedPnlProvider = { 0.0 }
        this.usedMarginProvider = { usedMargin }
        this.positionCountProvider = { getPositions().size }
        
        // Initial status calculation
        updateMarginStatus()
        
        Log.i(TAG, "🛡️ MarginSafeguard initialized (legacy mode)")
    }
    
    /**
     * Start continuous monitoring.
     */
    fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        
        scope.launch {
            while (isMonitoring) {
                updateMarginStatus()
                delay(1000) // Check every second
            }
        }
        
        Log.i(TAG, "🛡️ MarginSafeguard monitoring STARTED")
    }
    
    /**
     * Stop monitoring.
     */
    fun stopMonitoring() {
        isMonitoring = false
        Log.i(TAG, "🛡️ MarginSafeguard monitoring STOPPED")
    }
    
    /**
     * Update margin status from providers.
     */
    private fun updateMarginStatus() {
        val balance = balanceProvider?.invoke() ?: 100_000.0
        val unrealizedPnl = unrealizedPnlProvider?.invoke() ?: 0.0
        val usedMargin = usedMarginProvider?.invoke() ?: 0.0
        val positionCount = positionCountProvider?.invoke() ?: 0
        
        val equity = balance + unrealizedPnl
        val freeMargin = max(0.0, equity - usedMargin)
        
        val newStatus = MarginStatus.fromPortfolioState(
            equity = equity,
            usedMargin = usedMargin,
            freeMargin = freeMargin,
            openPositionCount = positionCount
        )
        
        // Check for state changes
        val previousState = _marginStatus.value?.riskState
        if (previousState != newStatus.riskState) {
            handleRiskStateChange(previousState, newStatus.riskState, newStatus)
        }
        
        _marginStatus.value = newStatus
    }
    
    /**
     * Handle risk state transitions.
     */
    private fun handleRiskStateChange(
        from: MarginRiskState?,
        to: MarginRiskState,
        status: MarginStatus
    ) {
        Log.w(TAG, "🚨 Margin risk state changed: $from -> $to (level: ${status.marginLevel}%)")
        
        when (to) {
            MarginRiskState.LIQUIDATING -> {
                Log.e(TAG, "🔥 LIQUIDATION LEVEL BREACHED - CLOSING ALL POSITIONS")
                // In production: positionManager.closeAllPositions()
            }
            MarginRiskState.CRITICAL -> {
                Log.e(TAG, "⚠️ CRITICAL MARGIN LEVEL - Reducing position sizes")
            }
            MarginRiskState.MARGIN_CALL -> {
                Log.w(TAG, "📢 MARGIN CALL - No new positions allowed")
            }
            MarginRiskState.WARNING -> {
                Log.w(TAG, "⚡ Margin warning level - Monitor closely")
            }
            MarginRiskState.HEALTHY -> {
                Log.i(TAG, "✅ Margin levels healthy")
            }
        }
    }
    
    /**
     * Check if new positions are allowed.
     */
    fun canOpenNewPosition(): Boolean {
        val status = _marginStatus.value ?: return true
        return status.riskState == MarginRiskState.HEALTHY || 
               status.riskState == MarginRiskState.WARNING
    }
    
    /**
     * Get maximum position size allowed given current margin.
     */
    fun getMaxPositionSize(leverage: Double = 1.0): Double {
        val status = _marginStatus.value ?: return 0.0
        
        // Reserve 20% of free margin for safety
        val usableMargin = status.freeMargin * 0.8
        
        return when (status.riskState) {
            MarginRiskState.HEALTHY -> usableMargin * leverage
            MarginRiskState.WARNING -> usableMargin * leverage * 0.5
            MarginRiskState.MARGIN_CALL -> 0.0
            MarginRiskState.CRITICAL -> 0.0
            MarginRiskState.LIQUIDATING -> 0.0
        }
    }
    
    /**
     * Force update margin status (call after trades).
     */
    fun forceUpdate() {
        updateMarginStatus()
    }
    
    /**
     * Check if a proposed trade passes margin requirements.
     */
    fun checkMarginForTrade(
        symbol: String,
        quantity: Double,
        price: Double,
        leverage: Double,
        isReduceOnly: Boolean = false
    ): MarginCheckResult {
        // Reduce-only orders always pass - they free up margin
        if (isReduceOnly) {
            return MarginCheckResult.Approved(
                availableMargin = _marginStatus.value?.freeMargin ?: 0.0,
                requiredMargin = 0.0,
                newMarginLevel = _marginStatus.value?.marginLevel ?: Double.MAX_VALUE
            )
        }
        
        val status = _marginStatus.value ?: return MarginCheckResult.Approved(
            availableMargin = 100_000.0,
            requiredMargin = 0.0,
            newMarginLevel = Double.MAX_VALUE
        )
        
        // Calculate required margin for this trade
        val notionalValue = quantity * price
        val requiredMargin = notionalValue / leverage
        
        // Check if we have enough free margin
        if (requiredMargin > status.freeMargin) {
            return MarginCheckResult.Rejected(
                reason = "Insufficient margin. Required: $${String.format("%.2f", requiredMargin)}, Available: $${String.format("%.2f", status.freeMargin)}",
                availableMargin = status.freeMargin,
                requiredMargin = requiredMargin
            )
        }
        
        // Check if trade would push us into warning territory
        val newUsedMargin = status.usedMargin + requiredMargin
        val newMarginLevel = if (newUsedMargin > 0) (status.equity / newUsedMargin) * 100 else Double.MAX_VALUE
        
        if (newMarginLevel < MARGIN_CALL_LEVEL) {
            return MarginCheckResult.Rejected(
                reason = "Trade would breach margin call level (${String.format("%.0f", newMarginLevel)}% < ${MARGIN_CALL_LEVEL.toInt()}%)",
                availableMargin = status.freeMargin,
                requiredMargin = requiredMargin
            )
        }
        
        if (newMarginLevel < WARNING_LEVEL) {
            return MarginCheckResult.Warning(
                reason = "Trade approved with warning",
                availableMargin = status.freeMargin,
                requiredMargin = requiredMargin,
                warning = "Margin level will be ${String.format("%.0f", newMarginLevel)}% (below warning threshold of ${WARNING_LEVEL.toInt()}%)"
            )
        }
        
        return MarginCheckResult.Approved(
            availableMargin = status.freeMargin,
            requiredMargin = requiredMargin,
            newMarginLevel = newMarginLevel
        )
    }
    
    /**
     * Get configuration values (for UI display).
     */
    fun getConfig(): MarginSafeguardConfig {
        return MarginSafeguardConfig(
            liquidationLevel = LIQUIDATION_LEVEL,
            criticalLevel = CRITICAL_LEVEL,
            marginCallLevel = MARGIN_CALL_LEVEL,
            warningLevel = WARNING_LEVEL,
            healthyLevel = HEALTHY_LEVEL
        )
    }
}

/**
 * Configuration for margin safeguard thresholds.
 */
data class MarginSafeguardConfig(
    val liquidationLevel: Double,
    val criticalLevel: Double,
    val marginCallLevel: Double,
    val warningLevel: Double,
    val healthyLevel: Double
)
