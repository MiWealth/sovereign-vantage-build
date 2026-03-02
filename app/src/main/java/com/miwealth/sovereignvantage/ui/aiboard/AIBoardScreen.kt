package com.miwealth.sovereignvantage.ui.aiboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.miwealth.sovereignvantage.core.ai.BoardVote
import com.miwealth.sovereignvantage.ui.theme.*

/**
 * AI BOARD SCREEN - V5.18.0 "Arthur Edition"
 * 
 * Displays the Octagon (8-member AI Board) with live recommendations,
 * consensus votes, confidence levels, and XAI decision audit trails.
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to: Cathryn 💘
 */
@Composable
fun AIBoardScreen(
    viewModel: AIBoardViewModel = viewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedMember by viewModel.selectedMember.collectAsState()
    val recentDecisions by viewModel.recentDecisions.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VintageColors.EmeraldDeep)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            AIBoardHeader(
                onNavigateBack = onNavigateBack
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Current Consensus Card
            if (uiState.currentConsensus != null) {
                ConsensusCard(
                    vote = uiState.currentConsensus!!.finalDecision,
                    confidence = uiState.currentConsensus!!.confidence,
                    weightedScore = uiState.currentConsensus!!.weightedScore,
                    unanimousCount = uiState.currentConsensus!!.unanimousCount,
                    totalMembers = uiState.currentConsensus!!.opinions.size
                )
                
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Board Members Grid
            Text(
                text = "THE OCTAGON BOARD",
                style = MaterialTheme.typography.titleMedium,
                color = VintageColors.GoldLeaf,
                fontFamily = FontFamily.Serif,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // 2x4 Grid of board members
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.boardMembers.chunked(2).forEach { rowMembers ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowMembers.forEach { member ->
                            BoardMemberCard(
                                member = member,
                                isSelected = selectedMember == member.displayName,
                                onClick = { viewModel.selectMember(member.displayName) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Fill empty space if odd number of members
                        if (rowMembers.size < 2) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Recent Decisions
            Text(
                text = "RECENT DECISIONS",
                style = MaterialTheme.typography.titleMedium,
                color = VintageColors.GoldLeaf,
                fontFamily = FontFamily.Serif,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(recentDecisions) { decision ->
                    DecisionCard(decision = decision)
                }
            }
        }
    }
}

@Composable
private fun AIBoardHeader(
    onNavigateBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        VintageColors.EmeraldVivid.copy(alpha = 0.3f),
                        VintageColors.SageGreen.copy(alpha = 0.2f)
                    )
                )
            )
            .border(
                width = 2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(VintageColors.GoldLeaf, VintageColors.BronzeDark)
                ),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "AI BOARD OF DIRECTORS",
                    style = MaterialTheme.typography.headlineSmall,
                    color = VintageColors.GoldLeaf,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "The Octagon • 8 Members • Live Analysis",
                    style = MaterialTheme.typography.bodySmall,
                    color = VintageColors.IvoryParchment.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Serif
                )
            }

            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = VintageColors.GoldLeaf
                )
            }
        }
    }
}

@Composable
private fun ConsensusCard(
    vote: BoardVote,
    confidence: Double,
    weightedScore: Double,
    unanimousCount: Int,
    totalMembers: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 2.dp,
                color = getVoteColor(vote),
                shape = RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = VintageColors.EmeraldVivid.copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "BOARD CONSENSUS",
                        style = MaterialTheme.typography.labelSmall,
                        color = VintageColors.IvoryParchment.copy(alpha = 0.6f),
                        fontFamily = FontFamily.Serif
                    )
                    Text(
                        text = vote.name.replace("_", " "),
                        style = MaterialTheme.typography.headlineMedium,
                        color = getVoteColor(vote),
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Confidence Meter
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "${(confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.headlineMedium,
                        color = VintageColors.GoldLeaf,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "CONFIDENCE",
                        style = MaterialTheme.typography.labelSmall,
                        color = VintageColors.IvoryParchment.copy(alpha = 0.6f),
                        fontFamily = FontFamily.Serif
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Statistics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatChip(
                    label = "Agreement",
                    value = "$unanimousCount/$totalMembers"
                )
                StatChip(
                    label = "Weighted Score",
                    value = String.format("%.3f", weightedScore)
                )
            }
        }
    }
}

