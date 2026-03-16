package com.miwealth.sovereignvantage.core.ai

import com.miwealth.sovereignvantage.core.indicators.*
import com.miwealth.sovereignvantage.core.ml.DQNTrader
import com.miwealth.sovereignvantage.core.ml.EnhancedFeatureVector
import com.miwealth.sovereignvantage.core.ml.MarketState
import com.miwealth.sovereignvantage.core.signals.*
import kotlin.math.abs
import java.util.UUID

/**
 * AI Board of Directors - The "Octagon" Architecture (8 Members) - V5.17.0
 * 
 * 8 specialized AI experts vote on every trade decision.
 * Each expert analyzes from their unique perspective.
 * All decisions are recorded for eXplainable AI (XAI) compliance.
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * 
 * OCTAGON Board Members (v5.5.62 - Clean Separation Complete):
 * ┌────────────┬──────────────────────┬────────────────────────────────────────┬────────┐
 * │ Persona    │ C-Suite Role         │ Analysis Focus                         │ Weight │
 * ├────────────┼──────────────────────┼────────────────────────────────────────┼────────┤
 * │ Arthur     │ CTO/Chairman         │ TrendFollower - Momentum & Direction   │ 12.5%  │
 * │ Helena     │ CRO                  │ MeanReverter - Risk & Mean Reversion   │ 12.5%  │
 * │ Sentinel   │ CCO                  │ VolatilityTrader - Compliance & Vol    │ 12.5%  │
 * │ Oracle     │ CDO                  │ SentimentAnalyst - Market Intel        │ 12.5%  │
 * │ Nexus      │ COO                  │ OnChainAnalyst - Blockchain Data       │ 12.5%  │
 * │ Marcus     │ CIO                  │ MacroStrategist - Portfolio Structure  │ 12.5%  │
 * │ Cipher     │ CSO                  │ PatternRecognizer - Chart Patterns     │ 12.5%  │
 * │ Aegis      │ Chief Defense        │ LiquidityHunter - Capitulation Buying  │ 12.5%  │
 * └────────────┴──────────────────────┴────────────────────────────────────────┴────────┘
 *                                                                        TOTAL = 100%
 * 
 * CASTING VOTE: Sentinel (VolatilityTrader) - Based on 100% accuracy in Q1 2025 backtest
 * 
 * Version History:
 * - v5.5.39: Original 8-member "Octagon" architecture
 * - v5.5.51-57: Expanded to 15 members (Pentadecagon) - HEDGE FUND CONTAMINATION
 * - v5.5.58+: Reverted to 8-member Octagon. Weights corrected to 0.125 each in v5.5.92
 *   (6 members still had stale 0.07 Pentadecagon weights — 1.78× imbalance vs TrendFollower/MeanReverter)
 * - v5.5.60: RESTORED to 8-member Octagon
 * - v5.5.62: HEDGE FUND CLASSES EXTRACTED to HedgeFundBoardMembers.kt
 *            This file now contains ONLY the 8 core Octagon member classes
 * 
 * Related Files (v5.5.62 Board Separation Complete):
 * - BoardMemberRegistry.kt: Member definitions, categories, presets
 * - ConfigurableBoardOrchestrator.kt: 1-20 members, any configuration
 * - TestBoardOrchestrator.kt: Backtesting with performance tracking
 * - HedgeFundBoardOrchestrator.kt: Hedge fund board logic (UNWIRED)
 * - HedgeFundBoardMembers.kt: 7 hedge fund member classes (extracted v5.5.62)
 * 
 * Hedge Fund Members (now in HedgeFundBoardMembers.kt):
 * - Soros (GlobalMacroAnalyst) - HEDGE_FUND
 * - Guardian (LiquidationCascadeDetector) - HEDGE_FUND
 * - Draper (DeFiSpecialist) - HEDGE_FUND
 * - Atlas (RegimeMetaStrategist) - HEDGE_FUND
 * - Theta (FundingRateArbitrageAnalyst) - HEDGE_FUND
 * - Moby (WhaleTracker) - CROSSOVER (potential Octagon candidate)
 * - Echo (OrderBookImbalanceAnalyst) - CROSSOVER (potential Octagon candidate)
 */

// ============================================================================
// XAI DECISION RECORD - For Explainable AI Compliance
// ============================================================================

/**
 * Complete record of an AI Board decision for XAI compliance.
 * Must be stored with every trade for audit and taxation purposes.
 */
data class BoardDecisionRecord(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val symbol: String,
    val timeframe: String,
    val marketContext: MarketContextSnapshot,
    val individualVotes: List<MemberVoteRecord>,
    val consensus: BoardConsensus,
    val actionTaken: String,  // "SIGNAL_GENERATED", "TRADE_EXECUTED", "HOLD", "REJECTED"
    val reasonForAction: String
) {
    fun toAuditString(): String {
        val sb = StringBuilder()
        sb.appendLine("═══════════════════════════════════════════════════════════")
        sb.appendLine("AI BOARD DECISION RECORD - XAI AUDIT TRAIL")
        sb.appendLine("═══════════════════════════════════════════════════════════")
        sb.appendLine("Decision ID: $id")
        sb.appendLine("Timestamp: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(timestamp))}")
        sb.appendLine("Symbol: $symbol | Timeframe: $timeframe")
        sb.appendLine("───────────────────────────────────────────────────────────")
        sb.appendLine("MARKET CONTEXT:")
        sb.appendLine("  Price: ${marketContext.currentPrice}")
        sb.appendLine("  24h Change: ${String.format("%.2f", marketContext.change24h)}%")
        sb.appendLine("───────────────────────────────────────────────────────────")
        sb.appendLine("BOARD VOTES:")
        individualVotes.forEach { vote ->
            sb.appendLine("  ${vote.displayName} (${vote.role}): ${vote.vote}")
            sb.appendLine("    Confidence: ${String.format("%.1f", vote.confidence * 100)}%")
            sb.appendLine("    Reasoning: ${vote.reasoning}")
            sb.appendLine("    Key Indicators: ${vote.keyIndicators.joinToString(", ")}")
        }
        sb.appendLine("───────────────────────────────────────────────────────────")
        sb.appendLine("CONSENSUS:")
        sb.appendLine("  Final Decision: ${consensus.finalDecision}")
        sb.appendLine("  Weighted Score: ${String.format("%.3f", consensus.weightedScore)}")
        sb.appendLine("  Board Agreement: ${consensus.unanimousCount}/${consensus.opinions.size}")
        sb.appendLine("  Confidence: ${String.format("%.1f", consensus.confidence * 100)}%")
        sb.appendLine("───────────────────────────────────────────────────────────")
        sb.appendLine("ACTION: $actionTaken")
        sb.appendLine("REASON: $reasonForAction")
        sb.appendLine("═══════════════════════════════════════════════════════════")
        return sb.toString()
    }
}

data class MarketContextSnapshot(
    val currentPrice: Double,
    val change24h: Double,
    val volume24h: Double,
    val high24h: Double,
    val low24h: Double,
    // Macro sentiment fields (V5.17.0)
    val macroSentiment: String? = null,      // "HAWKISH", "DOVISH", "NEUTRAL"
    val macroScore: Double? = null,          // -1.0 to +1.0
    val macroRiskLevel: String? = null,      // "LOW", "ELEVATED", "HIGH", "EXTREME"
    val upcomingHighImpactEvents: Int = 0,   // Count of critical events in next 48h
    val macroNarrative: String? = null       // Human-readable summary
)

data class MemberVoteRecord(
    val memberId: String,
    val displayName: String,
    val role: String,
    val vote: BoardVote,
    val sentiment: Double,
    val confidence: Double,
    val weight: Double,
    val reasoning: String,
    val keyIndicators: List<String>
)

// ============================================================================
// DATA CLASSES
// ============================================================================

enum class BoardVote {
    STRONG_BUY,   // +2 points
    BUY,          // +1 point
    HOLD,         //  0 points
    SELL,         // -1 point
    STRONG_SELL   // -2 points
}

