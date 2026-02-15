package com.miwealth.sovereignvantage.core.ml

import kotlin.math.sqrt

/**
 * Feature Normalizer with Z-Score Normalization and Cross-Feature Interactions
 * 
 * Implements AI Model Audit recommendations:
 * - Z-score normalization for 2-5x convergence speed
 * - Cross-feature interactions for 10-20% accuracy boost
 * 
 * V5.5.83: Quick wins from AI_MODEL_AUDIT_AND_RECOMMENDATIONS.md
 * 
 * @author MiWealth Pty Ltd
 * @version 5.5.83 "Arthur Edition"
 */
class FeatureNormalizer {
    
    // Running statistics for z-score normalization
    private data class FeatureStats(
        var count: Int = 0,
        var mean: Double = 0.0,
        var m2: Double = 0.0  // For Welford's online variance
    ) {
        val variance: Double
            get() = if (count > 1) m2 / (count - 1) else 1.0
        
        val stdDev: Double
            get() = sqrt(variance)
    }
    
    // Statistics for each base feature (19 features)
    private val stats = mutableMapOf<String, FeatureStats>()
    
    // Minimum observations before normalizing (use raw features until then)
    private val minObservations = 10
    
    /**
     * Update running statistics with new observation (Welford's online algorithm)
     * Numerically stable and memory efficient
     */
    fun update(features: EnhancedFeatureVector) {
        updateStat("marketPrice", features.marketPrice)
        updateStat("trend", features.trend)
        updateStat("volatility", features.volatility)
        updateStat("volumeProfile", features.volumeProfile)
        updateStat("ema20", features.ema20)
        updateStat("ema50", features.ema50)
        updateStat("rsi", features.rsi)
        updateStat("macd", features.macd)
        updateStat("macdHistogram", features.macdHistogram)
        updateStat("momentumScore", features.momentumScore)
        updateStat("roc", features.roc)
        updateStat("stochastic", features.stochastic)
        updateStat("williamsR", features.williamsR)
        updateStat("atr", features.atr)
        updateStat("bollingerBandPosition", features.bollingerBandPosition)
        updateStat("sentimentScore", features.sentimentScore)
        updateStat("fearGreedIndex", features.fearGreedIndex)
        updateStat("socialVolume", features.socialVolume)
        updateStat("newsImpact", features.newsImpact)
    }
    
    private fun updateStat(name: String, value: Double) {
        val stat = stats.getOrPut(name) { FeatureStats() }
        stat.count++
        val delta = value - stat.mean
        stat.mean += delta / stat.count
        val delta2 = value - stat.mean
        stat.m2 += delta * delta2
    }
    
    /**
     * Z-score normalize a single feature: (x - mean) / stdDev
     * Returns raw value if insufficient observations
     */
    private fun normalize(name: String, value: Double): Double {
        val stat = stats[name] ?: return value
        
        if (stat.count < minObservations) {
            return value  // Not enough data yet
        }
        
        val stdDev = stat.stdDev
        if (stdDev < 1e-8) {
            return 0.0  // Constant feature, no variance
        }
        
        return (value - stat.mean) / stdDev
    }
    
