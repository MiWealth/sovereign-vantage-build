package com.miwealth.sovereignvantage.core.ai

/**
 * Board Member Registry - Centralized member definitions and configuration
 * 
 * V5.17.0 "Arthur Edition" - Board Separation Refactor
 * 
 * This file contains:
 * 1. All 15 board member identifiers
 * 2. Member categorization (Core vs Hedge Fund vs Crossover)
 * 3. Board configuration presets
 * 4. Factory methods for creating board compositions
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 */

// ============================================================================
// MEMBER IDENTIFIERS
// ============================================================================

/**
 * All available board members across all boards.
 * Used for configuration and member selection.
 */
enum class BoardMemberId(
    val displayName: String,
    val role: String,
    val category: MemberCategory,
    val defaultWeight: Double
) {
    // Core Octagon Members (General Trading Engine)
    ARTHUR("Arthur", "CTO/Chairman - Trend Analysis", MemberCategory.CORE, 0.125),
    HELENA("Helena", "CRO - Risk & Mean Reversion", MemberCategory.CORE, 0.125),
    SENTINEL("Sentinel", "CCO - Volatility & Compliance", MemberCategory.CORE, 0.125),
    ORACLE("Oracle", "CDO - Sentiment Analysis", MemberCategory.CORE, 0.125),
    NEXUS("Nexus", "COO - On-Chain Analysis", MemberCategory.CORE, 0.125),
    MARCUS("Marcus", "CIO - Macro Strategy", MemberCategory.CORE, 0.125),
    CIPHER("Cipher", "CSO - Pattern Recognition", MemberCategory.CORE, 0.125),
    AEGIS("Aegis", "Chief Defense - Liquidity Hunter", MemberCategory.CORE, 0.125),
    
    // Hedge Fund Specialist Members
    SOROS("Soros", "Chief Economist - Global Macro", MemberCategory.HEDGE_FUND, 0.143),
    GUARDIAN("Guardian", "Chief Risk Guardian - Cascade Detection", MemberCategory.HEDGE_FUND, 0.143),
    DRAPER("Draper", "Chief DeFi Officer - Protocol Analysis", MemberCategory.HEDGE_FUND, 0.143),
    ATLAS("Atlas", "Chief Strategist - Regime Detection", MemberCategory.HEDGE_FUND, 0.143),
    THETA("Theta", "Chief Arbitrage - Funding Rate Analysis", MemberCategory.HEDGE_FUND, 0.143),
    
    // Crossover Members (Can serve on both boards)
    MOBY("Moby", "Chief Intelligence - Whale Tracking", MemberCategory.CROSSOVER, 0.143),
    ECHO("Echo", "Chief Order Flow - Order Book Analysis", MemberCategory.CROSSOVER, 0.143);
    
    companion object {
        /** Get all core Octagon members */
        fun getCoreMemberIds(): List<BoardMemberId> = values().filter { it.category == MemberCategory.CORE }
        
        /** Get all hedge fund specialist members */
        fun getHedgeFundMemberIds(): List<BoardMemberId> = values().filter { it.category == MemberCategory.HEDGE_FUND }
        
        /** Get all crossover members */
        fun getCrossoverMemberIds(): List<BoardMemberId> = values().filter { it.category == MemberCategory.CROSSOVER }
        
        /** Get hedge fund members + crossovers */
        fun getFullHedgeFundMemberIds(): List<BoardMemberId> = 
            getHedgeFundMemberIds() + getCrossoverMemberIds()
        
        /** Get all 15 members */
        fun getAllMemberIds(): List<BoardMemberId> = values().toList()
    }
}

/**
 * Member category for board assignment
 */
enum class MemberCategory {
    CORE,       // Original Octagon - General Trading Engine
    HEDGE_FUND, // Hedge Fund specialists only
    CROSSOVER   // Can serve on both boards
}

// ============================================================================
// BOARD CONFIGURATION
// ============================================================================

/**
 * Configuration for a board composition.
 * Supports 1-20 members with custom weights.
 */
