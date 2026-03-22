package com.miwealth.sovereignvantage.core.ai

import com.miwealth.sovereignvantage.core.indicators.*
import kotlin.math.abs

/**
 * HEDGE FUND BOARD MEMBERS - V5.17.0 "Arthur Edition"
 * 
 * This file contains the 7 hedge fund specialist and crossover member classes
 * that were extracted from AIBoardOrchestrator.kt during the board separation
 * refactor (v5.5.62).
 * 
 * These classes are used by:
 * - HedgeFundBoardOrchestrator.kt (hedge fund trading - UNWIRED)
 * - ConfigurableBoardOrchestrator.kt (any member configuration)
 * - TestBoardOrchestrator.kt (backtesting)
 * - BoardMemberFactory (creates instances for any board)
 * 
 * Member Categories:
 * ┌─────────────┬─────────────────────────────────────────────┬────────────┐
 * │ Category    │ Members                                     │ Count      │
 * ├─────────────┼─────────────────────────────────────────────┼────────────┤
 * │ HEDGE_FUND  │ Soros, Guardian, Draper, Atlas, Theta       │ 5          │
 * │ CROSSOVER   │ Moby, Echo                                  │ 2          │
 * └─────────────┴─────────────────────────────────────────────┴────────────┘
 * 
 * Crossover Note:
 * Moby (WhaleTracker) and Echo (OrderBookImbalanceAnalyst) are marked as
 * CROSSOVER members - they may be considered for inclusion in the general
 * Octagon trading engine in future versions.
 * 
 * Version History:
 * - v5.5.51: Soros, Draper, Moby added (Hendecagon)
 * - v5.5.54: Guardian added (Dodecagon)
 * - v5.5.55: Atlas added (Tridecagon)
 * - v5.5.56: Echo added (Tetradecagon)
 * - v5.5.57: Theta added (Pentadecagon - 15 member cap)
 * - v5.5.60: Octagon restored, hedge fund members isolated
 * - v5.5.62: Classes extracted to this file (current)
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 */

// ============================================================================
// HEDGE FUND SPECIALIST: SOROS (Global Macro Analyst)
// ============================================================================

/**
 * 9. Soros (Chief Economist) - Global Macro Analyst
 * 
 * Analyses central bank policy, macro sentiment, and geopolitical risk.
 * Named after George Soros - the quintessential macro trader.
 * 
 * Uses real macro data from MacroSentimentAnalyzer when available,
 * falls back to price-based proxies when macro data unavailable.
 */
class GlobalMacroAnalyst : BoardMember {
    override val name = "GlobalMacroAnalyst"
    override val displayName = "Soros"
    override val role = "Chief Economist"
    override val weight = 0.143  // Default for 7-member hedge fund board
    
    override fun analyze(context: MarketContext): AgentOpinion {
        var bullishScore = 0
        var bearishScore = 0
        val indicators = mutableListOf<String>()
        
        // === PRIMARY: Real Macro Data from MacroSentimentAnalyzer ===
        val hasMacroData = context.macroSentiment != null && context.macroScore != null
        
        if (hasMacroData) {
            val sentiment = context.macroSentiment!!
            val score = context.macroScore!!
            
            // Central bank policy stance
            when {
                sentiment == "HAWKISH" && score > 0.5 -> {
                    bearishScore += 4
                    indicators.add("Strong Hawkish stance - tightening imminent")
                }
                sentiment == "HAWKISH" && score > 0.2 -> {
                    bearishScore += 2
                    indicators.add("Hawkish bias - rates staying high")
                }
                sentiment == "DOVISH" && score < -0.5 -> {
                    bullishScore += 4
                    indicators.add("Strong Dovish pivot - easing coming")
                }
                sentiment == "DOVISH" && score < -0.2 -> {
                    bullishScore += 2
                    indicators.add("Dovish bias - supportive policy")
                }
                else -> {
                    indicators.add("Neutral monetary policy stance")
                }
            }
            
            // Risk level assessment
            context.macroRiskLevel?.let { risk ->
                when (risk) {
                    "EXTREME" -> {
                        bearishScore += 4
                        indicators.add("EXTREME RISK: Crisis conditions detected")
                    }
                    "HIGH" -> {
                        bearishScore += 2
                        indicators.add("High macro risk - defensive posture")
                    }
                    "ELEVATED" -> {
                        bearishScore += 1
                        indicators.add("Elevated uncertainty")
                    }
                    "LOW" -> {
                        bullishScore += 1
                        indicators.add("Stable macro environment")
                    }
                }
            }
            
            // Event risk
            val events = context.upcomingHighImpactEvents ?: 0
            if (events >= 3) {
                bearishScore += 1
                indicators.add("$events major events in 48h - reduce exposure")
            } else if (events == 0) {
                bullishScore += 1
                indicators.add("Clear calendar - low event risk")
            }
            
            // Add narrative if available
            context.macroNarrative?.let {
                indicators.add("Narrative: $it")
            }
        } else {
            // Fallback: No macro data available - use price-based proxy
            val closes = context.closes
            if (closes.size >= 20) {
                val sma20 = closes.takeLast(20).average()
                val volatility = calculateVolatility(closes.takeLast(20))
                
                // High volatility often precedes macro events
                if (volatility > 0.03) {
                    bearishScore += 1
                    indicators.add("Elevated volatility (macro uncertainty proxy)")
                }
                
                // Trend as risk sentiment proxy
                if (context.currentPrice > sma20 * 1.05) {
                    bullishScore += 1
                    indicators.add("Risk-on environment (price momentum)")
                } else if (context.currentPrice < sma20 * 0.95) {
                    bearishScore += 1
                    indicators.add("Risk-off environment (price weakness)")
                }
            }
            indicators.add("⚠️ No live macro data - using price proxy")
        }
        
        // Calculate final sentiment
        val maxScore = if (hasMacroData) 10.0 else 3.0
        val sentiment = (bullishScore - bearishScore) / maxScore
        val confidence = if (hasMacroData) minOf(abs(sentiment) + 0.3, 1.0) else abs(sentiment) * 0.5
        val vote = sentimentToVote(sentiment)
        
        val reasoning = buildString {
            if (hasMacroData) {
                append("Global macro: ${context.macroSentiment} (score: ${String.format("%.2f", context.macroScore)})")
                if (bullishScore > bearishScore) append(" - Risk assets favored")
                else if (bearishScore > bullishScore) append(" - Defensive positioning advised")
            } else {
                append("Macro data unavailable - limited conviction")
            }
        }
        
        return AgentOpinion(
            agentName = name,
            displayName = displayName,
            role = role,
            vote = vote,
            sentiment = sentiment,
            confidence = confidence,
            reasoning = reasoning,
            keyIndicators = indicators
        )
    }
    
