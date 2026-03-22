package com.miwealth.sovereignvantage.core.trading.engine

import com.miwealth.sovereignvantage.core.*
import com.miwealth.sovereignvantage.core.trading.StahlStairStop
import com.miwealth.sovereignvantage.core.trading.StahlStairStopManager
import com.miwealth.sovereignvantage.core.trading.StahlPreset
import com.miwealth.sovereignvantage.core.trading.StahlPosition
import com.miwealth.sovereignvantage.core.trading.ExitInfo
import com.miwealth.sovereignvantage.core.trading.ExitReason
import com.miwealth.sovereignvantage.core.trading.TradeDirection
import com.miwealth.sovereignvantage.core.trading.utils.LiquidationValidator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Position Manager - Real-time position tracking with STAHL integration
 * 
 * Tracks all open positions with:
 * - Real-time P&L calculation
 * - STAHL Stair Stop™ automatic updates
 * - Margin and leverage monitoring
 * - Risk exposure calculations
 * 
 * © 2025 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO: Arthur Iain McManus (1966-2025)
 */

data class Position(
    val id: String,
    val symbol: String,
    val side: TradeSide,
    val quantity: Double,
    val averageEntryPrice: Double,
    val currentPrice: Double,
    val leverage: Double = 1.0,
    val unrealizedPnl: Double,
    val unrealizedPnlPercent: Double,
    val realizedPnl: Double = 0.0,
    val margin: Double,
    val liquidationPrice: Double? = null,
    val openTime: Long,
    val lastUpdateTime: Long = System.currentTimeMillis(),
    
    // STAHL Stair Stop tracking
    val initialStopPrice: Double,
    val currentStopPrice: Double,
    val takeProfitPrice: Double,
    val maxProfitPercent: Double = 0.0,
    val stahlLevel: Int = 0,
    val isBreakeven: Boolean = false,
    
    // Additional metadata
    val exchange: String,
    val fees: Double = 0.0,
    val notes: String = ""
)

data class PositionSummary(
    val totalPositions: Int,
    val longPositions: Int,
    val shortPositions: Int,
    val totalUnrealizedPnl: Double,
    val totalRealizedPnl: Double,
    val totalMargin: Double,
    val totalExposure: Double,
    val largestPosition: Position?,
    val mostProfitable: Position?,
    val mostLosing: Position?
)

sealed class PositionEvent {
    data class Opened(val position: Position) : PositionEvent()
    data class Updated(val position: Position, val previousPrice: Double) : PositionEvent()
    data class Closed(val position: Position, val exitPrice: Double, val pnl: Double) : PositionEvent()
    data class StopUpdated(val position: Position, val newStop: Double, val stahlLevel: Int) : PositionEvent()
    data class LiquidationWarning(val position: Position, val currentPrice: Double) : PositionEvent()
    data class BreakevenReached(val position: Position) : PositionEvent()
    data class TakeProfitHit(val position: Position, val exitPrice: Double) : PositionEvent()
    data class StopLossHit(val position: Position, val exitPrice: Double) : PositionEvent()
}

