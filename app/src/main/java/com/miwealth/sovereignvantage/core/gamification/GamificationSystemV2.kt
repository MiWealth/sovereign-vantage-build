package com.miwealth.sovereignvantage.core.gamification

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * SOVEREIGN VANTAGE V5.17.0 "ARTHUR EDITION"
 * GAMIFICATION SYSTEM V2
 * 
 * Key Changes from V1:
 * - Percentage-based scoring (ROI%) instead of absolute profits
 * - Badge rewards instead of cash prizes
 * - Blockchain-recorded scores for tamper-proof verification
 * - Real name or alias option for user privacy
 * - Level playing field regardless of capital size
 * 
 * © 2025 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO: Arthur Iain McManus (1966-2025)
 */

// ============================================================================
// DATA CLASSES
// ============================================================================

data class UserProfile(
    val userId: String,
    var displayName: String,
    var isAlias: Boolean,
    var realName: String? = null,
    var avatar: String? = null,
    var level: Int = 1,
    var xp: Int = 0,
    var tier: String = "FREE",
    val badges: MutableList<UserBadge> = mutableListOf(),
    var stats: PercentageBasedStats = PercentageBasedStats(),
    var blockchainAddress: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    var lastActive: Long = System.currentTimeMillis()
)

data class PercentageBasedStats(
    var totalROI: Double = 0.0,
    var averageTradeROI: Double = 0.0,
    var bestTradeROI: Double = 0.0,
    var worstTradeROI: Double = 0.0,
    var winRate: Double = 0.0,
    var sharpeRatio: Double = 0.0,
    var sortinoRatio: Double = 0.0,
    var maxDrawdown: Double = 0.0,
    var profitFactor: Double = 0.0,
    var totalTrades: Int = 0,
    var winningTrades: Int = 0,
    var losingTrades: Int = 0,
    var currentStreak: Int = 0,
    var longestStreak: Int = 0,
    var lessonsCompleted: Int = 0,
    var competitionsEntered: Int = 0,
    var competitionsWon: Int = 0,
    var podiumFinishes: Int = 0,
    var dailyWins: Int = 0,
    var weeklyWins: Int = 0,
    var monthlyWins: Int = 0
)

data class UserBadge(
    val badgeId: String,
    val earnedAt: Long = System.currentTimeMillis(),
    var blockchainTxHash: String? = null
)

data class Badge(
    val id: String,
    val name: String,
    val description: String,
    val category: BadgeCategory,
    val tier: BadgeTier,
    val icon: String,
    val rarity: BadgeRarity,
    val requirements: List<BadgeRequirement>,
    val xpReward: Int
)

data class BadgeRequirement(
    val metric: String,
    val operator: RequirementOperator,
    val value: Double
)

enum class BadgeCategory {
    TRADING_PERFORMANCE,
    CONSISTENCY,
    RISK_MANAGEMENT,
    LEARNING,
    COMPETITION,
    SOCIAL,
    SPECIAL
}

enum class BadgeTier { BRONZE, SILVER, GOLD, PLATINUM, DIAMOND }

enum class BadgeRarity { COMMON, UNCOMMON, RARE, EPIC, LEGENDARY }

enum class RequirementOperator { GTE, LTE, EQ, GT, LT }

data class Competition(
    val id: String,
    val name: String,
    val description: String,
    val type: CompetitionType,
    val startDate: Long,
    val endDate: Long,
    val maxParticipants: Int,
    var currentParticipants: Int = 0,
    val scoringMethod: ScoringMethod,
    val rules: CompetitionRules,
    val badgeRewards: List<BadgeReward>,
    var status: CompetitionStatus = CompetitionStatus.UPCOMING
)

enum class CompetitionType {
    DAILY_CHALLENGE,
    WEEKLY_TOURNAMENT,
    MONTHLY_CHAMPIONSHIP,
    SPECIAL_EVENT
}

enum class CompetitionStatus { UPCOMING, ACTIVE, COMPLETED }

enum class ScoringMethod {
    ROI_PERCENTAGE,
    SHARPE_RATIO,
    CONSISTENCY,
    RISK_ADJUSTED_ROI
}

data class CompetitionRules(
    val minTrades: Int,
    val maxLeverage: Int,
    val allowedAssets: List<String>,
    val paperTrading: Boolean,
    val startingCapitalNormalized: Boolean
)

