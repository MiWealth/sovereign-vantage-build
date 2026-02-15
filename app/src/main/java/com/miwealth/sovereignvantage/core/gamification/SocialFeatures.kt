package com.miwealth.sovereignvantage.core.gamification

import java.util.concurrent.ConcurrentHashMap

/**
 * SOVEREIGN VANTAGE V5.5.8 "ARTHUR EDITION"
 * SOCIAL FEATURES
 * 
 * Social trading and community features:
 * - Follow/unfollow traders
 * - Challenge system (1v1 trading battles)
 * - Copy trading (with consent)
 * - Leaderboard social features
 * - Activity feed
 * 
 * © 2025 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO: Arthur Iain McManus (1966-2025)
 */

// ============================================================================
// DATA CLASSES
// ============================================================================

data class UserSocialProfile(
    val userId: String,
    var displayName: String,
    var isAlias: Boolean,
    var avatar: String? = null,
    var bio: String? = null,
    val followers: MutableList<String> = mutableListOf(),
    val following: MutableList<String> = mutableListOf(),
    var isVerified: Boolean = false,
    val badges: MutableList<String> = mutableListOf(),
    var stats: SocialStats = SocialStats(),
    var settings: SocialSettings = SocialSettings(),
    val createdAt: Long = System.currentTimeMillis()
) {
    val totalFollowers: Int get() = followers.size
    val totalFollowing: Int get() = following.size
}

data class SocialStats(
    var totalChallengesIssued: Int = 0,
    var totalChallengesReceived: Int = 0,
    var challengesWon: Int = 0,
    var challengesLost: Int = 0,
    var copiers: Int = 0,
    var copying: Int = 0,
    var strategyShareCount: Int = 0,
    var totalLikes: Int = 0,
    var totalComments: Int = 0
) {
    val challengeWinRate: Double
        get() = if (challengesWon + challengesLost > 0) {
            (challengesWon.toDouble() / (challengesWon + challengesLost)) * 100
        } else 0.0
}

data class SocialSettings(
    var allowFollow: Boolean = true,
    var allowCopyTrading: Boolean = false,
    var showRealTimeActivity: Boolean = true,
    var showPortfolio: Boolean = false,
    var showPerformance: Boolean = true,
    var allowChallenges: Boolean = true,
    var notifyOnFollow: Boolean = true,
    var notifyOnChallenge: Boolean = true,
    var notifyOnCopy: Boolean = true
)

data class Challenge(
    val id: String,
    val challengerId: String,
    val challengedId: String,
    var status: ChallengeStatus = ChallengeStatus.PENDING,
    val type: ChallengeType,
    val rules: ChallengeRules,
    var startDate: Long? = null,
    var endDate: Long? = null,
    var challengerScore: Double? = null,
    var challengedScore: Double? = null,
    var winnerId: String? = null,
    var badgeReward: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    var acceptedAt: Long? = null,
    var completedAt: Long? = null
)

enum class ChallengeStatus {
    PENDING, ACCEPTED, DECLINED, ACTIVE, COMPLETED, CANCELLED
}

enum class ChallengeType {
    ROI_BATTLE,
    SHARPE_BATTLE,
    CONSISTENCY_BATTLE,
    WIN_RATE_BATTLE
}

data class ChallengeRules(
    val duration: Int,
    val minTrades: Int,
    val maxLeverage: Int,
    val allowedAssets: List<String>,
    val scoringMethod: String,
    val paperTrading: Boolean
)

data class CopyTradingRelation(
    val id: String,
    val copierId: String,
    val traderId: String,
    var status: CopyStatus = CopyStatus.ACTIVE,
    var settings: CopySettings,
    var performance: CopyPerformance = CopyPerformance(),
    val startedAt: Long = System.currentTimeMillis(),
    var lastSync: Long = System.currentTimeMillis()
)

enum class CopyStatus { ACTIVE, PAUSED, STOPPED }

