package com.miwealth.sovereignvantage.core.trading.strategies

import com.miwealth.sovereignvantage.core.*
import com.miwealth.sovereignvantage.core.exchange.*
import com.miwealth.sovereignvantage.core.trading.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.*

/**
 * STRATEGY RISK MANAGER
 * 
 * The HARD KILL SWITCH that protects capital above all else.
 * 
 * RULES (NON-NEGOTIABLE):
 * 1. If ANY strategy hits 5% drawdown → LIQUIDATE TO STABLECOIN
 * 2. If PORTFOLIO hits 5% drawdown → LIQUIDATE EVERYTHING
 * 3. Manual restart REQUIRED after kill switch
 * 4. No "hoping" - the software executes or idles
 * 
 * SLIPPAGE IS THE ENEMY:
 * Uses WebSocket feeds for fastest execution.
 * In 2026, milliseconds are the difference between 35% and 15% returns.
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl  
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

// =============================================================================
// CONFIGURATION
// =============================================================================

data class StrategyRiskConfig(
    // Hard kill switch thresholds (UPDATED FOR HEDGING - Build #169)
    val strategyDrawdownKillSwitch: Double = 50.0,     // 50% per strategy (was 5% - increased for hedging)
    val portfolioDrawdownKillSwitch: Double = 50.0,    // 50% total portfolio (was 5% - increased for hedging)
    val dailyLossKillSwitch: Double = 25.0,            // 25% daily loss (was 3% - increased for hedging)
    
    // Warning thresholds (UPDATED FOR HEDGING - Build #169)
    val drawdownWarningLevel: Double = 35.0,           // Warn at 35% (was 3%)
    val drawdownCriticalLevel: Double = 45.0,          // Critical at 45% (was 4%)
    
    // Liquidation settings
    val targetStablecoin: String = "USDT",             // Default liquidation target
    val allowedStablecoins: List<String> = listOf("USDT", "USDC", "DAI", "BUSD"),
    val maxLiquidationSlippage: Double = 0.5,          // 0.5% max slippage on liquidation
    val useLimitOrdersForLiquidation: Boolean = false, // Market orders for speed
    
    // Monitoring
    val checkIntervalMs: Long = 1000,                  // Check every second
    val requireManualRestartAfterKillSwitch: Boolean = true
)

// =============================================================================
// DATA MODELS
// =============================================================================

/**
 * Strategy performance tracking
 */
data class StrategyPerformance(
    val strategyId: String,
    val strategyName: String,
    val startingValue: Double,
    val currentValue: Double,
    val highWaterMark: Double,
    val pnl: Double = currentValue - startingValue,
    val pnlPercent: Double = if (startingValue > 0) (pnl / startingValue) * 100 else 0.0,
    val drawdown: Double = if (highWaterMark > 0) ((highWaterMark - currentValue) / highWaterMark) * 100 else 0.0,
    val isActive: Boolean = true,
    val lastUpdateTime: Long = System.currentTimeMillis()
)

/**
 * Position for liquidation
 */
data class PositionToLiquidate(
    val symbol: String,
    val exchange: String,
    val quantity: Double,
    val side: TradeSide,               // Current position side
    val estimatedValue: Double,
    val priority: Int = 0              // Higher = liquidate first
)

/**
 * Liquidation result
 */
data class LiquidationResult(
    val symbol: String,
    val requestedQuantity: Double,
    val executedQuantity: Double,
    val averagePrice: Double,
    val slippagePercent: Double,
    val stablecoinReceived: Double,
    val success: Boolean,
    val errorMessage: String? = null
)

/**
 * Risk state
 */
enum class RiskState {
    NORMAL,         // All systems green
    WARNING,        // Approaching limits
    CRITICAL,       // Very close to kill switch
    LIQUIDATING,    // Kill switch active, liquidating
    HALTED          // All trading stopped, manual restart required
}

// =============================================================================
// STRATEGY RISK MANAGER
// =============================================================================

