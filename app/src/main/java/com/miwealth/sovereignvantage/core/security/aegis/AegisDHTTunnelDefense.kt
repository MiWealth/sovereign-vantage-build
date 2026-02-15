package com.miwealth.sovereignvantage.core.security.aegis

import com.miwealth.sovereignvantage.core.dht.DHTClient
import com.miwealth.sovereignvantage.core.dht.DHTNode
import com.miwealth.sovereignvantage.core.security.pqc.P2PSecureTransport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * SOVEREIGN VANTAGE V5.5.51 "ARTHUR EDITION"
 * AEGIS DHT TUNNEL DEFENSE SYSTEM
 * 
 * Protects DHT communications from sophisticated network attacks:
 * - Traffic padding and timing jitter (anti-traffic analysis)
 * - Cover traffic generation (obfuscation)
 * - Packet hash tracking (anti-replay attacks)
 * - Sybil attack detection via reputation scoring
 * - Eclipse attack prevention via diverse peer selection
 * - Tunnel integrity monitoring
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 */
class AegisDHTTunnelDefense private constructor() {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val secureRandom = SecureRandom()
    
    // Anti-replay: Track seen packet hashes
    private val seenPacketHashes = ConcurrentHashMap<String, Long>()
    private val maxPacketCacheSize = 100_000
    private val packetCacheExpiryMs = 300_000L // 5 minutes
    
    // Peer reputation for Sybil detection
    private val peerReputations = ConcurrentHashMap<String, PeerReputation>()
    private val peerBehaviorHistory = ConcurrentHashMap<String, MutableList<BehaviorEvent>>()
    
    // Traffic analysis defense
    private val trafficStats = ConcurrentHashMap<String, TrafficStats>()
    private var coverTrafficEnabled = true
    private var timingJitterEnabled = true
    
    // Tunnel health monitoring
    private val tunnelHealth = ConcurrentHashMap<String, TunnelHealthMetrics>()
    
    // Eclipse attack prevention
    private val peerDiversity = ConcurrentHashMap<String, PeerDiversityMetrics>()
    
    // Metrics
    private val _defenseMetrics = MutableStateFlow(TunnelDefenseMetrics())
    val defenseMetrics: StateFlow<TunnelDefenseMetrics> = _defenseMetrics.asStateFlow()
    
    private val _alerts = MutableSharedFlow<TunnelSecurityAlert>(replay = 10)
    val alerts: SharedFlow<TunnelSecurityAlert> = _alerts.asSharedFlow()
    
    companion object {
        @Volatile
        private var instance: AegisDHTTunnelDefense? = null
        
        // Traffic analysis defense settings
        const val MIN_PACKET_SIZE = 256 // Pad all packets to at least this
        const val PADDING_BLOCK_SIZE = 64 // Pad to multiples of this
        const val MAX_TIMING_JITTER_MS = 50L
        const val COVER_TRAFFIC_INTERVAL_MS = 5000L
        
        // Sybil detection thresholds
        const val SYBIL_SIMILARITY_THRESHOLD = 0.85
        const val MIN_REPUTATION_SCORE = 0.0
        const val MAX_REPUTATION_SCORE = 100.0
        const val REPUTATION_DECAY_FACTOR = 0.99
        const val NEW_PEER_INITIAL_REPUTATION = 50.0
        
        // Eclipse attack prevention
        const val MIN_UNIQUE_SUBNETS = 3
        const val MAX_PEERS_PER_SUBNET = 5
        const val DIVERSITY_CHECK_INTERVAL_MS = 60_000L
        
        // Replay detection
        const val PACKET_HASH_EXPIRY_MS = 300_000L
        
        fun getInstance(): AegisDHTTunnelDefense {
            return instance ?: synchronized(this) {
                instance ?: AegisDHTTunnelDefense().also { instance = it }
            }
        }
    }
    
