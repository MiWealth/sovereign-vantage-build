package com.miwealth.sovereignvantage.core.dflp

import kotlin.math.exp
import kotlin.random.Random

/**
 * Local Model Trainer
 * Trains AI model on-device using trade data
 * Contributes to DFLP without sharing raw data
 * 
 * Sovereign Vantage: Arthur Edition V5.17.0
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * Uses centralized DFLPConfiguration for all timing and training parameters.
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 */

data class FeatureVector(
    val marketPrice: Double,
    val volume: Double,
    val volatility: Double,
    val rsi: Double,
    val macd: Double,
    val trend: Double,  // -1 to 1
    val assetType: String,
    val strategyType: String
)

data class TradeLabel(
    val success: Boolean,
    val profitLoss: Double,
    val confidence: Double
)

data class TrainingExample(
    val input: FeatureVector,
    val output: TradeLabel
)

/**
 * Model weights container for DFLP operations.
 * 
 * @param weights The neural network weights as doubles
 * @param version Semantic version string for compatibility checking
 * @param timestamp When these weights were generated (for staleness)
 */
data class ModelWeights(
    val weights: List<Double>,
    val version: String = "1.0.0",
    val timestamp: java.time.Instant = java.time.Instant.now()
) {
    /**
     * Convert to FloatArray for efficient storage/transmission.
     */
    fun toFloatArray(): FloatArray = weights.map { it.toFloat() }.toFloatArray()
    
    companion object {
        /**
         * Create from FloatArray (e.g., after receiving from network).
         */
        fun fromFloatArray(
            floatWeights: FloatArray,
            version: String = "1.0.0",
            timestamp: java.time.Instant = java.time.Instant.now()
        ): ModelWeights = ModelWeights(
            weights = floatWeights.map { it.toDouble() },
            version = version,
            timestamp = timestamp
        )
    }
}

class LocalModelTrainer {
    
    private var localModelWeights: ModelWeights = initializeWeights()
    // Note: Now uses centralized DFLPConfiguration object instead of instance config
    
    /**
     * Train local model on new trades
     * Both paper and live trades contribute with different weights
     */
    fun trainOnNewTrades(trades: List<TradeRecord>) {
        if (trades.isEmpty()) return
        
        // Convert trades to training examples
        val trainingData = trades.map { trade ->
            val contributionWeight = calculateContributionWeight(trade)
            
            TrainingExample(
                input = FeatureVector(
                    marketPrice = trade.marketConditions.price,
                    volume = trade.marketConditions.volume,
                    volatility = trade.marketConditions.volatility,
                    rsi = trade.marketConditions.rsi,
                    macd = trade.marketConditions.macd,
                    trend = parseTrend(trade.marketConditions.trend),
                    assetType = trade.asset,
                    strategyType = trade.strategyUsed
                ),
                output = TradeLabel(
                    success = trade.outcome == TradeOutcome.SUCCESS,
                    profitLoss = trade.profitLoss ?: 0.0,
                    confidence = contributionWeight
                )
            )
        }
        
        // Train model with gradient descent
        val updatedWeights = performGradientDescent(
            currentWeights = localModelWeights,
            trainingData = trainingData,
            learningRate = DFLPConfiguration.LOCAL_LEARNING_RATE,
            epochs = DFLPConfiguration.LOCAL_TRAINING_EPOCHS
        )
        
        // Save updated weights
        localModelWeights = updatedWeights
        saveLocalModelWeights(updatedWeights)
        
        // Prepare for DFLP aggregation
        prepareForAggregation(updatedWeights)
    }
    
    /**
     * Calculate contribution weight based on trade type and quality
     * Paper trades: 0.7x weight (DFLPConfiguration.PAPER_TRADE_WEIGHT)
     * Live trades: 1.0x weight (DFLPConfiguration.LIVE_TRADE_WEIGHT)
     */
    private fun calculateContributionWeight(trade: TradeRecord): Double {
        var weight = 1.0
        
        // Factor 1: Trade type
        weight *= when (trade.tradeType) {
            TradeType.LIVE -> DFLPConfiguration.LIVE_TRADE_WEIGHT
            TradeType.PAPER -> DFLPConfiguration.PAPER_TRADE_WEIGHT
        }
        
        // Factor 2: Outcome confidence
        weight *= when {
            trade.outcome == TradeOutcome.SUCCESS && (trade.profitLoss ?: 0.0) > 0 -> 1.0
            trade.outcome == TradeOutcome.LOSS && (trade.profitLoss ?: 0.0) < 0 -> 1.0
            else -> 0.5  // Uncertain outcomes
        }
        
        return weight.coerceIn(0.1, 1.5)
    }
    
    private fun parseTrend(trend: String): Double {
        return when (trend.uppercase()) {
            "BULLISH" -> 1.0
            "BEARISH" -> -1.0
            else -> 0.0
        }
    }
    
    private fun performGradientDescent(
        currentWeights: ModelWeights,
        trainingData: List<TrainingExample>,
        learningRate: Double,
        epochs: Int
    ): ModelWeights {
        var weights = currentWeights.weights.toMutableList()
        
        repeat(epochs) {
            trainingData.forEach { example ->
                // Simple gradient descent update
                val prediction = predict(example.input, weights)
                val error = if (example.output.success) 1.0 - prediction else prediction
                val gradient = error * example.output.confidence
                
                // Update weights
                weights = weights.mapIndexed { index, weight ->
                    weight + learningRate * gradient * getFeatureValue(example.input, index)
                }.toMutableList()
            }
        }
        
        return ModelWeights(weights, version = "1.0.1")
    }
    
    private fun predict(input: FeatureVector, weights: List<Double>): Double {
        val features = listOf(
            input.marketPrice / 10000.0,  // Normalize
            input.volume / 1000000.0,
            input.volatility,
            input.rsi / 100.0,
            input.macd,
            input.trend
        )
        
        val sum = features.zip(weights).sumOf { (f, w) -> f * w }
        return sigmoid(sum)
    }
    
    private fun sigmoid(x: Double): Double {
        return 1.0 / (1.0 + exp(-x))
    }
    
    private fun getFeatureValue(input: FeatureVector, index: Int): Double {
        return when (index) {
            0 -> input.marketPrice / 10000.0
            1 -> input.volume / 1000000.0
            2 -> input.volatility
            3 -> input.rsi / 100.0
            4 -> input.macd
            5 -> input.trend
            else -> 0.0
        }
    }
    
    private fun initializeWeights(): ModelWeights {
        // Initialize with small random weights
        val weights = List(6) { Random.nextDouble(-0.1, 0.1) }
        return ModelWeights(weights)
    }
    
    private fun saveLocalModelWeights(weights: ModelWeights) {
        // Save to local storage (implementation depends on platform)
        println("Saving local model weights: ${weights.version}")
    }
    
    private fun prepareForAggregation(weights: ModelWeights) {
        // Queue for DFLP aggregation
        DFLPAggregationService().queueForAggregation(weights)
    }
    
    fun getLocalModelWeights(): ModelWeights {
        return localModelWeights
    }
}

// Note: DFLPConfig data class has been replaced by centralized DFLPConfiguration object
// in DFLPConfig.kt - provides 6-hour epochs, manual sync, and comprehensive settings
