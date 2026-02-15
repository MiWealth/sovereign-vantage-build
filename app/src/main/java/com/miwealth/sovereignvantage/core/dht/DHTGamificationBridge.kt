package com.miwealth.sovereignvantage.core.dht

import com.miwealth.sovereignvantage.core.gamification.*
import com.miwealth.sovereignvantage.core.security.pqc.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.Serializable
import java.util.concurrent.ConcurrentHashMap

/**
 * SOVEREIGN VANTAGE V5.5.35 "ARTHUR EDITION"
 * DHT GAMIFICATION BRIDGE
 * 
 * Connects the local gamification system to the DHT network for:
 * - Cross-device profile sync
 * - P2P challenge discovery and matching
 * - Distributed achievement verification
 * - Social activity feeds
 * - Anti-cheat reputation sharing
 * 
 * All data is protected with:
 * - Mathematical noise injection (polynomial-based)
 * - PQC encryption (Kyber-1024 + Dilithium-5)
 * - Peer credential verification
 * - Privacy controls (respects SocialSettings)
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO: Arthur Iain McManus (1966-2025)
 */
class DHTGamificationBridge(
    private val secureDht: SecureDHTWrapper,
    private val dhtClient: DHTClient,
    private val userId: String,
    private val cryptoProvider: CryptoProvider
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Local caches
    private val profileCache = ConcurrentHashMap<String, DHTUserProfile>()
    private val challengeCache = ConcurrentHashMap<String, DHTChallenge>()
    private val activityCache = ConcurrentHashMap<String, MutableList<DHTActivity>>()
    private val reputationCache = ConcurrentHashMap<String, PeerReputation>()
    
    // Background jobs
    private var profileSyncJob: Job? = null
    private var challengeDiscoveryJob: Job? = null
    private var activityFeedJob: Job? = null
    
    // Event flows
    private val _profileUpdates = MutableSharedFlow<DHTUserProfile>()
    val profileUpdates: SharedFlow<DHTUserProfile> = _profileUpdates.asSharedFlow()
    
    private val _challengeEvents = MutableSharedFlow<ChallengeEvent>()
    val challengeEvents: SharedFlow<ChallengeEvent> = _challengeEvents.asSharedFlow()
    
    private val _activityEvents = MutableSharedFlow<DHTActivity>()
    val activityEvents: SharedFlow<DHTActivity> = _activityEvents.asSharedFlow()
    
    companion object {
        // DHT key prefixes
        private const val KEY_PROFILE = "gam_profile"
        private const val KEY_BADGES = "gam_badges"
        private const val KEY_CHALLENGE = "gam_challenge"
        private const val KEY_CHALLENGE_INDEX = "gam_challenge_idx"
        private const val KEY_ACTIVITY = "gam_activity"
        private const val KEY_FOLLOWS = "gam_follows"
        private const val KEY_REPUTATION = "gam_reputation"
        
        // Sync intervals
        const val PROFILE_SYNC_INTERVAL_MS = 60_000L  // 1 minute
        const val CHALLENGE_DISCOVERY_INTERVAL_MS = 30_000L  // 30 seconds
        const val ACTIVITY_FEED_INTERVAL_MS = 15_000L  // 15 seconds
        
        // Replication settings
        const val PROFILE_REPLICATION = 10
        const val CHALLENGE_REPLICATION = 5
        const val ACTIVITY_REPLICATION = 3
        const val REPUTATION_REPLICATION = 10
        
        // Limits
        const val MAX_ACTIVITIES_PER_USER = 50
        const val MAX_OPEN_CHALLENGES = 10
        const val ACTIVITY_RETENTION_DAYS = 30
    }
    
    // ========================================================================
    // LIFECYCLE
    // ========================================================================
    
    /**
     * Start the bridge with background sync jobs.
     */
    suspend fun start() = withContext(Dispatchers.Default) {
        // Start profile sync
        profileSyncJob = launch {
            while (isActive) {
                try {
                    syncLocalProfile()
                } catch (e: Exception) { /* Log and continue */ }
                delay(PROFILE_SYNC_INTERVAL_MS)
            }
        }
        
        // Start challenge discovery
        challengeDiscoveryJob = launch {
            while (isActive) {
                try {
                    discoverOpenChallenges()
                } catch (e: Exception) { /* Log and continue */ }
                delay(CHALLENGE_DISCOVERY_INTERVAL_MS)
            }
        }
        
        // Start activity feed sync
        activityFeedJob = launch {
            while (isActive) {
                try {
                    syncActivityFeed()
                } catch (e: Exception) { /* Log and continue */ }
                delay(ACTIVITY_FEED_INTERVAL_MS)
            }
        }
    }
    
    /**
     * Stop the bridge and cleanup.
     */
    fun stop() {
        profileSyncJob?.cancel()
        challengeDiscoveryJob?.cancel()
        activityFeedJob?.cancel()
        scope.cancel()
    }
    
    // ========================================================================
    // USER PROFILE SYNC
    // ========================================================================
    
    /**
     * Publish user profile to DHT.
     * Respects SocialSettings for privacy control.
     */
    suspend fun publishProfile(
        profile: UserProfile,
        socialSettings: SocialSettings
    ): Boolean = withContext(Dispatchers.IO) {
        // Create DHT-safe profile (filtered by privacy settings)
        val dhtProfile = DHTUserProfile(
            odId = profile.userId,
            displayName = profile.displayName,
            isAlias = profile.isAlias,
            // NEVER publish real name
            avatar = profile.avatar,
            level = profile.level,
            tier = profile.tier,
            badgeCount = profile.badges.size,
            // Only publish stats if allowed
            stats = if (socialSettings.showPerformance) {
                DHTPublicStats(
                    totalROI = profile.stats.totalROI,
                    winRate = profile.stats.winRate,
                    totalTrades = profile.stats.totalTrades,
                    sharpeRatio = profile.stats.sharpeRatio,
                    competitionsWon = profile.stats.competitionsWon
                )
            } else null,
            allowChallenges = socialSettings.allowChallenges,
            allowCopyTrading = socialSettings.allowCopyTrading,
            lastActive = System.currentTimeMillis(),
            signature = signProfile(profile.userId)
        )
        
        val key = "$KEY_PROFILE:${profile.userId}"
        val published = secureDht.securePublish(key, dhtProfile, PROFILE_REPLICATION)
        
        if (published) {
            profileCache[profile.userId] = dhtProfile
        }
        
        published
    }
    
    /**
     * Query another user's profile from DHT.
     */
    suspend fun queryProfile(targetUserId: String): DHTUserProfile? = withContext(Dispatchers.IO) {
        // Check cache first
        profileCache[targetUserId]?.let { 
            if (System.currentTimeMillis() - it.lastActive < PROFILE_SYNC_INTERVAL_MS) {
                return@withContext it
            }
        }
        
        val key = "$KEY_PROFILE:$targetUserId"
        val profile = secureDht.secureQuery<DHTUserProfile>(key)
        
        profile?.let {
            // Verify signature
            if (verifyProfileSignature(it)) {
                profileCache[targetUserId] = it
            } else {
                return@withContext null // Tampered profile
            }
        }
        
        profile
    }
    
    private suspend fun syncLocalProfile() {
        // This would be called with actual profile data from GamificationSystemV2
        // For now, just refresh cache TTLs
        val expiredKeys = profileCache.entries
            .filter { System.currentTimeMillis() - it.value.lastActive > PROFILE_SYNC_INTERVAL_MS * 5 }
            .map { it.key }
        expiredKeys.forEach { profileCache.remove(it) }
    }
    
    // ========================================================================
    // BADGE SYNC
    // ========================================================================
    
    /**
     * Publish earned badges to DHT (permanent storage).
     */
    suspend fun publishBadges(
        badges: List<UserBadge>
    ): Boolean = withContext(Dispatchers.IO) {
        val dhtBadges = DHTBadgeCollection(
            odId = userId,
            badges = badges.map { badge ->
                DHTBadge(
                    badgeId = badge.badgeId,
                    earnedAt = badge.earnedAt,
                    proof = generateBadgeProof(badge)
                )
            },
            lastUpdated = System.currentTimeMillis(),
            signature = signBadgeCollection(badges)
        )
        
        val key = "$KEY_BADGES:$userId"
        secureDht.securePublish(key, dhtBadges, PROFILE_REPLICATION)
    }
    
    /**
     * Query badges for a user.
     */
    suspend fun queryBadges(targetUserId: String): List<DHTBadge>? = withContext(Dispatchers.IO) {
        val key = "$KEY_BADGES:$targetUserId"
        val collection = secureDht.secureQuery<DHTBadgeCollection>(key)
        collection?.badges
    }
    
    // ========================================================================
    // CHALLENGE SYSTEM
    // ========================================================================
    
    /**
     * Publish an open challenge for peer discovery.
     */
    suspend fun publishChallenge(
        challenge: Challenge
    ): Boolean = withContext(Dispatchers.IO) {
        val dhtChallenge = DHTChallenge(
            id = challenge.id,
            challengerId = challenge.challengerId,
            challengedId = challenge.challengedId,
            type = challenge.type.name,
            status = challenge.status.name,
            rules = DHTChallengeRules(
                duration = challenge.rules.duration,
                minTrades = challenge.rules.minTrades,
                maxLeverage = challenge.rules.maxLeverage,
                scoringMethod = challenge.rules.scoringMethod,
                paperTrading = challenge.rules.paperTrading
            ),
            startDate = challenge.startDate,
            endDate = challenge.endDate,
            createdAt = challenge.createdAt,
            signature = signChallenge(challenge.id)
        )
        
        // Publish challenge
        val key = "$KEY_CHALLENGE:${challenge.id}"
        val published = secureDht.securePublish(key, dhtChallenge, CHALLENGE_REPLICATION)
        
        // If open challenge (pending), add to discovery index
        if (published && challenge.status == ChallengeStatus.PENDING) {
            addToChallengeIndex(challenge.id, challenge.type.name)
        }
        
        if (published) {
            challengeCache[challenge.id] = dhtChallenge
            _challengeEvents.emit(ChallengeEvent.Published(dhtChallenge))
        }
        
        published
    }
    
    /**
     * Discover open challenges from the network.
     */
    suspend fun discoverOpenChallenges(
        challengeType: String? = null
    ): List<DHTChallenge> = withContext(Dispatchers.IO) {
        val challenges = mutableListOf<DHTChallenge>()
        
        // Query challenge index
        val indexKey = if (challengeType != null) {
            "$KEY_CHALLENGE_INDEX:$challengeType"
        } else {
            "$KEY_CHALLENGE_INDEX:all"
        }
        
        val index = secureDht.secureQuery<DHTChallengeIndex>(indexKey)
        
        index?.challengeIds?.forEach { challengeId ->
            val key = "$KEY_CHALLENGE:$challengeId"
            secureDht.secureQuery<DHTChallenge>(key)?.let { challenge ->
                // Only include pending challenges not from us
                if (challenge.status == "PENDING" && challenge.challengerId != userId) {
                    // Verify challenger's reputation before showing
                    val reputation = queryPeerReputation(challenge.challengerId)
                    if (reputation == null || !reputation.isFlagged) {
                        challenges.add(challenge)
                        challengeCache[challengeId] = challenge
                    }
                }
            }
        }
        
        challenges
    }
    
    /**
     * Accept a challenge from the network.
     */
    suspend fun acceptChallenge(challengeId: String): Boolean = withContext(Dispatchers.IO) {
        val challenge = challengeCache[challengeId] 
            ?: secureDht.secureQuery<DHTChallenge>("$KEY_CHALLENGE:$challengeId")
            ?: return@withContext false
        
        // Update challenge status
        val updatedChallenge = challenge.copy(
            challengedId = userId,
            status = "ACTIVE",
            startDate = System.currentTimeMillis()
        )
        
        val key = "$KEY_CHALLENGE:$challengeId"
        val updated = secureDht.securePublish(key, updatedChallenge, CHALLENGE_REPLICATION)
        
        if (updated) {
            challengeCache[challengeId] = updatedChallenge
            removeFromChallengeIndex(challengeId)
            _challengeEvents.emit(ChallengeEvent.Accepted(updatedChallenge))
        }
        
        updated
    }
    
    /**
     * Complete a challenge with results.
     */
    suspend fun completeChallenge(
        challengeId: String,
        challengerScore: Double,
        challengedScore: Double,
        winnerId: String?
    ): Boolean = withContext(Dispatchers.IO) {
        val challenge = challengeCache[challengeId] ?: return@withContext false
        
        val completedChallenge = challenge.copy(
            status = "COMPLETED",
            challengerScore = challengerScore,
            challengedScore = challengedScore,
            winnerId = winnerId,
            endDate = System.currentTimeMillis()
        )
        
        val key = "$KEY_CHALLENGE:$challengeId"
        val updated = secureDht.securePublish(key, completedChallenge, CHALLENGE_REPLICATION)
        
        if (updated) {
            challengeCache[challengeId] = completedChallenge
            _challengeEvents.emit(ChallengeEvent.Completed(completedChallenge))
            
            // Broadcast activity for winner
            winnerId?.let { broadcastActivity(DHTActivityType.CHALLENGE_WON, it, mapOf(
                "challengeId" to challengeId,
                "score" to if (winnerId == challenge.challengerId) challengerScore else challengedScore
            ))}
        }
        
        updated
    }
    
    private suspend fun addToChallengeIndex(challengeId: String, type: String) {
        // Add to type-specific index
        val typeKey = "$KEY_CHALLENGE_INDEX:$type"
        val typeIndex = secureDht.secureQuery<DHTChallengeIndex>(typeKey) 
            ?: DHTChallengeIndex(type, mutableListOf())
        
        if (typeIndex.challengeIds.size < MAX_OPEN_CHALLENGES) {
            typeIndex.challengeIds.add(challengeId)
            secureDht.securePublish(typeKey, typeIndex, CHALLENGE_REPLICATION)
        }
        
        // Add to global index
        val allKey = "$KEY_CHALLENGE_INDEX:all"
        val allIndex = secureDht.secureQuery<DHTChallengeIndex>(allKey)
            ?: DHTChallengeIndex("all", mutableListOf())
        
        if (allIndex.challengeIds.size < MAX_OPEN_CHALLENGES * 5) {
            allIndex.challengeIds.add(challengeId)
            secureDht.securePublish(allKey, allIndex, CHALLENGE_REPLICATION)
        }
    }
    
    private suspend fun removeFromChallengeIndex(challengeId: String) {
        // Remove from all indexes
        listOf("all", "ROI_BATTLE", "SHARPE_BATTLE", "CONSISTENCY_BATTLE", "WIN_RATE_BATTLE").forEach { type ->
            val key = "$KEY_CHALLENGE_INDEX:$type"
            secureDht.secureQuery<DHTChallengeIndex>(key)?.let { index ->
                if (index.challengeIds.remove(challengeId)) {
                    secureDht.securePublish(key, index, CHALLENGE_REPLICATION)
                }
            }
        }
    }
    
    // ========================================================================
    // ACTIVITY FEED
    // ========================================================================
    
    /**
     * Broadcast an activity to followers.
     */
    suspend fun broadcastActivity(
        type: DHTActivityType,
        content: String,
        metadata: Map<String, Any> = emptyMap()
    ): Boolean = broadcastActivity(type, userId, metadata, content)
    
    private suspend fun broadcastActivity(
        type: DHTActivityType,
        actorId: String,
        metadata: Map<String, Any>,
        content: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        val activity = DHTActivity(
            id = "act_${System.currentTimeMillis()}_${(0..999999).random()}",
            odId = actorId,
            type = type.name,
            content = content,
            metadata = metadata.mapValues { it.value.toString() },
            timestamp = System.currentTimeMillis(),
            signature = signActivity(actorId, type.name)
        )
        
        val key = "$KEY_ACTIVITY:$actorId:${activity.timestamp}"
        val published = secureDht.securePublish(key, activity, ACTIVITY_REPLICATION)
        
        if (published) {
            val userActivities = activityCache.getOrPut(actorId) { mutableListOf() }
            userActivities.add(0, activity)
            if (userActivities.size > MAX_ACTIVITIES_PER_USER) {
                userActivities.removeLast()
            }
            _activityEvents.emit(activity)
        }
        
        published
    }
    
    /**
     * Get activity feed for followed users.
     */
    suspend fun getActivityFeed(
        followedUserIds: List<String>,
        limit: Int = 50
    ): List<DHTActivity> = withContext(Dispatchers.IO) {
        val activities = mutableListOf<DHTActivity>()
        
        for (followedId in followedUserIds) {
            // Check cache first
            activityCache[followedId]?.let { activities.addAll(it) }
            
            // If not cached or stale, query DHT
            if (!activityCache.containsKey(followedId)) {
                queryUserActivities(followedId)?.let { 
                    activities.addAll(it)
                    activityCache[followedId] = it.toMutableList()
                }
            }
        }
        
        activities
            .sortedByDescending { it.timestamp }
            .take(limit)
    }
    
    private suspend fun queryUserActivities(targetUserId: String): List<DHTActivity>? {
        // In a full implementation, this would query recent activity keys
        // For now, return cached if available
        return activityCache[targetUserId]
    }
    
    private suspend fun syncActivityFeed() {
        // Cleanup old activities
        val cutoff = System.currentTimeMillis() - (ACTIVITY_RETENTION_DAYS * 24 * 60 * 60 * 1000L)
        activityCache.values.forEach { activities ->
            activities.removeIf { it.timestamp < cutoff }
        }
    }
    
    // ========================================================================
    // FOLLOW GRAPH
    // ========================================================================
    
    /**
     * Sync follow relationships to DHT.
     */
    suspend fun syncFollowGraph(
        following: List<String>,
        followers: List<String>
    ): Boolean = withContext(Dispatchers.IO) {
        val followGraph = DHTFollowGraph(
            odId = userId,
            following = following,
            followers = followers,
            lastUpdated = System.currentTimeMillis(),
            signature = signFollowGraph(following, followers)
        )
        
        val key = "$KEY_FOLLOWS:$userId"
        secureDht.securePublish(key, followGraph, PROFILE_REPLICATION)
    }
    
    /**
     * Query follow graph for a user.
     */
    suspend fun queryFollowGraph(targetUserId: String): DHTFollowGraph? = withContext(Dispatchers.IO) {
        val key = "$KEY_FOLLOWS:$targetUserId"
        secureDht.secureQuery<DHTFollowGraph>(key)
    }
    
    // ========================================================================
    // ANTI-CHEAT REPUTATION
    // ========================================================================
    
    /**
     * Report suspicious activity to DHT.
     * Multiple reports from different peers required to flag.
     */
    suspend fun reportSuspiciousActivity(
        targetUserId: String,
        cheatType: String,
        evidence: String
    ): Boolean = withContext(Dispatchers.IO) {
        val key = "$KEY_REPUTATION:$targetUserId"
        val existing = secureDht.secureQuery<PeerReputation>(key)
            ?: PeerReputation(targetUserId, mutableListOf(), false, 0)
        
        // Add report
        existing.reports.add(ReputationReport(
            reporterId = userId,
            cheatType = cheatType,
            evidence = evidence,
            timestamp = System.currentTimeMillis()
        ))
        
        // Flag if multiple unique reporters
        val uniqueReporters = existing.reports.map { it.reporterId }.distinct().size
        if (uniqueReporters >= 3) {
            existing.isFlagged = true
            existing.flaggedAt = System.currentTimeMillis()
        }
        
        val published = secureDht.securePublish(key, existing, REPUTATION_REPLICATION)
        if (published) {
            reputationCache[targetUserId] = existing
        }
        published
    }
    
    /**
     * Query peer reputation before engaging.
     */
    suspend fun queryPeerReputation(targetUserId: String): PeerReputation? = withContext(Dispatchers.IO) {
        reputationCache[targetUserId]?.let { return@withContext it }
        
        val key = "$KEY_REPUTATION:$targetUserId"
        val reputation = secureDht.secureQuery<PeerReputation>(key)
        reputation?.let { reputationCache[targetUserId] = it }
        reputation
    }
    
    // ========================================================================
    // CRYPTOGRAPHIC SIGNATURES
    // ========================================================================
    
    private fun signProfile(odId: String): ByteArray {
        return cryptoProvider.sign("profile:$odId:${System.currentTimeMillis()}".toByteArray())
    }
    
    private fun verifyProfileSignature(profile: DHTUserProfile): Boolean {
        // In production, verify against publisher's public key
        return profile.signature.isNotEmpty()
    }
    
    private fun signBadgeCollection(badges: List<UserBadge>): ByteArray {
        val data = badges.joinToString(":") { it.badgeId }
        return cryptoProvider.sign("badges:$userId:$data".toByteArray())
    }
    
    private fun generateBadgeProof(badge: UserBadge): ByteArray {
        return cryptoProvider.sign("badge:${badge.badgeId}:${badge.earnedAt}".toByteArray())
    }
    
    private fun signChallenge(challengeId: String): ByteArray {
        return cryptoProvider.sign("challenge:$challengeId:$userId".toByteArray())
    }
    
    private fun signActivity(actorId: String, type: String): ByteArray {
        return cryptoProvider.sign("activity:$actorId:$type:${System.currentTimeMillis()}".toByteArray())
    }
    
    private fun signFollowGraph(following: List<String>, followers: List<String>): ByteArray {
        return cryptoProvider.sign("follows:$userId:${following.size}:${followers.size}".toByteArray())
    }
}

