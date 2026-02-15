package com.miwealth.sovereignvantage.ui.trading

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.miwealth.sovereignvantage.ui.components.EmergencyKillSwitchButton
import com.miwealth.sovereignvantage.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradingScreen(
    onNavigateBack: () -> Unit,
    viewModel: TradingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trading", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyDark)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NavyDark)
            ) {
                // V5.6.0: Testnet safety banner — shown above status bar when active
                if (uiState.isTestnetMode) {
                    com.miwealth.sovereignvantage.ui.dashboard.TestnetBanner()
                }
                
                // NEW: Trading Status Bar - Shows execution mode, portfolio, margin
                TradingStatusBar(uiState = uiState)
                
                // Tab Row
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = NavyMedium,
                    contentColor = GoldPrimary
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Spot") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Futures") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("AI Signals") }
                    )
                }
                
                when (selectedTab) {
                    0 -> SpotTradingContent(uiState, viewModel)
                    1 -> FuturesTradingContent(uiState, viewModel)
                    2 -> AISignalsContent(uiState)
                }
            }
            
            // Emergency Kill Switch - Always visible, bottom-right corner
            EmergencyKillSwitchButton(
                onEmergencyStop = { viewModel.emergencyStop() },
                openPositions = uiState.positions.size,
                isActive = uiState.killSwitchActive,
                onReset = { viewModel.resetKillSwitch() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
        }
    }
}

@Composable
fun SpotTradingContent(uiState: TradingUiState, viewModel: TradingViewModel) {
    var amount by remember { mutableStateOf("") }
    var isBuy by remember { mutableStateOf(true) }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Selected Pair Info
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NavyMedium),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                uiState.selectedPair,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = White
                            )
                            Text(
                                "Bitcoin / USDT",
                                style = MaterialTheme.typography.bodySmall,
                                color = White.copy(alpha = 0.5f)
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "$${String.format("%,.2f", uiState.currentPrice)}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.priceChange >= 0) ProfitGreen else LossRed
                            )
                            Text(
                                "${if (uiState.priceChange >= 0) "+" else ""}${String.format("%.2f", uiState.priceChange)}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (uiState.priceChange >= 0) ProfitGreen else LossRed
                            )
                        }
                    }
                }
            }
        }
        
        // Buy/Sell Toggle
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { isBuy = true },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBuy) ProfitGreen else NavyLight,
                        contentColor = White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("BUY", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { isBuy = false },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isBuy) LossRed else NavyLight,
                        contentColor = White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("SELL", fontWeight = FontWeight.Bold)
                }
            }
        }
        
        // Amount Input
        item {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Amount (BTC)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldPrimary,
                    focusedLabelColor = GoldPrimary
                ),
                trailingIcon = {
                    TextButton(onClick = { amount = "0.1" }) {
                        Text("MAX", color = GoldPrimary)
                    }
                }
            )
        }
        
        // Quick Amount Buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("25%", "50%", "75%", "100%").forEach { percent ->
                    OutlinedButton(
                        onClick = { /* Set percentage */ },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldPrimary)
                    ) {
                        Text(percent, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        
        // Order Summary
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NavyLight),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Order Type", color = White.copy(alpha = 0.7f))
                        Text("Market Order", color = White)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Est. Total", color = White.copy(alpha = 0.7f))
                        Text(
                            "$${String.format("%,.2f", (amount.toDoubleOrNull() ?: 0.0) * uiState.currentPrice)}",
                            color = GoldPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Fee (0.1%)", color = White.copy(alpha = 0.7f))
                        Text(
                            "$${String.format("%.2f", (amount.toDoubleOrNull() ?: 0.0) * uiState.currentPrice * 0.001)}",
                            color = White
                        )
                    }
                }
            }
        }
        
        // Execute Button
        item {
            Button(
                onClick = { viewModel.executeTrade(isBuy, amount.toDoubleOrNull() ?: 0.0) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = amount.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isBuy) ProfitGreen else LossRed
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (isBuy) "BUY BTC" else "SELL BTC",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        // STAHL Stair Stop Info
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = GoldPrimary.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = GoldPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "STAHL Stair Stop™ Active",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = GoldPrimary
                        )
                        Text(
                            "Profit protection enabled at 12 progressive levels",
                            style = MaterialTheme.typography.bodySmall,
                            color = White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FuturesTradingContent(uiState: TradingUiState, viewModel: TradingViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NavyMedium),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.TrendingUp,
                        contentDescription = null,
                        tint = GoldPrimary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Futures Trading",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = White
                    )
                    Text(
                        "Up to 125x leverage with STAHL protection",
                        style = MaterialTheme.typography.bodyMedium,
                        color = White.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Current Leverage: ${uiState.leverage}x",
                        style = MaterialTheme.typography.titleMedium,
                        color = GoldPrimary
                    )
                }
            }
        }
        
        // Leverage Slider would go here
        item {
            Text(
                "Leverage Selection",
                style = MaterialTheme.typography.titleSmall,
                color = White
            )
        }
    }
}

