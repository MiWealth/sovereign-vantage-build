package com.miwealth.sovereignvantage.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.miwealth.sovereignvantage.ui.components.EmergencyKillSwitchCard
import com.miwealth.sovereignvantage.ui.components.VintageCandlestickChart
import com.miwealth.sovereignvantage.ui.components.ChartTimeframe
import com.miwealth.sovereignvantage.ui.components.generateMockCandles
import com.miwealth.sovereignvantage.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToTrading: () -> Unit,
    onNavigateToAIBoard: () -> Unit,  // BUILD #110: The Octagon
    onNavigateToWallet: () -> Unit,
    onNavigateToPortfolio: () -> Unit,
    onNavigateToEducation: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showTestnetDialog by remember { mutableStateOf(false) }
    var showKillSwitchInfo by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }
    
    Scaffold(
        containerColor = VintageColors.EmeraldDeep,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // BUILD #126: Make brain emoji clickable
                                Box(
                                    modifier = Modifier
                                        .clickable(onClick = onNavigateToAIBoard)
                                        .padding(end = 8.dp)
                                ) {
                                    Text(
                                        "🧠",
                                        fontSize = 28.sp
                                    )
                                }
                                Text(
                                    "SOVEREIGN VANTAGE",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = VintageColors.Gold,
                                    letterSpacing = 2.sp
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (uiState.paperTradingMode) {
                                    Surface(
                                        color = VintageColors.EmeraldAccent.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            "PAPER TRADING",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = VintageColors.EmeraldAccent,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    if (uiState.pqcSecurityEnabled) "PQC Secured" else "DEMO",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (uiState.pqcSecurityEnabled) VintageColors.Gold else VintageColors.TextTertiary
                                )
                        }
                    }
                },
                actions = {
                    // Kill switch indicator
                    if (uiState.killSwitchActive) {
                        IconButton(onClick = { showKillSwitchInfo = true }) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Kill Switch Active",
                                tint = Color.Red
                            )
                        }
                    }
                    IconButton(onClick = { showNotifications = true }) {
                        Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VintageColors.EmeraldDeep
                )
                )
                // Gold accent line
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.5.dp)
                        .background(
                            brush = Brush.horizontalGradient(
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
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(VintageColors.EmeraldDeep)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // V5.17.0: Testnet safety banner — full-width amber warning
                if (uiState.isTestnetMode) {
                    TestnetBanner()
                }
                
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                // Portfolio Value Card
                item {
                    PortfolioValueCard(
                        totalValue = uiState.totalPortfolioValue,
                        dailyChange = uiState.dailyChange,
                        dailyChangePercent = uiState.dailyChangePercent
                    )
                }
            
            // Quick Actions
            item {
                QuickActionsRow(
                    onTradeClick = onNavigateToTrading,
                    onWalletClick = onNavigateToWallet,
                    onPortfolioClick = onNavigateToPortfolio,
                    onTestnetClick = { showTestnetDialog = true },
                    isTestnetMode = uiState.isTestnetMode
                )
            }
            
            // AI Status Card
            item {
                AIStatusCard(
                    isActive = uiState.aiTradingActive,
                    activeStrategies = uiState.activeStrategies,
                    todayTrades = uiState.todayTrades
                )
            }
            
            // Emergency Kill Switch - Quick access for safety
            item {
                EmergencyKillSwitchCard(
                    onEmergencyStop = { viewModel.activateKillSwitch() },
                    openPositions = uiState.activePositions,
                    isActive = uiState.killSwitchActive,
                    onReset = { viewModel.resetKillSwitch() },
                    cooldownSecondsRemaining = uiState.emergencyStopCooldownSecondsRemaining  // BUILD #117
                )
            }
            
            // Market Overview
            item {
                Text(
                    "Market Overview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            item {
                MarketOverviewRow(markets = uiState.markets)
            }
            
            // Candlestick Chart — BTC primary view
            item {
                val mockCandles = remember { generateMockCandles(60, 104000.0) }
                var selectedTimeframe by remember { mutableStateOf(ChartTimeframe.H4) }
                
                VintageCandlestickChart(
                    symbol = "BTC/USD",
                    candles = mockCandles,
                    currentPrice = 104250.0,
                    priceChange24h = 2.34,
                    selectedTimeframe = selectedTimeframe,
                    onTimeframeChange = { selectedTimeframe = it },
                    showVolume = true,
                    showGrid = true
                )
            }
            
            // Recent Trades
            item {
                Text(
                    "Recent Trades",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            items(uiState.recentTrades) { trade ->
                TradeCard(trade = trade)
            }
        }
        }  // V5.17.0: Close Column wrapper (TestnetBanner + LazyColumn)
        
        // Loading overlay
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(VintageColors.EmeraldDeep.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = VintageColors.Gold
                )
            }
        }
        
        // Error snackbar
        uiState.error?.let { error ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("DISMISS", color = VintageColors.Gold)
                    }
                }
            ) {
                Text(error)
            }
        }
    }
    }  // V5.17.0: Close Scaffold content lambda
    
    // V5.17.0: Testnet launch dialog
    if (showTestnetDialog) {
        TestnetLaunchDialog(
            onDismiss = { showTestnetDialog = false },
            onLaunch = { exchangeId, apiKey, apiSecret, passphrase ->
                viewModel.launchTestnetTrading(
                    exchangeId = exchangeId,
                    credentials = com.miwealth.sovereignvantage.core.exchange.ExchangeCredentials(
                        exchangeId = exchangeId,
                        apiKey = apiKey,
                        apiSecret = apiSecret,
                        passphrase = passphrase.ifBlank { null },
                        isTestnet = true
                    )
                )
                showTestnetDialog = false
            }
        )
    }
    
    // Kill Switch info dialog
    if (showKillSwitchInfo) {
        AlertDialog(
            onDismissRequest = { showKillSwitchInfo = false },
            title = { Text("Kill Switch Active", color = Color.Red, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Emergency kill switch has been triggered.",
                        color = VintageColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "All trading has been halted. ${uiState.activePositions} open positions remain.",
                        color = VintageColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Max drawdown limit reached. Review positions before resuming.",
                        color = VintageColors.Gold,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetKillSwitch()
                    showKillSwitchInfo = false
                }) {
                    Text("Reset & Resume", color = VintageColors.Gold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showKillSwitchInfo = false }) {
                    Text("Dismiss", color = VintageColors.TextTertiary)
                }
            },
            containerColor = VintageColors.EmeraldDark
        )
    }
    
    // Notifications dialog
    if (showNotifications) {
        AlertDialog(
            onDismissRequest = { showNotifications = false },
            title = { Text("Notifications", color = VintageColors.Gold, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "No new notifications",
                        color = VintageColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Trade alerts, AI Board decisions, and system events will appear here.",
                        color = VintageColors.TextTertiary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showNotifications = false }) {
                    Text("OK", color = VintageColors.Gold)
                }
            },
            containerColor = VintageColors.EmeraldDark
        )
    }
}