    /**
     * Normalize all features and create cross-feature interactions
     * 
     * Returns normalized feature array with:
     * - 19 base features (z-score normalized)
     * - 10 cross-feature interactions
     * = 29 total features for neural network
     * 
     * Cross-features capture important relationships:
     * 1. trend * volatility: Trend strength in volatile markets
     * 2. rsi * volumeProfile: RSI reliability with volume confirmation
     * 3. macd * trend: MACD confirming trend direction
     * 4. sentimentScore * momentumScore: Sentiment-momentum alignment
     * 5. bollingerBandPosition * volatility: BB squeeze/expansion
     * 6. stochastic * rsi: Momentum oscillator agreement
     * 7. atr * trend: Trend strength with volatility context
     * 8. fearGreedIndex * sentimentScore: Fear/greed vs sentiment
     * 9. ema20 * ema50: Moving average interaction
     * 10. newsImpact * socialVolume: News impact with social activity
     */
    fun normalizeWithInteractions(features: EnhancedFeatureVector, currentPosition: Double): DoubleArray {
        // Update statistics first
        update(features)
        
        // Normalize base features (19 features)
        val normalized = doubleArrayOf(
            normalize("marketPrice", features.marketPrice),
            normalize("trend", features.trend),
            normalize("volatility", features.volatility),
            normalize("volumeProfile", features.volumeProfile),
            normalize("ema20", features.ema20),
            normalize("ema50", features.ema50),
            normalize("rsi", features.rsi),
            normalize("macd", features.macd),
            normalize("macdHistogram", features.macdHistogram),
            normalize("momentumScore", features.momentumScore),
            normalize("roc", features.roc),
            normalize("stochastic", features.stochastic),
            normalize("williamsR", features.williamsR),
            normalize("atr", features.atr),
            normalize("bollingerBandPosition", features.bollingerBandPosition),
            normalize("sentimentScore", features.sentimentScore),
            normalize("fearGreedIndex", features.fearGreedIndex),
            normalize("socialVolume", features.socialVolume),
            normalize("newsImpact", features.newsImpact)
        )
        
        // Create cross-feature interactions (10 interactions)
        // These capture non-linear relationships the model needs to learn
        val interactions = doubleArrayOf(
            // 1. Trend strength in volatile markets
            normalized[1] * normalized[2],  // trend * volatility
            
            // 2. RSI reliability with volume confirmation
            normalized[6] * normalized[3],  // rsi * volumeProfile
            
            // 3. MACD confirming trend
            normalized[7] * normalized[1],  // macd * trend
            
            // 4. Sentiment-momentum alignment
            normalized[15] * normalized[9], // sentimentScore * momentumScore
            
            // 5. Bollinger Band squeeze/expansion context
            normalized[14] * normalized[2], // bollingerBandPosition * volatility
            
            // 6. Momentum oscillator agreement
            normalized[11] * normalized[6], // stochastic * rsi
            
            // 7. Trend strength with volatility context
            normalized[13] * normalized[1], // atr * trend
            
            // 8. Fear/greed sentiment alignment
            normalized[16] * normalized[15], // fearGreedIndex * sentimentScore
            
            // 9. Moving average crossover interaction
            normalized[4] * normalized[5],  // ema20 * ema50
            
            // 10. News impact with social confirmation
            normalized[18] * normalized[17]  // newsImpact * socialVolume
        )
        
        // Add current position as 30th feature (already normalized -1 to 1)
        val positionFeature = currentPosition.coerceIn(-1.0, 1.0)
        
        // Combine: 19 base + 10 interactions + 1 position = 30 features
        return normalized + interactions + positionFeature
    }
    
    /**
     * Get total feature count (for neural network input size)
     */
    fun getFeatureCount(): Int = 30  // 19 base + 10 interactions + 1 position
    
    /**
     * Get observation count for a feature (for monitoring)
     */
    fun getObservationCount(featureName: String): Int {
        return stats[featureName]?.count ?: 0
    }
    
    /**
     * Get mean for a feature (for debugging)
     */
    fun getMean(featureName: String): Double {
        return stats[featureName]?.mean ?: 0.0
    }
    
    /**
     * Get standard deviation for a feature (for debugging)
     */
    fun getStdDev(featureName: String): Double {
        return stats[featureName]?.stdDev ?: 1.0
    }
    
    /**
     * Check if normalizer has sufficient data
     */
    fun isReady(): Boolean {
        return stats.values.any { it.count >= minObservations }
    }
    
    /**
     * Reset statistics (for retraining from scratch)
     */
    fun reset() {
        stats.clear()
    }
    
    /**
     * Get summary of normalization statistics
     */
    fun getSummary(): String {
        val avgCount = stats.values.map { it.count }.average().toInt()
        val readyFeatures = stats.values.count { it.count >= minObservations }
        
        return buildString {
            append("FeatureNormalizer: ")
            append("$readyFeatures/${stats.size} features ready, ")
            append("avg $avgCount observations")
        }
    }
}
