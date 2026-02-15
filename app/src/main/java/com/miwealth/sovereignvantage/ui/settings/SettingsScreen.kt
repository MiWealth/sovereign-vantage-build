package com.miwealth.sovereignvantage.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.miwealth.sovereignvantage.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    var showExchangeDialog by remember { mutableStateOf(false) }
    var showPurgeConfirmDialog by remember { mutableStateOf(false) }
    
    // Exchange API dialog
    if (showExchangeDialog) {
        ExchangeApiDialog(
            onDismiss = { showExchangeDialog = false },
            onSave = { exchange, apiKey, apiSecret, passphrase, isTestnet ->
                viewModel.saveExchangeCredentials(
                    exchangeId = exchange,
                    apiKey = apiKey,
                    apiSecret = apiSecret,
                    passphrase = passphrase,
                    isTestnet = isTestnet,
                    testConnection = true
                )
                showExchangeDialog = false
            }
        )
    }
    
    // Purge data confirmation dialog
    if (showPurgeConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showPurgeConfirmDialog = false },
            containerColor = NavyMedium,
            title = {
                Text("Purge All Data?", color = LossRed, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "This will permanently delete all your:\n\n" +
                    "• Exchange API credentials\n" +
                    "• Trading history\n" +
                    "• Settings and preferences\n\n" +
                    "This action cannot be undone.",
                    color = White.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.purgeAllData()
                        showPurgeConfirmDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LossRed)
                ) {
                    Text("PURGE ALL DATA", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPurgeConfirmDialog = false }) {
                    Text("Cancel", color = White.copy(alpha = 0.7f))
                }
            }
        )
    }
    
    // Connection result snackbar
    LaunchedEffect(uiState.connectionResult) {
        if (uiState.connectionResult != null) {
            // Auto-dismiss after 3 seconds
            kotlinx.coroutines.delay(3000)
            viewModel.clearConnectionResult()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyDark)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(NavyDark),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Account Section
            item {
                Text(
                    "Account",
                    style = MaterialTheme.typography.titleSmall,
                    color = GoldPrimary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                SettingsCard {
                    SettingsItem(
                        icon = Icons.Default.Person,
                        title = "Profile",
                        subtitle = "Manage your account details",
                        onClick = { }
                    )
                    HorizontalDivider(color = NavyDark)
                    SettingsItem(
                        icon = Icons.Default.Wallet,
                        title = "Connected Wallets",
                        subtitle = "3 wallets connected",
                        onClick = { }
                    )
                    HorizontalDivider(color = NavyDark)
                    SettingsItem(
                        icon = Icons.Default.Star,
                        title = "Subscription",
                        subtitle = "ELITE Tier - Active",
                        onClick = { }
                    )
                }
            }
            
            // System Status Section - NEW in v5.5.73
            item {
                Text(
                    "System Status",
                    style = MaterialTheme.typography.titleSmall,
                    color = GoldPrimary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                SystemStatusCard(
                    uiState = uiState,
                    onRunDiscovery = { viewModel.runAssetDiscovery() }
                )
            }
            
            // Security Section
            item {
                Text(
                    "Security",
                    style = MaterialTheme.typography.titleSmall,
                    color = GoldPrimary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                SettingsCard {
                    SettingsToggleItem(
                        icon = Icons.Default.Fingerprint,
                        title = "Biometric Login",
                        subtitle = "Use fingerprint or face to login",
                        checked = uiState.biometricEnabled,
                        onCheckedChange = { viewModel.setBiometricEnabled(it) }
                    )
                    HorizontalDivider(color = NavyDark)
                    SettingsItem(
                        icon = Icons.Default.Lock,
                        title = "Change Password",
                        subtitle = "Update your password",
                        onClick = { }
                    )
                    HorizontalDivider(color = NavyDark)
                    SettingsItem(
                        icon = Icons.Default.Security,
                        title = "Two-Factor Auth",
                        subtitle = "Enabled",
                        onClick = { }
                    )
                }
            }
            
            // Trading Section
            item {
                Text(
                    "Trading",
                    style = MaterialTheme.typography.titleSmall,
                    color = GoldPrimary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                SettingsCard {
                    SettingsItem(
                        icon = Icons.Default.Key,
                        title = "Exchange API Keys",
                        subtitle = "Connect to Kraken, Coinbase, Binance...",
                        onClick = { showExchangeDialog = true }
                    )
                    HorizontalDivider(color = NavyDark)
                    SettingsItem(
                        icon = Icons.Default.TrendingUp,
                        title = "Risk Settings",
                        subtitle = "Maximum Aggression",
                        onClick = { }
                    )
                    HorizontalDivider(color = NavyDark)
                    SettingsItem(
                        icon = Icons.Default.Shield,
                        title = "STAHL Stair Stop™",
                        subtitle = "Configure profit protection levels",
                        onClick = { }
                    )
                    HorizontalDivider(color = NavyDark)
                    SettingsItem(
                        icon = Icons.Default.SmartToy,
                        title = "AI Trading Engine",
                        subtitle = "5 strategies active",
                        onClick = { }
                    )
                }
            }
            
            // V5.5.97: Trading Mode & HYBRID Configuration
            item {
                Text(
                    "Operating Mode",
                    style = MaterialTheme.typography.titleSmall,
                    color = GoldPrimary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                TradingModeCard(
                    currentMode = uiState.tradingMode,
                    hybridAutoThreshold = uiState.hybridAutoExecuteThreshold,
                    hybridConfirmBelow = uiState.hybridRequireConfirmationBelow,
                    hybridMaxAutoTrades = uiState.hybridMaxAutoTradesPerHour,
                    hybridValueThreshold = uiState.hybridAbsoluteValueThreshold,
                    onModeChange = { viewModel.setTradingMode(it) },
                    onAutoThresholdChange = { viewModel.setHybridAutoExecuteThreshold(it) },
                    onConfirmBelowChange = { viewModel.setHybridConfirmationThreshold(it) },
                    onMaxAutoTradesChange = { viewModel.setHybridMaxAutoTrades(it) },
                    onValueThresholdChange = { viewModel.setHybridValueThreshold(it) }
                )
            }
            
            // NEW V5.5.74: Advanced Strategies Section
            item {
                Text(
                    "Advanced Strategies",
                    style = MaterialTheme.typography.titleSmall,
                    color = GoldPrimary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                AdvancedStrategiesCard(
                    uiState = uiState,
                    onAlphaScannerEnabledChange = { viewModel.setAlphaScannerEnabled(it) },
                    onFundingArbEnabledChange = { viewModel.setFundingArbEnabled(it) },
                    onAlphaScannerIntervalChange = { viewModel.setAlphaScannerInterval(it) },
                    onFundingArbMinRateChange = { viewModel.setFundingArbMinRate(it) },
                    onFundingArbMaxCapitalChange = { viewModel.setFundingArbMaxCapital(it) },
                    onDailyLossLimitChange = { viewModel.setDailyLossLimit(it) }
                )
            }
            
            // Preferences Section
            item {
                Text(
                    "Preferences",
                    style = MaterialTheme.typography.titleSmall,
                    color = GoldPrimary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                SettingsCard {
                    SettingsToggleItem(
                        icon = Icons.Default.Notifications,
                        title = "Notifications",
                        subtitle = "Trade alerts and updates",
                        checked = uiState.notificationsEnabled,
                        onCheckedChange = { viewModel.setNotificationsEnabled(it) }
                    )
                    HorizontalDivider(color = NavyDark)
                    SettingsToggleItem(
                        icon = Icons.Default.DarkMode,
                        title = "Dark Mode",
                        subtitle = "Use dark theme",
                        checked = uiState.darkModeEnabled,
                        onCheckedChange = { viewModel.setDarkModeEnabled(it) }
                    )
                    HorizontalDivider(color = NavyDark)
                    SettingsItem(
                        icon = Icons.Default.Language,
                        title = "Language",
                        subtitle = "English",
                        onClick = { }
                    )
                }
            }
            
            // Support Section
            item {
                Text(
                    "Support",
                    style = MaterialTheme.typography.titleSmall,
                    color = GoldPrimary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                SettingsCard {
                    SettingsItem(
                        icon = Icons.Default.Help,
                        title = "Help Center",
                        subtitle = "FAQs and guides",
                        onClick = { }
                    )
                    HorizontalDivider(color = NavyDark)
                    SettingsItem(
                        icon = Icons.Default.Email,
                        title = "Contact Support",
                        subtitle = "Get help from our team",
                        onClick = { }
                    )
                    HorizontalDivider(color = NavyDark)
                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = "About",
                        subtitle = "Version 5.5.73 (Arthur Edition)",
                        onClick = { }
                    )
                }
            }
            
            // Logout Button
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LossRed.copy(alpha = 0.2f),
                        contentColor = LossRed
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out", fontWeight = FontWeight.Bold)
                }
            }
            
            // Version Info
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Sovereign Vantage: Arthur Edition\nVersion 5.5.73\n© 2025-2026 MiWealth Pty Ltd",
                    style = MaterialTheme.typography.bodySmall,
                    color = White.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NavyMedium),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(content = content)
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = GoldPrimary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = White
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = White.copy(alpha = 0.5f)
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = White.copy(alpha = 0.3f)
        )
    }
}

@Composable
fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = GoldPrimary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = White
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = White.copy(alpha = 0.5f)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = GoldPrimary,
                checkedTrackColor = GoldPrimary.copy(alpha = 0.3f)
            )
        )
    }
}

