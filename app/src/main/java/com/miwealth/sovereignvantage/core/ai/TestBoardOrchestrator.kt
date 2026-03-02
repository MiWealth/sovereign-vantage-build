package com.miwealth.sovereignvantage.core.ai

import java.util.UUID

/**
 * Test Board Orchestrator - V5.17.0
 * 
 * Fully configurable board for backtesting and experimentation.
 * Supports 1-20 members with any combination from any category.
 * 
 * Key Features:
 * - Any member combination (1-20)
 * - Configurable weights (equal or custom)
 * - Configurable casting vote
 * - Performance tracking per member
 * - Easy preset switching
 * 
 * Common Test Configurations:
 * - Single member (1): Test individual member performance
 * - Duo (2): Test member pairs
 * - Trio (3): Original backtest configuration
 * - Octagon (8): Production configuration
 * - Full (15): All members comparison
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 */
class TestBoardOrchestrator(
    private var configuration: BoardConfiguration = BoardPresets.OCTAGON
) {
    
    // Active board members based on configuration
    private var boardMembers: List<ConfiguredBoardMember>
    
    // Member with casting vote when no consensus
    private var castingVoteMember: ConfiguredBoardMember?
    
    // Performance tracking
    private val memberPerformance = mutableMapOf<BoardMemberId, MemberTestPerformance>()
    
    // Decision history for analysis
    private val decisionHistory = mutableListOf<TestDecisionRecord>()
    
    init {
        boardMembers = instantiateMembers(configuration)
        castingVoteMember = findCastingVoteMember(configuration)
        initializePerformanceTracking()
    }
    
    // Consensus thresholds (configurable for testing)
    var strongBuyThreshold = 0.6
    var buyThreshold = 0.2
    var sellThreshold = -0.2
    var strongSellThreshold = -0.6
    var consensusThreshold = 0.60
    
    companion object {
        const val MIN_MEMBERS = 1
        const val MAX_MEMBERS = 20
    }
    
    // =========================================================================
    // CONFIGURATION METHODS
    // =========================================================================
    
    /**
     * Reconfigure the board with a new configuration.
     */
    fun reconfigure(newConfiguration: BoardConfiguration) {
        configuration = newConfiguration
        boardMembers = instantiateMembers(configuration)
        castingVoteMember = findCastingVoteMember(configuration)
        initializePerformanceTracking()
    }
    
    /**
     * Reconfigure with a preset.
     */
    fun usePreset(preset: BoardConfiguration) {
        reconfigure(preset)
    }
    
    /**
     * Quick reconfigure with member IDs only (equal weights).
     */
    fun setMembers(members: List<BoardMemberId>, castingVote: BoardMemberId? = null) {
        require(members.size in MIN_MEMBERS..MAX_MEMBERS) { 
            "Must have $MIN_MEMBERS-$MAX_MEMBERS members, got ${members.size}" 
        }
        
        val config = buildBoardConfiguration("Test - ${members.size} members") {
            description("Test configuration with ${members.joinToString { it.displayName }}")
            addMembers(members)
            (castingVote ?: members.first()).let { setCastingVote(it) }
        }
        reconfigure(config)
    }
    
    /**
     * Set a single member for isolated testing.
     */
    fun setSingleMember(memberId: BoardMemberId) {
        setMembers(listOf(memberId), memberId)
    }
    
    /**
     * Set the trio configuration (for original backtest comparison).
     */
    fun setTrioConfiguration(
        member1: BoardMemberId = BoardMemberId.ARTHUR,
        member2: BoardMemberId = BoardMemberId.HELENA,
        member3: BoardMemberId = BoardMemberId.SENTINEL
    ) {
        setMembers(listOf(member1, member2, member3), member3) // Sentinel casting vote
    }
    
    /**
     * Set the original Octagon configuration.
     */
    fun setOctagonConfiguration() {
        usePreset(BoardPresets.OCTAGON)
    }
    
    /**
     * Adjust consensus thresholds for testing.
     */
    fun setThresholds(
        strongBuy: Double = 0.6,
        buy: Double = 0.2,
        sell: Double = -0.2,
        strongSell: Double = -0.6,
        consensus: Double = 0.60
    ) {
        strongBuyThreshold = strongBuy
        buyThreshold = buy
        sellThreshold = sell
        strongSellThreshold = strongSell
        consensusThreshold = consensus
    }
    
    // =========================================================================
    // BOARD OPERATIONS
    // =========================================================================
    
    /**
     * Get the current board configuration.
     */
    fun getConfiguration(): BoardConfiguration = configuration
    
    /**
     * Get the number of active members.
     */
    fun getMemberCount(): Int = boardMembers.size
    
    /**
     * Get list of active member IDs.
     */
    fun getActiveMemberIds(): List<BoardMemberId> = boardMembers.map { it.memberId }
    
    /**
     * Get list of active member names.
     */
    fun getActiveMemberNames(): List<String> = boardMembers.map { it.member.displayName }
    
    /**
     * Convene the board and reach consensus.
     */
    fun conveneBoardroom(context: MarketContext): TestBoardConsensus {
        val startTime = System.nanoTime()
        
        // Gather all opinions with timing
        val opinions = boardMembers.map { configured ->
            val opinionStart = System.nanoTime()
            val opinion = configured.member.analyze(context)
            val opinionTime = System.nanoTime() - opinionStart
            
            MemberOpinionWithMeta(
                opinion = opinion,
                memberId = configured.memberId,
                effectiveWeight = configured.effectiveWeight,
                analysisTimeNanos = opinionTime
            )
        }
        
        // Calculate weighted score
        var weightedSentiment = 0.0
        var totalWeight = 0.0
        
        for (meta in opinions) {
            weightedSentiment += meta.opinion.sentiment * meta.opinion.confidence * meta.effectiveWeight
            totalWeight += meta.effectiveWeight * meta.opinion.confidence
        }
        
        val finalScore = if (totalWeight > 0) weightedSentiment / totalWeight else 0.0
        
        // Check for consensus
        val opinionList = opinions.map { it.opinion }
        val hasConsensus = checkConsensus(opinionList)
        
        // Determine final vote
        val finalDecision = if (hasConsensus || boardMembers.size == 1) {
            scoreToVote(finalScore)
        } else {
            // No consensus - use casting vote
            castingVoteMember?.let { caster ->
                val casterOpinion = opinionList.find { it.displayName == caster.member.displayName }
                if (casterOpinion != null && casterOpinion.confidence >= 0.20) {
                    casterOpinion.vote
                } else {
                    scoreToVote(finalScore)
                }
            } ?: scoreToVote(finalScore)
        }
        
        // Count unanimous votes
        val unanimousCount = opinionList.count { opinion ->
            val opinionBullish = opinion.vote == BoardVote.BUY || opinion.vote == BoardVote.STRONG_BUY
            val decisionBullish = finalDecision == BoardVote.BUY || finalDecision == BoardVote.STRONG_BUY
            opinionBullish == decisionBullish || opinion.vote == BoardVote.HOLD
        }
        
        // Collect dissenter reasons
        val dissenterReasons = opinionList
            .filter { opinion ->
                val opinionBullish = opinion.vote == BoardVote.BUY || opinion.vote == BoardVote.STRONG_BUY
                val decisionBullish = finalDecision == BoardVote.BUY || finalDecision == BoardVote.STRONG_BUY
                opinionBullish != decisionBullish && opinion.vote != BoardVote.HOLD
            }
            .map { "${it.displayName}: ${it.reasoning}" }
        
        // Calculate overall confidence
        val confidence = opinionList.map { it.confidence }.average()
        
        val totalTime = System.nanoTime() - startTime
        
        // Synthesize decision explanation
        val synthesis = synthesizeDecision(finalDecision, opinionList, finalScore, hasConsensus)
        
        val consensus = TestBoardConsensus(
            finalDecision = finalDecision,
            weightedScore = finalScore,
            confidence = confidence,
            unanimousCount = unanimousCount,
            dissenterReasons = dissenterReasons,
            opinions = opinionList,
            opinionsMeta = opinions,
            synthesis = synthesis,
            hasConsensus = hasConsensus,
            castingVoteUsed = !hasConsensus && boardMembers.size > 1,
            castingVoteMember = if (!hasConsensus) castingVoteMember?.memberId else null,
            totalAnalysisTimeNanos = totalTime,
            memberCount = boardMembers.size,
            configurationName = configuration.name
        )
        
        // Record for history
        decisionHistory.add(TestDecisionRecord(
            timestamp = System.currentTimeMillis(),
            consensus = consensus,
            context = context
        ))
        
        return consensus
    }
    
    /**
     * Record trade outcome for performance tracking.
     */
    fun recordOutcome(
        decision: TestBoardConsensus,
        actualProfitable: Boolean,
        actualReturnPercent: Double
    ) {
        for (meta in decision.opinionsMeta) {
            val perf = memberPerformance.getOrPut(meta.memberId) { 
                MemberTestPerformance(meta.memberId) 
            }
            
            val voteCorrect = when {
                actualProfitable && meta.opinion.vote in listOf(BoardVote.BUY, BoardVote.STRONG_BUY) -> true
                !actualProfitable && meta.opinion.vote in listOf(BoardVote.SELL, BoardVote.STRONG_SELL) -> true
                meta.opinion.vote == BoardVote.HOLD && kotlin.math.abs(actualReturnPercent) < 0.5 -> true
                else -> false
            }
            
            perf.recordVote(voteCorrect, actualReturnPercent, meta.opinion.confidence)
        }
    }
    
    // =========================================================================
    // PERFORMANCE ANALYSIS
    // =========================================================================
    
    /**
     * Get performance metrics for all members.
     */
    fun getMemberPerformance(): Map<BoardMemberId, MemberTestPerformance> {
        return memberPerformance.toMap()
    }
    
    /**
     * Get performance for a specific member.
     */
    fun getMemberPerformance(memberId: BoardMemberId): MemberTestPerformance? {
        return memberPerformance[memberId]
    }
    
    /**
     * Get ranked members by accuracy.
     */
    fun getRankedMembersByAccuracy(): List<Pair<BoardMemberId, Double>> {
        return memberPerformance.entries
            .filter { it.value.totalVotes > 0 }
            .map { it.key to it.value.accuracy }
            .sortedByDescending { it.second }
    }
    
    /**
     * Get decision history.
     */
    fun getDecisionHistory(): List<TestDecisionRecord> = decisionHistory.toList()
    
    /**
     * Clear performance data and history.
     */
    fun clearPerformanceData() {
        memberPerformance.clear()
        decisionHistory.clear()
        initializePerformanceTracking()
    }
    
    /**
     * Generate performance report.
     */
    fun generatePerformanceReport(): String {
        val sb = StringBuilder()
        sb.appendLine("═══════════════════════════════════════════════════════════════════")
        sb.appendLine("              TEST BOARD PERFORMANCE REPORT")
        sb.appendLine("═══════════════════════════════════════════════════════════════════")
        sb.appendLine("Configuration: ${configuration.name}")
        sb.appendLine("Members: ${boardMembers.size}")
        sb.appendLine("Total Decisions: ${decisionHistory.size}")
        sb.appendLine()
        sb.appendLine("┌────────────────┬──────────┬──────────┬──────────┬──────────────┐")
        sb.appendLine("│ Member         │ Accuracy │ Votes    │ Avg Conf │ Avg Return   │")
        sb.appendLine("├────────────────┼──────────┼──────────┼──────────┼──────────────┤")
        
        for ((memberId, perf) in memberPerformance.entries.filter { it.value.totalVotes > 0 }.sortedByDescending { it.value.accuracy }) {
            val name = memberId.displayName.padEnd(14)
            val acc = String.format("%6.1f%%", perf.accuracy * 100)
            val votes = perf.totalVotes.toString().padStart(8)
            val conf = String.format("%6.1f%%", perf.avgConfidence * 100)
            val ret = String.format("%+9.2f%%", perf.avgReturn)
            sb.appendLine("│ $name │ $acc │ $votes │ $conf │ $ret │")
        }
        
        sb.appendLine("└────────────────┴──────────┴──────────┴──────────┴──────────────┘")
        sb.appendLine()
        sb.appendLine("═══════════════════════════════════════════════════════════════════")
        return sb.toString()
    }
    
    // =========================================================================
    // PRIVATE METHODS
    // =========================================================================
    
    private fun instantiateMembers(config: BoardConfiguration): List<ConfiguredBoardMember> {
        return config.members.map { memberId ->
            ConfiguredBoardMember(
                member = BoardMemberFactory.create(memberId),
                memberId = memberId,
                effectiveWeight = config.getWeight(memberId)
            )
        }
    }
    
    private fun findCastingVoteMember(config: BoardConfiguration): ConfiguredBoardMember? {
        return config.castingVoteMember?.let { castingId ->
            boardMembers.find { it.memberId == castingId }
        }
    }
    
    private fun initializePerformanceTracking() {
        for (member in boardMembers) {
            if (member.memberId !in memberPerformance) {
                memberPerformance[member.memberId] = MemberTestPerformance(member.memberId)
            }
        }
    }
    
    private fun checkConsensus(opinions: List<AgentOpinion>): Boolean {
        if (opinions.size == 1) return true
        
        val bullishCount = opinions.count { it.vote == BoardVote.BUY || it.vote == BoardVote.STRONG_BUY }
        val bearishCount = opinions.count { it.vote == BoardVote.SELL || it.vote == BoardVote.STRONG_SELL }
        val totalVotes = opinions.size
        
        val bullishPct = bullishCount.toDouble() / totalVotes
        val bearishPct = bearishCount.toDouble() / totalVotes
        
        return bullishPct >= consensusThreshold || bearishPct >= consensusThreshold
    }
    
    private fun scoreToVote(score: Double): BoardVote {
        return when {
            score > strongBuyThreshold -> BoardVote.STRONG_BUY
            score > buyThreshold -> BoardVote.BUY
            score < strongSellThreshold -> BoardVote.STRONG_SELL
            score < sellThreshold -> BoardVote.SELL
            else -> BoardVote.HOLD
        }
    }
    
    private fun synthesizeDecision(
        decision: BoardVote,
        opinions: List<AgentOpinion>,
        score: Double,
        hasConsensus: Boolean
    ): String {
        val bullishCount = opinions.count { it.vote == BoardVote.BUY || it.vote == BoardVote.STRONG_BUY }
        val bearishCount = opinions.count { it.vote == BoardVote.SELL || it.vote == BoardVote.STRONG_SELL }
        val totalMembers = opinions.size
        val consensusNote = if (hasConsensus || totalMembers == 1) "" else " [casting vote]"
        
        return when (decision) {
            BoardVote.STRONG_BUY -> "TEST: Strong BUY ($bullishCount/$totalMembers)$consensusNote. Score: ${String.format("%.3f", score)}"
            BoardVote.BUY -> "TEST: Moderate BUY ($bullishCount/$totalMembers)$consensusNote. Score: ${String.format("%.3f", score)}"
            BoardVote.HOLD -> "TEST: HOLD ($totalMembers members). Score: ${String.format("%.3f", score)}"
            BoardVote.SELL -> "TEST: Moderate SELL ($bearishCount/$totalMembers)$consensusNote. Score: ${String.format("%.3f", score)}"
            BoardVote.STRONG_SELL -> "TEST: Strong SELL ($bearishCount/$totalMembers)$consensusNote. Score: ${String.format("%.3f", score)}"
        }
    }
}

