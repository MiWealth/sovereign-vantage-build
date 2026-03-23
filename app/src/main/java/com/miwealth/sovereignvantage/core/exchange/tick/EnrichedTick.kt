package com.miwealth.sovereignvantage.core.exchange.tick

/**
 * ENRICHED TICK - MULTI-EXCHANGE METADATA
 * 
 * Sovereign Vantage: Arthur Edition V5.19.244
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * Purpose:
 * Wraps a UniversalTick with additional metadata about which exchange it came from
 * and how it compares to prices on other exchanges. This enables:
 * 
 * - Cross-exchange arbitrage detection (price differences between exchanges)
 * - Liquidity analysis (which exchange has the deepest order book)
 * - Latency monitoring (how long did the tick take to arrive)
 * - Best price tracking (is this the best price across all connected exchanges)
 * 
 * Used by:
 * - DQN for learning cross-exchange price dynamics
 * - AI Board for making exchange-aware trading decisions
 * - Arbitrage strategies for finding profitable spreads
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */

/**
 * EnrichedTick wraps a UniversalTick with exchange context.
 * 
 * The base tick contains the price, volume, bid/ask from one exchange.
 * The enrichment adds cross-exchange comparison data.
 * 
 * Example:
 * If Binance shows BTC at $68,500 and Kraken shows BTC at $68,550,
 * the Binance tick would have spreadFromBest = 0.0 (it's the best price)
 * and the Kraken tick would have spreadFromBest = 50.0 (it's $50 higher).
 */
data class EnrichedTick(
    /**
     * The original tick from the exchange.
     * Contains: symbol, price, volume, bid, ask, timestamp, source
     */
    val baseTick: UniversalTick,
    
    /**
     * Which exchange this tick came from.
     * Examples: "binance", "kraken", "coinbase"
     * 
     * This allows the DQN and board to learn that Binance often leads
     * price movements, or that Kraken has deeper liquidity for certain pairs.
     */
    val exchangeId: String,
    
    /**
     * How far this price is from the best price across ALL exchanges.
     * 
     * Calculated as: abs(this.price - best_price_across_all_exchanges)
     * 
     * Use cases:
     * - Arbitrage detection: if spreadFromBest > $30, opportunity exists
     * - Price validation: if spreadFromBest > $100, might be a bad tick
     * - Exchange ranking: exchanges with lower spreads are better priced
     * 
     * Example:
     * Binance: BTC @ $68,500 → spreadFromBest = 0.0 (best price)
     * Kraken: BTC @ $68,550 → spreadFromBest = 50.0 ($50 above best)
     * Gate.io: BTC @ $68,525 → spreadFromBest = 25.0 ($25 above best)
     */
    val spreadFromBest: Double,
    
    /**
     * Liquidity rank of this exchange for this symbol.
     * 
     * 1 = Highest liquidity (deepest order book, most volume)
     * 2 = Second highest
     * 3 = Third highest, etc.
     * 
     * The board can use this to prefer executing on high-liquidity exchanges
     * where large orders won't move the market as much.
     * 
     * Example rankings for BTC/USDT:
     * 1. Binance (typically $500M+ daily volume)
     * 2. Coinbase (typically $200M+ daily volume)
     * 3. Kraken (typically $100M+ daily volume)
     */
    val exchangeRank: Int,
    
    /**
     * How long it took for this tick to arrive from the exchange.
     * 
     * Calculated as: (time_received_in_app - tick.timestamp)
     * 
     * Lower latency means fresher data. If one exchange consistently has
     * 100ms latency and another has 500ms, the DQN learns to trust the
     * lower-latency exchange for time-sensitive signals.
     * 
     * Typical values:
     * - WebSocket: 50-200ms (fast, real-time)
     * - REST polling: 0-5000ms (slow, depends on polling interval)
     */
    val latencyMs: Long,
    
    /**
     * Is this the best bid price across all exchanges?
     * 
     * If you're buying, you want the lowest ask.
     * If you're selling, you want the highest bid.
     * 
     * This flag lets the execution engine know if this exchange offers
     * the best price for the intended trade direction.
     */
    val isBestBid: Boolean = false,
    
    /**
     * Is this the best ask price across all exchanges?
     */
    val isBestAsk: Boolean = false
) {
    /**
     * Convenience access to underlying tick properties.
     * Allows code to use enrichedTick.symbol instead of enrichedTick.baseTick.symbol
     */
    val symbol: String get() = baseTick.symbol
    val price: Double get() = baseTick.price
    val bid: Double get() = baseTick.bid
    val ask: Double get() = baseTick.ask
    val volume: Double get() = baseTick.volume
    val timestamp: Long get() = baseTick.timestamp
    val source: TickSource get() = baseTick.source
    
    /**
     * Calculate the potential arbitrage profit if we bought on the best exchange
     * and sold on this exchange (or vice versa).
     * 
     * Returns positive number if this exchange offers arbitrage opportunity.
     * Returns zero if no arbitrage (this IS the best price).
     * 
     * Example:
     * Best bid across all exchanges: $68,550 (Kraken)
     * This exchange bid: $68,500 (Binance)
     * Arbitrage profit = 0.0 (no opportunity, would lose money)
     * 
     * Best ask across all exchanges: $68,500 (Binance)
     * This exchange ask: $68,550 (Kraken)
     * Arbitrage profit = 0.0 (no opportunity, would lose money)
     * 
     * But if we flip it:
     * Buy on Binance @ $68,500, sell on Kraken @ $68,550 = $50 profit
     */
    fun calculateArbitragePotential(bestBidAcrossExchanges: Double, bestAskAcrossExchanges: Double): Double {
        // If we can buy here cheap and sell elsewhere high, that's profit
        val buyHereSellElsewhere = bestBidAcrossExchanges - this.ask
        
        // If we can buy elsewhere cheap and sell here high, that's also profit
        val buyElsewhereSellHere = this.bid - bestAskAcrossExchanges
        
        // Return the better of the two opportunities
        return maxOf(buyHereSellElsewhere, buyElsewhereSellHere, 0.0)
    }
    
    /**
     * Is this tick stale (older than threshold)?
     * 
     * Stale ticks can happen when:
     * - Network delays
     * - Exchange API slowdowns
     * - Our polling interval is too slow
     * 
     * We generally don't want to trade on stale data because the price
     * might have moved significantly since this tick was generated.
     */
    fun isStale(thresholdMs: Long = 5000): Boolean {
        val age = System.currentTimeMillis() - timestamp
        return age > thresholdMs
    }
    
    /**
     * Convert to a human-readable string for logging.
     */
    override fun toString(): String {
        return "EnrichedTick(exchange=$exchangeId, symbol=$symbol, price=$price, " +
            "spread=$spreadFromBest, rank=$exchangeRank, latency=${latencyMs}ms, " +
            "bestBid=$isBestBid, bestAsk=$isBestAsk)"
    }
}