    private fun calculateVolatility(prices: List<Double>): Double {
        if (prices.size < 2) return 0.0
        val returns = prices.zipWithNext { a, b -> (b - a) / a }
        val mean = returns.average()
        val variance = returns.map { (it - mean) * (it - mean) }.average()
        return kotlin.math.sqrt(variance)
    }
}

// ============================================================================
// HEDGE FUND SPECIALIST: DRAPER (DeFi Specialist)
// ============================================================================

/**
 * 10. Draper (Chief DeFi Officer) - DeFi Specialist
 * 
 * Analyses DeFi protocols, yield opportunities, TVL flows, and protocol risks.
 * Named after Tim Draper - visionary crypto VC.
 * 
 * Uses volume/momentum as proxies for DeFi activity when direct protocol
 * data is unavailable.
 */
class DeFiSpecialist : BoardMember {
    override val name = "DeFiSpecialist"
    override val displayName = "Draper"
    override val role = "Chief DeFi Officer"
    override val weight = 0.143  // Default for 7-member hedge fund board
    
    override fun analyze(context: MarketContext): AgentOpinion {
        var bullishScore = 0
        var bearishScore = 0
        val indicators = mutableListOf<String>()
        
        val closes = context.closes
        val volumes = context.volumes
        
        if (closes.size >= 7) {
            // Volume trend (DeFi activity proxy)
            val recentVolAvg = volumes.takeLast(7).average()
            val priorVolAvg = volumes.dropLast(7).takeLast(7).average()
            val volGrowth = if (priorVolAvg > 0) (recentVolAvg - priorVolAvg) / priorVolAvg else 0.0
            
            when {
                volGrowth > 0.5 -> {
                    bullishScore += 2
                    indicators.add("Volume surge +${(volGrowth * 100).toInt()}% (DeFi inflows)")
                }
                volGrowth > 0.2 -> {
                    bullishScore += 1
                    indicators.add("Volume increasing (protocol activity up)")
                }
                volGrowth < -0.3 -> {
                    bearishScore += 2
                    indicators.add("Volume declining (DeFi outflows)")
                }
            }
            
            // Price momentum (TVL proxy - higher prices = higher TVL typically)
            val weekReturn = (closes.last() - closes[closes.size - 7]) / closes[closes.size - 7]
            when {
                weekReturn > 0.15 -> {
                    bullishScore += 2
                    indicators.add("Strong weekly momentum +${(weekReturn * 100).toInt()}%")
                }
                weekReturn > 0.05 -> {
                    bullishScore += 1
                    indicators.add("Positive momentum (TVL likely growing)")
                }
                weekReturn < -0.15 -> {
                    bearishScore += 2
                    indicators.add("Weak momentum (potential TVL exodus)")
                }
                weekReturn < -0.05 -> {
                    bearishScore += 1
                    indicators.add("Negative momentum")
                }
            }
            
            // Volatility check (high vol = potential exploit/depeg risk)
            val returns = closes.takeLast(14).zipWithNext { a, b -> kotlin.math.abs((b - a) / a) }
            val avgVolatility = returns.average()
            if (avgVolatility > 0.08) {
                bearishScore += 1
                indicators.add("High volatility (elevated protocol risk)")
            }
        }
        
        // Yield environment heuristic
        context.macroSentiment?.let { sentiment ->
            if (sentiment == "DOVISH") {
                bullishScore += 1
                indicators.add("Dovish macro = yield-seeking into DeFi")
            } else if (sentiment == "HAWKISH") {
                bearishScore += 1
                indicators.add("Hawkish macro = yield competition from TradFi")
            }
        }
        
        if (indicators.isEmpty()) {
            indicators.add("Insufficient DeFi data - neutral stance")
        }
        
        val sentiment = (bullishScore - bearishScore) / 6.0
        val confidence = minOf(kotlin.math.abs(sentiment) + 0.2, 0.8)
        val vote = sentimentToVote(sentiment)
        
        return AgentOpinion(
            agentName = name,
            displayName = displayName,
            role = role,
            vote = vote,
            sentiment = sentiment,
            confidence = confidence,
            reasoning = if (bullishScore > bearishScore) "DeFi metrics bullish - capital flowing in" 
                       else if (bearishScore > bullishScore) "DeFi metrics bearish - risk-off in protocols"
                       else "DeFi neutral",
            keyIndicators = indicators
        )
    }
}

// ============================================================================
// CROSSOVER MEMBER: MOBY (Whale Tracker)
// ============================================================================

/**
 * 11. Moby (Chief Intelligence Officer) - Whale & Institutional Tracker
 * 
 * Tracks whale wallet movements, exchange flows, and institutional positioning.
 * Named for the great white whale - we follow the big fish.
 * 
 * CROSSOVER: May be considered for Octagon inclusion in future versions.
 * Smart money tracking is valuable for both retail and institutional trading.
 */
class WhaleTracker : BoardMember {
    override val name = "WhaleTracker"
    override val displayName = "Moby"
    override val role = "Chief Intelligence Officer"
    override val weight = 0.143  // Default for hedge fund board
    
    override fun analyze(context: MarketContext): AgentOpinion {
        var bullishScore = 0
        var bearishScore = 0
        val indicators = mutableListOf<String>()
        
        val closes = context.closes
        val volumes = context.volumes
        
        if (closes.size >= 20 && volumes.size >= 20) {
            // Large volume bars often indicate whale activity
            val avgVol = volumes.average()
            val stdVol = kotlin.math.sqrt(volumes.map { (it - avgVol) * (it - avgVol) }.average())
            val recentLargeVolBars = volumes.takeLast(5).count { it > avgVol + 2 * stdVol }
            
            if (recentLargeVolBars >= 2) {
                // Check if large volume was on up or down moves
                val lastPrices = closes.takeLast(5)
                val lastVols = volumes.takeLast(5)
                var upVol = 0.0
                var downVol = 0.0
                
                for (i in 1 until lastPrices.size) {
                    if (lastPrices[i] > lastPrices[i-1]) upVol += lastVols[i]
                    else downVol += lastVols[i]
                }
                
                when {
                    upVol > downVol * 1.5 -> {
                        bullishScore += 3
                        indicators.add("Whale accumulation detected (high vol on up moves)")
                    }
                    downVol > upVol * 1.5 -> {
                        bearishScore += 3
                        indicators.add("Whale distribution detected (high vol on down moves)")
                    }
                    else -> {
                        indicators.add("Mixed whale activity")
                    }
                }
            }
            
            // Price stability at support (accumulation pattern)
            val recent20 = closes.takeLast(20)
            val low20 = recent20.min() ?: context.currentPrice
            val range20 = (recent20.max() ?: context.currentPrice) - low20
            val distFromLow = context.currentPrice - low20
            
            if (range20 > 0) {
                val positionInRange = distFromLow / range20
                
                // Tight range near lows with volume = accumulation
                if (positionInRange < 0.3 && range20 / context.currentPrice < 0.1) {
                    val recentVolTrend = volumes.takeLast(5).average() / volumes.takeLast(20).average()
                    if (recentVolTrend > 1.2) {
                        bullishScore += 2
                        indicators.add("Accumulation pattern: tight range + rising volume")
                    }
                }
                
                // Near highs with declining volume = distribution
                if (positionInRange > 0.8) {
                    val recentVolTrend = volumes.takeLast(5).average() / volumes.takeLast(20).average()
                    if (recentVolTrend < 0.8) {
                        bearishScore += 2
                        indicators.add("Distribution pattern: near highs + falling volume")
                    }
                }
            }
            
            // Exchange flow proxy: sharp moves on high volume
            val lastMove = (closes.last() - closes[closes.size - 2]) / closes[closes.size - 2]
            val lastVolRatio = volumes.last() / avgVol
            
            if (kotlin.math.abs(lastMove) > 0.03 && lastVolRatio > 2.5) {
                if (lastMove > 0) {
                    bullishScore += 1
                    indicators.add("Large buy block executed")
                } else {
                    bearishScore += 1
                    indicators.add("Large sell block executed")
                }
            }
        }
        
        if (indicators.isEmpty()) {
            indicators.add("No significant whale activity detected")
        }
        
        val sentiment = (bullishScore - bearishScore) / 6.0
        val confidence = minOf(kotlin.math.abs(sentiment) + 0.15, 0.85)
        val vote = sentimentToVote(sentiment)
        
        return AgentOpinion(
            agentName = name,
            displayName = displayName,
            role = role,
            vote = vote,
            sentiment = sentiment,
            confidence = confidence,
            reasoning = when {
                bullishScore >= 3 -> "Smart money accumulating - follow the whales"
                bearishScore >= 3 -> "Smart money distributing - whales exiting"
                bullishScore > bearishScore -> "Mild institutional interest"
                bearishScore > bullishScore -> "Institutional caution"
                else -> "No clear institutional signal"
            },
            keyIndicators = indicators
        )
    }
}

