package com.miwealth.sovereignvantage.core.trading.routing

import com.miwealth.sovereignvantage.core.*
import com.miwealth.sovereignvantage.core.trading.engine.*

/**
 * ROUTABLE EXCHANGE ADAPTER
 * 
 * Sovereign Vantage: Arthur Edition V5.5.94
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * V5.5.94: NEW FILE — Bridge interface enabling SmartOrderRouter to work
 * with BOTH legacy UnifiedExchangeAdapter AND AI-powered AIUnifiedExchangeAdapter.
 * 
 * SmartOrderRouter requires four capabilities beyond the base ExchangeAdapter:
 * - Exchange identity (for routing decisions and fee lookups)
 * - Connection status (skip disconnected exchanges)
 * - Ticker data (for price-based routing)
 * - Order book depth (for liquidity analysis — graceful degradation if null)
 * 
 * Architecture:
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │  ExchangeAdapter (base interface)                                      │
 * │    └── RoutableExchangeAdapter (this interface)                        │
 * │          ├── UnifiedExchangeAdapter (legacy PQC connectors)            │
 * │          ├── AIUnifiedExchangeAdapter (AI Exchange Interface)          │
 * │          └── [Future: DEXAdapter, AggregatorAdapter, etc.]             │
 * │                                                                        │
 * │  SmartOrderRouter accepts RoutableExchangeAdapter                      │
 * │    → Routes to best exchange regardless of adapter origin              │
 * └─────────────────────────────────────────────────────────────────────────┘
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */
interface RoutableExchangeAdapter : ExchangeAdapter {
    
    /**
     * Unique exchange identifier for routing decisions and fee lookups.
     * Examples: "kraken", "binance", "coinbase", "binance_testnet"
     */
    fun getExchangeId(): String
    
    /**
     * Whether this exchange is currently connected and accepting orders.
     * SmartOrderRouter skips disconnected exchanges during route analysis.
     */
    fun isConnected(): Boolean
    
    /**
     * Get current ticker (bid/ask/last) for a symbol.
     * Used by SmartOrderRouter for price-based routing decisions.
     * 
     * @return PriceTick or null if unavailable for this symbol
     */
    suspend fun getTicker(symbol: String): PriceTick?
    
    /**
     * Get order book depth for a symbol.
     * Used by SmartOrderRouter for liquidity analysis and slippage estimation.
     * 
     * Returns core.trading.routing.OrderBook for SmartOrderRouter compatibility.
     * 
     * GRACEFUL DEGRADATION: Returning null is valid — SmartOrderRouter falls back
     * to ticker-price-only routing with zero liquidity score. The exchange will
     * still be considered for routing based on price and fees alone.
     * 
     * @param symbol Trading pair (e.g. "BTC/USDT")
     * @param depth Number of order book levels to fetch (default 20)
     * @return OrderBook or null if order book data unavailable
     */
    suspend fun getOrderBook(symbol: String, depth: Int = 20): OrderBook?
}
