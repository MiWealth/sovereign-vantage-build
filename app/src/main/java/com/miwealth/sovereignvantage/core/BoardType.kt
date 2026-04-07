package com.miwealth.sovereignvantage.core

/**
 * Board Type - Identifies which AI board created/manages a position
 * 
 * BUILD #413: Dual Capital Architecture
 * 
 * Separate capital pools for independent operation:
 * - MAIN BOARD: Aggressive growth engine (A$50K initial)
 * - HEDGE_FUND: Conservative risk manager (A$50K initial)
 * 
 * © 2025 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO: Arthur Iain McManus (1966-2025)
 */
enum class BoardType {
    /**
     * Main Board (8 members)
     * - AGGRESSIVE: Lower confidence threshold, more trades
     * - Trend following, momentum, breakouts
     * - Higher leverage potential (up to 5x)
     * - Goal: Maximize returns
     */
    MAIN,
    
    /**
     * Hedge Fund Board (7 members)
     * - CONSERVATIVE: Higher confidence threshold (65%), fewer trades
     * - Counter-trend, mean reversion, hedging
     * - Lower leverage (up to 2x)
     * - Goal: Minimize risk, protect capital
     */
    HEDGE_FUND;
    
    companion object {
        fun fromString(value: String?): BoardType {
            return when (value?.uppercase()) {
                "MAIN", "MAIN_BOARD" -> MAIN
                "HEDGE", "HEDGE_FUND" -> HEDGE_FUND
                else -> MAIN // Default to main board
            }
        }
    }
}
