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
    val strategyName: String = "",
    val riskState: RiskState,
    val drawdownPercent: Double,
    val maxDrawdownPercent: Double,
    val currentValue: Double,
    val peakValue: Double,
    val isWithinLimits: Boolean
)

/**
 * Configuration for strategy risk management.
 */
data class StrategyRiskConfig(
    val strategyDrawdownKillSwitch: Double = 15.0,
    val portfolioDrawdownKillSwitch: Double = 20.0,
    val dailyLossKillSwitch: Double = 5.0,
    val warningThresholdPercent: Double = 10.0
)

/**
 * Strategy Risk Manager - monitors strategy performance and triggers risk events.
 * Integrates with PositionManager and ExchangeAggregator for real data.
 */
class StrategyRiskManager(
    private val exchangeAggregator: Any? = null,
    private val positionManager: Any? = null,
    private val config: StrategyRiskConfig = StrategyRiskConfig()
) {
    private val _state = MutableStateFlow(RiskState.NORMAL)
    val state: StateFlow<RiskState> = _state.asStateFlow()
    
    private val _events = MutableStateFlow<RiskManagerEvent?>(null)
    val events: StateFlow<RiskManagerEvent?> = _events.asStateFlow()
    
    private val strategies = mutableMapOf<String, StrategyRiskStatus>()
    private var peakPortfolioValue = 0.0
    private var initialPortfolioValue = 0.0
    private var isMonitoring = false
    
    /**
     * Initialize with portfolio value.
     */
    fun initialize(portfolioValue: Double) {
        initialPortfolioValue = portfolioValue
        peakPortfolioValue = portfolioValue
    }
    
    /**
     * Register a strategy for monitoring.
     */
    fun registerStrategy(strategyId: String, strategyName: String, initialValue: Double) {
        strategies[strategyId] = StrategyRiskStatus(
            strategyId = strategyId,
            strategyName = strategyName,
            riskState = RiskState.NORMAL,
            drawdownPercent = 0.0,
            maxDrawdownPercent = config.strategyDrawdownKillSwitch,
            currentValue = initialValue,
            peakValue = initialValue,
            isWithinLimits = true
        )
    }
    
    /**
     * Start continuous monitoring.
     */
    fun startMonitoring() {
        isMonitoring = true
    }
    
    /**
     * Stop monitoring.
     */
    fun stopMonitoring() {
        isMonitoring = false
    }
    
    /**
     * Manual restart after halt.
     */
    fun manualRestart() {
        _state.value = RiskState.NORMAL
        isMonitoring = true
    }
    
    /**
     * Manual restart with new portfolio value after halt.
     */
    fun manualRestart(newPortfolioValue: Double) {
        initialPortfolioValue = newPortfolioValue
        peakPortfolioValue = newPortfolioValue
        // Clear all strategy tracking - they'll re-register
        strategies.clear()
        _state.value = RiskState.NORMAL
        isMonitoring = true
    }
    
    /**
     * Update strategy value and check risk limits.
     */
    fun updateStrategyValue(strategyId: String, newValue: Double) {
        val status = strategies[strategyId] ?: return
        
        val newPeak = maxOf(status.peakValue, newValue)
        val drawdown = if (newPeak > 0) ((newPeak - newValue) / newPeak) * 100 else 0.0
        
        val newRiskState = when {
            drawdown >= config.strategyDrawdownKillSwitch -> RiskState.CRITICAL
            drawdown >= config.warningThresholdPercent -> RiskState.WARNING
            else -> RiskState.NORMAL
        }
        
        strategies[strategyId] = status.copy(
            currentValue = newValue,
            peakValue = newPeak,
            drawdownPercent = drawdown,
            riskState = newRiskState,
            isWithinLimits = drawdown < config.strategyDrawdownKillSwitch
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
     * Get overall risk status (first strategy or null).
     */
    fun getRiskStatus(): StrategyRiskStatus? {
        return strategies.values.firstOrNull()
    }
    
    /**
     * Shutdown the risk manager.
     */
    fun shutdown() {
        isMonitoring = false
        strategies.clear()
        _state.value = RiskState.HALTED
    }
}
