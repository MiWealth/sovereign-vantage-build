/**
 * DFLP Configuration
 * Decentralized Federated Learning Protocol - Central Configuration
 * 
 * Sovereign Vantage: Arthur Edition V5.17.0
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * This file centralizes all DFLP timing, network, and privacy settings.
 * 
 * DESIGN RATIONALE:
 * - 6-hour epochs balance learning quality with resource efficiency
 * - Manual sync allows user control for critical moments
 * - Minimum thresholds prevent meaningless updates
 * - Staleness checks reject outdated peer data
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 */

package com.miwealth.sovereignvantage.core.dflp

import java.time.Duration
import java.time.Instant

/**
 * Central configuration for the Decentralized Federated Learning Protocol.
 * All timing, network, and privacy parameters are defined here.
 */
object DFLPConfiguration {
    
    // ============================================================================
    // TIMING CONFIGURATION
    // ============================================================================
    
    /**
     * How often to automatically aggregate local updates and share with network.
     * 
     * 6 hours chosen because:
     * - Allows multiple trades to complete (scalps: 5-60min, day trades: 1-8hr)
     * - Provides statistically meaningful training data
     * - Reduces battery/network overhead (4 syncs/day vs 1,440 with 1-min)
     * - Roughly aligns with major trading sessions
     */
    const val AGGREGATION_INTERVAL_HOURS = 6
    
    /**
     * Aggregation interval as Duration for easier time calculations.
     */
    val AGGREGATION_INTERVAL: Duration = Duration.ofHours(AGGREGATION_INTERVAL_HOURS.toLong())
    
    /**
     * Minimum time between automatic aggregations.
     * Prevents rapid-fire syncs even if thresholds are met.
     */
    val MIN_TIME_BETWEEN_AGGREGATIONS: Duration = Duration.ofHours(1)
    
    /**
     * Maximum age of peer updates before rejection.
     * Updates older than this are considered stale and discarded.
     * Set to 2x aggregation interval to allow some flexibility.
     */
    val MAX_UPDATE_STALENESS: Duration = Duration.ofHours(12)
    
    // ============================================================================
    // TRAINING THRESHOLDS
    // ============================================================================
    
    /**
     * Minimum completed trades required before contributing to DFLP.
     * Prevents sharing noise from insufficient data.
     */
    const val MIN_TRADES_FOR_UPDATE = 5
    
    /**
     * Minimum data samples (price ticks, indicators, outcomes) for meaningful training.
     * Each trade generates multiple samples from its lifecycle.
     */
    const val MIN_SAMPLES_FOR_TRAINING = 50
    
    /**
     * Local training epochs per DFLP cycle.
     * More epochs with less frequent syncs = better local learning before sharing.
     */
    const val LOCAL_TRAINING_EPOCHS = 10
    
    /**
     * Learning rate for local gradient descent.
     * Conservative to prevent overfitting to recent trades.
     */
    const val LOCAL_LEARNING_RATE = 0.001
    
    // ============================================================================
    // NETWORK CONFIGURATION
    // ============================================================================
    
    /**
     * Minimum peer nodes required for federated averaging.
     * Too few peers = insufficient diversity, potential privacy leak.
     */
    const val MIN_PEERS_FOR_AGGREGATION = 5
    
    /**
     * Maximum peer nodes to include in single aggregation round.
     * Limits computational overhead while maintaining diversity.
     */
    const val MAX_PEERS_FOR_AGGREGATION = 50
    
    /**
     * Maximum model updates to queue before forced aggregation.
     * Prevents unbounded memory growth if sync is delayed.
     */
    const val MAX_QUEUED_UPDATES = 20
    
    /**
     * Timeout for peer response during SMPC protocol.
     */
    val PEER_TIMEOUT: Duration = Duration.ofSeconds(30)
    
    /**
     * Bootstrap node addresses for initial DHT discovery.
     * These are lightweight "phone book" servers, NOT involved in SMPC.
     */
    val BOOTSTRAP_NODES = listOf(
        "bootstrap1.miwealth.app:8468",
        "bootstrap2.miwealth.app:8468",
        "bootstrap3.miwealth.app:8468"
    )
    
    /**
     * Port for P2P DFLP communication between user devices.
     */
    const val P2P_PORT = 8469
    
    // ============================================================================
    // DIFFERENTIAL PRIVACY
    // ============================================================================
    
    /**
     * Differential privacy epsilon parameter.
     * Lower = more privacy, less utility. Higher = less privacy, more utility.
     * 0.1 is conservative (strong privacy guarantee).
     */
    const val DP_EPSILON = 0.1
    
