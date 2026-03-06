package com.miwealth.sovereignvantage.ui.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miwealth.sovereignvantage.core.DashboardState
import com.miwealth.sovereignvantage.core.InitializationState
import com.miwealth.sovereignvantage.core.TradingSystemManager
import com.miwealth.sovereignvantage.core.trading.TradingMode
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
 * DASHBOARD VIEW MODEL
 * 
 * V5.17.0 CHANGES:
 * - isTestnetMode wired from DashboardState to DashboardUiState
 * - launchTestnetTrading() action for UI testnet launch button
 * 
 * Connects the Dashboard UI to the TradingSystem via TradingSystemManager.
 * Provides real portfolio data, trading status, and market information.
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * 
 * Dedicated to Cathryn for tolerating me and my quirks. 💘
 */

// Data classes for UI display
data class MarketData(
    val symbol: String,
    val name: String,
    val price: Double,
    val change24h: Double
)

data class TradeData(
    val symbol: String,
    val type: String,  // "buy" or "sell"
    val amount: Double,
    val price: Double,
    val profit: Double,
    val timeAgo: String
)

data class DashboardUiState(
    // Loading & initialization
    val isLoading: Boolean = true,
    val initializationState: InitializationState = InitializationState.NotInitialized,
    val error: String? = null,
    
    // Portfolio values
    val totalPortfolioValue: Double = 100000.0,
    val dailyChange: Double = 0.0,
    val dailyChangePercent: Double = 0.0,
    
    // Trading status
    val aiTradingActive: Boolean = false,
    val tradingMode: TradingMode = TradingMode.SIGNAL_ONLY,
    val paperTradingMode: Boolean = true,
    val killSwitchActive: Boolean = false,
    val riskWarning: String? = null,
    
    // Counts
    val activeStrategies: Int = 8,  // AI Board has 8 members
    val activePositions: Int = 0,
    val pendingSignals: Int = 0,
    val todayTrades: Int = 0,
    
    // Security status
    val pqcSecurityEnabled: Boolean = false,
    val smartRoutingEnabled: Boolean = false,
    val connectedExchanges: Int = 0,
    val activeExchange: String? = null,
    /** V5.17.0: Whether running against testnet/sandbox endpoints */
    val isTestnetMode: Boolean = false,
    
    // Market data (populated from price feed)
    val markets: List<MarketData> = getDefaultMarkets(),
    
    // Recent trades (populated from trade history)
    val recentTrades: List<TradeData> = emptyList()
) {
    companion object {
        fun getDefaultMarkets() = listOf(
            MarketData("BTC", "Bitcoin", 0.0, 0.0),
            MarketData("ETH", "Ethereum", 0.0, 0.0),
            MarketData("SOL", "Solana", 0.0, 0.0),
            MarketData("XRP", "Ripple", 0.0, 0.0),
            MarketData("DOGE", "Dogecoin", 0.0, 0.0)
        )
    }
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val tradingSystemManager: TradingSystemManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()
    
    init {
        observeTradingSystemState()
        initializeTradingSystem()
    }
    
    // ========================================================================
    // INITIALIZATION
    // ========================================================================
    
    private fun initializeTradingSystem() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            // Check if already initialized
            if (tradingSystemManager.isReady.value) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        initializationState = InitializationState.Ready
                    )
                }
                return@launch
            }
            
            // V5.17.0: Auto-connect paper trading with LIVE prices on launch.
            // Randomly pick Binance or Kraken for variety. Start in AUTONOMOUS
            // mode so the AI Board is actively trading from the moment the
            // client logs in. Fall back to mock if live connection fails.
            
            val liveExchanges = listOf("binance", "kraken")
            val selectedExchange = liveExchanges.random()
            
            Log.d("DashboardViewModel", "Auto-connecting paper trading with live data from $selectedExchange")
            
            // Attempt live data first
            val liveResult = tradingSystemManager.initializeAIPaperTradingWithLiveData(
                startingBalance = 100_000.0,
                tradingSymbols = listOf("BTC/USDT", "ETH/USDT", "SOL/USDT", "XRP/USDT"),
                tradingMode = TradingMode.AUTONOMOUS,
                primaryExchangeId = selectedExchange
            )
            
            if (liveResult.isSuccess) {
                // Start full autonomous trading (coordinator + prices + order book)
                tradingSystemManager.startTrading()
                
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        initializationState = InitializationState.Ready,
                        paperTradingMode = true,
                        aiTradingActive = true,
                        activeExchange = selectedExchange
                    )
                }
                Log.i("DashboardViewModel", "✅ Paper trading LIVE + AUTONOMOUS via $selectedExchange")
                return@launch
            }
            
            // Fallback: mock data if live connection fails (no internet, etc.)
            Log.w("DashboardViewModel", "Live connection to $selectedExchange failed, falling back to mock data")
            
            val mockResult = tradingSystemManager.initializePaperTrading(
                startingBalance = 100_000.0
            )
            
            if (mockResult.isSuccess) {
                // Start autonomous trading with mock prices
                tradingSystemManager.setTradingMode(TradingMode.AUTONOMOUS)
                tradingSystemManager.startTrading()
                
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        initializationState = InitializationState.Ready,
                        paperTradingMode = true,
                        aiTradingActive = true
                    )
                }
                Log.i("DashboardViewModel", "✅ Paper trading MOCK + AUTONOMOUS (live fallback)")
            } else {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        initializationState = InitializationState.Error(
                            mockResult.exceptionOrNull()?.message ?: "Unknown error"
                        ),
                        error = mockResult.exceptionOrNull()?.message
                    )
                }
            }
        }
    }
    
    // ========================================================================
    // STATE OBSERVATION
    // ========================================================================
    
    init {
        // BUILD #116: Removed subscribeToTradeEvents() - DashboardViewModel doesn't have access
        // to TradingSystemIntegration.coordinatorEvents. Trade history will be shown via
        // separate route (TradingScreen or dedicated history screen).
    }
    
    private fun observeTradingSystemState() {
        // Observe initialization state
        viewModelScope.launch {
            tradingSystemManager.initializationState.collect { state ->
                _uiState.update { it.copy(initializationState = state) }
            }
        }
        
        // Observe dashboard state from TradingSystem
        viewModelScope.launch {
            // BUILD #116 FIX 1: Sample every 1 second to prevent screen flashing
            // Price updates come in every ~500ms per symbol (4 symbols = 8 updates/sec!)
            // This throttles to max 1 UI update per second
            tradingSystemManager.dashboardState
                .sample(1000L)  // Only emit latest value every 1000ms
                .collect { dashboardState ->
                    updateUiFromDashboardState(dashboardState)
                }
        }
    }
    
    private fun updateUiFromDashboardState(dashboardState: DashboardState) {
        // V5.18.0: Build live market data from Binance public feed
        val liveMarkets = if (dashboardState.latestPrices.isNotEmpty()) {
            val symbolNames = mapOf(
                "BTC" to "Bitcoin", "ETH" to "Ethereum", "SOL" to "Solana",
                "XRP" to "Ripple", "DOGE" to "Dogecoin", "ADA" to "Cardano",
                "AVAX" to "Avalanche", "DOT" to "Polkadot", "LINK" to "Chainlink",
                "MATIC" to "Polygon", "BNB" to "BNB", "LTC" to "Litecoin"
            )
            dashboardState.latestPrices.entries
                .filter { it.value > 0.0 }
                .map { (symbol, price) ->
                    val base = symbol.substringBefore("/")
                    MarketData(
                        symbol = base,
                        name = symbolNames[base] ?: base,
                        price = price,
                        change24h = dashboardState.priceChanges24h[symbol] ?: 0.0
                    )
                }
                .sortedByDescending { it.price }  // BTC first
        } else {
            DashboardUiState.getDefaultMarkets()
        }
        
        _uiState.update { current ->
            current.copy(
                totalPortfolioValue = dashboardState.portfolioValue,
                dailyChange = dashboardState.dailyPnl,
                dailyChangePercent = dashboardState.dailyPnlPercent,
                aiTradingActive = dashboardState.isTradingActive,
                tradingMode = dashboardState.tradingMode,
                paperTradingMode = dashboardState.paperTradingMode,
                killSwitchActive = dashboardState.killSwitchActive,
                riskWarning = dashboardState.riskWarning,
                activePositions = dashboardState.activePositionCount,
                pendingSignals = dashboardState.pendingSignalCount,
                todayTrades = dashboardState.tradesExecutedToday,
                pqcSecurityEnabled = dashboardState.pqcSecurityEnabled,
                smartRoutingEnabled = dashboardState.smartRoutingEnabled,
                connectedExchanges = dashboardState.connectedExchangeCount,
                activeExchange = dashboardState.activeExchange,
                isTestnetMode = dashboardState.isTestnetMode,  // V5.17.0
                markets = liveMarkets  // V5.18.0: Live prices from Binance
            )
        }
    }
    
    // ========================================================================
    // USER ACTIONS
    // ========================================================================
    
    /**
     * Toggle AI trading on/off
     */
    fun toggleAiTrading() {
        if (_uiState.value.aiTradingActive) {
            tradingSystemManager.stopTrading()
        } else {
            tradingSystemManager.startTrading()
        }
    }
    
    /**
     * V5.17.0: Launch testnet trading mode.
     * Initialises single-exchange testnet via TradingSystemManager.
     * Requires exchange credentials with testnet API keys.
     * 
     * @param exchangeId Exchange to connect (e.g. "binance", "bybit")
     * @param credentials Testnet API credentials
     */
    fun launchTestnetTrading(exchangeId: String, credentials: com.miwealth.sovereignvantage.core.exchange.ExchangeCredentials) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            val result = tradingSystemManager.initializeTestnetTrading(
                exchangeId = exchangeId,
                credentials = credentials
            )
            
            result.onSuccess {
                _uiState.update { it.copy(isLoading = false, isTestnetMode = true) }
            }.onFailure { error ->
                _uiState.update { 
                    it.copy(isLoading = false, error = error.message)
                }
            }
        }
    }
    
    /**
     * Set trading mode
     */
    fun setTradingMode(mode: TradingMode) {
        tradingSystemManager.setTradingMode(mode)
    }
    
    /**
     * Activate emergency kill switch
     */
    fun activateKillSwitch() {
        tradingSystemManager.activateKillSwitch("Manual activation from Dashboard")
    }
    
    /**
     * Reset kill switch (requires manual confirmation)
     */
    fun resetKillSwitch() {
        tradingSystemManager.resetKillSwitch()
    }
    
    /**
     * BUILD #107: Force restart the entire trading system.
     * Use this when the system is completely broken.
     */
    fun forceRestartSystem() {
        tradingSystemManager.forceRestartSystem(startingBalance = 100_000.0)
    }
    
    /**
     * Refresh dashboard data
     */
    fun refreshData() {
        // The data is automatically updated via StateFlow collection
        // This can be used to force a manual refresh if needed
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            // Small delay to show loading state
            kotlinx.coroutines.delay(500)
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    /**
     * Confirm a pending signal
     */
    fun confirmSignal(signalId: String) {
        viewModelScope.launch {
            tradingSystemManager.confirmSignal(signalId)
        }
    }
    
    /**
     * Reject a pending signal
     */
    fun rejectSignal(signalId: String) {
        tradingSystemManager.rejectSignal(signalId)
    }
    
    /**
     * Close a position
     */
    fun closePosition(symbol: String) {
        viewModelScope.launch {
            tradingSystemManager.closePosition(symbol)
        }
    }
    
    /**
     * Get pending signals list
     */
    fun getPendingSignals() = tradingSystemManager.getPendingSignals()
    
    /**
     * Get active positions list
     */
    fun getPositions() = tradingSystemManager.getPositions()
    
    /**
     * V5.19.0 BUILD #102: Reset paper trading account to initial balance.
     * Useful for starting fresh after testing strategies.
     */
    fun resetPaperTrading(newBalance: Double = 100_000.0) {
        tradingSystemManager.resetPaperTrading(newBalance)
    }
    
    /**
     * Clear error state
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
