package com.miwealth.sovereignvantage.core.ml

/**
 * Example usage and testing for DQN Pretraining
 * 
 * Shows how to pretrain DQN on 2025 backtest data before live trading
 * 
 * V5.5.85
 * @author MiWealth Pty Ltd
 */
object DQNPretrainingExample {
    
    /**
     * Example 1: Basic Pretraining
     * Load and train DQN on 2025 backtest patterns
     */
    fun basicPretraining() {
        println("=== BASIC DQN PRETRAINING ===\n")
        
        // Create new DQN (30 inputs, 2-layer architecture)
        val dqn = DQNTrader(
            stateSize = 30,
            actionSize = 5,
            learningRate = 0.001,
            explorationRate = 0.1  // Lower exploration since we're pretraining
        )
        
        // Pretrain on 2025 backtest data
        println("Loading 2025 backtest patterns...")
        val stats = DQNPretrainer.pretrain(
            dqn = dqn,
            epochs = 10,        // 10 passes over data
            batchSize = 32
        )
        
        // Print results
        println(stats.toSummary())
        
        // DQN is now ready for live trading with learned baseline!
    }
    
    /**
     * Example 2: Custom Pretraining with More Epochs
     * For maximum learning before deployment
     */
    fun intensivePretraining() {
        println("=== INTENSIVE DQN PRETRAINING ===\n")
        
        val dqn = DQNTrader()
        
        // Generate pretraining data
        val experiences = DQNPretrainer.generatePretrainingData()
        println("Generated ${experiences.size} pretraining experiences")
        println("Pattern breakdown:")
        experiences.groupBy { it.pattern }.forEach { (pattern, exps) ->
            println("  - $pattern: ${exps.size} occurrences")
        }
        println()
        
        // Train with more epochs for better learning
        val stats = DQNPretrainer.pretrain(
            dqn = dqn,
            experiences = experiences,
            epochs = 20,        // More iterations
            batchSize = 16      // Smaller batches for finer updates
        )
        
        println(stats.toSummary())
    }
    
    /**
     * Example 3: Verify Learned Patterns
     * Test that DQN recognizes profitable vs unprofitable setups
     */
    fun verifyLearning() {
        println("=== VERIFY DQN LEARNING ===\n")
        
        // Pretrain
        val dqn = DQNTrader()
        DQNPretrainer.pretrain(dqn)
        
        // Test Case 1: Strong Breakout (should be bullish)
        println("Test 1: Strong Trend Breakout")
        val breakoutState = MarketState(
            priceLevel = 50,
            trendDirection = 1,     // Bullish
            volatilityLevel = 2,     // High vol
            volumeLevel = 2,         // High volume
            rsiLevel = 1,            // Neutral RSI
            positionSize = 0
        )
        val breakoutSentiment = dqn.getLearnedSentiment(breakoutState)
        val breakoutConfidence = dqn.getDecisionConfidence(breakoutState)
        println("  Learned sentiment: ${"%.2f".format(breakoutSentiment)} (expect positive)")
        println("  Confidence: ${"%.1f".format(breakoutConfidence * 100)}%")
        println("  ✓ ${if (breakoutSentiment > 0.3) "CORRECT - Bullish!" else "NEEDS MORE TRAINING"}")
        println()
        
        // Test Case 2: False Breakout (should be bearish or neutral)
        println("Test 2: False Breakout (low volume)")
        val falseBreakoutState = MarketState(
            priceLevel = 50,
            trendDirection = 1,      // Looks bullish but...
            volatilityLevel = 0,      // Low vol (weak!)
            volumeLevel = 0,          // LOW volume (key!)
            rsiLevel = 2,             // Overbought
            positionSize = 0
        )
        val falseBreakoutSentiment = dqn.getLearnedSentiment(falseBreakoutState)
        val falseBreakoutConfidence = dqn.getDecisionConfidence(falseBreakoutState)
        println("  Learned sentiment: ${"%.2f".format(falseBreakoutSentiment)} (expect negative/neutral)")
        println("  Confidence: ${"%.1f".format(falseBreakoutConfidence * 100)}%")
        println("  ✓ ${if (falseBreakoutSentiment < 0.2) "CORRECT - Avoiding false breakout!" else "LEARNING..."}")
        println()
        
        // Test Case 3: Oversold Bounce (should be bullish)
        println("Test 3: Oversold Bounce")
        val oversoldState = MarketState(
            priceLevel = 45,
            trendDirection = 0,      // Neutral trend
            volatilityLevel = 1,      // Medium vol
            volumeLevel = 1,          // Medium volume
            rsiLevel = 0,             // Oversold (key!)
            positionSize = 0
        )
        val oversoldSentiment = dqn.getLearnedSentiment(oversoldState)
        val oversoldConfidence = dqn.getDecisionConfidence(oversoldState)
        println("  Learned sentiment: ${"%.2f".format(oversoldSentiment)} (expect positive)")
        println("  Confidence: ${"%.1f".format(oversoldConfidence * 100)}%")
        println("  ✓ ${if (oversoldSentiment > 0.2) "CORRECT - Mean reversion!" else "LEARNING..."}")
        println()
        
        // Test Case 4: Choppy Market (should prefer HOLD)
        println("Test 4: Choppy/Ranging Market")
        val choppyState = MarketState(
            priceLevel = 48,
            trendDirection = 0,       // No trend
            volatilityLevel = 0,       // Low vol
            volumeLevel = 0,           // Low volume
            rsiLevel = 1,              // Neutral RSI
            positionSize = 0
        )
        val choppySentiment = dqn.getLearnedSentiment(choppyState)
        val choppyConfidence = dqn.getDecisionConfidence(choppyState)
        println("  Learned sentiment: ${"%.2f".format(choppySentiment)} (expect near zero)")
        println("  Confidence: ${"%.1f".format(choppyConfidence * 100)}%")
        println("  ✓ ${if (kotlin.math.abs(choppySentiment) < 0.3) "CORRECT - Prefer HOLD!" else "LEARNING..."}")
        println()
    }
    