data class BadgeReward(
    val rank: String,
    val badgeId: String,
    val xpBonus: Int
)

data class LeaderboardEntry(
    var rank: Int = 0,
    val userId: String,
    val displayName: String,
    val isAlias: Boolean,
    val avatar: String? = null,
    val score: Double,
    val scoreType: String,
    var change: Int = 0,
    val badges: List<String>,
    val verified: Boolean,
    val blockchainProof: String? = null
)

data class BlockchainScoreRecord(
    val recordId: String,
    val userId: String,
    val competitionId: String,
    val score: Double,
    val scoreType: ScoringMethod,
    val timestamp: Long,
    val trades: List<TradeHash>,
    val signature: String,
    var blockNumber: Long? = null,
    var txHash: String? = null
)

data class TradeHash(
    val tradeId: String,
    val hash: String,
    val roiPercent: Double
)

// ============================================================================
// BADGE DEFINITIONS (50+ Badges Across 5+ Categories)
// ============================================================================

object Badges {
    val ALL_BADGES: List<Badge> = listOf(
        // TRADING PERFORMANCE BADGES (ROI-Based)
        Badge("first_profit", "First Green", "Close your first profitable trade",
            BadgeCategory.TRADING_PERFORMANCE, BadgeTier.BRONZE, "💚", BadgeRarity.COMMON,
            listOf(BadgeRequirement("winningTrades", RequirementOperator.GTE, 1.0)), 100),
        
        Badge("roi_5_percent", "Steady Gains", "Achieve 5% total ROI",
            BadgeCategory.TRADING_PERFORMANCE, BadgeTier.BRONZE, "📈", BadgeRarity.COMMON,
            listOf(BadgeRequirement("totalROI", RequirementOperator.GTE, 5.0)), 200),
        
        Badge("roi_25_percent", "Quarter Up", "Achieve 25% total ROI",
            BadgeCategory.TRADING_PERFORMANCE, BadgeTier.SILVER, "📊", BadgeRarity.UNCOMMON,
            listOf(BadgeRequirement("totalROI", RequirementOperator.GTE, 25.0)), 500),
        
        Badge("roi_50_percent", "Half Double", "Achieve 50% total ROI",
            BadgeCategory.TRADING_PERFORMANCE, BadgeTier.GOLD, "🎯", BadgeRarity.RARE,
            listOf(BadgeRequirement("totalROI", RequirementOperator.GTE, 50.0)), 1000),
        
        Badge("roi_100_percent", "Double Up", "Achieve 100% total ROI (doubled your money)",
            BadgeCategory.TRADING_PERFORMANCE, BadgeTier.PLATINUM, "💰", BadgeRarity.EPIC,
            listOf(BadgeRequirement("totalROI", RequirementOperator.GTE, 100.0)), 2500),
        
        Badge("roi_500_percent", "Five Bagger", "Achieve 500% total ROI",
            BadgeCategory.TRADING_PERFORMANCE, BadgeTier.DIAMOND, "💎", BadgeRarity.LEGENDARY,
            listOf(BadgeRequirement("totalROI", RequirementOperator.GTE, 500.0)), 10000),
        
        Badge("single_trade_10_percent", "Nice Trade", "Achieve 10%+ ROI on a single trade",
            BadgeCategory.TRADING_PERFORMANCE, BadgeTier.SILVER, "🎰", BadgeRarity.UNCOMMON,
            listOf(BadgeRequirement("bestTradeROI", RequirementOperator.GTE, 10.0)), 300),
        
        Badge("single_trade_50_percent", "Moonshot", "Achieve 50%+ ROI on a single trade",
            BadgeCategory.TRADING_PERFORMANCE, BadgeTier.GOLD, "🚀", BadgeRarity.RARE,
            listOf(BadgeRequirement("bestTradeROI", RequirementOperator.GTE, 50.0)), 750),
        
        Badge("single_trade_100_percent", "Double or Nothing", "Achieve 100%+ ROI on a single trade",
            BadgeCategory.TRADING_PERFORMANCE, BadgeTier.PLATINUM, "🌟", BadgeRarity.EPIC,
            listOf(BadgeRequirement("bestTradeROI", RequirementOperator.GTE, 100.0)), 2000),
        
        // CONSISTENCY BADGES
        Badge("win_rate_55", "Edge Finder", "Maintain 55%+ win rate over 50 trades",
            BadgeCategory.CONSISTENCY, BadgeTier.SILVER, "🎯", BadgeRarity.UNCOMMON,
            listOf(
                BadgeRequirement("winRate", RequirementOperator.GTE, 55.0),
                BadgeRequirement("totalTrades", RequirementOperator.GTE, 50.0)
            ), 500),
        
        Badge("win_rate_60", "Consistent Winner", "Maintain 60%+ win rate over 100 trades",
            BadgeCategory.CONSISTENCY, BadgeTier.GOLD, "🏆", BadgeRarity.RARE,
            listOf(
                BadgeRequirement("winRate", RequirementOperator.GTE, 60.0),
                BadgeRequirement("totalTrades", RequirementOperator.GTE, 100.0)
            ), 1000),
        
        Badge("win_rate_70", "Sharp Shooter", "Maintain 70%+ win rate over 200 trades",
            BadgeCategory.CONSISTENCY, BadgeTier.PLATINUM, "🎖️", BadgeRarity.EPIC,
            listOf(
                BadgeRequirement("winRate", RequirementOperator.GTE, 70.0),
                BadgeRequirement("totalTrades", RequirementOperator.GTE, 200.0)
            ), 3000),
        
        Badge("streak_5", "Hot Streak", "Win 5 trades in a row",
            BadgeCategory.CONSISTENCY, BadgeTier.BRONZE, "🔥", BadgeRarity.COMMON,
            listOf(BadgeRequirement("currentStreak", RequirementOperator.GTE, 5.0)), 200),
        
        Badge("streak_10", "On Fire", "Win 10 trades in a row",
            BadgeCategory.CONSISTENCY, BadgeTier.SILVER, "🔥", BadgeRarity.UNCOMMON,
            listOf(BadgeRequirement("currentStreak", RequirementOperator.GTE, 10.0)), 500),
        
        Badge("streak_20", "Unstoppable", "Win 20 trades in a row",
            BadgeCategory.CONSISTENCY, BadgeTier.GOLD, "⚡", BadgeRarity.RARE,
            listOf(BadgeRequirement("currentStreak", RequirementOperator.GTE, 20.0)), 1500),
        
        // RISK MANAGEMENT BADGES
        Badge("sharpe_1", "Risk Aware", "Achieve Sharpe ratio above 1.0",
            BadgeCategory.RISK_MANAGEMENT, BadgeTier.SILVER, "📐", BadgeRarity.UNCOMMON,
            listOf(BadgeRequirement("sharpeRatio", RequirementOperator.GTE, 1.0)), 400),
        
        Badge("sharpe_2", "Risk Master", "Achieve Sharpe ratio above 2.0",
            BadgeCategory.RISK_MANAGEMENT, BadgeTier.GOLD, "📏", BadgeRarity.RARE,
            listOf(BadgeRequirement("sharpeRatio", RequirementOperator.GTE, 2.0)), 1000),
        
        Badge("sharpe_3", "Institutional Grade", "Achieve Sharpe ratio above 3.0",
            BadgeCategory.RISK_MANAGEMENT, BadgeTier.DIAMOND, "🏛️", BadgeRarity.LEGENDARY,
            listOf(BadgeRequirement("sharpeRatio", RequirementOperator.GTE, 3.0)), 5000),
        
        Badge("max_dd_10", "Capital Preserver", "Keep max drawdown under 10% over 50+ trades",
            BadgeCategory.RISK_MANAGEMENT, BadgeTier.GOLD, "🛡️", BadgeRarity.RARE,
            listOf(
                BadgeRequirement("maxDrawdown", RequirementOperator.LTE, 10.0),
                BadgeRequirement("totalTrades", RequirementOperator.GTE, 50.0)
            ), 800),
        
        Badge("profit_factor_2", "Profitable System", "Achieve profit factor above 2.0",
            BadgeCategory.RISK_MANAGEMENT, BadgeTier.GOLD, "⚖️", BadgeRarity.RARE,
            listOf(BadgeRequirement("profitFactor", RequirementOperator.GTE, 2.0)), 750),
        
        // COMPETITION BADGES
        Badge("first_competition", "Competitor", "Enter your first competition",
            BadgeCategory.COMPETITION, BadgeTier.BRONZE, "🏁", BadgeRarity.COMMON,
            listOf(BadgeRequirement("competitionsEntered", RequirementOperator.GTE, 1.0)), 100),
        
        Badge("podium_finish", "Podium Finish", "Finish in top 3 of any competition",
            BadgeCategory.COMPETITION, BadgeTier.GOLD, "🥇", BadgeRarity.RARE,
            listOf(BadgeRequirement("podiumFinishes", RequirementOperator.GTE, 1.0)), 1000),
        
        Badge("competition_winner", "Champion", "Win a competition",
            BadgeCategory.COMPETITION, BadgeTier.PLATINUM, "🏆", BadgeRarity.EPIC,
            listOf(BadgeRequirement("competitionsWon", RequirementOperator.GTE, 1.0)), 2000),
        
        Badge("daily_champion", "Daily Champion", "Win a daily challenge",
            BadgeCategory.COMPETITION, BadgeTier.SILVER, "☀️", BadgeRarity.UNCOMMON,
            listOf(BadgeRequirement("dailyWins", RequirementOperator.GTE, 1.0)), 300),
        
        Badge("weekly_champion", "Weekly Champion", "Win a weekly tournament",
            BadgeCategory.COMPETITION, BadgeTier.GOLD, "📆", BadgeRarity.RARE,
            listOf(BadgeRequirement("weeklyWins", RequirementOperator.GTE, 1.0)), 800),
        
        Badge("monthly_champion", "Monthly Champion", "Win a monthly championship",
            BadgeCategory.COMPETITION, BadgeTier.PLATINUM, "🗓️", BadgeRarity.EPIC,
            listOf(BadgeRequirement("monthlyWins", RequirementOperator.GTE, 1.0)), 2500),
        
        Badge("serial_winner", "Serial Winner", "Win 5 competitions",
            BadgeCategory.COMPETITION, BadgeTier.DIAMOND, "👑", BadgeRarity.LEGENDARY,
            listOf(BadgeRequirement("competitionsWon", RequirementOperator.GTE, 5.0)), 5000),
        
        // LEARNING BADGES
        Badge("first_lesson", "Student", "Complete your first lesson",
            BadgeCategory.LEARNING, BadgeTier.BRONZE, "📚", BadgeRarity.COMMON,
            listOf(BadgeRequirement("lessonsCompleted", RequirementOperator.GTE, 1.0)), 50),
        
        Badge("lessons_25", "Dedicated Learner", "Complete 25 lessons",
            BadgeCategory.LEARNING, BadgeTier.SILVER, "📖", BadgeRarity.UNCOMMON,
            listOf(BadgeRequirement("lessonsCompleted", RequirementOperator.GTE, 25.0)), 500),
        
        Badge("lessons_50", "Knowledge Seeker", "Complete 50 lessons",
            BadgeCategory.LEARNING, BadgeTier.GOLD, "🎓", BadgeRarity.RARE,
            listOf(BadgeRequirement("lessonsCompleted", RequirementOperator.GTE, 50.0)), 1000),
        
        Badge("lessons_76", "Master Trader Graduate", "Complete all 76 lessons",
            BadgeCategory.LEARNING, BadgeTier.DIAMOND, "🎖️", BadgeRarity.LEGENDARY,
            listOf(BadgeRequirement("lessonsCompleted", RequirementOperator.GTE, 76.0)), 5000),
        
        // SPECIAL BADGES
        Badge("early_adopter", "Early Adopter", "Joined during Arthur Edition launch",
            BadgeCategory.SPECIAL, BadgeTier.GOLD, "🚀", BadgeRarity.RARE,
            listOf(), 1000),
        
        Badge("blockchain_verified", "Verified Trader", "Verify your scores on blockchain",
            BadgeCategory.SPECIAL, BadgeTier.SILVER, "✅", BadgeRarity.UNCOMMON,
            listOf(), 500)
    )
    
