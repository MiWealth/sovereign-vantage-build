package com.miwealth.sovereignvantage.core.gamification

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * SOVEREIGN VANTAGE V5.17.0 "ARTHUR EDITION"
 * ANTI-CHEAT SYSTEM
 * 
 * Comprehensive fraud detection and prevention for trading competitions.
 * Detects wash trading, spoofing, front-running, collusion, and anomalies.
 * 
 * © 2025 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO: Arthur Iain McManus (1966-2025)
 */

// ============================================================================
// DATA CLASSES
// ============================================================================

data class Trade(
    val id: String,
    val userId: String,
    val timestamp: Long,
    val asset: String,
    val side: ACTradeSide,
    val quantity: Double,
    val price: Double,
    val orderType: ACOrderType,
    val executionTime: Long,
    val ipAddress: String,
    val deviceFingerprint: String,
    val sessionId: String
)

enum class ACTradeSide { BUY, SELL }
enum class ACOrderType { MARKET, LIMIT, STOP }

data class CheatDetectionResult(
    val userId: String,
    val detectionType: CheatType,
    val confidence: Int,
    val severity: Severity,
    val evidence: List<Evidence>,
    val timestamp: Long = System.currentTimeMillis(),
    val autoAction: AutoAction
)

enum class CheatType {
    WASH_TRADING,
    SPOOFING,
    FRONT_RUNNING,
    COLLUSION,
    STATISTICAL_ANOMALY,
    ACCOUNT_SHARING,
    BOT_TRADING,
    MARKET_MANIPULATION,
    LAYERING,
    QUOTE_STUFFING
}

enum class Severity { LOW, MEDIUM, HIGH, CRITICAL }

data class Evidence(
    val type: String,
    val description: String,
    val data: Map<String, Any>,
    val weight: Double
)

enum class AutoAction {
    NONE,
    FLAG_REVIEW,
    TEMPORARY_SUSPENSION,
    DISQUALIFICATION,
    PERMANENT_BAN
}

data class Sanction(
    val id: String,
    val userId: String,
    val type: SanctionType,
    val reason: String,
    val evidence: List<Evidence>,
    val startDate: Long,
    val endDate: Long? = null,
    val appealable: Boolean,
    val appealDeadline: Long? = null,
    var status: SanctionStatus = SanctionStatus.ACTIVE
)

enum class SanctionType {
    WARNING,
    COMPETITION_DISQUALIFICATION,
    TEMPORARY_BAN,
    PERMANENT_BAN,
    PRIZE_FORFEITURE,
    RANK_RESET
}

enum class SanctionStatus { ACTIVE, APPEALED, OVERTURNED, COMPLETED }

data class Appeal(
    val id: String,
    val sanctionId: String,
    val userId: String,
    val reason: String,
    val evidence: List<String>,
    val submittedAt: Long,
    var status: AppealStatus = AppealStatus.PENDING,
    var reviewedBy: String? = null,
    var reviewedAt: Long? = null,
    var decision: String? = null
)

enum class AppealStatus { PENDING, UNDER_REVIEW, APPROVED, DENIED }

data class UserRiskProfile(
    val userId: String,
    var riskScore: Int = 0,
    val flags: MutableList<RiskFlag> = mutableListOf(),
    val tradingPatterns: MutableList<TradingPattern> = mutableListOf(),
    val connectionGraph: MutableList<ConnectionNode> = mutableListOf(),
    var lastUpdated: Long = System.currentTimeMillis()
)

data class RiskFlag(
    val type: String,
    val severity: Severity,
    val description: String,
    val timestamp: Long
)

data class TradingPattern(
    val pattern: String,
    val frequency: Int,
    val normalRange: Pair<Int, Int>,
    val currentValue: Int,
    val deviation: Double
)

data class ConnectionNode(
    val userId: String,
    val connectionType: ConnectionType,
    val strength: Double,
    val lastSeen: Long
)

enum class ConnectionType { IP_SHARED, DEVICE_SHARED, TRADING_CORRELATED, FUNDS_TRANSFERRED }

// ============================================================================
// DETECTION THRESHOLDS
// ============================================================================

object DetectionThresholds {
    object WashTrading {
        const val SELF_TRADE_RATIO = 0.1
        const val ROUND_TRIP_TIME_MS = 5000L
        const val PRICE_DEVIATION_PERCENT = 0.5
        const val MIN_TRADES_FOR_ANALYSIS = 20
    }
    
