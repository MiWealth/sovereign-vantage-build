package com.miwealth.sovereignvantage.ui.portfolio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miwealth.sovereignvantage.data.repository.PortfolioRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PortfolioUiState(
    val isLoading: Boolean = false,
    val totalValue: Double = 148523.67,
    val dailyPnL: Double = 2847.32,
    val dailyPnLPercent: Double = 1.95,
    val weeklyPnL: Double = 8234.50,
    val weeklyPnLPercent: Double = 5.87,
    val monthlyPnL: Double = 24567.89,
    val monthlyPnLPercent: Double = 19.82,
    val sharpeRatio: Double = 1.70,
    val winRate: Double = 48.61,
    val maxDrawdown: Double = 11.41,
    val profitFactor: Double = 2.78,
    val holdings: List<Holding> = listOf(
        Holding("BTC/USDT", 1.245, 122548.42, 24.5),
        Holding("ETH/USDT", 8.5, 32701.20, 18.2),
        Holding("SOL/USDT", 125.0, 23431.25, 45.8),
        Holding("XRP/USDT", 5000.0, 11700.00, 12.3),
        Holding("DOGE/USDT", 25000.0, 10500.00, -5.2)
    ),
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
    }
    
    private fun loadPortfolioData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                portfolioRepository.getPortfolioSummary().collect { summary ->
                    _uiState.update { state ->
                        state.copy(
                            totalValue = summary.totalValue,
                            dailyPnL = summary.dailyChange,
                            dailyPnLPercent = summary.dailyChangePercent
                        )
                    }
                }
                
                portfolioRepository.getPerformanceMetrics().collect { metrics ->
                    _uiState.update { state ->
                        state.copy(
                            sharpeRatio = metrics.sharpeRatio,
                            winRate = metrics.winRate,
                            maxDrawdown = metrics.maxDrawdown,
                            profitFactor = metrics.profitFactor
                        )
                    }
                }
                
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
            
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    fun refreshPortfolio() {
        loadPortfolioData()
    }
}