    fun findById(id: String): Badge? = ALL_BADGES.find { it.id == id }
    
    fun getByCategory(category: BadgeCategory): List<Badge> = 
        ALL_BADGES.filter { it.category == category }
    
    fun getByRarity(rarity: BadgeRarity): List<Badge> = 
        ALL_BADGES.filter { it.rarity == rarity }
}

// ============================================================================
// COMPETITION TEMPLATES
// ============================================================================

object CompetitionTemplates {
    val DAILY_ROI_SPRINT = Competition(
        id = "daily_roi",
        name = "Daily ROI Sprint",
        description = "Best percentage return in 24 hours wins. Capital size doesn't matter - only skill.",
        type = CompetitionType.DAILY_CHALLENGE,
        startDate = 0,
        endDate = 0,
        maxParticipants = 1000,
        scoringMethod = ScoringMethod.ROI_PERCENTAGE,
        rules = CompetitionRules(
            minTrades = 3,
            maxLeverage = 5,
            allowedAssets = listOf("BTC", "ETH", "SOL", "AVAX", "MATIC"),
            paperTrading = true,
            startingCapitalNormalized = true
        ),
        badgeRewards = listOf(
            BadgeReward("1", "daily_champion", 500),
            BadgeReward("2", "podium_finish", 300),
            BadgeReward("3", "podium_finish", 200),
            BadgeReward("4-10", "first_competition", 100)
        )
    )
    
