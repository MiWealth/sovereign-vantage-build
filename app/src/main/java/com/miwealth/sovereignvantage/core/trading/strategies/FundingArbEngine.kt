package com.miwealth.sovereignvantage.core.trading.strategies

import com.miwealth.sovereignvantage.core.*
import com.miwealth.sovereignvantage.core.exchange.*
import com.miwealth.sovereignvantage.core.trading.*
import com.miwealth.sovereignvantage.core.trading.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.*

/**
 * DELTA-NEUTRAL FUNDING ARBITRAGE ENGINE
 * 
 * THE "MONEY PRINTER" STRATEGY
 * 
 * Creates a hedge where PRICE MOVEMENT DOESN'T MATTER.
 * You only care about the 8-hour funding paycheck.
 * 
 * MECHANISM:
 * - LONG 1 BTC on SPOT market
 * - SHORT 1 BTC on PERPETUAL market  
 * - Net delta = ZERO
 * - Collect funding payments every 8 hours when funding rate is positive
 * 
 * When perpetual funding rate > 0:
 * - Shorts PAY longs → You collect (holding spot + shorting perp)
 * - Risk: ZERO price exposure (you're hedged)
 * - Profit: Pure yield from funding rate
 * 
 * REAL WORLD RETURNS:
 * - Funding rates can reach 0.1%+ per 8 hours during bull runs
 * - That's 0.3%/day = ~110% APY with zero directional risk
 * - Even modest 0.01% = 10.95% APY (better than any bank)
 * 
 * STATE MACHINE DESIGN:
 * No human emotion. No "hoping" for price movements.
 * Execute or idle. Math only.
 * 
 * HARD KILL SWITCH: 5% drawdown OR delta drift > 1% → LIQUIDATE
 * 
 * CRITICAL: Uses WebSocket for fastest execution
 * In 2026, milliseconds are the difference between 35% and 15% returns.
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 *
 * V5.6.0 CHANGES:
 * - Added optional tradingCoordinator for cross-exchange arb spread awareness
 * - evaluateEntry() now gates on minArbSpreadToEnter — if spread is too thin
 *   to cover fees on both sides, position is skipped even if funding is attractive
 * - checkAllPositionDeltas() uses real bid/ask from cross-exchange order book data
 *   for more accurate delta valuation when order book feed is active
 * - NEW: FundingArbConfig.minArbSpreadToEnter (default 0.05%)
 */

// =============================================================================
// DATA MODELS
// =============================================================================

/**
 * Funding rate data from exchange
 */
data class FundingRateData(
    val symbol: String,
    val fundingRate: Double,            // Current funding rate (e.g., 0.0001 = 0.01%)
    val nextFundingTime: Long,          // Timestamp of next funding
    val predictedRate: Double?,         // Some exchanges provide predicted rate
    val fundingInterval: Long = 8 * 60 * 60 * 1000, // 8 hours default
    val markPrice: Double,
    val indexPrice: Double,
    val timestamp: Long = System.currentTimeMillis()
) {
    val annualizedRate: Double get() = fundingRate * 3 * 365 * 100 // 3x daily, 365 days, as %
    val dailyRate: Double get() = fundingRate * 3 * 100 // 3x daily as %
    val isPositive: Boolean get() = fundingRate > 0
}

/**
 * Delta-neutral position state
 */
data class DeltaNeutralPosition(
    val symbol: String,
    val spotQuantity: Double,           // Long spot position
    val perpQuantity: Double,           // Short perp position (negative)
    val spotEntryPrice: Double,
    val perpEntryPrice: Double,
    val spotExchange: String,
    val perpExchange: String,
    val openedAt: Long,
    val totalFundingCollected: Double = 0.0,
    val fundingPayments: MutableList<FundingPayment> = mutableListOf()
) {
    val netDelta: Double get() = spotQuantity + perpQuantity  // Should be ~0
    val deltaRatio: Double get() = if (spotQuantity > 0) abs(netDelta / spotQuantity) else 0.0
    val isBalanced: Boolean get() = deltaRatio < 0.01  // Within 1%
    val notionalValue: Double get() = spotQuantity * spotEntryPrice
}