data class BoardConfiguration(
    val name: String,
    val description: String,
    val members: List<BoardMemberId>,
    val customWeights: Map<BoardMemberId, Double>? = null, // null = equal weights
    val castingVoteMember: BoardMemberId? = null // Member with casting vote when no consensus
) {
    init {
        require(members.size in 1..20) { "Board must have 1-20 members, got ${members.size}" }
        require(members.toSet().size == members.size) { "Duplicate members not allowed" }
        
        customWeights?.let { weights ->
            val totalWeight = weights.values.sum()
            require(totalWeight in 0.99..1.01) { "Custom weights must sum to 1.0, got $totalWeight" }
            require(weights.keys.all { it in members }) { "Custom weights must only include configured members" }
        }
        
        castingVoteMember?.let {
            require(it in members) { "Casting vote member must be in board" }
        }
    }
    
    /**
     * Get the effective weight for a member.
     * Uses custom weight if defined, otherwise equal distribution.
     */
    fun getWeight(memberId: BoardMemberId): Double {
        return customWeights?.get(memberId) ?: (1.0 / members.size)
    }
    
    /**
     * Get all members with their effective weights.
     */
    fun getMembersWithWeights(): List<Pair<BoardMemberId, Double>> {
        return members.map { it to getWeight(it) }
    }
}

// ============================================================================
// PRESET CONFIGURATIONS
// ============================================================================

/**
 * Standard board configuration presets.
 */
object BoardPresets {
    
    // =========================================================================
    // GENERAL TRADING ENGINE PRESETS
    // =========================================================================
    
    /** Single member test - Arthur only */
    val SINGLE_ARTHUR = BoardConfiguration(
        name = "Single - Arthur",
        description = "Single member test with Arthur (TrendFollower)",
        members = listOf(BoardMemberId.ARTHUR),
        castingVoteMember = BoardMemberId.ARTHUR
    )
    
    /** Single member test - Sentinel only */
    val SINGLE_SENTINEL = BoardConfiguration(
        name = "Single - Sentinel",
        description = "Single member test with Sentinel (VolatilityTrader) - 100% Q1 2025 accuracy",
        members = listOf(BoardMemberId.SENTINEL),
        castingVoteMember = BoardMemberId.SENTINEL
    )
    
    /** Duo test - Arthur + Sentinel */
    val DUO_TREND_VOLATILITY = BoardConfiguration(
        name = "Duo - Trend & Volatility",
        description = "Two member test: Arthur (Trend) + Sentinel (Volatility)",
        members = listOf(BoardMemberId.ARTHUR, BoardMemberId.SENTINEL),
        castingVoteMember = BoardMemberId.SENTINEL
    )
    
    /** Trio test - Arthur + Helena + Sentinel */
    val TRIO_CORE = BoardConfiguration(
        name = "Trio - Core Three",
        description = "Three core members: Arthur (Trend) + Helena (Mean Reversion) + Sentinel (Volatility)",
        members = listOf(BoardMemberId.ARTHUR, BoardMemberId.HELENA, BoardMemberId.SENTINEL),
        castingVoteMember = BoardMemberId.SENTINEL
    )
    
    /** Original 8-member Octagon - RECOMMENDED FOR GENERAL TRADING */
    val OCTAGON = BoardConfiguration(
        name = "Octagon",
        description = "Original 8-member board - Proven architecture for general trading",
        members = BoardMemberId.getCoreMemberIds(),
        castingVoteMember = BoardMemberId.SENTINEL // Based on Q1 2025 backtest accuracy
    )
    
    /** 8-member Octagon with Arthur casting vote (legacy) */
    val OCTAGON_ARTHUR_CASTING = BoardConfiguration(
        name = "Octagon - Arthur Casting",
        description = "8-member board with Arthur (Chairman) as casting vote",
        members = BoardMemberId.getCoreMemberIds(),
        castingVoteMember = BoardMemberId.ARTHUR
    )
    