// ============================================================================
// HEDGE FUND SPECIALIST: GUARDIAN (Liquidation Cascade Detector)
// ============================================================================

/**
 * 12. Guardian (Chief Risk Guardian) - Liquidation Cascade Detector
 * 
 * Monitors systemic risk from leveraged position liquidations.
 * Named Guardian - the protector against catastrophic loss events.
 * 
 * October 2025 Cascade Event Reference:
 * - $19.13 billion liquidated in 24 hours (largest in crypto history)
 * - BTC dropped 14% ($122K → $105K)
 * - Altcoins dropped 33% in 25 minutes
 * 
 * Detection: Composite risk score from 6 independent factors.
 */
class LiquidationCascadeDetector : BoardMember {
    override val name = "LiquidationCascadeDetector"
    override val displayName = "Guardian"
    override val role = "Chief Risk Guardian"
    override val weight = 0.143  // Default for hedge fund board
    
    companion object {
        const val EXTREME_RISK_THRESHOLD = 0.8
        const val HIGH_RISK_THRESHOLD = 0.6
        const val ELEVATED_RISK_THRESHOLD = 0.4
        const val EXTREME_VOL_MULTIPLE = 3.0
        const val HIGH_VOL_MULTIPLE = 2.0
        const val RAPID_DRAWDOWN_THRESHOLD = 0.05
        const val SEVERE_DRAWDOWN_THRESHOLD = 0.10
        const val PANIC_VOLUME_MULTIPLE = 4.0
        const val HIGH_VOLUME_MULTIPLE = 2.5
    }
    
