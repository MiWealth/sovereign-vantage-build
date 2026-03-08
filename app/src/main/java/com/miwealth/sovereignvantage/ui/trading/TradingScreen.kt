package com.miwealth.sovereignvantage.ui.trading

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.miwealth.sovereignvantage.ui.components.EmergencyKillSwitchButton
import com.miwealth.sovereignvantage.ui.components.VintageCandlestickChart
import com.miwealth.sovereignvantage.ui.components.ChartTimeframe
import com.miwealth.sovereignvantage.ui.components.generateMockCandles
import com.miwealth.sovereignvantage.ui.theme.*
import com.miwealth.sovereignvantage.core.utils.SystemLogger
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradingScreen(
    onNavigateBack: () -> Unit,
    viewModel: TradingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    
    // BUILD #149: Add snackbar for trade execution feedback
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    // Show error messages
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = "❌ $error",
                    duration = SnackbarDuration.Short
                )
            }
            viewModel.clearError()
        }
    }
    
    // Show order execution results
    LaunchedEffect(uiState.lastOrderResult) {
        uiState.lastOrderResult?.let { result ->
            scope.launch {
                when (result) {
                    is OrderResult.Success -> {
                        snackbarHostState.showSnackbar(
                            message = "✅ ${result.message}",
                            duration = SnackbarDuration.Short
                        )
                    }
                    is OrderResult.Error -> {
                        snackbarHostState.showSnackbar(
                            message = "❌ ${result.message}",
                            duration = SnackbarDuration.Long
                        )
                    }
                }
            }
            viewModel.clearOrderResult()
        }
    }
    
    // BUILD #157: Removed nested Scaffold to fix nav bar obscuring
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VintageColors.EmeraldDeep)
    ) {
        TopAppBar(
                title = { Text("TRADING", fontWeight = FontWeight.Bold, color = VintageColors.Gold, letterSpacing = 1.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = VintageColors.Gold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VintageColors.EmeraldDeep)
            )
        Spacer(modifier = Modifier.fillMaxWidth().height(1.5.dp).background(brush = Brush.horizontalGradient(colors = listOf(Color.Transparent, VintageColors.GoldDark, VintageColors.Gold, VintageColors.GoldDark, Color.Transparent))))
        
        // BUILD #157: Content area (no longer wrapped in Scaffold/Box)
        // TODO: Snackbar functionality will need to be restored via another method
        Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(VintageColors.EmeraldDeep)
            ) {
                // V5.17.0: Testnet safety banner — shown above status bar when active
                if (uiState.isTestnetMode) {
                    com.miwealth.sovereignvantage.ui.dashboard.TestnetBanner()
                }
                
                // NEW: Trading Status Bar - Shows execution mode, portfolio, margin
                TradingStatusBar(uiState = uiState)
                
                // Tab Row
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = VintageColors.EmeraldDark,
                    contentColor = VintageColors.Gold
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Spot", color = if (selectedTab == 0) VintageColors.Gold else VintageColors.TextTertiary, letterSpacing = 1.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Futures", color = if (selectedTab == 1) VintageColors.Gold else VintageColors.TextTertiary, letterSpacing = 1.sp) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("AI Signals", color = if (selectedTab == 2) VintageColors.Gold else VintageColors.TextTertiary, letterSpacing = 1.sp) }
                    )
                }
                
                when (selectedTab) {
                    0 -> SpotTradingContent(uiState, viewModel)
                    1 -> FuturesTradingContent(uiState, viewModel)
                    2 -> AISignalsContent(uiState, viewModel)
                }
            }
            
            // Emergency Kill Switch - Always visible, bottom-right corner
            EmergencyKillSwitchButton(
                onEmergencyStop = { viewModel.emergencyStop() },
                openPositions = uiState.positions.size,
                isActive = uiState.killSwitchActive,
                onReset = { viewModel.resetKillSwitch() },
                cooldownSecondsRemaining = uiState.emergencyStopCooldownSecondsRemaining,  // BUILD #117
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
        }
    }
}

