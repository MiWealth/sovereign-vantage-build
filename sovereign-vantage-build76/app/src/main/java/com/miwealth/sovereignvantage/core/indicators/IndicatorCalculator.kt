package com.miwealth.sovereignvantage.core.indicators

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.ln

/**
 * Technical Indicator Calculator - Feeds the AI Board's MarketContext
 * 
 * This module provides:
 * 1. Core technical indicators (SMA, EMA, RSI, MACD, ATR, ADX, Bollinger, etc.)
 * 2. IndicatorSnapshot generation for AI Board decisions
 * 3. Multi-timeframe analysis support
 * 4. Efficient rolling calculations for real-time updates
 * 
 * Indicator Categories:
 * - Trend: SMA, EMA, MACD, ADX, Parabolic SAR
 * - Momentum: RSI, Stochastic, ROC, Williams %R, CCI
 * - Volatility: ATR, Bollinger Bands, Keltner Channels
 * - Volume: OBV, VWAP, MFI, CMF
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage™: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to: Cathryn 💘
 */

// =============================================================================
// OHLCV DATA STRUCTURES
// =============================================================================

/**
 * Single OHLCV bar/candle.
 */
data class OHLCV(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
) {
    val typical: Double get() = (high + low + close) / 3.0
    val hlc3: Double get() = typical
    val hl2: Double get() = (high + low) / 2.0
    val ohlc4: Double get() = (open + high + low + close) / 4.0
    val range: Double get() = high - low
    
    /**
     * True Range for ATR calculation.
     */
    fun trueRange(previousClose: Double): Double {
        return maxOf(
            high - low,
            abs(high - previousClose),
            abs(low - previousClose)
        )
    }
}

/**
 * Series of OHLCV data for indicator calculations.
 * Most recent bar is at index 0.
 */
class OHLCVSeries(
    private val data: ArrayDeque<OHLCV> = ArrayDeque(),
    private val maxSize: Int = 500  // Keep last 500 bars
) {
    val size: Int get() = data.size
    val isEmpty: Boolean get() = data.isEmpty()
    val isNotEmpty: Boolean get() = data.isNotEmpty()
    
    /**
     * Get bar at index (0 = most recent).
     */
    operator fun get(index: Int): OHLCV = data[index]
    
    /**
     * Get most recent bar.
     */
    fun current(): OHLCV = data.first()
    
    /**
     * Get previous bar.
     */
    fun previous(): OHLCV = data[1]
    
    /**
     * Add new bar (most recent) — O(1) with ArrayDeque.
     */
    fun add(bar: OHLCV) {
        data.addFirst(bar)
        if (data.size > maxSize) {
            data.removeLast()
        }
    }
    
    /**
     * Update the most recent bar (for live updates within a candle).
     */
    fun updateCurrent(bar: OHLCV) {
        if (data.isNotEmpty()) {
            data[0] = bar
        } else {
            add(bar)
        }
    }
    
    /**
     * Get closes as a list (most recent first).
     */
    fun closes(): List<Double> = data.map { it.close }
    
    /**
     * Get highs as a list.
     */
    fun highs(): List<Double> = data.map { it.high }
    
    /**
     * Get lows as a list.
     */
    fun lows(): List<Double> = data.map { it.low }
    
    /**
     * Get volumes as a list.
     */
    fun volumes(): List<Double> = data.map { it.volume }
    
    /**
     * Take most recent N bars.
     */
    fun take(n: Int): List<OHLCV> = data.take(minOf(n, data.size))
    
    /**
     * Check if we have enough data for a given period.
     */
    fun hasEnoughData(period: Int): Boolean = data.size >= period
}

// =============================================================================
// INDICATOR RESULTS
// =============================================================================

/**
 * MACD indicator values.
 */
data class MACDResult(
    val macd: Double,       // MACD line
    val signal: Double,     // Signal line
    val histogram: Double   // MACD - Signal
)

/**
 * Bollinger Bands values.
 */
data class BollingerBandsResult(
    val upper: Double,
    val middle: Double,
    val lower: Double,
    val width: Double,      // (upper - lower) / middle * 100
    val percentB: Double    // (close - lower) / (upper - lower)
)

/**
 * Stochastic oscillator values.
 */
data class StochasticResult(
    val k: Double,          // Fast %K
    val d: Double           // Slow %D (SMA of %K)
)

/**
 * ADX with directional indicators.
 */
data class ADXResult(
    val adx: Double,
    val plusDI: Double,     // +DI
    val minusDI: Double     // -DI
)

