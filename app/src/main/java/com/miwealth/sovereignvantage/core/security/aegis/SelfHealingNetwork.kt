package com.miwealth.sovereignvantage.core.security.aegis

import android.content.Context
import com.miwealth.sovereignvantage.core.dht.DHTClient
import com.miwealth.sovereignvantage.core.dht.DHTNode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.exp
import kotlin.math.min

/**
 * SOVEREIGN VANTAGE V5.17.0 "ARTHUR EDITION"
 * SELF-HEALING NETWORK SYSTEM
 * 
 * Provides automatic network resilience and recovery:
 * - Automatic breach detection with anomaly scoring
 * - Dynamic path rerouting around compromised nodes
 * - Tunnel rebuilding with exponential backoff
 * - Redundant failover connections
 * - Health score monitoring per node and path
 * - Automatic quarantine and recovery of degraded components
 * 
 * Philosophy: "The network heals itself faster than attackers can damage it."
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 */
class SelfHealingNetwork private constructor(
    private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Node health tracking
    private val nodeHealth = ConcurrentHashMap<String, NodeHealthStatus>()
    private val pathHealth = ConcurrentHashMap<String, PathHealthStatus>()
    
    // Failover management
    private val primaryConnections = ConcurrentHashMap<String, ConnectionInfo>()
    private val backupConnections = ConcurrentHashMap<String, MutableList<ConnectionInfo>>()
    private val failoverAttempts = ConcurrentHashMap<String, AtomicInteger>()
    
    // Quarantine
    private val quarantinedNodes = ConcurrentHashMap<String, QuarantineEntry>()
    
    // Recovery state
    private val recoveryTasks = ConcurrentHashMap<String, Job>()
    private val tunnelRebuildQueue = MutableSharedFlow<TunnelRebuildRequest>(replay = 20)
    
    // Metrics and alerts
    private val _networkHealth = MutableStateFlow(NetworkHealthStatus())
    val networkHealth: StateFlow<NetworkHealthStatus> = _networkHealth.asStateFlow()
    
    private val _healingEvents = MutableSharedFlow<HealingEvent>(replay = 50)
    val healingEvents: SharedFlow<HealingEvent> = _healingEvents.asSharedFlow()
    
    // External dependencies (injected)
    private var dhtClient: DHTClient? = null
    private var tunnelDefense: AegisDHTTunnelDefense? = null
    
    companion object {
        @Volatile
        private var instance: SelfHealingNetwork? = null
        
        // Health thresholds
        const val HEALTH_CRITICAL = 20.0
        const val HEALTH_DEGRADED = 50.0
        const val HEALTH_HEALTHY = 80.0
        const val HEALTH_OPTIMAL = 95.0
        
        // Recovery settings
        const val MAX_FAILOVER_ATTEMPTS = 5
        const val BASE_RETRY_DELAY_MS = 1000L
        const val MAX_RETRY_DELAY_MS = 60_000L
        const val QUARANTINE_DURATION_MS = 300_000L // 5 minutes
        const val HEALTH_CHECK_INTERVAL_MS = 10_000L
        const val REDUNDANCY_FACTOR = 3 // Maintain 3x backup connections
        
        // Anomaly detection
        const val LATENCY_SPIKE_THRESHOLD = 3.0 // 3x normal latency
        const val ERROR_RATE_THRESHOLD = 0.1 // 10% error rate
        const val PACKET_LOSS_THRESHOLD = 0.05 // 5% packet loss
        
        fun getInstance(context: Context): SelfHealingNetwork {
            return instance ?: synchronized(this) {
                instance ?: SelfHealingNetwork(context.applicationContext).also { instance = it }
            }
        }
    }
    
    init {
        // Periodic health monitoring
        scope.launch {
            while (isActive) {
                delay(HEALTH_CHECK_INTERVAL_MS)
                performHealthCheck()
            }
        }
        
        // Process tunnel rebuild queue
        scope.launch {
            tunnelRebuildQueue.collect { request ->
                processTunnelRebuild(request)
            }
        }
        
        // Quarantine expiry checker
        scope.launch {
            while (isActive) {
                delay(60_000)
                processQuarantineExpiry()
            }
        }
    }
    
    /**
     * Initialize with external dependencies
     */
    fun initialize(dhtClient: DHTClient?, tunnelDefense: AegisDHTTunnelDefense?) {
        this.dhtClient = dhtClient
        this.tunnelDefense = tunnelDefense
    }
    
    // ========================================================================
    // BREACH DETECTION
    // ========================================================================
    
    /**
     * Detect potential breach based on node behavior anomalies
     */
    fun detectBreach(nodeId: String, metrics: NodeMetrics): BreachDetectionResult {
        val health = nodeHealth[nodeId] ?: NodeHealthStatus(nodeId = nodeId)
        val anomalies = mutableListOf<AnomalyType>()
        var breachProbability = 0.0
        
        // Check latency anomaly
        if (health.avgLatency > 0 && metrics.latencyMs > health.avgLatency * LATENCY_SPIKE_THRESHOLD) {
            anomalies.add(AnomalyType.LATENCY_SPIKE)
            breachProbability += 0.2
        }
        
        // Check error rate
        val errorRate = if (metrics.totalRequests > 0) {
            metrics.failedRequests.toDouble() / metrics.totalRequests
        } else 0.0
        
        if (errorRate > ERROR_RATE_THRESHOLD) {
            anomalies.add(AnomalyType.HIGH_ERROR_RATE)
            breachProbability += 0.3
        }
        
        // Check packet loss
        val packetLoss = if (metrics.packetsSent > 0) {
            1.0 - (metrics.packetsReceived.toDouble() / metrics.packetsSent)
        } else 0.0
        
        if (packetLoss > PACKET_LOSS_THRESHOLD) {
            anomalies.add(AnomalyType.PACKET_LOSS)
            breachProbability += 0.25
        }
        
        // Check for sudden behavior change
        if (detectBehaviorChange(nodeId, metrics)) {
            anomalies.add(AnomalyType.BEHAVIOR_CHANGE)
            breachProbability += 0.35
        }
        
        // Check for communication pattern anomaly
        if (detectCommunicationAnomaly(nodeId, metrics)) {
            anomalies.add(AnomalyType.COMMUNICATION_ANOMALY)
            breachProbability += 0.25
        }
        
        // Update node health
        val newHealthScore = calculateHealthScore(metrics, anomalies)
        updateNodeHealth(nodeId, newHealthScore, metrics)
        
        val result = BreachDetectionResult(
            nodeId = nodeId,
            breachProbability = breachProbability.coerceIn(0.0, 1.0),
            detectedAnomalies = anomalies,
            currentHealth = newHealthScore,
            recommendedAction = determineAction(breachProbability, newHealthScore)
        )
        
        // Trigger healing if needed
        if (result.recommendedAction != HealingAction.NONE) {
            scope.launch {
                executeHealingAction(nodeId, result.recommendedAction, anomalies)
            }
        }
        
        return result
    }
    
    private fun detectBehaviorChange(nodeId: String, metrics: NodeMetrics): Boolean {
        val health = nodeHealth[nodeId] ?: return false
        
        // Compare current behavior to historical baseline
        val latencyDelta = kotlin.math.abs(metrics.latencyMs - health.avgLatency) / maxOf(health.avgLatency, 1.0)
        val throughputDelta = if (health.avgThroughput > 0) {
            kotlin.math.abs(metrics.throughput - health.avgThroughput) / health.avgThroughput
        } else 0.0
        
        return latencyDelta > 0.5 || throughputDelta > 0.5
    }
    
    private fun detectCommunicationAnomaly(nodeId: String, metrics: NodeMetrics): Boolean {
        // Detect unusual communication patterns
        val health = nodeHealth[nodeId] ?: return false
        
        // Check for unusual request patterns
        val requestRateChange = if (health.avgRequestRate > 0) {
            kotlin.math.abs(metrics.requestRate - health.avgRequestRate) / health.avgRequestRate
        } else 0.0
        
        return requestRateChange > 2.0 // 200% change in request rate
    }
    
    private fun calculateHealthScore(metrics: NodeMetrics, anomalies: List<AnomalyType>): Double {
        var score = 100.0
        
        // Deduct for anomalies
        anomalies.forEach { anomaly ->
            score -= when (anomaly) {
                AnomalyType.LATENCY_SPIKE -> 15.0
                AnomalyType.HIGH_ERROR_RATE -> 25.0
                AnomalyType.PACKET_LOSS -> 20.0
                AnomalyType.BEHAVIOR_CHANGE -> 20.0
                AnomalyType.COMMUNICATION_ANOMALY -> 15.0
            }
        }
        
        // Deduct for high latency
        if (metrics.latencyMs > 1000) {
            score -= min(20.0, metrics.latencyMs / 100.0)
        }
        
        return score.coerceIn(0.0, 100.0)
    }
    
    private fun determineAction(breachProbability: Double, healthScore: Double): HealingAction {
        return when {
            breachProbability > 0.8 || healthScore < HEALTH_CRITICAL -> HealingAction.QUARANTINE_AND_REROUTE
            breachProbability > 0.5 || healthScore < HEALTH_DEGRADED -> HealingAction.FAILOVER
            breachProbability > 0.3 -> HealingAction.MONITOR_CLOSELY
            healthScore < HEALTH_HEALTHY -> HealingAction.INITIATE_RECOVERY
            else -> HealingAction.NONE
        }
    }
    
    // ========================================================================
    // PATH REROUTING
    // ========================================================================
    
    /**
     * Reroute traffic around compromised or degraded node
     */
    suspend fun rerouteAroundNode(nodeId: String, reason: String): RerouteResult {
        val affectedPaths = pathHealth.entries.filter { (pathId, _) ->
            pathId.contains(nodeId)
        }
        
        if (affectedPaths.isEmpty()) {
            return RerouteResult(
                success = true,
                reroutedPaths = 0,
                newPaths = emptyList(),
                failedReroutes = emptyList()
            )
        }
        
        val reroutedPaths = mutableListOf<PathInfo>()
        val failedReroutes = mutableListOf<String>()
        
        for ((pathId, pathStatus) in affectedPaths) {
            try {
                val newPath = findAlternativePath(pathId, nodeId)
                if (newPath != null) {
                    // Update path registry
                    pathHealth[newPath.pathId] = PathHealthStatus(
                        pathId = newPath.pathId,
                        healthScore = 100.0,
                        lastChecked = System.currentTimeMillis()
                    )
                    pathHealth.remove(pathId)
                    reroutedPaths.add(newPath)
                    
                    emitHealingEvent(HealingEvent(
                        type = HealingEventType.PATH_REROUTED,
                        nodeId = nodeId,
                        details = "Rerouted path $pathId to ${newPath.pathId}",
                        success = true,
                        timestamp = System.currentTimeMillis()
                    ))
                } else {
                    failedReroutes.add(pathId)
                }
            } catch (e: Exception) {
                failedReroutes.add(pathId)
            }
        }
        
        return RerouteResult(
            success = failedReroutes.isEmpty(),
            reroutedPaths = reroutedPaths.size,
            newPaths = reroutedPaths,
            failedReroutes = failedReroutes
        )
    }
    
    private suspend fun findAlternativePath(currentPathId: String, excludeNodeId: String): PathInfo? {
        // Parse current path to get source and destination
        val parts = currentPathId.split("->")
        if (parts.size < 2) return null
        
        val source = parts.first()
        val destination = parts.last()
        
        // Find healthy nodes that could serve as intermediate hops
        val healthyNodes = nodeHealth.entries
            .filter { (id, status) -> 
                id != excludeNodeId && 
                status.healthScore > HEALTH_HEALTHY &&
                !quarantinedNodes.containsKey(id)
            }
            .sortedByDescending { it.value.healthScore }
            .take(5)
            .map { it.key }
        
        if (healthyNodes.isEmpty()) {
            // Direct path without intermediates
            return PathInfo(
                pathId = "$source->$destination",
                nodes = listOf(source, destination),
                latency = 0.0,
                establishedAt = System.currentTimeMillis()
            )
        }
        
        // Select best intermediate node
        val intermediateNode = healthyNodes.first()
        val newPathId = "$source->$intermediateNode->$destination"
        
        return PathInfo(
            pathId = newPathId,
            nodes = listOf(source, intermediateNode, destination),
            latency = 0.0,
            establishedAt = System.currentTimeMillis()
        )
    }
    
    // ========================================================================
    // TUNNEL REBUILDING
    // ========================================================================
    
    /**
     * Request tunnel rebuild with exponential backoff
     */
    fun requestTunnelRebuild(tunnelId: String, reason: String) {
        scope.launch {
            tunnelRebuildQueue.emit(TunnelRebuildRequest(
                tunnelId = tunnelId,
                reason = reason,
                requestTime = System.currentTimeMillis(),
                attemptNumber = (failoverAttempts[tunnelId]?.get() ?: 0) + 1
            ))
        }
    }
    
    private suspend fun processTunnelRebuild(request: TunnelRebuildRequest) {
        val attemptCount = failoverAttempts.getOrPut(request.tunnelId) { AtomicInteger(0) }
        val attempt = attemptCount.incrementAndGet()
        
        if (attempt > MAX_FAILOVER_ATTEMPTS) {
            emitHealingEvent(HealingEvent(
                type = HealingEventType.TUNNEL_REBUILD_FAILED,
                nodeId = request.tunnelId,
                details = "Max rebuild attempts ($MAX_FAILOVER_ATTEMPTS) exceeded",
                success = false,
                timestamp = System.currentTimeMillis()
            ))
            return
        }
        
        // Calculate backoff delay
        val delayMs = calculateBackoffDelay(attempt)
        
        emitHealingEvent(HealingEvent(
            type = HealingEventType.TUNNEL_REBUILD_STARTED,
            nodeId = request.tunnelId,
            details = "Attempt $attempt, delay ${delayMs}ms",
            success = true,
            timestamp = System.currentTimeMillis()
        ))
        
        delay(delayMs)
        
        // Attempt rebuild
        val success = attemptTunnelRebuild(request.tunnelId)
        
        if (success) {
            attemptCount.set(0) // Reset on success
            emitHealingEvent(HealingEvent(
                type = HealingEventType.TUNNEL_REBUILD_SUCCESS,
                nodeId = request.tunnelId,
                details = "Tunnel rebuilt after $attempt attempts",
                success = true,
                timestamp = System.currentTimeMillis()
            ))
        } else {
            // Re-queue for another attempt
            tunnelRebuildQueue.emit(request.copy(
                attemptNumber = attempt + 1,
                requestTime = System.currentTimeMillis()
            ))
        }
    }
    
    private fun calculateBackoffDelay(attempt: Int): Long {
        val exponentialDelay = BASE_RETRY_DELAY_MS * (1L shl min(attempt - 1, 6))
        val jitter = (Math.random() * 0.3 * exponentialDelay).toLong()
        return min(exponentialDelay + jitter, MAX_RETRY_DELAY_MS)
    }
    
    private suspend fun attemptTunnelRebuild(tunnelId: String): Boolean {
        // In production, this would reconnect to DHT and rebuild secure channel
        // For now, simulate success/failure
        return try {
            // Check if we have backup connections
            val backups = backupConnections[tunnelId]
            if (!backups.isNullOrEmpty()) {
                val backup = backups.removeFirst()
                primaryConnections[tunnelId] = backup
                true
            } else {
                // Attempt to establish new connection
                establishNewConnection(tunnelId)
            }
        } catch (e: Exception) {
            false
        }
    }
    
    private suspend fun establishNewConnection(tunnelId: String): Boolean {
        // Would use DHTClient to find and connect to peers
        // Placeholder for actual implementation
        delay(100) // Simulate connection attempt
        return Math.random() > 0.3 // 70% success rate
    }
    
    // ========================================================================
    // REDUNDANT FAILOVER
    // ========================================================================
    
    /**
     * Establish redundant backup connections for critical paths
     */
    suspend fun establishRedundantConnections(primaryNodeId: String): RedundancyResult {
        val existingBackups = backupConnections[primaryNodeId]?.size ?: 0
        val neededBackups = REDUNDANCY_FACTOR - existingBackups
        
        if (neededBackups <= 0) {
            return RedundancyResult(
                nodeId = primaryNodeId,
                totalBackups = existingBackups,
                newBackupsEstablished = 0,
                success = true
            )
        }
        
        val newBackups = mutableListOf<ConnectionInfo>()
        
        repeat(neededBackups) { index ->
            try {
                val backupConnection = findAndConnectBackupNode(primaryNodeId, index)
                if (backupConnection != null) {
                    newBackups.add(backupConnection)
                }
            } catch (e: Exception) {
                // Log but continue trying other backups
            }
        }
        
        // Store backup connections
        backupConnections.getOrPut(primaryNodeId) { mutableListOf() }.addAll(newBackups)
        
        return RedundancyResult(
            nodeId = primaryNodeId,
            totalBackups = existingBackups + newBackups.size,
            newBackupsEstablished = newBackups.size,
            success = newBackups.size >= neededBackups / 2
        )
    }
    
    private suspend fun findAndConnectBackupNode(primaryNodeId: String, index: Int): ConnectionInfo? {
        // Would use DHTClient to find diverse backup nodes
        // Prioritize nodes with different network characteristics
        val healthyNodes = nodeHealth.entries
            .filter { (id, status) ->
                id != primaryNodeId &&
                status.healthScore > HEALTH_HEALTHY &&
                !quarantinedNodes.containsKey(id)
            }
            .sortedByDescending { it.value.healthScore }
        
        return healthyNodes.getOrNull(index)?.let { (nodeId, status) ->
            ConnectionInfo(
                nodeId = nodeId,
                address = "0.0.0.0", // Would be actual address
                port = 42069,
                establishedAt = System.currentTimeMillis(),
                healthScore = status.healthScore
            )
        }
    }
    
    /**
     * Execute immediate failover to backup connection
     */
    suspend fun executeFailover(nodeId: String): FailoverResult {
        val backups = backupConnections[nodeId]
        
        if (backups.isNullOrEmpty()) {
            return FailoverResult(
                nodeId = nodeId,
                success = false,
                newConnectionId = null,
                failoverTime = 0
            )
        }
        
        val startTime = System.currentTimeMillis()
        
        // Find healthiest backup
        val bestBackup = backups.maxByOrNull { it.healthScore }
        
        if (bestBackup != null) {
            backups.remove(bestBackup)
            primaryConnections[nodeId] = bestBackup
            
            val failoverTime = System.currentTimeMillis() - startTime
            
            emitHealingEvent(HealingEvent(
                type = HealingEventType.FAILOVER_EXECUTED,
                nodeId = nodeId,
                details = "Failed over to ${bestBackup.nodeId} in ${failoverTime}ms",
                success = true,
                timestamp = System.currentTimeMillis()
            ))
            
            // Replenish backup pool asynchronously
            scope.launch {
                establishRedundantConnections(nodeId)
            }
            
            return FailoverResult(
                nodeId = nodeId,
                success = true,
                newConnectionId = bestBackup.nodeId,
                failoverTime = failoverTime
            )
        }
        
        return FailoverResult(
            nodeId = nodeId,
            success = false,
            newConnectionId = null,
            failoverTime = 0
        )
    }
    
    // ========================================================================
    // HEALTH MONITORING
    // ========================================================================
    
    private fun updateNodeHealth(nodeId: String, healthScore: Double, metrics: NodeMetrics) {
        val existing = nodeHealth[nodeId]
        val updated = if (existing != null) {
            existing.copy(
                healthScore = (existing.healthScore * 0.7 + healthScore * 0.3), // Smoothed
                avgLatency = (existing.avgLatency * 0.8 + metrics.latencyMs * 0.2),
                avgThroughput = (existing.avgThroughput * 0.8 + metrics.throughput * 0.2),
                avgRequestRate = (existing.avgRequestRate * 0.8 + metrics.requestRate * 0.2),
                lastChecked = System.currentTimeMillis(),
                consecutiveFailures = if (healthScore < HEALTH_DEGRADED) existing.consecutiveFailures + 1 else 0
            )
        } else {
            NodeHealthStatus(
                nodeId = nodeId,
                healthScore = healthScore,
                avgLatency = metrics.latencyMs,
                avgThroughput = metrics.throughput,
                avgRequestRate = metrics.requestRate,
                lastChecked = System.currentTimeMillis()
            )
        }
        
        nodeHealth[nodeId] = updated
    }
    
    private fun performHealthCheck() {
        val now = System.currentTimeMillis()
        var totalHealth = 0.0
        var nodeCount = 0
        var criticalCount = 0
        var degradedCount = 0
        
        nodeHealth.forEach { (nodeId, status) ->
            totalHealth += status.healthScore
            nodeCount++
            
            when {
                status.healthScore < HEALTH_CRITICAL -> {
                    criticalCount++
                    // Auto-quarantine critically unhealthy nodes
                    if (status.consecutiveFailures > 3) {
                        quarantineNode(nodeId, "Auto-quarantine: critical health")
                    }
                }
                status.healthScore < HEALTH_DEGRADED -> degradedCount++
            }
        }
        
        val avgHealth = if (nodeCount > 0) totalHealth / nodeCount else 100.0
        
        _networkHealth.value = NetworkHealthStatus(
            overallHealth = avgHealth,
            totalNodes = nodeCount,
            healthyNodes = nodeCount - criticalCount - degradedCount,
            degradedNodes = degradedCount,
            criticalNodes = criticalCount,
            quarantinedNodes = quarantinedNodes.size,
            activeRecoveryTasks = recoveryTasks.count { it.value.isActive },
            lastChecked = now
        )
    }
    
    // ========================================================================
    // QUARANTINE MANAGEMENT
    // ========================================================================
    
    /**
     * Quarantine a node to prevent further damage
     */
    fun quarantineNode(nodeId: String, reason: String) {
        quarantinedNodes[nodeId] = QuarantineEntry(
            nodeId = nodeId,
            reason = reason,
            quarantinedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + QUARANTINE_DURATION_MS
        )
        
        // Update reputation if tunnel defense is available
        tunnelDefense?.decreasePeerReputation(nodeId, 25.0, "Quarantined: $reason")
        
        emitHealingEvent(HealingEvent(
            type = HealingEventType.NODE_QUARANTINED,
            nodeId = nodeId,
            details = reason,
            success = true,
            timestamp = System.currentTimeMillis()
        ))
        
        // Trigger rerouting
        scope.launch {
            rerouteAroundNode(nodeId, "Quarantined: $reason")
        }
    }
    
    /**
     * Release node from quarantine
     */
    fun releaseFromQuarantine(nodeId: String) {
        quarantinedNodes.remove(nodeId)
        
        emitHealingEvent(HealingEvent(
            type = HealingEventType.NODE_RELEASED,
            nodeId = nodeId,
            details = "Released from quarantine",
            success = true,
            timestamp = System.currentTimeMillis()
        ))
    }
    
    private fun processQuarantineExpiry() {
        val now = System.currentTimeMillis()
        quarantinedNodes.entries.removeIf { (nodeId, entry) ->
            if (now > entry.expiresAt) {
                emitHealingEvent(HealingEvent(
                    type = HealingEventType.NODE_RELEASED,
                    nodeId = nodeId,
                    details = "Quarantine expired",
                    success = true,
                    timestamp = now
                ))
                true
            } else {
                false
            }
        }
    }
    
    // ========================================================================
    // HEALING ACTION EXECUTION
    // ========================================================================
    
    private suspend fun executeHealingAction(
        nodeId: String,
        action: HealingAction,
        anomalies: List<AnomalyType>
    ) {
        when (action) {
            HealingAction.QUARANTINE_AND_REROUTE -> {
                quarantineNode(nodeId, "Automatic: ${anomalies.joinToString()}")
                rerouteAroundNode(nodeId, "Quarantined")
            }
            HealingAction.FAILOVER -> {
                executeFailover(nodeId)
            }
            HealingAction.INITIATE_RECOVERY -> {
                requestTunnelRebuild(nodeId, "Health degraded: ${anomalies.joinToString()}")
            }
            HealingAction.MONITOR_CLOSELY -> {
                // Just emit event for monitoring
                emitHealingEvent(HealingEvent(
                    type = HealingEventType.MONITORING_INCREASED,
                    nodeId = nodeId,
                    details = "Anomalies detected: ${anomalies.joinToString()}",
                    success = true,
                    timestamp = System.currentTimeMillis()
                ))
            }
            HealingAction.NONE -> { /* No action needed */ }
        }
    }
    
    private fun emitHealingEvent(event: HealingEvent) {
        scope.launch {
            _healingEvents.emit(event)
        }
    }
    
    /**
     * Shutdown the self-healing network
     */
    fun shutdown() {
        scope.cancel()
        nodeHealth.clear()
        pathHealth.clear()
        primaryConnections.clear()
        backupConnections.clear()
        quarantinedNodes.clear()
        recoveryTasks.values.forEach { it.cancel() }
        recoveryTasks.clear()
    }
}

