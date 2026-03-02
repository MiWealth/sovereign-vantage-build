package com.miwealth.sovereignvantage.core.trading

import com.miwealth.sovereignvantage.core.AssetType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * AI Board STAHL Integration - Intelligent Preset Selection & Dynamic Stair Expansion
 * 
 * This module provides:
 * 1. AIBoardStahlSelector - Entry-time preset recommendation based on market conditions
 * 2. AIBoardStairExpander - Mid-trade stair expansion when winners keep running
 * 
 * Philosophy: "Never cap a winner artificially."
 * - 3.5% initial stop is SACRED and never modified (optimized from 2.5%/3%)
 * - Stairs can ONLY be added in the profitable direction
 * - Expansion is evidence-based, triggered by momentum at top stair
 * 
 * Decision Hierarchy:
 * 1. Human Override → Highest priority
 * 2. User Settings → Filter/constraint on AI recommendations
 * 3. AI Board → Contextual selection and expansion
 * 4. Default → MODERATE
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage™: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to: Cathryn 💘
 */

// =============================================================================
// MARKET CONTEXT - Shared Data Model for AI Board Decisions
// =============================================================================

/**
 * Volatility state classification.
 * Based on ATR relative to historical average.
 */
enum class VolatilityState {
    LOW,        // ATR < 0.5x average - tight ranges, potential breakout setup
    MODERATE,   // ATR 0.5x-1.5x average - normal conditions
    HIGH,       // ATR 1.5x-2.5x average - active market, wider stops needed
    EXTREME;    // ATR > 2.5x average - crisis/news event, caution required
    
    companion object {
        fun fromATRRatio(atrRatio: Double): VolatilityState = when {
            atrRatio < 0.5 -> LOW
            atrRatio < 1.5 -> MODERATE
            atrRatio < 2.5 -> HIGH
            else -> EXTREME
        }
    }
}

/**
 * Trend state classification.
 * Based on price relative to moving averages and ADX.
 */
enum class TrendState {
    STRONG_DOWN,  // ADX > 25, price below MAs, falling
    DOWN,         // Price below MAs, weak downtrend
    NEUTRAL,      // Ranging, no clear direction
    UP,           // Price above MAs, weak uptrend
    STRONG_UP;    // ADX > 25, price above MAs, rising
    
    companion object {
        fun fromIndicators(
            priceVs20MA: Double,  // % above/below 20 MA
            priceVs50MA: Double,  // % above/below 50 MA
            adx: Double,          // ADX value
            maSlope: Double       // 20 MA slope (% change over N bars)
        ): TrendState {
            val isAboveMAs = priceVs20MA > 0 && priceVs50MA > 0
            val isBelowMAs = priceVs20MA < 0 && priceVs50MA < 0
            val isStrongTrend = adx > 25
            
            return when {
                isBelowMAs && isStrongTrend && maSlope < -0.1 -> STRONG_DOWN
                isBelowMAs -> DOWN
                isAboveMAs && isStrongTrend && maSlope > 0.1 -> STRONG_UP
                isAboveMAs -> UP
                else -> NEUTRAL
            }
        }
    }
}

/**
 * Momentum state classification.
 * Based on ROC, RSI trend, MACD histogram direction.
 */
enum class MomentumState {
    EXHAUSTED,    // RSI extreme (>80/<20), momentum divergence
    WEAKENING,    // Momentum slowing, MACD histogram shrinking
    STABLE,       // Consistent momentum, no acceleration
    BUILDING,     // Momentum increasing, MACD histogram growing
    SURGING;      // Strong acceleration, breakout conditions
    
    companion object {
        fun fromIndicators(
            rsi: Double,
            rsiChange: Double,        // RSI change over last 3 bars
            macdHistogram: Double,
            macdHistogramChange: Double,  // Change over last 3 bars
            roc: Double               // Rate of change %
        ): MomentumState {
            // Check for exhaustion (extreme RSI with reversal signs)
            val isExhausted = (rsi > 80 && rsiChange < 0) || (rsi < 20 && rsiChange > 0)
            if (isExhausted) return EXHAUSTED
            
            // Check MACD histogram trend
            val histogramGrowing = macdHistogramChange > 0 && macdHistogram > 0 ||
                                   macdHistogramChange < 0 && macdHistogram < 0
            val histogramShrinking = macdHistogramChange < 0 && macdHistogram > 0 ||
                                     macdHistogramChange > 0 && macdHistogram < 0
            
            return when {
                abs(roc) > 5 && histogramGrowing -> SURGING
                histogramGrowing && abs(rsiChange) > 5 -> BUILDING
                histogramShrinking -> WEAKENING
                else -> STABLE
            }
        }
    }
}

