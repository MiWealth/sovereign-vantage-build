package com.miwealth.sovereignvantage.core.indicators

import kotlin.math.*

/**
 * Trend Indicators - 20+ Implementations
 * Ported from advanced-strategies.ts
 * 
 * © 2025 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO: Arthur Iain McManus (1966-2025)
 */
object TrendIndicators {

    /**
     * Simple Moving Average (SMA)
     */
    fun sma(prices: List<Double>, period: Int): Double {
        if (prices.size < period) return Double.NaN
        return prices.takeLast(period).average()
    }

    /**
     * Exponential Moving Average (EMA)
     */
    fun ema(prices: List<Double>, period: Int): Double {
        if (prices.size < period) return Double.NaN
        val multiplier = 2.0 / (period + 1)
        var ema = prices[0]
        for (i in 1 until prices.size) {
            ema = (prices[i] - ema) * multiplier + ema
        }
        return ema
    }

    /**
     * Double Exponential Moving Average (DEMA)
     * Reduces lag compared to standard EMA
     */
    fun dema(prices: List<Double>, period: Int): Double {
        val ema1 = ema(prices, period)
        if (ema1.isNaN()) return Double.NaN
        val emaOfEma = ema(prices.dropLast(1) + ema1, period)
        return 2 * ema1 - emaOfEma
    }

    /**
     * Triple Exponential Moving Average (TEMA)
     * Further lag reduction
     */
    fun tema(prices: List<Double>, period: Int): Double {
        val ema1 = ema(prices, period)
        if (ema1.isNaN()) return Double.NaN
        val ema2 = ema(prices.dropLast(1) + ema1, period)
        val ema3 = ema(prices.dropLast(2) + listOf(ema1, ema2), period)
        return 3 * ema1 - 3 * ema2 + ema3
    }

    /**
     * Weighted Moving Average (WMA)
     * Gives more weight to recent prices
     */
    fun wma(prices: List<Double>, period: Int): Double {
        if (prices.size < period) return Double.NaN
        val slice = prices.takeLast(period)
        var weightedSum = 0.0
        var weightSum = 0.0
        for (i in 0 until period) {
            val weight = (i + 1).toDouble()
            weightedSum += slice[i] * weight
            weightSum += weight
        }
        return weightedSum / weightSum
    }

    /**
     * Hull Moving Average (HMA)
     * Significantly reduces lag while maintaining smoothness
     */
    fun hma(prices: List<Double>, period: Int): Double {
        val halfPeriod = period / 2
        val sqrtPeriod = sqrt(period.toDouble()).toInt()
        
        val wma1 = wma(prices, halfPeriod)
        val wma2 = wma(prices, period)
        if (wma1.isNaN() || wma2.isNaN()) return Double.NaN
        
        val rawHma = 2 * wma1 - wma2
        return wma(prices.dropLast(1) + rawHma, sqrtPeriod)
    }

    /**
     * Kaufman Adaptive Moving Average (KAMA)
     * Adapts to market volatility
     */
    fun kama(
        prices: List<Double>,
        period: Int = 10,
        fastPeriod: Int = 2,
        slowPeriod: Int = 30
    ): Double {
        if (prices.size < period) return Double.NaN
        
        // Calculate efficiency ratio
        val change = abs(prices.last() - prices[prices.size - period])
        var volatility = 0.0
        for (i in prices.size - period until prices.size) {
            volatility += abs(prices[i] - prices[i - 1])
        }
        val er = if (volatility != 0.0) change / volatility else 0.0
        
        // Calculate smoothing constant
        val fastSC = 2.0 / (fastPeriod + 1)
        val slowSC = 2.0 / (slowPeriod + 1)
        val sc = (er * (fastSC - slowSC) + slowSC).pow(2)
        
        // Calculate KAMA
        var kama = prices[prices.size - period]
        for (i in prices.size - period + 1 until prices.size) {
            kama += sc * (prices[i] - kama)
        }
        
        return kama
    }

    /**
     * McGinley Dynamic
     * Self-adjusting moving average
     */
    fun mcGinleyDynamic(prices: List<Double>, period: Int = 14): Double {
        if (prices.size < 2) return prices.lastOrNull() ?: Double.NaN
        
        val prevMD = if (prices.size > period) {
            sma(prices.dropLast(1), period)
        } else {
            prices[prices.size - 2]
        }
        val currentPrice = prices.last()
        
        return prevMD + (currentPrice - prevMD) / (period * (currentPrice / prevMD).pow(4))
    }

