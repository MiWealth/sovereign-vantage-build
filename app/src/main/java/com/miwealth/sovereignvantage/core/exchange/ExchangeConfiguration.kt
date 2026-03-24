package com.miwealth.sovereignvantage.core.exchange

import com.miwealth.sovereignvantage.core.exchange.tick.UniversalTick  // BUILD #254: Added missing import

/**
 * EXCHANGE CONFIGURATION SYSTEM
 * 
 * Sovereign Vantage: Arthur Edition V5.19.244
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * User-controlled exchange connection configuration.
 * 
 * Architecture Philosophy:
 * Users have full sovereignty over which exchanges they connect to,
 * how many exchanges they use, and when those connections are active.
 * The app enables but never controls the user's trading infrastructure.
 * 
 * This is non-custodial architecture at the connectivity layer:
 * - Users provide their own API credentials
 * - Credentials stored encrypted on-device only (Android Keystore)
 * - Users can add/remove exchanges at will
 * - Users control connection state (connect/disconnect)
 * 
 * Regulatory Compliance:
 * By making exchange connectivity user-controlled, the app remains
 * clearly a "software tool" under MiCA, GENIUS Act, and CLARITY Act.
 * Users establish their own relationships with exchanges.
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */

/**
 * Represents a single exchange connection configuration.
 * 
 * Each instance represents one exchange the user wants to connect to.
 * Users can have multiple instances for different exchanges or even
 * multiple connections to the same exchange (e.g., spot + futures).
 */
data class ExchangeConnectionConfig(
    /** Unique identifier for this connection (e.g., "binance-spot-1") */
    val connectionId: String,
    
    /** Exchange identifier (e.g., "binance", "coinbase", "kraken") */
    val exchangeId: String,
    
    /** Human-readable name (e.g., "Binance Spot", "Coinbase Sandbox") */
    val displayName: String,
    
    /** Provider type determines which tick provider class to instantiate */
    val providerType: ExchangeProviderType,
    
    /** Symbols this connection should track */
    val symbols: List<String>,
    
    /** API credentials (encrypted at rest, optional for public feeds) */
    val credentials: ExchangeCredentialsConfig? = null,
    
    /** Connection settings */
    val settings: ConnectionSettings = ConnectionSettings(),
    
    /** Whether this connection is currently enabled by user */
    val enabled: Boolean = true,
    
    /** User-assigned priority (higher = preferred for conflicts) */
    val priority: Int = 0
)

/**
 * Types of exchange providers available.
 * 
 * Each type maps to a specific tick provider implementation.
 */
enum class ExchangeProviderType {
    /** Binance public REST API (no auth, free, ~0.2 Hz per symbol) */
    BINANCE_PUBLIC,
    
    /** Binance public WebSocket (no auth, free, ~1-10 Hz per symbol) */
    BINANCE_WEBSOCKET,
    
    /** Coinbase Sandbox testnet (requires auth, free, ~1-2 Hz per symbol) */
    COINBASE_SANDBOX,
    
    /** Coinbase Production (requires auth, paid account, ~1-2 Hz per symbol) */
    COINBASE_PRODUCTION,
    
    /** Kraken Futures Demo testnet (requires auth, free, ~1-5 Hz per symbol) */
    KRAKEN_FUTURES_DEMO,
    
    /** Kraken Futures Production (requires auth, paid account, ~1-5 Hz per symbol) */
    KRAKEN_FUTURES_PRODUCTION,
    
    /** Kraken Spot (requires auth, paid account, ~0.5-2 Hz per symbol) */
    KRAKEN_SPOT;
    
    /**
     * Does this provider require authentication?
     */
    val requiresAuth: Boolean
        get() = when (this) {
            BINANCE_PUBLIC, BINANCE_WEBSOCKET -> false
            else -> true
        }
    
    /**
     * Is this a testnet/sandbox environment (fake money)?
     */
    val isTestnet: Boolean
        get() = when (this) {
            COINBASE_SANDBOX, KRAKEN_FUTURES_DEMO -> true
            else -> false
        }
    
    /**
     * Expected tick rate range (ticks per second per symbol).
     */
    val expectedTickRate: TickRateRange
        get() = when (this) {
            BINANCE_PUBLIC -> TickRateRange(min = 0.2, max = 0.2)
            BINANCE_WEBSOCKET -> TickRateRange(min = 1.0, max = 10.0)
            COINBASE_SANDBOX, COINBASE_PRODUCTION -> TickRateRange(min = 1.0, max = 2.0)
            KRAKEN_FUTURES_DEMO, KRAKEN_FUTURES_PRODUCTION -> TickRateRange(min = 1.0, max = 5.0)
            KRAKEN_SPOT -> TickRateRange(min = 0.5, max = 2.0)
        }
}

/**
 * Tick rate range (ticks per second).
 */
data class TickRateRange(
    val min: Double,
    val max: Double
)

/**
 * Exchange API credentials.
 * 
 * SECURITY NOTE: These are stored encrypted using Android Keystore.
 * The app never transmits credentials to any MiWealth server.
 * Credentials only flow from user device → exchange API.
 */
data class ExchangeCredentialsConfig(
    /** API key (public identifier) */
    val apiKey: String,
    
    /** API secret (used to sign requests) */
    val apiSecret: String,
    
    /** Optional passphrase (Coinbase requires this) */
    val passphrase: String? = null,
    
    /** Optional subaccount (for exchanges supporting multiple accounts) */
    val subaccount: String? = null
) {
    /**
     * Validate credentials are not empty.
     */
    fun isValid(): Boolean {
        return apiKey.isNotBlank() && apiSecret.isNotBlank()
    }
}

