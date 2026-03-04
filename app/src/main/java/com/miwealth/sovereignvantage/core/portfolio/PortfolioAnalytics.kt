package com.miwealth.sovereignvantage.core.portfolio

import com.miwealth.sovereignvantage.data.local.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*
import kotlin.math.*

/**
 * PORTFOLIO ANALYTICS ENGINE
 * 
 * Calculates real portfolio metrics from trade history:
 * - Sharpe Ratio, Sortino Ratio, Calmar Ratio
 * - Win rate, profit factor, expectancy
 * - Maximum drawdown, average drawdown
 * - Equity curve and performance attribution
 * - Asset allocation and correlation analysis
 * 
 * © 2025 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO: Arthur Iain McManus (1966-2025)
 * 
 * Dedicated to Cathryn for tolerating me and my quirks. 💘
 */

// ============================================================================
// DATA CLASSES
// ============================================================================

data class PortfolioMetrics(
    // Returns
    val totalReturn: Double,
    val totalReturnPercent: Double,
    val dailyReturn: Double,
    val weeklyReturn: Double,
    val monthlyReturn: Double,
    val ytdReturn: Double,
    val annualizedReturn: Double,
    
    // Risk metrics
    val sharpeRatio: Double,
    val sortinoRatio: Double,
    val calmarRatio: Double,
    val volatility: Double,
    val downsideDeviation: Double,
    val beta: Double?,  // vs benchmark
    val alpha: Double?, // vs benchmark
    
    // Drawdown
    val currentDrawdown: Double,
    val currentDrawdownPercent: Double,
    val maxDrawdown: Double,
    val maxDrawdownPercent: Double,
    val maxDrawdownDuration: Int,  // days
    val averageDrawdown: Double,
    
    // Trading metrics
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val winRate: Double,
    val profitFactor: Double,
    val averageWin: Double,
    val averageLoss: Double,
    val largestWin: Double,
    val largestLoss: Double,
    val expectancy: Double,
    val avgHoldingPeriod: Double,  // hours
    
    // Current state
    val totalEquity: Double,
    val cashBalance: Double,
    val investedValue: Double,
    val unrealizedPnl: Double,
    val realizedPnl: Double,
    val totalFeesPaid: Double,
    
    // Time info
    val calculatedAt: Long,
    val periodStart: Long,
    val periodEnd: Long
)

data class AssetAllocation(
    val symbol: String,
    val baseAsset: String,
    val quantity: Double,
    val currentValue: Double,
    val costBasis: Double,
    val unrealizedPnl: Double,
    val unrealizedPnlPercent: Double,
    val portfolioPercent: Double,
    val targetPercent: Double?,
    val driftPercent: Double?
)

data class PerformanceAttribution(
    val symbol: String,
    val contributionToReturn: Double,
    val contributionPercent: Double,
    val tradeCount: Int,
    val winRate: Double,
    val totalPnl: Double,
    val averagePnl: Double
)

data class EquityCurvePoint(
    val timestamp: Long,
    val equity: Double,
    val drawdown: Double,
    val drawdownPercent: Double,
    val highWaterMark: Double
)

data class PeriodPerformance(
    val periodLabel: String,
    val startDate: Long,
    val endDate: Long,
    val startEquity: Double,
    val endEquity: Double,
    val pnl: Double,
    val pnlPercent: Double,
    val trades: Int,
    val wins: Int,
    val losses: Int
)

data class CorrelationPair(
    val symbol1: String,
    val symbol2: String,
    val correlation: Double,
    val sampleSize: Int
)

// ============================================================================
// PORTFOLIO ANALYTICS ENGINE
// ============================================================================

