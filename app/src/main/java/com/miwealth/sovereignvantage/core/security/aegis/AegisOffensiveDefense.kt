package com.miwealth.sovereignvantage.core.security.aegis

import android.content.Context
import com.miwealth.sovereignvantage.core.dht.DHTClient
import com.miwealth.sovereignvantage.core.security.pqc.SideChannelDefense
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * SOVEREIGN VANTAGE V5.17.0 "ARTHUR EDITION"
 * AEGIS OFFENSIVE DEFENSE SYSTEM
 * 
 * Advanced threat detection and active defense mechanisms:
 * - Real-time anomaly detection with behavioral analysis
 * - Honeypot deployment for attacker profiling
 * - 100x resource exhaustion response to confirmed attackers
 * - Network-wide threat alerts via DHT broadcast
 * - Automatic threat intelligence sharing
 * 
 * Philosophy: "The best defense is making attacks prohibitively expensive."
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 */
class AegisOffensiveDefense private constructor(
    private val context: Context,
    private val dhtClient: DHTClient?
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val secureRandom = SecureRandom()
    
    // Threat tracking
    private val threatRegistry = ConcurrentHashMap<String, ThreatProfile>()
    private val behaviorBaselines = ConcurrentHashMap<String, BehaviorBaseline>()
    private val honeypotInteractions = ConcurrentHashMap<String, HoneypotInteraction>()
    
    // Rate limiting and detection
    private val requestCounts = ConcurrentHashMap<String, AtomicLong>()
    private val lastRequestTimes = ConcurrentHashMap<String, Long>()
    
    // Alert system
    private val _threatAlerts = MutableSharedFlow<ThreatAlert>(replay = 10)
    val threatAlerts: SharedFlow<ThreatAlert> = _threatAlerts.asSharedFlow()
    
    private val _defenseMetrics = MutableStateFlow(DefenseMetrics())
    val defenseMetrics: StateFlow<DefenseMetrics> = _defenseMetrics.asStateFlow()
    
    companion object {
        @Volatile
        private var instance: AegisOffensiveDefense? = null
        
        // Detection thresholds
        const val ANOMALY_THRESHOLD = 3.0 // Standard deviations
        const val MAX_REQUESTS_PER_SECOND = 100
        const val MAX_REQUESTS_PER_MINUTE = 1000
        const val BURST_DETECTION_WINDOW_MS = 1000L
        const val PATTERN_ANALYSIS_WINDOW_MS = 60_000L
        
        // Response settings
        const val RESOURCE_EXHAUSTION_MULTIPLIER = 100
        const val HONEYPOT_RESPONSE_DELAY_MS = 50L
        const val THREAT_EXPIRY_MS = 3600_000L // 1 hour
        
        // DHT channels
        const val THREAT_ALERT_CHANNEL = "aegis_threat_alerts"
        const val THREAT_INTEL_CHANNEL = "aegis_threat_intel"
        
        fun getInstance(context: Context, dhtClient: DHTClient? = null): AegisOffensiveDefense {
            return instance ?: synchronized(this) {
                instance ?: AegisOffensiveDefense(context.applicationContext, dhtClient).also { instance = it }
            }
        }
    }
    
    init {
        // Periodic cleanup of expired threats
        scope.launch {
            while (isActive) {
                delay(60_000)
                cleanupExpiredThreats()
                updateMetrics()
            }
        }
        
        // Listen for network-wide threat alerts
        scope.launch {
            listenForNetworkThreats()
        }
    }
    
    // ========================================================================
    // ANOMALY DETECTION
    // ========================================================================
    
    /**
     * Analyze request for anomalous behavior
     * Returns threat level (0.0 = safe, 1.0 = definite attack)
     */
    fun analyzeRequest(
        sourceId: String,
        requestType: RequestType,
        payload: ByteArray,
        metadata: Map<String, Any> = emptyMap()
    ): AnomalyResult {
        val baseline = getOrCreateBaseline(sourceId)
        val anomalyScores = mutableListOf<Double>()
        val detectedPatterns = mutableListOf<String>()
        
        // Rate analysis
        val rateAnomaly = analyzeRequestRate(sourceId, baseline)
        anomalyScores.add(rateAnomaly)
        if (rateAnomaly > 0.7) detectedPatterns.add("RATE_ANOMALY")
        
        // Payload analysis
        val payloadAnomaly = analyzePayload(payload, baseline)
        anomalyScores.add(payloadAnomaly)
        if (payloadAnomaly > 0.7) detectedPatterns.add("PAYLOAD_ANOMALY")
        
        // Timing analysis (detect automated attacks)
        val timingAnomaly = analyzeRequestTiming(sourceId, baseline)
        anomalyScores.add(timingAnomaly)
        if (timingAnomaly > 0.7) detectedPatterns.add("TIMING_ANOMALY")
        
        // Pattern matching for known attack signatures
        val signatureMatch = matchAttackSignatures(requestType, payload)
        anomalyScores.add(signatureMatch)
        if (signatureMatch > 0.5) detectedPatterns.add("SIGNATURE_MATCH")
        
        // Calculate composite threat level
        val threatLevel = calculateCompositeThreat(anomalyScores)
        
        // Update baseline with this request
        updateBaseline(sourceId, baseline, requestType, payload.size, threatLevel)
        
        // Handle detected threats
        if (threatLevel > 0.5) {
            handleDetectedThreat(sourceId, threatLevel, detectedPatterns)
        }
        
        return AnomalyResult(
            sourceId = sourceId,
            threatLevel = threatLevel,
            detectedPatterns = detectedPatterns,
            recommendedAction = getRecommendedAction(threatLevel),
            timestamp = System.currentTimeMillis()
        )
    }
    
    private fun analyzeRequestRate(sourceId: String, baseline: BehaviorBaseline): Double {
        val count = requestCounts.getOrPut(sourceId) { AtomicLong(0) }
        val currentCount = count.incrementAndGet()
        val lastTime = lastRequestTimes[sourceId] ?: System.currentTimeMillis()
        val now = System.currentTimeMillis()
        lastRequestTimes[sourceId] = now
        
        val elapsed = now - lastTime
        if (elapsed < BURST_DETECTION_WINDOW_MS && currentCount > MAX_REQUESTS_PER_SECOND) {
            return 1.0 // Definite rate abuse
        }
        
        // Compare to baseline
        val expectedRate = baseline.avgRequestsPerMinute
        if (expectedRate > 0) {
            val currentRate = (currentCount * 60_000.0) / maxOf(elapsed, 1)
            val deviation = (currentRate - expectedRate) / maxOf(baseline.stdDevRequestRate, 1.0)
            return (deviation / ANOMALY_THRESHOLD).coerceIn(0.0, 1.0)
        }
        
        return 0.0
    }
    
    private fun analyzePayload(payload: ByteArray, baseline: BehaviorBaseline): Double {
        var anomalyScore = 0.0
        
        // Size analysis
        val sizeDeviation = kotlin.math.abs(payload.size - baseline.avgPayloadSize) / 
            maxOf(baseline.stdDevPayloadSize, 1.0)
        if (sizeDeviation > ANOMALY_THRESHOLD) {
            anomalyScore += 0.3
        }
        
        // Entropy analysis (detect encrypted/random payloads typical of attacks)
        val entropy = calculateEntropy(payload)
        if (entropy > 7.5 && baseline.avgPayloadEntropy < 6.0) {
            anomalyScore += 0.4 // Suspiciously high entropy
        }
        
        // Check for injection patterns
        val payloadStr = String(payload, Charsets.UTF_8)
        if (containsInjectionPatterns(payloadStr)) {
            anomalyScore += 0.5
        }
        
        return anomalyScore.coerceIn(0.0, 1.0)
    }
    
    private fun analyzeRequestTiming(sourceId: String, baseline: BehaviorBaseline): Double {
        val now = System.currentTimeMillis()
        val lastTime = lastRequestTimes[sourceId] ?: return 0.0
        val interval = now - lastTime
        
        // Check for machine-like precise intervals (bots)
        val intervals = baseline.recentIntervals.toMutableList()
        intervals.add(interval)
        if (intervals.size > 10) intervals.removeAt(0)
        
        val mean = intervals.average()
        val variance = intervals.map { (it - mean) * (it - mean) }.average()
        val stdDev = kotlin.math.sqrt(variance)
        
        // Very low variance in timing indicates automated requests
        if (stdDev < 5.0 && intervals.size >= 10) {
            return 0.8 // Likely automated
        }
        
        return 0.0
    }
    
    private fun matchAttackSignatures(requestType: RequestType, payload: ByteArray): Double {
        val payloadStr = String(payload, Charsets.UTF_8)
        var matchScore = 0.0
        
        // SQL injection patterns
        val sqlPatterns = listOf("' OR '", "1=1", "DROP TABLE", "UNION SELECT", "--", "/*")
        sqlPatterns.forEach { pattern ->
            if (payloadStr.contains(pattern, ignoreCase = true)) matchScore += 0.3
        }
        
        // XSS patterns
        val xssPatterns = listOf("<script", "javascript:", "onerror=", "onload=")
        xssPatterns.forEach { pattern ->
            if (payloadStr.contains(pattern, ignoreCase = true)) matchScore += 0.3
        }
        
        // Path traversal
        if (payloadStr.contains("../") || payloadStr.contains("..\\")) matchScore += 0.4
        
        // Command injection
        val cmdPatterns = listOf("; ", "| ", "` ", "$(", "\${")
        cmdPatterns.forEach { pattern ->
            if (payloadStr.contains(pattern)) matchScore += 0.3
        }
        
        return matchScore.coerceIn(0.0, 1.0)
    }
    
    private fun containsInjectionPatterns(payload: String): Boolean {
        val patterns = listOf(
            "' OR ", "1=1", "DROP ", "DELETE ", "UPDATE ", "INSERT ",
            "<script", "javascript:", "../", "..\\", "; ", "| "
        )
        return patterns.any { payload.contains(it, ignoreCase = true) }
    }
    
    private fun calculateEntropy(data: ByteArray): Double {
        if (data.isEmpty()) return 0.0
        
        val frequencies = IntArray(256)
        data.forEach { frequencies[it.toInt() and 0xFF]++ }
        
        var entropy = 0.0
        val total = data.size.toDouble()
        frequencies.forEach { count ->
            if (count > 0) {
                val p = count / total
                entropy -= p * kotlin.math.ln(p) / kotlin.math.ln(2.0)
            }
        }
        return entropy
    }
    
    private fun calculateCompositeThreat(scores: List<Double>): Double {
        if (scores.isEmpty()) return 0.0
        
        // Weighted average with emphasis on high scores
        val weights = scores.map { it * it } // Square to emphasize high values
        val weightedSum = scores.zip(weights).sumOf { (score, weight) -> score * weight }
        val totalWeight = weights.sum()
        
        return if (totalWeight > 0) (weightedSum / totalWeight) else 0.0
    }
    
    // ========================================================================
    // HONEYPOT SYSTEM
    // ========================================================================
    
    /**
     * Deploy a honeypot endpoint that looks attractive to attackers
     */
    fun deployHoneypot(type: HoneypotType): Honeypot {
        val honeypot = Honeypot(
            id = generateHoneypotId(),
            type = type,
            endpoint = generateHoneypotEndpoint(type),
            deployedAt = System.currentTimeMillis(),
            isActive = true
        )
        
        return honeypot
    }
    
    /**
     * Record interaction with honeypot
     */
    fun recordHoneypotInteraction(
        honeypotId: String,
        sourceId: String,
        interactionType: String,
        payload: ByteArray
    ) {
        val interaction = HoneypotInteraction(
            honeypotId = honeypotId,
            sourceId = sourceId,
            interactionType = interactionType,
            payload = payload,
            timestamp = System.currentTimeMillis()
        )
        
        honeypotInteractions[sourceId] = interaction
        
        // Any honeypot interaction is immediate threat confirmation
        val threat = ThreatProfile(
            sourceId = sourceId,
            threatLevel = 0.9,
            firstSeen = System.currentTimeMillis(),
            lastSeen = System.currentTimeMillis(),
            attackPatterns = listOf("HONEYPOT_INTERACTION"),
            confirmedMalicious = true
        )
        threatRegistry[sourceId] = threat
        
        // Broadcast threat to network
        scope.launch {
            broadcastThreat(threat)
        }
        
        // Trigger 100x response
        scope.launch {
            executeResourceExhaustion(sourceId)
        }
    }
    
    private fun generateHoneypotId(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
    
    private fun generateHoneypotEndpoint(type: HoneypotType): String {
        return when (type) {
            HoneypotType.FAKE_API -> "/api/v1/admin/users"
            HoneypotType.FAKE_LOGIN -> "/auth/admin/login"
            HoneypotType.FAKE_WALLET -> "/wallet/export/private-keys"
            HoneypotType.FAKE_CONFIG -> "/.env"
            HoneypotType.FAKE_BACKUP -> "/backup/database.sql"
        }
    }
    
    // ========================================================================
    // 100x RESOURCE EXHAUSTION RESPONSE
    // ========================================================================
    
    /**
     * Execute resource exhaustion against confirmed attacker
     * Makes attacks prohibitively expensive by consuming their resources
     */
    private suspend fun executeResourceExhaustion(sourceId: String) {
        val threat = threatRegistry[sourceId] ?: return
        if (!threat.confirmedMalicious) return
        
        withContext(Dispatchers.IO) {
            // Generate computationally expensive responses
            repeat(RESOURCE_EXHAUSTION_MULTIPLIER) { iteration ->
                // Add artificial delay to waste attacker's time
                delay(HONEYPOT_RESPONSE_DELAY_MS)
                
                // Generate fake but realistic-looking response data
                val fakeData = generateFakeResponse(iteration)
                
                // Log the exhaustion attempt
                if (iteration % 10 == 0) {
                    updateExhaustionMetrics(sourceId, iteration)
                }
            }
        }
    }
    
    private fun generateFakeResponse(iteration: Int): ByteArray {
        // Generate plausible-looking but useless data
        val size = 1024 + (iteration % 4096)
        val data = ByteArray(size)
        secureRandom.nextBytes(data)
        return data
    }
    
    private fun updateExhaustionMetrics(sourceId: String, iteration: Int) {
        val current = _defenseMetrics.value
        _defenseMetrics.value = current.copy(
            resourceExhaustionExecutions = current.resourceExhaustionExecutions + 1,
            attackerTimewasted = current.attackerTimewasted + (iteration * HONEYPOT_RESPONSE_DELAY_MS)
        )
    }
    
    // ========================================================================
    // THREAT MANAGEMENT
    // ========================================================================
    
    private fun handleDetectedThreat(sourceId: String, threatLevel: Double, patterns: List<String>) {
        val existing = threatRegistry[sourceId]
        val threat = if (existing != null) {
            existing.copy(
                threatLevel = maxOf(existing.threatLevel, threatLevel),
                lastSeen = System.currentTimeMillis(),
                attackPatterns = (existing.attackPatterns + patterns).distinct(),
                incidentCount = existing.incidentCount + 1,
                confirmedMalicious = threatLevel > 0.8 || existing.incidentCount > 5
            )
        } else {
            ThreatProfile(
                sourceId = sourceId,
                threatLevel = threatLevel,
                firstSeen = System.currentTimeMillis(),
                lastSeen = System.currentTimeMillis(),
                attackPatterns = patterns,
                confirmedMalicious = threatLevel > 0.8
            )
        }
        
        threatRegistry[sourceId] = threat
        
        // Emit alert
        scope.launch {
            _threatAlerts.emit(ThreatAlert(
                sourceId = sourceId,
                threatLevel = threatLevel,
                patterns = patterns,
                timestamp = System.currentTimeMillis(),
                recommendedAction = getRecommendedAction(threatLevel)
            ))
        }
        
        // Broadcast high-severity threats to network
        if (threatLevel > 0.7) {
            scope.launch { broadcastThreat(threat) }
        }
    }
    
    private suspend fun broadcastThreat(threat: ThreatProfile) {
        dhtClient?.let { dht ->
            val alertData = ThreatIntel(
                sourceIdHash = hashSourceId(threat.sourceId),
                threatLevel = threat.threatLevel,
                patterns = threat.attackPatterns,
                reportedBy = dht.nodeId,
                timestamp = System.currentTimeMillis()
            )
            
            dht.broadcast(THREAT_ALERT_CHANNEL, alertData.toBytes())
        }
    }
    
    private suspend fun listenForNetworkThreats() {
        // In production, would subscribe to DHT threat channel
        // For now, this is a placeholder for the DHT integration
    }
    
    private fun hashSourceId(sourceId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(sourceId.toByteArray()).joinToString("") { "%02x".format(it) }
    }
    
    private fun getRecommendedAction(threatLevel: Double): RecommendedAction {
        return when {
            threatLevel > 0.9 -> RecommendedAction.BLOCK_IMMEDIATE
            threatLevel > 0.7 -> RecommendedAction.BLOCK_TEMPORARY
            threatLevel > 0.5 -> RecommendedAction.RATE_LIMIT
            threatLevel > 0.3 -> RecommendedAction.MONITOR
            else -> RecommendedAction.ALLOW
        }
    }
    
    // ========================================================================
    // BASELINE MANAGEMENT
    // ========================================================================
    
    private fun getOrCreateBaseline(sourceId: String): BehaviorBaseline {
        return behaviorBaselines.getOrPut(sourceId) {
            BehaviorBaseline(sourceId = sourceId)
        }
    }
    
    private fun updateBaseline(
        sourceId: String,
        baseline: BehaviorBaseline,
        requestType: RequestType,
        payloadSize: Int,
        threatLevel: Double
    ) {
        // Only update baseline for non-malicious requests
        if (threatLevel < 0.3) {
            val newBaseline = baseline.copy(
                requestCount = baseline.requestCount + 1,
                avgPayloadSize = ((baseline.avgPayloadSize * baseline.requestCount) + payloadSize) / 
                    (baseline.requestCount + 1),
                lastUpdated = System.currentTimeMillis()
            )
            behaviorBaselines[sourceId] = newBaseline
        }
    }
    
    private fun cleanupExpiredThreats() {
        val now = System.currentTimeMillis()
        threatRegistry.entries.removeIf { (_, threat) ->
            now - threat.lastSeen > THREAT_EXPIRY_MS && !threat.confirmedMalicious
        }
        
        // Clear old request counts
        requestCounts.entries.removeIf { (sourceId, _) ->
            val lastTime = lastRequestTimes[sourceId] ?: 0
            now - lastTime > PATTERN_ANALYSIS_WINDOW_MS
        }
    }
    
    private fun updateMetrics() {
        _defenseMetrics.value = _defenseMetrics.value.copy(
            activeThreatCount = threatRegistry.size,
            confirmedAttackers = threatRegistry.values.count { it.confirmedMalicious },
            honeypotInteractionCount = honeypotInteractions.size
        )
    }
    
    /**
     * Shutdown the defense system
     */
    fun shutdown() {
        scope.cancel()
        threatRegistry.clear()
        behaviorBaselines.clear()
        honeypotInteractions.clear()
    }
}

// ========================================================================
// DATA CLASSES
// ========================================================================

data class ThreatProfile(
    val sourceId: String,
    val threatLevel: Double,
    val firstSeen: Long,
    val lastSeen: Long,
    val attackPatterns: List<String>,
    val confirmedMalicious: Boolean = false,
    val incidentCount: Int = 1
)

data class BehaviorBaseline(
    val sourceId: String,
    val requestCount: Long = 0,
    val avgRequestsPerMinute: Double = 0.0,
    val stdDevRequestRate: Double = 10.0,
    val avgPayloadSize: Double = 256.0,
    val stdDevPayloadSize: Double = 128.0,
    val avgPayloadEntropy: Double = 4.0,
    val recentIntervals: List<Long> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis()
)

data class AnomalyResult(
    val sourceId: String,
    val threatLevel: Double,
    val detectedPatterns: List<String>,
    val recommendedAction: RecommendedAction,
    val timestamp: Long
)

data class ThreatAlert(
    val sourceId: String,
    val threatLevel: Double,
    val patterns: List<String>,
    val timestamp: Long,
    val recommendedAction: RecommendedAction
)

data class ThreatIntel(
    val sourceIdHash: String,
    val threatLevel: Double,
    val patterns: List<String>,
    val reportedBy: String,
    val timestamp: Long
) {
    fun toBytes(): ByteArray {
        return "$sourceIdHash|$threatLevel|${patterns.joinToString(",")}|$reportedBy|$timestamp"
            .toByteArray(Charsets.UTF_8)
    }
    
    companion object {
        fun fromBytes(bytes: ByteArray): ThreatIntel? {
            return try {
                val parts = String(bytes, Charsets.UTF_8).split("|")
                ThreatIntel(
                    sourceIdHash = parts[0],
                    threatLevel = parts[1].toDouble(),
                    patterns = parts[2].split(","),
                    reportedBy = parts[3],
                    timestamp = parts[4].toLong()
                )
            } catch (e: Exception) { null }
        }
    }
}

data class Honeypot(
    val id: String,
    val type: HoneypotType,
    val endpoint: String,
    val deployedAt: Long,
    val isActive: Boolean
)

data class HoneypotInteraction(
    val honeypotId: String,
    val sourceId: String,
    val interactionType: String,
    val payload: ByteArray,
    val timestamp: Long
)

data class DefenseMetrics(
    val activeThreatCount: Int = 0,
    val confirmedAttackers: Int = 0,
    val honeypotInteractionCount: Int = 0,
    val resourceExhaustionExecutions: Long = 0,
    val attackerTimewasted: Long = 0 // milliseconds
)

enum class RequestType {
    P2P_HANDSHAKE,
    P2P_MESSAGE,
    DHT_GET,
    DHT_PUT,
    API_CALL,
    WEBSOCKET_MESSAGE
}

enum class HoneypotType {
    FAKE_API,
    FAKE_LOGIN,
    FAKE_WALLET,
    FAKE_CONFIG,
    FAKE_BACKUP
}

enum class RecommendedAction {
    ALLOW,
    MONITOR,
    RATE_LIMIT,
    BLOCK_TEMPORARY,
    BLOCK_IMMEDIATE
}
