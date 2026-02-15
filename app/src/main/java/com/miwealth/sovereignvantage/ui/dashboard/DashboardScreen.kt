package com.miwealth.sovereignvantage.ui.dashboard

import androidx.compose.foundation.background
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.miwealth.sovereignvantage.ui.components.EmergencyKillSwitchCard
import com.miwealth.sovereignvantage.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToTrading: () -> Unit,
    onNavigateToPortfolio: () -> Unit,
    onNavigateToEducation: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showTestnetDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Sovereign Vantage",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (uiState.paperTradingMode) {
                                Surface(
                                    color = Color(0xFF4CAF50).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        "PAPER TRADING",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF4CAF50),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                if (uiState.pqcSecurityEnabled) "PQC Secured" else "DEMO",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (uiState.pqcSecurityEnabled) GoldPrimary else White.copy(alpha = 0.5f)
                            )
                        }
                    }
                },
                actions = {
                    // Kill switch indicator
                    if (uiState.killSwitchActive) {
                        IconButton(onClick = { /* Show kill switch dialog */ }) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Kill Switch Active",
                                tint = Color.Red
                            )
                        }
                    }
                    IconButton(onClick = { /* Notifications */ }) {
                        Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavyDark
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = NavyMedium
            ) {
                NavigationBarItem(
                    selected = true,
                    onClick = { },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                    label = { Text("Dashboard") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = GoldPrimary,
                        selectedTextColor = GoldPrimary,
                        indicatorColor = GoldPrimary.copy(alpha = 0.2f)
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToTrading,
                    icon = { Icon(Icons.Default.TrendingUp, contentDescription = null) },
                    label = { Text("Trading") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToPortfolio,
                    icon = { Icon(Icons.Default.AccountBalance, contentDescription = null) },
                    label = { Text("Portfolio") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToEducation,
                    icon = { Icon(Icons.Default.School, contentDescription = null) },
                    label = { Text("Learn") }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(NavyDark)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // V5.6.0: Testnet safety banner — full-width amber warning
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
                    onReset = { viewModel.resetKillSwitch() }
                )
            }
            
            // Market Overview
            item {
                Text(
                    "Market Overview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = White
                )
            }
            
            item {
                MarketOverviewRow(markets = uiState.markets)
            }
            
            // Recent Trades
            item {
                Text(
                    "Recent Trades",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = White
                )
            }
            
            items(uiState.recentTrades) { trade ->
                TradeCard(trade = trade)
            }
        }
        }  // V5.6.0: Close Column wrapper (TestnetBanner + LazyColumn)
        
        // Loading overlay
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NavyDark.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = GoldPrimary
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
                        Text("DISMISS", color = GoldPrimary)
                    }
                }
            ) {
                Text(error)
            }
        }
    }
    
    // V5.6.0: Testnet launch dialog
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
}

@Composable
fun PortfolioValueCard(
    totalValue: Double,
    dailyChange: Double,
    dailyChangePercent: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = NavyMedium
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            GoldPrimary.copy(alpha = 0.1f),
                            GoldDark.copy(alpha = 0.05f)
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Column {
                Text(
                    "Total Portfolio Value",
                    style = MaterialTheme.typography.bodyMedium,
                    color = White.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "$${String.format("%,.2f", totalValue)}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = GoldPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isPositive = dailyChange >= 0
                    Icon(
                        if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                        contentDescription = null,
                        tint = if (isPositive) ProfitGreen else LossRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "${if (isPositive) "+" else ""}$${String.format("%,.2f", dailyChange)} (${String.format("%.2f", dailyChangePercent)}%)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isPositive) ProfitGreen else LossRed
                    )
                    Text(
                        " today",
                        style = MaterialTheme.typography.bodyMedium,
                        color = White.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
fun QuickActionsRow(
    onTradeClick: () -> Unit,
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
            onClick = { }
        )
        if (!isTestnetMode) {
            // Show testnet launch when not already in testnet
            QuickActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Science,
                label = "Testnet",
                onClick = onTestnetClick,
                tint = Color(0xFFFF9800)
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
    tint: Color = GoldPrimary
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = NavyLight),
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
                color = White
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
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NavyMedium),
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
                    .background(if (isActive) ProfitGreen.copy(alpha = 0.2f) else LossRed.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = if (isActive) ProfitGreen else LossRed,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "AI Trading Engine",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = White
                )
                Text(
                    if (isActive) "Active • $activeStrategies strategies running" else "Inactive",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) ProfitGreen else LossRed
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "$todayTrades",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = GoldPrimary
                )
                Text(
                    "trades today",
                    style = MaterialTheme.typography.bodySmall,
                    color = White.copy(alpha = 0.5f)
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
        modifier = Modifier.width(140.dp),
        colors = CardDefaults.cardColors(containerColor = NavyLight),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                market.symbol,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = White
            )
            Text(
                market.name,
                style = MaterialTheme.typography.bodySmall,
                color = White.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "$${String.format("%,.2f", market.price)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = White
            )
            Text(
                "${if (market.change24h >= 0) "+" else ""}${String.format("%.2f", market.change24h)}%",
                style = MaterialTheme.typography.bodySmall,
                color = if (market.change24h >= 0) ProfitGreen else LossRed
            )
        }
    }
}

