package com.miwealth.sovereignvantage.core.exchange.tick

/**
 * BINANCE PUBLIC TICK PROVIDER
 * 
 * Sovereign Vantage: Arthur Edition V5.19.241
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * Provides tick data from Binance's PUBLIC API (no keys required).
 * Uses existing BinancePublicPriceFeed for data collection.
 * 
 * Advantages:
 * - No API keys needed
 * - Works immediately
 * - Real spot market data
 * - Perfect for paper trading and DQN training
 * 
 * Data Source:
 * - REST polling every 5 seconds
 * - Real BTC/USDT, ETH/USDT, SOL/USDT, XRP/USDT prices
 * - ~12 ticks per minute per symbol
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */

import com.miwealth.sovereignvantage.core.exchange.BinancePublicPriceFeed
import com.miwealth.sovereignvantage.core.utils.SystemLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Tick provider using Binance's public API.
 * 
 * No authentication required - perfect for getting started.
 */
class BinancePublicTickProvider(
    symbols: List<String> = listOf("BTC/USDT", "ETH/USDT", "SOL/USDT", "XRP/USDT")
) : BaseTickProvider(
    exchangeId = "binance-public",
    symbols = symbols
) {
    companion object {
        private const val TAG = "BinancePublicTickProvider"
    }
    
    private val feed = BinancePublicPriceFeed.getInstance()
    
    override suspend fun connect(): Boolean {
        return try {
            SystemLogger.system("🔌 BUILD #241: Connecting BinancePublicTickProvider for ${symbols.size} symbols")
            feed.start(symbols)
            connected = true
            SystemLogger.system("✅ BUILD #241: BinancePublicTickProvider connected")
            true
        } catch (e: Exception) {
            SystemLogger.error("❌ BUILD #241: BinancePublicTickProvider connection failed: ${e.message}", e)
            trackError()
            connected = false
            false
        }
    }
    
    override fun getTickStream(): Flow<UniversalTick> {
        return feed.priceTicks.map { tick ->
            trackTick()
            
            UniversalTick(
                exchange = "binance",
                symbol = tick.symbol,
                price = tick.last,
                volume = tick.volume24h,
                bid = tick.bid,
                ask = tick.ask,
                timestamp = tick.timestamp,
                source = TickSource.REST_POLL
            ).also {
                SystemLogger.d(TAG, "📊 BUILD #241: Tick ${it.symbol} @ ${it.price} " +
                    "(bid: ${it.bid}, ask: ${it.ask})")
            }
        }
    }
    
    override suspend fun disconnect() {
        SystemLogger.system("🔌 BUILD #241: Disconnecting BinancePublicTickProvider")
        feed.stop()
        connected = false
    }
    
    override fun getTickSource(): TickSource = TickSource.REST_POLL
}