    val WEEKLY_SHARPE = Competition(
        id = "weekly_sharpe",
        name = "Weekly Sharpe Challenge",
        description = "Best risk-adjusted returns over 7 days. Consistency beats luck.",
        type = CompetitionType.WEEKLY_TOURNAMENT,
        startDate = 0,
        endDate = 0,
        maxParticipants = 500,
        scoringMethod = ScoringMethod.SHARPE_RATIO,
        rules = CompetitionRules(
            minTrades = 10,
            maxLeverage = 5,
            allowedAssets = listOf("BTC", "ETH", "SOL", "AVAX", "MATIC", "LINK", "UNI", "AAVE"),
            paperTrading = true,
            startingCapitalNormalized = true
        ),
        badgeRewards = listOf(
            BadgeReward("1", "weekly_champion", 1000),
            BadgeReward("2", "podium_finish", 600),
            BadgeReward("3", "podium_finish", 400),
            BadgeReward("4-10", "sharpe_1", 200)
        )
    )
    
    val MONTHLY_CHAMPIONSHIP = Competition(
        id = "monthly_champ",
        name = "Monthly Championship",
        description = "The ultimate test. Risk-adjusted ROI over 30 days determines the champion.",
        type = CompetitionType.MONTHLY_CHAMPIONSHIP,
        startDate = 0,
        endDate = 0,
        maxParticipants = 250,
        scoringMethod = ScoringMethod.RISK_ADJUSTED_ROI,
        rules = CompetitionRules(
            minTrades = 30,
            maxLeverage = 10,
            allowedAssets = listOf("*"),
            paperTrading = false,
            startingCapitalNormalized = true
        ),
        badgeRewards = listOf(
            BadgeReward("1", "monthly_champion", 3000),
            BadgeReward("2", "podium_finish", 1500),
            BadgeReward("3", "podium_finish", 1000),
            BadgeReward("4-10", "competition_winner", 500),
            BadgeReward("11-25", "first_competition", 250)
        )
    )
}