    override fun analyze(context: MarketContext): AgentOpinion {
        var cascadeRiskScore = 0.0
        val riskFactors = mutableListOf<String>()
        val indicators = mutableListOf<String>()
        
        val closes = context.closes
        val highs = context.highs
        val lows = context.lows
        val volumes = context.volumes
        
        if (closes.size < 20 || volumes.size < 20) {
            return createNeutralOpinion("Insufficient data for cascade analysis")
        }
        
        // === RISK FACTOR 1: Rapid Price Drawdown ===
        val recentHigh = closes.takeLast(10).maxOrNull() ?: context.currentPrice
        val rapidDrawdown = if (recentHigh > 0) {
            (recentHigh - context.currentPrice) / recentHigh
        } else 0.0
        
        when {
            rapidDrawdown > SEVERE_DRAWDOWN_THRESHOLD -> {
                cascadeRiskScore += 0.25
                riskFactors.add("SEVERE: %.1f%% drawdown in 10 periods".format(rapidDrawdown * 100))
            }
            rapidDrawdown > RAPID_DRAWDOWN_THRESHOLD -> {
                cascadeRiskScore += 0.15
                riskFactors.add("Rapid %.1f%% drawdown detected".format(rapidDrawdown * 100))
            }
        }
        
        // === RISK FACTOR 2: Volatility Spike ===
        val atr = VolatilityIndicators.atr(highs, lows, closes, 14)
        val avgAtr = if (closes.size >= 50) {
            val atrSamples = mutableListOf<Double>()
            for (i in 20 until closes.size) {
                atrSamples.add(VolatilityIndicators.atr(
                    highs.take(i + 1), lows.take(i + 1), closes.take(i + 1), 14
                ))
            }
            if (atrSamples.isNotEmpty()) atrSamples.average() else atr
        } else atr
        
        val atrMultiple = if (avgAtr > 0) atr / avgAtr else 1.0
        
        when {
            atrMultiple > EXTREME_VOL_MULTIPLE -> {
                cascadeRiskScore += 0.20
                riskFactors.add("EXTREME volatility: %.1fx normal ATR".format(atrMultiple))
            }
            atrMultiple > HIGH_VOL_MULTIPLE -> {
                cascadeRiskScore += 0.10
                riskFactors.add("High volatility: %.1fx normal ATR".format(atrMultiple))
            }
        }
        
        // === RISK FACTOR 3: Volume Spike on Down Moves ===
        val priorVolume = volumes.dropLast(5).takeLast(20)
        val avgVolume = if (priorVolume.isNotEmpty()) priorVolume.average() else 0.0
        val recentVolume = volumes.takeLast(5).let { if (it.isNotEmpty()) it.average() else 0.0 }
        val volumeMultiple = if (avgVolume > 0) recentVolume / avgVolume else 1.0
        
        val recentPriceChange = if (closes.size >= 5) {
            val oldPrice = closes[closes.size - 5]
            if (oldPrice > 0) (closes.last() - oldPrice) / oldPrice else 0.0
        } else 0.0
        
        when {
            volumeMultiple > PANIC_VOLUME_MULTIPLE && recentPriceChange < -0.02 -> {
                cascadeRiskScore += 0.20
                riskFactors.add("PANIC SELLING: %.1fx volume on %.1f%% drop".format(
                    volumeMultiple, recentPriceChange * 100))
            }
            volumeMultiple > HIGH_VOLUME_MULTIPLE && recentPriceChange < 0 -> {
                cascadeRiskScore += 0.10
                riskFactors.add("High volume selling: %.1fx on down move".format(volumeMultiple))
            }
        }
        
        // === RISK FACTOR 4: Consecutive Down Candles ===
        val recentCloses = closes.takeLast(10)
        var consecutiveDown = 0
        var currentStreak = 0
        for (i in 1 until recentCloses.size) {
            if (recentCloses[i] < recentCloses[i - 1]) {
                currentStreak++
                consecutiveDown = maxOf(consecutiveDown, currentStreak)
            } else {
                currentStreak = 0
            }
        }
        
        when {
            consecutiveDown >= 7 -> {
                cascadeRiskScore += 0.15
                riskFactors.add("CAPITULATION: $consecutiveDown consecutive down candles")
            }
            consecutiveDown >= 5 -> {
                cascadeRiskScore += 0.08
                riskFactors.add("Sustained selling: $consecutiveDown consecutive down candles")
            }
        }
        
        // === RISK FACTOR 5: Multiple Support Levels Broken ===
        val low20 = closes.takeLast(20).minOrNull() ?: context.currentPrice
        val low50 = if (closes.size >= 50) closes.takeLast(50).minOrNull() ?: low20 else low20
        val ema50 = TrendIndicators.ema(closes, minOf(50, closes.size))
        
        var supportsBroken = 0
        if (context.currentPrice < low20) supportsBroken++
        if (context.currentPrice < low50) supportsBroken++
        if (context.currentPrice < ema50) supportsBroken++
        
        when (supportsBroken) {
            3 -> {
                cascadeRiskScore += 0.15
                riskFactors.add("CRITICAL: Below 20-day low, 50-day low, AND 50 EMA")
            }
            2 -> {
                cascadeRiskScore += 0.08
                riskFactors.add("Multiple supports broken ($supportsBroken levels)")
            }
            1 -> {
                cascadeRiskScore += 0.03
                riskFactors.add("Key support level breached")
            }
        }
        
        // === RISK FACTOR 6: RSI in Extreme Territory ===
        val rsi = MomentumIndicators.rsi(closes, 14)
        
        when {
            rsi < 20 -> {
                if (cascadeRiskScore > 0.3) {
                    cascadeRiskScore += 0.10
                    riskFactors.add("EXTREME oversold RSI ${rsi.toInt()} during cascade")
                } else {
                    indicators.add("Oversold RSI ${rsi.toInt()} - potential bounce zone")
                }
            }
            rsi < 30 && cascadeRiskScore > 0.2 -> {
                cascadeRiskScore += 0.05
                riskFactors.add("Oversold RSI ${rsi.toInt()} with elevated risk")
            }
        }
        
        // === COMPILE FINAL ASSESSMENT ===
        cascadeRiskScore = minOf(cascadeRiskScore, 1.0)
        
        val vote: BoardVote
        val sentiment: Double
        val confidence: Double
        val reasoning: String
        
        when {
            cascadeRiskScore >= EXTREME_RISK_THRESHOLD -> {
                vote = BoardVote.STRONG_SELL
                sentiment = -0.9
                confidence = 0.95
                reasoning = "⚠️ EXTREME CASCADE RISK - Reduce all exposure immediately"
                indicators.add("CASCADE ALERT: Risk score %.0f%%".format(cascadeRiskScore * 100))
            }
            cascadeRiskScore >= HIGH_RISK_THRESHOLD -> {
                vote = BoardVote.STRONG_SELL
                sentiment = -0.7
                confidence = 0.85
                reasoning = "🔴 HIGH cascade risk - Hedge long positions, avoid new entries"
                indicators.add("HIGH RISK: Score %.0f%%".format(cascadeRiskScore * 100))
            }
            cascadeRiskScore >= ELEVATED_RISK_THRESHOLD -> {
                vote = BoardVote.SELL
                sentiment = -0.4
                confidence = 0.70
                reasoning = "⚠️ Elevated cascade risk - Reduce position sizes"
                indicators.add("ELEVATED RISK: Score %.0f%%".format(cascadeRiskScore * 100))
            }
            cascadeRiskScore >= 0.2 -> {
                vote = BoardVote.HOLD
                sentiment = -0.1
                confidence = 0.55
                reasoning = "Mild cascade risk detected - Monitor closely"
                indicators.add("Moderate risk: Score %.0f%%".format(cascadeRiskScore * 100))
            }
            else -> {
                vote = BoardVote.HOLD
                sentiment = 0.1
                confidence = 0.50
                reasoning = "No significant cascade risk detected"
                indicators.add("Low risk: Score %.0f%%".format(cascadeRiskScore * 100))
            }
        }
        
        indicators.addAll(riskFactors)
        
        return AgentOpinion(
            agentName = name,
            displayName = displayName,
            role = role,
            vote = vote,
            sentiment = sentiment,
            confidence = confidence,
            reasoning = reasoning,
            keyIndicators = indicators
        )
    }
    
    private fun createNeutralOpinion(reason: String): AgentOpinion {
        return AgentOpinion(
            agentName = name,
            displayName = displayName,
            role = role,
            vote = BoardVote.HOLD,
            sentiment = 0.0,
            confidence = 0.3,
            reasoning = reason,
            keyIndicators = listOf(reason)
        )
    }
}

// ============================================================================
// HEDGE FUND SPECIALIST: ATLAS (Regime Meta-Strategist)
// ============================================================================

/**
 * 13. Atlas (Chief Strategist) - Regime Detector / Meta-Strategist
 *
 * The board's meta-strategist: analyses WHAT KIND of market we're in and
 * whether the other members' signals make sense for the current regime.
 * Named Atlas because he holds up the strategic framework.
 *
 * 9 Regime Classification:
 * BULL_TRENDING, WEAK_BULL, BEAR_TRENDING, WEAK_BEAR,
 * HIGH_VOLATILITY, LOW_VOLATILITY, RANGING, BREAKOUT_PENDING, CRASH
 */
class RegimeMetaStrategist : BoardMember {
    override val name = "RegimeMetaStrategist"
    override val displayName = "Atlas"
    override val role = "Chief Strategist"
    override val weight = 0.143  // Default for hedge fund board

    private var lastDetectedRegime: String? = null
    private var regimeDurationCandles: Int = 0
    private val regimeTransitionHistory = mutableListOf<String>()

    companion object {
        const val MATURE_REGIME_CANDLES = 50
        const val EXHAUSTION_CANDLES = 100
        const val SQUEEZE_BANDWIDTH_THRESHOLD = 0.03
        const val WIDE_BANDWIDTH_THRESHOLD = 0.12
        const val STRONG_ALIGNMENT_THRESHOLD = 0.8
        const val WEAK_ALIGNMENT_THRESHOLD = 0.3
        const val STRONG_TREND_ADX = 30.0
        const val WEAK_TREND_ADX = 20.0
        const val NO_TREND_ADX = 15.0
    }

