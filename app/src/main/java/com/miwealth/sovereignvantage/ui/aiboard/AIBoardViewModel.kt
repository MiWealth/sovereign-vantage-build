package com.miwealth.sovereignvantage.ui.aiboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miwealth.sovereignvantage.core.ai.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * AI BOARD VIEWMODEL - V5.18.0 "Arthur Edition"
 * 
 * Manages UI state for the AI Board screen, streaming live decisions
 * and member opinions from the AIBoardOrchestrator.
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 */

data class AIBoardUIState(
    val currentConsensus: BoardConsensus? = null,
    val boardMembers: List<BoardMemberUIState> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

data class BoardMemberUIState(
    val memberId: String,
    val displayName: String,
    val role: String,
    val currentVote: BoardVote? = null,
    val confidence: Double = 0.0,
    val reasoning: String = "",
    val weight: Double = 0.125  // Default Octagon weight
)

data class DecisionUIState(
    val id: String,
    val timestamp: String,
    val symbol: String,
    val vote: BoardVote,
    val confidence: Double,
    val weightedScore: Double,
    val unanimousCount: Int,
    val totalMembers: Int,
    val synthesis: String,
    val actionTaken: String
)

class AIBoardViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AIBoardUIState())
    val uiState: StateFlow<AIBoardUIState> = _uiState.asStateFlow()

    private val _selectedMember = MutableStateFlow<String?>(null)
    val selectedMember: StateFlow<String?> = _selectedMember.asStateFlow()

    private val _recentDecisions = MutableStateFlow<List<DecisionUIState>>(emptyList())
    val recentDecisions: StateFlow<List<DecisionUIState>> = _recentDecisions.asStateFlow()

    // Octagon board members (from AIBoardOrchestrator.kt)
    private val octagonMembers = listOf(
        BoardMemberUIState(
            memberId = "arthur",
            displayName = "Arthur",
            role = "CTO / Chairman",
            weight = 0.125
        ),
        BoardMemberUIState(
            memberId = "helena",
            displayName = "Helena",
            role = "CRO",
            weight = 0.125
        ),
        BoardMemberUIState(
            memberId = "sentinel",
            displayName = "Sentinel",
            role = "CCO",
            weight = 0.125
        ),
        BoardMemberUIState(
            memberId = "oracle",
            displayName = "Oracle",
            role = "CDO",
            weight = 0.125
        ),
        BoardMemberUIState(
            memberId = "nexus",
            displayName = "Nexus",
            role = "COO",
            weight = 0.125
        ),
        BoardMemberUIState(
            memberId = "marcus",
            displayName = "Marcus",
            role = "CIO",
            weight = 0.125
        ),
        BoardMemberUIState(
            memberId = "cipher",
            displayName = "Cipher",
            role = "CSO",
            weight = 0.125
        ),
        BoardMemberUIState(
            memberId = "aegis",
            displayName = "Aegis",
            role = "Chief Defense",
            weight = 0.125
        )
    )

    init {
        initializeBoard()
        observeBoardDecisions()
    }

    private fun initializeBoard() {
        _uiState.update { currentState ->
            currentState.copy(
                boardMembers = octagonMembers,
                isLoading = false
            )
        }
    }

    private fun observeBoardDecisions() {
        viewModelScope.launch {
            // Observe board decision repository for new decisions
            // This will connect to BoardDecisionRepository when wired
            // For now, show demo data
            
            // Simulate board activity with demo consensus
            val demoConsensus = BoardConsensus(
                finalDecision = BoardVote.BUY,
                weightedScore = 0.65,
                confidence = 0.82,
                unanimousCount = 6,
                dissenterReasons = listOf("Helena: Overbought conditions", "Sentinel: High volatility risk"),
                opinions = octagonMembers.map { member ->
                    AgentOpinion(
                        agentName = member.memberId,
                        displayName = member.displayName,
                        role = member.role,
                        vote = if (member.memberId == "helena" || member.memberId == "sentinel") 
                            BoardVote.HOLD 
                        else 
                            BoardVote.BUY,
                        sentiment = if (member.memberId == "helena" || member.memberId == "sentinel") 
                            0.2 
                        else 
                            0.65,
                        confidence = 0.82,
                        reasoning = "${member.displayName} analysis based on ${member.role} expertise"
                    )
                },
                synthesis = "Strong bullish momentum detected. 6 of 8 members recommend BUY. Dissenters cite overbought RSI and elevated volatility."
            )

            _uiState.update { it.copy(currentConsensus = demoConsensus) }

            // Update member votes from consensus
            updateMemberVotes(demoConsensus)

            // Add demo recent decisions
            addDemoDecisions()
        }
    }

    private fun updateMemberVotes(consensus: BoardConsensus) {
        val updatedMembers = _uiState.value.boardMembers.map { member ->
            val opinion = consensus.opinions.find { it.displayName == member.displayName }
            member.copy(
                currentVote = opinion?.vote,
                confidence = opinion?.confidence ?: 0.0,
                reasoning = opinion?.reasoning ?: ""
            )
        }
        
        _uiState.update { it.copy(boardMembers = updatedMembers) }
    }

    private fun addDemoDecisions() {
        val dateFormat = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.US)
        val now = System.currentTimeMillis()

        val demoDecisions = listOf(
            DecisionUIState(
                id = UUID.randomUUID().toString(),
                timestamp = dateFormat.format(Date(now - 300000)),  // 5 min ago
                symbol = "BTC/USD",
                vote = BoardVote.BUY,
                confidence = 0.82,
                weightedScore = 0.65,
                unanimousCount = 6,
                totalMembers = 8,
                synthesis = "Strong bullish momentum detected. 6 of 8 members recommend BUY. Dissenters cite overbought RSI and elevated volatility.",
                actionTaken = "SIGNAL_GENERATED"
            ),
            DecisionUIState(
                id = UUID.randomUUID().toString(),
                timestamp = dateFormat.format(Date(now - 900000)),  // 15 min ago
                symbol = "ETH/USD",
                vote = BoardVote.HOLD,
                confidence = 0.71,
                weightedScore = 0.12,
                unanimousCount = 5,
                totalMembers = 8,
                synthesis = "Mixed signals. Board recommends HOLD pending breakout confirmation. Volatility remains elevated.",
                actionTaken = "HOLD"
            ),
            DecisionUIState(
                id = UUID.randomUUID().toString(),
                timestamp = dateFormat.format(Date(now - 1800000)),  // 30 min ago
                symbol = "SOL/USD",
                vote = BoardVote.STRONG_BUY,
                confidence = 0.91,
                weightedScore = 1.45,
                unanimousCount = 8,
                totalMembers = 8,
                synthesis = "Unanimous STRONG_BUY. All indicators aligned. Breakout confirmed with strong volume. Risk/reward ratio favorable.",
                actionTaken = "TRADE_EXECUTED"
            ),
            DecisionUIState(
                id = UUID.randomUUID().toString(),
                timestamp = dateFormat.format(Date(now - 3600000)),  // 1 hour ago
                symbol = "XRP/USD",
                vote = BoardVote.SELL,
                confidence = 0.76,
                weightedScore = -0.52,
                unanimousCount = 6,
                totalMembers = 8,
                synthesis = "Bearish divergence detected. 6 of 8 recommend SELL. Support level broken. Volume declining.",
                actionTaken = "SIGNAL_GENERATED"
            )
        )

        _recentDecisions.value = demoDecisions
    }

    fun selectMember(memberId: String?) {
        _selectedMember.value = memberId
    }

    fun refreshBoard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            // Trigger board re-analysis
            // This will connect to AIBoardOrchestrator when wired
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /**
     * Connect to live AIBoardOrchestrator when wired.
     * For now using demo data.
     */
    private fun connectToLiveBoard() {
        // TODO V5.19.0: Wire to AIBoardOrchestrator
        // val orchestrator = tradingSystemManager.getAIBoardOrchestrator()
        // viewModelScope.launch {
        //     orchestrator.consensusFlow.collect { consensus ->
        //         _uiState.update { it.copy(currentConsensus = consensus) }
        //         updateMemberVotes(consensus)
        //     }
        // }
    }
}