@Composable
private fun StatChip(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = VintageColors.GoldLeaf,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = VintageColors.IvoryParchment.copy(alpha = 0.5f),
            fontFamily = FontFamily.Serif,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun BoardMemberCard(
    member: BoardMemberUIState,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(140.dp)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) VintageColors.GoldLeaf else VintageColors.BronzeDark.copy(alpha = 0.3f),
                shape = RoundedCornerShape(10.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                VintageColors.EmeraldVivid.copy(alpha = 0.3f)
            } else {
                VintageColors.EmeraldDeep.copy(alpha = 0.5f)
            }
        ),
        shape = RoundedCornerShape(10.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Member Icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                VintageColors.GoldLeaf.copy(alpha = 0.3f),
                                VintageColors.BronzeDark.copy(alpha = 0.2f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = VintageColors.GoldLeaf.copy(alpha = 0.5f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getMemberIcon(member.role),
                    contentDescription = member.displayName,
                    tint = VintageColors.GoldLeaf,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Member Name
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = member.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = VintageColors.GoldLeaf,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = member.role.split(" ").take(2).joinToString(" "),
                    style = MaterialTheme.typography.labelSmall,
                    color = VintageColors.IvoryParchment.copy(alpha = 0.5f),
                    fontFamily = FontFamily.Serif,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }

            // Current Vote
            if (member.currentVote != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(getVoteColor(member.currentVote).copy(alpha = 0.2f))
                        .border(
                            width = 1.dp,
                            color = getVoteColor(member.currentVote).copy(alpha = 0.5f),
                            shape = RoundedCornerShape(6.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = member.currentVote.name.split("_").take(1).joinToString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = getVoteColor(member.currentVote),
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun DecisionCard(
    decision: DecisionUIState
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = VintageColors.BronzeDark.copy(alpha = 0.3f),
                shape = RoundedCornerShape(10.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = VintageColors.EmeraldDeep.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Decision Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = decision.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        color = VintageColors.GoldLeaf,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = decision.timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = VintageColors.IvoryParchment.copy(alpha = 0.5f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(getVoteColor(decision.vote).copy(alpha = 0.2f))
                        .border(
                            width = 1.dp,
                            color = getVoteColor(decision.vote).copy(alpha = 0.5f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = decision.vote.name.replace("_", " "),
                        style = MaterialTheme.typography.labelMedium,
                        color = getVoteColor(decision.vote),
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Synthesis
            Text(
                text = decision.synthesis,
                style = MaterialTheme.typography.bodySmall,
                color = VintageColors.IvoryParchment.copy(alpha = 0.8f),
                fontFamily = FontFamily.Serif,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MiniStat(
                        label = "Confidence",
                        value = "${(decision.confidence * 100).toInt()}%"
                    )
                    MiniStat(
                        label = "Agreement",
                        value = "${decision.unanimousCount}/${decision.totalMembers}"
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniStat(
    label: String,
    value: String
) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = VintageColors.GoldLeaf,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = VintageColors.IvoryParchment.copy(alpha = 0.4f),
            fontFamily = FontFamily.Serif,
            fontSize = 9.sp
        )
    }
}

// Helper functions
private fun getVoteColor(vote: BoardVote): Color {
    return when (vote) {
        BoardVote.STRONG_BUY -> Color(0xFF00FF7F)  // Spring Green
        BoardVote.BUY -> Color(0xFF90EE90)         // Light Green
        BoardVote.HOLD -> Color(0xFFFFD700)        // Gold
        BoardVote.SELL -> Color(0xFFFF6347)        // Tomato
        BoardVote.STRONG_SELL -> Color(0xFFDC143C) // Crimson
    }
}

private fun getMemberIcon(role: String): ImageVector {
    return when {
        role.contains("CTO") || role.contains("Chairman") -> Icons.Default.Engineering
        role.contains("CRO") -> Icons.Default.Security
        role.contains("CCO") -> Icons.Default.Verified
        role.contains("CDO") -> Icons.Default.Analytics
        role.contains("COO") -> Icons.Default.NetworkCheck
        role.contains("CIO") -> Icons.Default.AccountBalance
        role.contains("CSO") -> Icons.Default.Lock
        role.contains("Chief Defense") -> Icons.Default.Shield
        else -> Icons.Default.Person
    }
}