    init {
        // Periodic cleanup
        scope.launch {
            while (isActive) {
                delay(60_000)
                cleanupExpiredData()
                updateMetrics()
            }
        }
        
        // Cover traffic generation
        scope.launch {
            while (isActive) {
                delay(COVER_TRAFFIC_INTERVAL_MS)
                if (coverTrafficEnabled) {
                    generateCoverTraffic()
                }
            }
        }
        
        // Periodic diversity check
        scope.launch {
            while (isActive) {
                delay(DIVERSITY_CHECK_INTERVAL_MS)
                checkPeerDiversity()
            }
        }
    }
    
    // ========================================================================
    // TRAFFIC PADDING AND OBFUSCATION
    // ========================================================================
    
    /**
     * Pad outgoing packet to prevent traffic analysis based on size
     */
    fun padPacket(data: ByteArray): ByteArray {
        // Calculate padded size (minimum size + round up to block)
        val targetSize = maxOf(MIN_PACKET_SIZE, 
            ((data.size / PADDING_BLOCK_SIZE) + 1) * PADDING_BLOCK_SIZE)
        
        if (data.size >= targetSize) return data
        
        // Create padded packet: [original length (4 bytes)][data][random padding]
        val padded = ByteArray(targetSize)
        val length = data.size
        
        // Write original length
        padded[0] = (length shr 24).toByte()
        padded[1] = (length shr 16).toByte()
        padded[2] = (length shr 8).toByte()
        padded[3] = length.toByte()
        
        // Copy original data
        System.arraycopy(data, 0, padded, 4, data.size)
        
        // Fill remaining with random padding
        val paddingStart = 4 + data.size
        val padding = ByteArray(targetSize - paddingStart)
        secureRandom.nextBytes(padding)
        System.arraycopy(padding, 0, padded, paddingStart, padding.size)
        
        return padded
    }
    
    /**
     * Remove padding from received packet
     */
    fun unpadPacket(padded: ByteArray): ByteArray? {
        if (padded.size < 4) return null
        
        // Read original length
        val length = ((padded[0].toInt() and 0xFF) shl 24) or
                    ((padded[1].toInt() and 0xFF) shl 16) or
                    ((padded[2].toInt() and 0xFF) shl 8) or
                    (padded[3].toInt() and 0xFF)
        
        if (length < 0 || length > padded.size - 4) return null
        
        return padded.copyOfRange(4, 4 + length)
    }
    
    /**
     * Add timing jitter to outgoing transmission
     */
    suspend fun applyTimingJitter() {
        if (timingJitterEnabled) {
            val jitter = secureRandom.nextLong(MAX_TIMING_JITTER_MS)
            delay(jitter)
        }
    }
    
    /**
     * Generate cover traffic to mask real communication patterns
     */
    private suspend fun generateCoverTraffic() {
        // Generate random-looking traffic that blends with real traffic
        val coverSize = secureRandom.nextInt(512) + MIN_PACKET_SIZE
        val coverData = ByteArray(coverSize)
        secureRandom.nextBytes(coverData)
        
        // Mark as cover traffic (first byte pattern)
        coverData[0] = 0xCC.toByte()
        
        // In production, this would be sent to random peers
        // For now, just update metrics
        val metrics = _defenseMetrics.value
        _defenseMetrics.value = metrics.copy(
            coverTrafficGenerated = metrics.coverTrafficGenerated + coverSize
        )
    }
    
    /**
     * Check if packet is cover traffic (should be discarded)
     */
    fun isCoverTraffic(data: ByteArray): Boolean {
        return data.isNotEmpty() && data[0] == 0xCC.toByte()
    }
    
    // ========================================================================
    // ANTI-REPLAY PROTECTION
    // ========================================================================
    