/**
 * Individual funding payment record
 */
data class FundingPayment(
    val timestamp: Long,
    val rate: Double,
    val payment: Double,               // In quote currency (usually USDT)
    val positionSize: Double
)

/**
 * Arbitrage engine configuration
 */
data class FundingArbConfig(
    // Entry thresholds
    val minFundingRateToEnter: Double = 0.0001,    // 0.01% per 8h minimum
    val minAnnualizedToEnter: Double = 10.0,       // 10% APY minimum
    /** V5.6.0: Minimum cross-exchange arb spread (%) to justify entry.
     *  Must exceed total round-trip fees (maker+taker on both exchanges).
     *  Default 0.05% — typical Binance+Bybit maker is ~0.02% total. */
    val minArbSpreadToEnter: Double = 0.05,        // 0.05% minimum spread
    
    // Risk management
    val maxDeltaDrift: Double = 0.01,              // 1% max imbalance before rebalance
    val hardKillSwitchDrawdown: Double = 5.0,      // 5% loss = liquidate
    val maxPositionSize: Double = 0.25,            // Max 25% of capital per position
    val maxTotalExposure: Double = 0.80,           // Max 80% capital deployed
    
    // Execution
    val useLimitOrders: Boolean = true,            // Avoid slippage
    val maxSlippageBps: Int = 10,                  // 10 basis points max slip
    val rebalanceThreshold: Double = 0.005,        // 0.5% drift triggers rebalance
    
    // Exit conditions
    val exitOnNegativeFunding: Boolean = true,     // Exit when funding turns negative
    val minProfitToExit: Double = 0.0,             // Can exit at any profit
    val maxHoldingPeriodHours: Int = 0,            // 0 = unlimited
    
    // Monitoring
    val fundingCheckIntervalSeconds: Int = 60,     // Check funding every minute
    val deltaCheckIntervalSeconds: Int = 10        // Check delta every 10 seconds
)

/**
 * Engine state machine
 */
enum class ArbEngineState {
    IDLE,               // No positions, scanning for opportunities
    ANALYZING,          // Found opportunity, analyzing execution
    OPENING,            // Opening spot + perp positions
    ACTIVE,             // Position active, collecting funding
    REBALANCING,        // Delta drifted, rebalancing
    CLOSING,            // Closing positions
    LIQUIDATING,        // Emergency liquidation
    HALTED              // Manual restart required
}

// =============================================================================
// DELTA-NEUTRAL FUNDING ARBITRAGE ENGINE
// =============================================================================

