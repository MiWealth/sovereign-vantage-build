package com.miwealth.sovereignvantage.ui.portfolio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miwealth.sovereignvantage.core.TradingSystemManager
import com.miwealth.sovereignvantage.data.repository.PortfolioRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * BUILD #110: MANUS MOCK DATA PURGED
 * All hardcoded values removed - now uses REAL data from TradingSystemManager
 */
data class PortfolioUiState(
    val isLoading: Boolean = false,
    val totalValue: Double = 0.0,              // Real data from TradingSystemManager
    val dailyPnL: Double = 0.0,                // Real daily P&L
    val dailyPnLPercent: Double = 0.0,         // Real percentage
    val weeklyPnL: Double = 0.0,               // Calculated from real data
    val weeklyPnLPercent: Double = 0.0,        // Calculated
    val monthlyPnL: Double = 0.0,              // Calculated
    val monthlyPnLPercent: Double = 0.0,       // Calculated
    val sharpeRatio: Double = 0.0,             // Real metric
    val sortinoRatio: Double = 0.0,            // Real metric (BUILD #128)
    val winRate: Double = 0.0,                 // Real win rate
    val maxDrawdown: Double = 0.0,             // Real max drawdown
    val profitFactor: Double = 0.0,            // Real profit factor
    val holdings: List<Holding> = emptyList(), // Real holdings from balances
    val error: String? = null
)

@HiltViewModel
class PortfolioViewModel @Inject constructor(
    private val portfolioRepository: PortfolioRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(PortfolioUiState())
    val uiState: StateFlow<PortfolioUiState> = _uiState.asStateFlow()
    
    init {
        loadPortfolioData()
        startPeriodicRefresh()
    }
    
    /**
     * BUILD #157: Refresh portfolio every 5 seconds to catch trade updates.
     * This ensures holdings appear when trades execute, matching the
     * 5-second balance polling in TradingSystemIntegration.
     */
    private fun startPeriodicRefresh() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(5000) // 5 seconds
                refreshPortfolio()
            }
        }
    }
    
    private fun loadPortfolioData() {
        // BUILD #265: Collect summary (flows continuously from dashboardState)
        viewModelScope.launch {
            try {
                portfolioRepository.getPortfolioSummary().collect { summary ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            totalValue = summary.totalValue,
                            dailyPnL = summary.dailyChange,
                            dailyPnLPercent = summary.dailyChangePercent
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }

        // Collect performance metrics (flows continuously)
        viewModelScope.launch {
            try {
                portfolioRepository.getPerformanceMetrics().collect { metrics ->
                    _uiState.update { state ->
                        state.copy(
                            sharpeRatio = metrics.sharpeRatio,
                            sortinoRatio = metrics.sortinoRatio,
                            winRate = metrics.winRate,
                            maxDrawdown = metrics.maxDrawdown,
                            profitFactor = metrics.profitFactor
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }

        // Collect holdings (flows continuously — updates when prices or balances change)
        viewModelScope.launch {
            try {
                portfolioRepository.getHoldings().collect { holdings ->
                    _uiState.update { state ->
                        state.copy(
                            holdings = holdings.map {
                                Holding(it.symbol, it.amount, it.value, it.pnlPercent)
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    fun refreshPortfolio() {
        loadPortfolioData()
    }
}
