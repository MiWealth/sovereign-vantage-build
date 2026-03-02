package com.miwealth.sovereignvantage.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miwealth.sovereignvantage.core.TradingSystemManager
import com.miwealth.sovereignvantage.core.exchange.BinancePublicPriceFeed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SOVEREIGN VANTAGE V5.18.0 "ARTHUR EDITION"
 * WALLET VIEW MODEL
 *
 * V5.18.0 CHANGES:
 * - Replaced hardcoded balances with live data from TradingSystemManager
 * - Paper trading balance = trading balance (unified pool)
 * - Assets valued using BinancePublicPriceFeed real prices
 * - ConnectedExchanges shows actual connection status
 *
 * © 2025-2026 MiWealth Pty Ltd
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

@HiltViewModel
class WalletViewModel @Inject constructor(
    private val tradingSystemManager: TradingSystemManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(WalletUiState(isLoading = true))
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

    init {
        observeBalances()
    }

    private fun observeBalances() {
        // Observe dashboard state for portfolio value and balances
        viewModelScope.launch {
            tradingSystemManager.dashboardState.collect { dashState ->
                val priceFeed = tradingSystemManager.getPublicPriceFeed()
                val prices = priceFeed.latestPrices.value

                // Build asset list from paper trading balances
                val balances = dashState.latestPrices  // symbol -> price
                val paperBalances = getPaperBalances()

                val assets = paperBalances
                    .filter { (asset, amount) -> amount > 0.0 && asset != "USDT" && asset != "USD" }
                    .map { (asset, amount) ->
                        val priceKey = "$asset/USDT"
                        val price = prices[priceKey]?.last
                            ?: dashState.latestPrices[priceKey]
                            ?: 0.0
                        WalletAsset(
                            symbol = asset,
                            amount = amount,
                            valueUsd = amount * price
                        )
                    }
                    .sortedByDescending { it.valueUsd }

                // Cash balance (USDT)
                val cashBalance = paperBalances["USDT"] ?: paperBalances["USD"] ?: 0.0

                // Total = cash + crypto holdings
                val totalBalance = cashBalance + assets.sumOf { it.valueUsd }

                // Connected exchange info
                val activeExchange = dashState.activeExchange
                val exchanges = buildExchangeList(dashState, cashBalance)

                _uiState.update {
                    WalletUiState(
                        totalBalance = if (totalBalance > 0) totalBalance else dashState.portfolioValue,
                        connectedExchanges = exchanges,
                        assets = assets,
                        cashBalance = cashBalance,
                        isPaperTrading = dashState.paperTradingMode,
                        isLoading = false
                    )
                }
            }
        }
    }

    /**
     * Get paper trading balances from the adapter.
     */
    private fun getPaperBalances(): Map<String, Double> {
        return try {
            val aiSystem = tradingSystemManager.getAIIntegratedSystemBalances()
            aiSystem.ifEmpty { mapOf("USDT" to 100_000.0) }
        } catch (e: Exception) {
            mapOf("USDT" to tradingSystemManager.dashboardState.value.portfolioValue)
        }
    }

    private fun buildExchangeList(
        dashState: com.miwealth.sovereignvantage.core.DashboardState,
        balance: Double
    ): List<ConnectedExchange> {
        val exchanges = mutableListOf<ConnectedExchange>()

        // Show paper trading as a "virtual" exchange
        if (dashState.paperTradingMode) {
            exchanges.add(
                ConnectedExchange(
                    id = "paper",
                    name = "Paper Trading",
                    isConnected = true,
                    isTestnet = false,
                    balance = balance
                )
            )
        }

        // If connected to a real exchange data feed, show it
        dashState.activeExchange?.let { exchangeId ->
            val name = when (exchangeId) {
                "binance" -> "Binance"
                "kraken" -> "Kraken"
                "coinbase" -> "Coinbase"
                else -> exchangeId.replaceFirstChar { it.uppercase() }
            }
            exchanges.add(
                ConnectedExchange(
                    id = exchangeId,
                    name = "$name (Data Feed)",
                    isConnected = true,
                    isTestnet = dashState.isTestnetMode,
                    balance = 0.0  // Data feed only, no balance
                )
            )
        }

        return exchanges
    }
}