    override fun analyze(context: MarketContext): AgentOpinion {
        val closes = context.closes
        val highs = context.highs
        val lows = context.lows
        val volumes = context.volumes

        if (closes.size < 50 || volumes.size < 20) {
            return createNeutralOpinion("Insufficient data for regime analysis (need 50+ candles)")
        }

        val indicators = mutableListOf<String>()

        // Factor 1: Trend strength via ADX
        val adx = TrendIndicators.adx(highs, lows, closes)
        val trendStrength = adx.adx
        val trendDirection = if (adx.plusDI > adx.minusDI) "BULLISH" else "BEARISH"

        // Factor 2: Volatility regime via Bollinger bandwidth
        val bb = VolatilityIndicators.bollingerBands(closes)
        val bbBandwidth = if (bb.middle > 0) (bb.upper - bb.lower) / bb.middle else 0.0

        // Factor 3: ATR percentile
        val currentAtr = VolatilityIndicators.atr(highs, lows, closes, 14)
        val historicalAtrSamples = mutableListOf<Double>()
        if (closes.size >= 50) {
            for (i in 30 until closes.size) {
                historicalAtrSamples.add(
                    VolatilityIndicators.atr(
                        highs.take(i + 1), lows.take(i + 1), closes.take(i + 1), 14
                    )
                )
            }
        }
        val atrPercentile = if (historicalAtrSamples.isNotEmpty()) {
            historicalAtrSamples.count { it <= currentAtr }.toDouble() / historicalAtrSamples.size
        } else 0.5

        // Factor 4: EMA alignment
        val ema10 = TrendIndicators.ema(closes, 10)
        val ema30 = TrendIndicators.ema(closes, 30)
        val ema50 = TrendIndicators.ema(closes, minOf(50, closes.size))
        val emaAligned = when {
            ema10 > ema30 && ema30 > ema50 -> 1.0
            ema10 < ema30 && ema30 < ema50 -> -1.0
            ema10 > ema30 -> 0.3
            ema10 < ema30 -> -0.3
            else -> 0.0
        }

        // Factor 5: Momentum phase via RSI
        val rsi = MomentumIndicators.rsi(closes, 14)
        val momentumPhase = when {
            rsi > 70 -> "OVERBOUGHT"
            rsi > 55 -> "BULLISH_MOMENTUM"
            rsi > 45 -> "NEUTRAL"
            rsi > 30 -> "BEARISH_MOMENTUM"
            else -> "OVERSOLD"
        }

        // Factor 6: Volume trend
        val recentAvgVolume = if (volumes.size >= 10) volumes.takeLast(10).average() else 0.0
        val priorAvgVolume = if (volumes.size >= 30) {
            volumes.dropLast(10).takeLast(20).average()
        } else recentAvgVolume
        val volumeTrend = if (priorAvgVolume > 0) recentAvgVolume / priorAvgVolume else 1.0

        // Classify regime
        val regime = classifyRegime(
            trendStrength, trendDirection, bbBandwidth, atrPercentile,
            emaAligned, momentumPhase, volumeTrend
        )
        indicators.add("Regime: $regime")
        indicators.add("ADX: %.1f (%s)".format(trendStrength, trendDirection))
        indicators.add("BB Width: %.4f".format(bbBandwidth))
        indicators.add("ATR Pctile: %.0f%%".format(atrPercentile * 100))
        indicators.add("EMA Alignment: %+.1f".format(emaAligned))
        indicators.add("RSI Phase: $momentumPhase (%.0f)".format(rsi))

        // Track regime duration
        if (regime == lastDetectedRegime) {
            regimeDurationCandles++
        } else {
            if (lastDetectedRegime != null) {
                regimeTransitionHistory.add("${lastDetectedRegime}→$regime")
                if (regimeTransitionHistory.size > 20) {
                    regimeTransitionHistory.removeAt(0)
                }
            }
            lastDetectedRegime = regime
            regimeDurationCandles = 1
        }

        val transitionProbability = calculateTransitionProbability(
            regime, regimeDurationCandles, trendStrength, bbBandwidth,
            atrPercentile, momentumPhase, volumeTrend
        )
        indicators.add("Transition Prob: %.0f%%".format(transitionProbability * 100))
        indicators.add("Regime Age: $regimeDurationCandles candles")

        // Generate vote
        val (vote, sentiment, confidence, reasoning) = generateRegimeVote(
            regime, trendDirection, transitionProbability, emaAligned,
            momentumPhase, regimeDurationCandles
        )

        return AgentOpinion(
            agentName = name,
            displayName = displayName,
            role = role,
            vote = vote,
            sentiment = sentiment,
            confidence = confidence,
            reasoning = reasoning,
            keyIndicators = indicators
        )
    }

    private fun classifyRegime(
        trendStrength: Double,
        trendDirection: String,
        bbBandwidth: Double,
        atrPercentile: Double,
        emaAligned: Double,
        momentumPhase: String,
        volumeTrend: Double
    ): String {
        // CRASH: Extreme conditions
        if (atrPercentile > 0.95 && momentumPhase == "OVERSOLD" && emaAligned < -0.5) {
            return "CRASH"
        }
        
        // HIGH_VOLATILITY: Very high ATR, wide BB
        if (atrPercentile > 0.85 || bbBandwidth > WIDE_BANDWIDTH_THRESHOLD) {
            return "HIGH_VOLATILITY"
        }
        
        // BREAKOUT_PENDING: Squeezed BB, low ATR
        if (bbBandwidth < SQUEEZE_BANDWIDTH_THRESHOLD && atrPercentile < 0.25) {
            return "BREAKOUT_PENDING"
        }
        
        // LOW_VOLATILITY: Moderate squeeze
        if (atrPercentile < 0.3 && trendStrength < WEAK_TREND_ADX) {
            return "LOW_VOLATILITY"
        }
        
        // Strong trending regimes
        if (trendStrength > STRONG_TREND_ADX) {
            return if (trendDirection == "BULLISH") "BULL_TRENDING" else "BEAR_TRENDING"
        }
        
        // Weak trending regimes
        if (trendStrength > WEAK_TREND_ADX) {
            return if (trendDirection == "BULLISH") "WEAK_BULL" else "WEAK_BEAR"
        }
        
        // Default: RANGING
        return "RANGING"
    }

    private fun calculateTransitionProbability(
        regime: String,
        duration: Int,
        trendStrength: Double,
        bbBandwidth: Double,
        atrPercentile: Double,
        momentumPhase: String,
        volumeTrend: Double
    ): Double {
        var prob = 0.0
        
        // Age-based probability
        if (duration > EXHAUSTION_CANDLES) prob += 0.35
        else if (duration > MATURE_REGIME_CANDLES) prob += 0.20
        else if (duration > 20) prob += 0.10
        
        // Momentum extremes suggest transition
        if (momentumPhase == "OVERBOUGHT" || momentumPhase == "OVERSOLD") {
            prob += 0.15
        }
        
        // Weakening trend strength
        if (trendStrength < WEAK_TREND_ADX && regime.contains("TRENDING")) {
            prob += 0.15
        }
        
        // Volume divergence
        if (volumeTrend < 0.7 && regime.contains("TRENDING")) {
            prob += 0.10
        }
        
        return minOf(prob, 0.9)
    }

