package com.miwealth.sovereignvantage.core.trading

import com.miwealth.sovereignvantage.core.ml.TickData
import com.miwealth.sovereignvantage.core.utils.SystemLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.LinkedList

/**
 * Rolling Tick Window (Build #242)
 * 
 * Maintains a rolling window of recent ticks per symbol.
 * Used by the AI Board for temporal context during analysis.
 * 
 * IMPORTANT: This is a CONTEXT buffer, not a LEARNING buffer.
 * - Learning happens real-time in RealtimeDQNLearner (online)
 * - This window provides history for board decisions (5 min of ticks)
 * 
 * Why separate?
 * - DQN learns from each tick as it arrives (immediate update)
 * - But board needs 5+ minutes of context to see patterns
 * - So: ticks feed both systems (real-time learning + context buffer)
 * 
 * Mike's insight: "Buffer sufficient data to permit real-time function."
 * Answer: Yes. We buffer context. But learning happens in real-time.
 */
class RollingTickWindow(
    private val windowSize: Int = 300  // 300 ticks = ~25 minutes if polls every 5 seconds
) {
    
    // Per-symbol tick buffers (thread-safe)
    private val buffers = ConcurrentHashMap<String, LinkedList<TickData>>()
    
    // Timestamps for when each symbol was last added
    private val lastUpdateTimes = ConcurrentHashMap<String, Long>()
    
    // Statistics per symbol
    private val statistics = ConcurrentHashMap<String, WindowStatistics>()
    
    /**
     * Add a tick to the rolling window.
     * Old ticks automatically drop off when buffer reaches max size.
     * 
     * @param symbol Trading pair (e.g., "BTC/USDT")
     * @param timestamp Unix milliseconds
     * @param price Current price
     * @param bid Best bid
     * @param ask Best ask
     * @param volume Trade volume
     */
    fun addTick(
        symbol: String,
        timestamp: Long,
        price: Double,
        bid: Double,
        ask: Double,
        volume: Double
    ) {
        try {
            val buffer = buffers.getOrPut(symbol) { LinkedList() }
            
            // Create tick data
            val tick = TickData(
                timestamp = timestamp,
                price = price,
                bid = bid,
                ask = ask,
                volume = volume
            )
            
            // Add to buffer
            buffer.addLast(tick)
            
            // Remove oldest tick if buffer exceeds window size
            while (buffer.size > windowSize) {
                buffer.removeFirst()
            }
            
            // Update timestamp
            lastUpdateTimes[symbol] = timestamp
            
            // Update statistics
            updateStatistics(symbol, buffer)
            
        } catch (e: Exception) {
            SystemLogger.error("❌ BUILD #242 TICK WINDOW ERROR: ${e.message}")
        }
    }
    
    /**
     * Get the complete tick history for a symbol.
     * Returns from oldest to newest.
     * 
     * @param symbol Trading pair
     * @return List of ticks (empty if no history)
     */
    fun getContext(symbol: String): List<TickData> {
        return buffers[symbol]?.toList() ?: emptyList()
    }
    
    /**
     * Get the last N ticks for a symbol.
     * 
     * @param symbol Trading pair
     * @param count Number of ticks to retrieve
     * @return List of most recent ticks (newest first)
     */
    fun getRecentTicks(symbol: String, count: Int = 20): List<TickData> {
        val buffer = buffers[symbol] ?: return emptyList()
        return buffer.takeLast(count).reversed()
    }
    
    /**
     * Get the single most recent tick.
     * 
     * @param symbol Trading pair
     * @return Latest tick or null if no data
     */
    fun getLatestTick(symbol: String): TickData? {
        return buffers[symbol]?.lastOrNull()
    }
    
    /**
     * Check if we have enough data to analyze.
     * The board needs at least 20 ticks before making decisions.
     * That's roughly 100 seconds of data (every ~5 seconds).
     * 
     * @param symbol Trading pair
     * @param minTicks Minimum number of ticks required (default 20)
     * @return True if buffer has at least minTicks
     */
    fun hasEnoughData(symbol: String, minTicks: Int = 20): Boolean {
        return (buffers[symbol]?.size ?: 0) >= minTicks
    }
    
    /**
     * Get the current buffer size for a symbol.
     * 
     * @param symbol Trading pair
     * @return Number of ticks in buffer
     */
    fun getBufferSize(symbol: String): Int {
        return buffers[symbol]?.size ?: 0
    }
    
    /**
     * Get statistics about the tick window.
     * Used for diagnostics and board analysis.
     * 
     * @param symbol Trading pair
     * @return Statistics snapshot
     */
    fun getStatistics(symbol: String): WindowStatistics {
        return statistics[symbol] ?: WindowStatistics()
    }
    
    /**
     * Get all symbols with data.
     */
    fun getTrackedSymbols(): Set<String> {
        return buffers.keys
    }
    
    /**
     * Clear all data for a symbol.
     */
    fun clearSymbol(symbol: String) {
        buffers.remove(symbol)
        lastUpdateTimes.remove(symbol)
        statistics.remove(symbol)
    }
    
    /**
     * Clear all data.
     */
    fun clearAll() {
        buffers.clear()
        lastUpdateTimes.clear()
        statistics.clear()
    }
    
    /**
     * Update rolling statistics for a symbol.
     * Calculates high/low/average price, bid-ask spread, volatility, etc.
     */
    private fun updateStatistics(symbol: String, buffer: LinkedList<TickData>) {
        if (buffer.isEmpty()) return
        
        val prices = buffer.map { it.price }
        val bids = buffer.map { it.bid }
        val asks = buffer.map { it.ask }
        val volumes = buffer.map { it.volume }
        
        // Calculate statistics
        val highPrice = prices.maxOrNull() ?: 0.0
        val lowPrice = prices.minOrNull() ?: 0.0
        val avgPrice = prices.average()
        val currentPrice = prices.last()
        
        // Bid-ask spread statistics
        val spreads = asks.zip(bids) { a, b -> a - b }
        val avgSpread = spreads.average()
        val maxSpread = spreads.maxOrNull() ?: 0.0
        val minSpread = spreads.minOrNull() ?: 0.0
        
        // Volatility (standard deviation of returns)
        val returns = prices.zipWithNext { a, b -> (b - a) / (a + 1e-6) }
        val volatility = if (returns.isNotEmpty()) {
            val mean = returns.average()
            val variance = returns.map { (it - mean) * (it - mean) }.average()
            Math.sqrt(variance).coerceAtLeast(0.0)
        } else {
            0.0
        }
        
        // Volume statistics
        val totalVolume = volumes.sum()
        val avgVolume = volumes.average()
        
        // Time span of buffer
        val oldestTimestamp = buffer.first().timestamp
        val newestTimestamp = buffer.last().timestamp
        val bufferAgeSeconds = (newestTimestamp - oldestTimestamp) / 1000L
        
        // Price momentum (current vs oldest)
        val momentumPercent = if (prices.first() > 0) {
            ((currentPrice - prices.first()) / prices.first()) * 100
        } else {
            0.0
        }
        
        // Store statistics
        statistics[symbol] = WindowStatistics(
            bufferSize = buffer.size,
            oldestTimestamp = oldestTimestamp,
            newestTimestamp = newestTimestamp,
            bufferAgeSeconds = bufferAgeSeconds,
            highPrice = highPrice,
            lowPrice = lowPrice,
            avgPrice = avgPrice,
            currentPrice = currentPrice,
            avgSpread = avgSpread,
            maxSpread = maxSpread,
            minSpread = minSpread,
            volatility = volatility,
            momentumPercent = momentumPercent,
            totalVolume = totalVolume,
            avgVolume = avgVolume,
            ticksPerSecond = if (bufferAgeSeconds > 0) 
                buffer.size.toDouble() / bufferAgeSeconds 
            else 0.0
        )
    }
}

