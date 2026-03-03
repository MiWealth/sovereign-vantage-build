package com.miwealth.sovereignvantage.core

import android.content.Context
import com.miwealth.sovereignvantage.core.wallet.TradeLedger as CoreTradeLedger

/**
 * Core Models - Consolidated types used across the application
 * 
 * © 2025 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 */

// Re-export common types from their actual locations
// NOTE: typealias Asset removed — conflicts with data class Asset below.
// Use com.miwealth.sovereignvantage.core.trading.assets.AssetClass directly where needed.
typealias TradeLedger = CoreTradeLedger

/**
 * Asset Type enumeration — canonical definition (consolidated from 7 files).
 * All other files should import from core.AssetType.
 */
enum class AssetType {
    // Core types
    CRYPTO,
    STOCK,
    ETF,
    FOREX,
    COMMODITY,
    BOND,
    DERIVATIVE,
    FUTURES,
    NFT,
    DEFI,
    TOKEN,
    STABLECOIN,
    INDICES,
    OPTIONS,
    SWAPS,
    CFDS,
    // Plural aliases (used by AssetClass, StockAsset, BondAsset, DerivativeAsset)
    STOCKS,
    BONDS,
    ETFS,
    COMMODITIES,
    DERIVATIVES,
    // Crypto subtypes (used by AssetClass)
    CRYPTO_SPOT,
    CRYPTO_FUTURES
}

/**
 * Trade side (direction) — canonical definition (consolidated from 4 files).
 */
enum class TradeSide {
    BUY,
    SELL,
    LONG,
    SHORT,
    DEPOSIT,
    WITHDRAWAL
}

/**
 * Simple asset representation — canonical definition.
 */
data class Asset(val symbol: String, val type: AssetType)

/**
 * Order representation for trading
 */
data class Order(
    val id: String,
    val symbol: String,
    val side: TradeSide,
    val type: OrderType,
    val price: Double,
    val quantity: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val status: OrderStatus = OrderStatus.PENDING
)

enum class OrderType {
    MARKET,
    LIMIT,
    STOP_LOSS,
    STOP_LIMIT,
    TRAILING_STOP,
    TAKE_PROFIT,
    TAKE_PROFIT_LIMIT
}

enum class OrderStatus {
    PENDING,
    OPEN,
    PARTIALLY_FILLED,
    FILLED,
    CANCELLED,
    REJECTED,
    EXPIRED
}

/**
 * Trade result from executed order
 */
data class TradeResult(
    val orderId: String,
    val symbol: String,
    val side: TradeSide,
    val executedPrice: Double,
    val executedQuantity: Double,
    val fee: Double,
    val pnl: Double = 0.0,
    val pnlPercent: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
    val success: Boolean = true,
    val message: String = ""
)

/**
 * Portfolio Service - Tracks positions and performance
 */
