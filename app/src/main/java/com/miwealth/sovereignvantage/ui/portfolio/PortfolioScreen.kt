package com.miwealth.sovereignvantage.ui.portfolio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.miwealth.sovereignvantage.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    onNavigateBack: () -> Unit,
    viewModel: PortfolioViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        containerColor = VintageColors.EmeraldDeep,
        topBar = {
            Column {
            TopAppBar(
                title = {
                    Column {
                        Text("PORTFOLIO", fontWeight = FontWeight.Bold, color = VintageColors.Gold, letterSpacing = 1.sp)
                        Text(
                            "Performance & Analytics",
                            style = MaterialTheme.typography.bodySmall,
                            color = VintageColors.Gold.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = VintageColors.Gold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VintageColors.EmeraldDeep)
            )
            Spacer(modifier = Modifier.fillMaxWidth().height(1.5.dp).background(brush = Brush.horizontalGradient(colors = listOf(Color.Transparent, VintageColors.GoldDark, VintageColors.Gold, VintageColors.GoldDark, Color.Transparent))))
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(VintageColors.EmeraldDeep),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Portfolio Summary Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().border(1.dp, VintageColors.GoldDark, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = VintageColors.EmeraldDark),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        VintageColors.Gold.copy(alpha = 0.15f),
                                        VintageColors.GoldDark.copy(alpha = 0.05f)
                                    )
                                )
                            )
                            .padding(24.dp)
                    ) {
                        Column {
                            Text(
                                "Total Portfolio Value",
                                style = MaterialTheme.typography.bodyMedium,
                                color = VintageColors.TextSecondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "A$${String.format("%,.2f", uiState.totalValue)}",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = VintageColors.Gold
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                StatItem("24h P&L", uiState.dailyPnL, uiState.dailyPnLPercent)
                                StatItem("7d P&L", uiState.weeklyPnL, uiState.weeklyPnLPercent)
                                StatItem("30d P&L", uiState.monthlyPnL, uiState.monthlyPnLPercent)
                            }
                        }
                    }
                }
            }
            
            // Performance Metrics
            item {
                Text(
                    "PERFORMANCE METRICS",
                    color = VintageColors.Gold,
                    letterSpacing = 1.sp,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Sharpe Ratio",
                        value = String.format("%.2f", uiState.sharpeRatio),
                        isGood = uiState.sharpeRatio > 1.0
                    )
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Win Rate",
                        value = "${String.format("%.1f", uiState.winRate)}%",
                        isGood = uiState.winRate > 50
                    )
                }
            }
            
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Max Drawdown",
                        value = "${String.format("%.1f", uiState.maxDrawdown)}%",
                        isGood = uiState.maxDrawdown < 15
                    )
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Profit Factor",
                        value = String.format("%.2f", uiState.profitFactor),
                        isGood = uiState.profitFactor > 1.5
                    )
                }
            }
            
            // Holdings
            item {
                Text(
                    "HOLDINGS",
                    color = VintageColors.Gold,
                    letterSpacing = 1.sp,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            items(uiState.holdings) { holding ->
                HoldingCard(holding = holding)
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: Double, percent: Double) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = VintageColors.TextTertiary
        )
        Text(
            "${if (value >= 0) "+" else ""}$${String.format("%,.0f", value)}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (value >= 0) VintageColors.ProfitGreen else VintageColors.LossRed
        )
        Text(
            "${if (percent >= 0) "+" else ""}${String.format("%.1f", percent)}%",
            style = MaterialTheme.typography.bodySmall,
            color = if (percent >= 0) VintageColors.ProfitGreen else VintageColors.LossRed
        )
    }
}

@Composable
fun MetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    isGood: Boolean
) {
    Card(
        modifier = modifier.border(0.5.dp, VintageColors.GoldDark.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = VintageColors.EmeraldMedium),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodySmall,
                color = VintageColors.TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (isGood) VintageColors.ProfitGreen else VintageColors.LossRed
            )
        }
    }
}

@Composable
fun HoldingCard(holding: Holding) {
    Card(
        modifier = Modifier.fillMaxWidth().border(0.5.dp, VintageColors.GoldDark.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = VintageColors.EmeraldMedium),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Asset Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(VintageColors.Gold.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    holding.symbol.take(2),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = VintageColors.Gold
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    holding.symbol,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${holding.amount} ${holding.symbol.split("/")[0]}",
                    style = MaterialTheme.typography.bodySmall,
                    color = VintageColors.TextTertiary
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "A$${String.format("%,.2f", holding.value)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${if (holding.pnlPercent >= 0) "+" else ""}${String.format("%.2f", holding.pnlPercent)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (holding.pnlPercent >= 0) VintageColors.ProfitGreen else VintageColors.LossRed
                )
            }
        }
    }
}

data class Holding(
    val symbol: String,
    val amount: Double,
    val value: Double,
    val pnlPercent: Double
)
