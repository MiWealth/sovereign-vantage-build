package com.miwealth.sovereignvantage.core.indicators

import kotlin.math.*

/**
 * Momentum Indicators - 25+ Implementations
 * Ported from advanced-strategies.ts
 * 
 * © 2025 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 */
object MomentumIndicators {

    /**
     * Relative Strength Index (RSI)
     */
    fun rsi(prices: List<Double>, period: Int = 14): Double {
        if (prices.size < period + 1) return 50.0
        
        var gains = 0.0
        var losses = 0.0
        
        for (i in prices.size - period until prices.size) {
            val change = prices[i] - prices[i - 1]
            if (change > 0) gains += change
            else losses -= change
        }
        
        val avgGain = gains / period
        val avgLoss = losses / period
        
        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100 - (100 / (1 + rs))
    }

    data class StochRSIResult(val k: Double, val d: Double)

    /**
     * Stochastic RSI
     */
    fun stochRSI(prices: List<Double>, rsiPeriod: Int = 14, stochPeriod: Int = 14): StochRSIResult {
        val rsiValues = mutableListOf<Double>()
        for (i in rsiPeriod..prices.size) {
            rsiValues.add(rsi(prices.subList(0, i), rsiPeriod))
        }
        
        if (rsiValues.size < stochPeriod) return StochRSIResult(50.0, 50.0)
        
        val recentRSI = rsiValues.takeLast(stochPeriod)
        val minRSI = recentRSI.min() ?: 0.0
        val maxRSI = recentRSI.max() ?: 100.0
        
        val k = if (maxRSI == minRSI) 50.0 
                else ((rsiValues.last() - minRSI) / (maxRSI - minRSI)) * 100
        val d = TrendIndicators.sma(rsiValues.takeLast(3), 3)
        
        return StochRSIResult(k, d)
    }

    data class MACDResult(val macd: Double, val signal: Double, val histogram: Double)

    /**
     * MACD (Moving Average Convergence Divergence)
     */
    fun macd(
        prices: List<Double>,
        fastPeriod: Int = 12,
        slowPeriod: Int = 26,
        signalPeriod: Int = 9
    ): MACDResult {
        val fastEMA = TrendIndicators.ema(prices, fastPeriod)
        val slowEMA = TrendIndicators.ema(prices, slowPeriod)
        val macdLine = fastEMA - slowEMA
        
        // Calculate signal line (EMA of MACD)
        val macdValues = mutableListOf<Double>()
        for (i in slowPeriod..prices.size) {
            val fast = TrendIndicators.ema(prices.subList(0, i), fastPeriod)
            val slow = TrendIndicators.ema(prices.subList(0, i), slowPeriod)
            macdValues.add(fast - slow)
        }
        
        val signalLine = TrendIndicators.ema(macdValues, signalPeriod)
        val histogram = macdLine - signalLine
        
        return MACDResult(macdLine, signalLine, histogram)
    }

    data class StochasticResult(val k: Double, val d: Double)

    /**
     * Stochastic Oscillator
     */
    fun stochastic(
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>,
        kPeriod: Int = 14,
        dPeriod: Int = 3
    ): StochasticResult {
        if (highs.size < kPeriod) return StochasticResult(50.0, 50.0)
        
        val highSlice = highs.takeLast(kPeriod)
        val lowSlice = lows.takeLast(kPeriod)
        
        val highestHigh = highSlice.max() ?: 0.0
        val lowestLow = lowSlice.min() ?: 0.0
        val currentClose = closes.last()
        
        val k = if (highestHigh == lowestLow) 50.0 
                else ((currentClose - lowestLow) / (highestHigh - lowestLow)) * 100
        
        // Calculate %D (SMA of %K)
        val kValues = mutableListOf<Double>()
        for (i in kPeriod..highs.size) {
            val h = highs.subList(i - kPeriod, i).max() ?: 0.0
            val l = lows.subList(i - kPeriod, i).min() ?: 0.0
            val c = closes[i - 1]
            kValues.add(if (h == l) 50.0 else ((c - l) / (h - l)) * 100)
        }
        
        val d = TrendIndicators.sma(kValues.takeLast(dPeriod), dPeriod)
        
        return StochasticResult(k, d)
    }

