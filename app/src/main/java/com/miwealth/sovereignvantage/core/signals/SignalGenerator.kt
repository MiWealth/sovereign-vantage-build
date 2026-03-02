package com.miwealth.sovereignvantage.core.signals

import com.miwealth.sovereignvantage.core.indicators.*
import java.util.*
import kotlin.math.*

/**
 * Signal Generator Engine
 * Ported from SignalEngine.ts
 * 
 * Generates trading signals using:
 * - Technical indicator analysis
 * - Pattern recognition
 * - Multi-timeframe confluence
 * - AI-enhanced scoring
 * 
 * © 2025 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO: Arthur Iain McManus (1966-2025)
 */

// ============================================================================
// DATA CLASSES
// ============================================================================

enum class SignalDirection { LONG, SHORT, NEUTRAL }
enum class SignalStrength { WEAK, MODERATE, STRONG, VERY_STRONG }
enum class SignalType { 
    TREND_FOLLOWING, 
    MEAN_REVERSION, 
    BREAKOUT, 
    MOMENTUM, 
    VOLUME_PROFILE, 
    SENTIMENT, 
    AI_COMPOSITE 
}
enum class SignalTimeframe { M1, M5, M15, H1, H4, D1, W1 }

data class IndicatorSignal(
    val name: String,
    val value: Double,
    val signal: String, // "bullish", "bearish", "neutral"
    val weight: Double
)

data class PatternSignal(
    val name: String,
    val type: String, // "continuation", "reversal"
    val direction: SignalDirection,
    val reliability: Int, // 0-100
    val priceTarget: Double? = null
)

data class TradingSignal(
    val id: String,
    val symbol: String,
    val direction: SignalDirection,
    val strength: SignalStrength,
    val type: SignalType,
    val timeframe: SignalTimeframe,
    val entryPrice: Double,
    val targetPrice: Double,
    val stopLoss: Double,
    val riskRewardRatio: Double,
    val confidence: Int, // 0-100
    val indicators: List<IndicatorSignal>,
    val patterns: List<PatternSignal>,
    val aiScore: Double,
    val timestamp: Date,
    val expiresAt: Date,
    val confluenceScore: Int
)

// ============================================================================
// PATTERN RECOGNIZER
// ============================================================================

object PatternRecognizer {

    /**
     * Detect Double Top pattern
     */
    fun detectDoubleTop(highs: List<Double>, lows: List<Double>): PatternSignal? {
        if (highs.size < 30) return null

        val recentHighs = highs.takeLast(30)
        
        // Find peaks
        val peaks = mutableListOf<Pair<Int, Double>>()
        for (i in 1 until recentHighs.size - 1) {
            if (recentHighs[i] > recentHighs[i - 1] && recentHighs[i] > recentHighs[i + 1]) {
                peaks.add(i to recentHighs[i])
            }
        }
        
        if (peaks.size < 2) return null
        
        // Check for double top (two peaks at similar levels)
        for (i in 0 until peaks.size - 1) {
            val peak1 = peaks[i]
            val peak2 = peaks[i + 1]
            
            val priceDiff = abs(peak1.second - peak2.second) / peak1.second
            val indexDiff = peak2.first - peak1.first
            
            if (priceDiff < 0.02 && indexDiff > 5 && indexDiff < 20) {
                val neckline = lows.subList(peak1.first, peak2.first + 1).min() ?: return null
                val target = neckline - (peak1.second - neckline)
                
                return PatternSignal(
                    name = "Double Top",
                    type = "reversal",
                    direction = SignalDirection.SHORT,
                    reliability = 75,
                    priceTarget = target
                )
            }
        }
        
        return null
    }

    /**
     * Detect Double Bottom pattern
     */
    fun detectDoubleBottom(highs: List<Double>, lows: List<Double>): PatternSignal? {
        if (lows.size < 30) return null

        val recentLows = lows.takeLast(30)
        
        // Find troughs
        val troughs = mutableListOf<Pair<Int, Double>>()
        for (i in 1 until recentLows.size - 1) {
            if (recentLows[i] < recentLows[i - 1] && recentLows[i] < recentLows[i + 1]) {
                troughs.add(i to recentLows[i])
            }
        }
        
        if (troughs.size < 2) return null
        
        // Check for double bottom
        for (i in 0 until troughs.size - 1) {
            val trough1 = troughs[i]
            val trough2 = troughs[i + 1]
            
            val priceDiff = abs(trough1.second - trough2.second) / trough1.second
            val indexDiff = trough2.first - trough1.first
            
            if (priceDiff < 0.02 && indexDiff > 5 && indexDiff < 20) {
                val neckline = highs.subList(trough1.first, trough2.first + 1).max() ?: return null
                val target = neckline + (neckline - trough1.second)
                
                return PatternSignal(
                    name = "Double Bottom",
                    type = "reversal",
                    direction = SignalDirection.LONG,
                    reliability = 75,
                    priceTarget = target
                )
            }
        }
        
        return null
    }