/**
 * Volume state classification.
 * Based on volume relative to average.
 */
enum class VolumeState {
    DECLINING,    // Volume < 0.5x average - drying up
    NORMAL,       // Volume 0.5x-1.5x average
    ELEVATED,     // Volume 1.5x-3x average - interest increasing
    CLIMACTIC;    // Volume > 3x average - potential exhaustion/reversal
    
    companion object {
        fun fromVolumeRatio(volumeRatio: Double): VolumeState = when {
            volumeRatio < 0.5 -> DECLINING
            volumeRatio < 1.5 -> NORMAL
            volumeRatio < 3.0 -> ELEVATED
            else -> CLIMACTIC
        }
    }
}

/**
 * Trading session classification.
 * Affects liquidity and volatility expectations.
 */
enum class TradingSession {
    ASIAN,      // Lower volatility, range-bound
    EUROPEAN,   // Increasing volatility, trend development
    US,         // Highest volatility, major moves
    OVERLAP;    // EU/US overlap - maximum liquidity
    
    companion object {
        fun fromHourUTC(hour: Int): TradingSession = when (hour) {
            in 0..7 -> ASIAN
            in 8..12 -> EUROPEAN
            in 13..16 -> OVERLAP
            in 17..21 -> US
            else -> ASIAN
        }
    }
}

/**
 * Snapshot of technical indicators for AI Board decisions.
 */
data class IndicatorSnapshot(
    val atr: Double,              // Average True Range (absolute)
    val atrPercent: Double,       // ATR as % of price
    val atrRatio: Double,         // ATR / 20-period average ATR
    val adx: Double,              // Average Directional Index
    val rsi: Double,              // Relative Strength Index
    val rsiChange: Double,        // RSI change over last 3 bars
    val macdHistogram: Double,    // MACD histogram value
    val macdHistogramChange: Double, // Histogram change over last 3 bars
    val roc: Double,              // Rate of Change %
    val priceVs20MA: Double,      // % above/below 20 MA
    val priceVs50MA: Double,      // % above/below 50 MA
    val maSlope: Double,          // 20 MA slope
    val volumeRatio: Double,      // Volume / 20-period average
    val bollingerWidth: Double,   // Bollinger Band width as % of price
    val currentPrice: Double,     // Current price
    val barsAtTopStair: Int = 0   // How many bars at current top stair
)

/**
 * Complete market context for AI Board decisions.
 * This is the primary input for both selector and expander.
 */
data class StahlMarketContext(
    val volatility: VolatilityState,
    val trend: TrendState,
    val momentum: MomentumState,
    val volume: VolumeState,
    val session: TradingSession,
    val indicators: IndicatorSnapshot,
    val assetType: AssetType = AssetType.CRYPTO,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * Build StahlMarketContext from raw indicator values.
         */
        fun build(indicators: IndicatorSnapshot, hourUTC: Int, assetType: AssetType = AssetType.CRYPTO): StahlMarketContext {
            return StahlMarketContext(
                volatility = VolatilityState.fromATRRatio(indicators.atrRatio),
                trend = TrendState.fromIndicators(
                    indicators.priceVs20MA,
                    indicators.priceVs50MA,
                    indicators.adx,
                    indicators.maSlope
                ),
                momentum = MomentumState.fromIndicators(
                    indicators.rsi,
                    indicators.rsiChange,
                    indicators.macdHistogram,
                    indicators.macdHistogramChange,
                    indicators.roc
                ),
                volume = VolumeState.fromVolumeRatio(indicators.volumeRatio),
                session = TradingSession.fromHourUTC(hourUTC),
                indicators = indicators,
                assetType = assetType
            )
        }
    }
}


// =============================================================================
// USER CONSTRAINTS - Respecting User Sovereignty
// =============================================================================

/**
 * User-defined constraints that filter AI Board recommendations.
 * These represent the user's risk preferences and boundaries.
 * 
 * AI Board suggestions must respect these constraints.
 */