// ============================================================================
// SYSTEM STATUS CARD - Pipeline & Discovery Status (v5.5.73)
// ============================================================================

@Composable
fun SystemStatusCard(
    uiState: SettingsUiState,
    onRunDiscovery: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NavyMedium),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Execution Mode Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = GoldPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Execution Mode",
                        style = MaterialTheme.typography.bodyLarge,
                        color = White
                    )
                }
                
                ExecutionModeBadgeSettings(
                    mode = uiState.executionMode,
                    isAI = uiState.isUsingAIIntegration
                )
            }
            
            HorizontalDivider(color = NavyDark)
            
            // Asset Discovery Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = GoldPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Asset Discovery",
                            style = MaterialTheme.typography.bodyLarge,
                            color = White
                        )
                        Text(
                            "Discovered: ${uiState.discoveredAssets} • Registered: ${uiState.registeredAssets}",
                            style = MaterialTheme.typography.bodySmall,
                            color = White.copy(alpha = 0.5f)
                        )
                    }
                }
                
                PipelineStatusBadge(status = uiState.pipelineStatus)
            }
            
            // Progress indicator when running
            if (uiState.isRunningDiscovery) {
                Column {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = GoldPrimary,
                        trackColor = NavyDark
                    )
                    uiState.currentDiscoveryAsset?.let { asset ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Processing: $asset",
                            style = MaterialTheme.typography.bodySmall,
                            color = White.copy(alpha = 0.5f)
                        )
                    }
                }
            }
            
            // Last discovery time
            uiState.lastDiscoveryTime?.let { time ->
                Text(
                    "Last run: $time",
                    style = MaterialTheme.typography.bodySmall,
                    color = White.copy(alpha = 0.4f)
                )
            }
            
            // Run Discovery Button
            Button(
                onClick = onRunDiscovery,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isRunningDiscovery,
                colors = ButtonDefaults.buttonColors(
                    containerColor = GoldPrimary.copy(alpha = 0.2f),
                    contentColor = GoldPrimary,
                    disabledContainerColor = NavyDark,
                    disabledContentColor = White.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (uiState.isRunningDiscovery) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = White.copy(alpha = 0.5f),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Discovering...")
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Run Discovery", fontWeight = FontWeight.Medium)
                }
            }
            
            HorizontalDivider(color = NavyDark)
            
            // Connected Exchanges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Link,
                        contentDescription = null,
                        tint = GoldPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Connected Exchanges",
                            style = MaterialTheme.typography.bodyLarge,
                            color = White
                        )
                        if (uiState.connectedExchangeNames.isNotEmpty()) {
                            Text(
                                uiState.connectedExchangeNames.joinToString(" • ") { it.replaceFirstChar { c -> c.uppercase() } },
                                style = MaterialTheme.typography.bodySmall,
                                color = White.copy(alpha = 0.5f)
                            )
                        } else {
                            Text(
                                "No exchanges connected",
                                style = MaterialTheme.typography.bodySmall,
                                color = White.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
                
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (uiState.connectedExchangeCount > 0) ProfitGreen.copy(alpha = 0.2f) else NavyDark
                ) {
                    Text(
                        text = "${uiState.connectedExchangeCount} active",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (uiState.connectedExchangeCount > 0) ProfitGreen else White.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
fun ExecutionModeBadgeSettings(
    mode: String,
    isAI: Boolean
) {
    val (backgroundColor, textColor, label) = when {
        mode == "PAPER" -> Triple(
            Color(0xFF2196F3).copy(alpha = 0.2f),
            Color(0xFF2196F3),
            "📝 PAPER"
        )
        mode == "LIVE_AI" || isAI -> Triple(
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
fun PipelineStatusBadge(status: PipelineStatusUi) {
    val (backgroundColor, textColor, label) = when (status) {
        PipelineStatusUi.IDLE -> Triple(
            NavyDark,
            White.copy(alpha = 0.5f),
            "Idle"
        )
        PipelineStatusUi.DISCOVERING -> Triple(
            Color(0xFF2196F3).copy(alpha = 0.2f),
            Color(0xFF2196F3),
            "Discovering..."
        )
        PipelineStatusUi.ENRICHING -> Triple(
            Color(0xFF9C27B0).copy(alpha = 0.2f),
            Color(0xFF9C27B0),
            "Enriching..."
        )
        PipelineStatusUi.ASSIGNING -> Triple(
            GoldPrimary.copy(alpha = 0.2f),
            GoldPrimary,
            "Assigning..."
        )
        PipelineStatusUi.REGISTERING -> Triple(
            Color(0xFFFF9800).copy(alpha = 0.2f),
            Color(0xFFFF9800),
            "Registering..."
        )
        PipelineStatusUi.COMPLETE -> Triple(
            ProfitGreen.copy(alpha = 0.2f),
            ProfitGreen,
            "✓ Complete"
        )
        PipelineStatusUi.ERROR -> Triple(
            LossRed.copy(alpha = 0.2f),
            LossRed,
            "✗ Error"
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
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}

// ============================================================================
// EXCHANGE API DIALOG
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExchangeApiDialog(
    onDismiss: () -> Unit,
    onSave: (exchange: String, apiKey: String, apiSecret: String, passphrase: String?, isTestnet: Boolean) -> Unit
) {
    var selectedExchange by remember { mutableStateOf("binance") }
    var apiKey by remember { mutableStateOf("") }
    var apiSecret by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var isTestnet by remember { mutableStateOf(false) }
    var showApiKey by remember { mutableStateOf(false) }
    var showApiSecret by remember { mutableStateOf(false) }
    var showPassphrase by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    
    // Exchanges with testnet indicator
    val exchanges = listOf(
        Triple("binance", "Binance", true),
        Triple("bybit", "Bybit", true),
        Triple("coinbase", "Coinbase", true),
        Triple("kraken", "Kraken", false),
        Triple("okx", "OKX", false),
        Triple("kucoin", "KuCoin", true),
        Triple("gateio", "Gate.io", false),
        Triple("mexc", "MEXC", false),
        Triple("bitget", "Bitget", false),
        Triple("htx", "HTX (Huobi)", false),
        Triple("gemini", "Gemini", true),
        Triple("uphold", "Uphold", true)
    )
    
    // Exchanges requiring passphrase
    val needsPassphrase = selectedExchange in listOf("coinbase", "kucoin")
    val selectedHasTestnet = exchanges.find { it.first == selectedExchange }?.third ?: false
    
    // Reset testnet toggle when switching to exchange without testnet
    LaunchedEffect(selectedExchange) {
        if (!selectedHasTestnet) isTestnet = false
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyMedium,
        title = {
            Text(
                "Connect Exchange",
                color = White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Exchange selector
                Text(
                    "Select Exchange",
                    style = MaterialTheme.typography.labelMedium,
                    color = GoldPrimary
                )
                
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = exchanges.find { it.first == selectedExchange }?.second ?: "",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = White,
                            unfocusedTextColor = White,
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = White.copy(alpha = 0.3f)
                        )
                    )
                    
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        exchanges.forEach { (id, name, hasTestnet) ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(name)
                                        if (hasTestnet) {
                                            Text(
                                                "TESTNET",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF4CAF50),
                                                modifier = Modifier
                                                    .background(
                                                        Color(0xFF4CAF50).copy(alpha = 0.15f),
                                                        RoundedCornerShape(4.dp)
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    selectedExchange = id
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                // Testnet toggle (only shown for exchanges that support it)
                if (selectedHasTestnet) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Testnet / Sandbox",
                                style = MaterialTheme.typography.bodyMedium,
                                color = White
                            )
                            Text(
                                "Use test environment (no real funds)",
                                style = MaterialTheme.typography.bodySmall,
                                color = White.copy(alpha = 0.5f)
                            )
                        }
                        Switch(
                            checked = isTestnet,
                            onCheckedChange = { isTestnet = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = NavyDark,
                                checkedTrackColor = Color(0xFF4CAF50),
                                uncheckedThumbColor = White.copy(alpha = 0.5f),
                                uncheckedTrackColor = White.copy(alpha = 0.1f)
                            )
                        )
                    }
                    
                    if (isTestnet) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "⚠ Testnet mode — orders will execute against the sandbox environment. " +
                                "Testnet API keys are different from production keys.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF4CAF50).copy(alpha = 0.9f),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
                
                // API Key input
                Text(
                    if (isTestnet) "Testnet API Key" else "API Key",
                    style = MaterialTheme.typography.labelMedium,
                    color = GoldPrimary
                )
                
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter your API key", color = White.copy(alpha = 0.3f)) },
                    visualTransformation = if (showApiKey) {
                        androidx.compose.ui.text.input.VisualTransformation.None
                    } else {
                        androidx.compose.ui.text.input.PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showApiKey) "Hide" else "Show",
                                tint = White.copy(alpha = 0.5f)
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = White,
                        unfocusedTextColor = White,
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = White.copy(alpha = 0.3f)
                    ),
                    singleLine = true
                )
                
                // API Secret input
                Text(
                    if (isTestnet) "Testnet API Secret" else "API Secret",
                    style = MaterialTheme.typography.labelMedium,
                    color = GoldPrimary
                )
                
                OutlinedTextField(
                    value = apiSecret,
                    onValueChange = { apiSecret = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter your API secret", color = White.copy(alpha = 0.3f)) },
                    visualTransformation = if (showApiSecret) {
                        androidx.compose.ui.text.input.VisualTransformation.None
                    } else {
                        androidx.compose.ui.text.input.PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { showApiSecret = !showApiSecret }) {
                            Icon(
                                if (showApiSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showApiSecret) "Hide" else "Show",
                                tint = White.copy(alpha = 0.5f)
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = White,
                        unfocusedTextColor = White,
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = White.copy(alpha = 0.3f)
                    ),
                    singleLine = true
                )
                
                // Passphrase input (only for Coinbase, KuCoin)
                if (needsPassphrase) {
                    Text(
                        "API Passphrase",
                        style = MaterialTheme.typography.labelMedium,
                        color = GoldPrimary
                    )
                    
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Enter your API passphrase", color = White.copy(alpha = 0.3f)) },
                        visualTransformation = if (showPassphrase) {
                            androidx.compose.ui.text.input.VisualTransformation.None
                        } else {
                            androidx.compose.ui.text.input.PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { showPassphrase = !showPassphrase }) {
                                Icon(
                                    if (showPassphrase) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPassphrase) "Hide" else "Show",
                                    tint = White.copy(alpha = 0.5f)
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = White,
                            unfocusedTextColor = White,
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = White.copy(alpha = 0.3f)
                        ),
                        singleLine = true
                    )
                }
                
                // Security note
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = GoldPrimary.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Your API credentials are encrypted locally using post-quantum cryptography (Kyber-1024). They never leave your device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            val canSave = apiKey.isNotBlank() && apiSecret.isNotBlank() &&
                          (!needsPassphrase || passphrase.isNotBlank())
            Button(
                onClick = { 
                    if (canSave) {
                        onSave(
                            selectedExchange,
                            apiKey,
                            apiSecret,
                            passphrase.ifBlank { null },
                            isTestnet
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isTestnet) Color(0xFF4CAF50) else GoldPrimary
                ),
                enabled = canSave
            ) {
                Text(
                    if (isTestnet) "Connect (Testnet)" else "Connect",
                    color = NavyDark,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = White.copy(alpha = 0.7f))
            }
        }
    )
}

// =============================================================================
// TRADING MODE CARD - V5.5.97
// =============================================================================

@Composable
fun TradingModeCard(
    currentMode: String,
    hybridAutoThreshold: Double,
    hybridConfirmBelow: Double,
    hybridMaxAutoTrades: Int,
    hybridValueThreshold: Double,
    onModeChange: (String) -> Unit,
    onAutoThresholdChange: (Double) -> Unit,
    onConfirmBelowChange: (Double) -> Unit,
    onMaxAutoTradesChange: (Int) -> Unit,
    onValueThresholdChange: (Double) -> Unit
) {
    val modes = listOf(
        Triple("SIGNAL_ONLY", "Signal Only", "AI suggests, you confirm every trade"),
        Triple("HYBRID", "Hybrid", "Auto-execute high-confidence, confirm the rest"),
        Triple("AUTONOMOUS", "Autonomous", "AI decides and executes automatically"),
        Triple("SCALPING", "Scalping", "High-frequency with ultra-tight stops"),
        Triple("ALPHA_SCANNER", "Alpha Scanner", "Multi-factor quantitative ranking")
    )
    
    SettingsCard {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = GoldPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Operating Mode",
                    style = MaterialTheme.typography.titleMedium,
                    color = White,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Mode selector
            modes.forEach { (modeId, displayName, description) ->
                val isSelected = currentMode == modeId
                val borderColor = if (isSelected) GoldPrimary else NavyDark
                val bgColor = if (isSelected) NavyDark.copy(alpha = 0.8f) else NavyDark.copy(alpha = 0.3f)
                
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = bgColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onModeChange(modeId) },
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = borderColor
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onModeChange(modeId) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = GoldPrimary,
                                unselectedColor = White.copy(alpha = 0.5f)
                            )
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSelected) GoldPrimary else White,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                description,
                                style = MaterialTheme.typography.bodySmall,
                                color = White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
            
            // HYBRID configuration — only show when HYBRID mode selected
            if (currentMode == "HYBRID") {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = GoldPrimary.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    "HYBRID MODE SETTINGS",
                    style = MaterialTheme.typography.labelLarge,
                    color = GoldPrimary,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Auto-execute threshold
                Text(
                    "Auto-execute above: ${hybridAutoThreshold.toInt()}% confidence",
                    style = MaterialTheme.typography.bodyMedium,
                    color = White
                )
                Slider(
                    value = hybridAutoThreshold.toFloat(),
                    onValueChange = { onAutoThresholdChange(it.toDouble()) },
                    valueRange = 50f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = GoldPrimary,
                        activeTrackColor = GoldPrimary
                    )
                )
                
                // Require confirmation below
                Text(
                    "Always confirm below: ${hybridConfirmBelow.toInt()}% confidence",
                    style = MaterialTheme.typography.bodyMedium,
                    color = White
                )
                Slider(
                    value = hybridConfirmBelow.toFloat(),
                    onValueChange = { onConfirmBelowChange(it.toDouble()) },
                    valueRange = 0f..95f,
                    colors = SliderDefaults.colors(
                        thumbColor = GoldPrimary,
                        activeTrackColor = GoldPrimary
                    )
                )
                
                // Max auto-trades per hour
                Text(
                    "Max auto-trades/hour: $hybridMaxAutoTrades",
                    style = MaterialTheme.typography.bodyMedium,
                    color = White
                )
                Slider(
                    value = hybridMaxAutoTrades.toFloat(),
                    onValueChange = { onMaxAutoTradesChange(it.toInt()) },
                    valueRange = 1f..20f,
                    steps = 18,
                    colors = SliderDefaults.colors(
                        thumbColor = GoldPrimary,
                        activeTrackColor = GoldPrimary
                    )
                )
                
                // Value threshold (AU$)
                Text(
                    "Confirm trades above: AU$${"%,.0f".format(hybridValueThreshold)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = White
                )
                Slider(
                    value = hybridValueThreshold.toFloat(),
                    onValueChange = { onValueThresholdChange(it.toDouble()) },
                    valueRange = 100f..100_000f,
                    colors = SliderDefaults.colors(
                        thumbColor = GoldPrimary,
                        activeTrackColor = GoldPrimary
                    )
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Trades between ${hybridConfirmBelow.toInt()}% and ${hybridAutoThreshold.toInt()}% confidence " +
                    "will auto-execute UNLESS the trade value exceeds AU$${"%,.0f".format(hybridValueThreshold)}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = White.copy(alpha = 0.5f)
                )
            }
            
            // Safety warning for AUTONOMOUS mode
            if (currentMode == "AUTONOMOUS") {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "⚠️ AUTONOMOUS MODE: The AI will execute all trades without confirmation. " +
                    "The 5% kill switch still applies. Ensure your risk settings are correct.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LossRed.copy(alpha = 0.8f)
                )
            }
        }
    }
}

// =============================================================================
// ADVANCED STRATEGIES CARD - V5.5.74
// =============================================================================

@Composable
fun AdvancedStrategiesCard(
    uiState: SettingsUiState,
    onAlphaScannerEnabledChange: (Boolean) -> Unit,
    onFundingArbEnabledChange: (Boolean) -> Unit,
    onAlphaScannerIntervalChange: (Int) -> Unit,
    onFundingArbMinRateChange: (Double) -> Unit,
    onFundingArbMaxCapitalChange: (Double) -> Unit,
    onDailyLossLimitChange: (Double) -> Unit
) {
    var showAlphaScannerDetails by remember { mutableStateOf(false) }
    var showFundingArbDetails by remember { mutableStateOf(false) }
    var showRiskDetails by remember { mutableStateOf(false) }
    
    SettingsCard {
        // Alpha Factor Scanner
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.QueryStats,
                        contentDescription = null,
                        tint = GoldPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Alpha Factor Scanner",
                            style = MaterialTheme.typography.titleSmall,
                            color = White,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Quantitative asset ranking",
                            style = MaterialTheme.typography.bodySmall,
                            color = White.copy(alpha = 0.6f)
                        )
                    }
                }
                Switch(
                    checked = uiState.alphaScannerEnabled,
                    onCheckedChange = onAlphaScannerEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = GoldPrimary,
                        checkedTrackColor = GoldPrimary.copy(alpha = 0.5f)
                    )
                )
            }
            
            // Expand/collapse details
            if (uiState.alphaScannerEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { showAlphaScannerDetails = !showAlphaScannerDetails }
                ) {
                    Text(
                        if (showAlphaScannerDetails) "Hide Settings" else "Show Settings",
                        color = GoldPrimary.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Icon(
                        if (showAlphaScannerDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = GoldPrimary.copy(alpha = 0.8f)
                    )
                }
                
                if (showAlphaScannerDetails) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Scan interval slider
                    Text(
                        "Scan Interval: ${uiState.alphaScannerInterval} minutes",
                        style = MaterialTheme.typography.bodySmall,
                        color = White.copy(alpha = 0.8f)
                    )
                    Slider(
                        value = uiState.alphaScannerInterval.toFloat(),
                        onValueChange = { onAlphaScannerIntervalChange(it.toInt()) },
                        valueRange = 15f..240f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = GoldPrimary,
                            activeTrackColor = GoldPrimary
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Top N assets
                    Text(
                        "Track Top: ${uiState.alphaScannerTopN} assets",
                        style = MaterialTheme.typography.bodySmall,
                        color = White.copy(alpha = 0.8f)
                    )
                    
                    // Factor weights info
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = NavyDark.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Factor Weights:",
                                style = MaterialTheme.typography.labelMedium,
                                color = GoldPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "• Momentum: 35%\n• Quality: 25%\n• Trend: 25%\n• Volatility: 15%",
                                style = MaterialTheme.typography.bodySmall,
                                color = White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
        
        HorizontalDivider(color = NavyDark)
        
        // Delta-Neutral Funding Arbitrage
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AccountBalance,
                        contentDescription = null,
                        tint = ProfitGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Funding Arbitrage",
                            style = MaterialTheme.typography.titleSmall,
                            color = White,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Delta-neutral funding printer",
                            style = MaterialTheme.typography.bodySmall,
                            color = White.copy(alpha = 0.6f)
                        )
                    }
                }
                Switch(
                    checked = uiState.fundingArbEnabled,
                    onCheckedChange = onFundingArbEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ProfitGreen,
                        checkedTrackColor = ProfitGreen.copy(alpha = 0.5f)
                    )
                )
            }
            
            if (uiState.fundingArbEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { showFundingArbDetails = !showFundingArbDetails }
                ) {
                    Text(
                        if (showFundingArbDetails) "Hide Settings" else "Show Settings",
                        color = ProfitGreen.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Icon(
                        if (showFundingArbDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = ProfitGreen.copy(alpha = 0.8f)
                    )
                }
                
                if (showFundingArbDetails) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Min funding rate
                    Text(
                        "Min Rate to Enter: ${String.format("%.3f", uiState.fundingArbMinRate)}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = White.copy(alpha = 0.8f)
                    )
                    Slider(
                        value = (uiState.fundingArbMinRate * 100).toFloat(),
                        onValueChange = { onFundingArbMinRateChange(it.toDouble() / 100) },
                        valueRange = 0.5f..10f,
                        colors = SliderDefaults.colors(
                            thumbColor = ProfitGreen,
                            activeTrackColor = ProfitGreen
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Max capital allocation
                    Text(
                        "Max Capital: ${uiState.fundingArbMaxCapital.toInt()}% of portfolio",
                        style = MaterialTheme.typography.bodySmall,
                        color = White.copy(alpha = 0.8f)
                    )
                    Slider(
                        value = uiState.fundingArbMaxCapital.toFloat(),
                        onValueChange = { onFundingArbMaxCapitalChange(it.toDouble()) },
                        valueRange = 10f..80f,
                        colors = SliderDefaults.colors(
                            thumbColor = ProfitGreen,
                            activeTrackColor = ProfitGreen
                        )
                    )
                    
                    // How it works
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = ProfitGreen.copy(alpha = 0.1f)
                        ),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "How It Works:",
                                style = MaterialTheme.typography.labelMedium,
                                color = ProfitGreen
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "LONG spot + SHORT perp = Δ0\nCollect 8-hour funding payments\nPotential 10-110% APY",
                                style = MaterialTheme.typography.bodySmall,
                                color = White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
        
        HorizontalDivider(color = NavyDark)
        
        // Risk Controls (HARD LIMITS)
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = LossRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Risk Controls",
                            style = MaterialTheme.typography.titleSmall,
                            color = White,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Hard kill switch at 5%",
                            style = MaterialTheme.typography.bodySmall,
                            color = LossRed.copy(alpha = 0.8f)
                        )
                    }
                }
                IconButton(onClick = { showRiskDetails = !showRiskDetails }) {
                    Icon(
                        if (showRiskDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = White.copy(alpha = 0.6f)
                    )
                }
            }
            
            if (showRiskDetails) {
                Spacer(modifier = Modifier.height(12.dp))
                
                // Kill switch - FIXED (Cannot be changed)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = LossRed.copy(alpha = 0.15f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "KILL SWITCH",
                                style = MaterialTheme.typography.labelMedium,
                                color = LossRed,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Liquidate ALL to USDT",
                                style = MaterialTheme.typography.bodySmall,
                                color = White.copy(alpha = 0.6f)
                            )
                        }
                        Text(
                            "5%",
                            style = MaterialTheme.typography.headlineSmall,
                            color = LossRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Daily loss limit (adjustable)
                Text(
                    "Daily Loss Halt: ${uiState.dailyLossLimit}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = White.copy(alpha = 0.8f)
                )
                Slider(
                    value = uiState.dailyLossLimit.toFloat(),
                    onValueChange = { onDailyLossLimitChange(it.toDouble()) },
                    valueRange = 1f..5f,
                    colors = SliderDefaults.colors(
                        thumbColor = LossRed,
                        activeTrackColor = LossRed
                    )
                )
                
                // Warning
                Text(
                    "⚠️ Kill switch is NON-NEGOTIABLE. At 5% portfolio drawdown, all positions are liquidated to stablecoin. Manual restart required.",
                    style = MaterialTheme.typography.bodySmall,
                    color = White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