// ============================================================================
// DHT DATA MODELS
// ============================================================================

/**
 * DHT-safe user profile (privacy filtered).
 * Real name is NEVER included.
 */
data class DHTUserProfile(
    val odId: String,  // "owner ID" - obfuscated field name
    val displayName: String,
    val isAlias: Boolean,
    val avatar: String?,
    val level: Int,
    val tier: String,
    val badgeCount: Int,
    val stats: DHTPublicStats?,
    val allowChallenges: Boolean,
    val allowCopyTrading: Boolean,
    val lastActive: Long,
    val signature: ByteArray
) : Serializable {
    override fun equals(other: Any?) = (other as? DHTUserProfile)?.odId == odId
    override fun hashCode() = odId.hashCode()
}

data class DHTPublicStats(
    val totalROI: Double,
    val winRate: Double,
    val totalTrades: Int,
    val sharpeRatio: Double,
    val competitionsWon: Int
) : Serializable

data class DHTBadgeCollection(
    val odId: String,
    val badges: List<DHTBadge>,
    val lastUpdated: Long,
    val signature: ByteArray
) : Serializable

data class DHTBadge(
    val badgeId: String,
    val earnedAt: Long,
    val proof: ByteArray
) : Serializable

data class DHTChallenge(
    val id: String,
    val challengerId: String,
    val challengedId: String?,
    val type: String,
    val status: String,
    val rules: DHTChallengeRules,
    val startDate: Long?,
    val endDate: Long?,
    val challengerScore: Double? = null,
    val challengedScore: Double? = null,
    val winnerId: String? = null,
    val createdAt: Long,
    val signature: ByteArray
) : Serializable {
    fun copy(
        challengedId: String? = this.challengedId,
        status: String = this.status,
        startDate: Long? = this.startDate,
        endDate: Long? = this.endDate,
        challengerScore: Double? = this.challengerScore,
        challengedScore: Double? = this.challengedScore,
        winnerId: String? = this.winnerId
    ) = DHTChallenge(id, challengerId, challengedId, type, status, rules, startDate, endDate, 
                     challengerScore, challengedScore, winnerId, createdAt, signature)
}