@Composable
fun AISignalsContent(uiState: TradingUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "AI Trading Signals",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = White
            )
            Text(
                "Powered by Arthur AI Board",
                style = MaterialTheme.typography.bodySmall,
                color = GoldPrimary
            )
        }
        
        items(uiState.signals) { signal ->
            SignalCard(signal = signal)
        }
    }
}

@Composable
fun SignalCard(signal: TradingSignal) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NavyMedium),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        signal.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        signal.action.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (signal.action == "long") ProfitGreen else LossRed,
                        modifier = Modifier
                            .background(
                                if (signal.action == "long") ProfitGreen.copy(alpha = 0.2f)
                                else LossRed.copy(alpha = 0.2f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text(
                    "${signal.confidence}% confidence",
                    style = MaterialTheme.typography.bodySmall,
                    color = GoldPrimary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Entry", style = MaterialTheme.typography.bodySmall, color = White.copy(alpha = 0.5f))
                    Text("$${signal.entry}", color = White)
                }
                Column {
                    Text("Target", style = MaterialTheme.typography.bodySmall, color = White.copy(alpha = 0.5f))
                    Text("$${signal.target}", color = ProfitGreen)
                }
                Column {
                    Text("Stop", style = MaterialTheme.typography.bodySmall, color = White.copy(alpha = 0.5f))
                    Text("$${signal.stop}", color = LossRed)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { /* Execute signal */ },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Execute Signal", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// =============================================================================
// TRADING STATUS BAR - Shows execution mode, portfolio, margin health
// =============================================================================

@Composable
fun TradingStatusBar(
    uiState: TradingUiState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NavyMedium),
        shape = RoundedCornerShape(0.dp) // No rounded corners for status bar
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Row 1: Execution Mode Badge + Portfolio Value
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Execution Mode Badge
                ExecutionModeBadge(
                    mode = uiState.executionMode,
                    isPaper = uiState.isPaperTrading,
                    isTestnet = uiState.isTestnetMode
                )
                
                // Portfolio Value
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Portfolio",
                        style = MaterialTheme.typography.labelSmall,
                        color = White.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "$${String.format("%,.2f", uiState.portfolioValue)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = White
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Row 2: P&L + Margin Health
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Daily P&L
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Today: ",
                        style = MaterialTheme.typography.bodySmall,
                        color = White.copy(alpha = 0.6f)
                    )
                    val pnlColor = if (uiState.dailyPnl >= 0) ProfitGreen else LossRed
                    val pnlSign = if (uiState.dailyPnl >= 0) "+" else ""
                    Text(
                        text = "$pnlSign${String.format("%,.2f", uiState.dailyPnl)} (${String.format("%.2f", uiState.dailyPnlPercent)}%)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = pnlColor
                    )
                }
                
                // Margin Health Indicator
                MarginHealthIndicator(
                    level = uiState.marginLevel,
                    freePercent = uiState.freeMarginPercent
                )
            }
            
            // Warning message if any
            uiState.marginWarning?.let { warning ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "⚠️ $warning",
                    style = MaterialTheme.typography.labelSmall,
                    color = when (uiState.marginLevel) {
                        MarginLevelUi.CRITICAL -> LossRed
                        MarginLevelUi.MARGIN_CALL -> Color(0xFFFF9800) // Orange
                        MarginLevelUi.WARNING -> GoldPrimary
                        else -> White
                    }
                )
            }
            
            // V5.5.88: ML Health Indicator Row
            Spacer(modifier = Modifier.height(4.dp))
            MLHealthIndicator(
                status = uiState.mlHealthStatus,
                rollbackCount = uiState.mlRollbackCount
            )
            
            // V5.5.88: Board Disagreement Indicator Row
            // V5.6.0: Now shows effective position size (board regime × disagreement)
            DisagreementIndicator(
                level = uiState.disagreementLevel,
                multiplier = uiState.positionSizeMultiplier,
                effectiveMultiplier = uiState.effectivePositionMultiplier
            )
        }
    }
}