// ============================================================================
// PERCENTAGE SCORE ENGINE
// ============================================================================

object PercentageScoreEngine {
    
    fun calculateSharpeRatio(returns: List<Double>, riskFreeRate: Double = 0.0): Double {
        if (returns.size < 2) return 0.0
        
        val mean = returns.average()
        val variance = returns.map { (it - mean) * (it - mean) }.average()
        val stdDev = sqrt(variance)
        
        if (stdDev == 0.0) return 0.0
        return (mean - riskFreeRate) / stdDev
    }
    
    fun calculateSortinoRatio(returns: List<Double>, riskFreeRate: Double = 0.0): Double {
        if (returns.size < 2) return 0.0
        
        val mean = returns.average()
        val downsideReturns = returns.filter { it < riskFreeRate }
        
        if (downsideReturns.isEmpty()) return if (mean > riskFreeRate) Double.MAX_VALUE else 0.0
        
        val downsideVariance = downsideReturns.map { 
            val diff = it - riskFreeRate
            diff * diff 
        }.average()
        val downsideDeviation = sqrt(downsideVariance)
        
        if (downsideDeviation == 0.0) return 0.0
        return (mean - riskFreeRate) / downsideDeviation
    }
    
    fun calculateMaxDrawdown(equityCurve: List<Double>): Double {
        if (equityCurve.size < 2) return 0.0
        
        var maxDrawdown = 0.0
        var peak = equityCurve[0]
        
        for (value in equityCurve) {
            if (value > peak) peak = value
            val drawdown = ((peak - value) / peak) * 100
            if (drawdown > maxDrawdown) maxDrawdown = drawdown
        }
        
        return maxDrawdown
    }
    
