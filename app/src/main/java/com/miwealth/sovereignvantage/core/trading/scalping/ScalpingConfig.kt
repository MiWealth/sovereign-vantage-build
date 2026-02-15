/**
 * Scalping Configuration - High-Frequency Trading Parameters
 * 
 * Sovereign Vantage: Arthur Edition V5.5.14
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * Defines configuration for scalping strategies with tight stops,
 * quick targets, and ultra-responsive STAHL levels.
 * 
 * SCALPING PHILOSOPHY:
 * - Trade duration: 1-60 minutes
 * - Profit targets: 0.3% - 2.0%
 * - Stop losses: 0.5% - 2.0%
 * - High win rate, small gains, minimal drawdown
 * - Requires high liquidity assets only
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 */
package com.miwealth.sovereignvantage.core.trading.scalping

import com.miwealth.sovereignvantage.core.trading.StairLevel

/**
 * Scalping mode determines aggressiveness of entries and targets.
 */
enum class ScalpMode {
    /** Conservative: Wider stops, fewer trades, higher win rate */
    CONSERVATIVE,
    
    /** Standard: Balanced risk/reward */
    STANDARD,
    
    /** Aggressive: Tighter entries, more trades, requires precision */
    AGGRESSIVE,
    
    /** Turbo: Ultra-fast scalps, sub-minute, highest risk */
    TURBO
}

/**
 * Scalping timeframe for analysis.
 */
enum class ScalpTimeframe(val seconds: Int, val displayName: String) {
    TICK(0, "Tick"),
    SEC_5(5, "5s"),
    SEC_15(15, "15s"),
    SEC_30(30, "30s"),
    MIN_1(60, "1m"),
    MIN_3(180, "3m"),
    MIN_5(300, "5m"),
    MIN_15(900, "15m")
}

/**
 * Configuration for scalping engine.
 */