    /**
     * Williams %R
     */
    fun williamsR(highs: List<Double>, lows: List<Double>, closes: List<Double>, period: Int = 14): Double {
        if (highs.size < period) return -50.0
        
        val highSlice = highs.takeLast(period)
        val lowSlice = lows.takeLast(period)
        
        val highestHigh = highSlice.max() ?: 0.0
        val lowestLow = lowSlice.min() ?: 0.0
        val currentClose = closes.last()
        
        return if (highestHigh == lowestLow) -50.0 
               else ((highestHigh - currentClose) / (highestHigh - lowestLow)) * -100
    }

    /**
     * Commodity Channel Index (CCI)
     */
    fun cci(highs: List<Double>, lows: List<Double>, closes: List<Double>, period: Int = 20): Double {
        if (highs.size < period) return 0.0
        
        val typicalPrices = highs.mapIndexed { i, _ -> (highs[i] + lows[i] + closes[i]) / 3 }
        val slice = typicalPrices.takeLast(period)
        val sma = slice.average()
        
        val meanDeviation = slice.map { abs(it - sma) }.average()
        
        return if (meanDeviation == 0.0) 0.0 
               else (typicalPrices.last() - sma) / (0.015 * meanDeviation)
    }

    /**
     * Rate of Change (ROC)
     */
    fun roc(prices: List<Double>, period: Int = 12): Double {
        if (prices.size < period + 1) return 0.0
        val previousPrice = prices[prices.size - period - 1]
        val currentPrice = prices.last()
        return if (previousPrice == 0.0) 0.0 else ((currentPrice - previousPrice) / previousPrice) * 100
    }

    /**
     * Momentum (Price Change)
     */
    fun momentum(prices: List<Double>, period: Int = 10): Double {
        if (prices.size < period + 1) return 0.0
        return prices.last() - prices[prices.size - period - 1]
    }

    /**
     * Ultimate Oscillator
     * Multi-timeframe momentum
     */
    fun ultimateOscillator(
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>,
        period1: Int = 7,
        period2: Int = 14,
        period3: Int = 28
    ): Double {
        if (highs.size < period3 + 1) return 50.0
        
        val bp = mutableListOf<Double>()
        val tr = mutableListOf<Double>()
        
        for (i in 1 until highs.size) {
            val prevClose = closes[i - 1]
            bp.add(closes[i] - minOf(lows[i], prevClose))
            tr.add(maxOf(highs[i], prevClose) - minOf(lows[i], prevClose))
        }
        
        fun avgRatio(n: Int): Double {
            val bpSum = bp.takeLast(n).sum()
            val trSum = tr.takeLast(n).sum()
            return if (trSum == 0.0) 0.0 else bpSum / trSum
        }
        
        val avg1 = avgRatio(period1)
        val avg2 = avgRatio(period2)
        val avg3 = avgRatio(period3)
        
        return ((4 * avg1) + (2 * avg2) + avg3) / 7 * 100
    }

    /**
     * Awesome Oscillator
     */
    fun awesomeOscillator(highs: List<Double>, lows: List<Double>): Double {
        val medianPrices = highs.mapIndexed { i, h -> (h + lows[i]) / 2 }
        val sma5 = TrendIndicators.sma(medianPrices, 5)
        val sma34 = TrendIndicators.sma(medianPrices, 34)
        return sma5 - sma34
    }

    /**
     * Accelerator Oscillator
     */
    fun acceleratorOscillator(highs: List<Double>, lows: List<Double>): Double {
        val ao = awesomeOscillator(highs, lows)
        
        val aoValues = mutableListOf<Double>()
        for (i in 34..highs.size) {
            aoValues.add(awesomeOscillator(highs.subList(0, i), lows.subList(0, i)))
        }
        
        val aoSMA5 = TrendIndicators.sma(aoValues, 5)
        return ao - aoSMA5
    }

    /**
     * Percentage Price Oscillator (PPO)
     */
    fun ppo(prices: List<Double>, fastPeriod: Int = 12, slowPeriod: Int = 26): Double {
        val fastEMA = TrendIndicators.ema(prices, fastPeriod)
        val slowEMA = TrendIndicators.ema(prices, slowPeriod)
        return if (slowEMA == 0.0) 0.0 else ((fastEMA - slowEMA) / slowEMA) * 100
    }