/**
 * Keltner Channel values.
 */
data class KeltnerChannelResult(
    val upper: Double,
    val middle: Double,
    val lower: Double
)

// =============================================================================
// CORE INDICATOR CALCULATOR
// =============================================================================

/**
 * Technical indicator calculator with efficient rolling calculations.
 * 
 * Usage:
 * ```kotlin
 * val calculator = IndicatorCalculator()
 * 
 * // Add OHLCV data
 * calculator.addBar(bar)
 * 
 * // Calculate indicators
 * val rsi = calculator.rsi(14)
 * val macd = calculator.macd(12, 26, 9)
 * val atr = calculator.atr(14)
 * 
 * // Generate snapshot for AI Board
 * val snapshot = calculator.generateSnapshot()
 * ```
 */
class IndicatorCalculator(
    private val series: OHLCVSeries = OHLCVSeries()
) {
    // Cache for expensive calculations
    private val emaCache = mutableMapOf<Int, Double>()
    private var lastBarTimestamp: Long = 0
    
    // ==========================================================================
    // DATA MANAGEMENT
    // ==========================================================================
    
    /**
     * Add a new OHLCV bar.
     */
    fun addBar(bar: OHLCV) {
        if (bar.timestamp != lastBarTimestamp) {
            series.add(bar)
            lastBarTimestamp = bar.timestamp
            clearCache()
        } else {
            series.updateCurrent(bar)
        }
    }
    
    /**
     * Add multiple bars (oldest first).
     */
    fun addBars(bars: List<OHLCV>) {
        bars.sortedBy { it.timestamp }.forEach { addBar(it) }
    }
    
    /**
     * Clear calculation cache (call when data changes).
     */
    private fun clearCache() {
        emaCache.clear()
    }
    
    /**
     * Check if we have enough data.
     */
    fun hasEnoughData(minBars: Int = 50): Boolean = series.hasEnoughData(minBars)
    
    /**
     * Get the current price.
     */
    fun currentPrice(): Double = if (series.isNotEmpty) series.current().close else 0.0
    
    // ==========================================================================
    // TREND INDICATORS
    // ==========================================================================
    
    /**
     * Simple Moving Average.
     */
    fun sma(period: Int, source: List<Double> = series.closes()): Double {
        if (source.size < period) return source.firstOrNull() ?: 0.0
        return source.take(period).average()
    }
    
    /**
     * Exponential Moving Average.
     */
    fun ema(period: Int, source: List<Double> = series.closes()): Double {
        if (source.size < period) return sma(period, source)
        
        val multiplier = 2.0 / (period + 1)
        var ema = source.takeLast(period).average()  // Initialize with SMA
        
        // Calculate EMA from oldest to newest
        for (i in (source.size - period - 1) downTo 0) {
            ema = (source[i] - ema) * multiplier + ema
        }
        
        return ema
    }
    
    /**
     * MACD - Moving Average Convergence Divergence.
     */
    fun macd(fastPeriod: Int = 12, slowPeriod: Int = 26, signalPeriod: Int = 9): MACDResult {
        val closes = series.closes()
        
        val fastEMA = ema(fastPeriod, closes)
        val slowEMA = ema(slowPeriod, closes)
        val macdLine = fastEMA - slowEMA
        
        // Calculate MACD history for signal line
        val macdHistory = mutableListOf<Double>()
        for (i in 0 until minOf(signalPeriod * 2, closes.size - slowPeriod)) {
            val subCloses = closes.drop(i)
            val fast = ema(fastPeriod, subCloses)
            val slow = ema(slowPeriod, subCloses)
            macdHistory.add(fast - slow)
        }
        
        val signal = if (macdHistory.size >= signalPeriod) {
            ema(signalPeriod, macdHistory)
        } else {
            macdLine
        }
        
        return MACDResult(
            macd = macdLine,
            signal = signal,
            histogram = macdLine - signal
        )
    }
    
    /**
     * ADX - Average Directional Index with +DI and -DI.
     */
    fun adx(period: Int = 14): ADXResult {
        if (series.size < period + 1) {
            return ADXResult(adx = 0.0, plusDI = 0.0, minusDI = 0.0)
        }
        
        val plusDMs = mutableListOf<Double>()
        val minusDMs = mutableListOf<Double>()
        val trs = mutableListOf<Double>()
        
        for (i in 0 until minOf(period * 2, series.size - 1)) {
            val current = series[i]
            val previous = series[i + 1]
            
            val upMove = current.high - previous.high
            val downMove = previous.low - current.low
            
            val plusDM = if (upMove > downMove && upMove > 0) upMove else 0.0
            val minusDM = if (downMove > upMove && downMove > 0) downMove else 0.0
            
            plusDMs.add(plusDM)
            minusDMs.add(minusDM)
            trs.add(current.trueRange(previous.close))
        }
        
        val smoothedPlusDM = ema(period, plusDMs)
        val smoothedMinusDM = ema(period, minusDMs)
        val smoothedTR = ema(period, trs)
        
        val plusDI = if (smoothedTR > 0) (smoothedPlusDM / smoothedTR) * 100 else 0.0
        val minusDI = if (smoothedTR > 0) (smoothedMinusDM / smoothedTR) * 100 else 0.0
        
        val dx = if (plusDI + minusDI > 0) {
            abs(plusDI - minusDI) / (plusDI + minusDI) * 100
        } else 0.0
        
        // ADX is smoothed DX
        val adxValue = dx  // Simplified; full ADX would smooth over period
        
        return ADXResult(
            adx = adxValue,
            plusDI = plusDI,
            minusDI = minusDI
        )
    }
    
    /**
     * Moving average slope (% change over N bars).
     */
    fun maSlope(maPeriod: Int = 20, slopeBars: Int = 5): Double {
        if (series.size < maPeriod + slopeBars) return 0.0
        
        val currentMA = sma(maPeriod, series.closes())
        val previousMA = sma(maPeriod, series.closes().drop(slopeBars))
        
        return if (previousMA > 0) {
            ((currentMA - previousMA) / previousMA) * 100
        } else 0.0
    }
    
    // ==========================================================================
    // MOMENTUM INDICATORS
    // ==========================================================================
    
    /**
     * RSI - Relative Strength Index.
     */
    fun rsi(period: Int = 14): Double {
        val closes = series.closes()
        if (closes.size < period + 1) return 50.0
        
        var gains = 0.0
        var losses = 0.0
        
        for (i in 0 until period) {
            val change = closes[i] - closes[i + 1]
            if (change > 0) gains += change
            else losses += abs(change)
        }
        
        val avgGain = gains / period
        val avgLoss = losses / period
        
        if (avgLoss == 0.0) return 100.0
        
        val rs = avgGain / avgLoss
        return 100 - (100 / (1 + rs))
    }
    
    /**
     * RSI change over N bars.
     */
    fun rsiChange(period: Int = 14, changeBars: Int = 3): Double {
        val currentRSI = rsi(period)
        
        // Calculate RSI from N bars ago
        if (series.size < period + changeBars + 1) return 0.0
        
        val olderCloses = series.closes().drop(changeBars)
        var gains = 0.0
        var losses = 0.0
        
        for (i in 0 until period) {
            val change = olderCloses[i] - olderCloses[i + 1]
            if (change > 0) gains += change
            else losses += abs(change)
        }
        
        val avgGain = gains / period
        val avgLoss = losses / period
        
        val previousRSI = if (avgLoss == 0.0) 100.0 else {
            val rs = avgGain / avgLoss
            100 - (100 / (1 + rs))
        }
        
        return currentRSI - previousRSI
    }
    
    /**
     * Stochastic Oscillator.
     */
    fun stochastic(kPeriod: Int = 14, dPeriod: Int = 3): StochasticResult {
        if (series.size < kPeriod) {
            return StochasticResult(k = 50.0, d = 50.0)
        }
        
        val highs = series.highs().take(kPeriod)
        val lows = series.lows().take(kPeriod)
        val close = series.current().close
        
        val highestHigh = highs.maxOrNull() ?: close
        val lowestLow = lows.minOrNull() ?: close
        
        val k = if (highestHigh != lowestLow) {
            ((close - lowestLow) / (highestHigh - lowestLow)) * 100
        } else 50.0
        
        // Calculate %D (SMA of %K)
        val kValues = mutableListOf<Double>()
        for (i in 0 until minOf(dPeriod, series.size - kPeriod)) {
            val subHighs = series.highs().drop(i).take(kPeriod)
            val subLows = series.lows().drop(i).take(kPeriod)
            val subClose = series[i].close
            
            val hh = subHighs.maxOrNull() ?: subClose
            val ll = subLows.minOrNull() ?: subClose
            
            val kVal = if (hh != ll) ((subClose - ll) / (hh - ll)) * 100 else 50.0
            kValues.add(kVal)
        }
        
        val d = if (kValues.isNotEmpty()) kValues.average() else k
        
        return StochasticResult(k = k, d = d)
    }
    
    /**
     * Rate of Change (%).
     */
    fun roc(period: Int = 10): Double {
        if (series.size <= period) return 0.0
        
        val current = series.current().close
        val previous = series[period].close
        
        return if (previous > 0) {
            ((current - previous) / previous) * 100
        } else 0.0
    }
    
    /**
     * Williams %R.
     */
    fun williamsR(period: Int = 14): Double {
        if (series.size < period) return -50.0
        
        val highs = series.highs().take(period)
        val lows = series.lows().take(period)
        val close = series.current().close
        
        val highestHigh = highs.maxOrNull() ?: close
        val lowestLow = lows.minOrNull() ?: close
        
        return if (highestHigh != lowestLow) {
            ((highestHigh - close) / (highestHigh - lowestLow)) * -100
        } else -50.0
    }
    
    /**
     * CCI - Commodity Channel Index.
     */
    fun cci(period: Int = 20): Double {
        if (series.size < period) return 0.0
        
        val typicals = series.take(period).map { it.typical }
        val smaTypical = typicals.average()
        
        // Mean deviation
        val meanDev = typicals.map { abs(it - smaTypical) }.average()
        
        return if (meanDev > 0) {
            (series.current().typical - smaTypical) / (0.015 * meanDev)
        } else 0.0
    }
    
    // ==========================================================================
    // VOLATILITY INDICATORS
    // ==========================================================================
    
    /**
     * ATR - Average True Range.
     */
    fun atr(period: Int = 14): Double {
        if (series.size < period + 1) return 0.0
        
        val trs = mutableListOf<Double>()
        for (i in 0 until period) {
            val current = series[i]
            val previous = series[i + 1]
            trs.add(current.trueRange(previous.close))
        }
        
        return trs.average()
    }
    
    /**
     * ATR as percentage of price.
     */
    fun atrPercent(period: Int = 14): Double {
        val atrValue = atr(period)
        val price = currentPrice()
        return if (price > 0) (atrValue / price) * 100 else 0.0
    }
    
    /**
     * ATR ratio (current ATR / average ATR).
     */
    fun atrRatio(shortPeriod: Int = 14, longPeriod: Int = 50): Double {
        val shortATR = atr(shortPeriod)
        val longATR = atr(longPeriod)
        return if (longATR > 0) shortATR / longATR else 1.0
    }
    
    /**
     * Bollinger Bands.
     */
    fun bollingerBands(period: Int = 20, stdDev: Double = 2.0): BollingerBandsResult {
        val closes = series.closes()
        if (closes.size < period) {
            val price = currentPrice()
            return BollingerBandsResult(
                upper = price,
                middle = price,
                lower = price,
                width = 0.0,
                percentB = 0.5
            )
        }
        
        val data = closes.take(period)
        val middle = data.average()
        val variance = data.map { (it - middle) * (it - middle) }.average()
        val std = sqrt(variance)
        
        val upper = middle + (stdDev * std)
        val lower = middle - (stdDev * std)
        val width = if (middle > 0) ((upper - lower) / middle) * 100 else 0.0
        val percentB = if (upper != lower) {
            (series.current().close - lower) / (upper - lower)
        } else 0.5
        
        return BollingerBandsResult(
            upper = upper,
            middle = middle,
            lower = lower,
            width = width,
            percentB = percentB
        )
    }
    
    /**
     * Keltner Channels.
     */
    fun keltnerChannels(emaPeriod: Int = 20, atrPeriod: Int = 10, multiplier: Double = 2.0): KeltnerChannelResult {
        val middle = ema(emaPeriod)
        val atrValue = atr(atrPeriod)
        
        return KeltnerChannelResult(
            upper = middle + (multiplier * atrValue),
            middle = middle,
            lower = middle - (multiplier * atrValue)
        )
    }
    
    // ==========================================================================
    // VOLUME INDICATORS
    // ==========================================================================
    
    /**
     * Volume ratio (current volume / average volume).
     */
    fun volumeRatio(period: Int = 20): Double {
        if (series.size < period) return 1.0
        
        val currentVolume = series.current().volume
        val avgVolume = series.volumes().take(period).average()
        
        return if (avgVolume > 0) currentVolume / avgVolume else 1.0
    }
    
    /**
     * OBV - On Balance Volume.
     */
    fun obv(): Double {
        if (series.size < 2) return series.current().volume
        
        var obvValue = 0.0
        for (i in (series.size - 2) downTo 0) {
            val change = series[i].close - series[i + 1].close
            obvValue += when {
                change > 0 -> series[i].volume
                change < 0 -> -series[i].volume
                else -> 0.0
            }
        }
        return obvValue
    }
    
    /**
     * VWAP - Volume Weighted Average Price.
     */
    fun vwap(period: Int = 20): Double {
        if (series.isEmpty) return 0.0
        
        val data = series.take(period)
        var sumPV = 0.0
        var sumV = 0.0
        
        data.forEach { bar ->
            sumPV += bar.typical * bar.volume
            sumV += bar.volume
        }
        
        return if (sumV > 0) sumPV / sumV else currentPrice()
    }
    
    /**
     * MFI - Money Flow Index.
     */
    fun mfi(period: Int = 14): Double {
        if (series.size < period + 1) return 50.0
        
        var positiveFlow = 0.0
        var negativeFlow = 0.0
        
        for (i in 0 until period) {
            val current = series[i]
            val previous = series[i + 1]
            val rawMF = current.typical * current.volume
            
            if (current.typical > previous.typical) {
                positiveFlow += rawMF
            } else if (current.typical < previous.typical) {
                negativeFlow += rawMF
            }
        }
        
        if (negativeFlow == 0.0) return 100.0
        
        val mfRatio = positiveFlow / negativeFlow
        return 100 - (100 / (1 + mfRatio))
    }
    
    // ==========================================================================
    // PRICE RELATIVE TO MOVING AVERAGES
    // ==========================================================================
    
    /**
     * Price relative to SMA (% above/below).
     */
    fun priceVsSMA(period: Int): Double {
        val ma = sma(period)
        val price = currentPrice()
        return if (ma > 0) ((price - ma) / ma) * 100 else 0.0
    }
    
    /**
     * Price relative to EMA (% above/below).
     */
    fun priceVsEMA(period: Int): Double {
        val ma = ema(period)
        val price = currentPrice()
        return if (ma > 0) ((price - ma) / ma) * 100 else 0.0
    }
    
    // ==========================================================================
    // SNAPSHOT GENERATION FOR AI BOARD
    // ==========================================================================
    
    /**
     * Generate complete IndicatorSnapshot for AI Board MarketContext.
     * 
     * @param barsAtTopStair Number of bars the position has been at top stair (for expansion)
     * @return IndicatorSnapshot with all required values
     */
    fun generateSnapshot(barsAtTopStair: Int = 0): IndicatorSnapshot {
        val atrValue = atr(14)
        val macdResult = macd(12, 26, 9)
        val adxResult = adx(14)
        val bbResult = bollingerBands(20, 2.0)
        
        // Calculate MACD histogram change
        val currentHistogram = macdResult.histogram
        val previousHistogram = if (series.size > 3) {
            // Recalculate MACD for 3 bars ago
            val olderCloses = series.closes().drop(3)
            val fastEMA = ema(12, olderCloses)
            val slowEMA = ema(26, olderCloses)
            val macdLine = fastEMA - slowEMA
            // Simplified - use current signal as approximation
            macdLine - macdResult.signal
        } else {
            currentHistogram
        }
        
        return IndicatorSnapshot(
            atr = atrValue,
            atrPercent = atrPercent(14),
            atrRatio = atrRatio(14, 50),
            adx = adxResult.adx,
            rsi = rsi(14),
            rsiChange = rsiChange(14, 3),
            macdHistogram = currentHistogram,
            macdHistogramChange = currentHistogram - previousHistogram,
            roc = roc(10),
            priceVs20MA = priceVsSMA(20),
            priceVs50MA = priceVsSMA(50),
            maSlope = maSlope(20, 5),
            volumeRatio = volumeRatio(20),
            bollingerWidth = bbResult.width,
            currentPrice = currentPrice(),
            barsAtTopStair = barsAtTopStair
        )
    }
}

