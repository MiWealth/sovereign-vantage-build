package com.miwealth.sovereignvantage.core.trading

import com.miwealth.sovereignvantage.core.AssetType
import com.miwealth.sovereignvantage.core.ai.MarketContext

/**
 * AI Board STAHL Usage Examples & Test Scenarios
 * 
 * This file demonstrates how to use the AIBoardStahlSelector and AIBoardStairExpander
 * in real trading scenarios.
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage™: Arthur Edition
 */

/**
 * Example 1: Entry-Time Preset Selection
 * 
 * Scenario: BTC/USD showing strong uptrend with building momentum
 */
fun examplePresetSelection() {
    // Build market context from indicator values
    val indicators = IndicatorSnapshot(
        atr = 1500.0,              // $1,500 ATR
        atrPercent = 1.5,          // 1.5% of price
        atrRatio = 1.2,            // Slightly above average
        adx = 32.0,                // Strong trend
        rsi = 62.0,                // Bullish but not overbought
        rsiChange = 3.0,           // RSI rising
        macdHistogram = 150.0,     // Positive histogram
        macdHistogramChange = 25.0, // Histogram growing
        roc = 3.5,                 // 3.5% rate of change
        priceVs20MA = 2.5,         // 2.5% above 20 MA
        priceVs50MA = 5.0,         // 5% above 50 MA
        maSlope = 0.3,             // MA trending up
        volumeRatio = 1.8,         // Elevated volume
        bollingerWidth = 4.5,      // Normal BB width
        currentPrice = 100000.0
    )
    
    val context = StahlMarketContext.build(
        indicators = indicators,
        hourUTC = 15,              // EU/US overlap
        assetType = AssetType.CRYPTO
    )
    
    // Create selector with default constraints (user accepts all presets)
    val selector = AIBoardStahlSelector()
    
    // Get recommendation
    val recommendation = selector.recommendPreset(context)
    
    println("=== PRESET SELECTION EXAMPLE ===")
    println("Market: ${recommendation.marketConditionsSummary}")
    println("Recommended: ${recommendation.preset}")
    println("Confidence: ${(recommendation.confidence * 100).toInt()}%")
    println("Reasoning: ${recommendation.reasoning}")
    println()
    
    // Expected: AGGRESSIVE selected due to strong trend + building momentum
}

/**
 * Example 2: Preset Selection with User Constraints
 * 
 * Scenario: Same market conditions, but user only allows CONSERVATIVE and MODERATE
 */
fun exampleConstrainedSelection() {
    val indicators = IndicatorSnapshot(
        atr = 1500.0, atrPercent = 1.5, atrRatio = 1.2,
        adx = 32.0, rsi = 62.0, rsiChange = 3.0,
        macdHistogram = 150.0, macdHistogramChange = 25.0,
        roc = 3.5, priceVs20MA = 2.5, priceVs50MA = 5.0,
        maSlope = 0.3, volumeRatio = 1.8, bollingerWidth = 4.5,
        currentPrice = 100000.0
    )
    
    val context = StahlMarketContext.build(indicators, hourUTC = 15)
    
    // User only allows conservative presets
    val constraints = UserTradeConstraints(
        allowedPresets = setOf(StahlPreset.CONSERVATIVE, StahlPreset.MODERATE),
        preferredPreset = StahlPreset.MODERATE
    )
    
    val selector = AIBoardStahlSelector(constraints)
    val recommendation = selector.recommendPreset(context)
    
    println("=== CONSTRAINED SELECTION EXAMPLE ===")
    println("User allows: ${constraints.allowedPresets}")
    println("AI wanted: ${recommendation.originalRecommendation ?: recommendation.preset}")
    println("Final selection: ${recommendation.preset}")
    println("Was filtered: ${recommendation.wasFiltered}")
    println("Reasoning: ${recommendation.reasoning}")
    println()
    
    // Expected: MODERATE (filtered from AGGRESSIVE)
}

/**
 * Example 3: Mid-Trade Stair Expansion - Borrow Scenario
 * 
 * Scenario: MODERATE position at top stair (200% profit), momentum still building
 */