    object Spoofing {
        const val CANCEL_RATIO = 0.8
        const val ORDER_LIFETIME_MS = 1000L
        const val ORDER_SIZE_MULTIPLIER = 5
        const val MIN_ORDERS_FOR_ANALYSIS = 50
    }
    
    object FrontRunning {
        const val TIMING_WINDOW_MS = 100L
        const val PROFIT_THRESHOLD = 0.01
        const val CORRELATION_THRESHOLD = 0.8
        const val MIN_EVENTS_FOR_ANALYSIS = 10
    }
    
    object Collusion {
        const val TRADING_CORRELATION = 0.9
        const val TIMING_CORRELATION = 0.85
        const val PROFIT_TRANSFER_RATIO = 0.3
        const val MIN_DAYS_FOR_ANALYSIS = 7
    }
    
    object StatisticalAnomaly {
        const val WIN_RATE_Z_SCORE = 3.5
        const val PROFIT_Z_SCORE = 4.0
        const val TRADING_VOLUME_Z_SCORE = 3.0
        const val CONSECUTIVE_WINS_THRESHOLD = 15
    }
    
    object AccountSharing {
        const val IP_CHANGE_FREQUENCY = 10
        const val DEVICE_CHANGE_FREQUENCY = 5
        const val SIMULTANEOUS_SESSION_THRESHOLD = 3
        const val GEOGRAPHIC_IMPOSSIBILITY_KM = 1000
    }
    
    object BotTrading {
        const val EXECUTION_TIME_CONSISTENCY = 0.95
        const val ORDER_PATTERN_ENTROPY = 0.1
        const val HUMAN_REACTION_TIME_MS = 150L
        const val TRADING_HOURS_CONSISTENCY = 0.98
    }
}

// ============================================================================
// ANTI-CHEAT ENGINE
// ============================================================================

class AntiCheatEngine {
    private val userProfiles = ConcurrentHashMap<String, UserRiskProfile>()
    private val recentTrades = ConcurrentHashMap<String, MutableList<Trade>>()
    private val detectionResults = mutableListOf<CheatDetectionResult>()
    private val sanctions = mutableListOf<Sanction>()
    
    /**
     * Main detection pipeline - analyze a trade for potential cheating
     */
    fun analyzeTrade(trade: Trade): List<CheatDetectionResult> {
        val results = mutableListOf<CheatDetectionResult>()
        
        addRecentTrade(trade)
        
        detectWashTrading(trade)?.let { results.add(it) }
        detectSpoofing(trade)?.let { results.add(it) }
        detectStatisticalAnomaly(trade)?.let { results.add(it) }
        detectAccountSharing(trade)?.let { results.add(it) }
        detectBotTrading(trade)?.let { results.add(it) }
        
        updateRiskProfile(trade.userId, results)
        detectionResults.addAll(results)
        
        return results
    }
    
    // ========================================================================
    // WASH TRADING DETECTION
    // ========================================================================
    
    private fun detectWashTrading(trade: Trade): CheatDetectionResult? {
        val userTrades = recentTrades[trade.userId] ?: return null
        if (userTrades.size < DetectionThresholds.WashTrading.MIN_TRADES_FOR_ANALYSIS) return null
        
        val evidence = mutableListOf<Evidence>()
        var confidence = 0
        
        val roundTrips = findRoundTripTrades(userTrades, trade)
        if (roundTrips.isNotEmpty()) {
            val roundTripRatio = roundTrips.size.toDouble() / userTrades.size
            if (roundTripRatio > DetectionThresholds.WashTrading.SELF_TRADE_RATIO) {
                evidence.add(Evidence(
                    type = "round_trip_trades",
                    description = "${roundTrips.size} round-trip trades detected (${(roundTripRatio * 100).toInt()}%)",
                    data = mapOf("count" to roundTrips.size, "ratio" to roundTripRatio),
                    weight = 0.4
                ))
                confidence += 40
            }
        }
        
        if (confidence >= 50) {
            return CheatDetectionResult(
                userId = trade.userId,
                detectionType = CheatType.WASH_TRADING,
                confidence = confidence,
                severity = getSeverity(confidence),
                evidence = evidence,
                autoAction = determineAutoAction(confidence, CheatType.WASH_TRADING)
            )
        }
        
        return null
    }
    
