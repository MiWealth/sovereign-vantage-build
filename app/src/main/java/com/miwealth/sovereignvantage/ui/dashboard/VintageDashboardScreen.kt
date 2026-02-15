package com.miwealth.sovereignvantage.ui.dashboard

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.miwealth.sovereignvantage.ui.components.VintageCandlestickChart
import com.miwealth.sovereignvantage.ui.components.CandleData
import com.miwealth.sovereignvantage.ui.components.ChartTimeframe
import com.miwealth.sovereignvantage.ui.components.generateMockCandles
import com.miwealth.sovereignvantage.ui.theme.*

/**
 * SOVEREIGN VANTAGE - VINTAGE DASHBOARD
 * 
 * Luxury-styled dashboard matching the exact mockup specifications:
 * - Deep emerald green leather background
 * - "SOVEREIGN VANTAGE" in gold serif typography  
 * - Portfolio value: Large gold numerals
 * - Green change indicator with gold text
 * - BTC/USD candlestick chart (placeholder)
 * - Gold decorative dividers with ornate scrollwork
 * - Position cards with gold borders
 * - Gold brushed metal navigation bar
 * 
 * DESIGN REFERENCE: Dashboard mockup Image 19
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VintageDashboardScreen(
    onNavigateToTrading: () -> Unit,
    onNavigateToAIBoard: () -> Unit,
    onNavigateToPortfolio: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = VintageTheme.colors
    val isVintage = VintageTheme.isVintageMode
    
    Scaffold(
        containerColor = colors.emeraldDeep,
        bottomBar = {
            VintageNavigationBar(
                currentDestination = "dashboard",
                onDashboardClick = { },
                onTradeClick = onNavigateToTrading,
                onAIBoardClick = onNavigateToAIBoard,
                onPortfolioClick = onNavigateToPortfolio,
                onSettingsClick = onNavigateToSettings
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    brush = Brush.verticalGradient(
                        colors = if (isVintage) {
                            listOf(
                                colors.emeraldDeep,
                                colors.emeraldDark
                            )
                        } else {
                            listOf(colors.background, colors.background)
                        }
                    )
                )
        ) {
            // Leather texture overlay for Vintage mode
            if (isVintage) {
                LeatherTextureOverlay()
            }
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with logo
                item {
                    VintageHeader(
                        isSecure = uiState.pqcSecurityEnabled,
                        isPaperTrading = uiState.paperTradingMode
                    )
                }
                
                // Portfolio Value Card
                item {
                    VintagePortfolioCard(
                        totalValue = uiState.totalPortfolioValue,
                        dailyChange = uiState.dailyChange,
                        dailyChangePercent = uiState.dailyChangePercent
                    )
                }
                
                // Decorative Divider
                item {
                    GoldDivider()
                }
                
                // Chart with real component
                item {
                    var selectedTimeframe by remember { mutableStateOf(ChartTimeframe.H4) }
                    val mockCandles = remember { generateMockCandles(60, 104000.0) }
                    
                    VintageCandlestickChart(
                        symbol = "BTC/USD",
                        candles = mockCandles,
                        currentPrice = 104235.67,
                        priceChange24h = 2.34,
                        selectedTimeframe = selectedTimeframe,
                        onTimeframeChange = { selectedTimeframe = it },
                        showVolume = true,
                        showGrid = true
                    )
                }
                
                // Decorative Divider
                item {
                    GoldDivider()
                }
                
                // Positions Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "POSITIONS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.gold,
                            letterSpacing = 2.sp
                        )
                        Text(
                            "${uiState.activePositions} Active",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
                
                // Position Cards
                items(getMockPositions()) { position ->
                    VintagePositionCard(position = position)
                }
                
                // Strategy Status
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    GoldDivider()
                    Spacer(modifier = Modifier.height(16.dp))
                    VintageStrategyStatusCard(
                        alphaScannerActive = true,
                        fundingArbActive = true,
                        activeTrades = uiState.todayTrades
                    )
                }
                
                // Bottom spacer for nav bar
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            
            // Kill switch overlay
            if (uiState.killSwitchActive) {
                KillSwitchOverlay(
                    onReset = { viewModel.resetKillSwitch() }
                )
            }
        }
    }
}

// =============================================================================
// VINTAGE HEADER
// =============================================================================

@Composable
private fun VintageHeader(
    isSecure: Boolean,
    isPaperTrading: Boolean
) {
    val colors = VintageTheme.colors
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo/Title
        Text(
            "SOVEREIGN VANTAGE",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = colors.gold,
            letterSpacing = 3.sp,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Status badges
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isPaperTrading) {
                StatusBadge(
                    text = "PAPER",
                    color = colors.profitGreen.copy(alpha = 0.3f),
                    textColor = colors.profitGreen
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            StatusBadge(
                text = if (isSecure) "PQC SECURED" else "DEMO",
                color = if (isSecure) colors.gold.copy(alpha = 0.2f) else colors.warningAmber.copy(alpha = 0.2f),
                textColor = if (isSecure) colors.gold else colors.warningAmber
            )
        }
    }
}

@Composable
private fun StatusBadge(
    text: String,
    color: Color,
    textColor: Color
) {
    Surface(
        color = color,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            letterSpacing = 1.sp
        )
    }
}

// =============================================================================
// PORTFOLIO VALUE CARD
// =============================================================================

@Composable
private fun VintagePortfolioCard(
    totalValue: Double,
    dailyChange: Double,
    dailyChangePercent: Double
) {
    val colors = VintageTheme.colors
    val isVintage = VintageTheme.isVintageMode
    val isPositive = dailyChange >= 0
    
    VintageCard(
        modifier = Modifier.fillMaxWidth(),
        showGoldFrame = isVintage
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "PORTFOLIO VALUE",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                letterSpacing = 2.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Large value display
            Text(
                "$${formatLargeNumber(totalValue)}",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = colors.gold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Daily change
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    color = if (isPositive) colors.profitGreen.copy(alpha = 0.15f) 
                            else colors.lossRed.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = if (isPositive) colors.profitGreen else colors.lossRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "${if (isPositive) "+" else ""}$${formatLargeNumber(kotlin.math.abs(dailyChange))}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isPositive) colors.profitGreen else colors.lossRed
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "(${if (isPositive) "+" else ""}${String.format("%.2f", dailyChangePercent)}%)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isPositive) colors.profitGreen else colors.lossRed
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// POSITION CARD
// =============================================================================

data class PositionData(
    val symbol: String,
    val quantity: Double,
    val entryPrice: Double,
    val currentPrice: Double,
    val pnl: Double,
    val pnlPercent: Double
)

@Composable
private fun VintagePositionCard(position: PositionData) {
    val colors = VintageTheme.colors
    val isPositive = position.pnl >= 0
    
    VintageCard(
        modifier = Modifier.fillMaxWidth(),
        showGoldFrame = VintageTheme.isVintageMode
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Symbol and quantity
            Column {
                Text(
                    position.symbol,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.gold
                )
                Text(
                    "${position.quantity} @ $${formatLargeNumber(position.entryPrice)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
            
            // Current value and PnL
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "$${formatLargeNumber(position.currentPrice * position.quantity)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                        contentDescription = null,
                        tint = if (isPositive) colors.profitGreen else colors.lossRed,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "${if (isPositive) "+" else ""}${String.format("%.2f", position.pnlPercent)}%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isPositive) colors.profitGreen else colors.lossRed
                    )
                }
            }
        }
    }
}

// =============================================================================
// STRATEGY STATUS CARD
// =============================================================================

@Composable
private fun VintageStrategyStatusCard(
    alphaScannerActive: Boolean,
    fundingArbActive: Boolean,
    activeTrades: Int
) {
    val colors = VintageTheme.colors
    
    VintageCard(
        modifier = Modifier.fillMaxWidth(),
        title = "STRATEGY STATUS",
        showGoldFrame = VintageTheme.isVintageMode
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            StrategyStatusRow(
                name = "Alpha Factor Scanner",
                isActive = alphaScannerActive,
                status = if (alphaScannerActive) "Scanning" else "Idle"
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            StrategyStatusRow(
                name = "Funding Arbitrage",
                isActive = fundingArbActive,
                status = if (fundingArbActive) "Active" else "Idle"
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            HorizontalDivider(color = colors.divider)
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Today's Trades",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
                Text(
                    "$activeTrades",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.gold
                )
            }
        }
    }
}

@Composable
private fun StrategyStatusRow(
    name: String,
    isActive: Boolean,
    status: String
) {
    val colors = VintageTheme.colors
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isActive) colors.profitGreen else colors.onSurfaceVariant)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface
            )
        }
        Text(
            status,
            style = MaterialTheme.typography.labelMedium,
            color = if (isActive) colors.profitGreen else colors.onSurfaceVariant
        )
    }
}

// =============================================================================
// NAVIGATION BAR
// =============================================================================

@Composable
private fun VintageNavigationBar(
    currentDestination: String,
    onDashboardClick: () -> Unit,
    onTradeClick: () -> Unit,
    onAIBoardClick: () -> Unit,
    onPortfolioClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val colors = VintageTheme.colors
    val isVintage = VintageTheme.isVintageMode
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isVintage) colors.navBarBackground else colors.surface,
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .then(
                    if (isVintage) Modifier.background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                VintageColors.GradientGoldStart.copy(alpha = 0.1f),
                                VintageColors.GradientGoldEnd.copy(alpha = 0.05f)
                            )
                        )
                    ) else Modifier
                ),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            NavBarItem(
                icon = Icons.Default.Dashboard,
                label = "Dashboard",
                selected = currentDestination == "dashboard",
                onClick = onDashboardClick
            )
            NavBarItem(
                icon = Icons.Default.TrendingUp,
                label = "Trade",
                selected = currentDestination == "trade",
                onClick = onTradeClick
            )
            NavBarItem(
                icon = Icons.Default.Groups,
                label = "AI Board",
                selected = currentDestination == "aiboard",
                onClick = onAIBoardClick
            )
            NavBarItem(
                icon = Icons.Default.AccountBalance,
                label = "Portfolio",
                selected = currentDestination == "portfolio",
                onClick = onPortfolioClick
            )
            NavBarItem(
                icon = Icons.Default.Settings,
                label = "Settings",
                selected = currentDestination == "settings",
                onClick = onSettingsClick
            )
        }
    }
}

@Composable
private fun NavBarItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = VintageTheme.colors
    
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) colors.gold else colors.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) colors.gold else colors.onSurfaceVariant
        )
    }
}

// =============================================================================
// DECORATIVE ELEMENTS
// =============================================================================

@Composable
private fun GoldDivider() {
    val colors = VintageTheme.colors
    val isVintage = VintageTheme.isVintageMode
    
    if (isVintage) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp),
            contentAlignment = Alignment.Center
        ) {
            // Center ornament
            Canvas(modifier = Modifier.fillMaxWidth()) {
                val centerY = size.height / 2
                val centerX = size.width / 2
                
                // Left line
                drawLine(
                    color = Color(0xFFD4AF37),
                    start = Offset(0f, centerY),
                    end = Offset(centerX - 40, centerY),
                    strokeWidth = 1f
                )
                
                // Right line
                drawLine(
                    color = Color(0xFFD4AF37),
                    start = Offset(centerX + 40, centerY),
                    end = Offset(size.width, centerY),
                    strokeWidth = 1f
                )
                
                // Center diamond
                val path = Path().apply {
                    moveTo(centerX, centerY - 8)
                    lineTo(centerX + 8, centerY)
                    lineTo(centerX, centerY + 8)
                    lineTo(centerX - 8, centerY)
                    close()
                }
                drawPath(path, color = Color(0xFFD4AF37), style = Stroke(width = 1f))
            }
        }
    } else {
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = colors.divider
        )
    }
}

@Composable
private fun LeatherTextureOverlay() {
    // Subtle texture overlay for leather effect
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Add subtle noise/grain for leather texture
        // In production, use actual texture bitmap
    }
}

@Composable
private fun KillSwitchOverlay(onReset: () -> Unit) {
    val colors = VintageTheme.colors
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = colors.lossRed,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "KILL SWITCH ACTIVE",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = colors.lossRed
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "All positions liquidated to USDT",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            VintageButton(
                text = "RESET & RESTART",
                onClick = onReset,
                modifier = Modifier.fillMaxWidth(0.8f)
            )
        }
    }
}

// =============================================================================
// UTILITY FUNCTIONS
// =============================================================================

private fun formatLargeNumber(value: Double): String {
    return when {
        value >= 1_000_000 -> String.format("%.2fM", value / 1_000_000)
        value >= 1_000 -> String.format("%,.2f", value)
        else -> String.format("%.2f", value)
    }
}

private fun getMockPositions(): List<PositionData> {
    return listOf(
        PositionData("BTC", 12.50, 69500.0, 104235.67, 434197.09, 49.98),
        PositionData("ETH", 150.0, 3450.0, 3891.23, 66184.50, 12.79),
        PositionData("SOL", 500.0, 98.0, 234.56, 68280.00, 139.35)
    )
}