    /**
     * Arnaud Legoux Moving Average (ALMA)
     * Gaussian-weighted moving average with offset
     */
    fun alma(
        prices: List<Double>,
        period: Int = 9,
        offset: Double = 0.85,
        sigma: Double = 6.0
    ): Double {
        if (prices.size < period) return Double.NaN
        
        val m = offset * (period - 1)
        val s = period / sigma
        
        var weightSum = 0.0
        var sum = 0.0
        
        for (i in 0 until period) {
            val weight = exp(-((i - m).pow(2)) / (2 * s * s))
            sum += prices[prices.size - period + i] * weight
            weightSum += weight
        }
        
        return sum / weightSum
    }

    /**
     * Zero-Lag EMA (ZLEMA)
     * Attempts to eliminate lag entirely
     */
    fun zlema(prices: List<Double>, period: Int): Double {
        if (prices.size < period) return Double.NaN
        
        val lag = (period - 1) / 2
        val zlemaData = prices.mapIndexed { i, price ->
            if (i >= lag) {
                price + (price - prices[i - lag])
            } else {
                price
            }
        }
        
        return ema(zlemaData, period)
    }

    /**
     * Volume Weighted Moving Average (VWMA)
     */
    fun vwma(prices: List<Double>, volumes: List<Double>, period: Int): Double {
        if (prices.size < period || volumes.size < period) return Double.NaN
        
        val priceSlice = prices.takeLast(period)
        val volumeSlice = volumes.takeLast(period)
        
        var sumPV = 0.0
        var sumV = 0.0
        for (i in 0 until period) {
            sumPV += priceSlice[i] * volumeSlice[i]
            sumV += volumeSlice[i]
        }
        
        return if (sumV > 0) sumPV / sumV else Double.NaN
    }

    data class ParabolicSARResult(val value: Double, val trend: String)

    /**
     * Parabolic SAR (Stop and Reverse)
     */
    fun parabolicSAR(
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>,
        af: Double = 0.02,
        maxAf: Double = 0.2
    ): ParabolicSARResult {
        if (highs.size < 2) return ParabolicSARResult(closes.last(), "up")
        
        var trend = if (closes[1] > closes[0]) "up" else "down"
        var sar = if (trend == "up") lows[0] else highs[0]
        var ep = if (trend == "up") highs[0] else lows[0]
        var currentAf = af
        
        for (i in 1 until highs.size) {
            val prevSar = sar
            
            if (trend == "up") {
                sar = prevSar + currentAf * (ep - prevSar)
                sar = minOf(sar, lows[i - 1], if (i > 1) lows[i - 2] else lows[i - 1])
                
                if (lows[i] < sar) {
                    trend = "down"
                    sar = ep
                    ep = lows[i]
                    currentAf = af
                } else {
                    if (highs[i] > ep) {
                        ep = highs[i]
                        currentAf = minOf(currentAf + af, maxAf)
                    }
                }
            } else {
                sar = prevSar - currentAf * (prevSar - ep)
                sar = maxOf(sar, highs[i - 1], if (i > 1) highs[i - 2] else highs[i - 1])
                
                if (highs[i] > sar) {
                    trend = "up"
                    sar = ep
                    ep = highs[i]
                    currentAf = af
                } else {
                    if (lows[i] < ep) {
                        ep = lows[i]
                        currentAf = minOf(currentAf + af, maxAf)
                    }
                }
            }
        }
        
        return ParabolicSARResult(sar, trend)
    }

    data class IchimokuResult(
        val tenkanSen: Double,
        val kijunSen: Double,
        val senkouSpanA: Double,
        val senkouSpanB: Double,
        val chikouSpan: Double
    )

    /**
     * Ichimoku Cloud Components
     */
    fun ichimoku(
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>,
        tenkanPeriod: Int = 9,
        kijunPeriod: Int = 26,
        senkouBPeriod: Int = 52
    ): IchimokuResult {
        fun highestHigh(arr: List<Double>, period: Int) = arr.takeLast(period).max() ?: 0.0
        fun lowestLow(arr: List<Double>, period: Int) = arr.takeLast(period).min() ?: 0.0
        
        val tenkanSen = (highestHigh(highs, tenkanPeriod) + lowestLow(lows, tenkanPeriod)) / 2
        val kijunSen = (highestHigh(highs, kijunPeriod) + lowestLow(lows, kijunPeriod)) / 2
        val senkouSpanA = (tenkanSen + kijunSen) / 2
        val senkouSpanB = (highestHigh(highs, senkouBPeriod) + lowestLow(lows, senkouBPeriod)) / 2
        val chikouSpan = closes.last()
        
        return IchimokuResult(tenkanSen, kijunSen, senkouSpanA, senkouSpanB, chikouSpan)
    }