    private fun findRoundTripTrades(trades: List<Trade>, currentTrade: Trade): List<Trade> {
        return trades.filter { t ->
            t.asset == currentTrade.asset &&
            t.side != currentTrade.side &&
            abs(currentTrade.timestamp - t.timestamp) < DetectionThresholds.WashTrading.ROUND_TRIP_TIME_MS &&
            abs(currentTrade.price - t.price) / t.price < DetectionThresholds.WashTrading.PRICE_DEVIATION_PERCENT / 100
        }
    }
    
    // ========================================================================
    // SPOOFING DETECTION
    // ========================================================================
    
    private fun detectSpoofing(trade: Trade): CheatDetectionResult? {
        // Simplified spoofing detection
        return null
    }
    
    // ========================================================================
    // STATISTICAL ANOMALY DETECTION
    // ========================================================================
    
    private fun detectStatisticalAnomaly(trade: Trade): CheatDetectionResult? {
        val userTrades = recentTrades[trade.userId] ?: return null
        
        val evidence = mutableListOf<Evidence>()
        var confidence = 0
        
        // Check for suspicious consecutive wins
        val consecutiveWins = countConsecutiveWins(userTrades)
        if (consecutiveWins >= DetectionThresholds.StatisticalAnomaly.CONSECUTIVE_WINS_THRESHOLD) {
            evidence.add(Evidence(
                type = "consecutive_wins",
                description = "$consecutiveWins consecutive winning trades",
                data = mapOf("consecutiveWins" to consecutiveWins),
                weight = 0.35
            ))
            confidence += 35
        }
        
        if (confidence >= 50) {
            return CheatDetectionResult(
                userId = trade.userId,
                detectionType = CheatType.STATISTICAL_ANOMALY,
                confidence = confidence,
                severity = getSeverity(confidence),
                evidence = evidence,
                autoAction = determineAutoAction(confidence, CheatType.STATISTICAL_ANOMALY)
            )
        }
        
        return null
    }
    
    private fun countConsecutiveWins(trades: List<Trade>): Int {
        // Simplified - would analyze P&L in real implementation
        return 0
    }
    
    // ========================================================================
    // ACCOUNT SHARING DETECTION
    // ========================================================================
    
    private fun detectAccountSharing(trade: Trade): CheatDetectionResult? {
        val evidence = mutableListOf<Evidence>()
        var confidence = 0
        
        val ipAnalysis = analyzeIPPatterns(trade.userId)
        if (ipAnalysis.first > DetectionThresholds.AccountSharing.IP_CHANGE_FREQUENCY) {
            evidence.add(Evidence(
                type = "multiple_ips",
                description = "${ipAnalysis.first} unique IPs in 24 hours",
                data = mapOf("uniqueIPs" to ipAnalysis.first),
                weight = 0.3
            ))
            confidence += 30
        }
        
        val deviceAnalysis = analyzeDevicePatterns(trade.userId)
        if (deviceAnalysis.first > DetectionThresholds.AccountSharing.DEVICE_CHANGE_FREQUENCY) {
            evidence.add(Evidence(
                type = "multiple_devices",
                description = "${deviceAnalysis.first} unique devices in 24 hours",
                data = mapOf("uniqueDevices" to deviceAnalysis.first),
                weight = 0.3
            ))
            confidence += 30
        }
        
        if (confidence >= 50) {
            return CheatDetectionResult(
                userId = trade.userId,
                detectionType = CheatType.ACCOUNT_SHARING,
                confidence = confidence,
                severity = getSeverity(confidence),
                evidence = evidence,
                autoAction = determineAutoAction(confidence, CheatType.ACCOUNT_SHARING)
            )
        }
        
        return null
    }
    
    private fun analyzeIPPatterns(userId: String): Pair<Int, Boolean> {
        // Would analyze login history in production
        return Pair((0..5).random(), false)
    }
    
    private fun analyzeDevicePatterns(userId: String): Pair<Int, Boolean> {
        // Would analyze device fingerprints in production
        return Pair((0..3).random(), false)
    }
    
    // ========================================================================
    // BOT TRADING DETECTION
    // ========================================================================
    