fun exampleStairExpansionBorrow() {
    // Create a position that's reached its top stair
    val position = StahlPosition.create(
        id = 1L,
        symbol = "BTC/USD",
        entryPrice = 50000.0,
        direction = TradeDirection.LONG,
        size = 0.1,
        positionValue = 5000.0,
        leverage = 1.0,
        preset = StahlPreset.MODERATE
    )
    
    // Simulate the position reaching 200%+ profit
    // (In reality, this would happen through update() calls)
    
    // Current market context shows continued strength
    val indicators = IndicatorSnapshot(
        atr = 2000.0, atrPercent = 1.3, atrRatio = 1.0,
        adx = 35.0, rsi = 65.0, rsiChange = 2.0,
        macdHistogram = 200.0, macdHistogramChange = 30.0,
        roc = 4.0, priceVs20MA = 3.0, priceVs50MA = 8.0,
        maSlope = 0.4, volumeRatio = 1.5, bollingerWidth = 5.0,
        currentPrice = 150000.0,  // Price has tripled!
        barsAtTopStair = 5        // Been at top stair for 5 bars
    )
    
    val context = StahlMarketContext.build(indicators, hourUTC = 14)
    
    val expander = AIBoardStairExpander()
    val result = expander.evaluateExpansion(position, context)
    
    println("=== STAIR EXPANSION (BORROW) EXAMPLE ===")
    println("Position: ${position.symbol} ${position.direction}")
    println("Current profit: ~200%")
    println("Decision: ${result.decision}")
    println("New stairs added: ${result.newStairs.size}")
    println("Confidence: ${(result.confidence * 100).toInt()}%")
    println("Reasoning: ${result.reasoning}")
    
    if (result.newStairs.isNotEmpty()) {
        println("\nNew stair levels:")
        result.newStairs.forEachIndexed { i, stair ->
            println("  ${i + 1}. At ${stair.profitPercent}% → lock in ${stair.calculateLockedProfit()}%")
        }
    }
    println()
}

/**
 * Example 4: Mid-Trade Stair Expansion - Extrapolate Scenario
 * 
 * Scenario: SCALPING position in extremely volatile conditions
 *           AGGRESSIVE stairs don't fit, need custom spacing
 */
fun exampleStairExpansionExtrapolate() {
    val position = StahlPosition.create(
        id = 2L,
        symbol = "DOGE/USD",
        entryPrice = 0.10,
        direction = TradeDirection.LONG,
        size = 10000.0,
        positionValue = 1000.0,
        leverage = 3.0,
        preset = StahlPreset.SCALPING
    )
    
    // Extremely volatile market - standard AGGRESSIVE spacing won't work
    val indicators = IndicatorSnapshot(
        atr = 0.015,              // 15% ATR!
        atrPercent = 15.0,
        atrRatio = 3.5,           // Extreme volatility
        adx = 45.0,               // Very strong trend
        rsi = 72.0,
        rsiChange = 5.0,
        macdHistogram = 0.008,
        macdHistogramChange = 0.002,
        roc = 25.0,               // Massive momentum
        priceVs20MA = 20.0,
        priceVs50MA = 35.0,
        maSlope = 2.5,
        volumeRatio = 4.5,        // Climactic volume
        bollingerWidth = 25.0,
        currentPrice = 0.15,      // 50% up already
        barsAtTopStair = 4
    )
    
    val context = StahlMarketContext.build(indicators, hourUTC = 16, assetType = AssetType.CRYPTO)
    
    val expander = AIBoardStairExpander()
    val result = expander.evaluateExpansion(position, context)
    
    println("=== STAIR EXPANSION (EXTRAPOLATE) EXAMPLE ===")
    println("Position: ${position.symbol} ${position.direction}")
    println("Volatility: EXTREME (${indicators.atrPercent}% ATR)")
    println("Decision: ${result.decision}")
    println("Reasoning: ${result.reasoning}")
    
    if (result.newStairs.isNotEmpty()) {
        println("\nExtrapolated stairs (volatility-adapted spacing):")
        result.newStairs.forEachIndexed { i, stair ->
            println("  ${i + 1}. At ${stair.profitPercent}% → lock in ${stair.calculateLockedProfit()}%")
        }
    }
    println()
}

/**
 * Example 5: Full Trade Lifecycle with AI Board Integration
 */