/**
 * Exchange connection configuration.
 * Holds everything needed to connect to one exchange.
 * 
 * Used by MultiExchangeManager to create tick providers.
 */
data class ExchangeConnectionConfig(
    /**
     * Unique identifier for this exchange in the app.
     * Examples: "binance-1", "kraken-futures-1", "coinbase-2"
     */
    val exchangeId: String,
    
    /**
     * Which tick provider implementation to use.
     * 
     * Options:
     * - "binance-public" → BinancePublicTickProvider (REST, no auth)
     * - "kraken-demo" → KrakenFuturesDemoTickProvider (WebSocket, testnet)
     * - "coinbase-sandbox" → CoinbaseSandboxTickProvider (WebSocket, testnet)
     * - "binance-websocket" → BinanceWebSocketTickProvider (WebSocket, real-time)
     */
    val providerType: String,
    
    /**
     * Which symbols to track on this exchange.
     * 
     * User can choose different symbols for different exchanges.
     * For example:
     * - Binance: Track BTC/USDT, ETH/USDT (USDT pairs)
     * - Kraken: Track BTC/USD, ETH/USD (USD pairs)
     */
    val symbols: List<String>,
    
    /**
     * API key for authenticated connections.
     * Null for public (no-auth) connections.
     * 
     * This key is encrypted before storage using Android Keystore.
     * It never leaves the user's device.
     */
    val apiKey: String? = null,
    
    /**
     * API secret for authenticated connections.
     * Null for public connections.
     * 
     * Also encrypted using Android Keystore.
     */
    val apiSecret: String? = null,
    
    /**
     * API passphrase (required by some exchanges like Coinbase).
     * Null for exchanges that don't use passphrases.
     */
    val passphrase: String? = null,
    
    /**
     * Should this exchange auto-reconnect on disconnection?
     * 
     * Recommended: true for live trading, false for testing.
     */
    val autoReconnect: Boolean = true,
    
    /**
     * Maximum number of reconnection attempts before giving up.
     * 
     * Prevents infinite reconnection loops if an exchange is permanently down.
     */
    val maxReconnectAttempts: Int = 5,
    
    /**
     * Delay between reconnection attempts (milliseconds).
     * 
     * Prevents hammering the exchange API with rapid reconnect attempts.
     */
    val reconnectDelayMs: Long = 30_000L,
    
    /**
     * Is this exchange currently enabled by the user?
     * 
     * User can disable an exchange without removing it from configuration.
     * Disabled exchanges don't connect or consume resources.
     */
    val enabled: Boolean = true,
    
    /**
     * Priority for this exchange (1 = highest priority).
     * 
     * Used when multiple exchanges offer the same price.
     * The board will prefer executing on the highest-priority exchange.
     */
    val priority: Int = 1
)
