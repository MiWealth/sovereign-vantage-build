package com.miwealth.sovereignvantage.core.indicators

import kotlin.math.*

/**
 * Volatility Indicators - 15+ Implementations
 * Ported from advanced-strategies.ts
 * 
 * © 2025 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 */
object VolatilityIndicators {

    /**
     * Average True Range (ATR)
     */
    fun atr(highs: List<Double>, lows: List<Double>, closes: List<Double>, period: Int = 14): Double {
        if (highs.size < period + 1) return 0.0
        
        val trValues = mutableListOf<Double>()
        for (i in 1 until highs.size) {
            val tr = maxOf(
                highs[i] - lows[i],
                abs(highs[i] - closes[i - 1]),
                abs(lows[i] - closes[i - 1])
            )
            trValues.add(tr)
        }
        
        return TrendIndicators.ema(trValues, period)
    }

    data class BollingerBandsResult(
        val upper: Double,
        val middle: Double,
        val lower: Double,
        val bandwidth: Double,
        val percentB: Double
    )

    /**
     * Bollinger Bands
     */
    fun bollingerBands(prices: List<Double>, period: Int = 20, stdDev: Double = 2.0): BollingerBandsResult {
        val middle = TrendIndicators.sma(prices, period)
        val slice = prices.takeLast(period)
        
        val variance = slice.map { (it - middle).pow(2) }.average()
        val std = sqrt(variance)
        
        val upper = middle + stdDev * std
        val lower = middle - stdDev * std
        val bandwidth = ((upper - lower) / middle) * 100
        val percentB = (prices.last() - lower) / (upper - lower)
        
        return BollingerBandsResult(upper, middle, lower, bandwidth, percentB)
    }

    data class KeltnerChannelsResult(val upper: Double, val middle: Double, val lower: Double)

    /**
     * Keltner Channels
     */
    fun keltnerChannels(
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>,
        emaPeriod: Int = 20,
        atrPeriod: Int = 10,
        multiplier: Double = 2.0
    ): KeltnerChannelsResult {
        val middle = TrendIndicators.ema(closes, emaPeriod)
        val atrValue = atr(highs, lows, closes, atrPeriod)
        
        return KeltnerChannelsResult(
            upper = middle + multiplier * atrValue,
            middle = middle,
            lower = middle - multiplier * atrValue
        )
    }

    data class DonchianChannelsResult(val upper: Double, val middle: Double, val lower: Double)

    /**
     * Donchian Channels
     */
    fun donchianChannels(highs: List<Double>, lows: List<Double>, period: Int = 20): DonchianChannelsResult {
        val highSlice = highs.takeLast(period)
        val lowSlice = lows.takeLast(period)
        
        val upper = highSlice.max() ?: 0.0
        val lower = lowSlice.min() ?: 0.0
        val middle = (upper + lower) / 2
        
        return DonchianChannelsResult(upper, middle, lower)
    }

    /**
     * Standard Deviation
     */
    fun standardDeviation(prices: List<Double>, period: Int = 20): Double {
        if (prices.size < period) return 0.0
        
        val slice = prices.takeLast(period)
        val mean = slice.average()
        val variance = slice.map { (it - mean).pow(2) }.average()
        
        return sqrt(variance)
    }

    /**
     * Historical Volatility (Annualized)
     */
    fun historicalVolatility(prices: List<Double>, period: Int = 20): Double {
        if (prices.size < period + 1) return 0.0
        
        val returns = mutableListOf<Double>()
        for (i in prices.size - period until prices.size) {
            returns.add(ln(prices[i] / prices[i - 1]))
        }
        
        val mean = returns.average()
        val variance = returns.map { (it - mean).pow(2) }.sum() / (period - 1)
        
        return sqrt(variance) * sqrt(252.0) * 100 // Annualized
    }

    /**
     * Chaikin Volatility
     */
    fun chaikinVolatility(
        highs: List<Double>,
        lows: List<Double>,
        emaPeriod: Int = 10,
        rocPeriod: Int = 10
    ): Double {
        val hlDiff = highs.mapIndexed { i, h -> h - lows[i] }
        val ema = TrendIndicators.ema(hlDiff, emaPeriod)
        
        val emaValues = mutableListOf<Double>()
        for (i in emaPeriod..hlDiff.size) {
            emaValues.add(TrendIndicators.ema(hlDiff.subList(0, i), emaPeriod))
        }
        
        if (emaValues.size < rocPeriod + 1) return 0.0
        
        val prevEma = emaValues[emaValues.size - rocPeriod - 1]
        return if (prevEma == 0.0) 0.0 else ((ema - prevEma) / prevEma) * 100
    }

    /**
     * GARCH(1,1) Volatility Forecast
     */
    fun garch(
        returns: List<Double>,
        omega: Double = 0.000001,
        alpha: Double = 0.1,
        beta: Double = 0.85
    ): Double {
        if (returns.size < 2) return 0.0
        
        var variance = returns.map { it * it }.average()
        
        for (r in returns) {
            variance = omega + alpha * r * r + beta * variance
        }
        
        return sqrt(variance) * sqrt(252.0) * 100 // Annualized volatility
    }