    /**
     * Example 4: Before/After Comparison
     * Show improvement from pretraining
     */
    fun beforeAfterComparison() {
        println("=== BEFORE/AFTER PRETRAINING ===\n")
        
        // BEFORE: Random DQN
        println("BEFORE PRETRAINING (random weights):")
        val randomDqn = DQNTrader()
        val testState = MarketState(
            priceLevel = 50,
            trendDirection = 1,
            volatilityLevel = 2,
            volumeLevel = 2,
            rsiLevel = 1,
            positionSize = 0
        )
        
        val beforeSentiment = randomDqn.getLearnedSentiment(testState)
        val beforeConfidence = randomDqn.getDecisionConfidence(testState)
        val beforeQValues = randomDqn.getQValues(testState)
        
        println("  Sentiment: ${"%.3f".format(beforeSentiment)}")
        println("  Confidence: ${"%.1f".format(beforeConfidence * 100)}%")
        println("  Q-values: ${beforeQValues.map { "${it.key.name}=${"%.2f".format(it.value)}" }}")
        println()
        
        // AFTER: Pretrained DQN
        println("AFTER PRETRAINING (learned from 2025 data):")
        val pretrainedDqn = DQNTrader()
        DQNPretrainer.pretrain(pretrainedDqn, epochs = 15)
        
        val afterSentiment = pretrainedDqn.getLearnedSentiment(testState)
        val afterConfidence = pretrainedDqn.getDecisionConfidence(testState)
        val afterQValues = pretrainedDqn.getQValues(testState)
        
        println("  Sentiment: ${"%.3f".format(afterSentiment)}")
        println("  Confidence: ${"%.1f".format(afterConfidence * 100)}%")
        println("  Q-values: ${afterQValues.map { "${it.key.name}=${"%.2f".format(it.value)}" }}")
        println()
        
        println("IMPROVEMENT:")
        println("  Sentiment change: ${"%.3f".format(afterSentiment - beforeSentiment)}")
        println("  Confidence change: ${"%.1f".format((afterConfidence - beforeConfidence) * 100)}%")
        println("  ${if (afterConfidence > beforeConfidence) "✓ DQN is more confident after pretraining!" else "Note: May need more epochs"}")
    }
}

// Uncomment to run examples:
// fun main() {
//     DQNPretrainingExample.basicPretraining()
//     DQNPretrainingExample.verifyLearning()
//     DQNPretrainingExample.beforeAfterComparison()
// }