class PositionManager(
    private val orderExecutor: OrderExecutor? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    
    private val positions = ConcurrentHashMap<String, Position>()
    
    /**
     * Select STAHL preset based on leverage and create configured instance.
     * Higher leverage = tighter stops (SCALPING preset).
     * Lower leverage = more room (CONSERVATIVE preset).
     * 
     * BUILD #169 FIX: Use StahlStairStop.getConfig() to get preset config,
     * then create StahlStairStopManager instance with that config.
     */
    private fun getStahlInstance(leverage: Double): StahlStairStopManager {
        val preset = when {
            leverage >= 3.0 -> StahlPreset.SCALPING      // 3x+ leverage = tight stops
            leverage >= 2.0 -> StahlPreset.AGGRESSIVE    // 2-3x leverage = aggressive
            leverage >= 1.5 -> StahlPreset.MODERATE      // 1.5-2x leverage = moderate
            else -> StahlPreset.CONSERVATIVE             // 1-1.5x leverage = conservative
        }
        return StahlStairStopManager.forPreset(preset)
    }
    
    private val _positionEvents = MutableSharedFlow<PositionEvent>(replay = 0, extraBufferCapacity = 64)
    val positionEvents: SharedFlow<PositionEvent> = _positionEvents.asSharedFlow()
    
    private val _positions = MutableStateFlow<List<Position>>(emptyList())
    val allPositions: StateFlow<List<Position>> = _positions.asStateFlow()
    
    /**
     * Open a new position
     */
    fun openPosition(
        symbol: String,
        side: TradeSide,
        quantity: Double,
        entryPrice: Double,
        leverage: Double = 1.0,
        exchange: String,
        useStahl: Boolean = true
    ): Position {
        val positionId = generatePositionId(symbol, side)
        val direction = if (side == TradeSide.BUY || side == TradeSide.LONG) TradeDirection.LONG else TradeDirection.SHORT
        
        // BUILD #169: Use appropriate STAHL instance based on leverage
        val stahlInstance = getStahlInstance(leverage)
        val initialStop = if (useStahl) stahlInstance.calculateInitialStop(entryPrice, direction) ?: 0.0 else 0.0
        val takeProfit = if (useStahl) stahlInstance.calculateTakeProfit(entryPrice, direction) ?: 0.0 else 0.0
        val margin = (quantity * entryPrice) / leverage
        
        val position = Position(
            id = positionId,
            symbol = symbol,
            side = side,
            quantity = quantity,
            averageEntryPrice = entryPrice,
            currentPrice = entryPrice,
            leverage = leverage,
            unrealizedPnl = 0.0,
            unrealizedPnlPercent = 0.0,
            margin = margin,
            openTime = System.currentTimeMillis(),
            initialStopPrice = initialStop,
            currentStopPrice = initialStop,
            takeProfitPrice = takeProfit,
            exchange = exchange
        )
        
        positions[positionId] = position
        updatePositionsList()
        
        scope.launch {
            _positionEvents.emit(PositionEvent.Opened(position))
        }
        
        return position
    }
    
    /**
     * Update position with new market price
     * This is called on every price tick
     */
    fun updatePrice(symbol: String, currentPrice: Double, high: Double? = null, low: Double? = null) {
        positions.values
            .filter { it.symbol == symbol }
            .forEach { position ->
                updatePositionPrice(position, currentPrice, high ?: currentPrice, low ?: currentPrice)
            }
    }
    
    private fun updatePositionPrice(
        position: Position,
        currentPrice: Double,
        high: Double,
        low: Double
    ) {
        val previousPrice = position.currentPrice
        val direction = if (position.side == TradeSide.BUY || position.side == TradeSide.LONG) TradeDirection.LONG else TradeDirection.SHORT
        
        // Calculate P&L
        val priceDiff = if (direction == TradeDirection.LONG) {
            currentPrice - position.averageEntryPrice
        } else {
            position.averageEntryPrice - currentPrice
        }
        
        val unrealizedPnl = priceDiff * position.quantity * position.leverage
        val unrealizedPnlPercent = (priceDiff / position.averageEntryPrice) * 100 * position.leverage
        
        // Update max profit for STAHL
        val newMaxProfit = max(position.maxProfitPercent, unrealizedPnlPercent)
        
        // BUILD #169: Get appropriate STAHL instance based on position leverage
        val stahlInstance = getStahlInstance(position.leverage)
        
        // Calculate new STAHL stop
        val stahlResult = stahlInstance.calculateStairStop(
            position.averageEntryPrice,
            newMaxProfit,
            direction
        )
        
        // Stop can only move in favor
        val newStop = stahlInstance.updateStairStop(position.currentStopPrice, stahlResult.stopPrice, direction)
        
        // Check for stop/TP hits
        val exitInfo = checkExitConditions(position, high, low, newStop, direction)
        
        if (exitInfo != null) {
            // Position should be closed
            closePosition(position.id, exitInfo.exitPrice, exitInfo.exitReason.name)
            return
        }
        
        // Update position
        val updatedPosition = position.copy(
            currentPrice = currentPrice,
            unrealizedPnl = unrealizedPnl,
            unrealizedPnlPercent = unrealizedPnlPercent,
            maxProfitPercent = newMaxProfit,
            currentStopPrice = newStop,
            stahlLevel = stahlResult.currentLevel,
            isBreakeven = stahlResult.isBreakeven,
            lastUpdateTime = System.currentTimeMillis()
        )
        
        positions[position.id] = updatedPosition
        updatePositionsList()
        
        // Emit events
        scope.launch {
            _positionEvents.emit(PositionEvent.Updated(updatedPosition, previousPrice))
            
            // Check for new STAHL level
            if (stahlResult.currentLevel > position.stahlLevel) {
                _positionEvents.emit(PositionEvent.StopUpdated(updatedPosition, newStop, stahlResult.currentLevel))
            }
            
            // Check for breakeven reached
            if (stahlResult.isBreakeven && !position.isBreakeven) {
                _positionEvents.emit(PositionEvent.BreakevenReached(updatedPosition))
            }
            
            // Check liquidation warning (within 5% of liquidation)
            updatedPosition.liquidationPrice?.let { liqPrice ->
                val distanceToLiq = abs(currentPrice - liqPrice) / currentPrice
                if (distanceToLiq < 0.05) {
                    _positionEvents.emit(PositionEvent.LiquidationWarning(updatedPosition, currentPrice))
                }
            }
        }
    }
    
    private fun checkExitConditions(
        position: Position,
        high: Double,
        low: Double,
        stopPrice: Double,
        direction: TradeDirection
    ): ExitInfo? {
        // BUILD #169: Get appropriate STAHL instance based on position leverage
        val stahlInstance = getStahlInstance(position.leverage)
        
        // Check take profit
        if (position.takeProfitPrice > 0) {
            val tpHit = if (direction == TradeDirection.LONG) {
                high >= position.takeProfitPrice
            } else {
                low <= position.takeProfitPrice
            }
            
            if (tpHit) {
                scope.launch {
                    _positionEvents.emit(PositionEvent.TakeProfitHit(position, position.takeProfitPrice))
                }
                return ExitInfo(
                    exitPrice = position.takeProfitPrice,
                    exitReason = ExitReason.TAKE_PROFIT,
                    profitPercent = stahlInstance.calculateProfitPercent(position.averageEntryPrice, position.takeProfitPrice, direction),
                    profitDollar = 0.0,
                    stairLevel = 0
                )
            }
        }
        
        // Check stop loss
        if (stopPrice > 0) {
            val stopHit = if (direction == TradeDirection.LONG) {
                low <= stopPrice
            } else {
                high >= stopPrice
            }
            
            if (stopHit) {
                val reason = if (stopPrice == position.initialStopPrice) "Initial Stop" else "STAHL Stop (Level ${position.stahlLevel})"
                scope.launch {
                    _positionEvents.emit(PositionEvent.StopLossHit(position, stopPrice))
                }
                return ExitInfo(
                    exitPrice = stopPrice,
                    exitReason = if (stopPrice == position.initialStopPrice) ExitReason.INITIAL_STOP else ExitReason.STAIR_STOP,
                    profitPercent = stahlInstance.calculateProfitPercent(position.averageEntryPrice, stopPrice, direction),
                    profitDollar = 0.0,
                    stairLevel = position.stahlLevel
                )
            }
        }
        
        return null
    }
    
    /**
     * Close a position
     */
    fun closePosition(positionId: String, exitPrice: Double, reason: String = "Manual Close"): Double? {
        val position = positions.remove(positionId) ?: return null
        
        val direction = if (position.side == TradeSide.BUY || position.side == TradeSide.LONG) TradeDirection.LONG else TradeDirection.SHORT
        val priceDiff = if (direction == TradeDirection.LONG) {
            exitPrice - position.averageEntryPrice
        } else {
            position.averageEntryPrice - exitPrice
        }
        
        val realizedPnl = priceDiff * position.quantity * position.leverage - position.fees
        
        updatePositionsList()
        
        scope.launch {
            _positionEvents.emit(PositionEvent.Closed(position, exitPrice, realizedPnl))
        }
        
        return realizedPnl
    }
    
    /**
     * Add to existing position
     */
    fun addToPosition(positionId: String, quantity: Double, price: Double): Position? {
        val position = positions[positionId] ?: return null
        
        // Calculate new average entry price
        val totalValue = (position.quantity * position.averageEntryPrice) + (quantity * price)
        val newQuantity = position.quantity + quantity
        val newAvgPrice = totalValue / newQuantity
        val newMargin = (newQuantity * newAvgPrice) / position.leverage
        
        val updatedPosition = position.copy(
            quantity = newQuantity,
            averageEntryPrice = newAvgPrice,
            margin = newMargin,
            lastUpdateTime = System.currentTimeMillis()
        )
        
        positions[positionId] = updatedPosition
        updatePositionsList()
        
        return updatedPosition
    }
    
    /**
     * Reduce position size
     */
    fun reducePosition(positionId: String, reduceQuantity: Double, exitPrice: Double): Double? {
        val position = positions[positionId] ?: return null
        
        if (reduceQuantity >= position.quantity) {
            return closePosition(positionId, exitPrice, "Reduce to Zero")
        }
        
        val direction = if (position.side == TradeSide.BUY || position.side == TradeSide.LONG) TradeDirection.LONG else TradeDirection.SHORT
        val priceDiff = if (direction == TradeDirection.LONG) {
            exitPrice - position.averageEntryPrice
        } else {
            position.averageEntryPrice - exitPrice
        }
        
        val partialPnl = priceDiff * reduceQuantity * position.leverage
        val newQuantity = position.quantity - reduceQuantity
        val newMargin = (newQuantity * position.averageEntryPrice) / position.leverage
        
        val updatedPosition = position.copy(
            quantity = newQuantity,
            margin = newMargin,
            realizedPnl = position.realizedPnl + partialPnl,
            lastUpdateTime = System.currentTimeMillis()
        )
        
        positions[positionId] = updatedPosition
        updatePositionsList()
        
        return partialPnl
    }
    
    /**
     * Get position by ID
     */
    fun getPosition(positionId: String): Position? = positions[positionId]
    
    /**
     * Get all positions for a symbol
     */
    fun getPositionsForSymbol(symbol: String): List<Position> {
        return positions.values.filter { it.symbol == symbol }
    }
    
    /**
     * Get all currently open positions.
     * Used by hedge fund execution bridge for cascade risk assessment.
     */
    fun getOpenPositions(): List<Position> {
        return positions.values.toList()
    }
    
    /**
     * Get portfolio summary
     */
    fun getPositionSummary(): PositionSummary {
        val allPositions = positions.values.toList()
        
        return PositionSummary(
            totalPositions = allPositions.size,
            longPositions = allPositions.count { it.side == TradeSide.BUY || it.side == TradeSide.LONG },
            shortPositions = allPositions.count { it.side == TradeSide.SELL || it.side == TradeSide.SHORT },
            totalUnrealizedPnl = allPositions.sumOf { it.unrealizedPnl },
            totalRealizedPnl = allPositions.sumOf { it.realizedPnl },
            totalMargin = allPositions.sumOf { it.margin },
            totalExposure = allPositions.sumOf { it.quantity * it.currentPrice },
            largestPosition = allPositions.maxByOrNull { it.quantity * it.currentPrice },
            mostProfitable = allPositions.maxByOrNull { it.unrealizedPnl },
            mostLosing = allPositions.minByOrNull { it.unrealizedPnl }
        )
    }
    
    /**
     * Close all positions
     */
    suspend fun closeAllPositions(symbol: String? = null): List<Double> {
        val positionsToClose = if (symbol != null) {
            positions.values.filter { it.symbol == symbol }
        } else {
            positions.values.toList()
        }
        
        return positionsToClose.mapNotNull { position ->
            closePosition(position.id, position.currentPrice, "Close All")
        }
    }
    
    private fun updatePositionsList() {
        _positions.value = positions.values.toList()
    }
    
    private fun generatePositionId(symbol: String, side: TradeSide): String {
        return "$symbol-${side.name}-${System.currentTimeMillis()}"
    }
    
    /**
     * Get total portfolio value (margin + unrealized P&L)
     */
    fun getTotalPortfolioValue(): Double {
        return positions.values.sumOf { it.margin + it.unrealizedPnl }
    }
    
    /**
     * Clean up resources
     */
    fun shutdown() {
        scope.cancel()
    }
}