    /**
     * Ulcer Index
     * Measures downside volatility
     */
    fun ulcerIndex(prices: List<Double>, period: Int = 14): Double {
        if (prices.size < period) return 0.0
        
        val slice = prices.takeLast(period)
        val maxPrice = slice.max() ?: 0.0
        
        var sumSquared = 0.0
        for (price in slice) {
            val percentDrawdown = ((price - maxPrice) / maxPrice) * 100
            sumSquared += percentDrawdown * percentDrawdown
        }
        
        return sqrt(sumSquared / period)
    }

    /**
     * Mass Index
     * Detects trend reversals
     */
    fun massIndex(highs: List<Double>, lows: List<Double>, emaPeriod: Int = 9, sumPeriod: Int = 25): Double {
        val hlDiff = highs.mapIndexed { i, h -> h - lows[i] }
        
        val singleEMA = mutableListOf<Double>()
        for (i in emaPeriod..hlDiff.size) {
            singleEMA.add(TrendIndicators.ema(hlDiff.subList(0, i), emaPeriod))
        }
        
        val doubleEMA = mutableListOf<Double>()
        for (i in emaPeriod..singleEMA.size) {
            doubleEMA.add(TrendIndicators.ema(singleEMA.subList(0, i), emaPeriod))
        }
        
        if (doubleEMA.size < sumPeriod) return 0.0
        
        var massIndex = 0.0
        for (i in doubleEMA.size - sumPeriod until doubleEMA.size) {
            if (doubleEMA[i] != 0.0) {
                massIndex += singleEMA[i + emaPeriod - 1] / doubleEMA[i]
            }
        }
        
        return massIndex
    }

    /**
     * Normalized ATR (NATR)
     * ATR as percentage of price
     */
    fun natr(highs: List<Double>, lows: List<Double>, closes: List<Double>, period: Int = 14): Double {
        val atrValue = atr(highs, lows, closes, period)
        val currentClose = closes.last()
        return if (currentClose == 0.0) 0.0 else (atrValue / currentClose) * 100
    }

    /**
     * True Range
     */
    fun trueRange(high: Double, low: Double, previousClose: Double): Double {
        return maxOf(
            high - low,
            abs(high - previousClose),
            abs(low - previousClose)
        )
    }

    /**
     * Average Daily Range (ADR)
     */
    fun averageDailyRange(highs: List<Double>, lows: List<Double>, period: Int = 14): Double {
        if (highs.size < period) return 0.0
        
        val ranges = highs.takeLast(period).mapIndexed { i, h -> 
            h - lows.takeLast(period)[i]
        }
        
        return ranges.average()
    }
}

/**
 * Volume Indicators - 15+ Implementations
 * Ported from advanced-strategies.ts
 */
object VolumeIndicators {

    /**
     * On-Balance Volume (OBV)
     */
    fun obv(closes: List<Double>, volumes: List<Double>): Double {
        var obv = 0.0
        
        for (i in 1 until closes.size) {
            obv += when {
                closes[i] > closes[i - 1] -> volumes[i]
                closes[i] < closes[i - 1] -> -volumes[i]
                else -> 0.0
            }
        }
        
        return obv
    }

    /**
     * Accumulation/Distribution Line
     */
    fun adl(highs: List<Double>, lows: List<Double>, closes: List<Double>, volumes: List<Double>): Double {
        var adl = 0.0
        
        for (i in highs.indices) {
            val mfm = if (highs[i] == lows[i]) 0.0
                      else ((closes[i] - lows[i]) - (highs[i] - closes[i])) / (highs[i] - lows[i])
            val mfv = mfm * volumes[i]
            adl += mfv
        }
        
        return adl
    }

    /**
     * Chaikin Money Flow (CMF)
     */
    fun cmf(
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>,
        volumes: List<Double>,
        period: Int = 20
    ): Double {
        if (highs.size < period) return 0.0
        
        var mfvSum = 0.0
        var volumeSum = 0.0
        
        for (i in highs.size - period until highs.size) {
            val mfm = if (highs[i] == lows[i]) 0.0
                      else ((closes[i] - lows[i]) - (highs[i] - closes[i])) / (highs[i] - lows[i])
            mfvSum += mfm * volumes[i]
            volumeSum += volumes[i]
        }
        
        return if (volumeSum == 0.0) 0.0 else mfvSum / volumeSum
    }

    /**
     * Money Flow Index (MFI)
     */
    fun mfi(
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>,
        volumes: List<Double>,
        period: Int = 14
    ): Double {
        if (highs.size < period + 1) return 50.0
        
        var positiveFlow = 0.0
        var negativeFlow = 0.0
        
        for (i in highs.size - period until highs.size) {
            val typicalPrice = (highs[i] + lows[i] + closes[i]) / 3
            val prevTypicalPrice = (highs[i - 1] + lows[i - 1] + closes[i - 1]) / 3
            val rawMoneyFlow = typicalPrice * volumes[i]
            
            if (typicalPrice > prevTypicalPrice) {
                positiveFlow += rawMoneyFlow
            } else {
                negativeFlow += rawMoneyFlow
            }
        }
        
        if (negativeFlow == 0.0) return 100.0
        val moneyRatio = positiveFlow / negativeFlow
        return 100 - (100 / (1 + moneyRatio))
    }