    data class SuperTrendResult(val value: Double, val trend: String)

    /**
     * SuperTrend Indicator
     */
    fun superTrend(
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>,
        period: Int = 10,
        multiplier: Double = 3.0
    ): SuperTrendResult {
        if (highs.size < period) return SuperTrendResult(closes.last(), "up")
        
        // Calculate ATR
        val trValues = mutableListOf<Double>()
        for (i in 1 until highs.size) {
            val tr = maxOf(
                highs[i] - lows[i],
                abs(highs[i] - closes[i - 1]),
                abs(lows[i] - closes[i - 1])
            )
            trValues.add(tr)
        }
        
        val atr = trValues.takeLast(period).average()
        val hl2 = (highs.last() + lows.last()) / 2
        
        val upperBand = hl2 + multiplier * atr
        val lowerBand = hl2 - multiplier * atr
        
        val close = closes.last()
        val trend = if (close > upperBand) "up" else if (close < lowerBand) "down" else "up"
        
        return SuperTrendResult(if (trend == "up") lowerBand else upperBand, trend)
    }

    data class ADXResult(val adx: Double, val plusDI: Double, val minusDI: Double)

    /**
     * Average Directional Index (ADX)
     * Measures trend strength
     */
    fun adx(
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>,
        period: Int = 14
    ): ADXResult {
        if (highs.size < period + 1) return ADXResult(0.0, 0.0, 0.0)
        
        val plusDM = mutableListOf<Double>()
        val minusDM = mutableListOf<Double>()
        val tr = mutableListOf<Double>()
        
        for (i in 1 until highs.size) {
            val highDiff = highs[i] - highs[i - 1]
            val lowDiff = lows[i - 1] - lows[i]
            
            plusDM.add(if (highDiff > lowDiff && highDiff > 0) highDiff else 0.0)
            minusDM.add(if (lowDiff > highDiff && lowDiff > 0) lowDiff else 0.0)
            
            tr.add(maxOf(
                highs[i] - lows[i],
                abs(highs[i] - closes[i - 1]),
                abs(lows[i] - closes[i - 1])
            ))
        }
        
        val smoothedPlusDM = ema(plusDM, period)
        val smoothedMinusDM = ema(minusDM, period)
        val smoothedTR = ema(tr, period)
        
        val plusDI = (smoothedPlusDM / smoothedTR) * 100
        val minusDI = (smoothedMinusDM / smoothedTR) * 100
        
        val dx = abs(plusDI - minusDI) / (plusDI + minusDI) * 100
        val adxValue = ema(listOf(dx), period)
        
        return ADXResult(adxValue, plusDI, minusDI)
    }

    data class AroonResult(val aroonUp: Double, val aroonDown: Double, val oscillator: Double)

    /**
     * Aroon Indicator
     * Identifies trend changes
     */
    fun aroon(highs: List<Double>, lows: List<Double>, period: Int = 25): AroonResult {
        if (highs.size < period) return AroonResult(0.0, 0.0, 0.0)
        
        val highSlice = highs.takeLast(period)
        val lowSlice = lows.takeLast(period)
        
        val highestIndex = highSlice.indexOf(highSlice.max())
        val lowestIndex = lowSlice.indexOf(lowSlice.min())
        
        val aroonUp = ((period - (period - 1 - highestIndex)).toDouble() / period) * 100
        val aroonDown = ((period - (period - 1 - lowestIndex)).toDouble() / period) * 100
        val oscillator = aroonUp - aroonDown
        
        return AroonResult(aroonUp, aroonDown, oscillator)
    }

    data class VortexResult(val plusVI: Double, val minusVI: Double)

    /**
     * Vortex Indicator
     */
    fun vortex(
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>,
        period: Int = 14
    ): VortexResult {
        if (highs.size < period + 1) return VortexResult(0.0, 0.0)
        
        var plusVM = 0.0
        var minusVM = 0.0
        var trSum = 0.0
        
        for (i in highs.size - period until highs.size) {
            plusVM += abs(highs[i] - lows[i - 1])
            minusVM += abs(lows[i] - highs[i - 1])
            trSum += maxOf(
                highs[i] - lows[i],
                abs(highs[i] - closes[i - 1]),
                abs(lows[i] - closes[i - 1])
            )
        }
        
        return VortexResult(plusVM / trSum, minusVM / trSum)
    }
}