    /**
     * Check if packet is a replay attack
     * Returns true if this is a NEW packet (not a replay)
     */
    fun checkAndRecordPacket(packet: ByteArray, senderId: String): ReplayCheckResult {
        val hash = computePacketHash(packet, senderId)
        val now = System.currentTimeMillis()
        
        // Check if we've seen this packet before
        val previousTime = seenPacketHashes.putIfAbsent(hash, now)
        
        if (previousTime != null) {
            // This is a replay!
            val timeSinceOriginal = now - previousTime
            
            scope.launch {
                _alerts.emit(TunnelSecurityAlert(
                    type = AlertType.REPLAY_ATTACK_DETECTED,
                    peerId = senderId,
                    details = "Replay detected, original sent ${timeSinceOriginal}ms ago",
                    severity = if (timeSinceOriginal < 1000) Severity.HIGH else Severity.MEDIUM,
                    timestamp = now
                ))
            }
            
            // Update metrics
            updateReplayMetrics()
            
            // Decrease peer reputation
            decreasePeerReputation(senderId, 10.0, "Replay attack")
            
            return ReplayCheckResult(
                isNew = false,
                isReplay = true,
                originalTimestamp = previousTime
            )
        }
        
        // Cleanup if cache is too large
        if (seenPacketHashes.size > maxPacketCacheSize) {
            cleanupPacketCache()
        }
        
        return ReplayCheckResult(isNew = true, isReplay = false, originalTimestamp = null)
    }
    
