package com.miwealth.sovereignvantage.core.ml

/**
 * BUILD #254: MINIMAL STUB
 * 
 * EnhancedFeatureVector was deleted in Build #251 revert but multiple files still reference it.
 * This is a minimal stub containing only the fields that other files expect.
 * 
 * Original implementation was part of Build #242 Real-Time DQN Learning System.
 * This stub allows compilation without implementing the full feature extraction logic.
 */
data class EnhancedFeatureVector(
    // Price & Market Data
    val marketPrice: Double = 0.0,
    val trend: Double = 0.0,
    val volatility: Double = 0.0,
    val volumeProfile: Double = 0.0,
    
    // Moving Averages
    val ema20: Double = 0.0,
    val ema50: Double = 0.0,
    
    // Momentum Indicators
    val rsi: Double = 50.0,
    val macd: Double = 0.0,
    val macdHistogram: Double = 0.0,
    val momentumScore: Double = 0.0,
    val roc: Double = 0.0,
    val stochastic: Double = 50.0,
    val williamsR: Double = -50.0,
    
    // Volatility Indicators
    val atr: Double = 0.0,
    val bollingerBandPosition: Double = 0.5,
    
    // Sentiment & External Data
    val sentimentScore: Double = 0.0,
    val fearGreedIndex: Double = 50.0,
    val socialVolume: Double = 0.0,
    val newsImpact: Double = 0.0
) {
    /**
     * Convert to DoubleArray for neural network input.
     * Returns all features in a fixed order.
     */
    fun toArray(): DoubleArray = doubleArrayOf(
        marketPrice,
        trend,
        volatility,
        volumeProfile,
        ema20,
        ema50,
        rsi,
        macd,
        macdHistogram,
        momentumScore,
        roc,
        stochastic,
        williamsR,
        atr,
        bollingerBandPosition,
        sentimentScore,
        fearGreedIndex,
        socialVolume,
        newsImpact
    )
    
    companion object {
        /**
         * Number of features in the vector.
         */
        const val FEATURE_COUNT = 19
        
        /**
         * Create a default/empty feature vector.
         */
        fun empty(): EnhancedFeatureVector = EnhancedFeatureVector()
    }
}