data class AgentOpinion(
    val agentName: String,       // Functional name (e.g., "TrendFollower")
    val displayName: String,     // Persona name (e.g., "Arthur") for XAI display
    val role: String,            // Full role title for XAI display
    val vote: BoardVote,
    val sentiment: Double,       // -1.0 (Bearish) to 1.0 (Bullish)
    val confidence: Double,      // 0.0 to 1.0
    val reasoning: String,
    val keyIndicators: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

data class BoardConsensus(
    val finalDecision: BoardVote,
    val weightedScore: Double,
    val confidence: Double,
    val unanimousCount: Int,
    val dissenterReasons: List<String>,
    val opinions: List<AgentOpinion>,
    val synthesis: String,
    /** V5.17.0: Board-recommended position size multiplier (0.0–1.0). 
     *  Applied to Kelly-derived position size. Board can only REDUCE, never exceed Kelly ceiling. */
    val recommendedPositionSize: Double = 1.0,
    val sessionId: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Generate XAI explanation for console display and audit logs.
     * This is the human-readable explanation of the AI decision.
     */
    fun toXAIExplanation(): String {
        val sb = StringBuilder()
        sb.appendLine("═══════════════════════════════════════════════════════════════════")
        sb.appendLine("                    AI BOARD DECISION - XAI AUDIT")
        sb.appendLine("═══════════════════════════════════════════════════════════════════")
        sb.appendLine("Session ID: $sessionId")
        sb.appendLine("Timestamp: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS z").format(java.util.Date(timestamp))}")
        sb.appendLine()
        sb.appendLine("┌─────────────────────────────────────────────────────────────────┐")
        sb.appendLine("│ FINAL DECISION: $finalDecision")
        sb.appendLine("│ Weighted Score: ${String.format("%.4f", weightedScore)}")
        sb.appendLine("│ Overall Confidence: ${String.format("%.1f", confidence * 100)}%")
        sb.appendLine("│ Board Agreement: $unanimousCount/${opinions.size} members")
        sb.appendLine("│ Position Size Rec: ${String.format("%.0f", recommendedPositionSize * 100)}% of Kelly ceiling")
        sb.appendLine("└─────────────────────────────────────────────────────────────────┘")
        sb.appendLine()
        sb.appendLine("SYNTHESIS: $synthesis")
        sb.appendLine()
        sb.appendLine("───────────────────────────────────────────────────────────────────")
        sb.appendLine("                        INDIVIDUAL VOTES")
        sb.appendLine("───────────────────────────────────────────────────────────────────")
        
        for (opinion in opinions) {
            val voteEmoji = when (opinion.vote) {
                BoardVote.STRONG_BUY -> "🟢🟢"
                BoardVote.BUY -> "🟢"
                BoardVote.HOLD -> "⚪"
                BoardVote.SELL -> "🔴"
                BoardVote.STRONG_SELL -> "🔴🔴"
            }
            sb.appendLine()
            sb.appendLine("$voteEmoji ${opinion.displayName} (${opinion.role})")
            sb.appendLine("   Vote: ${opinion.vote} | Confidence: ${String.format("%.1f", opinion.confidence * 100)}%")
            sb.appendLine("   Sentiment: ${String.format("%+.3f", opinion.sentiment)}")
            sb.appendLine("   Reasoning: ${opinion.reasoning}")
            if (opinion.keyIndicators.isNotEmpty()) {
                sb.appendLine("   Indicators: ${opinion.keyIndicators.joinToString(", ")}")
            }
        }
        
        if (dissenterReasons.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("───────────────────────────────────────────────────────────────────")
            sb.appendLine("                      DISSENTING OPINIONS")
            sb.appendLine("───────────────────────────────────────────────────────────────────")
            dissenterReasons.forEach { sb.appendLine("  ⚠️ $it") }
        }
        
        sb.appendLine()
        sb.appendLine("═══════════════════════════════════════════════════════════════════")
        return sb.toString()
    }
    
    /**
     * Generate compact JSON for database storage and tax records.
     */
    fun toAuditJson(): String {
        return org.json.JSONObject().apply {
            put("sessionId", sessionId)
            put("timestamp", timestamp)
            put("decision", finalDecision.name)
            put("weightedScore", weightedScore)
            put("confidence", confidence)
            put("boardAgreement", unanimousCount)
            put("synthesis", synthesis)
            put("votes", org.json.JSONArray().apply {
                opinions.forEach { opinion ->
                    put(org.json.JSONObject().apply {
                        put("member", opinion.displayName)
                        put("functionalName", opinion.agentName)
                        put("role", opinion.role)
                        put("vote", opinion.vote.name)
                        put("sentiment", opinion.sentiment)
                        put("confidence", opinion.confidence)
                        put("reasoning", opinion.reasoning)
                        put("indicators", org.json.JSONArray(opinion.keyIndicators))
                    })
                }
            })
            if (dissenterReasons.isNotEmpty()) {
                put("dissenters", org.json.JSONArray(dissenterReasons))
            }
        }.toString(2)
    }
}

data class MarketContext(
    val symbol: String,
    val currentPrice: Double,
    val opens: List<Double>,
    val highs: List<Double>,
    val lows: List<Double>,
    val closes: List<Double>,
    val volumes: List<Double>,
    val timeframe: String = "1h",
    // Macro context (V5.17.0)
    val macroSentiment: String? = null,
    val macroScore: Double? = null,
    val macroRiskLevel: String? = null,
    val upcomingHighImpactEvents: Int? = null,
    val macroNarrative: String? = null,
    // V5.17.0: Sentiment data (populated when SentimentEngine is wired)
    // When null, buildDQNFeatures() derives proxies from OHLCV data
    val socialVolume: Double? = null,     // Social media mention volume (0.0 = none, 1.0 = average, 5.0 = viral)
    val newsImpactScore: Double? = null   // News sentiment impact (-1.0 to +1.0)
)

// ============================================================================
// V5.17.0: DQN FEATURE VECTOR BUILDER
// Computes full 30-feature EnhancedFeatureVector from MarketContext
// Board members use this instead of lossy MarketState discretization
// ============================================================================

/**
 * Builds a rich EnhancedFeatureVector from MarketContext OHLCV data.
 * V5.17.0: All 19 base features computed from real indicators (not reconstructed from buckets).
 * Combined with 10 cross-feature interactions + position = 30 features total.
 * 
 * @return Pair(EnhancedFeatureVector, currentPosition) ready for DQN direct methods
 */
