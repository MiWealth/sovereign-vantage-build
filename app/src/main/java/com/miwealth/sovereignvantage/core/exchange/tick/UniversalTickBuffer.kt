package com.miwealth.sovereignvantage.core.exchange.tick

/**
 * UNIVERSAL TICK BUFFER SYSTEM
 * 
 * Sovereign Vantage: Arthur Edition V5.19.241
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * Purpose:
 * Buffers price ticks from ANY exchange and replays them in compressed time
 * to give the DQN and AI Board realistic market microstructure.
 * 
 * Why This Matters:
 * - DQN learns HOW price moved, not just where it ended
 * - Board sees realistic momentum, reversals, support/resistance tests
 * - No data loss — full tick-by-tick history preserved
 * - Works with any exchange (Binance, Kraken, Coinbase, etc.)
 * 
 * Architecture:
 * 1. Collection Phase: Buffer ticks for 30 seconds (one candle period)
 * 2. Replay Phase: Burst-feed ticks at 10x speed (30s → 3s playback)
 * 3. Learning Phase: DQN sees temporal causality, board votes with context
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */

import android.util.Log
import com.miwealth.sovereignvantage.core.trading.TradingCoordinator
import com.miwealth.sovereignvantage.core.utils.SystemLogger
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Universal tick format - works for ANY exchange.
 */
data class UniversalTick(
    val exchange: String,      // "binance", "kraken", "coinbase"
    val symbol: String,        // "BTC/USDT", "BTC/USD", etc.
    val price: Double,
    val volume: Double,
    val bid: Double,
    val ask: Double,
    val timestamp: Long,
    val source: TickSource
)

/**
 * Where the tick came from.
 */
enum class TickSource {
    WEBSOCKET,      // Real-time stream (best)
    REST_POLL,      // Periodic polling (Binance public)
    TESTNET,        // Testnet data
    PAPER           // Simulated
}

/**
 * Universal Tick Buffer Manager.
 * 
 * Collects ticks from any exchange, buffers them, and replays in compressed time.
 */
