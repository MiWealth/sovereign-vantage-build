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
            .padding(16.dp)
    ) {
        // Header
        Text(
            "SOVEREIGN VANTAGE",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = VintageColors.Gold,
            modifier = Modifier.padding(bottom = 8.dp)
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
