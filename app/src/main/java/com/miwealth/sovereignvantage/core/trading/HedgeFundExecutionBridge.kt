/**
 * HEDGE FUND EXECUTION BRIDGE
 * 
 * Sovereign Vantage: Arthur Edition V5.18.22
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * PURPOSE:
 * Bridges HedgeFundBoardOrchestrator decisions to actual trade execution.
 * 
 * BUILD #173: Wiring hedge fund board decisions → OrderExecutor
 * 
 * WORKFLOW:
 * 1. HedgeFundBoard convenes and produces HedgeFundBoardConsensus
 * 2. Bridge receives consensus decision
 * 3. Bridge translates decision → OrderRequest
 * 4. Bridge applies hedge fund risk rules (2% max position, Guardian override)
 * 5. Bridge routes order to TradingCoordinator for execution
 * 
 * WHY SEPARATE FROM GENERAL BOARD:
 * - Hedge fund has different risk rules (2% max position vs general board)
 * - Guardian override logic (cascade detection)
 * - Regime-specific position sizing (Atlas analysis)
 * - Funding arbitrage special handling
 * - Different execution requirements (spot+perp pairs)
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */

package com.miwealth.sovereignvantage.core.trading

import android.util.Log
import com.miwealth.sovereignvantage.core.ai.BoardVote
import com.miwealth.sovereignvantage.core.ai.HedgeFundBoardConsensus
import com.miwealth.sovereignvantage.core.OrderType
import com.miwealth.sovereignvantage.core.TradeSide
import com.miwealth.sovereignvantage.core.trading.engine.OrderExecutionResult
import com.miwealth.sovereignvantage.core.trading.engine.OrderExecutor
import com.miwealth.sovereignvantage.core.trading.engine.OrderRequest
import com.miwealth.sovereignvantage.core.trading.engine.PositionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Configuration for hedge fund execution.
 */
data class HedgeFundExecutionConfig(
    val maxPositionRiskPercent: Double = 2.0,      // Max 2% per position (hedge fund conservative)
    val minConfidenceToTrade: Double = 0.65,       // 65% confidence minimum (higher than general)
    val respectGuardianOverride: Boolean = true,   // Guardian can force SELL on cascade risk
    val enableFundingArb: Boolean = true,          // Allow funding arbitrage pairs
    val maxCascadeRiskLevel: Double = 0.7,         // Max tolerable cascade risk
    val useRegimeBasedSizing: Boolean = true       // Use Atlas regime analysis for sizing
)

/**
 * Execution result from hedge fund bridge.
 */
sealed class HedgeFundExecutionResult {
    data class OrderPlaced(
        val orderId: String,
        val symbol: String,
        val side: TradeSide,
        val quantity: Double,
        val reasoning: String
    ) : HedgeFundExecutionResult()
    
    data class OrderRejected(
        val reason: String,
        val consensus: HedgeFundBoardConsensus
    ) : HedgeFundExecutionResult()
    
    data class NoAction(
        val reason: String
    ) : HedgeFundExecutionResult()
}

/**
 * Bridges HedgeFundBoardOrchestrator decisions to OrderExecutor.
 * 
 * This is the execution layer for the hedge fund board - it takes board
 * decisions and translates them into actual trade orders while respecting
 * hedge fund specific risk rules.
 */
