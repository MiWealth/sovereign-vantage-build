package com.miwealth.sovereignvantage.ui.aiboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miwealth.sovereignvantage.core.TradingSystemManager
import com.miwealth.sovereignvantage.core.trading.CoordinatorEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * BUILD #117 FIX 4: AI Board ViewModel (Live Data!)
 * Now subscribes to real coordinatorEvents for live board decisions
 * 
 * For Arthur. For Cathryn. 💚
 */

@HiltViewModel
class AIBoardViewModel @Inject constructor(
    private val tradingSystemManager: TradingSystemManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(AIBoardUiState())
    val uiState: StateFlow<AIBoardUiState> = _uiState.asStateFlow()
    
    init {
        // BUILD #117 FIX 4: Subscribe to real coordinator events
        subscribeToCoordinatorEvents()
        // BUILD #146: Periodically update data collection progress
        startBufferSizeUpdates()
    }
    
    /**
     * BUILD #146: Periodically fetch price buffer sizes to show data collection progress.
     * Updates every 5 seconds to show "Collecting data: X/50 points" status.
     */
    private fun startBufferSizeUpdates() {
        viewModelScope.launch {
            while (true) {
                delay(5000)  // Update every 5 seconds
                val coordinator = tradingSystemManager.getAISystem()?.getTradingCoordinator()
                val bufferSizes = coordinator?.getPriceBufferSizes() ?: emptyMap()
                
                // Update UI state with current buffer sizes
                _uiState.value = _uiState.value.copy(
                    priceBufferSizes = bufferSizes
                )
                
                // If no analysis events yet but we have some data, update placeholder reasoning
                if (_uiState.value.boardMembers.all { it.reasoning.contains("Awaiting") } && bufferSizes.isNotEmpty()) {
                    val maxBufferSize = bufferSizes.values.maxOrNull() ?: 0
                    val progressReasoning = if (maxBufferSize < 50) {
                        "Collecting data: $maxBufferSize/50 points (${50 - maxBufferSize} more needed)..."
                    } else {
                        "Analyzing market conditions..."
                    }
                    
                    _uiState.value = _uiState.value.copy(
                        boardMembers = getPlaceholderMembersWithReasoning(progressReasoning)
                    )
                }
            }
        }
    }
    
    /**
     * BUILD #117 FIX 4: Subscribe to coordinator events for live AI Board updates
     */
    private fun subscribeToCoordinatorEvents() {
        viewModelScope.launch {
            tradingSystemManager.coordinatorEvents.collect { event ->
                when (event) {
                    is CoordinatorEvent.AnalysisComplete -> {
                        updateBoardDecision(event)
                    }
                    is CoordinatorEvent.TradingStarted -> {
                        _uiState.value = _uiState.value.copy(
                            isActive = true,
                            systemStatus = "AI Board active and analyzing markets"
                        )
                    }
                    is CoordinatorEvent.TradingStopped -> {
                        _uiState.value = _uiState.value.copy(
                            isActive = false,
                            systemStatus = "AI Board paused - trading stopped"
                        )
                    }
                    else -> {
                        // Ignore other events
                    }
                }
            }
        }
    }
    
    /**
     * BUILD #117 FIX 4: Update UI with real board decision from coordinator
     */
    private fun updateBoardDecision(event: CoordinatorEvent.AnalysisComplete) {
        val consensus = event.consensus
        
        // Map BoardConsensus to UI-friendly board members
        val boardMembers = mapConsensusToMembers(consensus)
        
        // Count votes matching final decision
        val votesForDecision = consensus.opinions.count { it.vote == consensus.finalDecision }
        
        _uiState.value = _uiState.value.copy(
            currentSymbol = event.symbol,
            currentDecision = consensus.finalDecision.name,  // STRONG_BUY, BUY, HOLD, SELL, STRONG_SELL
            consensusConfidence = consensus.confidence * 100.0,
            unanimousVotes = votesForDecision,
            totalVotes = consensus.opinions.size,
            boardMembers = boardMembers,
            lastUpdateTime = System.currentTimeMillis(),
            systemStatus = "Latest analysis: ${event.symbol}",
            isActive = true
        )
    }
    
    /**
     * Map BoardConsensus opinions to individual board member states
     * Uses actual AgentOpinion data from the consensus
     */
    private fun mapConsensusToMembers(consensus: com.miwealth.sovereignvantage.core.ai.BoardConsensus): List<BoardMemberState> {
        return consensus.opinions.map { opinion ->
            BoardMemberState(
                name = opinion.displayName,
                role = opinion.role,
                emoji = getAvatarForMember(opinion.displayName),
                vote = mapBoardVoteToUIVote(opinion.vote),
                confidence = opinion.confidence * 100.0,
                reasoning = opinion.reasoning
            )
        }
    }
    
    /**
     * Get avatar emoji for board member by name
     */
    private fun getAvatarForMember(displayName: String): String {
        return when (displayName) {
            "Arthur" -> "🎩"
            "Marcus" -> "💼"
            "Helena" -> "🛡️"
            "Sentinel" -> "⚖️"
            "Oracle" -> "🔮"
            "Nexus" -> "⚙️"
            "Cipher" -> "🔐"
            "Aegis" -> "🛡️"
            else -> "👤"
        }
    }
    
    /**
     * Map core.ai.BoardVote to ui.aiboard.Vote
     */
    private fun mapBoardVoteToUIVote(vote: com.miwealth.sovereignvantage.core.ai.BoardVote): Vote {
        return when (vote) {
            com.miwealth.sovereignvantage.core.ai.BoardVote.STRONG_BUY -> Vote.STRONG_BUY
            com.miwealth.sovereignvantage.core.ai.BoardVote.BUY -> Vote.BUY
            com.miwealth.sovereignvantage.core.ai.BoardVote.HOLD -> Vote.HOLD
            com.miwealth.sovereignvantage.core.ai.BoardVote.SELL -> Vote.SELL
            com.miwealth.sovereignvantage.core.ai.BoardVote.STRONG_SELL -> Vote.STRONG_SELL
        }
    }
}