fun buildDQNFeatures(context: MarketContext, currentPosition: Double = 0.0): Pair<EnhancedFeatureVector, Double> {
    val closes = context.closes
    val highs = context.highs
    val lows = context.lows
    val volumes = context.volumes
    val price = context.currentPrice
    
    // Compute real indicators from OHLCV
    val ema20 = if (closes.size >= 20) TrendIndicators.ema(closes, 20) else price
    val ema50 = if (closes.size >= 50) TrendIndicators.ema(closes, 50) else price
    val rsi = if (closes.size >= 14) MomentumIndicators.rsi(closes, 14) else 50.0
    val macdResult = if (closes.size >= 26) MomentumIndicators.macd(closes) else null
    val stochResult = if (highs.size >= 14 && lows.size >= 14 && closes.size >= 14)
        MomentumIndicators.stochastic(highs, lows, closes) else null
    val williamsR = if (highs.size >= 14 && lows.size >= 14 && closes.size >= 14)
        MomentumIndicators.williamsR(highs, lows, closes) else -50.0
    val roc = if (closes.size >= 12) MomentumIndicators.roc(closes) else 0.0
    val atr = if (highs.size >= 14 && lows.size >= 14 && closes.size >= 14)
        VolatilityVolumeIndicators.atr(highs, lows, closes) else 0.0
    val bbResult = if (closes.size >= 20)
        VolatilityVolumeIndicators.bollingerBands(closes) else null
    
    // Derive trend from EMA relationship
    val trend = when {
        ema20 > ema50 * 1.02 -> 0.8
        ema20 > ema50 -> 0.4
        ema20 < ema50 * 0.98 -> -0.8
        ema20 < ema50 -> -0.4
        else -> 0.0
    }
    
    // Normalize volatility (ATR as % of price)
    val volatility = if (price > 0) (atr / price).coerceIn(0.0, 1.0) else 0.5
    
    // Volume profile (current vs average)
    val avgVolume = if (volumes.isNotEmpty()) volumes.average() else 1.0
    val volumeProfile = if (avgVolume > 0 && volumes.isNotEmpty()) 
        (volumes.last() / avgVolume).coerceIn(0.1, 5.0) else 1.0
    
    // Bollinger Band position (-1 to +1)
    val bbPosition = if (bbResult != null && bbResult.upper > bbResult.lower) {
        val range = bbResult.upper - bbResult.lower
        ((price - bbResult.middle) / (range / 2.0)).coerceIn(-1.0, 1.0)
    } else 0.0
    
    // Composite momentum score
    val rsiScore = (rsi - 50.0) / 50.0
    val macdScore = ((macdResult?.macd ?: 0.0) / 10.0).coerceIn(-1.0, 1.0)
    val rocScore = (roc / 10.0).coerceIn(-1.0, 1.0)
    val momentumScore = (rsiScore * 0.4 + macdScore * 0.35 + rocScore * 0.25).coerceIn(-1.0, 1.0)
    
    // Macro sentiment (if available from context)
    val sentimentScore = (context.macroScore ?: 0.0).coerceIn(-1.0, 1.0)
    val fearGreedIndex = ((sentimentScore + 1.0) / 2.0 * 100.0).coerceIn(0.0, 100.0)
    
    // V5.17.0: Social volume — use real data if wired, else derive proxy from volume spikes
    // Volume spikes strongly correlate with social media attention in crypto
    val socialVolume = context.socialVolume ?: run {
        if (volumes.size >= 20) {
            val recentAvg = volumes.takeLast(5).average()
            val longerAvg = volumes.takeLast(20).average()
            if (longerAvg > 0) (recentAvg / longerAvg).coerceIn(0.0, 5.0) else 0.0
        } else 0.0
    }
    
    // V5.17.0: News impact — use real data if wired, else derive proxy from price shocks
    // Large sudden price moves almost always correlate with news events
    val newsImpact = context.newsImpactScore ?: run {
        if (closes.size >= 10 && atr > 0 && price > 0) {
            // Recent price change as multiple of ATR (large moves = news-driven)
            val recentReturn = (closes.last() - closes[closes.size - 2]) / closes[closes.size - 2]
            val atrPercent = atr / price
            if (atrPercent > 0) (recentReturn / atrPercent).coerceIn(-1.0, 1.0) else 0.0
        } else 0.0
    }
    
    val features = EnhancedFeatureVector(
        marketPrice = price,
        trend = trend,
        volatility = volatility,
        volumeProfile = volumeProfile,
        ema20 = ema20,
        ema50 = ema50,
        rsi = rsi,
        macd = macdResult?.macd ?: 0.0,
        macdHistogram = macdResult?.histogram ?: 0.0,
        momentumScore = momentumScore,
        roc = roc,
        stochastic = stochResult?.k ?: 50.0,
        williamsR = williamsR,
        atr = atr,
        bollingerBandPosition = bbPosition,
        sentimentScore = sentimentScore,
        fearGreedIndex = fearGreedIndex,
        socialVolume = socialVolume,
        newsImpact = newsImpact
    )
    
    return Pair(features, currentPosition.coerceIn(-1.0, 1.0))
}

// ============================================================================
// EXPERT ADVISERS
// ============================================================================

interface BoardMember {
    val name: String        // Functional name (e.g., "TrendFollower")
    val displayName: String // Persona name (e.g., "Arthur")
    val role: String        // Role description
    val weight: Double      // Voting weight (total = 1.0)
    fun analyze(context: MarketContext): AgentOpinion
}

/**
 * 1. Arthur (CTO) - Trend Follower
 * Captures long-duration momentum moves.
 * Weight: 12.5% (Octagon)
 * V5.17.0: Now DQN-augmented - blends learned Q-values with EMA/MACD/ADX analysis
 */
class TrendFollower(private val dqn: DQNTrader? = null) : BoardMember {
    override val name = "TrendFollower"
    override val displayName = "Arthur"
    override val role = "CTO - Trend Analysis"
    override val weight = 0.125  // 12.5% in Octagon
    
    override fun analyze(context: MarketContext): AgentOpinion {
        val closes = context.closes
        val highs = context.highs
        val lows = context.lows
        
        val ema10 = TrendIndicators.ema(closes, 10)
        val ema30 = TrendIndicators.ema(closes, 30)
        val macd = MomentumIndicators.macd(closes)
        val adx = TrendIndicators.adx(highs, lows, closes)
        val supertrend = TrendIndicators.superTrend(highs, lows, closes)
        
        var bullishScore = 0
        var bearishScore = 0
        val indicators = mutableListOf<String>()
        
        // EMA Cross
        if (ema10 > ema30) {
            bullishScore += 2
            indicators.add("EMA10 > EMA30")
        } else {
            bearishScore += 2
            indicators.add("EMA10 < EMA30")
        }
        
        // MACD
        if (macd.histogram > 0) {
            bullishScore += 1
            indicators.add("MACD Bullish")
        } else {
            bearishScore += 1
            indicators.add("MACD Bearish")
        }
        
        // ADX Trend Strength
        if (adx.adx > 25) {
            if (adx.plusDI > adx.minusDI) {
                bullishScore += 2
                indicators.add("ADX Strong Uptrend")
            } else {
                bearishScore += 2
                indicators.add("ADX Strong Downtrend")
            }
        }
        
        // SuperTrend
        if (supertrend.trend == "up") {
            bullishScore += 1
            indicators.add("SuperTrend Up")
        }
        
        // Calculate base sentiment from technical indicators
        val technicalSentiment = (bullishScore - bearishScore) / 6.0
        
        // V5.17.0: Blend with DQN learned patterns if available
        val (finalSentiment, finalConfidence, dqnInsight) = if (dqn != null) {
            try {
                
                val (dqnFeatures, dqnPosition) = buildDQNFeatures(context)
                val dqnSentiment = dqn.getLearnedSentimentDirect(dqnFeatures, dqnPosition)
                val dqnConfidence = dqn.getDecisionConfidenceDirect(dqnFeatures, dqnPosition)
                
                // Blend: 60% technical indicators, 40% DQN learned patterns
                val blendedSentiment = (technicalSentiment * 0.6) + (dqnSentiment * 0.4)
                val blendedConfidence = (abs(technicalSentiment) * 0.6) + (dqnConfidence * 0.4)
                
                val insight = "DQN learned ${String.format("%+.2f", dqnSentiment)} (${String.format("%.1f", dqnConfidence * 100)}% confident)"
                indicators.add(insight)
                
                Triple(blendedSentiment, blendedConfidence, insight)
            } catch (e: Exception) {
                // Fallback to pure technical if DQN fails
                Triple(technicalSentiment, abs(technicalSentiment), "DQN unavailable")
            }
        } else {
            // No DQN - use pure technical analysis
            Triple(technicalSentiment, abs(technicalSentiment), null)
        }
        
        val vote = sentimentToVote(finalSentiment)
        
        val reasoning = buildString {
            append("Trend analysis shows ${if (finalSentiment > 0) "bullish" else "bearish"} momentum")
            if (dqnInsight != null) {
                append(". $dqnInsight")
            }
        }
        
        return AgentOpinion(
            agentName = name,
            displayName = displayName,
            role = role,
            vote = vote,
            sentiment = finalSentiment,
            confidence = finalConfidence,
            reasoning = reasoning,
            keyIndicators = indicators
        )
    }
}

/**
 * 2. Helena (CRO) - Mean Reverter
 * Snipes overextended moves for reversion plays.
 * Weight: 12.5% (Octagon)
 * V5.17.0: Now DQN-augmented - blends learned patterns with RSI/BB mean reversion signals
 */
class MeanReverter(private val dqn: DQNTrader? = null) : BoardMember {
    override val name = "MeanReverter"
    override val displayName = "Helena"
    override val role = "Chief Risk Officer (CRO)"
    override val weight = 0.125  // 12.5% in Octagon
    
