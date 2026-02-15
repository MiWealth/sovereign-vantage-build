package com.miwealth.sovereignvantage.core.gamification

import com.miwealth.sovereignvantage.core.dht.*
import com.miwealth.sovereignvantage.core.security.pqc.CryptoProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

/**
 * SOVEREIGN VANTAGE V5.5.36 "ARTHUR EDITION"
 * GAMIFICATION COORDINATOR
 * 
 * Central orchestrator that wires local gamification systems to the DHT network.
 * 
 * Responsibilities:
 * - Connects UserProfileManager → DHTGamificationBridge for profile sync
 * - Connects SocialManager → DHTGamificationBridge for social features
 * - Listens for local events and publishes to DHT (respecting privacy settings)
 * - Listens for DHT events and updates local state
 * - Manages the lifecycle of all gamification background jobs
 * 
 * Privacy-First Design:
 * - All DHT publishing respects SocialSettings
 * - Real names are NEVER published
 * - Performance stats only published if showPerformance = true
 * - User can disable all social features via settings
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO: Arthur Iain McManus (1966-2025)
 */
class GamificationCoordinator(
    private val userId: String,
    private val secureDht: SecureDHTWrapper,
    private val dhtClient: DHTClient,
    private val cryptoProvider: CryptoProvider
) {
    // Core systems
    private val userProfileManager = UserProfileManager()
    private val socialManager = SocialManager()
    private lateinit var dhtBridge: DHTGamificationBridge
    
    // Coroutine scope for background tasks
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Event collectors
    private var profileSyncJob: Job? = null
    private var socialSyncJob: Job? = null
    private var challengeListenerJob: Job? = null
    private var activityListenerJob: Job? = null
    
    // Local state cache
    private val pendingBadgeAwards = ConcurrentHashMap<String, UserBadge>()
    private val discoveredChallenges = ConcurrentHashMap<String, DHTChallenge>()
    
    // Public event flows for UI consumption
    private val _badgeEarned = MutableSharedFlow<BadgeEarnedEvent>()
    val badgeEarned: SharedFlow<BadgeEarnedEvent> = _badgeEarned.asSharedFlow()
    
    private val _challengeReceived = MutableSharedFlow<ChallengeReceivedEvent>()
    val challengeReceived: SharedFlow<ChallengeReceivedEvent> = _challengeReceived.asSharedFlow()
    
    private val _levelUp = MutableSharedFlow<LevelUpEvent>()
    val levelUp: SharedFlow<LevelUpEvent> = _levelUp.asSharedFlow()
    
    private val _activityFeedUpdate = MutableSharedFlow<ActivityFeedUpdateEvent>()
    val activityFeedUpdate: SharedFlow<ActivityFeedUpdateEvent> = _activityFeedUpdate.asSharedFlow()
    
    private val _peerProfileUpdate = MutableSharedFlow<PeerProfileUpdateEvent>()
    val peerProfileUpdate: SharedFlow<PeerProfileUpdateEvent> = _peerProfileUpdate.asSharedFlow()
    
    private val _dailyChallengeCompleted = MutableSharedFlow<DailyChallengeCompletedEvent>()
    val dailyChallengeCompleted: SharedFlow<DailyChallengeCompletedEvent> = _dailyChallengeCompleted.asSharedFlow()
    
    // Current user's social settings
    private var currentSettings = SocialSettings()
    
    // ========================================================================
    // LIFECYCLE
    // ========================================================================
    
    /**
     * Initialize and start the coordinator.
     * Call this after user authentication is complete.
     */
    suspend fun start(displayName: String, isAlias: Boolean = true) {
        // Initialize DHT bridge
        dhtBridge = DHTGamificationBridge(secureDht, dhtClient, userId, cryptoProvider)
        
        // Create local profiles
        userProfileManager.createProfile(userId, displayName, isAlias)
        socialManager.getOrCreateProfile(userId, displayName, isAlias)
        
        // Start DHT bridge background jobs
        dhtBridge.start()
        
        // Start coordinator background jobs
        startProfileSync()
        startSocialSync()
        startChallengeListener()
        startActivityListener()
        
        // Initial sync to DHT
        syncProfileToDHT()
    }
    
    /**
     * Stop all coordinator activities.
     * Call this on logout or app termination.
     */
    fun stop() {
        profileSyncJob?.cancel()
        socialSyncJob?.cancel()
        challengeListenerJob?.cancel()
        activityListenerJob?.cancel()
        dhtBridge.stop()
        scope.cancel()
    }
    
    // ========================================================================
    // USER PROFILE MANAGEMENT
    // ========================================================================
    
    /**
     * Get the current user's profile.
     */
    fun getCurrentProfile(): UserProfile? = userProfileManager.getProfile(userId)
    
    /**
     * Get the current user's social profile.
     */
    fun getCurrentSocialProfile(): UserSocialProfile? = socialManager.getProfile(userId)
    
    /**
     * Update display name (triggers DHT sync if settings allow).
     */
    suspend fun updateDisplayName(displayName: String, isAlias: Boolean) {
        userProfileManager.updateDisplayName(userId, displayName, isAlias)
        socialManager.getProfile(userId)?.let {
            it.displayName = displayName
            it.isAlias = isAlias
        }
        syncProfileToDHT()
    }
    
    /**
     * Update social settings.
     */
    suspend fun updateSocialSettings(settings: SocialSettings) {
        currentSettings = settings
        socialManager.updateSettings(userId, settings)
        
        // Re-sync profile with new privacy settings
        syncProfileToDHT()
    }
    
    /**
     * Get current social settings.
     */
    fun getSocialSettings(): SocialSettings = currentSettings
    
    // ========================================================================
    // TRADING STATS INTEGRATION
    // ========================================================================
    
    /**
     * Record a completed trade.
     * Called by TradingSystem when a trade closes.
     */
    suspend fun recordTrade(
        tradeId: String,
        roiPercent: Double,
        isWin: Boolean,
        usedStahlStop: Boolean = false
    ) {
        val profile = userProfileManager.getProfile(userId) ?: return
        
        // Update stats
        profile.stats.apply {
            totalTrades++
            if (isWin) {
                winningTrades++
                currentStreak++
                if (currentStreak > longestStreak) longestStreak = currentStreak
            } else {
                losingTrades++
                currentStreak = 0
            }
            
            // Recalculate derived stats
            winRate = if (totalTrades > 0) {
                (winningTrades.toDouble() / totalTrades) * 100
            } else 0.0
            
            // Update ROI tracking
            totalROI += roiPercent
            averageTradeROI = totalROI / totalTrades
            
            if (roiPercent > bestTradeROI) bestTradeROI = roiPercent
            if (roiPercent < worstTradeROI) worstTradeROI = roiPercent
        }
        
        // Update daily challenge progress
        updateDailyChallengeProgress(DailyChallengeType.TRADES, 1)
        if (isWin) {
            updateDailyChallengeProgress(DailyChallengeType.WINNING_TRADES, 1)
            updateDailyChallengeProgress(DailyChallengeType.WIN_STREAK, profile.stats.currentStreak)
        }
        if (usedStahlStop) {
            updateDailyChallengeProgress(DailyChallengeType.USE_STAHL, 1)
        }
        
        // Check for new badges
        val newBadges = userProfileManager.checkAndAwardBadges(userId)
        
        // Process new badges
        for (badge in newBadges) {
            handleBadgeAwarded(badge)
        }
        
        // Sync to DHT periodically (not every trade to avoid spam)
        if (profile.stats.totalTrades % 5 == 0) {
            syncProfileToDHT()
        }
    }
    
    /**
     * Record lesson completion.
     * Called by EducationSystem when a lesson is completed.
     */
    suspend fun recordLessonCompleted(lessonId: String) {
        val profile = userProfileManager.getProfile(userId) ?: return
        profile.stats.lessonsCompleted++
        
        // Update daily challenge progress
        updateDailyChallengeProgress(DailyChallengeType.LESSONS, 1)
        
        // Check for learning badges
        val newBadges = userProfileManager.checkAndAwardBadges(userId)
        for (badge in newBadges) {
            handleBadgeAwarded(badge)
        }
    }
    
    /**
     * Update risk metrics (Sharpe, Sortino, MaxDrawdown).
     * Called periodically by RiskEngine.
     */
    suspend fun updateRiskMetrics(
        sharpeRatio: Double,
        sortinoRatio: Double,
        maxDrawdown: Double,
        profitFactor: Double
    ) {
        val profile = userProfileManager.getProfile(userId) ?: return
        profile.stats.apply {
            this.sharpeRatio = sharpeRatio
            this.sortinoRatio = sortinoRatio
            this.maxDrawdown = maxDrawdown
            this.profitFactor = profitFactor
        }
        
        // Check for risk management badges
        val newBadges = userProfileManager.checkAndAwardBadges(userId)
        for (badge in newBadges) {
            handleBadgeAwarded(badge)
        }
    }
    
    // ========================================================================
    // SOCIAL FEATURES
    // ========================================================================
    
    /**
     * Follow another user.
     */
    suspend fun followUser(targetUserId: String): Boolean {
        if (!currentSettings.allowFollow) return false
        
        val success = socialManager.follow(userId, targetUserId)
        if (success) {
            // Sync follow graph to DHT
            val socialProfile = socialManager.getProfile(userId) ?: return false
            dhtBridge.syncFollowGraph(socialProfile.following, socialProfile.followers)
        }
        return success
    }
    
    /**
     * Unfollow a user.
     */
    suspend fun unfollowUser(targetUserId: String): Boolean {
        val success = socialManager.unfollow(userId, targetUserId)
        if (success) {
            val socialProfile = socialManager.getProfile(userId) ?: return false
            dhtBridge.syncFollowGraph(socialProfile.following, socialProfile.followers)
        }
        return success
    }
    
    /**
     * Get activity feed (own + followed users).
     */
    suspend fun getActivityFeed(limit: Int = 50): List<ActivityFeedItem> {
        val socialProfile = socialManager.getProfile(userId) ?: return emptyList()
        
        // Get local activity
        val localFeed = socialManager.getActivityFeed(userId, limit)
        
        // Get DHT activity for followed users
        val dhtActivities = dhtBridge.getActivityFeed(socialProfile.following, limit)
        
        // Merge and sort
        val mergedFeed = mutableListOf<ActivityFeedItem>()
        mergedFeed.addAll(localFeed)
        
        // Convert DHT activities to local format
        for (dhtActivity in dhtActivities) {
            mergedFeed.add(ActivityFeedItem(
                id = dhtActivity.id,
                userId = dhtActivity.odId,
                type = ActivityType.valueOf(dhtActivity.type),
                content = dhtActivity.content,
                metadata = dhtActivity.metadata,
                timestamp = dhtActivity.timestamp
            ))
        }
        
        return mergedFeed.sortedByDescending { it.timestamp }.take(limit)
    }
    
    /**
     * Query another user's public profile.
     */
    suspend fun queryPeerProfile(targetUserId: String): DHTUserProfile? {
        return dhtBridge.queryProfile(targetUserId)
    }
    
    /**
     * Query another user's badges.
     */
    suspend fun queryPeerBadges(targetUserId: String): List<DHTBadge>? {
        return dhtBridge.queryBadges(targetUserId)
    }
    
    // ========================================================================
    // CHALLENGE SYSTEM
    // ========================================================================
    
    /**
     * Issue a challenge to another user.
     */
    suspend fun issueChallenge(
        challengedId: String,
        type: ChallengeType,
        rules: ChallengeRules
    ): Challenge? {
        if (!currentSettings.allowChallenges) return null
        
        // Check target's reputation first
        val reputation = dhtBridge.queryPeerReputation(challengedId)
        if (reputation?.isFlagged == true) {
            return null // Don't allow challenges to flagged users
        }
        
        // Create local challenge
        val challenge = socialManager.issueChallenge(userId, challengedId, type, rules)
            ?: return null
        
        // Publish to DHT
        dhtBridge.publishChallenge(challenge)
        
        // Broadcast activity
        if (currentSettings.showRealTimeActivity) {
            val profile = socialManager.getProfile(userId)
            dhtBridge.broadcastActivity(
                DHTActivityType.CHALLENGE_ISSUED,
                "${profile?.displayName ?: "User"} issued a ${type.name.lowercase().replace("_", " ")} challenge!",
                mapOf("challengeId" to challenge.id)
            )
        }
        
        return challenge
    }
    
    /**
     * Discover open challenges from the network.
     */
    suspend fun discoverChallenges(type: ChallengeType? = null): List<DHTChallenge> {
        val challenges = dhtBridge.discoverOpenChallenges(type?.name)
        challenges.forEach { discoveredChallenges[it.id] = it }
        return challenges
    }
    
    /**
     * Accept a discovered challenge.
     */
    suspend fun acceptChallenge(challengeId: String): Boolean {
        if (!currentSettings.allowChallenges) return false
        
        val success = dhtBridge.acceptChallenge(challengeId)
        if (success) {
            discoveredChallenges.remove(challengeId)
        }
        return success
    }
    
    /**
     * Respond to a challenge (local challenge from another user).
     */
    suspend fun respondToChallenge(challengeId: String, accept: Boolean): Challenge? {
        val challenge = socialManager.respondToChallenge(challengeId, accept)
        
        if (challenge != null && accept) {
            // Update DHT with acceptance
            dhtBridge.acceptChallenge(challengeId)
        }
        
        return challenge
    }
    
    /**
     * Complete a challenge with final scores.
     * Called by the challenge monitoring system.
     */
    suspend fun completeChallenge(
        challengeId: String,
        challengerScore: Double,
        challengedScore: Double
    ) {
        // Complete local challenge
        val challenge = socialManager.completeChallenge(challengeId, challengerScore, challengedScore)
            ?: return
        
        // Update DHT
        dhtBridge.completeChallenge(challengeId, challengerScore, challengedScore, challenge.winnerId)
        
        // Award badge if won
        if (challenge.winnerId == userId) {
            val profile = userProfileManager.getProfile(userId) ?: return
            profile.stats.apply {
                competitionsEntered++
                competitionsWon++
            }
            
            // Check for competition badges
            val newBadges = userProfileManager.checkAndAwardBadges(userId)
            for (badge in newBadges) {
                handleBadgeAwarded(badge)
            }
        }
    }
    
    /**
     * Get user's active and past challenges.
     */
    fun getUserChallenges(): List<Challenge> {
        return socialManager.getUserChallenges(userId)
    }
    
    // ========================================================================
    // COPY TRADING
    // ========================================================================
    
    /**
     * Start copy trading another user.
     */
    suspend fun startCopyTrading(traderId: String, settings: CopySettings): CopyTradingRelation? {
        if (!currentSettings.allowCopyTrading) return null
        
        // Check trader's reputation
        val reputation = dhtBridge.queryPeerReputation(traderId)
        if (reputation?.isFlagged == true) {
            return null
        }
        
        // Check trader allows copy trading
        val traderProfile = dhtBridge.queryProfile(traderId)
        if (traderProfile?.allowCopyTrading != true) {
            return null
        }
        
        return socialManager.startCopyTrading(userId, traderId, settings)
    }
    
    /**
     * Stop copy trading.
     */
    fun stopCopyTrading(relationId: String): Boolean {
        return socialManager.stopCopyTrading(relationId)
    }
    
    /**
     * Get copy trading relationships.
     */
    fun getCopyRelations(): CopyRelationsResult {
        return socialManager.getCopyRelations(userId)
    }
    
    // ========================================================================
    // DAILY/WEEKLY/MONTHLY CHALLENGES (PvE)
    // ========================================================================
    
    // Active daily challenges for the current user
    private val activeDailyChallenges = ConcurrentHashMap<String, DailyChallengeProgress>()
    
    /**
     * Generate and activate today's daily challenges.
     * Call this on app start and at midnight reset.
     */
    fun refreshDailyChallenges(): List<DailyChallenge> {
        val challenges = ChallengeGenerator.generateDaily()
        
        // Clear expired and add new
        activeDailyChallenges.entries.removeIf { 
            it.value.challenge.endTime < System.currentTimeMillis() 
        }
        
        challenges.forEach { challenge ->
            if (!activeDailyChallenges.containsKey(challenge.id)) {
                activeDailyChallenges[challenge.id] = DailyChallengeProgress(
                    challenge = challenge,
                    currentProgress = 0,
                    completed = false
                )
            }
        }
        
        return challenges
    }
    
    /**
     * Generate weekly challenges.
     */
    fun refreshWeeklyChallenges(): List<DailyChallenge> {
        return ChallengeGenerator.generateWeekly()
    }
    
    /**
     * Generate monthly challenges.
     */
    fun refreshMonthlyChallenges(): List<DailyChallenge> {
        return ChallengeGenerator.generateMonthly()
    }
    
    /**
     * Get current active daily challenges with progress.
     */
    fun getActiveDailyChallenges(): List<DailyChallengeProgress> {
        return activeDailyChallenges.values
            .filter { it.challenge.endTime > System.currentTimeMillis() }
            .toList()
    }
    
    /**
     * Update progress on a daily challenge.
     * Called internally when relevant actions occur.
     */
    internal suspend fun updateDailyChallengeProgress(
        challengeType: DailyChallengeType,
        incrementBy: Int = 1
    ) {
        activeDailyChallenges.values
            .filter { !it.completed && it.challenge.type == challengeType }
            .forEach { progress ->
                progress.currentProgress += incrementBy
                
                if (progress.currentProgress >= progress.challenge.target) {
                    completeDailyChallenge(progress)
                }
            }
    }
    
    /**
     * Complete a daily challenge and award XP.
     */
    private suspend fun completeDailyChallenge(progress: DailyChallengeProgress) {
        progress.completed = true
        progress.completedAt = System.currentTimeMillis()
        
        val profile = userProfileManager.getProfile(userId) ?: return
        
        // Award XP
        profile.xp += progress.challenge.xpReward.toInt()
        
        // Check for level up
        val newLevel = (profile.xp / 1000) + 1
        if (newLevel > profile.level) {
            profile.level = newLevel
            _levelUp.emit(LevelUpEvent(newLevel, profile.xp))
            
            if (currentSettings.showRealTimeActivity) {
                dhtBridge.broadcastActivity(
                    DHTActivityType.LEVEL_UP,
                    "Reached level $newLevel!",
                    mapOf("level" to newLevel)
                )
            }
        }
        
        // Emit challenge completed event
        _dailyChallengeCompleted.emit(DailyChallengeCompletedEvent(
            challengeId = progress.challenge.id,
            challengeName = progress.challenge.name,
            xpAwarded = progress.challenge.xpReward,
            newTotalXp = profile.xp
        ))
        
        // Create activity
        socialManager.createActivity(
            userId,
            ActivityType.MILESTONE_REACHED,
            "Completed challenge: ${progress.challenge.name}",
            mapOf("challengeId" to progress.challenge.id, "xp" to progress.challenge.xpReward)
        )
        
        // Check for challenge-related badges
        val newBadges = userProfileManager.checkAndAwardBadges(userId)
        for (badge in newBadges) {
            handleBadgeAwarded(badge)
        }
    }
    
    // ========================================================================
    // LEADERBOARDS
    // ========================================================================
    
    /**
     * Get ROI leaderboard.
     */
    fun getROILeaderboard(limit: Int = 100): List<LeaderboardEntry> {
        return userProfileManager.getLeaderboard(ScoringMethod.ROI_PERCENTAGE, limit)
    }
    
    /**
     * Get Sharpe ratio leaderboard.
     */
    fun getSharpeLeaderboard(limit: Int = 100): List<LeaderboardEntry> {
        return userProfileManager.getLeaderboard(ScoringMethod.SHARPE_RATIO, limit)
    }
    
    /**
     * Get social leaderboard (followers, challenges won, etc.).
     */
    fun getSocialLeaderboard(metric: SocialMetric, limit: Int = 100): List<UserSocialProfile> {
        return socialManager.getSocialLeaderboard(metric, limit)
    }
    
    // ========================================================================
    // ANTI-CHEAT
    // ========================================================================
    
    /**
     * Report suspicious activity.
     */
    suspend fun reportSuspiciousUser(
        targetUserId: String,
        cheatType: String,
        evidence: String
    ): Boolean {
        return dhtBridge.reportSuspiciousActivity(targetUserId, cheatType, evidence)
    }
    
    /**
     * Check a user's reputation before engaging.
     */
    suspend fun checkUserReputation(targetUserId: String): PeerReputation? {
        return dhtBridge.queryPeerReputation(targetUserId)
    }
    
    // ========================================================================
    // PRIVATE: Background Jobs
    // ========================================================================
    
    private fun startProfileSync() {
        profileSyncJob = scope.launch {
            while (isActive) {
                try {
                    syncProfileToDHT()
                } catch (e: Exception) {
                    // Log error, continue
                }
                delay(DHTGamificationBridge.PROFILE_SYNC_INTERVAL_MS)
            }
        }
    }
    
    private fun startSocialSync() {
        socialSyncJob = scope.launch {
            while (isActive) {
                try {
                    syncSocialToDHT()
                } catch (e: Exception) {
                    // Log error, continue
                }
                delay(DHTGamificationBridge.PROFILE_SYNC_INTERVAL_MS)
            }
        }
    }
    
    private fun startChallengeListener() {
        challengeListenerJob = scope.launch {
            dhtBridge.challengeEvents.collect { event ->
                when (event) {
                    is ChallengeEvent.Discovered -> {
                        event.challenges.forEach { challenge ->
                            if (challenge.challengedId == null || challenge.challengedId == userId) {
                                _challengeReceived.emit(ChallengeReceivedEvent(
                                    challengeId = challenge.id,
                                    challengerId = challenge.challengerId,
                                    type = challenge.type,
                                    rules = challenge.rules
                                ))
                            }
                        }
                    }
                    is ChallengeEvent.Accepted -> {
                        // Notify UI that our challenge was accepted
                        if (event.challenge.challengerId == userId) {
                            // Our challenge was accepted - could emit event here
                        }
                    }
                    is ChallengeEvent.Completed -> {
                        // Challenge finished
                    }
                    is ChallengeEvent.Published -> {
                        // Our challenge was published successfully
                    }
                }
            }
        }
    }
    
    private fun startActivityListener() {
        activityListenerJob = scope.launch {
            dhtBridge.activityEvents.collect { activity ->
                _activityFeedUpdate.emit(ActivityFeedUpdateEvent(
                    activityId = activity.id,
                    userId = activity.odId,
                    type = activity.type,
                    content = activity.content,
                    timestamp = activity.timestamp
                ))
            }
        }
    }
    
    // ========================================================================
    // PRIVATE: DHT Sync
    // ========================================================================
    
    private suspend fun syncProfileToDHT() {
        val profile = userProfileManager.getProfile(userId) ?: return
        dhtBridge.publishProfile(profile, currentSettings)
    }
    
    private suspend fun syncSocialToDHT() {
        val socialProfile = socialManager.getProfile(userId) ?: return
        
        // Sync follow graph
        dhtBridge.syncFollowGraph(socialProfile.following, socialProfile.followers)
        
        // Sync badges
        val profile = userProfileManager.getProfile(userId) ?: return
        dhtBridge.publishBadges(profile.badges)
    }
    
    // ========================================================================
    // PRIVATE: Event Handlers
    // ========================================================================
    
    private suspend fun handleBadgeAwarded(badge: UserBadge) {
        val badgeInfo = Badges.findById(badge.badgeId) ?: return
        val profile = userProfileManager.getProfile(userId) ?: return
        
        // Emit event for UI
        _badgeEarned.emit(BadgeEarnedEvent(
            badgeId = badge.badgeId,
            badgeName = badgeInfo.name,
            badgeDescription = badgeInfo.description,
            xpAwarded = badgeInfo.xpReward,
            newTotalXp = profile.xp,
            newLevel = profile.level
        ))
        
        // Check for level up
        val previousLevel = (profile.xp - badgeInfo.xpReward) / 1000 + 1
        if (profile.level > previousLevel) {
            _levelUp.emit(LevelUpEvent(
                newLevel = profile.level,
                totalXp = profile.xp
            ))
            
            // Broadcast level up to DHT
            if (currentSettings.showRealTimeActivity) {
                dhtBridge.broadcastActivity(
                    DHTActivityType.LEVEL_UP,
                    "Reached level ${profile.level}!",
                    mapOf("level" to profile.level)
                )
            }
        }
        
        // Broadcast badge earned to DHT
        if (currentSettings.showRealTimeActivity) {
            dhtBridge.broadcastActivity(
                DHTActivityType.BADGE_EARNED,
                "Earned the '${badgeInfo.name}' badge!",
                mapOf("badgeId" to badge.badgeId, "tier" to badgeInfo.tier.name)
            )
        }
        
        // Create local activity
        socialManager.createActivity(
            userId,
            ActivityType.BADGE_EARNED,
            "Earned the '${badgeInfo.name}' badge!",
            mapOf("badgeId" to badge.badgeId)
        )
        
        // Sync badges to DHT
        dhtBridge.publishBadges(profile.badges)
    }
}

