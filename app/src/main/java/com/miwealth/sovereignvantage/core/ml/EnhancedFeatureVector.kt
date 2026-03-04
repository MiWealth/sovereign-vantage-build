package com.miwealth.sovereignvantage.core.ml

/**
 * Enhanced Feature Vector for ML Models
 * 
 * Comprehensive feature set used across all ML components:
 * - EnsembleModels (trend, momentum, technical, sentiment predictions)
 * - ElasticWeightConsolidation (continual learning)
 * - ReinforcementLearning (state discretization)
 * - MarketRegimeDetector (regime classification)
 * 
 * @author MiWealth Pty Ltd
 * @version 5.5.15
 */
data class EnhancedFeatureVector(
    // Price & Market Data
    val marketPrice: Double,
    val trend: Double,                    // -1.0 (bearish) to 1.0 (bullish)
    val volatility: Double,               // Normalized volatility (0.0 to 1.0)
    val volumeProfile: Double,            // Volume relative to average (1.0 = average)
    
    // Moving Averages
    val ema20: Double,
    val ema50: Double,
    
    // Momentum Indicators
    val rsi: Double,                      // 0 to 100
    val macd: Double,
    val macdHistogram: Double,
    val momentumScore: Double,            // Composite momentum (-1.0 to 1.0)
    val roc: Double,                      // Rate of Change
    val stochastic: Double,               // Stochastic oscillator (0 to 100)
    val williamsR: Double,                // Williams %R (-100 to 0)
    
    // Volatility Indicators
    val atr: Double,                      // Average True Range
    val bollingerBandPosition: Double,    // Position within bands (-1.0 to 1.0)
    
    // Sentiment Indicators
    val sentimentScore: Double,           // -1.0 (bearish) to 1.0 (bullish)
    val fearGreedIndex: Double,           // 0 (extreme fear) to 100 (extreme greed)
    val socialVolume: Double,             // Social media activity volume
    val newsImpact: Double                // News sentiment impact (-1.0 to 1.0)
) {
    companion object {
        /**
         * Creates a default/neutral feature vector
         * Useful for initialization or when data is unavailable
         */
        fun neutral(marketPrice: Double = 0.0): EnhancedFeatureVector {
            return EnhancedFeatureVector(
                marketPrice = marketPrice,
                trend = 0.0,
                volatility = 0.5,
                volumeProfile = 1.0,
                ema20 = marketPrice,
                ema50 = marketPrice,
                rsi = 50.0,
                macd = 0.0,
                macdHistogram = 0.0,
                momentumScore = 0.0,
                roc = 0.0,
                stochastic = 50.0,
                williamsR = -50.0,
                atr = 0.0,
                bollingerBandPosition = 0.0,
                sentimentScore = 0.0,
                fearGreedIndex = 50.0,
                socialVolume = 0.0,
                newsImpact = 0.0
            )
        }
        
        /**
         * Creates feature vector from EnhancedFeatures.Features
         * Bridge method for backward compatibility
         */
        fun fromLegacyFeatures(
            features: EnhancedFeatures.Features,
            marketPrice: Double,
            sentimentScore: Double = 0.0,
            fearGreedIndex: Double = 50.0,
            socialVolume: Double = 0.0,
            newsImpact: Double = 0.0
        ): EnhancedFeatureVector {
            return EnhancedFeatureVector(
                marketPrice = marketPrice,
                trend = calculateTrendFromFeatures(features),
                volatility = features.historicalVolatility / 100.0,
                volumeProfile = 1.0,  // Not available in legacy
                ema20 = features.ema20,
                ema50 = features.ema50,
                rsi = features.rsi,
                macd = features.macd,
                macdHistogram = features.macdHist,
                momentumScore = calculateMomentumScore(features),
                roc = features.roc,
                stochastic = features.stochasticK,
                williamsR = features.williamsR,
                atr = features.atr,
                bollingerBandPosition = features.bollingerPos,
                sentimentScore = sentimentScore,
                fearGreedIndex = fearGreedIndex,
                socialVolume = socialVolume,
                newsImpact = newsImpact
            )
        }
        
        private fun calculateTrendFromFeatures(features: EnhancedFeatures.Features): Double {
            // Derive trend from EMA relationship
            return when {
                features.ema20 > features.ema50 * 1.02 -> 0.8   // Strong bullish
                features.ema20 > features.ema50 -> 0.4          // Mild bullish
                features.ema20 < features.ema50 * 0.98 -> -0.8  // Strong bearish
                features.ema20 < features.ema50 -> -0.4         // Mild bearish
                else -> 0.0                                      // Neutral
            }
        }
        
        private fun calculateMomentumScore(features: EnhancedFeatures.Features): Double {
            // Composite momentum from RSI, MACD, ROC
            val rsiScore = (features.rsi - 50.0) / 50.0  // -1 to 1
            val macdScore = (features.macd / 10.0).coerceIn(-1.0, 1.0)
            val rocScore = (features.roc / 10.0).coerceIn(-1.0, 1.0)
            
            return (rsiScore * 0.4 + macdScore * 0.35 + rocScore * 0.25)
                .coerceIn(-1.0, 1.0)
        }
    }
    
    /**
     * Validates that all values are within expected ranges
     */
    fun isValid(): Boolean {
        return marketPrice >= 0.0 &&
               trend in -1.0..1.0 &&
               volatility in 0.0..1.0 &&
               rsi in 0.0..100.0 &&
               stochastic in 0.0..100.0 &&
               williamsR in -100.0..0.0 &&
               fearGreedIndex in 0.0..100.0
    }
    
    /**
     * Returns a summary string for logging/debugging
     */
    fun toSummary(): String {
        return "Price=${"%.2f".format(marketPrice)} | " +
               "Trend=${"%.2f".format(trend)} | " +
               "RSI=${"%.1f".format(rsi)} | " +
               "Vol=${"%.3f".format(volatility)} | " +
               "Sentiment=${"%.2f".format(sentimentScore)}"
    }
}