data class ScalpingConfig(
    /** Scalping mode */
    val mode: ScalpMode = ScalpMode.STANDARD,
    
    /** Primary analysis timeframe */
    val timeframe: ScalpTimeframe = ScalpTimeframe.MIN_1,
    
    /** Secondary confirmation timeframe (should be higher) */
    val confirmationTimeframe: ScalpTimeframe = ScalpTimeframe.MIN_5,
    
    // ========================================================================
    // ENTRY CRITERIA
    // ========================================================================
    
    /** Minimum momentum score to enter (0-100) */
    val minMomentumScore: Int = 65,
    
    /** Minimum volume ratio vs average (1.0 = average) */
    val minVolumeRatio: Double = 1.2,
    
    /** Maximum spread as percentage of price */
    val maxSpreadPercent: Double = 0.15,
    
    /** RSI oversold threshold for long entries */
    val rsiOversold: Int = 30,
    
    /** RSI overbought threshold for short entries */
    val rsiOverbought: Int = 70,
    
    /** Require trend alignment with higher timeframe */
    val requireTrendAlignment: Boolean = true,
    
    // ========================================================================
    // EXIT CRITERIA
    // ========================================================================
    
    /** Target profit percentage */
    val targetProfitPercent: Double = 0.8,
    
    /** Initial stop loss percentage */
    val initialStopPercent: Double = 1.0,
    
    /** Maximum hold time in seconds (0 = unlimited) */
    val maxHoldTimeSeconds: Int = 3600,  // 1 hour max
    
    /** Exit on momentum reversal */
    val exitOnMomentumReversal: Boolean = true,
    
    /** Momentum reversal threshold */
    val momentumReversalThreshold: Int = 40,
    
    // ========================================================================
    // RISK MANAGEMENT
    // ========================================================================
    
    /** Maximum concurrent scalp positions */
    val maxConcurrentScalps: Int = 3,
    
    /** Maximum scalps per hour */
    val maxScalpsPerHour: Int = 10,
    
    /** Maximum daily loss percentage before pause */
    val maxDailyLossPercent: Double = 2.0,
    
    /** Cooldown after loss in seconds */
    val cooldownAfterLossSeconds: Int = 120,
    
    /** Position size as percentage of scalping allocation */
    val positionSizePercent: Double = 20.0,
    
    /** Maximum position size as percentage of portfolio (V5.5.34) */
    val maxPositionSizePercent: Double = 5.0,
    
    /** Auto-execute signals without confirmation (V5.5.34) */
    val autoExecute: Boolean = false,
    
    // ========================================================================
    // STAHL LEVELS
    // ========================================================================
    
    /** Use custom scalping STAHL levels */
    val useScalpingStahlLevels: Boolean = true
) {
    /**
     * Get STAHL levels appropriate for this scalping config.
     */
    fun getStahlLevels(): List<StairLevel> = when (mode) {
        ScalpMode.TURBO -> STAHL_LEVELS_TURBO
        ScalpMode.AGGRESSIVE -> STAHL_LEVELS_AGGRESSIVE
        ScalpMode.STANDARD -> STAHL_LEVELS_STANDARD
        ScalpMode.CONSERVATIVE -> STAHL_LEVELS_CONSERVATIVE
    }
    
    /**
     * Calculate effective target based on mode.
     */
    fun getEffectiveTarget(): Double = when (mode) {
        ScalpMode.TURBO -> targetProfitPercent * 0.5
        ScalpMode.AGGRESSIVE -> targetProfitPercent * 0.75
        ScalpMode.STANDARD -> targetProfitPercent
        ScalpMode.CONSERVATIVE -> targetProfitPercent * 1.5
    }
    
    /**
     * Calculate effective stop based on mode.
     */
    fun getEffectiveStop(): Double = when (mode) {
        ScalpMode.TURBO -> initialStopPercent * 0.5
        ScalpMode.AGGRESSIVE -> initialStopPercent * 0.75
        ScalpMode.STANDARD -> initialStopPercent
        ScalpMode.CONSERVATIVE -> initialStopPercent * 1.25
    }
    
    companion object {
        /**
         * Ultra-tight STAHL levels for turbo scalping.
         * Uses percentage-of-profit methodology (v5.5.68).
         * Breakeven at 0.2%, aggressive locking for quick profits.
         */
        val STAHL_LEVELS_TURBO = listOf(
            StairLevel(profitPercent = 0.2, lockPercentOfProfit = 0.0),    // Breakeven
            StairLevel(profitPercent = 0.3, lockPercentOfProfit = 33.0),   // 0.3% profit → lock 0.1% absolute
            StairLevel(profitPercent = 0.4, lockPercentOfProfit = 50.0),   // 0.4% profit → lock 0.2% absolute
            StairLevel(profitPercent = 0.5, lockPercentOfProfit = 60.0),   // 0.5% profit → lock 0.3% absolute
            StairLevel(profitPercent = 0.6, lockPercentOfProfit = 67.0),   // 0.6% profit → lock 0.4% absolute
            StairLevel(profitPercent = 0.8, lockPercentOfProfit = 75.0)    // 0.8% profit → lock 0.6% absolute
        )
        
        /**
         * Tight STAHL levels for aggressive scalping.
         * Uses percentage-of-profit methodology (v5.5.68).
         * Breakeven at 0.3%, max lock at 75%
         */
        val STAHL_LEVELS_AGGRESSIVE = listOf(
            StairLevel(profitPercent = 0.3, lockPercentOfProfit = 0.0),    // Breakeven
            StairLevel(profitPercent = 0.5, lockPercentOfProfit = 40.0),   // 0.5% profit → lock 0.2% absolute
            StairLevel(profitPercent = 0.8, lockPercentOfProfit = 50.0),   // 0.8% profit → lock 0.4% absolute
            StairLevel(profitPercent = 1.0, lockPercentOfProfit = 60.0),   // 1.0% profit → lock 0.6% absolute
            StairLevel(profitPercent = 1.5, lockPercentOfProfit = 67.0),   // 1.5% profit → lock 1.0% absolute
            StairLevel(profitPercent = 2.0, lockPercentOfProfit = 75.0)    // 2.0% profit → lock 1.5% absolute
        )
        
        /**
         * Standard STAHL levels for scalping.
         * Uses percentage-of-profit methodology (v5.5.68).
         * Breakeven at 0.5%, balanced locking
         */
        val STAHL_LEVELS_STANDARD = listOf(
            StairLevel(profitPercent = 0.5, lockPercentOfProfit = 0.0),    // Breakeven
            StairLevel(profitPercent = 0.8, lockPercentOfProfit = 38.0),   // 0.8% profit → lock 0.3% absolute
            StairLevel(profitPercent = 1.2, lockPercentOfProfit = 50.0),   // 1.2% profit → lock 0.6% absolute
            StairLevel(profitPercent = 1.8, lockPercentOfProfit = 56.0),   // 1.8% profit → lock 1.0% absolute
            StairLevel(profitPercent = 2.5, lockPercentOfProfit = 72.0),   // 2.5% profit → lock 1.8% absolute
            StairLevel(profitPercent = 4.0, lockPercentOfProfit = 75.0)    // 4.0% profit → lock 3.0% absolute
        )
        
        /**
         * Conservative STAHL levels for cautious scalping.
         * Uses percentage-of-profit methodology (v5.5.68).
         * Breakeven at 0.8%, max lock at 75%
         */
        val STAHL_LEVELS_CONSERVATIVE = listOf(
            StairLevel(profitPercent = 0.8, lockPercentOfProfit = 0.0),    // Breakeven
            StairLevel(profitPercent = 1.2, lockPercentOfProfit = 33.0),   // 1.2% profit → lock 0.4% absolute
            StairLevel(profitPercent = 1.8, lockPercentOfProfit = 44.0),   // 1.8% profit → lock 0.8% absolute
            StairLevel(profitPercent = 2.5, lockPercentOfProfit = 60.0),   // 2.5% profit → lock 1.5% absolute
            StairLevel(profitPercent = 4.0, lockPercentOfProfit = 70.0),   // 4.0% profit → lock 2.8% absolute
            StairLevel(profitPercent = 6.0, lockPercentOfProfit = 75.0)    // 6.0% profit → lock 4.5% absolute
        )
        
        /** Default configuration for crypto scalping */
        val CRYPTO_DEFAULT = ScalpingConfig(
            mode = ScalpMode.STANDARD,
            timeframe = ScalpTimeframe.MIN_1,
            confirmationTimeframe = ScalpTimeframe.MIN_5,
            targetProfitPercent = 0.8,
            initialStopPercent = 1.0,
            maxSpreadPercent = 0.1,
            minVolumeRatio = 1.5
        )
        
        /** Configuration for forex scalping (tighter spreads available) */
        val FOREX_DEFAULT = ScalpingConfig(
            mode = ScalpMode.STANDARD,
            timeframe = ScalpTimeframe.MIN_1,
            confirmationTimeframe = ScalpTimeframe.MIN_5,
            targetProfitPercent = 0.3,
            initialStopPercent = 0.4,
            maxSpreadPercent = 0.02,  // Forex has tighter spreads
            minVolumeRatio = 1.2,
            requireTrendAlignment = true
        )
        
        /** Aggressive config for high volatility periods */
        val HIGH_VOLATILITY = ScalpingConfig(
            mode = ScalpMode.AGGRESSIVE,
            timeframe = ScalpTimeframe.SEC_30,
            confirmationTimeframe = ScalpTimeframe.MIN_3,
            targetProfitPercent = 1.2,
            initialStopPercent = 1.5,
            maxSpreadPercent = 0.2,
            minMomentumScore = 75,
            maxConcurrentScalps = 2
        )
        
        /** Ultra-conservative for learning/testing */
        val PAPER_TRADING = ScalpingConfig(
            mode = ScalpMode.CONSERVATIVE,
            timeframe = ScalpTimeframe.MIN_5,
            confirmationTimeframe = ScalpTimeframe.MIN_15,
            targetProfitPercent = 1.5,
            initialStopPercent = 1.5,
            maxSpreadPercent = 0.15,
            minMomentumScore = 70,
            maxConcurrentScalps = 1,
            maxScalpsPerHour = 5
        )
    }
}

/**
 * Runtime scalping statistics for monitoring.
 */
data class ScalpingStats(
    val totalScalps: Int = 0,
    val winningScalps: Int = 0,
    val losingScalps: Int = 0,
    val totalProfitPercent: Double = 0.0,
    val averageHoldTimeSeconds: Int = 0,
    val averageProfitPercent: Double = 0.0,
    val averageLossPercent: Double = 0.0,
    val maxConsecutiveWins: Int = 0,
    val maxConsecutiveLosses: Int = 0,
    val currentStreak: Int = 0,  // Positive = wins, negative = losses
    val scalpsThisHour: Int = 0,
    val scalpsToday: Int = 0,
    val dailyPnlPercent: Double = 0.0
) {
    val winRate: Double
        get() = if (totalScalps > 0) winningScalps.toDouble() / totalScalps * 100 else 0.0
    
    val profitFactor: Double
        get() {
            val totalLoss = if (losingScalps > 0) averageLossPercent * losingScalps else 0.001
            val totalProfit = averageProfitPercent * winningScalps
            return if (totalLoss > 0) totalProfit / totalLoss else 0.0
        }
    
    val expectancy: Double
        get() = (winRate / 100 * averageProfitPercent) - ((1 - winRate / 100) * averageLossPercent)
}
