package com.miwealth.sovereignvantage.core.trading

import com.miwealth.sovereignvantage.core.trading.engine.Position
import kotlin.math.min
import kotlin.math.max

/**
 * Multi-Position Decision Engine
 * 
 * AI Board logic for deciding when to open additional positions on the same symbol.
 * 
 * Factors considered:
 * - Signal strength (higher confidence = more positions allowed)
 * - Current position count for symbol
 * - Portfolio concentration risk
 * - Available margin
 * - Market regime (trending = more positions, choppy = fewer)
 * - Correlation with existing positions
 * 
 * BUILD #364: Enable unlimited positions per symbol with intelligent limits
 * 
 * © 2025 MiWealth Pty Ltd - Sovereign Vantage™: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO: Arthur Iain McManus (1966-2025)
 * Dedicated to: Cathryn 💘
 */

/**
 * Multi-position configuration
 */
data class MultiPositionConfig(
    // Client override: Maximum positions per symbol (null = unlimited, AI decides)
    val maxPositionsPerSymbol: Int? = null,
    
    // Maximum total concurrent positions across all symbols
    val maxTotalPositions: Int = 10,
    
    // Maximum portfolio concentration per symbol (%)
    val maxConcentrationPercent: Double = 30.0,
    
    // Minimum margin required to open additional position (%)
    val minMarginPercent: Double = 20.0,
    
    // BUILD #432: Confidence threshold for additional positions
    // First position uses minConfidenceToTrade (1% in DEVELOPMENT)
    // Additional positions require higher confidence
    val minConfidenceForMultiple: Double = 30.0,  // Was 75.0 - adjusted per Mike's request
    
    // Enable correlated position limits (don't open BTC + ETH simultaneously if highly correlated)
    val limitCorrelatedPositions: Boolean = true,
    
    // Maximum correlation coefficient for concurrent positions
    val maxCorrelation: Double = 0.85
)

/**
 * Position opening decision
 */
data class PositionDecision(
    val canOpen: Boolean,
    val reason: String,
    val recommendedSize: Double = 0.0,
    val confidence: Double = 0.0,
    val riskScore: Double = 0.0
)

/**
 * Multi-Position Decision Engine
 */