    /** Octagon + Moby (Whale Tracking crossover) */
    val OCTAGON_PLUS_MOBY = BoardConfiguration(
        name = "Octagon + Moby",
        description = "8 core members + Moby (Whale Tracking) crossover",
        members = BoardMemberId.getCoreMemberIds() + BoardMemberId.MOBY,
        castingVoteMember = BoardMemberId.SENTINEL
    )
    
    /** Octagon + Echo (Order Flow crossover) */
    val OCTAGON_PLUS_ECHO = BoardConfiguration(
        name = "Octagon + Echo",
        description = "8 core members + Echo (Order Flow) crossover",
        members = BoardMemberId.getCoreMemberIds() + BoardMemberId.ECHO,
        castingVoteMember = BoardMemberId.SENTINEL
    )
    
    /** Octagon + Both Crossovers */
    val OCTAGON_PLUS_CROSSOVERS = BoardConfiguration(
        name = "Octagon + Crossovers",
        description = "8 core members + Moby + Echo crossovers",
        members = BoardMemberId.getCoreMemberIds() + BoardMemberId.MOBY + BoardMemberId.ECHO,
        castingVoteMember = BoardMemberId.SENTINEL
    )
    
    // =========================================================================
    // HEDGE FUND ENGINE PRESETS
    // =========================================================================
    
    /** Hedge Fund core - 5 specialists without crossovers */
    val HEDGE_FUND_CORE = BoardConfiguration(
        name = "Hedge Fund - Core",
        description = "5 hedge fund specialists: Soros, Guardian, Draper, Atlas, Theta",
        members = BoardMemberId.getHedgeFundMemberIds(),
        castingVoteMember = BoardMemberId.GUARDIAN
    )
    
    /** Hedge Fund full - 7 members with crossovers */
    val HEDGE_FUND_FULL = BoardConfiguration(
        name = "Hedge Fund - Full",
        description = "5 specialists + 2 crossovers: All hedge fund members",
        members = BoardMemberId.getFullHedgeFundMemberIds(),
        castingVoteMember = BoardMemberId.GUARDIAN
    )
    
    /** Hedge Fund + Arthur (for trend guidance) */
    val HEDGE_FUND_WITH_ARTHUR = BoardConfiguration(
        name = "Hedge Fund + Arthur",
        description = "Full hedge fund board + Arthur for trend analysis",
        members = BoardMemberId.getFullHedgeFundMemberIds() + BoardMemberId.ARTHUR,
        castingVoteMember = BoardMemberId.GUARDIAN
    )
    
    // =========================================================================
    // FULL BOARD PRESETS
    // =========================================================================
    
    /** Full 15-member Pentadecagon (for comparison testing) */
    val PENTADECAGON = BoardConfiguration(
        name = "Pentadecagon",
        description = "Full 15-member board - All members active",
        members = BoardMemberId.getAllMemberIds(),
        customWeights = mapOf(
            // Core members at 7%
            BoardMemberId.ARTHUR to 0.07,
            BoardMemberId.HELENA to 0.07,
            BoardMemberId.SENTINEL to 0.07,
            BoardMemberId.ORACLE to 0.07,
            BoardMemberId.NEXUS to 0.07,
            BoardMemberId.MARCUS to 0.07,
            BoardMemberId.CIPHER to 0.07,
            BoardMemberId.AEGIS to 0.07,
            BoardMemberId.SOROS to 0.07,
            BoardMemberId.GUARDIAN to 0.07,
            // Specialists at 6%
            BoardMemberId.DRAPER to 0.06,
            BoardMemberId.MOBY to 0.06,
            BoardMemberId.ATLAS to 0.06,
            BoardMemberId.ECHO to 0.06,
            BoardMemberId.THETA to 0.06
        ),
        castingVoteMember = BoardMemberId.SENTINEL
    )
    
    // =========================================================================
    // UTILITY METHODS
    // =========================================================================
    
