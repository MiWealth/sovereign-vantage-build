package com.miwealth.sovereignvantage.data.models

/**
 * CANONICAL ORDER BOOK MODELS
 * 
 * Sovereign Vantage: Arthur Edition V5.17.0
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * V5.17.0: Consolidated duplicate OrderBook/OrderBookLevel definitions from:
 * - core.exchange.UnifiedExchangeConnector (rich version with computed properties)
 * - core.trading.routing.SmartOrderRouter (lightweight version)
 * 
 * Both packages now import from this single canonical source.
 * Note: core.trading.engine.MatchingEngine.OrderBook is intentionally separate —
 * it's a full matching engine class, not a data snapshot.
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */

/**
 * Single price level in an order book (bid or ask).
 */


data class OrderBookLevel(
    val price: Double,
    val quantity: Double,
    val orderCount: Int = 1
)

/**
 * Full order book snapshot from an exchange.
 * 
 * Contains bid/ask levels with computed properties for spread analysis,
 * depth calculation, and liquidity assessment.
 * 
 * @param symbol Trading pair (e.g. "BTC/USDT")
 * @param exchange Exchange identifier (e.g. "kraken", "binance") — defaults to "" for routing contexts
 * @param bids Buy orders sorted by price descending (best bid first)
 * @param asks Sell orders sorted by price ascending (best ask first)
 * @param timestamp Unix timestamp (ms) of snapshot
 * @param sequenceId Exchange sequence number for ordering — defaults to 0 for routing contexts
 */
data class OrderBook(
    val symbol: String,
    val exchange: String = "",
    val bids: List<OrderBookLevel>,
    val asks: List<OrderBookLevel>,
    val timestamp: Long,
    val sequenceId: Long = 0
) {
    /** Best bid price (highest buy order) */
    val bestBid: Double get() = bids.firstOrNull()?.price ?: 0.0
    
    /** Best ask price (lowest sell order) */
    val bestAsk: Double get() = asks.firstOrNull()?.price ?: 0.0
    
    /** Spread in absolute terms */
    val spread: Double get() = bestAsk - bestBid
    
    /** Spread as percentage of best bid */
    val spreadPercent: Double get() = if (bestBid > 0) (spread / bestBid) * 100 else 0.0
    
    /** Mid price between best bid and ask */
    val midPrice: Double get() = (bestBid + bestAsk) / 2
    
    /** Total bid depth (notional value) for top N levels */
    fun getBidDepth(levels: Int = 10): Double =
        bids.take(levels).sumOf { it.price * it.quantity }
    
    /** Total ask depth (notional value) for top N levels */
    fun getAskDepth(levels: Int = 10): Double =
        asks.take(levels).sumOf { it.price * it.quantity }
    
    /** Total depth (both sides) for top N levels */
    fun getTotalDepth(levels: Int = 10): Double =
        getBidDepth(levels) + getAskDepth(levels)
}