data class UserTradeConstraints(
    val allowedPresets: Set<StahlPreset> = StahlPreset.values().toSet(),
    val maxInitialStopPercent: Double = 3.5,    // User's max acceptable initial stop (optimized)
    val preferredPreset: StahlPreset? = null,   // User preference (not mandatory)
    val allowStairExpansion: Boolean = true,    // Whether AI can expand stairs
    val maxStairLevels: Int = 50,               // Upper limit on stair count
    val humanOverrideActive: Boolean = false    // Human has taken control
) {
    /**
     * Check if a preset is allowed by user constraints.
     */
    fun isPresetAllowed(preset: StahlPreset): Boolean {
        return preset in allowedPresets
    }
    
    /**
     * Filter recommendations through user constraints.
     */
    fun filterPreset(recommended: StahlPreset): StahlPreset {
        // If human override is active, return user's preferred or default
        if (humanOverrideActive) {
            return preferredPreset ?: StahlPreset.MODERATE
        }
        
        // If recommended is allowed, use it
        if (isPresetAllowed(recommended)) {
            return recommended
        }
        
        // Fall back to user preference if set and allowed
        if (preferredPreset != null && isPresetAllowed(preferredPreset)) {
            return preferredPreset
        }
        
        // Fall back to first allowed preset, or MODERATE
        return allowedPresets.firstOrNull() ?: StahlPreset.MODERATE
    }
}

// =============================================================================
// AI BOARD STAHL SELECTOR - Entry-Time Preset Selection
// =============================================================================

/**
 * Recommendation from AIBoardStahlSelector.
 */
data class PresetRecommendation(
    val preset: StahlPreset,
    val confidence: Double,           // 0.0 to 1.0
    val reasoning: String,
    val marketConditionsSummary: String,
    val wasFiltered: Boolean = false, // True if user constraints changed recommendation
    val originalRecommendation: StahlPreset? = null  // Original before filtering
)

/**
 * AI Board STAHL Selector - Recommends optimal preset at trade entry.
 * 
 * Selection logic considers:
 * - Market volatility (high vol → wider stairs)
 * - Trend strength (strong trend → aggressive, let winners run)
 * - Momentum state (building momentum → more stairs for expansion)
 * - Volume confirmation (climactic volume → caution)
 * - Trading session (overlaps → more volatility expected)
 * 
 * User constraints act as a filter on recommendations.
 */