    /**
     * Detect Head and Shoulders pattern
     */
    fun detectHeadAndShoulders(highs: List<Double>, lows: List<Double>): PatternSignal? {
        if (highs.size < 40) return null

        val recentHighs = highs.takeLast(40)
        
        // Find peaks
        val peaks = mutableListOf<Pair<Int, Double>>()
        for (i in 2 until recentHighs.size - 2) {
            if (recentHighs[i] > recentHighs[i - 1] && 
                recentHighs[i] > recentHighs[i - 2] &&
                recentHighs[i] > recentHighs[i + 1] && 
                recentHighs[i] > recentHighs[i + 2]) {
                peaks.add(i to recentHighs[i])
            }
        }
        
        if (peaks.size < 3) return null
        
        // Check for head and shoulders
        for (i in 0 until peaks.size - 2) {
            val leftShoulder = peaks[i]
            val head = peaks[i + 1]
            val rightShoulder = peaks[i + 2]
            
            // Head must be higher than shoulders
            if (head.second > leftShoulder.second && 
                head.second > rightShoulder.second &&
                abs(leftShoulder.second - rightShoulder.second) / leftShoulder.second < 0.05) {
                
                val neckline = lows.subList(leftShoulder.first, rightShoulder.first + 1).min() 
                    ?: return null
                
                return PatternSignal(
                    name = "Head and Shoulders",
                    type = "reversal",
                    direction = SignalDirection.SHORT,
                    reliability = 85,
                    priceTarget = neckline - (head.second - neckline)
                )
            }
        }
        
        return null
    }

    /**
     * Detect Triangle patterns
     */
    fun detectTriangle(highs: List<Double>, lows: List<Double>): PatternSignal? {
        if (highs.size < 20) return null

        val recentHighs = highs.takeLast(20)
        val recentLows = lows.takeLast(20)

        // Calculate trend lines
        val highSlope = (recentHighs.last() - recentHighs.first()) / recentHighs.size
        val lowSlope = (recentLows.last() - recentLows.first()) / recentLows.size

        // Ascending triangle
        if (abs(highSlope) < 0.001 && lowSlope > 0.001) {
            return PatternSignal(
                name = "Ascending Triangle",
                type = "continuation",
                direction = SignalDirection.LONG,
                reliability = 70
            )
        }

        // Descending triangle
        if (highSlope < -0.001 && abs(lowSlope) < 0.001) {
            return PatternSignal(
                name = "Descending Triangle",
                type = "continuation",
                direction = SignalDirection.SHORT,
                reliability = 70
            )
        }

        // Symmetrical triangle
        if (highSlope < -0.001 && lowSlope > 0.001) {
            return PatternSignal(
                name = "Symmetrical Triangle",
                type = "continuation",
                direction = SignalDirection.NEUTRAL,
                reliability = 65
            )
        }

        return null
    }

    /**
     * Detect all patterns
     */
    fun detectAllPatterns(highs: List<Double>, lows: List<Double>): List<PatternSignal> {
        val patterns = mutableListOf<PatternSignal>()
        
        detectDoubleTop(highs, lows)?.let { patterns.add(it) }
        detectDoubleBottom(highs, lows)?.let { patterns.add(it) }
        detectHeadAndShoulders(highs, lows)?.let { patterns.add(it) }
        detectTriangle(highs, lows)?.let { patterns.add(it) }
        
        return patterns
    }
}

// ============================================================================
// SIGNAL GENERATOR
// ============================================================================

class SignalGenerator {
    
    private val signalHistory = mutableMapOf<String, MutableList<TradingSignal>>()
    
