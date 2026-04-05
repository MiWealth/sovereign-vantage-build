package com.miwealth.sovereignvantage.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.miwealth.sovereignvantage.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

/**
 * BUILD #298: SIMPLE FUNCTIONAL DASHBOARD
 * 
 * Stripped down to essentials:
 * - Live price tiles (BTC, ETH, SOL, XRP)
 * - Portfolio value card
 * - AI board status
 * - Active positions
 * 
 * Focus: FUNCTION over form. Show Mike his backend actually works!
 */

@Composable
fun SimpleDashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.US).apply { 
        maximumFractionDigits = 2
        currency = java.util.Currency.getInstance("AUD")
    }}
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VintageColors.EmeraldDeep)
    ) {
        // BUILD #367: Gold decorative accent line at top
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            VintageColors.GoldDark,
                            VintageColors.Gold,
                            VintageColors.GoldBright,
                            VintageColors.Gold,
                            VintageColors.GoldDark,
                            Color.Transparent
                        )
                    )
                )
        )
        
        Column(modifier = Modifier.padding(16.dp)) {
            // BUILD #367: Enhanced header with gold serif typography
            Text(
                "SOVEREIGN VANTAGE",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = VintageColors.GoldBright,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            
            // Tagline
            Text(
                "Arthur Edition",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Light,
                color = VintageColors.GoldDark,
                letterSpacing = 4.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        
        if (uiState.paperTradingMode) {
            Surface(
                color = VintageColors.EmeraldAccent.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text(
                    "📄 PAPER TRADING MODE",
                    style = MaterialTheme.typography.titleSmall,
                    color = VintageColors.EmeraldAccent,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
        
        // BUILD #330: Error/Info banner for VIEW LOGS feedback
        if (uiState.error != null) {
            Surface(
                color = if (uiState.error!!.startsWith("✅")) 
                    VintageColors.ProfitGreen.copy(alpha = 0.3f) 
                    else VintageColors.LossRed.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        uiState.error!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (uiState.error!!.startsWith("✅")) 
                            VintageColors.ProfitGreen 
                            else VintageColors.LossRed,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.clearError() }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = VintageColors.TextMuted
                        )
                    }
                }
            }
        }
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Portfolio Value
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = VintageColors.EmeraldMedium),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, VintageColors.Gold.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Portfolio Value",
                            style = MaterialTheme.typography.titleMedium,
                            color = VintageColors.TextSecondary
                        )
                        Text(
                            currencyFormat.format(uiState.totalPortfolioValue),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = VintageColors.Gold
                        )
                        
                        Row(modifier = Modifier.padding(top = 8.dp)) {
                            Text(
                                "Daily: ${if (uiState.dailyChange >= 0) "+" else ""}${currencyFormat.format(uiState.dailyChange)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (uiState.dailyChange >= 0) VintageColors.ProfitGreen else VintageColors.LossRed
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                "${if (uiState.dailyChangePercent >= 0) "+" else ""}${String.format("%.2f", uiState.dailyChangePercent)}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (uiState.dailyChangePercent >= 0) VintageColors.ProfitGreen else VintageColors.LossRed
                            )
                        }
                    }
                }
            }
            
            // AI Status
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = VintageColors.EmeraldMedium),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, VintageColors.Gold.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.SmartToy,
                                contentDescription = null,
                                tint = if (uiState.aiTradingActive) VintageColors.ProfitGreen else VintageColors.LossRed,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "AI Board: ${if (uiState.aiTradingActive) "ACTIVE" else "INACTIVE"}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.aiTradingActive) VintageColors.ProfitGreen else VintageColors.LossRed
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    "${uiState.activeStrategies}",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = VintageColors.Gold
                                )
                                Text(
                                    "Strategies",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VintageColors.TextSecondary
                                )
                            }
                            Column {
                                Text(
                                    "${uiState.activePositions}",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = VintageColors.Gold
                                )
                                Text(
                                    "Positions",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VintageColors.TextSecondary
                                )
                            }
                            Column {
                                Text(
                                    "${uiState.todayTrades}",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = VintageColors.Gold
                                )
                                Text(
                                    "Trades Today",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VintageColors.TextSecondary
                                )
                            }
                        }
                        
                        // BUILD #303: Logs button in AI Board card for visibility
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.exportLogs() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VintageColors.Gold.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(8.dp)
                        ) {
                            Icon(
                                Icons.Default.List,
                                contentDescription = "Logs",
                                modifier = Modifier.size(16.dp),
                                tint = VintageColors.Gold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "VIEW LOGS",
                                style = MaterialTheme.typography.labelMedium,
                                color = VintageColors.Gold
                            )
                        }
                    }
                }
            }
            
            // BUILD #338: Log Viewer (expandable)
            if (uiState.logsVisible) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = VintageColors.EmeraldDark),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, VintageColors.Gold.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Header with close button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.List,
                                        contentDescription = null,
                                        tint = VintageColors.Gold,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "SYSTEM LOGS",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = VintageColors.Gold
                                    )
                                }
                                Row {
                                    // Refresh button
                                    IconButton(
                                        onClick = { viewModel.refreshLogs() },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = "Refresh",
                                            tint = VintageColors.TextSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    // Close button
                                    IconButton(
                                        onClick = { viewModel.exportLogs() },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Close",
                                            tint = VintageColors.TextMuted,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Category filters
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(bottom = 12.dp)
                            ) {
                                item {
                                    FilterChip(
                                        selected = uiState.selectedLogCategory == null,
                                        onClick = { viewModel.filterLogs(null) },
                                        label = { Text("ALL", fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = VintageColors.Gold.copy(alpha = 0.3f),
                                            selectedLabelColor = VintageColors.Gold
                                        )
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = uiState.selectedLogCategory == com.miwealth.sovereignvantage.core.utils.SystemLogger.Category.TRADE,
                                        onClick = { viewModel.filterLogs(com.miwealth.sovereignvantage.core.utils.SystemLogger.Category.TRADE) },
                                        label = { Text("TRADE", fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = VintageColors.ProfitGreen.copy(alpha = 0.3f),
                                            selectedLabelColor = VintageColors.ProfitGreen
                                        )
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = uiState.selectedLogCategory == com.miwealth.sovereignvantage.core.utils.SystemLogger.Category.RISK,
                                        onClick = { viewModel.filterLogs(com.miwealth.sovereignvantage.core.utils.SystemLogger.Category.RISK) },
                                        label = { Text("RISK", fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFFFF9800).copy(alpha = 0.3f),
                                            selectedLabelColor = Color(0xFFFF9800)
                                        )
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = uiState.selectedLogCategory == com.miwealth.sovereignvantage.core.utils.SystemLogger.Category.ERROR,
                                        onClick = { viewModel.filterLogs(com.miwealth.sovereignvantage.core.utils.SystemLogger.Category.ERROR) },
                                        label = { Text("ERROR", fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = VintageColors.LossRed.copy(alpha = 0.3f),
                                            selectedLabelColor = VintageColors.LossRed
                                        )
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = uiState.selectedLogCategory == com.miwealth.sovereignvantage.core.utils.SystemLogger.Category.INIT,
                                        onClick = { viewModel.filterLogs(com.miwealth.sovereignvantage.core.utils.SystemLogger.Category.INIT) },
                                        label = { Text("INIT", fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = VintageColors.EmeraldAccent.copy(alpha = 0.3f),
                                            selectedLabelColor = VintageColors.EmeraldAccent
                                        )
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = uiState.selectedLogCategory == com.miwealth.sovereignvantage.core.utils.SystemLogger.Category.SYSTEM,
                                        onClick = { viewModel.filterLogs(com.miwealth.sovereignvantage.core.utils.SystemLogger.Category.SYSTEM) },
                                        label = { Text("SYSTEM", fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = VintageColors.TextSecondary.copy(alpha = 0.3f),
                                            selectedLabelColor = VintageColors.TextSecondary
                                        )
                                    )
                                }
                            }
                            
                            // Log entries (scrollable)
                            Surface(
                                color = Color.Black.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp)
                            ) {
                                if (uiState.logs.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "No logs to display",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = VintageColors.TextMuted
                                        )
                                    }
                                } else {
                                    // BUILD #345: Removed SelectionContainer - caused crashes when logs updated during text selection
                                    LazyColumn(
                                        modifier = Modifier.padding(8.dp),
                                        reverseLayout = false // Newest at bottom
                                    ) {
                                        items(uiState.logs) { log ->
                                            Text(
                                                log,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                    fontSize = 11.sp
                                                ),
                                                color = when {
                                                    log.contains("[E]") -> VintageColors.LossRed
                                                    log.contains("[W]") -> Color(0xFFFF9800)
                                                    log.contains("[TRADE]") -> VintageColors.ProfitGreen
                                                    log.contains("[INIT]") -> VintageColors.EmeraldAccent
                                                    else -> VintageColors.TextSecondary
                                                },
                                                modifier = Modifier.padding(vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            
                            // BUILD #352: Copy All Logs button + count footer
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${uiState.logs.size} entries (max 500 recent)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VintageColors.TextMuted
                                )
                                
                                // Copy all logs to clipboard
                                Button(
                                    onClick = { viewModel.copyAllLogsToClipboard() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = VintageColors.EmeraldAccent
                                    ),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(
                                        "COPY ALL",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Live Prices Header
            item {
                Text(
                    "Live Market Prices",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = VintageColors.TextPrimary
                )
            }
            
            // Live Price Tiles
            item {
                if (uiState.markets.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.markets) { market ->
                            SimplePriceTile(market = market, currencyFormat = currencyFormat)
                        }
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = VintageColors.EmeraldMedium)
                    ) {
                        Text(
                            "Loading market data...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VintageColors.TextSecondary,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            
            // Debug Info (removable later)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = VintageColors.EmeraldDark.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "🔧 DEBUG INFO",
                            style = MaterialTheme.typography.labelMedium,
                            color = VintageColors.TextMuted,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Initialization: ${uiState.initializationState}",
                            style = MaterialTheme.typography.bodySmall,
                            color = VintageColors.TextMuted
                        )
                        Text(
                            "Trading Mode: ${uiState.tradingMode}",
                            style = MaterialTheme.typography.bodySmall,
                            color = VintageColors.TextMuted
                        )
                        Text(
                            "Markets Loaded: ${uiState.markets.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = VintageColors.TextMuted
                        )
                        if (uiState.markets.isNotEmpty()) {
                            Text(
                                "Symbols: ${uiState.markets.joinToString { it.symbol }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = VintageColors.TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SimplePriceTile(market: MarketData, currencyFormat: NumberFormat) {
    Card(
        colors = CardDefaults.cardColors(containerColor = VintageColors.EmeraldMedium),
        modifier = Modifier
            .width(160.dp)
            .border(0.5.dp, VintageColors.GoldDark.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Symbol and Name
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        market.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = VintageColors.Gold
                    )
                    Text(
                        market.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = VintageColors.TextSecondary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Price
            Text(
                if (market.price > 0.0) {
                    currencyFormat.format(market.price)
                } else {
                    "Loading..."
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = VintageColors.TextPrimary
            )
            
            // 24h Change
            if (market.change24h != 0.0) {
                Text(
                    "${if (market.change24h >= 0) "+" else ""}${String.format("%.2f", market.change24h)}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (market.change24h >= 0) VintageColors.ProfitGreen else VintageColors.LossRed,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
