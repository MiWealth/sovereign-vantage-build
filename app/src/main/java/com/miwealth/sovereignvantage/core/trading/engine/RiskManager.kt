package com.miwealth.sovereignvantage.core.trading.engine

import com.miwealth.sovereignvantage.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.abs
import kotlin.math.max

/**
 * Risk Manager - Enforces trading limits and provides kill switch
 * 
 * Implements comprehensive risk controls:
 * - Maximum drawdown limits
 * - Daily loss limits
 * - Position size limits
 * - Correlation checks
 * - Emergency kill switch
 * 
 * © 2025 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO: Arthur Iain McManus (1966-2025)
 */

data class RiskConfig(
    val maxDrawdownPercent: Double = 50.0,      // Maximum portfolio drawdown before halt (BUILD #169: was 20%, increased to 50% for hedging)
    val dailyLossLimitPercent: Double = 60.0,    // Maximum daily loss before halt (increased for Hedge engine with Alpha Factor Scanner)
    val maxPositionPercent: Double = 25.0,       // Maximum single position size
    val maxTotalExposurePercent: Double = 100.0, // Maximum total exposure (can be >100% with leverage)
    val maxCorrelatedExposure: Double = 40.0,    // Maximum exposure to correlated assets
    val maxLeverage: Double = 3.0,               // Maximum allowed leverage
    val minCashReservePercent: Double = 10.0,    // Minimum cash reserve
    val cooldownMinutes: Int = 15,               // Cooldown after risk event
    val requireManualResetAfterKillSwitch: Boolean = true
)

sealed class RiskEvent {
    data class DrawdownWarning(val currentDrawdown: Double, val limit: Double) : RiskEvent()
    data class DailyLossWarning(val currentLoss: Double, val limit: Double) : RiskEvent()
    data class PositionSizeRejected(val symbol: String, val requestedSize: Double, val maxAllowed: Double) : RiskEvent()
    data class ExposureLimitReached(val currentExposure: Double, val limit: Double) : RiskEvent()
    data class KillSwitchActivated(val reason: String) : RiskEvent()
    data class TradingResumed(val afterCooldown: Boolean) : RiskEvent()
    object CooldownStarted : RiskEvent()
    data class CooldownEnded(val durationMinutes: Int) : RiskEvent()
}

sealed class RiskCheckResult {
    object Approved : RiskCheckResult()
    data class Rejected(val reason: String) : RiskCheckResult()
    data class Warning(val message: String) : RiskCheckResult()
}