@Composable
fun SpotTradingContent(uiState: TradingUiState, viewModel: TradingViewModel) {
    var amount by remember { mutableStateOf("0.01") }  // BUILD #149: Default amount so button is enabled
    var isBuy by remember { mutableStateOf(true) }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Selected Pair Info
        item {
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, VintageColors.GoldDark, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = VintageColors.EmeraldDark),
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
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Bitcoin / USDT",
                                style = MaterialTheme.typography.bodySmall,
                                color = VintageColors.TextTertiary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "A$${String.format("%,.2f", uiState.currentPrice)}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.priceChange >= 0) VintageColors.ProfitGreen else VintageColors.LossRed
                            )
                            Text(
                                "${if (uiState.priceChange >= 0) "+" else ""}${String.format("%.2f", uiState.priceChange)}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (uiState.priceChange >= 0) VintageColors.ProfitGreen else VintageColors.LossRed
                            )
                        }
                    }
                }
            }
        }
        
        // Candlestick Chart - V5.18.20: Using real data from Binance feed
        item {
            VintageCandlestickChart(
                symbol = uiState.selectedPair,
                candles = uiState.candleData,  // Real data from BinancePublicPriceFeed
                currentPrice = uiState.currentPrice,
                priceChange24h = uiState.priceChange,
                selectedTimeframe = uiState.selectedTimeframe,
                onTimeframeChange = { timeframe -> 
                    viewModel.changeTimeframe(timeframe)
                },
                showVolume = true,
                showGrid = true
            )
        }
        
        // BUILD #149: Coin Selector Dropdown
        item {
            var expanded by remember { mutableStateOf(false) }
            val availableCoins = listOf(
                "BTC/USDT" to "Bitcoin",   // BUILD #152: Changed to USDT to match Binance feed
                "ETH/USDT" to "Ethereum", 
                "SOL/USDT" to "Solana",
                "XRP/USDT" to "Ripple",
                "ADA/USDT" to "Cardano",
                "DOGE/USDT" to "Dogecoin",
                "DOT/USDT" to "Polkadot",
                "MATIC/USDT" to "Polygon"
            )
            
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, VintageColors.Gold, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = VintageColors.EmeraldDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Trading Pair",
                        style = MaterialTheme.typography.labelMedium,
                        color = VintageColors.TextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = availableCoins.find { it.first == uiState.selectedPair }?.let { 
                                "${it.first} (${it.second})" 
                            } ?: uiState.selectedPair,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VintageColors.Gold,
                                unfocusedBorderColor = VintageColors.Gold.copy(alpha = 0.5f),
                                focusedTextColor = VintageColors.TextPrimary,
                                unfocusedTextColor = VintageColors.TextPrimary,
                                focusedContainerColor = VintageColors.EmeraldMedium,
                                unfocusedContainerColor = VintageColors.EmeraldMedium
                            ),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                        )
                        
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.background(VintageColors.EmeraldDeep)
                        ) {
                            availableCoins.forEach { (symbol, name) ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                symbol,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = VintageColors.TextPrimary
                                            )
                                            Text(
                                                name,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = VintageColors.TextSecondary
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.selectPair(symbol)
                                        expanded = false
                                    },
                                    colors = MenuDefaults.itemColors(
                                        textColor = VintageColors.TextPrimary
                                    )
                                )
                            }
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
                    modifier = Modifier.weight(1f).height(48.dp)
                        .border(1.dp, if (isBuy) VintageColors.Gold else VintageColors.GoldDark.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBuy) VintageColors.ProfitGreen else VintageColors.EmeraldMedium,
                        contentColor = VintageColors.TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("BUY", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Button(
                    onClick = { isBuy = false },
                    modifier = Modifier.weight(1f).height(48.dp)
                        .border(1.dp, if (!isBuy) VintageColors.Gold else VintageColors.GoldDark.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isBuy) VintageColors.LossRed else VintageColors.EmeraldMedium,
                        contentColor = VintageColors.TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("SELL", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
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
                    focusedBorderColor = VintageColors.Gold,
                    focusedLabelColor = VintageColors.Gold
                ),
                trailingIcon = {
                    TextButton(onClick = { amount = "0.1" }) {
                        Text("MAX", color = VintageColors.Gold)
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
                    val fraction = percent.replace("%", "").toDouble() / 100.0
                    OutlinedButton(
                        onClick = {
                            val maxBtc = if (uiState.currentPrice > 0) {
                                uiState.availableBalance * fraction / uiState.currentPrice
                            } else 0.0
                            amount = String.format("%.6f", maxBtc)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VintageColors.Gold)
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
                colors = CardDefaults.cardColors(containerColor = VintageColors.EmeraldMedium),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Order Type", color = VintageColors.TextSecondary)
                        Text("Market Order", color = VintageColors.TextPrimary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Est. Total", color = VintageColors.TextSecondary)
                        Text(
                            "A$${String.format("%,.2f", (amount.toDoubleOrNull() ?: 0.0) * uiState.currentPrice)}",
                            color = VintageColors.Gold,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Fee (0.1%)", color = VintageColors.TextSecondary)
                        Text(
                            "A$${String.format("%.2f", (amount.toDoubleOrNull() ?: 0.0) * uiState.currentPrice * 0.001)}",
                            color = VintageColors.TextPrimary
                        )
                    }
                }
            }
        }
        
        // Execute Button
        item {
            // BUILD #151: Add logging
            val isEnabled = amount.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0
            val scope = rememberCoroutineScope()
            
            Button(
                onClick = { 
                    // BUILD #151: Aggressive logging
                    SystemLogger.i("TradingScreen", "🎯 BUY BUTTON CLICKED!")
                    SystemLogger.i("TradingScreen", "   amount=$amount")
                    SystemLogger.i("TradingScreen", "   isBuy=$isBuy")
                    SystemLogger.i("TradingScreen", "   isEnabled=$isEnabled")
                    SystemLogger.i("TradingScreen", "   Calling viewModel.executeTrade()")
                    
                    viewModel.executeTrade(isBuy, amount.toDoubleOrNull() ?: 0.0)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
                    .border(1.5.dp, VintageColors.Gold, RoundedCornerShape(12.dp)),
                enabled = true,  // BUILD #151: Force enabled for diagnostic
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isBuy) VintageColors.ProfitGreen else VintageColors.LossRed
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                val coinSymbol = uiState.selectedPair.substringBefore("/")  // BUILD #152: Extract BTC from BTC/USDT
                Text(
                    if (isBuy) "BUY $coinSymbol" else "SELL $coinSymbol",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
        }
        
        // STAHL Stair Stop Info
        item {
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, VintageColors.Gold, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = VintageColors.Gold.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = VintageColors.Gold,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "STAHL Stair Stop™ Active",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = VintageColors.Gold
                        )
                        Text(
                            "Profit protection enabled at 12 progressive levels",
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
fun FuturesTradingContent(uiState: TradingUiState, viewModel: TradingViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = VintageColors.EmeraldDark),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.TrendingUp,
                        contentDescription = null,
                        tint = VintageColors.Gold,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Futures Trading",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Up to 125x leverage with STAHL protection",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VintageColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Current Leverage: ${uiState.leverage}x",
                        style = MaterialTheme.typography.titleMedium,
                        color = VintageColors.Gold
                    )
                }
            }
        }
        
        // Leverage Slider would go here
        item {
            Text(
                "Leverage Selection",
                style = MaterialTheme.typography.titleSmall,
                color = VintageColors.TextPrimary
            )
        }
    }
}