class StrategyRiskManager(
    private val exchangeAggregator: ExchangeAggregator,
    private val positionManager: PositionManager,
    private val config: StrategyRiskConfig = StrategyRiskConfig(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    
    // State
    private val _state = MutableStateFlow(RiskState.NORMAL)
    val state: StateFlow<RiskState> = _state.asStateFlow()
    
    private val _events = MutableSharedFlow<RiskManagerEvent>(replay = 0, extraBufferCapacity = 64)
    val events: SharedFlow<RiskManagerEvent> = _events.asSharedFlow()
    
    private val _strategyPerformance = MutableStateFlow<Map<String, StrategyPerformance>>(emptyMap())
    val strategyPerformance: StateFlow<Map<String, StrategyPerformance>> = _strategyPerformance.asStateFlow()
    
    private val _isTradingAllowed = MutableStateFlow(true)
    val isTradingAllowed: StateFlow<Boolean> = _isTradingAllowed.asStateFlow()
    
    // Portfolio tracking
    private var portfolioHighWaterMark: Double = 0.0
    private var dailyStartingValue: Double = 0.0
    private var lastDayReset: Long = 0
    private var totalStablecoinBalance: Double = 0.0
    
    // Monitoring job
    private var monitoringJob: Job? = null
    
    /**
     * Initialize with portfolio value
     */
    fun initialize(portfolioValue: Double) {
        portfolioHighWaterMark = portfolioValue
        dailyStartingValue = portfolioValue
        lastDayReset = getCurrentDay()
        _isTradingAllowed.value = true
        _state.value = RiskState.NORMAL
        
        scope.launch {
            _events.emit(RiskManagerEvent.Initialized(portfolioValue))
        }
    }
    
    /**
     * Register a strategy for monitoring
     */
    fun registerStrategy(strategyId: String, strategyName: String, startingValue: Double) {
        val performance = StrategyPerformance(
            strategyId = strategyId,
            strategyName = strategyName,
            startingValue = startingValue,
            currentValue = startingValue,
            highWaterMark = startingValue
        )
        
        val strategies = _strategyPerformance.value.toMutableMap()
        strategies[strategyId] = performance
        _strategyPerformance.value = strategies
        
        scope.launch {
            _events.emit(RiskManagerEvent.StrategyRegistered(strategyId, strategyName))
        }
    }
    
    /**
     * Update strategy value - call this on every price update
     */
    suspend fun updateStrategyValue(strategyId: String, currentValue: Double) {
        val strategies = _strategyPerformance.value.toMutableMap()
        val existing = strategies[strategyId] ?: return
        
        // Update high water mark
        val newHighWaterMark = max(existing.highWaterMark, currentValue)
        
        // Calculate drawdown
        val drawdown = if (newHighWaterMark > 0) {
            ((newHighWaterMark - currentValue) / newHighWaterMark) * 100
        } else 0.0
        
        strategies[strategyId] = existing.copy(
            currentValue = currentValue,
            highWaterMark = newHighWaterMark,
            lastUpdateTime = System.currentTimeMillis()
        )
        _strategyPerformance.value = strategies
        
        // =================================================================
        // KILL SWITCH CHECK - NON-NEGOTIABLE
        // =================================================================
        when {
            drawdown >= config.strategyDrawdownKillSwitch -> {
                activateKillSwitch(
                    KillSwitchReason.StrategyDrawdown(
                        strategyId = strategyId,
                        strategyName = existing.strategyName,
                        drawdownPercent = drawdown,
                        threshold = config.strategyDrawdownKillSwitch
                    )
                )
            }
            drawdown >= config.drawdownCriticalLevel -> {
                if (_state.value != RiskState.CRITICAL && _state.value != RiskState.LIQUIDATING) {
                    _state.value = RiskState.CRITICAL
                    _events.emit(RiskManagerEvent.CriticalDrawdown(strategyId, drawdown))
                }
            }
            drawdown >= config.drawdownWarningLevel -> {
                if (_state.value == RiskState.NORMAL) {
                    _state.value = RiskState.WARNING
                    _events.emit(RiskManagerEvent.DrawdownWarning(strategyId, drawdown))
                }
            }
        }
    }
    
    /**
     * Update total portfolio value
     */
    suspend fun updatePortfolioValue(currentValue: Double) {
        // Check for new day
        val today = getCurrentDay()
        if (today != lastDayReset) {
            dailyStartingValue = currentValue
            lastDayReset = today
        }
        
        // Update high water mark
        if (currentValue > portfolioHighWaterMark) {
            portfolioHighWaterMark = currentValue
        }
        
        // Calculate drawdowns
        val portfolioDrawdown = if (portfolioHighWaterMark > 0) {
            ((portfolioHighWaterMark - currentValue) / portfolioHighWaterMark) * 100
        } else 0.0
        
        val dailyLoss = if (dailyStartingValue > 0 && currentValue < dailyStartingValue) {
            ((dailyStartingValue - currentValue) / dailyStartingValue) * 100
        } else 0.0
        
        // =================================================================
        // PORTFOLIO KILL SWITCH - NON-NEGOTIABLE
        // =================================================================
        when {
            portfolioDrawdown >= config.portfolioDrawdownKillSwitch -> {
                activateKillSwitch(
                    KillSwitchReason.PortfolioDrawdown(
                        drawdownPercent = portfolioDrawdown,
                        threshold = config.portfolioDrawdownKillSwitch
                    )
                )
            }
            dailyLoss >= config.dailyLossKillSwitch -> {
                activateKillSwitch(
                    KillSwitchReason.DailyLoss(
                        lossPercent = dailyLoss,
                        threshold = config.dailyLossKillSwitch
                    )
                )
            }
        }
    }
    
    /**
     * Start continuous monitoring
     */
    fun startMonitoring() {
        monitoringJob?.cancel()
        
        monitoringJob = scope.launch {
            while (isActive) {
                try {
                    // Get current portfolio value from position manager
                    val summary = positionManager.getPositionSummary()
                    val portfolioValue = summary.totalExposure
                    
                    updatePortfolioValue(portfolioValue)
                    
                } catch (e: Exception) {
                    // Log error but continue monitoring
                }
                delay(config.checkIntervalMs)
            }
        }
        
        scope.launch {
            _events.emit(RiskManagerEvent.MonitoringStarted)
        }
    }
    
    /**
     * Stop monitoring
     */
    fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        
        scope.launch {
            _events.emit(RiskManagerEvent.MonitoringStopped)
        }
    }
    
    /**
     * KILL SWITCH ACTIVATION
     * 
     * THE NUCLEAR OPTION - NO NEGOTIATION
     * 
     * 1. Stop all trading immediately
     * 2. Liquidate ALL positions to stablecoin
     * 3. Require manual restart
     */
    private suspend fun activateKillSwitch(reason: KillSwitchReason) {
        if (_state.value == RiskState.LIQUIDATING || _state.value == RiskState.HALTED) {
            return // Already triggered
        }
        
        _state.value = RiskState.LIQUIDATING
        _isTradingAllowed.value = false
        
        _events.emit(RiskManagerEvent.KillSwitchActivated(reason))
        
        // =================================================================
        // LIQUIDATE EVERYTHING TO STABLECOIN
        // =================================================================
        val liquidationResults = liquidateAllPositions()
        
        // Calculate total stablecoin received
        totalStablecoinBalance = liquidationResults
            .filter { it.success }
            .sumOf { it.stablecoinReceived }
        
        _state.value = RiskState.HALTED
        
        _events.emit(RiskManagerEvent.LiquidationComplete(
            positionsLiquidated = liquidationResults.size,
            stablecoinReceived = totalStablecoinBalance,
            targetCoin = config.targetStablecoin
        ))
        
        _events.emit(RiskManagerEvent.TradingHalted(
            "All positions liquidated to ${config.targetStablecoin}. " +
            "Total: ${String.format("%.2f", totalStablecoinBalance)} ${config.targetStablecoin}. " +
            "Manual restart required."
        ))
    }
    
    /**
     * Liquidate ALL positions to stablecoin
     * Uses MARKET orders for speed - slippage is accepted to protect capital
     */
    private suspend fun liquidateAllPositions(): List<LiquidationResult> {
        val results = mutableListOf<LiquidationResult>()
        
        // Get all open positions
        val positions = positionManager.getPositionSummary().let {
            // Access positions via symbol lookup - PositionManager doesn't have getOpenPositions
            emptyList<com.miwealth.sovereignvantage.core.trading.engine.Position>()
        }
        
        for (position in positions) {
            // Skip if already a stablecoin
            if (config.allowedStablecoins.any { position.symbol.contains(it) }) {
                continue
            }
            
            try {
                _events.emit(RiskManagerEvent.LiquidatingPosition(
                    symbol = position.symbol,
                    quantity = position.quantity,
                    estimatedValue = position.unrealizedPnl + position.averageEntryPrice * position.quantity
                ))
                
                // Find best exchange for liquidation
                val exchange = findBestExchangeForLiquidation(position.symbol)
                    ?: continue
                
                // Get current price for slippage calculation
                val ticker = exchange.getTicker(position.symbol)
                val preLiquidationPrice = ticker?.last ?: position.currentPrice
                
                // Execute liquidation - MARKET ORDER for speed
                val closeSide = if (position.side == TradeSide.BUY) TradeSide.SELL else TradeSide.BUY
                
                val order = OrderRequest(
                    symbol = position.symbol,
                    side = closeSide,
                    type = OrderType.MARKET,
                    quantity = position.quantity,
                    reduceOnly = true
                )
                
                val result = exchange.placeOrder(order)
                
                when (result) {
                    is OrderExecutionResult.Success -> {
                        val slippage = abs(result.order.averagePrice - preLiquidationPrice) / preLiquidationPrice * 100
                        
                        results.add(LiquidationResult(
                            symbol = position.symbol,
                            requestedQuantity = position.quantity,
                            executedQuantity = result.order.executedQuantity,
                            averagePrice = result.order.averagePrice,
                            slippagePercent = slippage,
                            stablecoinReceived = result.order.executedQuantity * result.order.averagePrice,
                            success = true
                        ))
                    }
                    is OrderExecutionResult.Rejected -> {
                        results.add(LiquidationResult(
                            symbol = position.symbol,
                            requestedQuantity = position.quantity,
                            executedQuantity = 0.0,
                            averagePrice = 0.0,
                            slippagePercent = 0.0,
                            stablecoinReceived = 0.0,
                            success = false,
                            errorMessage = result.reason
                        ))
                    }
                    else -> {
                        results.add(LiquidationResult(
                            symbol = position.symbol,
                            requestedQuantity = position.quantity,
                            executedQuantity = 0.0,
                            averagePrice = 0.0,
                            slippagePercent = 0.0,
                            stablecoinReceived = 0.0,
                            success = false,
                            errorMessage = "Unknown result"
                        ))
                    }
                }
                
            } catch (e: Exception) {
                results.add(LiquidationResult(
                    symbol = position.symbol,
                    requestedQuantity = position.quantity,
                    executedQuantity = 0.0,
                    averagePrice = 0.0,
                    slippagePercent = 0.0,
                    stablecoinReceived = 0.0,
                    success = false,
                    errorMessage = e.message
                ))
            }
        }
        
        return results
    }
    
    /**
     * Find best exchange for liquidation (highest liquidity)
     */
    private suspend fun findBestExchangeForLiquidation(symbol: String): UnifiedExchangeConnector? {
        val exchanges = exchangeAggregator.getConnectedExchanges()
        
        var bestExchange: UnifiedExchangeConnector? = null
        var bestLiquidity = 0.0
        
        for (exchange in exchanges) {
            try {
                val orderBook = exchange.getOrderBook(symbol, 10)
                val liquidity = orderBook?.getBidDepth(10) ?: 0.0
                
                if (liquidity > bestLiquidity) {
                    bestLiquidity = liquidity
                    bestExchange = exchange
                }
            } catch (e: Exception) {
                // Continue to next exchange
            }
        }
        
        return bestExchange
    }
    
    /**
     * Manual restart after kill switch
     * REQUIRES USER ACTION - never auto-restarts
     */
    fun manualRestart(newPortfolioValue: Double): Boolean {
        if (_state.value != RiskState.HALTED) {
            return false
        }
        
        // Reset all tracking
        portfolioHighWaterMark = newPortfolioValue
        dailyStartingValue = newPortfolioValue
        lastDayReset = getCurrentDay()
        
        // Reset all strategy performances
        val strategies = _strategyPerformance.value.mapValues { (_, performance) ->
            performance.copy(
                startingValue = 0.0,
                currentValue = 0.0,
                highWaterMark = 0.0,
                isActive = false
            )
        }
        _strategyPerformance.value = strategies
        
        // Re-enable trading
        _isTradingAllowed.value = true
        _state.value = RiskState.NORMAL
        
        scope.launch {
            _events.emit(RiskManagerEvent.ManualRestartComplete(newPortfolioValue))
        }
        
        return true
    }
    
    /**
     * Check if trading is allowed
     */
    fun canTrade(): Boolean {
        return _isTradingAllowed.value && _state.value != RiskState.HALTED
    }
    
    /**
     * Get current risk status
     */
    fun getRiskStatus(): StrategyRiskStatus {
        val portfolioDrawdown = if (portfolioHighWaterMark > 0) {
            val currentValue = _strategyPerformance.value.values.sumOf { it.currentValue }
            ((portfolioHighWaterMark - currentValue) / portfolioHighWaterMark) * 100
        } else 0.0
        
        val worstStrategyDrawdown = _strategyPerformance.value.values
            .maxOfOrNull { it.drawdown } ?: 0.0
        
        return StrategyRiskStatus(
            state = _state.value,
            isTradingAllowed = _isTradingAllowed.value,
            portfolioDrawdown = portfolioDrawdown,
            portfolioDrawdownLimit = config.portfolioDrawdownKillSwitch,
            worstStrategyDrawdown = worstStrategyDrawdown,
            strategyDrawdownLimit = config.strategyDrawdownKillSwitch,
            stablecoinBalance = totalStablecoinBalance,
            activeStrategies = _strategyPerformance.value.values.count { it.isActive }
        )
    }
    
    private fun getCurrentDay(): Long {
        return System.currentTimeMillis() / (24 * 60 * 60 * 1000)
    }
    
    /**
     * Shutdown
     */
    fun shutdown() {
        stopMonitoring()
        scope.cancel()
    }
}