@Composable
fun TradeCard(trade: TradeData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NavyLight),
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
                        fontWeight = FontWeight.Bold,
                        color = White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        trade.type.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (trade.type == "buy") ProfitGreen else LossRed,
                        modifier = Modifier
                            .background(
                                if (trade.type == "buy") ProfitGreen.copy(alpha = 0.2f) 
                                else LossRed.copy(alpha = 0.2f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    trade.timeAgo,
                    style = MaterialTheme.typography.bodySmall,
                    color = White.copy(alpha = 0.5f)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${if (trade.profit >= 0) "+" else ""}$${String.format("%.2f", trade.profit)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (trade.profit >= 0) ProfitGreen else LossRed
                )
                Text(
                    "${trade.amount} @ $${String.format("%.2f", trade.price)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = White.copy(alpha = 0.5f)
                )
            }
        }
    }
}

// =============================================================================
// V5.6.0: TESTNET SAFETY BANNER
// =============================================================================

/**
 * V5.6.0: Full-width amber warning banner shown when testnet mode is active.
 * Clearly communicates to the user that trades execute against sandbox endpoints.
 * Reusable across DashboardScreen and TradingScreen.
 */
@Composable
fun TestnetBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFFFF9800).copy(alpha = 0.15f)
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
                tint = Color(0xFFFF9800),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "TESTNET MODE — Sandbox endpoints, no real funds at risk",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF9800)
            )
        }
    }
}

// =============================================================================
// V5.6.0: TESTNET LAUNCH DIALOG
// =============================================================================

/**
 * V5.6.0: Dialog for configuring and launching testnet trading mode.
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
        containerColor = NavyMedium,
        titleContentColor = White,
        textContentColor = White,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Science,
                    contentDescription = null,
                    tint = Color(0xFFFF9800),
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
                    color = White.copy(alpha = 0.7f)
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
                            focusedTextColor = White,
                            unfocusedTextColor = White,
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = White.copy(alpha = 0.3f),
                            focusedLabelColor = GoldPrimary,
                            unfocusedLabelColor = White.copy(alpha = 0.5f)
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
                        focusedTextColor = White,
                        unfocusedTextColor = White,
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = White.copy(alpha = 0.3f),
                        focusedLabelColor = GoldPrimary,
                        unfocusedLabelColor = White.copy(alpha = 0.5f)
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
                        focusedTextColor = White,
                        unfocusedTextColor = White,
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = White.copy(alpha = 0.3f),
                        focusedLabelColor = GoldPrimary,
                        unfocusedLabelColor = White.copy(alpha = 0.5f)
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
                            focusedTextColor = White,
                            unfocusedTextColor = White,
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = White.copy(alpha = 0.3f),
                            focusedLabelColor = GoldPrimary,
                            unfocusedLabelColor = White.copy(alpha = 0.5f)
                        )
                    )
                }
                
                // Warning note
                Surface(
                    color = Color(0xFFFF9800).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Use testnet API keys only. Never enter production keys here.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFF9800)
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
                    containerColor = Color(0xFFFF9800),
                    contentColor = NavyDark
                )
            ) {
                Icon(
                    Icons.Default.Science,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Launch", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = White.copy(alpha = 0.7f))
            }
        }
    )
}