// =============================================================================
// MULTI-TIMEFRAME INDICATOR CALCULATOR
// =============================================================================

/**
 * Multi-timeframe analysis calculator.
 * Maintains separate indicator calculators for multiple timeframes.
 */
class MultiTimeframeCalculator {
    
    private val calculators = mutableMapOf<Timeframe, IndicatorCalculator>()
    
    /**
     * Supported timeframes.
     */
    enum class Timeframe(val minutes: Int, val weight: Double) {
        M1(1, 0.05),      // 1 minute - 5% weight
        M5(5, 0.10),      // 5 minutes - 10% weight
        M15(15, 0.15),    // 15 minutes - 15% weight
        H1(60, 0.25),     // 1 hour - 25% weight
        H4(240, 0.25),    // 4 hours - 25% weight
        D1(1440, 0.20);   // 1 day - 20% weight
        
        companion object {
            val all = values().toList()
        }
    }
    
    /**
     * Get or create calculator for a timeframe.
     */
    fun getCalculator(timeframe: Timeframe): IndicatorCalculator {
        return calculators.getOrPut(timeframe) { IndicatorCalculator() }
    }
    
    /**
     * Add bar to specific timeframe.
     */
    fun addBar(timeframe: Timeframe, bar: OHLCV) {
        getCalculator(timeframe).addBar(bar)
    }
    