    private fun computePacketHash(packet: ByteArray, senderId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(senderId.toByteArray())
        digest.update(packet)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    
    private fun cleanupPacketCache() {
        val now = System.currentTimeMillis()
        seenPacketHashes.entries.removeIf { (_, timestamp) ->
            now - timestamp > packetCacheExpiryMs
        }
    }
    
    private fun updateReplayMetrics() {
        val metrics = _defenseMetrics.value
        _defenseMetrics.value = metrics.copy(
            replayAttacksBlocked = metrics.replayAttacksBlocked + 1
        )
    }
    
    // ========================================================================
    // SYBIL ATTACK DETECTION
    // ========================================================================
    
    /**
     * Analyze peer for Sybil attack characteristics
     */
    fun analyzePeerForSybil(
        peerId: String,
        peerInfo: PeerInfo
    ): SybilAnalysisResult {
        val existingPeers = peerReputations.keys.toList()
        val suspiciousPatterns = mutableListOf<String>()
        var sybilProbability = 0.0
        
        // Check 1: Timing analysis (Sybil nodes often appear in clusters)
        val recentPeers = peerReputations.entries
            .filter { System.currentTimeMillis() - it.value.firstSeen < 60_000 }
            .map { it.key }
        
        if (recentPeers.size > 10) {
            sybilProbability += 0.2
            suspiciousPatterns.add("HIGH_PEER_RATE")
        }
        
        // Check 2: Behavioral similarity to existing peers
        val behaviorSimilarities = existingPeers.mapNotNull { existingId ->
            peerBehaviorHistory[existingId]?.let { existingBehavior ->
                val newBehavior = peerBehaviorHistory[peerId] ?: emptyList()
                calculateBehaviorSimilarity(newBehavior, existingBehavior)
            }
        }
        
        val avgSimilarity = if (behaviorSimilarities.isNotEmpty()) 
            behaviorSimilarities.average() else 0.0
        
        if (avgSimilarity > SYBIL_SIMILARITY_THRESHOLD) {
            sybilProbability += 0.4
            suspiciousPatterns.add("BEHAVIOR_SIMILARITY")
        }
        
        // Check 3: Network topology analysis (same subnet clustering)
        val subnet = extractSubnet(peerInfo.address)
        val peersInSameSubnet = peerReputations.entries.count { (id, rep) ->
            extractSubnet(rep.address) == subnet
        }
        
        if (peersInSameSubnet > MAX_PEERS_PER_SUBNET) {
            sybilProbability += 0.3
            suspiciousPatterns.add("SUBNET_CLUSTERING")
        }
        
        // Check 4: ID pattern analysis (sequential or patterned IDs)
        if (hasPatternedId(peerId, existingPeers)) {
            sybilProbability += 0.2
            suspiciousPatterns.add("PATTERNED_ID")
        }
        
        // Check 5: Response timing patterns
        val timingVariance = calculateTimingVariance(peerId)
        if (timingVariance < 5.0) { // Very consistent timing = bot-like
            sybilProbability += 0.15
            suspiciousPatterns.add("CONSISTENT_TIMING")
        }
        
        // Determine if peer is likely Sybil
        val isSybil = sybilProbability > 0.5
        
        if (isSybil) {
            scope.launch {
                _alerts.emit(TunnelSecurityAlert(
                    type = AlertType.SYBIL_ATTACK_SUSPECTED,
                    peerId = peerId,
                    details = "Sybil probability: ${(sybilProbability * 100).toInt()}%, patterns: $suspiciousPatterns",
                    severity = if (sybilProbability > 0.8) Severity.HIGH else Severity.MEDIUM,
                    timestamp = System.currentTimeMillis()
                ))
            }
            
            // Reduce reputation significantly
            decreasePeerReputation(peerId, 30.0, "Sybil attack suspected")
        }
        
        return SybilAnalysisResult(
            peerId = peerId,
            sybilProbability = sybilProbability,
            suspiciousPatterns = suspiciousPatterns,
            isSuspectedSybil = isSybil,
            recommendedAction = when {
                sybilProbability > 0.8 -> SybilAction.BLOCK
                sybilProbability > 0.5 -> SybilAction.QUARANTINE
                sybilProbability > 0.3 -> SybilAction.MONITOR
                else -> SybilAction.ALLOW
            }
        )
    }
    
    private fun calculateBehaviorSimilarity(
        behavior1: List<BehaviorEvent>,
        behavior2: List<BehaviorEvent>
    ): Double {
        if (behavior1.isEmpty() || behavior2.isEmpty()) return 0.0
        
        // Compare action distributions
        val actions1 = behavior1.groupingBy { it.action }.eachCount()
        val actions2 = behavior2.groupingBy { it.action }.eachCount()
        
        val allActions = (actions1.keys + actions2.keys).distinct()
        
        var similarity = 0.0
        allActions.forEach { action ->
            val count1 = actions1[action] ?: 0
            val count2 = actions2[action] ?: 0
            val total1 = behavior1.size.toDouble()
            val total2 = behavior2.size.toDouble()
            
            val ratio1 = count1 / total1
            val ratio2 = count2 / total2
            
            similarity += 1.0 - abs(ratio1 - ratio2)
        }
        
        return similarity / allActions.size
    }
    
    private fun extractSubnet(address: String): String {
        // Extract /24 subnet from IP address
        val parts = address.split(".")
        return if (parts.size >= 3) {
            "${parts[0]}.${parts[1]}.${parts[2]}"
        } else {
            address
        }
    }
    
    private fun hasPatternedId(newId: String, existingIds: List<String>): Boolean {
        // Check for sequential patterns or common prefixes
        val prefix = newId.take(8)
        val matchingPrefixes = existingIds.count { it.startsWith(prefix) }
        return matchingPrefixes > 3
    }
    
    private fun calculateTimingVariance(peerId: String): Double {
        val history = peerBehaviorHistory[peerId] ?: return Double.MAX_VALUE
        if (history.size < 5) return Double.MAX_VALUE
        
        val intervals = history.zipWithNext { a, b -> b.timestamp - a.timestamp }
        if (intervals.isEmpty()) return Double.MAX_VALUE
        
        val mean = intervals.average()
        val variance = intervals.map { (it - mean) * (it - mean) }.average()
        return sqrt(variance)
    }
    
    // ========================================================================
    // ECLIPSE ATTACK PREVENTION
    // ========================================================================
    
    /**
     * Check peer diversity to prevent eclipse attacks
     */
    fun checkPeerDiversity(): DiversityCheckResult {
        val now = System.currentTimeMillis()
        val subnets = mutableMapOf<String, MutableList<String>>()
        
        // Group peers by subnet
        peerReputations.forEach { (peerId, rep) ->
            val subnet = extractSubnet(rep.address)
            subnets.getOrPut(subnet) { mutableListOf() }.add(peerId)
        }
        
        val uniqueSubnets = subnets.size
        val maxPeersInSubnet = subnets.values.maxOfOrNull { it.size } ?: 0
        
        val isHealthy = uniqueSubnets >= MIN_UNIQUE_SUBNETS
        val hasDominantSubnet = maxPeersInSubnet > (peerReputations.size * 0.5)
        
        if (!isHealthy || hasDominantSubnet) {
            scope.launch {
                _alerts.emit(TunnelSecurityAlert(
                    type = AlertType.ECLIPSE_ATTACK_RISK,
                    peerId = "network",
                    details = "Unique subnets: $uniqueSubnets, max peers in subnet: $maxPeersInSubnet",
                    severity = if (hasDominantSubnet) Severity.HIGH else Severity.MEDIUM,
                    timestamp = now
                ))
            }
        }
        
        return DiversityCheckResult(
            uniqueSubnets = uniqueSubnets,
            totalPeers = peerReputations.size,
            subnetDistribution = subnets.mapValues { it.value.size },
            isHealthy = isHealthy && !hasDominantSubnet,
            recommendations = buildDiversityRecommendations(uniqueSubnets, hasDominantSubnet)
        )
    }
    
    /**
     * Select diverse peers for connection
     */
    fun selectDiversePeers(candidates: List<DHTNode>, count: Int): List<DHTNode> {
        val selectedSubnets = mutableSetOf<String>()
        val selected = mutableListOf<DHTNode>()
        
        // First pass: select one peer per unique subnet
        for (node in candidates.shuffled()) {
            val subnet = extractSubnet(node.address)
            if (subnet !in selectedSubnets) {
                selected.add(node)
                selectedSubnets.add(subnet)
                if (selected.size >= count) break
            }
        }
        
        // Second pass: fill remaining slots with highest reputation
        if (selected.size < count) {
            val remaining = candidates
                .filter { it !in selected }
                .sortedByDescending { peerReputations[it.id]?.score ?: NEW_PEER_INITIAL_REPUTATION }
                .take(count - selected.size)
            selected.addAll(remaining)
        }
        
        return selected
    }
    
    private fun buildDiversityRecommendations(uniqueSubnets: Int, hasDominant: Boolean): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (uniqueSubnets < MIN_UNIQUE_SUBNETS) {
            recommendations.add("Connect to peers from additional network segments")
        }
        
        if (hasDominant) {
            recommendations.add("Reduce connections from dominant subnet")
            recommendations.add("Prioritize geographically diverse peers")
        }
        
        return recommendations
    }
    
