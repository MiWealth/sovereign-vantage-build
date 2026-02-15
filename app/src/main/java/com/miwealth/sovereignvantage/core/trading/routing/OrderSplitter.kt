package com.miwealth.sovereignvantage.core.trading.routing

import com.miwealth.sovereignvantage.core.*
import kotlinx.coroutines.*
import kotlin.math.ceil
import kotlin.math.min

/**
 * ORDER SPLITTER
 * 
 * Intelligently splits large orders across multiple exchanges to:
 * - Minimise market impact
 * - Access deeper liquidity
 * - Optimise for best average execution price
 * - Balance between exchanges based on fee/liquidity
 * 
 * Splitting Strategies:
 * - PRO_RATA: Split proportionally to available liquidity
 * - WATERFALL: Fill best exchange first, then overflow to next
 * - EQUAL: Split equally across all exchanges
 * - LIQUIDITY_WEIGHTED: Weight by order book depth
 * - FEE_OPTIMIZED: Favour lower-fee exchanges
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

enum class SplitStrategy {
    PRO_RATA,           // Proportional to liquidity
    WATERFALL,          // Best exchange first
    EQUAL,              // Equal splits
    LIQUIDITY_WEIGHTED, // Weight by depth
    FEE_OPTIMIZED       // Favour low fees
}

/**
 * Result of order split calculation
 */
data class OrderSplit(
    val exchangeId: String,
    val quantity: Double,
    val percentOfTotal: Double,
    val limitPrice: Double?,
    val expectedPrice: Double,
    val expectedFeeUsd: Double,
    val delayMs: Long = 0,
    val reason: String
)

/**
 * Summary of split plan
 */
data class SplitPlan(
    val totalQuantity: Double,
    val splits: List<OrderSplit>,
    val strategy: SplitStrategy,
    val expectedAvgPrice: Double,
    val expectedTotalFees: Double,
    val estimatedSavingsVsSingleExchange: Double
)