data class CopySettings(
    var allocationPercent: Double,
    var maxPositionSize: Double,
    var copyStopLoss: Boolean,
    var copyTakeProfit: Boolean,
    var delaySeconds: Int,
    var excludeAssets: List<String>,
    var maxDailyTrades: Int
)

data class CopyPerformance(
    var totalCopiedTrades: Int = 0,
    var successfulCopies: Int = 0,
    var failedCopies: Int = 0,
    var totalPnL: Double = 0.0,
    var totalPnLPercent: Double = 0.0
)

data class ActivityFeedItem(
    val id: String,
    val userId: String,
    val type: ActivityType,
    val content: String,
    val metadata: Map<String, Any> = emptyMap(),
    var likes: Int = 0,
    var comments: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

enum class ActivityType {
    TRADE_EXECUTED,
    BADGE_EARNED,
    CHALLENGE_WON,
    CHALLENGE_ISSUED,
    LEVEL_UP,
    COMPETITION_WON,
    STRATEGY_SHARED,
    MILESTONE_REACHED
}

data class Comment(
    val id: String,
    val activityId: String,
    val userId: String,
    val content: String,
    var likes: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

// ============================================================================
// SOCIAL MANAGER
// ============================================================================

class SocialManager {
    private val profiles = ConcurrentHashMap<String, UserSocialProfile>()
    private val challenges = ConcurrentHashMap<String, Challenge>()
    private val copyRelations = ConcurrentHashMap<String, CopyTradingRelation>()
    private val activityFeed = ConcurrentHashMap<String, MutableList<ActivityFeedItem>>()
    private val comments = ConcurrentHashMap<String, MutableList<Comment>>()
    
    companion object {
        /** Maximum number of user feeds tracked in memory. */
        private const val MAX_TRACKED_FEED_USERS = 500
    }
    
    /**
     * Create or get social profile
     */
    fun getOrCreateProfile(userId: String, displayName: String, isAlias: Boolean = true): UserSocialProfile {
        return profiles.getOrPut(userId) {
            UserSocialProfile(
                userId = userId,
                displayName = displayName,
                isAlias = isAlias
            )
        }
    }
    
    /**
     * Follow a user
     */
    fun follow(followerId: String, targetId: String): Boolean {
        if (followerId == targetId) return false
        
        val followerProfile = profiles[followerId] ?: return false
        val targetProfile = profiles[targetId] ?: return false
        
        if (!targetProfile.settings.allowFollow) return false
        if (followerProfile.following.contains(targetId)) return false
        
        followerProfile.following.add(targetId)
        targetProfile.followers.add(followerId)
        
        return true
    }
    
    /**
     * Unfollow a user
     */
    fun unfollow(followerId: String, targetId: String): Boolean {
        val followerProfile = profiles[followerId] ?: return false
        val targetProfile = profiles[targetId] ?: return false
        
        if (!followerProfile.following.contains(targetId)) return false
        
        followerProfile.following.remove(targetId)
        targetProfile.followers.remove(followerId)
        
        return true
    }
    
    /**
     * Issue a challenge
     */
    fun issueChallenge(
        challengerId: String,
        challengedId: String,
        type: ChallengeType,
        rules: ChallengeRules
    ): Challenge? {
        if (challengerId == challengedId) return null
        
        val challengerProfile = profiles[challengerId] ?: return null
        val challengedProfile = profiles[challengedId] ?: return null
        
        if (!challengedProfile.settings.allowChallenges) return null
        
        val challenge = Challenge(
            id = "ch_${System.currentTimeMillis()}_${(0..999999).random()}",
            challengerId = challengerId,
            challengedId = challengedId,
            type = type,
            rules = rules
        )
        
        challenges[challenge.id] = challenge
        
        challengerProfile.stats.totalChallengesIssued++
        challengedProfile.stats.totalChallengesReceived++
        
        createActivity(
            challengerId,
            ActivityType.CHALLENGE_ISSUED,
            "${challengerProfile.displayName} challenged ${challengedProfile.displayName}",
            mapOf("challengeId" to challenge.id, "type" to type.name)
        )
        
        return challenge
    }
    
    /**
     * Respond to a challenge
     */
    fun respondToChallenge(challengeId: String, accept: Boolean): Challenge? {
        val challenge = challenges[challengeId] ?: return null
        if (challenge.status != ChallengeStatus.PENDING) return null
        
        if (accept) {
            challenge.status = ChallengeStatus.ACCEPTED
            challenge.acceptedAt = System.currentTimeMillis()
            challenge.startDate = System.currentTimeMillis()
            challenge.endDate = System.currentTimeMillis() + challenge.rules.duration * 60 * 60 * 1000L
            
            // Would start challenge monitoring here
            challenge.status = ChallengeStatus.ACTIVE
        } else {
            challenge.status = ChallengeStatus.DECLINED
        }
        
        return challenge
    }
    
    /**
     * Complete a challenge
     */
    fun completeChallenge(challengeId: String, challengerScore: Double, challengedScore: Double): Challenge? {
        val challenge = challenges[challengeId] ?: return null
        if (challenge.status != ChallengeStatus.ACTIVE) return null
        
        challenge.status = ChallengeStatus.COMPLETED
        challenge.challengerScore = challengerScore
        challenge.challengedScore = challengedScore
        challenge.completedAt = System.currentTimeMillis()
        
        // Determine winner
        challenge.winnerId = when {
            challengerScore > challengedScore -> challenge.challengerId
            challengedScore > challengerScore -> challenge.challengedId
            else -> null
        }
        
        // Update stats
        challenge.winnerId?.let { winnerId ->
            val loserId = if (winnerId == challenge.challengerId) challenge.challengedId else challenge.challengerId
            
            profiles[winnerId]?.let { winner ->
                winner.stats.challengesWon++
                createActivity(
                    winnerId,
                    ActivityType.CHALLENGE_WON,
                    "${winner.displayName} won a ${challenge.type.name.lowercase().replace("_", " ")} challenge!",
                    mapOf("challengeId" to challengeId, "score" to (if (winnerId == challenge.challengerId) challengerScore else challengedScore))
                )
            }
            
            profiles[loserId]?.let { loser ->
                loser.stats.challengesLost++
            }
        }
        
        return challenge
    }
    
    /**
     * Start copy trading
     */
    fun startCopyTrading(copierId: String, traderId: String, settings: CopySettings): CopyTradingRelation? {
        if (copierId == traderId) return null
        
        val traderProfile = profiles[traderId] ?: return null
        if (!traderProfile.settings.allowCopyTrading) return null
        
        val relation = CopyTradingRelation(
            id = "copy_${System.currentTimeMillis()}_${(0..999999).random()}",
            copierId = copierId,
            traderId = traderId,
            settings = settings
        )
        
        copyRelations[relation.id] = relation
        
        traderProfile.stats.copiers++
        profiles[copierId]?.let { it.stats.copying++ }
        
        return relation
    }
    
    /**
     * Stop copy trading
     */
    fun stopCopyTrading(relationId: String): Boolean {
        val relation = copyRelations[relationId] ?: return false
        
        relation.status = CopyStatus.STOPPED
        
        profiles[relation.traderId]?.let {
            it.stats.copiers = maxOf(0, it.stats.copiers - 1)
        }
        
        profiles[relation.copierId]?.let {
            it.stats.copying = maxOf(0, it.stats.copying - 1)
        }
        
        return true
    }
    
    /**
     * Create activity feed item
     */
    fun createActivity(
        userId: String,
        type: ActivityType,
        content: String,
        metadata: Map<String, Any> = emptyMap()
    ): ActivityFeedItem {
        val activity = ActivityFeedItem(
            id = "act_${System.currentTimeMillis()}_${(0..999999).random()}",
            userId = userId,
            type = type,
            content = content,
            metadata = metadata
        )
        
        val userFeed = activityFeed.getOrPut(userId) { mutableListOf() }
        userFeed.add(0, activity)
        
        if (userFeed.size > 100) userFeed.removeLast()
        
        // LRU eviction: drop least-recently-active user feeds when map grows too large
        if (activityFeed.size > MAX_TRACKED_FEED_USERS) {
            val lruUser = activityFeed.entries
                .minByOrNull { it.value.firstOrNull()?.timestamp?.toEpochMilli() ?: 0L }
                ?.key
            if (lruUser != null && lruUser != userId) {
                activityFeed.remove(lruUser)
            }
        }
        
        return activity
    }
    
    /**
     * Like an activity
     */
    fun likeActivity(activityId: String): Boolean {
        for ((_, feed) in activityFeed) {
            val activity = feed.find { it.id == activityId }
            if (activity != null) {
                activity.likes++
                profiles[activity.userId]?.let { it.stats.totalLikes++ }
                return true
            }
        }
        return false
    }
    
    /**
     * Comment on an activity
     */
    fun commentOnActivity(activityId: String, userId: String, content: String): Comment? {
        for ((_, feed) in activityFeed) {
            val activity = feed.find { it.id == activityId }
            if (activity != null) {
                val comment = Comment(
                    id = "cmt_${System.currentTimeMillis()}_${(0..999999).random()}",
                    activityId = activityId,
                    userId = userId,
                    content = content
                )
                
                val activityComments = comments.getOrPut(activityId) { mutableListOf() }
                activityComments.add(comment)
                
                activity.comments++
                profiles[activity.userId]?.let { it.stats.totalComments++ }
                
                return comment
            }
        }
        return null
    }
    
    /**
     * Get activity feed for a user (includes followed users)
     */
    fun getActivityFeed(userId: String, limit: Int = 50): List<ActivityFeedItem> {
        val profile = profiles[userId] ?: return emptyList()
        
        val allActivities = mutableListOf<ActivityFeedItem>()
        
        // Own activities
        activityFeed[userId]?.let { allActivities.addAll(it) }
        
        // Followed users' activities
        for (followedId in profile.following) {
            activityFeed[followedId]?.let { allActivities.addAll(it) }
        }
        
        return allActivities
            .sortedByDescending { it.timestamp }
            .take(limit)
    }
    
    /**
     * Get leaderboard with social info
     */
    fun getSocialLeaderboard(metric: SocialMetric, limit: Int = 100): List<UserSocialProfile> {
        val allProfiles = profiles.values.toList()
        
        return when (metric) {
            SocialMetric.FOLLOWERS -> allProfiles.sortedByDescending { it.totalFollowers }
            SocialMetric.CHALLENGES_WON -> allProfiles.sortedByDescending { it.stats.challengesWon }
            SocialMetric.COPIERS -> allProfiles.sortedByDescending { it.stats.copiers }
        }.take(limit)
    }
    
    /**
     * Get user's challenges
     */
    fun getUserChallenges(userId: String): List<Challenge> {
        return challenges.values
            .filter { it.challengerId == userId || it.challengedId == userId }
            .sortedByDescending { it.createdAt }
    }
    
    /**
     * Get copy trading relations for a user
     */
    fun getCopyRelations(userId: String): CopyRelationsResult {
        val relations = copyRelations.values.toList()
        return CopyRelationsResult(
            copying = relations.filter { it.copierId == userId && it.status == CopyStatus.ACTIVE },
            copiers = relations.filter { it.traderId == userId && it.status == CopyStatus.ACTIVE }
        )
    }
    
    /**
     * Update social settings
     */
    fun updateSettings(userId: String, settings: SocialSettings): Boolean {
        val profile = profiles[userId] ?: return false
        profile.settings = settings
        return true
    }
    
    fun getProfile(userId: String): UserSocialProfile? = profiles[userId]
}

enum class SocialMetric { FOLLOWERS, CHALLENGES_WON, COPIERS }

data class CopyRelationsResult(
    val copying: List<CopyTradingRelation>,
    val copiers: List<CopyTradingRelation>
)