class RiskManager(
    private val positionManager: PositionManager,
    private val config: RiskConfig = RiskConfig(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    
    // State
    private var portfolioHighWaterMark: Double = 0.0
    private var dailyStartingValue: Double = 0.0
    private var lastResetDay: Long = 0
    private var isKillSwitchActive: Boolean = false
    private var isInCooldown: Boolean = false
    private var cooldownEndTime: Long = 0
    
    private val _riskEvents = MutableSharedFlow<RiskEvent>(replay = 0, extraBufferCapacity = 32)
    val riskEvents: SharedFlow<RiskEvent> = _riskEvents.asSharedFlow()
    
    private val _isTradingAllowed = MutableStateFlow(true)
    val isTradingAllowed: StateFlow<Boolean> = _isTradingAllowed.asStateFlow()
    
    /**
     * Initialize with starting portfolio value
     */
    fun initialize(portfolioValue: Double) {
        portfolioHighWaterMark = portfolioValue
        dailyStartingValue = portfolioValue
        lastResetDay = getCurrentDay()
        _isTradingAllowed.value = true
        isKillSwitchActive = false
    }
    
    /**
     * Update portfolio value - called on each price update
     */
    suspend fun updatePortfolioValue(currentValue: Double) {
        // Check for new day
        val today = getCurrentDay()
        if (today != lastResetDay) {
            dailyStartingValue = currentValue
            lastResetDay = today
        }
        
        // Update high water mark
        if (currentValue > portfolioHighWaterMark) {
            portfolioHighWaterMark = currentValue
        }
        
        // Check cooldown
        if (isInCooldown && System.currentTimeMillis() >= cooldownEndTime) {
            endCooldown()
        }
        
        // Check drawdown
        val drawdown = ((portfolioHighWaterMark - currentValue) / portfolioHighWaterMark) * 100
        if (drawdown >= config.maxDrawdownPercent * 0.8) {
            _riskEvents.emit(RiskEvent.DrawdownWarning(drawdown, config.maxDrawdownPercent))
        }
        if (drawdown >= config.maxDrawdownPercent) {
            activateKillSwitch("Maximum drawdown exceeded: ${String.format("%.2f", drawdown)}%")
        }
        
        // Check daily loss
        val dailyLoss = ((dailyStartingValue - currentValue) / dailyStartingValue) * 100
        if (dailyLoss >= config.dailyLossLimitPercent * 0.8 && dailyLoss < config.dailyLossLimitPercent) {
            _riskEvents.emit(RiskEvent.DailyLossWarning(dailyLoss, config.dailyLossLimitPercent))
        }
        if (dailyLoss >= config.dailyLossLimitPercent) {
            activateKillSwitch("Daily loss limit exceeded: ${String.format("%.2f", dailyLoss)}%")
        }
    }
    
    /**
     * Pre-trade risk check
     */
    fun checkTradeAllowed(
        symbol: String,
        quantity: Double,
        price: Double,
        side: TradeSide,
        leverage: Double = 1.0,
        portfolioValue: Double
    ): RiskCheckResult {
        // Check kill switch
        if (isKillSwitchActive) {
            return RiskCheckResult.Rejected("Kill switch is active - trading halted")
        }
        
        // Check cooldown
        if (isInCooldown) {
            val remainingMinutes = (cooldownEndTime - System.currentTimeMillis()) / 60000
            return RiskCheckResult.Rejected("In cooldown period - $remainingMinutes minutes remaining")
        }
        
        // Check leverage
        if (leverage > config.maxLeverage) {
            return RiskCheckResult.Rejected("Leverage ${leverage}x exceeds maximum ${config.maxLeverage}x")
        }
        
        // Check position size
        val tradeValue = quantity * price
        val positionPercent = (tradeValue / portfolioValue) * 100
        
        if (positionPercent > config.maxPositionPercent) {
            scope.launch {
                _riskEvents.emit(RiskEvent.PositionSizeRejected(
                    symbol,
                    positionPercent,
                    config.maxPositionPercent
                ))
            }
            return RiskCheckResult.Rejected(
                "Position size ${String.format("%.1f", positionPercent)}% exceeds maximum ${config.maxPositionPercent}%"
            )
        }
        
        // Check total exposure
        val summary = positionManager.getPositionSummary()
        val newTotalExposure = summary.totalExposure + tradeValue
        val exposurePercent = (newTotalExposure / portfolioValue) * 100
        
        if (exposurePercent > config.maxTotalExposurePercent) {
            scope.launch {
                _riskEvents.emit(RiskEvent.ExposureLimitReached(
                    exposurePercent,
                    config.maxTotalExposurePercent
                ))
            }
            return RiskCheckResult.Rejected(
                "Total exposure ${String.format("%.1f", exposurePercent)}% would exceed limit ${config.maxTotalExposurePercent}%"
            )
        }
        
        // Check cash reserve
        val cashUsed = summary.totalMargin + (tradeValue / leverage)
        val cashReservePercent = ((portfolioValue - cashUsed) / portfolioValue) * 100
        
        if (cashReservePercent < config.minCashReservePercent) {
            return RiskCheckResult.Warning(
                "Trade would reduce cash reserve below ${config.minCashReservePercent}%"
            )
        }
        
        // Passed all checks
        return RiskCheckResult.Approved
    }
    
    /**
     * Calculate maximum allowed position size
     */
    fun getMaxPositionSize(price: Double, portfolioValue: Double, leverage: Double = 1.0): Double {
        val maxValue = portfolioValue * (config.maxPositionPercent / 100)
        return maxValue / price
    }
    
    /**
     * Activate kill switch - halt all trading
     */
    suspend fun activateKillSwitch(reason: String) {
        if (!isKillSwitchActive) {
            isKillSwitchActive = true
            _isTradingAllowed.value = false
            _riskEvents.emit(RiskEvent.KillSwitchActivated(reason))
            
            // Optionally close all positions
            // positionManager.closeAllPositions()
        }
    }
    
    /**
     * Manually reset kill switch (requires user action)
     */
    fun resetKillSwitch() {
        if (config.requireManualResetAfterKillSwitch) {
            isKillSwitchActive = false
            startCooldown()
        }
    }
    
    /**
     * Start cooldown period
     */
    private fun startCooldown() {
        isInCooldown = true
        cooldownEndTime = System.currentTimeMillis() + (config.cooldownMinutes * 60 * 1000)
        scope.launch {
            _riskEvents.emit(RiskEvent.CooldownStarted)
        }
    }
    
    /**
     * End cooldown period
     */
    private fun endCooldown() {
        isInCooldown = false
        _isTradingAllowed.value = true
        scope.launch {
            _riskEvents.emit(RiskEvent.CooldownEnded(config.cooldownMinutes))
            _riskEvents.emit(RiskEvent.TradingResumed(true))
        }
    }
    
    /**
     * Get current risk status
     */
    fun getRiskStatus(currentPortfolioValue: Double): RiskStatus {
        val drawdown = if (portfolioHighWaterMark > 0) {
            ((portfolioHighWaterMark - currentPortfolioValue) / portfolioHighWaterMark) * 100
        } else 0.0
        
        val dailyPnl = if (dailyStartingValue > 0) {
            ((currentPortfolioValue - dailyStartingValue) / dailyStartingValue) * 100
        } else 0.0
        
        val summary = positionManager.getPositionSummary()
        val exposurePercent = if (currentPortfolioValue > 0) {
            (summary.totalExposure / currentPortfolioValue) * 100
        } else 0.0
        
        return RiskStatus(
            isKillSwitchActive = isKillSwitchActive,
            isInCooldown = isInCooldown,
            isTradingAllowed = _isTradingAllowed.value,
            currentDrawdown = drawdown,
            maxDrawdownLimit = config.maxDrawdownPercent,
            drawdownUtilization = (drawdown / config.maxDrawdownPercent) * 100,
            dailyPnl = dailyPnl,
            dailyLossLimit = config.dailyLossLimitPercent,
            dailyLossUtilization = if (dailyPnl < 0) (abs(dailyPnl) / config.dailyLossLimitPercent) * 100 else 0.0,
            totalExposure = exposurePercent,
            maxExposure = config.maxTotalExposurePercent,
            exposureUtilization = (exposurePercent / config.maxTotalExposurePercent) * 100,
            openPositions = summary.totalPositions,
            highWaterMark = portfolioHighWaterMark
        )
    }
    
    /**
     * Update risk config
     */
    fun updateConfig(newConfig: RiskConfig) {
        // Note: In a real implementation, this would be more sophisticated
        // For now, we just note that config changes would require restart
    }
    
    private fun getCurrentDay(): Long {
        return System.currentTimeMillis() / (24 * 60 * 60 * 1000)
    }
    
    /**
     * Clean up resources
     */
    fun shutdown() {
        scope.cancel()
    }
}

data class RiskStatus(
    val isKillSwitchActive: Boolean,
    val isInCooldown: Boolean,
    val isTradingAllowed: Boolean,
    val currentDrawdown: Double,
    val maxDrawdownLimit: Double,
    val drawdownUtilization: Double,
    val dailyPnl: Double,
    val dailyLossLimit: Double,
    val dailyLossUtilization: Double,
    val totalExposure: Double,
    val maxExposure: Double,
    val exposureUtilization: Double,
    val openPositions: Int,
    val highWaterMark: Double
) {
    val overallRiskLevel: RiskLevel
        get() = when {
            isKillSwitchActive -> RiskLevel.CRITICAL
            drawdownUtilization > 80 || dailyLossUtilization > 80 -> RiskLevel.HIGH
            drawdownUtilization > 50 || dailyLossUtilization > 50 || exposureUtilization > 80 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
}

enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
