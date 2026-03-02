package com.miwealth.sovereignvantage.core.ml

/**
 * Integration Example for Ensemble Disagreement Detection
 * 
 * Shows how to integrate disagreement detection into trading flow
 * for 15-25% drawdown reduction
 * 
 * V5.17.0
 * @author MiWealth Pty Ltd
 */
object EnsembleDisagreementExample {
    
    /**
     * Example 1: Basic Disagreement Detection
     */
    fun basicUsage() {
        println("=== BASIC DISAGREEMENT DETECTION ===\n")
        
        val detector = EnsembleDisagreementDetector()
        
        // Scenario: All models agree (bullish)
        println("Scenario 1: Strong Agreement (all bullish)")
        val analysis1 = detector.analyzeFromBoard(
            trendFollower = 0.75,      // Bullish
            momentumTrader = 0.80,     // Bullish
            sentimentAnalyst = 0.70,   // Bullish
            technicalAnalyst = 0.78    // Bullish
        )
        
        println("  Level: ${analysis1.level.name}")
        println("  Score: ${"%.2f".format(analysis1.disagreementScore)}")
        println("  Position multiplier: ${analysis1.level.positionSizeMultiplier}x")
        println("  ${analysis1.explanation}")
        println()
        
        // Scenario: Moderate disagreement
        println("Scenario 2: Moderate Disagreement (mixed signals)")
        val analysis2 = detector.analyzeFromBoard(
            trendFollower = 0.60,      // Bullish
            momentumTrader = -0.40,    // Bearish (conflict!)
            sentimentAnalyst = 0.20,   // Slightly bullish
            technicalAnalyst = 0.50    // Bullish
        )
        
        println("  Level: ${analysis2.level.name}")
        println("  Score: ${"%.2f".format(analysis2.disagreementScore)}")
        println("  Position multiplier: ${analysis2.level.positionSizeMultiplier}x")
        println("  ${analysis2.explanation}")
        println()
        
        // Scenario: Extreme disagreement
        println("Scenario 3: Extreme Disagreement (total chaos)")
        val analysis3 = detector.analyzeFromBoard(
            trendFollower = 0.85,      // Strongly bullish
            momentumTrader = -0.90,    // Strongly bearish (CONFLICT!)
            sentimentAnalyst = 0.10,   // Neutral
            technicalAnalyst = -0.50   // Bearish
        )
        
        println("  Level: ${analysis3.level.name}")
        println("  Score: ${"%.2f".format(analysis3.disagreementScore)}")
        println("  Position multiplier: ${analysis3.level.positionSizeMultiplier}x")
        println("  ${analysis3.explanation}")
        println()
    }
    
    /**
     * Example 2: Position Size Adjustment
     */
    fun positionSizeAdjustment() {
        println("=== POSITION SIZE ADJUSTMENT ===\n")
        
        val detector = EnsembleDisagreementDetector()
        
        // Kelly recommends 5% position
        val kellyPosition = 0.05
        
        println("Base Kelly position: ${kellyPosition * 100}%\n")
        
        // Test different disagreement levels
        data class Scenario(val name: String, val trend: Double, val momentum: Double, val sentiment: Double, val technical: Double)
        val scenarios = listOf(
            Scenario("Strong Agreement", 0.75, 0.78, 0.80, 0.76),
            Scenario("Mild Disagreement", 0.60, 0.75, 0.50, 0.65),
            Scenario("Moderate Disagreement", 0.70, -0.30, 0.40, 0.50),
            Scenario("High Disagreement", 0.80, -0.60, 0.20, -0.40),
            Scenario("Extreme Disagreement", 0.90, -0.85, 0.10, -0.70)
        )
        
        scenarios.forEach { (name, trend, momentum, sentiment, technical) ->
            val analysis = detector.analyzeFromBoard(trend, momentum, sentiment, technical)
            val adjustedPosition = detector.adjustPositionSize(kellyPosition, analysis)
            
            println("$name:")
            println("  Disagreement: ${"%.2f".format(analysis.disagreementScore)}")
            println("  Adjusted position: ${adjustedPosition * 100}% (${analysis.level.positionSizeMultiplier}x)")
            println("  Capital saved: ${(kellyPosition - adjustedPosition) * 100}%")
            println()
        }
    }
    
    /**
     * Example 3: Trade Filtering
     */
    fun tradeFiltering() {
        println("=== TRADE FILTERING BY CONFIDENCE ===\n")
        
        val detector = EnsembleDisagreementDetector()
        
        // AI Board confidence levels
        val confidenceLevels = listOf(0.55, 0.65, 0.75, 0.85)
        
        println("Testing if trades should be taken...\n")
        
        // High disagreement scenario
        val highDisagreement = detector.analyzeFromBoard(
            trendFollower = 0.70,
            momentumTrader = -0.50,
            sentimentAnalyst = 0.20,
            technicalAnalyst = -0.30
        )
        
        println("High Disagreement (requires ${highDisagreement.level.minConfidenceRequired * 100}% confidence):")
        confidenceLevels.forEach { confidence ->
            val shouldTrade = detector.shouldTakeTrade(confidence, highDisagreement)
            val verdict = if (shouldTrade) "✓ TAKE TRADE" else "✗ SKIP (too uncertain)"
            println("  Confidence ${confidence * 100}%: $verdict")
        }
        println()
        
        // Strong agreement scenario
        val strongAgreement = detector.analyzeFromBoard(
            trendFollower = 0.75,
            momentumTrader = 0.78,
            sentimentAnalyst = 0.72,
            technicalAnalyst = 0.80
        )
        
        println("Strong Agreement (requires ${strongAgreement.level.minConfidenceRequired * 100}% confidence):")
        confidenceLevels.forEach { confidence ->
            val shouldTrade = detector.shouldTakeTrade(confidence, strongAgreement)
            val verdict = if (shouldTrade) "✓ TAKE TRADE" else "✗ SKIP (too uncertain)"
            println("  Confidence ${confidence * 100}%: $verdict")
        }
        println()
    }
    