class AIBoardStahlSelector(
    private val userConstraints: UserTradeConstraints = UserTradeConstraints()
) {
    
    /**
     * Recommend optimal STAHL preset based on market conditions.
     * 
     * @param context Current market context
     * @return Preset recommendation with reasoning
     */
    fun recommendPreset(context: StahlMarketContext): PresetRecommendation {
        // If human override is active, respect it completely
        if (userConstraints.humanOverrideActive) {
            val preset = userConstraints.preferredPreset ?: StahlPreset.MODERATE
            return PresetRecommendation(
                preset = preset,
                confidence = 1.0,
                reasoning = "Human override active - using user's preferred preset",
                marketConditionsSummary = summarizeConditions(context),
                wasFiltered = false
            )
        }
        
        // Calculate scores for each preset
        val scores = calculatePresetScores(context)
        
        // Get best scoring preset
        val bestPreset = scores.maxByOrNull { it.value }?.key ?: StahlPreset.MODERATE
        val confidence = scores[bestPreset] ?: 0.5
        
        // Apply user constraints filter
        val filteredPreset = userConstraints.filterPreset(bestPreset)
        val wasFiltered = filteredPreset != bestPreset
        
        return PresetRecommendation(
            preset = filteredPreset,
            confidence = if (wasFiltered) confidence * 0.8 else confidence,
            reasoning = generateReasoning(context, bestPreset, filteredPreset),
            marketConditionsSummary = summarizeConditions(context),
            wasFiltered = wasFiltered,
            originalRecommendation = if (wasFiltered) bestPreset else null
        )
    }
    
    /**
     * Calculate suitability scores for each preset (0.0 to 1.0).
     */
    private fun calculatePresetScores(context: StahlMarketContext): Map<StahlPreset, Double> {
        val scores = mutableMapOf<StahlPreset, Double>()
        
        // CONSERVATIVE scoring
        scores[StahlPreset.CONSERVATIVE] = calculateConservativeScore(context)
        
        // MODERATE scoring
        scores[StahlPreset.MODERATE] = calculateModerateScore(context)
        
        // AGGRESSIVE scoring
        scores[StahlPreset.AGGRESSIVE] = calculateAggressiveScore(context)
        
        // SCALPING scoring
        scores[StahlPreset.SCALPING] = calculateScalpingScore(context)
        
        // Normalize scores
        val maxScore = scores.values.maxOrNull() ?: 1.0
        return scores.mapValues { it.value / maxScore }
    }
    
    /**
     * CONSERVATIVE: Best for high volatility, ranging markets, risk-averse.
     */
    private fun calculateConservativeScore(context: StahlMarketContext): Double {
        var score = 0.5  // Base score
        
        // High volatility favors conservative
        when (context.volatility) {
            VolatilityState.EXTREME -> score += 0.3
            VolatilityState.HIGH -> score += 0.2
            VolatilityState.MODERATE -> score += 0.0
            VolatilityState.LOW -> score -= 0.1
        }
        
        // Neutral/ranging markets favor conservative (swing trade the range)
        when (context.trend) {
            TrendState.NEUTRAL -> score += 0.2
            TrendState.UP, TrendState.DOWN -> score += 0.1
            TrendState.STRONG_UP, TrendState.STRONG_DOWN -> score -= 0.1
        }
        
        // Exhausted momentum favors conservative
        when (context.momentum) {
            MomentumState.EXHAUSTED -> score += 0.2
            MomentumState.WEAKENING -> score += 0.1
            MomentumState.STABLE -> score += 0.0
            MomentumState.BUILDING, MomentumState.SURGING -> score -= 0.1
        }
        
        // Climactic volume suggests caution
        if (context.volume == VolumeState.CLIMACTIC) score += 0.15
        
        return score.coerceIn(0.0, 1.0)
    }
    
    /**
     * MODERATE: Balanced approach, works in most conditions.
     */
    private fun calculateModerateScore(context: StahlMarketContext): Double {
        var score = 0.6  // Higher base - it's the default for a reason
        
        // Moderate volatility is ideal
        when (context.volatility) {
            VolatilityState.MODERATE -> score += 0.2
            VolatilityState.LOW, VolatilityState.HIGH -> score += 0.0
            VolatilityState.EXTREME -> score -= 0.15
        }
        
        // Works in mild trends
        when (context.trend) {
            TrendState.UP, TrendState.DOWN -> score += 0.15
            TrendState.NEUTRAL -> score += 0.1
            TrendState.STRONG_UP, TrendState.STRONG_DOWN -> score -= 0.05
        }
        
        // Stable momentum is ideal
        when (context.momentum) {
            MomentumState.STABLE -> score += 0.15
            MomentumState.BUILDING -> score += 0.1
            MomentumState.WEAKENING -> score += 0.05
            MomentumState.EXHAUSTED, MomentumState.SURGING -> score -= 0.1
        }
        
        return score.coerceIn(0.0, 1.0)
    }
    
    /**
     * AGGRESSIVE: Best for strong trends with building momentum.
     * This is the PROVEN configuration (48.61% backtest).
     */
    private fun calculateAggressiveScore(context: StahlMarketContext): Double {
        var score = 0.5  // Base score
        
        // Moderate to low volatility allows tight stairs
        when (context.volatility) {
            VolatilityState.LOW -> score += 0.2
            VolatilityState.MODERATE -> score += 0.15
            VolatilityState.HIGH -> score -= 0.1
            VolatilityState.EXTREME -> score -= 0.25
        }
        
        // Strong trends are ideal for aggressive
        when (context.trend) {
            TrendState.STRONG_UP, TrendState.STRONG_DOWN -> score += 0.3
            TrendState.UP, TrendState.DOWN -> score += 0.15
            TrendState.NEUTRAL -> score -= 0.1
        }
        
        // Building/surging momentum confirms trend continuation
        when (context.momentum) {
            MomentumState.SURGING -> score += 0.25
            MomentumState.BUILDING -> score += 0.2
            MomentumState.STABLE -> score += 0.05
            MomentumState.WEAKENING -> score -= 0.15
            MomentumState.EXHAUSTED -> score -= 0.25
        }
        
        // Elevated volume confirms move
        when (context.volume) {
            VolumeState.ELEVATED -> score += 0.15
            VolumeState.NORMAL -> score += 0.0
            VolumeState.DECLINING -> score -= 0.1
            VolumeState.CLIMACTIC -> score -= 0.1  // Potential blow-off top
        }
        
        // High ADX confirms trend strength
        if (context.indicators.adx > 30) score += 0.1
        
        return score.coerceIn(0.0, 1.0)
    }
    
    /**
     * SCALPING: Best for very low volatility or when expecting breakout.
     */
    private fun calculateScalpingScore(context: StahlMarketContext): Double {
        var score = 0.4  // Lower base - specialized use case
        
        // Very low volatility (tight range before breakout)
        when (context.volatility) {
            VolatilityState.LOW -> score += 0.3
            VolatilityState.MODERATE -> score += 0.0
            VolatilityState.HIGH, VolatilityState.EXTREME -> score -= 0.2
        }
        
        // Works in any trend direction with momentum
        if (context.momentum == MomentumState.SURGING) score += 0.2
        if (context.momentum == MomentumState.BUILDING) score += 0.1
        
        // Tight Bollinger Bands suggest compression before breakout
        if (context.indicators.bollingerWidth < 2.0) score += 0.2
        
        // Overlapping sessions provide liquidity for quick trades
        if (context.session == TradingSession.OVERLAP) score += 0.15
        if (context.session == TradingSession.US) score += 0.1
        
        return score.coerceIn(0.0, 1.0)
    }
    
    /**
     * Generate human-readable reasoning for the recommendation.
     */
    private fun generateReasoning(
        context: StahlMarketContext,
        original: StahlPreset,
        filtered: StahlPreset
    ): String {
        val sb = StringBuilder()
        
        when (filtered) {
            StahlPreset.CONSERVATIVE -> {
                sb.append("CONSERVATIVE selected: ")
                if (context.volatility in listOf(VolatilityState.HIGH, VolatilityState.EXTREME)) {
                    sb.append("High volatility requires wider stair spacing. ")
                }
                if (context.momentum == MomentumState.EXHAUSTED) {
                    sb.append("Momentum exhaustion suggests caution. ")
                }
                if (context.trend == TrendState.NEUTRAL) {
                    sb.append("Ranging market suits swing approach. ")
                }
            }
            StahlPreset.MODERATE -> {
                sb.append("MODERATE selected: Balanced conditions suit standard approach. ")
                if (context.volatility == VolatilityState.MODERATE) {
                    sb.append("Normal volatility allows standard stair spacing. ")
                }
            }
            StahlPreset.AGGRESSIVE -> {
                sb.append("AGGRESSIVE selected: ")
                if (context.trend in listOf(TrendState.STRONG_UP, TrendState.STRONG_DOWN)) {
                    sb.append("Strong trend favors tight stairs to maximize runners. ")
                }
                if (context.momentum in listOf(MomentumState.BUILDING, MomentumState.SURGING)) {
                    sb.append("Building momentum supports trend continuation. ")
                }
                sb.append("PROVEN: 48.61% backtest performance. ")
            }
            StahlPreset.SCALPING -> {
                sb.append("SCALPING selected: ")
                if (context.volatility == VolatilityState.LOW) {
                    sb.append("Low volatility compression suggests breakout imminent. ")
                }
                sb.append("No TP ceiling - AI Board will manage exit. ")
            }
            StahlPreset.CUSTOM -> {
                sb.append("CUSTOM preset in use. ")
            }
        }
        
        if (original != filtered) {
            sb.append("(Original recommendation: $original, filtered by user constraints)")
        }
        
        return sb.toString().trim()
    }
    
    /**
     * Summarize market conditions for logging/display.
     */
    private fun summarizeConditions(context: StahlMarketContext): String {
        return "Vol:${context.volatility} Trend:${context.trend} Mom:${context.momentum} " +
               "Vol:${context.volume} Session:${context.session} " +
               "ATR:${String.format("%.2f", context.indicators.atrPercent)}% " +
               "ADX:${String.format("%.1f", context.indicators.adx)} " +
               "RSI:${String.format("%.1f", context.indicators.rsi)}"
    }
}