    /**
     * Differential privacy delta parameter.
     * Probability of privacy guarantee failing. 1e-5 is standard.
     */
    const val DP_DELTA = 1e-5
    
    /**
     * Noise multiplier for gradient perturbation.
     * Applied before sharing to protect individual trade data.
     */
    const val DP_NOISE_MULTIPLIER = 0.1
    
    /**
     * Maximum gradient norm for clipping.
     * Bounds sensitivity before noise addition.
     */
    const val GRADIENT_CLIP_NORM = 1.0
    
    // ============================================================================
    // MODEL MERGING
    // ============================================================================
    
    /**
     * Weight given to global (network) model when merging.
     * 0.3 = 30% global, 70% local.
     * Preserves user's local specialization while incorporating collective wisdom.
     */
    const val GLOBAL_MERGE_RATIO = 0.3
    
    /**
     * Weight given to local model when merging.
     * Automatically computed as 1 - GLOBAL_MERGE_RATIO.
     */
    const val LOCAL_MERGE_RATIO = 1.0 - GLOBAL_MERGE_RATIO
    
    // ============================================================================
    // TRADE CONTRIBUTION WEIGHTS
    // ============================================================================
    
    /**
     * Weight multiplier for live trades in training.
     * Live trades have real financial outcomes = higher confidence.
     */
    const val LIVE_TRADE_WEIGHT = 1.0
    
    /**
     * Weight multiplier for paper trades in training.
     * Paper trades lack real execution dynamics = lower confidence.
     */
    const val PAPER_TRADE_WEIGHT = 0.7
    
    /**
     * Weight multiplier for demo/backtest trades.
     * Historical simulation = lowest confidence.
     */
    const val DEMO_TRADE_WEIGHT = 0.5
    
    // ============================================================================
    // RESOURCE MANAGEMENT
    // ============================================================================
    
    /**
     * Maximum CPU usage percentage during local training.
     * Prevents app from becoming unresponsive.
     */
    const val MAX_CPU_USAGE_PERCENT = 30
    
    /**
     * Whether to pause training when device is on battery.
     * Saves battery for trading operations.
     */
    const val PAUSE_ON_BATTERY = true
    
    /**
     * Minimum battery percentage required for DFLP operations.
     */
    const val MIN_BATTERY_PERCENT = 20
    
    /**
     * Whether to only sync on WiFi (not mobile data).
     */
    const val WIFI_ONLY_SYNC = false
    
    // ============================================================================
    // VERSIONING
    // ============================================================================
    
    /**
     * Current DFLP protocol version.
     * Incompatible versions will not aggregate together.
     */
    const val PROTOCOL_VERSION = "2.0.0"
    
    /**
     * Minimum compatible protocol version.
     * Peers with older versions are rejected.
     */
    const val MIN_COMPATIBLE_VERSION = "2.0.0"
}

/**
 * Runtime state for DFLP scheduling.
 * Tracks aggregation timing, accumulated data, and manual sync requests.
 */
class DFLPScheduler {
    
    private var lastAggregationTime: Instant = Instant.EPOCH
    private var accumulatedTrades: Int = 0
    private var accumulatedSamples: Int = 0
    private var manualSyncRequested: Boolean = false
    private var queuedUpdatesCount: Int = 0
    
    /**
     * Check if automatic aggregation should be triggered.
     * 
     * Triggers when:
     * 1. Manual sync requested (user pressed "Sync Now")
     * 2. Time elapsed >= 6 hours AND minimum data thresholds met
     * 3. Update queue overflow (failsafe)
     */
    fun shouldTriggerAggregation(): Boolean {
        // Priority 1: Manual sync request
        if (manualSyncRequested) {
            return true
        }
        
        // Priority 2: Queue overflow (failsafe)
        if (queuedUpdatesCount >= DFLPConfiguration.MAX_QUEUED_UPDATES) {
            return true
        }
        
        // Priority 3: Time-based with data thresholds
        val now = Instant.now()
        val timeSinceLastAggregation = Duration.between(lastAggregationTime, now)
        
        // Check minimum time between aggregations
        if (timeSinceLastAggregation < DFLPConfiguration.MIN_TIME_BETWEEN_AGGREGATIONS) {
            return false
        }
        
        // Check if enough time has passed
        if (timeSinceLastAggregation >= DFLPConfiguration.AGGREGATION_INTERVAL) {
            // Only aggregate if we have meaningful data
            return hasMinimumData()
        }
        
        return false
    }
    