/**
 * UI State for AI Board Screen
 */
data class AIBoardUiState(
    val currentSymbol: String = "Awaiting analysis...",
    val currentDecision: String = "HOLD",
    val consensusConfidence: Double = 0.0,
    val unanimousVotes: Int = 0,
    val totalVotes: Int = 8,
    val boardMembers: List<BoardMemberState> = getPlaceholderMembers(),
    val lastUpdateTime: Long = 0L,
    val systemStatus: String = "Waiting for trading system...",
    val isActive: Boolean = false,
    val priceBufferSizes: Map<String, Int> = emptyMap()  // BUILD #146: Track data collection progress
)

/**
 * Individual board member state
 */
data class BoardMemberState(
    val name: String,
    val role: String,
    val emoji: String,
    val vote: Vote,
    val confidence: Double,
    val reasoning: String
)

/**
 * Vote enum (matches AIBoardScreen.kt)
 */
enum class Vote {
    STRONG_BUY,
    BUY,
    HOLD,
    SELL,
    STRONG_SELL,
    ABSTAIN
}

/**
 * Placeholder board members for initial state
 */
private fun getPlaceholderMembers(): List<BoardMemberState> = listOf(
    BoardMemberState("Arthur", "CTO (Chairman)", "👔", Vote.HOLD, 0.0, "Awaiting market data..."),
    BoardMemberState("Marcus", "CIO", "💼", Vote.HOLD, 0.0, "Analyzing portfolio allocation..."),
    BoardMemberState("Helena", "CRO", "🛡️", Vote.HOLD, 0.0, "Monitoring risk levels..."),
    BoardMemberState("Sentinel", "CCO (Casting Vote)", "⚖️", Vote.HOLD, 0.0, "Compliance check in progress..."),
    BoardMemberState("Oracle", "CDO", "🔮", Vote.HOLD, 0.0, "Processing market intelligence..."),
    BoardMemberState("Nexus", "COO", "⚡", Vote.HOLD, 0.0, "Trade execution standby..."),
    BoardMemberState("Cipher", "CSO", "🔐", Vote.HOLD, 0.0, "Security systems nominal..."),
    BoardMemberState("Aegis", "Chief Defense", "🛡️", Vote.HOLD, 0.0, "Network protection active...")
)

/**
 * BUILD #146: Get placeholder members with custom reasoning for data collection progress.
 */
private fun getPlaceholderMembersWithReasoning(reasoning: String): List<BoardMemberState> = listOf(
    BoardMemberState("Arthur", "CTO (Chairman)", "👔", Vote.HOLD, 0.0, reasoning),
    BoardMemberState("Marcus", "CIO", "💼", Vote.HOLD, 0.0, reasoning),
    BoardMemberState("Helena", "CRO", "🛡️", Vote.HOLD, 0.0, reasoning),
    BoardMemberState("Sentinel", "CCO (Casting Vote)", "⚖️", Vote.HOLD, 0.0, reasoning),
    BoardMemberState("Oracle", "CDO", "🔮", Vote.HOLD, 0.0, reasoning),
    BoardMemberState("Nexus", "COO", "⚡", Vote.HOLD, 0.0, reasoning),
    BoardMemberState("Cipher", "CSO", "🔐", Vote.HOLD, 0.0, reasoning),
    BoardMemberState("Aegis", "Chief Defense", "🛡️", Vote.HOLD, 0.0, reasoning)
)