/**
 * Statistics about a rolling tick window.
 * Provides diagnostics and decision context for the board.
 */
data class WindowStatistics(
    val bufferSize: Int = 0,
    val oldestTimestamp: Long = 0,
    val newestTimestamp: Long = 0,
    val bufferAgeSeconds: Long = 0,
    val highPrice: Double = 0.0,
    val lowPrice: Double = 0.0,
    val avgPrice: Double = 0.0,
    val currentPrice: Double = 0.0,
    val avgSpread: Double = 0.0,
    val maxSpread: Double = 0.0,
    val minSpread: Double = 0.0,
    val volatility: Double = 0.0,
    val momentumPercent: Double = 0.0,
    val totalVolume: Double = 0.0,
    val avgVolume: Double = 0.0,
    val ticksPerSecond: Double = 0.0
) {
    /**
     * Get a human-readable summary of the window.
     */
    fun summary(): String {
        return "Symbol Window: $bufferSize ticks over ${bufferAgeSeconds}s | " +
                "Price: $currentPrice (high=$highPrice, low=$lowPrice) | " +
                "Momentum: ${"%.2f".format(momentumPercent)}% | " +
                "Volatility: ${"%.4f".format(volatility)} | " +
                "Spread: ${"%.4f".format(avgSpread)} | " +
                "Rate: ${"%.2f".format(ticksPerSecond)} ticks/sec"
    }
}