// ============================================================================
// COORDINATOR EVENTS
// ============================================================================

data class BadgeEarnedEvent(
    val badgeId: String,
    val badgeName: String,
    val badgeDescription: String,
    val xpAwarded: Int,
    val newTotalXp: Int,
    val newLevel: Int
)

data class LevelUpEvent(
    val newLevel: Int,
    val totalXp: Int
)

data class ChallengeReceivedEvent(
    val challengeId: String,
    val challengerId: String,
    val type: String,
    val rules: DHTChallengeRules
)

data class ActivityFeedUpdateEvent(
    val activityId: String,
    val userId: String,
    val type: String,
    val content: String,
    val timestamp: Long
)

data class PeerProfileUpdateEvent(
    val userId: String,
    val profile: DHTUserProfile
)

data class DailyChallengeCompletedEvent(
    val challengeId: String,
    val challengeName: String,
    val xpAwarded: Long,
    val newTotalXp: Int
)

/**
 * Tracks progress on a daily/weekly/monthly challenge.
 */
data class DailyChallengeProgress(
    val challenge: DailyChallenge,
    var currentProgress: Int,
    var completed: Boolean,
    var completedAt: Long? = null
) {
    val progressPercent: Float
        get() = if (challenge.target > 0) {
            (currentProgress.toFloat() / challenge.target).coerceIn(0f, 1f)
        } else 0f
    
    val isExpired: Boolean
        get() = challenge.endTime < System.currentTimeMillis()
}
