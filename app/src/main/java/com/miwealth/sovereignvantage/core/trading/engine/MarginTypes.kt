package com.miwealth.sovereignvantage.core.trading.engine

/**
 * MARGIN TYPES
 * 
 * Sovereign Vantage: Arthur Edition V5.19.0
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * Margin status and risk state types used across the trading system.
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */

/**
 * Risk state for margin account.
 */
enum class MarginRiskState {
    /** Healthy - plenty of free margin */
    HEALTHY,
    
    /** Warning - margin utilization above 50% */
    WARNING,
    
    /** Margin call level - close to liquidation */
    MARGIN_CALL,
    
    /** Critical - imminent liquidation */
    CRITICAL,
    
    /** Liquidating positions */
    LIQUIDATING
}

/**
 * Comprehensive margin status snapshot.
 */
data class MarginStatus(
    /** Total equity (balance + unrealized PnL) */
    val equity: Double = 0.0,
    
    /** Margin used by open positions */
    val usedMargin: Double = 0.0,
    
    /** Available margin for new trades */
    val freeMargin: Double = 0.0,
    
    /** Free margin as percentage of equity (0-100) */
    val freeMarginPercent: Double = 100.0,
    
    /** Used margin as percentage of equity (0-100) */
    val marginUtilisation: Double = 0.0,
    
    /** Current risk state */
    val riskState: MarginRiskState = MarginRiskState.HEALTHY,
    
    /** Margin level percentage (equity / usedMargin * 100) */
    val marginLevel: Double = Double.MAX_VALUE,
    
    /** Distance to liquidation in quote currency */
    val distanceToLiquidation: Double = Double.MAX_VALUE,
    
    /** Number of open positions */
    val openPositionCount: Int = 0,
    
    /** Last update timestamp */
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * Create margin status from portfolio state.
         */
        fun fromPortfolioState(
            equity: Double,
            usedMargin: Double,
            freeMargin: Double,
            openPositionCount: Int = 0
        ): MarginStatus {
            val marginUtilisation = if (equity > 0) (usedMargin / equity) * 100.0 else 0.0
            val freeMarginPercent = if (equity > 0) (freeMargin / equity) * 100.0 else 100.0
            val marginLevel = if (usedMargin > 0) (equity / usedMargin) * 100.0 else Double.MAX_VALUE
            
            val riskState = when {
                marginLevel < 110.0 -> MarginRiskState.LIQUIDATING
                marginLevel < 125.0 -> MarginRiskState.CRITICAL
                marginLevel < 150.0 -> MarginRiskState.MARGIN_CALL
                marginLevel < 200.0 -> MarginRiskState.WARNING
                else -> MarginRiskState.HEALTHY
            }
            
            return MarginStatus(
                equity = equity,
                usedMargin = usedMargin,
                freeMargin = freeMargin,
                freeMarginPercent = freeMarginPercent,
                marginUtilisation = marginUtilisation,
                riskState = riskState,
                marginLevel = marginLevel,
                openPositionCount = openPositionCount
            )
        }
        
        /**
         * Default healthy status with no positions.
         */
        fun healthy(equity: Double = 100_000.0): MarginStatus {
            return MarginStatus(
                equity = equity,
                usedMargin = 0.0,
                freeMargin = equity,
                freeMarginPercent = 100.0,
                marginUtilisation = 0.0,
                riskState = MarginRiskState.HEALTHY,
                marginLevel = Double.MAX_VALUE
            )
        }
    }
}