class PortfolioAnalytics(
    private val enhancedTradeDao: EnhancedTradeDao,
    private val equitySnapshotDao: EquitySnapshotDao,
    private val taxLotDao: TaxLotDao,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    
    companion object {
        const val RISK_FREE_RATE = 0.05  // 5% annual risk-free rate
        const val TRADING_DAYS_PER_YEAR = 252
        const val MILLIS_PER_DAY = 86_400_000L
    }
    
    private val _metrics = MutableStateFlow<PortfolioMetrics?>(null)
    val metrics: StateFlow<PortfolioMetrics?> = _metrics.asStateFlow()
    
    private val _equityCurve = MutableStateFlow<List<EquityCurvePoint>>(emptyList())
    val equityCurve: StateFlow<List<EquityCurvePoint>> = _equityCurve.asStateFlow()
    
    // ========================================================================
    // MAIN CALCULATION
    // ========================================================================
    
    /**
     * Calculate all portfolio metrics for a given period
     */
    suspend fun calculateMetrics(
        periodStart: Long = 0,
        periodEnd: Long = System.currentTimeMillis(),
        currentEquity: Double,
        cashBalance: Double
    ): PortfolioMetrics {
        return withContext(Dispatchers.Default) {
            // Get all trades in period
            val trades = enhancedTradeDao.getTradesInRange(periodStart, periodEnd).first()
            
            // Get equity snapshots for returns calculation
            val snapshots = equitySnapshotDao.getSnapshotsInRange("DAILY", periodStart, periodEnd)
            
            // Calculate returns series
            val dailyReturns = calculateDailyReturns(snapshots)
            
            // Trading stats
            val closedTrades = trades.filter { it.realizedPnl != null }
            val winningTrades = closedTrades.filter { (it.realizedPnl ?: 0.0) > 0 }
            val losingTrades = closedTrades.filter { (it.realizedPnl ?: 0.0) < 0 }
            
            val totalRealizedPnl = closedTrades.sumOf { it.realizedPnl ?: 0.0 }
            val totalFees = trades.sumOf { it.totalFees }
            
            // Win/Loss metrics
            val winRate = if (closedTrades.isNotEmpty()) {
                (winningTrades.size.toDouble() / closedTrades.size) * 100
            } else 0.0
            
            val grossProfit = winningTrades.sumOf { it.realizedPnl ?: 0.0 }
            val grossLoss = abs(losingTrades.sumOf { it.realizedPnl ?: 0.0 })
            val profitFactor = if (grossLoss > 0) grossProfit / grossLoss else grossProfit
            
            val avgWin = if (winningTrades.isNotEmpty()) {
                winningTrades.sumOf { it.realizedPnl ?: 0.0 } / winningTrades.size
            } else 0.0
            
            val avgLoss = if (losingTrades.isNotEmpty()) {
                losingTrades.sumOf { it.realizedPnl ?: 0.0 } / losingTrades.size
            } else 0.0
            
            val largestWin = winningTrades.maxOfOrNull { it.realizedPnl ?: 0.0 } ?: 0.0
            val largestLoss = losingTrades.minOfOrNull { it.realizedPnl ?: 0.0 } ?: 0.0
            
            // Expectancy = (Win% × Avg Win) + (Loss% × Avg Loss)
            val winPct = winRate / 100
            val lossPct = 1 - winPct
            val expectancy = (winPct * avgWin) + (lossPct * avgLoss)
            
            // Average holding period
            val avgHoldingPeriod = calculateAverageHoldingPeriod(closedTrades)
            
            // Risk metrics
            val volatility = calculateVolatility(dailyReturns)
            val downsideDeviation = calculateDownsideDeviation(dailyReturns)
            val sharpeRatio = calculateSharpeRatio(dailyReturns, volatility)
            val sortinoRatio = calculateSortinoRatio(dailyReturns, downsideDeviation)
            
            // Drawdown analysis
            val drawdownAnalysis = calculateDrawdownAnalysis(snapshots, currentEquity)
            val calmarRatio = if (drawdownAnalysis.maxDrawdownPercent > 0) {
                (dailyReturns.average() * TRADING_DAYS_PER_YEAR) / drawdownAnalysis.maxDrawdownPercent
            } else 0.0
            
            // Period returns
            val startEquity = snapshots.firstOrNull()?.totalEquity ?: currentEquity
            val totalReturn = currentEquity - startEquity
            val totalReturnPercent = if (startEquity > 0) (totalReturn / startEquity) * 100 else 0.0
            
            val dailyReturn = calculatePeriodReturn(snapshots, 1)
            val weeklyReturn = calculatePeriodReturn(snapshots, 7)
            val monthlyReturn = calculatePeriodReturn(snapshots, 30)
            val ytdReturn = calculateYTDReturn(snapshots, currentEquity)
            val annualizedReturn = calculateAnnualizedReturn(dailyReturns)
            
            // Unrealized P&L
            val openLots = taxLotDao.getOpenLots().first()
            val investedValue = openLots.sumOf { it.remainingCostBasis }
            val unrealizedPnl = currentEquity - cashBalance - investedValue
            
            val result = PortfolioMetrics(
                totalReturn = totalReturn,
                totalReturnPercent = totalReturnPercent,
                dailyReturn = dailyReturn,
                weeklyReturn = weeklyReturn,
                monthlyReturn = monthlyReturn,
                ytdReturn = ytdReturn,
                annualizedReturn = annualizedReturn,
                sharpeRatio = sharpeRatio,
                sortinoRatio = sortinoRatio,
                calmarRatio = calmarRatio,
                volatility = volatility * 100,  // Convert to percentage
                downsideDeviation = downsideDeviation * 100,
                beta = null,  // Would need benchmark data
                alpha = null,
                currentDrawdown = drawdownAnalysis.currentDrawdown,
                currentDrawdownPercent = drawdownAnalysis.currentDrawdownPercent,
                maxDrawdown = drawdownAnalysis.maxDrawdown,
                maxDrawdownPercent = drawdownAnalysis.maxDrawdownPercent,
                maxDrawdownDuration = drawdownAnalysis.maxDrawdownDuration,
                averageDrawdown = drawdownAnalysis.averageDrawdown,
                totalTrades = closedTrades.size,
                winningTrades = winningTrades.size,
                losingTrades = losingTrades.size,
                winRate = winRate,
                profitFactor = profitFactor,
                averageWin = avgWin,
                averageLoss = avgLoss,
                largestWin = largestWin,
                largestLoss = largestLoss,
                expectancy = expectancy,
                avgHoldingPeriod = avgHoldingPeriod,
                totalEquity = currentEquity,
                cashBalance = cashBalance,
                investedValue = investedValue,
                unrealizedPnl = unrealizedPnl,
                realizedPnl = totalRealizedPnl,
                totalFeesPaid = totalFees,
                calculatedAt = System.currentTimeMillis(),
                periodStart = periodStart,
                periodEnd = periodEnd
            )
            
            _metrics.value = result
            result
        }
    }
    
    // ========================================================================
    // ASSET ALLOCATION
    // ========================================================================
    
    /**
     * Get current asset allocation with drift analysis
     */
    suspend fun getAssetAllocation(
        currentPrices: Map<String, Double>,
        targetAllocations: Map<String, Double>? = null
    ): List<AssetAllocation> {
        return withContext(Dispatchers.Default) {
            val openLots = taxLotDao.getOpenLots().first()
            
            // Group by symbol
            val bySymbol = openLots.groupBy { it.symbol }
            
            val totalValue = bySymbol.entries.sumOf { (symbol, lots) ->
                val price = currentPrices[symbol] ?: lots.first().acquisitionPrice
                lots.sumOf { it.remainingQuantity * price }
            }
            
            bySymbol.map { (symbol, lots) ->
                val quantity = lots.sumOf { it.remainingQuantity }
                val costBasis = lots.sumOf { it.remainingCostBasis }
                val currentPrice = currentPrices[symbol] ?: lots.first().acquisitionPrice
                val currentValue = quantity * currentPrice
                val unrealizedPnl = currentValue - costBasis
                val portfolioPercent = if (totalValue > 0) (currentValue / totalValue) * 100 else 0.0
                val targetPercent = targetAllocations?.get(symbol)
                val drift = targetPercent?.let { portfolioPercent - it }
                
                AssetAllocation(
                    symbol = symbol,
                    baseAsset = lots.first().baseAsset,
                    quantity = quantity,
                    currentValue = currentValue,
                    costBasis = costBasis,
                    unrealizedPnl = unrealizedPnl,
                    unrealizedPnlPercent = if (costBasis > 0) (unrealizedPnl / costBasis) * 100 else 0.0,
                    portfolioPercent = portfolioPercent,
                    targetPercent = targetPercent,
                    driftPercent = drift
                )
            }.sortedByDescending { it.currentValue }
        }
    }
    
    // ========================================================================
    // PERFORMANCE ATTRIBUTION
    // ========================================================================
    
    /**
     * Attribute performance to individual assets
     */
    suspend fun getPerformanceAttribution(
        periodStart: Long,
        periodEnd: Long
    ): List<PerformanceAttribution> {
        return withContext(Dispatchers.Default) {
            val trades = enhancedTradeDao.getTradesInRange(periodStart, periodEnd).first()
            val closedTrades = trades.filter { it.realizedPnl != null }
            
            val totalPnl = closedTrades.sumOf { it.realizedPnl ?: 0.0 }
            
            closedTrades.groupBy { it.symbol }.map { (symbol, symbolTrades) ->
                val symbolPnl = symbolTrades.sumOf { it.realizedPnl ?: 0.0 }
                val wins = symbolTrades.count { (it.realizedPnl ?: 0.0) > 0 }
                
                PerformanceAttribution(
                    symbol = symbol,
                    contributionToReturn = symbolPnl,
                    contributionPercent = if (totalPnl != 0.0) (symbolPnl / abs(totalPnl)) * 100 else 0.0,
                    tradeCount = symbolTrades.size,
                    winRate = if (symbolTrades.isNotEmpty()) (wins.toDouble() / symbolTrades.size) * 100 else 0.0,
                    totalPnl = symbolPnl,
                    averagePnl = if (symbolTrades.isNotEmpty()) symbolPnl / symbolTrades.size else 0.0
                )
            }.sortedByDescending { it.contributionToReturn }
        }
    }
    
    // ========================================================================
    // EQUITY CURVE
    // ========================================================================
    
    /**
     * Get equity curve data
     */
    suspend fun getEquityCurve(
        periodStart: Long,
        periodEnd: Long
    ): List<EquityCurvePoint> {
        return withContext(Dispatchers.Default) {
            val snapshots = equitySnapshotDao.getSnapshotsInRange("DAILY", periodStart, periodEnd)
            
            var highWaterMark = snapshots.firstOrNull()?.totalEquity ?: 0.0
            
            val curve = snapshots.map { snapshot ->
                if (snapshot.totalEquity > highWaterMark) {
                    highWaterMark = snapshot.totalEquity
                }
                
                val drawdown = highWaterMark - snapshot.totalEquity
                val drawdownPercent = if (highWaterMark > 0) (drawdown / highWaterMark) * 100 else 0.0
                
                EquityCurvePoint(
                    timestamp = snapshot.timestamp,
                    equity = snapshot.totalEquity,
                    drawdown = drawdown,
                    drawdownPercent = drawdownPercent,
                    highWaterMark = highWaterMark
                )
            }
            
            _equityCurve.value = curve
            curve
        }
    }
    
    // ========================================================================
    // PERIOD PERFORMANCE
    // ========================================================================
    
    /**
     * Get performance broken down by period (daily, weekly, monthly)
     */
    suspend fun getPeriodPerformance(
        periodType: String,  // DAILY, WEEKLY, MONTHLY
        count: Int = 12
    ): List<PeriodPerformance> {
        return withContext(Dispatchers.Default) {
            val snapshots = equitySnapshotDao.getSnapshots(periodType, count + 1)
            
            if (snapshots.size < 2) return@withContext emptyList()
            
            snapshots.zipWithNext().map { (start, end) ->
                val pnl = end.totalEquity - start.totalEquity
                val pnlPercent = if (start.totalEquity > 0) (pnl / start.totalEquity) * 100 else 0.0
                
                PeriodPerformance(
                    periodLabel = formatPeriodLabel(end.timestamp, periodType),
                    startDate = start.timestamp,
                    endDate = end.timestamp,
                    startEquity = start.totalEquity,
                    endEquity = end.totalEquity,
                    pnl = pnl,
                    pnlPercent = pnlPercent,
                    trades = end.periodTrades,
                    wins = end.periodWins,
                    losses = end.periodLosses
                )
            }.reversed()
        }
    }
    
    // ========================================================================
    // CORRELATION ANALYSIS
    // ========================================================================
    
    /**
     * Calculate correlation between assets
     */
    suspend fun getCorrelationMatrix(
        symbols: List<String>,
        periodStart: Long,
        periodEnd: Long
    ): List<CorrelationPair> {
        return withContext(Dispatchers.Default) {
            val correlations = mutableListOf<CorrelationPair>()
            
            // Get daily returns per symbol
            val returnsBySymbol = mutableMapOf<String, List<Double>>()
            
            for (symbol in symbols) {
                val trades = enhancedTradeDao.getTradesForSymbol(symbol).first()
                    .filter { it.disposalDate != null && it.disposalDate in periodStart..periodEnd }
                
                if (trades.isNotEmpty()) {
                    returnsBySymbol[symbol] = trades.mapNotNull { it.realizedPnlPercent }
                }
            }
            
            // Calculate pairwise correlations
            for (i in symbols.indices) {
                for (j in i + 1 until symbols.size) {
                    val returns1 = returnsBySymbol[symbols[i]] ?: continue
                    val returns2 = returnsBySymbol[symbols[j]] ?: continue
                    
                    val minSize = minOf(returns1.size, returns2.size)
                    if (minSize < 5) continue
                    
                    val correlation = calculateCorrelation(
                        returns1.takeLast(minSize),
                        returns2.takeLast(minSize)
                    )
                    
                    correlations.add(CorrelationPair(
                        symbol1 = symbols[i],
                        symbol2 = symbols[j],
                        correlation = correlation,
                        sampleSize = minSize
                    ))
                }
            }
            
            correlations.sortedByDescending { abs(it.correlation) }
        }
    }
    
    // ========================================================================
    // SNAPSHOT RECORDING
    // ========================================================================
    
    /**
     * Record an equity snapshot
     */
    suspend fun recordSnapshot(
        type: String,
        totalEquity: Double,
        cashBalance: Double,
        positionsValue: Double,
        unrealizedPnl: Double,
        allocationJson: String? = null
    ) {
        val previousSnapshot = when (type) {
            "DAILY" -> equitySnapshotDao.getLatestDailySnapshot()
            else -> equitySnapshotDao.getSnapshots(type, 1).firstOrNull()
        }
        
        val periodPnl = totalEquity - (previousSnapshot?.totalEquity ?: totalEquity)
        val periodPnlPercent = if (previousSnapshot != null && previousSnapshot.totalEquity > 0) {
            (periodPnl / previousSnapshot.totalEquity) * 100
        } else 0.0
        
        val highWaterMark = maxOf(previousSnapshot?.highWaterMark ?: totalEquity, totalEquity)
        val drawdown = highWaterMark - totalEquity
        val drawdownPercent = if (highWaterMark > 0) (drawdown / highWaterMark) * 100 else 0.0
        
        val cumulativePnl = (previousSnapshot?.cumulativePnl ?: 0.0) + periodPnl
        val startEquity = previousSnapshot?.totalEquity ?: totalEquity
        val cumulativePnlPercent = if (startEquity > 0) (cumulativePnl / startEquity) * 100 else 0.0
        
        val snapshot = EquitySnapshotEntity(
            snapshotType = type,
            totalEquity = totalEquity,
            cashBalance = cashBalance,
            positionsValue = positionsValue,
            unrealizedPnl = unrealizedPnl,
            periodPnl = periodPnl,
            periodPnlPercent = periodPnlPercent,
            periodTrades = 0,  // Would need to query
            periodWins = 0,
            periodLosses = 0,
            cumulativePnl = cumulativePnl,
            cumulativePnlPercent = cumulativePnlPercent,
            highWaterMark = highWaterMark,
            drawdown = drawdown,
            drawdownPercent = drawdownPercent,
            sharpeRatio = _metrics.value?.sharpeRatio,
            sortinoRatio = _metrics.value?.sortinoRatio,
            winRate = _metrics.value?.winRate,
            profitFactor = _metrics.value?.profitFactor,
            maxDrawdown = _metrics.value?.maxDrawdownPercent,
            allocationJson = allocationJson,
            timestamp = System.currentTimeMillis()
        )
        
        equitySnapshotDao.insertSnapshot(snapshot)
    }
    
    // ========================================================================
    // HELPER METHODS
    // ========================================================================
    
    private fun calculateDailyReturns(snapshots: List<EquitySnapshotEntity>): List<Double> {
        if (snapshots.size < 2) return emptyList()
        
        return snapshots.zipWithNext().map { (prev, curr) ->
            if (prev.totalEquity > 0) {
                (curr.totalEquity - prev.totalEquity) / prev.totalEquity
            } else 0.0
        }
    }
    
    private fun calculateVolatility(returns: List<Double>): Double {
        if (returns.size < 2) return 0.0
        
        val mean = returns.average()
        val variance = returns.map { (it - mean).pow(2) }.average()
        return sqrt(variance) * sqrt(TRADING_DAYS_PER_YEAR.toDouble())  // Annualized
    }
    
    private fun calculateDownsideDeviation(returns: List<Double>, threshold: Double = 0.0): Double {
        if (returns.isEmpty()) return 0.0
        
        val downsideReturns = returns.filter { it < threshold }
        if (downsideReturns.isEmpty()) return 0.0
        
        val variance = downsideReturns.map { (it - threshold).pow(2) }.average()
        return sqrt(variance) * sqrt(TRADING_DAYS_PER_YEAR.toDouble())
    }
    
    private fun calculateSharpeRatio(returns: List<Double>, volatility: Double): Double {
        if (returns.isEmpty() || volatility == 0.0) return 0.0
        
        val annualizedReturn = returns.average() * TRADING_DAYS_PER_YEAR
        val dailyRiskFree = RISK_FREE_RATE / TRADING_DAYS_PER_YEAR
        val excessReturn = annualizedReturn - RISK_FREE_RATE
        
        return excessReturn / volatility
    }
    
    private fun calculateSortinoRatio(returns: List<Double>, downsideDeviation: Double): Double {
        if (returns.isEmpty() || downsideDeviation == 0.0) return 0.0
        
        val annualizedReturn = returns.average() * TRADING_DAYS_PER_YEAR
        val excessReturn = annualizedReturn - RISK_FREE_RATE
        
        return excessReturn / downsideDeviation
    }
    
    private data class DrawdownAnalysis(
        val currentDrawdown: Double,
        val currentDrawdownPercent: Double,
        val maxDrawdown: Double,
        val maxDrawdownPercent: Double,
        val maxDrawdownDuration: Int,
        val averageDrawdown: Double
    )
    
    private fun calculateDrawdownAnalysis(
        snapshots: List<EquitySnapshotEntity>,
        currentEquity: Double
    ): DrawdownAnalysis {
        if (snapshots.isEmpty()) {
            return DrawdownAnalysis(0.0, 0.0, 0.0, 0.0, 0, 0.0)
        }
        
        var highWaterMark = snapshots.first().totalEquity
        var maxDrawdown = 0.0
        var maxDrawdownPercent = 0.0
        var maxDrawdownDuration = 0
        var currentDrawdownDuration = 0
        val drawdowns = mutableListOf<Double>()
        
        for (snapshot in snapshots) {
            if (snapshot.totalEquity > highWaterMark) {
                highWaterMark = snapshot.totalEquity
                currentDrawdownDuration = 0
            } else {
                currentDrawdownDuration++
            }
            
            val drawdown = highWaterMark - snapshot.totalEquity
            val drawdownPercent = if (highWaterMark > 0) (drawdown / highWaterMark) * 100 else 0.0
            
            if (drawdown > maxDrawdown) {
                maxDrawdown = drawdown
                maxDrawdownPercent = drawdownPercent
            }
            
            if (currentDrawdownDuration > maxDrawdownDuration) {
                maxDrawdownDuration = currentDrawdownDuration
            }
            
            if (drawdownPercent > 0) {
                drawdowns.add(drawdownPercent)
            }
        }
        
        // Current drawdown
        if (currentEquity > highWaterMark) {
            highWaterMark = currentEquity
        }
        val currentDrawdown = highWaterMark - currentEquity
        val currentDrawdownPercent = if (highWaterMark > 0) (currentDrawdown / highWaterMark) * 100 else 0.0
        
        val averageDrawdown = if (drawdowns.isNotEmpty()) drawdowns.average() else 0.0
        
        return DrawdownAnalysis(
            currentDrawdown = currentDrawdown,
            currentDrawdownPercent = currentDrawdownPercent,
            maxDrawdown = maxDrawdown,
            maxDrawdownPercent = maxDrawdownPercent,
            maxDrawdownDuration = maxDrawdownDuration,
            averageDrawdown = averageDrawdown
        )
    }
    
    private fun calculateAverageHoldingPeriod(trades: List<EnhancedTradeEntity>): Double {
        val tradesWithHolding = trades.filter { it.holdingPeriodDays != null && it.holdingPeriodDays > 0 }
        if (tradesWithHolding.isEmpty()) return 0.0
        
        return tradesWithHolding.map { it.holdingPeriodDays!!.toDouble() * 24 }.average()
    }
    
    private fun calculatePeriodReturn(snapshots: List<EquitySnapshotEntity>, days: Int): Double {
        if (snapshots.size < 2) return 0.0
        
        val cutoff = System.currentTimeMillis() - (days * MILLIS_PER_DAY)
        val recent = snapshots.filter { it.timestamp >= cutoff }
        
        if (recent.size < 2) return 0.0
        
        val start = recent.first().totalEquity
        val end = recent.last().totalEquity
        
        return if (start > 0) ((end - start) / start) * 100 else 0.0
    }
    
    private fun calculateYTDReturn(snapshots: List<EquitySnapshotEntity>, currentEquity: Double): Double {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_YEAR, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val yearStart = calendar.timeInMillis
        
        val startOfYear = snapshots.filter { it.timestamp >= yearStart }.minByOrNull { it.timestamp }
        
        val startEquity = startOfYear?.totalEquity ?: currentEquity
        return if (startEquity > 0) ((currentEquity - startEquity) / startEquity) * 100 else 0.0
    }
    
    private fun calculateAnnualizedReturn(dailyReturns: List<Double>): Double {
        if (dailyReturns.isEmpty()) return 0.0
        
        val avgDailyReturn = dailyReturns.average()
        return ((1 + avgDailyReturn).pow(TRADING_DAYS_PER_YEAR) - 1) * 100
    }
    
    private fun calculateCorrelation(series1: List<Double>, series2: List<Double>): Double {
        if (series1.size != series2.size || series1.size < 2) return 0.0
        
        val mean1 = series1.average()
        val mean2 = series2.average()
        
        var covariance = 0.0
        var variance1 = 0.0
        var variance2 = 0.0
        
        for (i in series1.indices) {
            val diff1 = series1[i] - mean1
            val diff2 = series2[i] - mean2
            covariance += diff1 * diff2
            variance1 += diff1 * diff1
            variance2 += diff2 * diff2
        }
        
        val denominator = sqrt(variance1 * variance2)
        return if (denominator > 0) covariance / denominator else 0.0
    }
    
    private fun formatPeriodLabel(timestamp: Long, periodType: String): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        
        return when (periodType) {
            "DAILY" -> String.format(
                "%d/%d",
                calendar.get(Calendar.DAY_OF_MONTH),
                calendar.get(Calendar.MONTH) + 1
            )
            "WEEKLY" -> String.format(
                "W%d",
                calendar.get(Calendar.WEEK_OF_YEAR)
            )
            "MONTHLY" -> String.format(
                "%d/%d",
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.YEAR)
            )
            else -> timestamp.toString()
        }
    }
}