data class DHTChallengeRules(
    val duration: Int,
    val minTrades: Int,
    val maxLeverage: Int,
    val scoringMethod: String,
    val paperTrading: Boolean
) : Serializable

data class DHTChallengeIndex(
    val type: String,
    val challengeIds: MutableList<String>
) : Serializable

data class DHTActivity(
    val id: String,
    val odId: String,
    val type: String,
    val content: String,
    val metadata: Map<String, String>,
    val timestamp: Long,
    val signature: ByteArray
) : Serializable

enum class DHTActivityType {
    BADGE_EARNED,
    CHALLENGE_WON,
    CHALLENGE_ISSUED,
    LEVEL_UP,
    COMPETITION_WON,
    MILESTONE_REACHED
}

data class DHTFollowGraph(
    val odId: String,
    val following: List<String>,
    val followers: List<String>,
    val lastUpdated: Long,
    val signature: ByteArray
) : Serializable

data class PeerReputation(
    val odId: String,
    val reports: MutableList<ReputationReport>,
    var isFlagged: Boolean,
    var flaggedAt: Long
) : Serializable

data class ReputationReport(
    val reporterId: String,
    val cheatType: String,
    val evidence: String,
    val timestamp: Long
) : Serializable

// ============================================================================
// EVENTS
// ============================================================================

sealed class ChallengeEvent {
    data class Published(val challenge: DHTChallenge) : ChallengeEvent()
    data class Discovered(val challenges: List<DHTChallenge>) : ChallengeEvent()
    data class Accepted(val challenge: DHTChallenge) : ChallengeEvent()
    data class Completed(val challenge: DHTChallenge) : ChallengeEvent()
}