// ========================================================================
// DATA CLASSES
// ========================================================================

data class NodeHealthStatus(
    val nodeId: String,
    val healthScore: Double = 100.0,
    val avgLatency: Double = 0.0,
    val avgThroughput: Double = 0.0,
    val avgRequestRate: Double = 0.0,
    val lastChecked: Long = System.currentTimeMillis(),
    val consecutiveFailures: Int = 0
)

data class PathHealthStatus(
    val pathId: String,
    val healthScore: Double = 100.0,
    val lastChecked: Long = System.currentTimeMillis()
)

data class NodeMetrics(
    val latencyMs: Double,
    val throughput: Double,
    val totalRequests: Int,
    val failedRequests: Int,
    val packetsSent: Int,
    val packetsReceived: Int,
    val requestRate: Double
)

data class PathInfo(
    val pathId: String,
    val nodes: List<String>,
    val latency: Double,
    val establishedAt: Long
)

data class ConnectionInfo(
    val nodeId: String,
    val address: String,
    val port: Int,
    val establishedAt: Long,
    val healthScore: Double
)

data class QuarantineEntry(
    val nodeId: String,
    val reason: String,
    val quarantinedAt: Long,
    val expiresAt: Long
)

data class TunnelRebuildRequest(
    val tunnelId: String,
    val reason: String,
    val requestTime: Long,
    val attemptNumber: Int
)