@Composable
fun PortfolioValueCard(
    totalValue: Double,
    dailyChange: Double,
    dailyChangePercent: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, VintageColors.GoldDark, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = VintageColors.EmeraldDark
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            VintageColors.Gold.copy(alpha = 0.12f),
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
                    "A$${String.format("%,.2f", totalValue)}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = VintageColors.Gold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isPositive = dailyChange >= 0
                    Icon(
                        if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                        contentDescription = null,
                        tint = if (isPositive) VintageColors.ProfitGreen else VintageColors.LossRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "${if (isPositive) "+" else ""}A$${String.format("%,.2f", dailyChange)} (${String.format("%.2f", dailyChangePercent)}%)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isPositive) VintageColors.ProfitGreen else VintageColors.LossRed
                    )
                    Text(
                        " today",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VintageColors.TextTertiary
                    )
                }
            }
        }
    }
}

@Composable
fun QuickActionsRow(
    onTradeClick: () -> Unit,
    onWalletClick: () -> Unit,
    onPortfolioClick: () -> Unit,
    onTestnetClick: () -> Unit = {},
    isTestnetMode: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickActionButton(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.SwapHoriz,
            label = "Trade",
            onClick = onTradeClick
        )
        QuickActionButton(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.AccountBalanceWallet,
            label = "Wallet",
            onClick = onWalletClick
        )
        if (!isTestnetMode) {
            QuickActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Science,
                label = "Testnet",
                onClick = onTestnetClick,
                tint = VintageColors.GoldDark
            )
        } else {
            QuickActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Analytics,
                label = "Analytics",
                onClick = onPortfolioClick
            )
        }
    }
}