// ============================================================================
// DATA CLASSES
// ============================================================================

/**
 * Opinion with additional metadata for testing.
 */
data class MemberOpinionWithMeta(
    val opinion: AgentOpinion,
    val memberId: BoardMemberId,
    val effectiveWeight: Double,
    val analysisTimeNanos: Long
)

/**
 * Extended consensus for testing with additional metrics.
 */
data class TestBoardConsensus(
    val finalDecision: BoardVote,
    val weightedScore: Double,
    val confidence: Double,
    val unanimousCount: Int,
    val dissenterReasons: List<String>,
    val opinions: List<AgentOpinion>,
    val opinionsMeta: List<MemberOpinionWithMeta>,
    val synthesis: String,
    // Test-specific fields
    val hasConsensus: Boolean,
    val castingVoteUsed: Boolean,
    val castingVoteMember: BoardMemberId?,
    val totalAnalysisTimeNanos: Long,
    val memberCount: Int,
    val configurationName: String,
    val sessionId: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Convert to standard BoardConsensus.
     */
    fun toStandardConsensus(): BoardConsensus {
        return BoardConsensus(
            finalDecision = finalDecision,
            weightedScore = weightedScore,
            confidence = confidence,
            unanimousCount = unanimousCount,
            dissenterReasons = dissenterReasons,
            opinions = opinions,
            synthesis = synthesis,
            sessionId = sessionId,
            timestamp = timestamp
        )
    }
    
    /**
     * Get analysis time in milliseconds.
     */
    fun getAnalysisTimeMs(): Double = totalAnalysisTimeNanos / 1_000_000.0
}

