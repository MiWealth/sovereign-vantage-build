package com.miwealth.sovereignvantage.ui.trading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miwealth.sovereignvantage.core.TradingSystemManager
import com.miwealth.sovereignvantage.core.TradeSide
import com.miwealth.sovereignvantage.core.OrderType
import com.miwealth.sovereignvantage.core.trading.engine.OrderRequest
import com.miwealth.sovereignvantage.core.trading.engine.MarginRiskState
import com.miwealth.sovereignvantage.core.trading.assets.PipelineStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.sample  // BUILD #116: For throttling UI updates
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SOVEREIGN VANTAGE V5.17.0 "ARTHUR EDITION"
 * TRADING VIEW MODEL
 * 
 * V5.17.0 CHANGES:
 * - isTestnetMode wired from DashboardState for testnet safety banner
 * 
 * V5.17.0 CHANGES:
 * - Added execution mode display (PAPER/LIVE)
 * - Added portfolio value & balance tracking
 * - Added margin status indicators
 * - Integrated with TradingSystemIntegration via TradingSystemManager
 * 
 * Connects UI to TradingSystemManager for:
 * - Real-time price updates
 * - Order placement (spot & futures)
 * - AI signal execution
 * - Position management
 * 
 * Supports both paper trading (FREE tier) and live trading (BRONZE+).
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * 
 * Dedicated to Cathryn for tolerating me and my quirks. 💘
 */

data class TradingUiState(
    // Loading state
    val isLoading: Boolean = false,
    val isExecuting: Boolean = false,
    
    // Trading mode
    val isPaperTrading: Boolean = true,
    val isSystemReady: Boolean = false,
    
    // NEW: Execution mode & AI integration status
    val executionMode: String = "PAPER",  // PAPER, PAPER_WITH_LIVE_DATA, LIVE_AI, LIVE_HARDCODED
    val isUsingAIIntegration: Boolean = true,
    
    // NEW: Portfolio & Balance
    val portfolioValue: Double = 0.0,
    val availableBalance: Double = 0.0,
    val dailyPnl: Double = 0.0,
    val dailyPnlPercent: Double = 0.0,
    
    // NEW: Margin Status
    val marginLevel: MarginLevelUi = MarginLevelUi.HEALTHY,
    val freeMarginPercent: Double = 100.0,
    val usedMarginPercent: Double = 0.0,
    val marginWarning: String? = null,
    
    // NEW: Asset Discovery Pipeline Status
    val pipelineStatus: PipelineStatusUi = PipelineStatusUi.IDLE,
    val discoveredAssets: Int = 0,
    val registeredAssets: Int = 0,
    val currentDiscoveryAsset: String? = null,
    val lastDiscoveryTime: String? = null,
    
    // Selected trading pair
    val selectedPair: String = "BTC/USD",
    val currentPrice: Double = 0.0,
    val priceChange: Double = 0.0,
    val bid: Double = 0.0,
    val ask: Double = 0.0,
    
    // V5.18.20: Real chart data from Binance feed
    val candleData: List<com.miwealth.sovereignvantage.ui.components.CandleData> = emptyList(),
    val selectedTimeframe: com.miwealth.sovereignvantage.ui.components.ChartTimeframe = com.miwealth.sovereignvantage.ui.components.ChartTimeframe.H4,
    
    // Futures settings
    val leverage: Int = 5,
    val maxLeverage: Int = 125,
    
    // AI Signals from TradingCoordinator
    val signals: List<TradingSignal> = emptyList(),
    val pendingSignalCount: Int = 0,
    
    // Open positions
    val positions: List<PositionInfo> = emptyList(),
    
    // Order result
    val lastOrderResult: OrderResult? = null,
    
    // Emergency Kill Switch
    val killSwitchActive: Boolean = false,
    
    // V5.17.0: Testnet mode indicator for safety UI
    val isTestnetMode: Boolean = false,
    
    // V5.17.0: ML Health Monitor status
    val mlHealthStatus: String = "HEALTHY",  // HEALTHY, WARNING, CRITICAL
    val mlHealthSummary: String? = null,
    val mlRollbackCount: Int = 0,
    // V5.17.0: Disagreement detection
    val disagreementLevel: String = "STRONG_AGREEMENT",
    val positionSizeMultiplier: Double = 1.0,
    // V5.17.0: Board regime-aware position sizing (read-only indicator)
    val effectivePositionMultiplier: Double = 1.0,  // boardRec × disagreement — displayed to user
    
    // Error state
    val error: String? = null
)