class HedgeFundExecutionBridge(
    private val orderExecutor: OrderExecutor,
    private val tradingCoordinator: TradingCoordinator,
    private val positionManager: PositionManager,
    private val config: HedgeFundExecutionConfig = HedgeFundExecutionConfig(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    
    companion object {
        private const val TAG = "HedgeFundExecution"
    }
    
    // Track last execution for debouncing
    private var lastExecutionTimestamp = 0L
    private val minExecutionIntervalMs = 5000L  // 5 seconds between executions
    
    /**
     * Process a hedge fund board consensus and potentially execute trades.
     * 
     * This is the main entry point - called when HeartbeatCoordinator delivers
     * a new snapshot and HedgeFundBoard makes a decision.
     */
    suspend fun processConsensus(
        consensus: HedgeFundBoardConsensus,
        symbol: String,
        currentPrice: Double,
        portfolioValue: Double
    ): HedgeFundExecutionResult {
        
        // 1. CHECK GUARDIAN OVERRIDE (CASCADE RISK)
        if (config.respectGuardianOverride && consensus.guardianOverride) {
            Log.w(TAG, "🛡️ GUARDIAN OVERRIDE: Cascade risk too high (${consensus.cascadeRiskLevel})")
            Log.w(TAG, "   Forcing SELL or NO TRADE regardless of consensus")
            
            // Check if we have open positions to close
            val openPositions = positionManager.getOpenPositions()
            if (openPositions.isNotEmpty()) {
                // Close all positions due to cascade risk
                return executeCloseAll(
                    reason = "Guardian cascade override - risk level ${consensus.cascadeRiskLevel}"
                )
            } else {
                return HedgeFundExecutionResult.NoAction(
                    reason = "Guardian override: No positions to close, blocking new entries"
                )
            }
        }
        
        // 2. CHECK CONFIDENCE THRESHOLD
        if (consensus.confidence < config.minConfidenceToTrade) {
            return HedgeFundExecutionResult.NoAction(
                reason = "Confidence ${consensus.confidence} below threshold ${config.minConfidenceToTrade}"
            )
        }
        
        // 3. CHECK CASCADE RISK LEVEL
        if (consensus.cascadeRiskLevel > config.maxCascadeRiskLevel) {
            Log.w(TAG, "⚠️ Cascade risk ${consensus.cascadeRiskLevel} exceeds max ${config.maxCascadeRiskLevel}")
            return HedgeFundExecutionResult.NoAction(
                reason = "Cascade risk too high: ${consensus.cascadeRiskLevel}"
            )
        }
        
        // 4. DEBOUNCE (avoid rapid-fire orders)
        val now = System.currentTimeMillis()
        if (now - lastExecutionTimestamp < minExecutionIntervalMs) {
            return HedgeFundExecutionResult.NoAction(
                reason = "Debounce: Last execution ${now - lastExecutionTimestamp}ms ago"
            )
        }
        
        // 5. TRANSLATE DECISION TO ACTION
        val result = when (consensus.finalDecision) {
            BoardVote.STRONG_BUY -> executeStrongBuy(symbol, currentPrice, portfolioValue, consensus)
            BoardVote.BUY -> executeBuy(symbol, currentPrice, portfolioValue, consensus)
            BoardVote.HOLD -> HedgeFundExecutionResult.NoAction("Board voted HOLD")
            BoardVote.SELL -> executeSell(symbol, consensus)
            BoardVote.STRONG_SELL -> executeStrongSell(symbol, consensus)
        }
        
        // 6. UPDATE LAST EXECUTION TIME
        if (result is HedgeFundExecutionResult.OrderPlaced) {
            lastExecutionTimestamp = now
        }
        
        return result
    }
    
    /**
     * Execute STRONG_BUY - full hedge fund position (2% risk).
     */
    private suspend fun executeStrongBuy(
        symbol: String,
        currentPrice: Double,
        portfolioValue: Double,
        consensus: HedgeFundBoardConsensus
    ): HedgeFundExecutionResult {
        
        // Calculate position size (2% max risk for hedge fund)
        val basePositionSize = portfolioValue * (config.maxPositionRiskPercent / 100.0)
        
        // Apply regime-based sizing if enabled
        val positionSize = if (config.useRegimeBasedSizing) {
            basePositionSize * consensus.recommendedPositionSize
        } else {
            basePositionSize
        }
        
        val quantity = positionSize / currentPrice
        
        Log.d(TAG, "🟢 STRONG_BUY: $symbol")
        Log.d(TAG, "   Confidence: ${consensus.confidence}")
        Log.d(TAG, "   Position size: $$positionSize (${config.maxPositionRiskPercent}% of portfolio)")
        Log.d(TAG, "   Quantity: $quantity @ $$currentPrice")
        Log.d(TAG, "   Regime multiplier: ${consensus.recommendedPositionSize}")
        
        // Create order request
        val orderRequest = OrderRequest(
            symbol = symbol,
            side = TradeSide.BUY,
            type = OrderType.MARKET,
            quantity = quantity,
            price = null,  // Market order
            stopLossPrice = null,  // STAHL handles this
            takeProfitPrice = null,  // STAHL handles this
            leverage = 1.0  // Hedge fund uses 1x (conservative)
        )
        
        // Execute via TradingCoordinator
        return executeTrade(orderRequest, consensus.synthesis)
    }
    
    /**
     * Execute BUY - half position size (1% risk).
     */
    private suspend fun executeBuy(
        symbol: String,
        currentPrice: Double,
        portfolioValue: Double,
        consensus: HedgeFundBoardConsensus
    ): HedgeFundExecutionResult {
        
        // Half position for regular BUY
        val basePositionSize = portfolioValue * (config.maxPositionRiskPercent / 100.0) * 0.5
        val positionSize = if (config.useRegimeBasedSizing) {
            basePositionSize * consensus.recommendedPositionSize
        } else {
            basePositionSize
        }
        
        val quantity = positionSize / currentPrice
        
        Log.d(TAG, "🟢 BUY: $symbol")
        Log.d(TAG, "   Confidence: ${consensus.confidence}")
        Log.d(TAG, "   Position size: $$positionSize (1% of portfolio)")
        Log.d(TAG, "   Quantity: $quantity @ $$currentPrice")
        
        val orderRequest = OrderRequest(
            symbol = symbol,
            side = TradeSide.BUY,
            type = OrderType.MARKET,
            quantity = quantity,
            price = null,
            stopLossPrice = null,
            takeProfitPrice = null,
            leverage = 1.0
        )
        
        return executeTrade(orderRequest, consensus.synthesis)
    }
    
    /**
     * Execute SELL - close 50% of position.
     */
    private suspend fun executeSell(
        symbol: String,
        consensus: HedgeFundBoardConsensus
    ): HedgeFundExecutionResult {
        
        // Find open position
        val openPositions = positionManager.getOpenPositions()
        val position = openPositions.find { it.symbol == symbol }
        
        if (position == null) {
            return HedgeFundExecutionResult.NoAction(
                reason = "SELL signal but no open position for $symbol"
            )
        }
        
        // Close 50% of position
        val sellQuantity = position.quantity * 0.5
        
        Log.d(TAG, "🔴 SELL (50%): $symbol")
        Log.d(TAG, "   Confidence: ${consensus.confidence}")
        Log.d(TAG, "   Closing: $sellQuantity of ${position.quantity}")
        
        val orderRequest = OrderRequest(
            symbol = symbol,
            side = TradeSide.SELL,
            type = OrderType.MARKET,
            quantity = sellQuantity,
            price = null,
            stopLossPrice = null,
            takeProfitPrice = null,
            leverage = 1.0
        )
        
        return executeTrade(orderRequest, consensus.synthesis)
    }
    
    /**
     * Execute STRONG_SELL - close entire position.
     */
    private suspend fun executeStrongSell(
        symbol: String,
        consensus: HedgeFundBoardConsensus
    ): HedgeFundExecutionResult {
        
        val openPositions = positionManager.getOpenPositions()
        val position = openPositions.find { it.symbol == symbol }
        
        if (position == null) {
            return HedgeFundExecutionResult.NoAction(
                reason = "STRONG_SELL signal but no open position for $symbol"
            )
        }
        
        Log.d(TAG, "🔴🔴 STRONG_SELL (100%): $symbol")
        Log.d(TAG, "   Confidence: ${consensus.confidence}")
        Log.d(TAG, "   Closing: ${position.quantity} (full position)")
        
        val orderRequest = OrderRequest(
            symbol = symbol,
            side = TradeSide.SELL,
            type = OrderType.MARKET,
            quantity = position.quantity,
            price = null,
            stopLossPrice = null,
            takeProfitPrice = null,
            leverage = 1.0
        )
        
        return executeTrade(orderRequest, consensus.synthesis)
    }
    
    /**
     * Close all positions (Guardian override scenario).
     */
    private suspend fun executeCloseAll(reason: String): HedgeFundExecutionResult {
        Log.w(TAG, "🛡️ GUARDIAN FORCE CLOSE: $reason")
        
        val openPositions = positionManager.getOpenPositions()
        if (openPositions.isEmpty()) {
            return HedgeFundExecutionResult.NoAction("No positions to close")
        }
        
        // Close each position
        scope.launch {
            openPositions.forEach { position ->
                val orderRequest = OrderRequest(
                    symbol = position.symbol,
                    side = TradeSide.SELL,
                    type = OrderType.MARKET,
                    quantity = position.quantity,
                    price = null,
                    stopLossPrice = null,
                    takeProfitPrice = null,
                    leverage = 1.0
                )
                
                executeTrade(orderRequest, reason)
            }
        }
        
        return HedgeFundExecutionResult.OrderPlaced(
            orderId = "GUARDIAN_CLOSE_ALL",
            symbol = "MULTIPLE",
            side = TradeSide.SELL,
            quantity = openPositions.sumOf { it.quantity },
            reasoning = reason
        )
    }
    
    /**
     * Execute trade via OrderExecutor.
     */
    private suspend fun executeTrade(
        orderRequest: OrderRequest,
        reasoning: String
    ): HedgeFundExecutionResult {
        
        return try {
            // Route through OrderExecutor (applies all exchange logic + executes)
            val result = orderExecutor.placeOrder(orderRequest)
            
            when (result) {
                is OrderExecutionResult.Success -> {
                    Log.d(TAG, "✅ Hedge fund order placed: ${result.order.orderId}")
                    HedgeFundExecutionResult.OrderPlaced(
                        orderId = result.order.orderId,
                        symbol = orderRequest.symbol,
                        side = orderRequest.side,
                        quantity = orderRequest.quantity,
                        reasoning = reasoning
                    )
                }
                is OrderExecutionResult.PartialFill -> {
                    Log.d(TAG, "⚠️ Hedge fund order partially filled: ${result.order.orderId}")
                    HedgeFundExecutionResult.OrderPlaced(
                        orderId = result.order.orderId,
                        symbol = orderRequest.symbol,
                        side = orderRequest.side,
                        quantity = result.order.executedQuantity,
                        reasoning = "$reasoning (partial fill: ${result.order.executedQuantity}/${orderRequest.quantity})"
                    )
                }
                is OrderExecutionResult.Rejected -> {
                    Log.e(TAG, "❌ Hedge fund order rejected: ${result.reason}")
                    HedgeFundExecutionResult.OrderRejected(
                        reason = result.reason,
                        consensus = HedgeFundBoardConsensus(
                            finalDecision = BoardVote.HOLD,
                            weightedScore = 0.0,
                            confidence = 0.0,
                            unanimousCount = 0,
                            dissenterReasons = emptyList(),
                            opinions = emptyList(),
                            synthesis = "Order rejected by exchange",
                            cascadeRiskLevel = 0.0,
                            guardianOverride = false,
                            regimeAnalysis = null,
                            recommendedPositionSize = 0.0
                        )
                    )
                }
                is OrderExecutionResult.Error -> {
                    Log.e(TAG, "❌ Hedge fund order error: ${result.exception.message}")
                    HedgeFundExecutionResult.OrderRejected(
                        reason = result.exception.message ?: "Unknown error",
                        consensus = HedgeFundBoardConsensus(
                            finalDecision = BoardVote.HOLD,
                            weightedScore = 0.0,
                            confidence = 0.0,
                            unanimousCount = 0,
                            dissenterReasons = emptyList(),
                            opinions = emptyList(),
                            synthesis = "Order execution error",
                            cascadeRiskLevel = 0.0,
                            guardianOverride = false,
                            regimeAnalysis = null,
                            recommendedPositionSize = 0.0
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception during hedge fund execution: ${e.message}", e)
            HedgeFundExecutionResult.OrderRejected(
                reason = e.message ?: "Exception during execution",
                consensus = HedgeFundBoardConsensus(
                    finalDecision = BoardVote.HOLD,
                    weightedScore = 0.0,
                    confidence = 0.0,
                    unanimousCount = 0,
                    dissenterReasons = emptyList(),
                    opinions = emptyList(),
                    synthesis = "Exception during execution",
                    cascadeRiskLevel = 0.0,
                    guardianOverride = false,
                    regimeAnalysis = null,
                    recommendedPositionSize = 0.0
                )
            )
        }
    }
}