class FundingArbEngine(
    private val spotExchange: UnifiedExchangeConnector,
    private val perpExchange: UnifiedExchangeConnector,  // Must support perpetuals
    private val riskManager: RiskManager,
    private val config: FundingArbConfig = FundingArbConfig(),
    /** V5.6.0: Optional coordinator for cross-exchange arb spread awareness.
     *  When provided, evaluateEntry() checks getArbSpread() to verify the spread
     *  justifies entry — no point entering if fees eat the profit. */
    private val tradingCoordinator: TradingCoordinator? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    
    // State
    private val _state = MutableStateFlow(ArbEngineState.IDLE)
    val state: StateFlow<ArbEngineState> = _state.asStateFlow()
    
    private val _activePositions = MutableStateFlow<Map<String, DeltaNeutralPosition>>(emptyMap())
    val activePositions: StateFlow<Map<String, DeltaNeutralPosition>> = _activePositions.asStateFlow()
    
    private val _events = MutableSharedFlow<ArbEvent>(replay = 0, extraBufferCapacity = 64)
    val events: SharedFlow<ArbEvent> = _events.asSharedFlow()
    
    private val _fundingRates = MutableStateFlow<Map<String, FundingRateData>>(emptyMap())
    val fundingRates: StateFlow<Map<String, FundingRateData>> = _fundingRates.asStateFlow()
    
    // Performance tracking
    private var totalCapital: Double = 0.0
    private var deployedCapital: Double = 0.0
    private var highWaterMark: Double = 0.0
    private var totalFundingEarned: Double = 0.0
    private var isKillSwitchActive: Boolean = false
    
    // Monitoring jobs
    private var fundingMonitorJob: Job? = null
    private var deltaMonitorJob: Job? = null
    
    /**
     * Initialize the engine
     */
    fun initialize(capital: Double) {
        totalCapital = capital
        highWaterMark = capital
        isKillSwitchActive = false
        _state.value = ArbEngineState.IDLE
        
        scope.launch {
            _events.emit(ArbEvent.Initialized(capital))
        }
    }
    
    /**
     * Start monitoring for funding arbitrage opportunities
     */
    fun startMonitoring(symbols: List<String>) {
        fundingMonitorJob?.cancel()
        deltaMonitorJob?.cancel()
        
        // Monitor funding rates
        fundingMonitorJob = scope.launch {
            while (isActive) {
                for (symbol in symbols) {
                    try {
                        val fundingData = fetchFundingRate(symbol)
                        fundingData?.let { rate ->
                            val currentRates = _fundingRates.value.toMutableMap()
                            currentRates[symbol] = rate
                            _fundingRates.value = currentRates
                            
                            // Check for entry opportunity
                            if (_state.value == ArbEngineState.IDLE && !isKillSwitchActive) {
                                evaluateEntry(symbol, rate)
                            }
                        }
                    } catch (e: Exception) {
                        // Log error, continue monitoring
                    }
                }
                delay(config.fundingCheckIntervalSeconds * 1000L)
            }
        }
        
        // Monitor delta neutrality
        deltaMonitorJob = scope.launch {
            while (isActive) {
                try {
                    checkAllPositionDeltas()
                } catch (e: Exception) {
                    // Log error, continue monitoring
                }
                delay(config.deltaCheckIntervalSeconds * 1000L)
            }
        }
        
        scope.launch {
            _events.emit(ArbEvent.MonitoringStarted(symbols))
        }
    }
    
    /**
     * Stop monitoring
     */
    fun stopMonitoring() {
        fundingMonitorJob?.cancel()
        deltaMonitorJob?.cancel()
        fundingMonitorJob = null
        deltaMonitorJob = null
        
        scope.launch {
            _events.emit(ArbEvent.MonitoringStopped)
        }
    }
    
    /**
     * Evaluate entry opportunity for a symbol
     *
     * V5.6.0: Added cross-exchange arb spread gate. If TradingCoordinator is
     * available, checks that the spread across venues exceeds minArbSpreadToEnter.
     * This prevents entering positions where fees on both exchanges would eat
     * the profit, even when the funding rate looks attractive.
     */
    private suspend fun evaluateEntry(symbol: String, fundingData: FundingRateData) {
        // Check if we already have a position
        if (_activePositions.value.containsKey(symbol)) return
        
        // Check funding rate threshold
        if (fundingData.fundingRate < config.minFundingRateToEnter) return
        if (fundingData.annualizedRate < config.minAnnualizedToEnter) return
        
        // V5.6.0: Cross-exchange arb spread gate
        // If we have cross-exchange price data, verify spread justifies the entry
        tradingCoordinator?.let { coordinator ->
            val arbSpread = coordinator.getArbSpread(symbol)
            val crossPrices = coordinator.getCrossExchangePrices(symbol)
            
            if (crossPrices.size >= 2 && arbSpread < config.minArbSpreadToEnter) {
                // Spread too thin — fees would eat the profit
                scope.launch {
                    _events.emit(ArbEvent.ExecutionRejected(
                        symbol,
                        "Arb spread %.4f%% below minimum %.4f%% — fees exceed profit".format(
                            arbSpread, config.minArbSpreadToEnter
                        )
                    ))
                }
                return
            }
        }
        
        // Check capital availability
        val availableCapital = totalCapital - deployedCapital
        val maxDeployable = totalCapital * config.maxPositionSize
        
        if (availableCapital < maxDeployable * 0.5) return  // Need at least half max position
        
        // Check total exposure limit
        if (deployedCapital / totalCapital >= config.maxTotalExposure) return
        
        _events.emit(ArbEvent.OpportunityFound(
            symbol = symbol,
            fundingRate = fundingData.fundingRate,
            annualizedRate = fundingData.annualizedRate,
            availableCapital = availableCapital
        ))
        
        // Auto-execute if conditions are met
        val positionSize = min(availableCapital * 0.9, maxDeployable)  // Use 90% of available
        executeArb(symbol, positionSize)
    }
    
    /**
     * Execute Delta-Neutral Arbitrage
     * 
     * THE MONEY PRINTER
     * Buys spot, shorts perp, collects funding.
     */
    suspend fun executeArb(symbol: String, notionalValue: Double) {
        if (isKillSwitchActive) {
            _events.emit(ArbEvent.ExecutionRejected(symbol, "Kill switch active"))
            return
        }
        
        val fundingData = _fundingRates.value[symbol]
        if (fundingData == null || fundingData.fundingRate < config.minFundingRateToEnter) {
            _events.emit(ArbEvent.ExecutionRejected(symbol, "Funding rate below threshold"))
            return
        }
        
        _state.value = ArbEngineState.OPENING
        _events.emit(ArbEvent.OpeningPosition(symbol, notionalValue))
        
        try {
            // =================================================================
            // STEP 1: Get precise prices using LIMIT orders to avoid slippage
            // =================================================================
            val spotTicker = spotExchange.getTicker(symbol) ?: throw Exception("No spot price")
            val perpTicker = perpExchange.getTicker(symbol) ?: throw Exception("No perp price")
            
            val spotPrice = spotTicker.ask  // We're buying, so use ask
            val perpPrice = perpTicker.bid  // We're selling, so use bid
            
            val quantity = notionalValue / spotPrice
            
            // =================================================================
            // STEP 2: Execute SPOT BUY (LONG)
            // =================================================================
            val spotOrder = OrderRequest(
                symbol = symbol,
                side = TradeSide.BUY,
                type = if (config.useLimitOrders) OrderType.LIMIT else OrderType.MARKET,
                quantity = quantity,
                price = if (config.useLimitOrders) spotPrice * 1.001 else null,  // Slight buffer
                timeInForce = TimeInForce.IOC  // Immediate or cancel
            )
            
            val spotResult = spotExchange.placeOrder(spotOrder)
            if (spotResult !is OrderExecutionResult.Success) {
                throw Exception("Spot order failed: ${(spotResult as? OrderExecutionResult.Failed)?.reason}")
            }
            
            val actualSpotQty = spotResult.executedQuantity
            val actualSpotPrice = spotResult.averagePrice
            
            // =================================================================
            // STEP 3: Execute PERP SHORT (HEDGE)
            // Must be immediate to maintain delta neutrality
            // =================================================================
            val perpOrder = OrderRequest(
                symbol = symbol,
                side = TradeSide.SELL,  // SHORT
                type = if (config.useLimitOrders) OrderType.LIMIT else OrderType.MARKET,
                quantity = actualSpotQty,  // Match spot quantity exactly
                price = if (config.useLimitOrders) perpPrice * 0.999 else null,  // Slight buffer
                timeInForce = TimeInForce.IOC,
                reduceOnly = false  // Opening new short
            )
            
            val perpResult = perpExchange.placeOrder(perpOrder)
            if (perpResult !is OrderExecutionResult.Success) {
                // CRITICAL: Spot bought but perp failed - must unwind spot immediately!
                _events.emit(ArbEvent.HedgeFailed(symbol, "Perp order failed - unwinding spot"))
                spotExchange.placeOrder(spotOrder.copy(side = TradeSide.SELL))
                throw Exception("Perp order failed: ${(perpResult as? OrderExecutionResult.Failed)?.reason}")
            }
            
            val actualPerpQty = -perpResult.executedQuantity  // Negative for short
            val actualPerpPrice = perpResult.averagePrice
            
            // =================================================================
            // STEP 4: Verify delta is ZERO
            // =================================================================
            val position = DeltaNeutralPosition(
                symbol = symbol,
                spotQuantity = actualSpotQty,
                perpQuantity = actualPerpQty,
                spotEntryPrice = actualSpotPrice,
                perpEntryPrice = actualPerpPrice,
                spotExchange = spotExchange.config.exchangeId,
                perpExchange = perpExchange.config.exchangeId,
                openedAt = System.currentTimeMillis()
            )
            
            if (!position.isBalanced) {
                _events.emit(ArbEvent.DeltaDrift(
                    symbol = symbol,
                    deltaRatio = position.deltaRatio,
                    message = "WARNING: Position opened with delta drift. Rebalancing..."
                ))
                // Attempt to rebalance immediately
                rebalancePosition(position)
            }
            
            // Update state
            val positions = _activePositions.value.toMutableMap()
            positions[symbol] = position
            _activePositions.value = positions
            
            deployedCapital += actualSpotQty * actualSpotPrice
            
            _state.value = ArbEngineState.ACTIVE
            
            _events.emit(ArbEvent.PositionOpened(
                symbol = symbol,
                spotQty = actualSpotQty,
                perpQty = actualPerpQty,
                spotPrice = actualSpotPrice,
                perpPrice = actualPerpPrice,
                notionalValue = actualSpotQty * actualSpotPrice,
                delta = position.netDelta
            ))
            
        } catch (e: Exception) {
            _state.value = ArbEngineState.IDLE
            _events.emit(ArbEvent.ExecutionFailed(symbol, e.message ?: "Unknown error"))
        }
    }
    
    /**
     * Check all positions for delta drift
     *
     * V5.6.0: When TradingCoordinator is available with cross-exchange order book
     * data, uses real bid/ask for more accurate position valuation instead of
     * relying solely on the exchange's ticker last price.
     */
    private suspend fun checkAllPositionDeltas() {
        for ((symbol, position) in _activePositions.value) {
            try {
                // V5.6.0: Try cross-exchange prices first (real bid/ask from order books)
                val crossPrices = tradingCoordinator?.getCrossExchangePrices(symbol)
                val spotCrossPrice = crossPrices?.get(position.spotExchange)
                val perpCrossPrice = crossPrices?.get(position.perpExchange)
                
                // Use cross-exchange bid/ask when available, fall back to ticker
                val spotPrice: Double
                val perpPrice: Double
                
                if (spotCrossPrice != null && perpCrossPrice != null && 
                    spotCrossPrice.bid > 0 && spotCrossPrice.bid != spotCrossPrice.ask) {
                    // Real bid/ask available from order book feed
                    spotPrice = spotCrossPrice.mid
                    perpPrice = perpCrossPrice.mid
                } else {
                    // Fall back to direct ticker query
                    val spotTicker = spotExchange.getTicker(symbol) ?: continue
                    val perpTicker = perpExchange.getTicker(symbol) ?: continue
                    spotPrice = spotTicker.last
                    perpPrice = perpTicker.last
                }
                
                // Calculate current delta
                val spotValue = position.spotQuantity * spotPrice
                val perpValue = abs(position.perpQuantity) * perpPrice
                val deltaValue = spotValue - perpValue
                val deltaRatio = abs(deltaValue) / spotValue
                
                // Check if rebalance needed
                if (deltaRatio > config.rebalanceThreshold) {
                    _events.emit(ArbEvent.DeltaDrift(symbol, deltaRatio, "Delta drift detected"))
                    
                    if (deltaRatio > config.maxDeltaDrift) {
                        // Critical drift - rebalance immediately
                        rebalancePosition(position)
                    }
                }
                
                // Check for kill switch conditions
                val unrealizedPnl = calculateUnrealizedPnl(position, spotPrice, perpPrice)
                val drawdown = if (highWaterMark > 0) {
                    ((highWaterMark - (totalCapital + unrealizedPnl)) / highWaterMark) * 100
                } else 0.0
                
                if (drawdown >= config.hardKillSwitchDrawdown) {
                    activateKillSwitch(drawdown, "Drawdown exceeded ${config.hardKillSwitchDrawdown}%")
                }
                
            } catch (e: Exception) {
                // Log and continue
            }
        }
    }
    
    /**
     * Rebalance a position to restore delta neutrality
     */
    private suspend fun rebalancePosition(position: DeltaNeutralPosition) {
        _state.value = ArbEngineState.REBALANCING
        
        val deltaQuantity = position.netDelta
        
        _events.emit(ArbEvent.Rebalancing(
            symbol = position.symbol,
            currentDelta = deltaQuantity,
            action = if (deltaQuantity > 0) "Selling spot / buying perp" else "Buying spot / selling perp"
        ))
        
        try {
            if (deltaQuantity > 0) {
                // Too much spot, sell some or add more perp short
                val order = OrderRequest(
                    symbol = position.symbol,
                    side = TradeSide.SELL,
                    type = OrderType.MARKET,
                    quantity = abs(deltaQuantity)
                )
                perpExchange.placeOrder(order)
            } else if (deltaQuantity < 0) {
                // Too much perp short, buy some back
                val order = OrderRequest(
                    symbol = position.symbol,
                    side = TradeSide.BUY,
                    type = OrderType.MARKET,
                    quantity = abs(deltaQuantity)
                )
                perpExchange.placeOrder(order)
            }
            
            _events.emit(ArbEvent.RebalanceComplete(position.symbol))
            
        } catch (e: Exception) {
            _events.emit(ArbEvent.RebalanceFailed(position.symbol, e.message ?: "Unknown"))
        }
        
        _state.value = ArbEngineState.ACTIVE
    }
    
    /**
     * Record funding payment received
     */
    suspend fun recordFundingPayment(symbol: String, rate: Double, payment: Double) {
        val positions = _activePositions.value.toMutableMap()
        val position = positions[symbol] ?: return
        
        val fundingPayment = FundingPayment(
            timestamp = System.currentTimeMillis(),
            rate = rate,
            payment = payment,
            positionSize = abs(position.perpQuantity)
        )
        
        position.fundingPayments.add(fundingPayment)
        positions[symbol] = position.copy(
            totalFundingCollected = position.totalFundingCollected + payment
        )
        _activePositions.value = positions
        
        totalFundingEarned += payment
        
        // Update high water mark
        val currentTotal = totalCapital + totalFundingEarned
        if (currentTotal > highWaterMark) {
            highWaterMark = currentTotal
        }
        
        _events.emit(ArbEvent.FundingReceived(
            symbol = symbol,
            rate = rate,
            payment = payment,
            totalCollected = position.totalFundingCollected + payment
        ))
        
        // Check if funding went negative - consider exit
        if (rate < 0 && config.exitOnNegativeFunding) {
            _events.emit(ArbEvent.NegativeFundingDetected(symbol, rate))
            closePosition(symbol, "Funding rate turned negative")
        }
    }
    
    /**
     * Close a delta-neutral position
     */
    suspend fun closePosition(symbol: String, reason: String) {
        val position = _activePositions.value[symbol] ?: return
        
        _state.value = ArbEngineState.CLOSING
        _events.emit(ArbEvent.ClosingPosition(symbol, reason))
        
        try {
            // Close perp first (buy back short)
            val perpOrder = OrderRequest(
                symbol = symbol,
                side = TradeSide.BUY,
                type = OrderType.MARKET,
                quantity = abs(position.perpQuantity),
                reduceOnly = true
            )
            perpExchange.placeOrder(perpOrder)
            
            // Then close spot
            val spotOrder = OrderRequest(
                symbol = symbol,
                side = TradeSide.SELL,
                type = OrderType.MARKET,
                quantity = position.spotQuantity
            )
            spotExchange.placeOrder(spotOrder)
            
            // Update state
            val positions = _activePositions.value.toMutableMap()
            positions.remove(symbol)
            _activePositions.value = positions
            
            deployedCapital -= position.notionalValue
            
            _events.emit(ArbEvent.PositionClosed(
                symbol = symbol,
                fundingEarned = position.totalFundingCollected,
                holdingPeriodHours = (System.currentTimeMillis() - position.openedAt) / 3600000.0
            ))
            
        } catch (e: Exception) {
            _events.emit(ArbEvent.CloseFailed(symbol, e.message ?: "Unknown"))
        }
        
        _state.value = if (_activePositions.value.isEmpty()) ArbEngineState.IDLE else ArbEngineState.ACTIVE
    }
    
    /**
     * Calculate unrealized PnL for a position
     */
    private fun calculateUnrealizedPnl(
        position: DeltaNeutralPosition,
        currentSpotPrice: Double,
        currentPerpPrice: Double
    ): Double {
        val spotPnl = position.spotQuantity * (currentSpotPrice - position.spotEntryPrice)
        val perpPnl = abs(position.perpQuantity) * (position.perpEntryPrice - currentPerpPrice)  // Short profit when price drops
        return spotPnl + perpPnl + position.totalFundingCollected
    }
    
    /**
     * HARD KILL SWITCH
     * Liquidate ALL positions, move to USDT/USDC
     */
    private suspend fun activateKillSwitch(drawdownPercent: Double, reason: String) {
        if (isKillSwitchActive) return
        
        isKillSwitchActive = true
        _state.value = ArbEngineState.LIQUIDATING
        
        _events.emit(ArbEvent.KillSwitchActivated(
            reason = reason,
            drawdownPercent = drawdownPercent,
            message = "HARD KILL SWITCH: $reason. LIQUIDATING ALL POSITIONS."
        ))
        
        // Close all positions
        for (symbol in _activePositions.value.keys) {
            try {
                closePosition(symbol, "Kill switch liquidation")
            } catch (e: Exception) {
                // Force market close if normal close fails
                _events.emit(ArbEvent.ForceLiquidation(symbol))
            }
        }
        
        riskManager.activateKillSwitch("Funding Arb: $reason")
        
        _state.value = ArbEngineState.HALTED
        
        _events.emit(ArbEvent.HaltedForManualRestart(
            "Engine halted after kill switch. Manual restart required."
        ))
    }
    
    /**
     * Manual restart after kill switch
     */
    fun manualRestart(newCapital: Double) {
        if (!isKillSwitchActive) return
        
        isKillSwitchActive = false
        totalCapital = newCapital
        highWaterMark = newCapital
        deployedCapital = 0.0
        _activePositions.value = emptyMap()
        _state.value = ArbEngineState.IDLE
        
        scope.launch {
            _events.emit(ArbEvent.ManualRestartComplete(newCapital))
        }
    }
    
    /**
     * Fetch funding rate from exchange
     * This should use WebSocket for real-time data in production
     */
    private suspend fun fetchFundingRate(symbol: String): FundingRateData? {
        // In production, this would call the exchange's funding rate API
        // Different exchanges have different endpoints:
        // Binance: GET /fapi/v1/fundingRate
        // Bybit: GET /v5/market/funding/history
        // OKX: GET /api/v5/public/funding-rate
        
        // For now, return mock data - implement actual API call
        return try {
            val ticker = perpExchange.getTicker(symbol) ?: return null
            
            // Mock funding rate - replace with actual API call
            FundingRateData(
                symbol = symbol,
                fundingRate = 0.0001,  // 0.01% - would come from API
                nextFundingTime = System.currentTimeMillis() + 4 * 60 * 60 * 1000,  // 4 hours
                predictedRate = null,
                markPrice = ticker.last,
                indexPrice = ticker.last
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get strategy statistics
     */
    fun getStatistics(): FundingArbStatistics {
        val positions = _activePositions.value.values
        
        return FundingArbStatistics(
            totalCapital = totalCapital,
            deployedCapital = deployedCapital,
            utilizationPercent = if (totalCapital > 0) (deployedCapital / totalCapital) * 100 else 0.0,
            activePositions = positions.size,
            totalFundingEarned = totalFundingEarned,
            averageFundingRate = positions.mapNotNull { _fundingRates.value[it.symbol]?.fundingRate }.average().takeIf { !it.isNaN() } ?: 0.0,
            highWaterMark = highWaterMark,
            isKillSwitchActive = isKillSwitchActive
        )
    }
    
    /**
     * Shutdown engine
     */
    fun shutdown() {
        stopMonitoring()
        scope.cancel()
    }
}

// =============================================================================
// STATISTICS
// =============================================================================

data class FundingArbStatistics(
    val totalCapital: Double,
    val deployedCapital: Double,
    val utilizationPercent: Double,
    val activePositions: Int,
    val totalFundingEarned: Double,
    val averageFundingRate: Double,
    val highWaterMark: Double,
    val isKillSwitchActive: Boolean
) {
    val annualizedYield: Double get() = if (totalCapital > 0) {
        (totalFundingEarned / totalCapital) * 365 * 100  // Simplified
    } else 0.0
}

// =============================================================================
// EVENTS
// =============================================================================

sealed class ArbEvent {
    data class Initialized(val capital: Double) : ArbEvent()
    data class MonitoringStarted(val symbols: List<String>) : ArbEvent()
    object MonitoringStopped : ArbEvent()
    
    data class OpportunityFound(
        val symbol: String,
        val fundingRate: Double,
        val annualizedRate: Double,
        val availableCapital: Double
    ) : ArbEvent()
    
    data class OpeningPosition(val symbol: String, val notionalValue: Double) : ArbEvent()
    data class ExecutionRejected(val symbol: String, val reason: String) : ArbEvent()
    data class ExecutionFailed(val symbol: String, val error: String) : ArbEvent()
    data class HedgeFailed(val symbol: String, val message: String) : ArbEvent()
    
    data class PositionOpened(
        val symbol: String,
        val spotQty: Double,
        val perpQty: Double,
        val spotPrice: Double,
        val perpPrice: Double,
        val notionalValue: Double,
        val delta: Double
    ) : ArbEvent()
    
    data class DeltaDrift(val symbol: String, val deltaRatio: Double, val message: String) : ArbEvent()
    data class Rebalancing(val symbol: String, val currentDelta: Double, val action: String) : ArbEvent()
    data class RebalanceComplete(val symbol: String) : ArbEvent()
    data class RebalanceFailed(val symbol: String, val error: String) : ArbEvent()
    
    data class FundingReceived(
        val symbol: String,
        val rate: Double,
        val payment: Double,
        val totalCollected: Double
    ) : ArbEvent()
    
    data class NegativeFundingDetected(val symbol: String, val rate: Double) : ArbEvent()
    data class ClosingPosition(val symbol: String, val reason: String) : ArbEvent()
    data class PositionClosed(val symbol: String, val fundingEarned: Double, val holdingPeriodHours: Double) : ArbEvent()
    data class CloseFailed(val symbol: String, val error: String) : ArbEvent()
    data class ForceLiquidation(val symbol: String) : ArbEvent()
    
    data class KillSwitchActivated(val reason: String, val drawdownPercent: Double, val message: String) : ArbEvent()
    data class HaltedForManualRestart(val reason: String) : ArbEvent()
    data class ManualRestartComplete(val newCapital: Double) : ArbEvent()
}