    /**
     * Calculate weighted trend alignment score.
     * Returns value from -1.0 (all bearish) to +1.0 (all bullish).
     */
    fun trendAlignmentScore(): Double {
        var weightedSum = 0.0
        var totalWeight = 0.0
        
        for (tf in Timeframe.all) {
            val calc = calculators[tf] ?: continue
            if (!calc.hasEnoughData(50)) continue
            
            val adx = calc.adx(14)
            val priceVs20MA = calc.priceVsSMA(20)
            val priceVs50MA = calc.priceVsSMA(50)
            
            // Score for this timeframe: -1 to +1
            val trendScore = when {
                priceVs20MA > 0 && priceVs50MA > 0 && adx.adx > 25 -> 1.0
                priceVs20MA > 0 && priceVs50MA > 0 -> 0.5
                priceVs20MA < 0 && priceVs50MA < 0 && adx.adx > 25 -> -1.0
                priceVs20MA < 0 && priceVs50MA < 0 -> -0.5
                else -> 0.0
            }
            
            weightedSum += trendScore * tf.weight
            totalWeight += tf.weight
        }
        
        return if (totalWeight > 0) weightedSum / totalWeight else 0.0
    }
    
    /**
     * Generate multi-timeframe summary.
     */
    fun generateSummary(): MultiTimeframeSummary {
        val snapshots = mutableMapOf<Timeframe, IndicatorSnapshot?>()
        
        for (tf in Timeframe.all) {
            val calc = calculators[tf]
            snapshots[tf] = if (calc?.hasEnoughData(50) == true) {
                calc.generateSnapshot()
            } else null
        }
        
        return MultiTimeframeSummary(
            snapshots = snapshots,
            alignmentScore = trendAlignmentScore(),
            dominantTrend = determineDominantTrend()
        )
    }
    