/**
 * Connection settings for an exchange.
 */
data class ConnectionSettings(
    /** Use WebSocket if available (vs REST polling) */
    val useWebSocket: Boolean = true,
    
    /** Automatically reconnect on connection loss */
    val autoReconnect: Boolean = true,
    
    /** Maximum reconnect attempts before giving up */
    val maxReconnectAttempts: Int = 5,
    
    /** Delay between reconnect attempts (milliseconds) */
    val reconnectDelayMs: Long = 30_000L,
    
    /** Maximum number of errors before disconnecting */
    val errorThreshold: Int = 10,
    
    /** Buffer size for tick storage (ticks) */
    val tickBufferSize: Int = 1000
)

/**
 * Status of an exchange connection.
 * 
 * Tracks real-time connection health and statistics.
 */
data class ExchangeConnectionStatus(
    val connectionId: String,
    val exchangeId: String,
    val providerType: ExchangeProviderType,
    val connected: Boolean,
    val symbols: List<String>,
    val ticksReceived: Long,
    val errorsEncountered: Int,
    val lastTickTimestamp: Long,
    val averageTickRate: Double,  // Ticks per second
    val reconnectAttempts: Int,
    val uptimeMs: Long
)

/**
 * User's complete multi-exchange configuration.
 * 
 * This represents the user's entire trading infrastructure:
 * which exchanges they're connected to, which symbols they track,
 * and how those connections are prioritized.
 */
data class MultiExchangeConfiguration(
    /** All configured exchanges */
    val exchanges: List<ExchangeConnectionConfig>,
    
    /** Arbitrage detection settings */
    val arbitrageSettings: ArbitrageSettings = ArbitrageSettings(),
    
    /** Strategy preferences for multi-exchange scenarios */
    val strategyPreferences: StrategyPreferences = StrategyPreferences()
) {
    /**
     * Get only enabled exchanges.
     */
    val activeExchanges: List<ExchangeConnectionConfig>
        get() = exchanges.filter { it.enabled }
    
    /**
     * Get exchanges sorted by priority (highest first).
     */
    val exchangesByPriority: List<ExchangeConnectionConfig>
        get() = exchanges.sortedByDescending { it.priority }
    
    /**
     * Check if arbitrage is possible (requires 2+ active exchanges).
     */
    val arbitragePossible: Boolean
        get() = activeExchanges.size >= 2
}

/**
 * Arbitrage detection and execution settings.
 */
data class ArbitrageSettings(
    /** Enable arbitrage opportunity detection */
    val enabled: Boolean = true,
    
    /** Minimum price spread to trigger alert (dollars) */
    val minSpreadUsd: Double = 30.0,
    
    /** Minimum spread percentage (0.5 = 0.5%) */
    val minSpreadPercent: Double = 0.5,
    
    /** Execution mode for arbitrage opportunities */
    val executionMode: ArbitrageExecutionMode = ArbitrageExecutionMode.SIGNAL_ONLY,
    
    /** Maximum exposure per exchange (percentage of portfolio) */
    val maxExposurePerExchangePercent: Double = 25.0,
    
    /** Maximum simultaneous arbitrage positions */
    val maxSimultaneousPositions: Int = 3
)

/**
 * How arbitrage opportunities should be handled.
 */
enum class ArbitrageExecutionMode {
    /** Only notify user, no automatic action */
    SIGNAL_ONLY,
    
    /** Board votes, user confirms before execution */
    BOARD_WITH_CONFIRMATION,
    
    /** Board executes automatically if consensus reached */
    FULLY_AUTONOMOUS
}

/**
 * Strategy preferences for multi-exchange scenarios.
 */
data class StrategyPreferences(
    /** Prefer liquidity (choose exchange with highest volume) */
    val preferLiquidity: Boolean = true,
    
    /** Prefer low latency (choose fastest exchange) */
    val preferLowLatency: Boolean = false,
    
    /** Prefer low fees (choose cheapest exchange) */
    val preferLowFees: Boolean = true,
    
    /** Allow cross-exchange hedging strategies */
    val allowCrossExchangeHedging: Boolean = true
)

/**
 * Enriched tick with cross-exchange context.
 * 
 * This extends UniversalTick with additional metadata that's only
 * available when multiple exchanges are connected simultaneously.
 */
data class EnrichedTick(
    /** The base tick data from the exchange */
    val baseTick: UniversalTick,
    
    /** Which exchange this tick came from */
    val exchangeId: String,
    
    /** Connection ID (in case user has multiple connections to same exchange) */
    val connectionId: String,
    
    /** How much this price differs from best price across all exchanges (dollars) */
    val spreadFromBest: Double,
    
    /** Percentage spread from best price (0.5 = 0.5% difference) */
    val spreadFromBestPercent: Double,
    
    /** Exchange's liquidity rank (1 = highest volume, 2 = second highest, etc.) */
    val exchangeRank: Int,
    
    /** Latency from exchange to app (milliseconds) */
    val latencyMs: Long,
    
    /** Whether this is the best price across all exchanges */
    val isBestPrice: Boolean
) {
    /**
     * Is this an arbitrage opportunity?
     * (Price significantly different from best price on other exchanges)
     */
    fun isArbitrageOpportunity(settings: ArbitrageSettings): Boolean {
        return spreadFromBest >= settings.minSpreadUsd &&
               spreadFromBestPercent >= settings.minSpreadPercent
    }
}