    override fun analyze(context: MarketContext): AgentOpinion {
        val closes = context.closes
        
        val rsi = MomentumIndicators.rsi(closes)
        val bb = VolatilityIndicators.bollingerBands(closes)
        val stoch = MomentumIndicators.stochastic(context.highs, context.lows, closes)
        val williamsR = MomentumIndicators.williamsR(context.highs, context.lows, closes)
        
        var bullishScore = 0
        var bearishScore = 0
        val indicators = mutableListOf<String>()
        
        // RSI Oversold/Overbought
        when {
            rsi < 30 -> {
                bullishScore += 3
                indicators.add("RSI Oversold (${rsi.toInt()})")
            }
            rsi > 70 -> {
                bearishScore += 3
                indicators.add("RSI Overbought (${rsi.toInt()})")
            }
        }
        
        // Bollinger %B
        when {
            bb.percentB < 0.2 -> {
                bullishScore += 2
                indicators.add("BB %B < 20%")
            }
            bb.percentB > 0.8 -> {
                bearishScore += 2
                indicators.add("BB %B > 80%")
            }
        }
        
        // Stochastic
        when {
            stoch.k < 20 -> {
                bullishScore += 1
                indicators.add("Stoch Oversold")
            }
            stoch.k > 80 -> {
                bearishScore += 1
                indicators.add("Stoch Overbought")
            }
        }
        
        // Williams %R
        when {
            williamsR < -80 -> {
                bullishScore += 1
                indicators.add("W%R Oversold")
            }
            williamsR > -20 -> {
                bearishScore += 1
                indicators.add("W%R Overbought")
            }
        }
        
        // Calculate base sentiment from mean reversion indicators
        val technicalSentiment = (bullishScore - bearishScore) / 7.0
        
        // V5.17.0: Blend with DQN learned reversal patterns if available
        val (finalSentiment, finalConfidence, dqnInsight) = if (dqn != null) {
            try {
                
                val (dqnFeatures, dqnPosition) = buildDQNFeatures(context)
                val dqnSentiment = dqn.getLearnedSentimentDirect(dqnFeatures, dqnPosition)
                val dqnConfidence = dqn.getDecisionConfidenceDirect(dqnFeatures, dqnPosition)
                
                // Blend: 60% technical indicators, 40% DQN learned patterns
                // DQN may have learned that some "oversold" conditions keep falling
                val blendedSentiment = (technicalSentiment * 0.6) + (dqnSentiment * 0.4)
                val blendedConfidence = (abs(technicalSentiment) * 0.6) + (dqnConfidence * 0.4)
                
                val insight = "DQN learned ${String.format("%+.2f", dqnSentiment)} (${String.format("%.1f", dqnConfidence * 100)}% confident)"
                indicators.add(insight)
                
                Triple(blendedSentiment, blendedConfidence, insight)
            } catch (e: Exception) {
                Triple(technicalSentiment, abs(technicalSentiment), "DQN unavailable")
            }
        } else {
            Triple(technicalSentiment, abs(technicalSentiment), null)
        }
        
        val vote = sentimentToVote(finalSentiment)
        
        val reasoning = buildString {
            append("Mean reversion signals ${if (finalSentiment > 0) "oversold bounce" else "overbought pullback"}")
            if (dqnInsight != null) {
                append(". $dqnInsight")
            }
        }
        
        return AgentOpinion(
            agentName = name,
            displayName = displayName,
            role = role,
            vote = vote,
            sentiment = finalSentiment,
            confidence = finalConfidence,
            reasoning = reasoning,
            keyIndicators = indicators
        )
    }
}

/**
 * 3. Sentinel (CCO) - Volatility Trader
 * Trades volatility compression/expansion cycles.
 * Weight: 10% - Vol trading is a specialist strategy requiring specific conditions.
 */
/**
 * 3. Sentinel (CCO) - Volatility Trader
 * Exploits volatility expansion/contraction cycles.
 * Weight: 12.5% (Octagon) - CASTING VOTE (100% accuracy Q1 2025)
 * V5.17.0: Now DQN-augmented
 */
class VolatilityTrader(private val dqn: DQNTrader? = null) : BoardMember {
    override val name = "VolatilityTrader"
    override val displayName = "Sentinel"
    override val role = "Chief Compliance Officer (CCO)"
    override val weight = 0.125  // 12.5% in Octagon (8 active members)
    
    override fun analyze(context: MarketContext): AgentOpinion {
        val closes = context.closes
        val highs = context.highs
        val lows = context.lows
        
        val atr = VolatilityIndicators.atr(highs, lows, closes)
        val bb = VolatilityIndicators.bollingerBands(closes)
        val keltner = VolatilityIndicators.keltnerChannels(highs, lows, closes)
        val histVol = VolatilityIndicators.historicalVolatility(closes)
        
        var bullishScore = 0
        var bearishScore = 0
        val indicators = mutableListOf<String>()
        
        // Bollinger Squeeze (inside Keltner)
        val isSqueezing = bb.lower > keltner.lower && bb.upper < keltner.upper
        if (isSqueezing) {
            indicators.add("Volatility Squeeze - Breakout Imminent")
            // Direction depends on price position
            if (context.currentPrice > (bb.upper + bb.lower) / 2) {
                bullishScore += 2
            } else {
                bearishScore += 2
            }
        }
        
        // BB Bandwidth expansion
        if (bb.bandwidth > 20) {
            indicators.add("High Volatility (BB Width: ${bb.bandwidth.toInt()}%)")
        }
        
        // Price vs Keltner
        when {
            context.currentPrice > keltner.upper -> {
                bullishScore += 1
                indicators.add("Above Keltner Upper")
            }
            context.currentPrice < keltner.lower -> {
                bearishScore += 1
                indicators.add("Below Keltner Lower")
            }
        }
        
        // Calculate base sentiment from volatility indicators
        val technicalSentiment = (bullishScore - bearishScore) / 3.0
        
        // V5.17.0: Blend with DQN learned volatility exploitation patterns
        val (finalSentiment, finalConfidence, dqnInsight) = if (dqn != null) {
            try {
                
                val (dqnFeatures, dqnPosition) = buildDQNFeatures(context)
                val dqnSentiment = dqn.getLearnedSentimentDirect(dqnFeatures, dqnPosition)
                val dqnConfidence = dqn.getDecisionConfidenceDirect(dqnFeatures, dqnPosition)
                
                // Blend: 60% technical indicators, 40% DQN learned patterns
                // DQN may have learned which volatility breakouts actually follow through
                val blendedSentiment = (technicalSentiment * 0.6) + (dqnSentiment * 0.4)
                // Boost confidence if DQN is very confident in volatile markets
                val blendedConfidence = ((abs(technicalSentiment) * 0.7 * 0.6) + (dqnConfidence * 0.4)).coerceIn(0.0, 1.0)
                
                val insight = "DQN learned ${String.format("%+.2f", dqnSentiment)} (${String.format("%.1f", dqnConfidence * 100)}% confident)"
                indicators.add(insight)
                
                Triple(blendedSentiment, blendedConfidence, insight)
            } catch (e: Exception) {
                Triple(technicalSentiment, abs(technicalSentiment) * 0.7, "DQN unavailable")
            }
        } else {
            Triple(technicalSentiment, abs(technicalSentiment) * 0.7, null)
        }
        
        val vote = sentimentToVote(finalSentiment)
        
        val reasoning = buildString {
            append("Volatility ${if (isSqueezing) "squeeze" else "expansion"} detected")
            if (dqnInsight != null) {
                append(". $dqnInsight")
            }
        }
        
        return AgentOpinion(
            agentName = name,
            displayName = displayName,
            role = role,
            vote = vote,
            sentiment = finalSentiment,
            confidence = finalConfidence,
            reasoning = reasoning,
            keyIndicators = indicators
        )
    }
}

/**
 * 4. Oracle (CDO) - Sentiment Analyst
 * Analyzes social & market momentum signals.
 * Weight: 10% - Sentiment is often lagging/noisy, requires confirmation.
 * TODO: Wire to real sentiment feeds (Fear & Greed Index, social APIs)
 */
/**
 * 4. Oracle (CDO) - Sentiment Analyst
 * Gauges crowd psychology and contrarian opportunities.
 * Weight: 12.5% (Octagon)
 * V5.17.0: Now DQN-augmented
 * V5.18.21: Now uses real SentimentEngine data (Fear & Greed Index + CoinGecko)
 */