    /**
     * Check if we have minimum data for a meaningful aggregation.
     */
    fun hasMinimumData(): Boolean {
        return accumulatedTrades >= DFLPConfiguration.MIN_TRADES_FOR_UPDATE &&
               accumulatedSamples >= DFLPConfiguration.MIN_SAMPLES_FOR_TRAINING
    }
    
    /**
     * Request immediate sync (user-initiated "Sync Now" button).
     * Will trigger on next shouldTriggerAggregation() check.
     */
    fun requestManualSync() {
        manualSyncRequested = true
    }
    
    /**
     * Record a completed trade for aggregation tracking.
     * 
     * @param trade The completed trade record
     * @param samplesGenerated Number of data samples from this trade's lifecycle
     */
    fun onTradeCompleted(samplesGenerated: Int = 10) {
        accumulatedTrades++
        accumulatedSamples += samplesGenerated
    }
    
    /**
     * Record queued update count for overflow detection.
     */
    fun setQueuedUpdatesCount(count: Int) {
        queuedUpdatesCount = count
    }
    
    /**
     * Called after successful aggregation to reset counters.
     */
    fun onAggregationComplete() {
        lastAggregationTime = Instant.now()
        accumulatedTrades = 0
        accumulatedSamples = 0
        manualSyncRequested = false
        queuedUpdatesCount = 0
    }
    
    /**
     * Get time until next scheduled aggregation.
     * Returns Duration.ZERO if aggregation is due now.
     */
    fun timeUntilNextAggregation(): Duration {
        if (manualSyncRequested || !hasMinimumData()) {
            return Duration.ZERO
        }
        
        val now = Instant.now()
        val nextAggregation = lastAggregationTime.plus(DFLPConfiguration.AGGREGATION_INTERVAL)
        val remaining = Duration.between(now, nextAggregation)
        
        return if (remaining.isNegative) Duration.ZERO else remaining
    }
    
    /**
     * Get current aggregation status for UI display.
     */
    fun getStatus(): DFLPStatus {
        return DFLPStatus(
            lastAggregation = lastAggregationTime,
            accumulatedTrades = accumulatedTrades,
            accumulatedSamples = accumulatedSamples,
            timeUntilNext = timeUntilNextAggregation(),
            manualSyncPending = manualSyncRequested,
            hasMinimumData = hasMinimumData()
        )
    }
}

/**
 * Status information for UI display.
 */
data class DFLPStatus(
    val lastAggregation: Instant,
    val accumulatedTrades: Int,
    val accumulatedSamples: Int,
    val timeUntilNext: Duration,
    val manualSyncPending: Boolean,
    val hasMinimumData: Boolean
) {
    /**
     * Human-readable time until next sync.
     */
    fun timeUntilNextFormatted(): String {
        if (manualSyncPending) return "Sync pending..."
        if (timeUntilNext == Duration.ZERO) return "Ready to sync"
        
        val hours = timeUntilNext.toHours()
        val minutes = timeUntilNext.toMinutes() % 60
        
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "< 1m"
        }
    }
    
    /**
     * Progress toward next aggregation (0.0 to 1.0).
     */
    fun progress(): Float {
        val intervalMillis = DFLPConfiguration.AGGREGATION_INTERVAL.toMillis().toFloat()
        val remainingMillis = timeUntilNext.toMillis().toFloat()
        val elapsedMillis = intervalMillis - remainingMillis
        
        return (elapsedMillis / intervalMillis).coerceIn(0f, 1f)
    }
}

/**
 * Staleness checker for incoming peer updates.
 */
object DFLPStalenessChecker {
    
    /**
     * Check if a peer update is too old to be useful.
     * 
     * @param updateTimestamp When the peer generated this update
     * @return true if update should be rejected as stale
     */
    fun isStale(updateTimestamp: Instant): Boolean {
        val age = Duration.between(updateTimestamp, Instant.now())
        return age > DFLPConfiguration.MAX_UPDATE_STALENESS
    }
    
    /**
     * Check if a peer's protocol version is compatible.
     * 
     * @param peerVersion The peer's reported protocol version
     * @return true if peer is compatible
     */
    fun isCompatibleVersion(peerVersion: String): Boolean {
        return compareVersions(peerVersion, DFLPConfiguration.MIN_COMPATIBLE_VERSION) >= 0
    }
    
    /**
     * Simple semantic version comparison.
     * Returns negative if v1 < v2, positive if v1 > v2, 0 if equal.
     */
    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        
        for (i in 0 until maxOf(parts1.size, parts2.size)) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1 - p2
        }
        return 0
    }
}
