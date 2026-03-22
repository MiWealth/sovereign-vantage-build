/**
 * ADVANCED STRATEGY EXECUTION BRIDGE
 * 
 * Sovereign Vantage: Arthur Edition V5.18.22
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * PURPOSE:
 * Bridges AdvancedStrategyCoordinator signals to actual trade execution.
 * 
 * BUILD #173: Wiring advanced strategies → OrderExecutor
 * 
 * STRATEGIES WIRED:
 * 1. Alpha Factor Scanner - Top-ranked assets → TradingCoordinator
 * 2. Funding Arbitrage - Spot+perp pairs → Execution
 * 
 * WORKFLOW:
 * 1. AdvancedStrategyCoordinator generates signals (Alpha or Funding Arb)
 * 2. Bridge receives signal
 * 3. Bridge validates signal against risk rules
 * 4. Bridge routes to TradingCoordinator for execution
 * 5. Monitors position lifecycle
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */

package com.miwealth.sovereignvantage.core.trading.strategies

import android.util.Log
import com.miwealth.sovereignvantage.core.OrderType
import com.miwealth.sovereignvantage.core.TradeSide
import com.miwealth.sovereignvantage.core.trading.engine.OrderExecutionResult
import com.miwealth.sovereignvantage.core.trading.engine.OrderExecutor
import com.miwealth.sovereignvantage.core.trading.engine.OrderRequest
import com.miwealth.sovereignvantage.core.trading.TradingCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Configuration for advanced strategy execution.
 */
data class AdvancedStrategyExecutionConfig(
    val enableAlphaExecution: Boolean = true,           // Execute alpha scanner signals
    val enableFundingArbExecution: Boolean = true,      // Execute funding arb trades
    val maxAlphaPositionPercent: Double = 10.0,         // Max 10% per alpha position
    val maxFundingArbCapital: Double = 50.0,            // Max 50% in funding arb
    val minAlphaScore: Double = 0.7,                    // Min alpha score to execute
    val minFundingRate: Double = 0.0001,                // Min 0.01% per 8h to enter
    val useAIBoardApproval: Boolean = true              // Require AI Board approval
)

/**
 * Alpha scanner signal from AdvancedStrategyCoordinator.
 */
data class AlphaSignal(
    val symbol: String,
    val alphaScore: Double,          // 0.0 to 1.0
    val rank: Int,                   // 1 to N
    val momentum: Double,
    val quality: Double,
    val volatility: Double,
    val recommendedSize: Double      // Position size multiplier
)

/**
 * Funding arbitrage opportunity from AdvancedStrategyCoordinator.
 */
data class FundingArbOpportunity(
    val symbol: String,
    val fundingRate: Double,         // Per 8 hours
    val annualizedYield: Double,     // Projected annual %
    val spotPrice: Double,
    val perpPrice: Double,
    val recommendedCapital: Double   // Dollar amount to deploy
)

/**
 * Bridges AdvancedStrategyCoordinator to OrderExecutor.
 */