@Composable
fun QuickActionButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = VintageColors.Gold
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = VintageColors.EmeraldMedium),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = VintageColors.TextPrimary
            )
        }
    }
}

@Composable
fun AIStatusCard(
    isActive: Boolean,
    activeStrategies: Int,
    todayTrades: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, VintageColors.GoldDark.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = VintageColors.EmeraldDark),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isActive) VintageColors.ProfitGreen.copy(alpha = 0.2f) else VintageColors.LossRed.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = if (isActive) VintageColors.ProfitGreen else VintageColors.LossRed,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "AI Trading Engine",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (isActive) "Active • $activeStrategies strategies running" else "Inactive",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) VintageColors.ProfitGreen else VintageColors.LossRed
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "$todayTrades",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = VintageColors.Gold
                )
                Text(
                    "trades today",
                    style = MaterialTheme.typography.bodySmall,
                    color = VintageColors.TextTertiary
                )
            }
        }
    }
}

@Composable
fun MarketOverviewRow(markets: List<MarketData>) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(markets) { market ->
            MarketCard(market = market)
        }
    }
}

@Composable
fun MarketCard(market: MarketData) {
    Card(
        modifier = Modifier.width(140.dp).border(0.5.dp, VintageColors.GoldDark.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = VintageColors.EmeraldMedium),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                market.symbol,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                market.name,
                style = MaterialTheme.typography.bodySmall,
                color = VintageColors.TextTertiary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "A$${String.format("%,.2f", market.price)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "${if (market.change24h >= 0) "+" else ""}${String.format("%.2f", market.change24h)}%",
                style = MaterialTheme.typography.bodySmall,
                color = if (market.change24h >= 0) VintageColors.ProfitGreen else VintageColors.LossRed
            )
        }
    }
}

@Composable
fun TradeCard(trade: TradeData) {
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
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        trade.symbol,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        trade.type.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (trade.type == "buy") VintageColors.ProfitGreen else VintageColors.LossRed,
                        modifier = Modifier
                            .background(
                                if (trade.type == "buy") VintageColors.ProfitGreen.copy(alpha = 0.2f) 
                                else VintageColors.LossRed.copy(alpha = 0.2f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    trade.timeAgo,
                    style = MaterialTheme.typography.bodySmall,
                    color = VintageColors.TextTertiary
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${if (trade.profit >= 0) "+" else ""}$${String.format("%.2f", trade.profit)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (trade.profit >= 0) VintageColors.ProfitGreen else VintageColors.LossRed
                )
                Text(
                    "${trade.amount} @ $${String.format("%.2f", trade.price)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = VintageColors.TextTertiary
                )
            }
        }
    }
}

// =============================================================================
// V5.17.0: TESTNET SAFETY BANNER
// =============================================================================

/**
 * V5.17.0: Full-width amber warning banner shown when testnet mode is active.
 * Clearly communicates to the user that trades execute against sandbox endpoints.
 * Reusable across DashboardScreen and TradingScreen.
 */
@Composable
fun TestnetBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = VintageColors.GoldDark.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = "Testnet Mode",
                tint = VintageColors.GoldDark,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "TESTNET MODE — Sandbox endpoints, no real funds at risk",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = VintageColors.GoldDark
            )
        }
    }
}