// =============================================================================
// AI BOARD STAIR EXPANDER - Mid-Trade Dynamic Stair Expansion
// =============================================================================

/**
 * Expansion decision types.
 */
enum class ExpansionDecision {
    HOLD,               // No expansion needed
    BORROW_AGGRESSIVE,  // Borrow stairs from AGGRESSIVE preset
    EXTRAPOLATE,        // Calculate new stairs dynamically
    EXPAND_BORROW,      // V5.17.0: Expand using borrowed levels
    EXPAND_ATR          // V5.17.0: Expand using ATR-based calculation
}

/**
 * Result of expansion evaluation.
 */
data class ExpansionResult(
    val decision: ExpansionDecision,
    val newStairs: List<StairLevel>,
    val confidence: Double,
    val reasoning: String
)

/**
 * AI Board Stair Expander - Dynamically expands stairs mid-trade.
 * 
 * Philosophy:
 * - Only expand when position reaches top stair with strong momentum
 * - 3.5% initial stop is SACRED - never touched
 * - Expansion is upward for longs, downward for shorts
 * - Either borrow from AGGRESSIVE preset or extrapolate based on volatility
 * 
 * Extrapolation algorithm:
 * - Stair spacing based on current ATR as % of price
 * - Lock-in ratio maintains ~80% of threshold (proven ratio)
 * - Adapts to current market volatility
 */