    /**
     * Volume Weighted Average Price (VWAP)
     */
    fun vwap(highs: List<Double>, lows: List<Double>, closes: List<Double>, volumes: List<Double>): Double {
        var cumulativeTPV = 0.0
        var cumulativeVolume = 0.0
        
        for (i in highs.indices) {
            val typicalPrice = (highs[i] + lows[i] + closes[i]) / 3
            cumulativeTPV += typicalPrice * volumes[i]
            cumulativeVolume += volumes[i]
        }
        
        return if (cumulativeVolume == 0.0) 0.0 else cumulativeTPV / cumulativeVolume
    }

    /**
     * Volume Rate of Change (VROC)
     */
    fun vroc(volumes: List<Double>, period: Int = 14): Double {
        if (volumes.size < period + 1) return 0.0
        
        val currentVolume = volumes.last()
        val previousVolume = volumes[volumes.size - period - 1]
        
        return if (previousVolume == 0.0) 0.0 
               else ((currentVolume - previousVolume) / previousVolume) * 100
    }

    /**
     * Ease of Movement (EMV)
     */
    fun emv(highs: List<Double>, lows: List<Double>, volumes: List<Double>, period: Int = 14): Double {
        if (highs.size < period + 1) return 0.0
        
        val emvValues = mutableListOf<Double>()
        for (i in 1 until highs.size) {
            val distanceMoved = ((highs[i] + lows[i]) / 2) - ((highs[i - 1] + lows[i - 1]) / 2)
            val boxRatio = (volumes[i] / 100_000_000) / (highs[i] - lows[i])
            emvValues.add(if (boxRatio == 0.0) 0.0 else distanceMoved / boxRatio)
        }
        
        return TrendIndicators.sma(emvValues, period)
    }

    /**
     * Negative Volume Index (NVI)
     */
    fun nvi(closes: List<Double>, volumes: List<Double>): Double {
        var nvi = 1000.0
        
        for (i in 1 until closes.size) {
            if (volumes[i] < volumes[i - 1]) {
                nvi += nvi * ((closes[i] - closes[i - 1]) / closes[i - 1])
            }
        }
        
        return nvi
    }

    /**
     * Positive Volume Index (PVI)
     */
    fun pvi(closes: List<Double>, volumes: List<Double>): Double {
        var pvi = 1000.0
        
        for (i in 1 until closes.size) {
            if (volumes[i] > volumes[i - 1]) {
                pvi += pvi * ((closes[i] - closes[i - 1]) / closes[i - 1])
            }
        }
        
        return pvi
    }

    /**
     * Volume Price Trend (VPT)
     */
    fun vpt(closes: List<Double>, volumes: List<Double>): Double {
        var vpt = 0.0
        
        for (i in 1 until closes.size) {
            vpt += volumes[i] * ((closes[i] - closes[i - 1]) / closes[i - 1])
        }
        
        return vpt
    }

    /**
     * Klinger Volume Oscillator (KVO)
     */
    fun kvo(
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>,
        volumes: List<Double>,
        fastPeriod: Int = 34,
        slowPeriod: Int = 55
    ): Double {
        if (highs.size < slowPeriod + 1) return 0.0
        
        val vf = mutableListOf<Double>()
        var trend = 1.0
        
        for (i in 1 until highs.size) {
            val hlc = highs[i] + lows[i] + closes[i]
            val prevHlc = highs[i - 1] + lows[i - 1] + closes[i - 1]
            
            trend = if (hlc > prevHlc) 1.0 else -1.0
            val dm = highs[i] - lows[i]
            val cm = if (i > 1 && vf.isNotEmpty()) {
                val prevTrend = if (highs[i-1] + lows[i-1] + closes[i-1] > 
                    highs[i-2] + lows[i-2] + closes[i-2]) 1.0 else -1.0
                if (trend == prevTrend) {
                    val lastVf = vf.last()
                    dm + (kotlin.math.abs(lastVf) / volumes[i-1] * dm)
                } else {
                    dm
                }
            } else dm
            
            vf.add(volumes[i] * abs(2 * dm / cm - 1) * trend * 100)
        }
        
        val fastEma = TrendIndicators.ema(vf, fastPeriod)
        val slowEma = TrendIndicators.ema(vf, slowPeriod)
        
        return fastEma - slowEma
    }

    /**
     * Force Index
     */
    fun forceIndex(closes: List<Double>, volumes: List<Double>, period: Int = 13): Double {
        if (closes.size < 2) return 0.0
        
        val forceValues = mutableListOf<Double>()
        for (i in 1 until closes.size) {
            forceValues.add((closes[i] - closes[i - 1]) * volumes[i])
        }
        
        return TrendIndicators.ema(forceValues, period)
    }
}
