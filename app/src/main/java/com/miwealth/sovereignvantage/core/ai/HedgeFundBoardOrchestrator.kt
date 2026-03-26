package com.miwealth.sovereignvantage.core.ai

import java.util.UUID
import com.miwealth.sovereignvantage.core.utils.SystemLogger

/**
 * Hedge Fund Board Orchestrator - V5.17.0
 * 
 * Specialized board for hedge fund trading strategies.
 * Supports 1-20 members with flexible configuration.
 * Default: 7 specialists + 2 crossovers (9 members)
 * 
 * ⚠️ STATUS: UNWIRED - Not connected to trading engine yet
 * 
 * Hedge Fund Specialists:
 * - Soros (Global Macro) - Central bank policy, macro sentiment
 * - Guardian (Cascade Detection) - Liquidation cascade protection
 * - Draper (DeFi) - Protocol analysis, TVL, yield
 * - Atlas (Regime Meta-Strategy) - HMM regime classification
 * - Theta (Funding Rate Arbitrage) - Perpetual basis, carry trades
 * 
 * Crossover Members (shared with General Engine):
 * - Moby (Whale Tracking) - Smart money flows
 * - Echo (Order Book Imbalance) - Microstructure analysis
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 */
class HedgeFundBoardOrchestrator(
    private val configuration: BoardConfiguration = BoardPresets.HEDGE_FUND_FULL,
    private val includeCrossovers: Boolean = true
) {
    
    // Active board members based on configuration
    private val boardMembers: List<ConfiguredBoardMember>
    
    // Member with casting vote when no consensus
    private val castingVoteMember: ConfiguredBoardMember?
    
    init {
        // Validate configuration uses hedge fund or crossover members
        val validMembers = BoardMemberId.getHedgeFundMemberIds() + BoardMemberId.getCrossoverMemberIds()
        val invalidMembers = configuration.members.filter { it !in validMembers && it.category == MemberCategory.CORE }
        
        // Allow core members to be added explicitly (e.g., Arthur for trend guidance)
        // but log a warning in production
        
        // Instantiate configured members with their weights
        boardMembers = configuration.members.map { memberId ->
            ConfiguredBoardMember(
                member = BoardMemberFactory.create(memberId),
                memberId = memberId,
                effectiveWeight = configuration.getWeight(memberId)
            )
        }
        
        // Set casting vote member (default: Guardian for risk focus)
        castingVoteMember = configuration.castingVoteMember?.let { castingId ->
            boardMembers.find { it.memberId == castingId }
        } ?: boardMembers.find { it.memberId == BoardMemberId.GUARDIAN }
    }
    
    // Consensus thresholds - more conservative for hedge fund
    companion object {
        const val STRONG_BUY_THRESHOLD = 0.65  // Higher threshold for HF
        const val BUY_THRESHOLD = 0.25
        const val SELL_THRESHOLD = -0.25
        const val STRONG_SELL_THRESHOLD = -0.65
        const val CONSENSUS_THRESHOLD = 0.65  // 65% agreement for consensus (more conservative)
        
        // Risk thresholds specific to hedge fund
        const val MAX_POSITION_RISK = 0.02      // Max 2% risk per position
        const val CASCADE_RISK_THRESHOLD = 0.7  // Guardian triggers at 70%
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
     * Check if crossover members are active.
     */
    fun hasCrossovers(): Boolean {
        return boardMembers.any { it.memberId.category == MemberCategory.CROSSOVER }
    }
    
    /**
     * Convene the board and reach consensus.
     * This is the main entry point for getting hedge fund trading signals.
     * 
     * Note: Currently returns consensus but is NOT wired to any execution engine.
     */
    fun conveneBoardroom(context: MarketContext): HedgeFundBoardConsensus {
        // BUILD #269: Log board convening so we can see it firing in logcat
        SystemLogger.d("⚡ HEDGE FUND: Board convening — ${boardMembers.size} members | " +
            "symbol=${context.symbol} price=\$${String.format("%.4f", context.currentPrice)}")

        // Gather all opinions
        val opinions = boardMembers.map { configured ->
            configured.member.analyze(context) to configured.effectiveWeight
        }

        // BUILD #269: Log each member's vote
        opinions.forEach { (opinion, weight) ->
            SystemLogger.d("⚡ HEDGE FUND [${opinion.displayName}/${opinion.role}]: " +
                "${opinion.vote} | conf=${String.format("%.0f", opinion.confidence * 100)}% | " +
                "sentiment=${String.format("%.2f", opinion.sentiment)} | ${opinion.reasoning}")
        }
        
        // Calculate weighted score
        var weightedSentiment = 0.0
        var totalWeight = 0.0
        
        for ((opinion, weight) in opinions) {
            weightedSentiment += opinion.sentiment * opinion.confidence * weight
            totalWeight += weight * opinion.confidence
        }
        
        val finalScore = if (totalWeight > 0) weightedSentiment / totalWeight else 0.0
        
        // Check for cascade risk (Guardian's special role)
        val cascadeRisk = checkCascadeRisk(opinions.map { it.first })
        
        // Check for consensus
        val opinionList = opinions.map { it.first }
        val hasConsensus = checkConsensus(opinionList)
        
        // Determine final vote (Guardian can override if cascade risk is high)
        val finalDecision = when {
            cascadeRisk > CASCADE_RISK_THRESHOLD -> BoardVote.STRONG_SELL // Guardian override
            hasConsensus -> scoreToVote(finalScore)
            else -> {
                // No consensus - use casting vote
                castingVoteMember?.let { caster ->
                    val casterOpinion = opinionList.find { it.displayName == caster.member.displayName }
                    if (casterOpinion != null && casterOpinion.confidence >= 0.25) {
                        casterOpinion.vote
                    } else {
                        scoreToVote(finalScore)
                    }
                } ?: scoreToVote(finalScore)
            }
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
        
        // Get regime analysis from Atlas if present
        val regimeAnalysis = getRegimeAnalysis(opinionList)
        
        // Synthesize decision explanation
        val synthesis = synthesizeDecision(finalDecision, opinionList, finalScore, hasConsensus, cascadeRisk)
        
        val consensus = HedgeFundBoardConsensus(
            finalDecision = finalDecision,
            weightedScore = finalScore,
            confidence = confidence,
            unanimousCount = unanimousCount,
            dissenterReasons = dissenterReasons,
            opinions = opinionList,
            synthesis = synthesis,
            cascadeRiskLevel = cascadeRisk,
            guardianOverride = cascadeRisk > CASCADE_RISK_THRESHOLD,
            regimeAnalysis = regimeAnalysis,
            recommendedPositionSize = calculateRecommendedPositionSize(confidence, cascadeRisk)
        )

        // BUILD #269: Log final consensus
        val guardianTag = if (consensus.guardianOverride) " 🛡️ GUARDIAN OVERRIDE" else ""
        SystemLogger.system("⚡ HEDGE FUND CONSENSUS: ${context.symbol} → $finalDecision$guardianTag | " +
            "conf=${String.format("%.0f", confidence * 100)}% | agree=$unanimousCount/${boardMembers.size} | " +
            "cascade=${String.format("%.0f", cascadeRisk * 100)}% | score=${String.format("%.3f", finalScore)}")

        return consensus
    }
    
    /**
     * Check cascade risk from Guardian's analysis.
     */
    private fun checkCascadeRisk(opinions: List<AgentOpinion>): Double {
        val guardianOpinion = opinions.find { it.displayName == "Guardian" }
        return guardianOpinion?.let {
            // Guardian's sentiment when negative indicates cascade risk
            if (it.sentiment < 0) kotlin.math.abs(it.sentiment) else 0.0
        } ?: 0.0
    }
    
    /**
     * Get regime analysis from Atlas if present.
     */
    private fun getRegimeAnalysis(opinions: List<AgentOpinion>): String? {
        val atlasOpinion = opinions.find { it.displayName == "Atlas" }
        return atlasOpinion?.reasoning
    }
    
    /**
     * Calculate recommended position size based on confidence and risk.
     */
    private fun calculateRecommendedPositionSize(confidence: Double, cascadeRisk: Double): Double {
        val baseSize = 1.0
        val confidenceMultiplier = confidence.coerceIn(0.3, 1.0)
        val riskMultiplier = (1.0 - cascadeRisk).coerceIn(0.2, 1.0)
        return baseSize * confidenceMultiplier * riskMultiplier * (1.0 - MAX_POSITION_RISK)
    }
    
    /**
     * Check if there's consensus among board members.
     */
    private fun checkConsensus(opinions: List<AgentOpinion>): Boolean {
        if (opinions.size == 1) return true
        
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
        hasConsensus: Boolean,
        cascadeRisk: Double
    ): String {
        val bullishCount = opinions.count { it.vote == BoardVote.BUY || it.vote == BoardVote.STRONG_BUY }
        val bearishCount = opinions.count { it.vote == BoardVote.SELL || it.vote == BoardVote.STRONG_SELL }
        val totalMembers = opinions.size
        
        val consensusNote = when {
            cascadeRisk > CASCADE_RISK_THRESHOLD -> " [GUARDIAN OVERRIDE - CASCADE RISK]"
            !hasConsensus -> " (casting vote applied)"
            else -> ""
        }
        
        val riskNote = if (cascadeRisk > 0.3) " Risk: ${String.format("%.0f", cascadeRisk * 100)}%" else ""
        
        return when (decision) {
            BoardVote.STRONG_BUY -> "HF: Strong BUY ($bullishCount/$totalMembers)$consensusNote. Score: ${String.format("%.3f", score)}$riskNote"
            BoardVote.BUY -> "HF: Moderate BUY ($bullishCount/$totalMembers)$consensusNote. Score: ${String.format("%.3f", score)}$riskNote"
            BoardVote.HOLD -> "HF: HOLD recommended ($totalMembers members). Score: ${String.format("%.3f", score)}$riskNote"
            BoardVote.SELL -> "HF: Moderate SELL ($bearishCount/$totalMembers)$consensusNote. Score: ${String.format("%.3f", score)}$riskNote"
            BoardVote.STRONG_SELL -> "HF: Strong SELL ($bearishCount/$totalMembers)$consensusNote. Score: ${String.format("%.3f", score)}$riskNote"
        }
    }
}

/**
 * Extended consensus for hedge fund board with additional risk metrics.
 */
data class HedgeFundBoardConsensus(
    val finalDecision: BoardVote,
    val weightedScore: Double,
    val confidence: Double,
    val unanimousCount: Int,
    val dissenterReasons: List<String>,
    val opinions: List<AgentOpinion>,
    val synthesis: String,
    // Hedge fund specific fields
    val cascadeRiskLevel: Double,          // 0.0 to 1.0 - Guardian's cascade risk assessment
    val guardianOverride: Boolean,          // True if Guardian forced SELL due to cascade risk
    val regimeAnalysis: String?,           // Atlas's regime assessment
    val recommendedPositionSize: Double,    // Suggested position size multiplier
    val sessionId: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Convert to standard BoardConsensus for compatibility.
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
     * Generate XAI explanation for hedge fund decision.
     */
    fun toXAIExplanation(): String {
        val sb = StringBuilder()
        sb.appendLine("═══════════════════════════════════════════════════════════════════")
        sb.appendLine("              HEDGE FUND BOARD DECISION - XAI AUDIT")
        sb.appendLine("═══════════════════════════════════════════════════════════════════")
        sb.appendLine("Session ID: $sessionId")
        sb.appendLine("Timestamp: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS z").format(java.util.Date(timestamp))}")
        sb.appendLine()
        sb.appendLine("┌─────────────────────────────────────────────────────────────────┐")
        sb.appendLine("│ FINAL DECISION: $finalDecision")
        sb.appendLine("│ Weighted Score: ${String.format("%.4f", weightedScore)}")
        sb.appendLine("│ Confidence: ${String.format("%.1f", confidence * 100)}%")
        sb.appendLine("│ Board Agreement: $unanimousCount/${opinions.size}")
        sb.appendLine("├─────────────────────────────────────────────────────────────────┤")
        sb.appendLine("│ CASCADE RISK: ${String.format("%.1f", cascadeRiskLevel * 100)}%")
        if (guardianOverride) {
            sb.appendLine("│ ⚠️ GUARDIAN OVERRIDE ACTIVE")
        }
        sb.appendLine("│ Recommended Position Size: ${String.format("%.0f", recommendedPositionSize * 100)}%")
        sb.appendLine("└─────────────────────────────────────────────────────────────────┘")
        sb.appendLine()
        regimeAnalysis?.let {
            sb.appendLine("REGIME ANALYSIS (Atlas): $it")
            sb.appendLine()
        }
        sb.appendLine("SYNTHESIS: $synthesis")
        sb.appendLine()
        sb.appendLine("───────────────────────────────────────────────────────────────────")
        sb.appendLine("                        INDIVIDUAL VOTES")
        sb.appendLine("───────────────────────────────────────────────────────────────────")
        
        for (opinion in opinions) {
            val voteEmoji = when (opinion.vote) {
                BoardVote.STRONG_BUY -> "🟢🟢"
                BoardVote.BUY -> "🟢"
                BoardVote.HOLD -> "⚪"
                BoardVote.SELL -> "🔴"
                BoardVote.STRONG_SELL -> "🔴🔴"
            }
            sb.appendLine()
            sb.appendLine("$voteEmoji ${opinion.displayName} (${opinion.role})")
            sb.appendLine("   Vote: ${opinion.vote} | Confidence: ${String.format("%.1f", opinion.confidence * 100)}%")
            sb.appendLine("   Reasoning: ${opinion.reasoning}")
        }
        
        sb.appendLine()
        sb.appendLine("═══════════════════════════════════════════════════════════════════")
        return sb.toString()
    }
}

// ============================================================================
// HEDGE FUND BOARD FACTORY FUNCTIONS
// ============================================================================

/**
 * Create a full hedge fund board (5 specialists + 2 crossovers).
 */
fun createHedgeFundBoard(): HedgeFundBoardOrchestrator {
    return HedgeFundBoardOrchestrator(BoardPresets.HEDGE_FUND_FULL)
}

/**
 * Create a hedge fund board without crossovers.
 */
fun createHedgeFundCoreBoard(): HedgeFundBoardOrchestrator {
    return HedgeFundBoardOrchestrator(BoardPresets.HEDGE_FUND_CORE, includeCrossovers = false)
}

/**
 * Create a custom hedge fund board.
 */
fun createCustomHedgeFundBoard(
    name: String,
    members: List<BoardMemberId>,
    castingVote: BoardMemberId? = BoardMemberId.GUARDIAN
): HedgeFundBoardOrchestrator {
    val config = buildBoardConfiguration(name) {
        description("Custom HF board: ${members.joinToString { it.displayName }}")
        addMembers(members)
        castingVote?.let { setCastingVote(it) }
    }
    return HedgeFundBoardOrchestrator(config)
}