    private fun generateRegimeVote(
        regime: String,
        trendDirection: String,
        transitionProb: Double,
        emaAligned: Double,
        momentumPhase: String,
        duration: Int
    ): VoteResult {
        val (baseVote, baseSentiment) = when (regime) {
            "BULL_TRENDING" -> Pair(BoardVote.BUY, 0.6)
            "WEAK_BULL" -> Pair(BoardVote.BUY, 0.3)
            "BEAR_TRENDING" -> Pair(BoardVote.SELL, -0.6)
            "WEAK_BEAR" -> Pair(BoardVote.SELL, -0.3)
            "HIGH_VOLATILITY" -> Pair(BoardVote.SELL, -0.4)
            "CRASH" -> Pair(BoardVote.STRONG_SELL, -0.9)
            "BREAKOUT_PENDING" -> Pair(BoardVote.HOLD, 0.0)
            "LOW_VOLATILITY" -> Pair(BoardVote.HOLD, 0.05)
            "RANGING" -> Pair(BoardVote.HOLD, 0.0)
            else -> Pair(BoardVote.HOLD, 0.0)
        }

        val transitionDamping = 1.0 - (transitionProb * 0.6)
        val adjustedSentiment = baseSentiment * transitionDamping

        val baseConfidence = when (regime) {
            "BULL_TRENDING", "BEAR_TRENDING" -> 0.85
            "CRASH" -> 0.95
            "BREAKOUT_PENDING" -> 0.75
            "HIGH_VOLATILITY" -> 0.70
            "WEAK_BULL", "WEAK_BEAR" -> 0.60
            "LOW_VOLATILITY" -> 0.55
            "RANGING" -> 0.50
            else -> 0.45
        }
        val adjustedConfidence = baseConfidence * (1.0 - transitionProb * 0.4)

        val finalVote = when {
            transitionProb > 0.7 && (baseVote == BoardVote.BUY || baseVote == BoardVote.SELL) ->
                BoardVote.HOLD
            transitionProb > 0.5 && baseVote == BoardVote.STRONG_SELL && regime != "CRASH" ->
                BoardVote.SELL
            else -> sentimentToVote(adjustedSentiment)
        }

        val freshness = when {
            duration < 10 -> "fresh"
            duration < MATURE_REGIME_CANDLES -> "established"
            duration < EXHAUSTION_CANDLES -> "mature"
            else -> "exhausting"
        }

        val reasoning = "$regime regime ($freshness, ${duration}c) | Trans prob: ${(transitionProb * 100).toInt()}%"

        return VoteResult(finalVote, adjustedSentiment, adjustedConfidence, reasoning)
    }

    private fun createNeutralOpinion(reason: String): AgentOpinion {
        return AgentOpinion(
            agentName = name,
            displayName = displayName,
            role = role,
            vote = BoardVote.HOLD,
            sentiment = 0.0,
            confidence = 0.3,
            reasoning = reason,
            keyIndicators = listOf(reason)
        )
    }

    private data class VoteResult(
        val vote: BoardVote,
        val sentiment: Double,
        val confidence: Double,
        val reasoning: String
    )
}

// ============================================================================
// CROSSOVER MEMBER: ECHO (Order Book Imbalance Analyst)
// ============================================================================

/**
 * 14. Echo (Chief Order Flow Officer) - Order Book Imbalance Analyst
 *
 * Detects buy/sell pressure imbalances from market microstructure signals.
 * Named Echo — reflecting how order flow imbalances echo through price action.
 *
 * CROSSOVER: May be considered for Octagon inclusion in future versions.
 * Order flow analysis is valuable for scalping timing in any context.
 *
 * 5 Factors:
 * 1. Volume-weighted price pressure
 * 2. Trade aggression asymmetry
 * 3. Absorption detection
 * 4. Spread-implied liquidity
 * 5. Momentum exhaustion
 */
class OrderBookImbalanceAnalyst : BoardMember {
    override val name = "OrderBookImbalanceAnalyst"
    override val displayName = "Echo"
    override val role = "Chief Order Flow Officer"
    override val weight = 0.143  // Default for hedge fund board

    companion object {
        const val STRONG_BODY_RATIO = 0.70
        const val WEAK_BODY_RATIO = 0.30
        const val ABSORPTION_VOL_MULTIPLE = 1.8
        const val ABSORPTION_MOVE_THRESHOLD = 0.003
        const val PRESSURE_LOOKBACK = 10
        const val STRONG_PRESSURE_THRESHOLD = 0.65
        const val MILD_PRESSURE_THRESHOLD = 0.55
        const val EXHAUSTION_VOL_DECLINE = 0.60
        const val EXHAUSTION_WICK_RATIO = 0.50
        const val SHORT_TF_CONFIDENCE_BOOST = 0.15
        const val LONG_TF_CONFIDENCE_PENALTY = 0.20
    }