class UniversalTickBufferManager(
    private val coordinator: TradingCoordinator,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val replaySpeedMultiplier: Int = 10  // 10x = 30s candle replays in 3s
) {
    companion object {
        private const val TAG = "UniversalTickBuffer"
        private const val BUFFER_DURATION_MS = 30_000L  // 30 seconds (1 candle period)
    }
    
    // Buffers: symbol -> list of ticks
    private val buffers = ConcurrentHashMap<String, MutableList<UniversalTick>>()
    
    // Replay jobs: symbol -> job
    private val replayJobs = ConcurrentHashMap<String, Job>()
    
    // Stats tracking
    private val stats = ConcurrentHashMap<String, BufferStats>()
    
    // Auto-replay timer per symbol
    private val autoReplayTimers = ConcurrentHashMap<String, Job>()
    
    /**
     * Buffer a tick from any exchange.
     * Called by tick providers as ticks arrive.
     */
    fun bufferTick(tick: UniversalTick) {
        val buffer = buffers.getOrPut(tick.symbol) { mutableListOf() }
        buffer.add(tick)
        
        // Update stats
        val symbolStats = stats.getOrPut(tick.symbol) { BufferStats() }
        symbolStats.ticksBuffered++
        symbolStats.lastTickTime = System.currentTimeMillis()
        
        SystemLogger.d(TAG, "📥 BUILD #241: Buffered tick ${tick.symbol} @ ${tick.price} " +
            "(source: ${tick.source}, buffer: ${buffer.size} ticks)")
        
        // Start auto-replay timer if not running
        if (!autoReplayTimers.containsKey(tick.symbol)) {
            startAutoReplayTimer(tick.symbol)
        }
    }
    
    /**
     * Auto-replay timer: triggers replay every BUFFER_DURATION_MS.
     * Simulates candle close events.
     */
    private fun startAutoReplayTimer(symbol: String) {
        autoReplayTimers[symbol] = scope.launch {
            while (isActive) {
                delay(BUFFER_DURATION_MS)
                
                val buffer = buffers[symbol]
                if (buffer != null && buffer.isNotEmpty()) {
                    SystemLogger.system("🕒 BUILD #241: Auto-replay timer fired for $symbol (${buffer.size} ticks buffered)")
                    replayBuffer(symbol)
                }
            }
        }
    }
    
    /**
     * Replay buffered ticks for a symbol in compressed time.
     * 
     * @param symbol The trading pair (e.g., "BTC/USDT")
     */
    suspend fun replayBuffer(symbol: String) {
        val ticks = buffers[symbol]?.toList() ?: return
        if (ticks.isEmpty()) {
            SystemLogger.d(TAG, "⚠️ BUILD #241: No ticks to replay for $symbol")
            return
        }
        
        // Cancel existing replay if running
        replayJobs[symbol]?.cancel()
        
        // Start new replay job
        replayJobs[symbol] = scope.launch {
            try {
                val delayMs = calculateReplayDelay(ticks.size)
                
                SystemLogger.system("🎬 BUILD #241: Replaying ${ticks.size} ticks for $symbol " +
                    "(${replaySpeedMultiplier}x speed, ${delayMs}ms between ticks)")
                
                val startTime = System.currentTimeMillis()
                
                ticks.forEachIndexed { index, tick ->
                    // Feed to coordinator
                    coordinator.onPriceUpdate(
                        symbol = tick.symbol,
                        open = tick.price,
                        high = tick.price,
                        low = tick.price,
                        close = tick.price,
                        volume = tick.volume
                    )
                    
                    // Delay between ticks (compressed time)
                    if (index < ticks.size - 1) {
                        delay(delayMs)
                    }
                }
                
                val duration = System.currentTimeMillis() - startTime
                
                SystemLogger.system("✅ BUILD #241: Replay complete for $symbol " +
                    "(${ticks.size} ticks in ${duration}ms, avg ${duration/ticks.size}ms/tick)")
                
                // Update stats
                stats[symbol]?.apply {
                    ticksReplayed += ticks.size
                    replayCount++
                    lastReplayDuration = duration
                }
                
            } catch (e: CancellationException) {
                SystemLogger.d(TAG, "⚠️ BUILD #241: Replay cancelled for $symbol")
                throw e
            } catch (e: Exception) {
                SystemLogger.error("❌ BUILD #241: Replay error for $symbol: ${e.message}", e)
            }
        }
        
        // Wait for replay to complete
        replayJobs[symbol]?.join()
        
        // Clear buffer after successful replay
        buffers[symbol]?.clear()
        SystemLogger.d(TAG, "🧹 BUILD #241: Buffer cleared for $symbol")
    }
    
    /**
     * Calculate delay between ticks during replay.
     * Adapts based on buffer size to maintain smooth playback.
     */
    private fun calculateReplayDelay(tickCount: Int): Long {
        // Target: replay in ~3 seconds (for 10x compression of 30s)
        val targetDurationMs = BUFFER_DURATION_MS / replaySpeedMultiplier
        val delayMs = if (tickCount > 0) targetDurationMs / tickCount else 500L
        
        // Clamp between 10ms (max speed) and 1000ms (min speed)
        return delayMs.coerceIn(10L, 1000L)
    }
    
    /**
     * Manually trigger replay for a symbol (e.g., when candle closes).
     */
    fun triggerReplay(symbol: String) {
        scope.launch {
            replayBuffer(symbol)
        }
    }
    
    /**
     * Get buffer statistics for a symbol.
     */
    fun getStats(symbol: String): BufferStats? {
        return stats[symbol]
    }
    
    /**
     * Get all buffer statistics.
     */
    fun getAllStats(): Map<String, BufferStats> {
        return stats.toMap()
    }
    
    /**
     * Clear all buffers (useful for reset/restart).
     */
    fun clearAllBuffers() {
        buffers.clear()
        stats.values.forEach { it.reset() }
        SystemLogger.system("🧹 BUILD #241: All tick buffers cleared")
    }
    
    /**
     * Shutdown the buffer manager.
     */
    fun shutdown() {
        autoReplayTimers.values.forEach { it.cancel() }
        replayJobs.values.forEach { it.cancel() }
        scope.cancel()
        SystemLogger.system("🛑 BUILD #241: UniversalTickBufferManager shutdown")
    }
}

/**
 * Statistics for a symbol's tick buffer.
 */
data class BufferStats(
    var ticksBuffered: Long = 0,
    var ticksReplayed: Long = 0,
    var replayCount: Int = 0,
    var lastTickTime: Long = 0,
    var lastReplayDuration: Long = 0
) {
    fun reset() {
        ticksBuffered = 0
        ticksReplayed = 0
        replayCount = 0
        lastTickTime = 0
        lastReplayDuration = 0
    }
}
