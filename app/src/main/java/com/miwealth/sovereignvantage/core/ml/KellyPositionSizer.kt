package com.miwealth.sovereignvantage.core.ml

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Kelly Criterion Position Sizing
 * Maximizes long-term capital growth while controlling drawdown risk
 */

class KellyPositionSizer {
    
    /**
     * Calculates optimal position size using Kelly Criterion
     * 
     * Kelly% = (Win Rate * Avg Win - Loss Rate * Avg Loss) / Avg Win
     * 
     * We use fractional Kelly (typically 25% of full Kelly) for safety
     */
    fun calculateOptimalSize(
        winRate: Double,
        avgWin: Double,
        avgLoss: Double,
        totalCapital: Double,
        maxKellyFraction: Double = 0.25,  // Conservative Kelly (25%)
        minPositionPct: Double = 0.01,    // Min 1% of capital
        maxPositionPct: Double = 0.10     // Max 10% of capital
    ): PositionSize {
        
        require(winRate in 0.0..1.0) { "Win rate must be between 0 and 1" }
        require(avgWin > 0) { "Average win must be positive" }
        require(avgLoss > 0) { "Average loss must be positive" }
        require(totalCapital > 0) { "Total capital must be positive" }
        
        val lossRate = 1.0 - winRate
        
        // Calculate Kelly percentage
        val kellyPercentage = (winRate * avgWin - lossRate * avgLoss) / avgWin
        
        // Apply fractional Kelly for safety
        val conservativeKelly = kellyPercentage * maxKellyFraction
        
        // Ensure within bounds
        val boundedKelly = conservativeKelly.coerceIn(minPositionPct, maxPositionPct)
        
        // Calculate position size in capital
        val positionSize = totalCapital * boundedKelly
        
        // Calculate expected value
        val expectedValue = (winRate * avgWin) - (lossRate * avgLoss)
        
        // Calculate risk of ruin (simplified)
        val riskOfRuin = calculateRiskOfRuin(winRate, avgWin, avgLoss, boundedKelly)
        
        return PositionSize(
            capitalAmount = positionSize,
            percentage = boundedKelly * 100.0,
            kellyPercentage = kellyPercentage * 100.0,
            fractionalKelly = maxKellyFraction,
            expectedValue = expectedValue,
            riskOfRuin = riskOfRuin,
            recommendation = getRecommendation(kellyPercentage, expectedValue, riskOfRuin)
        )
    }
    
    /**
     * Calculates position size with confidence adjustment
     * Lower confidence = smaller position
     */
    fun calculateWithConfidence(
        winRate: Double,
        avgWin: Double,
        avgLoss: Double,
        totalCapital: Double,
        confidence: Double,  // 0.0 to 1.0
        maxKellyFraction: Double = 0.25
    ): PositionSize {
        
        require(confidence in 0.0..1.0) { "Confidence must be between 0 and 1" }
        
        // Adjust Kelly fraction based on confidence
        val adjustedKellyFraction = maxKellyFraction * confidence
        
        return calculateOptimalSize(
            winRate = winRate,
            avgWin = avgWin,
            avgLoss = avgLoss,
            totalCapital = totalCapital,
            maxKellyFraction = adjustedKellyFraction
        )
    }
    
    /**
     * Calculates position size for multiple concurrent positions
     */
    fun calculateForMultiplePositions(
        winRate: Double,
        avgWin: Double,
        avgLoss: Double,
        totalCapital: Double,
        currentPositions: Int,
        maxConcurrentPositions: Int = 5,
        maxKellyFraction: Double = 0.25
    ): PositionSize {
        
        // Reduce position size based on number of concurrent positions
        val availableCapital = totalCapital * (1.0 - (currentPositions.toDouble() / maxConcurrentPositions))
        
        return calculateOptimalSize(
            winRate = winRate,
            avgWin = avgWin,
            avgLoss = avgLoss,
            totalCapital = availableCapital,
            maxKellyFraction = maxKellyFraction
        )
    }
    
    /**
     * Calculates optimal leverage using Kelly Criterion
     */
    fun calculateOptimalLeverage(
        winRate: Double,
        avgWinPct: Double,  // Average win as percentage
        avgLossPct: Double,  // Average loss as percentage
        maxLeverage: Double = 3.0
    ): LeverageRecommendation {
        
        val lossRate = 1.0 - winRate
        
        // Kelly leverage formula
        val kellyLeverage = (winRate * avgWinPct - lossRate * avgLossPct) / avgLossPct
        
        // Conservative leverage (50% of Kelly)
        val conservativeLeverage = (kellyLeverage * 0.5).coerceIn(1.0, maxLeverage)
        
        // Calculate liquidation risk
        val liquidationRisk = calculateLiquidationRisk(conservativeLeverage, avgLossPct)
        
        return LeverageRecommendation(
            optimalLeverage = conservativeLeverage,
            kellyLeverage = kellyLeverage,
            liquidationRisk = liquidationRisk,
            isRecommended = conservativeLeverage <= 2.0 && liquidationRisk < 0.05
        )
    }
    