class OrderSplitter(
    private val defaultStrategy: SplitStrategy = SplitStrategy.PRO_RATA,
    private val minSplitPercent: Double = 5.0,  // Minimum 5% per exchange
    private val maxSplits: Int = 5              // Max exchanges to split across
) {
    
    companion object {
        const val MIN_SPLIT_VALUE_USD = 100.0   // Minimum value per split
        const val WATERFALL_DELAY_MS = 500L     // Delay between waterfall executions
    }
    
    /**
     * Calculate optimal splits for an order
     */
    fun calculateSplits(
        totalQuantity: Double,
        routes: List<ExchangeRoute>,
        side: TradeSide,
        maxSlippagePercent: Double,
        strategy: SplitStrategy = defaultStrategy
    ): List<OrderSplit> {
        if (routes.isEmpty()) return emptyList()
        
        // Filter to connected exchanges with liquidity
        val validRoutes = routes.filter { it.availableLiquidity > 0 }
        
        if (validRoutes.isEmpty()) return emptyList()
        
        // If only one exchange, no split needed
        if (validRoutes.size == 1) {
            return listOf(createSingleSplit(validRoutes.first(), totalQuantity))
        }
        
        return when (strategy) {
            SplitStrategy.PRO_RATA -> calculateProRataSplits(totalQuantity, validRoutes, side)
            SplitStrategy.WATERFALL -> calculateWaterfallSplits(totalQuantity, validRoutes, side)
            SplitStrategy.EQUAL -> calculateEqualSplits(totalQuantity, validRoutes, side)
            SplitStrategy.LIQUIDITY_WEIGHTED -> calculateLiquidityWeightedSplits(totalQuantity, validRoutes, side)
            SplitStrategy.FEE_OPTIMIZED -> calculateFeeOptimizedSplits(totalQuantity, validRoutes, side)
        }
    }
    
    /**
     * Create a split plan with analysis
     */
    fun createSplitPlan(
        totalQuantity: Double,
        routes: List<ExchangeRoute>,
        side: TradeSide,
        maxSlippagePercent: Double,
        strategy: SplitStrategy = defaultStrategy
    ): SplitPlan {
        val splits = calculateSplits(totalQuantity, routes, side, maxSlippagePercent, strategy)
        
        if (splits.isEmpty()) {
            return SplitPlan(
                totalQuantity = totalQuantity,
                splits = emptyList(),
                strategy = strategy,
                expectedAvgPrice = 0.0,
                expectedTotalFees = 0.0,
                estimatedSavingsVsSingleExchange = 0.0
            )
        }
        
        val expectedAvgPrice = splits.sumOf { it.expectedPrice * it.quantity } / totalQuantity
        val expectedTotalFees = splits.sumOf { it.expectedFeeUsd }
        
        // Calculate savings vs single best exchange
        val bestSingleRoute = routes.maxByOrNull { it.score }
        val singleExchangeCost = bestSingleRoute?.let {
            (it.price * totalQuantity) + it.fees.totalFeeUsd
        } ?: 0.0
        
        val splitCost = (expectedAvgPrice * totalQuantity) + expectedTotalFees
        val savings = singleExchangeCost - splitCost
        
        return SplitPlan(
            totalQuantity = totalQuantity,
            splits = splits,
            strategy = strategy,
            expectedAvgPrice = expectedAvgPrice,
            expectedTotalFees = expectedTotalFees,
            estimatedSavingsVsSingleExchange = savings
        )
    }
    
    /**
     * Pro-rata split: proportional to available liquidity
     */
    private fun calculateProRataSplits(
        totalQuantity: Double,
        routes: List<ExchangeRoute>,
        side: TradeSide
    ): List<OrderSplit> {
        val totalLiquidity = routes.sumOf { it.availableLiquidity }
        if (totalLiquidity <= 0) return emptyList()
        
        val splits = mutableListOf<OrderSplit>()
        var remainingQty = totalQuantity
        
        // Sort by score (best first)
        val sortedRoutes = routes.sortedByDescending { it.score }
        
        for (route in sortedRoutes.take(maxSplits)) {
            val proportion = route.availableLiquidity / totalLiquidity
            val qty = min(totalQuantity * proportion, remainingQty)
            
            // Skip if below minimum threshold
            val percent = (qty / totalQuantity) * 100
            if (percent < minSplitPercent) continue
            
            val expectedFee = qty * route.price * route.fees.estimatedFeePercent / 100
            
            splits.add(OrderSplit(
                exchangeId = route.exchangeId,
                quantity = qty,
                percentOfTotal = percent,
                limitPrice = null,
                expectedPrice = route.price,
                expectedFeeUsd = expectedFee,
                reason = "Pro-rata: ${String.format("%.1f", percent)}% based on liquidity"
            ))
            
            remainingQty -= qty
            if (remainingQty <= 0) break
        }
        
        // Handle any remaining quantity by adding to largest split
        if (remainingQty > 0 && splits.isNotEmpty()) {
            val largestSplit = splits.maxByOrNull { it.quantity }!!
            val idx = splits.indexOf(largestSplit)
            splits[idx] = largestSplit.copy(
                quantity = largestSplit.quantity + remainingQty,
                percentOfTotal = ((largestSplit.quantity + remainingQty) / totalQuantity) * 100
            )
        }
        
        return splits
    }
    
    /**
     * Waterfall split: fill best exchange first, overflow to next
     */
    private fun calculateWaterfallSplits(
        totalQuantity: Double,
        routes: List<ExchangeRoute>,
        side: TradeSide
    ): List<OrderSplit> {
        val splits = mutableListOf<OrderSplit>()
        var remainingQty = totalQuantity
        
        // Sort by price (best first for the side)
        val sortedRoutes = when (side) {
            TradeSide.BUY, TradeSide.LONG -> routes.sortedBy { it.price }    // Lowest price first
            TradeSide.SELL, TradeSide.SHORT -> routes.sortedByDescending { it.price }  // Highest first
        }
        
        for ((index, route) in sortedRoutes.take(maxSplits).withIndex()) {
            val qty = min(route.availableLiquidity, remainingQty)
            
            if (qty <= 0) continue
            
            val percent = (qty / totalQuantity) * 100
            if (percent < 1.0) continue  // Skip very small splits
            
            val expectedFee = qty * route.price * route.fees.estimatedFeePercent / 100
            
            splits.add(OrderSplit(
                exchangeId = route.exchangeId,
                quantity = qty,
                percentOfTotal = percent,
                limitPrice = route.price,
                expectedPrice = route.price,
                expectedFeeUsd = expectedFee,
                delayMs = index * WATERFALL_DELAY_MS,
                reason = "Waterfall: Level ${index + 1} at ${route.price}"
            ))
            
            remainingQty -= qty
            if (remainingQty <= 0) break
        }
        
        return splits
    }
    
    /**
     * Equal split across all exchanges
     */
    private fun calculateEqualSplits(
        totalQuantity: Double,
        routes: List<ExchangeRoute>,
        side: TradeSide
    ): List<OrderSplit> {
        val numExchanges = min(routes.size, maxSplits)
        val qtyPerExchange = totalQuantity / numExchanges
        
        // Sort by score
        val sortedRoutes = routes.sortedByDescending { it.score }.take(numExchanges)
        
        return sortedRoutes.map { route ->
            val expectedFee = qtyPerExchange * route.price * route.fees.estimatedFeePercent / 100
            
            OrderSplit(
                exchangeId = route.exchangeId,
                quantity = qtyPerExchange,
                percentOfTotal = 100.0 / numExchanges,
                limitPrice = null,
                expectedPrice = route.price,
                expectedFeeUsd = expectedFee,
                reason = "Equal split: ${String.format("%.1f", 100.0 / numExchanges)}% each"
            )
        }
    }
    
    /**
     * Liquidity-weighted split: more to exchanges with deeper books
     */
    private fun calculateLiquidityWeightedSplits(
        totalQuantity: Double,
        routes: List<ExchangeRoute>,
        side: TradeSide
    ): List<OrderSplit> {
        // Use square root of liquidity for weighting (reduces impact of outliers)
        val sqrtLiquidity = routes.map { kotlin.math.sqrt(it.availableLiquidity) }
        val totalSqrtLiq = sqrtLiquidity.sum()
        
        if (totalSqrtLiq <= 0) return emptyList()
        
        val splits = mutableListOf<OrderSplit>()
        var remainingQty = totalQuantity
        
        val sortedRoutes = routes.sortedByDescending { it.availableLiquidity }.take(maxSplits)
        
        for ((index, route) in sortedRoutes.withIndex()) {
            val weight = kotlin.math.sqrt(route.availableLiquidity) / totalSqrtLiq
            val qty = min(totalQuantity * weight, remainingQty)
            
            val percent = (qty / totalQuantity) * 100
            if (percent < minSplitPercent) continue
            
            val expectedFee = qty * route.price * route.fees.estimatedFeePercent / 100
            
            splits.add(OrderSplit(
                exchangeId = route.exchangeId,
                quantity = qty,
                percentOfTotal = percent,
                limitPrice = null,
                expectedPrice = route.price,
                expectedFeeUsd = expectedFee,
                reason = "Liquidity-weighted: ${String.format("%.1f", percent)}%"
            ))
            
            remainingQty -= qty
            if (remainingQty <= 0) break
        }
        
        return consolidateSplits(splits, totalQuantity, routes)
    }
    
    /**
     * Fee-optimized split: favour lower-fee exchanges
     */
    private fun calculateFeeOptimizedSplits(
        totalQuantity: Double,
        routes: List<ExchangeRoute>,
        side: TradeSide
    ): List<OrderSplit> {
        // Sort by effective cost (price + fees)
        val sortedByFee = when (side) {
            TradeSide.BUY, TradeSide.LONG -> routes.sortedBy { it.effectivePrice }
            TradeSide.SELL, TradeSide.SHORT -> routes.sortedByDescending { it.effectivePrice }
        }
        
        val splits = mutableListOf<OrderSplit>()
        var remainingQty = totalQuantity
        
        for (route in sortedByFee.take(maxSplits)) {
            // Allocate as much as possible to lowest-cost exchange
            val qty = min(route.availableLiquidity * 0.8, remainingQty)  // 80% of liquidity to avoid impact
            
            if (qty <= 0) continue
            
            val percent = (qty / totalQuantity) * 100
            if (percent < minSplitPercent && splits.isNotEmpty()) continue
            
            val expectedFee = qty * route.price * route.fees.estimatedFeePercent / 100
            
            splits.add(OrderSplit(
                exchangeId = route.exchangeId,
                quantity = qty,
                percentOfTotal = percent,
                limitPrice = null,
                expectedPrice = route.price,
                expectedFeeUsd = expectedFee,
                reason = "Fee-optimized: ${route.fees.estimatedFeePercent}% fee"
            ))
            
            remainingQty -= qty
            if (remainingQty <= 0) break
        }
        
        return consolidateSplits(splits, totalQuantity, routes)
    }
    
    /**
     * Create single split (no splitting needed)
     */
    private fun createSingleSplit(route: ExchangeRoute, quantity: Double): OrderSplit {
        val expectedFee = quantity * route.price * route.fees.estimatedFeePercent / 100
        
        return OrderSplit(
            exchangeId = route.exchangeId,
            quantity = quantity,
            percentOfTotal = 100.0,
            limitPrice = null,
            expectedPrice = route.price,
            expectedFeeUsd = expectedFee,
            reason = "Single exchange: best overall"
        )
    }
    
    /**
     * Consolidate splits and handle remaining quantity
     */
    private fun consolidateSplits(
        splits: MutableList<OrderSplit>,
        totalQuantity: Double,
        routes: List<ExchangeRoute>
    ): List<OrderSplit> {
        if (splits.isEmpty()) return emptyList()
        
        val allocatedQty = splits.sumOf { it.quantity }
        val remainingQty = totalQuantity - allocatedQty
        
        if (remainingQty > 0.0001) {
            // Add remaining to the split with most capacity
            val bestForRemainder = routes
                .filter { route -> splits.none { it.exchangeId == route.exchangeId } || 
                         routes.find { r -> r.exchangeId == route.exchangeId }?.availableLiquidity ?: 0.0 > 
                         splits.find { it.exchangeId == route.exchangeId }?.quantity ?: 0.0 }
                .maxByOrNull { it.availableLiquidity }
            
            if (bestForRemainder != null) {
                val existingSplit = splits.find { it.exchangeId == bestForRemainder.exchangeId }
                if (existingSplit != null) {
                    val idx = splits.indexOf(existingSplit)
                    val newQty = existingSplit.quantity + remainingQty
                    splits[idx] = existingSplit.copy(
                        quantity = newQty,
                        percentOfTotal = (newQty / totalQuantity) * 100
                    )
                } else {
                    val expectedFee = remainingQty * bestForRemainder.price * bestForRemainder.fees.estimatedFeePercent / 100
                    splits.add(OrderSplit(
                        exchangeId = bestForRemainder.exchangeId,
                        quantity = remainingQty,
                        percentOfTotal = (remainingQty / totalQuantity) * 100,
                        limitPrice = null,
                        expectedPrice = bestForRemainder.price,
                        expectedFeeUsd = expectedFee,
                        reason = "Remainder allocation"
                    ))
                }
            }
        }
        
        return splits
    }
    
    /**
     * Recommend best split strategy based on order characteristics
     */
    fun recommendStrategy(
        totalQuantity: Double,
        notionalValue: Double,
        routes: List<ExchangeRoute>,
        volatility: Double
    ): SplitStrategy {
        val totalLiquidity = routes.sumOf { it.availableLiquidity }
        val orderToLiquidityRatio = totalQuantity / totalLiquidity
        
        return when {
            // Large order relative to liquidity - waterfall to minimize impact
            orderToLiquidityRatio > 0.5 -> SplitStrategy.WATERFALL
            
            // High volatility - equal splits for stability
            volatility > 2.0 -> SplitStrategy.EQUAL
            
            // Small order - fee optimization
            notionalValue < 10000 -> SplitStrategy.FEE_OPTIMIZED
            
            // Large notional - liquidity weighted
            notionalValue > 100000 -> SplitStrategy.LIQUIDITY_WEIGHTED
            
            // Default - pro-rata is most balanced
            else -> SplitStrategy.PRO_RATA
        }
    }
}
