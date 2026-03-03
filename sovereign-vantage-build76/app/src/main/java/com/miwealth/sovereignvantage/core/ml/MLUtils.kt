package com.miwealth.sovereignvantage.core.ml

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Shared Mathematical Utilities for ML Components
 * 
 * Centralizes common calculations used across:
 * - EnhancedFeatures
 * - KellyPositionSizer
 * - MarketRegimeDetector
 * - MultiTimeframeAnalysis
 * - EnsembleModels
 * 
 * For technical indicators (EMA, RSI, MACD, etc.), use:
 * - com.miwealth.sovereignvantage.core.indicators.TrendIndicators
 * - com.miwealth.sovereignvantage.core.indicators.MomentumIndicators
 * - com.miwealth.sovereignvantage.core.indicators.VolatilityVolumeIndicators
 * 
 * @author MiWealth Pty Ltd
 * @version 5.5.15
 */
object MLUtils {

    /**
     * Calculates standard deviation of a list of values
     * Uses population standard deviation (N divisor)
     */
    fun standardDeviation(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        if (values.size == 1) return 0.0
        
        val mean = values.average()
        val sumSquaredDiff = values.sumOf { (it - mean).pow(2) }
        return sqrt(sumSquaredDiff / values.size)
    }

    /**
     * Calculates sample standard deviation
     * Uses Bessel's correction (N-1 divisor)
     */
    fun sampleStandardDeviation(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        
        val mean = values.average()
        val sumSquaredDiff = values.sumOf { (it - mean).pow(2) }
        return sqrt(sumSquaredDiff / (values.size - 1))
    }

    /**
     * Calculates variance of a list of values
     */
    fun variance(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        
        val mean = values.average()
        return values.sumOf { (it - mean).pow(2) } / values.size
    }

    /**
     * Calculates covariance between two lists
     */
    fun covariance(x: List<Double>, y: List<Double>): Double {
        require(x.size == y.size) { "Lists must have same size" }
        if (x.isEmpty()) return 0.0
        
        val meanX = x.average()
        val meanY = y.average()
        
        return x.indices.sumOf { i -> (x[i] - meanX) * (y[i] - meanY) } / x.size
    }

    /**
     * Calculates Pearson correlation coefficient
     */
    fun correlation(x: List<Double>, y: List<Double>): Double {
        val cov = covariance(x, y)
        val stdX = standardDeviation(x)
        val stdY = standardDeviation(y)
        
        if (stdX == 0.0 || stdY == 0.0) return 0.0
        return cov / (stdX * stdY)
    }

    /**
     * Calculates linear regression slope
     */
    fun linearSlope(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        
        val n = values.size
        val x = (0 until n).map { it.toDouble() }
        val y = values
        
        val xMean = x.average()
        val yMean = y.average()
        
        val numerator = x.indices.sumOf { i -> (x[i] - xMean) * (y[i] - yMean) }
        val denominator = x.sumOf { (it - xMean).pow(2) }
        
        return if (denominator > 0) numerator / denominator else 0.0
    }

    /**
     * Normalizes a value to 0-1 range
     */
    fun normalize(value: Double, min: Double, max: Double): Double {
        if (max == min) return 0.5
        return ((value - min) / (max - min)).coerceIn(0.0, 1.0)
    }

    /**
     * Normalizes a value to -1 to 1 range
     */
    fun normalizeSymmetric(value: Double, min: Double, max: Double): Double {
        if (max == min) return 0.0
        return (2.0 * (value - min) / (max - min) - 1.0).coerceIn(-1.0, 1.0)
    }

    /**
     * Calculates percentage change
     */
    fun percentChange(oldValue: Double, newValue: Double): Double {
        if (oldValue == 0.0) return 0.0
        return ((newValue - oldValue) / oldValue) * 100.0
    }

    /**
     * Calculates exponential decay
     */
    fun exponentialDecay(initialValue: Double, decayRate: Double, time: Double): Double {
        return initialValue * kotlin.math.exp(-decayRate * time)
    }

    /**
     * Sigmoid function (logistic)
     */
    fun sigmoid(x: Double): Double {
        return 1.0 / (1.0 + kotlin.math.exp(-x))
    }

    /**
     * Softmax for a list of values
     */
    fun softmax(values: List<Double>): List<Double> {
        if (values.isEmpty()) return emptyList()
        
        val maxVal = values.maxOrNull() ?: 0.0
        val expValues = values.map { kotlin.math.exp(it - maxVal) }
        val sumExp = expValues.sum()
        
        return if (sumExp > 0) expValues.map { it / sumExp } else values.map { 1.0 / values.size }
    }

    /**
     * Calculates rolling mean for the last N values
     */
    fun rollingMean(values: List<Double>, window: Int): Double {
        if (values.isEmpty()) return 0.0
        return values.takeLast(window).average()
    }

    /**
     * Calculates rolling standard deviation for the last N values
     */
    fun rollingStdDev(values: List<Double>, window: Int): Double {
        if (values.isEmpty()) return 0.0
        return standardDeviation(values.takeLast(window))
    }

    /**
     * Calculates Z-score for a value given mean and stdDev
     */
    fun zScore(value: Double, mean: Double, stdDev: Double): Double {
        if (stdDev == 0.0) return 0.0
        return (value - mean) / stdDev
    }

    /**
     * Clips gradients by norm (for preventing exploding gradients)
     */
    fun clipByNorm(gradients: List<Double>, maxNorm: Double): List<Double> {
        val norm = sqrt(gradients.sumOf { it * it })
        return if (norm > maxNorm) {
            val scale = maxNorm / norm
            gradients.map { it * scale }
        } else {
            gradients
        }
    }

    /**
     * Clips gradients by value
     */
    fun clipByValue(gradients: List<Double>, maxValue: Double): List<Double> {
        return gradients.map { it.coerceIn(-maxValue, maxValue) }
    }
}