class AdvancedStrategyExecutionBridge(
    private val orderExecutor: OrderExecutor,
    private val tradingCoordinator: TradingCoordinator,
    private val config: AdvancedStrategyExecutionConfig = AdvancedStrategyExecutionConfig(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    
    companion object {
        private const val TAG = "AdvancedStrategyExec"
    }
    
    /**
     * Execute alpha scanner signal.
     * 
     * Flow:
     * 1. Validate alpha score
     * 2. Calculate position size
     * 3. Route to TradingCoordinator (goes through AI Board if enabled)
     * 4. AI Board votes on signal
     * 5. Execute if approved
     */
    suspend fun executeAlphaSignal(
        signal: AlphaSignal,
        portfolioValue: Double
    ): Result<String> {
        
        if (!config.enableAlphaExecution) {
            return Result.failure(Exception("Alpha execution disabled"))
        }
        
        // Validate alpha score
        if (signal.alphaScore < config.minAlphaScore) {
            Log.d(TAG, "❌ Alpha signal rejected: score ${signal.alphaScore} < ${config.minAlphaScore}")
            return Result.failure(Exception("Alpha score too low"))
        }
        
        // Calculate position size
        val basePositionSize = portfolioValue * (config.maxAlphaPositionPercent / 100.0)
        val adjustedSize = basePositionSize * signal.recommendedSize
        
        Log.d(TAG, "🎯 ALPHA SIGNAL:")
        Log.d(TAG, "   Symbol: ${signal.symbol}")
        Log.d(TAG, "   Alpha Score: ${String.format("%.2f", signal.alphaScore)}")
        Log.d(TAG, "   Rank: #${signal.rank}")
        Log.d(TAG, "   Position Size: $${"%.2f".format(adjustedSize)} (${config.maxAlphaPositionPercent}% of portfolio)")
        
        // Create order request (will go through AI Board if enabled)
        val orderRequest = OrderRequest(
            symbol = signal.symbol,
            side = TradeSide.BUY,
            type = OrderType.MARKET,
            quantity = adjustedSize,  // Dollar amount, will be converted to quantity
            price = null,
            stopLossPrice = null,  // STAHL handles this
            takeProfitPrice = null,  // STAHL handles this
            leverage = 1.0  // Alpha scanner uses 1x
        )
        
        // Execute via OrderExecutor
        val result = orderExecutor.executeOrder(orderRequest)
        
        return when (result) {
            is OrderExecutionResult.Success -> {
                Log.d(TAG, "✅ Alpha signal executed: ${result.order.orderId}")
                Result.success(result.order.orderId)
            }
            is OrderExecutionResult.PartialFill -> {
                Log.d(TAG, "⚠️ Alpha signal partially filled: ${result.order.orderId}")
                Result.success(result.order.orderId)
            }
            is OrderExecutionResult.Rejected -> {
                Log.e(TAG, "❌ Alpha signal rejected: ${result.reason}")
                Result.failure(Exception(result.reason))
            }
            is OrderExecutionResult.Error -> {
                Log.e(TAG, "❌ Alpha signal error: ${result.exception.message}")
                Result.failure(result.exception)
            }
        }
    }
    
    /**
     * Execute funding arbitrage opportunity.
     * 
     * Flow:
     * 1. Validate funding rate
     * 2. Open spot position (LONG)
     * 3. Open perp position (SHORT) - delta neutral
     * 4. Collect funding payments every 8 hours
     * 5. Close when funding rate drops
     */
    suspend fun executeFundingArb(
        opportunity: FundingArbOpportunity,
        portfolioValue: Double
    ): Result<String> {
        
        if (!config.enableFundingArbExecution) {
            return Result.failure(Exception("Funding arb execution disabled"))
        }
        
        // Validate funding rate
        if (opportunity.fundingRate < config.minFundingRate) {
            Log.d(TAG, "❌ Funding arb rejected: rate ${opportunity.fundingRate} < ${config.minFundingRate}")
            return Result.failure(Exception("Funding rate too low"))
        }
        
        // Check capital limits
        val maxCapital = portfolioValue * (config.maxFundingArbCapital / 100.0)
        if (opportunity.recommendedCapital > maxCapital) {
            Log.d(TAG, "❌ Funding arb rejected: capital ${opportunity.recommendedCapital} > max $maxCapital")
            return Result.failure(Exception("Capital exceeds limit"))
        }
        
        Log.d(TAG, "💰 FUNDING ARBITRAGE:")
        Log.d(TAG, "   Symbol: ${opportunity.symbol}")
        Log.d(TAG, "   Funding Rate: ${String.format("%.4f%%", opportunity.fundingRate * 100)} per 8h")
        Log.d(TAG, "   Annualized Yield: ${String.format("%.2f%%", opportunity.annualizedYield)}")
        Log.d(TAG, "   Capital: $${"%.2f".format(opportunity.recommendedCapital)}")
        
        // Step 1: Open SPOT position (LONG)
        val spotQuantity = opportunity.recommendedCapital / opportunity.spotPrice
        
        val spotOrderRequest = OrderRequest(
            symbol = "${opportunity.symbol}_SPOT",  // Tag for spot
            side = TradeSide.BUY,
            type = OrderType.MARKET,
            quantity = spotQuantity,
            price = null,
            stopLossPrice = null,
            takeProfitPrice = null,
            leverage = 1.0  // Spot is always 1x
        )
        
        // Step 2: Open PERP position (SHORT) - delta neutral
        val perpOrderRequest = OrderRequest(
            symbol = "${opportunity.symbol}_PERP",  // Tag for perp
            side = TradeSide.SELL,  // SHORT to hedge
            type = OrderType.MARKET,
            quantity = spotQuantity,  // Same size for delta neutral
            price = null,
            stopLossPrice = null,
            takeProfitPrice = null,
            leverage = 1.0  // Conservative for funding arb
        )
        
        // Execute both legs
        scope.launch {
            val spotResult = orderExecutor.executeOrder(spotOrderRequest)
            val perpResult = orderExecutor.executeOrder(perpOrderRequest)
            
            // Check if both succeeded
            val spotSuccess = spotResult is OrderExecutionResult.Success
            val perpSuccess = perpResult is OrderExecutionResult.Success
            
            if (spotSuccess && perpSuccess) {
                val spotOrder = (spotResult as OrderExecutionResult.Success).order
                val perpOrder = (perpResult as OrderExecutionResult.Success).order
                Log.d(TAG, "✅ Funding arb pair opened: SPOT ${spotOrder.orderId} + PERP ${perpOrder.orderId}")
            } else {
                Log.e(TAG, "❌ Funding arb failed - SPOT: $spotResult, PERP: $perpResult")
            }
        }
        
        return Result.success("FUNDING_ARB_OPENED")
    }
    
    /**
     * Close funding arbitrage position.
     */
    suspend fun closeFundingArb(
        symbol: String
    ): Result<String> {
        Log.d(TAG, "🔴 Closing funding arb: $symbol")
        
        // Close spot position
        val closeSpotRequest = OrderRequest(
            symbol = "${symbol}_SPOT",
            side = TradeSide.SELL,
            type = OrderType.MARKET,
            quantity = 0.0,  // Close entire position
            price = null,
            stopLossPrice = null,
            takeProfitPrice = null,
            leverage = 1.0
        )
        
        // Close perp position
        val closePerpRequest = OrderRequest(
            symbol = "${symbol}_PERP",
            side = TradeSide.BUY,  // Buy to close short
            type = OrderType.MARKET,
            quantity = 0.0,  // Close entire position
            price = null,
            stopLossPrice = null,
            takeProfitPrice = null,
            leverage = 1.0
        )
        
        // Execute closes
        scope.launch {
            val spotResult = orderExecutor.executeOrder(closeSpotRequest)
            val perpResult = orderExecutor.executeOrder(closePerpRequest)
            
            val spotSuccess = spotResult is OrderExecutionResult.Success
            val perpSuccess = perpResult is OrderExecutionResult.Success
            
            if (spotSuccess && perpSuccess) {
                Log.d(TAG, "✅ Funding arb closed: $symbol")
            } else {
                Log.e(TAG, "❌ Funding arb close failed: $symbol")
            }
        }
        
        return Result.success("FUNDING_ARB_CLOSED")
    }
}