    override fun analyze(context: MarketContext): AgentOpinion {
        val opens = context.opens
        val highs = context.highs
        val lows = context.lows
        val closes = context.closes
        val volumes = context.volumes

        if (closes.size < 20 || volumes.size < 20 || opens.size < 20) {
            return createNeutralOpinion("Insufficient data for order flow analysis (need 20+ candles)")
        }

        var buyPressureScore = 0.0
        var sellPressureScore = 0.0
        val indicators = mutableListOf<String>()

        // FACTOR 1: VOLUME-WEIGHTED PRICE PRESSURE
        val recentCount = minOf(PRESSURE_LOOKBACK, closes.size)
        var totalBuyPressureVol = 0.0
        var totalSellPressureVol = 0.0

        for (i in (closes.size - recentCount) until closes.size) {
            val range = highs[i] - lows[i]
            if (range <= 0) continue
            val closePosition = (closes[i] - lows[i]) / range
            val vol = volumes[i]
            totalBuyPressureVol += closePosition * vol
            totalSellPressureVol += (1.0 - closePosition) * vol
        }

        val totalPressureVol = totalBuyPressureVol + totalSellPressureVol
        val buyPressureRatio = if (totalPressureVol > 0) {
            totalBuyPressureVol / totalPressureVol
        } else 0.5

        when {
            buyPressureRatio > STRONG_PRESSURE_THRESHOLD -> {
                buyPressureScore += 0.30
                indicators.add("STRONG buy pressure: %.0f%% vol-weighted".format(buyPressureRatio * 100))
            }
            buyPressureRatio > MILD_PRESSURE_THRESHOLD -> {
                buyPressureScore += 0.15
                indicators.add("Mild buy pressure: %.0f%% vol-weighted".format(buyPressureRatio * 100))
            }
            buyPressureRatio < (1.0 - STRONG_PRESSURE_THRESHOLD) -> {
                sellPressureScore += 0.30
                indicators.add("STRONG sell pressure: %.0f%% vol-weighted".format((1.0 - buyPressureRatio) * 100))
            }
            buyPressureRatio < (1.0 - MILD_PRESSURE_THRESHOLD) -> {
                sellPressureScore += 0.15
                indicators.add("Mild sell pressure: %.0f%% vol-weighted".format((1.0 - buyPressureRatio) * 100))
            }
        }

        // FACTOR 2: TRADE AGGRESSION ASYMMETRY
        var aggressiveBuys = 0
        var aggressiveSells = 0
        val aggressionLookback = minOf(10, closes.size)

        for (i in (closes.size - aggressionLookback) until closes.size) {
            val range = highs[i] - lows[i]
            if (range <= 0) continue
            val body = kotlin.math.abs(closes[i] - opens[i])
            val bodyRatio = body / range

            if (bodyRatio >= STRONG_BODY_RATIO && volumes[i] > volumes.average() * 1.2) {
                if (closes[i] > opens[i]) aggressiveBuys++ else aggressiveSells++
            }
        }

        when {
            aggressiveBuys >= 4 && aggressiveSells <= 1 -> {
                buyPressureScore += 0.25
                indicators.add("Aggressive buying: $aggressiveBuys aggressive candles")
            }
            aggressiveSells >= 4 && aggressiveBuys <= 1 -> {
                sellPressureScore += 0.25
                indicators.add("Aggressive selling: $aggressiveSells aggressive candles")
            }
        }

        // FACTOR 3: ABSORPTION DETECTION
        val avgVolume = volumes.average()
        var absorptionCount = 0
        val absorptionLookback = minOf(5, closes.size)

        for (i in (closes.size - absorptionLookback) until closes.size) {
            val volRatio = if (avgVolume > 0) volumes[i] / avgVolume else 1.0
            val priceMove = if (closes[i] > 0) {
                kotlin.math.abs(closes[i] - opens[i]) / closes[i]
            } else 0.0

            if (volRatio > ABSORPTION_VOL_MULTIPLE && priceMove < ABSORPTION_MOVE_THRESHOLD) {
                absorptionCount++
            }
        }

        if (absorptionCount >= 2) {
            val recentTrend = if (closes.size >= 10) {
                (closes.last() - closes[closes.size - 10]) / closes[closes.size - 10]
            } else 0.0
            
            if (recentTrend > 0.01) {
                sellPressureScore += 0.15
                indicators.add("Absorption during uptrend ($absorptionCount events)")
            } else if (recentTrend < -0.01) {
                buyPressureScore += 0.15
                indicators.add("Absorption during downtrend ($absorptionCount events)")
            }
        }

        // Compile
        val netPressure = buyPressureScore - sellPressureScore
        val tfAdjustment = when (context.timeframe) {
            "1m", "3m", "5m", "15m" -> SHORT_TF_CONFIDENCE_BOOST
            "4h", "6h", "8h", "12h", "1d", "1w" -> -LONG_TF_CONFIDENCE_PENALTY
            else -> 0.0
        }

        val baseConfidence = minOf(
            (kotlin.math.abs(buyPressureScore) + kotlin.math.abs(sellPressureScore)) * 0.8,
            0.90
        )
        val adjustedConfidence = maxOf(minOf(baseConfidence + tfAdjustment, 0.95), 0.25)
        val sentiment = maxOf(minOf(netPressure, 1.0), -1.0)
        val vote = sentimentToVote(sentiment)

        val reasoning = when {
            netPressure > 0.4 -> "Strong order flow imbalance: buyers dominating"
            netPressure > 0.15 -> "Moderate buy-side pressure building"
            netPressure < -0.4 -> "Strong order flow imbalance: sellers dominating"
            netPressure < -0.15 -> "Moderate sell-side pressure building"
            else -> "Order flow balanced — no clear directional pressure"
        }

        return AgentOpinion(
            agentName = name,
            displayName = displayName,
            role = role,
            vote = vote,
            sentiment = sentiment,
            confidence = adjustedConfidence,
            reasoning = reasoning,
            keyIndicators = indicators
        )
    }

    private fun createNeutralOpinion(reason: String): AgentOpinion {
        return AgentOpinion(
            agentName = name,
            displayName = displayName,
            role = role,
            vote = BoardVote.HOLD,
            sentiment = 0.0,
            confidence = 0.3,
            reasoning = reason,
            keyIndicators = listOf(reason)
        )
    }
}

// ============================================================================
// HEDGE FUND SPECIALIST: THETA (Funding Rate Arbitrage Analyst)
// ============================================================================

/**
 * 15. Theta (Chief Arbitrage Officer) - Funding Rate Arbitrage Analyst
 *
 * Analyses perpetual futures funding rate dynamics, basis spread, and open
 * interest patterns. Named Theta — reflecting the time-decay component
 * fundamental to carry trades.
 *
 * 5 Factors:
 * 1. Funding rate trend (RSI proxy)
 * 2. Basis spread analysis (MA deviation)
 * 3. Open interest divergence (volume proxy)
 * 4. Funding rate extremes + mean reversion
 * 5. Crowded trade detection
 *
 * CONTRARIAN: Theta is inherently contrarian - detects crowded trades.
 */
class FundingRateArbitrageAnalyst : BoardMember {
    override val name = "FundingRateArbitrageAnalyst"
    override val displayName = "Theta"
    override val role = "Chief Arbitrage Officer"
    override val weight = 0.143  // Default for hedge fund board

    companion object {
        const val EXTREME_MOMENTUM_RSI = 80.0
        const val EXTREME_OVERSOLD_RSI = 20.0
        const val HIGH_MOMENTUM_RSI = 70.0
        const val LOW_MOMENTUM_RSI = 30.0
        const val LARGE_PREMIUM_THRESHOLD = 0.03
        const val LARGE_DISCOUNT_THRESHOLD = -0.03
        const val OI_RISING_VOL_THRESHOLD = 1.3
        const val OI_FALLING_VOL_THRESHOLD = 0.7
        const val VOLUME_FADE_RATIO = 0.65
        const val MEAN_REVERSION_LOOKBACK = 20
        const val EXTREME_SIGMA = 2.5
    }

