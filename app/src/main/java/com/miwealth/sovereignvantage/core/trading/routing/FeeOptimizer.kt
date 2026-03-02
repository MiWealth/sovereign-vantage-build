package com.miwealth.sovereignvantage.core.trading.routing

import com.miwealth.sovereignvantage.core.*
import com.miwealth.sovereignvantage.core.exchange.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

/**
 * FEE OPTIMIZER
 * 
 * Optimizes trading costs across exchanges by:
 * - Tracking fee schedules for all connected exchanges
 * - Calculating effective costs including spreads
 * - Managing volume-based tier progression
 * - Recommending optimal execution venues
 * - Tracking fee rebates and rewards
 * 
 * Fee Components Tracked:
 * - Trading fees (maker/taker)
 * - Network fees (gas for DEX)
 * - Withdrawal fees
 * - Spread costs
 * - Volume rebates
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

/**
 * Fee analysis result for a potential trade
 */
data class FeeAnalysis(
    val exchangeId: String,
    val symbol: String,
    val side: TradeSide,
    val quantity: Double,
    val price: Double,
    val notionalValue: Double,
    val tradingFee: TradingFeeBreakdown,
    val spreadCost: SpreadCost,
    val totalCost: TotalCost,
    val timestamp: Long = System.currentTimeMillis()
)

data class TradingFeeBreakdown(
    val makerFeePercent: Double,
    val takerFeePercent: Double,
    val appliedFeePercent: Double,   // Based on expected order type
    val feeAmount: Double,
    val feeCurrency: String,
    val rebateAmount: Double = 0.0,   // Volume rebates
    val netFeeAmount: Double
)

data class SpreadCost(
    val bidAskSpread: Double,
    val spreadPercent: Double,
    val impliedCost: Double,         // Half spread as cost to cross
    val marketImpact: Double = 0.0   // Estimated price impact
)

data class TotalCost(
    val tradingFeeUsd: Double,
    val spreadCostUsd: Double,
    val networkFeeUsd: Double,
    val totalCostUsd: Double,
    val totalCostPercent: Double,
    val costPerUnit: Double
)

/**
 * Volume tracking for tier progression
 */
data class VolumeTracker(
    val exchangeId: String,
    val volume30d: Double,
    val currentTier: FeeTier?,
    val nextTier: FeeTier?,
    val volumeToNextTier: Double,
    val estimatedSavingsNextTier: Double
)

/**
 * Fee comparison across exchanges
 */
data class FeeComparison(
    val symbol: String,
    val side: TradeSide,
    val quantity: Double,
    val analyses: List<FeeAnalysis>,
    val bestExchange: String?,
    val worstExchange: String?,
    val potentialSavings: Double,    // Best vs worst
    val recommendation: String
)