    // ========================================================================
    // PEER REPUTATION MANAGEMENT
    // ========================================================================
    
    /**
     * Get or create peer reputation
     */
    fun getPeerReputation(peerId: String, peerAddress: String = ""): PeerReputation {
        return peerReputations.getOrPut(peerId) {
            PeerReputation(
                peerId = peerId,
                address = peerAddress,
                score = NEW_PEER_INITIAL_REPUTATION,
                firstSeen = System.currentTimeMillis(),
                lastActivity = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Increase peer reputation for good behavior
     */
    fun increasePeerReputation(peerId: String, amount: Double, reason: String) {
        val rep = peerReputations[peerId] ?: return
        val newScore = (rep.score + amount).coerceIn(MIN_REPUTATION_SCORE, MAX_REPUTATION_SCORE)
        peerReputations[peerId] = rep.copy(
            score = newScore,
            lastActivity = System.currentTimeMillis(),
            positiveActions = rep.positiveActions + 1
        )
        
        recordBehaviorEvent(peerId, BehaviorEvent(
            action = "REPUTATION_INCREASE",
            details = reason,
            timestamp = System.currentTimeMillis()
        ))
    }
    
    /**
     * Decrease peer reputation for suspicious/malicious behavior
     */
    fun decreasePeerReputation(peerId: String, amount: Double, reason: String) {
        val rep = peerReputations[peerId] ?: return
        val newScore = (rep.score - amount).coerceIn(MIN_REPUTATION_SCORE, MAX_REPUTATION_SCORE)
        peerReputations[peerId] = rep.copy(
            score = newScore,
            lastActivity = System.currentTimeMillis(),
            negativeActions = rep.negativeActions + 1
        )
        
        recordBehaviorEvent(peerId, BehaviorEvent(
            action = "REPUTATION_DECREASE",
            details = reason,
            timestamp = System.currentTimeMillis()
        ))
        
        // Block peer if reputation drops too low
        if (newScore < 10.0) {
            scope.launch {
                _alerts.emit(TunnelSecurityAlert(
                    type = AlertType.PEER_BLOCKED,
                    peerId = peerId,
                    details = "Reputation dropped to $newScore: $reason",
                    severity = Severity.HIGH,
                    timestamp = System.currentTimeMillis()
                ))
            }
        }
    }
    
    /**
     * Record behavior event for analysis
     */
    fun recordBehaviorEvent(peerId: String, event: BehaviorEvent) {
        val history = peerBehaviorHistory.getOrPut(peerId) { mutableListOf() }
        history.add(event)
        
        // Keep only recent history
        if (history.size > 1000) {
            history.removeAt(0)
        }
    }
    
    /**
     * Check if peer should be allowed based on reputation
     */
    fun shouldAllowPeer(peerId: String): Boolean {
        val rep = peerReputations[peerId] ?: return true // Allow unknown peers initially
        return rep.score >= 10.0
    }
    
    // ========================================================================
    // TUNNEL HEALTH MONITORING
    // ========================================================================
    
    /**
     * Record tunnel health metrics
     */
    fun recordTunnelMetrics(tunnelId: String, metrics: TunnelMetrics) {
        val existing = tunnelHealth[tunnelId] ?: TunnelHealthMetrics(tunnelId = tunnelId)
        
        val updated = existing.copy(
            messagesTransmitted = existing.messagesTransmitted + metrics.messageCount,
            bytesTransmitted = existing.bytesTransmitted + metrics.byteCount,
            averageLatency = (existing.averageLatency + metrics.latencyMs) / 2,
            errorCount = existing.errorCount + metrics.errors,
            lastActivity = System.currentTimeMillis()
        )
        
        tunnelHealth[tunnelId] = updated
        
        // Check for tunnel health issues
        if (updated.errorCount > 10 || updated.averageLatency > 5000) {
            scope.launch {
                _alerts.emit(TunnelSecurityAlert(
                    type = AlertType.TUNNEL_HEALTH_DEGRADED,
                    peerId = tunnelId,
                    details = "Errors: ${updated.errorCount}, Avg latency: ${updated.averageLatency}ms",
                    severity = Severity.MEDIUM,
                    timestamp = System.currentTimeMillis()
                ))
            }
        }
    }
    
    // ========================================================================
    // CLEANUP AND MAINTENANCE
    // ========================================================================
    
    private fun cleanupExpiredData() {
        val now = System.currentTimeMillis()
        
        // Cleanup packet hashes
        seenPacketHashes.entries.removeIf { (_, timestamp) ->
            now - timestamp > PACKET_HASH_EXPIRY_MS
        }
        
        // Decay reputations over time
        peerReputations.forEach { (peerId, rep) ->
            if (now - rep.lastActivity > 3600_000) { // 1 hour inactive
                peerReputations[peerId] = rep.copy(
                    score = rep.score * REPUTATION_DECAY_FACTOR
                )
            }
        }
        
        // Remove very old behavior history
        peerBehaviorHistory.forEach { (peerId, history) ->
            history.removeIf { now - it.timestamp > 86400_000 } // 24 hours
        }
    }
    
    private fun updateMetrics() {
        _defenseMetrics.value = TunnelDefenseMetrics(
            activePeers = peerReputations.size,
            blockedPeers = peerReputations.values.count { it.score < 10.0 },
            replayAttacksBlocked = _defenseMetrics.value.replayAttacksBlocked,
            sybilSuspectsDetected = peerReputations.values.count { it.negativeActions > 3 },
            coverTrafficGenerated = _defenseMetrics.value.coverTrafficGenerated,
            packetsCached = seenPacketHashes.size.toLong()
        )
    }
    
    /**
     * Shutdown defense system
     */
    fun shutdown() {
        scope.cancel()
        seenPacketHashes.clear()
        peerReputations.clear()
        peerBehaviorHistory.clear()
        tunnelHealth.clear()
    }
}

// ========================================================================
// DATA CLASSES
// ========================================================================

data class PeerReputation(
    val peerId: String,
    val address: String,
    val score: Double,
    val firstSeen: Long,
    val lastActivity: Long,
    val positiveActions: Int = 0,
    val negativeActions: Int = 0
)

data class PeerInfo(
    val peerId: String,
    val address: String,
    val port: Int,
    val publicKey: ByteArray? = null
)

data class BehaviorEvent(
    val action: String,
    val details: String,
    val timestamp: Long
)

data class ReplayCheckResult(
    val isNew: Boolean,
    val isReplay: Boolean,
    val originalTimestamp: Long?
)

data class SybilAnalysisResult(
    val peerId: String,
    val sybilProbability: Double,
    val suspiciousPatterns: List<String>,
    val isSuspectedSybil: Boolean,
    val recommendedAction: SybilAction
)

data class DiversityCheckResult(
    val uniqueSubnets: Int,
    val totalPeers: Int,
    val subnetDistribution: Map<String, Int>,
    val isHealthy: Boolean,
    val recommendations: List<String>
)

data class TunnelHealthMetrics(
    val tunnelId: String,
    val messagesTransmitted: Long = 0,
    val bytesTransmitted: Long = 0,
    val averageLatency: Double = 0.0,
    val errorCount: Int = 0,
    val lastActivity: Long = System.currentTimeMillis()
)

data class TunnelMetrics(
    val messageCount: Int,
    val byteCount: Long,
    val latencyMs: Double,
    val errors: Int
)

data class PeerDiversityMetrics(
    val subnet: String,
    val peerCount: Int,
    val lastChecked: Long
)

data class TrafficStats(
    val peerId: String,
    val bytesSent: Long = 0,
    val bytesReceived: Long = 0,
    val packetsSent: Long = 0,
    val packetsReceived: Long = 0,
    val lastActivity: Long = System.currentTimeMillis()
)

data class TunnelDefenseMetrics(
    val activePeers: Int = 0,
    val blockedPeers: Int = 0,
    val replayAttacksBlocked: Long = 0,
    val sybilSuspectsDetected: Int = 0,
    val coverTrafficGenerated: Long = 0,
    val packetsCached: Long = 0
)

data class TunnelSecurityAlert(
    val type: AlertType,
    val peerId: String,
    val details: String,
    val severity: Severity,
    val timestamp: Long
)

enum class AlertType {
    REPLAY_ATTACK_DETECTED,
    SYBIL_ATTACK_SUSPECTED,
    ECLIPSE_ATTACK_RISK,
    TUNNEL_HEALTH_DEGRADED,
    PEER_BLOCKED,
    SUSPICIOUS_TRAFFIC_PATTERN
}

enum class Severity {
    LOW, MEDIUM, HIGH, CRITICAL
}

enum class SybilAction {
    ALLOW,
    MONITOR,
    QUARANTINE,
    BLOCK
}
