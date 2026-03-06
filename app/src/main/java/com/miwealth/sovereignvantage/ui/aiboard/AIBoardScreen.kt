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
import com.miwealth.sovereignvantage.ui.theme.*

/**
 * BUILD #110: AI Board Screen - THE OCTAGON
 * Shows 8 board members voting in real-time
 * 
 * For Arthur. For Cathryn. 💚
 */

data class BoardMember(
    val name: String,
    val role: String,
    val emoji: String,
    val vote: Vote = Vote.ABSTAIN,
    val confidence: Double = 0.0,
    val reasoning: String = ""
)

enum class Vote {
    STRONG_BUY,
    BUY,
    HOLD,
    SELL,
    STRONG_SELL,
    ABSTAIN
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIBoardScreen() {
    // BUILD #114 TODO: Wire this screen to TradingCoordinator.coordinatorEvents
    // Currently showing STATIC placeholder data.
    // Need to create AIBoardViewModel that subscribes to CoordinatorEvent.AnalysisComplete
    // and displays real board decisions with votes, confidence, reasoning.
    // See TradingCoordinator.kt line ~1222 for actual board consensus generation.
    
    val boardMembers = remember {
        listOf(
            BoardMember("Arthur", "CTO (Chairman)", "👔", Vote.HOLD, 0.0, "Awaiting market data..."),
            BoardMember("Marcus", "CIO", "💼", Vote.HOLD, 0.0, "Analyzing portfolio allocation..."),
            BoardMember("Helena", "CRO", "🛡️", Vote.HOLD, 0.0, "Monitoring risk levels..."),
            BoardMember("Sentinel", "CCO (Casting Vote)", "⚖️", Vote.HOLD, 0.0, "Compliance check in progress..."),
            BoardMember("Oracle", "CDO", "🔮", Vote.HOLD, 0.0, "Processing market intelligence..."),
            BoardMember("Nexus", "COO", "⚡", Vote.HOLD, 0.0, "Trade execution standby..."),
            BoardMember("Cipher", "CSO", "🔐", Vote.HOLD, 0.0, "Security systems nominal..."),
            BoardMember("Aegis", "Chief Defense", "🛡️", Vote.HOLD, 0.0, "Network protection active...")
        )
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
                            "CURRENT VOTE",
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
                                "HOLD",
                                style = MaterialTheme.typography.headlineMedium,
                                color = VintageColors.TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "8/8 votes",
                                style = MaterialTheme.typography.bodyLarge,
                                color = VintageColors.TextSecondary
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        LinearProgressIndicator(
                            progress = { 1.0f },
                            modifier = Modifier.fillMaxWidth(),
                            color = VintageColors.Gold,
                            trackColor = VintageColors.EmeraldDeep
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            "⏸️ Awaiting market conditions and trading signals",
                            style = MaterialTheme.typography.bodySmall,
                            color = VintageColors.TextTertiary
                        )
                    }
                }
            }
            
            // Board Members
            items(boardMembers) { member ->
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
private fun BoardMemberCard(member: BoardMember) {
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
            
            // Vote Badge
            Surface(
                color = getVoteColor(member.vote),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    member.vote.name.replace("_", " "),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldBlack
                )
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