    override fun analyze(context: MarketContext): AgentOpinion {
        val opens = context.opens
        val highs = context.highs
        val lows = context.lows
        val closes = context.closes
        val volumes = context.volumes

        if (closes.size < 20 || volumes.size < 20) {
            return createNeutralOpinion("Insufficient data for funding rate analysis (need 20+ candles)")
        }

        var bullishScore = 0.0
        var bearishScore = 0.0
        val indicators = mutableListOf<String>()

        // FACTOR 1: FUNDING RATE TREND (RSI proxy)
        val rsiPeriod = minOf(14, closes.size - 1)
        val rsi = calculateRSI(closes, rsiPeriod)

        when {
            rsi > EXTREME_MOMENTUM_RSI -> {
                bearishScore += 0.25
                indicators.add("Extreme positive funding proxy (RSI %.1f) — longs crowded".format(rsi))
            }
            rsi > HIGH_MOMENTUM_RSI -> {
                bearishScore += 0.10
                indicators.add("Elevated funding proxy (RSI %.1f)".format(rsi))
            }
            rsi < EXTREME_OVERSOLD_RSI -> {
                bullishScore += 0.25
                indicators.add("Extreme negative funding proxy (RSI %.1f) — shorts crowded".format(rsi))
            }
            rsi < LOW_MOMENTUM_RSI -> {
                bullishScore += 0.10
                indicators.add("Depressed funding proxy (RSI %.1f)".format(rsi))
            }
        }

        // FACTOR 2: BASIS SPREAD ANALYSIS
        val sma20 = closes.takeLast(20).average()
        val basisProxy = (closes.last() - sma20) / sma20

        when {
            basisProxy > LARGE_PREMIUM_THRESHOLD -> {
                bearishScore += 0.20
                indicators.add("Basis premium proxy: +%.1f%% above MA20".format(basisProxy * 100))
            }
            basisProxy < LARGE_DISCOUNT_THRESHOLD -> {
                bullishScore += 0.20
                indicators.add("Basis discount proxy: %.1f%% below MA20".format(basisProxy * 100))
            }
        }

        // FACTOR 3: OPEN INTEREST DIVERGENCE
        if (closes.size >= 10 && volumes.size >= 10) {
            val avgVol = volumes.average()
            val recentVol = volumes.takeLast(5).average()
            val volRatio = if (avgVol > 0) recentVol / avgVol else 1.0
            val priceChange5 = (closes.last() - closes[closes.size - 6]) / closes[closes.size - 6]

            when {
                volRatio > OI_RISING_VOL_THRESHOLD && priceChange5 > 0.01 -> {
                    bullishScore += 0.15
                    indicators.add("OI rising + price up: new longs")
                }
                volRatio > OI_RISING_VOL_THRESHOLD && priceChange5 < -0.01 -> {
                    bearishScore += 0.15
                    indicators.add("OI rising + price down: new shorts")
                }
                volRatio < OI_FALLING_VOL_THRESHOLD && priceChange5 > 0.01 -> {
                    bearishScore += 0.10
                    indicators.add("OI declining in uptrend — longs exiting")
                }
                volRatio < OI_FALLING_VOL_THRESHOLD && priceChange5 < -0.01 -> {
                    bullishScore += 0.10
                    indicators.add("OI declining in downtrend — shorts covering")
                }
            }
        }

        // FACTOR 4: MEAN REVERSION
        if (closes.size >= MEAN_REVERSION_LOOKBACK) {
            val returns = (1 until closes.size).map { i ->
                (closes[i] - closes[i-1]) / closes[i-1]
            }
            val meanReturn = returns.average()
            val stdReturn = kotlin.math.sqrt(
                returns.map { (it - meanReturn) * (it - meanReturn) }.average()
            )

            if (stdReturn > 0) {
                val recent3Return = (closes.last() - closes[closes.size - 4]) / closes[closes.size - 4]
                val zScore = (recent3Return - meanReturn * 3) / (stdReturn * kotlin.math.sqrt(3.0))

                when {
                    zScore > EXTREME_SIGMA -> {
                        bearishScore += 0.20
                        indicators.add("Mean reversion: +%.1fσ extreme".format(zScore))
                    }
                    zScore < -EXTREME_SIGMA -> {
                        bullishScore += 0.20
                        indicators.add("Mean reversion: %.1fσ extreme".format(zScore))
                    }
                }
            }
        }

        // FACTOR 5: CROWDED TRADE DETECTION
        if (closes.size >= 15) {
            val recent5Ranges = (closes.size - 5 until closes.size).map { i ->
                (highs[i] - lows[i]) / closes[i]
            }
            val prior10Ranges = (closes.size - 15 until closes.size - 5).map { i ->
                (highs[i] - lows[i]) / closes[i]
            }
            val rangeCompression = if (prior10Ranges.average() > 0) {
                recent5Ranges.average() / prior10Ranges.average()
            } else 1.0

            val trendDir = if (closes.last() > closes[closes.size - 15]) "UP" else "DOWN"

            var crowdedSignals = 0
            if (rangeCompression < 0.6) crowdedSignals++
            if (rsi > HIGH_MOMENTUM_RSI || rsi < LOW_MOMENTUM_RSI) crowdedSignals++

            if (crowdedSignals >= 2) {
                when (trendDir) {
                    "UP" -> {
                        bearishScore += 0.15
                        indicators.add("Crowded long: $crowdedSignals signals")
                    }
                    "DOWN" -> {
                        bullishScore += 0.15
                        indicators.add("Crowded short: $crowdedSignals signals")
                    }
                }
            }
        }

        // Compile
        val netSignal = bullishScore - bearishScore
        val baseConfidence = minOf(
            (kotlin.math.abs(bullishScore) + kotlin.math.abs(bearishScore)) * 0.75, 0.90
        )
        val sentiment = maxOf(minOf(netSignal, 1.0), -1.0)
        val vote = sentimentToVote(sentiment)

        val reasoning = when {
            netSignal > 0.35 -> "Funding/basis bullish: shorts crowded"
            netSignal > 0.12 -> "Moderate bullish carry"
            netSignal < -0.35 -> "Funding/basis bearish: longs crowded"
            netSignal < -0.12 -> "Moderate bearish carry"
            else -> "Carry neutral"
        }

        return AgentOpinion(
            agentName = name, displayName = displayName, role = role,
            vote = vote, sentiment = sentiment,
            confidence = maxOf(baseConfidence, 0.25),
            reasoning = reasoning, keyIndicators = indicators
        )
    }

    private fun calculateRSI(closes: List<Double>, period: Int): Double {
        if (closes.size < period + 1) return 50.0
        var avgGain = 0.0; var avgLoss = 0.0
        for (i in 1..period) {
            val change = closes[closes.size - period - 1 + i] - closes[closes.size - period - 1 + i - 1]
            if (change > 0) avgGain += change else avgLoss += kotlin.math.abs(change)
        }
        avgGain /= period; avgLoss /= period
        if (avgLoss == 0.0) return 100.0
        return 100.0 - (100.0 / (1.0 + avgGain / avgLoss))
    }

    private fun createNeutralOpinion(reason: String): AgentOpinion {
        return AgentOpinion(
            agentName = name, displayName = displayName, role = role,
            vote = BoardVote.HOLD, sentiment = 0.0, confidence = 0.3,
            reasoning = reason, keyIndicators = listOf(reason)
        )
    }
}