class PortfolioService(
    private val context: Context,
    private val tradeLedger: CoreTradeLedger
) {
    data class Position(
        val symbol: String,
        val quantity: Double,
        val averageCost: Double,
        val currentPrice: Double,
        val unrealizedPnl: Double,
        val unrealizedPnlPercent: Double
    )
    
    data class PortfolioSummary(
        val totalValue: Double,
        val cashBalance: Double,
        val investedValue: Double,
        val unrealizedPnl: Double,
        val realizedPnl: Double,
        val totalPnl: Double,
        val totalPnlPercent: Double,
        val positions: List<Position>
    )
    
    private var cashBalance: Double = 0.0
    private val positions = mutableMapOf<String, Position>()
    
    fun getPortfolioSummary(): PortfolioSummary {
        val investedValue = positions.values.sumOf { it.quantity * it.averageCost }
        val currentValue = positions.values.sumOf { it.quantity * it.currentPrice }
        val unrealizedPnl = currentValue - investedValue
        
        return PortfolioSummary(
            totalValue = cashBalance + currentValue,
            cashBalance = cashBalance,
            investedValue = investedValue,
            unrealizedPnl = unrealizedPnl,
            realizedPnl = tradeLedger.getTotalRealizedPnl(),
            totalPnl = unrealizedPnl + tradeLedger.getTotalRealizedPnl(),
            totalPnlPercent = if (investedValue > 0) (unrealizedPnl / investedValue) * 100 else 0.0,
            positions = positions.values.toList()
        )
    }
    
    fun updatePosition(symbol: String, quantity: Double, price: Double) {
        val existing = positions[symbol]
        if (existing != null) {
            val newQuantity = existing.quantity + quantity
            val newAvgCost = if (newQuantity > 0) {
                (existing.averageCost * existing.quantity + price * quantity) / newQuantity
            } else 0.0
            
            positions[symbol] = existing.copy(
                quantity = newQuantity,
                averageCost = newAvgCost,
                currentPrice = price,
                unrealizedPnl = (price - newAvgCost) * newQuantity,
                unrealizedPnlPercent = if (newAvgCost > 0) ((price - newAvgCost) / newAvgCost) * 100 else 0.0
            )
        } else {
            positions[symbol] = Position(
                symbol = symbol,
                quantity = quantity,
                averageCost = price,
                currentPrice = price,
                unrealizedPnl = 0.0,
                unrealizedPnlPercent = 0.0
            )
        }
    }
    
    fun updatePrice(symbol: String, price: Double) {
        positions[symbol]?.let { pos ->
            positions[symbol] = pos.copy(
                currentPrice = price,
                unrealizedPnl = (price - pos.averageCost) * pos.quantity,
                unrealizedPnlPercent = if (pos.averageCost > 0) ((price - pos.averageCost) / pos.averageCost) * 100 else 0.0
            )
        }
    }
    
    fun setCashBalance(amount: Double) {
        cashBalance = amount
    }
    
    fun getPosition(symbol: String): Position? = positions[symbol]
    
    fun getAllPositions(): List<Position> = positions.values.toList()
}

// Extension function for TradeLedger
private fun CoreTradeLedger.getTotalRealizedPnl(): Double {
    // This would normally sum up all realized P&L from closed trades
    return 0.0 // Placeholder - implement based on TradeLedger structure
}

/**
 * Real-time price tick from exchange
 */
data class PriceTick(
    val symbol: String,
    val bid: Double = 0.0,
    val ask: Double = 0.0,
    val last: Double,
    val volume: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
    val exchange: String = "",
    // Optional 24h fields — backward compatible with defaults
    val volume24h: Double? = null,
    val high24h: Double? = null,
    val low24h: Double? = null,
    val change24h: Double? = null,
    val changePercent24h: Double? = null,
    val averagePrice: Double? = null
) {
    val spread: Double get() = ask - bid
    val spreadPercent: Double get() = if (bid > 0) (spread / bid) * 100 else 0.0
    val mid: Double get() = (bid + ask) / 2
    // Safe accessors with fallbacks
    val safeVolume24h: Double get() = volume24h ?: volume
    val safeHigh24h: Double get() = high24h ?: last
    val safeLow24h: Double get() = low24h ?: last
    val safeChange24h: Double get() = change24h ?: 0.0
    val safeChangePercent24h: Double get() = changePercent24h ?: 0.0
    val safeAveragePrice: Double get() = averagePrice ?: last
}

/**
 * OHLCV Bar data for charting and analysis
 */
data class OHLCVBar(
    val symbol: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val timestamp: Long,
    val interval: String = "1m",  // 1m, 5m, 15m, 1h, 4h, 1d, 1w
    val trades: Int = 0           // Number of trades in this bar (exchange-provided)
) {
    val isBullish: Boolean get() = close > open
    val isBearish: Boolean get() = close < open
    val bodySize: Double get() = kotlin.math.abs(close - open)
    val range: Double get() = high - low
    val upperWick: Double get() = high - kotlin.math.max(open, close)
    val lowerWick: Double get() = kotlin.math.min(open, close) - low
}