    private fun determineDominantTrend(): TrendDirection {
        val score = trendAlignmentScore()
        return when {
            score > 0.5 -> TrendDirection.STRONG_UP
            score > 0.2 -> TrendDirection.UP
            score < -0.5 -> TrendDirection.STRONG_DOWN
            score < -0.2 -> TrendDirection.DOWN
            else -> TrendDirection.NEUTRAL
        }
    }
}

/**
 * Multi-timeframe analysis summary.
 */
data class MultiTimeframeSummary(
    val snapshots: Map<MultiTimeframeCalculator.Timeframe, IndicatorSnapshot?>,
    val alignmentScore: Double,  // -1.0 to +1.0
    val dominantTrend: TrendDirection
)

/**
 * Trend direction for MTF analysis.
 */
enum class TrendDirection {
    STRONG_DOWN,
    DOWN,
    NEUTRAL,
    UP,
    STRONG_UP
}

// =============================================================================
// IMPORT FOR AI BOARD INTEGRATION
// =============================================================================

// Re-export IndicatorSnapshot type for convenience
// Note: The actual IndicatorSnapshot is defined in AIBoardStahl.kt
// This calculator produces values compatible with that data class

/**
 * Bridge to create MarketContext from IndicatorCalculator.
 * 
 * Usage:
 * ```kotlin
 * val calculator = IndicatorCalculator()
 * // ... add bars ...
 * 
 * val snapshot = calculator.generateSnapshot()
 * val context = MarketContext.build(snapshot, hourUTC = 14)
 * 
 * // Now use with AI Board
 * val recommendation = selector.recommendPreset(context)
 * ```
 */
typealias IndicatorSnapshot = com.miwealth.sovereignvantage.core.trading.IndicatorSnapshot