class FeeOptimizer(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    
    companion object {
        private const val TAG = "FeeOptimizer"
        
        // Default fee schedules (updated periodically from exchange APIs)
        private val DEFAULT_FEE_SCHEDULES = mapOf(
            "kraken" to ExchangeFeeSchedule(
                exchangeId = "kraken",
                makerFee = 0.0016,  // 0.16%
                takerFee = 0.0026,  // 0.26%
                volumeTiers = listOf(
                    FeeTier(0.0, 0.0016, 0.0026),
                    FeeTier(50000.0, 0.0014, 0.0024),
                    FeeTier(100000.0, 0.0012, 0.0022),
                    FeeTier(250000.0, 0.0010, 0.0020),
                    FeeTier(500000.0, 0.0008, 0.0018),
                    FeeTier(1000000.0, 0.0006, 0.0016),
                    FeeTier(2500000.0, 0.0004, 0.0014),
                    FeeTier(5000000.0, 0.0002, 0.0012),
                    FeeTier(10000000.0, 0.0000, 0.0010)
                )
            ),
            "binance" to ExchangeFeeSchedule(
                exchangeId = "binance",
                makerFee = 0.0010,  // 0.10%
                takerFee = 0.0010,  // 0.10%
                volumeTiers = listOf(
                    FeeTier(0.0, 0.0010, 0.0010),
                    FeeTier(1000000.0, 0.0009, 0.0010),
                    FeeTier(5000000.0, 0.0008, 0.0009),
                    FeeTier(10000000.0, 0.0007, 0.0008),
                    FeeTier(25000000.0, 0.0006, 0.0007),
                    FeeTier(100000000.0, 0.0005, 0.0006),
                    FeeTier(250000000.0, 0.0004, 0.0005),
                    FeeTier(500000000.0, 0.0003, 0.0004)
                )
            ),
            "coinbase" to ExchangeFeeSchedule(
                exchangeId = "coinbase",
                makerFee = 0.0040,  // 0.40%
                takerFee = 0.0060,  // 0.60%
                volumeTiers = listOf(
                    FeeTier(0.0, 0.0040, 0.0060),
                    FeeTier(10000.0, 0.0025, 0.0035),
                    FeeTier(50000.0, 0.0015, 0.0025),
                    FeeTier(100000.0, 0.0010, 0.0020),
                    FeeTier(1000000.0, 0.0008, 0.0018),
                    FeeTier(15000000.0, 0.0005, 0.0015),
                    FeeTier(75000000.0, 0.0000, 0.0010),
                    FeeTier(250000000.0, 0.0000, 0.0008)
                )
            ),
            "bybit" to ExchangeFeeSchedule(
                exchangeId = "bybit",
                makerFee = 0.0010,
                takerFee = 0.0010,
                volumeTiers = listOf(
                    FeeTier(0.0, 0.0010, 0.0010),
                    FeeTier(1000000.0, 0.0006, 0.0010),
                    FeeTier(2500000.0, 0.0004, 0.0006),
                    FeeTier(5000000.0, 0.0002, 0.0005),
                    FeeTier(10000000.0, 0.0000, 0.0004)
                )
            ),
            "okx" to ExchangeFeeSchedule(
                exchangeId = "okx",
                makerFee = 0.0008,
                takerFee = 0.0010,
                volumeTiers = listOf(
                    FeeTier(0.0, 0.0008, 0.0010),
                    FeeTier(5000000.0, 0.0006, 0.0009),
                    FeeTier(10000000.0, 0.0005, 0.0008),
                    FeeTier(20000000.0, 0.0003, 0.0007),
                    FeeTier(50000000.0, 0.0002, 0.0006)
                )
            ),
            "kucoin" to ExchangeFeeSchedule(
                exchangeId = "kucoin",
                makerFee = 0.0010,
                takerFee = 0.0010,
                volumeTiers = listOf(
                    FeeTier(0.0, 0.0010, 0.0010),
                    FeeTier(50.0, 0.0010, 0.0010),  // Based on KCS holdings
                    FeeTier(200.0, 0.0008, 0.0009),
                    FeeTier(500.0, 0.0006, 0.0008),
                    FeeTier(1000.0, 0.0004, 0.0007)
                )
            )
        )
        
        // Network fees for DEX (gas estimates in USD)
        private val DEX_NETWORK_FEES = mapOf(
            "ethereum" to 25.0,    // Ethereum mainnet
            "arbitrum" to 0.50,    // Arbitrum
            "optimism" to 0.30,    // Optimism
            "polygon" to 0.05,     // Polygon
            "bsc" to 0.20,         // BNB Chain
            "solana" to 0.01       // Solana
        )
    }
    
    // Fee schedules cache
    private val feeSchedules = ConcurrentHashMap<String, ExchangeFeeSchedule>()
    
    // Volume tracking per exchange
    private val volumeTrackers = ConcurrentHashMap<String, VolumeTracker>()
    
    // Price cache for spread calculations
    private val priceCache = ConcurrentHashMap<String, PriceTick>()
    
    // Stats
    private val _stats = MutableStateFlow(FeeOptimizerStats())
    val stats: StateFlow<FeeOptimizerStats> = _stats.asStateFlow()
    
    init {
        // Initialize with default fee schedules
        DEFAULT_FEE_SCHEDULES.forEach { (id, schedule) ->
            feeSchedules[id] = schedule
        }
    }
    
    // =========================================================================
    // FEE SCHEDULE MANAGEMENT
    // =========================================================================
    
    /**
     * Get fee schedule for an exchange
     */
    fun getFeeSchedule(exchangeId: String): ExchangeFeeSchedule {
        return feeSchedules[exchangeId.lowercase()] 
            ?: ExchangeFeeSchedule.default(exchangeId)
    }
    
    /**
     * Update fee schedule for an exchange
     */
    fun updateFeeSchedule(schedule: ExchangeFeeSchedule) {
        feeSchedules[schedule.exchangeId.lowercase()] = schedule
    }
    
    /**
     * Get applicable fee tier based on 30-day volume
     */
    fun getApplicableTier(exchangeId: String, volume30d: Double): FeeTier? {
        val schedule = getFeeSchedule(exchangeId)
        return schedule.volumeTiers
            .filter { it.minVolume30d <= volume30d }
            .maxByOrNull { it.minVolume30d }
    }
    
    /**
     * Update volume tracker for tier management
     */
    fun updateVolume(exchangeId: String, volume30d: Double) {
        val schedule = getFeeSchedule(exchangeId)
        val currentTier = getApplicableTier(exchangeId, volume30d)
        val nextTier = schedule.volumeTiers
            .filter { it.minVolume30d > volume30d }
            .minByOrNull { it.minVolume30d }
        
        val volumeToNext = nextTier?.let { it.minVolume30d - volume30d } ?: 0.0
        val savings = if (nextTier != null && currentTier != null) {
            // Estimate savings on $100k of volume
            val currentFee = 100000.0 * currentTier.takerFee
            val nextFee = 100000.0 * nextTier.takerFee
            currentFee - nextFee
        } else 0.0
        
        volumeTrackers[exchangeId.lowercase()] = VolumeTracker(
            exchangeId = exchangeId,
            volume30d = volume30d,
            currentTier = currentTier,
            nextTier = nextTier,
            volumeToNextTier = volumeToNext,
            estimatedSavingsNextTier = savings
        )
    }
    
    // =========================================================================
    // FEE ANALYSIS
    // =========================================================================
    
    /**
     * Analyse fees for a potential trade on a specific exchange
     */
    fun analyzeFees(
        exchangeId: String,
        symbol: String,
        side: TradeSide,
        quantity: Double,
        price: Double,
        orderType: OrderType = OrderType.MARKET,
        volume30d: Double = 0.0
    ): FeeAnalysis {
        val schedule = getFeeSchedule(exchangeId)
        val tier = getApplicableTier(exchangeId, volume30d)
        
        val notional = quantity * price
        
        // Determine applied fee rate
        val (makerRate, takerRate) = if (tier != null) {
            tier.makerFee to tier.takerFee
        } else {
            schedule.makerFee to schedule.takerFee
        }
        
        val appliedRate = when (orderType) {
            OrderType.LIMIT -> makerRate
            else -> takerRate
        }
        
        val feeAmount = notional * appliedRate
        
        // Calculate rebates (some exchanges offer negative maker fees)
        val rebate = if (makerRate < 0 && orderType == OrderType.LIMIT) {
            notional * kotlin.math.abs(makerRate)
        } else 0.0
        
        val tradingFee = TradingFeeBreakdown(
            makerFeePercent = makerRate * 100,
            takerFeePercent = takerRate * 100,
            appliedFeePercent = appliedRate * 100,
            feeAmount = feeAmount,
            feeCurrency = "USD",
            rebateAmount = rebate,
            netFeeAmount = feeAmount - rebate
        )
        
        // Calculate spread cost
        val priceTick = priceCache[symbol]
        val spreadCost = if (priceTick != null) {
            val spread = priceTick.spread
            val spreadPct = priceTick.spreadPercent
            val impliedCost = (spread / 2) * quantity  // Cost to cross spread
            
            SpreadCost(
                bidAskSpread = spread,
                spreadPercent = spreadPct,
                impliedCost = impliedCost,
                marketImpact = estimateMarketImpact(quantity, notional)
            )
        } else {
            SpreadCost(0.0, 0.0, 0.0, 0.0)
        }
        
        // Network fee (for DEX)
        val networkFee = if (exchangeId.contains("uniswap") || 
                            exchangeId.contains("sushi") ||
                            exchangeId.contains("pancake")) {
            DEX_NETWORK_FEES["ethereum"] ?: 0.0
        } else 0.0
        
        val totalCostUsd = tradingFee.netFeeAmount + spreadCost.impliedCost + networkFee
        
        val totalCost = TotalCost(
            tradingFeeUsd = tradingFee.netFeeAmount,
            spreadCostUsd = spreadCost.impliedCost,
            networkFeeUsd = networkFee,
            totalCostUsd = totalCostUsd,
            totalCostPercent = if (notional > 0) (totalCostUsd / notional) * 100 else 0.0,
            costPerUnit = if (quantity > 0) totalCostUsd / quantity else 0.0
        )
        
        return FeeAnalysis(
            exchangeId = exchangeId,
            symbol = symbol,
            side = side,
            quantity = quantity,
            price = price,
            notionalValue = notional,
            tradingFee = tradingFee,
            spreadCost = spreadCost,
            totalCost = totalCost
        )
    }
    
    /**
     * Compare fees across multiple exchanges
     */
    fun compareFees(
        exchangeIds: List<String>,
        symbol: String,
        side: TradeSide,
        quantity: Double,
        prices: Map<String, Double>,
        orderType: OrderType = OrderType.MARKET
    ): FeeComparison {
        val analyses = exchangeIds.mapNotNull { exchangeId ->
            val price = prices[exchangeId] ?: return@mapNotNull null
            val volume30d = volumeTrackers[exchangeId.lowercase()]?.volume30d ?: 0.0
            analyzeFees(exchangeId, symbol, side, quantity, price, orderType, volume30d)
        }
        
        if (analyses.isEmpty()) {
            return FeeComparison(
                symbol = symbol,
                side = side,
                quantity = quantity,
                analyses = emptyList(),
                bestExchange = null,
                worstExchange = null,
                potentialSavings = 0.0,
                recommendation = "No exchange data available"
            )
        }
        
        // Find best and worst
        val sorted = analyses.sortedBy { it.totalCost.totalCostUsd }
        val best = sorted.first()
        val worst = sorted.last()
        
        val savings = worst.totalCost.totalCostUsd - best.totalCost.totalCostUsd
        
        // Generate recommendation
        val recommendation = buildString {
            append("Best: ${best.exchangeId} (${String.format("%.4f", best.totalCost.totalCostPercent)}% total cost)")
            if (savings > 1.0) {
                append(". Save \$${String.format("%.2f", savings)} vs ${worst.exchangeId}")
            }
            if (best.tradingFee.rebateAmount > 0) {
                append(". Includes \$${String.format("%.2f", best.tradingFee.rebateAmount)} rebate")
            }
        }
        
        return FeeComparison(
            symbol = symbol,
            side = side,
            quantity = quantity,
            analyses = analyses,
            bestExchange = best.exchangeId,
            worstExchange = worst.exchangeId,
            potentialSavings = savings,
            recommendation = recommendation
        )
    }
    
    /**
     * Get optimal exchange for a trade based on fees
     */
    fun getOptimalExchange(
        exchangeIds: List<String>,
        symbol: String,
        side: TradeSide,
        quantity: Double,
        prices: Map<String, Double>,
        orderType: OrderType = OrderType.MARKET
    ): String? {
        val comparison = compareFees(exchangeIds, symbol, side, quantity, prices, orderType)
        return comparison.bestExchange
    }
    
    // =========================================================================
    // COST CALCULATIONS
    // =========================================================================
    
    /**
     * Calculate total execution cost for an order
     */
    fun calculateExecutionCost(
        exchangeId: String,
        quantity: Double,
        entryPrice: Double,
        exitPrice: Double,
        entryType: OrderType = OrderType.MARKET,
        exitType: OrderType = OrderType.LIMIT
    ): ExecutionCostSummary {
        val schedule = getFeeSchedule(exchangeId)
        val volume30d = volumeTrackers[exchangeId.lowercase()]?.volume30d ?: 0.0
        val tier = getApplicableTier(exchangeId, volume30d)
        
        val entryNotional = quantity * entryPrice
        val exitNotional = quantity * exitPrice
        
        // Entry fee
        val entryFeeRate = when (entryType) {
            OrderType.LIMIT -> tier?.makerFee ?: schedule.makerFee
            else -> tier?.takerFee ?: schedule.takerFee
        }
        val entryFee = entryNotional * entryFeeRate
        
        // Exit fee
        val exitFeeRate = when (exitType) {
            OrderType.LIMIT -> tier?.makerFee ?: schedule.makerFee
            else -> tier?.takerFee ?: schedule.takerFee
        }
        val exitFee = exitNotional * exitFeeRate
        
        // Gross P&L
        val grossPnl = when {
            exitPrice > entryPrice -> (exitPrice - entryPrice) * quantity  // Long profit
            else -> (entryPrice - exitPrice) * quantity  // Short profit or loss
        }
        
        val totalFees = entryFee + exitFee
        val netPnl = grossPnl - totalFees
        val feesAsPercentOfPnl = if (grossPnl != 0.0) (totalFees / kotlin.math.abs(grossPnl)) * 100 else 0.0
        
        return ExecutionCostSummary(
            exchangeId = exchangeId,
            entryFee = entryFee,
            exitFee = exitFee,
            totalFees = totalFees,
            grossPnl = grossPnl,
            netPnl = netPnl,
            feesAsPercentOfPnl = feesAsPercentOfPnl,
            breakEvenMove = calculateBreakEvenMove(entryPrice, entryFeeRate + exitFeeRate)
        )
    }
    
    /**
     * Calculate minimum price move needed to break even after fees
     */
    fun calculateBreakEvenMove(entryPrice: Double, totalFeeRate: Double): Double {
        // For a round trip, you need price to move > 2x the fee rate to profit
        return entryPrice * totalFeeRate * 2
    }
    
    /**
     * Estimate market impact for large orders
     */
    private fun estimateMarketImpact(quantity: Double, notionalValue: Double): Double {
        // Simplified market impact model
        // Impact increases with order size (square root model)
        return when {
            notionalValue < 10000 -> 0.0
            notionalValue < 50000 -> notionalValue * 0.0001
            notionalValue < 100000 -> notionalValue * 0.0002
            notionalValue < 500000 -> notionalValue * 0.0005
            else -> notionalValue * 0.001
        }
    }
    
    // =========================================================================
    // PRICE CACHE
    // =========================================================================
    
    /**
     * Update price cache for spread calculations
     */
    fun updatePrice(symbol: String, tick: PriceTick) {
        priceCache[symbol] = tick
    }
    
    /**
     * Clear price cache
     */
    fun clearPriceCache() {
        priceCache.clear()
    }
    
    // =========================================================================
    // TIER RECOMMENDATIONS
    // =========================================================================
    
    /**
     * Get recommendations for reaching next fee tier
     */
    fun getTierRecommendations(exchangeId: String): TierRecommendation? {
        val tracker = volumeTrackers[exchangeId.lowercase()] ?: return null
        
        if (tracker.nextTier == null) {
            return TierRecommendation(
                exchangeId = exchangeId,
                currentTier = tracker.currentTier,
                nextTier = null,
                volumeNeeded = 0.0,
                daysAtCurrentRate = 0,
                estimatedSavings = 0.0,
                recommendation = "Already at highest tier"
            )
        }
        
        // Estimate days to reach next tier at current trading rate
        val dailyVolume = tracker.volume30d / 30
        val daysToNextTier = if (dailyVolume > 0) {
            (tracker.volumeToNextTier / dailyVolume).toInt()
        } else Int.MAX_VALUE
        
        val recommendation = when {
            daysToNextTier <= 7 -> "Close to next tier! ${daysToNextTier} days of normal trading"
            daysToNextTier <= 30 -> "On track for next tier this month"
            tracker.estimatedSavingsNextTier > 100 -> "Consider increasing volume - \$${tracker.estimatedSavingsNextTier}/100k savings at next tier"
            else -> "Current tier is appropriate for volume"
        }
        
        return TierRecommendation(
            exchangeId = exchangeId,
            currentTier = tracker.currentTier,
            nextTier = tracker.nextTier,
            volumeNeeded = tracker.volumeToNextTier,
            daysAtCurrentRate = daysToNextTier,
            estimatedSavings = tracker.estimatedSavingsNextTier,
            recommendation = recommendation
        )
    }
    
    /**
     * Shutdown
     */
    fun shutdown() {
        scope.cancel()
    }
}

// =============================================================================
// SUPPORTING TYPES
// =============================================================================

data class ExecutionCostSummary(
    val exchangeId: String,
    val entryFee: Double,
    val exitFee: Double,
    val totalFees: Double,
    val grossPnl: Double,
    val netPnl: Double,
    val feesAsPercentOfPnl: Double,
    val breakEvenMove: Double
)

data class TierRecommendation(
    val exchangeId: String,
    val currentTier: FeeTier?,
    val nextTier: FeeTier?,
    val volumeNeeded: Double,
    val daysAtCurrentRate: Int,
    val estimatedSavings: Double,
    val recommendation: String
)

data class FeeOptimizerStats(
    val totalFeesAnalyzed: Long = 0,
    val totalSavingsIdentified: Double = 0.0,
    val avgSavingsPerTrade: Double = 0.0
)