    /**
     * Generate trading signal for a symbol
     */
    fun generateSignal(
        symbol: String,
        opens: List<Double>,
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>,
        volumes: List<Double>,
        timeframe: SignalTimeframe,
        aiScore: Double? = null
    ): TradingSignal? {
        if (closes.size < 50) return null
        
        val currentPrice = closes.last()
        val indicators = analyzeIndicators(highs, lows, closes, volumes)
        val patterns = PatternRecognizer.detectAllPatterns(highs, lows)
        
        // Calculate composite signal
        val bullishScore = calculateBullishScore(indicators, patterns)
        val bearishScore = calculateBearishScore(indicators, patterns)
        
        val netScore = bullishScore - bearishScore
        
        if (abs(netScore) < 20) return null // No clear signal
        
        val direction = if (netScore > 0) SignalDirection.LONG else SignalDirection.SHORT
        val strength = calculateStrength(abs(netScore))
        val confidence = minOf(100, abs(netScore).toInt())
        
        // Calculate entry, target, and stop loss using ATR
        val atr = VolatilityIndicators.atr(highs, lows, closes)
        val entryPrice = currentPrice
        val stopLoss = if (direction == SignalDirection.LONG) 
            currentPrice - (atr * 2) else currentPrice + (atr * 2)
        val targetPrice = if (direction == SignalDirection.LONG)
            currentPrice + (atr * 3) else currentPrice - (atr * 3)
        
        val riskRewardRatio = abs(targetPrice - entryPrice) / abs(entryPrice - stopLoss)
        
        val signal = TradingSignal(
            id = "sig_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}",
            symbol = symbol,
            direction = direction,
            strength = strength,
            type = determineSignalType(indicators, patterns),
            timeframe = timeframe,
            entryPrice = entryPrice,
            targetPrice = targetPrice,
            stopLoss = stopLoss,
            riskRewardRatio = riskRewardRatio,
            confidence = confidence,
            indicators = indicators,
            patterns = patterns,
            aiScore = aiScore ?: confidence.toDouble(),
            timestamp = Date(),
            expiresAt = calculateExpiry(timeframe),
            confluenceScore = calculateConfluence(indicators, patterns)
        )
        
        // Store in history
        val history = signalHistory.getOrPut(symbol) { mutableListOf() }
        history.add(signal)
        if (history.size > 100) history.removeAt(0)
        
        return signal
    }
    
    /**
     * Analyze all technical indicators
     */
    private fun analyzeIndicators(
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>,
        volumes: List<Double>
    ): List<IndicatorSignal> {
        val indicators = mutableListOf<IndicatorSignal>()
        val currentPrice = closes.last()
        
        // RSI
        val rsi = MomentumIndicators.rsi(closes)
        indicators.add(IndicatorSignal(
            name = "RSI",
            value = rsi,
            signal = when {
                rsi < 30 -> "bullish"
                rsi > 70 -> "bearish"
                else -> "neutral"
            },
            weight = 0.15
        ))
        
        // MACD
        val macd = MomentumIndicators.macd(closes)
        indicators.add(IndicatorSignal(
            name = "MACD",
            value = macd.histogram,
            signal = when {
                macd.histogram > 0 -> "bullish"
                macd.histogram < 0 -> "bearish"
                else -> "neutral"
            },
            weight = 0.15
        ))
        
        // Bollinger Bands
        val bb = VolatilityIndicators.bollingerBands(closes)
        indicators.add(IndicatorSignal(
            name = "Bollinger %B",
            value = bb.percentB,
            signal = when {
                bb.percentB < 0.2 -> "bullish"
                bb.percentB > 0.8 -> "bearish"
                else -> "neutral"
            },
            weight = 0.10
        ))
        
        // Stochastic
        val stoch = MomentumIndicators.stochastic(highs, lows, closes)
        indicators.add(IndicatorSignal(
            name = "Stochastic",
            value = stoch.k,
            signal = when {
                stoch.k < 20 -> "bullish"
                stoch.k > 80 -> "bearish"
                else -> "neutral"
            },
            weight = 0.10
        ))
        
        // ADX
        val adx = TrendIndicators.adx(highs, lows, closes)
        indicators.add(IndicatorSignal(
            name = "ADX",
            value = adx.adx,
            signal = when {
                adx.plusDI > adx.minusDI && adx.adx > 25 -> "bullish"
                adx.minusDI > adx.plusDI && adx.adx > 25 -> "bearish"
                else -> "neutral"
            },
            weight = 0.10
        ))
        
        // EMA Cross
        val ema10 = TrendIndicators.ema(closes, 10)
        val ema20 = TrendIndicators.ema(closes, 20)
        indicators.add(IndicatorSignal(
            name = "EMA Cross",
            value = ema10 - ema20,
            signal = when {
                ema10 > ema20 -> "bullish"
                ema10 < ema20 -> "bearish"
                else -> "neutral"
            },
            weight = 0.15
        ))
        
        // Volume Profile
        val vwap = VolumeIndicators.vwap(highs, lows, closes, volumes)
        indicators.add(IndicatorSignal(
            name = "VWAP",
            value = vwap,
            signal = when {
                currentPrice > vwap -> "bullish"
                currentPrice < vwap -> "bearish"
                else -> "neutral"
            },
            weight = 0.10
        ))
        
        // CMF
        val cmf = VolumeIndicators.cmf(highs, lows, closes, volumes)
        indicators.add(IndicatorSignal(
            name = "CMF",
            value = cmf,
            signal = when {
                cmf > 0.05 -> "bullish"
                cmf < -0.05 -> "bearish"
                else -> "neutral"
            },
            weight = 0.05
        ))
        
        // SuperTrend
        val supertrend = TrendIndicators.superTrend(highs, lows, closes)
        indicators.add(IndicatorSignal(
            name = "SuperTrend",
            value = supertrend.value,
            signal = if (supertrend.trend == "up") "bullish" else "bearish",
            weight = 0.10
        ))
        
        return indicators
    }
    