    /**
     * Dynamic position sizing based on recent performance
     */
    fun calculateDynamicSize(
        recentTrades: List<TradeResult>,
        totalCapital: Double,
        lookbackPeriod: Int = 30,
        maxKellyFraction: Double = 0.25
    ): PositionSize {
        
        if (recentTrades.isEmpty()) {
            // No history - use conservative default
            return calculateOptimalSize(
                winRate = 0.50,
                avgWin = 100.0,
                avgLoss = 50.0,
                totalCapital = totalCapital,
                maxKellyFraction = 0.10  // Very conservative
            )
        }
        
        val relevantTrades = recentTrades.takeLast(lookbackPeriod)
        
        // Calculate statistics from recent trades
        val wins = relevantTrades.filter { it.profitLoss > 0 }
        val losses = relevantTrades.filter { it.profitLoss < 0 }
        
        val winRate = wins.size.toDouble() / relevantTrades.size.toDouble()
        val avgWin = if (wins.isNotEmpty()) wins.map { abs(it.profitLoss) }.average() else 100.0
        val avgLoss = if (losses.isNotEmpty()) losses.map { abs(it.profitLoss) }.average() else 50.0
        
        // Adjust Kelly fraction based on consistency
        val consistency = calculateConsistency(relevantTrades)
        val adjustedKellyFraction = maxKellyFraction * consistency
        
        return calculateOptimalSize(
            winRate = winRate,
            avgWin = avgWin,
            avgLoss = avgLoss,
            totalCapital = totalCapital,
            maxKellyFraction = adjustedKellyFraction
        )
    }
    
    // Private helper functions
    
    private fun calculateRiskOfRuin(
        winRate: Double,
        avgWin: Double,
        avgLoss: Double,
        positionSizePct: Double
    ): Double {
        // Simplified risk of ruin calculation
        // Assumes geometric Brownian motion
        
        val lossRate = 1.0 - winRate
        val winLossRatio = avgWin / avgLoss
        
        if (winRate * winLossRatio > lossRate) {
            // Positive expectancy - low risk of ruin
            val riskRatio = lossRate / (winRate * winLossRatio)
            return (riskRatio.pow(1.0 / positionSizePct)).coerceIn(0.0, 1.0)
        } else {
            // Negative expectancy - high risk of ruin
            return 0.95
        }
    }
    
    private fun calculateLiquidationRisk(leverage: Double, avgLossPct: Double): Double {
        // Risk of liquidation = probability that loss exceeds margin
        // Simplified: assumes normal distribution of returns
        
        val liquidationThreshold = 100.0 / leverage  // e.g., 33% for 3x leverage
        val sigma = avgLossPct * 2.0  // Estimate volatility
        
        // Z-score for liquidation
        val zScore = liquidationThreshold / sigma
        
        // Approximate probability (simplified)
        return (1.0 / (1.0 + zScore)).coerceIn(0.0, 1.0)
    }
    
    private fun calculateConsistency(trades: List<TradeResult>): Double {
        if (trades.size < 10) return 0.5  // Not enough data
        
        // Calculate consistency as inverse of coefficient of variation
        val returns = trades.map { it.profitLoss }
        val mean = returns.average()
        val stdDev = MLUtils.standardDeviation(returns)
        
        if (mean == 0.0 || stdDev == 0.0) return 0.5
        
        val cv = abs(stdDev / mean)
        val consistency = 1.0 / (1.0 + cv)
        
        return consistency.coerceIn(0.0, 1.0)
    }
    
    private fun getRecommendation(
        kellyPercentage: Double,
        expectedValue: Double,
        riskOfRuin: Double
    ): String {
        return when {
            kellyPercentage < 0 -> "AVOID - Negative expectancy"
            riskOfRuin > 0.20 -> "HIGH RISK - Consider reducing position size"
            expectedValue < 0 -> "AVOID - Negative expected value"
            kellyPercentage > 0.50 -> "EXCELLENT - Strong edge detected"
            kellyPercentage > 0.25 -> "GOOD - Positive expectancy"
            kellyPercentage > 0.10 -> "MODERATE - Small edge"
            else -> "MARGINAL - Very small edge"
        }
    }
    
    private fun Double.pow(exponent: Double): Double {
        return kotlin.math.pow(this, exponent)
    }
}

// Data classes

data class PositionSize(
    val capitalAmount: Double,
    val percentage: Double,
    val kellyPercentage: Double,
    val fractionalKelly: Double,
    val expectedValue: Double,
    val riskOfRuin: Double,
    val recommendation: String
)

data class LeverageRecommendation(
    val optimalLeverage: Double,
    val kellyLeverage: Double,
    val liquidationRisk: Double,
    val isRecommended: Boolean
)

data class TradeResult(
    val profitLoss: Double,
    val timestamp: Long
)

/**
 * Position sizing strategy that adapts to market conditions
 */
class AdaptivePositionSizer(
    private val kellySizer: KellyPositionSizer,
    private val regimeDetector: MarketRegimeDetector
) {
    
    fun calculateAdaptiveSize(
        winRate: Double,
        avgWin: Double,
        avgLoss: Double,
        totalCapital: Double,
        confidence: Double,
        priceHistory: List<Double>,
        volumeHistory: List<Double>,
        volatilityHistory: List<Double>
    ): PositionSize {
        
        // Detect market regime
        val regime = regimeDetector.detectRegime(priceHistory, volumeHistory, volatilityHistory)
        
        // Calculate base position size
        val baseSize = kellySizer.calculateWithConfidence(
            winRate = winRate,
            avgWin = avgWin,
            avgLoss = avgLoss,
            totalCapital = totalCapital,
            confidence = confidence
        )
        
        // Adjust for market regime
        val adjustedCapital = baseSize.capitalAmount * regime.riskMultiplier
        val adjustedPercentage = (adjustedCapital / totalCapital) * 100.0
        
        return baseSize.copy(
            capitalAmount = adjustedCapital,
            percentage = adjustedPercentage,
            recommendation = "${baseSize.recommendation} | Regime: ${regime.currentRegime}"
        )
    }
}