    /** Get all general trading presets */
    fun getGeneralTradingPresets(): List<BoardConfiguration> = listOf(
        SINGLE_ARTHUR,
        SINGLE_SENTINEL,
        DUO_TREND_VOLATILITY,
        TRIO_CORE,
        OCTAGON,
        OCTAGON_ARTHUR_CASTING,
        OCTAGON_PLUS_MOBY,
        OCTAGON_PLUS_ECHO,
        OCTAGON_PLUS_CROSSOVERS
    )
    
    /** Get all hedge fund presets */
    fun getHedgeFundPresets(): List<BoardConfiguration> = listOf(
        HEDGE_FUND_CORE,
        HEDGE_FUND_FULL,
        HEDGE_FUND_WITH_ARTHUR
    )
    
    /** Get all presets */
    fun getAllPresets(): List<BoardConfiguration> = 
        getGeneralTradingPresets() + getHedgeFundPresets() + PENTADECAGON
}

// ============================================================================
// CUSTOM CONFIGURATION BUILDER
// ============================================================================

/**
 * Builder for creating custom board configurations.
 * Supports 1-20 members with flexible weight assignment.
 */
class BoardConfigurationBuilder(private val name: String) {
    private val members = mutableListOf<BoardMemberId>()
    private val customWeights = mutableMapOf<BoardMemberId, Double>()
    private var castingVoteMember: BoardMemberId? = null
    private var description: String = ""
    
    fun description(desc: String) = apply { this.description = desc }
    
    fun addMember(memberId: BoardMemberId, weight: Double? = null) = apply {
        require(members.size < 20) { "Cannot add more than 20 members" }
        require(memberId !in members) { "Member $memberId already added" }
        members.add(memberId)
        weight?.let { customWeights[memberId] = it }
    }
    
    fun addMembers(memberIds: List<BoardMemberId>) = apply {
        memberIds.forEach { addMember(it) }
    }
    
    fun setCastingVote(memberId: BoardMemberId) = apply {
        this.castingVoteMember = memberId
    }
    
    fun setWeight(memberId: BoardMemberId, weight: Double) = apply {
        require(memberId in members) { "Member $memberId not in board" }
        customWeights[memberId] = weight
    }
    
    fun setEqualWeights() = apply {
        customWeights.clear()
    }
    
    fun build(): BoardConfiguration {
        require(members.isNotEmpty()) { "Board must have at least 1 member" }
        
        // Normalize weights if custom weights are partially set
        val finalWeights: Map<BoardMemberId, Double>? = if (customWeights.isNotEmpty()) {
            val totalCustomWeight = customWeights.values.sum()
            val membersWithoutWeight = members.filter { it !in customWeights }
            
            if (membersWithoutWeight.isEmpty()) {
                // All members have custom weights - normalize to 1.0
                val normalized = customWeights.mapValues { it.value / totalCustomWeight }
                normalized
            } else {
                // Some members need weight assignment
                val remainingWeight = 1.0 - totalCustomWeight
                val weightPerRemaining = remainingWeight / membersWithoutWeight.size
                val fullWeights = customWeights.toMutableMap()
                membersWithoutWeight.forEach { fullWeights[it] = weightPerRemaining }
                fullWeights
            }
        } else {
            null // Equal weights
        }
        
        return BoardConfiguration(
            name = name,
            description = description.ifEmpty { "Custom board: ${members.joinToString { it.displayName }}" },
            members = members.toList(),
            customWeights = finalWeights,
            castingVoteMember = castingVoteMember ?: members.first()
        )
    }
}

/**
 * DSL function for building custom board configurations.
 * 
 * Usage:
 * ```
 * val myBoard = buildBoardConfiguration("My Test Board") {
 *     description("Custom 4-member test")
 *     addMember(BoardMemberId.ARTHUR)
 *     addMember(BoardMemberId.SENTINEL)
 *     addMember(BoardMemberId.HELENA)
 *     addMember(BoardMemberId.ORACLE)
 *     setCastingVote(BoardMemberId.SENTINEL)
 * }
 * ```
 */
fun buildBoardConfiguration(name: String, block: BoardConfigurationBuilder.() -> Unit): BoardConfiguration {
    return BoardConfigurationBuilder(name).apply(block).build()
}