class MultiPositionDecisionEngine(
    private val config: MultiPositionConfig = MultiPositionConfig()
) {
    
    /**
     * Decide if we should open an additional position on this symbol
     * 
     * @param symbol Trading symbol (e.g. "BTC/USDT")
     * @param signalConfidence AI Board signal confidence (0-100)
     * @param currentPositionCount Current open positions for this symbol
     * @param totalPositions Total open positions across all symbols
     * @param availableMargin Available margin (USD)
     * @param portfolioValue Total portfolio value (USD)
     * @param symbolExposure Current exposure to this symbol (USD)
     * @param marketRegime Current market regime
     * @return PositionDecision with recommendation
     */
    fun shouldOpenPosition(
        symbol: String,
        signalConfidence: Double,
        currentPositionCount: Int,
        totalPositions: Int,
        availableMargin: Double,
        portfolioValue: Double,
        symbolExposure: Double,
        marketRegime: String = "UNKNOWN"
    ): PositionDecision {
        
        // Check 1: Client override limit
        config.maxPositionsPerSymbol?.let { maxPerSymbol ->
            if (currentPositionCount >= maxPerSymbol) {
                return PositionDecision(
                    canOpen = false,
                    reason = "Client limit: max $maxPerSymbol positions per symbol (currently $currentPositionCount)"
                )
            }
        }
        
        // Check 2: Total position limit
        if (totalPositions >= config.maxTotalPositions) {
            return PositionDecision(
                canOpen = false,
                reason = "Total position limit reached: ${config.maxTotalPositions}"
            )
        }
        
        // BUILD #432: First position bypass
        // First position uses normal minConfidenceToTrade threshold (1% in DEVELOPMENT)
        // Only additional positions require the higher confidence threshold
        if (currentPositionCount == 0) {
            // First position for this symbol - skip the high confidence check
            // It will be validated against minConfidenceToTrade elsewhere
            // Just check other constraints (margin, concentration, total positions)
        } else {
            // Check 3: Signal confidence threshold for ADDITIONAL positions
            if (signalConfidence < config.minConfidenceForMultiple) {
                return PositionDecision(
                    canOpen = false,
                    reason = "Signal confidence too low for additional position: ${signalConfidence.toInt()}% (need ${config.minConfidenceForMultiple.toInt()}%)"
                )
            }
        }
        
        // Check 4: Available margin
        val marginPercent = (availableMargin / portfolioValue) * 100.0
        if (marginPercent < config.minMarginPercent) {
            return PositionDecision(
                canOpen = false,
                reason = "Insufficient margin: ${marginPercent.toInt()}% (need ${config.minMarginPercent.toInt()}%)"
            )
        }
        
        // Check 5: Portfolio concentration risk
        val concentrationPercent = (symbolExposure / portfolioValue) * 100.0
        if (concentrationPercent >= config.maxConcentrationPercent) {
            return PositionDecision(
                canOpen = false,
                reason = "Concentration risk: ${concentrationPercent.toInt()}% in $symbol (max ${config.maxConcentrationPercent.toInt()}%)"
            )
        }
        
        // Calculate recommended position limit based on market regime
        val regimeMultiplier = when (marketRegime) {
            "BULL_TRENDING", "BEAR_TRENDING" -> 1.5  // Trending = more positions allowed
            "SIDEWAYS_RANGING", "BREAKOUT_PENDING" -> 1.0  // Neutral = standard
            "HIGH_VOLATILITY", "CRASH_MODE" -> 0.5  // Volatile/Crisis = fewer positions
            else -> 1.0
        }
        
        // Calculate confidence-based position limit
        // BUILD #432: Updated thresholds to match 30% minimum
        // 30% confidence = 1 position (first position only)
        // 50% confidence = 2 positions max
        // 70% confidence = 3 positions max
        // 85% confidence = 4 positions max
        // 95%+ confidence = 5 positions max
        val confidenceBasedLimit = when {
            signalConfidence >= 95.0 -> 5
            signalConfidence >= 85.0 -> 4
            signalConfidence >= 70.0 -> 3
            signalConfidence >= 50.0 -> 2
            else -> 1  // Below 50% = first position only
        }
        
        val recommendedLimit = (confidenceBasedLimit * regimeMultiplier).toInt()
        
        // Check if we're within recommended limit
        if (currentPositionCount >= recommendedLimit) {
            return PositionDecision(
                canOpen = false,
                reason = "AI Board limit: max $recommendedLimit positions for $symbol at ${signalConfidence.toInt()}% confidence in $marketRegime regime"
            )
        }
        
        // Calculate recommended position size
        // Size decreases with each additional position to manage risk
        val sizeMultiplier = when (currentPositionCount) {
            0 -> 1.0   // First position = 100% of Kelly
            1 -> 0.75  // Second position = 75% of Kelly
            2 -> 0.5   // Third position = 50% of Kelly
            3 -> 0.4   // Fourth position = 40% of Kelly
            else -> 0.3 // Fifth+ position = 30% of Kelly
        }
        
        // Calculate risk score (0-100, lower is better)
        val riskScore = calculateRiskScore(
            currentPositionCount = currentPositionCount,
            concentrationPercent = concentrationPercent,
            marginPercent = marginPercent,
            signalConfidence = signalConfidence
        )
        
        // GREEN LIGHT - Open position!
        return PositionDecision(
            canOpen = true,
            reason = "AI Board approved: Position ${currentPositionCount + 1}/$recommendedLimit for $symbol (confidence: ${signalConfidence.toInt()}%, risk: ${riskScore.toInt()}%)",
            recommendedSize = sizeMultiplier,
            confidence = signalConfidence,
            riskScore = riskScore
        )
    }
    
    /**
     * Calculate composite risk score for opening additional position
     * 
     * @return Risk score 0-100 (lower is better)
     */
    private fun calculateRiskScore(
        currentPositionCount: Int,
        concentrationPercent: Double,
        marginPercent: Double,
        signalConfidence: Double
    ): Double {
        // Position count penalty: Each additional position adds risk
        val countPenalty = currentPositionCount * 10.0  // 0, 10, 20, 30, 40...
        
        // Concentration penalty: Higher concentration = higher risk
        val concentrationPenalty = (concentrationPercent / config.maxConcentrationPercent) * 30.0
        
        // Margin penalty: Lower margin = higher risk
        val marginPenalty = (1.0 - (marginPercent / 100.0)) * 20.0
        
        // Confidence bonus: Higher confidence = lower risk
        val confidenceBonus = ((signalConfidence - 50.0) / 50.0) * 20.0
        
        val rawScore = countPenalty + concentrationPenalty + marginPenalty - confidenceBonus
        
        // Clamp to 0-100
        return max(0.0, min(100.0, rawScore))
    }
    
    /**
     * Calculate maximum safe position count for a symbol
     * 
     * @param signalConfidence AI Board signal confidence
     * @param marketRegime Current market regime
     * @return Recommended maximum position count
     */
    fun getMaxPositionCount(
        signalConfidence: Double,
        marketRegime: String = "UNKNOWN"
    ): Int {
        // Start with client override if set
        config.maxPositionsPerSymbol?.let { return it }
        
        // Regime multiplier
        val regimeMultiplier = when (marketRegime) {
            "BULL_TRENDING", "BEAR_TRENDING" -> 1.5
            "SIDEWAYS_RANGING", "BREAKOUT_PENDING" -> 1.0
            "HIGH_VOLATILITY", "CRASH_MODE" -> 0.5
            else -> 1.0
        }
        
        // Confidence-based limit
        // BUILD #432: Updated to match 30% threshold
        val confidenceLimit = when {
            signalConfidence >= 95.0 -> 5
            signalConfidence >= 85.0 -> 4
            signalConfidence >= 70.0 -> 3
            signalConfidence >= 50.0 -> 2
            else -> 1
        }
        
        return (confidenceLimit * regimeMultiplier).toInt()
    }
    
    /**
     * Get recommended position size multiplier based on count
     * 
     * @param positionIndex 0-based position index (0 = first, 1 = second, etc.)
     * @return Size multiplier (0.0-1.0)
     */
    fun getSizeMultiplier(positionIndex: Int): Double {
        return when (positionIndex) {
            0 -> 1.0   // First position = full size
            1 -> 0.75  // Second position = 75%
            2 -> 0.5   // Third position = 50%
            3 -> 0.4   // Fourth position = 40%
            else -> 0.3 // Fifth+ = 30%
        }
    }
}

/**
 * Extension: Calculate symbol exposure across all positions
 */
fun List<Position>.getExposureForSymbol(symbol: String): Double {
    return this.filter { it.symbol == symbol }
        .sumOf { it.margin + it.unrealizedPnl }
}

/**
 * Extension: Count positions for a symbol
 */
fun List<Position>.countForSymbol(symbol: String): Int {
    return this.count { it.symbol == symbol }
}