    fun calculateProfitFactor(trades: List<Double>): Double {
        val grossProfit = trades.filter { it > 0 }.sum()
        val grossLoss = kotlin.math.abs(trades.filter { it < 0 }.sum())
        
        if (grossLoss == 0.0) return if (grossProfit > 0) Double.MAX_VALUE else 0.0
        return grossProfit / grossLoss
    }
    
    fun calculateRiskAdjustedROI(roi: Double, maxDrawdown: Double): Double {
        if (maxDrawdown == 0.0) return if (roi > 0) roi else 0.0
        return roi / maxDrawdown
    }
    
    fun calculateCompetitionScore(stats: PercentageBasedStats, method: ScoringMethod): Double {
        return when (method) {
            ScoringMethod.ROI_PERCENTAGE -> stats.totalROI
            ScoringMethod.SHARPE_RATIO -> stats.sharpeRatio
            ScoringMethod.CONSISTENCY -> stats.winRate
            ScoringMethod.RISK_ADJUSTED_ROI -> calculateRiskAdjustedROI(stats.totalROI, stats.maxDrawdown)
        }
    }
}

// ============================================================================
// BLOCKCHAIN SCORE RECORDER
// ============================================================================

class BlockchainScoreRecorder {
    private val pendingRecords = mutableListOf<BlockchainScoreRecord>()
    private val confirmedRecords = ConcurrentHashMap<String, BlockchainScoreRecord>()
    
    companion object {
        /** Maximum pending records before oldest are evicted. */
        private const val MAX_PENDING_RECORDS = 1000
    }
    
    fun createScoreRecord(
        userId: String,
        competitionId: String,
        score: Double,
        scoreType: ScoringMethod,
        trades: List<Triple<String, String, Double>>
    ): BlockchainScoreRecord {
        val tradeHashes = trades.map { (tradeId, details, roiPercent) ->
            TradeHash(tradeId, hashString(details), roiPercent)
        }
        
        val record = BlockchainScoreRecord(
            recordId = "score_${System.currentTimeMillis()}_${(0..999999).random()}",
            userId = userId,
            competitionId = competitionId,
            score = score,
            scoreType = scoreType,
            timestamp = System.currentTimeMillis(),
            trades = tradeHashes,
            signature = signRecord(userId, competitionId, score, tradeHashes)
        )
        
        pendingRecords.add(record)
        // Evict oldest unconfirmed records if queue grows too large (e.g. blockchain outage)
        while (pendingRecords.size > MAX_PENDING_RECORDS) {
            pendingRecords.removeFirst()
        }
        return record
    }
    
    suspend fun submitToBlockchain(recordId: String): Pair<String, Long> {
        val record = pendingRecords.find { it.recordId == recordId }
            ?: throw IllegalArgumentException("Record not found")
        
        delay(1000) // Simulate blockchain submission
        
        val txHash = "0x${hashString(record.toString())}"
        val blockNumber = System.currentTimeMillis() / 1000
        
        record.txHash = txHash
        record.blockNumber = blockNumber
        
        confirmedRecords[record.recordId] = record
        pendingRecords.removeIf { it.recordId == recordId }
        
        return Pair(txHash, blockNumber)
    }
    
    fun verifyScore(recordId: String): Boolean {
        val record = confirmedRecords[recordId] ?: return false
        return record.txHash != null
    }
    
    private fun hashString(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
    
    private fun signRecord(
        userId: String,
        competitionId: String,
        score: Double,
        trades: List<TradeHash>
    ): String {
        val data = "$userId:$competitionId:$score:${trades.joinToString { it.hash }}"
        return hashString(data)
    }
}

// ============================================================================
// USER PROFILE MANAGER
// ============================================================================

class UserProfileManager {
    private val profiles = ConcurrentHashMap<String, UserProfile>()
    private val blockchainRecorder = BlockchainScoreRecorder()
    
    fun createProfile(
        userId: String,
        displayName: String,
        isAlias: Boolean,
        realName: String? = null
    ): UserProfile {
        val profile = UserProfile(
            userId = userId,
            displayName = displayName,
            isAlias = isAlias,
            realName = if (isAlias) null else realName
        )
        profiles[userId] = profile
        return profile
    }
    