fun exampleFullTradeLifecycle() {
    println("=== FULL TRADE LIFECYCLE ===")
    println()
    
    // Step 1: Market analysis and preset selection
    val entryIndicators = IndicatorSnapshot(
        atr = 800.0, atrPercent = 1.2, atrRatio = 1.1,
        adx = 28.0, rsi = 55.0, rsiChange = 2.0,
        macdHistogram = 50.0, macdHistogramChange = 15.0,
        roc = 2.0, priceVs20MA = 1.5, priceVs50MA = 3.0,
        maSlope = 0.2, volumeRatio = 1.3, bollingerWidth = 3.5,
        currentPrice = 65000.0
    )
    
    val entryContext = StahlMarketContext.build(entryIndicators, hourUTC = 10)
    
    val (selector, expander) = AIBoardStahlFactory.create()
    val recommendation = selector.recommendPreset(entryContext)
    
    println("1. ENTRY ANALYSIS")
    println("   Market: ${entryContext.trend} trend, ${entryContext.momentum} momentum")
    println("   Recommended preset: ${recommendation.preset}")
    println("   Confidence: ${(recommendation.confidence * 100).toInt()}%")
    println()
    
    // Step 2: Create position with recommended preset
    val position = StahlPosition.create(
        id = 100L,
        symbol = "BTC/USD",
        entryPrice = 65000.0,
        direction = TradeDirection.LONG,
        size = 0.1,
        positionValue = 6500.0,
        leverage = 1.0,
        preset = recommendation.preset
    )
    
    // Wrap in expanded position tracker
    val expandedPosition = AIBoardStahlFactory.createExpandedPosition(position)
    
    println("2. POSITION OPENED")
    println("   Entry: $${position.entryPrice}")
    println("   Initial stop: $${position.initialStop} (3.5%)")
    println("   Preset: ${position.preset} (${expandedPosition.originalConfig.levelCount} stairs)")
    println()
    
    // Step 3: Simulate price movement - trade is winning big
    println("3. TRADE PROGRESSES...")
    println("   [Simulating price movement to top stair]")
    println()
    
    // Step 4: At top stair - evaluate expansion
    val expansionIndicators = IndicatorSnapshot(
        atr = 1200.0, atrPercent = 0.8, atrRatio = 0.9,
        adx = 38.0, rsi = 68.0, rsiChange = 1.5,
        macdHistogram = 180.0, macdHistogramChange = 20.0,
        roc = 5.0, priceVs20MA = 4.0, priceVs50MA = 12.0,
        maSlope = 0.5, volumeRatio = 1.6, bollingerWidth = 4.0,
        currentPrice = 150000.0,  // 130% profit!
        barsAtTopStair = 4
    )
    
    val expansionContext = StahlMarketContext.build(expansionIndicators, hourUTC = 15)
    val expansionResult = expander.evaluateExpansion(position, expansionContext)
    
    println("4. EXPANSION EVALUATION (at top stair)")
    println("   Current profit: ~130%")
    println("   Momentum: ${expansionContext.momentum}")
    println("   Decision: ${expansionResult.decision}")
    
    if (expansionResult.decision != ExpansionDecision.HOLD) {
        expandedPosition.applyExpansion(expansionResult)
        println("   Stairs added: ${expansionResult.newStairs.size}")
        println("   Total stairs now: ${expandedPosition.totalStairCount}")
        println("   New top target: ${expandedPosition.allStairs.lastOrNull()?.profitPercent}%")
    }
    println()
    
    // Step 5: Calculate current stop with expanded stairs
    val currentMaxProfit = 135.0  // Peaked at 135%
    val stopResult = expandedPosition.calculateExpandedStop(currentMaxProfit)
    
    println("5. CURRENT POSITION STATUS")
    println("   Max profit reached: $currentMaxProfit%")
    println("   Current stair level: ${stopResult.currentLevel}")
    println("   Profit locked in: ${stopResult.lockedInPercent}%")
    println("   Stop price: $${String.format("%.2f", stopResult.stopPrice)}")
    println("   Next level at: ${stopResult.nextLevelProfitPercent}%")
    println()
    
    println("=== LIFECYCLE COMPLETE ===")
}

/**
 * Example 6: Different Market Scenarios
 */