class SentimentAnalyst(
    private val dqn: DQNTrader? = null,
    private val sentimentEngine: SentimentEngine? = null  // V5.18.21: Real sentiment data source
) : BoardMember {
    override val name = "SentimentAnalyst"
    override val displayName = "Oracle"
    override val role = "CDO - Market Intelligence"
    override val weight = 0.125  // 12.5% in Octagon (8 active members)
    
    override fun analyze(context: MarketContext): AgentOpinion {
        val closes = context.closes
        val volumes = context.volumes
        
        // V5.18.21: TRY TO GET REAL SENTIMENT FIRST ✅
        val realSentimentData = sentimentEngine?.getSentiment(context.symbol)
        
        var bullishScore = 0
        var bearishScore = 0
        val indicators = mutableListOf<String>()
        
        // Calculate base sentiment score
        val baseSentiment: Double
        val sentimentConfidence: Double
        
        if (realSentimentData != null && realSentimentData.providerConfidence > 0.3) {
            // ✅ USE REAL SENTIMENT from scraping (Fear & Greed Index + CoinGecko)
            baseSentiment = realSentimentData.score  // Already -1.0 to +1.0 scale
            sentimentConfidence = realSentimentData.providerConfidence
            
            indicators.add("Real Sentiment: ${String.format("%+.2f", baseSentiment)}")
            indicators.add("Provider: ${realSentimentData.providerName}")
            indicators.add("Volume: ${realSentimentData.volume} mentions")
            
            // Add macro adjustment info if present
            if (realSentimentData.macroAdjustment != 0.0) {
                indicators.add("Macro Adj: ${String.format("%+.2f", realSentimentData.macroAdjustment)}")
            }
            
            println("Oracle: Using REAL sentiment for ${context.symbol}: " +
                    "score=${String.format("%+.2f", baseSentiment)}, " +
                    "confidence=${String.format("%.1f", sentimentConfidence * 100)}%, " +
                    "provider=${realSentimentData.providerName}")
            
        } else {
            // ❌ FALLBACK: Use momentum-based sentiment proxy (old behavior)
            val momentum = MomentumIndicators.momentum(closes, 10)
            val roc = MomentumIndicators.roc(closes, 12)
            val ao = MomentumIndicators.awesomeOscillator(context.highs, context.lows)
            
            // Momentum
            when {
                momentum > 0 -> {
                    bullishScore += 1
                    indicators.add("Positive Momentum")
                }
                momentum < 0 -> {
                    bearishScore += 1
                    indicators.add("Negative Momentum")
                }
            }
            
            // ROC
            when {
                roc > 5 -> {
                    bullishScore += 2
                    indicators.add("Strong ROC (+${roc.toInt()}%)")
                }
                roc < -5 -> {
                    bearishScore += 2
                    indicators.add("Weak ROC (${roc.toInt()}%)")
                }
            }
            
            // Awesome Oscillator
            when {
                ao > 0 -> {
                    bullishScore += 1
                    indicators.add("AO Bullish")
                }
                ao < 0 -> {
                    bearishScore += 1
                    indicators.add("AO Bearish")
                }
            }
            
            baseSentiment = (bullishScore - bearishScore) / 4.0
            sentimentConfidence = abs(baseSentiment) * 0.8
            
            indicators.add("[Fallback: Momentum Proxy]")
            
            if (sentimentEngine == null) {
                println("Oracle: No SentimentEngine available - using momentum proxy")
            } else {
                println("Oracle: Real sentiment unavailable (low confidence or no data) - using momentum proxy")
            }
        }
        
        // V5.17.0: Blend with DQN learned crowd psychology patterns
        val (finalSentiment, finalConfidence, dqnInsight) = if (dqn != null) {
            try {
                val (dqnFeatures, dqnPosition) = buildDQNFeatures(context)
                val dqnSentiment = dqn.getLearnedSentimentDirect(dqnFeatures, dqnPosition)
                val dqnConfidence = dqn.getDecisionConfidenceDirect(dqnFeatures, dqnPosition)
                
                // Blend: 60% base sentiment (real or proxy), 40% DQN learned patterns
                // DQN may have learned contrarian plays when sentiment is extreme
                val blendedSentiment = (baseSentiment * 0.6) + (dqnSentiment * 0.4)
                val blendedConfidence = ((sentimentConfidence * 0.6) + (dqnConfidence * 0.4)).coerceIn(0.0, 1.0)
                
                val insight = "DQN learned ${String.format("%+.2f", dqnSentiment)} (${String.format("%.1f", dqnConfidence * 100)}% confident)"
                indicators.add(insight)
                
                Triple(blendedSentiment, blendedConfidence, insight)
            } catch (e: Exception) {
                Triple(baseSentiment, sentimentConfidence, "DQN unavailable")
            }
        } else {
            Triple(baseSentiment, sentimentConfidence, null)
        }
        
        val vote = sentimentToVote(finalSentiment)
        
        val reasoning = buildString {
            append("Market sentiment is ${if (finalSentiment > 0) "positive" else "negative"}")
            if (dqnInsight != null) {
                append(". $dqnInsight")
            }
        }
        
        return AgentOpinion(
            agentName = name,
            displayName = displayName,
            role = role,
            vote = vote,
            sentiment = finalSentiment,
            confidence = finalConfidence,
            reasoning = reasoning,
            keyIndicators = indicators
        )
    }
}

/**
 * 5. Sentinel (CCO) - On-Chain Analyst
 * Monitors whale accumulation/distribution patterns.
 * Weight: 13% - On-chain data is a leading indicator in crypto.
 * TODO: Wire to real on-chain data (Glassnode, Nansen, exchange flows)
 */
/**
 * 5. Nexus (COO) - On-Chain Analyst
 * Monitors on-chain activity and whale movements.
 * Weight: 12.5% (Octagon)
 * V5.17.0: Now DQN-augmented
 */
class OnChainAnalyst(private val dqn: DQNTrader? = null) : BoardMember {
    override val name = "OnChainAnalyst"
    override val displayName = "Nexus"
    override val role = "Chief Operating Officer (COO)"
    override val weight = 0.125  // 12.5% in Octagon (8 active members)
    
    override fun analyze(context: MarketContext): AgentOpinion {
        val closes = context.closes
        val volumes = context.volumes
        val highs = context.highs
        val lows = context.lows
        
        // Volume-based on-chain proxies
        val obv = VolumeIndicators.obv(closes, volumes)
        val cmf = VolumeIndicators.cmf(highs, lows, closes, volumes)
        val mfi = VolumeIndicators.mfi(highs, lows, closes, volumes)
        val adl = VolumeIndicators.adl(highs, lows, closes, volumes)
        
        var bullishScore = 0
        var bearishScore = 0
        val indicators = mutableListOf<String>()
        
        // CMF (Chaikin Money Flow)
        when {
            cmf > 0.1 -> {
                bullishScore += 2
                indicators.add("CMF Accumulation")
            }
            cmf < -0.1 -> {
                bearishScore += 2
                indicators.add("CMF Distribution")
            }
        }
        
        // MFI
        when {
            mfi < 30 -> {
                bullishScore += 2
                indicators.add("MFI Oversold")
            }
            mfi > 70 -> {
                bearishScore += 2
                indicators.add("MFI Overbought")
            }
        }
        
        // Calculate base sentiment from on-chain/volume indicators
        val technicalSentiment = (bullishScore - bearishScore) / 4.0
        
        // V5.17.0: Blend with DQN learned whale/accumulation patterns
        val (finalSentiment, finalConfidence, dqnInsight) = if (dqn != null) {
            try {
                
                val (dqnFeatures, dqnPosition) = buildDQNFeatures(context)
                val dqnSentiment = dqn.getLearnedSentimentDirect(dqnFeatures, dqnPosition)
                val dqnConfidence = dqn.getDecisionConfidenceDirect(dqnFeatures, dqnPosition)
                
                // Blend: 60% technical indicators, 40% DQN learned patterns
                // DQN may have learned when volume accumulation actually leads to breakouts
                val blendedSentiment = (technicalSentiment * 0.6) + (dqnSentiment * 0.4)
                val blendedConfidence = (abs(technicalSentiment) * 0.6) + (dqnConfidence * 0.4)
                
                val insight = "DQN learned ${String.format("%+.2f", dqnSentiment)} (${String.format("%.1f", dqnConfidence * 100)}% confident)"
                indicators.add(insight)
                
                Triple(blendedSentiment, blendedConfidence, insight)
            } catch (e: Exception) {
                Triple(technicalSentiment, abs(technicalSentiment), "DQN unavailable")
            }
        } else {
            Triple(technicalSentiment, abs(technicalSentiment), null)
        }
        
        val vote = sentimentToVote(finalSentiment)
        
        val reasoning = buildString {
            append("On-chain shows ${if (finalSentiment > 0) "accumulation" else "distribution"}")
            if (dqnInsight != null) {
                append(". $dqnInsight")
            }
        }
        
        return AgentOpinion(
            agentName = name,
            displayName = displayName,
            role = role,
            vote = vote,
            sentiment = finalSentiment,
            confidence = finalConfidence,
            reasoning = reasoning,
            keyIndicators = indicators
        )
    }
}

