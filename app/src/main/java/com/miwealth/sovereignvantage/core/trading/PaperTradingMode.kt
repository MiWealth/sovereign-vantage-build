package com.miwealth.sovereignvantage.core.trading

/**
 * PAPER TRADING EXECUTION MODE - V5.18.1
 * 
 * Three modes for paper trading execution to test strategies:
 * - MOCK: Simulated random walk prices, instant fills
 * - LIVE: Real-time Binance prices, paper execution against live market
 * - BACKTESTING: Historical data replay for strategy validation
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 */

enum class PaperTradingMode {
    /**
     * MOCK MODE:
     * - Random walk price simulation
     * - No real exchange connection
     * - Instant order fills at simulated prices
     * - Useful for UI testing and strategy logic validation
     */
    MOCK,
    
    /**
     * LIVE MODE (Default):
     * - Real-time prices from Binance public API
     * - Paper trading execution against live market prices
     * - Realistic fills considering bid/ask spread
     * - Current production mode for v5.18.0+
     */
    LIVE,
    
    /**
     * BACKTESTING MODE:
     * - Historical OHLCV data replay
     * - Fast-forward time simulation
     * - Validates strategy performance on past data
     * - Requires historical dataset loaded
     */
    BACKTESTING;
    
    companion object {
        val DEFAULT = LIVE
    }
}

/**
 * Configuration for each trading mode
 */
data class PaperTradingModeConfig(
    val mode: PaperTradingMode,
    val useLivePrices: Boolean,
    val useHistoricalData: Boolean,
    val simulatedLatencyMs: Long = 0,
    val backtestDataSource: String? = null,
    val backtestStartDate: String? = null,
    val backtestEndDate: String? = null
) {
    companion object {
        fun forMode(mode: PaperTradingMode): PaperTradingModeConfig {
            return when (mode) {
                PaperTradingMode.MOCK -> PaperTradingModeConfig(
                    mode = PaperTradingMode.MOCK,
                    useLivePrices = false,
                    useHistoricalData = false,
                    simulatedLatencyMs = 50  // 50ms simulated latency
                )
                PaperTradingMode.LIVE -> PaperTradingModeConfig(
                    mode = PaperTradingMode.LIVE,
                    useLivePrices = true,
                    useHistoricalData = false,
                    simulatedLatencyMs = 100  // 100ms realistic latency
                )
                PaperTradingMode.BACKTESTING -> PaperTradingModeConfig(
                    mode = PaperTradingMode.BACKTESTING,
                    useLivePrices = false,
                    useHistoricalData = true,
                    simulatedLatencyMs = 0,  // No latency in backtest
                    backtestDataSource = "binance",
                    backtestStartDate = "2025-01-01",
                    backtestEndDate = "2025-12-31"
                )
            }
        }
    }
}