/**
 * Record of a test decision for history tracking.
 */
data class TestDecisionRecord(
    val timestamp: Long,
    val consensus: TestBoardConsensus,
    val context: MarketContext
)

/**
 * Performance tracking for individual members.
 */
data class MemberTestPerformance(
    val memberId: BoardMemberId
) {
    var totalVotes: Int = 0
        private set
    var correctVotes: Int = 0
        private set
    var totalReturn: Double = 0.0
        private set
    var totalConfidence: Double = 0.0
        private set
    
    val accuracy: Double
        get() = if (totalVotes > 0) correctVotes.toDouble() / totalVotes else 0.0
    
    val avgReturn: Double
        get() = if (totalVotes > 0) totalReturn / totalVotes else 0.0
    
    val avgConfidence: Double
        get() = if (totalVotes > 0) totalConfidence / totalVotes else 0.0
    
    fun recordVote(correct: Boolean, returnPercent: Double, confidence: Double) {
        totalVotes++
        if (correct) correctVotes++
        totalReturn += returnPercent
        totalConfidence += confidence
    }
}

// ============================================================================
// FACTORY FUNCTIONS
// ============================================================================

/**
 * Create a test board with default Octagon configuration.
 */
fun createTestBoard(): TestBoardOrchestrator {
    return TestBoardOrchestrator(BoardPresets.OCTAGON)
}

/**
 * Create a test board with a specific preset.
 */
fun createTestBoard(preset: BoardConfiguration): TestBoardOrchestrator {
    return TestBoardOrchestrator(preset)
}

/**
 * Create a single-member test board.
 */
fun createSingleMemberTestBoard(memberId: BoardMemberId): TestBoardOrchestrator {
    return TestBoardOrchestrator().apply { setSingleMember(memberId) }
}

/**
 * Create a trio test board (original backtest configuration).
 */
fun createTrioTestBoard(
    member1: BoardMemberId = BoardMemberId.ARTHUR,
    member2: BoardMemberId = BoardMemberId.HELENA,
    member3: BoardMemberId = BoardMemberId.SENTINEL
): TestBoardOrchestrator {
    return TestBoardOrchestrator().apply { setTrioConfiguration(member1, member2, member3) }
}

/**
 * Create a custom test board with specific members.
 */
fun createCustomTestBoard(
    members: List<BoardMemberId>,
    castingVote: BoardMemberId? = null
): TestBoardOrchestrator {
    return TestBoardOrchestrator().apply { setMembers(members, castingVote) }
}