fun exampleMarketScenarios() {
    val selector = AIBoardStahlSelector()
    
    println("=== MARKET SCENARIO COMPARISON ===")
    println()
    
    // Scenario A: Ranging market, low volatility
    val rangingMarket = IndicatorSnapshot(
        atr = 500.0, atrPercent = 0.8, atrRatio = 0.4,
        adx = 15.0, rsi = 50.0, rsiChange = 0.0,
        macdHistogram = 5.0, macdHistogramChange = -2.0,
        roc = 0.2, priceVs20MA = 0.1, priceVs50MA = -0.2,
        maSlope = 0.0, volumeRatio = 0.7, bollingerWidth = 2.0,
        currentPrice = 62000.0
    )
    val rangingContext = StahlMarketContext.build(rangingMarket, hourUTC = 3)
    val rangingRec = selector.recommendPreset(rangingContext)
    
    println("SCENARIO A: Ranging/Low Volatility")
    println("  Trend: ${rangingContext.trend}, Momentum: ${rangingContext.momentum}")
    println("  Recommended: ${rangingRec.preset}")
    println()
    
    // Scenario B: Strong trend, high volatility (news event)
    val newsMarket = IndicatorSnapshot(
        atr = 3000.0, atrPercent = 4.5, atrRatio = 2.8,
        adx = 42.0, rsi = 75.0, rsiChange = 8.0,
        macdHistogram = 500.0, macdHistogramChange = 100.0,
        roc = 12.0, priceVs20MA = 8.0, priceVs50MA = 15.0,
        maSlope = 1.5, volumeRatio = 3.5, bollingerWidth = 12.0,
        currentPrice = 68000.0
    )
    val newsContext = StahlMarketContext.build(newsMarket, hourUTC = 14)
    val newsRec = selector.recommendPreset(newsContext)
    
    println("SCENARIO B: News Event/High Volatility")
    println("  Trend: ${newsContext.trend}, Momentum: ${newsContext.momentum}")
    println("  Recommended: ${newsRec.preset}")
    println()
    
    // Scenario C: Breakdown starting
    val breakdownMarket = IndicatorSnapshot(
        atr = 1200.0, atrPercent = 2.0, atrRatio = 1.5,
        adx = 30.0, rsi = 35.0, rsiChange = -5.0,
        macdHistogram = -120.0, macdHistogramChange = -40.0,
        roc = -4.0, priceVs20MA = -3.0, priceVs50MA = -1.0,
        maSlope = -0.3, volumeRatio = 2.0, bollingerWidth = 5.0,
        currentPrice = 58000.0
    )
    val breakdownContext = StahlMarketContext.build(breakdownMarket, hourUTC = 17)
    val breakdownRec = selector.recommendPreset(breakdownContext)
    
    println("SCENARIO C: Breakdown Starting")
    println("  Trend: ${breakdownContext.trend}, Momentum: ${breakdownContext.momentum}")
    println("  Recommended: ${breakdownRec.preset}")
    println()
    
    // Scenario D: Compression before breakout
    val compressionMarket = IndicatorSnapshot(
        atr = 200.0, atrPercent = 0.3, atrRatio = 0.25,
        adx = 12.0, rsi = 52.0, rsiChange = 1.0,
        macdHistogram = 10.0, macdHistogramChange = 5.0,
        roc = 0.5, priceVs20MA = 0.3, priceVs50MA = 0.5,
        maSlope = 0.05, volumeRatio = 0.4, bollingerWidth = 1.2,
        currentPrice = 64500.0
    )
    val compressionContext = StahlMarketContext.build(compressionMarket, hourUTC = 13)
    val compressionRec = selector.recommendPreset(compressionContext)
    
    println("SCENARIO D: Compression (Pre-Breakout)")
    println("  Trend: ${compressionContext.trend}, Momentum: ${compressionContext.momentum}")
    println("  Recommended: ${compressionRec.preset}")
    println()
}

/**
 * Run all examples
 */
fun main() {
    examplePresetSelection()
    exampleConstrainedSelection()
    exampleStairExpansionBorrow()
    exampleStairExpansionExtrapolate()
    exampleFullTradeLifecycle()
    exampleMarketScenarios()
}