class AIBoardStairExpander(
    private val userConstraints: UserTradeConstraints = UserTradeConstraints()
) {
    
    companion object {
        // Lock-in ratio: how much of the profit threshold to lock in
        // 80% is the sweet spot - enough cushion for volatility, enough profit secured
        private const val LOCK_IN_RATIO = 0.80
        
        // Minimum bars at top stair before considering expansion
        private const val MIN_BARS_AT_TOP = 3
        
        // Reference: Extended upper stairs for dynamic expansion
        // Uses percentage-of-profit methodology (v5.5.68)
        // These are for positions that exceed the standard presets
        private val AGGRESSIVE_UPPER_STAIRS = listOf(
            // At very high profits, lock progressively higher percentage of profit
            StairLevel(profitPercent = 120.0, lockPercentOfProfit = 83.0),  // 120% profit → lock 99.6% absolute
            StairLevel(profitPercent = 160.0, lockPercentOfProfit = 85.0),  // 160% profit → lock 136% absolute
            StairLevel(profitPercent = 220.0, lockPercentOfProfit = 86.0),  // 220% profit → lock 189.2% absolute
            StairLevel(profitPercent = 300.0, lockPercentOfProfit = 87.0),  // 300% profit → lock 261% absolute
            StairLevel(profitPercent = 400.0, lockPercentOfProfit = 88.0),  // 400% profit → lock 352% absolute
            StairLevel(profitPercent = 520.0, lockPercentOfProfit = 89.0),  // 520% profit → lock 462.8% absolute
            StairLevel(profitPercent = 680.0, lockPercentOfProfit = 90.0),  // 680% profit → lock 612% absolute
            StairLevel(profitPercent = 880.0, lockPercentOfProfit = 90.0),  // 880% profit → lock 792% absolute
            StairLevel(profitPercent = 1150.0, lockPercentOfProfit = 91.0), // 1150% profit → lock 1046.5% absolute
            StairLevel(profitPercent = 1500.0, lockPercentOfProfit = 92.0)  // 1500% profit → lock 1380% absolute
        )
    }
    
    /**
     * Evaluate whether to expand stairs for a position.
     * 
     * @param position Current STAHL position
     * @param context Current market context
     * @return Expansion result with new stairs if expansion recommended
     */
    fun evaluateExpansion(
        position: StahlPosition,
        context: StahlMarketContext
    ): ExpansionResult {
        // Check if user allows expansion
        if (!userConstraints.allowStairExpansion) {
            return ExpansionResult(
                decision = ExpansionDecision.HOLD,
                newStairs = emptyList(),
                confidence = 1.0,
                reasoning = "Stair expansion disabled by user settings"
            )
        }
        
        // Check if at top stair
        val currentLevel = position.getCurrentLevel()
        val config = StahlStairStopManager.getConfig(position.preset)
        val isAtTopStair = currentLevel >= config.levelCount
        
        if (!isAtTopStair) {
            return ExpansionResult(
                decision = ExpansionDecision.HOLD,
                newStairs = emptyList(),
                confidence = 1.0,
                reasoning = "Not at top stair (Level $currentLevel of ${config.levelCount})"
            )
        }
        
        // Check minimum bars at top stair
        if (context.indicators.barsAtTopStair < MIN_BARS_AT_TOP) {
            return ExpansionResult(
                decision = ExpansionDecision.HOLD,
                newStairs = emptyList(),
                confidence = 0.8,
                reasoning = "Consolidating at top stair (${context.indicators.barsAtTopStair}/$MIN_BARS_AT_TOP bars)"
            )
        }
        
        // Evaluate momentum strength
        val shouldExpand = evaluateMomentumForExpansion(context)
        
        if (!shouldExpand) {
            return ExpansionResult(
                decision = ExpansionDecision.HOLD,
                newStairs = emptyList(),
                confidence = 0.7,
                reasoning = "Momentum not strong enough for expansion: ${context.momentum}"
            )
        }
        
        // Decide expansion method: borrow or extrapolate
        val (decision, newStairs, confidence) = decideExpansionMethod(position, context)
        
        // Apply max stair limit
        val currentStairCount = config.levelCount
        val maxAdditional = userConstraints.maxStairLevels - currentStairCount
        val limitedStairs = newStairs.take(maxAdditional)
        
        return ExpansionResult(
            decision = decision,
            newStairs = limitedStairs,
            confidence = confidence,
            reasoning = generateExpansionReasoning(decision, context, limitedStairs.size)
        )
    }
    
    /**
     * Check if momentum justifies expansion.
     */
    private fun evaluateMomentumForExpansion(context: StahlMarketContext): Boolean {
        // Strong momentum states justify expansion
        if (context.momentum in listOf(MomentumState.SURGING, MomentumState.BUILDING)) {
            return true
        }
        
        // Stable momentum with strong trend also justifies
        if (context.momentum == MomentumState.STABLE &&
            context.trend in listOf(TrendState.STRONG_UP, TrendState.STRONG_DOWN)) {
            return true
        }
        
        // Volume confirmation can tip the balance
        if (context.momentum == MomentumState.STABLE &&
            context.volume == VolumeState.ELEVATED) {
            return true
        }
        
        return false
    }
    
    /**
     * Decide whether to borrow from AGGRESSIVE or extrapolate.
     */
    private fun decideExpansionMethod(
        position: StahlPosition,
        context: StahlMarketContext
    ): Triple<ExpansionDecision, List<StairLevel>, Double> {
        val currentConfig = StahlStairStopManager.getConfig(position.preset)
        val currentTopStair = currentConfig.stairLevels.lastOrNull()
            ?: return Triple(ExpansionDecision.HOLD, emptyList(), 0.0)
        
        val currentTopProfit = currentTopStair.profitPercent
        
        // Find AGGRESSIVE stairs above current position
        val availableAggressiveStairs = AGGRESSIVE_UPPER_STAIRS.filter { 
            it.profitPercent > currentTopProfit 
        }
        
        // Calculate volatility-based stair spacing for extrapolation
        val atrSpacing = calculateATRBasedSpacing(context)
        
        // Decision logic:
        // - If AGGRESSIVE stairs fit well with current volatility, borrow them
        // - If volatility suggests different spacing, extrapolate
        
        val aggressiveSpacing = if (availableAggressiveStairs.size >= 2) {
            availableAggressiveStairs[1].profitPercent - availableAggressiveStairs[0].profitPercent
        } else {
            40.0  // Default AGGRESSIVE spacing
        }
        
        // If AGGRESSIVE spacing is within 50% of ATR-suggested spacing, borrow
        val spacingRatio = aggressiveSpacing / atrSpacing
        
        return if (spacingRatio in 0.5..2.0 && availableAggressiveStairs.isNotEmpty()) {
            // Borrow from AGGRESSIVE - spacing is appropriate
            Triple(
                ExpansionDecision.BORROW_AGGRESSIVE,
                availableAggressiveStairs.take(5),  // Take up to 5 stairs
                0.85
            )
        } else {
            // Extrapolate - need custom spacing for current conditions
            val extrapolatedStairs = extrapolateStairs(currentTopStair, context, 5)
            Triple(
                ExpansionDecision.EXTRAPOLATE,
                extrapolatedStairs,
                0.75
            )
        }
    }
    
    /**
     * Calculate ATR-based stair spacing.
     * Stairs should be spaced at roughly 2-3x ATR to avoid noise.
     */
    private fun calculateATRBasedSpacing(context: StahlMarketContext): Double {
        val atrPercent = context.indicators.atrPercent
        
        // Base spacing at 2.5x ATR, adjusted for volatility state
        val multiplier = when (context.volatility) {
            VolatilityState.LOW -> 3.0       // Wider spacing in quiet markets
            VolatilityState.MODERATE -> 2.5
            VolatilityState.HIGH -> 2.0
            VolatilityState.EXTREME -> 1.5   // Tighter spacing in volatile markets
        }
        
        return (atrPercent * multiplier).coerceIn(5.0, 50.0)  // Min 5%, max 50%
    }
    
    /**
     * Extrapolate new stairs based on current market conditions.
     * 
     * @param currentTop The current top stair level
     * @param context Market context for volatility-adaptive spacing
     * @param count Number of stairs to generate
     * @return List of new stair levels
     */
    fun extrapolateStairs(
        currentTop: StairLevel,
        context: StahlMarketContext,
        count: Int
    ): List<StairLevel> {
        val spacing = calculateATRBasedSpacing(context)
        val stairs = mutableListOf<StairLevel>()
        
        var currentProfit = currentTop.profitPercent
        
        repeat(count) { i ->
            // Increase spacing slightly for each stair (compounding effect)
            val thisSpacing = spacing * (1.0 + i * 0.1)
            currentProfit += thisSpacing
            
            // Lock-in percentage of profit (percentage-of-profit methodology)
            // Use 80% as lock ratio - at currentProfit, lock 80% of it
            val lockPercentOfProfit = LOCK_IN_RATIO * 100.0  // Convert to percentage
            
            stairs.add(StairLevel(
                profitPercent = currentProfit.roundTo2Decimals(),
                lockPercentOfProfit = lockPercentOfProfit.roundTo2Decimals()
            ))
        }
        
        return stairs
    }
    
    /**
     * Generate reasoning for expansion decision.
     */
    private fun generateExpansionReasoning(
        decision: ExpansionDecision,
        context: StahlMarketContext,
        stairCount: Int
    ): String {
        return when (decision) {
            ExpansionDecision.HOLD -> "No expansion - conditions not met"
            ExpansionDecision.BORROW_AGGRESSIVE -> 
                "Borrowing $stairCount stairs from AGGRESSIVE preset. " +
                "Momentum: ${context.momentum}, Trend: ${context.trend}. " +
                "AGGRESSIVE spacing appropriate for current volatility."
            ExpansionDecision.EXTRAPOLATE ->
                "Extrapolating $stairCount new stairs. " +
                "ATR-based spacing: ${String.format("%.1f", calculateATRBasedSpacing(context))}%. " +
                "Momentum: ${context.momentum}, Volatility: ${context.volatility}."
            else -> "No expansion action taken"
        }
    }
    
    /**
     * Extension function for rounding.
     */
    private fun Double.roundTo2Decimals(): Double {
        return (this * 100).roundToInt() / 100.0
    }
}