    /**
     * Detrended Price Oscillator (DPO)
     */
    fun dpo(prices: List<Double>, period: Int = 20): Double {
        val shift = period / 2 + 1
        if (prices.size < period + shift) return 0.0
        
        val sma = TrendIndicators.sma(prices.dropLast(shift), period)
        return prices[prices.size - shift] - sma
    }

    data class KSTResult(val kst: Double, val signal: Double)

    /**
     * Know Sure Thing (KST)
     */
    fun kst(prices: List<Double>): KSTResult {
        val roc1 = roc(prices, 10)
        val roc2 = roc(prices, 15)
        val roc3 = roc(prices, 20)
        val roc4 = roc(prices, 30)
        
        val kst = (TrendIndicators.sma(listOf(roc1), 10) * 1) +
                  (TrendIndicators.sma(listOf(roc2), 10) * 2) +
                  (TrendIndicators.sma(listOf(roc3), 10) * 3) +
                  (TrendIndicators.sma(listOf(roc4), 15) * 4)
        
        val signal = TrendIndicators.sma(listOf(kst), 9)
        
        return KSTResult(kst, signal)
    }

    /**
     * True Strength Index (TSI)
     */
    fun tsi(prices: List<Double>, longPeriod: Int = 25, shortPeriod: Int = 13): Double {
        if (prices.size < 2) return 0.0
        
        val changes = mutableListOf<Double>()
        for (i in 1 until prices.size) {
            changes.add(prices[i] - prices[i - 1])
        }
        
        val absChanges = changes.map { abs(it) }
        
        val doubleSmoothedPC = TrendIndicators.ema(
            listOf(TrendIndicators.ema(changes, longPeriod)),
            shortPeriod
        )
        
        val doubleSmoothedAbsPC = TrendIndicators.ema(
            listOf(TrendIndicators.ema(absChanges, longPeriod)),
            shortPeriod
        )
        
        return if (doubleSmoothedAbsPC == 0.0) 0.0 
               else (doubleSmoothedPC / doubleSmoothedAbsPC) * 100
    }

    /**
     * Coppock Curve
     */
    fun coppockCurve(prices: List<Double>): Double {
        val roc14 = roc(prices, 14)
        val roc11 = roc(prices, 11)
        return TrendIndicators.wma(listOf(roc14 + roc11), 10)
    }

    /**
     * Elder Force Index
     */
    fun elderForceIndex(closes: List<Double>, volumes: List<Double>, period: Int = 13): Double {
        if (closes.size < 2) return 0.0
        
        val forceValues = mutableListOf<Double>()
        for (i in 1 until closes.size) {
            forceValues.add((closes[i] - closes[i - 1]) * volumes[i])
        }
        
        return TrendIndicators.ema(forceValues, period)
    }

    /**
     * Chande Momentum Oscillator (CMO)
     */
    fun cmo(prices: List<Double>, period: Int = 14): Double {
        if (prices.size < period + 1) return 0.0
        
        var sumUp = 0.0
        var sumDown = 0.0
        
        for (i in prices.size - period until prices.size) {
            val change = prices[i] - prices[i - 1]
            if (change > 0) sumUp += change
            else sumDown += abs(change)
        }
        
        return if (sumUp + sumDown == 0.0) 0.0 
               else ((sumUp - sumDown) / (sumUp + sumDown)) * 100
    }

    /**
     * Relative Vigor Index (RVI)
     */
    fun rvi(
        opens: List<Double>,
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>,
        period: Int = 10
    ): Double {
        if (closes.size < period + 3) return 0.0
        
        var numerator = 0.0
        var denominator = 0.0
        
        for (i in closes.size - period until closes.size) {
            val closeOpen = closes[i] - opens[i]
            val highLow = highs[i] - lows[i]
            
            // Smoothing with weights 1, 2, 2, 1
            val a = if (i >= 3) closeOpen else 0.0
            val b = if (i >= 2) closes[i-1] - opens[i-1] else 0.0
            val c = if (i >= 1) closes[i-2] - opens[i-2] else 0.0
            val d = if (i >= 0) closes[i-3] - opens[i-3] else 0.0
            
            numerator += (a + 2*b + 2*c + d) / 6
            denominator += highLow
        }
        
        return if (denominator == 0.0) 0.0 else (numerator / denominator) * 100
    }
}