@Composable
fun AISignalsContent(uiState: TradingUiState, viewModel: TradingViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "AI TRADING SIGNALS",
                color = VintageColors.Gold,
                letterSpacing = 1.sp,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Powered by Arthur AI Board",
                style = MaterialTheme.typography.bodySmall,
                color = VintageColors.Gold
            )
        }
        
        items(uiState.signals) { signal ->
            SignalCard(signal = signal, onExecute = { viewModel.executeSignal(signal) })
        }
    }
}

@Composable
fun SignalCard(signal: TradingSignal, onExecute: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth().border(0.5.dp, VintageColors.GoldDark.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = VintageColors.EmeraldDark),
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
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        signal.action.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (signal.action == "long") VintageColors.ProfitGreen else VintageColors.LossRed,
                        modifier = Modifier
                            .background(
                                if (signal.action == "long") VintageColors.ProfitGreen.copy(alpha = 0.2f)
                                else VintageColors.LossRed.copy(alpha = 0.2f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text(
                    "${signal.confidence}% confidence",
                    style = MaterialTheme.typography.bodySmall,
                    color = VintageColors.Gold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Entry", style = MaterialTheme.typography.bodySmall, color = VintageColors.TextTertiary)
                    Text("$${signal.entry}", color = VintageColors.TextPrimary)
                }
                Column {
                    Text("Target", style = MaterialTheme.typography.bodySmall, color = VintageColors.TextTertiary)
                    Text("$${signal.target}", color = VintageColors.ProfitGreen)
                }
                Column {
                    Text("Stop", style = MaterialTheme.typography.bodySmall, color = VintageColors.TextTertiary)
                    Text("$${signal.stop}", color = VintageColors.LossRed)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onExecute,
                modifier = Modifier.fillMaxWidth()
                    .border(1.dp, VintageColors.GoldDark, RoundedCornerShape(8.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = VintageColors.Gold, contentColor = VintageColors.EmeraldDeep),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("EXECUTE SIGNAL", fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
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
        colors = CardDefaults.cardColors(containerColor = VintageColors.EmeraldDark),
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
                        color = VintageColors.TextSecondary
                    )
                    Text(
                        text = "A$${String.format("%,.2f", uiState.portfolioValue)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
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
                        color = VintageColors.TextSecondary
                    )
                    val pnlColor = if (uiState.dailyPnl >= 0) VintageColors.ProfitGreen else VintageColors.LossRed
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
                        MarginLevelUi.CRITICAL -> VintageColors.LossRed
                        MarginLevelUi.MARGIN_CALL -> VintageColors.GoldDark // Orange
                        MarginLevelUi.WARNING -> VintageColors.Gold
                        else -> VintageColors.TextPrimary
                    }
                )
            }
            
            // V5.17.0: ML Health Indicator Row
            Spacer(modifier = Modifier.height(4.dp))
            MLHealthIndicator(
                status = uiState.mlHealthStatus,
                rollbackCount = uiState.mlRollbackCount
            )
            
            // V5.17.0: Board Disagreement Indicator Row
            // V5.17.0: Now shows effective position size (board regime × disagreement)
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
            VintageColors.GoldDark.copy(alpha = 0.2f), // Amber tint
            VintageColors.GoldDark,
            "🧪 TESTNET"
        )
        isPaper -> Triple(
            VintageColors.TextSecondary.copy(alpha = 0.2f),
            VintageColors.TextSecondary,
            "📝 PAPER"
        )
        mode == "LIVE_AI" -> Triple(
            VintageColors.ProfitGreen.copy(alpha = 0.2f),
            VintageColors.ProfitGreen,
            "🤖 LIVE AI"
        )
        else -> Triple(
            VintageColors.Gold.copy(alpha = 0.2f),
            VintageColors.Gold,
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
        MarginLevelUi.HEALTHY -> Pair(VintageColors.ProfitGreen, "✓")
        MarginLevelUi.WARNING -> Pair(VintageColors.Gold, "⚠")
        MarginLevelUi.MARGIN_CALL -> Pair(VintageColors.GoldDark, "⚠")
        MarginLevelUi.CRITICAL -> Pair(VintageColors.LossRed, "🚨")
    }
    
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Margin: ",
            style = MaterialTheme.typography.bodySmall,
            color = VintageColors.TextSecondary
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
// V5.17.0: ML HEALTH INDICATOR
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
            VintageColors.LossRed,
            "🚨",
            "ML: Rolled back${if (rollbackCount > 0) " (${rollbackCount}x)" else ""}"
        )
        "WARNING" -> Triple(
            VintageColors.Gold,
            "⚠️",
            "ML: Degraded"
        )
        else -> Triple(
            VintageColors.ProfitGreen,
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
 * V5.17.0: Displays board model agreement status.
 * Shows position size scaling when models disagree.
 */
@Composable
fun DisagreementIndicator(
    level: String,
    multiplier: Double,
    effectiveMultiplier: Double = multiplier  // V5.17.0: board × disagreement combined
) {
    val (icon, statusText, color) = when (level) {
        "EXTREME_DISAGREEMENT" -> Triple("🔴", "Chaos", VintageColors.LossRed)
        "HIGH_DISAGREEMENT" -> Triple("🟠", "Conflict", VintageColors.GoldDark)
        "MODERATE_DISAGREEMENT" -> Triple("🟡", "Mixed", VintageColors.Gold)
        "MILD_DISAGREEMENT" -> Triple("🟢", "Minor", VintageColors.EmeraldAccent)
        else -> Triple("🟢", "Aligned", VintageColors.ProfitGreen)
    }
    
    // V5.17.0: Show effective position size (board regime × disagreement combined)
    val effectivePct = (effectiveMultiplier * 100).toInt()
    val sizeColor = when {
        effectivePct < 30 -> VintageColors.LossRed
        effectivePct < 60 -> VintageColors.GoldDark
        effectivePct < 85 -> VintageColors.Gold
        else -> VintageColors.ProfitGreen
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