/**
 * UI representation of margin health levels.
 */
enum class MarginLevelUi {
    HEALTHY,      // > 35% free margin - green
    WARNING,      // 30-35% - yellow
    MARGIN_CALL,  // 25-30% - orange
    CRITICAL      // < 25% - red
}

/**
 * UI representation of asset discovery pipeline status.
 */
enum class PipelineStatusUi {
    IDLE,         // Not running
    DISCOVERING,  // Finding assets from exchanges
    ENRICHING,    // Adding DeFiLlama data
    ASSIGNING,    // Assigning risk parameters
    REGISTERING,  // Registering in AssetRegistry
    COMPLETE,     // Done
    ERROR         // Failed
}

data class PositionInfo(
    val symbol: String,
    val side: String,
    val quantity: Double,
    val entryPrice: Double,
    val currentPrice: Double,
    val unrealizedPnl: Double,
    val unrealizedPnlPercent: Double
)

sealed class OrderResult {
    data class Success(val message: String, val orderId: String? = null) : OrderResult()
    data class Error(val message: String) : OrderResult()
}

@HiltViewModel
class TradingViewModel @Inject constructor(
    private val tradingSystemManager: TradingSystemManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(TradingUiState())
    val uiState: StateFlow<TradingUiState> = _uiState.asStateFlow()
    
    init {
        observeTradingSystem()
        loadInitialData()
    }
    
    // ========================================================================
    // SYSTEM OBSERVATION
    // ========================================================================
    
    private fun observeTradingSystem() {
        // Observe system readiness
        viewModelScope.launch {
            tradingSystemManager.isReady.collect { ready ->
                _uiState.update { it.copy(isSystemReady = ready) }
                if (ready) {
                    refreshPositions()
                    refreshSignals()
                    refreshExecutionMode()
                    refreshMarginStatus()
                }
            }
        }
        
        // Observe dashboard state for trading mode, portfolio, and kill switch
        viewModelScope.launch {
            // BUILD #116 FIX 1: Sample every 1 second to prevent screen flashing
            tradingSystemManager.dashboardState
                .sample(1000L)
                .collect { dashState ->
                // V5.18.0: Get live price for selected trading pair from Binance feed
                val selectedPair = _uiState.value.selectedPair
                val livePrice = dashState.latestPrices[selectedPair] ?: _uiState.value.currentPrice
                
                _uiState.update { current ->
                    current.copy(
                        isPaperTrading = dashState.paperTradingMode,
                        pendingSignalCount = dashState.pendingSignalCount,
                        killSwitchActive = dashState.killSwitchActive,
                        // Portfolio updates
                        portfolioValue = dashState.portfolioValue,
                        dailyPnl = dashState.dailyPnl,
                        dailyPnlPercent = dashState.dailyPnlPercent,
                        // V5.18.0: Live price from Binance public feed
                        currentPrice = livePrice,
                        // Risk warning
                        marginWarning = dashState.riskWarning,
                        // V5.17.0: ML Health Monitor + Disagreement Detection (Option F event bridge)
                        mlHealthStatus = dashState.mlHealthStatus,
                        mlHealthSummary = dashState.mlHealthSummary,
                        mlRollbackCount = dashState.mlRollbackCount,
                        disagreementLevel = dashState.disagreementLevel,
                        positionSizeMultiplier = dashState.positionSizeMultiplier,
                        effectivePositionMultiplier = dashState.effectivePositionMultiplier,
                        isTestnetMode = dashState.isTestnetMode  // V5.17.0
                    )
                }
            }
        }
        
        // Observe margin status (AI system only)
        viewModelScope.launch {
            while (true) {
                if (tradingSystemManager.isReady.value) {
                    refreshMarginStatus()
                }
                kotlinx.coroutines.delay(2000) // Update every 2 seconds
            }
        }
        
        // V5.18.20: Observe real candle data from Binance feed
        viewModelScope.launch {
            val feed = com.miwealth.sovereignvantage.core.exchange.BinancePublicPriceFeed.getInstance()
            // BUILD #116 FIX 1: Sample every 2 seconds to prevent chart flashing
            // Candles update every 60s (1m timeframe), so 2s throttle is safe
            feed.candleData
                .sample(2000L)
                .collect { candleMap ->
                val selectedPair = _uiState.value.selectedPair
                // Map USD to USDT for lookup (BTC/USD -> BTC/USDT)
                val binanceSymbol = selectedPair.replace("/USD", "/USDT")
                val binanceCandles = candleMap[binanceSymbol] ?: emptyList()
                
                // Convert OHLCVCandle to CandleData
                val uiCandles = binanceCandles.map { candle ->
                    com.miwealth.sovereignvantage.ui.components.CandleData(
                        timestamp = candle.openTime,
                        open = candle.open,
                        high = candle.high,
                        low = candle.low,
                        close = candle.close,
                        volume = candle.volume
                    )
                }
                
                _uiState.update { current ->
                    current.copy(candleData = uiCandles)
                }
            }
        }
    }
    
    /**
     * Refresh execution mode from TradingSystemManager.
     */
    private fun refreshExecutionMode() {
        val isAI = tradingSystemManager.isUsingAIIntegration()
        val mode = tradingSystemManager.getExecutionMode()?.name ?: "PAPER"
        
        _uiState.update { current ->
            current.copy(
                executionMode = mode,
                isUsingAIIntegration = isAI
            )
        }
    }
    
    /**
     * Refresh margin status from MarginSafeguard.
     */
    private fun refreshMarginStatus() {
        val marginStatus = tradingSystemManager.getMarginStatus()
        
        if (marginStatus != null) {
            val marginLevelUi = when (marginStatus.riskState) {
                MarginRiskState.HEALTHY -> MarginLevelUi.HEALTHY
                MarginRiskState.WARNING -> MarginLevelUi.WARNING
                MarginRiskState.MARGIN_CALL -> MarginLevelUi.MARGIN_CALL
                MarginRiskState.CRITICAL, MarginRiskState.LIQUIDATING -> MarginLevelUi.CRITICAL
            }
            
            _uiState.update { current ->
                current.copy(
                    marginLevel = marginLevelUi,
                    freeMarginPercent = marginStatus.freeMarginPercent,
                    usedMarginPercent = 100.0 - marginStatus.freeMarginPercent,
                    availableBalance = marginStatus.freeMargin
                )
            }
        } else {
            // No margin data - assume healthy (paper trading without leverage)
            _uiState.update { current ->
                current.copy(
                    marginLevel = MarginLevelUi.HEALTHY,
                    freeMarginPercent = 100.0,
                    usedMarginPercent = 0.0
                )
            }
        }
    }
    
    private fun loadInitialData() {
        // Set placeholder signals until real ones arrive
        _uiState.update { current ->
            current.copy(
                signals = listOf(
                    TradingSignal(
                        id = "demo_1",
                        symbol = "BTC/USD",
                        action = "long",
                        entry = "97,500",
                        target = "102,000",
                        stop = "95,000",
                        confidence = 87,
                        source = "Demo"
                    ),
                    TradingSignal(
                        id = "demo_2",
                        symbol = "ETH/USD",
                        action = "long",
                        entry = "3,800",
                        target = "4,200",
                        stop = "3,600",
                        confidence = 82,
                        source = "Demo"
                    )
                )
            )
        }
    }
    
    // ========================================================================
    // TRADING PAIR SELECTION
    // ========================================================================
    
    fun selectPair(pair: String) {
        _uiState.update { it.copy(selectedPair = pair, isLoading = true) }
        
        // Subscribe to price updates for this pair
        tradingSystemManager.subscribeToPrices(listOf(pair))
        
        // Simulate price update (will be replaced by real data)
        viewModelScope.launch {
            // TODO: Connect to real price stream from TradingSystemManager
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    // ========================================================================
    // ORDER EXECUTION
    // ========================================================================
    
    /**
     * Execute a spot trade (buy or sell).
     */
    fun executeTrade(isBuy: Boolean, amount: Double) {
        if (!_uiState.value.isSystemReady) {
            _uiState.update { 
                it.copy(error = "Trading system not ready. Please wait...")
            }
            return
        }
        
        if (amount <= 0) {
            _uiState.update { it.copy(error = "Invalid amount") }
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isExecuting = true, error = null, lastOrderResult = null) }
            
            try {
                val side = if (isBuy) TradeSide.BUY else TradeSide.SELL
                val symbol = _uiState.value.selectedPair
                
                // Create order request
                val orderRequest = OrderRequest(
                    symbol = symbol,
                    side = side,
                    type = OrderType.MARKET,
                    quantity = amount,
                    leverage = if (_uiState.value.leverage > 1) _uiState.value.leverage else null
                )
                
                // Execute through TradingSystemManager
                val result = tradingSystemManager.placeOrder(orderRequest)
                
                result.onSuccess { executedTrade ->
                    _uiState.update { current ->
                        current.copy(
                            isExecuting = false,
                            lastOrderResult = OrderResult.Success(
                                message = "${if (isBuy) "Bought" else "Sold"} $amount $symbol",
                                orderId = executedTrade.orderId
                            )
                        )
                    }
                    // Refresh positions
                    refreshPositions()
                }.onFailure { error ->
                    _uiState.update { current ->
                        current.copy(
                            isExecuting = false,
                            lastOrderResult = OrderResult.Error(
                                error.message ?: "Order failed"
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { current ->
                    current.copy(
                        isExecuting = false,
                        error = e.message ?: "Trade execution failed"
                    )
                }
            }
        }
    }
    
    /**
     * Execute an AI-generated signal.
     */
    fun executeSignal(signal: TradingSignal) {
        if (!_uiState.value.isSystemReady) {
            _uiState.update { 
                it.copy(error = "Trading system not ready. Please wait...")
            }
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isExecuting = true, error = null) }
            
            try {
                // If this is a real signal from TradingCoordinator, confirm it
                if (signal.id.isNotEmpty() && !signal.id.startsWith("demo_")) {
                    val result = tradingSystemManager.confirmSignal(signal.id)
                    
                    result.onSuccess { trade ->
                        _uiState.update { current ->
                            current.copy(
                                isExecuting = false,
                                lastOrderResult = OrderResult.Success(
                                    message = "Signal executed: ${signal.symbol} ${signal.action.uppercase()}",
                                    orderId = trade.orderId
                                )
                            )
                        }
                        refreshPositions()
                        refreshSignals()
                    }.onFailure { error ->
                        _uiState.update { current ->
                            current.copy(
                                isExecuting = false,
                                lastOrderResult = OrderResult.Error(
                                    error.message ?: "Signal execution failed"
                                )
                            )
                        }
                    }
                } else {
                    // Demo signal - execute as regular trade
                    val isBuy = signal.action == "long"
                    _uiState.update { it.copy(isExecuting = false) }
                    executeTrade(isBuy, 0.01) // Default small amount for demo
                }
            } catch (e: Exception) {
                _uiState.update { current ->
                    current.copy(
                        isExecuting = false,
                        error = e.message ?: "Signal execution failed"
                    )
                }
            }
        }
    }
    
    /**
     * Reject a pending signal.
     */
    fun rejectSignal(signalId: String) {
        tradingSystemManager.rejectSignal(signalId)
        refreshSignals()
    }
    
    // ========================================================================
    // POSITION MANAGEMENT
    // ========================================================================
    
    /**
     * Close an open position.
     */
    fun closePosition(symbol: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExecuting = true) }
            
            val result = tradingSystemManager.closePosition(symbol)
            
            result.onSuccess {
                _uiState.update { current ->
                    current.copy(
                        isExecuting = false,
                        lastOrderResult = OrderResult.Success("Position closed: $symbol")
                    )
                }
                refreshPositions()
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(
                        isExecuting = false,
                        lastOrderResult = OrderResult.Error(
                            error.message ?: "Failed to close position"
                        )
                    )
                }
            }
        }
    }
    
    private fun refreshPositions() {
        val managedPositions = tradingSystemManager.getPositions()
        val positionInfos = managedPositions.map { pos ->
            PositionInfo(
                symbol = pos.symbol,
                side = pos.direction.name,
                quantity = pos.quantity,
                entryPrice = pos.entryPrice,
                currentPrice = pos.currentPrice,
                unrealizedPnl = pos.unrealizedPnL,
                unrealizedPnlPercent = pos.unrealizedPnLPercent
            )
        }
        _uiState.update { it.copy(positions = positionInfos) }
    }
    
    private fun refreshSignals() {
        val pendingSignals = tradingSystemManager.getPendingSignals()
        
        // Convert to UI model
        val tradingSignals = pendingSignals.map { signal ->
            TradingSignal(
                id = signal.id,
                symbol = signal.symbol,
                action = if (signal.direction == com.miwealth.sovereignvantage.core.trading.TradeDirection.LONG) "long" else "short",
                entry = "%.2f".format(signal.suggestedEntry),
                target = "%.2f".format(signal.suggestedTarget),
                stop = "%.2f".format(signal.suggestedStop),
                confidence = (signal.confidence * 100).toInt(),
                source = "AI Board"
            )
        }
        
        // Keep demo signals if no real ones
        val finalSignals = if (tradingSignals.isNotEmpty()) {
            tradingSignals
        } else {
            _uiState.value.signals.filter { it.id.startsWith("demo_") }
        }
        
        _uiState.update { it.copy(signals = finalSignals, pendingSignalCount = tradingSignals.size) }
    }
    
    // ========================================================================
    // V5.18.20: CHART TIMEFRAME
    // ========================================================================
    
    /**
     * Change chart timeframe and fetch new candle data.
     */
    fun changeTimeframe(timeframe: com.miwealth.sovereignvantage.ui.components.ChartTimeframe) {
        _uiState.update { it.copy(selectedTimeframe = timeframe) }
        
        // Tell BinancePublicPriceFeed to fetch candles for this timeframe
        val feed = com.miwealth.sovereignvantage.core.exchange.BinancePublicPriceFeed.getInstance()
        feed.setCandleTimeframe(timeframe.minutes)
    }
    
    // ========================================================================
    // LEVERAGE
    // ========================================================================
    
    fun setLeverage(leverage: Int) {
        val maxLev = _uiState.value.maxLeverage
        _uiState.update { it.copy(leverage = leverage.coerceIn(1, maxLev)) }
    }
    
    // ========================================================================
    // EMERGENCY KILL SWITCH
    // ========================================================================
    
    /**
     * Activate emergency kill switch - closes all positions and halts trading.
     * This is a critical safety feature for our HNW clients.
     */
    fun emergencyStop() {
        tradingSystemManager.activateKillSwitch("User activated emergency stop")
    }
    
    /**
     * Reset the kill switch to resume trading.
     * Requires manual confirmation - automatic reset is intentionally disabled.
     */
    fun resetKillSwitch() {
        tradingSystemManager.resetKillSwitch()
    }
    
    // ========================================================================
    // UTILITY
    // ========================================================================
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    fun clearOrderResult() {
        _uiState.update { it.copy(lastOrderResult = null) }
    }
}

// =============================================================================
// TRADING SIGNAL DATA CLASS
// =============================================================================

data class TradingSignal(
    val id: String = "",
    val symbol: String,
    val action: String,  // "long" or "short"
    val entry: String,
    val target: String,
    val stop: String,
    val confidence: Int,
    val source: String = "AI Board"
)