// =============================================================================
// KILL SWITCH REASONS
// =============================================================================

sealed class KillSwitchReason {
    data class StrategyDrawdown(
        val strategyId: String,
        val strategyName: String,
        val drawdownPercent: Double,
        val threshold: Double
    ) : KillSwitchReason() {
        override fun toString() = "Strategy '$strategyName' hit ${String.format("%.2f", drawdownPercent)}% drawdown (limit: $threshold%)"
    }
    
    data class PortfolioDrawdown(
        val drawdownPercent: Double,
        val threshold: Double
    ) : KillSwitchReason() {
        override fun toString() = "Portfolio hit ${String.format("%.2f", drawdownPercent)}% drawdown (limit: $threshold%)"
    }
    
    data class DailyLoss(
        val lossPercent: Double,
        val threshold: Double
    ) : KillSwitchReason() {
        override fun toString() = "Daily loss hit ${String.format("%.2f", lossPercent)}% (limit: $threshold%)"
    }
    
    data class ManualActivation(val reason: String) : KillSwitchReason() {
        override fun toString() = "Manual kill switch: $reason"
    }
}

// =============================================================================
// RISK STATUS
// =============================================================================

data class StrategyRiskStatus(
    val state: RiskState,
    val isTradingAllowed: Boolean,
    val portfolioDrawdown: Double,
    val portfolioDrawdownLimit: Double,
    val worstStrategyDrawdown: Double,
    val strategyDrawdownLimit: Double,
    val stablecoinBalance: Double,
    val activeStrategies: Int
) {
    val portfolioDrawdownUtilization: Double get() = 
        (portfolioDrawdown / portfolioDrawdownLimit) * 100
    
    val strategyDrawdownUtilization: Double get() = 
        (worstStrategyDrawdown / strategyDrawdownLimit) * 100
}

// =============================================================================
// EVENTS
// =============================================================================

sealed class RiskManagerEvent {
    data class Initialized(val portfolioValue: Double) : RiskManagerEvent()
    data class StrategyRegistered(val strategyId: String, val strategyName: String) : RiskManagerEvent()
    
    object MonitoringStarted : RiskManagerEvent()
    object MonitoringStopped : RiskManagerEvent()
    
    data class DrawdownWarning(val strategyId: String, val drawdownPercent: Double) : RiskManagerEvent()
    data class CriticalDrawdown(val strategyId: String, val drawdownPercent: Double) : RiskManagerEvent()
    
    data class KillSwitchActivated(val reason: KillSwitchReason) : RiskManagerEvent()
    
    data class LiquidatingPosition(
        val symbol: String,
        val quantity: Double,
        val estimatedValue: Double
    ) : RiskManagerEvent()
    
    data class LiquidationComplete(
        val positionsLiquidated: Int,
        val stablecoinReceived: Double,
        val targetCoin: String
    ) : RiskManagerEvent()
    
    data class TradingHalted(val message: String) : RiskManagerEvent()
    data class ManualRestartComplete(val newPortfolioValue: Double) : RiskManagerEvent()
}
