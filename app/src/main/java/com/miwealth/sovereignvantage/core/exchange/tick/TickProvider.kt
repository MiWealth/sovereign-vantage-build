package com.miwealth.sovereignvantage.core.exchange.tick

/**
 * TICK PROVIDER INTERFACE
 * 
 * Sovereign Vantage: Arthur Edition V5.19.241
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * Universal interface for ANY exchange to provide tick data.
 * Implementations exist for:
 * - Binance Public (REST polling, no keys)
 * - Coinbase Sandbox (WebSocket, testnet)
 * - Kraken Futures Demo (WebSocket, testnet)
 * 
 * Easy to add new exchanges - just implement this interface.
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */

import kotlinx.coroutines.flow.Flow

/**
 * Universal interface for exchange tick providers.
 * 
 * Any exchange can implement this to integrate with Sovereign Vantage.
 */
interface TickProvider {
    /**
     * Exchange identifier (e.g., "binance", "kraken", "coinbase").
     */
    val exchangeId: String
    
    /**
     * Symbols this provider tracks.
     */
    val symbols: List<String>
    
    /**
     * Connect to the exchange and start receiving ticks.
     * 
     * @return true if connection successful, false otherwise
     */
    suspend fun connect(): Boolean
    
    /**
     * Get the tick stream.
     * Emits UniversalTick objects as they arrive.
     */
    fun getTickStream(): Flow<UniversalTick>
    
    /**
     * Check if currently connected.
     */
    fun isConnected(): Boolean
    
    /**
     * Disconnect from the exchange.
     */
    suspend fun disconnect()
    
    /**
     * Get provider status/health info.
     */
    fun getStatus(): ProviderStatus
}

/**
 * Provider status information.
 */
data class ProviderStatus(
    val exchangeId: String,
    val connected: Boolean,
    val symbols: List<String>,
    val ticksReceived: Long,
    val lastTickTime: Long,
    val errors: Int,
    val source: TickSource
)

/**
 * Abstract base class for tick providers.
 * Implements common functionality.
 */
abstract class BaseTickProvider(
    override val exchangeId: String,
    override val symbols: List<String>
) : TickProvider {
    
    protected var connected: Boolean = false
    protected var ticksReceived: Long = 0
    protected var errorCount: Int = 0
    protected var lastTickTimestamp: Long = 0
    
    override fun isConnected(): Boolean = connected
    
    override fun getStatus(): ProviderStatus {
        return ProviderStatus(
            exchangeId = exchangeId,
            connected = connected,
            symbols = symbols,
            ticksReceived = ticksReceived,
            lastTickTime = lastTickTimestamp,
            errors = errorCount,
            source = getTickSource()
        )
    }
    
    /**
     * Track a received tick (for stats).
     */
    protected fun trackTick() {
        ticksReceived++
        lastTickTimestamp = System.currentTimeMillis()
    }
    
    /**
     * Track an error (for stats).
     */
    protected fun trackError() {
        errorCount++
    }
    
    /**
     * Get the tick source type for this provider.
     */
    protected abstract fun getTickSource(): TickSource
}