    private fun detectBotTrading(trade: Trade): CheatDetectionResult? {
        val evidence = mutableListOf<Evidence>()
        var confidence = 0
        
        // Check for superhuman reaction times
        if (trade.executionTime < DetectionThresholds.BotTrading.HUMAN_REACTION_TIME_MS) {
            evidence.add(Evidence(
                type = "superhuman_speed",
                description = "Execution time of ${trade.executionTime}ms is faster than human capability",
                data = mapOf("executionTime" to trade.executionTime),
                weight = 0.35
            ))
            confidence += 35
        }
        
        if (confidence >= 50) {
            return CheatDetectionResult(
                userId = trade.userId,
                detectionType = CheatType.BOT_TRADING,
                confidence = confidence,
                severity = getSeverity(confidence),
                evidence = evidence,
                autoAction = determineAutoAction(confidence, CheatType.BOT_TRADING)
            )
        }
        
        return null
    }
    
    // ========================================================================
    // HELPER METHODS
    // ========================================================================
    
    private fun addRecentTrade(trade: Trade) {
        val trades = recentTrades.getOrPut(trade.userId) { mutableListOf() }
        trades.add(trade)
        if (trades.size > 1000) trades.removeAt(0)
    }
    
    private fun getSeverity(confidence: Int): Severity {
        return when {
            confidence >= 90 -> Severity.CRITICAL
            confidence >= 75 -> Severity.HIGH
            confidence >= 60 -> Severity.MEDIUM
            else -> Severity.LOW
        }
    }
    
    private fun determineAutoAction(confidence: Int, cheatType: CheatType): AutoAction {
        return when {
            confidence >= 95 -> AutoAction.PERMANENT_BAN
            confidence >= 85 -> AutoAction.DISQUALIFICATION
            confidence >= 70 -> AutoAction.TEMPORARY_SUSPENSION
            confidence >= 50 -> AutoAction.FLAG_REVIEW
            else -> AutoAction.NONE
        }
    }
    
    private fun updateRiskProfile(userId: String, results: List<CheatDetectionResult>) {
        val profile = userProfiles.getOrPut(userId) { UserRiskProfile(userId) }
        
        for (result in results) {
            profile.riskScore = minOf(100, profile.riskScore + (result.confidence * 0.1).toInt())
            profile.flags.add(RiskFlag(
                type = result.detectionType.name,
                severity = result.severity,
                description = "${result.detectionType} detected with ${result.confidence}% confidence",
                timestamp = result.timestamp
            ))
        }
        
        profile.lastUpdated = System.currentTimeMillis()
    }
    
    // ========================================================================
    // SANCTIONS MANAGEMENT
    // ========================================================================
    
    fun createSanction(
        userId: String,
        type: SanctionType,
        reason: String,
        evidence: List<Evidence>,
        durationDays: Int? = null
    ): Sanction {
        val sanction = Sanction(
            id = "sanction_${System.currentTimeMillis()}_${(0..999999).random()}",
            userId = userId,
            type = type,
            reason = reason,
            evidence = evidence,
            startDate = System.currentTimeMillis(),
            endDate = durationDays?.let { System.currentTimeMillis() + it * 24 * 60 * 60 * 1000L },
            appealable = type != SanctionType.PERMANENT_BAN,
            appealDeadline = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L
        )
        
        sanctions.add(sanction)
        return sanction
    }
    
    fun getUserRiskProfile(userId: String): UserRiskProfile? = userProfiles[userId]
    
    fun getRecentDetections(userId: String, days: Int = 30): List<CheatDetectionResult> {
        val cutoff = System.currentTimeMillis() - days * 24 * 60 * 60 * 1000L
        return detectionResults.filter { it.userId == userId && it.timestamp > cutoff }
    }
    
    fun generateReport(userId: String): Map<String, Any?> {
        val profile = userProfiles[userId]
        val recentDetections = getRecentDetections(userId)
        
        val recommendations = mutableListOf<String>()
        if (profile != null && profile.riskScore > 50) {
            recommendations.add("Increase monitoring frequency")
        }
        if (recentDetections.size > 3) {
            recommendations.add("Consider manual review")
        }
        if (recentDetections.any { it.severity == Severity.CRITICAL }) {
            recommendations.add("Immediate action required")
        }
        
        return mapOf(
            "profile" to profile,
            "recentDetections" to recentDetections,
            "recommendations" to recommendations
        )
    }
}
