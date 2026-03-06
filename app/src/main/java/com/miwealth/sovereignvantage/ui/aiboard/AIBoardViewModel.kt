package com.miwealth.sovereignvantage.ui.aiboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miwealth.sovereignvantage.core.trading.TradingSystemIntegration
import com.miwealth.sovereignvantage.core.trading.coordinator.CoordinatorEvent
import com.miwealth.sovereignvantage.core.ai.BoardConsensus
import com.miwealth.sovereignvantage.core.ai.TradeDecision
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * BUILD #115: AI Board ViewModel
 * Subscribes to TradingCoordinator events and displays real board decisions
 * 
 * For Arthur. For Cathryn. 💚
 */

@HiltViewModel
class AIBoardViewModel @Inject constructor(
    private val tradingSystem: TradingSystemIntegration
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(AIBoardUiState())
    val uiState: StateFlow<AIBoardUiState> = _uiState.asStateFlow()
    
    init {
        subscribeToCoordinatorEvents()
    }
    
    private fun subscribeToCoordinatorEvents() {
        viewModelScope.launch {
            tradingSystem.getTradingCoordinator()?.coordinatorEvents?.collect { event ->
                when (event) {
                    is CoordinatorEvent.AnalysisComplete -> {
                        updateBoardDecision(event.symbol, event.consensus)
                    }
                    is CoordinatorEvent.Started -> {
                        _uiState.value = _uiState.value.copy(
                            systemStatus = "Trading system active",
                            isActive = true
                        )
                    }
                    is CoordinatorEvent.Stopped -> {
                        _uiState.value = _uiState.value.copy(
                            systemStatus = "Trading system stopped",
                            isActive = false
                        )
                    }
                    is CoordinatorEvent.EmergencyStopTriggered -> {
                        _uiState.value = _uiState.value.copy(
                            systemStatus = "⚠️ EMERGENCY STOP: ${event.reason}",
                            isActive = false
                        )
                    }
                    else -> {
                        // Ignore other events
                    }
                }
            }
        }
    }
    
    private fun updateBoardDecision(symbol: String, consensus: BoardConsensus) {
        // Extract board member votes from consensus opinions
        val members = consensus.opinions.map { opinion ->
            BoardMemberState(
                name = opinion.agentName,
                role = getRoleForAgent(opinion.agentName),
                emoji = getEmojiForAgent(opinion.agentName),
                vote = mapDecisionToVote(opinion.recommendation),
                confidence = opinion.confidence,
                reasoning = opinion.reasoning
            )
        }
        
        // Update UI state
        _uiState.value = _uiState.value.copy(
            currentSymbol = symbol,
            currentDecision = consensus.finalDecision.name,
            consensusConfidence = consensus.confidence,
            unanimousVotes = consensus.unanimousCount,
            totalVotes = 8,
            boardMembers = members,
            lastUpdateTime = System.currentTimeMillis(),
            systemStatus = "Analysis complete for $symbol"
        )
    }
    
    private fun getRoleForAgent(name: String): String = when (name) {
        "Arthur" -> "CTO (Chairman)"
        "Marcus" -> "CIO"
        "Helena" -> "CRO"
        "Sentinel" -> "CCO (Casting Vote)"
        "Oracle" -> "CDO"
        "Nexus" -> "COO"
        "Cipher" -> "CSO"
        "Aegis" -> "Chief Defense"
        else -> "Board Member"
    }
    
    private fun getEmojiForAgent(name: String): String = when (name) {
        "Arthur" -> "👔"
        "Marcus" -> "💼"
        "Helena" -> "🛡️"
        "Sentinel" -> "⚖️"
        "Oracle" -> "🔮"
        "Nexus" -> "⚡"
        "Cipher" -> "🔐"
        "Aegis" -> "🛡️"
        else -> "🤖"
    }
    
    private fun mapDecisionToVote(decision: TradeDecision): Vote {
        return when (decision) {
            TradeDecision.STRONG_BUY -> Vote.STRONG_BUY
            TradeDecision.BUY -> Vote.BUY
            TradeDecision.HOLD -> Vote.HOLD
            TradeDecision.SELL -> Vote.SELL
            TradeDecision.STRONG_SELL -> Vote.STRONG_SELL
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