data class NetworkHealthStatus(
    val overallHealth: Double = 100.0,
    val totalNodes: Int = 0,
    val healthyNodes: Int = 0,
    val degradedNodes: Int = 0,
    val criticalNodes: Int = 0,
    val quarantinedNodes: Int = 0,
    val activeRecoveryTasks: Int = 0,
    val lastChecked: Long = System.currentTimeMillis()
)

data class BreachDetectionResult(
    val nodeId: String,
    val breachProbability: Double,
    val detectedAnomalies: List<AnomalyType>,
    val currentHealth: Double,
    val recommendedAction: HealingAction
)

data class RerouteResult(
    val success: Boolean,
    val reroutedPaths: Int,
    val newPaths: List<PathInfo>,
    val failedReroutes: List<String>
)

data class RedundancyResult(
    val nodeId: String,
    val totalBackups: Int,
    val newBackupsEstablished: Int,
    val success: Boolean
)

data class FailoverResult(
    val nodeId: String,
    val success: Boolean,
    val newConnectionId: String?,
    val failoverTime: Long
)

data class HealingEvent(
    val type: HealingEventType,
    val nodeId: String,
    val details: String,
    val success: Boolean,
    val timestamp: Long
)

enum class AnomalyType {
    LATENCY_SPIKE,
    HIGH_ERROR_RATE,
    PACKET_LOSS,
    BEHAVIOR_CHANGE,
    COMMUNICATION_ANOMALY
}

enum class HealingAction {
    NONE,
    MONITOR_CLOSELY,
    INITIATE_RECOVERY,
    FAILOVER,
    QUARANTINE_AND_REROUTE
}

enum class HealingEventType {
    BREACH_DETECTED,
    PATH_REROUTED,
    TUNNEL_REBUILD_STARTED,
    TUNNEL_REBUILD_SUCCESS,
    TUNNEL_REBUILD_FAILED,
    FAILOVER_EXECUTED,
    NODE_QUARANTINED,
    NODE_RELEASED,
    MONITORING_INCREASED,
    REDUNDANCY_ESTABLISHED
}