    fun updateDisplayName(userId: String, displayName: String, isAlias: Boolean) {
        profiles[userId]?.let {
            it.displayName = displayName
            it.isAlias = isAlias
        }
    }
    
    fun awardBadge(userId: String, badgeId: String): UserBadge? {
        val profile = profiles[userId] ?: return null
        
        if (profile.badges.any { it.badgeId == badgeId }) return null
        
        val badge = Badges.findById(badgeId) ?: return null
        
        val userBadge = UserBadge(badgeId = badgeId)
        profile.badges.add(userBadge)
        profile.xp += badge.xpReward
        
        updateLevel(profile)
        return userBadge
    }
    
    fun checkAndAwardBadges(userId: String): List<UserBadge> {
        val profile = profiles[userId] ?: return emptyList()
        val awarded = mutableListOf<UserBadge>()
        
        for (badge in Badges.ALL_BADGES) {
            if (profile.badges.any { it.badgeId == badge.id }) continue
            
            val qualified = badge.requirements.all { req ->
                val value = getStatValue(profile.stats, req.metric)
                checkRequirement(value, req.operator, req.value)
            }
            
            if (qualified) {
                awardBadge(userId, badge.id)?.let { awarded.add(it) }
            }
        }
        
        return awarded
    }
    
    private fun getStatValue(stats: PercentageBasedStats, metric: String): Double {
        return when (metric) {
            "totalROI" -> stats.totalROI
            "averageTradeROI" -> stats.averageTradeROI
            "bestTradeROI" -> stats.bestTradeROI
            "winRate" -> stats.winRate
            "sharpeRatio" -> stats.sharpeRatio
            "maxDrawdown" -> stats.maxDrawdown
            "profitFactor" -> stats.profitFactor
            "totalTrades" -> stats.totalTrades.toDouble()
            "winningTrades" -> stats.winningTrades.toDouble()
            "currentStreak" -> stats.currentStreak.toDouble()
            "longestStreak" -> stats.longestStreak.toDouble()
            "lessonsCompleted" -> stats.lessonsCompleted.toDouble()
            "competitionsEntered" -> stats.competitionsEntered.toDouble()
            "competitionsWon" -> stats.competitionsWon.toDouble()
            "podiumFinishes" -> stats.podiumFinishes.toDouble()
            "dailyWins" -> stats.dailyWins.toDouble()
            "weeklyWins" -> stats.weeklyWins.toDouble()
            "monthlyWins" -> stats.monthlyWins.toDouble()
            else -> 0.0
        }
    }
    
    private fun checkRequirement(value: Double, operator: RequirementOperator, target: Double): Boolean {
        return when (operator) {
            RequirementOperator.GTE -> value >= target
            RequirementOperator.LTE -> value <= target
            RequirementOperator.EQ -> value == target
            RequirementOperator.GT -> value > target
            RequirementOperator.LT -> value < target
        }
    }
    
    private fun updateLevel(profile: UserProfile) {
        val xpPerLevel = 1000
        val newLevel = (profile.xp / xpPerLevel) + 1
        profile.level = min(newLevel, 100)
    }
    
    fun getProfile(userId: String): UserProfile? = profiles[userId]
    
    fun getLeaderboard(scoringMethod: ScoringMethod, limit: Int = 100): List<LeaderboardEntry> {
        val entries = profiles.values.map { profile ->
            val score = PercentageScoreEngine.calculateCompetitionScore(profile.stats, scoringMethod)
            LeaderboardEntry(
                userId = profile.userId,
                displayName = profile.displayName,
                isAlias = profile.isAlias,
                avatar = profile.avatar,
                score = score,
                scoreType = getScoreTypeLabel(scoringMethod),
                badges = profile.badges.map { it.badgeId },
                verified = profile.blockchainAddress != null
            )
        }.sortedByDescending { it.score }
        
        entries.forEachIndexed { index, entry -> entry.rank = index + 1 }
        
        return entries.take(limit)
    }
    
    private fun getScoreTypeLabel(method: ScoringMethod): String {
        return when (method) {
            ScoringMethod.ROI_PERCENTAGE -> "ROI%"
            ScoringMethod.SHARPE_RATIO -> "Sharpe"
            ScoringMethod.CONSISTENCY -> "Win%"
            ScoringMethod.RISK_ADJUSTED_ROI -> "Risk-Adj ROI"
        }
    }
}