/**
 * 6. Marcus (CIO) - Macro Strategist
 * Analyzes cross-asset correlations and macro context.
 * Weight: 12% - Macro context prevents regime errors (risk-off vs risk-on).
 */
/**
 * 6. Marcus (CIO) - Macro Strategist
 * Integrates global macro context into crypto trades.
 * Weight: 12.5% (Octagon)
 * V5.17.0: Now DQN-augmented
 */
class MacroStrategist(private val dqn: DQNTrader? = null) : BoardMember {
    override val name = "MacroStrategist"
    override val displayName = "Marcus"
    override val role = "Chief Investment Officer (CIO)"
    override val weight = 0.125  // 12.5% in Octagon (8 active members)
    
    override fun analyze(context: MarketContext): AgentOpinion {
        val closes = context.closes
        
        var bullishScore = 0
        var bearishScore = 0
        val indicators = mutableListOf<String>()
        
        // === REAL MACRO DATA (V5.17.0) ===
        // If macro context is available, use it as primary signal
        context.macroSentiment?.let { macroSent ->
            context.macroScore?.let { macroScore ->
                when {
                    macroSent == "HAWKISH" && macroScore > 0.3 -> {
                        bearishScore += 2  // Hawkish = tightening = bearish for risk assets
                        indicators.add("Fed Hawkish (score: ${String.format("%.2f", macroScore)})")
                    }
                    macroSent == "DOVISH" && macroScore < -0.3 -> {
                        bullishScore += 2  // Dovish = easing = bullish for risk assets
                        indicators.add("Fed Dovish (score: ${String.format("%.2f", macroScore)})")
                    }
                    else -> indicators.add("Macro Neutral")
                }
            }
        }
        
        // Risk level adjustment
        context.macroRiskLevel?.let { risk ->
            when (risk) {
                "EXTREME" -> { bearishScore += 3; indicators.add("EXTREME macro risk - reduce exposure") }
                "HIGH" -> { bearishScore += 2; indicators.add("HIGH macro risk - caution") }
                "ELEVATED" -> { bearishScore += 1; indicators.add("Elevated macro risk") }
                else -> { }
            }
        }
        
        // Upcoming high-impact events
        if ((context.upcomingHighImpactEvents ?: 0) >= 2) {
            indicators.add("${context.upcomingHighImpactEvents} high-impact events in 48h")
            // Don't change score, but flag for position sizing
        }
        
        // === TECHNICAL MACRO STRUCTURE ===
        val ema50 = TrendIndicators.ema(closes, 50)
        val ema200 = if (closes.size >= 200) TrendIndicators.ema(closes, 200) else TrendIndicators.ema(closes, closes.size / 2)
        
        // Golden/Death Cross
        if (ema50 > ema200) {
            bullishScore += 2
            indicators.add("Golden Cross (EMA50 > EMA200)")
        } else {
            bearishScore += 2
            indicators.add("Death Cross (EMA50 < EMA200)")
        }
        
        // Price vs long-term average
        if (context.currentPrice > ema200) {
            bullishScore += 1
            indicators.add("Above 200 EMA")
        } else {
            bearishScore += 1
            indicators.add("Below 200 EMA")
        }
        
        // Calculate base sentiment from macro indicators
        val maxScore = 8.0  // Updated for macro inputs
        val technicalSentiment = (bullishScore - bearishScore) / maxScore
        
        // V5.17.0: Blend with DQN learned macro regime patterns
        val (finalSentiment, finalConfidence, dqnInsight) = if (dqn != null) {
            try {
                
                val (dqnFeatures, dqnPosition) = buildDQNFeatures(context)
                val dqnSentiment = dqn.getLearnedSentimentDirect(dqnFeatures, dqnPosition)
                val dqnConfidence = dqn.getDecisionConfidenceDirect(dqnFeatures, dqnPosition)
                
                // Blend: 60% technical indicators, 40% DQN learned patterns
                // DQN may have learned which macro conditions actually impact crypto
                val blendedSentiment = (technicalSentiment * 0.6) + (dqnSentiment * 0.4)
                val baseConfidence = minOf(abs(technicalSentiment) + 0.2, 1.0)
                val blendedConfidence = (baseConfidence * 0.6) + (dqnConfidence * 0.4)
                
                val insight = "DQN learned ${String.format("%+.2f", dqnSentiment)} (${String.format("%.1f", dqnConfidence * 100)}% confident)"
                indicators.add(insight)
                
                Triple(blendedSentiment, blendedConfidence, insight)
            } catch (e: Exception) {
                Triple(technicalSentiment, minOf(abs(technicalSentiment) + 0.2, 1.0), "DQN unavailable")
            }
        } else {
            Triple(technicalSentiment, minOf(abs(technicalSentiment) + 0.2, 1.0), null)
        }
        
        val vote = sentimentToVote(finalSentiment)
        
        val reasoning = buildString {
            append("Macro structure is ${if (finalSentiment > 0) "bullish" else if (finalSentiment < 0) "bearish" else "neutral"}")
            context.macroNarrative?.let { append(". $it") }
            if (dqnInsight != null) {
                append(". $dqnInsight")
            }
        }
        
        return AgentOpinion(
            agentName = name,
            displayName = displayName,
            role = role,
            vote = vote,
            sentiment = finalSentiment,
            confidence = finalConfidence,
            reasoning = reasoning,
            keyIndicators = indicators
        )
    }
}

/**
 * 7. Cipher (CSO) - Pattern Recognizer
 * Identifies chart patterns & breakout setups.
 * Weight: 13% - Technical patterns confirm trade setups.
 */
/**
 * 7. Cipher (CSO) - Pattern Recognizer
 * Detects chart patterns and technical formations.
 * Weight: 12.5% (Octagon)
 * V5.17.0: Now DQN-augmented
 */
class PatternRecognizer(private val dqn: DQNTrader? = null) : BoardMember {
    override val name = "PatternRecognizer"
    override val displayName = "Cipher"
    override val role = "Chief Security Officer (CSO)"
    override val weight = 0.125  // 12.5% in Octagon (8 active members)
    
    override fun analyze(context: MarketContext): AgentOpinion {
        val highs = context.highs
        val lows = context.lows
        
        // Use pattern detection from SignalGenerator
        val patterns = com.miwealth.sovereignvantage.core.signals.PatternRecognizer.detectAllPatterns(highs, lows)
        
        var bullishScore = 0
        var bearishScore = 0
        val indicators = mutableListOf<String>()
        
        for (pattern in patterns) {
            when (pattern.direction) {
                SignalDirection.LONG -> {
                    bullishScore += (pattern.reliability / 25)
                    indicators.add("${pattern.name} (${pattern.reliability}%)")
                }
                SignalDirection.SHORT -> {
                    bearishScore += (pattern.reliability / 25)
                    indicators.add("${pattern.name} (${pattern.reliability}%)")
                }
                else -> {}
            }
        }
        
        // Support/Resistance analysis
        val recent20High = highs.takeLast(20).max() ?: context.currentPrice
        val recent20Low = lows.takeLast(20).min() ?: context.currentPrice
        
        if (context.currentPrice > recent20High * 0.98) {
            bullishScore += 1
            indicators.add("Near 20-bar High (Breakout?)")
        }
        if (context.currentPrice < recent20Low * 1.02) {
            bearishScore += 1
            indicators.add("Near 20-bar Low (Breakdown?)")
        }
        
        // Calculate base sentiment from pattern recognition
        val maxScore = (patterns.size * 4 + 2).coerceAtLeast(1)
        val technicalSentiment = (bullishScore - bearishScore).toDouble() / maxScore
        
        // V5.17.0: Blend with DQN learned pattern reliability
        val (finalSentiment, finalConfidence, dqnInsight) = if (dqn != null) {
            try {
                
                val (dqnFeatures, dqnPosition) = buildDQNFeatures(context)
                val dqnSentiment = dqn.getLearnedSentimentDirect(dqnFeatures, dqnPosition)
                val dqnConfidence = dqn.getDecisionConfidenceDirect(dqnFeatures, dqnPosition)
                
                // Blend: 60% technical indicators, 40% DQN learned patterns
                // DQN may have learned which chart patterns actually follow through
                val blendedSentiment = (technicalSentiment * 0.6) + (dqnSentiment * 0.4)
                val blendedConfidence = (abs(technicalSentiment) * 0.6) + (dqnConfidence * 0.4)
                
                val insight = "DQN learned ${String.format("%+.2f", dqnSentiment)} (${String.format("%.1f", dqnConfidence * 100)}% confident)"
                indicators.add(insight)
                
                Triple(blendedSentiment, blendedConfidence, insight)
            } catch (e: Exception) {
                Triple(technicalSentiment, abs(technicalSentiment), "DQN unavailable")
            }
        } else {
            Triple(technicalSentiment, abs(technicalSentiment), null)
        }
        
        val vote = sentimentToVote(finalSentiment)
        
        val reasoning = buildString {
            append(if (patterns.isEmpty()) "No clear patterns" else "Detected ${patterns.size} pattern(s)")
            if (dqnInsight != null) {
                append(". $dqnInsight")
            }
        }
        
        return AgentOpinion(
            agentName = name,
            displayName = displayName,
            role = role,
            vote = vote,
            sentiment = finalSentiment,
            confidence = finalConfidence,
            reasoning = reasoning,
            keyIndicators = indicators
        )
    }
}

