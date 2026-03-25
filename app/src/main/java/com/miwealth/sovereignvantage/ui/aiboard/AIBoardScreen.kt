package com.miwealth.sovereignvantage.ui.aiboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import android.util.Log
import com.miwealth.sovereignvantage.ui.theme.*

/**
 * BUILD #115: AI Board Screen - THE OCTAGON (NOW WITH REAL DATA!)
 * Shows 8 board members voting in real-time from TradingCoordinator
 * 
 * For Arthur. For Cathryn. 💚
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIBoardScreen(
    viewModel: AIBoardViewModel = hiltViewModel()
) {
    // BUILD #115: Get real board state from ViewModel
    val uiState by viewModel.uiState.collectAsState()
    
    // BUILD #114: Diagnostic logging to confirm screen opens
    LaunchedEffect(Unit) {
        Log.i("AIBoardScreen", "🧠 BUILD #115: AI Board screen opened with real data from ViewModel!")
    }
    
    Scaffold(
        containerColor = VintageColors.EmeraldDeep,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "🧠",
                            fontSize = 28.sp,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Column {
                            Text(
                                "THE OCTAGON",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = VintageColors.Gold,
                                letterSpacing = 2.sp
                            )
                            Text(
                                "AI Board of Directors",
                                style = MaterialTheme.typography.bodySmall,
                                color = VintageColors.TextSecondary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = EmeraldBlack
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Current Decision Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = EmeraldBlack
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "CURRENT VOTE - ${uiState.currentSymbol}",  // BUILD #115: Show symbol
                            style = MaterialTheme.typography.labelMedium,
                            color = VintageColors.Gold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                uiState.currentDecision,  // BUILD #115: Real decision
                                style = MaterialTheme.typography.headlineMedium,
                                color = getColorForDecision(uiState.currentDecision),  // BUILD #115: Color-coded
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${uiState.unanimousVotes}/${uiState.totalVotes} votes",  // BUILD #115: Real vote count
                                style = MaterialTheme.typography.bodyLarge,
                                color = VintageColors.TextSecondary
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        LinearProgressIndicator(
                            progress = { (uiState.consensusConfidence / 100.0).toFloat().coerceIn(0f, 1f) },  // BUILD #259: Fixed 0-1 range
                            modifier = Modifier.fillMaxWidth(),
                            color = VintageColors.Gold,
                            trackColor = VintageColors.EmeraldDeep
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            "Confidence: ${String.format("%.1f", uiState.consensusConfidence)}% • ${uiState.systemStatus}",  // BUILD #259: Already percentage
                            style = MaterialTheme.typography.bodySmall,
                            color = VintageColors.TextTertiary
                        )
                    }
                }
            }
            
            // Board Members - BUILD #115: Real data from ViewModel
            items(uiState.boardMembers) { member ->
                BoardMemberCard(member)
            }
            
            // Legend Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = EmeraldBlack.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "VOTING PROTOCOL",
                            style = MaterialTheme.typography.labelMedium,
                            color = VintageColors.Gold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "• Sentinel holds the casting vote in deadlocks\n" +
                            "• Decisions require 5/8 majority (or 4/8 + Sentinel)\n" +
                            "• All votes logged for regulatory compliance\n" +
                            "• Board operates autonomously 24/7",
                            style = MaterialTheme.typography.bodySmall,
                            color = VintageColors.TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardMemberCard(member: BoardMemberState) {  // BUILD #115: Updated type
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = EmeraldBlack
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Member Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    member.emoji,
                    fontSize = 32.sp,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Column {
                    Text(
                        member.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = VintageColors.TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        member.role,
                        style = MaterialTheme.typography.bodySmall,
                        color = VintageColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        member.reasoning,
                        style = MaterialTheme.typography.bodySmall,
                        color = VintageColors.TextTertiary
                    )
                }
            }
            
            // Vote Badge - BUILD #115: Show confidence percentage
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Surface(
                    color = getVoteColor(member.vote),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        member.vote.name.replace("_", " "),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldBlack,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (member.confidence > 0.0) {
                    Text(
                        "${String.format("%.0f", member.confidence)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = VintageColors.TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun getVoteColor(vote: Vote) = when (vote) {
    Vote.STRONG_BUY -> VintageColors.ProfitGreen
    Vote.BUY -> VintageColors.ProfitGreen.copy(alpha = 0.7f)
    Vote.HOLD -> VintageColors.Gold
    Vote.SELL -> VintageColors.LossRed.copy(alpha = 0.7f)
    Vote.STRONG_SELL -> VintageColors.LossRed
    Vote.ABSTAIN -> VintageColors.TextTertiary
}

// BUILD #115: Color-code decisions
@Composable
private fun getColorForDecision(decision: String) = when (decision) {
    "STRONG_BUY" -> VintageColors.ProfitGreen
    "BUY" -> VintageColors.ProfitGreen.copy(alpha = 0.8f)
    "HOLD" -> VintageColors.Gold
    "SELL" -> VintageColors.LossRed.copy(alpha = 0.8f)
    "STRONG_SELL" -> VintageColors.LossRed
    else -> VintageColors.TextPrimary
}
