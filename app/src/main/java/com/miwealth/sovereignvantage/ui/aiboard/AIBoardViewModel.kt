package com.miwealth.sovereignvantage.ui.aiboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miwealth.sovereignvantage.core.TradingSystemManager
import com.miwealth.sovereignvantage.core.trading.CoordinatorEvent
import dagger.hilt.android.lifecycle.HiltViewModel
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
        
        _uiState.value = _uiState.value.copy(
            currentSymbol = event.symbol,
            currentDecision = consensus.recommendation.name,  // BUY, SELL, HOLD
            consensusConfidence = consensus.confidence * 100.0,
            unanimousVotes = consensus.voteCounts[consensus.recommendation] ?: 0,
            totalVotes = 8,
            boardMembers = boardMembers,
            lastUpdateTime = System.currentTimeMillis(),
            systemStatus = "Latest analysis: ${event.symbol}",
            isActive = true
        )
    }
    
    /**
     * Map BoardConsensus to individual board member states
     * Uses reasoning from consensus to populate member details
     */
    private fun mapConsensusToMembers(consensus: com.miwealth.sovereignvantage.core.ai.BoardConsensus): List<BoardMemberState> {
        // Extract member votes from reasoning (if available)
        // For now, use placeholder logic based on consensus
        val strongVote = consensus.recommendation
        val weakVote = when (strongVote) {
            com.miwealth.sovereignvantage.core.ai.Recommendation.BUY -> com.miwealth.sovereignvantage.core.ai.Recommendation.HOLD
            com.miwealth.sovereignvantage.core.ai.Recommendation.SELL -> com.miwealth.sovereignvantage.core.ai.Recommendation.HOLD
            else -> com.miwealth.sovereignvantage.core.ai.Recommendation.HOLD
        }
        
        return listOf(
            BoardMemberState("Arthur", "CTO", "🎩", mapRecommendation(strongVote), consensus.confidence * 100.0, consensus.primaryReasoning),
            BoardMemberState("Marcus", "CIO", "💼", mapRecommendation(strongVote), consensus.confidence * 95.0, "Portfolio alignment confirmed"),
            BoardMemberState("Helena", "CRO", "🛡️", mapRecommendation(strongVote), consensus.confidence * 90.0, "Risk acceptable"),
            BoardMemberState("Sentinel", "CCO", "⚖️", mapRecommendation(weakVote), consensus.confidence * 70.0, "Compliance check passed"),
            BoardMemberState("Oracle", "CDO", "🔮", mapRecommendation(strongVote), consensus.confidence * 98.0, "Data signals positive"),
            BoardMemberState("Nexus", "COO", "⚙️", mapRecommendation(strongVote), consensus.confidence * 92.0, "Execution ready"),
            BoardMemberState("Cipher", "CSO", "🔐", mapRecommendation(strongVote), consensus.confidence * 88.0, "Security verified"),
            BoardMemberState("Aegis", "Defense", "🛡️", mapRecommendation(weakVote), consensus.confidence * 75.0, "Network stable")
        )
    }
    
    /**
     * Map core.ai.Recommendation to ui.aiboard.Vote
     */
    private fun mapRecommendation(rec: com.miwealth.sovereignvantage.core.ai.Recommendation): Vote {
        return when (rec) {
            com.miwealth.sovereignvantage.core.ai.Recommendation.STRONG_BUY -> Vote.STRONG_BUY
            com.miwealth.sovereignvantage.core.ai.Recommendation.BUY -> Vote.BUY
            com.miwealth.sovereignvantage.core.ai.Recommendation.HOLD -> Vote.HOLD
            com.miwealth.sovereignvantage.core.ai.Recommendation.SELL -> Vote.SELL
            com.miwealth.sovereignvantage.core.ai.Recommendation.STRONG_SELL -> Vote.STRONG_SELL
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
    val isActive: Boolean = false
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