/**
 * 8. Aegis (Chief Defense) - Liquidity Hunter
 * "Blood in the streets" buying - capitulation detection.
 * Weight: 15% - Capitulation buying is high-conviction with extreme setups.
 */
/**
 * 8. Aegis (Chief Defense) - Liquidity Hunter
 * Identifies capitulation bottoms and euphoric tops.
 * Weight: 12.5% (Octagon)
 * V5.17.0: Now DQN-augmented
 */
class LiquidityHunter(private val dqn: DQNTrader? = null) : BoardMember {
    override val name = "LiquidityHunter"
    override val displayName = "Aegis"
    override val role = "Chief Defense - Liquidity"
    override val weight = 0.125  // 12.5% in Octagon (8 active members)
    
    override fun analyze(context: MarketContext): AgentOpinion {
        val closes = context.closes
        val highs = context.highs
        val lows = context.lows
        val volumes = context.volumes
        
        val rsi = MomentumIndicators.rsi(closes)
        
        // Calculate drawdown from recent high
        val recentHigh = closes.takeLast(50).max() ?: context.currentPrice
        val drawdownPercent = ((recentHigh - context.currentPrice) / recentHigh) * 100
        
        // Volume spike detection
        val avgVolume = volumes.takeLast(20).average()
        val currentVolume = volumes.last()
        val volumeRatio = currentVolume / avgVolume
        
        var bullishScore = 0
        var bearishScore = 0
        val indicators = mutableListOf<String>()
        
        // Extreme oversold with RSI < 25
        if (rsi < 25) {
            bullishScore += 3
            indicators.add("Extreme Oversold (RSI: ${rsi.toInt()})")
        }
        
        // Significant drawdown (blood in streets)
        when {
            drawdownPercent > 30 -> {
                bullishScore += 3
                indicators.add("Major Drawdown (-${drawdownPercent.toInt()}%)")
            }
            drawdownPercent > 20 -> {
                bullishScore += 2
                indicators.add("Significant Drawdown (-${drawdownPercent.toInt()}%)")
            }
            drawdownPercent > 10 -> {
                bullishScore += 1
                indicators.add("Moderate Drawdown (-${drawdownPercent.toInt()}%)")
            }
        }
        
        // High volume on down move (capitulation)
        if (volumeRatio > 2 && closes.last() < closes[closes.size - 2]) {
            bullishScore += 2
            indicators.add("Volume Spike on Decline (Capitulation?)")
        }
        
        // Calculate base sentiment from capitulation indicators
        val technicalSentiment = (bullishScore - bearishScore) / 8.0
        
        // V5.17.0: Blend with DQN learned capitulation/euphoria patterns
        val (finalSentiment, finalConfidence, dqnInsight) = if (dqn != null) {
            try {
                
                val (dqnFeatures, dqnPosition) = buildDQNFeatures(context)
                val dqnSentiment = dqn.getLearnedSentimentDirect(dqnFeatures, dqnPosition)
                val dqnConfidence = dqn.getDecisionConfidenceDirect(dqnFeatures, dqnPosition)
                
                // Blend: 60% technical indicators, 40% DQN learned patterns
                // DQN may have learned which "blood in streets" moments are actually buying opportunities
                val blendedSentiment = (technicalSentiment * 0.6) + (dqnSentiment * 0.4)
                val blendedConfidence = (abs(technicalSentiment) * 0.6) + (dqnConfidence * 0.4)
                
                val insight = "DQN learned ${String.format("%+.2f", dqnSentiment)} (${String.format("%.1f", dqnConfidence * 100)}% confident)"
                indicators.add(insight)
                
                Triple(blendedSentiment, blendedConfidence, insight)
            } catch (e: Exception) {
                Triple(technicalSentiment, abs(technicalSentiment), "DQN unavailable")
            }
        } else {
            Triple(technicalSentiment, abs(technicalSentiment), null)
        }
        
        val vote = sentimentToVote(finalSentiment)
        
        val reasoning = buildString {
            append(if (bullishScore > 3) "Potential capitulation - buying opportunity" else "No extreme conditions")
            if (dqnInsight != null) {
                append(". $dqnInsight")
            }
        }
        
        return AgentOpinion(
            agentName = name,
            displayName = displayName,
            role = role,
            vote = vote,
            sentiment = finalSentiment,
            confidence = finalConfidence,
            reasoning = reasoning,
            keyIndicators = indicators
        )
    }
}

/**
 * 9. Soros (Chief Economist) - Global Macro Analyst
 * Analyzes central bank policy, economic indicators, and geopolitical risk.
 * Named after George Soros - the master of macro trading.
 * Weight: 10% - Macro calls can override technical signals in regime shifts.
 * 
 * V5.17.0: New board member for Global Macro strategy integration.
 * Uses real-time data from MacroSentimentAnalyzer when available.
 */
// ============================================================================
// AI BOARD ORCHESTRATOR - V5.17.0 OCTAGON (8-Member Core Board)
// ============================================================================
//
// REFACTORED: v5.5.62 - Hedge fund member classes EXTRACTED
// 
// Hedge fund members (Soros, Guardian, Draper, Moby, Atlas, Echo, Theta) are
// now in HedgeFundBoardMembers.kt - imported via BoardMemberFactory.
//
// For configurable boards, use:
// - ConfigurableBoardOrchestrator (1-20 members, any configuration)
// - TestBoardOrchestrator (for backtesting with performance tracking)
// - HedgeFundBoardOrchestrator (hedge fund strategies, unwired)
//
// This file now contains ONLY the 8 core Octagon member classes.
// ============================================================================

/**
 * OCTAGON AI Board Orchestrator
 * 8-member board with equal 12.5% voting weights
 * V5.17.0: Now accepts optional DQN learning engine to provide learned insights
 * V5.18.21: Now accepts optional SentimentEngine for Oracle to access real sentiment data
 */
