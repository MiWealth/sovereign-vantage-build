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
        // BUILD #265: Observe dashboardState as a Flow so wallet updates live
        // every time prices change (every 5 seconds from Binance feed)
        viewModelScope.launch {
            tradingSystemManager.dashboardState.collect { dashState ->
                val priceFeed = tradingSystemManager.getPublicPriceFeed()
                val prices = priceFeed.latestPrices.value

                // Get margin status (for futures trading)
                val marginStatus = tradingSystemManager.getMarginStatus()

                // BUILD #265: Get live balances — seeded BTC/ETH/SOL/XRP + USDT
                val paperBalances = tradingSystemManager.getAIIntegratedSystemBalances()

                // Build asset tiles for each crypto holding (exclude stablecoins)
                val assets = paperBalances
                    .filter { (asset, amount) ->
                        amount > 0.0 && asset != "USDT" && asset != "USD"
                    }
                    .map { (asset, amount) ->
                        val priceKey = "$asset/USDT"
                        // Try live price feed first, then dashboard state prices
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

                // BUILD #265: Total = cash + mark-to-market crypto value
                // If crypto prices not yet loaded, fall back to portfolioValue
                val cryptoValue = assets.sumOf { it.valueUsd }
                val totalBalance = when {
                    cryptoValue > 0.0 -> cashBalance + cryptoValue
                    dashState.portfolioValue > 0.0 -> dashState.portfolioValue
                    else -> cashBalance
                }

                val exchanges = buildExchangeList(dashState, cashBalance)

                _uiState.update {
                    WalletUiState(
                        totalBalance = totalBalance,
                        connectedExchanges = exchanges,
                        assets = assets,
                        cashBalance = cashBalance,
                        isPaperTrading = dashState.paperTradingMode,
                        isLoading = false,
                        // BUILD #165: Margin data for futures trading
                        equity = marginStatus?.equity ?: dashState.portfolioValue,
                        usedMargin = marginStatus?.usedMargin ?: 0.0,
                        freeMargin = marginStatus?.freeMargin ?: dashState.portfolioValue,
                        freeMarginPercent = marginStatus?.freeMarginPercent ?: 100.0,
                        marginUtilisation = marginStatus?.marginUtilisation ?: 0.0,
                        marginRiskState = marginStatus?.riskState?.name ?: "HEALTHY"
                    )
                }
            }
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