    private fun calculateBullishScore(indicators: List<IndicatorSignal>, patterns: List<PatternSignal>): Double {
        var score = 0.0
        
        for (ind in indicators) {
            if (ind.signal == "bullish") {
                score += ind.weight * 100
            }
        }
        
        for (pattern in patterns) {
            if (pattern.direction == SignalDirection.LONG) {
                score += pattern.reliability * 0.3
            }
        }
        
        return score
    }
    
    private fun calculateBearishScore(indicators: List<IndicatorSignal>, patterns: List<PatternSignal>): Double {
        var score = 0.0
        
        for (ind in indicators) {
            if (ind.signal == "bearish") {
                score += ind.weight * 100
            }
        }
        
        for (pattern in patterns) {
            if (pattern.direction == SignalDirection.SHORT) {
                score += pattern.reliability * 0.3
            }
        }
        
        return score
    }
    
    private fun calculateStrength(netScore: Double): SignalStrength {
        return when {
            netScore >= 60 -> SignalStrength.VERY_STRONG
            netScore >= 40 -> SignalStrength.STRONG
            netScore >= 25 -> SignalStrength.MODERATE
            else -> SignalStrength.WEAK
        }
    }
    
    private fun determineSignalType(indicators: List<IndicatorSignal>, patterns: List<PatternSignal>): SignalType {
        // Check patterns first
        for (pattern in patterns) {
            if (pattern.type == "reversal") return SignalType.MEAN_REVERSION
            if (pattern.name.contains("Triangle")) return SignalType.BREAKOUT
        }
        
        // Check indicators
        val rsi = indicators.find { it.name == "RSI" }?.value ?: 50.0
        if (rsi < 30 || rsi > 70) return SignalType.MEAN_REVERSION
        
        val adx = indicators.find { it.name == "ADX" }?.value ?: 0.0
        if (adx > 25) return SignalType.TREND_FOLLOWING
        
        return SignalType.MOMENTUM
    }
    
    private fun calculateConfluence(indicators: List<IndicatorSignal>, patterns: List<PatternSignal>): Int {
        var agreement = 0
        var total = 0
        
        val majoritySignal = if (indicators.count { it.signal == "bullish" } > 
                                indicators.count { it.signal == "bearish" }) "bullish" else "bearish"
        
        for (ind in indicators) {
            total++
            if (ind.signal == majoritySignal) agreement++
        }
        
        return if (total == 0) 50 else (agreement * 100) / total
    }
    
    private fun calculateExpiry(timeframe: SignalTimeframe): Date {
        val calendar = Calendar.getInstance()
        when (timeframe) {
            SignalTimeframe.M1 -> calendar.add(Calendar.MINUTE, 5)
            SignalTimeframe.M5 -> calendar.add(Calendar.MINUTE, 30)
            SignalTimeframe.M15 -> calendar.add(Calendar.HOUR, 2)
            SignalTimeframe.H1 -> calendar.add(Calendar.HOUR, 8)
            SignalTimeframe.H4 -> calendar.add(Calendar.DAY_OF_MONTH, 1)
            SignalTimeframe.D1 -> calendar.add(Calendar.DAY_OF_MONTH, 5)
            SignalTimeframe.W1 -> calendar.add(Calendar.WEEK_OF_YEAR, 2)
        }
        return calendar.time
    }
    
    /**
     * Get signal history for a symbol
     */
    fun getSignalHistory(symbol: String): List<TradingSignal> {
        return signalHistory[symbol]?.toList() ?: emptyList()
    }
}