class AIBoardOrchestrator(
    private val dqn: DQNTrader? = null,  // Optional: DQN learning engine that informs board decisions
    private val sentimentEngine: SentimentEngine? = null  // V5.18.21: Optional: Real sentiment data for Oracle
) {
    
    /**
     * OCTAGON: 8 Core Members - Original proven architecture
     * Each member has equal 12.5% weight (100% / 8)
     * 
     * V5.17.0: Board members now receive DQN instance to blend learned patterns
     * with their specialized heuristics (trend, risk, volatility, etc.)
     * DQN provides "what actually worked historically" to augment "what theory says"
     * 
     * V5.18.21: Oracle (SentimentAnalyst) now receives SentimentEngine for real
     * Fear & Greed Index + CoinGecko data instead of momentum proxy
     * 
     * Casting Vote: Sentinel (100% accuracy Q1 2025 backtest)
     */
    private val boardMembers: List<BoardMember> = listOf(
        TrendFollower(dqn),                 // Arthur   - 12.5% - CTO/Chairman, Trend Following
        MeanReverter(dqn),                  // Helena   - 12.5% - CRO, Mean Reversion
        VolatilityTrader(dqn),              // Sentinel - 12.5% - CCO, Volatility (CASTING VOTE)
        SentimentAnalyst(dqn, sentimentEngine),  // Oracle   - 12.5% - CDO, Sentiment Analysis ✅ NOW WIRED
        OnChainAnalyst(dqn),                // Nexus    - 12.5% - COO, On-Chain Analytics
        MacroStrategist(dqn),               // Marcus   - 12.5% - CIO, Macro Strategy
        PatternRecognizer(dqn),             // Cipher   - 12.5% - CSO, Pattern Recognition
        LiquidityHunter(dqn)                // Aegis    - 12.5% - Chief Defense, Liquidity/Capitulation
    )
    // Total: 8 members × 12.5% = 100%
    
    // Casting vote member (used when no consensus)
    private val castingVoteMemberName = "Sentinel" // Based on Q1 2025 backtest accuracy
    
    // Consensus thresholds
    companion object {
        const val STRONG_BUY_THRESHOLD = 0.6
        const val BUY_THRESHOLD = 0.2
        const val SELL_THRESHOLD = -0.2
        const val STRONG_SELL_THRESHOLD = -0.6
    }
    
    /**
     * Convene the board and reach consensus.
     * 
     * V5.17.0: Now accepts regime-aware weight overrides from TradingCoordinator.
     * When [weightOverrides] is provided, each member's voting influence is determined
     * by the current market regime (e.g. Sentinel gets 0.22 in HIGH_VOLATILITY,
     * Helena gets 0.20 in SIDEWAYS_RANGING) rather than the static 0.125 default.
     * 
     * Also calculates [BoardConsensus.recommendedPositionSize] — a multiplier (0.0–1.0)
     * that reflects regime-adjusted confidence. This feeds into position sizing
     * downstream, capped by the Kelly criterion ceiling.
     * 
     * @param context Current market data (OHLCV, sentiment, etc.)
     * @param weightOverrides Regime-specific weights keyed by member.name (e.g. "TrendFollower" → 0.22).
     *                        When null, falls back to each member's static weight.
     */
    fun conveneBoardroom(
        context: MarketContext,
        weightOverrides: Map<String, Double>? = null
    ): BoardConsensus {
        // Gather all opinions
        val opinions = boardMembers.map { it.analyze(context) }
        
        // Calculate weighted score — V5.17.0: use regime-aware weights when available
        var weightedSentiment = 0.0
        var totalWeight = 0.0
        
        for (i in opinions.indices) {
            val opinion = opinions[i]
            val member = boardMembers[i]
            val effectiveWeight = weightOverrides?.get(member.name) ?: member.weight
            weightedSentiment += opinion.sentiment * opinion.confidence * effectiveWeight
            totalWeight += effectiveWeight * opinion.confidence
        }
        
        val finalScore = if (totalWeight > 0) weightedSentiment / totalWeight else 0.0
        
        // Determine final vote
        val finalDecision = when {
            finalScore > STRONG_BUY_THRESHOLD -> BoardVote.STRONG_BUY
            finalScore > BUY_THRESHOLD -> BoardVote.BUY
            finalScore < STRONG_SELL_THRESHOLD -> BoardVote.STRONG_SELL
            finalScore < SELL_THRESHOLD -> BoardVote.SELL
            else -> BoardVote.HOLD
        }
        
        // Count unanimous votes
        val majorityVote = if (finalScore > 0) BoardVote.BUY else BoardVote.SELL
        val unanimousCount = opinions.count { 
            (it.vote == BoardVote.BUY || it.vote == BoardVote.STRONG_BUY) == (finalScore > 0)
        }
        
        // Collect dissenter reasons
        val dissenterReasons = opinions
            .filter { (it.vote == BoardVote.BUY || it.vote == BoardVote.STRONG_BUY) != (finalScore > 0) }
            .map { "${it.agentName}: ${it.reasoning}" }
        
        // Calculate confidence
        val confidence = opinions.map { it.confidence }.average()
        
        // V5.17.0: Calculate recommended position size multiplier (0.0–1.0)
        // This is a REDUCTION ONLY multiplier — downstream code caps at Kelly ceiling.
        // Board reduces position when: low confidence, weak consensus, or dangerous regime.
        val recommendedPositionSize = calculateBoardPositionSize(
            confidence = confidence,
            unanimousCount = unanimousCount,
            totalMembers = opinions.size,
            weightOverridesProvided = weightOverrides != null
        )
        
        // Synthesize decision
        val synthesis = synthesizeDecision(finalDecision, opinions, finalScore)
        
        return BoardConsensus(
            finalDecision = finalDecision,
            weightedScore = finalScore,
            confidence = confidence,
            unanimousCount = unanimousCount,
            dissenterReasons = dissenterReasons,
            opinions = opinions,
            synthesis = synthesis,
            recommendedPositionSize = recommendedPositionSize
        )
    }
    
    /**
     * V5.17.0: Board-level position size recommendation.
     * 
     * Returns a multiplier between 0.0 and 1.0 that reduces position size
     * based on board confidence and agreement level. This is intentionally
     * conservative — the board can REDUCE below Kelly but NEVER exceed it.
     * 
     * Factors:
     * - Board confidence (average across members)
     * - Agreement level (unanimous = full size, split = reduced)
     * - Minimum floor of 0.2 (never recommend less than 20% of Kelly)
     */
    private fun calculateBoardPositionSize(
        confidence: Double,
        unanimousCount: Int,
        totalMembers: Int,
        weightOverridesProvided: Boolean
    ): Double {
        // Confidence factor: linear scale, floored at 0.3
        val confidenceFactor = confidence.coerceIn(0.3, 1.0)
        
        // Agreement factor: how aligned is the board?
        // 8/8 agreement = 1.0, 5/8 = 0.625, etc.
        val agreementFactor = if (totalMembers > 0) {
            (unanimousCount.toDouble() / totalMembers).coerceIn(0.4, 1.0)
        } else 1.0
        
        // Combined multiplier — geometric mean gives balanced influence
        val rawMultiplier = kotlin.math.sqrt(confidenceFactor * agreementFactor)
        
        // Floor at 0.2 (never recommend less than 20% of max), ceiling at 1.0
        return rawMultiplier.coerceIn(0.2, 1.0)
    }
    
    private fun synthesizeDecision(
        decision: BoardVote,
        opinions: List<AgentOpinion>,
        score: Double
    ): String {
        val bullishCount = opinions.count { it.vote == BoardVote.BUY || it.vote == BoardVote.STRONG_BUY }
        val bearishCount = opinions.count { it.vote == BoardVote.SELL || it.vote == BoardVote.STRONG_SELL }
        val totalMembers = opinions.size
        
        return when (decision) {
            BoardVote.STRONG_BUY -> "Strong consensus to BUY ($bullishCount/$totalMembers advisers). Score: ${String.format("%.2f", score)}"
            BoardVote.BUY -> "Moderate BUY signal ($bullishCount/$totalMembers advisers). Score: ${String.format("%.2f", score)}"
            BoardVote.HOLD -> "Mixed signals - HOLD recommended. Score: ${String.format("%.2f", score)}"
            BoardVote.SELL -> "Moderate SELL signal ($bearishCount/$totalMembers advisers). Score: ${String.format("%.2f", score)}"
            BoardVote.STRONG_SELL -> "Strong consensus to SELL ($bearishCount/$totalMembers advisers). Score: ${String.format("%.2f", score)}"
        }
    }
}

// ============================================================================
// UTILITY FUNCTIONS
// ============================================================================

internal fun sentimentToVote(sentiment: Double): BoardVote {
    return when {
        sentiment > 0.6 -> BoardVote.STRONG_BUY
        sentiment > 0.2 -> BoardVote.BUY
        sentiment < -0.6 -> BoardVote.STRONG_SELL
        sentiment < -0.2 -> BoardVote.SELL
        else -> BoardVote.HOLD
    }
}

// ============================================================================
// HEDGE FUND MEMBERS - EXTRACTED TO HedgeFundBoardMembers.kt (v5.5.62)
// ============================================================================
// The following classes have been moved to HedgeFundBoardMembers.kt:
// - GlobalMacroAnalyst (Soros) - HEDGE_FUND
// - DeFiSpecialist (Draper) - HEDGE_FUND
// - WhaleTracker (Moby) - CROSSOVER
// - LiquidationCascadeDetector (Guardian) - HEDGE_FUND
// - RegimeMetaStrategist (Atlas) - HEDGE_FUND
// - OrderBookImbalanceAnalyst (Echo) - CROSSOVER
// - FundingRateArbitrageAnalyst (Theta) - HEDGE_FUND
//
// For backward compatibility, these can still be instantiated via
// BoardMemberFactory which imports from HedgeFundBoardMembers.kt
// ============================================================================