// =============================================================================
// STAHL POSITION EXTENSIONS - Integrate Expansion Capability
// =============================================================================

/**
 * Expanded position state tracking.
 * Used when AI Board has added stairs to a position.
 */
data class ExpandedStahlPosition(
    val basePosition: StahlPosition,
    val originalConfig: StahlConfig,
    val expandedStairs: MutableList<StairLevel> = mutableListOf(),
    val expansionHistory: MutableList<ExpansionEvent> = mutableListOf()
) {
    /**
     * All stairs including expansions.
     */
    val allStairs: List<StairLevel>
        get() = originalConfig.stairLevels + expandedStairs
    
    /**
     * Total stair count including expansions.
     */
    val totalStairCount: Int
        get() = allStairs.size
    
    /**
     * Apply expansion to this position.
     */
    fun applyExpansion(result: ExpansionResult) {
        if (result.decision != ExpansionDecision.HOLD && result.newStairs.isNotEmpty()) {
            expandedStairs.addAll(result.newStairs)
            expansionHistory.add(ExpansionEvent(
                timestamp = System.currentTimeMillis(),
                decision = result.decision,
                stairsAdded = result.newStairs.size,
                reasoning = result.reasoning
            ))
        }
    }
    
    /**
     * Calculate stop based on all stairs (original + expanded).
     * Uses percentage-of-profit methodology (v5.5.68).
     */
    fun calculateExpandedStop(maxProfitPercent: Double): StahlStopResult {
        var lockedProfitPercent = -originalConfig.initialStopPercent
        var currentLevel = 0
        var nextLevelProfit: Double? = allStairs.firstOrNull()?.profitPercent
        
        for (i in allStairs.indices.reversed()) {
            val level = allStairs[i]
            if (maxProfitPercent >= level.profitPercent) {
                // Calculate locked profit using percentage-of-profit methodology
                lockedProfitPercent = level.calculateLockedProfit()
                currentLevel = i + 1
                nextLevelProfit = if (i + 1 < allStairs.size) {
                    allStairs[i + 1].profitPercent
                } else {
                    null
                }
                break
            }
        }
        
        val stopPrice = when (basePosition.direction) {
            TradeDirection.LONG -> basePosition.entryPrice * (1 + lockedProfitPercent / 100)
            TradeDirection.SHORT -> basePosition.entryPrice * (1 - lockedProfitPercent / 100)
        }
        
        return StahlStopResult(
            stopPrice = stopPrice,
            currentLevel = currentLevel,
            lockedInPercent = lockedProfitPercent,
            isBreakeven = lockedProfitPercent == 0.0,
            isInProfit = lockedProfitPercent > 0.0,
            preset = originalConfig.preset,
            nextLevelProfitPercent = nextLevelProfit
        )
    }
}

/**
 * Record of an expansion event.
 */
data class ExpansionEvent(
    val timestamp: Long,
    val decision: ExpansionDecision,
    val stairsAdded: Int,
    val reasoning: String
)

// =============================================================================
// CONVENIENCE FACTORY
// =============================================================================

/**
 * Factory for creating AI Board components with shared configuration.
 */
object AIBoardStahlFactory {
    
    /**
     * Create selector and expander with consistent constraints.
     */
    fun create(constraints: UserTradeConstraints = UserTradeConstraints()): Pair<AIBoardStahlSelector, AIBoardStairExpander> {
        return Pair(
            AIBoardStahlSelector(constraints),
            AIBoardStairExpander(constraints)
        )
    }
    
    /**
     * Create expanded position from base position.
     */
    fun createExpandedPosition(position: StahlPosition): ExpandedStahlPosition {
        return ExpandedStahlPosition(
            basePosition = position,
            originalConfig = StahlStairStopManager.getConfig(position.preset)
        )
    }
}