    /**
     * Example 4: Integration with Trading Flow
     */
    fun fullTradingIntegration() {
        println("=== FULL TRADING INTEGRATION ===\n")
        
        val detector = EnsembleDisagreementDetector()
        
        // Simulated AI Board sentiments
        val trendSentiment = 0.65
        val momentumSentiment = -0.25  // Conflict!
        val sentimentSentiment = 0.40
        val technicalSentiment = 0.50
        
        // Simulated Kelly position size
        val kellyPosition = 0.06  // 6% recommended
        
        // Simulated AI Board confidence
        val boardConfidence = 0.68  // 68%
        
        println("Trading Decision Flow:")
        println("---------------------")
        println("1. AI Board Sentiments:")
        println("   TrendFollower: ${trendSentiment}")
        println("   MomentumTrader: ${momentumSentiment}")
        println("   SentimentAnalyst: ${sentimentSentiment}")
        println("   TechnicalAnalyst: ${technicalSentiment}")
        println()
        
        println("2. Kelly Position Sizing:")
        println("   Recommended: ${kellyPosition * 100}%")
        println()
        
        println("3. Disagreement Detection:")
        val analysis = detector.analyzeFromBoard(
            trendFollower = trendSentiment,
            momentumTrader = momentumSentiment,
            sentimentAnalyst = sentimentSentiment,
            technicalAnalyst = technicalSentiment
        )
        println("   ${analysis.explanation}")
        println("   Level: ${analysis.level.name}")
        println()
        
        println("4. Position Size Adjustment:")
        val adjustedPosition = detector.adjustPositionSize(kellyPosition, analysis)
        println("   Base: ${kellyPosition * 100}%")
        println("   Adjusted: ${adjustedPosition * 100}%")
        println("   Reduction: ${((kellyPosition - adjustedPosition) / kellyPosition * 100).toInt()}%")
        println()
        
        println("5. Confidence Check:")
        println("   Board Confidence: ${boardConfidence * 100}%")
        println("   Required: ${analysis.level.minConfidenceRequired * 100}%")
        val shouldTrade = detector.shouldTakeTrade(boardConfidence, analysis)
        println("   Decision: ${if (shouldTrade) "✓ PROCEED WITH TRADE" else "✗ SKIP TRADE (insufficient confidence)"}")
        println()
        
        if (shouldTrade) {
            println("6. FINAL TRADE EXECUTION:")
            println("   Position Size: ${adjustedPosition * 100}%")
            println("   Risk Reduced: ${((kellyPosition - adjustedPosition) / kellyPosition * 100).toInt()}% due to disagreement")
            println("   Trade Type: Conservative (disagreement detected)")
        }
    }
    
    /**
     * Example 5: Trend Detection
     */
    fun trendDetection() {
        println("=== DISAGREEMENT TREND DETECTION ===\n")
        
        val detector = EnsembleDisagreementDetector()
        
        println("Simulating 20 trading periods...\n")
        
        // Simulate market transitioning from agreement to disagreement
        repeat(20) { i ->
            // Gradually increase disagreement (simulating regime change)
            val disagreementLevel = i / 20.0
            
            val trendSentiment = 0.70
            val momentumSentiment = 0.70 - (disagreementLevel * 1.5)  // Diverges over time
            val sentimentSentiment = 0.65 - (disagreementLevel * 0.5)
            val technicalSentiment = 0.68 - (disagreementLevel * 1.0)
            
            val analysis = detector.analyzeFromBoard(
                trendFollower = trendSentiment,
                momentumTrader = momentumSentiment,
                sentimentAnalyst = sentimentSentiment,
                technicalAnalyst = technicalSentiment
            )
            
            // Track over time
            detector.trackDisagreement(analysis)
            
            if (i >= 10) {  // Start checking trend after 10 periods
                val trend = detector.getDisagreementTrend()
                val increasing = detector.isDisagreementIncreasing()
                
                if (increasing && i == 15) {
                    println("⚠ Period $i: REGIME CHANGE DETECTED!")
                    println("   Trend: $trend")
                    println("   Recommendation: Reduce all positions by 50%")
                    println()
                }
            }
        }
        
        println("Final trend: ${detector.getDisagreementTrend()}")
    }
}

// Uncomment to run examples:
// fun main() {
//     EnsembleDisagreementExample.basicUsage()
//     EnsembleDisagreementExample.positionSizeAdjustment()
//     EnsembleDisagreementExample.tradeFiltering()
//     EnsembleDisagreementExample.fullTradingIntegration()
//     EnsembleDisagreementExample.trendDetection()
// }