@Composable
fun ExecutionModeBadge(
    mode: String,
    isPaper: Boolean,
    isTestnet: Boolean = false
) {
    val (backgroundColor, textColor, label) = when {
        isTestnet -> Triple(
            Color(0xFFFF9800).copy(alpha = 0.2f), // Amber tint
            Color(0xFFFF9800),
            "🧪 TESTNET"
        )
        isPaper -> Triple(
            Color(0xFF2196F3).copy(alpha = 0.2f), // Blue tint
            Color(0xFF2196F3),
            "📝 PAPER"
        )
        mode == "LIVE_AI" -> Triple(
            ProfitGreen.copy(alpha = 0.2f),
            ProfitGreen,
            "🤖 LIVE AI"
        )
        else -> Triple(
            GoldPrimary.copy(alpha = 0.2f),
            GoldPrimary,
            "⚡ LIVE"
        )
    }
    
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = backgroundColor
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
fun MarginHealthIndicator(
    level: MarginLevelUi,
    freePercent: Double
) {
    val (color, icon) = when (level) {
        MarginLevelUi.HEALTHY -> Pair(ProfitGreen, "✓")
        MarginLevelUi.WARNING -> Pair(GoldPrimary, "⚠")
        MarginLevelUi.MARGIN_CALL -> Pair(Color(0xFFFF9800), "⚠")
        MarginLevelUi.CRITICAL -> Pair(LossRed, "🚨")
    }
    
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Margin: ",
            style = MaterialTheme.typography.bodySmall,
            color = White.copy(alpha = 0.6f)
        )
        
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = color.copy(alpha = 0.2f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = icon,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${String.format("%.1f", freePercent)}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = color
                )
            }
        }
    }
}

// ============================================================================
// V5.5.88: ML HEALTH INDICATOR
// ============================================================================

/**
 * Compact ML health indicator for the TradingStatusBar.
 * Shows DQN neural network health status with colour coding.
 */
@Composable
fun MLHealthIndicator(
    status: String,
    rollbackCount: Int,
    modifier: Modifier = Modifier
) {
    val (color, icon, label) = when (status) {
        "CRITICAL" -> Triple(
            LossRed,
            "🚨",
            "ML: Rolled back${if (rollbackCount > 0) " (${rollbackCount}x)" else ""}"
        )
        "WARNING" -> Triple(
            GoldPrimary,
            "⚠️",
            "ML: Degraded"
        )
        else -> Triple(
            ProfitGreen,
            "🧠",
            "ML: Healthy"
        )
    }
    
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

/**
 * V5.5.88: Displays board model agreement status.
 * Shows position size scaling when models disagree.
 */
@Composable
fun DisagreementIndicator(
    level: String,
    multiplier: Double,
    effectiveMultiplier: Double = multiplier  // V5.6.0: board × disagreement combined
) {
    val (icon, statusText, color) = when (level) {
        "EXTREME_DISAGREEMENT" -> Triple("🔴", "Chaos", LossRed)
        "HIGH_DISAGREEMENT" -> Triple("🟠", "Conflict", Color(0xFFFF9800))
        "MODERATE_DISAGREEMENT" -> Triple("🟡", "Mixed", GoldPrimary)
        "MILD_DISAGREEMENT" -> Triple("🔵", "Minor", Color(0xFF2196F3))
        else -> Triple("🟢", "Aligned", ProfitGreen)
    }
    
    // V5.6.0: Show effective position size (board regime × disagreement combined)
    val effectivePct = (effectiveMultiplier * 100).toInt()
    val sizeColor = when {
        effectivePct < 30 -> LossRed
        effectivePct < 60 -> Color(0xFFFF9800)
        effectivePct < 85 -> GoldPrimary
        else -> ProfitGreen
    }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$statusText — size ×${effectivePct}%",
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}