// =============================================================================
// V5.17.0: TESTNET LAUNCH DIALOG
// =============================================================================

/**
 * V5.17.0: Dialog for configuring and launching testnet trading mode.
 * Collects exchange selection and testnet API credentials, then triggers
 * DashboardViewModel.launchTestnetTrading().
 * 
 * Supported testnet exchanges: Binance, Bybit, OKX.
 * Gate.io has no testnet — requires allowProductionFallback (not offered here).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestnetLaunchDialog(
    onDismiss: () -> Unit,
    onLaunch: (exchangeId: String, apiKey: String, apiSecret: String, passphrase: String) -> Unit
) {
    var selectedExchange by remember { mutableStateOf("binance") }
    var apiKey by remember { mutableStateOf("") }
    var apiSecret by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    
    val exchanges = listOf(
        "binance" to "Binance Testnet",
        "bybit" to "Bybit Testnet",
        "okx" to "OKX Demo"
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VintageColors.EmeraldDark,
        titleContentColor = VintageColors.TextPrimary,
        textContentColor = VintageColors.TextPrimary,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Science,
                    contentDescription = null,
                    tint = VintageColors.GoldDark,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Launch Testnet", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Connect to a sandbox exchange environment. No real funds at risk.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VintageColors.TextSecondary
                )
                
                // Exchange selector
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = exchanges.first { it.first == selectedExchange }.second,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Exchange") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = VintageColors.TextPrimary,
                            unfocusedTextColor = VintageColors.TextPrimary,
                            focusedBorderColor = VintageColors.Gold,
                            unfocusedBorderColor = VintageColors.TextMuted,
                            focusedLabelColor = VintageColors.Gold,
                            unfocusedLabelColor = VintageColors.TextPrimary.copy(alpha = 0.5f)
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        exchanges.forEach { (id, name) ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    selectedExchange = id
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                // API Key
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = VintageColors.TextPrimary,
                        unfocusedTextColor = VintageColors.TextPrimary,
                        focusedBorderColor = VintageColors.Gold,
                        unfocusedBorderColor = VintageColors.TextMuted,
                        focusedLabelColor = VintageColors.Gold,
                        unfocusedLabelColor = VintageColors.TextPrimary.copy(alpha = 0.5f)
                    )
                )
                
                // API Secret
                OutlinedTextField(
                    value = apiSecret,
                    onValueChange = { apiSecret = it },
                    label = { Text("API Secret") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = VintageColors.TextPrimary,
                        unfocusedTextColor = VintageColors.TextPrimary,
                        focusedBorderColor = VintageColors.Gold,
                        unfocusedBorderColor = VintageColors.TextMuted,
                        focusedLabelColor = VintageColors.Gold,
                        unfocusedLabelColor = VintageColors.TextPrimary.copy(alpha = 0.5f)
                    )
                )
                
                // Passphrase (OKX requires it)
                if (selectedExchange == "okx") {
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Passphrase") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = VintageColors.TextPrimary,
                            unfocusedTextColor = VintageColors.TextPrimary,
                            focusedBorderColor = VintageColors.Gold,
                            unfocusedBorderColor = VintageColors.TextMuted,
                            focusedLabelColor = VintageColors.Gold,
                            unfocusedLabelColor = VintageColors.TextPrimary.copy(alpha = 0.5f)
                        )
                    )
                }
                
                // Warning note
                Surface(
                    color = VintageColors.GoldDark.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = VintageColors.GoldDark,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Use testnet API keys only. Never enter production keys here.",
                            style = MaterialTheme.typography.labelSmall,
                            color = VintageColors.GoldDark
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onLaunch(selectedExchange, apiKey, apiSecret, passphrase) },
                enabled = apiKey.isNotBlank() && apiSecret.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VintageColors.Gold,
                    contentColor = VintageColors.EmeraldDeep
                )
            ) {
                Icon(
                    Icons.Default.Science,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("LAUNCH", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = VintageColors.TextSecondary)
            }
        }
    )
}
