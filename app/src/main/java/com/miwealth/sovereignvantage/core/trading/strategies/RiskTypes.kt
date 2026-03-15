package com.miwealth.sovereignvantage.core.trading.strategies

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * RISK MANAGEMENT TYPES
 * 
 * Sovereign Vantage: Arthur Edition V5.19.0
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * Risk state enums, events, and manager used by AdvancedStrategyCoordinator
 * and the strategy risk management system.
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */

/**
 * Risk state for strategy execution.
 */
enum class RiskState {
    /** Normal operation */
    NORMAL,
    
    /** Warning threshold reached */
    WARNING,
    
    /** Critical threshold - reducing exposure */
    CRITICAL,
    
    /** Actively liquidating positions */
    LIQUIDATING,
    
    /** Trading halted */
    HALTED
}

/**
 * Risk manager events.
 */
sealed class RiskManagerEvent {
    /** Drawdown warning threshold reached */
    data class DrawdownWarning(
        val strategyId: String,
        val drawdownPercent: Double,
        val reason: String
    ) : RiskManagerEvent()
    
    /** Critical drawdown - action required */
    data class CriticalDrawdown(
        val strategyId: String,
        val drawdownPercent: Double,
        val reason: String
    ) : RiskManagerEvent()
    
    /** Kill switch has been activated */
    data class KillSwitchActivated(
        val reason: String
    ) : RiskManagerEvent()
    
    /** Liquidation complete */
    data class LiquidationComplete(
        val strategyId: String,
        val positionsLiquidated: Int,
        val stablecoinReceived: Double
    ) : RiskManagerEvent()
    
    /** Trading halted */
    data class TradingHalted(
        val reason: String,
        val message: String
    ) : RiskManagerEvent()
}

/**
 * Risk status for a strategy.
 */
data class StrategyRiskStatus(
    val strategyId: String,
    val riskState: RiskState,
    val drawdownPercent: Double,
    val maxDrawdownPercent: Double,
    val currentValue: Double,
    val peakValue: Double,
    val isWithinLimits: Boolean
)

/**
 * Minimal Strategy Risk Manager for build compatibility.
 * Full implementation should integrate with PositionManager and MarginSafeguard.
 */
class StrategyRiskManager(
    private val maxDrawdownPercent: Double = 15.0,
    private val warningDrawdownPercent: Double = 10.0
) {
    private val _state = MutableStateFlow(RiskState.NORMAL)
    val state: StateFlow<RiskState> = _state.asStateFlow()
    
    private val _events = MutableStateFlow<RiskManagerEvent?>(null)
    val events: StateFlow<RiskManagerEvent?> = _events.asStateFlow()
    
    private val strategies = mutableMapOf<String, StrategyRiskStatus>()
    private var peakPortfolioValue = 0.0
    
    /**
     * Register a strategy for monitoring.
     */
    fun registerStrategy(strategyId: String, initialValue: Double) {
        strategies[strategyId] = StrategyRiskStatus(
            strategyId = strategyId,
            riskState = RiskState.NORMAL,
            drawdownPercent = 0.0,
            maxDrawdownPercent = maxDrawdownPercent,
            currentValue = initialValue,
            peakValue = initialValue,
            isWithinLimits = true
        )
    }
    
    /**
     * Update strategy value and check risk limits.
     */
    fun updateStrategyValue(strategyId: String, newValue: Double) {
        val status = strategies[strategyId] ?: return
        
        val newPeak = maxOf(status.peakValue, newValue)
        val drawdown = if (newPeak > 0) ((newPeak - newValue) / newPeak) * 100 else 0.0
        
        val newRiskState = when {
            drawdown >= maxDrawdownPercent -> RiskState.CRITICAL
            drawdown >= warningDrawdownPercent -> RiskState.WARNING
            else -> RiskState.NORMAL
        }
        
        strategies[strategyId] = status.copy(
            currentValue = newValue,
            peakValue = newPeak,
            drawdownPercent = drawdown,
            riskState = newRiskState,
            isWithinLimits = drawdown < maxDrawdownPercent
        )
        
        // Update global state to worst case
        _state.value = strategies.values.maxOfOrNull { it.riskState } ?: RiskState.NORMAL
        
        // Emit events if thresholds crossed
        when (newRiskState) {
            RiskState.WARNING -> {
                _events.value = RiskManagerEvent.DrawdownWarning(
                    strategyId = strategyId,
                    drawdownPercent = drawdown,
                    reason = "Drawdown warning at ${String.format("%.1f", drawdown)}%"
                )
            }
            RiskState.CRITICAL -> {
                _events.value = RiskManagerEvent.CriticalDrawdown(
                    strategyId = strategyId,
                    drawdownPercent = drawdown,
                    reason = "Critical drawdown at ${String.format("%.1f", drawdown)}%"
                )
            }
            else -> {}
        }
    }
    
    /**
     * Get risk status for a strategy.
     */
    fun getRiskStatus(strategyId: String): StrategyRiskStatus? {
        return strategies[strategyId]
    }
    
    /**
     * Shutdown the risk manager.
     */
    fun shutdown() {
        strategies.clear()
        _state.value = RiskState.HALTED
    }
}
