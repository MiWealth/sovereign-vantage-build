package com.miwealth.sovereignvantage.core.ai

import java.util.UUID

/**
 * Configurable AI Board Orchestrator - V5.17.0
 * 
 * Supports 1-20 board members with flexible configuration.
 * Default: 8-member Octagon (original proven architecture)
 * 
 * This replaces the hardcoded 15-member board with a configurable system
 * that can be used for:
 * - General Trading Engine (8 core members)
 * - Hedge Fund Engine (7 specialists + 2 crossovers)
 * - Testing configurations (1-20 members)
 * 
 * v5.5.62: Hedge fund member classes extracted to HedgeFundBoardMembers.kt
 * BoardMemberFactory still creates all 15 members (classes in same package)
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 */
class ConfigurableBoardOrchestrator(
    private val configuration: BoardConfiguration = BoardPresets.OCTAGON
) {
    
    // Active board members based on configuration
    private val boardMembers: List<ConfiguredBoardMember>
    
    // Member with casting vote when no consensus
    private val castingVoteMember: ConfiguredBoardMember?
    
    init {
        // Instantiate configured members with their weights
        boardMembers = configuration.members.map { memberId ->
            ConfiguredBoardMember(
                member = BoardMemberFactory.create(memberId),
                memberId = memberId,
                effectiveWeight = configuration.getWeight(memberId)
            )
        }
        
        // Set casting vote member
        castingVoteMember = configuration.castingVoteMember?.let { castingId ->
            boardMembers.find { it.memberId == castingId }
        }
    }
    
    // Consensus thresholds
    companion object {
        const val STRONG_BUY_THRESHOLD = 0.6
        const val BUY_THRESHOLD = 0.2
        const val SELL_THRESHOLD = -0.2
        const val STRONG_SELL_THRESHOLD = -0.6
        const val CONSENSUS_THRESHOLD = 0.60 // 60% agreement for consensus
    }
    
    /**
     * Get the current board configuration.
     */
    fun getConfiguration(): BoardConfiguration = configuration
    
    /**
     * Get the number of active members.
     */
    fun getMemberCount(): Int = boardMembers.size
    
    /**
     * Get list of active member names.
     */
    fun getActiveMemberNames(): List<String> = boardMembers.map { it.member.displayName }
    
    /**
     * Convene the board and reach consensus.
     * This is the main entry point for getting trading signals.
     */
    fun conveneBoardroom(context: MarketContext): BoardConsensus {
        // Gather all opinions
        val opinions = boardMembers.map { configured ->
            val opinion = configured.member.analyze(context)
            // Apply configured weight (override member's default weight)
            opinion.copy(
                // Note: AgentOpinion doesn't have weight, it's in BoardMember
                // We track weight separately in ConfiguredBoardMember
            )
            opinion to configured.effectiveWeight
        }
        
        // Calculate weighted score
        var weightedSentiment = 0.0
        var totalWeight = 0.0
        
        for ((opinion, weight) in opinions) {
            weightedSentiment += opinion.sentiment * opinion.confidence * weight
            totalWeight += weight * opinion.confidence
        }
        
        val finalScore = if (totalWeight > 0) weightedSentiment / totalWeight else 0.0
        
        // Check for consensus
        val opinionList = opinions.map { it.first }
        val hasConsensus = checkConsensus(opinionList)
        
        // Determine final vote
        val finalDecision = if (hasConsensus) {
            scoreToVote(finalScore)
        } else {
            // No consensus - use casting vote if available
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
        
        // Synthesize decision explanation
        val synthesis = synthesizeDecision(finalDecision, opinionList, finalScore, hasConsensus)
        
        return BoardConsensus(
            finalDecision = finalDecision,
            weightedScore = finalScore,
            confidence = confidence,
            unanimousCount = unanimousCount,
            dissenterReasons = dissenterReasons,
            opinions = opinionList,
            synthesis = synthesis
        )
    }
    
    /**
     * Check if there's consensus among board members.
     */
    private fun checkConsensus(opinions: List<AgentOpinion>): Boolean {
        if (opinions.size == 1) return true // Single member always has consensus
        
        val bullishCount = opinions.count { it.vote == BoardVote.BUY || it.vote == BoardVote.STRONG_BUY }
        val bearishCount = opinions.count { it.vote == BoardVote.SELL || it.vote == BoardVote.STRONG_SELL }
        val totalVotes = opinions.size
        
        val bullishPct = bullishCount.toDouble() / totalVotes
        val bearishPct = bearishCount.toDouble() / totalVotes
        
        return bullishPct >= CONSENSUS_THRESHOLD || bearishPct >= CONSENSUS_THRESHOLD
    }
    
    /**
     * Convert weighted score to vote.
     */
    private fun scoreToVote(score: Double): BoardVote {
        return when {
            score > STRONG_BUY_THRESHOLD -> BoardVote.STRONG_BUY
            score > BUY_THRESHOLD -> BoardVote.BUY
            score < STRONG_SELL_THRESHOLD -> BoardVote.STRONG_SELL
            score < SELL_THRESHOLD -> BoardVote.SELL
            else -> BoardVote.HOLD
        }
    }
    
    /**
     * Synthesize human-readable decision explanation.
     */
    private fun synthesizeDecision(
        decision: BoardVote,
        opinions: List<AgentOpinion>,
        score: Double,
        hasConsensus: Boolean
    ): String {
        val bullishCount = opinions.count { it.vote == BoardVote.BUY || it.vote == BoardVote.STRONG_BUY }
        val bearishCount = opinions.count { it.vote == BoardVote.SELL || it.vote == BoardVote.STRONG_SELL }
        val totalMembers = opinions.size
        val consensusNote = if (hasConsensus) "" else " (casting vote applied)"
        
        return when (decision) {
            BoardVote.STRONG_BUY -> "Strong consensus to BUY ($bullishCount/$totalMembers)$consensusNote. Score: ${String.format("%.3f", score)}"
            BoardVote.BUY -> "Moderate BUY signal ($bullishCount/$totalMembers)$consensusNote. Score: ${String.format("%.3f", score)}"
            BoardVote.HOLD -> "Mixed signals - HOLD recommended ($totalMembers members). Score: ${String.format("%.3f", score)}"
            BoardVote.SELL -> "Moderate SELL signal ($bearishCount/$totalMembers)$consensusNote. Score: ${String.format("%.3f", score)}"
            BoardVote.STRONG_SELL -> "Strong consensus to SELL ($bearishCount/$totalMembers)$consensusNote. Score: ${String.format("%.3f", score)}"
        }
    }
    
    /**
     * Create a decision record for XAI audit trail.
     */
    fun createDecisionRecord(
        symbol: String,
        timeframe: String,
        context: MarketContext,
        consensus: BoardConsensus,
        actionTaken: String,
        reasonForAction: String
    ): BoardDecisionRecord {
        return BoardDecisionRecord(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            symbol = symbol,
            timeframe = timeframe,
            marketContext = MarketContextSnapshot(
                currentPrice = context.currentPrice,
                change24h = calculateChange24h(context.closes),
                volume24h = context.volumes.takeLast(24).sum(),
                high24h = context.highs.takeLast(24).maxOrNull() ?: context.currentPrice,
                low24h = context.lows.takeLast(24).minOrNull() ?: context.currentPrice,
                macroSentiment = context.macroSentiment,
                macroScore = context.macroScore,
                macroRiskLevel = context.macroRiskLevel,
                upcomingHighImpactEvents = context.upcomingHighImpactEvents ?: 0,
                macroNarrative = context.macroNarrative
            ),
            individualVotes = consensus.opinions.map { opinion ->
                MemberVoteRecord(
                    memberId = opinion.agentName,
                    displayName = opinion.displayName,
                    role = opinion.role,
                    vote = opinion.vote,
                    sentiment = opinion.sentiment,
                    confidence = opinion.confidence,
                    weight = boardMembers.find { it.member.displayName == opinion.displayName }?.effectiveWeight ?: 0.0,
                    reasoning = opinion.reasoning,
                    keyIndicators = opinion.keyIndicators
                )
            },
            consensus = consensus,
            actionTaken = actionTaken,
            reasonForAction = reasonForAction
        )
    }
    
    private fun calculateChange24h(closes: List<Double>): Double {
        if (closes.size < 24) return 0.0
        val current = closes.last()
        val previous = closes[closes.size - 24]
        return if (previous > 0) ((current - previous) / previous) * 100 else 0.0
    }
}

/**
 * Wrapper for a board member with its configured weight.
 */
data class ConfiguredBoardMember(
    val member: BoardMember,
    val memberId: BoardMemberId,
    val effectiveWeight: Double
)

/**
 * Factory for creating board member instances from their IDs.
 */
object BoardMemberFactory {
    
    /**
     * Create a board member instance from its ID.
     */
    fun create(memberId: BoardMemberId): BoardMember {
        return when (memberId) {
            // Core Octagon Members
            BoardMemberId.ARTHUR -> TrendFollower()
            BoardMemberId.HELENA -> MeanReverter()
            BoardMemberId.SENTINEL -> VolatilityTrader()
            BoardMemberId.ORACLE -> SentimentAnalyst()
            BoardMemberId.NEXUS -> OnChainAnalyst()
            BoardMemberId.MARCUS -> MacroStrategist()
            BoardMemberId.CIPHER -> PatternRecognizer()
            BoardMemberId.AEGIS -> LiquidityHunter()
            
            // Hedge Fund Specialists
            BoardMemberId.SOROS -> GlobalMacroAnalyst()
            BoardMemberId.GUARDIAN -> LiquidationCascadeDetector()
            BoardMemberId.DRAPER -> DeFiSpecialist()
            BoardMemberId.ATLAS -> RegimeMetaStrategist()
            BoardMemberId.THETA -> FundingRateArbitrageAnalyst()
            
            // Crossover Members
            BoardMemberId.MOBY -> WhaleTracker()
            BoardMemberId.ECHO -> OrderBookImbalanceAnalyst()
        }
    }
    
    /**
     * Create multiple board members from a list of IDs.
     */
    fun createAll(memberIds: List<BoardMemberId>): List<BoardMember> {
        return memberIds.map { create(it) }
    }
}

// ============================================================================
// CONVENIENCE FACTORY FUNCTIONS
// ============================================================================

/**
 * Create an 8-member Octagon board (recommended for general trading).
 */
fun createOctagonBoard(): ConfigurableBoardOrchestrator {
    return ConfigurableBoardOrchestrator(BoardPresets.OCTAGON)
}

/**
 * Create a single-member test board.
 */
fun createSingleMemberBoard(memberId: BoardMemberId): ConfigurableBoardOrchestrator {
    val config = buildBoardConfiguration("Single - ${memberId.displayName}") {
        description("Single member test with ${memberId.displayName}")
        addMember(memberId)
        setCastingVote(memberId)
    }
    return ConfigurableBoardOrchestrator(config)
}

/**
 * Create a custom board with specified members.
 */
fun createCustomBoard(
    name: String,
    members: List<BoardMemberId>,
    castingVote: BoardMemberId? = null
): ConfigurableBoardOrchestrator {
    val config = buildBoardConfiguration(name) {
        description("Custom board: ${members.joinToString { it.displayName }}")
        addMembers(members)
        castingVote?.let { setCastingVote(it) } ?: setCastingVote(members.first())
    }
    return ConfigurableBoardOrchestrator(config)
}

/**
 * Create a board from a preset.
 */
fun createBoardFromPreset(preset: BoardConfiguration): ConfigurableBoardOrchestrator {
    return ConfigurableBoardOrchestrator(preset)
}
